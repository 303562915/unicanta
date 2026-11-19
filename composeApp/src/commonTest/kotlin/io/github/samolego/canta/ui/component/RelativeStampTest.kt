@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.github.samolego.canta.ui.component

import io.github.samolego.canta.data.community.PlanComment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The way a comment list dates its rows: what is fresh reads as the time that passed since it
 * landed, what is old falls back to the plain stamp. The clock comes in as a parameter, so the
 * tests do not have to race the real one.
 */
class RelativeStampTest {

    private fun at(iso: String) = Instant.parse(iso)

    @Test
    fun aCommentUnderAMinuteOldJustHappened() {
        assertEquals(
            "刚刚",
            relativeStamp("2026-11-19T09:59:30Z", now = at("2026-11-19T10:00:00Z")),
        )
    }

    @Test
    fun aCommentFromTheFutureStillJustHappened() {
        // A device clock a little behind the server's must not print a negative count.
        assertEquals(
            "刚刚",
            relativeStamp("2026-11-19T10:05:00Z", now = at("2026-11-19T10:00:00Z")),
        )
    }

    @Test
    fun minutesAndHoursReadAsElapsed() {
        val now = at("2026-11-19T10:00:00Z")
        assertEquals("5分钟前", relativeStamp("2026-11-19T09:55:00Z", now = now))
        assertEquals("3小时前", relativeStamp("2026-11-19T07:00:00Z", now = now))
    }

    @Test
    fun daysAndMonthsReadAsElapsed() {
        val now = at("2026-11-19T10:00:00Z")
        assertEquals("2天前", relativeStamp("2026-11-17T10:00:00Z", now = now))
        assertEquals("1个月前", relativeStamp("2026-10-05T10:00:00Z", now = now))
    }

    @Test
    fun aYearOldCommentFallsBackToThePlainStamp() {
        assertEquals(
            "01-19 10:00",
            relativeStamp("2025-01-19T10:00:00Z", now = at("2026-11-19T10:00:00Z")),
        )
    }

    @Test
    fun anOffsetSignedTimestampIsParsedToo() {
        // PostgREST signs its UTC values "+00:00"; the parser wants "Z".
        assertEquals(
            "5分钟前",
            relativeStamp("2026-11-19T09:55:00+00:00", now = at("2026-11-19T10:00:00Z")),
        )
    }

    @Test
    fun anUnparsableStampFallsBackInsteadOfBlanking() {
        // The fallback hands the string to the plain stamp, which echoes what it cannot slice.
        assertEquals("bad-stamp", relativeStamp("bad-stamp"))
        assertEquals("", relativeStamp(""))
    }
}

/**
 * The broken heart puts a comment away under a key: the comment's own id when the server sent one,
 * otherwise a key folded from where the thread and the answer stand on the page — and never a key
 * that could match a first-level comment's.
 */
class CommentHideKeyTest {

    private fun comment(id: Long) = PlanComment(
        author = "小王",
        body = "x",
        createdAt = "2026-11-19T10:00:00Z",
        id = id,
    )

    @Test
    fun aCommentWithAnIdHidesUnderItsId() {
        assertEquals(42L, hideKey(comment(42), key = 7L))
        assertEquals(42L, replyHideKey(key = 7L, reply = comment(42), replyIndex = 3))
    }

    @Test
    fun aLegacyThreadHidesUnderItsThreadKey() {
        // An older backend sends no ids: threadKey of the first thread is -1.
        assertEquals(-1L, hideKey(comment(0), key = -1L))
    }

    @Test
    fun aLegacyAnswerNeverSharesAKeyWithItsThread() {
        // The folded key moves the answer far past the -1, -2, … range the threads use.
        val key = replyHideKey(key = -1L, reply = comment(0), replyIndex = 0)
        assertEquals(-4097L, key)
        assertEquals(-4098L, replyHideKey(key = -1L, reply = comment(0), replyIndex = 1))
        assertEquals(-8193L, replyHideKey(key = -2L, reply = comment(0), replyIndex = 0))
    }
}
