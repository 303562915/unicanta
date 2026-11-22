package io.github.samolego.canta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import io.github.samolego.canta.ui.fl.FlAccent
import io.github.samolego.canta.ui.fl.FlTheme

/**
 * Canta's theme. The palette, type scale and shapes are FlClash's, seeded with its
 * default accent `#007AFF`: the app looks the same on every phone rather than tinting
 * itself after the wallpaper. [accent] is the color picked in the settings, which only
 * re-derives the accent roles and leaves the surfaces where FlClash put them.
 */
@Composable
fun CantaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: FlAccent = FlAccent.Default,
    content: @Composable () -> Unit
) {
    FlTheme(darkTheme = darkTheme, accent = accent, content = content)
}
