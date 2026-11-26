package io.github.samolego.canta.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 面板宽度：fluxdo `ControlledCategoryDrawerState._panelWidth` 的值。
 *
 * 304 是"侧栏"而不是"整屏"：留出的那一截让列表还看得见，人知道自己是把东西拉到一边，
 * 而不是跳到了一个新页面。跟手拖动的增量也按这个数归一化。
 */
private val DrawerPanelWidth = 304.dp

/** 遮罩最深处：`Colors.black.withValues(alpha: 0.54 * v)`，手拖到一半就只压一半。 */
private const val DrawerScrimAlpha = 0.54f

/**
 * 甩动阈值（px/s）：`settle()` 里那个 365。过了它就只看方向，不看位置——
 * 快速甩一下哪怕只拉出四分之一也照开，慢拖到一半才开。
 */
private const val DrawerFlingThreshold = 365f

/**
 * 收尾弹簧：fluxdo 的 `kHeaderSpringDescription`（dampingRatio **1.0** / stiffness **500**），
 * 也就是不弹、只滑，跟首页头部那套运动同族。Compose 的 spring 与 Flutter 的
 * `SpringDescription` 用同一套单位（k 是 1/s²），数值直接搬。
 */
private val DrawerSpring = spring<Float>(dampingRatio = 1f, stiffness = 500f)

/**
 * 帖子页左侧的分类侧栏，逐项照 fluxdo 的 `lib/widgets/topic/category_drawer.dart`：
 *
 * - 面板 **304dp**，右侧上下圆角 **24**，底色 `surface`；遮罩黑 **54%**，跟着进度渐变
 * - 头部 `16 / 10 / 8 / 6`：左「分类 ⇄ 标签」分段切换，右一枚关闭
 * - 行高 **48**、圆角 **14**、左右内边距 **10**；左边 **32dp 彩色图标块**（分类色 14% 底、
 *   圆角 9、里面一颗 9dp 实心色点），块与文字之间 **12**，名字 **14.5sp**
 * - 分区标题：**12sp / w600 / letterSpacing 0.3**，上下 `4 / 6`、左右 **8**
 * - 标签行与分类行同构：32dp 图标块里一个 `#`，名字下面一条 **3dp 热度条**（相对榜首归一化），
 *   右端是出现在这批帖子里的次数
 *
 * 手势：开着时在面板或遮罩上横向拖 = 跟手关（位移按 304 归一化）；松手按甩动速度/位置就近
 * 收尾；点遮罩关闭。**不做**"从左缘拖出打开"——那一条在 Android 上被系统返回手势占着，
 * 抢过来会让人想返回时把抽屉拉出来。
 *
 * 关闭动画走完会回调 [onDismiss]，把调用方的开合状态一起收掉；否则下一次拖到 0 之后
 * 状态还停在"开着"，再点 ☰ 不会有任何反应。
 *
 * @param open 调用方的开合状态（☰ 点一下置 true）
 * @param category 选中的分类；空串 = 全部分类
 * @param tag 选中的标签；空串 = 不按标签筛
 * @param tags 从已经拉到手的帖子汇总出来的标签与次数，降序
 */
@Composable
internal fun PostCategoryDrawer(
    open: Boolean,
    categories: List<String>,
    category: String,
    tags: List<PostTagCount>,
    tag: String,
    onDismiss: () -> Unit,
    onPickCategory: (String) -> Unit,
    onPickTag: (String) -> Unit,
    /**
     * 分类名 → 后端下发的颜色（`#RRGGBB`）。查不到就用按名字哈希的兜底色。
     *
     * 必须有这一项：卡片与详情页上的分类色块用的是下发的 hex，侧栏只按名字哈希的话，
     * 同一个分类在两处颜色不同，看起来像两个分类。
     */
    categoryColors: Map<String, String> = emptyMap(),
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val panelPx = with(LocalDensity.current) { DrawerPanelWidth.toPx() }
    // 页签状态提到早退之前：放在后面的话，每开一次抽屉都会被重新建立（回到分类页）。
    var showTags by remember { mutableStateOf(false) }

    // 开合：点击与拖动后的收尾都只改 value，实际位置由这个动画统一负责。
    LaunchedEffect(open) {
        val endpoint = if (open) 1f else 0f
        progress.animateTo(endpoint, DrawerSpring)
        // 弹簧"走完"不等于落到端点：Float 的默认 visibilityThreshold 是 0.01（304dp 的 3dp），
        // 差一点就宣告结束。不钉到端点的话，遮罩那张 fillMaxSize 的点击面会一直挂着吃事件，
        // 面板也会留一条贴边残影。
        if (progress.value != endpoint) progress.snapTo(endpoint)
    }

    /** 松手收尾：照 fluxdo `settle()` —— 先看甩动速度，不够快再按位置就近。 */
    fun settle(velocity: Float) {
        val target = if (abs(velocity) >= DrawerFlingThreshold) {
            if (velocity > 0f) 1f else 0f
        } else {
            if (progress.value >= 0.5f) 1f else 0f
        }
        scope.launch {
            progress.animateTo(
                targetValue = target,
                animationSpec = DrawerSpring,
                initialVelocity = velocity / panelPx,
            )
            if (progress.value != target) progress.snapTo(target)
            if (target == 0f) onDismiss()
        }
    }

    /** 跟手：位移直接换成进度增量，拖动期间不让弹簧插手。 */
    val dragState = rememberDraggableState { delta ->
        val next = (progress.value + delta / panelPx).coerceIn(0f, 1f)
        scope.launch {
            if (progress.isRunning) progress.stop()
            progress.snapTo(next)
            // 一拖到 0 就当场把开合状态收掉：面板一进 0，下面那句早退会把整个抽屉从组合里拿走，
            // 手势节点跟着消失，onDragStopped 再也不会来——那样 open 会永远停在 true，
            // 抽屉再也打不开。
            if (next <= 0f) onDismiss()
        }
    }
    val dragModifier = Modifier.draggable(
        state = dragState,
        orientation = Orientation.Horizontal,
        onDragStopped = { velocity -> settle(velocity) },
    )

    val visible = progress.value.coerceIn(0f, 1f)
    // 只在"确实关了"的时候才退出组合（open=true 而进度还是 0 的那一帧要留着，手势还在上面）。
    if (!open && visible <= 0f) return

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩：点击关闭、也能横向拖着关；透明度跟着进度走，手拖到一半就只压一半。
        // 透明度在 draw 阶段读进度（`drawBehind`）：拖动时只重画，不重组整棵子树。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Color.Black.copy(
                            alpha = DrawerScrimAlpha * progress.value.coerceIn(0f, 1f),
                        ),
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .then(dragModifier),
        )

        Column(
            modifier = Modifier
                .width(DrawerPanelWidth)
                .fillMaxHeight()
                .offset {
                    IntOffset(
                        ((progress.value.coerceIn(0f, 1f) - 1f) * panelPx).roundToInt(),
                        0,
                    )
                }
                .then(dragModifier)
                .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                .background(scheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // —— 头部：分类 ⇄ 标签 ——
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawerTabSwitcher(showTags = showTags, onChanged = { showTags = it })
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    FlGlyphIcon(
                        glyph = FlGlyphs.close,
                        size = 20.dp,
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 24.dp),
            ) {
                if (!showTags) {
                    item { DrawerSectionLabel("全部分类") }
                    item {
                        DrawerRow(
                            accent = scheme.primary,
                            label = "全部话题",
                            selected = category.isBlank(),
                            onClick = {
                                onPickCategory("")
                                onDismiss()
                            },
                        ) {
                            FlGlyphIcon(
                                glyph = FlGlyphs.appsList,
                                size = 18.dp,
                                tint = scheme.primary,
                            )
                        }
                    }
                    items(categories) { name ->
                        // 与卡片、详情页同一个取色来源：先看下发的 hex，没有才落哈希兜底。
                        val accent = categoryColorOf(category = name, hex = categoryColors[name].orEmpty())
                        DrawerRow(
                            accent = accent,
                            label = name,
                            selected = name == category,
                            onClick = {
                                onPickCategory(name)
                                onDismiss()
                            },
                        ) {
                            // 分类没有图标也没有 logo（后端只存一个字符串），这里就是一颗分类色实心点。
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(accent),
                            )
                        }
                    }
                } else if (tags.isEmpty()) {
                    item { DrawerSectionLabel("标签") }
                    item {
                        Text(
                            text = "这批帖子上还没有标签",
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, end = 8.dp),
                            fontSize = 13.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item { DrawerSectionLabel("标签") }
                    items(tags) { entry ->
                        val heat = if (tags.first().count > 0) {
                            entry.count.toFloat() / tags.first().count
                        } else {
                            0f
                        }
                        DrawerTagRow(
                            name = entry.name,
                            count = entry.count,
                            heat = heat,
                            selected = entry.name == tag,
                            onClick = {
                                onPickTag(entry.name)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 抽屉「标签」页的一行：名字与它在这批帖子里的次数（热度条按榜首归一化）。 */
internal data class PostTagCount(val name: String, val count: Int)

/**
 * 头部那个「分类 ⇄ 标签」分段切换，照 `_DrawerTabSwitcher` 的紧凑口径：
 *
 * 轨道内边距 **3**、圆角 **23**，底色 `surfaceContainerHighest` **45%**；每一段左右 **16**、
 * 上下 **7**、圆角 **20**，选中时铺 `secondaryContainer` 并把字加粗到 w600。
 *
 * 没有复用 `FlSegmentedControl`：那个是**通栏** 38dp 的滑拇指控件（槽位平分整宽），而侧栏头部
 * 这里是一条**靠左的小胶囊**，右边还要留关闭按钮的位置。
 */
@Composable
private fun DrawerTabSwitcher(showTags: Boolean, onChanged: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(23.dp))
            .background(scheme.surfaceContainerHighest.copy(alpha = 0.45f))
            .padding(3.dp),
    ) {
        DrawerTab("分类", selected = !showTags) { onChanged(false) }
        DrawerTab("标签", selected = showTags) { onChanged(true) }
    }
}

@Composable
private fun RowScope.DrawerTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) scheme.secondaryContainer else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 分区标题（「全部分类」/「标签」）：12sp、w600、字距 0.3。 */
@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 6.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 侧栏的一行（分类 / 全部话题）：高 48、圆角 14、左右 10；左边 32dp 图标块（分类色 14% 底、
 * 圆角 9），块与名字之间 12，名字 14.5sp，选中时右端一枚勾。
 *
 * 名字用 `weight(1f)` 占满剩下的宽度：没有它，长分类名与右端的勾会互相挤，勾会飘到名字中间。
 *
 * 没有缩进层级：我们的分类是平的（后端只存一个字符串，没有父子），fluxdo 那个 `left: 20`
 * 是给"父分类展开后的子分类"用的，这里没有那种行。
 */
@Composable
private fun DrawerRow(
    accent: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 14.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Spacer(modifier = Modifier.width(4.dp))
            FlGlyphIcon(glyph = FlGlyphs.check, size = 18.dp, tint = scheme.primary)
        }
    }
}

/**
 * 标签行，与分类行同构，多两样小东西：
 *
 * - 名字下面那条 **3dp** 热度条：底是 `surfaceContainerHighest` 60%，前面按 heat 铺一段
 *   强调色 65%。至少留 2%，否则"冷门"那几行看起来像没加载出来。
 * - 右端是出现在这批帖子里的次数（过千写成 `1.2k`，12sp）。
 *
 * 颜色统一走强调色：fluxdo 那套按标签名从一张图标表里取色，我们没有那种表（标签是用户在
 * 帖子里自由写的字符串），硬按名字哈希上色会和分类色撞车，反而分不清哪个是分类。
 */
@Composable
private fun DrawerTagRow(
    name: String,
    count: Int,
    heat: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = name, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "#", fontSize = 15.sp, color = accent, maxLines = 1)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.5.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.surfaceContainerHighest.copy(alpha = 0.6f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(heat.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .background(accent.copy(alpha = 0.65f)),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = drawerCountText(count),
            fontSize = 12.sp,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 侧栏右端那个次数：四位数起缩写，免得几位数把名字挤窄。 */
private fun drawerCountText(count: Int): String =
    if (count >= 1000) "${(count / 100) / 10f}k" else count.toString()