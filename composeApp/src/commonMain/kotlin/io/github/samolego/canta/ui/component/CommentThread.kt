package io.github.samolego.canta.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.CommentOrder
import io.github.samolego.canta.data.community.CommentThread
import io.github.samolego.canta.data.community.FIRST_REPLY_BATCH
import io.github.samolego.canta.data.community.PlanComment
import kotlin.math.absoluteValue
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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

/**
 * The red a tapped heart turns: the same red the short-video comment lists light their hearts with,
 * and the same one the "作者" badge wears. A heart is a feeling, not a theme colour.
 */
private val LIKE_RED = Color(0xFFFE2C55)

/** Height of a name badge. Fixed, so "我" and "作者" cannot grow taller than the name beside them. */
private val TAG_HEIGHT = 16.dp

/**
 * The type scale of the comment list, measured off the short-video comment list this page is laid
 * out like: the name is small and grey, the comment itself is the size of body text, and everything
 * that is not the comment — the phone it was written on, the date, "回复" — is smaller and quieter
 * than the comment it stands under.
 *
 * Spelled as the raw numbers first and turned into `sp` below, because [EmotionText] draws a
 * comment's text from integers (so a sticker can be measured against the same line height) and the
 * two must not drift apart.
 */
private const val NAME_SP = 13
private const val BODY_SP = 15
private const val META_SP = 11

private val NAME_SIZE = NAME_SP.sp
private val NAME_LINE = (NAME_SP + 4).sp
private val BODY_LINE = (BODY_SP + 6).sp
private val META_SIZE = META_SP.sp
private val META_LINE = (META_SP + 3).sp
private val ACTION_SIZE = 12.sp

/** Where a thread's own lines start: the 12dp gutter, the 36dp avatar and the 9dp gap after it. */
private val THREAD_TEXT_INSET = 57.dp

/** Where a reply's text starts: the 48dp indent, its 26dp avatar and the 9dp gap after it. */
private val REPLY_TEXT_INSET = 83.dp

/**
 * How many answers a thread shows the first time it is opened, and how many each later "展开更多"
 * adds: three is enough to see whether the conversation is worth reading, ten is a page of it.
 */


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
 * The key a comment is hidden under when the reader puts it away with the broken heart.
 *
 * The comment's own id is what to use, but an older backend sends none: the thread's own key stands
 * in for it there — a hide that lasts one session has no need for more.
 */
internal fun hideKey(comment: PlanComment, key: Long): Long = if (comment.id > 0L) comment.id else key

/**
 * The hide key of an answer: its id, or its thread's key folded with where it stands in that
 * thread. The fold keeps an answer's key from ever matching a first-level comment's key, which is
 * what would make one broken heart swallow the wrong row.
 */
internal fun replyHideKey(key: Long, reply: PlanComment, replyIndex: Int): Long =
    if (reply.id > 0L) reply.id else key * 4096L - 1L - replyIndex

/**
 * The switcher above a comment list: newest, most hearted, or most useful.
 *
 * Three taps is all this is, so it is drawn as plain text buttons rather than a dropdown: a
 * dropdown for three fixed options hides two of them behind an extra tap, and the current order is
 * exactly what a reader wants to see at a glance.
 */
@Composable
fun CommentOrderRow(order: CommentOrder, onOrderChange: (CommentOrder) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommentOrder.entries.forEach { option ->
            val selected = option == order
            Text(
                text = option.label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surfaceContainerHighest
                        else Color.Transparent
                    )
                    .clickable { if (!selected) onOrderChange(option) }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * The rows of one comment thread, for a `LazyColumn` that already has its own sections: every
 * comment that starts a conversation, then — once the reader asks for them — the answers it
 * collected, indented under it.
 *
 * A thread's answers never show on their own: the first-level comment stands alone until the
 * reader opens it with "展开 N 条回复", and closes it again with "收起", which sits after the last
 * answer, at the very end of the thread. [expanded] holds the [threadKey]s that are open,
 * [onToggleThread] flips one.
 *
 * [me] is how this install is signed (its display name, or its code before it picked one): only the
 * rows carrying that name offer **delete**, and only they are "mine" for the reply hint. [authorId]
 * and [authorName] identify the plan's uploader, whose rows carry the red "作者" badge; both are
 * empty on an app page, which has no author to mark.
 *
 * [onLike] taps a comment's heart; the heart and its count are read off the comment itself, so the
 * page only has to write the new state back into its list. [hidden] holds the hide keys of
 * comments this install folded with the broken heart for this session. A folded first-level
 * comment also conceals its replies. [onHide] toggles one key.
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
    /** How many answers each open thread currently shows; see [FIRST_REPLY_BATCH]. */
    shownReplies: Map<Long, Int> = emptyMap(),
    /** Asks for the next batch of one thread's answers. */
    onShowMoreReplies: (Long) -> Unit = {},
    onLike: (PlanComment) -> Unit = {},
    hidden: Set<Long> = emptySet(),
    onHide: (Long) -> Unit = {},
) {
    threads.forEachIndexed { index, thread ->
        val starter = thread.comment
        val key = threadKey(starter, index)
        val starterHidden = hideKey(starter, key) in hidden
        // A folded first-level comment conceals its answers until it is expanded again.
        val open = thread.replies.isEmpty() || key in expanded
        val wanted = shownReplies[key] ?: FIRST_REPLY_BATCH
        val visible = thread.replies.take(wanted)
        val remaining = thread.replies.size - visible.size
        val shown = if (open && !starterHidden) visible.withIndex() else emptyList()
        item(key = "comment-${starter.id}-$index") {
            CommentRow(
                comment = starter,
                me = me,
                byAuthor = isPlanAuthor(starter, authorId, authorName),
                onReply = onReply,
                onDelete = onDelete,
                onLike = { onLike(starter) },
                folded = starterHidden,
                onHide = { onHide(hideKey(starter, key)) },
            )
        }
        shown.forEach { (replyIndex, answer) ->
            item(key = "reply-${answer.id}-$index-$replyIndex") {
                CommentRow(
                    comment = answer,
                    me = me,
                    byAuthor = isPlanAuthor(answer, authorId, authorName),
                    // Only an answer aimed at another answer names anyone: a direct answer to the
                    // first-level comment needs no marker at all.
                    replyToName = thread.markerFor(answer),
                    indented = true,
                    onReply = onReply,
                    onDelete = onDelete,
                    onLike = { onLike(answer) },
                    folded = replyHideKey(key, answer, replyIndex) in hidden,
                    onHide = { onHide(replyHideKey(key, answer, replyIndex)) },
                )
            }
        }
        if (thread.replies.isNotEmpty() && !starterHidden) {
            when {
                // Closed: one line that opens the first batch.
                !open -> item(key = "thread-more-$key") {
                    ThreadToggle(
                        label = "展开 ${thread.replies.size} 条回复",
                        up = false,
                        onClick = { onToggleThread(key) },
                    )
                }
                // Open, with more behind it: the same line, counting only what is still hidden.
                remaining > 0 -> item(key = "thread-more-$key") {
                    ThreadToggle(
                        label = "展开更多 $remaining 条",
                        up = false,
                        onClick = { onShowMoreReplies(key) },
                    )
                }
            }
            if (open) {
                item(key = "thread-less-$key") {
                    ThreadToggle(label = "收起", up = true, onClick = { onToggleThread(key) })
                }
            }
        }
    }
}

/** "展开 5 条回复" / "展开更多 10 条" / "收起": the one line that moves a thread along. */
@Composable
private fun ThreadToggle(label: String, up: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Lined up with the comment above it, not with that comment's own answers: the line is
            // about the thread, so it starts where the thread's text does.
            .padding(start = THREAD_TEXT_INSET, end = 12.dp, top = 1.dp, bottom = 5.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onClick)
                .padding(horizontal = 3.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "—— $label",
                fontSize = META_SIZE,
                lineHeight = META_LINE,
                fontWeight = FontWeight.Medium,
                color = colors.outline,
            )
            Spacer(Modifier.width(1.dp))
            Icon(
                imageVector = if (up) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colors.outline,
            )
        }
    }
}

/**
 * How much of a [CrackedHeart]'s box the drawn heart leaves empty, as a fraction of its side.
 *
 * The outline is measured against what is left after this inset is taken off both sides, so the
 * shape ends up the size Material's glyphs draw themselves inside the same box — which is the only
 * way two icons given the same dp look like a pair.
 */
private const val HEART_GLYPH_INSET = 0.11f

/**
 * A heart outline with one thin crack through it: the "not for me, fold this" reaction.
 *
 * Drawn rather than picked from the icon set, because neither glyph in the set is this: `Bolt` is a
 * lightning strike (a different idea altogether) and `HeartBroken` tears the heart into two halves
 * with a gap wide enough that it reads as "destroyed" instead of "put away". This is one closed
 * outline — the same silhouette as the like button next to it — plus a narrow zigzag stroke down the
 * middle, which is what the cracked heart in a short-video comment list looks like.
 *
 * Everything is measured against the canvas box, so the shape holds at 16dp beside a heart and at
 * any other size it may be asked for later.
 */
@Composable
internal fun CrackedHeart(
    tint: Color,
    modifier: Modifier = Modifier,
    /** Hairline by intent: a heavier stroke turns the crack into a slash. */
    strokeWidth: Dp = 1.1.dp,
) {
    Canvas(modifier = modifier) {
        val w = size.width * (1f - HEART_GLYPH_INSET)
        val h = size.height * (1f - HEART_GLYPH_INSET)
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val stroke = strokeWidth.toPx()
        val outline = Path().apply {
            moveTo(left + w * 0.50f, top + h * 0.31f)
            cubicTo(
                left + w * 0.50f, top + h * 0.20f,
                left + w * 0.41f, top + h * 0.06f,
                left + w * 0.28f, top + h * 0.06f,
            )
            cubicTo(
                left + w * 0.13f, top + h * 0.06f,
                left + w * 0.03f, top + h * 0.19f,
                left + w * 0.03f, top + h * 0.33f,
            )
            cubicTo(
                left + w * 0.03f, top + h * 0.53f,
                left + w * 0.23f, top + h * 0.70f,
                left + w * 0.50f, top + h * 0.94f,
            )
            cubicTo(
                left + w * 0.77f, top + h * 0.70f,
                left + w * 0.97f, top + h * 0.53f,
                left + w * 0.97f, top + h * 0.33f,
            )
            cubicTo(
                left + w * 0.97f, top + h * 0.19f,
                left + w * 0.87f, top + h * 0.06f,
                left + w * 0.72f, top + h * 0.06f,
            )
            cubicTo(
                left + w * 0.59f, top + h * 0.06f,
                left + w * 0.50f, top + h * 0.20f,
                left + w * 0.50f, top + h * 0.31f,
            )
            close()
        }
        drawPath(
            path = outline,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // One crack, starting in the notch at the top and running down past the middle. The kinks
        // are sharp (Miter) so it reads as a fracture rather than as a curve.
        val crack = Path().apply {
            moveTo(left + w * 0.58f, top + h * 0.13f)
            lineTo(left + w * 0.43f, top + h * 0.38f)
            lineTo(left + w * 0.58f, top + h * 0.53f)
            lineTo(left + w * 0.44f, top + h * 0.73f)
            lineTo(left + w * 0.50f, top + h * 0.90f)
        }
        drawPath(
            path = crack,
            color = tint,
            style = Stroke(width = stroke * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Miter),
        )
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
 * quiet line with how long ago it landed and "回复".
 *
 * The row itself carries the two gestures a comment list owns: a tap answers it — the composer comes
 * up below, naming who it answers — and a long press opens the action sheet, which is where delete
 * lives. Nothing on the row says "删除": a comment somebody else wrote is not something the page
 * puts a bin on, and one's own is taken back the way the short-video lists do it, from the sheet.
 *
 * On the right edge sits the column those lists end every row with: the heart and its count, red
 * once this install tapped it, and under it the broken heart, which puts the comment away — a hide
 * that belongs to this reader and this session only, not a deletion on the server.
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
    indented: Boolean = false,
    /** True for a comment the plan's uploader wrote: the row then carries the red "作者" badge. */
    byAuthor: Boolean = false,
    onReply: (PlanComment) -> Unit = {},
    onDelete: (PlanComment) -> Unit = {},
    /** Taps the heart on this comment. */
    onLike: () -> Unit = {},
    /** Folds this comment's text for this reader, this session only; taps again to open it back. */
    onHide: () -> Unit = {},
    folded: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val mine = me.isNotEmpty() &&
        (comment.author == me || comment.author == "#$me" || comment.authorId == me)
    var sheet by remember(comment.id, comment.author, comment.body) { mutableStateOf(false) }
    val openSheet = { sheet = true }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // One gesture pair for the whole row: a thumb lands anywhere on the comment and gets
            // the same two actions, the way a short-video comment list does it. The buttons keep
            // their own taps.
            .combinedClickable(onClick = { onReply(comment) }, onLongClick = openSheet)
            .animateContentSize()
            .padding(start = if (indented) 48.dp else 12.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.clip(CircleShape)) {
            LetterAvatar(
                name = comment.author,
                size = if (indented) 26.dp else 36.dp,
                background = avatarColorOf(comment.author),
                letterColor = Color.White,
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            // The avatar and the name are never folded: a list of folded comments still has to say
            // *whose* comments they are. Only the text below them goes away, which is what makes a
            // folded row one readable line instead of a blank.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author.ifEmpty { "匿名" },
                    // The name is the one step below the comment itself: grey, and quiet enough that
                    // the text under it is what the eye lands on.
                    fontSize = NAME_SIZE,
                    // The line height is given, not inherited: the ambient body style would leave a
                    // 20sp-tall line box around 11sp type, which is what made the badges beside it
                    // look oversized.
                    lineHeight = NAME_LINE,
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
                        fontSize = META_SIZE,
                        lineHeight = META_LINE,
                        color = colors.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            // The fold is animated in place rather than replacing the row: the text slides shut
            // under the name it belongs to, and the heart beside it never moves, so a fold cannot
            // look like the comment was deleted.
            AnimatedVisibility(
                visible = !folded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Row(modifier = Modifier.padding(top = 3.dp), verticalAlignment = Alignment.Top) {
                        if (replyToName.isNotEmpty()) {
                            // A grey right-pointing triangle, then whose comment this answers. Not
                            // an "@": this row is an answer to another answer, and the arrow says
                            // "this line is aimed at that person" without spelling out a prefix.
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = colors.outline,
                                modifier = Modifier
                                    .padding(top = 4.dp, end = 2.dp)
                                    .size(9.dp),
                            )
                            Text(
                                text = "$replyToName ",
                                fontSize = NAME_SIZE,
                                lineHeight = BODY_LINE,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        // Stickers are drawn as pictures here: the body carries "[weixiao]" and the
                        // renderer puts the sticker back where it was typed, in the same paragraph.
                        EmotionText(
                            body = comment.body,
                            fontSize = BODY_SP,
                            lineHeight = BODY_LINE.value.toInt(),
                            color = colors.onSurface,
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = relativeStamp(comment.createdAt),
                            fontSize = META_SIZE,
                            lineHeight = META_LINE,
                            color = colors.outline,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "回复",
                            fontSize = META_SIZE,
                            lineHeight = META_LINE,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onReply(comment) }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = folded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "评论已折叠",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = colors.outline,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "展开",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onHide)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        // The two reactions on the far right, side by side the way this list ends every row: the
        // heart with its count *beside* it, and the single-stroke bolt that folds the comment. The
        // count sits to the right of the heart rather than under it — a number under the heart
        // pushes the two icons apart and reads as a caption instead of a tally.
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onLike)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (comment.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "赞这条评论",
                    modifier = Modifier.size(16.dp),
                    tint = if (comment.liked) LIKE_RED else colors.outline,
                )
                if (comment.likes > 0) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${comment.likes}",
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (comment.liked) LIKE_RED else colors.outline,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            // A heart with a thin crack down it, not a bolt and not Material's HeartBroken: that
            // glyph splits the heart into two halves with a wide gap, which reads as "broken
            // forever", while this one is the same outline heart as the like button beside it with
            // one narrow zigzag stroke through the middle.
            //
            // 16dp like the heart, but drawn with [HEART_GLYPH_INSET] of it left empty: Material's
            // glyphs fill about 7/8 of their box, so a shape drawn edge to edge in the same 16dp
            // looks noticeably bigger than the icon it sits beside. The inset is what makes the two
            // measure the same rather than merely share a size.
            CrackedHeart(
                modifier = Modifier
                    .padding(4.dp)
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onHide),
                tint = if (folded) colors.primary else colors.outline,
            )
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
 * The composer every comment page ends with: one quiet pill field and the send button. The field
 * asks for a comment on its own — "发条评论，说说你的感受" — and names the person it answers once
 * one was picked, the way a short-video composer does it. Picking a comment brings the field up
 * focused, so the answer starts where the thumb landed.
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
    failLabel: String = "发送失败，检查一下网络再试",
    /**
     * Whether the sticker panel is up.
     *
     * Hoisted out of this composable on purpose: the panel covers the bottom of the page, and the
     * only way a tap on the empty space above it can close it is if the page itself knows the panel
     * is open. The bar owns the key that toggles it, the page owns "somebody tapped outside".
     */
    pickerOpen: Boolean = false,
    /** Asked for when the panel's key is tapped: true to open it, false to go back to the keyboard. */
    onPickerOpenChange: (Boolean) -> Unit = {},
) {    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val imeController = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(replyToName) {
        if (replyToName.isNotEmpty() && !pickerOpen) focusRequester.requestFocus()
    }
    // The panel closing from outside — a tap on the page behind it — has to put the keyboard back:
    // a bar with neither the panel nor a caret is a bar the reader cannot type into.
    LaunchedEffect(pickerOpen) {
        if (!pickerOpen) focusRequester.requestFocus()
    }
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        // What the bar has to clear at the bottom is whichever wins: the keyboard while it is up,
        // the navigation bar while it is down. The IME inset is measured, not trusted — ROMs that
        // report the whole window as the IME inset are what once made this bar a screen tall with
        // the field sitting at its top, and a keyboard is never that tall, so anything above 75%
        // of the room is that broken value and only the navigation bar is cleared. The room is
        // bounded first: an unbounded measure would let every value pass that check, which is the
        // same as not having one.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // Read here, in the BoxWithConstraints scope, and carried into the columns below: the
            // panel's share of the room is a fact about this box, and the inner scope cannot reach
            // its receiver.
            val roomHeight = if (maxHeight.value.isFinite() && maxHeight > 0.dp) maxHeight else 800.dp
            val panelHeight = (roomHeight * 0.42f).coerceIn(180.dp, 260.dp)
            val keyboard = if (imeBottom > navBottom && imeBottom < roomHeight * 0.75f) imeBottom else 0.dp
            // The row of controls and the sticker panel are stacked, not overlaid. A Box lays its
            // children on top of each other, which is what put the panel over the field and made
            // the input box disappear the moment the stickers opened: the field was still there,
            // under an opaque grid. The outer column clears the bottom inset once, and the inner
            // one keeps the row off the edges; the panel runs edge to edge under both.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = maxOf(navBottom, keyboard)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
                ) {
                if (replyToName.isNotEmpty()) {
                    ReplyChip(name = replyToName, onCancel = onCancelReply)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The field: a white box one line tall (about 54dp with its padding) with a small
                    // radius rather than a full pill, so it reads as an input box and not as a
                    // button. White, not the tinted container colour: a comment field is a sheet of
                    // paper under the text, and the tint made it look like a disabled control.
                    //
                    // It grows with what is written — a comment that runs past the edge of the line
                    // wraps and the bar gets taller — up to a palm's worth of lines; past that the
                    // box stops growing and scrolls. The scroll sits outside the field on purpose:
                    // the field itself is never scrolled, which is what keeps a sticker drawn from
                    // its own line's measurement on top of the character it stands for.
                    val fieldScroll = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 132.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.outlineVariant, RoundedCornerShape(10.dp))
                            .clickable { focusRequester.requestFocus() },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(fieldScroll)
                                .padding(horizontal = 12.dp, vertical = 16.dp),
                        ) {
                            // The field shows the stickers, not the tokens: "[偷笑]" is how a
                            // comment travels through the clipboard and the backend, but a person
                            // typing sees the sticker itself.
                            //
                            // A text field cannot draw a picture inside its own line, so the field
                            // is laid out from a line where every sticker is one ideographic space
                            // — one picture wide — and the picture is drawn in that space's box,
                            // underneath the field's own drawing. The caret is measured from the
                            // same line, which is what keeps it on the sticker's right edge instead
                            // of a token's width away from it. What receives the keystrokes, the
                            // selection and the paste is still the field, and the text it reports
                            // back is still the body.
                            var fieldValue by remember {
                                mutableStateOf(TextFieldValue(draft, TextRange(draft.length)))
                            }
                            LaunchedEffect(draft) {
                                // A sticker from the panel, or a send that cleared the draft,
                                // arrives from outside the field: move the caret to the end so the
                                // next keystroke lands after what was just added.
                                if (fieldValue.text != draft) {
                                    fieldValue = TextFieldValue(draft, TextRange(draft.length))
                                }
                            }
                            val display = remember(draft) { emotionFieldDisplayOf(draft) }
                            val stickers = display.cells.map { painterResource(it.emotion.resource) }
                            var lineLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
                            // The prompt rides on the first line rather than under it: the field is
                            // one empty line tall before anything is typed, and a second child in
                            // the column would push the bar down by a line for no reason.
                            Box(modifier = Modifier.fillMaxWidth()) {
                                BasicTextField(
                                    value = fieldValue,
                                    onValueChange = { typed ->
                                        // Backspace over a sticker takes the whole sticker: half a
                                        // token on screen would be a bracket the reader never typed.
                                        // Nothing is cut off at the end: a comment is as long as it
                                        // was written.
                                        val next = backspaceEmotionAware(draft, typed.text)
                                        fieldValue = if (next == typed.text) {
                                            typed
                                        } else {
                                            TextFieldValue(next, TextRange(next.length))
                                        }
                                        if (next != draft) onDraftChange(next)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                        // The stickers go under the field's own drawing, not over
                                        // it: their space has no glyph, so nothing of the field is
                                        // hidden by them, and the caret stays visible when it sits
                                        // right before one.
                                        .drawBehind {
                                            val laid = lineLayout ?: return@drawBehind
                                            val shownLength = laid.layoutInput.text.length
                                            display.cells.forEachIndexed { cellIndex, cell ->
                                                val painter = stickers.getOrNull(cellIndex)
                                                    ?: return@forEachIndexed
                                                if (cell.index >= shownLength) {
                                                    return@forEachIndexed
                                                }
                                                val box = laid.getBoundingBox(cell.index)
                                                val side = minOf(box.width, box.height)
                                                if (side <= 0f) return@forEachIndexed
                                                translate(
                                                    left = box.left + (box.width - side) / 2f,
                                                    top = box.top + (box.height - side) / 2f,
                                                ) {
                                                    with(painter) {
                                                        draw(size = Size(side, side))
                                                    }
                                                }
                                            }
                                        },
                                    // The body's own words are the field's own text; only the
                                    // stickers are substitutes, and their space draws nothing. No
                                    // `singleLine`: a comment that reaches the edge of the box wraps
                                    // instead of running off it.
                                    textStyle = TextStyle(
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = colors.onSurface,
                                    ),
                                    cursorBrush = SolidColor(colors.primary),
                                    visualTransformation = remember(display) {
                                        EmotionFieldTransformation(display)
                                    },
                                    onTextLayout = { lineLayout = it },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                                )
                                if (draft.isEmpty()) {
                                    Text(
                                        text = if (replyToName.isNotEmpty()) {
                                            "回复 @$replyToName"
                                        } else {
                                            "发条评论，说说你的感受"
                                        },
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = colors.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // The sticker key, between the field and send: the one action this bar has that
                    // is not text. Lit while the panel is up, so the bar says where the taps go.
                    Icon(
                        imageVector = if (pickerOpen) {
                            Icons.Default.Keyboard
                        } else {
                            Icons.Default.EmojiEmotions
                        },
                        contentDescription = if (pickerOpen) "收起表情" else "表情",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                if (pickerOpen) {
                                    onPickerOpenChange(false)
                                    focusRequester.requestFocus()
                                } else {
                                    // The keyboard gives way to the panel: both open at once would
                                    // leave the grid with a quarter of the screen.
                                    focus.clearFocus()
                                    imeController?.hide()
                                    onPickerOpenChange(true)
                                }
                            }
                            .padding(6.dp),
                        tint = if (pickerOpen) colors.primary else colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    val canSend = draft.isNotBlank() && !sending
                    // The send pill: not a Material button — its minimum touch target would prop
                    // the whole bar up higher than a comment bar needs to be. A capsule, lit when
                    // there is something to send, dim when there is not.
                    Box(
                        modifier = Modifier
                            .height(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (canSend) colors.primary else colors.surfaceContainerHighest)
                            .clickable(enabled = canSend, onClick = onSend)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (canSend) colors.onPrimary else colors.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = sendLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (canSend) colors.onPrimary else colors.onSurfaceVariant,
                            )
                        }
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
                // No counter and no ceiling: the field keeps every character that was typed, and
                // the backend stores the comment whole.
            }

                // Below the field, never over it: the panel is a second row of the same stack, so
                // the input box stays on screen while the stickers are open.
                if (pickerOpen) {
                    EmotionPicker(
                        onPick = { emotion ->
                            rememberEmotionPick(emotion.name)
                            onDraftChange(draft + emotion.token)
                        },
                        onDelete = { onDraftChange(dropLastSticker(draft)) },
                        onKeyboard = {
                            onPickerOpenChange(false)
                            focusRequester.requestFocus()
                        },
                        panelHeight = panelHeight,
                    )
                }
            }
        }
    }
}

/**
 * [draft] with its last sticker taken out, or its last character when it does not end with one.
 *
 * Backspace in the sticker panel deletes a sticker whole: a sticker is one thing the reader put
 * there, and half of `[wei` on screen is a token that no longer means anything.
 */
internal fun dropLastSticker(draft: String): String {
    val trimmed = draft.trimEnd()
    if (trimmed.endsWith("]")) {
        val open = trimmed.lastIndexOf('[')
        if (open >= 0) {
            val name = trimmed.substring(open + 1, trimmed.length - 1)
            if (emotionOf(name) != null) return trimmed.substring(0, open)
        }
    }
    return if (draft.isEmpty()) draft else draft.dropLast(1)
}

/**
 * What the draft becomes when the field reports [typed] after the reader deleted one character.
 *
 * Backspace is the one edit that has to know about stickers: a token is written as "[偷笑]", and
 * deleting its last character would leave "[偷笑" on screen — a bracket the reader never typed and
 * cannot see the end of. So a deletion that removes exactly the last character removes the whole
 * token when the draft ends with one, and the ordinary character when it does not.
 */
internal fun backspaceEmotionAware(shown: String, typed: String): String =
    if (typed == shown.dropLast(1)) dropLastSticker(shown) else typed



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
 * The bin on the row deletes it; a long press does the same thing for anyone who tries it. Both
 * are quiet — nothing on the row announces either one, because a list of one's own comments does
 * not need a caption explaining how to remove one of them.
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
    /** How many people hearted this comment: the tally the inbox line is about, on the row itself. */
    likes: Int = 0,
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
        // The same renderer the thread uses: a comment full of stickers has to look the same on the
        // page that lists what this install wrote as it does under the plan.
        EmotionText(body = body, fontSize = 12, lineHeight = 16, maxLines = 3, color = colors.onSurface)
        // The two numbers a comment of one's own collects, said the way the thread says them: how
        // many hearts and how many answers. A heart on this row is the same event the inbox
        // reports, so the tally is drawn here too — otherwise "3 人赞了" would arrive without the
        // row it is about ever showing the 3.
        if (replies > 0 || likes > 0) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildList {
                    if (likes > 0) add("$likes 赞")
                    if (replies > 0) add("$replies 条回复")
                }.joinToString(" · "),
                fontSize = 9.sp,
                color = colors.primary,
            )
        }
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

/**
 * "3分钟前" out of a Postgres timestamp — the way a short-video comment list dates its rows: what
 * is fresh reads as the time that passed since it landed, what is months old falls back to the
 * plain "11-18 06:51".
 *
 * The timestamp is parsed rather than sliced; PostgREST signs its UTC values "+00:00", which the
 * parser wants as "Z". Anything that does not parse — a drifted clock, a row written before the
 * column existed — falls back to [shortStamp] instead of blanking the row.
 */
@OptIn(ExperimentalTime::class)
fun relativeStamp(
    iso: String,
    now: Instant = Clock.System.now(),
    /** The calendar the "昨天" is read against; tests pin it so a runner's zone cannot flip them. */
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val parsed = runCatching { Instant.parse(iso.replace(Regex("[+]00:00$"), "Z")) }.getOrNull()
        ?: return shortStamp(iso)
    val elapsed = now - parsed
    if (elapsed.inWholeSeconds < 60L) return "刚刚"
    val minutes = elapsed.inWholeMinutes
    if (minutes < 60L) return "${minutes}分钟前"
    // The calendar says it landed yesterday: it reads as that day and its time, not as a bare
    // hour count — "11小时前" tells nothing about which evening the comment came from.
    val daysBetween = now.toLocalDateTime(zone).date.toEpochDays() -
        parsed.toLocalDateTime(zone).date.toEpochDays()
    if (daysBetween == 1L) {
        val time = parsed.toLocalDateTime(zone).time
        return "昨天 " + time.hour.toString().padStart(2, '0') + ":" + time.minute.toString().padStart(2, '0')
    }
    val hours = elapsed.inWholeHours
    if (hours < 24L) return "${hours}小时前"
    val days = elapsed.inWholeDays
    if (days < 30L) return "${days}天前"
    if (days < 365L) return "${days / 30L}个月前"
    return shortStamp(iso)
}
