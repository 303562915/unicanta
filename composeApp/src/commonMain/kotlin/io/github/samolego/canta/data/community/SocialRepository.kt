package io.github.samolego.canta.data.community

import io.github.samolego.canta.data.imagebed.ImageBed
import io.github.samolego.canta.util.TelemetryConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
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
 * 传一张图能等多久。
 *
 * 比 [RPC_TIMEOUT_MS] 宽一倍：那张图最多 2.5MB，一个普通 4G 上传要十几秒，30 秒在弱网里
 * 会把"其实还在传"判成失败，用户看到的是"图片没上传成"却已经传上去了。
 */
private const val IMAGE_UPLOAD_TIMEOUT_MS = 60_000L

/**
 * 翻译走 MyMemory 的公开接口：它不要 key、每天 5 000 字符（带邮箱 50 000），够「按条翻」用。
 *
 * 之所以不放到服务端（`pg_net` + cron 那一套）：翻译是**一个人点一下**的动作，不是群发事件。
 * 放服务端要多一张表、一个队列、一条 cron 和一次轮询，而客户端本来就已经拿着这段文字。
 * 代价是这段话会经过第三方——它和「发给 AI」是同一类取舍，页面上有明确的一次点击。
 */
private const val TRANSLATE_ENDPOINT = "https://api.mymemory.translated.net/get"

/**
 * 这段字大概是什么语言：有汉字就当中文，其余当英文。
 *
 * 只用来决定「往哪个方向翻」，猜错的代价是翻出来还是原文，不是数据错——所以不引语言识别库。
 */
private fun guessLanguageOf(text: String): String =
    if (text.any { it in '\u4E00'..'\u9FFF' }) "zh-CN" else "en"

/**
 * 一个人，以及这一台手机和他之间那两条关注边。
 *
 * [iFollow] / [followsMe] 是**看的方向**，不只是后端存的两列：拉「我关注的人」回来的一行，
 * 天然就是我关注了他；拉「关注我的人」回来的一行，天然就是他关注了我。后端要是自己也带了
 * 这两列（`i_follow` / `follows_me`），以后端的为准，因为那是它算出来的实时状态。
 *
 * [since] 是这条关系开始的那一刻，后端不给就是空串——空串是"不知道"，不是"刚刚"。
 */
@Serializable
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
 * [pinned] 是这一行被置顶了没有，与后端 `conversations_for` 的排序无关：怎么排是后端的事，
 * 这里只搬运"是不是置顶"，缺字段就是没置顶。
 */
@Serializable
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
    val pinned: Boolean = false,
    /**
     * 这个人给这个会话关了提醒。存在自己的成员行上，所以同一群里每个人可以不一样。
     *
     * 界面据此在行尾画一个静音小标；**不**改变未读数的计算——静音是"不打扰"，
     * 不是"这条不重要"，红点仍然该出现。
     */
    val muted: Boolean = false,
    /**
     * 这个人把这个群折起来了。折叠的群不占消息页的主列表，收进一个「折叠的群聊」入口里。
     *
     * 和 [pinned] 是两件事：置顶是「这件事我先看」，折叠是「这件事平时不用看」。
     * 两个都开着时以置顶为准——人把它拎到最上面，说明现在要看它。
     */
    val folded: Boolean = false,
    /**
     * 这个会话里「别人 @ 我、我还没读」的条数。消息页据此在摘要前挂一个「[有人@我]」。
     *
     * 判据和 [unread] 完全一致（都比自己的 `last_read_at`），所以不会出现"未读 0 条却挂着 @ 提示"。
     */
    val unreadMentions: Int = 0,
    /**
     * 最后一条是不是**别人** @ 我的。后台轮询据此把通知标题写成「某某 @了你」，
     * 而不是只报"有新消息"——被点名和群里闲聊，是两种该不该马上看的判断。
     */
    val lastMentionsMe: Boolean = false,
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
 *
 * [removed] 是被删掉的那一条：后端保留占位（这样上下文的时序不乱），页面据此画"已撤回"，
 * 而不是把气泡整个从列表里拿掉——拿掉会让上面那句话失去被回复的对象。
 *
 * [kind] 是这一条的类型：`"text"` 是文字，`"image"` 是图片，那时 [body] 里放的是 storage 里的
 * 对象路径（不是 URL）。认不出来的值原样保留，页面按"不认识"处理即可——不回退成 `"text"`，
 * 免得把一张图当文字画出来。缺失才是 `"text"`。
 */

/**
 * 群聊 AI 发言时用的 `sender` 值。
 *
 * 它**不是** `canta_users` 里的号码，别拿它去查人：查不到，`social_name` 会返回空串。
 * 服务端写这一条时已经把 `sender_name` 填成「群聊AI」，这个常量只在那一列意外为空时兜底。
 */
const val AiSender: String = "ai"

/**
 * `kind == "share"` 的那条消息里装的东西：一个 App 或一个方案。
 *
 * [target] 是包名（`kind == "app"`）或方案 id 的十进制字符串（`kind == "plan"`）；两种卡片
 * 打开方式不同，所以类型和目标是分开的两个字段，而不是一坨拼好的 URL——拼好了也还要再拆开。
 *
 * [title] / [note] 是**发出时**的那份措辞：对方改了应用名或方案名，已经发出去的这张卡不该
 * 跟着变，就像转发出去的一条消息不会因为原消息被编辑而变化。
 */
@Serializable
data class ShareCard(
    /** `"app"` 或 `"plan"`；后端只放行这两种。 */
    val kind: String,
    val target: String,
    val title: String = "",
    val note: String = "",
) {

    /** 方案那条要走 `plan/{id}`，而路由要的是数字：不是数字就当没有，别拼出一个打不开的路由。 */
    val planId: Long?
        get() = target.toLongOrNull()?.takeIf { it > 0L }
}

/**
 * `kind == "card"` 的那条消息里装的东西：一个人的名片。
 *
 * 名字是发出那一刻的名字（后端写进去的），和真名片一样，之后改了名这张不变。
 */
@Serializable
data class PersonCard(
    val owner: String,
    val name: String = "",
) {

    /** 卡片上那行名字；没有就用编号，总比空着强。 */
    val label: String
        get() = name.ifBlank { if (owner.isNotEmpty()) "#$owner" else "" }
}


@Serializable
data class ChatMessage(
    val id: Long,
    val sender: String,
    val senderName: String,
    val body: String,
    val createdAt: String,
    val removed: Boolean = false,
    val kind: String = "text",
    /**
     * 这条消息点了谁的名字，存的是身份号码而不是正文里那几个字：人改了名之后字就对不上了，
     * 而「谁被点到」要的是人。后端只收本会话的成员，也去掉了自己。
     */
    val mentions: List<String> = emptyList(),
    /**
     * 这条消息引用了哪一条，没引用就是 null。
     *
     * 只存 id 不存副本：引用块里的摘要（[replyBody]）是后端每次读的时候现取的，所以原消息
     * 后来被撤回，那块摘要也会跟着变空，不会留着一段已经不存在的话。
     */
    val replyTo: Long? = null,
    /** 被引用那条的正文前 60 字；原消息撤回过了就是空串。 */
    val replyBody: String = "",
    /** 被引用那条是谁说的，给引用块抬头用。 */
    val replySenderName: String = "",
    /**
     * `kind == "share"` 时解析出来的卡片；别的类型、或者 body 的 JSON 坏了就是 null。
     *
     * 解析一次放在这儿而不是每帧在界面里解：一个会话翻上去几十条，每条每帧都解一遍 JSON
     * 没有意义，而且解析失败要在同一个地方兜住。
     */
    val share: ShareCard? = null,
    /** `kind == "card"` 时解析出来的名片；别的类型是 null。 */
    val card: PersonCard? = null,
) {

    /**
     * 气泡上方那个署名；后端没给名字就退回编号。
     *
     * `sender = "ai"` 那条单独认一下：它不是 `canta_users` 里的人，编号前面挂个 `#` 会显示成
     * `#ai`，看着像谁漏填了名字。后端正常会给 `sender_name = "群聊AI"`，这里只是兜底——
     * 比如某次查询没带上那一列。
     */
    val label: String
        get() = senderName.ifBlank {
            if (sender == AiSender) "群聊AI" else "#$sender"
        }
}

/**
 * 群里的一个人：身份 [owner] 和他今天用的名字 [name]。
 *
 * 名字是群详情这一次调用时的名字，改了名要重新拉一次详情才看得到；[owner] 才是认人的那一位。
 */
@Serializable
data class ConversationMember(
    val owner: String,
    val name: String,
) {

    /** 成员列表上那一行字；没名字就亮编号。 */
    val label: String
        get() = name.ifBlank { if (owner.isNotEmpty()) "#$owner" else "" }
}

/**
 * 一个会话的详情：谁在里面、群名是什么、什么时候建的。
 *
 * [members] 在私聊里也有，就是那两个人——后端按会话成员表直出，不区分 kind。
 * 一个成员都没有时是空列表，不是 null：空群也是群，界面自己决定怎么画。
 */
@Serializable
data class ConversationDetail(
    val id: Long,
    val kind: String,
    val title: String,
    val members: List<ConversationMember>,
    val createdAt: String = "",
    /** 群公告，空串表示没设过。只有建群的人能改（见 `group_set_announcement`）。 */
    val announcement: String = "",
    /**
     * 建群的人的身份号码。资料页拿它和 [ConversationDetail] 的访客身份比对，决定显示不显示
     * 「编辑公告」「踢人」——**这只是画不画按钮**，真正的裁决在后端：自报身份不可信。
     */
    val createdBy: String = "",
    /** 群简介，空串表示没填过。和公告一样只有建群的人能改（见 `group_set_intro`）。 */
    val intro: String = "",
    /**
     * 我在这个群里的昵称，空串表示没设过（那就用全局名字）。
     *
     * 只影响**显示**，不影响认人：认的永远是 [ConversationMember.owner]。
     */
    val myNickname: String = "",
    /** 我把这个群折起来了没有。 */
    val myFolded: Boolean = false,
    /**
     * 这个群允不允许成员发图片。
     *
     * 开关在群设置里，只有建群的人能改（`group_set_allow_image`）。关着的时候后端也会拒成员
     * 发图，所以界面按它隐藏图片入口不是"藏起来不让点"，而是把后端那条裁决先说给人听。
     * 私聊没有这个开关，恒为 true 的那一侧由调用方判（见 [isGroup]）。
     */
    val allowImage: Boolean = false,
    /**
     * 群里**一共**多少人。
     *
     * [members] 只回前 200 个（头像墙够用，几千人的群不该一次把整张表搬给客户端），
     * 所以人数要用这个数，不能用 `members.size`。旧后端没有这个字段时是 0，
     * 由 [memberTotal] 退回 `members.size`。
     */
    val memberCount: Int = 0,
) {

    /** 人数：优先用后端给的总数，拿不到就数一数手里的成员。 */
    val memberTotal: Int
        get() = if (memberCount > 0) memberCount else members.size

    /** 与 [Conversation.isGroup] 同一判据：`kind == "group"`。 */
    val isGroup: Boolean
        get() = kind == "group"

    /** 详情页的标题：没群名就叫「群聊」，不把会话号当名字摆在标题上。 */
    val label: String
        get() = title.ifBlank { "群聊" }

    /** [viewer] 是不是这个群的建群者；私聊永远 false（私聊没有公告也没有人可踢）。 */
    fun isOwner(viewer: String): Boolean = isGroup && createdBy.isNotEmpty() && createdBy == viewer
}

/**
 * 会话的实时状态：别人读到哪一条、谁正在打字。
 *
 * 两样东西都来自 `conversation_live`，一次调用一起回来——它们的刷新节奏一样（几秒一次），
 * 分成两个函数只会让聊天页开两条同样的轮询。
 *
 * [reads] 是「成员号码 → 他的读位置」，不含自己；[typing] 是此刻正在打字的人的名字，
 * 已经是给人看的字符串（后端拼好的 `private.social_name` 兜底）。
 */
data class LivePresence(
    val reads: Map<String, String> = emptyMap(),
    val typing: List<String> = emptyList(),
) {

    /** 正在打字的人那一行字：一个人说名字，几个人说人数，没人就是空串。 */
    val typingLine: String
        get() = when (typing.size) {
            0 -> ""
            1 -> "${typing.first()} 正在输入…"
            else -> "${typing.first()} 等 ${typing.size} 人正在输入…"
        }
}

/**
 * 搜索命中的一条消息。
 *
 * 比 [ChatMessage] 少一份会话内的上下文、多一份"它在哪个会话里"：[conversationId] 用来跳转，
 * [conversationTitle] 是结果行上那句"来自某某群"。正文截断由后端做（本来就是整条），这里不再切。
 */
@Serializable
data class MessageHit(
    val id: Long,
    val conversationId: Long,
    val conversationTitle: String,
    val conversationKind: String,
    val sender: String,
    val senderName: String,
    val body: String,
    val createdAt: String,
) {

    /** 结果行的抬头：谁 + 来自哪里。个人会话（私聊）没有名字，直接写"私聊"。 */
    val where: String
        get() = if (conversationKind == "group") conversationTitle.ifBlank { "群聊" } else "私聊"
}

/**
 * 一个人的主页数字：他上传了多少方案、投了多少票、写了多少评论、被多少人关注、关注了多少人。
 *
 * **名字是 `SocialUserProfile` 而不是 `UserProfile`**：同包 [PlanRepository] 里已经有一个
 * `UserProfile`（token / userId / name，那是"我自己的身份"），两个同名顶层类在同一包里是重定义，
 * 所以这一位带上前缀。这里的 [owner] 是**别人**的身份字符串，和那边的 `userId` 不是一回事。
 *
 * [iFollow] / [followsMe] 是"我"（[userProfile] 的 viewer）和他之间的两条边，viewer 为空时后端
 * 给的就是 false。数字字段缺失或类型不对一律退 0，[since] 缺失是空串；[bio] 是他自己写的一句话，
 * 没写过就是空串。
 */
@Serializable
data class SocialUserProfile(
    val owner: String,
    val name: String,
    val plans: Int,
    val votes: Int,
    val comments: Int,
    val followers: Int,
    val following: Int,
    val iFollow: Boolean,
    val followsMe: Boolean,
    val since: String = "",
    val bio: String = "",
    /**
     * 上一次从哪来，后端算好的省/州名（「广东」「California」这种），空串表示还没查出来。
     *
     * 只到省一级、不存原始 IP：这是给别人看的一行灰字，不是定位。
     */
    val ipRegion: String = "",
) {

    /** 主页标题那一行字：有名字用名字，没有就亮编号。 */
    val label: String
        get() = name.ifBlank { if (owner.isNotEmpty()) "#$owner" else "" }
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
    /**
     * 这台手机的身份凭据。写调用会把它一起交给后端，后端据此认出是谁在写，而不是采信
     * 每个方法第一个参数里那个谁都能填的号码。
     *
     * 默认值拿不到凭据（空串），那时一个 `p_token` 也不发，后端照旧按自报的号码处理——灰度
     * 期间新旧服务端、新旧客户端四种组合都能跑，撑住这件事的就是这个默认值。构造函数不提它
     * 也不会编译失败，接上凭据是上层一行的事。
     */
    private val identityToken: suspend () -> String = { "" },
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 这一次写调用要带上的凭据；取不到就是空串。
     *
     * 凭据出错不该让一次发消息整个失败，所以 [identityToken] 抛什么都吞掉：少了凭据最坏只是
     * 回到"信自报号码"的老样子，和今天完全一样。
     */
    private suspend fun callerToken(): String =
        runCatching { identityToken() }.getOrDefault("").trim()

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

    /** 二进制下载的三种结局，与 [Call] 一一对应；拆开是因为成功的那个还带着字节。 */
    private sealed interface Blob {
        class Ok(val bytes: ByteArray) : Blob
        class Refused(val reason: String) : Blob
        object Unreachable : Blob
    }

    /**
     * 把一次**非 rpc** 请求的结果记进 [lastError] / [lastFailureWasNetwork]，成功返回 true。
     *
     * 这就是 [rpc] 里那三个分支的等价物，只是 [rpc] 还要把正文交出去，而 storage 的写只关心
     * 成没成。两处的语义必须一模一样，否则页面在"上传失败"和"发消息失败"上会得到两种判断。
     */
    private fun record(result: Call): Boolean = when (result) {
        is Call.Ok -> {
            lastFailureWasNetwork = false
            lastError = null
            BackendHealth.reportReachable()
            true
        }
        is Call.Refused -> {
            lastFailureWasNetwork = false
            lastError = result.reason
            BackendHealth.reportReachable()
            false
        }
        Call.Unreachable -> {
            lastFailureWasNetwork = true
            lastError = null
            BackendHealth.reportUnreachable()
            false
        }
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
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_actor", actor)
            put("p_target", target)
            put("p_follow", follow)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("follow_set", body) ?: return false
        return followingFrom(text) ?: false
    }

    /**
     * [owner] 的主页数字，带上看它的那个人 [viewer] 与 [owner] 之间的两条关注边。
     *
     * [viewer] 为空也照问：那时 [SocialUserProfile.iFollow] / [SocialUserProfile.followsMe] 都是
     * false，页面按"没登录"处理。返回里没有 `owner` 的行（空对象、或者后端拿不到这个人）就是
     * null——那和"拉失败"在页面上是同一件事：画不出主页。
     */
    suspend fun userProfile(owner: String, viewer: String = ""): SocialUserProfile? {
        if (owner.isEmpty()) return null
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_viewer", viewer)
        }.toString()
        val text = rpc("user_profile", body) ?: return null
        val row = firstRowOf(text) ?: return null
        val who = row.textOf("owner")
        if (who.isEmpty()) return null
        return SocialUserProfile(
            owner = who,
            name = row.textOf("name"),
            plans = row.intOf("plans"),
            votes = row.intOf("votes"),
            comments = row.intOf("comments"),
            followers = row.intOf("followers"),
            following = row.intOf("following"),
            iFollow = row.flagOf("i_follow") ?: false,
            followsMe = row.flagOf("follows_me") ?: false,
            // 后端 `user_profile` 返回的键叫 `created_at`，没有 `since`：一直读 `since` 的结果
            // 是这一行永远是空串。两层都认，先新名后旧名，和 [personOf] 那边一致。
            since = row.textOf("created_at").ifBlank { row.textOf("since") },
            bio = row.textOf("bio"),
            // 后端还没查出来、或者这个人的请求没带回 IP，这里就是空串，页面显示「未知」。
            ipRegion = row.textOf("ip_region"),
        )
    }

    /**
     * 改自己的签名，返回这件事成没成。
     *
     * 只有本人能改；后端拒绝时返回 false，它那句话写进 [lastError]，页面据此解释
     * "为什么没改上"，而不是笼统报错。
     */
    suspend fun setBio(owner: String, bio: String): Boolean {
        if (owner.isEmpty()) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_bio", bio)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("profile_set_bio", body) ?: return false
        return booleanFrom(text, "profile_set_bio") ?: false
    }

    // ------------------------------------------------------------------ 会话

    /**
     * [owner] 参与的全部会话，后端排好的顺序原样返回；一次解析失败就是空列表。
     *
     * [since] 非空时只拿「自那个时刻以来有动静」的那几个会话：后端按
     * `conversations.last_message_at >= p_since` 过滤，所以拿回来的是增量，调用方得按会话号
     * upsert 进本地列表，整份替换等于把没动静的那几十行全删了。
     *
     * [since] 为空就一个 `p_since` 键都不发（同本文件里 `p_mentions` / `p_reply_to` 的写法）：
     * 后端默认值是 null，走全量，和这一版之前的行为逐字一致。服务端还没上线新签名时，不带这个键
     * 的请求也照样命中老函数——灰度的顺序是服务端先、客户端后（见 AGENTS 10.5）。
     */
    suspend fun conversationsFor(owner: String, since: String? = null): List<Conversation> {
        if (owner.isEmpty()) return emptyList()
        val body = buildJsonObject {
            put("p_owner", owner)
            if (!since.isNullOrBlank()) put("p_since", since)
        }.toString()
        val text = rpc("conversations_for", body) ?: return emptyList()
        return rowsOf(text).mapNotNull { row -> conversationOf(row) }
    }

    /**
     * [conversation] 的详情：群名、成员表、创建时间。
     *
     * 空对象、以及没有 `id` 的返回都是 null——那不是"一个空会话"，是"这个会话不存在（或者不是
     * 我的）"，页面该退回列表而不是画一个空壳。
     */
    suspend fun conversationDetail(owner: String, conversation: Long): ConversationDetail? {
        if (owner.isEmpty() || conversation <= 0L) return null
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
        }.toString()
        val text = rpc("conversation_detail", body) ?: return null
        val row = firstRowOf(text) ?: return null
        val id = row.longOf("id") ?: return null
        return ConversationDetail(
            id = id,
            kind = row.textOf("kind"),
            title = row.textOf("title"),
            members = membersOf(row),
            createdAt = row.textOf("created_at"),
            announcement = row.textOf("announcement"),
            createdBy = row.textOf("created_by"),
            intro = row.textOf("intro"),
            myNickname = row.textOf("my_nickname"),
            myFolded = row.flagOf("my_folded") ?: false,
            allowImage = row.flagOf("allow_image") ?: false,
            memberCount = row.intOf("member_count"),
        )
    }

    /**
     * 这个会话现在的状态：别人读到哪、谁正在打字。
     *
     * 聊天页每几秒问一次（见 `ChatScreen` 的 `ChatPresencePollMs`）。失败回答 null，调用方保留
     * 上一次的结果——"这一次没问到"不该让『正在输入』突然消失。
     */
    suspend fun conversationLive(owner: String, conversation: Long): LivePresence? {
        if (owner.isEmpty() || conversation <= 0L) return null
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("conversation_live", body) ?: return null
        val row = firstRowOf(text) ?: return null

        val reads = mutableMapOf<String, String>()
        runCatching { row["reads"]?.jsonArray }.getOrNull()?.forEach { element ->
            val entry = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
            val member = entry.textOf("member")
            if (member.isNotEmpty()) reads[member] = entry.textOf("last_read_at")
        }

        val typing = buildList {
            runCatching { row["typing"]?.jsonArray }.getOrNull()?.forEach { element ->
                val entry = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
                val display = entry.textOf("name").ifBlank { entry.textOf("member") }
                if (display.isNotEmpty()) add(display)
            }
        }

        return LivePresence(reads = reads, typing = typing)
    }

    /**
     * 告诉后端「我正在打字」。客户端 4 秒最多发一次（见聊天页），后端再挡一层 2 秒。
     *
     * 失败什么都不做：一条心跳丢了，下一键还会再发一次。
     */
    suspend fun markTyping(owner: String, conversation: Long) {
        if (owner.isEmpty() || conversation <= 0L) return
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        rpc("conversation_typing", body)
    }

    /**
     * 把一段话翻成 [target]（`zh-CN` / `en` 这种 BCP-47 短码），失败回答 null。
     *
     * 走 MyMemory 的公开接口，不需要 key：`GET /get?q=…&langpair=zh-CN|en`，答案在
     * `responseData.translatedText`。它自己的错误也塞在这个字段里（"QUERY LENGTH LIMIT"
     * 之类），所以拿回来还要看一眼 `responseStatus`。
     */
    suspend fun translate(text: String, target: String): String? {
        val source = guessLanguageOf(text)
        val trimmed = text.trim().take(480)
        if (trimmed.isEmpty() || source == target) return null
        val url = buildString {
            append(TRANSLATE_ENDPOINT)
            append("?q=").append(trimmed.encodeURLParameter())
            append("&langpair=").append("$source|$target".encodeURLParameter())
        }
        val answered = runCatching {
            withTimeoutOrNull(RPC_TIMEOUT_MS) {
                val response = client.get(url) { header("Accept", "application/json") }
                if (!response.status.isSuccess()) return@withTimeoutOrNull null
                response.bodyAsText()
            }
        }.getOrNull() ?: return null
        val root = runCatching { json.parseToJsonElement(answered).jsonObject }.getOrNull() ?: return null
        val status = root["responseStatus"]?.jsonPrimitive?.intOrNull ?: 200
        if (status != 200) return null
        return root["responseData"]?.jsonObject?.textOf("translatedText")
            ?.takeIf { it.isNotBlank() && !it.startsWith("QUERY LENGTH LIMIT", ignoreCase = true) }
    }

    /**
     * 问一次「群聊 AI 的回答写好没有」，返回这次写回了几条（0 = 还没回来，再等等）。
     *
     * 服务端只处理这个会话、只处理未完成的请求，而且写回之前会先抢占那一条，所以两秒问一次
     * 不会问出两条回答。失败也返回 0：等不到就当没有，页面不必区别「没回来」和「问失败」。
     */
    suspend fun pollAiReply(owner: String, conversation: Long): Int {
        if (owner.isEmpty() || conversation <= 0L) return 0
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("ai_poll", body) ?: return 0
        return intFrom(text, "ai_poll") ?: 0
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
     * [conversation] 里比 [beforeId] 更旧的一页消息，解析与 [messagesFor] 完全一致。
     *
     * 往上翻历史用这一条：拿当前看到的最旧那条的 id 当锚，新消息再多也不会把页错开
     * （用 offset 翻历史会被刚到的消息挤动，所以有这两个入口）。
     * [beforeId] 传 0 就是"从最新一条往前"，后端按同一件事处理。
     */
    suspend fun messagesBefore(
        owner: String,
        conversation: Long,
        beforeId: Long,
        limit: Int = 30,
    ): List<ChatMessage> {
        if (owner.isEmpty() || conversation <= 0L) return emptyList()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_before_id", beforeId)
            put("p_limit", limit)
        }.toString()
        val text = rpc("messages_before", body) ?: return emptyList()
        return rowsOf(text).mapNotNull { row -> messageOf(row) }
    }

    /**
     * 往 [conversation] 发一条 [body]，返回后端落库的那一行（带 id 与时间戳）。
     *
     * 返回的对象只有 id 和 `created_at` 是从后端来的，`sender` 与 [body] 用调用方传进去的，
     * 所以界面可以先拿它直接把气泡画出来，不用等下一次 [messagesFor]。
     * 名字优先用后端回来的 `sender_name`；后端没给就留空，由 [ChatMessage.label] 退回编号。
     */
    suspend fun sendMessage(
        owner: String,
        conversation: Long,
        body: String,
        mentions: List<String> = emptyList(),
        replyTo: Long? = null,
    ): ChatMessage? {
        if (owner.isEmpty() || conversation <= 0L) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_body", body)
            if (proof.isNotEmpty()) put("p_token", proof)
            // 空名单就不发这个键：后端默认值是空数组，少传一个键比传 [] 更省事，
            // 也让"这条根本没 @ 人"和"@ 了但后端筛掉"在请求里长得不一样。
            if (mentions.isNotEmpty()) {
                put("p_mentions", buildJsonArray { mentions.forEach { add(it) } })
            }
            // 同理：没引用就不发，后端拿到 null 就是"不引用"。
            if (replyTo != null && replyTo > 0L) put("p_reply_to", replyTo)
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
            // 后端会筛掉非成员和自己，所以本机这一条也用后端回来的名单，不是传进去那个。
            mentions = row.stringsOf("mentions"),
            replyTo = replyTo,
            // 刚发出去这条的引用摘要后端没回（它只回 id），界面那一格先空着，
            // 下一次 messagesFor 会带着真摘要回来——比在本地拼一份可能对不上的强。
        )
    }

    /**
     * 把一张已经传上去的图登记成一条消息，返回后端落库的那一行。
     *
     * [path] 是**图片地址**：图床的完整 URL（`https://.../file/...`），或者老消息那种 `chat-images`
     * bucket 里的对象路径。后端只是把它写进 `body`、不理解它，所以两种都收；读回来时
     * [downloadChatImage] 按 `http` 前缀分流。
     *
     * 顺序是死的：先上传（[uploadChatImageToImageBed]）拿到地址，再调这一条。返回的 [ChatMessage]
     * 里 `kind = "image"`，而它的 `body`（[ChatMessage.body]）里放的是地址而不是文字，气泡照着它
     * 直接画，不用等下一次 [messagesFor]。
     *
     * 失败返回 null；此时图可能已经传上去了（上传成功、登记失败）。重试会再传一张新的——id 里带
     * 时间戳和随机数，不会盖掉上一条消息用的那张。
     */
    suspend fun sendImageMessage(owner: String, conversation: Long, path: String): ChatMessage? {
        if (owner.isEmpty() || conversation <= 0L || path.isEmpty()) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_path", path)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("message_send_image", payload) ?: return null
        val row = firstRowOf(text) ?: return null
        val id = row.longOf("id") ?: return null
        return ChatMessage(
            id = id,
            sender = owner,
            senderName = "",
            body = path,
            createdAt = row.textOf("created_at"),
            kind = "image",
        )
    }

    /**
     * 分享一个 App（[kind] = "app"，[target] 是包名）或一个方案（[kind] = "plan"，[target] 是方案号）
     * 到 [conversation]，返回后端落库的那一行。
     *
     * 和 [sendMessage] 分开是有意的：那个要求正文非空、并把 body 当"人说的话"。卡片不是话，
     * 它有三个字段和一个类型，所以服务端也另开了 `message_send_share`。
     */
    suspend fun sendShare(
        owner: String,
        conversation: Long,
        kind: String,
        target: String,
        title: String,
        note: String = "",
    ): ChatMessage? {
        if (owner.isEmpty() || conversation <= 0L || target.isBlank()) return null
        if (kind != "app" && kind != "plan") return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_kind", kind)
            put("p_target", target)
            put("p_title", title)
            put("p_note", note)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("message_send_share", payload) ?: return null
        val row = firstRowOf(text) ?: return null
        val id = row.longOf("id") ?: return null
        return ChatMessage(
            id = id,
            sender = owner,
            senderName = "",
            body = "",
            createdAt = row.textOf("created_at"),
            kind = "share",
            share = ShareCard(kind = kind, target = target, title = title, note = note),
        )
    }

    /**
     * 把 [target] 这个人的名片发到 [conversation]。
     *
     * 名字不由调用方给：后端按号码去查，查不到就不发。这样卡片上的名字和那一刻的他是同一个人，
     * 而不是"发的人以为他是谁"。
     */
    suspend fun sendCard(owner: String, conversation: Long, target: String): ChatMessage? {
        if (owner.isEmpty() || conversation <= 0L || target.isBlank()) return null
        val proof = callerToken()
        val payload = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_target", target)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("message_send_card", payload) ?: return null
        val row = firstRowOf(text) ?: return null
        val id = row.longOf("id") ?: return null
        return ChatMessage(
            id = id,
            sender = owner,
            senderName = "",
            body = "",
            createdAt = row.textOf("created_at"),
            kind = "card",
            // 名字暂时空着：这一条要等重拉才准。手里的列表已经在用它，空名字会退回编号，
            // 比编一个名字诚实。
            card = PersonCard(owner = target),
        )
    }

    /**
     * 删掉 [messageId] 这条消息，返回这件事成没成。
     *
     * 删是"标记撤回"，不是把行抹掉：所以这个方法成功之后，`messages_for` 里那条还在，
     * 只是 [ChatMessage.removed] 变成 true，气泡该画成"已撤回"。这笔账由后端记，
     * 本对象不本地改列表——重拉一次才是准的。
     */
    suspend fun deleteMessage(owner: String, messageId: Long): Boolean {
        if (owner.isEmpty() || messageId <= 0L) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_message", messageId)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("message_delete", body) ?: return false
        return booleanFrom(text, "message_delete") ?: false
    }

    /**
     * 只在自己这一侧删掉一条消息（对所有人还在）。
     *
     * 和 [deleteMessage] 是两件事：那个把 `removed` 置真、所有人都看不见正文；这个只在
     * `private.message_hidden` 里记一行"这个人不想再看到"，别人那里原样不动。
     *
     * 成功之后本地要把这条从列表里摘掉，而不是标成已撤回——它没有"撤回"这个说法。
     */
    suspend fun hideMessage(owner: String, messageId: Long): Boolean {
        if (owner.isEmpty() || messageId <= 0L) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_message", messageId)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("message_hide", body) ?: return false
        return booleanFrom(text, "message_hide") ?: false
    }

    /**
     * 把 [messageId] 转发到 [conversation]，返回是否成功。
     *
     * 后端会把正文和 kind 复制一条新的过去，发的人是本机这位，所以转发之后两边都有这条消息。
     * 被 @ 的名单和引用不跟着走：那些都只对原会话成立。
     */
    suspend fun forwardMessage(owner: String, messageId: Long, conversation: Long): Boolean {
        if (owner.isEmpty() || messageId <= 0L || conversation <= 0L) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_message", messageId)
            put("p_conversation", conversation)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("message_forward", body) ?: return false
        // 成功时后端回的是一个对象 `{id, created_at}`，失败回 `null` 或者根本没响应。
        return firstRowOf(text)?.longOf("id") != null
    }

    /**
     * 打开和 [peer] 的私聊，返回会话号——已经有了就把原来那个还回来，没有才新建。
     *
     * 失败返回 null。函数靠 `(owner, peer)` 去重，所以这个方法是幂等的，重复点同一个头像不会
     * 长出第二个会话。
     */
    suspend fun openDm(owner: String, peer: String): Long? {
        if (owner.isEmpty() || peer.isEmpty()) return null
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_peer", peer)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("dm_open", body) ?: return null
        return idFrom(text)
    }

    /** 建一个群，[members] 是除 [owner] 以外的人；返回新会话号，失败 null。 */
    suspend fun createGroup(owner: String, title: String, members: List<String>): Long? {
        if (owner.isEmpty()) return null
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_title", title)
            put("p_members", buildJsonArray { members.forEach { add(it) } })
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("group_create", body) ?: return null
        return idFrom(text)
    }

    /**
     * 往已有的群里加人，返回加完之后群里有几个人。
     *
     * 加进去的人后端自己会去掉重复的，所以这个方法也是幂等的：同一个 [members] 再发一次，
     * 人数不变。失败返回 null——群至少有群主，人数不会真的是 0，所以 null 不会被一个真值盖住。
     */
    suspend fun addGroupMembers(owner: String, conversation: Long, members: List<String>): Int? {
        if (owner.isEmpty() || conversation <= 0L) return null
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_members", buildJsonArray { members.forEach { add(it) } })
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("group_add_members", body) ?: return null
        return intFrom(text, "members")
    }

    /**
     * 改群名，返回这件事成没成。
     *
     * 只有群主能改，后端拒绝时返回 false 并把它那句话写进 [lastError]——页面据此说"只有群主
     * 能改名"，而不是笼统说"失败了"。
     */
    suspend fun renameGroup(owner: String, conversation: Long, title: String): Boolean {
        if (owner.isEmpty() || conversation <= 0L) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_title", title)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("group_rename", body) ?: return false
        return booleanFrom(text, "group_rename") ?: false
    }

    /**
     * 退出 [conversation]，返回这件事成没成。
     *
     * 退群之后这个会话就不该再出现在 [conversationsFor] 里了，但本对象不替页面做这个删除：
     * 谁持有列表谁自己重拉。
     */
    suspend fun leaveConversation(owner: String, conversation: Long): Boolean {
        if (owner.isEmpty() || conversation <= 0L) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("conversation_leave", body) ?: return false
        return booleanFrom(text, "conversation_leave") ?: false
    }

    /**
     * 把 [conversation] 置顶或取消置顶，返回这件事成没成。
     *
     * [pin] 传什么就是什么，不在这里取反：后端存的是"当前是不是置顶"，所以重复调同一个值也是
     * 幂等的。失败 false，原因是网络还是服务端看 [lastError] / [lastFailureWasNetwork]。
     */
    suspend fun pinConversation(owner: String, conversation: Long, pin: Boolean): Boolean {
        if (owner.isEmpty() || conversation <= 0L) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_pin", pin)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("conversation_pin", body) ?: return false
        return booleanFrom(text, "conversation_pin") ?: false
    }

    /**
     * 把 [conversation] 从列表里藏掉，返回这件事成没成。
     *
     * 藏不是删：消息还在，成员还在，只是这一行不再出现在 [conversationsFor] 里；要它回来只能由
     * 后端的规则决定（比如又有人发消息），本对象不做本地隐藏，免得两处状态打架。
     */
    suspend fun hideConversation(owner: String, conversation: Long): Boolean {
        if (owner.isEmpty() || conversation <= 0L) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("conversation_hide", body) ?: return false
        return booleanFrom(text, "conversation_hide") ?: false
    }

    /**
     * 给自己这个会话开/关免打扰。返回这件事成没成。
     *
     * 只影响自己的成员行：别人在同一个群里想不想收提醒跟你无关。后端只改 `member = me` 那一行，
     * 所以即使这里传了别人也改不动。
     */
    suspend fun setMuted(owner: String, conversation: Long, muted: Boolean): Boolean {
        if (owner.isEmpty() || conversation <= 0L) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_muted", muted)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("conversation_mute", body) ?: return false
        return booleanFrom(text, "conversation_mute") ?: false
    }

    /**
     * 设群公告；空串等于撤掉。只有建群的人能改，别人调用后端会返回 false。
     *
     * 界面据此显示结果——返回 false 时不能假装成功，那会让建群者以为全群都看到了。
     */
    suspend fun setAnnouncement(owner: String, conversation: Long, text: String): Boolean {
        if (owner.isEmpty() || conversation <= 0L) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_text", text)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val response = rpc("group_set_announcement", body) ?: return false
        return booleanFrom(response, "group_set_announcement") ?: false
    }

    /**
     * 改群简介。和公告一样，只有建群的人能改——返回 false 就说明没改成，界面别自作聪明地先画出来。
     */
    suspend fun setIntro(owner: String, conversation: Long, text: String): Boolean {
        if (owner.isEmpty() || conversation <= 0L) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_text", text)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val response = rpc("group_set_intro", body) ?: return false
        return booleanFrom(response, "group_set_intro") ?: false
    }

    /**
     * 打开/关掉这个群的「允许发图片」。只有建群的人能改，别人调用后端返回 false。
     *
     * 关着的时候成员发图会被 `message_send_image` 拒掉，建群的人自己不受限——他得能先把图放进群里。
     */
    suspend fun setGroupAllowImage(owner: String, conversation: Long, allowed: Boolean): Boolean {
        if (owner.isEmpty() || conversation <= 0L) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_allowed", allowed)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val response = rpc("group_set_allow_image", body) ?: return false
        return booleanFrom(response, "group_set_allow_image") ?: false
    }

    /**
     * 改我在这个群里的昵称；空串就是清掉，退回全局名字。
     *
     * 只能改自己那一行，所以这里没有 `member` 参数——要改别人的昵称是另一件事，而且群主也没这个权力。
     */
    suspend fun setMyNickname(owner: String, conversation: Long, nickname: String): Boolean {
        if (owner.isEmpty() || conversation <= 0L) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_nickname", nickname)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val response = rpc("conversation_set_nickname", body) ?: return false
        return booleanFrom(response, "conversation_set_nickname") ?: false
    }

    /** 折叠 / 取消折叠一个群。和免打扰一样只动自己那一行。 */
    suspend fun setFolded(owner: String, conversation: Long, folded: Boolean): Boolean {
        if (owner.isEmpty() || conversation <= 0L) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_folded", folded)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val response = rpc("conversation_fold", body) ?: return false
        return booleanFrom(response, "conversation_fold") ?: false
    }

    /**
     * 清空这个会话的聊天记录——**只对自己生效**。
     *
     * 后端做的是把它们记进 `message_hidden`，不是删行：对方那边一条都不会少。返回被隐藏的条数，
     * 0 表示本来就没有可见的消息。
     */
    suspend fun clearHistory(owner: String, conversation: Long): Int? {
        if (owner.isEmpty() || conversation <= 0L) return null
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val response = rpc("conversation_clear_history", body) ?: return null
        return scalarOf(response)?.intOrNull
    }

    /**
     * 把 [member] 从群里踢出去；只有建群的人能踢，而且踢不掉自己（自己想走是退群）。
     *
     * 返回 false 的三种情况对界面是同一种：没踢成。要区分就得让后端返回错误码，
     * 不值得为一个按钮加一层协议。
     */
    suspend fun kickMember(owner: String, conversation: Long, member: String): Boolean {
        if (owner.isEmpty() || conversation <= 0L || member.isEmpty()) return false
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_member", member)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("group_kick_member", body) ?: return false
        return booleanFrom(text, "group_kick_member") ?: false
    }

    /**
     * 拿这个群的邀请码；没生成过就现场生成一个，已经有过就返回同一个。
     *
     * 只有成员能拿：非成员返回 null。返回的只是码，链接怎么拼是界面的事
     * （`unicanta://group/<code>`）——换一种拼接方式不用改这里。
     */
    suspend fun createInvite(owner: String, conversation: Long): String? {
        if (owner.isEmpty() || conversation <= 0L) return null
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("group_invite_create", body) ?: return null
        return scalarOf(text)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    /**
     * 用邀请码进群，成功返回会话号。
     *
     * 已经在群里也算成功（返回同一个会话号）：点两次链接不该报错，也不该把"你本来就在这"当失败。
     */
    suspend fun joinByInvite(owner: String, code: String): Long? {
        if (owner.isEmpty() || code.isBlank()) return null
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_code", code.trim())
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("group_invite_join", body) ?: return null
        return scalarOf(text)?.longOrNull
    }

    /**
     * [conversation] 里比 [afterId] 更新的消息，按 id 升序。
     *
     * 「最小拉动」就靠这一条：轮询和推送都先问「比我手上最新那条还新的有几条」，正常情况后端
     * 回一个 `[]`，几百字节；只有真有人说话才带回那几条。用 [messagesFor] 整页拉的话，每问一次
     * 都是三十条正文，等于把没变的东西反复搬一遍。
     *
     * [afterId] 传 0 等价于「从最旧开始」，那时用 [messagesFor] 更合适——这里只做增量。
     */
    suspend fun messagesAfter(
        owner: String,
        conversation: Long,
        afterId: Long,
        limit: Int = 50,
    ): List<ChatMessage> {
        if (owner.isEmpty() || conversation <= 0L || afterId <= 0L) return emptyList()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            put("p_after_id", afterId)
            put("p_limit", limit)
        }.toString()
        val text = rpc("messages_after", body) ?: return emptyList()
        return rowsOf(text).mapNotNull { row -> messageOf(row) }
    }

    /**
     * 在"我是成员的所有会话"里搜消息正文，按新到旧。
     *
     * 后端只搜 `kind = 'text'`、未撤回、自己没隐藏的那些，所以这里不做二次过滤——
     * 多做一层反而会和后端的判据漂移。空查询、非成员身份都由后端返回空数组。
     */
    suspend fun searchMessages(owner: String, query: String, limit: Int = 50): List<MessageHit> {
        val trimmed = query.trim()
        if (owner.isEmpty() || trimmed.isEmpty()) return emptyList()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_query", trimmed)
            put("p_limit", limit)
        }.toString()
        val text = rpc("messages_search", body) ?: return emptyList()
        return rowsOf(text).mapNotNull { row -> messageHitOf(row) }
    }

    /**
     * 把 [conversation] 里 [owner] 的已读位置推到现在。
     *
     * 写失败什么也不发生：下一条消息之前再问一次就好，读回执丢了不是错。
     */
    suspend fun markRead(owner: String, conversation: Long) {
        if (owner.isEmpty() || conversation <= 0L) return
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_conversation", conversation)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        rpc("message_read", body)
    }

    /**
     * 把 [owner] 所有会话的已读位置一起推到现在，返回动了几个会话。
     *
     * 失败返回 0——**0 不是失败**：一个全都读过的账号，这个调用本来就该返回 0。页面要分这两件事
     * 就看 [lastError] / [lastFailureWasNetwork]：两个都干净、返回值是 0，那就是真的没有新消息。
     */
    suspend fun markAllConversationsRead(owner: String): Int {
        if (owner.isEmpty()) return 0
        val proof = callerToken()
        val body = buildJsonObject {
            put("p_owner", owner)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = rpc("conversations_mark_all_read", body) ?: return 0
        return intFrom(text, "conversations_mark_all_read") ?: 0
    }

    // ------------------------------------------------------------------ 聊天图片

    /**
     * Storage 的根。
     *
     * [baseUrl] 是 **REST 的 rpc 入口**（默认 `https://<ref>.supabase.co/rest/v1/rpc/`，见
     * `composeApp/build.gradle.kts` 里 `canta.telemetry.baseUrl` 的默认值），而 storage 挂在同一个
     * 主机的另一个前缀下（`https://<ref>.supabase.co/storage/v1/...`）。直接把 `baseUrl` 拼上去
     * 会得到 `.../rest/v1/rpc/storage/v1/...`，那是 404。所以这里切掉 `/rest/v1` 那一段，
     * 只留 scheme + host。
     *
     * 构建时把 baseUrl 换成一个没有 `/rest/v1` 的前缀时，原样使用它（trimEnd 掉尾斜杠），
     * 这样自定义部署仍然按"它给的根"拼接。
     */
    private val storageBase: String =
        baseUrl.substringBefore("/rest/v1").trimEnd('/').ifEmpty { baseUrl.trimEnd('/') }

    /** `chat-images` 这个 bucket 里 [objectPath] 的完整地址。路径里的前导斜杠不算两遍。 */
    private fun chatImageUrl(objectPath: String): String =
        storageBase + "/storage/v1/object/chat-images/" + objectPath.trimStart('/')

    /**
     * 把 [bytes] 传进 `chat-images` bucket 的 [objectPath]，成功（HTTP 2xx）返回 true。
     *
     * **老路**：2026-12 起聊天发图走图床（[uploadChatImageToImageBed]），这条留在仓库里是为了
     * 「图床挂了能不能立刻退回 Supabase」这件事还有一条现成的路由；正常发图不再经过它，所以
     * [downloadChatImage] 那一支兼容老消息的代码也不能跟着删。
     *
     * [owner] 不参与定位：对象路径完全由 [objectPath] 决定（谁传的、放在哪个会话目录下，都是
     * 调用方的事）。这里只用它挡一下"身份是空的就别传"。
     *
     * 直接对 storage 发 PUT，不走 rpc：Supabase 的 storage 是普通 HTTP 端点，`apikey` 加
     * `Authorization: Bearer` 两个头都要（前者给网关，后者给 storage 的鉴权），`x-upsert: true`
     * 让同一条路径可以覆盖——重发同一张图不该变成第二个对象。
     *
     * 失败返回 false，原因进 [lastError]（响应体前 200 字）；连不上是
     * [lastFailureWasNetwork] = true。与 [rpc] 的约定完全一致，页面用同一套判断。
     */
    suspend fun uploadChatImage(
        owner: String,
        objectPath: String,
        bytes: ByteArray,
        mime: String,
    ): Boolean {
        if (owner.isEmpty() || objectPath.isBlank()) return false
        val answered = runCatching {
            withTimeoutOrNull(RPC_TIMEOUT_MS) {
                val response = client.put(chatImageUrl(objectPath)) {
                    header("apikey", apiKey)
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    header(HttpHeaders.ContentType, mime)
                    header("x-upsert", "true")
                    setBody(bytes)
                }
                if (response.status.isSuccess()) {
                    Call.Ok("")
                } else {
                    val text = runCatching { response.bodyAsText() }.getOrDefault("")
                    Call.Refused(text.take(200).ifBlank { "HTTP ${response.status.value}" })
                }
            }
        }
        return record(answered.getOrNull() ?: Call.Unreachable)
    }

    /**
     * 把 [bytes] 传到图床的 [id]，返回能直接读的完整地址；失败返回 null，原因进 [lastError]。
     *
     * [id] 形如 `unicanta/群聊/12/1699999-314.jpg`（见 [ImageBed.chatImageId]）：目录那一段交给
     * `folder`、文件名交给 `fileName`。图床的 `folder` 优先于 `fileName` 里的目录，两个都传是为了
     * 让"放在哪"在请求里看得见，不用去猜谁优先。
     *
     * **为什么挂在 [SocialRepository] 里而不是 [ImageBed] 自己 new 一个 HttpClient**：这一层已经有
     * 一个 client、一个 [json]、一套 [lastError] / [lastFailureWasNetwork] 的失败约定，复用它们比再养
     * 一个客户端稳——多一个客户端就多一份超时、一份连接池、一处"这个页面为什么读不到失败原因"。
     * [ImageBed] 那边只留协议（地址、目录、上限、请求体怎么拼）。
     *
     * 成功取响应里的 `url`（图床回的是百分号编码过的直链，直接放进消息体）；万一没给就自己用
     * [ImageBed.fileUrl] 拼一个。失败按 [Call] 那三支记账：4xx/5xx 或 `code != 0` 是服务端拒绝，
     * 连不上是 [lastFailureWasNetwork]。
     */
    suspend fun uploadChatImageToImageBed(
        id: String,
        bytes: ByteArray,
        contentType: String = "image/jpeg",
        /**
         * 重名时覆盖（`overwrite=1`）还是让图床拿 409 拒绝（`overwrite=0`，默认，聊天图用这个）。
         *
         * 头像必须覆盖：名字固定成 `<owner>.jpg`，不覆盖的话**第一次之后再也换不了**头像，而"换头像"
         * 正是这个功能的全部内容。聊天图反过来：id 每次都是新的，覆盖只会在撞名时把上一条消息的图
         * 悄悄换掉，所以那边保持 `false`。
         */
        overwrite: Boolean = false,
    ): String? {
        if (id.isBlank() || bytes.isEmpty()) return null
        val boundary = ImageBed.newBoundary()
        val body = ImageBed.multipartBody(
            boundary = boundary,
            fields = listOf(
                "uid" to ImageBed.TOKEN,
                "folder" to ImageBed.folderOf(id),
                "fileName" to ImageBed.fileNameOf(id),
                // 重名就 409、不覆盖：id 里已经有时间戳和随机数，撞上的概率可以不管；真撞上了
                // 也该是"这一条失败"，而不是悄悄把上一条消息的图换成这张。头像反过来，见 [overwrite]。
                "overwrite" to if (overwrite) "1" else "0",
            ),
            fileName = ImageBed.fileNameOf(id),
            contentType = contentType,
            bytes = bytes,
        )
        val answered = runCatching {
            withTimeoutOrNull(IMAGE_UPLOAD_TIMEOUT_MS) {
                val response = client.post(ImageBed.UPLOAD_URL) {
                    header(HttpHeaders.ContentType, "multipart/form-data; boundary=$boundary")
                    setBody(body)
                }
                val text = runCatching { response.bodyAsText() }.getOrDefault("")
                val fields = parse(text)?.let { root -> runCatching { root.jsonObject }.getOrNull() }
                val code = fields?.get("code")
                    ?.let { element -> runCatching { element.jsonPrimitive.intOrNull }.getOrNull() }
                val said = fields?.get("msg")
                    ?.let { element -> runCatching { element.jsonPrimitive.contentOrNull }.getOrNull() }
                    .orEmpty()
                if (response.status.isSuccess() && (code == null || code == 0)) {
                    Call.Ok(text)
                } else {
                    Call.Refused(
                        said.ifBlank { text.take(200) }.ifBlank { "HTTP ${response.status.value}" },
                    )
                }
            }
        }
        return when (val result = answered.getOrNull() ?: Call.Unreachable) {
            is Call.Ok -> {
                lastFailureWasNetwork = false
                lastError = null
                BackendHealth.reportReachable()
                val url = parse(result.text)?.let { root ->
                    runCatching { root.jsonObject["url"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                }
                url?.takeIf { it.startsWith("http") } ?: ImageBed.fileUrl(id)
            }
            is Call.Refused -> {
                lastFailureWasNetwork = false
                lastError = result.reason
                BackendHealth.reportReachable()
                null
            }
            Call.Unreachable -> {
                lastFailureWasNetwork = true
                lastError = null
                BackendHealth.reportUnreachable()
                null
            }
        }
    }

    /**
     * 换一个人的头像：[bytes] 传到 `unicanta/头像/<owner>.jpg`，返回图床直链；失败返回 null，
     * 原因照旧进 [lastError]。
     *
     * **id 是固定的、上传是覆盖式的**（[ImageBed.avatarId]）：同一个人重传就是换头像，图床上的对象
     * 不会越攒越多，设置里存的那个地址也不会失效——地址一变，本机存着的那条直链就 404 了。
     *
     * 大小与压缩不在这里做：`ImageBed.MAX_BYTES` 那条规则要动平台层的解码器（
     * `CantaPlatform.compressImage`），仓储层拿不到它。调用方先压好再进来，压不下来就别调这里。
     */
    suspend fun uploadAvatar(owner: String, bytes: ByteArray): String? {
        val who = owner.trim()
        if (who.isEmpty() || bytes.isEmpty()) return null
        return uploadChatImageToImageBed(
            id = ImageBed.avatarId(who),
            bytes = bytes,
            contentType = "image/jpeg",
            overwrite = true,
        )
    }

    /**
     * 把 [objectPath] 那张图读回来，2xx 返回字节，别的一律 null。
     *
     * [objectPath] 是消息体里那一格，两种形状都认：**图床的完整地址**（`https://.../file/...`，公开
     * 免鉴权）或**老消息的 bucket 对象路径**（`<owner>/<时间戳>-<随机数>.jpg`，要拼 storage 的地址
     * 再加鉴权头）。气泡和全屏查看器都只调这一个入口，所以这里一处兼容，两条路的消息都能显示。
     *
     * 字节走 Ktor 自带的 `body<ByteArray>()`（仓库里读远程图片的现成先例是
     * `ui/component/DeviceHero.kt`），不需要 ContentNegotiation；解码成图片是界面的事，
     * 这一层只负责把字节拿回来。
     */
    suspend fun downloadChatImage(objectPath: String): ByteArray? {
        if (objectPath.isBlank()) return null
        // 两种地址都在这儿汇合：
        //  * 新消息的 body 是图床的完整直链（`https://.../file/...`）——公开免鉴权，直接 GET；
        //  * 老消息的 body 还是 `chat-images` bucket 里的对象路径，照旧拼 storage 的地址并带那两个
        //    鉴权头。**老消息必须还能点开**，所以这一支不能删。
        val isDirect = objectPath.startsWith("https://", ignoreCase = true) ||
            objectPath.startsWith("http://", ignoreCase = true)
        val address = if (isDirect) objectPath else chatImageUrl(objectPath)
        val answered = runCatching {
            withTimeoutOrNull(RPC_TIMEOUT_MS) {
                val response = client.get(address) {
                    // 图床的 /file/<id> 是公开的，带 Supabase 的两个头只会多余；storage 那一支才需要。
                    if (!isDirect) {
                        header("apikey", apiKey)
                        header(HttpHeaders.Authorization, "Bearer $apiKey")
                    }
                }
                if (response.status.isSuccess()) {
                    // 状态码对了，但正文仍可能读不出来（连接被掐断）：那两个字节算"服务端没给"，
                    // 不是网络失败，也不该让页面去查代理。
                    val bytes = runCatching { response.body<ByteArray>() }.getOrNull()
                    if (bytes == null) {
                        Blob.Refused("HTTP ${response.status.value}（正文读不出来）")
                    } else {
                        Blob.Ok(bytes)
                    }
                } else {
                    val text = runCatching { response.bodyAsText() }.getOrDefault("")
                    Blob.Refused(text.take(200).ifBlank { "HTTP ${response.status.value}" })
                }
            }
        }
        return when (val blob = answered.getOrNull() ?: Blob.Unreachable) {
            is Blob.Ok -> {
                lastFailureWasNetwork = false
                lastError = null
                blob.bytes
            }
            is Blob.Refused -> {
                lastFailureWasNetwork = false
                lastError = blob.reason
                null
            }
            Blob.Unreachable -> {
                lastFailureWasNetwork = true
                lastError = null
                null
            }
        }
    }

    /**
     * 清掉 [days] 天前的聊天图片，返回被清掉的消息条数。
     *
     * 这是后端按天数扫的清理，客户端只负责报"多少天"，不自己算时间。失败返回 0——
     * **0 可能是一次成功的清理**（没有过期图），要分辨就看 [lastError] / [lastFailureWasNetwork]。
     */
    suspend fun purgeOldImages(days: Int = 10): Int {
        val body = buildJsonObject { put("p_days", days) }.toString()
        val text = rpc("chat_purge_images", body) ?: return 0
        return intFrom(text, "expired_messages") ?: 0
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
            BackendHealth.reportReachable()
            result.text
        }
        is Call.Refused -> {
            lastFailureWasNetwork = false
            lastError = result.reason
            BackendHealth.reportReachable()
            null
        }
        Call.Unreachable -> {
            lastFailureWasNetwork = true
            lastError = null
            BackendHealth.reportUnreachable()
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

    /**
     * 字符串数组字段。后端给的是 JSON 数组；缺字段、不是数组、元素不是字符串，都当空。
     *
     * 单个元素可能是数字也可能是字符串，按 [jsonPrimitive] 取文本而不是强转
     * `jsonArray`——`mentions` 里存的是身份号码，早前后端有把它序列化成数字的可能。
     */
    private fun JsonObject.stringsOf(key: String): List<String> =
        runCatching {
            get(key)?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.filter { it.isNotBlank() }
        }.getOrNull().orEmpty()

    /** `follow_set` 的答案：对象里的 `following`，或者直接就是一个布尔。 */
    private fun followingFrom(text: String): Boolean? =
        firstRowOf(text)?.flagOf("following") ?: scalarOf(text)?.booleanOrNull

    /** `dm_open` / `group_create` 的答案：会话号，可能直接就是一个数。 */
    private fun idFrom(text: String): Long? {
        firstRowOf(text)?.let { row -> return row.longOf("conversation_id") ?: row.longOf("id") }
        return scalarOf(text)?.longOrNull
    }

    /**
     * 一个整数字段的答案：对象里的 [key]，或者直接就是一个数（`returns int` 那种）。
     *
     * 和 [JsonObject.intOf] 不同，这里**没有兜底值**：缺字段就是 null，因为调用方要区分
     * "后端没给"和"后端给了 0"。
     */
    private fun intFrom(text: String, key: String): Int? {
        firstRowOf(text)?.let { row -> row.longOf(key)?.let { return it.toInt() } }
        return scalarOf(text)?.longOrNull?.toInt()
    }

    /**
     * 一个布尔答案：后端 `returns boolean` 时 PostgREST 有两种吐法——直接 `true`，或者包在
     * 一个以函数名命名的键里（`{"group_rename": true}`）。
     *
     * 先认 [key]；对象里换了键名也照样认（取第一个布尔），这样后端哪天改包装不影响这一侧。
     * 都没有就是 null，由调用方决定退成什么。
     */
    private fun booleanFrom(text: String, key: String): Boolean? {
        val row = firstRowOf(text) ?: return scalarOf(text)?.booleanOrNull
        row.flagOf(key)?.let { return it }
        return row.values.firstNotNullOfOrNull { value ->
            runCatching { value.jsonPrimitive.booleanOrNull }.getOrNull()
        }
    }

    /** [row] 里的成员表；没有 `members`、它不是数组、元素不是对象，都只影响对应那一项。 */
    private fun membersOf(row: JsonObject): List<ConversationMember> {
        val array = runCatching { row["members"]?.jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val member = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val owner = member.textOf("owner")
            val display = member.textOf("name")
            // 既没有身份也没有名字的成员画不出来，也退不掉，直接丢掉。
            if (owner.isEmpty() && display.isEmpty()) return@mapNotNull null
            ConversationMember(owner = owner, name = display)
        }
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
            // 后端把身份号放在 `owner` 列（followers_of / following_of 都是），`id` 只作旧字段兜底：
            // 早先这里只读 `id`，拿到空串，于是所有人都被下面的空判断丢掉——关注列表、可拉进群的
            // 名单、可私聊的人全成了空的。
            val id = row.textOf("owner").ifBlank { row.textOf("id") }
            val display = row.textOf("name")
            // 一个既没有编号也没有名字的行在界面上画不出来，也没法点，直接丢掉。
            if (id.isEmpty() && display.isEmpty()) return@mapNotNull null
            SocialPerson(
                id = id,
                name = display,
                iFollow = row.flagOf("i_follow") ?: iFollow,
                followsMe = row.flagOf("follows_me") ?: followsMe,
                // 关注时间在 `created_at`，`since` 是同一个意思的旧名。
                since = row.textOf("created_at").ifBlank { row.textOf("since") },
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
            pinned = row.flagOf("pinned") ?: false,
            muted = row.flagOf("muted") ?: false,
            folded = row.flagOf("folded") ?: false,
            unreadMentions = row.intOf("unread_mentions"),
            lastMentionsMe = row.flagOf("last_mentions_me") ?: false,
        )
    }

    /**
     * 一条搜索命中（`messages_search`）。`conversation_id` 缺失的行跳不过去，直接丢掉——
     * 一条点了没反应的结果比没有这条结果更让人困惑。
     */
    private fun messageHitOf(row: JsonObject): MessageHit? {
        val id = row.longOf("id") ?: return null
        val conversation = row.longOf("conversation_id") ?: return null
        return MessageHit(
            id = id,
            conversationId = conversation,
            conversationTitle = row.textOf("conversation_title"),
            conversationKind = row.textOf("conversation_kind"),
            sender = row.textOf("sender"),
            senderName = row.textOf("sender_name"),
            body = row.textOf("body"),
            createdAt = row.textOf("created_at"),
        )
    }

    /** 一行消息（`messages_for` / `messages_before` 同一解析）；连 id 都没有的行画不成气泡，丢掉。 */
    /**
     * `kind == "share"` 那条的卡片；别的类型、body 不是 JSON、或者少字段都算"没有卡片"。
     *
     * 坏数据只让这一条画不出来，不让整页加载失败：body 是后端写的，但它是用户可见的文本字段，
     * 客户端不该假设它一定完好。
     */
    private fun shareOf(row: JsonObject): ShareCard? {
        if (row.textOf("kind") != "share") return null
        val obj = runCatching { parse(row.textOf("body"))?.jsonObject }.getOrNull() ?: return null
        val kind = obj.textOf("k")
        val target = obj.textOf("t")
        if (kind != "app" && kind != "plan") return null
        if (target.isBlank()) return null
        return ShareCard(
            kind = kind,
            target = target,
            title = obj.textOf("n"),
            note = obj.textOf("d"),
        )
    }

    /** `kind == "card"` 那条的名片；认不出就当没有。 */
    private fun cardOf(row: JsonObject): PersonCard? {
        if (row.textOf("kind") != "card") return null
        val obj = runCatching { parse(row.textOf("body"))?.jsonObject }.getOrNull() ?: return null
        val owner = obj.textOf("o")
        if (owner.isBlank()) return null
        return PersonCard(owner = owner, name = obj.textOf("n"))
    }

    private fun messageOf(row: JsonObject): ChatMessage? {
        val id = row.longOf("id") ?: return null
        return ChatMessage(
            id = id,
            sender = row.textOf("sender"),
            senderName = row.textOf("sender_name"),
            body = row.textOf("body"),
            createdAt = row.textOf("created_at"),
            removed = row.flagOf("removed") ?: false,
            kind = row.textOf("kind").ifBlank { "text" },
            mentions = row.stringsOf("mentions"),
            // 后端给的是 0 或 null 都算"没有引用"：`longOf` 对 null 返回 null，
            // 对 0 返回 0，而消息 id 从 1 起，所以 <= 0 一律当没有。
            replyTo = row.longOf("reply_to")?.takeIf { it > 0L },
            replyBody = row.textOf("reply_body"),
            replySenderName = row.textOf("reply_sender_name"),
            share = shareOf(row),
            card = cardOf(row),
        )
    }
}
