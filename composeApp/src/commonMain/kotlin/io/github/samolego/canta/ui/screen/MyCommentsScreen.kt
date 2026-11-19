package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.style.TextOverflow
import io.github.samolego.canta.data.community.COMMENT_KIND_APP
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.data.community.MyComment
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.newestReplyId
import io.github.samolego.canta.ui.component.LetterAvatar
import io.github.samolego.canta.ui.component.MyCommentRow
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.UnreadDot
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import kotlinx.coroutines.launch

/**
 * Everything this install wrote, and everything other people wrote back.
 *
 * Two lists on one page, because they answer two questions with the same data: *what did I say, on
 * which plan or app?* — each row opens that page, and the bin next to it deletes the comment — and
 * *did anyone answer me?* — each reply shows who answered and what they answered, and opens the
 * same page.
 *
 * Nothing here is stored locally: the page asks the backend for the comments signed with this
 * install's own name or number, which is the identity the app already comments under.
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
    /** Whether the page opens on the answers: the inbox is usually opened *because* of them. */
    startOnReplies: Boolean = false,
    /** What this install had already opened when it came in, so the new answers can be marked. */
    unreadSince: Long = 0L,
    /** Reports the newest answer once the list of them has been opened: that clears the red dot. */
    onRepliesSeen: (Long) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(if (startOnReplies) 1 else 0) }
    var mine by remember { mutableStateOf<List<MyComment>>(emptyList()) }
    var replies by remember { mutableStateOf<List<MyComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloads by remember { mutableIntStateOf(0) }
    var myPlans by remember { mutableStateOf<List<CommunityPlan>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<MyComment?>(null) }
    // The plan whose delete button was tapped: deleting a plan takes its votes and comments with it.
    var pendingDeletePlan by remember { mutableStateOf<CommunityPlan?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(author, owner, token, reloads) {
        loading = true
        loadError = null
        mine = repository.myComments(owner, token)
        replies = repository.repliesToMe(owner, token)
        // Management asks for the owner's plans by identity, not by device: an OS update must not
        // hide the plan someone came here to delete. An older deployment falls back to the list of
        // this phone.
        myPlans = repository.plansByOwner(owner).ifEmpty {
            repository.plansFor(profile).filter { it.owner == owner }
        }
        // A backend that is missing the function, or a phone that is offline, must not read as
        // "you never commented": say which one it was.
        loadError = repository.lastError
        loading = false
    }

    // Having the answers on screen is what "read" means: the dot is cleared the moment the tab
    // that shows them is the one being looked at, not when the page is left.
    LaunchedEffect(tab, replies) {
        if (tab == 1 && replies.isNotEmpty()) onRepliesSeen(newestReplyId(replies))
    }

    val open: (MyComment) -> Unit = { comment ->
        val plan = comment.planIdOrNull()
        val pkg = comment.packageOrNull()
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
                // again, and the switch between the two lists. The name and the two counts used to
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
                        FlSegment("mine", "我写的 · ${mine.size}"),
                        FlSegment("replies", "回复我的 · ${replies.size}"),
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
            val rows = if (tab == 0) mine else replies
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

                rows.isEmpty() -> Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Text(
                        text = if (tab == 0) "你还没有评论过任何方案或应用。" else "还没有人回复你的评论。",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (tab == 0) {
                            "在方案详情或应用详情里写一条，这里就会出现。"
                        } else {
                            "有人在你的评论下面回复时，会出现在这里。"
                        },
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // "0" and "the request failed" look the same otherwise, and the second one is a
                    // bug report waiting to happen: name the reason the list is empty.
                    loadError?.let { reason ->
                        Text(
                            text = "后端没有返回列表：$reason",
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    owner.takeIf { it.isEmpty() }?.let {
                        Text(
                            text = "这台设备还没有拿到编号，评论会记在令牌下；点右上角刷新后如果仍是 0，把上面那行发给我。",
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(rows, key = { comment -> "comment-${comment.id}" }) { comment ->
                        if (tab == 0) {
                            MyCommentRow(
                                author = author,
                                body = comment.body,
                                targetLabel = targetLabel(comment),
                                createdAt = comment.createdAt,
                                replies = comment.replies,
                                onOpen = { open(comment) },
                                onDelete = { pendingDelete = comment },
                            )
                        } else {
                            ReplyRow(
                                comment = comment,
                                isNew = comment.id > unreadSince,
                                onOpen = { open(comment) },
                            )
                        }
                    }
                }
            }
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
 * One answer somebody left under a comment of mine: who, what they answered, what they said.
 *
 * [isNew] marks the ones that arrived since the last time this page was opened with the red dot,
 * so a list of ten answers still says which two are the news.
 */
@Composable
private fun ReplyRow(comment: MyComment, isNew: Boolean, onOpen: () -> Unit) {
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
            LetterAvatar(name = comment.author, size = 22.dp, background = colors.secondaryContainer)
            Spacer(Modifier.width(7.dp))
            Text(
                text = comment.author,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (isNew) {
                Spacer(Modifier.width(4.dp))
                UnreadDot(size = 6.dp)
            }
            Spacer(Modifier.width(6.dp))
            Text(text = comment.createdAt.take(10), fontSize = 9.sp, color = colors.outline)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (comment.kind == COMMENT_KIND_APP) "应用" else "方案",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = colors.primary,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(text = comment.body, fontSize = 12.sp, lineHeight = 16.sp)
        if (comment.parentBody.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            // Quoting what was answered: without it a reply reads as a remark about nothing.
            Text(
                text = "回复了你的：「${comment.parentBody.take(50)}」",
                fontSize = 9.sp,
                lineHeight = 12.sp,
                color = colors.onSurfaceVariant,
            )
        }
        Text(
            text = "查看 · " + (comment.title.ifEmpty { comment.target }),
            fontSize = 9.sp,
            color = colors.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
