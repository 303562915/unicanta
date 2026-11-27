package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
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
    /**
     * Whether the title sits in the middle. Root pages keep it at the start.
     *
     * 帖子详情页传 false：那里要照 fluxdo 的原生 `AppBar`（`centerTitle: false`），
     * 标题左对齐、高 56dp，而不是 FlClash 这套"二级页居中 72dp"。
     */
    centered: Boolean = true,
    /** Overrides the title style; see [FlTopBar.titleStyle]. */
    titleStyle: TextStyle? = null,
) {
    FlTopBar(
        title = title,
        centered = centered,
        onBack = onNavigateBack,
        actions = actions,
        height = height,
        titleStyle = titleStyle,
    )
}
