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
    /** 低功耗清单的两个脚本（K90 Pro Max 这一类真正压外放的那份）。 */
    var lowPowerDisable by remember { mutableStateOf<String?>(null) }
    var lowPowerRestore by remember { mutableStateOf<String?>(null) }
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
     *
     * 恢复失败也要报，而且必须报：属性写成 -1 之后如果再也改不回来，这台机器就一直是「解除了」的样子，
     * 而我们从没在那台机器上看到过它的屏幕。
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
        lowPowerDisable = runCatching { AudioWhitelist.lowPowerDisableScript() }.getOrNull()
        lowPowerRestore = runCatching { AudioWhitelist.lowPowerRestoreScript() }.getOrNull()
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
            // 「恢复清单」也是恢复，所以按前缀判，不按完全相等。
            report(
                stage = if (label.startsWith("恢复")) TweakReport.STAGE_RESTORE else TweakReport.STAGE_RUN,
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
                    // 名单文件找不到也允许解除：是不是「空转」由脚本自己的输出和用户听感说明。
                    // **有低功耗清单也不置灰**：灰按钮只会让用户觉得「连点都点不了了」（群里那位 K90pm 的原话），
                    // 而且他没法从灰色看出原因；点亮它、点完照实说「这条属性你这台机器不读，要改的是下面那份」，
                    // 比一个灰按钮有用。
                    canBypass = bypass != null && current != null && current.bypassed != true,
                    canRestore = restore != null && current?.bypassed == true,
                    onBypass = { run("解除", bypass) },
                    onRestore = { run("恢复", restore) },
                    onRefresh = { scope.launch { refresh() } },
                    onCopyManual = {
                        clipboard.setText(
                            AnnotatedString("su -c \"setprop ${AudioWhitelist.PROP} ''\"\nsu -c reboot"),
                        )
                    },
                )
            }

            item(key = "explain") { ExplainCard() }

            // K90 Pro Max 这一类：没有 trackefx 名单，但有一份低功耗外放清单，B 站/抖音的声音就是被它压的。
            // 这份能改——清空清单里的包名（root，改前自动备份），不必再去点上面那个对它无效的开关。
            current?.takeIf { it.hasLowPower }?.let { snapshot ->
                item(key = "lowpower") {
                    LowPowerCard(
                        state = snapshot,
                        busy = busy,
                        // 没有 root 就点不动：/odm 只读，Shizuku 的 shell 身份写不进去。
                        // 让按钮亮着、点完再报错，就是刚才那位 K90pm 用户的「解除不了」。
                        // `maybe`（装了 root 管理器但没授权）仍然放行——点了会弹授权框，那才是他要的下一步。
                        canDisable = lowPowerDisable != null && snapshot.canElevate,
                        canRestore = lowPowerRestore != null && snapshot.canElevate,
                        onDisable = { run("解除清单", lowPowerDisable) },
                        onRestore = { run("恢复清单", lowPowerRestore) },
                    )
                }
            }

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
                        text = if (current.hasLowPower) {
                            // 已经有低功耗清单的机器：不要再引导他去点上面那个开关，答案在下面那张卡里。
                            "这台机器上没有 trackefx 名单，场景开关对它无效——真正压外放的是上面那份「低功耗应用清单」，" +
                                "要动的是那一份。清单在 /odm 只读分区，改它需要 root；" +
                                "如果这台机器没有 root，那条路走不通，诊断信息里的 lowpower 属性是找免 root 入口的唯一线索。"
                        } else {
                            "这台机器上没找到名单文件：按名字（trackefx_config.xml）、按目录（odm / vendor / system_ext / product / my_product）、" +
                                "按内容都翻过一遍，odm 的 audio 目录里到底有什么，下面那栏诊断信息里列着。" +
                                "这套机制是澎湃OS 4（Android 17）才有的：那代机器上有个 persist.sys.scene.partition.level 属性，" +
                                "写成 -1 引擎就跳过整份名单（K100 Pro Max 上验证过）。" +
                                "澎湃OS 3（Android 16）的机器本来就没有这条属性——K90 Pro Max 就是这一类，属性写得进去，但引擎不读，也没有名单文件，" +
                                "所以「解除」在这台机器上多半只是空转。仍可以点一次试：点完自己听一遍，低频回来了就是生效，没变化就点「恢复」写回原值。" +
                                "把这段诊断发给开发者，能确认这台机器是「没有这套东西」还是「换了个名字」。"
                        },
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
    /** 删不掉那条属性时，把手工命令放进剪贴板。 */
    onCopyManual: () -> Unit,
) {
    val headline = when {
        failure != null -> "读不到状态"
        state == null -> if (loading) "读取中…" else "读不到状态"
        // 有低功耗清单的机器必须排在最前面：这类机器的开关往往已经被点成 -1（`bypassed`），
        // 如果把 `!supported && bypassed` 放前面，就永远显示「多半没有效果」，看不到「要改的是下面那份」
        // —— 群里那位 K90pm 骂的「还是一样」就是这个分支顺序的锅。
        !state.supported && state.hasLowPower -> "这台机器没有场景名单：要改的是下面那份「低功耗应用清单」"
        // 没有名单文件时，属性值说明不了「效果如何」：名单可能由云控下发，本机没有文件不等于这道处理不存在。
        !state.supported && state.bypassed -> "开关已经是 -1，但这台机器上没有名单文件：多半没有效果，自己听一遍"
        !state.supported -> "这台机器上没有名单文件：解除多半是空转，可以点一次试，没变化就点「恢复」"
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
            // 不把 Restore 当成成功：RESTORE_DELETE_FAIL 表示这条属性删不掉，
            // 机器上还留着一条本不该存在的值；写成「成功」只会让人以为已经回到出厂。
            val restoreStuck = failure?.contains("RESTORE_DELETE_FAIL") == true
            if (failure != null) {
                Text(
                    text = if (restoreStuck) "这条属性在这台机器上删不掉，还留着：$failure" else failure,
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
            // 有低功耗清单时把这个开关为什么没用说清楚——按钮不灰，但点之前就得知道结果。
            if (state?.hasLowPower == true) {
                Text(
                    text = "上面这个「解除」对你这台机器不会有任何变化：它写的是场景开关，而你机器的引擎不读那条属性，" +
                        "压你外放的是下面那份低功耗清单。点它只会看到开关变成 -1，声音不会变。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
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
            // 删不掉那条属性时，把最后一步交给用户自己跑：这一步要 root，而且得重启才可能回落。
            if (failure?.contains("RESTORE_DELETE_FAIL") == true) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = "手工最后一步（Termux，要 root）：su -c \"setprop ${AudioWhitelist.PROP} ''\" 然后 su -c reboot",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCopyManual) { Text("复制") }
                }
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
                text = "澎湃OS / MIUI 的音频引擎按 /odm/etc/audio/trackefx_config.xml 处理这批应用：短视频、电商、" +
                    "资讯类切掉 280 Hz 以下低频并压低 3.5 dB，导航类压低 4.5 dB。开关为 -1 时整张名单一起跳过。" +
                    "所以没出现在下面的应用（爱奇艺、腾讯视频、优酷、喜马拉雅、网易云、酷狗、红果这些）本来就不处理，" +
                    "解锁前后都是原始信号；把它们加进名单，得到的反而是被压。" +
                    "真要按应用挑，只能 root 后改 /odm 里那个文件，系统升级会被覆盖。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * 低功耗外放清单：K90 Pro Max 这一代真正把 B 站/抖音声音压小的东西。
 *
 * 它有按钮，因为这是这台机器上**唯一**能让外放变响的动作：把清单里点名的应用清空（root，
 * 改前自动备份，改完重启 audioserver）。上面那个场景开关对这类机器是空转，不在这里点它。
 */
@Composable
private fun LowPowerCard(
    state: AudioWhitelist.State,
    busy: String?,
    canDisable: Boolean,
    canRestore: Boolean,
    onDisable: () -> Unit,
    onRestore: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "这台机器压外放的是「低功耗应用清单」，不是上面那个开关",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "清单文件：" + state.lowPowerPath.ifEmpty { "（没找到具体路径）" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = "被点名的应用在播放时，系统会压低功率（声音小、低频少）。它和上面的场景开关是两条机制：" +
                    "这台机器没有 trackefx 名单，写那个开关不会改变这份清单——所以上面「解除」点了不会有变化，" +
                    "真正要动的是这一份。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (state.lowPowerPackages.isNotEmpty()) {
                Text(
                    text = "清单里的应用（${state.lowPowerPackages.size} 个，本机装了 ${state.lowPowerInstalled} 个）",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp),
                )
                state.lowPowerPackages.take(20).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry.installed) scheme.onSurface else scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (entry.installed) {
                            Text(
                                text = "已装",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.primary,
                            )
                        }
                    }
                }
                if (state.lowPowerPackages.size > 20) {
                    Text(
                        text = "…还有 ${state.lowPowerPackages.size - 20} 个",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                Text(
                    text = "找到了这份清单文件，但没能从里面解析出包名（格式可能是二进制，或者名字不在这里）。" +
                        "把下面那栏诊断发给开发者，用 root 直接看这个文件更准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.lowPowerPackages.isNotEmpty()) {
                Text(
                    text = "这台机型的压制表（数值来自 ROM 里那份清单的 template）：",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp),
                )
                AudioWhitelist.LOWPOWER_TEMPLATES.forEach { (_, text) ->
                    Text(
                        text = "· $text",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = if (state.rootAccess == "no") {
                        "所以外放小不是「坏了」，是系统把这几组的音量乘了上面那个系数。要改这份清单：" +
                            "它在 /odm 只读分区，只能用 KernelSU / Magisk 模块做 bind mount 覆盖，而且必须在开机阶段" +
                            "（audioserver 起来之前）挂上去——运行中改文件它也不读。这台机器没有 root，所以按钮点不动。"
                    } else {
                        "要改这份清单：它在 /odm 只读分区。脚本会先备份，再尝试清掉里面点名的应用并重启 audioserver；" +
                            "/odm 被 AVB 保护写不进去时会明确报 ODM_WRITE_BLOCKED——那种情况只能靠模块在开机阶段 bind mount。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    TextButton(onClick = onDisable, enabled = canDisable && busy == null) { Text("解除清单") }
                    TextButton(onClick = onRestore, enabled = canRestore && busy == null) { Text("恢复清单") }
                }
                // 不想 root 的人问得最多的就是这一句，所以把「免 root 到底能做什么」摆出来，
                // 而不是让他反复点按钮试。里面每条都是这份清单能对上的事实。
                Text(
                    text = "不想 root 的话，只有这几种（都不是「解除」，是绕开）：",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
                AudioWhitelist.NO_ROOT_OPTIONS.forEach { line ->
                    Text(
                        text = "· $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
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
    val known = listOf(
        "STATE_FAIL", "SET_FAIL", "TIMEOUT",
        "RESTORE_FAIL", "RESTORE_SET_FAIL", "RESTORE_NO_KEY", "RESTORE_DELETE_FAIL",
        // 低功耗清单那两个脚本自己的判定：没有 root、备份失败、/odm 写不进去、还原失败。
        "BACKUP_FAIL", "ODM_WRITE_BLOCKED", "ODM_RESTORE_BLOCKED",
    )
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
