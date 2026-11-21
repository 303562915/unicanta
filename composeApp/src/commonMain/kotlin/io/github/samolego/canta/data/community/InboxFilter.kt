package io.github.samolego.canta.data.community

/**
 * The filter over the inbox: the four kinds of news, cut into the groups a reader actually looks
 * for.
 *
 * The backend counts them separately (`inbox_summary_kinds` answers `replies`, `plan_comments`,
 * `comment_hearts`, `plan_hearts`), so the marks on these tabs are the backend's own numbers — the
 * same ones the red dot is made of. Each group also carries its own read cursor
 * ([InboxCursors]): opening one group moves that group's position and leaves the others where they
 * were, which is what stops "read the replies" from clearing the hearts as well.
 */
enum class InboxFilter(val label: String) {
    /** Everything, in the order it happened. */
    ALL("全部"),

    /** Somebody answered a comment of mine. */
    REPLY("回复"),

    /** Somebody wrote under a plan I uploaded. */
    PLAN_COMMENT("方案评论"),

    /** Somebody hearted a comment of mine, or endorsed a plan of mine. */
    HEART("赞"),
    ;

    /**
     * The news kinds this group holds — the whole of [InboxKind] for [ALL], one for the two named
     * groups, both hearts for [HEART].
     *
     * This is the bridge between a tab and the per-kind cursors: reading a tab reads the kinds it
     * holds, and a kind shows a mark only while its own cursor is behind. A tab that covers several
     * kinds (the two hearts, or [ALL]) needs every one of them read before it goes quiet.
     */
    fun kinds(): Set<InboxKind> = when (this) {
        ALL -> InboxKind.entries.toSet()
        REPLY -> setOf(InboxKind.REPLY)
        PLAN_COMMENT -> setOf(InboxKind.PLAN_COMMENT)
        // The two hearts are one tab: "someone liked something of mine" is one kind of news, and
        // the row itself already says which of the two it was.
        HEART -> setOf(InboxKind.COMMENT_HEART, InboxKind.PLAN_HEART)
    }

    /** Whether [kind] belongs in this group. */
    fun accepts(kind: InboxKind): Boolean = kind in kinds()

    /**
     * How much of this group is new, from [summary]. Zero for [ALL] is not answered here: the total
     * is `summary.total`, and the caller already shows it on the inbox tab.
     */
    fun unreadIn(summary: InboxSummary): Int = when (this) {
        ALL -> summary.total
        REPLY -> summary.replies
        PLAN_COMMENT -> summary.planComments
        HEART -> summary.commentHearts + summary.planHearts
    }
}

/** The rows of the inbox [filter] keeps; [ALL] keeps every one of them in the order served. */
fun List<InboxItem>.matching(filter: InboxFilter): List<InboxItem> = filter { filter.accepts(it.kind) }