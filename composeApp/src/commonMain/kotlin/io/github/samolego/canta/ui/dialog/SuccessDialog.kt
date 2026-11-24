package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.ok
import io.github.samolego.canta.generated.resources.success
import io.github.samolego.canta.generated.resources.success_disabled
import io.github.samolego.canta.generated.resources.success_enabled
import io.github.samolego.canta.generated.resources.success_reinstalled
import io.github.samolego.canta.generated.resources.success_uninstalled
import io.github.samolego.canta.ui.component.CantaDialog
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SuccessDialog(
    platform: CantaPlatform,
    count: Int,
    /** What the batch did, which is what the line below says it did. */
    kind: OperationKind,
    onDismissRequest: () -> Unit,
    /**
     * Opens the restore-script picker. Only an uninstall passes it: putting packages back is a
     * reinstall, and freezing is undone by one tap — neither leaves the phone needing a rescue file.
     */
    onExportRestore: (() -> Unit)? = null,
) {
    CantaDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(Res.string.success, "🎉"),
        dismissible = false,
        scrollable = true,
        buttons = {
            if (onExportRestore != null) {
                TextButton(onClick = onExportRestore) { Text("导出恢复脚本") }
            }
            TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.ok)) }
        },
    ) {
        Text(
            text = pluralStringResource(
                when (kind) {
                    OperationKind.UNINSTALL -> Res.plurals.success_uninstalled
                    OperationKind.REINSTALL -> Res.plurals.success_reinstalled
                    OperationKind.DISABLE -> Res.plurals.success_disabled
                    OperationKind.ENABLE -> Res.plurals.success_enabled
                },
                count,
                count
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        // 卸载之后、重启之前：这一刻手机看起来完全正常，真正出问题的是下一次开机。
        // 所以清单放在成功提示里——人刚删完东西、正准备重启，是唯一会读它的时候。
        if (onExportRestore != null) {
            val colors = MaterialTheme.colorScheme
            Spacer(Modifier.height(12.dp))
            Text(
                text = "重启前先留好退路",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Text(
                text = "· 保持 USB 调试开启：插上数据线，电脑上 `adb devices` 能列出这台手机",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = "· 点下面「导出恢复脚本」，把这个 .bat 发到电脑上（微信文件传输助手、QQ 都行）",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = "· 记住这次删了哪些包：记录页随时能查，脚本里也写着它们",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = "现在正常不代表重启后还正常；脚本没发出去就重启，起不来时能救回来的路通常只有刷机。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
            )
        }
    }
}
