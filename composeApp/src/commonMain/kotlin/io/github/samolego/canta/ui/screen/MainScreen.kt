package io.github.samolego.canta.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.data.history.OperationLogStore
import io.github.samolego.canta.data.history.RestoreMode
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.records_action_disable
import io.github.samolego.canta.generated.resources.records_action_enable
import io.github.samolego.canta.generated.resources.reinstall
import io.github.samolego.canta.generated.resources.uninstall
import io.github.samolego.canta.ui.AppsTab
import io.github.samolego.canta.ui.AppsType
import io.github.samolego.canta.ui.component.AppList
import io.github.samolego.canta.ui.component.AppListPermissionBanner
import io.github.samolego.canta.ui.permission.needsRequest
import io.github.samolego.canta.ui.permission.rememberAppListAccess
import io.github.samolego.canta.ui.component.CantaTopBar
import io.github.samolego.canta.ui.component.dismissInputOnTap
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import io.github.samolego.canta.ui.navigation.Screen
import io.github.samolego.canta.ui.dialog.AppListPermissionDialog
import io.github.samolego.canta.ui.dialog.DeviceChooserDialog
import io.github.samolego.canta.ui.dialog.ExplainBadgesDialog
import io.github.samolego.canta.ui.dialog.RestoreSuspectsDialog
import io.github.samolego.canta.ui.dialog.ShizukuRequirementDialog
import io.github.samolego.canta.ui.dialog.UnsafeSelectionDialog
import io.github.samolego.canta.ui.dialog.FailureDialog
import io.github.samolego.canta.ui.dialog.ResumeBatchDialog
import io.github.samolego.canta.ui.dialog.RestoreExportDialog
import io.github.samolego.canta.ui.dialog.SuccessDialog
import io.github.samolego.canta.ui.dialog.restoreExportMessage
import io.github.samolego.canta.ui.dialog.UninstallAppsDialog
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.MainDialog
import io.github.samolego.canta.ui.viewmodel.MainViewModel
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The app list screen: installed/records/uninstalled/disabled tabs, the action FAB and its dialogs. */
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
    navigateToPage: (route: String) -> Unit,
    closeApp: () -> Unit,
    enableSelectAll: Boolean,
) {
    // Reversing a record runs on the page's own scope: the dialog is already closed by then.
    val reverseScope = rememberCoroutineScope()

    // The records tab's switches: whether the log is dropped the next time the app opens after a
    // reboot, and whether a tap on a row opens the app page instead of reversing it.
    val clearRecordsAfterBoot by settingsViewModel.clearRecordsAfterBoot.collectAsStateWithLifecycle()
    val recordTapOpensDetail by settingsViewModel.recordTapOpensDetail.collectAsStateWithLifecycle()

    // Pager state is hoisted to CantaNavigation so a page opened elsewhere knows the tab.
    val currentTab = AppsTab.entries[pagerState.currentPage]
    val selectedAppsType = currentTab.appsType

    // What Canta itself removed: the uninstalled list is everything with no install for this user,
    // and only the log can tell the two apart. 那一格现在叫「未安装」，所以这件事不再逐行显示
    // （见 [NeverInstalledBadge]），这个集合留着给需要它的清单用。
    val log by history.records.collectAsStateWithLifecycle()
    val uninstalledByCanta = remember(log) {
        log.filter { it.kind == OperationKind.UNINSTALL && it.success }
            .mapTo(HashSet()) { it.packageName }
    }

    LaunchedEffect(pagerState) {
        // Filters and selection are per tab: reset the selection when the user switches
        // tabs. The first emission is skipped, so a selection set just before coming back
        // to this page is not cleared.
        snapshotFlow { pagerState.currentPage }.drop(1).collect {
            appListViewModel.selectedApps.clear()
        }
    }

    Scaffold(
        topBar = {
            CantaTopBar(
                openBadgesInfoDialog = mainViewModel::showBadgesExplanation,
                navigateToPage = navigateToPage,
                appListViewModel = appListViewModel,
            )
        },
        floatingActionButton = {
            MainFab(
                visible = selectedAppsType != null && appListViewModel.selectedApps.isNotEmpty(),
                appsType = selectedAppsType,
                onClick = { selectedAppsType?.let { mainViewModel.applyToSelected(it) } },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 点应用列表的空白处：收起顶栏搜索框的焦点与输入法。
                .dismissInputOnTap(),
        ) {
            AppsTabs(
                pagerState = pagerState,
                // 双击已经选中的那一格：把顶栏搜索框里的字清掉，列表回到不筛的样子。
                onDoubleTap = { appListViewModel.searchQuery = "" },
            )
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
                                    OperationKind.DISABLE -> OperationKind.ENABLE
                                    OperationKind.ENABLE -> OperationKind.DISABLE
                                }
                                val done = appListViewModel.applyPackages(
                                    packageNames = listOf(record.packageName),
                                    kind = reverse,
                                )
                                if (done > 0) {
                                    val action = getString(
                                        when (reverse) {
                                            OperationKind.UNINSTALL -> Res.string.uninstall
                                            OperationKind.REINSTALL -> Res.string.reinstall
                                            OperationKind.DISABLE -> Res.string.records_action_disable
                                            OperationKind.ENABLE -> Res.string.records_action_enable
                                        }
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
                        // Off by default: unasked-for, the log stays until it is cleared by hand.
                        clearRecordsAfterBoot = clearRecordsAfterBoot,
                        onClearRecordsAfterBootChange = settingsViewModel::saveClearRecordsAfterBoot,
                        // Bound to the page's second switch: on, a tap looks first and acts from
                        // there; off, a tap is the reverse operation, which is how it always was.
                        recordTapOpensDetail = recordTapOpensDetail,
                        onRecordTapOpensDetailChange = settingsViewModel::saveRecordTapOpensDetail,
                        onOpenApp = { packageName ->
                            navigateToPage(Screen.AppDetail.routeForApp(packageName))
                        },
                        // 那颗垃圾桶：清掉整份记录（blob 与下载目录里的 CSV 一起清），清完说一句，
                        // 否则点下去看不见任何变化，像没生效。
                        onClearRecords = {
                            reverseScope.launch {
                                history.clear()
                                platform.showMessage("记录已清空")
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
                            uninstalledByCanta = uninstalledByCanta,
                            // 设置里那个开关决定要不要走这里：默认点一下就进这个包的详情页，
                            // 关掉之后列表自己弹小窗，不会叫到这个回调。
                            onOpenAppPage = { packageName ->
                                navigateToPage(Screen.AppDetail.routeForApp(packageName))
                            },
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

    MainDialogs(handler, platform, mainViewModel, history)
}

@Composable
private fun MainFab(
    visible: Boolean,
    appsType: AppsType?,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        // Fades where it stands: it must not shrink towards the middle of the page as it goes,
        // which is the dismissal the dialogs use.
        exit = fadeOut()
    ) {
        appsType ?: return@AnimatedVisibility
        FloatingActionButton(
            containerColor = when (appsType) {
                AppsType.INSTALLED -> MaterialTheme.colorScheme.error
                AppsType.UNINSTALLED, AppsType.DISABLED -> MaterialTheme.colorScheme.primary
            },
            contentColor = when (appsType) {
                AppsType.INSTALLED -> MaterialTheme.colorScheme.onError
                AppsType.UNINSTALLED, AppsType.DISABLED -> MaterialTheme.colorScheme.onPrimary
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
                // A frozen app is put back, not removed: the button says which way it goes.
                AppsType.DISABLED -> Icon(
                    Icons.Default.PlayArrow,
                    stringResource(Res.string.records_action_enable),
                )
            }
        }
    }
}

@Composable
private fun AppsTabs(pagerState: PagerState, onDoubleTap: () -> Unit) {
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
        onDoubleTap = { onDoubleTap() },
    )
}

/**
 * Renders [MainViewModel.dialog] (once, outside the pager).
 *
 * Shared with the app page: an app's own page removes one package through the same view model, so
 * the confirmation, the blocklist, the biometric prompt and the success/failure report are drawn by
 * this same code there — two pages cannot drift into two different wordings for one action.
 */
@Composable
internal fun MainDialogs(
    handler: CantaHandler,
    platform: CantaPlatform,
    mainViewModel: MainViewModel,
    /** Where the restore script comes from; null on builds that cannot hand a file out. */
    history: OperationLogStore? = null,
) {
    val scope = rememberCoroutineScope()
    // 卸载成功之后的那一步：先把恢复脚本带走，再谈重启。弹窗关掉、选择框开着，是本页的两个状态。
    var restorePicker by remember { mutableStateOf(false) }

    when (val dialog = mainViewModel.dialog) {
        null -> {}
        is MainDialog.ConfirmUninstall -> UninstallAppsDialog(
            appCount = dialog.appCount,
            canResetToFactory = dialog.canResetToFactory,
            bootloopRisk = dialog.bootloopRisk,
            unsafeLevelCount = dialog.unsafeLevelCount,
            expertLevelCount = dialog.expertLevelCount,
            newPackages = dialog.newPackages,
            canFreeze = dialog.canFreeze,
            onDismiss = mainViewModel::dismissDialog,
            onAgree = mainViewModel::onUninstallConfirmed,
            onFreeze = mainViewModel::onFreezeConfirmed,
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
            kind = dialog.kind,
            onDismissRequest = mainViewModel::dismissDialog,
            // 只有卸载会留下"起不来"的可能：恢复和停用都能一键回到原样，不需要这份文件。
            onExportRestore = if (dialog.kind == OperationKind.UNINSTALL && history != null) {
                {
                    mainViewModel.dismissDialog()
                    restorePicker = true
                }
            } else {
                null
            },
        )
        is MainDialog.Failure -> FailureDialog(
            attempts = dialog.attempts,
            reasons = dialog.reasons,
            kind = dialog.kind,
            onDismissRequest = mainViewModel::dismissDialog,
            // The one failure worth a hand-off: an app that will not come back has to be fetched
            // from somewhere, and our own download page is the link to pass on.
            onOpenDownloadPage = if (dialog.kind == OperationKind.REINSTALL) {
                ({ platform.openUrl(DOWNLOAD_PAGE_URL) })
            } else {
                null
            },
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

    // 导出哪一份：只导记录里的，还是不看记录、把全部已卸载的都装回来。
    if (restorePicker) {
        RestoreExportDialog(
            onDismiss = { restorePicker = false },
            onPick = { mode ->
                restorePicker = false
                history?.let { store ->
                    scope.launch {
                        val exported = store.shareRestoreScript(mode)
                        platform.showMessage(restoreExportMessage(exported))
                    }
                }
            },
        )
    }
}
