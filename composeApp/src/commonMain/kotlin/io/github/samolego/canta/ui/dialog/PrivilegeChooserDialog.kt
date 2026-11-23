package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.PrivilegeChoice
import io.github.samolego.canta.core.PrivilegeKind
import io.github.samolego.canta.core.PrivilegeStatus
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.close
import io.github.samolego.canta.generated.resources.onboarding_privilege_detect
import io.github.samolego.canta.generated.resources.onboarding_privilege_grant
import io.github.samolego.canta.generated.resources.privilege_chooser_current
import io.github.samolego.canta.generated.resources.privilege_chooser_dhizuku_note
import io.github.samolego.canta.generated.resources.privilege_chooser_download
import io.github.samolego.canta.generated.resources.privilege_chooser_no_root
import io.github.samolego.canta.generated.resources.privilege_chooser_open_dhizuku
import io.github.samolego.canta.generated.resources.privilege_chooser_open_manager
import io.github.samolego.canta.generated.resources.privilege_chooser_root_note
import io.github.samolego.canta.generated.resources.privilege_chooser_switch
import io.github.samolego.canta.generated.resources.privilege_chooser_title
import io.github.samolego.canta.generated.resources.privilege_state_active
import io.github.samolego.canta.generated.resources.privilege_state_none
import io.github.samolego.canta.generated.resources.privilege_state_not_authorized
import io.github.samolego.canta.generated.resources.privilege_state_not_running
import io.github.samolego.canta.generated.resources.privilege_state_pending
import io.github.samolego.canta.generated.resources.settings_privilege_dhizuku
import io.github.samolego.canta.generated.resources.settings_privilege_root
import io.github.samolego.canta.generated.resources.settings_privilege_shizuku
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
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
 * @param onSwitch move the chain onto a route that is already granted, e.g. away from Shizuku to
 *   the Dhizuku grant this phone also holds. Only offered for rows that are not [PrivilegeChoice.current].
 */
@Composable
fun PrivilegeChooserDialog(
    choices: List<PrivilegeChoice>,
    onDismiss: () -> Unit,
    onGrantRoot: () -> Unit,
    onOpenManager: (PrivilegeKind) -> Unit,
    onDownload: (PrivilegeKind) -> Unit,
    onRefresh: () -> Unit,
    onSwitch: (PrivilegeKind) -> Unit,
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
                        onSwitch = onSwitch,
                    )
                }
        }
    }
}

/**
 * One route as a tappable card: a status dot, its name with an "in use" tag, one word for the
 * state, and the single button that moves it forward. The whole card does what that button does,
 * so the switch is as wide as the row instead of a 3-character word on the right.
 */
@Composable
private fun PrivilegeChoiceRow(
    choice: PrivilegeChoice,
    onGrantRoot: () -> Unit,
    onOpenManager: (PrivilegeKind) -> Unit,
    onDownload: (PrivilegeKind) -> Unit,
    onSwitch: (PrivilegeKind) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val action = actionOf(choice, onGrantRoot, onOpenManager, onDownload, onSwitch)
    val granted = choice.status == PrivilegeStatus.ACTIVE
    // A dot carries the whole state at a glance: working, reachable but not ready, or not there.
    val accent = when {
        granted -> scheme.primary
        choice.installed -> scheme.tertiary
        else -> scheme.outline
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (choice.current) scheme.primary.copy(alpha = 0.10f) else FlSurfaceLevel.Card.colorOf(scheme))
            .then(
                if (choice.current) {
                    Modifier.border(1.dp, scheme.primary.copy(alpha = 0.55f), shape)
                } else {
                    Modifier
                },
            )
            .then(if (action != null) Modifier.clickable(onClick = action.second) else Modifier)
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            // The row the app is actually running on is tagged as such: two rows can read "granted"
            // at once, and without this the dialog would leave the user to guess which one works.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = titleOf(choice),
                    style = FlText.subheadline,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (choice.current) {
                    Spacer(modifier = Modifier.width(6.dp))
                    CurrentTag()
                }
            }
            Text(
                text = statusText(choice),
                style = FlText.caption2,
                color = if (granted) accent else scheme.onSurfaceVariant,
            )
            // Dhizuku is the one route whose powers stop before removal: said on the row itself,
            // because the difference only shows up later, as a refused restore.
            if (choice.kind == PrivilegeKind.DHIZUKU) {
                Text(
                    text = stringResource(Res.string.privilege_chooser_dhizuku_note),
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (choice.kind == PrivilegeKind.ROOT && choice.installed) {
                Text(
                    text = stringResource(Res.string.privilege_chooser_root_note),
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (action != null) {
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = action.second,
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text(
                    text = action.first,
                    style = FlText.caption1,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The same name the settings row uses, so both pages read as one thing. */
@Composable
private fun titleOf(choice: PrivilegeChoice): String = when (choice.kind) {
    PrivilegeKind.ROOT -> stringResource(Res.string.settings_privilege_root)
    PrivilegeKind.SHIZUKU -> stringResource(Res.string.settings_privilege_shizuku)
    PrivilegeKind.DHIZUKU -> stringResource(Res.string.settings_privilege_dhizuku)
    // Filtered out above: NONE is not a privilege, there is nothing to name.
    PrivilegeKind.NONE -> ""
}

/** The little "in use" tag next to the row the app is running on. */
@Composable
private fun CurrentTag() {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = stringResource(Res.string.privilege_chooser_current),
        style = FlText.caption2,
        fontWeight = FontWeight.SemiBold,
        color = scheme.onPrimary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(scheme.primary)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * How far [choice] got, in one word: the settings row says the same thing, so the two pages agree
 * without either of them repeating a sentence. Root has a case of its own — a phone with no `su`
 * at all has nothing to grant.
 */
@Composable
private fun statusText(choice: PrivilegeChoice): String = when {
    choice.kind == PrivilegeKind.ROOT && !choice.installed ->
        stringResource(Res.string.privilege_chooser_no_root)

    choice.status == PrivilegeStatus.ACTIVE ->
        stringResource(Res.string.privilege_state_active)

    choice.kind == PrivilegeKind.ROOT ->
        stringResource(Res.string.privilege_state_pending)

    choice.status == PrivilegeStatus.NOT_AUTHORIZED ->
        stringResource(Res.string.privilege_state_not_authorized)

    choice.status == PrivilegeStatus.NOT_RUNNING ->
        stringResource(Res.string.privilege_state_not_running)

    else -> stringResource(Res.string.privilege_state_none)
}

/**
 * The button for [choice], or null when there is nothing left to do: a granted root, an authorized
 * runtime, or a phone with no `su` — root is not something to download.
 *
 * A route that is granted but not [PrivilegeChoice.current] keeps one button: switching to it. Two
 * grants can coexist (Shizuku and Dhizuku on the same phone), and the row that is not in use is
 * exactly where the user expects to be able to pick it.
 */
@Composable
private fun actionOf(
    choice: PrivilegeChoice,
    onGrantRoot: () -> Unit,
    onOpenManager: (PrivilegeKind) -> Unit,
    onDownload: (PrivilegeKind) -> Unit,
    onSwitch: (PrivilegeKind) -> Unit,
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

    // The route in use: nothing left to do on it.
    choice.status == PrivilegeStatus.ACTIVE && choice.current -> null

    // Granted, but the chain is running on another route: this is the hand-picked switch.
    choice.status == PrivilegeStatus.ACTIVE ->
        stringResource(Res.string.privilege_chooser_switch) to { onSwitch(choice.kind) }

    // Installed but not authorized or not running: both are answered inside the manager app — the
    // service to start for Stellar/Shizuku, the approval screen for Dhizuku.
    choice.kind == PrivilegeKind.DHIZUKU ->
        stringResource(Res.string.privilege_chooser_open_dhizuku) to { onOpenManager(choice.kind) }

    else -> stringResource(Res.string.privilege_chooser_open_manager) to { onOpenManager(choice.kind) }
}
