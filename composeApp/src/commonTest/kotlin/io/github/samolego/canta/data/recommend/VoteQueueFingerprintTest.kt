package io.github.samolego.canta.data.recommend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the red dot on the recommendations page.
 *
 * 规则只有一条：**只有上榜是消息**。别人投一票和这一页的读者没关系——那条东西既不是他投的，
 * 也还没上榜，为它亮一个点等于把红点变成常亮。这一组测试就是钉住这条线：票数怎么变都不亮，
 * 有人进了精选才亮，掉榜不算，老格式的指纹不当新闻。
 */
class VoteQueueFingerprintTest {

    private fun app(id: Long, agrees: Int, disagrees: Int = 0, applied: Boolean = false) = SuggestedApp(
        id = id,
        name = "App $id",
        url = "https://github.com/owner/app$id",
        agrees = agrees,
        disagrees = disagrees,
        applied = applied,
    )

    @Test
    fun theSameQueueIsTheSameFingerprint() {
        val queue = listOf(app(1, 45, 1, applied = true), app(2, 3, 0))
        assertFalse(hasNewApplied(appliedQueueFingerprint(queue), queue), "a queue that has not moved is not news")
    }

    @Test
    fun theFingerprintDoesNotDependOnTheOrderTheBackendSends() {
        val first = listOf(app(2, 3, 0), app(1, 45, 1, applied = true))
        val second = listOf(app(1, 45, 1, applied = true), app(2, 3, 0))
        assertEquals(appliedQueueFingerprint(first), appliedQueueFingerprint(second))
    }

    @Test
    fun votesCastByOtherPeopleAreNotNews() {
        // 这是这次改动的全部理由：草原上的每一票都不该冒红点。
        val seen = appliedQueueFingerprint(listOf(app(1, 2, 1), app(2, 3, 0)))
        assertFalse(hasNewApplied(seen, listOf(app(1, 3, 1), app(2, 3, 0))), "an agreeing vote arrived")
        assertFalse(hasNewApplied(seen, listOf(app(1, 2, 2), app(2, 3, 0))), "a disagreeing vote arrived")
        assertFalse(hasNewApplied(seen, listOf(app(1, 1, 1), app(2, 3, 0))), "a vote was withdrawn")
        assertFalse(hasNewApplied(seen, listOf(app(1, 44, 0), app(2, 60, 2))), "counts moved a lot, nobody上榜")
        assertEquals(0, newAppliedSince(seen, listOf(app(1, 44, 0), app(2, 60, 2))))
    }

    @Test
    fun aNewEntryThatHasNotClearedTheBarIsNotNews() {
        val seen = appliedQueueFingerprint(listOf(app(1, 2, 1)))
        assertFalse(hasNewApplied(seen, listOf(app(1, 2, 1), app(2, 1, 0))), "a pending entry arrived")
    }

    @Test
    fun goingUpIsNews() {
        val seen = appliedQueueFingerprint(listOf(app(1, 2, 1), app(2, 3, 0)))
        assertTrue(hasNewApplied(seen, listOf(app(1, 45, 1, applied = true), app(2, 3, 0))))
        assertEquals(1, newAppliedSince(seen, listOf(app(1, 45, 1, applied = true), app(2, 3, 0))))
        assertEquals(
            2,
            newAppliedSince(
                seen,
                listOf(app(1, 45, 1, applied = true), app(2, 40, 1, applied = true)),
            ),
        )
    }

    @Test
    fun fallingOffTheListIsNotNews() {
        // 掉榜要不要提醒？不要：这不是"有事发生"，是"那条又不够数了"，点一下只会白跑一趟。
        val seen = appliedQueueFingerprint(listOf(app(1, 45, 1, applied = true)))
        assertFalse(hasNewApplied(seen, listOf(app(1, 20, 9))), "the entry lost the mark")
        assertEquals(0, newAppliedSince(seen, listOf(app(1, 20, 9))))
    }

    @Test
    fun neverHavingLookedIsNotANotification() {
        // 从没打开过投票那一格（存的是空串）不亮点：新装的 App 不该一开就背一条"历史全是新消息"。
        assertFalse(hasNewApplied("", listOf(app(1, 45, 1, applied = true))), "nothing seen yet, nothing to page")
        assertEquals(0, newAppliedSince("", listOf(app(1, 45, 1, applied = true))))
    }

    @Test
    fun anEmptyQueueThatWasLookedAtLightsUpWhenSomethingGoesUp() {
        // 看过、而且当时一条上榜的都没有，也是一条可比较的状态：之后有人上榜就该亮。
        val seen = appliedQueueFingerprint(emptyList())
        assertEquals("applied:", seen, "an empty queue still has a fingerprint")
        assertTrue(hasNewApplied(seen, listOf(app(1, 45, 1, applied = true))))
        assertEquals(1, newAppliedSince(seen, listOf(app(1, 45, 1, applied = true))))
        assertEquals(0, newAppliedSince(seen, emptyList()))
    }

    @Test
    fun aStoredFingerprintFromTheOldAppIsNotNews() {
        // 升级前的指纹是 `id:认同:不认同` 或 `empty`，对不上新格式，一律当没看过。
        assertEquals(0, newAppliedSince("", listOf(app(1, 45, 1, applied = true))))
        assertEquals(0, newAppliedSince("empty", listOf(app(1, 45, 1, applied = true))))
        assertEquals(0, newAppliedSince("1:44:1;2:3:0", listOf(app(1, 45, 1, applied = true))))
        assertFalse(hasNewApplied("1:44:1;2:3:0", listOf(app(2, 45, 1, applied = true))))
    }

    @Test
    fun theCountIsHowManyEntriesWentUp() {
        val seen = appliedQueueFingerprint(emptyList())
        assertEquals(0, newAppliedSince(seen, listOf(app(1, 2, 1))))
        assertEquals(1, newAppliedSince(seen, listOf(app(1, 45, 1, applied = true))))
        assertEquals(
            2,
            newAppliedSince(seen, listOf(app(1, 45, 1, applied = true), app(2, 50, 2, applied = true))),
        )
        assertEquals(0, newAppliedSince(seen, emptyList()))
    }
}
