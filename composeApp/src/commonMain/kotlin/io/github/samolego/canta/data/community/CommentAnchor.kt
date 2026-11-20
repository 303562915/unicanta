package io.github.samolego.canta.data.community

/**
 * The thread that [commentId] belongs to, whether it starts one or answers one; null when this page
 * does not have it (deleted, or a stale inbox row).
 *
 * An answer is reached through its starter: opening the thread is what shows it.
 */
fun List<CommentThread>.threadOf(commentId: Long): CommentThread? {
    if (commentId <= 0L) return null
    return firstOrNull { it.comment.id == commentId }
        ?: firstOrNull { thread -> thread.replies.any { it.id == commentId } }
}

/**
 * [threads] with the one holding [commentId] moved to the front, so the row a mention led to is the
 * first thing on the page. Every other thread keeps the order it had — this is a way of pointing,
 * not a fourth sort order.
 *
 * The thread is put first rather than scrolled to: the list's own indices depend on the package
 * ranking above it, and a page that jumps to a number it computed would be one package row away
 * from jumping to the wrong comment. Being first needs no arithmetic and cannot be wrong.
 */
fun List<CommentThread>.withAnchorFirst(commentId: Long): List<CommentThread> {
    val anchored = threadOf(commentId) ?: return this
    return listOf(anchored) + filterNot { it === anchored }
}