package io.github.samolego.canta.data.community

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A tapped heart writes its new state back into the page's list: the comment carrying the id is
 * replaced, everything else — including rows an older backend sent without an id — stays as it
 * was, because with no id there is no way to tell two rows apart.
 */
class CommentUpdatesTest {

    private fun comment(id: Long, likes: Int = 0, liked: Boolean = false) = PlanComment(
        author = "小王",
        body = "x",
        createdAt = "2026-11-19T10:00:00Z",
        id = id,
        likes = likes,
        liked = liked,
    )

    @Test
    fun theTappedCommentCarriesTheNewState() {
        val list = listOf(comment(1), comment(2, likes = 3))
        val updated = list.updatedComment(2) { it.copy(liked = true, likes = 4) }
        assertEquals(false, updated[0].liked)
        assertEquals(true, updated[1].liked)
        assertEquals(4, updated[1].likes)
    }

    @Test
    fun rowsWithoutAnIdAreNeverRewritten() {
        // A 0 must not match every id-less row the older backend sent: nothing matches it.
        val list = listOf(comment(0), comment(0))
        val updated = list.updatedComment(0) { it.copy(liked = true) }
        assertEquals(list, updated)
    }

    @Test
    fun anUnknownIdLeavesTheListAlone() {
        val list = listOf(comment(1))
        assertEquals(list, list.updatedComment(9) { it.copy(liked = true) })
    }
}
