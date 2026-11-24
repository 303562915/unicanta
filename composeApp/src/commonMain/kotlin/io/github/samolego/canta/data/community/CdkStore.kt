package io.github.samolego.canta.data.community

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 我取走的一个码：哪件商品、什么标题、码本身、什么时候取的。
 *
 * 订单里也有这份快照（`point_orders.deliverable`），但这一条读的是码池，带商品标题，页面直接能画。
 */
data class CdkCode(
    val id: Long,
    val offerId: Long,
    val title: String,
    val code: String,
    val takenAt: String,
)

/**
 * 货架上的一件商品，只取兑换中心画得出来的那几栏。
 *
 * [seller] 是卖家写的署名（后端 `seller` 那一栏），不是身份码：判断"是不是我挂的"用 [mine]，
 * 别拿名字去比。
 */
data class CdkOffer(
    val id: Long,
    val title: String,
    val detail: String,
    val price: Int,
    val stock: Int,
    val kind: String,
    val seller: String,
    val sold: Int,
    val mine: Boolean,
    /** 还有码可兑：没卖完、也没被下架。 */
    val available: Boolean,
) {
    /** 类型的一句话名字，页面上的小标签用它。 */
    val kindLabel: String
        get() = when (kind) {
            "airport" -> "订阅"
            "invite" -> "邀请码"
            "account" -> "账号"
            else -> "其它"
        }
}

/** 一次上架的结果：[codes] 是真正入库的码条数，不是粘进去的行数（重复的会并掉）。 */
data class CdkPublishResult(val id: Long, val codes: Int)

/**
 * 一次兑换的结果。
 *
 * [ok] 为 false 时 [error] 是后端说的那一句人话（积分不够、码已经发完了……），不是网络失败；
 * 网络失败这个方法整个返回 null。
 */
data class CdkRedeemResult(
    val ok: Boolean,
    val balance: Long,
    val code: String,
    val title: String,
    val price: Int,
    val error: String,
)

/**
 * CDK 兑换中心的数据层：一个 [CommunityRpc] 加四个 RPC，没有本地缓存。
 *
 * 为什么不塞进 `PlanRepository`：那个类一千七百多行，推荐页的新块各自成文件各自有自己的仓储，
 * 两个文件同时改它必然撞车（`CommunityRpc` 的注释里也是这么说的）。
 *
 * 失败语义与 `PlanRepository` 一致：被拒或根本没通，写方法返回 null，理由留在 [lastError]
 * （后端自己的一句话）或 [lastFailureWasNetwork]（没答过，多半是代理 / 网络）。读方法返回空表，
 * 因为"没有码"和"没拉到"在这一块是同一件可显示的事，页面不该为此转圈。
 */
class CdkStore(private val rpc: CommunityRpc) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 上一次调用为什么没成：后端的一句话（拒绝）或 null（网络没通）。原样透出去给页面用。 */
    val lastError: String?
        get() = rpc.lastError

    /** 上一次失败是不是"根本没答"，而不是"答了但拒绝"。 */
    val lastFailureWasNetwork: Boolean
        get() = rpc.lastFailureWasNetwork

    /** 这个部署还没有 CDK 那几个函数（老后端）：页面据此藏起这一块，而不是弹红字。 */
    val functionMissing: Boolean
        get() = rpc.functionMissing

    /**
     * 我的积分余额。认不出身份时后端答 0，这里也就是 0，不是 null。
     *
     * 走 `points_account`：它的 `balance` 与 `point_offers` 里那份是同一个数，但只读钱的时候
     * 不必把整个货架拉下来。
     */
    suspend fun balance(): Long? {
        val proof = rpc.token()
        val body = buildJsonObject {
            put("p_owner", "")
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("points_account", body) ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonObject["balance"]?.jsonPrimitive?.longOrNull ?: 0L
        }.getOrNull()
    }

    /**
     * 货架：所有在架商品，价格低的在前（后端排好序）。
     *
     * 列表接口给的是 `sold_out` 与 `mine` 两个布尔，[CdkOffer.available] 由它们和 `active` 算出来——
     * 页面不需要知道"卖完"是库存为 0 还是被下架。
     */
    suspend fun offers(): List<CdkOffer>? {
        val proof = rpc.token()
        val body = buildJsonObject {
            put("p_owner", "")
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("point_offers", body) ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonObject["offers"]?.jsonArray?.map(::parseOffer).orEmpty()
        }.getOrNull()
    }

    /**
     * 贴一批码上架：[codes] 是一整段文本，一行一个（后端也认逗号和空白）。
     *
     * 价格由后端夹在 1–5000 之间；一条有效码都没有时后端会拒绝，理由在 [lastError] 里。
     */
    suspend fun publish(
        title: String,
        detail: String,
        price: Int,
        codes: String,
        kind: String,
        ownerName: String,
    ): CdkPublishResult? {
        if (title.isBlank() || codes.isBlank()) return null
        val proof = rpc.token()
        val body = buildJsonObject {
            put("p_title", title)
            put("p_detail", detail)
            put("p_price", price)
            put("p_codes", codes)
            put("p_kind", kind)
            // 署名，不是身份：后端只用它当挂东西的人写的名字，身份取自 token。
            put("p_owner", ownerName)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("point_offer_publish_codes", body) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            CdkPublishResult(
                id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                codes = o["codes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            )
        }.getOrNull()
    }

    /**
     * 兑换一件商品：买家扣分、卖家全额到账、有码池的话从池子里取走一个码。
     *
     * 拒绝（分不够、码已经发完了、自己挂的、已下架）不是网络失败：后端把理由写在 `error` 里，
     * 这里原样带回去给页面显示。
     */
    suspend fun redeem(offerId: Long): CdkRedeemResult? {
        if (offerId <= 0L) return null
        val proof = rpc.token()
        val body = buildJsonObject {
            put("p_offer_id", offerId)
            put("p_owner", "")
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("point_offer_redeem", body) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            CdkRedeemResult(
                ok = o["ok"]?.jsonPrimitive?.booleanOrNull == true,
                balance = o["balance"]?.jsonPrimitive?.longOrNull ?: 0L,
                // 有码池的商品把码放在 `code` 里，`deliverable` 是同一份快照（老客户端读它）；
                // 没有码池的商品只有 `deliverable`。
                code = o["code"]?.jsonPrimitive?.contentOrNull
                    ?: o["deliverable"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                price = o["price"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                error = o["error"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.getOrNull()
    }

    /**
     * 我取走的码，最近的在前，最多 100 条。
     *
     * 失败时是空表而不是 null：这一块"没有东西"和"没拉到"都只能画成一句提示，用 null 会让
     * 调用方每处都要判两遍。
     */
    suspend fun myCodes(): List<CdkCode> {
        val proof = rpc.token()
        val body = buildJsonObject {
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("point_my_codes", body) ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(text).jsonArray.map { element ->
                val o = element.jsonObject
                CdkCode(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    offerId = o["offer_id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    code = o["code"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    takenAt = o["taken_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 货架上的一件商品；字段缺了就退回默认值，老回答仍然画得出来（照 `PlanRepository.parsePointOffer` 的写法）。 */
    private fun parseOffer(element: kotlinx.serialization.json.JsonElement): CdkOffer {
        val o = element.jsonObject
        val stock = o["stock"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0
        val soldOut = o["sold_out"]?.jsonPrimitive?.booleanOrNull == true
        val active = o["active"]?.jsonPrimitive?.booleanOrNull != false
        return CdkOffer(
            id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            detail = o["detail"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            price = o["price"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            stock = stock,
            kind = o["kind"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "other" },
            seller = o["seller"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            sold = o["sold"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            mine = o["mine"]?.jsonPrimitive?.booleanOrNull == true,
            available = active && !soldOut && stock > 0,
        )
    }
}
