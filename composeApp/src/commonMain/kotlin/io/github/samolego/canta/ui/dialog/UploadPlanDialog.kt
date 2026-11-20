package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.plan_upload_candidate_count
import io.github.samolego.canta.generated.resources.plan_upload_clear
import io.github.samolego.canta.generated.resources.plan_upload_device_label
import io.github.samolego.canta.generated.resources.plan_upload_flow_hint
import io.github.samolego.canta.generated.resources.plan_upload_name_label
import io.github.samolego.canta.generated.resources.plan_upload_no_candidates
import io.github.samolego.canta.generated.resources.plan_upload_note_label
import io.github.samolego.canta.generated.resources.plan_upload_publishing
import io.github.samolego.canta.generated.resources.plan_upload_row_awaiting
import io.github.samolego.canta.generated.resources.plan_upload_row_verified
import io.github.samolego.canta.generated.resources.plan_upload_select_all
import io.github.samolego.canta.generated.resources.plan_upload_selected
import io.github.samolego.canta.generated.resources.plan_upload_sheet_title
import io.github.samolego.canta.generated.resources.plan_upload_state_blocked
import io.github.samolego.canta.generated.resources.plan_upload_state_blocked_body
import io.github.samolego.canta.generated.resources.plan_upload_state_partial
import io.github.samolego.canta.generated.resources.plan_upload_state_partial_body
import io.github.samolego.canta.generated.resources.plan_upload_state_ready
import io.github.samolego.canta.generated.resources.plan_upload_state_ready_body
import io.github.samolego.canta.generated.resources.plan_upload_still_installed
import io.github.samolego.canta.generated.resources.plan_upload_submit
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.fl.isDark
import io.github.samolego.canta.ui.fl.successColor
import io.github.samolego.canta.ui.fl.warningColor
import io.github.samolego.canta.util.DeviceProfile
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Icon size of a package row inside the sheet: small, so a batch of twenty still fits. */
private val CANDIDATE_ICON = 28.dp

/** How much of the window the sheet asks for: it is meant to be a page, not a popup. */
private const val SHEET_HEIGHT_FRACTION: Float = 0.92f

/**
 * Whether the batch on screen may be published, as the three colours the sheet draws.
 *
 * The order of the work is the order of this enum: packages are removed, the phone is restarted,
 * the restart check marks what survived — and only then is there a [READY] batch. [PARTIAL] keeps
 * the proven packages uploadable while saying how many are still waiting; [BLOCKED] is a phone
 * that has nothing proven yet, so the sheet explains the steps instead of offering a form.
 */
private enum class GateState { READY, PARTIAL, BLOCKED }

/**
 * Publishes a set of packages as a community plan, as a sheet that comes up from the bottom.
 *
 * The reboot check is the gate, not an afterthought: the sheet opens on the state of the batch
 * (green when it survived a restart, amber while part of it is still waiting, red when nothing has
 * been proven), and only packages that passed the check can be ticked. There is no way around it —
 * a plan nobody restarted the phone for is not a plan anyone should install.
 *
 * @param candidates every package this phone may publish, in the order they should be listed.
 * @param preselected the packages the sheet starts on: the reboot-proven ones.
 * @param verified the packages the reboot check passed; nothing outside this set is tickable.
 * @param publishing true while the caller is still sending: the sheet stays up, buttons go dead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadPlanDialog(
    candidates: List<AppInfo>,
    preselected: Set<String>,
    profile: DeviceProfile?,
    verified: Set<String>,
    onDismiss: () -> Unit,
    onSubmit: (title: String, note: String, packages: List<String>) -> Unit,
    publishing: Boolean = false,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    // Order is kept as given: the plan's package list is what the uploader ticked, in list order.
    var selected by remember { mutableStateOf(preselected.toList()) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close = {
        if (!publishing) {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        }
        Unit
    }

    val uploadable = candidates.filter { it.packageName in verified }
    val gate = when {
        uploadable.isEmpty() -> GateState.BLOCKED
        uploadable.size < candidates.size -> GateState.PARTIAL
        else -> GateState.READY
    }
    // Only proven packages count, whatever was passed in: the tick list is filtered by the check.
    val provenSelection = selected.filter { it in verified }
    val mayPublish = title.isNotBlank() && provenSelection.isNotEmpty() && !publishing

    ModalBottomSheet(
        onDismissRequest = { if (!publishing) onDismiss() },
        sheetState = sheetState,
        containerColor = FlSurfaceLevel.Block.colorOf(MaterialTheme.colorScheme),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(SHEET_HEIGHT_FRACTION)
                .navigationBarsPadding(),
        ) {
            SheetGrip(enabled = !publishing, onClose = close)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.plan_upload_sheet_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                StateChip(gate)
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item("state") { StateCard(gate = gate, awaiting = candidates.size - uploadable.size) }
                item("device") { DeviceCard(profile = profile, totalCount = candidates.size) }
                item("pick") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                Res.string.plan_upload_selected,
                                provenSelection.size,
                                uploadable.size,
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { selected = uploadable.map { it.packageName } },
                            enabled = uploadable.isNotEmpty(),
                        ) {
                            Text(stringResource(Res.string.plan_upload_select_all), fontSize = 11.sp)
                        }
                        TextButton(onClick = { selected = emptyList() }, enabled = selected.isNotEmpty()) {
                            Text(stringResource(Res.string.plan_upload_clear), fontSize = 11.sp)
                        }
                    }
                }
                if (candidates.isEmpty()) {
                    item("empty") {
                        Text(
                            text = stringResource(Res.string.plan_upload_no_candidates),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(candidates, key = { it.packageName }) { app ->
                        CandidateRow(
                            app = app,
                            checked = app.packageName in selected,
                            proven = app.packageName in verified,
                            // Only a package the reboot check passed can be part of the plan: the
                            // row of a waiting one is there to say why it cannot be ticked.
                            onToggle = {
                                if (app.packageName in verified) {
                                    selected = if (app.packageName in selected) {
                                        selected - app.packageName
                                    } else {
                                        selected + app.packageName
                                    }
                                }
                            },
                        )
                    }
                }
                item("name") {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 40) title = it },
                        label = { Text(stringResource(Res.string.plan_upload_name_label), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item("note") {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { if (it.length <= 200) note = it },
                        label = { Text(stringResource(Res.string.plan_upload_note_label), fontSize = 12.sp) },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = close, enabled = !publishing) {
                    Text(stringResource(Res.string.cancel))
                }
                Button(
                    onClick = { onSubmit(title.trim(), note.trim(), provenSelection) },
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
        }
    }
}

/** The sheet's grip: the visual "you can drag this" pill, plus the way out when it cannot be dragged. */
@Composable
private fun SheetGrip(enabled: Boolean, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 40.dp, height = 4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(50),
                ),
        )
        IconButton(
            onClick = onClose,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(32.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(Res.string.cancel),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "重启验证通过" / "部分通过" / "待重启验证", in the colour of that state. */
@Composable
private fun StateChip(gate: GateState) {
    val color = gateColor(gate)
    Text(
        text = stringResource(
            when (gate) {
                GateState.READY -> Res.string.plan_upload_state_ready
                GateState.PARTIAL -> Res.string.plan_upload_state_partial
                GateState.BLOCKED -> Res.string.plan_upload_state_blocked
            }
        ),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** The whole point of the sheet: why this batch may or may not go up, said in one card. */
@Composable
private fun StateCard(gate: GateState, awaiting: Int) {
    val color = gateColor(gate)
    val icon: ImageVector = if (gate == GateState.READY) Icons.Default.Check else Icons.Default.Warning
    val headline = stringResource(
        when (gate) {
            GateState.READY -> Res.string.plan_upload_state_ready
            GateState.PARTIAL -> Res.string.plan_upload_state_partial
            GateState.BLOCKED -> Res.string.plan_upload_state_blocked
        }
    )
    val lines = buildList {
        when (gate) {
            GateState.READY -> add(stringResource(Res.string.plan_upload_state_ready_body))
            GateState.PARTIAL -> {
                add(stringResource(Res.string.plan_upload_state_partial_body, awaiting))
                add(stringResource(Res.string.plan_upload_flow_hint))
            }
            GateState.BLOCKED -> {
                add(stringResource(Res.string.plan_upload_state_blocked_body))
                add(stringResource(Res.string.plan_upload_flow_hint))
            }
        }
    }

    Surface(
        color = color.copy(alpha = if (MaterialTheme.colorScheme.isDark) 0.18f else 0.10f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Box(
                modifier = Modifier.size(20.dp).background(color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = Color(0xFFFFFFFF),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = headline, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
                lines.forEach { line ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = line,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The device line is the promise of the whole feature: plans only match the same model. */
@Composable
private fun DeviceCard(profile: DeviceProfile?, totalCount: Int) {
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
                text = stringResource(Res.string.plan_upload_candidate_count, totalCount),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One package: tickable when the reboot check passed, state chip at the end of the row. */
@Composable
private fun CandidateRow(app: AppInfo, checked: Boolean, proven: Boolean, onToggle: () -> Unit) {
    val tint = when {
        proven -> successColor()
        app.isUninstalled -> warningColor()
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = proven)
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
                    Text(text = "system", fontSize = 8.sp, color = MaterialTheme.colorScheme.tertiary)
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
        Text(
            text = when {
                proven -> stringResource(Res.string.plan_upload_row_verified)
                app.isUninstalled -> stringResource(Res.string.plan_upload_row_awaiting)
                else -> stringResource(Res.string.plan_upload_still_installed)
            },
            fontSize = 9.sp,
            color = tint,
        )
    }
}

/** Green when the batch may go up, amber while it is waiting, red when nothing is proven. */
@Composable
private fun gateColor(gate: GateState): Color = when (gate) {
    GateState.READY -> successColor()
    GateState.PARTIAL -> warningColor()
    GateState.BLOCKED -> MaterialTheme.colorScheme.error
}