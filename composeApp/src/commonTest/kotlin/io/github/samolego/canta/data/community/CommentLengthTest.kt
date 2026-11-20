package io.github.samolego.canta.data.community

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The comment length limit: the backend stores `left(body, 1000)`, so the composer has to know
 * the same number — otherwise a long comment goes up missing its ending and nothing says so.
 */
class CommentLengthTest {

    @Test
    fun aShortCommentHasRoomToSpare() {
        assertEquals(990, commentCharactersLeft(10))
    }

    @Test
    fun exactlyAtTheLimitNothingIsLeft() {
        assertEquals(0, commentCharactersLeft(COMMENT_MAX_LENGTH))
    }

    @Test
    fun pastTheLimitTheCountStopsAtZero() {
        // A paste can overshoot before the field trims it; the number beside it must not go negative.
        assertEquals(0, commentCharactersLeft(COMMENT_MAX_LENGTH + 37))
    }

    @Test
    fun theLimitMatchesWhatTheBackendKeeps() {
        // `plan_comment` inserts left(p_body, 1000); this is the same number, on purpose.
        assertEquals(1000, COMMENT_MAX_LENGTH)
    }
}