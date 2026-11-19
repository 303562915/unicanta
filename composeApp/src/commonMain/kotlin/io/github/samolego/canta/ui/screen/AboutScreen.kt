package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.APP_NAME
import io.github.samolego.canta.BuildInfo
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.about_check_update
import io.github.samolego.canta.generated.resources.about_check_update_action
import io.github.samolego.canta.generated.resources.about_check_update_hint
import io.github.samolego.canta.generated.resources.about_checking
import io.github.samolego.canta.generated.resources.about_contributors
import io.github.samolego.canta.generated.resources.about_original_project
import io.github.samolego.canta.generated.resources.about_tagline
import io.github.samolego.canta.generated.resources.about_title
import io.github.samolego.canta.generated.resources.about_update_available
import io.github.samolego.canta.generated.resources.about_update_failed
import io.github.samolego.canta.generated.resources.about_up_to_date
import io.github.samolego.canta.generated.resources.auto_update_bloat_list
import io.github.samolego.canta.generated.resources.confirm_uninstall
import io.github.samolego.canta.generated.resources.hide_success_dialog
import io.github.samolego.canta.generated.resources.settings_show_recommend
import io.github.samolego.canta.generated.resources.settings_show_tweaks
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsChevron
import io.github.samolego.canta.ui.component.SettingsSwitch
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Where the app is published and where it came from. */
private const val REPO_URL = "https://github.com/303562915/Canta"
private const val CONTRIBUTORS_URL = "$REPO_URL/graphs/contributors"
private const val ORIGINAL_URL = "https://github.com/samolego/Canta"
private const val RELEASES_URL = "$REPO_URL/releases"
private const val LATEST_RELEASE_API = "https://api.github.com/repos/303562915/Canta/releases/latest"

/** Side inset of a card, the same as the settings page uses. */
private val CardInset = 13.dp

/** Corner of every card on this page. */
private val CardCorner = 12.dp

/** Height the icon tile is drawn at. */
private val IconTile = 62.dp

/** What the last check said. */
private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Latest : UpdateState
    data class Available(val version: String) : UpdateState
    data object Failed : UpdateState
}

/**
 * The about page, laid out the way FlClash lays out its own: the app on top, then the switches
 * that decide how it behaves, then where it lives.
 *
 * The five switches are one card each rather than rows inside one card: every one of them is a
 * decision on its own, and a page with five separate settings reads faster than a list.
 */
@Composable
fun AboutScreen(
    platform: CantaPlatform,
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
) {
    val autoUpdateBloatList by settingsViewModel.autoUpdateBloatList.collectAsStateWithLifecycle()
    val confirmBeforeUninstall by settingsViewModel.confirmBeforeUninstall.collectAsStateWithLifecycle()
    val hideSuccessDialog by settingsViewModel.hideSuccessDialog.collectAsStateWithLifecycle()
    val showTweaksTab by settingsViewModel.showTweaksTab.collectAsStateWithLifecycle()
    val showRecommendTab by settingsViewModel.showRecommendTab.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val client = remember { HttpClient() }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    val checkUpdate: () -> Unit = {
        if (updateState != UpdateState.Checking) {
            updateState = UpdateState.Checking
            scope.launch {
                updateState = runCatching { latestRelease(client) }
                    .getOrNull()
                    ?.let { tag ->
                        val latest = tag.trimStart('v', 'V')
                        if (latest.equals(BuildInfo.VERSION_NAME, ignoreCase = true)) {
                            UpdateState.Latest
                        } else {
                            UpdateState.Available(tag)
                        }
                    }
                    ?: UpdateState.Failed
            }
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(title = stringResource(Res.string.about_title), onNavigateBack = onNavigateBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 28.dp),
        ) {
            AppHero()
            Spacer(Modifier.height(16.dp))

            UpdateCard(
                state = updateState,
                onCheck = checkUpdate,
                onOpenRelease = { platform.openUrl(RELEASES_URL) },
            )
            Spacer(Modifier.height(10.dp))

            // The five switches: each its own card, so nothing has to be read as a table.
            ToggleCard(
                glyph = FlGlyphs.cloudDownload,
                title = stringResource(Res.string.auto_update_bloat_list),
                checked = autoUpdateBloatList,
                onCheckedChange = settingsViewModel::saveAutoUpdateBloatList,
            )
            ToggleCard(
                glyph = FlGlyphs.warning,
                title = stringResource(Res.string.confirm_uninstall),
                checked = confirmBeforeUninstall,
                onCheckedChange = settingsViewModel::saveConfirmBeforeUninstall,
            )
            ToggleCard(
                glyph = FlGlyphs.check,
                title = stringResource(Res.string.hide_success_dialog),
                checked = hideSuccessDialog,
                onCheckedChange = settingsViewModel::saveHideSuccessDialog,
            )
            ToggleCard(
                glyph = FlGlyphs.tools,
                title = stringResource(Res.string.settings_show_tweaks),
                checked = showTweaksTab,
                onCheckedChange = settingsViewModel::saveShowTweaksTab,
            )
            ToggleCard(
                glyph = FlGlyphs.appsList,
                title = stringResource(Res.string.settings_show_recommend),
                checked = showRecommendTab,
                onCheckedChange = settingsViewModel::saveShowRecommendTab,
            )

            Spacer(Modifier.height(6.dp))
            LinkCard(
                glyph = FlGlyphs.profiles,
                title = stringResource(Res.string.about_contributors),
                onClick = { platform.openUrl(CONTRIBUTORS_URL) },
            )
            LinkCard(
                glyph = FlGlyphs.link,
                title = stringResource(Res.string.about_original_project),
                onClick = { platform.openUrl(ORIGINAL_URL) },
            )
        }
    }
}

/** The app itself: icon tile, name, version, one line about what it does. */
@Composable
private fun AppHero() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardInset + 3.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(IconTile)
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(glyph = FlGlyphs.iconTile, size = 32.dp, tint = scheme.onPrimary, fill = 1f)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text = APP_NAME, style = FlText.title2, color = scheme.onSurface)
            Text(
                text = "v${BuildInfo.VERSION_NAME}",
                style = FlText.footnote,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.about_tagline),
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The update row: what the last check said, and the tap that runs another one. The whole row is
 * the button, so there is nothing small to aim at.
 */
@Composable
private fun UpdateCard(
    state: UpdateState,
    onCheck: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (statusText, statusColor) = when (state) {
        UpdateState.Idle -> stringResource(Res.string.about_check_update_hint) to scheme.onSurfaceVariant
        UpdateState.Checking -> stringResource(Res.string.about_checking) to scheme.onSurfaceVariant
        UpdateState.Latest -> stringResource(Res.string.about_up_to_date) to scheme.primary
        is UpdateState.Available ->
            stringResource(Res.string.about_update_available, state.version) to scheme.primary
        UpdateState.Failed -> stringResource(Res.string.about_update_failed) to scheme.error
    }
    val openRelease = state is UpdateState.Available

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardInset)
            .clip(RoundedCornerShape(CardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable { if (openRelease) onOpenRelease() else onCheck() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.about_check_update),
                style = FlText.subheadline.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state == UpdateState.Checking) {
                    CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(text = statusText, style = FlText.caption2, color = statusColor)
            }
        }
        if (openRelease) {
            SettingsChevron()
        } else {
            Text(
                text = stringResource(Res.string.about_check_update_action),
                style = FlText.caption1,
                color = scheme.primary,
            )
        }
    }
}

/** One switch, in its own card. */
@Composable
private fun ToggleCard(
    glyph: FlGlyph,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardInset, vertical = 4.dp)
            .clip(RoundedCornerShape(CardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIcon(glyph = glyph, size = 19.dp, tint = scheme.primary, fill = 0.2f)
        Spacer(Modifier.width(11.dp))
        Text(
            text = title,
            style = FlText.subheadline,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        SettingsSwitch(checked)
    }
}

/** A row that opens something outside the app. */
@Composable
private fun LinkCard(
    glyph: FlGlyph,
    title: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardInset, vertical = 4.dp)
            .clip(RoundedCornerShape(CardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIcon(glyph = glyph, size = 19.dp, tint = scheme.primary, fill = 0.2f)
        Spacer(Modifier.width(11.dp))
        Text(
            text = title,
            style = FlText.subheadline,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SettingsChevron()
    }
}

/** `tag_name` of the latest release, e.g. `v4.2.0`; throws when GitHub cannot be reached. */
private suspend fun latestRelease(client: HttpClient): String {
    val body = client.get(LATEST_RELEASE_API).bodyAsText()
    return Json.parseToJsonElement(body)
        .jsonObject["tag_name"]
        ?.jsonPrimitive
        ?.content
        ?.takeIf { it.isNotBlank() }
        ?: error("no tag in the release payload")
}
