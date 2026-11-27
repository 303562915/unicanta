package io.github.samolego.canta.data.community

import kotlinx.serialization.Serializable
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
@Serializable
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
    /**
     * 分类色：`#RRGGBB`，由后端随帖子一起下发（Discourse 的分类都有自己的一格颜色）。
     *
     * 空串表示这一行没带色——老部署的 `posts_list` 不返回它，或者这个分类本来就没配色。
     * 页面这时退回按分类名哈希取兜底色（见 `categoryTint`），所以空的也能画。
     */
    val categoryColor: String = "",
    /** 标签，最多 5 个、每个最多 20 字（后端归整过）。老回答没有这一项时是空表。 */
    val tags: List<String> = emptyList(),
)

/**
 * 一条回帖。
 *
 * [replyToId] / [replyToAuthor] 是「这一楼在回哪一楼」：0L 表示不是回复某一楼。
 *
 * 为什么两个都有：id 用来在 `comments` 里找到被引用的那一层（画 fluxdo 那条「回复 #N · 名字」
 * 指示条、以及点它滚过去）；名字是发帖那一刻由后端抄下来的冗余值 —— 被引用的楼层后来可能被
 * 删掉，届时按 id 找不到，名字仍然在，指示条照样有字可写。
 *
 * 缺省退默认值：老后端（没有这两列）的回答里根本没有这两个键，解析回来就是 0L / ""，
 * 页面按"不是回复"画，不会因为一个字段缺失整条帖子画不出来。
 */
data class PostComment(
    val id: Long = 0L,
    val body: String = "",
    val author: String = "",
    val authorId: String = "",
    val createdAt: String = "",
    /** 引用的那一楼（同一帖子内）的 id；0 表示没有引用。 */
    val replyToId: Long = 0L,
    /** 被引用那一楼发帖时的署名；空串表示没有引用（或老后端没发这个字段）。 */
    val replyToAuthor: String = "",
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
     *
     * [replyToId] 大于 0 表示这一楼在回同一帖子里的第 [replyToId] 楼（引用回复）。后端会校验它
     * 确实属于这条帖子，对不上就当没带、照常收下 —— 引用是附加信息，不值得为它整条回帖失败。
     *
     * **只在 > 0 时才往 payload 里放这个键**（不是放一个 0 或 null）：老后端（没有
     * `p_reply_to_id`）遇到不认识的参数名会报 404 / PGRST202，那时候连普通回帖都发不出去。
     * 不放键等于"没有这个能力的老后端上，这个参数从来不存在"。
     *
     * 名字不在这里发：`reply_to_author` 由后端从被引用的那一楼读出来抄一份，客户端不参与
     * ——署名是别人的，让发帖人自报等于谁都能把别人的名字挂上去。
     */
    suspend fun comment(
        postId: Long,
        body: String,
        author: String,
        replyToId: Long = 0L,
    ): Int? {
        lastError = null
        if (postId <= 0L || body.isBlank()) return null
        val proof = rpc.token()
        val payload = buildJsonObject {
            put("p_post_id", postId)
            put("p_body", body.trim())
            put("p_author", author)
            if (proof.isNotEmpty()) put("p_token", proof)
            // 见上面注释：只在真有引用时发这个键，老后端才不会被一个陌生参数绊倒。
            if (replyToId > 0L) put("p_reply_to_id", replyToId)
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

    /**
     * 改一帖：只有作者本人能改，成功 true，其余 false，理由在 [lastError]。
     *
     * 与 [add] 的差别：署名可以改（[author] 会写回 `author` 列），但**身份码不动** ——
     * 帖子还是那个人写的。分类与标签同样原样发上去，归整由 `post_update` 做，规则和
     * `post_add` 一致（trim、每个 20 字、去重、最多 5 个）。
     *
     * 锁住的帖子后端会拒绝（`locked`），理由会回到 [lastError]，页面照实显示。
     */
    suspend fun update(
        id: Long,
        title: String,
        body: String,
        author: String,
        category: String = "",
        tags: List<String> = emptyList(),
    ): Boolean {
        lastError = null
        if (id <= 0L) return false
        // 标题空就在这里说清，别让它退到调用方的"检查一下网络"——那是另一件事。
        if (title.isBlank()) {
            lastError = "标题不能空"
            return false
        }
        val proof = rpc.token()
        val payload = buildJsonObject {
            put("p_id", id)
            put("p_title", title.trim())
            put("p_body", body)
            put("p_author", author)
            put("p_category", category.trim())
            putJsonArray("p_tags") { tags.forEach { tag -> add(JsonPrimitive(tag)) } }
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc.post("post_update", payload) ?: return false
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
            // 分类色：Discourse 那边每个分类自带一格 `#RRGGBB`。老部署不返回这个键，
            // 读不到就落空串，页面退回按名字哈希的兜底色（见 `categoryColorOf`）。
            categoryColor = o["category_color"]?.jsonPrimitive?.contentOrNull.orEmpty(),
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
            // 引用关系：老后端的回答里没有这两个键，`?.` 一路落空 → 0L / ""，按"不是回复"画。
            // `reply_to_id` 后端发的是 null（没引用）或数字，`longOrNull` 都接得住，null 也退回 0L。
            replyToId = o["reply_to_id"]?.jsonPrimitive?.longOrNull ?: 0L,
            replyToAuthor = o["reply_to_author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }
}
