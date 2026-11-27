package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 行内标记：`**粗**` `*斜*` `_斜_` `` `代码` `` `~~删除~~` `[文字](链接)`，以及**裸地址**
 * （`https://…` 直接粘进来那种）。不处理块级。
 *
 * 只认**成对**的标记：没闭合的那一半原样输出，因为帖子正文常有人手打一个星号，
 * 吃掉它比留着更难看。链接是**可点**的（`LinkAnnotation.Url`，点击由 `LocalUriHandler` 打开，
 * 与 [io.github.samolego.canta.ui.component.text.UrlText] 同一套做法）——
 * 参考实现里正文的链接都能点，之前这里只上色，等于整页链接都是死字。
 *
 * 这个函数不读主题（不是 @Composable），颜色全靠调用方给：正文用 primary，
 * 引用块里也用 primary 就够了，代码片的底色借用它的低透明度。
 */
fun markdownInline(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    appendMarked(text, linkColor)
}

/**
 * 块级渲染：标题、段落、无序/有序列表、引用、围栏代码块（带语言标签与复制）、**GFM 表格**、
 * 分割线、整行图片、整行视频地址、空行。
 *
 * 图文混排的帖子不需要完整的 Markdown，只要"看起来分了段"。所以这里不做嵌套块、不做 HTML，
 * 遇到不认识的行就当段落——宁可原样显示，也不要把用户写的字吞掉（表格的分隔行或列数对不上时
 * 也退回段落，见 [parseTableAt]）。
 *
 * 参考实现（Lingyan000/fluxdo v0.2.31）那一侧不是自己拼 UI，而是把 raw 交给 `markdown` 包的
 * `ExtensionSet.gitHubFlavored` 出 HTML（`lib/widgets/markdown_editor/markdown_renderer.dart:220-223`），
 * 再由 `fluxdo_render` 渲染。这里没有 HTML 中间层，所以只把那张扩展表里**帖子里真会出现**的几样
 * 搬过来：表格、代码块语言、视频地址卡片。
 */
@Composable
fun MiniMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    // 正文可能几千字，逐帧重新切行没必要：文本不变就不重算。
    val blocks = remember(text) { parseBlocks(text) }
    val linkColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        // 用下标 while 而不是 `forEach`：**连续的多张图片要合成一块图片墙**（参考实现里图片
        // 是按 `[grid]`/相邻图片成组渲染的，见 `builders/image_grid_builder.dart`），
        // 而分组必须能看到"下一块是什么"，`forEach` 给不了这个视角。
        var index = 0
        while (index < blocks.size) {
            val block = blocks[index]
            if (block is MdBlock.Image) {
                // 连续图片成组：中间**只允许**隔一个空行块（`Gap`）——
                // `![](a.png)` 空一行 `![](b.png)` 在 Markdown 里就是两组图片撞在一起，
                // 用户的本意几乎总是"这几张是一起的"。
                val group = mutableListOf(block)
                var next = index + 1
                while (next < blocks.size) {
                    val candidate = blocks[next]
                    if (candidate is MdBlock.Image) {
                        group += candidate
                        next++
                    } else if (candidate is MdBlock.Gap &&
                        next + 1 < blocks.size &&
                        blocks[next + 1] is MdBlock.Image
                    ) {
                        // 空行只是分隔，不算一组内容的结束。
                        next++
                    } else {
                        break
                    }
                }
                val urls = group.map { it.url }
                if (urls.size > 1) {
                    // 两张以上交给图片墙：2 列方图、9 张以内全铺开、再多第 9 格显示 +N。
                    PostImageGrid(
                        urls = urls,
                        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                    )
                } else {
                    // 单张仍然走单图那套（按真实宽高比铺满内容宽），不要因为多了个 `PostImageGrid`
                    // 就把单图也压成方图 —— 长截图在方格里会被裁掉两头。
                    PostImage(
                        url = group[0].url,
                        alt = group[0].alt,
                        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                    )
                }
                index = next
                continue
            }
            when (block) {
                is MdBlock.Heading -> Text(
                    text = markdownInline(block.text, linkColor),
                    style = headingStyle(block.level),
                    color = color,
                    // 标题只是加粗变色不够，跟上一段之间要留一点气口。
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )

                is MdBlock.Paragraph -> Text(
                    text = markdownInline(block.text, linkColor),
                    style = baseStyle,
                    color = color,
                    softWrap = true,
                )

                is MdBlock.BulletList -> Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    block.items.forEach { item ->
                        Row {
                            Text(
                                text = "•",
                                style = baseStyle,
                                color = color,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                            Text(
                                text = markdownInline(item, linkColor),
                                style = baseStyle,
                                color = color,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.OrderedList -> Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    block.items.forEach { item ->
                        Row {
                            Text(
                                text = item.marker,
                                style = baseStyle,
                                color = color,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                            Text(
                                text = markdownInline(item.text, linkColor),
                                style = baseStyle,
                                color = color,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Quote -> Text(
                    text = markdownInline(block.text, linkColor),
                    style = baseStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            // 底色从 onSurfaceVariant 取 6%：深浅主题下都只是"比正文暗一层"，
                            // 不用另配一套引用色。
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                )

                is MdBlock.CodeBlock -> CodeBlock(block.code, block.language, color)

                // 表格整块自己画（表头底色 + 横向滚动），单元格里的行内标记仍走 [markdownInline]。
                is MdBlock.Table -> MdTable(
                    header = block.header,
                    rows = block.rows,
                    baseStyle = baseStyle,
                    color = color,
                    linkColor = linkColor,
                )

                // 图片在上面那个分组循环里已经消费掉了（单张走 `PostImage`、连续多张走
                // `PostImageGrid`）。这里保留分支只为让这个 `when` 对 `MdBlock` 仍然**穷尽**：
                // Kotlin 2.x 对 sealed 类型上的 `when` 语句也要求穷尽，漏一个就是编译错误。
                is MdBlock.Image -> Unit

                // 整行一个视频地址（`.mp4`/`.webm`/`.mov`）：画成卡片，点开交给系统浏览器，
                // 见 [videoLinkOf] 与 [VideoCard]。
                is MdBlock.Video -> VideoCard(block.url)

                is MdBlock.Rule -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                is MdBlock.Gap -> Spacer(modifier = Modifier.height(4.dp))
            }
            index++
        }
    }
}

/** 标题字号按级别往下压，四级正好落到正文大小，再深的级别按四级处理。 */
@Composable
private fun headingStyle(level: Int): TextStyle {
    val style = when (level) {
        1 -> MaterialTheme.typography.titleMedium
        2 -> MaterialTheme.typography.titleSmall
        3 -> MaterialTheme.typography.bodyLarge
        else -> MaterialTheme.typography.bodyMedium
    }
    return style.copy(fontWeight = FontWeight.Bold)
}

/**
 * 围栏代码块：一条 28dp 的标题行（左语言名、右复制）+ 代码本体。
 *
 * 单独一个 composable 是为了让 `rememberScrollState()` 有个固定的调用点：
 * 写在 [MiniMarkdown] 的 forEach 里，块数一变 scroll 位置就会串到别的块上。
 *
 * 标题行的结构照抄参考实现的代码块外壳（`lib/utils/fluxdo_render_callbacks.dart:1920-2017`）：
 * 顶栏 `padding(horizontal = 8, vertical = 4)` + 一条底部分隔线，左边语言/模式名 `fontSize 12`、
 * `onSurface` 70%，右边一颗 16dp 的 `content_copy_rounded`，点一下 `Clipboard.setData` 并弹一句
 * 「已复制代码」（文案 `lib/l10n/modules/common/common_zh.arb:30`）。这里按需求把整条压到 28dp、
 * 图标 18dp，并按主题改用 `labelSmall` + `onSurfaceVariant`（顶栏底色在深浅主题下都是
 * `surfaceVariant`，`onSurface 70%` 与 `onSurfaceVariant` 在这个底色上读感一致）。
 *
 * 剪贴板用 `LocalClipboardManager.setText(AnnotatedString(...))`——仓库里 `ChatScreen.kt:649`
 * （复制消息）、`TweaksScreen.kt:156`（复制命令）都是这一套，commonMain 三端（安卓/桌面/wasm）
 * 通用；不能用 `android.content.ClipboardManager`，那是 androidMain 才有的东西。
 */
@Composable
private fun CodeBlock(code: String, language: String, color: Color) {
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 先 clip 再铺底色：标题行的底色会被外圈圆角裁掉，不会顶出直角。
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // 没写 info string 的围栏就写「代码」：顶栏空着左侧会像少画了一块。
                text = language.ifBlank { "代码" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            // 28dp 的方框 + 18dp 的图标：与顶栏同高，点按区域不越出这条标题行。
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { clipboard.setText(AnnotatedString(code)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "复制代码",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = color,
            // 代码不折行：折过之后缩进全乱，横向滚动比看不懂强。
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(scroll)
                .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// 表格
// ---------------------------------------------------------------------------

/**
 * 单元格左右内边距。需求里的「列与列之间 12dp 内边距」落在每格自己身上：相邻两格的留白相加
 * 就是两列之间的距离，格子左边第一列、右边最后一列也各有这一段，文字不贴着表格边缘。
 * 参考实现的 web 端表格是 `thead th`/`tbody td` 各 `padding: 8px`（`lib/utils/export_utils.dart:648`、
 * `:655`），8dp 在手机上太挤，按需求取 12dp。
 */
private val TableCellPadding = 12.dp

/** 单元格上下内边距（需求给的 6dp）。 */
private val TableCellPaddingVertical = 6.dp

/**
 * 单列内容宽的上限。
 *
 * 列宽本来按内容量（见 [MdTable]），但一段两百字的备注能把整张表撑到一个屏幕都滚不完，
 * 所以量到超过这个数就让文字在格子里折行（行高自然变高）。200dp 比正文宽度的六成还窄，
 * 手机上"一格最多折两三行"。
 */
private val TableCellMaxContentWidth = 200.dp

/**
 * GFM 表格：表头一行 + 分隔行 + 若干数据行，整表可横向滚动。
 *
 * 为什么这么排：
 *  * **列宽按内容量出来**——`rememberTextMeasurer()` 逐格量一次（与 `PlanDanmaku.kt:171-181`、
 *    `FloatingPillBar.kt:429-433` 同一套做法：`measure(AnnotatedString(...), style, softWrap = false,
 *    maxLines = 1)`，取 `size.width`），每列取该列最宽的一格。量的是**已经上过行内标记**的
 *    AnnotatedString，因为粗体/行内码比原文字宽，用原文量会让粗体那格折行。不做等宽强制
 *    （没有 `weight(1f)`）：两列的表里「机型」那列就该比「版本」窄。
 *  * **整表 clip 成 8dp 圆角**再交给 `horizontalScroll`：滚动内容在 Box 内绘制，圆角把表头底色
 *    的四个角一起裁掉；不 clip 的话横向滚动会把表头底色顶成直角的色块。
 *  * **数据行之间一条 1dp 分隔线**，宽度取各列之和（`horizontalScroll` 里宽度无界，
 *    `HorizontalDivider` 默认的 `fillMaxWidth()` 在这种约束下量成 0，必须显式给宽）。出处是参考
 *    实现的 `tbody td { border-bottom: 1px solid }`，最后一行不画（`export_utils.dart:654-661`）。
 */
@Composable
private fun MdTable(
    header: List<String>,
    rows: List<List<String>>,
    baseStyle: TextStyle,
    color: Color,
    linkColor: Color,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val headerStyle = baseStyle.copy(fontWeight = FontWeight.Bold)
    val columnWidths: List<Dp> = remember(header, rows, baseStyle, linkColor, density, measurer) {
        // 表头与数据行一起参与量宽：表头是粗体，只量数据行会让表头那格折行。
        val allRows: List<Pair<List<String>, Boolean>> =
            listOf(header to true) + rows.map { row -> row to false }
        header.indices.map { column ->
            val widestPx = allRows.maxOf { entry ->
                val text = entry.first.getOrNull(column).orEmpty()
                if (text.isEmpty()) {
                    0
                } else {
                    measurer.measure(
                        text = markdownInline(text, linkColor),
                        style = if (entry.second) headerStyle else baseStyle,
                        overflow = TextOverflow.Clip,
                        softWrap = false,
                        maxLines = 1,
                    ).size.width
                }
            }
            // 量出来的是像素，布局要的是 dp；再补回左右内边距，那一格才装得下自己的字。
            with(density) { widestPx.toFloat().toDp() }.coerceAtMost(TableCellMaxContentWidth) +
                TableCellPadding * 2f
        }
    }
    val tableWidth = columnWidths.fold(0.dp) { sum, column -> sum + column }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .horizontalScroll(scroll),
    ) {
        Column {
            TableRow(header, columnWidths, headerStyle, color, linkColor, header = true)
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.width(tableWidth),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
                TableRow(row, columnWidths, baseStyle, color, linkColor, header = false)
            }
        }
    }
}

/**
 * 表格的一行。单元格正文用调用方给的 [style]（表头是粗体版），行内标记照旧走 [markdownInline]，
 * 所以 `**加粗**`、`` `代码` ``、链接在格子里都还能用。
 *
 * 宽度用 [MdTable] 量好的固定值而不是 `weight`：固定值才能保证同一列上下对齐。
 * 高度不同的行靠 `CenterVertically` 对齐（折行的长句子把行撑高时，短的那格居中不贴顶）。
 */
@Composable
private fun TableRow(
    cells: List<String>,
    widths: List<Dp>,
    style: TextStyle,
    color: Color,
    linkColor: Color,
    header: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { column, text ->
            Text(
                text = markdownInline(text, linkColor),
                style = style,
                color = color,
                modifier = Modifier
                    .width(widths[column])
                    .then(
                        if (header) {
                            // 表头底色：surfaceVariant 60%（需求给的数值）。深浅主题下都只是
                            // "比正文底再深一档"，不用另配一套表格色。
                            Modifier.background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        horizontal = TableCellPadding,
                        vertical = TableCellPaddingVertical,
                    ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 视频地址卡片
// ---------------------------------------------------------------------------

/**
 * 整行一个视频地址（`https://…/clip.mp4`）时画这张卡：16:9 的框，中间一颗播放圈，
 * 下面一行提示和文件名，整卡可点。
 *
 * 为什么不走 `LinkAnnotation.Url`：链接注解是挂在**文字**上的，卡片没有文字可点（`Modifier`
 * 区域不在注解的命中范围里），所以用 `Modifier.clickable` + `LocalUriHandler.openUri`。
 * 参考实现那边视频卡也是整块 `onTap` 交给 `launchContentLink`
 * （`lib/widgets/content/discourse_html_content/builders/onebox/video_onebox_builder.dart:60`），
 * 16:9 的框（`:69-71`）、居中播放键（`:105-119`）、圆角 7（`:71-73`）都是照它来的，
 * 圆角取 8dp 与文件里其它卡片统一。
 *
 * 文件名只取路径最后一段并用 `TextOverflow.Ellipsis` 截断（参考实现的信息行同样是
 * `labelSmall` + `onSurfaceVariant`，见 `video_onebox_builder.dart:186-191`）：整条 URL
 * 太长，显示出来只会把卡片挤成两行。
 */
@Composable
private fun VideoCard(url: String) {
    val uriHandler = LocalUriHandler.current
    val fileName = remember(url) { videoFileName(url) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { uriHandler.openUri(url) },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = null,
                    // 播放键用 primary：卡底是 surfaceContainerHigh，浅色主题下白图标等于看不见。
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = "视频 · 点开用浏览器播放",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Text(
            text = fileName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// 块级解析
// ---------------------------------------------------------------------------

private sealed interface MdBlock {
    /** [level] 是 `#` 的个数，1..4。 */
    class Heading(val level: Int, val text: String) : MdBlock
    class Paragraph(val text: String) : MdBlock
    class BulletList(val items: List<String>) : MdBlock
    class OrderedList(val items: List<OrderedItem>) : MdBlock
    /** 连续多行 `> ` 已经拼成一段，行间保留换行。 */
    class Quote(val text: String) : MdBlock
    /** [language] 是围栏后的 info string（```kotlin 里的 `kotlin`），没写就是空串。 */
    class CodeBlock(val code: String, val language: String) : MdBlock
    /**
     * GFM 表格。[header] 与 [rows] 的每一行**列数都相同**（解析时已经补齐，见 [parseTableAt]），
     * 所以渲染那边可以直接按下标取列宽。
     */
    class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    /** 一行独占一张图：`![说明](http…)`。 */
    class Image(val url: String, val alt: String) : MdBlock
    /** 一行只有一个视频地址（`.mp4`/`.webm`/`.mov`），画成可点的卡片。 */
    class Video(val url: String) : MdBlock
    object Rule : MdBlock
    object Gap : MdBlock
}

/** 有序列表的一项：序号是原文里那个（`3.` 就还是 3，不重新编号）。 */
private class OrderedItem(val marker: String, val text: String)

/**
 * 按行切、逐行判断、把连续同类行聚成一个块。
 *
 * 一行只归一类，判定顺序就是显示上的优先级：围栏最优先（里面的 `#`、`-` 都是代码），
 * 然后是空行/分割线/标题/引用/列表，剩下的才是段落。
 */
private fun parseBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.replace("\r\n", "\n").split('\n')
    var i = 0
    while (i < lines.size) {
        val trimmed = lines[i].trim()
        if (trimmed.startsWith("```")) {
            // info string：```kotlin 里的 `kotlin`。GFM 只认第一个词，后面那些（`{1,3}` 这类
            // 高亮属性）这里用不上，直接丢掉；语言名只做展示，不参与解析。
            val language = trimmed.removePrefix("```").trim().substringBefore(' ').trim()
            // 代码块内部一个字都不动，只去掉收尾围栏那一行。
            val body = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                body += lines[i]
                i++
            }
            // 没有收尾围栏时按"到这里为止"处理，正文不会被吞掉。
            if (i < lines.size) i++
            blocks += MdBlock.CodeBlock(body.joinToString("\n"), language)
            continue
        }
        if (trimmed.isEmpty()) {
            // 连续空行只留一个间隙：两行空行在屏幕上不该变成两个 4dp 的洞。
            if (blocks.lastOrNull() !== MdBlock.Gap) blocks += MdBlock.Gap
            i++
            continue
        }
        if (isRule(trimmed)) {
            blocks += MdBlock.Rule
            i++
            continue
        }
        // 表格要**连着两行**才认（表头 + 分隔行），所以在分割线之后、单行判定之前先试一次。
        // 分隔行或列数对不上一律不认，那两行会落回下面的段落分支原样显示——宁可难看，不吞字。
        val table = parseTableAt(lines, i)
        if (table != null) {
            blocks += table.first
            i = table.second
            continue
        }
        // 图片要先于标题判断：`![x](y)` 里那个 `!` 后面跟的是 `[`，不会被误认成标题，
        // 但顺序上放在这里与代码块、空行一起，读起来就是"整行资源类"的那一组。
        val image = imageBlockOf(trimmed)
        if (image != null) {
            blocks += MdBlock.Image(url = image.second, alt = image.first)
            i++
            continue
        }
        // 视频地址：整行只有一个 http(s) 地址且路径以视频后缀结尾时画卡片，不再走下面
        // 「裸地址 → 可点链接」那条。放在图片之后：`![](x.mp4)` 是明确的图片语法，那是图。
        val video = videoLinkOf(trimmed)
        if (video != null) {
            blocks += MdBlock.Video(video)
            i++
            continue
        }
        val level = headingLevel(trimmed)
        if (level > 0) {
            blocks += MdBlock.Heading(level, trimmed.substring(level).trim())
            i++
            continue
        }
        if (trimmed.startsWith(">")) {
            val quoted = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoted += lines[i].trim().removePrefix(">").trim()
                i++
            }
            blocks += MdBlock.Quote(quoted.joinToString("\n"))
            continue
        }
        if (bulletText(trimmed) != null) {
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val item = bulletText(lines[i].trim()) ?: break
                items += item
                i++
            }
            blocks += MdBlock.BulletList(items)
            continue
        }
        if (orderedText(trimmed) != null) {
            val items = mutableListOf<OrderedItem>()
            while (i < lines.size) {
                val item = orderedText(lines[i].trim()) ?: break
                items += item
                i++
            }
            blocks += MdBlock.OrderedList(items)
            continue
        }
        // 段落：吃到空行或下一个块的开头。段内的换行保留，作者按的行就是他要的断句。
        val paragraph = mutableListOf<String>()
        while (i < lines.size) {
            // 这里要带上下标：表格是不是开头得看**下一行**是不是分隔行，单看这一行判不出来。
            if (startsBlock(lines, i)) break
            paragraph += lines[i].trim()
            i++
        }
        blocks += MdBlock.Paragraph(paragraph.joinToString("\n"))
    }
    return blocks
}

/**
 * 这一行会不会自己起一个新块。段落只在这些行前面断开。
 *
 * 只有**单行就能判定**的都放这里；表格要多看一行，见下面的重载。
 */
private fun startsBlock(trimmed: String): Boolean =
    trimmed.isEmpty() ||
        trimmed.startsWith("```") ||
        isRule(trimmed) ||
        headingLevel(trimmed) > 0 ||
        trimmed.startsWith(">") ||
        bulletText(trimmed) != null ||
        orderedText(trimmed) != null ||
        imageBlockOf(trimmed) != null ||
        videoLinkOf(trimmed) != null

/**
 * 带上下标的版本：段落循环用它。
 *
 * 少了这一条，一段落往上吃的时候会把表格的表头行当普通文字吞掉（表头行单看就是个带 `|` 的句子），
 * 接下来分隔行又对不上表头，整张表就退化成两行乱码。
 */
private fun startsBlock(lines: List<String>, index: Int): Boolean =
    startsBlock(lines[index].trim()) || parseTableAt(lines, index) != null

/**
 * `![说明](http…)` 整行时返回 `说明 to 地址`，否则 null。
 *
 * 只认 http(s)：别的地址（相对路径、`data:`）既下不到也没意义，按普通文字留着更诚实。
 * 说明可以空（`![](…)`），空串就让 [PostImage] 用「图片」兜底。
 */
private val ImagePattern = Regex("""^!\[([^\]]*)\]\((https?://[^\s)]+)\)$""")

private fun imageBlockOf(trimmed: String): Pair<String, String>? {
    val hit = ImagePattern.matchEntire(trimmed) ?: return null
    return hit.groupValues[1].trim() to hit.groupValues[2]
}

/** `---` 或 `***`，整行只有同一个字符且至少三个。 */
private fun isRule(trimmed: String): Boolean {
    if (trimmed.length < 3) return false
    val marker = trimmed[0]
    if (marker != '-' && marker != '*') return false
    return trimmed.all { it == marker }
}

/**
 * `#` 的个数；不是标题返回 0。
 *
 * 后面必须跟一个空白：`#话题` 这种是正文里的标签，不是标题；中文输入法打出的全角空格也算。
 */
private fun headingLevel(trimmed: String): Int {
    var level = 0
    while (level < trimmed.length && level < 4 && trimmed[level] == '#') level++
    if (level == 0) return 0
    if (level >= trimmed.length || !trimmed[level].isWhitespace()) return 0
    return level
}

/** `- ` / `* ` / `+ ` 开头时返回去掉标记的正文。 */
private fun bulletText(trimmed: String): String? {
    if (trimmed.length < 2) return null
    val marker = trimmed[0]
    if (marker != '-' && marker != '*' && marker != '+') return null
    if (trimmed[1] != ' ') return null
    return trimmed.substring(2).trim()
}

private val OrderedPattern = Regex("""^(\d{1,3})\.\s+(.*)$""")

/** `1. ` 这种开头时返回序号与正文。 */
private fun orderedText(trimmed: String): OrderedItem? {
    val hit = OrderedPattern.matchEntire(trimmed) ?: return null
    return OrderedItem("${hit.groupValues[1]}.", hit.groupValues[2].trim())
}

// ---------------------------------------------------------------------------
// 表格解析
// ---------------------------------------------------------------------------

/**
 * 从 [index] 起试着认一张 GFM 表格，认出来就返回 `表 to 下一行的下标`。
 *
 * 判定三件套（缺一不认，宁可退成段落）：
 *  1. 表头行和分隔行**都**得含 `|`。少了这条，`---` 那种水平线（[isRule] 已经先吃掉了）
 *     和「一列的表」会互相打架；
 *  2. 分隔行每一格都是 `---` / `:--` / `--:` / `:-:`（[isTableDivider]）；
 *  3. 表头格数 == 分隔行格数。这是 GFM 的硬规矩，也是需求里「列数对不上就原样当段落」那一条。
 *
 * 数据行一直吃到空行、不含 `|` 的行、另一条分隔行或别的块开头为止；**每行列数补齐到最宽的那行**
 * （多出来的格保留、缺的补空串），这样渲染那边 `header.indices` 就能直接按下标取列宽，
 * 也不会因为某行多打了一个 `|` 就把正文丢掉。参考实现走 `ExtensionSet.gitHubFlavored`
 * （`lib/widgets/markdown_editor/markdown_renderer.dart:220-223`），表格语义就是那张扩展表定下的。
 */
private fun parseTableAt(lines: List<String>, index: Int): Pair<MdBlock.Table, Int>? {
    if (index + 1 >= lines.size) return null
    val headerLine = lines[index].trim()
    val dividerLine = lines[index + 1].trim()
    if (!headerLine.contains('|') || !dividerLine.contains('|')) return null
    // 表头行自己不能是别的块：`- a | b` 是列表项、`# a | b` 是标题，都不该被表格抢走。
    if (startsBlock(headerLine)) return null
    if (!isTableDivider(dividerLine)) return null
    val header = splitTableCells(headerLine)
    if (header.isEmpty() || header.size != splitTableCells(dividerLine).size) return null
    val body = mutableListOf<List<String>>()
    var i = index + 2
    while (i < lines.size) {
        val line = lines[i].trim()
        // 段内换行对表格没意义：空行、没有竖线的行、又一条分隔行、别的块开头，都是表尾。
        if (line.isEmpty() || !line.contains('|') || isTableDivider(line) || startsBlock(line)) break
        body += splitTableCells(line)
        i++
    }
    val columns = maxOf(header.size, body.maxOfOrNull { row -> row.size } ?: 0)
    fun pad(cells: List<String>): List<String> =
        if (cells.size >= columns) cells else cells + List(columns - cells.size) { "" }
    return MdBlock.Table(pad(header), body.map { row -> pad(row) }) to i
}

/**
 * `| --- | :--: |` 这种分隔行。
 *
 * 每格去掉两端空白后必须是「可选的一个 `:` + 至少一个 `-` + 可选的一个 `:`」。要求整行含 `|`
 * 是刻意的：不带竖线的 `---` 在 Markdown 里是水平线（[isRule]），不能当表格分隔行。
 */
private fun isTableDivider(trimmed: String): Boolean {
    if (!trimmed.contains('|')) return false
    val cells = splitTableCells(trimmed)
    if (cells.isEmpty()) return false
    return cells.all { cell ->
        val body = cell.trim().removePrefix(":").removeSuffix(":")
        body.isNotEmpty() && body.all { it == '-' }
    }
}

/**
 * 按 `|` 切一格一格的正文，两端的竖线不算空格子（`| a | b |` 是两格不是四格）。
 *
 * `\|` 是 GFM 里转义出来的字面竖线，这里保留成一个 `|` 字符：不这么处理的话，
 * 「A\|B」这种写法会被切成两格，后半截直接串到下一列去。
 */
private fun splitTableCells(line: String): List<String> {
    var body = line.trim()
    if (body.startsWith("|")) body = body.substring(1)
    if (body.endsWith("|") && !body.endsWith("\\|")) body = body.dropLast(1)
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var i = 0
    while (i < body.length) {
        val ch = body[i]
        if (ch == '\\' && i + 1 < body.length && body[i + 1] == '|') {
            cell.append('|')
            i += 2
            continue
        }
        if (ch == '|') {
            cells += cell.toString().trim()
            cell.clear()
            i++
            continue
        }
        cell.append(ch)
        i++
    }
    cells += cell.toString().trim()
    return cells
}

// ---------------------------------------------------------------------------
// 视频地址
// ---------------------------------------------------------------------------

/** 认作"视频"的扩展名（都按小写比）。 */
private val VideoSuffixes = listOf(".mp4", ".webm", ".mov")

/**
 * 整行只有一个 http(s) 地址、且路径以视频后缀结尾时返回它，否则 null。
 *
 * 只认**整行**：正文里顺手提一句「见 https://a/b.mp4」不该变成一张大卡片，那句话还按原来的
 * 裸地址链接走（[bareUrlEnd]）。中间有空白就不是"整行一个地址"，直接放行给段落。
 * 查后缀前先砍掉 `?query` 和 `#fragment`——带参数的地址同样以 `.mp4` 结尾（`clip.mp4?t=3`）。
 */
private fun videoLinkOf(trimmed: String): String? {
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
    if (trimmed.any { it.isWhitespace() }) return null
    val path = trimmed.substringBefore('#').substringBefore('?').lowercase()
    if (VideoSuffixes.none { suffix -> path.endsWith(suffix) }) return null
    return trimmed
}

/** 视频卡片上显示的文件名：路径最后一段；取不到就退回整条地址。 */
private fun videoFileName(url: String): String {
    val path = url.substringBefore('#').substringBefore('?').trimEnd('/')
    return path.substringAfterLast('/').ifBlank { url }
}

// ---------------------------------------------------------------------------
// 行内解析
// ---------------------------------------------------------------------------

/**
 * 命中的一对行内标记：[contentStart]..[contentEnd] 是要输出的内容，[end] 是整段标记之后的位置。
 *
 * [deep] 表示内容里还要不要继续解析：粗体里套斜体是正常的，代码和链接文字里不该再有标记。
 * [url] 非空时这段内容按**可点链接**输出（`LinkAnnotation.Url`，与 [UrlText] 同一套做法）。
 */
private class InlineMatch(
    val end: Int,
    val contentStart: Int,
    val contentEnd: Int,
    val style: SpanStyle,
    val deep: Boolean,
    val url: String? = null,
)

/**
 * 在 [start] 处试每种标记，命中就返回。返回 null 表示这个字符按字面输出。
 *
 * 长的先试：`**` 在 `*` 前面，否则粗体会被当成两个斜体。
 */
private fun matchInlineAt(text: String, start: Int, linkColor: Color): InlineMatch? {
    if (text[start] == '`') {
        val close = text.indexOf('`', start + 1)
        // 空的 `` 不算代码片段，按字面留着。
        if (close > start + 1) {
            return InlineMatch(
                end = close + 1,
                contentStart = start + 1,
                contentEnd = close,
                style = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    // 透明度借用链接色：正文只有正文色和链接色两个颜色可用，
                    // 代码片的底只能这么凑，读起来是"一块浅底"，不抢眼。
                    background = linkColor.copy(alpha = 0.12f),
                ),
                deep = false,
            )
        }
        return null
    }
    if (text.startsWith("**", start)) {
        val close = text.indexOf("**", start + 2)
        if (close > start + 2) {
            return InlineMatch(
                end = close + 2,
                contentStart = start + 2,
                contentEnd = close,
                style = SpanStyle(fontWeight = FontWeight.Bold),
                deep = true,
            )
        }
        return null
    }
    if (text.startsWith("~~", start)) {
        val close = text.indexOf("~~", start + 2)
        if (close > start + 2) {
            return InlineMatch(
                end = close + 2,
                contentStart = start + 2,
                contentEnd = close,
                style = SpanStyle(textDecoration = TextDecoration.LineThrough),
                deep = true,
            )
        }
        return null
    }
    if (text[start] == '*') {
        val close = text.indexOf('*', start + 1)
        if (close > start + 1) {
            return InlineMatch(
                end = close + 1,
                contentStart = start + 1,
                contentEnd = close,
                style = SpanStyle(fontStyle = FontStyle.Italic),
                deep = true,
            )
        }
        return null
    }
    if (text[start] == '_') {
        val close = text.indexOf('_', start + 1)
        if (close > start + 1) {
            return InlineMatch(
                end = close + 1,
                contentStart = start + 1,
                contentEnd = close,
                style = SpanStyle(fontStyle = FontStyle.Italic),
                deep = true,
            )
        }
        return null
    }
    if (text[start] == '[') {
        val labelEnd = text.indexOf(']', start + 1)
        if (labelEnd > start + 1 && labelEnd + 1 < text.length && text[labelEnd + 1] == '(') {
            val urlEnd = text.indexOf(')', labelEnd + 2)
            if (urlEnd > labelEnd + 2) {
                val url = text.substring(labelEnd + 2, urlEnd)
                // 只认 http(s)：别的地址当普通文字，写的人可能就是顺手打了个方括号。
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return InlineMatch(
                        end = urlEnd + 1,
                        contentStart = start + 1,
                        contentEnd = labelEnd,
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                        deep = false,
                        url = url,
                    )
                }
            }
        }
        return null
    }
    return null
}

/**
 * 没被行内标记命中的位置，是不是一个**裸地址**（`http://…` / `https://…`）的起点；是就返回结束位置。
 *
 * 为什么要单独认它：帖子里九成的链接都是直接粘进来的地址，`[文字](地址)` 那种写法只有少数人用。
 * 参考实现里这两种都能点开；这里原本一个都不能点，等于正文里所有链接都是死字。
 *
 * 结尾会把标点吃掉：`见 https://a.b/c。` 那个中文句号不属于地址，跟着链接走会 404；
 * `)` `]` `」` 同理（地址常被括在括号里）。至少要有一个像样域名长度才算，否则 `https://` 四个字
 * 也会被当成链接。
 */
private fun bareUrlEnd(text: String, start: Int): Int? {
    if (!text.startsWith("http://", start) && !text.startsWith("https://", start)) return null
    var end = start
    while (end < text.length && !text[end].isWhitespace() && text[end] !in "<>\"'") end++
    while (end > start && text[end - 1] in ".,;:!?，。；：！？、）】」》)]}") end--
    return if (end - start > 8) end else null
}

/** 扫描 [text]，把命中的标记换成样式，没命中的字符原样写进去。 */
private fun AnnotatedString.Builder.appendMarked(text: String, linkColor: Color) {
    var cursor = 0
    var i = 0
    while (i < text.length) {
        val hit = matchInlineAt(text, i, linkColor)
        if (hit == null) {
            val urlEnd = bareUrlEnd(text, i)
            if (urlEnd != null) {
                append(text.substring(cursor, i))
                val url = text.substring(i, urlEnd)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    withLink(LinkAnnotation.Url(url = url)) { append(url) }
                }
                i = urlEnd
                cursor = i
                continue
            }
            i++
            continue
        }
        append(text.substring(cursor, i))
        val inner = text.substring(hit.contentStart, hit.contentEnd)
        withStyle(hit.style) {
            when {
                // 链接文字本身不再解析标记，但要挂上点击目标。
                hit.url != null -> withLink(LinkAnnotation.Url(url = hit.url)) { append(inner) }
                hit.deep -> appendMarked(inner, linkColor)
                else -> append(inner)
            }
        }
        i = hit.end
        cursor = i
    }
    append(text.substring(cursor))
}
