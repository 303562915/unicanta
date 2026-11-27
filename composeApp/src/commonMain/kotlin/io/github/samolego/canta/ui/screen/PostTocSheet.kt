package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.data.community.PostComment

/**
 * 帖子详情页的**目录面板**：一列楼层索引，点一行就跳到那一楼。
 *
 * ## 它干什么
 *
 * 回帖到几十层之后，「翻回某一楼」只能靠手指一路划。这个面板把 `comments` 摊成一列：
 * 主楼 + 每一条回复，一行就是一层楼（楼层号 / 作者 / 正文首行摘要），当前视口所在的那一层
 * 带一颗主色圆点。点任意一行 → [onJump] 交出那一层的 id，然后 [onDismiss] 自己收起来。
 *
 * ## 对应参考源码
 *
 * 参考 fluxdo 的话题目录：
 *  - `topic_detail_page/controllers/topic_toc_controller.dart` —— 目录的**状态与滚动编排**：
 *    提取（:104-124）、scroll-spy 取当前项（:227-245，视口顶往下 150 的缓冲）、
 *    点一项先粗定位再精确对齐（:166-187）、跳转期间冻结高亮免得闪（:69-71 / :214-223）。
 *  - `topic_detail_page/widgets/topic_toc_panel.dart` —— 目录的**长相**：
 *    标题 + 条数（:110-145 的 `_TocTitle`，图标 + 「目录」 + 条目数）、
 *    一行一项（:400-499 的 `_TocTile`：左竖条 / 内边距随层级 / 激活项用 `selected` 色 α0.08 底 /
 *    弹层里最小触控高 `kMinInteractiveDimension` = 48）、
 *    以及弹层这一侧（:171-184 `showTocBottomSheet`、:196-256 用弹层实际高度决定
 *    "够不够一屏、要不要可拖"）。
 *
 * 与参考的两处**有意不同**，改之前先看明白：
 *  1. 参考的目录是「正文左侧的可折叠侧栏」（`TopicTocSidePanel`，展开 240dp / 收起 44dp，
 *     `topic_toc_panel.dart:29-30`），放不下时才退到底部弹层（:147-169 的 `TopicTocFab`）。
 *     这里是**只有底部面板**：Canta 的帖子正文是整宽通栏（楼层外边距见
 *     `PostsSection.kt:3241-3244`），侧栏要吃掉 240dp 横向空间，正文得重新折行，
 *     一屏读到的字数明显变少；而"从底下弹出来选一项"在本仓社区里已经是既定形态
 *     （`ComposerPickerSheet`，`PostsSection.kt:5201-5230`），用户不用学第二套手势。
 *  2. 参考的目录是**文档标题树**（Markdown `#` 层级，有缩进与父子高亮）；这里的"目录"是
 *     **楼层列表**（后端按时间正序给，楼层号就是位置，见 `PostsSection.kt:2804-2813`）。
 *     所以一行里放的不是标题层级，而是「谁、说了什么」—— 摘要由 [plainSummary] 从 Markdown
 *     源码剥出来。
 *
 * ## 面板形态
 *
 * `ModalBottomSheet`：内部就是 MD3 的 sheet，`skipPartiallyExpanded = false` 让它先停在半屏、
 * 想看得更全再往上拖。**列表自己滚**（`LazyColumn` + `weight(1f)`），不吃面板的拖动手势 ——
 * 本仓另外两个从底下推出来的面板（`UploadPlanDialog` / `PlanScopeDialog`）曾因"上滑翻列表
 * 反把整块面板拖走"退回成自绘对话框（`PostsSection.kt:5208-5213`）；这个面板的列表只在
 * sheet 内部滚，若日后真的出现抢手势，退路是保留下面全部结构与数值、只把外壳换成
 * `ComposerPickerSheet` 那套 `BasicAlertDialog`，行与标题都不用动。
 *
 * 键盘不参与：面板里没有输入框，所以不挂 `imePadding()`（`ComposerPickerSheet` 只在带输入框
 * 的标签面板上才加，`PostsSection.kt:5269-5270`）。
 *
 * @param comments 回帖，后端按时间正序（主楼**不在**里面，见 `PostThread`）。
 * @param floorLabelOf 楼层 id → 楼层号：主楼是 1、第 i 条回复是 i+2、找不到是 -1
 *   （`PostsSection.kt:2811-2813` 的同一套编号）。
 * @param currentFloorId 当前视口所在楼层；`0L` 表示"在主楼 / 还不知道"。
 * @param onJump 点一行：交出那一层的 id（主楼行交 `0L`）。
 * @param onDismiss 收起面板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostTocSheet(
    comments: List<PostComment>,
    floorLabelOf: (Long) -> Int,
    currentFloorId: Long,
    onJump: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    // 只有两层结构：`ModalBottomSheet` 自己画遮罩、圆角与贴底，内容直接进它的 `ColumnScope`
    // ——**不要**再套一层 `Column`：`weight(1f)` 只对"直接父级是有界高度的 Column"有效，
    // 中间多一层 wrap-content 的 Column，列表高度会被算成 0（一行都看不见）。
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = scheme.surface,
        // 自己画抓手（见 [TocDragHandle]）：留着默认那颗就是两条抓手叠在一起。
        dragHandle = null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TocDragHandle()
        // 「N 层」含主楼：楼层号从主楼的 #1 起算，所以总层数 = 回帖数 + 1。
        TocSheetHeader(floorCount = comments.size + 1)

        if (comments.isEmpty()) {
            // 主楼行照旧给：一条回复都没有时，"回主楼"仍然是有意义的动作。
            TocOpRow(
                current = currentFloorId == 0L,
                onClick = {
                    onJump(0L)
                    onDismiss()
                },
            )
            TocEmptyHint(modifier = Modifier.fillMaxWidth().weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // 垫在**列表**上而不是整个 sheet 上：面板底色仍铺到手势条下面，
                    // 只有内容抬起来（与 `ComposerPickerSheet` 把 `navigationBarsPadding()`
                    // 垫在面板内容上同一个目的，`PostsSection.kt:5267-5268`）。
                    .navigationBarsPadding(),
                // 上下 8dp：首行不贴标题、末行不贴底；左右留白在行自己身上
                // （[TocEntryRow] 的 `padding(horizontal = 8.dp)`），这样高亮底色
                // 是从屏幕边往里收 8dp 的一条圆角块，而不是通栏色带。
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                // 首项固定是主楼（`id = 0L`）：参考里目录第一项就是 1 楼
                // （`topic_toc_controller.dart:81-88` 只对 1 楼提取），这里对应"主楼"。
                item(key = OP_KEY) {
                    TocOpRow(
                        current = currentFloorId == 0L,
                        onClick = {
                            onJump(0L)
                            onDismiss()
                        },
                    )
                }
                // `key = { it.id }`：列表刷新换血后行位置会变，按 id 复用才能让
                // "当前楼层"那颗圆点跟着楼层走，而不是跟着第 N 个槽位走
                // （同一套理由见 `PostsSection.kt:2799-2800`）。
                items(comments, key = { it.id }) { comment ->
                    val floor = floorLabelOf(comment.id)
                    // 摘要剥标记 + 截断是纯字符串运算，但正文有几百字、正则也有十来条：
                    // 包在 `remember(comment.body)` 里，滚动时每帧重组不再重算
                    // （参考侧是靠渲染缓存省这笔，`topic_toc_controller.dart:104-110`）。
                    val summary = remember(comment.body) { plainSummary(comment.body) }
                    TocEntryRow(
                        floorText = floorTextOf(floor),
                        author = comment.author,
                        summary = summary,
                        // 找不到的那一层（`floorLabelOf` 给 -1）与"还没定"的 0L 都不会命中任何一行：
                        // 圆点只在真正对得上时出现，不编一个"你在第 0 层"。
                        current = floor > 0 && comment.id == currentFloorId,
                        onClickLabel = "跳到第 $floor 层",
                        onClick = {
                            onJump(comment.id)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

/** 主楼那一行的楼层号写法：参考里主楼也是 `#1`，这里写「主楼」是因为面板里只有它不在 [PostTocSheet] 的 `comments` 里。 */
private const val OP_FLOOR_TEXT = "主楼"

/** 主楼行在 `LazyColumn` 里的键（与回帖的 `Long` 键区分开、不会撞）。 */
private const val OP_KEY = "toc-op"

/** 摘要字符上限（含省略号，见 [plainSummary]）。 */
private const val SUMMARY_MAX = 40

// ------------------------------------------------------------------ 摘要剥标记
//
// 全部提到文件级：`Regex` 的构造要编译一次模式，放进 composable 里就是每帧编译一遍。
// 顺序在 [plainSummary] 里是有讲究的（先图后链接、先围栏后行内标记），改顺序前先读那里的注释。

private val IMAGE_ONLY = Regex("""!\[([^\]]*)]\([^)]*\)""")
private val LINK_ONLY = Regex("""\[([^\]]*)]\([^)]*\)""")
private val FENCE_LINE = Regex("""^\s*(```|~~~).*$""")
private val ATX_HEADING = Regex("""^\s{0,3}#{1,6}\s*""")
private val BLOCK_QUOTE = Regex("""^\s{0,3}>\s?""")
private val BULLET_MARK = Regex("""^\s{0,3}([-*+]|\d{1,3}[.)])\s+""")
private val THEMATIC_BREAK = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
private val HTML_TAG = Regex("""<[^>]*>""")
private val CODE_TICK = Regex("""`+""")
private val EMPHASIS_MARK = Regex("""[*_~]{1,3}""")
private val RUN_OF_SPACE = Regex("""\s+""")

/**
 * 把一份 Markdown 正文压成一行纯文字摘要，最多 [SUMMARY_MAX] 个字符。
 *
 * 为什么自己剥而不是复用渲染器：目录要的是"一眼认出这是哪一楼"，不是排版。渲染器给出的
 * 是节点树，折成一行反而是另一套代码；这里按行扫原文、把标记字符删掉就够。
 *
 * 参考侧没有这段逻辑 —— fluxdo 的目录取的是文档标题（`TocEntry.text` 由标题节点直接给出，
 * `topic_toc_panel.dart:426-442` 只是把它排上去），而这里一行要同时装"谁 + 说了什么"，
 * 摘要只能从正文里现剥。
 *
 * 几条边界（`null` / 空串 / 纯代码块正文都不崩）：
 *  - `null`、空白串 → `""`；
 *  - **只取第一段有字的东西**：正文第一行常常是 `# 标题`、`> 引用` 或一张 `![](url)`，
 *    剥完就空了，继续往下找；整篇都是标记（纯图片、纯代码围栏）时也只会得到 `""`；
 *  - 代码围栏那一行（``` ``` / ```kotlin ）整行丢掉，围栏**里面**的行按普通文字处理 ——
 *    纯代码块正文于是得到的是第一行代码，比一片空白有用；
 *  - 图片保留 alt（`![示意图](url)` → `示意图`），alt 为空就什么都不留；
 *  - 链接只留文字、不留 URL；裸 URL 不动（它本身是能读的内容）；
 *  - 长度：`39 字 + …` = 40，省略号也算一个字符，不会因为加了它而超上限。
 */
private fun plainSummary(markdown: String?): String {
    if (markdown.isNullOrBlank()) return ""

    var picked: String? = null
    for (rawLine in markdown.lineSequence()) {
        if (FENCE_LINE.matches(rawLine)) continue
        // 先把"图"和"链接"换成它们的**文字**，再删别的标记：顺序反了的话 `[a](u)` 里的
        // `]` / `)` 先被当标点留下，摘要里会冒出 `a(u` 这种半截。
        var line = IMAGE_ONLY.replace(rawLine) { it.groupValues[1] }
        line = LINK_ONLY.replace(line) { it.groupValues[1] }
        line = HTML_TAG.replace(line, "")
        line = ATX_HEADING.replace(line, "")
        line = BLOCK_QUOTE.replace(line, "")
        // 分隔线整行丢掉：`---` 剥成空行之后行号还在，摘要会莫名从一个空串开始。
        if (THEMATIC_BREAK.matches(line)) continue
        line = BULLET_MARK.replace(line, "")
        line = CODE_TICK.replace(line, "")
        line = EMPHASIS_MARK.replace(line, "")
        line = line.trim()
        if (line.isEmpty()) continue
        picked = line
        break
    }

    val flat = RUN_OF_SPACE.replace(picked ?: return "", " ").trim()
    if (flat.isEmpty()) return ""
    return if (flat.length <= SUMMARY_MAX) flat else flat.take(SUMMARY_MAX - 1).trimEnd() + "…"
}

/**
 * 楼层号文字的写法：`#N`；`floorLabelOf` 说找不到（-1）时写一个破折号。
 *
 * 为什么不写 `#-1`：那是把"查不到"当成一个楼层号画出来，人会以为真的有一层叫 -1
 * （同一判断见 `PostsSection.kt:3910-3911` 的引用指示条：找不到就只写名字、不编号）。
 */
private fun floorTextOf(floor: Int): String = if (floor > 0) "#$floor" else "—"

/**
 * 顶部那条拖拽条：36dp 宽、4dp 高、圆角 2dp、`onSurfaceVariant` α0.4。
 *
 * 数值照本仓既有的底部面板（`ComposerPickerSheet` 是 40×4 / α0.3，`PostsSection.kt:5272-5280`），
 * 宽度收到 36 是因为这颗面板的标题行左边有「目录」两个字贴着 20dp 边距，抓手太宽会显得
 * 比标题还抢眼。它是**视觉提示**，不是手势区：`ModalBottomSheet` 自己认全宽的拖动。
 */
@Composable
private fun TocDragHandle() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(scheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}

/**
 * 标题行：「目录」在左、「N 层」在右。
 *
 * 照参考的 `_TocTitle`（`topic_toc_panel.dart:110-145`：图标 + 标题 + 条数），去掉图标 ——
 * 这里标题本身只有两个字，前面再站一颗图标，右边那颗计数会被挤到第二行。
 * 字号用 `titleSmall` / `labelSmall`：面板很小，标题不该比正文（`bodyMedium`）大两号；
 * 计数用 `onSurfaceVariant` 退到次要层级，让"目录"两个字是这一行的主语。
 *
 * @param floorCount 总层数（含主楼），由调用点算好传进来。
 */
@Composable
private fun TocSheetHeader(floorCount: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "目录",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        // 弹性留白而不是 `Arrangement.SpaceBetween`：标题短的时候两种写法一样，
        // 但换成 `SpaceBetween` 之后，将来标题变长会把计数压成 0 宽（参考侧同样用
        // Flexible 收标题、把条数留在原位，`topic_toc_panel.dart:121-141`）。
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "$floorCount 层",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 首项「主楼」：面板顶部固定一行，点它回到帖子开头（`onJump(0L)`）。
 *
 * 参考侧的第一项就是 1 楼（`topic_toc_controller.dart:81-88` 的目录只从 1 楼提取），
 * 这里对应"主楼"。它和回帖行共用 [TocEntryRow] 的几何，只有两格空着：
 *  - 作者：主楼作者不在 `comments` 里，本面板的参数里也没有它 —— 这一格留空，
 *    宁可少一行信息，也不拿第一条回复的作者去猜；
 *  - 摘要：同理，正文没传进来。
 *
 * @param current `currentFloorId == 0L`（视口在主楼 / 还不知道）时为 true，
 *   这一行就带上"你在这"的底色与圆点。
 */
@Composable
private fun TocOpRow(current: Boolean, onClick: () -> Unit) {
    TocEntryRow(
        floorText = OP_FLOOR_TEXT,
        author = "",
        summary = "",
        current = current,
        onClickLabel = "跳到主楼",
        onClick = onClick,
    )
}

/**
 * 一行：楼层号 / 作者 + 摘要 / （当前楼层那颗圆点）。
 *
 * 几何与参考 `_TocTile`（`topic_toc_panel.dart:400-499`）逐项对上：
 *  - 最小触控高 48dp（`kMinInteractiveDimension`，:472）—— 与
 *    `PickerOptionRow`、属性条胶囊同一个数（`PostsSection.kt:5333-5354`）；
 *  - 圆角 8dp 的整行可点块（:421-423 的 `borderRadius(8)`）；
 *  - 激活项 = 主色 α0.08 的底色（:449 `selectedColor.withValues(alpha: .08)`），
 *    本仓读作 `primary.copy(alpha = 0.08f)`；
 *  - 圆点 6dp 是**这里加的**：参考用左侧一根 3dp 竖条表示激活（:456-469），
 *    竖条在窄行里容易被误读成"缩进线"；面板里只有一列，一颗圆点更直白地回答
 *    "你在这"（参考侧弹层里也放弃了竖条之外的层级缩进，:430 的 `maxLines` 分支）。
 *
 * 楼层号那一格**定宽 40dp、右对齐**：数字位数不一样（`#9` vs `#120`），右对齐之后
 * 个位对齐，扫一列下来是一绺整齐的数字；不定宽的话每行作者名的左边缘都会跟着抖。
 *
 * @param floorText 楼层号文字（`#N` / `主楼` / `—`，见 [floorTextOf]）。
 * @param author 作者名，单行省略。
 * @param summary 纯文字摘要（[plainSummary] 的产物），空串时这一行不画。
 * @param current 是不是"你在这"那一层。
 * @param onClickLabel 无障碍读出来的动作名（`Modifier.clickable` 的 `onClickLabel`）。
 */
@Composable
private fun TocEntryRow(
    floorText: String,
    author: String,
    summary: String,
    current: Boolean,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 左右 8dp：高亮底色是从屏幕边往里收的一条圆角块，通栏色带会看起来像分隔带。
            .padding(horizontal = 8.dp)
            // `clip` 必须在 `clickable` **之前**：水波纹跟着 8dp 圆角走，不然点下去是一块
            // 方角高亮压在圆角底上（同一顺序见 `PostsSection.kt:5347-5351`）。
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (current) {
                    Modifier.background(scheme.primary.copy(alpha = 0.08f))
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .padding(horizontal = 8.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = floorText,
            modifier = Modifier.width(40.dp),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 作者名只在非空时画：主楼那一行没有作者（见调用点），画一条空行会让它比
            // 别的行矮一截、垂直居中看着偏。
            if (author.isNotEmpty()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (current) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }
    }
}

/**
 * 一条回复都没有时的占位：在整个列表区里居中一行「还没有回复」。
 *
 * 拿 `weight(1f)` 撑满剩下的高度再居中，而不是画在标题下面一行 —— 面板这时是半屏，
 * 一行字贴在顶部会让人以为"下面还有内容没加载出来"。
 */
@Composable
private fun TocEmptyHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "还没有回复",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
