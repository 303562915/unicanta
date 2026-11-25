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
import io.github.samolego.canta.core.TweakRun
import io.github.samolego.canta.data.audio.AudioWhitelist
import io.github.samolego.canta.util.DeviceContext
import io.github.samolego.canta.util.FailureReportDevice
import io.github.samolego.canta.util.PackageTelemetry
import io.github.samolego.canta.util.TweakReport
import io.github.samolego.canta.ui.component.ScreenTopBar
import kotlinx.coroutines.launch

/**
 * 音频白名单：一页看完「谁被压、开关是谁」，并且能当场开合。
 *
 * 音频这块只有这一个入口：工具页那一行点进来就是这里，读状态、解除、恢复、看名单、筛应用全在这一页，
 * 没有第二张卡、第二份脚本。三份 shell 文本都在 `files/audio_whitelist_*.sh` 里，由 [AudioWhitelist] 读，
 * 改一处不会让另一处变成旧行为。
 */
@Composable
fun AudioWhitelistScreen(
    handler: CantaHandler,
    telemetry: PackageTelemetry,
    cantaVersion: String,
    onNavigateBack: () -> Unit,
) {
    var state by remember { mutableStateOf<AudioWhitelist.State?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    /** The label of the action in flight, null when nothing is running. */
    var busy by remember { mutableStateOf<String?>(null) }
    var log by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var bypass by remember { mutableStateOf<String?>(null) }
    var restore by remember { mutableStateOf<String?>(null) }
    /** 「本机没有名单文件」这一页已经报过一次了；同一次进入不重复写。 */
    var reportedUnsupported by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    /**
     * 把这一页此刻的事实报到后端：「用不了」这类反馈只有本机诊断行说得清。
     *
     * 用户点「解除」或「恢复」之后调，报的是操作**之后**的状态。读取本身平时不上报（不然每开一次
     * 页面就写一行，数据里全是「有人打开过」），只有一个例外：「本机没有名单文件」这个结论第一次
     * 读出来时报一行——那是我们自己机器上永远看不到、而反馈里最多的一种。
     */
    fun report(
        stage: String,
        result: TweakRun?,
        snapshot: AudioWhitelist.State?,
        overrideOutcome: String? = null,
    ) {
        val outcome = overrideOutcome ?: when {
            result == null -> TweakReport.OUTCOME_FAILED
            result.failure != null -> TweakReport.OUTCOME_BLOCKED
            result.ok -> TweakReport.OUTCOME_OK
            // 脚本自己的判定：没有配置文件 = 这台机器没有这套东西，比「失败」准确得多。
            snapshot != null && !snapshot.supported -> TweakReport.OUTCOME_UNSUPPORTED
            else -> TweakReport.OUTCOME_FAILED
        }
        scope.launch {
            telemetry.recordTweak(
                TweakReport(
                    v = 1,
                    platform = "android",
                    tweak = AudioWhitelist.TWEAK_ID,
                    stage = stage,
                    outcome = outcome,
                    code = codeOf(result, failure),
                    level = snapshot?.level.orEmpty(),
                    configPath = snapshot?.configPath.orEmpty(),
                    entries = snapshot?.entries?.size ?: 0,
                    installed = snapshot?.entries?.count { it.installed } ?: 0,
                    diagnostics = snapshot?.diagnostics.orEmpty().joinToString("\n"),
                    output = result?.output.orEmpty(),
                    cantaVersion = cantaVersion,
                    device = deviceOf(handler),
                )
            )
        }
    }

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
            else -> {
                val parsed = AudioWhitelist.parse(run.output)
                state = parsed
                // 「本机没有这份名单」是反馈最多的一种，也是唯一没法从我们自己机器上复现的一种：
                // K90 / K90 Pro Max 这类机器 /odm 里到底有什么，只能靠它们自己送上来。所以这一条
                // 在页面第一次读出这个结论时报一行（诊断行里没有包列表），同一页重复刷新不再写。
                if (!parsed.supported && !reportedUnsupported) {
                    reportedUnsupported = true
                    report(TweakReport.STAGE_READ, null, parsed, TweakReport.OUTCOME_UNSUPPORTED)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        bypass = runCatching { AudioWhitelist.bypassScript() }.getOrNull()
        restore = runCatching { AudioWhitelist.restoreScript() }.getOrNull()
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
            // 报的是**操作之后**的状态：解除是否真的把开关写成了 -1。
            report(
                stage = if (label == "恢复") TweakReport.STAGE_RESTORE else TweakReport.STAGE_RUN,
                result = result,
                snapshot = state,
            )
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
                    // 名单文件找不到也允许解除：名单可能整份由云控下发，本机没有文件不等于开关无效。
                    // 真正没法动的只有「既没有文件、属性也读不出来」那种机器（[AudioWhitelist.State.writable]）。
                    // 恢复始终允许——这台机器上可能已经被人点过解除，得留一条路改回 1。
                    canBypass = bypass != null && current?.writable == true && current?.bypassed != true,
                    canRestore = restore != null && current?.bypassed == true,
                    onBypass = { run("解除", bypass) },
                    onRestore = { run("恢复", restore) },
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
                        text = "这台机器上没找到名单文件：按名字（trackefx_config.xml）、按目录（odm / vendor / system_ext / product）、" +
                            "按内容都翻过一遍，odm 的 audio 目录里只有什么，下面那栏诊断信息里列着。" +
                            "没找到不代表开关没用——K90 / K90 Pro Max 这一代可能整份名单都由云控下发。" +
                            "所以「解除」还能点：写下去自己听一遍，低频回来了就是生效；没有变化就点「恢复」写回原值。" +
                            "把这段诊断发给开发者，能继续找这台机器上真正的入口。",
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
        // 没有名单文件时，属性值说明不了「效果如何」：名单可能由云控下发，本机没有文件不等于这道处理不存在。
        !state.supported && state.bypassed -> "开关已经是 -1，但这台机器上没找到名单文件：效果得自己听一遍"
        !state.supported -> "这台机器上没找到名单文件（名单可能由云控下发），仍可写一次开关试试"
        state.bypassed -> "已解除：名单里的应用也按原始信号输出"
        state.active -> "按名单处理：名单里的应用被切低频、压低音量"
        else -> "开关值：${state.level.ifEmpty { "未设置" }}（不是 1 也不是 -1）"
    }
    val accent = when {
        failure != null -> MaterialTheme.colorScheme.error
        state != null && !state.supported -> MaterialTheme.colorScheme.onSurfaceVariant
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

/** 报给后端用的失败代号：脚本自己打的、失败枚举、最后一行输出，取第一个能用的。 */
private fun codeOf(result: TweakRun?, failure: String?): String {
    if (result == null) return failure?.take(60).orEmpty()
    result.failure?.let { return it.name.uppercase() }
    val known = listOf("STATE_FAIL", "SET_FAIL", "TIMEOUT", "RESTORE_FAIL", "RESTORE_SET_FAIL", "RESTORE_NO_KEY")
    result.output.lineSequence().forEach { line ->
        known.firstOrNull { it in line }?.let { return it }
    }
    return if (result.ok) "" else result.output.lineSequence().lastOrNull()?.take(60).orEmpty()
}

/** 这台机器在这份报告里的样子：只带机型/ROM/代号，不带任何身份。 */
private suspend fun deviceOf(handler: CantaHandler): FailureReportDevice {
    val context = runCatching { handler.deviceContext() }.getOrNull() ?: DeviceContext.UNKNOWN
    val profile = runCatching { handler.deviceProfile() }.getOrNull()
    return FailureReportDevice(
        brand = context.brand,
        rom = context.rom,
        romVersion = context.romVersion,
        sdk = context.sdk,
        model = profile?.model.orEmpty(),
        codename = profile?.device.orEmpty(),
    )
}

/** Why the shell never answered, in a sentence a reader can act on. */private fun failureText(failure: TweakFailure): String = when (failure) {
    TweakFailure.Unsupported -> "这个平台没有 sudo 的 shell：只有 Android 上的 Shizuku 或 root 能读这些系统文件"
    TweakFailure.NoPrivilege -> "Shizuku 没在运行，或者 Canta 还没被授权"
    TweakFailure.Timeout -> "Shizuku 没有响应"
    TweakFailure.Broken -> "Shizuku 返回了错误，没有结果"
}
