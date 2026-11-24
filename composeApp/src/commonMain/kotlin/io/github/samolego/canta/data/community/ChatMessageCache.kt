package io.github.samolego.canta.data.community

/**
 * 一条会话最近一次画出来的那一屏消息。
 *
 * 和 [ConversationCache] 一个理由：聊天页每次被导航进来都是新的 Composable 实例，`remember` 跟着
 * 一起没了，于是从消息页点进一个刚看过的会话，又要先看一次「… → 等网络 → 才出现」，而那几条
 * 刚才明明就在屏幕上。
 *
 * 只活在内存里，没有持久化：重启后重拉一次是应该的（后端可能已经变了），这里解决的是
 * 「页面之间来回切不要重拉」，不是「离线也能看」。
 *
 * 按会话号分份：不同会话的消息绝不能互相串。读写在 Compose 主线程，所以不加锁——最坏情况是
 * 某一帧读到上一份，而它只是一份 UI 快照。
 */
object ChatMessageCache {

    /** 一份最多留多少条：够填满首屏（`ChatPageSize` 是 30），多留一点是为了滚一两屏也不空。 */
    private const val LIMIT = 60

    private val entries = mutableMapOf<Long, List<ChatMessage>>()

    /** 这个会话上次画出来的那些；没看过就是 null（第一次进，或者刚被清掉）。 */
    fun get(conversationId: Long): List<ChatMessage>? =
        if (conversationId <= 0L) null else entries[conversationId]

    /**
     * 整份换掉。
     *
     * 留的是**最新的**那些（按 id 从大到小的尾巴）：缓存的意义是「点进来先看见最近的对话」，
     * 留一堆很早以前的历史对第一屏没有帮助。
     */
    fun put(conversationId: Long, messages: List<ChatMessage>) {
        if (conversationId <= 0L) return
        if (messages.isEmpty()) {
            entries.remove(conversationId)
            return
        }
        entries[conversationId] = if (messages.size <= LIMIT) {
            messages
        } else {
            messages.sortedBy { it.id }.takeLast(LIMIT)
        }
    }

    /** 丢掉一条会话的缓存（清空聊天记录之后用）。 */
    fun invalidate(conversationId: Long) {
        entries.remove(conversationId)
    }

    /** 全扔：退出、清数据这类场景。 */
    fun invalidateAll() {
        entries.clear()
    }
}
