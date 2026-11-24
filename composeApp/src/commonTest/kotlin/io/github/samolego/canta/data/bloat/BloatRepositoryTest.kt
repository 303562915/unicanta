package io.github.samolego.canta.data.bloat

import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.testing.MemoryStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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

    /** The day key the repository writes: the phone's own date, `YYYY-MM-DD`. */
    private fun today(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
}
