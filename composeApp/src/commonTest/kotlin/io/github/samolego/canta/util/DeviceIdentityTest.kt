package io.github.samolego.canta.util

import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.testing.MemoryStorage
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The per-install identity: what it looks like, and that it survives a restart. */
class DeviceIdentityTest {

    @Test
    fun tokenIsFixedLengthLowercaseHex() {
        repeat(20) {
            val token = DeviceIdentity.newToken(Random(it))
            assertEquals(DeviceIdentity.TOKEN_LENGTH, token.length, token)
            assertTrue(token.all { c -> c in '0'..'9' || c in 'a'..'f' }, token)
        }
    }

    @Test
    fun tokensDiffer() {
        val tokens = (0 until 50).map { DeviceIdentity.newToken() }.toSet()
        assertEquals(50, tokens.size)
    }

    @Test
    fun codeIsTheUppercaseHeadInTwoGroups() {
        assertEquals("A1B2C-3D4E5", DeviceIdentity.codeOf("a1b2c3d4e5f60718"))
        // Padding keeps the shape stable for a short or empty token.
        assertEquals("ABC00-00000", DeviceIdentity.codeOf("abc"))
        assertEquals("—", DeviceIdentity.codeOf(""))
        // Separators and case do not matter.
        assertEquals("A1B2C-3D4E5", DeviceIdentity.codeOf("A1-B2-C3-D4-E5"))
    }

    @Test
    fun ownerIsPrefixedAndEmptyStaysEmpty() {
        assertEquals("dev-abc", DeviceIdentity.ownerOf("abc"))
        assertEquals("", DeviceIdentity.ownerOf(""))
    }

    @Test
    fun onlyTheExactPasswordUnlocks() {
        assertTrue(DeviceIdentity.isTestPassword("369369"))
        assertTrue(DeviceIdentity.isTestPassword(" 369369\n"))
        assertFalse(DeviceIdentity.isTestPassword("3693690"))
        assertFalse(DeviceIdentity.isTestPassword("36936"))
        assertFalse(DeviceIdentity.isTestPassword(""))
        assertFalse(DeviceIdentity.isTestPassword("936936"))
    }

    @Test
    fun ensureKeepsTheTokenItGenerated() = runTest {
        val settings = CantaSettings(MemoryStorage())

        val first = DeviceIdentity.ensure(settings)
        assertEquals(DeviceIdentity.TOKEN_LENGTH, first.length)
        // Second visit, same install: the same code, no new login.
        assertEquals(first, DeviceIdentity.ensure(settings))
    }

    @Test
    fun ensureReturnsAStoredTokenUnchanged() = runTest {
        val settings = CantaSettings(MemoryStorage())
        settings.setIdentityToken("0123456789abcdef0123456789abcdef")

        assertEquals("0123456789abcdef0123456789abcdef", DeviceIdentity.ensure(settings))
    }
}
