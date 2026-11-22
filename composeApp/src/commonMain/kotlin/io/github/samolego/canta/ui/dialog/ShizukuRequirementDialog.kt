package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.core.PrivilegeKind
import io.github.samolego.canta.core.PrivilegeStatus
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.close
import io.github.samolego.canta.generated.resources.dhizuku_open_app
import io.github.samolego.canta.generated.resources.dhizuku_required
import io.github.samolego.canta.generated.resources.dhizuku_requirement_description
import io.github.samolego.canta.generated.resources.grant_dhizuku_permission_to_canta
import io.github.samolego.canta.generated.resources.grant_shizuku_permission_to_canta
import io.github.samolego.canta.generated.resources.install_shizuku
import io.github.samolego.canta.generated.resources.install_stellar_instead
import io.github.samolego.canta.generated.resources.start_shizuku_service
import io.github.samolego.canta.generated.resources.take_action
import io.github.samolego.canta.generated.resources.shizuku_required
import io.github.samolego.canta.generated.resources.shizuku_requirement_description
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.ui.theme.GreenOk
import io.github.samolego.canta.ui.theme.Orange
import org.jetbrains.compose.resources.stringResource

private const val SHIZUKU_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"
private const val SHIZUKU_GITHUB_URL = "https://github.com/RikkaApps/Shizuku/releases"

/** The fork we steer people to: same API and same binder, finer permissions, boots with the phone. */
private const val STELLAR_URL = "https://github.com/roro2239/Stellar/releases"

/** The manager that shares the DeviceOwner role; no Play Store listing, so GitHub is the download. */
private const val DHIZUKU_PACKAGE = "com.rosan.dhizuku"
private const val DHIZUKU_URL = "https://github.com/iamr0s/Dhizuku/releases"

/**
 * Walks the user through the Shizuku setup. [onClose] with `true` means
 * "request authorization and continue"; `false` means the user dismissed it.
 *
 * Android-only in practice (other targets pick a device instead of using
 * Shizuku), with Android-only steps filled in per target; see the actuals.
 */
@Composable
expect fun ShizukuRequirementDialog(
    onClose: (shouldProceed: Boolean) -> Unit,
    handler: CantaHandler,
    platform: CantaPlatform,
)

/**
 * The shared Shizuku setup UI. [usbRequirementRow] is an Android-only slot
 * rendered first in the requirements list; other targets leave it empty.
 */
@Composable
internal fun ShizukuRequirementDialogContent(
    onClose: (shouldProceed: Boolean) -> Unit,
    handler: CantaHandler,
    platform: CantaPlatform,
    usbRequirementRow: @Composable () -> Unit = {},
) {
    // Re-read on resume: the user fixes these steps in the Shizuku app and comes back.
    var status by remember { mutableStateOf(handler.privilegeStatus) }
    var isAuthorized by remember { mutableStateOf(handler.isAuthorized) }
    LifecycleResumeEffect(handler) {
        status = handler.privilegeStatus
        isAuthorized = handler.isAuthorized
        onPauseOrDispose {}
    }

    CantaDialog(
        onDismissRequest = { onClose(false) },
        // The title has to name the right manager: a Dhizuku user reading "Shizuku required" would
        // go looking for an app they do not need.
        title = stringResource(
            if (handler.privilegeKind == PrivilegeKind.DHIZUKU) Res.string.dhizuku_required
            else Res.string.shizuku_required
        ),
        widthFraction = WIDE_DIALOG_WIDTH,
        buttons = {
            TextButton(onClick = { onClose(false) }) { Text(stringResource(Res.string.close)) }
        },
    ) {
        // Dhizuku is answered inside its own app, not by starting a service: the grant is a prompt
        // the manager raises, and when it is not running the app has to be opened first.
        if (handler.privilegeKind == PrivilegeKind.DHIZUKU) {
            Text(
                text = stringResource(Res.string.dhizuku_requirement_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RequirementItem(
                    text = stringResource(Res.string.dhizuku_open_app),
                    isCompleted = status == PrivilegeStatus.ACTIVE || status == PrivilegeStatus.NOT_AUTHORIZED,
                    onActionClick = {
                        if (!platform.openApp(DHIZUKU_PACKAGE)) platform.openUrl(DHIZUKU_URL)
                    },
                )
                RequirementItem(
                    text = stringResource(Res.string.grant_dhizuku_permission_to_canta),
                    isCompleted = isAuthorized,
                    onActionClick = { onClose(true) },
                )
            }
            return@CantaDialog
        }

        // A rooted phone has nothing to install and no service to start: the only step is the root
        // manager's own prompt. Listing Stellar, Shizuku and "start its service" there would be
        // four instructions about software the user does not need.
        if (handler.privilegeKind == PrivilegeKind.ROOT) {
            Text(
                text = "这台手机有 root，Canta 直接通过 root 工作。第一次操作时会弹一次授权，允许之后就不用再问。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RequirementItem(
                    text = "在 root 管理器里允许 Canta",
                    isCompleted = isAuthorized,
                    onActionClick = { onClose(true) },
                )
            }
            return@CantaDialog
        }

        Text(
            text = stringResource(Res.string.shizuku_requirement_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            usbRequirementRow()
            // Offered above the original: one tap to the fork most people should use, while the
            // Shizuku row below keeps working for anyone who already has it.
            RequirementItem(
                text = stringResource(Res.string.install_stellar_instead),
                isCompleted = status != PrivilegeStatus.NOT_AVAILABLE,
                onActionClick = { platform.openUrl(STELLAR_URL) },
            )
            RequirementItem(
                text = stringResource(Res.string.install_shizuku),
                isCompleted = status != PrivilegeStatus.NOT_AVAILABLE,
                onActionClick = { platform.openUrl(SHIZUKU_PLAY_STORE_URL) },
            )
            RequirementItem(
                text = stringResource(Res.string.start_shizuku_service),
                isCompleted = status == PrivilegeStatus.ACTIVE || status == PrivilegeStatus.NOT_AUTHORIZED,
                onActionClick = { platform.openUrl(SHIZUKU_GITHUB_URL) },
            )
            RequirementItem(
                text = stringResource(Res.string.grant_shizuku_permission_to_canta),
                isCompleted = isAuthorized,
                onActionClick = { onClose(true) },
            )
        }
    }
}

@Composable
internal fun RequirementItem(
    text: String,
    isCompleted: Boolean,
    enabled: Boolean = true,
    onActionClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.Circle,
            contentDescription = null,
            tint = if (isCompleted) GreenOk else Orange,
            modifier = Modifier.size(16.dp)
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color =
            if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )

        if (!isCompleted) {
            IconButton(onClick = onActionClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowForward,
                    contentDescription = stringResource(Res.string.take_action),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
