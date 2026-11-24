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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.APP_NAME
import io.github.samolego.canta.BuildInfo
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.core.INSTALL_PROGRESS_UNKNOWN
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.about_check_update
import io.github.samolego.canta.generated.resources.about_check_update_action
import io.github.samolego.canta.generated.resources.about_check_update_hint
import io.github.samolego.canta.generated.resources.about_auto_install
import io.github.samolego.canta.generated.resources.about_auto_install_retry
import io.github.samolego.canta.generated.resources.about_auto_install_retry_hint
import io.github.samolego.canta.generated.resources.about_confirm_install
import io.github.samolego.canta.generated.resources.about_download_cancel
import io.github.samolego.canta.generated.resources.about_download_page
import io.github.samolego.canta.generated.resources.about_downloading
import io.github.samolego.canta.generated.resources.about_downloading_unknown
import io.github.samolego.canta.generated.resources.about_ready_to_install
import io.github.samolego.canta.generated.resources.about_installing_version
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
import io.github.samolego.canta.generated.resources.update_channel
import io.github.samolego.canta.generated.resources.update_channel_dev
import io.github.samolego.canta.generated.resources.update_channel_stable
import io.github.samolego.canta.ui.component.AppCardCorner
import io.github.samolego.canta.ui.component.AppCardInset
import io.github.samolego.canta.ui.component.AppLinkCard
import io.github.samolego.canta.ui.component.AppToggleCard
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsChevron
import io.github.samolego.canta.ui.component.UnreadDot
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlProgressBar
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.update.UpdateCenter
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

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
     * 更新链路的唯一实现（检查 / 下载 / 安装 / 留痕）与它那份状态。
     *
     * 这一页不再自己发请求：卡片画的是 [UpdateCenter.state]，所以启动时那条自动下载进来，
     * 这一页照样有进度条在动，而不是"只有手点才有动画"。
     */
    updateCenter: UpdateCenter,
) {
    val autoInstallUpdates by settingsViewModel.autoInstallUpdates.collectAsStateWithLifecycle()
    val confirmBeforeInstall by settingsViewModel.confirmBeforeInstall.collectAsStateWithLifecycle()
    val updateChannel by settingsViewModel.updateChannel.collectAsStateWithLifecycle()
    /** The build whose automatic install already failed; the page then leaves it to a manual tap. */
    val skippedAutoInstall by settingsViewModel.skippedAutoInstallVersion.collectAsStateWithLifecycle()
    // 「作者的群」那一行的红点：点过一次群就永久熄灭。
    val qqGroupSeen by settingsViewModel.qqGroupSeen.collectAsStateWithLifecycle()

    // 更新只有一份实现（UpdateCenter）：启动时的自动下载和这里的手点，走的是同一条路、同一个状态，
    // 所以自动下载进来时这张卡片照样有进度条在动，不再只有手点才看得见。
    val updateState = updateCenter.state
    // 换了更新通道，卡片上那个版本属于另一个通道：先清掉，别把旧通道的版本装到新通道头上。
    LaunchedEffect(updateChannel) {
        if (updateChannel != null) updateCenter.onChannelChanged()
    }
    // Read here, not in the tap handler: `getString` suspends, and a click is not a suspend call.
    val qqGroupCopied = stringResource(Res.string.about_qq_group_copied)

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
            AppHero(onVersionTap = onVersionTap)
            Spacer(Modifier.height(16.dp))

            UpdateCard(
                state = updateState,
                // Only while the row has nothing of its own to say: a found release, a download or
                // an install all speak for themselves, and a dot over them would be a second voice.
                fresh = updateAvailable && updateState == UpdateState.Idle,
                onCheck = { updateCenter.check() },
                onOpenRelease = {
                    (updateState as? UpdateState.Available)?.let { updateCenter.startDownload(it.release) }
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
                        onClick = { updateCenter.cancelDownload() },
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
                        updateCenter.check()
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
                    if (enabled && pending is UpdateState.Available) updateCenter.startDownload(pending.release)
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
                // The app-list loaders draw this same bar (FlProgressBar), so waiting for a build
                // and waiting for the list look like one thing moving rather than two.
                FlProgressBar(progress = barProgress)
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
