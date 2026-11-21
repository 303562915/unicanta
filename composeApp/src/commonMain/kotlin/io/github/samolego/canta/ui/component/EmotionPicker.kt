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
import androidx.compose.material.icons.filled.Keyboard
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

/** How many stickers the 常用 row remembers: one row of thumbs, not a second grid. */
private const val RECENT_LIMIT = 12

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
internal fun rememberEmotionDocument(spans: List<EmotionSpan>): EmotionDocument {
    val inline = remember(spans) {
        spans.mapIndexedNotNull { index, span ->
            if (span !is EmotionSpan.Sticker) return@mapIndexedNotNull null
            EmotionInlineId(index) to androidx.compose.foundation.text.InlineTextContent(
                // A placeholder is measured in text units, not dp: it stands where a character of
                // the paragraph would, so the line it is on keeps its height.
                placeholder = Placeholder(
                    width = EmotionInlineSizeSp,
                    height = EmotionInlineSizeSp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                Image(
                    painter = painterResource(span.emotion.resource),
                    contentDescription = span.emotion.name,
                    modifier = Modifier.size(EmotionInlineSize),
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
) {
    val spans = remember(body) { parseEmotionBody(body) }
    if (spans.none { it is EmotionSpan.Sticker }) {
        Text(
            text = body,
            fontSize = fontSize.sp,
            lineHeight = lineHeight.sp,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            modifier = modifier,
        )
        return
    }
    val document = rememberEmotionDocument(spans)
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
 * The sticker panel the comment bar opens: the last ones picked on top, everything below.
 *
 * A panel rather than a dialog because it belongs to the field it types into — it sits under the
 * composer, the keyboard stays down, and a tap puts the sticker in the draft at once. It also ends
 * with the two keys that only exist while it is open: a backspace that takes one sticker out, and
 * the way back to the keyboard.
 */
@Composable
fun EmotionPicker(
    onPick: (Emotion) -> Unit,
    onDelete: () -> Unit,
    onKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * How tall the grid gets. The caller passes a share of the room it has rather than a constant:
     * the panel sits under the field in the same column, and a fixed height would push the field
     * off the top of a short screen — which is the bug that made the input box disappear.
     */
    panelHeight: androidx.compose.ui.unit.Dp = 236.dp,
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
            SectionLabel("最常使用", top = 10.dp)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(recent, key = { "recent-$it" }) { key ->
                    val emotion = emotionOf(key)
                    if (emotion != null) EmotionCell(emotion = emotion, onPick = { onPick(emotion) })
                }
            }
        }
        SectionLabel("全部表情", top = if (recent.isEmpty()) 10.dp else 6.dp)
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            gridItems(EMOTIONS, key = { it.key }) { emotion ->
                EmotionCell(emotion = emotion, onPick = { onPick(emotion) })
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "退格",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onKeyboard)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "键盘",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** "最常使用" / "全部表情": the two headings the panel is split by. */
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
