package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * 行内标记：`**粗**` `*斜*` `_斜_` `` `代码` `` `~~删除~~` `[文字](链接)`。不处理块级。
 *
 * 只认**成对**的标记：没闭合的那一半原样输出，因为帖子正文常有人手打一个星号，
 * 吃掉它比留着更难看。链接只上色加下划线，不接点击——正文里的地址点不开，
 * 与 [io.github.samolego.canta.ui.component.text.UrlText] 那边可点的链接是两回事。
 *
 * 这个函数不读主题（不是 @Composable），颜色全靠调用方给：正文用 primary，
 * 引用块里也用 primary 就够了，代码片的底色借用它的低透明度。
 */
fun markdownInline(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    appendMarked(text, linkColor)
}

/**
 * 块级渲染：标题、段落、无序/有序列表、引用、围栏代码块、分割线、空行。
 *
 * 图文混排的帖子不需要完整的 Markdown，只要"看起来分了段"。所以这里不做嵌套块、不做表格、
 * 不做 HTML，遇到不认识的行就当段落——宁可原样显示，也不要把用户写的字吞掉。
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
        blocks.forEach { block ->
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

                is MdBlock.CodeBlock -> CodeBlock(block.code, color)

                is MdBlock.Rule -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                is MdBlock.Gap -> Spacer(modifier = Modifier.height(4.dp))
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
 * 围栏代码块。单独一个 composable 是为了让 `rememberScrollState()` 有个固定的调用点：
 * 写在 [MiniMarkdown] 的 forEach 里，块数一变 scroll 位置就会串到别的块上。
 */
@Composable
private fun CodeBlock(code: String, color: Color) {
    val scroll = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = color,
            // 代码不折行：折过之后缩进全乱，横向滚动比看不懂强。
            softWrap = false,
            modifier = Modifier.horizontalScroll(scroll),
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
    class CodeBlock(val code: String) : MdBlock
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
            // 代码块内部一个字都不动，只去掉收尾围栏那一行。
            val body = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                body += lines[i]
                i++
            }
            // 没有收尾围栏时按"到这里为止"处理，正文不会被吞掉。
            if (i < lines.size) i++
            blocks += MdBlock.CodeBlock(body.joinToString("\n"))
            continue
        }
        if (trimmed.isEmpty()) {
            blocks += MdBlock.Gap
            i++
            continue
        }
        if (isRule(trimmed)) {
            blocks += MdBlock.Rule
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
            val line = lines[i].trim()
            if (startsBlock(line)) break
            paragraph += line
            i++
        }
        blocks += MdBlock.Paragraph(paragraph.joinToString("\n"))
    }
    return blocks
}

/** 这一行会不会自己起一个新块。段落只在这些行前面断开。 */
private fun startsBlock(trimmed: String): Boolean =
    trimmed.isEmpty() ||
        trimmed.startsWith("```") ||
        isRule(trimmed) ||
        headingLevel(trimmed) > 0 ||
        trimmed.startsWith(">") ||
        bulletText(trimmed) != null ||
        orderedText(trimmed) != null

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
 * 后面必须跟一个空格：`#话题` 这种是正文里的标签，不是标题。
 */
private fun headingLevel(trimmed: String): Int {
    var level = 0
    while (level < trimmed.length && level < 4 && trimmed[level] == '#') level++
    if (level == 0) return 0
    if (level >= trimmed.length || trimmed[level] != ' ') return 0
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
// 行内解析
// ---------------------------------------------------------------------------

/**
 * 命中的一对行内标记：[contentStart]..[contentEnd] 是要输出的内容，[end] 是整段标记之后的位置。
 *
 * [deep] 表示内容里还要不要继续解析：粗体里套斜体是正常的，代码和链接文字里不该再有标记。
 */
private class InlineMatch(
    val end: Int,
    val contentStart: Int,
    val contentEnd: Int,
    val style: SpanStyle,
    val deep: Boolean,
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
                    )
                }
            }
        }
        return null
    }
    return null
}

/** 扫描 [text]，把命中的标记换成样式，没命中的字符原样写进去。 */
private fun AnnotatedString.Builder.appendMarked(text: String, linkColor: Color) {
    var cursor = 0
    var i = 0
    while (i < text.length) {
        val hit = matchInlineAt(text, i, linkColor)
        if (hit == null) {
            i++
            continue
        }
        append(text.substring(cursor, i))
        val inner = text.substring(hit.contentStart, hit.contentEnd)
        withStyle(hit.style) {
            if (hit.deep) appendMarked(inner, linkColor) else append(inner)
        }
        i = hit.end
        cursor = i
    }
    append(text.substring(cursor))
}
