package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
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
import io.github.samolego.canta.generated.resources.tweaks_this_device
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
 * The tools page, in two layers: a list of vendors, and one vendor's tools.
 *
 * With [family] null this is the page the bottom bar leads to: one row per vendor that ships a
 * tool, the phone's own vendor first. With [family] set it is that vendor's page — its tools, and
 * for Xiaomi the row that opens the audio-whitelist page, which is a page of its own because the
 * list behind it is dozens of rows long.
 *
 * The two layers are one composable rather than two: the run/restore/dialog machinery below is the
 * same on both, and a second copy of it would drift.
 *
 * Only vendors that actually ship a tool get a row — an OPPO owner sees no Xiaomi entry and no
 * empty "OPPO" section either; a vendor is added by adding its tweak to `rom_tweaks.json`. The one
 * tool every ROM has, restoring the apps uninstalled for this user, sits at the bottom.
 */
@Composable
fun TweaksScreen(
    handler: CantaHandler,
    onOpenAudio: () -> Unit,
    onOpenFamily: (String) -> Unit,
    onNavigateBack: () -> Unit,
    family: String? = null,
) {
    var profile by remember { mutableStateOf<DeviceProfile?>(null) }
    var tweaks by remember { mutableStateOf<RomTweaks?>(null) }
    var step by remember { mutableStateOf<RestoreStep?>(null) }
    var run by remember { mutableStateOf<RunStep?>(null) }
    var detail by remember { mutableStateOf<RomTweak?>(null) }
    var copied by remember { mutableStateOf<String?>(null) }
    /** The card waiting for a package name; null when nothing is being asked. */
    var asking by remember { mutableStateOf<RomTweak?>(null) }
    /** 这台机器有没有音频白名单配置；null 表示还在问（或不在这层问）。 */
    var audioDetected by remember { mutableStateOf<Boolean?>(null) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    // 双击底栏「工具」那一格：这一页回顶部。
    val tweakListState = rememberLazyListState()
    ScrollToTopEffect(tweakListState)

    LaunchedEffect(Unit) {
        profile = handler.deviceProfile()
        tweaks = RomTweaks.bundled()
        // 音频白名单这一行要先知道本机有没有那套配置：没有就写在标题上，别让人点进去看一个空转的开关。
        if (family == familyKeyOf("hyperos") && handler.supportsTweakCommands) {
            audioDetected = runCatching {
                handler.runTweakCommand(AudioWhitelist.probeScript()).output.contains(AudioWhitelist.PROBE_YES)
            }.getOrNull()
        }
    }

    val allGroups = remember(tweaks, profile) { tweaks?.groupedFor(profile).orEmpty() }
    val mine = profile?.let { familyKeyOf(it.rom) }
    // 目录层只列厂商；进了某一层就只留那一组，标题也换成厂商名。
    val groups = if (family == null) emptyList() else allGroups.filter { (key, _) -> key == family }

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
            ScreenTopBar(
                // 目录层是底栏自己的一页，不画返回箭头；厂商层是压在它上面的一页。
                onNavigateBack = if (family == null) null else onNavigateBack,
                title = family?.let { familyLabel(it) } ?: stringResource(Res.string.tweaks_title),
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), state = tweakListState) {
            if (family == null) {
                // 目录层：一个厂商一行，本机那个排在最前（groupedFor 已经把它排好）。
                items(allGroups, key = { (key, _) -> "fam-$key" }) { (key, list) ->
                    ToolRow(
                        icon = Icons.Default.Build,
                        title = familyLabel(key) + " · " + list.size + " 个工具" +
                            if (key == mine) "（本机）" else "",
                        canRun = false,
                        running = false,
                        onRun = {},
                        onClick = { onOpenFamily(key) },
                    )
                }
            } else {
                groups.forEach { (key, list) ->
                    item(key = "head-$key") { GroupHeading(family = key, mine = key == mine) }
                    items(list, key = { it.id }) { tweak ->
                        ToolRow(
                            icon = iconOf(tweak),
                            title = tweak.title,
                            // A card Canta cannot act on gets no button; its explanation lives in the detail.
                            canRun = runnable(tweak),
                            running = (run as? RunStep.Running)?.tweak?.id == tweak.id,
                            onRun = { runTweak(tweak) },
                            onClick = {
                                // Which script was copied last belongs to the dialog, not to the page.
                                copied = null
                                detail = tweak
                            },
                        )
                    }
                    // 音频白名单单独一页：那份名单有几十行，弹窗里只能读，页面上能筛、能开合。
                    if (key == familyKeyOf("hyperos")) {
                        item(key = "audio-page") {
                            ToolRow(
                                icon = Icons.Default.ViewList,
                                // 三态标题：检查中 / 有这套配置 / 本机没有（仍然可进，里面是诊断）。
                                title = when (audioDetected) {
                                    null -> "音频白名单 · 检查中…"
                                    true -> "音频白名单 · 看名单与开关"
                                    false -> "音频白名单 · 本机没有这套配置（进去看诊断）"
                                },
                                canRun = false,
                                running = false,
                                onRun = {},
                                onClick = onOpenAudio,
                            )
                        }
                    }
                }
            }
            if ((family == null && allGroups.isEmpty()) || (family != null && groups.isEmpty())) {
                item(key = "no-tools") {
                    Text(
                        text = stringResource(Res.string.tweaks_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }

            // The one tool every ROM has: last on the page, not repeated under each vendor.
            item(key = "separator") { Spacer(modifier = Modifier.height(10.dp)) }
            item(key = "restore-tool") {
                ToolRow(
                    icon = Icons.Default.Restore,
                    title = stringResource(Res.string.tweaks_restore_button),
                    canRun = true,
                    running = step is RestoreStep.Loading || step is RestoreStep.Running,
                    onRun = { askToRestoreAll() },
                    onClick = null,
                )
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

/** The icon a card shows: one per kind of action, so the button reads as the same job everywhere. */
private fun iconOf(tweak: RomTweak): ImageVector = when (tweak.action) {
    RomTweaks.ACTION_SHELL, RomTweaks.ACTION_SHELL_WITH_PACKAGE -> Icons.Default.Bolt
    RomTweaks.ACTION_RESTORE_ALL -> Icons.Default.Restore
    else -> Icons.Default.Info
}

/**
 * Heading above a vendor group. The phone's own group is headed by what that means for the user —
 * "本机可用" — instead of the vendor string plus a second "你的系统" line, so the section says at a
 * glance that these are the tools that can actually run here.
 */
@Composable
private fun GroupHeading(family: String, mine: Boolean) {
    Text(
        text = if (mine) stringResource(Res.string.tweaks_this_device) else familyLabel(family),
        style = MaterialTheme.typography.titleSmall,
        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp),
    )
}

/**
 * One tool: icon, name, and — when it can actually do something — an icon button that runs it.
 * Tapping the row itself opens the full text and the copyable command, so the row stays one line
 * and does not repeat the explanation that dialog shows.
 */
@Composable
private fun ToolRow(
    icon: ImageVector,
    title: String,
    canRun: Boolean,
    running: Boolean,
    onRun: () -> Unit,
    onClick: (() -> Unit)?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
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
            if (canRun) {
                FilledTonalIconButton(
                    onClick = onRun,
                    enabled = !running,
                    modifier = Modifier.size(40.dp),
                ) {
                    if (running) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(Res.string.tweaks_run),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
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
