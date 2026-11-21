package io.github.samolego.canta.data.history

import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.AppBlobStorage
import io.github.samolego.canta.data.app.PackageSnapshot
import io.github.samolego.canta.util.LogUtils
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
