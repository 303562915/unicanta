package io.github.samolego.canta.data.bloat

import io.github.samolego.canta.data.app.AppBadgeInfo
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the shipped corrections against the mistakes that would only show up as a wrong badge on
 * a phone: a package name the list does not carry, a level the enum cannot read, a correction with
 * no wording, or notes filed under a language the interface never asks for.
 */
class BloatOverridesFileTest {

    // desktopTest runs with the composeApp module as its working directory, the same way
    // RecommendedAppsFileTest reads its catalog.
    private val table: BloatOverrideTable by lazy {
        val file = File("src/commonMain/composeResources/files/bloat_overrides.json")
        assertTrue(file.isFile, "corrections not found at ${file.absolutePath}")
        Json { ignoreUnknownKeys = true }
            .decodeFromString(BloatOverrideTable.serializer(), file.readText())
    }

    private val shippedList: Map<String, JsonElement> by lazy {
        val file = File("src/commonMain/composeResources/files/bloat_list.json")
        assertTrue(file.isFile, "bloat list not found at ${file.absolutePath}")
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    @Test
    fun thereIsSomethingToCorrect() {
        assertTrue(table.packages.isNotEmpty(), "the corrections file corrects nothing")
    }

    /**
     * A typo in the package name is invisible in the UI: the correction simply never applies and
     * the upstream level stays, which is exactly the bug the entry was written to fix.
     */
    @Test
    fun everyCorrectedPackageIsInTheShippedList() {
        val unknown = table.packages.keys - shippedList.keys
        assertTrue(unknown.isEmpty(), "corrected but absent from the shipped list: $unknown")
    }

    @Test
    fun everyLevelIsOneTheListUses() {
        val bad = table.packages.mapNotNull { (pkg, entry) ->
            entry.removal.takeIf { it.isNotBlank() && AppBadgeInfo.byNameIgnoreCaseOrNull(it) == null }
                ?.let { "$pkg=$it" }
        }
        assertTrue(bad.isEmpty(), "levels the enum cannot read: $bad")
    }

    /** A correction with neither a level nor a note is dead weight; it does nothing at all. */
    @Test
    fun everyCorrectionChangesSomething() {
        val empty = table.packages.filter { (_, entry) ->
            entry.removal.isBlank() && entry.notes.values.none { it.isNotBlank() }
        }.keys
        assertTrue(empty.isEmpty(), "corrections that change nothing: $empty")
    }

    /**
     * The notes are appended to a description, so a correction without wording would downgrade a
     * badge and say nothing about why — and English is the fallback every other language reads.
     */
    @Test
    fun everyCorrectionExplainsItselfInEnglish() {
        val silent = table.packages.filter { (_, entry) ->
            entry.notes["en"].isNullOrBlank()
        }.keys
        assertTrue(silent.isEmpty(), "corrections with no English note: $silent")
    }

    @Test
    fun notesUseLanguagesTheAppShips() {
        val known = setOf("en", "zh")
        val odd = table.packages.flatMap { (pkg, entry) ->
            entry.notes.keys.filter { it !in known }.map { "$pkg:$it" }
        }
        assertTrue(odd.isEmpty(), "notes filed under languages the app does not probe: $odd")
    }

    /** The list's own level is what the correction is measured against; it has to differ. */
    @Test
    fun everyLevelCorrectionActuallyMovesTheBadge() {
        val noOps = table.packages.mapNotNull { (pkg, entry) ->
            if (entry.removal.isBlank()) return@mapNotNull null
            val published = (shippedList[pkg] as? JsonObject)
                ?.get("removal")
                ?.let { it as? JsonPrimitive }
                ?.content
            if (published.equals(entry.removal, ignoreCase = true)) "$pkg=$published" else null
        }
        assertTrue(noOps.isEmpty(), "corrections that repeat the list's own level: $noOps")
    }
}
