package io.github.samolego.canta.data.community

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Opening a page on one comment: what a mention or a reply in the inbox leads to.
 *
 * The thread is put first instead of scrolled to, so there is no index to get wrong — the row the
 * reader came for is simply the first thing under the title.
 */
class CommentAnchorTest {

    private fun comment(id: Long, replyTo: Long = 0L) = PlanComment(
        author = "u-1",
        body = "body $id",
        createdAt = "2026-11-20T10:00:00.000000Z",
        id = id,
        replyTo = replyTo,
    )

    private fun page() = listOf(
        comment(1),
        comment(2),
        comment(3),
        comment(21, replyTo = 2),
        comment(22, replyTo = 2),
    ).threads()

    @Test
    fun aStarterIsFoundByItsOwnId() {
        val found = assertNotNull(page().threadOf(2))
        assertEquals(2L, found.comment.id)
        assertEquals(2, found.replies.size)
    }

    @Test
    fun anAnswerIsFoundThroughItsStarter() {
        // The reader is led to the answer, but what opens is the thread it lives in: an answer alone
        // has no context to read.
        val found = assertNotNull(page().threadOf(21))
        assertEquals(2L, found.comment.id, "the answer's thread, not the answer itself")
    }

    @Test
    fun aCommentFromAnotherPageIsNotInvented() {
        assertNull(page().threadOf(999), "a stale inbox row must not open something made up")
        assertNull(page().threadOf(0), "no anchor is not an anchor")
    }

    @Test
    fun theAnchoredThreadComesFirst() {
        val order = page().withAnchorFirst(3).map { it.comment.id }
        assertEquals(3L, order.first())
        assertEquals(page().size, order.size, "nothing is dropped by pointing at one row")
    }

    @Test
    fun theRestOfThePageKeepsItsOrder() {
        val before = page().map { it.comment.id }
        val after = page().withAnchorFirst(3).map { it.comment.id }

        assertEquals(before.filterNot { it == 3L }, after.drop(1), "only one row moved")
    }

    @Test
    fun anAnchorThatIsNotThereChangesNothing() {
        assertEquals(page().map { it.comment.id }, page().withAnchorFirst(999).map { it.comment.id })
        assertEquals(page().map { it.comment.id }, page().withAnchorFirst(0).map { it.comment.id })
    }

    @Test
    fun anchoringAnAnswerBringsItsWholeThread() {
        val anchored = page().withAnchorFirst(21)

        assertEquals(2L, anchored.first().comment.id)
        assertTrue(anchored.first().replies.any { it.id == 21L }, "the answer rides with its thread")
    }
}