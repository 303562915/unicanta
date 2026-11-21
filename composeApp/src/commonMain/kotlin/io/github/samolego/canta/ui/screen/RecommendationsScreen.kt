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
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.recommend.RecommendedApp
import io.github.samolego.canta.data.recommend.Recommendations
import io.github.samolego.canta.data.recommend.SuggestedApp
import io.github.samolego.canta.data.recommend.githubOwnerOf
import io.github.samolego.canta.data.recommend.githubProjectUrl
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
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Room kept under the last row for the floating bar, which draws over the content. */
private val BarRoom = 96.dp

/** The tile that stands in for an app icon: the catalog carries no images. */
private val TileSize = 46.dp

/** The tile of a grid cell, which has no room for a summary under it. */
private val CellTileSize = 56.dp

/** Narrowest cell the grid will lay out, which settles on four columns on a phone. */
private val MinCellWidth = 76.dp

/** Green for "这个应用该进列表", the same pass colour the other votes use. */
private val SuggestAgree = Color(0xFF2FA84F)

/** Grey-red for "不认同"; quieter than [SuggestAgree], because a rejected entry is not a fault. */
private val SuggestDisagree = Color(0xFFC0564C)

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
 * group per category — plus the open half, where anybody may put up an app of their own and the
 * community votes on whether it joins.
 *
 * Like the other pages the bottom bar leads to, it has no back chevron: it is a place of its own,
 * which is why there is no back callback here to pass down.
 *
 * The catalog is a wall of icons by default — a name and its author under each tile, four to a row,
 * the way an app store shows them — and the toolbar button switches it to one card per app with the
 * summary spelled out. Either way a tap opens the entry's detail sheet rather than the browser, so
 * a mis-tap costs nothing; the sheet is where the link out lives. The arrangement is a stored
 * setting, not a per-visit whim: leaving the page and coming back keeps the one that was chosen.
 *
 * @param repository the community backend, for the proposed apps; null leaves that half out.
 * @param author the name this install's submissions are signed with.
 * @param authorId the identity number behind [author]: what "mine" and one vote per identity key on.
 * @param asGrid false for the one-card-per-app arrangement; the toolbar button flips it.
 */
@Composable
fun RecommendationsScreen(
    platform: CantaPlatform,
    repository: PlanRepository? = null,
    author: String = "",
    authorId: String = "",
    asGrid: Boolean = true,
    onToggleLayout: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<Recommendations?>(null) }
    var detail by remember { mutableStateOf<RecommendedApp?>(null) }
    /** What the search box holds; empty shows the whole catalog, categories and all. */
    var query by rememberSaveable { mutableStateOf("") }

    // The proposed half: what the community put up, how the votes stand, and the form that files
    // one more. Kept beside the shipped catalog rather than merged into it, because the two are
    // decided by different people and the page says so.
    var suggested by remember { mutableStateOf<List<SuggestedApp>>(emptyList()) }
    var suggestReloads by remember { mutableIntStateOf(0) }
    var suggestError by remember { mutableStateOf<String?>(null) }
    var votingId by remember { mutableStateOf(0L) }
    var composerOpen by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        catalog = Recommendations.bundled()
    }

    LaunchedEffect(repository, authorId, suggestReloads) {
        val repo = repository ?: return@LaunchedEffect
        val fresh = repo.suggestedApps(authorId.ifEmpty { author })
        if (fresh != null) {
            suggested = fresh
            suggestError = null
        } else {
            // A backend without the function is not a failure of the page: it simply has no
            // proposals to show, and `lastError` is the function-missing message. A call that never
            // arrived leaves `lastError` null, which is the case that needs words of its own —
            // otherwise the half simply vanishes and the reader cannot tell why.
            suggestError = repo.lastError ?: "社区推荐拉不到，检查一下网络或代理再刷新。"
        }
    }

    val loaded = catalog
    val groups = remember(loaded) { loaded?.grouped().orEmpty() }
    val featured = remember(loaded) { loaded?.featured.orEmpty() }
    // While a search is running the category headings go away: a result is a result wherever it is
    // filed, and a heading with one row under it is noise.
    val found = remember(loaded, query) { loaded?.search(query).orEmpty() }
    val searching = query.isNotBlank()

    val submit: (String, String, String, String, String) -> Unit = submit@{ name, url, summary, category, license ->
        val repo = repository
        if (repo == null) {
            submitError = "社区后端没连上，提交没发出去。"
            return@submit
        }
        scope.launch {
            submitting = true
            submitError = null
            val id = repo.addSuggestedApp(
                name = name,
                url = url,
                summary = summary,
                category = category,
                license = license,
                author = author.ifEmpty { githubOwnerOf(url) },
                authorId = authorId,
            )
            submitting = false
            if (id != null) {
                composerOpen = false
                suggestReloads++
                platform.showMessage("已提交，等大家投票")
            } else {
                submitError = repo.lastError ?: "没发出去：检查一下网络再试"
            }
        }
    }

    val vote: (SuggestedApp, Boolean) -> Unit = { app, agree ->
        if (votingId == 0L) {
            val repo = repository
            if (repo == null) {
                platform.showMessage("社区后端没连上，投票没发出去。")
            } else {
                // Lifted out of the coroutine on purpose: two taps that arrive in the same frame
                // both pass the check above, and the second one would then overwrite the marker
                // the first is still using.
                votingId = app.id
                scope.launch {
                    val fresh = repo.voteSuggestedApp(app.id, authorId.ifEmpty { author }, agree)
                    votingId = 0L
                    if (fresh == null) {
                        platform.showMessage("没投上：${repo.lastError ?: "检查一下网络再试"}")
                    } else {
                        suggestReloads++
                    }
                }
            }
        }
    }

    val openSuggest: () -> Unit = { composerOpen = true }
    val canVote = authorId.isNotEmpty() || author.isNotEmpty()

    Scaffold(
        topBar = {
            // A page the bottom bar leads to: no chevron, only the layout button.
            ScreenTopBar(
                title = stringResource(Res.string.recommend_title),
                actions = {
                    // One button, two arrangements: the icon shows what a tap gets you. The choice
                    // is written to the settings, so the page opens the way it was left.
                    IconButton(onClick = onToggleLayout) {
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
        val openSuggested: (SuggestedApp) -> Unit = { platform.openUrl(it.url) }

        if (asGrid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = MinCellWidth),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = BarRoom),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                    CatalogSearchField(query = query, onQueryChange = { query = it })
                }
                item(key = "intro", span = { GridItemSpan(maxLineSpan) }) { IntroNote() }

                if (!searching) {
                    item(key = "suggest-new", span = { GridItemSpan(maxLineSpan) }) {
                        SuggestEntryRow(onClick = openSuggest, inset = 6.dp)
                    }
                    if (suggested.isNotEmpty()) {
                        item(key = "suggest-head", span = { GridItemSpan(maxLineSpan) }) {
                            CommunityHeader(count = suggested.size)
                        }
                        suggested.forEachIndexed { index, entry ->
                            item(key = "suggest-$index-${entry.id}", span = { GridItemSpan(maxLineSpan) }) {
                                SuggestedAppCard(
                                    app = entry,
                                    voting = votingId == entry.id,
                                    canVote = canVote,
                                    onVote = { agree -> vote(entry, agree) },
                                    onOpen = { openSuggested(entry) },
                                )
                            }
                        }
                    } else if (suggestError != null) {
                        item(key = "suggest-error", span = { GridItemSpan(maxLineSpan) }) {
                            CommunityHint(text = "社区推荐暂时没拉到：$suggestError", inset = 6.dp)
                        }
                    }
                }

                if (loaded.apps.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyNote() }
                }

                if (searching) {
                    if (found.isEmpty()) {
                        item(key = "no-hits", span = { GridItemSpan(maxLineSpan) }) {
                            NoHits(query = query)
                        }
                    }
                    gridItems(found, key = { "hit-${it.id}" }) { app ->
                        AppGridCell(app = app, featured = app.featured, onClick = { openDetails(app) })
                    }
                    item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                        FooterNote(found.size)
                    }
                } else {
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
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = BarRoom),
            ) {
                item(key = "search") { CatalogSearchField(query = query, onQueryChange = { query = it }, inset = 16.dp) }
                item(key = "intro") { IntroNote() }

                if (!searching) {
                    item(key = "suggest-new") {
                        SuggestEntryRow(onClick = openSuggest, inset = 16.dp)
                    }
                    if (suggested.isNotEmpty()) {
                        item(key = "suggest-head") { CommunityHeader(count = suggested.size, inset = 16.dp) }
                        items(suggested.size, key = { "suggest-$it" }) { index ->
                            val entry = suggested[index]
                            SuggestedAppCard(
                                app = entry,
                                voting = votingId == entry.id,
                                canVote = canVote,
                                onVote = { agree -> vote(entry, agree) },
                                onOpen = { openSuggested(entry) },
                            )
                        }
                    } else if (suggestError != null) {
                        item(key = "suggest-error") { CommunityHint(text = "社区推荐暂时没拉到：$suggestError", inset = 16.dp) }
                    }
                }

                if (loaded.apps.isEmpty()) {
                    item(key = "empty") { EmptyNote() }
                }

                if (searching) {
                    if (found.isEmpty()) {
                        item(key = "no-hits") { NoHits(query = query, inset = 16.dp) }
                    }
                    items(found, key = { "hit-${it.id}" }) { app ->
                        RecommendationCard(app = app, featured = app.featured, onClick = { openDetails(app) })
                    }
                    item(key = "footer") { FooterNote(found.size, inset = 16.dp) }
                } else {
                    if (featured.isNotEmpty()) {
                        item(key = "featured-head") {
                            SectionHeader(
                                label = stringResource(Res.string.recommend_featured),
                                count = featured.size,
                                inset = 16.dp,
                            )
                        }
                        items(featured, key = { "featured-${it.id}" }) { app ->
                            RecommendationCard(app = app, featured = true, onClick = { openDetails(app) })
                        }
                    }

                    groups.forEach { (category, apps) ->
                        item(key = "head-$category") {
                            SectionHeader(label = categoryLabel(category), count = apps.size, inset = 16.dp)
                        }
                        items(apps, key = { it.id }) { app ->
                            RecommendationCard(app = app, featured = false, onClick = { openDetails(app) })
                        }
                    }

                    item(key = "footer") { FooterNote(loaded.apps.size, inset = 16.dp) }
                }
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

    if (composerOpen) {
        SuggestComposerDialog(
            submitting = submitting,
            error = submitError,
            onDismiss = {
                composerOpen = false
                submitError = null
            },
            onSubmit = submit,
        )
    }
}

/**
 * The line under an app's name: its author, or nothing when that would only repeat the name.
 *
 * A good few catalog entries are made by the account they are named after — `Droid-ify` by
 * Droid-ify, `AdAway` by AdAway — and drawing the name twice, once bold and once grey underneath,
 * reads as a rendering bug rather than as credit. The licence still gets its own line where there
 * is one, so nothing is lost by dropping the repetition.
 */
private fun authorLineOf(name: String, author: String): String? {
    val trimmed = author.trim()
    if (trimmed.isEmpty()) return null
    val plain = trimmed.filter { it.isLetterOrDigit() }.lowercase()
    if (plain.isEmpty() || plain == name.trim().filter { it.isLetterOrDigit() }.lowercase()) return null
    return trimmed
}

/** A category heading, with how many apps are filed under it; [inset] lines it up with the row below. */
@Composable
private fun SectionHeader(label: String, count: Int, inset: Dp = 6.dp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = inset, end = inset, top = 14.dp, bottom = 2.dp),
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

/** The heading of the proposed half, saying who decides what lands under it. */
@Composable
private fun CommunityHeader(count: Int, inset: Dp = 6.dp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = inset, end = inset, top = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "社区推荐",
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

/** One quiet line of explanation, for the cases where the list below it is missing. */
@Composable
private fun CommunityHint(text: String, inset: Dp = 6.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = inset, end = inset, top = 6.dp, bottom = 2.dp),
    )
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
    val accent = remember(app.accent, app.id) { accentOf(app.accent, app.id) }
    val author = authorLineOf(app.name, app.author)
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
        if (author != null) {
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
    val accent = remember(app.accent, app.id) { accentOf(app.accent, app.id) }
    // "作者 qianyumeng0228 · MIT": the author is shown unless it repeats the name, the licence only
    // when the entry names one.
    val authorLine = authorLineOf(app.name, app.author)?.let { stringResource(Res.string.recommend_by, it) }
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
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
 * The row that opens the submission form: the one place on this page where the reader writes
 * instead of reading. It sits above the proposed apps because that is the order of the flow —
 * file one, and it lands in the list underneath.
 */
@Composable
private fun SuggestEntryRow(onClick: () -> Unit, inset: Dp = 6.dp) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+", color = scheme.onPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "推荐一个开源应用",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = "必须是 GitHub 上的开源项目；大家投票，满 " +
                    "${SuggestedApp.VOTES_NEEDED} 票且认同率 " +
                    "${(SuggestedApp.SHARE_NEEDED * 100).toInt()}% 就进上面的列表。",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One proposed app: who filed it, what it is called, the sentence about it, and the two counts its
 * fate is decided by. An accepted entry carries a badge and a link out; the rest wait for votes.
 */
@Composable
private fun SuggestedAppCard(
    app: SuggestedApp,
    voting: Boolean,
    canVote: Boolean,
    onVote: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = remember(app.id, app.category) { accentOf("", "${app.id}-${app.category}") }
    val meta = listOfNotNull(
        authorLineOf(app.name, app.author)?.let { stringResource(Res.string.recommend_by, it) },
        app.license.trim().ifEmpty { null },
        categoryLabel(app.category).takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        border = if (app.applied) BorderStroke(1.dp, SuggestAgree.copy(alpha = 0.6f)) else null,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppTile(mark = app.tileMark, accent = accent, size = 36.dp, corner = 12.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (app.applied) {
                    Text(
                        text = "已收录",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(SuggestAgree)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(onClick = onOpen) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = stringResource(Res.string.recommend_open_browser),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (app.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = app.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            // The bar: the same green/red split the removal vote uses, so both read as one idea.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(scheme.surfaceContainerHighest),
            ) {
                if (app.agrees > 0) {
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .weight(app.agrees.toFloat())
                            .background(SuggestAgree),
                    )
                }
                if (app.disagrees > 0) {
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .weight(app.disagrees.toFloat())
                            .background(SuggestDisagree),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = buildString {
                    append("${app.agrees} 人认同")
                    app.share?.let { append(" · ${(it * 100).toInt()}%") }
                    append(" · ${app.disagrees} 人不认同")
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    app.applied -> "已进列表；认同率掉下去会自动退出。"
                    app.missingVotes > 0 ->
                        "还差 ${app.missingVotes} 票" +
                            (app.share?.let { " · 现在认同 ${(it * 100).toInt()}%" } ?: "")
                    (app.share ?: 0f) < app.shareNeeded ->
                        "票数够了，但认同率只有 ${((app.share ?: 0f) * 100).toInt()}%，还差 " +
                            "${(app.shareNeeded * 100).toInt()}%"
                    else -> "已达成条件，等待后端结算"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (app.applied) scheme.primary else scheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SuggestVotePill(
                    label = if (app.mine == true) "已认同" else "认同",
                    count = app.agrees,
                    selected = app.mine == true,
                    color = SuggestAgree,
                    enabled = canVote && !voting,
                    onClick = { onVote(true) },
                )
                Spacer(modifier = Modifier.width(8.dp))
                SuggestVotePill(
                    label = if (app.mine == false) "已不认同" else "不认同",
                    count = app.disagrees,
                    selected = app.mine == false,
                    color = SuggestDisagree,
                    enabled = canVote && !voting,
                    onClick = { onVote(false) },
                )
                Spacer(modifier = Modifier.weight(1f))
                if (voting) {
                    Text(text = "…", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** One side of a proposal's vote: filled once this install picked it, raised surface while not. */
@Composable
private fun SuggestVotePill(
    label: String,
    count: Int,
    selected: Boolean,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) color else FlSurfaceLevel.Page.raised.colorOf(scheme))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Text(text = "✓", fontSize = 11.sp, color = Color.White)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else scheme.onSurface,
        )
    }
}

/** The six category keys, in the order the page lists them. */
private val ComposerCategories = listOf("system", "store", "privacy", "files", "media", "tools")

/**
 * The submission form: the GitHub project page, what the app is called, where it belongs, one
 * sentence about it and its licence.
 *
 * The link field is the gate: [githubProjectUrl] has to recognise what was typed before the button
 * comes alive, because "open source, on GitHub" is the only promise the list makes and a form that
 * accepts anything would break it on the first submission.
 */
@Composable
private fun SuggestComposerDialog(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var license by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("tools") }

    val normalized = githubProjectUrl(url)
    val canSend = normalized != null && name.trim().isNotEmpty() && !submitting

    CantaDialog(
        onDismissRequest = onDismiss,
        title = "推荐一个开源应用",
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
            Button(
                enabled = canSend,
                onClick = {
                    onSubmit(name.trim(), normalized.orEmpty(), summary.trim(), category, license.trim())
                },
            ) { Text(if (submitting) "正在提交…" else "提交") }
        },
    ) {
        Text(
            text = "只收 GitHub 上的开源项目。提交后大家投票：满 " +
                "${SuggestedApp.VOTES_NEEDED} 票、且认同率到 " +
                "${(SuggestedApp.SHARE_NEEDED * 100).toInt()}% 才进列表；票数掉下去也会自动退出。",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it.take(300) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            isError = url.isNotBlank() && normalized == null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            label = { Text("GitHub 项目地址") },
            placeholder = { Text("https://github.com/owner/repo") },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (url.isNotBlank() && normalized == null) {
                "这不像一个 GitHub 项目地址；只有 owner/repo 也行。"
            } else {
                "粘贴项目主页或 owner/repo；仓库下的子页面会收成项目根。"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (url.isNotBlank() && normalized == null) scheme.error else scheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            label = { Text("应用名") },
            placeholder = { Text("别人在列表里看到的名字") },
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "分类", style = MaterialTheme.typography.labelMedium, color = scheme.onSurface)
        Spacer(modifier = Modifier.height(6.dp))
        ComposerCategories.chunked(3).forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowKeys.forEach { key ->
                    val chosen = category == key
                    Text(
                        text = categoryLabel(key),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (chosen) Color.White else scheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (chosen) scheme.primary else FlSurfaceLevel.Page.raised.colorOf(scheme))
                            .clickable { category = key }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = summary,
            onValueChange = { summary = it.take(300) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            textStyle = MaterialTheme.typography.bodySmall,
            label = { Text("一句话介绍（可选）") },
            placeholder = { Text("它解决什么问题，为什么值得装") },
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = license,
            onValueChange = { license = it.take(40) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            label = { Text("许可证（可选）") },
            placeholder = { Text("GPL-3.0、MIT…") },
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = error, style = MaterialTheme.typography.labelSmall, color = scheme.error)
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
    val accent = remember(app.accent, app.id) { accentOf(app.accent, app.id) }
    val meta = listOfNotNull(
        authorLineOf(app.name, app.author)?.let { stringResource(Res.string.recommend_by, it) },
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
private fun FooterNote(count: Int, inset: Dp = 6.dp) {
    if (count == 0) return
    Text(
        text = stringResource(Res.string.recommend_footer),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = inset, end = inset, top = 14.dp, bottom = 8.dp),
    )
}

/** The accent named by the entry, or one picked from its id so every card still differs. */
private fun accentOf(accent: String, id: String): Color {
    val hex = accent.removePrefix("#").trim()
    val parsed = hex.toLongOrNull(16)?.let { value ->
        when (hex.length) {
            6 -> Color(0xFF000000L or value)
            8 -> Color(value)
            else -> null
        }
    }
    return parsed ?: FallbackAccents[(id.hashCode() and Int.MAX_VALUE) % FallbackAccents.size]
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

/**
 * The catalog's search box: a quiet pill, the same shape as the comment field, so the page keeps one
 * kind of "type here" control.
 *
 * It searches the replaced package names as well as the prose, because pasting a package out of a
 * debloat list is how people arrive at "what can replace this" — the name of the app is the answer,
 * not the thing they know.
 */
@Composable
private fun CatalogSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    inset: Dp = 6.dp,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, top = 10.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = "搜应用、作者，或系统包名",
                    fontSize = 12.sp,
                    color = colors.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 13.sp, color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                singleLine = true,
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "清空搜索",
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onQueryChange("") }
                    .padding(5.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

/** Nothing matched: say what was searched, so a typo is visible rather than mysterious. */
@Composable
private fun NoHits(query: String, inset: Dp = 6.dp) {
    Text(
        text = "没有匹配「$query」的应用。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = inset, end = inset, top = 14.dp),
    )
}