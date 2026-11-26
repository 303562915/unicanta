package io.github.samolego.canta.ui.component

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 元信息那几行小字的颜色：`onSurfaceVariant` 按 alpha **预乘**。
 *
 * 为什么不用 `Color.copy(alpha = …)` 或外面包一层 `Modifier.alpha`：
 *
 *  1. `Modifier.alpha` 会把整棵子树单独合成一层，叠在卡片上时整段文字发虚；
 *  2. `copy(alpha)` 之后如果没人把底色垫在下面，字会透出页面的亮色。
 *
 * fluxdo 的 `topic_card.dart` 也是这么做的——「已读退灰走色值预乘，不再外包 Opacity 层」
 * （`topic_card.dart:525`）。
 *
 * @param alpha 未读 **0.8**、全部读完 **0.6**（`topic_card.dart:292-293`）；
 *   标题「全读完」那一档是 **0.75**（`topic_card.dart:288-289`）。
 */
internal fun metaTint(base: Color, alpha: Float): Color =
    if (base.alpha <= 0f) base else base.copy(alpha = base.alpha * alpha)

/**
 * 按名字哈希从一组色里挑一个：后端没给分类色时的兜底。
 *
 * **不是**正路：fluxdo 那边的分类色是每个分类自己的一格（`ColorUtils.parseHex`），
 * 哈希只是"认不出颜色时也别画成灰的"的退路。
 */
internal fun fallbackBrandColor(seed: String, palette: List<Color>): Color {
    if (palette.isEmpty()) return Color.Gray
    // 先取模再取绝对值：`hashCode()` 可能是 `Int.MIN_VALUE`，对它取 abs 仍是负数，下标会越界。
    val index = abs(seed.hashCode() % palette.size)
    return palette[index]
}

/**
 * 某个"数据色"（分类色、标签色这类从后端或名字推出来的品牌色）在当前主题下的显示色。
 *
 * 逐条照 fluxdo `lib/utils/color_utils.dart` 的 `readableOn`：品牌色是为**亮色网页**设计的，
 * 直接搬到深色主题上会糊在背景里，搬到亮色主题上又会打眼，所以只调**明度**，色相与饱和度
 * 一格不动——"同一个分类在两种主题下还是那个颜色"，只是深浅合适了。
 *
 *   - 深色主题：明度 `< 0.6` 的提亮成 `0.6 + l × 0.2`（越暗提得越多），其余不动
 *   - 亮色主题：明度 `> 0.55` 的压到 `0.42`，其余不动
 *
 * @param base 原始色（`#RRGGBB` 解出来的那个）
 * @param dark 当前是不是深色主题（`MaterialTheme.colorScheme.isDark`）
 */
internal fun readableBrandColor(base: Color, dark: Boolean): Color {
    if (base.alpha <= 0f) return base
    val h = base.hue() // 0..360
    val s = base.saturationHsl() // 0..1
    val l = base.lightnessHsl() // 0..1
    val target = if (dark) {
        if (l < 0.6f) 0.6f + l * 0.2f else l
    } else {
        if (l > 0.55f) 0.42f else l
    }
    if (target == l) return base
    return hslToColor(h, s, target.coerceIn(0f, 1f), base.alpha)
}

/**
 * 把 `#RRGGBB` / `RRGGBB` 解成一个颜色；形状不对返回 null。
 *
 * 只认 6 位：Discourse 下发的分类色就是 6 位十六进制，3 位缩写与 8 位带透明度的写法
 * 在那边不存在；猜着解会解出一个错颜色，比解不出来更糟（后者有兜底色，前者看不出错）。
 */
internal fun parseHexColor(hex: String?): Color? {
    if (hex == null) return null
    val clean = hex.removePrefix("#").trim()
    if (clean.length != 6) return null
    var value = 0L
    for (ch in clean) {
        val digit = when (ch) {
            in '0'..'9' -> ch - '0'
            in 'a'..'f' -> ch - 'a' + 10
            in 'A'..'F' -> ch - 'A' + 10
            else -> return null
        }
        value = value * 16 + digit
    }
    return Color(0xFF000000L or value)
}

/** 色相（0..360）。灰度返回 0。 */
private fun Color.hue(): Float {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    if (d == 0f) return 0f
    val h = when (max) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    }
    val degrees = h * 60f
    return if (degrees < 0f) degrees + 360f else degrees
}

/** HSL 里的饱和度（0..1）。 */
private fun Color.saturationHsl(): Float {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val l = (max + min) / 2f
    val d = max - min
    if (d == 0f) return 0f
    return if (l > 0.5f) d / (2f - max - min) else d / (max + min)
}

/** HSL 里的明度（0..1）。 */
private fun Color.lightnessHsl(): Float = (maxOf(red, green, blue) + minOf(red, green, blue)) / 2f

/** HSL（h 用 0..360，s/l 用 0..1）→ 颜色。 */
private fun hslToColor(h: Float, s: Float, l: Float, a: Float): Color {
    if (s <= 0f) return Color(l, l, l, a)
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = h / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    fun clamp(v: Float) = v.coerceIn(0f, 1f)
    return Color(clamp(r1 + m), clamp(g1 + m), clamp(b1 + m), a)
}