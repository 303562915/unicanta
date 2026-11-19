package io.github.samolego.canta.ui.fl

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One choice of a segmented control. */
data class FlSegment(val key: String, val label: String, val glyph: FlGlyph? = null)

/** A pill: the track and its thumb both round to half their height. */
private val Stadium = RoundedCornerShape(percent = 50)

private val SegmentHeight = 38.dp
private val ThumbInset = 4.dp

/**
 * FlClash's segmented control: a groove on the page, with the selected choice on a thumb
 * that slides between slots, casts a soft shadow, and carries the emphasis of its label.
 * A switch between two lists reads as one control instead of two underlined tabs.
 *
 * [position] is where the thumb stands in slots, so a swipe that is halfway between two
 * choices leaves the thumb halfway too; it defaults to [selectedIndex], for a control
 * with no gesture to follow.
 *
 * [onImage] is for a control drawn over a picture (the phone hero on the safety page): the groove
 * turns into a dark translucent band and the thumb a light translucent one, so the photo stays
 * visible through the control instead of being cut by an opaque pill.
 */
@Composable
fun FlSegmentedControl(
    segments: List<FlSegment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    position: Float = selectedIndex.toFloat(),
    onImage: Boolean = false,
    /** Side inset of the groove; 0 when the caller already keeps the control off the edges. */
    insetHorizontal: Dp = 16.dp,
) {
    if (segments.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDark
    // A groove under the page, and a thumb lifted out of it with a shadow.
    val track = when {
        onImage -> Color.Black.copy(alpha = 0.32f)
        dark -> scheme.surfaceContainerLow
        else -> scheme.surfaceContainerHighest
    }
    val thumb = when {
        onImage -> Color.White.copy(alpha = 0.92f)
        dark -> scheme.surfaceContainerHigh
        else -> scheme.surfaceBright
    }
    // Over a photo the labels sit on their own dark band, so they are always light.
    val selectedTint = if (onImage) Color.Black else scheme.onSurface
    val unselectedTint = if (onImage) Color.White.copy(alpha = 0.85f) else scheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = insetHorizontal, vertical = 8.dp)
            .height(SegmentHeight)
            .drawBehind {
                drawRoundRect(
                    color = track,
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val slot: Dp = maxWidth / segments.size
            Box(
                modifier = Modifier
                    .offset(
                        x = slot * position.coerceIn(0f, (segments.size - 1).toFloat()) + ThumbInset,
                        y = ThumbInset,
                    )
                    .width(slot - ThumbInset * 2)
                    .height(SegmentHeight - ThumbInset * 2)
                    .shadow(elevation = 5.dp, shape = Stadium, clip = false)
                    .clip(Stadium)
                    .background(thumb),
            )
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            segments.forEachIndexed { index, segment ->
                val selected = index == selectedIndex
                val color by animateColorAsState(
                    targetValue = if (selected) selectedTint else unselectedTint,
                    label = "segment-tint",
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .semantics(mergeDescendants = true) { this.selected = selected }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val glyph = segment.glyph
                    if (glyph != null) {
                        FlGlyphIcon(
                            glyph = glyph,
                            size = 17.dp,
                            tint = color,
                            fill = if (selected) 1f else 0f,
                        )
                        Box(Modifier.size(6.dp))
                    }
                    Text(
                        text = segment.label,
                        style = FlText.footnote,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = color,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
