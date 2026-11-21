package io.github.samolego.canta.data.community

/**
 * 消息中心每栏各自的已读位置。
 *
 * 消息中心是四栏（回复 / 方案评论 / 赞），但已经读到哪里原来只有一个值：一个瞬间，四栏共用。
 * 于是把回复栏点开，其它三栏的红点跟着一起清掉，"读过"被记成了"四栏都读过"。
 *
 * 这里把一个值拆成四个，一栏一个：后端 `inbox_seen` 表一栏一行，计数按各栏自己的位置算
 * （`inbox_summary_kinds`），写回也用各栏自己的位置（`inbox_seen_mark`）。旧的后端只有
 * `inbox_summary`，四栏只能一起走，那时本对象仍然只有一个位置——见 [PlanRepository.inboxSummary]。
 *
 * 位置是后端盖的 UTC 瞬间（`YYYY-MM-DDTHH:MM:SS.uuuuuuZ`），所以按字符串比较就是按时间比较，
 * 和手机时钟无关；空字符串是"从来没读过"。
 *
 * ## 旧值的迁移
 *
 * 旧版本在本地只存了 `inboxSeenAt` 一个瞬间。[decode] 见到一个不是本类新格式的字符串，就把它
 * 当成**四栏都已读到那个位置**：这是旧行为逐字重现——那个瞬间之前的新闻旧版本本来就不亮红点，
 * 拆成四栏之后也不该突然冒出来，否则升级一次就炸出一堆"旧红点"，而它们其实早就看过了。
 *
 * ## 为什么走字符串
 *
 * 界面对这个值只有搬运：`AppNoteStore.inboxSeenAt` → `PlanRepository.inboxSummary(seenAt)`，
 * 回来的 `InboxSummary.checkedAt` 再被搬回 `AppNoteStore.markInboxSeen`。把它编码成一个字符串，
 * 界面一行都不用改就拿到了四栏。（点栏这件事界面没有传出来，见 [InboxFilter.kinds]。）
 *
 * 编码：`k2;reply=<瞬间>;plan_comment=<瞬间>;comment_heart=<瞬间>;plan_heart=<瞬间>`。
 * 空串表示四栏都没读过——旧语义里"空 = 从头算"照旧成立。瞬间里没有 `;` 和 `=`，所以这个格式
 * 不需要转义。
 */
data class InboxCursors(
    val reply: String = "",
    val planComment: String = "",
    val commentHeart: String = "",
    val planHeart: String = "",
) {

    /** [kind] 这一栏读到哪里。 */
    operator fun get(kind: InboxKind): String = when (kind) {
        InboxKind.REPLY -> reply
        InboxKind.PLAN_COMMENT -> planComment
        InboxKind.COMMENT_HEART -> commentHeart
        InboxKind.PLAN_HEART -> planHeart
    }

    /** 原样写入 [kind] 这一栏；空串就是"没读过"。[with] 是往前推的那个版本。 */
    fun put(kind: InboxKind, at: String): InboxCursors = when (kind) {
        InboxKind.REPLY -> copy(reply = at)
        InboxKind.PLAN_COMMENT -> copy(planComment = at)
        InboxKind.COMMENT_HEART -> copy(commentHeart = at)
        InboxKind.PLAN_HEART -> copy(planHeart = at)
    }

    /**
     * 把 [kind] 这一栏推到 [at]，**只往后**。
     *
     * 一个不是瞬间的值、一个比现有位置更早的瞬间，都原样返回自己：慢回答、旧页面、坏值都不该
     * 让已经读过的位置倒退，否则同一个红点会亮第二遍。
     */
    fun with(kind: InboxKind, at: String): InboxCursors {
        if (!isStamp(at) || at <= this[kind]) return this
        return put(kind, at)
    }

    /** 四栏里最新的那一个位置；四栏都空就是空串。只给需要压成一个游标的地方用（旧后端）。 */
    val latest: String
        get() = buildList {
            add(reply)
            add(planComment)
            add(commentHeart)
            add(planHeart)
        }.maxOrNull().orEmpty()

    /** 四栏都没有位置：和旧版本里"还没打开过消息中心"是同一件事。 */
    val isBlank: Boolean
        get() = reply.isEmpty() && planComment.isEmpty() && commentHeart.isEmpty() && planHeart.isEmpty()

    /** 按栏取一遍，键是四栏本身。 */
    fun asMap(): Map<InboxKind, String> = InboxKind.entries.associateWith { this[it] }

    /** 从 [before] 到现在真的往前走了的栏。 */
    fun movedFrom(before: InboxCursors): Set<InboxKind> =
        InboxKind.entries.filterTo(mutableSetOf()) { this[it] != before[it] && this[it].isNotEmpty() }

    /**
     * 一次计数之后的新位置。
     *
     * 三件事分开看，每栏各自判断：
     *
     *  * [served] 里有这一栏，说明这一栏的行真的送到了屏幕上——推到那一栏最新的一条送达的行。
     *    "看过"的证据只有这一个：界面把四栏合成一条流，点栏本身不产生回调。
     *  * 没有送达、而 [counts] 说这一栏是 0，说明这一栏确实没有新东西：推到 [checkedAt]，
     *    下次从同一个点往后算。
     *  * 其余情况——这一栏有新东西，而新东西没露过面——位置不动，栏上的数字留着。这正是
     *    "读过回复栏不清掉赞栏"的那一半。
     *
     * [checkedAt] 不是瞬间（旧部署不盖章）时什么也不推：宁可多算一次，也不拿一个假时间当地基。
     */
    fun afterCounting(
        counts: Map<InboxKind, Int>,
        served: Map<InboxKind, String>,
        checkedAt: String,
    ): InboxCursors {
        var next = this
        InboxKind.entries.forEach { kind ->
            val shown = served[kind].orEmpty()
            next = when {
                shown.isNotEmpty() -> next.with(kind, shown)
                (counts[kind] ?: 0) == 0 -> next.with(kind, checkedAt)
                else -> next
            }
        }
        return next
    }

    /** 编码成界面搬运的那一个字符串；四栏都空就是空串（"从来没读过"）。 */
    fun encode(): String {
        if (isBlank) return ""
        return buildString {
            append(CURSOR_PREFIX)
            InboxKind.entries.forEach { kind ->
                append(';').append(kind.wire).append('=').append(this@InboxCursors[kind])
            }
        }
    }

    companion object {
        /** 什么都没读过。 */
        val EMPTY: InboxCursors = InboxCursors()

        /** 四栏都读到同一个瞬间 [at]；不是瞬间就是 [EMPTY]。 */
        fun of(at: String): InboxCursors =
            if (isStamp(at)) InboxCursors(at, at, at, at) else EMPTY

        /** 从持久化的表（`kind` → 瞬间）还原；坏值当没读过。 */
        fun ofMap(stored: Map<String, String>): InboxCursors {
            var cursors = EMPTY
            InboxKind.entries.forEach { kind ->
                cursors = cursors.with(kind, stored[kind.wire].orEmpty())
            }
            return cursors
        }

        /**
         * 解码 [encode] 写下的值；**旧值是原样传进来的一个瞬间**（见类注释的迁移策略），
         * 按四栏都已读到那里处理。认不出来的字符串当没读过。
         */
        fun decode(text: String): InboxCursors {
            val raw = text.trim()
            if (raw.isEmpty()) return EMPTY
            if (!raw.startsWith(CURSOR_PREFIX + ";")) return of(raw)
            var cursors = EMPTY
            raw.removePrefix(CURSOR_PREFIX + ";").split(';').forEach { pair ->
                val split = pair.indexOf('=')
                if (split <= 0) return@forEach
                val kind = InboxKind.entries.firstOrNull { it.wire == pair.substring(0, split) }
                    ?: return@forEach
                cursors = cursors.with(kind, pair.substring(split + 1).trim())
            }
            return cursors
        }
    }
}

/** 编码前缀。改格式就换一个，旧的 `k2;...` 会自然落进"认不出来 = 没读过"。 */
private const val CURSOR_PREFIX: String = "k2"

/**
 * 后端盖的戳长这样：`2026-11-20T10:00:00.000000Z`。
 *
 * 只认头十位，和 SQL 里 `'^[0-9]{4}-[0-9]{2}-[0-9]{2}'` 的判断一致：一个坏值在两边都是
 * "没给"。空的、被截断的、一个纯数字（旧的回复 id）都不算。
 */
internal fun isStamp(text: String): Boolean =
    text.length >= 10 && text[4] == '-' && text[7] == '-'

/**
 * `inbox_page.src` 用的名字，也是 `inbox_seen.kind` 那一列允许的四个值。
 *
 * 反过来是 [InboxKind.of]：那边认不出来的名字按回复读，是给"后端多长出一栏"用的；这里只写
 * 后端和本机都认识的四个，多出来的栏根本没有位置可存。
 */
val InboxKind.wire: String
    get() = when (this) {
        InboxKind.REPLY -> "reply"
        InboxKind.PLAN_COMMENT -> "plan_comment"
        InboxKind.COMMENT_HEART -> "comment_heart"
        InboxKind.PLAN_HEART -> "plan_heart"
    }

/**
 * 这一页里每一栏最新的一条的时间，也就是"这一栏真的送到了屏幕上"的部分。
 *
 * 只认带着戳的行：旧部署的某些行没有 `created_at`，那种行不能当证据用——拿一个猜出来的时间
 * 去推游标，会把没看过的新闻一起算成看过。
 */
fun List<InboxItem>.newestPerKind(): Map<InboxKind, String> {
    val newest = mutableMapOf<InboxKind, String>()
    forEach { item ->
        val at = item.createdAt
        if (!isStamp(at)) return@forEach
        if (at > newest[item.kind].orEmpty()) newest[item.kind] = at
    }
    return newest
}

/**
 * 把两份"每栏最新一条"合起来，每栏取更晚的。
 *
 * 送达记录只增不减：位置本来就只往前走，旧的证据比当前游标更早，喂进去也是空转。
 */
fun mergeNewest(
    into: Map<InboxKind, String>,
    fresh: Map<InboxKind, String>,
): Map<InboxKind, String> = fresh.entries.fold(into) { merged, (kind, at) ->
    val known = merged[kind].orEmpty()
    if (at > known) merged + (kind to at) else merged
}