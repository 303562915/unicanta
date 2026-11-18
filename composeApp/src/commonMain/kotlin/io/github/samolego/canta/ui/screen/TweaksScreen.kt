package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.tweaks_copied
import io.github.samolego.canta.generated.resources.tweaks_copy_path
import io.github.samolego.canta.generated.resources.tweaks_steps
import io.github.samolego.canta.generated.resources.tweaks_title
import io.github.samolego.canta.generated.resources.tweaks_your_rom
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.RomTweak
import io.github.samolego.canta.util.RomTweaks
import io.github.samolego.canta.util.familyKeyOf
import io.github.samolego.canta.util.familyLabel
import org.jetbrains.compose.resources.stringResource

/**
 * Workarounds for things the stock system does, grouped by ROM family with the phone's own family
 * first. Everything that needs root or a shell is written as steps rather than executed: replacing
 * files under `Android/data` from inside the app is not possible anyway, and pretending otherwise
 * would just fail.
 */
@Composable
fun TweaksScreen(
    handler: CantaHandler,
    onNavigateBack: () -> Unit,
) {
    var profile by remember { mutableStateOf<DeviceProfile?>(null) }
    var tweaks by remember { mutableStateOf<RomTweaks?>(null) }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        profile = handler.deviceProfile()
        tweaks = RomTweaks.bundled()
    }

    val groups = remember(tweaks, profile) { tweaks?.groupedFor(profile).orEmpty() }
    val mine = profile?.let { familyKeyOf(it.rom) }

    Scaffold(
        topBar = {
            ScreenTopBar(onNavigateBack = onNavigateBack, title = { Text(stringResource(Res.string.tweaks_title)) })
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            groups.forEach { (family, list) ->
                item(key = "head-$family") {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)) {
                        Text(
                            text = familyLabel(family),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (family == mine) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (family == mine) {
                            Text(
                                text = stringResource(Res.string.tweaks_your_rom),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                items(list, key = { it.id }) { tweak ->
                    TweakCard(
                        tweak = tweak,
                        copied = copied == tweak.id,
                        onCopy = { path ->
                            clipboard.setText(AnnotatedString(path))
                            copied = tweak.id
                        },
                    )
                }
                item(key = "div-$family") { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

@Composable
private fun TweakCard(tweak: RomTweak, copied: Boolean, onCopy: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tweak.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (tweak.why.isNotEmpty()) {
                Text(
                    text = tweak.why,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tweak.steps.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.tweaks_steps),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                tweak.steps.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall)
                }
            }
            tweak.paths.forEach { path ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onCopy(path) }) {
                        Text(
                            stringResource(
                                if (copied) Res.string.tweaks_copied else Res.string.tweaks_copy_path
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            if (tweak.packages.isNotEmpty()) {
                Text(
                    text = tweak.packages.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
