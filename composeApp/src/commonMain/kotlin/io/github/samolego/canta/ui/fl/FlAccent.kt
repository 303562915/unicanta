package io.github.samolego.canta.ui.fl

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * The accent colors the settings page offers: the twelve the reference app (tv) ships, the same
 * hues in the same order.
 *
 * [Blue] is FlClash's own `#007AFF`, i.e. the seed [FlPalette] was generated from, so a fresh
 * install draws exactly the palette in that file. Every other accent is derived from the base
 * scheme at runtime ([scheme]) instead of being precomputed: twelve seeds × two schemes would be
 * 24 hand-checked tables, and the derivation only touches the accent roles, which is what "theme
 * color" means — the paper the app is printed on stays FlClash's.
 */
enum class FlAccent(val argb: Int) {
    Blue(0xFF007AFF.toInt()),
    Red(0xFFF44336.toInt()),
    Orange(0xFFFF9800.toInt()),
    Amber(0xFFFFC107.toInt()),
    Lime(0xFFCDDC39.toInt()),
    Green(0xFF4CAF50.toInt()),
    Teal(0xFF009688.toInt()),
    Cyan(0xFF00BCD4.toInt()),
    Indigo(0xFF3F51B5.toInt()),
    Purple(0xFF9C27B0.toInt()),
    DeepPurple(0xFF673AB7.toInt()),
    Pink(0xFFE91E63.toInt());

    val color: Color get() = Color(argb)

    /** `#007AFF`: the label of a swatch, which carries no text of its own. */
    val label: String get() = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

    companion object {
        /** What the app draws before anyone picks something: the FlClash accent. */
        val Default = Blue

        /** A stored ARGB value; 0 (nothing stored yet) and unknown values fall back to [Default]. */
        fun ofArgb(argb: Int): FlAccent = entries.firstOrNull { it.argb == argb } ?: Default
    }
}

/** The dark-mode choices of the settings page, in the order they are shown and stored. */
enum class FlThemeMode(val stored: Int) {
    System(0),
    Light(1),
    Dark(2);

    companion object {
        fun ofStored(value: Int): FlThemeMode = entries.firstOrNull { it.stored == value } ?: System
    }
}

/**
 * The scheme this accent draws with, [dark] selecting the side.
 *
 * The default accent answers with [FlPalette]'s own tables, byte for byte: the app must look
 * exactly as it did before the setting existed until someone changes it.
 */
fun FlAccent.scheme(dark: Boolean): ColorScheme {
    val base = if (dark) FlPalette.dark else FlPalette.light
    if (this == FlAccent.Default) return base
    return base.accented(color, dark)
}

/**
 * [base] with its accent roles re-derived from [seed]; surfaces, outlines and the error role are
 * left alone, so a card still sits on the same paper whatever the accent is.
 *
 * The tones follow FlClash's own relationship to its seed rather than Material's strict HCT
 * ladder: a light-mode primary lands in the 34–52% lightness band (bright enough to read as the
 * accent, dark enough for white ink on it), its container is the same hue washed out to near
 * paper, and the dark side mirrors that with a raised primary and a sunken container.
 */
private fun ColorScheme.accented(seed: Color, dark: Boolean): ColorScheme {
    val hsl = seed.toHsl()
    // A near-grey seed has no hue to spread; floor the saturation so fills stay visible.
    val hue = hsl.hue
    val sat = hsl.saturation.coerceAtLeast(0.45f)
    return if (!dark) {
        val primary = hsl(hue, sat, hsl.lightness.coerceIn(0.34f, 0.52f))
        copy(
            primary = primary,
            onPrimary = inkOn(primary),
            primaryContainer = hsl(hue, sat * 0.55f, 0.88f),
            onPrimaryContainer = hsl(hue, sat, 0.22f),
            inversePrimary = hsl(hue, sat, 0.72f),
            secondary = hsl(hue, sat * 0.42f, 0.36f),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = hsl(hue, sat * 0.55f, 0.82f),
            onSecondaryContainer = hsl(hue, sat * 0.80f, 0.20f),
            surfaceTint = primary,
        )
    } else {
        val primary = hsl(hue, sat, hsl.lightness.coerceIn(0.60f, 0.78f))
        copy(
            primary = primary,
            onPrimary = inkOn(primary),
            primaryContainer = hsl(hue, sat * 0.75f, 0.30f),
            onPrimaryContainer = hsl(hue, sat * 0.60f, 0.90f),
            inversePrimary = hsl(hue, sat, 0.44f),
            secondary = hsl(hue, sat * 0.48f, 0.74f),
            onSecondary = hsl(hue, sat, 0.16f),
            secondaryContainer = hsl(hue, sat * 0.55f, 0.28f),
            onSecondaryContainer = hsl(hue, sat * 0.50f, 0.84f),
            surfaceTint = primary,
        )
    }
}

/** Which ink reads on [fill]: near-black on the bright accents (amber, lime), white elsewhere. */
private fun inkOn(fill: Color): Color =
    if (fill.relativeLuminance() > 0.45f) Color(0xFF16181D) else Color(0xFFFFFFFF)

/**
 * Perceived brightness of a color, 0–1.
 *
 * Compose 1.12 removed `Color.luminance()`, and this is the one thing the theme needed out of it:
 * which ink a fill can carry.
 */
fun Color.relativeLuminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/** A color as hue (0–360°), saturation and lightness (both 0–1). */
private data class Hsl(val hue: Float, val saturation: Float, val lightness: Float)

private fun Color.toHsl(): Hsl {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val lightness = (max + min) / 2f
    val delta = max - min
    if (delta <= 0f) return Hsl(0f, 0f, lightness)
    val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
    val hue = when (max) {
        red -> (green - blue) / delta + if (green < blue) 6f else 0f
        green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    } * 60f
    return Hsl(hue, saturation, lightness)
}

private fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val chroma = (1f - abs(2f * l - 1f)) * s
    val second = chroma * (1f - abs((h / 60f) % 2f - 1f))
    val (r, g, b) = when {
        h < 60f -> Triple(chroma, second, 0f)
        h < 120f -> Triple(second, chroma, 0f)
        h < 180f -> Triple(0f, chroma, second)
        h < 240f -> Triple(0f, second, chroma)
        h < 300f -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }
    val m = l - chroma / 2f
    return Color(r + m, g + m, b + m)
}