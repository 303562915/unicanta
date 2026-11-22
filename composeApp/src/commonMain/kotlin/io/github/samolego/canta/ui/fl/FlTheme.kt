package io.github.samolego.canta.ui.fl

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The scheme the app is drawing with, for the few places that need more than Material's roles. */
val LocalFlColorScheme = staticCompositionLocalOf { FlPalette.light }

/** Corner radii, from the sheet down to a chip, in FlClash's scale. */
val FlShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Canta's theme: FlClash's palette, type scale and shapes, in the platform's dark mode.
 *
 * There is no Material You here on purpose — the app draws the colors FlClash's default
 * accent generates, so it looks the same on every phone and matches the reference on the
 * ones that would otherwise tint themselves after the wallpaper. The one accent the user
 * picks in the settings is the app's own choice, not the wallpaper's: see [FlAccent].
 */
@Composable
fun FlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** The accent picked in the settings; [FlAccent.Default] is FlClash's own table. */
    accent: FlAccent = FlAccent.Default,
    content: @Composable () -> Unit,
) {
    val scheme = accent.scheme(darkTheme)
    CompositionLocalProvider(LocalFlColorScheme provides scheme) {
        MaterialTheme(
            colorScheme = scheme,
            typography = FlTypography,
            shapes = FlShapes,
            content = content,
        )
    }
}

/** The success fill of the current scheme. */
@Composable
fun successColor(): Color =
    if (LocalFlColorScheme.current.isDark) FlPalette.darkSuccess else FlPalette.lightSuccess

/** The warning fill of the current scheme. */
@Composable
fun warningColor(): Color =
    if (LocalFlColorScheme.current.isDark) FlPalette.darkWarning else FlPalette.lightWarning
