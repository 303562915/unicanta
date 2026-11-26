package io.github.samolego.canta.data.app

import io.github.samolego.canta.util.currentTimeMillis
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Instant

/** 包名排序：不分大小写，同一份备份在两台手机上排出来一样。 */
val BACKUP_ORDER: Comparator<String> = compareBy { it.lowercase() }

/** 一份备份里的一个应用：包名、应用名，以及它离开手机那一刻的样子（知道的话）。 */
@Serializable
data class BackupApp(
    val packageName: String,
    /** 应用名。记录里没有就退回包名，列表里不显示空行。 */
    val label: String = "",
    /** 备份那一刻这个包装的是哪个版本；不知道就是空。 */
    val versionName: String = "",
    /** 是不是系统应用（系统应用"装回"和"卸载"的含义与人装的不同）。 */
    val isSystem: Boolean = false,
) {
    /** 列表里那一行标题。 */
    val shownLabel: String get() = label.ifEmpty { packageName }
}

/**
 * 一次备份：**点击备份那一刻**这台手机的两份名单。
 *
 *  1. `uninstall` —— 卸载名单：Canta 删掉、到现在还没装回来的包（记录算出来的）。
 *  2. `frozen` —— 冻结名单：现在被停用（冻结）的包。
 *
 * 两份分开存，是因为"恢复"的方向相反：前者要装回来，后者要启用回来。
 * 恢复时按 [frozen] 判断方向，一个包归哪边就是恢复哪边，不会两边都跑一遍。
 *
 * [createdAt] 就是点备份的时间，也是列表的排序键与默认名字（`卸载记录 2026-11-26 19:04`）。
 */
@Serializable
data class DeletionBackup(
    /** 判重、删除、恢复都用它；导入时生成。 */
    val id: String,
    /** 名字，可以改；默认是点击时间。 */
    val name: String,
    /** 点备份的那一刻（epoch 毫秒）。 */
    val createdAt: Long,
    /** 卸载名单。 */
    val uninstall: List<BackupApp> = emptyList(),
    /** 冻结名单。 */
    val frozen: List<BackupApp> = emptyList(),
    /** 备份时这台手机的型号 / ROM 一句话，换手机后看列表能认出"哪台机器上的"。 */
    val device: String = "",
    /** 从别处导入进来的（换手机、分享给别人的那份）。 */
    val imported: Boolean = false,
) {
    val uninstallNames: List<String> get() = uninstall.map { it.packageName }

    val frozenNames: List<String> get() = frozen.map { it.packageName }

    val total: Int get() = uninstall.size + frozen.size

    /** "卸载 12 · 冻结 3"：列表里第二行那串数字。 */
    val counts: String get() = "卸载 ${uninstall.size} · 冻结 ${frozen.size}"

    /** 默认名字：点备份的那一刻，人一眼能对上时间。 */
    companion object {
        const val PREFIX = "卸载记录"

        /** [at] 那一刻的默认名字：`卸载记录 2026-11-26 19:04`。 */
        fun defaultName(at: Long): String = "$PREFIX ${BackupTime.point(at)}"
    }
}

/** 备份文件与列表上的时间格式，都按手机自己的时区。 */
object BackupTime {
    private fun local(timestamp: Long) =
        Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())

    /** "2026-11-26 19:04"：备份那一刻，用在默认名字和列表第二行。 */
    fun point(timestamp: Long): String {
        val time = local(timestamp)
        return "${time.date} ${time.hour.pad()}:${time.minute.pad()}"
    }

    private fun Int.pad(): String = toString().padStart(2, '0')
}

/**
 * 一份备份 JSON 的读写。
 *
 * 写出去的是自描述的（`uninstall` / `frozen` 两个数组，每项带包名和名字），
 * 所以分享给别人、几个月后再导回来都还认得；读的时候也收旧形状：
 * `packages`（上一次那版「方案」）、`apps`（更早的预设导出）、以及裸数组 `["pkg"]`。
 */
object DeletionBackupCodec {

    const val KEY_UNINSTALL = "uninstall"
    const val KEY_FROZEN = "frozen"
    const val KEY_PACKAGES = "packages"
    const val KEY_LEGACY = "apps"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 分享 / 存到手机时那个文件名，用备份名，去掉文件名里不能有的字符。 */
    fun fileName(name: String): String {
        val clean = name.map { if (it.isLetterOrDigit() || it == '-' || it == '_' || it == ' ') it else '_' }
            .joinToString("")
            .trim()
            .replace(' ', '-')
        return "canta-backup-${clean.ifEmpty { "backup" }}.json"
    }

    /** 一份备份的 JSON 正文。 */
    fun encode(backup: DeletionBackup): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("app", "canta")
            put("kind", "deletion-backup")
            put("version", 2)
            put("name", backup.name)
            put("createdAt", backup.createdAt)
            if (backup.device.isNotEmpty()) put("device", backup.device)
            put(KEY_UNINSTALL, backup.uninstall.toJson())
            put(KEY_FROZEN, backup.frozen.toJson())
        },
    )

    private fun List<BackupApp>.toJson(): JsonArray = buildJsonArray {
        forEach { app ->
            add(
                buildJsonObject {
                    put("packageName", app.packageName)
                    if (app.label.isNotEmpty()) put("label", app.label)
                    if (app.versionName.isNotEmpty()) put("versionName", app.versionName)
                    if (app.isSystem) put("isSystem", true)
                },
            )
        }
    }

    /**
     * 把用户挑回来的文本读成一份备份。
     *
     * 读不懂就回答 null（调用方据此说「这份文件读不懂」）。两份名单都空也回答 null：
     * 那种文件恢复不了任何东西。
     */
    fun decode(text: String): ImportedBackup? {
        val root = runCatching { json.parseToJsonElement(text.trim()) }.getOrNull() ?: return null
        val name = (root as? JsonObject)?.get("name")?.asText()?.takeIf { it.isNotBlank() }
        val createdAt = (root as? JsonObject)?.get("createdAt")?.asLong() ?: currentTimeMillis()
        val device = (root as? JsonObject)?.get("device")?.asText().orEmpty()

        val uninstall = mutableListOf<BackupApp>()
        var rejected = 0
        when (root) {
            is JsonArray -> readApps(root, uninstall) { rejected++ }
            is JsonObject -> {
                val sections = listOfNotNull(
                    root[KEY_UNINSTALL]?.let { asArray(it) },
                    root[KEY_FROZEN]?.let { asArray(it) },
                )
                // 上一次那版「方案」只有一个 `packages`，更早的预设导出是 `apps`：都当成"卸载名单"。
                val plain = root[KEY_PACKAGES]?.let { asArray(it) }
                    ?: root[KEY_LEGACY]?.let { asArray(it) }
                if (plain != null) readApps(plain, uninstall) { rejected++ }
                sections.forEach { section -> readApps(section, uninstall) { rejected++ } }
                if (plain == null && sections.isEmpty()) return null
            }

            else -> return null
        }
        val before = uninstall.size
        val kept = uninstall.filter { it.packageName.isValidBackupPackage() }
            .distinctBy { it.packageName.lowercase() }
            .sortedWith(compareBy(BACKUP_ORDER) { it.packageName })
        rejected += before - kept.size
        if (kept.isEmpty()) return null
        return ImportedBackup(
            name = name ?: DeletionBackup.defaultName(createdAt),
            createdAt = createdAt,
            device = device,
            uninstall = kept,
            rejected = rejected,
            raw = text,
        )
    }

    /** 一个节要么是数组，要么是单个对象（手写的那份文件），后者当成只有一项的数组。 */
    private fun asArray(element: JsonElement): JsonArray = when (element) {
        is JsonArray -> element
        is JsonObject -> JsonArray(listOf(element))
        else -> JsonArray(emptyList())
    }

    private fun readApps(array: JsonArray, into: MutableList<BackupApp>, onRejected: () -> Unit) {
        array.forEach { element ->
            val app = runCatching {
                when (element) {
                    is JsonPrimitive -> BackupApp(packageName = element.content)
                    is JsonObject -> {
                        val pkg = element["packageName"]?.asText()
                            ?: element["package"]?.asText()
                            ?: element["id"]?.asText()
                        if (pkg == null) {
                            onRejected()
                            null
                        } else {
                            BackupApp(
                                packageName = pkg,
                                label = element["label"]?.asText()
                                    ?: element["name"]?.asText().orEmpty(),
                                versionName = element["versionName"]?.asText().orEmpty(),
                                isSystem = element["isSystem"]?.asText() == "true",
                            )
                        }
                    }

                    else -> null
                }
            }.getOrNull()
            if (app == null) onRejected() else into += app
        }
    }

    private fun JsonElement.asText(): String? =
        runCatching { (this as? JsonPrimitive)?.content }.getOrNull()

    private fun JsonElement.asLong(): Long? =
        runCatching { (this as? JsonPrimitive)?.longOrNull }.getOrNull()

    /** 磁盘上那一份：整份备份列表。 */
    @Serializable
    data class Blob(val backups: List<DeletionBackup> = emptyList())

    fun decodeBlob(text: String): List<DeletionBackup> =
        runCatching { json.decodeFromString(Blob.serializer(), text).backups }.getOrDefault(emptyList())

    fun encodeBlob(backups: List<DeletionBackup>): String =
        json.encodeToString(Blob.serializer(), Blob(backups))
}

/** 导入一份备份读出来的结果：能恢复的那部分 + 丢掉了几条 + 原文（确认之后照它真的导进来）。 */
data class ImportedBackup(
    val name: String,
    val createdAt: Long,
    val device: String,
    val uninstall: List<BackupApp>,
    /** 包名不合法、或者那一行根本不是一个字符串/对象的条数。 */
    val rejected: Int,
    /** 用户挑回来的那份原文：页面确认之后再交回仓库，不必重读文件。 */
    val raw: String = "",
)

/** 只认包名形状的字符串：`a.b.c`，点号分隔、不以点开头结尾。 */
internal fun String.isValidBackupPackage(): Boolean =
    isNotEmpty() &&
        !startsWith('.') &&
        !endsWith('.') &&
        !contains(' ') &&
        all { it.isLetterOrDigit() || it == '.' || it == '_' }