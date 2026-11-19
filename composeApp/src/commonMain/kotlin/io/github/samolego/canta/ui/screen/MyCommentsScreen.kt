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
import io.github.samolego.canta.data.community.COMMENT_KIND_APP
import io.github.samolego.canta.data.community.MyComment
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.ui.component.LetterAvatar
import io.github.samolego.canta.ui.component.MyCommentRow
import io.github.samolego.canta.ui.component.avatarColorOf
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.CantaDialog
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
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var mine by remember { mutableStateOf<List<MyComment>>(emptyList()) }
    var replies by remember { mutableStateOf<List<MyComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloads by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<MyComment?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(author, owner, token, reloads) {
        loading = true
        loadError = null
        mine = repository.myComments(owner, token)
        replies = repository.repliesToMe(owner, token)
        // A backend that is missing the function, or a phone that is offline, must not read as
        // "you never commented": say which one it was.
        loadError = repository.lastError
        loading = false
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

    Scaffold(
        topBar = {
            Column {
                ScreenTopBar(onNavigateBack = onNavigateBack, title = "我的评论")
                // Who the page is about, and how many of the two things there are.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LetterAvatar(
                        name = author,
                        size = 26.dp,
                        background = avatarColorOf(author),
                        letterColor = androidx.compose.ui.graphics.Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = author.ifEmpty { "—" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                        )
                        Text(
                            text = "${mine.size} 条我写的 · ${replies.size} 条回复我的",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                    }
                    Text(
                        text = "刷新",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { reloads++ }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                FlSegmentedControl(
                    segments = listOf(
                        FlSegment("mine", "我写的 · ${mine.size}"),
                        FlSegment("replies", "回复我的 · ${replies.size}"),
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
                            text = "这台设备还没有拿到编号，评论会记在令牌下；下拉刷新后如果仍是 0，把上面那行发给我。",
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
                            ReplyRow(comment = comment, onOpen = { open(comment) })
                        }
                    }
                }
            }
        }
    }
}

/** "方案 · 标题" or "应用 · com.example.app": where a comment of mine lives. */
private fun targetLabel(comment: MyComment): String {
    val kind = if (comment.kind == COMMENT_KIND_APP) "应用" else "方案"
    val name = comment.title.ifEmpty { comment.target }
    return "$kind · $name"
}

/** One answer somebody left under a comment of mine: who, what they answered, what they said. */
@Composable
private fun ReplyRow(comment: MyComment, onOpen: () -> Unit) {
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
