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
 * 聊天页最近几屏消息落在盘上的那一份。
 *
 * [ChatMessageCache] 是进程内的：App 被杀之后冷启动再点进一个刚聊过的会话，又是「… → 等网络」。
 * 这一层把最近几个会话的最后几条写到本地文件，`AppBlobStorage.read()` 同步可读，首帧就能画。
 *
 * 三个取舍：
 *
 *  * **只留最近 20 个会话**：聊天记录是这 App 里体量最大的一类数据，全存会把文件撑到几十兆；
 *    20 个正好覆盖「今天聊过的那一圈」，再往前的会话点进去本来就要等网络。
 *  * **每个会话只留最后 30 条**：够填满一屏，往上翻历史本来就要走网络。
 *  * **分享卡片、名片不落盘**：它们的正文是 JSON，存下来还得跟着后端格式走；读回来时缺了这几条
 *    会画成「这条卡片打不开」，不如让它们等网络那一版。文本、图片、表情照常。
 *    代价是行里的最大 id 可能比真正的尾巴小，所以尾巴另存一个 [cursor]，别拿行去推。
 */
class ChatMessageStore(private val storage: AppBlobStorage) {

    /**
     * 进程里最近构造的那一个。
     *
     * 这一层是 [ChatMessageCache] 在 App 启动时接上的（`ChatMessageCache.bind`），聊天页手里只有仓储
     * 和那个缓存，拿不到这里的引用；而聊天页首屏需要的正是「这个会话上次的尾巴到哪」。为一次读盘
     * 补一条依赖注入链不值当，App 启动时只构造一个实例，直接从这里取。
     */
    companion object {
        var latest: ChatMessageStore? = null
            private set
    }

    init {
        latest = this
    }

    @Serializable
    private data class Row(
        val id: Long = 0L,
        val sender: String = "",
        val senderName: String = "",
        val body: String = "",
        val createdAt: String = "",
        val removed: Boolean = false,
        val kind: String = "text",
        val mentions: List<String> = emptyList(),
        val replyTo: Long = 0L,
        val replyBody: String = "",
        val replySenderName: String = "",
    ) {
        fun toModel(): ChatMessage = ChatMessage(
            id = id,
            sender = sender,
            senderName = senderName,
            body = body,
            createdAt = createdAt,
            removed = removed,
            kind = kind,
            mentions = mentions,
            replyTo = if (replyTo > 0L) replyTo else null,
            replyBody = replyBody,
            replySenderName = replySenderName,
        )

        companion object {
            /** 落不了盘的那种返回 null（见类注释里第三条取舍）。 */
            fun of(message: ChatMessage): Row? {
                if (message.share != null || message.card != null) return null
                return Row(
                    id = message.id,
                    sender = message.sender,
                    senderName = message.senderName,
                    body = message.body,
                    createdAt = message.createdAt,
                    removed = message.removed,
                    kind = message.kind,
                    mentions = message.mentions,
                    replyTo = message.replyTo ?: 0L,
                    replyBody = message.replyBody,
                    replySenderName = message.replySenderName,
                )
            }
        }
    }

    @Serializable
    private data class ConversationRows(
        val conversation: Long = 0L,
        val at: Long = 0L,
        val rows: List<Row> = emptyList(),
        /** 这个会话见到的最后一条消息 id（含落不了盘的卡片），冷启动只补它之后的部分。 */
        val cursor: Long = 0L,
    )

    @Serializable
    private data class Blob(val conversations: List<ConversationRows> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }
    /**
     * 盘上那一份，解码一次就留在内存里。
     *
     * `null` 表示还没读过；读完是空 `Blob` 表示「盘上没有」。读盘失败（文件坏了、权限没了）
     * 一律退回空，不把异常抛到界面上去。
     */
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

    /** 这个会话盘上留的最后几条；没存过就是空。 */
    fun read(conversationId: Long): List<ChatMessage> {
        if (conversationId <= 0L) return emptyList()
        return blob().conversations
            .firstOrNull { it.conversation == conversationId }
            ?.rows
            ?.map { it.toModel() }
            .orEmpty()
    }

    /**
     * 这个会话见到的最后一条消息 id；没存过就是 0。
     *
     * 它和 [read] 是两件事：落盘的正文有舍取，行里的最大 id 可能比服务端真正的尾巴小
     * （最新那几条可能是分享卡片）。冷启动要问的是「比这条新的都补回来」，所以尾巴单独存一行。
     */
    fun cursor(conversationId: Long): Long {
        if (conversationId <= 0L) return 0L
        return blob().conversations.firstOrNull { it.conversation == conversationId }?.cursor ?: 0L
    }

    /**
     * 把这一屏写回盘上：这个会话排到最前（最近的会话优先留着），其余按时间保留。
     */
    fun write(conversationId: Long, messages: List<ChatMessage>) {
        if (conversationId <= 0L || messages.isEmpty()) return
        val rows = messages.sortedBy { it.id }
            .takeLast(ChatMessageDiskLimit)
            .mapNotNull { Row.of(it) }
        if (rows.isEmpty()) return
        // 游标取整份消息里最大的那个，不是 [rows] 的：落不了盘的分享卡片也算「这条我见过了」。
        commit(conversationId, rows, messages.maxOf { it.id })
    }

    /**
     * 只把尾巴记下来，正文不动。
     *
     * 单独一个入口是因为 [write] 在没有可落盘的正文时直接返回：一个会话如果最新那几条全是分享卡片，
     * 它一条都不写，尾巴就丢了，下一次冷启动只能整页拉。
     */
    fun putCursor(conversationId: Long, lastId: Long) {
        if (conversationId <= 0L || lastId <= 0L) return
        val existing = blob().conversations.firstOrNull { it.conversation == conversationId }
        if (existing != null && existing.cursor >= lastId) return
        commit(conversationId, rows = null, lastId = lastId)
    }

    /**
     * 落盘：这个会话排到最前，其余按最近写入时间留着。
     *
     * [rows] 传 null 表示「只推游标，正文照旧」（见 [putCursor]）。`at` 也只在写正文时才更新——
     * 否则一个没人说话的会话只要被点开过就会一直排在淘汰顺序前面。
     */
    private fun commit(conversationId: Long, rows: List<Row>?, lastId: Long) {
        val current = blob()
        val existing = current.conversations.firstOrNull { it.conversation == conversationId }
        val nextRows = rows ?: existing?.rows ?: emptyList()
        // 游标只前进：两次读回来的先后不代表大小，退回去会让下一次冷启动重复补一堆。
        val nextCursor = maxOf(lastId, existing?.cursor ?: 0L)
        if (nextRows.isEmpty() && nextCursor <= 0L) return
        val entry = ConversationRows(
            conversation = conversationId,
            at = if (rows == null) (existing?.at ?: currentTimeMillis()) else currentTimeMillis(),
            rows = nextRows,
            cursor = nextCursor,
        )
        // 两个上限一起夹：按最近写入从新到旧收，收满会话数或条数就停，文件长度不会无限长。
        //
        // 这一次要写的 [entry] **无条件占一个名额**。只推游标的那种写入（`rows == null`）不更新 `at`，
        // 一个很久没说话的会话按时间排会掉到 20 名之外——循环第一次就 break，结果这条 entry 整个不见了：
        // 刚写的游标白写，它原来那 30 条正文也从盘上消失。而「打开一个很久没动的老会话」正是触发场景。
        var budget = ChatMessageDiskRows - nextRows.size
        val kept = mutableListOf(entry)
        for (row in current.conversations.filterNot { it.conversation == conversationId }
            .sortedByDescending { it.at }) {
            if (kept.size >= ChatMessageDiskConversations || row.rows.size > budget) break
            budget -= row.rows.size
            kept += row
        }
        val next = Blob(conversations = kept)
        loaded = next
        // 写是后台的：调用点在 Compose 主线程，几十 KB 的序列化不该挡着这一帧。
        scope.launch {
            runCatching {
                storage.write(json.encodeToString(Blob.serializer(), next).encodeToByteArray())
            }
        }
    }
}

/** 每个会话在盘上留多少条：一屏（30）就够，多了只是让文件变大。 */
private const val ChatMessageDiskLimit = 30

/** 盘上留几个会话：20 个覆盖「今天聊过的那一圈」，再多只是让文件变长。 */
private const val ChatMessageDiskConversations = 20

/**
 * 整个文件最多多少条。
 *
 * 会话数 × 每会话条数已经是理论上限，这一条是硬闸：上限哪天被改大、或者淘汰逻辑出错时，
 * 文件也不会长到几兆去。
 */
private const val ChatMessageDiskRows = ChatMessageDiskConversations * ChatMessageDiskLimit
