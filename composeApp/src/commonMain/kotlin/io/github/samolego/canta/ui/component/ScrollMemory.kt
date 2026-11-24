package io.github.samolego.canta.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import io.github.samolego.canta.data.cache.ScreenCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.Serializable

/**
 * 「下次打开还停在原处」：把一屏滚到哪记在本地，回来时直接落到那一行。
 *
 * 为什么需要它：`rememberLazyListState` 只在**同一进程**里记住位置。App 被系统回收再打开、或者
 * 隔天冷启动，列表永远从头开始——用户说的「下次打开还在那」就是这件事。
 *
 * 记的是 [LazyListState.firstVisibleItemIndex] 与偏移，不是绝对像素：数据变了（少了几个包、
 * 多了几条帖）照样能落到大致那一行。
 *
 * 写入有节流：滚动时每帧都会产生新的位置，`conflate` + 一小段延迟把一串位置压成「停手之后的那一个」；
 * 停在顶部时干脆不写，省得每次进页面都覆盖一次。
 */
@Serializable
data class ScrollMemory(val index: Int = 0, val offset: Int = 0)

/** 停手多久算「就停在这」；比 [ScreenCache] 自己 800ms 的合并写入更短，避免滚完立刻退出时丢失。 */
private const val ScrollSaveDelayMillis = 500L

/** 一屏的滚动位置在 [ScreenCache] 里的键。各页面传自己的名字，别互相覆盖。 */
fun scrollCacheKey(page: String): String = "scroll:$page"

/**
 * 带记忆的列表滚动状态。
 *
 * @param page 这一屏的名字（`"apps"` / `"posts"` / `"messages"`…），落盘键由它生成。
 */
@Composable
fun rememberRememberedListState(page: String): LazyListState {
    val key = remember(page) { scrollCacheKey(page) }
    val saved = remember(key) { ScreenCache.get<ScrollMemory>(key) }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = saved?.index ?: 0,
        initialFirstVisibleItemScrollOffset = saved?.offset ?: 0,
    )
    LaunchedEffect(state, key) {
        snapshotFlow { ScrollMemory(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
            .distinctUntilChanged()
            .conflate()
            .collect { memory ->
                // 顶部不记：那是每次进来都会落到的地方，写它只是让上一次的位置白白丢掉。
                if (memory.index <= 0 && memory.offset <= 0) return@collect
                delay(ScrollSaveDelayMillis)
                ScreenCache.put(key, memory)
            }
    }
    return state
}

/** 网格版（应用列表那种自适应列）：同一套记忆，只是换成 [LazyGridState]。 */
@Composable
fun rememberRememberedGridState(page: String): LazyGridState {
    val key = remember(page) { scrollCacheKey(page) }
    val saved = remember(key) { ScreenCache.get<ScrollMemory>(key) }
    val state = rememberLazyGridState(
        initialFirstVisibleItemIndex = saved?.index ?: 0,
        initialFirstVisibleItemScrollOffset = saved?.offset ?: 0,
    )
    LaunchedEffect(state, key) {
        snapshotFlow { ScrollMemory(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
            .distinctUntilChanged()
            .conflate()
            .collect { memory ->
                if (memory.index <= 0 && memory.offset <= 0) return@collect
                delay(ScrollSaveDelayMillis)
                ScreenCache.put(key, memory)
            }
    }
    return state
}
