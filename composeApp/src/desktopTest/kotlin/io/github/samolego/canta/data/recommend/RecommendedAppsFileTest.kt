package io.github.samolego.canta.data.recommend

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the shipped recommendation catalog: it is a plain JSON file that nothing type-checks at
 * the call site, so a stale category key or a non-https link would only surface as a card that
 * does nothing on a phone.
 */
class RecommendedAppsFileTest {

    // desktopTest runs with the composeApp module as its working directory, the same way
    // StringResourcesTest reads the resources.
    private val catalog: Recommendations by lazy {
        val file = File("src/commonMain/composeResources/files/recommended_apps.json")
        assertTrue(file.isFile, "catalog not found at ${file.absolutePath}")
        Recommendations(file.readText())
    }

    @Test
    fun everyEntryHasWhatItsCardDraws() {
        assertTrue(catalog.apps.isNotEmpty(), "the catalog is empty")
        catalog.apps.forEach { app ->
            assertTrue(app.id.isNotBlank(), "missing id: $app")
            assertTrue(app.name.isNotBlank(), "missing name: ${app.id}")
            assertTrue(app.author.isNotBlank(), "missing author: ${app.id}")
            assertTrue(app.summary.isNotBlank(), "missing summary: ${app.id}")
            assertTrue(app.tileMark.isNotBlank(), "missing tile mark: ${app.id}")
            assertTrue(
                Regex("^#[0-9a-fA-F]{6}$").matches(app.accent),
                "accent of ${app.id} is not #RRGGBB: ${app.accent}",
            )
        }
    }

    @Test
    fun idsAreUnique() {
        val duplicates = catalog.apps.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "duplicate ids: $duplicates")
    }

    @Test
    fun everyLinkOpensOutsideTheApp() {
        catalog.apps.forEach { app ->
            assertTrue(app.url.startsWith("https://"), "not an https link: ${app.id} -> ${app.url}")
        }
    }

    /**
     * A card filed under a category the page does not know would be dropped without a trace, so
     * the file has to agree with [Recommendations.CATEGORIES] exactly.
     */
    @Test
    fun categoriesMatchThePage() {
        val used = catalog.apps.map { it.category }.toSet()
        assertEquals(emptySet(), used - Recommendations.CATEGORIES.toSet(), "unknown categories")
        assertEquals(Recommendations.CATEGORIES.toSet(), used, "categories with no app")
    }

    @Test
    fun featuredEntriesShowUpInNoCategory() {
        assertTrue(catalog.featured.isNotEmpty(), "nothing is pinned above the categories")
        val grouped = catalog.grouped().flatMap { it.second }.map { it.id }.toSet()
        catalog.featured.forEach { app ->
            assertTrue(app.id !in grouped, "${app.id} is both pinned and filed under a category")
        }
    }
}
