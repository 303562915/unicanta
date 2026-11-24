package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.data.history.RestoreExport
import io.github.samolego.canta.data.history.RestoreMode
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.ui.component.CantaDialog
import org.jetbrains.compose.resources.stringResource

/**
 * 恢复脚本的两个版本，各占一行：记录里的按名字逐个装，全部的交给保底那段自己列。
 * 说清区别比让用户猜「导出全部」是什么意思重要。
 *
 * 设置页和「卸载完成」的弹窗共用这一份：同一个选择出现在两处，措辞就不能有两套。
 */
@Composable
fun RestoreExportDialog(
    onDismiss: () -> Unit,
    onPick: (RestoreMode) -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "导出恢复脚本",
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    ) {
        RestoreChoiceRow(
            title = "最近删除的包名",
            subtitle = "只写 Canta 记录里还没装回来的包，最后再保底全量恢复一次",
            onClick = { onPick(RestoreMode.REMOVED) },
        )
        Spacer(Modifier.height(6.dp))
        RestoreChoiceRow(
            title = "导出全部",
            subtitle = "不看记录：一键还原所有「已卸载但文件还在」的包",
            onClick = { onPick(RestoreMode.ALL) },
        )
    }
}

/** 对话框里的一行可点选项：标题一行、说明一行，整行都是点击区。 */
@Composable
private fun RestoreChoiceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 分享面板 / 落盘之后对用户说的那一句。
 *
 * 三件事要分开说：面板开了没有、脚本里写了几个包、以及没有面板时文件落在哪里——
 * 只回一句「已导出」，人不知道该去哪里点。
 */
fun restoreExportMessage(exported: RestoreExport): String = when {
    exported.shared && exported.mode == RestoreMode.ALL ->
        "已生成一键恢复脚本（全部已卸载的包），选「发送到电脑」即可"

    exported.shared && exported.packages > 0 ->
        "已生成 ${exported.packages} 个包名的恢复脚本，选「发送到电脑」即可"

    exported.shared -> "已生成恢复脚本（记录里没有待恢复的包），选「发送到电脑」即可"

    exported.path != null -> "已保存到 ${exported.path}，拷到电脑上双击运行"

    else -> "这个版本不能导出文件"
}
