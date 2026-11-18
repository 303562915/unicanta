package io.github.samolego.canta.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = MaterialTheme.colorScheme.shadow.copy(alpha = 0.28f),
                spotColor = MaterialTheme.colorScheme.shadow.copy(alpha = 0.34f),
            )
            .background(containerColor, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
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
    Row(
        modifier = Modifier
            // The selected pill is a soft tonal block, not an underline: same idea as the
            // floating capsules in FlClash's dashboard.
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
            .padding(horizontal = if (item.selected) 16.dp else 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (item.selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(22.dp),
        )
        AnimatedVisibility(
            visible = item.selected,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Text(
                text = item.label,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp).widthIn(max = 120.dp),
            )
        }
    }
}
