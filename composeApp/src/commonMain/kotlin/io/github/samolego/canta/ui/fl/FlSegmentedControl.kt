package io.github.samolego.canta.ui.fl

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** One choice of a segmented control. */
data class FlSegment(val key: String, val label: String, val glyph: FlGlyph? = null)

/**
 * FlClash's segmented control: a track on the raised surface with the selected choice
 * drawn on a thumb that slides, not an underline. A page's switch between two lists
 * reads as one of these.
 */
@Composable
fun FlSegmentedControl(
    segments: List<FlSegment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val track = FlSurfaceLevel.Page.raised.colorOf(scheme)
    val thumb = FlSurfaceLevel.Page.colorOf(scheme)
    val shape = RoundedCornerShape(percent = 50)
    // The thumb springs between choices, so a tap reads as one movement.
    val position by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "segment-thumb",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(38.dp)
            .clip(shape)
            .drawBehind {
                drawRect(track)
                val count = segments.size.toFloat()
                val slot = size.width / count
                val inset = 3.dp.toPx()
                val left = position * slot + inset
                drawRoundRect(
                    color = thumb,
                    topLeft = Offset(left, inset),
                    size = Size(slot - inset * 2f, size.height - inset * 2f),
                    cornerRadius = CornerRadius((size.height - inset * 2f) / 2f),
                )
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(38.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            segments.forEachIndexed { index, segment ->
                val selected = index == selectedIndex
                val color by animateColorAsState(
                    targetValue = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                    label = "segment-tint",
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(38.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (segment.glyph != null) {
                        FlGlyphIcon(
                            glyph = segment.glyph,
                            size = 18.dp,
                            tint = color,
                            fill = if (selected) 1f else 0f,
                        )
                        Box(Modifier.padding(start = 6.dp))
                    }
                    Text(
                        text = segment.label,
                        style = FlText.footnote.copy(
                            fontWeight = if (selected) FlText.emphasized else null,
                        ),
                        color = color,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A label drawn in the color a status uses, for a caption that has to stand out. */
@Composable
fun FlStatusText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = FlText.footnote,
        color = color,
        maxLines = 1,
    )
}
