package io.github.samolego.canta.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.app.Filter
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.data.history.OperationLogStore
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.reinstall
import io.github.samolego.canta.generated.resources.uninstall
import io.github.samolego.canta.ui.AppsTab
import io.github.samolego.canta.ui.AppsType
import io.github.samolego.canta.ui.component.AppList
import io.github.samolego.canta.ui.component.AppListPermissionBanner
import io.github.samolego.canta.ui.permission.needsRequest
import io.github.samolego.canta.ui.permission.rememberAppListAccess
import io.github.samolego.canta.ui.component.CantaTopBar
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import io.github.samolego.canta.ui.component.fab.PresetEditFAB
import io.github.samolego.canta.ui.dialog.AppListPermissionDialog
import io.github.samolego.canta.ui.dialog.DeviceChooserDialog
import io.github.samolego.canta.ui.dialog.ExplainBadgesDialog
import io.github.samolego.canta.ui.dialog.RestoreSuspectsDialog
import io.github.samolego.canta.ui.dialog.ShizukuRequirementDialog
import io.github.samolego.canta.ui.dialog.UnsafeSelectionDialog
import io.github.samolego.canta.ui.dialog.FailureDialog
import io.github.samolego.canta.ui.dialog.ResumeBatchDialog
import io.github.samolego.canta.ui.dialog.SuccessDialog
import io.github.samolego.canta.ui.dialog.UninstallAppsDialog
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.MainDialog
import io.github.samolego.canta.ui.viewmodel.MainViewModel
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The app list screen: installed/records/uninstalled tabs, the action FAB and its dialogs. */
@Composable
fun MainScreen(
    handler: CantaHandler,
    platform: CantaPlatform,
    pagerState: PagerState,
    mainViewModel: MainViewModel,
    appListViewModel: AppListViewModel,
    settingsViewModel: SettingsViewModel,
    /** The uninstall/restore log shown by the middle records tab. */
    history: OperationLogStore,
    presetEditMode: Boolean,
    onPresetEditFinish: () -> Unit,
    navigateToPage: (route: String) -> Unit,
    closeApp: () -> Unit,
    enableSelectAll: Boolean,
    /** Replies to this install's comments that it has not opened: the bar's red dot. */
    unreadReplies: Int = 0,
    /** Opens the inbox from that dot. */
    onOpenInbox: () -> Unit = {},
) {
    // Reversing a record runs on the page's own scope: the dialog is already closed by then.
    val reverseScope = rememberCoroutineScope()

    // Pager state is hoisted to CantaNavigation so preset-applying knows the tab.
    val currentTab = AppsTab.entries[pagerState.currentPage]
    val selectedAppsType = currentTab.appsType

    LaunchedEffect(pagerState) {
        // Filters and selection are per tab: reset them when the user switches
        // tabs. The first emission is skipped: returning from the Presets screen
        // recomposes MainScreen, and an applied preset populates the selection
        // just before that navigation.
        snapshotFlow { pagerState.currentPage }.drop(1).collect {
            appListViewModel.selectedFilter = Filter.any
            appListViewModel.selectedApps.clear()
        }
    }

    Scaffold(
        topBar = {
            CantaTopBar(
                openBadgesInfoDialog = mainViewModel::showBadgesExplanation,
                navigateToPage = navigateToPage,
                appListViewModel = appListViewModel,
                unreadReplies = unreadReplies,
                onOpenInbox = onOpenInbox,
            )
        },
        floatingActionButton = {
            MainFab(
                visible = selectedAppsType != null && appListViewModel.selectedApps.isNotEmpty(),
                presetEditMode = presetEditMode,
                appsType = selectedAppsType,
                onPresetEditFinish = onPresetEditFinish,
                onClick = { selectedAppsType?.let { mainViewModel.applyToSelected(it) } },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AppsTabs(pagerState)
            if (appListViewModel.appListRestricted) {
                AppListPermissionBanner(
                    onOpenSettings = appListViewModel::openPackageListSettings,
                    onDismiss = appListViewModel::dismissAppListNotice,
                )
            }
            HorizontalPager(
                state = pagerState,
                // No bottom padding here: each list keeps the bar's room inside its own
                // scrolling area, so nothing is left as an empty strip at the foot.
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val tab = AppsTab.entries[page]
                if (tab == AppsTab.RECORDS) {
                    RecordsScreen(
                        history = history,
                        apps = appListViewModel.apps,
                        // A record says what was done, so a tap does the opposite of it: a removed
                        // app is put back, a restored one is removed again, without walking to
                        // another tab first.
                        onReverse = { record ->
                            reverseScope.launch {
                                val reverse = when (record.kind) {
                                    OperationKind.UNINSTALL -> OperationKind.REINSTALL
                                    OperationKind.REINSTALL -> OperationKind.UNINSTALL
                                }
                                val done = appListViewModel.applyPackages(
                                    packageNames = listOf(record.packageName),
                                    kind = reverse,
                                )
                                if (done > 0) {
                                    val action = getString(
                                        if (reverse == OperationKind.UNINSTALL) Res.string.uninstall
                                        else Res.string.reinstall
                                    )
                                    platform.showMessage("$action · ${record.label}")
                                }
                            }
                        },
                        // A whole batch is one tap on the action button, so undoing it is one tap
                        // too: the rows are the last resort, not the normal way back.
                        onRollbackBatch = { batch ->
                            reverseScope.launch {
                                val done = appListViewModel.reinstallPackages(batch.packages)
                                if (done > 0) {
                                    platform.showMessage("已恢复 $done 个")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    PullToRefreshBox(
                        isRefreshing = appListViewModel.isLoading,
                        onRefresh = appListViewModel::loadApps,
                    ) {
                        AppList(
                            appType = tab.appsType ?: AppsType.INSTALLED,
                            appListModel = appListViewModel,
                            settingsViewModel = settingsViewModel,
                            platform = platform,
                            handler = handler,
                            enableSelectAll = enableSelectAll,
                        )
                    }
                }
            }
        }
    }

    // On ROMs that expose com.android.permission.GET_INSTALLED_APPS this pops the system dialog,
    // exactly like FlClash does; everywhere else it falls back to the ROM's settings page.
    val appListAccess = rememberAppListAccess()
    var askedThisRun by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(appListAccess.supported, appListAccess.granted) {
        if (appListAccess.needsRequest && !askedThisRun) {
            askedThisRun = true
            appListAccess.request()
        }
    }

    if (appListViewModel.askForAppListPermission) {
        AppListPermissionDialog(
            canRequest = appListAccess.supported && !appListAccess.granted,
            onRequest = {
                appListViewModel.onAppListPermissionPrompted()
                appListAccess.request()
            },
            onOpenSettings = {
                appListViewModel.onAppListPermissionPrompted()
                appListViewModel.openPackageListSettings()
            },
            onDismiss = appListViewModel::onAppListPermissionPrompted,
        )
    }

    MainDialogs(handler, platform, mainViewModel)
}

@Composable
private fun MainFab(
    visible: Boolean,
    presetEditMode: Boolean,
    appsType: AppsType?,
    onPresetEditFinish: () -> Unit,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        if (presetEditMode) {
            PresetEditFAB(onPresetEditFinish = onPresetEditFinish)
            return@AnimatedVisibility
        }
        appsType ?: return@AnimatedVisibility
        FloatingActionButton(
            containerColor = when (appsType) {
                AppsType.INSTALLED -> MaterialTheme.colorScheme.error
                AppsType.UNINSTALLED -> MaterialTheme.colorScheme.primary
            },
            contentColor = when (appsType) {
                AppsType.INSTALLED -> MaterialTheme.colorScheme.onError
                AppsType.UNINSTALLED -> MaterialTheme.colorScheme.onPrimary
            },
            shape = RoundedCornerShape(32.dp),
            // Clear of the floating bar: the bar is 62 dp tall, 21 dp off the bottom edge,
            // and a press on the action must not land on it.
            modifier = Modifier.padding(end = 16.dp, top = 16.dp, bottom = 104.dp),
            onClick = onClick,
        ) {
            when (appsType) {
                AppsType.INSTALLED -> Icon(Icons.Default.Delete, stringResource(Res.string.uninstall))
                AppsType.UNINSTALLED -> Icon(Icons.Default.InstallMobile, stringResource(Res.string.reinstall))
            }
        }
    }
}

@Composable
private fun AppsTabs(pagerState: PagerState) {
    val coroutineScope = rememberCoroutineScope()
    // A segmented control rather than a tab strip: FlClash switches between two lists
    // this way, and an underline under a filled title reads as Material, not as the app.
    FlSegmentedControl(
        segments = AppsTab.entries.map { tab ->
            FlSegment(
                key = tab.name,
                label = stringResource(tab.label),
                glyph = tab.glyph,
            )
        },
        selectedIndex = pagerState.currentPage,
        // The thumb rides a swipe as it happens, and the page animation carries it on a tap.
        position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
        onSelect = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
    )
}

/** Renders [MainViewModel.dialog] (once, outside the pager) */
@Composable
private fun MainDialogs(
    handler: CantaHandler,
    platform: CantaPlatform,
    mainViewModel: MainViewModel,
) {
    when (val dialog = mainViewModel.dialog) {
        null -> {}
        is MainDialog.ConfirmUninstall -> UninstallAppsDialog(
            appCount = dialog.appCount,
            canResetToFactory = dialog.canResetToFactory,
            bootloopRisk = dialog.bootloopRisk,
            newPackages = dialog.newPackages,
            onDismiss = mainViewModel::dismissDialog,
            onAgree = mainViewModel::onUninstallConfirmed,
        )
        is MainDialog.UnsafeSelection -> UnsafeSelectionDialog(
            blocked = dialog.blocked,
            risky = dialog.risky,
            deviceLabel = dialog.deviceLabel,
            unknownCount = dialog.unknownCount,
            onDismiss = mainViewModel::dismissDialog,
            onContinue = mainViewModel::onUnsafeSelectionAccepted,
        )
        is MainDialog.RestoreSuspects -> RestoreSuspectsDialog(
            packages = dialog.packages,
            onRevert = mainViewModel::onRestoreSuspectsReverted,
            onDismiss = mainViewModel::onRestoreSuspectsDismissed,
        )
        is MainDialog.ResumeBatch -> ResumeBatchDialog(
            packages = dialog.packages,
            onResume = mainViewModel::onResumeBatchConfirmed,
            onDismiss = mainViewModel::onResumeBatchDismissed,
        )
        is MainDialog.Success -> SuccessDialog(
            platform = platform,
            count = dialog.count,
            isReinstall = dialog.isReinstall,
            onDismissRequest = mainViewModel::dismissDialog,
        )
        is MainDialog.Failure -> FailureDialog(
            attempts = dialog.attempts,
            reasons = dialog.reasons,
            isReinstall = dialog.isReinstall,
            onDismissRequest = mainViewModel::dismissDialog,
        )
        MainDialog.ShizukuSetup -> ShizukuRequirementDialog(
            onClose = mainViewModel::onShizukuSetupClosed,
            handler = handler,
            platform = platform,
        )
        MainDialog.ExplainBadges -> ExplainBadgesDialog(onDismissRequest = mainViewModel::dismissDialog)
        is MainDialog.DeviceChooser -> DeviceChooserDialog(
            devices = dialog.devices,
            isLoading = dialog.isLoading,
            discovery = handler.deviceDiscovery,
            connectionSupported = handler.connectionSupported,
            onRefresh = mainViewModel::refreshDevices,
            onSystemPick = mainViewModel::pickSystemDevice,
            onSelect = mainViewModel::selectDevice,
            onDismiss = mainViewModel::dismissDialog,
        )
    }
}
