package io.github.samolego.canta.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf

/**
 * 「双击底栏当前那一格 → 整页回顶部」的信号源。
 *
 * 底栏把点击打在 [ping] 上，页面用 [ScrollToTopEffect] 听：计数本身没有含义，只有变化有意义。
 * 0 是初始值，而页面在 0 上什么都不做——首帧不会把用户上次停下的滚动位置顶掉。
 *
 * 用单例而不是 CompositionLocal：底栏和页面在 NavHost 的两头，中间隔着一层宿主；把信号挂在
 * 一个小的 Compose 状态上，两端读同一份就行，不必为此重排 `CantaApp` 的层级。
 */
object ScrollToTop {
    private val counter = mutableIntStateOf(0)

    /** 当前的信号值：在 composition 里读它，页面就订阅了「回顶部」。 */
    val tick: Int get() = counter.intValue

    /** 底栏双击：请求当前页回到顶部。 */
    fun ping() {
        counter.intValue++
    }
}

/** 收回顶部：列表不在顶上才滚，已经在顶上就什么也不做。 */
private fun shouldScroll(index: Int, offset: Int): Boolean = index > 0 || offset > 0

/**
 * 这一页的列表跟着底栏的双击回到顶部。
 *
 * 只滚「其实不在顶部」的列表：不然一次双击会让所有还活着的页面（NavHost 里留在栈上的那些）
 * 各跑一遍没用的动画。
 */
@Composable
fun ScrollToTopEffect(state: LazyListState) {
    val tick = ScrollToTop.tick
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        if (shouldScroll(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)) {
            state.animateScrollToItem(0)
        }
    }
}

/** [ScrollToTopEffect] 的网格版：应用列表和推荐页都是格子。 */
@Composable
fun ScrollToTopEffect(state: LazyGridState) {
    val tick = ScrollToTop.tick
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        if (shouldScroll(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)) {
            state.animateScrollToItem(0)
        }
    }
}

/** [ScrollToTopEffect] 的普通滚动版：成员页这类整页 `verticalScroll` 的页面用这个。 */
@Composable
fun ScrollToTopEffect(state: ScrollState) {
    val tick = ScrollToTop.tick
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        if (state.value > 0) state.animateScrollTo(0)
    }
}
