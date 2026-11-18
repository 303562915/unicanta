package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.rules_device_title
import io.github.samolego.canta.generated.resources.rules_empty
import io.github.samolego.canta.generated.resources.rules_scope_note
import io.github.samolego.canta.generated.resources.rules_title
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.SafetyRule
import io.github.samolego.canta.util.SafetyRules
import io.github.samolego.canta.util.SafetyStatus
import org.jetbrains.compose.resources.stringResource

/**
 * Which packages may be removed on *this* phone. The rules are matched against the model, the
 * ROM family and the ROM version, so the same package can be safe on one device and load-bearing
 * on another; a package no rule mentions stays unknown rather than safe.
 */
@Composable
fun SafetyRulesScreen(
    handler: CantaHandler,
    onNavigateBack: () -> Unit,
) {
    var profile by remember { mutableStateOf<DeviceProfile?>(null) }
    var rules by remember { mutableStateOf<SafetyRules?>(null) }

    LaunchedEffect(Unit) {
        profile = handler.deviceProfile()
        rules = SafetyRules.bundled()
    }

    val all: List<SafetyRule> = rules?.all.orEmpty()
    val applicable = remember(all, profile) { all.filter { it.matches(profile) } }
    val grouped = remember(applicable) {
        SafetyStatus.entries.map { status -> status to applicable.filter { it.safety == status } }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(onNavigateBack = onNavigateBack, title = { Text(stringResource(Res.string.rules_title)) })
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = stringResource(Res.string.rules_device_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = profile?.label() ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    profile?.fingerprint?.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(Res.string.rules_scope_note, applicable.size, all.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                HorizontalDivider()
            }
            if (applicable.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.rules_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            grouped.forEach { (status, entries) ->
                if (entries.isEmpty()) return@forEach
                item(key = "header-${status.name}") {
                    Text(
                        text = statusLabel(status),
                        style = MaterialTheme.typography.titleSmall,
                        color = when (status) {
                            SafetyStatus.CRITICAL -> MaterialTheme.colorScheme.error
                            SafetyStatus.RISKY -> MaterialTheme.colorScheme.tertiary
                            SafetyStatus.SAFE -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(entries, key = { "${status.name}-${it.pkg}-${it.scope}" }) { rule ->
                    RuleRow(rule)
                }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: SafetyRule) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(rule.pkg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = rule.scope,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (rule.why.isNotEmpty()) {
            Text(
                text = rule.why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusLabel(status: SafetyStatus): String = when (status) {
    SafetyStatus.CRITICAL -> "禁区 · 会导致开不了机"
    SafetyStatus.RISKY -> "有代价 · 会破坏部分功能"
    SafetyStatus.SAFE -> "本机可卸"
}
