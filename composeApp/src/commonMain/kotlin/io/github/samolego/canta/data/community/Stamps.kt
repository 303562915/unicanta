package io.github.samolego.canta.data.community

import io.github.samolego.canta.util.currentTimeMillis

/**
 * 社区给一个包盖的「章」：两字定罪，比如 毒瘤 / 病毒 / 远控 / 广告。
 *
 * 与级别修正的区别：级别回答「该不该删、删了会不会出事」，章回答「它犯了什么罪」。两件事可以
 * 同时成立（一个包可能既标着「不推荐卸载」，又被盖了「广告」），所以章是独立的一组票，不写进
 * `level_fix_ballots` 那三个维度里。
 *
 * 只收有罪的章：清白、正常、可保留这些不给章——没被盖就是没问题，不另发明一枚「好人章」。
 */
object Stamps {

    /** 主章要多少票；后端答复里带 `needed` 时以答复为准。 */
    const val NEEDED: Int = 12

    /** 主章占该包全部章票的比例；后端答复里带 `share_needed` 时以答复为准。 */
    const val SHARE_NEEDED: Float = 0.55f

    /**
     * 首发只上六个，全部是两字、定罪明确、不用解释就懂。
     *
     * 顺序就是页面上的顺序：先黑后橙。改这个列表要同时改后端 `package_stamp_votes.title` 的
     * 约束（见 `.github/sql/package_stamps.sql`），否则新词投上去会被静默拒绝。
     */
    val TITLES: List<String> = listOf(
        "毒瘤",
        "病毒",
        "远控",
        "木马",
        "广告",
        "弹窗",
    )
}

/** 某一个章在这个包上拿了多少票。 */
data class StampCount(
    val title: String = "",
    val votes: Int = 0,
    /** 占该包全部章票的比例，0f–1f；没人投时为 0。 */
    val share: Float = 0f,
    /** 本机投的是不是这一枚；一人一包只有一枚是 true。 */
    val mine: Boolean = false,
)

/**
 * 一个包的印章现状。
 *
 * [applied] 非空表示这枚已经盖到详情页上了（票够了）；[appliedVotes] 是盖章那一刻的票数，
 * 后面还在涨，但章不会因为涨票换一枚。
 */
data class StampSummary(
    val counts: List<StampCount> = emptyList(),
    val applied: String = "",
    val appliedVotes: Int = 0,
    val needed: Int = Stamps.NEEDED,
    val shareNeeded: Float = Stamps.SHARE_NEEDED,
) {
    /** 这个包收到的章票总数（所有标题加起来）。 */
    val total: Int get() = counts.sumOf { it.votes }

    /** 本机投的那一枚；还没投是 null。 */
    val mine: String? get() = counts.firstOrNull { it.mine }?.title

    /** 票最多的一枚；一枚都没人投时是 null。 */
    val top: StampCount? get() = counts.filter { it.votes > 0 }.maxByOrNull { it.votes }

    fun votesOf(title: String): Int = counts.firstOrNull { it.title == title }?.votes ?: 0

    fun shareOf(title: String): Float = counts.firstOrNull { it.title == title }?.share ?: 0f
}

/**
 * 按包名的「上次拿到的印章现状」，进程级，5 分钟过期。
 *
 * 为什么要有它：详情页已经是一次四个请求，盖章这件事**不能**让它变成五个——每一次点开一个包都
 * 多发一趟，就是几万次调用里再添一份。所以详情页先读这里，没有才问后端；在「级别修正」页投完
 * 一票之后前端把新计数直接塞进来，两边看到的是同一份，详情页立刻能盖上去。
 */
internal object StampCache {
    private const val MAX_PACKAGES = 64
    private const val TTL_MS = 5 * 60 * 1000L

    private class Entry(val at: Long, val summary: StampSummary)

    private val entries = LinkedHashMap<String, Entry>(MAX_PACKAGES, 0.75f, true)

    fun get(packageName: String): StampSummary? = synchronized(entries) {
        val entry = entries[packageName] ?: return null
        if (currentTimeMillis() - entry.at > TTL_MS) {
            entries.remove(packageName)
            null
        } else {
            entry.summary
        }
    }

    fun put(packageName: String, summary: StampSummary) = synchronized(entries) {
        entries[packageName] = Entry(currentTimeMillis(), summary)
        while (entries.size > MAX_PACKAGES) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }
}
