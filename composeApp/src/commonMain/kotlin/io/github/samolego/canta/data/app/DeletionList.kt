package io.github.samolego.canta.data.app

import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.AppBlobStorage
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.data.history.OperationLogStore
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 「卸载记录」页背后的备份仓库。
 *
 * 用户嘴里的"备份"就是这里的一份 [DeletionBackup]：**点备份那一刻**，这台手机的两份名单
 * ——卸载名单（删了还没装回来的）和冻结名单（现在停用的）——一起存成一份，可以重命名、
 * 可以分享出去、可以再导入进来、可以照它整份恢复。
 *
 * 与旧版的区别：旧版是"本机卸了哪些包"实时算一份、导入的每份各占一行；现在本机那一份只是
 * 列表顶上的"当前状态"（不落盘、不能删），下面全是**点过备份的那些时刻**。恢复只认备份里的
 * 那两份名单，不再现算，所以"备份时删了什么"和"现在删了什么"能分开看。
 *
 * 落盘的就是备份列表本身（[DeletionBackupCodec.Blob]），换手机靠导出 / 导入那份 JSON。
 */
class DeletionListStore(
    private val storage: AppBlobStorage,
    private val platform: CantaPlatform,
    /** 卸载名单的来源：Canta 删掉、到现在还没装回来的包由它回答。 */
    private val history: OperationLogStore,
) {

    companion object {
        private const val TAG = "DeletionList"

        /** 分享面板 / 存到手机时那一份的名字（备份名会替换掉这里的默认值）。 */
        const val FILE_NAME = "canta-backup.json"

        private const val JSON_MIME = "application/json"
    }

    private val mutex = Mutex()
    private var loaded = false

    private val _backups = MutableStateFlow<List<DeletionBackup>>(emptyList())

    /** 点过的备份，最新的在前。 */
    val backups: StateFlow<List<DeletionBackup>> = _backups.asStateFlow()

    /** 读一次落盘的那一份。 */
    suspend fun ensureReady() {
        mutex.withLock {
            if (loaded) return@withLock
            runCatching {
                storage.read()?.decodeToString()?.let { text ->
                    _backups.value = DeletionBackupCodec.decodeBlob(text)
                        .filter { it.total > 0 }
                        .sortedByDescending { it.createdAt }
                }
            }.onFailure { LogUtils.e(TAG, "Could not read the backups", it) }
            loaded = true
        }
    }

    /**
     * **点备份**：按现在这一刻的两份名单做一份备份。
     *
     * [uninstall] / [frozen] 由调用方从应用列表里挑好（那边才知道每一个包现在到底在不在、
     * 是不是被停用），这里只负责去重、排序、起名、落盘。
     */
    suspend fun createBackup(
        uninstall: List<BackupApp>,
        frozen: List<BackupApp>,
        device: String = "",
    ): DeletionBackup {
        ensureReady()
        val at = currentTimeMillis()
        // 同一个包两边都有时只算冻结：它还在这台手机上，只是被停了。
        val frozenKeys = frozen.mapTo(HashSet()) { it.packageName.lowercase() }
        val backup = DeletionBackup(
            id = "backup-" + at + "-" + (0..999).random(),
            name = uniqueName(DeletionBackup.defaultName(at)),
            createdAt = at,
            uninstall = uninstall
                .filter { it.packageName.lowercase() !in frozenKeys }
                .distinctBy { it.packageName.lowercase() }
                .sortedWith(compareBy(BACKUP_ORDER) { it.packageName }),
            frozen = frozen
                .distinctBy { it.packageName.lowercase() }
                .sortedWith(compareBy(BACKUP_ORDER) { it.packageName }),
            device = device,
        )
        val next = (listOf(backup) + _backups.value).sortedByDescending { it.createdAt }
        mutex.withLock {
            _backups.value = next
            persist(next)
        }
        LogUtils.i(TAG, "Backed up ${backup.uninstall.size} removed and ${backup.frozen.size} frozen")
        return backup
    }

    /** 现在这一刻本机删掉、还没装回来的包（冻结名单要问应用列表，那边才知道谁被停用）。 */
    suspend fun currentState(): DeletionSnapshot {
        val removed = runCatching { history.recentlyRemovedPackages() }.getOrDefault(emptyList())
        return DeletionSnapshot(
            uninstall = removed.filter(::isValidBackupPackage).distinct().sortedWith(BACKUP_ORDER),
        )
    }

    /** 改名字。[name] 空了就退回默认名字（点备份那个时间点），不会出现一行没有名字的备份。 */
    suspend fun rename(id: String, name: String): DeletionBackup? {
        ensureReady()
        val target = _backups.value.firstOrNull { it.id == id } ?: return null
        val wanted = name.trim().ifEmpty { DeletionBackup.defaultName(target.createdAt) }
        val renamed = target.copy(name = uniqueName(wanted, exceptId = id))
        val next = _backups.value.map { if (it.id == id) renamed else it }
        mutex.withLock {
            _backups.value = next
            persist(next)
        }
        return renamed
    }

    /** 删掉一份备份。返回真的删了吗。 */
    suspend fun removeBackup(id: String): Boolean {
        ensureReady()
        val next = _backups.value.filterNot { it.id == id }
        if (next.size == _backups.value.size) return false
        mutex.withLock {
            _backups.value = next
            persist(next)
        }
        return true
    }

    /** 按 id 取一份。 */
    suspend fun backup(id: String): DeletionBackup? {
        ensureReady()
        return _backups.value.firstOrNull { it.id == id }
    }

    /**
     * 备份文件的 JSON 正文。给"存到手机"和"分享出去"两处共用，
     * 也回答"这一份导出来会是什么样"。
     */
    fun toJson(backup: DeletionBackup): String = DeletionBackupCodec.encode(backup)

    /** 存到用户自己挑的位置；取消返回 path = null，不写任何文件。 */
    suspend fun saveAs(backup: DeletionBackup): DeletionBackupExport {
        val content = toJson(backup)
        val fileName = DeletionBackupCodec.fileName(backup.name)
        val picked = runCatching { platform.saveFileAs(fileName, content, JSON_MIME) }.getOrNull()
        return DeletionBackupExport(path = picked, fileName = fileName)
    }

    /** 交给系统分享面板（微信文件传输助手、QQ、邮件、U 盘……）。打不开返回 false。 */
    suspend fun shareAs(backup: DeletionBackup): DeletionBackupExport {
        val content = toJson(backup)
        val fileName = DeletionBackupCodec.fileName(backup.name)
        val shared = runCatching { platform.shareFile(fileName, content, JSON_MIME) }.getOrDefault(false)
        val path = if (shared) null else runCatching { platform.exportLog(fileName, content) }.getOrNull()
        return DeletionBackupExport(path = path, fileName = fileName, shared = shared)
    }

    /**
     * 把用户挑回来的文本读成一份备份并**加进列表**（导入进来就留着，可以再看、再恢复，
     * 也可以再分享出去）。读不懂回答 null。
     */
    suspend fun import(text: String): DeletionBackup? {
        ensureReady()
        val parsed = DeletionBackupCodec.decode(text) ?: return null
        val at = currentTimeMillis()
        val backup = DeletionBackup(
            id = "import-" + at + "-" + (0..999).random(),
            name = uniqueName(parsed.name),
            createdAt = at,
            uninstall = parsed.uninstall.sortedWith(compareBy(BACKUP_ORDER) { it.packageName }),
            frozen = emptyList(),
            device = parsed.device,
            imported = true,
        )
        val next = (listOf(backup) + _backups.value).sortedByDescending { it.createdAt }
        mutex.withLock {
            _backups.value = next
            persist(next)
        }
        return backup
    }

    /** 导入前先解析一次，让调用方能在确认框里说清"有多少条、丢掉几条"。 */
    fun parse(text: String): ImportedBackup? = DeletionBackupCodec.decode(text)

    /**
     * 照一份备份恢复：停用过的先启用，卸载过的再装回来。
     *
     * [restore] 是真正动手的那一段（走应用列表那条路，不在这里另写一套）。一个包只恢复一次：
     * 它在 [DeletionBackup.frozen] 里就按"启用"恢复，否则按"装回"恢复。
     */
    suspend fun restore(
        backup: DeletionBackup,
        restore: suspend (packageName: String, kind: OperationKind) -> Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): DeletionRestoreResult {
        val failed = mutableListOf<String>()
        // 先启用后装回：冻结的包本来就还在，装回对它是空操作，顺序错了会把成功算成失败。
        val ordered = backup.frozen.map { it.packageName to OperationKind.ENABLE } +
            backup.uninstall.map { it.packageName to OperationKind.REINSTALL }
        var succeeded = 0
        ordered.forEachIndexed { index, (pkg, kind) ->
            val done = runCatching { restore(pkg, kind) }.getOrDefault(0)
            if (done > 0) succeeded++ else failed += pkg
            onProgress(index + 1, ordered.size)
        }
        return DeletionRestoreResult(succeeded = succeeded, failed = failed)
    }

    /** 同名时加后缀：`卸载记录 2026-11-26 19:04` → `… (2)`。 */
    private fun uniqueName(base: String, exceptId: String = ""): String {
        val taken = _backups.value.filterNot { it.id == exceptId }.mapTo(HashSet()) { it.name }
        if (base !in taken) return base
        var n = 2
        while ("$base ($n)" in taken) n++
        return "$base ($n)"
    }

    private suspend fun persist(backups: List<DeletionBackup>) = withContext(Dispatchers.Default) {
        runCatching {
            storage.write(DeletionBackupCodec.encodeBlob(backups).encodeToByteArray())
        }.onFailure { LogUtils.e(TAG, "Could not store the backups", it) }
        Unit
    }
}

/** 现在这一刻本机删掉的包（不落盘；备份时由页面配上应用名和冻结名单）。 */
data class DeletionSnapshot(
    val uninstall: List<String> = emptyList(),
) {
    val total: Int get() = uninstall.size
}

/** 存 / 分享的结果。 */
data class DeletionBackupExport(
    /** 文件落到了哪里；null = 取消了，或者平台没有这个能力。 */
    val path: String? = null,
    val fileName: String = "",
    val shared: Boolean = false,
)

/** 整份恢复的结果：成功几个、失败哪些（失败的包名照原样带出来，调用方点一下就能单独重试）。 */
data class DeletionRestoreResult(
    val succeeded: Int = 0,
    val failed: List<String> = emptyList(),
) {
    val total: Int get() = succeeded + failed.size
}