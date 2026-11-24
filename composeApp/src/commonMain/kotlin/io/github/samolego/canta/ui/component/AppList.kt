package io.github.samolego.canta.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.samolego.canta.data.app.AppBadgeInfo
import io.github.samolego.canta.data.app.AppInfo
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.app_list_incomplete
import io.github.samolego.canta.generated.resources.clear_selected_apps
import io.github.samolego.canta.generated.resources.loading_apps
import io.github.samolego.canta.generated.resources.loading_badges
import io.github.samolego.canta.generated.resources.no_apps_found
import io.github.samolego.canta.generated.resources.num_selected_apps
import io.github.samolego.canta.generated.resources.package_name_copied
import io.github.samolego.canta.generated.resources.select_all
import io.github.samolego.canta.generated.resources.selected_apps
import io.github.samolego.canta.ui.AppsType
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.ui.dialog.AppInfoDialog
import io.github.samolego.canta.ui.fl.FlProgressBar
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel

/** Space below the last row so the FAB never covers it. */
private val FAB_CLEARANCE = 64.dp

/** Room a list keeps at its foot for the floating bar drawn over the page. */
private val FLOATING_BAR_ROOM = 96.dp

/** Narrowest app tile; wider windows get more columns. */
private val MIN_TILE_WIDTH = 360.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppList(
    appType: AppsType = AppsType.INSTALLED,
    appListModel: AppListViewModel,
    settingsViewModel: SettingsViewModel,
    platform: CantaPlatform,
    handler: CantaHandler,
    enableSelectAll: Boolean = false,
    /**
     * Packages Canta itself uninstalled, from the records tab's log. Everything else on the
     * uninstalled list is a package this phone never installed for the user — the AOSP installer a
     * vendor replaced, a vault this build does not use — and saying so is what keeps a reader from
     * believing they removed it (see [NeverInstalledBadge]).
     */
    uninstalledByCanta: Set<String> = emptySet(),
    /**
     * 点一个包要去的详情页（包名进路由）。设置里「点击应用打开详情页」关着的时候不用它，
     * 那时点一下还是弹 [AppInfoDialog]。
     */
    onOpenAppPage: (String) -> Unit = {},
) {
    var showAppDialog by remember { mutableStateOf<AppInfo?>(null) }

    // 存的是"弹窗"，所以这里读的是它的反：出厂是"点一下直接进详情页"。
    val appTapOpensDialog by settingsViewModel.appTapOpensDialog.collectAsStateWithLifecycle()

    /** 点一行：默认开详情页，开关关掉就退回弹窗。 */
    fun openApp(app: AppInfo) {
        if (appTapOpensDialog) showAppDialog = app else onOpenAppPage(app.packageName)
    }

    // 复制包名：列表里最常见的动作是把它拿去问 AI，所以点包名就落到剪贴板，再 toast 一句。
    val copiedMessage = stringResource(Res.string.package_name_copied)
    fun copyPackage(packageName: String) {
        platform.copyToClipboard(packageName)
        platform.showMessage(copiedMessage)
    }

    val appList by remember { derivedStateOf { appListModel.appList.filter(appType::matches) } }
    val selectedAppList by remember { derivedStateOf { appListModel.selectedAppsSorted.filter(appType::matches) } }
    /** The list is showing removals, so "Canta never touched this" is only a question here. */
    fun neverInstalled(app: AppInfo): Boolean =
        appType == AppsType.UNINSTALLED && app.packageName !in uninstalledByCanta
    val scores by appListModel.scores.collectAsStateWithLifecycle()
    val allowUnsafeUninstalls by settingsViewModel.allowUnsafeUninstalls.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        if (showAppDialog != null) {
            AppInfoDialog(
                appInfo = showAppDialog!!,
                score = scores[showAppDialog!!.packageName],
                platform = platform,
                handler = handler,
                onDismiss = { showAppDialog = null },
            )
        }

        // Said before the list, not after a search that finds nothing: a list that is missing two
        // thirds of the phone has to explain itself, or the empty tabs look like a broken app.
        if (appListModel.listIncomplete) {
            Text(
                text = stringResource(Res.string.app_list_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        if (appListModel.isLoading) {
            LoadingAppsInfo()
        } else {
            if (appListModel.isLoadingBadges) {
                LoadingBadgesIndicator()
            }
            if (appType == AppsType.UNINSTALLED ||
                appType == AppsType.DISABLED ||
                enableSelectAll &&
                appListModel.selectedFilter.badgeInfo ==
                AppBadgeInfo.RECOMMENDED
            ) {
                // Derived from the selection, and scoped to the visible apps, so it
                // stays in sync and never clears selections on the other tab.
                val visiblePackages = appList.map { it.packageName }
                SelectAllOption(
                    checked = visiblePackages.isNotEmpty() &&
                            visiblePackages.all { it in appListModel.selectedApps },
                    onCheckedChange = { selectAll ->
                        if (selectAll) {
                            appListModel.selectedApps.addAll(visiblePackages)
                        } else {
                            visiblePackages.forEach { appListModel.selectedApps.remove(it) }
                        }
                    }
                )
            }

            if (selectedAppList.isNotEmpty()) {
                Dropdown(
                    title = stringResource(Res.string.selected_apps),
                    subtitle = pluralStringResource(
                        Res.plurals.num_selected_apps,
                        selectedAppList.size,
                        selectedAppList.size
                    ),
                    modifier = Modifier.padding(8.dp),
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    // The expanded list is capped so a large selection does not push the
                    // app list off the screen; the FAB sits at the foot of the page, not
                    // under this card, so the list needs no trailing clearance.
                    maxHeight = 288.dp,
                    content = {
                        LazyColumn {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(all = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Button(
                                        onClick = {
                                            selectedAppList.forEach { appListModel.selectedApps.remove(it.packageName) }
                                        }
                                    ) {
                                        Text(
                                            pluralStringResource(
                                                Res.plurals.clear_selected_apps,
                                                selectedAppList.size,
                                                selectedAppList.size
                                            )
                                        )
                                    }
                                }
                            }
                            items(selectedAppList) { appInfo ->
                                Box(
                                    modifier = Modifier.padding(vertical = 2.dp).padding(horizontal = 16.dp)
                                ) {
                                    SelectedAppTile(
                                        appInfo = appInfo,
                                        score = scores[appInfo.packageName],
                                        neverInstalled = neverInstalled(appInfo),
                                        onCopyPackage = { copyPackage(appInfo.packageName) },
                                        onCheckChanged = {
                                            appListModel.selectedApps.remove(appInfo.packageName)
                                        },
                                        onOpen = { openApp(appInfo) },
                                    )
                                }
                            }
                        }
                    }
                )
            }

            if (appList.isNotEmpty()) {
                // Adaptive columns: single column on phone portrait, multiple
                // tiles per row on tablets, desktop and wide browser windows.
                //
                // The bar's own room is added *inside* the scrolling area rather than by
                // padding the page: a page that reserves a strip outside its list shows an
                // empty band at the foot, which the other pages do not have. Nothing is
                // added at the top, since the bar above already carries the status-bar inset.
                val pageInsets = WindowInsets.safeDrawing.asPaddingValues()
                val bottomRoom = pageInsets.calculateBottomPadding() + FLOATING_BAR_ROOM
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = MIN_TILE_WIDTH),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomRoom),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(appList, key = { it.packageName }) { appInfo ->
                        AppTile(
                            modifier = Modifier.padding(vertical = 2.dp),
                            appInfo = appInfo,
                            score = scores[appInfo.packageName],
                            isSelected =
                                appListModel.selectedApps.contains(appInfo.packageName),
                            neverInstalled = neverInstalled(appInfo),
                            enabled = appInfo.badgeInfo != AppBadgeInfo.UNSAFE || allowUnsafeUninstalls,
                            onCopyPackage = { copyPackage(appInfo.packageName) },
                            onCheckChanged = { checked ->
                                if (checked) {
                                    appListModel.selectedApps.add(appInfo.packageName)
                                } else {
                                    appListModel.selectedApps.remove(appInfo.packageName)
                                }
                            },
                            onOpen = { openApp(appInfo) }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(FAB_CLEARANCE))
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(Res.string.no_apps_found)) }
            }
        }
    }
}

/**
 * The badge network check is a wait with no length, so it wears the same bar the update card uses
 * while it downloads: a hairline that moves, instead of a spinner that reads as a stuck clock.
 */
@Composable
fun LoadingBadgesIndicator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FlProgressBar()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.loading_badges),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SelectAllOption(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    CheckboxRow(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = stringResource(Res.string.select_all),
        // Same end inset as the app tiles' list items, so the checkboxes line up.
        modifier = Modifier.padding(horizontal = 16.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

/**
 * The same bar as [LoadingBadgesIndicator], centred: the whole page is waiting for the package
 * list itself, and no part of the content is on screen yet.
 */
@Composable
fun LoadingAppsInfo() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(), contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FlProgressBar(modifier = Modifier.width(180.dp))
            Spacer(modifier = Modifier.size(16.dp))
            Text(stringResource(Res.string.loading_apps))
        }
    }
}
