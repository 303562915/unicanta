package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.CommentThread
import io.github.samolego.canta.data.community.PlanComment

/**
 * The rows of one comment thread, for a `LazyColumn` that already has its own sections: every
 * comment that starts a conversation, then the answers it collected, indented under it.
 *
 * [me] is how this install is signed (its display name, or its code before it picked one): only the
 * rows carrying that name offer **delete**, and only they are "mine" for the reply hint.
 */
fun LazyListScope.commentThreads(
    threads: List<CommentThread>,
    me: String,
    onReply: (PlanComment) -> Unit,
    onDelete: (PlanComment) -> Unit,
) {
    // The ids are unique on the server, but an older backend sends none: the running index keeps
    // the keys distinct either way, which `LazyColumn` insists on.
    var position = 0
    threads.forEach { thread ->
        val starterKey = "comment-${thread.comment.id}-${position++}"
        item(key = starterKey) {
            CommentRow(
                comment = thread.comment,
                me = me,
                replies = thread.comment.replies.coerceAtLeast(thread.replies.size),
                onReply = onReply,
                onDelete = onDelete,
            )
        }
        thread.replies.forEach { answer ->
            item(key = "comment-${answer.id}-${position++}") {
                CommentRow(
                    comment = answer,
                    me = me,
                    replyToName = thread.comment.author,
                    replies = 0,
                    indented = true,
                    onReply = onReply,
                    onDelete = onDelete,
                )
            }
        }
    }
}

/**
 * One comment: the letter avatar the reviews use, who wrote it and when, the text, and — under it —
 * the two actions a thread needs, "回复" and, for a comment of this install, a bin.
 *
 * [replyToName] is set on answers: the row then says who it answers, which is what makes a flat
 * list of indented replies readable.
 */
@Composable
fun CommentRow(
    comment: PlanComment,
    me: String,
    replyToName: String = "",
    replies: Int = 0,
    indented: Boolean = false,
    onReply: (PlanComment) -> Unit = {},
    onDelete: (PlanComment) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val mine = me.isNotEmpty() && comment.author == me
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 40.dp else 16.dp, end = 16.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // The same letter avatar the reviews use: the first character of the name, or of the
        // install code when the commenter has not renamed themselves.
        LetterAvatar(
            name = comment.author,
            size = if (indented) 20.dp else 24.dp,
            background = if (indented) colors.surfaceContainerHighest else colors.secondaryContainer,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = comment.createdAt.take(10),
                    fontSize = 9.sp,
                    color = colors.outline,
                )
                if (replyToName.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "回复 @$replyToName",
                        fontSize = 9.sp,
                        color = colors.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (mine && comment.id > 0L) {
                    Text(
                        text = "删除",
                        fontSize = 9.sp,
                        color = colors.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDelete(comment) }
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = comment.body,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 1.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Answering is one tap on the comment itself, not a mode the composer has to
                // explain: the field above says who is being answered once it is on.
                Text(
                    text = "回复",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.primary.copy(alpha = 0.10f))
                        .clickable { onReply(comment) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                if (replies > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$replies 条回复",
                        fontSize = 9.sp,
                        color = colors.outline,
                    )
                }
            }
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

/** A row of the "my comments" page: what was written, where, and how to get there. */
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
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterAvatar(name = author, size = 22.dp, background = colors.secondaryContainer)
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
                text = createdAt.take(10),
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
    }
}
