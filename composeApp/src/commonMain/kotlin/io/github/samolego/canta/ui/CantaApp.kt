package io.github.samolego.canta.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
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
import io.github.samolego.canta.core.DeviceUser
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.badge_recommended_name
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
import io.github.samolego.canta.ui.component.BottomBarItem
import io.github.samolego.canta.ui.component.CollapsingBottomBar
import io.github.samolego.canta.ui.component.FloatingPillBar
import io.github.samolego.canta.ui.component.dockScroll
import io.github.samolego.canta.ui.component.rememberDockVisibility
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.navigation.Screen
import io.github.samolego.canta.ui.screen.AboutScreen
import io.github.samolego.canta.ui.screen.AppDetailScreen
import io.github.samolego.canta.ui.screen.LogsScreen
import io.github.samolego.canta.ui.screen.MainScreen
import io.github.samolego.canta.ui.screen.MyCommentsScreen
import io.github.samolego.canta.ui.screen.OnboardingScreen
import io.github.samolego.canta.ui.screen.PlanDetailScreen
import io.github.samolego.canta.ui.screen.PresetsScreen
import io.github.samolego.canta.ui.screen.RecommendationsScreen
import io.github.samolego.canta.ui.screen.SafetyRulesScreen
import io.github.samolego.canta.ui.screen.UpdateState
import io.github.samolego.canta.ui.screen.downloadRelease
import io.github.samolego.canta.ui.screen.isNewerVersion
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Taps on the version in settings that unlock "select all" for recommended apps. */
private const val SECRET_TAPS = 6

/** Taps after which the remaining count is hinted. */
private const val SECRET_TAPS_HINT_AFTER = 3

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
        AppListViewModel(handler, deps.bloatRepository, deps.packageTelemetry, deps.history)
    }
    // One client for the community pages: plans, comments, and the identity in settings.
    val communityRepository = remember { PlanRepository(HttpClient()) }
    val settingsViewModel: SettingsViewModel = viewModel {
        // The device key is what gives a reinstalled app its old identity back.
        SettingsViewModel(deps.settings, deps.packageTelemetry, communityRepository, platform.deviceKey())
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
    var autoUpdate by remember { mutableStateOf<UpdateState?>(null) }
    var autoInstallAsked by remember { mutableStateOf<UpdateState.ReadyToInstall?>(null) }
    val updateClient = remember { HttpClient() }

    LaunchedEffect(autoInstallUpdates) {
        if (!autoInstallUpdates) return@LaunchedEffect
        val tag = runCatching { latestRelease(updateClient) }.getOrNull() ?: return@LaunchedEffect
        if (!isNewerVersion(tag, BuildInfo.VERSION_NAME)) return@LaunchedEffect

        autoUpdate = UpdateState.Downloading(tag, 0)
        val path = downloadRelease(platform, tag) { percent ->
            autoUpdate = UpdateState.Downloading(tag, percent)
        }
        autoUpdate = if (path == null) {
            null
        } else {
            UpdateState.ReadyToInstall(tag, path).also { autoInstallAsked = it }
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
                        if (failure == null) platform.exitApp() else platform.showMessage(failure)
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

    // A share code pasted into the clipboard before the app was opened: read it once, then open
    // that plan as soon as the app itself is on screen (not over onboarding).
    var sharedPlan by remember { mutableStateOf<Long?>(null) }
    var shareChecked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!shareChecked) {
            shareChecked = true
            sharedPlan = ShareCode.parse(platform.readClipboard())
        }
    }
    LaunchedEffect(sharedPlan, currentRoute) {
        val planId = sharedPlan ?: return@LaunchedEffect
        if (currentRoute != Screen.Main.route) return@LaunchedEffect
        sharedPlan = null
        platform.showMessage("识别到分享码，正在打开方案 #$planId")
        navController.navigate(Screen.PlanDetail.routeFor(planId))
    }

    Box(modifier = Modifier.fillMaxSize().dockScroll(dockScroll)) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    platform = platform,
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
                RecommendationsScreen(
                    platform = platform,
                    onNavigateBack = { navController.navigateUp() },
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
                var planProfile by remember { mutableStateOf<DeviceProfile?>(null) }
                // Comments are posted under this install's own code: no account, no login.
                var planAuthor by remember { mutableStateOf("") }
                // The identity number and its secret: what a comment of this install is deleted with.
                var planIdentity by remember { mutableStateOf(Identity("")) }
                // The plan's packages are shown with their icons and their device rules, the same
                // way the app list shows them.
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
                    // "Use this plan": select its packages on the app list and go there, so the
                    // uninstall action is one tap away.
                    onUsePlan = { packages ->
                        appListViewModel.selectedApps.clear()
                        appListViewModel.selectedApps.addAll(packages)
                        navController.navigate(Screen.Main.route) { launchSingleTop = true }
                    },
                    // Sharing hands the plan to the clipboard as a code; the receiver's app reads
                    // it at launch and opens this same page.
                    onShare = { plan ->
                        val code = ShareCode.of(plan.id)
                        platform.copyToClipboard(ShareCode.message(plan.id, plan.title))
                        platform.showMessage("分享码已复制：$code")
                    },
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
                var appProfile by remember { mutableStateOf<DeviceProfile?>(null) }
                var appAuthor by remember { mutableStateOf("") }
                // Who the vote and the comments are recorded under: the number this install owns,
                // or the token itself before the backend has given it one. The full token, not a
                // slice: "my comments" is looked up by the same value, and a slice matches nothing
                // the backend stores.
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
                    onNavigateBack = { navController.navigateUp() },
                    repository = communityRepository,
                    voter = appVoter,
                    token = appToken,
                )
            }
            composable(route = Screen.MyComments.route) {
                // The page is about one identity: the name its comments are signed with, and the
                // number plus token that prove it when one of them is deleted.
                var myName by remember { mutableStateOf("") }
                var myIdentity by remember { mutableStateOf(Identity("")) }
                LaunchedEffect(Unit) {
                    myName = DeviceIdentity.displayName(deps.settings)
                    myIdentity = DeviceIdentity.current(deps.settings)
                }
                MyCommentsScreen(
                    repository = communityRepository,
                    author = myName,
                    owner = myIdentity.owner.ifEmpty { myIdentity.token },
                    token = myIdentity.token,
                    onOpenPlan = { id -> navController.navigate(Screen.PlanDetail.routeFor(id)) },
                    onOpenApp = { pkg -> navController.navigate(Screen.AppDetail.routeForApp(pkg)) },
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
                    items = bottomBarItems(route = currentRoute, showTweaks = showTweaksTab, showRecommend = showRecommendTab),
                    onSelect = { key -> onBottomBarSelect(key, navController) },
                )
            }
        }

        SnackbarHost(
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
): List<BottomBarItem> = buildList {
    // The same glyphs FlClash's own bar leads with — the grid of apps, the rules list,
    // the toolbox, the gear — plus one for the recommendations page.
    add(BottomBarItem(BarKey.APPS, FlGlyphs.dashboard, stringResource(Res.string.tab_apps), route == Screen.Main.route))
    add(BottomBarItem(BarKey.RULES, FlGlyphs.rules, stringResource(Res.string.tab_rules), route == Screen.Rules.route))
    if (showTweaks) {
        add(BottomBarItem(BarKey.TWEAKS, FlGlyphs.tools, stringResource(Res.string.tab_tweaks), route == Screen.Tweaks.route))
    }
    if (showRecommend) {
        add(BottomBarItem(BarKey.RECOMMEND, FlGlyphs.appsList, stringResource(Res.string.tab_recommend), route == Screen.Recommendations.route))
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
