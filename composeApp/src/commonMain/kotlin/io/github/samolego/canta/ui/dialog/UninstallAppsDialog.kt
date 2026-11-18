package io.github.samolego.canta.ui.dialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.are_you_sure_to_uninstall_apps
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.community_bootloop_warning
import io.github.samolego.canta.generated.resources.ok
import io.github.samolego.canta.generated.resources.reset_to_factory_version
import io.github.samolego.canta.generated.resources.uninstall_large_batch_warning
import io.github.samolego.canta.generated.resources.uninstall_new_packages
import io.github.samolego.canta.generated.resources.uninstall_new_packages_more
import io.github.samolego.canta.generated.resources.uninstall_recovery_hint
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.CheckboxRow
import io.github.samolego.canta.util.isLargeBatch
import org.jetbrains.compose.resources.stringResource

/** Most newly added package names listed in the dialog; the rest is summarised. */
private const val MAX_SHOWN_NEW = 8

@Composable
fun UninstallAppsDialog(
    appCount: Int,
    canResetToFactory: Boolean = false,
    bootloopRisk: Boolean = false,
    newPackages: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onAgree: (resetToFactory: Boolean) -> Unit,
) {
    var resetToFactory by rememberSaveable { mutableStateOf(false) }
    val largeBatch = isLargeBatch(appCount)
    val shownNew = newPackages.take(MAX_SHOWN_NEW)

    CantaDialog(
        onDismissRequest = onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = { onAgree(resetToFactory) }) { Text(stringResource(Res.string.ok)) }
        },
    ) {
        Text(stringResource(Res.string.are_you_sure_to_uninstall_apps, appCount))
        if (bootloopRisk) {
            Text(
                text = stringResource(Res.string.community_bootloop_warning),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (newPackages.isNotEmpty()) {
            // A batch pasted from a shared list looks like any other: name what was just added.
            Text(
                text = stringResource(Res.string.uninstall_new_packages, shownNew.joinToString(", ")),
                color = MaterialTheme.colorScheme.error,
            )
            if (newPackages.size > shownNew.size) {
                Text(stringResource(Res.string.uninstall_new_packages_more, newPackages.size - shownNew.size))
            }
        }
        if (largeBatch) {
            Text(stringResource(Res.string.uninstall_large_batch_warning))
        }
        // A single app from the list is routine, so the reminder stays out of the way; the moment a
        // batch is large or a bootloop is already on record, it is the most useful thing on screen.
        if (largeBatch || bootloopRisk) {
            Text(
                text = stringResource(Res.string.uninstall_recovery_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canResetToFactory) {
            CheckboxRow(
                checked = resetToFactory,
                onCheckedChange = { resetToFactory = it },
                label = stringResource(Res.string.reset_to_factory_version),
            )
        }
    }
}
