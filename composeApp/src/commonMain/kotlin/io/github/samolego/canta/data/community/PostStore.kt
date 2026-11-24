package io.github.samolego.canta.data.community

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 一条帖子。列表回答里 [body] 是空的（列表只发 [excerpt]），[mine] 也只有详情回答才有。
 *
 * 字段缺了就退回默认值（与 `PlanRepository.parseAirport` 同一个约定）：后端是老部署、或者某个
 * 字段这次没发，页面照样画得出一条，而不是整块变成空白。
 */
data class CommunityPost(
    val id: Long = 0L,
    val title: String = "",
    /** 正文前 200 字符，列表用。 */
    val excerpt: String = "",
    /** 正文全文，只有 [PostStore.get] 那个回答里才有。 */
    val body: String = "",
    val author: String = "",
    /** 署名背后的身份码：删帖认它，不认署名。 */
    val authorId: String = "",
    val createdAt: String = "",
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val comments: Int = 0,
    val likes: Int = 0,
    val mineLike: Boolean = false,
    /** 这条是不是我发的；只有详情回答里才为 true。 */
    val mine: Boolean = false,
    /**
     * 分类：页面上五个候选之一（闲聊/求助/分享/资源/其它），空串表示发帖时没选。
     *
     * 后端只当一段字符串存，不校验候选集：`CommunityPost.category` 是原样回来的，所以页面
     * 画 chip 时对不上候选的照样能画出来（画原文）。
     */
    val category: String = "",
    /** 标签，最多 5 个、每个最多 20 字（后端归整过）。老回答没有这一项时是空表。 */
    val tags: List<String> = emptyList(),
)

/** 一条回帖。 */
data class PostComment(
    val id: Long = 0L,
    val body: String = "",
    val author: String = "",
    val authorId: String = "",
    val createdAt: String = "",
)

/** 一条帖子的详情：正文全文、回帖（后端按时间正序，最多 200 条）和汇总数。 */
data class PostThread(
    val post: CommunityPost,
    val comments: List<PostComment> = emptyList(),
)

/**
 * 「社区帖子」的数据层：六个 RPC（`posts_list` / `post_get` / `post_add` / `post_comment_add` /
 * `post_like` / `post_delete`）各一个方法。
 *
 * 为什么单独成文件、不复用 `PlanRepository`：那个类已经一千八百多行，帖子这一块与方案/评论没有
 * 共享状态，挤进去只会让两个功能同时改同一个文件。网络那一层也不重复实现：[CommunityRpc] 已经
 * 处理好了 apikey、超时、`PGRST202`（老后端还没有这个函数）与失败原因。
 *
 * 身份：所有写方法都不发"我是谁"的号码，只带 [CommunityRpc.token]；认不出来时后端拒绝，
 * [lastError] 里就是后端那句话。这与 `PlanRepository` 里 `p_token` 的做法一致。
 *
 * 解析风格照 `PlanRepository.parseAirport`：`json.parseToJsonElement(text)` + `jsonObject` /
 * `jsonArray`，字段缺了退回默认值，`runCatching` 兜住形状不对的回答（返回 null 而不是抛）。
 */
class PostStore(private val rpc: CommunityRpc) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 上一次调用被后端明确拒绝的理由（`{ok:false,error:"…"}`），或者 null。
     *
     * 与 [CommunityRpc.lastError] 分开：那个是"这一趟 HTTP 就失败了"，这个是"HTTP 通了但后端说不行"
     * ——比如"刚发过一帖，等一分钟再发"、"只有作者本人能删"。网络没通时用 [reason] 统一取一句。
     */
    var lastError: String? = null
        private set

    /** 上一次失败的说明，页面直接显示：后端的一句话优先，没有就用传输层的。 */
    val reason: String?
        get() = lastError ?: rpc.lastError

    // ---------------------------------------------------------------- 读

    /**
     * 帖子列表：置顶在前、其余按时间倒序。[offset] 翻页，[limit] 后端夹在 1..50。
     *
     * null 表示这一次没拿到（网络没通，或后端还没这个函数）：页面据此说"还没拉到"，而不是
     * "一条都没有"——空的数组才是"一条都没有"。
     */
    suspend fun list(offset: Int = 0, limit: Int = 20): List<CommunityPost>? {
        lastError = null
        val proof = rpc.token()
        val payload = buildJsonObject {
            put("p_offset", offset.coerceAtLeast(0))
            put("p_limit", limit.coerceIn(1, 50))
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("posts_list", payload) ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonArray.map(::parsePost)
        }.getOrNull()
    }

    /**
     * 一条帖子的详情：[PostThread.post] 带正文全文，[PostThread.comments] 是回帖。
     * 帖子不在了（或后端还没这个函数）返回 null。
     */
    suspend fun get(id: Long): PostThread? {
        if (id <= 0L) return null
        lastError = null
        val proof = rpc.token()
        val payload = buildJsonObject {
            put("p_id", id)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("post_get", payload) ?: return null
        return runCatching {
            val answer = json.parseToJsonElement(text).jsonObject
            val post = answer["post"]?.jsonObject?.let(::parsePost) ?: return@runCatching null
            val comments = answer["comments"]?.jsonArray?.map(::parseComment).orEmpty()
            PostThread(post = post, comments = comments)
        }.getOrNull()
    }

    // ---------------------------------------------------------------- 写

    /**
     * 发一帖，回答新帖的 id。标题空（或后端拒绝）返回 null，[lastError] 里是理由。
     *
     * [category] 与 [tags] 原样发上去，归整（trim、截 20 字、去重、最多 5 个）由 `post_add` 做：
     * 界面只管收集，长度规则只有一处实现，不会两边算得不一样。`p_tags` 发的是 JSON 字符串数组，
     * PostgREST 把它映射到 `text[]` 参数上。
     *
     * 后端对同一身份有 60 秒一帖的闸：撞上它时 [lastError] 是"刚发过一帖，等一分钟再发"。
     */
    suspend fun add(
        title: String,
        body: String,
        author: String,
        category: String = "",
        tags: List<String> = emptyList(),
    ): Long? {
        lastError = null
        if (title.isBlank()) return null
        val proof = rpc.token()
        val payload = buildJsonObject {
            put("p_title", title.trim())
            put("p_body", body)
            put("p_author", author)
            put("p_category", category.trim())
            putJsonArray("p_tags") { tags.forEach { tag -> add(JsonPrimitive(tag)) } }
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("post_add", payload) ?: return null
        val answer = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        answer["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { lastError = it }
        return answer["id"]?.jsonPrimitive?.longOrNull
    }

    /**
     * 回一帖，回答回完之后那条帖子的**回帖总数**（列表上的数字不必再问一次）。
     * 帖子被锁、不在了、回帖是空的、认不出身份 —— 都是 null，理由在 [lastError]。
     */
    suspend fun comment(postId: Long, body: String, author: String): Int? {
        lastError = null
        if (postId <= 0L || body.isBlank()) return null
        val proof = rpc.token()
        val payload = buildJsonObject {
            put("p_post_id", postId)
            put("p_body", body.trim())
            put("p_author", author)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("post_comment_add", payload) ?: return null
        val answer = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        answer["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { lastError = it }
        if (answer["ok"]?.jsonPrimitive?.booleanOrNull == false) return null
        return answer["comments"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0
    }

    /**
     * 点赞 / 取消点赞，回答 `(赞数, 我现在赞了没有)`。[like] 是 false 就是取消。
     * 认不出身份、帖子不在了返回 null。
     */
    suspend fun like(postId: Long, like: Boolean): Pair<Int, Boolean>? {
        lastError = null
        if (postId <= 0L) return null
        val proof = rpc.token()
        val payload = buildJsonObject {
            put("p_post_id", postId)
            put("p_like", like)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("post_like", payload) ?: return null
        val answer = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        answer["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { lastError = it }
        if (answer["ok"]?.jsonPrimitive?.booleanOrNull == false) return null
        val likes = answer["likes"]?.jsonPrimitive?.longOrNull?.toInt() ?: return null
        val mineLike = answer["mine_like"]?.jsonPrimitive?.booleanOrNull == true
        return likes to mineLike
    }

    /**
     * 删一帖：只有作者本人能删（后端按身份码判断，认不出来就拒绝）。成功 true，其余 false，
     * 理由在 [lastError]。
     */
    suspend fun delete(id: Long): Boolean {
        lastError = null
        if (id <= 0L) return false
        val proof = rpc.token()
        val payload = buildJsonObject {
            put("p_id", id)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("post_delete", payload) ?: return false
        val answer = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return false
        answer["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { lastError = it }
        return answer["ok"]?.jsonPrimitive?.booleanOrNull == true
    }

    // ---------------------------------------------------------------- 解析

    /** 一行帖子：字段缺了就退回默认值，老回答仍然画得出来。 */
    private fun parsePost(element: JsonElement): CommunityPost {
        val o = element.jsonObject
        return CommunityPost(
            id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            excerpt = o["excerpt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            authorId = o["author_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            pinned = o["pinned"]?.jsonPrimitive?.booleanOrNull == true,
            locked = o["locked"]?.jsonPrimitive?.booleanOrNull == true,
            comments = o["comments"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            likes = o["likes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            mineLike = o["mine_like"]?.jsonPrimitive?.booleanOrNull == true,
            mine = o["mine"]?.jsonPrimitive?.booleanOrNull == true,
            category = o["category"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            // 后端发的是 `to_jsonb(tags)`，也就是 JSON 数组。老部署没有这个键、或者哪天形状变了，
            // 都退回空表：`as? JsonArray` 不抛，整条列表也就不会因为一个字段变 null。
            tags = (o["tags"] as? JsonArray)
                ?.mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }
                ?.filter { it.isNotBlank() }
                .orEmpty(),
        )
    }

    /** 一行回帖。 */
    private fun parseComment(element: JsonElement): PostComment {
        val o = element.jsonObject
        return PostComment(
            id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            authorId = o["author_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }
}
