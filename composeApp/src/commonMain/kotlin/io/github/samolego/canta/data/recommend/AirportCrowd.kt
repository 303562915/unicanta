package io.github.samolego.canta.data.recommend

/**
 * 「众筹机场」这一格的数据：一群人合买同一家的订阅，凑够人数就开团。
 *
 * 与 [CommunityAirport] 分开一条数据线，理由和「机场 ≠ 商城」一样：机场投票回答的是
 * 「这家的东西好不好」，众筹回答的是「这单谁一起出钱」。票数够不够决定它上不上推荐，
 * 人数够不够决定这一团成不成，两套门槛混在一张表里，改一边就会牵动另一边。
 *
 * 后端还没推这张表时列表是空的（见 `crowd_airports`），页面说明一句就够，不是报错。
 */
data class CrowdAirport(
    val id: Long = 0L,
    val name: String = "",
    /** 官网或邀请页，可能为空。 */
    val url: String = "",
    /** 每人分摊的原文：`¥18 / 人 / 季`。币种和周期都保留，客户端不算汇率。 */
    val share: String = "",
    val summary: String = "",
    val author: String = "",
    val authorId: String = "",
    val createdAt: String = "",
    /** 凑够几个人开团。 */
    val target: Int = 10,
    /** 已经认筹了几个人。 */
    val joined: Int = 0,
    /** `open` 征集 / `funded` 已凑够 / `closed` 已关闭，认不出的一律按 `open` 画。 */
    val status: String = "open",
    /** 这个身份认过没有。 */
    val pledged: Boolean = false,
    /** 是不是这个身份自己发起的：自己发起的可以看，但不再重复认筹。 */
    val mine: Boolean = false,
) {
    /** 图标方块里的字，与机场条目一致。 */
    val tileMark: String get() = name.trim().take(1).ifBlank { "?" }

    /** 0f–1f；目标人数为 0 时按 0 算，不除以零。 */
    val progress: Float
        get() = if (target <= 0) 0f else (joined.toFloat() / target).coerceIn(0f, 1f)

    /** 一行小字：分摊 · 人数组。空的那几项不画。 */
    val metaLine: String
        get() = listOfNotNull(
            share.ifBlank { null },
            "已认筹 $joined / ${target.coerceAtLeast(1)} 人",
        ).joinToString(" · ")

    /** `open` / `funded` / `closed` 的中文说法。 */
    val statusLabel: String
        get() = when (status.lowercase()) {
            "funded" -> "已凑够"
            "closed" -> "已关闭"
            else -> "征集中"
        }

    /** 还能认筹：没关闭、没凑够、也不是自己发起的。 */
    val joinable: Boolean get() = status.lowercase() == "open" && !mine

    /** 进度那一行：凑够了说成团，还差几个就说差几个。 */
    val progressLabel: String
        get() = when {
            status.equals("funded", ignoreCase = true) -> "人数够了，等发起人开团"
            target > joined -> "还差 ${target - joined} 人"
            else -> "人数够了，等结算"
        }
}

/** 一次认筹之后服务器回答的几个数。 */
data class CrowdPledgeResult(
    val joined: Int = 0,
    val target: Int = 0,
    val status: String = "open",
    val pledged: Boolean = false,
) {
    /** 这一次认筹把这一团凑够了：页面据此说一句「凑够了」。 */
    val funded: Boolean get() = status.equals("funded", ignoreCase = true)
}
