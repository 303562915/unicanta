package io.github.samolego.canta.ui.dialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.samolego.canta.ui.component.CantaDialog

/** Most package names listed in the resume dialog; the rest is summarised. */
private const val MAX_SHOWN = 8

/**
 * Shown once at start when an uninstall batch never finished: the app went away, or the device
 * was unplugged, in the middle of it. The packages below never got an answer — not refused,
 * simply never tried — and the selection that named them died with the screen.
 *
 * Finishing is offered rather than done: apps that were already handled are not on the list, but
 * only the user knows whether the batch was abandoned on purpose.
 */
@Composable
fun ResumeBatchDialog(
    packages: List<String>,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shown = packages.take(MAX_SHOWN)
    val rest = packages.size - shown.size

    CantaDialog(
        onDismissRequest = onDismiss,
        title = "上一次没做完",
        buttons = {
            TextButton(onClick = onDismiss) { Text("算了") }
            TextButton(onClick = onResume) { Text("继续") }
        },
    ) {
        Text(
            text = "上次那批卸载没走完，这 ${packages.size} 个还没动手：" +
                shown.joinToString(", ") + if (rest > 0) ", …" else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "点是就接着删剩下的，已经处理过的不会再来一遍。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}