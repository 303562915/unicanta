package io.github.samolego.canta.ui.dialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.restore_suspects_description
import io.github.samolego.canta.generated.resources.restore_suspects_dismiss
import io.github.samolego.canta.generated.resources.restore_suspects_revert
import io.github.samolego.canta.generated.resources.restore_suspects_title
import io.github.samolego.canta.ui.component.CantaDialog
import org.jetbrains.compose.resources.stringResource

/** Most package names listed in the rescue dialog; the rest is summarised. */
private const val MAX_SHOWN = 8

/**
 * Shown once at start when the last uninstall batch had to be put back by hand: the packages are
 * installed again although the system build did not change, which nothing but a person can have
 * done. Offers to put the whole batch back, since a batch that killed the launcher is not worth
 * keeping — and the user already had to rescue it.
 */
@Composable
fun RestoreSuspectsDialog(
    packages: List<String>,
    onRevert: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shown = packages.take(MAX_SHOWN)
    val rest = packages.size - shown.size

    CantaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.restore_suspects_title),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.restore_suspects_dismiss)) }
            if (packages.isNotEmpty()) {
                TextButton(onClick = onRevert) { Text(stringResource(Res.string.restore_suspects_revert)) }
            }
        },
    ) {
        Text(
            text = stringResource(
                Res.string.restore_suspects_description,
                shown.joinToString(", ") + if (rest > 0) ", …" else "",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
