package io.github.samolego.canta.data.community

/** A comment under a community plan: `target` is the plan id as text. */
const val COMMENT_KIND_PLAN: String = "plan"

/** A comment under one app's page: `target` is the package name. */
const val COMMENT_KIND_APP: String = "app"

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
 * The longest comment the backend keeps: `plan_comment` stores `left(body, 1000)`.
 *
 * It used to be a silent truncation — a 1200-character comment went up as its first 1000 and
 * nothing said so. The composer counts against this instead, so the limit is visible while typing
 * rather than discovered afterwards.
 */
const val COMMENT_MAX_LENGTH: Int = 1000

/**
 * How much of [length] is left to type: never negative, because a paste can carry more than the
 * limit and the number beside the field should read 0 rather than -37.
 */
fun commentCharactersLeft(length: Int): Int = (COMMENT_MAX_LENGTH - length).coerceAtLeast(0)

/**
 * The longest plan note the backend keeps: `plan_set_note` stores `left(p_note, 2000)`, the same
 * cap the upload path has had since the table was created.
 */
const val NOTE_MAX_LENGTH: Int = 2000

/**
 * What kind of news one row of the inbox is.
 *
 * The list is one stream of four different things — somebody answered me, somebody wrote under the
 * plan I uploaded, somebody hearted my comment, somebody endorsed my plan — and the row has to say
 * which, because "赞了你的评论" and "赞了你的方案" are different sentences.
 */
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
data class CommentThread(val comment: PlanComment, val replies: List<PlanComment>)

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
