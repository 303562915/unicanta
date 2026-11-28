package io.github.samolego.canta.ui.fl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 应用列表的四格 tab 上双击是「清空搜索」：第二下只有落在同一格、而且离第一下够近，
 * 才算这一次双击。
 */
class RepeatTapTest {

    @Test
    fun theSameTabTappedTwiceQuicklyIsADoubleTap() {
        assertTrue(isRepeatTap(previousIndex = 2, index = 2, gapMs = 120))
        assertTrue(isRepeatTap(previousIndex = 0, index = 0, gapMs = 0))
        assertTrue(isRepeatTap(previousIndex = 3, index = 3, gapMs = RepeatTapWindowMs))
    }

    @Test
    fun twoTapsOnDifferentTabsAreJustTabSwitches() {
        assertFalse(isRepeatTap(previousIndex = 0, index = 1, gapMs = 80))
        assertFalse(isRepeatTap(previousIndex = 3, index = 2, gapMs = RepeatTapWindowMs))
    }

    @Test
    fun aSlowSecondTapIsNotADoubleTap() {
        assertFalse(isRepeatTap(previousIndex = 1, index = 1, gapMs = RepeatTapWindowMs + 1))
        assertFalse(isRepeatTap(previousIndex = 1, index = 1, gapMs = 5_000))
    }

    @Test
    fun theFirstTapCanNeverBeADoubleTap() {
        // 页面刚打开时没有「上一格」，随便给个负数也就不能匹配任何真实序号。
        assertFalse(isRepeatTap(previousIndex = -1, index = 0, gapMs = 10))
    }
}
