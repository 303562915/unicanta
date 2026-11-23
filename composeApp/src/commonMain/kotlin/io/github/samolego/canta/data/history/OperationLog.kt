package io.github.samolego.canta.data.history

import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.AppBlobStorage
import io.github.samolego.canta.data.app.PackageSnapshot
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * What Canta did to a package. The first two remove and put back; the last two freeze and
 * unfreeze, which is the same intent with a reachable way out — the package is still there, so
 * undoing needs no copy of it.
 */
@Serializable
enum class OperationKind { UNINSTALL, REINSTALL, DISABLE, ENABLE }

/**
 * One operation on one package, as it happened: what was done, to which app, when, whether it
 * worked, and which batch it belonged to.
 *
 * A batch is one tap on the action button: [batchId] is the moment it started and [batchSize] how
 * many apps were in it, so a log read weeks later still shows "3 of the 8 I removed that evening".
 */
@Serializable
data class OperationRecord(
    /** Epoch milliseconds of the operation. */
    val timestamp: Long,
    val kind: OperationKind,
    val packageName: String,
    /** Display name of the app at the moment of the operation; the package may be gone by now. */
    val label: String,
    val success: Boolean,
    /** The handler's own words when it failed; empty on success. */
    val message: String = "",
    /**
     * What the failure means, in the user's language, worked out from the status code and
     * [message] at the moment it happened. Empty on success, and empty for records written
     * before this was added — the row falls back to [message] for those.
     */
    val reason: String = "",
    /** What to try next, when there is something worth trying. */
    val advice: String = "",
    /**
     * The package as it was before the operation: version, installer, first install time. Only
     * uninstalls carry one — that is the direction where knowing what was there matters — and
     * records written before snapshots existed simply have none.
     */
    val snapshot: PackageSnapshot? = null,
    val batchId: Long = 0L,
    val batchIndex: Int = 0,
    val batchSize: Int = 1,
    /** An updated system app was rolled back to its factory version instead of removed. */
    val resetToFactory: Boolean = false,
    /** Model/ROM of the phone, so records from two devices don't get mixed up. */
    val device: String = "",
    /** Android user the package was removed for, when the platform has that choice. */
    val user: Int? = null,
)

/**
 * The batch that was running when the app last stopped, kept beside the log in the same blob: a
 * resume needs both the packages that never got an answer and what the batch was doing to them.
 *
 * The kind is stored by name rather than by ordinal so a record written by a newer build cannot be
 * misread as a different operation: an unknown name falls back to [OperationKind.UNINSTALL], which
 * is also what a blob written before this field existed means — those versions only ever
 * remembered uninstall batches.
 */
@Serializable
private data class PendingBatchBlob(
    val packages: List<String> = emptyList(),
    val startedAt: Long = 0L,
    val kind: String = "",
) {
    /** The batch as a candidate, or null when nothing is pending. */
    fun candidate(): ResumeCandidate? {
        if (packages.isEmpty() || startedAt <= 0) return null
        return ResumeCandidate(
            packages = packages,
            startedAt = startedAt,
            kind = runCatching { OperationKind.valueOf(kind) }.getOrDefault(OperationKind.UNINSTALL),
        )
    }

    companion object {
        fun of(packages: List<String>, startedAt: Long, kind: OperationKind) =
            PendingBatchBlob(packages = packages, startedAt = startedAt, kind = kind.name)

        /** Anything still undecided: a batch that was answered in full, or one never started. */
        val NONE = PendingBatchBlob()
    }
}

@Serializable
private data class OperationLogBlob(
    val records: List<OperationRecord> = emptyList(),
    /**
     * The batch that never finished. Blobs written before this field existed decode as "nothing
     * pending", and the copy `CantaSettings` keeps is used instead (an uninstall batch).
     */
    val pending: PendingBatchBlob = PendingBatchBlob.NONE,
)

/**
 * The record of everything Canta uninstalled or put back: shown on the records tab and mirrored
 * to a CSV the user can pull out of the device by themselves (Download/Canta on Android).
 *
 * The CSV is the durable copy — it is rewritten on every operation, so it always holds the whole
 * log even if the app's own storage is cleared — while the blob holds the newest [MAX_RECORDS] for
 * the list.
 */
class OperationLogStore(
    private val storage: AppBlobStorage,
    private val platform: CantaPlatform,
) {

    companion object {
        private const val TAG = "OperationLog"

        /** Records kept in the blob and the list; the CSV keeps everything. */
        const val MAX_RECORDS = 500

        /** Name of the CSV under the platform's download folder. */
        const val FILE_NAME = "canta-operations.csv"

        /**
         * The rescue script the settings page hands to the share sheet: a Windows batch file that
         * puts back what was removed, since that has to run on a computer with adb, not here.
         */
        const val RESTORE_FILE_NAME = "canta-restore.bat"

        /** What the share sheet is told the rescue script is; `.bat` has no registered MIME. */
        private const val RESTORE_MIME = "application/octet-stream"

        private const val CSV_HEADER =
            "time,action,result,package,label,batch_index,batch_size,reset_to_factory,device,user," +
                "message,reason,advice,version_name,version_code,is_system,installer,first_install_time"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _records = MutableStateFlow<List<OperationRecord>>(emptyList())

    /** Newest first, as the records tab shows them. */
    val records: StateFlow<List<OperationRecord>> = _records.asStateFlow()

    /** The batch that never finished, if any; read and written under [mutex] with the records. */
    private var pending = PendingBatchBlob.NONE

    private var loaded = false

    /** Reads the blob once; every entry point calls it before answering. */
    suspend fun ensureReady() {
        mutex.withLock {
            if (loaded) return@withLock
            runCatching {
                storage.read()?.decodeToString()?.let { text ->
                    val blob = json.decodeFromString(OperationLogBlob.serializer(), text)
                    _records.value = blob.records.sortedByDescending { it.timestamp }
                    pending = blob.pending
                }
            }.onFailure { LogUtils.e(TAG, "Could not read the operation log", it) }
            loaded = true
        }
    }

    /**
     * Adds [records] (one batch) to the log and writes it out: the blob for the list, the CSV for
     * the user. The newest [MAX_RECORDS] are kept in memory.
     */
    suspend fun record(records: List<OperationRecord>) {
        if (records.isEmpty()) return
        ensureReady()
        val (kept, pendingNow) = mutex.withLock {
            val merged = (records.sortedByDescending { it.timestamp } + _records.value)
                .take(MAX_RECORDS)
            _records.value = merged
            merged to pending
        }
        persist(kept, pendingNow)
        LogUtils.i(TAG, "Recorded ${records.size} operation(s)")
    }

    /**
     * Remembers the batch that is starting, together with what it is doing, so a batch cut short by
     * the app dying still knows which apps it never reached and what to do to them.
     *
     * @param packages the whole list; the finished ones are dropped as their answers arrive.
     * @param startedAt the batch id, which is also when it started.
     */
    suspend fun startPendingBatch(packages: List<String>, startedAt: Long, kind: OperationKind) {
        if (packages.isEmpty()) return
        ensureReady()
        val next = PendingBatchBlob.of(packages, startedAt, kind)
        val (kept, pendingNow) = mutex.withLock {
            pending = next
            _records.value to next
        }
        persist(kept, pendingNow)
        LogUtils.i(TAG, "Remembered a ${kind.name.lowercase()} batch of ${packages.size} package(s)")
    }

    /**
     * The stored batch, or null when the last one was answered in full. The caller still has to
     * drop the packages that do have a record: this only says what was remembered.
     */
    suspend fun pendingBatch(): ResumeCandidate? {
        ensureReady()
        return mutex.withLock { pending }.candidate()
    }

    /** The batch finished (or was abandoned): nothing is left to resume. */
    suspend fun clearPendingBatch() {
        ensureReady()
        val (kept, pendingNow) = mutex.withLock {
            if (pending == PendingBatchBlob.NONE) return@withLock null
            pending = PendingBatchBlob.NONE
            _records.value to PendingBatchBlob.NONE
        } ?: return
        persist(kept, pendingNow)
    }

    /** Forgets every record: the blob and the CSV both start over. */
    suspend fun clear() {
        mutex.withLock {
            _records.value = emptyList()
            // A resume is worked out against the records; without them a stored batch would look
            // untouched and be redone from the first app. Both go together.
            pending = PendingBatchBlob.NONE
            loaded = true
        }
        persist(emptyList(), PendingBatchBlob.NONE)
        LogUtils.i(TAG, "Operation log cleared")
    }

    /**
     * Writes the log to the user's download folder now and returns where it landed, or null on a
     * platform without one (the web build). The mirror is normally already up to date; this is for
     * "save it where I can see it" on demand.
     */
    suspend fun export(): String? = writeCsv(_records.value)

    /**
     * 最近被 Canta 删掉、到现在还没装回来的包名，最新的在前。
     *
     * 每个包只看最后一条卸载/恢复记录：最后一条是成功卸载的，才算"这台手机现在没有它"。
     * 冻结/解冻不算，它们不改包在不在。
     */
    suspend fun recentlyRemovedPackages(): List<String> {
        ensureReady()
        val latest = LinkedHashMap<String, OperationRecord>()
        _records.value.sortedByDescending { it.timestamp }.forEach { record ->
            if (record.kind == OperationKind.UNINSTALL || record.kind == OperationKind.REINSTALL) {
                // Newest first: the first record seen for a package is its last word.
                if (!latest.containsKey(record.packageName)) latest[record.packageName] = record
            }
        }
        return latest.filterValues { it.kind == OperationKind.UNINSTALL && it.success }.keys.toList()
    }

    /**
     * 生成"把删掉的装回来"的脚本，交给分享面板发到电脑上双击运行。
     *
     * 为什么是一个文件而不是一页清单：`install-existing` 只能在电脑上通过 adb 打，
     * 手机自己救不了自己；而人手里真正要的是"插上数据线、双击、等它跑完"。
     * 脚本先按记录逐个恢复，再保底把系统里所有已卸载的包恢复一遍——即使记录丢了、
     * 或者删的时候 Canta 不在，第二次也还在。
     */
    suspend fun shareRestoreScript(): RestoreExport {
        ensureReady()
        val packages = recentlyRemovedPackages()
        // 拼字符串放后台；分享面板得在调用者的线程上开（Android 从应用上下文启 Activity 要主线程）。
        val script = withContext(Dispatchers.Default) { restoreScript(packages) }
        val shared = runCatching { platform.shareFile(RESTORE_FILE_NAME, script, RESTORE_MIME) }
            .getOrDefault(false)
        // 没有分享面板的构建（桌面、网页）至少把文件放到用户找得到的地方。
        val path = if (shared) {
            null
        } else {
            runCatching { platform.exportLog(RESTORE_FILE_NAME, script) }.getOrNull()
        }
        return RestoreExport(packages = packages.size, shared = shared, path = path)
    }

    /** The batch file itself; CRLF because it is a Windows script, whoever writes it. */
    private fun restoreScript(packages: List<String>): String = buildString {
        append("@echo off\r\n")
        append("chcp 65001 >nul\r\n")
        append("rem ==== Canta 恢复脚本（生成于 ${OperationTime.stamp(currentTimeMillis())}）====\r\n")
        append("rem 用法：手机打开「USB 调试」，用数据线连上电脑，电脑装好 adb，然后双击本文件。\r\n")
        append("rem 先按 Canta 的记录把删掉的包装回来，再保底把系统里所有「已卸载但文件还在」\r\n")
        append("rem 的包恢复一次。两者都是 install-existing，不碰数据，不会清手机。\r\n")
        append("setlocal enabledelayedexpansion\r\n")
        append("set \"ADB=adb\"\r\n")
        append("\r\n")
        append("%ADB% version >nul 2>&1 || (\r\n")
        append("  echo [x] 找不到 adb，请把 platform-tools 放进 PATH 再试一次。\r\n")
        append("  pause\r\n")
        append("  exit /b 1\r\n")
        append(")\r\n")
        append("%ADB% get-state >nul 2>&1 || (\r\n")
        append("  echo [x] 手机没连上：检查数据线、USB 调试授权，看看 adb devices 里有没有这台设备。\r\n")
        append("  pause\r\n")
        append("  exit /b 1\r\n")
        append(")\r\n")
        append("\r\n")
        if (packages.isEmpty()) {
            append("rem 记录里没有未恢复的卸载，直接走保底。\r\n")
        } else {
            append("echo [1/2] 按 Canta 的记录恢复 ${packages.size} 个包...\r\n")
            append("for %%P in (\r\n")
            packages.forEach { append(it).append("\r\n") }
            append(") do (\r\n")
            append("  echo   %%P\r\n")
            append("  %ADB% shell cmd package install-existing --user 0 %%P\r\n")
            append(")\r\n")
            append("\r\n")
        }
        append("echo [2/2] 保底：恢复系统里所有已卸载的包...\r\n")
        // 循环放在手机上跑：adb 的输出带 CRLF，批处理的 for /f 会把行尾回车一起读进变量，
        // 拼出来的包名带着 \r 传给 install-existing 必然对不上。只依赖 cut 与 while read，
        // 已安装的包再 install-existing 一次是无害的，所以这里不做差集，宁可多跑几个。
        append(
            "%ADB% shell \"pm list packages -u | cut -d: -f2 | while read p; do " +
                "echo   \$p; cmd package install-existing --user 0 \$p; done\"\r\n"
        )
        append("\r\n")
        append("echo.\r\n")
        append("echo 跑完了，重启手机看看应用回来没有。\r\n")
        append("pause\r\n")
    }

    private suspend fun persist(
        records: List<OperationRecord>,
        pending: PendingBatchBlob,
    ) = withContext(Dispatchers.Default) {
        runCatching {
            storage.write(
                json.encodeToString(
                    OperationLogBlob.serializer(),
                    OperationLogBlob(records = records, pending = pending),
                ).encodeToByteArray()
            )
        }.onFailure { LogUtils.e(TAG, "Could not store the operation log", it) }
        writeCsv(records)
        Unit
    }

    private suspend fun writeCsv(records: List<OperationRecord>): String? = withContext(Dispatchers.Default) {
        runCatching { platform.exportLog(FILE_NAME, toCsv(records)) }
            .onFailure { LogUtils.e(TAG, "Could not mirror the operation log to the download folder", it) }
            .getOrNull()
    }

    /** The whole log, oldest first, one operation per line. */
    private fun toCsv(records: List<OperationRecord>): String = buildString {
        append(CSV_HEADER)
        append('\n')
        records.asReversed().forEach { record ->
            append(OperationTime.stamp(record.timestamp))
            append(',')
            append(record.kind.name.lowercase())
            append(',')
            append(if (record.success) "ok" else "failed")
            append(',')
            append(record.packageName.csvCell())
            append(',')
            append(record.label.csvCell())
            append(',')
            append(record.batchIndex)
            append(',')
            append(record.batchSize)
            append(',')
            append(if (record.resetToFactory) "yes" else "no")
            append(',')
            append(record.device.csvCell())
            append(',')
            append(record.user?.toString().orEmpty())
            append(',')
            append(record.message.csvCell())
            append(',')
            append(record.reason.csvCell())
            append(',')
            append(record.advice.csvCell())
            // The snapshot columns: what the package was before it left, so a CSV pulled off the
            // device still answers "which build was that?" months later.
            append(',')
            append(record.snapshot?.versionName.orEmpty().csvCell())
            append(',')
            append(record.snapshot?.versionCode?.toString().orEmpty())
            append(',')
            append(if (record.snapshot?.isSystem == true) "yes" else "no")
            append(',')
            append(record.snapshot?.installerPackage.orEmpty().csvCell())
            append(',')
            append(
                record.snapshot?.firstInstallTime
                    ?.takeIf { it > 0 }
                    ?.let(OperationTime::stamp)
                    .orEmpty()
            )
            append('\n')
        }
    }

    /** Quotes a cell only when it needs it, doubling the quotes inside. */
    private fun String.csvCell(): String =
        if (any { it == ',' || it == '"' || it == '\n' }) "\"" + replace("\"", "\"\"") + "\"" else this
}

/**
 * 分享恢复脚本的结果：脚本里写进了几个包名、分享面板有没有打开、以及没有面板时文件落在了哪里。
 */
data class RestoreExport(
    val packages: Int,
    val shared: Boolean,
    val path: String?,
)

/**
 * Wall-clock formatting for the record list and its CSV, in the phone's own time zone: a log read
 * on another machine should still say what time it was here.
 */
object OperationTime {
    private fun local(timestamp: Long) =
        Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())

    /** "2026-11-19": the day a record belongs to, sorted and grouped by. */
    fun day(timestamp: Long): String = local(timestamp).date.toString()

    /** Whole days since 1970-01-01, so "today" and "yesterday" need no date arithmetic. */
    fun dayIndex(timestamp: Long): Long = local(timestamp).date.toEpochDays()

    /** "01:12:33": the time of day, with seconds, because a batch can take a while. */
    fun clock(timestamp: Long): String {
        val time = local(timestamp)
        return buildString {
            append(time.hour.pad())
            append(':')
            append(time.minute.pad())
            append(':')
            append(time.second.pad())
        }
    }

    /** "2026-11-19T01:12:33": the CSV's own column, unambiguous without a locale. */
    fun stamp(timestamp: Long): String = day(timestamp) + "T" + clock(timestamp)

    private fun Int.pad(): String = toString().padStart(2, '0')
}
