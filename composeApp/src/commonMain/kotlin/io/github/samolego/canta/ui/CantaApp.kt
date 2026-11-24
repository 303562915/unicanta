package io.github.samolego.canta.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.savedstate.read
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.samolego.canta.core.CantaAppDependencies
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.core.DeviceUser
import io.github.samolego.canta.core.PrivilegeChoice
import io.github.samolego.canta.core.PrivilegeKind
import io.github.samolego.canta.core.UpdateDownload
import io.github.samolego.canta.data.community.ChatLiveSource
import io.github.samolego.canta.data.community.ChatRealtimeHub
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.data.community.unreadSince
import io.github.samolego.canta.data.history.RestoreMode
import io.github.samolego.canta.data.recommend.hasNewApplied
import io.github.samolego.canta.data.recommend.newAppliedSince
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.privilege_root_refused
import io.github.samolego.canta.generated.resources.privilege_root_opened
import io.github.samolego.canta.generated.resources.badge_recommended_name
import io.github.samolego.canta.generated.resources.about_update_download_toast
import io.github.samolego.canta.generated.resources.about_auto_download_failed
import io.github.samolego.canta.generated.resources.about_auto_install_paused
import io.github.samolego.canta.generated.resources.about_download_page
import io.github.samolego.canta.generated.resources.got_it
import io.github.samolego.canta.generated.resources.about_install_done
import io.github.samolego.canta.generated.resources.about_install_failed_title
import io.github.samolego.canta.generated.resources.about_install_handoff
import io.github.samolego.canta.generated.resources.about_installing_version
import io.github.samolego.canta.generated.resources.preset_save_error
import io.github.samolego.canta.generated.resources.select_all_enabled
import io.github.samolego.canta.generated.resources.select_all_tip
import io.github.samolego.canta.generated.resources.settings
import io.github.samolego.canta.generated.resources.tab_apps
import io.github.samolego.canta.generated.resources.tab_recommend
import io.github.samolego.canta.generated.resources.tab_rules
import io.github.samolego.canta.generated.resources.tab_tweaks
import io.github.samolego.canta.generated.resources.vpn_notice_never
import io.github.samolego.canta.generated.resources.vpn_notice_open
import io.github.samolego.canta.generated.resources.vpn_notice_text
import io.github.samolego.canta.ui.AppsTab
import io.github.samolego.canta.ui.AppsType
import io.github.samolego.canta.ui.dialog.CrashReportDialog
import io.github.samolego.canta.ui.dialog.PrivilegeChooserDialog
import io.github.samolego.canta.ui.component.BottomBarItem
import io.github.samolego.canta.ui.component.CollapsingBottomBar
import io.github.samolego.canta.ui.component.FloatingPillBar
import io.github.samolego.canta.ui.component.ThemeToastHost
import io.github.samolego.canta.ui.component.dockScroll
import io.github.samolego.canta.ui.component.rememberDockVisibility
import io.github.samolego.canta.ui.fl.FlAccent
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlThemeMode
import io.github.samolego.canta.ui.lock.launchGateOf
import io.github.samolego.canta.ui.navigation.Screen
import io.github.samolego.canta.ui.screen.AboutScreen
import io.github.samolego.canta.ui.screen.AppDetailScreen
import io.github.samolego.canta.ui.screen.DescriptionFixScreen
import io.github.samolego.canta.ui.screen.LaunchLockScreen
import io.github.samolego.canta.ui.screen.LogsScreen
import io.github.samolego.canta.ui.screen.MainDialogs
import io.github.samolego.canta.ui.screen.MainScreen
import io.github.samolego.canta.ui.screen.ChatScreen
import io.github.samolego.canta.ui.screen.CreateGroupScreen
import io.github.samolego.canta.ui.screen.GroupSettingsScreen
import io.github.samolego.canta.ui.screen.FollowListScreen
import io.github.samolego.canta.ui.screen.GroupsScreen
import io.github.samolego.canta.ui.screen.InteractionsScreen
import io.github.samolego.canta.ui.screen.MessagesScreen
import io.github.samolego.canta.ui.screen.MyPlansScreen
import io.github.samolego.canta.ui.screen.UserProfileScreen
import io.github.samolego.canta.ui.screen.OnboardingScreen
import io.github.samolego.canta.ui.screen.PlanDetailScreen
import io.github.samolego.canta.ui.screen.PresetsScreen
import io.github.samolego.canta.ui.screen.RecommendationsScreen
import io.github.samolego.canta.ui.screen.UploaderProfileScreen
import io.github.samolego.canta.ui.screen.SafetyRulesScreen
import io.github.samolego.canta.ui.screen.UpdateState
import io.github.samolego.canta.ui.screen.updateLabel
import io.github.samolego.canta.ui.screen.DOWNLOAD_PAGE_URL
import io.github.samolego.canta.ui.screen.INSTALL_DONE_LINGER_MILLIS
import io.github.samolego.canta.ui.screen.downloadReleaseWithRetries
import io.github.samolego.canta.ui.screen.UpdateChannel
import io.github.samolego.canta.ui.screen.isNewerThanInstalled
import io.github.samolego.canta.ui.screen.latestReleaseFromPage
import io.github.samolego.canta.ui.screen.TweaksScreen
import io.github.samolego.canta.ui.screen.MineScreen
import io.github.samolego.canta.ui.screen.SettingsScreen
import io.github.samolego.canta.ui.screen.ThemeScreen
import io.github.samolego.canta.generated.resources.about_install_cancel
import io.github.samolego.canta.generated.resources.about_install_confirm
import io.github.samolego.canta.generated.resources.about_install_message
import io.github.samolego.canta.generated.resources.about_install_title
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.CURRENT_ONBOARDING_VERSION
import io.github.samolego.canta.ui.viewmodel.MainViewModel
import io.github.samolego.canta.ui.viewmodel.PresetsViewModel
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import io.github.samolego.canta.util.Identity
import io.github.samolego.canta.util.CrashReportStore
import io.github.samolego.canta.util.DeviceIdentity
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.SafetyRules
import io.github.samolego.canta.util.ShareCode
import io.github.samolego.canta.util.TelemetryConfig
import io.github.samolego.canta.BuildInfo
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Taps on the version in settings that unlock "select all" for recommended apps. */
private const val SECRET_TAPS = 6

/** How long a page takes to slide in from, or back out to, the right edge. */
private const val PAGE_SLIDE_MS = 300

/** Taps after which the remaining count is hinted. */
private const val SECRET_TAPS_HINT_AFTER = 3

/**
 * How often the running app asks for a release again. One poll is a single HTML read of the
 * release page and no GitHub API quota, so half a minute costs nothing; it runs only while the app
 * is on screen, because a check the person cannot see the result of is just traffic.
 */
private const val UPDATE_CHECK_INTERVAL_MILLIS = 30_000L

/**
 * How often the "no VPN" reminder is asked again while the app is on screen. The community
 * deployment sits in Japan, so a phone without a proxy reads as "everything is offline"; a minute
 * is short enough to catch a proxy that was switched off mid-session or a client that was opened
 * but never connected, and long enough not to sit in the way of reading.
 */
private const val VPN_NOTICE_INTERVAL_MILLIS = 60_000L

/** How long that reminder stays on screen before it takes itself away; it returns a minute later. */
private const val VPN_NOTICE_VISIBLE_MILLIS = 12_000L

/**
 * App 退到后台之后多久问一次「有没有新消息」，用来决定要不要弹通知栏。
 *
 * 一分钟和上面那个未读计数同一个节奏：更勤只是白花电和流量，更慢则人已经走了才提醒。
 * 它只在 `appVisible` 为 false 时跑，回前台第一个 effect 就把循环取消掉。
 */
private const val CHAT_NOTICE_POLL_MILLIS = 60_000L

/**
 * Where the manager behind Stellar/Shizuku comes from. The app points at the fork it recommends,
 * and this is also the fallback for "open the manager": shared code has no way to launch another
 * app by package name yet, so the page it is installed from is the closest thing available.
 */
private const val STELLAR_URL = "https://github.com/roro2239/Stellar/releases"

/** The manager app that holds the Shizuku service; Stellar is the fork this build recommends. */
private const val STELLAR_PACKAGE = "roro.stellar.manager"

/** The upstream manager, for installs that already run plain Shizuku. */
private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

/**
 * The manager that shares the DeviceOwner role instead of a shell. It is a third route, not a
 * replacement: it gives the freeze half of the app, and its own app is where both the activation
 * and the per-app grant live.
 */
private const val DHIZUKU_PACKAGE = "com.rosan.dhizuku"

/** Where Dhizuku is published; it has no Play Store listing, so this is the download. */
private const val DHIZUKU_URL = "https://github.com/iamr0s/Dhizuku/releases"

/**
 * The root managers whose own screen has to be visited to change an answer. Kept next to the
 * names detection uses in `PrivilegeChain`: a grant lives in that app, not in an API Canta can
 * call, so the best this side can do is open it.
 */
private val ROOT_MANAGER_PACKAGES = listOf(
    "com.topjohnwu.magisk",
    "io.github.vvb2060.magisk",
    "io.github.huskydg.magisk",
    "me.weishu.kernelsu",
    "com.rifsxd.ksunext",
    "me.bmax.apatch",
)

/** The app's root composable, shared by the Android, desktop and web entry points. */
@Composable
fun CantaApp(deps: CantaAppDependencies, closeApp: () -> Unit, crashReports: CrashReportStore? = null) {
    // One client for the community pages, and the settings view model that owns how the app
    // looks: both are made here rather than inside the navigation, because the theme is applied
    // above it and has to read the stored accent and dark mode before anything is drawn.
    //
    // 两个仓储都带上身份凭据。写函数的身份一直是客户端自报的（`p_owner => '10003'`），知道别人的
    // 号码就能替别人发消息、投票；带上 token 之后服务端以 token 认出来的号码为准，自报的只在没带
    // token 时才采信。**顺序不能反**：这一条依赖服务端已经推过 20261123110000 那个迁移，先发客户端
    // 会在旧库上报 "function ... does not exist"。
    val communityRepository = remember {
        PlanRepository(HttpClient(), identityToken = { DeviceIdentity.ensure(deps.settings) })
    }
    // Direct messages, groups and follows: their own calls, against the same backend.
    val socialRepository = remember {
        SocialRepository(HttpClient(), identityToken = { DeviceIdentity.ensure(deps.settings) })
    }
    // 实时聊天：服务端在 messages 上挂的 INSERT 触发器往 `chat:<会话号>` 广播一声，这里订阅它。
    // 网页版没有 websocket 引擎，构造得出来但连不上，聊天页会退回轮询（见 ChatScreen.liveSource）。
    val chatLive: ChatLiveSource? = remember {
        runCatching {
            ChatRealtimeHub(
                restUrl = TelemetryConfig.BASE_URL,
                apiKey = TelemetryConfig.PUBLISHABLE_KEY,
            )
        }.getOrNull()
    }
    val settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            deps.settings,
            deps.packageTelemetry,
            communityRepository,
            deps.platform.deviceKey(),
            { deps.handler.deviceProfile() },
            { deps.handler.privilegeKind.wire },
        )
    }
    val themeAccent by settingsViewModel.themeAccent.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()

    // The start-up lock: both halves are null until the store has been read, so an install with a
    // lock never shows the app list for a frame. Once read, a usable pair keeps the question over
    // everything until its date is typed — and again every time the app is left, which is what
    // "打开软件要先回答问题" means to the person who set it.
    val lockQuestion by settingsViewModel.launchQuestion.collectAsStateWithLifecycle()
    val lockAnswer by settingsViewModel.launchAnswer.collectAsStateWithLifecycle()
    val unlocked by settingsViewModel.sessionUnlocked.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { settingsViewModel.markSessionLocked() }

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (FlThemeMode.ofStored(themeMode)) {
        FlThemeMode.Light -> false
        FlThemeMode.Dark -> true
        FlThemeMode.System -> systemDark
    }
    CantaTheme(darkTheme = darkTheme, accent = FlAccent.ofArgb(themeAccent)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Plain locals, so the two are smart-cast together: a delegated property is not.
            val question = lockQuestion
            val answer = lockAnswer
            Box(modifier = Modifier.fillMaxSize()) {
                if (question != null && answer != null) {
                    // A pair that is not a question this build knows plus a real date — a file
                    // edited by hand, a retired format — is ignored instead of becoming a question
                    // nobody could answer: the one mistake this lock must never make is locking
                    // the owner out for good.
                    val gate = launchGateOf(question, answer)
                    // Composed only once the store has answered: before that there is nothing
                    // worth drawing, and the alternative is a flash of the app list under the
                    // question.
                    CantaNavigation(deps, closeApp, crashReports, communityRepository, socialRepository, settingsViewModel, chatLive)
                    // The lock is a cover rather than a replacement. Drawn over the pages, it
                    // keeps their state and their side effects — the telemetry flush on the way
                    // out, the one look at the update feed — alive; swapping the navigation out and
                    // back would throw the back stack away and cancel the work that was running.
                    if (gate != null && !unlocked) {
                        LaunchLockScreen(gate = gate, onUnlock = { settingsViewModel.markSessionUnlocked() })
                    }
                }
            }
        }
    }
}

@Composable
private fun CantaNavigation(
    deps: CantaAppDependencies,
    closeApp: () -> Unit,
    crashReports: CrashReportStore?,
    communityRepository: PlanRepository,
    socialRepository: SocialRepository,
    settingsViewModel: SettingsViewModel,
    /** 实时聊天的那条订阅；null 表示这一侧没有（网页版），聊天页会退回轮询。 */
    chatLive: ChatLiveSource?,
) {
    val handler = deps.handler
    val platform = deps.platform

    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    val appListViewModel: AppListViewModel = viewModel {
        AppListViewModel(handler, deps.bloatRepository, deps.packageTelemetry, deps.history, deps.settings)
    }
    val presetViewModel: PresetsViewModel = viewModel { PresetsViewModel(deps.presetStore, handler, platform) }
    val mainViewModel: MainViewModel = viewModel { MainViewModel(handler, platform, deps.settings, appListViewModel) }

    // Hoisted so preset-applying (below) knows which tab is showing.
    val pagerState = rememberPagerState(pageCount = { AppsTab.entries.size })
    // The records tab shows no app list, so a preset applied from there falls back to installed.
    val selectedAppsType = AppsTab.entries[pagerState.currentPage].appsType ?: AppsType.INSTALLED

    var versionTapCounter by remember { mutableIntStateOf(0) }

    // Send queued analytics when the app goes to the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        coroutineScope.launch { deps.telemetry.flush() }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(platform) {
        platform.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // A release found at start-up is downloaded without asking; the install runs as soon as the
    // download is done, unless the about page's switch asks for a confirmation first (it is off
    // by default).
    val autoInstallUpdates by settingsViewModel.autoInstallUpdates.collectAsStateWithLifecycle()
    val updateChannel by settingsViewModel.updateChannel.collectAsStateWithLifecycle()
    val skippedAutoInstall by settingsViewModel.skippedAutoInstallVersion.collectAsStateWithLifecycle()
    var autoInstallAsked by remember { mutableStateOf<UpdateState.ReadyToInstall?>(null) }
    val updateClient = remember { HttpClient() }

    // One install at a time. The start-up path, the confirmation dialog and the about page can
    // all reach the same APK, and two silent installs of one package make one of them fail for a
    // reason the reader cannot see or act on — which then looks exactly like "一直在安装".
    var installingUpdate by remember { mutableStateOf(false) }
    /**
     * The per-route report of a refused install, shown as a dialog rather than a one-line toast:
     * "装不上" is not actionable, "Shizuku 读不到这个目录 / root 没授权" is, and the reader needs to
     * see every route that was tried before being sent to the download page.
     */
    var installFailure by remember { mutableStateOf<String?>(null) }

    // One install path for the automatic case and for the dialog: say that it is running, say how
    // it ended, and only then let the process go. Every stage is spoken out loud on purpose — the
    // report behind this change was "一直弹安装中，不知道到底装上了没有": the old path announced the
    // start and then either vanished (success) or printed one opaque `pm` line (failure).
    val installRelease: (UpdateState.ReadyToInstall) -> Unit = { ready ->
        if (!installingUpdate) {
            installingUpdate = true
            coroutineScope.launch {
                platform.showMessage(getString(Res.string.about_installing_version, ready.version))
                val failure = platform.installUpdate(ready.path)
                if (failure == null) {
                    platform.showMessage(getString(Res.string.about_install_done, ready.version))
                    // Let the line be read before the APK's process goes away; closing at once is
                    // indistinguishable from a crash, which is what people kept reporting.
                    delay(INSTALL_DONE_LINGER_MILLIS)
                    platform.exitApp()
                } else {
                    // This build will not install itself on this phone: a foreign signature, a ROM
                    // that refuses `pm install`, a runtime that stopped. Remembering the version is
                    // what stops the next launch from downloading it again and showing the same
                    // endless "正在安装" — the About page keeps the manual retry.
                    settingsViewModel.saveSkippedAutoInstallVersion(ready.version)
                    val handedOff = platform.requestInstall(ready.path)
                    if (handedOff) {
                        platform.showMessage(getString(Res.string.about_install_handoff, ready.version))
                    } else {
                        // No privileged route worked and the system installer would not open: the
                        // dialog names each route's own refusal and offers the download page as
                        // the last way out.
                        installFailure = failure
                    }
                    installingUpdate = false
                }
            }
        }
    }

    // The poll below only asks while the app is visible: in the background nobody is reading the
    // answer, and a check every half minute is a request the person never asked for.
    var appVisible by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { appVisible = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { appVisible = false }

    // ---------------------------------------------------------------- 新消息的通知栏提醒
    //
    // 这一段的边界要先说清：它只在**进程还活着**的时候有用。消息是下面那个后台轮询问出来的，
    // 轮询是协程，协程活在进程里；App 被系统回收之后没有任何东西在跑，也就没有人在那一刻调用
    // 通知栏。真正的后台推送要 FCM 或者一条常驻长连接，这一轮不做，所以这里也不假装做了——
    // 被杀掉就是没有通知，直到人再次打开 App 为止。
    //
    // 为什么不复用上面那个 60 秒的未读计数：那一个只在四个带数字的页面上跑，而且它手里只有
    // 一个「一共几条」的总数，说不出「哪个会话来了几条」。人从聊天页退到后台的时候，正好是
    // 那一处不跑、而这条通知最该来的时候。
    val muteChatNotices by settingsViewModel.muteChatNotices.collectAsStateWithLifecycle()

    // 点通知进来的：把会话号交给导航。冷启动那一条由入口先存着（进程刚起来时还没人在听），
    // 这里第一次收集时补发，之后才是后续的推送。
    LaunchedEffect(navController) {
        platform.chatIntents.collect { conversation ->
            navController.navigate(Screen.Chat.routeFor(conversation)) { launchSingleTop = true }
        }
    }

    // 点群邀请链接（`unicanta://group/<code>`）进来的：入群，然后直接进那个群。
    //
    // 入群要身份（后端既认 token、也认自报的号码），而冷启动时本地设置还没读出来，第一帧的 owner
    // 是空串。这条链接只来一次事件，不能靠「等 owner 变了再重试」——那次事件已经消费掉了。
    // 所以在这里原地等一小会儿（最多三秒）：等到了再发，等不到就直说，别拿空身份去问后端。
    LaunchedEffect(navController) {
        platform.groupInviteCodes.collect { code ->
            var owner = ""
            var waited = 0
            while (owner.isEmpty() && waited < 20) {
                val identity = DeviceIdentity.current(deps.settings)
                owner = identity.owner.ifEmpty { identity.token }
                if (owner.isEmpty()) {
                    delay(150)
                    waited += 1
                }
            }
            if (owner.isEmpty()) {
                platform.showMessage("还没认出身份，邀请链接没进去：等 App 加载完再点一次")
                return@collect
            }
            // 后端幂等：本来就在群里也返回同一个会话号，所以点两次不会变成"进不去"。
            val joined = runCatching { socialRepository.joinByInvite(owner, code) }.getOrNull()
            if (joined != null) {
                navController.navigate(Screen.Chat.routeFor(joined)) { launchSingleTop = true }
            } else {
                platform.showMessage("邀请链接没进成：链接可能已经失效，或者没连上后端")
            }
        }
    }

    // 回到前台就把通知栏上的聊天提醒收掉：人已经在看着 app 了，那几条没有意义。
    LaunchedEffect(appVisible) {
        if (appVisible) platform.clearChatNotices()
    }

    LaunchedEffect(appVisible, muteChatNotices) {
        // 只在后台、且开关是开的时候跑。开关默认就是开的（proto 存的是"静音"）。
        if (appVisible || muteChatNotices) return@LaunchedEffect
        // 弹不出来的设备就别轮询了。权限要人自己去设置里开，而绝大多数人不会去开——
        // 对他们来说这个循环一分钟一次请求，只换来一个被系统丢掉的 notify，纯属白耗电和流量。
        // 权限是在设置页给的，那一刻 app 可见，回前台会让本 effect 以新的 key 重启，所以
        // 这里只看一次不会漏掉"中途给了权限"的情况。
        if (!platform.canNotify()) return@LaunchedEffect
        // 基线：上一次看到的「每个会话几条未读」。第一圈只记不弹——否则一退到后台就会把此前
        // 积攒的未读一口气补成一屏通知，那不是提醒，是把历史重播一遍。
        var baseline: Map<Long, Int>? = null
        while (true) {
            val identity = DeviceIdentity.current(deps.settings)
            val owner = identity.owner.ifEmpty { identity.token }
            if (owner.isNotEmpty()) {
                val conversations = runCatching { socialRepository.conversationsFor(owner) }.getOrNull()
                if (conversations != null) {
                    baseline?.let { before ->
                        for (conversation in conversations) {
                            // 只有数字往上走才算「来了新的」。同一个数字反复看到不弹，这就是
                            // 同一条消息不会连弹三次的第一层；第二层在平台那一侧——通知 id 用
                            // 会话号，同一会话的提醒互相覆盖而不是堆成一列。
                            if (conversation.unread > (before[conversation.id] ?: 0)) {
                                platform.notifyChat(
                                    conversationId = conversation.id,
                                    title = conversation.title
                                        .ifBlank { conversation.peerName }
                                        .ifBlank { "新消息" },
                                    body = noticeBody(conversation.lastBody),
                                    unread = conversation.unread,
                                )
                            }
                        }
                    }
                    baseline = conversations.associate { it.id to it.unread }
                }
            }
            delay(CHAT_NOTICE_POLL_MILLIS)
        }
    }

    LaunchedEffect(autoInstallUpdates, updateChannel, skippedAutoInstall) {
        autoInstallAsked = null
        if (!autoInstallUpdates || updateChannel == null) return@LaunchedEffect
        // F-Droid signs its own builds; downloading this repo's APK over one of them cannot work.
        if (platform.isInstalledFromFdroid()) return@LaunchedEffect
        val channel = if (updateChannel == "dev") UpdateChannel.DEVELOPMENT else UpdateChannel.STABLE

        // One look at start-up misses every release published while the app stays open, so the
        // check repeats for the rest of the session — a person who keeps the app running in the
        // background is exactly the one who wants the new build without a restart. The poll reads
        // the release page, not the API: half a minute cannot spend the anonymous quota the about
        // page needs for a clean answer. A failing poll says nothing, because the next one is the
        // retry; a poll that found a build stops the loop, since asking again cannot make the APK
        // that is already here fresher.
        while (true) {
            if (appVisible) {
                val release = try {
                    latestReleaseFromPage(updateClient, channel)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The about page is where a failure is named and what to do about it; a poll
                    // that failed in the background has no one to tell and nothing to say.
                    null
                }
                if (release != null && release.isNewerThanInstalled()) {
                    val label = updateLabel(release)
                    // This exact build already failed to install on its own. Another download and
                    // another "正在安装" would be the loop this mark exists to break: say it once,
                    // point at the page that can still do it by hand, and stop asking.
                    if (label == skippedAutoInstall) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                getString(Res.string.about_auto_install_paused, label),
                            )
                        }
                        return@LaunchedEffect
                    }
                    // The download runs behind whatever page is open, so a notice in the theme
                    // colour is the only signal that something new is on its way. It runs
                    // alongside: the download starts at once.
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            getString(Res.string.about_update_download_toast, label),
                        )
                    }
                    // No progress is shown from here: a toast cannot carry a percentage, and the
                    // About page is where the download renders its own bar. What matters at this
                    // level is that every ending is spoken out loud.
                    val result = downloadReleaseWithRetries(
                        platform = platform,
                        tag = release.tag,
                        buildNumber = release.buildNumber,
                        onProgress = { _ -> },
                    )
                    when (result) {
                        is UpdateDownload.Done -> {
                            val ready = UpdateState.ReadyToInstall(label, result.path)
                            // Asking first is a switch, off by default: the download only ran
                            // because automatic updates are on, so the install is the thing that
                            // was asked for. Turned on, the dialog below is the second tap.
                            if (settingsViewModel.confirmBeforeInstall.value) {
                                autoInstallAsked = ready
                            } else {
                                installRelease(ready)
                            }
                        }
                        // Said instead of dropped in silence: a download that ends with no word at
                        // all is what made the previous "downloading" toast read as a lie. The
                        // download page is the action, because a download problem cannot be fixed
                        // by the installer. The failure is not remembered, so the next start tries
                        // again.
                        is UpdateDownload.Failed -> coroutineScope.launch {
                            val answer = snackbarHostState.showSnackbar(
                                message = getString(Res.string.about_auto_download_failed),
                                actionLabel = getString(Res.string.about_download_page),
                            )
                            if (answer == SnackbarResult.ActionPerformed) {
                                platform.openUrl(DOWNLOAD_PAGE_URL)
                            }
                        }
                    }
                    return@LaunchedEffect
                }
            }
            delay(UPDATE_CHECK_INTERVAL_MILLIS)
        }
    }

    // The two optional pages: off by default, so the bar only leads to what everyone needs.
    val showTweaksTab by settingsViewModel.showTweaksTab.collectAsStateWithLifecycle()
    val showRecommendTab by settingsViewModel.showRecommendTab.collectAsStateWithLifecycle()

    // Null until the stored settings have loaded, so the wrong screen never flashes.
    val onboardingVersion by settingsViewModel.onboardingVersion.collectAsStateWithLifecycle()
    val storedOnboardingVersion = onboardingVersion ?: return

    // Fixed at first composition: finishing onboarding navigates explicitly.
    val startDestination = remember {
        if (storedOnboardingVersion >= CURRENT_ONBOARDING_VERSION) Screen.Main.route else Screen.Onboarding.route
    }

    // "Not now" is an answer, not a request to ask again: the version is remembered, so the next
    // launch neither downloads it again nor puts this dialog back on screen. Installing it by hand
    // from the About page still works.
    fun postponeAutoInstall(version: String) {
        settingsViewModel.saveSkippedAutoInstallVersion(version)
        autoInstallAsked = null
    }

    autoInstallAsked?.let { ready ->
        AlertDialog(
            onDismissRequest = { postponeAutoInstall(ready.version) },
            title = { Text(stringResource(Res.string.about_install_title, ready.version)) },
            text = { Text(stringResource(Res.string.about_install_message)) },
            confirmButton = {
                TextButton(onClick = {
                    autoInstallAsked = null
                    installRelease(ready)
                }) { Text(stringResource(Res.string.about_install_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { postponeAutoInstall(ready.version) }) {
                    Text(stringResource(Res.string.about_install_cancel))
                }
            },
        )
    }

    // Every privileged route refused and the system installer would not open. The report is one
    // line per route (Shizuku directories, root, …), because which privilege failed decides what
    // the reader does next; the download page is the way out that always exists.
    installFailure?.let { report ->
        AlertDialog(
            onDismissRequest = { installFailure = null },
            title = { Text(stringResource(Res.string.about_install_failed_title)) },
            text = {
                // Scrollable: one line per route, and a narrow screen must still be able to read
                // every one of them before deciding what to do next.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(report) }
            },
            confirmButton = {
                TextButton(onClick = {
                    platform.openUrl(DOWNLOAD_PAGE_URL)
                    installFailure = null
                }) { Text(stringResource(Res.string.about_download_page)) }
            },
            dismissButton = {
                TextButton(onClick = { installFailure = null }) {
                    Text(stringResource(Res.string.got_it))
                }
            },
        )
    }

    // The page under the bar drives it: scrolling down hides it, dragging back up shows it.
    val (dock, dockScroll) = rememberDockVisibility()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    // A page arrives at its top, so the bar is always there until something is scrolled.
    LaunchedEffect(currentRoute) { dock.show() }

    // Without a proxy this phone reads the community backend across a border it cannot reach: the
    // pages then show cached or empty data while the network itself is fine, which looks like a
    // broken app. The platform is asked once a minute while a page is open — the reminder goes away
    // by itself as soon as a VPN carries the traffic, and a reader who picked "不再提示" never sees
    // it again, because that answer is stored rather than remembered for this session.
    val hideVpnNotice by settingsViewModel.hideVpnNotice.collectAsStateWithLifecycle()
    var vpnNoticeVisible by remember { mutableStateOf(false) }
    LaunchedEffect(hideVpnNotice, currentRoute, appVisible) {
        val onOnboarding = currentRoute == null || currentRoute == Screen.Onboarding.route
        if (hideVpnNotice || onOnboarding || !appVisible || !platform.supportsVpnDetection()) {
            vpnNoticeVisible = false
            return@LaunchedEffect
        }
        while (true) {
            if (!platform.isVpnActive()) {
                // Shown, then taken away again: a notice that never leaves is a banner, and this one
                // comes back by itself a minute later while no VPN carries the traffic.
                vpnNoticeVisible = true
                delay(VPN_NOTICE_VISIBLE_MILLIS)
                vpnNoticeVisible = false
            }
            // Re-checked every round: a proxy opened from the reminder, then closed again, has to
            // bring the notice back without waiting for the next launch.
            delay(VPN_NOTICE_INTERVAL_MILLIS)
        }
    }

    // The inbox: everything that happened to what this install wrote, counted for the red dot on
    // the app list's bar and in Settings. That is no longer "replies" alone — a heart on a comment
    // and a comment under a plan this install uploaded are the same news to the person reading it
    // — so the count comes from `inbox_summary`, one call that answers four numbers instead of
    // pulling the rows back to count them here.
    //
    // "Unread" means *since the inbox was last opened*, and the cursor for that is an instant the
    // backend stamped on that visit: this count is asked for with it, so a list that has been read
    // answers 0 and the dot stays off — after a restart, and on the way back to the app list. A dot
    // that comes back the moment you leave the page is not a notification, it is a tally.
    //
    // A backend older than that function still answers `comments_replies_to`, so the fallback counts
    // one page of replies and keeps the dot's old meaning instead of never lighting up.
    var unreadReplies by remember { mutableIntStateOf(0) }
    // Unread direct messages and group messages, counted apart from the news above: the bar entry
    // wears one number for both, the way a chat app's messages tab does, and the page itself splits
    // them again.
    var unreadChats by remember { mutableIntStateOf(0) }
    var lastSeenReplyId by remember { mutableStateOf(0L) }
    var inboxSeenAt by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        deps.notes.ensureReady()
        lastSeenReplyId = deps.notes.lastSeenReplyId.value
        inboxSeenAt = deps.notes.inboxSeenAt.value
    }
    // Counting again when the app comes back is what keeps the dot honest: replies arriving while
    // the phone was in a pocket would otherwise wait for a page change that may never come.
    var countTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { countTick++ }
    // 一直停在这一页也要跟上：别的手机在这期间发来消息，红点和底栏的数字不该等到切页才动。
    // 60 秒一次，只在带着这几个数字的页面上跑（下面那个 effect 会自己筛）。
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            countTick++
        }
    }

    // 聊天图片只留 10 天：启动时清一次就够了（一天开几次也只多问几次，后端限 10 次/分钟）。
    // 清不掉不打扰任何人——它只是"顺手把旧图删掉"，失败不影响任何页面。
    LaunchedEffect(Unit) {
        runCatching { socialRepository.purgeOldImages(10) }
    }
    LaunchedEffect(currentRoute, countTick) {
        // The three pages that carry the count: the app list (top bar bell), the messages page
        // (its own row and the bar entry) and the interactions list. Anywhere else the number is
        // not on screen, so counting there would be a call for nothing.
        val countsHere = currentRoute == Screen.Main.route ||
            currentRoute == Screen.Messages.route ||
            currentRoute == Screen.Interactions.route ||
            currentRoute == Screen.MyComments.route ||
            // "我的"把未读数写进了"我的评论"那一行的副标题，所以它也得算一份。
            currentRoute == Screen.Mine.route
        if (!countsHere) return@LaunchedEffect
        deps.notes.ensureReady()
        val identity = DeviceIdentity.current(deps.settings)
        val owner = identity.owner.ifEmpty { identity.token }
        val seen = deps.notes.lastSeenReplyId.value
        lastSeenReplyId = seen
        val seenAt = deps.notes.inboxSeenAt.value
        inboxSeenAt = seenAt
        unreadReplies = if (owner.isEmpty()) {
            0
        } else {
            // Four counts in one cheap call, all of them "since the inbox was last opened": the
            // cursor is the instant the backend stamped on that visit, so nothing here depends on
            // the phone's clock, and a list that has been read counts 0. The fallback keeps the old
            // meaning on a deployment that predates the cursor: one page of replies, never the whole
            // history.
            communityRepository.inboxSummary(owner, identity.token, seenAt)?.total
                ?: communityRepository.repliesToMe(owner, identity.token, offset = 0)
                    .unreadSince(seen).size
        }
        // The chats are their own count on the backend, and they are read on the same three pages:
        // one more call, where the summary above is already being asked for.
        unreadChats = if (owner.isEmpty()) {
            0
        } else {
            socialRepository.conversationsFor(owner).sumOf { it.unread }
        }
    }

    // 红点只代表一件事：**有东西进精选了**（见 `appliedQueueFingerprint`）。
    //
    // 以前这里比的是整份队列的票数指纹，于是别人给任何一条投一票、撤一票都会亮一次——那些票
    // 和这一页的读者没有关系，那条东西既不是他投的、也没上榜。现在存的只有上榜条目的 id：
    // 多一条上榜才亮，掉榜不算，票数怎么动都不算。看过投票那一格之后写回新的指纹。
    //
    // 队列在这里也拉一次，是因为点必须在页面打开之前就在：要先进去看才知道有东西可看的提示，
    // 不算提示。一次 resume 一趟调用，拉失败就保持原样。
    //
    // [newVotes] 是"有没有新上榜"，[newVoteCount] 是"新上榜几条"，后者就是标记上那个数字。
    var newVotes by remember { mutableStateOf(false) }
    var newVoteCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(countTick) {
        val identity = DeviceIdentity.current(deps.settings)
        val voter = identity.owner.ifEmpty { identity.token }
        val queue = communityRepository.suggestedApps(voter) ?: return@LaunchedEffect
        val seen = deps.settings.votesSeenFlow.first()
        newVotes = hasNewApplied(seen, queue)
        newVoteCount = newAppliedSince(seen, queue)
    }

    // A newer build than the one installed, for the marker that leads the way to it: the Settings
    // entry in the bar, the 关于 row on that page, and the check card inside it. Same release page
    // as the poll above (no API quota), read once per resume, and only while the automatic path is
    // off — with it on, the APK is fetched and installed without anyone having to be led anywhere.
    //
    // This one says "a newer build is published", not "something happened": it is a fact about the
    // installed APK, so it goes out when the app is actually replaced — not when a page is looked
    // at — and a read that failed leaves it exactly as it was instead of switching the guide off.
    // A build that came from F-Droid is left alone too: its signature is the store's, so this
    // repo's APK could not replace it and the marker would only point at a dead end.
    var updateAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(updateChannel, autoInstallUpdates, countTick) {
        if (updateChannel == null || autoInstallUpdates) return@LaunchedEffect
        if (platform.isInstalledFromFdroid()) return@LaunchedEffect
        val channel = if (updateChannel == "dev") UpdateChannel.DEVELOPMENT else UpdateChannel.STABLE
        val release = try {
            latestReleaseFromPage(updateClient, channel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing to report: the about page is where a failed check is named, and the next
            // resume is the retry.
            null
        }
        if (release != null) updateAvailable = release.isNewerThanInstalled()
    }

    // A share code pasted into the clipboard before the app was opened: read it once, then open
    // that plan as soon as the app itself is on screen (not over onboarding).
    //
    // "Once" is the whole point. The clipboard holds the code until something replaces it, so a
    // read on every composition opens the same plan again and again — which is what "它识别很多次"
    // was. The code that was acted on is remembered here, and reading again never re-opens it.
    var sharedPlan by remember { mutableStateOf<Long?>(null) }
    /** The clipboard text already acted on: reading it again must not open the same plan twice. */
    var consumedText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val text = platform.readClipboard()
        val found = ShareCode.parse(text)
        // Compared against the whole text, not just the id: copying the same code again replaces
        // the clipboard, and a person who does that expects it to open again. Nothing but a fresh
        // copy changes this string, so a recomposition still opens nothing.
        if (found != null && text != consumedText) sharedPlan = found
    }
    LaunchedEffect(sharedPlan, currentRoute) {
        val planId = sharedPlan ?: return@LaunchedEffect
        if (currentRoute != Screen.Main.route) return@LaunchedEffect
        sharedPlan = null
        consumedText = platform.readClipboard()
        platform.showMessage("识别到分享码，正在打开方案 #$planId")
        navController.navigate(Screen.PlanDetail.routeFor(planId))
    }

    Box(modifier = Modifier.fillMaxSize().dockScroll(dockScroll)) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            // The drawer: a page comes in from the right edge over the one it was opened from,
            // which gives up a quarter of its width and stays put behind it. Leaving does the
            // same in reverse — the page slides back out to the right and the page under it
            // returns to the middle. Nothing shrinks towards the centre: that was the platform's
            // dialog animation, and a page is not a dialog.
            enterTransition = {
                slideInHorizontally(tween(PAGE_SLIDE_MS)) { it }
            },
            exitTransition = {
                slideOutHorizontally(tween(PAGE_SLIDE_MS)) { -it / 4 }
            },
            popEnterTransition = {
                slideInHorizontally(tween(PAGE_SLIDE_MS)) { -it / 4 }
            },
            popExitTransition = {
                slideOutHorizontally(tween(PAGE_SLIDE_MS)) { it }
            },
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    platform = platform,
                    handler = handler,
                    closeApp = closeApp,
                    onFinish = { contribute ->
                        settingsViewModel.completeOnboarding(contribute)
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(Screen.Main.route) {
                MainScreen(
                    handler = handler,
                    platform = platform,
                    pagerState = pagerState,
                    mainViewModel = mainViewModel,
                    appListViewModel = appListViewModel,
                    settingsViewModel = settingsViewModel,
                    history = deps.history,
                    presetEditMode = presetViewModel.editingPreset != null,
                    onPresetEditFinish = {
                        val preset = presetViewModel.editingPreset ?: return@MainScreen
                        presetViewModel.setPresetApps(
                            preset = preset,
                            // Snapshot: the selection is cleared right after saving.
                            newApps = appListViewModel.selectedApps.toSet(),
                            onSuccess = {
                                appListViewModel.selectedApps.clear()
                                presetViewModel.editingPreset = null
                                navController.navigate(Screen.Presets.route)
                            },
                            onError = {
                                coroutineScope.launch { platform.showMessage(getString(Res.string.preset_save_error)) }
                            },
                        )
                    },
                    navigateToPage = { navController.navigate(it) },
                    closeApp = closeApp,
                    enableSelectAll = versionTapCounter >= SECRET_TAPS,
                )
            }
            composable(route = Screen.Rules.route) {
                SafetyRulesScreen(
                    handler = handler,
                    appListViewModel = appListViewModel,
                    settings = deps.settings,
                    telemetry = deps.packageTelemetry,
                    onOpenPlan = { id -> navController.navigate(Screen.PlanDetail.routeFor(id)) },
                    onOpenApp = { pkg -> navController.navigate(Screen.AppDetail.routeForApp(pkg)) },
                    // The shared client, and the list this phone saw last time, so a page opened
                    // right after a reboot is not empty while the request is in flight.
                    repository = communityRepository,
                    planCache = deps.planCache,
                    onNavigateBack = { navController.navigateUp() },
                )
            }
            composable(route = Screen.Tweaks.route) {
                TweaksScreen(handler = handler, onNavigateBack = { navController.navigateUp() })
            }
            composable(route = Screen.Recommendations.route) {
                // The proposed half is written under this install's identity, the same way the
                // description corrections and the comments are; the arrangement is a stored setting,
                // so the page opens the way it was left rather than resetting to the grid.
                var recommendAuthor by remember { mutableStateOf("") }
                var recommendIdentity by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    recommendAuthor = DeviceIdentity.displayName(deps.settings)
                    val identity = DeviceIdentity.current(deps.settings)
                    recommendIdentity = identity.owner.ifEmpty { identity.token }
                }
                val listMode by settingsViewModel.recommendationsListMode.collectAsStateWithLifecycle()
                RecommendationsScreen(
                    platform = platform,
                    repository = communityRepository,
                    author = recommendAuthor,
                    authorId = recommendIdentity,
                    asGrid = !listMode,
                    onToggleLayout = { settingsViewModel.saveRecommendationsListMode(!listMode) },
                    // What this phone has, so an entry can point at the packages it replaces and
                    // take the reader to the page where they are removed.
                    installedPackages = appListViewModel.apps.map { it.packageName }.toSet(),
                    onOpenApp = { pkg -> navController.navigate(Screen.AppDetail.routeForApp(pkg)) },
                    // 新上榜的条数：那一个点画在投票那一格上（页内），底栏那格只在离开这一页时
                    // 还留着，两处说的是同一件事，不是两份消息。
                    newApplied = newVoteCount,
                    // 投票那一格看完：把此刻上榜的 id 列表存下来，点熄灭。
                    // 存进设置而不只是内存清零，重启之后没有新上榜也保持安静。
                    onVoteQueueSeen = { fingerprint ->
                        coroutineScope.launch {
                            deps.settings.setVotesSeen(fingerprint)
                            newVotes = false
                            newVoteCount = 0
                        }
                    },
                )
            }
            composable(route = Screen.Logs.route) {
                LogsScreen(
                    platform = platform,
                    onNavigateBack = { navController.navigateUp() },
                )
            }
            composable(route = Screen.About.route) {
                AboutScreen(
                    platform = platform,
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { navController.navigateUp() },
                    // What the start-up read already knows, so the row can say "there is one" before
                    // the tap that checks: the dot is the guide, the check is what confirms it.
                    updateAvailable = updateAvailable,
                    // 连点版本号：隐藏的"全选推荐应用"开关。手势挂在关于页的版本号上。
                    onVersionTap = {
                        versionTapCounter += 1
                        coroutineScope.launch {
                            when {
                                versionTapCounter == SECRET_TAPS ->
                                    platform.showMessage(
                                        getString(
                                            Res.string.select_all_enabled,
                                            getString(Res.string.badge_recommended_name),
                                        )
                                    )
                                versionTapCounter in (SECRET_TAPS_HINT_AFTER + 1)..<SECRET_TAPS ->
                                    platform.showMessage(getString(Res.string.select_all_tip, SECRET_TAPS - versionTapCounter))
                            }
                        }
                    },
                )
            }
            composable(route = Screen.Theme.route) {
                ThemeScreen(
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { navController.navigateUp() },
                )
            }
            composable(route = Screen.Mine.route) {
                MineScreen(
                    settingsViewModel = settingsViewModel,
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenMyComments = { navController.navigate(Screen.MyComments.route) },
                    onOpenAbout = { navController.navigate(Screen.About.route) },
                    unreadReplies = unreadReplies,
                    // Same dot as the bar's: the about page behind this row is where updates live.
                    updateAvailable = updateAvailable,
                )
            }
            composable(route = Screen.Settings.route) {
                val fakeScores by appListViewModel.fakeScoresEnabled.collectAsStateWithLifecycle()
                var deviceUsers by remember { mutableStateOf(emptyList<DeviceUser>()) }
                var selectedUserId by remember { mutableStateOf(handler.selectedUserId) }
                // The chooser's own list, read when it opens and again after every action in it:
                // each action ends in another app (the root manager's prompt, Stellar's service),
                // so nothing here changes until the user comes back from it.
                var showPrivilegeChooser by remember { mutableStateOf(false) }
                var privilegeChoices by remember { mutableStateOf(emptyList<PrivilegeChoice>()) }
                val reloadPrivilegeChoices: () -> Unit = {
                    coroutineScope.launch { privilegeChoices = handler.privilegeChoices() }
                }
                // Said when the manager answers no: a press that changes nothing on screen reads as
                // a broken button, while the fix (allow it in the manager, or the phone is not
                // rooted) is somewhere else entirely.
                val privilegeRefused = stringResource(Res.string.privilege_root_refused)
                val privilegeOpened = stringResource(Res.string.privilege_root_opened)
                LaunchedEffect(showPrivilegeChooser) {
                    if (showPrivilegeChooser) privilegeChoices = handler.privilegeChoices()
                }
                if (showPrivilegeChooser) {
                    PrivilegeChooserDialog(
                        choices = privilegeChoices,
                        onDismiss = { showPrivilegeChooser = false },
                        onRefresh = reloadPrivilegeChoices,
                        onGrantRoot = {
                            // The root manager's own prompt is the grant, and only the handler can
                            // raise it: the dialog gets out of its way and the answer is read back
                            // once it has been given. `authorize`, not `requestAuthorization`, because
                            // the chain settles once: a root grant given here has to take over from
                            // Shizuku now, not wait for the next launch.
                            showPrivilegeChooser = false
                            coroutineScope.launch {
                                val granted = handler.authorize(PrivilegeKind.ROOT)
                                if (!granted) {
                                    // The grant belongs to the manager, and it has no API to be
                                    // called into: opening its screen is the only way to help. The
                                    // message says which of the two happened, so "nothing changed"
                                    // never has to be guessed at.
                                    val opened = ROOT_MANAGER_PACKAGES.any { platform.openApp(it) }
                                    platform.showMessage(if (opened) privilegeOpened else privilegeRefused)
                                }
                                privilegeChoices = handler.privilegeChoices()
                                appListViewModel.loadApps()
                            }
                        },
                        onOpenManager = { kind ->
                            if (kind == PrivilegeKind.DHIZUKU) {
                                // Dhizuku has no service to start: when it is up, the grant is given
                                // in its own approval screen, which the handler raises. When it is
                                // not up (never activated, or stopped), opening the app is the only
                                // step that helps, so that is the fallback.
                                coroutineScope.launch {
                                    showPrivilegeChooser = false
                                    val granted = handler.authorize(PrivilegeKind.DHIZUKU)
                                    if (!granted) {
                                        val opened = platform.openApp(DHIZUKU_PACKAGE)
                                        if (!opened) platform.openUrl(DHIZUKU_URL)
                                    }
                                    privilegeChoices = handler.privilegeChoices()
                                    if (granted) appListViewModel.loadApps()
                                }
                            } else {
                                // The manager's own app is the only place its service can be started
                                // or Canta allowed, so open it; the releases page stays as the
                                // fallback for a phone that has neither manager installed.
                                val opened =
                                    platform.openApp(STELLAR_PACKAGE) || platform.openApp(SHIZUKU_PACKAGE)
                                if (!opened) platform.openUrl(STELLAR_URL)
                            }
                        },
                        onDownload = { kind ->
                            when (kind) {
                                PrivilegeKind.SHIZUKU -> platform.openUrl(STELLAR_URL)
                                PrivilegeKind.DHIZUKU -> platform.openUrl(DHIZUKU_URL)
                                // Root has nothing to download; its row never offers this.
                                else -> Unit
                            }
                        },
                        onSwitch = { kind ->
                            // A route that already holds its grant needs no screen of its own: the
                            // chain moves onto it, and the settings row names it from then on.
                            coroutineScope.launch {
                                showPrivilegeChooser = false
                                handler.authorize(kind)
                                privilegeChoices = handler.privilegeChoices()
                                appListViewModel.loadApps()
                            }
                        },
                    )
                }
                LaunchedEffect(Unit) {
                    if (handler.isAuthorized) {
                        deviceUsers = handler.availableUsers()
                        selectedUserId = handler.selectedUserId
                    }
                }
                SettingsScreen(
                    platform = platform,
                    onNavigateBack = { navController.navigateUp() },
                    settingsViewModel = settingsViewModel,
                    deviceUsers = deviceUsers,
                    selectedUserId = selectedUserId,
                    fakeScores = fakeScores,
                    onFakeScoresChange = appListViewModel::setFakeScores,
                    // What is granting the privilege right now, so the page can name it instead of
                    // leaving the user to guess between root and Stellar/Shizuku.
                    privilegeKind = handler.privilegeKind,
                    privilegeStatus = handler.privilegeStatus,
                    // Tapping the row opens the chooser, so the user decides between root and
                    // Stellar/Shizuku instead of being handed whichever the chain picked.
                    privilegeChoices = privilegeChoices,
                    onOpenPrivilegeChooser = { showPrivilegeChooser = true },
                    onUserSelected = { id ->
                        coroutineScope.launch {
                            handler.selectUser(id)
                            selectedUserId = handler.selectedUserId
                            appListViewModel.selectedApps.clear()
                            appListViewModel.loadApps()
                        }
                    },
                    onOpenTheme = { navController.navigate(Screen.Theme.route) },
                    // 救急用的文件：删掉的包还在系统分区里，恢复命令得在电脑上跑。
                    // 生成的批处理交给分享面板（发到电脑），打不开面板的构建就留在下载目录。
                    onExportRestoreScript = { mode ->
                        coroutineScope.launch {
                            val exported = deps.history.shareRestoreScript(mode)
                            platform.showMessage(
                                when {
                                    exported.shared && mode == RestoreMode.ALL ->
                                        "已生成一键恢复脚本（全部已卸载的包），选「发送到电脑」即可"

                                    exported.shared && exported.packages > 0 ->
                                        "已生成 ${exported.packages} 个包名的恢复脚本，选「发送到电脑」即可"

                                    exported.shared -> "已生成恢复脚本（记录里没有待恢复的包），选「发送到电脑」即可"
                                    exported.path != null ->
                                        "已保存到 ${exported.path}，拷到电脑上双击运行"

                                    else -> "这个版本不能导出文件"
                                }
                            )
                        }
                    },
                )
            }
            composable(route = Screen.Presets.route) {
                PresetsScreen(
                    platform = platform,
                    presetViewModel = presetViewModel,
                    onNavigateBack = { appliedPreset ->
                        appliedPreset?.let { preset ->
                            // Select only preset apps shown on the current tab.
                            // Unknown packages can't match any tab, so they are
                            // dropped too: handlers couldn't act on them anyway.
                            val byPackage = appListViewModel.apps.associateBy { it.packageName }
                            appListViewModel.selectedApps.clear()
                            appListViewModel.selectedApps.addAll(
                                preset.apps.filter { pkg ->
                                    byPackage[pkg]?.let { selectedAppsType.matches(it) } == true
                                }
                            )
                        }
                        navController.navigateUp()
                    },
                    appListViewModel = appListViewModel,
                )
            }
            composable(route = Screen.PlanComment.route) { entry ->
                // A plan opened on one of its comments: the same page, plus the thread to lead with.
                val args = entry.arguments
                val planId = args?.let { a ->
                    runCatching { a.read { if (contains("planId")) getString("planId").toLongOrNull() else null } }
                        .getOrNull()
                } ?: 0L
                val commentId = args?.let { a ->
                    runCatching { a.read { if (contains("commentId")) getString("commentId").toLongOrNull() else null } }
                        .getOrNull()
                } ?: 0L
                PlanDetailRoute(
                    planId = planId,
                    anchorCommentId = commentId,
                    navController = navController,
                    handler = handler,
                    deps = deps,
                    communityRepository = communityRepository,
                    appListViewModel = appListViewModel,
                    platform = platform,
                )
            }
            composable(route = Screen.PlanDetail.route) { entry ->
                // Navigation 2.10 hands out a SavedState, not a Bundle: read it the same way the
                // navigation library itself does. The id is an undeclared path argument, so it
                // arrives as a plain string.
                val planId = entry.arguments
                    ?.let { args ->
                        runCatching {
                            args.read { if (contains("planId")) getString("planId").toLongOrNull() else null }
                        }.getOrNull()
                    } ?: 0L
                PlanDetailRoute(
                    planId = planId,
                    anchorCommentId = 0L,
                    navController = navController,
                    handler = handler,
                    deps = deps,
                    communityRepository = communityRepository,
                    appListViewModel = appListViewModel,
                    platform = platform,
                )
            }
            composable(route = Screen.AppComment.route) { entry ->
                // An app page opened on one of its comments: the same page plus the comment to lead
                // with. Both segments are undeclared path arguments, read the way navigation does.
                val args = entry.arguments
                val packageName = args?.let { a ->
                    runCatching { a.read { if (contains("package")) getString("package") else null } }.getOrNull()
                }.orEmpty()
                val commentId = args?.let { a ->
                    runCatching { a.read { if (contains("commentId")) getString("commentId").toLongOrNull() else null } }
                        .getOrNull()
                } ?: 0L
                AppDetailRoute(
                    packageName = packageName,
                    anchorCommentId = commentId,
                    navController = navController,
                    handler = handler,
                    deps = deps,
                    communityRepository = communityRepository,
                    appListViewModel = appListViewModel,
                    platform = platform,
                    mainViewModel = mainViewModel,
                )
            }
            composable(route = Screen.AppDetail.route) { entry ->
                // The package name is an undeclared path argument, read the way navigation
                // itself reads it.
                val packageName = entry.arguments
                    ?.let { args ->
                        runCatching {
                            args.read { if (contains("package")) getString("package") else null }
                        }.getOrNull()
                    }.orEmpty()
                AppDetailRoute(
                    packageName = packageName,
                    anchorCommentId = 0L,
                    navController = navController,
                    handler = handler,
                    deps = deps,
                    communityRepository = communityRepository,
                    appListViewModel = appListViewModel,
                    platform = platform,
                    mainViewModel = mainViewModel,
                )
            }
            composable(route = Screen.Uploader.route) { entry ->
                // The identity number is an undeclared path argument, like every other page's key.
                val owner = entry.arguments
                    ?.let { args ->
                        runCatching {
                            args.read { if (contains("owner")) getString("owner") else null }
                        }.getOrNull()
                    }.orEmpty()
                var profileIdentity by remember { mutableStateOf(Identity("")) }
                LaunchedEffect(owner) { profileIdentity = DeviceIdentity.current(deps.settings) }
                UserProfileScreen(
                    repository = communityRepository,
                    social = socialRepository,
                    owner = owner,
                    viewer = profileIdentity.owner.ifEmpty { profileIdentity.token },
                    onOpenPlan = { id -> navController.navigate(Screen.PlanDetail.routeFor(id)) },
                    onOpenChat = { peer ->
                        // 从主页点私信：开一个会话再进去，dm_open 对已有会话是复用的。
                        coroutineScope.launch {
                            val self = profileIdentity.owner.ifEmpty { profileIdentity.token }
                            val opened = socialRepository.openDm(self, peer)
                            if (opened != null && opened > 0L) {
                                navController.navigate(Screen.Chat.routeFor(opened))
                            } else {
                                platform.showMessage("开不了私聊，检查代理后再试")
                            }
                        }
                    },
                    onNavigateBack = { navController.navigateUp() },
                    onOpenFollows = { followers ->
                        navController.navigate(Screen.FollowList.routeFor(owner, followers))
                    },
                    onMessage = { line -> coroutineScope.launch { platform.showMessage(line) } },
                )
            }
            composable(route = Screen.DescriptionFix.route) { entry ->
                // The package name is the same undeclared path argument the app page reads.
                val packageName = entry.arguments
                    ?.let { args ->
                        runCatching {
                            args.read { if (contains("package")) getString("package") else null }
                        }.getOrNull()
                    }.orEmpty()
                var fixAuthor by remember { mutableStateOf("") }
                var fixIdentity by remember { mutableStateOf("") }
                LaunchedEffect(packageName) {
                    fixAuthor = DeviceIdentity.displayName(deps.settings)
                    val identity = DeviceIdentity.current(deps.settings)
                    fixIdentity = identity.owner.ifEmpty { identity.token }
                }
                val fixedApp = appListViewModel.apps.firstOrNull { it.packageName == packageName }
                DescriptionFixScreen(
                    packageName = packageName,
                    appName = fixedApp?.name ?: packageName,
                    publishedDescription = fixedApp?.description.orEmpty(),
                    repository = communityRepository,
                    author = fixAuthor,
                    authorId = fixIdentity,
                    onNavigateBack = { navController.navigateUp() },
                )
            }
            composable(route = Screen.MyComments.route) {
                // 这一页现在是「我的方案」：只问身份（上传记录按身份挂着），不写评论、不收消息——
                // 评论和互动都在「消息 → 互动消息」里，这一页只剩「我上传过什么」。
                var myIdentity by remember { mutableStateOf(Identity("")) }
                // "My plans" asks for them by identity; this phone is only the fallback for a
                // deployment that predates `plans_by_owner`.
                var myProfile by remember { mutableStateOf<DeviceProfile?>(null) }
                LaunchedEffect(Unit) {
                    myIdentity = DeviceIdentity.current(deps.settings)
                    myProfile = handler.deviceProfile()
                }
                MyPlansScreen(
                    repository = communityRepository,
                    owner = myIdentity.owner.ifEmpty { myIdentity.token },
                    token = myIdentity.token,
                    onOpenPlan = { id -> navController.navigate(Screen.PlanDetail.routeFor(id)) },
                    profile = myProfile,
                    onNavigateBack = { navController.navigateUp() },
                )
            }
            composable(route = Screen.Messages.route) {
                // The messages page is about one identity: the name an outgoing message is signed
                // with, and the number plus token that prove it to the backend.
                var messagesName by remember { mutableStateOf("") }
                var messagesIdentity by remember { mutableStateOf(Identity("")) }
                // 拉黑名单是本机记录（后端没有这个接口），来源只有设置里那一份；消息页据此把
                // 这些人的私聊从列表里滤掉。用 StateFlow，所以在别处改了这一页也会跟着刷新。
                val blockedPeers by settingsViewModel.blockedPeers.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    messagesName = DeviceIdentity.displayName(deps.settings)
                    messagesIdentity = DeviceIdentity.current(deps.settings)
                }
                MessagesScreen(
                    repository = socialRepository,
                    planRepository = communityRepository,
                    owner = messagesIdentity.owner.ifEmpty { messagesIdentity.token },
                    userName = messagesName,
                    token = messagesIdentity.token,
                    // The same count the bar and the top bar carry: the page repeats it on the
                    // interactions row, and none of it is a second source of truth.
                    inboxUnread = unreadReplies,
                    inboxPreview = "",
                    onOpenInteractions = {
                        navController.navigate(Screen.Interactions.route) { launchSingleTop = true }
                    },
                    onOpenChat = { id -> navController.navigate(Screen.Chat.routeFor(id)) },
                    onOpenCreateGroup = { navController.navigate(Screen.CreateGroup.route) },
                    onOpenGroups = { navController.navigate(Screen.Groups.route) },
                    onOpenDm = { peer ->
                        // 点一个只有关注关系的人：先开一个私聊再进去，dm_open 本身复用已有会话。
                        coroutineScope.launch {
                            val self = messagesIdentity.owner.ifEmpty { messagesIdentity.token }
                            val opened = socialRepository.openDm(self, peer)
                            if (opened != null && opened > 0L) {
                                navController.navigate(Screen.Chat.routeFor(opened))
                            } else {
                                platform.showMessage("开不了私聊，检查代理后再试")
                            }
                        }
                    },
                    // 拉黑：本机名单 + 藏掉那一行；落盘交给 view model，这一页不碰设置文件。
                    blockedPeers = blockedPeers,
                    onBlockPeer = { peer, blocked ->
                        settingsViewModel.saveBlockedPeer(peer, blocked)
                    },
                    // 系统没给通知权限时，页顶摆一条「当前无法接收朋友的消息提醒」，点它申请权限。
                    // 只在真能申请通知的平台上摆（Android）；桌面端 `canRequestNotify` 为 false，
                    // 摆一条点不开的提示等于骗人。
                    noticesBlocked = platform.canRequestNotify && !platform.canNotify(),
                    onEnableNotices = {
                        coroutineScope.launch {
                            if (!platform.requestNotifyPermission()) {
                                platform.showMessage("没有通知权限，系统不会显示消息提醒")
                            }
                        }
                    },
                    onMessage = { line -> coroutineScope.launch { platform.showMessage(line) } },
                )
            }
            composable(route = Screen.Interactions.route) {
                var interactionsIdentity by remember { mutableStateOf(Identity("")) }
                // 「我的评论」那一栏的头像首字：这台设备的显示名。
                var interactionsName by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    interactionsIdentity = DeviceIdentity.current(deps.settings)
                    interactionsName = DeviceIdentity.displayName(deps.settings)
                }
                InteractionsScreen(
                    repository = communityRepository,
                    social = socialRepository,
                    owner = interactionsIdentity.owner.ifEmpty { interactionsIdentity.token },
                    token = interactionsIdentity.token,
                    author = interactionsName,
                    seenAt = inboxSeenAt,
                    onInboxSeen = { newest, checkedAt ->
                        // The same closing of the loop the bar does: reading a row is what moves
                        // the cursor the count is taken from.
                        coroutineScope.launch {
                            val moved = deps.notes.markInboxSeen(checkedAt)
                            deps.notes.markRepliesSeen(newest)
                            inboxSeenAt = deps.notes.inboxSeenAt.value
                            if (moved) unreadReplies = 0
                        }
                    },
                    onOpenPlanComment = { id, commentId ->
                        navController.navigate(Screen.PlanComment.routeFor(id, commentId))
                    },
                    onOpenAppComment = { pkg, commentId ->
                        navController.navigate(Screen.AppComment.routeFor(pkg, commentId))
                    },
                    // 点互动行上的头像，看那个人：InboxItem 里带着 actor 的身份号。
                    onOpenProfile = { person ->
                        navController.navigate(Screen.Uploader.routeFor(person))
                    },
                    onNavigateBack = { navController.navigateUp() },
                )
            }
            composable(route = Screen.Chat.route) { entry ->
                val conversationId = entry.arguments
                    ?.let { args ->
                        runCatching {
                            args.read { if (contains("conversationId")) getString("conversationId") else null }
                        }.getOrNull()
                    }
                    ?.toLongOrNull() ?: 0L
                var chatName by remember { mutableStateOf("") }
                var chatIdentity by remember { mutableStateOf(Identity("")) }
                // A chat is opened by id, and the route only carries the id: the name over the
                // messages is read back from the conversation list, so a group says its own name
                // and a direct chat says the other person's instead of a generic "私聊".
                var chatTitle by remember { mutableStateOf("") }
                var chatIsGroup by remember { mutableStateOf(false) }
                // 私聊对面是谁：拉黑名单里存的是身份号，所以这里要的是 peer 而不是标题。
                var chatPeer by remember { mutableStateOf("") }
                // 草稿按会话存：离开这一页再回来，上次没说完的话还在。读取用 StateFlow，
                // 所以在别处改了草稿这里也会跟着刷新。
                val chatDrafts by settingsViewModel.chatDrafts.collectAsStateWithLifecycle()
                val chatBlockedPeers by settingsViewModel.blockedPeers.collectAsStateWithLifecycle()
                LaunchedEffect(conversationId) {
                    chatName = DeviceIdentity.displayName(deps.settings)
                    val identity = DeviceIdentity.current(deps.settings)
                    chatIdentity = identity
                    if (conversationId > 0L) {
                        val self = identity.owner.ifEmpty { identity.token }
                        val opened = socialRepository.conversationsFor(self)
                            .firstOrNull { it.id == conversationId }
                        chatTitle = opened
                            ?.let { row -> row.title.ifBlank { row.peerName.ifBlank { row.peer } } }
                            .orEmpty()
                        chatIsGroup = opened?.isGroup == true
                        chatPeer = opened?.peer.orEmpty().ifEmpty { opened?.peerName.orEmpty() }
                    }
                }
                ChatScreen(
                    repository = socialRepository,
                    owner = chatIdentity.owner.ifEmpty { chatIdentity.token },
                    userName = chatName,
                    conversationId = conversationId,
                    title = chatTitle,
                    isGroup = chatIsGroup,
                    onNavigateBack = { navController.navigateUp() },
                    onOpenGroupInfo = {
                        navController.navigate(Screen.GroupInfo.routeFor(conversationId))
                    },
                    // 点头像看那个人的主页：私聊的对端，或群里发言的那个人。
                    onOpenProfile = { person ->
                        navController.navigate(Screen.Uploader.routeFor(person))
                    },
                    // 发图片：相册由平台层拉起来，桌面/网页没有相册就回答 null，页面静默放弃。
                    pickImage = { platform.pickImage() },
                    // 草稿：进来时把这一条上次没发完的话填回去，改动由页面防抖后写回。
                    initialDraft = chatDrafts.firstOrNull { it.first == conversationId }?.second.orEmpty(),
                    onDraftPersist = { text -> settingsViewModel.saveChatDraft(conversationId, text) },
                    // 拉黑只对私聊有意义：群里没有"那个人"，群不受本机名单影响。
                    blocked = !chatIsGroup && chatPeer.isNotEmpty() && chatPeer in chatBlockedPeers,
                    // 实时：对方一发就到，不再等轮询那一下。
                    liveSource = chatLive,
                )
            }
            composable(route = Screen.CreateGroup.route) {
                var groupIdentity by remember { mutableStateOf(Identity("")) }
                LaunchedEffect(Unit) { groupIdentity = DeviceIdentity.current(deps.settings) }
                CreateGroupScreen(
                    repository = socialRepository,
                    owner = groupIdentity.owner.ifEmpty { groupIdentity.token },
                    onCreated = { id ->
                        // 建完直接进去说话：建一个空群站在原地，人还得自己再找一遍。
                        navController.navigate(Screen.Chat.routeFor(id)) {
                            popUpTo(Screen.CreateGroup.route) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.navigateUp() },
                    onMessage = { line -> coroutineScope.launch { platform.showMessage(line) } },
                )
            }
            composable(route = Screen.GroupInfo.route) { entry ->
                val groupId = entry.arguments
                    ?.let { args ->
                        runCatching {
                            args.read { if (contains("conversationId")) getString("conversationId") else null }
                        }.getOrNull()
                    }
                    ?.toLongOrNull() ?: 0L
                var infoIdentity by remember { mutableStateOf(Identity("")) }
                LaunchedEffect(Unit) { infoIdentity = DeviceIdentity.current(deps.settings) }
                GroupSettingsScreen(
                    repository = socialRepository,
                    owner = infoIdentity.owner.ifEmpty { infoIdentity.token },
                    conversationId = groupId,
                    onNavigateBack = { navController.navigateUp() },
                    // 群成员网格里点谁的头像就看谁的主页。
                    onOpenProfile = { person ->
                        navController.navigate(Screen.Uploader.routeFor(person))
                    },
                    onMessage = { line -> coroutineScope.launch { platform.showMessage(line) } },
                    // 「查找聊天内容」：回到消息页，那里有搜索框（会话 / 联系人 / 消息三段）。
                    // 不把搜索词带过去——从群资料页出发时还没有词，人是要去**开始**找。
                    onOpenSearch = {
                        navController.navigate(Screen.Messages.route) { launchSingleTop = true }
                    },
                )
            }
            composable(route = Screen.Groups.route) {
                var groupsIdentity by remember { mutableStateOf(Identity("")) }
                LaunchedEffect(Unit) { groupsIdentity = DeviceIdentity.current(deps.settings) }
                GroupsScreen(
                    repository = socialRepository,
                    owner = groupsIdentity.owner.ifEmpty { groupsIdentity.token },
                    onOpenChat = { id -> navController.navigate(Screen.Chat.routeFor(id)) },
                    onOpenCreateGroup = { navController.navigate(Screen.CreateGroup.route) },
                    onNavigateBack = { navController.navigateUp() },
                    onMessage = { line -> coroutineScope.launch { platform.showMessage(line) } },
                )
            }
            composable(route = Screen.FollowList.route) { entry ->
                val followOwner = entry.arguments
                    ?.let { args ->
                        runCatching {
                            args.read { if (contains("owner")) getString("owner") else null }
                        }.getOrNull()
                    }.orEmpty()
                val followTab = entry.arguments
                    ?.let { args ->
                        runCatching {
                            args.read { if (contains("tab")) getString("tab") else null }
                        }.getOrNull()
                    }.orEmpty()
                var followIdentity by remember { mutableStateOf(Identity("")) }
                LaunchedEffect(Unit) { followIdentity = DeviceIdentity.current(deps.settings) }
                FollowListScreen(
                    repository = socialRepository,
                    owner = followOwner,
                    startOnFollowers = followTab == "followers",
                    onOpenProfile = { person -> navController.navigate(Screen.Uploader.routeFor(person)) },
                    onOpenChat = { peer ->
                        coroutineScope.launch {
                            val self = followIdentity.owner.ifEmpty { followIdentity.token }
                            val opened = socialRepository.openDm(self, peer)
                            if (opened != null && opened > 0L) {
                                navController.navigate(Screen.Chat.routeFor(opened))
                            } else {
                                platform.showMessage("开不了私聊，检查代理后再试")
                            }
                        }
                    },
                    onNavigateBack = { navController.navigateUp() },
                    onMessage = { line -> coroutineScope.launch { platform.showMessage(line) } },
                )
            }
        }

        // Read once at launch; shown only after onboarding, never on top of it.
        var crashReport by remember { mutableStateOf(crashReports?.read()) }
        crashReport?.let { report ->
            if (currentRoute != null && currentRoute != Screen.Onboarding.route) {
                CrashReportDialog(
                    report = report,
                    platform = platform,
                    onClose = {
                        crashReports?.delete()
                        crashReport = null
                    },
                )
            }
        }

        // Floating capsule bar, above the content like the dashboard capsule in FlClash.
        // It steps out of the way while the page scrolls down and returns on the way up.
        if (currentRoute != null && currentRoute in BOTTOM_BAR_ROUTES) {
            CollapsingBottomBar(
                visible = dock.visible,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                FloatingPillBar(
                    items = bottomBarItems(
                        route = currentRoute,
                        showTweaks = showTweaksTab,
                        showRecommend = showRecommendTab,
                        // Nothing to point at while the queue itself is open: that page is the dot.
                        showVoteDot = newVotes && currentRoute != Screen.Recommendations.route,
                        showVoteCount = newVoteCount,
                        // The way to a newer build: 设置 → 关于 → 检查更新, marked at its first step.
                        showUpdateDot = updateAvailable,
                        // What the bell in the top bar counts, on the entry that now leads to the
                        // page the bell opens.
                        showMessageCount = unreadReplies + unreadChats,
                    ),
                    onSelect = { key -> onBottomBarSelect(key, navController) },
                )
            }
        }

        // The proxy reminder covers every page: it is about the app sitting on the wrong side of
        // the network, not about whichever page happens to be open.
        if (vpnNoticeVisible) {
            VpnNoticeCard(
                text = stringResource(Res.string.vpn_notice_text),
                openLabel = stringResource(Res.string.vpn_notice_open),
                neverLabel = stringResource(Res.string.vpn_notice_never),
                onOpenProxy = {
                    // Out of the way as soon as the proxy screen answers; the next round of the
                    // check above brings it back while no VPN carries the traffic.
                    if (platform.openProxySettings()) vpnNoticeVisible = false
                },
                onNeverShow = {
                    vpnNoticeVisible = false
                    settingsViewModel.saveHideVpnNotice(true)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        ThemeToastHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp),
        )
    }
}

/**
 * The proxy reminder: one line saying why the community data is out of reach, and the only two
 * answers that matter — switch a proxy on, or stop being asked.
 *
 * It is deliberately not a dialog: it rides on top of the page, so the app list stays usable while
 * the phone is offline, which is the whole point of "不开也能用".
 */
@Composable
private fun VpnNoticeCard(
    text: String,
    openLabel: String,
    neverLabel: String,
    onOpenProxy: () -> Unit,
    onNeverShow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceVariant,
        contentColor = scheme.onSurfaceVariant,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text = text, style = FlText.caption1, color = scheme.onSurfaceVariant)
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenProxy) {
                    Text(
                        text = openLabel,
                        style = FlText.caption1,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                    )
                }
                TextButton(onClick = onNeverShow) {
                    Text(
                        text = neverLabel,
                        style = FlText.caption1,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Routes that show the floating bar. Onboarding deliberately has none. */
// Every destination the bar itself leads to, so tapping an entry never lands on a page without it.
private val BOTTOM_BAR_ROUTES = setOf(
    Screen.Main.route,
    Screen.Rules.route,
    Screen.Tweaks.route,
    Screen.Recommendations.route,
    Screen.Messages.route,
    Screen.Mine.route,
    Screen.Presets.route,
)

/** Stable keys of the bar entries. */
private object BarKey {
    const val APPS = "apps"
    const val RULES = "rules"
    const val TWEAKS = "tweaks"
    const val RECOMMEND = "recommend"
    const val MESSAGES = "messages"
    const val SETTINGS = "settings"
}

/**
 * The bar's entries: the app list (installed/uninstalled are an in-page switch) and the device
 * safety page always, the ROM tweaks and the recommended apps only when the settings keep them
 * on, and the settings last. The dock centres itself below five entries, so hiding a page just
 * makes the bar narrower.
 */
@Composable
private fun bottomBarItems(
    route: String?,
    showTweaks: Boolean,
    showRecommend: Boolean,
    showVoteDot: Boolean = false,
    /** How many votes are new: shown as the number in the marker, when there is one to show. */
    showVoteCount: Int = 0,
    /** A newer build than this one is published: the Settings entry carries the dot to it. */
    showUpdateDot: Boolean = false,
    /** How much news and how many unread chats wait behind the messages entry. */
    showMessageCount: Int = 0,
): List<BottomBarItem> = buildList {
    // The same glyphs FlClash's own bar leads with — the grid of apps, the rules list,
    // the toolbox, the gear — plus one for the recommendations page.
    add(BottomBarItem(BarKey.APPS, FlGlyphs.dashboard, stringResource(Res.string.tab_apps), route == Screen.Main.route))
    add(BottomBarItem(BarKey.RULES, FlGlyphs.rules, stringResource(Res.string.tab_rules), route == Screen.Rules.route))
    if (showTweaks) {
        add(BottomBarItem(BarKey.TWEAKS, FlGlyphs.tools, stringResource(Res.string.tab_tweaks), route == Screen.Tweaks.route))
    }
    if (showRecommend) {
        // 标记只代表「有东西进精选了」，不是「有票在动」：别人给一条没上榜的东西投票，和这一页
        // 的读者没有关系。看过投票那一格后点就熄灭；数字是新增上榜的条数，只在离开这一页时画。
        val votesOpen = route == Screen.Recommendations.route
        add(
            BottomBarItem(
                BarKey.RECOMMEND,
                FlGlyphs.appsList,
                stringResource(Res.string.tab_recommend),
                votesOpen,
                dot = showVoteDot,
                badge = if (votesOpen) 0 else showVoteCount,
            ),
        )
    }
    add(
        BottomBarItem(
            BarKey.MESSAGES,
            FlGlyphs.requests,
            // A literal rather than a resource key: the page's own labels are Chinese too, and
            // adding a key here would have to be mirrored into every language folder.
            "消息",
            route == Screen.Messages.route,
            // The same number the top bar's bell wears: what arrived while this page was not the
            // one being looked at. On the page itself it is read, so it drops to nothing.
            badge = if (route == Screen.Messages.route) 0 else showMessageCount,
        ),
    )
    add(
        BottomBarItem(
            BarKey.SETTINGS,
            FlGlyphs.settings,
            // 字面量而不是资源键：这一格指向"我的"，开关已经搬到设置页，和"消息"那格一样
            // 不做多语言镜像。
            "我的",
            route == Screen.Mine.route,
            dot = showUpdateDot,
        ),
    )
}

private fun onBottomBarSelect(
    key: String,
    navController: NavHostController,
) {
    when (key) {
        BarKey.APPS -> navController.navigateSingleTop(Screen.Main.route)
        BarKey.RULES -> navController.navigateSingleTop(Screen.Rules.route)
        BarKey.TWEAKS -> navController.navigateSingleTop(Screen.Tweaks.route)
        BarKey.RECOMMEND -> navController.navigateSingleTop(Screen.Recommendations.route)
        BarKey.MESSAGES -> navController.navigateSingleTop(Screen.Messages.route)
        BarKey.SETTINGS -> navController.navigateSingleTop(Screen.Mine.route)
    }
}

/** Navigating to the route already shown would stack a second copy. */
private fun NavHostController.navigateSingleTop(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}

/**
 * The plan page itself, in one place: the plain plan route and the comment-anchored one are the same
 * page with one extra argument, so they share this rather than two copies of the wiring.
 *
 * @param anchorCommentId the comment to lead with, 0 for "top of the page".
 */
@Composable
private fun PlanDetailRoute(
    planId: Long,
    anchorCommentId: Long,
    navController: NavHostController,
    handler: CantaHandler,
    deps: CantaAppDependencies,
    communityRepository: PlanRepository,
    appListViewModel: AppListViewModel,
    platform: CantaPlatform,
) {
    var planProfile by remember { mutableStateOf<DeviceProfile?>(null) }
    // Comments are posted under this install's own code: no account, no login.
    var planAuthor by remember { mutableStateOf("") }
    // The identity number and its secret: what a comment of this install is deleted with.
    var planIdentity by remember { mutableStateOf(Identity("")) }
    // The plan's packages are shown with their icons and their device rules, the same way the app
    // list shows them.
    var planRules by remember { mutableStateOf<SafetyRules?>(null) }
    LaunchedEffect(Unit) {
        planProfile = handler.deviceProfile()
        // The name from the settings, or "#10000" while none was picked.
        planAuthor = DeviceIdentity.displayName(deps.settings)
        planIdentity = DeviceIdentity.current(deps.settings)
        planRules = SafetyRules.bundled()
    }
    PlanDetailScreen(
        planId = planId,
        repository = communityRepository,
        profile = planProfile,
        onNavigateBack = { navController.navigateUp() },
        author = planAuthor,
        apps = appListViewModel.apps,
        rules = planRules,
        notes = deps.notes,
        owner = planIdentity.owner.ifEmpty { planIdentity.token },
        token = planIdentity.token,
        onOpenApp = { pkg -> navController.navigate(Screen.AppDetail.routeForApp(pkg)) },
        // "Use this plan": select its packages on the app list and go there, so the uninstall action
        // is one tap away.
        onUsePlan = { packages ->
            appListViewModel.selectedApps.clear()
            appListViewModel.selectedApps.addAll(packages)
            navController.navigate(Screen.Main.route) { launchSingleTop = true }
        },
        // Sharing hands the plan to the clipboard as a code; the receiver's app reads it at launch
        // and opens this same page.
        onShare = { plan ->
            val code = ShareCode.of(plan.id)
            platform.copyToClipboard(ShareCode.message(plan.id, plan.title))
            platform.showMessage("分享码已复制：$code")
        },
        anchorCommentId = anchorCommentId,
        // The page knows whose plan it is; handing that back keeps this call from guessing that the
        // uploader is this install.
        onOpenUploader = { owner ->
            if (owner.isNotEmpty()) navController.navigate(Screen.Uploader.routeFor(owner))
        },
    )
}

/**
 * The app page itself, in one place: the plain route and the comment-anchored one differ by one
 * argument, so the wiring lives here once.
 *
 * @param anchorCommentId the comment to lead with, 0 for "top of the page".
 */
@Composable
private fun AppDetailRoute(
    packageName: String,
    anchorCommentId: Long,
    navController: NavHostController,
    handler: CantaHandler,
    deps: CantaAppDependencies,
    communityRepository: PlanRepository,
    appListViewModel: AppListViewModel,
    platform: CantaPlatform,
    mainViewModel: MainViewModel,
) {
    var appProfile by remember { mutableStateOf<DeviceProfile?>(null) }
    var appAuthor by remember { mutableStateOf("") }
    // Who the vote and the comments are recorded under: the number this install owns, or the token
    // itself before the backend has given it one. The full token, not a slice: "my comments" is
    // looked up by the same value, and a slice matches nothing the backend stores.
    var appVoter by remember { mutableStateOf("") }
    // The secret that proves the number, needed to delete a comment of this install.
    var appToken by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        appProfile = handler.deviceProfile()
        appAuthor = DeviceIdentity.displayName(deps.settings)
        val identity = DeviceIdentity.current(deps.settings)
        appVoter = identity.owner.ifEmpty { identity.token }
        appToken = identity.token
    }
    AppDetailScreen(
        packageName = packageName,
        app = appListViewModel.apps.firstOrNull { it.packageName == packageName },
        profile = appProfile,
        notes = deps.notes,
        author = appAuthor,
        onOpenUrl = { platform.openUrl(it) },
        onNavigateBack = { navController.navigateUp() },
        repository = communityRepository,
        voter = appVoter,
        token = appToken,
        onOpenDescriptionFixes = { pkg ->
            navController.navigate(Screen.DescriptionFix.routeForApp(pkg))
        },
        // A plan opened from this page's drawer: the same page the plans list opens, so the reader
        // can go back to the app they were reading about.
        onOpenPlan = { id -> navController.navigate(Screen.PlanDetail.routeFor(id)) },
        anchorCommentId = anchorCommentId,
        // 卸载与恢复交给主屏那一个 view model：确认框、设备规则、占用检查、生物识别和结果
        // 提示因此和列表页是同一套代码，而不是这一页自己另写一遍。
        onUninstall = { mainViewModel.uninstallPackage(packageName) },
        onReinstall = { mainViewModel.reinstallPackage(packageName) },
    )
    // 上面那两个动作的弹窗由主屏用的那份实现来画：同一个 dialog 状态，同样的措辞。
    MainDialogs(handler = handler, platform = platform, mainViewModel = mainViewModel)
}

/**
 * 通知栏那一行的正文。
 *
 * 后端的 `last_body` 对图片消息存的是 Storage 的对象路径而不是一句话（`message_send_image`
 * 把路径写进 `body`），把它原样摆进通知栏等于糊一串谁也读不懂的字符。所以"/" 开头又没有空格
 * 的那种字符串按图片处理——宁可说「[图片]」，也不假装那是一句话。
 */
private fun noticeBody(lastBody: String): String {
    val text = lastBody.trim()
    return when {
        text.isEmpty() -> "新消息"
        text.startsWith("/") && !text.contains(' ') -> "[图片]"
        // 通知栏正文两三行就是极限，长消息在通知里截断比撑成一大块更容易读；点进去是全文。
        text.length > 120 -> text.take(117) + "…"
        else -> text
    }
}
