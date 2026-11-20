package io.github.samolego.canta.data.recommend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The catalog's search box.
 *
 * Package names are part of what is searched: pasting `com.miui.analytics` out of a debloat list is
 * how people arrive here, and the app that replaces it is the answer to the question they have.
 */
class RecommendationsSearchTest {

    private val catalog = Recommendations(
        RecommendedAppSet(
            apps = listOf(
                RecommendedApp(
                    id = "via",
                    name = "Via 浏览器",
                    author = "Tu Yafeng",
                    category = "privacy",
                    url = "https://viayoo.com/",
                    summary = "轻量浏览器",
                    replacesPackages = listOf("com.android.browser", "com.mi.globalbrowser"),
                ),
                RecommendedApp(
                    id = "shizuku",
                    name = "Shizuku",
                    author = "RikkaApps",
                    category = "system",
                    url = "https://github.com/RikkaApps/Shizuku",
                    summary = "把 adb 权限借给普通应用",
                ),
                RecommendedApp(
                    id = "telemetry",
                    name = "TelemetryCleaner",
                    author = "Eynnzerr",
                    category = "privacy",
                    url = "https://github.com/Eynnzerr/TelemetryCleaner",
                    summary = "清掉国产系统的遥测模块",
                    replacesPackages = listOf("com.miui.analytics"),
                ),
            )
        )
    )

    private fun ids(query: String) = catalog.search(query).map { it.id }

    @Test
    fun anEmptyQueryKeepsTheWholeCatalog() {
        assertEquals(3, catalog.search("").size)
        assertEquals(3, catalog.search("   ").size, "spaces are not a search")
    }

    @Test
    fun theNameIsSearched() {
        assertEquals(listOf("shizuku"), ids("shizuku"))
        assertEquals(listOf("via"), ids("Via"), "case does not matter")
    }

    @Test
    fun theAuthorIsSearched() {
        assertEquals(listOf("telemetry"), ids("Eynnzerr"))
    }

    @Test
    fun theSummaryIsSearched() {
        // "借给普通应用" only appears in Shizuku's own sentence.
        assertEquals(listOf("shizuku"), ids("借给普通应用"))
        assertEquals(listOf("telemetry"), ids("遥测"))
    }

    @Test
    fun aPackageNameFindsTheAppThatReplacesIt() {
        // The way people actually arrive: a package copied out of a debloat list.
        assertEquals(listOf("telemetry"), ids("com.miui.analytics"))
        assertEquals(listOf("via"), ids("com.android.browser"))
    }

    @Test
    fun aPartialPackageNameStillMatches() {
        assertEquals(listOf("via"), ids("mi.globalbrowser"))
    }

    @Test
    fun anImaginaryQueryMatchesNothing() {
        assertTrue(ids("这东西不存在").isEmpty())
    }
}