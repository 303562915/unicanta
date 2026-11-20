package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import io.github.samolego.canta.data.community.COMMENT_KIND_APP
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.INBOX_PAGE_SIZE
import io.github.samolego.canta.data.community.InboxItem
import io.github.samolego.canta.data.community.InboxKind
import io.github.samolego.canta.data.community.InboxSummary
import io.github.samolego.canta.data.community.MyComment
import io.github.samolego.canta.data.community.Page
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.newestInboxCommentId
import io.github.samolego.canta.ui.component.EmotionText
import io.github.samolego.canta.ui.component.LetterAvatar
import io.github.samolego.canta.ui.component.relativeStamp
import io.github.samolego.canta.ui.component.MyCommentRow
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.UnreadDot
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import io.github.samolego.canta.util.DeviceProfile
import kotlinx.coroutines.launch

/**
 * Everything this install wrote, and everything other people did about it.
 *
 * Two kinds of page in one, because they answer one question with the same data: *what did I say,
 * on which plan or app?* — each row opens that page, the bin next to it deletes the comment — and
 * *did anything happen to it?* — the inbox, which is one stream of four kinds of news: somebody
 * answered me, somebody wrote under the plan I uploaded, somebody hearted my comment, somebody
 * endorsed my plan.
 *
 * The inbox is paged twice over: the server hands out one page per source per scroll, and the page
 * asks for the next one only when the list reaches its end. Nothing here downloads a history to
 * look at the top of it: the first screen is [INBOX_PAGE_SIZE] rows whatever the person's history
 * is, and [perSource] rows come from each source on every further scroll.
 *
 * Nothing here is stored locally: the page asks the backend for what is signed with this install's
 * own name or number, which is the identity the app already comments under.
 */
@Composable
fun MyCommentsScreen(
    repository: PlanRepository,
    /** How this install signs its comments: its display name, or its identity code. */
    author: String,
    /** This install's identity number; the backend needs it (with the token) to delete. */
    owner: String = "",
    /** This install's secret. */
    token: String = "",
    onOpenPlan: (Long) -> Unit = {},
    onOpenApp: (String) -> Unit = {},
    /** This phone: what "my plans" falls back to when the deployment has no `plans_by_owner`. */
    profile: DeviceProfile? = null,
    onNavigateBack: () -> Unit,
    /** Whether the page opens on the inbox: it is usually opened *because* of the news. */
    startOnInbox: Boolean = false,
    /** What this install had already opened when it came in, so the new rows can be marked. */
    unreadSince: Long = 0L,
    /** Reports the newest news once the inbox has been opened: that clears the red dot. */
    onInboxSeen: (Long) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // 0 = what I wrote, 1 = the inbox, 2 = the plans I uploaded.
    var tab by remember { mutableIntStateOf(if (startOnInbox) 1 else 0) }
    var mine by remember { mutableStateOf<Page<MyComment>>(Page.EMPTY) }
    var inbox by remember { mutableStateOf<Page<InboxItem>>(Page.EMPTY) }
    // How far each source of the inbox has been read. The feed takes [perSource] rows *per source*
    // from one offset, so the offset advances by that window, not by the number of rows that came
    // back: a source that ran out after three rows must not pull the other three back by seven.
    var inboxOffset by remember { mutableIntStateOf(0) }
    var summary by remember { mutableStateOf(InboxSummary.EMPTY) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var reloads by remember { mutableIntStateOf(0) }
    var myPlans by remember { mutableStateOf<List<CommunityPlan>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<MyComment?>(null) }
    // The plan whose delete button was tapped: deleting a plan takes its votes and comments with it.
    var pendingDeletePlan by remember { mutableStateOf<CommunityPlan?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    /** The rows one inbox request takes from each of its four sources. */
    val perSource = INBOX_PAGE_SIZE / 2

    // One page of each list, and nothing else: the first screen is one request per list, and the
    // inbox is one request for its summary plus one for its first page. A phone that has to parse
    // two years of history before it can draw is a phone that looks frozen.
    LaunchedEffect(author, owner, token, reloads) {
        loading = true
        loadError = null
        inboxOffset = 0
        val firstMine = repository.myCommentsPage(owner, token, offset = 0)
        val firstInbox = repository.inboxPage(owner, token, offset = 0, perSource = perSource)
        summary = repository.inboxSummary(owner, token) ?: InboxSummary.EMPTY
        // Management asks for the owner's plans by identity, not by device: an OS update must not
        // hide the plan someone came here to delete. An older deployment falls back to the list of
        // this phone.
        myPlans = repository.plansByOwner(owner).ifEmpty {
            repository.plansFor(profile).filter { it.owner == owner }
        }
        mine = firstMine
        inbox = firstInbox
        inboxOffset = perSource
        // A backend that is missing the function, or a phone that is offline, must not read as
        // "you never commented": say which one it was.
        loadError = repository.lastError
        loading = false
    }

    // Asking for the next page, when the list reaches its end and only then. Guarded by
    // [loadingMore] so a fast fling cannot queue ten identical requests, and merged by key so a row
    // that slid between two pages — somebody wrote while the list was open — is drawn once.
    val loadMore: (Boolean) -> Unit = { isInbox ->
        if (!loadingMore) {
            scope.launch {
                loadingMore = true
                if (isInbox) {
                    val next = repository.inboxPage(owner, token, offset = inboxOffset, perSource = perSource)
                    inbox = Page(
                        items = (inbox.items + next.items).distinctBy { "${it.kind}-${it.id}-${it.createdAt}" },
                        hasMore = next.hasMore,
                    )
                    inboxOffset += perSource
                } else {
                    val next = repository.myCommentsPage(owner, token, offset = mine.items.size)
                    mine = Page(
                        items = (mine.items + next.items).distinctBy { it.id },
                        hasMore = next.hasMore,
                    )
                }
                loadingMore = false
            }
        }
    }

    // Having the inbox on screen is what "read" means: the dot is cleared the moment the tab that
    // shows it is the one being looked at, not when the page is left.
    LaunchedEffect(tab, inbox) {
        if (tab == 1 && inbox.items.isNotEmpty()) {
            onInboxSeen(maxOf(newestInboxCommentId(inbox.items), summary.newestCommentId))
        }
    }

    val open: (String, String) -> Unit = { kind, target ->
        val plan = if (kind == COMMENT_KIND_APP) null else target.toLongOrNull()
        val pkg = if (kind == COMMENT_KIND_APP) target else null
        when {
            plan != null -> onOpenPlan(plan)
            pkg != null -> onOpenApp(pkg)
        }
    }

    pendingDelete?.let { comment ->
        CantaDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除评论",
            buttons = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        val ok = repository.deleteComment(comment.id, owner, token)
                        notice = if (ok) null else "删不掉：这条评论不是本机写的，或者后端没通。"
                        reloads++
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
        ) {
            Text(
                text = "「${comment.body.take(40)}」会被删除，下面别人给的回复一起去掉。",
                fontSize = 12.sp,
            )
        }
    }

    // Deleting a plan is deleting the whole thing: its packages, its votes and every comment under
    // it. The comment dialog above cannot say that, so the plan gets its own.
    pendingDeletePlan?.let { plan ->
        CantaDialog(
            onDismissRequest = { pendingDeletePlan = null },
            title = "删除方案",
            buttons = {
                TextButton(onClick = { pendingDeletePlan = null }) { Text("取消") }
                TextButton(onClick = {
                    pendingDeletePlan = null
                    scope.launch {
                        val ok = repository.delete(plan.id, owner, token)
                        notice = if (ok) "已删除「${plan.title}」" else "删不掉：这个方案不是本机上传的，或者后端没通。"
                        if (ok) myPlans = myPlans.filterNot { it.id == plan.id }
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
        ) {
            Text(
                text = "「${plan.title}」和它下面的 ${plan.comments} 条评论会一起消失，别人的点赞也不算数了。",
                fontSize = 12.sp,
            )
        }
    }

    Scaffold(
        topBar = {
            Column {
                // Only two things belong above the list: the way out, the way to ask the backend
                // again, and the switch between the three lists. The name and the two counts used to
                // sit in a coloured card of their own, repeating what the segments already say —
                // the page is about one install, so naming it a second time said nothing new.
                ScreenTopBar(
                    onNavigateBack = onNavigateBack,
                    title = "我的评论",
                    actions = {
                        FlBarButton(
                            glyph = FlGlyphs.refresh,
                            contentDescription = "刷新",
                            onClick = { reloads++ },
                            iconSize = 20.dp,
                            filled = false,
                        )
                    },
                )
                FlSegmentedControl(
                    segments = listOf(
                        FlSegment("mine", "我写的 · ${mine.items.size}"),
                        // The inbox says how much of it is new, which is the number the red dot on
                        // the app list is about: opening this tab is what clears that dot.
                        FlSegment("inbox", inboxLabel(summary)),
                        FlSegment("plans", "我的方案 · ${myPlans.size}"),
                    ),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            notice?.let { line ->
                Text(
                    text = line,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
            val inboxListState = rememberLazyListState()
            val mineListState = rememberLazyListState()
            // The next page is requested when the last row is on screen, not before: a list of ten
            // rows that asks for more the moment it is drawn would load the history it was built
            // to avoid.
            EndlessScroll(inboxListState, enabled = tab == 1 && inbox.hasMore && !loadingMore) {
                loadMore(true)
            }
            EndlessScroll(mineListState, enabled = tab == 0 && mine.hasMore && !loadingMore) {
                loadMore(false)
            }
            when {
                loading -> Text("…", fontSize = 12.sp, modifier = Modifier.padding(14.dp))

                // Managing what this install published: the list is the identity's, not the phone's,
                // so a plan uploaded before an OS update is still here to be deleted.
                tab == 2 -> if (myPlans.isEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                        Text("你还没有上传过方案。", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "在安全页的悬浮按钮里上传一份，这里就能管理和删除。",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        loadError?.let { reason ->
                            Text(
                                text = "后端没有返回列表：$reason",
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(myPlans, key = { plan -> "plan-${plan.id}" }) { plan ->
                            MyPlanRow(
                                plan = plan,
                                onOpen = { onOpenPlan(plan.id) },
                                onDelete = { pendingDeletePlan = plan },
                            )
                        }
                    }
                }

                // The inbox: four kinds of news in one stream, newest first.
                tab == 1 -> if (inbox.items.isEmpty()) {
                    EmptyNotice(
                        title = "还没有人理你的评论和方案。",
                        hint = "有人回复你、赞了你的评论，或者赞了你上传的方案时，会出现在这里。",
                        loadError = loadError,
                        warning = null,
                    )
                } else {
                    LazyColumn(
                        state = inboxListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(inbox.items, key = { row -> "${row.kind}-${row.id}-${row.createdAt}" }) { row ->
                            InboxRow(
                                item = row,
                                isNew = row.commentIdOrZero > unreadSince,
                                onOpen = { open(row.targetKind, row.target) },
                            )
                        }
                        if (loadingMore) {
                            item(key = "inbox-more") { LoadingMore() }
                        }
                    }
                }

                mine.items.isEmpty() -> EmptyNotice(
                    title = "你还没有评论过任何方案或应用。",
                    hint = "在方案详情或应用详情里写一条，这里就会出现。",
                    loadError = loadError,
                    warning = if (owner.isEmpty()) {
                        "这台设备还没有拿到编号，评论会记在令牌下；点右上角刷新后如果仍是 0，把上面那行发给我。"
                    } else {
                        null
                    },
                )

                else -> LazyColumn(
                    state = mineListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(mine.items, key = { comment -> "comment-${comment.id}" }) { comment ->
                        MyCommentRow(
                            author = author,
                            body = comment.body,
                            targetLabel = targetLabel(comment),
                            createdAt = comment.createdAt,
                            replies = comment.replies,
                            likes = comment.likes,
                            onOpen = { open(comment.kind, comment.target) },
                            onDelete = { pendingDelete = comment },
                        )
                    }
                    if (loadingMore) {
                        item(key = "mine-more") { LoadingMore() }
                    }
                }
            }
        }
    }
}

/** "消息 · 7" while something is waiting, "消息" when nothing is: the dot already says it. */
private fun inboxLabel(summary: InboxSummary): String =
    if (summary.total > 0) "消息 · ${summary.total}" else "消息"

/**
 * Calls [onEnd] once the list's last row is visible, which is the moment the next page is worth
 * asking for.
 *
 * [enabled] is the caller's "there is another page and nothing is being loaded": a list that has
 * everything, or is already waiting for it, must not ask again on every recomposition.
 */
@Composable
private fun EndlessScroll(state: LazyListState, enabled: Boolean, onEnd: () -> Unit) {
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

/** One line under a list that has more rows on the server than it is showing. */
@Composable
private fun LoadingMore() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
    }
}

/**
 * What an empty list says: a title, what would put something in it, and — when the request failed
 * — the reason, because "0" and "the call broke" look identical otherwise.
 */
@Composable
private fun EmptyNotice(title: String, hint: String, loadError: String?, warning: String?) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = hint,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        loadError?.let { reason ->
            Text(
                text = "后端没有返回列表：$reason",
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        warning?.let { line ->
            Text(
                text = line,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * One plan this install uploaded: tapping opens it, the bin deletes it.
 *
 * The row says what deleting costs — how many packages it publishes, how many people endorsed it,
 * whether it ever passed a reboot — so the confirmation is not the first time that is visible.
 */
@Composable
private fun MyPlanRow(plan: CommunityPlan, onOpen: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plan.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append("${plan.packages.size} 包 · ${plan.votes} 赞")
                    append(if (plan.isTest) " · 测试" else " · 重启验证")
                    append(" · ${plan.rebootedAt.take(10)}")
                },
                fontSize = 9.sp,
                lineHeight = 12.sp,
                color = colors.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除这个方案",
                modifier = Modifier.size(16.dp),
                tint = colors.error,
            )
        }
    }
}

/** "方案 · 标题" or "应用 · com.example.app": where a comment of mine lives. */
private fun targetLabel(comment: MyComment): String {
    val kind = if (comment.kind == COMMENT_KIND_APP) "应用" else "方案"
    val name = comment.title.ifEmpty { comment.target }
    return "$kind · $name"
}

/**
 * One line of the inbox, read as a sentence first and a card second: *who* did *what* to *which*
 * thing of mine, and when.
 *
 * The four kinds share one shape on purpose — an avatar, the actor's name, a verb, the thing it is
 * about, and the quote — because that is how a real inbox reads: hearts and replies are different
 * events, not different pages. A heart also carries its tally, which is the part a single-tap
 * notification cannot say: "张三 等 3 人赞了你的评论" is one line, three people.
 */
@Composable
private fun InboxRow(item: InboxItem, isNew: Boolean, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterAvatar(name = item.actor, size = 22.dp, background = colors.secondaryContainer)
            Spacer(Modifier.width(7.dp))
            Text(
                text = item.actor.ifBlank { "某人" },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (isNew) {
                Spacer(Modifier.width(4.dp))
                UnreadDot(size = 6.dp)
            }
            Spacer(Modifier.width(6.dp))
            Text(text = relativeStamp(item.createdAt), fontSize = 9.sp, color = colors.outline)
            Spacer(Modifier.weight(1f))
            // The kind of event, as an icon: a heart reads as a heart at a glance, which is the
            // whole reason the two heart kinds exist as separate rows.
            if (item.kind.isHeart) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "赞",
                    modifier = Modifier.size(11.dp),
                    tint = colors.error,
                )
            } else {
                Text(
                    text = if (item.targetKind == COMMENT_KIND_APP) "应用" else "方案",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.primary,
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        // The verb, said the way the notification would say it: "张三 等 3 人赞了你的评论". The name
        // is already drawn above, so the row reads name → what happened → about which thing.
        Text(
            text = verbOf(item),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (item.kind.isHeart) colors.error else colors.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        // What it is about: the plan's title, or the app's package name when nobody resolved one.
        Text(
            text = (if (item.targetKind == COMMENT_KIND_APP) "应用 · " else "方案 · ") +
                (item.title.ifEmpty { item.target }),
            fontSize = 9.sp,
            color = colors.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        // For a reply or a comment under my plan: what was written. For a heart: the comment or
        // plan that was hearted — "somebody hearted this thing of yours" needs to say which thing.
        EmotionText(body = item.body, fontSize = 12, lineHeight = 16, maxLines = 3)
        if (item.parentBody.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            // Quoting what was answered: without it a reply reads as a remark about nothing.
            Text(
                text = "回复了你的：「${item.parentBody.take(50)}」",
                fontSize = 9.sp,
                lineHeight = 12.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * The verb on an inbox row: what the actor did, and — for a heart — how many people did it.
 *
 * "赞了你的评论" against "赞了你的方案" matters: a plan is endorsed by the whole community and the
 * uploader wants to know their work was picked up, while a comment is one voice answering another.
 * The tally is said the way people say it ("张三 等 3 人赞了…"), with the count only added when it
 * is bigger than the one person already named.
 */
private fun verbOf(item: InboxItem): String = when (item.kind) {
    InboxKind.REPLY -> "回复了你"
    InboxKind.PLAN_COMMENT -> "评论了你的方案"

    // "等 3 人" reads after the name already drawn beside it: "张三 等 3 人赞了你的评论" — the actor
    // is the newest one, and the count includes them, which is why it is only said above 1.
    InboxKind.COMMENT_HEART ->
        if (item.likes > 1) "等 ${item.likes} 人赞了你的评论" else "赞了你的评论"

    InboxKind.PLAN_HEART ->
        if (item.likes > 1) "等 ${item.likes} 人赞了你的方案" else "赞了你的方案"
}
