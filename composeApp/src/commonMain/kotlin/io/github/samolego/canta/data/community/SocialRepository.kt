package io.github.samolego.canta.data.community

import io.github.samolego.canta.util.TelemetryConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** How long one backend call may take before the app treats it as never having arrived. */
private const val RPC_TIMEOUT_MS = 30_000L

/**
 * 一个人，以及这一台手机和他之间那两条关注边。
 *
 * [iFollow] / [followsMe] 是**看的方向**，不只是后端存的两列：拉「我关注的人」回来的一行，
 * 天然就是我关注了他；拉「关注我的人」回来的一行，天然就是他关注了我。后端要是自己也带了
 * 这两列（`i_follow` / `follows_me`），以后端的为准，因为那是它算出来的实时状态。
 *
 * [since] 是这条关系开始的那一刻，后端不给就是空串——空串是"不知道"，不是"刚刚"。
 */
data class SocialPerson(
    val id: String,
    val name: String,
    val iFollow: Boolean = false,
    val followsMe: Boolean = false,
    val since: String = "",
) {

    /** 界面上那一行字：有名字用名字，没有名字就把编号亮出来，两样都没有才是空。 */
    val label: String
        get() = name.ifBlank { if (id.isNotEmpty()) "#$id" else "" }
}

/**
 * 一个会话：私聊（[kind] = `dm`）或群聊（[kind] = `group`）。
 *
 * [peer] 是私聊对面的身份，[peerName] 是他今天用的名字，两个都是空的就是群聊；
 * [lastBody] / [lastAt] 是列表上那一行的预览，后端没给就是空串。
 * [members] 只有群聊有意义，私聊那一行后端给什么都原样搬过来，这里不替它猜。
 */
data class Conversation(
    val id: Long,
    val kind: String,
    val title: String,
    val peer: String = "",
    val peerName: String = "",
    val lastBody: String = "",
    val lastAt: String = "",
    val unread: Int = 0,
    val members: Int = 0,
) {

    /** 群聊与会话共用一张列表，界面靠这一位决定画头像还是画群标。 */
    val isGroup: Boolean
        get() = kind == "group"

    /**
     * 标题的兜底链：群名 → 私聊对面今天用的名字 → 私聊对面的编号 → `#会话号`。
     *
     * 会话号一定在，所以这个名字永远不为空：列表上不会出现一行没有标题的空白。
     */
    val label: String
        get() = title.ifBlank { peerName.ifBlank { peer.ifBlank { "#$id" } } }
}

/**
 * 一条消息。
 *
 * [sender] 是发消息那个身份，[senderName] 是他发这条时用的名字（历史消息保留当时的名字，
 * 后端改名后旧消息跟着变，这是后端的决定，这里只搬运）。
 */
data class ChatMessage(
    val id: Long,
    val sender: String,
    val senderName: String,
    val body: String,
    val createdAt: String,
) {

    /** 气泡上方那个署名；后端没给名字就退回编号。 */
    val label: String
        get() = senderName.ifBlank { "#$sender" }
}

/**
 * 社交那一层：关注、私聊、群聊。
 *
 * 和 [PlanRepository] 一样，这只是后端 RPC 的一层薄壳：一个方法一次调用，返回的东西在本地解析成
 * 上面的模型。**任何失败都不抛异常**——网络没通、服务端拒绝、返回的 JSON 长得不对，都退成
 * `null` 或空列表，页面照常画空态。
 *
 * 失败的原因留在 [lastError] / [lastFailureWasNetwork] 上，两者一起才能回答页面真正要问的那句
 * 「是我没开代理，还是服务端不要」：
 *
 *  * [lastFailureWasNetwork] 为 true：请求根本没出去（没有路由、宿主被墙、超时）。该提示用户去看
 *    网络或代理。
 *  * 否则 [lastError] 非空：服务端答了，但拒绝了这次调用（函数不存在、参数不合法、密钥不对），
 *    原文（截短）就放在这里。
 *  * 两个都干净：上一次调用成功了。
 */
class SocialRepository(
    private val client: HttpClient,
    private val baseUrl: String = TelemetryConfig.BASE_URL,
    private val apiKey: String = TelemetryConfig.PUBLISHABLE_KEY,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 上一次调用为什么失败：服务端拒绝时是它自己那句话（函数不存在、参数被否、密钥不对），
     * 网络失败时是 null，由 [lastFailureWasNetwork] 说明。上一次调用成功则也是 null。
     */
    var lastError: String? = null
        private set

    /**
     * 上一次调用是不是"根本没有回答"——没有路由、宿主被拦、超时——而不是"服务端拒绝了"。
     * 只有前者是网络或代理的问题，也只有前者值得让用户去查连接。
     */
    var lastFailureWasNetwork: Boolean = false
        private set

    /** What one call to the backend came back as. */
    private sealed interface Call {
        class Ok(val text: String) : Call
        class Refused(val reason: String) : Call
        object Unreachable : Call
    }

    // ------------------------------------------------------------------ 关注

    /**
     * 关注 [owner] 的人，一个一行；拉不到就是空列表。
     *
     * 回来的人都算 [SocialPerson.followsMe]：他出现在这个列表里，本来就是因为他关注了 [owner]。
     */
    suspend fun followersOf(owner: String): List<SocialPerson> =
        people("followers_of", owner, followsMe = true)

    /**
     * [owner] 关注的人，一个一行；拉不到就是空列表。
     *
     * 回来的人都算 [SocialPerson.iFollow]，理由同上，方向反过来。
     */
    suspend fun followingOf(owner: String): List<SocialPerson> =
        people("following_of", owner, iFollow = true)

    /**
     * 让 [actor] 关注或取关 [target]，返回这件事之后的关系状态。
     *
     * 成功时返回后端说的那一句（`following`），失败返回 false——false 是"现在不是关注状态"，
     * 与"写失败了"共用同一个返回值，所以页面要同时看 [lastError] / [lastFailureWasNetwork]
     * 才知道该不该重试。
     */
    suspend fun setFollow(actor: String, target: String, follow: Boolean): Boolean {
        if (actor.isEmpty() || target.isEmpty()) return false
        val body = buildJsonObject {
            put("p_actor", actor)
            put("p_target", target)
            put("p_follow", follow)
        }.toString()
        val text = rpc("follow_set", body) ?: return false
        return followingFrom(text) ?: false
    }

    // ------------------------------------------------------------------ 会话

    /** [owner] 参与的全部会话，后端排好的顺序原样返回；一次解析失败就是空列表。 */
    suspend fun conversationsFor(owner: String): List<Conversation> {
        if (owner.isEmpty()) return emptyList()
        val body = buildJsonObject { put("p_owner", owner) }.toString()
        val text = rpc("conversations_for", body) ?: return emptyList()
        return rowsOf(text).mapNotNull { row -> conversationOf(row) }
    }

    /**
     * [conversation] 里的一页消息，按后端给的顺序（旧在前，界面自己滚到底）。
     *
     * [offset] 是已经拿到的条数，[limit] 是这一页要多少条：翻历史就是拿下一个 offset 再问一次，
     * 两次请求之间来了新消息也不会把页错开。
     */
    suspend fun messagesFor(
        owner: String,
        conversation: Long,
        offset: Int = 0,
        limit: Int = 30,
    ): List<ChatMessage> {
        if (owner.isEmpty() || conversation <= 0L) return emptyList()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_offset", offset)
            put("p_limit", limit)
        }.toString()
        val text = rpc("messages_for", body) ?: return emptyList()
        return rowsOf(text).mapNotNull { row -> messageOf(row) }
    }

    /**
     * 往 [conversation] 发一条 [body]，返回后端落库的那一行（带 id 与时间戳）。
     *
     * 返回的对象只有 id 和 `created_at` 是从后端来的，`sender` 与 [body] 用调用方传进去的，
     * 所以界面可以先拿它直接把气泡画出来，不用等下一次 [messagesFor]。
     * 名字优先用后端回来的 `sender_name`；后端没给就留空，由 [ChatMessage.label] 退回编号。
     */
    suspend fun sendMessage(owner: String, conversation: Long, body: String): ChatMessage? {
        if (owner.isEmpty() || conversation <= 0L) return null
        val payload = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_body", body)
        }.toString()
        val text = rpc("message_send", payload) ?: return null
        val row = firstRowOf(text) ?: return null
        val id = row.longOf("id") ?: return null
        return ChatMessage(
            id = id,
            sender = owner,
            senderName = row.textOf("sender_name"),
            body = body,
            createdAt = row.textOf("created_at"),
        )
    }

    /**
     * 打开和 [peer] 的私聊，返回会话号——已经有了就把原来那个还回来，没有才新建。
     *
     * 失败返回 null。函数靠 `(owner, peer)` 去重，所以这个方法是幂等的，重复点同一个头像不会
     * 长出第二个会话。
     */
    suspend fun openDm(owner: String, peer: String): Long? {
        if (owner.isEmpty() || peer.isEmpty()) return null
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_peer", peer)
        }.toString()
        val text = rpc("dm_open", body) ?: return null
        return idFrom(text)
    }

    /** 建一个群，[members] 是除 [owner] 以外的人；返回新会话号，失败 null。 */
    suspend fun createGroup(owner: String, title: String, members: List<String>): Long? {
        if (owner.isEmpty()) return null
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_title", title)
            put("p_members", buildJsonArray { members.forEach { add(it) } })
        }.toString()
        val text = rpc("group_create", body) ?: return null
        return idFrom(text)
    }

    /**
     * 把 [conversation] 里 [owner] 的已读位置推到现在。
     *
     * 写失败什么也不发生：下一条消息之前再问一次就好，读回执丢了不是错。
     */
    suspend fun markRead(owner: String, conversation: Long) {
        if (owner.isEmpty() || conversation <= 0L) return
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
        }.toString()
        rpc("message_read", body)
    }

    // ------------------------------------------------------------------ 传输

    private suspend fun call(name: String, body: String): Call {
        val answered = runCatching {
            withTimeoutOrNull(RPC_TIMEOUT_MS) {
                val response = client.post(baseUrl + name) {
                    header("apikey", apiKey)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(body)
                }
                if (response.status.isSuccess()) {
                    Call.Ok(response.bodyAsText())
                } else {
                    // PostgREST answers a refusal with a JSON body carrying `message`; keep a short
                    // form of it so the page can say what the server did not like.
                    val text = runCatching { response.bodyAsText() }.getOrDefault("")
                    val message = runCatching {
                        Json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull().orEmpty().ifBlank { text.take(200) }
                    Call.Refused(message.ifBlank { "HTTP ${response.status.value}" })
                }
            }
        }
        // A call that threw, and a call that ran out of time, both mean nothing answered.
        return answered.getOrNull() ?: Call.Unreachable
    }

    private suspend fun rpc(name: String, body: String): String? = when (val result = call(name, body)) {
        is Call.Ok -> {
            lastFailureWasNetwork = false
            lastError = null
            result.text
        }
        is Call.Refused -> {
            lastFailureWasNetwork = false
            lastError = result.reason
            null
        }
        Call.Unreachable -> {
            lastFailureWasNetwork = true
            lastError = null
            null
        }
    }

    // ------------------------------------------------------------------ 解析

    /** [text] 解析成 JSON；不是合法 JSON 就是 null。 */
    private fun parse(text: String): JsonElement? = runCatching { json.parseToJsonElement(text) }.getOrNull()

    /**
     * [text] 里的每一行。
     *
     * PostgREST 把 `returns setof` 吐成数组，单个对象也当一行（`returns json` 那种）；
     * 标量不是行，落进空列表。某个元素不是对象就跳过它，不连累其他行。
     */
    private fun rowsOf(text: String): List<JsonObject> {
        val root = parse(text) ?: return emptyList()
        val rows = runCatching { root.jsonArray }.getOrNull()
            ?: listOfNotNull(runCatching { root.jsonObject }.getOrNull())
        return rows.mapNotNull { row -> runCatching { row.jsonObject }.getOrNull() }
    }

    /** [text] 里的第一行；标量给 null。 */
    private fun firstRowOf(text: String): JsonObject? {
        val root = parse(text) ?: return null
        runCatching { root.jsonObject }.getOrNull()?.let { return it }
        return runCatching { root.jsonArray.firstOrNull()?.jsonObject }.getOrNull()
    }

    /** [text] 那个标量（`returns bigint` 这类）；不是标量就给 null。 */
    private fun scalarOf(text: String): JsonPrimitive? {
        val root = parse(text) ?: return null
        return runCatching { root.jsonPrimitive }.getOrNull()
    }

    /** 字符串字段；缺字段、类型不对、`null` 都算空串。 */
    private fun JsonObject.textOf(key: String): String =
        runCatching { get(key)?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()

    /** 长整数字段；缺字段、类型不对都算 null。 */
    private fun JsonObject.longOf(key: String): Long? =
        runCatching { get(key)?.jsonPrimitive?.longOrNull }.getOrNull()

    /** 整数字段；后端可能把数字写成字符串，两种都认，认不出就是 [fallback]。 */
    private fun JsonObject.intOf(key: String, fallback: Int = 0): Int =
        runCatching { get(key)?.jsonPrimitive?.intOrNull }.getOrNull()
            ?: runCatching { get(key)?.jsonPrimitive?.longOrNull?.toInt() }.getOrNull()
            ?: fallback

    /** 布尔字段；缺字段、类型不对都算 null（调用方才知道该退成什么）。 */
    private fun JsonObject.flagOf(key: String): Boolean? =
        runCatching { get(key)?.jsonPrimitive?.booleanOrNull }.getOrNull()

    /** `follow_set` 的答案：对象里的 `following`，或者直接就是一个布尔。 */
    private fun followingFrom(text: String): Boolean? =
        firstRowOf(text)?.flagOf("following") ?: scalarOf(text)?.booleanOrNull

    /** `dm_open` / `group_create` 的答案：会话号，可能直接就是一个数。 */
    private fun idFrom(text: String): Long? {
        firstRowOf(text)?.let { row -> return row.longOf("conversation_id") ?: row.longOf("id") }
        return scalarOf(text)?.longOrNull
    }

    /** 一次关注列表调用：RPC 名 + `p_owner`，回来的人按方向补上那两条边的默认值。 */
    private suspend fun people(
        name: String,
        owner: String,
        iFollow: Boolean = false,
        followsMe: Boolean = false,
    ): List<SocialPerson> {
        if (owner.isEmpty()) return emptyList()
        val body = buildJsonObject { put("p_owner", owner) }.toString()
        val text = rpc(name, body) ?: return emptyList()
        return rowsOf(text).mapNotNull { row ->
            val id = row.textOf("id")
            val display = row.textOf("name")
            // 一个既没有编号也没有名字的行在界面上画不出来，也没法点，直接丢掉。
            if (id.isEmpty() && display.isEmpty()) return@mapNotNull null
            SocialPerson(
                id = id,
                name = display,
                iFollow = row.flagOf("i_follow") ?: iFollow,
                followsMe = row.flagOf("follows_me") ?: followsMe,
                since = row.textOf("since"),
            )
        }
    }

    /** 一行 `conversations_for`；连 id 都没有的行没法再问消息，丢掉。 */
    private fun conversationOf(row: JsonObject): Conversation? {
        val id = row.longOf("id") ?: return null
        return Conversation(
            id = id,
            kind = row.textOf("kind"),
            title = row.textOf("title"),
            peer = row.textOf("peer"),
            peerName = row.textOf("peer_name"),
            lastBody = row.textOf("last_body"),
            lastAt = row.textOf("last_at"),
            unread = row.intOf("unread"),
            members = row.intOf("members"),
        )
    }

    /** 一行 `messages_for`；连 id 都没有的行画不成气泡，丢掉。 */
    private fun messageOf(row: JsonObject): ChatMessage? {
        val id = row.longOf("id") ?: return null
        return ChatMessage(
            id = id,
            sender = row.textOf("sender"),
            senderName = row.textOf("sender_name"),
            body = row.textOf("body"),
            createdAt = row.textOf("created_at"),
        )
    }
}
