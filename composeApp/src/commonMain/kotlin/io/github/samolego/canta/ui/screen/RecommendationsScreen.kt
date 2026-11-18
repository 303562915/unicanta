package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.recommend.RecommendedApp
import io.github.samolego.canta.data.recommend.Recommendations
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.recommend_by
import io.github.samolego.canta.generated.resources.recommend_cat_files
import io.github.samolego.canta.generated.resources.recommend_cat_media
import io.github.samolego.canta.generated.resources.recommend_cat_privacy
import io.github.samolego.canta.generated.resources.recommend_cat_store
import io.github.samolego.canta.generated.resources.recommend_cat_system
import io.github.samolego.canta.generated.resources.recommend_cat_tools
import io.github.samolego.canta.generated.resources.recommend_close
import io.github.samolego.canta.generated.resources.recommend_detail_hint
import io.github.samolego.canta.generated.resources.recommend_details
import io.github.samolego.canta.generated.resources.recommend_empty
import io.github.samolego.canta.generated.resources.recommend_featured
import io.github.samolego.canta.generated.resources.recommend_footer
import io.github.samolego.canta.generated.resources.recommend_intro
import io.github.samolego.canta.generated.resources.recommend_open_browser
import io.github.samolego.canta.generated.resources.recommend_switch_grid
import io.github.samolego.canta.generated.resources.recommend_switch_list
import io.github.samolego.canta.generated.resources.recommend_title
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import org.jetbrains.compose.resources.stringResource

/** Room kept under the last row for the floating bar, which draws over the content. */
private val BarRoom = 96.dp

/** The tile that stands in for an app icon: the catalog carries no images. */
private val TileSize = 46.dp

/** The tile of a grid cell, which has no room for a summary under it. */
private val CellTileSize = 56.dp

/** Narrowest cell the grid will lay out, which settles on four columns on a phone. */
private val MinCellWidth = 76.dp

/** Fallback tile colours, for a catalog entry that names no accent. */
private val FallbackAccents = listOf(
    Color(0xFF5C6BC0),
    Color(0xFF26A69A),
    Color(0xFFEF6C00),
    Color(0xFF8E24AA),
    Color(0xFF1E88E5),
)

/**
 * The recommendations page: open-source apps the maintainers like, pinned ones first and then one
 * group per category.
 *
 * Like the other pages the bottom bar leads to, it has no back chevron: it is a place of its own.
 *
 * The catalog is a wall of icons by default — a name and its author under each tile, four to a row,
 * the way an app store shows them — and the toolbar button switches it to one card per app with the
 * summary spelled out. Either way a tap opens the entry's detail sheet rather than the browser, so
 * a mis-tap costs nothing; the sheet is where the link out lives.
 */
@Composable
fun RecommendationsScreen(
    platform: CantaPlatform,
    onNavigateBack: () -> Unit,
) {
    var catalog by remember { mutableStateOf<Recommendations?>(null) }
    var asGrid by rememberSaveable { mutableStateOf(true) }
    var detail by remember { mutableStateOf<RecommendedApp?>(null) }

    LaunchedEffect(Unit) {
        catalog = Recommendations.bundled()
    }

    val loaded = catalog
    val groups = remember(loaded) { loaded?.grouped().orEmpty() }
    val featured = remember(loaded) { loaded?.featured.orEmpty() }

    Scaffold(
        topBar = {
            // A page the bottom bar leads to: no chevron, only the layout button.
            ScreenTopBar(
                title = stringResource(Res.string.recommend_title),
                actions = {
                    // One button, two arrangements: the icon shows what a tap gets you.
                    IconButton(onClick = { asGrid = !asGrid }) {
                        Icon(
                            imageVector = if (asGrid) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = stringResource(
                                if (asGrid) Res.string.recommend_switch_list else Res.string.recommend_switch_grid,
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (loaded == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val openDetails: (RecommendedApp) -> Unit = { detail = it }

        if (asGrid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = MinCellWidth),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = BarRoom),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "intro", span = { GridItemSpan(maxLineSpan) }) { IntroNote() }

                if (loaded.apps.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyNote() }
                }

                if (featured.isNotEmpty()) {
                    item(key = "featured-head", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(label = stringResource(Res.string.recommend_featured), count = featured.size)
                    }
                    gridItems(featured, key = { "featured-${it.id}" }) { app ->
                        AppGridCell(app = app, featured = true, onClick = { openDetails(app) })
                    }
                }

                groups.forEach { (category, apps) ->
                    item(key = "head-$category", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(label = categoryLabel(category), count = apps.size)
                    }
                    gridItems(apps, key = { it.id }) { app ->
                        AppGridCell(app = app, featured = false, onClick = { openDetails(app) })
                    }
                }

                item(key = "footer", span = { GridItemSpan(maxLineSpan) }) { FooterNote(loaded.apps.size) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = BarRoom),
            ) {
                item(key = "intro") { IntroNote() }

                if (loaded.apps.isEmpty()) {
                    item(key = "empty") { EmptyNote() }
                }

                if (featured.isNotEmpty()) {
                    item(key = "featured-head") {
                        SectionHeader(label = stringResource(Res.string.recommend_featured), count = featured.size)
                    }
                    items(featured, key = { "featured-${it.id}" }) { app ->
                        RecommendationCard(app = app, featured = true, onClick = { openDetails(app) })
                    }
                }

                groups.forEach { (category, apps) ->
                    item(key = "head-$category") { SectionHeader(label = categoryLabel(category), count = apps.size) }
                    items(apps, key = { it.id }) { app ->
                        RecommendationCard(app = app, featured = false, onClick = { openDetails(app) })
                    }
                }

                item(key = "footer") { FooterNote(loaded.apps.size) }
            }
        }
    }

    detail?.let { app ->
        RecommendationDetailDialog(
            app = app,
            onDismiss = { detail = null },
            onOpen = {
                detail = null
                platform.openUrl(app.url)
            },
        )
    }
}

/** A category heading, with how many apps are filed under it. */
@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One app as a tile: the icon stand-in, its name and its author, nothing else. A pinned entry keeps
 * the primary ring around its tile, since the group heading alone is easy to scroll past.
 */
@Composable
private fun AppGridCell(
    app: RecommendedApp,
    featured: Boolean,
    onClick: () -> Unit,
) {
    val accent = remember(app.accent, app.id) { accentOf(app) }
    val author = app.author.trim()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                onClickLabel = stringResource(Res.string.recommend_details, app.name),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AppTile(
            mark = app.tileMark,
            accent = accent,
            size = CellTileSize,
            corner = 16.dp,
            ring = if (featured) MaterialTheme.colorScheme.primary else null,
        )
        Text(
            text = app.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (author.isNotEmpty()) {
            Text(
                text = author,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One app as a row: tile, name, author and licence, and a sentence about it. A pinned card is
 * drawn with a primary outline so it reads as the one to look at first.
 */
@Composable
private fun RecommendationCard(
    app: RecommendedApp,
    featured: Boolean,
    onClick: () -> Unit,
) {
    val accent = remember(app.accent, app.id) { accentOf(app) }
    // "作者 qianyumeng0228 · MIT": the author is always shown, the licence only when the entry
    // names one.
    val author = app.author.trim()
    val authorLine = if (author.isEmpty()) null else stringResource(Res.string.recommend_by, author)
    val meta = listOfNotNull(authorLine, app.license.trim().ifEmpty { null }).joinToString(" · ")

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (featured) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(Res.string.recommend_details, app.name),
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTile(mark = app.tileMark, accent = accent, size = TileSize, corner = 14.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (app.summary.isNotBlank()) {
                    Text(
                        text = app.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * What a tap on a cell opens: the whole entry, and the one button that leaves Canta. Nothing is
 * installed from here either — the button hands the project page to the browser.
 */
@Composable
private fun RecommendationDetailDialog(
    app: RecommendedApp,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    val accent = remember(app.accent, app.id) { accentOf(app) }
    val author = app.author.trim()
    val meta = listOfNotNull(
        if (author.isEmpty()) null else stringResource(Res.string.recommend_by, author),
        app.license.trim().ifEmpty { null },
    ).joinToString(" · ")

    CantaDialog(
        onDismissRequest = onDismiss,
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.recommend_close)) }
            Button(onClick = onOpen) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = stringResource(Res.string.recommend_open_browser), modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppTile(mark = app.tileMark, accent = accent, size = CellTileSize, corner = 16.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.padding(top = 14.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = categoryLabel(app.category),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        if (app.summary.isNotBlank()) {
            Text(
                text = app.summary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Text(
            text = stringResource(Res.string.recommend_detail_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * The stand-in icon: a rounded tile filled with the app's accent colour and its mark drawn white on
 * top, so a list with no images still reads as a list of apps rather than a list of links. [ring]
 * draws an outline around it, which is how a pinned entry is marked.
 */
@Composable
private fun AppTile(
    mark: String,
    accent: Color,
    size: Dp,
    corner: Dp,
    ring: Color? = null,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.18f), lerp(accent, Color.Black, 0.18f))))
            .then(if (ring != null) Modifier.border(1.5.dp, ring, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = mark,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            // Two characters need to be smaller to stay inside the tile.
            fontSize = when {
                mark.length > 1 -> 15.sp
                size > TileSize -> 23.sp
                else -> 19.sp
            },
            maxLines = 1,
        )
    }
}

/** What the page is: open-source apps, and what a tap does. */
@Composable
private fun IntroNote() {
    Text(
        text = stringResource(Res.string.recommend_intro),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 2.dp),
    )
}

/** Shown when the catalog is empty, or could not be read. */
@Composable
private fun EmptyNote() {
    Text(
        text = stringResource(Res.string.recommend_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

/** Closes the page: where the list comes from, and how many entries it holds. */
@Composable
private fun FooterNote(count: Int) {
    if (count == 0) return
    Text(
        text = stringResource(Res.string.recommend_footer),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 8.dp),
    )
}

/** The accent named by the entry, or one picked from its id so every card still differs. */
private fun accentOf(app: RecommendedApp): Color {
    val hex = app.accent.removePrefix("#").trim()
    val parsed = hex.toLongOrNull(16)?.let { value ->
        when (hex.length) {
            6 -> Color(0xFF000000L or value)
            8 -> Color(value)
            else -> null
        }
    }
    return parsed ?: FallbackAccents[(app.id.hashCode() and Int.MAX_VALUE) % FallbackAccents.size]
}

/** Localised heading of a category key. */
@Composable
private fun categoryLabel(key: String): String = when (key) {
    "system" -> stringResource(Res.string.recommend_cat_system)
    "store" -> stringResource(Res.string.recommend_cat_store)
    "privacy" -> stringResource(Res.string.recommend_cat_privacy)
    "files" -> stringResource(Res.string.recommend_cat_files)
    "media" -> stringResource(Res.string.recommend_cat_media)
    "tools" -> stringResource(Res.string.recommend_cat_tools)
    else -> key
}
