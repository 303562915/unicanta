package io.github.samolego.canta.ui.component

import io.github.samolego.canta.data.community.PlanComment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The "作者" badge is decided on the identity number, not on the name: two people may pick the same
 * name, and one person may rename themselves between the upload and the comment.
 */
class PlanAuthorBadgeTest {

    private fun comment(author: String, id: String, commentId: Long = 1L) =
        PlanComment(author = author, body = "x", createdAt = "2026-11-19T10:00:00Z", id = commentId, authorId = id)

    @Test
    fun matchesTheUploadersNumber() {
        assertTrue(isPlanAuthor(comment("小王", "10000"), authorId = "10000", authorName = "#10000"))
    }

    @Test
    fun doesNotMatchAnotherNumberWithTheSameName() {
        // Same display name, different person: the number is what separates them.
        assertFalse(isPlanAuthor(comment("小王", "20000"), authorId = "10000", authorName = "#10000"))
    }

    @Test
    fun fallsBackToTheNameOnRowsWithoutANumber() {
        // A comment carried over from the old plan_comments table has no number of its own.
        assertTrue(isPlanAuthor(comment("#10000", ""), authorId = "10000", authorName = "#10000"))
        assertFalse(isPlanAuthor(comment("#20000", ""), authorId = "10000", authorName = "#10000"))
    }

    @Test
    fun marksNobodyWhenThereIsNoAuthorToMark() {
        // An app page has no uploader: every comment is just a comment.
        assertFalse(isPlanAuthor(comment("小王", "10000"), authorId = "", authorName = ""))
    }

    @Test
    fun keysAThreadByItsIdWhenTheBackendSendsOne() {
        assertEquals(42L, threadKey(comment("小王", "10000", commentId = 42L), index = 3))
    }

    @Test
    fun keysAThreadByItsPositionOnAnOldBackend() {
        // Without ids two threads would share a key and open together.
        assertEquals(-1L, threadKey(comment("小王", "", commentId = 0L), index = 0))
        assertEquals(-2L, threadKey(comment("小王", "", commentId = 0L), index = 1))
    }
}
