package io.github.samolego.canta.ui.dialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.app_list_restricted_description
import io.github.samolego.canta.generated.resources.app_list_restricted_dismiss
import io.github.samolego.canta.generated.resources.app_list_restricted_grant
import io.github.samolego.canta.generated.resources.app_list_restricted_open_settings
import io.github.samolego.canta.generated.resources.app_list_restricted_title
import io.github.samolego.canta.ui.component.CantaDialog
import org.jetbrains.compose.resources.stringResource

/**
 * Asked once per run when the ROM hides the package list: HyperOS and MIUI keep a "get installed
 * apps" switch that no app can request at runtime, so the only way is to send the user to it.
 * The button opens the ROM's permission editor (or the app details page as a fallback).
 */
@Composable
fun AppListPermissionDialog(
    canRequest: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.app_list_restricted_title),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.app_list_restricted_dismiss)) }
            TextButton(onClick = if (canRequest) onRequest else onOpenSettings) {
                Text(
                    stringResource(
                        if (canRequest) Res.string.app_list_restricted_grant
                        else Res.string.app_list_restricted_open_settings
                    )
                )
            }
        },
    ) {
        Text(
            text = stringResource(Res.string.app_list_restricted_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
