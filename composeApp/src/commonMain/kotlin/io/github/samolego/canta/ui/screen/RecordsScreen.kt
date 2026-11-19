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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.data.history.OperationLogStore
import io.github.samolego.canta.data.history.OperationRecord
import io.github.samolego.canta.data.history.OperationTime
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.records_action_reinstall
import io.github.samolego.canta.generated.resources.records_action_uninstall
import io.github.samolego.canta.generated.resources.records_batch
import io.github.samolego.canta.generated.resources.records_failed
import io.github.samolego.canta.generated.resources.records_reset_to_factory
import io.github.samolego.canta.generated.resources.records_success
import io.github.samolego.canta.ui.component.AppIconImage
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
 */
@Composable
fun RecordsScreen(
    history: OperationLogStore,
    apps: List<AppInfo>,
    modifier: Modifier = Modifier,
) {
    val records by history.records.collectAsStateWithLifecycle()

    // The store keeps the log in a blob and only reads it on demand: do that once, so the
    // records from earlier runs are there on the first look instead of an empty page.
    LaunchedEffect(history) { history.ensureReady() }

    // Icons come from the apps the list already loaded: a log line names a package, and the
    // package is what the list is keyed by.
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }

    if (records.isEmpty()) {
        EmptyRecords(modifier = modifier.fillMaxSize())
        return
    }

    // One flat list of day headers and rows: a lazy list cannot nest one inside another.
    val entries = remember(records) { flattenByDay(records) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 13.dp,
            end = 13.dp,
            top = 6.dp,
            bottom = FLOATING_BAR_ROOM,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(entries, key = RecordEntry::key) { entry ->
            when (entry) {
                is RecordEntry.Day -> DayHeader(entry.day)
                is RecordEntry.Operation -> RecordRow(
                    record = entry.record,
                    app = byPackage[entry.record.packageName],
                )
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
        modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 1.dp),
    )
}

/**
 * One operation: the app's icon and name, then when it happened and how it went. The batch is
 * only mentioned when it had more than one app in it, since "1 of 1" says nothing.
 */
@Composable
private fun RecordRow(record: OperationRecord, app: AppInfo?) {
    val scheme = MaterialTheme.colorScheme
    val isUninstall = record.kind == OperationKind.UNINSTALL
    val actionColor = if (isUninstall) scheme.error else scheme.primary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = FlSurfaceLevel.Card.colorOf(scheme),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordIcon(app)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
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
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = OperationTime.clock(record.timestamp),
                        style = FlText.caption2.copy(fontFamily = FontFamily.Monospace),
                        color = scheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                if (isUninstall) Res.string.records_action_uninstall
                                else Res.string.records_action_reinstall
                            ),
                            style = FlText.caption2,
                            color = actionColor,
                        )
                        Text(
                            text = " · " + stringResource(
                                if (record.success) Res.string.records_success else Res.string.records_failed
                            ),
                            style = FlText.caption2,
                            color = if (record.success) successColor() else scheme.error,
                        )
                    }
                }
            }

            if (record.message.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = record.message,
                    style = FlText.caption2,
                    color = scheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = ROW_ICON + 10.dp),
                )
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
            val notes = listOf(batchNote, resetNote).filter { it.isNotEmpty() }
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = notes.joinToString(" · "),
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
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
