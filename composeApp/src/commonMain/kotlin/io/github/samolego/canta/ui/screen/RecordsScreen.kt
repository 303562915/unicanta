package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.data.history.OperationLogStore
import io.github.samolego.canta.data.history.OperationRecord
import io.github.samolego.canta.data.history.OperationTime
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.records_action_reinstall
import io.github.samolego.canta.generated.resources.records_action_uninstall
import io.github.samolego.canta.generated.resources.records_batch
import io.github.samolego.canta.generated.resources.records_empty
import io.github.samolego.canta.generated.resources.records_failed
import io.github.samolego.canta.generated.resources.records_hint
import io.github.samolego.canta.generated.resources.records_reset_to_factory
import io.github.samolego.canta.generated.resources.records_save
import io.github.samolego.canta.generated.resources.records_saved
import io.github.samolego.canta.generated.resources.records_success
import io.github.samolego.canta.generated.resources.tab_records
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.ui.fl.successColor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Room the list keeps at its foot for the floating bar drawn over the page. */
private val FLOATING_BAR_ROOM = 96.dp

/**
 * The middle zone between the two app lists: the log of everything Canta did.
 *
 * Every uninstall and restore is written here with the moment it happened and whether it
 * worked, and the same log is mirrored to a CSV in the download folder so it can be pulled
 * off the device and analysed later. Records are grouped by the day they happened.
 */
@Composable
fun RecordsScreen(
    history: OperationLogStore,
    platform: CantaPlatform,
    modifier: Modifier = Modifier,
) {
    val records by history.records.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // The store keeps the log in a blob and only reads it on demand: do that once, so the
    // records from earlier runs are there on the first look instead of an empty page.
    LaunchedEffect(history) { history.ensureReady() }

    val title = stringResource(Res.string.tab_records)
    val hint = stringResource(Res.string.records_hint)
    val save = stringResource(Res.string.records_save)
    val saved = stringResource(Res.string.records_saved)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = FlText.headline, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = hint,
                    style = FlText.caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        // null where the platform has no download folder (the web build).
                        if (history.export() != null) platform.showMessage(saved)
                    }
                },
                enabled = records.isNotEmpty(),
            ) {
                Text(save, style = FlText.caption1)
            }
        }

        if (records.isEmpty()) {
            EmptyRecords(modifier = Modifier.fillMaxSize())
        } else {
            // One flat list of day headers and rows: a lazy list cannot nest one inside another.
            val entries = remember(records) { flattenByDay(records) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Room for the floating bar, inside the list rather than around the page.
                contentPadding = PaddingValues(bottom = FLOATING_BAR_ROOM),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = RecordEntry::key) { entry ->
                    when (entry) {
                        is RecordEntry.Day -> DayHeader(entry.day)
                        is RecordEntry.Operation -> RecordRow(record = entry.record)
                    }
                }
            }
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
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

/** One operation: when, what was done, which app, and how it went. */
@Composable
private fun RecordRow(record: OperationRecord) {
    val scheme = MaterialTheme.colorScheme
    val isUninstall = record.kind == OperationKind.UNINSTALL

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = FlSurfaceLevel.Card.colorOf(scheme),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = OperationTime.clock(record.timestamp),
                    style = FlText.caption1.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(if (isUninstall) Res.string.records_action_uninstall else Res.string.records_action_reinstall),
                    style = FlText.caption1,
                    color = if (isUninstall) scheme.error else scheme.primary,
                    modifier = Modifier
                        .background(
                            (if (isUninstall) scheme.error else scheme.primary).copy(alpha = 0.14f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(if (record.success) Res.string.records_success else Res.string.records_failed),
                    style = FlText.caption1,
                    color = if (record.success) successColor() else scheme.error,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = record.label,
                style = FlText.subheadline,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = record.packageName,
                style = FlText.caption2.copy(fontFamily = FontFamily.Monospace),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (record.message.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = record.message,
                    style = FlText.caption2,
                    color = scheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.records_batch, record.batchIndex, record.batchSize),
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                )
                if (record.resetToFactory) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.records_reset_to_factory),
                        style = FlText.caption2,
                        color = scheme.tertiary,
                    )
                }
                if (record.device.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = record.device,
                        style = FlText.caption2,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The page before there is anything to log: it still says what the zone is for. */
@Composable
private fun EmptyRecords(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.records_empty),
            style = FlText.callout,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
