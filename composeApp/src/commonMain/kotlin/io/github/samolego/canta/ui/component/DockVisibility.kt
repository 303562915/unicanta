package io.github.samolego.canta.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** How far the page has to travel before the bar reacts, so a small wobble does not hide it. */
private val ScrollThreshold = 12.dp

/**
 * Whether the floating bar is showing, decided by which way the page under it is scrolling.
 *
 * The bar steps out of the way while the page scrolls down — the content is what the user is
 * reading — and comes back as soon as the page is dragged back up, which is where the
 * destinations are wanted again. [scroll] takes the vertical travel of a scroll, positive when
 * the page moves down towards its start and negative when it moves further along.
 */
@Stable
class DockVisibility(private val thresholdPx: Float) {
    var visible by mutableStateOf(true)
        private set

    /** Travel since the last direction change; its sign is the direction being dragged. */
    private var travelled = 0f

    fun scroll(deltaY: Float) {
        if (deltaY == 0f) return
        // A drag back the other way starts counting from zero again, so the bar answers the
        // gesture the user is making now instead of the distance already travelled.
        if ((deltaY > 0f) != (travelled > 0f)) travelled = 0f
        travelled += deltaY
        when {
            travelled >= thresholdPx -> {
                if (!visible) visible = true
                travelled = 0f
            }
            -travelled >= thresholdPx -> {
                if (visible) visible = false
                travelled = 0f
            }
        }
    }

    /** Puts the bar back, for a new page that starts at its top with nothing scrolled yet. */
    fun show() {
        travelled = 0f
        visible = true
    }
}

/** Remembers a [DockVisibility] and the connection that feeds it the page's scrolling. */
@Composable
fun rememberDockVisibility(): Pair<DockVisibility, NestedScrollConnection> {
    val thresholdPx = with(LocalDensity.current) { ScrollThreshold.toPx() }
    val dock = remember { DockVisibility(thresholdPx) }
    val connection = remember(dock) {
        // Observation only: the offset is never consumed, so the page scrolls as it always did.
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                dock.scroll(available.y)
                return Offset.Zero
            }
        }
    }
    return dock to connection
}

/**
 * The bar itself, slid out of the way while the page scrolls down.
 *
 * [AnimatedVisibility] also takes it out of hit testing while it is gone, so a press meant for
 * the content at the foot of the page never lands on a bar nobody can see.
 */
@Composable
fun CollapsingBottomBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        content()
    }
}

/** Applies the dock's scroll observation to a page container. */
fun Modifier.dockScroll(connection: NestedScrollConnection): Modifier = nestedScroll(connection)
