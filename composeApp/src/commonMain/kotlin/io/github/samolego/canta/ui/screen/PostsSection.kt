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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.community.CommunityPost
import io.github.samolego.canta.data.community.PostComment
import io.github.samolego.canta.data.community.PostStore
import io.github.samolego.canta.data.community.PostThread
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.ui.component.relativeStamp
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import kotlinx.coroutines.launch

/** 一页拉多少条。后端把 `p_limit` 夹在 1..50，这里取一屏够看的数。 */
private const val POSTS_PAGE = 20

/** 置顶标记的颜色：与「认同」同一个绿，页面上一眼是"被挑出来的那条"。 */
private val PostPinned = Color(0xFF2FA84F)

/**
 * 推荐页里的「社区帖子」：发帖、看帖、回帖、点赞。
 *
 * 排布就是阅读顺序：
 *
 *  1. 顶部一行「发一帖」入口 —— 点开是标题 + 正文的表单。标题必填，正文可以留空。
 *  2. 帖子列表 —— 置顶在前、其余按时间倒序；每条显示置顶标记、标题、摘要、作者、时间、
 *     回帖数、赞数。点一条打开详情。
 *  3. 详情对话框 —— 正文全文、点赞按钮、回帖列表（后端最多给 200 条，按时间正序）、底下的
 *     回复框；自己发的帖子在按钮行里多一个「删除」，点了还要再确认一次。
 *
 * 身份：写操作只认后端从 token 认出来的身份（见 `PostStore`）。[authorId] 为空时这一块只读：
 * 「发一帖」的入口还在，但点开会说"还没认出身份"，而不是让后端悄悄记在一串自报号码上。
 *
 * @param store 帖子数据层；null 时这一块只显示一句说明，不装作有数据。
 * @param platform 用来提示（[CantaPlatform.showMessage]）。
 * @param author 署名：发帖、回帖时一起发上去，只当名字显示，不当身份。
 * @param authorId 身份码：只有它等于某条帖子的 `author_id` 时，页面才给那条帖子显示删除。
 */
@Composable
internal fun PostsSection(
    store: PostStore?,
    platform: CantaPlatform,
    author: String,
    authorId: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val canWrite = authorId.isNotBlank()

    var posts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** 发帖、回帖、点赞、删帖之后 +1：只有它变了才重新拉。 */
    var reloadKey by remember { mutableIntStateOf(0) }
    /** 正在进行的那一件事：`like:7` / `reply:7` / `delete:7`。 */
    var busyKey by remember { mutableStateOf<String?>(null) }

    var composerOpen by remember { mutableStateOf(false) }
    var composerBusy by remember { mutableStateOf(false) }
    var composerError by remember { mutableStateOf<String?>(null) }

    /** 打开的那条帖子的 id；0 表示没开。 */
    var openId by remember { mutableStateOf(0L) }
    var thread by remember { mutableStateOf<PostThread?>(null) }
    var threadLoading by remember { mutableStateOf(false) }
    var threadError by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf("") }
    /** 二次确认要删的那条帖子；null 表示没有待确认的删除。 */
    var confirmTarget by remember { mutableStateOf<CommunityPost?>(null) }

    // 列表：第一次进来拉一页，之后每次 reloadKey 变动再拉。
    LaunchedEffect(store, reloadKey) {
        val current = store
        if (current == null) {
            loading = false
            return@LaunchedEffect
        }
        if (posts.isEmpty()) loading = true
        val fresh = current.list(0, POSTS_PAGE)
        if (fresh != null) {
            posts = fresh
            error = null
        } else {
            error = current.reason ?: "帖子没拉到，检查一下网络或代理。"
        }
        loading = false
    }

    // 详情：打开哪条拉哪条；回帖/点赞之后 reloadKey 变了也跟着刷一遍，数字与后端一致。
    LaunchedEffect(store, openId, reloadKey) {
        val current = store
        if (current == null || openId <= 0L) return@LaunchedEffect
        threadLoading = true
        threadError = null
        val fresh = current.get(openId)
        if (fresh == null) {
            thread = null
            threadError = current.reason ?: "这条帖子没拉到。"
        } else {
            thread = fresh
        }
        threadLoading = false
    }

    val loadMore: () -> Unit = more@{
        val current = store ?: return@more
        if (loadingMore || busyKey != null) return@more
        loadingMore = true
        scope.launch {
            val fresh = current.list(posts.size, POSTS_PAGE)
            loadingMore = false
            when {
                fresh == null -> platform.showMessage("没拉到更多：${current.reason ?: "检查一下网络"}")
                fresh.isEmpty() -> platform.showMessage("到底了")
                else -> posts = posts + fresh.filterNot { next -> posts.any { it.id == next.id } }
            }
        }
    }

    val submitPost: (String, String) -> Unit = submit@{ title, body ->
        val current = store
        if (current == null) {
            composerError = "社区后端没连上，帖子没发出去。"
            return@submit
        }
        composerBusy = true
        composerError = null
        scope.launch {
            val id = current.add(title, body, author)
            composerBusy = false
            if (id == null) {
                composerError = current.reason ?: "没发出去：检查一下网络再试"
            } else {
                composerOpen = false
                reloadKey++
                platform.showMessage("已发布")
            }
        }
    }

    val toggleLike: (CommunityPost) -> Unit = like@{ post ->
        val current = store ?: return@like
        if (busyKey != null) return@like
        busyKey = "like:${post.id}"
        scope.launch {
            val fresh = current.like(post.id, !post.mineLike)
            busyKey = null
            if (fresh == null) {
                platform.showMessage("没点上：${current.reason ?: "检查一下网络"}")
            } else {
                reloadKey++
            }
        }
    }

    val sendReply: () -> Unit = send@{
        val current = store ?: return@send
        val text = reply.trim()
        if (text.isEmpty() || busyKey != null || openId <= 0L) return@send
        busyKey = "reply:$openId"
        scope.launch {
            val count = current.comment(openId, text, author)
            busyKey = null
            if (count == null) {
                platform.showMessage("没回上：${current.reason ?: "检查一下网络"}")
            } else {
                reply = ""
                reloadKey++
            }
        }
    }

    val remove: (CommunityPost) -> Unit = del@{ post ->
        val current = store ?: return@del
        if (busyKey != null) return@del
        busyKey = "delete:${post.id}"
        scope.launch {
            val done = current.delete(post.id)
            busyKey = null
            confirmTarget = null
            if (done) {
                if (openId == post.id) {
                    openId = 0L
                    thread = null
                }
                reloadKey++
                platform.showMessage("已删除")
            } else {
                platform.showMessage("没删掉：${current.reason ?: "只有作者本人能删"}")
            }
        }
    }

    when {
        store == null -> {
            PostNote(
                text = "社区后端没连上，帖子暂时读不了。",
                modifier = modifier.padding(top = 8.dp),
            )
        }

        loading && posts.isEmpty() -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        else -> {
            val listError = error
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item(key = "post-new") {
                    PostEntryRow(
                        title = "发一帖",
                        hint = if (canWrite) "标题加正文，有话说就写下来。"
                        else "还没认出身份，登录之后才能发。",
                        onClick = {
                            composerError = null
                            if (canWrite) {
                                composerOpen = true
                            } else {
                                platform.showMessage("还没认出身份，发帖记不上")
                            }
                        },
                    )
                }

                item(key = "posts-head") {
                    PostSectionHeader(label = "帖子", count = posts.size)
                }

                if (listError != null) {
                    item(key = "posts-error") { PostNote(text = listError) }
                }

                if (posts.isEmpty()) {
                    item(key = "posts-empty") {
                        PostNote(text = "还没有人发帖。第一帖可以是你在用的某个方案、某台机器，或者一句吐槽。")
                    }
                }

                items(posts, key = { "post-${it.id}" }) { post ->
                    PostRow(
                        post = post,
                        mine = authorId.isNotBlank() && post.authorId == authorId,
                        onClick = {
                            thread = null
                            threadError = null
                            reply = ""
                            openId = post.id
                        },
                    )
                }

                if (posts.size >= POSTS_PAGE) {
                    item(key = "posts-more") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            TextButton(onClick = loadMore, enabled = !loadingMore && busyKey == null) {
                                Text(if (loadingMore) "正在拉…" else "加载更多")
                            }
                        }
                    }
                }

                item(key = "posts-foot") {
                    PostNote(
                        text = "帖子只经后端读写：认不出身份就发不出去，也删不了别人的帖子。" +
                            "刚发过一帖要等一分钟才能再发。",
                    )
                }
            }
        }
    }

    if (composerOpen) {
        PostComposerDialog(
            submitting = composerBusy,
            error = composerError,
            onDismiss = {
                composerOpen = false
                composerError = null
            },
            onSubmit = submitPost,
        )
    }

    if (openId > 0L) {
        val current = thread?.takeIf { it.post.id == openId }
        PostThreadDialog(
            post = current?.post,
            comments = current?.comments.orEmpty(),
            loading = threadLoading,
            error = threadError,
            busyKey = busyKey,
            reply = reply,
            locked = current?.post?.locked == true,
            canDelete = authorId.isNotBlank() && current?.post?.authorId == authorId,
            onReplyChange = { reply = it.take(4000) },
            onSend = sendReply,
            onToggleLike = { current?.post?.let(toggleLike) },
            onDeleteRequest = { current?.post?.let { confirmTarget = it } },
            onDismiss = {
                openId = 0L
                thread = null
                threadError = null
                reply = ""
            },
        )
    }

    confirmTarget?.let { target ->
        PostDeleteDialog(
            busy = busyKey == "delete:${target.id}",
            onDismiss = { confirmTarget = null },
            onConfirm = { remove(target) },
        )
    }
}

/** 一行能点的入口：加号、标题、一句说明。与机场那一块的入口同一副样子。 */
@Composable
private fun PostEntryRow(
    title: String,
    hint: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.primary)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = scheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 一个区块的标题：名字 + 条数。 */
@Composable
private fun PostSectionHeader(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 一句说明 / 空状态。 */
@Composable
private fun PostNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

/** 列表上一行帖子：置顶/锁定标记、标题、摘要、作者与时间、两个数。 */
@Composable
private fun PostRow(
    post: CommunityPost,
    mine: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (post.pinned) {
                PostBadge(text = "置顶", color = PostPinned)
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (post.locked) {
                PostBadge(text = "已锁", color = scheme.outline)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = post.title.ifBlank { "（无标题）" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (post.excerpt.isNotBlank()) {
            Text(
                text = post.excerpt,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = buildString {
                append(post.author.ifBlank { "匿名" })
                val stamp = relativeStamp(post.createdAt)
                if (stamp.isNotBlank()) {
                    append(" · ")
                    append(stamp)
                }
                append(" · ")
                append(post.comments)
                append(" 回复 · ")
                append(post.likes)
                append(" 赞")
                if (mine) append(" · 我的")
            },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一枚小标记：置顶、已锁。 */
@Composable
private fun PostBadge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** 发帖的表单：标题（必填）+ 正文。 */
@Composable
private fun PostComposerDialog(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val canSend = title.trim().isNotEmpty() && !submitting

    CantaDialog(
        onDismissRequest = onDismiss,
        title = "发一帖",
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
            Button(
                enabled = canSend,
                onClick = { onSubmit(title.trim(), body) },
            ) { Text(if (submitting) "正在发…" else "发布") }
        },
    ) {
        Text(
            text = "标题必填，正文可以留空。发出去之后只有你自己能删；同一身份一分钟只能发一帖。",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(120) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("标题") },
            placeholder = { Text("一句话说清这帖讲什么") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it.take(8000) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            label = { Text("正文（可留空）") },
            placeholder = { Text("细节、机型、ROM、试过什么") },
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = error, style = MaterialTheme.typography.labelSmall, color = scheme.error)
        }
    }
}

/**
 * 一条帖子的详情：正文全文、点赞、回帖列表、回复框。
 *
 * 回帖放在可滚动的对话框里而不是 LazyColumn：`CantaDialog(scrollable = true)` 已经给了一个
 * 纵向滚动，里面再套一个滚动列表会互相抢手势。后端一次最多给 200 条，撑得住。
 */
@Composable
private fun PostThreadDialog(
    post: CommunityPost?,
    comments: List<PostComment>,
    loading: Boolean,
    error: String?,
    busyKey: String?,
    reply: String,
    locked: Boolean,
    canDelete: Boolean,
    onReplyChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleLike: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 收进一个 val 再用：参数在下面几层 lambda 里要 smart cast，写作一处最省心。
    val target = post
    CantaDialog(
        onDismissRequest = onDismiss,
        title = target?.title?.ifBlank { "帖子" } ?: "帖子",
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text("关闭") }
            if (canDelete) {
                target?.let { shown ->
                    TextButton(
                        enabled = busyKey == null,
                        onClick = onDeleteRequest,
                    ) { Text(if (busyKey == "delete:${shown.id}") "正在删…" else "删除") }
                }
            }
        },
    ) {
        when {
            target == null && loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            target == null -> {
                Text(
                    text = error ?: "这条帖子没拉到。",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.error,
                )
            }

            else -> {
                Text(
                    text = buildString {
                        append(target.author.ifBlank { "匿名" })
                        val stamp = relativeStamp(target.createdAt)
                        if (stamp.isNotBlank()) {
                            append(" · ")
                            append(stamp)
                        }
                        if (target.pinned) append(" · 置顶")
                        if (target.locked) append(" · 已锁定")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (target.body.isNotBlank()) {
                    Text(
                        text = target.body,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                            .padding(10.dp),
                    )
                } else {
                    Text(
                        text = "（没有正文）",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = busyKey == null || busyKey == "like:${target.id}",
                        onClick = onToggleLike,
                    ) {
                        Text(
                            if (busyKey == "like:${target.id}") "…"
                            else if (target.mineLike) "已赞 ${target.likes}"
                            else "赞 ${target.likes}",
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${target.comments} 条回复",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "回复",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                )
                if (comments.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "还没有人回。",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    comments.forEach { comment ->
                        PostCommentRow(comment = comment)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                if (locked) {
                    Text(
                        text = "这条帖已经锁了，回不了。",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = reply,
                        onValueChange = onReplyChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        label = { Text("写一句回复") },
                        textStyle = MaterialTheme.typography.bodySmall,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            enabled = reply.isNotBlank() && busyKey == null,
                            onClick = onSend,
                        ) { Text(if (busyKey == "reply:${target.id}") "正在回…" else "回复") }
                    }
                }
            }
        }
    }
}

/** 详情里的一行回帖：作者、时间、正文。 */
@Composable
private fun PostCommentRow(comment: PostComment) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceContainerLow)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = buildString {
                append(comment.author.ifBlank { "匿名" })
                val stamp = relativeStamp(comment.createdAt)
                if (stamp.isNotBlank()) {
                    append(" · ")
                    append(stamp)
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = comment.body,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface,
        )
    }
}

/** 删帖的二次确认：删了回帖和赞一起没了，不能只靠一次点错。 */
@Composable
private fun PostDeleteDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "删除这条帖子？",
        widthFraction = WIDE_DIALOG_WIDTH,
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
            Button(enabled = !busy, onClick = onConfirm) {
                Text(if (busy) "正在删…" else "删除")
            }
        },
    ) {
        Text(
            text = "删了之后帖子、回帖和赞都一起没了，恢复不了。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
