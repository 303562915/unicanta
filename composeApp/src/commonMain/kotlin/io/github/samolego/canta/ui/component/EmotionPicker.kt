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
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource

/** One sticker's side inside a comment: the line it sits on is 19sp tall, so it fits with room. */
private val InlineStickerSize = 17.dp

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
    // Inline content is keyed per sticker occurrence, and the same sticker may appear twice in one
    // body, so the ids are span indices rather than keys.
    val inline = mutableMapOf<String, androidx.compose.foundation.text.InlineTextContent>()
    val annotated = buildAnnotatedString {
        spans.forEachIndexed { index, span ->
            when (span) {
                is EmotionSpan.Words -> append(span.text)
                is EmotionSpan.Sticker -> {
                    val id = "emotion-$index"
                    inline[id] = androidx.compose.foundation.text.InlineTextContent(
                        placeholder = Placeholder(
                            width = InlineStickerSize,
                            height = InlineStickerSize,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                        ),
                    ) {
                        Image(
                            painter = painterResource(span.emotion.resource),
                            contentDescription = span.emotion.key,
                            modifier = Modifier.size(InlineStickerSize),
                        )
                    }
                    appendInlineContent(id, span.emotion.token)
                }
            }
        }
    }
    Text(
        text = annotated,
        inlineContent = inline,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        modifier = modifier,
    )
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
) {
    val colors = MaterialTheme.colorScheme
    val recent = recentEmotions.toList()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(236.dp)
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
            contentDescription = emotion.key,
            modifier = Modifier.size(30.dp),
        )
    }
}
