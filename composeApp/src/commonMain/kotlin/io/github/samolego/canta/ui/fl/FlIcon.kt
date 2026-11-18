package io.github.samolego.canta.ui.fl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One icon of the FlClash set. [fill] runs from 0, the outlined form FlClash uses in a
 * bar, to 1, the filled form it uses in a dock.
 */
@Composable
fun FlGlyphIcon(
    glyph: FlGlyph,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    fill: Float = 0f,
) {
    Canvas(modifier = modifier.size(size)) {
        drawGlyph(glyph, fill, tint)
    }
}

/**
 * A button in a bar: a circle filled with the surface above the page, and a press that
 * swells it, which is how FlClash's `ElasticPress` answers a finger.
 */
@Composable
fun FlBarButton(
    glyph: FlGlyph,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    selected: Boolean = false,
    filled: Boolean = true,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val background = FlSurfaceLevel.Page.raised.colorOf(scheme)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "bar-button-press",
    )
    val tint = if (selected) scheme.primary else scheme.onSurface

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .scale(pressScale)
            .clip(CircleShape)
            .then(if (filled) Modifier.drawBehind { drawCircle(background) } else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        FlGlyphIcon(
            glyph = glyph,
            size = iconSize,
            tint = tint,
            fill = if (selected) 1f else 0f,
        )
    }
}
