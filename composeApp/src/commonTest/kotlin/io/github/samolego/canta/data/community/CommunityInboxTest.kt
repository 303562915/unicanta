package io.github.samolego.canta.data.community

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The red dot is "a reply arrived after the last one this install opened". These are the two
 * questions behind it: which replies are new, and what to remember once they have been read.
 */
class CommunityInboxTest {

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
}
