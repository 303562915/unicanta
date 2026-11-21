package io.github.samolego.canta.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.unreadSince
import io.github.samolego.canta.data.recommend.hasNewVotes
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.badge_recommended_name
import io.github.samolego.canta.generated.resources.about_update_download_toast
import io.github.samolego.canta.generated.resources.preset_save_error
import io.github.samolego.canta.generated.resources.select_all_enabled
import io.github.samolego.canta.generated.resources.select_all_tip
import io.github.samolego.canta.generated.resources.settings
import io.github.samolego.canta.generated.resources.tab_apps
import io.github.samolego.canta.generated.resources.tab_recommend
import io.github.samolego.canta.generated.resources.tab_rules
import io.github.samolego.canta.generated.resources.tab_tweaks
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
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.navigation.Screen
import io.github.samolego.canta.ui.screen.AboutScreen
import io.github.samolego.canta.ui.screen.AppDetailScreen
import io.github.samolego.canta.ui.screen.DescriptionFixScreen
import io.github.samolego.canta.ui.screen.LogsScreen
import io.github.samolego.canta.ui.screen.MainScreen
import io.github.samolego.canta.ui.screen.MyCommentsScreen
import io.github.samolego.canta.ui.screen.OnboardingScreen
import io.github.samolego.canta.ui.screen.PlanDetailScreen
import io.github.samolego.canta.ui.screen.PresetsScreen
import io.github.samolego.canta.ui.screen.RecommendationsScreen
import io.github.samolego.canta.ui.screen.UploaderProfileScreen
import io.github.samolego.canta.ui.screen.SafetyRulesScreen
import io.github.samolego.canta.ui.screen.UpdateState
import io.github.samolego.canta.ui.screen.DOWNLOAD_PROGRESS_UNKNOWN
import io.github.samolego.canta.ui.screen.downloadReleaseWithRetries
import io.github.samolego.canta.ui.screen.UpdateChannel
import io.github.samolego.canta.ui.screen.isNewerThanInstalled
import io.github.samolego.canta.ui.screen.latestRelease
import io.github.samolego.canta.ui.screen.TweaksScreen
import io.github.samolego.canta.ui.screen.SettingsScreen
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
import io.github.samolego.canta.BuildInfo
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Taps on the version in settings that unlock "select all" for recommended apps. */
private const val SECRET_TAPS = 6

/** How long a page takes to fade in or out as it is entered or left. */
private const val PAGE_ENTER_MS = 220
private const val PAGE_EXIT_MS = 180

/** How long the page takes to travel; the fade is shorter, so the page is gone before it stops. */
private const val PAGE_SLIDE_MS = 260

/** Taps after which the remaining count is hinted. */
private const val SECRET_TAPS_HINT_AFTER = 3

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

/** The app's root composable, shared by the Android, desktop and web entry points. */
@Composable
fun CantaApp(deps: CantaAppDependencies, closeApp: () -> Unit, crashReports: CrashReportStore? = null) {
    CantaTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CantaNavigation(deps, closeApp, crashReports)
        }
    }
}

@Composable
private fun CantaNavigation(deps: CantaAppDependencies, closeApp: () -> Unit, crashReports: CrashReportStore?) {
    val handler = deps.handler
    val platform = deps.platform

    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    val appListViewModel: AppListViewModel = viewModel {
        AppListViewModel(handler, deps.bloatRepository, deps.packageTelemetry, deps.history, deps.settings)
    }
    // One client for the community pages: plans, comments, and the identity in settings.
    val communityRepository = remember { PlanRepository(HttpClient()) }
    val settingsViewModel: SettingsViewModel = viewModel {
        // The device key is what gives a reinstalled app its old identity back; the profile is what
        // tells the backend which model that identity is, even if the community page is never opened.
        SettingsViewModel(
            deps.settings,
            deps.packageTelemetry,
            communityRepository,
            platform.deviceKey(),
            { handler.deviceProfile() },
        )
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

    // A release found at start-up is downloaded without asking; installing it still asks,
    // because it replaces the running app and closes it.
    val autoInstallUpdates by settingsViewModel.autoInstallUpdates.collectAsStateWithLifecycle()
    val updateChannel by settingsViewModel.updateChannel.collectAsStateWithLifecycle()
    var autoUpdate by remember { mutableStateOf<UpdateState?>(null) }
    var autoInstallAsked by remember { mutableStateOf<UpdateState.ReadyToInstall?>(null) }
    val updateClient = remember { HttpClient() }

    LaunchedEffect(autoInstallUpdates, updateChannel) {
        autoUpdate = null
        autoInstallAsked = null
        if (!autoInstallUpdates || updateChannel == null) return@LaunchedEffect
        // F-Droid signs its own builds; downloading this repo's APK over one of them cannot work.
        if (platform.isInstalledFromFdroid()) return@LaunchedEffect
        val channel = if (updateChannel == "dev") UpdateChannel.DEVELOPMENT else UpdateChannel.STABLE
        val release = try {
            latestRelease(updateClient, channel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A silently skipped check is right here: the about page is where a failure is reported,
            // and this pass runs on every start.
            return@LaunchedEffect
        }
        if (!release.isNewerThanInstalled()) return@LaunchedEffect

        val label = if (release.buildNumber > 0) "${release.version} (dev ${release.buildNumber})" else release.version
        autoUpdate = UpdateState.Downloading(label, DOWNLOAD_PROGRESS_UNKNOWN)
        // The download runs behind whatever page is open, so a notice in the theme colour is the
        // only signal that something new is on its way. It runs alongside: the download starts at once.
        coroutineScope.launch {
            snackbarHostState.showSnackbar(getString(Res.string.about_update_download_toast, label))
        }
        val result = downloadReleaseWithRetries(
            platform = platform,
            tag = release.tag,
            buildNumber = release.buildNumber,
            onRound = { attempt ->
                if (attempt > 1) autoUpdate = UpdateState.Downloading(label, DOWNLOAD_PROGRESS_UNKNOWN)
            },
            onProgress = { percent -> autoUpdate = UpdateState.Downloading(label, percent) },
        )
        // A failed start-up download is dropped in silence: the about page is where a failure is
        // named and where the download page is offered as the way out.
        autoUpdate = when (result) {
            is UpdateDownload.Done ->
                UpdateState.ReadyToInstall(label, result.path).also { autoInstallAsked = it }
            is UpdateDownload.Failed -> null
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

    autoInstallAsked?.let { ready ->
        AlertDialog(
            onDismissRequest = { autoInstallAsked = null },
            title = { Text(stringResource(Res.string.about_install_title, ready.version)) },
            text = { Text(stringResource(Res.string.about_install_message)) },
            confirmButton = {
                TextButton(onClick = {
                    autoInstallAsked = null
                    coroutineScope.launch {
                        val failure = platform.installUpdate(ready.path)
                        if (failure == null) platform.exitApp()
                        else if (!platform.requestInstall(ready.path)) platform.showMessage(failure)
                    }
                }) { Text(stringResource(Res.string.about_install_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { autoInstallAsked = null }) {
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
    LaunchedEffect(currentRoute, countTick) {
        if (currentRoute != Screen.Main.route && currentRoute != Screen.MyComments.route) return@LaunchedEffect
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
    }

    // The vote queue's news, on the same principle as the inbox count above: a red dot on the
    // recommendations entry while somebody has voted since this install last looked at the queue.
    // What "last looked" means is stored as the queue's own shape (see `voteQueueFingerprint`), so
    // the dot is a comparison rather than a countdown, and it goes out when the vote tab is opened
    // — not when the page is left, which is what made the old counters feel like a tally.
    //
    // The queue is fetched here rather than only inside the page because the dot has to be there
    // before the page is: an entry that waits to be visited before it says there is something to
    // visit is not a notification. One call per resume, and failures leave the dot as it was.
    var newVotes by remember { mutableStateOf(false) }
    LaunchedEffect(countTick) {
        val identity = DeviceIdentity.current(deps.settings)
        val voter = identity.owner.ifEmpty { identity.token }
        val queue = communityRepository.suggestedApps(voter) ?: return@LaunchedEffect
        newVotes = hasNewVotes(deps.settings.votesSeenFlow.first(), queue)
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
            // A page arrives from the right and leaves to the right, on a fade. A page must not
            // shrink towards the middle of the window as it goes: that reads as a dialog zooming
            // away (which is what it was doing, the platform's own dialog animation) rather than
            // a page being left.
            enterTransition = {
                fadeIn(tween(PAGE_ENTER_MS)) +
                    slideInHorizontally(tween(PAGE_SLIDE_MS)) { it / 6 }
            },
            exitTransition = { fadeOut(tween(PAGE_EXIT_MS)) },
            popEnterTransition = { fadeIn(tween(PAGE_ENTER_MS)) },
            popExitTransition = {
                fadeOut(tween(PAGE_EXIT_MS)) +
                    slideOutHorizontally(tween(PAGE_SLIDE_MS)) { it / 6 }
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
                    // The dot on the bar, and the way to the page it is about: one tap from the
                    // app list to the answers somebody left under this install's comments.
                    unreadReplies = unreadReplies,
                    onOpenInbox = { navController.navigate(Screen.MyComments.route) { launchSingleTop = true } },
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
                    // The queue has been read: remember the counts it showed and put the dot out.
                    // Stored, not just cleared in memory, so a restart with no new votes stays quiet.
                    onVoteQueueSeen = { fingerprint ->
                        coroutineScope.launch {
                            deps.settings.setVotesSeen(fingerprint)
                            newVotes = false
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
                                handler.authorize(PrivilegeKind.ROOT)
                                privilegeChoices = handler.privilegeChoices()
                                appListViewModel.loadApps()
                            }
                        },
                        onOpenManager = {
                            // The manager's own app is the only place its service can be started or
                            // Canta allowed, so open it; the releases page stays as the fallback for
                            // a phone that has neither manager installed.
                            val opened = platform.openApp(STELLAR_PACKAGE) || platform.openApp(SHIZUKU_PACKAGE)
                            if (!opened) platform.openUrl(STELLAR_URL)
                        },
                        onDownload = { kind ->
                            // Root has nothing to download; its row never offers this.
                            if (kind == PrivilegeKind.SHIZUKU) platform.openUrl(STELLAR_URL)
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
                    onOpenAbout = { navController.navigate(Screen.About.route) },
                    onOpenMyComments = { navController.navigate(Screen.MyComments.route) },
                    unreadReplies = unreadReplies,
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
                UploaderProfileScreen(
                    owner = owner,
                    repository = communityRepository,
                    onOpenPlan = { id -> navController.navigate(Screen.PlanDetail.routeFor(id)) },
                    onNavigateBack = { navController.navigateUp() },
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
                // The page is about one identity: the name its comments are signed with, and the
                // number plus token that prove it when one of them is deleted.
                var myName by remember { mutableStateOf("") }
                var myIdentity by remember { mutableStateOf(Identity("")) }
                // "My plans" asks for them by identity; this phone is only the fallback for a
                // deployment that predates `plans_by_owner`.
                var myProfile by remember { mutableStateOf<DeviceProfile?>(null) }
                LaunchedEffect(Unit) {
                    myName = DeviceIdentity.displayName(deps.settings)
                    myIdentity = DeviceIdentity.current(deps.settings)
                    myProfile = handler.deviceProfile()
                }
                MyCommentsScreen(
                    repository = communityRepository,
                    author = myName,
                    owner = myIdentity.owner.ifEmpty { myIdentity.token },
                    token = myIdentity.token,
                    // Opened from the red dot: the news is what the reader came for.
                    startOnInbox = unreadReplies > 0,
                    unreadSince = lastSeenReplyId,
                    seenAt = inboxSeenAt,
                    onInboxSeen = { newest, checkedAt ->
                        // Opening the list is what clears the dot, and it stays cleared — after a
                        // restart too, and on the next count — because the backend is told to count
                        // from this instant rather than from the beginning of time.
                        coroutineScope.launch {
                            // The ids this visit came in with stay as they are: the rows the reader
                            // has not reached yet keep their marker until the page is opened again.
                            val moved = deps.notes.markInboxSeen(checkedAt)
                            deps.notes.markRepliesSeen(newest)
                            inboxSeenAt = deps.notes.inboxSeenAt.value
                            // Only when the cursor really moved. A backend that sends no
                            // `checked_at` leaves it where it was, so clearing here would hide a
                            // count that comes back on the next look — the dot blinking.
                            if (moved) unreadReplies = 0
                        }
                    },
                    onOpenPlan = { id -> navController.navigate(Screen.PlanDetail.routeFor(id)) },
                    onOpenApp = { pkg -> navController.navigate(Screen.AppDetail.routeForApp(pkg)) },
                    // A row with a comment behind it opens the page on that comment, so "somebody
                    // answered you" lands on the answer instead of the top of a long page.
                    onOpenPlanComment = { id, commentId ->
                        navController.navigate(Screen.PlanComment.routeFor(id, commentId))
                    },
                    onOpenAppComment = { pkg, commentId ->
                        navController.navigate(Screen.AppComment.routeFor(pkg, commentId))
                    },
                    profile = myProfile,
                    onNavigateBack = { navController.navigateUp() },
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
                    ),
                    onSelect = { key -> onBottomBarSelect(key, navController) },
                )
            }
        }

        ThemeToastHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp),
        )
    }
}

/** Routes that show the floating bar. Onboarding deliberately has none. */
// Every destination the bar itself leads to, so tapping an entry never lands on a page without it.
private val BOTTOM_BAR_ROUTES = setOf(
    Screen.Main.route,
    Screen.Rules.route,
    Screen.Tweaks.route,
    Screen.Recommendations.route,
    Screen.Settings.route,
    Screen.Presets.route,
)

/** Stable keys of the bar entries. */
private object BarKey {
    const val APPS = "apps"
    const val RULES = "rules"
    const val TWEAKS = "tweaks"
    const val RECOMMEND = "recommend"
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
): List<BottomBarItem> = buildList {
    // The same glyphs FlClash's own bar leads with — the grid of apps, the rules list,
    // the toolbox, the gear — plus one for the recommendations page.
    add(BottomBarItem(BarKey.APPS, FlGlyphs.dashboard, stringResource(Res.string.tab_apps), route == Screen.Main.route))
    add(BottomBarItem(BarKey.RULES, FlGlyphs.rules, stringResource(Res.string.tab_rules), route == Screen.Rules.route))
    if (showTweaks) {
        add(BottomBarItem(BarKey.TWEAKS, FlGlyphs.tools, stringResource(Res.string.tab_tweaks), route == Screen.Tweaks.route))
    }
    if (showRecommend) {
        // The dot is the vote queue's news, the way the one in Settings is the inbox's: it goes
        // out when the queue has been looked at, not when the page has been left.
        add(
            BottomBarItem(
                BarKey.RECOMMEND,
                FlGlyphs.appsList,
                stringResource(Res.string.tab_recommend),
                route == Screen.Recommendations.route,
                dot = showVoteDot,
            ),
        )
    }
    add(BottomBarItem(BarKey.SETTINGS, FlGlyphs.settings, stringResource(Res.string.settings), route == Screen.Settings.route))
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
        BarKey.SETTINGS -> navController.navigateSingleTop(Screen.Settings.route)
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
        anchorCommentId = anchorCommentId,
    )
}
