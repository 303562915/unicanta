package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.plan_upload_back
import io.github.samolego.canta.generated.resources.plan_upload_clear
import io.github.samolego.canta.generated.resources.plan_upload_device_label
import io.github.samolego.canta.generated.resources.plan_upload_name_label
import io.github.samolego.canta.generated.resources.plan_upload_next
import io.github.samolego.canta.generated.resources.plan_upload_no_candidates
import io.github.samolego.canta.generated.resources.plan_upload_note_label
import io.github.samolego.canta.generated.resources.plan_upload_password_label
import io.github.samolego.canta.generated.resources.plan_upload_password_optional
import io.github.samolego.canta.generated.resources.plan_upload_publishing
import io.github.samolego.canta.generated.resources.plan_upload_reboot_ok
import io.github.samolego.canta.generated.resources.plan_upload_select_all
import io.github.samolego.canta.generated.resources.plan_upload_selected
import io.github.samolego.canta.generated.resources.plan_upload_step_details
import io.github.samolego.canta.generated.resources.plan_upload_step_of
import io.github.samolego.canta.generated.resources.plan_upload_step_packages
import io.github.samolego.canta.generated.resources.plan_upload_still_installed
import io.github.samolego.canta.generated.resources.plan_upload_submit
import io.github.samolego.canta.generated.resources.plan_upload_test_hint
import io.github.samolego.canta.generated.resources.plan_upload_test_note
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.util.DeviceIdentity
import io.github.samolego.canta.util.DeviceProfile
import org.jetbrains.compose.resources.stringResource

/** Icon size of a package row inside the dialog: small, so a batch of twenty still fits. */
private val CANDIDATE_ICON = 28.dp

/** How tall the package list of step one may get before it scrolls on its own. */
private val CANDIDATE_LIST_MAX = 260.dp

/**
 * Publishes a set of packages as a community plan, in two steps: pick the packages, then name the
 * plan.
 *
 * A batch that survived a reboot uploads directly. Without one, the test password unlocks the
 * upload anyway: the plan is then stored and labelled as a test, so the flow can be tried before
 * the phone has been restarted.
 *
 * @param candidates every package this phone may publish, in the order they should be listed
 *   (already-removed apps first): the owner sees exactly what a plan would contain.
 * @param preselected the packages that are the point of the upload — usually the removed ones.
 * @param canUpload whether that removal was confirmed by a reboot.
 * @param testMode start with the test password field focused, for the "test upload" row.
 * @param publishing true while the caller is still sending: the dialog stays up, buttons go dead.
 */
@Composable
fun UploadPlanDialog(
    candidates: List<AppInfo>,
    preselected: Set<String>,
    profile: DeviceProfile?,
    canUpload: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (title: String, note: String, packages: List<String>, test: Boolean) -> Unit,
    testMode: Boolean = false,
    publishing: Boolean = false,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    // Order is kept as given: the plan's package list is what the uploader ticked, in list order.
    var selected by remember { mutableStateOf(preselected.toList()) }

    val unlocked = DeviceIdentity.isTestPassword(password)
    val mayPublish = title.isNotBlank() && selected.isNotEmpty() && !publishing && (canUpload || unlocked)

    CantaDialog(
        onDismissRequest = { if (!publishing) onDismiss() },
        widthFraction = WIDE_DIALOG_WIDTH,
        dismissible = !publishing,
        buttons = {
            if (step == 0) {
                TextButton(onClick = onDismiss, enabled = !publishing) {
                    Text(stringResource(Res.string.cancel))
                }
                Button(onClick = { step = 1 }, enabled = selected.isNotEmpty()) {
                    Text(stringResource(Res.string.plan_upload_next))
                }
            } else {
                TextButton(onClick = { step = 0 }, enabled = !publishing) {
                    Text(stringResource(Res.string.plan_upload_back))
                }
                Button(
                    onClick = { onSubmit(title.trim(), note.trim(), selected, !canUpload || unlocked) },
                    enabled = mayPublish,
                ) {
                    if (publishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (publishing) {
                            stringResource(Res.string.plan_upload_publishing)
                        } else {
                            stringResource(Res.string.plan_upload_submit)
                        }
                    )
                }
            }
        },
    ) {
        StepHeader(step)
        Spacer(Modifier.height(14.dp))
        when (step) {
            0 -> PackageStep(
                candidates = candidates,
                total = candidates.size,
                selected = selected,
                onToggle = { pkg ->
                    selected = if (pkg in selected) selected - pkg else selected + pkg
                },
                onSelectAll = { selected = candidates.map { it.packageName } },
                onClear = { selected = emptyList() },
            )
            else -> DetailsStep(
                profile = profile,
                canUpload = canUpload,
                testMode = testMode,
                unlocked = unlocked,
                selectedCount = selected.size,
                totalCount = candidates.size,
                title = title,
                onTitleChange = { title = it },
                note = note,
                onNoteChange = { note = it },
                password = password,
                onPasswordChange = { password = it },
            )
        }
    }
}

/** "1 选包 — 2 写方案": where the flow is, without a stepper widget. */
@Composable
private fun StepHeader(step: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepChip("1", stringResource(Res.string.plan_upload_step_packages), active = step == 0, done = step > 0)
        Spacer(Modifier.width(6.dp))
        Text("—", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(6.dp))
        StepChip("2", stringResource(Res.string.plan_upload_step_details), active = step == 1, done = false)
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(Res.string.plan_upload_step_of, step + 1, 2),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun StepChip(number: String, label: String, active: Boolean, done: Boolean) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        active -> colors.primary
        done -> colors.primary.copy(alpha = 0.18f)
        else -> colors.surfaceContainerHighest
    }
    val content = when {
        active -> colors.onPrimary
        done -> colors.primary
        else -> colors.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = content)
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = content)
    }
}

/** Step one: which packages the plan contains. */
@Composable
private fun PackageStep(
    candidates: List<AppInfo>,
    total: Int,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    val chosen = selected.toSet()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.plan_upload_selected, selected.size, total),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSelectAll) { Text(stringResource(Res.string.plan_upload_select_all), fontSize = 11.sp) }
        TextButton(onClick = onClear) { Text(stringResource(Res.string.plan_upload_clear), fontSize = 11.sp) }
    }

    if (candidates.isEmpty()) {
        Text(
            text = stringResource(Res.string.plan_upload_no_candidates),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = CANDIDATE_LIST_MAX)) {
        items(candidates, key = { it.packageName }) { app ->
            CandidateRow(app = app, checked = app.packageName in chosen, onToggle = { onToggle(app.packageName) })
        }
    }
}

/** One package of step one: tap anywhere on the row to include or drop it. */
@Composable
private fun CandidateRow(app: AppInfo, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        AppIconImage(app, size = CANDIDATE_ICON)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.name,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (app.isSystemApp) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "system",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(
                text = app.packageName,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!app.isUninstalled) {
            // Still installed: the plan would describe a removal that has not happened yet.
            Text(
                text = stringResource(Res.string.plan_upload_still_installed),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Step two: what the phone is, what the plan is called, and whether it counts as verified. */
@Composable
private fun DetailsStep(
    profile: DeviceProfile?,
    canUpload: Boolean,
    testMode: Boolean,
    unlocked: Boolean,
    selectedCount: Int,
    totalCount: Int,
    title: String,
    onTitleChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The device line is the promise of the whole feature: plans only match the same model.
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(Res.string.plan_upload_device_label),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = profile?.shortLabel() ?: "-",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.plan_upload_selected, selectedCount, totalCount),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { if (it.length <= 40) onTitleChange(it) },
            label = { Text(stringResource(Res.string.plan_upload_name_label), fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= 200) onNoteChange(it) },
            label = { Text(stringResource(Res.string.plan_upload_note_label), fontSize = 12.sp) },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(Res.string.plan_upload_password_label), fontSize = 12.sp) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
            isError = testMode && !unlocked && !canUpload,
        )
        Text(
            text = when {
                unlocked -> stringResource(Res.string.plan_upload_test_note)
                canUpload -> stringResource(Res.string.plan_upload_reboot_ok)
                testMode -> stringResource(Res.string.plan_upload_test_hint)
                else -> stringResource(Res.string.plan_upload_password_optional)
            },
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = when {
                unlocked || canUpload -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
