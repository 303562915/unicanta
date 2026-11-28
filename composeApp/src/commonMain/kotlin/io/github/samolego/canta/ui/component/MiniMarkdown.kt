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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 行内标记：`**粗**` `*斜*` `_斜_` `` `代码` `` `~~删除~~` `[文字](链接)`、**裸地址**
 * （`https://…` 直接粘进来那种），以及 `@名字` **提及**（主色 + 半粗，刻意不可点，见 [mentionEndAt]）。
 * 不处理块级。
 *
 * 只认**成对**的标记：没闭合的那一半原样输出，因为帖子正文常有人手打一个星号，
 * 吃掉它比留着更难看。链接是**可点**的（`LinkAnnotation.Url`，点击由 `LocalUriHandler` 打开，
 * 与 [io.github.samolego.canta.ui.component.text.UrlText] 同一套做法）——
 * 参考实现里正文的链接都能点，之前这里只上色，等于整页链接都是死字。
 *
 * 这个函数不读主题（不是 @Composable），颜色全靠调用方给：正文用 primary，
 * 引用块里也用 primary 就够了，代码片的底色借用它的低透明度。
 *
 * [onFloorRef] 非空时，正文里 `#12` 这种**楼层引用**额外做成可点的（主色、无下划线，
 * `LinkAnnotation.Clickable`，见 [floorRefEndAt]）；为 `null`（默认）时这一行**一个字节都不变**，
 * 所以老的调用点不用改。行内码（`` `#12` ``）、代码块、链接文字里的 `#12` 仍然不可点——
 * 它们走的是 [InlineMatch.deep] 为 false 的"原样 append"分支，根本到不了楼层判定。
 */
fun markdownInline(
    text: String,
    linkColor: Color,
    onFloorRef: ((Int) -> Unit)? = null,
): AnnotatedString = buildAnnotatedString {
    appendMarked(text, linkColor, onFloorRef)
}

/**
 * 块级渲染：标题、段落、无序/有序列表、引用、围栏代码块（带语言标签与复制）、**GFM 表格**、
 * 分割线、整行图片、整行视频地址、**整行链接卡**、空行。
 *
 * 图文混排的帖子不需要完整的 Markdown，只要"看起来分了段"。所以这里不做嵌套块、不做 HTML，
 * 遇到不认识的行就当段落——宁可原样显示，也不要把用户写的字吞掉（表格的分隔行或列数对不上时
 * 也退回段落，见 [parseTableAt]）。
 *
 * 参考实现（Lingyan000/fluxdo v0.2.31）那一侧不是自己拼 UI，而是把 raw 交给 `markdown` 包的
 * `ExtensionSet.gitHubFlavored` 出 HTML（`lib/widgets/markdown_editor/markdown_renderer.dart:220-223`），
 * 再由 `fluxdo_render` 渲染。这里没有 HTML 中间层，所以只把那张扩展表里**帖子里真会出现**的几样
 * 搬过来：表格、代码块语言、视频地址卡片。
 *
 * [onFloorRef] 用**带默认值的可选参数**而不是重载：重载得把整段渲染体（下面那个 `when` 十几个分支
 * 加上连续图片分组那段循环）再抄一遍，之后每改一次正文渲染就要同步改两处，抄漏一处就是"一楼和二楼
 * 长得不一样"。默认参数只有一份实现，四个现有调用点（`PostsSection.kt:5449`、`:5687`、`:6128`、
 * `:6757`，都不传它）源码一个字不用改；而且默认值会让 `onFloorRef == null` 把楼层识别整条短路
 * （[appendMarked] 里先判空，[floorRefEndAt] 一次都不跑），所以"不传参数 = 输出与改动前逐项一致"
 * 是**结构性**成立，不靠以后每次改代码时自觉。
 *
 * 引用块有两种画法：首行认得出「楼层号 / 作者 / 冒号」的（本仓「引用回复」塞进正文的那段，见
 * `ui/screen/PostsSection.kt:1816-1822` 的 `quoteComment`）画成 [QuoteCard]，其余引用块**一个像素
 * 都不动**，还是下面那个 6% 底 + 8dp 圆角的普通引用。识别为什么这么保守见 [parseFloorQuote]，
 * 卡片为什么整卡不吃点击、为什么只露 4 行见 [QuoteCard]。
 *
 * 递归渲染（引用卡里的正文）走同文件里那个带 `quoteDepth` 的 [MiniMarkdownContent]：这个公开函数
 * 的签名一个字都没变，四个现有调用点不用改。
 */
@Composable
fun MiniMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    /** 正文里的 `#N` 楼层引用被点击时回调；null = 不做成可点的（保持现状）。 */
    onFloorRef: ((Int) -> Unit)? = null,
) {
    MiniMarkdownContent(
        text = text,
        modifier = modifier,
        baseStyle = baseStyle,
        color = color,
        onFloorRef = onFloorRef,
        // 正文本身在最外层：它是"第 1 层"，里面出现的引用块是第 2 层，见 [QuoteCardMaxDepth]。
        quoteDepth = 0,
    )
}

/**
 * [MiniMarkdown] 的实现体，外加一个只在本文件内流转的 [quoteDepth]（**引用嵌套层数**）。
 *
 * 为什么拆成两层而不是给公开函数加个 `quoteDepth: Int = 0`：那样嵌套层数就成了对外 API 的一部分，
 * 调用方传错一个数字（比如传 2）就能让一楼的那段正文全部退回普通引用，而且以后每加一个"只在递归时
 * 生效"的参数都要再改一次公开签名。拆开之后 [MiniMarkdown] 的签名与行为对调用方**完全不变**，
 * 递归只发生在 [QuoteCard] 内部（那里调用的就是本函数），层数出不了这个文件。
 *
 * [quoteDepth] 只在 [MdBlock.Quote] 那一支里被读：其余块级的渲染与它无关，所以递归只沿"引用里套引用"
 * 这一条路径加深，不会因为正文里有一堆段落就把层数加上去。
 */
@Composable
private fun MiniMarkdownContent(
    text: String,
    modifier: Modifier,
    baseStyle: TextStyle,
    color: Color,
    onFloorRef: ((Int) -> Unit)?,
    quoteDepth: Int,
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
                    text = markdownInline(block.text, linkColor, onFloorRef),
                    style = headingStyle(block.level),
                    color = color,
                    // 标题只是加粗变色不够，跟上一段之间要留一点气口。
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )

                // 段落可能夹着行内图片（`看这张 ![截图](url) 效果`），由 [ParagraphContent]
                // 决定是"一个 Text"还是"文字 + 图片 + 文字"；没有行内图片时它内部只画一个 Text，
                // 与改动前逐字节一致，不额外套一层 Column。
                is MdBlock.Paragraph -> ParagraphContent(
                    text = block.text,
                    baseStyle = baseStyle,
                    color = color,
                    linkColor = linkColor,
                    onFloorRef = onFloorRef,
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
                                text = markdownInline(item, linkColor, onFloorRef),
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
                                text = markdownInline(item.text, linkColor, onFloorRef),
                                style = baseStyle,
                                color = color,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // 引用块分两支：首行认得出「楼层号 / 作者 / 冒号」的画成引用卡（[QuoteCard]），
                // 认不出、或嵌套已经深到 [QuoteCardMaxDepth] 的，**原样走下面这支**。
                // 那支的代码与改动前逐字一致（6% `onSurfaceVariant` 底 + 8dp 圆角 + 10/6 内边距，
                // 单行 `Text`），所以"普通引用逐像素不变"是结构性的：只要 [parseFloorQuote] 返回
                // null 就一个属性都不会经过新的分支。
                is MdBlock.Quote -> {
                    val quoted = parseFloorQuote(block.text)
                    if (quoted != null && quoteDepth < QuoteCardMaxDepth) {
                        QuoteCard(
                            quote = quoted,
                            baseStyle = baseStyle,
                            onFloorRef = onFloorRef,
                            quoteDepth = quoteDepth,
                        )
                    } else {
                        Text(
                            text = markdownInline(block.text, linkColor, onFloorRef),
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
                    }
                }

                is MdBlock.CodeBlock -> CodeBlock(block.code, block.language, color)

                // 表格整块自己画（表头底色 + 横向滚动），单元格里的行内标记仍走 [markdownInline]。
                is MdBlock.Table -> MdTable(
                    header = block.header,
                    rows = block.rows,
                    alignments = block.alignments,
                    baseStyle = baseStyle,
                    color = color,
                    linkColor = linkColor,
                    onFloorRef = onFloorRef,
                )

                // 图片在上面那个分组循环里已经消费掉了（单张走 `PostImage`、连续多张走
                // `PostImageGrid`）。这里保留分支只为让这个 `when` 对 `MdBlock` 仍然**穷尽**：
                // Kotlin 2.x 对 sealed 类型上的 `when` 语句也要求穷尽，漏一个就是编译错误。
                is MdBlock.Image -> Unit

                // 整行一个视频地址（`.mp4`/`.webm`/`.mov`）：画成卡片，点开交给系统浏览器，
                // 见 [videoLinkOf] 与 [VideoCard]。
                is MdBlock.Video -> VideoCard(block.url)

                // 整行一个普通 http(s) 地址：画成链接卡（左上角域名首字母方块 + 域名 + 路径），
                // 见 [LinkPreviewCard]。视频那一支在它上面，所以 `.mp4` 永远走视频卡。
                is MdBlock.LinkCard -> LinkPreviewCard(block.url)

                is MdBlock.Rule -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                is MdBlock.Gap -> Spacer(modifier = Modifier.height(4.dp))
            }
            index++
        }
    }
}

// ---------------------------------------------------------------------------
// 引用卡（「回复 #N · 作者」）
// ---------------------------------------------------------------------------

/**
 * 一条「楼层引用」拆出来的三部分：楼层号、作者、被引用的正文。
 *
 * [floor] 可以是 null：两种写法都要认 —— `> #12 张三：…` 与 `> 张三：…`。注意本仓现在的
 * `quoteComment`（`ui/screen/PostsSection.kt:1816-1822`）写进去的是**后一种**（`"> ${作者}：$snippet\n"`，
 * head 里没有楼层号），带 `#N` 的那种是历史格式/用户自己编辑过的形态；两者都拆成同样的三件东西，
 * 后者首行只写「回复 作者」。拆出来的形状与参考实现从 `aside.quote` 上取的属性一一对应
 * （`lib/widgets/markdown_editor/markdown_renderer.dart:437-491`：`data-post` 是楼层号、
 * 首个字段是显示名），那边也是"作者、楼层号、正文"三件东西分开存，而不是把首行当一整句留着。
 */
private class FloorQuote(val floor: Int?, val author: String, val body: String)

/** 作者名的字数上限，见 [parseFloorQuote] 里"为什么宁可漏认也不误认"那一段。 */
private const val QuoteHeadMaxAuthorChars = 24

/**
 * 作者名里一出现就说明"这是一整句话里有个冒号"的标点：正常昵称不会用句读标点。
 *
 * 只列句读与省略号，**不列** `-` `·` `_`：这几个在真昵称里很常见（`小明-2k`、`·阿狸·`）。
 */
private const val QuoteHeadStopChars = "。，、；！？…"

/**
 * 把 [MdBlock.Quote] 的首行识别成 [FloorQuote]；认不出返回 null，调用方保持原来的普通引用样式。
 *
 * ## 认的两种写法
 *
 * 本仓「引用回复」把这样一段塞进正文（`ui/screen/PostsSection.kt:1816-1822` 的 `quoteComment`，
 * head 是 `"> ${作者}：$snippet\n"`）：
 *
 * ```
 * > #12 张三：被引用的原文第一行
 * ```
 *
 * 所以首行可以是 `#数字 + 空白 + 名字 + 冒号`，也可以只有 `名字 + 冒号`。全角 `：` 与半角 `:`
 * 都认；名字里带空格也认（一律取到**第一个**冒号之前）。冒号之后是正文，后面几行原样接上
 * （换行保留）。
 *
 * ## 为什么宁可漏认也不误认
 *
 * 认错的代价不对称：**漏认**只是这段话还是普通引用块，读起来照样对；**误认**会把
 * `> 注意：先看第三条` 这种普通引用变成一张带头像的引用卡，作者名显示成「注意」，正文还被裁到
 * 4 行——信息直接读不到。所以下面每一条都往"更保守"的方向收，宁可少认几条：
 *
 *  * **必须有冒号**：`> #12 张三` 这种没冒号的不认。它更像正文里的一句「回复 12 楼的张三」，
 *    而不是"作者 + 正文"的分界；
 *  * **冒号前必须非空且不超过 [QuoteHeadMaxAuthorChars] 个字**：超了就是"一整句里只有一个冒号"，
 *    不是「作者：正文」。这也是需求里那个 24 字上限的用处；
 *  * **冒号前不能有句读标点**（[QuoteHeadStopChars]）：昵称里不会出现 `。，、；！？…`；
 *  * **`#` 后面必须有数字、数字后面必须是空白**：`#12张三：` 这种缺空白的直接返回 null ——
 *    按规则读不出楼层号，硬把 `#12张三` 当作者名又会在卡片上显示一串编号；
 *    而 `#` 后面没有数字（`# 标题：…`）也返回 null，那更像被人手写成引用的一个 Markdown 标题。
 *  * **首行开头的 `>` 先剥掉**：引用里套引用时 `parseBlocks` 只剥一层，剩下的 `>` 会浮到下一层
 *    的首行上；不剥的话「作者」会变成 `> 张三`，卡片上就带个箭头。
 *
 * 判定顺序是先切冒号再拆楼层号：冒号在 `#` 前面（`张三：#12 你说的对`）时，首行照样是
 * 「作者 + 正文」，正文里的 `#12` 会由 [markdownInline] 变成可点的楼层引用，这正是想要的。
 */
private fun parseFloorQuote(text: String): FloorQuote? {
    val lineEnd = text.indexOf('\n')
    // 引用里再引用：`parseBlocks` 每层只剥掉一个 `>`，剩下的会浮到内层的首行上。这里再剥一次
    // （连同空白），否则「作者」会变成 `> 张三`，卡片首行就多一个箭头。
    val firstLine = (if (lineEnd < 0) text else text.substring(0, lineEnd))
        .trim()
        .trimStart { it == '>' || it.isWhitespace() }
    val restLines = if (lineEnd < 0) "" else text.substring(lineEnd + 1)

    // 第一个冒号（全角或半角）就是"作者"和"正文"的分界。下标 0 说明冒号前什么都没有，不是引用。
    val colon = firstLine.indexOfFirst { it == '：' || it == ':' }
    if (colon <= 0) return null

    var author = firstLine.substring(0, colon).trim()
    var floor: Int? = null
    if (author.startsWith("#")) {
        var digitsEnd = 1
        while (digitsEnd < author.length && author[digitsEnd] in '0'..'9') digitsEnd++
        // `#` 后面一个数字都没有：`# 标题：…`，不是楼层引用。
        if (digitsEnd == 1) return null
        // 数字后面紧跟名字（没有空白）：读不出楼层号，也说不清作者叫什么，整块按普通引用处理。
        if (digitsEnd < author.length && !author[digitsEnd].isWhitespace()) return null
        floor = author.substring(1, digitsEnd).toIntOrNull()
        author = author.substring(digitsEnd).trim()
    }
    if (author.isEmpty() || author.length > QuoteHeadMaxAuthorChars) return null
    if (author.any { it in QuoteHeadStopChars }) return null

    // 正文 = 首行冒号之后的那截（去掉首尾空白）+ 其余行原样接上；换行保留，交给
    // [MiniMarkdownContent] 当普通 Markdown 再解析一遍。
    val firstBody = firstLine.substring(colon + 1).trim()
    val body = when {
        restLines.isEmpty() -> firstBody
        firstBody.isEmpty() -> restLines
        else -> firstBody + "\n" + restLines
    }
    return FloorQuote(floor = floor, author = author, body = body)
}

/**
 * 引用卡外壳的圆角：**左侧直角、右侧 6dp 圆角**。
 *
 * 左侧留成直角是因为那条 3dp 的主色竖条要从卡片顶端一直画到底端（[drawBehind] 里那个满高矩形）：
 * 左上/左下若是 6dp 圆角，竖条在两端会被裁掉一小块，看起来像一根断了两头的虚线，反而不像"引导线"。
 * 右侧保持 6dp 是为了和本文件里代码块（8dp）、普通引用（8dp）那一档圆角处在同一套语言里。
 */
private val QuoteCardShape = RoundedCornerShape(
    topStart = 0.dp,
    topEnd = 6.dp,
    bottomEnd = 6.dp,
    bottomStart = 0.dp,
)

/** 引用卡左侧那条主色竖条的宽度。 */
private val QuoteCardBarWidth = 3.dp

/** 引用卡正文最多露几行，见 [quoteBodyMaxHeight]。 */
private const val QuoteCardMaxLines = 4

/**
 * 允许画成引用卡的**最大嵌套层数**：第 3 层起退回普通引用样式。
 *
 * 引用里再引用再引用，每张卡都带 4 行裁剪，叠三层之后一屏里只剩三个"作者 + 半句话"，什么都读不出来。
 * 所以 [MiniMarkdownContent] 在这一支上判 `quoteDepth < QuoteCardMaxDepth`：最外层正文是 0 层
 * （里面的引用算第 1 层）、卡里的正文是 1 层（里面的引用算第 2 层），到第 3 层就画成普通引用块。
 * 普通引用块**不再往下递归**（它只是把整段文字丢给 [markdownInline]），所以递归深度到这里有硬上界，
 * 不存在"引用里套引用"把一屏撑爆或把栈压穿的路径。
 */
private const val QuoteCardMaxDepth = 2

/**
 * 引用卡正文区的限高：4 行。
 *
 * 4 行是「看得出被引用的是哪句话、又不把这一楼撑成两屏」的折中。参考实现里被引用那一段的紧凑形态
 * （`lib/widgets/post/post_item/widgets/embedded_posts.dart:133-145`）正文只有 13sp、`onSurfaceVariant`，
 * 高度靠 `_FoldableContent` 的 `foldedLines` 折；那边是有"展开"的，因为它是**引用历史里的一条帖子**。
 * 这里**刻意没有**「展开」：引用卡是给正文当背景信息的，不是入口 —— 想读全文就点首行那个 `#N`
 * 跳过去，那是这张卡上唯一该有的动作。加个"展开"等于在一条回复里再摊开一条回复，读者会分不清
 * 哪句是当前楼说的。
 *
 * 数值按 [base] 算（调用方传进来的正文样式，不是写死 `bodyMedium`）：`fontSize` / `lineHeight`
 * 读回来都是 `TextUnit`，本文件统一按 sp 与 dp 1:1 折算 —— 与 [CodeBlockCollapsedHeight] 同一个
 * 取舍（见那一节：过 `LocalDensity` + 字体缩放会让限高随系统字号漂，折叠区大小就不可预期了）。
 * `TextUnit.Unspecified` 的 `.value` 是 NaN，`NaN > 0f` 为 false，正好落到兜底值。
 */
private fun quoteBodyMaxHeight(base: TextStyle): Dp {
    val size = base.fontSize.value.takeIf { it > 0f } ?: 14f
    val line = base.lineHeight.value.takeIf { it > 0f } ?: size * 1.4f
    return (line * QuoteCardMaxLines).dp
}

/**
 * 「回复 #N · 作者」那一行（`quote.floor` 为 null 时只有「回复 作者」）。
 *
 * `#N` **必须**用 [markdownInline] 渲染，不能手拼纯文本：楼层号的可点性挂在
 * `LinkAnnotation.Clickable` 上（见 [appendMarked] 里那段），手拼字符串就只剩死字，
 * `onFloorRef` 在有这行的时候等于失效。所以这里先 append「回复」，再 append 一段
 * [markdownInline] 出来的 `#N`，最后接作者名与间隔点 —— `AnnotatedString` 的 span 会被
 * `Builder.append(AnnotatedString)` 原样搬过来。
 */
private fun quoteHeadText(
    quote: FloorQuote,
    linkColor: Color,
    onFloorRef: ((Int) -> Unit)?,
): AnnotatedString = buildAnnotatedString {
    append("回复 ")
    val floor = quote.floor
    if (floor != null) {
        append(markdownInline("#$floor", linkColor, onFloorRef))
        append(" · ")
    }
    append(quote.author)
}

/**
 * 楼层引用的卡片：`Icons.AutoMirrored.Rounded.Reply` + 20dp 头像 + 「回复 #N · 作者」一行，
 * 下面是被引用正文（最多 [QuoteCardMaxLines] 行）。
 *
 * 结构照参考实现的紧凑引用形态（`lib/widgets/post/post_item/widgets/embedded_posts.dart:100-146`：
 * 一颗 14dp 的 `reply_rounded`、20dp 头像、名字、`#N`，正文用 `onSurfaceVariant` 铺在下面，
 * 间距是 6 / 6）。这里的间距按需求收窄成 6dp（图标→头像）与 4dp（头像→文字），
 * 字号是 `labelSmall` 抬到 11sp：这一行是"这张卡属于谁"的标签，不该和正文抢注意力。
 *
 * ## 几处刻意的选择
 *
 *  * **整卡不吃点击**：引用是信息，不是入口。点整卡没有地方可去，反倒会和首行的 `#N` 抢手势
 *    （点偏一点就落进外层），而"点 `#N` 跳到那一楼"才是这张卡唯一该有的交互。所以这里没有
 *    `clickable`，那颗图标与头像也没有 `contentDescription`。
 *  * **左侧竖条用 `drawBehind` 而不是 `border`**：`border` 画的是一圈描边（四边都有，或沿 shape
 *    画整圈），想要"只有左边一条"就得靠 `BorderStroke` 去凑，圆角处还会留毛边；`drawBehind` 只画
 *    一个 3dp × 满高的矩形，位置写死在 `(0, 0)`，宽度与颜色都是主色，不受圆角影响。它挂在
 *    `background` **之后**（链上更靠内 = 更晚画），所以竖条压在底色上，不会被 6% 底盖掉。
 *  * **正文走 [MiniMarkdownContent] 递归**（不是 `markdownInline`）：被引用的原文里可能有段落、
 *    列表、代码块、表格、图片。递归进来以后它们照常按块渲染，再由 `heightIn` + `clipToBounds`
 *    统一裁到 4 行 —— 不会因为"引用里有一张图"就整块崩掉。
 *  * **[quoteDepth] 传 `+ 1`**：这一层是引用卡，卡里再出现的引用就是下一层，上限见
 *    [QuoteCardMaxDepth]。
 */
@Composable
private fun QuoteCard(
    quote: FloorQuote,
    baseStyle: TextStyle,
    onFloorRef: ((Int) -> Unit)?,
    quoteDepth: Int,
) {
    val primary = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 顺序是「裁切 → 底色 → 竖条」：`clip` 在最外层，圆角与竖条都咬在同一个轮廓里。
            .clip(QuoteCardShape)
            .background(primary.copy(alpha = 0.06f))
            .drawBehind {
                drawRect(
                    color = primary,
                    size = Size(QuoteCardBarWidth.toPx(), size.height),
                )
            }
            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Reply,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            // 与 [PostAvatar] 同包（`io.github.samolego.canta.ui.component`），不需要 import；
            // seed 用作者名，拿不到照片时就是名字首字，不会画一块灰。
            PostAvatar(name = quote.author, seed = quote.author, size = 20.dp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = quoteHeadText(quote, primary, onFloorRef),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        // 首行之外没有正文（`> #12 张三：`）时不留那 4dp 间隙，否则卡里会多出一条空带。
        if (quote.body.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // `heightIn` 只把量出来的高度夹住，超出的部分要靠 `clipToBounds` 才是真的切掉
                    // （与 `CodeBlock` 的折叠同一套做法，见 [CodeBlockCollapsedHeight] 附近）。
                    .heightIn(max = quoteBodyMaxHeight(baseStyle))
                    .clipToBounds(),
            ) {
                MiniMarkdownContent(
                    text = quote.body,
                    modifier = Modifier,
                    // 正文用**调用方传进来的**样式，不另配一套：引用块的字号应当和它所在的那段一致。
                    baseStyle = baseStyle,
                    color = bodyColor,
                    onFloorRef = onFloorRef,
                    quoteDepth = quoteDepth + 1,
                )
            }
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
 * 段落渲染：纯文字就一个 [Text]；带行内图片时按 `[文字][图片][文字…]` 依次铺。
 *
 * 为什么要拆：Markdown 里图片本来就可以写在句子中间（`看这张 ![截图](https://…) 效果`），
 * 但 [parseBlocks] 只把**整行**一个 `![](…)` 认成图片块（[imageBlockOf]），行内那种会落进段落、
 * 被当普通文字原样吐出来——`![截图](https://…)` 连括号一起显示，等于这张图在手机上永远看不见。
 * 参考实现的扩展表里行内图片是正常渲染的（`lib/widgets/markdown_editor/markdown_renderer.dart:220-223`
 * 把正文交给 `ExtensionSet.gitHubFlavored`），这里补上同一件事。
 *
 * 拆出来的图片**不参与** [PostImageGrid] 成组：成组只对"整行图片"成立（[MiniMarkdown] 里那段
 * 连续图片循环，对应参考实现的 `builders/image_grid_builder.dart`）。把句子中间的图并进方图墙
 * 等于把它从那句上下文里拽出来，读者就不知道那句话在说什么了。
 *
 * 纯文字段落**不加**额外的组合项：`pieces` 只有一个文字片时直接画一个 [Text]，与改动前的调用点
 * 完全一致（一楼几百个段落，凭空多套一层 Column 是白送的布局开销）。改动来源是需求里
 * 「没有行内图片的段落行为与现在完全一致」这一条。
 */
@Composable
private fun ParagraphContent(
    text: String,
    baseStyle: TextStyle,
    color: Color,
    linkColor: Color,
    onFloorRef: ((Int) -> Unit)? = null,
) {
    val pieces = remember(text) { splitInlineImages(text) }
    val single = pieces.singleOrNull()
    if (single is ParagraphPiece.Plain) {
        Text(
            text = markdownInline(single.text, linkColor, onFloorRef),
            style = baseStyle,
            color = color,
            softWrap = true,
        )
        return
    }
    Column {
        pieces.forEach { piece ->
            when (piece) {
                is ParagraphPiece.Plain -> Text(
                    text = markdownInline(piece.text, linkColor, onFloorRef),
                    style = baseStyle,
                    color = color,
                    softWrap = true,
                )
                // 上下留白与整行图片那条对齐（6dp）：段内的图不会把上下两行字挤在一起。
                // 同一地址重复出现时由 `PostImageCache`（`PostImage.kt:578`）兜住，只下一次网络。
                is ParagraphPiece.Picture -> PostImage(
                    url = piece.url,
                    alt = piece.alt,
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                )
            }
        }
    }
}

/** 段落里的一段：[Plain] 是普通文字，[Picture] 是一张行内图片。 */
private sealed interface ParagraphPiece {
    class Plain(val text: String) : ParagraphPiece
    class Picture(val url: String, val alt: String) : ParagraphPiece
}

/**
 * 行内图片语法：`![说明](https://…)`，说明可以为空。
 *
 * 与 [ImagePattern]（整行版）只差锚点：那边要求整行**只有**这一个语法，这边允许前后还有字，
 * 所以不加 `^…$`。地址同样只认 http(s)，别的（相对路径、`data:`）留着当文字，见 [imageBlockOf]。
 */
private val InlineImagePattern = Regex("""!\[([^\]]*)\]\((https?://[^\s)]+)\)""")

/**
 * 把一段正文按行内图片切成若干片；没有图片时返回**单独一片原文**，
 * 调用方 [ParagraphContent] 据此走"一个 Text"的快路径。
 */
private fun splitInlineImages(text: String): List<ParagraphPiece> {
    val pieces = mutableListOf<ParagraphPiece>()
    var cursor = 0
    for (hit in InlineImagePattern.findAll(text)) {
        val start = hit.range.first
        if (start > cursor) pieces += ParagraphPiece.Plain(text.substring(cursor, start))
        pieces += ParagraphPiece.Picture(
            url = hit.groupValues[2],
            alt = hit.groupValues[1].trim(),
        )
        cursor = hit.range.last + 1
    }
    if (cursor < text.length) pieces += ParagraphPiece.Plain(text.substring(cursor))
    // 整段就是一张行内图时 `pieces` 里只有图片片，这里不补空文字片：空的 Text 会多占一行行高。
    if (pieces.isEmpty()) pieces += ParagraphPiece.Plain(text)
    return pieces
}

/**
 * 代码块先露几行，超过就折起来。
 *
 * 12 行是"能看出这段在干什么、又不至于把一层楼撑成两三屏"的折中；一段两百行的日志摊开后，
 * 后面几层回复要翻好几屏才看得到。参考实现的代码外壳同样给内容区设了高度上限
 * （`lib/utils/fluxdo_render_callbacks.dart:697` 的 `maxHeight: screenHeight * 0.3`），
 * 只是那边限的是图表预览区，这里按行数限，行数比屏高更好预期。
 */
private const val CodeBlockCollapsedLines = 12

/**
 * 折起时**代码文本区**的限高：`bodySmall` 是 `fontSize 12sp` / `lineHeight 18sp`，
 * 12 行 = `18 × 12 = 216dp`（sp 与 dp 在字体缩放 1.0 时 1:1，这里就按 1:1 算）。
 *
 * 为什么不读 `MaterialTheme.typography.bodySmall.lineHeight`：拿回来的是 `TextUnit`，
 * 再折算成 dp 要过 `LocalDensity` + 字体缩放，限高值会随系统字号漂，折叠区的大小就不可预期了。
 * 布局尺寸用固定 dp；系统字号放大的用户会少看到小半行，这是有意的取舍。
 *
 * 216dp 只是文字区的上限，`10dp` 的左右/上下内边距在它外侧，所以折叠时容器比 216 稍高一点。
 */
private val CodeBlockCollapsedHeight = (18 * CodeBlockCollapsedLines).dp

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
 *
 * 超过 [CodeBlockCollapsedLines] 行时**纵向裁剪**到 [CodeBlockCollapsedHeight]，底部给一行
 * 「展开全部（共 N 行）」/「收起」。这里刻意**不加**纵向滚动：`verticalScroll` 套
 * `horizontalScroll`（或反过来）在 `LazyColumn` 里会拿到无限高约束，一量就崩；而且嵌套滚动手势
 * 在手机上本来就分不清是在滚代码还是在滚楼层。裁剪的好处是"这一楼占多少高度"是写死的，
 * 展开才把它放开。
 *
 * 代码本体走「代码块着色」一节的 [highlightCode]：注释/字符串/数字/关键字各上一种主题色，
 * 其余仍是调用方给的正文色；认不出语言、或超过 [CodeHighlightMaxChars] 就退回单色。
 * 换掉的只有 `Text` 的 `text`（`code` → `highlighted`），标题行、复制、横向滚动、折叠按钮
 * 和行数统计一个都没动，所以复制出来的仍是纯代码。
 */
@Composable
private fun CodeBlock(code: String, language: String, color: Color) {
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()
    // 着色结果按 (code, language) 缓存（见 [rememberHighlightedCode]）：滚动和展开/折叠都只是
    // 重新量同一份 AnnotatedString，不会再扫一遍代码。
    val highlighted = rememberHighlightedCode(code, language)
    // 行数 = 换行符个数 + 1：`"a\nb"` 是两行；末尾没有换行的最后一行也算一行（`"a\n"` 是两行）。
    val lineCount = remember(code) { code.count { it == '\n' } + 1 }
    val collapsible = lineCount > CodeBlockCollapsedLines
    // 键是 `code`：同一条帖子里换了代码内容就回到收起态，不把上一段代码"展开过"的状态带过来。
    var expanded by remember(code) { mutableStateOf(false) }
    val collapsed = collapsible && !expanded
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
            // 只把这一处从纯文本换成带 span 的版本：横向滚动、复制、折叠、行数统计都还吃 `code`
            // 原文，所以复制出去的永远是纯代码，不带任何着色痕迹。
            text = highlighted,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = color,
            // 代码不折行：折过之后缩进全乱，横向滚动比看不懂强。
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(scroll)
                .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp)
                .then(
                    // 只约束高度，宽度照旧由 horizontalScroll 给（无限宽）；两者是不同轴，不会互相
                    // 抢约束，也就不存在"无限约束传下去把布局量崩"的那条老问题。
                    if (collapsed) {
                        Modifier
                            .heightIn(max = CodeBlockCollapsedHeight)
                            // 超出 12 行的部分要真的切掉：`heightIn` 只是把量出来的高度夹住，
                            // 手快的排版实现仍可能画到框外，`clipToBounds()` 才是那道闸。
                            .clipToBounds()
                    } else {
                        Modifier
                    },
                ),
        )
        if (collapsible) {
            // 「叠」在容器**底部**，但用独立一行 32dp 而不是浮在代码上：浮层要么盖住第 12 行，
            // 要么得垫一层不透明底色/渐变压在代码上，而代码字是正文色，一压就读不清。
            // 居中对齐而不是右对齐：这一行是整块代码的分界线，居中读作"这块内容的开关"；
            // 右对齐会与顶栏那颗复制图标连成一条竖线，像是第二个复制按钮。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clickable(
                        onClickLabel = if (expanded) "收起代码" else "展开全部代码",
                    ) { expanded = !expanded }
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (expanded) "收起" else "展开全部（共 $lineCount 行）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 代码块着色
// ---------------------------------------------------------------------------

/**
 * 代码着色的字符上限：超过这个长度就整段单色画。
 *
 * 4000 字符（约 150 行紧凑代码）是拿"200 行日志滚动不掉帧"换来的：这里的着色是**在渲染路径上
 * 同步跑**的，没有第二个 isolate，也不能加依赖。一次扫描几千字符只要几十微秒，但一层楼里常有
 * 五六个代码块，滚动时每个都要重新量一遍文字，扫两万字符的那一个就是掉帧的那一个。
 * 参考实现给长代码的熔断是 30000 字符（`lib/services/highlighter_service.dart:181`，
 * 判断在 `:190-194` 的 `shouldSkipHighlight`），但那边把高亮丢进 isolate 异步算、用的还是完整
 * 语法包（`re_highlight`，见 `lib/utils/fluxdo_render_callbacks.dart:1561-1636`）；本仓没有那个
 * 线程池也没有那个包，阈值只能自己压到"手机上滚起来肯定不卡"的量级。
 *
 * 跳过着色时返回的是不带任何 span 的 `AnnotatedString(code)`，与改动前的 `text = code`
 * 是同一份排版：这段分支与现状逐像素一致。
 */
private const val CodeHighlightMaxChars = 4000

/**
 * 代码块用到的四档颜色（第五类"其余"直接用调用方传进来的正文色，不单独开 span）。
 *
 * 全部由 [rememberHighlightedCode] 从 `MaterialTheme.colorScheme` 现取，**这里不写任何 hex**：
 * 应用除了默认的 [io.github.samolego.canta.ui.fl.FlPalette] 还有按色相生成的主题
 * （`FlAccent.kt:90` / `:104` 的 secondary），写死颜色在那些主题的浅色底上就会读不清。
 *
 * - [comment] `onSurfaceVariant` 55%：注释是"可以跳过"的内容，对着 12sp 等宽字再压一档；
 *   取 onSurfaceVariant 而不是 onSurface 是因为它本来就比正文淡一层，深浅主题都成立。
 * - [string] `primary` 90%：主色是应用里最稳的可读强调色（浅色 `#007AFF`、深色 `#2F83FF`，
 *   见 `FlPalette.kt:22` / `:60`），压在 90% 又不会比正文更跳。**不使用绿色**：绿色在浅色
 *   `surfaceVariant`（`#E4E2E4`）上的对比只有 2 出头，而代码块里占字符数最多的恰恰是字符串。
 * - [number] `tertiary`：第三种强调色，色相和 primary/secondary 都拉开了；应用里本就拿它标
 *   "另一个维度"的信息（`RecordsScreen.kt:282`、`AppBadge.kt:55`），数字/常量正好是这一类。
 * - [keyword] `secondary`：浅色 `#405E96` 压在 `surfaceVariant #E4E2E4` 上约 5.1:1、
 *   深色 `#ADC6FF` 压在深色 surfaceVariant 上约 5.5:1（`FlPalette.kt:27` / `:65`），
 *   都过 4.5:1，所以**不需要**退回 `primary`。
 */
private data class CodePalette(
    val comment: Color,
    val string: Color,
    val number: Color,
    val keyword: Color,
)

/**
 * 一种语言的"词法外形"：只记注释怎么写、关键字是哪张表，**不做任何语法解析**。
 *
 * 为什么只有五类（注释 / 字符串 / 数字 / 关键字 / 其余）：代码块在帖子里的作用是"看个大概"，
 * 不是编译器的语法树。完整语法包要给每门语言几 MB 的语法定义、还要一个 isolate 去跑
 * （参考实现就是这么做的：`lib/utils/fluxdo_render_callbacks.dart:1561-1636` 的异步高亮 widget
 * 委托给 `HighlighterService`），本仓不能加依赖；而且真按 token 类型铺开十几档颜色，在 12sp
 * 等宽字、半屏宽的代码块里也没人分得清 `Type` 和 `Function`。五类刚好覆盖"读代码时眼睛要找的
 * 东西"：哪段被注释掉了、字符串边界在哪、哪里是关键字——剩下的交给正文色。
 *
 * 关键字表每门语言**只收 20 个以内**，够读就行：这不是补全库，收多了反而会让普通标识符满屏变色。
 */
private enum class CodeSyntax(
    /** 行注释的起始记号；`null` = 这门语言在这里不做行注释。 */
    val lineComment: String?,
    /** 块注释的起始记号；`null` = 不支持。HTML 的 `<!-- -->` 也走这一对。 */
    val blockCommentStart: String?,
    val blockCommentEnd: String?,
    /** 关键字表（不含大小写变体，SQL 靠 [keywordIgnoresCase] 放宽）。 */
    val keywords: Set<String>,
    /** SQL 的关键字大小写不敏感：`select` / `SELECT` 都算。 */
    val keywordIgnoresCase: Boolean = false,
) {
    KOTLIN(
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        keywords = setOf(
            "fun", "val", "var", "class", "object", "interface", "data", "when", "if", "else",
            "for", "while", "return", "null", "true", "false", "import", "package", "is", "override",
        ),
    ),
    JAVA(
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        keywords = setOf(
            "public", "private", "protected", "static", "final", "class", "interface", "void",
            "new", "return", "if", "else", "for", "while", "null", "true", "false", "import",
            "package", "try",
        ),
    ),
    DART(
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        keywords = setOf(
            "class", "extends", "implements", "final", "var", "void", "async", "await", "Future",
            "String", "int", "bool", "double", "null", "true", "false", "return", "if", "else",
            "import",
        ),
    ),
    JAVASCRIPT(
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        keywords = setOf(
            "function", "const", "let", "var", "return", "if", "else", "for", "new", "class",
            "extends", "async", "await", "export", "import", "default", "null", "undefined",
            "true", "false",
        ),
    ),
    PYTHON(
        // 三引号字符串在这里会被切成几段相邻的字符串色，视觉上仍是整段字符串色，够用。
        lineComment = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
        keywords = setOf(
            "def", "class", "import", "from", "as", "if", "elif", "else", "for", "while",
            "return", "None", "True", "False", "in", "not", "and", "or", "lambda", "with",
        ),
    ),
    SQL(
        // `#` 在 MySQL 里也是注释，但 `--` 三端通用，只收这一种。
        lineComment = "--",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        keywords = setOf(
            "select", "from", "where", "join", "left", "right", "inner", "outer", "group", "by",
            "order", "having", "insert", "update", "delete", "values", "create", "table", "and",
            "or",
        ),
        keywordIgnoresCase = true,
    ),
    JSON(
        // 严格 JSON 没有注释，但 `//` 是 jsonc（VS Code 的 settings.json 之类）的日常写法，
        // 认它比不认划算：字符串里的 `//` 会被字符串分支先吃掉（`https://…` 不受影响）。
        lineComment = "//",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
        keywords = setOf("true", "false", "null"),
    ),
    SHELL(
        lineComment = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
        keywords = setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
            "function", "export", "local", "return", "echo", "cd", "set", "unset", "source",
        ),
    ),
    XML(
        lineComment = null,
        blockCommentStart = "<!--",
        blockCommentEnd = "-->",
        // 标记语言没有关键字：`<div>` 是标签名，按正文色就够了，硬塞一张表只会满屏变色。
        keywords = emptySet(),
    ),
}

/**
 * `language` → 语法表；认不出返回 `null`（调用方按单色画）。
 *
 * 按**小写前缀**匹配：围栏的 info string 是用户随手写的，`kt` / `kotlin`、`js` / `javascript` /
 * `ts`、`sh` / `bash` / `shell`、`py` / `python` 都得认。两条**顺序**上的坑：`java` 必须排在
 * `javascript` 后面判断（否则 `javascript` 会被 `java` 抢走）；`json` 必须排在 `js` 前面
 * （`"json".startsWith("js")` 是 true，反过来写 JSON 会被当成 JavaScript）。
 */
private fun codeSyntaxOf(language: String): CodeSyntax? {
    val lang = language.trim().lowercase()
    if (lang.isEmpty()) return null
    return when {
        lang.startsWith("json") -> CodeSyntax.JSON
        lang.startsWith("kotlin") || lang.startsWith("kt") -> CodeSyntax.KOTLIN
        lang.startsWith("javascript") || lang.startsWith("typescript") ||
            lang.startsWith("js") || lang.startsWith("ts") -> CodeSyntax.JAVASCRIPT
        lang.startsWith("java") -> CodeSyntax.JAVA
        lang.startsWith("dart") -> CodeSyntax.DART
        lang.startsWith("python") || lang.startsWith("py") -> CodeSyntax.PYTHON
        lang.startsWith("sql") -> CodeSyntax.SQL
        lang.startsWith("bash") || lang.startsWith("shell") ||
            lang.startsWith("sh") -> CodeSyntax.SHELL
        lang.startsWith("xml") || lang.startsWith("html") -> CodeSyntax.XML
        else -> null
    }
}

/**
 * `[start, end)` 是不是这张关键字表里的词。
 *
 * 逐字符比而不是 `substring` 后查表：一段代码里标识符有几百个，每个都切出一个 String 是白扔的
 * 分配；表最多 20 条，先比长度就能筛掉绝大多数，剩下的也只比到第一个不同的字符。
 */
private fun CodeSyntax.isKeywordAt(code: String, start: Int, end: Int): Boolean {
    val len = end - start
    for (word in keywords) {
        if (word.length != len) continue
        var k = 0
        while (k < len) {
            val a = code[start + k]
            val b = word[k]
            val same = if (keywordIgnoresCase) a.equals(b, ignoreCase = true) else a == b
            if (!same) break
            k++
        }
        if (k == len) return true
    }
    return false
}

/**
 * 数字字面量里还能接哪些字符：十进制/十六进制/二进制里的数字与字母，加浮点、整型后缀和位数
 * 分隔符（`1_000`）。
 *
 * 只放这些是**故意**的：把英文字母全放进去，`1.toString`、`3.foo` 这种会把点后面一整段吞成
 * 数字色。`.` 后面必须还是数字，也是同一个理由。
 */
private fun isNumberBody(ch: Char): Boolean =
    ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F' ||
        ch == 'x' || ch == 'X' || ch == 'o' || ch == 'O' ||
        ch == 'l' || ch == 'L' || ch == 'u' || ch == 'U' || ch == '_'

/**
 * 把 [code] 切成五类，产出一份带 span 的 [AnnotatedString]。
 *
 * 实现是**单遍**扫描：从 0 走到 `n`，每认出一种记号就往后吃到它的结尾、写一段 [SpanStyle]，
 * 然后从结尾继续。三条"不会卡死/不会溢出"的保证：
 * 1. 字符串最多吃到**行尾**：没关的引号不会把后面几百行正文全染成字符串色，下标也不会停在原地；
 * 2. 块注释没闭合时吃到文本结尾；
 * 3. 每个分支都让 `i` 严格增大（要么跳到记号结尾，要么 `i++`），不存在原地打转的路径。
 *
 * 性能：整段只 `append(code)` 一次，着色靠 `addStyle` 标区间，**不切 substring 拼文本**，
 * 所以内存是"一份代码 + 几十个 span"，复杂度 O(n)。认不出的语言、或超过
 * [CodeHighlightMaxChars] 的代码，直接返回不带 span 的 `AnnotatedString`（与纯文本渲染一致）。
 */
private fun highlightCode(code: String, language: String, palette: CodePalette): AnnotatedString {
    val syntax = codeSyntaxOf(language) ?: return AnnotatedString(code)
    if (code.length > CodeHighlightMaxChars) return AnnotatedString(code)
    val n = code.length
    return buildAnnotatedString {
        // 整段只 append 一次：下面是往这份文本上标区间，不是一段段拼字符串。
        append(code)
        var i = 0
        while (i < n) {
            val ch = code[i]

            // 1) 行注释：吃到换行前一个字符。
            val line = syntax.lineComment
            if (line != null && code.startsWith(line, i)) {
                var end = i + line.length
                while (end < n && code[end] != '\n') end++
                addStyle(SpanStyle(color = palette.comment), i, end)
                i = end
                continue
            }

            // 2) 块注释（HTML 的 `<!-- -->` 也走这里）：没闭合就吃到文本结尾。
            val open = syntax.blockCommentStart
            val close = syntax.blockCommentEnd
            if (open != null && close != null && code.startsWith(open, i)) {
                var end = i + open.length
                while (end < n && !code.startsWith(close, end)) end++
                if (end < n) end += close.length
                addStyle(SpanStyle(color = palette.comment), i, end)
                i = end
                continue
            }

            // 3) 字符串：三种引号一视同仁（kotlin/js/dart 的模板串、shell 的反引号都长这样）。
            //    `\` 吃掉下一个字符，所以 `\"` 不算收尾；没闭合就到行尾收手，绝不跨行。
            if (ch == '"' || ch == '\'' || ch == '`') {
                var end = i + 1
                while (end < n) {
                    val c = code[end]
                    if (c == '\\') {
                        end += 2
                        continue
                    }
                    if (c == ch) {
                        end++
                        break
                    }
                    if (c == '\n') break
                    end++
                }
                if (end > n) end = n
                addStyle(SpanStyle(color = palette.string), i, end)
                i = end
                continue
            }

            // 4) 数字：从数字起头，吃掉 `0x` / 指数 / 类型后缀 / 小数部分。
            if (ch in '0'..'9') {
                var end = i + 1
                while (end < n) {
                    val c = code[end]
                    val exponentSign = (c == '+' || c == '-') &&
                        (code[end - 1] == 'e' || code[end - 1] == 'E')
                    if (exponentSign) {
                        end++
                    } else if (c == '.') {
                        if (end + 1 < n && code[end + 1].isDigit()) end++ else break
                    } else if (isNumberBody(c)) {
                        end++
                    } else {
                        break
                    }
                }
                addStyle(SpanStyle(color = palette.number), i, end)
                i = end
                continue
            }

            // 5) 标识符：整段吃下去再查表，命中才上色，没命中当正文。整段吃还有一个好处：
            //    `x1` 里的 `1` 不会被当成数字重新起头。
            if (ch.isLetter() || ch == '_') {
                var end = i + 1
                while (end < n && (code[end].isLetterOrDigit() || code[end] == '_')) end++
                if (syntax.isKeywordAt(code, i, end)) {
                    addStyle(SpanStyle(color = palette.keyword), i, end)
                }
                i = end
                continue
            }

            // 6) 其余（空白、括号、运算符）：不开 span，交给 Text 的 `color`。
            i++
        }
    }
}

/**
 * 按当前主题算出四档颜色并缓存着色结果。
 *
 * `remember` 的键是 `code` + `language`（再加一份 [CodePalette]）：前两个保证"同一段代码只扫
 * 一遍"——滚动、展开/折叠、父层重组都命中缓存，不会重算；palette 是 data class，颜色没变时
 * `equals` 相等，同样不击穿缓存，只有换主题才会重算一次。**不能只用 `(code, language)`**：
 * `remember` 看不见主题，单靠它的话切深浅色后高亮会停在旧配色上。
 */
@Composable
private fun rememberHighlightedCode(code: String, language: String): AnnotatedString {
    val scheme = MaterialTheme.colorScheme
    val palette = CodePalette(
        comment = scheme.onSurfaceVariant.copy(alpha = 0.55f),
        string = scheme.primary.copy(alpha = 0.9f),
        number = scheme.tertiary,
        keyword = scheme.secondary,
    )
    return remember(code, language, palette) { highlightCode(code, language, palette) }
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
 * 表头底下那条分隔线的粗细：**2dp**，比数据行之间的 1dp 粗一倍。
 *
 * 出处是参考实现的 `thead th { border-bottom: 2px solid var(--primary-low) }`
 * （`lib/utils/export_utils.dart:646-652`），数据行那条 1dp 在同一文件的 `tbody td`
 * （`:654-657`）、最后一行不画（`:659-661`）。分成两档粗细是有意义的：手机屏上表格常常
 * 横向滚动，表头那一段是"列名"，粗一点的线才能把它和第一行数据分开；数据行之间只需要一条
 * 弱线提示"换行了"。
 *
 * 颜色用**不透明的** `outlineVariant`（下面数据行那条仍是 `outlineVariant 40%`）：
 * 2dp 已经给足了视觉重量，再用半透明反而两头都不像。
 */
private val TableHeaderDividerThickness = 2.dp

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
 *  * **表头底下一条 [TableHeaderDividerThickness] 的分隔线**，数据行之间一条 1dp，宽度取各列之和
 *    （`horizontalScroll` 里宽度无界，`HorizontalDivider` 默认的 `fillMaxWidth()` 在这种约束下
 *    量成 0，必须显式给宽）。最后一行的下面不画线（`export_utils.dart:659-661`），所以数据行那条
 *    只在 `index > 0` 时画——它是"行与行之间"的线，不是"行与行下边"的线。
 *  * **[alignments] 决定每格 `textAlign`**（GFM 分隔行里的冒号，见 [TableAlign]）：列宽是量出来的
 *    固定值，对齐只影响文字在格子里靠哪边，不会把列宽带偏。
 */
@Composable
private fun MdTable(
    header: List<String>,
    rows: List<List<String>>,
    alignments: List<TableAlign>,
    baseStyle: TextStyle,
    color: Color,
    linkColor: Color,
    onFloorRef: ((Int) -> Unit)? = null,
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
            TableRow(
                cells = header,
                widths = columnWidths,
                alignments = alignments,
                style = headerStyle,
                color = color,
                linkColor = linkColor,
                header = true,
                onFloorRef = onFloorRef,
            )
            // 表头底下那条 2dp：**永远画**（参考实现的 `thead th` 也不看 tbody 有没有行，
            // 见 `export_utils.dart:646-652`）。只有表头、没有数据行的表极少见，但真出现时
            // 这条线正好说明"下面没东西了"。
            HorizontalDivider(
                modifier = Modifier.width(tableWidth),
                thickness = TableHeaderDividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.width(tableWidth),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
                TableRow(
                    cells = row,
                    widths = columnWidths,
                    alignments = alignments,
                    style = baseStyle,
                    color = color,
                    linkColor = linkColor,
                    header = false,
                    onFloorRef = onFloorRef,
                )
            }
        }
    }
}

/**
 * 表格的一行。单元格正文用调用方给的 [style]（表头是粗体版），行内标记照旧走 [markdownInline]，
 * 所以 `**加粗**`、`` `代码` ``、链接在格子里都还能用（[onFloorRef] 非空时 `#12` 在格子里也可点）。
 *
 * 宽度用 [MdTable] 量好的固定值而不是 `weight`：固定值才能保证同一列上下对齐。
 * 高度不同的行靠 `CenterVertically` 对齐（折行的长句子把行撑高时，短的那格居中不贴顶）。
 *
 * [alignments] 只喂给 `Text` 的 `textAlign`：格子宽度是量死的，对齐只挪文字、不挪边界，
 * 所以同一列上下**边界仍然对齐**，只是文字靠左边/中间/右边——这正是 GFM 分隔行
 * `:---` / `:---:` / `---:` 要表达的东西。按下标取不到时退回 `Start`（列数补齐后本该取得到，
 * 这只是不给"下标越界"留口子）。
 */
@Composable
private fun TableRow(
    cells: List<String>,
    widths: List<Dp>,
    alignments: List<TableAlign>,
    style: TextStyle,
    color: Color,
    linkColor: Color,
    header: Boolean,
    onFloorRef: ((Int) -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { column, text ->
            Text(
                text = markdownInline(text, linkColor, onFloorRef),
                style = style,
                color = color,
                textAlign = (alignments.getOrNull(column) ?: TableAlign.Start).toTextAlign(),
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

/**
 * 三种对齐落到 Compose 的写法。
 *
 * 用 `TextAlign.Start` / `End` 而不是 `Left` / `Right`：GFM 的 `:---` 语义就是"起始边"，
 * 让框架自己按书写方向决定是左还是右；这个应用目前只有 LTR，两种写法画出来一样，
 * 但 `Start` 不会在以后接入 RTL 时变成一个反着的表格。输入方向固定的表（比如纯数字列）
 * 本来也没有 `#` 之外的右对齐需求，所以不给 `TextAlign.Right` 单独开一档。
 */
private fun TableAlign.toTextAlign(): TextAlign = when (this) {
    TableAlign.Start -> TextAlign.Start
    TableAlign.Center -> TextAlign.Center
    TableAlign.End -> TextAlign.End
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
// 链接卡片（整行一个普通地址）
// ---------------------------------------------------------------------------

/** 卡片上那个方角标里放域名首字母，取不到（空域名）时的兜底字。 */
private const val LinkCardFallbackMark = "链"

/**
 * 整行一个 http(s) 地址时画这张卡：左边一个 36dp 的方角标（域名首字母），右边两行
 * （域名 / 路径 + 查询），最右一颗外链图标，**整卡可点**。
 *
 * ## 为什么整卡可点、而不是把地址做成可点文字
 *
 * 一行只有一个地址时，"可点"的范围本该是整张卡，而不是那串又长又难看清的 URL 字符。
 * 但链接注解是挂在**文字**上的，卡片没有文字可点（`Modifier` 区域不在注解的命中范围里），
 * 所以用 `Modifier.clickable` + `LocalUriHandler.openUri`，与同文件 [VideoCard] 完全同一写法
 * （参考实现那边整块也是 `await launchContentLink(context, url)`，
 * 见 `lib/widgets/content/discourse_html_content/builders/onebox/default_onebox_builder.dart:48-49`，
 * 视频卡对应 `.../onebox/video_onebox_builder.dart:559`）。
 *
 * ## 为什么不用 favicon
 *
 * 参考实现的 onebox 卡左侧图标是服务端抓下来的 `img.site-icon`
 * （`default_onebox_builder.dart:35-36`：`element.querySelector('img.site-icon')` 取 `src`），
 * 那是"发帖时后端已经把站点元数据抓好了"的产物。本仓是纯客户端渲染，正文里只有一个 URL：
 * 要拿 favicon 就得先 `GET https://域名/favicon.ico` 再解码，等于**看一条帖子就向发帖人指定的任意
 * 主机发一次请求**（内网地址、埋点地址一样会中），而且离线/被墙时卡片会空一格。所以这里只画
 * 域名首字母——本地算得出来、不联网、不会因为对方站点挂了而变化。图标位、域名行、说明行这套骨架
 * 照 `default_onebox_builder.dart:46-77` 的 `OneboxContainer(sourceName, title, description)`。
 *
 * ## 尺寸
 *
 * 圆角 8dp 与 [VideoCard] 及其它卡片统一（参考实现是 `context.shapes.borderRadius(8)`，
 * `video_onebox_builder.dart:111`）；`heightIn(min = 56.dp)` 保证两行文字的卡在窄屏上也不会被
 * 挤成一条，但内容多时**自己撑高**，不写死高度。
 */
@Composable
private fun LinkPreviewCard(url: String) {
    val uriHandler = LocalUriHandler.current
    val host = remember(url) { linkCardHost(url) }
    val path = remember(url) { linkCardPath(url) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { uriHandler.openUri(url) }
            // 先限最小高度再进内边距：这样"至少 56dp"说的是整张卡（含 10dp 内边距），
            // 而不是内边距里那块内容区。
            .heightIn(min = 56.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                // primaryContainer 再压到 50%：深浅主题下都是"主色的一层薄底"，
                // 不至于比正文还抢眼；里面那一个字用 primary，对比度够。
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = host.take(1).uppercase().ifEmpty { LinkCardFallbackMark },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 8.dp),
        ) {
            Text(
                // 域名按需求**不截断**：它本来就短（28 字符已是很长的子域），
                // 截断反而看不出是哪个站。真超长时 maxLines=1 + 省略号兜底，不至于撑破卡片。
                text = host,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // 路径整条常常很长（`/t/topic/12345/23`），这一行才是靠省略号收的地方。
                text = path.ifEmpty { "打开链接" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            // 选 OpenInNew 而不是 ChevronRight：`openUri` 是**交给系统浏览器**打开（出应用），
            // 而 `>` 在应用里通常表示"进下一层页面"，那是本 App 内的导航语义，会误导。
            // 这也与 [VideoCard] 上那句「点开用浏览器播放」一致。
            imageVector = Icons.Rounded.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
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
     * 所以渲染那边可以直接按下标取列宽。[alignments] 与它们等长，一格一个对齐。
     */
    class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val alignments: List<TableAlign>,
    ) : MdBlock
    /** 一行独占一张图：`![说明](http…)`。 */
    class Image(val url: String, val alt: String) : MdBlock
    /** 一行只有一个视频地址（`.mp4`/`.webm`/`.mov`），画成可点的卡片。 */
    class Video(val url: String) : MdBlock
    /**
     * 一行只有**一个** http(s) 地址（同一行除了它只剩空白），画成链接卡。
     *
     * 与 [Video] 分开而不是塞一个 `kind` 字段：两者的渲染完全不同（视频要 16:9 的框，
     * 链接卡只有一行文字），而且"哪一种先判"是显示优先级的一部分，见 [linkCardOf]。
     */
    class LinkCard(val url: String) : MdBlock
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
        // 整行一个普通 http(s) 地址：画链接卡，不再走下面「裸地址 → 挡在段落里的可点文字」那条。
        // **必须放在视频之后**：`https://…/clip.mp4` 同时满足两边的条件，视频优先（见 [linkCardOf]）。
        // 段落中间的地址（`看这个 https://x.y`）到不了这里——这行里有别的字，[linkCardOf] 直接返回 null。
        val linkCard = linkCardOf(trimmed)
        if (linkCard != null) {
            blocks += MdBlock.LinkCard(linkCard)
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
        videoLinkOf(trimmed) != null ||
        // 整行链接也要能自己起一块，否则它会被上面的段落循环当成"上一段的续行"吞掉，
        // 于是"整行一个地址"变成段落里的一句可点文字，链接卡永远出不来。
        linkCardOf(trimmed) != null

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
 * 一列的对齐方式，落在 [MdBlock.Table.alignments] 里。
 *
 * **为什么只有这三种（而需求说的是"左/中/右/默认"四种）**：GFM 的分隔行只有四种写法，
 * 但 `---`（两边都没冒号）与 `:---`（左边一个冒号）**渲染结果完全一样**，都是起始边对齐——
 * 分隔行里的冒号表达的是"往哪边靠"，没写就是想不起来往哪靠，那当然就是最常见的左。
 * 所以按"渲染结果"归类只有 Start / Center / End 三档；需求里的"默认"是**解析层**的概念
 * （[tableAlignments] 里没冒号就落到 [Start]），不是渲染层的第四种表现。多开一个
 * `Unspecified` 枚举值只会在 [TableAlign.toTextAlign] 里多一条和 `Start` 一模一样的
 * `when` 分支，然后永远测不出差别。
 *
 * 参考实现的 web 表格干脆不看对齐：`thead th { text-align: left }`
 * （`lib/utils/export_utils.dart:646-652`）是写死的，`:---:` 在那边也还是左对齐。这里
 * 比它多做一步，纯粹因为 GFM 写了这条语法、而写表的人用 `:---:` 时**确实**想让那一列居中。
 */
private enum class TableAlign { Start, Center, End }

/**
 * 从 [index] 起试着认一张 GFM 表格，认出来就返回 `表 to 下一行的下标`。
 *
 * 判定三件套（缺一不认，宁可退成段落）：
 *  1. 表头行和分隔行**都**得含 `|`。少了这条，`---` 那种水平线（[isRule] 已经先吃掉了）
 *     和「一列的表」会互相打架；
 *  2. 分隔行每一格都是 `---` / `:--` / `--:` / `:-:`（[isTableDivider]）；
 *  3. 表头格数 == 分隔行格数。这是 GFM 的硬规矩，也是需求里「列数对不上就原样当段落」那一条。
 *
 * 分隔行不只是"是不是表格"的判据，它同时是**对齐声明**：每格的冒号位置由 [tableAlignments]
 * 翻成 [TableAlign]，随表格一起交给渲染层（`Text` 的 `textAlign`）。
 *
 * 数据行一直吃到空行、不含 `|` 的行、另一条分隔行或别的块开头为止；**每行列数补齐到最宽的那行**
 * （多出来的格保留、缺的补空串），这样渲染那边 `header.indices` 就能直接按下标取列宽，
 * 也不会因为某行多打了一个 `|` 就把正文丢掉。对齐列表**用同一条规则补到一样长**：
 * 分隔行的格数只等于表头，数据行多出来的列没人声明过对齐，一律按 [TableAlign.Start] 补
 * ——于是 `alignments[i]`、`header[i]`、每一行的 `row[i]` 三者下标永远对得上，
 * `TableRow` 里不会出现取不到对齐的格子。参考实现走 `ExtensionSet.gitHubFlavored`
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
    val declared = tableAlignments(dividerLine)
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
    // 对齐跟着列数一起补：多出来的列是数据行自己多打的 `|`，分隔行没声明过，按默认（左）处理。
    val alignments =
        if (declared.size >= columns) declared else declared + List(columns - declared.size) { TableAlign.Start }
    return MdBlock.Table(pad(header), body.map { row -> pad(row) }, alignments) to i
}

/**
 * 把分隔行翻成每列的对齐：两头都有冒号 = 居中，只有尾冒号 = 靠右，其余（只有头冒号 / 没冒号）= 靠左。
 *
 * 判据只看冒号在不在，**不看横线的长度**：GFM 里 `:-:` 和 `:-----:` 是同一件事，
 * 横线多长只是写表的人手抖，没有任何语义。
 *
 * 只在 [isTableDivider] 已经点头之后才调用，所以这里不需要再校验一遍"是不是合法的分隔格"；
 * `:` 之外的字符在这里不会被看到。
 */
private fun tableAlignments(dividerLine: String): List<TableAlign> =
    splitTableCells(dividerLine).map { cell ->
        val trimmed = cell.trim()
        when {
            trimmed.startsWith(":") && trimmed.endsWith(":") -> TableAlign.Center
            trimmed.endsWith(":") -> TableAlign.End
            else -> TableAlign.Start
        }
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
// 整行链接
// ---------------------------------------------------------------------------

/**
 * 地址长度上限：超过就当普通段落，**不画卡**。
 *
 * 防的是"有人把一整篇 base64 / data 塞进一行地址"这种情形：那种行动辄几十 KB，画成卡只会让
 * 卡片算宽度、算省略号白烧一轮布局，而它本来也不长成一个能被点开的链接。2000 字符足够覆盖
 * 正常的长链接（带一长串 `?utm_…` 的那种）。
 */
private const val LinkCardMaxUrlChars = 2000

/**
 * 整行**只有一个** http(s) 地址时返回它（原样，含查询串与 `#fragment`），否则 null。
 *
 * ## 为什么只有"整行"才算卡
 *
 * 卡是块级元素（另一行、另一块底色、整块可点）。正文里顺手提一句「见 https://a/b」时，那句话
 * 是句子的一部分，把它抽出来变成一张卡会把一句人话拆成两半，而且卡片会占满整行宽度，
 * 语境直接断开。所以判定就是"这一行除了地址只有空白"：去掉首尾空白之后，地址**中间**只要还有
 * 一个空白就放行，让那行继续走段落的裸地址链接（[bareUrlEnd]）。`[文字](地址)` 同理——它不以
 * `http` 开头，落的还是行内链接那一支，一个字都不变。
 *
 * ## 视频优先
 *
 * `.mp4`/`.webm`/`.mov` 同时也满足"整行一个地址"，但视频该画成 16:9 的播放卡（[VideoCard]），
 * 不是一行域名加路径。所以这里**主动**把视频地址排除掉，而不是只靠 [parseBlocks] 里"先判视频"的
 * 顺序：让这个纯函数自己保证"返回非空的都是链接卡"，两处顺序谁先写错都不会退回一个错误的表现，
 * 自查看函数就够了。
 *
 * ## 只认 http(s)
 *
 * 别的写法（相对路径、`mailto:`、`data:`、`javascript:`）既不该画成"外链卡"，也不该被
 * `openUri` 直接丢给浏览器。方案名只认小写的 `http://` / `https://`（`HTTP://` 不认），
 * 与 [videoLinkOf] 同一口径——正文里贴出来的地址本来就都是小写。
 *
 * 把域名单独算一遍（[linkCardHost]）是为了顺带排除 `https://` 后面什么都没有的畸形串。
 */
private fun linkCardOf(trimmed: String): String? {
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
    // 中间有空白就不是"整行一个地址"（`https://a https://b`、`看这个 https://x.y` 全挡在这里）。
    if (trimmed.any { it.isWhitespace() }) return null
    if (trimmed.length > LinkCardMaxUrlChars) return null
    // 视频优先：见上面「视频优先」那段。
    if (videoLinkOf(trimmed) != null) return null
    // 连域名都没有（`https://`、`https:///path`）就不算一个能开的链接。
    if (linkCardHost(trimmed).isEmpty()) return null
    return trimmed
}

/**
 * 链接卡第一行显示的域名：去掉 `://` 之前的部分与第一个 `/` 之后的部分，再剥掉 `www.`。
 *
 * 端口保留（`example.com:8080`）：它属于"这是哪个站"的一部分，去掉反而认不出本地/测试站点。
 * 去掉 `www.` 只图好看——同一站点在卡上一律显示成同一个名字。
 */
private fun linkCardHost(url: String): String =
    url.substringAfter("://", "").substringBefore('/').removePrefix("www.")

/**
 * 链接卡第二行显示的路径：去掉协议与域名后剩下的部分，前面补回 `/`；根路径返回空串
 * （渲染那边空串会写「打开链接」）。
 *
 * 查询串（`?…`）与 `#fragment` **留着**：它们常常正是这条链接的重点（`/s?q=关键词`、
 * `/p/123#post_456`），砍掉就成了一条看不出内容的路径。只有单独一个尾部 `/`
 * （`https://x.y/`）算"没有路径"，否则卡片第二行会显示成一个孤零零的斜杠。
 */
private fun linkCardPath(url: String): String {
    val rest = url.substringAfter("://", "")
    if ('/' !in rest) return ""
    val tail = rest.substringAfter('/')
    return if (tail.isEmpty()) "" else "/$tail"
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
 * `@提及` 允许的字符：ASCII 字母数字、`_`、`-`，以及中文汉字（`\u4e00`..`\u9fa5`，覆盖常用汉字区）。
 *
 * 不认全角 `＠`：Discourse 那条来源的提及是半角敲出来的，全角那个更可能是排版时的随手输入。
 */
private fun isMentionChar(ch: Char): Boolean =
    ch in 'A'..'Z' ||
        ch in 'a'..'z' ||
        ch in '0'..'9' ||
        ch == '_' ||
        ch == '-' ||
        ch in '\u4e00'..'\u9fa5'

/** 名字最短/最长：一个字符就当提及太容易误伤（正文里手打一个 `@` 太常见），超过 20 也不是用户名。 */
private const val MentionMinLength = 2
private const val MentionMaxLength = 20

/**
 * `@` 前面出现这些字符时，说明它多半在一段地址里，不是在跟人说话：`/` `:` `?` `#` `&` `=` 都是
 * URL 的结构符。
 *
 * 刻意**不**把这几个字符放进来：`_` `-` `+` `%` `.`。`_@张三_` 这种斜体写法里 `@` 前面就是 `_`，
 * 挡掉它等于把提及吞了；而邮箱那侧由"前面不能是 ASCII 字母数字"那条管住（`a.b@c.com` 的 `@`
 * 前面是字母，已经挡住了），不需要再加一层。
 */
private const val MentionBlockingPrefix = "/:?#=&"

/**
 * [start] 处是不是一个 `@名字`；是就返回它结束的位置（`@` 之后那串名字的末尾）。
 *
 * 规则来自需求：半角 `@` + 紧跟的 2–20 个 [isMentionChar] 字符。三道闸：
 *
 *  1. **`@` 前一个字符不能是 ASCII 字母数字**——这就是"不把邮箱当提及"的判据。
 *     刻意只算 ASCII：中文里 `谢谢@张三` 是词与词直接相接，把汉字也当成"紧贴邮箱"的证据会把
 *     最常见的写法挡掉（`Char.isLetterOrDigit()` 对汉字返回 true，所以这里不能图省事用它）。
 *  2. **`@` 前一个字符不能是地址结构符**（[MentionBlockingPrefix]）。裸地址在 [appendMarked] 里
 *     是整条先吃掉的，`https://x/@y` 轮不到这里；留着这道闸是为了兜住"前半截已经被当文字输出"的
 *     情况，别让地址尾巴被高亮成半截蓝色。
 *  3. **长度 2–20**，短了不认、长了截断到 20（后面的字符还给正文，不吞字）。
 *
 * 参考实现的提及正则（`lib/models/mention_user.dart:7`）走的是"前面必须是行首或空白"那条路，
 * 这里放宽成"不是字母数字"：中文里 `谢谢@张三` 前面没有空格，按空白判会漏掉一半的提及。
 */
private fun mentionEndAt(text: String, start: Int): Int? {
    if (start >= text.length || text[start] != '@') return null
    if (start > 0) {
        val prev = text[start - 1]
        if (prev in 'A'..'Z' || prev in 'a'..'z' || prev in '0'..'9') return null
        if (prev in MentionBlockingPrefix) return null
    }
    var end = start + 1
    while (end < text.length && end - start - 1 < MentionMaxLength && isMentionChar(text[end])) end++
    if (end - start - 1 < MentionMinLength) return null
    return end
}

/**
 * 在 [start] 处试每种标记，命中就返回。返回 null 表示这个字符按字面输出。
 *
 * 长的先试：`**` 在 `*` 前面，否则粗体会被当成两个斜体。
 */
private fun matchInlineAt(text: String, start: Int, linkColor: Color): InlineMatch? {
    // `@名字` 排在最前面：它只认 `@`，抢不走 `**`/`*`/`[`/`` ` `` 那些按首字符分的分支。
    // 与裸地址的先后关系是由调用方决定的——[appendMarked] 在每个位置先问这个函数，
    // 问不到才试 [bareUrlEnd]；所以 `https://x/@y` 是在 `h` 那个位置被整条吃掉，`@` 根本轮不到。
    // 这里的"`@` 前一个字符是地址结构符就不认"是第二道闸，兜住裸地址前半截已经画成文字的情况。
    val mentionEnd = mentionEndAt(text, start)
    if (mentionEnd != null) {
        return InlineMatch(
            end = mentionEnd,
            contentStart = start + 1,
            contentEnd = mentionEnd,
            // 主色 + 半粗：只表达"这是对某个人说的"，**点不开**——本仓库没有"用户名 → 主页"的
            // 解析接口，挂 `LinkAnnotation` 等于给一个点了没反应的链接。参考实现那边能点，
            // 是因为它拿得到 `mentionedUsers` 并配了 `onMentionUser` 回调
            // （`lib/models/mention_user.dart:7` 起那段提及解析就是给这个用途的）。
            style = SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold),
            deep = false,
        )
    }
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

/**
 * 楼层引用最多几位数字：Discourse 的 `postNumber` 到四位数已经是几万楼的超长帖，
 * 五位以上在真实帖子里不存在，放宽只会把 `#12345` 这种编号误当成楼层。
 */
private const val FloorRefMaxDigits = 4

/** 楼层引用 [LinkAnnotation.Clickable] 的 `tag` 前缀，后接楼层号（本文件目前没有别处读它）。 */
private const val FloorRefTagPrefix = "floor:"

/**
 * [start] 处是不是一个 `#N` 楼层引用；是就返回数字之后的位置（也就是整段引用的末尾）。
 *
 * 为什么**前后都要判字符**：`#` 在正文里不止一种身份，光看"后面跟数字"会认错一大片。
 *
 *  * **前一个字符不能是 ASCII 字母数字**——挡 `C#4`（语言名）、`PHP#8`、`v2#3`（版本/编号）。
 *    刻意只算 ASCII：`见#3` 这种中文紧贴的写法非常常见，而 `Char.isLetterOrDigit()` 对汉字
 *    返回 true，用它会把中文语境下大半的楼层引用挡掉（同一条取舍见 [mentionEndAt] 的第 1 道闸，
 *    那里也是这么处理的）。
 *  * **前一个字符不能是 `&`**——挡 HTML 实体 `&#123;`、`&#x4E00;`（正文里贴代码/转义文本很常见）。
 *    只挡 `&` 而不再挡别的符号：`(`、`、`、空格后面接 `#3` 都是正常写法。
 *  * **后一个字符不能是数字**——四位上限之外再挡一道：`#12345` 前四位会被切出来当楼层 1234，
 *    后面那个 `5` 变成普通文字，点击跳到的就是个不存在的楼层。宁可整段不可点，也不给一个错的目标。
 *
 * 长度 1–4 位（[FloorRefMaxDigits]）：`#0` 这种也认，交给调用方决定怎么处理（Discourse 的楼层从 1 开始，
 * 跳不到就是跳不到，不该由解析层替它改数字）。
 *
 * 只在 [appendMarked] 的"什么都没命中"分支里被调用，所以行内码、代码块、链接文字里的 `#3`
 * 根本走不到这里（见 [appendMarked] 里那三处顺序的说明）。
 */
private fun floorRefEndAt(text: String, start: Int): Int? {
    if (start >= text.length || text[start] != '#') return null
    if (start > 0) {
        val prev = text[start - 1]
        if (prev in 'A'..'Z' || prev in 'a'..'z' || prev in '0'..'9') return null
        if (prev == '&') return null
    }
    var end = start + 1
    while (end < text.length && end - start - 1 < FloorRefMaxDigits && text[end] in '0'..'9') end++
    // 一位都没有：单独的 `#` 就是井号本身。
    if (end == start + 1) return null
    // 四位截断之后还跟着数字：这是编号/长数字，不是楼层。
    if (end < text.length && text[end] in '0'..'9') return null
    return end
}

/**
 * 扫描 [text]，把命中的标记换成样式，没命中的字符原样写进去。
 *
 * [onFloorRef] 非空时，在"什么都没命中"的位置额外试一次楼层引用（[floorRefEndAt]），
 * 命中就画成主色、无下划线的可点文字。三处顺序是有讲究的：
 *
 *  1. 先问 [matchInlineAt]——行内码 / 链接 / 粗体这些结构标记优先，`` `#3` `` 是在 `` ` `` 那
 *     一步整段被吃掉的（[InlineMatch.deep] = false，只 `append(inner)`，不再递归），
 *     所以行内码里的 `#3`、链接文字里的 `#3` 都**不会**变可点；
 *  2. 再问 [bareUrlEnd]——裸地址整条先吃掉，`https://a/#3` 里的 `#3` 是地址的一部分；
 *  3. 最后才试楼层引用。
 *
 * 反过来（楼层先判）会把 `https://a/#3` 切成"链接 + 可点的 3"，点击行为就串了。
 */
private fun AnnotatedString.Builder.appendMarked(
    text: String,
    linkColor: Color,
    onFloorRef: ((Int) -> Unit)? = null,
) {
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
            // 收进局部 val 再判空：这样下面那个 lambda 里拿到的是**已判定非空**的引用，
            // 不用在闭包里再 `!!` 一次。
            val refHandler = onFloorRef
            if (refHandler != null) {
                val refEnd = floorRefEndAt(text, i)
                if (refEnd != null) {
                    val number = text.substring(i + 1, refEnd).toIntOrNull()
                    if (number != null) {
                        append(text.substring(cursor, i))
                        // 主色 + **不加下划线**：下划线在本文件里是"会打开别处"的记号（链接、裸地址），
                        // 楼层引用只是同页跳转，跟链接长得一样反而误导。
                        withStyle(SpanStyle(color = linkColor)) {
                            withLink(
                                LinkAnnotation.Clickable(
                                    // `tag` 是这个 Compose 版本里 Clickable 的第一个参数（必填），
                                    // 带上楼层号便于命中检测/日志里认出来；点击走下面的 listener，
                                    // 不经过 LocalUriHandler，所以不会去开浏览器。
                                    tag = FloorRefTagPrefix + number,
                                    styles = null,
                                    linkInteractionListener = { refHandler(number) },
                                ),
                            ) { append(text.substring(i, refEnd)) }
                        }
                        i = refEnd
                        cursor = i
                        continue
                    }
                }
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
                // 粗体/斜体/删除线里继续解析：`**见 #12**` 里的连楼层号仍然是那个楼层号。
                hit.deep -> appendMarked(inner, linkColor, onFloorRef)
                else -> append(inner)
            }
        }
        i = hit.end
        cursor = i
    }
    append(text.substring(cursor))
}
