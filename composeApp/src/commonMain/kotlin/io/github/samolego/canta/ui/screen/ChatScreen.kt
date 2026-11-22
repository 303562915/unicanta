package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.ChatMessage
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBarHeight
import kotlinx.coroutines.launch

/** 一个气泡最宽多少：一行字到这儿换行，免得一条长消息把整块屏幕占满。 */
private val ChatBubbleMaxWidth = 280.dp

/**
 * 私聊 / 群聊一页。
 *
 * 结构是最朴素的聊天页：顶栏一个标题、中间一列气泡、下面一行输入。别的都不做——
 * 没有已读回执的动画，没有"正在输入"，因为后端只提供两件事：读一页 [SocialRepository.messagesFor]
 * 和写一条 [SocialRepository.sendMessage]。写成功的那一条直接用后端回来的行画出来，不等下一次拉取。
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
) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendFailed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 一个会话一页：进页面读最近 30 条，读完把已读位置推到现在。
    LaunchedEffect(conversationId) {
        loading = true
        loadError = null
        messages = repository.messagesFor(owner, conversationId, offset = 0, limit = 30)
        loadError = chatErrorOf(repository.lastError, repository.lastFailureWasNetwork)
        loading = false
        repository.markRead(owner, conversationId)
    }

    // 新消息（自己发的也算）落到最下面才看得见；列表是正序的，所以滚到最后一行。
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
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

    Scaffold(
        topBar = {
            ChatTopBar(title = title, isGroup = isGroup, onNavigateBack = onNavigateBack)
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
                        items(messages, key = { message -> message.id }) { message ->
                            ChatBubble(
                                message = message,
                                mine = message.sender == owner,
                                // 群聊里别人的气泡要说清是谁：一对一的对方不用说，页面标题就是名字。
                                showSender = isGroup && message.sender != owner,
                            )
                        }
                    }
                }
            }
            ChatComposer(
                draft = draft,
                onDraftChange = { draft = it },
                userName = userName,
                sending = sending,
                onSend = send,
            )
            if (sendFailed) {
                Text(
                    text = "没发出去，检查代理后再试",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/**
 * 顶栏：回去的箭头 + 标题（没给标题就叫「私聊」）。
 *
 * 群聊在标题后面挂一个灰色小标，因为同一个页面里"这是谁在说话"决定了气泡上面要不要写名字。
 */
@Composable
private fun ChatTopBar(title: String, isGroup: Boolean, onNavigateBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlTopBarHeight)
                .padding(horizontal = 10.dp),
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
            Text(
                text = title.ifBlank { "私聊" },
                style = FlText.headline,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isGroup) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "群聊",
                    fontSize = 9.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.surfaceContainerHigh)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * 一条消息：自己的靠右、主色气泡、主色上的字；别人的靠左、灰气泡。
 *
 * 群聊里别人的气泡上面署一个名字（[ChatMessage.label]，后端没给名字就退回编号）。
 */
@Composable
private fun ChatBubble(message: ChatMessage, mine: Boolean, showSender: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
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
        Box(
            modifier = Modifier
                .widthIn(max = ChatBubbleMaxWidth)
                .clip(RoundedCornerShape(14.dp))
                .background(if (mine) colors.primary else colors.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column {
                Text(
                    text = message.body,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = if (mine) colors.onPrimary else colors.onSurfaceVariant,
                )
                ChatStampOf(message.createdAt)?.let { stamp ->
                    Text(
                        text = stamp,
                        fontSize = 8.sp,
                        color = if (mine) colors.onPrimary.copy(alpha = 0.7f) else colors.outline,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * 底部那一行：一个会长的输入框 + 一个圆的发送按钮。
 *
 * 按钮在输入框有字的时候才亮成主色（[FlBarButton] 的 selected），没字的时候只是个灰按钮，
 * 点了也不会有事发生——[onSend] 自己会挡空消息。
 */
@Composable
private fun ChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    userName: String,
    sending: Boolean,
    onSend: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val me = userName.ifBlank { "我" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                        text = "以${me}的身份说点什么",
                        fontSize = 14.sp,
                        color = colors.outline,
                    )
                }
                inner()
            },
        )
        Spacer(Modifier.width(8.dp))
        FlBarButton(
            glyph = FlGlyphs.send,
            contentDescription = "发送",
            onClick = onSend,
            size = 40.dp,
            iconSize = 18.dp,
            selected = draft.isNotBlank(),
            enabled = !sending,
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

/**
 * 时间只用最朴素的那种写法：后端给的是 `2026-11-22T19:20:33Z`，这里截到分钟，
 * 把中间那个 `T` 换成空格；没有时间就不画这一行。不引时间库，也不换算时区。
 */
private fun ChatStampOf(createdAt: String): String? {
    if (createdAt.isBlank()) return null
    return createdAt.take(16).replace('T', ' ')
}

/** 拉不到消息的两种原因分开说：没连上是网络的事，被拒绝是后端的事。 */
private fun chatErrorOf(reason: String?, network: Boolean): String? = when {
    network -> "没连上后端，检查代理或网络后再试。"
    !reason.isNullOrBlank() -> "后端没有返回消息：$reason"
    else -> null
}
