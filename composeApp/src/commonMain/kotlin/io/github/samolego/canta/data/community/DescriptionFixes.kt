package io.github.samolego.canta.data.community

/**
 * One proposed rewrite of an app's description, and how the community voted on it.
 *
 * The published line comes from the bloat list and is written for a build that may be two ROMs old;
 * this is the same line as the people who run the app would write it. A rewrite takes effect once
 * [needed] votes agree with it *and* those agreeing votes are [shareNeeded] of all votes — the number
 * on the page says which of the two is still missing.
 */
data class DescriptionFix(
    /** The rewrite's own id; 0 when the server did not send one. */
    val id: Long = 0L,
    /** The corrected description itself. */
    val body: String = "",
    /** Why it was corrected ("这条在 HyperOS 2 上叫…"); empty when the writer skipped it. */
    val note: String = "",
    /** The display name it was signed with. */
    val author: String = "",
    /** The identity number behind [author]: what "my rewrite" and one-per-identity are keyed on. */
    val authorId: String = "",
    val createdAt: String = "",
    /** Votes agreeing with this rewrite. */
    val agrees: Int = 0,
    /** Votes disagreeing with it. */
    val disagrees: Int = 0,
    /** How this install voted: true 认同, false 不认同, null while it did not vote. */
    val mine: Boolean? = null,
    /** True once the rewrite took effect: the app page shows [body] instead of the published line. */
    val applied: Boolean = false,
    /** Agreeing votes needed before it can take effect; the backend's number, not a guess. */
    val needed: Int = VOTES_NEEDED,
    /** Share of all votes that has to agree; the backend's number, not a guess. */
    val shareNeeded: Float = SHARE_NEEDED,
) {
    val votes: Int get() = agrees + disagrees

    /** Share of the votes that agree, null while nobody voted. */
    val share: Float? get() = if (votes == 0) null else agrees.toFloat() / votes

    /** True when the counts here are enough for the rewrite to stand in. */
    val satisfied: Boolean get() = agrees >= needed && (share ?: 0f) >= shareNeeded

    /** Agreeing votes still missing, 0 once there are enough. */
    val missingVotes: Int get() = (needed - agrees).coerceAtLeast(0)

    /** Voted again by this install? Then it can change its mind by tapping the other side. */
    val mineKnown: Boolean get() = mine != null

    companion object {
        /** Agreeing votes a rewrite needs before it can replace the published description. */
        const val VOTES_NEEDED = 50

        /** Share of those votes that has to agree. */
        const val SHARE_NEEDED = 0.8f
    }
}

/** What the backend answered after one vote: the fresh counts and whether the rewrite now stands. */
data class DescriptionFixVote(
    val agrees: Int = 0,
    val disagrees: Int = 0,
    val applied: Boolean = false,
) {
    val votes: Int get() = agrees + disagrees
}
