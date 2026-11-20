package io.github.samolego.canta.data.community

/**
 * The filter over the inbox: the four kinds of news, cut into the groups a reader actually looks
 * for.
 *
 * The backend counts them separately (`inbox_summary` answers `replies`, `plan_comments`,
 * `comment_hearts`, `plan_hearts`), so the marks on these tabs are the backend's own numbers — the
 * same ones the red dot is made of. Reading one group cannot silently clear the others: the cursor
 * is a single instant, so every group is read at the same moment, and a count that survives a
 * restart is a count that was actually new.
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

    /** Whether [kind] belongs in this group. */
    fun accepts(kind: InboxKind): Boolean = when (this) {
        ALL -> true
        REPLY -> kind == InboxKind.REPLY
        // The two hearts are one tab: "someone liked something of mine" is one kind of news, and
        // the row itself already says which of the two it was.
        PLAN_COMMENT -> kind == InboxKind.PLAN_COMMENT
        HEART -> kind.isHeart
    }

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