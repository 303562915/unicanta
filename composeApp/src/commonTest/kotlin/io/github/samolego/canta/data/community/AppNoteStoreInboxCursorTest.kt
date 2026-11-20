package io.github.samolego.canta.data.community

import io.github.samolego.canta.testing.MemoryStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The stored read cursor: the instant the inbox was last opened, which the backend counts from.
 *
 * These are the rules that make the red dot stay away — the cursor is written, it comes back after a
 * restart, it never goes backwards, and a backend that answers no cursor does not erase it.
 */
class AppNoteStoreInboxCursorTest {

    @Test
    fun theCursorSurvivesARestart() = runTest {
        val storage = MemoryStorage()
        AppNoteStore(storage).markInboxSeen("2026-11-20T10:00:00.000000Z")

        // A second store over the same bytes is what the next launch of the app is.
        val reopened = AppNoteStore(storage)
        reopened.ensureReady()
        assertEquals("2026-11-20T10:00:00.000000Z", reopened.inboxSeenAt.value)
    }

    @Test
    fun theCursorOnlyMovesForward() = runTest {
        val store = AppNoteStore(MemoryStorage())
        store.markInboxSeen("2026-11-20T10:00:00.000000Z")
        // A slow answer from an earlier visit must not un-read what arrived after it.
        store.markInboxSeen("2026-11-20T09:00:00.000000Z")
        assertEquals("2026-11-20T10:00:00.000000Z", store.inboxSeenAt.value)
    }

    @Test
    fun anEmptyStampDoesNotEraseTheCursor() = runTest {
        val store = AppNoteStore(MemoryStorage())
        // A backend that predates the cursor answers no `checked_at`, and that must not wipe what an
        // earlier visit stored.
        store.markInboxSeen("2026-11-20T10:00:00.000000Z")
        store.markInboxSeen("")
        assertEquals("2026-11-20T10:00:00.000000Z", store.inboxSeenAt.value)
    }

    @Test
    fun onlyARealAdvanceSaysTheDotMayBeCleared() = runTest {
        val store = AppNoteStore(MemoryStorage())

        // The red dot is only cleared when this answers true: false means "the cursor did not
        // move", so the next count would put the same number back and the dot would blink.
        assertTrue(store.markInboxSeen("2026-11-20T10:00:00.000000Z"), "the first stamp moves it")
        assertFalse(store.markInboxSeen("2026-11-20T10:00:00.000000Z"), "the same stamp changes nothing")
        assertFalse(store.markInboxSeen(""), "no stamp at all changes nothing")
        assertFalse(store.markInboxSeen("2026-11-20T09:00:00.000000Z"), "an older stamp never un-reads")
        assertTrue(store.markInboxSeen("2026-11-20T11:00:00.000000Z"), "a newer one moves it again")
    }
}