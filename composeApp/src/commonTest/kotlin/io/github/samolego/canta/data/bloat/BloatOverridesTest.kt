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

    // ---- 库里那份（package_level_fix_list 的答复）----------------------------------------

    private val remoteJson = """
        [
          {"pkg": "com.qualcomm.location", "removal": "Expert",
           "notes": {"en": "Emergency-call locator on some ROMs.", "zh": "部分 ROM 上是紧急呼叫定位。"},
           "author": "开发者", "updated_at": "2026-11-24T09:55:32Z"}
        ]
    """.trimIndent()

    @Test
    fun theBackendAnswerReadsAsACorrection() {
        val table = BloatOverrides.fromRemote(remoteJson, "zh")

        val patched = table.apply("com.qualcomm.location", published.getValue("com.qualcomm.location"))

        assertEquals(AppBadgeInfo.EXPERT, patched.badgeInfo)
        assertTrue(patched.description!!.endsWith("部分 ROM 上是紧急呼叫定位。"), patched.description)
    }

    /** 老部署答的是错误正文而不是数组：那就是"没有修正"，不是崩溃。 */
    @Test
    fun anUnreadableAnswerIsNoCorrection() {
        assertEquals(0, BloatOverrides.fromRemote("{\"message\":\"Could not find the function\"}").size)
        assertEquals(0, BloatOverrides.fromRemote("").size)
        assertEquals(0, BloatOverrides.fromRemote("[]").size)
    }

    @Test
    fun rowsWithoutAPackageAreDropped() {
        val table = BloatOverrides.fromRemote("""[{"pkg": "", "removal": "Expert"}, {"removal": "Expert"}]""")
        assertEquals(0, table.size)
    }

    /** 库里那份比随包那份新：同一个包两边都有时，库里的说了算。 */
    @Test
    fun theBackendWinsOverTheBundledTable() {
        val bundled = overrides("com.qualcomm.location" to BloatOverrideEntry(removal = "Advanced"))
        val remote = BloatOverrides.fromRemote(remoteJson, "zh")

        val merged = bundled.overriddenBy(remote)
        val patched = merged.apply("com.qualcomm.location", published.getValue("com.qualcomm.location"))

        assertEquals(AppBadgeInfo.EXPERT, patched.badgeInfo)
    }

    /** 两边都管不着的包原样留着：合并不是"用库里那份替掉全部"。 */
    @Test
    fun mergingKeepsTheBundledEntriesTheBackendDoesNotKnow() {
        val bundled = overrides("com.android.chrome" to BloatOverrideEntry(removal = "Unsafe"))
        val merged = bundled.overriddenBy(BloatOverrides.fromRemote(remoteJson, "zh"))

        assertEquals(2, merged.size)
    }
}
