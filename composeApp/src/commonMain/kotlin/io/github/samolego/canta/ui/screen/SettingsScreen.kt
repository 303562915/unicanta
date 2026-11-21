package io.github.samolego.canta.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.BuildFlags
import io.github.samolego.canta.BuildInfo
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.core.DeviceUser
import io.github.samolego.canta.core.PrivilegeKind
import io.github.samolego.canta.core.PrivilegeStatus
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.about_us
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_active
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_not_authorized
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_not_available
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_not_running
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_root_active
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_root_pending
import io.github.samolego.canta.generated.resources.settings_privilege_none
import io.github.samolego.canta.generated.resources.settings_privilege_root
import io.github.samolego.canta.generated.resources.settings_privilege_shizuku
import io.github.samolego.canta.generated.resources.settings_privilege_title
import io.github.samolego.canta.generated.resources.advanced_settings
import io.github.samolego.canta.generated.resources.allow_unsafe_selections
import io.github.samolego.canta.generated.resources.allow_unsafe_uninstalls_description
import io.github.samolego.canta.generated.resources.auth_required
import io.github.samolego.canta.generated.resources.auth_required_description
import io.github.samolego.canta.generated.resources.auto_update_bloat_list
import io.github.samolego.canta.generated.resources.auto_update_bloat_list_description
import io.github.samolego.canta.generated.resources.bloat_list_url
import io.github.samolego.canta.generated.resources.bloat_list_url_description
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.commits_url
import io.github.samolego.canta.generated.resources.commits_url_description
import io.github.samolego.canta.generated.resources.confirm_uninstall
import io.github.samolego.canta.generated.resources.debug_section
import io.github.samolego.canta.generated.resources.device_user
import io.github.samolego.canta.generated.resources.device_user_description
import io.github.samolego.canta.generated.resources.fake_community_scores
import io.github.samolego.canta.generated.resources.fake_community_scores_description
import io.github.samolego.canta.generated.resources.hide_success_dialog
import io.github.samolego.canta.generated.resources.ok
import io.github.samolego.canta.generated.resources.profile_default_name
import io.github.samolego.canta.generated.resources.profile_edit_name
import io.github.samolego.canta.generated.resources.profile_name_hint
import io.github.samolego.canta.generated.resources.profile_name_title
import io.github.samolego.canta.generated.resources.require_auth_setting
import io.github.samolego.canta.generated.resources.require_auth_setting_desc
import io.github.samolego.canta.generated.resources.settings
import io.github.samolego.canta.generated.resources.settings_group_device
import io.github.samolego.canta.generated.resources.settings_group_general
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ExpandIcon
import io.github.samolego.canta.ui.component.LetterAvatar
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsGroup
import io.github.samolego.canta.ui.component.SettingsItem
import io.github.samolego.canta.ui.component.SettingsChevron
import io.github.samolego.canta.ui.component.UnreadDot
import io.github.samolego.canta.ui.component.SettingsTextItem
import io.github.samolego.canta.ui.component.SwitchSettingsItem
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.stringResource


/** Longest nickname the dialog takes, so the avatar row never has to shrink it. */
private const val MAX_NAME_LENGTH = 24

/** Space between two group cards. */
private val GroupGap = 12.dp

/** Room left under the last card for the floating bar that is drawn over the page. */
private val FloatingBarRoom = 96.dp

@Composable
fun SettingsScreen(
    platform: CantaPlatform,
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel,
    onVersionTap: () -> Unit,
    onOpenAbout: () -> Unit,
    /** Opens the page that lists this install's comments and the replies to them. */
    onOpenMyComments: () -> Unit = {},
    /** Replies this install has not opened yet; the row shows a dot instead of quiet text. */
    unreadReplies: Int = 0,
    deviceUsers: List<DeviceUser> = emptyList(),
    selectedUserId: Int? = null,
    onUserSelected: (Int) -> Unit = {},
    fakeScores: Boolean = false,
    onFakeScoresChange: (Boolean) -> Unit = {},
    /**
     * Which privileged runtime is actually working right now, and how far it got. Read from the
     * handler rather than remembered: a phone that was rooted yesterday and lost it today has to
     * say so on this page, not on the setup page the user has already passed.
     */
    privilegeKind: PrivilegeKind = PrivilegeKind.NONE,
    privilegeStatus: PrivilegeStatus = PrivilegeStatus.NOT_AVAILABLE,
) {
    val autoUpdateBloatList by settingsViewModel.autoUpdateBloatList.collectAsStateWithLifecycle()
    val confirmBeforeUninstall by settingsViewModel.confirmBeforeUninstall.collectAsStateWithLifecycle()
    val allowUnsafe by settingsViewModel.allowUnsafeUninstalls.collectAsStateWithLifecycle()
    val hideSuccessDialog by settingsViewModel.hideSuccessDialog.collectAsStateWithLifecycle()
    val authEnabled by settingsViewModel.authEnabled.collectAsStateWithLifecycle()
    val bloatListUrl by settingsViewModel.bloatListUrl.collectAsStateWithLifecycle()
    val commitsUrl by settingsViewModel.commitsUrl.collectAsStateWithLifecycle()
    val userName by settingsViewModel.userName.collectAsStateWithLifecycle()
    val identityUserId by settingsViewModel.identityUserId.collectAsStateWithLifecycle()
    val showTweaksTab by settingsViewModel.showTweaksTab.collectAsStateWithLifecycle()
    val showRecommendTab by settingsViewModel.showRecommendTab.collectAsStateWithLifecycle()
    var showRename by rememberSaveable { mutableStateOf(false) }
    var advancedSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    val authRequiredTitle = stringResource(Res.string.auth_required)
    val authRequiredSubtitle = stringResource(Res.string.auth_required_description)

    if (showRename) {
        RenameDialog(
            current = userName,
            onDismiss = { showRename = false },
            onSave = { name ->
                settingsViewModel.saveUserName(name)
                showRename = false
            },
        )
    }

    Scaffold(
        topBar = {
            ScreenTopBar(title = stringResource(Res.string.settings))
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = FloatingBarRoom)
        ) {
            SettingsGroup {
                UserCard(
                    name = userName,
                    identityUserId = identityUserId,
                    onEdit = { showRename = true },
                )
            }
            Spacer(modifier = Modifier.height(GroupGap))

            // Which privilege the app is actually running on. The setup page says it once, at the
            // start; this is where someone looks when an uninstall is refused and wants to know why.
            SettingsGroup(title = stringResource(Res.string.settings_privilege_title)) {
                SettingsItem(
                    title = when (privilegeKind) {
                        PrivilegeKind.ROOT -> stringResource(Res.string.settings_privilege_root)
                        PrivilegeKind.SHIZUKU -> stringResource(Res.string.settings_privilege_shizuku)
                        PrivilegeKind.NONE -> stringResource(Res.string.settings_privilege_none)
                    },
                    description = when {
                        privilegeKind == PrivilegeKind.ROOT && privilegeStatus == PrivilegeStatus.ACTIVE ->
                            stringResource(Res.string.onboarding_privilege_state_root_active)

                        privilegeKind == PrivilegeKind.ROOT ->
                            stringResource(Res.string.onboarding_privilege_state_root_pending)

                        privilegeStatus == PrivilegeStatus.ACTIVE ->
                            stringResource(Res.string.onboarding_privilege_state_active)

                        privilegeStatus == PrivilegeStatus.NOT_AUTHORIZED ->
                            stringResource(Res.string.onboarding_privilege_state_not_authorized)

                        privilegeStatus == PrivilegeStatus.NOT_RUNNING ->
                            stringResource(Res.string.onboarding_privilege_state_not_running)

                        else -> stringResource(Res.string.onboarding_privilege_state_not_available)
                    },
                    icon = Icons.Default.Lock,
                    divider = false,
                )
            }
            Spacer(modifier = Modifier.height(GroupGap))

            if (deviceUsers.size > 1) {
                SettingsGroup(title = stringResource(Res.string.settings_group_device)) {
                    DeviceUserItem(deviceUsers, selectedUserId, onUserSelected, divider = false)
                }
                Spacer(modifier = Modifier.height(GroupGap))
            }

            SettingsGroup(title = stringResource(Res.string.settings_group_general)) {
                SwitchSettingsItem(
                    title = stringResource(Res.string.auto_update_bloat_list),
                    description = stringResource(Res.string.auto_update_bloat_list_description),
                    icon = Icons.Default.Update,
                    checked = autoUpdateBloatList,
                    onCheckedChange = settingsViewModel::saveAutoUpdateBloatList,
                )
                SwitchSettingsItem(
                    title = stringResource(Res.string.confirm_uninstall),
                    icon = Icons.Default.Delete,
                    checked = confirmBeforeUninstall,
                    onCheckedChange = settingsViewModel::saveConfirmBeforeUninstall,
                )
                SwitchSettingsItem(
                    title = stringResource(Res.string.hide_success_dialog),
                    icon = Icons.AutoMirrored.Default.Message,
                    checked = hideSuccessDialog,
                    onCheckedChange = settingsViewModel::saveHideSuccessDialog,
                )
                SwitchSettingsItem(
                    title = stringResource(Res.string.require_auth_setting),
                    description = stringResource(Res.string.require_auth_setting_desc),
                    icon = Icons.Default.Lock,
                    checked = authEnabled,
                    divider = false,
                    onCheckedChange = { enabled ->
                        platform.requireBiometric(title = authRequiredTitle, subtitle = authRequiredSubtitle) {
                            settingsViewModel.saveAuthEnabled(enabled)
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(GroupGap))
            SettingsGroup {
                SettingsItem(
                    title = stringResource(Res.string.advanced_settings),
                    icon = Icons.Default.Settings,
                    onClick = { advancedSettingsExpanded = !advancedSettingsExpanded },
                    // The hairline is only there while something follows the row.
                    divider = advancedSettingsExpanded,
                    trailing = { ExpandIcon(advancedSettingsExpanded) },
                )
                AnimatedVisibility(
                    visible = advancedSettingsExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        SwitchSettingsItem(
                            title = stringResource(Res.string.allow_unsafe_selections),
                            description = stringResource(Res.string.allow_unsafe_uninstalls_description),
                            icon = Icons.Default.Close,
                            checked = allowUnsafe,
                            onCheckedChange = settingsViewModel::saveAllowUnsafeUninstalls,
                        )
                        SettingsTextItem(
                            title = stringResource(Res.string.bloat_list_url),
                            description = stringResource(Res.string.bloat_list_url_description),
                            icon = Icons.Default.Link,
                            keyboardType = KeyboardType.Uri,
                            storedValue = bloatListUrl,
                            onCommit = settingsViewModel::saveBloatListUrl,
                        )
                        SettingsTextItem(
                            title = stringResource(Res.string.commits_url),
                            description = stringResource(Res.string.commits_url_description),
                            icon = Icons.Default.Link,
                            keyboardType = KeyboardType.Uri,
                            storedValue = commitsUrl,
                            onCommit = settingsViewModel::saveCommitsUrl,
                            divider = false,
                        )
                    }
                }
            }

            if (BuildFlags.IS_DEBUG) {
                Spacer(modifier = Modifier.height(GroupGap))
                SettingsGroup(title = stringResource(Res.string.debug_section)) {
                    SwitchSettingsItem(
                        title = stringResource(Res.string.fake_community_scores),
                        description = stringResource(Res.string.fake_community_scores_description),
                        icon = Icons.Default.BugReport,
                        checked = fakeScores,
                        onCheckedChange = onFakeScoresChange,
                        divider = false,
                    )
                }
            }

            Spacer(modifier = Modifier.height(GroupGap))
            SettingsGroup {
                // What this install wrote in the community pages, and who answered: the comments
                // live on the backend, so the page is one tap away rather than a local list.
                SettingsItem(
                    title = "我的评论",
                    description = if (unreadReplies > 0) {
                        "$unreadReplies 条新消息，点这里看"
                    } else {
                        "我评论过的方案和应用，别人给我的回复"
                    },
                    icon = Icons.AutoMirrored.Default.Message,
                    onClick = onOpenMyComments,
                    trailing = {
                        if (unreadReplies > 0) {
                            UnreadDot(modifier = Modifier.padding(end = 6.dp))
                        }
                        SettingsChevron()
                    },
                )
                SettingsItem(
                    title = stringResource(Res.string.about_us),
                    description = BuildInfo.VERSION_NAME,
                    icon = Icons.Default.Info,
                    divider = false,
                    onClick = onOpenAbout,
                    trailing = { SettingsChevron() },
                )
            }
        }
    }
}

/**
 * Who the page belongs to: a letter avatar — the first character of the name, since the app has
 * no picture to show — the name itself, and a tap that renames it.
 */
@Composable
private fun UserCard(name: String, identityUserId: Long = 0L, onEdit: () -> Unit) {
    val defaultName = stringResource(Res.string.profile_default_name)
    // What this install is called when nobody picked a name: the number the backend gave it —
    // "#10134" — which is also what every other reader sees beside its plans and comments. It is
    // not a name, and showing it back is the point of the pulse below.
    val identityTag = if (identityUserId > 0L) "#$identityUserId" else ""
    val shown = name.trim().ifEmpty { identityTag.ifEmpty { defaultName } }
    val unnamed = isDefaultIdentityName(name) || (name.isBlank() && identityTag.isNotEmpty())
    // The pulse: pale yellow fading in and out for as long as the name is a number. Slow on
    // purpose — a fast blink reads as an error to dismiss, while this is a request the page makes
    // until it is answered, and nothing but picking a name switches it off.
    val transition = rememberInfiniteTransition(label = "rename-pulse")
    val glow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rename-glow",
    )
    // Kept inside 0.10..0.20 alpha: enough to notice out of the corner of an eye, soft enough that
    // the row stays readable — a wall of colour would read as a warning, and this is a nudge.
    val highlight = if (unnamed) 0.10f + 0.10f * glow else 0f
    val glowText = Color(0xFF8A6D00)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (unnamed) Color(0xFFFFE96B).copy(alpha = highlight) else Color.Transparent)
            .clickable(onClick = onEdit)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterAvatar(name = shown, size = 38.dp)
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = shown,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (unnamed) glowText else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unnamed) {
                    Spacer(Modifier.width(6.dp))
                    // The one place the page says why it keeps pulsing, instead of a dialog the
                    // reader would dismiss without reading.
                    Text(
                        text = "还没起名字",
                        fontSize = 9.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFFE96B).copy(alpha = 0.45f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        color = Color(0xFF6B5400),
                    )
                }
            }
            Text(
                text = if (unnamed) {
                    // The number is quoted back, so the request is about something the reader can
                    // see on screen rather than an abstract "rename yourself".
                    "点这里起个名字，别拿 $shown 当名字"
                } else {
                    stringResource(Res.string.profile_edit_name)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (unnamed) glowText else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (unnamed) glowText else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Whether [name] is still the identity the backend handed out rather than a name somebody picked.
 *
 * Empty and "#10134" both mean "no name": the first is an install that never opened the dialog, the
 * second is one that saved the number it was shown. Nothing else counts — a user who calls
 * themselves "用户" made a choice, and telling them otherwise would be wrong.
 */
internal fun isDefaultIdentityName(name: String): Boolean {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return true
    return trimmed.length > 1 && trimmed.startsWith("#") && trimmed.drop(1).all { it.isDigit() }
}

/** The first character of [name] is drawn by [LetterAvatar]; this dialog only edits the text. */
@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(current) }
    val save = { onSave(value.trim()) }
    CantaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.profile_name_title),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = save) { Text(stringResource(Res.string.ok)) }
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= MAX_NAME_LENGTH) value = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(Res.string.profile_name_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
    }
}

/** The "device user" row: the selected user, with a dropdown to switch to another one. */
@Composable
private fun DeviceUserItem(
    users: List<DeviceUser>,
    selectedId: Int?,
    onSelected: (Int) -> Unit,
    divider: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingsItem(
        title = stringResource(Res.string.device_user),
        description = stringResource(Res.string.device_user_description),
        icon = Icons.Default.AccountCircle,
        onClick = { expanded = true },
        divider = divider,
        trailing = {
            Text(
                text = users.firstOrNull { it.id == selectedId }?.label().orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        },
    )
}

/** "Name (id)", or just the id when the user has no name. */
private fun DeviceUser.label() = if (name.isBlank()) "$id" else "$name ($id)"
