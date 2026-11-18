package io.github.samolego.canta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import io.github.samolego.canta.ui.fl.FlTheme

/**
 * Canta's theme. The palette, type scale and shapes are FlClash's, seeded with its
 * default accent `#007AFF`: the app looks the same on every phone rather than tinting
 * itself after the wallpaper.
 */
@Composable
fun CantaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    FlTheme(darkTheme = darkTheme, content = content)
}
