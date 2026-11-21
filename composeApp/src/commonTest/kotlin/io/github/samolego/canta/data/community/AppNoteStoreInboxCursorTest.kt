package io.github.samolego.canta.data.community

import io.github.samolego.canta.testing.MemoryStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The stored read cursor — now four of them, one per inbox tab.
 *
 * These are the rules that make the red dot stay away: each tab is written on its own, the positions
 * come back after a restart, they never go backwards, a backend that answers no position does not
 * erase one, and a single stored instant from an older build reads as "all four tabs were read up to
 * here" rather than "nothing was ever read".
 */
class AppNoteStoreInboxCursorTest {

    private val nine = "2026-11-20T09:00:00.000000Z"
    private val ten = "2026-11-20T10:00:00.000000Z"
    private val eleven = "2026-11-20T11:00:00.000000Z"

    private fun cursors(store: AppNoteStore): InboxCursors = store.inboxSeenCursors.value

    @Test
    fun everyPositionSurvivesARestart() = runTest {
        val storage = MemoryStorage()
        val store = AppNoteStore(storage)
        store.markInboxSeen(InboxKind.REPLY, ten)
        store.markInboxSeen(InboxKind.COMMENT_HEART, eleven)

        // A second store over the same bytes is what the next launch of the app is.
        val reopened = AppNoteStore(storage)
        reopened.ensureReady()
        assertEquals(ten, cursors(reopened)[InboxKind.REPLY])
        assertEquals(eleven, cursors(reopened)[InboxKind.COMMENT_HEART])
        // The tabs that were never read came back as never read, not as "read at epoch".
        assertEquals("", cursors(reopened)[InboxKind.PLAN_COMMENT])
        assertEquals("", cursors(reopened)[InboxKind.PLAN_HEART])
    }

    @Test
    fun oneTabMovesWithoutDraggingTheOthers() = runTest {
        val store = AppNoteStore(MemoryStorage())
        store.markInboxSeen(InboxKind.REPLY, ten)
        // The whole point of the four cursors: reading the replies is not reading the hearts.
        assertEquals("", cursors(store)[InboxKind.COMMENT_HEART], "the hearts moved with a tab that was not read")
        store.markInboxSeen(InboxKind.COMMENT_HEART, nine)
        assertEquals(ten, cursors(store)[InboxKind.REPLY])
        assertEquals(nine, cursors(store)[InboxKind.COMMENT_HEART])
    }

    @Test
    fun aTabOnlyMovesForward() = runTest {
        val store = AppNoteStore(MemoryStorage())
        store.markInboxSeen(InboxKind.REPLY, ten)
        // A slow answer from an earlier visit must not un-read what arrived after it.
        assertFalse(store.markInboxSeen(InboxKind.REPLY, nine), "an older stamp moved the tab")
        assertEquals(ten, cursors(store)[InboxKind.REPLY])
    }

    @Test
    fun anEmptyStampDoesNotEraseAPosition() = runTest {
        val store = AppNoteStore(MemoryStorage())
        store.markInboxSeen(InboxKind.REPLY, ten)
        // A backend that answers no `checked_at`, or an older build that stored nothing usable.
        assertFalse(store.markInboxSeen(InboxKind.REPLY, ""))
        assertFalse(store.markInboxSeen(""))
        assertEquals(ten, cursors(store)[InboxKind.REPLY])
        assertEquals("", cursors(store)[InboxKind.PLAN_HEART])
    }

    @Test
    fun onlyARealAdvanceSaysTheDotMayBeCleared() = runTest {
        // A plain instant is the old, single cursor: it means all four tabs.
        val store = AppNoteStore(MemoryStorage())
        assertTrue(store.markInboxSeen(ten), "the first stamp moves every tab")
        assertFalse(store.markInboxSeen(ten), "the same stamp changes nothing")
        assertFalse(store.markInboxSeen(""), "no stamp at all changes nothing")
        assertFalse(store.markInboxSeen(nine), "an older stamp never un-reads")
        assertTrue(store.markInboxSeen(eleven), "a newer one moves them again")
    }

    @Test
    fun aTabLeftBehindKeepsTheDotOn() = runTest {
        val store = AppNoteStore(MemoryStorage())
        store.markInboxSeen(ten)
        // The backend read the replies to a newer instant but could not show the hearts: clearing
        // the dot now would hide them, and the next count would put the number straight back.
        val oneTab = InboxCursors.of(ten).with(InboxKind.REPLY, eleven).encode()
        assertFalse(store.markInboxSeen(oneTab), "the dot may be cleared although the hearts are behind")
        assertEquals(eleven, cursors(store)[InboxKind.REPLY], "the tab that did move still moved")
        assertEquals(ten, cursors(store)[InboxKind.COMMENT_HEART])

        // Every tab reached the same instant: that is the old single-cursor state, and the dot may go.
        val allTabs = InboxCursors.of(eleven).encode()
        assertTrue(store.markInboxSeen(allTabs), "all four agree, the dot may be cleared")
        assertEquals(InboxCursors.of(eleven), cursors(store))
    }

    @Test
    fun theEncodedCursorIsWhatTheStoredStringReadsBack() = runTest {
        val store = AppNoteStore(MemoryStorage())
        store.markInboxSeen(ten)
        // The page carries this one string around and hands it straight back on the next count.
        assertEquals(InboxCursors.of(ten), InboxCursors.decode(store.inboxSeenAt.value))
    }

    @Test
    fun anOldSingleCursorReadsAsEveryTabRead() = runTest {
        // The blob a build from before the four cursors wrote: one instant, no per-tab map.
        val legacy = """{"inboxSeenAt":"$ten"}""".encodeToByteArray()
        val store = AppNoteStore(MemoryStorage(legacy))
        store.ensureReady()

        // Treating it as "nothing was read" would light the dot with a year of news the reader had
        // already seen. Treating it as "all four read up to here" is what that value meant.
        assertEquals(InboxCursors.of(ten), cursors(store))
        assertEquals(InboxCursors.of(ten), InboxCursors.decode(store.inboxSeenAt.value))
    }

    @Test
    fun aBlobWithAPerTabMapIgnoresTheSingleCursor() = runTest {
        // Both fields present: the map is the truth, the single value is only a mirror for old builds.
        val blob = """{"inboxSeenCursors":{"reply":"$ten"},"inboxSeenAt":"$nine"}""".encodeToByteArray()
        val store = AppNoteStore(MemoryStorage(blob))
        store.ensureReady()

        assertEquals(ten, cursors(store)[InboxKind.REPLY])
        assertEquals("", cursors(store)[InboxKind.COMMENT_HEART], "a tab with no stored position was invented")
    }

    @Test
    fun theSingleValueIsKeptAsTheNewestOfTheFour() = runTest {
        val storage = MemoryStorage()
        val store = AppNoteStore(storage)
        store.markInboxSeen(InboxKind.REPLY, nine)
        store.markInboxSeen(InboxKind.PLAN_HEART, eleven)

        // An older build reading the same blob must not count the whole history again: the one value
        // it understands is the newest position any tab reached.
        val reopened = AppNoteStore(storage)
        reopened.ensureReady()
        assertEquals(eleven, InboxCursors.decode(reopened.inboxSeenAt.value).latest)
    }
}