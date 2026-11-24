package io.github.samolego.canta.data.community

import io.github.samolego.canta.data.AppBlobStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 会话列表落在盘上的那一份。
 *
 * [ConversationCache] 是进程内的，App 一被杀就没了——冷启动照样要转几秒等网络，这正是「明明加了
 * 缓存还是要等」的原因。这一层把上一份写到本地文件，`AppBlobStorage.read()` 是**同步**的，所以
 * 第一次访问时就能读出来，首帧直接有东西可画。
 *
 * 只存画列表要用的那几个字段（[Row]），不存派生值（`label` / `isGroup`）：派生值由模型在
 * 内存里算，存进去反而会跟字段对不上。
 *
 * 只留一份（最近写入的那个身份）。会话列表本来就是一屏的东西，为多身份各留一份不值；
 * 换身份时旧那份会被下一次写入盖掉。
 */
class ConversationStore(private val storage: AppBlobStorage) {

    /** 一行会话里会变的那些字段；字段名与 [Conversation] 一致，读回来按名字对上即可。 */
    @Serializable
    private data class Row(
        val id: Long = 0L,
        val kind: String = "",
        val title: String = "",
        val peer: String = "",
        val peerName: String = "",
        val lastBody: String = "",
        val lastAt: String = "",
        val unread: Int = 0,
        val members: Int = 0,
        val pinned: Boolean = false,
        val muted: Boolean = false,
        val folded: Boolean = false,
    ) {
        fun toModel(): Conversation = Conversation(
            id = id,
            kind = kind,
            title = title,
            peer = peer,
            peerName = peerName,
            lastBody = lastBody,
            lastAt = lastAt,
            unread = unread,
            members = members,
            pinned = pinned,
            muted = muted,
            folded = folded,
        )

        companion object {
            fun of(conversation: Conversation): Row = Row(
                id = conversation.id,
                kind = conversation.kind,
                title = conversation.title,
                peer = conversation.peer,
                peerName = conversation.peerName,
                lastBody = conversation.lastBody,
                lastAt = conversation.lastAt,
                unread = conversation.unread,
                members = conversation.members,
                pinned = conversation.pinned,
                muted = conversation.muted,
                folded = conversation.folded,
            )
        }
    }

    @Serializable
    private data class Blob(
        val owner: String = "",
        /** 写入它的那一刻，只用于排查「这份是哪天的」，界面不用。 */
        val at: Long = 0L,
        val conversations: List<Row> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 盘上那一份，解码一次就留在内存里。
     *
     * `null` 表示还没读过；读完是空 `Blob` 表示「盘上没有」。用 `Lazy` 而不是 `by lazy`：
     * 读盘失败（文件坏了、权限没了）要能退回空，而不是把异常抛到界面上去。
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

    /** 盘上这份是谁写的；空串表示盘上没有。 */
    fun storedOwner(): String = blob().owner

    /** 盘上给 [owner] 存的那份列表；不是他写的就当作没有。 */
    fun read(owner: String): List<Conversation> {
        if (owner.isEmpty()) return emptyList()
        val cached = blob()
        if (cached.owner != owner) return emptyList()
        return cached.conversations.map { it.toModel() }
    }

    /**
     * 把这一份写到盘上（后台写，不阻塞界面）。
     *
     * 写的时候连 `at` 一起更新：下次读出来虽然不看它，但排查「列表为什么是旧的」时，
     * 有一个时间戳总比什么都没有强。
     */
    fun write(owner: String, conversations: List<Conversation>) {
        if (owner.isEmpty() || conversations.isEmpty()) return
        val blob = Blob(
            owner = owner,
            at = io.github.samolego.canta.util.currentTimeMillis(),
            conversations = conversations.take(ConversationStoreLimit).map { Row.of(it) },
        )
        loaded = blob
        scope.launch {
            runCatching {
                storage.write(json.encodeToString(Blob.serializer(), blob).encodeToByteArray())
            }
        }
    }
}

/** 盘上留多少条：够填满消息页首屏还有富余，再多就是为一个列表文件付利息。 */
private const val ConversationStoreLimit = 60
