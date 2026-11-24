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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
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
import io.github.samolego.canta.core.INSTALL_PROGRESS_UNKNOWN
import io.github.samolego.canta.core.InstallStage
import io.github.samolego.canta.core.UpdateDownload
import io.github.samolego.canta.core.UpdateDownloadFailure
import io.github.samolego.canta.util.PackageTelemetry
import io.github.samolego.canta.util.UpdateReport
import io.ktor.client.HttpClient
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.about_check_update
import io.github.samolego.canta.generated.resources.about_check_update_action
import io.github.samolego.canta.generated.resources.about_check_update_hint
import io.github.samolego.canta.generated.resources.about_auto_install
import io.github.samolego.canta.generated.resources.about_auto_install_retry
import io.github.samolego.canta.generated.resources.about_auto_install_retry_hint
import io.github.samolego.canta.generated.resources.about_confirm_install
import io.github.samolego.canta.generated.resources.about_download_cancel
import io.github.samolego.canta.generated.resources.about_download_failed_network
import io.github.samolego.canta.generated.resources.about_download_failed_storage
import io.github.samolego.canta.generated.resources.about_download_failed_timeout
import io.github.samolego.canta.generated.resources.about_download_failed_unsupported
import io.github.samolego.canta.generated.resources.about_download_page
import io.github.samolego.canta.generated.resources.got_it
import io.github.samolego.canta.generated.resources.about_downloading
import io.github.samolego.canta.generated.resources.about_downloading_unknown
import io.github.samolego.canta.generated.resources.about_ready_to_install
import io.github.samolego.canta.generated.resources.about_installing_version
import io.github.samolego.canta.generated.resources.about_install_done
import io.github.samolego.canta.generated.resources.about_install_failed_title
import io.github.samolego.canta.generated.resources.about_install_title
import io.github.samolego.canta.generated.resources.about_install_message
import io.github.samolego.canta.generated.resources.about_install_confirm
import io.github.samolego.canta.generated.resources.about_install_cancel
import io.github.samolego.canta.generated.resources.about_download_failed
import io.github.samolego.canta.generated.resources.about_checking
import io.github.samolego.canta.generated.resources.about_contributors
import io.github.samolego.canta.generated.resources.about_coolapk
import io.github.samolego.canta.generated.resources.about_coolapk_hint
import io.github.samolego.canta.generated.resources.about_source_repo
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
import io.github.samolego.canta.generated.resources.about_install_handoff
import io.github.samolego.canta.generated.resources.about_install_stage_installing
import io.github.samolego.canta.generated.resources.about_install_stage_preparing
import io.github.samolego.canta.generated.resources.about_install_stage_pushing
import io.github.samolego.canta.generated.resources.about_install_stage_root
import io.github.samolego.canta.generated.resources.about_install_stage_staging
import io.github.samolego.canta.generated.resources.update_channel
import io.github.samolego.canta.generated.resources.update_channel_dev
import io.github.samolego.canta.generated.resources.update_channel_stable
import io.github.samolego.canta.ui.component.AppCardCorner
import io.github.samolego.canta.ui.component.AppCardInset
import io.github.samolego.canta.ui.component.AppLinkCard
import io.github.samolego.canta.ui.component.AppToggleCard
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsChevron
import io.github.samolego.canta.ui.component.ThemeToastHost
import io.github.samolego.canta.ui.component.UnreadDot
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.TimeSource

/** Where the app is published: the only repository the page points at. */
private const val REPO_URL = "https://github.com/303562915/unicanta"
private const val CONTRIBUTORS_URL = "$REPO_URL/graphs/contributors"
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

/** Height the icon tile is drawn at. */
private val IconTile = 62.dp

/**
 * The about page, laid out the way FlClash lays out its own: the app on top, then the update row
 * and the two switches that decide how an update lands, then where the app lives.
 *
 * 卡片几何与两张通用卡片在 AppCards.kt 里，设置页用同一份；这里只留版本、更新与"关于作者"。
 */
@Composable
fun AboutScreen(
    platform: CantaPlatform,
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    /**
     * A newer build than this one is published, as the start-up read found it. The page does not
     * check on its own — the row is the check — so this is what puts the red dot on that row
     * before the tap. Once the tap answers, the row says the version itself and the dot is gone.
     */
    updateAvailable: Boolean = false,
    /**
     * 连点版本号的手势：原来挂在设置页，设置页拆成"我的"+"设置"之后挂到这里——
     * 版本号本来就在这一页。
     */
    onVersionTap: () -> Unit = {},
    /**
     * 更新链路的留痕：检查 / 下载 / 安装每一步结束都写一行到后端（`update_reports`）。
     *
     * 这一页是唯一能看到更新失败原因的地方，但它只在当事人手机上；记录上传之后，同一个机型上
     * 反复出现的「下载失败 / 装不上」不必再让人描述屏幕。
     */
    updateTelemetry: PackageTelemetry? = null,
) {
    val autoInstallUpdates by settingsViewModel.autoInstallUpdates.collectAsStateWithLifecycle()
    val confirmBeforeInstall by settingsViewModel.confirmBeforeInstall.collectAsStateWithLifecycle()
    val updateChannel by settingsViewModel.updateChannel.collectAsStateWithLifecycle()
    /** The build whose automatic install already failed; the page then leaves it to a manual tap. */
    val skippedAutoInstall by settingsViewModel.skippedAutoInstallVersion.collectAsStateWithLifecycle()
    // 「作者的群」那一行的红点：点过一次群就永久熄灭。
    val qqGroupSeen by settingsViewModel.qqGroupSeen.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val client = remember { HttpClient() }
    // The page's own toast host, painted in the theme colour; the check and the download report here.
    val toast = remember { SnackbarHostState() }
    var updateState by remember(updateChannel) { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var pendingInstall by remember { mutableStateOf<UpdateState.ReadyToInstall?>(null) }
    /**
     * The full per-route install report. The status row below allows three lines, which is not
     * enough to say which privilege refused: that needs a dialog of its own.
     */
    var installReport by remember { mutableStateOf<String?>(null) }
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
        val version = updateLabel(release)
        val channelName = if (updateChannel == "dev") "dev" else "stable"
        updateState = UpdateState.Downloading(version, DOWNLOAD_PROGRESS_UNKNOWN)
        downloadable = version to release
        // A download runs in the background: say so in the theme's own colour before it starts.
        scope.launch { toast.showSnackbar(getString(Res.string.about_update_download_toast, version)) }
        updateJob = scope.launch {
            val startedAt = TimeSource.Monotonic.markNow()
            // The percentage the transfer reached, so a failed download reports where it stopped
            // instead of only that it stopped.
            var reached = DOWNLOAD_PROGRESS_UNKNOWN
            // Not one shot: the rounds and the platform's resume keep a flapping proxy from
            // turning into "download failed, tap it yourself again".
            val result = downloadReleaseWithRetries(
                platform = platform,
                tag = release.tag,
                buildNumber = release.buildNumber,
                onRound = { attempt ->
                    // Every round but the first follows a failed one: back to a spinner instead of
                    // leaving an error sentence on screen while the next attempt is already running.
                    if (attempt > 1) updateState = UpdateState.Downloading(version, DOWNLOAD_PROGRESS_UNKNOWN)
                },
                onProgress = { percent ->
                    reached = percent
                    updateState = UpdateState.Downloading(version, percent)
                },
            )
            downloadable = null
            val elapsed = startedAt.elapsedNow().inWholeMilliseconds
            // A failure carries its reason, so the page can name it and leave the browser as the
            // way out instead of only offering the same download again.
            updateState = when (result) {
                is UpdateDownload.Done -> {
                    scope.launch {
                        updateTelemetry?.recordUpdate(
                            stage = UpdateReport.STAGE_DOWNLOAD,
                            outcome = UpdateReport.OUTCOME_OK,
                            channel = channelName,
                            version = version,
                            progress = 100,
                            bytes = result.bytes,
                            elapsedMs = elapsed,
                        )
                    }
                    UpdateState.ReadyToInstall(version, result.path)
                }
                is UpdateDownload.Failed -> {
                    val reason = getString(result.failure.reason())
                    scope.launch {
                        updateTelemetry?.recordUpdate(
                            stage = UpdateReport.STAGE_DOWNLOAD,
                            outcome = UpdateReport.OUTCOME_FAILED,
                            channel = channelName,
                            version = version,
                            code = result.failure.name,
                            reason = reason,
                            progress = reached,
                            elapsedMs = elapsed,
                        )
                    }
                    UpdateState.FailedWith(reason, result.failure)
                }
            }
        }
    }

    // A download that is not moving is something the reader has to be able to stop, and a stopped
    // download goes back to the release it was fetching rather than to an empty page.
    val cancelDownload: () -> Unit = {
        val stopping = downloadable?.first
        updateJob?.cancel()
        updateJob = null
        updateState = downloadable
            ?.let { (version, release) -> UpdateState.Available(version, release) }
            ?: UpdateState.Idle
        downloadable = null
        // A cancelled download is worth a line too: a build that nobody finishes downloading looks
        // exactly like a download that never started once the page is closed.
        if (stopping != null) {
            val channelName = if (updateChannel == "dev") "dev" else "stable"
            scope.launch {
                updateTelemetry?.recordUpdate(
                    stage = UpdateReport.STAGE_DOWNLOAD,
                    outcome = UpdateReport.OUTCOME_CANCELLED,
                    channel = channelName,
                    version = stopping,
                )
            }
        }
    }

    // One install path for both the switch-off case and the dialog: the APK replaces this app, so
    // a success closes it, and a refused install is handed to the system installer instead.
    val startInstall: (UpdateState.ReadyToInstall) -> Unit = { ready ->
        pendingInstall = null
        updateState = UpdateState.Installing(ready.version, ready.path)
        // Said out loud as well as in the status row: the tap that starts the install is the last
        // thing this page shows before the app is replaced.
        scope.launch { toast.showSnackbar(getString(Res.string.about_installing_version, ready.version)) }
        scope.launch {
            val channelName = if (updateChannel == "dev") "dev" else "stable"
            val startedAt = TimeSource.Monotonic.markNow()
            // Every route `pm install` is offered to is reported back here, so the row names the
            // step instead of sitting on one frozen "正在安装" line.
            val failure = platform.installUpdate(ready.path) { stage, percent ->
                updateState = UpdateState.Installing(ready.version, ready.path, stage, percent)
            }
            val elapsed = startedAt.elapsedNow().inWholeMilliseconds
            if (failure == null) {
                // Reported first, in its own coroutine: a success closes this process 1.8s later,
                // and the row has to leave before that.
                scope.launch {
                    updateTelemetry?.recordUpdate(
                        stage = UpdateReport.STAGE_INSTALL,
                        outcome = UpdateReport.OUTCOME_OK,
                        channel = channelName,
                        version = ready.version,
                        elapsedMs = elapsed,
                    )
                }
                // Not awaited: `showSnackbar` suspends until the line goes away, and the exit must
                // not wait on it — a success that closes the app before its own line is readable is
                // the "不知道装没装上" this change is about.
                scope.launch { toast.showSnackbar(getString(Res.string.about_install_done, ready.version)) }
                delay(INSTALL_DONE_LINGER_MILLIS)
                platform.exitApp()
            } else {
                // Remembered, so the start-up check stops downloading and retrying this build on
                // its own: this page is where a retry is still one tap away.
                settingsViewModel.saveSkippedAutoInstallVersion(ready.version)
                if (platform.requestInstall(ready.path)) {
                    // The privileged runtime could not read any home for the APK, but the system
                    // installer reads through the granted content URI, so the same file still
                    // installs — with the system's own dialog.
                    scope.launch {
                        updateTelemetry?.recordUpdate(
                            stage = UpdateReport.STAGE_HANDOFF,
                            outcome = UpdateReport.OUTCOME_OK,
                            channel = channelName,
                            version = ready.version,
                            // The privileged routes' own refusals: they explain why the dialog had
                            // to be the answer.
                            detail = failure,
                            elapsedMs = elapsed,
                        )
                    }
                    updateState = UpdateState.HandedOff(ready.version)
                } else {
                    scope.launch {
                        updateTelemetry?.recordUpdate(
                            stage = UpdateReport.STAGE_INSTALL,
                            outcome = UpdateReport.OUTCOME_FAILED,
                            channel = channelName,
                            version = ready.version,
                            code = "REFUSED_ALL_ROUTES",
                            reason = failure,
                            detail = failure,
                            elapsedMs = elapsed,
                        )
                    }
                    installReport = failure
                    updateState = UpdateState.FailedWith(failure)
                }
            }
        }
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
                val channelName = if (channel == UpdateChannel.DEVELOPMENT) "dev" else "stable"
                val startedAt = TimeSource.Monotonic.markNow()
                val found = try {
                    latestRelease(client, channel)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: UpdateCheckException) {
                    updateState = UpdateState.Failed(e.failure)
                    // A check that failed on someone's proxy is invisible from here; the row makes
                    // "检查更新总是失败" countable per device instead of an anecdote.
                    updateTelemetry?.recordUpdate(
                        stage = UpdateReport.STAGE_CHECK,
                        outcome = UpdateReport.OUTCOME_FAILED,
                        channel = channelName,
                        code = e.failure.name,
                        elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
                    )
                    return@launch
                } catch (e: Exception) {
                    updateState = UpdateState.Failed(UpdateFailure.UNKNOWN)
                    updateTelemetry?.recordUpdate(
                        stage = UpdateReport.STAGE_CHECK,
                        outcome = UpdateReport.OUTCOME_FAILED,
                        channel = channelName,
                        code = UpdateFailure.UNKNOWN.name,
                        detail = e.toString().take(400),
                        elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
                    )
                    return@launch
                }
                updateTelemetry?.recordUpdate(
                    stage = UpdateReport.STAGE_CHECK,
                    outcome = UpdateReport.OUTCOME_OK,
                    channel = channelName,
                    version = updateLabel(found),
                    elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
                )
                val label = updateLabel(found)
                when {
                    !found.isNewerThanInstalled() -> updateState = UpdateState.Latest
                    // Downloading on its own is what the switch is for; it is on by default. A
                    // build that already failed to install on its own is left for a manual tap:
                    // fetching it again only to fail again is the loop this mark breaks.
                    autoInstallUpdates && label != skippedAutoInstall -> download(found)
                    else -> updateState = UpdateState.Available(label, found)
                }
            }
        }
    }

    // The download is done: install it right away, or ask first when the switch says so. Asking is
    // off by default — the download was already the request, and the install takes long enough for
    // the "正在安装" line to be read before the app closes.
    LaunchedEffect(updateState, confirmBeforeInstall) {
        val ready = updateState
        if (ready is UpdateState.ReadyToInstall) {
            if (confirmBeforeInstall) pendingInstall = ready else startInstall(ready)
        }
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
            AppHero(onVersionTap = onVersionTap)
            Spacer(Modifier.height(16.dp))

            UpdateCard(
                state = updateState,
                // Only while the row has nothing of its own to say: a found release, a download or
                // an install all speak for themselves, and a dot over them would be a second voice.
                fresh = updateAvailable && updateState == UpdateState.Idle,
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
                    AppLinkCard(
                        glyph = FlGlyphs.close,
                        title = stringResource(Res.string.about_download_cancel),
                        onClick = cancelDownload,
                    )
                }

                // Every failure gets the page, not only a failed download: an install that no
                // privileged route would take is still an APK the reader can fetch and tap by hand.
                updateState is UpdateState.FailedWith || updateState is UpdateState.Failed -> {
                    Spacer(Modifier.height(10.dp))
                    AppLinkCard(
                        glyph = FlGlyphs.link,
                        title = stringResource(Res.string.about_download_page),
                        subtitle = DOWNLOAD_PAGE_URL,
                        onClick = { platform.openUrl(DOWNLOAD_PAGE_URL) },
                    )
                }
            }
            // A build that failed to install on its own is remembered, so the start-up check stops
            // retrying it. This is the way back: one tap clears the mark and asks again.
            if (skippedAutoInstall.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                AppLinkCard(
                    glyph = FlGlyphs.cloudDownload,
                    title = stringResource(Res.string.about_auto_install_retry),
                    subtitle = stringResource(Res.string.about_auto_install_retry_hint, skippedAutoInstall),
                    onClick = {
                        settingsViewModel.saveSkippedAutoInstallVersion("")
                        checkUpdate()
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            UpdateChannelCard(
                channel = updateChannel,
                onChannelSelected = { settingsViewModel.saveUpdateChannel(it) },
            )
            Spacer(Modifier.height(10.dp))

            AppToggleCard(
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
            AppToggleCard(
                glyph = FlGlyphs.warning,
                title = stringResource(Res.string.about_confirm_install),
                checked = confirmBeforeInstall,
                onCheckedChange = settingsViewModel::saveConfirmBeforeInstall,
            )
            Spacer(Modifier.height(10.dp))
            // The author first: the two entries people look for when they want to ask something,
            // each opened in its own app when it is installed and on the web page otherwise.
            AppLinkCard(
                glyph = FlGlyphs.profiles,
                title = stringResource(Res.string.about_coolapk),
                subtitle = stringResource(Res.string.about_coolapk_hint),
                onClick = { platform.openLink(COOLAPK_URL, *COOLAPK_SCHEMES.toTypedArray()) },
            )
            AppLinkCard(
                glyph = FlGlyphs.link,
                title = stringResource(Res.string.about_qq_group),
                subtitle = stringResource(Res.string.about_qq_group_hint),
                // 这个小点只有"点过群"才熄灭：它是这一页唯一在说"来这里问"的地方。
                showDot = !qqGroupSeen,
                onClick = {
                    settingsViewModel.saveQqGroupSeen(true)
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
            AppLinkCard(
                glyph = FlGlyphs.profiles,
                title = stringResource(Res.string.about_contributors),
                onClick = { platform.openUrl(CONTRIBUTORS_URL) },
            )
            AppLinkCard(
                glyph = FlGlyphs.link,
                title = stringResource(Res.string.about_source_repo),
                subtitle = REPO_URL,
                onClick = { platform.openUrl(REPO_URL) },
            )
        }

        // The install replaces the app and closes it. By default it runs as soon as the APK is here;
        // the "安装前确认" switch above brings this dialog back for anyone who wants the last word.
        pendingInstall?.let { ready ->
            // "Not now" is an answer: the version is remembered so the next launch does not fetch
            // and ask about the same build again. The row above still installs it by hand.
            val postpone = {
                settingsViewModel.saveSkippedAutoInstallVersion(ready.version)
                pendingInstall = null
                // The row must not keep saying "已下载，点击安装" for a build that was just put
                // aside: an Idle row makes the next tap of 「检查更新」 the explicit ask.
                updateState = UpdateState.Idle
            }
            AlertDialog(
                onDismissRequest = postpone,
                title = { Text(stringResource(Res.string.about_install_title, ready.version)) },
                text = { Text(stringResource(Res.string.about_install_message)) },
                confirmButton = {
                    TextButton(onClick = { startInstall(ready) }) {
                        Text(stringResource(Res.string.about_install_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = postpone) {
                        Text(stringResource(Res.string.about_install_cancel))
                    }
                },
            )
        }

        // The full report: one line per privilege that was tried, with the download page as the
        // way out that always exists. The status row below allows three lines, which cannot carry
        // "Shizuku 读不到这个目录 / root 授权超时 / 系统安装器也没打开".
        installReport?.let { report ->
            AlertDialog(
                onDismissRequest = { installReport = null },
                title = { Text(stringResource(Res.string.about_install_failed_title)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(report)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        platform.openUrl(DOWNLOAD_PAGE_URL)
                        installReport = null
                    }) { Text(stringResource(Res.string.about_download_page)) }
                },
                dismissButton = {
                    TextButton(onClick = { installReport = null }) {
                        Text(stringResource(Res.string.got_it))
                    }
                },
            )
        }
    }
}

/** The app itself: icon tile, name, version, one line about what it does. */
@Composable
private fun AppHero(onVersionTap: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset + 3.dp, vertical = 10.dp),
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
                // A development build carries the same version name as the release it was cut
                // from; the build number is the only thing that tells the two apart on screen.
                text = if (BuildInfo.DEVELOPMENT_BUILD_NUMBER > 0) {
                    "v${BuildInfo.VERSION_NAME} (dev ${BuildInfo.DEVELOPMENT_BUILD_NUMBER})"
                } else {
                    "v${BuildInfo.VERSION_NAME}"
                },
                style = FlText.footnote,
                color = scheme.onSurfaceVariant,
                // 连点版本号的那个手势（全选推荐应用的隐藏开关）挂在这一行上。
                modifier = Modifier.clickable(onClick = onVersionTap),
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
    /** A newer build is known to exist: the row wears the red dot until it says so itself. */
    fresh: Boolean = false,
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
            // 0% for the whole transfer. The percent sign is appended here rather than written in
            // the resource: Compose's formatter knows only `%N$s` / `%N$d` and prints `%%` as two
            // literal signs, which is exactly the "42%%" this row used to show.
            (if (state.progress == DOWNLOAD_PROGRESS_UNKNOWN) {
                stringResource(Res.string.about_downloading_unknown)
            } else {
                "${stringResource(Res.string.about_downloading, state.progress)}%"
            }) to scheme.primary
        is UpdateState.ReadyToInstall ->
            stringResource(Res.string.about_ready_to_install, state.version) to scheme.primary
        is UpdateState.Installing ->
            stringResource(Res.string.about_installing_version, state.version) to scheme.primary
        is UpdateState.HandedOff ->
            stringResource(Res.string.about_install_handoff, state.version) to scheme.primary
        is UpdateState.FailedWith -> state.reason to scheme.error
    }
    val openRelease = state is UpdateState.Available
    val busy = state == UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing

    // 进度条要覆盖两段：下载（有总长的百分比）和安装。安装本身 `pm install` 不报进度，所以只有
    // 把安装包推进 /data/local/tmp 那一段有数字，别的阶段用不确定进度条 + 阶段的文字。
    val barProgress: Float? = when (state) {
        is UpdateState.Downloading ->
            if (state.progress == DOWNLOAD_PROGRESS_UNKNOWN) null else state.progress / 100f
        is UpdateState.Installing ->
            state.percent.takeIf { it != INSTALL_PROGRESS_UNKNOWN }?.div(100f)
        else -> null
    }
    val showsBar = state is UpdateState.Downloading || state is UpdateState.Installing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            // A failure is the one state where a second tap is the obvious next move, so the row
            // keeps working as the retry button instead of only reporting.
            .clickable(enabled = !busy) { if (openRelease) onOpenRelease() else onCheck() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.about_check_update),
                    style = FlText.subheadline.copy(fontWeight = FontWeight.Medium),
                    color = scheme.onSurface,
                )
                // The guide, not the news: it says "there is one, tap here", and the line under it
                // is what names the version once the tap has asked.
                if (fresh) {
                    Spacer(Modifier.width(6.dp))
                    UnreadDot()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The spinner belongs to the one state with nothing to measure. A download and an
                // install draw the bar below instead of a second, smaller "something is happening".
                if (state == UpdateState.Checking) {
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
            if (showsBar) {
                Spacer(Modifier.height(8.dp))
                val barModifier = Modifier.fillMaxWidth().height(3.dp)
                if (barProgress != null) {
                    LinearProgressIndicator(
                        progress = { barProgress },
                        modifier = barModifier,
                        color = scheme.primary,
                        trackColor = scheme.primary.copy(alpha = 0.18f),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = barModifier,
                        color = scheme.primary,
                        trackColor = scheme.primary.copy(alpha = 0.18f),
                    )
                }
            }
            // Which route the install is on: "正在安装" alone cannot tell a slow push from a dead
            // Shizuku, and that difference is the whole question when an update will not land.
            if (state is UpdateState.Installing) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = installStageLabel(state.stage, state.percent),
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
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

/**
 * The step the privileged installer is on, with the push's own percentage when that step has one.
 *
 * The sign is appended here, not stored in the resource: see [UpdateState.Downloading]'s note about
 * `%%` — the same formatter handles both lines.
 */
@Composable
private fun installStageLabel(stage: InstallStage, percent: Int): String {
    val label = stringResource(
        when (stage) {
            InstallStage.PREPARING -> Res.string.about_install_stage_preparing
            InstallStage.STAGING -> Res.string.about_install_stage_staging
            InstallStage.PUSHING -> Res.string.about_install_stage_pushing
            InstallStage.INSTALLING -> Res.string.about_install_stage_installing
            InstallStage.ROOT -> Res.string.about_install_stage_root
        },
    )
    return if (percent == INSTALL_PROGRESS_UNKNOWN) label else "$label $percent%"
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
            .padding(horizontal = AppCardInset)
            .clip(RoundedCornerShape(AppCardCorner))
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
