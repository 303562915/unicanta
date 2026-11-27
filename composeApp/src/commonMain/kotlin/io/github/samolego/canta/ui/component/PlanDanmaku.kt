package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/** 几条轨道同时飘；三条够热闹，又不至于把卡片顶部盖成一片糊。 */
private const val DANMAKU_LANES = 3

/** 每条轨道的高度：11 号字加一点呼吸，三条正好一条窄带。 */
private const val DANMAKU_LANE_HEIGHT_DP = 20

/**
 * 同时在场的最多条数：评论可能上百，全飘出来只显得吵。
 *
 * 给到二十几条是因为弹幕池里有一级评论也有它们的回复（见 `danmakuRows`）：一级全排在前、回复跟在
 * 后面，池子太小的话回复会一条都轮不上，看起来就像「只有一级在飘」。
 */
private const val DANMAKU_MAX_LINES = 24

/** 一条弹幕最多几个字：三十多个字在手机上已经横跨一屏，再多就只剩尾巴。 */
private const val DANMAKU_MAX_CHARS = 34

/** 每秒走过的距离（dp）。慢一点才读得清，也才配得上「慢慢淡过来」。 */
private const val DANMAKU_SPEED_DP_PER_SEC = 40f

/** 两条同轨道的弹幕错开的毫秒数；比最慢的一条全程短，所以轨道上不会空出空档。 */
private const val DANMAKU_PHASE_MS = 1400L

/** 一条弹幕里，开头这一段（占全程的比例）从全透明升到不透明。 */
private const val DANMAKU_FADE_IN = 0.18f

/** 结尾这一段从可见淡出：走到左边时是渐渐没有，不是被一刀切掉。 */
private const val DANMAKU_FADE_OUT = 0.10f

/** 再短的一段路也留这么久，否则短句会像闪一下就不见了。 */
private const val DANMAKU_MIN_LIFETIME_MS = 4200L

/**
 * 评论弹幕：把方案详情下面那些评论，一句一句地从右往左飘过卡片顶部。
 *
 * 为什么不叠在正文上、也不占满整张卡片：这是「有人说过话」的动静，不是要盖掉方案本身的名字和
 * 作者。所以它是一条固定高的窄带，卡片变高一点，正文一个字都不挡。
 *
 * 三件让它看起来像弹幕、而不是三行斜着放的字：
 * 1. **从右往左**——每条都从容器右边缘外起步，走到文字整个离开左边缘，再从头开始，所以永远有字
 *    在路上，不会一起空掉；
 * 2. **慢慢淡过来**——开头五分之一的路程里 alpha 从 0 升到满，结尾一小段再淡出，进场和离场都
 *    不是硬切；
 * 3. **错开相位**——同一条轨道上下标相邻的两条隔一段时间进场，不会并排贴在一起走。
 *
 * 动画只写一个每帧递增的时间轴，位移和透明度在 [graphicsLayer] 的绘制块里算：这两样都只影响
 * 绘制，于是每帧只重绘这一层，不重组、不重新排版。关掉弹幕时调用方不组合这个函数，帧循环跟着
 * [LaunchedEffect] 一起取消，不留空转的时钟。
 */
@Composable
fun PlanDanmaku(
    lines: List<String>,
    modifier: Modifier = Modifier,
    laneCount: Int = DANMAKU_LANES,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val shown = remember(lines) { danmakuLines(lines) }
    if (shown.isEmpty()) return

    val style = remember(tint) {
        TextStyle(
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            // 弹幕压在蓝色卡片上，卡片要求的对比度是给正文算的，不是给飘过去的字算的：一层
            // 深色晕边让它走到哪一段都还读得出来。
            shadow = Shadow(color = Color.Black.copy(alpha = 0.32f), blurRadius = 4f),
        )
    }
    // 每条自己的宽度，量一次就够：向左的行程要按它算，量短了文字会在左边被截一下才消失。
    val measurer = rememberTextMeasurer()
    val widths = remember(shown, style) {
        shown.map { line ->
            measurer.measure(
                text = AnnotatedString(line),
                style = style,
                overflow = TextOverflow.Clip,
                softWrap = false,
                maxLines = 1,
            ).size.width.toFloat()
        }
    }

    var clock by remember(shown) { mutableLongStateOf(0L) }
    LaunchedEffect(shown) {
        val started = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> clock = (now - started) / 1_000_000L }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height((DANMAKU_LANE_HEIGHT_DP * laneCount).dp)
            // 带子自己的边界：从右边进场的那一截在外面，这里裁掉，看起来才是从卡片边缘冒出来。
            .clipToBounds(),
    ) {
        val span = constraints.maxWidth.toFloat()
        if (span <= 0f) return@BoxWithConstraints
        shown.forEachIndexed { index, line ->
            val travel = span + widths[index]
            Text(
                text = line,
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // 轨道是固定的，只有横向在动：纵向用 offset（排版阶段定一次），横向留给
                    // graphicsLayer（每帧只重绘）。
                    .offset(y = (DANMAKU_LANE_HEIGHT_DP * (index % laneCount)).dp)
                    .graphicsLayer {
                        if (travel <= 0f) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        // 全程时长按「行程 / 速度」算，速度是 dp 每秒，所以先把像素换回 dp。
                        val travelDp = travel.toDp().value
                        val lifetime = ((travelDp / DANMAKU_SPEED_DP_PER_SEC) * 1000f)
                            .toLong()
                            .coerceAtLeast(DANMAKU_MIN_LIFETIME_MS)
                        val phase = (index * DANMAKU_PHASE_MS) % lifetime
                        val elapsed = (clock + phase) % lifetime
                        val progress = elapsed.toFloat() / lifetime.toFloat()
                        translationX = span - progress * travel
                        val fadeIn = progress / DANMAKU_FADE_IN
                        val fadeOut = (1f - progress) / DANMAKU_FADE_OUT
                        alpha = min(1f, min(fadeIn, fadeOut)).coerceIn(0f, 1f)
                    },
            )
        }
    }
}

/** 评论正文整理成能飘的一行一句：去换行、去空、截断、去重，最多 [DANMAKU_MAX_LINES] 条。 */
private fun danmakuLines(raw: List<String>): List<String> =
    raw.asSequence()
        .map { it.replace('\n', ' ').replace('\r', ' ').trim() }
        .filter { it.isNotEmpty() }
        .map { if (it.length > DANMAKU_MAX_CHARS) it.take(DANMAKU_MAX_CHARS) + "…" else it }
        .distinct()
        .take(DANMAKU_MAX_LINES)
        .toList()
