package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.settings_group_theme
import io.github.samolego.canta.generated.resources.theme_accent
import io.github.samolego.canta.generated.resources.theme_mode
import io.github.samolego.canta.generated.resources.theme_mode_dark
import io.github.samolego.canta.generated.resources.theme_mode_light
import io.github.samolego.canta.generated.resources.theme_mode_system
import io.github.samolego.canta.generated.resources.theme_preview
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsGroup
import io.github.samolego.canta.ui.fl.FlAccent
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import io.github.samolego.canta.ui.fl.FlThemeMode
import io.github.samolego.canta.ui.fl.relativeLuminance
import io.github.samolego.canta.ui.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.stringResource

/** Side inset inside a card: the same 14dp the rows of the settings lists use. */
private val ThemeRowInset = 14.dp

/** How many accent swatches share a row; twelve accents make two even rows of six. */
private const val ACCENTS_PER_ROW = 6

/** Diameter of one accent swatch. */
private val AccentSwatchSize = 34.dp

/** Space between two cards. */
private val ThemeGroupGap = 12.dp

/**
 * The theme page: a live preview of the accent, the twelve swatches, and the dark-mode choice.
 *
 * It is the reference app's `ThemeActivity` in Compose — same order, same controls — with the one
 * difference Compose allows: nothing here has to be recreated for the change to take. The theme is
 * applied above the navigation, so picking a color repaints every page, this one included, the
 * moment the value is stored.
 */
@Composable
fun ThemeScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
) {
    val themeAccent by settingsViewModel.themeAccent.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val accent = FlAccent.ofArgb(themeAccent)

    Scaffold(
        topBar = {
            ScreenTopBar(
                onNavigateBack = onNavigateBack,
                title = stringResource(Res.string.settings_group_theme),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            SettingsGroup { ThemePreview(accent) }

            Spacer(modifier = Modifier.height(ThemeGroupGap))
            SettingsGroup(title = stringResource(Res.string.theme_accent)) {
                ThemeAccentPicker(
                    selected = accent,
                    onSelect = { settingsViewModel.saveThemeAccent(it.argb) },
                )
            }

            Spacer(modifier = Modifier.height(ThemeGroupGap))
            SettingsGroup(title = stringResource(Res.string.theme_mode)) {
                FlSegmentedControl(
                    segments = listOf(
                        FlSegment("system", stringResource(Res.string.theme_mode_system)),
                        FlSegment("light", stringResource(Res.string.theme_mode_light)),
                        FlSegment("dark", stringResource(Res.string.theme_mode_dark)),
                    ),
                    selectedIndex = FlThemeMode.ofStored(themeMode).ordinal,
                    onSelect = { settingsViewModel.saveThemeMode(FlThemeMode.entries[it].stored) },
                    insetHorizontal = ThemeRowInset,
                )
            }
        }
    }
}

/** The accent as the app actually fills things: a pill on primary, a dot on its container. */
@Composable
private fun ThemePreview(accent: FlAccent) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ThemeRowInset, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.primary)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = accent.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onPrimary,
            )
        }
        Spacer(modifier = Modifier.size(10.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.primaryContainer),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(Res.string.theme_preview),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * The accent swatches, six to a row. Each one is a bare circle — no caption, the way the color
 * pickers of both reference apps draw them — and the selected one is ringed and checked rather
 * than filled with a badge over its own color.
 */
@Composable
private fun ThemeAccentPicker(selected: FlAccent, onSelect: (FlAccent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ThemeRowInset, vertical = 10.dp)
    ) {
        FlAccent.entries.chunked(ACCENTS_PER_ROW).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { accent ->
                    AccentSwatch(
                        accent = accent,
                        selected = accent == selected,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
                // The last row may be short; the empty slots keep the columns aligned.
                repeat(ACCENTS_PER_ROW - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    accent: FlAccent,
    selected: Boolean,
    onSelect: (FlAccent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // A ring in the text color rather than in primary: on the accent's own circle a primary
    // ring would be the very same color and read as no ring at all.
    val ring = if (selected) scheme.onSurface else scheme.outlineVariant.copy(alpha = 0.6f)
    val label = stringResource(Res.string.theme_accent) + " " + accent.label
    Box(modifier = modifier.padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(AccentSwatchSize)
                .clip(CircleShape)
                .background(accent.color)
                .border(width = if (selected) 2.dp else 1.dp, color = ring, shape = CircleShape)
                .semantics {
                    this.selected = selected
                    contentDescription = label
                }
                .clickable(role = Role.RadioButton) { onSelect(accent) },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                // The check has to read on amber and lime as well as on indigo.
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (accent.color.relativeLuminance() > 0.45f) Color(0xFF16181D) else Color.White,
                )
            }
        }
    }
}