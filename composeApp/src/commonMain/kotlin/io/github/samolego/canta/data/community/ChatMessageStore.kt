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
 *  * **只留最近三个会话**：聊天记录是这 App 里体量最大的一类数据，全存会把文件撑到几十兆；
 *    三个会话正好覆盖「刚聊完、切出去、再回来」这条最常见的路径。
 *  * **每个会话只留最后 30 条**：够填满一屏，往上翻历史本来就要走网络。
 *  * **分享卡片、名片不落盘**：它们的正文是 JSON，存下来还得跟着后端格式走；读回来时缺了这几条
 *    会画成「这条卡片打不开」，不如让它们等网络那一版。文本、图片、表情照常。
 */
class ChatMessageStore(private val storage: AppBlobStorage) {

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
     * 把这一屏写回盘上：这个会话排到最前（最近的会话优先留着），其余按时间保留两个。
     *
     * 写是后台的：调用点在 Compose 主线程，几十 KB 的序列化不该挡着这一帧。
     */
    fun write(conversationId: Long, messages: List<ChatMessage>) {
        if (conversationId <= 0L || messages.isEmpty()) return
        val rows = messages.sortedBy { it.id }
            .takeLast(ChatMessageDiskLimit)
            .mapNotNull { Row.of(it) }
        if (rows.isEmpty()) return
        val kept = blob().conversations
            .filterNot { it.conversation == conversationId }
            .take(ChatMessageDiskConversations - 1)
        val next = Blob(
            conversations = listOf(
                ConversationRows(conversation = conversationId, at = currentTimeMillis(), rows = rows),
            ) + kept,
        )
        loaded = next
        scope.launch {
            runCatching {
                storage.write(json.encodeToString(Blob.serializer(), next).encodeToByteArray())
            }
        }
    }
}

/** 每个会话在盘上留多少条：一屏（30）就够，多了只是让文件变大。 */
private const val ChatMessageDiskLimit = 30

/** 盘上留几个会话。 */
private const val ChatMessageDiskConversations = 3
