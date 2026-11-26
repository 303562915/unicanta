package io.github.samolego.canta.data.app

import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.AppBlobStorage
import io.github.samolego.canta.packages.isValidPackageName
import kotlinx.serialization.json.JsonObject as SerializationJsonObject
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * 删除名单：本机记录的那些"我自己决定要删掉"的包名。
 *
 * 它不是一条操作记录（那是 [io.github.samolego.canta.data.history.OperationLogStore] 的事），
 * 而是一份可以被带走、也可以被带回来的清单：导出成 JSON 留着自己保管，换手机或者重装之后
 * 再导进来。所以这里只做两件事——在原地维护这份名单，以及把它和 JSON 互相翻译。
 *
 * 文件是**纯 JSON**（没有 BOM、没有 CSV）：导出之后可以直接打开读、可以手改一行再导回来。
 * 格式：
 * ```
 * {
 *   "name": "我的删除名单",
 *   "exportedAt": 1780000000000,
 *   "packages": ["com.example.a", "com.example.b"]
 * }
 * ```
 * 导入时容忍三种形状：上面这种对象、直接是数组的 `["pkg"]`、以及旧版只写 `apps` 的对象。
 * 不认识的包名（[isValidPackageName] 不过的）一律丢掉：这些东西最后会进 shell 命令。
 */
class DeletionListStore(
    private val storage: AppBlobStorage,
    private val platform: CantaPlatform,
) {

    companion object {
        private const val TAG = "DeletionList"

        /** 分享面板 / 下载目录里那一份的名字。 */
        const val FILE_NAME = "canta-deletion-list.json"

        /** 导出文件的默认名字，也是导入时缺 `name` 之后的名字。 */
        const val DEFAULT_NAME = "我的删除名单"

        private const val JSON_MIME = "application/json"

        /** 旧版预设导出用的键：同样是一串包名，所以一并认。 */
        private const val LEGACY_KEY = "apps"
        private const val KEY = "packages"
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val mutex = Mutex()
    private var loaded = false

    private val _packages = MutableStateFlow<List<String>>(emptyList())

    /** 名单，按名字排好（大小写不敏感），给界面直接画。 */
    val packages: StateFlow<List<String>> = _packages.asStateFlow()

    /** 读一次落盘的那份；每个入口都先调它。 */
    suspend fun ensureReady() {
        mutex.withLock {
            if (loaded) return@withLock
            runCatching {
                storage.read()?.decodeToString()?.let { text ->
                    _packages.value = json.decodeFromString(ListSerializer(String.serializer()), text)
                        .filter(::isValidPackageName)
                        .distinct()
                        .sortedWith(String.CASE_INSENSITIVE_ORDER)
                }
            }.onFailure { LogUtils.e(TAG, "Could not read the deletion list", it) }
            loaded = true
        }
    }

    /** 整份改写。写进去的会是排好序、去重、只留合法包名的那一份。 */
    suspend fun replace(packages: List<String>) {
        ensureReady()
        val next = packages.filter(::isValidPackageName)
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        mutex.withLock {
            _packages.value = next
            persist(next)
        }
    }

    /** 加进去（已有的不动）；返回真正新增的条数。 */
    suspend fun addAll(packages: List<String>): Int {
        ensureReady()
        val before = _packages.value.toSet()
        val merged = (before + packages.filter(::isValidPackageName))
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        mutex.withLock {
            _packages.value = merged
            persist(merged)
        }
        return merged.size - before.size
    }

    /** 从名单里划掉；返回划掉了几个。 */
    suspend fun remove(packages: Collection<String>): Int {
        ensureReady()
        val gone = packages.toSet()
        val before = _packages.value
        val next = before.filter { it !in gone }
        mutex.withLock {
            _packages.value = next
            persist(next)
        }
        return before.size - next.size
    }

    /** 全清；返回清掉了几个。 */
    suspend fun clear(): Int {
        ensureReady()
        val count = _packages.value.size
        mutex.withLock {
            _packages.value = emptyList()
            persist(emptyList())
        }
        return count
    }

    /** 导出用的 JSON 文本；[name] 只写进文件里，不落盘。 */
    suspend fun exportJson(name: String = DEFAULT_NAME): String {
        ensureReady()
        val body = buildJsonObject {
            put("name", name)
            put("exportedAt", Clock.System.now().toEpochMilliseconds())
            put(
                KEY,
                buildJsonArray {
                    _packages.value.forEach { add(JsonPrimitive(it)) }
                },
            )
        }
        return json.encodeToString(SerializationJsonObject.serializer(), body)
    }

    /**
     * 导出并交给分享面板（没有面板的构建落在下载目录）；返回一句给用户看的话。
     *
     * 两件事都要说清楚：面板开没开、文件里写了几个包名——只回「已导出」，人不知道该去哪点。
     */
    suspend fun shareJson(name: String = DEFAULT_NAME): DeletionListExport {
        val content = exportJson(name)
        val shared = runCatching { platform.shareFile(FILE_NAME, content, JSON_MIME) }
            .getOrDefault(false)
        val path = if (shared) {
            null
        } else {
            runCatching { platform.exportLog(FILE_NAME, content) }.getOrNull()
        }
        return DeletionListExport(
            packages = _packages.value.size,
            shared = shared,
            path = path,
        )
    }

    /**
     * 把用户挑回来的文本读成一份名单。
     *
     * 结果里 [DeletionListImport.accepted] 是按顺序去重之后的合法包名，
     * [DeletionListImport.rejected] 是被丢掉的条数——后者大于 0 时要告诉用户，别悄悄少几条。
     * 文本不是 JSON 时回答 null（调用方据此说「这份文件读不懂」）。
     */
    fun parse(text: String): DeletionListImport? {
        val root = runCatching { json.parseToJsonElement(text.trim()) }.getOrNull() ?: return null
        val elements = when (root) {
            is JsonArray -> root.jsonArray
            is SerializationJsonObject -> {
                val array = root[KEY]?.jsonArray
                    ?: root[LEGACY_KEY]?.jsonArray
                    ?: return null
                array
            }

            else -> return null
        }
        val raw = elements.mapNotNull { element ->
            runCatching { element.jsonPrimitive.content }.getOrNull()
                ?: runCatching { element.jsonObject["packageName"]?.jsonPrimitive?.content }.getOrNull()
        }
        val accepted = raw.filter(::isValidPackageName)
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        val name = (root as? SerializationJsonObject)?.get("name")?.jsonPrimitive?.content.orEmpty()
        return DeletionListImport(
            accepted = accepted,
            rejected = raw.size - accepted.size,
            name = name.ifBlank { DEFAULT_NAME },
        )
    }

    private suspend fun persist(list: List<String>) = withContext(Dispatchers.Default) {
        runCatching {
            storage.write(json.encodeToString(ListSerializer(String.serializer()), list).encodeToByteArray())
        }.onFailure { LogUtils.e(TAG, "Could not store the deletion list", it) }
        Unit
    }
}

/** 导入一份名单读出来的结果。 */
data class DeletionListImport(
    /** 合法、去重、排好序的包名。 */
    val accepted: List<String>,
    /** 被丢掉的条数（包名不合法、或者那一行根本不是一个字符串）。 */
    val rejected: Int,
    /** 文件里写的名字；没写就默认名字。 */
    val name: String,
)

/** 导出之后对用户说的那一句。 */
data class DeletionListExport(
    val packages: Int,
    val shared: Boolean,
    val path: String?,
)