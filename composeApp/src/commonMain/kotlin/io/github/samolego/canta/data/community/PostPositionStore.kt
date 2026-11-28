package io.github.samolego.canta.data.community

import io.github.samolego.canta.data.AppBlobStorage
import io.github.samolego.canta.util.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 「这条帖子我上次读到哪一屏」——写在本机，不进后端。
 *
 * 与 [PostReadStore]（读了没有 / 收藏了没有）、[PostDraftStore]（手上那半篇）同一套骨架
 * （`PostReadStore.kt:25-63`）：[AppBlobStorage] 里一份 `@Serializable` 的 blob、[loaded] 懒解码
 * 一次就留内存、写入丢到后台 [scope]、有上限。理由也一样 —— 这是**这台手机上的这个人**的读法，
 * 不是帖子的公共属性，后端没有它的位置。
 *
 * ## 为什么记 `(item, offset)` 而不是「滚到第几楼」
 *
 * 详情页那一列 `LazyColumn` 的 item 序列是
 * `thread-header` / `op` / `thread-head` / `c-<id>`… / `related-*` / `thread-foot`
 * （见 `PostsSection.kt` 里 `FLOORS_FIRST` 那段注释）。同一个"第 N 楼"在不同时候对应不同的
 * **像素位置**：长正文折没折、回复历史展开没展开、主楼折叠没折叠、上一条回帖有多长 —— 任何一个
 * 变了，第 N 楼就跑到别处去了。反过来，条目之间的相对位置只有 LazyColumn 自己那套坐标系是稳的：
 * `firstVisibleItemIndex` + `firstVisibleItemScrollOffset` 就是"这一屏从第几个 item 的哪个高度开始"，
 * 恢复时 [androidx.compose.foundation.lazy.LazyListState.scrollToItem] 吃的是同一种数。
 *
 * 而"第几楼"这个说法在这个列表里**根本不闭合**：header / op（主楼）/ 续读条所在的那个 `thread-head`
 * / 结尾的相关帖子与脚注都是不定项，楼层前面有几格随时可能变（`FLOORS_FIRST` 这个名字本身
 * 就写着"会变"）。拿楼层号当坐标，等于让存与读各自再套一次那个换算，改一处就两边对不上。
 *
 * ## 为什么上限是 [PositionLimit] 条
 *
 * 与 [PostReadStore] 的 `ReadLimit` / `SavedLimit` 同一种纪律：盘上这一份不能无限长。200 条正好
 * 盖住"这个人在翻的那一批帖子"——帖子详情是**一次只读一条**的地方，阅后即走，200 条以外的记录
 * 丢掉不算损失（下次进来从头看，第一屏本来就是主楼）。记录是变长的（帖子越长，`item` 越大），
 * 所以上限比已读那 400 条小一点。超出时按 [Entry.at] 淘汰最旧的一条：最近读的那批最可能被再读。
 *
 * ## 为什么不"每次滚动都写盘"
 *
 * 滚动是每帧都在变的量：一次滑两秒、60 fps 就是一百多次状态变化。逐次写盘等于一百多次 JSON
 * 编码 + 文件写，全在后台也照样耗电、耗 IO，而且除了最后一个位置，前一百多个都没人要。
 * 所以写入方（`PostsSection.kt` 里那条 `snapshotFlow` + 400 ms 防抖）**手停下来**才调 [remember]，
 * 这个类自己也再挡一层：位置没变就直接返回，不编码、不写盘（同 [PostReadStore.markRead] 里
 * 「数字一模一样就什么都不用做」那条早退）。本仓同一套节流在应用列表那边已经用过
 * （`ui/component/ScrollMemory.kt:71-81`：`snapshotFlow` + `conflate` + 500 ms 延迟，只有"停手之后
 * 的那一个"落到盘上），这边只是按"一次只读一条帖"的节奏把延迟收到 400 ms。
 *
 * ## 为什么关帖不清这条记录
 *
 * 这条记录存在的唯一目的就是"关掉再进来回到原处"。关帖时清掉，等于这个功能只对"没关过"的帖子
 * 有效。换帖也不需要清：读的时候按帖子 id 取，写的时候按帖子 id 覆盖，200 条上限兜底。
 * 真要清只有一种情况 —— 帖子被删了、楼层变少，那时恢复方按"越界就当没有记录"处理（见
 * `PostsSection.kt` 里恢复那段的注释），记录本身留着也无害。
 */
class PostPositionStore(private val storage: AppBlobStorage) {

    /**
     * 一行的形状：帖子 id + item 序号 + 像素偏移 + 时刻。
     *
     * 用 `List` 而不是 `Map<Long, …>`：`Map` 的 `Long` 键在 JSON 里只能变成字符串，解码回来还得
     * 再转一次（[PostReadStore] 里 `Entry` + `List` 是同一个理由）。
     */
    @Serializable
    private data class Entry(
        val id: Long = 0L,
        val item: Int = 0,
        val offset: Int = 0,
        /** 最后一次记下位置的时刻（epoch 毫秒）：超上限时按它淘汰最旧的。 */
        val at: Long = 0L,
    )

    /** 盘上的形状。只留 [entries]：这一份不按身份分，谁读谁写都是这台手机的读法。 */
    @Serializable
    private data class Blob(val entries: List<Entry> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    /** 盘上那一份，解码一次就留在内存里；null 表示还没读过。 */
    private var loaded: Blob? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun blob(): Blob {
        loaded?.let { return it }
        // 读盘失败（文件坏了、权限没了）退回空表，而不是把异常抛到界面上去 ——
        // 丢一次"回到原处"可惜，崩一次更糟（同 [PostDraftStore.blob] 的取舍）。
        val decoded = runCatching {
            storage.read()?.decodeToString()?.let { json.decodeFromString(Blob.serializer(), it) }
        }.getOrNull() ?: Blob()
        loaded = decoded
        return decoded
    }

    /**
     * 这条帖子上次停在哪儿：`(item, offset)` = `LazyListState.firstVisibleItemIndex` 与
     * `firstVisibleItemScrollOffset`；没记过时返回 `null`。
     *
     * 只给读：不写盘、不改任何东西。返回 `null` = "没有记录，别动列表"。这个类**不记 `(0, 0)`**
     * （见 [remember] 的第四个早退），所以"记过、就在顶部"不会在盘上出现 —— 顶部的正确表现本来
     * 就是"没有记录、什么都不滚"，两种说法指向同一个画面。
     *
     * 普通函数（不是 suspend）：首帧就要拿到答案，界面不等协程；读的是内存里那一份，
     * 第一次访问顺手解一次码（见 [blob]）。
     *
     * [postId] <= 0 时返回 `null`：那不是一条帖子。
     */
    fun positionOf(postId: Long): Pair<Int, Int>? {
        if (postId <= 0L) return null
        val entry = blob().entries.firstOrNull { it.id == postId } ?: return null
        return entry.item to entry.offset
    }

    /**
     * 记下"这条帖子停在这儿了"：写回并落盘（盘上的写丢到后台，界面不等）。
     *
     * 普通函数（不是 suspend）—— 与 [PostReadStore.markRead] 同款：调用方在滚动回调里同步调它，
     * 不能为了写盘把那一帧挂住。
     *
     * 三个早退：`postId <= 0`；坐标是负数（LazyColumn 不会给出这种数，出现就是调用点算错了，
     * 存进去只会在恢复时变成一次无意义的滚动）；与已记下的位置**完全相同**（同一条帖子来回
     * 抖一下不该反复编码写盘，同 [PostReadStore.markRead] 的早退）。
     *
     * 第四个早退是 `(0, 0)`：停在顶部不是一个"位置"，它本来就是每次进来的默认落点 ——
     * 记它只会把 [PositionLimit] 的位置槽位白白占掉（本仓同一条纪律见
     * `ui/component/ScrollMemory.kt:76-77` 的"顶部不记"）。恢复方那边对应的是
     * `positionOf` 返回 `null` → 什么都不滚，两边合起来就是"顶部 = 没有记录"。
     */
    fun remember(postId: Long, item: Int, offset: Int) {
        if (postId <= 0L) return
        if (item == 0 && offset == 0) return
        if (item < 0 || offset < 0) return
        val current = blob()
        val existing = current.entries.firstOrNull { it.id == postId }
        if (existing != null && existing.item == item && existing.offset == offset) return
        // 新的一条放最前，同一帖子的旧记录去掉，再按时刻倒序截断 —— 超出 [PositionLimit] 时
        // 被截掉的必然是 `at` 最小的那些，也就是最久没读过的那批帖子。
        val next = current.copy(
            entries = (listOf(Entry(postId, item, offset, now())) + current.entries.filterNot { it.id == postId })
                .sortedByDescending { it.at }
                .take(PositionLimit),
        )
        write(next)
    }

    /** 内存里立刻换成新的一份（下一次 [positionOf] 马上看得到），盘上的写丢到后台。 */
    private fun write(next: Blob) {
        loaded = next
        scope.launch {
            runCatching {
                storage.write(json.encodeToString(Blob.serializer(), next).encodeToByteArray())
            }
        }
    }

    private fun now(): Long = currentTimeMillis()
}

/**
 * 最多记多少条帖子。
 *
 * 只留最近读的那一批：详情页是"一次读一条、读完就走"的地方，200 条以外的位置记录没人会回头用；
 * 砍掉旧的最坏结果也只是"下次进来从头看"，而这一屏本来就是主楼 + 前几楼。
 */
private const val PositionLimit = 200
