package io.github.samolego.canta.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

/** The avatar of a review is the first readable character of the name it is signed with. */
class AvatarLetterTest {

    @Test
    fun takesTheFirstLetterOfAName() {
        assertEquals("A", avatarLetter("Alice"))
        assertEquals("李", avatarLetter("李雷"))
    }

    @Test
    fun skipsTheSignOfAnInstallCode() {
        // A review written before the user picked a name is signed with the install code.
        assertEquals("1", avatarLetter("#10000"))
        assertEquals("A", avatarLetter(" A1B2C-3D4E5"))
    }

    @Test
    fun keepsAnEmojiWhole() {
        assertEquals("🦊", avatarLetter("🦊 fox"))
        assertEquals("🦊", avatarLetter("#🦊"))
    }

    @Test
    fun hasSomethingToShowForNothing() {
        assertEquals("?", avatarLetter(""))
        assertEquals("?", avatarLetter("   "))
        assertEquals("?", avatarLetter("#"))
    }
}
