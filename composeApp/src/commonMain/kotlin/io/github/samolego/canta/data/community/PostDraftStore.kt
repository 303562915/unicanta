package io.github.samolego.canta.data.community

import io.github.samolego.canta.data.AppBlobStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一篇还没发出去的帖子：写在手机上，发出去成功之后才删。
 *
 * [id] 是本地自己的（`currentTimeMillis().toString()`），后端不认识它：草稿在发布之前只是一段
 * 本地文字，没有帖子 id 可挂。同一篇草稿换着编辑时沿用同一个 [id]，所以「改一改再取消」覆盖的
 * 是同一行，而不是攒出一堆副本。
 */
@Serializable
data class PostDraft(
    val id: String,
    val title: String = "",
    val body: String = "",
    /** 最后一次写盘的时刻（epoch 毫秒）：列表按它倒序，编辑器顶部拿它显示「已自动保存 · HH:mm」。 */
    val updatedAt: Long = 0L,
)

/**
 * 帖子草稿落在盘上的那一份。
 *
 * 形状与 [ConversationStore] 同一套：`AppBlobStorage.read()` 是**同步**的，第一次访问就能读出来，
 * 所以列表首帧直接有草稿可画，不用等协程；写入丢到后台，界面不等它。
 *
 * 只留一个身份的那一份（盘上一个 [Blob]）：[drafts] 在 `owner` 不匹配时返回空表，所以换身份不会
 * 读到别人的草稿；下一个身份一存草稿，旧那份就被整个盖掉。这和会话列表的行为一致 —— 草稿是
 * 「手上这一篇」，不是需要跨身份归档的东西。
 *
 * 每个身份最多留 [PostDraftLimit] 篇，超了丢最旧的：草稿一多就变成第二个收件箱，谁也不翻。
 */
class PostDraftStore(private val storage: AppBlobStorage) {

    @Serializable
    private data class Blob(
        val owner: String = "",
        val drafts: List<PostDraft> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 盘上那一份，解码一次就留在内存里。
     *
     * `null` 表示还没读过；读完是空 [Blob] 表示「盘上没有」。读盘失败（文件坏了、权限没了）要退回
     * 空表，而不是把异常抛到界面上去 —— 丢草稿可惜，崩一次更糟。
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

    /**
     * [owner] 的草稿，最近改过的在前。不是他写的那份就当没有；没有身份时也当没有
     * （草稿要挂在能认出的人身上，否则谁都能看见上一台手机的半篇帖子）。
     */
    fun drafts(owner: String): List<PostDraft> {
        if (owner.isEmpty()) return emptyList()
        val cached = blob()
        if (cached.owner != owner) return emptyList()
        return cached.drafts.sortedByDescending { it.updatedAt }
    }

    /**
     * 存一篇：同一个 [id] 覆盖旧的那篇（不改它在列表里的年龄以外的任何东西），最多留
     * [PostDraftLimit] 篇，超了丢最旧的。
     *
     * 标题和正文都空白的不存：「点开编辑器又关掉」不该在盘上留一条空草稿。
     */
    fun save(owner: String, draft: PostDraft) {
        if (owner.isEmpty() || draft.id.isEmpty()) return
        if (draft.title.isBlank() && draft.body.isBlank()) return
        val merged = (listOf(draft) + drafts(owner).filterNot { it.id == draft.id })
            .sortedByDescending { it.updatedAt }
            .take(PostDraftLimit)
        write(owner, merged)
    }

    /** 删掉一篇（发了、或者用户自己不要了）。 */
    fun delete(owner: String, id: String) {
        if (owner.isEmpty() || id.isEmpty()) return
        val current = drafts(owner)
        if (current.none { it.id == id }) return
        write(owner, current.filterNot { it.id == id })
    }

    /** 清空这个身份的草稿。盘上那份是别人的时候什么都不做：不是他的东西不替他删。 */
    fun clear(owner: String) {
        if (owner.isEmpty()) return
        if (blob().owner != owner) return
        write(owner, emptyList())
    }

    /** 内存里立刻换成新的一份（下一次 [drafts] 马上读得到），盘上的写丢到后台。 */
    private fun write(owner: String, drafts: List<PostDraft>) {
        val next = Blob(
            owner = owner,
            drafts = drafts.sortedByDescending { it.updatedAt }.take(PostDraftLimit),
        )
        loaded = next
        scope.launch {
            runCatching {
                storage.write(json.encodeToString(Blob.serializer(), next).encodeToByteArray())
            }
        }
    }
}

/** 每个身份在盘上留几篇：够接着写，又不至于变成第二个收件箱。 */
private const val PostDraftLimit = 20
