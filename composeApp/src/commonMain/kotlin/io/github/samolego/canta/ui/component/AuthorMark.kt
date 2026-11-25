package io.github.samolego.canta.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「这个人还没改名字」的那一套显示：名字打成马赛克、头像也打码，旁边一个一直呼吸的红点。
 *
 * 为什么要区分：这个社区没有账号，签名默认就是装机码（`#10000`、`A1B2C-3D4E5`）。那串东西
 * 当头像还行，当名字看是一堆噪音——列表上一屏全是 `#1000x`，谁也认不出谁。打码说的正是这件事：
 * 「这里本该有个名字，但他还没起」。红点负责催：它只在没名字的时候出现。
 *
 * 判据是名字的**形状**而不是本地开关：别人的名字是上传时存下来的字符串，没改过名的就是那串码，
 * 改成真名之后形状自然变了。所以同一个判据对「我」和「别人」都成立，也不需要后端加字段。
 */

/** 装机码的形状一：`#10000`。 */
private val DefaultCode = Regex("^#\\d{1,9}$")

/** 装机码的形状二：token 头 `A1B2C-3D4E5`（还没拿到号码时的签名）。 */
private val DefaultToken = Regex("^[0-9A-Z]{5}-[0-9A-Z]{5}$")

/**
 * [name] 是不是"还没改名字"。
 *
 * 空串也算：没名字就是没名字。判据是形状，不是"有没有设置过"——别人的名字只有字符串可看。
 */
fun isDefaultAuthorName(name: String): Boolean {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return true
    return DefaultCode.matches(trimmed) || DefaultToken.matches(trimmed)
}

/**
 * 把名字打成马赛克：一个字符换一个 `▓`，长度取 3..8 块。
 *
 * 保留长度而不固定成五个块：`#10000` 与 `A1B2C-3D4E5` 在版面上本来就宽窄不同，打出来也该不同，
 * 但太长的名字不该把一行撑满。
 */
fun mosaicOf(name: String): String {
    val visible = name.count { !it.isWhitespace() }.coerceIn(3, 8)
    return "▓".repeat(visible)
}

/**
 * 呼吸用的透明度：0.35 ↔ 1，900 毫秒一次来回。
 *
 * 这里的 `rememberInfiniteTransition` 是**无条件**调用的：只在外面决定用不用它的值，不把它放进
 * `if`。放进 `if` 会让组合槽位数随着"有没有名字"变化，切一次名字就乱一次。
 */
@Composable
fun rememberAnonymousPulse(): Float {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    return alpha
}

/**
 * 没名字的人的头像：中性灰圆 + 一块马赛克。
 *
 * 抽成一个组件是因为三处都要它（帖子卡片、楼层、评论）：没名字的状态必须一眼就是同一种样子，
 * 各画各的就会有人画成灰色首字、有人画成问号，看着像三种不同的 bug。
 */
@Composable
fun MosaicAvatar(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            // 中性灰而不是色板里那几色：没名字的人连"属于他的颜色"都还没有。
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "▓",
            // 字号跟着圆走，和 [LetterAvatar] 里 `labelLarge` 的比例接近。
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}

/** 一个在闪的小红点：「去把名字改了」。 */
@Composable
fun AnonymousDot(size: Dp = 6.dp, modifier: Modifier = Modifier) {
    val pulse = rememberAnonymousPulse()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error.copy(alpha = pulse)),
    )
}

/**
 * 作者名：改过名的人照常显示，没改过的人显示成 `▓▓▓` + 红点。
 *
 * 打码的那一支也要能看出"这里是名字"，所以用正文色的一半透明度而不是彻底不画：一屏里既看得出
 * 谁是谁（位置固定），也看得出谁还没名字（马赛克 + 红点）。
 */
@Composable
fun AuthorLabel(
    name: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = 1,
) {
    if (!isDefaultAuthorName(name)) {
        Text(
            text = name,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }
    val pulse = rememberAnonymousPulse()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = mosaicOf(name),
            style = style,
            // 半透明让"这块不是真名字"一眼看得出来；呼吸则是催人去改的那点小动作。
            color = color.copy(alpha = 0.45f + 0.35f * pulse),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(4.dp))
        AnonymousDot()
    }
}
