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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
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
 * The tools page: a short list of things Canta can do to this ROM, each one tap away.
 *
 * Only vendors that actually ship a tool get a heading — an OPPO owner sees no Xiaomi card and no
 * empty "OPPO" section either; a vendor is added by adding its tweak to `rom_tweaks.json`. The one
 * tool every ROM has, restoring the apps uninstalled for this user, sits on its own at the bottom
 * instead of being copied into each vendor group.
 */
@Composable
fun TweaksScreen(
    handler: CantaHandler,
    onNavigateBack: () -> Unit,
) {
    var profile by remember { mutableStateOf<DeviceProfile?>(null) }
    var tweaks by remember { mutableStateOf<RomTweaks?>(null) }
    var step by remember { mutableStateOf<RestoreStep?>(null) }
    var run by remember { mutableStateOf<RunStep?>(null) }
    var detail by remember { mutableStateOf<RomTweak?>(null) }
    var copied by remember { mutableStateOf<String?>(null) }
    /** The card waiting for a package name; null when nothing is being asked. */
    var asking by remember { mutableStateOf<RomTweak?>(null) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        profile = handler.deviceProfile()
        tweaks = RomTweaks.bundled()
    }

    val groups = remember(tweaks, profile) { tweaks?.groupedFor(profile).orEmpty() }
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

    /** Runs whatever the card's `action` asks for: a shipped script, or a job Canta does itself. */
    fun runTweak(tweak: RomTweak) {
        when (tweak.action) {
            RomTweaks.ACTION_RESTORE_ALL -> askToRestoreAll()
            RomTweaks.ACTION_SHELL -> {
                if (tweak.command.isBlank()) return
                scope.launch {
                    run = RunStep.Running(tweak)
                    run = RunStep.Finished(tweak, handler.runTweakCommand(tweak.command))
                }
            }
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
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            groups.forEach { (family, list) ->
                item(key = "head-$family") { GroupHeading(family = family, mine = family == mine) }
                items(list, key = { it.id }) { tweak ->
                    ToolRow(
                        icon = iconOf(tweak),
                        title = tweak.title,
                        // A card Canta cannot act on gets no button; its explanation lives in the detail.
                        canRun = runnable(tweak),
                        running = (run as? RunStep.Running)?.tweak?.id == tweak.id,
                        onRun = { runTweak(tweak) },
                        onClick = { detail = tweak },
                    )
                }
            }
            if (groups.isEmpty()) {
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
        TweakDetailDialog(
            tweak = tweak,
            canRun = runnable(tweak),
            copied = copied == tweak.id,
            onCopy = {
                clipboard.setText(AnnotatedString(tweak.command))
                copied = tweak.id
            },
            onRun = {
                detail = null
                runTweak(tweak)
            },
            onDismiss = { detail = null },
        )
    }

    asking?.let { tweak ->
        PackageNameDialog(
            tweak = tweak,
            onRun = { packageName ->
                asking = null
                val script = tweak.commandFor(packageName)
                if (script != null) {
                    scope.launch {
                        run = RunStep.Running(tweak)
                        run = RunStep.Finished(tweak, handler.runTweakCommand(script))
                    }
                }
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
    copied: Boolean,
    onCopy: () -> Unit,
    onRun: () -> Unit,
    onDismiss: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = tweak.title,
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            if (canRun) {
                TextButton(onClick = onRun) { Text(stringResource(Res.string.tweaks_run)) }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = tweak.command,
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
