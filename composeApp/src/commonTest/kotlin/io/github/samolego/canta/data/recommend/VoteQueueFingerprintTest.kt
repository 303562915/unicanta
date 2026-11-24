package io.github.samolego.canta.data.recommend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the red dot on the recommendations page.
 *
 * 规则两条，缺一条它就变成常亮：
 *  1. **只有上榜是消息**，票数怎么动都不算；
 *  2. **只有与我有关的上榜算消息**——我提的那条，或我投过票的那条。别人提、别人投的东西
 *     进精选，与一个陌生人发的广告没有区别。
 */
class VoteQueueFingerprintTest {

    /** 这台手机的身份：测试里统一用它。 */
    private val me = "42"

    private fun app(
        id: Long,
        agrees: Int,
        disagrees: Int = 0,
        applied: Boolean = false,
        authorId: String = "999",
        mine: Boolean? = null,
    ) = SuggestedApp(
        id = id,
        name = "App $id",
        url = "https://github.com/owner/app$id",
        agrees = agrees,
        disagrees = disagrees,
        applied = applied,
        authorId = authorId,
        mine = mine,
    )

    private fun seen(vararg apps: SuggestedApp) = appliedQueueFingerprint(apps.toList(), me)

    @Test
    fun theSameQueueIsTheSameFingerprint() {
        val queue = listOf(app(1, 45, 1, applied = true, authorId = me), app(2, 3, 0))
        assertFalse(hasNewApplied(appliedQueueFingerprint(queue, me), queue, me), "没变就不是消息")
    }

    @Test
    fun theFingerprintDoesNotDependOnTheOrderTheBackendSends() {
        val first = listOf(app(2, 3, 0), app(1, 45, 1, applied = true, authorId = me))
        val second = listOf(app(1, 45, 1, applied = true, authorId = me), app(2, 3, 0))
        assertEquals(appliedQueueFingerprint(first, me), appliedQueueFingerprint(second, me))
    }

    @Test
    fun votesCastByOtherPeopleAreNotNews() {
        val before = seen(app(1, 2, 1), app(2, 3, 0))
        assertFalse(hasNewApplied(before, listOf(app(1, 3, 1), app(2, 3, 0)), me), "别人投了认同")
        assertFalse(hasNewApplied(before, listOf(app(1, 2, 2), app(2, 3, 0)), me), "别人投了不认同")
        assertFalse(hasNewApplied(before, listOf(app(1, 1, 1), app(2, 3, 0)), me), "别人撤了票")
        assertFalse(hasNewApplied(before, listOf(app(1, 44, 0), app(2, 60, 2)), me), "票数动了很多，但没人上榜")
    }

    @Test
    fun aNewEntryThatHasNotClearedTheBarIsNotNews() {
        val before = seen(app(1, 2, 1))
        assertFalse(hasNewApplied(before, listOf(app(1, 2, 1), app(2, 1, 0)), me), "有人提了新东西，还没上榜")
    }

    @Test
    fun anEntryIProposedGoingUpIsNews() {
        val before = seen(app(1, 2, 1, authorId = me))
        val now = listOf(app(1, 45, 1, applied = true, authorId = me))
        assertTrue(hasNewApplied(before, now, me), "我提的那条上榜了")
        assertEquals(1, newAppliedSince(before, now, me))
    }

    @Test
    fun anEntryIVotedOnGoingUpIsNews() {
        // 我投过票（mine 非空，不论投的是认同还是不认同）就说明我参与过，它上榜值得说一声。
        val before = seen(app(1, 2, 1, mine = true))
        val now = listOf(app(1, 45, 1, applied = true, mine = true))
        assertTrue(hasNewApplied(before, now, me))
        val notMine = seen(app(2, 2, 1, mine = null))
        assertFalse(
            hasNewApplied(notMine, listOf(app(2, 45, 1, applied = true, mine = null)), me),
            "没参与过的上榜不算",
        )
    }

    @Test
    fun somebodyElsesEntryGoingUpIsNotNews() {
        // 这次改动的重点：别人提、我也没投过的条目进精选，不亮。
        val before = seen(app(1, 2, 1, authorId = "999", mine = null))
        val now = listOf(app(1, 45, 1, applied = true, authorId = "999", mine = null))
        assertFalse(hasNewApplied(before, now, me), "与陌生人无关的一个点上榜，不要叫我")
        assertEquals(0, newAppliedSince(before, now, me))
    }

    @Test
    fun fallingOffTheListIsNotNews() {
        val before = seen(app(1, 45, 1, applied = true, authorId = me))
        assertFalse(hasNewApplied(before, listOf(app(1, 20, 9, authorId = me)), me), "掉榜不是有事发生")
        assertEquals(0, newAppliedSince(before, listOf(app(1, 20, 9, authorId = me)), me))
    }

    @Test
    fun neverHavingLookedIsNotANotification() {
        assertFalse(hasNewApplied("", listOf(app(1, 45, 1, applied = true, authorId = me)), me))
        assertEquals(0, newAppliedSince("", listOf(app(1, 45, 1, applied = true, authorId = me)), me))
    }

    @Test
    fun anEmptyQueueThatWasLookedAtLightsUpWhenSomethingOfMineGoesUp() {
        val before = appliedQueueFingerprint(emptyList(), me)
        assertEquals("applied:", before, "空队列也有一条可比较的状态")
        assertTrue(hasNewApplied(before, listOf(app(1, 45, 1, applied = true, authorId = me)), me))
        assertEquals(0, newAppliedSince(before, emptyList(), me))
    }

    @Test
    fun aStoredFingerprintFromTheOldAppIsNotNews() {
        assertEquals(0, newAppliedSince("", listOf(app(1, 45, 1, applied = true, authorId = me)), me))
        assertEquals(0, newAppliedSince("empty", listOf(app(1, 45, 1, applied = true, authorId = me)), me))
        assertEquals(0, newAppliedSince("1:44:1;2:3:0", listOf(app(1, 45, 1, applied = true, authorId = me)), me))
        assertFalse(hasNewApplied("1:44:1;2:3:0", listOf(app(2, 45, 1, applied = true, authorId = me)), me))
    }

    @Test
    fun theCountIsHowManyOfMineWentUp() {
        val before = appliedQueueFingerprint(emptyList(), me)
        assertEquals(0, newAppliedSince(before, listOf(app(1, 2, 1)), me))
        assertEquals(1, newAppliedSince(before, listOf(app(1, 45, 1, applied = true, authorId = me)), me))
        assertEquals(
            2,
            newAppliedSince(
                before,
                listOf(
                    app(1, 45, 1, applied = true, authorId = me),
                    app(2, 50, 2, applied = true, mine = false),
                    app(3, 60, 3, applied = true, authorId = "999", mine = null),
                ),
            ),
            "两条与我有关，第三条是陌生人的",
        )
    }

    @Test
    fun withoutAnIdentityOnlyVotedEntriesCount() {
        // 认不出身份（token 空）时，viewer 是空串：提不了也投不了，只有历史投票记录算参与。
        val noIdentity = ""
        assertEquals(0, newAppliedSince("applied:", listOf(app(1, 45, 1, applied = true, authorId = "")), noIdentity))
        assertEquals(1, newAppliedSince("applied:", listOf(app(1, 45, 1, applied = true, mine = true)), noIdentity))
    }
}
