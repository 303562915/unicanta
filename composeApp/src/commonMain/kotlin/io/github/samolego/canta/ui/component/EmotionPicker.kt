package io.github.samolego.canta.ui.component

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource

/**
 * The last few stickers this install picked, newest first.
 *
 * Keys rather than pictures: the pictures live in [EMOTIONS], and what a thumb needs back is the
 * order it left them in. It is kept in the process for now — a sticker history is a convenience,
 * not something worth a storage round trip on every launch — and until something has been picked
 * the picker simply shows its one grid.
 */
private val recentEmotions = mutableStateListOf<String>()

/** How many stickers the 常用 row remembers: one row of eight, filling the panel's width once. */
private const val RECENT_LIMIT = 8

/** Remembers that [key] was picked, moving it to the front of the 常用 row. */
fun rememberEmotionPick(key: String) {
    recentEmotions.remove(key)
    recentEmotions.add(0, key)
    while (recentEmotions.size > RECENT_LIMIT) {
        recentEmotions.removeAt(recentEmotions.lastIndex)
    }
}

/** One run of a comment body: words, or one sticker standing where its key was written. */
sealed interface EmotionSpan {
    data class Words(val text: String) : EmotionSpan
    data class Sticker(val emotion: Emotion) : EmotionSpan
}

/**
 * Splits a comment body into the words it is and the stickers it carries.
 *
 * A sticker is written as `[weixiao]` in the body, which is what the composer inserts and what the
 * backend stores. Everything between two such tokens is words, including a bracket that names
 * nothing: `[not_a_sticker]` stays on screen as typed rather than vanishing, because a comment that
 * silently loses characters is worse than one showing its own syntax.
 */
fun parseEmotionBody(body: String): List<EmotionSpan> {
    val spans = mutableListOf<EmotionSpan>()
    val words = StringBuilder()
    var index = 0
    while (index < body.length) {
        val open = body.indexOf('[', index)
        if (open < 0) {
            words.append(body, index, body.length)
            break
        }
        val close = body.indexOf(']', open + 1)
        if (close < 0) {
            words.append(body, index, body.length)
            break
        }
        val emotion = emotionOf(body.substring(open + 1, close))
        if (emotion == null) {
            // Not a sticker: keep the opening bracket as text and carry on from just after it, so
            // "[a][b]" and "[]]" both survive intact.
            words.append(body, index, open + 1)
            index = open + 1
            continue
        }
        if (open > index) words.append(body, index, open)
        if (words.isNotEmpty()) {
            spans += EmotionSpan.Words(words.toString())
            words.clear()
        }
        spans += EmotionSpan.Sticker(emotion)
        index = close + 1
    }
    if (words.isNotEmpty()) spans += EmotionSpan.Words(words.toString())
    return spans
}

/** The id one sticker occurrence is drawn under: its span index, not its name — a body may carry the
 * same sticker twice, and two inline contents cannot share an id. */
internal fun EmotionInlineId(index: Int): String = "emotion-$index"

/**
 * The body as text with its stickers replaced by inline pictures: what every place that shows a
 * comment draws, from the thread to the field it is typed into.
 *
 * The tokens are what the string carries — [an annotated string] keeps them as the placeholder's
 * alternative text, and the caller that needs the tokens back (an editor) reads them from there.
 */
@Composable
internal fun rememberEmotionDocument(
    spans: List<EmotionSpan>,
    /**
     * 图片放大到几倍。1f 是正文里的样子（一个字符高）；聊天气泡里「只有表情」的那条传 3f，
     * 一张 28 的画面读得出是哪个表情，和微信/抖音一致。
     */
    scale: Float = 1f,
): EmotionDocument {
    val inline = remember(spans, scale) {
        spans.mapIndexedNotNull { index, span ->
            if (span !is EmotionSpan.Sticker) return@mapIndexedNotNull null
            EmotionInlineId(index) to androidx.compose.foundation.text.InlineTextContent(
                // A placeholder is measured in text units, not dp: it stands where a character of
                // the paragraph would, so the line it is on keeps its height.
                placeholder = Placeholder(
                    width = EmotionInlineSizeSp * scale,
                    height = EmotionInlineSizeSp * scale,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                Image(
                    painter = painterResource(span.emotion.resource),
                    contentDescription = span.emotion.name,
                    modifier = Modifier.size(EmotionInlineSize * scale),
                )
            }
        }.toMap()
    }
    val annotated = remember(spans) {
        buildAnnotatedString {
            spans.forEachIndexed { index, span ->
                when (span) {
                    is EmotionSpan.Words -> append(span.text)
                    is EmotionSpan.Sticker -> appendInlineContent(EmotionInlineId(index), span.emotion.token)
                }
            }
        }
    }
    return EmotionDocument(annotated = annotated, inline = inline)
}

/** What [rememberEmotionDocument] hands back: the paragraph, and the pictures it refers to. */
internal data class EmotionDocument(
    val annotated: AnnotatedString,
    val inline: Map<String, androidx.compose.foundation.text.InlineTextContent>,
)

/**
 * 正文里被点到的那一截：`@` 开头，一直到空白或下一个 `@` 为止。
 *
 * 只认形态不认名单：正文里的名字是发的人当时打的那几个字，可能早改过了，也可能根本不是群里
 * 任何一个人（手动打的 `@`）。标色只是让它在一段话里显眼，确认"点到了谁"是后端名单的事。
 */
private val MentionPattern = Regex("@[^\\s@]+")

/** 把 [body] 里的 `@某某` 上成 [color]，其余原样。 */
private fun mentionStyled(body: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(body)
    MentionPattern.findAll(body).forEach { hit ->
        addStyle(
            SpanStyle(color = color, fontWeight = FontWeight.SemiBold),
            hit.range.first,
            hit.range.last + 1,
        )
    }
}

/**
 * A comment body with its stickers drawn as pictures.
 *
 * Text and stickers live in one annotated string rather than a row of views: a paragraph wraps, and
 * a row of images beside a line of text would not — the third sticker in a sentence would run off
 * the edge instead of moving to the next line. A body with no sticker in it takes the plain path,
 * so ordinary text keeps whatever line breaking it had before.
 */
@Composable
fun EmotionText(
    body: String,
    fontSize: Int = 13,
    lineHeight: Int = 19,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
    /**
     * 正文里 `@某某` 那一截用什么颜色标出来；null 表示这段正文不做这个处理。
     *
     * 只作用于没有表情图的那一路：一段话里同时有表情和 @ 时，表情那一路要按 inline 占位符的
     * 位置排版，为一个颜色去动它不划算——而且那种消息本来也不常见，@ 少一个颜色不是错。
     */
    mentionColor: Color? = null,
    /**
     * 表情图放大到几倍。1f = 正文大小；聊天气泡里「整条只有表情」的那种传 3f，
     * 一张表情才会被当成图看，而不是被当成一个字符。
     */
    stickerScale: Float = 1f,
) {
    val spans = remember(body) { parseEmotionBody(body) }
    if (spans.none { it is EmotionSpan.Sticker }) {
        Text(
            text = if (mentionColor == null) {
                AnnotatedString(body)
            } else {
                mentionStyled(body, mentionColor)
            },
            fontSize = fontSize.sp,
            lineHeight = lineHeight.sp,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            modifier = modifier,
        )
        return
    }
    val document = rememberEmotionDocument(spans, stickerScale)
    Text(
        text = document.annotated,
        inlineContent = document.inline,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        modifier = modifier,
    )
}

/**
 * The character a sticker stands for inside the composer.
 *
 * A composer is a text field, and a text field cannot draw a picture on its own line. Given the
 * token `[偷笑]` it measures five characters, so a caret placed after the sticker lands five
 * characters to the right of a picture one glyph wide — "the caret runs off" is exactly that.
 * Substituting one ideographic space per sticker makes the field measure one picture wide, and the
 * picture is drawn into that measurement, so the caret comes down against its right edge.
 *
 * A space rather than a more descriptive replacement character because its advance is the one thing
 * that is predictable: an ideographic space is one em in every font that carries one.
 */
internal const val EmotionFieldCell: Char = '\u3000'

/**
 * The em the substituted space is given, and therefore the side of the square the sticker is drawn
 * in. A little larger than the line's own text so the sticker reads as a picture at text size.
 */
internal val EmotionFieldCellSize = 18.sp

/** One sticker in the composer's line: which cell of the displayed text it is, and what it shows. */
internal data class EmotionFieldCellPlace(val index: Int, val emotion: Emotion)

/**
 * What the composer's field is laid out from: the body with every sticker replaced by one cell, the
 * cells' positions, and the map that keeps the caret and the selection on the body's own offsets.
 */
internal data class EmotionFieldDisplay(
    /** The body this was made from, so a field reporting the same text can reuse it. */
    val body: String,
    val shown: AnnotatedString,
    val cells: List<EmotionFieldCellPlace>,
    val mapping: OffsetMapping,
)

/**
 * Builds the composer's line out of a body.
 *
 * The line is the body with each sticker standing as one [EmotionFieldCell]; everything else is the
 * body's own words, so the field's measurement of a sticker no longer depends on how long its token
 * happens to be. Offsets stay the body's: [EmotionFieldDisplay.mapping] answers both directions, and
 * a caret inside a token's range is pulled to the nearer edge of the cell, because there is no room
 * in the picture to put a caret in.
 */
internal fun emotionFieldDisplayOf(body: String): EmotionFieldDisplay {
    val spans = parseEmotionBody(body)
    val builder = AnnotatedString.Builder()
    val cells = mutableListOf<EmotionFieldCellPlace>()
    val bodyStarts = IntArray(spans.size)
    val bodyLengths = IntArray(spans.size)
    val shownStarts = IntArray(spans.size)
    val shownLengths = IntArray(spans.size)
    var bodyAt = 0
    var shownAt = 0
    spans.forEachIndexed { index, span ->
        val words = (span as? EmotionSpan.Words)?.text
        val sticker = span as? EmotionSpan.Sticker
        val length = words?.length ?: sticker!!.emotion.token.length
        bodyStarts[index] = bodyAt
        bodyLengths[index] = length
        shownStarts[index] = shownAt
        if (words != null) {
            builder.append(words)
            shownLengths[index] = words.length
            shownAt += words.length
        } else {
            builder.append(EmotionFieldCell.toString())
            builder.addStyle(SpanStyle(fontSize = EmotionFieldCellSize), shownAt, shownAt + 1)
            cells += EmotionFieldCellPlace(shownAt, sticker!!.emotion)
            shownLengths[index] = 1
            shownAt += 1
        }
        bodyAt += length
    }
    val shownLength = shownAt
    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            val at = offset.coerceIn(0, body.length)
            spans.forEachIndexed { index, span ->
                val start = bodyStarts[index]
                val end = start + bodyLengths[index]
                if (at < start) return shownStarts[index]
                when (span) {
                    is EmotionSpan.Words -> if (at <= end) return shownStarts[index] + (at - start)
                    is EmotionSpan.Sticker -> if (at < end) {
                        // 光标落在 token 里时贴到图片的一侧：前半在左，后半在右。
                        val half = bodyLengths[index] / 2
                        return if (at - start <= half) shownStarts[index] else shownStarts[index] + 1
                    }
                }
            }
            return shownLength
        }

        override fun transformedToOriginal(offset: Int): Int {
            val at = offset.coerceIn(0, shownLength)
            spans.forEachIndexed { index, span ->
                val start = shownStarts[index]
                val end = start + shownLengths[index]
                if (at < start) return bodyStarts[index]
                when (span) {
                    is EmotionSpan.Words -> if (at <= end) return bodyStarts[index] + (at - start)
                    is EmotionSpan.Sticker -> {
                        if (at <= start) return bodyStarts[index]
                        if (at == end) return bodyStarts[index] + bodyLengths[index]
                    }
                }
            }
            return body.length
        }
    }
    return EmotionFieldDisplay(
        body = body,
        shown = builder.toAnnotatedString(),
        cells = cells,
        mapping = mapping,
    )
}

/**
 * Hands the field the cell text and the map back to the body's offsets.
 *
 * The field keeps editing the body itself: a keystroke, a paste and a backspace are all reported as
 * the token text, not as what is on the line, so nothing here has to translate an edit back. Only
 * drawing and caret placement ever see the cells.
 */
internal class EmotionFieldTransformation(
    private val display: EmotionFieldDisplay,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // An IME still composing hands over a text that is longer than the draft; measure that one,
        // so no offset in the map ever points past the text it is used with.
        val current = if (text.text == display.body) display else emotionFieldDisplayOf(text.text)
        return TransformedText(current.shown, current.mapping)
    }
}

/**
 * The sticker panel the composer opens: the last ones picked on top, everything below.
 *
 * A panel rather than a dialog because it belongs to the field it types into — it sits under the
 * composer, the keyboard stays down, and a tap puts the sticker in the draft at once. The two keys
 * that only exist while it is open float in the grid's bottom-right corner: a round × that takes one
 * sticker out, and the send key the caller hands in.
 *
 * [onKeyboard] is kept for the callers that still own a keyboard button of their own; the panel
 * itself no longer draws one — tapping the field is the way back to the keyboard (see the chat
 * screen's focus handling), and a panel that looks like the one in the screenshots has no such row.
 */
@Composable
fun EmotionPicker(
    onPick: (Emotion) -> Unit,
    onDelete: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 网格右下角那颗粉色「发送」要走的回调；null 时这一格不画（评论区那种没有发送动作的地方）。
     *
     * 走的是调用方自己那个 send，不要在这里另开一条发送路径：引用、@ 名单、失败重发的语义
     * 都在那一侧，面板里再抄一份迟早会跟输入栏对不上。
     */
    onSend: (() -> Unit)? = null,
    /** 发送键能不能按：草稿为空或者正在发的时候画成禁用色，按下去不动作。 */
    canSend: Boolean = true,
    /**
     * How tall the grid gets. The caller passes a share of the room it has rather than a constant:
     * the panel sits under the field in the same column, and a fixed height would push the field
     * off the top of a short screen — which is the bug that made the input box disappear.
     */
    panelHeight: androidx.compose.ui.unit.Dp = 300.dp,
) {
    val colors = MaterialTheme.colorScheme
    val recent = recentEmotions.toList()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight)
            .background(colors.surfaceContainerLow),
    ) {
        if (recent.isNotEmpty()) {
            SectionHeader(title = "最近使用", action = "装扮 >")
            // 一行八格正好铺满：最近那颗在最左，不横向滚动（横滑的话第八颗以后永远看不见）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                recent.forEach { key ->
                    val emotion = emotionOf(key)
                    if (emotion == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmotionCell(emotion = emotion, onPick = { onPick(emotion) })
                        }
                    }
                }
                // 不满八颗时把剩下的格子占住，否则前几颗会被拉成一样大的巨图。
                repeat((RECENT_LIMIT - recent.size).coerceAtLeast(0)) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        SectionLabel("全部表情", top = if (recent.isEmpty()) 10.dp else 6.dp)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.fillMaxSize(),
                // 底部空出两个浮动钮的高度：最后一行不会被它们压住。
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                gridItems(EMOTIONS, key = { it.key }) { emotion ->
                    EmotionCell(emotion = emotion, onPick = { onPick(emotion) })
                }
            }
            // 右下角那两个浮动钮：一个是删掉最后一颗，一个是把草稿发出去。
            // 删的是一整颗 token，不是半个 "[偷"——见 dropLastSticker。
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.surfaceContainerHighest)
                        .clickable(onClickLabel = "删掉最后一颗表情", onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "删掉最后一颗表情",
                        modifier = Modifier.size(15.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
                if (onSend != null) {
                    Text(
                        text = "发送",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (canSend) Color.White else colors.outline,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (canSend) EmotionSendPink else colors.surfaceContainerHighest,
                            )
                            .clickable(
                                enabled = canSend,
                                onClickLabel = "发送",
                                onClick = onSend,
                            )
                            .padding(horizontal = 20.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

/** 表情面板右下角那颗「发送」的颜色：截图里是粉的，不跟主题走。 */
private val EmotionSendPink = Color(0xFFFE2C55)

/** 「最近使用 + 装扮 >」那样的一行：左边标题，右边一个灰字入口。 */
@Composable
private fun SectionHeader(title: String, action: String?) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, fontSize = 11.sp, lineHeight = 14.sp, color = colors.outline)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(text = action, fontSize = 11.sp, lineHeight = 14.sp, color = colors.outline)
        }
    }
}

/** "最近使用" / "全部表情": the two headings the panel is split by. */
@Composable
private fun SectionLabel(text: String, top: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(start = 14.dp, top = top, bottom = 4.dp),
    )
}

/** One sticker in a grid: the picture, and nothing else — a name under it would double the row. */
@Composable
private fun EmotionCell(emotion: Emotion, onPick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(emotion.resource),
            contentDescription = emotion.name,
            modifier = Modifier.size(30.dp),
        )
    }
}
