package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.APP_NAME
import io.github.samolego.canta.BuildInfo
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.core.UpdateDownload
import io.github.samolego.canta.core.UpdateDownloadFailure
import io.ktor.client.HttpClient
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.about_check_update
import io.github.samolego.canta.generated.resources.about_check_update_action
import io.github.samolego.canta.generated.resources.about_check_update_hint
import io.github.samolego.canta.generated.resources.about_auto_install
import io.github.samolego.canta.generated.resources.about_download_cancel
import io.github.samolego.canta.generated.resources.about_download_failed_network
import io.github.samolego.canta.generated.resources.about_download_failed_storage
import io.github.samolego.canta.generated.resources.about_download_failed_timeout
import io.github.samolego.canta.generated.resources.about_download_failed_unsupported
import io.github.samolego.canta.generated.resources.about_download_page
import io.github.samolego.canta.generated.resources.about_downloading
import io.github.samolego.canta.generated.resources.about_downloading_unknown
import io.github.samolego.canta.generated.resources.about_ready_to_install
import io.github.samolego.canta.generated.resources.about_installing
import io.github.samolego.canta.generated.resources.about_install_title
import io.github.samolego.canta.generated.resources.about_install_message
import io.github.samolego.canta.generated.resources.about_install_confirm
import io.github.samolego.canta.generated.resources.about_install_cancel
import io.github.samolego.canta.generated.resources.about_download_failed
import io.github.samolego.canta.generated.resources.about_checking
import io.github.samolego.canta.generated.resources.about_contributors
import io.github.samolego.canta.generated.resources.about_coolapk
import io.github.samolego.canta.generated.resources.about_coolapk_hint
import io.github.samolego.canta.generated.resources.about_original_project
import io.github.samolego.canta.generated.resources.about_qq_group
import io.github.samolego.canta.generated.resources.about_qq_group_copied
import io.github.samolego.canta.generated.resources.about_qq_group_hint
import io.github.samolego.canta.generated.resources.about_tagline
import io.github.samolego.canta.generated.resources.about_title
import io.github.samolego.canta.generated.resources.about_update_available
import io.github.samolego.canta.generated.resources.about_update_download_toast
import io.github.samolego.canta.generated.resources.about_update_failed
import io.github.samolego.canta.generated.resources.about_update_failed_bad_answer
import io.github.samolego.canta.generated.resources.about_update_failed_network
import io.github.samolego.canta.generated.resources.about_update_failed_no_release
import io.github.samolego.canta.generated.resources.about_update_failed_rate_limited
import io.github.samolego.canta.generated.resources.about_update_failed_server
import io.github.samolego.canta.generated.resources.about_update_failed_timeout
import io.github.samolego.canta.generated.resources.about_up_to_date
import io.github.samolego.canta.generated.resources.about_update_store_managed
import io.github.samolego.canta.generated.resources.auto_update_bloat_list
import io.github.samolego.canta.generated.resources.about_install_handoff
import io.github.samolego.canta.generated.resources.confirm_uninstall
import io.github.samolego.canta.generated.resources.hide_success_dialog
import io.github.samolego.canta.generated.resources.settings
import io.github.samolego.canta.generated.resources.settings_bar_slots_hint
import io.github.samolego.canta.generated.resources.tab_apps
import io.github.samolego.canta.generated.resources.tab_recommend
import io.github.samolego.canta.generated.resources.tab_rules
import io.github.samolego.canta.generated.resources.tab_tweaks
import io.github.samolego.canta.generated.resources.update_channel
import io.github.samolego.canta.generated.resources.update_channel_dev
import io.github.samolego.canta.generated.resources.update_channel_stable
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsChevron
import io.github.samolego.canta.ui.component.SettingsSwitch
import io.github.samolego.canta.ui.component.ThemeToastHost
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Where the app is published and where it came from. */
private const val REPO_URL = "https://github.com/303562915/unicanta"
private const val CONTRIBUTORS_URL = "$REPO_URL/graphs/contributors"
private const val ORIGINAL_URL = "https://github.com/samolego/Canta"
/** The author's Coolapk page, and the number at the end of it. */
private const val COOLAPK_UID = "43186862"
private const val COOLAPK_URL = "https://www.coolapk.com/u/$COOLAPK_UID"

/**
 * What Coolapk answers to, best first: the scheme spelled with the page's host is the form the app
 * registers for its own site, the short one is the older spelling some builds still use. The web
 * page is the fallback, so a phone without Coolapk installed loses nothing.
 */
private val COOLAPK_SCHEMES = listOf(
    "coolapk://www.coolapk.com/u/$COOLAPK_UID",
    "coolapk://u/$COOLAPK_UID",
)

/** The author's group, and the two spellings QQ accepts for a group card. */
private const val QQ_GROUP = "913795959"
private val QQ_GROUP_SCHEMES = listOf(
    "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$QQ_GROUP&card_type=group&source=qrcode",
    "mqq://card/show_pslcard?uin=$QQ_GROUP&card_type=group",
)

/** Side inset of a card, the same as the settings page uses. */
private val CardInset = 13.dp

/** Corner of every card on this page. */
private val CardCorner = 12.dp

/** Height the icon tile is drawn at. */
private val IconTile = 62.dp

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
    val showDevicePhoto by settingsViewModel.showDevicePhoto.collectAsStateWithLifecycle()

    val autoInstallUpdates by settingsViewModel.autoInstallUpdates.collectAsStateWithLifecycle()
    val updateChannel by settingsViewModel.updateChannel.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val client = remember { HttpClient() }
    // The page's own toast host, painted in the theme colour; the check and the download report here.
    val toast = remember { SnackbarHostState() }
    var updateState by remember(updateChannel) { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var pendingInstall by remember { mutableStateOf<UpdateState.ReadyToInstall?>(null) }
    var updateJob by remember { mutableStateOf<Job?>(null) }
    // What a cancelled download can go back to: the release it was fetching, with its label.
    var downloadable by remember { mutableStateOf<Pair<String, UpdateRelease>?>(null) }
    LaunchedEffect(updateChannel) {
        updateJob?.cancel()
        updateJob = null
        pendingInstall = null
        downloadable = null
    }
    // Read here, not in the tap handler: `getString` suspends, and a click is not a suspend call.
    val qqGroupCopied = stringResource(Res.string.about_qq_group_copied)

    val download: (UpdateRelease) -> Unit = { release ->
        val version = if (release.buildNumber > 0) "${release.version} (dev ${release.buildNumber})" else release.version
        updateState = UpdateState.Downloading(version, DOWNLOAD_PROGRESS_UNKNOWN)
        downloadable = version to release
        // A download runs in the background: say so in the theme's own colour before it starts.
        scope.launch { toast.showSnackbar(getString(Res.string.about_update_download_toast, version)) }
        updateJob = scope.launch {
            val result = downloadRelease(platform, release.tag, release.buildNumber) { percent ->
                updateState = UpdateState.Downloading(version, percent)
            }
            downloadable = null
            // A failure carries its reason, so the page can name it and leave the browser as the
            // way out instead of only offering the same download again.
            updateState = when (result) {
                is UpdateDownload.Done -> UpdateState.ReadyToInstall(version, result.path)
                is UpdateDownload.Failed ->
                    UpdateState.FailedWith(getString(result.failure.reason()), result.failure)
            }
        }
    }

    // A download that is not moving is something the reader has to be able to stop, and a stopped
    // download goes back to the release it was fetching rather than to an empty page.
    val cancelDownload: () -> Unit = {
        updateJob?.cancel()
        updateJob = null
        updateState = downloadable
            ?.let { (version, release) -> UpdateState.Available(version, release) }
            ?: UpdateState.Idle
        downloadable = null
    }

    val checkUpdate: () -> Unit = checkUpdate@{
        if (updateChannel != null && updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading && updateState !is UpdateState.Installing) {
            // A store build cannot be replaced by this repo's APK: its signature is the store's.
            if (platform.isInstalledFromFdroid()) {
                updateState = UpdateState.StoreManaged
                return@checkUpdate
            }
            updateState = UpdateState.Checking
            updateJob = scope.launch {
                val channel = if (updateChannel == "dev") UpdateChannel.DEVELOPMENT else UpdateChannel.STABLE
                val found = try {
                    latestRelease(client, channel)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: UpdateCheckException) {
                    updateState = UpdateState.Failed(e.failure)
                    return@launch
                } catch (e: Exception) {
                    updateState = UpdateState.Failed(UpdateFailure.UNKNOWN)
                    return@launch
                }
                val label = if (found.buildNumber > 0) "${found.version} (dev ${found.buildNumber})" else found.version
                when {
                    !found.isNewerThanInstalled() -> updateState = UpdateState.Latest
                    // Downloading on its own is what the switch is for; it is on by default.
                    autoInstallUpdates -> download(found)
                    else -> updateState = UpdateState.Available(label, found)
                }
            }
        }
    }

    // The download is done: ask before replacing a running app.
    LaunchedEffect(updateState) {
        val ready = updateState
        if (ready is UpdateState.ReadyToInstall) pendingInstall = ready
    }

    Scaffold(
        topBar = {
            ScreenTopBar(title = stringResource(Res.string.about_title), onNavigateBack = onNavigateBack)
        },
        // Notices land above the bottom edge, in the app's own colour rather than Material's grey.
        snackbarHost = { ThemeToastHost(hostState = toast) },
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
                onOpenRelease = {
                    (updateState as? UpdateState.Available)?.let { download(it.release) }
                },
            )
            // The two ways out of an update that is not arriving. A download in flight can be
            // stopped, and anything that failed can be fetched from the download page in a browser
            // — the same page the check reads, which needs nothing from this app.
            when {
                updateState is UpdateState.Downloading -> {
                    Spacer(Modifier.height(10.dp))
                    LinkCard(
                        glyph = FlGlyphs.close,
                        title = stringResource(Res.string.about_download_cancel),
                        onClick = cancelDownload,
                    )
                }

                (updateState as? UpdateState.FailedWith)?.download != null ||
                    updateState is UpdateState.Failed -> {
                    Spacer(Modifier.height(10.dp))
                    LinkCard(
                        glyph = FlGlyphs.link,
                        title = stringResource(Res.string.about_download_page),
                        subtitle = DOWNLOAD_PAGE_URL,
                        onClick = { platform.openUrl(DOWNLOAD_PAGE_URL) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            UpdateChannelCard(
                channel = updateChannel,
                onChannelSelected = { settingsViewModel.saveUpdateChannel(it) },
            )
            Spacer(Modifier.height(10.dp))

            ToggleCard(
                glyph = FlGlyphs.cloudDownload,
                title = stringResource(Res.string.about_auto_install),
                checked = autoInstallUpdates,
                onCheckedChange = { enabled ->
                    settingsViewModel.saveAutoInstallUpdates(enabled)
                    // Turning it on with a release already waiting starts the download right away.
                    val pending = updateState
                    if (enabled && pending is UpdateState.Available) download(pending.release)
                },
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

            // The bar itself, as one row of five: the three pages everyone needs are lit and
            // cannot be tapped, the other two are the switches — a page shows once its slot is on.
            BarSlotsCard(
                showTweaks = showTweaksTab,
                showRecommend = showRecommendTab,
                onToggleTweaks = settingsViewModel::saveShowTweaksTab,
                onToggleRecommend = settingsViewModel::saveShowRecommendTab,
            )

            // The name and the photo at the top of the safety page: both are decoration, and the
            // model string is more than some users want on screen, so they share one switch and it
            // starts off. Turned on, the picture is looked up and the model names the band.
            ToggleCard(
                glyph = FlGlyphs.iconTile,
                title = "显示机型与照片",
                checked = showDevicePhoto,
                onCheckedChange = settingsViewModel::saveShowDevicePhoto,
            )

            Spacer(Modifier.height(6.dp))
            // The author first: the two entries people look for when they want to ask something,
            // each opened in its own app when it is installed and on the web page otherwise.
            LinkCard(
                glyph = FlGlyphs.profiles,
                title = stringResource(Res.string.about_coolapk),
                subtitle = stringResource(Res.string.about_coolapk_hint),
                onClick = { platform.openLink(COOLAPK_URL, *COOLAPK_SCHEMES.toTypedArray()) },
            )
            LinkCard(
                glyph = FlGlyphs.link,
                title = stringResource(Res.string.about_qq_group),
                subtitle = stringResource(Res.string.about_qq_group_hint),
                onClick = {
                    // No web page can join a group without an invite key, so there is nothing to
                    // fall back to: no QQ means the number is copied instead.
                    val opened = platform.openLink("", *QQ_GROUP_SCHEMES.toTypedArray())
                    // No QQ on this phone: the number is what a person needs, so hand it over
                    // instead of opening a page that cannot join anything.
                    if (!opened) {
                        platform.copyToClipboard(QQ_GROUP)
                        platform.showMessage(qqGroupCopied)
                    }
                },
            )
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

        // The install replaces the app and closes it, so it always asks first — the switch only
        // decides who downloads, never whether the user gets told.
        pendingInstall?.let { ready ->
            AlertDialog(
                onDismissRequest = { pendingInstall = null },
                title = { Text(stringResource(Res.string.about_install_title, ready.version)) },
                text = { Text(stringResource(Res.string.about_install_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingInstall = null
                        updateState = UpdateState.Installing(ready.version, ready.path)
                        scope.launch {
                            val failure = platform.installUpdate(ready.path)
                            if (failure == null) {
                                platform.exitApp()
                            } else if (platform.requestInstall(ready.path)) {
                                // The privileged runtime could not read any home for the APK, but
                                // the system installer reads through the granted content URI, so
                                // the same file still installs — with the system's own dialog.
                                updateState = UpdateState.HandedOff(ready.version)
                            } else {
                                updateState = UpdateState.FailedWith(failure)
                            }
                        }
                    }) { Text(stringResource(Res.string.about_install_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingInstall = null }) {
                        Text(stringResource(Res.string.about_install_cancel))
                    }
                },
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
 *
 * A failed check reads as the reason it failed, not as one generic sentence: on a phone behind a
 * proxy the difference between "your node is dead", "GitHub counted this exit IP as spent" and
 * "upstream answered badly" decides what the user does next. Either way the tap retries.
 */
@Composable
private fun UpdateCard(
    state: UpdateState,
    onCheck: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val failed = state is UpdateState.Failed || state is UpdateState.FailedWith
    val (statusText, statusColor) = when (state) {
        UpdateState.Idle -> stringResource(Res.string.about_check_update_hint) to scheme.onSurfaceVariant
        UpdateState.Checking -> stringResource(Res.string.about_checking) to scheme.onSurfaceVariant
        UpdateState.Latest -> stringResource(Res.string.about_up_to_date) to scheme.primary
        is UpdateState.Available ->
            stringResource(Res.string.about_update_available, state.version) to scheme.primary
        is UpdateState.Failed -> state.failure.reason() to scheme.error
        UpdateState.StoreManaged -> stringResource(Res.string.about_update_store_managed) to scheme.onSurfaceVariant
        is UpdateState.Downloading ->
            // No announced size: show that bytes are moving instead of a number that would sit at
            // 0% for the whole transfer.
            (if (state.progress == DOWNLOAD_PROGRESS_UNKNOWN) {
                stringResource(Res.string.about_downloading_unknown)
            } else {
                stringResource(Res.string.about_downloading, state.progress)
            }) to scheme.primary
        is UpdateState.ReadyToInstall ->
            stringResource(Res.string.about_ready_to_install, state.version) to scheme.primary
        is UpdateState.Installing ->
            stringResource(Res.string.about_installing) to scheme.primary
        is UpdateState.HandedOff ->
            stringResource(Res.string.about_install_handoff, state.version) to scheme.primary
        is UpdateState.FailedWith -> state.reason to scheme.error
    }
    val openRelease = state is UpdateState.Available
    val busy = state == UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardInset)
            .clip(RoundedCornerShape(CardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            // A failure is the one state where a second tap is the obvious next move, so the row
            // keeps working as the retry button instead of only reporting.
            .clickable(enabled = !busy) { if (openRelease) onOpenRelease() else onCheck() }
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
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = statusText,
                    style = FlText.caption2,
                    color = statusColor,
                    // A reason names a proxy or a quota, so it is allowed to wrap rather than clip.
                    maxLines = if (failed) 3 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
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

/** One sentence per reason, naming the thing the user can act on. */
@Composable
private fun UpdateFailure.reason(): String = stringResource(
    when (this) {
        UpdateFailure.RATE_LIMITED -> Res.string.about_update_failed_rate_limited
        UpdateFailure.TIMEOUT -> Res.string.about_update_failed_timeout
        UpdateFailure.NETWORK -> Res.string.about_update_failed_network
        UpdateFailure.SERVER -> Res.string.about_update_failed_server
        UpdateFailure.NO_RELEASE -> Res.string.about_update_failed_no_release
        UpdateFailure.MALFORMED -> Res.string.about_update_failed_bad_answer
        UpdateFailure.UNKNOWN -> Res.string.about_update_failed
    },
)

/**
 * The same for a download that did not finish. Every one of these is read together with the
 * download-page row under the card, so each sentence only has to say what went wrong.
 */
private fun UpdateDownloadFailure.reason(): StringResource = when (this) {
    UpdateDownloadFailure.NETWORK -> Res.string.about_download_failed_network
    UpdateDownloadFailure.TIMEOUT -> Res.string.about_download_failed_timeout
    UpdateDownloadFailure.STORAGE -> Res.string.about_download_failed_storage
    UpdateDownloadFailure.UNSUPPORTED -> Res.string.about_download_failed_unsupported
    UpdateDownloadFailure.UNKNOWN -> Res.string.about_download_failed
}

@Composable
private fun UpdateChannelCard(channel: String?, onChannelSelected: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardInset)
            .clip(RoundedCornerShape(CardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable(enabled = channel != null) { expanded = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.update_channel),
            style = FlText.subheadline,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box {
            Text(
                text = stringResource(if (channel == "dev") Res.string.update_channel_dev else Res.string.update_channel_stable),
                style = FlText.subheadline,
                color = scheme.onSurfaceVariant,
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.update_channel_stable)) },
                    onClick = {
                        expanded = false
                        onChannelSelected("stable")
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.update_channel_dev)) },
                    onClick = {
                        expanded = false
                        onChannelSelected("dev")
                    },
                )
            }
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

/** One entry of the bottom bar: [glyph], [label], whether it is lit, and whether it takes a tap. */
private data class BarSlot(
    val glyph: FlGlyph,
    val label: String,
    val lit: Boolean,
    val onToggle: (() -> Unit)?,
)

/**
 * The bar as one row of five slots. The three pages everyone needs are always lit and cannot be
 * tapped; the tools page and the picks page are switches, and a page only gets an entry in the bar
 * while its slot is lit. This is the only place the two optional pages are turned on.
 */
@Composable
private fun BarSlotsCard(
    showTweaks: Boolean,
    showRecommend: Boolean,
    onToggleTweaks: (Boolean) -> Unit,
    onToggleRecommend: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val slots = listOf(
        BarSlot(FlGlyphs.dashboard, stringResource(Res.string.tab_apps), lit = true, onToggle = null),
        BarSlot(FlGlyphs.rules, stringResource(Res.string.tab_rules), lit = true, onToggle = null),
        BarSlot(
            FlGlyphs.tools,
            stringResource(Res.string.tab_tweaks),
            lit = showTweaks,
            onToggle = { onToggleTweaks(!showTweaks) },
        ),
        BarSlot(
            FlGlyphs.appsList,
            stringResource(Res.string.tab_recommend),
            lit = showRecommend,
            onToggle = { onToggleRecommend(!showRecommend) },
        ),
        BarSlot(FlGlyphs.settings, stringResource(Res.string.settings), lit = true, onToggle = null),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardInset, vertical = 4.dp)
            .clip(RoundedCornerShape(CardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .padding(horizontal = 10.dp, vertical = 11.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_bar_slots_hint),
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(9.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            slots.forEach { slot -> BarSlotChip(slot, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun BarSlotChip(slot: BarSlot, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val lit = slot.lit
    val toggle = slot.onToggle
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (toggle != null) Modifier.clickable(onClick = toggle) else Modifier)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (lit) scheme.primary else scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(
                glyph = slot.glyph,
                size = 18.dp,
                tint = if (lit) scheme.onPrimary else scheme.onSurfaceVariant,
                fill = if (lit) 1f else 0f,
            )
        }
        Text(
            text = slot.label,
            style = FlText.caption2,
            color = if (lit) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row that opens something outside the app: [title] with the address under it, when known. */
@Composable
private fun LinkCard(
    glyph: FlGlyph,
    title: String,
    subtitle: String = "",
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = FlText.subheadline,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SettingsChevron()
    }
}
