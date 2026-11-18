package io.github.samolego.canta.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign

/** One destination of the floating bottom bar. */
data class BottomBarItem(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
)

// Geometry of the dock FlClash draws on every mobile view (prism_ui's
// NavigationDock): 62 dp bar, 4 dp inner padding, 21 dp side margin, 86 dp
// maximum slot, an 8 dp band above the bar for its drop shadow, a 28 dp glyph
// over a 10 sp label, and a lens that overhangs its slot by the inner padding.
private val BarHeight = 62.dp
private val BarPadding = 4.dp
private val EdgeMargin = 21.dp
private val ShadowRoom = 8.dp
private val IconSize = 28.dp
private val LabelSize = 10.sp
private val LabelLine = 14.5.sp
private val LabelGap = 2.dp
private val LabelInset = 2.dp
private const val MaxItemExtent = 86f
private const val MinLensWidth = 84f
private const val FullWidthCount = 5
private const val MinLabelScale = 0.9f

// Lens motion: 8 dp of growth on every side while it is lifted, and a stretch
// along the travel that reaches 20 % at three slots per second.
private const val LensGrowth = 8f
private const val JellyStretch = 0.2f
private const val JellySpeed = 3f
private const val JellyAspect = 1.4f
private const val Overdrag = 0.35f

// FlClash's springs, converted from their durations and bounces.
private val TravelSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 247f)
private val LiftSpring = spring<Float>(dampingRatio = 1f, stiffness = 632f)
private val PressSpring = spring<Float>(dampingRatio = 0.593f, stiffness = 311f)

/** A pill: the bar and its lens both round to half their shortest side. */
private val Stadium: Shape = RoundedCornerShape(percent = 50)

/** Where the destinations sit inside the bar, in pixels. */
private data class DockLayout(val pitch: Float, val lens: Float, val inset: Float)

/**
 * Spaces the destinations as the iOS tab bar does: the resting lens overhangs
 * its slot by the inner padding and never narrows below [MinLensWidth] px.
 */
private fun dockLayout(span: Float, count: Int, pad: Float, minLens: Float): DockLayout {
    if (count <= 1) {
        return DockLayout(pitch = span - pad * 2, lens = span, inset = pad)
    }
    val pitch = (span - pad * 2) / count
    if (pitch + pad * 2 >= minLens) {
        return DockLayout(pitch = pitch, lens = pitch + pad * 2, inset = pad)
    }
    val lens = minOf(minLens, span)
    val closed = (span - lens) / (count - 1)
    return DockLayout(pitch = closed, lens = lens, inset = (lens - closed) / 2)
}

/** The slot under [x], as a fraction: 0 at the first destination's centre. */
private fun dockPositionAt(x: Float, layout: DockLayout, lastIndex: Int): Float {
    val position = (x - layout.inset) / layout.pitch - 0.5f
    return when {
        position < 0f -> rubberBand(position, Overdrag)
        position > lastIndex -> lastIndex + rubberBand(position - lastIndex, Overdrag)
        else -> position
    }
}

private fun dockIndexAt(position: Float, lastIndex: Int): Int =
    position.roundToInt().coerceIn(0, lastIndex)

/** Pulls harder the further the finger goes, never past [limit]. */
private fun rubberBand(overshoot: Float, limit: Float): Float {
    val pull = 1f - 1f / (abs(overshoot) * 0.55f / limit + 1f)
    return limit * pull * sign(overshoot)
}

private fun saturate(value: Float): Float = 1f - 2f / (exp(2f * value) + 1f)

/** How far the lens stretches at its current speed, in fractions of its width. */
private fun lensStretch(lens: Animatable<Float>, pitch: Float): Float {
    if (pitch <= 0f) {
        return 0f
    }
    return JellyStretch * saturate(abs(lens.velocity) / pitch / JellySpeed)
}

/**
 * The floating capsule bar FlClash puts over its content: a translucent-looking
 * dock detached from every edge, whose selection is a lens that springs between
 * destinations, swells under a press, follows a dragged finger across the bar,
 * and swells the whole bar while it is held.
 *
 * Presses are read as raw pointers rather than per-item clickables, as FlClash
 * does: a tap has nothing to win from a gesture arena, and the lens can start
 * travelling on touch-down instead of on release. Each destination still exposes
 * a tab role to accessibility services.
 */
@Composable
fun FloatingPillBar(
    items: List<BottomBarItem>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        return
    }

    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    // One surface step above the content, and one more for the lens.
    val barColor = if (dark) colors.surfaceContainerHigh else colors.surfaceBright
    val lensColor = if (dark) colors.surfaceContainerHighest else colors.surfaceContainer
    val shadowColor = colors.shadow

    val density = LocalDensity.current
    val bottomMargin = maxOf(
        EdgeMargin,
        WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
    )
    // The bar grows with the label's scaled line so a larger font is not clipped.
    val scaledLabelLine = with(density) { LabelSize.toDp() }
    val barHeight = BarHeight + (scaledLabelLine - 10.dp).coerceAtLeast(0.dp)

    val selectedIndex = items.indexOfFirst { it.selected }.coerceAtLeast(0)
    val lens = remember { Animatable(selectedIndex.toFloat()) }
    val lift = remember { Animatable(0f) }
    val swell = remember { Animatable(0f) }
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentItems by rememberUpdatedState(items)
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)

    // A selection made elsewhere (a deep link, a preset) still slides the lens.
    LaunchedEffect(selectedIndex) {
        if (pressedIndex == null) {
            lens.animateTo(selectedIndex.toFloat(), TravelSpring)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Below five destinations the dock stops growing and centres itself, as
        // FlClash's does; four slots leave it narrower than the screen.
        val cap: Dp = if (items.size >= FullWidthCount) {
            Dp.Unspecified
        } else {
            (MaxItemExtent * items.size + BarPadding.value * 4f).dp
        }
        Box(
            modifier = Modifier
                .padding(
                    start = EdgeMargin,
                    end = EdgeMargin,
                    top = ShadowRoom,
                    bottom = bottomMargin,
                )
                .height(barHeight)
                .then(if (cap.isSpecified) Modifier.widthIn(max = cap) else Modifier)
                .fillMaxWidth()
                // A press swells the bar the way FlClash's ElasticPress does: it
                // only paints, layout and hit testing still see the resting size.
                .graphicsLayer {
                    val side = maxOf(size.width, size.height)
                    val growth = if (side > 0f) minOf(0.25f, 16f / side) else 0f
                    val scale = 1f + swell.value * growth
                    scaleX = scale
                    scaleY = scale
                }
                // A soft, wide drop in the manner of iOS rather than a Material
                // elevation: the bar is meant to float over the content.
                .shadow(
                    elevation = 10.dp,
                    shape = Stadium,
                    clip = false,
                    ambientColor = shadowColor.copy(alpha = if (dark) 0.30f else 0.10f),
                    spotColor = shadowColor.copy(alpha = if (dark) 0.40f else 0.16f),
                )
                .clip(Stadium)
                .background(barColor),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val pad = BarPadding.toPx()
                val span = constraints.maxWidth.toFloat() - pad * 2
                val minLens = MinLensWidth.dp.toPx()
                val layout = remember(span, items.size, pad, minLens) {
                    dockLayout(span, items.size, pad, minLens)
                }
                val lastIndex = items.size - 1

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(layout, items.size) {
                            val inner = BarPadding.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var index = dockIndexAt(
                                    dockPositionAt(down.position.x - inner, layout, lastIndex),
                                    lastIndex,
                                )
                                pressedIndex = index
                                scope.launch { swell.animateTo(1f, PressSpring) }
                                scope.launch { lens.animateTo(index.toFloat(), TravelSpring) }

                                var dragging = false
                                var committed = true
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        committed = false
                                        break
                                    }
                                    if (!change.pressed) {
                                        break
                                    }
                                    if (!change.positionChanged()) {
                                        continue
                                    }
                                    if (!dragging &&
                                        abs(change.position.x - down.position.x) >= viewConfiguration.touchSlop
                                    ) {
                                        dragging = true
                                    }
                                    if (dragging) {
                                        // The lens rides the finger, so the slot it
                                        // covers is the one a release would pick.
                                        val position =
                                            dockPositionAt(change.position.x - inner, layout, lastIndex)
                                        scope.launch { lens.snapTo(position) }
                                        val moved = dockIndexAt(position, lastIndex)
                                        if (moved != index) {
                                            index = moved
                                            pressedIndex = moved
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        }
                                        change.consume()
                                    }
                                }

                                // UIKit keeps the lens lifted until it has arrived.
                                val released = pressedIndex
                                pressedIndex = null
                                scope.launch { swell.animateTo(0f, PressSpring) }
                                scope.launch {
                                    if (released != null) {
                                        lens.animateTo(released.toFloat(), TravelSpring)
                                    }
                                    lift.animateTo(0f, LiftSpring)
                                }
                                if (committed && released != null &&
                                    released != currentSelected && released in currentItems.indices
                                ) {
                                    currentOnSelect(currentItems[released].key)
                                }
                            }
                        },
                ) {
                    DockContents(
                        items = items,
                        selectedIndex = selectedIndex,
                        tintedIndex = pressedIndex ?: selectedIndex,
                        layout = layout,
                        lens = lens,
                        lift = lift,
                        lensColor = lensColor,
                        onSelect = { index ->
                            currentItems.getOrNull(index)?.let { currentOnSelect(it.key) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DockContents(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    tintedIndex: Int,
    layout: DockLayout,
    lens: Animatable<Float>,
    lift: Animatable<Float>,
    lensColor: Color,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = LabelSize,
        lineHeight = LabelLine,
    )
    val labelInset = with(density) { LabelInset.toPx() }
    val growth = with(density) { (LensGrowth * 2f).dp.toPx() }
    // A label wider than its slot shrinks, down to the smallest size FlClash
    // allows, instead of being cut off.
    val widest = remember(items.map { it.label }, labelStyle, measurer) {
        items.maxOf { item ->
            measurer.measure(AnnotatedString(item.label), labelStyle, maxLines = 1).size.width.toFloat()
        }
    }
    val room = layout.pitch - labelInset * 2f
    val scale = when {
        widest <= room || widest <= 0f -> 1f
        else -> maxOf(room / widest, MinLabelScale)
    }
    val textStyle = labelStyle.copy(fontSize = LabelSize * scale)
    val horizontalInset = with(density) { layout.inset.toDp() }

    Box(modifier = Modifier.fillMaxSize().padding(BarPadding)) {
        // The lens: a stadium that rests over the selected slot, overhanging it
        // by the bar's inner padding, and grows and stretches while pressed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val stretch = lensStretch(lens, layout.pitch)
                    val grow = growth * lift.value
                    val width = (layout.lens + grow) * (1f + stretch)
                    val height = (size.height + grow) * (1f - stretch * JellyAspect)
                    val left = layout.inset + (lens.value + 0.5f) * layout.pitch - width / 2f
                    drawRoundRect(
                        color = lensColor,
                        topLeft = Offset(left, (size.height - height) / 2f),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(minOf(width, height) / 2f),
                    )
                },
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = horizontalInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                BarEntry(
                    item = item,
                    selected = index == selectedIndex,
                    tinted = index == tintedIndex,
                    textStyle = textStyle,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun BarEntry(
    item: BottomBarItem,
    selected: Boolean,
    tinted: Boolean,
    textStyle: TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only the destination under the lens is tinted; the rest stay muted, as
    // FlClash paints them.
    val color = if (tinted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.Tab
            this.selected = selected
            onClick(label = item.label) {
                onClick()
                true
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(IconSize),
        )
        Spacer(modifier = Modifier.height(LabelGap))
        // Every label stays visible: an icon alone makes people guess which tab
        // they are on.
        Text(
            text = item.label,
            modifier = Modifier.padding(horizontal = LabelInset),
            color = color,
            style = textStyle,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
