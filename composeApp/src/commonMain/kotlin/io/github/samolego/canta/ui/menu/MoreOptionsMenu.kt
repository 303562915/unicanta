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
import io.github.samolego.canta.ui.navigation.Screen

/**
 * 应用页右上角那个「更多」。
 *
 * 只留两样：标记说明与日志。**设置与预设都已撤掉**——它们各自有正经入口（设置从底栏「我的」
 * 进，预设从设置页进），放在这个下拉里等于同一页两处门。之前的「我的」也是同一个理由撤的。
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
    }
}