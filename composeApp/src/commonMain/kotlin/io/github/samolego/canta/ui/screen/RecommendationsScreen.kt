package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import io.github.samolego.canta.generated.resources.recommend_empty
import io.github.samolego.canta.generated.resources.recommend_featured
import io.github.samolego.canta.generated.resources.recommend_footer
import io.github.samolego.canta.generated.resources.recommend_intro
import io.github.samolego.canta.generated.resources.recommend_open
import io.github.samolego.canta.generated.resources.recommend_title
import io.github.samolego.canta.ui.component.ScreenTopBar
import org.jetbrains.compose.resources.stringResource

/** Room kept under the last card for the floating bar, which draws over the content. */
private val BarRoom = 96.dp

/** The tile that stands in for an app icon: the catalog carries no images. */
private val TileSize = 46.dp

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
 * group per category. A card is one app and one tap: it opens the project's page in the browser, so
 * nothing here installs anything by itself.
 */
@Composable
fun RecommendationsScreen(
    platform: CantaPlatform,
    onNavigateBack: () -> Unit,
) {
    var catalog by remember { mutableStateOf<Recommendations?>(null) }

    LaunchedEffect(Unit) {
        catalog = Recommendations.bundled()
    }

    val loaded = catalog
    val groups = remember(loaded) { loaded?.grouped().orEmpty() }
    val featured = remember(loaded) { loaded?.featured.orEmpty() }

    Scaffold(
        topBar = {
            ScreenTopBar(onNavigateBack = onNavigateBack, title = stringResource(Res.string.recommend_title))
        },
    ) { padding ->
        if (loaded == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = BarRoom),
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(Res.string.recommend_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                )
            }

            if (loaded.apps.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(Res.string.recommend_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }

            if (featured.isNotEmpty()) {
                item(key = "featured-head") {
                    SectionHeader(label = stringResource(Res.string.recommend_featured), count = featured.size)
                }
                items(featured, key = { "featured-${it.id}" }) { app ->
                    RecommendationCard(app = app, featured = true, onOpen = { platform.openUrl(app.url) })
                }
            }

            groups.forEach { (category, apps) ->
                item(key = "head-$category") { SectionHeader(label = categoryLabel(category), count = apps.size) }
                items(apps, key = { it.id }) { app ->
                    RecommendationCard(app = app, featured = false, onOpen = { platform.openUrl(app.url) })
                }
            }

            if (loaded.apps.isNotEmpty()) {
                item(key = "footer") {
                    Text(
                        text = stringResource(Res.string.recommend_footer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

/** A category heading, with how many apps are filed under it. */
@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
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
 * One app: tile, name, author and licence, a sentence about it, and the browser it opens. A pinned
 * card is drawn with a primary outline so it reads as the one to look at first.
 */
@Composable
private fun RecommendationCard(
    app: RecommendedApp,
    featured: Boolean,
    onOpen: () -> Unit,
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
                    onClickLabel = stringResource(Res.string.recommend_open, app.name),
                    role = Role.Button,
                    onClick = onOpen,
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTile(mark = app.tileMark, accent = accent)
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
 * The stand-in icon: a rounded tile filled with the app's accent colour and its mark drawn white on
 * top, so a list with no images still reads as a list of apps rather than a list of links.
 */
@Composable
private fun AppTile(mark: String, accent: Color) {
    Box(
        modifier = Modifier
            .size(TileSize)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.18f), lerp(accent, Color.Black, 0.18f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = mark,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            // Two characters need to be smaller to stay inside the tile.
            fontSize = if (mark.length > 1) 15.sp else 19.sp,
            maxLines = 1,
        )
    }
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
