package io.github.samolego.canta.ui.dialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.ok
import io.github.samolego.canta.generated.resources.success
import io.github.samolego.canta.generated.resources.success_reinstalled
import io.github.samolego.canta.generated.resources.success_uninstalled
import io.github.samolego.canta.ui.component.CantaDialog
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SuccessDialog(
    platform: CantaPlatform,
    count: Int,
    isReinstall: Boolean = false,
    onDismissRequest: () -> Unit
) {
    CantaDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(Res.string.success, "🎉"),
        dismissible = false,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.ok)) }
        },
    ) {
        Text(
            text = pluralStringResource(
                if (isReinstall) Res.plurals.success_reinstalled else Res.plurals.success_uninstalled,
                count,
                count
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
