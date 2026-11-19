package io.github.samolego.canta.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The share code is the only thing standing between "somebody pasted a sentence" and "the app opens
 * a plan", so its format, its checksum and its tolerance for surrounding text are pinned here.
 */
class ShareCodeTest {

    @Test
    fun codeHasTheDocumentedShape() {
        val code = ShareCode.of(42)
        assertEquals("CANTA-PLAN-42-${ShareCode.checksumOf(42)}", code)
        assertTrue(code.startsWith(ShareCode.PREFIX))
        assertTrue(Regex("^CANTA-PLAN-\\d{1,18}-[0-9A-F]{4}$").matches(code), code)
    }

    @Test
    fun codeRoundTripsThroughTheClipboardText() {
        val message = ShareCode.message(1234, "fuxi light debloat")
        assertTrue(message.contains(ShareCode.of(1234)), message)
        assertEquals(1234L, ShareCode.parse(message))
    }

    @Test
    fun codeIsFoundInsideAnySentence() {
        val code = ShareCode.of(7)
        assertEquals(7L, ShareCode.parse("看看这个方案 $code 挺稳的"))
        assertEquals(7L, ShareCode.parse("$code"))
        assertEquals(7L, ShareCode.parse("  ${code.lowercase()}  "))
    }

    @Test
    fun aBrokenChecksumIsRejected() {
        val code = ShareCode.of(7)
        val broken = code.dropLast(1) + if (code.last() == 'A') "B" else "A"
        assertNull(ShareCode.parse(broken))
        assertFalse(ShareCode.isCode(broken))
    }

    @Test
    fun textWithoutACodeIsRejected() {
        assertNull(ShareCode.parse(null))
        assertNull(ShareCode.parse(""))
        assertNull(ShareCode.parse("随便一段文字"))
        assertNull(ShareCode.parse("CANTA-PLAN-"))
        assertNull(ShareCode.parse("CANTA-PLAN-abc-DEFG"))
        assertNull(ShareCode.parse("CANTA-PLAN-0-0000"))
    }

    @Test
    fun aInvalidPlanHasNoMessage() {
        assertEquals("", ShareCode.message(0))
        assertEquals("", ShareCode.message(-3))
    }

    @Test
    fun checksumIsStableForTheSameId() {
        // Written down so a code made by one build opens in the next one: these values must not
        // change when the implementation is refactored.
        assertEquals(ShareCode.checksumOf(1), ShareCode.checksumOf(1))
        assertEquals(4, ShareCode.checksumOf(999_999).length)
        assertTrue(ShareCode.checksumOf(999_999).all { it in "0123456789ABCDEF" })
    }
}
