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
 * 一条要飘过去的评论。
 *
 * [likes] 和 [top] 一起决定它是不是「被点赞最多的那几条」：那几条换一个亮色、字更粗、后面还挂上
 * 赞数，在满屏一样颜色的弹幕里一眼就能认出来。赞数只挂在它们身上——每条都挂一个数字，等于没挂。
 */
@Immutable
data class DanmakuLine(
    val text: String,
    val likes: Int = 0,
    /** 是不是点赞最多的那几条；由调用方按整批评论的赞数排出来。 */
    val top: Boolean = false,
)

/**
 * 弹幕长什么样、飘多快。
 *
 * 三样都是读者自己在面板里调的（长按顶栏那个图标），所以它只是个纯数据袋：页面持有当前这一份，
 * 改动后马上传给 [PlanDanmaku]，下一帧的绘制就是新的样子。
 */
@Immutable
data class DanmakuStyle(
    /** ARGB；[DANMAKU_COLOR_FOLLOW_THEME] 表示用主题的 `onPrimaryContainer`。 */
    val colorArgb: Int = DANMAKU_COLOR_FOLLOW_THEME,
    val fontSizeSp: Float = 12f,
    /**
     * 一条弹幕从右边走到左边要几秒。
     *
     * 用「时间」而不是「每秒多少像素」当速度：同一条轨道被占用的时长于是是固定的，密度就可算
     * ——轨道数决定屏幕上同时有几条，与句子长短无关。按像素算速度的话，长句占轨道占得久，同一条
     * 轨道排队的后面那几条就全被推后，「这会儿好挤、一会儿全空」的忽快忽慢就是从这来的。
     */
    val passSeconds: Float = 9f,
)

/** 没调过的样子：跟随主题、12 号字、一条走九秒。 */
val DefaultDanmakuStyle = DanmakuStyle()

/** 点赞最多的那几条的颜色：琥珀金，在蓝色卡片上最跳，和默认的白/浅字不会混。 */
val DanmakuTopColor = Color(0xFFFFB300)

/** 字号可选档位（sp）。 */
val DanmakuSizeChoices: List<Pair<String, Float>> = listOf(
    "小" to 10f,
    "中" to 12f,
    "大" to 14f,
    "特大" to 17f,
)

/** 速度可选档位：一条弹幕走完全程要几秒（越小越快）。 */
val DanmakuSpeedChoices: List<Pair<String, Float>> = listOf(
    "快" to 6.5f,
    "中" to 9f,
    "慢" to 12f,
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
 * 封顶是因为再多也挤：一张卡片铺八九行已经读不过来，而且每条轨道自己排队，轨道越多、每条的重现
 * 周期越长——二十几条弹幕分到十几条轨道上，一轮下来接近半分钟，看起来就像"没有弹幕"。
 */
private const val DANMAKU_MAX_LANES = 8

/** 没量到高度时（约束无界）用的轨道数，握着一种能看的样子，不至于一条都不画。 */
private const val DANMAKU_FALLBACK_LANES = 3

/** 轨道高度 = 字号 × 这个数：字越大行距越大，不然大字号会上下贴在一起。 */
private const val DANMAKU_LANE_HEIGHT_FACTOR = 2.1f

/** 表情图片按这个字号当 1 倍画（[rememberEmotionDocument] 的 scale）：调大字号时表情跟着一起大。 */
private const val DANMAKU_EMOTION_BASE_SP = 12f

/**
 * 弹幕池的上限条数。
 *
 * 实际放几条还要乘轨道数看（[DANMAKU_LINES_PER_LANE]）：八条轨道、一条走九秒、每条轨道之间歇
 * 一点四秒，一轮大约八条；池子给到十八，两轮多一点就能把一级评论和回复都过一遍。
 */
private const val DANMAKU_MAX_LINES = 18

/**
 * 每条轨道分到几条弹幕。
 *
 * 这条是"密度"的真正旋钮：池子大小不再写死，而是「轨道数 × 这个数」，于是不管卡片多高、屏幕
 * 多宽，一条轨道一轮的时长都一样（约三十秒），不会出现"高一点的卡片一轮要一分钟、矮一点的十五秒
 * 就转完"。
 */
private const val DANMAKU_LINES_PER_LANE = 3

/** 一条弹幕最多几个字：三十多个字在手机上已经横跨一屏，再多就只剩尾巴。 */
private const val DANMAKU_MAX_CHARS = 34

/** 同一条轨道上，前一条完全离开左边之后，空这么久再放下一条进场：留这一口气，弹幕才不像排队。 */
private const val DANMAKU_GAP_MS = 1400L

/** 一条弹幕里，开头这一段（占全程的比例）从全透明升到不透明。 */
private const val DANMAKU_FADE_IN = 0.16f

/** 结尾这一段从可见淡出：走到左边时是渐渐没有，不是被一刀切掉。 */
private const val DANMAKU_FADE_OUT = 0.10f

/**
 * 评论弹幕：把方案详情下面那些评论，一句一句地从右往左飘过整张卡片。
 *
 * **不占位置**：调用方把它盖在卡片上（用 `matchParentSize` 这类覆盖方式放上去），卡片该多高还是
 * 多高，不会被撑出一条弹幕带。
 *
 * **不堆在一起**：轨道数按这层实际有多高算（[DANMAKU_MAX_LANES] 封顶），一条弹幕占一条轨道；
 * 同一条轨道上按「上一条完全离开左边、再歇一口气，才放下一条进场」排队，所以轨道上的两条永远
 * 不会同时出现在画面里。
 *
 * **节奏可算**：每条走完全程的时间是固定的（[DanmakuStyle.passSeconds]），所以「同时在场几条」
 * 只由轨道数决定，句子长短不会让某条轨道忽挤忽空。
 *
 * 其余两件让它像弹幕而不是几行飘着的字：
 * 1. **从右往左**——每条都从这一层右边缘外起步，走到文字整个离开左边缘才算完，然后到下一轮；
 * 2. **慢慢淡过来**——开头一小段 alpha 从 0 升到满，结尾再淡出，进场和离场都不是硬切。
 *
 * 动画只写一个每帧递增的时间轴，位移和透明度在 [graphicsLayer] 的绘制块里算：这两样都只影响
 * 绘制，于是每帧只重绘这一层，不重组、不重新排版。关掉弹幕时调用方不组合这个函数，帧循环跟着
 * [LaunchedEffect] 一起取消，不留空转的时钟。
 */
@Composable
fun PlanDanmaku(
    lines: List<DanmakuLine>,
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
    // 点赞最多的那几条后面挂赞数：挂完的字才是要量的那个宽度，否则它们会提前消失。
    val labels = remember(shown) { shown.map { it.label } }
    // 量宽度用的是"表情换成一个字"的纯文字版：真正的段落里表情是 inline 图片，[Text] 之外的
    // 量器看不到它，会让带表情的那条行程短一截——走到左边还剩半个表情就从头开始了。
    val measured = remember(labels) { labels.map { measureTextOf(it) } }
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
    val widths = remember(measured, textStyle) {
        measured.map { text ->
            measurer.measure(
                text = AnnotatedString(text),
                style = textStyle,
                overflow = TextOverflow.Clip,
                softWrap = false,
                maxLines = 1,
            ).size.width.toFloat()
        }
    }
    val passMs = (style.passSeconds * 1000f).toLong().coerceAtLeast(3000L)

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
        // 池子按轨道数摊开（见 [DANMAKU_LINES_PER_LANE]）：不管这一层多高，一条轨道一轮都是
        // 三十秒左右，节奏稳定。
        val planned = min(shown.size, lanes * DANMAKU_LINES_PER_LANE)
        val slots = remember(widths, span, lanes, passMs, planned) {
            scheduleDanmaku(widths.take(planned), lanes, span, passMs)
        }
        shown.forEachIndexed { index, line ->
            if (index >= planned) return@forEachIndexed
            val slot = slots.getOrNull(index) ?: return@forEachIndexed
            // 正文里的 `[weixiao]` 这类 token 换回表情图片：和评论区用同一个解析器、同一份图片，
            // 弹幕里看到的和楼里看到的是同一个表情，而不是一串方括号拼音。
            val spans = remember(line.text) { parseEmotionBody(line.text) }
            val document = rememberEmotionDocument(
                spans = spans,
                // 表情图片的尺寸按字号比例放大，不然把弹幕调大之后，字变大了、表情还是原来那么小。
                scale = style.fontSizeSp / DANMAKU_EMOTION_BASE_SP,
            )
            Text(
                text = document.annotated,
                inlineContent = document.inline,
                style = textStyle,
                // 点赞最多的那几条：金色、更粗。没点过赞的（likes = 0）不会进这一支，因为
                // [DanmakuLine.top] 是调用方按整批评论排出来的，不是"有赞就算"。
                color = if (line.top) DanmakuTopColor else Color.Unspecified,
                fontWeight = if (line.top) FontWeight.ExtraBold else null,
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

/** 飘出去的那一行字：点赞最多的那条后面带上赞数，其余的只有正文。 */
private val DanmakuLine.label: String
    get() = if (top && likes > 0) "$text · $likes 赞" else text

/**
 * 用来量宽度的那一版文字：表情 token 换成一个全角圈。
 *
 * 为什么量这份而不是真段落：真段落里表情是 inline 图片，宽度由 [Text] 在排版时才知道，量器算不到
 * ——按 token 原文量（`[weixiao]` 九个字符）会宽出一大截，按零算又短一截，两头都会让那条弹幕在
 * 左边"还没走完就从头开始"。一个圈大约就是一个表情宽，够准。
 */
private fun measureTextOf(label: String): String =
    parseEmotionBody(label).joinToString("") { span ->
        when (span) {
            is EmotionSpan.Words -> span.text
            is EmotionSpan.Sticker -> "○"
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
    passMs: Long,
): List<DanmakuSlot> {
    if (lanes <= 0 || widths.isEmpty() || passMs <= 0L) return emptyList()
    val period = passMs + DANMAKU_GAP_MS
    // 每条轨道的第一条错开出发：所有轨道都从 0 开始的话，八条弹幕会一起从右边冲进来、一起走完
    // 消失，屏幕上就成了「一波一波」，而不是一直有字在飘。按轨道号摊开一个周期，任意时刻都有
    // 一半左右的轨道正在走。
    val offsets = LongArray(lanes) { lane -> lane * period / lanes }
    // 每条轨道排到哪里了：下一条同轨道的弹幕从这里出发。
    val cursor = offsets.copyOf()
    val drafted = widths.mapIndexed { index, width ->
        val lane = index % lanes
        val start = cursor[lane]
        cursor[lane] = start + period
        DanmakuSlot(
            lane = lane,
            travelPx = spanPx + width,
            lifetimeMs = passMs,
            startMs = start,
            cycleMs = 0L,
        )
    }
    // 一条轨道的循环周期 = 这条轨道上所有条走完（含间隔）的总和，不把开头那段错开的偏移算进去，
    // 否则每条轨道的第一条都会在下一次循环里越错越远。
    return drafted.map { slot ->
        slot.copy(cycleMs = cursor[slot.lane] - offsets[slot.lane])
    }
}

/** 评论正文整理成能飘的一行一句：去换行、去空、截断、去重，最多 [DANMAKU_MAX_LINES] 条。 */
private fun danmakuLines(raw: List<DanmakuLine>): List<DanmakuLine> {
    val seen = HashSet<String>()
    val out = ArrayList<DanmakuLine>(min(raw.size, DANMAKU_MAX_LINES))
    raw.forEach { line ->
        val text = line.text.replace('\n', ' ').replace('\r', ' ').trim()
        if (text.isEmpty()) return@forEach
        val clipped = clipDanmakuText(text)
        if (!seen.add(clipped)) return@forEach
        out += line.copy(text = clipped)
        if (out.size >= DANMAKU_MAX_LINES) return out
    }
    return out
}

/**
 * 截到 [DANMAKU_MAX_CHARS] 个字，**不在一段表情 token 中间下刀**。
 *
 * 直接 `take(n)` 会切出 `[weix`：解析器认不出它是表情，于是屏幕上原样留着半个方括号和几个拼音
 * ——比不显示表情更难看。所以数的时候把一整段 `[key]` 当一个字，截断点只落在表情外面。
 */
private fun clipDanmakuText(text: String): String {
    if (text.length <= DANMAKU_MAX_CHARS) return text
    val builder = StringBuilder(DANMAKU_MAX_CHARS + 1)
    var count = 0
    for (span in parseEmotionBody(text)) {
        when (span) {
            is EmotionSpan.Words -> {
                for (ch in span.text) {
                    if (count >= DANMAKU_MAX_CHARS) return builder.append('…').toString()
                    builder.append(ch)
                    count++
                }
            }

            is EmotionSpan.Sticker -> {
                if (count >= DANMAKU_MAX_CHARS) return builder.append('…').toString()
                builder.append('[').append(span.emotion.key).append(']')
                count++
            }
        }
    }
    return builder.toString()
}
