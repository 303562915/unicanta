package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import io.github.samolego.canta.ui.fl.FlTopBar
import io.github.samolego.canta.ui.fl.FlTopBarHeight

/**
 * The bar of a page opened on top of another: FlClash's compact toolbar — the title
 * centred between a chevron that goes back and whatever the page puts at the end.
 *
 * A page the bottom bar leads to passes no [onNavigateBack]: it is a place of its own,
 * not something opened over another, and FlClash puts no chevron on those.
 */
@Composable
fun ScreenTopBar(
    onNavigateBack: (() -> Unit)? = null,
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    /** Row height below the status bar; pages that float the bar over content go slimmer. */
    height: Dp = FlTopBarHeight,
) {
    FlTopBar(
        title = title,
        centered = true,
        onBack = onNavigateBack,
        actions = actions,
        height = height,
    )
}
