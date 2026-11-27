package io.github.samolego.canta.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.copy_logs
import io.github.samolego.canta.generated.resources.log_copied
import io.github.samolego.canta.generated.resources.logs
import io.github.samolego.canta.generated.resources.logs_filter_all
import io.github.samolego.canta.generated.resources.logs_filter_update
import io.github.samolego.canta.generated.resources.logs_filter_update_empty
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.util.LogUtils

@Composable
fun LogsScreen(
    platform: CantaPlatform,
    onNavigateBack: () -> Unit,
) {
    // 订阅而不是一次性读：日志是任意线程写进来的（见 LogUtils），这里跟着重画；同时也是
    // 修「后台线程写日志、主线程正在重组」那条 snapshot 崩溃的地方——StateFlow 不参与快照系统。
    val logs by LogUtils.logs.collectAsStateWithLifecycle()
    val logsCopiedText = stringResource(Res.string.log_copied)

    // 「更新」只看这一条链路（检查 → 下载 → 安装）：它横跨 UpdateCenter 与 AndroidCantaPlatform，
    // 平时混在设备/包列表那几千行里，事后找不到「哪条通道报了错、最后又是怎么装上的」。
    var onlyUpdate by remember { mutableStateOf(false) }
    // 上一次运行落盘的那一份（tag 带 `·上次`）：装成功时进程会被换掉，本次的内存日志到不了这里。
    var persisted by remember { mutableStateOf<List<LogUtils.LogEntry>>(emptyList()) }
    LaunchedEffect(Unit) { persisted = LogUtils.persistedUpdateEntries() }
    val shown = remember(logs, onlyUpdate, persisted) {
        if (onlyUpdate) {
            persisted + logs.filter { it.tag.startsWith(LogUtils.UPDATE_TAG) }
        } else {
            logs
        }
    }

    Scaffold(
        topBar = {
            Column {
                ScreenTopBar(onNavigateBack = onNavigateBack, title = stringResource(Res.string.logs))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !onlyUpdate,
                        onClick = { onlyUpdate = false },
                        label = { Text(stringResource(Res.string.logs_filter_all)) },
                    )
                    FilterChip(
                        selected = onlyUpdate,
                        onClick = { onlyUpdate = true },
                        label = { Text(stringResource(Res.string.logs_filter_update)) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    platform.copyToClipboard(shown.joinToString("\n") { it.format() })
                    platform.showMessage(logsCopiedText)
                }
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(Res.string.copy_logs)
                )
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (shown.isEmpty() && onlyUpdate) {
                item {
                    Text(
                        text = stringResource(Res.string.logs_filter_update_empty),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            // 序号进 key：同一毫秒同一 tag 的两行在旧写法里会撞 key，LazyColumn 会直接抛。
            itemsIndexed(shown, key = { index, entry -> "$index:${entry.timestamp}:${entry.tag}" }) { _, logEntry ->
                LogEntryChip(logEntry, platform)
            }
        }
    }
}

@Composable
private fun LogEntryChip(logEntry: LogUtils.LogEntry, platform: CantaPlatform) {
    var expanded by remember { mutableStateOf(false) }
    val logCopiedText = stringResource(Res.string.log_copied)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .padding(horizontal = 8.dp)
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = {
                    platform.copyToClipboard(logEntry.format())
                    platform.showMessage(logCopiedText)
                }
            ),
        color = logEntry.level.color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${logEntry.level} ${logEntry.tag}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = logEntry.getFormattedTime(),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    text = logEntry.message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
