package io.github.samolego.canta.data.recommend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the red dot on the recommendations tab: it has to light up for a vote that arrived and
 * stay dark for a page that has already been read, including after a restart.
 */
class VoteQueueFingerprintTest {

    private fun app(id: Long, agrees: Int, disagrees: Int = 0) = SuggestedApp(
        id = id,
        name = "App $id",
        url = "https://github.com/owner/app$id",
        agrees = agrees,
        disagrees = disagrees,
    )

    @Test
    fun theSameQueueIsTheSameFingerprint() {
        val queue = listOf(app(1, 45, 1), app(2, 3, 0))
        assertFalse(hasNewVotes(voteQueueFingerprint(queue), queue), "a queue that has not moved is not news")
    }

    @Test
    fun theFingerprintDoesNotDependOnTheOrderTheBackendSends() {
        val first = listOf(app(2, 3, 0), app(1, 45, 1))
        val second = listOf(app(1, 45, 1), app(2, 3, 0))
        assertTrue(voteQueueFingerprint(first) == voteQueueFingerprint(second))
    }

    @Test
    fun aVoteOnEitherSideIsNews() {
        val seen = voteQueueFingerprint(listOf(app(1, 2, 1)))
        assertTrue(hasNewVotes(seen, listOf(app(1, 3, 1))), "an agreeing vote arrived")
        assertTrue(hasNewVotes(seen, listOf(app(1, 2, 2))), "a disagreeing vote arrived")
        assertTrue(hasNewVotes(seen, listOf(app(1, 5, 1), app(2, 1, 0))), "a new entry arrived")
        assertTrue(hasNewVotes(seen, listOf(app(1, 1, 1))), "a vote was withdrawn")
    }

    @Test
    fun neverHavingLookedIsNotANotification() {
        // The first visit stores the fingerprint instead of lighting a dot: a fresh install that
        // has never opened the queue is not being told something changed.
        assertFalse(hasNewVotes("", listOf(app(1, 45, 1))))
    }

    @Test
    fun anEmptyQueueThatFallsBackToEmptyStaysQuiet() {
        val seen = voteQueueFingerprint(emptyList())
        assertFalse(hasNewVotes(seen, emptyList()))
        assertTrue(hasNewVotes(seen, listOf(app(1, 1, 0))))
    }

    @Test
    fun theCountIsHowManyVotesArrived() {
        val seen = voteQueueFingerprint(listOf(app(1, 2, 1), app(2, 3, 0)))
        assertEquals(0, newVotesSince(seen, listOf(app(1, 2, 1), app(2, 3, 0))), "a queue that has not moved")
        assertEquals(1, newVotesSince(seen, listOf(app(1, 3, 1), app(2, 3, 0))), "one agreeing vote")
        assertEquals(2, newVotesSince(seen, listOf(app(1, 2, 3), app(2, 3, 0))), "two disagreeing votes")
        assertEquals(3, newVotesSince(seen, listOf(app(1, 5, 1), app(2, 3, 0))), "three votes on one entry")
    }

    @Test
    fun aBrandNewEntryBringsItsVotesWithIt() {
        val seen = voteQueueFingerprint(listOf(app(1, 2, 1)))
        assertEquals(4, newVotesSince(seen, listOf(app(1, 2, 1), app(2, 3, 1))))
    }

    @Test
    fun newsThatIsNotANewVoteCountsZero() {
        // A withdrawal, or a flip that keeps the total: the dot still lights up, but the marker
        // has no number to put there, so the caller keeps the plain dot.
        val seen = voteQueueFingerprint(listOf(app(1, 2, 1)))
        assertEquals(0, newVotesSince(seen, listOf(app(1, 1, 1))))
        assertEquals(0, newVotesSince(seen, listOf(app(1, 3, 0))))
        assertEquals(0, newVotesSince("", listOf(app(1, 45, 1))), "never having looked is not news")
    }
}
