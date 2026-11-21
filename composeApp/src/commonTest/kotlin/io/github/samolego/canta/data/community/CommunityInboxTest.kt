package io.github.samolego.canta.data.community

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The red dot is "something arrived in this tab after the last time it was opened".
 *
 * Two questions behind it: which rows of a page are new, and what to remember once they have been
 * read. The second is now per tab — four positions instead of one — so these tests also pin down the
 * rules that keep reading one tab from clearing another, and the migration of the single stored
 * instant an older build wrote.
 */
class CommunityInboxTest {

    private val nine = "2026-11-20T09:00:00.000000Z"
    private val ten = "2026-11-20T10:00:00.000000Z"
    private val eleven = "2026-11-20T11:00:00.000000Z"

    private fun reply(id: Long, body: String = "x") = MyComment(
        id = id,
        kind = COMMENT_KIND_PLAN,
        target = "7",
        body = body,
        createdAt = "2026-11-19T10:00:00Z",
    )

    @Test
    fun countsOnlyTheRepliesAfterTheLastOpenedOne() {
        val replies = listOf(reply(9), reply(12), reply(15))
        assertEquals(listOf(12L, 15L), replies.unreadSince(9).map { it.id })
    }

    @Test
    fun nothingIsNewWhenEverythingHasBeenOpened() {
        val replies = listOf(reply(9), reply(12), reply(15))
        assertTrue(replies.unreadSince(15).isEmpty())
    }

    @Test
    fun aFirstLookMakesEveryReplyNew() {
        // No id has ever been stored: every answer that exists is one the reader has not seen.
        assertEquals(3, listOf(reply(1), reply(2), reply(3)).unreadSince(0).size)
    }

    @Test
    fun remembersTheNewestIdToMarkTheInboxRead() {
        assertEquals(15L, newestReplyId(listOf(reply(9), reply(15), reply(12))))
        // Nothing to remember when nobody answered: a 0 must not wipe what was already read.
        assertEquals(0L, newestReplyId(emptyList()))
    }

    // ---------------------------------------------------------------- the four positions

    @Test
    fun anEncodedValueReadsBackAsItself() {
        val cursors = InboxCursors.of(ten).with(InboxKind.PLAN_HEART, eleven)
        assertEquals(cursors, InboxCursors.decode(cursors.encode()))
        assertEquals(ten, InboxCursors.decode(cursors.encode())[InboxKind.REPLY])
        assertEquals(eleven, InboxCursors.decode(cursors.encode())[InboxKind.PLAN_HEART])
    }

    @Test
    fun nothingIsReadUntilSomethingIsStamped() {
        // Empty has always meant "from the beginning of time"; garbage must not become a position,
        // because a guessed instant would silently mark news as read.
        assertEquals(InboxCursors.EMPTY, InboxCursors.decode(""))
        assertEquals(InboxCursors.EMPTY, InboxCursors.decode("   "))
        assertEquals(InboxCursors.EMPTY, InboxCursors.decode("not-a-stamp"))
        assertEquals(InboxCursors.EMPTY, InboxCursors.decode("k2;reply=nonsense;plan_comment="))
        assertTrue(InboxCursors.EMPTY.isBlank)
    }

    @Test
    fun anOldSingleInstantReadsAsEveryTabRead() {
        // The migration rule: a stored value from before the tabs were counted apart is one instant,
        // and it meant "the whole inbox was read up to here". Reading it as "nothing was read" would
        // light the dot with a year of old news the moment the app is updated.
        assertEquals(InboxCursors.of(ten), InboxCursors.decode(ten))
        assertEquals(
            "k2;reply=$ten;plan_comment=$ten;comment_heart=$ten;plan_heart=$ten",
            InboxCursors.decode(ten).encode(),
        )
    }

    @Test
    fun aPositionOnlyMovesForward() {
        val held = InboxCursors.of(ten)
        assertEquals(held, held.with(InboxKind.REPLY, nine), "an older instant moved a tab")
        assertEquals(held, held.with(InboxKind.REPLY, ""), "an empty instant moved a tab")
        assertEquals(held, held.with(InboxKind.REPLY, "k2"), "a bad instant moved a tab")
        assertEquals(eleven, held.with(InboxKind.REPLY, eleven)[InboxKind.REPLY])
    }

    @Test
    fun aTabWithNothingWaitingMovesToTheInstantCountedAt() {
        // The count came back 0 for every tab: they are all read, and the next count starts here.
        val next = InboxCursors.EMPTY.afterCounting(
            counts = InboxKind.entries.associateWith { 0 },
            served = emptyMap(),
            checkedAt = ten,
        )
        assertEquals(InboxCursors.of(ten), next)
    }

    @Test
    fun aTabWhoseRowsWereNotShownKeepsItsPosition() {
        // This is the whole bug: the replies arrived and were read, the hearts arrived and were not
        // shown. Moving the hearts' position here would clear their mark without them being read.
        val next = InboxCursors.EMPTY.afterCounting(
            counts = mapOf(InboxKind.REPLY to 1, InboxKind.COMMENT_HEART to 3),
            served = mapOf(InboxKind.REPLY to eleven),
            checkedAt = eleven,
        )
        assertEquals(eleven, next[InboxKind.REPLY], "the tab that was shown did not move")
        assertEquals("", next[InboxKind.COMMENT_HEART], "a tab nobody looked at was marked read")
        // No news in this one: there is nothing to be behind, so it moves to the instant counted at.
        assertEquals(eleven, next[InboxKind.PLAN_HEART])
    }

    @Test
    fun aTabWhoseRowsWereShownMovesToTheNewestRowShown() {
        // Rows served are rows on the screen: the tab is read up to the newest one of them, and the
        // ones below it are history, not news.
        val next = InboxCursors.EMPTY.afterCounting(
            counts = mapOf(InboxKind.PLAN_COMMENT to 5),
            served = mapOf(InboxKind.PLAN_COMMENT to ten),
            checkedAt = eleven,
        )
        assertEquals(ten, next[InboxKind.PLAN_COMMENT])
    }

    @Test
    fun anAnswerWithoutAnInstantMovesNothing() {
        // An old deployment stamps no `checked_at`. Counting from a guessed instant would put the
        // same number straight back on the next look, and the dot would blink.
        val next = InboxCursors.EMPTY.afterCounting(
            counts = InboxKind.entries.associateWith { 0 },
            served = emptyMap(),
            checkedAt = "",
        )
        assertEquals(InboxCursors.EMPTY, next)
    }

    @Test
    fun onlyTheTabsThatAdvancedAreWrittenBack() {
        val before = InboxCursors.of(ten)
        val after = before.with(InboxKind.REPLY, eleven)
        assertEquals(setOf(InboxKind.REPLY), after.movedFrom(before))
        // A tab that was already at the instant is not "moved", whatever else changed around it.
        assertEquals(emptySet<InboxKind>(), before.movedFrom(before))
    }

    @Test
    fun theSingleCursorMirrorIsTheNewestTab() {
        // For a deployment that only understands one cursor: the most recent thing read, so it never
        // reports news this install already saw.
        assertEquals(eleven, InboxCursors.of(ten).with(InboxKind.PLAN_HEART, eleven).latest)
        assertEquals("", InboxCursors.EMPTY.latest)
    }
}