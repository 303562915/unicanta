package io.github.samolego.canta.data.community

import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.util.currentTimeMillis
import io.github.samolego.canta.data.recommend.AirportDetail
import io.github.samolego.canta.data.recommend.AirportRatingResult
import io.github.samolego.canta.data.recommend.AirportSubscription
import io.github.samolego.canta.data.recommend.AirportVoteResult
import io.github.samolego.canta.data.recommend.CheckInResult
import io.github.samolego.canta.data.recommend.CommunityAirport
import io.github.samolego.canta.data.recommend.PointLedgerEntry
import io.github.samolego.canta.data.recommend.PointOffer
import io.github.samolego.canta.data.recommend.PointOrder
import io.github.samolego.canta.data.recommend.PointRedeemResult
import io.github.samolego.canta.data.recommend.PointShelf
import io.github.samolego.canta.data.recommend.PointsAccount
import io.github.samolego.canta.data.recommend.SuggestedApp
import io.github.samolego.canta.data.recommend.SuggestedAppVote
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.TelemetryConfig
import io.github.samolego.canta.util.androidReleaseOf
import io.github.samolego.canta.util.romLabelOf
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** How long one backend call may take before the app treats it as never having arrived. */
private const val RPC_TIMEOUT_MS = 30_000L

/** How long the preflight waits for any answer at all: a phone with no route must be told quickly. */
private const val PROBE_TIMEOUT_MS = 6_000L

/**
 * 离线快照的默认地址（两份）：`db-backup.yml` 每天导出的 `site/db/plans.json`。
 *
 * Pages 那份走 GitHub 的国内 CDN，raw 那份直连 GitHub——两个都失效才算真读不到。
 */
const val PAGES_SNAPSHOT_URL = "https://303562915.github.io/unicanta/db/plans.json"
const val GITHUB_SNAPSHOT_URL =
    "https://raw.githubusercontent.com/303562915/unicanta/master/site/db/plans.json"

/**
 * 推荐条目快照的两个地址：同一份 `db-backup.yml` 生成，只是文件名换成 `recommendations.json`。
 *
 * 条目内容、赞成/反对数对所有人都一样（`p_voter` 在 SQL 里只影响 `mine`），
 * 所以它和方案列表一样可以从 GitHub 读，见 [suggestedApps]。
 */
const val PAGES_SUGGESTIONS_URL = "https://303562915.github.io/unicanta/db/recommendations.json"
const val GITHUB_SUGGESTIONS_URL =
    "https://raw.githubusercontent.com/303562915/unicanta/master/site/db/recommendations.json"

/**
 * 包的安全分数（`package_scores_for`）快照的两个地址。
 *
 * 那个函数是只读的、**不接受身份参数**：同一个 ROM 上所有人拿到同一份数据，所以整份表适合搬进
 * 仓库。实测 377,695 个数据块（第二大，最大的是 `conversations_for` 但它要身份，搬不了）。
 * 内容是 `[{"rom","rom_version","scores":[...]}]`，`rom_version = -1` 是整个家族。
 *
 * 客户端先在这里按 rom / rom_version 本地筛，筛不出来才问后端（见 Telemetry.fetchScores）。
 */
const val PAGES_SCORES_URL = "https://303562915.github.io/unicanta/db/package_scores.json"
const val GITHUB_SCORES_URL =
    "https://raw.githubusercontent.com/303562915/unicanta/master/site/db/package_scores.json"

/** 快照最多等这么久；它是兜底，不该比一次正常请求还慢。 */
private const val SNAPSHOT_TIMEOUT_MS = 15_000L

/**
 * 快照读失败之后，多久之内不再重试。
 *
 * 这条现在是「每次列方案都会走」的热路径（见 [offlinePlans]）。两个源各 15 秒超时，
 * 断网时一次刷新就是 30 秒的等待；失败后停 60 秒，期间直接走后端那条路。
 */
private const val SNAPSHOT_RETRY_BACKOFF_MS = 60_000L

/**
 * 快照内存缓存的有效期。
 *
 * **30 秒**（原 60 秒，2026-11-27 收紧）。钱不是问题，时效才是：
 * GitHub Pages 对静态文件基本不限流量，`plans.json` 现在 320 KB，一个月被读几千次
 * 也就几百 MB，离 100 GB 额度差着两个数量级。没理由为了省这点带宽让人等一分钟。
 *
 * 30 秒的依据是这个文件多久变一次：`db-backup` 现在 3–5 分钟跑一轮，所以
 * 「30 秒缓存」比「1 秒缓存」几乎不会多读到一次**内容不同**的版本，却能让
 * 「刚提交完、回去看列表」在半分钟内成立。
 *
 * 这条**必须有**：2026-11-27 有人提交方案后，自己开着 App 一天都看不到它，
 * 就是因为缓存原来永不过期（见 [snapshotCache]）。
 */
private const val SNAPSHOT_CACHE_TTL_MS = 30_000L

/**
 * 随包带的那一份方案快照（composeResources/files/plans_snapshot.json）。
 *
 * 由 db-backup.yml 在每次生成 site/db/plans.json 之后一并写进来，所以它和线上那份同源。
 * 只在两张网络源都读不到时用它 —— 断网的人看到的应该是"某个时刻的真实社区"，不是空白。
 */
private const val BUNDLED_SNAPSHOT_PATH = "files/plans_snapshot.json"

/** 随包带的推荐条目快照；与 [BUNDLED_SNAPSHOT_PATH] 同样的用途和生成方式。 */
private const val BUNDLED_SUGGESTIONS_PATH = "files/recommendations_snapshot.json"

/** One community plan: a set of packages someone removed and confirmed by a reboot. */
@Serializable
data class CommunityPlan(
    val id: Long,
    val title: String,
    val note: String,
    val packages: List<String>,
    val votes: Int,
    val comments: Int,
    val createdAt: String,
    val rebootedAt: String,
    /** Identity of the uploader; only that identity may delete the plan. */
    val owner: String = "",
    /** True for a plan uploaded without a reboot proof: older rows, kept and labelled as such. */
    val isTest: Boolean = false,
    /** The name the uploader uses today; empty when the backend does not send it (old deployment). */
    val uploader: String = "",
    /**
     * The phone the plan was made on. Empty on rows from a deployment that predates the device
     * columns; the page then cannot say whether the plan fits this phone, and treats it as "this
     * one" instead of inventing a mismatch.
     */
    val brand: String = "",
    val model: String = "",
    /**
     * The model's marketing name ("Redmi K70") when the uploader's ROM reported one. The retail
     * number in [model] is what MIUI/HyperOS puts in `ro.product.model`, and nobody recognises it.
     */
    val marketName: String = "",
    val rom: String = "",
    val romVersion: Int = 0,
    /**
     * The ROM's own build string ("16.0.3"), as the uploader's phone reported it. Empty on rows
     * from a deployment that predates the column.
     *
     * One model ships several builds a year, and the package set that is safe on one is not the
     * same claim on the next: two plans for the same phone can differ only in this. The label
     * shows it, [sameBuild] compares it, and the page warns when it is not the reader's build.
     */
    val romBuild: String = "",
    /**
     * The Android API level the uploader ran when the reboot check passed. 0 on rows from a
     * deployment that predates the column, which is why it only ever extends [deviceLabel] and
     * never stands in for something the row does not have.
     */
    val sdk: Int = 0,
) {
    /**
     * What the card shows next to the avatar: the uploader's name, or the identity number
     * ("#10000") for a plan from before names were synced.
     */
    val authorName: String
        get() = uploader.ifBlank { if (owner.isNotEmpty()) "#$owner" else "" }

    /**
     * "Redmi K70 · HyperOS 2", or "" when the backend did not say which device this is for.
     *
     * Every part here comes from the row — the *uploader's* phone and build — never from the reader's
     * own [DeviceProfile]: the detail page also shows the reader's device next to it, and a header
     * that repeated the reader's model would say "this plan is mine" about somebody else's phone.
     * A row that carries only a model code still reads as something: the code is better than silence.
     *
     * The Android release comes from the plan's own [sdk] — the level the uploader ran when the
     * reboot check passed — so the line describes one phone start to finish. Backends that predate
     * that column send no [sdk], and the line then stops after the ROM instead of guessing.
     */
    val deviceLabel: String
        get() = buildList {
            val phone = marketName.ifBlank { model }
            if (phone.isNotBlank()) add(phone)
            if (rom.isNotBlank()) add(romLabelOf(rom, romVersion, romBuild))
            androidReleaseOf(sdk).takeIf { it.isNotEmpty() }?.let { add("Android $it") }
        }.joinToString(" · ")

    /**
     * Whether this plan was made for [profile]'s own build. A plan from an older deployment
     * carries no device at all, and "unknown" must not read as "another phone": only a known,
     * different model or build does.
     */
    fun matches(profile: DeviceProfile?): Boolean {
        if (profile == null || model.isBlank()) return true
        if (!model.equals(profile.model, ignoreCase = true)) return false
        if (rom.isBlank()) return true
        return rom.equals(profile.rom, ignoreCase = true) && romVersion == profile.romVersion
    }

    /**
     * Whether this plan was proven on exactly the build [profile] runs — "16.0.501" against
     * "16.0.3" answers false.
     *
     * [matches] deliberately stays at the major version: hiding every plan of the same ROM because
     * the phone took a point update would empty the page for most readers. This is the narrower
     * question the detail page asks, to say out loud that the package list may not fit yet.
     */
    fun sameBuild(profile: DeviceProfile?): Boolean {
        if (profile == null) return true
        if (romBuild.isBlank() || profile.romBuild.isBlank()) return true
        return romBuild.equals(profile.romBuild, ignoreCase = true)
    }
}

/** Id of the local "test upload" row: never a server id, so it can be told apart. */
const val TEST_PLAN_ID: Long = -1L

/**
 * A comment under a plan or an app page.
 *
 * [replyTo] is what makes a thread: 0 for the comment that starts the conversation, the starter's
 * [id] for an answer to it. A backend that predates the thread table sends neither id nor parent,
 * which is why both default to 0 and the row still reads as a plain comment.
 */
@Serializable
data class PlanComment(
    val author: String,
    val body: String,
    val createdAt: String,
    /** The comment's own id; 0 when the server did not send one. */
    val id: Long = 0L,
    /** The comment this one answers; 0 when it starts a thread. */
    val replyTo: Long = 0L,
    /**
     * The identity number behind [author], as the backend keeps it. A display name can be picked
     * twice or changed at any time; the number is what says a comment belongs to the plan's
     * uploader ("作者"), and what keeps "delete" working after a rename.
     */
    val authorId: String = "",
    /** How many answers hang under this comment. */
    val replies: Int = 0,
    /**
     * How many hearts the comment carries. It is the only reaction a comment has: a wrong comment is
     * answered, not counted down, which is why there is no second number beside this one.
     */
    val likes: Int = 0,
    /** Whether this install is one of those hearts. */
    val liked: Boolean = false,
    /** The phone it was written on ("Redmi K70"); empty on comments written before it was kept. */
    val deviceModel: String = "",
    /** Its system, the way the ROM line reads ("HyperOS 2"); the API level is not shown at all. */
    val systemName: String = "",
) {
    /** "Redmi K70 · HyperOS 2", or "" when the writer's device is unknown. */
    val deviceLabel: String
        get() = buildList {
            if (deviceModel.isNotBlank()) add(deviceModel)
            if (systemName.isNotBlank()) add(systemName)
        }.joinToString(" · ")
}

/**
 * A comment's hearts, as the backend counts them: how many there are, and whether this install is
 * one of them. Returned by the tap so the page shows the server's number, not its own guess.
 */
data class CommentLikes(val likes: Int = 0, val liked: Boolean = false)

/**
 * What people said about removing one package: "it was fine" against "it broke something".
 *
 * Two scopes at once, because both are worth seeing: [canRemove]/[cannotRemove] only count the
 * votes cast on this model and ROM version, the `all` ones count every device.
 */
@Serializable
data class PackageVotes(
    val canRemove: Int = 0,
    val cannotRemove: Int = 0,
    val allCanRemove: Int = 0,
    val allCannotRemove: Int = 0,
    val bySystem: Map<String, Pair<Int, Int>> = emptyMap(),
) {
    /** Votes on this device. */
    val total: Int get() = canRemove + cannotRemove

    /** Votes on every device. */
    val allTotal: Int get() = allCanRemove + allCannotRemove

    /** Share of "this can go" on this device, null while nobody voted. */
    val share: Float? get() = if (total == 0) null else canRemove.toFloat() / total

    /** Share of "this can go" across all devices, null while nobody voted. */
    val allShare: Float? get() = if (allTotal == 0) null else allCanRemove.toFloat() / allTotal

    companion object {
        val EMPTY = PackageVotes()
    }
}

/**
 * An identity as the backend knows it: the token it resolves to, the number it owns, and the
 * display name the user picked. [name] is empty until they rename themselves, in which case the
 * install code is shown instead.
 */
data class UserProfile(val token: String, val userId: Long, val name: String = "")

/**
 * Reads and writes the community plans for the caller's own device. Matching is exact on model,
 * codename, ROM and ROM version: a plan for another build is worthless and potentially harmful,
 * so it is never shown.
 */
class PlanRepository(
    private val client: HttpClient,
    private val baseUrl: String = TelemetryConfig.BASE_URL,
    private val apiKey: String = TelemetryConfig.PUBLISHABLE_KEY,
    /**
     * 这台手机的身份凭据。上传方案、投票、写评论这三件"署上自己的名"的事会把它一起交给后端，
     * 后端据此确认号码真是这个 token 的，而不是信客户端自报的那一串——号码在界面上是公开的，
     * 只认它等于谁都能替别人署名。
     *
     * 默认拿不到凭据（空串），那时一个 `p_token` 也不发，后端照旧按自报的号码处理。
     * [delete] / [setNote] / [deleteComment] 那几条本来就把 token 当必填参数收，不受这个默认值影响。
     */
    private val identityToken: suspend () -> String = { "" },
/**
     * 后端整个不通时，社区方案从 GitHub 读。
     *
     * `.github/workflows/db-backup.yml` 每天把"重启验证通过的方案"导成 `site/db/plans.json`，形如
     * `plans_for` / `plans_all` 的返回行（点赞已算好、按点赞排序）。后端挂掉（2026-11-24 那三次
     * 全站空白）或者这台手机根本连不上 Supabase 时，社区至少还能看着这份离线快照。
     *
     * 只覆盖"读方案"这一条：上传、投票、评论都得写库，离线快照不假装能写。
     */
    private val snapshotUrl: String = GITHUB_SNAPSHOT_URL,
    /** 同一份快照的备用源：Pages 走国内 CDN，raw 走 GitHub，总有一个先通。 */
    private val snapshotMirrorUrl: String = PAGES_SNAPSHOT_URL,
    /**
     * 快照读到之后落盘的那一处；null 表示不落盘（测试、或接线的人还没接）。
     *
     * 2026-11-26 加。三个源的分工现在是：
     *   ① Pages / ② raw —— 有网时最新；
     *   ③ APK 内置 —— 断网时是**装机那天**的社区；
     *   ④ 这一处 —— 断网时是**上次联网时**的社区，比 ③ 新。
     *
     * ③ 和 ④ 的差别就是「装完机一直没网」和「昨天还联过网」：后者才是一个人真正会遇到的场景。
     * 落盘复用既有的 `PlanCache`（`StoredData.PLAN_CACHE`，本来就在存「上次看到的方案」），
     * 所以这里只传两个挂起函数，不把整个仓储和数据层绑在一起。
     */
    private val persistSnapshot: (suspend (List<CommunityPlan>) -> Unit)? = null,
    /** 读回 [persistSnapshot] 存下的那一份；null 表示没有或读不出来。 */
    private val loadPersisted: (suspend () -> List<CommunityPlan>)? = null,
    /** 推荐条目快照的 URL（Pages / raw），与方案快照同一套生成方式，只是文件名不同。 */
    private val suggestionUrl: String = GITHUB_SUGGESTIONS_URL,
    private val suggestionMirrorUrl: String = PAGES_SUGGESTIONS_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 快照读成功过一次就记着，别每次进列表都重新拉一遍几百 KB。
     *
     * **但它会过期**（[SNAPSHOT_CACHE_TTL_MS]）。2026-11-27 的真事：有人 03:36 提交了方案
     * （`plans.id=381`，真我 Neo8），而 `db-backup` 03:24 刚生成过快照、下一次要到 03:45，
     * 于是 GitHub 上那份还没有他。这时如果他（或任何人）在 03:24 之后已经打开过列表，
     * 这一格缓存**永远不会失效**——App 开着一天就一天看不到，刷新也没用，因为刷新走的是
     * 同一个 `offlinePlans()`，第一行就是 `snapshotCache?.let { return it }`。
     *
     * 所以缓存要带时间：过了 TTL 就重新去网上读一次。一分钟这个量级既不会让用户等
     * （有旧内容可用时先画旧的、后台换新的），也不会让一份几十 KB 的文件被反复下载。
     */
    private var snapshotCache: List<CommunityPlan>? = null

    /** [snapshotCache] 是什么时候填的；配合 [SNAPSHOT_CACHE_TTL_MS] 判过期。 */
    private var snapshotCachedAt: Long = 0L

    /** 推荐条目快照读成功一次的缓存；与 [snapshotCache] 同样的理由（一份几十 KB，别反复拉）。 */
    @Volatile
    private var suggestionCache: List<SuggestedApp>? = null

    /** [suggestionCache] 的填充时间，配合 [SNAPSHOT_CACHE_TTL_MS] 判过期。 */
    private var suggestionCachedAt: Long = 0L

    /**
     * 上一次「两个源都读不到」的时间（单调递增的本机时钟）。
     *
     * 2026-11-26 加：快照从「兜底」变成了 [plansAll] / [plansFor] 的**首选来源**，于是它从
     * 「后端挂了才走」变成「每次列表都要走」。Cache 只在**成功**时填，读失败就会反复重来 ——
     * 而这里两轮各 15 秒超时，真要断网，一次列表刷新就要卡 30 秒。
     *
     * 所以失败之后 [SNAPSHOT_RETRY_BACKOFF_MS] 内直接返回空（调用方于是走后端那条路），
     * 不再重试。用单调时钟而不是墙上时间：用户改系统时间不该影响这里的判断。
     */
    private var snapshotFailedAt: Long = 0L

    /**
     * 读离线快照：先 Pages，再 raw；两个都不通就是空。
     *
     * 失败不出声（不写 [lastError] / [lastFailureWasNetwork]）：这一趟是**内容来源**，
     * 不是一次业务请求，它失败时调用方看到的是「这边没有，去问后端」，不该被改写成另一种错误。
     */
    suspend fun offlinePlans(): List<CommunityPlan> {
        // 缓存只在 TTL 内算数（见 snapshotCache 的注释）：过了就重新去网上读一次。
        // 读失败时下面还有「磁盘 → APK 内置」两层，所以这里不用担心把内容弄丢。
        snapshotCache?.let {
            if (currentTimeMillis() - snapshotCachedAt < SNAPSHOT_CACHE_TTL_MS) return it
        }
        // 刚失败过就别再去网上试：这条现在是热路径（每次列表），不能让它反复等满超时。
        // 但**不能就此返回空** —— 那把这个"保底"自己变成了坏消息。退避期间读本地：
        // 先磁盘（上次联网那份），再 APK 内置（打包那份）。
        if (snapshotFailedAt != 0L && currentTimeMillis() - snapshotFailedAt < SNAPSHOT_RETRY_BACKOFF_MS) {
            val persisted = runCatching { loadPersisted?.invoke() }.getOrNull().orEmpty()
            if (persisted.isNotEmpty()) {
                // 磁盘那份也不标成「刚读到」：它可能比网上那份旧，下一轮该继续尝试联网。
                return persisted
            }
            return bundledSnapshot()
        }
        for (url in listOf(snapshotMirrorUrl, snapshotUrl)) {
            if (url.isBlank()) continue
            val text = runCatching {
                withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
                    val response = client.get(url)
                    if (response.status.isSuccess()) response.bodyAsText() else null
                }
            }.getOrNull() ?: continue
            val rows = parsePlans(text)
            // 解析出一个空表多半是文件被写坏了：那也算没读到，接着试下一个源。
            if (rows.isEmpty()) continue
            snapshotCache = rows
            snapshotCachedAt = currentTimeMillis()
            snapshotFailedAt = 0L
            // 同时落盘：下次断网时读的就是这一份（比 APK 内置那份新）。
            // 失败吞掉 —— 存不进去不该让这次成功的读取变成失败。
            runCatching { persistSnapshot?.invoke(rows) }
            return rows
        }
        // 两张网都断了（没网、Pages 被墙、raw 超时）→ 先用**上次联网时落盘的那一份**。
        //
        // 2026-11-26 加：前两个源都在网络上，一个断网的人（或者一个只开了内网的人）会得到
        // 空列表，而方案列表现在是 [plansAll] / [plansFor] 的**首选**来源 —— 空列表直接等于
        // 「社区是空的」，那是在说假话。
        //
        // 顺序是「磁盘 → APK 内置」而不是反过来：磁盘那份是这个手机上**最近一次**看到的社区，
        // 内置那份是**打包那天**的。只有在从来没联网成功过（磁盘是空的）时才退回内置。
        snapshotFailedAt = currentTimeMillis()
        val persisted = runCatching { loadPersisted?.invoke() }.getOrNull().orEmpty()
        if (persisted.isNotEmpty()) {
            snapshotCache = persisted
            return persisted
        }
        return bundledSnapshot()
    }

    /**
     * 读随包那一份方案快照；读不到就是空表（文件被删、或打包时漏了）。
     *
     * 单独提出来是因为它现在有两个调用点：网络两张源都失败时，以及 [SNAPSHOT_RETRY_BACKOFF_MS]
     * 的退避窗口内。两处都必须给出「有内容」，否则保底这一层等于不存在。
     */
    private suspend fun bundledSnapshot(): List<CommunityPlan> {
        // 同一份 TTL：内置那份是 APK 里读出来的，内容不会变，但**缓存里可能已经换成网络那份**
        // （磁盘 → 内置这条链上，读到的可能是别的来源留下的旧值），所以同样按时间放行。
        snapshotCache?.let {
            if (currentTimeMillis() - snapshotCachedAt < SNAPSHOT_CACHE_TTL_MS) return it
        }
        val rows = runCatching {
            parsePlans(Res.readBytes(BUNDLED_SNAPSHOT_PATH).decodeToString())
        }.getOrDefault(emptyList())
        // 成功读出来就进缓存：后面整个会话都不必再碰这个文件。
        if (rows.isNotEmpty()) {
            snapshotCache = rows
            // 内置那份不标成新鲜：它最旧，下一轮该继续尝试联网。
        }
        return rows
    }

    /**
     * 这一次写调用要带上的凭据；取不到就是空串。
     *
     * 出错吞掉：凭据读失败不该让一次投票整个失败，最坏只是回到"信自报号码"的老样子。
     */
    private suspend fun callerToken(): String =
        runCatching { identityToken() }.getOrDefault("").trim()

    /** 级别修正这边要同一份凭据；`LevelFixRepository` 拿不到 private 的 [callerToken]。 */
    suspend fun callerTokenForLevelFix(): String = callerToken()

    /**
     * 一个包的级别修正（`level_fixes_for`），原样返回 JSON。
     *
     * 级别修正放在 [io.github.samolego.canta.data.community.LevelFixRepository] 里解析，这里只管
     * 发请求：应用的列表页每进一次已经四个请求了，级别修正不能让它变五个。
     */
    suspend fun levelFixesFor(body: String): String? = rpc("level_fixes_for", body)

    /** 提一条级别修正（`level_fix_add`），返回原样 JSON。 */
    suspend fun addLevelFix(body: String): String? = rpc("level_fix_add", body)

    /** 给级别修正投票（`level_fix_vote`），返回原样 JSON。 */
    suspend fun voteLevelFix(body: String): String? = rpc("level_fix_vote", body)

    /**
     * 三个维度（级别 / 后果 / 系统应用）的投票现状（`level_fix_ballots_for`），原样返回 JSON。
     *
     * 解析在 [LevelFixRepository] 里做，与上面三条同一个分工：这里只管发请求。
     */
    suspend fun levelFixBallotsFor(body: String): String? = rpc("level_fix_ballots_for", body)

    /** 投三个维度里的一个（`level_fix_ballot_vote_v2`：带显示名），返回投完之后三个维度的完整计数。 */
    suspend fun voteLevelFixBallot(body: String): String? = rpc("level_fix_ballot_vote_v2", body)

    /** 最近的投票记录（`level_fix_ballot_history`），原样返回 JSON：谁在哪个维度投了什么。 */
    suspend fun levelFixBallotHistory(body: String): String? = rpc("level_fix_ballot_history", body)

    /**
     * Why the last call came back empty: the backend's own message when it refused (a missing
     * function, a rejected upload, a bad key), or the network error. "上传失败" without this is a
     * dead end, so the pages that can fail show it.
     */
    var lastError: String? = null
        private set

    /**
     * True when the last call failed because nothing answered — no route out, a blocked host, a
     * timeout — rather than because the server refused what was sent. An upload that never left the
     * phone has to be told apart from one the server rejected: only the first is a network or VPN
     * problem, and only the first is worth telling the user to check their connection.
     */
    var lastFailureWasNetwork: Boolean = false
        private set

    /**
     * 把「上一次调用」的错误状态清成「答上了」。
     *
     * 快照命中时**必须**调它：那条路一个请求都不发，于是 [lastError] /
     * [lastFailureWasNetwork] 会保留上一次调用留下的值；调用方普遍按
     * `lastError == null && !lastFailureWasNetwork` 判断这一趟的结果可不可信，
     * 残留值会把一份明明拿到手的列表判成「没读到」。
     *
     * 2026-11-27 的真事：别人给某人的方案点了赞，他从互动消息点进去却看不到那个方案 ——
     * 列表其实查到了（快照里就有），只是被残留的错误状态判成失败，页面停在旧内容上。
     *
     * **新增任何「不发请求就返回」的路径，都要调这个。**
     */
    private fun markAnswered() {
        lastError = null
        lastFailureWasNetwork = false
    }

    /**
     * The newest row of each kind this install was actually shown by [inboxPage], keyed by kind.
     *
     * The inbox is four sources merged into one stream, and the page hands the whole stream to a
     * list that filters it in place: switching tabs makes no call and reports nothing, so "this tab
     * was opened" does not exist as a signal here. What does exist is the rows that reached the
     * screen — and that is the honest evidence for moving a tab's read position. A kind whose rows
     * were served has been seen; a kind that still has news below the first page has not.
     *
     * Only ever grows, and only ever forwards: an old entry is behind the stored cursor and does
     * nothing. Replaced wholesale rather than mutated, so a concurrent read sees one consistent map.
     */
    private var servedSinceCount: Map<InboxKind, String> = emptyMap()

    /** Which identity [servedSinceCount] belongs to: another account's rows are not evidence here. */
    private var servedOwner: String = ""

    /** What one call to the backend came back as. */
    private sealed interface Call {
        class Ok(val text: String) : Call
        class Refused(val reason: String) : Call
        object Unreachable : Call
    }

    /**
     * Whether the community backend answers at all.
     *
     * Uploading needs the request to leave the phone, and on many networks that only happens
     * through a VPN or a proxy. Any answer proves reachability — the REST root answering "not
     * found" is still a host that answered — so the check is about the route, not about the call.
     */
    suspend fun isReachable(): Boolean =
        answers { client.head(baseUrl) { header("apikey", apiKey) } } ||
            answers { client.get(baseUrl) { header("apikey", apiKey) } }
    // 这里不写 BackendHealth：调用它的人自己知道该怎么处理这个答案。
    // 主界面的确认探测要连着两次失败才置位，页面上的上传预检只把结果画在自己的提示条里——
    // 谁都不是"这一次失败就等于断网"。

    /** True when [request] came back inside [PROBE_TIMEOUT_MS]; a connection that never opens is not an answer. */
    private suspend fun answers(request: suspend () -> Any?): Boolean =
        // `withTimeoutOrNull` answers null when the probe ran out of time, and runCatching answers a
        // failure; both are "no". The Boolean is spelled out on the lambda rather than inferred:
        // the nested ternaries of the two wrappers are what turned this into a Boolean? once.
        runCatching {
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                request()
                true
            } ?: false
        }.getOrDefault(false)

    private suspend fun call(name: String, body: String): Call {
        val answered = runCatching {
            withTimeoutOrNull(RPC_TIMEOUT_MS) {
                val response = client.post(baseUrl + name) {
                    header("apikey", apiKey)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(body)
                }
                if (response.status.isSuccess()) {
                    Call.Ok(response.bodyAsText())
                } else {
                    // PostgREST answers a refusal with a JSON body carrying `message`; keep a short
                    // form of it so the upload dialog can say what the server did not like.
                    val text = runCatching { response.bodyAsText() }.getOrDefault("")
                    val message = runCatching {
                        Json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull().orEmpty().ifBlank { text.take(200) }
                    Call.Refused(message.ifBlank { "HTTP ${response.status.value}" })
                }
            }
        }
        // A call that threw, and a call that ran out of time, both mean nothing answered.
        return answered.getOrNull() ?: Call.Unreachable
    }

    private suspend fun rpc(name: String, body: String): String? = when (val result = call(name, body)) {
        is Call.Ok -> {
            lastFailureWasNetwork = false
            lastError = null
            BackendHealth.reportReachable()
            result.text
        }
        is Call.Refused -> {
            lastFailureWasNetwork = false
            lastError = result.reason
            BackendHealth.reportReachable()
            null
        }
        Call.Unreachable -> {
            lastFailureWasNetwork = true
            lastError = null
            BackendHealth.reportUnreachable()
            null
        }
    }

    /**
     * True when the last call failed only because this deployment does not have the function yet:
     * an older backend answers PostgREST with `PGRST202` / "Could not find the function". The pages
     * use it to fall back to the calls that deployment does have instead of reporting a failure.
     */
    private val functionMissing: Boolean
        get() = lastError?.let { it.contains("PGRST202") || it.contains("Could not find the function") } == true

    /** Verified plans for [profile], most liked first; empty on any failure. */
    suspend fun plansFor(profile: DeviceProfile?): List<CommunityPlan> {
        profile ?: return emptyList()

        // **先在 GitHub 快照里本地筛**，筛不出来才问后端。方向与 [plansAll] 同一个理由：
        // `plans_for` 实测 88 次调用读掉 12,192 个数据块，而它要回答的问题只是
        // 「哪些方案是给这台机型的」——快照里 model / rom / rom_version / rom_build 都在，
        // 在本地过滤是纯内存操作，一次网络往返都不用。
        //
        // 尺子与后端那句 `where model = ... and rom = ... and rom_version = ...` 对齐：
        // 型号、ROM、主版本三项相同即算。**不能**加上 romBuild 做硬过滤 ——
        // 那会让刚打了一个点更新的人看到空页面（后端也只是把它排前面，不排除）。
        val snapshot = offlinePlans()
        if (snapshot.isNotEmpty()) {
            val mine = snapshot.filter { plan ->
                // 型号必须相符：这是唯一真实的「这台机器」标识。
                if (!plan.model.equals(profile.model, ignoreCase = true)) return@filter false
                // ROM 系列**同族**即算，不要求字面相等。理由见 [sameRomFamily]：
                // 同一台真我 Neo8 昨天报 `realmeui 7`、今天报 `coloros 17`（同一个人）。
                // 原因是 OPPO/realme 系那几个系统属性有时读得到、有时读不到，读不到就落到下一个键。
                if (!sameRomFamily(plan.rom, profile.rom)) return@filter false
                // **同族但不同名时，不比版本号**。这是上面那台机器的第二个坑：
                // realme UI 的 7 和它 ColorOS 底座的 17 是同一代系统的**两个编号体系**，
                // 数字天然对不上（realme UI N ↔ ColorOS N+10 的说法在港台论坛也是这么写的）。
                // 同名时才比版本：那时数字是同一套体系，跨代该区分开。
                if (plan.rom.equals(profile.rom, ignoreCase = true)) {
                    if (plan.romVersion != profile.romVersion && plan.romVersion != 0 && profile.romVersion != 0) {
                        return@filter false
                    }
                }
                true
            }
            // **必须清掉错误状态**：调用方（`PlanDetailScreen` 等）靠
            // `lastError == null && !lastFailureWasNetwork` 判断「这一次的答案可不可信」。
            // 快照命中时不发任何请求，这两个字段会**保留上一次调用的残留值** ——
            // 之前任何一次失败都会让它们非空，于是调用方把一份明明拿到手的列表判成「没读到」，
            // 屏幕上就停着旧内容。这正是 2026-11-27「别人给我点了赞、点进去却看不到那个方案」
            // 的形状：列表其实查到了，只是被判成失败。
            markAnswered()
            // 排序照后端：**同一个 build 的排前面**（`p_rom_build` 的作用就是这个），
            // 然后才是票数多的在前。不能反过来——先按票数排的话，同一 build 的那几条
            // 会被票数更高的别的 build 挤到后面，"这台机器实测过"就看不出来了。
            //
            // 上面放宽了 ROM 系列，这里补一道：**ROM 字面相符的排在同族前面**。
            // 那台真我 Neo8 的 5 条方案里，`realmeui` 那条才是他昨天在这台机器上实测的，
            // `coloros` 那几条是今天属性读不到时记下的同族数据 —— 先给他看真正属于他的。
            return mine.sortedWith(
                compareByDescending<CommunityPlan> { it.rom.equals(profile.rom, ignoreCase = true) }
                    .thenByDescending { it.romBuild.equals(profile.romBuild, ignoreCase = true) }
                    .thenByDescending { it.votes },
            )
        }

        val build = profile.romBuild
        val body = buildJsonObject {
            put("p_model", profile.model)
            put("p_codename", profile.device)
            put("p_rom", profile.rom)
            put("p_rom_version", profile.romVersion)
            // The exact build: the backend then puts the plans proven on this same build on top.
            // It does not filter the others out — hiding a whole major version would empty the page
            // for everybody who just took a point update.
            if (build.isNotBlank()) put("p_rom_build", build)
        }.toString()
        rpc("plans_for", body)?.let { return parsePlans(it) }

        // A deployment without the `p_rom_build` parameter refuses the call above; ask again the
        // way it does understand, and the rows simply carry no build.
        if (functionMissing && build.isNotBlank()) {
            val legacy = buildJsonObject {
                put("p_model", profile.model)
                put("p_codename", profile.device)
                put("p_rom", profile.rom)
                put("p_rom_version", profile.romVersion)
            }.toString()
            rpc("plans_for", legacy)?.let { return parsePlans(it) }
        }
        return emptyList()
    }

    /** Every plan row in [text]; an answer the parser cannot read is an empty list, not a crash. */
    private fun parsePlans(text: String): List<CommunityPlan> = runCatching {
        json.parseToJsonElement(text).jsonArray.mapNotNull { element -> parsePlan(element.jsonObject) }
    }.getOrDefault(emptyList())

    /**
     * The plans of one model (and optionally one of its builds) — what the model chips open.
     *
     * [rom] empty asks for every build of the model, which is the "全部" chip. The answer carries
     * the device columns, so the list can say which of these plans are for another phone. Null when
     * the backend could not be reached; an empty list is an answer ("nobody uploaded here yet").
     *
     * A deployment without `plans_for_model` still answers `plans_for`, which matches on model and
     * build alone when the codename is left out: the same rows, without the device columns.
     */
    suspend fun plansForModel(
        brand: String,
        model: String,
        rom: String = "",
        romVersion: Int = 0,
        romBuild: String = "",
    ): List<CommunityPlan>? {
        if (model.isBlank()) return null
        val body = buildJsonObject {
            put("p_brand", brand)
            put("p_model", model)
            put("p_rom", rom)
            put("p_rom_version", romVersion)
            // The chip the user picked is one build ("16.0.501"), not the whole major version.
            if (rom.isNotBlank() && romBuild.isNotBlank()) put("p_rom_build", romBuild)
        }.toString()
        rpc("plans_for_model", body)?.let { return parsePlans(it) }

        if (functionMissing) {
            val legacy = buildJsonObject {
                put("p_brand", brand)
                put("p_model", model)
                put("p_rom", rom)
                put("p_rom_version", romVersion)
            }.toString()
            rpc("plans_for_model", legacy)?.let { return parsePlans(it) }

            if (rom.isNotBlank() && romVersion > 0) {
                val viaPlansFor = buildJsonObject {
                    put("p_model", model)
                    // Empty codename: `plans_for` then matches the model and build, whichever the
                    // device codename of that phone is.
                    put("p_codename", "")
                    put("p_rom", rom)
                    put("p_rom_version", romVersion)
                }.toString()
                rpc("plans_for", viaPlansFor)?.let { return parsePlans(it) }
            }
        }
        return null
    }

    /**
     * Every verified plan the backend has, whatever the phone — the ranking's widest scope.
     *
     * A phone that matches nothing (a fresh install on a model nobody uploaded for, a young
     * community) would otherwise read an empty page although the server has plans. Null when the
     * backend could not be reached; an empty list is an answer ("nobody uploaded anything yet").
     */
    suspend fun plansAll(limit: Int = 200): List<CommunityPlan>? {
        if (limit <= 0) return emptyList()
        // **先读 GitHub 上的快照**，不通才问后端。这是 2026-11-26 调换的方向，
        // 原来反着来（先问后端，网络失败才读快照），代价是每台手机每次都打一次
        // `plans_all` —— 实测 90 次调用读掉 596,253 个数据块（约 4.6 GB），
        // 而那台免费实例只有 426 MB 内存、磁盘突发余额 0%。方案列表是**所有人看同一份**的
        // 只读内容，没有理由让每台手机各自去库里算一遍。
        //
        // 快照由 `db-backup.yml` 定时生成（`site/db/plans.json`，字段与 `plans_all` 返回逐列对齐，
        // 点赞与评论数都已算好），Pages 的流量额度对它来说远远够用。
        //
        // 代价只有一个：别人刚发的方案最多晚一轮（15–60 分钟）才出现在这里。这是**可以接受**的
        // ——这一趟只负责"有哪些方案、各多少票"，自己的投票在本地先更新、后台再同步。
        val snapshot = offlinePlans()
        if (snapshot.isNotEmpty()) {
            // 与 [plansFor] 同一条规矩：快照命中也要清掉错误状态，否则上一次任何一次失败
            // 都会让调用方把这份列表判成「没读到」。
            markAnswered()
            return snapshot.take(limit)
        }

        // 快照读不到（没发布过 / 两个源都断）：退回后端，然后再退回"没有"。
        val body = buildJsonObject { put("p_limit", limit) }.toString()
        rpc("plans_all", body)?.let { return parsePlans(it) }
        return null
    }

    /**
     * 全社区里收了这个包的方案，不分机型：应用详情页右上角那个抽屉的第二部分。
     *
     * 「本机型」那一趟只回答「这台手机上有没有人证明过这个包能卸」，而应用页真正想问的是
     * 「别人怎么卸的」——老机型、别的 ROM、甚至同品牌另一台，里面的包名和顺序照样能抄。
     * 所以本机型为空时不能画成「还没有人写过」，那是凭空的结论。按点赞排序，等点赞时才按时间。
     */
    suspend fun plansForPackage(packageName: String, limit: Int = 200): List<CommunityPlan>? {
        if (packageName.isBlank()) return null
        // 没有「按包名查方案」的后端函数时，退成拉全量再在本地筛：社区方案量级还不大，
        // 一次 200 条已经把「谁收了这个包」覆盖得差不多了。后端不通时 `plansAll` 自己会退回
        // GitHub 快照，所以这一条在没有网、或者后端整个挂掉的时候照样有内容。
        plansAll(limit)?.let { all -> return all.filter { packageName in it.packages } }
        return null
    }

    /**
     * The phones this exact set of packages was reboot-proven on, most-proven first.
     *
     * A plan page says which device *the uploader* held; this says whether anyone else with the
     * same list came back with a working phone, which is the question a reader actually has. The
     * set is compared by content, so two uploads with the packages in a different order match.
     *
     * ## 纯本地算（2026-11-27 改成不再发请求）
     *
     * `plan_device_coverage` 的 SQL 只有一句话：把 `plans` 里 `is_test = false`、`packages` 去重
     * 排序后**等于**这个集合的行拿出来，按
     * `(brand, model, market_name, rom, rom_version, only_build, sdk)` 分组数 `count(*)`，
     * 按 `verified desc, model asc` 排前 `p_limit`（夹在 1..20）。它**不读身份参数**、不加任何
     * 客户端拿不到的字段；唯一的"额外知识"是 `private.only_build(model, rom, rom_version)` ——
     * 那是「同一型号同一 ROM 主版本下没有歧义时的那一个 build」，纯粹由 `plans` 自己的行推出来，
     * 见 [coverageBuild]。
     *
     * 这份行集合正是 `site/db/plans.json` 那份快照（[offlinePlans]）的来源：`db-backup.yml`
     * 往里导的就是 `plans` 里重新启动验证过的行，`brand / model / market_name / rom /
     * rom_version / rom_build / sdk / packages` 逐列都在。所以「这些包在别的机型上被卸载过吗」
     * 在本地就是一次过滤 + 聚合：**不联网、不算错**。
     *
     * 唯一的等价性前提是「快照里装的就是全部非测试方案」：本是 151 行、`reboot_verified` 全真、
     * 快照 148 行（少的三行是绝大快照导出后几分钟才传的），也就是说快照漏掉的最多只有比快照更新的
     * 那几条。所以仍然保留一次后端回退：快照为空（没网、没打过包那层也没读出来）时才问库，
     * 拿到结果后不再缓存 —— 不致于让这条路径变成每次都要往返一次。
     *
     * Empty when neither the snapshot nor the backend answers — the page then shows the
     * uploader's device alone, as before.
     */
    suspend fun deviceCoverage(packages: List<String>, limit: Int = 5): List<DeviceCoverage> {
        if (packages.isEmpty()) return emptyList()
        // 后端那句 `limit greatest(1, least(coalesce(p_limit, 5), 20))`，本地照抄同一个尺子。
        val capped = limit.coerceIn(1, 20)

        val snapshot = offlinePlans()
        if (snapshot.isNotEmpty()) return coverageFrom(snapshot, packages, capped)

        val body = buildJsonObject {
            putJsonArray("p_packages") { packages.forEach { add(JsonPrimitive(it)) } }
            put("p_limit", limit)
        }.toString()
        val text = rpc("plan_device_coverage", body) ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
                val o = element.jsonObject
                val model = o["model"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (model.isBlank()) return@mapNotNull null
                DeviceCoverage(
                    brand = o["brand"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    model = model,
                    marketName = o["market_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    rom = o["rom"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    romVersion = o["rom_version"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                    romBuild = o["rom_build"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    sdk = o["sdk"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                    verified = o["verified"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                )
            }
        }.getOrNull().orEmpty()
    }

    /**
     * `plan_device_coverage` 的本地版：在 [all] 里找包集合**完全相同**的方案，按机型+build+SDK 聚合。
     *
     * 与 SQL 逐步对齐（每一条都不能省）：
     *  - `is_test = false`、`packages is not null`：测试方案不算，没写包的跳过；
     *  - 集合比较：两边都去重排序后比 —— 顺序无关、重复包不影响（SQL 用的也是
     *    `array_agg(distinct ... order by ...)` 再相等）；
     *  - 分组键带上 `market_name` / `sdk`：同一台机器如果两条方案填了不同的名字或不同的 SDK，
     *    后端会算成两行，这里也一样；
     *  - `rom_build` 走 [coverageBuild]，不是简单取 `p.rom_build`；
     *  - 排序 `verified desc, model asc`：`model` 用字典序（SQL 的 `asc` 是库排序规则，
     *    对 `23127PN0CC` 这种型号，与 `compareTo` 一致）。
     */
    private fun coverageFrom(
        all: List<CommunityPlan>,
        packages: List<String>,
        limit: Int,
    ): List<DeviceCoverage> {
        val mine = packages.filter { it.isNotBlank() }.distinct().sorted()
        if (mine.isEmpty()) return emptyList()
        val wanted = mine.toSet()

        val matching = all.filter { plan ->
            !plan.isTest && plan.packages.isNotEmpty() && plan.packages.toSet() == wanted
        }
        if (matching.isEmpty()) return emptyList()

        // `only_build` 看的是**整张** plans 表（那是快照的全部来源），所以用 all，而不是 matching。
        val rows = matching.groupBy { plan ->
            CoverageKey(
                brand = plan.brand,
                model = plan.model,
                marketName = plan.marketName,
                rom = plan.rom,
                romVersion = plan.romVersion,
                build = coverageBuild(plan, all),
                sdk = plan.sdk,
            )
        }.map { (key, plans) ->
            DeviceCoverage(
                brand = key.brand,
                model = key.model,
                marketName = key.marketName,
                rom = key.rom,
                romVersion = key.romVersion,
                romBuild = key.build,
                sdk = key.sdk,
                verified = plans.size,
            )
        }

        return rows.sortedWith(
            compareByDescending<DeviceCoverage> { it.verified }.thenBy { it.model },
        ).take(limit)
    }

    /**
     * `private.only_build(model, rom, rom_version)` 的本地复刻。
     *
     * 后端那句的意思是：在**同型号、同 ROM、同主版本**的所有行里，如果位数最多的那个 build
     * （数字段最多的，如 `OS4.0.5.0.XAFCNXM` 比 `OS4.0` 多）是**唯一**的，就用它；有多个并列
     * （或者一个都没有）就返回空串。调用方再 `coalesce(..., p.rom_build, '')` 回退到这一行自己的 build。
     *
     * 为什么要它：同一个机型同一主版本下，有的行填了完整 build、有的没填，页面要靠这条把它们
     * 归到同一个 build 上显。**型号/ROM 的比较用 `lower()`**（后端口径），所以这里也忽略大小写。
     *
     * 代价是每个被聚合的行扫一遍 [all]；方案量级是百来条，可忽略。
     */
    private fun coverageBuild(plan: CommunityPlan, all: List<CommunityPlan>): String {
        val known = all.asSequence()
            .filter { it.romBuild.isNotBlank() }
            .filter { it.model.equals(plan.model, ignoreCase = true) }
            .filter { it.rom == plan.rom && it.romVersion == plan.romVersion }
            .map { it.romBuild }
            .distinct()
            .toList()
        if (known.isNotEmpty()) {
            val segmentsOf = { build: String -> Regex("[0-9]+").findAll(build).count() }
            val most = known.maxOf { segmentsOf(it) }
            val winners = known.filter { segmentsOf(it) == most }
            if (winners.size == 1) return winners.single()
        }
        return plan.romBuild
    }

    /** 后端 `group by` 的那一组列，原样本地当键用。 */
    private data class CoverageKey(
        val brand: String,
        val model: String,
        val marketName: String,
        val rom: String,
        val romVersion: Int,
        val build: String,
        val sdk: Int,
    )

    /**
     * Every model the community has plans for, one row per build, with how many plans and people
     * each has. Null when the backend could not be reached or predates the catalog call.
     */
    suspend fun catalog(): List<CatalogBuild>? {
        val text = rpc("plan_catalog", "{}") ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonArray.mapNotNull { element -> parseCatalogBuild(element.jsonObject) }
        }.getOrNull()
    }

    private fun parseCatalogBuild(o: JsonObject): CatalogBuild? {
        val model = o["model"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (model.isBlank()) return null
        return CatalogBuild(
            brand = o["brand"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            model = model,
            marketName = o["market_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            rom = o["rom"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            romVersion = o["rom_version"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            romBuild = o["rom_build"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            plans = o["plans"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            people = o["people"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            modelPlans = o["model_plans"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            modelPeople = o["model_people"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
        )
    }

    /**
     * Every plan [owner] uploaded, whatever device it was matched to, newest first.
     *
     * "Manage my plans" needs this: after an OS update the plan no longer matches this phone's
     * `plans_for` row, so a list built from that call would lose the very plans the user wants to
     * delete. Empty when the backend cannot be reached or predates the call.
     */
    suspend fun plansByOwner(owner: String): List<CommunityPlan> {
        if (owner.isEmpty()) return emptyList()
        val text = rpc("plans_by_owner", buildJsonObject { put("p_owner", owner) }.toString())
            ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(text).jsonArray.mapNotNull { parsePlan(it.jsonObject) }
        }.getOrDefault(emptyList())
    }

    /**
     * One plan by id, whatever device it was uploaded for.
     *
     * The page a comment or a notification links to may not be in this phone's own list: another
     * model, a plan the list did not carry, or one whose ROM version changed since. Asking for that
     * one plan is what still lets the page open instead of "找不到这个方案". Null when it is gone —
     * or when the deployment predates `plan_get`.
     */
    suspend fun planById(id: Long): CommunityPlan? {
        if (id <= 0L) return null
        val text = rpc("plan_get", buildJsonObject { put("p_id", id) }.toString()) ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonArray.firstNotNullOfOrNull { parsePlan(it.jsonObject) }
        }.getOrNull()
    }

    /** One row of `plans_for` / `plan_get`, or null when it carries no id. */
    private fun parsePlan(o: JsonObject): CommunityPlan? {
        val id = o["id"]?.jsonPrimitive?.longOrNull ?: return null
        return CommunityPlan(
            id = id,
            title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            note = o["note"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            packages = (o["packages"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            votes = o["votes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            comments = o["comments"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            rebootedAt = o["rebooted_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            owner = o["owner"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            isTest = o["is_test"]?.jsonPrimitive?.booleanOrNull ?: false,
            uploader = o["owner_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            // Absent on a deployment that predates the device catalog: the page then treats the
            // plan as this phone's, which is what the old backend meant anyway.
            brand = o["brand"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            model = o["model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            marketName = o["market_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            rom = o["rom"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            romVersion = o["rom_version"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            // The exact build the uploader ran ("16.0.3"); absent on a deployment that predates
            // the column, and the row then says only its major version.
            romBuild = o["rom_build"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            // The API level the uploader ran; absent on a deployment that predates the column.
            sdk = o["sdk"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
        )
    }

    /**
     * 一次性的上传挑战：服务端签发、绑来源 IP、5 分钟过期，提交时用一次就作废。
     *
     * 拿到就跟着 [submit] 一起发，服务端据此把这条方案记成 `challenge`；拿不到（后端还没这个
     * 函数、网络不通）就返回 null，上传照旧走老路径，库里记成 `self-reported`。
     *
     * 这里失败不该让上传失败：挑战是加强，不是前提。它挡的是「截获一份提交再原样重放」，
     * 不是「伪造一份提交」——后者在纯客户端模型下没有可验证的证据能挡。
     */
    private suspend fun uploadChallenge(): String? =
        when (val result = call("upload_challenge", buildJsonObject { put("p_kind", "plan_submit") }.toString())) {
            is Call.Ok -> runCatching {
                json.parseToJsonElement(result.text).jsonObject["nonce"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.takeIf { it.isNotBlank() }
            else -> null
        }

    /**
     * Uploads [packages] as a plan.
     *
     * The app only calls this with a batch the restart check proved, so [test] is false on every
     * upload this build makes; the flag is kept because the server still understands and stores it
     * for rows that older builds sent, and the list has to label those for what they are.
     */
    suspend fun submit(
        profile: DeviceProfile,
        title: String,
        note: String,
        packages: List<String>,
        rebootedAt: String,
        reporter: String,
        owner: String = "",
        test: Boolean = false,
    ): Long? {
        if (packages.isEmpty()) return null
        val body = buildJsonObject {
            put("brand", profile.brand)
            put("model", profile.model)
            // The name people recognise, so the model chips read "Redmi K70" and not "23113RKC6C".
            put("market_name", profile.marketName)
            put("codename", profile.device)
            put("rom", profile.rom)
            put("rom_version", profile.romVersion)
            // The exact build ("16.0.3"): one model ships several a year, and the row has to say
            // which one its package list was proven on. A backend that predates the column ignores
            // the key; the plan is then matched on the major version alone, as before.
            put("rom_build", profile.romBuild)
            put("sdk", profile.sdk)
            put("title", title)
            put("note", note)
            putJsonArray("packages") { packages.forEach { add(it) } }
            put("reboot_verified", true)
            put("rebooted_at", rebootedAt)
            put("reporter", reporter)
            put("owner", owner)
            put("test", test)
        }.toString()
        val proof = callerToken()
        // 先取服务端的挑战：同一份提交被截获重放时，这个 nonce 在第一次提交里已经作废。
        val challenge = uploadChallenge()
        return rpc(
            "plan_submit",
            buildJsonObject {
                put("p_plan", Json.parseToJsonElement(body))
                if (proof.isNotEmpty()) put("p_token", proof)
                if (challenge != null) put("p_nonce", challenge)
            }.toString(),
        )
            ?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.longOrNull }.getOrNull() }
    }

    /**
     * Deletes a plan uploaded by [owner]. The server needs the [token] of that identity as well,
     * so this returns false for somebody else's plan — or for a guessed number — instead of
     * doing anything.
     */
    suspend fun delete(planId: Long, owner: String, token: String): Boolean {
        if (owner.isEmpty() || token.isEmpty() || planId <= 0) return false
        val body = buildJsonObject {
            put("p_id", planId)
            put("p_owner", owner)
            put("p_token", token)
        }.toString()
        return rpc("plan_delete", body)
            ?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.booleanOrNull }.getOrNull() } == true
    }

    /**
     * Rewrites the note under [planId], which is the one part of a plan its author can still change
     * after uploading. Both [owner] and [token] are needed, exactly as for [delete]: a plan's
     * number is printed next to every plan and every comment, so it proves nothing on its own.
     *
     * Returns false when the backend refused — somebody else's plan, a stale token, no note.
     */
    suspend fun setNote(planId: Long, owner: String, token: String, note: String): Boolean {
        if (owner.isEmpty() || token.isEmpty() || planId <= 0) return false
        val body = buildJsonObject {
            put("p_id", planId)
            put("p_owner", owner)
            put("p_token", token)
            put("p_note", note)
        }.toString()
        return rpc("plan_set_note", body)
            ?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.booleanOrNull }.getOrNull() } == true
    }

    /**
     * This install's token, number and display name, creating the row on first use.
     *
     * [deviceKey] is the phone's stable lookup key (see `CantaPlatform.deviceKey`). When that phone
     * already bound a token — i.e. the app was reinstalled — the backend answers with the old one,
     * so the identity, its number and its name survive the reinstall. The returned [UserProfile.token]
     * is therefore the token the caller must use from now on, which may differ from [token].
     *
     * Null when the backend cannot be reached; the caller then keeps its local token.
     */
    suspend fun identify(
        token: String,
        deviceKey: String = "",
        profile: DeviceProfile? = null,
        privilege: String = "",
    ): UserProfile? {
        if (token.isEmpty()) return null
        val body = buildJsonObject {
            put("p_token", token)
            put("p_device_key", deviceKey)
            // The phone travels with the identity, not only with the community page's sign-in: an
            // install that never opens that page still says which model it is, which is what the
            // catalog's people counts are built from.
            put("p_model", profile?.model.orEmpty())
            put("p_codename", profile?.device.orEmpty())
            // Which route is granting the privilege right now, so "how many people run on Dhizuku"
            // is a query on `canta_users` instead of a guess. Older backends ignore the argument.
            put("p_privilege", privilege)
            // 这台机器现在跑的具体系统版本。登录是每台装机都会做的事，所以放在这里：用户一上线，
            // 他自己那些还没记版本的老方案就能补上具体版本号，界面上才分得出 16.0.3 和 16.0.501。
            profile?.rom?.takeIf { it.isNotBlank() }?.let { put("p_rom", it) }
            put("p_rom_version", profile?.romVersion ?: 0)
            profile?.romBuild?.takeIf { it.isNotBlank() }?.let { put("p_rom_build", it) }
        }.toString()
        rpc("identity_bind", body)?.let { return parseProfile(it) }
        // A deployment without the three build arguments refuses the call above; bind the identity
        // the way that deployment understands, rather than failing to sign in at all.
        if (functionMissing) {
            val legacy = buildJsonObject {
                put("p_token", token)
                put("p_device_key", deviceKey)
                put("p_model", profile?.model.orEmpty())
                put("p_codename", profile?.device.orEmpty())
                put("p_privilege", privilege)
            }.toString()
            rpc("identity_bind", legacy)?.let { return parseProfile(it) }
        }
        return null
    }

    /**
     * This install's number and display name, creating the row on first use. The name is the one
     * the user picked in Settings; it is empty until they do, and the install code shows instead.
     * Null when the backend cannot be reached.
     */
    suspend fun profile(token: String): UserProfile? {
        if (token.isEmpty()) return null
        val text = rpc("profile_get", buildJsonObject { put("p_token", token) }.toString()) ?: return null
        return parseProfile(text)
    }

    /** Stores [name] for [token] and answers what the backend kept; null on failure. */
    suspend fun setName(token: String, name: String): UserProfile? {
        if (token.isEmpty()) return null
        val body = buildJsonObject {
            put("p_token", token)
            put("p_name", name)
        }.toString()
        val text = rpc("profile_set_name", body) ?: return null
        return parseProfile(text)
    }

    private fun parseProfile(text: String): UserProfile? = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject
        val id = o["id"]?.jsonPrimitive?.longOrNull ?: return null
        if (id <= 0) return null
        UserProfile(
            token = o["token"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            userId = id,
            name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }.getOrNull()

    /**
     * The number this [token] owns, creating it on first use: 10000 for the first install, then
     * one up. Returns null when the backend cannot be reached. The token is created locally and
     * never leaves the device except in these calls.
     */
    suspend fun signIn(token: String, profile: DeviceProfile? = null): Long? {
        if (token.isEmpty()) return null
        val body = buildJsonObject {
            put("p_token", token)
            put("p_model", profile?.model.orEmpty())
            put("p_codename", profile?.device.orEmpty())
        }.toString()
        return rpc("user_login", body)
            ?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.longOrNull }.getOrNull() }
            ?.takeIf { it > 0 }
    }

    /**
     * Deletes the plans this install already uploaded with exactly the same package set.
     *
     * Uploading the same set twice is a retry, not a second plan: without this the list ends up
     * with two identical rows and the ranking counts the same removal twice.
     */
    suspend fun deleteOwnDuplicates(
        profile: DeviceProfile,
        owner: String,
        token: String,
        packages: List<String>,
    ): Int {
        if (owner.isEmpty() || token.isEmpty() || packages.isEmpty()) return 0
        val wanted = packages.toSet()
        val duplicates = plansFor(profile).filter { it.owner == owner && it.packages.toSet() == wanted }
        var removed = 0
        duplicates.forEach { if (delete(it.id, owner, token)) removed++ }
        return removed
    }

    suspend fun vote(planId: Long, voter: String): Int? {
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_id", planId)
            put("p_voter", voter)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        return rpc("plan_vote", body)?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.longOrNull?.toInt() }.getOrNull() }
    }

    /**
     * What people say about removing [pkg]: this device's votes and everybody's, in one call.
     * Null when the backend cannot be reached (or the table is not there yet).
     */
    suspend fun packageVotes(pkg: String, profile: DeviceProfile?): PackageVotes? {
        val body = buildJsonObject {
            put("p_pkg", pkg)
            put("p_model", profile?.model.orEmpty())
            put("p_codename", profile?.device.orEmpty())
            put("p_rom", profile?.rom.orEmpty())
            put("p_rom_version", profile?.romVersion ?: 0)
        }.toString()
        val text = rpc("package_vote_counts", body) ?: return null
        return parseVotes(text)
    }

    /**
     * Records that [voter] says [canRemove] about [pkg] on this device, and answers the fresh
     * counts. Voting again replaces the earlier vote, so the same call changes someone's mind.
     */
    suspend fun votePackage(
        pkg: String,
        voter: String,
        canRemove: Boolean,
        profile: DeviceProfile?,
    ): PackageVotes? {
        if (voter.isEmpty()) return null
        // 凭据要在 buildJsonObject 之前取：那个 lambda 不是 suspend，里面调不了 callerToken()。
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_pkg", pkg)
            put("p_voter", voter)
            put("p_can_remove", canRemove)
            put("p_brand", profile?.brand.orEmpty())
            put("p_model", profile?.model.orEmpty())
            put("p_codename", profile?.device.orEmpty())
            put("p_rom", profile?.rom.orEmpty())
            put("p_rom_version", profile?.romVersion ?: 0)
            put("p_sdk", profile?.sdk ?: 0)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("package_vote", body) ?: return null
        return parseVotes(text)
    }

    private fun parseVotes(text: String): PackageVotes? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        val systems = o["systems"]?.jsonObject.orEmpty().mapValues { (_, value) ->
            val counts = value.jsonObject
            (counts["yes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0) to
                (counts["no"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0)
        }
        PackageVotes(
            canRemove = o["yes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            cannotRemove = o["no"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            allCanRemove = o["all_yes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            allCannotRemove = o["all_no"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            bySystem = systems,
        )
    }.getOrNull()

    // ---------------------------------------------------------------- comments

    /**
     * The thread of one plan, newest first.
     *
     * A backend that predates the shared comment table only knows `plan_comments_for`: falling back
     * to it keeps an older deployment readable instead of showing an empty thread.
     */
    suspend fun comments(planId: Long, viewer: String = ""): List<PlanComment> =
        commentsFor(COMMENT_KIND_PLAN, planId.toString(), viewer)

    /** The thread of one app's page, newest first. [viewer] is whose hearts are marked. */
    suspend fun appComments(packageName: String, viewer: String = ""): List<PlanComment> =
        commentsFor(COMMENT_KIND_APP, packageName, viewer)

    /**
     * 任意 kind 的评论线程（`kind` / `target` 在后端都是自由文本）。
     *
     * 加一种页面不必动库：推荐页的条目就是 `recommend` + 项目地址。机场那条不走这里——它另有
     * [airportComments]，先试专用函数、再退回通用那条。
     */
    suspend fun commentsOf(kind: String, target: String, viewer: String = ""): List<PlanComment> =
        commentsFor(kind, target, viewer)

    private suspend fun commentsFor(kind: String, target: String, viewer: String = ""): List<PlanComment> {
        if (target.isEmpty()) return emptyList()
        val body = buildJsonObject {
            put("p_kind", kind)
            put("p_target", target)
            put("p_voter", viewer)
        }.toString()
        rpc("comments_for", body)?.let { text -> return parseComments(text) }
        // A backend older than the hearts reads `comments_for` with two arguments only. Asking again
        // without the viewer keeps the thread readable there, unhearted rather than empty.
        if (viewer.isNotEmpty()) {
            val plain = buildJsonObject {
                put("p_kind", kind)
                put("p_target", target)
            }.toString()
            rpc("comments_for", plain)?.let { text -> return parseComments(text) }
        }
        if (kind != COMMENT_KIND_PLAN) return emptyList()
        val planId = target.toLongOrNull() ?: return emptyList()
        val legacy = rpc("plan_comments_for", buildJsonObject { put("p_id", planId) }.toString())
            ?: return emptyList()
        return parseComments(legacy)
    }

    private fun parseComments(text: String): List<PlanComment> = runCatching {
        json.parseToJsonElement(text).jsonArray.map { element ->
            val o = element.jsonObject
            PlanComment(
                author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                replyTo = o["parent_id"]?.jsonPrimitive?.longOrNull ?: 0L,
                // Absent on a backend older than the thread table: an empty id simply matches
                // nobody, which is the right answer when the server does not say who wrote it.
                authorId = o["author_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                replies = o["replies"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                // Absent on a backend older than the hearts: no hearts is the right answer there.
                likes = o["likes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                liked = o["liked"]?.jsonPrimitive?.booleanOrNull == true,
                deviceModel = o["device_model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                systemName = o["system_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }.getOrElse { emptyList() }

    /**
     * Posts one comment, or one answer to [replyTo], and answers its id — null when the backend did
     * not take it.
     *
     * [author] is the name the page shows next to the avatar; [authorId] is the identity number
     * behind it, which is what keeps the comment deleteable after the user renames themselves.
     */
    suspend fun comment(
        kind: String,
        target: String,
        body: String,
        author: String,
        authorId: String = "",
        replyTo: Long = 0L,
        deviceModel: String = "",
        systemName: String = "",
        /** Who wrote the comment being answered; only the old, flat backend needs the name. */
        replyToName: String = "",
    ): Long? {
        if (target.isEmpty() || body.isBlank()) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_kind", kind)
            put("p_target", target)
            put("p_body", body)
            put("p_author", author)
            put("p_author_id", authorId)
            put("p_model", deviceModel)
            put("p_system", systemName)
            if (replyTo > 0L) put("p_parent", replyTo)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        rpc("comment_add", payload)?.let { text ->
            return runCatching { json.parseToJsonElement(text).jsonPrimitive.longOrNull }.getOrNull()
        }
        // A deployment that predates the thread table has no `comment_add`, but it still has the
        // old plan writer. Posting there keeps the thread readable instead of showing "发送失败" on
        // a comment the old page would have taken; a reply carries its "回复 @name" in the text,
        // because the old table has no parent column to put it in.
        if (kind == COMMENT_KIND_PLAN && functionMissing) {
            val planId = target.toLongOrNull() ?: return null
            val text = if (replyTo > 0L && replyToName.isNotEmpty()) "回复 @$replyToName：$body" else body
            val legacyBody = buildJsonObject {
                put("p_id", planId)
                put("p_body", text)
                put("p_author", author)
            }.toString()
            return rpc("plan_comment", legacyBody)
                ?.let { text -> runCatching { json.parseToJsonElement(text).jsonPrimitive.longOrNull }.getOrNull() }
        }
        return null
    }

    /** A comment on plan [planId]. */
    suspend fun commentOnPlan(
        planId: Long,
        body: String,
        author: String,
        authorId: String = "",
        replyTo: Long = 0L,
        deviceModel: String = "",
        systemName: String = "",
        replyToName: String = "",
    ): Long? = comment(
        kind = COMMENT_KIND_PLAN,
        target = planId.toString(),
        body = body,
        author = author,
        authorId = authorId,
        replyTo = replyTo,
        deviceModel = deviceModel,
        systemName = systemName,
        replyToName = replyToName,
    )

    /** A comment on the page of [pkg]. */
    suspend fun commentOnApp(
        pkg: String,
        body: String,
        author: String,
        authorId: String = "",
        replyTo: Long = 0L,
        deviceModel: String = "",
        systemName: String = "",
        replyToName: String = "",
    ): Long? = comment(
        kind = COMMENT_KIND_APP,
        target = pkg,
        body = body,
        author = author,
        authorId = authorId,
        replyTo = replyTo,
        deviceModel = deviceModel,
        systemName = systemName,
        replyToName = replyToName,
    )

    /**
     * Deletes one comment this install wrote. [owner] is the identity number and [token] the
     * secret that proves it, exactly like [delete]: somebody else's comment — or a guessed number —
     * deletes nothing.
     */
    suspend fun deleteComment(id: Long, owner: String, token: String): Boolean {
        // The token is the proof; the number only narrows the match. A comment written before the
        // number arrived is signed with the token, and must still be deletable.
        if (id <= 0L || token.isEmpty()) return false
        val body = buildJsonObject {
            put("p_id", id)
            put("p_owner", owner)
            put("p_token", token)
        }.toString()
        return rpc("comment_delete", body)
            ?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.booleanOrNull }.getOrNull() } == true
    }

    /**
     * Taps the heart on comment [id]: [liked] true adds it, false takes it back.
     *
     * Answers the count and the state the server now holds — the page shows that rather than the
     * number it guessed, so two devices watching the same comment cannot drift apart. Null when the
     * heart did not reach the backend, in which case the page keeps what it already showed.
     *
     * [voter] is the identity number, the same one the comment was written under; a backend that
     * predates the hearts has no `comment_like` and simply answers nothing.
     */
    suspend fun likeComment(id: Long, voter: String, liked: Boolean): CommentLikes? {
        if (id <= 0L || voter.isEmpty()) return null
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_id", id)
            put("p_voter", voter)
            put("p_liked", liked)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        return rpc("comment_like", body)?.let(::parseCommentLikes)
    }

    private fun parseCommentLikes(text: String): CommentLikes? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        CommentLikes(
            likes = o["likes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            liked = o["liked"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }.getOrNull()

    /**
     * Which side this install picked for [pkg]: true, false, or null when it never voted.
     *
     * Asked of the backend rather than remembered locally, so the page agrees with the count after a
     * reinstall or a second device. Null also comes back when the call fails; the page keeps its
     * local memory in that case instead of clearing a vote it knows about.
     */
    suspend fun myPackageVote(pkg: String, voter: String): Boolean? {
        if (pkg.isEmpty() || voter.isEmpty()) return null
        val body = buildJsonObject {
            put("p_pkg", pkg)
            put("p_voter", voter)
        }.toString()
        val text = rpc("package_my_vote", body) ?: return null
        return runCatching { json.parseToJsonElement(text).jsonPrimitive.booleanOrNull }.getOrNull()
    }

    /**
     * Everything [owner] wrote — comments and answers alike — newest first, one page at a time.
     *
     * [owner] is the identity number, not the display name: renaming yourself must not lose the
     * list. [token] is sent as well, because a comment written before the backend handed out that
     * number was signed with the token instead; the backend matches either.
     *
     * [offset] is how far the list has already been read: the reader asks for the next page when it
     * scrolls to the end, so a person with a year of comments still draws the first screen at once.
     * A backend older than the paging simply ignores the limit and answers with everything it has,
     * which is why the app keeps its own page size rather than trusting the server's.
     */
    suspend fun myComments(
        owner: String,
        token: String = "",
        offset: Int = 0,
        limit: Int = INBOX_PAGE_SIZE,
    ): List<MyComment> = myCommentsPage(owner, token, offset, limit).items

    /** The same, with "is there another page" alongside: what a scrolling list needs. */
    suspend fun myCommentsPage(
        owner: String,
        token: String = "",
        offset: Int = 0,
        limit: Int = INBOX_PAGE_SIZE,
    ): Page<MyComment> {
        if (owner.isEmpty() && token.isEmpty()) return Page.EMPTY
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_token", token)
            put("p_limit", limit)
            put("p_offset", offset)
        }.toString()
        val text = rpc("comments_by_author", body) ?: return Page.EMPTY
        val items = runCatching {
            json.parseToJsonElement(text).jsonArray.map { element ->
                val o = element.jsonObject
                MyComment(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    kind = o["kind"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    target = o["target"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    author = "",
                    body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    replies = o["replies"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                    likes = o["likes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                )
            }
        }.getOrElse { emptyList() }
        return Page(items, hasMore = items.size >= limit)
    }

    /** The answers other people left under [owner]'s comments, newest first; [token] proves it too. */
    suspend fun repliesToMe(
        owner: String,
        token: String = "",
        offset: Int = 0,
        limit: Int = INBOX_PAGE_SIZE,
    ): List<MyComment> = repliesToMePage(owner, token, offset, limit).items

    /** The same, with "is there another page" alongside. */
    suspend fun repliesToMePage(
        owner: String,
        token: String = "",
        offset: Int = 0,
        limit: Int = INBOX_PAGE_SIZE,
    ): Page<MyComment> {
        if (owner.isEmpty() && token.isEmpty()) return Page.EMPTY
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_token", token)
            put("p_limit", limit)
            put("p_offset", offset)
        }.toString()
        val text = rpc("comments_replies_to", body) ?: return Page.EMPTY
        val items = runCatching {
            json.parseToJsonElement(text).jsonArray.map { element ->
                val o = element.jsonObject
                MyComment(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    kind = o["kind"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    target = o["target"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    parentBody = o["parent_body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    parentId = o["parent_id"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }
        }.getOrElse { emptyList() }
        return Page(items, hasMore = items.size >= limit)
    }

    // ---------------------------------------------------------------- the inbox feed

    /**
     * One page of the whole feed: replies, comments under my plans, hearts on my comments, and
     * endorsements of my plans, merged and ordered by the backend.
     *
     * [perSource] rows are taken from each of the four at [offset], so the feed grows by asking for
     * the next offset — one request per scroll, and the same rows whatever happened in between.
     * A backend that predates `inbox_page` answers nothing, and the page falls back to the two
     * list readers it does have; that is why [fallback] exists rather than the page failing.
     */
    suspend fun inboxPage(
        owner: String,
        token: String = "",
        offset: Int = 0,
        perSource: Int = INBOX_PAGE_SIZE / 2,
    ): Page<InboxItem> {
        if (owner.isEmpty() && token.isEmpty()) return Page.EMPTY
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_token", token)
            put("p_per_source", perSource)
            put("p_offset", offset)
        }.toString()
        val text = rpc("inbox_page", body) ?: return Page.EMPTY
        val items = runCatching {
            json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
                val o = element.jsonObject
                val src = o["src"]?.jsonPrimitive?.contentOrNull.orEmpty()
                // A heart says "小张 等 3 人赞了你的评论": the name the voter uses today is what it
                // has to read, and an unnamed voter falls back to the identity string beside it.
                val actorName = o["actor_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                InboxItem(
                    kind = InboxKind.of(src),
                    id = o["src_id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    targetKind = o["kind"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        .ifEmpty { COMMENT_KIND_PLAN },
                    target = o["target"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    parentBody = o["parent_body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    actor = actorName.ifEmpty { o["actor"]?.jsonPrimitive?.contentOrNull.orEmpty() },
                    actorId = o["actor"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    likes = o["likes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                )
            }
        }.getOrElse { emptyList() }
        // What was just handed over is what has been seen: remember the newest row of each kind, so
        // that the count that follows this page can move exactly those tabs' read positions.
        if (owner != servedOwner) {
            servedSinceCount = emptyMap()
            servedOwner = owner
        }
        servedSinceCount = mergeNewest(servedSinceCount, items.newestPerKind())
        return Page(items, hasMore = pageHasMore(items, perSource))
    }

    // ---------------------------------------------------------------- corrected descriptions

    /**
     * Every rewrite of [packageName]'s description, the accepted one first — null when the backend
     * cannot be reached (or does not carry the function yet, where the page shows the published
     * line and says nothing about corrections).
     *
     * [viewer] is the identity whose votes are marked, so the page can light up the side this
     * install already picked.
     */
    suspend fun descriptionFixes(packageName: String, viewer: String = ""): List<DescriptionFix>? {
        if (packageName.isEmpty()) return emptyList()
        val body = buildJsonObject {
            put("p_pkg", packageName)
            put("p_voter", viewer)
        }.toString()
        val text = rpc("description_fixes_for", body) ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonArray.map(::parseDescriptionFix)
        }.getOrNull()
    }

    /**
     * The rewrite that currently stands in for [packageName]'s published description, or null while
     * the published one still does.
     */
    suspend fun appliedDescriptionFix(packageName: String): DescriptionFix? {
        if (packageName.isEmpty()) return null
        val body = buildJsonObject { put("p_pkg", packageName) }.toString()
        val text = rpc("description_fix_current", body) ?: return null
        return runCatching {
            val element = json.parseToJsonElement(text)
            if (element is kotlinx.serialization.json.JsonNull) null else parseDescriptionFix(element)
        }.getOrNull()
    }

    /**
     * Files [body] as a corrected description of [packageName], signed with [author] and owned by
     * [authorId]. Writing again replaces the pending rewrite this identity already filed, so this
     * is also how one's own suggestion is edited.
     *
     * Answers the rewrite's id; null when the backend refused it (text too long, no identity).
     */
    suspend fun addDescriptionFix(
        packageName: String,
        body: String,
        note: String = "",
        author: String = "",
        authorId: String = "",
    ): Long? {
        if (packageName.isEmpty() || body.isBlank()) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_pkg", packageName)
            put("p_body", body)
            put("p_note", note)
            put("p_author", author)
            put("p_author_id", authorId)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("description_fix_add", payload) ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.longOrNull
        }.getOrNull()
    }

    /**
     * Votes [agree] on rewrite [id] as [voter], and answers the fresh counts.
     *
     * The backend re-settles the package after every vote, so [DescriptionFixVote.applied] can turn
     * true here: that is the moment the rewrite replaces the published description.
     */
    suspend fun voteDescriptionFix(id: Long, voter: String, agree: Boolean): DescriptionFixVote? {
        if (id <= 0L || voter.isEmpty()) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_id", id)
            put("p_voter", voter)
            put("p_agree", agree)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("description_fix_vote", payload) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            DescriptionFixVote(
                agrees = o["agrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                disagrees = o["disagrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                applied = o["applied"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }.getOrNull()
    }

    /** One row of [descriptionFixes], with every field optional: an old answer stays readable. */
    private fun parseDescriptionFix(element: kotlinx.serialization.json.JsonElement): DescriptionFix {
        val o = element.jsonObject
        return DescriptionFix(
            id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            note = o["note"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            authorId = o["author_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            agrees = o["agrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            disagrees = o["disagrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            mine = o["mine"]?.jsonPrimitive?.booleanOrNull,
            applied = o["applied"]?.jsonPrimitive?.booleanOrNull == true,
            needed = o["needed"]?.jsonPrimitive?.longOrNull?.toInt() ?: DescriptionFix.VOTES_NEEDED,
            shareNeeded = o["share"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: DescriptionFix.SHARE_NEEDED,
        )
    }

    /**
     * Every app the community proposed for the recommendations page, accepted ones first — null
     * when nothing could be read (no snapshot yet **and** the backend is unreachable).
     *
     * [viewer] is the identity whose votes are marked, so the page lights up the side this install
     * already picked.
     *
     * ## 2026-11-26：内容走快照，`mine` 仍然只有后端能回答
     *
     * 这个接口实测 81,858 个数据块 / 524 次调用。它的 SQL 里 `p_voter` **只影响 `mine`
     * 一个字段** —— 条目内容、`agrees`、`disagrees`、`applied` 对所有人一样。
     *
     * 但 `mine` **不能**省：页面靠它把「认同 / 不认同」点亮（`RecommendationsScreen` 直接读
     * `app.mine`），而它取决于这台手机投过什么，快照里没有、也不该有。
     *
     * 所以这里**仍然调一次 rpc** —— 换来的是「内容不用等后端」：快照先给出一份完整的列表，
     * rpc 回来后只把 `mine` 贴上去。快照读不到时，rpc 的整份结果照旧直接用（老路径不变）。
     *
     * **注意**：这不是「省掉一次请求」，而是让这一页在后端慢/挂的时候**仍然有内容**。
     * 真要省调用次数，得让客户端自己记住投过什么 —— 那是另一个改动，别在这里顺手做。
     */
    suspend fun suggestedApps(viewer: String = ""): List<SuggestedApp>? {
        val snapshot = offlineSuggestions()
        val body = buildJsonObject { put("p_voter", viewer) }.toString()
        val text = rpc("recommend_suggestions", body)
        val live = text?.let { answered ->
            runCatching {
                json.parseToJsonElement(answered).jsonArray.map(::parseSuggestedApp)
            }.getOrNull()
        }

        // 后端答了就以它为准：它同时带着内容与 mine，是完整答案。
        if (live != null) return live
        // 后端没答：用快照的内容，`mine` 一律留空（页面于是四个按钮都不点亮，但条目、票数都在）。
        return snapshot
    }

    /**
     * 读推荐条目的快照；读不到就是 null（调用方接着走 rpc）。
     *
     * 解析出来是 `SuggestedApp` 但 `mine` 一律留空：快照是全社区共用的一份，
     * 里面**不该**、也**不能**带上"这台手机投过没"。谁投过由客户端自己的本地状态叠加。
     */
    private suspend fun offlineSuggestions(): List<SuggestedApp>? {
        // 与 [snapshotCache] 同一条 TTL 规矩：推荐条目也是「别人随时会新提一条」的东西，
        // 永不过期的缓存会让刚提的条目在自己和别人屏幕上都不出现。
        suggestionCache?.let {
            if (currentTimeMillis() - suggestionCachedAt < SNAPSHOT_CACHE_TTL_MS) return it
        }
        for (url in listOf(suggestionMirrorUrl, suggestionUrl)) {
            if (url.isBlank()) continue
            val text = runCatching {
                withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
                    val response = client.get(url)
                    if (response.status.isSuccess()) response.bodyAsText() else null
                }
            }.getOrNull() ?: continue
            val rows = runCatching {
                json.parseToJsonElement(text).jsonArray.map(::parseSuggestedApp)
            }.getOrDefault(emptyList())
            if (rows.isEmpty()) continue
            suggestionCache = rows
            suggestionCachedAt = currentTimeMillis()
            return rows
        }
        // 网络不通 → 随包那份（装机那天的社区）。
        val bundled = runCatching {
            json.parseToJsonElement(
                Res.readBytes(BUNDLED_SUGGESTIONS_PATH).decodeToString(),
            ).jsonArray.map(::parseSuggestedApp)
        }.getOrDefault(emptyList())
        if (bundled.isNotEmpty()) {
            suggestionCache = bundled
            return bundled
        }
        return null
    }

    /**
     * Files an app of this install's own as a recommendation, signed with [author] and owned by
     * [authorId]. The backend keeps one pending entry per identity and project, so filing again is
     * editing what was filed.
     *
     * [url] must be a GitHub project page — the server refuses anything else and answers with its
     * reason, which the form shows. Answers the entry's id; null when the backend refused it.
     */
    suspend fun addSuggestedApp(
        name: String,
        url: String,
        summary: String = "",
        category: String = "tools",
        license: String = "",
        author: String = "",
        authorId: String = "",
    ): Long? {
        if (name.isBlank() || url.isBlank()) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_name", name)
            put("p_url", url)
            put("p_summary", summary)
            put("p_category", category)
            put("p_license", license)
            put("p_author", author)
            put("p_author_id", authorId)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("recommend_suggestion_add", payload) ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.longOrNull
        }.getOrNull()
    }

    /**
     * Votes [agree] on proposed app [id] as [voter], and answers the fresh counts.
     *
     * The backend re-settles the project after every vote, so [SuggestedAppVote.applied] can turn
     * true here: that is the moment the app joins the page.
     */
    suspend fun voteSuggestedApp(id: Long, voter: String, agree: Boolean): SuggestedAppVote? {
        if (id <= 0L || voter.isEmpty()) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_id", id)
            put("p_voter", voter)
            put("p_agree", agree)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("recommend_suggestion_vote", payload) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            SuggestedAppVote(
                agrees = o["agrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                disagrees = o["disagrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                applied = o["applied"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }.getOrNull()
    }

    /** One row of [suggestedApps], with every field optional: an old answer stays readable. */
    private fun parseSuggestedApp(element: kotlinx.serialization.json.JsonElement): SuggestedApp {
        val o = element.jsonObject
        return SuggestedApp(
            id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            url = o["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            category = o["category"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            summary = o["summary"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            license = o["license"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            authorId = o["author_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            agrees = o["agrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            disagrees = o["disagrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            mine = o["mine"]?.jsonPrimitive?.booleanOrNull,
            applied = o["applied"]?.jsonPrimitive?.booleanOrNull == true,
            needed = o["needed"]?.jsonPrimitive?.longOrNull?.toInt() ?: SuggestedApp.VOTES_NEEDED,
            shareNeeded = o["share"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: SuggestedApp.SHARE_NEEDED,
        )
    }

    // ---------------------------------------------------------------- 机场（订阅分享 + 投票 + 打分）

    /**
     * 机场列表，认同最多的在前。每一条带自己的票、星和「我投过什么」，但**不带订阅链接**——
     * 链接要单独调 [airportSubscription]，而且只有投过票或打过分才拿得到。
     *
     * null 表示这一次没拿到（网络没通，或后端还没这个函数）：页面据此说「还没拉到」，而不是
     * 说「一条都没有」。
     */
    suspend fun airports(viewer: String = ""): List<CommunityAirport>? {
        // token 一起发：列表里的「我投过什么」要和投票时写的身份对得上，否则自己投过的
        // 显示成没投过，明明评过还取不出订阅。
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_voter", viewer)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("airport_entries", body) ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonArray.map(::parseAirport)
        }.getOrNull()
    }

    /**
     * 分享一个机场。同一个身份、同一个名字再提交一次是改，不是又挂一条（后端保证）。
     *
     * [subscribeUrl] 可以是 http(s) 订阅，也可以是单个节点链接；后端两样都收，别的会拒绝并
     * 把理由放进 [lastError]，表单照着显示。
     */
    suspend fun addAirport(
        name: String,
        url: String = "",
        subscribeUrl: String = "",
        kind: String = "airport",
        summary: String = "",
        author: String = "",
        authorId: String = "",
    ): Long? {
        if (name.isBlank()) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_name", name)
            put("p_url", url)
            put("p_subscribe_url", subscribeUrl)
            put("p_kind", kind)
            put("p_summary", summary)
            put("p_author", author)
            put("p_author_id", authorId)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("airport_add", payload) ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.longOrNull
        }.getOrNull()
    }

    /** 认同 / 不认同一条机场，回答投完之后的三个数。 */
    suspend fun voteAirport(id: Long, voter: String, agree: Boolean): AirportVoteResult? {
        if (id <= 0L) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_id", id)
            put("p_voter", voter)
            put("p_agree", agree)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("airport_vote", payload) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            AirportVoteResult(
                agrees = o["agrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                disagrees = o["disagrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                mine = o["mine"]?.jsonPrimitive?.booleanOrNull,
                // 老后端没有这个键：false 就是"没结算"，与它现在的行为一致。
                approved = o["approved"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }.getOrNull()
    }

    /** 给一条机场打 1–5 星，回答新的平均分。重复打分是改分。 */
    suspend fun rateAirport(id: Long, voter: String, stars: Int): AirportRatingResult? {
        if (id <= 0L) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_id", id)
            put("p_voter", voter)
            put("p_stars", stars)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("airport_rate", payload) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            AirportRatingResult(
                stars = o["stars"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f,
                ratingCount = o["rating_count"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                myStars = o["my_stars"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            )
        }.getOrNull()
    }

    /**
     * 一条机场的订阅链接。没投过票也没打过分的身份拿不到：答案里 `unlocked` 是 false，链接是空串。
     *
     * 后端还没这个函数时同样返回 null，页面就说「暂时取不到」。
     */
    suspend fun airportSubscription(id: Long, voter: String): AirportSubscription? {
        if (id <= 0L) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_id", id)
            put("p_voter", voter)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("airport_subscription", payload) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            AirportSubscription(
                unlocked = o["unlocked"]?.jsonPrimitive?.booleanOrNull == true,
                subscribeUrl = o["subscribe_url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.getOrNull()
    }

    /**
     * 一条机场下面的评论，最新在前。[viewer] 决定哪些心是点亮的。
     *
     * 先走机场专用的 `airport_comments_for`（它只收一个 id，客户端不必拼 `kind`）；后端还没有
     * 这个函数时退回通用的 `comments_for('airport', id)`，再没有就拿空表——一个老后端的机场
     * 页面因此是「还没有人评论」，而不是报错。
     */
    suspend fun airportComments(airportId: Long, viewer: String = ""): List<PlanComment> {
        if (airportId <= 0L) return emptyList()
        rpc("airport_comments_for", buildJsonObject { put("p_id", airportId) }.toString())
            ?.let { text -> return parseComments(text) }
        if (!functionMissing) return emptyList()
        return commentsFor(COMMENT_KIND_AIRPORT, airportId.toString(), viewer)
    }

    /**
     * 在一条机场下面发一条评论 / 一条回复，回答它的 id；null 表示后端没接。
     *
     * 身份与其它写函数一致：认出来的身份优先，认不出来才用自报号码；名字空着时后端按 `#编号`
     * 补一个，读的人至少看得出这是两个人。
     */
    suspend fun commentOnAirport(
        airportId: Long,
        body: String,
        author: String,
        authorId: String = "",
        replyTo: Long = 0L,
        deviceModel: String = "",
        systemName: String = "",
    ): Long? {
        if (airportId <= 0L || body.isBlank()) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_target", airportId.toString())
            put("p_body", body)
            put("p_author", author)
            put("p_author_id", authorId)
            put("p_model", deviceModel)
            put("p_system", systemName)
            if (replyTo > 0L) put("p_parent", replyTo)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        rpc("airport_comment_add", payload)?.let { text ->
            return runCatching { json.parseToJsonElement(text).jsonPrimitive.longOrNull }.getOrNull()
        }
        if (!functionMissing) return null
        // 后端还没有机场专用的写函数：走通用的那条，kind 由这里写死，机场页面因此照样能发。
        return comment(
            kind = COMMENT_KIND_AIRPORT,
            target = airportId.toString(),
            body = body,
            author = author,
            authorId = authorId,
            replyTo = replyTo,
            deviceModel = deviceModel,
            systemName = systemName,
        )
    }

    // ---------------------------------------------------------------- 积分（签到 + 商城）

    /** 这个身份的钱包：余额、今天签到了没有、连签几天。 */
    suspend fun pointsAccount(owner: String = ""): PointsAccount? {
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_owner", owner)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("points_account", payload) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            PointsAccount(
                owner = o["owner"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                balance = o["balance"]?.jsonPrimitive?.longOrNull ?: 0L,
                earned = o["earned"]?.jsonPrimitive?.longOrNull ?: 0L,
                spent = o["spent"]?.jsonPrimitive?.longOrNull ?: 0L,
                day = o["day"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                reward = o["reward"]?.jsonPrimitive?.longOrNull?.toInt()
                    ?: PointsAccount.DEFAULT_CHECK_IN_REWARD,
                checkedInToday = o["checked_in_today"]?.jsonPrimitive?.booleanOrNull == true,
                streak = o["streak"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                todayPoints = o["today_points"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            )
        }.getOrNull()
    }

    /**
     * 今天的签到。一天只加一次分：同一天再调只回答 `already` = true，页面刷新不会刷分。
     * 认不出身份时后端返回 null，这里也返回 null。
     */
    suspend fun checkIn(owner: String = ""): CheckInResult? {
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_owner", owner)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("points_check_in", payload) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            CheckInResult(
                awarded = o["awarded"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                balance = o["balance"]?.jsonPrimitive?.longOrNull ?: 0L,
                already = o["already"]?.jsonPrimitive?.booleanOrNull == true,
                reward = o["reward"]?.jsonPrimitive?.longOrNull?.toInt()
                    ?: PointsAccount.DEFAULT_CHECK_IN_REWARD,
                day = o["day"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.getOrNull()
    }

    /** 货架 + 我的余额。交付内容不在这份答案里，只有兑换成功那一次才给。 */
    suspend fun pointShelf(owner: String = ""): PointShelf? {
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_owner", owner)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("point_offers", payload) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            PointShelf(
                balance = o["balance"]?.jsonPrimitive?.longOrNull ?: 0L,
                offers = o["offers"]?.jsonArray?.map(::parsePointOffer).orEmpty(),
            )
        }.getOrNull()
    }

    /**
     * 挂一件商品。发布不花分，花分的是兑换它的人——这样「发东西赚分」不用先有本钱。
     *
     * [deliverable] 是兑换成功后交给买家的东西（邀请码、订阅链接、一段配置），列表和别人的
     * 查询都看不到它。
     */
    suspend fun publishPointOffer(
        title: String,
        detail: String = "",
        price: Int = 10,
        stock: Int = 1,
        kind: String = "airport",
        deliverable: String = "",
        owner: String = "",
        ownerId: String = "",
    ): Long? {
        if (title.isBlank()) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_title", title)
            put("p_detail", detail)
            put("p_price", price)
            put("p_stock", stock)
            put("p_kind", kind)
            put("p_deliverable", deliverable)
            put("p_owner", owner)
            put("p_owner_id", ownerId)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("point_offer_publish", payload) ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.longOrNull
        }.getOrNull()
    }

    /**
     * 用积分换一件商品：买家的分当场扣、卖家的分当场到账、库存减一。
     *
     * 拒绝（分不够、卖完了、自己挂的、已下架）不是网络失败，后端把理由写在 `error` 里，这里
     * 原样带回来给页面显示。
     */
    suspend fun redeemPointOffer(id: Long, owner: String = ""): PointRedeemResult? {
        if (id <= 0L) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_offer_id", id)
            put("p_owner", owner)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("point_offer_redeem", payload) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            PointRedeemResult(
                ok = o["ok"]?.jsonPrimitive?.booleanOrNull == true,
                balance = o["balance"]?.jsonPrimitive?.longOrNull ?: 0L,
                deliverable = o["deliverable"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                price = o["price"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                orderId = o["order_id"]?.jsonPrimitive?.longOrNull ?: 0L,
                error = o["error"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.getOrNull()
    }

    /** 我兑换过的东西，交付内容只发给买的人。失败时是空表，不是 null：这一块没有东西也是答案。 */
    suspend fun myPointOrders(owner: String = ""): List<PointOrder> {
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_owner", owner)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("point_orders", payload) ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(text).jsonArray.map { element ->
                val o = element.jsonObject
                PointOrder(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    offerId = o["offer_id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    price = o["price"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                    sellerId = o["seller_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    deliverable = o["deliverable"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 我的积分流水，最近的在前。 */
    suspend fun pointsLedger(owner: String = ""): List<PointLedgerEntry> {
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_owner", owner)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("points_ledger", payload) ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(text).jsonArray.map { element ->
                val o = element.jsonObject
                PointLedgerEntry(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    delta = o["delta"]?.jsonPrimitive?.longOrNull ?: 0L,
                    reason = o["reason"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    ref = o["ref"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 一行机场；字段缺了就退回默认值，老回答仍然画得出来。 */
    private fun parseAirport(element: kotlinx.serialization.json.JsonElement): CommunityAirport {
        val o = element.jsonObject
        return CommunityAirport(
            id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            kind = o["kind"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "airport" },
            url = o["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            summary = o["summary"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            authorId = o["author_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            agrees = o["agrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            disagrees = o["disagrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            mine = o["mine"]?.jsonPrimitive?.booleanOrNull,
            stars = o["stars"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f,
            ratingCount = o["rating_count"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            myStars = o["my_stars"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            hasSubscription = o["has_subscription"]?.jsonPrimitive?.booleanOrNull == true,
            // 老后端没有这一格：false = 还在候选池里，正好是它当时的样子（两格画的是同一份）。
            approved = o["approved"]?.jsonPrimitive?.booleanOrNull == true,
            detail = parseAirportDetail(o["detail"]),
        )
    }

    /** 列表回答里那一格 `detail`；老回答没有它，就是空详情（卡片照样画得出来）。 */
    private fun parseAirportDetail(element: kotlinx.serialization.json.JsonElement?): AirportDetail {
        if (element == null) return AirportDetail()
        val o = runCatching { element.jsonObject }.getOrNull() ?: return AirportDetail()
        fun text(key: String): String = o[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        return AirportDetail(
            price = text("price"),
            traffic = text("traffic"),
            speed = text("speed"),
            devices = text("devices"),
            protocols = text("protocols"),
            note = text("note"),
        )
    }

    /** 货架上的一件商品。 */
    private fun parsePointOffer(element: kotlinx.serialization.json.JsonElement): PointOffer {
        val o = element.jsonObject
        return PointOffer(
            id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            detail = o["detail"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            price = o["price"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            stock = o["stock"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            kind = o["kind"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "other" },
            sellerId = o["seller_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            seller = o["seller"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            active = o["active"]?.jsonPrimitive?.booleanOrNull != false,
            sold = o["sold"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            mine = o["mine"]?.jsonPrimitive?.booleanOrNull == true,
            soldOut = o["sold_out"]?.jsonPrimitive?.booleanOrNull == true,
            createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    /**
     * What is waiting for [owner] since [seenAt]: four counts, the newest comment id and the four
     * read positions this answer moved to, as one cheap call.
     *
     * This is what the red dot is drawn from, and it is counted **per tab**: [seenAt] is the four
     * positions in encoded form ([InboxCursors.encode] — a plain instant still reads as all four, so
     * an old stored value keeps working), and each count comes back as "newer than that tab's own
     * position". Empty = everything ever, for a tab that has never been read.
     *
     * [InboxSummary.checkedAt] carries the positions back in the same encoded form: the tabs whose
     * rows were served by [inboxPage] have moved to those rows, the tabs that answered 0 have moved
     * to the instant the backend counted at, and a tab that still has news the reader was not shown
     * stays exactly where it was. That is how reading one tab stops clearing the others.
     *
     * An older backend has no per-kind function at all, so this falls back to `inbox_summary` there:
     * four counts off one cursor, and all four positions move together — exactly today's behaviour.
     *
     * ## 「读到哪儿了」只存在本机，不回写后端（2026-11-26 改）
     *
     * 以前这里在拿到摘要后**紧接着调一次 `inbox_seen_mark`**，把游标写回服务器，理由是
     * 「换台手机重装后能接着读」。那个理由是错的：
     *
     *   1. 服务器该存的是**消息本身**，不是每台设备读到了第几条。存游标把 3,071 个用户 ×
     *      4 个栏位铺成 12,284 行 / 1.4 MB，每次还要 `on conflict do update` 重写一遍。
     *   2. 因为它紧跟在每次计数后面，「查未读」和「写游标」变成 1:1：实测 24 小时
     *      44,941 次 `inbox_summary_kinds` → 40,932 次 `inbox_seen_mark`。
     *   3. 压的是那台 426 MB 的免费实例 —— 多这一份写，就是 checkpoint 被拖慢的一份料。
     *
     * 本地本来就有完整的一份（[io.github.samolego.canta.data.notes.AppNoteStore] 的
     * `_inboxSeenCursors`，随设置落盘），红点、未读数、四栏各自的游标全都来自那里。
     * 换手机重装从零开始计红点，这是**正确的**行为：新设备就是没读过。
     *
     * **别再把 `inbox_seen_mark` 加回来。** 真要跨设备同步，那是另一个决定，
     * 得先算它对这台实例的写入成本，而不是顺手跟在每次计数后面。
     */
    suspend fun inboxSummary(owner: String, token: String = "", seenAt: String = ""): InboxSummary? {
        if (owner.isEmpty() && token.isEmpty()) return null
        val held = InboxCursors.decode(seenAt)
        val served = servedSinceCount
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_token", token)
            put("p_seen_reply", held[InboxKind.REPLY])
            put("p_seen_plan_comment", held[InboxKind.PLAN_COMMENT])
            put("p_seen_comment_heart", held[InboxKind.COMMENT_HEART])
            put("p_seen_plan_heart", held[InboxKind.PLAN_HEART])
        }.toString()
        rpc("inbox_summary_kinds", body)?.let { text ->
            // 只要摘要。游标（读到哪儿了）不再回写后端 —— 那是本机的事，见上面 inboxSummary 的注释。
            // 之前这里紧跟着一次 `inbox_seen_mark`，让「查未读」和「写游标」变成 1:1 配对，
            // 24 小时 40,932 次，全砸在同一台 426 MB 的实例上。
            val (summary, _) = parseKindSummary(text, held, served) ?: return null
            return summary
        }
        if (!functionMissing) return null
        // A deployment that predates the per-tab cursors has one function, one cursor. Asking it with
        // the newest of the four is the closest thing to what that deployment used to do: it counted
        // from the last instant this install had read to, and all four tabs went quiet at once.
        val legacy = buildJsonObject {
            put("p_owner", owner)
            put("p_token", token)
            put("p_seen_at", held.latest)
        }.toString()
        val legacyText = rpc("inbox_summary", legacy) ?: return null
        return parseSummary(legacyText)
    }

    /** One answer of `inbox_summary_kinds`, with the positions the four tabs now stand at. */
    private fun parseKindSummary(
        text: String,
        held: InboxCursors,
        served: Map<InboxKind, String>,
    ): Pair<InboxSummary, InboxCursors>? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        val counts = mapOf(
            InboxKind.REPLY to (o["replies"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0),
            InboxKind.PLAN_COMMENT to (o["plan_comments"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0),
            InboxKind.COMMENT_HEART to (o["comment_hearts"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0),
            InboxKind.PLAN_HEART to (o["plan_hearts"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0),
        )
        // The backend answers the position it actually counted from, which may be the one it has
        // stored for this identity rather than the one just sent — a reinstall gets the old phone's
        // positions that way, without asking for them separately.
        var base = held
        (o["seen"] as? JsonObject)?.forEach { (name, value) ->
            val kind = InboxKind.entries.firstOrNull { it.wire == name } ?: return@forEach
            base = base.with(kind, value.jsonPrimitive.contentOrNull.orEmpty())
        }
        val next = base.afterCounting(counts, served, o["checked_at"]?.jsonPrimitive?.contentOrNull.orEmpty())
        InboxSummary(
            replies = counts.getValue(InboxKind.REPLY),
            planComments = counts.getValue(InboxKind.PLAN_COMMENT),
            commentHearts = counts.getValue(InboxKind.COMMENT_HEART),
            planHearts = counts.getValue(InboxKind.PLAN_HEART),
            newestCommentId = o["newest_comment_id"]?.jsonPrimitive?.longOrNull ?: 0L,
            checkedAt = next.encode(),
        ) to next
    }.getOrNull()

    /** One answer of the old `inbox_summary`: one cursor for the lot, as that deployment means it. */
    private fun parseSummary(text: String): InboxSummary? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        InboxSummary(
            replies = o["replies"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            planComments = o["plan_comments"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            commentHearts = o["comment_hearts"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            planHearts = o["plan_hearts"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            newestCommentId = o["newest_comment_id"]?.jsonPrimitive?.longOrNull ?: 0L,
            // All four at one instant: an old backend has nothing finer to say, and its old app
            // cleared every tab at once anyway.
            checkedAt = InboxCursors.of(o["checked_at"]?.jsonPrimitive?.contentOrNull.orEmpty()).encode(),
        )
    }.getOrNull()
}

@Suppress("unused")
private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

/**
 * 两个 ROM 名算不算「一家人」——用于 [PlanRepository.plansFor] 的本地筛选。
 *
 * **为什么不要求字面相等**：OPPO / realme / 一加 那几个系统属性
 * （`ro.build.version.realmeui`、`ro.build.version.oplusrom`、`ro.build.version.opporom`）
 * 在同一台机器上**有时读得到、有时读不到**，读不到就落到下一个键。于是同一台真我 Neo8
 * 昨天上报 `realmeui 7`、今天上报 `coloros 17`（2026-11-27 实测，同一个人同一条方案列表）。
 * 拿 ROM 名做硬过滤，用户就会「今天提交的方案今天看不见」。
 *
 * 分组依据是**底座相同**（同一套系统属性、同一批系统包），不是营销名：
 *
 * - `realmeui` / `coloros` / `oxygenos`：都是 ColorOS 底座，OPPO / realme / 一加 三家共用
 * - `hyperos` / `miui`：小米，后者是前者的前身
 * - `oneui` / `magicos` / `emui` / `harmonyos`：华为系与三星各自独立
 * - 其余（`stock`、`funtouch`、`originos`、`flyme`、`myos`…）：只认同名
 *
 * `stock`（原生 Android）**不跟任何一家合并**：一台刷了原生系统的 realme，它的「能卸哪些」
 * 与 ColorOS 上的答案不是一回事。
 */
private fun sameRomFamily(a: String, b: String): Boolean {
    if (a.equals(b, ignoreCase = true)) return true
    fun group(rom: String): String? = when (rom.lowercase()) {
        "realmeui", "coloros", "oxygenos" -> "bbk"
        "hyperos", "miui" -> "xiaomi"
        else -> null
    }
    val ga = group(a) ?: return false
    return ga == group(b)
}

@Suppress("unused")
private fun JsonObject.num(key: String): Double = this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
