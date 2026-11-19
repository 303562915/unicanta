package io.github.samolego.canta.data.community

/** A comment under a community plan: `target` is the plan id as text. */
const val COMMENT_KIND_PLAN: String = "plan"

/** A comment under one app's page: `target` is the package name. */
const val COMMENT_KIND_APP: String = "app"

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
 * [transform] applied to the comment carrying [id]; the list comes back otherwise untouched.
 *
 * A comment without an id (an older backend's row) matches nothing: its page keeps what it showed
 * rather than rewriting the wrong row, because with no id there is no way to tell two rows apart.
 */
fun List<PlanComment>.updatedComment(id: Long, transform: (PlanComment) -> PlanComment): List<PlanComment> =
    map { if (it.id == id) transform(it) else it }

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
