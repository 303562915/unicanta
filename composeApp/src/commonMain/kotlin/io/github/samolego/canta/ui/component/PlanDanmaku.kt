package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/** 「颜色跟随主题」的那个值；见 [DanmakuStyle.colorArgb]。 */
const val DANMAKU_COLOR_FOLLOW_THEME = 0

/**
 * 弹幕长什么样、飘多快。
 *
 * 三样都是读者自己在面板里调的（长按顶栏那个图标），所以它只是个纯数据袋：页面持有当前这一份，
 * 改动后马上传给 [PlanDanmaku]，下一帧的绘制就是新的样子，不需要重启那一轮滚动。
 */
@Immutable
data class DanmakuStyle(
    /** ARGB；[DANMAKU_COLOR_FOLLOW_THEME] 表示用主题的 `onPrimaryContainer`。 */
    val colorArgb: Int = DANMAKU_COLOR_FOLLOW_THEME,
    val fontSizeSp: Float = 12f,
    /** 每秒走过多少 dp。小一点慢、看得清，大一点快、热闹。 */
    val speedDpPerSec: Float = 45f,
)

/** 没调过的样子：跟随主题、12 号字、每秒 45dp。 */
val DefaultDanmakuStyle = DanmakuStyle()

/** 字号可选档位（sp）。 */
val DanmakuSizeChoices: List<Pair<String, Float>> = listOf(
    "小" to 10f,
    "中" to 12f,
    "大" to 14f,
    "特大" to 17f,
)

/** 速度可选档位（dp/秒）。 */
val DanmakuSpeedChoices: List<Pair<String, Float>> = listOf(
    "慢" to 30f,
    "中" to 45f,
    "快" to 65f,
)

/** 颜色可选档位（ARGB）；第一项是"默认"，跟主题走。 */
val DanmakuColorChoices: List<Pair<String, Int>> = listOf(
    "默认" to DANMAKU_COLOR_FOLLOW_THEME,
    "白" to 0xFFFFFFFF.toInt(),
    "黄" to 0xFFFFD54F.toInt(),
    "青" to 0xFF4DD0E1.toInt(),
    "粉" to 0xFFFF8A80.toInt(),
    "绿" to 0xFFA5D6A7.toInt(),
)

/**
 * 弹幕样式解析成真正要画的那个颜色。[DanmakuStyle.colorArgb] 是"跟随主题"时从主题取——主题色
 * 要在这里读，[DanmakuStyle] 自己读不到 `MaterialTheme`。
 */
@Composable
fun DanmakuStyle.resolvedColor(): Color =
    if (colorArgb == DANMAKU_COLOR_FOLLOW_THEME) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        Color(colorArgb)
    }

/**
 * 这层有多高就铺多少条轨道，封顶这么多。
 *
 * 封顶是因为再多也挤：一张卡片铺十几行已经读不过来，而且轨道越多、每条的重现周期越长。
 */
private const val DANMAKU_MAX_LANES = 10

/** 没量到高度时（约束无界）用的轨道数，握着一种能看的样子，不至于一条都不画。 */
private const val DANMAKU_FALLBACK_LANES = 3

/** 轨道高度 = 字号 × 这个数：字越大行距越大，不然大字号会上下贴在一起。 */
private const val DANMAKU_LANE_HEIGHT_FACTOR = 2.1f

/**
 * 同时在场的最多条数。
 *
 * 一级评论全排在前、它们的回复跟在后面（见 `danmakuRows`），池子太小的话回复一条都轮不上，
 * 看起来就像「只有一级在飘」。
 */
private const val DANMAKU_MAX_LINES = 24

/** 一条弹幕最多几个字：三十多个字在手机上已经横跨一屏，再多就只剩尾巴。 */
private const val DANMAKU_MAX_CHARS = 34

/** 同一条轨道上，前一条完全离开左边之后，空这么久再放下一条进场。 */
private const val DANMAKU_GAP_MS = 900L

/** 一条弹幕里，开头这一段（占全程的比例）从全透明升到不透明。 */
private const val DANMAKU_FADE_IN = 0.16f

/** 结尾这一段从可见淡出：走到左边时是渐渐没有，不是被一刀切掉。 */
private const val DANMAKU_FADE_OUT = 0.10f

/** 再短的一段路也留这么久，否则短句会像闪一下就不见了。 */
private const val DANMAKU_MIN_LIFETIME_MS = 4200L

/**
 * 评论弹幕：把方案详情下面那些评论，一句一句地从右往左飘过整张卡片。
 *
 * **不占位置**：调用方把它盖在卡片上（用 `matchParentSize` 这类覆盖方式放上去），卡片该多高还是
 * 多高，不会被撑出一条弹幕带。
 *
 * **不堆在一起**：轨道数按这层实际有多高算（[DANMAKU_MAX_LANES] 封顶），一条弹幕占一条轨道；
 * 同一条轨道上按「上一条完全离开左边，才放下一条进场」排队，所以轨道上的两条永远不会同时出现在
 * 画面里。这比按固定时间错开相位靠谱：按相位错开时两条的间距是「相位差 × 速度」，短句之间看着
 * 还行，长句一多就并排糊在一起。
 *
 * 其余两件让它像弹幕而不是三行飘着的字：
 * 1. **从右往左**——每条都从这一层右边缘外起步，走到文字整个离开左边缘才算完，然后到下一轮；
 * 2. **慢慢淡过来**——开头一小段 alpha 从 0 升到满，结尾再淡出，进场和离场都不是硬切。
 *
 * 动画只写一个每帧递增的时间轴，位移和透明度在 [graphicsLayer] 的绘制块里算：这两样都只影响
 * 绘制，于是每帧只重绘这一层，不重组、不重新排版。关掉弹幕时调用方不组合这个函数，帧循环跟着
 * [LaunchedEffect] 一起取消，不留空转的时钟。
 */
@Composable
fun PlanDanmaku(
    lines: List<String>,
    style: DanmakuStyle = DefaultDanmakuStyle,
    modifier: Modifier = Modifier,
    /** 指定轨道数；0 表示按这一层的高度自己算（推荐）。 */
    laneCount: Int = 0,
) {
    val shown = remember(lines) { danmakuLines(lines) }
    if (shown.isEmpty()) return

    val tint = style.resolvedColor()
    val fontSize = style.fontSizeSp.sp
    val laneHeight = (style.fontSizeSp * DANMAKU_LANE_HEIGHT_FACTOR).dp
    val textStyle = remember(tint, fontSize) {
        TextStyle(
            fontSize = fontSize,
            lineHeight = fontSize * 1.25f,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            // 弹幕压在卡片正文上，正文要求的对比度是给"读一段话"算的，不是给飘过去的字算的：
            // 一层深色晕边让它走到哪一段都还读得出来。
            shadow = Shadow(color = Color.Black.copy(alpha = 0.35f), blurRadius = 4f),
        )
    }
    // 每条自己的宽度，量一次就够：向左的行程要按它算，量短了文字会在左边被截一下才消失。
    val measurer = rememberTextMeasurer()
    val widths = remember(shown, textStyle) {
        shown.map { line ->
            measurer.measure(
                text = AnnotatedString(line),
                style = textStyle,
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
            // 这一层自己的边界：从右边进场的那一截在外面，这里裁掉，看着才是从边缘冒出来。
            .clipToBounds(),
    ) {
        val span = constraints.maxWidth.toFloat()
        if (span <= 0f) return@BoxWithConstraints
        val byHeight = if (maxHeight.value.isFinite()) (maxHeight / laneHeight).toInt() else 0
        val lanes = if (laneCount > 0) {
            laneCount
        } else {
            byHeight.coerceIn(1, DANMAKU_MAX_LANES).takeIf { byHeight > 0 } ?: DANMAKU_FALLBACK_LANES
        }
        val speedPx = with(LocalDensity.current) { style.speedDpPerSec.dp.toPx() }
        val slots = remember(shown, widths, span, lanes, speedPx) {
            scheduleDanmaku(widths, lanes, span, speedPx)
        }
        shown.forEachIndexed { index, line ->
            val slot = slots.getOrNull(index) ?: return@forEachIndexed
            Text(
                text = line,
                style = textStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // 轨道是固定的，只有横向在动：纵向用 offset（排版阶段定一次），横向留给
                    // graphicsLayer（每帧只重绘）。
                    .offset(y = laneHeight * slot.lane)
                    .graphicsLayer {
                        // 时钟从 0 起算，每条在自己轨道里的出发时刻是排好的：还没轮到的那几条
                        // alpha 就是 0，不会在右边排队等着被看见。
                        val elapsed = (clock - slot.startMs).mod(slot.cycleMs)
                        if (elapsed >= slot.lifetimeMs) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        val progress = elapsed.toFloat() / slot.lifetimeMs.toFloat()
                        translationX = span - progress * slot.travelPx
                        val fadeIn = progress / DANMAKU_FADE_IN
                        val fadeOut = (1f - progress) / DANMAKU_FADE_OUT
                        alpha = min(1f, min(fadeIn, fadeOut)).coerceIn(0f, 1f)
                    },
            )
        }
    }
}

/**
 * 一条弹幕的排期：哪条轨道、走完要多久、什么时候出发、它那条轨道多久循环一次。
 *
 * 出发时刻和循环周期一起排的道理：同一条轨道要保证「上一条完全离开左边，下一条才从右边进场」，
 * 所以下一条的出发时刻只能是「上一条的出发时刻 + 上一条走完全程的时间」；一条轨道上所有条加起来
 * 就是这条轨道的循环周期。
 */
private data class DanmakuSlot(
    val lane: Int,
    val travelPx: Float,
    val lifetimeMs: Long,
    val startMs: Long,
    val cycleMs: Long,
)

/**
 * 把每条弹幕按轨道轮着分下去，再在每条轨道里按顺序排出发时刻（见 [DanmakuSlot]）。
 *
 * 轮着分（`index % lanes`）而不是按顺序铺满第一条轨道：这样第一屏就有好几条在不同高度上同时
 * 飘，而不是从最上面那条开始一条一条往下排。
 */
private fun scheduleDanmaku(
    widths: List<Float>,
    lanes: Int,
    spanPx: Float,
    speedPxPerSec: Float,
): List<DanmakuSlot> {
    if (lanes <= 0 || widths.isEmpty() || speedPxPerSec <= 0f) return emptyList()
    // 每条轨道排到哪里了：下一条同轨道的弹幕从这里出发。
    val cursor = LongArray(lanes)
    val drafted = widths.mapIndexed { index, width ->
        val lane = index % lanes
        val travel = spanPx + width
        val lifetime = ((travel / speedPxPerSec) * 1000f).toLong()
            .coerceAtLeast(DANMAKU_MIN_LIFETIME_MS)
        val start = cursor[lane]
        cursor[lane] = start + lifetime + DANMAKU_GAP_MS
        DanmakuSlot(
            lane = lane,
            travelPx = travel,
            lifetimeMs = lifetime,
            startMs = start,
            cycleMs = 0L,
        )
    }
    // 一条轨道的循环周期 = 这条轨道上所有条走完（含间隔）的总和：cursor 在这一轮里被推到了那儿。
    return drafted.map { slot -> slot.copy(cycleMs = cursor[slot.lane]) }
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
