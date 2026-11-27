package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl

/** 赞助形式：值 → 页面上的名字，顺序就是选择器的顺序（与后端 `sponsor_add` 的归一化一致）。 */
private val SponsorKinds = listOf(
    "money" to "打赏金额",
    "ai" to "AI 邀请",
)

/**
 * 「我也赞助」那张表单：形式（打赏金额 / AI 邀请）、金额、一句话说明、匿名开关。
 *
 * 这里不接支付：钱是在群里/私聊里收的，这张表只是把它记进榜。所以按钮写「记上」而不是「支付」，
 * 免得人以为点一下就付了钱。
 */
@Composable
internal fun SponsorComposerDialog(
    submitting: Boolean,
    error: String?,
    defaultName: String,
    onDismiss: () -> Unit,
    onSubmit: (amount: Int, kind: String, note: String, anonymous: Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var kindIndex by remember { mutableStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var anonymous by remember { mutableStateOf(false) }

    val measured = amount.toIntOrNull() ?: 0
    val ready = if (SponsorKinds[kindIndex].first == "money") measured > 0 else true

    CantaDialog(
        onDismissRequest = onDismiss,
        title = "我也赞助",
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
            Button(
                enabled = ready && !submitting,
                onClick = {
                    onSubmit(measured, SponsorKinds[kindIndex].first, note.trim(), anonymous)
                },
            ) { Text(if (submitting) "正在记…" else "记上") }
        },
    ) {
        error?.let { line ->
            Text(text = line, style = MaterialTheme.typography.labelSmall, color = scheme.error)
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            text = "所有赞助都用在服务器建设和 token 采购上，支撑软件一直开发下去。" +
                "记上之后名字会出现在榜上；不想露名字就打开匿名。",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        FlSegmentedControl(
            segments = SponsorKinds.map { FlSegment(it.first, it.second) },
            selectedIndex = kindIndex,
            onSelect = { kindIndex = it },
            insetHorizontal = 0.dp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (SponsorKinds[kindIndex].first == "money") {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() }.take(7) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("金额（元）") },
                placeholder = { Text("20") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
        } else {
            Text(
                text = "AI 邀请：邀请一个人来用这个软件，不涉及钱，所以榜上记一笔「邀请」。",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(200) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("一句话（可不填）") },
            placeholder = { Text("这笔钱想用在哪儿") },
            minLines = 2,
            maxLines = 3,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "匿名赞助",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
                Text(
                    text = if (defaultName.isBlank()) "榜上显示「匿名」" else "榜上显示「匿名」，不显示 $defaultName",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = anonymous, onCheckedChange = { anonymous = it })
        }
    }
}
