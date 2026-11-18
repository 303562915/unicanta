package io.github.samolego.canta.ui.fl

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Canta's colors, generated the way FlClash generates its own: a vivid scheme seeded
 * with iOS blue `#007AFF`, the app's default accent, in the HCT color space with the
 * "content" variant, a surface chroma held down to 3 so paper stays paper, and cool
 * dark greys that step up rather than down.
 *
 * The values are precomputed from that seed (see `docs/theme.md`), so no color math
 * runs at startup.
 */
object FlPalette {
    /** The accent FlClash ships as its default. */
    val seed = Color(0xFF007AFF)

    val light: ColorScheme = lightColorScheme(
        primary = Color(0xFF007AFF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD8E2FF),
        onPrimaryContainer = Color(0xFF0E448E),
        inversePrimary = Color(0xFFADC6FF),
        secondary = Color(0xFF405E96),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFACC6FF),
        onSecondaryContainer = Color(0xFF11346B),
        tertiary = Color(0xFF801AA4),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFA949CD),
        onTertiaryContainer = Color(0xFFFFFFFF),
        background = Color(0xFFEFEDF0),
        onBackground = Color(0xFF181C23),
        surface = Color(0xFFEFEDF0),
        onSurface = Color(0xFF181C23),
        onSurfaceVariant = Color(0xFF414755),
        surfaceTint = Color(0xFF007AFF),
        inverseSurface = Color(0xFF2D3039),
        inverseOnSurface = Color(0xFFEEF0FC),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF717786),
        outlineVariant = Color(0xFFC7C6CA),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFCF8F9),
        surfaceDim = Color(0xFFDBD9DC),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F3F6),
        surfaceContainer = Color(0xFFFCF8F9),
        surfaceContainerHigh = Color(0xFFEFEDF0),
        surfaceContainerHighest = Color(0xFFE4E2E4),
    )

    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFF2F83FF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF0E448E),
        onPrimaryContainer = Color(0xFFD8E2FF),
        inversePrimary = Color(0xFF005BC1),
        secondary = Color(0xFFADC6FF),
        onSecondary = Color(0xFF082F65),
        secondaryContainer = Color(0xFF1B3C73),
        onSecondaryContainer = Color(0xFFBED1FF),
        tertiary = Color(0xFFEEB1FF),
        onTertiary = Color(0xFF53006F),
        tertiaryContainer = Color(0xFFA949CD),
        onTertiaryContainer = Color(0xFFFFFFFF),
        background = Color(0xFF131315),
        onBackground = Color(0xFFF5F6FF),
        surface = Color(0xFF131315),
        onSurface = Color(0xFFF5F6FF),
        onSurfaceVariant = Color(0xFFC1C6D7),
        surfaceTint = Color(0xFF2F83FF),
        inverseSurface = Color(0xFFE0E2ED),
        inverseOnSurface = Color(0xFF2D3039),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8B90A0),
        outlineVariant = Color(0xFF46464A),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF39393B),
        surfaceDim = Color(0xFF131315),
        surfaceContainerLowest = Color(0xFF0D0E10),
        surfaceContainerLow = Color(0xFF1B1B1E),
        surfaceContainer = Color(0xFF252628),
        surfaceContainerHigh = Color(0xFF323235),
        surfaceContainerHighest = Color(0xFF404042),
    )

    /** Green for anything that succeeded, as FlClash tones its success palette. */
    val lightSuccess = Color(0xFF006E1C)
    val lightOnSuccess = Color(0xFFFFFFFF)
    val lightSuccessContainer = Color(0xFFD5E8CF)
    val lightOnSuccessContainer = Color(0xFF005313)

    val darkSuccess = Color(0xFF78DC77)
    val darkOnSuccess = Color(0xFF00390A)
    val darkSuccessContainer = Color(0xFF3B4B38)
    val darkOnSuccessContainer = Color(0xFF94F990)

    val lightWarning = Color(0xFF8B5000)
    val lightOnWarning = Color(0xFFFFFFFF)
    val lightWarningContainer = Color(0xFFFFDCBE)
    val lightOnWarningContainer = Color(0xFF693C00)

    val darkWarning = Color(0xFFFFB870)
    val darkOnWarning = Color(0xFF4A2800)
    val darkWarningContainer = Color(0xFF59422C)
    val darkOnWarningContainer = Color(0xFFFFDCBE)
}

/**
 * FlClash's four surface levels. A page sits on [page]; anything raised out of it is a
 * step lighter in dark mode and a step lighter in light mode too, so a card never
 * sinks into the page it stands on.
 */
enum class FlSurfaceLevel {
    Page,
    Card,
    Block,
    Inset;

    /** The level a floating bar or a bar button stands on. */
    val raised: FlSurfaceLevel
        get() = when (this) {
            Page -> Card
            Card -> Block
            Block, Inset -> Inset
        }

    companion object {
        /** What a dock or a floating bar is filled with. */
        fun floatingOf(scheme: ColorScheme): FlSurfaceLevel =
            if (scheme.isDark) Block else Card
    }
}

/** Resolves a surface level against a scheme, the way prism_ui's `SurfaceLevel` does. */
fun FlSurfaceLevel.colorOf(scheme: ColorScheme): Color {
    val dark = scheme.isDark
    return when (this) {
        FlSurfaceLevel.Page -> if (dark) scheme.surface else scheme.surfaceContainer
        FlSurfaceLevel.Card -> if (dark) scheme.surfaceContainer else scheme.surfaceBright
        FlSurfaceLevel.Block -> if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainer
        FlSurfaceLevel.Inset -> scheme.surfaceContainerHighest
    }
}

/** Whether a scheme is the dark one. */
val ColorScheme.isDark: Boolean
    get() = (surface.red + surface.green + surface.blue) / 3f < 0.5f
