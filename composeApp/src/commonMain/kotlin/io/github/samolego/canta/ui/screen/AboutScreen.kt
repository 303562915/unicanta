package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.Image
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.APP_NAME
import io.github.samolego.canta.BuildInfo
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.cache.ScreenCache
import io.github.samolego.canta.data.community.SponsorEntry
import io.github.samolego.canta.data.community.SponsorStore
import io.github.samolego.canta.data.community.SponsorWall
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
import io.github.samolego.canta.generated.resources.about_installing_progress
import io.github.samolego.canta.generated.resources.about_installing_version
import io.github.samolego.canta.generated.resources.about_checking
import io.github.samolego.canta.generated.resources.about_contributors
import io.github.samolego.canta.generated.resources.about_coolapk
import io.github.samolego.canta.generated.resources.about_coolapk_hint
import io.github.samolego.canta.generated.resources.about_latest_download
import io.github.samolego.canta.generated.resources.about_telemetry_guide
import io.github.samolego.canta.generated.resources.about_telemetry_guide_hint
import io.github.samolego.canta.generated.resources.about_qq_group
import io.github.samolego.canta.generated.resources.about_qq_group_copied
import io.github.samolego.canta.generated.resources.about_qq_group_hint
import io.github.samolego.canta.generated.resources.about_sponsors_add
import io.github.samolego.canta.generated.resources.about_sponsors_close
import io.github.samolego.canta.generated.resources.about_sponsors_collapse
import io.github.samolego.canta.generated.resources.about_sponsors_code_desc
import io.github.samolego.canta.generated.resources.about_sponsors_copy_group
import io.github.samolego.canta.generated.resources.about_sponsors_empty
import io.github.samolego.canta.generated.resources.about_sponsors_expand
import io.github.samolego.canta.generated.resources.about_sponsors_expand_open
import io.github.samolego.canta.generated.resources.about_sponsors_howto_money
import io.github.samolego.canta.generated.resources.about_sponsors_howto_note
import io.github.samolego.canta.generated.resources.about_sponsors_howto_token
import io.github.samolego.canta.generated.resources.about_sponsors_loading
import io.github.samolego.canta.generated.resources.about_sponsors_total
import io.github.samolego.canta.generated.resources.sponsor_miniapp_code
import io.github.samolego.canta.generated.resources.about_tagline
import io.github.samolego.canta.generated.resources.about_title
import io.github.samolego.canta.generated.resources.about_update_available
import io.github.samolego.canta.generated.resources.about_update_history
import io.github.samolego.canta.generated.resources.about_update_history_collapse
import io.github.samolego.canta.generated.resources.about_update_history_expand
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
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.PersonAvatar
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsChevron
import io.github.samolego.canta.ui.component.UnreadDot
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlProgressBar
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.update.UpdateCenter
import io.github.samolego.canta.ui.update.downloadSourceLabel
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// 「贡献者」原来是一个跳 `github.com/303562915/unicanta/graphs/contributors` 的外链。
// 现在那一行画的是赞助榜（见 [SponsorWallCard]），两样（这个地址与它的 `REPO_URL` 前缀）都不再有
// 人用，常量一起去了。
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

/** 赞助榜一格里的头像直径。 */
private val SponsorAvatar = 34.dp

/**
 * 赞助榜在 [ScreenCache] 里的键。
 *
 * 榜是公开的、与"谁在看"无关，所以**不分人一格**（商城页的余额要分人，这里不用）。
 *
 * 后面几个后缀是几次顺序切换留下的（`money-first`：资金赞助排在 token 赞助前面；
 * `tokencount`：补录的归属互换；`decimal`：金额支持小数，旧缓存里没有 `amount_exact`，
 * 不换键会一直画成 `¥8`）。现在 [mergeSponsors] 以服务端顺序为准，顺序本身就跟着刷新走，
 * 所以键名不用再跟着改——后缀留着只是因为改回去等于让老用户回到更旧的一格，没有好处。
 */
private const val SPONSOR_WALL_CACHE = "screen:sponsors:money-first:tokencount:decimal"

/**
 * 把这次拉到的榜并到已经画出来的那份上：**顺序以服务端这次返回的为准，老缓存只用来补人**。
 *
 * 以前是"老的一个不动、新来的人接在后面"，理由是整块替换会每刷新一次重排一遍，看起来像"人变了"。
 * 但榜单的排序本身就是后端说了算（资金赞助在前、金额/token 倒序），只要有人补录或金额变了，
 * "老位置不动"就会把错误顺序一直画下去：新上榜的人永远排在老用户看到的最后一格，哪怕他是第一名。
 * 现在改成 [fresh] 的顺序直接生效，只把这次没返回、但已经画过的人接在末尾（网络抖一下不丢人）。
 *
 * 已经被撤掉（`hidden`）的人这一趟不会被移除：榜很少撤人，而"用缓存兜底"是这一块的主要诉求；
 * 真要清掉某人，把这一格的缓存键改个名字即可（见 [SPONSOR_WALL_CACHE]）。
 */
private fun mergeSponsors(old: List<SponsorEntry>, fresh: List<SponsorEntry>): List<SponsorEntry> {
    if (fresh.isEmpty()) return old
    if (old.isEmpty()) return fresh
    val freshIds = fresh.mapTo(mutableSetOf()) { it.id }
    return fresh + old.filter { it.id !in freshIds }
}

/** 「我也赞助」小窗里那张收款码的边长：够扫，又不至于把整屏占满。 */
private val CodeSize = 220.dp

/**
 * 赞助榜一行排几个人。
 *
 * 4 个：头像 34 + 名字一行，四列在手机上每个格子约 70dp 宽，名字缩到 6 个字以内还能看清；
 * 再多就挤成看不清的小方块，再少就变成一行一个名字那种"列表"，不是这一栏要的样子。
 */
private const val SponsorsPerRow = 4

/**
 * The about page, laid out the way FlClash lays out its own: the app on top, then the update row
 * and the two switches that decide how an update lands, then where the app lives.
 *
 * 卡片几何与两张通用卡片在 AppCards.kt 里，设置页用同一份；这里只留版本、更新与"关于作者"。
 */
/**
 * 关于页：版本、更新卡片、作者与出处。
 *
 * `internal` 而不是 public：它收一个 [UpdateCenter]，而那个类是 internal（状态与返回值都是
 * 模块内的类型），public 函数把 internal 类型写进签名，编译器会直接拒绝。
 */
@Composable
internal fun AboutScreen(
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
     * 更新链路的唯一实现（检查 / 下载 / 安装 / 留痕）与它那份状态。
     *
     * 这一页不再自己发请求：卡片画的是 [UpdateCenter.state]，所以启动时那条自动下载进来，
     * 这一页照样有进度条在动，而不是"只有手点才有动画"。
     */
    updateCenter: UpdateCenter,
    /**
     * 赞助榜（贡献值那一栏）的数据层；null 时那张卡片只说一句为什么看不见，不装作有数据。
     *
     * 带默认值是为了不逼所有调用点都改：这一页只读它，不承担"连不上就报错"的责任。
     */
    sponsors: SponsorStore? = null,
    /**
     * 点赞助榜上那一格：进那个人的主页（与互动页、群成员网格同一条路由）。
     *
     * 这一页**只读榜单**：记账（谁赞助了多少）由维护者在后台补录，客户端没有入口，
     * 所以这里不再需要"提交者叫什么名字"。
     */
    onOpenProfile: (String) -> Unit = {},
    /**
     * 进「遥测组件卸载指南」：各厂商运营商/行为采集组件的清单、它们要的权限，以及
     * Shizuku / Dhizuku / Root 三条通道下的卸载结论。
     *
     * 默认空实现：关于页只负责画那一行，路由由 `CantaApp` 给。
     */
    onOpenTelemetryGuide: () -> Unit = {},
) {
    val autoInstallUpdates by settingsViewModel.autoInstallUpdates.collectAsStateWithLifecycle()
    val confirmBeforeInstall by settingsViewModel.confirmBeforeInstall.collectAsStateWithLifecycle()
    val updateChannel by settingsViewModel.updateChannel.collectAsStateWithLifecycle()
    /** The build whose automatic install already failed; the page then leaves it to a manual tap. */
    val skippedAutoInstall by settingsViewModel.skippedAutoInstallVersion.collectAsStateWithLifecycle()
    // 「作者的群」那一行的红点：点过一次群就永久熄灭。
    val qqGroupSeen by settingsViewModel.qqGroupSeen.collectAsStateWithLifecycle()
    // 页面最下面那张「最近更新」展开没展开；记在设置里，重进这一页还是上次选的样子。
    val historyExpanded by settingsViewModel.updateHistoryExpanded.collectAsStateWithLifecycle()
    // 「赞助者（贡献值）」那一块展开没展开；和「最近更新」同一个做法，记在设置里。
    val sponsorsExpanded by settingsViewModel.sponsorsExpanded.collectAsStateWithLifecycle()

    // 更新只有一份实现（UpdateCenter）：启动时的自动下载和这里的手点，走的是同一条路、同一个状态，
    // 所以自动下载进来时这张卡片照样有进度条在动，不再只有手点才看得见。
    val updateState = updateCenter.state
    // 换了更新通道，卡片上那个版本属于另一个通道：先清掉，别把旧通道的版本装到新通道头上。
    // 「最近更新」也跟着换：开发版与稳定版的发布说明是两份。
    //
    // **只有真的换了通道才清**：`onChannelChanged` 会取消正在跑的下载并清空状态，而这一页每次进来
    // 都是一次新的组合；以前它无条件跑，于是"自动下载正在进行时点进关于页"会把那趟下载掐掉，
    // 看到的就是"没在更新"。现在用记住的上一个值比对，第一次进来不算换。
    var channelSeen by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(updateChannel) {
        val channel = updateChannel ?: return@LaunchedEffect
        if (channelSeen == null) {
            channelSeen = channel
        } else if (channelSeen != channel) {
            channelSeen = channel
            updateCenter.onChannelChanged()
        }
        updateCenter.loadHistory(force = true)
        // 进这一页就自己查一次（一分钟内查过就不重复）：查到新版本会**直接开始下载**，
        // 所以卡片上立刻就是进度条，不用先点一下「检查更新」。
        updateCenter.checkOnEnter()
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
            AppHero()
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
                        // 手点「重新尝试」= 用户的明确意图，两个闸门都要放开：skip 标记，以及
                        // 那个跨进程累计的失败次数（只清前一个的话，check() 还会因为次数用尽而
                        // 拒绝自动下载，点了半天还是"可以下载"）。
                        settingsViewModel.saveSkippedAutoInstallVersion("")
                        settingsViewModel.clearInstallFailureCount(skippedAutoInstall)
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
            // 「贡献者」那一行原来是 GitHub contributors 的外链。现在它是赞助榜：点一下就地展开，
            // 和下面「最近更新」同一个做法——人点它是想看看"都有谁在支持"，而不是跳到另一个网站。
            SponsorWallCard(
                store = sponsors,
                platform = platform,
                expanded = sponsorsExpanded,
                onToggle = { settingsViewModel.saveSponsorsExpanded(!sponsorsExpanded) },
                onOpenProfile = onOpenProfile,
            )
            AppLinkCard(
                glyph = FlGlyphs.cloudDownload,
                // 这一栏原来指向 GitHub 仓库。人点它是为了「装最新版」，而仓库首页到安装包还要再跳两次，
                // 下载页才是那条捷径：版本号、APK、校验和都在同一屏。
                title = stringResource(Res.string.about_latest_download),
                subtitle = DOWNLOAD_PAGE_URL,
                onClick = { platform.openUrl(DOWNLOAD_PAGE_URL) },
            )
            // 遥测组件指南：群里反复问的「这个包能不能删」「为什么删了报失败」在这里一次说清，
            // 按厂商分段，每一行给出 Shizuku / Dhizuku / Root 三条通道的结论。
            AppLinkCard(
                glyph = FlGlyphs.warning,
                title = stringResource(Res.string.about_telemetry_guide),
                subtitle = stringResource(Res.string.about_telemetry_guide_hint),
                onClick = onOpenTelemetryGuide,
            )
            // 「最近更新」放最下面，而且默认折叠：它答的是「最近几版改了什么」，是翻到底才要找的东西，
            // 不该压在版本号和更新卡片上面。展开与否记在设置里，下次进来还是这个样子。
            if (updateCenter.history.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                UpdateHistoryCard(
                    entries = updateCenter.history,
                    expanded = historyExpanded,
                    onToggle = { settingsViewModel.saveUpdateHistoryExpanded(!historyExpanded) },
                )
            }
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
        is UpdateState.Downloading -> {
            // No announced size: show that bytes are moving instead of a number that would sit at
            // 0% for the whole transfer. The percent sign is appended here rather than written in
            // the resource: Compose's formatter knows only `%N$s` / `%N$d` and prints `%%` as two
            // literal signs, which is exactly the "42%%" this row used to show.
            val progressText = if (state.progress == DOWNLOAD_PROGRESS_UNKNOWN) {
                stringResource(Res.string.about_downloading_unknown)
            } else {
                "${stringResource(Res.string.about_downloading, state.progress)}%"
            }
            // 前面先标线路：图床那份写「国内镜像」，兜底走 GitHub 就写 GitHub；后面跟实测速度。
            val speed = formatDownloadSpeed(state.bytesPerSecond)
            val speedText = if (speed.isEmpty()) "" else " · $speed"
            "${downloadSourceLabel(state.source)} · $progressText$speedText" to scheme.primary
        }
        is UpdateState.ReadyToInstall ->
            stringResource(Res.string.about_ready_to_install, state.version) to scheme.primary
        is UpdateState.Installing -> {
            // 安装也带数字：`pm install` 不报进度，所以数字是按阶段折算的（见 shownPercent），
            // 但读的人要的是"到哪了"，不是"在装"。百分号在代码里拼，理由同上面那条注释。
            val percent = state.shownPercent()
            if (percent == null) {
                stringResource(Res.string.about_installing_version, state.version) to scheme.primary
            } else {
                "${stringResource(Res.string.about_installing_progress, state.version, percent)}%" to scheme.primary
            }
        }
        is UpdateState.HandedOff ->
            stringResource(Res.string.about_install_handoff, state.version) to scheme.primary
        is UpdateState.FailedWith -> state.reason to scheme.error
    }
    val openRelease = state is UpdateState.Available
    val busy = state == UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing

    // 进度条要覆盖两段：下载（有总长的百分比）和安装。安装本身 `pm install` 不报进度，所以数字
    // 按阶段折算（[shownPercent]）：条和数字一起往前走，而不是整段安装停在那儿。
    val barProgress: Float? = when (state) {
        is UpdateState.Downloading ->
            if (state.progress == DOWNLOAD_PROGRESS_UNKNOWN) null else state.progress / 100f
        is UpdateState.Installing ->
            state.shownPercent()?.div(100f)
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

/**
 * 最近几个版本改了什么：一版一个小标题，下面几行要点。默认折叠，点标题那一行展开。
 *
 * 数据是 CI 发版时写进下载页 `latest.json` / `development.json` 的那份 `history`，所以这一页不发
 * GitHub API 请求，也就不吃匿名配额——格子里的字不会因为别人用完出口 IP 的额度而消失。
 *
 * 每个版本只放几条：这是「这次更新了什么」的答案，不是变更总账。要全的，下载页上逐版本展开。
 */
@Composable
private fun UpdateHistoryCard(
    entries: List<UpdateHistoryEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.about_update_history),
                style = FlText.subheadline.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // 折叠时给出「有几个版本」，展开时那句换成「收起」：两种状态各自说清点一下会怎样。
            Text(
                text = if (expanded) {
                    stringResource(Res.string.about_update_history_collapse)
                } else {
                    stringResource(Res.string.about_update_history_expand, entries.size)
                },
                style = FlText.caption1,
                color = scheme.primary,
            )
        }
        if (expanded) {
            entries.forEach { entry ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = historyHeading(entry),
                    style = FlText.caption1,
                    color = scheme.primary,
                )
                entry.notes.forEach { note ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "· $note",
                        style = FlText.caption2,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 关于页的「赞助者（贡献值）」：一行标题，点开就地展开赞助榜。
 *
 * 为什么做成折叠而不是直接堆一屏：这一页是"关于这个软件"的一页，赞助榜是它的证据之一，
 * 不是主角。默认折叠、点开就显示，与下面那张「最近更新」一个做法——两个都是"想知道才展开"。
 *
 * 展开后：顶部一行总额与人数，下面一位一行（头像、名字、形式、金额），点一行进那个人的主页。
 * 头像按身份号从图床取（见 `PersonAvatar`），所以设过头像的人显示照片，没设过的是名字首字。
 */
@Composable
private fun SponsorWallCard(
    store: SponsorStore?,
    platform: CantaPlatform,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    // 上一屏画出来的那一份（见 [ScreenCache]）：**在 state 初始化时就摆上**，与商城页、方案详情一个做法。
    // 有缓存就直接画，不再先转一圈；网断了也还有东西看。
    val cached = remember(store) { ScreenCache.get<SponsorWall>(SPONSOR_WALL_CACHE) }
    var wall by remember(store) { mutableStateOf(cached) }
    // 有缓存就不挂 loading：那一屏刚才还在。
    var loading by remember(store) { mutableStateOf(cached == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember(store) { mutableIntStateOf(0) }
    /** 「我也赞助」那张小窗开着没有。 */
    var showHowTo by remember { mutableStateOf(false) }
    // 复制群号之后那句提示：和本文件上面 `qqGroupCopied` 同一个道理——`stringResource` 是
    // @Composable，只能在组合里读，不能在点击的 lambda 里现读（那里不是可组合上下文）。
    val groupCopied = stringResource(Res.string.about_qq_group_copied)

    // 只在展开时才去拉：折叠状态下这一页不该为一块看不见的东西多发一次请求。
    LaunchedEffect(store, expanded, reloadKey) {
        val s = store ?: return@LaunchedEffect
        if (!expanded) {
            // 收起来时把「正在读」收掉：下次展开会重新置位，否则会一直停在转圈的字上。
            loading = false
            return@LaunchedEffect
        }
        // 已经有缓存就别转圈：转一下再画出来只是让人以为页面卡了。
        if (wall == null) loading = true
        val fresh = s.wall()
        loading = false
        if (fresh != null) {
            // **新来的人接在后面，老的一个不动**：直接整块替换会让榜上的人每刷新一次就重排一遍，
            // 看着像"人变了"。所以先摆缓存里已经有的人（保持原顺序），再补这次新出现的。
            val merged = mergeSponsors(wall?.entries.orEmpty(), fresh.entries)
            val next = fresh.copy(entries = merged)
            wall = next
            error = null
            // 存回本地：下次进来先画这一份，再等网络顶上去。空榜不写——一次空响应不该把
            // 已经看到的人从本地抹掉。
            if (merged.isNotEmpty()) ScreenCache.put(SPONSOR_WALL_CACHE, next)
        } else {
            // 旧榜留着看，但这次没刷到要说得出来（下面非空分支也会把这句话画出来）。
            error = if (s.functionMissing) {
                "这一版的社区后端还没有赞助榜。"
            } else {
                s.lastError ?: "没拉到，检查网络。"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.about_contributors),
                style = FlText.subheadline.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // 折叠时写「N 位赞助者」，展开时换成「收起」：两种状态各自说清点一下会怎样。
            Text(
                text = if (expanded) {
                    stringResource(Res.string.about_sponsors_collapse)
                } else {
                    val count = wall?.count ?: 0
                    if (count > 0) {
                        stringResource(Res.string.about_sponsors_expand, count)
                    } else {
                        stringResource(Res.string.about_sponsors_expand_open)
                    }
                },
                style = FlText.caption1,
                color = scheme.primary,
            )
        }

        if (expanded) {
            // 首拉才画「正在读取」：收起来再展开时旧榜还在，转圈那行只会闪一下。
            if (loading && wall == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.about_sponsors_loading),
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                )
            }
            val entries = wall?.entries.orEmpty()
            // 旧榜还在、但这次没刷到：这一句必须画出来，否则界面看起来就是"刷新成功了"。
            val staleNote = error?.takeIf { entries.isNotEmpty() }
            if (entries.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error ?: stringResource(Res.string.about_sponsors_empty),
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                staleNote?.let { line ->
                    Text(
                        text = line,
                        style = FlText.caption2,
                        color = scheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                // 这里原来还有一行「累计 ¥N · M 位」的统计。按维护者的意思，总额不公开统计展示，
                // 所以整行去掉；人数还在上面那个「展开榜单（N 位）」的按钮文案里（[about_sponsors_expand]）。
                entries.chunked(SponsorsPerRow).forEach { row ->
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { entry ->
                            SponsorWallCell(
                                entry = entry,
                                modifier = Modifier.weight(1f),
                                onOpenProfile = onOpenProfile,
                            )
                        }
                        // 最后一行没排满：补空位，免得那几个人被拉宽成一排大格子。
                        repeat(SponsorsPerRow - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // 这一行是"怎么赞助"的入口，不是记账入口：点开是一张收款码 + 一句 token 赞助怎么来。
            TextButton(
                onClick = { showHowTo = true },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(stringResource(Res.string.about_sponsors_add))
            }
        }
    }

    if (showHowTo) {
        SponsorHowToDialog(
            onDismiss = { showHowTo = false },
            onCopyGroup = {
                // 复制群号比"打开 QQ"稳：没装 QQ 的人至少拿到了数字。
                platform.copyToClipboard(QQ_GROUP)
                platform.showMessage(groupCopied)
            },
        )
    }
}

/**
 * 「我也赞助」那张小窗：一张收款小程序码，加一句"token 赞助请联系群主"。
 *
 * 这里**只给方式，不记账**：钱和码都在群主那边收，榜由维护者补录，客户端没有提交通道——
 * 谁都能往榜上插一行"我赞助了一万"的话，这张榜就没有意义了。
 *
 * 码是从一张金底图片里裁出来的（`drawable/sponsor_miniapp_code.png`）：原图一整块版面只有中间
 * 那个码有用，四周的金色是背景，所以素材只留码本身（400×400 加 8px 白边）。
 */
@Composable
private fun SponsorHowToDialog(
    onDismiss: () -> Unit,
    onCopyGroup: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    CantaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.about_sponsors_add),
        widthFraction = WIDE_DIALOG_WIDTH,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.about_sponsors_close)) }
            TextButton(onClick = onCopyGroup) { Text(stringResource(Res.string.about_sponsors_copy_group)) }
        },
    ) {
        // 扫一下就能赞助：码本身画在白色底上，深色主题里也不会糊成一片。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.sponsor_miniapp_code),
                contentDescription = stringResource(Res.string.about_sponsors_code_desc),
                modifier = Modifier.size(CodeSize),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(Res.string.about_sponsors_howto_money),
            style = FlText.caption1,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.about_sponsors_howto_token, QQ_GROUP),
            style = FlText.caption1,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.about_sponsors_howto_note),
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * 榜上的一位：一格 = 头像、名字、赞助方式。
 *
 * 为什么是竖着排的一格而不是一行：这一栏要的是"一排人头像"的样子，一行一个名字会占掉太多高度，
 * 二十个人就是一屏。一格 4 个（见 [SponsorsPerRow]），头像在上、名字在下、赞助什么在最下面。
 *
 * 整格可点：进那个人的主页（与互动页、群成员网格同一条路由）。
 */
@Composable
private fun SponsorWallCell(
    entry: SponsorEntry,
    modifier: Modifier = Modifier,
    onOpenProfile: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shownName = when {
        entry.anonymous -> "匿名"
        entry.name.isNotBlank() -> entry.name
        else -> "#${entry.owner}"
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                enabled = entry.canOpenProfile,
                role = Role.Button,
                onClick = { if (entry.canOpenProfile) onOpenProfile(entry.owner) },
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 匿名的头像不给照片：名字都藏了，脸还挂着等于没藏。
        if (entry.anonymous) {
            Box(
                modifier = Modifier
                    .size(SponsorAvatar)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(scheme.surfaceContainerHighest.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "匿", style = FlText.caption1, color = scheme.onSurfaceVariant)
            }
        } else {
            PersonAvatar(
                name = shownName,
                owner = entry.owner,
                size = SponsorAvatar,
                background = scheme.primary.copy(alpha = 0.9f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = shownName,
            style = FlText.caption2,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            // 出钱的写「¥N」，token 赞助写出数量（「6000 token」），都没有才写「token 赞助」：
            // 这几样不是一回事，用一个数糊住会让人以为是 ¥0。
            text = entry.badge,
            style = FlText.caption2,
            color = if (entry.hasCount) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 「v4.3.5 · 11-24」；开发版把构建号写出来，否则两个滚动构建看起来是同一个版本。 */
private fun historyHeading(entry: UpdateHistoryEntry): String {
    val version = if (entry.buildNumber > 0) {
        "v${entry.version} (dev ${entry.buildNumber})"
    } else {
        "v${entry.version}"
    }
    // `published_at` 是 ISO 8601；只取 MM-dd，年份在「最近几次」里没有信息量。
    val day = if (entry.publishedAt.length >= 10) entry.publishedAt.substring(5, 10) else ""
    return if (day.isNotEmpty()) "$version · $day" else version
}
