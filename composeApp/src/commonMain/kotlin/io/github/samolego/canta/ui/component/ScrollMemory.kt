package io.github.samolego.canta.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
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
 * 「这一轮运行里，这个键的盘上那份已经读过了」。
 *
 * 以前这里是 `claim(key): Boolean` —— 同一个键第二次问就一律回答「不要恢复」。本意是「从别的页面
 * 返回时落在顶部」，但判断依据是「这一轮运行里有没有恢复过」，而 Compose 在页面被导航回来、外层换了个
 * 头、或者列表所在的分支从「加载中」切成「有内容」时，都会重新走一遍这个 `remember`。于是：
 *
 *  * 第一屏读到了盘上那份，第二次读就是 null —— 冷启动正落到位置时，页面一动就跳回顶部；
 *  * 盘上一份都没有的时候，`remember` 里的实例才是唯一的位置来源，而它被 `remember` 键一改就没了。
 *
 * 现在只回答「盘上那份能不能用」，用不用它由 [rememberRememberedListState] 自己判断（见
 * `position`）：同一轮运行里优先用手上那个（真的在被滚动、位置一直是最新的）实例，盘上那份只负责
 * 冷启动的第一次落位。判据从「第几次问」变成「谁更准」，返回时才不会被顶回顶部，也不会跳。
 */
private object ScrollRestore {
    /** 这一轮运行里已经从盘上读过这个键了：只影响要不要再读文件，不影响位置。 */
    private val read = mutableSetOf<String>()

    /** 这个键还要不要去盘上找；同一次运行里第二次问就不必再读一遍文件。 */
    fun claim(key: String): Boolean = read.add(key)
}

/**
 * 带记忆的列表滚动状态。
 *
 * 位置有三个来源，优先级从高到低：
 *
 *  1. **盘上那份**（[ScreenCache]，`scroll:page`）：冷启动落回上次那一行；
 *  2. **这一轮运行里手上那个实例** [live]（放在 `remember` 里，跨组合重建存活）：从详情页返回、
 *     分支切换时位置就接在这里；
 *  3. 顶部（都没有）。
 *
 * 第 2 条是这一版新加的。以前只留「第 1 条 + 同一进程里 `remember` 出来的那个实例」，页面一被
 * 重新组合（导航返回是常态）实例就没了，位置清零，看起来正是「点进应用详情再返回，列表顺序就跳到
 * 下面去了」。
 *
 * @param page 这一屏的名字（`"apps"` / `"posts"` / `"messages"`…），落盘键由它生成。
 */
@Composable
fun rememberRememberedListState(page: String): LazyListState {
    val live = remember(page) { LazyListState() }
    val key = remember(page) { scrollCacheKey(page) }
    val saved = remember(key) {
        if (ScrollRestore.claim(key)) ScreenCache.get<ScrollMemory>(key) else null
    }
    // 盘上那份只管冷启动的第一次落位；手上那个只要真的被滚过，就以它为准（它永远是最新的）。
    val initial = live.position() ?: saved
    val state = rememberSavedListState(initial, live)
    guardIndex(initial, onCorrect = { last -> state.scrollToItem(last) }) {
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
    val live = remember(page) { LazyGridState() }
    val key = remember(page) { scrollCacheKey(page) }
    val saved = remember(key) {
        if (ScrollRestore.claim(key)) ScreenCache.get<ScrollMemory>(key) else null
    }
    val initial = live.position() ?: saved
    val state = rememberSavedGridState(initial, live)
    guardIndex(initial, onCorrect = { last -> state.scrollToItem(last) }) {
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
 * 手上这个实例现在停在哪；已经在顶部就算「没有位置」——那是每次进来都会落到的地方，不值得为了让
 * 界面对着它而把盘上那份（上次真的停过的那一行）丢掉。
 *
 * 定义在这里是为了和 [rememberRememberedListState] 的文档贴在一起：它解释的是「回退到哪一份位置」，
 * 而不是列表本身。
 */
private fun LazyListState.position(): ScrollMemory? =
    if (firstVisibleItemIndex <= 0 && firstVisibleItemScrollOffset <= 0) {
        null
    } else {
        ScrollMemory(firstVisibleItemIndex, firstVisibleItemScrollOffset)
    }

private fun LazyGridState.position(): ScrollMemory? =
    if (firstVisibleItemIndex <= 0 && firstVisibleItemScrollOffset <= 0) {
        null
    } else {
        ScrollMemory(firstVisibleItemIndex, firstVisibleItemScrollOffset)
    }

/**
 * 列表状态：能落位就落位，落不了就从顶部来。
 *
 * `remember` 键用 [saved] 本身（不是它的字段）：一屏有多份列表（不同范围、不同 tab）时，换的那一份
 * 往往带同一个位置值，用值当键会把两份列表的状态混成一个。
 */
@Composable
private fun rememberSavedListState(saved: ScrollMemory?, live: LazyListState): LazyListState = remember(saved) {
    if (saved == null) {
        live
    } else {
        LazyListState(saved.index, saved.offset)
    }
}

@Composable
private fun rememberSavedGridState(saved: ScrollMemory?, live: LazyGridState): LazyGridState = remember(saved) {
    if (saved == null) {
        live
    } else {
        LazyGridState(saved.index, saved.offset)
    }
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
