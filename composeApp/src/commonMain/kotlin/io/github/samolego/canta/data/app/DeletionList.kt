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
import kotlinx.serialization.Serializable
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

/** 包名的排序：不分大小写，同一份名单在两台手机上排出来一样。 */
private val PACKAGE_ORDER: Comparator<String> = compareBy { it.lowercase() }

/**
 * 一个「方案」：一次导入进来的那份名单，或者这台手机自己的卸载记录。
 *
 * 用户嘴里的"方案"就是这个：一份可以被带走、也可以在另一台手机上整份装回来的包名清单。
 * [origin] 区分它从哪来——本机的记录（[Origin.LOCAL]）还是别的手机导进来的（[Origin.IMPORTED]）。
 */
@Serializable
data class DeletionPlan(
    /** 判重与删除用；导入时生成，本机那份固定为 [LOCAL_PLAN_ID]。 */
    val id: String,
    val name: String,
    /** 生成 / 导入的那一刻（epoch 毫秒），列表按它倒序。 */
    val createdAt: Long,
    val origin: Origin,
    /** 包名，已去重、已排序。 */
    val packages: List<String>,
) {
    enum class Origin { LOCAL, IMPORTED }
}

/** 本机那份方案的固定 id：它由卸载记录实时算出来，不落盘。 */
const val LOCAL_PLAN_ID = "local"

/**
 * 卸载记录与导入进来的方案。
 *
 * 两件事分开看：
 *  1. **本机删了什么** —— 由 [OperationLogStore] 实时算（[localPlan]），不落盘、不会为空。
 *  2. **别处导进来的方案** —— 每一次导入都留下一个 [DeletionPlan]，可以并存多份，点开看包，
 *     也可以整份恢复。
 *
 * 导出的文件就是其中一个方案的 JSON：
 * ```
 * { "name": "...", "exportedAt": 1780000000000, "packages": ["a", "b"] }
 * ```
 * 导入时容忍三种形状：上面这种对象、直接是数组的 `["pkg"]`、以及旧版只写 `apps` 的对象。
 */
class DeletionListStore(
    private val storage: AppBlobStorage,
    private val platform: CantaPlatform,
    /** 记录的来源：「本机删了什么」全部由它回答。 */
    private val history: OperationLogStore,
) {

    companion object {
        private const val TAG = "DeletionList"

        /** 分享面板 / 下载目录里那一份的名字。 */
        const val FILE_NAME = "canta-uninstall-list.json"

        /** 导出文件的默认名字，也是导入时缺 `name` 之后的名字。 */
        const val DEFAULT_NAME = "我的卸载方案"

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

    private val _plans = MutableStateFlow<List<DeletionPlan>>(emptyList())

    /** 导入进来的方案，最新的在前。本机那份不在里面（它是算出来的）。 */
    val importedPlans: StateFlow<List<DeletionPlan>> = _plans.asStateFlow()

    /** 读一次落盘的那份。 */
    suspend fun ensureReady() {
        mutex.withLock {
            if (loaded) return@withLock
            runCatching {
                storage.read()?.decodeToString()?.let { text ->
                    // 只落导入进来的方案；本机那份每次现算，不占盘。
                    _plans.value = json.decodeFromString(PlanBlob.serializer(), text)
                        .plans
                        .filter { it.packages.isNotEmpty() }
                        .sortedByDescending { it.createdAt }
                }
            }.onFailure { LogUtils.e(TAG, "Could not read the imported plans", it) }
            loaded = true
        }
    }

    /**
     * 本机那份：「删了、到现在还没装回来」的包，由记录实时算出来。
     *
     * 名字里带个数，因为用户看列表时最想知道的就是"这一份有多少条"。
     */
    suspend fun localPlan(): DeletionPlan {
        val removed = runCatching { history.recentlyRemovedPackages() }.getOrDefault(emptyList())
        val packages = removed.filter(::isValidPackageName).distinct().sortedWith(PACKAGE_ORDER)
        return DeletionPlan(
            id = LOCAL_PLAN_ID,
            name = "本机卸载记录",
            createdAt = 0L,
            origin = DeletionPlan.Origin.LOCAL,
            packages = packages,
        )
    }

    /** 所有方案：本机那份在最前，随后是导入进来的（新→旧）。 */
    suspend fun plans(): List<DeletionPlan> {
        ensureReady()
        return listOf(localPlan()) + _plans.value
    }

    /** 按 id 取一份方案；本机那份现算，导入的从内存取。 */
    suspend fun plan(id: String): DeletionPlan? =
        if (id == LOCAL_PLAN_ID) localPlan() else plans().firstOrNull { it.id == id }

    /**
     * 把导入进来的文本存成一个**新方案**，不动已有的。
     *
     * 同名时后面加「(2)」这类后缀，免得列表里两行一模一样分不清。
     */
    suspend fun importAsPlan(parsed: DeletionListImport): DeletionPlan {
        ensureReady()
        val unique = uniqueName(parsed.name)
        val plan = DeletionPlan(
            id = "import-" + currentTimeMillis() + "-" + (0..999).random(),
            name = unique,
            createdAt = currentTimeMillis(),
            origin = DeletionPlan.Origin.IMPORTED,
            packages = parsed.accepted.sortedWith(PACKAGE_ORDER),
        )
        val next = (listOf(plan) + _plans.value).sortedByDescending { it.createdAt }
        mutex.withLock {
            _plans.value = next
            persist(next)
        }
        return plan
    }

    /** 删掉一个导入进来的方案；本机那份删不掉（它跟着记录走）。返回真的删了吗。 */
    suspend fun removePlan(id: String): Boolean {
        ensureReady()
        if (id == LOCAL_PLAN_ID) return false
        val next = _plans.value.filter { it.id != id }
        if (next.size == _plans.value.size) return false
        mutex.withLock {
            _plans.value = next
            persist(next)
        }
        return true
    }

    /** 清掉所有导入进来的方案；返回清掉几份。 */
    suspend fun clearImported(): Int {
        ensureReady()
        val count = _plans.value.size
        mutex.withLock {
            _plans.value = emptyList()
            persist(emptyList())
        }
        return count
    }

    /** 导出用的 JSON 文本。 */
    fun exportJson(plan: DeletionPlan): String {
        val body = buildJsonObject {
            put("name", plan.name)
            put("exportedAt", currentTimeMillis())
            put(
                KEY,
                buildJsonArray {
                    plan.packages.forEach { add(JsonPrimitive(it)) }
                },
            )
        }
        return json.encodeToString(SerializationJsonObject.serializer(), body)
    }

    /**
     * 导出一个方案并让**用户自己挑位置**（系统「存储到」界面）；取消返回 path = null，
     * 不写任何文件。
     */
    suspend fun savePlanAs(plan: DeletionPlan): DeletionListExport {
        val content = exportJson(plan)
        val picked = runCatching { platform.saveFileAs(FILE_NAME, content, JSON_MIME) }.getOrNull()
        return DeletionListExport(packages = plan.packages.size, path = picked)
    }

    /**
     * 把用户挑回来的文本读成一份名单。
     *
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

    /** 同名时加后缀：`我的方案` → `我的方案 (2)`。 */
    private fun uniqueName(base: String): String {
        val taken = _plans.value.mapTo(HashSet()) { it.name }
        if (base !in taken) return base
        var n = 2
        while ("$base ($n)" in taken) n++
        return "$base ($n)"
    }

    private suspend fun persist(plans: List<DeletionPlan>) = withContext(Dispatchers.Default) {
        runCatching {
            storage.write(json.encodeToString(PlanBlob.serializer(), PlanBlob(plans)).encodeToByteArray())
        }.onFailure { LogUtils.e(TAG, "Could not store the imported plans", it) }
        Unit
    }
}

/** 落盘那一份：只装导入进来的方案（本机那份每次现算）。 */
@Serializable
private data class PlanBlob(val plans: List<DeletionPlan> = emptyList())

/** 导入一份名单读出来的结果。 */
data class DeletionListImport(
    /** 合法、去重、排好序的包名。 */
    val accepted: List<String>,
    /** 被丢掉的条数（包名不合法、或者那一行根本不是一个字符串）。 */
    val rejected: Int,
    /** 文件里写的名字；没写就默认名字。 */
    val name: String,
)

/** 导出之后的结果。 */
data class DeletionListExport(
    /** 文件里写了几个包名。 */
    val packages: Int,
    /**
     * 文件落到了哪里（用户挑的那个位置）。null = 用户取消了，或者这个平台没有「存储到」界面，
     * 两种情况都没有写出任何文件。
     */
    val path: String?,
)