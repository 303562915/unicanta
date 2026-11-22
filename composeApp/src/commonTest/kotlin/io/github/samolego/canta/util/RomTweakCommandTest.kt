package io.github.samolego.canta.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The prompt-driven card builds a `sh` script out of what the user types, so these tests are about
 * the two things that can go wrong there: a name that is not a package name (a command injection),
 * and a package the phone cannot live without.
 */
class RomTweakCommandTest {

    private fun tweak(command: String = "pm disable-user --user 0 {{pkg}}") = RomTweak(
        id = "test",
        rom = "hyperos",
        title = "test",
        command = command,
        action = RomTweaks.ACTION_SHELL_WITH_PACKAGE,
        prompt = "包名",
    )

    @Test
    fun thePackageNameIsSubstituted() {
        assertEquals(
            "pm disable-user --user 0 com.example.app",
            tweak().commandFor("com.example.app"),
        )
    }

    @Test
    fun surroundingWhitespaceIsNotPartOfTheName() {
        assertEquals(
            "pm disable-user --user 0 com.example.app",
            tweak().commandFor("  com.example.app \n"),
        )
    }

    @Test
    fun aShellMetacharacterIsRefusedRatherThanRun() {
        // Whatever a paste brings along, the token never becomes part of the script.
        listOf(
            "com.example.app; reboot",
            "com.example.app && rm -rf /data",
            "`id`",
            "\$(id)",
            "com.example.app|sh",
            "../../data",
            "",
        ).forEach { name ->
            assertNull(tweak().commandFor(name), "refused: $name")
        }
    }

    @Test
    fun aPackageThePhoneNeedsIsRefused() {
        NEVER_TOUCH.forEach { name ->
            assertNull(tweak().commandFor(name), "refused: $name")
        }
    }

    @Test
    fun aCardWithoutAPromptTakesNoName() {
        val plain = tweak().copy(action = RomTweaks.ACTION_SHELL)
        assertNull(plain.commandFor("com.example.app"))
    }

    @Test
    fun aCardWhoseCommandHasNoTokenStillRunsTheSameScript() {
        // The token is optional: a card can ask for a package and not use it in one branch.
        val noToken = tweak(command = "pm list packages -d")
        assertEquals("pm list packages -d", noToken.commandFor("com.example.app"))
        assertTrue(noToken.commandFor("com.example.app")!!.isNotEmpty())
    }
}
