package io.github.samolego.canta.data.cache

/**
 * 一屏的进程级快照：页面被导航回来时先把上一次画的东西摆上，网络回来的那一版再顶上去。
 *
 * 为什么需要它：这些页面每次被导航进来都是新的 Composable 实例，`remember` 跟着一起没了，
 * 于是「点进去 → 空白/转圈 → 等网络 → 才出现」成了固定动作。后端一次往返几百毫秒，人眼
 * 看着就是卡。把上一屏留在进程里，切回来是立刻的，刷新在后台发生。
 *
 * 和 [io.github.samolego.canta.data.community.ConversationCache] 的区别只是这个更通用：
 * 那边按 owner 分份并且有明确的业务语义，这边只认一个字符串键，由各页面自己拼
 * （惯例是 `"screen:<页面>:<身份或编号>"`）。
 *
 * 只活在内存里，**没有持久化**：重启后重拉一次是应该的（后端可能已经变了），这里解决的是
 * 「页面之间来回切不要重拉」，不是「离线也能用」。
 *
 * 读写都发生在 Compose 主线程（`LaunchedEffect` 与 `rememberCoroutineScope` 派出去的协程默认
 * 都在 Main），所以不加锁——最坏情况是某一帧读到上一份，而它只是一份 UI 快照。
 */
object ScreenCache {

    /**
     * 各页面的槽位。
     *
     * `@PublishedApi internal` 而不是 private：`get` 是 public inline，内联之后它读的这个字段就
     * 出现在调用方的类里，Kotlin 不许 public inline 碰 private 成员，所以只能降到 internal 并
     * 显式发布。
     */
    @PublishedApi
    internal val entries = mutableMapOf<String, Any>()

    /** 这个键上一次存的快照；类型对不上或者没存过都返回 null。 */
    inline fun <reified T> get(key: String): T? = entries[key] as? T

    /** 整份换掉。键为空时什么都不做：空键会把所有页面串到同一个槽位里。 */
    fun put(key: String, value: Any) {
        if (key.isEmpty()) return
        entries[key] = value
    }

    /** 丢掉一屏（清空记录、退出某个对象之后用）。 */
    fun remove(key: String) {
        entries.remove(key)
    }

    /** 全扔：退出登录、清数据这类场景。 */
    fun clear() {
        entries.clear()
    }
}
