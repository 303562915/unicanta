package io.github.samolego.canta.data.community

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 榜上的一位赞助者。
 *
 * [owner] 是身份号（`#12345` 那种数字），**头像就靠它**：图床上的对象名固定是
 * `unicanta/头像/<owner>.jpg`（见 `ImageBed.avatarId`），所以这一行点了就能进那个人的主页
 * （`Screen.Uploader.routeFor(owner)`）。
 *
 * [name] 是榜上显示的名字；匿名的人后端下发空串，页面画「匿名」。[anonymous] 用来决定画哪个。
 */
@Serializable
data class SponsorEntry(
    val id: Long,
    val owner: String,
    val name: String,
    val amountYuan: Int,
    /** token 赞助的数量（[kind] = `ai`）；资金赞助恒为 0（金额走 [amountYuan]）。 */
    val tokenCount: Int = 0,
    val kind: String,
    val anonymous: Boolean,
    val createdAt: String,
) {
    /** 页面上的形式标签。 */
    val kindLabel: String
        get() = when (kind) {
            "ai" -> "token 赞助"
            "other" -> "其它支持"
            else -> "打赏"
        }

    /**
     * 榜上那一格下面写什么：出钱的写金额，token 赞助写出数量，两者都没有才写赞助方式。
     *
     * 只写「token 赞助」看不出帮了多少，6000 和 6 万在同一格子里长得一样。
     */
    val badge: String
        get() = when {
            amountYuan > 0 -> "¥$amountYuan"
            tokenCount > 0 -> "$tokenCount token"
            else -> "token 赞助"
        }

    /** 这一格有没有具体数量（钱或 token）：有就用强调色，没有用次要色。 */
    val hasCount: Boolean
        get() = amountYuan > 0 || tokenCount > 0

    /** 身份号能不能点（进主页）：空串、或者不是数字（老客户端的自报号码）都不给入口。 */
    val canOpenProfile: Boolean
        get() = owner.isNotEmpty() && owner.all { it.isDigit() }
}

/**
 * 一次排行榜：总额、人数、两种形式的条数，以及榜本身。
 *
 * 全部字段缺了就退回 0 / 空表：老后端没有这几个键时页面仍然画得出来（只是空榜）。
 */
@Serializable
data class SponsorWall(
    val totalYuan: Long = 0L,
    val count: Int = 0,
    val moneyCount: Int = 0,
    val aiCount: Int = 0,
    val entries: List<SponsorEntry> = emptyList(),
)

/** 我提交过的一笔（含已经撤掉的）。 */
data class SponsorMine(
    val id: Long,
    val amountYuan: Int,
    val kind: String,
    val hidden: Boolean,
    val createdAt: String,
)

/**
 * 一次记账的结果。[duplicate] 为真表示"这一笔已经在榜上了"（24 小时内同身份同金额同形式）。
 */
data class SponsorAddResult(val id: Long, val duplicate: Boolean)

/**
 * 赞助榜的数据层：一个 [CommunityRpc] 加四个 RPC，没有本地缓存。
 *
 * 与 `CdkStore` 分开成两个文件是两个并行的改动互不撞车的唯一办法（`CommunityRpc` 的注释里
 * 也是这么说的）；失败语义也照它：写方法返回 null 并留下 [lastError]，读方法返回空榜。
 */
class SponsorStore(private val rpc: CommunityRpc) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 上一次调用为什么没成：后端的一句话（拒绝）或 null（网络没通）。 */
    val lastError: String?
        get() = rpc.lastError

    /** 上一次失败是不是"根本没答"，而不是"答了但拒绝"。 */
    val lastFailureWasNetwork: Boolean
        get() = rpc.lastFailureWasNetwork

    /** 这个部署还没有赞助榜那几个函数（老后端）：页面据此藏起这一块，而不是弹红字。 */
    val functionMissing: Boolean
        get() = rpc.functionMissing

    /**
     * 榜单。失败时返回 null，让页面分清"榜是空的"和"没拉到"——一个刚上线的榜很可能是真的空的。
     */
    suspend fun wall(): SponsorWall? {
        val text = rpc.post("sponsor_wall", "{}") ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            SponsorWall(
                totalYuan = o["total_yuan"]?.jsonPrimitive?.longOrNull ?: 0L,
                count = o["count"]?.jsonPrimitive?.intOrNull ?: 0,
                moneyCount = o["money_count"]?.jsonPrimitive?.intOrNull ?: 0,
                aiCount = o["ai_count"]?.jsonPrimitive?.intOrNull ?: 0,
                entries = o["entries"]?.jsonArray?.map(::parseEntry).orEmpty(),
            )
        }.getOrNull()
    }

    /**
     * 记一笔赞助。
     *
     * [amountYuan] 只在 [kind] 是 `money` 时生效；[anonymous] 为真时榜上显示「匿名」。
     * 后端认不出身份时返回 null（也拿不到署名），这里同样返回 null。
     */
    suspend fun add(
        amountYuan: Int,
        kind: String,
        name: String,
        note: String = "",
        anonymous: Boolean = false,
    ): SponsorAddResult? {
        val proof = rpc.token()
        val body = buildJsonObject {
            put("p_amount_yuan", amountYuan)
            put("p_kind", kind)
            put("p_name", name)
            put("p_note", note)
            put("p_anonymous", anonymous)
            put("p_owner", "")
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("sponsor_add", body) ?: return null
        // 后端认不出身份 / 打赏金额记成 0 时回的是 JSON `null`：那不是"网络没通"，
        // 让它落到 null 之后上层会拿 lastError（此时为空）说"检查网络"，把人指错方向。
        if (text.trim() == "null") return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            SponsorAddResult(
                id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                duplicate = o["duplicate"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }.getOrNull()
    }

    /** 把一笔从榜上摘掉（只能摘自己的）。true = 摘掉了。 */
    suspend fun hide(id: Long): Boolean {
        if (id <= 0L) return false
        val proof = rpc.token()
        val body = buildJsonObject {
            put("p_id", id)
            put("p_owner", "")
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("sponsor_hide", body) ?: return false
        return runCatching {
            json.parseToJsonElement(text).jsonPrimitive.booleanOrNull == true
        }.getOrDefault(false)
    }

    /** 我提交过的（含撤掉的），最近的在前。 */
    suspend fun mine(): List<SponsorMine> {
        val proof = rpc.token()
        val body = buildJsonObject {
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("sponsor_mine", body) ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(text).jsonArray.map { element ->
                val o = element.jsonObject
                SponsorMine(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    amountYuan = o["amount_yuan"]?.jsonPrimitive?.intOrNull ?: 0,
                    kind = o["kind"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    hidden = o["hidden"]?.jsonPrimitive?.booleanOrNull == true,
                    createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 榜上的一行；字段缺了就退回默认值，老回答仍然画得出来。 */
    private fun parseEntry(element: kotlinx.serialization.json.JsonElement): SponsorEntry {
        val o = element.jsonObject
        return SponsorEntry(
            id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            owner = o["owner"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            amountYuan = o["amount_yuan"]?.jsonPrimitive?.intOrNull ?: 0,
            tokenCount = o["token_count"]?.jsonPrimitive?.intOrNull ?: 0,
            kind = o["kind"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "money" },
            anonymous = o["anonymous"]?.jsonPrimitive?.booleanOrNull == true,
            createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }
}