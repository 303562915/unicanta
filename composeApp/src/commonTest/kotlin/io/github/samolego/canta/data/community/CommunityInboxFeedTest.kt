package io.github.samolego.canta.data.community

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The inbox is one stream of four kinds of news, read a page at a time.
 *
 * These are the two questions the page cannot answer for itself: whether a page it received means
 * "ask for the next one", and which rows on it the reader has not seen before. Both have to stay
 * right when the backend hands back fewer rows than the page size, which is the case that makes a
 * naive implementation loop forever or claim there is more history than there is.
 */
class CommunityInboxFeedTest {

    private fun item(kind: InboxKind, id: Long, likes: Int = 0) = InboxItem(
        kind = kind,
        id = id,
        target = "7",
        body = "x",
        createdAt = "2026-11-20T10:00:00Z",
        likes = likes,
    )

    @Test
    fun aFullSourceMeansThereIsAnotherPage() {
        // Ten rows from each of four sources, taken per_source = 10: every source filled its
        // window, so any of them may have history left.
        val page = List(10) { item(InboxKind.REPLY, it) } +
            List(10) { item(InboxKind.COMMENT_HEART, it, likes = 2) }
        assertTrue(pageHasMore(page, perSource = 10))
    }

    @Test
    fun aShortSourceAndAnEmptyOneDoNotCountAsMore() {
        // Three replies and nothing else: the reply source ran out at three, so a next offset would
        // hand back the same rows again.
        val page = List(3) { item(InboxKind.REPLY, it) }
        assertFalse(pageHasMore(page, perSource = 10))
    }

    @Test
    fun oneExhaustedSourceDoesNotHideAWholeOne() {
        // Replies ran out at two, hearts filled their window: stopping here would silently drop the
        // rest of the hearts, which is why "more" is per source rather than per page.
        val page = List(2) { item(InboxKind.REPLY, it) } +
            List(10) { item(InboxKind.COMMENT_HEART, it, likes = 1) }
        assertTrue(pageHasMore(page, perSource = 10))
    }

    @Test
    fun anEmptyPageEndsTheList() {
        assertFalse(pageHasMore(emptyList(), perSource = 10))
    }

    @Test
    fun newRowsAreTheCommentShapedOnesAfterTheMark() {
        val page = listOf(
            item(InboxKind.REPLY, 12),
            item(InboxKind.PLAN_COMMENT, 9),
            item(InboxKind.COMMENT_HEART, 8, likes = 3),
            item(InboxKind.PLAN_HEART, 5, likes = 2),
        )
        val fresh = page.newSince(lastSeenId = 10)
        assertEquals(setOf(page[0]), fresh)
    }

    @Test
    fun aHeartIsNeverMarkedNewByAnIdItDoesNotShare() {
        // A plan heart carries a plan id, which is not a comment id and must not be compared with
        // one: plan 500 being bigger than the mark says nothing about the reader having seen it.
        val heart = item(InboxKind.PLAN_HEART, 500, likes = 4)
        assertTrue(listOf(heart).newSince(lastSeenId = 10).isEmpty())
    }

    @Test
    fun theNewestCommentIdIgnoresPlanHearts() {
        val page = listOf(
            item(InboxKind.REPLY, 12),
            item(InboxKind.PLAN_HEART, 900, likes = 2),
        )
        assertEquals(12L, newestInboxCommentId(page))
    }

    @Test
    fun aSummaryAddsUpEveryKindOfNews() {
        val summary = InboxSummary(
            replies = 2,
            planComments = 1,
            commentHearts = 4,
            planHearts = 3,
        )
        assertEquals(10, summary.total)
        assertEquals(0, InboxSummary.EMPTY.total)
    }

    @Test
    fun anUnknownSourceReadsAsAReply() {
        // A backend that grows a fifth kind later must still render on an older app rather than
        // vanish: the fallback is the kind that was there first.
        assertEquals(InboxKind.REPLY, InboxKind.of("something_new"))
        assertEquals(InboxKind.PLAN_HEART, InboxKind.of("plan_heart"))
    }
}
