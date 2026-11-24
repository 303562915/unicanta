package io.github.samolego.canta.data.bloat

import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.app.AppBadgeInfo
import io.github.samolego.canta.testing.MemoryStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
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

    /** Serves the commits API and the list; records requested URLs. */
    private class Server(var sha: String? = "new", var listOk: Boolean = true, val list: String) {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            val url = request.url.toString()
            requests += url
            when {
                "api.github.com" in url && sha != null -> respond("""[{"sha": "$sha"}]""")
                "raw.githubusercontent.com" in url && listOk -> respond(list)
                else -> respondError(HttpStatusCode.ServiceUnavailable)
            }
        })
        val listDownloads get() = requests.count { "raw.githubusercontent.com" in it }
    }

    private suspend fun settings(latestHash: String = "", autoUpdate: Boolean = true) =
        CantaSettings(MemoryStorage()).apply {
            setLatestCommitHash(latestHash)
            setAutoUpdateBloatList(autoUpdate)
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

        val bloat = BloatRepository(server.client, cache, settings("same")).load()

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

    private val remoteFixes =
        """[{"pkg": "com.cached", "removal": "Expert", "notes": {"en": "Emergency-call locator."}}]"""

    private fun correctionsBundled() = BloatOverrides(
        BloatOverrideTable(packages = mapOf("com.cached" to BloatOverrideEntry(removal = "Recommended"))),
    )

    /** 库里把随包的 Recommended 改成了 Expert：列表上看到的是 Expert，答复本身也落了盘。 */
    @Test
    fun theBackendsCorrectionsAreFetchedCachedAndApplied() = runTest {
        val server = Server(sha = "same", list = listJson)
        val fixes = MemoryStorage()

        val list = BloatRepository(
            server.client,
            MemoryStorage(cachedJson.encodeToByteArray()),
            settings("same"),
            overrides = { correctionsBundled() },
            remoteCorrections = { remoteFixes },
            correctionsCache = fixes,
        ).load()

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
            remoteCorrections = { error("the stored table has to be enough") },
            correctionsCache = MemoryStorage(remoteFixes.encodeToByteArray()),
        ).local()

        assertEquals(AppBadgeInfo.EXPERT, list.getValue("com.cached").badgeInfo)
        assertTrue(server.requests.isEmpty())
    }

    /** 拉取失败：手上那张随包的表照旧生效，不因为这次没连上就退回上游的分级。 */
    @Test
    fun aFailedFetchKeepsWhatIsAlreadyKnown() = runTest {
        val server = Server(sha = "same", list = listJson)

        val list = BloatRepository(
            server.client,
            MemoryStorage(cachedJson.encodeToByteArray()),
            settings("same"),
            overrides = { correctionsBundled() },
            remoteCorrections = { null },
            correctionsCache = MemoryStorage(),
        ).load()

        assertEquals(AppBadgeInfo.RECOMMENDED, list.getValue("com.cached").badgeInfo)
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
