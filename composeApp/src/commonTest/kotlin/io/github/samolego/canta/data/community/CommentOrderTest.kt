package io.github.samolego.canta.data.community

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The three comment orders: newest, most hearted, most useful.
 *
 * The rule that matters is that none of them drops a comment — "useful" is a ranking, and a page
 * that hides the unpopular rows would be editing the community rather than sorting it.
 */
class CommentOrderTest {

    private fun comment(id: Long, likes: Int = 0, replyTo: Long = 0L) = PlanComment(
        author = "u-1",
        body = "body $id",
        createdAt = "2026-11-20T10:00:0${id}.000000Z",
        id = id,
        replyTo = replyTo,
        likes = likes,
    )

    private fun ids(threads: List<CommentThread>) = threads.map { it.comment.id }

    @Test
    fun newestIsTheIdDescending() {
        val list = listOf(comment(1), comment(3), comment(2)).threads()

        assertEquals(listOf(3L, 2L, 1L), ids(list.inOrder(CommentOrder.NEWEST)))
    }

    @Test
    fun heartedIsTheLikeCount() {
        val list = listOf(comment(1, likes = 0), comment(2, likes = 5), comment(3, likes = 2)).threads()

        assertEquals(listOf(2L, 3L, 1L), ids(list.inOrder(CommentOrder.HEARTED)))
    }

    @Test
    fun usefulCountsTheAnswersToo() {
        // The unhearted question with three answers beats the hearted one nobody answered.
        val list = listOf(
            comment(1, likes = 1),
            comment(2, likes = 0),
            comment(11, replyTo = 2),
            comment(12, replyTo = 2),
            comment(13, replyTo = 2),
        ).threads()

        assertEquals(listOf(2L, 1L), ids(list.inOrder(CommentOrder.USEFUL)))
    }

    @Test
    fun noOrderDropsAComment() {
        val list = listOf(
            comment(1),
            comment(2, likes = 3),
            comment(3, replyTo = 2),
        ).threads()

        CommentOrder.entries.forEach { order ->
            assertEquals(list.size, list.inOrder(order).size, "$order dropped a thread")
        }
    }

    @Test
    fun equalRowsKeepTheBackendOrder() {
        val list = listOf(comment(5), comment(4), comment(3)).threads()

        // All hearted zero: the order they arrived in is the tie-breaker, so nothing jumps around.
        assertEquals(listOf(5L, 4L, 3L), ids(list.inOrder(CommentOrder.HEARTED)))
    }

    @Test
    fun answersStayInsideTheirThread() {
        val list = listOf(
            comment(1, likes = 9),
            comment(2),
            comment(21, replyTo = 2),
        ).threads()

        val ordered = list.inOrder(CommentOrder.HEARTED)

        assertEquals(listOf(1L, 2L), ids(ordered))
        assertEquals(listOf(21L), ordered.last().replies.map { it.id })
    }
}