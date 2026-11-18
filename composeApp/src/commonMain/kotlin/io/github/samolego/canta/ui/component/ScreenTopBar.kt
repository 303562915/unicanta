package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import io.github.samolego.canta.ui.fl.FlTopBar

/**
 * The bar of a page opened on top of another: FlClash's compact toolbar — the title
 * centred between a chevron that goes back and whatever the page puts at the end.
 */
@Composable
fun ScreenTopBar(
    onNavigateBack: () -> Unit,
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    FlTopBar(
        title = title,
        centered = true,
        onBack = onNavigateBack,
        actions = actions,
    )
}
