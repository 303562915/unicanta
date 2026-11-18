package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.app_list_restricted_description
import io.github.samolego.canta.generated.resources.app_list_restricted_dismiss
import io.github.samolego.canta.generated.resources.app_list_restricted_open_settings
import io.github.samolego.canta.generated.resources.app_list_restricted_title
import org.jetbrains.compose.resources.stringResource

/**
 * Shown above the list when the ROM would not hand over its packages: HyperOS and MIUI keep a
 * "get installed apps" permission for that, off by default and impossible to request at runtime,
 * so the user has to flip it in the system settings themselves.
 */
@Composable
fun AppListPermissionBanner(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(Res.string.app_list_restricted_title),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(Res.string.app_list_restricted_description),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.app_list_restricted_dismiss))
                }
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(Res.string.app_list_restricted_open_settings))
                }
            }
        }
    }
}
