package io.github.samolego.canta.data.community

import kotlinx.serialization.Serializable

/** A comment under a community plan: `target` is the plan id as text. */
const val COMMENT_KIND_PLAN: String = "plan"

/** A comment under one app's page: `target` is the package name. */
const val COMMENT_KIND_APP: String = "app"

/**
 * 一条机场下面的评论：`target` 是 `airport_entries.id` 的字符串。
 *
 * 机场的底子（认同 / 不认同、五星）已经能说「这条值不值得看」，但说不清「哪条线路挂了」
 * 「续费从哪进」——那是评论的话。
 */
const val COMMENT_KIND_AIRPORT: String = "airport"

/**
 * 推荐页条目下面的评论：`target` 是该条目的项目地址（`RecommendedApp.url`）。
 *
 * 为什么用地址而不是列表下标：地址跨设备、跨版本都对得齐同一件事，下标会因为一次投票把榜单
 * 重排之后指到别人身上。后端 `community_comments` 的 kind/target 本来就是自由文本，不需要迁移。
 */
const val COMMENT_KIND_RECOMMEND: String = "recommend"

/**
 * 一条推荐条目在评论表里的身份：项目地址（去空白、去末尾斜杠）。
 *
 * 放在这里而不是页面里：列表页和详情页都要用它拼同一个 target，两份实现只要差一个字符，
 * 同一项目就会生出两条线程——「列表里能看到评论、点进去却是空的」。去掉末尾斜杠是为了
 * `https://github.com/x/y` 和 `https://github.com/x/y/` 算同一件事。
 */
fun recommendCommentTarget(url: String): String = url.trim().trimEnd('/')

/**
 * 一条推荐条目在评论表里的身份：项目地址（去空白、去末尾斜杠）。
 *
 * 放在这里而不是页面里：列表页和详情页都要用它拼同一个 target，两份实现只要差一个字符，
 * 同一项目就会生出两条线程——「列表里能看到评论、点进去却是空的」。去掉末尾斜杠是为了
 * `https://github.com/x/y` 和 `https://github.com/x/y/` 算同一件事。
 */
fun recommendCommentTarget(url: String): String = url.trim().trimEnd('/')

/**
 * How many rows one page of an inbox list holds.
 *
 * The page asks for this many and no more: a phone that parses 200 rows of history before it can
 * draw the first one is a phone that hangs on a slow connection, and the reader only ever looks at
 * the top of the list. Scrolling asks for the next [INBOX_PAGE_SIZE], so the list grows by use
 * rather than by opening the page. The backend clamps every call to 50, so this must stay under it.
 */
const val INBOX_PAGE_SIZE: Int = 20

/**
 * The longest plan note the backend keeps: `plan_set_note` stores `left(p_note, 2000)`, the same
 * cap the upload path has had since the table was created.
 */
const val NOTE_MAX_LENGTH: Int = 2000

/**
 * How many answers a thread shows the first time it is opened, and how many each later "展开更多"
 * adds. Three is enough to judge whether the conversation is worth reading; ten is a page of it.
 */
const val FIRST_REPLY_BATCH: Int = 3
const val NEXT_REPLY_BATCH: Int = 10

/**
 * What kind of news one row of the inbox is.
 *
 * The list is one stream of four different things — somebody answered me, somebody wrote under the
 * plan I uploaded, somebody hearted my comment, somebody endorsed my plan — and the row has to say
 * which, because "赞了你的评论" and "赞了你的方案" are different sentences.
 */
@Serializable
enum class InboxKind {
    /** Somebody answered a comment of mine. */
    REPLY,

    /** Somebody commented under a plan I uploaded. */
    PLAN_COMMENT,

    /** Somebody hearted a comment of mine; [InboxItem.likes] is how many people did. */
    COMMENT_HEART,

    /** Somebody endorsed a plan I uploaded; [InboxItem.likes] is how many people did. */
    PLAN_HEART,
    ;

    /** True for the two that are hearts, which are counted rather than quoted. */
    val isHeart: Boolean get() = this == COMMENT_HEART || this == PLAN_HEART

    companion object {
        /** The wire name the backend uses in `inbox_page.src`; unknown names read as a reply. */
        fun of(src: String): InboxKind = when (src) {
            "plan_comment" -> PLAN_COMMENT
            "comment_heart" -> COMMENT_HEART
            "plan_heart" -> PLAN_HEART
            else -> REPLY
        }
    }
}

/**
 * One line of the inbox: something that happened to what this install wrote.
 *
 * [id] is the id of the thing the line is about — the comment for a reply, the plan or comment for a
 * heart — not a row id of the feed, because the feed is four tables and only the underlying object
 * is worth opening. "New" is therefore still "id newer than the one I last opened" for the comment
 * shaped rows; hearts carry [createdAt] and are reported as new by the count the summary returns.
 */
@Serializable
data class InboxItem(
    val kind: InboxKind,
    /** The comment id (replies, comment hearts) or the plan id (plan comments, plan hearts). */
    val id: Long,
    /** [COMMENT_KIND_PLAN] or [COMMENT_KIND_APP]: which page this opens. */
    val targetKind: String = COMMENT_KIND_PLAN,
    /** The plan id as text, or the package name. */
    val target: String = "",
    /** The plan's title, or the app's name when the backend resolved one. */
    val title: String = "",
    /** What was written: the reply, the comment, or the hearted comment/plan's own text. */
    val body: String = "",
    /** For a reply or a comment under my plan: the comment it answers, when it answers one. */
    val parentBody: String = "",
    /** Who did it: the reply's writer, or the newest person to tap the heart. */
    val actor: String = "",
    /**
     * 那个人的身份号：主页、名单这些"点人进主页"的入口要用它，[actor] 只是今天用的名字，
     * 后端在 `actor` 之外单独给了一列，没有就空着（行还能开，只是头像点不出主页）。
     */
    val actorId: String = "",
    val createdAt: String = "",
    /**
     * For the heart kinds: how many people hearted it in total, the newest included. One line says
     * "张三 等 3 人赞了你的评论", which is what that number is for. Zero on the other kinds.
     */
    val likes: Int = 0,
) {
    /** The plan id, when this line opens a plan. */
    fun planIdOrNull(): Long? = if (targetKind == COMMENT_KIND_PLAN) target.toLongOrNull() else null

    /** The package name, when this line opens an app page. */
    fun packageOrNull(): String? = if (targetKind == COMMENT_KIND_APP) target else null

    /**
     * The id the "new" marker is compared against: a comment id for the comment shaped rows, and
     * the plan id for plan hearts, which have no comment behind them at all.
     */
    val commentIdOrZero: Long get() = if (kind == InboxKind.PLAN_HEART) 0L else id
}

/**
 * What the server says is waiting for this install, counted per source.
 *
 * The red dot is the sum, and the sum is "since the inbox was last opened": a heart on a comment
 * and a comment under a plan are both news, and news stops being news the moment the list is read.
 * The four numbers are kept apart because the tab labels say how many of each — "回复 2 · 赞 5" —
 * which a single total cannot.
 */
@Serializable
data class InboxSummary(
    val replies: Int = 0,
    val planComments: Int = 0,
    val commentHearts: Int = 0,
    val planHearts: Int = 0,
    /** The newest comment id among the comment shaped sources, for the per-row "new" marker. */
    val newestCommentId: Long = 0L,
    /**
     * When the backend counted these numbers: a UTC instant it stamped itself, which the page
     * stores and hands back as the next call's cursor. That round trip is the read cursor — the
     * phone's own clock never enters the comparison. Empty on a backend that predates it.
     */
    val checkedAt: String = "",
) {
    /** Everything that happened, which is what the red dot counts. */
    val total: Int get() = replies + planComments + commentHearts + planHearts

    /** Nothing waiting: the shape an older backend answers with, so the dot simply stays off. */
    companion object {
        val EMPTY = InboxSummary()
    }
}

/**
 * One page of a list, and whether asking again would hand back more.
 *
 * Every list on the inbox page is read this way: a page, not the whole thing. The backend clamps
 * what a page may be, so [hasMore] is the server's answer to "is there a next offset worth asking
 * for" rather than the app guessing from a row count it did not receive.
 */
data class Page<out T>(val items: List<T> = emptyList(), val hasMore: Boolean = false) {
    companion object {
        /** An empty page of anything: what a first read looks like before it has answered. */
        val EMPTY: Page<Nothing> = Page()
    }
}

/**
 * How many rows of one source came back: a source that filled its window may have another page,
 * so [hasMore] is true when any single source did.
 *
 * The feed merges four sources, which is why the period of "there is more" is one source's worth
 * rather than the page's total: three sources can run out while the fourth still has history, and
 * saying "that is all" there would silently drop it.
 */
fun pageHasMore(items: List<InboxItem>, perSource: Int): Boolean =
    items.groupingBy { it.kind }.eachCount().values.any { it >= perSource }

/**
 * One comment this install wrote, with what it was written under, so the page that lists them can
 * show where each one lives and jump there.
 *
 * The same shape answers both questions the page asks: [parentBody] is empty for "my comments" and
 * holds the comment being answered for "replies to me".
 */
@Serializable
data class MyComment(
    val id: Long,
    /** [COMMENT_KIND_PLAN] or [COMMENT_KIND_APP]. */
    val kind: String,
    /** The plan id as text, or the package name. */
    val target: String,
    /** The plan's title; empty for an app page, whose name the app itself resolves. */
    val title: String = "",
    /** Who wrote it: this install for "mine", the other person for a reply. */
    val author: String = "",
    val body: String,
    val createdAt: String,
    /** How many answers hang under it. */
    val replies: Int = 0,
    /** For a reply: the comment it answers. */
    val parentBody: String = "",
    /** For a reply: id of the comment it answers. */
    val parentId: Long = 0L,
    /** How many hearts this comment carries; only "my comments" is told, the reply list does not. */
    val likes: Int = 0,
) {
    /** True for a comment that stands on its own, false for an answer to somebody. */
    val isThreadStarter: Boolean get() = parentId == 0L

    /** The plan id, when this comment hangs under a plan. */
    fun planIdOrNull(): Long? = if (kind == COMMENT_KIND_PLAN) target.toLongOrNull() else null

    /** The package name, when this comment hangs under an app page. */
    fun packageOrNull(): String? = if (kind == COMMENT_KIND_APP) target else null
}

/**
 * The replies that are new to this install: everything newer than the last one it opened.
 *
 * The backend only ever adds rows, so a reply id grows and a comment the reader already saw keeps
 * its id — which is why "unread" is [lastSeenId] against the id, not a timestamp against a clock.
 */
fun List<MyComment>.unreadSince(lastSeenId: Long): List<MyComment> = filter { it.id > lastSeenId }

/** The id to remember once the inbox has been opened: the newest reply in it, 0 when empty. */
fun newestReplyId(replies: List<MyComment>): Long = replies.maxOfOrNull { it.id } ?: 0L

/**
 * The inbox id to remember after this page of news has been read: the newest comment id among
 * [items].
 *
 * Hearts are not comments and have no id to compare against, so they do not move the mark — a
 * heart cannot be "opened" per row, it is read as a count. Taking the max over the comment shaped
 * rows keeps the red dot's rule the same as before, now that the list is four sources rather than
 * one.
 */
fun newestInboxCommentId(items: List<InboxItem>): Long =
    items.maxOfOrNull { it.commentIdOrZero } ?: 0L

/**
 * The rows of [items] that arrived after the last one this install opened.
 *
 * A heart row has no id to compare against — the plan id or comment id it carries is not a
 * sequence the mark was taken from — so it is never marked new here. What makes a heart new is the
 * count in [InboxSummary], which is what turned the dot on in the first place.
 */
fun List<InboxItem>.newSince(lastSeenId: Long): Set<InboxItem> =
    filter { it.commentIdOrZero > lastSeenId }.toSet()

/**
 * [transform] applied to the comment carrying [id]; the list comes back otherwise untouched.
 *
 * A call without a real id — 0, the mark of an older backend's row — rewrites nothing: with no id
 * there is no way to tell two rows apart, so the page keeps what it showed.
 */
fun List<PlanComment>.updatedComment(id: Long, transform: (PlanComment) -> PlanComment): List<PlanComment> =
    if (id <= 0L) this else map { if (it.id == id) transform(it) else it }

/** One comment and the answers it collected, in the order the backend served them. */
@Serializable
data class CommentThread(val comment: PlanComment, val replies: List<PlanComment>) {

    /**
     * Who an answer is aimed at, for the grey arrow that marks a reply to a reply — or empty when
     * the answer is a direct answer to the comment that started the thread.
     *
     * A direct answer needs no marker: it sits under the comment it answers and the indentation says
     * as much. An answer to one of the *other* answers is the case that has to be spelled out,
     * because otherwise it reads as if it were answering the starter.
     */
    fun markerFor(answer: PlanComment): String {
        if (answer.replyTo == comment.id) return ""
        // Zero means an older backend that never sent parent ids: every answer is a direct one.
        if (answer.replyTo <= 0L) return ""
        return replies.firstOrNull { it.id == answer.replyTo }?.author.orEmpty()
    }

    /** The answers this thread shows before anyone asks for more. */
    val firstBatch: Int get() = FIRST_REPLY_BATCH

    /** The count to show after one more "展开更多" tap, from [shown] currently visible. */
    fun nextBatch(shown: Int): Int = (shown + NEXT_REPLY_BATCH).coerceAtMost(replies.size)
}

/**
 * Groups a flat comment list into threads: the comments that start a conversation, each with its
 * answers. An answer whose starter is not in [comments] (deleted while the page was open) is shown
 * as a thread of its own rather than dropped, so nothing silently disappears.
 */
fun List<PlanComment>.threads(): List<CommentThread> {
    val starters = filter { it.replyTo == 0L }
    val answersByStarter = filter { it.replyTo != 0L }.groupBy { it.replyTo }
    val starterIds = starters.mapTo(mutableSetOf()) { it.id }
    val threads = starters.map { starter ->
        // Oldest answer first: a conversation is read downwards, unlike the list of threads.
        CommentThread(starter, answersByStarter[starter.id].orEmpty().sortedBy { it.id })
    }
    val orphans = this.filter { it.replyTo != 0L && it.replyTo !in starterIds }
    return threads + orphans.map { CommentThread(it, emptyList()) }
}
