package io.github.samolego.canta.ui.menu

import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.badge_info
import io.github.samolego.canta.generated.resources.logs
import io.github.samolego.canta.generated.resources.presets
import io.github.samolego.canta.generated.resources.settings
import io.github.samolego.canta.ui.navigation.Screen

/**
 * 应用页右上角那个「更多」。
 *
 * 「我的」那一行已经撤了：底栏最后一格本来就是「我的」，从这一页再放一个入口等于两处通同一个
 * 地方。这里只留那些**不挂在底栏**的页：标记说明、日志、设置、预设。
 */
@Composable
fun MoreOptionsMenu(
        showMenu: Boolean,
        showBadgeInfoDialog: () -> Unit,
        navigateToPage: (route: String) -> Unit,
        onDismiss: () -> Unit,
) {

    DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismiss,
            modifier = Modifier.width(200.dp)
    ) {
        // Badge info dialog
        DropdownMenuItem(
                text = { Text(stringResource(Res.string.badge_info)) },
                onClick = {
                    showBadgeInfoDialog()
                    onDismiss()
                }
        )

        // Logs page
        DropdownMenuItem(
                text = { Text(stringResource(Res.string.logs)) },
                onClick = {
                    navigateToPage(Screen.Logs.route)
                    onDismiss()
                }
        )

        // 设置：开关都在这里，布局照关于页。
        DropdownMenuItem(
                text = { Text(stringResource(Res.string.settings)) },
                onClick = {
                    navigateToPage(Screen.Settings.route)
                    onDismiss()
                }
        )

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.presets)) },
            onClick = {
                navigateToPage(Screen.Presets.route)
                onDismiss()
            }
        )
    }
}