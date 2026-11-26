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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 名单由我们自己托管：`bloat-list-sync.yml` 每周从上游 UAD 拉一份到这个路径，提交进 master。
 *
 * 默认源不再直接指上游的 `raw.githubusercontent.com`：那份名单"哪些系统包能删"的判定是安全相关，
 * 版本要可追溯（同步时会一起写 `site/bloat/_sync_meta.json` 记上游 commit），而且上游地址在国内
 * 经常整段超时——客户端把一次核对白白花在重试上。上游那两家仓库名只出现在同步 workflow 里。
 */
const val DEFAULT_BLOAT_URL =
    "https://raw.githubusercontent.com/303562915/unicanta/master/site/bloat/bloat_list.json"

/**
 * 核对有没有更新，看的是这个路径最近的提交。我们自己的镜像由 [bloat-list-sync.yml] 提交，
 * 上游没动我们就不会有新提交，所以这个地址就是"名单变没变"的判据。
 */
const val DEFAULT_BLOAT_COMMITS_URL =
    "https://api.github.com/repos/303562915/unicanta/commits?path=site%2Fbloat%2Fbloat_list.json"

/**
 * 分级修正表的默认地址：也是我们自己托管的静态文件，**不是数据库**。
 *
 * 内容：`{ "packages": { "<包名>": { "removal": "...", "notes": {...} } } }`，
 * 一部分是我们自己确认过写下的，一部分是用户从应用里提交、`bloat-corrections.yml` 核对合并的
 * （见 `.github/ISSUE_TEMPLATE/level-correction.yml`）。随包发的 `files/bloat_overrides.json`
 * 是同一个格式，所以两份表可以直接叠。
 */
const val DEFAULT_LEVEL_FIXES_URL =
    "https://raw.githubusercontent.com/303562915/unicanta/master/site/bloat/level_fixes.json"

/** 修正表的「变没变」判据：同一路径的提交列表，与名单那条是同一个办法。 */
const val DEFAULT_LEVEL_FIXES_COMMITS_URL =
    "https://api.github.com/repos/303562915/unicanta/commits?path=site%2Fbloat%2Flevel_fixes.json"

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
    /**
     * The corrections to the list's levels and wording ([BloatOverrides]), resolved on first use
     * for the same reason as [descriptions]. Empty means "take the list as published", which is
     * what a build without the resource behaves like.
     */
    private val overrides: suspend () -> BloatOverrides = { BloatOverrides.empty() },
    /**
     * 修正表的来源（`site/bloat/level_fixes.json` 的原文 URL），默认就是本仓库托管的那份；
     * null = 这个构建不带这个能力，随包的那张表就是全部。
     *
     * 分成 [correctionsUrl] 与 [correctionsCommitsUrl] 两个 lambda 而不是塞一个仓储进来：这个类
     * 已经管着「按日历天数核对、把答复落盘、失败不出声」这套机制，调用本身只有一行 HTTP。
     *
     * **不走数据库**：修正表是一张静态文件。GitHub 免费、无请求数计费，而数据库那一侧 2026-11-25
     * 刚因为请求量把 426 MB 的实例压到开 swap（见 `ops-notes/backend-incident-2026-11-25.md`）。
     */
    private val correctionsUrl: (suspend () -> String)? = null,
    /** 核对修正表有没有变（同一路径的 commits API）；null = 每次直接下载。 */
    private val correctionsCommitsUrl: (suspend () -> String)? = null,
    /** Where the last fetched correction table lives between launches. */
    private val correctionsCache: AppBlobStorage? = null,
) {
    private companion object {
        const val TAG = "BloatRepository"

        /**
         * 核对间隔，按日历天数。
         *
         * 上游这个列表一周也动不了几次，而一次核对在国内要连 `api.github.com`：天天问一遍只是在
         * 白花流量与超时重试，换来的"早知道一天"没有意义。七天问一次，剩下的次数留给用户自己按
         * 「检查更新」或在设置里改地址。
         */
        const val CHECK_INTERVAL_DAYS = 7L

        /**
         * 修正表的核对间隔：与列表同一套「按日历天数、失败不算用掉额度」的规矩。
         *
         * 修正表只有几十行，一周一次足够：改一条就要等七天以内生效，而它本来也不该天天动——
         * 门槛是「我们确认过这个包在某台机器上确实删不得」，不是社区投票。每周同步时它跟名单
         * 一起核对，所以手机真正联网的次数**没有增加**。
         */
        const val CORRECTIONS_INTERVAL_DAYS = 7L
    }

    /** Resolved once: the table is a bundled file, it does not change while the app runs. */
    private var resolvedDescriptions: BloatDescriptions? = null

    private suspend fun translationTable(): BloatDescriptions =
        resolvedDescriptions ?: descriptions().also { resolvedDescriptions = it }

    /** Resolved once, same as the translation table. */
    private var resolvedBundledOverrides: BloatOverrides? = null

    private suspend fun bundledCorrections(): BloatOverrides =
        resolvedBundledOverrides ?: overrides().also { resolvedBundledOverrides = it }

    /**
     * The corrections actually in force: the bundled table, with the last list the backend gave us
     * laid over it.
     *
     * Read from disk, never off the network: [local] has to answer without waiting for anything, and
     * a correction read from a previous launch is exactly as good an answer as a fresh one. The
     * fetch happens in [refresh], in the same [CHECK_INTERVAL_DAYS]-day window as the list itself.
     */
    private var resolvedCorrections: BloatOverrides? = null

    private suspend fun corrections(): BloatOverrides {
        resolvedCorrections?.let { return it }
        val local = bundledCorrections()
        return local.overriddenBy(readStoredCorrections(local.language)).also { resolvedCorrections = it }
    }

    private suspend fun readStoredCorrections(language: String): BloatOverrides {
        val storage = correctionsCache ?: return BloatOverrides.empty()
        return runCatching {
            storage.read()?.decodeToString()?.let { BloatOverrides.fromRemote(it, language) }
        }.onFailure {
            LogUtils.w(TAG, "Ignoring unreadable level corrections: $it")
        }.getOrNull() ?: BloatOverrides.empty()
    }

    /**
     * 把仓库里那份修正表拉一次、落盘，变了才返回 true。
     *
     * 每一条失败都是空操作：没配 URL、没地方存、请求超时、答复读不懂——都不是丢掉盘上已有修正的
     * 理由，也都不算用掉这一周的那次核对（那由 [refreshCorrections] 记账）。
     */
    private suspend fun syncRemoteCorrections(): Boolean {
        val url = correctionsUrl?.invoke() ?: return false
        val storage = correctionsCache ?: return false
        val language = bundledCorrections().language
        val text = runCatching { client.get(url).bodyAsText() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return false
        val stored = runCatching { storage.read()?.decodeToString() }.getOrNull()
        if (stored == text) {
            settings.setCorrectionsFetchedDay(todayKey())
            return false
        }
        // 先确认读得懂再落盘：一份格式坏掉的答复不该顶掉盘上那份好用的。
        val remote = BloatOverrides.fromRemote(text, language)
        runCatching { storage.write(text.encodeToByteArray()) }
            .onFailure { LogUtils.w(TAG, "Failed to cache level corrections: $it") }
        resolvedCorrections = bundledCorrections().overriddenBy(remote)
        settings.setCorrectionsFetchedDay(todayKey())
        LogUtils.i(TAG, "Level corrections updated (${remote.size} entries)")
        return true
    }

    /**
     * 等级修正的核对：这一周已经拉过就直接用手上那份，不联网。返回 true 表示**这一趟带来了
     * 新修正**，调用方据此决定要不要重画列表。
     *
     * 修正表是 `site/bloat/level_fixes.json` 一张静态文件，跟名单同一个节拍（一周），
     * 所以手机真正联网的次数没有增加：同一次应用列表刷新里，两个请求并排，而不是多出一次数据库。
     */
    suspend fun refreshCorrections(): Boolean {
        if (!settings.autoUpdateBloatListFlow.first()) return false
        val today = todayKey()
        if (checkedRecently(settings.correctionsFetchedDayFlow.first(), today, CORRECTIONS_INTERVAL_DAYS)) {
            return false
        }
        return syncRemoteCorrections()
    }

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
        // The raw copy, not [local]: the corrections are applied exactly once, below. Layering them
        // over an already-patched map would append the note a second time.
        val localList = localEntries()
        val refreshed = refresh()
        // The list may not have changed while the corrections did: put the current table over the
        // copy already in hand, so the first screen after a correction is not a launch behind.
        return refreshed ?: localList.withOverrides(corrections())
    }

    /**
     * 手上这份列表：缓存里那份，没有就退到随包发的那份，都没有才是空表。**不联网**。
     *
     * 应用列表要的是"立刻有徽标"，不是"等 GitHub 回话"：以前每进一次列表都先问一遍 commits
     * 接口（国内经常直接超时），于是列表上的「正在加载特殊标志」要转很久。现在先答本地这份，
     * 核对放到后台（[refresh]）。
     */
    suspend fun local(): Map<String, BloatData> = localEntries().withOverrides(corrections())

    /** [local] without the corrections: the copy as the list and the cache publish it. */
    private suspend fun localEntries(): Map<String, BloatData> {
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
     * 四条边界，缺一条就会出现"每次都连"或者"坏一次就再也不更新"：
     *  - 自动更新关着 → 不联网；
     *  - [CHECK_INTERVAL_DAYS] 天内已经核对过 → 不联网（列表一周动不了几次，天天问只是白花流量）；
     *  - 核对不上（离线、限流、代理坏了）→ 返回 null 且**不**记账，下次启动再试；
     *  - 库里那张家里的修正表搭同一次核对跑，失败不影响列表本身的更新。
     */
    suspend fun refresh(): Map<String, BloatData>? {
        if (!settings.autoUpdateBloatListFlow.first()) {
            LogUtils.i(TAG, "Bloat list auto-update is off; using the local list")
            return null
        }
        val today = todayKey()
        val lastChecked = settings.bloatListCheckedDayFlow.first()
        if (checkedRecently(lastChecked, today)) {
            LogUtils.i(TAG, "Bloat list checked $lastChecked; using the local list")
            return null
        }

        // 两件事分两步判，别用同一个闸门：
        //  * 「记了核对日期」= 这一周已经核对过（不管结果如何），决定要不要发请求；
        //  * 「手上有能用的缓存」= 决定要不要在已经是最新的时候收工。
        // 以前把两者绑在 `cached != null` 上：缓存读不出来（坏了 / 从没下过）时明明已经是最新，
        // 也会再下一次整张名单——而"这次核对失败"的判断是「一个包都不认识」，
        // 「已是最新所以不下载」会被读成"离线"。两件事必须分开。
        val remoteHash = fetchLatestHash()
        if (remoteHash.isEmpty()) {
            LogUtils.w(TAG, "Couldn't check for bloat list updates; using the local list")
            return null
        }
        if (remoteHash == settings.latestCommitHashFlow.first()) {
            LogUtils.i(TAG, "Bloat list is up to date (commit ${remoteHash.take(7)})")
            settings.setBloatListCheckedDay(today)
            return null
        }

        val l10n = translationTable()
        val start = currentTimeMillis()
        val raw = fetchBloatList() ?: return null
        val table = corrections()
        val parsed = runCatching {
            parseBloatList(raw).localisedWith(l10n).withOverrides(table)
        }
            .onFailure { LogUtils.e(TAG, "Downloaded bloat list is invalid", it) }
            .getOrNull() ?: return null

        runCatching { cache.write(raw.encodeToByteArray()) }
            .onFailure { LogUtils.e(TAG, "Failed to cache the bloat list", it) }
        settings.setLatestCommitHash(remoteHash)
        settings.setBloatListCheckedDay(today)
        LogUtils.i(TAG, "Updated bloat list in ${currentTimeMillis() - start}ms (${parsed.size} entries, commit ${remoteHash.take(7)})")
        return parsed
    }

    /**
     * 今天要不要跳过这次核对：上次核对在 [CHECK_INTERVAL_DAYS] 天之内就算"刚看过"。
     *
     * 记的是日期不是时间戳（见 [CantaSettings.bloatListCheckedDayFlow]），所以这里比的是日历天数；
     * 日期读不懂（旧版本写的怪值）当作没核对过，宁可多问一次也不要永远不再问。
     */
    private fun checkedRecently(lastCheckedKey: String, todayKey: String, windowDays: Long = CHECK_INTERVAL_DAYS): Boolean {
        if (lastCheckedKey.isBlank()) return false
        val last = runCatching { LocalDate.parse(lastCheckedKey) }.getOrNull() ?: return false
        val today = runCatching { LocalDate.parse(todayKey) }.getOrNull() ?: return false
        return today.toEpochDays().toLong() - last.toEpochDays().toLong() < windowDays
    }

    /** 本地日期键 `YYYY-MM-DD`：一天的判据按手机自己的时区，不是 UTC。 */
    @OptIn(ExperimentalTime::class)
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
