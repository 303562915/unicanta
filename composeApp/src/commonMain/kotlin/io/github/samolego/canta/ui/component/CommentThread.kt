package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.CommentThread
import io.github.samolego.canta.data.community.PlanComment
import kotlin.math.absoluteValue

/**
 * The avatar colours, one per commenter, picked by name so the same person keeps the same circle on
 * every page and after a restart. They are the saturated, slightly flat tones a short-video comment
 * list uses, not theme colours: an avatar is an identity, not a surface.
 */
private val AVATAR_COLORS = listOf(
    Color(0xFFF06A6A),
    Color(0xFF4E8DF5),
    Color(0xFF35B37E),
    Color(0xFFEF9A3D),
    Color(0xFF9A6BF0),
    Color(0xFF2FB5C8),
    Color(0xFFE0609B),
    Color(0xFF7C8A3C),
)

/**
 * The red of the "作者" badge, taken from the short-video comment lists this page is laid out like.
 * It stays that red in both themes: the badge marks a role, not a surface, and a theme-toned badge
 * would read as one more piece of the plan's own colour scheme.
 */
private val AUTHOR_TAG = Color(0xFFFE2C55)

/** Height of a name badge. Fixed, so "我" and "作者" cannot grow taller than the name beside them. */
private val TAG_HEIGHT = 14.dp

/** Answers shown under a comment before the rest folds behind "展开 N 条回复". */
private const val THREAD_PREVIEW = 2

/** Where a reply's text starts: the 48dp indent, its 26dp avatar and the 9dp gap after it. */
private val REPLY_TEXT_INSET = 83.dp

/** The stable colour of [name]'s avatar. A hashCode can be negative or Int.MIN_VALUE: take the long. */
fun avatarColorOf(name: String): Color {
    if (AVATAR_COLORS.isEmpty()) return Color(0xFF8A8A8A)
    val index = (name.hashCode().toLong().absoluteValue % AVATAR_COLORS.size).toInt()
    return AVATAR_COLORS[index]
}

/**
 * Whether [comment] was written by the person who uploaded the plan.
 *
 * The identity number decides it whenever both sides have one: two people can pick the same
 * display name, and a name can change between the upload and the comment. [authorName] is the
 * uploader's number-shaped signature ("#10000"), which is what a comment written before the
 * backend kept the number was signed with — the only thing left to match on in that case.
 */
fun isPlanAuthor(comment: PlanComment, authorId: String, authorName: String): Boolean = when {
    authorId.isEmpty() && authorName.isEmpty() -> false
    comment.authorId.isEmpty() -> authorName.isNotEmpty() && comment.author == authorName
    authorId.isEmpty() -> false
    else -> comment.authorId == authorId
}

/**
 * The key a thread's open/closed state is remembered under.
 *
 * A comment id is what to use, but an older backend sends none: the position on the page keeps the
 * keys distinct there, which is all a fold needs.
 */
fun threadKey(comment: PlanComment, index: Int): Long = if (comment.id > 0L) comment.id else -1L - index

/**
 * The rows of one comment thread, for a `LazyColumn` that already has its own sections: every
 * comment that starts a conversation, then the answers it collected, indented under it.
 *
 * A thread that collected more than [THREAD_PREVIEW] answers is folded after the first ones, the
 * way a short-video comment list leaves one long conversation from burying everything else; the
 * reader opens it with "展开 N 条回复" and closes it again with "收起". [expanded] holds the
 * [threadKey]s that are open, [onToggleThread] flips one.
 *
 * [me] is how this install is signed (its display name, or its code before it picked one): only the
 * rows carrying that name offer **delete**, and only they are "mine" for the reply hint. [authorId]
 * and [authorName] identify the plan's uploader, whose rows carry the red "作者" badge; both are
 * empty on an app page, which has no author to mark.
 */
fun LazyListScope.commentThreads(
    threads: List<CommentThread>,
    me: String,
    onReply: (PlanComment) -> Unit,
    onDelete: (PlanComment) -> Unit,
    authorId: String = "",
    authorName: String = "",
    expanded: Set<Long> = emptySet(),
    onToggleThread: (Long) -> Unit = {},
) {
    // The ids are unique on the server, but an older backend sends none: the running index keeps
    // the keys distinct either way, which `LazyColumn` insists on.
    var position = 0
    threads.forEachIndexed { index, thread ->
        val starter = thread.comment
        val key = threadKey(starter, index)
        // A short thread is always open: folding two answers would cost a tap and save nothing.
        val open = thread.replies.size <= THREAD_PREVIEW || key in expanded
        val shown = if (open) thread.replies else thread.replies.take(THREAD_PREVIEW)
        item(key = "comment-${starter.id}-${position++}") {
            CommentRow(
                comment = starter,
                me = me,
                byAuthor = isPlanAuthor(starter, authorId, authorName),
                replies = starter.replies.coerceAtLeast(thread.replies.size),
                onReply = onReply,
                onDelete = onDelete,
            )
        }
        shown.forEach { answer ->
            item(key = "comment-${answer.id}-${position++}") {
                CommentRow(
                    comment = answer,
                    me = me,
                    byAuthor = isPlanAuthor(answer, authorId, authorName),
                    replyToName = starter.author,
                    replies = 0,
                    indented = true,
                    onReply = onReply,
                    onDelete = onDelete,
                )
            }
        }
        if (thread.replies.size > THREAD_PREVIEW) {
            item(key = "thread-more-$key") {
                ThreadToggle(
                    expanded = open,
                    count = thread.replies.size,
                    onClick = { onToggleThread(key) },
                )
            }
        }
    }
}

/** "展开 5 条回复" / "收起" under a thread: the one line that shows or hides its answers. */
@Composable
private fun ThreadToggle(expanded: Boolean, count: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = REPLY_TEXT_INSET, end = 12.dp, top = 1.dp, bottom = 5.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onClick)
                .padding(horizontal = 3.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) "收起" else "展开 $count 条回复",
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.primary,
            )
            Spacer(Modifier.width(1.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colors.primary,
            )
        }
    }
}

/**
 * A badge beside a comment's name: "作者" or "我".
 *
 * It is as tall as the small type inside it ([TAG_HEIGHT]) rather than as tall as its text's line
 * box, and it is centred in that height, so it lines up with the name instead of standing over it.
 */
@Composable
fun NameTag(label: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(TAG_HEIGHT)
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            // Slightly taller than the glyphs, or the descent of a CJK character is clipped by the
            // capsule it sits in.
            lineHeight = 11.sp,
            fontWeight = FontWeight.Medium,
            color = content,
            maxLines = 1,
        )
    }
}

/**
 * One comment, laid out the way a short-video comment list lays one out: a round avatar on the left,
 * the name in small grey type with the phone it was written on, the comment underneath, then one
 * quiet line with the date and "回复". A long press opens the same actions as that line, because
 * that is how people delete a comment there.
 *
 * Next to the name sit the two badges that tell a reader who they are talking to: the plan's
 * uploader is marked "作者" in red, this install is marked "我" in the accent colour. Both are the
 * height of a line of small type — a badge that grows taller than the name next to it reads as a
 * button, which neither of them is.
 *
 * Answers sit under their comment indented by the avatar's width, prefixed with "回复 @name：" the
 * way a reply reads in those threads.
 */
@Composable
fun CommentRow(
    comment: PlanComment,
    me: String,
    replyToName: String = "",
    replies: Int = 0,
    indented: Boolean = false,
    /** True for a comment the plan's uploader wrote: the row then carries the red "作者" badge. */
    byAuthor: Boolean = false,
    onReply: (PlanComment) -> Unit = {},
    onDelete: (PlanComment) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val mine = me.isNotEmpty() &&
        (comment.author == me || comment.author == "#$me" || comment.authorId == me)
    var sheet by remember(comment.id, comment.author, comment.body) { mutableStateOf(false) }
    val openSheet = { sheet = true }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 48.dp else 12.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // The avatar carries the long press as well as the text: a thumb lands on either.
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { openSheet() }) },
        ) {
            LetterAvatar(
                name = comment.author,
                size = if (indented) 26.dp else 36.dp,
                background = avatarColorOf(comment.author),
                letterColor = Color.White,
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                // The long press belongs to the comment's own text block, so the "回复" and "删除"
                // underneath stay ordinary taps.
                .pointerInput(Unit) { detectTapGestures(onLongPress = { openSheet() }) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author.ifEmpty { "匿名" },
                    fontSize = 11.sp,
                    // The line height is given, not inherited: the ambient body style would leave a
                    // 20sp-tall line box around 11sp type, which is what made the badges beside it
                    // look oversized.
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (byAuthor) {
                    Spacer(Modifier.width(4.dp))
                    NameTag(label = "作者", container = AUTHOR_TAG, content = Color.White)
                }
                if (mine) {
                    Spacer(Modifier.width(4.dp))
                    NameTag(label = "我", container = colors.primary, content = colors.onPrimary)
                }
                // The phone it was written on, right after the name: a comment about removing an app
                // is only as useful as the model and system it is about.
                if (comment.deviceLabel.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = comment.deviceLabel,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        color = colors.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            Row(modifier = Modifier.padding(top = 3.dp)) {
                if (replyToName.isNotEmpty()) {
                    Text(
                        text = "回复 @$replyToName：",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                Text(
                    text = comment.body,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.onSurface,
                )
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shortStamp(comment.createdAt),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = colors.outline,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "回复",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onReply(comment) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                if (mine && comment.id > 0L) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "删除",
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = colors.error.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onDelete(comment) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        // The reply count sits on the far right, the way a like count does in a comment list.
        if (replies > 0) {
            Column(
                modifier = Modifier
                    .padding(start = 8.dp, top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onReply(comment) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "$replies", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.outline)
                Text(text = "回复", fontSize = 8.sp, color = colors.outline)
            }
        }
    }

    if (sheet) {
        CommentActions(
            mine = mine && comment.id > 0L,
            canReply = true,
            onDismiss = { sheet = false },
            onReply = {
                sheet = false
                onReply(comment)
            },
            onDelete = {
                sheet = false
                onDelete(comment)
            },
        )
    }
}

/** The long-press sheet: what a comment can be answered with, and whether it can be taken back. */
@Composable
private fun CommentActions(
    mine: Boolean,
    canReply: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
) {
    CantaDialog(onDismissRequest = onDismiss, title = null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (canReply) {
                Text(
                    text = "回复这条评论",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onReply)
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                )
            }
            if (mine) {
                Text(
                    text = "删除这条评论",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                )
            }
            Text(
                text = "取消",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * The composer every comment page ends with: one line, the send button, and — while an answer is
 * being written — the name of the person it will answer, with a way out of it.
 */
@Composable
fun CommentComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    replyToName: String = "",
    onCancelReply: () -> Unit = {},
    sending: Boolean = false,
    failed: Boolean = false,
    sendLabel: String = "发送",
    hint: String = "写一条评论",
    failLabel: String = "发送失败，检查一下网络再试",
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            if (replyToName.isNotEmpty()) {
                ReplyChip(name = replyToName, onCancel = onCancelReply)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(hint, fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSend,
                    enabled = draft.isNotBlank() && !sending,
                    modifier = Modifier.height(44.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(sendLabel, fontSize = 11.sp)
                }
            }
            if (failed) {
                Text(
                    text = failLabel,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** "回复 @name" over the field, with the cross that turns the answer back into a plain comment. */
@Composable
private fun ReplyChip(name: String, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.primary.copy(alpha = 0.12f))
                .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = colors.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(text = "回复 $name", fontSize = 9.sp, color = colors.primary)
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "取消回复",
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onCancel),
                tint = colors.primary,
            )
            Spacer(Modifier.width(2.dp))
        }
    }
}

/**
 * A row of the "my comments" page: what was written, where, and how to get there.
 *
 * A long press deletes it, which is the gesture that page is used with; the bin on the row does the
 * same thing for people who do not know the gesture.
 */
@Composable
fun MyCommentRow(
    author: String,
    body: String,
    targetLabel: String,
    createdAt: String,
    replies: Int,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            // One gesture detector for both: a separate detectTapGestures(onLongPress) swallows
            // the tap it sits next to, which is why the row never opened its page.
            .combinedClickable(onClick = onOpen, onLongClick = onDelete)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterAvatar(
                name = author,
                size = 22.dp,
                background = avatarColorOf(author),
                letterColor = Color.White,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = targetLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = shortStamp(createdAt),
                fontSize = 9.sp,
                color = colors.outline,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除这条评论",
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDelete),
                tint = colors.error,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(text = body, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (replies > 0) {
            Spacer(Modifier.height(3.dp))
            Text(text = "$replies 条回复", fontSize = 9.sp, color = colors.primary)
        }
        Spacer(Modifier.height(2.dp))
        Text(text = "长按可以删除", fontSize = 8.sp, color = colors.outline)
    }
}

/**
 * "11-18 06:51" out of a Postgres timestamp. The string is sliced rather than parsed: every backend
 * this app talks to answers in ISO-8601 UTC, and a date that fails to parse must not blank a row.
 */
fun shortStamp(iso: String): String {
    if (iso.length < 16) return iso.take(10)
    return iso.substring(5, 10) + " " + iso.substring(11, 16)
}
