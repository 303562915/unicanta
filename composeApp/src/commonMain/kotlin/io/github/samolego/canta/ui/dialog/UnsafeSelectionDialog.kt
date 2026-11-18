package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.unsafe_blocked_title
import io.github.samolego.canta.generated.resources.unsafe_continue_safe_only
import io.github.samolego.canta.generated.resources.unsafe_device_prefix
import io.github.samolego.canta.generated.resources.unsafe_risky_title
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.unsafe_unknown_note
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.util.SafetyVerdict
import org.jetbrains.compose.resources.stringResource

/**
 * Shown before removing packages that the device-aware rules flag. Critical ones are listed but
 * never removed; the user can continue with everything else. Unlike a generic badge, these rules
 * are matched against the model and ROM version of this particular phone.
 */
@Composable
fun UnsafeSelectionDialog(
    blocked: List<SafetyVerdict>,
    risky: List<SafetyVerdict>,
    deviceLabel: String,
    unknownCount: Int,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = onContinue) { Text(stringResource(Res.string.unsafe_continue_safe_only)) }
        },
    ) {
        val scroll = rememberScrollState()
        Column(modifier = Modifier.verticalScroll(scroll)) {
            if (deviceLabel.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.unsafe_device_prefix, deviceLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (blocked.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.unsafe_blocked_title),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                blocked.forEach { verdict ->
                    Text("• ${verdict.pkg}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (verdict.why.isNotEmpty()) {
                        Text(verdict.why, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (risky.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.unsafe_risky_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                risky.forEach { verdict ->
                    Text("• ${verdict.pkg}", style = MaterialTheme.typography.bodySmall)
                    if (verdict.why.isNotEmpty()) {
                        Text(verdict.why, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (unknownCount > 0) {
                Text(
                    text = stringResource(Res.string.unsafe_unknown_note, unknownCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
