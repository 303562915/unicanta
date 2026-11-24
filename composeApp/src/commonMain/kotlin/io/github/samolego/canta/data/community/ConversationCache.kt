package io.github.samolego.canta.data.community

import io.github.samolego.canta.util.currentTimeMillis

/**
 * 消息页那一屏的进程级快照。
 *
 * 为什么放在这里，而不是放进 `MessagesScreen` 的 `remember`：那个 Composable 每次被导航进来都是
 * 一个新实例，`remember` 跟着一起没了。于是从聊天页点返回，看到的永远是「空白 → 转圈 → 等网络」，
 * 而人刚才明明已经看过这一屏。这份快照活到进程结束，所以切回来能先把上一份画出来，再在后台刷新。
 *
 * 只活在内存里，**没有持久化**：重启之后重拉一次是应该的（那时候后端可能已经变了很多），
 * 要解决的是「页面之间来回切不要重拉」，不是「离线也能用」。
 *
 * 关于线程：所有读写都发生在 Compose 主线程——`LaunchedEffect` 里的协程、`rememberCoroutineScope()`
 * 派出去的协程，默认都在 Main。所以这里没有加锁，也没有 `@Volatile`：加锁会让这一层读起来比它
 * 实际承担的复杂得多，而它承担的只是一份 UI 快照，最坏情况是某一帧读到上一份。将来真要从 IO 线程
 * 读写，先把它换成 `mutableStateMapOf` 再谈。
 *
 * 按 `owner` 分份存：换身份要换整张列表，不同身份共用一个槽位会把别人的会话画给这个人。
 */
object ConversationCache {

    /**
     * 一屏所需的全部东西，一次存一起取。
     *
     * 三个字段捆在一起是因为它们来自同一次刷新：分开存的话，可能出现「会话是新的、关注是旧的」
     * 这种半新半旧的中间态，而界面上那两段是并排显示在同一条搜索里的。
     */
    class Entry(
        val conversations: List<Conversation>,
        val followers: List<SocialPerson>,
        val summary: InboxSummary,
        /** 这份快照是什么时候拿到的；现在没有 TTL，留着是给以后判断新旧用。 */
        val at: Long,
    )

    private val entries = mutableMapOf<String, Entry>()

    /**
     * 盘上那一份（见 [ConversationStore]）。
     *
     * 内存这份活不过进程：冷启动时它是空的，于是「加了缓存还是要转几秒」。接上磁盘之后，
     * `get` 找不到内存就去盘上拿——那一步是同步读，首帧就有东西可画。
     */
    private var store: ConversationStore? = null

    /** 接上磁盘缓存。App 启动时调一次；之后再进来的页面就先有上一份了。 */
    fun bind(store: ConversationStore) {
        this.store = store
    }

    /**
     * 上一次写缓存的是谁。
     *
     * 存在的原因很具体：调用方是从 `DeviceIdentity.current(...)` **异步**拿到身份的，所以
     * 页面第一帧的 `owner` 是空串——那一刻按 owner 查缓存必然查不到，于是每次进这一页都要
     * 先转一圈。身份是这台设备生成的，缓存里通常只有一份，拿它兜第一帧是对的。
     */
    private var lastOwner: String = ""

    /** 这个身份上次看到的快照；没有就是 null（第一次进这一页，或者刚被清掉）。 */
    fun get(owner: String): Entry? {
        if (owner.isEmpty()) return null
        entries[owner]?.let { return it }
        // 冷启动：内存里没有，但盘上可能有。先把它画出来，网络回来再顶掉。
        val fromDisk = store?.read(owner).orEmpty()
        return if (fromDisk.isEmpty()) null else entryOf(fromDisk)
    }

    /**
     * 只装会话列表的一份快照：关注的人与互动总数没有落盘（它们每次刷新都会重拉，且体积不小），
     * 所以从磁盘还原出来的这一份里那两样是空的。界面照样先画列表，多的两段等网络。
     */
    private fun entryOf(conversations: List<Conversation>): Entry =
        Entry(conversations, emptyList(), InboxSummary.EMPTY, 0L)

    /**
     * `owner` 还没读到时的兜底：这台设备上一次看到的那一份。
     *
     * 只在 [get] 返回 null 时用。换身份意味着清数据重装，那时候缓存也一起没了，
     * 所以这里不会把上一个账号的会话画给下一个人。
     */
    fun getRecent(): Entry? {
        if (lastOwner.isNotEmpty()) return entries[lastOwner]
        // 冷启动时 `lastOwner` 还是空的，但盘上记着上一次是谁写的。
        val storedOwner = store?.storedOwner().orEmpty()
        if (storedOwner.isEmpty()) return null
        val fromDisk = store?.read(storedOwner).orEmpty()
        return if (fromDisk.isEmpty()) null else entryOf(fromDisk)
    }

    /** 整份换掉：一次完整刷新之后调这个。 */
    fun put(
        owner: String,
        conversations: List<Conversation>,
        followers: List<SocialPerson>,
        summary: InboxSummary,
    ) {
        if (owner.isEmpty()) return
        entries[owner] = Entry(conversations, followers, summary, currentTimeMillis())
        lastOwner = owner
        store?.write(owner, conversations)
    }

    /**
     * 只换会话列表，关注的人与互动总数沿用上一份。
     *
     * 置顶、标记已读、删除会话、免打扰这些动作只动列表，为了它们重新去问一遍关注列表没有意义。
     *
     * 没有上一份时**什么都不做**：那样拼出来的 Entry 里关注是空的，会让下一次进来把「还没拉过」
     * 误画成「没有关注任何人」。宁可让它继续没有缓存，走一次正常加载。
     */
    fun putConversations(owner: String, conversations: List<Conversation>) {
        if (owner.isEmpty()) return
        val old = entries[owner] ?: return
        entries[owner] = Entry(conversations, old.followers, old.summary, currentTimeMillis())
        lastOwner = owner
        store?.write(owner, conversations)
    }

    /** 扔掉这个身份的快照；下次进来重新走加载。 */
    fun invalidate(owner: String) {
        if (owner.isEmpty()) return
        entries.remove(owner)
        if (lastOwner == owner) lastOwner = ""
    }

    /** 全扔：退出登录、清数据这类场景用。 */
    fun invalidateAll() {
        entries.clear()
        lastOwner = ""
    }
}
