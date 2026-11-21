package io.github.samolego.canta.data.recommend

/**
 * One app the community proposed for the recommendations page, and how the votes stand.
 *
 * The shipped catalog is written by the maintainers; this is the other half — anybody can put an
 * app up, as long as it is open source and lives on GitHub, and everybody else votes on whether it
 * belongs. The rule is the one the description corrections use: [needed] votes in all, of which
 * [shareNeeded] have to agree — forty-five votes with thirty-six of them approving is the bar, and
 * thirty-six out of forty-six is not. Until both hold, the entry waits with its counts showing.
 *
 * @param url the GitHub project page, normalized by [githubProjectUrl]; the backend refuses
 *   anything else, because "open source and on GitHub" is the only thing this list promises.
 */
data class SuggestedApp(
    /** The entry's own id; 0 when the server did not send one. */
    val id: Long = 0L,
    val name: String = "",
    val url: String = "",
    /** One of [Recommendations.CATEGORIES]; an unknown key is shown under its own name. */
    val category: String = "",
    val summary: String = "",
    val license: String = "",
    /** The display name it was filed under. */
    val author: String = "",
    /** The identity number behind [author]: what "mine" and one pending entry are keyed on. */
    val authorId: String = "",
    val createdAt: String = "",
    /** Votes saying the app belongs on the page. */
    val agrees: Int = 0,
    /** Votes saying it does not. */
    val disagrees: Int = 0,
    /** How this install voted: true 认同, false 不认同, null while it did not vote. */
    val mine: Boolean? = null,
    /** True once the entry cleared the rule and joined the page. */
    val applied: Boolean = false,
    /** Agreeing votes needed before it can join; the backend's number, not a guess. */
    val needed: Int = VOTES_NEEDED,
    /** Share of all votes that has to agree; the backend's number, not a guess. */
    val shareNeeded: Float = SHARE_NEEDED,
) {
    val votes: Int get() = agrees + disagrees

    /** Share of the votes that agree, null while nobody voted. */
    val share: Float? get() = if (votes == 0) null else agrees.toFloat() / votes

    /** True when the counts here are enough for the entry to be shown as part of the page. */
    val satisfied: Boolean get() = votes >= needed && (share ?: 0f) >= shareNeeded

    /** Votes still missing, whatever side they would be cast on; 0 once there are enough of them. */
    val missingVotes: Int get() = (needed - votes).coerceAtLeast(0)

    /** The letter drawn in the icon tile, the same stand-in the shipped entries use. */
    val tileMark: String get() = name.trim().take(1).ifBlank { "?" }

    companion object {
        /** Votes an entry needs, counting both sides, before it can join the page. */
        const val VOTES_NEEDED = 45

        /** Share of those votes that has to agree: 36 of 45 clears it, 36 of 46 does not. */
        const val SHARE_NEEDED = 0.8f
    }
}

/** What the backend answered after one vote: the fresh counts and whether the entry now stands. */
data class SuggestedAppVote(
    val agrees: Int = 0,
    val disagrees: Int = 0,
    val applied: Boolean = false,
) {
    val votes: Int get() = agrees + disagrees
}

/**
 * The whole queue as one string: `id:agrees:disagrees` per entry, in id order.
 *
 * It exists so "somebody voted since this install last looked" can be answered with a comparison
 * instead of a stored timestamp the phone's clock would have to agree with. Only the counts go in:
 * a vote changed, withdrawn or flipped moves one of them, and a new entry adds a triple. The
 * exact formatting does not matter — it is only ever compared against another one of these.
 *
 * An empty queue still has a fingerprint, and it cannot be the empty string: that one is reserved
 * for "the page was never opened", which [hasNewVotes] reads as no news at all. Otherwise a visit
 * that found the queue empty, and a queue both empty and untried, would look the same and the
 * first vote to arrive would land on a page that thinks nobody has ever looked.
 */
fun voteQueueFingerprint(apps: List<SuggestedApp>): String =
    if (apps.isEmpty()) "empty" else apps.sortedBy { it.id }.joinToString(";") { "${it.id}:${it.agrees}:${it.disagrees}" }

/**
 * Whether [apps] holds news compared to [seen] — the queue this install last looked at.
 *
 * [seen] empty means the queue was never opened, and an install that has not looked yet is not
 * being paged: the first sight of the page stores the fingerprint without a dot. Otherwise any
 * difference, in either direction, is news: a vote withdrawn is as much a change as one cast.
 */
fun hasNewVotes(seen: String, apps: List<SuggestedApp>): Boolean =
    seen.isNotEmpty() && seen != voteQueueFingerprint(apps)

/**
 * `owner/repo` of a GitHub project page, or null when [text] is not one.
 *
 * The recommendation list promises exactly one thing — open source, on GitHub — so the submission
 * form accepts nothing else, and this is the check both the form and the backend's own guard use.
 * A pasted link keeps only its project root: `github.com/x/y/releases/tag/v1` is stored as
 * `github.com/x/y`, because the page opens the project and the releases tab is one tap further.
 *
 * A bare `owner/repo` is accepted too: that is what people copy out of a chat.
 */
fun githubProjectUrl(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    // Only the two spellings that can be pasted from a browser bar; anything with a scheme this
    // does not know is not a GitHub link, and is not guessed at.
    val withScheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring("http://".length)
        trimmed.contains("://") -> return null
        // `owner/repo` pasted out of a chat has no host at all. A first segment carrying a dot is a
        // host (`github.com/owner/repo`), which only needs the scheme put in front of it.
        isBareProjectPath(trimmed) -> "https://github.com/$trimmed"
        else -> "https://$trimmed"
    }
    val match = GITHUB_PROJECT.matchEntire(withScheme) ?: return null
    val owner = match.groupValues[1]
    // One `.git` is the suffix a clone URL carries; a second one is a typo, not a repository.
    val repo = match.groupValues[2].replace(TRAILING_GIT, "")
    if (owner.isEmpty() || repo.isEmpty() || repo == "." || repo == "..") return null
    if (repo.endsWith(".git", ignoreCase = true)) return null
    return "https://github.com/$owner/$repo"
}

/** The account a [githubProjectUrl] belongs to, which is the author the form pre-fills. */
fun githubOwnerOf(url: String): String =
    GITHUB_PROJECT.matchEntire(url.trim())?.groupValues?.getOrNull(1).orEmpty()

/**
 * `github.com/<owner>/<repo>` with an optional trailing path; the owner is a real account name and
 * the repository is anything GitHub allows, minus the `.git` suffix a clone URL carries.
 */
private val GITHUB_PROJECT = Regex(
    "^https://(?:www\\.)?github\\.com/([A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)/([A-Za-z0-9._-]+)(?:[/?#].*)?$",
    RegexOption.IGNORE_CASE,
)

/** The `.git` a clone URL ends with, in whatever case it was copied: `Repo.GIT` is `Repo`. */
private val TRAILING_GIT = Regex("\\.git$", RegexOption.IGNORE_CASE)

/**
 * True for `owner/repo` with no host in front of it: the first segment has no dot, so it cannot be
 * a host name. `Obtainium` on its own is neither a host nor a path, and is left to be refused.
 */
private fun isBareProjectPath(text: String): Boolean {
    val first = text.substringBefore('/')
    return text.contains('/') && first.isNotEmpty() && !first.contains('.')
}