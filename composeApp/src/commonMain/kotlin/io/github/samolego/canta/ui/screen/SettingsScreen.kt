package io.github.samolego.canta.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.BuildFlags
import io.github.samolego.canta.BuildInfo
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.core.DeviceUser
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.advanced_settings
import io.github.samolego.canta.generated.resources.allow_unsafe_selections
import io.github.samolego.canta.generated.resources.allow_unsafe_uninstalls_description
import io.github.samolego.canta.generated.resources.app_version
import io.github.samolego.canta.generated.resources.auth_required
import io.github.samolego.canta.generated.resources.auth_required_description
import io.github.samolego.canta.generated.resources.auto_update_bloat_list
import io.github.samolego.canta.generated.resources.auto_update_bloat_list_description
import io.github.samolego.canta.generated.resources.bloat_list_url
import io.github.samolego.canta.generated.resources.bloat_list_url_description
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.click_to_expand
import io.github.samolego.canta.generated.resources.commits_url
import io.github.samolego.canta.generated.resources.commits_url_description
import io.github.samolego.canta.generated.resources.confirm_uninstall
import io.github.samolego.canta.generated.resources.confirm_uninstall_description
import io.github.samolego.canta.generated.resources.debug_section
import io.github.samolego.canta.generated.resources.device_user
import io.github.samolego.canta.generated.resources.device_user_description
import io.github.samolego.canta.generated.resources.fake_community_scores
import io.github.samolego.canta.generated.resources.fake_community_scores_description
import io.github.samolego.canta.generated.resources.hide_success_dialog
import io.github.samolego.canta.generated.resources.hide_success_dialog_description
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
import io.github.samolego.canta.generated.resources.settings_group_interface
import io.github.samolego.canta.generated.resources.settings_show_recommend
import io.github.samolego.canta.generated.resources.settings_show_recommend_description
import io.github.samolego.canta.generated.resources.settings_show_tweaks
import io.github.samolego.canta.generated.resources.settings_show_tweaks_description
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ExpandIcon
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsGroup
import io.github.samolego.canta.ui.component.SettingsItem
import io.github.samolego.canta.ui.component.SettingsTextItem
import io.github.samolego.canta.ui.component.SwitchSettingsItem
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.stringResource

private const val CANTA_WEBSITE = "https://303562915.github.io/Canta"

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
    deviceUsers: List<DeviceUser> = emptyList(),
    selectedUserId: Int? = null,
    onUserSelected: (Int) -> Unit = {},
    fakeScores: Boolean = false,
    onFakeScoresChange: (Boolean) -> Unit = {},
) {
    val autoUpdateBloatList by settingsViewModel.autoUpdateBloatList.collectAsStateWithLifecycle()
    val confirmBeforeUninstall by settingsViewModel.confirmBeforeUninstall.collectAsStateWithLifecycle()
    val allowUnsafe by settingsViewModel.allowUnsafeUninstalls.collectAsStateWithLifecycle()
    val hideSuccessDialog by settingsViewModel.hideSuccessDialog.collectAsStateWithLifecycle()
    val authEnabled by settingsViewModel.authEnabled.collectAsStateWithLifecycle()
    val bloatListUrl by settingsViewModel.bloatListUrl.collectAsStateWithLifecycle()
    val commitsUrl by settingsViewModel.commitsUrl.collectAsStateWithLifecycle()
    val userName by settingsViewModel.userName.collectAsStateWithLifecycle()
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
                UserCard(name = userName, onEdit = { showRename = true })
            }
            Spacer(modifier = Modifier.height(GroupGap))

            SettingsGroup(title = stringResource(Res.string.settings_group_interface)) {
                SwitchSettingsItem(
                    title = stringResource(Res.string.settings_show_tweaks),
                    description = stringResource(Res.string.settings_show_tweaks_description),
                    icon = Icons.Default.Build,
                    checked = showTweaksTab,
                    onCheckedChange = settingsViewModel::saveShowTweaksTab,
                )
                SwitchSettingsItem(
                    title = stringResource(Res.string.settings_show_recommend),
                    description = stringResource(Res.string.settings_show_recommend_description),
                    icon = Icons.Default.Apps,
                    checked = showRecommendTab,
                    onCheckedChange = settingsViewModel::saveShowRecommendTab,
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
                    description = stringResource(Res.string.confirm_uninstall_description),
                    icon = Icons.Default.Delete,
                    checked = confirmBeforeUninstall,
                    onCheckedChange = settingsViewModel::saveConfirmBeforeUninstall,
                )
                SwitchSettingsItem(
                    title = stringResource(Res.string.hide_success_dialog),
                    description = stringResource(Res.string.hide_success_dialog_description),
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
                    description = stringResource(Res.string.click_to_expand),
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

            Spacer(modifier = Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(Res.string.app_version, BuildInfo.VERSION_NAME),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onVersionTap)
                )
                Text(
                    text = CANTA_WEBSITE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable { platform.openUrl(CANTA_WEBSITE) }
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
private fun UserCard(name: String, onEdit: () -> Unit) {
    val defaultName = stringResource(Res.string.profile_default_name)
    val shown = name.trim().ifEmpty { defaultName }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = avatarLetter(shown),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shown,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.profile_edit_name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The first character of [name], kept whole when it is a surrogate pair. */
private fun avatarLetter(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val first = trimmed[0]
    return if (first.isHighSurrogate() && trimmed.length > 1) trimmed.substring(0, 2) else first.toString()
}

/** Renames the local profile; the name is only used on this page. */
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
