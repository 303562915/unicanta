package io.github.samolego.canta.data.bloat

import io.github.samolego.canta.data.app.AppBadgeInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The shipped corrections are a small JSON file that nothing type-checks, and the failure it
 * guards against is silent: a wrong level or a note that never lands still renders a perfectly
 * normal list row.
 */
class BloatOverridesTest {

    private val published = mapOf(
        "com.qualcomm.location" to BloatData("LocationServices", AppBadgeInfo.RECOMMENDED),
        "com.android.chrome" to BloatData("Chrome", AppBadgeInfo.ADVANCED),
        "com.untouched" to BloatData(null, null),
    )

    private fun overrides(
        vararg entries: Pair<String, BloatOverrideEntry>,
        language: String = "zh",
    ) = BloatOverrides(BloatOverrideTable(packages = entries.toMap()), language)

    @Test
    fun aCorrectionLowersTheLevel() {
        val table = overrides("com.qualcomm.location" to BloatOverrideEntry(removal = "Expert"))

        val patched = table.apply("com.qualcomm.location", published.getValue("com.qualcomm.location"))

        assertEquals(AppBadgeInfo.EXPERT, patched.badgeInfo)
        // No note, no rewording: the level is the whole correction.
        assertEquals("LocationServices", patched.description)
        assertFalse(patched.translated, "the list's own text is still the list's own text")
    }

    @Test
    fun aNoteIsAppendedInTheInterfaceLanguage() {
        val table = overrides(
            "com.qualcomm.location" to BloatOverrideEntry(
                removal = "Expert",
                notes = mapOf("en" to "English note", "zh" to "中文说明"),
            ),
            language = "zh",
        )

        val patched = table.apply("com.qualcomm.location", published.getValue("com.qualcomm.location"))

        assertTrue(patched.description!!.startsWith("LocationServices"), patched.description)
        assertTrue(patched.description!!.endsWith("中文说明"), patched.description)
        // Our wording is in there now, so the page must stop calling the text upstream's.
        assertTrue(patched.translated)
    }

    /** An interface language the file does not cover still gets the note, not silence. */
    @Test
    fun aMissingTranslationFallsBackToEnglish() {
        val table = overrides(
            "com.android.chrome" to BloatOverrideEntry(notes = mapOf("en" to "English note")),
            language = "ja",
        )

        val patched = table.apply("com.android.chrome", published.getValue("com.android.chrome"))

        assertTrue(patched.description!!.endsWith("English note"), patched.description)
    }

    /** A typo in the file must not erase a badge: the list's level survives it. */
    @Test
    fun anUnknownLevelKeepsTheListsOwn() {
        val table = overrides("com.android.chrome" to BloatOverrideEntry(removal = "Recommendd"))

        val patched = table.apply("com.android.chrome", published.getValue("com.android.chrome"))

        assertEquals(AppBadgeInfo.ADVANCED, patched.badgeInfo)
    }

    /** An entry for a package the list does not carry is inert, not a crash. */
    @Test
    fun aCorrectionForAnAbsentPackageChangesNothing() {
        val table = overrides("com.nowhere" to BloatOverrideEntry(removal = "Expert"))

        val patched = published.withOverrides(table)

        assertEquals(published.keys, patched.keys)
        assertEquals(AppBadgeInfo.RECOMMENDED, patched.getValue("com.qualcomm.location").badgeInfo)
    }

    /** With no corrections the map is handed back as-is; the list path stays allocation-free. */
    @Test
    fun anEmptyTableReturnsTheSameMap() {
        assertSame(published, published.withOverrides(BloatOverrides.empty()))
    }

    @Test
    fun aPackageWithNoDescriptionGetsTheNoteAsItsDescription() {
        val table = overrides(
            "com.untouched" to BloatOverrideEntry(notes = mapOf("en" to "Only a note")),
            language = "en",
        )

        val patched = table.apply("com.untouched", published.getValue("com.untouched"))

        assertEquals("Only a note", patched.description)
        assertNull(patched.badgeInfo)
    }
}
