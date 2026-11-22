package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.PrivilegeChoice
import io.github.samolego.canta.core.PrivilegeKind
import io.github.samolego.canta.core.PrivilegeStatus
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.close
import io.github.samolego.canta.generated.resources.onboarding_privilege_detect
import io.github.samolego.canta.generated.resources.onboarding_privilege_grant
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_active
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_not_authorized
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_not_available
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_not_running
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_root_active
import io.github.samolego.canta.generated.resources.onboarding_privilege_state_root_pending
import io.github.samolego.canta.generated.resources.privilege_chooser_dhizuku_note
import io.github.samolego.canta.generated.resources.privilege_chooser_download
import io.github.samolego.canta.generated.resources.privilege_chooser_no_root
import io.github.samolego.canta.generated.resources.privilege_chooser_open_dhizuku
import io.github.samolego.canta.generated.resources.privilege_chooser_open_manager
import io.github.samolego.canta.generated.resources.privilege_chooser_root_title
import io.github.samolego.canta.generated.resources.privilege_chooser_title
import io.github.samolego.canta.generated.resources.settings_privilege_dhizuku
import io.github.samolego.canta.generated.resources.settings_privilege_shizuku
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import org.jetbrains.compose.resources.stringResource

/**
 * Lets the user pick which privilege Canta works through, instead of having to accept the one the
 * app guessed: a rooted phone has a `su` behind it and needs no runtime app, a phone without one
 * needs Stellar or Shizuku installed, started and allowed. Both rows say how far they got and carry
 * the single button that moves them forward from there.
 *
 * The buttons call back into the host, which owns the coroutine and the platform: the dialog itself
 * only names the step.
 *
 * @param choices what the handler reports as available, root first; the row for a [PrivilegeKind.NONE]
 *   entry is not drawn, since there is no such thing to offer.
 * @param onDismiss the dialog was closed.
 * @param onGrantRoot raise the root manager's prompt; the host closes the dialog for it.
 * @param onOpenManager lead to the manager app of [PrivilegeKind]: Stellar/Shizuku has a service to
 *   start there, Dhizuku has an approval screen to answer.
 * @param onDownload install the manager app of [PrivilegeKind] (root has nothing to download).
 * @param onRefresh read the two states again, after the user did a step in another app.
 */
@Composable
fun PrivilegeChooserDialog(
    choices: List<PrivilegeChoice>,
    onDismiss: () -> Unit,
    onGrantRoot: () -> Unit,
    onOpenManager: (PrivilegeKind) -> Unit,
    onDownload: (PrivilegeKind) -> Unit,
    onRefresh: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.privilege_chooser_title),
        widthFraction = WIDE_DIALOG_WIDTH,
        buttons = {
            // Every step happens in another app, so the state is stale the moment the user comes
            // back: checking again is the one action that is always worth offering.
            TextButton(onClick = onRefresh) { Text(stringResource(Res.string.onboarding_privilege_detect)) }
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            choices
                .filter { it.kind != PrivilegeKind.NONE }
                .forEach { choice ->
                    PrivilegeChoiceRow(
                        choice = choice,
                        onGrantRoot = onGrantRoot,
                        onOpenManager = onOpenManager,
                        onDownload = onDownload,
                    )
                }
        }
    }
}

/**
 * One route: what it is, how far it got, and the one button that moves it forward — none when it
 * already works, because there is nothing left to do there.
 */
@Composable
private fun PrivilegeChoiceRow(
    choice: PrivilegeChoice,
    onGrantRoot: () -> Unit,
    onOpenManager: (PrivilegeKind) -> Unit,
    onDownload: (PrivilegeKind) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (choice.kind) {
                        PrivilegeKind.ROOT -> stringResource(Res.string.privilege_chooser_root_title)
                        // The same name the settings row uses, so both pages read as one thing.
                        PrivilegeKind.SHIZUKU -> stringResource(Res.string.settings_privilege_shizuku)
                        PrivilegeKind.DHIZUKU -> stringResource(Res.string.settings_privilege_dhizuku)
                        // Filtered out above: NONE is not a privilege, there is nothing to name.
                        PrivilegeKind.NONE -> ""
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = statusText(choice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Dhizuku is the one route whose powers stop before removal: said on the row itself,
                // because the difference only shows up later, as a refused uninstall.
                if (choice.kind == PrivilegeKind.DHIZUKU) {
                    Text(
                        text = stringResource(Res.string.privilege_chooser_dhizuku_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            actionOf(choice, onGrantRoot, onOpenManager, onDownload)?.let { (label, onClick) ->
                TextButton(onClick = onClick) { Text(label) }
            }
        }
    }
}

/**
 * How far [choice] got, in the words the setup page already uses. Root has two sentences of its
 * own: "not authorized" there means the root manager has not answered yet, not that a service is
 * running without Canta in it, and a phone without a `su` at all has nothing to grant.
 */
@Composable
private fun statusText(choice: PrivilegeChoice): String = when {
    choice.kind == PrivilegeKind.ROOT && choice.status == PrivilegeStatus.ACTIVE ->
        stringResource(Res.string.onboarding_privilege_state_root_active)

    choice.kind == PrivilegeKind.ROOT && !choice.installed ->
        stringResource(Res.string.privilege_chooser_no_root)

    choice.kind == PrivilegeKind.ROOT ->
        stringResource(Res.string.onboarding_privilege_state_root_pending)

    choice.status == PrivilegeStatus.ACTIVE ->
        stringResource(Res.string.onboarding_privilege_state_active)

    choice.status == PrivilegeStatus.NOT_AUTHORIZED ->
        stringResource(Res.string.onboarding_privilege_state_not_authorized)

    choice.status == PrivilegeStatus.NOT_RUNNING ->
        stringResource(Res.string.onboarding_privilege_state_not_running)

    else -> stringResource(Res.string.onboarding_privilege_state_not_available)
}

/**
 * The button for [choice], or null when there is nothing left to do: a granted root, an authorized
 * runtime, or a phone with no `su` — root is not something to download.
 */
@Composable
private fun actionOf(
    choice: PrivilegeChoice,
    onGrantRoot: () -> Unit,
    onOpenManager: (PrivilegeKind) -> Unit,
    onDownload: (PrivilegeKind) -> Unit,
): Pair<String, () -> Unit>? = when {
    // Nothing installed yet: the fix is to install the runtime, and the row's button goes there.
    // Root is not something to download, so it keeps the branch below.
    choice.kind != PrivilegeKind.ROOT && !choice.installed ->
        stringResource(Res.string.privilege_chooser_download) to { onDownload(choice.kind) }

    // A phone with no `su` at all: root is not something to download, so the row says so above and
    // offers nothing here.
    !choice.installed -> null

    // Root's grant lives in the root manager's own prompt, which the handler raises.
    choice.kind == PrivilegeKind.ROOT && choice.status != PrivilegeStatus.ACTIVE ->
        stringResource(Res.string.onboarding_privilege_grant) to onGrantRoot

    choice.status == PrivilegeStatus.ACTIVE -> null

    // Installed but not authorized or not running: both are answered inside the manager app — the
    // service to start for Stellar/Shizuku, the approval screen for Dhizuku.
    choice.kind == PrivilegeKind.DHIZUKU ->
        stringResource(Res.string.privilege_chooser_open_dhizuku) to { onOpenManager(choice.kind) }

    else -> stringResource(Res.string.privilege_chooser_open_manager) to { onOpenManager(choice.kind) }
}
