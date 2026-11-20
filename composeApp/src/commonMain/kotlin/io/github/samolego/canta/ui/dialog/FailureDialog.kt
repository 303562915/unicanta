package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.ok
import io.github.samolego.canta.ui.component.CantaDialog
import org.jetbrains.compose.resources.stringResource

/**
 * Why a batch did nothing at all — the case the app used to answer with silence.
 *
 * One block per distinct reason: a batch of ten usually fails for one cause, and repeating the
 * same sentence ten times buries it. Each block is the device's refusal in words that can be acted
 * on, then the next step worth trying.
 *
 * @param attempts how many packages the batch tried in total.
 * @param reasons what the device said and what to do about it, already distinct.
 * @param isReinstall whether the batch was putting apps back rather than removing them.
 */
@Composable
fun FailureDialog(
    attempts: Int,
    reasons: List<Pair<String, String>>,
    isReinstall: Boolean,
    onDismissRequest: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismissRequest,
        title = if (isReinstall) "没能恢复" else "没能卸载",
        dismissible = false,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.ok)) }
        },
    ) {
        Column {
            Text(
                text = if (attempts == 1) {
                    "尝试了 1 个应用，设备没有同意。"
                } else {
                    "尝试了 $attempts 个应用，设备一个都没有同意。"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            reasons.forEach { (reason, advice) ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (advice.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = advice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "详细过程都在「记录」里，失败项带着原话。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}