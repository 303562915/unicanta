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
 * 「哪些帖子读过了」「哪些帖子收起来了」——都写在本机，不进后端。
 *
 * 只记回帖数不记"读过没有"：不在表里就是没读过，读过了但回帖数变大就是"有新回复"——
 * 两件事共用一个数字，不需要额外的布尔量。
 *
 * 为什么不进后端：这两件事是**这台手机上的这个人**的看法，不是帖子的公共属性；后端加两个表
 * 就要同步、要处理换机、要处理一人多机，而客户端要的只是"列表上标一个点"。所以它跟
 * [PostDraftStore] 一样落在 [AppBlobStorage] 里，形状也一样：同步读一次、写入丢后台。
 *
 * 两份都是**上限**保持的：已读留最近 [ReadLimit] 条、收藏留最近 [SavedLimit] 条。发帖量再大，
 * 盘上这一份也不会无限长——旧帖子的已读标记丢了不算损失（顶多又标一次未读）。
 */
class PostReadStore(private val storage: AppBlobStorage) {

    /** 一行的形状：id + 回帖数 + 时刻。用表而不是 Map，`Map<Long, …>` 的键在 JSON 里只能当字符串。 */
    @Serializable
    private data class Entry(val id: Long = 0L, val comments: Int = 0, val at: Long = 0L)

    /**
     * 盘上的形状。
     *
     * [saved] 是**最近的在前**：收藏列表按收藏时间倒序看才顺手，而收藏时间就是它进表的顺序。
     */
    @Serializable
    private data class Blob(
        val reads: List<Entry> = emptyList(),
        val saved: List<Long> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** 盘上那一份，解码一次就留在内存里；null 表示还没读过。 */
    private var loaded: Blob? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun blob(): Blob {
        loaded?.let { return it }
        val decoded = runCatching {
            storage.read()?.decodeToString()?.let { json.decodeFromString(Blob.serializer(), it) }
        }.getOrNull() ?: Blob()
        loaded = decoded
        return decoded
    }

    // ---------------------------------------------------------------- 已读

    /**
     * 这条帖子现在算不算"没看过 / 有新回复"。
     *
     * 没读过 true；读过了但回帖数比记下来的多 true；其余 false。
     */
    fun isUnread(postId: Long, comments: Int): Boolean {
        if (postId <= 0L) return false
        val mark = blob().reads.firstOrNull { it.id == postId } ?: return true
        return comments > mark.comments
    }

    /**
     * 记下"读到这儿了"：回帖数只涨不跌，读一次就把数字对齐到当前值。
     *
     * 返回是否真的写了一次：调用方据此决定要不要让列表重算（已经对齐过就什么都不用做）。
     */
    fun markRead(postId: Long, comments: Int): Boolean {
        if (postId <= 0L) return false
        val current = blob()
        val existing = current.reads.firstOrNull { it.id == postId }
        // 已经对齐过就不写盘：打开同一条帖子两次是常事，不该每次都落一次盘。
        if (existing != null && existing.comments >= comments) return false
        val next = current.copy(
            reads = (listOf(Entry(postId, comments, now()) + current.reads.filterNot { it.id == postId }))
                .take(ReadLimit),
        )
        write(next)
        return true
    }

    // ---------------------------------------------------------------- 收藏

    fun isSaved(postId: Long): Boolean = blob().saved.contains(postId)

    /** 收藏里的 id，最近的在前。 */
    fun savedIds(): List<Long> = blob().saved

    /**
     * 收藏 / 取消收藏，回答改完之后"现在收着没有"。
     *
     * 收藏与已读互不影响：收起来不代表读过，读过也不代表收起来。
     */
    fun toggleSaved(postId: Long): Boolean {
        if (postId <= 0L) return false
        val current = blob()
        val nowSaved = if (current.saved.contains(postId)) {
            current.saved.filterNot { it == postId }
        } else {
            (listOf(postId) + current.saved).take(SavedLimit)
        }
        write(current.copy(saved = nowSaved))
        return nowSaved.contains(postId)
    }

    /** 内存里立刻换成新的一份（下一次读取马上看到），盘上的写丢到后台。 */
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

/** 已读留多少条：再多也没人翻回去看"我读过哪几帖"。 */
private const val ReadLimit = 400

/** 收藏留多少条：收得下常用那一批，又不至于变成第二个书签管理器。 */
private const val SavedLimit = 300
