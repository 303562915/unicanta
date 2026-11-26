package io.github.samolego.canta.data.app

import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.AppBlobStorage
import io.github.samolego.canta.data.history.OperationLogStore
import io.github.samolego.canta.packages.isValidPackageName
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.currentTimeMillis
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
import kotlinx.serialization.json.JsonObject as SerializationJsonObject

/** 名单的排序：不分大小写，同一份名单在两台手机上排出来一样。 */
private val PACKAGE_ORDER: Comparator<String> = compareBy { it.lowercase() }

/**
 * 删除名单：**真的被删掉、到现在还没装回来**的那些包名。
 *
 * 它没有自己的一份清单——真相在 [OperationLogStore] 的卸载记录里。这里做的是：
 * 把记录读成"这台手机现在缺哪些包"，再把它写成一份可以被带走、也可以被带回来的 JSON。
 * 导出 → 换手机/重装 → 导入，这样下一台手机上就知道上一台删过什么。
 *
 * 为什么不再自己存一份：自己存的那份永远是空的——没人会去手动往里面加包名，导出来就是零。
 * 记录本来就在，而且每次卸载都在写。
 *
 * 文件是纯 JSON：
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
    /** 记录的来源；「已经删掉的」全部由它回答。 */
    private val history: OperationLogStore,
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

    /**
     * 上一台手机上留下来、这一台还没有的包（导入进来的那些）。真正"已删除"的名单在
     * [current] 里，那是记录算出来的，不落盘。
     */
    val imported: StateFlow<List<String>> = _packages.asStateFlow()

    /**
     * 现在导出的会是哪些包：记录里删了、到现在还没装回来的那些，加上导入进来、这台还没有的。
     *
     * 顺序按包名排，重复的并掉。
     */
    suspend fun current(): List<String> {
        ensureReady()
        val removed = runCatching { history.recentlyRemovedPackages() }.getOrDefault(emptyList())
        return (removed + _packages.value)
            .filter(::isValidPackageName)
            .distinct()
            .sortedWith(PACKAGE_ORDER)
    }

    /** 读一次导入进来的那份；每个入口都先调它。 */
    suspend fun ensureReady() {
        mutex.withLock {
            if (loaded) return@withLock
            runCatching {
                storage.read()?.decodeToString()?.let { text ->
                    _packages.value = json.decodeFromString(ListSerializer(String.serializer()), text)
                        .filter(::isValidPackageName)
                        .distinct()
                        .sortedWith(PACKAGE_ORDER)
                }
            }.onFailure { LogUtils.e(TAG, "Could not read the imported deletion list", it) }
            loaded = true
        }
    }

    /** 加进来（已有的不动）；返回真正新增的条数。 */
    suspend fun addAll(packages: List<String>): Int {
        ensureReady()
        val before = _packages.value.toSet()
        val merged = (before + packages.filter(::isValidPackageName))
            .sortedWith(PACKAGE_ORDER)
        mutex.withLock {
            _packages.value = merged
            persist(merged)
        }
        return merged.size - before.size
    }

    /** 把导入进来的那部分划掉；返回划掉了几个。记录里那份名单不受影响。 */
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

    /** 清掉导入进来的那部分；返回清掉了几个。 */
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
        val packages = current()
        val body = buildJsonObject {
            put("name", name)
            put("exportedAt", currentTimeMillis())
            put(
                KEY,
                buildJsonArray {
                    packages.forEach { add(JsonPrimitive(it)) }
                },
            )
        }
        return json.encodeToString(SerializationJsonObject.serializer(), body)
    }

    /**
     * 导出到用户挑的位置；取消（或者平台没有这个界面）返回 path = null，不写任何文件。
     */
    suspend fun saveJsonAs(name: String = DEFAULT_NAME): DeletionListExport {
        val packages = current()
        val content = exportJson(name)
        val picked = runCatching { platform.saveFileAs(FILE_NAME, content, JSON_MIME) }.getOrNull()
        return DeletionListExport(packages = packages.size, path = picked)
    }

    /**
     * 把用户挑回来的文本读成一份名单。
     *
     * [DeletionListImport.accepted] 是按顺序去重之后的合法包名，[DeletionListImport.rejected]
     * 是被丢掉的条数——后者大于 0 时要告诉用户，别悄悄少几条。
     * 文本不是 JSON 时回答 null（调用方据此说「这份文件读不懂」）。
     */
    fun parse(text: String): DeletionListImport? {
        val root = runCatching { json.parseToJsonElement(text.trim()) }.getOrNull() ?: return null
        val elements = when (root) {
            is JsonArray -> root.jsonArray
            is SerializationJsonObject -> {
                root[KEY]?.jsonArray
                    ?: root[LEGACY_KEY]?.jsonArray
                    ?: return null
            }

            else -> return null
        }
        val raw = elements.mapNotNull { element ->
            runCatching { element.jsonPrimitive.content }.getOrNull()
                ?: runCatching { element.jsonObject["packageName"]?.jsonPrimitive?.content }.getOrNull()
        }
        // 被丢掉的条数：原始条数减去留下来的条数（不合法的和重复的都算）。
        val distinctRaw = raw.filter(::isValidPackageName).distinct()
        val accepted = distinctRaw.sortedWith(PACKAGE_ORDER)
        val name = (root as? SerializationJsonObject)?.get("name")?.jsonPrimitive?.content.orEmpty()
        return DeletionListImport(
            accepted = accepted,
            rejected = raw.size - distinctRaw.size,
            name = name.ifBlank { DEFAULT_NAME },
        )
    }

    private suspend fun persist(list: List<String>) = withContext(Dispatchers.Default) {
        runCatching {
            storage.write(json.encodeToString(ListSerializer(String.serializer()), list).encodeToByteArray())
        }.onFailure { LogUtils.e(TAG, "Could not store the imported deletion list", it) }
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
    /** 文件里写了几个包名。 */
    val packages: Int,
    /**
     * 文件落到了哪里（用户挑的那个位置）。null = 用户取消了，或者这个平台没有「存储到」界面，
     * 两种情况都没有写出任何文件。
     */
    val path: String?,
)