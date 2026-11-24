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
import kotlinx.coroutines.flow.first
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
 * 两件事必须一起做，少一件就会出难看的画面：
 *
 *  1. **落位要合法**（[guardIndex]）：数据比记忆的短时（包卸掉了、帖子删了），越界的 index 会让
 *     列表停在没有任何 item 的地方——看上去就是一整片空白，只有底栏浮在上面。
 *  2. **写入要有节流**：滚动时每帧都会产生新的位置，`conflate` + 一小段延迟把一串压成「停手之后
 *     的那一个」；停在顶部时干脆不写，省得每次进页面都覆盖一次。
 */
@Serializable
data class ScrollMemory(val index: Int = 0, val offset: Int = 0)

/** 停手多久算「就停在这」；比 [ScreenCache] 自己 800ms 的合并写入更短，避免滚完立刻退出时丢失。 */
private const val ScrollSaveDelayMillis = 500L

/** 一屏的滚动位置在 [ScreenCache] 里的键。各页面传自己的名字，别互相覆盖。 */
fun scrollCacheKey(page: String): String = "scroll:$page"

/**
 * 「这一轮运行里，这一页的位置恢复过一次了」。
 *
 * 分两个场景，两者的正确行为不一样：
 *
 *  * **冷启动 / App 被杀后再打开**：要「上次看到哪就从哪开始」——这是用户要的那个缓存感。
 *  * **只是去别的页面又返回来**：人刚刚就在这一页上，回来应该落在顶部，而不是突然出现在中间；
 *    恢复成上一次的位置反而像是「点错了」。
 *
 * 两件事在 Compose 里长得一模一样（都是一个新的 Composable 实例），唯一的区别是「这一轮运行里
 * 有没有恢复过」：第一次恢复、之后一律给 null（从顶部开始），而滚动中照样写盘，下个冷启动还是对的。
 */
private object ScrollRestore {
    private val restored = mutableSetOf<String>()

    /** 这个 key 现在能不能恢复；同一轮运行里第二次问就是 false。 */
    fun claim(key: String): Boolean = restored.add(key)
}

/**
 * 带记忆的列表滚动状态。
 *
 * @param page 这一屏的名字（`"apps"` / `"posts"` / `"messages"`…），落盘键由它生成。
 */
@Composable
fun rememberRememberedListState(page: String): LazyListState {
    val key = remember(page) { scrollCacheKey(page) }
    val saved = remember(key) { if (ScrollRestore.claim(key)) ScreenCache.get<ScrollMemory>(key) else null }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = saved?.index ?: 0,
        initialFirstVisibleItemScrollOffset = saved?.offset ?: 0,
    )
    guardIndex(saved, onCorrect = { last -> state.scrollToItem(last) }) {
        state.layoutInfo.totalItemsCount
    }
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
    val saved = remember(key) { if (ScrollRestore.claim(key)) ScreenCache.get<ScrollMemory>(key) else null }
    val state = rememberLazyGridState(
        initialFirstVisibleItemIndex = saved?.index ?: 0,
        initialFirstVisibleItemScrollOffset = saved?.offset ?: 0,
    )
    guardIndex(saved, onCorrect = { last -> state.scrollToItem(last) }) {
        state.layoutInfo.totalItemsCount
    }
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

/**
 * 越界修正：记忆里的位置比现在这份数据长时，落到最后一项。
 *
 * 不修的话表现是「从别的页面回来，整屏空白」——`LazyListState` 带着一个没有对应 item 的
 * [initialFirstVisibleItemIndex] 组合，在数据补齐之前不画任何东西，而包列表、帖子列表本来就
 * 会因为卸载/发帖/删帖变短。
 *
 * 等到列表真的测量出项数再判断（首帧 `totalItemsCount` 还是 0），否则会把「还没开始画」当成
 * 「数据是空的」。
 */
@Composable
private fun guardIndex(
    saved: ScrollMemory?,
    onCorrect: suspend (lastIndex: Int) -> Unit,
    totalItems: () -> Int,
) {
    val memory = saved ?: return
    LaunchedEffect(memory) {
        val total = snapshotFlow { totalItems() }.first { it > 0 }
        if (memory.index >= total) onCorrect(total - 1)
    }
}
