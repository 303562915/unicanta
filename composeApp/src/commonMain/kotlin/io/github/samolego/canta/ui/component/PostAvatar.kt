package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * 帖子头像的 8 个底色（固定色板）：都压到中低明度，白字压上去在浅色和深色主题下都读得出来。
 *
 * 为什么不跟主题的 primary/secondary 走：同一个人在很多地方出现，颜色必须认人，不能随主题变；
 * 而且主题色只有两三个，满屏回帖会撞成一片。
 */
private val AVATAR_COLORS = listOf(
    Color(0xFF2F6B8A),
    Color(0xFF2E6E5C),
    Color(0xFF3B5A8C),
    Color(0xFF8A5A2B),
    Color(0xFF8C4A5B),
    Color(0xFF5C6B2A),
    Color(0xFF4A5A6E),
    Color(0xFF7A4A8A),
)

/**
 * 由种子字符串稳定推出一个头像底色。同一作者任何地方都是同一个颜色。
 *
 * 先取模再取绝对值：`hashCode` 可能是 `Int.MIN_VALUE`，对负数取 `abs` 仍是负数，
 * 直接拿来当下标会越界。
 */
fun avatarColorFor(seed: String): Color {
    val index = abs(seed.hashCode() % AVATAR_COLORS.size)
    return AVATAR_COLORS[index]
}

/**
 * 帖子里用的字母头像：圆形色块 + 首字。name 空时用 "?"。
 *
 * [seed] 和 [name] 分开：改名不该换颜色，颜色认的是作者身份号一类的稳定种子。
 */
@Composable
fun PostAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    seed: String = name,
) {
    Box(
        // 形状交给 background：圆之外没有别的东西要裁，省一层 clip。
        modifier = modifier
            .size(size)
            .background(avatarColorFor(seed), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatarGlyph(name),
            // 字号跟着圆走，同一个组件在列表里 32、在详情页 48 都是同一个比例。
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/**
 * 取头像上那一个字。
 *
 * 拉丁字母大写化（`a` → `A`）更像名字本身；CJK 和其它文字没有大小写，硬转只会变形，
 * 所以原样取一个字符。emoji 是两个 UTF-16 单元，只取前半截会画成方框，这里整对拿下。
 */
private fun avatarGlyph(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val first = trimmed[0]
    if (first.isHighSurrogate() && trimmed.length > 1) return trimmed.substring(0, 2)
    val code = first.code
    val cjk = code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF
    if (cjk) return first.toString()
    // 拉丁字母含带音标的扩展段（é、ø 这类），大写化后仍是同一个字母。
    val latin = code in 0x41..0x5A || code in 0x61..0x7A || code in 0xC0..0x24F
    return if (latin) first.uppercaseChar().toString() else first.toString()
}
