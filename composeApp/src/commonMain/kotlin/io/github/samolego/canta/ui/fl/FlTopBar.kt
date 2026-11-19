package io.github.samolego.canta.ui.fl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The height of the bar's content, below the status bar. */
val FlTopBarHeight: Dp = 72.dp

/**
 * The height of the bar on pages that float it over their own content: slimmer, so the page's
 * first card starts just under the status bar instead of a full band below it.
 */
val FlTopBarCompactHeight: Dp = 54.dp

/** Where a bar button starts, and how far the last one stays from the edge. */
private val BarInset = 20.dp

/** The gap between an action button and the next. */
private val ActionGap = 8.dp

/** The width of a leading button, so a page's content can line up under a title. */
val FlBarButtonSize: Dp = 44.dp

/**
 * The bar FlClash puts over a page: the page's own surface, a large title, a round
 * button at either end, and no shadow at all. A page opened on top of another centres
 * its title and carries a chevron to go back; a root page keeps its title at the start
 * and holds its actions at the end.
 */
@Composable
fun FlTopBar(
    title: String,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    /** Row height below the status bar; pages that float the bar over content go slimmer. */
    height: Dp = FlTopBarHeight,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = BarInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                // The arrow every phone owner already knows, drawn flat instead of floating in
                // a bubble: a bare chevron on a page-coloured circle reads as "something is
                // broken" at this size, and the stem makes the direction unmistakable.
                FlBarButton(
                    glyph = FlGlyphs.arrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    iconSize = 22.dp,
                    filled = false,
                )
                Spacer(Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(
                    horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    Text(
                        text = title,
                        style = if (centered) FlText.headline else FlText.pageTitle,
                        color = scheme.onSurface,
                        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = FlText.footnote,
                            color = scheme.onSurfaceVariant,
                            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ActionGap),
            ) {
                ProvideTextStyle(FlText.subheadline.copy(color = scheme.onSurface)) {
                    actions()
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
