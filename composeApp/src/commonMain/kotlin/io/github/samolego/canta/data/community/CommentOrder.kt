package io.github.samolego.canta.data.community

/**
 * The order the comments under a plan or an app are read in.
 *
 * All three orders are about the *conversation starters* — the answers inside a thread always read
 * oldest first, because a conversation is followed downwards. What changes is which thread comes
 * first.
 */
enum class CommentOrder {
    /** Newest first: what the backend serves, and the right default for a page people revisit. */
    NEWEST,

    /** Most hearts first: what the crowd agreed with, useful on a plan with a long history. */
    HEARTED,

    /**
     * The ones worth reading first: hearts, then whether the thread collected answers. A comment
     * nobody hearted and nobody answered is still there, at the bottom — "useful" is a ranking, not
     * a filter.
     */
    USEFUL;

    /** "最新" / "最多赞" / "最有用", the label the switcher shows. */
    val label: String
        get() = when (this) {
            NEWEST -> "最新"
            HEARTED -> "最多赞"
            USEFUL -> "最有用"
        }
}

/**
 * [threads] in [order]. Ties keep the order they came in, so equal rows never shuffle between
 * sorts — the backend's own order is the tie-breaker, which is what a reader expects.
 */
fun List<CommentThread>.inOrder(order: CommentOrder): List<CommentThread> = when (order) {
    CommentOrder.NEWEST -> sortedByDescending { it.comment.id }
    CommentOrder.HEARTED -> sortedByDescending { it.comment.likes }
    CommentOrder.USEFUL -> sortedWith(
        compareByDescending<CommentThread> { it.usefulness }
            .thenByDescending { it.comment.likes }
    )
}

/**
 * The thread's own weight under [CommentOrder.USEFUL]: a heart is worth more than an answer,
 * because hearting says "this is right" while answering only says "this needed saying back".
 */
private val CommentThread.usefulness: Int
    get() = comment.likes * 2 + replies.size