package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.got_it
import io.github.samolego.canta.generated.resources.tweaks_copied
import io.github.samolego.canta.generated.resources.tweaks_copy_command
import io.github.samolego.canta.generated.resources.tweaks_restore_button
import io.github.samolego.canta.generated.resources.tweaks_restore_confirm_body
import io.github.samolego.canta.generated.resources.tweaks_restore_confirm_title
import io.github.samolego.canta.generated.resources.tweaks_restore_done
import io.github.samolego.canta.generated.resources.tweaks_restore_loading
import io.github.samolego.canta.generated.resources.tweaks_restore_needs_privilege
import io.github.samolego.canta.generated.resources.tweaks_restore_nothing
import io.github.samolego.canta.generated.resources.tweaks_restore_running
import io.github.samolego.canta.generated.resources.tweaks_restore_start
import io.github.samolego.canta.generated.resources.tweaks_title
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.util.RomTweak
import io.github.samolego.canta.util.RomTweaks
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Package names listed in the confirmation dialog; the rest is summarised with an ellipsis. */
private const val MAX_SHOWN_PACKAGES = 8

/**
 * What the page's one actionable card is doing right now: nothing, checking what there is to put
 * back, asking first, putting it back, or reporting how it went.
 */
private sealed interface RestoreStep {
    data object Loading : RestoreStep
    data object Nothing : RestoreStep
    data object NeedsPrivilege : RestoreStep
    data class Confirm(val packages: List<String>) : RestoreStep
    data class Running(val done: Int, val total: Int) : RestoreStep
    data class Done(val restored: Int, val failed: Int) : RestoreStep
}

/**
 * The system tweaks page: a flat list of cards, one per tweak. There is no per-vendor grouping —
 * what a phone's ROM is called does not change what the card does — and a card whose action Canta
 * knows gets a button instead of instructions, so restoring the apps a user uninstalled is one tap
 * plus a confirmation instead of a copied shell command.
 */
@Composable
fun TweaksScreen(
    handler: CantaHandler,
    onNavigateBack: () -> Unit,
) {
    var tweaks by remember { mutableStateOf<List<RomTweak>>(emptyList()) }
    var step by remember { mutableStateOf<RestoreStep?>(null) }
    var copied by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { tweaks = RomTweaks.bundled().all }

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

    Scaffold(
        topBar = {
            ScreenTopBar(onNavigateBack = onNavigateBack, title = { Text(stringResource(Res.string.tweaks_title)) })
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(tweaks, key = { it.id }) { tweak ->
                TweakCard(
                    tweak = tweak,
                    copied = copied == tweak.id,
                    onCopy = {
                        clipboard.setText(AnnotatedString(tweak.command))
                        copied = tweak.id
                    },
                    action = when (tweak.action) {
                        RomTweaks.ACTION_RESTORE_ALL ->
                            stringResource(Res.string.tweaks_restore_button) to { askToRestoreAll() }
                        else -> null
                    },
                )
            }
        }
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
            text = stringResource(Res.string.tweaks_restore_done, current.restored, current.failed),
            onDismiss = { step = null },
        )
    }
}

@Composable
private fun TweakCard(
    tweak: RomTweak,
    copied: Boolean,
    onCopy: () -> Unit,
    action: Pair<String, () -> Unit>?,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tweak.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (tweak.why.isNotEmpty()) {
                Text(
                    text = tweak.why,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tweak.command.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
            if (action != null) {
                Button(onClick = action.second, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(text = action.first, modifier = Modifier.padding(start = 8.dp))
                }
            }
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
