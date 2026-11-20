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
    fun ownerIsTheNumberItself() {
        assertEquals("10000", DeviceIdentity.ownerOf(10_000))
        // Not signed in yet: nothing to own anything with.
        assertEquals("", DeviceIdentity.ownerOf(0))
        assertEquals("", DeviceIdentity.ownerOf(-5))
    }

    @Test
    fun theVisibleCodeIsTheNumber() {
        assertEquals("#10000", DeviceIdentity.codeOfUser(10_000))
        assertEquals("#99999", DeviceIdentity.codeOfUser(99_999))
        assertEquals("—", DeviceIdentity.codeOfUser(0))
        // The first number the backend can hand out is 10000.
        assertEquals(10_000L, DeviceIdentity.FIRST_USER_ID)
    }

    @Test
    fun identityFallsBackToTheTokenHeadBeforeSignIn() {
        val token = "0123456789abcdef0123456789abcdef"
        val signedOut = Identity(token)
        assertEquals("01234-56789", signedOut.code)
        assertEquals("", signedOut.owner)
        assertFalse(signedOut.known)

        val signedIn = Identity(token, 10_042)
        assertEquals("#10042", signedIn.code)
        assertEquals("10042", signedIn.owner)
        assertTrue(signedIn.known)
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

    @Test
    fun currentRemembersTheNumberAcrossRestarts() = runTest {
        val storage = MemoryStorage()
        val settings = CantaSettings(storage)

        // First run: a token, but no number yet.
        val first = DeviceIdentity.current(settings)
        assertEquals(DeviceIdentity.TOKEN_LENGTH, first.token.length)
        assertFalse(first.known)

        settings.setIdentityUserId(10_000)

        // Next run, same storage: the same token and the same number, without asking anybody.
        val again = DeviceIdentity.current(CantaSettings(storage))
        assertEquals(first.token, again.token)
        assertEquals(10_000L, again.userId)
        assertEquals("#10000", again.code)
    }
}
