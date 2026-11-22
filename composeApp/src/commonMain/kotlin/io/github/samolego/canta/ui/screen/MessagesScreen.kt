package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.samolego.canta.data.community.Conversation
import io.github.samolego.canta.data.community.InboxSummary
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.SocialPerson
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.ui.component.UnreadBadge
import io.github.samolego.canta.ui.component.UnreadRed
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBarHeight
import kotlinx.coroutines.delay

/** 页面开着时多久问一次后端：没有推送，靠这一行让列表跟上别人的动作。 */
private const val MessagePollMs: Long = 15_000L

/** 头像那一排最多画多少位最近联系人：再多就不是「最近」了。 */
private const val RecentContactCap: Int = 8

/** 横排里从会话、从关注列表各取多少位，取完去重再截到 [RecentContactCap]。 */
private const val ContactsFromChats: Int = 6
private const val ContactsFromFollows: Int = 6

/** 「限时日常」与每位联系人那一格的宽度：圆头像 44，下面一行字居中。 */
private val ContactCellWidth = 64.dp

/** 「还没有好友」那一格的宽度：比头像格宽一点，五个字不用折行。 */
private val HintCellWidth = 88.dp

/** 折叠群聊那个圆底的绿：和互动消息的红一样，一个颜色一件事。 */
private val GroupGreen: Color = Color(0xFF07C160)

/**
 * 「消息」页：私聊与群聊的入口，摆在互动消息下面。
 *
 * 结构照抖音的消息页从上到下排：自绘顶栏（菜单 / 标题 / 搜索 + 建群）、一排最近联系人、
 * 两行整块的入口（互动消息、折叠的群聊）、最后是会话列表。
 *
 * 这一页只负责「去哪儿」：点会话把会话号交给 [onOpenChat]，点互动把页面交给
 * [onOpenInteractions]，还没做的入口一律 [onMessage]。什么都不自己写：会话、关注、未读数
 * 全部来自 [SocialRepository] 和 [PlanRepository]，一页一次拉，失败就画空态。
 */
@Composable
fun MessagesScreen(
    repository: SocialRepository,
    planRepository: PlanRepository,
    owner: String,
    userName: String,
    token: String,
    inboxUnread: Int,
    inboxPreview: String,
    onOpenInteractions: () -> Unit,
    onOpenChat: (conversationId: Long) -> Unit,
    /** 发起群聊：挑人、起名、建群。 */
    onOpenCreateGroup: () -> Unit = {},
    /** 打开和某人的私聊：没有会话就先开一个（后端 dm_open 是幂等的）。 */
    onOpenDm: (peerOwner: String) -> Unit = {},
    onMessage: (String) -> Unit = {},
) {
    var loading by remember { mutableStateOf(true) }
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var followers by remember { mutableStateOf<List<SocialPerson>>(emptyList()) }
    // 刚数回来的互动总数：这一版的副标题和红点都按调用方传进来的值画（见下面两行入口），
    // 这个数先留着，页面拿到它才算把「有多少条新互动」问过一遍。
    var summary by remember { mutableStateOf(InboxSummary.EMPTY) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // 已经拉过几次：定时刷新不能把 loading 又点亮一遍，否则每十几秒整页闪一下骨架。
    var reloads by remember { mutableIntStateOf(0) }

    // 没有推送通道，页面开着的时候只能自己问。15 秒一次，比"退出再进"快得多，又不至于把
    // 每分钟 120 次的后端额度吃满（一次刷新是三四个查询）。
    LaunchedEffect(owner) {
        if (owner.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(MessagePollMs)
            reloads++
        }
    }

    // 从会话页、建群页退回来时立刻再问一次：定时器最久还要等 15 秒，人刚说完话回来却看到
    // 旧的预览和旧的未读数，会以为没发出去。第一次进入不重复拉（那时 reloads 还是 0）。
    var resumedOnce by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumedOnce) reloads++ else resumedOnce = true
    }

    // 一页三次请求，一次拉完再画：先画骨架再补数据会让这一页闪两下。
    LaunchedEffect(owner, reloads) {
        if (reloads == 0) loading = true
        loadError = null
        val chats = repository.conversationsFor(owner)
        val people = repository.followersOf(owner)
        summary = planRepository.inboxSummary(owner, token) ?: InboxSummary.EMPTY
        conversations = chats
        followers = people
        // 拉不到就说一句：空列表和后端没通在界面上长得一样，这一行是唯一的区别。
        loadError = repository.lastError
        loading = false
    }

    val scheme = MaterialTheme.colorScheme
    val groups = conversations.filter { it.isGroup }
    // 折叠的群聊那一行只亮「最近说过话的那个群」：它的最后一句就是这个入口的预览。
    val latestGroup = groups.maxByOrNull { it.lastAt }
    val groupUnread = groups.sumOf { it.unread }
    val mutual = mutualKeysOf(followers)
    val recentContacts = recentContactsOf(conversations, followers)

    Scaffold(
        topBar = {
            MessagesTopBar(
                onMenu = { onMessage("菜单还没做") },
                onSearch = { onMessage("搜索还没做") },
                onNewGroup = onOpenCreateGroup,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                loadError?.let { reason ->
                    Text(
                        text = "后端没有返回消息：$reason",
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
                RecentContactsRow(
                    userName = userName,
                    contacts = recentContacts,
                    onTapDaily = { onMessage("限时日常还没做") },
                    onTapContact = { contact ->
                        // 有关会话就进去；只有关注关系的，开一个私聊再进去——那一格点下去就是
                        // "和这个人说话"，弹一句"还没聊过"等于让用户自己去别处找入口。
                        if (contact.conversationId > 0L) {
                            onOpenChat(contact.conversationId)
                        } else {
                            onOpenDm(contact.key)
                        }
                    },
                )
                MessageEntryRow(
                    glyph = FlGlyphs.send,
                    circle = UnreadRed,
                    title = "互动消息",
                    subtitle = inboxPreview.ifBlank { inboxLine(summary) },
                    unread = inboxUnread,
                    onClick = onOpenInteractions,
                )
                MessageEntryRow(
                    glyph = FlGlyphs.appsList,
                    circle = GroupGreen,
                    title = "折叠的群聊",
                    subtitle = latestGroup?.lastBody?.ifBlank { "还没有消息" } ?: "还没有群聊",
                    unread = groupUnread,
                    onClick = {
                        val group = latestGroup
                        if (group != null) onOpenChat(group.id) else onMessage("还没有群聊")
                    },
                )
                // 会话列表吃掉剩下的高度：上面那三块是固定的入口，只有这里可以滚。
                if (conversations.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "还没有私聊，去方案页找人聊聊",
                            style = FlText.footnote,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(conversations, key = { it.conversationKey() }) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                // 只有两个方向都成立的人，名字右边才挂那个灰标。
                                mutual = mutual.contains(conversation.peer) ||
                                    mutual.contains(conversation.peerName),
                                onOpen = { onOpenChat(conversation.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顶栏自己画：左边一个「菜单」占位，中间「消息」，右边搜索与建群。
 *
 * 不用 [io.github.samolego.canta.ui.component.ScreenTopBar]：那个只留一个返回箭头和一个
 * 尾部动作位，抖音这一页是左边菜单、右边两个图标。高度、留白、标题字号照它的那一套
 * （[FlTopBarHeight]、20.dp 边距、[FlText.headline]）。
 */
@Composable
private fun MessagesTopBar(
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onNewGroup: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(FlTopBarHeight).padding(horizontal = 20.dp),
        ) {
            FlBarButton(
                glyph = FlGlyphs.more,
                contentDescription = "菜单",
                onClick = onMenu,
                modifier = Modifier.align(Alignment.CenterStart),
                iconSize = 20.dp,
                filled = false,
            )
            Text(
                text = "消息",
                style = FlText.headline,
                color = scheme.onSurface,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlBarButton(
                    glyph = FlGlyphs.search,
                    contentDescription = "搜索",
                    onClick = onSearch,
                    iconSize = 20.dp,
                    filled = false,
                )
                FlBarButton(
                    glyph = FlGlyphs.addCircle,
                    contentDescription = "新建群聊",
                    onClick = onNewGroup,
                    iconSize = 20.dp,
                    filled = false,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 横排里的一位最近联系人；[conversationId] 为 0 表示只关注过、还没有会话。 */
private data class RecentContact(
    val key: String,
    val label: String,
    val conversationId: Long,
)

/** 会话行在列表里的 key：会话号本身就是唯一的。 */
private fun Conversation.conversationKey(): String = "conversation-$id"

/**
 * 互相关注的人，按编号和名字各记一份。
 *
 * 后端的会话只带 `peer`（身份）和 `peer_name`（今天用的名字），关注列表给的是 `id` / `name`，
 * 两个字段对得上任何一个就算同一个人。
 */
private fun mutualKeysOf(followers: List<SocialPerson>): Set<String> =
    followers.filter { it.iFollow && it.followsMe }
        .flatMap { listOf(it.id, it.name) }
        .filter { it.isNotEmpty() }
        .toSet()

/**
 * 头像那一排的人：先取聊过的（非群聊会话），再取关注列表里的人，同一个身份只画一格。
 *
 * 聊过的排在前面是有意的——那一格点了能直接进会话；只有关注关系的排在后面，点了会说明还
 * 没聊过。已经聊过的人不会因为出现在关注列表里而画第二遍。
 */
private fun recentContactsOf(
    conversations: List<Conversation>,
    followers: List<SocialPerson>,
): List<RecentContact> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<RecentContact>()
    conversations.filter { !it.isGroup }.take(ContactsFromChats).forEach { conversation ->
        val key = conversation.peer.ifEmpty { conversation.label }
        if (key.isNotEmpty() && seen.add(key)) {
            out += RecentContact(
                key = key,
                label = conversation.label,
                conversationId = conversation.id,
            )
        }
    }
    followers.take(ContactsFromFollows).forEach { person ->
        val key = person.id.ifEmpty { person.name }
        if (key.isNotEmpty() && seen.add(key)) {
            out += RecentContact(key = key, label = person.label, conversationId = 0L)
        }
    }
    return out.take(RecentContactCap)
}

/**
 * 「限时日常」那一格和最近联系人共用的一排。
 *
 * 一个人都没有的时候不整排空着，而是画一格「还没有好友」：一排空的横线看起来像没加载出来。
 */
@Composable
private fun RecentContactsRow(
    userName: String,
    contacts: List<RecentContact>,
    onTapDaily: () -> Unit,
    onTapContact: (RecentContact) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "daily") { DailyCell(userName = userName, onClick = onTapDaily) }
        if (contacts.isEmpty()) {
            item(key = "no-friends") { NoFriendsCell() }
        } else {
            items(contacts, key = { "contact-${it.key}" }) { contact ->
                ContactCell(contact = contact, onClick = { onTapContact(contact) })
            }
        }
    }
}

/** 「限时日常」：一个描边的圆加号，下面一行字。 */
@Composable
private fun DailyCell(userName: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(ContactCellWidth)
            .clickable(
                onClickLabel = if (userName.isEmpty()) "限时日常" else "$userName 的限时日常",
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, scheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(glyph = FlGlyphs.add, size = 18.dp, tint = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "限时日常",
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一位最近联系人：首字画圆，下面一行名字。 */
@Composable
private fun ContactCell(contact: RecentContact, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(ContactCellWidth)
            .clickable(onClickLabel = contact.label, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InitialAvatar(label = contact.label)
        Spacer(Modifier.height(6.dp))
        Text(
            text = contact.label,
            style = FlText.caption2,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一个好友都没有时的提示格：灰圆里一个人像，下面一句实话。 */
@Composable
private fun NoFriendsCell() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.width(HintCellWidth).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(scheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(glyph = FlGlyphs.profiles, size = 18.dp, tint = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "还没有好友",
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 整块的一行入口：左边一个色圆里放图标，中间标题与副标题，右边未读数。
 *
 * 互动消息和折叠的群聊共用这一个形状，只是圆底的颜色和图标不同——两行看起来是一对，
 * 读起来也就能一眼分出「别人对我做了什么」和「几个人的群」。
 */
@Composable
private fun MessageEntryRow(
    glyph: FlGlyph,
    circle: Color,
    title: String,
    subtitle: String,
    unread: Int,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(circle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(glyph = glyph, size = 22.dp, tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = FlText.subheadline,
                fontWeight = FlText.emphasized,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        // 时间只留给有时间的行（会话列表）：这两行是入口，没有「最后一条」可言。
        UnreadBadge(count = unread)
    }
}

/** 一条会话：头像、名字（互相关注带灰标）、最后一句、时间与未读数。 */
@Composable
private fun ConversationRow(
    conversation: Conversation,
    mutual: Boolean,
    onOpen: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(label = conversation.label)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.label,
                    style = FlText.subheadline,
                    fontWeight = FlText.emphasized,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (mutual) {
                    Spacer(Modifier.width(6.dp))
                    MutualFollowTag()
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = conversation.lastBody.ifBlank { "还没有消息" },
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
            val time = clockOf(conversation.lastAt)
            if (time.isNotEmpty()) {
                Text(text = time, style = FlText.caption2, color = scheme.outline)
            }
            UnreadBadge(count = conversation.unread)
        }
    }
}

/** 名字右边那个小灰标：两个人互相都关注了，说一句。 */
@Composable
private fun MutualFollowTag() {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = "互相关注",
        style = FlText.caption2,
        color = scheme.outline,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** 头像：名字首字画圆。没有名字（连编号都没有）就画一个问号，不留一个空圆。 */
@Composable
private fun InitialAvatar(label: String) {
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
 * 时间戳里的 HH:mm：列表右侧只画钟点，日期留给会话页。
 *
 * 后端盖的是 `2026-11-20T10:00:00.000000Z`，取 T 后面那五个字符就是。不是这个形状（没有 T、
 * 或者被截短了）就返回空串——宁可这一格空着，也不画一个猜出来的时间。
 */
private fun clockOf(stamp: String): String {
    val at = stamp.indexOf('T')
    if (at < 0 || stamp.length < at + 6) return ""
    return stamp.substring(at + 1, at + 6)
}

/**
 * 互动消息那一行的副标题：调用方没有现成的一句时，用后端已经数好的四个数字拼一句。
 *
 * 顺序按读者关心的先后：有人回复你比有人点了个赞要紧。一个数字都没有就等于没有新闻，
 * 这时说「还没有新的互动」比列四个 0 诚实。
 */
private fun inboxLine(summary: InboxSummary): String {
    if (summary.total <= 0) return "还没有新的互动"
    val parts = buildList {
        if (summary.replies > 0) add("回复 ${summary.replies}")
        if (summary.planComments > 0) add("评论 ${summary.planComments}")
        val hearts = summary.commentHearts + summary.planHearts
        if (hearts > 0) add("赞 $hearts")
    }
    return parts.joinToString(" · ").ifBlank { "还没有新的互动" }
}
