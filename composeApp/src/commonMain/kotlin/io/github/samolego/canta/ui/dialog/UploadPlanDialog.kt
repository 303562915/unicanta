package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.plan_upload_name_hint
import io.github.samolego.canta.generated.resources.plan_upload_need_reboot
import io.github.samolego.canta.generated.resources.plan_upload_note_hint
import io.github.samolego.canta.generated.resources.plan_upload_submit
import io.github.samolego.canta.generated.resources.plan_upload_title
import io.github.samolego.canta.ui.component.CantaDialog
import org.jetbrains.compose.resources.stringResource

/**
 * Publishes this phone's reboot-verified batch as a community plan.
 *
 * @param packages the verified packages; fixed, the owner only names and describes the plan.
 * @param canUpload false when the pending batch has not survived a reboot yet, which is the only
 *   thing that makes a shared list worth anything.
 */
@Composable
fun UploadPlanDialog(
    packages: List<String>,
    canUpload: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (title: String, note: String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    CantaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.plan_upload_title),
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            Button(
                onClick = { onSubmit(title.trim(), note.trim()) },
                enabled = canUpload && title.isNotBlank(),
            ) {
                Text(stringResource(Res.string.plan_upload_submit))
            }
        },
    ) {
        // No string key carries the count, and the number is the only variable part.
        Text(
            text = "${packages.size} 个包",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!canUpload) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.plan_upload_need_reboot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text(stringResource(Res.string.plan_upload_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text(stringResource(Res.string.plan_upload_note_hint)) },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
