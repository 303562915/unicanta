package io.github.samolego.canta.data.recommend

import kotlinx.serialization.Serializable

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
@Serializable
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
 * 「谁上榜了」的指纹：`applied:3,7`，上榜条目的 id，升序。
 *
 * 只记上榜，不记票数。这是这一页红点的全部定义：别人的每一票都要算消息的话，红点等于常亮——
 * 那条东西既不是你投的、也没上榜，来了三票和你没有一点关系。这一页真正值得把人叫回来的事
 * 只有一件：有东西进了精选。
 *
 * 空队列也有指纹（`applied:`），而且不能是空串：空串表示「从没打开过」，那时不该有红点。
 * 只带前缀没有 id 也照写，因为「上次看的时候一条都没上榜」本身就是一条可比较的状态。
 */
fun appliedQueueFingerprint(apps: List<SuggestedApp>): String =
    APPLIED_PREFIX + apps.asSequence()
        .filter { it.applied }
        .map { it.id }
        .sorted()
        .joinToString(",")

/** 指纹前缀。升级前存的是 `id:认同:不认同` 或 `empty`，对不上一律当没看过。 */
private const val APPLIED_PREFIX = "applied:"

/**
 * 自 [seen] 之后新上榜的条目数。
 *
 * [seen] 为空、或者不是 [appliedQueueFingerprint] 写出来的格式（老版本留下来的指纹）时返回 0：
 * 换一个版本不该换来一次「历史全算新」的红点。
 */
fun newAppliedSince(seen: String, apps: List<SuggestedApp>): Int {
    if (!seen.startsWith(APPLIED_PREFIX)) return 0
    val before = seen.removePrefix(APPLIED_PREFIX).idsOf()
    return apps.count { it.applied && it.id !in before }
}

/** 指纹后半段那串 id；认不出来的跳过。 */
private fun String.idsOf(): Set<Long> =
    splitToSequence(',').mapNotNull { it.trim().toLongOrNull() }.toSet()

/** 有没有新上榜的条目：底栏那格和投票标签上那个点，问的都是这一句。 */
fun hasNewApplied(seen: String, apps: List<SuggestedApp>): Boolean = newAppliedSince(seen, apps) > 0

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