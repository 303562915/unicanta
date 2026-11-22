package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.ChatMessage
import io.github.samolego.canta.data.community.ConversationDetail
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.ui.component.EmotionPicker
import io.github.samolego.canta.ui.component.EmotionText
import io.github.samolego.canta.ui.component.rememberEmotionPick
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBarHeight
import io.github.samolego.canta.util.currentTimeMillis
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap

/** 一个气泡最宽多少：一行字到这儿换行，免得一条长消息把整块屏幕占满。 */
private val ChatBubbleMaxWidth = 260.dp

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

/** 会话开着时多久问一次新消息：没有推送，短了浪费额度，长了就"不准时"。 */
private const val ChatPollMs: Long = 8_000L

/**
 * 输入框上面那排快捷短语：左边是画出来的样子，右边是点下去真正填进输入框的字。
 *
 * 「@ 群聊AI」带上一个尾巴空格，点完接着打字就是 @ 了一个人，不用再自己补一个空格。
 */
private val ChatQuickPhrases = listOf(
    ChatQuickPhrase(label = "🌟 晚上好", insert = "晚上好"),
    ChatQuickPhrase(label = "🥰 比心", insert = "比心"),
    ChatQuickPhrase(label = "@ 群聊AI", insert = "@群聊AI "),
    ChatQuickPhrase(label = "👍 赞", insert = "赞"),
    ChatQuickPhrase(label = "😂 捂脸", insert = "捂脸"),
)

/** 一条快捷短语：显示的文字与填进输入框的文字分开，因为 emoji 不该跟着发出去。 */
private data class ChatQuickPhrase(val label: String, val insert: String)

/**
 * 私聊 / 群聊一页。
 *
 * 样子照着抖音私信做，但只做能落到这一层数据上的东西：
 *
 *  * 顶栏：回去的箭头、首字头像、名字（群聊挂人数），右边两个圆形按钮（通话 / 更多）。
 *    头像和名字是一块入口：群聊进群资料，私聊进对面那个人的主页（[onOpenProfile]）。
 *  * 消息区：别人的消息左边一张小圆头像，自己的靠右；换一天的头上压一条居中的灰字日期。
 *    点头像看那个人的主页，别人的长按气泡复制正文，自己的长按弹「复制 / 撤回」小菜单，
 *    撤回过的消息画成一行居中灰字，三者与头像点击是各自独立的触摸区域。
 *    图片消息（`kind == "image"`）画成一张图，自己的靠右、别人的带头像，过期的画一行灰字。
 *  * 滚动：不在最底的时候右下角有一个「新消息(N)」胶囊和一个向下箭头，点了滚到最新一条；
 *    滚到最顶时自动往回翻一页历史（[SocialRepository.messagesBefore]）。
 *  * 底部：一排快捷短语胶囊 + 输入栏（九宫格 / 输入框 / 语音或发送 / 表情 / 加号），
 *    表情拉开输入栏上方的表情面板（[EmotionPicker]），九宫格和加号拉开「更多」面板；
 *    面板里只有「图片」能用（[pickImage] 选图 → 上传 → 登记），视频/位置/文件点一下只给一句
 *    「只支持发图片」；两个面板互斥，拉开一个收起另一个。
 *
 * 后端只提供读一页（[SocialRepository.messagesFor]、[SocialRepository.messagesBefore]）、写一条
 * （[SocialRepository.sendMessage]、[SocialRepository.sendImageMessage]）、传一张图
 * （[SocialRepository.uploadChatImage]、[SocialRepository.downloadChatImage]）、撤回一条
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
) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendFailed by remember { mutableStateOf(false) }
    var loadingEarlier by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    /** 表情面板开没开：和 [showMore] 互斥，拉开一个就把另一个收回去。 */
    var showEmotions by remember { mutableStateOf(false) }
    /** 撤回失败亮一句：样式与位置照 [sendFailed] 那行。 */
    var recallFailed by remember { mutableStateOf(false) }
    /** 正在选图或传图：挡住重复点「图片」，同时底部那一行亮「正在发送图片…」。 */
    var sendingImage by remember { mutableStateOf(false) }
    /**
     * 更多面板那四个钮的一句话反馈：图片没传成/没发出去、别的钮点了不该动，
     * 都借输入框下面这一行说，2 秒后自己灭掉。
     */
    var imageHint by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<ConversationDetail?>(null) }
    /** 刚复制的是哪一条：不是 null 的时候底部亮一句「已复制」，过一会儿自己灭掉。 */
    var copiedId by remember { mutableStateOf<Long?>(null) }
    // 跨平台剪贴板：与 TweaksScreen 里复制命令用的是同一个（LocalClipboardManager.setText）。
    val clipboard = LocalClipboardManager.current
    /** 上一次滚到底时最后一条的 id：只有尾巴变了才往下滚，往上翻历史不该把视图拽到底。 */
    var tailId by remember { mutableStateOf<Long?>(null) }
    /** 首屏已经落到底了没有：没落过底之前不往上翻页，否则一进页面就白拉一页历史。 */
    var settled by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 一个会话一页：进页面读最近 30 条，读完把已读位置推到现在，顺便问一次会话详情。
    // 详情排在消息之后，是为了不让它的失败覆盖掉下面读的 lastError 文案。
    LaunchedEffect(conversationId, owner) {
        loading = true
        loadError = null
        detail = null
        tailId = null
        settled = false
        messages = repository.messagesFor(owner, conversationId, offset = 0, limit = ChatPageSize)
        loadError = chatErrorOf(repository.lastError, repository.lastFailureWasNetwork)
        loading = false
        repository.markRead(owner, conversationId)
        detail = repository.conversationDetail(owner, conversationId)
    }

    // 会话开着时自己问新消息：没有推送通道，不轮询的话对方说什么都要等退出再进才看得见。
    // 只并进尾巴，不动已经翻出来的历史。fresh 放在前面：同一 id 以后回来的为准，别的设备
    // 撤回的消息才能在本机也变灰；最后按 id 排回正序。有实质变化才走一次已读。
    LaunchedEffect(conversationId, owner) {
        if (conversationId <= 0L) return@LaunchedEffect
        while (true) {
            delay(ChatPollMs)
            val fresh = repository.messagesFor(owner, conversationId, offset = 0, limit = ChatPageSize)
            if (fresh.isNotEmpty()) {
                val merged = (fresh + messages).distinctBy { it.id }.sortedBy { it.id }
                if (merged != messages) {
                    messages = merged
                    repository.markRead(owner, conversationId)
                }
            }
        }
    }

    // 新消息（自己发的也算）落到最下面才看得见；列表是正序的，所以滚到最后一行。
    // 键是最后一条的 id：往上翻历史时尾巴没变，这个 effect 不会重跑，视图停在原处。
    LaunchedEffect(messages.lastOrNull()?.id) {
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        if (last.id != tailId) {
            tailId = last.id
            listState.scrollToItem(messages.size - 1)
        }
        // 首屏（或新消息）已经落到底：从现在起，滚到顶才算"要翻历史"。
        settled = true
    }

    /** 滚到最新一条：胶囊和向下箭头共用这一个动作。 */
    val scrollToBottom: () -> Unit = {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    // 视图停在最顶那一行时往回翻一页。loadingEarlier 挡住重入，空结果就静默认了。
    // 注意 keys 里不能放 loadingEarlier：这个 effect 一置位就把自己取消掉，翻页永远发不出去。
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    LaunchedEffect(atTop, conversationId, settled) {
        if (!settled || !atTop || loadingEarlier) return@LaunchedEffect
        val oldest = messages.firstOrNull() ?: return@LaunchedEffect
        loadingEarlier = true
        try {
            val older = repository.messagesBefore(owner, conversationId, oldest.id, ChatPageSize)
            if (older.isNotEmpty()) {
                messages = (older + messages).distinctBy { message -> message.id }
                // 新来的那一页接在前面：把原来第一条挪回视野顶上，手感上像"没动过"。
                listState.scrollToItem(older.size)
            }
        } finally {
            // 被取消（下拉到底、换会话）也要把闸门放回 false，否则再也翻不了页。
            loadingEarlier = false
        }
    }

    // 滚到底了没有：最后一条可见项就是最后一条消息，就算在底。
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= messages.size - 1
        }
    }
    // 还有几条没滚到：估一个数就够，胶囊上的数字不是账目。
    val unseen by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            (messages.size - 1 - last).coerceAtLeast(0)
        }
    }

    /** 发一条：写成功才清空输入框，写失败把输入留着，别让人重打一遍。 */
    val send: () -> Unit = {
        val body = draft.trim()
        if (body.isNotEmpty() && !sending) {
            scope.launch {
                sending = true
                sendFailed = false
                val sent = repository.sendMessage(owner, conversationId, body)
                if (sent != null) {
                    messages = messages + sent
                    draft = ""
                } else {
                    sendFailed = true
                }
                sending = false
            }
        }
    }

    /**
     * 发一张图：选 → 传 → 登记，三步都走完才把气泡画出来。
     *
     * 选图返回 null 就静默退出：用户取消不是错误，桌面/网页本来也没有相册（[pickImage] 的默认实现
     * 就返回 null），这时候弹一句红字只会让人以为自己做错了什么。上传成功但登记失败时，对象已经
     * 躺在 bucket 里了，这一层没有删对象的接口，所以只提示，不假装撤回过。
     */
    val sendImage: () -> Unit = {
        if (!sendingImage) {
            scope.launch {
                sendingImage = true
                imageHint = null
                try {
                    val bytes = pickImage()
                    if (bytes != null) {
                        // 路径按 owner 分目录：bucket 是大家共用的，时间戳加随机数让同一张图重发也不撞名。
                        val path = "$owner/${currentTimeMillis()}-${Random.nextInt(1_000_000)}.jpg"
                        if (!repository.uploadChatImage(owner, path, bytes, "image/jpeg")) {
                            imageHint = "图片没上传成，检查代理后再试"
                        } else {
                            val sent = repository.sendImageMessage(owner, conversationId, path)
                            if (sent == null) {
                                imageHint = "图片没发出去"
                            } else {
                                messages = chatAppend(messages, sent)
                                // 发出去就把面板收起来：让人直接看见那张图落到了最下面。
                                showMore = false
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
     * 复制一条消息：整条正文进剪贴板，底部亮一句。别人的长按直接进来，自己的从长按菜单的「复制」进来。
     *
     * 图片消息的 [ChatMessage.body] 是 storage 路径，不是人写的字：复制过去也没法用，所以给空串。
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

    // 观感上的群聊：形参说不是、但详情说是，就按群聊画（同一个判据见 ConversationDetail.isGroup）。
    val grouped = isGroup || detail?.isGroup == true
    // 标题：形参空的时候用详情里的群名，都空就叫「私聊」；人数优先用调用方给的，没有数成员。
    val heading = title.ifBlank { detail?.title.orEmpty() }.ifBlank { "私聊" }
    val members = if (memberCount > 0) memberCount else detail?.members?.size ?: 0
    // 私聊里"另一个人"的身份：详情成员表里第一个不是自己的那个。取不到就是空串，
    // 顶栏据此不挂点击——宁可点不动，也不要推开一张没有内容的主页。
    val peerOwner = detail?.members?.firstOrNull { member -> member.owner != owner }?.owner.orEmpty()

    Scaffold(
        topBar = {
            ChatTopBar(
                title = heading,
                isGroup = grouped,
                memberCount = members,
                peerOwner = peerOwner,
                onNavigateBack = onNavigateBack,
                onOpenGroupInfo = onOpenGroupInfo,
                onOpenProfile = onOpenProfile,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            loadError?.let { reason -> ChatErrorLine(reason) }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
                    ) {
                        // 日期分隔画在每条消息自己的 item 里，不额外占一个索引：
                        // 这样 layoutInfo 里的索引仍然与 messages 一一对应，下面的"在不在底"才算得准。
                        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                            val previous = if (index > 0) messages[index - 1] else null
                            val newDay = previous == null ||
                                chatDayOf(previous.createdAt) != chatDayOf(message.createdAt)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (newDay) {
                                    val stamp = chatDayStampOf(message.createdAt)
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
                                        message = message,
                                        mine = message.sender == owner,
                                        // 群聊里别人的气泡要说清是谁：一对一的对方不用说，页面标题就是名字。
                                        showSender = grouped && message.sender != owner,
                                        // 点头像看那个人的主页：自己那条不画头像，这个回调也就不给。
                                        // 群里谁发言谁有自己的一张头像，身份就是 message.sender。
                                        onAvatarClick = if (message.sender != owner && message.sender.isNotEmpty()) {
                                            { onOpenProfile(message.sender) }
                                        } else {
                                            null
                                        },
                                        // 长按：别人的直接复制；自己的弹「复制 / 撤回」小菜单（见 [ChatBubble]）。
                                        onLongPress = { copyMessage(message) },
                                        onDelete = if (message.sender == owner) {
                                            { recallMessage(message) }
                                        } else {
                                            null
                                        },
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
                        ChatNewMessagesPill(count = unseen, onClick = scrollToBottom)
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
            ChatQuickPhraseRow(onInsert = insert)
            if (showMore) {
                // 四个钮里只有「图片」真能用：其余三个点一下给一句话，不当哑巴按钮。
                ChatMorePanel(
                    onPickImage = sendImage,
                    onUnavailable = { imageHint = "只支持发图片" },
                )
            }
            if (showEmotions) {
                // 表情面板：贴在输入栏上方，点一颗把 token（如 [偷笑]）填进草稿，「键盘」把它收回去。
                EmotionPicker(
                    onPick = { emotion ->
                        rememberEmotionPick(emotion.name)
                        draft = draft + emotion.token
                    },
                    onDelete = {},
                    onKeyboard = { showEmotions = false },
                )
            }
            ChatComposer(
                draft = draft,
                onDraftChange = { draft = it },
                sending = sending,
                onSend = send,
                onInsert = insert,
                showMore = showMore,
                onToggleMore = {
                    showMore = !showMore
                    // 两个面板互斥：拉开一个就把另一个收回去。
                    if (showMore) showEmotions = false
                },
                showEmotions = showEmotions,
                onToggleEmotions = {
                    showEmotions = !showEmotions
                    if (showEmotions) showMore = false
                },
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
            } else if (imageHint != null) {
                Text(
                    text = imageHint.orEmpty(),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            if (sendFailed) {
                Text(
                    text = "没发出去，检查代理后再试",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
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
        }
    }
}

/**
 * 顶栏：回去的箭头 + 首字头像 + 名字（群聊挂人数）+ 右边两个圆按钮。
 *
 * 图标集里没有电话和相机，通话用 [FlGlyphs.profiles]、更多用 [FlGlyphs.more]；
 * 两个都走 [onOpenGroupInfo]——这一层没有单独的通话实现，点开的是同一个资料页。
 *
 * 左边的圆头像和标题也是一块入口：群聊点进群资料（[onOpenGroupInfo]），
 * 私聊点进对面那个人的主页（[onOpenProfile]，身份用 [peerOwner]）。私聊还没拿到对方身份时
 * 这一块不挂点击——空身份只会推开一张空主页。
 */
@Composable
private fun ChatTopBar(
    title: String,
    isGroup: Boolean,
    memberCount: Int,
    peerOwner: String,
    onNavigateBack: () -> Unit,
    onOpenGroupInfo: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 群聊有人数就挂在名字后面，和抖音一样是名字的一部分，不另起一个角标。
    val heading = if (isGroup && memberCount > 0) "${title}(${memberCount})" else title
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
                .height(FlTopBarHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlBarButton(
                glyph = FlGlyphs.chevronBack,
                contentDescription = "返回",
                onClick = onNavigateBack,
                iconSize = 20.dp,
                filled = false,
            )
            Spacer(Modifier.width(2.dp))
            ChatHeaderAvatar(title = title, isGroup = isGroup, onClick = openHeader)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f).then(headerClick)) {
                Text(
                    text = heading,
                    style = FlText.headline,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isGroup) {
                    Text(
                        text = if (memberCount > 0) "群聊 · ${memberCount} 人" else "群聊",
                        style = FlText.caption2,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            FlBarButton(
                glyph = FlGlyphs.profiles,
                contentDescription = "通话",
                onClick = onOpenGroupInfo,
                size = 36.dp,
                iconSize = 18.dp,
            )
            Spacer(Modifier.width(2.dp))
            FlBarButton(
                glyph = FlGlyphs.more,
                contentDescription = "更多",
                onClick = onOpenGroupInfo,
                size = 36.dp,
                iconSize = 18.dp,
            )
        }
    }
}

/**
 * 顶栏左边那个 40 的圆头像：首字画圆，没标题时群给「群」、私聊给「聊」，不留空圆。
 *
 * [onClick] 是「点头像进资料/主页」这一个动作；为 null 的时候就只是一个装饰圆，不抢触摸。
 */
@Composable
private fun ChatHeaderAvatar(title: String, isGroup: Boolean, onClick: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val circle = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(scheme.primary.copy(alpha = 0.15f), CircleShape)
    Box(
        modifier = if (onClick == null) {
            circle
        } else {
            circle.clickable(
                onClickLabel = if (isGroup) "打开群资料" else "看主页",
                onClick = onClick,
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = chatInitial(title = title, isGroup = isGroup),
            style = FlText.subheadline,
            fontWeight = FlText.emphasized,
            color = scheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * 一条消息左侧的小圆头像：别人的话才画，32 的圆里放 sender 的首字。
 *
 * 头像自己是一块独立的可点区域（[onClick]）：气泡整块挂的是长按复制，点头像看人不能挂在气泡上，
 * 否则两个手势会互相抢。[onClick] 为 null 时就只是一个装饰圆。
 */
@Composable
private fun ChatRowAvatar(label: String, onClick: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val circle = Modifier
        .size(32.dp)
        .clip(CircleShape)
        .background(scheme.primary.copy(alpha = 0.15f), CircleShape)
    Box(
        modifier = if (onClick == null) {
            circle
        } else {
            circle.clickable(onClickLabel = "看主页", onClick = onClick)
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.trim().take(1).ifEmpty { "?" },
            style = FlText.caption2,
            fontWeight = FlText.emphasized,
            color = scheme.primary,
            maxLines = 1,
        )
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
 * `kind == "image"` 的那一条不走文字那一套：不套气泡、不喂 [EmotionText]，正文（storage 路径）
 * 交给 [ChatImageBody] 去取字节画成一张图。图片消息自己也带时间，位置挪到图下面一行小字。
 * 图片的菜单只有「撤回」：正文是路径不是话，复制过去没有意义（见 [ChatBubbleMenu] 的 canCopy）。
 *
 * 头像上的点击是**另一块区域**（[onAvatarClick]）：头像在 [Row] 里是气泡的兄弟节点，气泡自己
 * 挂的是长按，两处手势不重叠，点头像不会顺手触发菜单，长按气泡也不会跳走。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    repository: SocialRepository,
    message: ChatMessage,
    mine: Boolean,
    showSender: Boolean,
    onAvatarClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    /** 撤回这条的回调；只有自己的消息给了它，长按才弹菜单，别人的长按仍旧直接复制。 */
    onDelete: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    // 自己的气泡带了撤回回调：长按弹「复制 / 撤回」菜单；其余情况维持各自原来的长按。
    val menuable = mine && onDelete != null
    var menuOpen by remember { mutableStateOf(false) }
    val longPress = when {
        menuable -> ({ menuOpen = true })
        mine -> null
        else -> onLongPress
    }
    // 图片不是文字：气泡的底色和 12 的内边距会在图四周多出一圈主色，那一路换成一张裸图。
    val isImage = message.kind == "image"
    // 长按的手套：文字和图片两条路都要挂一次，包成值免得写两遍修饰符链。
    val holdModifier = if (longPress == null) {
        Modifier
    } else {
        Modifier.combinedClickable(
            onClickLabel = "这条消息",
            onLongClickLabel = if (menuable) "打开这条消息的菜单" else "复制这条消息",
            onLongClick = longPress,
            onClick = {},
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            ChatRowAvatar(label = message.label, onClick = onAvatarClick)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = ChatBubbleMaxWidth),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            if (showSender) {
                Text(
                    text = message.label,
                    fontSize = 9.sp,
                    color = colors.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                )
            }
            if (isImage) {
                // 图片自己带圆角，所以外面不再套那个有底色、有内边距的气泡。
                Box(
                    modifier = Modifier
                        .clip(ChatImageShape)
                        .then(holdModifier),
                ) {
                    ChatImageBody(repository = repository, objectPath = message.body)
                    if (menuable) {
                        // 图片的菜单只有「撤回」：canCopy = false 把「复制」那一项拿掉。
                        ChatBubbleMenu(
                            modifier = Modifier.align(Alignment.TopEnd),
                            open = menuOpen,
                            canCopy = false,
                            onDismiss = { menuOpen = false },
                            onCopy = { onLongPress?.invoke() },
                            onDelete = { onDelete?.invoke() },
                        )
                    }
                }
                val clock = chatClockOf(message.createdAt)
                if (clock.isNotEmpty()) {
                    Text(
                        text = clock,
                        fontSize = 8.sp,
                        color = colors.outline,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (mine) colors.primary else colors.surfaceVariant)
                        .then(holdModifier)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Column {
                        EmotionText(
                            body = message.body,
                            fontSize = 14,
                            lineHeight = 19,
                            color = if (mine) colors.onPrimary else colors.onSurfaceVariant,
                        )
                        val clock = chatClockOf(message.createdAt)
                        if (clock.isNotEmpty()) {
                            Text(
                                text = clock,
                                fontSize = 8.sp,
                                color = if (mine) colors.onPrimary.copy(alpha = 0.7f) else colors.outline,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    if (menuable) {
                        // 菜单锚在气泡上：Box 包着的 DropdownMenu 贴着气泡右上角弹出来。
                        ChatBubbleMenu(
                            modifier = Modifier.align(Alignment.TopEnd),
                            open = menuOpen,
                            canCopy = true,
                            onDismiss = { menuOpen = false },
                            onCopy = { onLongPress?.invoke() },
                            onDelete = { onDelete?.invoke() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 气泡右上角那个长按菜单：「复制」（[canCopy] 时才画）与「撤回」。
 *
 * 两项都是先收起菜单再动手：菜单留着会把下面的气泡挡住，动作却已经发出去了，看着像没点上。
 * 图片消息传 [canCopy] = false —— 它的正文是 storage 路径，不是人写的字。
 */
@Composable
private fun ChatBubbleMenu(
    modifier: Modifier = Modifier,
    open: Boolean,
    canCopy: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
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
        DropdownMenuItem(
            text = { Text("撤回", fontSize = 14.sp) },
            onClick = {
                onDismiss()
                onDelete()
            },
        )
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
private fun ChatImageBody(repository: SocialRepository, objectPath: String) {
    var bitmap by remember(objectPath) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(objectPath) { mutableStateOf(false) }

    // 键是路径：同一个 slot 换了图就重新取。滚出去再滚回来会再取一次，但不会留着上一张。
    LaunchedEffect(objectPath) {
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

/** 右下角那个「⌃ 新消息(N)」胶囊：不在最底的时候才出现，点了滚到最新一条。 */
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

/** 输入框上面那排快捷短语：横向懒列表，胶囊样式和仓库里别的标签一致。 */
@Composable
private fun ChatQuickPhraseRow(onInsert: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(ChatQuickPhrases) { phrase ->
            Text(
                text = phrase.label,
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(scheme.surfaceContainerHigh)
                    .clickable(onClickLabel = phrase.label) { onInsert(phrase.insert) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
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
 * 底部那一行：九宫格（展开更多）+ 会长的输入框 + 三个圆按钮。
 *
 * 右边的第一个圆按钮是**会变的那一个**：输入框里有字它就是发送（[FlGlyphs.send]，主色），
 * 没字的时候才是语音按钮。发送是这个页面唯一的出口，不能让它藏在别处。
 * 表情钮拉开输入栏上方的表情面板（再点一次收起），加号和左边的九宫格一样拉开更多面板。
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
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlBarButton(
            glyph = FlGlyphs.appsList,
            contentDescription = if (showMore) "收起更多" else "展开更多",
            onClick = onToggleMore,
            size = 40.dp,
            iconSize = 20.dp,
            filled = false,
        )
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            textStyle = TextStyle(fontSize = 14.sp, color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            maxLines = 4,
            decorationBox = { inner ->
                if (draft.isEmpty()) {
                    Text(
                        text = "发消息或按住说话…",
                        fontSize = 14.sp,
                        color = colors.outline,
                    )
                }
                inner()
            },
        )
        Spacer(Modifier.width(6.dp))
        if (draft.isNotBlank()) {
            FlBarButton(
                glyph = FlGlyphs.send,
                contentDescription = "发送",
                onClick = onSend,
                size = 36.dp,
                iconSize = 17.dp,
                selected = true,
                enabled = !sending,
            )
        } else {
            FlBarButton(
                glyph = FlGlyphs.profiles,
                contentDescription = "语音",
                onClick = { onInsert("（语音）") },
                size = 36.dp,
                iconSize = 17.dp,
            )
        }
        Spacer(Modifier.width(4.dp))
        FlBarButton(
            glyph = FlGlyphs.add,
            contentDescription = if (showEmotions) "收起表情" else "表情",
            onClick = onToggleEmotions,
            size = 36.dp,
            iconSize = 17.dp,
        )
        Spacer(Modifier.width(4.dp))
        FlBarButton(
            glyph = FlGlyphs.addCircle,
            contentDescription = "更多",
            onClick = onToggleMore,
            size = 36.dp,
            iconSize = 17.dp,
        )
    }
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

/** 消息落在哪一天：后端串 `2026-11-21T08:40:00Z` 的前 10 个字符就是日期。 */
private fun chatDayOf(createdAt: String): String = createdAt.take(10)

/**
 * 日期分隔条上那一行字：`2026-11-21 08:40`。
 *
 * 不换算时区，也不比较"今天 / 昨天"——这条串是后端盖的 UTC 时间，本地换算要引一个时间库，
 * 而这一行灰字只需要让人看出"这是另一天"。日期都没有就返回空串，调用方不画。
 */
private fun chatDayStampOf(createdAt: String): String {
    if (createdAt.isBlank()) return ""
    val day = chatDayOf(createdAt).trim()
    if (day.isEmpty()) return ""
    val clock = chatClockOf(createdAt)
    return if (clock.isEmpty()) day else "${day} ${clock}"
}

/** 时间戳里的 HH:mm：取 `T` 后面那五个字符，不是这个形状就返回空串，不画一个猜出来的时间。 */
private fun chatClockOf(createdAt: String): String {
    val at = createdAt.indexOf('T')
    if (at < 0 || createdAt.length < at + 6) return ""
    return createdAt.substring(at + 1, at + 6)
}

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
