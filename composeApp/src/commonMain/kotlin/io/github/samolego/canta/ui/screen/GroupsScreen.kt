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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.Conversation
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.ui.component.UnreadDot
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBarHeight
import kotlinx.coroutines.delay

/** 页面开着时多久重拉一次群聊：没有推送通道，只能靠这一行让列表跟上别人的动作。 */
private const val GroupsPollMs: Long = 15_000L

/**
 * 「折叠的群聊」页：我折叠起来的那些群，一个不折叠的列表。
 *
 * 结构照抖音的群聊列表从上到下排：自绘顶栏（返回 / 标题 / 建群）、群列表、没有群时的空态。
 * 消息页那一行「折叠的群聊」只看得见最近说话的那个群，要翻全部折叠的群就走这一页——所以这里的
 * 排序只看时间，不分置顶：这一页的用处是「找那个群」，不是「按聊天习惯排」。
 *
 * 只列 [Conversation.folded] 的群：折叠是人在群设置里自己开的，没折叠的群留在消息页的主列表里，
 * 这边列出来就变成同一个群在两个地方各有一份。
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
    var foldedGroups by remember { mutableStateOf<List<Conversation>>(emptyList()) }
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
        // 一个查询就够：群聊是会话的一种，后端已经把两者放在同一张表里返回，这里挑出折叠的群。
        // 只看 `folded`：没折叠的群留在消息页主列表里，这边不该再列一遍。
        foldedGroups = repository.conversationsFor(owner)
            .filter { it.isGroup && it.folded }
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

                foldedGroups.isEmpty() -> Column(
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
                        text = "还没有折叠的群聊",
                        style = FlText.subheadline,
                        fontWeight = FlText.emphasized,
                        color = scheme.onSurface,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "在群设置里打开「折叠群聊」，那个群就收进这里",
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
                        items(foldedGroups, key = { "group-${it.id}" }) { group ->
                            GroupRow(group = group, onOpen = { onOpenChat(group.id) })
                        }
                        // 列表末尾那一句：微信「折叠的群聊」这一页也是这样收尾的，说明到这儿就是全部，
                        // 不是还没加载出来。
                        item(key = "groups-end") {
                            Text(
                                text = "暂时没有更多了",
                                style = FlText.caption2,
                                color = scheme.outline,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                textAlign = TextAlign.Center,
                            )
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
                text = "折叠的群聊",
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
 * 一个群：圆头像里放群名首字，右边两行（群名 + 静音标，[N条] + 最后一句），行尾一个红点。
 *
 * 形状照微信的「折叠的群聊」：群名后面挂一个划掉的铃说明它被静音了，摘要前面写「[61条]」
 * 表示攒了多少条没看——折叠起来的群里，未读数比「谁说了什么」更值得先看见。
 *
 * 这一页是「找到那个群然后进去」，所以不带长按菜单：置顶、免打扰、删除都留在消息页做，
 * 免得同一个动作在两个地方长得不一样。
 */
@Composable
private fun GroupRow(group: Conversation, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val time = groupClockOf(group.lastAt)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.label,
                    style = FlText.subheadline,
                    fontWeight = FlText.emphasized,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // 静音小标：和微信一样挂在群名后面，说明这个群被设成免打扰了。
                if (group.muted) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = "已设为免打扰",
                        modifier = Modifier.size(12.dp),
                        tint = scheme.outline,
                    )
                }
                if (time.isNotEmpty()) {
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    Text(text = time, style = FlText.caption2, color = scheme.outline)
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = groupDigest(group),
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 未读只画一个红点，不写数字：摘要前面已经有「[N条]」，一个红点够了。
                if (group.unread > 0) {
                    Spacer(Modifier.width(6.dp))
                    UnreadDot(size = 7.dp)
                }
            }
        }
    }
}

/**
 * 那一行摘要：有未读就写「[N条] 最后一句」，没有就只写最后一句。
 *
 * 「[N条]」是微信折叠群聊的写法，一眼看得出「这个群攒了多少」，而不是只有一句旧话在那儿。
 * 最后一条是谁说的，后端 `conversations_for` 没给，这里不猜。
 */
private fun groupDigest(group: Conversation): String {
    val body = group.lastBody.ifBlank { "还没有消息" }
    return if (group.unread > 0) "[${group.unread}条] $body" else body
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
