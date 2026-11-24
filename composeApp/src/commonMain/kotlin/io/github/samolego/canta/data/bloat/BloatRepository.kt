package io.github.samolego.canta.data.bloat

import io.github.samolego.canta.data.AppBlobStorage
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.app.AppBadgeInfo
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val DEFAULT_BLOAT_URL =
    "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
const val DEFAULT_BLOAT_COMMITS_URL =
    "https://api.github.com/repos/Universal-Debloater-Alliance/universal-android-debloater-next-generation/commits?path=resources%2Fassets%2Fuad_lists.json"

/** An app's entry in the UAD bloat list. */
data class BloatData(
    internal val description: String?,
    internal val badgeInfo: AppBadgeInfo?,
    /**
     * True when [description] is this build's translation of the list rather than the list's own
     * English. The app page says which one it is showing, so the wording is never passed off as
     * the upstream text.
     */
    internal val translated: Boolean = false,
)

/**
 * Provides the UAD bloat list.
 *
 * Two steps, and the order is the point: [local] answers from the cache (or the shipped copy)
 * without touching the network, so the app list has its badges the moment the list is on screen;
 * [refresh] then checks GitHub **at most once a day** and only downloads when the commit changed.
 * A failed check or a failed download leaves the local copy alone and does not spend the day's one
 * attempt, so the next launch tries again.
 *
 * A usable cached list is never thrown away because of a failed download,
 * and a corrupt cache is replaced by a fresh download.
 *
 * When there is no cache and GitHub cannot be reached at all, the list shipped with the build
 * ([bundled]) is used instead: a phone without a route to `raw.githubusercontent.com` still gets
 * badges and descriptions rather than an empty list. The bundled copy is a starting point only —
 * every successful download still replaces it and is cached as usual.
 *
 * [descriptions] is this build's translation of the list's English wording; it is applied to the
 * parsed entries, so everything downstream (the badge, the app page, the list) sees one language.
 */
class BloatRepository(
    private val client: HttpClient,
    private val cache: AppBlobStorage,
    private val settings: CantaSettings,
    /**
     * The translation to apply, resolved on first use: reading it touches the bundled resources,
     * which the platforms that build this repository are not all ready for at construction time.
     */
    private val descriptions: suspend () -> BloatDescriptions = { BloatDescriptions.empty() },
    /**
     * The raw list shipped with the build, resolved on first use for the same reason as
     * [descriptions]. Returning null (or failing) simply means "no local copy", and the repository
     * behaves exactly as it did before: whatever the download gives, possibly nothing.
     */
    private val bundled: suspend () -> String? = { null },
) {
    private companion object {
        const val TAG = "BloatRepository"
    }

    /** Resolved once: the table is a bundled file, it does not change while the app runs. */
    private var resolvedDescriptions: BloatDescriptions? = null

    private suspend fun translationTable(): BloatDescriptions =
        resolvedDescriptions ?: descriptions().also { resolvedDescriptions = it }

    /** The shipped list, parsed once; empty when this build has none. */
    private var resolvedBundled: Map<String, BloatData>? = null

    private suspend fun bundledEntries(l10n: BloatDescriptions): Map<String, BloatData> {
        resolvedBundled?.let { return it }
        val entries = runCatching { bundled()?.let { parseBloatList(it).localisedWith(l10n) } }
            .onFailure { LogUtils.w(TAG, "Shipped bloat list is unreadable: $it") }
            .getOrNull()
            .orEmpty()
        resolvedBundled = entries
        return entries
    }

    suspend fun load(): Map<String, BloatData> {
        val localList = local()
        return refresh() ?: localList
    }

    /**
     * 手上这份列表：缓存里那份，没有就退到随包发的那份，都没有才是空表。**不联网**。
     *
     * 应用列表要的是"立刻有徽标"，不是"等 GitHub 回话"：以前每进一次列表都先问一遍 commits
     * 接口（国内经常直接超时），于是列表上的「正在加载特殊标志」要转很久。现在先答本地这份，
     * 核对放到后台（[refresh]）。
     */
    suspend fun local(): Map<String, BloatData> {
        val l10n = translationTable()
        return readCache(l10n) ?: bundledEntries(l10n).also {
            if (it.isEmpty()) {
                LogUtils.i(TAG, "No cached bloat list and none shipped")
            } else {
                LogUtils.i(TAG, "Using the shipped bloat list (${it.size} entries)")
            }
        }
    }

    /**
     * 后台核对一次：GitHub 上真的换了 commit 才下载，返回**新的**列表；其余情况返回 null，
     * 调用方继续用手上那份。
     *
     * 三条边界，缺一条就会出现"每次都连"或者"坏一次就再也不更新"：
     *  - 自动更新关着 → 不联网；
     *  - 今天已经核对过（[CantaSettings.bloatListCheckedDayFlow] 是今天）→ 不联网，一天最多一次；
     *  - 核对不上（离线、限流、代理坏了）→ 返回 null 且**不**记账，下次启动再试。
     */
    suspend fun refresh(): Map<String, BloatData>? {
        if (!settings.autoUpdateBloatListFlow.first()) {
            LogUtils.i(TAG, "Bloat list auto-update is off; using the local list")
            return null
        }
        val today = todayKey()
        if (settings.bloatListCheckedDayFlow.first() == today) {
            LogUtils.i(TAG, "Bloat list already checked today ($today); using the local list")
            return null
        }

        val l10n = translationTable()
        // A cache that does not parse counts as "no cache": the first fetch has to happen whatever
        // the stored commit says, or a corrupt file would keep the list empty forever.
        val cached = readCache(l10n)

        // Empty when unknown (offline, rate limited, ...).
        val remoteHash = fetchLatestHash()
        if (remoteHash.isEmpty()) {
            LogUtils.w(TAG, "Couldn't check for bloat list updates; using the local list")
            return null
        }
        if (cached != null && remoteHash == settings.latestCommitHashFlow.first()) {
            LogUtils.i(TAG, "Bloat list is up to date (commit ${remoteHash.take(7)})")
            settings.setBloatListCheckedDay(today)
            return null
        }

        val start = currentTimeMillis()
        val raw = fetchBloatList() ?: return null
        val parsed = runCatching { parseBloatList(raw).localisedWith(l10n) }
            .onFailure { LogUtils.e(TAG, "Downloaded bloat list is invalid", it) }
            .getOrNull() ?: return null

        runCatching { cache.write(raw.encodeToByteArray()) }
            .onFailure { LogUtils.e(TAG, "Failed to cache the bloat list", it) }
        settings.setLatestCommitHash(remoteHash)
        settings.setBloatListCheckedDay(today)
        LogUtils.i(TAG, "Updated bloat list in ${currentTimeMillis() - start}ms (${parsed.size} entries, commit ${remoteHash.take(7)})")
        return parsed
    }

    /** 本地日期键 `YYYY-MM-DD`：一天的判据按手机自己的时区，不是 UTC。 */
    private fun todayKey(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    /** The cached list, or null if there is none or it can't be parsed. */
    private fun readCache(l10n: BloatDescriptions): Map<String, BloatData>? =
        runCatching { cache.read()?.decodeToString()?.let(::parseBloatList)?.localisedWith(l10n) }
            .onFailure { LogUtils.w(TAG, "Ignoring unreadable bloat list cache: $it") }
            .getOrNull()

    private suspend fun fetchLatestHash(): String =
        runCatching { parseLatestHash(client.get(settings.commitsUrlFlow.first()).bodyAsText()) }
            .onFailure { LogUtils.e(TAG, "Failed to check for bloat list updates", it) }
            .getOrDefault("")

    private suspend fun fetchBloatList(): String? =
        runCatching { client.get(settings.bloatListUrlFlow.first()).bodyAsText() }
            .onFailure { LogUtils.e(TAG, "Failed to download the bloat list", it) }
            .getOrNull()
}

/**
 * The UAD list shipped inside the build.
 *
 * The list is normally downloaded from GitHub and cached, but that host is not always reachable
 * from the phone (no route, proxy, rate limit). This copy is what [BloatRepository] falls back to:
 * it is parsed like any other list, and a later download still overrides it.
 */
object BundledBloatList {
    /** Path of the shipped list under `composeResources`. */
    const val RESOURCE_PATH = "files/bloat_list.json"

    /** The shipped list, or null when it is missing or unreadable on this platform. */
    suspend fun raw(): String? = runCatching { Res.readBytes(RESOURCE_PATH).decodeToString() }
        .onFailure { LogUtils.w("BundledBloatList", "Shipped bloat list is unreadable: $it") }
        .getOrNull()
}

/** Parses a raw bloat-list JSON string into a map of package name -> [BloatData]. */
private fun parseBloatList(json: String): Map<String, BloatData> =
    Json.parseToJsonElement(json).jsonObject
        .mapNotNull { (packageName, entry) -> (entry as? JsonObject)?.let { packageName to it.toBloatData() } }
        .toMap()

private fun JsonObject.toBloatData() = BloatData(
    description = this["description"]?.jsonPrimitive?.content,
    badgeInfo = this["removal"]?.jsonPrimitive?.content?.let { AppBadgeInfo.byNameIgnoreCaseOrNull(it) },
)

/**
 * The latest commit hash from a GitHub commits API response: a JSON array
 * whose first element has a "sha" string. Returns "" when the payload is
 * empty, malformed or has no usable sha, so callers treat it as "unknown"
 * instead of a bogus hash.
 */
private fun parseLatestHash(commits: String): String {
    if (commits.isBlank()) return ""
    return try {
        Json.parseToJsonElement(commits)
            .jsonArray
            .firstOrNull()
            ?.jsonObject
            ?.get("sha")
            ?.jsonPrimitive
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
    } catch (_: Exception) {
        ""
    }
}
