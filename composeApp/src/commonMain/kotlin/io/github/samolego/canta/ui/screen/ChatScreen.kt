package io.github.samolego.canta.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.samolego.canta.data.community.AiSender
import io.github.samolego.canta.data.community.ChatLiveSource
import io.github.samolego.canta.data.community.ChatMessage
import io.github.samolego.canta.data.community.ChatMessageCache
import io.github.samolego.canta.data.community.ChatMessageStore
import io.github.samolego.canta.data.community.Conversation
import io.github.samolego.canta.data.community.ConversationCache
import io.github.samolego.canta.data.community.ConversationDetail
import io.github.samolego.canta.data.community.ConversationMember
import io.github.samolego.canta.data.community.LivePresence
import io.github.samolego.canta.data.community.OfficialGroups
import io.github.samolego.canta.data.community.PersonCard
import io.github.samolego.canta.data.community.ShareCard
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.data.imagebed.ImageBed
import io.github.samolego.canta.ui.component.AuthorLabel
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.dismissInputOnTap
import io.github.samolego.canta.ui.component.Emotion
import io.github.samolego.canta.ui.component.EmotionPicker
import io.github.samolego.canta.ui.component.EmotionSpan
import io.github.samolego.canta.ui.component.EmotionText
import io.github.samolego.canta.ui.component.GroupAvatarView
import io.github.samolego.canta.ui.component.PersonAvatar
import io.github.samolego.canta.ui.component.UserAvatar
import io.github.samolego.canta.ui.component.personAvatarUrl
import io.github.samolego.canta.ui.component.dropLastSticker
import io.github.samolego.canta.ui.component.emotionOf
import io.github.samolego.canta.ui.component.parseEmotionBody
import io.github.samolego.canta.ui.component.rememberEmotionPick
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.isDark
import io.github.samolego.canta.util.BeijingTime
import io.github.samolego.canta.util.IdentitySetup
import io.github.samolego.canta.util.currentTimeMillis
import io.github.samolego.canta.util.rememberVoiceInput
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource

/** 单颗表情那条消息画多大：比气泡里内联的表情大得多，又没有大到占半屏。 */
private val ChatStickerSize = 112.dp

// ---------------------------------------------------------------- 顶部那块（标题栏 + 入口行）的布局尺
//
// 一整块只用这几个数，不再逐个写 padding/Spacer：标题栏与入口行左右内边距相同、元素间距相同，
// 两排的左右基线才对得上；某一格想往哪边挪，只能改这里的共同值，不能单独加 margin。

/** 标题栏高度：比 `FlTopBarHeight` 矮一截，去掉顶部那块多余的留白。 */
private val ChatHeaderBarHeight = 60.dp

/** 标题栏/入口行左右内边距：两排共用，左右基线才对得齐。 */
private val ChatHeaderSidePadding = 16.dp

/** 标题栏里各元素之间的统一间距（返回 / 头像 / 群名 / 折叠按钮）。 */
private val ChatHeaderGap = 10.dp

/** 顶栏那个圆头像的直径：比正文里的行内头像大一圈，和抖音群聊顶栏一个量级。 */
private val ChatHeaderAvatarSize = 52.dp

/** 入口行高度：选中那一格的底色居中放在这一行里，上下都留一点，不贴着顶栏与分割线。 */
private val ChatTabBarHeight = 52.dp

/** 标题栏与入口行底部那条 1px 浅灰分割线。 */
private val ChatHeaderDividerThickness = 0.5.dp

/**
 * 「快捷表情直接发出去」那一条在本机的类型。
 *
 * 后端只存 `body`，`kind` 一律是 `text`（`message_send` 甚至不写这一列），所以这个标记**只活在
 * 本机**：发送时打上、写进消息缓存，刷新合并时保住。它的意义只有一个——决定这条消息画成
 * 大贴纸还是普通气泡；从表情面板选进草稿再发的，仍旧是后者。
 */
private const val StickerKind = "sticker"

/**
 * 合并两批消息，并保住本机的贴纸标记。
 *
 * 后端不知道 `StickerKind`，`messages_for` / `messages_after` 回来的那一条 `kind` 永远是 `text`；
 * 不在这里补回去，快捷发的表情在第一次刷新之后就会缩回气泡里。
 */
private fun mergeMessages(fresh: List<ChatMessage>, current: List<ChatMessage>): List<ChatMessage> {
    val stickerIds = current.filter { it.kind == StickerKind }.map { it.id }.toSet()
    if (stickerIds.isEmpty()) {
        return (fresh + current).distinctBy { it.id }.sortedBy { it.id }
    }
    return (fresh + current)
        .distinctBy { it.id }
        .sortedBy { it.id }
        .map { if (it.id in stickerIds && it.kind == "text") it.copy(kind = StickerKind) else it }
}

/** 一个气泡最宽多少：一行字到这儿换行，免得一条长消息把整块屏幕占满。 */
private val ChatBubbleMaxWidth = 260.dp

/**
 * 输入栏那两处底色：**亮色保持截图里的浅灰，暗色跟随主题**。
 *
 * 以前这里是一对写死的浅灰色（#F7F7F7 / #F2F2F2）。截图那种牌子感在亮色下没问题，但换到暗色
 * 主题后整条栏会在深色页面上挖出一块白板，输入框里自己打的字反而更不清楚。
 */
private fun composerBarColor(scheme: ColorScheme): Color =
    if (scheme.isDark) scheme.surfaceContainerLow else Color(0xFFF7F7F7)

private fun composerFieldColor(scheme: ColorScheme): Color =
    if (scheme.isDark) scheme.surfaceContainerHigh else Color(0xFFF2F2F2)

/** 占位字：亮色仍是截图里的 #999，暗色用主题的次级前景色，免得白底灰字反过来变成亮块。 */
private fun composerPlaceholderColor(scheme: ColorScheme): Color =
    if (scheme.isDark) scheme.onSurfaceVariant else Color(0xFF999999)

/** 发送键：截图里是粉的（#FE2C55），输入栏和表情面板共用这一个。 */
private val ChatSendPink = Color(0xFFFE2C55)

/** 同一段对话隔了这么久就再起一条居中时间：微信是 5 分钟。 */
private const val ChatStampGapSeconds = 5 * 60L

/**
 * 卡片（分享 / 名片）最宽多少。
 *
 * 比气泡窄一点：卡片里只有一个标题和一行备注，撑到气泡那么宽会让聊天列表看起来半屏都是方块。
 */
private val ChatCardMaxWidth = 220.dp

/** 一张图最多占多大：竖图再高也不能把一屏撑满，宽高两条都收着。 */
private val ChatImageMaxWidth = 220.dp
private val ChatImageMaxHeight = 280.dp

/** 图片还没到、或者根本解不开时那个灰框：尺寸固定，换图时列表不会上下跳。 */
private val ChatImagePlaceholderWidth = 160.dp
private val ChatImagePlaceholderHeight = 120.dp

/** 图片的圆角：比气泡的 14 小一点，图按 12 收边更像一张卡片。 */
private val ChatImageShape = RoundedCornerShape(12.dp)

/** 一页读多少条：首屏和往上翻历史用同一个数，翻页就用这个数往回退一屏。 */
private const val ChatPageSize = 30

/**
 * 没有实时通道时多久问一次新消息。
 *
 * 这一页的兜底是轮询：数值就是「对方发完到你看得见」的上限，平均还要再折一半。
 * 有 [ChatLiveSource] 的时候它退成 [ChatPollFallbackMs]，新消息走推送；只有网页版这种
 * 没有 websocket 引擎的地方才用得上这个 2 秒。
 */
private const val ChatPollMs: Long = 2_000L

/**
 * 有实时通道时的兜底轮询间隔。
 *
 * 推送会漏（断线、切后台回来、服务端广播丢一条），所以不能完全停；但这时它只是「保险」，
 * 10 秒问一次足够——真有事，广播那一侧几百毫秒就到了。连不上就退回 [ChatPollMs]。
 */
private const val ChatPollFallbackMs: Long = 10_000L

/** 增量拉取一次最多带回多少条新消息；一次真到 50 条以上的情况，下一条轮询会接着拉。 */
private const val ChatNewLimit = 50

/**
 * 首屏落底补几次。
 *
 * 一进群要看见最新那条，而第一次 `scrollToItem` 往往落在半截：列表还没量完高度。补三轮
 * （立刻 + 两帧之后 + 再 60ms）足够盖住「首屏 + 紧随其后的一页增量 + 输入栏/导航区把视口
 * 压矮」这几种情况，又不至于在用户已经开始往上翻时还拽回来。
 */
private const val FirstPaintScrollPasses = 3

/** 首屏每一轮落底之间等多久（毫秒）。 */
private const val FirstPaintScrollPassMs: Long = 60L

/**
 * 首屏补差一页问多少条。
 *
 * 比 [ChatNewLimit] 大：冷启动只补一次，落后两百条的时候按 50 条一页要来回问四趟，中间那几屏
 * 就是"先画旧的、再一截一截长出来"。一页 100 条，四趟变两趟，正常情况下第一页就是空数组。
 */
private const val ChatCatchupPage = 100

/**
 * 隔多久顺带整页核对一次。
 *
 * 增量只问「比手上最新那条还新的」，对「对方撤回 / 编辑了一条老消息」是瞎的：那些 id 比游标旧，
 * 永远不在 `messagesAfter` 的结果里。所以每隔这么久补一次整页拉取，把原地变化对齐。
 * 一分钟：撤回本来就少见，一分钟兜一次够用，而平均开销从「每轮 30 条正文」降到「每轮一个空数组」。
 *
 * 用时间而不是轮数：轮询有两档间隔（2 秒 / 10 秒），按 30 轮算就是一分钟和五分钟两个结果。
 */
private const val TailFullIntervalMillis: Long = 60_000L

/**
 * 草稿落盘的防抖时长。
 *
 * 600ms 是「一个词打完的停顿」：再短会在一个字一个字敲的时候反复写设置文件，
 * 再长会让人觉得"我退出去怎么没存上"。它比一次 `ProtoBlobStore.update` 的落盘开销大得多，
 * 所以宁可等这一会儿。
 */
private const val CHAT_DRAFT_PERSIST_MILLIS: Long = 600L

/**
 * 多久问一次「别人读到哪、谁在打字」。
 *
 * 5 秒是「正在输入」能用的下限：再慢，人打完了提示才亮；再快，群里每个人的心跳都变成一个请求。
 * 已读回执搭同一趟车，所以这条间隔同时也是回执的延迟。
 */
private const val ChatPresencePollMs: Long = 5_000L

/**
 * 两次「我正在打字」之间至少隔这么久。
 *
 * 后端在 2 秒内收到第二次会直接当成功、不发 UPDATE，所以 4 秒是客户端自己的节流：
 * 打字是连续的，不必每个字都告诉服务端。
 */
private const val ChatTypingPingMs: Long = 4_000L

/**
 * 叫了群聊 AI 之后，客户端自己催回答的间隔与次数。
 *
 * 服务端 cron 是每分钟一拍，单靠它最坏要等 60 秒（2026-11-24 实测 30 秒：@ 在 04:15:30，
 * 回答落在 04:16:00）。发完 2 秒问一次、最多 15 次，正常几秒就能看见；等不到也不算错，
 * cron 那一拍迟早会把回答写回来。
 */
private const val ChatAiPollMs: Long = 2_000L
private const val ChatAiPollTries = 15

/**
 * 后端 `message_send` 里触发群 AI 的那条判定，客户端复刻一份。
 *
 * 两处不一致会出现最难查的状态：客户端在等"正在思考"，后端根本没排队。所以这里的正则和
 * `private.ai_queue` 前的那个 `body ~ '@\s*群聊AI'` 必须字面对齐。
 */
private val ChatAiTrigger = Regex("@\\s*群聊AI")

/**
 * 这条正文里有几颗表情（且整条只有表情）。0 表示它不算"纯表情消息"，按普通正文画。
 *
 * 用数量而不是"是不是纯表情"：一颗放大了好看，三颗一起放大会把气泡撑成半屏，
 * 有人就爱连发四五个，那种必须保持正文大小。
 */
private fun chatStickerCount(body: String): Int {
    val spans = parseEmotionBody(body)
    return if (spans.isNotEmpty() && spans.all { it is EmotionSpan.Sticker }) spans.size else 0
}

/** 全屏看图能放大到几倍；双击在 1f 和它之间切换。 */
private const val ChatImageMaxZoom = 4f

/**
 * 双击放大到几倍。
 *
 * 不是 4f（双击要的是"看清细节"，但一下顶到上限之后就只剩拖动了，回头还得再双击一次）；
 * 2.5 大约是「一行小字能读清」的倍数，也留了继续往里捏的余地。
 */
private const val ChatImageDoubleTapZoom = 2.5f

/**
 * 输入框上面那排快捷短语：左边是画出来的样子，右边是点下去真正填进输入框的字。
 *
 * 「@ 群聊AI」带上一个尾巴空格，点完接着打字就是 @ 了一个人，不用再自己补一个空格。
 */
/**
 * 输入框上面那排「点一下就发」的表情。
 *
 * 抖音私信那一排就是这个：一排小图，点一颗**当场发出去**一条表情消息，不经过输入框，
 * 也不用再按一次发送。所以这里是表情的 key，不是文字——文字那套（先填进草稿再发）
 * 是输入框和表情面板的事。
 */
private val ChatQuickStickers = listOf("zan", "wulian", "meigui", "weixiao", "daxiao")

/**
 * 私聊 / 群聊一页。
 *
 * 样子照着抖音私信做，但只做能落到这一层数据上的东西：
 *
 *  * 顶栏：回去的箭头、首字头像、名字（**不挂人数**，内测群人少），右边两个圆形按钮（通话 / 更多）。
 *    头像和名字是一块入口：群聊进群资料，私聊进对面那个人的主页（[onOpenProfile]）。
 *  * 消息区：自己的消息靠右、右边跟一张自己的圆头像；群里别人的消息左边也有头像，私聊里对方不画。
 *    换一天的头上压一条居中的灰字日期，同一天隔了五分钟再起一条 HH:mm；气泡里不再塞时间
 *    （微信就是这样，时间是行与行之间的事，不是某一条的装饰）。
 *    点头像看那个人的主页，别人的长按气泡复制正文，自己的长按弹「复制 / 撤回」小菜单，
 *    撤回过的消息画成一行居中灰字，三者与头像点击是各自独立的触摸区域。
 *    图片消息（`kind == "image"`）画成一张图，自己的靠右、别人的带头像，过期的画一行灰字。
 *  * 滚动：不在最底的时候右下角有一个「新消息(N)」胶囊和一个向下箭头，点了滚到最新一条；
 *    滚到最顶时自动往回翻一页历史（[SocialRepository.messagesBefore]）。
 *  * 底部：一排快捷短语胶囊 + 输入栏（九宫格 / 输入框 / 语音或发送 / 表情 / 加号），
 *    表情拉开输入栏上方的表情面板（[EmotionPicker]），九宫格和加号拉开「更多」面板；
 *    面板里只有「图片」能用（[pickImage] 选图 → 超 2.5MB 先压 → 上图床 → 登记），视频/位置/文件点一下只给一句
 *    「只支持发图片」；两个面板互斥，拉开一个收起另一个。
 *
 * 后端只提供读一页（[SocialRepository.messagesFor]、[SocialRepository.messagesBefore]）、写一条
 * （[SocialRepository.sendMessage]、[SocialRepository.sendImageMessage]）、传一张图
 * （[SocialRepository.uploadChatImageToImageBed]、[SocialRepository.downloadChatImage]）、撤回一条
 * （[SocialRepository.deleteMessage]）和一次会话详情（[SocialRepository.conversationDetail]）；
 * 写成功的那一条直接用后端回来的行画出来，不等下一次拉取，撤回成功把本地那条标成已撤回。
 * 详情拉不到就当没有，聊天照常。
 *
 * 顺序：后端给的是旧在前，所以列表本身正序，进入页面和发出消息后都滚到最底一行。
 * 打开页面就把已读位置推到此刻（[SocialRepository.markRead]）：读回执丢了不是错，下一条消息前再推一次。
 */
@Composable
fun ChatScreen(
    repository: SocialRepository,
    owner: String,
    userName: String,
    conversationId: Long,
    title: String,
    isGroup: Boolean,
    onNavigateBack: () -> Unit,
    memberCount: Int = 0,
    onOpenGroupInfo: () -> Unit = {},
    /** 点头像/顶栏抬头，打开那个人的主页。 */
    onOpenProfile: (owner: String) -> Unit = {},
    /** 让平台挑一张图片：桌面/网页没有相册，返回 null。 */
    pickImage: suspend () -> ByteArray? = { null },
    /**
     * 把一张图压到 2.5MB（[ImageBed.MAX_BYTES]）以内：由上层接平台能力
     * （`{ platform.compressImage(it, ImageBed.MAX_BYTES) }`），压不动就回答 null。
     *
     * 这一层只负责"超了就调它、压不下来就别发"，怎么压是平台的事（Android 那侧解码 + 重编码
     * JPEG，桌面/网页沿用默认回答 null）。默认值是"什么也压不了"，所以漏传它的调用方看到的
     * 行为是"大图发不出去"，而不是悄悄传一张 8MB 的原图上去。
     */
    compressImage: (ByteArray) -> ByteArray? = { null },
    /**
     * 这个会话上次没发出去的草稿；空串表示没有。
     *
     * 由上层从设置里读出来传进来（[io.github.samolego.canta.data.CantaSettings.chatDraftsFlow]），
     * 这一层不自己去读存储：聊天页不该认识设置文件长什么样。只在进来那一刻回填一次，
     * 之后以本地的 [draft] 为准——人正在打字，中途被存储里那个旧值盖掉是最糟的体验。
     */
    initialDraft: String = "",
    /**
     * 草稿变了要落盘时叫一声；null 表示这一层不做持久化。
     *
     * 调用方负责防抖之外的一切（写哪儿、怎么合并）。这里已经做了 600ms 防抖，
     * 所以它收到的是"停下来之后的那一版"，不是每个按键。
     */
    onDraftPersist: ((String) -> Unit)? = null,
    /**
     * 点分享卡片时的去处；null 表示这一层不跳转，卡片照旧画但不响应点击。
     *
     * 传出来的是整张卡而不是一个拼好的路由：App 走包名、方案走方案号，两种参数形状不同，
     * 拼路由的事该由认识路由的那一层做。
     */
    onOpenShare: ((ShareCard) -> Unit)? = null,
    /** 点个人名片：进那个人的主页。 */
    onOpenCard: ((PersonCard) -> Unit)? = null,
    /**
     * 实时通道：服务端每有人发一条就往这里报一声。
     *
     * null 表示这一侧没有（网页版没有 websocket 引擎），页面就退回纯轮询。给了它以后轮询只当
     * 兜底（30 秒），新消息靠推送——别再为了"准时"把轮询调密，那等于两套都在跑。
     */
    liveSource: ChatLiveSource? = null,
    /**
     * 这一页对面的人被本机拉黑了没有。
     *
     * 拉黑没有后端接口，名单在设置里（[io.github.samolego.canta.data.CantaSettings.blockedPeersFlow]），
     * 由上层读出来传进来。被拉黑时这一页照旧能看历史，只是输入栏整个收起来（见 [ChatComposer]）。
     */
    blocked: Boolean = false,
    /**
     * 名字与头像设过了没有：没设过就发不了言（见 [IdentitySetup]）。
     *
     * 处理方式和 [blocked] 同一套——输入栏整条换成一行说明，没有输入框、没有发送键；
     * 区别只是这句话领人去「我的 → 个人主页」。默认"没设过"，漏接的调用点宁可发不出去。
     */
    setupGate: IdentitySetup = IdentitySetup.UNSET,
    /**
     * 本机收藏的这个会话的消息，收得最晚的在最后（见 `CantaSettings.chatSavedFlow`）。
     *
     * 收藏只对自己可见：不进后端、不在群设置里，入口是长按菜单和顶栏那排的「收藏」格子。
     */
    savedMessages: List<ChatMessage> = emptyList(),
    /** 收藏 / 取消收藏一条；[keep] 为 true 是收进来。null 表示这一层不做持久化。 */
    onToggleSaved: ((ChatMessage, Boolean) -> Unit)? = null,
    /**
     * 本人已经确认过的那条公告的指纹（见 [onNoticeSeen]）；等于当前公告的指纹就不再显示横幅。
     *
     * 空串是"这条公告没确认过"。
     */
    noticeSeen: String = "",
    /** 点了公告的「知道了」或者关掉过横幅：把这条公告的指纹记到本机（由上层落盘）。 */
    onNoticeSeen: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // 上一次看过的这一屏先画出来：从消息页点进来时不该再看一次「… → 等网络」。
    // 缓存是进程级的（见 [ChatMessageCache]），所以来回切页面不会重拉；网络回来的那一版再顶上去。
    var messages by remember(conversationId) {
        mutableStateOf(ChatMessageCache.get(conversationId).orEmpty())
    }
    var loading by remember(conversationId) {
        mutableStateOf(ChatMessageCache.get(conversationId).isNullOrEmpty())
    }
    var loadError by remember { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(conversationId) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendFailed by remember { mutableStateOf(false) }
    var loadingEarlier by remember { mutableStateOf(false) }
    // 顶栏「更多」菜单里的两个开关。状态来自会话列表那一行——会话详情里没有置顶/免打扰这两列，
    // 而菜单要按真实状态说话（已经置顶就该写「取消置顶」，写反了等于每次点都在猜）。
    var pinned by remember(conversationId) { mutableStateOf(false) }
    var muted by remember(conversationId) { mutableStateOf(false) }
    /**
     * 这个群钉住的消息，最新钉的在前。
     *
     * 和聊天记录分开存：置顶那几条可能在很久以前、不在刚拉回来的这一页里；[togglePinMessage]
     * 改完会重拉一次，所以这里始终是服务端那一份，不做本地乐观拼接。
     *
     * 声明必须在 [togglePinMessage] 之前：那个函数按值读写这两个状态，Kotlin 的局部变量不能先用后声明。
     */
    var pinnedMessages by remember(conversationId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    /** 置顶/取消置顶的一句话结果；和图片那句反馈共用输入框上方那一行。 */
    var actionHint by remember { mutableStateOf<String?>(null) }
    var showMore by remember { mutableStateOf(false) }
    /** 表情面板开没开：和 [showMore] 互斥，拉开一个就把另一个收回去。 */
    var showEmotions by remember { mutableStateOf(false) }
    /** @ 面板开没开：只有群聊才给这个入口，私聊 @ 谁都没有意义。 */
    var showMentions by remember { mutableStateOf(false) }
    /**
     * 输入框有没有焦点。
     *
     * 它决定占位文案（有焦点才说「按住说话」），也决定面板要不要让位：面板开着的时候
     * 点一下输入框，键盘会起来、面板必须同时收起，否则两半屏幕各占一块。
     */
    var composerFocused by remember { mutableStateOf(false) }
    /** 三个面板里有没有开着的：只有互斥成立，这条才成立。 */
    val panelOpen = showMore || showEmotions || showMentions
    // 焦点与键盘的唯一持有处：开面板要收键盘、面板要回键盘，都从这里走。
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val composerFocus = remember { FocusRequester() }

    /** 三个面板一起收掉：互斥从此只有一个出口，不必每个回调手抄一遍。 */
    fun closePanels() {
        showMore = false
        showEmotions = false
        showMentions = false
    }

    /** 面板打开前先落这一步：清焦点 + 收输入法，否则面板会画在键盘底下。 */
    fun dismissKeyboard() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    /**
     * 已读：后端推一次已读位置，本机也当场把这一行清零。
     *
     * 后半句不是顺手做的：底栏「消息」那个数字由一份完整会话列表算出来，下一趟计数最晚要等十秒
     * （见 `CantaApp` 的 `CHAT_UNREAD_TTL_MILLIS`）。人读完消息退回消息页的那一刻，数字还挂在读之前
     * 的值上，看起来就是「点了群消息还在显示」。所以这里连本机快照一起改，不指望下一拍。
     */
    suspend fun markReadNow() {
        repository.markRead(owner, conversationId)
        // 推失败就别动本机：清掉之后服务端还挂着未读，下一趟全量会把它加回来，数字先消失再复活。
        if (repository.lastError == null && !repository.lastFailureWasNetwork) {
            ConversationCache.markRead(owner, conversationId)
        }
    }

    /**
     * 顶栏菜单里的「置顶 / 免打扰」：先按点击后的样子画，服务端说不行再退回去。
     *
     * 这两下是「我这么想」而不是「这样一定行」——会话列表是下一次进消息页才重新拉的，
     * 乐观更新让人立刻看到勾选变了，失败时退回原样，不需要弹一句错误提示。
     */
    fun togglePin() {
        val target = !pinned
        pinned = target
        scope.launch {
            val ok = runCatching { repository.pinConversation(owner, conversationId, target) }
                .getOrDefault(false)
            if (!ok) pinned = !target
        }
    }

    fun toggleMute() {
        val target = !muted
        muted = target
        scope.launch {
            val ok = runCatching { repository.setMuted(owner, conversationId, target) }
                .getOrDefault(false)
            if (!ok) muted = !target
        }
    }

    /**
     * 钉住 / 取消钉住一条消息。
     *
     * 没有乐观更新：置顶是"整个群都能看见"的动作，先画上再退回去会让人以为钉成了。所以等后端
     * 回话，再重拉一次置顶列表——顺带把别人刚钉的那条也带回来。
     */
    fun togglePinMessage(message: ChatMessage) {
        val target = pinnedMessages.none { it.id == message.id }
        scope.launch {
            val ok = runCatching { repository.pinMessage(owner, message.id, target) }
                .getOrDefault(false)
            if (ok) {
                pinnedMessages = runCatching {
                    repository.pinnedMessages(owner, conversationId)
                }.getOrDefault(pinnedMessages)
                actionHint = if (target) "已置顶这条消息" else "已取消置顶"
            } else {
                actionHint = "没钉上，检查代理后再试"
            }
        }
    }
    /**
     * 这一条草稿里点名了谁，存身份号码而不是名字：人可能在发出之前改名，号码不会。
     *
     * 发出成功就清空；发送失败保留着——重发的时候还是同一批人。
     */
    var pendingMentions by remember { mutableStateOf<List<String>>(emptyList()) }
    /** 撤回失败亮一句：样式与位置照 [sendFailed] 那行。 */
    var recallFailed by remember { mutableStateOf(false) }
    /**
     * 正在回复哪一条，null 表示没在回复。
     *
     * 存整条消息而不是只存 id：输入栏上方那行要显示「回复 某某：正文摘要」，正文就在手里，
     * 没必要为了画一行字再查一次。发送成功才清空——失败时要留着，重发还是回同一条。
     */
    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    /**
     * 正在转发哪一条，null 表示转发对话框没开。
     *
     * 和 [replyTarget] 分开：转发是一次性动作，选完会话就结束，不需要在输入栏上留痕。
     */
    var forwarding by remember { mutableStateOf<ChatMessage?>(null) }
    /** 转发对话框里的会话候选；打开对话框时拉一次，没拉到就是空列表（显示「没有别的会话」）。 */
    var forwardTargets by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    /** 只本机删掉失败亮一句，和 [recallFailed] 同一处显示。 */
    var hideFailed by remember { mutableStateOf(false) }
    /** 转发成/不成的一句话，2 秒后自己灭掉。 */
    var forwardHint by remember { mutableStateOf<String?>(null) }
    /** 正在选图或传图：挡住重复点「图片」，同时底部那一行亮「正在发送图片…」。 */
    var sendingImage by remember { mutableStateOf(false) }
    /**
     * 更多面板那四个钮的一句话反馈：图片没传成/没发出去、别的钮点了不该动，
     * 都借输入框下面这一行说，2 秒后自己灭掉。
     */
    var imageHint by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<ConversationDetail?>(null) }
    /**
     * 本群公告的正文（私聊恒为空）与它的指纹。
     *
     * 指纹用来记「这条公告本人已经确认过」：`长度:哈希` 就够了——换了公告内容就变，同一条公告
     * 重进页面还是同一个指纹。只在本机比对（见 [noticeSeen]），不进后端。
     */
    val noticeText = detail?.takeIf { it.isGroup }?.announcement.orEmpty()
    val noticeFingerprint = if (noticeText.isBlank()) {
        ""
    } else {
        "${noticeText.length}:${noticeText.hashCode()}"
    }
    /**
     * 公告横幅这一趟被关掉了没有。
     *
     * 跟着会话号和公告指纹一起重置：换了群、或者群主换了公告，横幅都该重新出现一次。
     */
    var announcementDismissed by remember(conversationId, noticeFingerprint) {
        mutableStateOf(false)
    }
    /** 公告全文对话框开没开：横幅点一下看全文（横幅只显示两三行）。 */
    var showAnnouncement by remember { mutableStateOf(false) }
    /** 刚复制的是哪一条：不是 null 的时候底部亮一句「已复制」，过一会儿自己灭掉。 */
    var copiedId by remember { mutableStateOf<Long?>(null) }
    // 跨平台剪贴板：与 TweaksScreen 里复制命令用的是同一个（LocalClipboardManager.setText）。
    val clipboard = LocalClipboardManager.current
    /** 上一次滚到底时最后一条的 id：只有尾巴变了才往下滚，往上翻历史不该把视图拽到底。 */
    var tailId by remember { mutableStateOf<Long?>(null) }
    /** 首屏已经落到底了没有：没落过底之前不往上翻页，否则一进页面就白拉一页历史。 */
    var settled by remember { mutableStateOf(false) }
    /**
     * 首屏那几轮"落到底"补完了没有。
     *
     * 和 [settled] 不是一件事：[settled] 管的是「能不能往上翻历史」，它只要求列表已经跳到尾巴
     * 一次；这里管的是「首屏的尺寸已经安定」，补完之前允许反复把视图落回最新那条（见下面那个
     * 首屏落底的 effect），补完之后只在真来了新消息时才落底。
     */
    var firstPaintDone by remember(conversationId) { mutableStateOf(false) }
    /**
     * 上一次整页核对是什么时候。[refreshTail] 按**时间**决定要不要顺带整页拉一次。
     *
     * 为什么不是按轮数：两档轮询间隔差五倍（无实时通道 2 秒、有实时通道 10 秒），按 30 轮算就是
     * 一分钟和五分钟两个结果——撤回这种原地变化在有推送的机器上要拖五分钟才对齐，那是肉眼可见的
     * 回退。按时间算，两种情况下都是同一分钟。
     *
     * 只在协程里读写、composition 不读它，所以它变了不会引起重组；按会话重置。
     */
    var lastFullAt by remember(conversationId) { mutableStateOf(0L) }
    val listState = rememberLazyListState()
    /** 正在全屏看哪张图（消息体里那个图片地址）；null 表示查看器没开。 */
    var viewingImage by remember { mutableStateOf<String?>(null) }
    /**
     * 已经解码好的图，按图片地址存。
     *
     * 只为了让查看器"点开就能看"：气泡那边刚解码完的那张直接拿来用，不必为了放大再下一次；
     * 同一个会话里翻回去点第二次也是立刻出来。不设上限——它跟这个页面的生命周期一样长，
     * 而一个会话里的图本来就有限（超过 10 天后端就标过期了）。
     */
    val decodedImages = remember { mutableStateMapOf<String, ImageBitmap>() }

    // 草稿回填：只在本地还空着的时候接受一次。人在打字时存储里那个旧值不该把它盖掉。
    LaunchedEffect(conversationId, initialDraft) {
        if (draft.isEmpty() && initialDraft.isNotEmpty()) draft = initialDraft
    }

    // 草稿落盘：600ms 没再敲字才写一次。key 里带 draft，所以每敲一个字都会把上一次的
    // LaunchedEffect 取消掉——这就是防抖，不用自己维护计时器。
    LaunchedEffect(conversationId, draft) {
        val persist = onDraftPersist ?: return@LaunchedEffect
        delay(CHAT_DRAFT_PERSIST_MILLIS)
        persist(draft)
    }

    // 一个会话一页：本地画着的那一屏照旧先画，之后只补「比它更新的那几条」——本地一条都没有才整页拉。
    // 读完把已读位置推到现在，顺便问一次会话详情。
    // 详情排在消息之后，是为了不让它的失败覆盖掉下面读的 lastError 文案。
    LaunchedEffect(conversationId, owner) {
        // 换一个会话就把面板全收掉：上一个会话开着的表情面板不该跟着过来。
        showMore = false
        showEmotions = false
        showMentions = false
        composerFocused = false
        // 有缓存就直接画缓存，不点 loading：那几行刚才还在屏幕上。
        loading = messages.isEmpty()
        loadError = null
        detail = null
        tailId = null
        settled = false
        firstPaintDone = false
        lastFullAt = 0L
        // 补差从哪儿往后问：手上最后一条，以及盘上记的尾巴，取小的那个。
        // 盘上那个可能比屏幕上最后一条更新（最新几条是分享卡片 / 名片，落不了盘），按它补会把中间
        // 那几条永远跳过；反过来说，盘上没有记录时就用手上这条，不会因为缺记录退化成整页拉。
        val paintedTail = messages.maxOfOrNull { it.id } ?: 0L
        val storedCursor = ChatMessageStore.latest?.cursor(conversationId) ?: 0L
        val localCursor = if (storedCursor > 0L) minOf(paintedTail, storedCursor) else paintedTail
        // 先进来就先落底：缓存里那一屏立刻贴在最新一条，用户不用等网络回来才「跳到新消息」。
        // 首屏之后还会补几轮（见下面「首屏落底」），这里先给一个初值，避免中间那几帧停在历史位置。
        if (messages.isNotEmpty()) listState.scrollToItem(0)
        if (messages.isNotEmpty() && localCursor > 0L) {
            // 冷启动 / 从消息页切回来：本地那一屏已经在屏幕上了，只问它之后的部分，正常就是一个 `[]`。
            //
            // 一次 50 条不够：盘上有游标、真实尾巴却差了两百条的时候（装完没打开过、或者在别的设备上
            // 聊过），只补 50 条会让首屏停在半路，剩下的靠 2 秒轮询一截一截往下长。所以这一趟连续
            // 拉到「回来的不满一页」为止——满一页说明后面还有，接着问；空一页或不满就是到底了。
            var from = localCursor
            while (true) {
                val page = repository.messagesAfter(owner, conversationId, from, ChatCatchupPage)
                if (page.isEmpty()) break
                // 和轮询、推送走同一个合并（[mergeMessages]）：本机的贴纸标记不能因为这一趟补差丢掉。
                messages = mergeMessages(page, messages)
                if (page.size < ChatCatchupPage) break
                from = page.last().id
            }
        } else {
            messages = repository.messagesFor(owner, conversationId, offset = 0, limit = ChatPageSize)
        }
        loadError = chatErrorOf(repository.lastError, repository.lastFailureWasNetwork)
        // 把尾巴记到盘上（只前进不后退）：下一次冷启动从这里往后补，不再整页拉。单独记一遍是因为
        // 整份落盘只在真有正文可写时才发生——一个会话最新那几条全是分享卡片时，它一条都不会写。
        messages.maxOfOrNull { it.id }?.takeIf { id -> id > 0L }?.let { id ->
            ChatMessageStore.latest?.putCursor(conversationId, id)
        }
        loading = false
        markReadNow()
        detail = repository.conversationDetail(owner, conversationId)
        // 群置顶消息：和详情一起问一次就够。置顶不常变，改的那一下自己会重拉（见 togglePinMessage）。
        pinnedMessages = if (detail?.isGroup == true) {
            // 读不到就是空列表：置顶拉不动不该让整页报错，入口行那格会老实说「还没有置顶消息」。
            runCatching { repository.pinnedMessages(owner, conversationId) }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
        // 顺手把「我把它置顶了没有 / 免打扰了没有」取回来：顶栏菜单的那两项要按它写文案。
        // 失败就当没有（保持不置顶、不免打扰），点一下仍然会真的改一次。
        val row = runCatching { repository.conversationsFor(owner) }
            .getOrNull()?.firstOrNull { it.id == conversationId }
        pinned = row?.pinned == true
        muted = row?.muted == true
    }

    /**
     * 整页拉一次最新 30 条并并进来：现在它只剩一个职责——**发现对方撤回 / 编辑了老消息**。
     *
     * 这类变化是原地改一条旧行的，id 比手上最新那条小，增量永远问不到；只有把这一页重新读回来
     * 对比才看得见。所以它由 [refreshTail] 低频触发（每隔 [TailFullIntervalMillis] 一次），不再每轮都跑。
     *
     * 只并尾巴，不动已经翻出来的历史。fresh 放在前面：同一 id 以后回来的为准，别的设备撤回
     * 的消息才能在本机也变灰；最后按 id 排回正序。有实质变化才走一次已读。
     */
    suspend fun refreshTailFull() {
        val fresh = repository.messagesFor(owner, conversationId, offset = 0, limit = ChatPageSize)
        if (fresh.isEmpty()) return
        val merged = mergeMessages(fresh, messages)
        if (merged != messages) {
            messages = merged
            markReadNow()
        }
    }

    /**
     * 只拉「比手上最新那条还新的」，正常就是一个空数组。
     *
     * 这是轮询的常态：没新消息时后端回 `[]`，几百字节，而不是把最近 30 条正文再搬一遍。
     * 手上一条都没有（首屏还没回来）时增量无从下手，退回一次整页拉取当起点。
     */
    suspend fun refreshTailDelta() {
        val newest = messages.maxOfOrNull { it.id } ?: 0L
        if (newest <= 0L) {
            refreshTailFull()
            return
        }
        val fresh = repository.messagesAfter(owner, conversationId, newest, ChatNewLimit)
        if (fresh.isEmpty()) return
        val merged = mergeMessages(fresh, messages)
        if (merged != messages) {
            messages = merged
            markReadNow()
        }
    }

    /**
     * 把尾巴拉一次并并进来：常态是增量，每隔 [TailFullIntervalMillis] 顺带整页核对一次。
     *
     * 名字保住是有原因的：[waitForAiReply]、[refreshNew] 和兜底轮询都调它，改名要连带改一串调用点。
     * 对外语义只有一条：**调一次一定看得见新消息**；撤回这种原地变化由定时那一趟
     * [refreshTailFull] 兜住，最坏晚 [TailFullIntervalMillis] 被发现。
     *
     * 计时只看时间，不看轮数：轮询有两档间隔（2 秒 / 10 秒），按轮数算会变成"一分钟"和"五分钟"
     * 两个答案。推送不推进这个计时——它是事件驱动的，掺进来会让整页核对忽快忽慢。
     */
    suspend fun refreshTail() {
        refreshTailDelta()
        val now = currentTimeMillis()
        if (now - lastFullAt >= TailFullIntervalMillis) {
            lastFullAt = now
            refreshTailFull()
        }
    }

    /**
     * 刚点了群聊 AI、正在等它回答。
     *
     * 只影响顶栏那一行（「群聊AI 正在思考…」）和一次短轮询；不写库、不发消息——
     * 真正的回答由后端写回来，这里只是让人知道"它收到了"，而不是盯着屏幕猜。
     *
     * 声明必须在 [waitForAiReply] 之前：Kotlin 的局部函数不能引用晚于它声明的本地变量。
     */
    var aiThinking by remember(conversationId) { mutableStateOf(false) }

    // 每变一次（新来的、发出的、撤回的、往上翻的）都把这一屏写回缓存：下一次进这个会话先画它。
    LaunchedEffect(conversationId, messages) {
        if (conversationId > 0L && messages.isNotEmpty()) {
            ChatMessageCache.put(conversationId, messages)
        }
    }

    /**
     * 等这一轮群聊 AI 的回答写回来。
     *
     * 只在「这条正文点到了 @群聊AI」之后跑：服务端 cron 是每分钟一拍，不等就等于让人盯着屏幕猜
     * 它到底收到没有。轮询是幂等的（服务端先抢占再写回），问密一点只是多几次几毫秒的查询。
     */
    suspend fun waitForAiReply() {
        aiThinking = true
        try {
            repeat(ChatAiPollTries) {
                delay(ChatAiPollMs)
                val arrived = runCatching {
                    repository.pollAiReply(owner, conversationId)
                }.getOrDefault(0)
                if (arrived > 0) {
                    refreshTail()
                    return
                }
            }
        } finally {
            aiThinking = false
        }
    }

    /**
     * 推送来了：只问「比我手上最新那条还新的有几条」。
     *
     * 这就是 [refreshTailDelta] 那一次请求，所以直接转给它，两条路的行为不会漂。
     * 推送不推进整页核对的计数：撤销 / 编辑老消息本来就是轮询在兜（见 [refreshTail]）。
     */
    suspend fun refreshNew() {
        refreshTailDelta()
    }

    // 实时通道能不能用：连上了才算数。连不上（网页版没有引擎、被墙、key 不对）就退回快轮询，
    // 免得页面以为有人推、实际上 10 秒才问一次。
    var liveUp by remember(conversationId) { mutableStateOf(false) }

    // 实时通道：服务端在 `messages` 上有 INSERT 触发器，往 `chat:<id>` 广播一声，
    // 这里收到就立刻拉一次——对方发完到你看见之间不再有「等下一个轮询点」。
    LaunchedEffect(conversationId, owner, liveSource) {
        val source = liveSource ?: return@LaunchedEffect
        if (conversationId <= 0L) return@LaunchedEffect
        liveUp = true
        // 传上来的是 Flow，异常已经在里面吞过；这里再兜一层，推送坏了也不能让页面停摆。
        runCatching { source.updates(conversationId).collect { refreshNew() } }
        liveUp = false
    }

    // 兜底轮询：实时通道在跑时只当保险（10 秒一次），没有它就是唯一的来源（2 秒一次）。
    // 每轮跑的都是增量（[refreshTailDelta]），只有到点的那一轮才会顺带整页核对（见 [refreshTail]）。
    LaunchedEffect(conversationId, owner, liveUp) {
        if (conversationId <= 0L) return@LaunchedEffect
        val interval = if (liveUp) ChatPollFallbackMs else ChatPollMs
        while (true) {
            delay(interval)
            refreshTail()
        }
    }

    /**
     * 新消息落到最下面才看得见；列表正序 + `reverseLayout`，所以`scrollToItem(0)` 就是落到最新那条。
     *
     * 两条规则必须分开，混在一起就会出现「一直往上滑，新消息越来越多」：
     * - **来新消息**（键是尾巴 id）：只要人贴在底部，就跟着滚到底；人在翻历史，就**不动视图**，
     *   只把这条记成未读，靠右下角那个胶囊提示。
     * - **往上翻历史**（并进来一页旧的）：只改 [messages]，绝不落底、也绝不碰未读数。
     *
     * 首屏不算「新消息」：那时列表还没量出高度，`scrollToItem` 落不到位，交给下面那段「首屏落底」。
     */
    LaunchedEffect(messages.lastOrNull()?.id) {
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        if (last.id != tailId) {
            tailId = last.id
            if (firstPaintDone) {
                val bottomNow = atBottom
                if (bottomNow) {
                    listState.scrollToItem(0)
                } else if (seenTailId != null && last.id > (seenTailId ?: 0L)) {
                    unseenNew = unseenNew + last.id
                }
                seenTailId = last.id
            }
        } else if (!firstPaintDone) {
            // 首屏（消息数量没变）：还是落一次底，只是这里先给一个初值。
            listState.scrollToItem(0)
        }
        // 首屏（或新消息）已经落到底：从现在起，滚到顶才算"要翻历史"。
        settled = true
    }

    /**
     * 进来就看见最新那条（群聊最常说的「一进群就该在底部」）。
     *
     * 上面那个 effect 在首屏只能落一次：列表这时还没测高完，落到的位置和最终的布局差一截；
     * 而且首屏之后紧接着可能来一页增量、或者视口变高（上面的输入栏、下面的导航区），都要重落。
     * 所以首屏专门补三轮：立刻、下一帧、再下一帧各落一次，然后把 [firstPaintDone] 置真，
     * 之后一律只在"来了新消息"时落底，不再劫持用户往上翻的位置。
     */
    LaunchedEffect(conversationId, messages.isNotEmpty()) {
        if (firstPaintDone || messages.isEmpty()) return@LaunchedEffect
        repeat(FirstPaintScrollPasses) { pass ->
            // reverseLayout 下 index 0 就是最新那条，也就是底部。
            listState.scrollToItem(0)
            if (pass < FirstPaintScrollPasses - 1) {
                withFrameNanos { }
                delay(FirstPaintScrollPassMs)
            }
        }
        firstPaintDone = true
        // 首屏即底：那时屏幕上的所有消息都已经看过了，未读基线从这里开始算。
        seenTailId = messages.lastOrNull()?.id
        unseenNew = emptySet()
    }

    // 视口会变矮：键盘弹起、输入框从 40dp 长到 120dp、表情/更多面板拉开、快捷表情条出现。
    // LazyColumn 这时只会守住原来的滚动位置，最新那一条就被输入栏压掉一半——所以视口一变矮，
    // 只要刚才贴在底部，就把最后一条重新顶回可见区。变化前的高度/底部那条要自己记，
    // 因为 layoutInfo 里读到的已经是新布局。
    LaunchedEffect(conversationId) {
        var previousHeight = 0
        var previousBottomIndex = -1
        snapshotFlow {
            // reverseLayout 的起点是底部：`firstVisibleItemIndex` 就是压在视口下沿的那一条，
            // 它是 0 就说明刚才贴在最新一条上。
            listState.layoutInfo.viewportSize.height to listState.firstVisibleItemIndex
        }.collect { (height, bottomIndex) ->
            if (height > 0 && height < previousHeight && previousBottomIndex == 0) {
                listState.scrollToItem(0)
            }
            previousHeight = height
            previousBottomIndex = bottomIndex
        }
    }

    /** 滚到最新一条：胶囊和向下箭头共用这一个动作。 */
    val scrollToBottom: () -> Unit = {
        if (messages.isNotEmpty()) {
            // 点了就是「这些都算看过」：先清数字再滚，动画那一帧不会先亮一个旧计数。
            settleSeen()
            scope.launch { listState.animateScrollToItem(0) }
        }
    }

    // 视图停在最顶那一行时往回翻一页。loadingEarlier 挡住重入，空结果就静默认了。
    // 注意 keys 里不能放 loadingEarlier：这个 effect 一置位就把自己取消掉，翻页永远发不出去。
    // reverseLayout 把方向倒过来了：最旧那条是索引最大的那一条，它露出来才算"翻到顶"。
    val atTop by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            // 用最大索引而不是 `lastOrNull().index`：倒序列表里可见项是按屏幕位置排的，
            // 最上面那一条才是索引最大的那条（最旧的）。
            total > 0 && (info.visibleItemsInfo.maxOfOrNull { it.index } ?: -1) >= total - 1
        }
    }
    LaunchedEffect(atTop, conversationId, settled) {
        if (!settled || !atTop || loadingEarlier) return@LaunchedEffect
        val oldest = messages.firstOrNull() ?: return@LaunchedEffect
        loadingEarlier = true
        try {
            val older = repository.messagesBefore(owner, conversationId, oldest.id, ChatPageSize)
            if (older.isNotEmpty()) {
                // 旧的那一页排在"更上面"：倒序列表里它们是**后面**的索引，所以现有每一条的索引
                // 一个都没变，视口自己就停在原处，不用再补一次 scrollToItem。
                messages = (older + messages).distinctBy { message -> message.id }
            }
        } finally {
            // 被取消（下拉到底、换会话）也要把闸门放回 false，否则再也翻不了页。
            loadingEarlier = false
        }
    }

    // 滚到底了没有：reverseLayout 下压在视口下沿的是索引 0，那就是最新一条。
    val atBottom by remember {
        derivedStateOf {
            listState.layoutInfo.totalItemsCount > 0 && listState.firstVisibleItemIndex == 0
        }
    }
    /**
     * 基线：这一条及更早的都已经看过了。首屏落底时钉住，之后只前进不后退。
     *
     * 胶囊上的数字**不**看滚动位置。以前那个数是直接取 `firstVisibleItemIndex` 的，于是往上翻
     * 历史时索引越涨数字越大（滑得越多，「新消息」越多）——那是把「翻到了第几条历史」当成了未读。
     */
    var seenTailId by remember(conversationId) { mutableStateOf<Long?>(null) }
    /** 不在底部时新到的消息 id：去重后的大小就是胶囊上的数字，滚到底清空。 */
    var unseenNew by remember(conversationId) { mutableStateOf<Set<Long>>(emptySet()) }
    // 首屏落到底就把基线钉在这里：进来时已经在屏幕上的那些不叫「新消息」。
    // 依赖 messages 的尾巴：首屏那段 effect 跑完之前消息可能还没到，晚一步也要补上。
    LaunchedEffect(conversationId, firstPaintDone, messages.lastOrNull()?.id) {
        if (firstPaintDone && seenTailId == null) seenTailId = messages.lastOrNull()?.id
    }
    /**
     * 「这些都已经看过了」：滚到底或者点了胶囊之后调用一次。
     *
     * 先在动画之前清：胶囊在滚动动画期间还会画一帧，不清就会先亮一个旧计数。
     */
    val settleSeen: () -> Unit = {
        seenTailId = messages.lastOrNull()?.id
        unseenNew = emptySet()
    }

    /** 基线与未读集合只管「页面里的新消息」，换一个会话就从头算。 */
    LaunchedEffect(conversationId) {
        seenTailId = null
        unseenNew = emptySet()
    }

    /**
     * 人在底部 → 这些都算看过：清掉未读集合，基线推到尾巴。
     *
     * 只认「是否贴底」，**不**认「第几条」：往上翻历史时 `firstVisibleItemIndex` 会一路涨，
     * 拿它当未读数就是「越翻越多」那个 bug。这里只在真的沉到底时才动这两个状态。
     */
    LaunchedEffect(atBottom, messages.lastOrNull()?.id) {
        if (atBottom && messages.isNotEmpty()) settleSeen()
    }

    /** 发一条：写成功才清空输入框，写失败把输入留着，别让人重打一遍。 */
    val send: () -> Unit = {
        val body = draft.trim()
        // 名字/头像没设完：输入栏已经被 [GateComposerBar] 换掉了，这里挡住的是回车与粘贴那条路。
        if (!setupGate.ready) {
            imageHint = setupGate.refusal
        } else if (body.isNotEmpty() && !sending) {
            scope.launch {
                sending = true
                sendFailed = false
                val sent = repository.sendMessage(
                    owner = owner,
                    conversation = conversationId,
                    body = body,
                    mentions = pendingMentions,
                    // 回的是哪一条。null 就是普通发言，后端那边 `p_reply_to` 走默认值。
                    replyTo = replyTarget?.id,
                )
                if (sent != null) {
                    messages = messages + sent
                    draft = ""
                    // 名单跟着草稿一起清：下一条要 @ 谁得重新点。
                    pendingMentions = emptyList()
                    // 引用也跟着清：这条已经发出去了，下一条默认不带引用。
                    replyTarget = null
                    // 这条点到了 @群聊AI：自己催几次，别等 cron 那一分钟。
                    if (isGroup && ChatAiTrigger.containsMatchIn(body)) {
                        scope.launch { waitForAiReply() }
                    }
                } else {
                    sendFailed = true
                }
                sending = false
            }
        }
    }

    /**
     * 点一颗快捷表情：**当场发出去**，不经过输入框。
     *
     * 和输入栏的发送共用同一个 `sendMessage`，失败也共用同一条「没发出去，检查代理后再试」；
     * 差别只在于正文是那一颗的 token（如 `[偷笑]`），发出去之后气泡里画成图。
     */
    val sendSticker: (Emotion) -> Unit = { emotion ->
        // 快捷表情也只是一句话：闸门没开就同样发不出去，还要说清差哪一步。
        if (!setupGate.ready) {
            imageHint = setupGate.refusal
        } else if (!sending && conversationId > 0L) {
            scope.launch {
                sending = true
                sendFailed = false
                val sent = repository.sendMessage(
                    owner = owner,
                    conversation = conversationId,
                    body = emotion.token,
                    mentions = emptyList(),
                    replyTo = replyTarget?.id,
                )
                if (sent != null) {
                    // 打上本机标记：这一条画成大贴纸。后端不认这个 kind，刷新合并时由
                    // [mergeMessages] 保住它。
                    messages = messages + sent.copy(kind = StickerKind)
                    // 发过的进「最近使用」，和面板里点过一颗是同一种记法。
                    rememberEmotionPick(emotion.name)
                    replyTarget = null
                } else {
                    sendFailed = true
                }
                sending = false
            }
        }
    }

    /**
     * 发一张图：选 → （超了就压）→ 传 → 登记，都走完才把气泡画出来。
     *
     * 选图返回 null 就静默退出：用户取消不是错误，桌面/网页本来也没有相册（[pickImage] 的默认实现
     * 就返回 null），这时候弹一句红字只会让人以为自己做错了事。
     *
     * 2.5MB 是**我们自己的规则**（图床不限大小，见 [ImageBed.MAX_BYTES]）：超了就交给
     * [compressImage] 压一次；压不动（null）或者压完还超，就说一句实话然后不发——不硬塞一张
     * 8MB 的原图上去，也不偷偷把图降到看不清。
     *
     * 上传走图床（[SocialRepository.uploadChatImageToImageBed]），消息体里存的是**完整地址**
     * （`https://.../file/...`），气泡照着它直接 GET 就能画。上传成功但登记失败时，图已经躺在图床
     * 上了，这一层没有删它的接口，所以只提示，不假装撤回过。
     */
    val sendImage: () -> Unit = {
        if (!setupGate.ready) {
            // 发图也是发言：先挡住，别让人白挑一张、压一遍、传上去。
            imageHint = setupGate.refusal
        } else if (!sendingImage) {
            scope.launch {
                sendingImage = true
                imageHint = null
                try {
                    val picked = pickImage()
                    if (picked != null) {
                        // 压图是同步的重活，挪到后台线程：直接在这儿调会让那几百毫秒卡在界面上。
                        val bytes = if (picked.size > ImageBed.MAX_BYTES) {
                            withContext(Dispatchers.Default) { compressImage(picked) }
                        } else {
                            picked
                        }
                        if (bytes == null || bytes.size > ImageBed.MAX_BYTES) {
                            imageHint = "这张图超过 2.5MB，压不下来，换一张或先在相册里裁一下"
                        } else {
                            // 目录按会话分（`unicanta/群聊/<会话号>/`）：时间戳加随机数让同一张图重发
                            // 也不撞名，图床是"重名就 409"的。
                            val id = ImageBed.chatImageId(
                                conversationId = conversationId,
                                stamp = currentTimeMillis(),
                                salt = Random.nextInt(1_000_000),
                            )
                            val url = repository.uploadChatImageToImageBed(id, bytes, "image/jpeg")
                            if (url == null) {
                                imageHint = "图片没上传成，检查代理后再试"
                            } else {
                                val sent = repository.sendImageMessage(owner, conversationId, url)
                                if (sent == null) {
                                    imageHint = "图片没发出去"
                                } else {
                                    messages = chatAppend(messages, sent)
                                    // 发出去就把面板收起来：让人直接看见那张图落到了最下面。
                                    showMore = false
                                }
                            }
                        }
                    }
                } finally {
                    // 选图被打断（换会话、页面走了）也要把闸门放开，否则这个钮再也点不动。
                    sendingImage = false
                }
            }
        }
    }

    /** 快捷短语 / 语音 / 表情都往输入框里塞字：前面有字又不是空格就隔开一个。 */
    val insert: (String) -> Unit = { piece ->
        draft = if (draft.isEmpty() || draft.last().isWhitespace()) {
            draft + piece
        } else {
            "${draft} ${piece}"
        }
    }

    /**
     * 把一个人点进草稿：正文里加 `@名字`，名单里加他的号码。
     *
     * 正文里那串只是一句给人看的话，后端不拿它反查是谁——名字会变，号码不会。所以两处都要加，
     * 而且名单要先去重：同一个人点两次只该有一条记录，正文里也只会多一个 `@名字`。
     */
    val mention: (ConversationMember) -> Unit = { member ->
        val handle = member.name.ifBlank { "#${member.owner}" }
        if (pendingMentions.none { it == member.owner }) {
            pendingMentions = pendingMentions + member.owner
            // 尾巴带一个空格：接着打的字不会粘在名字后面（`@张三你好` 会被整段当成一个 @）。
            insert("@$handle ")
        } else {
            // 已经点过就只把正文补上，名单不动。
            insert("@$handle ")
        }
        showMentions = false
    }

    /**
     * 复制一条消息：整条正文进剪贴板，底部亮一句。别人的长按直接进来，自己的从长按菜单的「复制」进来。
     *
     * 图片消息的 [ChatMessage.body] 是图片地址（图床的 URL 或老消息的 bucket 路径），不是人写的字：
     * 复制过去也没法用，所以给空串。
     */
    val copyMessage: (ChatMessage) -> Unit = { message ->
        clipboard.setText(AnnotatedString(if (message.kind == "text") message.body else ""))
        copiedId = message.id
    }

    /**
     * 撤回自己的一条：问后端 [SocialRepository.deleteMessage]，成了把本地这条标成已撤回
     * （重画成一行灰字），败了底部亮一句提示，气泡原样不动。
     */
    val recallMessage: (ChatMessage) -> Unit = { message ->
        scope.launch {
            if (repository.deleteMessage(owner, message.id)) {
                recallFailed = false
                messages = messages.map { current ->
                    if (current.id == message.id) current.copy(removed = true) else current
                }
            } else {
                recallFailed = true
            }
        }
    }

    /**
     * 只在自己这一侧删掉一条：调 [SocialRepository.hideMessage]，成了把这条从列表里摘掉。
     *
     * 摘掉而不是标成「已撤回」——那两个字的意思是「对所有人把正文拿掉了」，而这条对别人来说
     * 原样还在，画成已撤回是在撒谎。下次重新拉这一页，后端也不会再把它带回来。
     */
    val hideMessageInPlace: (ChatMessage) -> Unit = { message ->
        scope.launch {
            if (repository.hideMessage(owner, message.id)) {
                hideFailed = false
                messages = messages.filterNot { it.id == message.id }
            } else {
                hideFailed = true
            }
        }
    }

    /**
     * 打开转发对话框：先把能转发到的会话拉一遍。
     *
     * 拉失败就留空列表——对话框里显示「没有别的会话」，比弹一句红字有用：人至少知道现在
     * 没得选，而不是以为整个功能坏了。
     */
    val openForward: (ChatMessage) -> Unit = { message ->
        forwarding = message
        forwardHint = null
        scope.launch {
            forwardTargets = runCatching { repository.conversationsFor(owner) }
                .getOrDefault(emptyList())
        }
    }

    /**
     * 转发到 [target]。
     *
     * 成了就把对话框收起来并亮一句「已转发」；败了**留着对话框**——人还站在原地，再选一个
     * 会话重试是显然的下一步，关掉反而要重新长按一次。
     */
    val forwardTo: (Conversation) -> Unit = { target ->
        val message = forwarding
        if (message != null) {
            scope.launch {
                if (repository.forwardMessage(owner, message.id, target.id)) {
                    forwarding = null
                    forwardHint = "已转发"
                } else {
                    forwardHint = "没转发成，检查代理后再试"
                }
            }
        }
    }

    // 剪贴板本身没有任何看得见的反应：不说一句，人不知道自己按成功没有。1.6 秒后自己灭掉。
    LaunchedEffect(copiedId) {
        if (copiedId != null) {
            delay(1600L)
            copiedId = null
        }
    }

    // 更多面板那几句反馈只借一次机会：说清 2 秒就收走，别一直挂在输入框下面。
    LaunchedEffect(imageHint) {
        if (imageHint != null) {
            delay(2000L)
            imageHint = null
        }
    }

    // 转发那句结果也只借一次机会：成功是个很短的动作，一条「已转发」挂在输入框下面
    // 超过两秒就变成噪音了。对话框里那一份跟着一起收，人已经看见了。
    LaunchedEffect(forwardHint) {
        if (forwardHint != null) {
            delay(2000L)
            forwardHint = null
        }
    }

    // 置顶/取消置顶那一句结果：和「转发成不成」同一套，两秒后自己收走。
    LaunchedEffect(actionHint) {
        if (actionHint != null) {
            delay(2000L)
            actionHint = null
        }
    }

    // ---------------------------------------------------------------- 已读回执 / 正在输入 / 翻译
    //
    // 一次调用两样东西：别人的读位置和谁在打字（见 `SocialRepository.conversationLive`）。
    // 拉到 null 就留着上一次的——"这一次没问到"不该让「正在输入」突然消失。
    var presence by remember(conversationId) { mutableStateOf<LivePresence?>(null) }
    LaunchedEffect(conversationId, owner) {
        if (conversationId <= 0L || owner.isEmpty()) return@LaunchedEffect
        while (true) {
            presence = repository.conversationLive(owner, conversationId) ?: presence
            delay(ChatPresencePollMs)
        }
    }

    // 我在打字：4 秒最多告诉服务端一次。失败不提示——下一键还会再发一遍。
    var typingPingAt by remember(conversationId) { mutableStateOf(0L) }

    fun noteTyping() {
        if (conversationId <= 0L || owner.isEmpty()) return
        val now = currentTimeMillis()
        if (now - typingPingAt < ChatTypingPingMs) return
        typingPingAt = now
        scope.launch { runCatching { repository.markTyping(owner, conversationId) } }
    }

    // 按条翻译：消息 id → 译文。只活在这一次打开里——「看一眼这条说的什么」不是消息的属性，
    // 退出页面就该忘掉，否则换个人拿这台手机还能读到译文。
    var translations by remember(conversationId) { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var translating by remember(conversationId) { mutableStateOf<Set<Long>>(emptySet()) }
    var translateHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(translateHint) {
        if (translateHint != null) {
            delay(2200L)
            translateHint = null
        }
    }
    suspend fun translateMessage(message: ChatMessage) {
        // 图像、卡片、已经在翻的那一条都不再发请求：点了没反应比重复请求好。
        if (message.kind != "text" || message.body.isBlank() || message.id in translating) return
        translating = translating + message.id
        val target = if (message.body.any { it in '\u4E00'..'\u9FFF' }) "en" else "zh-CN"
        val result = runCatching { repository.translate(message.body, target) }.getOrNull()
        translating = translating - message.id
        if (result.isNullOrBlank()) {
            translateHint = "没翻成：这个免费接口可能到今天的额度了，过一会儿再试"
        } else {
            translations = translations + (message.id to result)
        }
    }

    // 观感上的群聊：形参说不是、但详情说是，就按群聊画（同一个判据见 ConversationDetail.isGroup）。
    val grouped = isGroup || detail?.isGroup == true
    // 能不能发图：私聊一直可以；群里看群主那个「允许群成员发图片」开关（detail.allowImage）。
    // 关着的时候后端 `message_send_image` 也会拒，所以这里是把后端那条裁决先说给人听，
    // 不是"偷偷藏一个按钮"。群主自己不受限（后端也放他过）：他要能先把图放进群里。
    val imagesAllowed = !grouped || detail?.allowImage == true || detail?.isOwner(owner) == true
    // 标题：形参空的时候用详情里的群名，都空就叫「私聊」；人数优先用调用方给的，没有数成员。
    val heading = title.ifBlank { detail?.title.orEmpty() }.ifBlank { "私聊" }
    // 人数用后端给的总数：`members` 只有前 200 个（官方群几千人），数它会数少。
    val members = if (memberCount > 0) memberCount else detail?.memberTotal ?: 0
    // 私聊里"另一个人"的身份：详情成员表里第一个不是自己的那个。取不到就是空串，
    // 顶栏据此不挂点击——宁可点不动，也不要推开一张没有内容的主页。
    val peerOwner = detail?.members?.firstOrNull { member -> member.owner != owner }?.owner.orEmpty()

    // 列表是从底部往上排的（LazyColumn 的 reverseLayout），所以喂给 items 的是倒过来的视图：
    // index 0 是最新那条、index + 1 才是更旧的那条。它只是个视图，不复制列表。
    val ordered = messages.asReversed()

    // 已读回执挂在自己**最后一条**下面：微信、抖音都是这个位置，人也只在那儿找。
    // 判据是"别人的读位置比这条的创建时间晚"——后端每人只存一个 last_read_at，不存每条回执。
    val lastMine = messages.lastOrNull { it.sender == owner }
    val lastMineAt = lastMine?.let { epochSecondsOf(it.createdAt) }
    val readBy = if (lastMineAt == null) {
        0
    } else {
        presence?.reads?.values?.count { stamp -> (epochSecondsOf(stamp) ?: 0L) >= lastMineAt } ?: 0
    }
    // 官方群（灰度测试群、Bug 反馈）几千人：一条消息底下挂「2331 人已读」既没有意义又很吵，
    // 这两个群不画已读回执。判据用群名——官方群的名单在 OfficialGroups 里，改名时跟着改那一处。
    val officialGroup = title in OfficialGroups.titles || (detail?.title ?: "") in OfficialGroups.titles
    val receiptText = when {
        lastMine == null || presence == null || readBy <= 0 -> null
        officialGroup -> null
        grouped -> "$readBy 人已读"
        else -> "已读"
    }
    // 顶栏那一行：「别人正在输入」优先；没人打字但自己刚叫了 AI，就写 AI 在思考。
    val typingLine = presence?.typingLine.orEmpty()
        .ifEmpty { if (aiThinking) "群聊AI 正在思考…" else "" }

    Scaffold(
        topBar = {
            ChatTopBar(
                title = heading,
                isGroup = grouped,
                memberCount = members,
                peerOwner = peerOwner,
                // 群头像拼九宫格要用成员名单；私聊传空（那边画对方一个人的头像）。
                groupMembers = detail?.members.orEmpty(),
                conversationId = conversationId,
                typingLine = typingLine,
                onNavigateBack = onNavigateBack,
                onOpenGroupInfo = onOpenGroupInfo,
                onOpenProfile = onOpenProfile,
                pinned = pinned,
                muted = muted,
                onTogglePin = { togglePin() },
                onToggleMute = { toggleMute() },
                // 群公告：从会话详情里拿。私聊没有公告，传空串就是「还没有公告」。
                announcement = noticeText,
                // 置顶消息：进页面时拉一次，钉/取消钉之后由 togglePinMessage 重拉。
                pinnedMessages = pinnedMessages,
                // 收藏：本机那一份（上层按会话号筛好传进来）。
                savedMessages = savedMessages,
                // 「聊天」那一下：入口行收起 + 回到最新一条（reverseLayout 里 0 就是尾巴）。
                onJumpToLatest = scrollToBottom,
                // 公告卡片不挂「编辑」：写公告统一在群设置页（点头像或点「设置」进去）。
            )
        },
        // 底部 inset 只留一份。Scaffold 默认已经给内容一份 navigationBars，内容里再来一次
        // `navigationBarsPadding()`、再加一次 `imePadding()`，就是三份叠在一起：三键导航
        // （经典导航键）下导航栏本身就有 48dp 左右，叠出来近 100dp 的空白露着 Scaffold 的黑底，
        // 键盘弹出时正好卡在输入栏和键盘之间——群里报的「键盘与输入栏之间的黑框」就是它。
        // 水平方向照旧留给 Scaffold（横屏时的侧边导航栏、刘海屏的侧边缺口）。
        contentWindowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal),
    ) { padding ->
        // 整页吃输入法 inset：edge-to-edge 下系统不再缩窗口，只有吃到 ime inset 才会把输入栏
        // 顶到键盘上方（不加的话键盘直接盖住输入栏）。键盘展开时 ime inset 已经把导航栏那一段
        // 算进去了，所以这里取并集而不是相加——相加就是上面说的那条黑框。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(
                    WindowInsets.ime
                        .union(WindowInsets.navigationBars)
                        .only(WindowInsetsSides.Bottom),
                ),
        ) {
            // 群公告横幅：群里发过公告就挂在消息上面（微信就是这个位置），点开看全文。
            // 顶栏那排入口里的「公告」格子是同一个内容的第二个入口——横幅会被往下翻走，格子不会。
            //
            // 确认过就不再出现：指纹与 [noticeSeen] 相同时，这条公告本人已经点过「知道了」，
            // 每次进来都再挂一条、每次都要再关一次，那不是提醒，是噪音。
            if (
                grouped &&
                noticeText.isNotBlank() &&
                noticeFingerprint != noticeSeen &&
                !announcementDismissed
            ) {
                ChatAnnouncementBanner(
                    text = noticeText,
                    onOpen = { showAnnouncement = true },
                    onClose = {
                        // 关掉也算确认：这一条不再自动出现，换新公告（指纹变了）才会再来一次。
                        announcementDismissed = true
                        onNoticeSeen(noticeFingerprint)
                    },
                )
            }
            loadError?.let { reason -> ChatErrorLine(reason) }
            // 翻译失败那句只借一次机会：它和 loadError 同一个样式，两秒后自己收走。
            translateHint?.let { line -> ChatErrorLine(line) }
            Box(
                // 点消息区的空白处 = 收起输入：清焦点、收输入法，顺便把三个面板一起收掉
                // （面板本来就和键盘互斥，点空白时两边都该退场）。
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .dismissInputOnTap { closePanels() },
                contentAlignment = Alignment.TopStart,
            ) {
                when {
                    loading -> Text("…", fontSize = 12.sp, modifier = Modifier.padding(14.dp))

                    messages.isEmpty() -> Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "还没有消息，打个招呼吧",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "这里只显示你和对方（或群里）说过的话。",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        // 从底部往上排：最新那条天生就在最底下，一进页面不用再靠几次 `scrollToItem`
                        // 去"追"底（追的那几帧就是"从头像开始"那一眼）。消息不够一屏时也是贴着输入栏
                        // 往上长，不会吊在屏幕顶上。
                        reverseLayout = true,
                    ) {
                        // 日期分隔画在每条消息自己的 item 里，不额外占一个索引：
                        // 这样 layoutInfo 里的索引仍然与 ordered 一一对应，下面的"在不在底"才算得准。
                        itemsIndexed(ordered, key = { _, message -> message.id }) { index, message ->
                            val previous = ordered.getOrNull(index + 1)
                            val newDay = previous == null ||
                                chatDayOf(previous.createdAt) != chatDayOf(message.createdAt)
                            // 同一天里隔得久了也起一条居中时间（微信是 5 分钟）：
                            // 换天显示完整日期，同一天只显示 HH:mm。
                            val gap = previous?.let { prev ->
                                val before = epochSecondsOf(prev.createdAt)
                                val now = epochSecondsOf(message.createdAt)
                                if (before != null && now != null) now - before else null
                            }
                            val showStamp = newDay || (gap != null && gap >= ChatStampGapSeconds)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (showStamp) {
                                    val stamp = if (newDay) {
                                        chatDayStampOf(message.createdAt)
                                    } else {
                                        chatClockOf(message.createdAt)
                                    }
                                    if (stamp.isNotEmpty()) ChatDayDivider(stamp)
                                }
                                if (message.removed) {
                                    // 撤回过的消息不画气泡：一行居中灰字，不带头像，也不参与长按复制。
                                    Text(
                                        text = "该消息已撤回",
                                        style = FlText.caption2,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else if (message.kind == "expired") {
                                    // 过期的图片后端不再留存，只剩一行灰字占住当时的位置。
                                    Text(
                                        text = "图片已过期（只保留 10 天）",
                                        style = FlText.caption2,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    ChatBubble(
                                        // 图片要自己按 storage 路径去取字节，所以把仓库也递进去。
                                        repository = repository,
                                        // 和全屏查看器共用同一份解码结果：点开时不必再下一次。
                                        imageCache = decodedImages,
                                        message = message,
                                        mine = message.sender == owner,
                                        // 自己那张头像上画名字首字：后端没有头像字段，首字就是兜底。
                                        myLabel = userName.ifBlank { "我" },
                                        // 自己那张头像：换了头像之后同一个 URL 就是新图（覆盖式上传）。
                                        myAvatarUrl = personAvatarUrl(owner),
                                        // 右边那张头像的配色：和聊天页其它话题一致的主色实底 + 白字。
                                        // 与左边别人的「淡主色底 + 主色字」形成对照，两边一眼分得开。
                                        myAvatarBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        myAvatarLetterColor = Color.White,
                                        // 群聊里别人的气泡要说清是谁：一对一的对方不用说，页面标题就是名字。
                                        showSender = grouped && message.sender != owner,
                                        // 点头像看那个人的主页：自己那条不画头像，这个回调也就不给。
                                        // 群里谁发言谁有自己的一张头像，身份就是 message.sender。
                                        // AI 那条除外：它的 sender 是个占位值，主页上根本没有这个人，
                                        // 点了只会进一个查不到的空页——所以它是一张不可点的头像。
                                        onAvatarClick = if (
                                            message.sender != owner &&
                                            message.sender.isNotEmpty() &&
                                            message.sender != AiSender
                                        ) {
                                            { onOpenProfile(message.sender) }
                                        } else {
                                            null
                                        },
                                        // 点自己那张头像：进个人主页换头像（这一页唯一一处"这是我"）。
                                        // 自己那一侧的头像在 [ChatBubble] 里画，所以这里单独给一个回调。
                                        onMyAvatarClick = if (owner.isNotEmpty()) {
                                            { onOpenProfile(owner) }
                                        } else {
                                            null
                                        },
                                        // 长按弹菜单；菜单第一项是复制，所以老习惯按下去还是那个结果。
                                        onLongPress = { copyMessage(message) },
                                        // 撤回：自己的、而且还在两分钟内才给这一项。超了就不出现——
                                        // 按下去只会拿到服务端的 false，再亮一句「没撤回成」纯属添乱。
                                        onDelete = if (
                                            message.sender == owner &&
                                            withinRecallWindow(message.createdAt)
                                        ) {
                                            { recallMessage(message) }
                                        } else {
                                            null
                                        },
                                        // 回复：谁的消息都能引，包括自己的。
                                        onReply = { replyTarget = message },
                                        // 转发：也是谁的都行——转发别人的话是最常见的用法。
                                        onForward = { openForward(message) },
                                        // 置顶消息：只有群里才有这一项（私聊没有"群里那条"可言）；
                                        // 已经在置顶里的那条，菜单写「取消置顶」。
                                        onPin = if (grouped) {
                                            { togglePinMessage(message) }
                                        } else {
                                            null
                                        },
                                        pinned = pinnedMessages.any { it.id == message.id },
                                        // 收藏：只在本机，只给文字消息（图片和卡片收进列表里也没法看，
                                        // 真正要留的是那句话）。已经在收藏里就说「取消收藏」。
                                        onSave = if (message.kind == "text" && onToggleSaved != null) {
                                            {
                                                val keep = savedMessages.none { it.id == message.id }
                                                // 用 `?.` 而不是靠智能转换：这个回调是外面递进来的可空参数，
                                                // lambda 里按非空用会踩到"捕获的可空值"。
                                                onToggleSaved?.invoke(message, keep)
                                                actionHint = if (keep) "已收藏这条消息" else "已取消收藏"
                                            }
                                        } else {
                                            null
                                        },
                                        saved = savedMessages.any { it.id == message.id },
                                        // 删除（只本机）：只对自己的。它和能不能撤回无关：
                                        // 老消息撤不了了，但自己仍然可以从这一侧把它拿掉。
                                        onHide = if (message.sender == owner) {
                                            { hideMessageInPlace(message) }
                                        } else {
                                            null
                                        },
                                        // 点开看大图：只有还活着的图片消息给这个回调——
                                        // 过期的那条正文里已经没有对象了，点开只会是一个加载失败的黑屏。
                                        onOpenImage = if (message.kind == "image") {
                                            { viewingImage = message.body }
                                        } else {
                                            null
                                        },
                                        // 卡片：跳哪儿由上层决定，这一层只把"点了哪张"递出去。
                                        onOpenShare = onOpenShare,
                                        onOpenCard = onOpenCard,
                                        // 翻译只给文字消息：图片和卡片的 body 不是给人读的句子。
                                        onTranslate = if (message.kind == "text") {
                                            { scope.launch { translateMessage(message) } }
                                        } else {
                                            null
                                        },
                                        translation = translations[message.id],
                                        // 回执只挂在自己最后一条下面：每条都挂会把整屏变成一堆小字。
                                        receipt = if (message.id == lastMine?.id) receiptText else null,
                                        // 长按对方的头像 = 直接 @ 他；私聊里没有可 @ 的人。
                                        onAvatarLongPress = if (grouped && message.sender != owner) {
                                            {
                                                mention(
                                                    ConversationMember(
                                                        owner = message.sender,
                                                        name = message.label,
                                                    ),
                                                )
                                                composerFocus.requestFocus()
                                                keyboardController?.show()
                                            }
                                        } else {
                                            null
                                        },
                                        // 有人在这条里点了我的名：名字后面挂「@你」，不用翻正文找。
                                        mentionsMe = message.sender != owner &&
                                            message.mentions.contains(owner),
                                    )
                                }
                            }
                        }
                    }
                }

                // 首屏落到底（settled）之前不画浮标：那会儿列表还没排完，"不在底"只是错觉。
                if (settled && messages.isNotEmpty() && !atBottom) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 数字为 0 只画箭头：那颗向下箭头是「不在最底」的唯一提示，以前它和胶囊绑在一起，
                        // 没新消息时整组都不画，人翻了半天历史却找不到回到底部的按钮。
                        if (unseenNew.isNotEmpty()) {
                            ChatNewMessagesPill(count = unseenNew.size, onClick = scrollToBottom)
                        }
                        FlBarButton(
                            glyph = FlGlyphs.chevronDown,
                            contentDescription = "滚到最新消息",
                            onClick = scrollToBottom,
                            size = 34.dp,
                            iconSize = 16.dp,
                        )
                    }
                }
            }
            // 快捷表情只在「默认态」出现：面板一拉开，这一排就挤在面板和输入栏中间了。
            // 背景完全透明：不铺底色、不套底板，就是五颗图浮在消息区和输入栏之间。
            if (!panelOpen && draft.isEmpty()) {
                ChatStickerStrip(onPick = sendSticker, background = Color.Transparent)
            }
            if (showMore) {
                // 四个钮里「图片」真能用，其余三个点一下给一句话，不当哑巴按钮。
                // 群里开关关着时，「图片」也走那句话那条路——按下去不会掉进一个被后端拒掉的流程。
                val pickImage: () -> Unit = if (imagesAllowed) {
                    sendImage
                } else {
                    { imageHint = "这个群关闭了发图片，等群主打开" }
                }
                ChatMorePanel(
                    onPickImage = pickImage,
                    onUnavailable = { imageHint = "只支持发图片" },
                )
            }
            if (showEmotions) {
                // 表情面板：贴在输入栏上方。点一颗只是把 token（如 [偷笑]）放进草稿，
                // 不会立刻发出去——要发就按右下角那颗「发送」，走的是同一个 send。
                EmotionPicker(
                    onPick = { emotion ->
                        rememberEmotionPick(emotion.name)
                        // 和别的插入走同一条路：前面有字又没空格时先补一个空格。
                        insert(emotion.token)
                    },
                    // × 删的是草稿里最后一整颗 token，不是半个 "[偷"。
                    onDelete = { draft = dropLastSticker(draft) },
                    onKeyboard = {
                        showEmotions = false
                        composerFocus.requestFocus()
                        keyboardController?.show()
                    },
                    onSend = send,
                    canSend = draft.isNotBlank() && !sending && setupGate.ready,
                    // 半透明底色：面板下面是消息，一整块实底看着像"又开了一页"。
                    // 深色主题多给一点不透明度，否则底下的字会透出来压住表情。
                    background = composerBarColor(MaterialTheme.colorScheme)
                        .copy(alpha = if (MaterialTheme.colorScheme.isDark) 0.86f else 0.72f),
                )
            }
            if (showMentions) {
                // @ 面板：只列别人，自己 @ 自己不会有任何人收到反应。和另外两个面板互斥。
                MentionPanel(
                    members = detail?.members.orEmpty().filter { it.owner != owner },
                    onPick = mention,
                    onDismiss = { showMentions = false },
                )
            }
            // 正在回复的那一条：贴在输入栏正上方，一边打字一边看得见在回谁。
            // 放在三个面板之后、输入栏之前：面板一拉开就占掉输入区，这条夹在中间会被挤走形。
            replyTarget?.let { target ->
                ReplyComposerBar(
                    name = target.label,
                    body = if (target.kind == "text") target.body else "图片",
                    onCancel = { replyTarget = null },
                )
            }
            ChatComposer(
                draft = draft,
                onDraftChange = { value ->
                    draft = value
                    // 每敲一下就报一次「我在打字」（内部 4 秒节流）：这就是对方顶栏那行字的唯一来源。
                    noteTyping()
                },
                sending = sending,
                onSend = send,
                onInsert = insert,
                showMore = showMore,
                onToggleMore = {
                    if (showMore) {
                        showMore = false
                    } else {
                        closePanels()
                        showMore = true
                        dismissKeyboard()
                    }
                },
                showEmotions = showEmotions,
                onToggleEmotions = {
                    if (showEmotions) {
                        showEmotions = false
                    } else {
                        closePanels()
                        showEmotions = true
                        dismissKeyboard()
                    }
                },
                // 私聊没有 @：只有群聊、而且确实有别人可点时，才给这个入口。
                onMention = if (grouped && detail?.members.orEmpty().any { it.owner != owner }) {
                    {
                        if (showMentions) {
                            showMentions = false
                        } else {
                            closePanels()
                            showMentions = true
                            dismissKeyboard()
                        }
                    }
                } else {
                    null
                },
                showMentions = showMentions,
                // 占位文案与「点输入框收面板」都看这两个：面板开着只说「发消息」，
                // 点进输入框（键盘起来）就把三个面板让位。
                panelOpen = panelOpen,
                focused = composerFocused,
                onFocusedChange = { nowFocused ->
                    composerFocused = nowFocused
                    if (nowFocused && panelOpen) closePanels()
                },
                fieldFocus = composerFocus,
                blocked = blocked,
                // 没设名字/头像：输入栏整条换成一行说明，和拉黑同一个形状。
                gateHint = setupGate.prompt,
            )
            // 图片这一路的提示与文字那条同款：传的时候一直亮，失败了才说话，成功了不说话。
            if (sendingImage) {
                Text(
                    text = "正在发送图片…",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            } else if (imageHint != null || actionHint != null) {
                Text(
                    // 两句话共用这一行：一句是图片/发送的结果，一句是置顶的结果，同时只会有一句。
                    text = imageHint ?: actionHint.orEmpty(),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            if (sendFailed) {
                // 失败时草稿、@ 名单、引用目标都还在（见 send 里那几条只在成功时清），
                // 所以「重发」就是再按一次发送，正文不用人重打。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "没发出去，检查代理后再试",
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "重发",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !sending) { send() }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            if (recallFailed) {
                Text(
                    text = "没撤回成，检查代理后再试",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            if (copiedId != null) {
                Text(
                    text = "已复制这条消息",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            if (hideFailed) {
                Text(
                    text = "没删成，检查代理后再试",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            if (forwardHint != null) {
                Text(
                    // 成败共用这一行：转发成功是个很短的动作，单独再弹一个对话框去说"成功了"
                    // 比这条消息本身还重。失败时这行是红的，成功时是灰的（见下面 color 的判断）。
                    text = forwardHint.orEmpty(),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = if (forwardHint == "已转发") {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
    }

    // 全屏看图。也放在根作用域：它是一个 Dialog，进 Column 只会被排成一行内容。
    // 用 `?.let` 而不是三元：viewingImage 是可变状态，读一次存进局部量，免得在分支里反复读。
    viewingImage?.let { path ->
        ChatImageViewer(
            repository = repository,
            objectPath = path,
            cache = decodedImages,
            onDismiss = { viewingImage = null },
        )
    }

    // 公告全文：横幅只放两三行，点开看完整那份。和群设置里编辑的是同一段文字。
    if (showAnnouncement) {
        Dialog(onDismissRequest = { showAnnouncement = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(18.dp),
            ) {
                Text(
                    text = "群公告",
                    style = FlText.headline,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = detail?.announcement.orEmpty().ifBlank { "这个群还没有公告。" },
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = "知道了",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 14.dp)
                        .clickable {
                            // 确认过就记下来：这一条公告以后不再自动挂在聊天上面（换新公告才再出现）。
                            showAnnouncement = false
                            announcementDismissed = true
                            onNoticeSeen(noticeFingerprint)
                        },
                )
            }
        }
    }

    // 转发去哪：列出所有会话让人点一个。对话框本身画在页面之外，所以它放在根作用域里，
    // 不进上面那个 Column——进去会被当成最后一行内容排版。
    forwarding?.let { message ->
        ForwardDialog(
            preview = when {
                message.kind == "image" -> "图片"
                message.removed -> "该消息已撤回"
                else -> message.body
            },
            targets = forwardTargets,
            hint = forwardHint,
            onPick = forwardTo,
            onDismiss = {
                forwarding = null
                // 收起对话框就把那句结果一起收掉：它说的是刚刚那次转发，离开这层就没意义了。
                forwardHint = null
            },
        )
    }
}

/**
 * 顶栏下面那一排入口里，此刻展开的是哪一块。
 *
 * [Chat] 不是"聊天页"，而是"什么都别展开"：入口行还开着的时候，点第一个格子就是把下面那块收掉、
 * 顺手回到最新一条消息。和抖音群聊那一下一致——顶上那排是开关，不是第二个页面。
 */
private enum class ChatHeaderSection { Chat, Announcement, Pinned, Saved }

/**
 * 顶栏：回去的箭头 + 圆头像 + 名字（群聊挂人数）+ 右边一颗折叠按钮。
 *
 * 右边以前是两个圆按钮（通话 / 更多），现在合成一颗：灰底圆里一个实心三角，收着时朝下、展开时朝上。
 * 点开在顶栏下面铺一排入口——聊天 / 公告 / 置顶消息 / 收藏 / 设置，照微信、抖音的群聊：
 * 群公告、置顶消息这类"整个群都该看见"的东西从顶栏进，而不是藏在右上角某个菜单的第三项里。
 *
 * 公告、置顶、收藏三格右上角各有一颗红点：有内容就亮，一眼看出这个群有没有新公告、有没有置顶。
 * 「设置」不展开任何东西，直接进群设置页——群名、群头像、公告、我的群昵称、进群码都在那一页。
 *
 * 左边的圆头像和标题也是一块入口：群聊点进群资料（[onOpenGroupInfo]），
 * 私聊点进对面那个人的主页（[onOpenProfile]，身份用 [peerOwner]）。私聊还没拿到对方身份时
 * 这一块不挂点击——空身份只会推开一张空主页。
 *
 * [pinned] / [muted] / [onTogglePin] / [onToggleMute] 暂时留着但不画：「置顶聊天」「免打扰」两个开关
 * 在群设置页里（`GroupSettingsScreen`），顶栏不再重复一遍。
 */
@Composable
private fun ChatTopBar(
    title: String,
    isGroup: Boolean,
    memberCount: Int,
    peerOwner: String,
    /** 群成员名单：已经不再参与画头像（见 [GroupAvatarView]），留着只是为了不改调用点。 */
    groupMembers: List<ConversationMember> = emptyList(),
    /** 会话号：群头像按它推图床地址（设过群头像就显示那张图）。 */
    conversationId: Long = 0L,
    /** 「X 正在输入…」；空串表示没人打字（这时群聊退回写「群聊」）。 */
    typingLine: String = "",
    onNavigateBack: () -> Unit,
    onOpenGroupInfo: () -> Unit,
    onOpenProfile: (String) -> Unit,
    pinned: Boolean = false,
    muted: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    onToggleMute: (() -> Unit)? = null,
    /** 群公告正文；空串 = 没设过。私聊恒为空，入口行那格点了就写「还没有公告」。 */
    announcement: String = "",
    /** 群置顶消息：有内容时「置顶消息」那格亮红点，展开那一块把它们列出来。 */
    pinnedMessages: List<ChatMessage> = emptyList(),
    /** 本机收藏的消息（和「置顶」分开：收藏只对自己可见）。 */
    savedMessages: List<ChatMessage> = emptyList(),
    /** 点「聊天」：把入口行收起来并回到最新一条。null 时只收起入口行。 */
    onJumpToLatest: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    // 入口行开没开：那颗三角按钮是它的唯一开关。
    var entriesOpen by remember { mutableStateOf(false) }
    // 入口行开着时，下面展开的是哪一块。默认 [ChatHeaderSection.Chat]＝什么都不展开。
    var section by remember { mutableStateOf(ChatHeaderSection.Chat) }
    // 标题就是群名，**不挂人数**：内测群人少，人数不该出现在顶栏（见 ChatScreen 里 members 的注释）。
    val heading = title
    // 私聊的入口需要一个具体的人：拿不到身份（空串）就返回 null，整块不响应。
    val openPeer: (() -> Unit)? = if (!isGroup && peerOwner.isNotEmpty()) {
        { onOpenProfile(peerOwner) }
    } else {
        null
    }
    // 抬头点一下去哪：群聊是群资料，私聊是这个人的主页；两边都没有就什么都不挂。
    val openHeader: (() -> Unit)? = if (isGroup) onOpenGroupInfo else openPeer
    // 头像与标题共用同一段点击：两块都能点，但只算一个动作。
    val headerClick = if (openHeader == null) {
        Modifier
    } else {
        Modifier.clickable(
            onClickLabel = if (isGroup) "打开群资料" else "看主页",
            onClick = openHeader,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChatHeaderBarHeight)
                .padding(horizontal = ChatHeaderSidePadding),
            verticalAlignment = Alignment.CenterVertically,
            // 四个元素之间只有一个间距值（原来每个 Spacer 各写一个数，宽窄不一）：
            // 想调就改 ChatHeaderGap，别在某一处单独加宽。
            horizontalArrangement = Arrangement.spacedBy(ChatHeaderGap),
        ) {
            FlBarButton(
                glyph = FlGlyphs.chevronBack,
                contentDescription = "返回",
                onClick = onNavigateBack,
                size = 40.dp,
                iconSize = 20.dp,
                filled = false,
            )
            ChatHeaderAvatar(
                title = title,
                isGroup = isGroup,
                peerOwner = peerOwner,
                groupMembers = groupMembers,
                conversationId = conversationId,
                size = ChatHeaderAvatarSize,
                onClick = openHeader,
            )
            Column(modifier = Modifier.weight(1f).then(headerClick)) {
                Text(
                    text = heading,
                    style = FlText.headline,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (typingLine.isNotEmpty() || isGroup) {
                    Text(
                        // 有人打字时这一行让给它：正在输入比"这是个群聊"有用得多，而且能立刻看见。
                        // 没人打字就退回原来那句「群聊」，不写人数（内测群人少，人数是主办方的事；
                        // memberCount 仍然收着，进资料页时用它决定画不画成员名单）。
                        text = typingLine.ifEmpty { "群聊" },
                        style = FlText.caption2,
                        color = if (typingLine.isNotEmpty()) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            ChatFoldButton(
                expanded = entriesOpen,
                dot = announcement.isNotBlank() ||
                    pinnedMessages.isNotEmpty() ||
                    savedMessages.isNotEmpty(),
                onClick = {
                    entriesOpen = !entriesOpen
                    // 收起来时连展开的那一块也一起收掉：下次点开是干净的一排入口。
                    if (!entriesOpen) section = ChatHeaderSection.Chat
                },
            )
        }
        AnimatedVisibility(visible = entriesOpen) {
            ChatHeaderEntries(
                section = section,
                hasAnnouncement = announcement.isNotBlank(),
                pinnedCount = pinnedMessages.size,
                savedCount = savedMessages.size,
                onPick = { picked ->
                    when (picked) {
                        // 「聊天」= 收起入口行并回到最新一条：顶上这排是开关，不是第二个页面。
                        ChatHeaderSection.Chat -> {
                            entriesOpen = false
                            section = ChatHeaderSection.Chat
                            onJumpToLatest?.invoke()
                        }
                        // 其余三格都在原位展开/收起：再点同一个格子就是收回去。
                        else -> section = if (section == picked) ChatHeaderSection.Chat else picked
                    }
                },
                // 「设置」不进展开区：收起这排，直接进群设置页（群名、群头像、公告、进群码都在那儿）。
                onOpenSettings = {
                    entriesOpen = false
                    section = ChatHeaderSection.Chat
                    onOpenGroupInfo()
                },
            )
        }
        // 展开区的正文：公告全文、置顶消息、收藏，三块共用同一个位置，选哪块显示哪块。
        // 只在入口行开着时画——入口行收起来，下面这一块跟着一起收，不留一条没来由的卡片。
        if (entriesOpen) {
            when (section) {
                ChatHeaderSection.Announcement -> ChatAnnouncementCard(text = announcement)
                ChatHeaderSection.Pinned -> ChatMessageListCard(
                    title = "置顶消息",
                    messages = pinnedMessages,
                    emptyText = "这个群还没有置顶消息：长按一条消息，菜单里有「置顶消息」。",
                )
                ChatHeaderSection.Saved -> ChatMessageListCard(
                    title = "我收藏的消息",
                    messages = savedMessages,
                    emptyText = "还没有收藏的消息：长按一条消息就能收进来。",
                )
                ChatHeaderSection.Chat -> Unit
            }
        }
        // 顶部这块底部那条 1px 浅灰线：**常驻**。入口行收起时它仍是顶部区域的下边界——
        // 不然消息列表会直接贴着顶栏，一条分界都没有。
        HorizontalDivider(
            thickness = ChatHeaderDividerThickness,
            color = scheme.outlineVariant,
        )
    }
}

/**
 * 顶栏右边那颗折叠按钮：灰色圆底 + 一个实心三角。
 *
 * 三角而不是折线 chevron：这个按钮只表示"这一排收起来 / 放下去"，实心三角一眼能看出朝哪边；
 * 展开时朝上（再点一下就是收起来），收起时朝下（点一下放出来）。红点表示收起状态里也有新东西
 * （新公告、置顶消息、收藏），不然收着的时候什么提示都没有。
 */
@Composable
private fun ChatFoldButton(
    expanded: Boolean,
    dot: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "fold-button-press",
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .minimumInteractiveComponentSize()
            .scale(pressScale)
            .clip(CircleShape)
            .background(scheme.surfaceVariant)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = if (expanded) "收起群功能" else "展开群功能",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            val triangle = Path()
            if (expanded) {
                // 展开时朝上：尖在中间偏上，底边贴着下面。
                triangle.moveTo(0f, size.height * 0.74f)
                triangle.lineTo(size.width, size.height * 0.74f)
                triangle.lineTo(size.width / 2f, size.height * 0.26f)
            } else {
                triangle.moveTo(0f, size.height * 0.26f)
                triangle.lineTo(size.width, size.height * 0.26f)
                triangle.lineTo(size.width / 2f, size.height * 0.74f)
            }
            triangle.close()
            drawPath(triangle, scheme.onSurfaceVariant)
        }
        if (dot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(scheme.error),
            )
        }
    }
}

/**
 * 顶栏下面那一排入口：聊天 / 公告 / 置顶消息 / 收藏 / 设置，五格均分，一格一个动作。
 *
 * 前四格是"就地展开/收起"：点一下展开下面那张卡，再点同一格收回去；「聊天」是收起整排并回到
 * 最新一条（它本来就是"我在看消息"这个状态）。第五格「设置」不展开任何东西，直接进群设置页。
 */
@Composable
private fun ChatHeaderEntries(
    section: ChatHeaderSection,
    hasAnnouncement: Boolean,
    pinnedCount: Int,
    savedCount: Int,
    onPick: (ChatHeaderSection) -> Unit,
    /** 第五格「设置」：直接进群设置页，不在顶栏里展开任何东西（用户 2026-11-27 定的）。 */
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChatTabBarHeight)
            // 左右内边距与标题栏同一个值：两排的左右基线才对得齐。
            .padding(horizontal = ChatHeaderSidePadding),
        verticalAlignment = Alignment.CenterVertically,
        // 五格一律 weight(1f) 均分；格子之间给一点点缝，但不靠给某一格加 margin 来挪位置。
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChatEntryCell(
            label = "聊天",
            selected = section == ChatHeaderSection.Chat,
            dot = false,
            modifier = Modifier.weight(1f),
            onClick = { onPick(ChatHeaderSection.Chat) },
        )
        ChatEntryCell(
            label = "公告",
            selected = section == ChatHeaderSection.Announcement,
            dot = hasAnnouncement,
            modifier = Modifier.weight(1f),
            onClick = { onPick(ChatHeaderSection.Announcement) },
        )
        ChatEntryCell(
            label = "置顶消息",
            selected = section == ChatHeaderSection.Pinned,
            dot = pinnedCount > 0,
            modifier = Modifier.weight(1f),
            onClick = { onPick(ChatHeaderSection.Pinned) },
        )
        ChatEntryCell(
            label = "收藏",
            selected = section == ChatHeaderSection.Saved,
            dot = savedCount > 0,
            modifier = Modifier.weight(1f),
            onClick = { onPick(ChatHeaderSection.Saved) },
        )
        ChatEntryCell(
            label = "设置",
            selected = false,
            dot = false,
            modifier = Modifier.weight(1f),
            onClick = onOpenSettings,
        )
    }
}

/**
 * 入口行里的一格：整格可点（高度撑满这一行），文字居中，选中时文字底下一块圆角浅灰。
 *
 * 选中底色只包住文字（左右各 6dp），不铺满整格：五格的位置仍然由 weight 均分决定，
 * 底色宽窄不会把别的格子挤动。
 */
@Composable
private fun ChatEntryCell(
    label: String,
    selected: Boolean,
    dot: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) scheme.surfaceVariant else Color.Transparent)
                // 左右各 6dp：五格均分后每格约 62dp（360dp 宽的机器），"置顶消息"四个字 12sp 刚好放得下；
                // 再宽就会在窄屏上被省略号截掉。
                .padding(horizontal = 6.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                // 选中深色、未选中灰色：五格里哪一格开着，一眼看得出来。
                color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (dot) {
                Box(
                    modifier = Modifier
                        // 红点贴在文字右上角：绝对定位在文字这一格里，不占布局，别的格子不会被推走。
                        .align(Alignment.TopEnd)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(scheme.error),
                )
            }
        }
    }
}

/**
 * 聊天区上方的群公告横幅：一个喇叭 + 两行字 + 右边一颗关闭。
 *
 * 微信把群公告放在这个位置，因为它是"整个群"的信息，不该只在设置里藏一份。点一下看全文，
 * 右边的叉只关这一趟——下次进这个群还该看见。
 */
@Composable
private fun ChatAnnouncementBanner(
    text: String,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.secondaryContainer.copy(alpha = 0.55f))
            .clickable(onClickLabel = "看群公告全文", onClick = onOpen)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        FlGlyphIcon(glyph = FlGlyphs.info, size = 16.dp, tint = scheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        FlBarButton(
            glyph = FlGlyphs.close,
            contentDescription = "关闭公告",
            onClick = onClose,
            size = 26.dp,
            iconSize = 12.dp,
            filled = false,
        )
    }
}

/**
 * 公告卡片：群公告全文，三行以后自己收成「展开全文」。
 *
 * **这里只读，不给「编辑」**（2026-11-27 用户定的）：写公告这件事统一在群设置页，点群头像或者
 * 顶栏那排的「设置」进去就有；聊天页顶上再多一个编辑入口，等于同一个动作两处可做、两处各改一半。
 *
 * 没有公告的时候也说一句，而不是画一个空白框：这一格点了要有回应，不然人以为按钮坏了。
 */
@Composable
private fun ChatAnnouncementCard(text: String) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember(text) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "群公告",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
        )
        Text(
            text = text.ifBlank { "这个群还没有公告。" },
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = if (text.isBlank()) scheme.onSurfaceVariant else scheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        // 短公告不给「展开全文」：点了没变化比没这个入口更让人困惑。
        if (text.length > 60) {
            Text(
                text = if (expanded) "收起" else "展开全文",
                fontSize = 12.sp,
                color = scheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(
                        onClickLabel = if (expanded) "收起公告" else "展开公告全文",
                        onClick = { expanded = !expanded },
                    ),
            )
        }
    }
}


/**
 * 置顶消息 / 收藏共用的一张卡片：标题 + 每一行「谁说的：摘要」，最多五条。
 *
 * 两种东西一个样子，是因为它们要回答的是同一个问题——"这个群里有几条被挑出来的话"；
 * 收藏多一句"只对自己可见"的区分写在标题里，不另做一种排版。
 */
@Composable
private fun ChatMessageListCard(
    title: String,
    messages: List<ChatMessage>,
    emptyText: String,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
        )
        if (messages.isEmpty()) {
            Text(
                text = emptyText,
                fontSize = 13.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            return@Column
        }
        messages.take(5).forEach { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.label,
                    fontSize = 12.sp,
                    color = scheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        message.removed -> "该消息已撤回"
                        message.kind == "image" -> "[图片]"
                        message.body.isBlank() -> "[消息]"
                        else -> message.body
                    },
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 顶栏左边那个 40 的圆头像：设过头像就是照片，没设过就是首字（群给「群」、私聊给「聊」）——
 * 两态由 [PersonAvatar] 决定，这一层不再自己画字。
 *
 * [peerOwner] 只在私聊里有：有了身份号才能推出图床地址（见 [personAvatarUrl]）。群聊没有"一个人"
 * 可以对应，所以画**成员九宫格**（[groupMembers] 不够两个人时退回群名首字）。
 *
 * [onClick] 是「点头像进资料/主页」这一个动作；为 null 的时候就只是一个装饰圆，不抢触摸。
 */
@Composable
private fun ChatHeaderAvatar(
    title: String,
    isGroup: Boolean,
    peerOwner: String = "",
    /** 群成员名单：已经不再参与画头像（见 [GroupAvatarView]），留着只是为了不改调用点。 */
    groupMembers: List<ConversationMember> = emptyList(),
    /** 会话号：群头像按它推图床地址——设过群头像就显示那张图，没设过是群名首字。 */
    conversationId: Long = 0L,
    /**
     * 圆的直径。顶栏那一处用 [ChatHeaderAvatarSize]（比正文里的行内头像大一圈），
     * 其它地方不传就是原来那个 40。
     */
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val clickModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            onClickLabel = if (isGroup) "打开群资料" else "看主页",
            onClick = onClick,
        )
    }
    // 群聊：设过群头像就是那张图，没设过就是群名首字方块（见 [GroupAvatarView]）。
    if (isGroup) {
        GroupAvatarView(
            name = title.ifBlank { chatInitial(title = title, isGroup = true) },
            conversationId = conversationId,
            members = groupMembers,
            size = size,
            modifier = clickModifier,
        )
        return
    }
    // 私聊：对面那个人自己的头像，和消息页那一行是同一张脸。
    PersonAvatar(
        name = title.ifBlank { chatInitial(title = title, isGroup = false) },
        owner = peerOwner,
        size = size,
        modifier = clickModifier,
        background = scheme.primary.copy(alpha = 0.15f),
        letterColor = scheme.primary,
    )
}

/**
 * 一条消息左侧的小圆头像：别人的话才画，32 的圆里放 sender 的首字。
 *
 * 头像自己是一块独立的可点区域（[onClick]）：气泡整块挂的是长按复制，点头像看人不能挂在气泡上，
 * 否则两个手势会互相抢。[onClick] 为 null 时就只是一个装饰圆。
 */
@Composable
private fun ChatRowAvatar(
    label: String,
    /**
     * 这个人的头像直链；空串就只有首字母圆。
     *
     * 头像在"个人主页"里换：图床对象名固定成 `<owner>.jpg`（覆盖式），所以换完之后同一个 URL
     * 就是新图——这一层不再画那个"字"，否则改了头像聊天页还是老的。
     */
    url: String = "",
    onClick: (() -> Unit)? = null,
    /**
     * 长按头像：在输入框里 @ 这个人。
     *
     * 群里 @ 人最顺的手是「在他说的话上点住他的头像」，而不是先去左边点 @、再在一列名字里找。
     * 放长按是因为点击已经被「看主页」占了；两个手势同一块区域，长按不会误触。
     */
    onLongClick: (() -> Unit)? = null,
    /** 首字圆的底色；null 就跟着主题主色走（[UserAvatar] 的默认）。 */
    background: Color? = null,
    /** 首字那个字的颜色；底色是实心主色时用白色。 */
    letterColor: Color = Color.White,
) {
    val circle = Modifier
        .size(32.dp)
        .clip(CircleShape)
    val shape = if (onClick == null && onLongClick == null) {
        circle
    } else {
        circle.combinedClickable(
            onClickLabel = "看主页",
            onLongClickLabel = "在输入框里 @ 他",
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
    }
    Box(modifier = shape, contentAlignment = Alignment.Center) {
        // 图没到、或者根本没有头像时，这层自己画首字母圆，所以加载过程不会出现一个空洞。
        UserAvatar(name = label, url = url, size = 32.dp, background = background, letterColor = letterColor)
    }
}

/** 换了一天的那条居中灰字：`2026-11-21 08:40`，不换算时区也不猜"今天/昨天"。 */
@Composable
private fun ChatDayDivider(stamp: String) {
    Text(
        text = stamp,
        style = FlText.caption2,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
}

/**
 * 一条消息：自己的靠右、主色气泡、主色上的字；别人的靠左、灰气泡，左边一张小圆头像。
 *
 * 群聊里别人的气泡上面署一个名字（[ChatMessage.label]，后端没给名字就退回编号）。
 * 长按别人的气泡把整条正文复制到剪贴板（[onLongPress]）；自己的气泡长按弹一个小菜单
 * （[DropdownMenu]，写法照 MessagesScreen 的 ConversationRow）：「复制」走 [onLongPress]，
 * 「撤回」只在给了 [onDelete] 时出现。正文里的表情 token 由 [EmotionText] 画成图。
 *
 * `kind == "image"` 的那一条不走文字那一套：不套气泡、不喂 [EmotionText]，正文（图片地址）
 * 交给 [ChatImageBody] 去取字节画成一张图。图片消息自己也带时间，位置挪到图下面一行小字。
 * 图片的菜单只有「撤回」：正文是地址不是话，复制过去没有意义（见 [ChatBubbleMenu] 的 canCopy）。
 *
 * 头像上的点击是**另一块区域**（[onAvatarClick]）：头像在 [Row] 里是气泡的兄弟节点，气泡自己
 * 挂的是长按，两处手势不重叠，点头像不会顺手触发菜单，长按气泡也不会跳走。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    repository: SocialRepository,
    /** 解码好的图，气泡与全屏查看器共用一份；见 [ChatScreen] 里的 `decodedImages`。 */
    imageCache: MutableMap<String, ImageBitmap>,
    message: ChatMessage,
    mine: Boolean,
    /** 自己这一侧那张头像上画什么字：没有头像照片时用它兜底（见 [ChatRowAvatar]）。 */
    myLabel: String = "我",
    /** 我自己那张头像的直链；空串就是首字母圆。由调用点按 owner 拼好递进来。 */
    myAvatarUrl: String = "",
    /**
     * 右侧这张头像的**配色**（底色 + 首字颜色），由调用点按同一套主题变量递进来。
     *
     * 没设过头像的人占多数，这时这一行的观感就完全取决于这两格颜色。自己这一侧和左侧别人的气泡
     * 各用各的：左边是主题主色的淡底 + 主色字（与消息列表那 44dp 的头像同一套），右边是主色实底
     * + 白字——两边看起来像"两个人"而不是两个灰圆。
     */
    myAvatarBackground: Color? = null,
    myAvatarLetterColor: Color = Color.White,
    showSender: Boolean,
    onAvatarClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    /** 撤回这条的回调；只有自己的消息、且还在时限内才给，菜单里才出现「撤回」。 */
    onDelete: (() -> Unit)? = null,
    /** 引用这一条；别人的消息也能引用，所以它和 [onDelete] 不是同一批。 */
    onReply: (() -> Unit)? = null,
    /** 转发这一条到别的会话。 */
    onForward: (() -> Unit)? = null,
    /** 钉住 / 取消钉住这一条；只有群聊会给（私聊没有"群里那条消息"）。 */
    onPin: (() -> Unit)? = null,
    /** 这一条此刻在不在置顶里：菜单那一项按它说「置顶消息」还是「取消置顶」。 */
    pinned: Boolean = false,
    /** 收藏 / 取消收藏这一条（只在本机）；null 表示不给这个入口（图片、卡片）。 */
    onSave: (() -> Unit)? = null,
    /** 这一条已经在收藏里：菜单那一项写「取消收藏」。 */
    saved: Boolean = false,
    /** 只在自己这一侧删掉（对所有人还在）；只有自己的消息才给。 */
    onHide: (() -> Unit)? = null,
    /**
     * 点图片放大看。只有图片消息才会给这个回调，别的那几种类型没有可放大的东西。
     *
     * 它和长按是两回事：长按仍然是菜单（复制/回复/转发/撤回），点击才是看大图。
     */
    onOpenImage: (() -> Unit)? = null,
    /** 点分享卡片（App / 方案）；没给就说明这一页不跳转，卡片照旧画但不响应点击。 */
    onOpenShare: ((ShareCard) -> Unit)? = null,
    /** 点名片：进那个人的主页。 */
    onOpenCard: ((PersonCard) -> Unit)? = null,
    /** 长按菜单里的「翻译」；null 表示这一条没什么可翻的（图片、卡片、系统消息）。 */
    onTranslate: (() -> Unit)? = null,
    /** 这一条翻出来的译文；null 表示没翻过，气泡下面就不多一行。 */
    translation: String? = null,
    /** 气泡下面那行小字（自己最后一条的「已读」）；null 表示不画。 */
    receipt: String? = null,
    /** 长按对方头像：在输入框里 @ 他。null 表示这一页不给（私聊、或者自己那一侧）。 */
    onAvatarLongPress: (() -> Unit)? = null,
    /**
     * 点**自己**那张头像：去个人主页（换头像、改名都在那儿）。
     *
     * 以前自己这一侧的头像不挂点击——"看自己的主页"确实没人需要；但它也是页面上唯一一处
     * 「这是我」的地方，想换头像的人第一反应就是点它，所以现在点它直接进那一页。
     */
    onMyAvatarClick: (() -> Unit)? = null,
    /** 这条消息的 @ 名单里有我：名字后面挂一个「@你」。 */
    mentionsMe: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val menuable = onLongPress != null || onReply != null || onForward != null ||
        onDelete != null || onHide != null || onTranslate != null || onPin != null || onSave != null
    var menuOpen by remember { mutableStateOf(false) }
    val longPress = if (menuable) ({ menuOpen = true }) else null
    // 图片不是文字：气泡的底色和 12 的内边距会在图四周多出一圈主色，那一路换成一张裸图。
    val isImage = message.kind == "image"
    // 卡片（分享 / 名片）：和图片一样不带气泡底色，但里面的内容是可点的。
    // 先取到局部变量，省得下面每处都写 `message.share`——那样 Kotlin 不一定能智能转换。
    val shareCard = message.share
    val personCard = message.card
    val isCard = shareCard != null || personCard != null
    // 只有「点输入框上面那排快捷表情直接发出去」的那一条才算贴纸（本地 kind 标记，后端不认也不校验）。
    // 从表情面板选进草稿再发的，仍旧是普通消息：一颗表情老老实实待在气泡里，和以前一样。
    val soloSticker = if (message.kind == StickerKind) {
        remember(message.body) {
            parseEmotionBody(message.body).singleOrNull() as? EmotionSpan.Sticker
        }
    } else {
        null
    }
    // 卡片点了去哪儿，由上层决定；这一层只负责把"点了哪张卡"传出去。
    val cardClick: (() -> Unit)? = when {
        shareCard != null && onOpenShare != null -> ({ onOpenShare.invoke(shareCard) })
        personCard != null && onOpenCard != null -> ({ onOpenCard.invoke(personCard) })
        else -> null
    }
    // 长按的手套：文字和图片两条路都要挂一次，包成值免得写两遍修饰符链。
    val holdModifier = if (longPress == null) {
        Modifier
    } else {
        Modifier.combinedClickable(
            onClickLabel = "这条消息",
            onLongClickLabel = "打开这条消息的菜单",
            onLongClick = longPress,
            onClick = {},
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        // 别人的消息**一律留一块头像位**（用户要求，2026-11-26）：以前这里挂的是 `showSender`，
        // 也就是「是群聊，而且不是我发的」两个条件都成立才画。私聊不画、拿不到 sender 不画、
        // 群身份没判出来也不画——一行消息左边空一块，看着就是"头像丢了"，还找不到原因。
        //
        // 现在不判群：只要不是我发的，左边就固定摆一张 32 的圆——有照片就是照片，没设过就是
        // 名字首字（[ChatRowAvatar] → [UserAvatar] 的两态）。位置永远在，不会因为某个字段空了
        // 而整块消失。
        if (!mine) {
            ChatRowAvatar(
                label = message.label,
                url = personAvatarUrl(message.sender),
                onClick = onAvatarClick,
                // 长按头像 = 在这个人的话上直接 @ 他。只有群聊会传进来（私聊 @ 没有意义）。
                onLongClick = onAvatarLongPress,
            )
            // 这一格固定 8dp：头像有没有下下来都不变，消息正文的左边线保持对齐。
            Spacer(Modifier.width(8.dp))
        }
        // 头像只挂在自己这一侧：截图里对方那条是干净的（没有头像也没有昵称），
        // 自己的每条消息右边都跟着一张圆头像。点自己的头像不跳主页。
        Column(
            modifier = Modifier.widthIn(max = ChatBubbleMaxWidth),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            if (showSender) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 名字走 AuthorLabel：还没改名的群成员显示成占位条 + 红点，而不是 `#10001`。
                    AuthorLabel(
                        name = message.label,
                        style = TextStyle(fontSize = 9.sp),
                        color = colors.outline,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp, bottom = 5.dp),
                    )
                    // 别人在群里点了我：名字后面挂一个「@你」。比整条高亮更省事，也不会让人
                    // 把"被点名"和"刚好有人说了一句话"看混。
                    if (mentionsMe) {
                        Text(
                            text = " @你",
                            fontSize = 9.sp,
                            color = colors.primary,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }
            if (isImage) {
                // 图片自己带圆角，所以外面不再套那个有底色、有内边距的气泡。
                Box(
                    modifier = Modifier
                        .clip(ChatImageShape)
                        // 点击看大图，长按仍是菜单：两个手势分开挂，谁也不吃谁。
                        // 没给 [onOpenImage]（比如过期的图）时就不挂点击，免得点下去什么反应都没有。
                        .then(
                            if (onOpenImage != null) {
                                Modifier.combinedClickable(
                                    onClickLabel = "查看大图",
                                    onLongClickLabel = "打开图片菜单",
                                    onClick = onOpenImage,
                                    onLongClick = longPress,
                                )
                            } else {
                                Modifier
                            },
                        )
                        // 给了点击那一路就不必再挂 holdModifier：它会和上面的 combinedClickable 抢事件。
                        .then(if (onOpenImage == null) holdModifier else Modifier),
                ) {
                    ChatImageBody(
                        repository = repository,
                        objectPath = message.body,
                        cache = imageCache,
                    )
                    if (menuable) {
                        // 图片的菜单不画「复制」（canCopy = false）：正文是图片地址不是话。
                        ChatBubbleMenu(
                            modifier = Modifier.align(Alignment.TopEnd),
                            open = menuOpen,
                            canCopy = false,
                            canRetract = onDelete != null,
                            canReply = onReply != null,
                            canForward = onForward != null,
                            canPin = onPin != null,
                            canHide = onHide != null,
                            onDismiss = { menuOpen = false },
                            onCopy = { onLongPress?.invoke() },
                            onDelete = onDelete,
                            onReply = onReply,
                            onForward = onForward,
                            pinned = pinned,
                            onPin = onPin,
                            canSave = onSave != null,
                            saved = saved,
                            onSave = onSave,
                            onHide = onHide,
                        )
                    }
                }
            } else if (isCard) {
                // 卡片不套气泡底色：它自己就是一张卡，外面再垫一层主色会显得脏。
                // 点击与长按分开挂，理由和图片那段一样——叠两层修饰符会抢事件。
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (cardClick != null) {
                                Modifier.combinedClickable(
                                    onClickLabel = "打开这张卡片",
                                    onLongClickLabel = "打开这条消息的菜单",
                                    onClick = cardClick,
                                    onLongClick = longPress,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .then(if (cardClick == null) holdModifier else Modifier),
                ) {
                    when {
                        shareCard != null -> ChatShareCard(share = shareCard, mine = mine)
                        personCard != null -> ChatPersonCard(card = personCard, mine = mine)
                        // 解析不出卡片（后端换了格式、或者库被人手改过）：说一句，不画个空框。
                        else -> Text(
                            text = "这条卡片打不开",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    if (menuable) {
                        ChatBubbleMenu(
                            modifier = Modifier.align(Alignment.TopEnd),
                            open = menuOpen,
                            // 卡片的正文是 JSON 或者一个号码，复制过去没有意义。
                            canCopy = false,
                            canRetract = onDelete != null,
                            canReply = onReply != null,
                            canForward = onForward != null,
                            canPin = onPin != null,
                            canHide = onHide != null,
                            onDismiss = { menuOpen = false },
                            onCopy = { onLongPress?.invoke() },
                            onDelete = onDelete,
                            onReply = onReply,
                            onForward = onForward,
                            pinned = pinned,
                            onPin = onPin,
                            canSave = onSave != null,
                            saved = saved,
                            onSave = onSave,
                            onHide = onHide,
                        )
                    }
                }
            } else if (soloSticker != null) {
                // 贴纸那一路：没有底色、没有内边距，长按照样能撤回/复制/引用。
                Image(
                    painter = painterResource(soloSticker.emotion.resource),
                    contentDescription = soloSticker.emotion.name,
                    modifier = Modifier
                        .size(ChatStickerSize)
                        .then(holdModifier),
                )
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (mine) colors.primary else colors.surfaceVariant)
                        .then(holdModifier)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Column {
                        // 引用块画在正文上方：左边一条竖线加两行小字，像把被引的那句话
                        // 折起来压在下面。有引用才画，没引用时这一列和以前一模一样。
                        if (message.replyTo != null) {
                            ChatQuoteBlock(
                                senderName = message.replySenderName,
                                body = message.replyBody,
                                mine = mine,
                            )
                            Spacer(Modifier.height(5.dp))
                        }
                        // 只放大**单颗**表情：17dp → 34dp，读得出是哪个，又不至于比一句话还高。
                        // 连发好几颗时保持正文大小（三颗巨图能吃掉半屏）；混着文字的更不放大。
                        val stickers = chatStickerCount(message.body)
                        EmotionText(
                            body = message.body,
                            fontSize = 14,
                            lineHeight = if (stickers == 1) 40 else 19,
                            color = if (mine) colors.onPrimary else colors.onSurfaceVariant,
                            // 自己的气泡底色是主色，@ 再上主色就看不见了：用 onPrimary 反过来提亮。
                            mentionColor = if (mine) colors.onPrimary else colors.primary,
                            stickerScale = if (stickers == 1) 2f else 1f,
                        )
                    }
                    if (menuable) {
                        // 菜单锚在气泡上：Box 包着的 DropdownMenu 贴着气泡右上角弹出来。
                        ChatBubbleMenu(
                            modifier = Modifier.align(Alignment.TopEnd),
                            open = menuOpen,
                            canCopy = true,
                            canRetract = onDelete != null,
                            canReply = onReply != null,
                            canForward = onForward != null,
                            canPin = onPin != null,
                            canHide = onHide != null,
                            canTranslate = onTranslate != null,
                            onDismiss = { menuOpen = false },
                            onCopy = { onLongPress?.invoke() },
                            onDelete = onDelete,
                            onReply = onReply,
                            onForward = onForward,
                            pinned = pinned,
                            onPin = onPin,
                            canSave = onSave != null,
                            saved = saved,
                            onSave = onSave,
                            onHide = onHide,
                            onTranslate = onTranslate,
                        )
                    }
                }
            }
            // 译文和回执都挂在气泡外面、自己这一侧那一列里：气泡本身是一条消息的"皮"，
            // 后加上去的这两行字是解释，不该跟着气泡底色一起被当成消息正文。
            translation?.let { line ->
                Text(
                    text = line,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            receipt?.let { line ->
                Text(
                    text = line,
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        // 自己那一侧的头像：群聊里别人的名字靠 showSender 那行小字说清，私聊本来只有两个人，
        // 所以这一张只在「我」这边。点它进个人主页——换头像的入口就在那儿。
        if (mine) {
            Spacer(Modifier.width(8.dp))
            ChatRowAvatar(
                label = myLabel,
                url = myAvatarUrl,
                onClick = onMyAvatarClick,
                background = myAvatarBackground,
                letterColor = myAvatarLetterColor,
            )
        }
    }
}

/**
 * 分享卡片：左边一个图标方块，右边标题加一行备注，最下面一行小字说这是应用还是方案。
 *
 * 底色不跟气泡走主色：卡片是一块内容，不是"我说的一句话"。自己的分享给一层很淡的主色底，
 * 只是为了让右边一眼看出是谁发的，不至于把里面的字都逼着反过来排。
 *
 * 宽度卡在 [ChatCardMaxWidth]：一个长应用名不该把整条消息撑到屏幕边上，那会让人找不到哪儿是头。
 */
@Composable
private fun ChatShareCard(share: ShareCard, mine: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .widthIn(max = ChatCardMaxWidth)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (mine) colors.primary.copy(alpha = 0.10f) else colors.surfaceContainerHigh,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(
                // 应用用九宫格、方案用清单：两种卡片标题旁边的小字已经写了类型，
                // 图标只是让它们在聊天列表里扫过去时能分开。
                glyph = if (share.kind == "app") FlGlyphs.iconTile else FlGlyphs.appsList,
                size = 20.dp,
                tint = colors.primary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = share.title.ifBlank { share.target },
                fontSize = 13.sp,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (share.note.isNotBlank()) {
                Text(
                    text = share.note,
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (share.kind == "app") "应用" else "方案",
                fontSize = 9.sp,
                color = colors.outline,
                maxLines = 1,
            )
        }
    }
}

/**
 * 个人名片：一个圆头像加名字，底下一行「个人名片」。
 *
 * 名字是发出那一刻的名字（后端写进 body 的），所以别人后来改了名，这张卡还印着旧字——
 * 和真名片一样，也和有意的：聊天记录是当时的样子。
 */
@Composable
private fun ChatPersonCard(card: PersonCard, mine: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .widthIn(max = ChatCardMaxWidth)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (mine) colors.primary.copy(alpha = 0.10f) else colors.surfaceContainerHigh,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(
            name = card.label,
            owner = card.owner,
            size = 40.dp,
            background = colors.primary.copy(alpha = 0.14f),
            letterColor = colors.primary,
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = card.label,
                fontSize = 13.sp,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "个人名片",
                fontSize = 9.sp,
                color = colors.outline,
                maxLines = 1,
            )
        }
    }
}

/**
 * 气泡里的引用块：左边一条竖线，右边「某某：原话前几句」。
 *
 * 只给两行：它回答的是「我在回哪句」，完整的原话在那条消息自己身上，往上翻就有。所以宁可
 * 截断，也不让一条引用把一个短回复撑成一屏。
 *
 * 颜色跟着气泡底色走：自己的气泡是主色，竖线和字都得换成 onPrimary 的淡色，
 * 否则一道主色竖线画在主色底上等于没画。
 *
 * 摘要是空的 = 被引的那条已经撤回：后端每次读的时候现取，撤回后就空了。这里必须说一句，
 * 否则引用块只剩「某某：」后面一片空白，看着像界面出了问题。
 */
@Composable
private fun ChatQuoteBlock(senderName: String, body: String, mine: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val lineColor = if (mine) {
        scheme.onPrimary.copy(alpha = 0.5f)
    } else {
        scheme.primary.copy(alpha = 0.5f)
    }
    val textColor = if (mine) {
        scheme.onPrimary.copy(alpha = 0.85f)
    } else {
        scheme.onSurfaceVariant
    }
    Row(modifier = Modifier.widthIn(max = 230.dp)) {
        Box(
            modifier = Modifier
                .width(2.dp)
                // 竖线跟着两行字的自然高度走；只有一行字时也不至于缩成一个点。
                .heightIn(min = 24.dp)
                .background(lineColor),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (body.isBlank()) {
                "该消息已撤回"
            } else {
                "${senderName.ifBlank { "某人" }}：$body"
            },
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 气泡右上角那个长按菜单：复制 / 回复 / 转发 / 删除 / 撤回。
 *
 * 每一项都是先收起菜单再动手：菜单留着会把下面的气泡挡住，动作却已经发出去了，看着像没点上。
 * 图片消息传 [canCopy] = false —— 它的正文是图片地址，不是人写的字。
 *
 * 「删除」和「撤回」的差别是这五个里唯一需要想一下的：删除只让自己这一侧看不见（别人那里
 * 原样还在），撤回是对所有人把正文拿掉。所以删除排在撤回前面，破坏力小的先出现。
 */
@Composable
private fun ChatBubbleMenu(
    modifier: Modifier = Modifier,
    open: Boolean,
    canCopy: Boolean,
    canRetract: Boolean = false,
    canReply: Boolean = false,
    canForward: Boolean = false,
    /** 群聊才有「置顶消息」这一项。 */
    canPin: Boolean = false,
    canHide: Boolean = false,
    /** 有没有「翻译」这一项：只有文字消息给。 */
    canTranslate: Boolean = false,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    /** 这一条已经在置顶里：那一项写「取消置顶」，不然每次点都像第一次钉。 */
    pinned: Boolean = false,
    /** 有没有「收藏」这一项：只在本机，图片/卡片不给。 */
    canSave: Boolean = false,
    /** 这一条已经在收藏里：那一项写「取消收藏」。 */
    saved: Boolean = false,
    onHide: (() -> Unit)? = null,
    onTranslate: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
) {
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        if (canCopy) {
            DropdownMenuItem(
                text = { Text("复制", fontSize = 14.sp) },
                onClick = {
                    onDismiss()
                    onCopy()
                },
            )
        }
        if (canTranslate && onTranslate != null) {
            DropdownMenuItem(
                text = { Text("翻译", fontSize = 14.sp) },
                onClick = {
                    onDismiss()
                    onTranslate()
                },
            )
        }
        if (canReply && onReply != null) {
            DropdownMenuItem(
                text = { Text("回复", fontSize = 14.sp) },
                onClick = {
                    onDismiss()
                    onReply()
                },
            )
        }
        if (canForward && onForward != null) {
            DropdownMenuItem(
                text = { Text("转发", fontSize = 14.sp) },
                onClick = {
                    onDismiss()
                    onForward()
                },
            )
        }
        // 置顶消息：群聊里挑一条钉到顶上。和"置顶聊天"不是一件事——那个在群设置里。
        if (canPin && onPin != null) {
            DropdownMenuItem(
                text = { Text(if (pinned) "取消置顶" else "置顶消息", fontSize = 14.sp) },
                onClick = {
                    onDismiss()
                    onPin()
                },
            )
        }
        // 收藏：只在这台设备上留着，别人看不见；放在置顶后面——它比"全群都看见"轻一档。
        if (canSave && onSave != null) {
            DropdownMenuItem(
                text = { Text(if (saved) "取消收藏" else "收藏", fontSize = 14.sp) },
                onClick = {
                    onDismiss()
                    onSave()
                },
            )
        }
        // 「删除」和「撤回」都只对自己的消息出现，但它们是两件事：删除只影响自己这一侧，
        // 撤回对所有人。放在同一张菜单里靠措辞分开，顺序是破坏力从小到大。
        if (canHide && onHide != null) {
            DropdownMenuItem(
                text = { Text("删除", fontSize = 14.sp) },
                onClick = {
                    onDismiss()
                    onHide()
                },
            )
        }
        if (canRetract && onDelete != null) {
            DropdownMenuItem(
                text = { Text("撤回", fontSize = 14.sp) },
                onClick = {
                    onDismiss()
                    onDelete()
                },
            )
        }
    }
}

/**
 * 图片消息的正文：先一个灰框，字节到了换成图。
 *
 * 字节按 [objectPath] 现取（[SocialRepository.downloadChatImage]，失败就是 null），解码用
 * compose resources 的 `ByteArray.decodeToImageBitmap()`——仓库里读远程图片的现成先例是
 * `ui/component/DeviceHero.kt`，那一处也没有额外的 OptIn。
 *
 * 三态：还没到 → 160×120 的灰框；成功 → 按 [ChatImageMaxWidth] / [ChatImageMaxHeight] 收边的图；
 * 取不到或解不开 → 同一个灰框上写「图片加载失败」。占位先占住位置，图到了列表也不会跳。
 */
@Composable
private fun ChatImageBody(
    repository: SocialRepository,
    objectPath: String,
    /**
     * 这张图解码好之后往哪儿放一份；进来时也先到这里找。
     *
     * 让全屏查看器能"点开立刻看到"：气泡刚解码完的那张就是查看器要的那张，
     * 为了放大再下一次既慢又费流量。滚出列表再滚回来也省一次。
     */
    cache: MutableMap<String, ImageBitmap>,
) {
    var bitmap by remember(objectPath) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(objectPath) { mutableStateOf(false) }

    // 键是路径：同一个 slot 换了图就重新取。滚出去再滚回来会再取一次，但不会留着上一张。
    LaunchedEffect(objectPath) {
        // 缓存里已经有就直接用，不再问一次网络。
        val cached = cache[objectPath]
        if (cached != null) {
            bitmap = cached
            failed = false
            return@LaunchedEffect
        }
        bitmap = null
        failed = false
        if (objectPath.isBlank()) {
            failed = true
            return@LaunchedEffect
        }
        val bytes = repository.downloadChatImage(objectPath)
        val decoded = bytes?.let { raw -> runCatching { raw.decodeToImageBitmap() }.getOrNull() }
        if (decoded == null) {
            failed = true
        } else {
            bitmap = decoded
            cache[objectPath] = decoded
        }
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = "图片",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = ChatImageMaxWidth)
                .heightIn(max = ChatImageMaxHeight)
                .clip(ChatImageShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(width = ChatImagePlaceholderWidth, height = ChatImagePlaceholderHeight)
                .clip(ChatImageShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (failed) {
                Text(
                    text = "图片加载失败",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 全屏看一张图。
 *
 * 黑底铺满整块屏幕（`usePlatformDefaultWidth = false` 才不会被 Dialog 默认的窄边裁住），
 * 图按 [ContentScale.Fit] 居中，怎么缩放都不裁掉边缘。
 *
 * 手势分两层挂，因为它们得同时成立：
 *  * 一层只管点击/双击（`detectTapGestures`）—— 点任意处或右上角 × 关掉；双击在 1f 与
 *    [ChatImageDoubleTapZoom] 之间来回。
 *  * 另一层管缩放与拖动（`detectTransformGestures`）。`zoom` 是相对上一次的增量，所以乘上去
 *    而不是直接赋值，捏合才顺。
 *
 * 缩回 1f（或更小被夹回 1f）时把位移清零：图小于画面时还留着位移，会歪在一边像没居中。
 *
 * [cache] 和气泡共用：气泡刚解好的那张直接拿来画，点开是立刻的，不再下一次网络。
 */
@Composable
private fun ChatImageViewer(
    repository: SocialRepository,
    objectPath: String,
    cache: MutableMap<String, ImageBitmap>,
    onDismiss: () -> Unit,
) {
    var bitmap by remember(objectPath) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(objectPath) { mutableStateOf(false) }
    var scale by remember(objectPath) { mutableStateOf(1f) }
    var offset by remember(objectPath) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(objectPath) {
        val cached = cache[objectPath]
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        val bytes = repository.downloadChatImage(objectPath)
        val decoded = bytes?.let { raw -> runCatching { raw.decodeToImageBitmap() }.getOrNull() }
        if (decoded == null) {
            failed = true
        } else {
            bitmap = decoded
            cache[objectPath] = decoded
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        // 只关掉 dialog 自己的默认宽度，让它铺满整屏。
        //
        // 不写 `decorFitsSystemWindows`：那是 Android 版 DialogProperties 才有的参数，
        // Compose Multiplatform 的这份没有，写了必编译不过（CI 上就是这么红的）。
        // 全屏要盖住状态栏，靠的是黑底 fillMaxSize 本身，而不是这个开关。
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = ChatImageDoubleTapZoom
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, ChatImageMaxZoom)
                        scale = next
                        offset = if (next <= 1f) Offset.Zero else offset + pan
                    }
                },
        ) {
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded,
                    contentDescription = "图片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            } else {
                Text(
                    text = if (failed) "图片加载失败" else "加载中…",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // 右上角的 ×：点图本身也能关，但放一个明确的出口，免得有人不敢点。
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                FlGlyphIcon(glyph = FlGlyphs.close, size = 18.dp, tint = Color.White)
            }
        }
    }
}

/** 右下角那个「⌃ 新消息(N)」胶囊：不在最底、并且真有新消息的时候才出现，点了滚到最新一条。 */
@Composable
private fun ChatNewMessagesPill(count: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = "⌃ 新消息(${count})",
        style = FlText.caption2,
        color = scheme.onPrimary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(scheme.primary)
            .clickable(onClickLabel = "滚到最新消息", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * 输入框上面那排「点一下就发」的表情：一排小图，点了立刻发一条，不进输入框。
 *
 * **整条透明**：外面那个 `Column` 已经有 `surface` 底色，这里再铺一层（哪怕只是带 alpha 的
 * `composerBarColor`）就会看着像一条浅色横带。现在这一层不画任何底色，也不画底板，
 * 只留五颗图 + 点击区域；[background] 留着给以后真要铺底的地方显式传。
 */
@Composable
private fun ChatStickerStrip(
    onPick: (Emotion) -> Unit,
    background: Color = Color.Transparent,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (background == Color.Transparent) Modifier else Modifier.background(background)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(ChatQuickStickers) { key ->
            val emotion = emotionOf(key) ?: return@items
            // 表情本身就是头像大小的图，别再套一个灰圆底：底下那份底色让一整排看起来像一排按钮，
            // 而且表情的边缘（透明的部分）会被那块灰补成圆的，图案显得又小又糊。直接画图。
            Image(
                painter = painterResource(emotion.resource),
                contentDescription = emotion.name,
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClickLabel = emotion.name) { onPick(emotion) },
            )
        }
    }
}

/**
 * 「更多」面板：四个圆形按钮，只有第一个真的能用。
 *
 * 「图片」走 [onPickImage]（选图 → 上传 → 登记，见 `ChatScreen` 里的 sendImage）；视频、位置、
 * 文件这三样后端还没有落点，所以它们只借 [onUnavailable] 说一句「只支持发图片」——
 * 点了什么都不发生比说一句更让人以为按钮坏了。
 */
@Composable
private fun ChatMorePanel(
    onPickImage: () -> Unit,
    onUnavailable: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ChatMoreCell(glyph = FlGlyphs.iconTile, label = "图片", onClick = onPickImage)
        ChatMoreCell(glyph = FlGlyphs.eye, label = "视频", onClick = onUnavailable)
        ChatMoreCell(glyph = FlGlyphs.pin, label = "位置", onClick = onUnavailable)
        ChatMoreCell(glyph = FlGlyphs.importFile, label = "文件", onClick = onUnavailable)
    }
}

/** 更多面板里的一格：一个灰圆 + 底下一行字。整格都能点（圆子太小，手指对不准）。 */
@Composable
private fun ChatMoreCell(glyph: FlGlyph, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(scheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(glyph = glyph, size = 18.dp, tint = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 输入栏上方那条「回复 某某：…」。
 *
 * 用一条窄条而不是把输入框改成两行：人正在打字，不该因为引用了别人就让输入框跳高一块。
 * 正文只给一行、超出打省略号——那行是个提示，不是让人在输入栏里重读一遍原话的地方。
 */
@Composable
private fun ReplyComposerBar(name: String, body: String, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerHigh)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左边那道竖线：和气泡里的引用块用同一个记号，让人一眼对上"我引的就是那种块"。
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(26.dp)
                .background(scheme.primary.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "回复 $name",
                fontSize = 10.sp,
                color = scheme.primary,
                maxLines = 1,
            )
            Text(
                text = body,
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlBarButton(
            glyph = FlGlyphs.close,
            contentDescription = "取消回复",
            onClick = onCancel,
            size = 30.dp,
            iconSize = 14.dp,
            filled = false,
        )
    }
}

/**
 * 转发去哪：一列会话，点一个就转过去。
 *
 * 不做搜索：会话通常就那么几条，翻一眼比打字快；真到几十条再加。
 * 结果提示显示在正文下面而不是另弹一个 toast——对话框还开着的时候，人正需要知道刚才那一下
 * 成没成，才好决定是再点一个还是关掉。
 */
@Composable
private fun ForwardDialog(
    preview: String,
    targets: List<Conversation>,
    hint: String?,
    onPick: (Conversation) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "转发到",
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        Text(
            text = preview,
            fontSize = 11.sp,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = hint,
                fontSize = 10.sp,
                color = if (hint == "已转发") scheme.primary else scheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (targets.isEmpty()) {
            Text(
                text = "没有别的会话可以转",
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(targets, key = { it.id }) { target ->
                    val label = target.title.ifBlank { target.peerName }.ifBlank { "会话" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(target) }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PersonAvatar(
                            name = label,
                            // 私聊取对面那个人的照片；群聊没有 peer，落回群名首字。
                            owner = target.peer,
                            size = 32.dp,
                            background = scheme.primary.copy(alpha = 0.12f),
                            letterColor = scheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 输入栏上那个 @。
 *
 * 图标集里没有这个字形，而 `profiles` 已经被「语音」那个钮占着——两个一样的人像摆在边上会分不清，
 * 所以这里直接写字。四十年前的键盘上就有它，不用教。
 */
@Suppress("unused") // 输入栏不再显示 @ 键；留着这一颗，用户要恢复时一行就能放回去。
@Composable
private fun MentionKey(active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) scheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "@",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}

/**
 * 群里能被点到的人。只列别人：@ 自己不会有任何人有反应，后端也会把它从名单里筛掉。
 *
 * 横向一条而不是下拉菜单：群成员通常不超过十个，横向一眼扫完比展开一层菜单快，
 * 而且它和表情面板挤在同一块位置，上下两个面板长得一样才不会让人找。
 */
@Composable
private fun MentionPanel(
    members: List<ConversationMember>,
    onPick: (ConversationMember) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow)
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "提醒谁看",
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "收起",
                fontSize = 11.sp,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (members.isEmpty()) {
            Text(
                text = "群里还没有别人",
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(members, key = { it.owner }) { member ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(member) }
                            .padding(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PersonAvatar(
                            name = member.label,
                            owner = member.owner,
                            size = 44.dp,
                            background = scheme.primary.copy(alpha = 0.12f),
                            letterColor = scheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = member.label,
                            fontSize = 10.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.widthIn(max = 56.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 底部那一行：四宫格（展开更多）+ 可选的 @ + 会长的输入框 + 语音/发送 + 表情 + 加号。
 *
 * 右边第一个圆按钮是**会变的那一个**：草稿非空时它变成粉色「发送」，空着时才是语音。
 * 发送是这个页面唯一的出口，输入栏和表情面板都走它（[ChatSendPill]），不能让它藏在别处。
 *
 * 输入框的占位三态：面板开着只说「发消息」，点进去（键盘起来）才说「发消息或按住说话...」。
 * 输入框本身从 40dp 起，最多长到 120dp（约五行）之后自己滚，不再把消息区挤没。
 *
 * 表情钮拉开输入栏上方的表情面板（再点一次收起），加号和左边的四宫格一样拉开更多面板。
 * 输入栏左边不再有 @ 键（用户要求去掉）；群成员面板 [MentionPanel] 与插入逻辑原样保留。
 */
@Composable
private fun ChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
    onInsert: (String) -> Unit,
    showMore: Boolean,
    onToggleMore: () -> Unit,
    showEmotions: Boolean = false,
    onToggleEmotions: () -> Unit = {},
    /** @ 面板的开关；null 表示这个会话不该有 @（私聊，或者群里没有别人）。 */
    onMention: (() -> Unit)? = null,
    showMentions: Boolean = false,
    /** 三个面板里有没有开着的：面板开着时占位文案回落到「发消息」。 */
    panelOpen: Boolean = false,
    /** 输入框现在有没有焦点。 */
    focused: Boolean = false,
    /** 焦点变化回给调用方：面板开着时被聚焦要顺手把面板收掉。 */
    onFocusedChange: (Boolean) -> Unit = {},
    /** 输入框的焦点请求器：面板要回键盘时从这里把焦点还回去。 */
    fieldFocus: FocusRequester = remember { FocusRequester() },
    /** 对面这个人被本机拉黑了：输入栏整条换成一行灰字，见 [BlockedComposerBar]。 */
    blocked: Boolean = false,
    /**
     * 名字/头像没设过时那句话（见 [IdentitySetup]）；空串 = 闸门开着。
     *
     * 和 [blocked] 一样整条收起来：留一个能点却发不出去的框，比直接收起来更像坏了。
     */
    gateHint: String = "",
) {
    if (blocked) {
        BlockedComposerBar()
        return
    }
    if (gateHint.isNotEmpty()) {
        GateComposerBar(hint = gateHint)
        return
    }
    val colors = MaterialTheme.colorScheme

    // 输入框自己持有一份 TextFieldValue：**光标位置是这个状态的一部分**。
    //
    // 用 `BasicTextField(value: String)` 那版的话，从外面插一段字（长按头像 @ 人、点表情、草稿回填）
    // 只能改文本，改不了光标——插完光标还停在原处，接着打字就插进句子中间，看起来像"光标不知道跟着走"。
    var field by remember { mutableStateOf(TextFieldValue(draft)) }
    // 外部改了草稿：文本真的变了才跟，并把光标放到**末尾**（插入的东西永远加在最后）。
    // 用户自己敲键走的是 onValueChange，那时 field 与 draft 已经同步，这里不会把它拉回末尾。
    LaunchedEffect(draft) {
        if (field.text != draft) {
            field = TextFieldValue(draft, selection = TextRange(draft.length))
        }
    }
    // 语音转文字：系统自带识别服务，认出来的字直接落进草稿（见 util/VoiceInput）。
    val voice = rememberVoiceInput()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(composerBarColor(colors)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlBarButton(
                glyph = FlGlyphs.grid2x2,
                contentDescription = if (showMore) "收起更多" else "展开更多",
                onClick = onToggleMore,
                size = 48.dp,
                iconSize = 26.dp,
                filled = false,
            )
            // 输入栏左边的 @ 不再显示（用户要求）：MentionPanel 与 onMention 都还在，
            // 以后要恢复入口就在这里把 MentionKey 放回来。
            Spacer(Modifier.width(4.dp))
            BasicTextField(
                // TextFieldValue 版：文本和光标一起交出，从外面插字之后光标才跟得上（见 field 的注释）。
                value = field,
                onValueChange = { next ->
                    field = next
                    onDraftChange(next.text)
                },
                modifier = Modifier
                    .weight(1f)
                    // 40dp 起、120dp 封顶：先长到五行，再往上翻，消息区始终留着。
                    .heightIn(min = 40.dp, max = 120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(composerFieldColor(colors))
                    .focusRequester(fieldFocus)
                    .onFocusChanged { onFocusedChange(it.isFocused) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                textStyle = TextStyle(fontSize = 14.sp, color = colors.onSurface),
                // 光标用发送键那个粉：同一个动作的两种表示。
                cursorBrush = SolidColor(ChatSendPink),
                maxLines = 5,
                decorationBox = { inner ->
                    if (field.text.isEmpty()) {
                        Text(
                            // 面板开着：只说「发消息」；点进去（键盘在）：加上「按住说话」。
                            text = when {
                                panelOpen -> "发消息"
                                focused -> "发消息或按住说话..."
                                else -> "发消息"
                            },
                            fontSize = 14.sp,
                            color = composerPlaceholderColor(colors),
                            // 一行放不下就截断：折成两行会把输入框从 40dp 撑高，一行栏变两行。
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(6.dp))
            if (draft.isNotBlank() && !voice.listening) {
                ChatSendPill(text = "发送", enabled = !sending, onClick = onSend)
            } else {
                // 语音：点一下交给系统的语音识别，识别中这颗变高亮，再点一下就是停。
                FlBarButton(
                    glyph = FlGlyphs.voiceWave,
                    contentDescription = if (voice.listening) "停止语音输入" else "语音输入",
                    onClick = {
                        if (voice.listening) {
                            voice.stop()
                        } else {
                            voice.start { onInsert(it) }
                        }
                    },
                    size = 44.dp,
                    iconSize = 24.dp,
                    selected = voice.listening,
                    backgroundOverride = colors.surfaceContainerHigh,
                )
            }
            Spacer(Modifier.width(4.dp))
            FlBarButton(
                glyph = FlGlyphs.smile,
                contentDescription = if (showEmotions) "收起表情" else "表情",
                onClick = onToggleEmotions,
                size = 44.dp,
                iconSize = 24.dp,
                backgroundOverride = colors.surfaceContainerHigh,
            )
            Spacer(Modifier.width(4.dp))
            FlBarButton(
                glyph = FlGlyphs.addCircle,
                contentDescription = "更多",
                onClick = onToggleMore,
                size = 44.dp,
                iconSize = 24.dp,
                backgroundOverride = colors.surfaceContainerHigh,
            )
        }
        // 「正在听…」「没听清」「需要麦克风权限」都落在这行小字里：说话的时候屏幕上总得有点反应。
        voice.message?.let { hint ->
            Text(
                text = hint,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = if (voice.listening) colors.primary else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )
        }
    }
}

/**
 * 拉黑之后的输入区：一行灰字，没有输入框。
 *
 * 拉黑是本机的（后端没有这个接口），历史消息照旧能看；这里只说清"你不会再回这个人"，并把发送
 * 这条路整个收起来。留一个能点却发不出去的框，比直接收起来更像坏了。
 */
@Composable
private fun BlockedComposerBar() {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = "已拉黑，不再接收这个人的消息",
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = scheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(composerBarColor(scheme))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * 名字/头像还没设过时的输入区：一行说明，没有输入框。
 *
 * 和 [BlockedComposerBar] 同一个形状，用系统报错色：这不是"对方不想理你"，是"你还差一步"，
 * 所以这句话要说清去哪一步（见 [io.github.samolego.canta.util.IdentitySetup] 的 `prompt`）。
 */
@Composable
private fun GateComposerBar(hint: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = hint,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = scheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .background(composerBarColor(scheme))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** 输入栏和表情面板共用的发送键：粉底白字，禁用时淡下去。 */
@Composable
private fun ChatSendPill(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = if (enabled) 1f else 0.75f),
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (enabled) ChatSendPink else ChatSendPink.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClickLabel = text, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** 一行说明「没拿到消息」的灰字，摆在列表顶上。 */
@Composable
private fun ChatErrorLine(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

/** 顶栏头像上的那一个字：标题空了按群/私聊给一个字，别留一个空圆。 */
private fun chatInitial(title: String, isGroup: Boolean): String {
    val first = title.trim().take(1)
    if (first.isNotEmpty()) return first
    return if (isGroup) "群" else "聊"
}

/** 消息落在哪一天（**北京时区**的日期键，`yyyy-MM-dd`）：分隔条要不要画的判据。 */
private fun chatDayOf(createdAt: String): String = BeijingTime.dayKey(createdAt)

/**
 * 日期分隔条上那一行字：「今天」「昨天」「11月21日」。
 *
 * 换算与"今/昨"的判断都在 [BeijingTime]（后端给的是 UTC）。以前这里直接切字符串，日期按 UTC 算，
 * 北京时间 0 点到 8 点之间发的消息会被归到前一天。
 */
private fun chatDayStampOf(createdAt: String): String = BeijingTime.dayLabel(createdAt)

/** 气泡上那一格时间：`HH:mm`（北京），不是这个形状就返回空串，不画一个猜出来的时间。 */
private fun chatClockOf(createdAt: String): String = BeijingTime.clock(createdAt)

/**
 * 把后端刚回来的那一条并进列表：后到的同 id 覆盖先前的，最后按 id 排回正序。
 *
 * 与轮询里的合并同一套判据（`(fresh + messages).distinctBy { id }.sortedBy { id }`）：
 * 自己发的这一条可能已经随下一次拉取进了列表，重复画一个 id 的气泡会让 LazyColumn 的 key 撞车。
 */
private fun chatAppend(current: List<ChatMessage>, incoming: ChatMessage): List<ChatMessage> =
    (listOf(incoming) + current).distinctBy { message -> message.id }.sortedBy { message -> message.id }

/** 拉不到消息的两种原因分开说：没连上是网络的事，被拒绝是后端的事。 */
private fun chatErrorOf(reason: String?, network: Boolean): String? = when {
    network -> "没连上后端，检查代理或网络后再试。"
    !reason.isNullOrBlank() -> "后端没有返回消息：$reason"
    else -> null
}

/**
 * 服务端允许撤回多久。这条线在 `message_delete` 里，客户端改不动也不该去改。
 */
private const val RECALL_WINDOW_SECONDS = 120L

/**
 * 客户端这一侧给的宽限，略大于 [RECALL_WINDOW_SECONDS]。
 *
 * 手机时钟和服务器差几秒是常态，卡死在 120 会让「刚发出去的消息突然撤不了」。所以这一侧
 * 只做粗判：明显超时的（超过这条线）就不给撤回菜单项；落在 120~130 秒之间的仍然给，
 * 真超了由服务端拒绝，那时显示的才是「没撤回成」。
 */
private const val RECALL_CLIENT_GRACE_SECONDS = RECALL_WINDOW_SECONDS + 10L

/**
 * 这条消息还来不来得及撤回。
 *
 * 解不出时间就当成「太旧」：宁可少给一个菜单项，也不要给一个按下去必然失败的。
 */
private fun withinRecallWindow(createdAt: String): Boolean {
    val epoch = epochSecondsOf(createdAt) ?: return false
    return currentTimeMillis() / 1000 - epoch <= RECALL_CLIENT_GRACE_SECONDS
}

/**
 * 把 `YYYY-MM-DDTHH:MM:SS.ffffffZ` 解成 epoch 秒；形状不对返回 null。
 *
 * 自己算而不是上日期库：整个工程只用到这一处，为几行算术拉一个依赖不划算。
 * 后端用 `to_char(..., 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')` 拼的，形状是固定的，按下标取就够。
 * 时区按 UTC——那个 `Z` 就是 UTC，所以这里不做任何偏移。
 */
private fun epochSecondsOf(stamp: String): Long? {
    if (stamp.length < 19) return null
    val year = stamp.substring(0, 4).toIntOrNull() ?: return null
    val month = stamp.substring(5, 7).toIntOrNull() ?: return null
    val day = stamp.substring(8, 10).toIntOrNull() ?: return null
    val hour = stamp.substring(11, 13).toIntOrNull() ?: return null
    val minute = stamp.substring(14, 16).toIntOrNull() ?: return null
    val second = stamp.substring(17, 19).toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..60) {
        return null
    }
    var days = 0L
    for (y in 1970 until year) {
        days += if (isLeapYear(y)) 366L else 365L
    }
    days += daysBeforeMonth(year, month)
    days += (day - 1)
    return days * 86_400L + hour * 3_600L + minute * 60L + second
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

/** [month] 之前这个月已经过了多少天（1 月是 0）。 */
private fun daysBeforeMonth(year: Int, month: Int): Long {
    val lengths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var sum = 0L
    for (index in 0 until month - 1) {
        sum += lengths[index]
        // 二月在闰年多一天，补在它自己那一格上。
        if (index == 1 && isLeapYear(year)) sum += 1
    }
    return sum
}


