package io.github.samolego.canta.data.rank

import io.github.samolego.canta.data.AppBlobStorage
import io.github.samolego.canta.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 一天的毫秒数：快照按"哪一天"归档，同一天刷新多少次都只留最后一条。 */
private const val DAY_MS = 86_400_000L

/** 一次请求最多等多久。GitHub 在墙内经常是"挂着不回"，等太久不如直接报没连上。 */
private const val REQUEST_TIMEOUT_MS = 15_000L

/** 快照留多少天：够算"本周"和"上周"两段对比，再久也没人看。 */
private const val KEEP_DAYS = 70L

/** 缓存多久以内的抓取结果直接复用：GitHub 未认证 Search 只有 10 次/分钟。 */
private const val CACHE_TTL_MS = 10 * 60_000L

/** 盘上最多跟多少个仓库的快照：掉出候选池的不能无限堆。 */
private const val MAX_TRACKED_REPOS = 400

/** 榜单显示几行。 */
private const val RANK_SIZE = 10

/** 每个 topic 拉多少个候选；三个 topic 合起来去重后当候选池。 */
private const val CANDIDATES_PER_TOPIC = 30

/**
 * 榜单取哪几个 topic：AI 这一摊在 GitHub 上就是按这几个标签分的。
 *
 * 不是写死仓库名：写死名单等于"榜单永远这几个人"，而这里要的是"最近一周谁涨得猛"，
 * 候选池就得跟着 GitHub 上打这几个标签的仓库走。
 */
private val TOPICS = listOf("llm", "ai", "ai-agent")

/** 一行榜单。 */
@Serializable
data class RankRow(
    val repo: String,
    val stars: Int = 0,
    val description: String = "",
    val url: String = "",
    /** 最近 7 天净增；快照还不够 7 天时是 null（不是 0，0 会被读成"没涨"）。 */
    val week: Int? = null,
    /** 再往前 7 天的净增，用来判断加速还是放缓；不够 14 天时是 null。 */
    val previous: Int? = null,
)

/** 一次刷新得到的整块数据。 */
@Serializable
data class RankBoard(
    /** 这次抓取的时刻（epoch 毫秒）；0 表示这是盘上缓存而不是刚拉的。 */
    val fetchedAt: Long = 0L,
    val rows: List<RankRow> = emptyList(),
    /** 本机已经攒了多少天快照。 */
    val snapshotDays: Int = 0,
    /** 有没有算得出周净增（快照满 7 天才算得出）。 */
    val hasWeekly: Boolean = false,
)

/**
 * 「GitHub AI 开源周榜」的数据层：真的去 GitHub 拉，不写死。
 *
 * 三件事：
 *
 *  1. **候选池**：GitHub Search API 按 topic（llm / ai / ai-agent）拉 star 最多的几十个仓库。
 *     榜单的成员跟着 GitHub 走，不是客户端里的一张死名单。
 *  2. **本周净增**：GitHub 不给"某仓库一周涨了多少"，只有当前总数。所以本机按天存快照
 *     （[Point]），`今天 - 7 天前` 就是这一周的净增。第一次装上是没有的——那时榜单按 star 总数排，
 *     页面会直说"再攒几天才有周净增"，而不是编一个数字出来。
 *  3. **落盘**：候选、快照、上一屏结果都写进 [AppBlobStorage]，冷启动先画上次那一屏。
 *
 * 为什么不在服务端算：算一次要每天抓一次 GitHub 并入库，那是另一套定时任务；这台机器上
 * 先用本机快照能给出同样的数，且不用碰线上库。等以后要"所有人看到同一份"再搬到后端。
 */
class WeeklyRankRepository(
    private val client: HttpClient,
    private val storage: AppBlobStorage,
) {
    /** 一天的采样点。 */
    @Serializable
    private data class Point(val day: Long, val stars: Int)

    /** 盘上的形状：某天各仓库的 star 数、上次抓取时刻、上一屏结果。 */
    @Serializable
    private data class Blob(
        val fetchedAt: Long = 0L,
        val samples: Map<String, List<Point>> = emptyMap(),
        val rows: List<RankRow> = emptyList(),
    )

    /** GitHub Search 的返回：只取要用的那几个字段，其余 `ignoreUnknownKeys` 兜掉。 */
    @Serializable
    private data class GhRepo(
        val full_name: String = "",
        val stargazers_count: Int = 0,
        val description: String? = null,
        val html_url: String = "",
    )

    @Serializable
    private data class GhSearch(val items: List<GhRepo> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    /** 盘上那一份，解码一次就留在内存里。 */
    private var loaded: Blob? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 上一次刷新为什么没成：GitHub 自己的一句话、或者"没连上"。 */
    var lastError: String? = null
        private set

    /** 上一次失败是不是"根本没答"（没路由、被挡、超时）。 */
    var lastFailureWasNetwork: Boolean = false
        private set

    /**
     * 盘上那一份换过几次。
     *
     * 帖子页那张卡片要在从详情页返回后显示新的「抓取于…」与前三名，但它读的是仓储里的盘上数据、
     * 不是 Compose 状态；没有这个计数器，卡片会一直是进这一页那一刻的值。
     */
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    // ------------------------------------------------------------ 读盘 / 写盘

    private fun blob(): Blob {
        loaded?.let { return it }
        val decoded = runCatching {
            storage.read()?.decodeToString()?.let { json.decodeFromString(Blob.serializer(), it) }
        }.getOrNull() ?: Blob()
        loaded = decoded
        return decoded
    }

    private fun write(next: Blob) {
        loaded = next
        _revision.value = _revision.value + 1
        scope.launch {
            runCatching {
                storage.write(json.encodeToString(Blob.serializer(), next).encodeToByteArray())
            }
        }
    }

    /** 盘上那一屏（冷启动先画它）；一行都没有时返回 null。 */
    fun cachedBoard(): RankBoard? {
        val current = blob()
        if (current.rows.isEmpty()) return null
        return RankBoard(
            fetchedAt = current.fetchedAt,
            rows = current.rows,
            snapshotDays = snapshotDays(current),
            hasWeekly = current.rows.any { it.week != null },
        )
    }

    /** 本机攒了几天快照：最早那个采样点到今天。 */
    private fun snapshotDays(current: Blob): Int {
        val first = current.samples.values.minOfOrNull { points -> points.minOfOrNull { it.day } ?: Long.MAX_VALUE }
            ?: return 0
        if (first == Long.MAX_VALUE) return 0
        return ((today() - first) + 1).coerceAtLeast(1).toInt()
    }

    private fun today(): Long = currentTimeMillis() / DAY_MS

    // ------------------------------------------------------------ 刷新

    /**
     * 去 GitHub 拉一次，顺手记一个今天的采样点，再算出周净增。
     *
     * 失败返回 null（并把理由写进 [lastError]）：调用方先把盘上那一屏继续画着，不要因为一次
     * 断网把满屏数据清空。
     *
     * @param force true 表示用户自己点了「刷新」：那就忽略 [CACHE_TTL_MS] 直接打网络。
     *   GitHub 未认证的 Search 只有 10 次/分钟，来回切页面时靠这个缓存挡住重复请求。
     */
    suspend fun refresh(force: Boolean = false): RankBoard? {
        val cached = cachedBoard()
        if (!force && cached != null && currentTimeMillis() - cached.fetchedAt in 0 until CACHE_TTL_MS) {
            lastError = null
            lastFailureWasNetwork = false
            return cached
        }

        val fetched = fetchCandidates()
        if (fetched.isEmpty()) {
            if (lastError == null) {
                lastError = "GitHub 没有返回任何一个仓库（搜索接口限流或改动？）"
                lastFailureWasNetwork = false
            }
            return null
        }

        lastError = null
        lastFailureWasNetwork = false

        val current = blob()
        val day = today()
        val now = currentTimeMillis()

        // 今天的采样点：同一天刷新多次只留最后一次的数（star 只涨不跌，但也会掉，取最新）。
        val samples = current.samples.toMutableMap()
        fetched.forEach { (repo, stars) ->
            val points = samples[repo].orEmpty().toMutableList()
            points.removeAll { it.day == day }
            points.add(Point(day, stars))
            // 丢太旧的：留 KEEP_DAYS 天，够算本周与上周两段。
            samples[repo] = points.filter { it.day >= day - KEEP_DAYS }.sortedBy { it.day }
        }
        // 掉出候选池的仓库不该一直堆在盘上：只留最近两周还有采样的，并且总数封顶。
        val trimmed = samples.entries
            .filter { (repo, points) -> points.isNotEmpty() && (repo in fetched.keys || points.last().day >= day - 14) }
            .sortedByDescending { (_, points) -> points.last().day }
            .take(MAX_TRACKED_REPOS)
            .associate { it.key to it.value }

        val rows = fetched.map { (repo, info) ->
            val points = trimmed[repo].orEmpty()
            val latest = points.maxByOrNull { it.day }
            val anchor7 = nearest(points, day - 7)
            val anchor14 = nearest(points, day - 14)
            RankRow(
                repo = repo,
                stars = info.stars,
                description = info.description,
                url = info.url,
                // 本周 = 今天 − 7 天前；上期 = 7 天前 − 14 天前。两段都是 7 天，才比得出快慢。
                week = if (latest != null && anchor7 != null && anchor7.day < latest.day) {
                    latest.stars - anchor7.stars
                } else {
                    null
                },
                previous = if (anchor7 != null && anchor14 != null && anchor14.day < anchor7.day) {
                    anchor7.stars - anchor14.stars
                } else {
                    null
                },
            )
        }
        val ranked = rank(rows)
        val board = RankBoard(
            fetchedAt = now,
            rows = ranked,
            snapshotDays = snapshotDays(current.copy(samples = trimmed)),
            hasWeekly = ranked.any { it.week != null },
        )
        write(current.copy(fetchedAt = now, samples = trimmed, rows = ranked))
        return board
    }

    /**
     * 离 [target] 最近的那个采样点；差超过一天就当没有。
     *
     * 必须是"最近"而不是"窗口里最早的那个"：窗口里最早的点会让 7 天增量变成 9 天增量，
     * 和"上期"那一段就不是一个口径了。
     */
    private fun nearest(points: List<Point>, target: Long): Point? {
        val closest = points.minByOrNull { abs(it.day - target) } ?: return null
        return if (abs(closest.day - target) <= 1) closest else null
    }

    /** 有周净增的按净增排，没有的按总数排——两组不会混在一起比大小。 */
    private fun rank(rows: List<RankRow>): List<RankRow> =
        rows.sortedWith(
            compareByDescending<RankRow> { it.week != null }
                .thenByDescending { it.week ?: 0 }
                .thenByDescending { it.stars },
        ).take(RANK_SIZE)

    // ------------------------------------------------------------ GitHub

    private data class Candidate(val stars: Int, val description: String, val url: String)

    /**
     * 按 topic 拉候选，合成一张 `仓库全名 → 数据` 的表。
     *
     * 只打 Search 一个接口：它返回里就带 `stargazers_count` 与 `description`，不用再对每个仓库
     * 各打一次 `/repos/{owner}/{repo}`——那会把未认证的 60 次/小时额度一下用掉十分之一。
     */
    private suspend fun fetchCandidates(): Map<String, Candidate> = coroutineScope {
        val results = TOPICS.map { topic ->
            async { search("https://api.github.com/search/repositories?q=topic:$topic&sort=stars&order=desc&per_page=$CANDIDATES_PER_TOPIC") }
        }.awaitAll()

        val merged = LinkedHashMap<String, Candidate>()
        var sawAny = false
        results.forEach { list ->
            if (list == null) return@forEach
            sawAny = true
            list.forEach { repo ->
                if (repo.full_name.isBlank()) return@forEach
                val existing = merged[repo.full_name]
                // 同一个仓库可能同时挂两个 topic：取 star 更多的那一份（其实一样，防御而已）。
                if (existing == null || repo.stargazers_count > existing.stars) {
                    merged[repo.full_name] = Candidate(
                        stars = repo.stargazers_count,
                        description = repo.description.orEmpty(),
                        url = repo.html_url,
                    )
                }
            }
        }
        if (!sawAny) {
            merged.clear()
        }
        merged
    }

    /** 打一次 Search；失败返回 null 并写下 [lastError]。 */
    private suspend fun search(url: String): List<GhRepo>? {
        var refusal: String? = null
        val body = runCatching {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                val response = client.get(url) {
                    header(HttpHeaders.UserAgent, "UniCanta")
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                }
                if (response.status.isSuccess()) {
                    response.bodyAsText()
                } else {
                    refusal = "GitHub 回了 HTTP ${response.status.value}" +
                        if (response.status.value == 403) "（可能是接口限流，过几分钟再刷新）" else ""
                    null
                }
            }
        }.getOrNull()

        if (body == null) {
            if (refusal != null) {
                lastFailureWasNetwork = false
                if (lastError == null) lastError = refusal
            } else {
                lastFailureWasNetwork = true
                if (lastError == null) lastError = "没连上 GitHub（需要代理的话先开代理再刷新）"
            }
            return null
        }
        val parsed = runCatching { json.decodeFromString(GhSearch.serializer(), body) }.getOrNull()
        if (parsed == null && lastError == null) lastError = "GitHub 的返回看不懂（接口改动？）"
        return parsed?.items
    }
}
