package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.got_it
import io.github.samolego.canta.generated.resources.tweaks_copied
import io.github.samolego.canta.generated.resources.tweaks_copy_command
import io.github.samolego.canta.generated.resources.tweaks_package_refused
import io.github.samolego.canta.generated.resources.tweaks_empty
import io.github.samolego.canta.generated.resources.tweaks_restore_button
import io.github.samolego.canta.generated.resources.tweaks_restore_confirm_body
import io.github.samolego.canta.generated.resources.tweaks_restore_confirm_title
import io.github.samolego.canta.generated.resources.tweaks_restore_done
import io.github.samolego.canta.generated.resources.tweaks_restore_loading
import io.github.samolego.canta.generated.resources.tweaks_restore_needs_privilege
import io.github.samolego.canta.generated.resources.tweaks_restore_nothing
import io.github.samolego.canta.generated.resources.tweaks_restore_running
import io.github.samolego.canta.generated.resources.tweaks_restore_start
import io.github.samolego.canta.generated.resources.tweaks_run
import io.github.samolego.canta.generated.resources.tweaks_run_broken
import io.github.samolego.canta.generated.resources.tweaks_run_done
import io.github.samolego.canta.generated.resources.tweaks_run_no_privilege
import io.github.samolego.canta.generated.resources.tweaks_run_not_ok
import io.github.samolego.canta.generated.resources.tweaks_run_timeout
import io.github.samolego.canta.generated.resources.tweaks_run_unsupported
import io.github.samolego.canta.generated.resources.tweaks_running
import io.github.samolego.canta.generated.resources.tweaks_splash_needs_service
import io.github.samolego.canta.generated.resources.tweaks_title
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ScrollToTopEffect
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.RomTweak
import io.github.samolego.canta.util.RomTweaks
import io.github.samolego.canta.util.commandFor
import io.github.samolego.canta.util.familyKeyOf
import io.github.samolego.canta.util.familyLabel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Package names listed in the confirmation dialog; the rest is summarised with an ellipsis. */
private const val MAX_SHOWN_PACKAGES = 8

/** Marker the Xiaomi splash script prints when 智能服务 is not on the phone at all. */
private const val SERVICE_MISSING_MARKER = "SVC_MISSING"

/** Which of a card's scripts was just copied: the card's own, or the one that undoes it. */
private const val COPY_KEY_RUN = "run"
private const val COPY_KEY_RESTORE = "restore"

/**
 * What the restore row is doing right now: nothing, checking what there is to put back, asking
 * first, putting it back, or reporting how it went.
 */
private sealed interface RestoreStep {
    data object Loading : RestoreStep
    data object Nothing : RestoreStep
    data object NeedsPrivilege : RestoreStep
    data class Confirm(val packages: List<String>) : RestoreStep
    data class Running(val done: Int, val total: Int) : RestoreStep
    data class Done(val restored: Int, val failed: Int) : RestoreStep
}

/** A tool that runs a shipped script: it is either still running or done and reporting back. */
private sealed interface RunStep {
    data class Running(val tweak: RomTweak) : RunStep
    data class Finished(val tweak: RomTweak, val run: TweakRun) : RunStep
}

/**
 * 一页到底：每个厂商一个可折叠的分组，默认全部折叠；点分组标题展开，再点收起。
 *
 * 行上没有执行按钮——点条目就是弹详情，弹窗里再执行；只有音频白名单那一行是直接进页面，
 * 因为它后面是几十行名单，不是一个动作。
 *
 * Only vendors that actually ship a tool get a group — an OPPO owner sees no Xiaomi entry and no
 * empty "OPPO" section either; a vendor is added by adding its tweak to `rom_tweaks.json`. The one
 * tool every ROM has, restoring the apps uninstalled for this user, rides as one more chip in the
 * phone's own group, and only falls back to a full-width row on the page when that group is absent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TweaksScreen(
    handler: CantaHandler,
    onOpenAudio: () -> Unit,
) {
    var profile by remember { mutableStateOf<DeviceProfile?>(null) }
    var tweaks by remember { mutableStateOf<RomTweaks?>(null) }
    var step by remember { mutableStateOf<RestoreStep?>(null) }
    var run by remember { mutableStateOf<RunStep?>(null) }
    var detail by remember { mutableStateOf<RomTweak?>(null) }
    var copied by remember { mutableStateOf<String?>(null) }
    /** The card waiting for a package name; null when nothing is being asked. */
    var asking by remember { mutableStateOf<RomTweak?>(null) }
    /** 这台机器有没有音频白名单配置；null 表示还在问。 */
    var audioDetected by remember { mutableStateOf<Boolean?>(null) }
    /** 展开了哪几个厂商的分组；默认全部折叠。 */
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    // 双击底栏「工具」那一格：这一页回顶部。
    val tweakListState = rememberLazyListState()
    ScrollToTopEffect(tweakListState)

    LaunchedEffect(Unit) {
        profile = handler.deviceProfile()
        tweaks = RomTweaks.bundled()
        // 音频白名单那一行要先知道本机有没有那套配置：没有就写在标题上，别让人点进去看一个空转的开关。
        if (handler.supportsTweakCommands) {
            audioDetected = runCatching {
                handler.runTweakCommand(AudioWhitelist.probeScript()).output.contains(AudioWhitelist.PROBE_YES)
            }.getOrNull()
        }
    }

    val allGroups = remember(tweaks, profile) { tweaks?.groupedFor(profile).orEmpty() }
    val mine = profile?.let { familyKeyOf(it.rom) }

    /** Reads which apps are uninstalled for this user and asks before touching any of them. */
    fun askToRestoreAll() {
        scope.launch {
            if (!handler.isAuthorized) {
                step = RestoreStep.NeedsPrivilege
                return@launch
            }
            step = RestoreStep.Loading
            val apps = runCatching { handler.loadApps() }.getOrNull()
            if (apps == null) {
                step = RestoreStep.NeedsPrivilege
                return@launch
            }
            val packages = apps.filter { it.isUninstalled }.map { it.packageName }
            step = if (packages.isEmpty()) RestoreStep.Nothing else RestoreStep.Confirm(packages)
        }
    }

    /** Reinstalls [packages] one after another, reporting progress as each one answers. */
    fun restoreAll(packages: List<String>) {
        scope.launch {
            var done = 0
            var restored = 0
            step = RestoreStep.Running(done = 0, total = packages.size)
            handler.reinstallApps(packages).collect { result ->
                done++
                if (result.success) restored++
                step = RestoreStep.Running(done = done, total = packages.size)
            }
            step = RestoreStep.Done(restored = restored, failed = packages.size - restored)
        }
    }

    /** Runs [script] with the privileged shell and shows what it printed, under [tweak]'s name. */
    fun runScript(tweak: RomTweak, script: String) {
        if (script.isBlank()) return
        scope.launch {
            run = RunStep.Running(tweak)
            run = RunStep.Finished(tweak, handler.runTweakCommand(script))
        }
    }

    /** Runs whatever the card's `action` asks for: a shipped script, or a job Canta does itself. */
    fun runTweak(tweak: RomTweak) {
        when (tweak.action) {
            RomTweaks.ACTION_RESTORE_ALL -> askToRestoreAll()
            RomTweaks.ACTION_SHELL -> runScript(tweak, tweak.command)
            // The command is only known once a package is named, so the button asks first.
            RomTweaks.ACTION_SHELL_WITH_PACKAGE -> asking = tweak
            else -> Unit
        }
    }

    /** Whether the card gets a run button at all: no button where nothing could happen. */
    fun runnable(tweak: RomTweak): Boolean = when (tweak.action) {
        RomTweaks.ACTION_SHELL, RomTweaks.ACTION_SHELL_WITH_PACKAGE -> handler.supportsTweakCommands
        RomTweaks.ACTION_RESTORE_ALL -> true
        else -> false
    }

    Scaffold(
        topBar = {
            ScreenTopBar(title = stringResource(Res.string.tweaks_title))
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), state = tweakListState) {
            // 一个厂商一个折叠分组：标题带项数，点开才铺出条目，默认全部收起。
            allGroups.forEach { (key, list) ->
                val open = key in expanded
                val hasAudio = key == familyKeyOf("hyperos")
                // 本机这一组还多两块：音频白名单（后面是页面不是弹窗）和恢复所有已卸载的应用。
                val hasRestore = key == mine
                item(key = "head-$key") {
                    GroupHeader(
                        title = groupTitle(key),
                        count = list.size + (if (hasAudio) 1 else 0) + (if (hasRestore) 1 else 0),
                        mine = key == mine,
                        expanded = open,
                        onToggle = {
                            expanded = if (open) expanded - key else expanded + key
                        },
                    )
                }
                if (open) {
                    // 一行放几个：每个条目只有图标 + 名字，宽度跟着文字走，放不下自动换行。
                    item(key = "grid-$key") {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            list.forEach { tweak ->
                                ToolChip(
                                    icon = iconOf(tweak),
                                    title = tweak.title,
                                    badge = tweak.badge.ifEmpty { null },
                                    onClick = {
                                        // Which script was copied last belongs to the dialog, not to the page.
                                        copied = null
                                        detail = tweak
                                    },
                                )
                            }
                            // 音频白名单是页面不是弹窗：它后面是几十行名单，弹窗里只能读。
                            if (hasAudio) {
                                ToolChip(
                                    icon = Icons.Default.ViewList,
                                    // 角标三态：检查中 / 有这套配置（不挂角标）/ 本机没有。
                                    title = "音频白名单",
                                    badge = when (audioDetected) {
                                        null -> "检查中"
                                        false -> "不支持"
                                        true -> null
                                    },
                                    onClick = onOpenAudio,
                                )
                            }
                            // 恢复所有已卸载的应用：本来在页底独占一整行，现在做成同样的小块放进本机那一组，
                            // 一行放得下就并排，右侧的空位不再白留。只有本机这组能接——它是每台机器都能做的事。
                            if (hasRestore) {
                                ToolChip(
                                    icon = Icons.Default.Restore,
                                    title = stringResource(Res.string.tweaks_restore_button),
                                    running = step is RestoreStep.Loading || step is RestoreStep.Running,
                                    onClick = { askToRestoreAll() },
                                )
                            }
                        }
                    }
                }
            }
            if (allGroups.isEmpty()) {
                item(key = "no-tools") {
                    Text(
                        text = stringResource(Res.string.tweaks_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }

            // 页底那一行只在「本机这一组没出现」时才有：认不出 ROM（没有 mine）或者这一家没工具，
            // 这时它是页面上唯一的动作，不能跟着分组一起消失。
            if (allGroups.none { (key, _) -> key == mine }) {
                item(key = "separator") { Spacer(modifier = Modifier.height(10.dp)) }
                item(key = "restore-tool") {
                    ToolRow(
                        icon = Icons.Default.Restore,
                        title = stringResource(Res.string.tweaks_restore_button),
                        running = step is RestoreStep.Loading || step is RestoreStep.Running,
                        onClick = { askToRestoreAll() },
                    )
                }
            }
            item(key = "bottom-space") { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }

    detail?.let { tweak ->
        // The way back, when the card has one: the same dialog, its own button and its own script.
        val undo: (() -> Unit)? = tweak.restore?.let { choice ->
            {
                detail = null
                runScript(tweak, choice.command)
            }
        }
        TweakDetailDialog(
            tweak = tweak,
            canRun = runnable(tweak),
            copiedKey = copied,
            onCopy = { key, text ->
                clipboard.setText(AnnotatedString(text))
                copied = key
            },
            onRun = {
                detail = null
                runTweak(tweak)
            },
            onRestore = undo,
            onDismiss = { detail = null },
        )
    }

    asking?.let { tweak ->
        PackageNameDialog(
            tweak = tweak,
            onRun = { packageName ->
                asking = null
                tweak.commandFor(packageName)?.let { script -> runScript(tweak, script) }
            },
            onDismiss = { asking = null },
        )
    }

    when (val current = run) {
        null -> Unit
        is RunStep.Running -> BusyDialog(
            text = stringResource(Res.string.tweaks_running, current.tweak.title),
            showSpinner = true,
            onDismiss = {},
        )
        is RunStep.Finished -> TweakResultDialog(
            tweak = current.tweak,
            run = current.run,
            onDismiss = { run = null },
        )
    }

    when (val current = step) {
        null -> Unit
        RestoreStep.Loading -> BusyDialog(
            text = stringResource(Res.string.tweaks_restore_loading),
            showSpinner = true,
            onDismiss = {},
        )
        RestoreStep.Nothing -> BusyDialog(text = stringResource(Res.string.tweaks_restore_nothing), onDismiss = { step = null })
        RestoreStep.NeedsPrivilege ->
            BusyDialog(text = stringResource(Res.string.tweaks_restore_needs_privilege), onDismiss = { step = null })
        is RestoreStep.Confirm -> RestoreConfirmDialog(
            packages = current.packages,
            onDismiss = { step = null },
            onConfirm = { restoreAll(current.packages) },
        )
        is RestoreStep.Running -> BusyDialog(
            text = stringResource(Res.string.tweaks_restore_running, current.done, current.total),
            showSpinner = true,
            onDismiss = {},
        )
        is RestoreStep.Done -> BusyDialog(
            text = stringResource(Res.string.tweaks_restore_done, current.restored, current.failed) +
                // The link is printed rather than only linked: a phone that could not take its apps
                // back is exactly the case where someone passes the download page on by hand.
                if (current.failed > 0) {
                    "\n\n装不回去的可以到我们的 GitHub 下载页取安装包：$DOWNLOAD_PAGE_URL"
                } else {
                    ""
                },
            onDismiss = { step = null },
        )
    }
}

/** 组名：小米这一组直接叫「米系工具」，其余仍用厂商全称。 */
private fun groupTitle(family: String): String = when (family) {
    "xiaomi" -> "米系工具"
    else -> familyLabel(family)
}

/** The icon a card shows: one per kind of action, so the button reads as the same job everywhere. */
private fun iconOf(tweak: RomTweak): ImageVector = when (tweak.action) {
    RomTweaks.ACTION_SHELL, RomTweaks.ACTION_SHELL_WITH_PACKAGE -> Icons.Default.Bolt
    RomTweaks.ACTION_RESTORE_ALL -> Icons.Default.Restore
    else -> Icons.Default.Info
}

/**
 * 一个厂商的折叠标题：左边是组名（小米就叫「米系工具」），右边是项数与展开箭头。
 *
 * 本机那个厂商用主题色，和别的厂商区分开——这些才是真能在这台手机上跑的。
 */
@Composable
private fun GroupHeader(
    title: String,
    count: Int,
    mine: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (mine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$count 项",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 一个工具，做成自适应宽度的小块：图标 + 名字，宽度只跟着文字走，由 [FlowRow] 决定一行放几个。
 *
 * 没有右侧箭头：整块就是一个可点的目标，箭头只会白占一截宽度。
 */
@Composable
private fun ToolChip(
    icon: ImageVector,
    title: String,
    badge: String? = null,
    running: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box {
            Row(
                // 角上有字的时候顶上多留 3dp，让标题和那一小块字错开，不用把它挤成一整行。
                modifier = Modifier.padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = if (badge == null) 9.dp else 12.dp,
                    bottom = 9.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 条件写在角上，不写在名字后面：`停用应用（root）` 比 `停用应用` 宽出一大截，
            // 一行就少放一个，右边剩下的空位全是浪费。
            if (badge != null) {
                Text(
                    text = badge,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 3.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 3.dp),
                )
            }
            if (running) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                }
            }
        }
    }
}

/**
 * 一个工具：图标 + 名字，整行可点。
 *
 * 行上没有执行按钮：点它就是弹详情，执行在弹窗里。一页上堆六七个圆形 ▶ 只会让人分不清
 * 「这行是打开还是执行」，而且真要执行的东西本来就需要先读一遍弹窗里的说明。
 * [running] 只用来在这一行显示一个转圈，表示这个工具正在跑（比如恢复已卸载的应用）。
 */
@Composable
private fun ToolRow(
    icon: ImageVector,
    title: String,
    running: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/** Full explanation plus the command as text, so it can be copied and run by hand. */
@Composable
private fun TweakDetailDialog(
    tweak: RomTweak,
    canRun: Boolean,
    copiedKey: String?,
    onCopy: (key: String, text: String) -> Unit,
    onRun: () -> Unit,
    onRestore: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = tweak.title,
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            val undo = tweak.restore
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            // A card that can be undone gets both buttons here instead of a second card: the two are
            // one switch, and a switch is read as two buttons, not two entries on a list.
            if (undo != null && onRestore != null) {
                TextButton(onClick = onRestore) { Text(undo.label) }
            }
            if (canRun) {
                TextButton(onClick = onRun) {
                    Text(tweak.runLabel.ifEmpty { stringResource(Res.string.tweaks_run) })
                }
            }
        },
    ) {
        // What the card demands comes before what it does: a phone without that privilege gets
        // nothing but a refusal from the button, so it has to be visible before the button is used.
        if (tweak.needs.isNotEmpty()) {
            Text(
                text = tweak.needs,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (tweak.why.isNotEmpty()) {
            Text(
                text = tweak.why,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (tweak.command.isNotEmpty()) {
            // Two scripts means two blocks: each one has to be copyable on its own, or the way back
            // is only reachable through a button the phone may refuse.
            CommandBlock(
                label = if (tweak.restore != null) tweak.runLabel else "",
                command = tweak.command,
                copied = copiedKey == COPY_KEY_RUN,
                onCopy = { onCopy(COPY_KEY_RUN, tweak.command) },
            )
        }
        tweak.restore?.let { choice ->
            CommandBlock(
                label = choice.label,
                command = choice.command,
                copied = copiedKey == COPY_KEY_RESTORE,
                onCopy = { onCopy(COPY_KEY_RESTORE, choice.command) },
            )
        }
    }
}

/** One copyable script in the detail dialog, with an optional name above it. */
@Composable
private fun CommandBlock(
    label: String,
    command: String,
    copied: Boolean,
    onCopy: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            TextButton(onClick = onCopy, modifier = Modifier.align(Alignment.End)) {
                Text(
                    text = stringResource(if (copied) Res.string.tweaks_copied else Res.string.tweaks_copy_command),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * Asks for the package a prompt-driven card needs.
 *
 * The name is checked here as the user types, so the reason a name is refused is visible before the
 * button is pressed: a command assembled from free text would otherwise be a shell injection point,
 * and the few packages that keep the phone usable are refused outright (see [NEVER_TOUCH]).
 */
@Composable
private fun PackageNameDialog(
    tweak: RomTweak,
    onRun: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val name = text.trim()
    val usable = tweak.commandFor(name) != null
    CantaDialog(
        onDismissRequest = onDismiss,
        title = tweak.title,
        widthFraction = WIDE_DIALOG_WIDTH,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = { onRun(name) }, enabled = usable) {
                Text(stringResource(Res.string.tweaks_run))
            }
        },
    ) {
        if (tweak.why.isNotEmpty()) {
            Text(
                text = tweak.why,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            label = { Text(tweak.prompt) },
            placeholder = { Text("com.example.app") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        if (name.isNotEmpty() && !usable) {
            Text(
                text = stringResource(Res.string.tweaks_package_refused),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** What the script printed, or why it never ran. */
@Composable
private fun TweakResultDialog(    tweak: RomTweak,
    run: TweakRun,
    onDismiss: () -> Unit,
) {
    var failure: String? = null
    when (run.failure) {
        TweakFailure.Unsupported -> failure = stringResource(Res.string.tweaks_run_unsupported)
        TweakFailure.NoPrivilege -> failure = stringResource(Res.string.tweaks_run_no_privilege)
        TweakFailure.Timeout -> failure = stringResource(Res.string.tweaks_run_timeout)
        TweakFailure.Broken -> failure = stringResource(Res.string.tweaks_run_broken)
        null -> Unit
    }
    CantaDialog(
        onDismissRequest = onDismiss,
        title = tweak.title,
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.got_it)) }
        },
    ) {
        Text(
            text = failure ?: stringResource(if (run.ok) Res.string.tweaks_run_done else Res.string.tweaks_run_not_ok),
            style = MaterialTheme.typography.bodyMedium,
            color = if (run.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (run.output.isNotBlank()) {
            Text(
                text = run.output,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                    .padding(10.dp),
            )
        }
        if (run.output.contains(SERVICE_MISSING_MARKER)) {
            Text(
                text = stringResource(Res.string.tweaks_splash_needs_service),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Asks before putting [packages] back, and lists what is about to change. */
@Composable
private fun RestoreConfirmDialog(
    packages: List<String>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val shown = packages.take(MAX_SHOWN_PACKAGES)
    CantaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.tweaks_restore_confirm_title),
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.tweaks_restore_start)) }
        },
    ) {
        Text(
            text = stringResource(Res.string.tweaks_restore_confirm_body, packages.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = shown.joinToString("\n") + if (shown.size < packages.size) "\n…" else "",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** A one-line report: what is happening now, or what just happened. */
@Composable
private fun BusyDialog(
    text: String,
    showSpinner: Boolean = false,
    onDismiss: () -> Unit,
) {
    // Passing the buttons as a typed local keeps the conditional out of the call site: a lambda
    // with a RowScope receiver cannot be inferred from an `if` branch on its own.
    val buttons: (@Composable RowScope.() -> Unit)? = if (showSpinner) {
        null
    } else {
        { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.got_it)) } }
    }

    CantaDialog(
        onDismissRequest = onDismiss,
        dismissible = !showSpinner,
        buttons = buttons,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showSpinner) CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
