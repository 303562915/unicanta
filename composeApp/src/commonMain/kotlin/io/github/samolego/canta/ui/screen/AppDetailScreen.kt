package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.community.AppNoteStore
import io.github.samolego.canta.data.community.AppReview
import io.github.samolego.canta.ui.component.AppBadge
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.DeviceProfile
import kotlinx.coroutines.launch

/**
 * One app's own page: what it is, whether this phone can live without it, and what other
 * owners of the same phone said about removing it.
 *
 * The verdict on this page is the community's: a like means "this can go", which is the
 * same meaning a like has in the uninstall ranking. Reviews carry the phone they were
 * written on — a note from another model says nothing about yours, so it is shown as
 * context rather than as advice.
 */
@Composable
fun AppDetailScreen(
    packageName: String,
    app: AppInfo?,
    profile: DeviceProfile?,
    notes: AppNoteStore,
    author: String,
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var reviews by remember { mutableStateOf<List<AppReview>>(emptyList()) }
    var liked by remember { mutableStateOf(false) }
    var likes by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(packageName) {
        reviews = notes.reviewsOf(packageName)
        liked = packageName in notes.likedPackages.value
        // The count starts from this install's own verdict; the ranking adds the rest.
        likes = if (liked) 1 else 0
    }

    val toggleLike: () -> Unit = {
        scope.launch {
            liked = notes.togglePackageLike(packageName)
            likes = if (liked) 1 else 0
        }
    }

    val send: () -> Unit = {
        val body = draft.trim()
        if (body.isNotEmpty()) {
            val review = AppReview(
                author = author.ifEmpty { "u-anon" },
                body = body,
                model = profile?.displayModel.orEmpty(),
                sdk = profile?.sdk ?: 0,
                createdAt = "",
                removable = liked,
            )
            scope.launch {
                notes.addReview(packageName, review)
                reviews = notes.reviewsOf(packageName)
                draft = ""
            }
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = app?.name ?: packageName,
                onNavigateBack = onNavigateBack,
                actions = {
                    FlBarButton(
                        glyph = if (liked) FlGlyphs.checkCircle else FlGlyphs.check,
                        contentDescription = if (liked) "已标记可卸载" else "标记为可卸载",
                        selected = liked,
                        onClick = toggleLike,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item { AppHero(app = app, packageName = packageName, likes = likes, liked = liked, onToggleLike = toggleLike) }
            item { SectionHeading("评价") }
            if (reviews.isEmpty()) {
                item {
                    Text(
                        text = "还没有人评价这个应用。写一条，告诉别人它在你的机型上是什么样。",
                        style = FlText.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(reviews) { review -> ReviewRow(review) }
        }

        ReviewComposer(
            draft = draft,
            onDraftChange = { draft = it },
            onSend = send,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** The app itself: icon, name, package, version, size, and the verdict with its count. */
@Composable
private fun AppHero(
    app: AppInfo?,
    packageName: String,
    likes: Int,
    liked: Boolean,
    onToggleLike: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (app != null) {
                AppIconImage(app, size = 56.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surfaceContainerHighest),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app?.name ?: packageName,
                    style = FlText.title3,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = packageName,
                    style = FlText.caption2.copy(fontFamily = FontFamily.Monospace),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val facts = buildList {
                    app?.let { add("v${it.versionName}") }
                    app?.apkSize?.let { add(formatSize(it)) }
                    if (app?.isSystemApp == true) add("系统应用")
                    if (app?.isUninstalled == true) add("已卸载")
                }
                if (facts.isNotEmpty()) {
                    Text(
                        text = facts.joinToString(" · "),
                        style = FlText.caption1,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        app?.badgeInfo?.let { badge ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppBadge(badge)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = badge.description,
                    style = FlText.caption1,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        app?.description?.takeIf { it.isNotBlank() }?.let { text ->
            Spacer(Modifier.height(8.dp))
            Text(text = text, style = FlText.footnote, color = scheme.onSurface)
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(if (liked) scheme.primary else FlSurfaceLevel.Page.raised.colorOf(scheme))
                .clickable(onClick = onToggleLike)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlGlyphIconInline(
                glyph = if (liked) FlGlyphs.checkCircle else FlGlyphs.check,
                tint = if (liked) scheme.onPrimary else scheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (liked) "已标记：可以卸载" else "标记：可以卸载",
                style = FlText.footnote.copy(fontWeight = io.github.samolego.canta.ui.fl.FlText.emphasized),
                color = if (liked) scheme.onPrimary else scheme.onSurface,
            )
            if (likes > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$likes",
                    style = FlText.caption1,
                    color = if (liked) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReviewRow(review: AppReview) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.medium)
            .drawBehind { drawRect(FlSurfaceLevel.Page.raised.colorOf(scheme)) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = review.author,
                style = FlText.caption1.copy(fontWeight = io.github.samolego.canta.ui.fl.FlText.emphasized),
                color = scheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            // The phone and its Android version: a review is only worth as much as the
            // device it was written on.
            val device = buildList {
                if (review.model.isNotBlank()) add(review.model)
                if (review.sdk > 0) add("Android ${review.sdk}")
            }.joinToString(" · ")
            if (device.isNotEmpty()) {
                Text(
                    text = device,
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            if (review.removable) {
                Text(
                    text = "可卸",
                    style = FlText.caption2,
                    color = scheme.primary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(text = review.body, style = FlText.footnote, color = scheme.onSurface)
    }
}

@Composable
private fun ReviewComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(percent = 50))
            .drawBehind { drawRect(FlSurfaceLevel.floatingOf(scheme).colorOf(scheme)) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (draft.isEmpty()) {
                Text(
                    text = "写一条评价…",
                    style = FlText.footnote,
                    color = scheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = FlText.footnote.copy(color = scheme.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FlBarButton(
            glyph = FlGlyphs.send,
            contentDescription = "发送",
            onClick = onSend,
            filled = false,
            size = 34.dp,
            iconSize = 20.dp,
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = FlText.headline,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FlGlyphIconInline(
    glyph: io.github.samolego.canta.ui.fl.FlGlyph,
    tint: androidx.compose.ui.graphics.Color,
) {
    io.github.samolego.canta.ui.fl.FlGlyphIcon(glyph = glyph, size = 16.dp, tint = tint, fill = 1f)
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) "${(mb * 10).toInt() / 10.0} MB" else "${bytes / 1024} KB"
}
