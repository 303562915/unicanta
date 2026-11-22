package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.Conversation
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.ui.component.UnreadBadge
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBarHeight
import kotlinx.coroutines.delay

/** 页面开着时多久重拉一次群聊：没有推送通道，只能靠这一行让列表跟上别人的动作。 */
private const val GroupsPollMs: Long = 15_000L

/**
 * 「群聊」页：我参与的每一个群，一个不折叠的列表。
 *
 * 结构照抖音的群聊列表从上到下排：自绘顶栏（返回 / 标题 / 建群）、群列表、没有群时的空态。
 * 消息页那一行「折叠的群聊」只看得见最近说话的那个群，要翻全部群就走这一页——所以这里的
 * 排序只看时间，不分置顶：这一页的用处是「找那个群」，不是「按聊天习惯排」。
 *
 * 这一页只做两件事：把群列表画出来、点一行进对话。会话数据来自 [SocialRepository]，一次拉一页，
 * 拉不到就画空态并留一句原因（[SocialRepository.lastError]）。
 */
@Composable
fun GroupsScreen(
    repository: SocialRepository,
    owner: String,
    onOpenChat: (conversationId: Long) -> Unit,
    onOpenCreateGroup: () -> Unit,
    onNavigateBack: () -> Unit,
    onMessage: (String) -> Unit = {},
) {
    var loading by remember { mutableStateOf(true) }
    var groups by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // 已经拉过几次：定时刷新不能把 loading 又点亮一遍，否则每十几秒整页闪一下骨架。
    var reloads by remember { mutableIntStateOf(0) }

    // 没有推送通道：页面开着就自己问，15 秒一次，和消息页同一个节奏与同一个额度预算。
    LaunchedEffect(owner) {
        if (owner.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(GroupsPollMs)
            reloads++
        }
    }

    LaunchedEffect(owner, reloads) {
        if (reloads == 0) loading = true
        loadError = null
        // 一个查询就够：群聊是会话的一种，后端已经把两者放在同一张表里返回，这里只挑 groups。
        groups = repository.conversationsFor(owner)
            .filter { it.isGroup }
            .sortedByDescending { it.lastAt }
        // 拉不到就是空列表，和「真的一个群都没有」在界面上长得一样，所以要留一句原因。
        loadError = repository.lastError
        loading = false
    }

    val scheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            GroupsTopBar(
                onNavigateBack = onNavigateBack,
                onCreateGroup = onOpenCreateGroup,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                groups.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    loadError?.let { reason ->
                        Text(
                            text = "后端没有返回群聊：$reason",
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    Text(
                        text = "还没有群聊",
                        style = FlText.subheadline,
                        fontWeight = FlText.emphasized,
                        color = scheme.onSurface,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "点右上角建一个",
                        style = FlText.caption2,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    loadError?.let { reason ->
                        Text(
                            text = "后端没有返回群聊：$reason",
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(groups, key = { "group-${it.id}" }) { group ->
                            GroupRow(group = group, onOpen = { onOpenChat(group.id) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顶栏自己画：回去的箭头、标题、右边的建群。
 *
 * 高度、安全区与留白照这一套页面里其它自绘顶栏（[FlTopBarHeight]、`statusBars` 内边距），
 * 返回用 [FlGlyphs.chevronBack]：与「发起群聊」「群资料」两页同一个箭头，人从那边过来不用重新认。
 */
@Composable
private fun GroupsTopBar(onNavigateBack: () -> Unit, onCreateGroup: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(FlTopBarHeight).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlBarButton(
                glyph = FlGlyphs.chevronBack,
                contentDescription = "返回",
                onClick = onNavigateBack,
                iconSize = 20.dp,
                filled = false,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "群聊",
                style = FlText.headline,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FlBarButton(
                glyph = FlGlyphs.addCircle,
                contentDescription = "发起群聊",
                onClick = onCreateGroup,
                iconSize = 20.dp,
                filled = false,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * 一个群：圆头像里放群名首字，中间两行（群名、最后一句），右边一列（时间、未读数）。
 *
 * 形状与消息页的会话行一致，只是不带长按菜单：这一页是「找到那个群然后进去」，
 * 置顶与删除留在消息页做，免得同一个动作在两个地方长得不一样。
 */
@Composable
private fun GroupRow(group: Conversation, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainer)
            .clickable(onClickLabel = group.label, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupAvatar(label = group.label)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.label,
                style = FlText.subheadline,
                fontWeight = FlText.emphasized,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = group.lastBody.ifBlank { "还没有消息" },
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val time = groupClockOf(group.lastAt)
            if (time.isNotEmpty()) {
                Text(text = time, style = FlText.caption2, color = scheme.outline)
            }
            if (group.unread > 0) {
                UnreadBadge(count = group.unread)
            }
        }
    }
}

/** 群头像：群名首字画圆，和消息页的头像同一个尺寸与同一个底色。没有名字就画一个问号。 */
@Composable
private fun GroupAvatar(label: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(scheme.primary.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.take(1).ifEmpty { "?" },
            style = FlText.subheadline,
            fontWeight = FlText.emphasized,
            color = scheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * 时间戳里的 HH:mm：列表右侧只画钟点，日期留给对话页。
 *
 * 与消息页 `clockOf` 同一套做法（这边自己一份，因为那边是 private）——后端盖的是
 * `2026-11-20T10:00:00.000000Z`，取 T 后面那五个字符就是。不是这个形状（没有 T、或者被截短了）
 * 就返回空串：宁可这一格空着，也不画一个猜出来的时间。
 */
private fun groupClockOf(stamp: String): String {
    val at = stamp.indexOf('T')
    if (at < 0 || stamp.length < at + 6) return ""
    return stamp.substring(at + 1, at + 6)
}
