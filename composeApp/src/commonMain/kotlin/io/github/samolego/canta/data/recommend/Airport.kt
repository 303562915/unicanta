package io.github.samolego.canta.data.recommend

import kotlinx.serialization.Serializable
import kotlin.math.round

/**
 * 推荐页第三块的数据：机场（订阅分享 + 投票 + 打分）和积分（签到 + 商城）。
 *
 * 这些行都来自社区后端（`airport_entries` / `results` / `point_offers` 那几个函数），后端还没
 * 推上去时页面拿到的是空表，而不是崩溃——每个字段都有默认值，少一个键就退回默认值。
 */

/** 一个被分享出来的机场 / VPN / 自建节点，以及它在别人眼里的票和星。 */
data class CommunityAirport(
    val id: Long = 0L,
    val name: String = "",
    /** `airport` 机场、`vpn` 商业 VPN、`self` 自建；不认识的按机场画。 */
    val kind: String = "airport",
    /** 官网或邀请页，可能为空。 */
    val url: String = "",
    val summary: String = "",
    val author: String = "",
    val authorId: String = "",
    val createdAt: String = "",
    /** 认同「值得看」的票数。 */
    val agrees: Int = 0,
    /** 不认同的票数。 */
    val disagrees: Int = 0,
    /** 这个身份投了什么：true 认同、false 不认同、null 没投。 */
    val mine: Boolean? = null,
    /** 平均星，0 表示还没有人打分。 */
    val stars: Float = 0f,
    val ratingCount: Int = 0,
    /** 这个身份打了几星，0 表示没打过。 */
    val myStars: Int = 0,
    /** 这条有没有订阅链接可取（链接本身不在列表里，要先投票或打分）。 */
    val hasSubscription: Boolean = false,
    /**
     * 详情：价格、流量、速度、协议、备注里的一段。列表只显示一行小字，点开卡片才连这份一起画。
     *
     * 它是列表回答里带的，不额外跑一趟网络——所以是一个字段而不是一次查询。
     */
    val detail: AirportDetail = AirportDetail(),
) {
    /** 图标方块里的字，与目录条目一致。 */
    val tileMark: String get() = name.trim().take(1).ifBlank { "?" }

    val kindLabel: String get() = when (kind.lowercase()) {
        "vpn" -> "VPN"
        "self" -> "自建"
        else -> "机场"
    }

    val votes: Int get() = agrees + disagrees

    /** 「★4.2 · 12 人评分」里的前半段，没人评分时是空串。 */
    val starsText: String
        get() {
            if (ratingCount <= 0) return ""
            val tenths = round(stars * 10f).toInt()
            return "★${tenths / 10}.${tenths % 10}"
        }

    /** 一行小字：类型 · 评分 · 票数，缺哪项就少哪项。 */
    val metaLine: String
        get() = listOfNotNull(
            kindLabel,
            starsText.ifEmpty { null }?.let { "$it · $ratingCount 人评分" },
            if (votes > 0) "$agrees 认同 / $disagrees 不认同" else null,
        ).joinToString(" · ")

    /**
     * 上一次列表回来时，这个身份算不算「参与过」（投过票或打过分）。
     *
     * 只作展示上的参考：能不能取订阅由服务端按 token 现算，页面不拿这份快照去挡人——列表
     * 可能是在投票之前拉的，用旧快照拦人会把刚投完票的人挡在门外。
     */
    val unlocked: Boolean get() = mine != null || myStars > 0
}

/**
 * 一条机场摊开看时多出来的那几格：价格、流量、速度、设备数、协议和一段更长的备注。
 *
 * 全都可以为空。那些为空的是"这家没说"，不是"零"：页面据此少画一行，而不是写一个 0。
 */
data class AirportDetail(
    /** 参考价格原文：`¥6 / 月`、`≈ $0.6 / 月`、`¥208.95 按量`，币种和周期都保留。 */
    val price: String = "",
    /** 流量原文：`1000GB/月`、`300GB 总量`。 */
    val traffic: String = "",
    /** 速度上限原文：`3Gbps`、`不限速`。 */
    val speed: String = "",
    /** 设备数原文：`20 台`、`不限制`。 */
    val devices: String = "",
    /** 协议，逗号分隔：`hysteria2, vless`；自建节点多半是空的。 */
    val protocols: String = "",
    /** 更长的介绍：官网、参考汇率、营业状态之类的说明。 */
    val note: String = "",
) {
    /** 有没有任何一格能画：空的详情不占地方，也不显示一条空的分隔线。 */
    val isEmpty: Boolean
        get() = price.isBlank() && traffic.isBlank() && speed.isBlank() &&
            devices.isBlank() && protocols.isBlank() && note.isBlank()
}

/** 一次投票之后服务器回答的三个数。 */
data class AirportVoteResult(
    val agrees: Int = 0,
    val disagrees: Int = 0,
    val mine: Boolean? = null,
)

/** 一次打分之后服务器回答的平均分与人数。 */
data class AirportRatingResult(
    val stars: Float = 0f,
    val ratingCount: Int = 0,
    val myStars: Int = 0,
)

/** 订阅链接的取用结果：没解锁时 [subscribeUrl] 是空串。 */
data class AirportSubscription(
    val unlocked: Boolean = false,
    val subscribeUrl: String = "",
)

/**
 * 一个人的钱包。
 *
 * 积分只由两件事产生：每天签到 +10、别人兑换你挂的东西。花出去只有一件事：兑换别人的东西。
 */
data class PointsAccount(
    val owner: String = "",
    val balance: Long = 0L,
    /** 累计进账与累计花掉，只用来在钱包卡上说清"这些分哪来的"。 */
    val earned: Long = 0L,
    val spent: Long = 0L,
    /** 服务器算的「今天」，按 Asia/Shanghai，不是手机时钟。 */
    val day: String = "",
    /** 今天签到能拿多少分。 */
    val reward: Int = DEFAULT_CHECK_IN_REWARD,
    val checkedInToday: Boolean = false,
    /** 连签了几天。 */
    val streak: Int = 0,
    val todayPoints: Int = 0,
) {
    companion object {
        /** 签到一次的分数，与后端 `private.point_checkin_reward()` 一致。 */
        const val DEFAULT_CHECK_IN_REWARD = 10
    }
}

/** 一次签到的回答：今天加了还是早就加过了。 */
data class CheckInResult(
    val awarded: Int = 0,
    val balance: Long = 0L,
    val already: Boolean = false,
    val reward: Int = PointsAccount.DEFAULT_CHECK_IN_REWARD,
    val day: String = "",
)

/** 货架上的一件商品。交付内容不在这一层，兑换成功才拿得到。 */
@Serializable
data class PointOffer(
    val id: Long = 0L,
    val title: String = "",
    val detail: String = "",
    val price: Int = 0,
    val stock: Int = 0,
    /** `airport` 订阅 / `invite` 邀请码 / `account` 账号 / `other` 其它。 */
    val kind: String = "other",
    /** 收积分的身份码：兑换的分记在它头上。 */
    val sellerId: String = "",
    /** 挂东西的人写的署名。 */
    val seller: String = "",
    val active: Boolean = true,
    val sold: Int = 0,
    /** 是不是这个身份自己挂的：自己挂的不能自己兑。 */
    val mine: Boolean = false,
    val soldOut: Boolean = false,
    val createdAt: String = "",
) {
    val kindLabel: String get() = when (kind.lowercase()) {
        "airport" -> "订阅"
        "invite" -> "邀请码"
        "account" -> "账号"
        else -> "其它"
    }

    /** 能不能按：下架、卖完、自己挂的都不行。 */
    val buyable: Boolean get() = active && !soldOut && !mine
}

/** 货架 + 我的余额，一次读回来的那一份。 */
data class PointShelf(
    val balance: Long = 0L,
    val offers: List<PointOffer> = emptyList(),
)

/** 兑换的结果：失败时说人话的 [error]，成功时给出交付内容。 */
data class PointRedeemResult(
    val ok: Boolean = false,
    val balance: Long = 0L,
    val deliverable: String = "",
    val title: String = "",
    val price: Int = 0,
    val orderId: Long = 0L,
    val error: String = "",
)

/** 我兑换过的一件东西，交付内容是订单当时的快照。 */
@Serializable
data class PointOrder(
    val id: Long = 0L,
    val offerId: Long = 0L,
    val title: String = "",
    val price: Int = 0,
    /** 卖家的身份码（署名不在这里，订单本来就是给买家的收据）。 */
    val sellerId: String = "",
    val deliverable: String = "",
    val createdAt: String = "",
)

/** 钱包流水的上面几条：正数是进账。 */
data class PointLedgerEntry(
    val id: Long = 0L,
    val delta: Long = 0L,
    val reason: String = "",
    val ref: String = "",
    val createdAt: String = "",
) {
    val reasonLabel: String get() = when (reason) {
        "check_in" -> "每日签到"
        "redeem" -> "兑换"
        "sold" -> "卖出"
        else -> reason.ifBlank { "变动" }
    }
}
