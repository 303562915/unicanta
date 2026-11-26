package io.github.samolego.canta.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.BuildFlags
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.core.DeviceUser
import io.github.samolego.canta.core.PrivilegeChoice
import io.github.samolego.canta.core.PrivilegeKind
import io.github.samolego.canta.core.PrivilegeStatus
import io.github.samolego.canta.data.history.RestoreMode
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.advanced_settings
import io.github.samolego.canta.generated.resources.allow_unsafe_selections
import io.github.samolego.canta.generated.resources.allow_unsafe_uninstalls_description
import io.github.samolego.canta.generated.resources.auto_update_bloat_list
import io.github.samolego.canta.generated.resources.bloat_list_url
import io.github.samolego.canta.generated.resources.bloat_list_url_description
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.commits_url
import io.github.samolego.canta.generated.resources.commits_url_description
import io.github.samolego.canta.generated.resources.confirm_uninstall
import io.github.samolego.canta.generated.resources.device_user
import io.github.samolego.canta.generated.resources.device_user_description
import io.github.samolego.canta.generated.resources.fake_community_scores
import io.github.samolego.canta.generated.resources.fake_community_scores_description
import io.github.samolego.canta.generated.resources.hide_success_dialog
import io.github.samolego.canta.generated.resources.privilege_state_active
import io.github.samolego.canta.generated.resources.privilege_state_not_authorized
import io.github.samolego.canta.generated.resources.privilege_state_not_running
import io.github.samolego.canta.generated.resources.privilege_state_pending
import io.github.samolego.canta.generated.resources.require_auth_setting
import io.github.samolego.canta.generated.resources.require_auth_setting_desc
import io.github.samolego.canta.generated.resources.settings
import io.github.samolego.canta.generated.resources.settings_group_theme
import io.github.samolego.canta.generated.resources.settings_privilege_dhizuku
import io.github.samolego.canta.generated.resources.settings_privilege_dhizuku_active
import io.github.samolego.canta.generated.resources.settings_privilege_none
import io.github.samolego.canta.generated.resources.settings_privilege_root
import io.github.samolego.canta.generated.resources.settings_privilege_shizuku
import io.github.samolego.canta.generated.resources.theme_mode_dark
import io.github.samolego.canta.generated.resources.theme_mode_light
import io.github.samolego.canta.generated.resources.theme_mode_system
import io.github.samolego.canta.ui.component.AppBarSlotsCard
import io.github.samolego.canta.ui.component.AppCardCorner
import io.github.samolego.canta.ui.component.AppCardInset
import io.github.samolego.canta.ui.component.AppLinkCard
import io.github.samolego.canta.ui.component.AppToggleCard
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ExpandIcon
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsGroup
import io.github.samolego.canta.ui.component.SettingsTextItem
import io.github.samolego.canta.ui.component.SwitchSettingsItem
import io.github.samolego.canta.ui.dialog.LaunchGateDialog
import io.github.samolego.canta.ui.dialog.RestoreExportDialog
import io.github.samolego.canta.ui.fl.FlAccent
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlThemeMode
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.lock.launchGateOf
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** 卡片之间的距离，与关于页保持一致。 */
private val CardGap = 10.dp

/**
 * 「设置」页：从「我的」页进来，摆的是那些不常改、但总要能改到的开关。
 *
 * 布局沿用关于页的卡片流——一张卡一个决定，比一张长表读得快；权限、多用户、主题、
 * 底栏槽位和启动锁这些原本散在设置页与关于页两头的东西，在这里合成一页。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsScreen(
    platform: CantaPlatform,
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    /** 打开主题页：配色与深浅色在那页里选，这里只写当前值。 */
    onOpenTheme: () -> Unit = {},
    /** 打开删除名单页：导出一份 JSON、再导回来（见 [DeletionListScreen]）。 */
    onOpenDeletionList: () -> Unit = {},
    /**
     * 现在实际生效的是哪一种特权运行时，以及走到哪一步。读 handler 而不是记着：
     * 昨天能 root、今天丢了的手机，得在这一页说清楚。
     */
    privilegeKind: PrivilegeKind = PrivilegeKind.NONE,
    privilegeStatus: PrivilegeStatus = PrivilegeStatus.NOT_AVAILABLE,
    /**
     * 这台手机可走的路由，由 handler 报出。页面上不用它（选择器自己有），
     * 保留是为了调用方接线口径一致。
     */
    privilegeChoices: List<PrivilegeChoice> = emptyList(),
    /** 打开选择器，让用户手选 root 或 Stellar/Shizuku。 */
    onOpenPrivilegeChooser: () -> Unit = {},
    deviceUsers: List<DeviceUser> = emptyList(),
    selectedUserId: Int? = null,
    onUserSelected: (Int) -> Unit = {},
    fakeScores: Boolean = false,
    onFakeScoresChange: (Boolean) -> Unit = {},
    /**
     * 生成恢复脚本并交给分享面板，好发到电脑上双击运行：[RestoreMode.REMOVED] 按记录恢复，
     * [RestoreMode.ALL] 一键恢复所有已卸载的包。文件由
     * [io.github.samolego.canta.data.history.OperationLogStore.shareRestoreScript] 生成。
     */
    onExportRestoreScript: (RestoreMode) -> Unit = {},
) {
    val autoUpdateBloatList by settingsViewModel.autoUpdateBloatList.collectAsStateWithLifecycle()
    val confirmBeforeUninstall by settingsViewModel.confirmBeforeUninstall.collectAsStateWithLifecycle()
    val allowUnsafe by settingsViewModel.allowUnsafeUninstalls.collectAsStateWithLifecycle()
    val hideSuccessDialog by settingsViewModel.hideSuccessDialog.collectAsStateWithLifecycle()
    // 存的是「点一下弹小窗」，所以下面那个开关读的是它的反；见 AppSettingsProto.appTapOpensDialog。
    val appTapOpensDialog by settingsViewModel.appTapOpensDialog.collectAsStateWithLifecycle()
    val authEnabled by settingsViewModel.authEnabled.collectAsStateWithLifecycle()
    val bloatListUrl by settingsViewModel.bloatListUrl.collectAsStateWithLifecycle()
    val commitsUrl by settingsViewModel.commitsUrl.collectAsStateWithLifecycle()
    val showTweaksTab by settingsViewModel.showTweaksTab.collectAsStateWithLifecycle()
    val showRecommendTab by settingsViewModel.showRecommendTab.collectAsStateWithLifecycle()
    val showPostsTab by settingsViewModel.showPostsTab.collectAsStateWithLifecycle()
    val showStoreTab by settingsViewModel.showStoreTab.collectAsStateWithLifecycle()
    val themeAccent by settingsViewModel.themeAccent.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val showDevicePhoto by settingsViewModel.showDevicePhoto.collectAsStateWithLifecycle()
    // 存的是「静音」，所以下面那个开关读的是它的反；见 AppSettingsProto.muteChatNotices。
    val muteChatNotices by settingsViewModel.muteChatNotices.collectAsStateWithLifecycle()

    // 启动锁，按库里的值摊开。认不出的题加一个真日期的组合也按「未设置」显示，
    // 免得关锁的那一页自己先被一个没人答得出的问题挡住。
    val lockQuestion by settingsViewModel.launchQuestion.collectAsStateWithLifecycle()
    val lockAnswer by settingsViewModel.launchAnswer.collectAsStateWithLifecycle()
    val activeGate = lockQuestion?.let { question ->
        lockAnswer?.let { answer -> launchGateOf(question, answer) }
    }
    var gateDialogShown by remember { mutableStateOf(false) }
    var restoreDialogShown by remember { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }

    val authRequiredTitle = stringResource(Res.string.require_auth_setting)
    val authRequiredSubtitle = stringResource(Res.string.require_auth_setting_desc)
    val themeModeLabel = when (FlThemeMode.ofStored(themeMode)) {
        FlThemeMode.System -> stringResource(Res.string.theme_mode_system)
        FlThemeMode.Light -> stringResource(Res.string.theme_mode_light)
        FlThemeMode.Dark -> stringResource(Res.string.theme_mode_dark)
    }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            // 从「我的」页进来，所以顶栏带回退箭头。
            ScreenTopBar(
                title = stringResource(Res.string.settings),
                onNavigateBack = onNavigateBack,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 28.dp),
        ) {
            // 当前特权来源。装机页只说过一次，卸载被拒时用户要看的就是这里。
            AppLinkCard(
                glyph = FlGlyphs.lock,
                title = when (privilegeKind) {
                    PrivilegeKind.ROOT -> stringResource(Res.string.settings_privilege_root)
                    PrivilegeKind.SHIZUKU -> stringResource(Res.string.settings_privilege_shizuku)
                    PrivilegeKind.DHIZUKU -> stringResource(Res.string.settings_privilege_dhizuku)
                    PrivilegeKind.NONE -> stringResource(Res.string.settings_privilege_none)
                },
                subtitle = when {
                    privilegeKind == PrivilegeKind.ROOT && privilegeStatus == PrivilegeStatus.ACTIVE ->
                        stringResource(Res.string.privilege_state_active)

                    privilegeKind == PrivilegeKind.ROOT ->
                        stringResource(Res.string.privilege_state_pending)

                    // Dhizuku 也是「已授权」，但能做的事更窄：这一行写明白，
                    // 否则第一次被拒的卸载会被当成 bug。
                    privilegeKind == PrivilegeKind.DHIZUKU && privilegeStatus == PrivilegeStatus.ACTIVE ->
                        stringResource(Res.string.settings_privilege_dhizuku_active)

                    privilegeStatus == PrivilegeStatus.ACTIVE ->
                        stringResource(Res.string.privilege_state_active)

                    privilegeStatus == PrivilegeStatus.NOT_AUTHORIZED ->
                        stringResource(Res.string.privilege_state_not_authorized)

                    privilegeStatus == PrivilegeStatus.NOT_RUNNING ->
                        stringResource(Res.string.privilege_state_not_running)

                    // 一条也没检测到：标题已经写「没检测到」，副标题不再说第二遍。
                    else -> ""
                },
                // 点它就是换路由：链路自己会挑一条，这里是唯一能手选的地方。
                onClick = onOpenPrivilegeChooser,
            )
            Spacer(Modifier.height(CardGap))

            // 多用户/工作资料：只有一个用户时没有可选的，整张卡不出现。
            if (deviceUsers.size > 1) {
                DeviceUserCard(
                    users = deviceUsers,
                    selectedId = selectedUserId,
                    onSelected = onUserSelected,
                )
                Spacer(Modifier.height(CardGap))
            }

            AppToggleCard(
                glyph = FlGlyphs.cloudDownload,
                title = stringResource(Res.string.auto_update_bloat_list),
                checked = autoUpdateBloatList,
                onCheckedChange = settingsViewModel::saveAutoUpdateBloatList,
            )
            Spacer(Modifier.height(CardGap))
            AppToggleCard(
                glyph = FlGlyphs.warning,
                title = stringResource(Res.string.confirm_uninstall),
                checked = confirmBeforeUninstall,
                onCheckedChange = settingsViewModel::saveConfirmBeforeUninstall,
            )
            Spacer(Modifier.height(CardGap))
            AppToggleCard(
                glyph = FlGlyphs.check,
                title = stringResource(Res.string.hide_success_dialog),
                checked = hideSuccessDialog,
                onCheckedChange = settingsViewModel::saveHideSuccessDialog,
            )
            Spacer(Modifier.height(CardGap))
            // 应用列表里点一个包去哪：出厂是直接开它的详情页，关掉这个开关就退回弹小窗。
            // 放在第一页而不是「高级设置」里，因为这是每次点列表都会遇到的事。
            AppToggleCard(
                glyph = FlGlyphs.appsList,
                title = "点击应用打开详情页",
                checked = !appTapOpensDialog,
                onCheckedChange = { openPage -> settingsViewModel.saveAppTapOpensDialog(!openPage) },
                subtitle = "关掉就还是弹小窗",
            )
            Spacer(Modifier.height(CardGap))
            // 生物识别那条：真正的开关由系统校验的回调决定，被取消就什么也不改。
            AppToggleCard(
                glyph = FlGlyphs.lock,
                title = stringResource(Res.string.require_auth_setting),
                checked = authEnabled,
                onCheckedChange = { enabled ->
                    platform.requireBiometric(title = authRequiredTitle, subtitle = authRequiredSubtitle) {
                        settingsViewModel.saveAuthEnabled(enabled)
                    }
                },
                subtitle = stringResource(Res.string.require_auth_setting_desc),
            )
            Spacer(Modifier.height(CardGap))

            // 配色和深浅色在主题页里选；这张卡只说现在用的是哪一套，点开那页。
            AppLinkCard(
                glyph = FlGlyphs.settings,
                title = stringResource(Res.string.settings_group_theme),
                subtitle = "${FlAccent.ofArgb(themeAccent).label} · $themeModeLabel",
                onClick = onOpenTheme,
            )
            Spacer(Modifier.height(CardGap))

            // 底栏本身：第一行四个常亮，第二行三个可选项（工具/推荐/帖子），点了才上底栏。
            AppBarSlotsCard(
                showTweaks = showTweaksTab,
                showRecommend = showRecommendTab,
                showPosts = showPostsTab,
                showStore = showStoreTab,
                onToggleTweaks = settingsViewModel::saveShowTweaksTab,
                onToggleRecommend = settingsViewModel::saveShowRecommendTab,
                onTogglePosts = settingsViewModel::saveShowPostsTab,
                onToggleStore = settingsViewModel::saveShowStoreTab,
            )
            Spacer(Modifier.height(CardGap))

            // 安全页顶部的名字与机型照片：两者都是装饰，机型串有些人不想露在外面，
            // 所以合成一个开关，出厂关着。
            AppToggleCard(
                glyph = FlGlyphs.iconTile,
                title = "显示机型与照片",
                checked = showDevicePhoto,
                onCheckedChange = settingsViewModel::saveShowDevicePhoto,
            )
            Spacer(Modifier.height(CardGap))

            // 启动问题：一道生日挡在一切前面，给那些递到别人手里会被乱点的手
            // 机用。没设置之前是关着的，只在这里设。
            AppLinkCard(
                glyph = FlGlyphs.lock,
                title = "启动密码",
                subtitle = activeGate?.let { "${it.question.label} · 已开启" }
                    ?: "未设置 · 设置后打开软件要先答一个生日",
                onClick = { gateDialogShown = true },
            )
            Spacer(Modifier.height(CardGap))

            // 有人发消息来的时候要不要在系统通知栏弹一条。
            //
            // 出厂是开的（proto 里存的是"静音"），但打开这个开关的同时得把系统权限要到：
            // Android 13 起那是运行时权限，没有它 NotificationManagerCompat 会把通知静默
            // 丢掉，开关看着是开的、却什么都不弹。拒绝了就把开关退回去，不留一个说谎的勾。
            AppToggleCard(
                glyph = FlGlyphs.send,
                title = "消息通知",
                checked = !muteChatNotices,
                onCheckedChange = { wantOn ->
                    when {
                        !wantOn -> settingsViewModel.saveMuteChatNotices(true)
                        // 早就有了权限（Android 12 及以下，或者之前点过允许）：直接开，不打扰。
                        platform.canNotify() -> settingsViewModel.saveMuteChatNotices(false)
                        else -> scope.launch {
                            if (platform.requestNotifyPermission()) {
                                settingsViewModel.saveMuteChatNotices(false)
                            } else {
                                platform.showMessage("没有通知权限，系统不会显示消息提醒")
                            }
                        }
                    }
                },
            )
            Spacer(Modifier.height(CardGap))

            // 撤回卸载的最后一条线：删掉的包其实还在系统分区里，install-existing 能把标记取消，
            // 但那条命令只能在电脑上通过 adb 打。这里生成一个批处理发出去，双击就跑：
            // 可以先按记录恢复，也可以不看记录、一键恢复全部已卸载的包。
            AppLinkCard(
                glyph = FlGlyphs.send,
                title = "导出恢复脚本",
                subtitle = "最近删除的包名 / 一键恢复全部已卸载的包，发到电脑上双击运行",
                onClick = { restoreDialogShown = true },
            )
            Spacer(Modifier.height(CardGap))

            // 删除名单：自己决定要删的包名，导出一份 JSON 带走，换手机再导回来。
            // 和上面那份恢复脚本是两件事：那个是"救我这次删的"，这个是"我一直想删的清单"。
            AppLinkCard(
                glyph = FlGlyphs.importFile,
                title = "删除名单（导入 / 导出）",
                subtitle = "把自己删掉的包名导成 JSON，换机或重装后再导回来",
                onClick = onOpenDeletionList,
            )
            Spacer(Modifier.height(CardGap))

            // 高级设置：地址和危险开关收在折叠里，平时看不见，免得一页里全是需要读的句子。
            ExpandTriggerCard(
                glyph = FlGlyphs.tools,
                title = stringResource(Res.string.advanced_settings),
                expanded = advancedExpanded,
                onClick = { advancedExpanded = !advancedExpanded },
            )
            AnimatedVisibility(
                visible = advancedExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(top = CardGap)) {
                    SettingsGroup {
                        SwitchSettingsItem(
                            title = stringResource(Res.string.allow_unsafe_selections),
                            description = stringResource(Res.string.allow_unsafe_uninstalls_description),
                            checked = allowUnsafe,
                            onCheckedChange = settingsViewModel::saveAllowUnsafeUninstalls,
                        )
                        SettingsTextItem(
                            title = stringResource(Res.string.bloat_list_url),
                            description = stringResource(Res.string.bloat_list_url_description),
                            keyboardType = KeyboardType.Uri,
                            storedValue = bloatListUrl,
                            onCommit = settingsViewModel::saveBloatListUrl,
                        )
                        SettingsTextItem(
                            title = stringResource(Res.string.commits_url),
                            description = stringResource(Res.string.commits_url_description),
                            keyboardType = KeyboardType.Uri,
                            storedValue = commitsUrl,
                            onCommit = settingsViewModel::saveCommitsUrl,
                            divider = false,
                        )
                    }
                }
            }

            // 调试包的开关：只在开发版里出现，正式包里这一整块不存在。
            if (BuildFlags.IS_DEBUG) {
                Spacer(Modifier.height(CardGap))
                AppToggleCard(
                    glyph = FlGlyphs.warning,
                    title = stringResource(Res.string.fake_community_scores),
                    checked = fakeScores,
                    onCheckedChange = onFakeScoresChange,
                    subtitle = stringResource(Res.string.fake_community_scores_description),
                )
            }
        }

        // 设置、换题、关锁都在这个对话框里完成；写进库之后，下次启动由锁屏页读回来。
        if (gateDialogShown) {
            LaunchGateDialog(
                existing = activeGate,
                onSave = { questionId, answer -> settingsViewModel.saveLaunchGate(questionId, answer) },
                onClear = { settingsViewModel.clearLaunchGate() },
                onDismiss = { gateDialogShown = false },
            )
        }

        // 导出哪一份：只导记录里的，还是不看记录、把全部已卸载的都装回来。
        if (restoreDialogShown) {
            RestoreExportDialog(
                onDismiss = { restoreDialogShown = false },
                onPick = { mode ->
                    restoreDialogShown = false
                    onExportRestoreScript(mode)
                },
            )
        }
    }
}

/**
 * 多用户设备那一行：当前用户，外加一个下拉换人。
 *
 * 手写而不是用 [AppLinkCard]，因为右端要放的是「当前用户 + 菜单」而不是箭头；
 * 卡片几何与 [AppLinkCard] 逐项对齐，混在卡片流里看不出区别。
 */
@Composable
private fun DeviceUserCard(
    users: List<DeviceUser>,
    selectedId: Int?,
    onSelected: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset, vertical = 4.dp)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable { expanded = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIcon(glyph = FlGlyphs.profiles, size = 19.dp, tint = scheme.primary, fill = 0.2f)
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.device_user),
                style = FlText.subheadline,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.device_user_description),
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box {
            Text(
                text = users.firstOrNull { it.id == selectedId }?.label().orEmpty(),
                style = FlText.caption1,
                color = scheme.primary,
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                users.forEach { user ->
                    DropdownMenuItem(
                        text = { Text(user.label()) },
                        onClick = {
                            expanded = false
                            if (user.id != selectedId) onSelected(user.id)
                        },
                    )
                }
            }
        }
    }
}

/**
 * 折叠区的触发行：[AppLinkCard] 的几何，右端换成展开/收起箭头，
 * 让人看得出这一行下面还有东西。
 */
@Composable
private fun ExpandTriggerCard(
    glyph: FlGlyph,
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset, vertical = 4.dp)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIcon(glyph = glyph, size = 19.dp, tint = scheme.primary, fill = 0.2f)
        Spacer(Modifier.width(11.dp))
        Text(
            text = title,
            style = FlText.subheadline,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ExpandIcon(expanded)
    }
}

/** "名字 (id)"，用户没起名字时就只写 id。 */
private fun DeviceUser.label() = if (name.isBlank()) "$id" else "$name ($id)"
