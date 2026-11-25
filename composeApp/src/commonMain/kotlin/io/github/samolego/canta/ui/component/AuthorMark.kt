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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 「这个人还没改名字」的那一套显示：名字与头像都换成**骨架占位**（柔和灰块），旁边一个呼吸的红点。
 *
 * 为什么不是显示默认值：这个社区没有账号，签名默认就是装机码（`#10000`、`A1B2C-3D4E5`）。
 * 一屏列表里全是 `#1000x` 等于没有信息量，还会把真名淹掉。所以那串东西**不显示**，用一块
 * 占位形状代替 —— 跟列表加载时画的骨架是同一套视觉语言：这里本该有内容，但还没来。
 *
 * 为什么不是马赛克字符：`▓▓▓` 还是一串字，读起来像乱码；灰块没有字形，才真的"看不出是什么"。
 * 色块用极轻的渐变而不是纯色，是为了看起来像一块被磨砂盖住的地方，而不是一个灰洞。
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
 * 只认装机码的形状。空串**不算**：调用方拿不到名字时大多会先兜一句"匿名"或"未知"，
 * 那种位置画个骨架反而把"不知道是谁"说成"还没起名"——两件事。
 */
fun isDefaultAuthorName(name: String): Boolean {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return false
    return DefaultCode.matches(trimmed) || DefaultToken.matches(trimmed)
}

/**
 * 名字占位条的宽度：按字数估，3..8 个字宽。
 *
 * 保留差异（`#10000` 与 `A1B2C-3D4E5` 在版面上本来宽窄不同）但封顶：一条太长的灰条会把
 * 一行的时间、楼层号都挤走。
 */
fun anonymousPillWidth(name: String): Dp {
    val chars = name.count { !it.isWhitespace() }.coerceIn(3, 8)
    return (chars * 9).dp
}

/**
 * 呼吸用的透明度：0.35 ↔ 1，900 毫秒一次来回。
 *
 * 这里的 `rememberInfiniteTransition` 是**无条件**调用的：只在外面决定用不用它的值，不把它放进
 * `if`。放进 `if` 会让组合槽位数随着"有没有名字"变化，改一次名字就乱一次。
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
 * 没名字的人的头像：一块骨架灰圆，中间一道更浅的内圈。
 *
 * 抽成一个组件是因为三处都要它（帖子卡片、楼层、评论）：这种"还没来"的状态必须一眼是同一种样子，
 * 各画各的就会有人画成灰色首字、有人画成问号，看着像三种不同的毛病。
 */
@Composable
fun SkeletonAvatar(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val scheme = MaterialTheme.colorScheme
    val pulse = rememberAnonymousPulse()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            // 斜向渐变而不是纯色：读起来像被磨砂盖住的一块，而不是"图挂了"的黑洞。
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.surfaceVariant.copy(alpha = 0.55f + 0.35f * pulse),
                        scheme.surfaceVariant.copy(alpha = 0.35f + 0.45f * pulse),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.52f)
                .clip(CircleShape)
                .background(scheme.surface.copy(alpha = 0.30f * pulse)),
        )
    }
}

/** 一条骨架占位：圆角灰块，宽度可变。名字那一格用它代替文字。 */
@Composable
private fun SkeletonPill(width: Dp, height: Dp = 10.dp) {
    val scheme = MaterialTheme.colorScheme
    val pulse = rememberAnonymousPulse()
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        scheme.surfaceVariant.copy(alpha = 0.45f + 0.25f * pulse),
                        scheme.surfaceVariant.copy(alpha = 0.70f + 0.30f * pulse),
                        scheme.surfaceVariant.copy(alpha = 0.45f + 0.25f * pulse),
                    ),
                ),
            ),
    )
}

/**
 * 作者名：改过名的人照常显示，没改过的人显示成骨架占位条 + 红点。
 *
 * 占位条保留名字的大致宽度：一屏里位置固定，读的人知道"这儿有个名字"，只是还没起。
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
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SkeletonPill(width = anonymousPillWidth(name))
        Spacer(modifier = Modifier.width(4.dp))
        AnonymousDot()
    }
}
