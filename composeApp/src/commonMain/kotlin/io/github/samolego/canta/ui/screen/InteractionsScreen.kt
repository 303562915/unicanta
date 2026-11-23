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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.COMMENT_KIND_APP
import io.github.samolego.canta.data.community.InboxItem
import io.github.samolego.canta.data.community.InboxKind
import io.github.samolego.canta.data.community.InboxSummary
import io.github.samolego.canta.data.community.Page
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.SocialPerson
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.data.community.newestInboxCommentId
import io.github.samolego.canta.ui.component.UnreadRed
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBarHeight
import io.github.samolego.canta.ui.fl.colorOf
import kotlinx.coroutines.launch

/** 一页互动消息从每个来源各取多少行。 */
private const val INTERACTION_PER_SOURCE = 8

/**
 * 「互动消息」页：别人对我做过什么，以及谁新近关注了我。
 *
 * 两件事画在一个页面里，因为它们是同一个问题的两面：*别人对我做过什么*（回复、方案下的评论、
 * 点赞、认同）走 [PlanRepository.inboxPage]，*谁刚关注了我* 走 [SocialRepository.followersOf]。
 * 顶栏把两栏做成左右两个文字入口，选中的那一栏在左边当标题，另一边是去另一栏的入口，
 * 宽度不会因为页签数量而跳动。
 *
 * 名单只翻一页：消息流是一年一年长出来的历史，所以要分页（滚到底再问下一段 offset）；
 * 关注列表是当下的状态，一次给全。
 *
 * 打开消息这一栏，就等于把它们读过：第一页到货后把「最新的一条」和「后端计数时的那个瞬间」
 * 交回上层（[onInboxSeen]），红点靠那对值熄灭，而不是靠本页记住看了几行。
 */
@Composable
fun InteractionsScreen(
    repository: PlanRepository,
    social: SocialRepository,
    owner: String,
    token: String,
    seenAt: String = "",
    onInboxSeen: (newestCommentId: Long, checkedAt: String) -> Unit = { _, _ -> },
    onOpenPlanComment: (planId: Long, commentId: Long) -> Unit = { id, _ -> },
    onOpenAppComment: (packageName: String, commentId: Long) -> Unit = { pkg, _ -> },
    /** 点行头像看那个人的主页；后端没给身份号时行内不挂这个点击。 */
    onOpenProfile: (owner: String) -> Unit = {},
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 0 = 互动消息，1 = 新关注我的。
    var tab by remember { mutableIntStateOf(0) }
    var inbox by remember { mutableStateOf<Page<InboxItem>>(Page.EMPTY) }
    // 每个来源读到哪儿：一页从同一个 offset 各取 [INTERACTION_PER_SOURCE] 行，所以 offset 按
    // 这个窗口往前推，而不是按回来多少行数——某一路只回了三行，不该把别路的七行一起往前拖。
    var inboxOffset by remember { mutableIntStateOf(0) }
    var summary by remember { mutableStateOf(InboxSummary.EMPTY) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var followers by remember { mutableStateOf<List<SocialPerson>>(emptyList()) }
    var followersLoading by remember { mutableStateOf(false) }
    var followersError by remember { mutableStateOf<String?>(null) }
    // 正在写关注关系的那一行：写的时候按钮先别理手指，免得一次点击写两遍。
    var followBusy by remember { mutableStateOf("") }

    val inboxListState = rememberLazyListState()
    val followersListState = rememberLazyListState()

    // 首屏：一次计数 + 一次消息页。计数是为了拿到「最新一条」和「从哪个瞬间开始算」，
    // 没有它，红点就只能靠本页自己猜。
    LaunchedEffect(owner, token) {
        loading = true
        loadError = null
        inboxOffset = 0
        summary = repository.inboxSummary(owner, token, seenAt) ?: InboxSummary.EMPTY
        inbox = repository.inboxPage(owner, token, offset = 0, perSource = INTERACTION_PER_SOURCE)
        inboxOffset = INTERACTION_PER_SOURCE
        // 后端没这个函数、或者手机没连上，都不能读成「没人理你」：把原因摆出来。
        loadError = repository.lastError
        loading = false
    }

    // 滚到底才问下一页：用 [loadingMore] 挡一下，免得一次快滑排出十个一样的请求；按 key 合并，
    // 免得正好落在两页之间的那一行被画两次。
    val loadMore: () -> Unit = {
        if (!loadingMore) {
            scope.launch {
                loadingMore = true
                val next = repository.inboxPage(
                    owner = owner,
                    token = token,
                    offset = inboxOffset,
                    perSource = INTERACTION_PER_SOURCE,
                )
                inbox = Page(
                    items = (inbox.items + next.items)
                        .distinctBy { "${it.kind}-${it.id}-${it.createdAt}" },
                    hasMore = next.hasMore,
                )
                inboxOffset += INTERACTION_PER_SOURCE
                loadError = repository.lastError
                loadingMore = false
            }
        }
    }

    // 消息这一栏被真正看见了，才算读过：交回去的是「最新的一条」和「后端计数时的那个瞬间」，
    // 后者才是游标，本页记住看了几行没有意义。
    LaunchedEffect(tab, inbox, summary) {
        if (tab == 0 && inbox.items.isNotEmpty()) {
            onInboxSeen(
                maxOf(newestInboxCommentId(inbox.items), summary.newestCommentId),
                summary.checkedAt,
            )
        }
    }

    // 关注列表：每次切到这一栏都重新问一次，别人刚关注你那一眼就该在。
    LaunchedEffect(tab, owner) {
        if (tab == 1) {
            followersLoading = true
            followersError = null
            followers = social.followersOf(owner)
            followersError = interactionErrorOf(social.lastError, social.lastFailureWasNetwork)
            followersLoading = false
        }
    }

    /** 打开一行：方案评论落到方案页的那条评论上，应用评论落到应用页的那条评论上。 */
    val open: (InboxItem) -> Unit = { item ->
        val commentId = item.commentIdOrZero
        if (item.targetKind == COMMENT_KIND_APP) {
            if (item.target.isNotEmpty()) onOpenAppComment(item.target, commentId)
        } else {
            item.planIdOrNull()?.let { planId -> onOpenPlanComment(planId, commentId) }
        }
    }

    /** 关注 / 取关。写成功才改本行，写失败把原因摆到列表顶上。 */
    val toggleFollow: (SocialPerson) -> Unit = { person ->
        if (followBusy.isEmpty()) {
            scope.launch {
                followBusy = person.id
                val following = social.setFollow(owner, person.id, !person.iFollow)
                val reason = interactionErrorOf(social.lastError, social.lastFailureWasNetwork)
                if (reason == null) {
                    followersError = null
                    followers = followers.map { row ->
                        if (row.id == person.id) row.copy(iFollow = following) else row
                    }
                } else {
                    followersError = reason
                }
                followBusy = ""
            }
        }
    }

    Scaffold(
        topBar = {
            InteractionsTopBar(
                tab = tab,
                onSelectTab = { tab = it },
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 下一页只在这一栏、还有页、而且没有正在加载的时候才问。
            InteractionEndlessScroll(
                state = inboxListState,
                enabled = tab == 0 && inbox.hasMore && !loadingMore,
                onEnd = loadMore,
            )
            if (tab == 0) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 「后端没回答」和「真的没人理你」长得一样，所以原因要摆在列表顶上。
                    loadError?.let { reason -> InteractionErrorLine(reason) }
                    when {
                        loading -> Text("…", fontSize = 12.sp, modifier = Modifier.padding(14.dp))

                        inbox.items.isEmpty() -> InteractionEmptyNotice(
                            title = "还没有新的互动。",
                            hint = "有人回复你、评论你上传的方案，或者赞了你的评论和方案时，会出现在这里。",
                        )

                        else -> LazyColumn(
                            state = inboxListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(
                                items = inbox.items,
                                key = { row -> "${row.kind}-${row.id}-${row.createdAt}" },
                            ) { row ->
                                InteractionRow(
                                    item = row,
                                    onOpen = { open(row) },
                                    onOpenProfile = onOpenProfile,
                                )
                            }
                            if (loadingMore) {
                                item(key = "interactions-more") { InteractionLoadingMore() }
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    followersError?.let { reason -> InteractionErrorLine(reason) }
                    when {
                        followersLoading && followers.isEmpty() ->
                            Text("…", fontSize = 12.sp, modifier = Modifier.padding(14.dp))

                        followers.isEmpty() -> InteractionEmptyNotice(
                            title = "还没有人关注你",
                            hint = "别人关注你之后，会出现在这里，你可以从这里回关。",
                        )

                        else -> LazyColumn(
                            state = followersListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(followers) { person ->
                                FollowerRow(
                                    person = person,
                                    busy = followBusy == person.id,
                                    onToggleFollow = { toggleFollow(person) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顶栏：左边是回去的箭头，中间偏左是当前这一栏的名字（后面那个小箭头是装饰，不弹菜单），
 * 右边是去另一栏的文字入口。
 *
 * 两栏做成「标题 + 入口」而不是分段控件，是抖音那一套：同一时刻只有一件事在标题上，
 * 另一件事缩成一行字，页面因此不会因为页签宽度不一而左右跳。
 */
@Composable
private fun InteractionsTopBar(
    tab: Int,
    onSelectTab: (Int) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val title = if (tab == 0) "互动消息" else "新关注我的"
    val other = if (tab == 0) "新关注我的" else "互动消息"
    val otherTab = if (tab == 0) 1 else 0

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
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InteractionTabText(label = title, active = true, onClick = null)
                Spacer(Modifier.width(1.dp))
                // 装饰：说明这一栏是个可以换的地方，但点它不弹任何菜单。
                FlGlyphIcon(
                    glyph = FlGlyphs.chevronDown,
                    size = 13.dp,
                    tint = colors.onSurfaceVariant,
                )
            }
            InteractionTabText(
                label = other,
                active = false,
                onClick = { onSelectTab(otherTab) },
            )
        }
    }
}

/**
 * 顶栏上的一栏文字。选中的那一栏是标题：大一号、加粗、用正文色；另一边是入口：小一号、灰。
 */
@Composable
private fun InteractionTabText(label: String, active: Boolean, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = label,
        style = if (active) FlText.headline else FlText.subheadline,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        color = if (active) colors.onSurface else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/**
 * 一行互动：谁 + 做了什么 + 说了什么 + 什么时候，右下角给一个直接去回复的入口。
 *
 * 整行都能点，打开的地方和「回复评论」一样：一行新闻的价值在于跳到那条评论上，
 * 而不是在于读这三十个字。
 */
@Composable
private fun InteractionRow(
    item: InboxItem,
    onOpen: () -> Unit,
    onOpenProfile: (owner: String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Card.colorOf(colors))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // 头像是一块自己的入口：点开那个人的主页。行本身的点击仍然是开那条消息指向的页。
            InteractionAvatar(
                name = item.actor,
                size = 40.dp,
                modifier = Modifier.clickable(
                    enabled = item.actorId.isNotEmpty(),
                    onClickLabel = "看主页",
                ) { onOpenProfile(item.actorId) },
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 名字和动作短语一起占左边这一块，时间被 weight 挤到最右。
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.actor.ifBlank { "有人" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = interactionActionOf(item),
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = interactionStampOf(item.createdAt),
                        fontSize = 9.sp,
                        color = colors.outline,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(3.dp))
                // 正文只留两行：这是列表，读得下去的那两行就够决定点不点开了。
                Text(
                    text = item.body,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "回复评论",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onOpen)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** 谁刚关注了我：头像、名字、时间（有就原样，没有就说「最近」），右边是回关 / 已关注。 */
@Composable
private fun FollowerRow(person: SocialPerson, busy: Boolean, onToggleFollow: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Card.colorOf(colors))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InteractionAvatar(name = person.label, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = person.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (person.since.isBlank()) {
                    "最近关注了你"
                } else {
                    // 后端给的是 `2026-11-22T13:09:33.5Z`：只留日期，整串贴上去读不懂也太长。
                    val day = person.since.substringBefore('T')
                    "$day 关注了你"
                },
                fontSize = 10.sp,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        InteractionFollowButton(followed = person.iFollow, enabled = !busy, onClick = onToggleFollow)
        Spacer(Modifier.width(2.dp))
        FlGlyphIcon(glyph = FlGlyphs.chevronForward, size = 13.dp, tint = colors.outline)
    }
}

/** 回关按钮：没关注是红底白字的「回关」，关注过是灰底的「已关注」，再点一次就是取关。 */
@Composable
private fun InteractionFollowButton(followed: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (followed) colors.surfaceContainerHighest else UnreadRed)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (followed) "已关注" else "回关",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (followed) colors.onSurfaceVariant else Color.White,
            maxLines = 1,
        )
    }
}

/** 首字的圆头像：名字是别人给的，这里只取第一个字符，取不到就画一个问号。 */
@Composable
private fun InteractionAvatar(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).ifEmpty { "?" },
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            maxLines = 1,
        )
    }
}

/** 一行说明「没拿到列表」的灰字，摆在列表顶上。 */
@Composable
private fun InteractionErrorLine(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

/** 空态：一句说清楚这里会长出什么。 */
@Composable
private fun InteractionEmptyNotice(title: String, hint: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
        Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = hint,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 列表末尾那一行转圈：还有下一页在后台读着。 */
@Composable
private fun InteractionLoadingMore() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
    }
}

/**
 * 列表滚到最后一行时才叫一次 [onEnd]，也就是「下一页值得问了」的那一刻。
 *
 * [enabled] 是调用方的「还有下一页、而且没有正在读」：已经拿全了的列表不该在每次重组时再问一次。
 */
@Composable
private fun InteractionEndlessScroll(state: LazyListState, enabled: Boolean, onEnd: () -> Unit) {
    val nearEnd by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(nearEnd, enabled) {
        if (enabled && nearEnd) onEnd()
    }
}

/** 一行互动的动词：做了什么，落在什么上。 */
private fun interactionActionOf(item: InboxItem): String = when (item.kind) {
    InboxKind.REPLY -> "回复了你"
    InboxKind.PLAN_COMMENT -> "评论了你的方案"
    InboxKind.COMMENT_HEART ->
        if (item.likes > 1) "等 ${item.likes} 人赞了你的评论" else "赞了你的评论"
    InboxKind.PLAN_HEART ->
        if (item.likes > 1) "等 ${item.likes} 人认同了你的方案" else "认同了你的方案"
    else -> "有新的互动"
}

/**
 * 时间只用最朴素的那种写法：后端给的是 `2026-11-22T19:20:33Z`，这里截到分钟，
 * 把中间那个 `T` 换成空格。不引时间库，也不换算时区。
 */
private fun interactionStampOf(createdAt: String): String =
    createdAt.take(16).replace('T', ' ')

/** 列表读空的两种原因分开说：没连上是网络的事，被拒绝是后端的事。 */
private fun interactionErrorOf(reason: String?, network: Boolean): String? = when {
    network -> "没连上后端，检查代理或网络后再试。"
    !reason.isNullOrBlank() -> "后端没有返回列表：$reason"
    else -> null
}
