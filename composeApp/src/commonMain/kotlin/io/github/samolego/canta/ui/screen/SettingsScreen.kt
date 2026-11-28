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
import io.github.samolego.canta.generated.resources.language_system
import io.github.samolego.canta.generated.resources.privilege_state_active
import io.github.samolego.canta.generated.resources.privilege_state_not_authorized
import io.github.samolego.canta.generated.resources.privilege_state_not_running
import io.github.samolego.canta.generated.resources.privilege_state_pending
import io.github.samolego.canta.generated.resources.require_auth_setting
import io.github.samolego.canta.generated.resources.require_auth_setting_desc
import io.github.samolego.canta.generated.resources.settings
import io.github.samolego.canta.generated.resources.settings_group_theme
import io.github.samolego.canta.generated.resources.settings_group_language
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
import io.github.samolego.canta.ui.component.AppCardPair
import io.github.samolego.canta.ui.component.AppLinkTile
import io.github.samolego.canta.ui.component.AppToggleTile
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
import io.github.samolego.canta.ui.locale.AppLanguageOptions
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
 *
 * 这里也是「机场」那一整页的入口（推荐 / 投票 / 众筹，见 [AirportHubScreen]）：它不占底栏，
 * 点这一行才进得去，与「主题」「卸载记录」是同一类二级页。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsScreen(
    platform: CantaPlatform,
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    /** 打开主题页：配色与深浅色在那页里选，这里只写当前值。 */
    onOpenTheme: () -> Unit = {},
    /** 打开卸载记录页：一份份方案，导出 JSON、导入、整份装回来（见 [DeletionListScreen]）。 */
    onOpenDeletionList: () -> Unit = {},
    /**
     * 打开「机场」独立页：推荐 / 投票 / 众筹三格（见 [AirportHubScreen]）。
     *
     * 它不占底栏，也不在推荐页里：点这一行才出现，与「主题」「卸载记录」是同一类入口。
     */
    onOpenAirports: () -> Unit = {},
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
    // 商城那一格不再有开关：它从底栏挪到了这一页（见下面的「商城」那一行），
    // 存着的 `showStoreTab` 因此没有读者，字段留着不动。
    val themeAccent by settingsViewModel.themeAccent.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    /** 手动挑的界面语言（BCP-47 标签）；空串 = 跟随系统。 */
    val appLanguage by settingsViewModel.appLanguage.collectAsStateWithLifecycle()
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
    /** 语言选择框开着没有。 */
    var languageDialogShown by rememberSaveable { mutableStateOf(false) }

    val authRequiredTitle = stringResource(Res.string.require_auth_setting)
    val authRequiredSubtitle = stringResource(Res.string.require_auth_setting_desc)
    val themeModeLabel = when (FlThemeMode.ofStored(themeMode)) {
        FlThemeMode.System -> stringResource(Res.string.theme_mode_system)
        FlThemeMode.Light -> stringResource(Res.string.theme_mode_light)
        FlThemeMode.Dark -> stringResource(Res.string.theme_mode_dark)
    }
    // 现在用的是哪门语言：清单里的原生写法（「简体中文」「English」）；空串或清单里没有的标签
    // 都算「跟随系统」——存的东西可能是手改过的设置文件，界面上不该出现一格看不懂的字符串。
    val languageSystemLabel = stringResource(Res.string.language_system)
    val languageLabel = AppLanguageOptions
        .firstOrNull { option -> option.tag == appLanguage }
        ?.name
        ?.ifBlank { languageSystemLabel }
        ?: languageSystemLabel
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

            // 前两个开关并排：标题都短，一上一行纯浪费高度。
            AppCardPair {
                AppToggleTile(
                    glyph = FlGlyphs.cloudDownload,
                    title = stringResource(Res.string.auto_update_bloat_list),
                    checked = autoUpdateBloatList,
                    onCheckedChange = settingsViewModel::saveAutoUpdateBloatList,
                )
                AppToggleTile(
                    glyph = FlGlyphs.warning,
                    title = stringResource(Res.string.confirm_uninstall),
                    checked = confirmBeforeUninstall,
                    onCheckedChange = settingsViewModel::saveConfirmBeforeUninstall,
                )
            }
            Spacer(Modifier.height(CardGap))
            // 「删完别弹成功框」自己一行：它和下面那两格不是一回事——那两格说的是"界面长什么样"。
            AppToggleCard(
                glyph = FlGlyphs.check,
                title = stringResource(Res.string.hide_success_dialog),
                checked = hideSuccessDialog,
                onCheckedChange = settingsViewModel::saveHideSuccessDialog,
            )
            Spacer(Modifier.height(CardGap))
            // 主题与语言并排，一行两格：配色与深浅色在主题页里选，语言在这一格点开挑。
            //
            // 语言为什么要有自己的开关：Compose Resources 取的是手机报的语言，而不少 ROM 的语言列表
            // 只有「简体中文 / English」，系统被识别成英文的机器上界面就整页英文。挑完立刻按新语言
            // 重画（见 `CantaTheme` 的 appLocale），不必去改系统设置、也不用重启应用。
            AppCardPair {
                AppLinkTile(
                    glyph = FlGlyphs.settings,
                    title = stringResource(Res.string.settings_group_theme),
                    subtitle = "${FlAccent.ofArgb(themeAccent).label} · $themeModeLabel",
                    onClick = onOpenTheme,
                )
                AppLinkTile(
                    glyph = FlGlyphs.resources,
                    title = stringResource(Res.string.settings_group_language),
                    subtitle = languageLabel,
                    onClick = { languageDialogShown = true },
                )
            }
            Spacer(Modifier.height(CardGap))

            // 生物识别那条：真正的开关由系统校验的回调决定，被取消就什么也不改。
            // 它带一句说明，独占一行——半宽写不下这句，挤成省略号反而更糟。
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

            // 底栏本身：第一行四个常亮，第二行三个可选项（工具/推荐/帖子），点了才上底栏。
            // 商城不在这张卡里：它已经整页挪到下面「商城」那一行。
            AppBarSlotsCard(
                showTweaks = showTweaksTab,
                showRecommend = showRecommendTab,
                showPosts = showPostsTab,
                onToggleTweaks = settingsViewModel::saveShowTweaksTab,
                onToggleRecommend = settingsViewModel::saveShowRecommendTab,
                onTogglePosts = settingsViewModel::saveShowPostsTab,
            )
            Spacer(Modifier.height(CardGap))

            // 商城不在这张卡里，也不在这一页：它的入口在「我的」页（和「我的方案」挨着），
            // 见 `MineScreen` 的 onOpenStore。

            // 安全页顶部的名字与机型照片：两者都是装饰，机型串有些人不想露在外面，
            // 所以合成一个开关，出厂关着。第二格是列表里点一个包去哪——都只有一行标题。
            AppCardPair {
                AppToggleTile(
                    glyph = FlGlyphs.iconTile,
                    title = "显示机型与照片",
                    checked = showDevicePhoto,
                    onCheckedChange = settingsViewModel::saveShowDevicePhoto,
                )
                AppToggleTile(
                    glyph = FlGlyphs.appsList,
                    title = "点应用开详情页",
                    checked = !appTapOpensDialog,
                    onCheckedChange = { openPage ->
                        settingsViewModel.saveAppTapOpensDialog(!openPage)
                    },
                )
            }
            Spacer(Modifier.height(CardGap))

            // 启动问题：一道生日挡在一切前面，给那些递到别人手里会被乱点的手
            // 机用。没设置之前是关着的，只在这里设。第二格是消息通知——两者都只写一行标题，
            // 并排摆比一上一行省一半高度。
            AppCardPair {
                AppLinkTile(
                    glyph = FlGlyphs.lock,
                    title = "启动密码",
                    onClick = { gateDialogShown = true },
                )
                AppToggleTile(
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
            }
            // 生日那道锁设了什么，单独一行说清楚：并排的格子太窄，写不下「已开启 / 未设置」。
            if (activeGate != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${activeGate.question.label} · 已开启",
                    style = FlText.caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppCardInset + 2.dp),
                )
            }
            Spacer(Modifier.height(CardGap))

            // 撤回卸载的最后一条线：删掉的包其实还在系统分区里，install-existing 能把标记取消，
            // 但那条命令只能在电脑上通过 adb 打。这里生成一个批处理发出去，双击就跑：
            // 可以先按记录恢复，也可以不看记录、一键恢复全部已卸载的包。
            //
            // 旁边那格是卸载记录：删过的包名按「方案」攒着，可以备份出去、也可以从备份恢复。
            // 两件事一起做，所以并排放，各占一半。
            AppCardPair {
                AppLinkTile(
                    glyph = FlGlyphs.send,
                    title = "恢复脚本",
                    onClick = { restoreDialogShown = true },
                )
                AppLinkTile(
                    glyph = FlGlyphs.importFile,
                    title = "卸载记录",
                    onClick = onOpenDeletionList,
                )
            }
            Spacer(Modifier.height(CardGap))

            // 机场：推荐、投票、众筹三格合成一整页。以前它在推荐页的分段控件上，和「按应用推荐 /
            // 按应用投票」挤在同一条控件里；那两半读社区后端的一张表，机场读另一张，门槛、详情页、
            // 评论区各有一套，所以整块搬出来，只在这里留一个入口——点一下才进得去，不占底栏。
            AppLinkCard(
                glyph = FlGlyphs.connections,
                title = "机场",
                subtitle = "推荐、投票与众筹合买；从这一行点进去",
                onClick = onOpenAirports,
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

        // 挑界面语言：选完立刻写进设置，`CantaTheme` 那一层会按新语言把整页重画一遍。
        if (languageDialogShown) {
            LanguagePickerDialog(
                selected = appLanguage,
                onPick = { tag ->
                    languageDialogShown = false
                    settingsViewModel.saveAppLanguage(tag)
                },
                onDismiss = { languageDialogShown = false },
            )
        }
    }
}

/**
 * 挑界面语言：一门一行，用这门语言自己的写法，当前那门右边一个勾。
 *
 * 四十门语言一屏放不下，所以借 [CantaDialog] 的 `scrollable`：标题、清单和取消钮一起滚，
 * 清单再长也不会被窗口裁掉一半。
 */
@Composable
private fun LanguagePickerDialog(
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.settings_group_language),
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    ) {
        AppLanguageOptions.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(option.tag) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option.name.ifBlank { stringResource(Res.string.language_system) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (option.tag == selected) {
                    FlGlyphIcon(
                        glyph = FlGlyphs.check,
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
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
