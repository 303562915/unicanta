package io.github.samolego.canta.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The composer's line: every sticker stands as one cell, and the caret the field measures from that
 * line still addresses the body's own offsets.
 *
 * This is the "the caret runs off" bug written down: a token is five characters wide, the picture
 * drawn for it is one glyph wide, so a caret measured from the token lands five characters past the
 * picture. One cell per sticker is what keeps the two on top of each other.
 */
class EmotionFieldDisplayTest {

    @Test
    fun aStickerBecomesOneCell() {
        val display = emotionFieldDisplayOf("好[666]的")

        assertEquals("好${EmotionFieldCell}的", display.shown.text)
        assertEquals(1, display.cells.size)
        assertEquals(1, display.cells.single().index)
        assertEquals("666", display.cells.single().emotion.name)
    }

    @Test
    fun theCaretAfterAStickerIsTheEndOfItsCell() {
        val body = "a[666]"
        val display = emotionFieldDisplayOf(body)

        assertEquals(2, display.shown.length)
        assertEquals(2, display.mapping.originalToTransformed(body.length))
    }

    @Test
    fun theCaretBeforeAStickerIsTheStartOfItsCell() {
        val display = emotionFieldDisplayOf("a[666]b")

        assertEquals(1, display.mapping.originalToTransformed(1))
        assertEquals(1, display.mapping.transformedToOriginal(1))
    }

    @Test
    fun offsetsBetweenTheWordsAreUnchanged() {
        val display = emotionFieldDisplayOf("好[666]的")

        // 好 = 0 … [666] = 1..5 … 的 = 6
        assertEquals(0, display.mapping.originalToTransformed(0))
        assertEquals(2, display.mapping.originalToTransformed(6))
        assertEquals(3, display.mapping.originalToTransformed(7))
        assertEquals(6, display.mapping.transformedToOriginal(2))
        assertEquals(7, display.mapping.transformedToOriginal(3))
    }

    @Test
    fun aCaretInsideATokenIsPulledToTheNearerEdge() {
        val display = emotionFieldDisplayOf("a[666]b")
        val tokenStart = 1
        val tokenLength = "[666]".length

        assertEquals(1, display.mapping.originalToTransformed(tokenStart + 1))
        assertEquals(2, display.mapping.originalToTransformed(tokenStart + tokenLength - 1))
    }

    @Test
    fun aBodyWithoutStickersIsItsOwnLine() {
        val display = emotionFieldDisplayOf("纯文字")

        assertEquals("纯文字", display.shown.text)
        assertTrue(display.cells.isEmpty())
        assertEquals(3, display.mapping.originalToTransformed(3))
        assertEquals(0, display.mapping.transformedToOriginal(0))
    }

    @Test
    fun aTokenThatNamesNoStickerStaysText() {
        val display = emotionFieldDisplayOf("[not_a_sticker]")

        assertEquals("[not_a_sticker]", display.shown.text)
        assertTrue(display.cells.isEmpty())
    }
}
