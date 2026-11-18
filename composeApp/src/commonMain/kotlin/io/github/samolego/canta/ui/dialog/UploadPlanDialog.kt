package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.plan_upload_name_hint
import io.github.samolego.canta.generated.resources.plan_upload_need_reboot
import io.github.samolego.canta.generated.resources.plan_upload_note_hint
import io.github.samolego.canta.generated.resources.plan_upload_submit
import io.github.samolego.canta.generated.resources.plan_upload_title
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.util.DeviceIdentity
import org.jetbrains.compose.resources.stringResource

/**
 * Publishes a set of packages as a community plan.
 *
 * A batch that survived a reboot uploads directly. Without one, the test password unlocks the
 * upload anyway: the plan is then stored and labelled as a test, so the flow can be tried before
 * the phone has been restarted.
 *
 * @param entries the apps this phone has already removed, name to package; fixed, the owner only
 *   names and describes the plan.
 * @param canUpload whether that removal was confirmed by a reboot.
 * @param testMode start with the test password field focused, for the "test upload" row.
 */
@Composable
fun UploadPlanDialog(
    entries: List<Pair<String, String>>,
    canUpload: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (title: String, note: String, test: Boolean) -> Unit,
    testMode: Boolean = false,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val unlocked = DeviceIdentity.isTestPassword(password)
    val maySubmit = (canUpload || unlocked) && title.isNotBlank()

    CantaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.plan_upload_title),
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            Button(
                onClick = { onSubmit(title.trim(), note.trim(), !canUpload || unlocked) },
                enabled = maySubmit,
            ) {
                Text(stringResource(Res.string.plan_upload_submit))
            }
        },
    ) {
        // The plan *is* the list of apps this phone no longer has, so show that list rather than a
        // bare count: the uploader has to recognise what is being published.
        Text(
            text = if (entries.isEmpty()) {
                "本机还没有已删除的应用，先卸载几个再来"
            } else {
                "将上传这 ${entries.size} 个已删除的应用"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (entries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 168.dp).verticalScroll(rememberScrollState())) {
                entries.forEach { (name, pkg) ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pkg,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider()
        }
        if (!canUpload) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.plan_upload_need_reboot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text(stringResource(Res.string.plan_upload_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text(stringResource(Res.string.plan_upload_note_hint)) },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("测试密码（免重启）", fontSize = 12.sp) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                unlocked && !canUpload -> "密码正确：这次上传不需要重启，方案会标成「测试」。"
                unlocked -> "密码正确：本次按测试方案上传。"
                testMode -> "没有重启验证的批次要填写测试密码才能上传。"
                else -> "测试密码是可选的；填对了就不用等重启验证。"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
