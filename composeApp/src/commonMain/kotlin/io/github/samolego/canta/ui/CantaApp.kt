package io.github.samolego.canta.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import io.github.samolego.canta.data.app.BackupApp
import io.github.samolego.canta.data.cache.ScreenCache
import kotlinx.serialization.Serializable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
import io.github.samolego.canta.data.community.BackendHealth
import io.github.samolego.canta.data.community.CdkStore
import io.github.samolego.canta.data.community.ChatLiveSource
import io.github.samolego.canta.data.community.ChatRealtimeHub
import io.github.samolego.canta.data.community.ChatUnread
import io.github.samolego.canta.data.community.CommunityRpc
import io.github.samolego.canta.data.community.LevelFixRepository
import io.github.samolego.canta.data.community.OfficialGroups
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.PostStore
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.data.community.unreadSince
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.data.history.RestoreMode
import io.github.samolego.canta.data.imagebed.ImageBed
import io.github.samolego.canta.data.recommend.hasNewApplied
import io.github.samolego.canta.data.recommend.newAppliedSince
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.privilege_root_refused
import io.github.samolego.canta.generated.resources.privilege_root_opened
import io.github.samolego.canta.generated.resources.about_auto_install_paused
import io.github.samolego.canta.generated.resources.settings
import io.github.samolego.canta.generated.resources.tab_apps
import io.github.samolego.canta.generated.resources.tab_recommend
import io.github.samolego.canta.generated.resources.tab_rules
import io.github.samolego.canta.generated.resources.tab_tweaks
import io.github.samolego.canta.generated.resources.vpn_notice_never
import io.github.samolego.canta.generated.resources.vpn_notice_open
import io.github.samolego.canta.generated.resources.vpn_notice_switch_network
import io.github.samolego.canta.generated.resources.vpn_notice_text
import io.github.samolego.canta.ui.AppsTab
import io.github.samolego.canta.ui.AppsType
import io.github.samolego.canta.ui.dialog.CrashReportDialog
import io.github.samolego.canta.ui.dialog.PrivilegeChooserDialog
import io.github.samolego.canta.ui.dialog.restoreExportMessage
import io.github.samolego.canta.ui.component.BottomBarItem
import io.github.samolego.canta.ui.component.CollapsingBottomBar
import io.github.samolego.canta.ui.component.FloatingPillBar
import io.github.samolego.canta.ui.component.ScrollToTop
import io.github.samolego.canta.ui.component.ThemeToastHost
import io.github.samolego.canta.ui.component.dockScroll
import io.github.samolego.canta.ui.component.personAvatarUrl
import io.github.samolego.canta.ui.component.rememberDockVisibility
import io.github.samolego.canta.ui.fl.FlAccent
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlThemeMode
import io.github.samolego.canta.ui.lock.launchGateOf
import io.github.samolego.canta.ui.navigation.Screen
import io.github.samolego.canta.ui.screen.AboutScreen
import io.github.samolego.canta.ui.screen.AppDetailScreen
import io.github.samolego.canta.ui.screen.AudioWhitelistScreen
import io.github.samolego.canta.ui.screen.DescriptionFixScreen
import io.github.samolego.canta.ui.screen.LevelFixScreen
import io.github.samolego.canta.ui.screen.DeletionListScreen
import io.github.samolego.canta.ui.screen.LaunchLockScreen
import io.github.samolego.canta.ui.screen.LogsScreen
import io.github.samolego.canta.ui.screen.MainDialogs
import io.github.samolego.canta.ui.screen.MainScreen
import io.github.samolego.canta.ui.screen.PostsScreen
import io.github.samolego.canta.ui.screen.WeeklyRankScreen
import io.github.samolego.canta.ui.screen.StoreScreen
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
import io.github.samolego.canta.ui.screen.RecommendationsScreen
import io.github.samolego.canta.ui.screen.UploaderProfileScreen
import io.github.samolego.canta.ui.screen.SafetyRulesScreen
import io.github.samolego.canta.ui.screen.updateLabel
import io.github.samolego.canta.ui.update.UpdateCenter
import io.github.samolego.canta.ui.screen.UpdateChannel
import io.github.samolego.canta.ui.screen.isNewerThanInstalled
import io.github.samolego.canta.ui.screen.latestReleaseFromPage
import io.github.samolego.canta.ui.screen.TweaksScreen
import io.github.samolego.canta.ui.screen.MineScreen
import io.github.samolego.canta.ui.screen.ProfileScreen
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
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import io.github.samolego.canta.util.Identity
import io.github.samolego.canta.util.CrashReportStore
import io.github.samolego.canta.util.DeviceIdentity
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.IdentitySetup
import io.github.samolego.canta.util.SafetyRules
import io.github.samolego.canta.util.ShareCode
import io.github.samolego.canta.util.currentTimeMillis
import io.github.samolego.canta.util.TelemetryConfig
import io.github.samolego.canta.BuildInfo
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** How long one page takes to fade in while the one under it fades out. */
private const val PAGE_FADE_MS = 220

/**
 * How often the running app asks for a release again.
 *
 * Five minutes, not thirty seconds. The loop already stops itself the moment it finds a newer
 * build (or a build that already failed to install), so this number only decides how long a person
 * waits *after opening the app* before a new release is noticed — and five minutes is still far
 * inside the window where they are looking at the app.
 *
 * What a poll actually costs: [UpdateChecker.fetchRelease] asks `latest.json` /
 * `development.json` on our own GitHub Pages site first, and only falls back to the GitHub API and
 * the release HTML page when the static site has not been deployed yet. So a steady-state poll is
 * **not** a GitHub API call and cannot burn the anonymous quota — it is still a real HTTP request
 * though, for two files `pages.yml` rewrites only when a release is published, i.e. a handful of
 * times a day. Thirty seconds meant up to 120 of those an hour on every open device: 99.9% of them
 * read a byte-for-byte identical answer.
 *
 * The About page keeps its own explicit "check now", so anyone impatient has a button.
 */
private const val UPDATE_CHECK_INTERVAL_MILLIS = 300_000L

/**
 * 已经确认断了之后，问「好了没有」的退避间隔：第一次 30 秒，之后翻倍，封顶 5 分钟。
 *
 * 提示本身由事实驱动（[BackendHealth.networkTrouble]，确认探测连着两次没答上来才置 true），不是
 * 靠 `isVpnActive()` 硬判。人可能已经出去把 VPN 打开了，所以不能完全不问；
 * 但后端 503 的时候也不能催 —— 每次探测自己就是三发请求（HEAD + GET 兜底），
 * 而后端越不稳、这个 effect 被状态变化重启得越勤，就是它 2026-11-26 那天占到全部请求 33% 的原因。
 *
 * 退避到 5 分钟的代价只是一条提示晚几分钟消失；换回来的是后端在恢复期不再被自己的客户端捶。
 * 真正的恢复信号来自**任何一个真实请求**成功（打开页面、发消息、切 tab 都会 answer 一次），
 * 那条路会先调 `reportReachable()`，不等这里。
 */
private const val BACKEND_PROBE_FIRST_DELAY_MILLIS = 30_000L
private const val BACKEND_PROBE_MAX_DELAY_MILLIS = 300_000L

/**
 * 提示出现之前，确认探测要连着几次都没答上来，以及两次之间留多久。
 *
 * 单次请求失败不算断网：冷启动那几百毫秒里网络栈、DNS、代理都还没就绪，切页面时的一次超时、
 * 后台被系统压住的那一拍，都会让一个请求失败。留一秒再探一次，两次都没回音才认账——
 * 这条提示只在检测过之后才出现，不是"有请求失败就弹"。
 */
private const val BACKEND_CONFIRM_ATTEMPTS = 2
private const val BACKEND_CONFIRM_GAP_MILLIS = 1_000L

/**
 * App 退到后台之后多久问一次「有没有新消息」，用来决定要不要弹通知栏。
 *
 * 一分钟和上面那个未读计数同一个节奏：更勤只是白花电和流量，更慢则人已经走了才提醒。
 * 它只在 `appVisible` 为 false 时跑，回前台第一个 effect 就把循环取消掉。
 */
private const val CHAT_NOTICE_POLL_MILLIS = 60_000L

/**
 * 未读聊天数的最短重问间隔。
 *
 * 那个数字要让后端把**全部会话**算一遍才知道（`conversationsFor(owner).sumOf { it.unread }`），
 * 而计数用的 effect 跟着切页跑：底栏五个页面来回点，每点一次就是一份全量列表。
 * 十秒内再来沿用上一次：红点晚十秒不算错。
 */
private const val CHAT_UNREAD_TTL_MILLIS = 10_000L
/**
 * Where the manager behind Stellar/Shizuku comes from. The app points at the fork it recommends,
 * and this is also the fallback for "open the manager": shared code has no way to launch another
 * app by package name yet, so the page it is installed from is the closest thing available.
 */
private const val STELLAR_URL = "https://github.com/roro2239/Stellar/releases"

/** The manager app that holds the Shizuku service; Stellar is the fork this build recommends. */
private const val STELLAR_PACKAGE = "roro.stellar.manager"

/** 应用页停在哪个 tab，落盘用（见 [AppsTabMemory]）。 */
private const val APPS_TAB_MEMORY = "tab:apps"

/**
 * 应用页上一次停在哪一格（未卸载/记录/冻结/已卸载）。
 *
 * 单独一个模型而不是复用 `ScrollMemory`：那个记的是「滚到第几行、偏多少像素」，这里是「哪个 tab」，
 * 混用会让两件事互相覆盖。
 */
@Serializable
private data class AppsTabMemory(val index: Int = 0)

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
    // 兑换中心（码池）与社区帖子：各自一套 RPC，但身份凭据与上面两个仓储同一个 token。
    // 与项目里其它仓储一致，每个仓储自己一份 HttpClient。
    val cdkStore = remember {
        CdkStore(CommunityRpc(HttpClient(), identityToken = { DeviceIdentity.ensure(deps.settings) }))
    }
    val postStore = remember {
        PostStore(CommunityRpc(HttpClient(), identityToken = { DeviceIdentity.ensure(deps.settings) }))
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
    // 身份预热：商城、消息、我的方案这些按身份查快照的页面，第一帧就要知道「这是谁」。
    // 不预热的话，`DeviceIdentity.current()` 得等某个页面自己把设置读完，那一帧只能先画加载圈。
    // 只读一次，失败（设置还没落盘）就当没有，各页面自己的读取照旧。
    //
    // `writtenIdentity` 是这份预热的**结果**：下面那张"我的头像"要用它兜底（见 `shownAvatarUrl`），
    // 而 `LaunchedEffect` 的块里读不到外面要用的值，所以这里存进 state 而不是丢掉。
    var writtenIdentity by remember { mutableStateOf(Identity("")) }
    LaunchedEffect(deps) {
        writtenIdentity = runCatching { DeviceIdentity.current(deps.settings) }
            .getOrDefault(Identity(""))
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
        // 系统栏（状态栏 / 导航栏）的底色跟着页面背景走。`enableEdgeToEdge()` 能让内容画到系统栏
        // 底下时这一步看不出来；但小米 / 澎湃这类 ROM 在「经典导航键」（三键导航）下不让应用铺
        // 过去，系统自己给那两条填色，于是浅色页面上看着是上下各一条黑框（群里 pmkarl 报的
        // 「经典导航键模式下的黑框」/「小米14上下都有黑框」）。系统既然不给铺，就让它的颜色等于
        // 页面底色。桌面 / 网页没有系统栏，平台侧是空实现。
        val background = MaterialTheme.colorScheme.background
        SideEffect {
            deps.platform.followSystemBarColor(
                backgroundArgb = background.toArgb().toLong() and 0xFFFFFFFFFFL,
                lightBars = background.luminance() > 0.5f,
            )
        }
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
                    CantaNavigation(
                        deps,
                        closeApp,
                        crashReports,
                        communityRepository,
                        socialRepository,
                        settingsViewModel,
                        chatLive,
                        cdkStore = cdkStore,
                        postStore = postStore,
                        writtenIdentity = writtenIdentity,
                    )
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
    /**
     * CDK 兑换中心与帖子页的两个仓储。
     *
     * 它们在 [CantaApp] 那一层 `remember` 出来（各自一个 `CommunityRpc` + `HttpClient`），
     * 这里必须当参数接进来：`CantaNavigation` 是另一个函数，看不到外层的局部变量——上一版直接
     * 在这里写 `cdkStore` 就是"跨作用域引用"，编不过。
     */
    cdkStore: CdkStore,
    postStore: PostStore,
    /**
     * 预热出来的身份，给「我的头像」兜底用（见那边的 `shownAvatarUrl`）。
     *
     * 必须当参数传进来，理由与上面 [cdkStore] 那条注释一样：`writtenIdentity` 是
     * [CantaApp] 里的局部 `var`，这个函数看不到它。2026-11-26 那版就是直接在这里写
     * `writtenIdentity.owner`，于是 `Unresolved reference 'writtenIdentity'`，开发版连红两次。
     */
    writtenIdentity: Identity,
) {
    val handler = deps.handler
    val platform = deps.platform

    // 名字 + 头像设过了没有：没设过就发不了言（评论、方案评论、修正、群聊发言都读这一份）。
    // 在这里读一次，各页共用——每页自己 `collectAsState` 也行，但那样漏接一处就是一个能被发帖的漏洞。
    val setupGate by settingsViewModel.identitySetup.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    val appListViewModel: AppListViewModel = viewModel {
        AppListViewModel(
            handler,
            deps.bloatRepository,
            deps.packageTelemetry,
            deps.history,
            deps.settings,
            deps.appListSnapshot,
        )
    }
    val mainViewModel: MainViewModel = viewModel { MainViewModel(handler, platform, deps.settings, appListViewModel) }

    // Hoisted so preset-applying (below) knows which tab is showing.
    //
    // 停在哪个 tab 也落盘：冷启动再进来还是上次那一格（未卸载/记录/冻结/已卸载），
    // 不是每次都先落在第一格再自己点回去。
    val savedAppsTab = remember { ScreenCache.get<AppsTabMemory>(APPS_TAB_MEMORY)?.index ?: 0 }
    val pagerState = rememberPagerState(
        initialPage = savedAppsTab.coerceIn(0, AppsTab.entries.lastIndex),
        pageCount = { AppsTab.entries.size },
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> ScreenCache.put(APPS_TAB_MEMORY, AppsTabMemory(page)) }
    }
    // The records tab shows no app list, so a preset applied from there falls back to installed.
    val selectedAppsType = AppsTab.entries[pagerState.currentPage].appsType ?: AppsType.INSTALLED

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
    // 自己设的头像直链：个人页和「我的」那张身份卡都读它，所以在这里收一次，
    // 换完头像两处一起变。空串 = 本机没有记录（没设过，或者刚清完应用数据），
    // 这时**不能**直接画首字母——真正的地址由下面的 [shownAvatarUrl] 按身份号推出来。
    val myAvatarUrl by settingsViewModel.avatarUrl.collectAsStateWithLifecycle()
    // 这张照片的地址：存着就用存着的，存着的是空串（从没设过，或者刚清完应用数据）就按身份号
    // 从图床推出来（见 `personAvatarUrl`）。为什么非要推：后端没有头像列，图床上的对象名是固定的
    // `unicanta/头像/<身份号>.jpg`，所以**这个人有没有头像，图床自己答得上来**；设置里那条直链
    // 只是本机的一个缓存。以前只读那条缓存，清一次数据就把自己那张照片弄丢了——头像明明还在图床上。
    // 真没设过的人这条地址是 404，`UserAvatar` 会记一笔并退回名字首字，代价只是多一次询问。
    // 身份号是异步读的（`writtenIdentity` 是上面那次预热存下来的结果），读到之后这一串才有意义。
    //
    // 注意这里必须是**局部变量的普通赋值**（`remember` 里算出值），
    // 不能写成 `val x: String get() = ...`：自定义 getter 只允许出现在类/对象的成员上，
    // 写在 Composable 函数体里编译器给的是
    // 「Unresolved reference 'get'」+「Variable expected」+ 下游两处
    // 「Variable 'shownAvatarUrl' must be initialized」——2026-11-26 开发版就是这么红的。
    //
    // 用 `remember` 而不是每次重组现算：`personAvatarUrl(...)` 会拼字符串，
    // 两个输入（`myAvatarUrl`、`writtenIdentity`）都是 State，变了才该重算。
    val shownAvatarUrl: String = remember(myAvatarUrl, writtenIdentity) {
        myAvatarUrl.trim().ifEmpty { personAvatarUrl(writtenIdentity.owner) }
    }
    val updateClient = remember { HttpClient() }
    /**
     * 更新只接线一次：启动时那条自动下载和「关于」页的手点，用的是同一份实现、同一个状态。
     * 所以自动下载进来时，关于页那张卡片同样有进度条在动 —— 以前两条路各写一遍，自动那条
     * 从不把状态告诉页面，看起来像"只有手点才会更新"。
     */
    val updateCenter = remember {
        UpdateCenter(
            platform = platform,
            settings = deps.settings,
            telemetry = deps.packageTelemetry,
            client = updateClient,
            scope = coroutineScope,
        )
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
                                val who = conversation.title
                                    .ifBlank { conversation.peerName }
                                    .ifBlank { "新消息" }
                                platform.notifyChat(
                                    conversationId = conversation.id,
                                    // 被点名和群里闲聊不是一回事：标题直接写「某某 @了你」，
                                    // 正文仍是那条消息的摘要。判断在后端（last_mentions_me），
                                    // 客户端只管照说的写。
                                    title = if (conversation.lastMentionsMe) "$who @了你" else who,
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
        if (!autoInstallUpdates || updateChannel == null) return@LaunchedEffect
        // 自动下载只属于开发版通道：稳定版的人要的是「有新版本就点一下红点，别替我下」。
        // 这条守门必须在循环**外面**，而且 `updateChannel` 已经是这个 effect 的 key —— 换了通道
        // 就重新进一次，不会出现"还按上一次的通道往下跑"。
        if (updateChannel != "dev") return@LaunchedEffect
        // F-Droid signs its own builds; downloading this repo's APK over one of them cannot work.
        if (platform.isInstalledFromFdroid()) return@LaunchedEffect

        // One look at start-up misses every release published while the app stays open, so the
        // check repeats for the rest of the session — a person who keeps the app running in the
        // background is exactly the one who wants the new build without a restart. The poll reads
        // the release page, not the API: half a minute cannot spend the anonymous quota the about
        // page needs for a clean answer. A failing poll says nothing, because the next one is the
        // retry; a poll that found a build stops the loop, since asking again cannot make the APK
        // that is already here fresher.
        while (true) {
            if (appVisible) {
                // 读法只有一份（UpdateCenter.poll）：读发布页、失败吞掉；关于页也用同一份状态。
                val release = updateCenter.poll()
                if (release != null && release.isNewerThanInstalled()) {
                    val label = updateLabel(release)
                    // This exact build already failed to install on its own. Another download and
                    // another "正在安装" would be the loop this mark exists to break: say it once,
                    // point at the page that can still do it by hand, and stop asking.
                    if (label == skippedAutoInstall) {
                        // 这一版自动装过、没装上：只说一句 toast，别在底部再压一条横幅。
                        coroutineScope.launch {
                            platform.showMessage(getString(Res.string.about_auto_install_paused, label))
                        }
                        return@LaunchedEffect
                    }
                    // 下载、进度、安装、留痕全在 UpdateCenter 里：它只 toast 一句「正在下载」，
                    // 并且把状态写给关于页，所以那里也看得到进度条。
                    updateCenter.startDownload(release, auto = true)
                    return@LaunchedEffect
                }
            }
            delay(UPDATE_CHECK_INTERVAL_MILLIS)
        }
    }

    // The two optional pages: off by default, so the bar only leads to what everyone needs.
    val showTweaksTab by settingsViewModel.showTweaksTab.collectAsStateWithLifecycle()
    val showRecommendTab by settingsViewModel.showRecommendTab.collectAsStateWithLifecycle()
    val showPostsTab by settingsViewModel.showPostsTab.collectAsStateWithLifecycle()
    val showStoreTab by settingsViewModel.showStoreTab.collectAsStateWithLifecycle()

    // Null until the stored settings have loaded, so the wrong screen never flashes.
    val onboardingVersion by settingsViewModel.onboardingVersion.collectAsStateWithLifecycle()
    val storedOnboardingVersion = onboardingVersion ?: return

    // Fixed at first composition: finishing onboarding navigates explicitly.
    val startDestination = remember {
        if (storedOnboardingVersion >= CURRENT_ONBOARDING_VERSION) Screen.Main.route else Screen.Onboarding.route
    }

    // 下载完了等一句话（「安装前确认」开着才这样）：自动和手动共用这一个对话框。
    updateCenter.pendingConfirm?.let { ready ->
        AlertDialog(
            onDismissRequest = { updateCenter.postponePending() },
            title = { Text(stringResource(Res.string.about_install_title, ready.version)) },
            text = { Text(stringResource(Res.string.about_install_message)) },
            confirmButton = {
                TextButton(onClick = { updateCenter.confirmPending() }) {
                    Text(stringResource(Res.string.about_install_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { updateCenter.postponePending() }) {
                    Text(stringResource(Res.string.about_install_cancel))
                }
            },
        )
    }

    // The page under the bar drives it: scrolling down hides it, dragging back up shows it.
    val (dock, dockScroll) = rememberDockVisibility()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    // A page arrives at its top, so the bar is always there until something is scrolled.
    LaunchedEffect(currentRoute) { dock.show() }

    // 不在启动时问"有没有 VPN"。那是在猜原因：分流规则、公司网络、桌面版都没有 VPN 网卡照样通，
    // 节点挂了的 VPN 有网卡也照样不通，猜错就是每次启动都打扰一遍用得好好的人。
    // 顺序反过来：先把社区数据正常拉一遍（预加载），拉得动就什么都不说；某次请求真的没答上来，
    // 只登记成"可疑"（BackendHealth.suspicion），由下面这段确认探测连着两次都没回音才让提示出现。
    var backendProbeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { backendProbeTick++ }
    val backendTrouble by BackendHealth.networkTrouble.collectAsStateWithLifecycle()
    val backendSuspicion by BackendHealth.suspicion.collectAsStateWithLifecycle()
    // 只有"可疑"或"提示已挂着"时才花这份钱：确认断了之后按**指数退避**问"好了没有"，
    // 别的时候一次都不探（启动那一次除外，它建立的是"这台手机现在通"这个事实）。
    //
    // 为什么从「固定 60 秒」改成退避（2026-11-26 实测教训）：
    //   后端 503 的时候，这个 effect 的 key 里有 `backendTrouble` / `backendSuspicion`，
    //   而每次探测结果都会改 `BackendHealth`，状态一变就把 effect 重启一遍 ——
    //   于是「后端越不稳，探测越频繁」，把 60 秒一次的礼貌询问变成了几十秒内连发
    //   一串 `HEAD /rpc/`（实测 30 分钟内 186 次，占全部请求的 33%，而且全被 503）。
    //   后端本来就在喘，再被自己的客户端持续捶，是正反馈。
    //
    //   现在：第一次 30 秒，之后 60 / 120 / 240 / 300 封顶。一次都没答上来就继续按 300 秒
    //   等着，不去催。链路恢复的那一刻，任何一个真实请求（打开页面、发消息、切 tab）
    //   都会先 answer 一次，`reportReachable()` 由那条路触发，不靠这里死等。
    LaunchedEffect(backendTrouble, backendSuspicion, backendProbeTick, appVisible) {
        if (!appVisible) return@LaunchedEffect
        // 还没确认断：要连着两次都没答上来才算；已经确认断了：这一次只是在问"好了没有"。
        val attempts = if (backendTrouble) 1 else BACKEND_CONFIRM_ATTEMPTS
        var reachable = false
        for (attempt in 0 until attempts) {
            if (attempt > 0) delay(BACKEND_CONFIRM_GAP_MILLIS)
            reachable = communityRepository.isReachable()
            if (reachable) break
        }
        if (reachable) {
            BackendHealth.reportReachable()
            return@LaunchedEffect
        }
        // 连 attempts 次都没答上来：到这一步才认账，提示这时才出现。
        BackendHealth.confirmUnreachable()
        var wait = BACKEND_PROBE_FIRST_DELAY_MILLIS
        while (true) {
            delay(wait)
            if (communityRepository.isReachable()) {
                BackendHealth.reportReachable()
                break
            }
            wait = (wait * 2).coerceAtMost(BACKEND_PROBE_MAX_DELAY_MILLIS)
        }
    }

    val hideVpnNotice by settingsViewModel.hideVpnNotice.collectAsStateWithLifecycle()
    // 提示只在"真的连不上"时出现，链路恢复（任何一次请求答了话）就自己走开。
    // "不再提示"是持久化的用户选择；点掉一次只算这一次故障，下一次故障仍然会说话。
    var vpnNoticeDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(backendTrouble) { if (!backendTrouble) vpnNoticeDismissed = false }
    val vpnNoticeVisible = backendTrouble && !hideVpnNotice && !vpnNoticeDismissed && appVisible &&
        currentRoute != null && currentRoute != Screen.Onboarding.route

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
    //
    // 这个数只存在 [ChatUnread] 一处：消息页刷新完、聊天页读完某一条、本机标已读，都会直接把新数字
    // 推到那里；这里只在「底栏看得见数字的那几个页面」顺手问一次后端做校准。以前它是本地的一个
    // `remember`，两边各算一份，读完消息退出聊天页时还会拿十秒前那份列表把数字画回去。
    //
    // 上一次真的去问「有多少条未读聊天」的时间。
    //
    // 这个数字由一个**完整会话列表**算出来（`conversationsFor(owner).sumOf { it.unread }`），
    // 而下面那段 effect 跟着切页和每分钟一拍跑：在底栏那五个页面上来回点，每点一次就拉一份全量会话。
    // 线上实测这正是 conversations_for 的主要来源之一（切页比轮询密）。同一个页面上十秒内再来沿用
    // 上一次的数字：红点晚十秒不算错，一份全量列表却是实打实的几百毫秒与几 KB。**换页不算重算的理由**
    // ——从一个页面切过来时看到的是新页面，那一次要用真数据。
    var chatsCountedAt by remember { mutableStateOf(0L) }
    // 上一次那一趟计数是在哪个页面上跑的：切页之后不能因为「十秒内」就沿用上一页那份。
    var chatsCountedOn by remember { mutableStateOf<String?>(null) }
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
    // 三分钟一次（原来一分钟），只在带着这几个数字的页面上跑（下面那个 effect 会自己筛）。
    //
    // 为什么放到三分钟还能接受：这一拍每跑一次都要拉一份**完整会话列表**才能算出未读数
    // （`conversationsFor(owner).sumOf { it.unread }`），是所有轮询里单价最高的一趟。
    // 而红点本身不是通知——晚两三分钟亮起来，没人会以为消息丢了；切页、回前台、读完消息
    // 这三个动作都会立刻把它推准（`countTick` 与 `ChatUnread` 那几条路）。反过来，若真要
    // 「秒级红点」，就得给每个用户常开一条 WebSocket，那比轮询更容易压垮免费档实例。
    LaunchedEffect(Unit) {
        while (true) {
            delay(180_000L)
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
        if (owner.isEmpty()) {
            ChatUnread.set(0)
        } else {
            val now = currentTimeMillis()
            val samePage = chatsCountedOn == currentRoute
            if (samePage && now - chatsCountedAt < CHAT_UNREAD_TTL_MILLIS) {
                // 同一页上十秒内刚问过：沿用那一次的数字，不再为同一个红点拉一份全量会话列表。
                Unit
            } else {
                chatsCountedAt = now
                chatsCountedOn = currentRoute
                val rows = socialRepository.conversationsFor(owner)
                // 拉失败时它回的是空列表，不是「一个会话都没有」。照它写等于把消息页刚推上来的
                // 真数字擦成 0，而且同一个页面十秒内不会再问一次——只在请求真的成功时才写。
                if (socialRepository.lastError == null && !socialRepository.lastFailureWasNetwork) {
                    ChatUnread.from(rows)
                }
            }
        }
    }

    // 红点只代表一件事：**与我有关的东西进精选了**——我提的那条，或我投过票的那条
    // （见 `SuggestedApp.concernsMe`）。别人提、别人投的东西上榜跟我无关，提醒我一次我就得
    // 点进去确认一次，等于把红点变成常亮。
    //
    // 队列在这里也拉一次，是因为点必须在页面打开之前就在：要先进去看才知道有东西可看的提示，
    // 不算提示。一次 resume 一趟调用，拉失败就保持原样。
    //
    // [newVotes] 是"有没有"，[newVoteCount] 是"几条"，后者就是标记上那个数字。
    var newVotes by remember { mutableStateOf(false) }
    var newVoteCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(countTick) {
        val identity = DeviceIdentity.current(deps.settings)
        val voter = identity.owner.ifEmpty { identity.token }
        val queue = communityRepository.suggestedApps(voter) ?: return@LaunchedEffect
        val seen = deps.settings.votesSeenFlow.first()
        newVotes = hasNewApplied(seen, queue, voter)
        newVoteCount = newAppliedSince(seen, queue, voter)
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
            // Independent pages, so the transition is the one a platform uses between pages:
            // the new page fades in while the old one fades out. No page is treated as a drawer
            // sliding in over another, and none of them moves sideways.
            enterTransition = {
                fadeIn(tween(PAGE_FADE_MS))
            },
            exitTransition = {
                fadeOut(tween(PAGE_FADE_MS))
            },
            popEnterTransition = {
                fadeIn(tween(PAGE_FADE_MS))
            },
            popExitTransition = {
                fadeOut(tween(PAGE_FADE_MS))
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
                    navigateToPage = { navController.navigate(it) },
                    closeApp = closeApp,
                    // 全选不再藏在「连点版本号」后面：它默认隐藏，勾了一个应用才出现（见 AppList）。
                    enableSelectAll = true,
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
                    // 记住「选择机型」挑的那台：这一页是二级页，返回再进来不该变回全部机型。
                    onScopeRemember = { value -> settingsViewModel.savePlanScope(value) },
                    onNavigateBack = { navController.navigateUp() },
                )
            }
            composable(route = Screen.Tweaks.route) {
                TweaksScreen(
                    handler = handler,
                    onOpenAudio = { navController.navigate(Screen.Audio.route) },
                )
            }
            composable(route = Screen.Audio.route) {
                AudioWhitelistScreen(
                    handler = handler,
                    telemetry = deps.packageTelemetry,
                    cantaVersion = BuildInfo.VERSION_NAME,
                    onNavigateBack = { navController.navigateUp() },
                )
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
                    // 名字/头像没设完：推荐能看能投，但提交不了新条目。
                    setupGate = setupGate,
                )
            }
            composable(route = Screen.Posts.route) {
                // 帖子是独立一页：列表、编辑器、草稿都在里面。草稿走 deps.postDrafts
                // （按身份分开存本机），身份与推荐页那一半取的是同一套。
                var postsAuthor by remember { mutableStateOf("") }
                var postsIdentity by remember { mutableStateOf("") }
                var postsOwner by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    postsAuthor = DeviceIdentity.displayName(deps.settings)
                    val identity = DeviceIdentity.current(deps.settings)
                    postsOwner = identity.owner
                    postsIdentity = identity.owner.ifEmpty { identity.token }
                }
                PostsScreen(
                    platform = platform,
                    store = postStore,
                    drafts = deps.postDrafts,
                    reads = deps.postReads,
                    author = postsAuthor,
                    authorId = postsIdentity,
                    // 「分享方案」那一格：先列自己传过的方案，一份都没有才把最近的一批端上来，
                    // 免得一个刚装上的人点开只看到"还没有可分享的方案"。
                    loadPlans = {
                        val mine = if (postsOwner.isNotBlank()) {
                            communityRepository.plansByOwner(postsOwner)
                        } else {
                            emptyList()
                        }
                        if (mine.isNotEmpty()) mine else communityRepository.plansAll(limit = 50).orEmpty()
                    },
                    // 列表顶上那张「GitHub AI 开源周榜」卡片：数据现拉 GitHub，点开是榜单详情。
                    weeklyRank = deps.weeklyRank,
                    onOpenWeeklyRank = { navController.navigate(Screen.WeeklyRank.route) },
                    // 底栏一级页面：不给返回箭头，离开靠点底栏别的格。
                    // 名字/头像没设完：帖子能写能存草稿，但发不出去（按钮灰掉 + 说一句为什么）。
                    setupGate = setupGate,
                    // 「创建话题」不再在列表页里原地换 pane：交给独立路由，那一页不挂底栏。
                    // 新页进来时自己会开一条新的编辑器（openEditorOnEnter）；从草稿继续写
                    // 那条路仍留在本页（草稿行点一下就是页内打开，不必为它多一层返回栈）。
                    onOpenCompose = { _ -> navController.navigate(Screen.PostEditor.route) },
                )
            }
            composable(route = Screen.PostEditor.route) {
                // 写新话题：**独立一页，不挂底栏**（见 BOTTOM_BAR_ROUTES —— 它不在那张表里）。
                // 编辑器、草稿、发布仍走 PostsScreen 那一份实现，这里只改变「起步停在哪一格」：
                // openEditorOnEnter 让它一进来就是编辑态，onEditorClosed 在取消/发布成功后
                // 把这一页弹回去。写东西时底栏那一排图标既没用又容易误触走掉，所以这里不要它。
                var editorAuthor by remember { mutableStateOf("") }
                var editorIdentity by remember { mutableStateOf("") }
                var editorOwner by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    editorAuthor = DeviceIdentity.displayName(deps.settings)
                    val identity = DeviceIdentity.current(deps.settings)
                    editorOwner = identity.owner
                    editorIdentity = identity.owner.ifEmpty { identity.token }
                }
                PostsScreen(
                    platform = platform,
                    store = postStore,
                    drafts = deps.postDrafts,
                    reads = deps.postReads,
                    author = editorAuthor,
                    authorId = editorIdentity,
                    loadPlans = {
                        val mine = if (editorOwner.isNotBlank()) {
                            communityRepository.plansByOwner(editorOwner)
                        } else {
                            emptyList()
                        }
                        if (mine.isNotEmpty()) mine else communityRepository.plansAll(limit = 50).orEmpty()
                    },
                    weeklyRank = deps.weeklyRank,
                    onOpenWeeklyRank = null,
                    setupGate = setupGate,
                    openEditorOnEnter = true,
                    onEditorClosed = { navController.popBackStack() },
                )
            }
            composable(route = Screen.WeeklyRank.route) {
                // 周榜是「帖子」页顶上那张卡片点进来的二级页：进页面就去 GitHub 拉一次。
                WeeklyRankScreen(
                    repository = deps.weeklyRank,
                    onNavigateBack = { navController.navigateUp() },
                    // 点一行就打开那个仓库的 GitHub 页面；地址来自 Search 返回的 html_url。
                    onOpenRepo = { url -> if (url.isNotBlank()) platform.openUrl(url) },
                )
            }
            composable(route = Screen.Store.route) {
                // 商城：积分、积分商城、兑换中心（CDK 码池）。机场页只管投票，钱的事都在这。
                //
                // 身份用启动时预热过的那一份做初值：这一页按身份查快照，`LaunchedEffect` 读完设置
                // 之前 `authorId` 一直是空串，那一帧就只能是加载圈（缓存明明就在盘上）。
                val bootIdentity = DeviceIdentity.remembered()
                var storeAuthor by remember { mutableStateOf("") }
                var storeIdentity by remember {
                    mutableStateOf(bootIdentity?.let { it.owner.ifEmpty { it.token } }.orEmpty())
                }
                LaunchedEffect(Unit) {
                    storeAuthor = DeviceIdentity.displayName(deps.settings)
                    val identity = DeviceIdentity.current(deps.settings)
                    storeIdentity = identity.owner.ifEmpty { identity.token }
                }
                StoreScreen(
                    platform = platform,
                    repository = communityRepository,
                    cdk = cdkStore,
                    author = storeAuthor,
                    authorId = storeIdentity,
                    // 名字/头像没设完：能看能买能兑换，但上不了架。
                    setupGate = setupGate,
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
                    // 连点版本号那个隐藏手势已经不需要了：全选现在默认隐藏、勾一个应用就出现，
                    // 所以这里不再传 onVersionTap（默认空实现），关于页照样只画版本号。
                    // 更新的那份实现与状态：这一页只画它，不再自己发请求，所以自动下载也有动画。
                    updateCenter = updateCenter,
                )
            }
            composable(route = Screen.Theme.route) {
                ThemeScreen(
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { navController.navigateUp() },
                )
            }
            composable(route = Screen.DeletionList.route) {
                DeletionListScreen(
                    deletionList = deps.deletionList,
                    platform = platform,
                    onNavigateBack = { navController.navigateUp() },
                    // 恢复走「未安装 / 冻结」页那同一条路：一个包装回来是 REINSTALL，冻回去的启用是 ENABLE。
                    onRestore = { packageName, kind ->
                        appListViewModel.applyPackages(
                            packageNames = listOf(packageName),
                            kind = kind,
                        )
                    },
                    // 点备份那一刻的两份名单：卸载名单由记录现算，冻结名单问应用列表（它才知道谁被停用）。
                    currentSnapshot = {
                        val removed = deps.deletionList.currentState().uninstall
                        val byPackage = appListViewModel.apps.associateBy { it.packageName }
                        val frozen = appListViewModel.apps
                            .filter { it.isDisabled }
                            .map { app ->
                                BackupApp(
                                    packageName = app.packageName,
                                    label = app.name,
                                    versionName = app.versionName,
                                    isSystem = app.isSystemApp,
                                )
                            }
                        val uninstall = removed.map { pkg ->
                            val app = byPackage[pkg]
                            BackupApp(
                                packageName = pkg,
                                label = app?.name.orEmpty(),
                                versionName = app?.versionName.orEmpty(),
                                isSystem = app?.isSystemApp == true,
                            )
                        }
                        uninstall to frozen
                    },
                    deviceLabel = { appListViewModel.deviceLabel() },
                )
            }
            composable(route = Screen.Mine.route) {
                MineScreen(
                    settingsViewModel = settingsViewModel,
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenMyComments = { navController.navigate(Screen.MyComments.route) },
                    onOpenAbout = { navController.navigate(Screen.About.route) },
                    // 身份卡点一下进个人主页：改名与换头像都在那一页。
                    onOpenProfile = { navController.navigate(Screen.Profile.route) },
                    // 兜底过的地址（存着为空就按身份号推），清完应用数据这张卡也不会退回首字。
                    avatarUrl = shownAvatarUrl,
                    unreadReplies = unreadReplies,
                    // Same dot as the bar's: the about page behind this row is where updates live.
                    updateAvailable = updateAvailable,
                )
            }
            composable(route = Screen.Profile.route) {
                // 身份号要异步读（DeviceIdentity 是 suspend 的），名字与头像直链则跟着设置走；
                // 两个计数由这里用现成的仓储拉一次，拉不到就传 null，页面不画那一组。
                var profileIdentity by remember { mutableStateOf(Identity("")) }
                var profilePlanCount by remember { mutableStateOf<Int?>(null) }
                var profileCommentCount by remember { mutableStateOf<Int?>(null) }
                val myName by settingsViewModel.userName.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    val identity = DeviceIdentity.current(deps.settings)
                    profileIdentity = identity
                    val owner = identity.owner
                    if (owner.isNotEmpty()) {
                        // 两次读各自成败：一次失败不该把另一个数字也变成 null。
                        profilePlanCount = runCatching {
                            communityRepository.plansByOwner(owner).size
                        }.getOrNull()
                        // 评论是分页的（第一页 + 有没有下一页）：这里只取第一页的条数，
                        // hasMore 不影响显示，页面那一行只是"大概写了多少"。
                        profileCommentCount = runCatching {
                            communityRepository.myCommentsPage(owner, identity.token).items.size
                        }.getOrNull()
                    }
                }
                ProfileScreen(
                    // 头像上传走图床，那条路在 SocialRepository 上（PlanRepository 没有上传口）。
                    repository = socialRepository,
                    owner = profileIdentity.owner,
                    userName = myName,
                    // 这张照片在「我的」页和这个头像上是同一张，兜底也在同一处做：
                    // 存着的直链为空时按身份号推图床地址（理由见上面那道闸）。
                    avatarUrl = shownAvatarUrl,
                    planCount = profilePlanCount,
                    commentCount = profileCommentCount,
                    onSaveName = { name -> settingsViewModel.saveUserName(name) },
                    // 上传成功就在这儿落盘：头像直链存进设置，两处头像一起换成新的。
                    onAvatarSaved = { url -> settingsViewModel.saveAvatarUrl(url) },
                    // 相册与压缩都来自平台层，和聊天发图那两处接的是同一个实现。
                    pickImage = { platform.pickImage() },
                    // 预览用的是聊天那套压缩（长边 ≤2048、≤400KB）：裁剪框摆在这张缩图上，原图太大会 OOM。
                    compressImage = { bytes -> platform.compressImage(bytes, ImageBed.AVATAR_PREVIEW_BYTES) },
                    // 真正落地的是头像专用那一条：裁方 + 缩到 256 + 压到 200KB 以内。
                    compressAvatar = { bytes, crop ->
                        platform.compressAvatar(bytes, crop, ImageBed.AVATAR_MAX_BYTES)
                    },
                    onNavigateBack = { navController.navigateUp() },
                    // 没设名字/头像就发不了言，这一页是设它们的地方。
                    setupGate = setupGate,
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
                    onOpenDeletionList = { navController.navigate(Screen.DeletionList.route) },
                    // 救急用的文件：删掉的包还在系统分区里，恢复命令得在电脑上跑。
                    // 生成的批处理交给分享面板（发到电脑），打不开面板的构建就留在下载目录。
                    onExportRestoreScript = { mode ->
                        coroutineScope.launch {
                            val exported = deps.history.shareRestoreScript(mode)
                            platform.showMessage(restoreExportMessage(exported))
                        }
                    },
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
                    setupGate = setupGate,
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
                    setupGate = setupGate,
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
                    setupGate = setupGate,
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
                    setupGate = setupGate,
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
                    // 没设名字/头像就发不了新修正：页面据此把发送键灰掉。
                    setupGate = setupGate,
                    onNavigateBack = { navController.navigateUp() },
                )
            }
            composable(route = Screen.LevelFix.route) { entry ->
                val packageName = entry.arguments
                    ?.let { args ->
                        runCatching {
                            args.read { if (contains("package")) getString("package") else null }
                        }.getOrNull()
                    }.orEmpty()
                var levelAuthor by remember { mutableStateOf("") }
                var levelIdentity by remember { mutableStateOf("") }
                LaunchedEffect(packageName) {
                    levelAuthor = DeviceIdentity.displayName(deps.settings)
                    val identity = DeviceIdentity.current(deps.settings)
                    levelIdentity = identity.owner.ifEmpty { identity.token }
                }
                val levelApp = appListViewModel.apps.firstOrNull { it.packageName == packageName }
                LevelFixScreen(
                    packageName = packageName,
                    appName = levelApp?.name ?: packageName,
                    // 名单现在给的级别：从列表里那份已经叠好修正的徽标读，界面和列表页一致。
                    publishedBadge = levelApp?.badgeInfo,
                    repository = LevelFixRepository(communityRepository),
                    author = levelAuthor,
                    authorId = levelIdentity,
                    // 没设名字/头像就提不了新修正（投票仍然可以）：发送键据此灰掉。
                    setupGate = setupGate,
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
                // 消息页上本人删掉的那几行（官方群名 / 会话号）：落盘，所以断网、重进、重启都一样不显示。
                val hiddenMessageRows by settingsViewModel.hiddenMessageRows.collectAsStateWithLifecycle()
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
                    // 消息页已经把这一行是群还是私聊告诉我们了：群走 `chat-group` 那条路由，
                    // 聊天页开门那一刻就按群画（别人的头像、名字、群名都在），不用再等一趟
                    // `conversations_for`。私聊照旧走 `chat/{id}`。
                    onOpenChat = { id, isGroup ->
                        navController.navigate(
                            if (isGroup) Screen.ChatGroup.routeFor(id) else Screen.Chat.routeFor(id),
                        )
                    },
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
                    hiddenRows = hiddenMessageRows,
                    onHideRow = { key, hidden -> settingsViewModel.saveMessageRowHidden(key, hidden) },
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
            // 同一套聊天页挂两条路由（见 [Screen.ChatGroup]）：`chat/{id}` 是「不知道是不是群」的老入口，
            // `chat-group/{id}` 是消息页/折叠群页/官方群把「这是群」这个结论一起带进来的入口。
            // 两条共用下面这一整段，只有 `knownGroup` 一个初值不同——群身份一旦定下就不再等第二个请求。
            fun chatRoute(route: String, knownGroup: Boolean) = composable(
                route = route,
                // 两条路由共用这一个 builder：`popUpTo` / `launchSingleTop` 之类是按条目算的，
                // 不在这里额外声明，行为和原来那条 `composable(route = Screen.Chat.route)` 一致。
            ) { entry ->
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
                // 知道是群就直接按群开始：列表里那一行本来就是群，没必要先当私聊画一帧。
                // 「知道是群就不降级」：后面问回来的那一行只有在**确认是群**时才把它写成 true，
                // 问回来的是别的（拉取失败、列表里没有这一行）就保持原样——把群误画成私聊的代价是
                // 别人的头像和名字全没了，比多留一帧群样式贵得多。
                var chatIsGroup by remember { mutableStateOf(knownGroup) }
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
                        // 没名字的群（早期测试留下的空群）在消息页不列，但人可能从退回来的路由
                        // 直接进到这里：给顶栏一句话，别让标题空着。
                        // 用 `?:` 收口而不是在链上继续挂 `?.`：`?.let` 之后缺省值是 null，整条链的类型
                        // 就是 String?，发进 `chatTitle: String` 那一侧就报 nullable receiver。
                        chatTitle = opened
                            ?.let { row -> row.title.ifBlank { row.peerName.ifBlank { row.peer } } }
                            .orEmpty()
                            .ifBlank { if (opened?.isGroup == true) OfficialGroups.UNNAMED_GROUP_LABEL else "" }
                        chatIsGroup = chatIsGroup || opened?.isGroup == true
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
                    // 超 2.5MB 的图先压：平台有解码能力（Android）就压，没有就回答 null，
                    // 页面据此说"换一张"，不会把原图硬塞上去。
                    compressImage = { bytes -> platform.compressImage(bytes, ImageBed.MAX_BYTES) },
                    // 草稿：进来时把这一条上次没发完的话填回去，改动由页面防抖后写回。
                    initialDraft = chatDrafts.firstOrNull { it.first == conversationId }?.second.orEmpty(),
                    onDraftPersist = { text -> settingsViewModel.saveChatDraft(conversationId, text) },
                    // 拉黑只对私聊有意义：群里没有"那个人"，群不受本机名单影响。
                    blocked = !chatIsGroup && chatPeer.isNotEmpty() && chatPeer in chatBlockedPeers,
                    // 名字/头像没设过：发不了言，输入栏整条换成一行说明。
                    setupGate = setupGate,
                    // 实时：对方一发就到，不再等轮询那一下。
                    liveSource = chatLive,
                )
            }
            // 两条路由共用上面那一段（见 [Screen.ChatGroup]）：`chat/{id}` 是「不知道是不是群」的老入口，
            // `chat-group/{id}` 是消息页/折叠群页/官方群把「这是群」这个结论一起带进来的入口。
            chatRoute(route = Screen.Chat.route, knownGroup = false)
            chatRoute(route = Screen.ChatGroup.route, knownGroup = true)
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
                    // 换群头像：和个人主页接的是同一套平台能力（相册、预览压缩、按裁剪框裁方并压到 200KB 内）。
                    pickImage = { platform.pickImage() },
                    compressPreview = { bytes -> platform.compressImage(bytes, ImageBed.AVATAR_PREVIEW_BYTES) },
                    compressAvatar = { bytes, crop ->
                        platform.compressAvatar(bytes, crop, ImageBed.AVATAR_MAX_BYTES)
                    },
                )
            }
            composable(route = Screen.Groups.route) {
                var groupsIdentity by remember { mutableStateOf(Identity("")) }
                LaunchedEffect(Unit) { groupsIdentity = DeviceIdentity.current(deps.settings) }
                GroupsScreen(
                    repository = socialRepository,
                    owner = groupsIdentity.owner.ifEmpty { groupsIdentity.token },
                    // 「折叠的群聊」页里全是群：走同一条带群身份的路由。
                    onOpenChat = { id, isGroup ->
                        navController.navigate(
                            if (isGroup) Screen.ChatGroup.routeFor(id) else Screen.Chat.routeFor(id),
                        )
                    },
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
                        showPosts = showPostsTab,
                        showStore = showStoreTab,
                        // Nothing to point at while the queue itself is open: that page is the dot.
                        showVoteDot = newVotes && currentRoute != Screen.Recommendations.route,
                        showVoteCount = newVoteCount,
                        // The way to a newer build: 底栏「设置」→ 关于 → 检查更新, marked at its first step.
                        showUpdateDot = updateAvailable,
                        // What the bell in the top bar counts, on the entry that now leads to the
                        // page the bell opens.
                        showMessageCount = unreadReplies + ChatUnread.total,
                    ),
                    onSelect = { key -> onBottomBarSelect(key, navController) },
                    // 双击当前那一格 → 整页回顶部（页面各自用 ScrollToTopEffect 听这个信号）。
                    onReselect = { ScrollToTop.ping() },
                )
            }
        }

        // The reminder covers every page: it is about the request that did not come back, not about
        // whichever page happens to be open. It appears only after a real failure — see the
        // BackendHealth check above — so a working network never sees it.
        if (vpnNoticeVisible) {
            VpnNoticeCard(
                text = stringResource(Res.string.vpn_notice_text),
                openLabel = stringResource(Res.string.vpn_notice_open),
                switchLabel = stringResource(Res.string.vpn_notice_switch_network),
                neverLabel = stringResource(Res.string.vpn_notice_never),
                onOpenProxy = {
                    if (platform.openProxySettings()) vpnNoticeDismissed = true
                },
                onSwitchNetwork = {
                    if (platform.openNetworkSettings()) vpnNoticeDismissed = true
                },
                onNeverShow = {
                    vpnNoticeDismissed = true
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
 * 连不上后端时的提示：一句说清现在发生了什么，和三个真正有用的动作——开代理、换网络、别再提醒。
 *
 * 它故意不是对话框：它浮在页面上，断网时应用列表照样能用，这也正是"不开也能用"的意思。
 * 触发条件是**请求真的没答上来**，不是"没看到 VPN 网卡"，所以它出现时说的就是事实。
 */
@Composable
private fun VpnNoticeCard(
    text: String,
    openLabel: String,
    switchLabel: String,
    neverLabel: String,
    onOpenProxy: () -> Unit,
    onSwitchNetwork: () -> Unit,
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
                // 另一个同样常见的解法：不是代理的问题，是 Wi-Fi 本身不通（校园网、酒店网、
                // 家里路由器），这时要动的是流量/Wi-Fi 开关，不是 VPN 客户端。
                TextButton(onClick = onSwitchNetwork) {
                    Text(
                        text = switchLabel,
                        style = FlText.caption1,
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
    // 帖子是底栏上的一格，跟上面几页一样：点进来底栏还在，顶栏也不画返回箭头。
    Screen.Posts.route,
    Screen.Store.route,
    Screen.Messages.route,
    // 底栏最后一格是「我的」；设置页从它里面进，设置页本身不是底栏的一格。
    Screen.Mine.route,
)

/** Stable keys of the bar entries. */
private object BarKey {
    const val APPS = "apps"
    const val RULES = "rules"
    const val POSTS = "posts"
    const val STORE = "store"
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
    /** 帖子页要不要占一格：出厂就在（见 [showPostsTab][io.github.samolego.canta.data.proto.AppSettingsProto]），设置里可以关掉。 */
    showPosts: Boolean = true,
    /** 商城要不要占一格：和帖子一样出厂就在，设置里可以关掉。 */
    showStore: Boolean = true,
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
    if (showPosts) {
        // 帖子：自己的列表和自己的编辑器，和上面三页不是一类，所以默认不上底栏，
        // 设置里点亮那一格才出现（见 AppBarSlotsCard 的第二行）。
        add(
            BottomBarItem(
                BarKey.POSTS,
                FlGlyphs.resources,
                "帖子",
                route == Screen.Posts.route,
            ),
        )
    }
    if (showStore) {
        // 商城：积分、积分商城、兑换中心。机场榜单那一页不再兼着放钱的事。
        add(
            BottomBarItem(
                BarKey.STORE,
                FlGlyphs.tools,
                "商城",
                route == Screen.Store.route,
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
            // 这一格是「我的」：身份、我上传过的方案、以及进设置那一行。设置页本身**不**占底栏，
            // 从「我的」里进——用户要的是"别在右上角菜单里翻设置"，不是"设置顶掉我的"。
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
        BarKey.POSTS -> navController.navigateSingleTop(Screen.Posts.route)
        BarKey.STORE -> navController.navigateSingleTop(Screen.Store.route)
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
    /** 名字与头像设过了没有：没设过这一页的评论发不出去。 */
    setupGate: IdentitySetup = IdentitySetup.UNSET,
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
        // and opens this same page. The device line rides along so the message says which phone the
        // plan was proven on, which is the part that makes the code worth opening.
        onShare = { plan ->
            val code = ShareCode.of(plan.id)
            platform.copyToClipboard(ShareCode.message(plan.id, plan.title, plan.deviceLabel))
            platform.showMessage("分享码已复制：$code")
        },
        anchorCommentId = anchorCommentId,
        // The page knows whose plan it is; handing that back keeps this call from guessing that the
        // uploader is this install.
        onOpenUploader = { owner ->
            if (owner.isNotEmpty()) navController.navigate(Screen.Uploader.routeFor(owner))
        },
        // 方案详情里那些「点一下说一句」的小提示（比如卡片角上「卸得太多」的披带）：走平台的
        // toast，和别处的提示同一套。
        onMessage = { line -> platform.showMessage(line) },
        setupGate = setupGate,
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
    /** 名字与头像设过了没有：没设过这一页的评论发不出去。 */
    setupGate: IdentitySetup = IdentitySetup.UNSET,
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
        onOpenLevelFixes = { pkg ->
            navController.navigate(Screen.LevelFix.routeForApp(pkg))
        },
        // A plan opened from this page's drawer: the same page the plans list opens, so the reader
        // can go back to the app they were reading about.
        onOpenPlan = { id -> navController.navigate(Screen.PlanDetail.routeFor(id)) },
        anchorCommentId = anchorCommentId,
        // 卸载与恢复交给主屏那一个 view model：确认框、设备规则、占用检查、生物识别和结果
        // 提示因此和列表页是同一套代码，而不是这一页自己另写一遍。
        onUninstall = { mainViewModel.uninstallPackage(packageName) },
        onReinstall = { mainViewModel.reinstallPackage(packageName) },
        // 名字/头像没设过：这一页收不了评论，发送键灰掉并说一句为什么。
        setupGate = setupGate,
    )
    // 上面那两个动作的弹窗由主屏用的那份实现来画：同一个 dialog 状态，同样的措辞。
    MainDialogs(
        handler = handler,
        platform = platform,
        mainViewModel = mainViewModel,
        history = deps.history,
    )
}

/**
 * 通知栏那一行的正文。
 *
 * 后端的 `last_body` 对图片消息存的是图片地址而不是一句话（`message_send_image` 把地址写进 `body`）：
 * 新消息是图床的 `https://.../file/<id>`，老消息是 `chat-images` 的对象路径。把它原样摆进通知栏
 * 等于糊一串谁也读不懂的字符，所以两种形状都按图片处理、只写「[图片]」：
 * "/" 开头又没有空格的老路径，以及 http(s) 且带 `/file/` 的图床直链（人发的一句裸链接不会长这样，
 * 所以这一条不会把分享的网址误判成图片）。
 */
private fun noticeBody(lastBody: String): String {
    val text = lastBody.trim()
    val looksLikeImageUrl = (text.startsWith("https://") || text.startsWith("http://")) &&
        text.contains("/file/") && !text.contains(' ')
    return when {
        text.isEmpty() -> "新消息"
        text.startsWith("/") && !text.contains(' ') -> "[图片]"
        looksLikeImageUrl -> "[图片]"
        // 通知栏正文两三行就是极限，长消息在通知里截断比撑成一大块更容易读；点进去是全文。
        text.length > 120 -> text.take(117) + "…"
        else -> text
    }
}