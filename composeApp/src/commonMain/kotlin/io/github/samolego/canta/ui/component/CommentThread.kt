package io.github.samolego.canta.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.onFocusChanged
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
import io.github.samolego.canta.data.cache.ScreenCache
import io.github.samolego.canta.util.BeijingTime
import kotlinx.serialization.Serializable
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
fun CommentOrderRow(
    order: CommentOrder,
    onOrderChange: (CommentOrder) -> Unit,
    /**
     * True when the switcher shares a line with a heading, instead of owning one: it then drops
     * its own margins and stops filling the width, so the heading can take the left end.
     */
    inline: Boolean = false,
) {
    Row(
        modifier = Modifier
            .then(if (inline) Modifier else Modifier.fillMaxWidth())
            .padding(
                horizontal = if (inline) 0.dp else 16.dp,
                vertical = if (inline) 0.dp else 2.dp,
            ),
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
 * 一个评论列表上一次画出来的那一批线程，按被评论的对象（包名或方案号）分格存在进程里。
 *
 * 为什么放在这一层：这里的数据是上层参数传进来的，它自己不拉网络，所以"失败保留旧内容、只在顶部
 * 说一行原因"归页面管（页面自己有那一份快照，也自己画那行原因）。这一层能保住的是另一件小事——
 * 页面刚进来、还没读过的那一帧手里也是空列表，而上一批线程刚才明明画在屏幕上，先把它们摆上，
 * 等页面把网络回来的那一批交下来再顶掉。**只在页面说"还没到"（[commentThreads] 的 `loading`）时
 * 才垫**：空列表也可能就是"评论被删光了"，那时垫上一次的内容等于把删掉的评论又画回来。
 *
 * 只放画一屏要用的只读数据（[CommentThread] 里的 [PlanComment] 都是不可变 data class）；
 * **不放** MutableState、协程、回调、输入框草稿、回复对象、以及展开/折叠这些开关——它们记在
 * 页面里，重新进来本该是干净的，缓存它们只会把上一回没做完的动作带回来。
 *
 * 展开/折叠、点赞、删除都改在上层：这一层只读地画，所以它不做写回，也不可能留下一份"点赞数
 * 还是旧的"名单——每次画的都是刚交下来的那一批（见下面 [commentThreads] 里的写入）。
 */
@Serializable
private data class CommentThreadsSnapshot(val threads: List<CommentThread>)

/**
 * 快照的键：带上被评论的对象，一个对象一格。
 *
 * 评论是"这个应用 / 这个方案"底下的话，两个对象共用一格会把别人家那几条画到这里来。
 * 对象未知（空串）时返回 null：那时既不读也不写，空串那一格会被所有页面串用。
 * 键的惯例见 [ScreenCache]：`"screen:comments:<包名或方案号>"`。
 */
private fun commentThreadsCacheKey(target: String): String? =
    if (target.isEmpty()) null else "screen:comments:$target"

/**
 * The rows of one comment thread, for a `LazyColumn` that already has its own sections: every
 * comment that starts a conversation, then — once the reader asks for them — the answers it
 * collected, indented under it.
 *
 * A thread's answers never show on their own: the first-level comment stands alone until the
 * reader opens it with "展开 N 条回复", and closes it again with "收起", which sits after the last
 * answer shown. While the thread still has answers left, "收起" shares that last line with
 * "展开更多 N 条" — on its right, not on a line of its own below it: two stacked lines read as two
 * unrelated actions at the bottom of the thread. [expanded] holds the [threadKey]s that are open,
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
    /**
     * 从「我的评论 / 互动消息」点进来的那一条：[highlightId] 是它，[highlight] 是此刻的闪光强度。
     * 详情页把这一行滚到屏幕中间之后连闪两下，位置和亮度一起说明「就是这一条」。
     */
    highlightId: Long = 0L,
    highlight: Float = 0f,
    /**
     * 被评论的对象：应用的包名，或方案的编号（见 [CommentThreadsSnapshot]）。
     *
     * 给了它，这一层就把上一次画过的那批线程留在进程里，上层还没把网络的那一批交下来（`loading`）
     * 时先把它们摆上；空串表示"这一层不管缓存"，那时一行也不会读、不会写。**不带这个参数的两个
     * 调用点行为完全不变**——缓存只在有人认领这一格、并且说了"还没到"的时候才起作用。
     */
    cacheTarget: String = "",
    /**
     * 上层这一批评论还没到——**不是**"到了，但没有"。
     *
     * 两种情况在参数上都是空列表，而这一层分不开：把"评论被删光了"当成"还没读到"，就会把已经
     * 删掉的那几条又画到屏幕上。所以由页面说清楚这件事：在读网络的那一段置 true，读完（哪怕读完
     * 是空的）就放下。默认 false，也就是"手上的就是最终的"，一个调用点不改都不会变。
     */
    loading: Boolean = false,
) {
    val cacheKey = commentThreadsCacheKey(cacheTarget)
    // 这一批还没到（`loading`）而上一次画过同一个对象的那批还在：先把上次那批判出来占着位置，
    // 上层把网络的那一批交下来时再顶掉。页面自己那份快照在"头一帧"同样是空的，这一层是垫背。
    val drawn = if (loading && threads.isEmpty() && cacheKey != null) {
        ScreenCache.get<CommentThreadsSnapshot>(cacheKey)?.threads ?: threads
    } else {
        threads
    }
    // 画过就记下，下一次进来先拿它垫着。写的是普通 Map 不是 snapshot state，不会反过来触发重组；
    // 而且每次画的都是刚交下来的那一批，所以点赞/删除之后再画一遍，这一格跟着就新了，留不下旧数。
    if (cacheKey != null && threads.isNotEmpty()) {
        ScreenCache.put(cacheKey, CommentThreadsSnapshot(threads))
    }
    drawn.forEachIndexed { index, thread ->
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
                highlight = if (starter.id == highlightId) highlight else 0f,
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
                    highlight = if (answer.id == highlightId) highlight else 0f,
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
                // Open, with more behind it: both toggles share one line — "展开更多" on the left,
                // "收起" right after it. Stacked below instead, "收起" reads as a second, unrelated
                // action under the answers rather than the other half of "this thread is open".
                remaining > 0 -> item(key = "thread-more-$key") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = THREAD_TEXT_INSET, end = 12.dp, top = 1.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThreadToggle(
                            label = "展开更多 $remaining 条",
                            up = false,
                            onClick = { onShowMoreReplies(key) },
                            modifier = Modifier,
                        )
                        Spacer(Modifier.width(10.dp))
                        ThreadToggle(
                            label = "收起",
                            up = true,
                            onClick = { onToggleThread(key) },
                            modifier = Modifier,
                        )
                    }
                }
                // Open and at the end: nothing left to unfold, so "收起" stands alone.
                else -> item(key = "thread-less-$key") {
                    ThreadToggle(label = "收起", up = true, onClick = { onToggleThread(key) })
                }
            }
        }
    }
}

/**
 * "展开 5 条回复" / "展开更多 10 条" / "收起": the one line that moves a thread along.
 *
 * [modifier] is the whole outer padding in the default case — full width, lined up with the thread's
 * text. Callers that put two toggles side by side pass [Modifier] and give the row the insets
 * themselves, so the second one does not start where the first one's text does.
 */
@Composable
private fun ThreadToggle(
    label: String,
    up: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        // Lined up with the comment above it, not with that comment's own answers: the line is
        // about the thread, so it starts where the thread's text does.
        .padding(start = THREAD_TEXT_INSET, end = 12.dp, top = 1.dp, bottom = 5.dp),
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = modifier) {
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
    /**
     * 0f..1f 的闪光强度：从消息页点进来时，目标那一行会闪两下，好让眼睛落在正确的那一行。
     * 0f 时叠上去的背景是全透明的，布局和颜色跟没这个参数时完全一样。
     */
    highlight: Float = 0f,
) {
    val colors = MaterialTheme.colorScheme
    val mine = me.isNotEmpty() &&
        (comment.author == me || comment.author == "#$me" || comment.authorId == me)
    var sheet by remember(comment.id, comment.author, comment.body) { mutableStateOf(false) }
    val openSheet = { sheet = true }
    val highlightColor = colors.primary.copy(alpha = highlight.coerceIn(0f, 1f) * 0.22f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 高亮只是一层背景，画在整行底下（原先行本身没有背景），0f 时不改任何东西。
            .background(color = highlightColor, shape = RoundedCornerShape(12.dp))
            // One gesture pair for the whole row: a thumb lands anywhere on the comment and gets
            // the same two actions, the way a short-video comment list does it. The buttons keep
            // their own taps.
            .combinedClickable(onClick = { onReply(comment) }, onLongClick = openSheet)
            .animateContentSize()
            .padding(start = if (indented) 48.dp else 12.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.clip(CircleShape)) {
            PersonAvatar(
                name = comment.author,
                owner = comment.authorId,
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
                // 名字走 AuthorLabel：还没改名字的人是马赛克 + 红点，改过的人照常显示。
                AuthorLabel(
                    name = comment.author.ifEmpty { "匿名" },
                    // The name is the one step below the comment itself: grey, and quiet enough that
                    // the text under it is what the eye lands on.
                    // The line height is given, not inherited: the ambient body style would leave a
                    // 20sp-tall line box around 11sp type, which is what made the badges beside it
                    // look oversized.
                    style = TextStyle(
                        fontSize = NAME_SIZE,
                        lineHeight = NAME_LINE,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
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

/** 输入栏那颗「发送」的颜色：截图里是抖音那个粉，和表情面板右下角的是同一个。 */
private val CommentSendPink = Color(0xFFFE2C55)

/**
 * 评论输入框的形状：14dp 圆角矩形，不是胶囊。
 *
 * 按截图量的：1080px 宽的画面上，框高约 152px、圆角按左边缘 8px 处下沉 18px 反推约 43px，
 * 43px / 3x ≈ 14dp。抖音那种框是「一块圆角灰板」，做成 percent = 50 的胶囊会跟它不像。
 */
private val CommentFieldShape = RoundedCornerShape(14.dp)

/** 评论输入框的底色：浅色 #F3F3F3、深色 #2C2C2E，都不带描边（截图里框上没有边线）。 */
@Composable
private fun commentFieldFill(): Color =
    if (isSystemInDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFF3F3F3)

/**
 * The composer every comment page ends with: one quiet pill field and the send button. The field
 * asks for a comment on its own — "分享你此刻的想法" — and names the person it answers once
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
    /**
     * 这一条发言的先决条件（名字 + 头像都设过）没满足时的那句话。
     *
     * 非空就是"现在不许发"：发送键灰掉、输入框只读、这句话摆在输入栏上面；空串（默认）等于不设闸，
     * 老调用方一行不用改。判断本身在 [io.github.samolego.canta.util.IdentitySetup] 里，页面只把结果
     * 那句话递进来。
     */
    gateHint: String = "",
) {    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val imeController = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(replyToName) {
        if (replyToName.isNotEmpty() && !pickerOpen) focusRequester.requestFocus()
    }
    // The panel closing from outside — a tap on the page behind it — has to put the keyboard back:
    // a bar with neither the panel nor a caret is a bar the reader cannot type into.
    //
    // Only after it was open, though. A page that ends with this bar opens with the panel closed,
    // and taking that first "closed" as a closing would put the caret in the field the moment the
    // page appears — the input box popped up by itself and the keyboard covered the page the
    // reader had just opened.
    var panelWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(pickerOpen) {
        if (panelWasOpen && !pickerOpen) focusRequester.requestFocus()
        panelWasOpen = pickerOpen
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
                // 名字/头像还没设过：先说清为什么发不出去，再画那条按不动的输入栏。
                // 摆在输入栏上面而不是塞进占位文案里：占位文案只在没字的时候看得见，人一开始打字就不见了。
                if (gateHint.isNotEmpty()) {
                    Text(
                        text = gateHint,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = colors.error,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                }
                // 抖音那套形状：收着的时候（没焦点、没字）整条就是一个胶囊，占位文案在左，
                // 「@ / 表情」两个键贴在它右端，没有发送键；一动笔就换成
                // 「一个空胶囊 + 下面一行工具条」——工具条的键靠左，发送靠右。
                //
                // 没有图片键：评论这一层不收图片（后端只存正文），发了也没地方落。能发图的是群聊，
                // 而且那得群主在群设置里给这个群打开才轮得到；这里摆一颗按不动的相册键就是骗人。
                var fieldFocused by remember { mutableStateOf(false) }
                // 展开的唯一判据是**焦点**（面板开着也算，那是同一次输入里换键盘）。
                //
                // 以前还带一个「有草稿就展开」：点页面空白之后光标没了、键盘收了，但那行工具条
                // 仍旧挂在底下，看着像点空白没反应。抖音那边点哪儿都收，草稿留在框里，再点框就续上。
                val open = fieldFocused || pickerOpen
                val draftKeys: @Composable () -> Unit = {
                    Icon(
                        imageVector = Icons.Outlined.AlternateEmail,
                        contentDescription = "插入 @",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { onDraftChange(draft + "@") }
                            .padding(8.dp),
                        tint = colors.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = if (pickerOpen) {
                            Icons.Outlined.Keyboard
                        } else {
                            Icons.Outlined.EmojiEmotions
                        },
                        contentDescription = if (pickerOpen) "收起表情" else "表情",
                        modifier = Modifier
                            .size(36.dp)
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
                            .padding(8.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
                // 发送：抖音是粉底白字的小胶囊，禁用的时灰底灰字（截图里就是这样），
                // 和表情面板右下角那颗是同一个颜色，两处不能长得不一样。
                val sendKey: @Composable () -> Unit = {
                    val canSend = draft.isNotBlank() && !sending && gateHint.isEmpty()
                    Row(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (canSend) CommentSendPink else inputFieldBorderColor())
                            .clickable(enabled = canSend, onClick = onSend)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = sendLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            color = if (canSend) Color.White else colors.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 输入框：抖音是**浅灰底、无描边、圆角约 14dp 的圆角矩形**，不是白底胶囊。
                    // 截图取色：底色 #F3F3F3，左右各留 32px（约 11dp）贴边，高约 152px（约 50dp），
                    // 圆角按左边缘在 8px 处下沉 18px 反推约 14dp。所以这里不能用 percent=50 的胶囊，
                    // 也不能留一圈边框——灰底自己在浅色页面上就有边界。
                    //
                    // 它会随写的字长高——一行写满就换行、整条变高——上限是一掌的行数，超过就滚动。
                    // 滚动器挂在这个 Box 上而不是字段里：字段本身从不在内部滚，贴纸才能按自己那一行的
                    // 量测盖在它代表的那一个字上面。
                    // It grows with what is written — a comment that runs past the edge of the line
                    // wraps and the bar gets taller — up to a palm's worth of lines; past that the
                    // box stops growing and scrolls. The scroll sits outside the field on purpose:
                    // the field itself is never scrolled, which is what keeps a sticker drawn from
                    // its own line's measurement on top of the character it stands for.
                    val fieldScroll = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            // 一行高最少 48dp（截图里约 50dp），最多一掌。
                            .heightIn(min = 48.dp, max = 140.dp)
                            .clip(CommentFieldShape)
                            .background(commentFieldFill())
                            .clickable { focusRequester.requestFocus() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(fieldScroll)
                                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
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
                                    // 闸门没开就只读：还能看见自己上一条草稿，打不出新的字。
                                    readOnly = gateHint.isNotEmpty(),
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
                                        // The bar changes shape when this field takes the caret: the
                                        // keys leave the pill and move under it. Tracked here, because
                                        // the field is the only thing that knows it got the focus.
                                        .onFocusChanged { fieldFocused = it.isFocused }
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
                                    // stickers are substitutes, and their space draws nothing. The
                                    // line is left to wrap: `singleLine` stays false and the line
                                    // count is unbounded, so a comment that reaches the edge of the
                                    // box continues on the next line instead of running off it.
                                    textStyle = TextStyle(
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp,
                                        color = colors.onSurface,
                                    ),
                                    cursorBrush = SolidColor(colors.primary),
                                    visualTransformation = remember(display) {
                                        EmotionFieldTransformation(display)
                                    },
                                    onTextLayout = { lineLayout = it },
                                    singleLine = false,
                                    maxLines = Int.MAX_VALUE,
                                    minLines = 1,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                                )
                                if (draft.isEmpty()) {
                                    Text(
                                        text = if (replyToName.isNotEmpty()) {
                                            "回复 @$replyToName"
                                        } else {
                                            "分享你此刻的想法"
                                        },
                                        fontSize = 14.sp,
                                        lineHeight = 19.sp,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        // 收着的时候三个键就在胶囊里：草稿在它们左边长，永远不会钻到键下面。
                        if (!open) draftKeys()
                    }
                }
                // 打开了才有的工具条：键靠左、发送靠右，就是截图里那一行。收着的时候没有它，
                // 一整条只有那个胶囊——没有字就没有可发的东西，摆一颗灰按钮只是占地方。
                AnimatedVisibility(visible = open) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        draftKeys()
                        Spacer(Modifier.weight(1f))
                        sendKey()
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
                        // 面板右下角那颗「发送」走同一条发送路径：截图里它一直在，
                        // 没字/正在发的时候画成灰的。
                        onSend = onSend,
                        canSend = draft.isNotBlank() && !sending && gateHint.isEmpty(),
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
            // The glyph stays 12dp but the tap target does not: a 14dp square is unmissable only
            // with a mouse. The chip grows by 6dp to hold it, which is what makes "cancel" reachable
            // on a phone without turning the chip into a button.
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消回复",
                    modifier = Modifier.size(12.dp),
                    tint = colors.primary,
                )
            }
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
    /**
     * 这一行是谁的：这一栏全是自己的评论，所以调用方传自己的身份号，头像就能出照片。
     * 空串就落回首字圆（没见过头像的时候）。
     */
    owner: String = "",
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            // One gesture detector for both: a separate detectTapGestures(onLongPress) swallows
            // the tap it sits next to, which is why the row never opened its page.
            .combinedClickable(onClick = onOpen, onLongClick = onDelete)
            // 通栏一行 + 发丝线，不做卡片：这一页和互动消息那张表是同一种行。
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(
                name = author,
                owner = owner,
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
        // 发丝线画在行内、贴满这一段内容宽度：列表里那一列靠它分出"一条一条"。
        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.outlineVariant.copy(alpha = 0.6f),
        )
    }
}

/**
 * "11-18 06:51"（**北京时间**）out of a Postgres timestamp。
 *
 * 以前这里是切字符串（`substring(5,10)` + `substring(11,16)`），切出来的是 UTC 的钟点；现在统一走
 * [BeijingTime.stamp]。解析不了就退回原来的日期切片，不把这一格留成空白。
 */
fun shortStamp(iso: String): String {
    return BeijingTime.stamp(iso).ifEmpty { iso.take(10) }
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
    zone: TimeZone = BeijingTime.zone,
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
    // 一年以上退回 "MM-dd HH:mm"：**按调用方给的 [zone]**（界面上是北京时间），而不是走
    // [shortStamp] 那条固定北京时间的捷径——测试把 zone 钉成 UTC 来验这条兜底，两者不一致就会
    // 差出八小时。这也让"传入的 zone"这个参数在最后一段分支上真的生效。
    val local = parsed.toLocalDateTime(zone)
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return local.date.toString().substring(5) + " " + hour + ":" + minute
}
