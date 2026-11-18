package io.github.samolego.canta.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One destination of the floating bottom bar. */
data class BottomBarItem(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
)

/**
 * The floating capsule bar: detached from the screen edges, translucent over the content,
 * with a soft wide shadow and a pill that wraps the selected item. The container colour comes
 * from the theme, so light and dark themes both keep the frosted look.
 */
@Composable
fun FloatingPillBar(
    items: List<BottomBarItem>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = Color.Transparent,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .background(containerColor, shape)
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            BottomBarEntry(item = item, onClick = { onSelect(item.key) }, shape = shape)
        }
    }
}

@Composable
private fun BottomBarEntry(
    item: BottomBarItem,
    onClick: () -> Unit,
    shape: Shape,
) {
    val interaction = remember { MutableInteractionSource() }
    val alpha by animateFloatAsState(if (item.selected) 1f else 0.72f, label = "barItemAlpha")
    Column(
        modifier = Modifier
            // Only the selected entry gets a soft pill; the bar itself stays invisible so the
            // content scrolls underneath it.
            .background(
                color = if (item.selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                shape = shape,
            )
            .selectable(
                selected = item.selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = if (item.selected) 14.dp else 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (item.selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(20.dp),
        )

        // Every label stays visible: an icon alone makes people guess which tab they are on.
        Text(
            text = item.label,
            color = if (item.selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 2.dp).widthIn(max = 78.dp),
            textAlign = TextAlign.Center,
        )
    }
}
