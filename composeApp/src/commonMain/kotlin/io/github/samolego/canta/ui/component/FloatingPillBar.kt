package io.github.samolego.canta.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One destination of the floating bottom bar. */
data class BottomBarItem(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
)

/**
 * Heights and paddings follow the dock the FlClash client uses (62 dp bar, 4 dp inner padding,
 * 21 dp side margin, 86 dp maximum item width): four equal slots come out at roughly that width,
 * which is what makes the selected pill feel generous instead of cramped.
 */
private val BarHeight = 62.dp
private val BarPadding = 4.dp
private val EdgeMargin = 21.dp
private val IconSize = 22.dp
private val LabelSize = 10.sp

/**
 * The floating capsule bar: detached from the screen edges, translucent over the content, with a
 * soft wide shadow and a pill that fills the selected slot. Only the selected entry is tinted; the
 * bar itself stays glassy so content shows through.
 */
@Composable
fun FloatingPillBar(
    items: List<BottomBarItem>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = EdgeMargin, vertical = 10.dp)
            .fillMaxWidth()
            // Frosted capsule: a soft drop shadow lifts it off the content, a translucent fill lets
            // the content show through, and a vertical highlight along the edge reads as glass.
            .shadow(elevation = 16.dp, shape = shape, clip = false)
            .background(containerColor, shape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f)),
                ),
                shape = shape,
            )
            .padding(BarPadding)
            .height(BarHeight - BarPadding * 2)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            BottomBarEntry(
                item = item,
                onClick = { onSelect(item.key) },
                shape = shape,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RowScope.BottomBarEntry(
    item: BottomBarItem,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val alpha by animateFloatAsState(if (item.selected) 1f else 0.75f, label = "barItemAlpha")
    val pill = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(pill)
            // The selected slot is a wide tonal pill, not an underline: the label sits inside it.
            .background(
                color = if (item.selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                shape = pill,
            )
            .selectable(
                selected = item.selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (item.selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(IconSize),
        )
        // Every label stays visible: an icon alone makes people guess which tab they are on.
        Text(
            text = item.label,
            color = if (item.selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            fontSize = LabelSize,
            lineHeight = LabelSize,
            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
