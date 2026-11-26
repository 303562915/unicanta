package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
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
import io.github.samolego.canta.data.cache.ScreenCache
import kotlinx.serialization.Serializable
import io.github.samolego.canta.data.community.Conversation
import io.github.samolego.canta.data.community.OfficialGroups
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.ui.component.UnreadDot
import io.github.samolego.canta.ui.component.bottomHairline
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBarHeight
import io.github.samolego.canta.util.BeijingTime
import kotlinx.coroutines.delay

/**
 * 页面开着时多久重拉一次群聊：没有推送通道，只能靠这一行让列表跟上别人的动作。
 *
 * 60 秒，不是 15 秒。这一页只画「有哪些群、几个成员、最后一条消息」——那三样东西在 15 秒里
 * 基本不变，人眼看不出 15 秒和 60 秒的差别，但后端承受的差别是四倍。这个数字按实测调的：
 * 2026-11-26 那次全站白屏的根因是免费档 426 MB 内存被 swap 拖死（见 ops-notes 备案），
 * 而 `conversations_for` 是 24 小时 3.6 万次调用里的主力之一，降频是唯一不花钱的解法。
 *
 * **不要为了"感觉更实时"把它调回去。** 真要即时，就得开推送频道，而那要额外一条
 * WebSocket —— 免费档 Realtime 并发只有 200，比轮询更容易出事。
 */
private const val GroupsPollMs: Long = 60_000L

/**
 * 这一屏上一次画出来的群列表，整份进 [ScreenCache]。
 *
 * 只放画一屏要用的只读数据：折叠群的那几行。所有 `remember` 里的东西（loading、错误原因、刷新计数）
 * 都不进来——那些是「这一次打开页面时发生的事」，下一次进来该重新开始。
 */
@Serializable
private data class GroupsSnapshot(val groups: List<Conversation>)

/**
 * 这一页缓存的槽位：按身份分。
 *
 * 身份为空时返回空串：调用方是从 `DeviceIdentity.current(...)` 异步取身份的，第一帧就是空串；
 * 空键会把不同身份的群串进同一个槽位，[ScreenCache] 也会丢弃空键的写入。
 */
private fun groupsCacheKey(owner: String): String =
    if (owner.isEmpty()) "" else "screen:groups:$owner"

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
    onOpenChat: (conversationId: Long, isGroup: Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenCreateGroup: () -> Unit,
    onNavigateBack: () -> Unit,
    onMessage: (String) -> Unit = {},
) {
    // 上一屏留下的那一份先摆上：这一页每次被导航进来都是新的 Composable 实例，`remember` 跟着一起
    // 没了，所以快照放在进程级的 [ScreenCache] 里（见 [GroupsSnapshot]）。有它就直接画，没有再走加载。
    //
    // 按 [cacheKey] 做 remember 的键：身份是异步到的，`owner` 从空串变成真身份时这几个状态会按缓存
    // 重新初始化一次，那一刻缓存才接得上。
    val cacheKey = groupsCacheKey(owner)
    val cached = remember(cacheKey) {
        if (cacheKey.isEmpty()) null else ScreenCache.get<GroupsSnapshot>(cacheKey)
    }
    // 有缓存就不停在上面的转圈上：这一页是「找那个群」，看到上一次的列表比看到空白有用。
    var loading by remember(cacheKey) { mutableStateOf(cached == null) }
    var foldedGroups by remember(cacheKey) { mutableStateOf(cached?.groups.orEmpty()) }
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
        // 首次进来又没有缓存时才摆骨架；后面每 15 秒的刷新只把新的一版顶上去。
        if (reloads == 0 && cached == null) loading = true
        loadError = null
        // 一个查询就够：群聊是会话的一种，后端已经把两者放在同一张表里返回，这里挑出折叠的群。
        // 只看 `folded`：没折叠的群留在消息页主列表里，这边不该再列一遍。
        val fresh = repository.conversationsFor(owner)
            // 没名字的群（早期测试留下的空群）在这里也不列，和消息页同一道过滤。
            .filter { it.isGroup && it.folded && OfficialGroups.isVisibleInList(it) }
            .sortedByDescending { it.lastAt }
        // 拉不到就是空列表，和「真的一个群都没有」在界面上长得一样，所以要留一句原因。
        //
        // 失败时不覆盖屏幕上那一份：照着空列表画就等于因为一次断网把群清空，下一次进来还得重拉。
        val failure = repository.lastError
        if (failure == null) {
            foldedGroups = fresh
            if (cacheKey.isNotEmpty()) ScreenCache.put(cacheKey, GroupsSnapshot(foldedGroups))
        }
        loadError = failure
        loading = false
    }

    val scheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            GroupsTopBar(
                onNavigateBack = onNavigateBack,
                // 建群先关着（后端 `group_create` 一律返回建不成），点在原地说明一句。
                onCreateGroup = { onMessage("群聊暂未开放，只能进官方群") },
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
                    ) {
                        items(foldedGroups, key = { "group-${it.id}" }) { group ->
                            // 这一页里的每一行都是群，群身份直接给出去：聊天页开门就按群画。
                            GroupRow(group = group, onOpen = { onOpenChat(group.id, true) })
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
        // 顶栏下面那条发丝线，和消息页、互动消息页同一条。
        HorizontalDivider(
            thickness = 0.5.dp,
            color = scheme.outlineVariant.copy(alpha = 0.6f),
        )
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
            .background(scheme.surface)
            .bottomHairline(scheme.outlineVariant.copy(alpha = 0.6f))
            .clickable(onClickLabel = group.label, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
 * 列表右侧那一格时间，和消息页同一个写法（[BeijingTime.listStamp]）：刚刚 / N分钟前 / HH:mm /
 * 昨天 HH:mm / MM-dd。
 *
 * 这一页原来自己切字符串取 `HH:mm`，那是 UTC 的钟点，比北京时间早八小时；现在统一走北京时间。
 */
private fun groupClockOf(stamp: String): String = BeijingTime.listStamp(stamp)
