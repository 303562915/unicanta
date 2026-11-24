package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.core.TweakFailure
import io.github.samolego.canta.data.audio.AudioWhitelist
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.util.RomTweak
import io.github.samolego.canta.util.RomTweaks
import kotlinx.coroutines.launch

/**
 * 音频白名单：一页看完「谁被压、开关是谁」，并且能当场开合。
 *
 * 做成页面而不是弹窗，是因为这份名单本来就有几十行：弹窗里塞一段文本，只能读不能挑；页面里能滚、
 * 能筛、能点按钮，还能把这一台机器上真正装了的那几个排到最前面。
 *
 * 两个动作不在这里写脚本：解除与恢复的 shell 文本属于 [RomTweaks] 里那张卡（`xiaomi-audio-whitelist`），
 * 这里按 id 取来跑。一份脚本两处入口，改一处不会让另一处变成旧行为。
 */
@Composable
fun AudioWhitelistScreen(
    handler: CantaHandler,
    onNavigateBack: () -> Unit,
) {
    var state by remember { mutableStateOf<AudioWhitelist.State?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    /** The label of the action in flight, null when nothing is running. */
    var busy by remember { mutableStateOf<String?>(null) }
    var log by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var tweak by remember { mutableStateOf<RomTweak?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    /** Reads the phone's list and switch again; the only source of truth this page has. */
    suspend fun refresh() {
        loading = true
        failure = null
        val run = handler.runTweakCommand(AudioWhitelist.script())
        loading = false
        when {
            run.failure != null -> {
                failure = failureText(run.failure)
                state = null
            }
            !run.ok -> failure = run.output.ifBlank { "读取失败，没有任何输出" }
            else -> state = AudioWhitelist.parse(run.output)
        }
    }

    LaunchedEffect(Unit) {
        tweak = RomTweaks.bundled().byId(AudioWhitelist.TWEAK_ID)
        refresh()
    }

    /** Runs one of the two scripts, keeps what it printed, then re-reads the state. */
    fun run(label: String, script: String?) {
        if (script.isNullOrBlank()) return
        scope.launch {
            busy = label
            val result = handler.runTweakCommand(script)
            log = result.output.ifBlank { if (result.ok) "完成，没有输出" else "执行失败，没有输出" }
            busy = null
            refresh()
        }
    }

    val current = state
    val entries = current?.entries.orEmpty()
    val keyword = query.trim()
    val shown = if (keyword.isEmpty()) {
        entries
    } else {
        entries.filter {
            it.label.contains(keyword, ignoreCase = true) || it.packageName.contains(keyword, ignoreCase = true)
        }
    }
    val installedCount = entries.count { it.installed }
    val diagnostics = current?.diagnostics.orEmpty()

    Scaffold(
        topBar = { ScreenTopBar(onNavigateBack = onNavigateBack, title = "音频白名单") },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "status") {
                StatusCard(
                    state = current,
                    loading = loading,
                    failure = failure,
                    busy = busy,
                    canBypass = tweak != null && current?.bypassed != true,
                    canRestore = tweak?.restore != null && current?.bypassed == true,
                    onBypass = { run("解除", tweak?.command) },
                    onRestore = { run("恢复", tweak?.restore?.command) },
                    onRefresh = { scope.launch { refresh() } },
                )
            }

            item(key = "explain") { ExplainCard() }

            if (entries.isNotEmpty()) {
                item(key = "list-head") {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp)) {
                        Text(
                            text = "被点名处理的应用（${entries.size} 个，本机装了 $installedCount 个）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("筛选应用名或包名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
                items(shown, key = { it.packageName }) { entry -> EntryRow(entry) }
                if (shown.isEmpty()) {
                    item(key = "no-match") {
                        Text(
                            text = "没有匹配的应用。名字不认识就填包名——不在名单里的应用，系统本来就不处理它。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            } else if (current != null && !current.supported) {
                item(key = "no-config") {
                    Text(
                        text = "这台机器上没找到 /odm/etc/audio/trackefx_config.xml：没有这道音频处理，所有应用都是原始音量，不需要动。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }

            if (log.isNotEmpty()) {
                item(key = "log") {
                    LogCard(log = log, onCopy = { clipboard.setText(AnnotatedString(log)) })
                }
            }

            // 这个功能是从别的工具的字节码里反推出来的，没有任何公开文档：说不生效的时候，
            // 只有这一屏原生数据能说明是哪种机器、哪种 ROM、哪一步对不上。所以它常驻在页面上。
            if (diagnostics.isNotEmpty()) {
                item(key = "diagnostics") {
                    DiagnosticsCard(
                        lines = diagnostics,
                        onCopy = { clipboard.setText(AnnotatedString(diagnostics.joinToString("\n"))) },
                    )
                }
            }

            item(key = "space") { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }
}

/** The switch itself: what it says now, and the two buttons that change it. */
@Composable
private fun StatusCard(
    state: AudioWhitelist.State?,
    loading: Boolean,
    failure: String?,
    busy: String?,
    canBypass: Boolean,
    canRestore: Boolean,
    onBypass: () -> Unit,
    onRestore: () -> Unit,
    onRefresh: () -> Unit,
) {
    val headline = when {
        failure != null -> "读不到状态"
        state == null -> if (loading) "读取中…" else "读不到状态"
        state.bypassed -> "已解除：名单里的应用也按原始信号输出"
        state.active -> "按名单处理：名单里的应用被切低频、压低音量"
        else -> "开关值：${state.level.ifEmpty { "未设置" }}（不是 1 也不是 -1）"
    }
    val accent = when {
        failure != null -> MaterialTheme.colorScheme.error
        state?.bypassed == true -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            Text(
                text = "${AudioWhitelist.PROP} = ${state?.level?.ifEmpty { "未设置" } ?: "?"}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (failure != null) {
                Text(
                    text = failure,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (busy != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = "正在$busy…（云控生效最多要等 12 秒）",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                TextButton(onClick = onBypass, enabled = canBypass && busy == null) { Text("解除") }
                TextButton(onClick = onRestore, enabled = canRestore && busy == null) { Text("恢复") }
                TextButton(onClick = onRefresh, enabled = busy == null) { Text("重新读取") }
            }
        }
    }
}

/** Why the list is the list it is: the sentence that stops people from adding their own apps. */
@Composable
private fun ExplainCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "名单是「被压制」的应用，不是「受益」的应用",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "澎湃OS / MIUI 的音频引擎按 /odm/etc/audio/trackefx_config.xml 处理这批应用：短视频、电商、"
                    + "资讯类切掉 280 Hz 以下低频并压低 3.5 dB，导航类压低 4.5 dB。开关为 -1 时整张名单一起跳过。"
                    + "所以没出现在下面的应用（爱奇艺、腾讯视频、优酷、喜马拉雅、网易云、酷狗、红果这些）本来就不处理，"
                    + "解锁前后都是原始信号；把它们加进名单，得到的反而是被压。"
                    + "真要按应用挑，只能 root 后改 /odm 里那个文件，系统升级会被覆盖。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** One app the config names, with where it stands on this phone. */
@Composable
private fun EntryRow(entry: AudioWhitelist.Entry) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (entry.installed) "已安装" else "未安装",
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/** What the last action printed, verbatim: the failure codes are the diagnosis. */
@Composable
private fun LogCard(log: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "执行日志",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCopy) { Text("复制", fontSize = 11.sp) }
            }
            Text(
                text = log,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * 这台机器自己的音频事实，原样列出来：有哪些 scene / trackefx 属性、两个 global 设置里现在是什么、
 * 配置文件开头长什么样。说不生效时，只有这一段能区分「开关换了名字」和「这台 ROM 根本没有这套东西」。
 */
@Composable
private fun DiagnosticsCard(lines: List<String>, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "诊断信息",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCopy) { Text("复制", fontSize = 11.sp) }
            }
            Text(
                text = "不生效就把这一段发给开发者：它能直接看出这台机器和预期的差在哪。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                text = lines.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }
    }
}

/** Why the shell never answered, in a sentence a reader can act on. */private fun failureText(failure: TweakFailure): String = when (failure) {
    TweakFailure.Unsupported -> "这个平台没有 sudo 的 shell：只有 Android 上的 Shizuku 或 root 能读这些系统文件"
    TweakFailure.NoPrivilege -> "Shizuku 没在运行，或者 Canta 还没被授权"
    TweakFailure.Timeout -> "Shizuku 没有响应"
    TweakFailure.Broken -> "Shizuku 返回了错误，没有结果"
}
