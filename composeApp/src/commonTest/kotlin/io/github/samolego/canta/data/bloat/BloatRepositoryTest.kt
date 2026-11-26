package io.github.samolego.canta.data.bloat

import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.bloat.BundledBloatList
import io.github.samolego.canta.data.app.AppBadgeInfo
import io.github.samolego.canta.testing.MemoryStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BloatRepositoryTest {

    private val listJson = """{"com.a": {"description": "A", "removal": "Recommended"}}"""
    private val cachedJson = """{"com.cached": {"description": "C", "removal": "Expert"}}"""
    private val bundledJson = """{"com.bundled": {"description": "B", "removal": "Caution"}}"""

    /** 枚举目录里随包的那一份，只读一次。 */
    private val bundledFromResources: String by lazy {
        runBlocking { BundledBloatList.raw() }.orEmpty()
    }

    /**
     * 这条用例自己那台服务器的 commits 返回值：永远不会等于 settings 里写的 `same`，
     * 所以"要不要真的去下名单"这件事在这里永远是确定的。
     */
    private val SENTINEL_SHA = "sentinel-sha"

    /**
     * Serves the corrections file, the commits API and the list; records requested URLs.
     *
     * 三个地址（名单、commits、修正表）都由 [settings] 显式写进去，MockEngine 按完整 URL
     * 精确匹配。不按主机名模糊命中：产品默认值将来还会变，测试不该跟着变。
     */
    private class Server(
        var sha: String? = "new",
        var listOk: Boolean = true,
        val list: String,
        var fixes: String? = null,
    ) {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            val url = request.url.toString()
            requests += url
            // 先取到局部变量再判空：`fixes` / `sha` 是 var，Kotlin 不给它们做智能转换。
            val answer = fixes
            val shaNow = sha
            // 三个地址**精确**匹配：测试用的 URL 由 settings 显式指定，谁先命中就不再取决于
            // DEFAULT_* 常量长什么样（那正是这两条用例曾经在 CI 上不稳的原因）。
            when {
                url == fixesUrl && answer != null -> respond(answer)
                url == commitsUrl && shaNow != null -> respond(shaJson(shaNow))
                url == listUrl && listOk -> respond(list)
                else -> respondError(HttpStatusCode.ServiceUnavailable)
            }
        })
        val listDownloads get() = requests.count { it == listUrl }
    }

    private companion object {
        /** 修正表：只在显式放进 settings 的用例里命中。 */
        const val fixesUrl = "https://example.test/level_fixes.json"

        /** 修正表的「变没变」接口。 */
        const val commitsUrl = "https://example.test/commits"

        /** 名单本体。 */
        const val listUrl = "https://example.test/bloat_list.json"

        /** commits 接口的答复，避开在源码里写嵌套三引号。 */
        fun shaJson(sha: String) = "[{\"sha\": \"" + sha + "\"}]"
    }

    private suspend fun settings(latestHash: String = "", autoUpdate: Boolean = true) =
        CantaSettings(MemoryStorage()).apply {
            setLatestCommitHash(latestHash)
            setAutoUpdateBloatList(autoUpdate)
            // 三个地址显式指着 MockEngine 认的那几个：不让任何用例依赖产品的默认 URL。
            setBloatListUrl(listUrl)
            setCommitsUrl(commitsUrl)
        }

    @Test
    fun downloadsAndCachesWhenNothingIsCached() = runTest {
        val server = Server(list = listJson)
        val cache = MemoryStorage()
        val settings = settings()

        val bloat = BloatRepository(server.client, cache, settings).load()

        assertEquals(setOf("com.a"), bloat.keys)
        assertEquals(listJson, cache.content?.decodeToString())
        assertEquals("new", settings.latestCommitHashFlow.first())
    }

    @Test
    fun usesCacheWhenUpToDate() = runTest {
        val server = Server(sha = "same", list = listJson)
        val bloat = BloatRepository(server.client, MemoryStorage(cachedJson.encodeToByteArray()), settings("same")).load()

        assertEquals(setOf("com.cached"), bloat.keys)
        assertEquals(0, server.listDownloads)
    }

    @Test
    fun keepsCacheWhenDownloadFails() = runTest {
        val server = Server(sha = "newer", listOk = false, list = listJson)
        val cache = MemoryStorage(cachedJson.encodeToByteArray())

        val bloat = BloatRepository(server.client, cache, settings("old")).load()

        assertEquals(setOf("com.cached"), bloat.keys)
        assertEquals(cachedJson, cache.content?.decodeToString())
    }

    @Test
    fun replacesCorruptCache() = runTest {
        val server = Server(sha = "same", list = listJson)
        val cache = MemoryStorage("{not json".encodeToByteArray())

        val bloat = BloatRepository(server.client, cache, settings("same"), bundled = { bundledFromResources }).load()

        assertEquals(setOf("com.a"), bloat.keys)
        assertEquals(listJson, cache.content?.decodeToString())
    }

    @Test
    fun noNetworkWhenAutoUpdateIsOffAndCacheExists() = runTest {
        val server = Server(list = listJson)
        BloatRepository(server.client, MemoryStorage(cachedJson.encodeToByteArray()), settings(autoUpdate = false)).load()
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun fallsBackToShippedListWhenOffline() = runTest {
        val server = Server(sha = null, listOk = false, list = listJson)
        val cache = MemoryStorage()

        val bloat = BloatRepository(
            server.client,
            cache,
            settings(),
            bundled = { bundledJson },
        ).load()

        assertEquals(setOf("com.bundled"), bloat.keys)
        // The shipped copy is not a download: it must not be written where the cache lives.
        assertNull(cache.content)
    }

    @Test
    fun downloadedListWinsOverShippedList() = runTest {
        val server = Server(list = listJson)
        val cache = MemoryStorage()

        val bloat = BloatRepository(
            server.client,
            cache,
            settings(),
            bundled = { bundledJson },
        ).load()

        assertEquals(setOf("com.a"), bloat.keys)
        assertEquals(listJson, cache.content?.decodeToString())
    }

    // ---- 本地先答、一天核对一次 -------------------------------------------------------------

    /** The list on screen comes from the cache, and asking for it costs no request at all. */
    @Test
    fun theLocalListIsAnsweredWithoutAnyRequest() = runTest {
        val server = Server(list = listJson)

        val bloat = BloatRepository(server.client, MemoryStorage(cachedJson.encodeToByteArray()), settings()).local()

        assertEquals(setOf("com.cached"), bloat.keys)
        assertTrue(server.requests.isEmpty(), "the local answer must not touch the network")
    }

    /** A day that already had its check stays offline, even when GitHub has something newer. */
    @Test
    fun aDayThatWasAlreadyCheckedDoesNotTouchTheNetwork() = runTest {
        val server = Server(sha = "newer", list = listJson)
        val settings = settings("old").apply { setBloatListCheckedDay(today()) }

        val fresh = BloatRepository(server.client, MemoryStorage(cachedJson.encodeToByteArray()), settings).refresh()

        assertNull(fresh, "nothing new is a null answer, not an empty list")
        assertTrue(server.requests.isEmpty())
    }

    /** Up to date: the day is spent so the next launches stay offline until tomorrow. */
    @Test
    fun anUpToDateCheckSpendsTheDay() = runTest {
        val server = Server(sha = "same", list = listJson)
        val settings = settings("same")

        BloatRepository(server.client, MemoryStorage(cachedJson.encodeToByteArray()), settings).refresh()

        assertEquals(today(), settings.bloatListCheckedDayFlow.first())
    }

    /** Offline: no day is spent, so the next launch tries again instead of waiting until tomorrow. */
    @Test
    fun anOfflineCheckDoesNotSpendTheDay() = runTest {
        val server = Server(sha = null, listOk = false, list = listJson)
        val settings = settings("old")

        BloatRepository(server.client, MemoryStorage(cachedJson.encodeToByteArray()), settings).refresh()

        assertEquals("", settings.bloatListCheckedDayFlow.first())
    }

    // ---- 一周问一次：列表动得慢，天天问只是白花流量 ------------------------------------------

    /** 六天前核对过也算"刚看过"：这一周里一次网络都不碰。 */
    @Test
    fun aCheckWithinTheWeekStaysOffline() = runTest {
        val server = Server(sha = "newer", list = listJson)
        val settings = settings("old").apply { setBloatListCheckedDay(dayOffset(6)) }

        val fresh = BloatRepository(server.client, MemoryStorage(cachedJson.encodeToByteArray()), settings).refresh()

        assertNull(fresh, "nothing new is a null answer, not an empty list")
        assertTrue(server.requests.isEmpty(), "a check six days old is still fresh")
    }

    /** 过了七天就重新问一遍；离线时照旧不记账。 */
    @Test
    fun aCheckOlderThanTheWeekGoesOutAgain() = runTest {
        val server = Server(sha = "newer", list = listJson)
        val settings = settings("old").apply { setBloatListCheckedDay(dayOffset(8)) }

        BloatRepository(server.client, MemoryStorage(cachedJson.encodeToByteArray()), settings).refresh()

        assertTrue(server.requests.isNotEmpty(), "a check eight days old has to be redone")
    }

    // ---- 库里的分级修正：同一个窗口下来一次，落在本地，下次不联网也在 ------------------------

    private val remoteFixes = """
        {
          "version": 2,
          "updated": "2026-11-26",
          "packages": {
            "com.cached": {
              "removal": "Expert",
              "source": "maintainer",
              "notes": {"en": "Emergency-call locator."}
            }
          }
        }
    """.trimIndent()

    private fun correctionsBundled() = BloatOverrides(
        BloatOverrideTable(packages = mapOf("com.cached" to BloatOverrideEntry(removal = "Recommended"))),
    )

    /** 仓库里那份把随包的 Recommended 改成了 Expert：列表上看到的是 Expert，文件本身也落了盘。 */
    @Test
    fun theRepositoryCorrectionsAreFetchedCachedAndApplied() = runTest {
        val server = Server(sha = "same", list = listJson, fixes = remoteFixes)
        val fixes = MemoryStorage()
        val repository = BloatRepository(
            server.client,
            MemoryStorage(cachedJson.encodeToByteArray()),
            settings("same"),
            overrides = { correctionsBundled() },
            // 修正表现在是仓库里的静态文件：地址给一个占位，真正的内容由 MockEngine 按 URL 返回。
            correctionsUrl = { fixesUrl },
            correctionsCommitsUrl = null,
            correctionsCache = fixes,
        )
        assertTrue(repository.refreshCorrections())
        val list = repository.load()

        assertEquals(AppBadgeInfo.EXPERT, list.getValue("com.cached").badgeInfo)
        assertEquals(remoteFixes, fixes.content?.decodeToString())
    }

    /** 下次启动、而且这一周不再核对：修正只从盘上读，一个请求都不发。 */
    @Test
    fun theStoredCorrectionsApplyWithoutTouchingTheNetwork() = runTest {
        val server = Server(list = listJson)
        val settings = settings("same").apply { setBloatListCheckedDay(dayOffset(1)) }

        val list = BloatRepository(
            server.client,
            MemoryStorage(cachedJson.encodeToByteArray()),
            settings,
            overrides = { correctionsBundled() },
            correctionsUrl = { error("the stored table has to be enough") },
            correctionsCommitsUrl = null,
            correctionsCache = MemoryStorage(remoteFixes.encodeToByteArray()),
        ).local()

        assertEquals(AppBadgeInfo.EXPERT, list.getValue("com.cached").badgeInfo)
        assertTrue(server.requests.isEmpty())
    }

    /** 拉取失败：手上那张随包的表照旧生效，不因为这次没连上就退回上游的分级。 */
    @Test
    fun aFailedFetchKeepsWhatIsAlreadyKnown() = runTest {
        // 刻意用**别的用例绝不会用**的地址：MockEngine 对未知 URL 回 503，这才是"这次没连上"。
        // 之前这里用的是 fixesUrl 而 `Server.fixes = null`——但 `fixes` 是 var，同档里别的用例把它
        // 改成有内容之后，这个用例就会真的拉到修正表，于是 refreshCorrections() 返回 true、
        // 断言 expected:<false> but was:<true>（CI run 45757908675 / 45758627750）。地址不共享，
        // 这个前提就稳了。
        val unreachable = "https://example.test/level_fixes.unreachable.json"
        // 用**整份随包名单**当脚手架：手上的缓存通常读不出来（那正是要继续往下拉的理由），随包那份
        // 才是"下到名单之后""一个是新commit"这种情况真正用得上的一份。空表会让下面的断言变成
        // "什么都没下到"，而那不是这条用例要说的事。
        val server = Server(sha = SENTINEL_SHA, list = bundledFromResources)
        val settings = settings("same")
        val fixes = MemoryStorage()

        val repository = BloatRepository(
            server.client,
            MemoryStorage(cachedJson.encodeToByteArray()),
            settings,
            overrides = { correctionsBundled() },
            correctionsUrl = { unreachable },
            correctionsCommitsUrl = null,
            correctionsCache = fixes,
            bundled = { bundledFromResources },
        )

        assertEquals(false, repository.refreshCorrections())
        assertNull(fixes.content)
        // 没拉到东西不算用掉这一周的那次核对：下次启动还会再试。
        assertEquals("", settings.correctionsFetchedDayFlow.first())
        // 界面上仍是我们随包那张表（Recommended）：没联网就不会凭空多出一条修正。
        assertEquals(AppBadgeInfo.RECOMMENDED, repository.local().getValue("com.cached").badgeInfo)
    }

    /** 修正也是按自然周核对：同一周内第二次进列表，一个请求都不发。 */
    @Test
    fun theCorrectionsAreCheckedOnceAWeek() = runTest {
        val server = Server(sha = "same", list = listJson, fixes = remoteFixes)
        val settings = settings("same")
        var fetches = 0
        val repository = BloatRepository(
            server.client,
            MemoryStorage(cachedJson.encodeToByteArray()),
            settings,
            overrides = { correctionsBundled() },
            correctionsUrl = { fetches++; fixesUrl },
            correctionsCommitsUrl = null,
            correctionsCache = MemoryStorage(),
        )

        assertTrue(repository.refreshCorrections())
        assertEquals(1, fetches)

        // 同一周再进列表：不联网，也不重复解析。
        assertEquals(false, repository.refreshCorrections())
        assertEquals(1, fetches)
        assertEquals(AppBadgeInfo.EXPERT, repository.local().getValue("com.cached").badgeInfo)

        // 上次核对已经是 8 天前：下一周这次照常发请求。
        val stale = settings("same").apply { setCorrectionsFetchedDay(dayOffset(8)) }
        var refetches = 0
        BloatRepository(
            server.client,
            MemoryStorage(cachedJson.encodeToByteArray()),
            stale,
            overrides = { correctionsBundled() },
            correctionsUrl = { refetches++; fixesUrl },
            correctionsCommitsUrl = null,
            correctionsCache = MemoryStorage(),
        ).let { assertTrue(it.refreshCorrections()) }
        assertEquals(1, refetches)
    }

    /** The day key the repository writes: the phone's own date, `YYYY-MM-DD`. */
    @OptIn(ExperimentalTime::class)
    private fun today(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    /** [today] shifted by [days], in the same `YYYY-MM-DD` form the repository stores. */
    @OptIn(ExperimentalTime::class)
    private fun dayOffset(days: Int): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            .minus(DatePeriod(days = days))
            .toString()
}
