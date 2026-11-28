package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.history.BatchRollback
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.data.history.OperationLogStore
import io.github.samolego.canta.data.history.OperationRecord
import io.github.samolego.canta.data.history.OperationTime
import io.github.samolego.canta.data.history.rollbackCandidate
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.records_action_disable
import io.github.samolego.canta.generated.resources.records_action_enable
import io.github.samolego.canta.generated.resources.records_action_reinstall
import io.github.samolego.canta.generated.resources.records_action_uninstall
import io.github.samolego.canta.generated.resources.records_batch
import io.github.samolego.canta.generated.resources.records_clear_after_boot
import io.github.samolego.canta.generated.resources.records_empty
import io.github.samolego.canta.generated.resources.records_failed
import io.github.samolego.canta.generated.resources.records_reset_to_factory
import io.github.samolego.canta.generated.resources.records_success
import io.github.samolego.canta.generated.resources.records_tap_opens_detail
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.SettingsSwitch
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.fl.successColor
import org.jetbrains.compose.resources.stringResource

/** Room the list keeps at its foot for the floating bar drawn over the page. */
private val FLOATING_BAR_ROOM = 96.dp

/** The icon the rows are drawn at: the same size as a tile's, small enough for a log line. */
private val ROW_ICON = 34.dp

/**
 * The middle zone between the two app lists: the log of everything Canta did.
 *
 * Every uninstall and restore is recorded as it happens — there is nothing to save — and the
 * same log is mirrored to a CSV in the download folder for anyone who wants to analyse it.
 *
 * One row per app: only where an app stands *now* is worth a glance, so the newest operation on
 * a package hides the ones before it. Tapping a row does the opposite of what it shows — an
 * uninstall is a one-tap restore, a restore is a one-tap uninstall — unless the second switch at
 * the top of the page is on, in which case the tap opens the app's own page and the decision is
 * made there.
 */
@Composable
fun RecordsScreen(
    history: OperationLogStore,
    apps: List<AppInfo>,
    onReverse: (OperationRecord) -> Unit,
    modifier: Modifier = Modifier,
    /** Undoes a whole batch in one tap: the newest removal or freeze that is still in effect. */
    onRollbackBatch: (BatchRollback) -> Unit = {},
    /** Whether the log is dropped at the next system start; the first switch at the top of the page. */
    clearRecordsAfterBoot: Boolean = false,
    onClearRecordsAfterBootChange: (Boolean) -> Unit = {},
    /**
     * 点一行时先开那个包的详情页（`true`），还是照旧直接做反向操作（`false`，出厂）。
     * 第二个开关读的就是它；两个方向都要留，因为"批量删完回头看"和"看一眼就装回来"是两种用法。
     */
    recordTapOpensDetail: Boolean = false,
    onRecordTapOpensDetailChange: (Boolean) -> Unit = {},
    /** Opens the app page of a record's package; only called while [recordTapOpensDetail] is on. */
    onOpenApp: (String) -> Unit = {},
    /** 清空整份记录：blob 与下载目录里那份 CSV 一起清，页面底部那颗按钮问过之后才调。 */
    onClearRecords: () -> Unit = {},
) {
    val records by history.records.collectAsStateWithLifecycle()

    // The store keeps the log in a blob and only reads it on demand: do that once, so the
    // records from earlier runs are there on the first look instead of an empty page.
    LaunchedEffect(history) { history.ensureReady() }

    // Icons come from the apps the list already loaded: a log line names a package, and the
    // package is what the list is keyed by.
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }

    // Which cut of the log is on screen: the four pills under the switch.
    var filter by remember { mutableStateOf(RecordFilter.ALL) }

    // Whether the 「清空」 confirmation is up. Clearing is not undoable, and the log is the only
    // way back to what was removed — so the icon asks once before it throws that away.
    var confirmClear by remember { mutableStateOf(false) }

    // Newest first already, so the first record of a package is the one that describes it now.
    val latest = remember(records) { records.distinctBy { it.packageName } }

    // Whether a package is gone right now, asked of the list rather than of the log: a later
    // operation on the same package is what decides, and the log does not say which came last.
    val gone = remember(apps) { apps.filter { it.isUninstalled }.map { it.packageName }.toSet() }
    // The same for a freeze: an app thawed by hand after the batch is not part of the undo any
    // more, so the banner counts what is still frozen instead of what the batch froze.
    val frozen = remember(apps) { apps.filter { it.isDisabled }.map { it.packageName }.toSet() }
    val rollback = remember(records, gone, frozen) {
        records.rollbackCandidate(currentlyUninstalled = { it in gone }, currentlyDisabled = { it in frozen })
    }

    // The pills cut the log before it is laid out, so the day headers describe what is left.
    val shown = remember(latest, filter) { latest.filter { filter.matches(it) } }

    // One flat list of day headers and rows: a lazy list cannot nest one inside another.
    val entries = remember(shown) { flattenByDay(shown) }

    Column(modifier = modifier.fillMaxSize()) {
        // Above the log and outside the empty check on purpose: with the switches inside the list they
        // would disappear the moment the log emptied the page, leaving no way to turn them off again.
        RecordSettingsCard(
            clearAfterBoot = clearRecordsAfterBoot,
            onClearAfterBootChange = onClearRecordsAfterBootChange,
            tapOpensDetail = recordTapOpensDetail,
            onTapOpensDetailChange = onRecordTapOpensDetailChange,
        )
        RecordFilters(
            selected = filter,
            onSelect = { filter = it },
            canClear = latest.isNotEmpty(),
            onClear = { confirmClear = true },
        )
        if (shown.isEmpty()) {
            EmptyRecords(modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 13.dp,
                    end = 13.dp,
                    top = 6.dp,
                    bottom = FLOATING_BAR_ROOM,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rollback?.let { batch ->
                    item(key = "rollback-${batch.batchId}") {
                        RollbackBanner(batch = batch, onRollback = { onRollbackBatch(batch) })
                    }
                }
                items(entries, key = RecordEntry::key) { entry ->
                    when (entry) {
                        is RecordEntry.Day -> DayHeader(entry.day)
                        is RecordEntry.Operation -> RecordRow(
                            record = entry.record,
                            app = byPackage[entry.record.packageName],
                            onReverse = { onReverse(entry.record) },
                            onOpen = { onOpenApp(entry.record.packageName) },
                            openDetailOnTap = recordTapOpensDetail,
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ClearRecordsDialog(
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                onClearRecords()
            },
        )
    }
}

/**
 * The page's two settings as two small switches side by side, left and right, one line high.
 *
 * They used to be two full-width rows with a caption each, which ate a whole card's height above
 * the log for two things nobody reads twice. Now each is a chip: the words on the left, a small
 * switch on the right, half the row each — the page opens on the records themselves.
 *
 * Left: whether the log is thrown away at the next system start. Off is the default, and off means
 * the log is kept for good — a record of what was removed is the only way back to it.
 *
 * Right: what a tap on a row does. Off (the default) is the behaviour the page always had — one tap
 * is the reverse operation — and on opens the package's own page first.
 */
@Composable
private fun RecordSettingsCard(
    clearAfterBoot: Boolean,
    onClearAfterBootChange: (Boolean) -> Unit,
    tapOpensDetail: Boolean,
    onTapOpensDetailChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 13.dp, end = 13.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RecordSettingChip(
            label = stringResource(Res.string.records_clear_after_boot),
            checked = clearAfterBoot,
            onCheckedChange = onClearAfterBootChange,
            modifier = Modifier.weight(1f),
        )
        RecordSettingChip(
            label = stringResource(Res.string.records_tap_opens_detail),
            checked = tapOpensDetail,
            onCheckedChange = onTapOpensDetailChange,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One switch of [RecordSettingsCard]: a one-line label and a small switch, all inside the chip. */
@Composable
private fun RecordSettingChip(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        shape = RoundedCornerShape(9.dp),
        color = FlSurfaceLevel.Card.colorOf(scheme),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = FlText.caption1,
                color = if (checked) scheme.primary else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SettingsSwitch(checked)
        }
    }
}

/**
 * The four cuts of the log: everything, what was put back on the device, what was taken off it, and
 * what was frozen. A freeze is not a removal — the app is still there — so it has its own cut
 * rather than being counted among the removals; thawing it again stays in that same cut, because a
 * freeze and its undo are one story told twice.
 */
private enum class RecordFilter(val label: String) {
    ALL("全部"),

    /** The pill says 恢复, not 安装: the app is put back the way it was, not installed anew. */
    INSTALL("恢复"),
    UNINSTALL("卸载"),
    DISABLE("停用");

    fun matches(record: OperationRecord): Boolean = when (this) {
        ALL -> true
        INSTALL -> record.kind == OperationKind.REINSTALL
        UNINSTALL -> record.kind == OperationKind.UNINSTALL
        DISABLE -> record.kind == OperationKind.DISABLE || record.kind == OperationKind.ENABLE
    }
}

/**
 * The pills that cut the log. One tap each, so the row is short and stays put: switching between
 * "what I removed" and "what I froze" must cost no more than a look.
 *
 * The bin at the far right belongs to the same row because that is where the reader's eye already
 * is: the filters pick what to look at, the bin throws all of it away. It greys out when there is
 * nothing to clear instead of vanishing, so the row does not change width under the finger.
 */
@Composable
private fun RecordFilters(
    selected: RecordFilter,
    onSelect: (RecordFilter) -> Unit,
    canClear: Boolean,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 13.dp, end = 13.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordFilter.entries.forEach { cut ->
            val on = cut == selected
            val tint = if (on) scheme.primary else scheme.onSurfaceVariant
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(role = Role.Tab, onClick = { onSelect(cut) }),
                shape = RoundedCornerShape(9.dp),
                color = if (on) tint.copy(alpha = 0.16f) else FlSurfaceLevel.Card.colorOf(scheme),
            ) {
                Text(
                    text = cut.label,
                    style = FlText.caption1.copy(
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = tint,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        val clearTint = if (canClear) scheme.onSurfaceVariant else scheme.onSurfaceVariant.copy(alpha = 0.35f)
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable(enabled = canClear, role = Role.Button, onClick = onClear),
            shape = RoundedCornerShape(9.dp),
            color = FlSurfaceLevel.Card.colorOf(scheme),
        ) {
            Box(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
                FlGlyphIcon(glyph = FlGlyphs.delete, size = 15.dp, tint = clearTint, fill = 0f)
            }
        }
    }
}

/**
 * 「清空记录」asks first: emptying the log is the one action on this page that cannot be taken back
 * — the record of what was removed is the only way back to it.
 */
@Composable
private fun ClearRecordsDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "清空记录？",
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(onClick = onConfirm) { Text("清空", color = scheme.error) }
        },
    ) {
        Text(
            text = "这一页的卸载 / 恢复 / 停用记录会全部清掉，下载目录里那份 canta-operations.csv 也一起清空。" +
                "清掉之后就查不到删过哪些包，也没法再按记录一个个装回来。",
            style = FlText.footnote,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * The one-tap way out of a batch: undoing ten removals by tapping ten rows is not an undo.
 *
 * It only appears for a batch that can actually be undone — one whose apps are still in the state
 * it left them in — so the button is never there to fail. The words follow the batch: an uninstall
 * batch is put back, a freeze batch is thawed, and a freeze is never called a reinstall.
 */
@Composable
private fun RollbackBanner(batch: BatchRollback, onRollback: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val isFreeze = batch.kind == OperationKind.DISABLE
    // A removal is the loud one (primary); a freeze is a pause (tertiary), the same way the rows
    // colour it, because the app is still on the device.
    val accent = if (isFreeze) scheme.tertiary else scheme.primary
    val title = if (isFreeze) "把这一批全部启用" else "把这一批全部装回来"
    val body = if (isFreeze) {
        "这一批停用了 ${batch.size} 个，还都没启用"
    } else {
        "这一批删了 ${batch.size} 个，还都没回来"
    }
    val action = if (isFreeze) "全部启用" else "一键恢复"
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onRollback),
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = FlText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = accent,
                )
                Text(
                    text = body,
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = action,
                style = FlText.caption2.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
            )
        }
    }
}

/** A row of the records list: the day a group starts, or one operation. */
private sealed interface RecordEntry {
    val key: String

    data class Day(val day: String) : RecordEntry {
        override val key: String get() = "day-$day"
    }

    data class Operation(val record: OperationRecord) : RecordEntry {
        override val key: String
            get() = "${record.timestamp}-${record.packageName}-${record.batchIndex}"
    }
}

/** The log as it is drawn: newest day first, a header before each day's operations. */
private fun flattenByDay(records: List<OperationRecord>): List<RecordEntry> = buildList {
    var day = ""
    records.forEach { record ->
        val recordDay = OperationTime.day(record.timestamp)
        if (recordDay != day) {
            day = recordDay
            add(RecordEntry.Day(recordDay))
        }
        add(RecordEntry.Operation(record))
    }
}

@Composable
private fun DayHeader(day: String) {
    Text(
        text = day,
        style = FlText.caption1.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 1.dp),
    )
}

/**
 * One operation: the app's icon and name, then when it happened and how it went. The batch is
 * only mentioned when it had more than one app in it, since "1 of 1" says nothing.
 *
 * The row is a button for the opposite operation: what is gone can be put back with one tap, and
 * what was put back can be removed again the same way.
 */
@Composable
private fun RecordRow(
    record: OperationRecord,
    app: AppInfo?,
    onReverse: () -> Unit,
    onOpen: () -> Unit,
    openDetailOnTap: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    // What happened and how it went, in one word: "停用成功", "启用失败". The row no longer spells
    // out what a tap will do underneath — the tap is the action and the toast is the answer, so a
    // second label under the time was one thing to read too many.
    val action = stringResource(
        when (record.kind) {
            OperationKind.UNINSTALL -> Res.string.records_action_uninstall
            OperationKind.REINSTALL -> Res.string.records_action_reinstall
            OperationKind.DISABLE -> Res.string.records_action_disable
            OperationKind.ENABLE -> Res.string.records_action_enable
        }
    )
    val outcome = action + stringResource(
        if (record.success) Res.string.records_success else Res.string.records_failed
    )
    // Green when it took, red when the device refused: the colour is the outcome, not the verb.
    val outcomeColor = if (record.success) successColor() else scheme.error

    // The name the row leads with. Some records carry no label at all — written for a package the
    // list could not name, or for one whose own label is an empty string — and a row whose first
    // line is blank is a row that says nothing. So the package name takes its place, and the line
    // under it is only drawn when it still adds something.
    val title = record.label.ifBlank { app?.name.orEmpty() }.ifBlank { record.packageName }
    val subtitle = record.packageName.takeIf { it != title }.orEmpty()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = if (openDetailOnTap) onOpen else onReverse),
        shape = RoundedCornerShape(12.dp),
        color = FlSurfaceLevel.Card.colorOf(scheme),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordIcon(app)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = FlText.subheadline,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = FlText.caption2.copy(fontFamily = FontFamily.Monospace),
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = OperationTime.clock(record.timestamp),
                        style = FlText.caption2.copy(fontFamily = FontFamily.Monospace),
                        color = scheme.onSurfaceVariant,
                    )
                    // One chip for the whole outcome, painted green when it took and red when the
                    // device refused. Nothing under it: the tap is the reverse, the toast is the
                    // answer.
                    Text(
                        text = outcome,
                        style = FlText.caption2.copy(fontWeight = FontWeight.SemiBold),
                        color = outcomeColor,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(outcomeColor.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }

            // The failure, said in two parts: what the device refused, and what to try next.
            // Records written before the explanation existed still have their raw message, so
            // that is what they fall back to.
            if (record.reason.isNotEmpty() || record.message.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = record.reason.ifEmpty { record.message },
                    style = FlText.caption2,
                    color = scheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = ROW_ICON + 10.dp),
                )
                if (record.advice.isNotEmpty()) {
                    Text(
                        text = record.advice,
                        style = FlText.caption2,
                        color = scheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = ROW_ICON + 10.dp, top = 1.dp),
                    )
                }
            }

            // Anything beyond "one app, done" only shows when it is actually there.
            val batchNote =
                if (record.batchSize > 1) {
                    stringResource(Res.string.records_batch, record.batchIndex, record.batchSize)
                } else {
                    ""
                }
            val resetNote =
                if (record.resetToFactory) stringResource(Res.string.records_reset_to_factory) else ""
            // What the package was when it left: the version, and who put it there. A log that
            // says only the package name cannot be acted on a month later.
            val snapshotNote = record.snapshot?.let { snapshot ->
                if (snapshot.isBare) "" else snapshot.labelWithVersion
            }.orEmpty()
            val installerNote = record.snapshot?.installerPackage
                ?.takeIf { it.isNotEmpty() }
                ?.let { "来自 $it" }
                .orEmpty()
            val notes = listOf(batchNote, snapshotNote, installerNote, resetNote)
                .filter { it.isNotEmpty() }
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = notes.joinToString(" · "),
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = ROW_ICON + 10.dp),
                )
            }
        }
    }
}

/** The app's own icon, or a neutral tile when the package is not in the list any more. */
@Composable
private fun RecordIcon(app: AppInfo?) {
    val scheme = MaterialTheme.colorScheme
    if (app != null) {
        AppIconImage(app, size = ROW_ICON)
        return
    }
    Box(
        modifier = Modifier
            .size(ROW_ICON)
            .clip(RoundedCornerShape(9.dp))
            .background(scheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        FlGlyphIcon(glyph = FlGlyphs.appsList, size = 16.dp, tint = scheme.onSurfaceVariant, fill = 0f)
    }
}

/** The page before there is anything to log. */
@Composable
private fun EmptyRecords(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.records_empty),
            style = FlText.footnote,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
