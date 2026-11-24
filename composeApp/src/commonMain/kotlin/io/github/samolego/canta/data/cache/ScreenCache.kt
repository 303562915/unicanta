package io.github.samolego.canta.data.cache

import io.github.samolego.canta.data.AppBlobStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一屏的进程级快照 + 本地文件兜底：页面被导航回来时先把上一次画的东西摆上，网络回来的那一版再顶上去。
 *
 * 为什么需要它：这些页面每次被导航进来都是新的 Composable 实例，`remember` 跟着一起没了，
 * 于是「点进去 → 空白/转圈 → 等网络 → 才出现」成了固定动作。后端一次往返几百毫秒，人眼
 * 看着就是卡。
 *
 * **为什么还要落盘**：进程级那份活不过进程。App 被系统回收之后再打开，缓存是空的，照样转几秒——
 * 这正是「明明加了缓存还是要等」的原因。接上 [bind] 之后，`put` 的每一份都顺手写成 JSON 落到
 * 本地文件；`get` 在内存里找不到时（冷启动、换身份）从那份文件里同步解出来，首帧就有东西可画。
 *
 * 只活在内存与这个文件里，**不做版本迁移**：键或模型变了，旧 JSON 解不出来就当没有（`runCatching`
 * 兜住），页面退回一次正常加载，不会崩。
 *
 * 读写都发生在 Compose 主线程（`LaunchedEffect` 与 `rememberCoroutineScope` 派出去的协程默认都在
 * Main），内存那份不加锁；文件写入走后台，并且合并成一次（见 [scheduleFlush]）。
 */
object ScreenCache {

    /** 盘上那一份：键 → 这个键对应快照的 JSON 文本。文本而不是对象，解不开的旧数据才不会拖垮整份。 */
    @Serializable
    private data class Blob(val entries: Map<String, String> = emptyMap())

    /**
     * 各页面的槽位。
     *
     * `@PublishedApi internal` 而不是 private：下面的读写都是 public inline，内联之后它们读的字段
     * 就出现在调用方的类里，Kotlin 不许 public inline 碰 private 成员，所以只能降到 internal 并
     * 显式发布。
     */
    @PublishedApi
    internal val entries = mutableMapOf<String, Any>()

    /** 同一批快照的 JSON 文本，落盘用；和 [entries] 一一对应。 */
    @PublishedApi
    internal val texts = mutableMapOf<String, String>()

    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var storage: AppBlobStorage? = null
    private var loadedFromDisk = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 待写的那一次。新的一次进来就把上一次取消掉，等于「停手 800ms 再写」。 */
    private var flushJob: Job? = null

    /** 接上落盘。App 启动时调一次；之后再 `get` 就多一层本地文件兜底。 */
    fun bind(storage: AppBlobStorage) {
        this.storage = storage
        // 立刻把盘上那份读进来，而不是等某一屏第一次 `get` 时才读：
        // 那一次读是同步的，落在首帧里就是肉眼可见的卡顿；启动这一下读，正好跟其它初始化重叠。
        ensureLoaded()
    }

    /**
     * 快照格式的版本。
     *
     * 改它就是一次性作废所有旧快照：盘上那些键在 `v2:` 前缀下全部落空，页面退回一次正常加载，
     * 而不是继续把一份可能写坏/格式过时的数据画出来。用户不必去清数据。
     */
    @PublishedApi
    internal const val SCHEMA_VERSION = 2

    /** 把调用方的键映射到带版本号的槽位。 */
    @PublishedApi
    internal fun versioned(key: String): String = "v$SCHEMA_VERSION:$key"

    /**
     * 把盘上那份读进 [texts]（只读文本，不反序列化）。
     *
     * 第一次 `get`/`put` 时调；读失败（文件坏了、权限没了）就当盘上是空的，不把异常抛给界面。
     */
    @PublishedApi
    internal fun ensureLoaded() {
        if (loadedFromDisk) return
        loadedFromDisk = true
        val raw = runCatching { storage?.read()?.decodeToString() }.getOrNull() ?: return
        val blob = runCatching { json.decodeFromString(Blob.serializer(), raw) }.getOrNull() ?: return
        blob.entries.forEach { (key, text) -> if (!texts.containsKey(key)) texts[key] = text }
    }

    /** 这个键上一次存的快照；类型对不上或者没存过都返回 null。 */
    inline fun <reified T> get(key: String): T? {
        if (key.isEmpty()) return null
        ensureLoaded()
        val slot = versioned(key)
        (entries[slot] as? T)?.let { return it }
        // 冷启动：内存空，盘上那份文本还在，解出来先用。
        val text = texts[slot] ?: return null
        val decoded = runCatching { json.decodeFromString<T>(text) }.getOrNull() ?: return null
        entries[slot] = decoded as Any
        return decoded
    }

    /** 整份换掉：内存与盘上那份文本一起更新，写入合并到一次（见 [scheduleFlush]）。 */
    inline fun <reified T> put(key: String, value: T) {
        if (key.isEmpty()) return
        ensureLoaded()
        val slot = versioned(key)
        entries[slot] = value as Any
        // 编码失败（模型没加 @Serializable 之类）不该让页面崩：退回「只在内存里」，下一次还是会画对。
        runCatching { json.encodeToString(value) }.onSuccess { texts[slot] = it }
        scheduleFlush()
    }

    /** 丢掉一屏（清空记录、退出某个对象之后用）。 */
    fun remove(key: String) {
        if (key.isEmpty()) return
        val slot = versioned(key)
        entries.remove(slot)
        texts.remove(slot)
        scheduleFlush()
    }

    /** 全扔：退出登录、清数据这类场景。 */
    fun clear() {
        entries.clear()
        texts.clear()
        scheduleFlush()
    }

    /**
     * 合并写入：800ms 内的多次 `put` 只落一次盘。
     *
     * 一个页面进来往往连着写好几份（列表、详情、评论），逐次写就是反复序列化 + 反复写文件；
     * 800ms 是「这一屏已经画完」的停顿，人不会在这段时间里看到差别。
     */
    @PublishedApi
    internal fun scheduleFlush() {
        val target = storage ?: return
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(FlushDelayMillis)
            val snapshot = texts.toMap()
            runCatching {
                target.write(json.encodeToString(Blob.serializer(), Blob(snapshot)).encodeToByteArray())
            }
        }
    }
}

/** 合并写入的停顿；见 [ScreenCache.scheduleFlush]。 */
private const val FlushDelayMillis = 800L
