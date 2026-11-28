package io.github.samolego.canta.ui.screen

// ───────────────────────────────────────────────────────────────
// 图标口径：本文件的图标**一律用 Material Symbols Rounded**。
//
// fluxdo 那一整套图标都是 `Symbols.xxx_rounded`（`post_action_bar.dart` 的
// `favorite_rounded` / `chat_bubble_rounded`，`topic_statistics.dart` 的
// `menu_book_rounded`，`editor_tools.dart` 的格式化图标）。Compose 的
// `materialIconsExtended` 里对应的就是 `Icons.Rounded.*`：笔画圆、粗细一致。
//
// 曾经混用过三套（`Icons.Outlined` 的空心笔画 + 自绘 `FlGlyphs` 的 FlClash 几何风 +
// 个别 `Icons.Filled`），结果是一条动作行上三种粗细，看着像三张图拼起来的。
// 现在统一成：
//   1. `Icons.Rounded.*` —— 有对应 Material Symbols 的一律用它；
//   2. `Icons.AutoMirrored.Rounded.*` —— 有方向语义的（发送 / 回复）；
//   3. `FlGlyphs` 已从本文件**全部撤出**（自绘图标留给了代理设置那些 FlClash 页面，
//      那儿的视觉语言才是它的主场）。
//
// 改图标时别再用 `Icons.Outlined.*`：那个是细笔画空心版，和 Rounded 摆一起很显眼。
// ───────────────────────────────────────────────────────────────

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
// 举报：楼层「更多」菜单里那一项与举报面板的标题都用它（Rounded 集，必须单独 import，
// 这仓库漏过一次 import 直接 `Unresolved reference`）。
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
// 「回到最新」浮钮那颗下箭头。**必须显式 import**：`Icons.Rounded.*` 是扩展属性，
// 漏了就只是 `Unresolved reference: KeyboardArrowDown`（本仓在这类事上红过三次）。
// 只用 Rounded 集：本文件的图标口径见文件头那段注释。
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import io.github.samolego.canta.ui.fl.isDark
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
// 「上次读到哪一屏」的防抖：`snapshotFlow` 把 `firstVisibleItemIndex` / `ScrollOffset` 变成一条流，
// 停手 400ms 才写一次盘（见 `PostThreadPane` 里那条保存 effect）。
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
// 列表卡片的「有未读」：圆点是 `drawBehind` 画的、没有节点，读屏要靠 stateDescription 才知道
// （与 `PostImage.kt:194-195` 补 `onClick` 标签同一条理由）。
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
// 回复胶囊的草稿文案要两种颜色（「草稿」强调色 + 摘要 onSurface），
// 一个 `Text` 里分段上色只能走 `buildAnnotatedString`。
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.cache.ScreenCache
import io.github.samolego.canta.data.cache.stableMerge
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.CommunityPost
import io.github.samolego.canta.data.community.PostComment
import io.github.samolego.canta.data.community.PostDraft
import io.github.samolego.canta.data.community.PostDraftStore
import io.github.samolego.canta.data.community.PostPositionStore
import io.github.samolego.canta.data.community.PostReadStore
import io.github.samolego.canta.data.community.PostStore
import io.github.samolego.canta.data.community.PostThread
import io.github.samolego.canta.data.rank.WeeklyRankRepository
import io.github.samolego.canta.ui.component.AuthorLabel
import io.github.samolego.canta.ui.component.AnonymousDot
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.MiniMarkdown
import io.github.samolego.canta.ui.component.PostAvatar
import io.github.samolego.canta.ui.component.PostImageThumb
import io.github.samolego.canta.ui.component.ScrollToTopEffect
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.fallbackBrandColor
import io.github.samolego.canta.ui.component.isDefaultAuthorName
import io.github.samolego.canta.ui.component.metaTint
import io.github.samolego.canta.ui.component.parseHexColor
import io.github.samolego.canta.ui.component.readableBrandColor
import io.github.samolego.canta.ui.component.relativeStamp
import io.github.samolego.canta.ui.component.rememberRememberedListState
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.BeijingTime
import io.github.samolego.canta.util.IdentitySetup
import io.github.samolego.canta.util.ShareCode
import io.github.samolego.canta.util.currentTimeMillis
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** 一页拉多少条。后端把 `p_limit` 夹在 1..50，这里取一屏够看的数。 */
private const val POSTS_PAGE = 20

/**
 * 「刚跳到的那一层」左侧那根竖条亮多久（毫秒）。
 *
 * 从亮起到开始淡出是 1500ms（源码 `post_replies_sheet.dart:221` 的 `Timer(seconds: 2)`
 * 是"高亮持续 2 秒、然后直接清掉"；本仓取 1.5s + 一段淡出，观感上等于 2s 内消失，
 * 但不会在 2s 那一刻硬切）。
 */
private const val HIGHLIGHT_FADE_MS = 600

/** 竖条亮着的时长（毫秒）：到点把 [PostThreadPane] 里那个 `highlightedFloorId` 清成 0。 */
private const val HIGHLIGHT_HOLD_MS = 1500L

/**
 * 侧栏「标签」页最多列多少行。
 *
 * 标签是从已经拉到手的帖子里汇总的（后端没有 `/tags.json` 那样的全量接口），一屏几百条
 * 既没人看也拖慢首帧；60 行已经盖住常用的那些，剩下的用搜索胶囊找。
 */
private const val DRAWER_TAG_LIMIT = 60

/**
 * 帖子列表在 [ScreenCache] 里的槽位。
 *
 * 键里不带身份：帖子是公共列表，谁看都是同一份；带了身份的键反而会让换身份后白等一次加载。
 */
private const val POSTS_CACHE_KEY = "screen:posts"

/** 标题、正文的上限，与后端 `community_posts` 的约束同一个数。 */
private const val POST_TITLE_LIMIT = 120
private const val POST_BODY_LIMIT = 8000

/**
 * 详情页那一列 LazyColumn 里，**第一条回复的绝对 item 索引**。
 *
 * 0 = `thread-header`、1 = `op`、2 = `thread-head`，之后每一条回复占一个 item
 * （空列表时 3 是 `thread-empty`，回复从 3 开始，公式照样对）。
 *
 * 能写成常量、不用 `Map<Long, Int>` 的原因是：**日期分隔不是独立 item**，它和楼层共用
 * 一个 `item(key = "c-…")`（见 `items(floorRows)` 里的注释）。于是"第 i 条回复"的索引恒为
 * `FLOORS_FIRST + i`，与跨天分隔的条数无关。哪天往楼层前面再插一项，改这个数就行。
 */
private const val FLOORS_FIRST = 3

/**
 * 「上次读到哪一屏」停手多久才写一次盘（毫秒）。
 *
 * 400 是"人停下来了"与"还没反应过来"之间的那个数：手指离开屏幕、惯性滑动停住之后，一屏位置
 * 不会再变，这时候写才有意义。更短（比如 100ms）等于慢速滑动时也每几百毫秒写一次；更长（比如 1s）
 * 则"滑到一半直接按返回"这种常见动作会漏记。
 *
 * 为什么不每帧写：一次滑动有两秒、几百次 `firstVisibleItemIndex` 变化，而其中只有最后一次是
 * "读到了哪儿"。逐次写盘是几百次 JSON 编码 + 文件写，除了最后一个值全都没人要
 * （见 `PostPositionStore` 的 KDoc）。
 */
private const val POSITION_SAVE_DEBOUNCE_MS = 400L

// ---------------------------------------------------------------------------- 长正文折叠
//
// 参考实现把长帖按 HTML 顶层块**切成多段**、交给同一列 sliver 慢慢补（fluxdo
// `lib/widgets/post/post_item/segmented_long_post.dart:44-84` 的 `tryBuild`：`chunkThreshold`
// 一过就 `chunkDocument`，每段一个 `NewEngineChunkSegment`）。本仓的正文是 `MiniMarkdown`
// 直接吃 Markdown 源码，没有"块"这种中间形态，也**不能**把源码截成半截去渲染（截在半截表格
// 或代码块里，出来的是一块残片）。所以这边只做折叠：整块照旧渲染一次，用**容器**的高度
// 夹住前 8 行 —— 拿不到虚拟化的省，但省掉的是"每一层楼都要先滚过去才知道它有多长"的观感问题，
// 而实现只在容器上，`MiniMarkdown` 一行都不用动。

/**
 * 折叠门槛之一：正文字符数**超过**它就折叠。
 *
 * 800 是"一段长文"的量：手机一屏大约 20 行 × 20 来字，800 字差不多两屏多；短于这个量的
 * 楼层保持原样 —— 楼层是列表项，常见楼层多出一行就会把整列表的密度拖垮。
 */
private const val FOLD_LONG_BODY_CHARS = 800

/**
 * 折叠门槛之二：正文**原始行数**超过它就折叠。
 *
 * 字符数与行数两个门槛都要：代码块、清单、空行多、每行一两个字的正文，字符不多但照样把
 * 那一层楼撑到两屏高；只留字符数门槛，这一类永远漏过去。
 */
private const val FOLD_LONG_BODY_LINES = 20

/** 折叠态露出来的行数。 */
private const val FOLDED_BODY_VISIBLE_LINES = 8

/**
 * 正文行高：与 `PostFloorCard` 里 `MiniMarkdown` 的 `lineHeight = 21.sp` 是**同一个数**
 * （14sp 正文 × 1.5，见 `post_item.dart:332-347` 的 `bodyMedium.copyWith(height: 1.5)`）。
 */
private val FOLDED_BODY_LINE_HEIGHT = 21.sp

/**
 * 折叠态容器的最大高度 = 8 行 × 21sp = **168sp**；`fontScale = 1` 时就是 **168dp**。
 *
 * 这里不写死 `168.dp`，而是走 `TextUnit.toDp()`：系统字号调到 1.3 倍时 21sp 已经比 21dp 高，
 * 写死 168dp 就只剩 6 行，"前 8 行"这个承诺当场失效；走换算的话行数不动、只是那 8 行更高。
 */
private fun foldedBodyMaxHeight(density: Density): Dp =
    with(density) { FOLDED_BODY_LINE_HEIGHT.toDp() * FOLDED_BODY_VISIBLE_LINES.toFloat() }

/**
 * 折叠态底部那层遮罩的高度（32dp）：三段渐变（透明 → 底色），让第 8 行末尾"淡进底色"，
 * 而不是被容器边缘硬切一刀。
 */
private val FOLDED_BODY_FADE_HEIGHT = 32.dp

/**
 * 正文算不算「长」：**字符数 > [FOLD_LONG_BODY_CHARS]** 或 **行数 > [FOLD_LONG_BODY_LINES]**，
 * 任一超过就要折叠。
 *
 * 行数按原始换行（`\n`）数：Markdown 渲染后的视觉行只会更多（自动换行、列表项），所以这是个
 * **下界** —— 会把"其实很长"的正文判成长，不会反过来。要精确就得先渲染再量，那是给每一层楼
 * 都加一次测量，不划算。
 *
 * 复杂度 O(n)、只按 `body` 记忆（见调用点 `remember(body)`）：一次列表刷新只对**变了**的正文重算。
 */
private fun isLongFloorBody(body: String): Boolean {
    if (body.length > FOLD_LONG_BODY_CHARS) return true
    var lines = 1
    var index = 0
    while (index < body.length) {
        if (body[index] == '\n') {
            lines++
            if (lines > FOLD_LONG_BODY_LINES) return true
        }
        index++
    }
    return false
}

// ------------------------------------------------------------------ 楼层「回复历史」（父链）
//
// 注意：这一套和上面那套「长帖折叠」是**两套**，别合并。
//   - 上面折的是**楼层自己的正文**：门槛 800 字 / 20 行，露 8 行，状态按楼层 id 记在
//     `expandedBodyFloors`（`Set<Long>`），容器上还压了一层渐变遮罩。
//   - 这里折的是**父链卡片里的引用原文**：固定 3 行，露 54dp，状态按「楼层 id + 层序」记在
//     `unfoldedReplyCards`（`Set<String>`），只裁不渐变（行高整数倍，切在行与行之间，不切字）。
// 复用其中任何一样（常量、容器、状态集），调一处就会连带改到另一处的观感 —— 这两块调参的
// 目的完全不同：一个省滚动高度，一个让"在回谁"能原地读完又不撑爆一屏。

/**
 * 父链最多上溯几层。
 *
 * 3 层是"能读懂上下文"与"别炸一屏"之间取的数：卡片最矮也有 头像行 20 + 6 + 3 行正文 54 +
 * 24 内边距 ≈ 104dp，3 层 ≈ 312dp，正好半屏；放开到 5 层，一屏里就只剩楼层自己的开头一行。
 * 参考实现的父帖链一整串全展开（`post_reply_history.dart:47-70` 一次性列出 `replyHistory`
 * 全部），那边一条帖子最多两三层父帖、不会失控；本仓的 `replyToId` 是自由引用，可以有人连环
 * 引用十几层，所以这里必须封顶。
 */
private const val REPLY_HISTORY_DEPTH = 3

/** 父链卡片露几行正文。 */
private const val REPLY_HISTORY_CARD_LINES = 3

/**
 * 父链卡片之间 **3dp** 的缝。
 *
 * 与参考实现的 `SegmentedCardItem._gap = 3`（`embedded_posts.dart:64-66`）同一个数：那边一整串
 * 父帖是一组分段卡片，条目之间就靠这 3dp 分出"每一张是一层"，再宽就散成三张不相干的卡。
 */
private val REPLY_HISTORY_CARD_GAP = 3.dp

/**
 * 父链卡片正文行高 **18sp**（13sp × 1.4）。
 *
 * 与 [NESTED_LINE] 同一个数、同一个来源（`embedded_posts.dart:24-25` 的
 * `_embeddedFontSize = 13` / `_embeddedLineHeight = 1.4`）：父链卡与嵌套回复露的都是"引用来的
 * 次级内容"，字号、行高只要差一格，同一屏里两块引用就会一大一小。
 */
private val REPLY_HISTORY_CARD_LINE_HEIGHT = 18.sp

/**
 * 父链卡片折叠时估算"一行能放几个字"。
 *
 * 卡片内容宽 ≈ 屏宽 380 − 楼层左右 32 − 卡片内边距 24 ≈ 324dp，13sp 中文字约 13dp 宽 → 约 22 字。
 * 只用于**判断要不要折**，不参与布局：估多了会把短正文也折起来（多一颗「展开」），估少了
 * 长正文不折（撑高卡片）。宁可估少 —— 少折只是不省高度，多折却是"字被切掉还没按钮"。
 */
private const val REPLY_HISTORY_CARD_CHARS_PER_LINE = 22

/**
 * 折叠态容器的最大高度 = 3 行 × 18sp = **54sp**；`fontScale = 1` 时正好 **54dp**。
 *
 * 与 [foldedBodyMaxHeight] 同一个理由不写死 `54.dp`：系统字号调到 1.3 倍时 18sp 已经比 18dp 高，
 * 写死 54dp 就只剩 2 行多，"露 3 行"当场失效。走 `TextUnit.toDp()` 的话行数不动、只是那 3 行更高；
 * 而且高度恒等于行高的整数倍，`clipToBounds` 正好切在行间空隙上，不会切出半截字。
 */
private fun replyHistoryCardFoldHeight(density: Density): Dp =
    with(density) { REPLY_HISTORY_CARD_LINE_HEIGHT.toDp() * REPLY_HISTORY_CARD_LINES.toFloat() }

/**
 * 父卡正文放不下 [REPLY_HISTORY_CARD_LINES] 行吗（要不要折）。
 *
 * 按字符数除以 [REPLY_HISTORY_CARD_CHARS_PER_LINE] 估行（每个 `\n` 另起一段），**不真的去量**：
 * `MiniMarkdown` 出来的是一列（段落/表格/代码块/图片），没有一处能挂 `onTextLayout`；要精确就得
 * 把它塞进 `SubcomposeLayout` 先测一遍，那等于给每张父卡付一次额外的组合与测量。
 *
 * 复杂度 O(n)、只按正文 `remember`（见调用点）：一次刷新只对变了的正文重算。
 */
private fun isLongReplyCardBody(body: String): Boolean {
    if (body.isEmpty()) return false
    var lines = 0
    var run = 0
    var index = 0
    while (index < body.length) {
        if (body[index] == '\n') {
            lines += if (run == 0) 1 else (run + REPLY_HISTORY_CARD_CHARS_PER_LINE - 1) / REPLY_HISTORY_CARD_CHARS_PER_LINE
            if (lines > REPLY_HISTORY_CARD_LINES) return true
            run = 0
        } else {
            run++
        }
        index++
    }
    return lines + (run + REPLY_HISTORY_CARD_CHARS_PER_LINE - 1) / REPLY_HISTORY_CARD_CHARS_PER_LINE >
        REPLY_HISTORY_CARD_LINES
}

/**
 * 父链卡片的折叠状态键：**楼层 id + 层序**。
 *
 * 为什么带层序：同一个 id 在一条链里只会出现一次（[replyHistoryOf] 用已访问集合挡环），所以
 * 光用 id 也不会串；但"层序"进键之后，同一条楼层出现在两条不同链的不同位置时（A 引 B、C 也引 B），
 * 展开状态各算各的 —— 那是两处不同的引用，不该一处展开另一处跟着展开。
 */
private fun replyHistoryCardKey(commentId: Long, depth: Int): String = "$commentId#$depth"

/**
 * 沿 `replyToId` 往上收父链，最多 [REPLY_HISTORY_DEPTH] 层（index 0 = 直接父楼）。
 *
 * 两道闸，缺一不可：
 *  1. **已访问集合** `seen` —— 脏数据里 `replyToId` 可能成环（A 引 B、B 引 A，甚至自己引自己），
 *     只靠层数上限的话环会在 3 层后停住、但会画出"引用了自己两次"的鬼卡；先查 `seen` 就在
 *     第一次重复处断掉。
 *  2. **层数上限** —— 正常数据也可能一路引用到第 20 层，`seen` 挡不住，必须封顶。
 *
 * 父楼**不在 [byId] 里**（被删 / 还没拉到）时链条就地截断：上面几层还有就照画，一个都没有就是
 * 空链 —— 调用点靠"空链 → 不给长按"把这条通路关掉，不伪造一层空卡。
 */
private fun replyHistoryOf(start: PostComment, byId: Map<Long, PostComment>): List<PostComment> {
    val chain = ArrayList<PostComment>(REPLY_HISTORY_DEPTH)
    val seen = HashSet<Long>(REPLY_HISTORY_DEPTH * 2)
    seen += start.id
    var cursor = start
    while (chain.size < REPLY_HISTORY_DEPTH) {
        val parentId = cursor.replyToId
        if (parentId <= 0L) break
        val parent = byId[parentId] ?: break
        if (!seen.add(parent.id)) break
        chain += parent
        cursor = parent
    }
    return chain
}

/**
 * 自动存草稿的防抖窗口：还在敲就重新计时，700 毫秒没再动才写一次盘。
 *
 * 不是每一键都写：写盘一次比一次内存赋值贵得多，而且草稿要的是"手停下来那一刻的样子"，
 * 不是每一次中间态。
 */
private const val AUTOSAVE_DELAY_MS = 700L

/**
 * 分类的候选：发帖时点一个，再点一下取消。
 *
 * 固定五个、写死在客户端：后端只把分类当字符串存，所以这一串是"页面给什么"而不是"数据库允许
 * 什么"——以后加一个候选不需要动迁移。
 */
private val POST_CATEGORIES = listOf("闲聊", "求助", "分享", "资源", "其它")

/** 标签上限：个数与每个的长度，与后端 `post_add` 里的归整规则同一个数。 */
private const val TAG_MAX = 5
private const val TAG_LIMIT = 20

/**
 * 工具分组。顺序就是 fluxdo `editor_tools.dart` 里 enum 的声明顺序
 * （format → insert → editing），面板按这个顺序铺，不另写一份排序表——
 * 加一组只要往枚举里插，位置自然对。
 *
 * Editing 这一组没有撤销/重做：本仓的正文是一个受控的 `TextFieldValue`，
 * 没有历史栈，做不出"点一下倒退一步"的真动作，所以那一组只收**真能用**的编辑动作
 * （见 [EditorToolAction] 里的 `Clear`）。
 */
private enum class ToolGroup(val label: String) {
    Format("格式"),
    Insert("插入"),
    Editing("编辑"),
}

/**
 * 编辑器工具栏的一项：图标 + 无障碍名 + 所在分组 + 点下去干什么。
 *
 * [snippet] 仍保留原样（`**粗体**` 而不是 `****`）：插进去以后改掉中间那几个字就行，
 * 不用先猜这串符号是干嘛的。`Clear` 那一项用不到它，给空串即可（不用可空类型：
 * 那会让每个调用点都多一次 `?:` 判空，而它只有一个"没有模板"的用途）。
 */
private class EditorTool(
    val label: String,
    val icon: ImageVector,
    val group: ToolGroup,
    val snippet: String,
    val action: EditorToolAction = EditorToolAction.Insert,
)

/**
 * 工具栏的顺序照 discourse/fluxdo 的编辑器。头七项就是原来那七颗，**snippet 与顺序一字未改**
 * （粗体、斜体、标题、列表、引用、代码、链接）：常驻那一排照旧直接点，行为不变。
 *
 * 后面六项是 fluxdo `editor_tools.dart` 里本仓做得到的纯文本 Markdown：删除线、有序列表、
 * 行内代码之外的代码块、分隔线、表格骨架。图片/附件/上传全没放——本仓没有上传通道，
 * 点了只能弹一句"做不到"，那种按钮比没有更糟。
 *
 * 图标一律走 `Icons.Rounded.*`（Material Symbols Rounded，与 fluxdo 同一套）：
 * （全是 `Icons.Rounded.*`：同一套 Material Symbols，笔画粗细一致，16dp 下也不会糊。
 * 两套粗细）。`bold / italic / heading / bulletList / quote / strikethrough / orderedList /
 * codeBlock / horizontalRule / table` 是本仓新补的，FlClash 原版没有写作工具；
 * `code / link` 用的是它原有的那两个。
 */
private val EDITOR_TOOLS = listOf(
    EditorTool("粗体", Icons.Rounded.FormatBold, ToolGroup.Format, "**粗体**"),
    EditorTool("斜体", Icons.Rounded.FormatItalic, ToolGroup.Format, "*斜体*"),
    EditorTool("删除线", Icons.Rounded.FormatStrikethrough, ToolGroup.Format, "~~删除线~~"),
    EditorTool("标题", Icons.Rounded.Title, ToolGroup.Format, "## 小标题"),
    EditorTool("有序列表", Icons.Rounded.FormatListNumbered, ToolGroup.Format, "1. 一项"),
    EditorTool("列表", Icons.Rounded.FormatListBulleted, ToolGroup.Format, "- 一项"),
    EditorTool("链接", Icons.Rounded.Link, ToolGroup.Insert, "[文字](https://)"),
    EditorTool("引用", Icons.Rounded.FormatQuote, ToolGroup.Insert, "> 引用"),
    EditorTool("代码", Icons.Rounded.Code, ToolGroup.Insert, "`代码`"),
    EditorTool("代码块", Icons.Rounded.DataObject, ToolGroup.Insert, "\n```\n代码\n```"),
    EditorTool("分隔线", Icons.Rounded.HorizontalRule, ToolGroup.Insert, "\n---\n"),
    EditorTool(
        "表格",
        Icons.Rounded.TableChart,
        ToolGroup.Insert,
        "\n| 列 1 | 列 2 |\n| --- | --- |\n| 内容 | 内容 |",
    ),
    EditorTool("清空正文", Icons.Rounded.Delete, ToolGroup.Editing, "", EditorToolAction.Clear),
)

/** 工具栏一项点下去干什么：[Insert] 往游标处插 [EditorTool.snippet]，[Clear] 清空正文。 */
private enum class EditorToolAction { Insert, Clear }

/**
 * 分组标题的行顺序：跟着 [ToolGroup] 的枚举顺序走，不在别处再写一份字面量列表。
 * 用 `entries` 而不是手写 `listOf(Format, Insert, Editing)`：以后插一组自动进面板。
 */
private val TOOL_GROUPS: List<ToolGroup> = ToolGroup.entries

/**
 * 常驻排里那几颗（fluxdo `composer_tool_style.dart` 的固定按钮表）。
 *
 * 就是原来那七颗、原来那个顺序：粗体、斜体、标题、列表、引用、代码、链接。用 `take(7)`
 * 从 `EDITOR_TOOLS` 里切而不是另抄一份 `listOf(...)` —— 常驻排与面板是同一份表的两个视图，
 * 抄两份就会出现"面板里改了 snippet、下面那排没改"这种对不上的事。
 */
private val PINNED_TOOLS: List<EditorTool> = EDITOR_TOOLS.take(7)

/**
 * 面板里各组的工具：从 [EDITOR_TOOLS] 里按 [EditorTool.group] 切出来。
 *
 * 与 [PINNED_TOOLS] 一样"切"而不是"抄"：一颗工具的分组改一处，面板和常驻排一起跟着动。
 * 放在顶层 `private val` 而不是面板里每次重组都 `filter` 一遍——这些都是常量。
 */
private val TOOLS_BY_GROUP: Map<ToolGroup, List<EditorTool>> =
    TOOL_GROUPS.associateWith { group -> EDITOR_TOOLS.filter { it.group == group } }

/** 置顶标记的颜色：与「认同」同一个绿，页面上一眼是"被挑出来的那条"。 */
private val PostPinned = Color(0xFF2FA84F)

/**
 * 参与者头像的叠压步长：`size - 8` = 21 - 8 = **13dp**（fluxdo `topic_participant_avatars.dart:53`）。
 *
 * 重叠量固定是 8dp，头像一变步长就跟着变 —— 所以写成常量而不是"写死 13"，
 * 改直径时不用回来再算一遍。
 */
private val PARTICIPANT_STEP = 13.dp

/** 阅读时长只在超过这个分钟数时才报：短帖报"1 分钟阅读"没有信息量。 */
private const val READ_MINUTES_MIN = 3

/**
 * 名字行右侧那条引用指示条里，名字最宽多少。
 *
 * 参考实现是 `ConstrainedBox(maxWidth: 80)`（`post_header.dart:472-473`），按那边 11sp 的字号
 * 大约装得下 8~9 个中文字。本仓同样是 11sp，但整条还要带上「回复 #12 · 」这一截，
 * 所以这里给名字单独留 96dp：再多就会把左边的作者名挤成省略号。
 */
private val INLINE_REPLY_NAME_MAX = 96.dp

// ---- 楼层内「直接回复」的几何（照 fluxdo `embedded_posts.dart:156-174` 的 EmbeddedReplyMetrics） ----

/** 头像列宽 = 父楼头像直径（40），竖线画在它的中线上。 */
private val NESTED_GUTTER = 40.dp

/** 头像列到文字的距离 = `PostHeader` 里头像与名字的间距。 */
private val NESTED_GAP = 12.dp

/** 嵌套回复的头像直径（源码 `avatarRadius = 14`）。 */
private val NESTED_AVATAR = 28.dp

/** 竖线粗细（源码 `railWidth = 1.5`）。 */
private val NESTED_RAIL = 1.5.dp

/** 竖线的横坐标 = `gutter / 2`。 */
private val NESTED_RAIL_CENTER = 20.dp

/** 竖线在头像上下断开的那点缝（源码 `railGap = 3`）。 */
private val NESTED_RAIL_GAP = 3.dp

/** 单条回复自己的上下内边距（源码 `verticalPadding = 10`）。 */
private val NESTED_TILE_PAD = 10.dp

/** 收起按钮与回复头像同尺寸（源码 `buttonSize = avatarRadius * 2`）。 */
private val NESTED_BUTTON = 28.dp

/** 嵌套正文 13sp / 1.4 = 18sp（源码 `_embeddedFontSize = 13` / `_embeddedLineHeight = 1.4`）。 */
private val NESTED_FONT = 13.sp
private val NESTED_LINE = 18.sp

/** 列表上方的四个口径：最新（后端原序）、热门（赞与回复算出来的热度）、收藏、我的。 */
private enum class PostBoard(val label: String) {
    Latest("最新"),
    Hot("热门"),
    Saved("收藏"),
    Mine("我的"),
}

/**
 * 「社区帖子」这一页：列表 → 帖子详情 → 编辑器，三种形态在同一个 composable 里切。
 *
 * 样子照 fluxdo（Linux.do 第三方客户端）的帖子页取的：
 *
 *  1. **列表** —— 顶上「最新 / 热门 / 我的」+ 分类横向筛选，下面是卡片：左边作者字母头像，
 *     右边标题、分类与标签、摘要、一行元信息（时间、回复数、赞数）。点卡片进详情。
 *  2. **详情** —— 整页而不是对话框：主楼与回帖按楼层排（楼主 / #1 / #2…），正文按 Markdown
 *     渲染，底下一个吸底的回复栏。作者本人能删。
 *  3. **编辑器** —— 标题 + 分类 + 标签 + Markdown 正文（可切预览），输入停顿自动存草稿。
 *
 * 身份：写操作只认后端从 token 认出来的身份（见 `PostStore`），草稿按同一个身份分开存
 * （见 `PostDraftStore`）。[authorId] 为空时这一页只读：点「写帖子」会说"还没认出身份"。
 *
 * @param platform 用来提示（[CantaPlatform.showMessage]）。
 * @param store 帖子数据层；null 时列表只显示一句说明，不装作有数据。
 * @param drafts 草稿；null 时编辑器照样能写，只是关掉就没了。
 * @param author 署名：发帖、回帖时一起发上去，只当名字显示，不当身份。
 * @param authorId 身份码：草稿按它分开，删除按钮也只在作者本人的帖子上出现。
 * @param onNavigateBack 顶栏返回；null 表示这是底栏上的一级页面，顶栏不画返回箭头。
 * @param loadPlans 编辑器里「分享方案」要列的方案；null 时那一格不画。它在点开时才拉，
 *   不是为了发一条帖子先等一次网络——写帖的人大多数不贴方案。
 */
/**
 * 帖子页顶上的常驻工具栏，逐项照 fluxdo `topics_page.dart:1267-1323` 的 `_buildToolbar` 与
 * `widgets/topic/topic_filter_menu.dart` 的 `titleStyle` 形态：
 *
 * - 高 **48dp**（`_toolbarRowHeight = 48`），永不折叠
 * - 左：**8dp 留白 + ☰**（三条等长横线，`Symbols.menu_rounded`；打开分类侧栏）
 * - 中：**筛选用页面标题的样子**——「最新」**17sp / w700 / onSurface**（不是 13sp 的下拉小字），
 *   右侧一个 **20dp 圆**里住着 chevron：底 `surfaceContainerHighest` 60%，菜单开着换
 *   `secondaryContainer` 并翻转 180°（`topic_filter_menu.dart:298-343`）
 * - 右：**开源周榜（可选）→ 创建话题（可选）→ 🔍 → 8dp**。fluxdo `topics_page.dart:1307-1318`
 *   的右簇是「搜索落位格（`_SearchSlotSpacer`，胶囊收拢成图标时的落位）→ 🔔 → 8dp」；
 *   本仓胶囊常驻在工具栏**下面一行**（`_capsuleRowHeight = 48`）、没有 morph，所以那个落位格在这里
 *   没有意义，改为直接放一颗**真的搜索入口**：点了把焦点交给下面那颗常驻胶囊（`searchFocus`），
 *   省掉"先找胶囊再点"的一步。图标 24 / 触控 40，与 fluxdo 的 🔔 同一口径（`compact` 只收触控不缩 glyph）。
 *   通知位（🔔）：本仓**没有通知中心**（没有通知数据源、也没有面板），放一颗点了没处去的铃铛
 *   是在骗点击，所以跳过——不是漏做。
 * - 计数只跟两种口径走：最新 = 新帖 + 未读，未读 = 未读（`_countForFilter`）；其余口径不挂数字。
 *
 * 为什么自己画一条而不是用 `ScreenTopBar`：那个是 72dp、标题居中，而 fluxdo 这里是 **48dp、
 * 左边图标 + 标题左对齐**。高度这件事不能靠 padding 硬凑——它决定了列表第一屏能露出几条。
 *
 * 开了分类侧栏（见 [PostCategoryDrawer]）以后，☰ 变成强调色，中间那行接一句「· 分类」/「· #标签」：
 * 抽屉一关，人得看得出列表是按什么筛的，否则会以为帖子少了。
 */
@Composable
private fun PostsToolbar(
    board: PostBoard,
    category: String,
    tag: String,
    newCount: Int,
    menuOpen: Boolean,
    onOpenCategories: () -> Unit,
    onOpenFilters: () -> Unit,
    /** 开源周榜入口；null 时不画那颗按钮。 */
    onOpenWeeklyRank: (() -> Unit)? = null,
    /**
     * 「创建话题」那颗图标；null 时不画。
     *
     * 为什么顶栏也要有一个：FAB 是"展开菜单再点第二下"的两段动作，而 fluxdo 的首页工具栏
     * 右端就是一颗直达的创作按钮——新建话题是这一页最常用的写操作，不该只能从右下角绕进去。
     * 两颗入口同时存在不是重复：FAB 那三个格（草稿 / 写新帖 / 刷新）仍然只有它给得了。
     */
    onCompose: (() -> Unit)? = null,
    /**
     * 右端那颗搜索图标：null 时不画（胶囊自己就在下面一行，没有胶囊的形态不该有这颗入口）。
     *
     * 为什么顶栏要有一颗：fluxdo 的顶栏右簇本来就是「搜索落位格 + 🔔」，本仓没有 morph，
     * 那颗落位格的位置就换成一颗真的搜索按钮——手指在右上一伸手就能开始搜，
     * 不必先去下面那条胶囊上对准。
     */
    onSearch: (() -> Unit)? = null,
    /** 顶栏底色：与它所在页面的底一致（见调用点注释）。 */
    background: Color = Color.Unspecified,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(48.dp)
            .background(background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(8.dp))
        // ☰ 分类入口：打开 fluxdo 那条从左侧滑出的分类侧栏（见 [PostCategoryDrawer]）。
        // 图标必须是**三条等长横线**：`appsList` 那种"左三块右三线"的格子看着像应用列表。
        TopBarIconButton(
            icon = Icons.Rounded.Menu,
            label = "浏览分类",
            active = category.isNotBlank() || tag.isNotBlank(),
            onClick = onOpenCategories,
        )

        // 「最新」+ 活指示器：**页面标题字号**（17sp / w700 / onSurface），不是下拉小按钮的 13sp。
        // 左对齐、占满中间；窄屏时文字自己省略。
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onOpenFilters)
                .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildString {
                    append(board.label)
                    if (newCount > 0) append(" ($newCount)")
                    // 当前按哪个分类/标签筛，写在这里：抽屉关了以后，列表为什么短了要有交代。
                    if (tag.isNotBlank()) {
                        append(" · #$tag")
                    } else if (category.isNotBlank()) {
                        append(" · $category")
                    }
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(6.dp))
            // chevron 住在 20dp 的色调小圆里：开着换 `secondaryContainer` 并翻转 180°。
            // 数值照 `topic_filter_menu.dart:320-343`：
            //   - 圆底色 `AnimatedContainer` **220ms / easeOut**
            //   - 翻转 `AnimatedRotation` **340ms / easeOutBack**（带一点过冲回弹，
            //     这正是它看起来"啪"一下翻过去的原因；线性 tween 是死板的匀速）
            val turn by animateFloatAsState(
                targetValue = if (menuOpen) 180f else 0f,
                animationSpec = tween(durationMillis = 340, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)),
                label = "filter-chevron-turn",
            )
            val orb by animateColorAsState(
                targetValue = if (menuOpen) {
                    scheme.secondaryContainer
                } else {
                    scheme.surfaceContainerHighest.copy(alpha = 0.6f)
                },
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "filter-chevron-orb",
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(orb),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(15.dp).rotate(turn), tint = if (menuOpen) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
            }
        }

        // 右端：开源周榜（有数据层才画）→ 「创建话题」→ 搜索 → 8dp 留白。
        // fluxdo 右簇的顺序是「落位格（最左，所以贴在标题右侧）→ 🔔 → 8dp」，这里把那个
        // 只在 morph 里有意义的落位格换成**真按钮**：搜索紧贴右缘（原 🔔 的位置），
        // 「创建话题」是本仓多出来的一颗，排在搜索左边，免得它把搜索挤离右缘。
        if (onOpenWeeklyRank != null) {
            WeeklyRankIconButton(onClick = onOpenWeeklyRank)
        }
        // 「创建话题」：一颗直达的写作入口，照 fluxdo 首页工具栏右端的创作按钮。
        // 图标用 [Icons.Rounded.Edit]（笔），和 FAB 菜单里「写新帖」同一颗——同一件事在哪儿都是同一件图标。
        // 身份没认出来时（[onCompose] 由调用方传 null）不画：那颗按钮点了也只能说"还没认出身份"。
        if (onCompose != null) {
            TopBarIconButton(
                icon = Icons.Rounded.Edit,
                label = "创建话题",
                onClick = onCompose,
            )
        }
        // 搜索：把焦点交给工具栏下面那颗常驻胶囊（[onSearch] 由调用方绑 `searchFocus`）。
        // `Icons.Rounded.Search` 是官方 Material Symbols，不像自绘 glyph 那样有 `fill = 0f` 下不显示的坑。
        if (onSearch != null) {
            TopBarIconButton(
                icon = Icons.Rounded.Search,
                label = "搜索话题",
                onClick = onSearch,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
    }
}

/**
 * 顶栏上的一个图标按钮。
 *
 * 触控框 40dp、图标 24dp（fluxdo 的 glyph 口径）：24 是这套图标在别处的默认大小，缩到 20 会
 * 和底栏那一排对不上。
 *
 * [active] 是"这个入口现在管着事"：☰ 在按分类/标签筛的时候换成强调色，不然一条纯灰的图标
 * 看不出列表的短是有原因的。
 */
@Composable
private fun TopBarIconButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            // `Icon` 没有 `size` 参数（它是 `FlGlyphIcon` 的），尺寸要走 `modifier`。
            modifier = Modifier.size(24.dp),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 首页右下角的创作入口，照 fluxdo 的 `TopicsFab`：一个圆形 FAB，点开往上弹出三项
 * **草稿 / 写新帖 / 刷新**（图标各 24dp），再点一次收起（图标变成 × ）。
 *
 * 为什么是 FAB 而不是顶栏文字：顶栏那一行在 fluxdo 里只放导航与筛选（☰ / 最新 / 🔍），
 * 「写」是一个**动作**，动作归右下角。文字按钮还占宽度，标题一长就被挤。
 */
@Composable
private fun PostsFab(
    open: Boolean,
    draftCount: Int,
    canWrite: Boolean,
    onToggle: () -> Unit,
    onNewPost: () -> Unit,
    onDrafts: () -> Unit,
    onRefresh: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 展开进度：0 = 收起（圆 + primaryContainer），1 = 展开（方形继续用 primary）。
    // 数值照 fluxdo 的 `M3eFabMenuButton`（`packages/m3e_ui/lib/src/m3e_fab_menu.dart:94-129`）：
    //   - **56×56**，收起态圆角 **16**、展开态是**正圆**
    //   - 底色 `primaryContainer` → 展开渐变成 `primary`，前景 `onPrimaryContainer` → `onPrimary`
    //   - 图标 **24**，展开时收到 **20**，并且从 ＋ 换成 ×
    //   - 菜单项间距 **4**（原来 10，太散）
    //   - 菜单项本身是 `primaryContainer` 的胶囊，**图标在右**、文字在左，内边距 24/8
    //   - 弹出时从 0.2 缩放到 1（淡入）
    //
    // 驱动它的**不是 tween 而是两根弹簧**（`m3e_fab_menu.dart:61` 的 `M3eMotion.fastSpatial`）：
    // 转形（圆角、底色、图标尺寸）走 ζ=0.6 / stiffness=800 的空间弹簧——欠阻尼，所以
    // 展开到头时会有一点点回弹；透明度走 ζ=1.0 / stiffness=3800 的效果弹簧——临界阻尼、
    // 快得多，只要不抖。上一版写的是 240ms 匀速 tween，展开过程是"匀速变圆"，没有那股回弹。
    val spatial = spring<Float>(dampingRatio = 0.6f, stiffness = 800f)
    val expand = animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = spatial,
        label = "posts-fab-expand",
    )
    val tint = expand.value
    val fill = androidx.compose.ui.graphics.lerp(scheme.primaryContainer, scheme.primary, tint)
    val ink = androidx.compose.ui.graphics.lerp(scheme.onPrimaryContainer, scheme.onPrimary, tint)
    // 圆角 16 → 正圆（28 = 56/2），跟着进度插值。
    val corner = RoundedCornerShape((16f + (28f - 16f) * tint).dp)

    // 间距 4：`M3eFabMenu` 的 `_itemSpacing`（原写 10，太散）。**必须是 4** ——
    // 之前这段注释写着 4，代码里却没有 `verticalArrangement`，实际间距是 0，菜单项贴着排。
    //
    // 三个菜单项**不套** `if (open)`：动画要能播退出就必须一直留在树上，由 AnimatedVisibility
    // 自己按 `visible` 决定进出。外面再包一层 `if` 的话，`open` 变 false 的那一帧整棵子树就被
    // 摘掉了，`exit` 规格根本轮不到执行——按下去会"啪"地消失。
    Column(
        horizontalAlignment = Alignment.End,
        // 收起态不能留 4dp：三个 AnimatedVisibility 收起后高度归零，但 `spacedBy` 算的是
        // "孩子之间"，于是 FAB 上方永远空出两格 4dp 的缝。展开时才上间距。
        verticalArrangement = if (open) Arrangement.spacedBy(4.dp) else Arrangement.Top,
    ) {
        // 弹出：每项从 0.2 缩放到 1 并淡入，收起时反向（`m3e_fab_menu.dart` 的 `_buildItem`）。
        FabAction(
            visible = open,
            icon = Icons.Rounded.Refresh,
            label = "刷新",
            onClick = onRefresh,
        )
        if (canWrite) {
            // 顺序照 `M3eFabMenu`：离 FAB 最近的是"写新帖"（主动作），草稿在它上面一格。
            FabAction(
                visible = open,
                icon = Icons.Rounded.Edit,
                label = "写新帖",
                onClick = onNewPost,
            )
            FabAction(
                visible = open,
                icon = Icons.Rounded.Drafts,
                label = if (draftCount > 0) "草稿 ($draftCount)" else "草稿",
                onClick = onDrafts,
            )
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(corner)
                .background(fill)
                .clickable(role = Role.Button, onClickLabel = if (open) "收起" else "发帖", onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            // ＋ / × 走自绘：这颗 FAB 两态要同一个族，而 Material 的 Add/Close 笔画粗细
            // 两态用同一套 `Icons.Rounded`（Close / Add），缩放到 20dp 仍是同一款笔画。
            Icon(Icons.Rounded.Close.takeIf { open } ?: Icons.Rounded.Add, null, Modifier.size((24f - 4f * tint).dp), tint = ink)
        }
    }
}

/**
 * FAB 弹出来的一项：图标 + 一行字，都压在一块圆角底上。
 *
 * [visible] 走 [AnimatedVisibility]：弹出时从 0.2 缩放到 1 并淡入，收起时反向。
 * 调用方**不要**再在外面包 `if`——包了就没有退出动画（见 [PostsFab] 那段注释）。
 *
 * 进场用两根弹簧而不是 tween，和 FAB 主体同一套（`m3e_fab_menu.dart:108-109`）：
 * 缩放走 `fastSpatial`（ζ=0.6、k=800，欠阻尼带回弹），透明度走 `fastEffects`
 * （ζ=1.0、k=3800，临界阻尼、快得多）。用 220ms 匀速 tween 的话，菜单项是"匀速长出来"，
 * 而 FAB 自己已经带上了回弹 —— 一动一静，看着像两个组件。
 */
@Composable
private fun FabAction(visible: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = spring(dampingRatio = 1f, stiffness = 3800f),
        ) + scaleIn(
            initialScale = 0.2f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        ),
        exit = fadeOut(
            animationSpec = spring(dampingRatio = 1f, stiffness = 3800f),
        ) + scaleOut(
            targetScale = 0.2f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        ),
    ) {
        val scheme = MaterialTheme.colorScheme
        Row(
            modifier = Modifier
                // 顺序是 `clip → background → clickable → padding`：背景必须画在点击层**下面**，
                // 反过来（先 clickable 再 background）背景会盖住涟漪，和抽屉、全仓其它胶囊都反着。
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(50))
                .background(scheme.primaryContainer)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 菜单项：`primaryContainer` 胶囊、**图标在右、文字在左**（照 `M3eFabMenuItem`
                // 的 `iconAlignment: end`），与那颗展开成 primary 的 FAB 是同一族颜色。
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimaryContainer,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = scheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun PostsScreen(
    platform: CantaPlatform,
    store: PostStore?,
    drafts: PostDraftStore?,
    author: String,
    authorId: String,
    onNavigateBack: (() -> Unit)? = null,
    /** 已读痕迹与收藏；null 时列表不画未读点、详情里也没有收藏按钮。 */
    reads: PostReadStore? = null,
    /**
     * 详情页的「上次读到哪一屏」：`(item, offset)`，见 [PostPositionStore]。
     *
     * 默认 null —— 不接它的时候这一页的行为与没有这个功能时**逐帧相同**：不恢复、不写盘，
     * `LazyColumn` 的 item 序列、跳楼、只看此人、续读条一个都没变。接上之后只有两件事发生：
     * 首次成功加载这条帖子后滚回记录的位置，以及停手 400ms 记一次新位置。
     *
     * 为什么不做成必传：构造 store 的那一侧（`CantaAppDependencies`）与这一页是两个改动面，
     * 参数有默认值就允许两边各自落地、谁先合都不会把对方编译打断。
     */
    positions: PostPositionStore? = null,
    loadPlans: (suspend () -> List<CommunityPlan>)? = null,
    /**
     * 周榜的数据层：卡片上那前三名与「抓取于…」都读它盘上那一份，不发请求（见 [WeeklyRankRepository]）。
     * null 时卡片不画。
     */
    weeklyRank: WeeklyRankRepository? = null,
    /**
     * 列表顶上那张「GitHub AI 开源周榜」卡片点开的去处；null 时那张卡片不画。
     *
     * 卡片放在列表里而不是挂在底栏：一周出一期的东西不值得占一格底栏，但它在"帖子"这一页
     * 最上面，看得见、点得到，也不打断列表本身。
     */
    onOpenWeeklyRank: (() -> Unit)? = null,
    /**
     * 名字与头像设过了没有：没设过就发不了帖（见 [IdentitySetup]）。
     *
     * 只挡「发」，不挡「写」：草稿照旧能存，发的时候按钮是灰的并说清差哪一步。
     */
    setupGate: IdentitySetup = IdentitySetup.UNSET,
    /**
     * 一进来就打开编辑器（写新话题），而不是先画列表。
     *
     * 给独立路由 `Screen.PostEditor` 用：那一页从「帖子」里单独开出来、不挂底栏，
     * 进来唯一的目的就是写。为 false 时是原来的 [io.github.samolego.canta.ui.navigation.Screen.Posts]：
     * 列表起步，点「发帖」才在页内换成编辑态。
     *
     * 编辑器本身、草稿、发布逻辑全都还是这一份 —— 这里只是决定「起步停在哪一格」，
     * 所以两条路不会写出两种行为。
     */
    openEditorOnEnter: Boolean = false,
    /**
     * 编辑态结束（取消或发成功）时回调。
     *
     * 独立那一页（[openEditorOnEnter] = true）用它把自己弹回列表：那一页存在的理由就是写，
     * 写完了留在原地会是一个空的编辑器。为 null 时是原来的页内切换，什么都不用做。
     */
    onEditorClosed: (() -> Unit)? = null,
    /**
     * 「创建话题」/ FAB 的「写新帖」要去的去处；null 时退回页内切换（老行为）。
     *
     * 传进来的话，这一页就只负责列表：点发帖交给外面导航到 `Screen.PostEditor`，
     * 那一页不挂底栏。带参数的是「从某条草稿继续写」，null 是写新的。
     */
    onOpenCompose: ((PostDraft?) -> Unit)? = null,
    /**
     * 一进来就打开这条帖子的详情，而不是先画列表。
     *
     * 给独立路由 `Screen.PostDetail` 用：**那一页不挂底栏**（它不在 `BOTTOM_BAR_ROUTES`
     * 里）。fluxdo 的 `TopicDetailPage` 也是独立一页（`Navigator.push`，全屏、无底栏），
     * 而本仓原来把详情做成 [PostsScreen] 内部的状态切换 —— [PostsScreen] 挂在底栏那一格上，
     * 于是"读一条帖"时底下还压着一排无关的 Tab。
     *
     * 为 0 时是原来的 [io.github.samolego.canta.ui.navigation.Screen.Posts]：列表起步，
     * 点一条才在页内换成详情。详情本身、回帖、点赞、引用全还是这一份实现。
     */
    openThreadOnEnter: Long = 0L,
    /**
     * 详情关闭（顶栏返回）时回调。
     *
     * 独立那一页（[openThreadOnEnter] > 0）用它把自己弹回列表：那一页存在的理由就是读，
     * 没有列表可退，退回去才是对的。为 null 时是原来的页内切换，什么都不用做。
     */
    onThreadClosed: (() -> Unit)? = null,
    /**
     * 列表里点一条帖子要去的去处；null 时退回页内切换（老行为）。
     *
     * 传进来的话，这一页只负责列表：点一条交给外面导航到 `Screen.PostDetail/{id}`，
     * 那一页不挂底栏。
     */
    onOpenThread: ((CommunityPost) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    /** 草稿挂在能认出来的身份上：身份码有了就用它，没有才退回署名。 */
    val who = remember(authorId, author) { authorId.ifEmpty { author } }
    val canWrite = authorId.isNotBlank()
    // 双击底栏「帖子」那一格：这一页的列表回顶部。
    // 位置带记忆（见 [rememberRememberedListState]）：冷启动再进来还停在原来那条帖子。
    val postListState = rememberRememberedListState("posts")
    ScrollToTopEffect(postListState)

    // 这一屏上一次画出来的帖子列表（见 [ScreenCache]）：进来先画它，网络回来再顶上去。
    var posts by remember {
        mutableStateOf(ScreenCache.get<List<CommunityPost>>(POSTS_CACHE_KEY).orEmpty())
    }
    var loading by remember { mutableStateOf(posts.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    /** 还有没有下一页：翻到底之后「加载更多」就收起来，别让人一直点。 */
    var hasMore by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    /** 发帖、回帖、点赞、删帖之后 +1：只有它变了才重新拉。 */
    var reloadKey by remember { mutableIntStateOf(0) }
    /** 正在进行的那一件事：`like:7` / `reply:7` / `delete:7`。 */
    var busyKey by remember { mutableStateOf<String?>(null) }
    /**
     * 已读 / 收藏变了就 +1。
     *
     * 这两件事存在盘上（见 [PostReadStore]），不是 Compose 状态；没有这个计数器，收藏完列表
     * 不会重算，人点了"收藏"却看不到它跑到收藏栏里。
     */
    var readsVersion by remember { mutableIntStateOf(0) }

    // ---- 列表筛选 ----
    var board by remember { mutableStateOf(PostBoard.Latest) }
    var categoryPick by remember { mutableStateOf("") }
    /** 按标签筛：空串表示不按标签。标签页在分类侧栏里（见 [PostCategoryDrawer]）。 */
    var tagPick by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    /** 盘上这个身份的草稿，最近改过的在前。首帧同步读一次（`read()` 是同步的）。 */
    var draftList by remember(who) {
        mutableStateOf(if (drafts != null) drafts.drafts(who) else emptyList())
    }

    // ---- 编辑器 ----
    // 独立那一页（`Screen.PostEditor`）进来就是编辑态；「帖子」自己那一页从列表起步。
    var composing by remember { mutableStateOf(openEditorOnEnter) }
    /** 预览开关：和「写」「改文字」一样放在整页状态里，顶栏那个按钮要读它。 */
    var previewing by remember { mutableStateOf(false) }
    /** 上一次拉列表时见过的最大帖子 id：用来算「有几个新的或更新的话题」。 */
    var seenMaxId by remember { mutableStateOf(0L) }
    /** 这一趟多出来的新帖数（横幅上的数字）；点一下刷新后归零。 */
    var freshCount by remember { mutableIntStateOf(0) }
    /** 顶栏「最新 ▾」的下拉开合。 */
    var filterMenuOpen by remember { mutableStateOf(false) }
    /** 左侧分类侧栏（☰）的开合。侧栏内部拖到关闭时会自己把它收回 false。 */
    var drawerOpen by remember { mutableStateOf(false) }
    /** 右下角 FAB 的展开状态（草稿 / 写新帖 / 刷新）。 */
    var fabOpen by remember { mutableStateOf(false) }
    /** 搜索胶囊的焦点。胶囊常驻在列表上方一行，这一页没有「点图标跳进去」的入口。 */
    val searchFocus = remember { FocusRequester() }
    /** 正在编辑的那篇草稿的本地 id；空串表示没在编辑。 */
    var editorId by remember { mutableStateOf("") }
    /**
     * 正在**改**的那条已发布帖子的 id；0 表示这次是新建。
     *
     * 和 [editorId] 是两回事：那个是本地草稿的键（盘上的文件），这个是后端那一行。
     * 新建时它是 0，发布走 `post_add`；>0 时发布走 `post_update`，而且**不存草稿** ——
     * 改的是线上那条，草稿在这里没有意义（中途退出就该放弃改动，回详情看原样）。
     */
    var editingPostId by remember { mutableStateOf(0L) }
    var title by remember { mutableStateOf("") }
    /**
     * 正文：`TextFieldValue` 而不是 `String`。
     *
     * 差别只有一个，但很要命：**游标位置**。工具栏那七个 Markdown 按钮要插在"我正在写的地方"，
     * 而 `String` 里没有这个概念，上一版只能往末尾追加，人得自己把那段搬上去。
     *
     * 落盘与发文时一律取 `body.text`（草稿存储仍是 `String`，见 [PostDraft]）。
     */
    var body by remember { mutableStateOf(TextFieldValue("")) }
    /** 分类：空串表示没选。候选见 [POST_CATEGORIES]，后端原样存。 */
    var category by remember { mutableStateOf("") }
    /** 已经成形的标签（chip）。 */
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    /** 标签输入框里还没被逗号/空格切开的那一截。 */
    var tagInput by remember { mutableStateOf("") }
    /** 最后一次自动保存的时刻（epoch 毫秒）；0 表示这次还没存过。 */
    var savedAt by remember { mutableStateOf(0L) }
    /**
     * 目录面板开着没有（[PostTocSheet]，入口在详情顶栏的「更多」菜单里）。
     *
     * 状态放在这一层而不是 `PostThreadPane` 里：入口在顶栏（`DetailMoreMenu`），
     * 而 `listState`（面板要用它算"我在第几层"）在 `PostThreadPane` 里 —— 一边一半，
     * 只能把开关提上来。关帖时一并收掉，免得下次点开别的帖子面板还盖在上面。
     */
    var tocOpen by remember { mutableStateOf(false) }
    /**
     * 帖内搜索面板开着没有（[PostSearchSheet]）。
     *
     * 与 [tocOpen] 同一套理由：入口在顶栏「更多」里，面板要用 `comments` 与 `listState`
     * （只有 `PostThreadPane` 拿得到），所以开关提到这一层，面板本体画在 pane 里。
     */
    var searchOpen by remember { mutableStateOf(false) }
    /**
     * 「跳到第几楼」对话框开着没有（[PostJumpDialog]）。
     *
     * 与 [tocOpen] / [searchOpen] 同一套理由：入口在顶栏「更多」，而面板要知道"总共有几层"
     * （`comments.size + 1`）与怎么滚（`listState`），两者都只有 `PostThreadPane` 拿得到。
     */
    var jumpOpen by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    var editorError by remember { mutableStateOf<String?>(null) }
    /**
     * 「分享方案」的选择器：开着没有、那一刻拉到的方案、拉的时候出错没有。
     *
     * 方案列表不跟帖子列表一起拉：写帖的人多数不贴方案，为一次可能不发生的点击先等一次网络不划算。
     * 拉到过就留在这一次会话里复用——方案不是一秒钟变一次的东西，点第二回不必再等一次；
     * 重开编辑器只把选择器关掉（见 [openEditor]），不留一个悬着的对话框。
     */
    var sharePickerOpen by remember { mutableStateOf(false) }
    var sharePlans by remember { mutableStateOf<List<CommunityPlan>>(emptyList()) }
    var shareLoading by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }

    // ---- 详情 ----
    /** 打开的那条帖子的 id；0 表示还在列表上。 */
    // 独立详情页（`Screen.PostDetail`）起步就带着目标帖；列表页这里是 0。
    var openId by remember { mutableStateOf(openThreadOnEnter) }
    /**
     * 这一次打开详情时，「标记已读之前」这条帖子算不算有新的回帖。
     *
     * `null` = 还没问过（刚打开、正文还没拉回来）；`false` / `true` = 问过的那一个答案。
     *
     * 为什么要在**打开之前**问一次、而不是每次重组都问一遍：打开详情的那个 `LaunchedEffect`
     * 拉回正文之后紧接着就 `markRead(fresh.post.id, fresh.post.comments)`，把记下来的回帖数
     * 对齐到当前值；对齐之后 [PostReadStore.isUnread] 对这条帖子恒为 false ——
     * "比上次看到的多"这个事实只存在于对齐之前的那一瞬间，不接住它就永远读不到
     * 「上次读到这儿」。
     *
     * 只接一个布尔量、不接数字：`PostReadStore` 只公开 `hasHistory` / `isUnread` / `markRead`
     * （`Entry` 与 `blob()` 都是 private），没有"这条帖子记着多少条回帖"这种读法；在本地再存
     * 一份 mark 就是第二份真相，写读两条路早晚会分叉。所以续读条按"有没有增量"处理 ——
     * 具体取舍见 [ThreadResumeBar] 的 KDoc。
     *
     * 判"有没有增量"要用**两次** `isUnread`：当前回帖数那一次答"比记下来的多不多"，
     * 传 0 那一次答"表里有没有这条帖子的记录"（只有没有记录时它对 0 也回答 true）。
     * 少了后一次，第一次读的帖子会和"有新回复"一样被判成 true。
     *
     * `remember(openId)`：换一条帖子、或者关掉回到 0 就重来一次，不会把上一帖的答案带过来。
     */
    var resumeUnread by remember(openId) { mutableStateOf<Boolean?>(null) }
    /**
     * 上次记下的回帖数（`PostReadStore.readMark`），与 [resumeUnread] 同一次读取带回来的。
     *
     * `null` = 没有精确记录（第一次读 / 被已读上限淘汰）→ 续读条只写「后面有新回复」。
     * 必须在 `markRead` 之前取：之后就被对齐成当前值了。
     */
    var resumeMark by remember(openId) { mutableStateOf<Int?>(null) }
    var thread by remember { mutableStateOf<PostThread?>(null) }
    var threadLoading by remember { mutableStateOf(false) }
    var threadError by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf("") }
    /**
     * 这一条回帖要挂到哪一楼下面（引用回复）。
     *
     * null 表示"不是回复某一楼"，按普通回帖发。为什么是整条 [PostComment] 而不是只存一个 id：
     * 回复栏胶囊要写「回复 #N · 名字」，名字得从这条上取（[PostComment.author]），楼层号要
     * 靠它在 `thread.comments` 里的位置算 —— 只存 id 就得每次再去列表里反查一遍。
     *
     * 生命周期只有一处清空点：发送成功之后（见 [sendReply]）与离开详情页时（[openThread] /
     * [closeThread]）。**不在引用后改主意时自动清**：写的人可能先引用了 A 又去引用 B，
     * 那一次点击会直接把 [quoteComment] 覆盖成 B —— 这就是最新意图。
     */
    var replyTo by remember { mutableStateOf<PostComment?>(null) }
    /** 二次确认要删的那条帖子；null 表示没有待确认的删除。 */
    var confirmTarget by remember { mutableStateOf<CommunityPost?>(null) }

    // 列表：第一次进来拉一页，之后每次 reloadKey 变动再拉。
    LaunchedEffect(store, reloadKey) {
        val current = store
        if (current == null) {
            loading = false
            refreshing = false
            return@LaunchedEffect
        }
        if (posts.isEmpty()) loading = true
        val fresh = current.list(0, POSTS_PAGE)
        if (fresh != null) {
            // 首页拿回来的条数就能说明还有没有下一页：不够一页就是到底了。
            hasMore = fresh.size >= POSTS_PAGE
            // 「有几个新的或更新的话题」按**这一趟新出现的 id** 算，而不是按未读数：
            // 未读是"我没看过"，新话题是"刚刚才出现"，两件事。第一次进来（seenMaxId 还是 0）
            // 不报——否则首屏每一条都算"新"。
            val maxId = fresh.maxOfOrNull { it.id } ?: 0L
            freshCount = if (seenMaxId <= 0L) {
                0
            } else {
                fresh.count { it.id > seenMaxId }
            }
            if (maxId > seenMaxId) seenMaxId = maxId
            // 就地合并：内容没变的那几条沿用旧实例，整表不重画；一个字节都没变时连状态写入都省掉。
            val merged = stableMerge(posts, fresh) { it.id }
            if (merged !== posts) {
                posts = merged
                // 空回答不写缓存：一次失败或者后端返回空，不该把已经看到过的帖子从本地抹掉，
                // 否则下次进来先画的是一张空表，看着就像「什么都没有」。
                if (merged.isNotEmpty()) ScreenCache.put(POSTS_CACHE_KEY, merged)
            }
            error = null
        } else {
            error = current.reason ?: "帖子没拉到，检查一下网络或代理。"
        }
        loading = false
        refreshing = false
    }

    // 详情：打开哪条拉哪条；回帖/点赞之后 reloadKey 变了也跟着刷一遍，数字与后端一致。
    LaunchedEffect(store, openId, reloadKey) {
        val current = store
        if (current == null || openId <= 0L) return@LaunchedEffect
        threadLoading = true
        threadError = null
        val fresh = current.get(openId)
        if (fresh == null) {
            thread = null
            threadError = current.reason ?: "这条帖子没拉到。"
        } else {
            thread = fresh
            // 先问「进来之前有没有新的回帖」，**再**标记已读：`markRead` 会把回帖数对齐到
            // 当前值，之后 `isUnread` 对这条帖子恒为 false（见 [resumeUnread] 的注释）。
            // 只在这条帖子这一趟的第一次成功加载时问：`reloadKey` 变动会再跑一遍这个 effect，
            // 那时手里的答案已经是"打开那一刻"的，不该被覆盖成 false。
            if (resumeUnread == null) {
                // 三个问题，缺一个都算"没有增量"：
                //  1. 这台手机上有没有任何已读记录（`hasHistory`）：全新装上时列表都不画未读点，
                //     详情里也不该冒出一条"上次读到这儿"；
                //  2. **这条帖子有没有被读过**：拿 `isUnread(id, 0)` 当探针 —— 表里没有这条时
                //     它对任何数都回答 true，而 `0 > mark.comments` 对任何真实记下来的数都不成立，
                //     所以这里回答的正是"有没有记录"。第一次读的帖子靠这一步挡住
                //     （否则它会和"有新回复"一样被判成 true）；
                //  3. 现在的回帖数比记下来的多（`isUnread(id, 当前回帖数)`）：没有增量就不出现，
                //     `mark == comments.size` 时这一步就是 false。
                resumeUnread = reads != null &&
                    reads.hasHistory() &&
                    !reads.isUnread(fresh.post.id, 0) &&
                    reads.isUnread(fresh.post.id, fresh.post.comments)
                // 同一时刻、同样在 `markRead` 之前，把"上次记下的回帖数"也取下来：
                // 有了这个数，续读条才能写「上次读到 #N · 后面 M 条没看」并精确落到下一层；
                // 读晚了（`markRead` 之后）它会被对齐成当前值，M 恒为 0，那就什么都显示不出来。
                resumeMark = reads?.readMark(fresh.post.id)
            }
            // 已经读了正文，就把这条帖子此时的回帖数记下来：列表上的未读点因此灭掉，
            // 以后回帖变多再重新亮起来（"有新回复"）。
            if (reads != null && reads.markRead(fresh.post.id, fresh.post.comments)) {
                readsVersion++
            }
        }
        threadLoading = false
    }

    /** 加一个标签：去空、单个 20 字、最多 5 个、不许重复；不收的给一句提示。 */
    val addTag: (String) -> Unit = addTag@{ raw ->
        val tag = raw.trim()
        if (tag.isEmpty()) return@addTag
        if (tag.length > TAG_LIMIT) {
            platform.showMessage("单个标签最多 $TAG_LIMIT 字")
            return@addTag
        }
        if (tags.contains(tag)) return@addTag
        if (tags.size >= TAG_MAX) {
            platform.showMessage("标签最多 $TAG_MAX 个")
            return@addTag
        }
        tags = tags + tag
    }

    /**
     * 标签输入：遇到逗号、顿号、分号或空格就地切一个 chip 出来，最后一截没写完的留在输入框里。
     *
     * 就地切成 chip 而不是"发布时再解析"：写的人得能看见这一栏到底有几行标签、哪一行打错了，
     * 才有机会在按发布之前删掉它。
     */
    val onTagInputChange: (String) -> Unit = onTag@{ raw ->
        val text = raw.take(TAG_LIMIT * TAG_MAX + 64)
        var buffer = StringBuilder()
        text.forEach { ch ->
            if (isTagSeparator(ch)) {
                if (buffer.isNotEmpty()) {
                    addTag(buffer.toString())
                    buffer = StringBuilder()
                }
            } else {
                buffer.append(ch)
            }
        }
        tagInput = buffer.toString()
    }

    /*
     * 自动存草稿：键变了就重新计时，所以"一直在敲"不会写盘，手停下来 700 毫秒才写一次。
     *
     * 延时之后要再确认一次还在同一条草稿上：发布成功会把 `composing` / `editorId` 清掉，但那个
     * 协程可能还没被取消，照旧写下去就等于把刚发出去的帖子又存成了草稿。
     *
     * 分类与标签也进键里：只改分类、只加一个标签，同样是"手停下来就该记住"的改动。
     *
     * 键里用 `body.text` 而不是 `body` 本身：`TextFieldValue` 每次**光标移动**都会变成新实例，
     * 直接进键就等于"点一下正文也重新计时并写一次盘"——手没动，草稿却被反复覆盖。
     */
    LaunchedEffect(composing, editorId, editingPostId, title, body.text, category, tags, tagInput) {
        if (!composing) return@LaunchedEffect
        // 改帖不存草稿：改的是线上那条，写盘只会留下一条"看起来像新帖、其实是旧内容"的草稿，
        // 下次点「草稿」进来还以为自己在起草一篇新的。
        if (editingPostId > 0L) return@LaunchedEffect
        val draftStore = drafts ?: return@LaunchedEffect
        val id = editorId
        if (id.isEmpty() || who.isEmpty()) return@LaunchedEffect
        if (title.isBlank() && body.text.isBlank()) return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MS)
        if (!composing || editorId != id) return@LaunchedEffect
        val stamp = currentTimeMillis()
        draftStore.save(
            who,
            PostDraft(
                id = id,
                title = title,
                body = body.text,
                category = category,
                tags = tagsWithPending(tags, tagInput),
                updatedAt = stamp,
            ),
        )
        savedAt = stamp
        draftList = draftStore.drafts(who)
    }

    /**
     * 打开编辑器：[draft] 为 null 就是一篇新的（本地新 id，空标题空正文、没选分类、没标签）。
     *
     * 无论从哪条路进来，都先把 [editingPostId] 归零 —— 上一次改帖留下的 id 如果忘了清，
     * 接着点「写新帖」就会把新写的内容写进上一条帖子里。
     */
    val openEditor: (PostDraft?) -> Unit = edit@{ draft ->
        if (!canWrite) {
            platform.showMessage("还没认出身份，帖子记不上")
            return@edit
        }
        editingPostId = 0L
        editorId = draft?.id?.takeIf { it.isNotEmpty() } ?: currentTimeMillis().toString()
        title = draft?.title.orEmpty()
        body = TextFieldValue(draft?.body.orEmpty())
        category = draft?.category.orEmpty()
        tags = draft?.tags.orEmpty()
        tagInput = ""
        savedAt = draft?.updatedAt ?: 0L
        editorError = null
        // 从草稿或「写帖子」进来时不让上一次留下的选择器挡在编辑器前面。
        sharePickerOpen = false
        composing = true
    }

    /**
     * 改一条**已经发出去的**帖子：把它的标题/正文/分类/标签回填进编辑器，发布时走 `post_update`。
     *
     * 与 [openEditor] 的区别只有三处：不让 [onOpenCompose] 把它导到独立那一页（那一页是"写新的"，
     * 没有帖子 id 可带）、不产生本地草稿（改的是线上那条，中途退出就该丢掉改动）、
     * 发布走 update 而不是 add。
     */
    val editPost: (CommunityPost) -> Unit = edit@{ post ->
        if (!canWrite) {
            platform.showMessage("还没认出身份，改不了")
            return@edit
        }
        if (authorId.isBlank() || post.authorId != authorId) {
            platform.showMessage("只有作者本人能改")
            return@edit
        }
        editingPostId = post.id
        // 本地草稿 id 给一个不会被"发布成功后删草稿"删掉的临时值：这次编辑不存草稿，
        // 但自动保存那条 LaunchedEffect 仍然会跑，得让它有一条自己的键而不去覆盖任何真草稿。
        editorId = "edit-${post.id}"
        title = post.title
        body = TextFieldValue(post.body)
        category = post.category
        tags = post.tags
        tagInput = ""
        savedAt = 0L
        editorError = null
        sharePickerOpen = false
        composing = true
    }

    /**
     * 所有「去写点东西」的入口都走这里。
     *
     * 有 [onOpenCompose] 就把活交给外面（导航到独立那一页 `Screen.PostEditor`，不挂底栏），
     * 没有就还是页内切换。**新增入口也请走这个函数**，别直接调 [openEditor] ——
     * 直接调会绕过外面那条路由，于是同一个按钮在不同页面上行为不一样。
     */
    val startCompose: (PostDraft?) -> Unit = compose@{ draft ->
        val navigate = onOpenCompose
        if (navigate != null) navigate(draft) else openEditor(draft)
    }

    /** 取消：有内容就存成草稿并说一声；空的一次什么都不留。改帖时一律不存草稿。 */
    val cancelEditor: () -> Unit = cancel@{
        // 改帖：退出就是放弃这次改动，原帖一个字不动，也不留草稿（见 [editPost]）。
        if (editingPostId > 0L) {
            composing = false
            editingPostId = 0L
            editorId = ""
            platform.showMessage("已放弃修改")
            onEditorClosed?.invoke()
            return@cancel
        }
        val draftStore = drafts
        val hasText = title.isNotBlank() || body.text.isNotBlank()
        if (!hasText) {
            draftStore?.delete(who, editorId)
            draftList = draftStore?.drafts(who).orEmpty()
            composing = false
            editorId = ""
            // 独立那一页（`Screen.PostEditor`）存在的理由就是写；取消了就回去，
            // 别把人留在一条空编辑器上。页内切换时这里是 null，什么都不做。
            onEditorClosed?.invoke()
            return@cancel
        }
        if (draftStore != null && who.isNotEmpty()) {
            draftStore.save(
                who,
                PostDraft(
                    id = editorId,
                    title = title,
                    body = body.text,
                    category = category,
                    tags = tagsWithPending(tags, tagInput),
                    updatedAt = currentTimeMillis(),
                ),
            )
            draftList = draftStore.drafts(who)
        }
        composing = false
        platform.showMessage("已存为草稿")
        onEditorClosed?.invoke()
    }

    /**
     * 发布：新建走 `post_add`，改帖走 `post_update`；成功后关编辑器、刷新列表，
     * 失败就把后端那句话摆在编辑器里。
     */
    val publish: () -> Unit = pub@{
        val current = store
        // 名字/头像没设完：帖子也是发言，按钮已经是灰的，这里挡住回车那条路。
        if (!setupGate.ready) {
            editorError = setupGate.refusal
            return@pub
        }
        if (current == null) {
            editorError = "社区后端没连上，帖子没发出去。"
            return@pub
        }
        if (posting) return@pub
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            editorError = "标题不能空"
            return@pub
        }
        val draftId = editorId
        val editing = editingPostId
        // 输入框里那半截标签也一起发上去：写完了没按逗号的那一个，人也当它是标签。
        val finalTags = tagsWithPending(tags, tagInput)
        posting = true
        editorError = null
        scope.launch {
            if (editing > 0L) {
                val done = current.update(editing, cleanTitle, body.text, author, category, finalTags)
                posting = false
                // 这一趟回来时人可能已经离开编辑态了（按了返回 / 又写了别的）：
                // 那就只把结果说一声，不去清别人的状态，也不再弹一次页。
                // 判断照自动保存那条的守卫写法（见上面那条 LaunchedEffect）。
                if (!composing || editingPostId != editing) {
                    platform.showMessage(if (done) "已保存修改" else (current.reason ?: "没改上"))
                    return@launch
                }
                if (!done) {
                    editorError = current.reason ?: "没改上：检查一下网络再试"
                    return@launch
                }
                // 改完回详情那条帖子：人刚编辑过它，接着要看的是它自己的样子，不是列表。
                composing = false
                editingPostId = 0L
                editorId = ""
                title = ""
                body = TextFieldValue("")
                category = ""
                tags = emptyList()
                tagInput = ""
                savedAt = 0L
                reloadKey++
                platform.showMessage("已保存修改")
                onEditorClosed?.invoke()
                return@launch
            }
            val id = current.add(cleanTitle, body.text, author, category, finalTags)
            posting = false
            if (id == null) {
                editorError = current.reason ?: "没发出去：检查一下网络再试"
                return@launch
            }
            composing = false
            editingPostId = 0L
            editorId = ""
            title = ""
            body = TextFieldValue("")
            category = ""
            tags = emptyList()
            tagInput = ""
            savedAt = 0L
            drafts?.delete(who, draftId)
            draftList = drafts?.drafts(who).orEmpty()
            reloadKey++
            platform.showMessage("已发布")
            // 发完了就回去看列表里的新帖：独立那一页留在这里只会是一条空编辑器。
            onEditorClosed?.invoke()
        }
    }

    /** 从草稿列表里删掉一篇（用户明确不要了）。 */
    val discardDraft: (PostDraft) -> Unit = discarded@{ draft ->
        drafts?.delete(who, draft.id)
        draftList = drafts?.drafts(who).orEmpty()
        if (composing && editorId == draft.id) {
            composing = false
            editorId = ""
            title = ""
            body = TextFieldValue("")
            category = ""
            tags = emptyList()
            tagInput = ""
            savedAt = 0L
        }
        platform.showMessage("草稿已删除")
    }

    val loadMore: () -> Unit = more@{
        val current = store ?: return@more
        if (loadingMore || busyKey != null) return@more
        loadingMore = true
        scope.launch {
            val fresh = current.list(posts.size, POSTS_PAGE)
            loadingMore = false
            when {
                fresh == null -> platform.showMessage("没拉到更多：${current.reason ?: "检查一下网络"}")
                fresh.isEmpty() -> {
                    hasMore = false
                    platform.showMessage("到底了")
                }
                else -> posts = posts + fresh.filterNot { next -> posts.any { it.id == next.id } }
            }
        }
    }

    val toggleLike: (CommunityPost) -> Unit = like@{ post ->
        val current = store ?: return@like
        if (busyKey != null) return@like
        busyKey = "like:${post.id}"
        scope.launch {
            val fresh = current.like(post.id, !post.mineLike)
            busyKey = null
            if (fresh == null) {
                platform.showMessage("没点上：${current.reason ?: "检查一下网络"}")
            } else {
                reloadKey++
            }
        }
    }

    val sendReply: () -> Unit = send@{
        val current = store ?: return@send
        val text = reply.trim()
        if (text.isEmpty() || busyKey != null || openId <= 0L) return@send
        busyKey = "reply:$openId"
        // 先把引用关系取到局部：launch 里发送成功那一刻要把 replyTo 清空，先读下来这两件事
        // 才确定对得上（clear 之后 replyTo 就是 null 了）。
        val quoteId = replyTo?.id ?: 0L
        scope.launch {
            val count = current.comment(openId, text, author, replyToId = quoteId)
            busyKey = null
            if (count == null) {
                platform.showMessage("没回上：${current.reason ?: "检查一下网络"}")
            } else {
                reply = ""
                // 发完就清：留着的话下一条回帖会莫名其妙地又挂到上一次引用的那一楼上。
                replyTo = null
                reloadKey++
            }
        }
    }

    val remove: (CommunityPost) -> Unit = del@{ post ->
        val current = store ?: return@del
        if (busyKey != null) return@del
        busyKey = "delete:${post.id}"
        scope.launch {
            val done = current.delete(post.id)
            busyKey = null
            confirmTarget = null
            if (done) {
                if (openId == post.id) {
                    openId = 0L
                    thread = null
                }
                reloadKey++
                platform.showMessage("已删除")
            } else {
                platform.showMessage("没删掉：${current.reason ?: "只有作者本人能删"}")
            }
        }
    }

    /**
     * 顶栏返回：编辑器里还有字就先存成草稿再走。
     *
     * 防抖窗口是 700 毫秒 —— 敲完立刻按返回，那一刻还没写盘；这里补一次，人才不会因为
     * "点错了返回"丢掉半篇。
     */
    val leave: () -> Unit = leave@{
        // 改帖：退回上一层就是放弃这次修改。这里必须**一起清掉 editingPostId** ——
        // 只清 composing 的话，残留的 id 会让下一次「写新帖」的发布误走 `post_update`，
        // 把新写的内容写进上一条帖子里。
        if (composing && editingPostId > 0L) {
            composing = false
            editingPostId = 0L
            editorId = ""
            platform.showMessage("已放弃修改")
            onNavigateBack?.invoke()
            return@leave
        }
        if (composing && who.isNotEmpty() && (title.isNotBlank() || body.text.isNotBlank())) {
            drafts?.save(
                who,
                PostDraft(
                    id = editorId,
                    title = title,
                    body = body.text,
                    category = category,
                    tags = tagsWithPending(tags, tagInput),
                    updatedAt = currentTimeMillis(),
                ),
            )
            platform.showMessage("已存为草稿")
        }
        onNavigateBack?.invoke()
    }

    /** 关掉详情：把这条的临时状态清干净，回列表时不会闪上一跳的旧内容。 */
    val closeThread: () -> Unit = close@{
        openId = 0L
        thread = null
        threadError = null
        reply = ""
        replyTo = null
        // 目录面板跟着关：它是"这一帖的楼层索引"，帖子关掉之后留着只会盖在列表上。
        tocOpen = false
        // 帖内搜索同理。
        searchOpen = false
        // 「跳到楼层」也一样：它属于这一帖，关帖之后留着只会盖在列表上。
        jumpOpen = false
        // 独立详情那一页（`Screen.PostDetail`）：没有"列表"可以退回页内，所以直接把自己弹回去。
        // 页内切换时这里是 null，什么都不做。
        onThreadClosed?.invoke()
    }

    /** 打开详情：先清空上一次的正文，免得新帖刷新前先显示上一条的字。 */
    val openThread: (CommunityPost) -> Unit = open@{ post ->
        thread = null
        threadError = null
        reply = ""
        // 换了一条帖子，上一帖里的引用关系必须一起扔掉：`reply_to_id` 是**同一条帖子内**的
        // 楼层 id，带到新帖里必然对不上（后端会当没带），但胶囊会写出一条假的「回复 #N」。
        replyTo = null
        openId = post.id
        // 先进加载态：详情那一帧还没有正文，不置 true 会闪一句「这条帖子没拉到」。
        threadLoading = true
    }

    /**
     * 所有"打开一条帖子"的入口都走这里。
     *
     * 有 [onOpenThread] 就把活交给外面（导航到独立那一页 `Screen.PostDetail/{id}`，不挂底栏），
     * 没有就还是页内切换。**新增入口也请走这个函数**，别直接调 [openThread] ——
     * 直接调会绕过外面那条路由，于是同一个点击在不同入口上行为不一样。
     *
     * 和 [openEditorOnEnter] / [onOpenCompose] 是同一套写法：一页两种用法（页内 / 独立页）
     * 只差一个"起步停在哪一格"的参数，底下永远是同一份实现。
     */
    val startThread: (CommunityPost) -> Unit = start@{ post ->
        val navigate = onOpenThread
        if (navigate != null) navigate(post) else openThread(post)
    }

    /**
     * 引用某一楼：把那句话做成 Markdown 引用塞进回复框，接着写就行。
     *
     * 同时**记下这一楼**（[replyTo]）——塞进文本框的只是一段字，后端不知道那是引用；真正让它
     * 变成一条关系的是发帖时带上的 `p_reply_to_id`（见 [sendReply]）。
     *
     * 再点另一楼就覆盖：最新一次点击就是这句话要回的那一楼。
     */
    val quoteComment: (PostComment) -> Unit = quote@{ comment ->
        val snippet = comment.body.lineSequence().firstOrNull().orEmpty().trim().take(40)
        val head = if (snippet.isEmpty()) "" else "> ${comment.author.ifBlank { "匿名" }}：$snippet\n"
        reply = (head + reply).take(POST_BODY_LIMIT)
        replyTo = comment
        platform.showMessage("已引用，写完点发送")
    }

    /**
     * 举报某一楼：帖子、楼层、楼层号、理由 → 一段文本进剪贴板 + 一句 toast。
     *
     * 只在这里落地（`platform` 就在这个 composable 手里），与 [onCopy] / `onCopyFloor` 同一条
     * 链路：本文件里 [PostThreadPane] 往下一路都是 private 的纯 UI，拿不到平台句柄，也不该
     * 让每个 composable 都能随手写剪贴板。
     *
     * 为什么不是"发请求"：后端没有举报 RPC（见 [reportableFloor] 的说明），所以这里做到
     * "把要说的话整理好、复制走"为止，并如实告诉人下一步是发到反馈群。
     */
    val onReportFloor: (CommunityPost, PostComment, Int, String) -> Unit =
        report@{ post, comment, floor, reason ->
            platform.copyToClipboard(reportableFloor(post, comment, floor, reason))
            platform.showMessage("举报内容已复制，请发到反馈群")
        }

    /** 当前这一屏的口径算出来的列表：分类、标签、搜索词、排序都只在这一层做，不动网络。 */
    val shown = remember(posts, board, categoryPick, tagPick, query, authorId, reads, readsVersion) {
        val keyword = query.trim()
        // 收藏用到的是**顺序**（最近收的在前），所以要 List 而不是 Set。
        val savedOrder = if (board == PostBoard.Saved) reads?.savedIds().orEmpty() else emptyList()
        val byFilter = posts.filter { post ->
            (categoryPick.isBlank() || post.category == categoryPick) &&
                (tagPick.isBlank() || post.tags.any { it.equals(tagPick, ignoreCase = true) }) &&
                (keyword.isEmpty() ||
                    post.title.contains(keyword, ignoreCase = true) ||
                    post.excerpt.contains(keyword, ignoreCase = true)) &&
                (board != PostBoard.Mine || (authorId.isNotBlank() && post.authorId == authorId)) &&
                (board != PostBoard.Saved || savedOrder.contains(post.id))
        }
        when (board) {
            // 置顶永远在前，其余按"赞 3 分、回复 2 分"排：这个权重只是让热闹的浮上来，
            // 不参与任何排序决定，改动它不会改变数据。
            PostBoard.Hot -> byFilter.sortedWith(
                compareByDescending<CommunityPost> { it.pinned }
                    .thenByDescending { it.likes * 3 + it.comments * 2 },
            )

            // 收藏栏按收藏时间倒序：这是"我收起来那一批"的自然顺序。
            // 还没拉到列表里的那几条排到最后（indexOf 是 -1），翻到它们自然会浮上来。
            PostBoard.Saved -> byFilter.sortedBy { post ->
                savedOrder.indexOf(post.id).let { index -> if (index < 0) savedOrder.size else index }
            }

            else -> byFilter
        }
    }

    val canDeleteOpen = authorId.isNotBlank() && thread?.post?.authorId == authorId

    /**
     * 详情页「收藏 / 取消收藏」的**唯一**实现：主楼动作行那颗星与顶栏那颗星同时用它。
     *
     * 为什么提成 val 而不是各写一遍：收藏状态存在盘上（[PostReadStore]），页面靠 [readsVersion] 这个
     * 计数器重算。两处入口各写一份 `toggleSaved + readsVersion++`，只要有人改错一处（比如漏掉
     * `readsVersion++`，或者写成 `setSaved(!saved)`），就会变成"顶栏点了亮、主楼那颗还暗"这种
     * 同一个状态两种显示的 bug。这里只有一条路径，两个入口传的是同一个 lambda。
     *
     * `reads == null`（本机存储还没起来）时什么都不做：那时读不出也写不了，点一颗画出来的钮
     * 等于骗人 —— 所以顶栏那颗在这种时候干脆不画（见 [TopBarSaveButton] 的调用点）。
     */
    val toggleOpenSaved: () -> Unit = {
        reads?.let { store ->
            store.toggleSaved(openId)
            readsVersion++
        }
    }

    /**
     * 侧栏「标签」页：从已经拉到手的帖子里汇总，出现多的在上面。
     *
     * 不发请求：后端只给每条帖子自己的 `tags`，没有标签目录；为一次侧栏里的一次点击去拉一遍
     * 全部帖子不划算，而"在这批帖子里出现几次"本身就是能用的热度。
     */
    val drawerTags = remember(posts) {
        // 同一个标签有人写 `Android` 有人写 `android`：合并成一行，展示先用到的那个写法。
        // 分成两行的话，点任何一行都会把两条都筛出来（过滤是 ignoreCase 的），看着像 bug。
        val counts = LinkedHashMap<String, Int>()
        val shown = LinkedHashMap<String, String>()
        posts.forEach { post ->
            post.tags.forEach { raw ->
                val name = raw.trim()
                if (name.isNotEmpty()) {
                    val key = name.lowercase()
                    counts[key] = (counts[key] ?: 0) + 1
                    if (key !in shown) shown[key] = name
                }
            }
        }
        counts.entries
            .sortedByDescending { it.value }
            .take(DRAWER_TAG_LIMIT)
            .map { PostTagCount(shown[it.key] ?: it.key, it.value) }
    }

    /**
     * 侧栏「分类」页列的分类：五个候选在前，后面接**已经出现在帖子里的别的分类**。
     *
     * `POST_CATEGORIES` 只是发帖时的候选，后端把分类当字符串存，历史上存进去的别的名字
     * （早期数据里有过「公告」）不会出现在那五个里；只列候选的话，那几条帖子在侧栏里
     * 就筛不出来，点「全部话题」才看得见。
     */
    val drawerCategories = remember(posts) {
        val seen = posts.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct()
        (POST_CATEGORIES + seen.filterNot { it in POST_CATEGORIES }).distinct()
    }

    /**
     * 分类名 → 后端下发的颜色（`#RRGGBB`），给侧栏用。
     *
     * 不给的话，侧栏那颗色点只能按名字哈希兜底，而卡片上是下发的色：同一个分类在两个地方
     * 长得不一样，看着像两个分类。同一分类出现在多条帖子上时取第一条带色的。
     */
    val drawerCategoryColors = remember(posts) {
        val map = LinkedHashMap<String, String>()
        posts.forEach { post ->
            val name = post.category.trim()
            val hex = post.categoryColor.trim()
            if (name.isNotEmpty() && hex.isNotEmpty() && name !in map) map[name] = hex
        }
        map
    }

    /**
     * 打开的那条帖子旁边能去的地方：同分类、不是它自己，按热闹程度取三条。
     *
     * 从已经拉到手的列表里挑，不额外发请求——帖子详情页底部"还能看点什么"是个顺手的出口，
     * 不该为它多一次往返；列表没拉到的时候就干脆不画。
     */
    val related = remember(posts, thread) {
        val open = thread?.post ?: return@remember emptyList<CommunityPost>()
        posts.asSequence()
            .filter { it.id != open.id }
            .filter { open.category.isBlank() || it.category == open.category }
            .sortedByDescending { it.likes * 2 + it.comments }
            .take(3)
            .toList()
    }

    // 分类侧栏要连顶栏一起盖住（遮罩从屏幕最上面压下来），所以 Scaffold 外面套一层 Box：
    // 抽屉是它的第二个孩子，画在 Scaffold 之上。Scaffold 自己仍占满整屏。
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            when {
                openId > 0L -> ScreenTopBar(
                    title = thread?.post?.title?.ifBlank { "帖子" } ?: "帖子",
                    onNavigateBack = closeThread,
                    // 详情页顶栏高度 **56dp**、标题**左对齐**：fluxdo 这里就是 Flutter 原生
                    // `AppBar`（`kToolbarHeight = 56`、`centerTitle: false`、
                    // `textTheme.titleMedium`）。本仓 `FlTopBarHeight` 是 72dp 且标题居中，
                    // 那是 FlClash 的二级页规范 —— 帖子详情这一页要照 fluxdo，所以单独传。
                    height = 56.dp,
                    centered = false,
                    // 标题照 fluxdo 的 `textTheme.titleMedium`（`topic_detail_page.dart:1487`
                    // 的 `_buildAppBarTitle` 用的正是 `titleMedium`）：**16sp / 行高 24 / w500 /
                    // letterSpacing 0.15**，不是本仓二级页那套 17sp 加粗，也不是首页 24sp 大标题。
                    // 上一版只抄了字号、字重还是 400，标题比旁边那颗图标轻一档，看着像"副标题"。
                    titleStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.15.sp,
                    ),
                    actions = {
                        val open = thread?.post
                        if (open != null) {
                            // 收藏快捷钮（在「更多」**左边**）。为什么顶栏也要一颗：收藏原来只藏在
                            // 主楼动作行右侧那颗星里，读一条长帖时要先滑到第一楼底部才点得到 ——
                            // 而"收藏一条正在读的帖"是读的过程中随时会冒出来的念头，不是读完才做的事。
                            // fluxdo 的详情顶栏放的就是收藏这一类"对整帖"的动作（`topic_detail_page.dart`
                            // 的 AppBar actions）。
                            //
                            // **不画的条件**（不是画成灰钮）：认不出身份（`authorId` 为空）或盘上那份
                            // 收藏表还没起来（`reads == null`）。这两种情况下 `toggleSaved` 点了也不会
                            // 生效，一颗按不出反应的钮比没有更让人迷惑。
                            if (reads != null && authorId.isNotBlank()) {
                                TopBarSaveButton(
                                    saved = reads?.isSaved(openId) == true,
                                    onClick = toggleOpenSaved,
                                )
                            }
                            // 「帖内搜索」图标钮：照 fluxdo 的 AppBar actions
                            // （`_buildAppBarActions`，`topic_detail_page.dart:1706` 的
                            // `_buildSearchAction` —— 那边顶栏是 **搜索 + 更多** 两颗，
                            // 搜索是顶栏的一等动作，不是菜单里的一项）。
                            // 为什么本仓也要一颗：帖内搜索以前只藏在「更多」里，读一条 200 层的
                            // 长帖时想找一句话得先点开菜单再找那一项；而"在这条帖子里找一句话"
                            // 是读的过程中随时会冒出来的动作，跟收藏同一个性质。
                            // 形状与右边那颗「更多」同一档（触控 48dp / 图标 24dp / 正圆底），
                            // 两颗钮并排时视觉重量一致；菜单里那一项**保留**，两条路通同一个面板。
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = "帖内搜索",
                                        onClick = { searchOpen = true },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            // 顶栏右侧照 fluxdo 只留一颗「更多」（`Symbols.more_vert_rounded`）。
                            // **编辑与删除都不在顶栏** —— 上一版把两颗圆图标放在这里，
                            // 与 fluxdo 的位置正好相反（见下面那个菜单）。
                            DetailMoreMenu(
                                open = open,
                                canManage = canDeleteOpen,
                                busy = busyKey != null,
                                onEdit = { editPost(open) },
                                onDelete = { confirmTarget = open },
                                onCopy = {
                                    platform.copyToClipboard(copyablePost(open))
                                    platform.showMessage("帖子已复制")
                                },
                                onOpenToc = { tocOpen = true },
                                onOpenSearch = { searchOpen = true },
                                onOpenJump = { jumpOpen = true },
                            )
                        }
                    },
                )

                composing -> ScreenTopBar(
                    // 改帖时标题写「编辑话题」，新建写「创建话题」：同一个编辑器两种语义，
                    // 顶栏是唯一能一眼看出"我是在改还是在新写"的地方。
                    title = if (editingPostId > 0L) "编辑话题" else "创建话题",
                    onNavigateBack = cancelEditor,
                    actions = {
                        // 顺序照 fluxdo 的 `ComposerPageGrid`（`composer_page_chrome.dart:27-47`）：
                        // 文档动作（预览）在前、提交在后、中间留 8dp（[FlTopBar] 的 `ActionGap`）。
                        //
                        // 预览是**一颗 44dp 的圆图标按钮**（`ComposerActionButton` 的
                        // `BoxConstraints.tightFor(44, 44)` + 图标 21dp），不是文字按钮：
                        // 「改文字 / 预览」这两个字一翻转就改变宽度，左边那颗「更多」跟着左右跳，
                        // 而一颗 44dp 的圆永远占同一格。文案只进无障碍名（tooltip）。
                        //
                        // 本仓**没有**富文本模式，所以 fluxdo 那另一颗 rich↔source
                        // （`ComposerModeButton`）不做：做了也只有一颗死键。
                        ComposerPreviewButton(previewing = previewing) { previewing = !previewing }
                        // 圆形纸飞机发送（fluxdo 的 ComposerSubmitButton：40dp 正圆，图标 21dp）。
                        // 改帖时这颗按钮的含义是"保存修改"，仍然是同一个位置、同一个形状。
                        PublishCircleButton(
                            busy = posting,
                            enabled = canWrite && !posting && setupGate.ready,
                            onClick = publish,
                        )
                        // 「更多」：草稿状态的**唯一**去处（见 [ComposerDraftMenuButton]）。
                        // 放在最右端 —— 它是次要入口，右手拇指扫过顶栏时不会误碰。
                        ComposerDraftMenuButton(savedAt = savedAt)
                    },
                )

else -> Column {
                    // 列表页顶上不是「标题栏」而是 fluxdo 那条 48dp 工具栏：☰ / 最新▾ / 🔍，
                    // 搜索胶囊是它下面那一行常驻的输入框（🔍 只负责把焦点交给胶囊）。
                    // 原来这里是 72dp 居中标题 + 「写帖子」文字按钮——两样都不在 fluxdo 的首页上。
                    Box {
                        PostsToolbar(
                            board = board,
                            category = categoryPick,
                            tag = tagPick,
                            newCount = freshCount,
                            menuOpen = filterMenuOpen,
                            onOpenCategories = { drawerOpen = true },
                            onOpenFilters = { filterMenuOpen = true },
                            // 「创建话题」：身份认出来了才画（发不了帖的按钮不如不放，
                            // 点了只会说"还没认出身份"）。它和 FAB 的「写新帖」进同一个编辑器。
                            onCompose = if (canWrite) ({ startCompose(null) }) else null,
                            // 开源周榜：入口搬到顶栏右上角（列表里那条 40dp 横条删掉了）。
                            onOpenWeeklyRank = if (weeklyRank != null) onOpenWeeklyRank else null,
                            // 搜索：点的就是下面那条常驻胶囊 —— 只把焦点交过去，不新开页面。
                            // 胶囊在同一个 Column 里，`requestFocus` 时它一定已经挂上树了。
                            onSearch = { searchFocus.requestFocus() },
                            // 顶栏与**页面**同色：Scaffold 的默认底色是 `colorScheme.surface`（主题的页面底色），
// 而不是 `FlSurfaceLevel.Page`（那是 `surfaceContainer`，浅色主题下比页面亮一档，栏就会
// 显出一条比正文底色浅的带子）。所以这里传 `surface`，不传 Page。
                            background = MaterialTheme.colorScheme.surface,
                        )
                        DropdownMenu(
                            expanded = filterMenuOpen,
                            onDismissRequest = { filterMenuOpen = false },
                        ) {
                            PostBoard.entries.forEach { entry ->
                                DropdownMenuItem(
                                    text = { Text(entry.label) },
                                    onClick = {
                                        board = entry
                                        filterMenuOpen = false
                                    },
                                    trailingIcon = if (entry == board) {
                                        { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // 右下角创作入口：只在列表形态出现（详情/编辑器各有自己的动作）。
            // 底栏在我们这层 Scaffold 之外，所以自己垫 96：和列表的 contentPadding 同一个数。
            if (openId <= 0L && !composing) {
                Box(modifier = Modifier.padding(bottom = 96.dp)) {
                    PostsFab(
                        open = fabOpen,
                        draftCount = draftList.size,
                        canWrite = canWrite,
                        onToggle = { fabOpen = !fabOpen },
                        onNewPost = {
                            fabOpen = false
                            startCompose(null)
                        },
                        onDrafts = {
                            fabOpen = false
                            draftList.firstOrNull()?.let { startCompose(it) }
                        },
                        onRefresh = {
                            fabOpen = false
                            if (!refreshing) {
                                refreshing = true
                                reloadKey++
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        when {
            openId > 0L -> PostThreadPane(
                post = thread?.post,
                comments = thread?.comments.orEmpty(),
                loading = threadLoading,
                error = threadError,
                busyKey = busyKey,
                reply = reply,
                authorId = authorId,
                related = related,
                // 「上次读到哪一屏」（见 [PostPositionStore]）：恢复在 `PostThreadPane` 里做，
                // 它才拿着 `listState` —— 与目录面板、帖内搜索同一个理由（那一层拿得到状态才动）。
                positions = positions,
                saved = reads?.isSaved(openId) == true,
                // 续读条只在「打开之前确实有新的回帖」时才出现（见 [resumeUnread]）；
                // `null`（还没问过）与 `false` 都是"不出现"——没有未读时那一格一个组合项都不建。
                resumeUnread = resumeUnread == true,
                resumeMark = resumeMark,
                /**
                 * 这一条要回的是哪一楼（null = 普通回帖）。
                 *
                 * 传"要发的那条关系"，而不是"这个帖子曾经有过哪些引用"：胶囊上写的
                 * 「回复 #N · 名字」只描述**这一次**发送要挂到哪儿，发完全部清空。
                 * 历史楼层自己的引用指示条从 [comments] 里每条自己的 `replyToId` 读
                 * （那是下一路要画的东西，本轮只做数据与状态）。
                 */
                replyTo = replyTo,
                onReplyChange = { reply = it.take(POST_BODY_LIMIT) },
                onSend = sendReply,
                onToggleLike = { thread?.post?.let(toggleLike) },
                // 主楼动作行那颗星：与顶栏那颗星**同一个** lambda（见 [toggleOpenSaved]），
                // 谁点都走 `toggleSaved + readsVersion++` 这一条路径。
                onToggleSave = toggleOpenSaved,
                onQuote = quoteComment,
                onCopy = {
                    thread?.post?.let { post ->
                        platform.copyToClipboard(copyablePost(post))
                        platform.showMessage("帖子已复制")
                    }
                },
                // 复制某一楼：与上面那条「复制帖子」同一个落点（platform 只在 PostsScreen 手里），
                // 文本换成 [copyableFloor]（`#N` + 作者 + 日期 + 正文）。
                // 楼层号由 `PostThreadPane` 里 `items(floorRows)` 那个 `index + 2` 传上来，
                // 所以复出来的 `#N` 与卡片上画的、与跳转用的那一个数完全一致。
                onCopyFloor = { comment, floor ->
                    platform.copyToClipboard(copyableFloor(floor, comment))
                    platform.showMessage("已复制")
                },
                // 举报某一楼：**文本在 PostsScreen 里拼、剪贴板也在这里写**（见 [onReportFloor]
                // 那条 val 的注释）。面板本身画在 `PostThreadPane` 里张罗，只有"点提交"那一下
                // 回到这一层 —— 与复制、收藏、点赞同一个方向。
                onReportFloor = onReportFloor,
                onOpenRelated = { post -> startThread(post) },
                // 目录面板：开关在 `PostsScreen`（顶栏入口也在这儿），面板本体画在
                // `PostThreadPane` 里（它才拿着 `listState`，算得出"现在在第几层"）。
                tocOpen = tocOpen,
                onDismissToc = { tocOpen = false },
                searchOpen = searchOpen,
                onDismissSearch = { searchOpen = false },
                jumpOpen = jumpOpen,
                onDismissJump = { jumpOpen = false },
                // 详情页下拉刷新：**就是 `reloadKey++` 那条重载路径**（与发帖/回帖/点赞之后
                // 刷新的那条完全同一个入口，见 `LaunchedEffect(store, openId, reloadKey)`）。
                // 不另写一条"拉一次详情"：那会把 `stableMerge` / `markRead` / `resumeUnread`
                // 那套逻辑抄第二遍。指示器的 `isRefreshing` 读的是同一条 effect 收尾时清掉的
                // `threadLoading`，所以不需要额外记一个布尔量。
                onRefresh = { reloadKey++ },
                modifier = Modifier.padding(padding),
            )

            composing -> PostEditorPane(
                title = title,
                body = body,
                category = category,
                tags = tags,
                tagInput = tagInput,
                error = editorError,
                previewing = previewing,
                onTitleChange = { title = it.take(POST_TITLE_LIMIT) },
                // 截断时要同时收 游标 的范围，否则超长那一刻 selection 会落在文本外面，
                // Compose 1.7+ 会直接抛 IndexOutOfBounds。
                onBodyChange = { next ->
                    body = if (next.text.length <= POST_BODY_LIMIT) {
                        next
                    } else {
                        // 截断时 Composition 必须跟着收窄：`copy` 不传 composition 会沿用原来的范围，
                        // 而它可能落在截短后的文本之外 —— TextField 内部拿它去 subSequence 就会越界。
                        // 收成 Collapsed（组字区为空）等于"这一下不算组字"，输入法会重新来一次。
                        //
                        // `take` 是按 Char 截的，可能切在代理对中间（emoji、少数字）：
                        // 末尾落在高位代理上就往回退一位，别造出孤立 surrogate。
                        val kept = next.text.take(POST_BODY_LIMIT).let { text ->
                            if (text.isNotEmpty() && text.last().isHighSurrogate()) text.dropLast(1) else text
                        }
                        val composition = next.composition
                            ?.let { c ->
                                TextRange(
                                    c.min.coerceIn(0, kept.length),
                                    c.max.coerceIn(0, kept.length),
                                )
                            }
                            ?.takeIf { it.min != it.max }
                        next.copy(
                            text = kept,
                            selection = TextRange(
                                next.selection.start.coerceIn(0, kept.length),
                                next.selection.end.coerceIn(0, kept.length),
                            ),
                            composition = composition,
                        )
                    }
                },
                onCategoryChange = { category = it },
                onTagInputChange = onTagInputChange,
                onRemoveTag = { tag -> tags = tags - tag },
                // 标签面板点「完成」：**整份列表**一起写回。
                //
                // 不逐个 add/remove：面板里可能一次删两个、加三个，逐个回调会把"中间那一份"
                // 也写进自动保存的键里（见上面那条 `LaunchedEffect(... tags ...)`），
                // 于是 700 毫秒里连写几次盘。整份写回只有一次状态变化。
                onTagsApply = { nextTags -> tags = nextTags.take(TAG_MAX) },
                // 「分享方案」/「插入分享码」：点了才拉列表，拉不到只说一句，不挡着发帖。
                // 插进去的是一整段分享文案（方案名 + 方案所属机型 + 分享码），
                // 别人在帖子里看到就知道这是哪台机子验证过的方案。
                onInsertShare = loadPlans?.let { load ->
                    {
                        sharePickerOpen = true
                        if (sharePlans.isEmpty() && !shareLoading) {
                            shareLoading = true
                            shareError = null
                            scope.launch {
                                sharePlans = runCatching { load() }.getOrElse { problem ->
                                    shareError = problem.message ?: "方案列表没拉到"
                                    emptyList()
                                }
                                shareLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier.padding(padding),
            )

            else -> PostListPane(
                posts = posts,
                shown = shown,
                loading = loading,
                refreshing = refreshing,
                loadingMore = loadingMore,
                error = error,
                drafts = draftList,
                board = board,
                category = categoryPick,
                query = query,
                authorId = authorId,
                selfName = author,
                canWrite = canWrite,
                hasMore = hasMore,
                hasBackend = store != null,
                freshCount = freshCount,
                unreadOf = { post ->
                    // 一次都没读过（全新装上）时不画点：满屏都是点等于没有提示。
                    reads != null && reads.hasHistory() && reads.isUnread(post.id, post.comments)
                },
                readOf = { post ->
                    // "全读完"= 读且没有新回复；一次都没读过的帖子不算（那一档是"未读"）。
                    reads != null && reads.hasHistory() && !reads.isUnread(post.id, post.comments)
                },
                savedOf = { post -> reads?.isSaved(post.id) == true },
                savedTotal = reads?.savedIds()?.size ?: 0,
                onLongPress = { post ->
                    val store = reads
                    if (store == null) {
                        platform.showMessage("收藏要等本机存储就绪")
                    } else {
                        val nowSaved = store.toggleSaved(post.id)
                        readsVersion++
                        platform.showMessage(if (nowSaved) "已收藏" else "已取消收藏")
                    }
                },
                listState = postListState,
                searchFocus = searchFocus,
                onBoardChange = { board = it },
                onCategoryChange = { categoryPick = it },
                onQueryChange = { query = it },
                onRefresh = {
                    if (!refreshing) {
                        refreshing = true
                        reloadKey++
                    }
                },
                onLoadMore = loadMore,
                onOpen = startThread,
                onOpenDraft = { startCompose(it) },
                onDeleteDraft = { discardDraft(it) },
                onWrite = { startCompose(null) },
                modifier = Modifier.padding(padding),
            )
        }
    }

        // 分类侧栏（☰）。只在列表形态存在：详情与编辑器各有自己的顶栏与返回。
        if (openId <= 0L && !composing) {
            PostCategoryDrawer(
                open = drawerOpen,
                categories = drawerCategories,
                // 分类名 → 下发色：侧栏那颗色点要和卡片、详情页用同一个来源。
                categoryColors = drawerCategoryColors,
                category = categoryPick,
                tags = drawerTags,
                tag = tagPick,
                onDismiss = { drawerOpen = false },
                onPickCategory = { picked ->
                    // 分类与标签互斥：两者叠着筛会筛出空列表，而顶栏一行只写得下其中一个。
                    categoryPick = picked
                    tagPick = ""
                },
                onPickTag = { picked ->
                    tagPick = picked
                    categoryPick = ""
                },
            )
        }
    }

    if (sharePickerOpen) {
        SharePlanPickerDialog(
            plans = sharePlans,
            loading = shareLoading,
            error = shareError,
            onDismiss = { sharePickerOpen = false },
            onPick = { plan ->
                // 与正文里那排 Markdown 模板同一条路：插在光标处，不是往末尾追加。
                // 前面空一行（光标不在行尾时补）是为了让分享码自成一段——它是一整块引用。
                val share = ShareCode.message(plan.id, plan.title, plan.deviceLabel)
                val at = body.selection.min
                val head = body.text.take(at)
                val glue = if (head.isEmpty() || head.endsWith("\n\n")) {
                    ""
                } else if (head.endsWith("\n")) {
                    "\n"
                } else {
                    "\n\n"
                }
                val next = body.text.replaceRange(at, at, glue + share)
                if (next.length <= POST_BODY_LIMIT) {
                    val caret = at + glue.length + share.length
                    body = body.copy(text = next, selection = TextRange(caret, caret))
                }
                sharePickerOpen = false
            },
        )
    }

    confirmTarget?.let { target ->
        PostDeleteDialog(
            busy = busyKey == "delete:${target.id}",
            onDismiss = { confirmTarget = null },
            onConfirm = { remove(target) },
        )
    }
}

/**
 * 首页那根搜索胶囊，逐项照 fluxdo 的 `widgets/common/search_capsule.dart`：
 *
 * - 高 **40dp**（固定），圆角 **20**（胶囊）
 * - 底色 `surfaceContainerHighest` **50%**（`search_capsule.dart:50-52`，`0.5 * backgroundOpacity`；
 *   本仓没有 morph，`backgroundOpacity` 恒 1，所以就是 0.5，尾段渐隐那一段没有落点）
 * - 左侧留白 **16**，放大镜 **20dp**，用**强调色**（`context.emphasis.accent`，本仓用 `primary`；不是灰色 outline）
 * - 图标与提示文字之间 **8**，文字 **14sp**、色 `onSurfaceVariant`、右边留 **12**
 * - 有字时右端一颗**清除**（20dp，`Symbols.close_rounded` 的位置，见 `search_page.dart:866-871`）：
 *   fluxdo 的首页胶囊本身**没有**这颗（它只是个按钮，点进去才是搜索页），但本仓这颗是**真输入框**，
 *   没有清除键就得一直按退格——这是"胶囊即输入框"带来的、必须补的一格。
 *
 * 为什么不用 `OutlinedTextField`：它自带 56dp 的最小高度与内部 padding，压不到 40；
 * 而且它的 placeholder 走的是输入框那套排版（12sp 起），和这里要的 14sp 对不上。
 * 自己搭一层 `decorationBox` 更短的代码就能把上面几个数逐个落实。
 */
@Composable
private fun SearchCapsule(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val scheme = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = scheme.onSurface, fontSize = 14.sp),
        cursorBrush = SolidColor(scheme.primary),
        modifier = (if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerHighest.copy(alpha = 0.5f)),
        decorationBox = { inner ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.width(16.dp))
                // 放大镜用自绘的：`CantaTopBar` 那颗搜索也是它，两颗得同族。
                Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = scheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "搜索话题…",
                            fontSize = 14.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
                // 右端：有字才画清除键，没字时留 12 的呼吸位（`search_capsule.dart:74` 的 `right: 12`）。
                // 40dp 触控框刚好等于胶囊高度，点起来够大又不会把胶囊撑高。
                if (value.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "清空搜索", onClick = { onValueChange("") }),
                        contentAlignment = Alignment.Center,
                    ) {
                        // `clearField` 的外圈是 `GlyphCircle`（默认 `GlyphRole.Body`，`fill = 0f` 画不出来），
                        // `Icons.Rounded.Cancel` 是官方 Material Symbols，没有自绘 glyph 那套 `GlyphRole` 陷阱，
                        // 默认 `fill = 0f` 照样出图（缺的只是外圈实心圆，胶囊底色本来就托住了这一格）。
                        Icon(Icons.Rounded.Cancel, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
        },
    )
}

@Composable
private fun PostListPane(
    posts: List<CommunityPost>,
    shown: List<CommunityPost>,
    loading: Boolean,
    refreshing: Boolean,
    loadingMore: Boolean,
    error: String?,
    drafts: List<PostDraft>,
    board: PostBoard,
    category: String,
    query: String,
    authorId: String,
    /** 我自己的署名（未改名时会是一串装机码，用来判断要不要提示去改名）。 */
    selfName: String,
    canWrite: Boolean,
    hasMore: Boolean,
    hasBackend: Boolean,
    /** 这一趟多出来的新帖数（横幅用；0 时不画横幅）。 */
    freshCount: Int,
    /** 这条帖子现在算不算没看过 / 有新回复（见 [PostReadStore.isUnread]）。 */
    unreadOf: (CommunityPost) -> Boolean,
    /** 这条帖子是不是"已经全读完了"：标题与元信息在那一档统一退灰（`topic_card.dart` 的 isFullyRead）。 */
    readOf: (CommunityPost) -> Boolean,
    /** 这条帖子是不是收起来了。 */
    savedOf: (CommunityPost) -> Boolean,
    /** 收藏一共几条（不一定都在已加载的列表里，空态文案要区分这两件事）。 */
    savedTotal: Int,
    /** 长按一张卡片：切收藏。 */
    onLongPress: (CommunityPost) -> Unit,
    listState: LazyListState,
    /** 搜索胶囊的焦点（胶囊常驻；顶栏那颗 🔍 也把焦点交给它，所以这一页只有一个搜索落点）。 */
    searchFocus: FocusRequester,
    onBoardChange: (PostBoard) -> Unit,
    onCategoryChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (CommunityPost) -> Unit,
    onOpenDraft: (PostDraft) -> Unit,
    onDeleteDraft: (PostDraft) -> Unit,
    onWrite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val refreshState = rememberPullToRefreshState()
    // 置顶分组：三样东西都必须在 LazyColumn **外面**算。`LazyListScope` 不是 composable 上下文，
    // 在那里调 remember/state 会直接编译失败（`@Composable invocations can only happen from …`）。
    val pinnedShown = remember(shown) { shown.filter { it.pinned } }
    val normalShown = remember(shown) { shown.filterNot { it.pinned } }
    var pinnedOpen by remember { mutableStateOf(true) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = refreshState,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 搜索胶囊**常驻在列表外面**的那一行：fluxdo 的搜索是工具栏下面整行 48dp 的胶囊
            // （`_capsuleRowHeight = 48`，展开态 rect 在 `statusBar + 48 + 4`），它不跟着列表滚走。
            // 放进 LazyColumn 里的话往下滑就看不见了——那不是同一个东西。
            //
            // **没有做**胶囊 → 工具栏落位格那条整条 morph（`topics_page.dart:1856-1875` 的
            // `Rect.lerp` + `Curves.easeInOutCubic`）：那要求把胶囊搬到顶栏的 Stack overlay 层、
            // 并按折叠进度重排顶栏（落位格宽度 44×t 随 p1 变），等于把这一页的头部整个换掉。
            // 收益只是滚动时的视觉衔接，风险是列表顶部每次重组都跟着头部走。这里只保留"常驻 + 有字时
            // 可清空"，其余照 fluxdo 的静态值（底色 0.5、图标 20、左 16、间距 8、x 右 12）。
            SearchCapsule(
                value = query,
                onValueChange = onQueryChange,
                focusRequester = searchFocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            )

            // 分类 chips 行**不要**：分类入口只有顶栏那颗 ☰（打开侧栏）。不要再把它摆到搜索下面，
            // 也不要在列表里插「帖子 · N」那种统计行。

            // 左右 12dp 从卡片挪到这里（`PostCard` 不再自带 `horizontal = 12`）：fluxdo 的卡片自身
            // 只有 `EdgeInsets.only(bottom: 8)`（`topic_card.dart:329`），左右留白由列表层给
            // （`topics_page.dart:1769` 的 `EdgeInsets.only(left: 12, right: 12)` 套整页）。
            // 放在 contentPadding 上还有个额外好处：贴边滚动时字不再从卡片圆角里露出来。
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = 96.dp,
                ),
            ) {
                // 「↑ 查看 N 个新的或更新的话题」：只有真的有未读时才出现，点一下重拉一遍。
                if (board == PostBoard.Latest && freshCount > 0) {
                    item(key = "new-topics") {
                        FreshTopicsBanner(count = freshCount, onClick = onRefresh)
                    }
                }

                // 开源周榜**不在这张列表里**了：它搬到了顶栏右上角那颗按钮（见 [WeeklyRankIconButton]）。
                // 列表里留一条 40dp 的横条等于每屏少一条帖子，而它是个入口不是内容。

                // 我自己还没改名字：列表顶端说一次，并指出去哪儿改（旁边那个红点一直在呼吸）。
                if (authorId.isNotBlank() && isDefaultAuthorName(selfName)) {
                    item(key = "anonymous-banner") { AnonymousHintBanner() }
                }

                if (drafts.isNotEmpty()) {
                    val newest = drafts.first()
                    item(key = "drafts-head") {
                        DraftBanner(
                            count = drafts.size,
                            newest = newest,
                            onContinue = { onOpenDraft(newest) },
                        )
                    }
                    items(drafts, key = { "draft-${it.id}" }) { draft ->
                        DraftRow(
                            draft = draft,
                            onClick = { onOpenDraft(draft) },
                            onDelete = { onDeleteDraft(draft) },
                        )
                    }
                }

                // 置顶话题单独成一组（截图里顶上那块「置顶话题 · 3」）：置顶的老帖混在时间序里会把
                // 新帖挤下去，分组后既看得见又不挡路。默认展开、点一下就收起。
                if (pinnedShown.isNotEmpty()) {
                    item(key = "pinned-head") {
                        PinnedTopicsHeader(
                            count = pinnedShown.size,
                            unreadCount = pinnedShown.count { unreadOf(it) },
                            open = pinnedOpen,
                            onToggle = { pinnedOpen = !pinnedOpen },
                        )
                    }
                    if (pinnedOpen) {
                        items(pinnedShown, key = { "pin-${it.id}" }) { post ->
                            PostCard(
                                post = post,
                                mine = authorId.isNotBlank() && post.authorId == authorId,
                                unread = unreadOf(post),
                                read = readOf(post),
                                saved = savedOf(post),
                                onClick = { onOpen(post) },
                                onLongClick = { onLongPress(post) },
                            )
                        }
                    }
                }

                // 列表里**没有**区块标题行：fluxdo 首页从第一张卡片直接开始，口径只写在顶栏「最新」那里。
                // （上一版这里还有一行「帖子 · N」，用户点名不要，删掉。）

                if (error != null) {
                    item(key = "posts-error") { PostNote(text = error) }
                }

                if (loading && posts.isEmpty()) {
                    // 首屏还不知道有没有帖子：画四张灰卡而不是转圈。灰卡说的是"一列帖子马上到"，
                    // 转圈说的是"卡住了"。也不说"还没有人发帖"：那是另一件事。
                    items(count = 4, key = { "posts-skeleton-$it" }) {
                        PostSkeletonCard()
                    }
                }

                if (!loading && posts.isNotEmpty() && shown.isEmpty()) {
                    item(key = "posts-filtered-empty") {
                        PostNote(
                            text = if (board == PostBoard.Mine && authorId.isBlank()) {
                                "还没认出身份，「我的」这一栏要等身份就绪。"
                            } else if (board == PostBoard.Saved && savedTotal > 0) {
                                "收藏里有 $savedTotal 条，只是都还没拉到列表里：往下翻，或者点「加载更多」。"
                            } else if (board == PostBoard.Saved) {
                                "收藏是空的：在帖子详情里点「收藏」，它就会收到这一栏。"
                            } else {
                                "这个口径下没有帖子，换个分类或者点「最新」看看。"
                            },
                        )
                    }
                }

                if (posts.isEmpty() && !loading && error == null) {
                    item(key = "posts-empty") {
                        PostNote(
                            text = if (hasBackend) {
                                "还没有人发帖。第一帖可以是你在用的某个方案、某台机器，或者一句吐槽。"
                            } else {
                                "社区后端没连上：这一栏现在只是一张空页，连上没有帖子的时候才是真的没人发。"
                            },
                        )
                    }
                }

                items(normalShown, key = { "post-${it.id}" }) { post ->
                    PostCard(
                        post = post,
                        mine = authorId.isNotBlank() && post.authorId == authorId,
                        unread = unreadOf(post),
                        read = readOf(post),
                        saved = savedOf(post),
                        onClick = { onOpen(post) },
                        onLongClick = { onLongPress(post) },
                    )
                }

                if (hasMore && posts.size >= POSTS_PAGE) {
                    item(key = "posts-more") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            TextButton(onClick = onLoadMore, enabled = !loadingMore) {
                                Text(if (loadingMore) "正在拉…" else "加载更多")
                            }
                        }
                    }
                }

                // 列表末尾**不留**那行说明文字（「帖子只经后端读写…」/「现在只能看…」）：
                // 它不是内容，只是开发笔记，每屏最底下白占一段。身份没就绪这件事由 FAB 自己提示。
            }
        }
    }
}

/**
 * 卡片右侧那颗首图缩略的边长。
 *
 * 72dp 是**量出来的**，不是随手挑的：卡片右侧只剩"一行字的宽度"可用，而卡内两行内容
 * （标题 1~2 行 19.5sp + 间距 8 + 署名行 32dp）在单行标题时约 60dp 高。72 略高于它，
 * 所以有图的卡片只比无图的高 **十二三 dp**，缩略图自己也不会被压成一条；再大（比如 88）
 * 每屏就少半条帖子，读者是在扫列表、不是在刷图。参考 fluxdo 卡内缩略的高度（单图 16:9 夹
 * 80..144、多图 104，见 `topic_expandable_excerpt.dart:239-240`）：那边缩略**独占一行**、
 * 高度可以给到三位数，本仓是侧栏一格，所以取的是那个范围的下沿再收一档。
 */
private val PostCardThumbSize = 72.dp

/**
 * `![说明](https://…)` 整行时把地址取出来 —— 列表卡片用它找"首图"。
 *
 * 语义**逐条对齐** `MiniMarkdown.kt:1321` 的 `ImagePattern`
 * （`^!\[([^\]]*)\]\((https?://[^\s)]+)\)$`），只是这里要的是地址、不要说明，所以把第一组
 * 换成不捕获的 `[^\]]*`：
 *
 *  1. **整行**（`^…$` + 逐行 `trim()`）：`看这张 ![截图](url) 效果` 这种行内图**不算**——
 *     `MiniMarkdown` 那边它落进段落（`MiniMarkdown.kt:285-291` 的 `splitInlineImages`），
 *     不是块级图片，卡片照它的规则走；
 *  2. **只认 `http(s)`**：相对路径、`data:` 既下不到也没意义（同 `MiniMarkdown.kt:1318`）；
 *  3. **说明可空**：`![](url)` 与 `![截图](url)` 一视同仁（同 `MiniMarkdown.kt:1319`）；
 *  4. **地址里不能有空白与 `)`**（`[^\s)]+`）：否则 `![](a b)` 会被吞成一条假地址。
 *
 * 为什么不直接调 `MiniMarkdown` 里那个私有函数：它在另一个文件里、是 private，而且那条正则
 * 返回的是「说明 to 地址」；这里只需要地址。改 `MiniMarkdown.kt` 是别人的范围，不动它。
 */
private val PostCardImagePattern = Regex("""^!\[[^\]]*\]\((https?://[^\s)]+)\)$""")

/**
 * 正文里第一张**整行**图片的地址；没有就 null。
 *
 * 跳过围栏代码块里的内容：三反引号之间那些 `![](...)` 是代码，`MiniMarkdown` 也不把它们画成图
 * （`MiniMarkdown.kt:1191-1206`），卡片跟着一致 —— 否则一个贴 Markdown 教程的帖子，列表缩略
 * 会变成那段示例代码里的占位地址。
 *
 * 纯函数、没有 Compose 依赖：调用点用 `remember(...)` 包住（见 [PostCard]），
 * 列表滚动时不会每帧重跑正则。
 */
private fun firstImageUrl(body: String): String? {
    if (body.isEmpty()) return null
    var fence = false
    for (raw in body.lineSequence()) {
        val trimmed = raw.trim()
        // 围栏行自己先吃掉：开闭都算，里面的行一律不看。
        if (trimmed.startsWith("```")) {
            fence = !fence
            continue
        }
        if (fence) continue
        // 便宜的先判：绝大多数正文行不以 `![` 开头，正则连进都不用进。
        if (!trimmed.startsWith("![")) continue
        val hit = PostCardImagePattern.matchEntire(trimmed) ?: continue
        return hit.groupValues[1]
    }
    return null
}

/**
 * 摘要最多画几行：**无缩略 2 行**（上一版 3）。
 *
 * 行数上限本身照参考（`topic_expandable_excerpt.dart:32` 的 `this.maxCollapsedLines = 3`，
 * `topic_card_style.dart:57` 是同一个默认值），摘要摆的位置也一样：标题下、署名上，
 * 标题到摘要 4dp、摘要到署名 8dp（`topic_card.dart:489-493`）。
 *
 * **为什么本仓取 2 而不是 3**：参考的卡片默认**根本不画摘要**
 * （`topic_card_style.dart:54` 的 `showExcerpt = false`），它那个 3 是"用户手动打开摘要之后"
 * 的收起行数；本仓是**默认就画**摘要，于是 3 行变成了每张卡都要付的固定高度。
 * 从 3 收到 2，每张没缩略的卡少 16.9dp（一行），一屏（按 720dp 可视高度、卡均 ~120dp 计）
 * 大约多回 0.5~1 条。
 * 上一版把"参考的 3"当成"参考的默认值"抄了过来 —— 那个 3 是开关打开后的上界，不是默认形态。
 *
 * **没有做的两件事**（用户没要求，别顺手加）：
 *  1. 不改成"默认不画摘要"：参考能关是因为它有整卡样式配置，本仓没有那个开关，
 *     列表里"这帖讲什么"就全靠这一行摘要，关掉只剩标题；
 *  2. 不加"展开更多"按钮：卡片是列表里的一格，展开会改高度、把下面整屏重排，
 *     参考那边也只在**详情页**做展开，卡片这边只做截断。
 *
 * **这里搬的是"行数"，不是"行高"**：参考侧摘要 13sp / 行高 1.45
 * （`topic_expandable_excerpt.dart:132-133`），一行 18.85dp；本仓卡内摘要用 `bodySmall`
 * （FlTypography 里是 `FlText.footnote`，13sp），行高按卡内正文同一个比例 1.3 重算 → 16.9dp
 * （两行 33.8dp，参考那边三行 56.6dp）。
 * 不照抄 18.85 的原因：`bodySmall` 自带 18sp 行高（ratio 1.38），照抄的话同一张卡里标题 1.3、
 * 摘要 1.38 两种行距，有缩略图时摘要那一段会明显比左边松。
 */
private const val POST_CARD_EXCERPT_LINES_PLAIN = 2

/**
 * 摘要最多画几行：**有缩略 2 行**（本项未变）。
 *
 * 缩略 [PostCardThumbSize] 72dp 是从内容列的**宽度**里拿走的（72 + 间距 10 = 82dp），内容列窄了
 * 约两成；宽度少两成，同样 3 行装下的字数也少两成 —— 结果就是"三行被切碎的短句 + 一颗图"，
 * 摘要退化成几个关键词。
 *
 * 高度那边更直接：卡片高是 `max(内容列, 72)`。无缩略时内容列本来就约 60dp（标题 19.5 + 8 + 署名 40），
 * 加三行摘要（4 + 50.7）到 115dp 左右还匀称；有缩略时第 3 行那 16.9dp 是**纯增高**，卡片会被拉到
 * 缩略图的 1.6 倍高，右侧那颗方图就从"陪着一行字"变成"一大块空白旁边的小图标"。
 * 砍到 2 行（4 + 33.8）与 72 的图仍在同一量级，"图陪着一行字"的观感才成立。
 *
 * **和 [POST_CARD_EXCERPT_LINES_PLAIN] 现在是同值（都 2）**，但这个常量仍然单独留着：
 * 两个数是两条独立的判断依据（上面是"宽度被图拿走两成"，PLAIN 是"默认画摘要的整卡高度"），
 * 将来任一边再调（比如缩略改回 64、或摘要字号变）只改自己那一个，不必回头拆常量。
 * 合并成一个数会让下次调整时看不出"有图为什么是这个范围"。
 */
private const val POST_CARD_EXCERPT_LINES_WITH_THUMB = 2

/**
 * 块级行首标记：`#` 标题、`>` 引用、`-` / `*` / `+` 列表、`1.` / `1)` 有序列表。
 *
 * 只吃掉**行首一个标记 + 它后面的空白**：行中间的 `#`（`C#`）、行中间的 `-`（`2024-11-28`）不动。
 */
private val PostExcerptLineMarker = Regex("""^(?:#{1,6}\s*|>+\s*|[-+*]\s+|\d{1,9}[.)]\s+)""")

/** `![说明](https://…)` 整条丢，见 [plainExcerpt] 第 3 步。 */
private val PostExcerptImage = Regex("""!\[[^\]]*\]\([^)]*\)""")

/** `[文字](url)` 只留文字，见 [plainExcerpt] 第 4 步。 */
private val PostExcerptLink = Regex("""\[([^\]]*)\]\([^)]*\)""")

/** 行内码（两侧各一颗反引号）只留代码本身，见 [plainExcerpt] 第 5 步。 */
private val PostExcerptInlineCode = Regex("""`([^`]*)`""")

/** `**粗**`、`*斜*`、`~~删除~~` 的标记符号本身，见 [plainExcerpt] 第 6 步。 */
private val PostExcerptEmphasis = Regex("""\*+|~~""")

/**
 * 连续空白：收到一颗空格，见 [plainExcerpt] 最后一步。
 *
 * 只压行与行之间的空白还不够 —— 第 3 步把行内图片换成一颗空格之后，`还有 ![](a.png) 行内图`
 * 会留下三颗空格。参考侧收起状态也是整段压平（`topic_expandable_excerpt.dart:139` 的
 * `text.replaceAll(RegExp(r'\s+'), ' ').trim()`），这里跟上。
 */
private val PostExcerptSpaces = Regex("""\s+""")

/**
 * 把 `excerpt` 剥成纯文字，供列表卡片直接画。
 *
 * ## 为什么不渲染 Markdown
 *
 * `MiniMarkdown` 是一条完整解析器（`parseBlocks` 做块级切分 → 每个块再切行内 → 每种块一个组合项）：
 * 一条消息十几到几十个组合项。列表一屏十几张卡、`LazyColumn` 一次装 200 条，
 * 每张卡再挂一套解析器，滚动的时间预算就全花在摘要上了 —— 而摘要在这里只是"这帖大概讲什么"的
 * 一行灰字：不需要加粗、不需要代码底色、也不需要把 `![](…)` 画成图（图已经进了右侧那颗缩略）。
 * 详情页照旧用 `MiniMarkdown` 渲染全文，那边一次只有一条，值得这个成本。
 *
 * ## 剥的顺序（逐行，先块级、再行内，最后把行收成一段）
 *
 *  1. **围栏行整行丢**（三个反引号，或三个反引号 + 语言名），围栏**里面**的代码文本留着 ——
 *     读者要的是"这帖在讲什么"，把整段代码删掉反而是丢信息；
 *  2. 行首块级标记 [PostExcerptLineMarker]：井号标题、大于号引用、列表短横/星号/加号、有序列表；
 *  3. 图片 `![说明](url)` 整条丢 [PostExcerptImage]：说明多半是"截图""图片"，留着是一句废话；
 *  4. 链接 `[文字](url)` 只留文字 [PostExcerptLink]；
 *  5. 行内码只留代码 [PostExcerptInlineCode]，落单的反引号（没配成对的）直接去掉；
 *  6. 强调标记 `*` 与 `~~` 去掉 [PostExcerptEmphasis]；**下划线不剥** —— `snake_case`、`__init__`
 *     这类标识符里的下划线比强调用法常见得多，剥掉就把代码改错了；
 *  7. 空行、以及只剩标记符号的行（`-`、`***`、`>`、`###`）压掉，**不补空格**；"连续空行压成
 *     一个空格"落在行与行之间那一颗空格上（下面 `joined` 的拼接），所以整段仍是单行一段字；
 *  8. 最后整段再压一次空白 [PostExcerptSpaces]：第 3 步换出来的那颗空格、原文里的连续空格与
 *     制表符都收成一颗，和参考侧收起状态的口径一致（`topic_expandable_excerpt.dart:139`）。
 *
 * 只有标记的摘要（比如整段就是一颗 `**`）会一路剥成空串，调用点据此**一行都不画**。
 * 纯函数、没有 Compose 依赖；调用点用 `remember(post.excerpt)` 包住（见 [PostCard]），
 * 200 条列表每条只剥一遍，不是每帧。
 */
private fun plainExcerpt(text: String): String {
    if (text.isEmpty()) return ""
    val joined = StringBuilder(text.length)
    for (raw in text.lineSequence()) {
        val trimmed = raw.trim()
        // 1. 围栏行自己吃掉：开闭都算，围栏里的代码行照常往下走。
        if (trimmed.startsWith("```")) continue
        // 2. 行首块级标记。
        var line = PostExcerptLineMarker.replace(trimmed, "")
        // 3. 行内图片整条丢。换成空格而不是空串：`文字![](a.png)文字` 才不会粘成一个词。
        line = PostExcerptImage.replace(line, " ")
        // 4. 链接只留文字（`\$1` 转义，Kotlin 字符串里的 `$` 是模板起始符）。
        line = PostExcerptLink.replace(line, "\$1")
        // 5. 行内码只留代码，再扫掉落单的反引号。
        line = PostExcerptInlineCode.replace(line, "\$1")
        line = line.replace("`", "")
        // 6. 强调标记。
        line = PostExcerptEmphasis.replace(line, "")
        line = line.trim()
        // 7. 空行 / 纯标记行压掉；行与行之间补**一颗**空格，等价于"连续空行压成一个空格"。
        if (line.isEmpty() || line.all { c -> c in "#>-+*_~`" }) continue
        if (joined.isNotEmpty()) joined.append(' ')
        joined.append(line)
    }
    // 最后一步：整段压平（行内图片换出来的那颗空格、原文里的连续空格、制表符都收成一颗）。
    return PostExcerptSpaces.replace(joined.toString(), " ").trim()
}

/**
 * 列表上的一张帖子卡片。
 *
 * 画出来三块：**标题**（15sp / 行高 1.3、最多 2 行）→ 间距 4 → **摘要**（`bodySmall` / 行高 1.3、
 * 最多 [POST_CARD_EXCERPT_LINES_PLAIN] 行、**空则不画**）→ 间距 8 → **署名块**（头像 32 +
 * 昵称/时间 + 分类标签/赞回复）。摘要取自列表回答里的 `excerpt`（`PostStore.kt:19`：列表只发
 * `excerpt`，`body` 是空的），先经 [plainExcerpt] 剥成纯文字再画。
 *
 * 有首图时右侧多一颗 [PostCardThumbSize] 的方缩略（[PostImageThumb]），首图由 [firstImageUrl] 认。
 * **缩略图不用行数**：标题仍是 `maxLines = 2` 那条上限（见本函数里那个 `Text`），只把可用宽度让出
 * 72 + 10dp，所以有图不会让标题多折一行。缩略图唯一改行数的地方是摘要：有缩略时
 * [POST_CARD_EXCERPT_LINES_WITH_THUMB] 行，没缩略时 [POST_CARD_EXCERPT_LINES_PLAIN] 行
 * （理由见两个常量的 KDoc）。没有首图时 `Row` 里只有内容列本身，连 `Spacer` 都不加 ——
 * 布局与加缩略图之前逐项相同。
 *
 * [unread] 是"没看过 / 有新回复"：**卡片右上角**一颗 8dp 主色圆点（叠层画的，不参与布局），扫列表时先认它。
 */
/**
 * 列表上的一张帖子卡片，逐项照 fluxdo 的 `TopicCard`（`lib/widgets/topic/topic_card.dart`
 * 的 `_buildNormalBody` + `_buildMetadataBlock`，默认 `TopicCardAvatarLayout.inline`）：
 *
 * ```
 * Column(
 * 行1: 标题（满宽，最多两行，15sp/1.3）
 *   间距 4
 *   行1.5: 摘要（满宽，最多 3 行 / 有缩略时 2 行，bodySmall/1.3，空则不画）
 *   间距 8
 *   行2: 头像 32 跨两行 + 右侧 Column(
 *         行A: 作者名（Expanded）+ 8 + 时间
 *         间距 2
 *         行B: 分类+标签（Expanded）+ 8 + 统计（赞 10 回复）
 *        )
 * )
 * ```
 *
 * 卡片自身：圆角 10、内边距 12/10/12/10、底部间距 8 —— 这三个数直接来自
 * `topic_card.dart:323/329/363`（`cardRadius = borderRadius(10)`、`EdgeInsets.only(bottom: 8)`、
 * `EdgeInsets.fromLTRB(12, 10, 12, 10)`），不是估的。**左右外留白不在卡上**，在列表的
 * `contentPadding` 上（见 [PostListPane]）。
 *
 * **摘要为什么是这个位置、这两个数**：位置与间距照 `topic_card.dart:486-496`（标题 → 4 → 摘要 →
 * 8 → 署名，官方注释写的是"Gmail snippet 的位置"）；**不要展开** —— 参考侧收起时靠 `TextPainter`
 * 测行、再挂渐隐 + `TextButton`（`topic_expandable_excerpt.dart:141-190`），列表里一张卡两种高度
 * 会让滚动中的位置一直跳，这里只做 `maxLines` 截断。**空摘要一个组合项都不建**（连那颗 4dp 的
 * Spacer 也不建），所以"没摘要 + 没缩略"的卡片与加摘要之前**逐项相同**。
 *
 * **故意不跟 fluxdo 的项**：
 *  1. `TopicCardStyle.showExcerpt = false`、`showImages = false`（`topic_card_style.dart:54-55`）。
 *     fluxdo 默认不显示摘要，是因为它的摘要要**额外发请求**取首楼（`allowExcerptFetch`，
 *     `topic_card_style.dart:39-40` 明说"默认关闭以避免增加列表请求量"）。本仓的 `excerpt` 已经在
 *     `posts_for` 的列表回答里（后端 `left(body, 200)`），不发第二次请求，所以摘要**默认画**。
 *  2. `maxCollapsedLines = 3`（`topic_card_style.dart:57`）：这里只搬了"3"这个**行数**，没搬
 *     "点一下展开"那一套。而且有缩略时改成 2 行（[POST_CARD_EXCERPT_LINES_WITH_THUMB]）——
 *     缩略占了内容列 82dp 的宽度，三行会被切得只剩关键词。
 *  3. 未读点的**摆法**：fluxdo 把 `unreadIndicator` 摆进标题行右端的**固定槽位**
 *     （`topic_card.dart:296` 与 `:428-433` 的 `if (unreadIndicator != null) … Padding(top: …)`，
 *     `:879-886` 那颗 8×8 的蓝点），它参与布局 —— 所以"有未读"的标题比"已读"的少一截可用宽度，
 *     同一列里换行位置会不一样。本仓改成画在卡片背景层上（见下一段），标题永远满宽。
 *  4. `showImages = false` 这条**只跟了一半**：缩略图确实进卡了，但走的是右侧一颗
 *     [PostCardThumbSize] 方图（[PostImageThumb]），不是 fluxdo 那条"摘要下面按内容宽铺一行"
 *     （`topic_expandable_excerpt.dart:233-277` 的 `_buildImages`）。理由见 [PostCardThumbSize] 的 KDoc。
 *
 * 标题为什么独占一行：论坛列表以标题为主信息，照片和署名排在它后面，视线沿左边缘直扫。
 *
 * [unread] 画在**卡片右上角**（标题首行右侧、卡片右内边距那条沟里），[saved] / [mine] 只加在名字后面，
 * 不抢标题的注意力。
 *
 * ## 未读点为什么这样画（改这里之前先读这三条）
 *
 * 1. **为什么用 `drawBehind`，而不是叠一个 `Box`**：`Box` 有两难。塞进标题 `Row` 里（fluxdo 就这么做）
 *    会参与测量，未读卡片的标题可用宽度少 `6 + 8 = 14dp`，同一屏里"读过"与"有未读"的标题折行位置不同；
 *    改成 `Box { Column(卡片内容); Box(Modifier.align(TopEnd)) }` 又要把 `clip` / `background` /
 *    `combinedClickable` 整条修饰符链挪到外层 `Box` 上，圆角裁切、水波纹、长按手势的作用范围全部
 *    跟着变一次，等于重排这张卡片。`drawBehind` 只在同一个节点上多一条 draw 记录：卡片节点、
 *    测量尺寸、手势区域一个都不动，而且它排在 `clip` 之后，点天然被圆角裁在卡片里。
 * 2. **为什么是 8dp 圆点 + 1.5dp 描边**：参考实现是 6dp 圆点 + 1dp `surface` 描边
 *    （`post_header_section.dart:334-345` 的 `width: 6, height: 6` +
 *    `Border.all(color: theme.colorScheme.surface, width: 1)`），那是**楼层头**里的一颗点，
 *    旁边的字比列表标题小一号。本仓列表卡片的标题是 15sp（见本函数标题的 `fontSize = 15.sp`），
 *    字号大一号，点也大一号：`6 → 8`（1.33 倍）、`1 → 1.5`（1.5 倍）。描边多放大半档不是手滑：
 *    1.33dp 在 2x/3x 屏上会被取整吃掉半条像素，看着像根本没描边，1.5dp 才落得住。
 *    描边画在**内侧**（`radius = 4dp - 0.75dp`），所以视觉外径正好 8dp，不会额外撑出一圈 1.5dp 的边。
 * 3. **为什么 `reads == null` / 认不出身份 / 已读时一个组合项都不建**：这个点的显隐只由传进来的
 *    [unread] 决定，而它来自 `PostsScreen` 的 `unreadOf`：`reads != null && reads.hasHistory() &&
 *    reads.isUnread(post.id, post.comments)`。三档各自的理由是 ——
 *    · `reads == null`（本机存储还没起来）：算不出"有没有新回复"，画点等于撒谎；
 *    · `hasHistory() == false`（全新装上，一条都没读过）：满屏都是点，等于没有提示；
 *    · 已读：`isUnread` 自己就返回 `false`；认不出身份时 `post.id <= 0L`，`isUnread` 同样返回 `false`
 *      （见 `PostReadStore.isUnread`）。
 *    这三档在卡片里都只是"`drawBehind` 里那个 `if (!unread) return@drawBehind`" —— 不加空 `Box`、
 *    不加 `Spacer`、不进布局，卡片仍是同一个组合项、同一个尺寸。
 */
@Composable
private fun PostCard(
    post: CommunityPost,
    mine: Boolean,
    unread: Boolean,
    read: Boolean,
    saved: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val stamp = relativeStamp(post.createdAt)
    // 首图：正文里第一条**整行**的 `![](https://…)`，规则与 `MiniMarkdown` 的 `ImagePattern` 一致
    // （见 [PostCardImagePattern] 与 [firstImageUrl]）。
    //
    // 为什么还要看 `excerpt`：列表回答里 `body` 是**空的**（`PostStore.kt:19` 写明"列表只发
    // `excerpt`"），只认 `body` 等于这条缩略图在实际列表里永远画不出来。`excerpt` 是后端对正文取的
    // `left(body, 200)` 原文（`20261124160000_community_posts.sql:152`），Markdown 没被洗掉，
    // 首图落在前 200 字里时照样能认出来；认不出就当这条帖没图。
    //
    // 为什么放进 `remember`：卡片是 `LazyColumn` 的项，滚动时反复重组；`remember(post.body,
    // post.excerpt)` 让这两条正则**每条帖子只跑一次**（200 条列表 = 200 次，不是每帧 200 次）。
    val thumbUrl = remember(post.body, post.excerpt) {
        firstImageUrl(post.body) ?: firstImageUrl(post.excerpt)
    }
    // 摘要纯文字：`excerpt` 是后端 `left(body, 200)` 的**原文**（Markdown 没洗过），先经
    // [plainExcerpt] 剥掉标记再画（为什么不渲染 `MiniMarkdown`、剥哪些标记，见那个函数的 KDoc）。
    // 同样放进 `remember(post.excerpt)`：200 条列表每条只剥一遍，滚动时不重跑那串正则。
    val excerpt = remember(post.excerpt) { plainExcerpt(post.excerpt) }
    // 三档颜色，照 `topic_card.dart:288-293`：
    //   未读       标题 onSurface             元信息 onSurfaceVariant ×0.8
    //   读过没读完 标题 onSurfaceVariant×0.75  元信息 onSurfaceVariant ×0.8
    //   全读完     标题 onSurfaceVariant×0.75  元信息 onSurfaceVariant ×0.6
    val titleColor = if (read) {
        metaTint(scheme.onSurfaceVariant, 0.75f)
    } else {
        scheme.onSurface
    }
    // 时间那一格：未读用主色（和右上角那颗点呼应），其余跟着元信息色走。
    val timeColor = if (unread) {
        scheme.primary
    } else {
        metaTint(scheme.onSurfaceVariant, if (read) 0.6f else 0.8f)
    }
    // 摘要的字：`bodySmall`（卡内**不再放大** —— 它已经是本仓最小的一档正文，摘要不该和标题抢），
    // 行高按卡内正文同一个比例 1.3 重算：`bodySmall` 自带 18sp（ratio 1.38），跟标题的
    // 19.5/15 = 1.3 不是同一个行距，同一张卡里两种行距、有缩略时右边那段会显松。
    // 13sp × 1.3 = 16.9sp；万一把这套排版换成非 sp 单位，退回同一个数。
    val excerptBase = MaterialTheme.typography.bodySmall
    val excerptStyle = excerptBase.copy(
        lineHeight = if (excerptBase.fontSize.isSp) excerptBase.fontSize * 1.3f else 16.9.sp,
    )
    // 摘要色：`onSurfaceVariant`；读过的帖再褪一档 0.7（照 `topic_expandable_excerpt.dart:134-136`
    // 的 `isFullyRead ? 0.7 : 1`）—— 否则一张"已读完"的卡里摘要比标题还亮，视线先落在摘要上。
    val excerptColor = if (read) {
        metaTint(scheme.onSurfaceVariant, 0.7f)
    } else {
        scheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 卡外只有 bottom = 8（`topic_card.dart:329` 的 `EdgeInsets.only(bottom: 8)`）。
            // 左右那 12 不在卡上：`topics_page.dart:1769` 是 `EdgeInsets.only(left: 12, right: 12)`
            // 套整张列表层。所以水平留白由 `PostListPane` 的 `LazyColumn.contentPadding` 给，
            // 这里再写一遍等于 24，卡片会明显缩进、跟搜索胶囊的左缘对不齐。
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            // 未读点**画在背景层上**，不进布局（为什么这么画见 [PostCard] 的 KDoc「未读点为什么这样画」）。
            // 这一条排在 `background` 之后、内边距之前，所以 `size` 就是卡片自身的尺寸
            // （宽 = 这一列的宽度；高 = 卡片高，卡间距那 8dp 已经被上面的 `padding(bottom = 8)` 扣掉），
            // 坐标原点就是卡片左上角。
            .drawBehind {
                // 只有"有未读"才画：其余三档（`reads == null`、认不出身份、已读）连 draw 都不进，
                // 不是"画个透明的点占着位置"。
                if (!unread) return@drawBehind
                // 外径 8dp、描边 1.5dp，描边画在内侧 → 视觉外径仍是 8dp（与 Flutter
                // `Border.all` 的"描边占盒内"口径一致，见 `post_header_section.dart:334-345`）。
                val stroke = 1.5.dp.toPx()
                val radius = 4.dp.toPx() - stroke / 2f
                // 位置：右上角，落在卡片右侧 12dp 内边距那条沟里（点的右缘离卡边 2dp），
                // 所以它永远压不到标题文字；纵向对齐标题**首行**的中线 = 顶内边距 10dp
                // + 首行行高 19.5sp 的一半（标题的 `lineHeight = 19.5.sp`）。
                val center = Offset(
                    x = size.width - 6.dp.toPx(),
                    y = 10.dp.toPx() + 19.5.sp.toPx() / 2f,
                )
                drawCircle(color = scheme.primary, radius = radius, center = center)
                // 描边用 `surface`（`post_header_section.dart:340-343` 的
                // `Border.all(color: theme.colorScheme.surface, width: 1)`）：卡片底色是
                // `FlSurfaceLevel.Block`，圆点直接压上去边缘会和底色糊在一起，一圈底色描边把它托出来。
                drawCircle(
                    color = scheme.surface,
                    radius = radius,
                    center = center,
                    style = Stroke(width = stroke),
                )
            }
            // 长按 = 收藏 / 取消收藏：列表上最常用的两个动作（看、收）各占一种手势，不必先进详情。
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = "收藏或取消收藏",
                onLongClick = onLongClick,
                onClick = onClick,
            )
            // 圆点没有节点（`drawBehind` 画的），读屏软件看不见它，所以"有未读"在这里补一句话。
            // 排在 `combinedClickable` 之后：并进它那个按钮节点一起念，不新增一个读屏停靠点。
            .semantics {
                if (unread) stateDescription = "有未读"
            }
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
) {
        // 内容列（原样）+ 右侧首图缩略：**没有首图时这一层不建**，见 [firstImageUrl] 与
        // `if (thumbUrl != null)` —— 无图卡片的组合项、测量尺寸与改动前逐项相同。
        //
        // 为什么把原来那两行（标题 / 头像+元信息）包进一个 `Column(weight(1f))`：缩略图要占
        // 右侧那一格，内容必须先有一个「可被挤压」的宽度；`weight(1f)` 让标题与署名行的可用宽度
        // 自动变成「卡宽 - 缩略 - 间距」，不用在任何地方写一个写死的 dp。没有缩略图时这个 Column
        // 独吃整行，两行内容的位置、行高、间距（8dp 的 Spacer）与改动前完全一致。
        Row(
            modifier = Modifier.fillMaxWidth(),
            // 顶对齐：缩略图与标题**首行**齐平，否则单行标题的卡片里缩略会浮在署名行上方。
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 行1：标题满宽。这一行必须 fillMaxWidth —— 标题吃 weight(1f)，无界宽度下会被算成 0。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (post.pinned) {
                        Icon(Icons.Rounded.PushPin, null, Modifier.size(13.dp), tint = PostPinned)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (post.locked) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(13.dp), tint = titleColor.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = post.title.ifBlank { "（无标题）" },
                        // 15sp / 行高 1.3：与 `TopicCardStyle.titleFontSize` 的默认值同一个数，
                        // 行高 19.5 就是 `topic_card.dart:314` 的 `height: 1.3`。
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 19.5.sp),
                        // 读完的帖子退一档：w600 → w500（`topic_card.dart:313` 的 `isFullyRead ? w500 : w600`）
                        fontWeight = if (read) FontWeight.Medium else FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // 未读点**不在这里**：它由卡片自己的 `drawBehind` 画在右上角（零布局影响）。
                    // 上一版在这一行末尾塞过 `Spacer(6dp) + Box(8dp)`，那 14dp 会从标题的可用宽度里扣掉，
                    // 同一列里"有未读"和"已读"的标题换行位置就不一样了。
                }

                // 行1.5：摘要。位置与间距照参考 `topic_card.dart:486-496`（标题 → 4 → 摘要 → 8 → 署名，
                // 那边注释写的是"Gmail snippet 的位置"）：所以下面那颗 8dp 的 Spacer 原封不动留着，
                // 这里只补一颗 4dp 的。顺序是 标题 →[4]→ 摘要 →[8]→ 署名。
                //
                // **空摘要一个组合项都不建**，连这颗 4dp 的 Spacer 也不建 —— 于是"没摘要 + 没缩略"的
                // 卡片与加摘要之前**逐项相同**（标题 → 8 → 署名还是原来那颗 Spacer），不会多出一层空
                // Column、一个空 Text 或一截间距。
                //
                // 行数：无缩略 [POST_CARD_EXCERPT_LINES_PLAIN]、有缩略 [POST_CARD_EXCERPT_LINES_WITH_THUMB]
                // （为什么两个数见两个常量的 KDoc）。高度**不写死**，靠 `maxLines` 自撑，卡片跟着长高。
                //
                // 读屏：这里**不加** `contentDescription` —— `Text` 自带 `text` 语义，再写一遍等于同一段
                // 念两遍。也**没有**给整段 `invisibleToUser()`：卡片是 `combinedClickable` 合并出来的
                // 一个节点，朗读顺序就是标题 → 摘要 → 署名（与视觉顺序一致）；而列表里不少帖子的标题
                // 本身就是"求助""问一下"这种不自描述的短句，摘要是"这帖讲什么"的唯一信息源，藏掉之后
                // 读屏用户只能靠点进去判断。代价是每条多念一两句（`excerpt` 最长 200 字），换的是
                // 进不进得去由听的人自己判断。
                if (excerpt.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = excerpt,
                        style = excerptStyle,
                        color = excerptColor,
                        maxLines = if (thumbUrl != null) {
                            POST_CARD_EXCERPT_LINES_WITH_THUMB
                        } else {
                            POST_CARD_EXCERPT_LINES_PLAIN
                        },
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 行2：头像跨两行（radius 16 = 直径 32），右边上下两行小字。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PostAvatar(
                        name = post.author.ifBlank { "匿" },
                        seed = post.authorId.ifEmpty { post.author },
                        size = 32.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // 行A：署名（占满）+ 时间。未读时时间也用主色，和那个点呼应。
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AuthorLabel(
                                name = post.author.ifBlank { "匿名" },
                                // 署名行 **12sp / 行高 16**（`labelMedium` → FlTypography 的
                                // `FlText.caption1`，见 `FlTypography.kt:50`）。
                                //
                                // 参考 `topic_card.dart:822-832` 这一行是 `labelSmall`（11sp）；
                                // 本仓上一版写 `labelLarge`（`FlText.callout`，16sp/21）——
                                // 比同一张卡里的摘要（`bodySmall` 13sp）**还大**，
                                // 「谁说的」压过「说了什么」，卡片的层级是颠倒的。
                                //
                                // 为什么不直接取参考的 11：本仓这张卡的标题 15sp、缩略 72dp
                                // 都比参考大一号（参考 `TopicCardStyle.titleFontSize = 15` 但整卡
                                // 更紧凑），整体大一档时把署名一个人压到 11 会显得字"抖"、
                                // 与旁边 13sp 的摘要差两档；取 12 只差一档，层级清楚又不塌。
                                //
                                // 这一行里**只有主文案**动：右边的时间戳仍是 `labelSmall`，
                                // 徽章 / 图标 / 颜色都不碰。
                                style = MaterialTheme.typography.labelMedium,
                                // 读过就跟着元信息色退灰，未读提亮到 onSurface×0.85（`topic_card.dart:825-827`）
                                color = if (read) {
                                    metaTint(scheme.onSurfaceVariant, 0.6f)
                                } else {
                                    metaTint(scheme.onSurface, 0.85f)
                                },
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (mine) {
                                Spacer(modifier = Modifier.width(5.dp))
                                PostBadge(text = "我", color = scheme.primary)
                            }
                            if (saved) {
                                Spacer(modifier = Modifier.width(5.dp))
                                PostBadge(
                                    text = "收藏",
                                    color = scheme.secondary,
                                    foreground = scheme.onSecondary,
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (stamp.isNotBlank()) {
                                Text(
                                    text = stamp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = timeColor,
                                    maxLines = 1,
                                )
                            }
                        }
                        // 行B：分类 + 标签（占满）+ 赞 / 回复。
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PostMetaChips(
                                category = post.category,
                                tags = post.tags,
                                maxTags = 2,
                                // 全读完时整行退灰：fluxdo 是 0.55（`topic_card.dart:851`）。
                                dim = if (read) 0.55f else 1f,
                                categoryColor = post.categoryColor,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            PostMetric(glyph = Icons.Rounded.Favorite, count = post.likes, active = post.mineLike)
                            Spacer(modifier = Modifier.width(10.dp))
                            // 回复数用主色：一列里它是最想让人点进去的那个数。
                            PostMetric(glyph = Icons.Rounded.ChatBubble, count = post.comments, active = true)
                        }
                    }
                }
            }
            // 有图才多这两个孩子：没图时 Row 里只有上面那个内容列，一个 Spacer 都不留。
            if (thumbUrl != null) {
                Spacer(modifier = Modifier.width(10.dp))
                // 72dp 的来历见 [PostCardThumbSize]；缩略图自己**不挂手势**（见 PostImageThumb），
                // 点击与长按照旧落在卡片这一层的 `combinedClickable` 上。
                PostImageThumb(url = thumbUrl, size = PostCardThumbSize)
            }
        }
    }
}

/**
 * 卡片右下角的一个统计：一个小图标 + 数字。
 *
 * 用 `Icons.Rounded.*`：爱心与气泡是 fluxdo 同一套（`favorite_rounded` / `chat_bubble_rounded`），
 * 一眼认得出的形状，所以用 Material 的 outlined 版，颜色跟着主题走。
 */
@Composable
private fun PostMetric(glyph: ImageVector, count: Int, active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // fluxdo `topic_card.dart:909-939`：统计图标与数字都是 `onSurfaceVariant @ 0.75`，
    // 只有"我赞过了"才换成 primary。本仓原来用 `outline`（比 onSurfaceVariant 更淡），
    // 整行会显得比作者名还弱，与源码的层级相反。
    val ink = if (active) scheme.primary else metaTint(scheme.onSurfaceVariant, 0.75f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = ink,
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * 首屏占位卡片：灰条的形状与真卡片一样（标题两行 → 作者行 → 分类行）。
 *
 * 数值照 `topic_list_skeleton.dart:40-84` 的 `_TopicCardSkeleton`：`Card(margin: only(bottom: 8))`
 * → 圆角 10 → 内边距 `fromLTRB(12, 10, 12, 10)` → 标题两条 16 高（第二条 180 宽）→ 间距 8 →
 * 头像圆 32 + 间距 8 + 右侧两行（72/44 与 110/32，行间距 6）。
 *
 * 目的：转圈看不出下面会出现什么，灰卡看得出是一列帖子。
 */
@Composable
private fun PostSkeletonCard() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 和真卡片同一套边距口径：外面只有 bottom = 8，左右交给列表的 contentPadding。
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBar(widthFraction = 1f, height = 16.dp)
        SkeletonBar(width = 180.dp, height = 16.dp)
        // 署名块：头像 32 跨两行，右边昵称/时间、分类/统计两行。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceVariant),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                // 6 来自 `topic_list_skeleton.dart:71` 的 `SizedBox(height: 6)`：
                // 这两行是"昵称/时间"和"分类/统计"，比外层 8 的两组之间更近。
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBar(width = 72.dp, height = 11.dp)
                    Spacer(modifier = Modifier.weight(1f))
                    SkeletonBar(width = 44.dp, height = 11.dp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBar(width = 110.dp, height = 11.dp)
                    Spacer(modifier = Modifier.weight(1f))
                    SkeletonBar(width = 32.dp, height = 11.dp)
                }
            }
        }
    }
}

/**
 * 骨架里的一条灰条。两个重载分开写，不塞进一个带分支的 `Modifier`：
 *
 * - 比例版：**吃满一行**，只用在 `Column` 里（标题条）。
 * - 定宽版：**固定像素宽**，用在 `Row` 里——比例版在那儿会把整行撑满、把邻居挤出去。
 *
 * [height] 默认 10 是上一版小标题条的高度；`topic_list_skeleton.dart` 里标题是 16、
 * 元信息是 11，都从调用点传。
 */
@Composable
private fun SkeletonBar(widthFraction: Float, height: Dp = 10.dp) {
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth(widthFraction)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun SkeletonBar(width: Dp, height: Dp = 11.dp) {
    Box(
        modifier = Modifier
            .height(height)
            .width(width)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}


/**
 * 详情底部「相关话题」的一行，照 fluxdo `topic_more_topics.dart:343-419`：
 *
 * - **没有卡片背景**：`InkWell` + `padding(horizontal = 16, vertical = 9)` 的通栏行
 * - 左侧**创建人头像 32dp** + 10dp 间距（上一版没有头像）
 * - 标题 `bodyMedium` + w500 + 行高 1.35、最多 2 行
 * - 元信息 `labelSmall`：左边分类，右边回复数 + 「·」+ 相对时间
 *
 * 它比列表卡片轻：这里是"看完这帖还能顺手看什么"，不是再铺一张列表。
 */
@Composable
private fun RelatedPostRow(post: CommunityPost, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.padding(top = 1.dp)) {
            PostAvatar(
                name = post.author.ifBlank { "匿" },
                seed = post.authorId.ifEmpty { post.author },
                size = 32.dp,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = post.title.ifBlank { "（无标题）" },
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (post.category.isNotBlank()) {
                    Text(
                        text = post.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = metaTint(scheme.onSurfaceVariant, 0.8f),
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Rounded.ChatBubble,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = metaTint(scheme.onSurfaceVariant, 0.8f),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "${post.comments}",
                    style = MaterialTheme.typography.labelSmall,
                    color = metaTint(scheme.onSurfaceVariant, 0.8f),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(12.dp))
                val stamp = relativeStamp(post.createdAt)
                if (stamp.isNotBlank()) {
                    Text(
                        text = stamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = metaTint(scheme.onSurfaceVariant, 0.8f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 复制到剪贴板的那一份：标题、作者与时间、正文、分类与标签。
 *
 * 不拼帖子 id、也不拼站内链接：这个社区没有可分享的帖子地址，拼出来别人也点不开，
 * 反而像一条坏链接。要传给别人时，贴上去的就是能读的整段字。
 */
private fun copyablePost(post: CommunityPost): String = buildString {
    appendLine(post.title.ifBlank { "（无标题）" })
    append("—— ")
    append(post.author.ifBlank { "匿名" })
    // 日期用绝对的那一段（ISO 前 10 位就是 yyyy-MM-dd）：复制出去的文字可能是过几天才被人看到，
    // "3 分钟前"贴到别处就失真了。
    val day = post.createdAt.take(10)
    if (day.length == 10) {
        append(" · ")
        append(day)
    }
    appendLine()
    if (post.body.isNotBlank()) {
        appendLine()
        appendLine(post.body.trim())
    }
    if (post.category.isNotBlank() || post.tags.isNotEmpty()) {
        appendLine()
        if (post.category.isNotBlank()) append("#${post.category} ")
        post.tags.forEach { tag -> append("#$tag ") }
    }
    appendLine()
    append("（${post.comments} 回复 · ${post.likes} 赞）")
}

/**
 * 复制**某一楼**到剪贴板的那一份：楼层号、作者、日期、正文。
 *
 * 与 [copyablePost] 同一套写法（日期取 `createdAt` 前 10 位那个绝对值，不用「3 分钟前」：
 * 复制出去的字可能是过几天才被人看到），只是把「标题 + 分类 + 标签」换成了 `#N`。
 *
 * 楼层号由调用点传进来（[PostThreadPane] 里 `items(floorRows)` 那个 `index + 2`，和卡片上
 * 画出来的 `#N` 是**同一个表达式**）：在这边重新按 `comments` 扫一遍也算得出来，但那样
 * 就多了一份"楼层号怎么算"的实现；而这正是本文件反复强调只该有一处的知识
 * （见 [FLOORS_FIRST] 那段注释）。
 *
 * 参考 fluxdo 那一侧的剪贴板动作（`post_footer_section/actions/menu_actions.dart:320-348`：
 * 动作就是 `Clipboard.setData` + 一条成功 toast）：那边菜单里只有调试用的 cooked HTML /
 * 签名，**没有**"复制这一楼"；本仓补上它，并把楼层号与日期一起拼进去 ——
 * 复制出去的字常被转到别处（群里、文档里），"第几楼 + 哪天"是它唯一还能对回原帖的凭据。
 */
private fun copyableFloor(floor: Int, comment: PostComment): String = buildString {
    append("#$floor")
    append(" —— ")
    append(comment.author.ifBlank { "匿名" })
    // 绝对日期（ISO 前 10 位就是 yyyy-MM-dd）；解析不出形状时干脆不写这一段。
    val day = comment.createdAt.take(10)
    if (day.length == 10) {
        append(" · ")
        append(day)
    }
    appendLine()
    if (comment.body.isNotBlank()) {
        appendLine()
        appendLine(comment.body.trim())
    }
}

/**
 * 举报某一楼时写进剪贴板的那一段：**帖子标题 + 楼层号 + 作者 + 理由**（外加两个编号）。
 *
 * 为什么是"复制"而不是发一个请求：本仓后端**没有举报 RPC** —— `PostStore` 只有
 * `posts_list` / `post_get` / `post_add` / `post_comment_add` / `post_like` / `post_delete`
 * 六个方法，没有任何一条能收下举报。所以提交这一步如实落在剪贴板上，由人贴到反馈群里；
 * 界面上也写清了这件事（见 [FloorReportSheet] 里那行说明）。**不**造一个发出去必然失败的
 * 请求，也不弹"举报成功"—— 那是在骗点按钮的人。
 *
 * 帖名与楼层号都写全：群里收到这一段的人只看这几行，得能对回原帖的哪一层。楼层号由调用点
 * 传（与卡片上的 `#N` 是同一个表达式，见 [copyableFloor] 的说明），这边不再扫一遍 `comments`。
 */
private fun reportableFloor(
    post: CommunityPost,
    comment: PostComment,
    floor: Int,
    reason: String,
): String = buildString {
    appendLine("【举报】")
    append("帖子：")
    appendLine(post.title.ifBlank { "（无标题）" })
    append("楼层：#")
    append(floor)
    append(" · ")
    appendLine(comment.author.ifBlank { "匿名" })
    val day = comment.createdAt.take(10)
    if (day.length == 10) {
        append("时间：")
        appendLine(day)
    }
    append("理由：")
    appendLine(reason)
    // 编号给人对账用：帖名可能被别人改过、楼层号会随删帖变，这两个数不会。
    append("（帖子编号 ")
    append(post.id)
    append(" · 楼层编号 ")
    append(comment.id)
    append("）")
}

/**
 * 一条帖子的详情：主楼 + 回帖按楼层排，底下吸着一个回复栏。
 *
 * 用整页而不是对话框：回帖一多，对话框里那点高度连三层都看不完，「楼层」这个概念也就没了。
 */
@Composable
private fun PostThreadPane(
    post: CommunityPost?,
    comments: List<PostComment>,
    loading: Boolean,
    error: String?,
    busyKey: String?,
    reply: String,
    authorId: String,
    related: List<CommunityPost>,
    /** 这条帖子现在收着没有。 */
    saved: Boolean,
    /**
     * 「上次读到哪一屏」的记录（见 [PostPositionStore]）；null 时既不恢复也不记录。
     *
     * 放在这一层而不是 `PostsScreen`：恢复与记录都要 `listState`，而 `listState` 只在这里
     * （`rememberLazyListState()`）。往上提一格等于把整个列表的状态也提上去，`PostsScreen`
     * 里就会多出一份只给详情页用的状态。这与目录面板 / 帖内搜索"开关在上面、用状态的在下面"
     * 的分工相反，因为那两个是**入口在上**（顶栏菜单），这一个没有任何入口。
     */
    positions: PostPositionStore? = null,
    /**
     * 这一次打开这条帖子时，标记已读**之前**它算不算有新的回帖（见 `PostsScreen` 里的
     * `resumeUnread`）。为 true 才在楼层列表开头画那条 [ThreadResumeBar]；
     * 为 false 时那一格一个组合项都不建，`thread-*` 的 item 序列与改动前完全一样。
     */
    resumeUnread: Boolean = false,
    /**
     * 上次记下的回帖数（`readMark`，在 `markRead` 之前取的）。
     *
     * 与 [resumeUnread] 配套：true 时才用得到的"精确位置"；`null` 时续读条只写「后面有新回复」。
     */
    resumeMark: Int? = null,
    /**
     * 这一次发送要挂到哪一楼下面（引用回复）；null 表示普通回帖。
     *
     * 只影响回复栏胶囊的文案（「回复 #N · 名字」）——正文里那段 `> 作者：…` 是另一件事
     * （[onQuote] 塞进去的），两者可以并存，也允许人删掉正文里的引用段却仍然挂在那一楼下面。
     */
    replyTo: PostComment? = null,
    onReplyChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleSave: () -> Unit,
    onQuote: (PostComment) -> Unit,
    onCopy: () -> Unit,
    /**
     * 复制**某一楼**（`#N` + 作者 + 日期 + 正文）。
     *
     * 为什么是回调而不是在这里直接摸 platform：本文件里 [PostThreadPane] 是 private composable，
     * 手上没有 `CantaPlatform`（那一路上来的是数据与动作，不是平台句柄）。把 platform 顺着
     * `PostsScreen → PostThreadPane` 塞进去，等于让每个 composable 都能随手弹 toast、写剪贴板，
     * 与 [onCopy]（复制整帖）这条已经存在的链路保持同一种形状：**动作在 PostsScreen 里落地**。
     *
     * 楼层号一起传：卡片上写的 `#N` 和复制出来的 `#N` 只能是同一个表达式算的，
     * 让接收方自己再扫一遍 `comments` 就多了一份实现。
     */
    onCopyFloor: (PostComment, Int) -> Unit,
    /**
     * 举报某一楼：帖子、楼层、楼层号、理由 → 由 `PostsScreen` 拼成文本写进剪贴板并 toast。
     *
     * 和 [onCopyFloor] 完全同一个理由不在这里直接摸 platform：这一路下来是 private 的纯 UI。
     * 楼层号（`#N`）同样由这一层传上去 —— 菜单抬头、举报文本、卡片标签用的是同一个
     * `index + 2`，接收方不该再算一次。
     *
     * 「提交」这一步只是**复制**：后端没有举报 RPC，面板上那行小字也这么写（见
     * [FloorReportSheet]）。
     */
    onReportFloor: (CommunityPost, PostComment, Int, String) -> Unit,
    onOpenRelated: (CommunityPost) -> Unit,
    /** 目录面板开着没有（[PostTocSheet]）：入口在顶栏「更多」里，开关状态由 `PostsScreen` 持有。 */
    tocOpen: Boolean = false,
    onDismissToc: () -> Unit = {},
    /** 帖内搜索面板开着没有（[PostSearchSheet]）；理由同 [tocOpen]。 */
    searchOpen: Boolean = false,
    onDismissSearch: () -> Unit = {},
    /** 「跳到第几楼」对话框开着没有（[PostJumpDialog]）；理由同 [tocOpen]。 */
    jumpOpen: Boolean = false,
    onDismissJump: () -> Unit = {},
    /**
     * 下拉刷新：**接的就是 `PostsScreen` 里 `reloadKey++` 那条重载路径**。
     *
     * 为什么是回调而不是在这里自己拉：这一层是纯 UI，手里没有 `PostStore`（拉取、缓存换血、
     * 未读/收藏的重算全在 `PostsScreen` 那条 `LaunchedEffect(store, openId, reloadKey)` 里）。
     * 在这里再写一条"拉一次详情"的实现，等于把 `stableMerge` / `markRead` / `resumeUnread`
     * 那套逻辑抄第二遍 —— 抄漏一处就是"下拉之后楼层号变了、未读点不灭"这种只在某一条路上出现的 bug。
     *
     * 默认空实现：编辑器预览、别的宿主里单独摆这个 pane 时不会因为没传它而编译不过，
     * 也不会出现"拽下去没反应"以外的东西（默认**不画**刷新指示器，见下面 `isRefreshing`）。
     */
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    // 下拉刷新控件自己的状态（阈值、回弹动画）。与列表状态分开：`listState` 还管着跳楼、
    // 目录/搜索的"现在第几层"、「回到最新」浮钮，掺进刷新状态会让那几处跟着指示器一起重组。
    val pullState = rememberPullToRefreshState()
    // 回复面板开着没有。放在这一层是为了让**动作行左侧那颗「N 条回复」胶囊**也能打开它
    // （fluxdo `post_action_bar.dart:270` 的 `leftButton` 就是那颗回复胶囊）：
    // 两处各自 remember 一份，就会出现"点了胶囊、底下的面板没反应"。
    var composerOpen by remember { mutableStateOf(false) }
    // 换到另一条帖子时把面板收掉：`remember` 会跟着这个 composable 活到下一次换帖，
    // 不清的话从 A 帖的面板"穿"到 B 帖上，人会以为自己刚才在 B 里写了什么。
    LaunchedEffect(post?.id) { composerOpen = false }
    // 收进一个局部 val：下面几个 lambda 里要读它的字段，写一处最稳。
    val shown = post

    // ------------------------------------------------------------------ 跳到某一层
    //
    // 楼层号的**绝对 item 索引**只在这里算一次（`floorIndexOf`），不硬编码、也不用
    // `layoutInfo.visibleItemsInfo`（那只查得到屏上的那几项）。
    //
    // LazyColumn 的 item 顺序是固定的：0 = `thread-header`、1 = `op`、2 = `thread-head`、
    // 3.. = 每一条回复（空列表时 3 = `thread-empty`），再往后是 `related-head` / 每一条相关帖子
    // / `thread-foot`。**没有独立的日期分隔 item**：分隔线并进楼层自己的 item 里
    // （见下面 `items(floorRows)` 里的注释），所以"第 i 条回复"的索引恒为 `FLOORS_FIRST + i`，
    // 与日期分隔无关。以后往楼层前面插 item，只改 [FLOORS_FIRST] 这一个数就够。
    //
    // 用 id 而不是用 index 找目标：列表会被 `stableMerge` / 刷新换血，引用指示条上写着
    // "#N"，而 N 是**那一刻**的位置；真正稳定的键只有楼层 id。
    //
    // 返回 `FLOORS_FIRST + i`（i 可能为 -1，表示那一楼不在列表里 —— 调用点先判 `>= 0`）。
    // 不用 `Map<Long, Int>`：列表每次换血都要重建一张表，而这里只是 O(n) 整数比较。
    fun floorIndexOf(commentId: Long): Int =
        FLOORS_FIRST + comments.indexOfFirst { it.id == commentId }

    // 楼层 id → 楼层号（`#N`）：引用指示条上的 N 与"跳到 N"用**同一个**公式。
    // 主楼是 #1、第 i 条回复是 #(i+2)；找不到（被删了）时是 -1，指示条就只写名字。
    // 这里每次调用都扫一遍 `comments`（200 层也就是 200 次整数比较，可忽略），
    // 但**不要**把它升级成 `remember` 包住的闭包：那会让它在列表换血后拿着旧表算号。
    val floorLabelOf: (Long) -> Int = { id ->
        comments.indexOfFirst { it.id == id }.let { if (it < 0) -1 else it + 2 }
    }

    /**
     * 每一层楼底下的「直接回复」：`replyToId` → 那几条。
     *
     * 与参考实现同一个模型（fluxdo 的 `post.replies`）：一条回复既在主列表里占一层楼，
     * 又会作为"父楼下面的一串"重复出现一次。**不把子树从主列表里摘掉** —— 摘掉之后
     * 楼层号会断号、`#N` 跳转会跳空，而且按时间正序读完整个帖子的路子就没了。
     *
     * 键只收"列表里真的存在的那一层"：`replyToId` 指向一条已经被删 / 还没拉到的楼层时，
     * 那条回复在主列表里照旧是一层楼，只是不挂在谁底下（不会凭空长出一个空锚点）。
     *
     * `remember(comments)`：列表换血才重建。O(n)。
     */
    val repliesOf: Map<Long, List<PostComment>> = remember(comments) {
        if (comments.isEmpty()) {
            emptyMap()
        } else {
            val ids = comments.mapTo(HashSet(comments.size)) { it.id }
            comments
                .filter { it.replyToId > 0L && it.replyToId in ids }
                .groupBy { it.replyToId }
        }
    }

    /**
     * 楼层 id → 楼层：**「回复历史」上溯用的索引**。
     *
     * 为什么单独一张表：父链是"从这一楼的 `replyToId` 往上一层一层找"，每找一次就是一次
     * `comments.indexOfFirst`。200 层楼里若每层都各自上溯 3 次，就是 200 × 3 × 200 = 12 万次
     * 整数比较 —— 每重组一次付一遍（O(n²)）。有了这张表，每一步都是 O(1)。
     *
     * `remember(comments)`：**列表换血必须重建**，否则拿着上一次刷新的 `PostComment` 对象画卡
     * （名字、正文、楼层号都会是旧的）。链表里的键只认 id，与对象无关，所以换血之后
     * "展开着的是哪一层"（`expandedReplyHistoryOf`）仍然对得上。
     *
     * 只用这一份，别再在楼层里 `firstOrNull`：同一个"找父楼"的动作写两处实现，早晚两处算错
     * 不一样的东西。
     */
    val commentsById: Map<Long, PostComment> = remember(comments) {
        if (comments.isEmpty()) emptyMap() else comments.associateBy { it.id }
    }

    /**
     * 现在**原地展开着**回复历史（父链）的是哪一层楼（0 = 都没展开）。
     *
     * 单值，照 [expandedRepliesFloor] 的做法：一屏里同时摊开两条父链，正文会被挤成碎片，
     * 而且"父链 → 正文 → 动作行"这套读序会乱。展开新的就自动收起旧的。
     */
    var expandedReplyHistoryOf by remember(post?.id) { mutableStateOf(0L) }

    /**
     * 父链卡里已经展开全文的那些卡（键 = [replyHistoryCardKey]，即「楼层 id + 层序」）。
     *
     * 为什么是 `Set` 而不是单值：一张卡展开只把**那张卡**放回原高，同一条链上最多 3 张，
     * 各开各的彼此不挤 —— 与 `expandedBodyFloors` 同一个理由。
     *
     * 键里带 id，所以换一层楼展开另一条链、或者列表换血，都不会串到别的卡上；
     * 不清空也无害（一层楼的链最多 3 张卡，键的数量上界就是 层数 × 3）。
     */
    var unfoldedReplyCards by remember(post?.id) { mutableStateOf(emptySet<String>()) }

    /**
     * 当前展开着的那一层的父链（空 = 没展开 / 父楼已不在列表里）。
     *
     * 只算**一次**（`remember(commentsById, expandedReplyHistoryOf)`），不是每层楼各算一次：
     * 200 层楼里只有一处能展开，算 200 遍纯属浪费。链的起点就是"展开着的那个 id"对应的楼层。
     */
    val replyHistoryChain: List<PostComment> = remember(commentsById, expandedReplyHistoryOf) {
        val start = commentsById[expandedReplyHistoryOf]
        if (start == null) emptyList() else replyHistoryOf(start, commentsById)
    }

    /**
     * 现在展开的是哪一层楼（0 = 都没展开）。
     *
     * 单值而不是 `Set`：一屏里同时摊开两串嵌套回复，正文会被挤成碎片，而且滚动定位会跳来跳去。
     * 参考实现是每层一个布尔量，能同时开多个；这里收成一个，展开新的就自动收起旧的 ——
     * 信息量上等价（点一下就能换），视觉上稳。
     */
    var expandedRepliesFloor by remember(post?.id) { mutableStateOf(0L) }

    /**
     * 主楼（`#1`）的长正文展开没有：**单独一个布尔位、键是帖子 id**。
     *
     * 为什么不与下面那个按楼层 id 记的 `Set` 合用一个：主楼不在 `comments` 里，它的 id 是
     * `post.id`，两张表的 id 完全可能撞上 —— 撞上就等于"展开主楼把某一层楼也展开了"。
     * 参考实现那边每层楼一个布尔量（`segmented_long_post.dart` 里各段自己管自己的状态），
     * 搬到这边就是"一个 id 一个位"。
     */
    var opBodyExpanded by remember(post?.id) { mutableStateOf(false) }

    /**
     * 长正文**展开着**的那些楼层（按楼层 id）。照 [expandedRepliesFloor] 的做法放在这一层
     * （`PostFloorCard` 是无状态的，它自己记不住任何东西）。
     *
     * 为什么是 `Set` 而不是像嵌套回复那样的单值：嵌套回复一展开就把这一层楼撑长，同时开两串
     * 会互相挤；"展开全文"只是把一层楼放回它原本的高度，多开几层各长各的，不打架。
     *
     * 刷新列表后不会错位：存的是 id，`stableMerge` 换血之后同一个 id 还是同一条回复；
     * 被删掉的楼层由下面那个 `LaunchedEffect` 从 Set 里剔掉。
     */
    var expandedBodyFloors by remember(post?.id) { mutableStateOf(emptySet<Long>()) }

    // 被删掉的楼层别在 Set 里留残影：`comments` 一换血就把已经不在列表里的 id 剔掉。
    // 只在 Set 非空时才扫（默认零开销），扫的也只是 `comments` 那一遍整数比较。
    LaunchedEffect(comments) {
        if (expandedBodyFloors.isNotEmpty()) {
            val alive = comments.mapTo(HashSet(comments.size)) { it.id }
            if (!expandedBodyFloors.all { it in alive }) {
                expandedBodyFloors = expandedBodyFloors.filterTo(HashSet(expandedBodyFloors.size)) { it in alive }
            }
        }
    }

    /**
     * 举报面板正对着哪一层（null = 面板关着）。
     *
     * 状态在这一层、面板也画在这一层：卡片的 footer 是 `LazyColumn` 的一个 item，滚出屏就被
     * 回收 —— 把开关放在那里，人往下一滑面板就跟着没了。与 [PostTocSheet] 同一个放法。
     */
    var reportTarget by remember(post?.id) { mutableStateOf<FloorReportTarget?>(null) }

    /**
     * 刚跳过去的那一层（0 = 没有）：左边竖条亮 [HIGHLIGHT_HOLD_MS] 之后清掉。
     *
     * 它是**状态**而不是一次性的动画：同一条指示条连点两次也要能重新亮起来，
     * 所以走"清 0 → 再置 id"这条路（见下面的 `LaunchedEffect` 与跳转 lambda）。
     */
    var highlightedFloorId by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    // ------------------------------------------------------------------ 只看此人（楼层过滤）
    //
    // 过滤**只影响渲染**：`comments` 一个字都不动，`floorRows` 只是"按当前条件挑出来的那些
    // 「全量索引 + 楼层」"。
    //
    // 为什么**不**用"被过滤的楼层渲染成 0 高度空 Box、好让 item 索引继续与全量一一对应"
    // 那条路（看起来最省事，实际最贵）：
    //  1. `verticalArrangement = spacedBy(4.dp)` 是按 **item 个数** 给的，0 高度的 item 也照样
    //     吃一份 4dp：连着藏 40 层就凭空多出 160dp 可滚动的空白 —— 不漏内容，但整段是空的；
    //  2. 要让那 160dp 消失，就得在过滤态把 `spacedBy` 换成 0、再把间距塞进每个可见楼层内部，
    //     于是"层间距"变成随开关取两个值的一份知识，而它本来是 LazyColumn 的一行参数；
    //  3. 索引之所以要对得上，只是因为**跳转**要算 `FLOORS_FIRST + i`；那就只在跳转这一处
    //     处理（见 [jumpToFloorId]：目标正被藏起来时先撤过滤、下一帧再滚），而不是让 200 层楼
    //     全部参与组合、只为维持一个没人读的索引。
    //
    // 反过来，过滤态下**楼层号**（`#${index + 2}`）与**日期分隔**的判断仍然用全量索引：
    // `floorRows` 里带的就是全量索引，所以"#N 与不过滤时一致""跨天分隔与不过滤时一致"两条
    // 一个都没丢（参考 fluxdo `topic_post_list.dart:234-235`：那边把 `postNumber` 与列表里的
    // `postIndex` 分成两张表，楼层号从来不等于"列表里的第几行"）。
    var authorFilter by remember(post?.id) { mutableStateOf("") }
    // 撤过滤之后要落回的位置（过滤前那一刻的 firstVisible + offset），由下面的 LaunchedEffect 消费。
    //
    // 键跟着帖子（`post?.id`）：换帖时上面那个 `authorFilter` 会被重置成空，若这里还留着上一条
    // 帖子的位置，那个 `LaunchedEffect` 就会在新帖上"撤过滤"一次、把人滚到旧帖的索引上。
    var pendingRestore by remember(post?.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    // 过滤态下跳到了被藏起来的楼层：先撤过滤，下一帧再按全量索引滚过去。键同上。
    var pendingJumpId by remember(post?.id) { mutableStateOf(0L) }

    /** 楼层作者的身份键：优先身份码（改名后仍认得出），没有身份码（匿名 / 老数据）时退回署名。 */
    fun authorKeyOf(comment: PostComment): String = comment.authorId.ifBlank { comment.author }

    /**
     * 渲染用的楼层序列：`(全量索引, 楼层)`。
     *
     * 不过滤时就是 `comments.withIndex()`，所以"第 i 条回复 → `FLOORS_FIRST + i`"这个前提
     * 在默认状态下与上一版**完全一样**；过滤时只把不匹配的挑掉，`index` 仍然是全量的那个。
     */
    val floorRows: List<IndexedValue<PostComment>> = remember(comments, authorFilter) {
        if (authorFilter.isBlank()) {
            comments.withIndex().toList()
        } else {
            comments.withIndex().filter { authorKeyOf(it.value) == authorFilter }
        }
    }

    /**
     * 跳到某一层楼：滚到它自己的 item，并让它左边那根竖条亮起来 —— 与"跳到被回复的楼层"
     * 同一套行为（[HIGHLIGHT_HOLD_MS] 之后由 `LaunchedEffect` 收掉）。
     *
     * 索引有两套，别混：
     *  - `floorIndexOf` 给的是**全量**序列里的位置（`FLOORS_FIRST + i`，见它上面的注释）；
     *  - 过滤态下真正能滚的是**渲染**序列里的位置（它是 `floorRows` 里的第几行）。
     *
     * 目标被过滤掉时渲染序列里没有它：这时**先撤过滤**，把 id 交给 [pendingJumpId]，
     * 由下面的 `LaunchedEffect` 在全量 item 回来之后的下一帧再滚。为什么不在这里
     * `authorFilter = ""` 之后立刻 `animateScrollToItem(全量索引)`：同一帧里 LazyColumn 的
     * item 序列还是过滤后的那几十行，那个索引会落在完全不相干的一层上 —— 不报错，
     * 只是"点了跳到别处"，最难查的那种。
     */
    fun jumpToFloorId(id: Long) {
        // 走 `floorIndexOf` 而不是在这里再写一遍 `FLOORS_FIRST + indexOfFirst`：
        // "日期分隔并进楼层 item"这个前提只该有**一处**实现。抄成两份的话，
        // 哪天有人把分隔改回独立 item，改了这处漏了那处，跳转就会悄悄偏一层 ——
        // 而且没有编译错误，只有"跳少一格"的体验 bug。
        val absolute = floorIndexOf(id)
        if (absolute < FLOORS_FIRST) return   // 不在 `comments` 里（被删了 / 还没拉到）
        // 先清 0 再置 id：连着点同一个目标时，`highlightedFloorId` 的值会从 0 → id
        // 变一次，下面那个计时 `LaunchedEffect` 重新起一轮，竖条重新亮起来，而不是"没反应"。
        highlightedFloorId = 0L
        highlightedFloorId = id
        val rendered = if (authorFilter.isBlank()) {
            absolute
        } else {
            FLOORS_FIRST + floorRows.indexOfFirst { it.index == absolute - FLOORS_FIRST }
        }
        if (rendered >= FLOORS_FIRST) {
            // `animateScrollToItem` 是挂起函数：在 `launch` 里滚，不挡当前帧。
            scope.launch { listState.animateScrollToItem(rendered) }
        } else {
            // 被藏起来了：撤过滤 + 记账，落位交给 LaunchedEffect。
            // `pendingRestore` 一并作废，否则"撤过滤"那一轮会先把人滚回过滤前的位置、
            // 再被这里的目标覆盖成两次滚动。
            pendingRestore = null
            authorFilter = ""
            pendingJumpId = id
        }
    }

    /**
     * 「只看此人」：记下当前滚动位置，换成过滤序列，并滚到这个人的第一层楼。
     *
     * 记住位置是为了「看全部」能原样落回去：过滤前后 item 数量差得远，
     * 只靠 LazyColumn 自己按 key 保位，落点会跑到"现在这一屏里还剩的那一层"上。
     */
    fun applyAuthorFilter(key: String) {
        if (key.isBlank() || key == authorFilter) return
        // 先记位置再换序列：换完之后 `firstVisibleItemIndex` 已经是过滤序列里的序号了。
        // 已经在过滤态时**不覆盖**：这张记事要留的是"回到全量序列之后该落在哪"，
        // 记成过滤序列里的序号会在"看全部"那一刻偏到别的楼层去。
        if (authorFilter.isBlank()) {
            pendingRestore = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
        authorFilter = key
        // 这个人的第一层楼在过滤序列里固定是 `FLOORS_FIRST`（header / op / thread-head 三格之后）：
        // 过滤完先给人看见他写的那一楼，而不是停在原地（原地那层可能已经被藏掉了）。
        //
        // 用 `scrollToItem` 而不是 `animateScrollToItem`：序列刚被换掉，原来的像素位置在新序列里
        // 没有对应物，动画会先滚一段"已经不存在的内容"再落下 —— 直接落位更老实。
        scope.launch { listState.scrollToItem(FLOORS_FIRST) }
    }

    /** 「看全部」：撤掉过滤让全量序列回来，落位交给下面的 `LaunchedEffect`。 */
    fun clearAuthorFilter() {
        if (authorFilter.isBlank()) return
        authorFilter = ""
    }

    // 过滤开关与"跨过滤跳转"的**落位**：都在序列换好之后才滚。
    // 键里带 `authorFilter` 就是为了等那一帧 —— 改过滤的同一帧里 LazyColumn 还是旧序列。
    LaunchedEffect(authorFilter, pendingRestore, pendingJumpId) {
        // 还在过滤态：要落的目标（过滤前的位置 / 被藏起来的楼层）现在都不存在，等撤过滤。
        if (authorFilter.isNotBlank()) return@LaunchedEffect
        val restore = pendingRestore
        if (restore != null) {
            // `scrollToItem` 而不是 `animateScrollToItem`：这是"回到原处分"，动画会让人
            // 以为自己按错了；而且过滤前后可能差上百格，动画要跑很久。
            listState.scrollToItem(restore.first, restore.second)
            // **滚完再清这张记事**：这两个状态都是本 effect 的键，先清会让键变化、把正在跑的
            // 这个协程直接取消掉（`animateScrollToItem` 那种慢动画会被砍在半路）。
            pendingRestore = null
            return@LaunchedEffect
        }
        val id = pendingJumpId
        if (id != 0L) {
            val absolute = floorIndexOf(id)
            if (absolute >= FLOORS_FIRST) listState.animateScrollToItem(absolute)
            pendingJumpId = 0L
        }
    }

    // ------------------------------------------------------------------ 回到上次读到的位置
    //
    // 这一段与「续读条」（[ThreadResumeBar]）的分工，顺序写死在这里：
    //  1. 续读条讲的是"有新的回帖"（`resumeUnread` / `resumeMark`），恢复讲的是"这一屏上次停在哪"；
    //  2. 续读条**不自动滚** —— 它只是 `thread-head` 顶部的一条入口，点了才走 `jumpToFloorId`
    //     （见它的 KDoc）。所以两者不会互相顶走；把恢复写在它下面（也在上面那条"过滤落位"
    //     effect 之后）只是为了让"精确位置"先落地，续读条照样画、不去抢。
    //     哪天有人给续读条加了"自动滚到下一层"，必须让它排在恢复**之后**、并且只在这条帖子
    //     没有位置记录时才滚；否则"关掉再进来"会先恢复、再被续读条顶到别处去。
    //  3. 换帖不靠"清"而是靠 `remember(post?.id)`：`positionRestored` 以帖子 id 为键，换一条
    //     就重新来一次；盘上的记录**关帖不清**（下次进来还要用，见 [PostPositionStore] 的 KDoc）。

    /** 这条帖子这一趟试过恢复了没有。`remember(post?.id)`：换帖重来，同一条刷新不重来。 */
    var positionRestored by remember(post?.id) { mutableStateOf(false) }

    /**
     * 这一列现在**最大合法**的 item 下标（全量序列）。
     *
     * 组成：`FLOORS_FIRST`(3) + 楼层（没有回复时是那一条 `thread-empty`）+ 相关帖子块
     * （`related-head` 一条 + 每条相关帖子，`related` 为空时整块不存在）+ 结尾那条 `thread-foot`。
     * 与下面 `LazyColumn { … }` 里的 `item {…}` / `items(…)` 逐项对应 —— 以后往那儿加一格，
     * 这里也得跟着改。
     *
     * 不用 `listState.layoutInfo.totalItemsCount`：那个数要等列表**量过一次**才有值，而这里正好
     * 在第一次组合时就要判断（还没量过）。也不用 `floorRows`：恢复发生在首次加载成功那一刻，
     * 那时 `authorFilter` 还是空的（它是 `remember(post?.id)`），序列里全是楼层。
     */
    val lastThreadItemIndex = FLOORS_FIRST +
        (if (comments.isEmpty()) 1 else comments.size) +
        (if (related.isNotEmpty()) 1 + related.size else 0)

    LaunchedEffect(post?.id) {
        val open = post ?: return@LaunchedEffect
        if (positionRestored) return@LaunchedEffect
        // 一次成功加载只尝试一次：有记录就滚，没记录 / 越界都当"没有记录"，不再重试。
        // 重试没有意义 —— `post` 与 `comments` 是同一次请求一起回来的；之后再变是"刷新"，
        // 那时人已经在读了，再滚一次等于把正在读的位置抢走。
        val saved = positions?.positionOf(open.id)
        if (saved != null) {
            val (item, offset) = saved
            // 越界就当没有记录：帖子可能变短了（楼层被删）、相关帖子可能不够三条、记录也可能是
            // 上一次 200 层那一版的。`scrollToItem` 越界不会崩，但会停在列表末尾 —— 那看起来
            // 就是"进来直接跳到最底下"，比不恢复更糟。
            if (item >= 0 && item <= lastThreadItemIndex) {
                listState.scrollToItem(
                    item.coerceIn(0, lastThreadItemIndex),
                    offset.coerceAtLeast(0),
                )
            }
        } else if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            // 没有记录（或者记录越界）：回到顶部。
            //
            // 这一句不是"顺手加的优化"，它是本功能的正确性前提：`listState` 是
            // `rememberLazyListState()`，从 A 帖点进相关帖子 B 时同一个 `PostThreadPane`
            // 没有离开组合，`listState` 连同 A 的**索引**一起留在手上（LazyColumn 在加载态
            // 那一帧退出组合又回来，状态对象不重建）。不归位的话，B 会从上一条帖子的半中腰
            // 开始，而下面那条保存 effect 会把这个索引当成"B 读到这儿"记下去 ——
            // 也就是把一次换帖变成一条假记录。
            listState.scrollToItem(0)
        }
        positionRestored = true
    }

    // 位置记录：**停手 [POSITION_SAVE_DEBOUNCE_MS] 才写一次盘**，不是每帧写。
    //
    // 本仓没有 `Flow.debounce` 的先例，已有的节流写法是 `ui/component/ScrollMemory.kt:71-81`
    // （`snapshotFlow` + `conflate` + `delay` + 一次写入）。这里照它的形状，但把"醒来就写"
    // 收紧成"醒来再比一次值、没变才写"—— 详情页的 item 高度差得远（长正文、展开的回复历史），
    // 靠 `conflate` 只能保证"写的是最新的那一刻"，保证不了"那一刻已经停手了"：
    //  1. `snapshotFlow` 把两个状态合流，值变了才发（它自带去重，不必再 `distinctUntilChanged`）；
    //  2. 收到之后 `delay(400)`：这 400ms 里人还在滑的话值已经变了，醒来那次比较不通过，这一趟
    //     什么都不写；而流"收集慢就合并到最新"，所以再快也只会在停下之后写一次；
    //  3. 醒来后**再读一次** `firstVisibleItemIndex` / `ScrollOffset`，与当初收到的那一对逐项比 ——
    //     相等才说明"停下来了"，这时才落盘。
    //
    // 键里带 `post?.id`：换帖就重开一条流，别把上一帖的位置写进这一帖。`positionRestored` 也
    // 一起判：恢复还没落地时不记，否则可能把恢复前的 (0,0) 当成"读到这里"写回去（(0,0) 本身
    // 由 `PostPositionStore.remember` 挡掉，见那边的"顶部不记"）。
    LaunchedEffect(listState, post?.id) {
        val open = post ?: return@LaunchedEffect
        val store = positions ?: return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (item, offset) ->
                delay(POSITION_SAVE_DEBOUNCE_MS)
                if (!positionRestored) return@collect
                if (listState.firstVisibleItemIndex != item ||
                    listState.firstVisibleItemScrollOffset != offset
                ) {
                    return@collect
                }
                // 过滤态下 `firstVisibleItemIndex` 是**过滤序列**里的序号（`floorRows` 的下标），
                // 而恢复时序列一定是全量的（`authorFilter` 是 `remember(post?.id)`，进来就是空）。
                // 两种坐标混着存，下次进来就会落到不相干的一层上。所以过滤态干脆不记：
                // 上一次那条全量坐标（如果有）留着不动，比存一个错的好。
                if (authorFilter.isNotBlank()) return@collect
                store.remember(open.id, item, offset)
            }
    }

    // 点「回复 #N」要跳的层：null = 不挂 clickable（不做"点了没反应"的东西）。
    // 目标楼不在 `comments` 里（被删了 / 还没拉到）时也返回 null —— 同上。
    //
    // **不再 `remember`**（上一版是 `remember(comments, listState)`）：这个 lambda 现在还要读
    // 过滤状态与 `floorRows`（渲染序列），记住它就会在换了过滤之后拿着上一份 `floorRows` 算位置。
    // 代价只是每次重组新建一个 lambda（楼层里那几处 `replyIndicator` 跟着重建），
    // 比"跳错一层"便宜得多。
    val onJumpToFloor: ((Long) -> Unit)? = if (comments.isEmpty()) {
        null
    } else {
        { target: Long -> jumpToFloorId(target) }
    }
    // 竖条到点收掉：`LaunchedEffect` 以 id 为键，跳转 lambda 会先把 id 清成 0 再置回来，
// 所以连点同一个目标也能重新起一轮计时（不会因为"键没变"而不重跑）。
    LaunchedEffect(highlightedFloorId) {
        if (highlightedFloorId != 0L) {
            delay(HIGHLIGHT_HOLD_MS)
            highlightedFloorId = 0L
        }
    }

    /**
     * 正文里 `#N` 楼层引用的跳转：把**楼层号**换成楼层 id 再复用 [jumpToFloorId]。
     *
     * 换算是 `floorLabelOf` 的反向：`#1` 是主楼（不在 `comments` 里，滚到 item 1）、
     * `#2` 起是 `comments[0…]`；越界或算不出来时什么都不做（不给"点了没反应"的假手势，
     * 因为正文里的数字本来就可能是随手写的）。
     *
     * 为什么放在这一层：换算要 `comments` 与 `listState` 两者，只有这里同时拿得到。
     */
    val onFloorRef: (Int) -> Unit = { floor ->
        when {
            floor <= 0 -> Unit
            floor == 1 -> scope.launch { listState.animateScrollToItem(1) }
            else -> comments.getOrNull(floor - 2)?.let { target -> jumpToFloorId(target.id) }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (shown == null) {
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = error ?: "这条帖子没拉到。",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.error,
                    )
                }
            }
        } else {
            // 楼层列表外面这一层容器，两件事一起干：
            //  1. 给右侧那条**阅读进度轨**（[FloorProgressTrack]）和右下角那颗「回到最新」浮钮
            //     一个坐标系：两者都是 `align` 盖在列表上的，不参与列表的测量，也就不会让楼层的
            //     高度/宽度跟着它们变（塞进 LazyColumn 的 contentPadding 里会让每一层楼都窄 2dp，
            //     而且贴不到"整条列表"的那个高度上）。
            //  2. **下拉刷新**：`PullToRefreshBox` 自己就是一个 `Box`（content 的 receiver 是
            //     `BoxScope`），所以它**顶替**了原来那个裸 `Box`，里面的子项、`align`、间距
            //     一个都没动 —— 只是多了一个包在外面、管纵向拖拽与顶部指示器的容器。
            //     只包住列表这一块：`ReplyBar` 是这个 `Column` 的另一个子项（在它下面），
            //     不能被一起拽下来，也不该挡住指示器。
            //
            // `isRefreshing = loading`：重载走的就是 `PostsScreen` 里 `reloadKey++` 那条路，
            // 那条 `LaunchedEffect(store, openId, reloadKey)` 在整趟拉取里把 `threadLoading`
            // 置真、结束置假 —— 正好就是指示器要的两个时刻。不为下拉单独记一个布尔量：
            // 那样"回帖后刷新"和"下拉刷新"各有一份状态，早晚有一份忘了收（指示器一直转）。
            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = onRefresh,
                state = pullState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 16.dp),
                    // 间距 4 而不是 8：楼层本身已经是一条通栏（自己带 14/14 的上下留白 + 底部 0.5dp 分隔线），
                    // 8dp 的层间缝会把那条线推到两层中间悬着。4dp 刚好留一口气，又让分隔线读作"下边界"。
                    //
                    // **这个 4 不要再改**：楼层块上下是 14，14 + 4 + 14 = 32，正好等于参考
                    // `post_segment_frame.dart:63-67` 的 16 + 16 = 32 的层间节奏 ——
                    // 移动 4 就必须连 14 一起重算，否则层间空白会偏。
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 顶上那块「标题 + 标签 + 元信息 + 参与者」：discourse 的话题页第一屏就是它，
                    // 楼层从它下面开始数。
                    item(key = "thread-header") {
                        ThreadHeader(
                            post = shown,
                            comments = comments,
                        )
                    }

                    item(key = "op") {
                        PostFloorCard(
                            floorLabel = "#1",
                            author = shown.author,
                            authorSeed = shown.authorId.ifEmpty { shown.author },
                            stamp = relativeStamp(shown.createdAt),
                            body = shown.body,
                            badges = buildList {
                                if (shown.pinned) add("置顶")
                                if (shown.locked) add("已锁")
                                if (authorId.isNotBlank() && shown.authorId == authorId) add("我")
                            },
                            // 主楼永远不引用别的楼：引用只在同一帖子的楼层之间成立。
                            replyIndicator = null,
                            // 主楼的 id 就是帖子的 id：跳转锚点用的是"这一楼"的 id，
                            // 而主楼不在 `comments` 里，所以单独用帖子 id 参与高亮判定。
                            highlighted = highlightedFloorId != 0L && highlightedFloorId == shown.id,
                            // 主楼的长正文同样先折：状态是**帖子 id 那一个位**（见 `opBodyExpanded`），
                            // 不复用按楼层 id 记的那个 Set。
                            bodyExpanded = opBodyExpanded,
                            onToggleBody = { opBodyExpanded = !opBodyExpanded },
                            // 主楼正文里的 `#N` 也可点：引用回复常被人写在主楼里（编辑过的主楼也会带）。
                            onFloorRef = onFloorRef,
                            footer = {
                                // 顺序照 fluxdo `post_action_bar.dart:263-281`：
                                //   Row { [leftButton = 回复胶囊] + 12, Expanded(Wrap(end){ 其它动作 }) }
                                // 也就是**回复胶囊在左、其余动作靠右**。上一版是"赞在最左"，与源码相反。
                                //
                                // fluxdo 那颗左胶囊点开的是"内联回复"；本仓没有内联回复，
                                // 所以它的 `onClick` 直接打开底部的回复面板 —— 位置照抄，行为落到
                                // 本仓真实有的功能上，不做成点了没反应的装饰。
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FloorPill(
                                        label = "${shown.comments} 条回复",
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Rounded.ChatBubble,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = scheme.onSurfaceVariant,
                                            )
                                        },
                                        active = composerOpen,
                                        // 锁帖 / 认不出身份时不能开面板：胶囊跟着置灰，
                                        // 而不是点下去弹一个"回不了"。
                                        enabled = !shown.locked && authorId.isNotBlank(),
                                        onClick = { composerOpen = true },
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    FloorPill(
                                        label = if (busyKey == "like:${shown.id}") "…"
                                        else "${shown.likes}",
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Favorite,
                                                contentDescription = null,
                                                // 心形 **20dp**（`post_action_bar.dart:538-541` 的
                                                // `Icon(Symbols.favorite_rounded, size: 20)`）：
                                                // 参考里回复胶囊的聊天图标是 15、赞这颗是 20 —— 心形本来
                                                // 就比气泡饱满，同一个尺寸会显得小一圈。上一版两颗都写 15。
                                                modifier = Modifier.size(20.dp),
                                                tint = if (shown.mineLike) {
                                                    scheme.primary
                                                } else {
                                                    scheme.onSurfaceVariant
                                                },
                                            )
                                        },
                                        active = shown.mineLike,
                                        enabled = busyKey == null,
                                        onClick = onToggleLike,
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    // 复制整帖（标题、作者、正文、标签）：「这帖转给我看看」最省事的做法。
                                    FloorCircleButton(
                                        label = "复制帖子",
                                        tint = scheme.onSurfaceVariant,
                                        onClick = onCopy,
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                                    }
                                    // 收藏放在这里而不是顶栏：顶栏已经有删除，再挤一个按钮标题就要被压扁了。
                                    // 已收藏时星实心 + 主色，与 fluxdo 的选中态同一个表达式。
                                    FloorCircleButton(
                                        label = if (saved) "取消收藏" else "收藏",
                                        tint = if (saved) scheme.primary else scheme.onSurfaceVariant,
                                        onClick = onToggleSave,
                                    ) {
                                        Icon(Icons.Rounded.Star, null, Modifier.size(18.dp))
                                    }
                                }
                            },
                        )
                    }

                    item(key = "thread-head") {
                        // 「上次读到这儿」的续读条：**塞进这一个 item 的顶部**，不另起一个
                        // `item(key = "thread-resume")`。
                        //
                        // 理由（也写在 [ThreadResumeBar] 的 KDoc 里）：楼层索引是
                        // `FLOORS_FIRST + i`（= 3 + i）算出来的，而这个 3 被好几处同时读到
                        // （`floorIndexOf` / `jumpToFloorId` / `applyAuthorFilter` 的"滚到
                        // FLOORS_FIRST" / 目录面板的 `first < FLOORS_FIRST` 判断）。往楼层前面
                        // 插一个新 item 就得把它改成 4 —— 数字好改，漏掉任何一处都变成
                        // "跳少一格"这种**没有编译错误**的 bug。塞进已有 item 则一个索引都不用动：
                        // LazyColumn 的 item 序列与改动前逐项相同，跳楼 / 日期分隔 / 只看此人 /
                        // 目录 / 帖内搜索全部照旧。这一格的语义本来就是"楼层列表的开头"，
                        // 续读条摆在这里也不违和。
                        //
                        // `resumeUnread` 为 false 时**一个组合项都不建**（判断在 item 内部、
                        // 在 LazyColumn 的作用域之外），所以没有增量时这一格与改动前完全一样：
                        // 不占高度，也不留空 item。
                        if (resumeUnread) {
                            // 先落成非空/非委托的局部 val：`resumeMark` 是 `by remember` 的委托属性，
                            // 直接在里面写 `if (resumeMark != null) shown.comments - resumeMark`
                            // 拿不到 smart cast（委托属性一律不能），会编译不过。
                            val mark = resumeMark
                            // 目标：有 `mark` 时是"上次看到的那一条之下第一条"（精确续读），
                            // 没 `mark` 时退回第一层楼。两种都走 `jumpToFloorId`，与
                            // 「回复 #N」指示条、目录面板、帖内搜索同一个落位函数。
                            val target = mark
                                ?.let { seen -> comments.getOrNull(seen) }
                                ?: comments.firstOrNull()
                            ThreadResumeBar(
                                mark = mark,
                                unseen = if (mark != null) {
                                    (shown.comments - mark).coerceAtLeast(0)
                                } else {
                                    // 没有精确数字时至少说清"这条帖子有多少条回复"这个语境，
                                    // 而不是编一个"后面 N 条"。
                                    shown.comments
                                },
                                onClick = if (target != null) {
                                    { jumpToFloorId(target.id) }
                                } else {
                                    // 一层楼都没有（`comments` 空）时算不出目标：
                                    // 胶囊置灰、整条不挂点击，不做"点了没反应"的东西。
                                    null
                                },
                            )
                        }
                        PostSectionHeader(label = "回复", count = shown.comments)
                        // 「只看此人」开着时的说明条：200 层的帖子里只剩三层，不写清楚"为什么"，
                        // 看的人只会以为楼层丢了。出口（「看全部」）就放在同一行 ——
                        // 不让人再回去翻某一层楼的菜单找出口。
                        //
                        // 名字与层数直接读 `floorRows`：过滤态下它全是同一个人、且就是**留下**的
                        // 那些楼，正好是这里要说的两个数（不必再扫一遍全量 `comments`）。
                        if (authorFilter.isNotBlank()) {
                            FloorFilterBanner(
                                name = floorRows.firstOrNull()?.value?.author?.ifBlank { "匿名" }
                                    ?: "匿名",
                                count = floorRows.size,
                                onClear = { clearAuthorFilter() },
                            )
                        }
                    }

                    if (comments.isEmpty()) {
                        item(key = "thread-empty") {
                            // 这一层 `item` 覆盖 `FLOORS_FIRST(3)` 那个位置，回复从 4 开始数；
                            // 没有回复时 `onJumpToFloor` 本来就是 null，索引算不到这里来。
                            PostNote(text = "还没有人回。")
                        }
                    }

                    // 楼层号就是时间序里的位置：后端按时间正序给，这里 index + 1 直接对得上。
                    //
                    // 遍历 `floorRows`（`(全量索引, 楼层)`）而不是直接 `itemsIndexed(comments)`：
                    // 「只看此人」开着时它只含匹配的那些楼，但每一行的 `index` 仍然是**全量位置** ——
                    // 楼层号 `#${index + 2}`、下面那段跨天判断（`comments[index - 1]`）、
                    // 以及 `floorIndexOf` 算出的跳转索引因此全部照旧成立，过滤没有改变任何一处的含义。
                    // 不过滤时 `floorRows` 就是 `comments.withIndex()`，与上一版逐项等价。
                    items(floorRows, key = { "c-${it.value.id}" }) { row ->
                        val index = row.index
                        val comment = row.value
                        // 跨天的第一层（以及紧跟在主楼下面那层）前面加一条日期分隔线：
                        // 后面的回复和上一条不是同一天时，才值得说一声"从这天起"。
                        // 主楼自己的日期在 header 里，所以 `index == 0` 时始终和主楼那一天比。
                        //
                        // **不拿 `comments[index - 1]` 当"前一层"就完事**：这个列表会被
                        // `stableMerge` 就地合并、也可能因为刷新换血，索引相邻不等于时间相邻。
                        // 先比较时间戳，只有"上一条真的更早"时才把它的日期当作前一层；
                        // 否则退回主楼那一天 —— 那样至少不会插在错误的位置上。
                        val previousDay = if (index == 0) {
                            BeijingTime.dayKey(shown.createdAt)
                        } else {
                            val prev = comments[index - 1]
                            if (prev.createdAt <= comment.createdAt) {
                                BeijingTime.dayKey(prev.createdAt)
                            } else {
                                BeijingTime.dayKey(shown.createdAt)
                            }
                        }
                        val thisDay = BeijingTime.dayKey(comment.createdAt)
                        // 两边都解析不出日期（后端给了怪形状）时不画，免得插一条空标签。
                        //
                        // 分隔线并进**这一层自己的**那个「楼层 item」里，不再单独 `item(key = "date-…")`：
                        //  1. `items`/`itemsIndexed` 的 itemContent 的 receiver 是 `LazyItemScope`，它带 scope
                        //     marker，外面那层 `LazyListScope` 被挡住，`item()` 在里面根本调不到；
                        //     就算调到了也是"组成一个条目的时候再往列表里插条目"，运行期一样错。
                        //  2. 更重要的：跳转要用绝对索引。只要日期分隔是**独立 item**，"第 i 条回复"
                        //     的索引就会随跨天分隔的条数漂移（`FLOORS_FIRST + i` 立刻是错的）。
                        //     把分隔线和楼层放进同一个 item，楼层索引恒为 `FLOORS_FIRST + i`，
                        //     与有没有分隔无关 —— 这就是下面 `floorIndexOf` 敢用加法算的前提。
                        // 同一个 item 内先后 emit 的两块内容，LazyColumn 照样按垂直方向上下摆，
                        // 线上位置与原来那条独立条目一致。
                        if (thisDay.isNotEmpty() && thisDay != previousDay) {
                            FloorDateSeparator(iso = comment.createdAt)
                        }
                        // 被回复的那一楼的作者名与头像种子：优先取**当前列表里**那一行
                        // （名字可能已经改过），列表里没有时才退回后端抄在 replyToAuthor 上的那份。
                        //
                        // 走 `commentsById`（`remember(comments)` 建的那张表）而不是
                        // `comments.firstOrNull`：父链上溯本来就要这张表，指示条这一处再各自
                        // 扫一遍列表，等于 200 层楼各付一次 O(n)。表随列表换血重建，所以刷新后
                        // 拿到的是**新的**那一条；`replyToAuthor` 仍然是"当时那一份"的兜底。
                        val quotedRow = if (comment.replyToId > 0L) commentsById[comment.replyToId] else null
                        val quotedAuthor = quotedRow?.author?.takeIf { it.isNotBlank() }
                            ?: comment.replyToAuthor
                        // 头像种子与别处同一个口径（`authorId.ifEmpty { author }`）：图床地址是按
                        // 身份码推出来的，名字只作兜底里的首字。
                        val quotedSeed = quotedRow?.let { it.authorId.ifEmpty { it.author } }
                            .orEmpty()
                            .ifEmpty { comment.authorId.ifEmpty { comment.author } }
                        PostFloorCard(
                            floorLabel = "#${index + 2}",
                            author = comment.author,
                            authorSeed = comment.authorId.ifEmpty { comment.author },
                            stamp = relativeStamp(comment.createdAt),
                            body = comment.body,
                            badges = if (authorId.isNotBlank() && comment.authorId == authorId) {
                                listOf("我")
                            } else {
                                emptyList()
                            },
                            // 引用指示条：这一楼在回哪一楼。`replyToId <= 0` 时整个 lambda 是 null，
                            // `PostFloorCard` 里那一段就一个组合项都不建（与没有功能时完全一样）。
                            //
                            // 点击 = 滚到被回复的那一层。**跳转回调本身可能为 null**
                            // （列表里还没有那一楼），那时指示条照样画（信息有用），但不挂 clickable
                            // —— 不做"点了没反应"的东西。
                            //
                            // 长按 = 原地展开这一楼的「回复历史」（父链，见 [ReplyHistoryList]）。
                            // 为什么挂在指示条上：那一条写的正是"在回谁"，父链就是"这条引用链的
                            // 前文"，两者是同一件事的入口与内容（参考实现把指示条本身当成展开开关，
                            // `post_header.dart:364-375` 的 `onTap` 就是 `onToggleReplyHistory`）。
                            //
                            // 这里**不套 `derivedStateOf`**：`floorLabelOf(target)` 每次重组扫一遍
                            // `comments` 也就几十次整数比较，比多一层快照订阅便宜。
                            replyIndicator = if (comment.replyToId > 0L) {
                                val target = comment.replyToId
                                val jump = onJumpToFloor
                                val floorOfTarget = floorLabelOf(target)
                                // 父楼在不在列表里：决定"能不能长按展开"。找不到（被删 / 还没拉到）
                                // 时**不给长按**，指示条退回只显示名字的老样子 —— 不伪造一层空卡。
                                val chainable = commentsById[target] != null
                                val expandedHere = expandedReplyHistoryOf == comment.id
                                // 先落成非空 lambda 再判 `chainable`：多行 lambda 直接写在
                                // `if (…) { … } else { null }` 的分支里能编译，但读的人要数两层花括号，
                                // 这里拆开更好认（也避开"孤立 lambda 是不是块"的那点歧义）。
                                val toggleHistory: () -> Unit = {
                                    expandedReplyHistoryOf = if (expandedHere) 0L else comment.id
                                }
                                val onToggleHistory: (() -> Unit)? = if (chainable) toggleHistory else null
                                // 显式声明成 composable lambda 再交给参数：直接写 `{ … }` 紧跟在
                                // `val floorOfTarget = …` 后面，Kotlin 会把它当成上一行的尾随 lambda
                                // 参数（`floorLabelOf(target) { … }`），于是既报"类型不匹配"，又让
                                // 块里的 `floorOfTarget` 落到作用域外。
                                val block: @Composable () -> Unit = {
                                    FloorReplyIndicator(
                                        floor = floorOfTarget,
                                        author = quotedAuthor,
                                        authorSeed = quotedSeed,
                                        // 跳转的**实质**条件：目标楼真的在列表里（`floorOfTarget`
                                        // 能算出号来，即找到过它）。找不到时 `jump` 也可能非空，
                                        // 所以两道都过才挂手势。
                                        onClick = if (floorOfTarget >= 2 && jump != null) {
                                            { jump(target) }
                                        } else {
                                            null
                                        },
                                        expanded = expandedHere,
                                        onLongClick = onToggleHistory,
                                    )
                                }
                                block
                            } else {
                                null
                            },
                            // 「回复历史」（父链）：指示条下方、正文上方的那一段小卡。
                            // 只有"这一层正是展开着的那一层"、且链条真有内容时才建；其余情况传 null，
                            // 这一层楼的组合项与改动前**一个都不多**。
                            //
                            // 链条与状态都在 [PostThreadPane]：`PostFloorCard` 是无状态的，手里也没有
                            // 全量 `comments`，让它自己上溯只能靠 `indexOfFirst` 扫列表（200 层楼 = O(n²)）。
                            replyHistory = if (expandedReplyHistoryOf == comment.id &&
                                replyHistoryChain.isNotEmpty()
                            ) {
                                val block: @Composable () -> Unit = {
                                    ReplyHistoryList(
                                        chain = replyHistoryChain,
                                        floorLabelOf = floorLabelOf,
                                        unfoldedKeys = unfoldedReplyCards,
                                        // 键是「楼层 id + 层序」（见 replyHistoryCardKey），
                                        // 所以收放着的是**哪一张卡**永远不含糊。
                                        onToggleUnfold = { key ->
                                            unfoldedReplyCards = if (key in unfoldedReplyCards) {
                                                unfoldedReplyCards - key
                                            } else {
                                                unfoldedReplyCards + key
                                            }
                                        },
                                        // 点父卡 = 跳到那一层 + **收起整条链**：跳走了链还摊着，
                                        // 人会以为那是目标楼下面的内容（与指示条单击同一个落点，
                                        // 只是收尾多做一步）。
                                        onJump = { id ->
                                            expandedReplyHistoryOf = 0L
                                            jumpToFloorId(id)
                                        },
                                        // 父卡**正文里**的 `#N`：与楼层正文同一个换算、同一个落位函数
                                        // （[onFloorRef]）。不传的话父卡里的 `#N` 就是死文字 ——
                                        // 而这层楼之所以展开父链，恰恰是因为有人在正文里引用了楼号。
                                        onFloorRef = onFloorRef,
                                    )
                                }
                                block
                            } else {
                                null
                            },
                            highlighted = highlightedFloorId != 0L && highlightedFloorId == comment.id,
                            // 折叠态按**楼层 id** 记（刷新换血后 id 还是同一条回复，不会错位）。
                            bodyExpanded = comment.id in expandedBodyFloors,
                            onToggleBody = {
                                expandedBodyFloors = if (comment.id in expandedBodyFloors) {
                                    expandedBodyFloors - comment.id
                                } else {
                                    expandedBodyFloors + comment.id
                                }
                            },
                            onFloorRef = onFloorRef,
                            footer = {
                                // 动作行左起：**「N 条回复」胶囊**（有子回复时才有）+ 「引用回复」圆钮，
                                // 右端一颗「更多」（`MoreVert`）。
                                // 参考里"查看直接回复"是动作行上单独一颗按钮（`post_action_bar.dart`
                                // 的 replies 按钮 + `post_replies_list.dart` 的收起按钮），
                                // 本仓用同一颗胶囊管开关：展开时文案变「收起」并吃 `active` 底色。
                                //
                                // 右端那颗「更多」的位置照 `post_action_bar.dart:263-281` 的左右分组：
                                // `Row { [左组] + Expanded { 右组靠 end } }` —— 所以这里用
                                // `Spacer(weight(1f))` 把右侧动作顶到行尾（主楼的动作行也是这么写的）。
                                val kids = repliesOf[comment.id].orEmpty()
                                val expanded = expandedRepliesFloor == comment.id
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (kids.isNotEmpty()) {
                                        FloorPill(
                                            label = if (expanded) "收起" else "${kids.size} 条回复",
                                            icon = {
                                                Icon(
                                                    imageVector = if (expanded) {
                                                        Icons.Rounded.KeyboardArrowUp
                                                    } else {
                                                        Icons.Rounded.ChatBubble
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp),
                                                    tint = if (expanded) {
                                                        scheme.primary
                                                    } else {
                                                        scheme.onSurfaceVariant
                                                    },
                                                )
                                            },
                                            active = expanded,
                                            enabled = true,
                                            onClick = {
                                                expandedRepliesFloor = if (expanded) 0L else comment.id
                                            },
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    if (!shown.locked) {
                                        // 「引用回复」= 一颗 36dp 正圆（照 `post_action_bar.dart:386-397`
                                        // 的 `reply_rounded` 圆形按钮）。本仓的引用是"把原文塞进回复框"
                                        // 而不是内联展开，所以这一颗是**动作**，与源码同一个位置、同一个形状。
                                        FloorCircleButton(
                                            label = "引用回复",
                                            tint = scheme.onSurfaceVariant,
                                            onClick = { onQuote(comment) },
                                        ) {
                                            Icon(Icons.Rounded.FormatQuote, null, Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    // 「更多」：复制本楼 / 跳到本楼顶部 / 只看此人（看全部）/ 举报。
                                    // 前三种动作是**只读**的，所以锁帖也照给 —— 与顶栏那颗
                                    // `DetailMoreMenu` 同一个口径（锁帖只挡编辑与删除）；
                                    // 举报同理：锁帖恰恰是有事要上报的时候。
                                    FloorMoreMenu(
                                        floorLabel = "#${index + 2}",
                                        author = comment.author,
                                        filteredBy = authorFilter,
                                        onCopy = { onCopyFloor(comment, index + 2) },
                                        onJumpToSelf = { jumpToFloorId(comment.id) },
                                        onOnlyThisAuthor = { applyAuthorFilter(authorKeyOf(comment)) },
                                        onShowAll = { clearAuthorFilter() },
                                        // 举报面板的开关在这一层（卡片 item 会被回收），
                                        // 楼层号用同一个 `index + 2`。
                                        onReport = { reportTarget = FloorReportTarget(comment, index + 2) },
                                    )
                                }
                            },
                            // 展开的那一串直接回复。收起时传 null —— 这一层楼的高度与没有子回复时一模一样。
                            replies = if (expandedRepliesFloor == comment.id &&
                                repliesOf[comment.id].orEmpty().isNotEmpty()
                            ) {
                                {
                                    NestedRepliesList(
                                        replies = repliesOf[comment.id].orEmpty(),
                                        floorLabelOf = floorLabelOf,
                                        onJump = onJumpToFloor,
                                        onCollapse = { expandedRepliesFloor = 0L },
                                        // 嵌套楼正文里的 `#N`：同一条换算、同一个落位函数。
                                        // 点整行仍然跳它自己那一层，点号跳号指到的那一层。
                                        onFloorRef = onFloorRef,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }

                    if (related.isNotEmpty()) {
                        item(key = "related-head") {
                            PostSectionHeader(label = "相关帖子", count = related.size)
                        }
                        items(related, key = { "rel-${it.id}" }) { rel ->
                            RelatedPostRow(post = rel, onClick = { onOpenRelated(rel) })
                        }
                    }

                    item(key = "thread-foot") {
                        PostNote(
                            text = "回帖按时间正序，最多显示 200 层；正文支持 Markdown（标题、列表、引用、代码块）。",
                        )
                    }
                }

                // 阅读进度轨（右侧那根 3dp 细条）：画在列表**之上**、贴右边缘，
                // 不挡正文 —— 它只有 3dp，外面那圈 16dp 的点击热区是透明的。
                // `align` 是 BoxScope 的扩展，所以由调用点给（见 [FloorProgressTrack] 的注释）。
                FloorProgressTrack(
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )

                // 右下角「回到最新」浮钮（显隐算式与坐标见 [ScrollToLatestButton]）：
                // 距右 16dp、距底 16dp —— 右边那 16dp 正好是进度轨的热区，两者**边界相接、零重叠**；
                // 底下那 16dp 是相对这个 `Box`（= 列表区）的，而 `ReplyBar` 在 `Box` **外面**，
                // 所以浮钮永远浮在回复栏上方，不会被它盖住。
                ScrollToLatestButton(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                )
            }

            // 锁帖不另起一条横带：fluxdo 是把"话题已关闭"写进回复胶囊的文案里
            // （`topic_bottom_bar.dart:376-379` 的 `TopicReplyEntryState.closed` →「话题已关闭」+ 锁图标）。
            // 那条横带每屏白占 40dp，而胶囊已经在同一位置说明同一件事。
            ReplyBar(
                reply = reply,
                busy = busyKey == "reply:${shown.id}",
                locked = shown.locked,
                // 认不出身份的人写不了回帖，胶囊文字换成"登录后参与讨论"而不是点了才报错。
                enabled = authorId.isNotBlank(),
                // 胶囊上那句「回复 #N · 名字」：楼层号 = 它在 `comments` 里的位置 + 2（主楼是 #1）。
                // 按 **id** 找而不是记住 index：列表会被刷新换血，index 在两次刷新之间可能已经变了，
                // 存 id 就永远对得上正确的那一层。找不到（比如这一楼刚被别人删掉）就退回只写名字，
                // 名字还有（后端在发帖那一刻抄过一份），指示条不至于变成一句空话。
                replyTo = replyTo?.let { quote ->
                    val floor = comments.indexOfFirst { it.id == quote.id } + 2
                    if (floor >= 2) {
                        "${floor}" to quote.author.ifBlank { "匿名" }
                    } else {
                        "" to quote.author.ifBlank { "匿名" }
                    }
                },
                // 面板开关在 `PostThreadPane` 那一层：动作行左侧那颗回复胶囊也要开它。
                open = composerOpen,
                onOpenChange = { composerOpen = it },
                onChange = onReplyChange,
                onSend = onSend,
            )

            // ---- 目录面板（[PostTocSheet]）----
            //
            // 放在这一层是因为只有这里拿得到 `listState`：面板要把"你正在看第几层"高亮出来。
            // 开关状态在 `PostsScreen`（顶栏「更多」里的入口在那儿），这边只负责画。
            if (tocOpen) {
                // 当前视口所在楼层：`firstVisibleItemIndex` 是**渲染序列**里的位置 ——
                // 过滤态下它同样是 `floorRows` 的下标（见 `floorRows` 的注释），两种状态通用。
                // 前三格（thread-header / op / thread-head）都算"在主楼"，交给面板画在「主楼」行上。
                val currentFloorId by remember(floorRows) {
                    derivedStateOf {
                        val first = listState.firstVisibleItemIndex
                        if (first < FLOORS_FIRST) {
                            0L
                        } else {
                            floorRows.getOrNull(first - FLOORS_FIRST)?.value?.id ?: 0L
                        }
                    }
                }
                PostTocSheet(
                    comments = comments,
                    floorLabelOf = floorLabelOf,
                    currentFloorId = currentFloorId,
                    onJump = { id ->
                        if (id == 0L) {
                            // 主楼：item 1 是 `op`（item 0 是标题区，滚到那里等于把标题顶上去）。
                            scope.launch { listState.animateScrollToItem(1) }
                        } else {
                            // 与「回复 #N」那条指示条同一个落位逻辑：过滤态下会先撤过滤再滚。
                            jumpToFloorId(id)
                        }
                    },
                    onDismiss = onDismissToc,
                )
            }

            // ---- 帖内搜索面板（[PostSearchSheet]）----
            //
            // 与目录同一个位置、同一套理由：`comments` 与 `floorLabelOf` 只有这一层有。
            // 跳转复用 [jumpToFloorId]（过滤态下会先撤过滤再滚），与「回复 #N」指示条、
            // 目录面板**完全同一条路径** —— 三个入口跳到同一层时的行为必须一模一样，
            // 否则"目录里跳得对、搜索里跳偏一层"这种 bug 最难查。
            if (searchOpen) {
                PostSearchSheet(
                    topicTitle = shown.title,
                    comments = comments,
                    floorLabelOf = floorLabelOf,
                    onJump = { id -> jumpToFloorId(id) },
                    onDismiss = onDismissSearch,
                )
            }

            // ---- 「跳到第几楼」对话框（[PostJumpDialog]）----
            //
            // 输入 1 基楼层号。`onJump` 直接复用 [onFloorRef]：正文里点 `#N` 与在这里输入 N
            // 走的是**同一个换算**（`#1` → 主楼、`#2+` → `comments[n-2]`、越界什么都不做），
            // 少一份"楼层号 → 楼层 id"的实现就少一处会跳偏的地方。
            if (jumpOpen) {
                PostJumpDialog(
                    // 层数含主楼：`comments` 只有回帖，主楼是 #1。
                    maxFloor = comments.size + 1,
                    onJump = onFloorRef,
                    onDismiss = onDismissJump,
                )
            }

            // ---- 举报面板（[FloorReportSheet]）----
            //
            // 与目录面板同一种放法：开关在楼层菜单里（菜单只是把 `reportTarget` 置上），
            // 面板本体画在这一层 —— 楼层 item 会被 LazyColumn 回收，面板不能跟着走。
            // 提交之后**先关面板再交出去**：剪贴板与 toast 都在 `PostsScreen`，那边的动作
            // 一旦慢了半拍，人看到的就是"点了提交、面板还盖在上面"。
            val target = reportTarget
            if (target != null) {
                FloorReportSheet(
                    floorLabel = "#${target.floor}",
                    author = target.comment.author,
                    onDismiss = { reportTarget = null },
                    onSubmit = { reason ->
                        reportTarget = null
                        onReportFloor(shown, target.comment, target.floor, reason)
                    },
                )
            }
        }
        }
    }

/**
 * 楼层卡片外面那一圈边距：详情页的正文块统一用它，header 也用它，这样左边缘是一条直线。
 *
 * [PostFloorCard] 自己不再加左右边距：它被放在 LazyColumn 里，边距由内容区的 contentPadding
 * 统一给，避免"卡片套边距"两处相加。
 */
// `FlowRow` 仍是实验性布局 API（compose 1.12 也一样），必须逐个函数 opt-in ——
// 本仓其它用到它的地方（`PlanScopePicker` / `TweaksScreen` / `AppTile`）都是这么写的。
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreadHeader(post: CommunityPost, comments: List<PostComment>) {
    val scheme = MaterialTheme.colorScheme
    // 参与者：从回帖里按作者去重，取最近出现的 5 个（discourse 那几个小圆就是干这个的）。
    val participants = remember(comments) {
        comments.asReversed().map { it.authorId.ifEmpty { it.author } }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)
            .map { person ->
                val source = comments.lastOrNull { it.authorId.ifEmpty { it.author } == person }
                person to (source?.author.orEmpty())
            }
    }
    // 阅读时长：按正文字数估，中文一分钟约 400 字。没有正文时按回复数估。
    val readMinutes = remember(post.body, comments.size) {
        val chars = post.body.length + comments.sumOf { it.body.length }
        (chars / 400).coerceIn(1, 60)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 16 而不是 12：楼层现在是一条 padding 16 的通栏，header 跟着对齐 —— 详情页里
            // 所有正文块的左边缘必须落在同一条直线上（`PostSectionHeader` 本来也是 16）。
            .padding(horizontal = 16.dp)
            // 顶 20：参考实现的 header 是 `EdgeInsets.fromLTRB(16, 20, 16, 16)`
            // （`topic_detail_header.dart:84`），标题上方那口气就是 20 —— 原来写 14，标题贴着顶栏。
            .padding(top = 20.dp)
            // **底 12**：上一版底边是 0（分隔线紧贴列底，下面的楼层立刻顶上来）。
            // 参考 `:84` 的 bottom 是 16，这里取 12 的理由是两边这条线**画在哪儿**不一样：
            // 参考的线是容器自己的 `Border(bottom)`（`:90-94`，画在最下沿，线下就是 0），
            // 它的 16 在线**上方**；本仓这条 `HorizontalDivider` 是列里的**最后一个子项**，
            // 线下方还要接 `LazyColumn` 的 `spacedBy(4.dp)` —— 12 + 4 = 16，
            // 正好等于参考"标题区下沿到下一块 16"；取满 16 会变成 20 的缝（16 + 4），松一档。
            // 线上方那 6 不在这里：见分隔线自己的 `padding(top = 6.dp)`（只留一处，别重复）。
            .padding(bottom = 12.dp),
    ) {
        // 分档间距（上一版是 `verticalArrangement = spacedBy(10.dp)` 一把梭，四段间隔全是 10）：
        //   标题 →[12]→ 分类/标签 →[16]→ 元信息 →[6]→ 统计 →[6]→ 分隔线
        // 参考依据：`:148` 的 `SizedBox(height: showTitle ? 16 : 8)`（标题与下一块之间）、
        // `:212` 的 `const SizedBox(height: 16)`（分类/标签之后）、
        // 统计行上方的 6 在 `topic_statistics.dart:200-201` 的 `Padding(only(top: 6))`。
        // 押成一个数就把"标题-分类 12"与"分类-元信息 16"的差别抹平了，还把元信息与统计行之间的
        // 6 撑成 10（一行 12dp 的小字，四周却各留 10，读着像独立段落）。
        //
        // 用列内**显式 `Spacer`** 而不是改小 `spacedBy`：分类行与统计行都是条件块，
        // `spacedBy` 是"有几块给几条"，某一块缺席时剩下的间距会整体上移一格（标题直接贴着元信息
        // 就会是 16 而不是 12）；显式 Spacer 各归各位，也方便逐处单独回滚。

        // 标题 + 锁图标：锁住的帖子在标题**前面**画一颗锁（`topic_detail_header.dart:109-120`
            // 的 20dp / `outline` 色），而不是另起一条"这条帖已经锁了"的横带 —— 那条横带
            // 每屏白占一行，而锁本来就是标题的属性。
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (post.locked) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(20.dp), tint = scheme.outline)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = post.title.ifBlank { "（无标题）" },
                    // **必须显式给字号**：本仓的 `headlineSmall` 被 FlTypography 映射到
                    // `FlText.headline`（17sp/22，那是 FlClash 二级页标题的档），
                    // 直接写 `typography.headlineSmall` 点进帖子标题反倒比列表卡片还小一截。
                    //
                    // 数值照 `topic_detail_header.dart:103-107`：`titleLarge` + `w600` +
                    // `height: 1.4` + `letterSpacing: 0.2`。M3 的 `titleLarge` 是 **22sp**，
                    // 1.4 倍行高 = 30.8 ≈ 31sp。上一版写 24sp / Bold(700)，比参考大两号、重一档。
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        lineHeight = 31.sp,
                        letterSpacing = 0.2.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

        // 分类 + 标签：`Wrap(spacing: 6, runSpacing: 8)`（`topic_detail_header.dart:163-166`）。
        // 上一版是横滚一行，标签一多就有一半藏在屏幕外；参考实现是**换行**，多出来的落到第二行。
        //
        // 标题 →(12)→ 分类：参考 `:148` 的 16 是"标题 → `TopicStateSummary`"（状态摘要，
        // 本仓没有对应物）；本仓这一格改成分类/标签行，它比参考的摘要行轻（胶囊、单行），
        // 所以取 12，与下面"分类 → 元信息"的 16 拉开一档 —— 读起来"标签是挂在标题上的"。
        if (post.category.isNotBlank() || post.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (post.category.isNotBlank()) {
                    CategoryPill(category = post.category, colorHex = post.categoryColor)
                }
                post.tags.forEach { tag -> TagPill(tag = tag) }
            }
            // 分类 →(16)→ 元信息：参考 `:212` 的 `const SizedBox(height: 16)`。
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            // 没有分类/标签时不叠两个空档：标题直接接元信息，取 12（标题到任何下一块的最小那一档），
            // 与有分类时"12 + 分类行 + 16"的节奏同一个起点。
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 元信息：回复 / 赞 / 时间。每项内部照 `_buildMetadataItem`
        // （`topic_detail_header.dart:346-381`）：图标 14dp、与数字之间 4dp、
        // 数字 α0.8 + w500、后面的标签 α0.5 —— 一眼能分出"数"和"名"。
        //
        // 外层照参考 `:262-302` 的 `Row { Expanded(Wrap(spacing: 12, runSpacing: 4)) }`：
        // 三项之间只有 **12dp 间距**，不再放中点。上一版用 "·" 把三项串起来（中点前后各 6dp），
        // 而参考里每一项都自带图标，分隔符是多余的墨；窄屏放不下时这里会按 `runSpacing: 4`
        // 让第三项落到第二行，而不是把时间挤成"3 天…"。
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ThreadMeta(
                glyph = Icons.Rounded.ChatBubble,
                value = "${post.comments}",
                label = "回复",
                tint = scheme.onSurfaceVariant,
            )
            ThreadMeta(
                glyph = Icons.Rounded.Favorite,
                value = "${post.likes}",
                label = "赞",
                tint = if (post.mineLike) scheme.primary else scheme.onSurfaceVariant,
            )
            val stamp = relativeStamp(post.createdAt)
            if (stamp.isNotBlank()) {
                ThreadMeta(
                    glyph = Icons.Rounded.Schedule,
                    value = stamp,
                    tint = scheme.onSurfaceVariant,
                )
            }
        }

        // 统计行：参与者头像 + 「N 参与者」+ 「X 分钟阅读」。
        // 照 fluxdo 的 `topic_statistics.dart`：一个 `Wrap(spacing: 12)`，每项是"数值 + 浅色标签"
        // 两段文字，阅读时长是**下一项**而不是被推到行右端。
        if (participants.isNotEmpty() || readMinutes > READ_MINUTES_MIN) {
            // Compose 里对应的不是 Dart 的 `Wrap` 而是 `FlowRow`（换行 + 水平间距）。
            //
            // `padding(top = 6.dp)` 就是分档表里"元信息 →(6)→ 统计"那一档，与参考
            // `topic_statistics.dart:200-201` 的 `Padding(only(top: 6))` 同值。
            // **这一档不换成 Spacer**：留白挂在统计行自己身上，统计行缺席时它就跟着一起消失，
            // 元信息与分隔线之间只剩下面那 6 —— 不会出现"统计行没有了、它的上方还空着 6"。
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (participants.isNotEmpty()) {
                    // 头像叠 + 「N 参与者」是**一项**，不能被 FlowRow 拆到两行去。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ParticipantStack(participants = participants)
                        Text(
                            text = "${participants.size.coerceAtLeast(1)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = metaTint(scheme.onSurfaceVariant, 0.8f),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "参与者",
                            style = MaterialTheme.typography.bodySmall,
                            color = metaTint(scheme.onSurfaceVariant, 0.5f),
                        )
                    }
                }
                // 只有读起来"值得"的时候才报时长：fluxdo 是 minutes > 3，短帖报一个
                // "1 分钟阅读"没有信息量，只是多占一格。
                if (readMinutes > READ_MINUTES_MIN) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = metaTint(scheme.onSurfaceVariant, 0.8f),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$readMinutes",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = metaTint(scheme.onSurfaceVariant, 0.8f),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "分钟阅读",
                            style = MaterialTheme.typography.bodySmall,
                            color = metaTint(scheme.onSurfaceVariant, 0.5f),
                        )
                    }
                }
            }
        }

        // 0.5dp，和楼层底部那条同一个口径（`post_segment_frame.dart:47` 的 `width: 0.5`）：
        // 默认 1dp 在这里比楼层线粗一倍，两条线摆在同一页里一眼能看出不一样。
        //
        // 颜色 `outlineVariant α0.3`：数值照 `topic_detail_header.dart:90-94`（header 自己的
        // `Border(bottom: outlineVariant α0.3, width: 0.5)`）。上一版写 0.5，比参考重一档 ——
        // header 这条线是"标题区的下沿"，不是楼层之间那条分隔，它该更淡。
        //
        // `padding(top = 6.dp)`：分档表里"统计 →(6)→ 分隔线"（统计行缺席时它就是
        // "元信息 →(6)→ 分隔线"，两种情形都是 6，所以不必按条件给两个值）。
        //
        // **这一档只有这一处**：上面已经去掉了 `Column(verticalArrangement = spacedBy(10.dp))`，
        // 所以这里**不要**再补一颗 `Spacer(6.dp)` —— 两个 6 叠起来会变成 12，line 上下各多一截。
        // 上一版写"本列已经有 10dp 的 spacedBy，这里补 6 正好是 16"：那句随 `spacedBy` 一起作废了，
        // 现在线上方就是纯 6，与参考 `topic_statistics.dart:201` 的取法一致
        // （参考 header 的 bottom 16 在线**下方**，那一段由本列 `padding(bottom = 12.dp)` 承担）。
        HorizontalDivider(
            thickness = 0.5.dp,
            color = scheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * 元信息里的一个小图标 + 一个数 + 可选的标签，照 `topic_detail_header.dart:346-381`
 * 的 `_buildMetadataItem`：
 *
 * - 图标 **14dp**，颜色是 `onSurfaceVariant α0.7`
 * - 图标与数之间 **4dp**
 * - 数 `α0.8` + **w500**（比标签亮、比正文稳）；标签 `α0.5`
 *
 * 为什么把"数"和"标签"分开而不是拼成一句 `"3 回复"`：拼起来只能同色同字重，
 * 一行的三个数（回复/赞/时间）读起来会糊成一条灰线，分不开"值"和"它是什么"。
 */
@Composable
private fun ThreadMeta(
    glyph: ImageVector,
    value: String,
    tint: Color,
    label: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = metaTint(tint, 0.7f),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = metaTint(tint, 0.8f),
            maxLines = 1,
        )
        if (label != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = metaTint(tint, 0.5f),
                maxLines = 1,
            )
        }
    }
}

/**
 * 分类 chips 行：**不用**。分类的唯一入口是顶栏那颗 ☰（打开 [PostCategoryDrawer]）。
 *
 * fluxdo 首页在工具栏下面还有一条 40dp 的 chips 导航行（`_navRowHeight = 40`，`_CategoryChip`），
 * 但用户明确要求这里不要它——把分类摆到搜索下面，等于同一个东西有两个入口，而且那一条会
 * 常驻占掉 40dp 的列表高度。所以这段保留成注释，谁要恢复就照着补回来：
 * 几何 = 行高 40（chip 上下各 6 留白）、左右 12、chip 间距 8、chip 内左右 14、
 * 字 13sp/行高 1.0、选中 w600；颜色 = 选中 `secondaryContainer` + `onSecondaryContainer`，
 * 未选 `surfaceContainerHigh` + `onSurface` 85%，「＋」用 `surfaceContainerHighest` 35%。
 */

/** 分类标签：一小块颜色 + 分类名，浅底圆角。颜色与列表里的色块同一个来源（后端下发 / 兜底 / 可读化）。 */
@Composable
private fun CategoryPill(category: String, colorHex: String = "") {
    val color = categoryColorOf(category = category, hex = colorHex)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = category,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

/** 标签：灰底圆角，前面一个 #。 */
@Composable
private fun TagPill(tag: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = "#$tag",
        style = MaterialTheme.typography.labelSmall,
        color = scheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/**
 * 参与者头像叠压，照 fluxdo `topic_participant_avatars.dart:53-70` 的真做法：
 *
 * ```
 * step = size - 8
 * SizedBox(width = size + (n-1) * step, height = size) {
 *     Stack { for (i) PositionedDirectional(start = i * step, width = size) { avatar } }
 * }
 * ```
 *
 * 三处容易写错的地方，都是对照源码抄出来的：
 *
 * 1. **占位宽度**：`size + (n-1) * step`。`offset` 只平移、不改测量位置，所以外层容器
 *    必须**再补一个 `size - step`**（= 重叠量 8dp）的余量，否则最后一个头像的右边缘会被
 *    父容器的 `clipToBounds` 裁掉 8dp。用 `wrapContentWidth(unbounded = true)` 就不必算这个数，
 *    但那样外层在 Row 里会退化成无界宽；这里显式给宽，算得清。
 * 2. **圆环不是实心圆**：相邻头像之间那道 1.5dp 的 `surface` 分隔是**描边**（`Border.all`），
 *    画成实心 `drawCircle` 就成了"每个头像垫一块白饼"，头像本身就看不出来了。
 * 3. **不要重复 clip**：`PostAvatar` 内部已经 `clip(CircleShape)`，外面再 clip 一次只多一层图层。
 */
@Composable
private fun ParticipantStack(participants: List<Pair<String, String>>) {
    val scheme = MaterialTheme.colorScheme
    val size = 21.dp
    val step = PARTICIPANT_STEP
    // 重叠量 = size - step = 8dp；外层要把最后这一个的完整圆包住。
    val overlap = size - step
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(size + step * (participants.size - 1).coerceAtLeast(0) + overlap)
                .height(size),
        ) {
            participants.forEachIndexed { index, (seed, name) ->
                Box(
                    modifier = Modifier
                        // x 是**正的**：第 i 个头像从 i*step 起画，后画的压在前一个的右半边上，
                        // 视觉上是一摞"向右展开"的叠压（和 fluxdo 的 start 方向一致）。
                        .offset(x = step * index)
                        .size(size)
                        // 描边（不是实心圆）：半径还要收回 0.75dp，否则线宽的一半会溢出被裁。
                        .drawBehind {
                            drawCircle(
                                color = scheme.surface,
                                radius = this.size.minDimension / 2f - 0.75.dp.toPx(),
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                        },
                ) {
                    PostAvatar(
                        name = name.ifBlank { "匿" },
                        seed = seed,
                        size = size,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
    }
}

/**
 * 详情页顶栏那颗「收藏」圆钮：未收藏 [Icons.Rounded.StarBorder]、已收藏 [Icons.Rounded.Star] + 主色。
 *
 * 为什么顶栏也要一颗：收藏原来只藏在**主楼动作行最右**那颗星里（见 `PostThreadPane` 里那个
 * `FloorCircleButton`），读一条几十层的长帖时，人要么先滚回第一楼、要么一路滑到底再滑回来才点得到。
 * 「收藏这条正在读的帖」是读到一半随时会冒出来的念头，不是读完才做的事，所以顶栏给一个随时够得着的入口。
 * fluxdo 的详情顶栏放的也正是这类"对整帖"的动作（`topic_detail_page.dart` 的 AppBar actions）。
 *
 * 为什么两处入口共用一个状态：星的两态都由 `reads?.isSaved(openId)` 算出来，点下去都走
 * `PostsScreen` 里那一条 [toggleOpenSaved]（`toggleSaved` + `readsVersion++`，列表与详情都靠
 * `readsVersion` 重算）。这里**不持有任何本地状态** —— 一旦在这里 `remember { mutableStateOf }`，
 * 就会出现"顶栏是实心星、主楼那颗还是空心"这种同一个事实两种显示。
 *
 * 触控 48dp、图标 24dp、正圆底：与旁边那颗「更多」（[DetailMoreMenu]）同一档，两颗钮并排时视觉重量一致。
 * 未收藏用 `onSurface`（与「更多」同色，不抢注意力），已收藏用 `primary` —— 与主楼那颗星的选中态同一个表达式。
 */
@Composable
private fun TopBarSaveButton(
    saved: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 无障碍名跟着状态走：读屏念的是"收藏"/"取消收藏"，而不是一个光秃秃的"按钮"。
    val label = if (saved) "取消收藏" else "收藏"
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (saved) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            // 只在这里写一次名字（不额外给 `clickable` 传 `onClickLabel`）：
            // 两个都写会让读屏把"收藏"念两遍。
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (saved) scheme.primary else scheme.onSurface,
        )
    }
}

/**
 * 详情页顶栏那颗「更多」，照 fluxdo `topic_detail_page.dart:1781-1786` + `:2002-2017`：
 *
 * - 触发器：一颗 `more_vert`（竖三点）图标按钮，触控 48dp、图标 24dp
 * - 菜单项：**图标 20dp + 12dp 间距 + 文字**（`PopupMenuItem` 形态），不是一排图标
 * - 作者本人比其他人只多两项：**「编辑话题」第一项**、**「删除」最后一项**
 *
 * 为什么编辑/删除要收进菜单而不是像上一版那样摆在顶栏上当两颗圆按钮：
 * 1. 源码就是这么放的（顶栏 actions 只有 更多）；
 * 2. 顶栏标题吃 `weight(1f)`，一颗 TextButton 最小 58dp，两颗 124dp，360dp 屏上标题
 *    只剩 148dp（八个字）就被省略 —— 收进菜单等于把这段宽度还给标题；
 * 3. 删除是破坏性动作，藏进菜单才需要多点一下。
 *
 * 锁住的帖子不给「编辑」（后端 `post_update` 也会拒），这一项直接灰掉而不是隐藏：
 * 藏起来人会以为"我明明作者却没有编辑"，灰掉才说明"是这条锁了"。
 */
@Composable
private fun DetailMoreMenu(
    open: CommunityPost,
    canManage: Boolean,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    /** 复制整帖（标题、作者、正文、标签）。 */
    onCopy: () -> Unit,
    /** 打开目录面板（[PostTocSheet]）：楼层一多，"翻回某一楼"就只剩这一条路了。 */
    onOpenToc: () -> Unit,
    /** 打开帖内搜索（[PostSearchSheet]）：在**当前这条帖子**的楼层里找关键词。 */
    onOpenSearch: () -> Unit,
    /** 打开「跳到第几楼」对话框（[PostJumpDialog]）：知道楼层号时最快的一条路。 */
    onOpenJump: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "更多选项",
                    onClick = { menuOpen = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreVert, null, Modifier.size(24.dp), tint = scheme.onSurface)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            // 第一项固定是「目录」：它是**读类**动作，作者与非作者都用得上，所以放在
            // `canManage` 那个分叉**之前**（编辑/删除只有作者有，复制帖子两边都有）。
            // 图标用 `FormatListBulleted` 而不是 `Icons.Rounded.List`：后者会把 `List`
            // 这个名字带进作用域，与 `List<PostComment>` 这种类型名同形，读代码时容易误会。
            MenuRow(
                // `AutoMirrored` 版本：`Icons.Rounded.FormatListBulleted` 在本仓的 Compose 版本里
                // 已标 `@Deprecated`（RTL 语言下图标该镜像），CI 会报 warning。
                icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                label = "目录",
                tint = scheme.onSurface,
                enabled = true,
                onClick = {
                    menuOpen = false
                    onOpenToc()
                },
            )
            // 「帖内搜索」紧跟在目录下面：两者是同一类动作（在长帖里找位置），
            // 一个按楼层列表找、一个按关键词找。
            MenuRow(
                icon = Icons.Rounded.Search,
                label = "帖内搜索",
                tint = scheme.onSurface,
                enabled = true,
                onClick = {
                    menuOpen = false
                    onOpenSearch()
                },
            )
            // 「跳到楼层」：已知楼层号时的捷径（"你看 87 楼"）。与上面两项同属"在长帖里定位"，
            // 一个按列表找、一个按关键词找、一个按号直达。
            MenuRow(
                icon = Icons.Rounded.Numbers,
                label = "跳到楼层",
                tint = scheme.onSurface,
                enabled = true,
                onClick = {
                    menuOpen = false
                    onOpenJump()
                },
            )
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            if (canManage) {
                // 第一项：编辑话题（作者本人 / 有编辑权）。
                MenuRow(
                    icon = Icons.Rounded.Edit,
                    label = "编辑话题",
                    tint = if (open.locked) scheme.onSurfaceVariant.copy(alpha = 0.38f) else scheme.onSurface,
                    enabled = !busy && !open.locked,
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                // 「复制整帖」对谁都有用（把这条转给别处看），不分作者。
                MenuRow(
                    icon = Icons.Rounded.ContentCopy,
                    label = "复制帖子",
                    tint = scheme.onSurface,
                    enabled = true,
                    onClick = {
                        menuOpen = false
                        onCopy()
                    },
                )
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                // 最后一项：删除（红色）。放在分隔线下面，和上面的常用项隔开。
                MenuRow(
                    icon = Icons.Rounded.Delete,
                    label = "删除",
                    tint = scheme.error,
                    enabled = !busy,
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            } else {
                // 不是作者：fluxdo 那边这一档只有读类动作。本仓能给的也就「复制帖子」，
                // 所以菜单只有这一项 —— 不放灰掉的编辑/删除，那会像"我应该能点"。
                MenuRow(
                    icon = Icons.Rounded.ContentCopy,
                    label = "复制帖子",
                    tint = scheme.onSurface,
                    enabled = true,
                    onClick = {
                        menuOpen = false
                        onCopy()
                    },
                )
            }
        }
    }
}

/**
 * 菜单里的一行：图标 20dp + 12dp 间距 + 文字。
 *
 * 不用 `DropdownMenuItem(text = …)` 的默认形态：那个的图标走 `leadingIcon`，尺寸和间距
 * 都由 M3 默认值决定（24dp 图标），而 fluxdo 这里是 **20dp + 12dp**。
 */
@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val ink = if (enabled) tint else tint.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 圆角 4（M3 的 `menuItemShape`）：不给的话 clickable 的涟漪是**直角矩形**，
            // 铺在圆角菜单里像一块补丁。
            .clip(RoundedCornerShape(4.dp))
            // 最小 48dp：内容本身 14+20+14 正好 48，但中文字的 fallback 行高可能比
            // `bodyMedium` 的 20sp 高一两像素，那样菜单项就会比别的鼓一格。
            .heightIn(min = 48.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = ink)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * 楼层底部的一颗**胶囊**动作：36dp 高、全圆角、`surfaceContainerHighest α0.3` 底、图标 15dp。
 *
 * 数值照 fluxdo `post_action_bar.dart`：回复胶囊 `:293-312`（高 36 / 圆角 18 /
 * `surfaceContainerHighest α0.3`）、赞胶囊 `:561-580`（同一个底色与半径）、
 * 里面那颗图标 `:323-329` 是 **15dp** + 6dp 间距、数字 `labelMedium w600`。
 *
 * 为什么不用 `Button` / `TextButton`：M3 的按钮最小高 40、自带 tonal 底与自己的形状，
 * 摆在同一行里比源码那排胶囊高一头、粗一圈，而且 `TextButton` 的文字按钮在楼层底下
 * 看起来像一条链接，不像"这一楼能做什么"。
 *
 * 选中态（已赞）换成 `primaryContainer α0.3` 底 + `primary` 前景 + **1dp `primary α0.2` 描边**，
 * 照 `:563-573`（`side: BorderSide(primary α0.2)`）。
 * 楼层 footer 里「N 条回复」这种**只读数**的胶囊把 [onClick] 传 null：这样它既不挂
 * `clickable`（不会给出"按得下去"的涟漪和无障碍按钮角色），也不再被禁用色压到 0.38 透明度——
 * 读数照旧清楚，人也不会去点它。
 */
@Composable
private fun FloorPill(
    label: String,
    icon: @Composable () -> Unit,
    active: Boolean,
    enabled: Boolean,
    /** null = 只读数，不响应点击。 */
    onClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    // 只读数时用正常色（`onSurfaceVariant`）；可点的才区分禁用 / 选中 / 常态。
    val ink = when {
        onClick == null -> scheme.onSurfaceVariant
        !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.38f)
        active -> scheme.primary
        else -> scheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (active) scheme.primaryContainer.copy(alpha = 0.3f)
                else scheme.surfaceContainerHighest.copy(alpha = 0.3f),
            )
            // 选中态那圈 **1dp `primary α0.2`** 描边照 `post_action_bar.dart:569-573`
            // （赞胶囊的 `side: BorderSide(color: primary α0.2)`）。参考在**未选中**时给的是
            // `Colors.transparent`，所以这里只在 `active` 时画：常态一个像素都不加。
            // 用 `border` 而不是 `drawBehind`：胶囊的圆角是 50%（= 高度的一半），
            // 和 `border` 的形状参数走同一套 `RoundedCornerShape(50)`，不用另算半径。
            .then(
                if (active) {
                    Modifier.border(1.dp, scheme.primary.copy(alpha = 0.2f), RoundedCornerShape(50))
                } else {
                    Modifier
                },
            )
            // 点击层压在背景之上（顺序 clip → background → clickable → padding）：
            // 反过来背景会盖住涟漪，和抽屉、全仓其它胶囊是反着的。
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = label,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * 楼层底部的一颗**圆形图标按钮**：36×36 正圆、图标 18dp、`surfaceContainerHighest α0.3` 底。
 *
 * 照 fluxdo `post_action_bar.dart:409-437` 的 `_iconCircle`（高 36 / 宽 36 / 正圆 /
 * 图标 18 / `onSurfaceVariant`）。
 *
 * 触控盒**也收到 36**（上一版是"视觉 36 / 触控 40"）：参考 `post_action_bar.dart:418-419`
 * 的 `height: 36 / width: 36` 本身就是手势区，源码外面没有那圈 4dp。
 * 这里仍保留"透明 Box 包着视觉圆"这层结构（不要 `minimumInteractiveComponentSize`
 * 给相邻两颗之间塞 4dp 空档、看着散），只是把外层从 40 收成 36：
 * 一层楼的动作行是「赞 / 回复 / 更多 / 引用」几颗并排，每颗各多 4dp 合起来就是十几 dp，
 * 200 层楼累积下来约一屏的可读高度（200 × 4dp = 800dp）；而 36 仍满足可用的触控下限
 * （M3 的 48 是推荐值，这一行挤在 16dp 内容区里、旁边还并着高 36 的胶囊，
 * 撑到 48 会把动作行顶成两行、层高反超参考）。
 */
@Composable
private fun FloorCircleButton(
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            // 触控盒 36 = 视觉圆 36（`post_action_bar.dart:418-419` 的 `height/width: 36`），
            // 上一版这里写 40，多出来的 4dp 是透明的、看不见但会把动作行撑高。见上面 KDoc。
            .size(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHighest.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            // 不吃 `content()` 的默认色：图标颜色由调用方按"选中没有"决定，
            // 所以这里禁用状态也要压一档透明度。
            Box(modifier = Modifier.size(18.dp)) {
                CompositionLocalProvider(
                    LocalContentColor.provides(
                        if (enabled) tint else tint.copy(alpha = 0.38f),
                    ),
                ) { content() }
            }
        }
    }
}

/**
 * 一层楼右端那颗「更多」（**横**三点 `MoreHoriz`）圆钮 + 落下的小菜单。
 *
 * 形状照同文件的 [DetailMoreMenu]：外层一个 `Box`，触发件复用 [FloorCircleButton]
 * （36dp 正圆 / 图标 18dp，照 fluxdo `post_action_bar.dart:409-437` 的 `_iconCircle`；
 * 图标横三点而不是竖三点，同 `:402` 的 `Symbols.more_horiz_rounded`），
 * 菜单里每行都走 [MenuRow]（图标 20dp + 12dp 间距 + 最小 48dp 高 + 圆角 4dp）。
 * 同一个文件里已经有一套菜单皮肤了，这里只复用、不另起一套。
 *
 * 三项的来源与理由（参考 fluxdo 的楼层菜单 `post_footer_section/actions/menu_actions.dart`
 * —— 那边是按 `canRecover` / `canDelete` 之类的**能力开关**逐项决定画不画（`:265-319`），
 * 以及 `post_flag_sheet.dart:127-138` 那种"先把操作对象写在标题里"的抬头）：
 *  - 「复制本楼」：楼层是帖子里最常被单独转走的一段（"你看这层说的"）；顶栏那颗「复制帖子」
 *    复制的是整帖。文本在 [copyableFloor] 里拼，剪贴板由 [PostsScreen] 的 `onCopyFloor` 落地
 *    —— 本 composable 没有 platform，也不该有（见 `PostThreadPane` 那个参数的注释）。
 *  - 「跳到本楼顶部」：长楼层（一段代码块、一张长图）滚到一半时，回到这一层的开头要往上推很久。
 *    行为与"跳到被回复的楼层"完全同一套（见 `PostThreadPane` 里的 `jumpToFloorId`）：
 *    滚到这一层自己的 item + 左边竖条亮 1.5s（[HIGHLIGHT_HOLD_MS]）。
 *  - 「只看此人」/「看全部」：200 层长帖里只追一个人说的话。过滤只改渲染、不重排 `comments`
 *    （见 `PostThreadPane` 里 `floorRows` 那段注释），所以菜单文案随过滤状态在两者之间切换。
 *  - 「举报」（红）：不是本地动作，开的是 [FloorReportSheet]。**先关菜单再开面板**（见 [onReport]
 *    的注释），理由清单与文案照 `post_flag_sheet.dart` + `FlagType.defaultTypes`。
 *
 * 抬头那一行（`#N` + 作者）**不可点**：它是"这个菜单在操作哪一层"，不是动作 ——
 * 所以不挂 `clickable`，只吃 `labelSmall` + `onSurfaceVariant`。
 */
@Composable
private fun FloorMoreMenu(
    /** 这一层的楼层号（`#N`，与卡片上画出来的是同一个表达式）。 */
    floorLabel: String,
    author: String,
    /** 当前「只看此人」的键（空 = 看全部）；决定最后一项写「只看此人」还是「看全部」。 */
    filteredBy: String,
    onCopy: () -> Unit,
    onJumpToSelf: () -> Unit,
    onOnlyThisAuthor: () -> Unit,
    onShowAll: () -> Unit,
    /**
     * 「举报」：只负责把面板打开（面板本体与开关状态在 [PostThreadPane]）。
     *
     * 这个菜单是 `DropdownMenu`，**先关菜单再开面板**（与上面每一项同一个顺序）：不关的话
     * 菜单的弹出层还挂在这颗按钮上，面板一出来就压在它上面，「面板属于哪一层楼」这件事
     * 当场糊掉；而且下拉菜单的遮罩还在吃点击。
     */
    onReport: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        FloorCircleButton(
            label = "本楼更多",
            tint = scheme.onSurfaceVariant,
            onClick = { menuOpen = true },
        ) {
            // 图标 18dp，**横三点**而不是竖三点：楼层这一颗照 `post_action_bar.dart:402`
            // 的 `_iconCircle(icon: Symbols.more_horiz_rounded)`。顶栏那颗「更多」才是竖三点
            // （`topic_detail_page.dart:1785` 的 `more_vert_rounded`）——源码里本来就是两种摆法，
            // 一个在两行高的动作行里、一个在 56dp 的标题栏里。
            Icon(Icons.Rounded.MoreHoriz, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            // 抬头：这一层是谁、是第几楼。不挂 `clickable`，只是给人对一眼。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = floorLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = author.ifBlank { "匿名" },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 长名字（"某某的测试小号二号"）会把菜单撑宽：菜单宽度由最长的一行决定，
                    // 而下面三项都是四个字，抬头不该比动作行宽出一截。
                    modifier = Modifier.widthIn(max = 150.dp),
                )
            }
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            MenuRow(
                icon = Icons.Rounded.ContentCopy,
                label = "复制本楼",
                tint = scheme.onSurface,
                enabled = true,
                onClick = {
                    menuOpen = false
                    onCopy()
                },
            )
            MenuRow(
                icon = Icons.Rounded.ArrowUpward,
                label = "跳到本楼顶部",
                tint = scheme.onSurface,
                enabled = true,
                onClick = {
                    menuOpen = false
                    onJumpToSelf()
                },
            )
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            if (filteredBy.isNotBlank()) {
                // 过滤态：这一层就是"被留下来的那一批"，出口是「看全部」。
                MenuRow(
                    icon = Icons.Rounded.Visibility,
                    label = "看全部",
                    tint = scheme.onSurface,
                    enabled = true,
                    onClick = {
                        menuOpen = false
                        onShowAll()
                    },
                )
            } else {
                MenuRow(
                    icon = Icons.Rounded.Person,
                    label = "只看此人",
                    tint = scheme.onSurface,
                    enabled = true,
                    onClick = {
                        menuOpen = false
                        onOnlyThisAuthor()
                    },
                )
            }
            // 举报：单独一条分隔线压在它上面 —— 它跟上面那几个"只读动作"不是一类，
            // 也照惯例放在菜单最后。颜色吃 `scheme.error`（红 = "这一条需要人来看"），
            // 图标 `Flag`（照 fluxdo `post_flag_sheet.dart:129` 的 `Symbols.flag_rounded`）。
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            MenuRow(
                icon = Icons.Rounded.Flag,
                label = "举报",
                tint = scheme.error,
                enabled = true,
                onClick = {
                    menuOpen = false
                    onReport()
                },
            )
        }
    }
}

/**
 * 「回复」标题下面那条**过滤说明**：现在只看谁、留下几层、怎么退出。
 *
 * 为什么需要它：楼层过滤是"列表里少了一大半"的效果，如果不留一行字，人只会觉得楼层丢了
 * （200 层里只剩三层）；而唯一出口藏在某一层楼的「更多」菜单里 —— 那正好是"被过滤掉之后
 * 最不容易想起来的地方"。所以把名字、层数和「看全部」摆在同一行。
 *
 * 尺寸与语气都跟着楼层里的小字走（`labelSmall` / `onSurfaceVariant`），不抢标题：
 * 它是一条状态说明，不是一个区块。参考 fluxdo `topic_filter_controls.dart` 的做法
 * （筛选态在列表头附近给一枚带名字的胶囊 + 一个清空入口）。
 */
@Composable
private fun FloorFilterBanner(name: String, count: Int, onClear: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 与 [PostSectionHeader] 的 16dp 内容区对齐；顶部不用留 —— header 自己带了 8dp 底距。
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = scheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            // 名字长的（"某某的测试小号二号"）要能省略，不能把「看全部」顶出行外。
            text = "只看 $name（$count 层）",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(modifier = Modifier.width(8.dp))
        // 行内的小胶囊：最小高 32（比楼层动作行那 36 矮一档，因为它是说明不是动作），
        // 热区靠 `heightIn` 兜住 —— 一行小字直接挂 clickable 的话按起来太窄。
        Box(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(50))
                .background(scheme.primaryContainer.copy(alpha = 0.3f))
                .clickable(role = Role.Button, onClickLabel = "看全部", onClick = onClear)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "看全部",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
                maxLines = 1,
            )
        }
    }
}

/**
 * 详情页楼层列表顶上那条「上次读到这儿」的续读条：
 * `[▶] 后面有新回复 ──────────────── ( 接着看 )`，40dp 高、圆角 8、`primaryContainer α0.25` 底。
 *
 * 为什么需要它：长帖关掉再进来，人只想接着看后面那几条，而现在只能自己一路往下划。
 * **本仓自有需求** —— 参考实现（fluxdo 的详情页）里没有等价物：它有列表吸顶的筛选条，
 * 但没有任何"上次读到哪儿"的入口，所以这一条的尺寸与文案都是按本仓自己的语气定的。
 *
 * ## 为什么塞进 `thread-head` 那个 item，而不新起一个 `item(key = "thread-resume")`
 *
 * 楼层索引一律是 `FLOORS_FIRST + i`（3 + i）算出来的：`floorIndexOf`、`jumpToFloorId`、
 * `applyAuthorFilter` 里"滚到 `FLOORS_FIRST`"、目录面板里 `first < FLOORS_FIRST` 那条判断，
 * 全都读同一个 3。在 `thread-head` **之前**再插一个 item，这个 3 就得变成 4 ——
 * 数字本身好改，坏的是漏改一处不会有编译错误，只会让某一条跳转「少一格」。
 * 塞进已有的那个 item（`PostSectionHeader` 上面）则**一个索引都不用动**：LazyColumn 的
 * item 序列与改动前逐项相同，跳楼 / 日期分隔 / 只看此人 / 目录 / 帖内搜索全部落在正确楼层。
 *
 * ## `#N` /「后面 M 条」从哪来
 *
 * 从 `PostReadStore.readMark(postId)`：它直接给出"上次记下的回帖数"，命中不了时是 `null`
 * （与"记过 0 条"分开）。**关键是时序** —— 打开详情时会立刻 `markRead(当前回帖数)` 把数字
 * 对齐，对齐之后这个方法返回的就是当前值；所以 `PostsScreen` 必须在 `markRead` **之前**把
 * 它取下来，与"有没有增量"一起带进来（见那边的 `resumeUnread` / `resumeMark`）。
 *
 * 拿不到（`null`：第一次读、或已被已读上限淘汰）就不编数字：文案退回「后面有新回复」，
 * 跳转目标退回**第一层楼**，至少省掉"从标题一路划到第一层"那一段。
 * 不做"本地另存一份 mark"这种第二份真相。
 *
 * ## 行为边界
 *
 * 它只是入口：**不做任何自动滚动**，也不改变 `FLOORS_FIRST` 之外的任何索引假设。
 * 目标楼层算不出来（`comments` 为空）时 [onClick] 传 null：整条不挂 `clickable`、
 * 胶囊置灰 —— 不做"点了没反应"的东西。
 */
@Composable
private fun ThreadResumeBar(
    /**
     * 上次记下的回帖数（`PostReadStore.readMark`，在 `markRead` **之前**取）。
     *
     * null = 这台机器上没有这条帖子的精确记录（第一次读、或被已读上限淘汰）：文案退回
     * 「后面有新回复」，不编 `#N` / `M`。
     */
    mark: Int? = null,
    /** 没看到的条数：精确时有 `mark` 就按它算，没有时给当前回帖数当语境。 */
    unseen: Int = 0,
    /** 目标楼层算不出来时为 null：胶囊置灰、整条不挂点击。 */
    onClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    // 置灰只压前景、不换底色：这一条仍然是"这里有个入口"，只是这一次点不动。
    val ink = if (onClick != null) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f)
    val accent = if (onClick != null) scheme.primary else scheme.primary.copy(alpha = 0.38f)
    // 楼层号换算：`mark` 条回复看过了，其中最后一条的位置是 `#(mark + 1)`
    // （主楼是 #1、第 i 条回复是 `#(i + 2)`，所以看过 `mark` 条 = 看到 `#(mark + 1)`）。
    val text = if (mark != null && mark > 0) {
        "上次读到 #${mark + 1} · 后面 $unseen 条没看"
    } else if (mark != null) {
        // `mark == 0`：读过但一条回复都没有 —— 说"读到 #1"（主楼）是准的。
        "上次读到 #1 · 后面 $unseen 条没看"
    } else {
        "后面有新回复"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 与楼层 / [PostSectionHeader] 同一个 16dp 内容区（详情页所有正文块的左缘在一条
            // 直线上）。上面留 12：上面是 `thread-header` 末尾那条 0.5dp 分隔线；
            // 下面不用留 —— [PostSectionHeader] 自己带 16dp 顶距。
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.primaryContainer.copy(alpha = 0.25f))
            // 点击层压在底色之上（clip → background → clickable → padding）：反过来底色会盖住
            // 涟漪，与 [FloorPill] / 抽屉那几处是同一个顺序。
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "接着看",
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = accent,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        // 右侧那颗「接着看」：样式照 [FloorFilterBanner] 里那枚行内胶囊（32dp / 全圆角 /
        // `primaryContainer α0.3` 底 / `primary` 字），比楼层动作行那 36dp 矮一档 ——
        // 它是说明条里的一个动作，不是楼层动作栏。
        //
        // 不单独挂 clickable：整条已经是热区（40dp 高、通栏），胶囊点下去落到外层的
        // `clickable` 上，无障碍那儿只有"接着看"这一个按钮，不会读成两个。
        Box(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(50))
                .background(scheme.primaryContainer.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "接着看",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
            )
        }
    }
}

/**
 * 详情页右侧那条**阅读进度轨**：3dp 宽的竖条，贴右边缘，表示"读到哪儿了"。
 *
 * 形态照 fluxdo `topic_progress_track.dart:9-30` 的 `sidebar` 档（`restThickness = 3`、
 * `vertical = true`、贴边、不挡内容），本仓把数值收窄成下面这一组：
 *
 * ```
 * 条宽        3dp
 * 距右边缘    2dp          （撑到屏幕边上会像一条边框）
 * 上下留白    8dp
 * 轨道色      onSurfaceVariant α0.12
 * 已读色      primary α0.5
 * 点击热区    16dp 宽       （3dp 按不中手指；源码 sidebar 的 grip 也只有 10~17dp）
 * 首屏        alpha 0      （刚进来就竖一根线是纯噪音）
 * ```
 *
 * 比例怎么算：用 `listState.layoutInfo` 里**第一项**的 `index` 和 `offset`（这一项已经滚出
 * 视口多少像素）估出"第一屏往后第几个 item"，再除以 `totalItemsCount - 1`
 * —— `topic_progress_track.dart:94-95` 的 `fraction(index, total)` 就是同一个式子
 * （`total > 1` 才除、结果 clamp 到 0..1，这里照抄这两条边界）。
 * **不**靠 `animateScrollToItem` 去"量"位置：那等于让读代码的人以为它在偷偷滚列表，
 * 而且滚动本身会触发重组、读数又变。
 *
 * 首屏整条 `alpha 0` 用 `animateFloatAsState` 过渡，而不是 `if` 直接抽掉节点：
 * 一进一出不会闪，且 `alpha == 0` 时连点击手势都不挂（看不见的东西不该吃点击）。
 *
 * 点击定位照 `topic_progress_track.dart`（轨道本身可点），落点换算与
 * `topic_reading_rail.dart:86-91` 的 `_commit` 同一件事：按比例挑 item 再滚过去。
 * 注意这是**比例定位**而不是精确落点 —— 楼层高度差得远（三行和三十行都有），
 * 精确映射需要整条列表的高度，那正是 LazyColumn 不肯给的东西。
 */
@Composable
private fun FloorProgressTrack(
    listState: LazyListState,
    /** 对齐由调用点给（`Modifier.align(Alignment.CenterEnd)`，`align` 只有 BoxScope 才有）。 */
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // 只在**读数变了**才重组：滚动时 `layoutInfo` 每帧都在变，直接在组合里读它会让这条轨道
    // 每帧重组一次（它很小，但它是叠在 200 层列表上的一层，能省就省）。
    val progress by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val first = info.visibleItemsInfo.firstOrNull()
            if (total <= 1 || first == null) {
                0f
            } else {
                // 第一项自己滚出去了多少（0..1）。`size <= 0`（还没量出来）时按 0 算：
                // 除出 NaN 会让后面那两段 `drawRoundRect` 拿到 NaN 尺寸（画不出来，还会污染
                // 整层的绘制缓存），所以分母必须先兜住。
                val inside = if (first.size <= 0) {
                    0f
                } else {
                    (-first.offset).toFloat() / first.size
                }
                ((first.index + inside.coerceIn(0f, 1f)) / (total - 1)).coerceIn(0f, 1f)
            }
        }
    }
    // 首屏（第一项是 index 0 且一点都没滚）—— 连"还没量出来"（没有可见项）也算首屏。
    val atTop by remember(listState) {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            first == null || (first.index == 0 && first.offset == 0)
        }
    }
    val trackAlpha by animateFloatAsState(
        targetValue = if (atTop) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "floor-progress-track",
    )
    // 总数只用于点击换算，单独取：它一帖之内只在换帖/换过滤时变，不该跟着滚动重组。
    val total by remember(listState) {
        derivedStateOf { listState.layoutInfo.totalItemsCount }
    }
    val scope = rememberCoroutineScope()
    val trackColor = scheme.onSurfaceVariant.copy(alpha = 0.12f)
    val fillColor = scheme.primary.copy(alpha = 0.5f)
    // 用 `Spacer` 而不是空内容的 `Box`：这一层只有"尺寸 + 手势 + 绘制"，没有任何子项，
    // `Spacer` 就是为这种"只占位、自己画"的节点准备的（也省掉一次布局节点的语义开销）。
    Spacer(
        modifier = modifier
            // **热区 16dp**：3dp 的细条手指按不中，外面这一圈要够宽；它整块透明，
            // 所以视觉上还是一条 3dp 的线（见下面 `drawBehind` 里那 2dp 的右侧内缩）。
            .width(16.dp)
            .fillMaxHeight()
            // 手势挂在 `padding` 之前：热区包含上下那 8dp 留白，手指更好按。
            // 条还没到 alpha 1（首屏 / 没有列表）时整块不挂手势 —— 看不见的东西不该吃点击。
            .then(
                if (trackAlpha > 0.01f && total > 1) {
                    // 键只用 `total`：点击换算要读它，而它在一次滚动里不变，
                    // 所以这个 pointerInput 不会跟着滚动重建。
                    Modifier.pointerInput(total) {
                        detectTapGestures { offset ->
                            val height = size.height.toFloat().coerceAtLeast(1f)
                            val fraction = (offset.y / height).coerceIn(0f, 1f)
                            val target = (fraction * (total - 1))
                                .roundToInt()
                                .coerceIn(0, total - 1)
                            scope.launch { listState.animateScrollToItem(target) }
                        }
                    }
                } else {
                    Modifier
                },
            )
            // 上下各留 8dp：贴到顶栏 / 回复栏上会读成一条边框。
            .padding(top = 8.dp, bottom = 8.dp)
            .alpha(trackAlpha)
            // 两根条都画在这一层里，不用两个 `Box` 叠：
            //  - 轨道是"满高"的一段，已读是"从顶边往下 progress"的一段，两者左边缘对齐；
            //  - `.drawBehind` 排在 `padding` 之后，所以 `size` 是**去掉上下留白**之后的那段高度，
            //    progress 与点击换算用的是同一个高度口径；
            //  - 读 `progress` 发生在绘制阶段（draw phase），滚动只让这一层重绘，
            //    不会把整棵组合树重跑一遍。
            .drawBehind {
                val barWidth = 3.dp.toPx()
                // 右侧内缩 2dp：贴到屏幕边上会读成"这条是边框"，留一口气才显得是叠加的一条轨。
                val x = size.width - 2.dp.toPx() - barWidth
                val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
                // 轨道：整条淡色垫底。
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius,
                )
                // 已读：从顶边往下这么多（`progress` 已 clamp 在 0..1；0 时高度为 0，不画）。
                val filled = size.height * progress
                if (filled > 0f) {
                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(x, 0f),
                        size = Size(barWidth, filled),
                        cornerRadius = radius,
                    )
                }
            },
    )
}

/**
 * 详情页右下角的「回到最新」浮钮：往下滚了一段之后才出现，点一下落到列表**最后一项**（`thread-foot`，
 * 也就是最新回复之后那一格）。
 *
 * ## 为什么没有复用 `ui/component/ScrollToTop.kt`
 *
 * 那个文件先读过了：里面只有**信号源**（`ScrollToTop` 那个计数单例）与**监听器**
 * （`ScrollToTopEffect(state)`，`ScrollToTop.kt:41-54`：听底栏双击，然后 `animateScrollToItem(0)`），
 * **它一个可视元素都不产生**，既没有浮钮的外观，也没有"滚了多少才显示"的判定 —— 没有可复用的东西。
 * 所以照它的风格另写一个，并把它那两条可取的做法搬过来：
 *  1. `ScrollToTop.kt:33` 的 `shouldScroll`：**只在真的偏离目的地时才动作**（在顶部就不滚）。
 *     本浮钮同理，到底之后自己收起来（见下面显隐算式的第 2 条），不做"点了没反应"的按钮；
 *  2. `ScrollToTop.kt:47-53` 的"先记 baseline 再听变化"：判定**只读**列表状态、不写回任何状态。
 *     这里用 `derivedStateOf` 读，滚动时只有浮钮自己重组（与 [FloorProgressTrack] 同一条理由，
 *     见它那边 `derivedStateOf` 的注释）—— 不能让 200 层楼跟着每帧重组。
 *
 * 动画时长也照它：起落不另配 `AnimatedVisibility`（那颗是整块滑入/滑出，会在列表右下角撕出
 * 一条滚动热区被临时占据的空档），直接 `if` 替换 —— 与 `ScrollToTopEffect` 一样"不做多余的动效"。
 *
 * ## 显隐算式（两个条件**同时**成立才画）
 *
 *  1. **已滚过的行程 ≥ 30%**，也就是"还没看到的部分（可见比例）< 70%"：
 *     `progress = (firstVisible.index + inside) / (totalItemsCount - 1)`，
 *     `inside = (-firstVisible.offset) / firstVisible.size` 夹在 0..1 —— 与 [FloorProgressTrack]
 *     （`PostsSection.kt:5291-5310`）用的是**同一个** progress 表达式。两边各写一份的话，早晚会
 *     出现"轨道显示已读了七成、浮钮却还不出来"。
 *
 *     为什么是 70%（即行程 30%）：这条列表的前三格是 `thread-header` / `op` / `thread-head`
 *     （标题 + 主楼 + 续读条），本身就要占掉一屏多；`progress` 到 0.3 时人已经读完主楼、正落在
 *     回复中段 —— 这时"回到最新"才有意义。阈值再低（比如 10%）会在刚进详情、只想看主楼的时候
 *     就盖住正文右下角；再高（比如 90%）则要快到底了才出现，而那时它已经快没用了。
 *  2. **还没到底**：`atBottom` 为真时一律不画 —— 按钮指向的 `thread-foot` 已经在屏幕上，
 *     再挂一颗就是"点了没反应"（与 `ScrollToTop.kt:33` 同一个原则：已经在目的地就别给入口）。
 *     "到底"不用 `canScrollForward`（那是 `ScrollableState` 上的成员，本仓当前 Compose 版本里
 *     能不能直接读没验证过），只用有十年历史的老 API：最后一项可见、且它的下边界已经进到视口里
 *     `1px` 以内 —— 列表的 `contentPadding.bottom` 是 16dp，所以真到底时它比视口下沿高 16dp，
 *     这条判断稳定成立；而"还有内容在下面"时最后一项是**半截**的、下边界正好等于视口下沿，不成立。
 *  `totalItemsCount <= 1`（还没量出来 / 只有标题 / 空帖）时两条都不成立 → 不画。
 *
 * ## 坐标（与另外两块 UI 的关系）
 *
 *  - 视觉 40dp 正圆，外面套 **48dp 透明触控区**（Material 的最小可点尺寸）。调用点给的
 *    `padding(end = 16.dp, bottom = 16.dp)` 加在 48dp 盒子**之外**，所以**触控区**的右边/下边
 *    离屏幕右缘、离列表底边各是 16dp；那颗 40dp 的圆在触控区里居中，看起来因此再收进去 4dp
 *    （视觉上离边 20dp）。这是 M3 浮钮的常规取舍：**16dp 的贴边量给触控区**，视觉圆不会贴到边上
 *    挤着进度轨，也不至于让 48dp 的透明方块吃掉轨道那一条的手势。
 *  - 右侧：48dp 触控区的右边界因此落在 `width - 16dp`。阅读进度轨 [FloorProgressTrack] 的热区是
 *    贴右边的 16dp（`PostsSection.kt:5336` 那句 `.width(16.dp)`），占 `width - 16dp .. width` ——
 *    两者**边界相接、零重叠**（相差正好 0dp，不是负值），40dp 的圆和 16dp 的条谁也抢不到谁的手势。
 *  - 底部：这颗钮是这个列表 `Box` 的子项，而 `ReplyBar` 是 `PostThreadPane` 那个 `Column` 的
 *    **另一个子项**（在 `Box` 下面）。所以 `bottom = 16.dp` 是"回复栏上沿往上 16dp"，
 *    浮钮永远浮在回复栏上方，不会被它盖住。
 *
 * 参考：fluxdo 详情页把一小块 UI `Positioned` 叠在列表右边缘的做法见
 * `lib/pages/topic_detail_page/widgets/topic_scrub_indicator.dart:98`；底色取
 * `surfaceContainerHighest`，与详情页底栏那颗回复胶囊同一档
 * （`lib/pages/topic_detail_page/widgets/topic_bottom_bar.dart:453`）。
 */
@Composable
private fun ScrollToLatestButton(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // 显隐只在这里读：`derivedStateOf` 把每帧都在变的 `layoutInfo` 折成一个布尔量，
    // 滚动时只有这个 composable 跟着重组。`remember(listState)` 的键只认列表，
    // 换帖/换过滤都不会让它拿着旧状态（`layoutInfo` 本来就是从 `listState` 现读的）。
    val show by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val first = info.visibleItemsInfo.firstOrNull()
            if (total <= 1 || first == null) {
                false
            } else {
                // 第一项自己滚出去了多少（0..1）。`size <= 0`（还没量出来）按 0 算：
                // 除出 NaN 会让下面的比较恒为 false，浮钮永远不出现 —— 而且 NaN 会顺着
                // `derivedStateOf` 的依赖往下传（与 [FloorProgressTrack] 里那句同一个理由）。
                val inside = if (first.size <= 0) 0f else (-first.offset).toFloat() / first.size
                val progress = ((first.index + inside.coerceIn(0f, 1f)) / (total - 1))
                    .coerceIn(0f, 1f)
                val last = info.visibleItemsInfo.lastOrNull()
                // 到底：最后一项在屏上，且它的下边界已经进了视口（留 1px 容差，见 KDoc 第 2 条）。
                val atBottom = last != null &&
                    last.index >= total - 1 &&
                    last.offset + last.size <= info.viewportEndOffset - 1
                // 「可见比例 < 70%」= `1 - progress < 0.7`，等价于 `progress > 0.3`。
                (1f - progress) < 0.7f && !atBottom
            }
        }
    }
    if (!show) return
    Box(
        modifier = modifier
            // 48dp 是**触控区**（那颗 40dp 的圆在它里面居中）；透明的，所以视觉上仍是一颗 40dp 的钮。
            .size(48.dp)
            .clip(CircleShape)
            // 不写 `onClickLabel`：读屏要念的那句话在图标上（`contentDescription = "回到最新"`），
            // 两处都给会把同一句话念两遍。
            .clickable(role = Role.Button) {
                scope.launch {
                    // 滚到最后一项 = `thread-foot`：最新回复之后那句说明，也就是"最新"那一端。
                    // 每一帧现读 `totalItemsCount`，不用记住的常量：列表会被刷新换血。
                    val lastIndex = listState.layoutInfo.totalItemsCount - 1
                    if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHighest)
                // 1dp 描边排在 `background` 之后：线画在圆的内侧，外径仍是 40dp
                //（与 [NestedRepliesList] 末尾那颗 28dp 收起钮同一个口径）。
                .border(1.dp, scheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "回到最新",
                modifier = Modifier.size(18.dp),
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 楼层之间的**日期分隔线**：一条 0.5dp 横线，正中间一块页面底色的小标签，写着「今天 / 昨天 / M月d日」。
 *
 * 照 fluxdo `post_segment_frame.dart:97-148`（日期标签骑在楼层底边框上：
 * `FractionalTranslation(0, ±0.5)` + 居中 + `padding h10 v1` + `labelSmall 11` +
 * `onSurfaceVariant α0.6`）。
 *
 * 为什么值得做：本仓的楼里只有 `relativeStamp`（"3 分钟前"），一层层往下滚十几层就分不清
 * "这是上午那批人还是隔天回的"。分隔线只在线**跨天**时插一条，不跨天的楼层一点额外高度都不占。
 *
 * 标签底色必须不透明（页面色）：它是用来把线"咬断"的，透明的话线会从字下面穿过去。
 */
@OptIn(kotlin.time.ExperimentalTime::class)
@Composable
private fun FloorDateSeparator(iso: String) {
    val scheme = MaterialTheme.colorScheme
    // 标签文字直接复用 `BeijingTime.dayLabel`（聊天里的日期分隔条就是它）：同一个项目里"今天/昨天/
    // M月d日"不该有两种写法。`dayKey` 负责判断要不要画，`dayLabel` 负责写什么。
    // 它是 `@OptIn(ExperimentalTime::class)` 的：opt-in 只挂在这个函数上，不传染给别的调用方。
    val label = BeijingTime.dayLabel(iso)
    if (label.isEmpty()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 左右 16 与楼层自己的边距对齐，线不从屏幕边上长出来。
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(scheme.outlineVariant.copy(alpha = 0.5f)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = metaTint(scheme.onSurfaceVariant, 0.6f),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                // 不透明页面色：它是用来把线"咬断"的，透明的话线会从字下面穿过去。
                .background(scheme.surface)
                .padding(horizontal = 10.dp, vertical = 1.dp),
            maxLines = 1,
        )
    }
}

/**
 * 「回复 #N · 名字」那条窄指示条，照 fluxdo `post_header.dart:422-487` 的 `_buildReplyIndicator`：
 *
 * - 容器：`padding(h 8, v 5)`、底色 `primary α0.08`、圆角 **20**、边框 1dp `primary α0.1`
 * - 内容：`reply_rounded` **14dp**（主色）→ 6dp → 对方头像 **20dp** → 4dp → 名字
 *   `labelSmall` 抬到 **11sp**、主色、最多 [INLINE_REPLY_NAME_MAX] 之后省略号
 * - 位置：**名字那一行的右侧、时间/楼层号左边**，与源码 `_buildRightSection` 的
 *   「指示条 → 12 → 时间/楼层号」一致。整条不再单占一行 —— 那样一层楼多花 30dp，
 *   引用一多整页就垮了。
 *
 * [onClick] 为 null 时**不挂 `clickable`**：目标楼层不在当前列表里（刚被删 / 还没拉到）时，
 * 这条指示条仍然要画（"在回谁"是有用的信息），但点下去没有地方可去，就不该有个假手势。
 *
 * [onLongClick] 是「回复历史」的入口（原地展开父链，见 [ReplyHistoryList]）——**长按而不是加箭头**，
 * 理由见该参数自己的注释。它同样在"父楼不在列表里"时为 null，于是那条通路不给手势、一个组合项
 * 也不多变。
 *
 * [floor] 是对方楼层号；找不到（-1）时只写名字，不编一个 `#-1`。
 */
@Composable
private fun FloorReplyIndicator(
    floor: Int,
    author: String,
    authorSeed: String,
    onClick: (() -> Unit)? = null,
    /**
     * 长按：在原地展开 / 收起这一层的「回复历史」（父链）。
     *
     * 为什么选长按、不在指示条右侧加一颗展开箭头：这条指示条是按**内容宽**画的（`Row` 没
     * `fillMaxWidth`），里面已经挤着 `reply_rounded` 14dp + 6 + 头像 20dp + 4 + 名字（最多
     * [INLINE_REPLY_NAME_MAX] = 96dp）。再塞一颗 12~14dp 的箭头：
     *  1. 名字那 96dp 得再让一格，长名字直接变成"回复 #3 · 张…"；
     *  2. 指示条只有 5dp 上下内边距、整条高 30dp，14dp 的箭头贴着圆角边框，看着像残次品；
     *  3. 箭头会**一直显示**，而"这里能展开父链"不是主操作（主操作是点它跳过去），常驻图标反而
     *     抢走点击焦点。
     * 长按不占一个像素：无引用 / 父楼已被删的那些楼层，指示条与改动前**逐项一致**（见 [onLongClick]
     * 的默认值）。代价是可发现性差一档 —— 由 `onLongClickLabel`（读屏会念「展开回复历史」）与
     * 父链展开后卡片自身的形状补回来。
     */
    onLongClick: (() -> Unit)? = null,
    /** 这一层的父链现在展开着没有：只影响长按的无障碍标签，不影响任何绘制。 */
    expanded: Boolean = false,
    /**
     * 是不是挂在名字行右侧的那一版。
     *
     * 参考实现里它就和时间/楼层号同一行（`post_header.dart:364-419`），所以这里是**默认形态**：
     * 按内容宽、不外撑、名字最多 [INLINE_REPLY_NAME_MAX]。false 时退回"整行铺满 + 右留 12"，
     * 给以后需要在正文里单独铺一条的场合留个口子。
     */
    inline: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val name = author.ifBlank { "匿名" }
    Row(
        modifier = Modifier
            .then(
                if (inline) {
                    Modifier
                } else {
                    // 整行版：`Row` 自己撑满宽度，指示条内容仍按内容宽，右边 12dp 就是源码里
                    // `SizedBox(width: 12)` 的等价物。
                    Modifier.fillMaxWidth().padding(end = 12.dp)
                },
            )
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.primary.copy(alpha = 0.08f))
            // 边框 **1dp `primary α0.1`**。用 `drawBehind` 里的 `Stroke` 而不是 `border()`：
            // `border()` 会在内容之外再加 1dp，指示条高度就会比 5+20+5 高一点；
            // 画在内部，高度精确等于头像那一行的 20dp + 上下 5dp。
            .drawBehind {
                drawRoundRect(
                    color = scheme.primary.copy(alpha = 0.1f),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            // 内边距必须排在 clickable 之前：这样点击区覆盖到圆角里的整条，而不是只有字。
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .then(
                if (onLongClick != null) {
                    // 单击 = 跳走、长按 = 原地展开父链，两种手势落在同一个区域里。
                    // `combinedClickable` 就是为这个存在的（列表卡片的长按收藏同一套，见 PostCard）。
                    // `onClick ?: {}`：能长按的前提是父楼在列表里，那时 `onClick` 一定非空；
                    // 兜一个空 lambda 只是为了让签名成立，不会真的走到"点了没反应"。
                    Modifier.combinedClickable(
                        role = Role.Button,
                        onClickLabel = if (onClick != null) "跳到被回复的楼层" else null,
                        onLongClickLabel = if (expanded) "收起回复历史" else "展开回复历史",
                        onLongClick = onLongClick,
                        onClick = onClick ?: {},
                    )
                } else if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "跳到被回复的楼层", onClick = onClick)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 官方图标没有自绘 glyph 的 `fill = 0f` 陷阱（那条只影响 `GlyphRole.Body`），这里不必再操心。//
        // "fill=0 画不出来"的那一档，见 Glyph.kt 的 drawBody）。
        Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(14.dp), tint = scheme.primary)
        Spacer(modifier = Modifier.width(6.dp))
        PostAvatar(name = name, seed = authorSeed.ifEmpty { name }, size = 20.dp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (floor >= 2) "回复 #$floor · $name" else "回复 $name",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = scheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // 同排那一版名字最多 12 个字（源码 `ConstrainedBox(maxWidth: 80)` 是按 **8~9 个中文字**
            // 的宽度定的；本仓的字号是 11sp，这里按同样的观感给 96dp），再多就省略：
            // 名字比它旁边的正文长，标题区会被这一条撑歪。
            modifier = Modifier.widthIn(max = if (inline) INLINE_REPLY_NAME_MAX else 200.dp),
        )
    }
}

/**
 * 一层楼：左边头像，右边作者、楼层号、时间与正文，底下一行动作。
 *
 * [floorLabel] 是 `#1` / `#2` —— 楼层号让"第几层"这件事一眼对得上，也是引用回复的抓手。
 *
 * 注意：fluxdo 的**主楼也是 `#1`**，没有「楼主」这种写法
 * （`post_header_section.dart:356` 的 `'#${post.postNumber}'`）。
 */
@Composable
private fun PostFloorCard(
    floorLabel: String,
    author: String,
    authorSeed: String,
    stamp: String,
    body: String,
    badges: List<String>,
    /**
     * 「这一楼在回哪一楼」那条指示条（fluxdo `post_header.dart:422-487`：`primary α0.08` 底、
     * 圆角 20、`primary α0.1` 边框、`reply_rounded` 14dp + 6 + 对方头像 20dp + 名字 `labelSmall 11`）。
     *
     * **默认 null 什么都不画**，`Column` 里那一段就一个组合项都不建，布局与没有这一条时一样。
     * 实际内容由调用点传（[FloorReplyIndicator]）：楼层数据（`replyToId` / `replyToAuthor`）
     * 在 `comments` 里，`PostFloorCard` 自己拿不到，也就不该自己去找。
     *
     * 位置：**作者名那一行的右侧、时间/楼层号左边**（源码 `post_header.dart:364-419` 的
     * `_buildRightSection`）。
     */
    replyIndicator: (@Composable () -> Unit)? = null,
    /**
     * 展开后的「直接回复」列表（fluxdo `post_replies_list.dart` + `embedded_posts.dart`）：
     * 竖线串起一串 28dp 头像，正文 13sp/1.4，末尾一颗 28dp 的收起按钮。
     *
     * 默认 null —— 收起状态下这一层楼和没有回复时**完全一样高**。谁有子回复、展开没有，
     * 只有调用点（手里有整份 `comments`）知道，所以内容在这里由调用点给。
     */
    replies: (@Composable () -> Unit)? = null,
    /**
     * 正文里的 `#N` 楼层引用被点时的回调（`MiniMarkdown` 的 `onFloorRef`）。
     *
     * 本仓的引用回复会把 `> #N 作者：…` 塞进正文（见 `quoteComment`），那些数字本来只是死文字；
     * 接上这条之后点一下就到那一层 —— 与「回复 #N · 名字」指示条同一个落点。
     *
     * 默认 null：编辑器预览、回复历史卡等**没有"跳转"语义**的地方不传，`#N` 保持普通文字。
     * （楼层号 → 楼层 id 的换算是调用点的事：`#1` 是主楼、`#2` 起对应 `comments[0]`。）
     */
    onFloorRef: ((Int) -> Unit)? = null,
    /**
     * 展开的「回复历史」：沿 `replyToId` 往上最多 3 层的小卡（[ReplyHistoryList]）。
     *
     * 位置在**名字行下面、正文上面**：参考实现把父帖卡挂在头部正下方
     * （`post_reply_history.dart:47-52` 的 `Padding(only(top: 12))` 套 `ParentPostCard` 组），
     * 读起来是"这条在回谁"的一段展开注脚，而不是这一楼正文的一部分 —— 放进正文下面会变成
     * "正文 → 别人的正文"，层级反了。
     *
     * 默认 null，和 [replies] 同一个口径：收起时这一层楼的组合项一个都不多。
     * 链条只由调用点（[PostThreadPane]，手里有全量 `comments`）算，这里不扫列表。
     */
    replyHistory: (@Composable () -> Unit)? = null,
    /**
     * 这一层是不是"刚刚被跳过来"的那一层。true 时左边画一条 3dp 主色竖条并在 1.5s 后淡出
     * （fluxdo `post_segment_frame.dart:164-183` 的 `_PostSelectionIndicator`：`left/top/bottom = 0`
     * 的 `SizedBox(width: 3)`）。淡出靠 `animateColorAsState`：它不是开关一个 `if`，
     * 而是让颜色自己走回透明，所以竖条**不会在动画中途被抽掉**（那会闪一下）。
     */
    highlighted: Boolean = false,
    /**
     * 这一层是**已删**（`post_segment_frame.dart:152-160/59-67`）：整块 `Opacity(0.6)` +
     * 底色 `errorContainer α0.15`。本仓后端删帖是真的删行（刷新后楼层就没了），所以只有
     * "刷新前手里那份列表里还留着它"的这一小段时间会画到这里；留着这条通路是为了别在
     * 那种一帧上把它画成和活楼层一模一样。
     */
    deleted: Boolean = false,
    /**
     * 长的正文现在是不是"展开全文"那一态（判定见 [isLongFloorBody]，门槛是 800 字 / 20 行）。
     *
     * 状态**不在这里**：这个 composable 是无状态的（同一份它被 200 层楼复用，自己记状态就等于
     * 把 200 份状态塞进一个被回收来回收去的列表项里），由 [PostThreadPane] 按楼层 id 记住再传进来
     * —— 与 `replies` / `replyIndicator` 同一个做法。
     */
    bodyExpanded: Boolean = false,
    /**
     * 「展开全文（共 N 字）」/「收起」的落点；**null = 这一层不给折叠**。
     *
     * 短正文（不到 800 字且不到 20 行）走的是下面那条**与加折叠之前逐项一致**的分支：不套
     * 容器、不加按钮、不多一个组合项，所以常见楼层的高度一个字都没变。
     */
    onToggleBody: (() -> Unit)? = null,
    footer: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 竖条颜色：`on` 时主色，之后走回透明。动画过来的颜色**必须**参与 `drawBehind` 的键，
    // 否则 lambda 捕获的是初始色，竖条永远画不出来（合成阶段不会因为状态变了而重画）。
    val barColor by animateColorAsState(
        targetValue = if (highlighted) scheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = HIGHLIGHT_FADE_MS),
        label = "floor-highlight",
    )
    // 这一层的底色：正常是页面色，已删的那一层压在页面色下面垫一格 `errorContainer α0.15`
    // （源码 `post_segment_frame.dart:152-160` 同色同透明度）。摘出来单独一个值，是因为折叠态
    // 那层渐变遮罩的**终点色必须与它一模一样** —— 写死 `scheme.surface` 的话，已删楼层/换主题
    // 时遮罩底部会现出一条颜色不同的横带。
    val floorBackground = if (deleted) {
        scheme.errorContainer.copy(alpha = 0.15f).compositeOver(scheme.surface)
    } else {
        scheme.surface
    }
    // 要不要折：只有调用点给了动作、且正文确实长（800 字 / 20 行）才折。按 `body` 记忆 ——
    // 列表刷新时没变的正文不重算，O(n) 只付一次。`remember` 放在 `&&` 前面（而不是后面）：
    // 短路写在前面的 `onToggleBody != null` 一旦由 null 变成非 null，后面的 `remember` 就
    // 从"没调用过"变成"调用"，那条条件分支的组合位置会挪一格。
    val foldable = remember(body) { isLongFloorBody(body) } && onToggleBody != null
    // 折叠态容器的高度：8 行 × 21sp（fontScale = 1 时 168dp，换算见 [foldedBodyMaxHeight]）。
    val foldedMaxHeight = foldedBodyMaxHeight(LocalDensity.current)
    val toggleBody: () -> Unit = { onToggleBody?.invoke() }
    // 通栏 + 分隔线，不是卡片：fluxdo 的一层楼是 `PostSegmentFrame` 里 `Padding(EdgeInsets.all(16))`
    // 的一块**无背景、无圆角**的内容，层与层之间靠一条 0.5dp 的底边框分开
    //（`post_item.dart:263-264` / `post_segment_frame.dart:63-67`）。
    //
    // 为什么分隔线画在**这一层自己的底部**、而不是在 LazyColumn 里插 `HorizontalDivider`：
    //  1. LazyColumn 里每层之间还有层间距，插进来的那一条上下各带一份空隙，读起来是
    //     "一块内容、一条悬空的细线、一块内容"，不是通栏连续的分隔（header 末尾那条也是贴着自己的）；
    //     所以层间距同时压到 4dp（原 8dp），让这条线读作"这一层的下边界"，而不是悬在两层正中间；
    //  2. 插 item 会多出 N 个组合项（200 层楼就多 200 个），滚动的组合/测量账翻一倍；
    //  3. 画在这一层自己身上就等于放到任意列表容器里都对——以后楼层被搬进别的滚动容器不用改。
    // 左右边距因此收成 16（源码 `all(16)`），删掉了原来的 12+12+圆角+Block 底。
    // 分隔线覆盖的是 content 区（含 padding）：drawBehind 画在 padding 之内，所以线宽 = 满宽 - 32，
    // 与 fluxdo 的 `Border(bottom)`（同样的 content 宽）一致。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 底色用**页面色**：分隔线要压在这块底色上（日期标签也是这么遮线的，
            // 见 `post_segment_frame.dart:48-51`），换成透明的话画不到目标上。
            // 值见上面的 `floorBackground`（折叠态渐变要跟它对齐）。
            .background(floorBackground)
            // 上下 **14**（参考 `post_segment_frame.dart:63-67` 是 `all(16)`）：本仓这一层带着
            // 一条 0.5dp 的底分隔线（线画在自己的 padding 之内），16+16+4（层间距）= 36dp 的
            // 层间空白会把"楼"切断成一屏一屏；14 是折中值 ——
            // 算式：14（本层底）+ 4（LazyColumn 层间距）+ 14（下一层顶）= **32**，
            // 与参考的 16 + 16 = 32 完全一致，所以层间距保持 4 不动、不必再动 `spacedBy`。
            .padding(horizontal = 16.dp, vertical = 14.dp)
            // 0.5dp 分隔线，颜色 `outlineVariant α0.5`（`post_segment_frame.dart:47`）。
            // `.drawBehind` 排在 `.padding(…)` **之后**，所以它的 `size` 是内容区尺寸：线等于
            // "满宽 − 32"，与 fluxdo 的 `Border(bottom)`（画在内容边界上）同一个宽度，
            // 也和 header 末尾那条 `HorizontalDivider`（同样处在 16dp 内容区里）对齐。
            .drawBehind {
                // 被跳过来的那一层：左侧 3dp 主色竖条，整层高（含上下 padding，源码同样是
                // `top: 0, bottom: 0`）。淡出后 `barColor` 的 alpha 归 0，画了也看不见。
                if (barColor.alpha > 0.01f) {
                    val barWidth = 3.dp.toPx()
                    drawRect(
                        color = barColor,
                        topLeft = Offset.Zero,
                        size = Size(barWidth, size.height),
                    )
                }
                val w = 0.5.dp.toPx()
                drawLine(
                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                    start = Offset(0f, size.height - w / 2f),
                    end = Offset(size.width, size.height - w / 2f),
                    strokeWidth = w,
                )
            },
    ) {
        // 已删的那一层整块退到 60%（`post_segment_frame.dart:158-160` 的 `Opacity(opacity: 0.6)`）。
        // 只包内容、不包底色与竖条：源码那个 RepaintBoundary 同样只包「内容」，
        // 把底色一起压会让页面底色跟着变灰，看着像整块挂了。
        Box(modifier = if (deleted) Modifier.alpha(0.6f) else Modifier) {
            // 注意这里**没有** `.verticalArrangement = spacedBy(...)`：外层 Column 的间距会匀给
            // 定长孩子，实测 4dp 只落成 3.4、19→17.6，行高/胶囊高就不准了。层内三段留白靠各段自己的 padding。
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // 头像 **40dp** + 1dp 描边（fluxdo `post_header.dart:72` 的 `radius = 20`，
                // 以及 `:131-134` 的 `Border.all(outlineVariant, 1)`）。描边画在内部，
                // 用 `drawBehind` 画圆环而不是 `border()`，这样尺寸不会被撑大。
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .drawBehind {
                            drawCircle(
                                color = scheme.outlineVariant,
                                radius = this.size.minDimension / 2f,
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        },
                ) {
                    PostAvatar(name = author.ifBlank { "匿" }, seed = authorSeed, size = 40.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // 头部照 fluxdo `post_header.dart:233-360`：左边一列是「名字 + 徽章」，
                    // 右边一列是「时间 / 楼层号」**竖排两行**（时间在上、楼层号在下）。
                    // 上一版把楼层号和名字挤在同一行，长名字时时间/楼层号会被压到 0 宽。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        // 整行 `CrossAxisAlignment.center`（源码 `post_header.dart:223-224`）：头像 40、
                        // 名字与右列都对着头像的中线，而不是各自贴着顶边。
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AuthorLabel(
                                name = author.ifBlank { "匿名" },
                                // **14sp / w600**：`post_header.dart:240-249` 的名字行是
                                // `titleSmall?.copyWith(fontWeight: w600, fontSize: 14)`。
                                // 本仓 `titleSmall` 被 FlTypography 映射到 `subheadline`（15sp、W400），
                                // 不显式压回来名字就跟旁边的正文一样粗、还大一号 —— 头像旁边那行
                                // 认不出"这是谁说的"。
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = scheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            badges.forEach { badge ->
                                Spacer(modifier = Modifier.width(4.dp))
                                PostBadge(
                                    text = badge,
                                    color = when (badge) {
                                        // 置顶：绿（这一条是被挑出来的）
                                        "置顶" -> PostPinned
                                        // 已锁：中性灰。原来跟"我"一样吃 `primary`，同一行里两个蓝徽章
                                        // 分不出哪个是权限状态、哪个是身份标记。
                                        "已锁" -> scheme.outline
                                        else -> scheme.primary
                                    },
                                )
                            }
                        }
                        // 引用指示条：**名字行右侧、时间/楼层号左边**（fluxdo `post_header.dart:364-419`
                        // 的 `_buildRightSection`：指示条 → 12 → 时间/楼层号）。上一版把它另起一行放在
                        // 名字下面，每层楼白多占 30dp，引用多时整页往下垮，而且"这条在回谁"读不出层级。
                        if (replyIndicator != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            replyIndicator()
                        }
                        Column(
                            // 指示条与「时间 / #N」之间 **12dp**：`post_header.dart:389`
                            // 的 `const SizedBox(width: 12)`（`_buildRightSection` 里
                            // 「指示条 → 12 → 时间/楼层号」）。上一版写 8，指示条贴得比参考近。
                            modifier = Modifier.padding(start = 12.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            if (stamp.isNotBlank()) {
                                Text(
                                    text = stamp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = metaTint(scheme.onSurfaceVariant, 0.8f),
                                    maxLines = 1,
                                    // 右列是"固定宽度 + 名字先占"，窄屏上时间可能只剩几个字：
                                    // 不给 `Ellipsis` 时 `Text` 默认是 `Clip`，会硬切出半截"3 天"。
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            // 楼层号：**10sp / onSurfaceVariant α0.4**（`post_header_section.dart:357-362`
                            // 的 `labelSmall + fontSize: 10`），在时间下面一行、右对齐。
                            Text(
                                text = floorLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = metaTint(scheme.onSurfaceVariant, 0.4f),
                                maxLines = 1,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    // 引用指示条已经挪到上面名字行里（见那段注释），这里不再单独占一行：
                    // 一层楼的高度回到「头像/名字行 + 正文 + 动作行」三段，楼层之间的密度才跟得上参考图。
                    // 回复历史（父链）就挂在指示条正下方、正文之前：null 时这一段一个组合项都不建，
                    // 楼层高度与没有这个功能时逐项一致。
                    // 顶 **8dp**（参考 `post_reply_history.dart:48` 是 12）：父链是一张**内嵌小卡**
                    // （自己带圆角与底色），它上面的 8 就是在卡与名字行之间"留一口气"，
                    // 不必跟下面"头部 → 正文"的 12 对齐 —— 正文是裸文字，需要更大的呼吸；
                    // 上一版这里写的是"跟下一段 Spacer 同一个数"，那条前提随正文间距改成 12 已不成立。
                    if (replyHistory != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        replyHistory()
                    }
                    // 正文与头部之间 **12dp**（`post_item.dart:284` 的 `SizedBox(height: 12)`）。
                    // 上一版这行注释写的就是 12、代码却是 8（注释与代码不一致本身就是隐患），
                    // 这次把代码补齐到 12：头部那行名字的基线到正文首行，只留 8 会让"谁说的"
                    // 和"说了什么"黏在一起，尤其名字后面还跟着徽章时。
                    // 不挂 `Arrangement.spacedBy`：见上面那段注释。
                    Spacer(modifier = Modifier.height(12.dp))
                    if (body.isBlank()) {
                        Text(
                            text = "（没有正文）",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        // 正文 **14sp / 行高 1.5 = 21sp**（`post_item.dart:332-347` 的
                        // `bodyMedium.copyWith(height: 1.5)`）。本仓 `bodyMedium` 被 FlTypography
                        // 抬到 15/20，不显式压回来每层楼会多占约 5% 的高度、密度明显比 fluxdo 松。
                        //
                        // 收成一个局部 lambda，是因为下面三条分支共用同一段渲染：**正文源码一次都不截**
                        // ——把 Markdown 截成半截再交给它，切在表格/代码块中间时出来的是残片。
                        // 折叠作用在**容器**上（高度 + 裁剪 + 遮罩），正文本身始终是完整的一份。
                        val bodyText: @Composable () -> Unit = {
                            MiniMarkdown(
                                text = body,
                                baseStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = FOLDED_BODY_LINE_HEIGHT,
                                ),
                                color = scheme.onSurface,
                                // 正文里的 `#N` 变可点（引用回复塞进来的 `> #N 作者：…` 就是它）：
                                // 传 null 时 `MiniMarkdown` 走短路，`#N` 仍是普通文字，输出与改动前一致。
                                onFloorRef = onFloorRef,
                            )
                        }
                        // 短正文 / 已经展开：与加折叠之前**逐项一致**（短正文连按钮都不画）。
                        if (!foldable || bodyExpanded) {
                            bodyText()
                            if (foldable) {
                                FoldBodyToggle(label = "收起", onClick = toggleBody)
                            }
                        } else {
                            // 折叠态：容器夹到 8 行高，超出的部分裁掉，再在底部叠一段 32dp 的
                            // 竖向渐变（透明 → 底色），读起来是"下面还有，淡出去了"。
                            //
                            // 遮罩用 `drawWithContent` **画**、而不是再叠一个 Box：叠出来的那个
                            // 兄弟节点会参与命中测试，把正文最后几行的点击（链接、图片）吃掉；
                            // 画上去的东西没有布局节点，一个事件都不碰。
                            //
                            // 不做 `animateContentSize`：200 层楼的列表里给每层挂一个尺寸动画，
                            // 展开/收起那一刻会把整屏的测量拉长一倍，滚动跟手感直接崩掉。
                            // 盒子高度跳变、滚动位置不动（上面的内容一像素都没挪）。
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = foldedMaxHeight)
                                    .clipToBounds()
                                    .drawWithContent {
                                        drawContent()
                                        // `coerceAtMost(size.height)`：容器万一比遮罩还矮
                                        // （正文只有几行、却因为长行被判成长），`startY` 会成负数、
                                        // 渐变直接画到框外。夹一下，这种边角上最多是"整块都是实色"。
                                        val fade = FOLDED_BODY_FADE_HEIGHT.toPx()
                                            .coerceAtMost(size.height)
                                        drawRect(
                                            // 终点是这一层的底色、起点透明：底部是实色（第 8 行
                                            // 被压淡），往上淡出到没有。
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, floorBackground),
                                                startY = size.height - fade,
                                                endY = size.height,
                                            ),
                                            topLeft = Offset(0f, size.height - fade),
                                            size = Size(size.width, fade),
                                        )
                                    },
                            ) {
                                bodyText()
                            }
                            FoldBodyToggle(
                                label = "展开全文（共 ${body.length} 字）",
                                onClick = toggleBody,
                            )
                        }
                    }
                    // 这一行原来靠父级 `spacedBy(4.dp)` 与正文隔开；父级间距去掉后自己垫。
                    // **8dp**：参考 `post_item.dart:469` 给 `PostFooterSection` 的是
                    // `EdgeInsets.only(top: 12)`，这里取 8 的折中理由是 —— 本仓动作行的胶囊高
                    // 36、参考 `post_action_bar.dart:418-419` 也是 36，正文行高两边同为 21，
                    // 8 已经足够让胶囊顶边离开字的降部；再拉到 12 会让"正文最后一行的行距 +
                    // 12"在视觉上读成两个段落，而参考那边正文与 footer 之间本来就夹着引用/尾注。
                    Spacer(modifier = Modifier.height(8.dp))
                    footer()
                    // 展开的直接回复：动作行**下面**，顶 4dp（`post_replies_list.dart:36-37`）。
                    if (replies != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        replies()
                    }
                }
            }
        }
    }
}

/**
 * 长正文底下那颗「展开全文（共 N 字）」/「收起」。
 *
 * 用 `TextButton` 而不是自绘一行 `clickable`：它自带按钮的无障碍角色、涟漪与最小触控高度，
 * 而这里要的正是一颗"点得着的一行字"。`contentPadding` 收到 0，文字**贴正文的左边缘**——
 * 正文没有缩进，按钮再缩一格就成了另一层的东西。
 *
 * 展开与收起只换文案（「收起」）、不换图标不换颜色：同一颗按钮连点两次是展开→收起，位置不动，
 * 手指不用重新找。
 */
@Composable
private fun FoldBodyToggle(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * 一层楼原地展开的「回复历史」：沿 `replyToId` 往上最多 [REPLY_HISTORY_DEPTH] 层，一层一张小卡。
 *
 * 参考实现 `post_reply_history.dart:47-70`：把 `replyHistory` 整个列成一列 [ParentPostCard]
 * （`embedded_posts.dart:43-152`）。本仓的差别只有两处，都是为了"引用一多别炸一屏"：
 *  1. 层数封顶 3（那边是后端给多少列多少，本仓 `replyToId` 是自由引用，可以连环引用十几层）；
 *  2. 圆角统一 8dp（那边走 `SegmentedCardItem`，首尾各切一角；这里三条各自独立，切角反而像
 *     拼错的分组）。
 *
 * 链条**不在这里算**：`PostFloorCard` 是无状态的（同一份被 200 层楼复用），`comments` 也不在它
 * 手上。算链条的地方在 [PostThreadPane]，那里有全量 `comments` 与现成的 id → 楼层表。
 *
 * [onJump] 为 null 时卡片不挂手势（只读）。真给的话，点一张父卡 = 跳到那一层并**收起整条链**
 * （收起由调用点做），与指示条的单击同一个落点 —— 跳走了链却还摊在那里，人回头会以为那是
 * 目标楼的下一层。
 */
@Composable
private fun ReplyHistoryList(
    chain: List<PostComment>,
    floorLabelOf: (Long) -> Int,
    /** 已经展开全文的那些卡片键（[replyHistoryCardKey]）。 */
    unfoldedKeys: Set<String>,
    onToggleUnfold: (String) -> Unit,
    onJump: ((Long) -> Unit)?,
    /**
     * 父链卡片**正文里** `#N` 楼层引用被点时的回调，直接交给 [ReplyHistoryCard] 再交给
     * `MiniMarkdown(onFloorRef = …)`。
     *
     * 与 [onJump] 是两条**不同的**手势，语义必须分清：
     *  - 点**卡片本身**（`onJump`）= 跳到**这一张卡**对应的那一层；
     *  - 点正文里的 `#N`（本参数）= 跳到**那句正文指到的那一层** —— 它可能根本不是链上的任何一层
     *    （父帖正文里随手引用了一个别的楼层），所以两条路不能合成一条。
     *
     * 默认 null：`MiniMarkdown` 内部对 null 是短路的（见 `MiniMarkdown.kt` 里 `onFloorRef` 那段），
     * 于是 `#N` 仍是普通文字 —— 与加这个参数之前**逐项一致**。
     */
    onFloorRef: ((Int) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        chain.forEachIndexed { depth, parent ->
            if (depth > 0) {
                Spacer(modifier = Modifier.height(REPLY_HISTORY_CARD_GAP))
            }
            val key = replyHistoryCardKey(parent.id, depth)
            ReplyHistoryCard(
                parent = parent,
                floor = floorLabelOf(parent.id),
                unfolded = key in unfoldedKeys,
                onToggleUnfold = { onToggleUnfold(key) },
                onJump = onJump,
                // 父卡正文里的 `#N` 也能点。与卡片的"点整卡跳自己"不冲突：`#N` 走的是
                // `MiniMarkdown` 生成的 `LinkAnnotation.Clickable`（文字自己的点击处理器会吃掉
                // 那一下），而"点整卡"是这个 `Column` 上的 `clickable`；子节点先收到事件并消费，
                // 祖先的 `clickable` 在 up 已被消费时不会触发 —— 与楼层卡片里 URL 链接
                // 早就同屏共存的那套机制是同一条（见 [ReplyHistoryCard] 的 KDoc）。
                onFloorRef = onFloorRef,
            )
        }
    }
}

/**
 * 父链上的一张卡：**谁（`#N`）+ 说了什么（3 行）**，点一下跳到那一层。
 *
 * 逐项照 `embedded_posts.dart:43-152` 的 `ParentPostCard`：
 * - 首行：`reply_rounded` **14dp**（主色）→ 6dp → 头像 **20dp**（`radius: 10`，
 *   `embedded_posts.dart:110-115`）→ 6dp → 名字 `labelMedium` **w600** 单行省略
 *   （`:120-129`）→ 右侧 `#N` `labelSmall` + `onSurfaceVariant`（`:130-135`）
 * - 正文：13sp / 行高 1.4（=18sp）+ `onSurfaceVariant`（`:27-41` 与 `:136-146`），只露 3 行
 * - 外壳：`surfaceContainerHighest α0.3` 底 + 圆角 8dp + 内边距 12dp（`:100-104` 的 `all(12)`；
 *   底色本仓沿用楼层动作胶囊那一档，见 [FloorPill]，同屏两块"次级块"的底色才是一个调子）
 *
 * 3 行折叠与楼层正文的"长帖折叠"是**两套**，阈值/容器/状态都不共用（见 [REPLY_HISTORY_DEPTH]
 * 上面那段）。折叠判定是估算（[isLongReplyCardBody]），估漏了只是不折、不会切字。
 */
@Composable
private fun ReplyHistoryCard(
    parent: PostComment,
    /** 这一张在 `comments` 里的楼层号；< 2 表示那一层已不在列表里（理论上不会，链条只用列表内的行）。 */
    floor: Int,
    unfolded: Boolean,
    onToggleUnfold: () -> Unit,
    onJump: ((Long) -> Unit)?,
    /**
     * 正文里 `#N` 楼层引用被点时的回调（[MiniMarkdown] 的 `onFloorRef`）。
     *
     * **两个手势、两个目标，别混**：
     *  - 点**这张卡**（[onJump]）= 跳到 `parent` 自己那一层（`floor`）；
     *  - 点正文里的 `#N`（本参数）= 跳到**那句话指到的那一层**，可能是链上别的楼、也可能压根
     *    不在链上（父帖正文里随手引用的第 7 楼）。
     *
     * 两者不冲突的原因：`#N` 是正文文字上的 `LinkAnnotation.Clickable`（`MiniMarkdown.kt` 里
     * `appendMarked` 造出来的），点击由文本自己的链接处理器消费；卡片的 `clickable` 挂在**祖先**
     * `Column` 上，Compose 的 `clickable` 在 up 事件已被消费时不会触发。同一张卡里 URL 链接与
     * "点整卡"早就这么共存着，这里只是把 `#N` 也做成同一类链接。
     *
     * 默认 null：`MiniMarkdown` 对 null 短路，`#N` 还是普通文字 —— 与改动前逐项一致。
     */
    onFloorRef: ((Int) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val name = parent.author.ifBlank { "匿名" }
    // 只在正文变了时重算：一次刷新不该把每张卡都重数一遍行。
    val long = remember(parent.body) { isLongReplyCardBody(parent.body) }
    val foldHeight = replyHistoryCardFoldHeight(LocalDensity.current)
    val jumpFn = onJump
    // 先落成局部 val 再判空（同 [NestedRepliesList] 里那段注释：直接抓 `onJump` 要靠 smart-cast）。
    val jump: (() -> Unit)? = if (jumpFn != null && floor >= 2) {
        { jumpFn(parent.id) }
    } else {
        null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceContainerHighest.copy(alpha = 0.3f))
            .then(
                if (jump != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "跳到第 $floor 楼",
                        onClick = jump,
                    )
                } else {
                    Modifier
                },
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Reply,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = scheme.primary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            PostAvatar(
                name = name,
                seed = parent.authorId.ifEmpty { parent.author },
                size = 20.dp,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 名字吃满剩余宽度：`#N` 因此恒贴右边，几张卡的数字在同一列上，扫读时不用找。
                modifier = Modifier.weight(1f),
            )
            if (floor >= 2) {
                Text(
                    text = "#$floor",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (parent.body.isBlank()) {
            Text(
                text = "（没有正文）",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        } else {
            val bodyText: @Composable () -> Unit = {
                MiniMarkdown(
                    text = parent.body,
                    baseStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = REPLY_HISTORY_CARD_LINE_HEIGHT,
                    ),
                    color = scheme.onSurfaceVariant,
                    // 父卡正文里的 `#N`：点号跳那一层，点卡跳这一层（语义差别见本函数参数表）。
                    onFloorRef = onFloorRef,
                )
            }
            // 与楼层正文同一条原则：折叠作用在**容器**上，正文本身始终是完整的一份（截半截
            // Markdown 会在表格/代码块中间切出残片）。
            if (!long || unfolded) {
                bodyText()
                if (long) ReplyCardFoldToggle(label = "收起", onClick = onToggleUnfold)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = foldHeight)
                        // 只裁不渐变：高度是行高的整数倍，切口落在行间，本来就没有"半截字"要淡出；
                        // 再叠一层渐变还要知道卡片底色（α 叠加过的），等于把主题色抄一份进来。
                        .clipToBounds(),
                ) {
                    bodyText()
                }
                ReplyCardFoldToggle(label = "展开", onClick = onToggleUnfold)
            }
        }
    }
}

/**
 * 父卡正文末尾那颗「展开 / 收起」。
 *
 * 不用 [FoldBodyToggle]：那颗是 `TextButton`（M3 最小触控高度 40dp 起），垫在一张 3 行小卡里
 * 会凭空多出近 40dp，三张卡就是一截空屏；这里要的只是"一行点得着的字"。
 * 也不复用它的折叠语义 —— 两套折叠的阈值、容器、状态各自独立，共用一个按钮只会让人以为它们
 * 是同一个开关。
 *
 * 整行铺满 + `clickable`：点哪都算，手指不用瞄准两个字。
 */
@Composable
private fun ReplyCardFoldToggle(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "展开或收起引用的原文",
                onClick = onClick,
            )
            .padding(top = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * 一条举报理由：稳定键 + 标题 + 一句说明。
 *
 * [key] 不进界面，只留在代码里当标识（真正的落点是剪贴板里那段人话）；界面只画 [title] 与
 * [description] 两层，与 fluxdo 的 `_buildFlagOption`（`post_flag_sheet.dart:223-292`）同一个结构：
 * 上面一行标题、下面一行灰字说明。
 */
private class FloorReportReason(
    val key: String,
    val title: String,
    val description: String,
)

/**
 * 举报理由清单：分类与文案照参考实现。
 *
 *  - 来源一：`lib/widgets/post/post_item/widgets/post_flag_sheet.dart:41-44` 把清单分成两段
 *    （「向 @用户 发送消息」= `notify_user`，与「私下通知管理人员」= 其余）；
 *  - 来源二：`lib/models/topic.dart:2871-2905` 的 `FlagType.defaultTypes` 是那四条的**兜底**
 *    定义（`off_topic` / `inappropriate` / `spam` / `notify_moderators`，position 1..4）；
 *  - 来源三：中文文案在 `lib/l10n/modules/topic/topic_zh.arb:244-251`。
 *
 * 只搬「通知管理人员」那一段：本仓没有私信通道（`PostStore` 连举报都没有），"向作者发送消息"
 * 那几条摆出来就是点下去没有任何落点的选项 —— 宁可少一段，也不给一颗假按钮。
 * 顺序按源码的 `position`，`other`（= `notify_moderators`）照旧在最后。
 */
private val FLOOR_REPORT_REASONS = listOf(
    FloorReportReason("off_topic", "离题", "此帖与当前讨论无关，应该移到其他话题"),
    FloorReportReason("inappropriate", "不当内容", "此帖包含不适当的内容"),
    FloorReportReason("spam", "垃圾信息", "此帖是广告或垃圾信息"),
    FloorReportReason("other", "其他问题", "需要管理人员关注的其他问题"),
)

/**
 * 举报面板正对着的那一层：楼层本体 + 楼层号。
 *
 * 楼层号在这里存一份（而不是画面板时再扫 `comments`）：与卡片上的 `#N`、菜单抬头、复制出来的
 * `#N` 都是调用点那一个 `index + 2`，本文件反复强调"楼层号只该有一处算法"。
 */
private class FloorReportTarget(val comment: PostComment, val floor: Int)

/**
 * 楼层举报面板：贴底、自绘。
 *
 * 骨架照同文件的 [ComposerPickerSheet]（`BasicAlertDialog` + 整屏遮罩 + 贴底 `Surface` +
 * 抓手/关闭 + 内容区自己滚），**没有**用 `ModalBottomSheet`：本仓别处的面板已经踩过
 * "sheet 抢手势、与列表滚动打架"的坑，贴底 Surface 这条路一直稳。
 *
 * 内容照 `post_flag_sheet.dart:119-208`：标题写清**操作对象**（`#N · 作者`，同 `:127-138`），
 * 一段一条的单选列表，底部一颗主按钮。差别只有一处，而且是刻意的：**提交不是发请求**。
 * 本仓后端没有举报 RPC，所以这里老老实实写一行「当前版本没有在线举报通道」，
 * 提交 = 把整理好的文本复制走（见 [reportableFloor]），toast 也说同一件事。
 * 一个空请求、一句"举报成功"都比这行小字更糟：那是在骗点按钮的人。
 *
 * 空理由时「提交」是灰的：复制一段没有理由的举报等于什么也没说。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloorReportSheet(
    /** 这一层的楼层号（`#N`）。 */
    floorLabel: String,
    author: String,
    onDismiss: () -> Unit,
    /** 选中理由的**标题**（「不当内容」这种），由调用点拼进剪贴板文本。 */
    onSubmit: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var picked by remember { mutableStateOf<FloorReportReason?>(null) }
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        // 整宽：面板里每一行都是"整宽一行都可点"的候选，平台默认宽度会让它缩成中间一条。
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 遮罩整屏铺满（理由见 [ComposerPickerSheet] 里那段注释：按面板高度画会留下死区）。
            // 面板自己是不透明 `Surface`，天然挡点击，所以遮罩铺满不会误关。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = FlSurfaceLevel.Block.colorOf(scheme),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 导航栏留白垫在面板上：最后那排按钮不垫会被手势条压住。
                        .navigationBarsPadding(),
                ) {
                    // 抓手 + 关闭：与 [ComposerPickerSheet] 同一个尺寸（40×4、`onSurfaceVariant α0.3`）。
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(width = 40.dp, height = 4.dp)
                                .background(scheme.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(50)),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable(role = Role.Button, onClickLabel = "关闭", onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Close, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                        }
                    }
                    // 标题行：红旗下 + 「举报 #N · 作者」（照 `post_flag_sheet.dart:127-138`：
                    // 图标吃 `error`，标题把这个面板在操作哪一层写在脸上）。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Flag, null, Modifier.size(20.dp), tint = scheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "举报 $floorLabel · ${author.ifBlank { "匿名" }}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 「提交其实是复制」必须写在这里、用正常的字号读得见：这是这个面板唯一一处
                    // 可能与预期不符的地方，藏进 toast 里太晚（人已经点完了）。
                    Text(
                        text = "当前版本没有在线举报通道，提交只是把这条举报复制下来，需要你发到反馈群。",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
                    )
                    // 理由列表：四条其实一屏就放得下，`heightIn` + 滚动是给"以后加理由 / 字号很大"
                    // 留的余量，免得小屏上一颗按钮被挤出屏幕。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        FLOOR_REPORT_REASONS.forEach { reason ->
                            ReportReasonRow(
                                reason = reason,
                                picked = picked === reason,
                                onClick = { picked = reason },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 「取消」与标题右边那颗 X 是同一个出口，多给一颗文字按钮是因为这一行
                        // 已经有「提交」了：只给 X 时人得先找一次出口。
                        TextButton(onClick = onDismiss) {
                            Text("取消", color = scheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { picked?.let { reason -> onSubmit(reason.title) } },
                            // 没选理由就点不动：见这个函数上面的注释。
                            enabled = picked != null,
                        ) {
                            Text("提交")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 举报面板里的一行理由：单选点 + 标题 + 一句说明。
 *
 * 单选点自己画（两个 `drawCircle`），不引 M3 的 `RadioButton`：那个自带 48dp 触控区与禁用色，
 * 套进这一行就是两层点击区、两种灰。形状沿用 [PickerOptionRow] 的圆角与 48dp 行高，
 * 从别的面板点过来手指还是那根手指。
 */
@Composable
private fun ReportReasonRow(
    reason: FloorReportReason,
    picked: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.RadioButton, onClickLabel = reason.title, onClick = onClick)
            // 最小 48dp 放在 padding **之前**：这样那一行算上内边距正好 48（与菜单项、胶囊
            // 同一个触控档），写在 padding 之后会变成 48 + 12 = 60，四条理由就凭空高出 48dp。
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .drawBehind {
                    drawCircle(
                        color = if (picked) scheme.primary else scheme.outlineVariant,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    if (picked) {
                        // 实心内点：半径收 4dp，读作"选中的那一颗"。
                        drawCircle(color = scheme.primary, radius = size.minDimension / 2f - 4.dp.toPx())
                    }
                },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reason.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                color = if (picked) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reason.description,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 一层楼底下那串「直接回复」，照 fluxdo `embedded_posts.dart:156-377` 的
 * `EmbeddedReplyMetrics` + `EmbeddedReplyTile` + `EmbeddedReplyCollapseButton`：
 *
 * ```
 * gutter          = 40   // 头像列宽 = 父楼头像直径（所以竖线正好落在父楼头像的中心线上）
 * gap             = 12   // 头像列到文字
 * avatarRadius    = 14   // → 28dp 头像
 * railCenter      = 20   // gutter / 2
 * railWidth       = 1.5
 * railGap         = 3    // 竖线在头像上下断开的那点缝
 * verticalPadding = 10   // 每条自己的上下内边距
 * buttonSize      = 28   // 收起按钮与回复头像同尺寸，读作这串头像的收尾
 * ```
 *
 * 竖线**每条自己画**：头像上方一段、下方一段，与头像之间留 3dp。首条不画上方那段
 * （线从第一个头像起），末条下段在底部收 3dp 留给收起按钮 —— 相邻条目首尾相接，
 * 拼成"只在头像处断开的一根线"。画在 `padding` **之前**，这样 `size` 含上下 10dp，
 * 头像顶边正好落在 10dp 处，与源码的 `avatarTop = verticalPadding` 一致。
 *
 * 正文 13sp / 1.4（=18sp）而不是楼层正文的 14/21：嵌套内容要能被一眼读成"引用来的次级内容"，
 * 同字号会让整段看起来像多了一层楼。
 */
@Composable
private fun NestedRepliesList(
    replies: List<PostComment>,
    floorLabelOf: (Long) -> Int,
    onJump: ((Long) -> Unit)?,
    onCollapse: () -> Unit,
    /**
     * 楼内嵌套回复**正文里** `#N` 楼层引用被点时的回调（[MiniMarkdown] 的 `onFloorRef`）。
     *
     * 与 [onJump] 的分工，和 [ReplyHistoryList] 那边完全一致：点**整行**（[onJump]）= 跳到这
     * 一条回复自己的楼层；点正文里的 `#N`（本参数）= 跳到那句正文指到的那一层。
     * 两条手势互不冲突：`#N` 是文字节点上的 `LinkAnnotation.Clickable`，它先消费掉那一下，
     * 外层 `Row` 的 `clickable` 就不会再触发。
     *
     * 默认 null：`MiniMarkdown` 对 null 短路，`#N` 保持普通文字（输出与改动前逐项一致）。
     */
    onFloorRef: ((Int) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        replies.forEachIndexed { index, reply ->
            val floor = floorLabelOf(reply.id)
            val first = index == 0
            val last = index == replies.size - 1
            // 先落成局部 val 再判空：直接写 `if (onJump != null) { { onJump(id) } }` 时，
            // 捕获里那个 `onJump` 要靠 smart-cast 才非空，读的人（和某些编译器版本）会在这里打结。
            val jumpFn = onJump
            val jump: (() -> Unit)? = if (jumpFn != null && floor >= 2) {
                { jumpFn(reply.id) }
            } else {
                null
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val railWidth = NESTED_RAIL.toPx()
                        val left = NESTED_RAIL_CENTER.toPx() - railWidth / 2f
                        val avatarTop = NESTED_TILE_PAD.toPx()
                        val avatarBottom = avatarTop + NESTED_AVATAR.toPx()
                        val gap = NESTED_RAIL_GAP.toPx()
                        if (!first) {
                            drawRect(
                                color = scheme.outlineVariant,
                                topLeft = Offset(left, 0f),
                                size = Size(railWidth, (avatarTop - gap).coerceAtLeast(0f)),
                            )
                        }
                        val bottom = if (last) size.height - gap else size.height
                        val from = avatarBottom + gap
                        if (bottom > from) {
                            drawRect(
                                color = scheme.outlineVariant,
                                topLeft = Offset(left, from),
                                size = Size(railWidth, bottom - from),
                            )
                        }
                    }
                    .then(
                        if (jump != null) {
                            Modifier.clickable(
                                role = Role.Button,
                                onClickLabel = "跳到第 $floor 楼",
                                onClick = jump,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = NESTED_TILE_PAD),
                verticalAlignment = Alignment.Top,
            ) {
                // 头像列：宽度固定 40，头像 28 居中 —— 竖线（画在 x=20）正好从它中间穿过。
                Box(
                    modifier = Modifier.width(NESTED_GUTTER),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    PostAvatar(
                        name = reply.author.ifBlank { "匿" },
                        seed = reply.authorId.ifEmpty { reply.author },
                        size = NESTED_AVATAR,
                    )
                }
                Spacer(modifier = Modifier.width(NESTED_GAP))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reply.author.ifBlank { "匿名" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        // 楼层号：**`#N` 与主列表同一套编号**（同一个 `floorLabelOf`），
                        // 点一下就到了那一层，所以这里只写号不重复名字。
                        if (floor >= 2) {
                            Text(
                                text = "#$floor",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (reply.body.isBlank()) {
                        Text(
                            text = "（没有正文）",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        MiniMarkdown(
                            text = reply.body,
                            baseStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = NESTED_FONT,
                                lineHeight = NESTED_LINE,
                            ),
                            // 次级内容压一档：与源码里嵌入帖渲染用 `onSurfaceVariant` 同一个意思。
                            color = scheme.onSurfaceVariant,
                            // 嵌套楼正文里的 `#N`：点号跳那一层，点整行跳这一层
                            //（两个手势为什么不会互相打架，见本函数参数表）。
                            onFloorRef = onFloorRef,
                        )
                    }
                }
            }
        }
        // 收起按钮：28dp 正圆 + 1dp 竖线色描边，左移 (railCenter - buttonSize/2) = 6dp，
        // 于是它自己也落在竖线上（`embedded_posts.dart:368-374`）。
        Box(
            modifier = Modifier
                .padding(
                    start = NESTED_RAIL_CENTER - NESTED_BUTTON / 2,
                    top = 2.dp,
                    bottom = 2.dp,
                )
                .size(NESTED_BUTTON)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(
                        color = scheme.outlineVariant,
                        radius = size.minDimension / 2f - 0.5.dp.toPx(),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                .clickable(role = Role.Button, onClickLabel = "收起回复", onClick = onCollapse),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 吸底回复栏：**不是输入框，是一条 56dp 高的操作行**，左边一颗「假输入框」胶囊。
 *
 * 照 fluxdo `topic_bottom_bar.dart`：
 * - 行高 `contentHeight = 56`（`:24`），水平内边距 `12`（`:136`）
 * - 胶囊：`surfaceContainerHighest` 底 + 全圆角（`radius(9999)`，`:455`），
 *   **高 40**、水平内边距 `14`（`:354`）、`edit_rounded` 图标 **18dp** + 8dp 间距、文案 `bodyMedium`
 * - 文案随状态变：没登录 / 已关闭 / 正常时的「写下你的回复…」/ 有草稿时的摘要（`:358-385`）
 *
 * 为什么不再是一行 `OutlinedTextField`：真输入框贴在最底下，只要一出现就整个被键盘顶走、
 * 或者被键盘盖住，而且列表只剩半屏。fluxdo 的做法是"点一下才升起面板"，读帖时不占高度。
 */
@Composable
private fun ReplyBar(
    reply: String,
    busy: Boolean,
    locked: Boolean,
    enabled: Boolean,
    /**
     * 这一次要挂到哪一楼下面（`楼层号 to 名字`）；null 表示普通回帖。
     *
     * 楼层号可能是空串（那一楼在列表里已经找不到了，比如刚被删），这时只写名字，不编一个号。
     */
    replyTo: Pair<String, String>? = null,
    /**
     * 面板开着没有。**状态提到上层**（`PostThreadPane`）而不是留在本函数里：
     * 动作行左边那颗「N 条回复」胶囊也要能把它打开（fluxdo 的 `_buildRepliesButton`
     * 就是动作行左侧那颗），两处各留一份 `remember` 会出现"点了胶囊、面板没开"。
     */
    open: Boolean = false,
    onOpenChange: (Boolean) -> Unit = {},
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 关着时这一栏只有那颗胶囊；开着才画遮罩与面板（`composerOpen` 来自上层，见上）。

    // 这条底栏**不再吃 `imePadding`**：它里面已经没有真输入框了（只在点开的面板里有），
// 键盘弹起时它不需要让位；垫了的话面板开着的那段时间，底栏会被顶起一条空白，
// 面板底部到键盘之间就多出一道缝。
    Surface(color = FlSurfaceLevel.Block.colorOf(scheme)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 56dp 高 + 左右 12，照 `contentHeight` / `_horizontalPadding`。
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    // 胶囊高 40：不给固定高的话它会被父 Row 拉到 56，看着像一块板。
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainerHighest)
                    .clickable(
                        enabled = enabled && !locked,
                        role = Role.Button,
                        onClickLabel = "回复",
                        onClick = { onOpenChange(true) },
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = if (enabled && !locked) scheme.onSurfaceVariant else scheme.onSurfaceVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.width(8.dp))
                // 有草稿时的文案分两段上色，照 `topic_bottom_bar.dart:396-413`：
                // 「草稿」两个字吃强调色 + w600，正文摘要吃 `onSurface`（比胶囊常态的
                // `onSurfaceVariant` 亮一档，一眼能看出这行是**我写过的内容**而不是提示语）。
                // 上一版是同一色同一字重的「草稿：xxx」，摘要读起来和提示语一样，分不出真假。
                val draftHead = if (!locked && enabled && replyTo == null && reply.isNotBlank()) {
                    // 摘要取**第一个非空行**：只取 `firstOrNull()` 时，正文以一个空行开头就会
                    // 显示成"草稿"后面什么都没有，看着像把草稿弄丢了。
                    reply.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: reply.trim()
                } else {
                    null
                }
                val message = if (draftHead != null) {
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = scheme.primary, fontWeight = FontWeight.SemiBold)) {
                            // 参考里是两个空格（`'${l10n.drafts_draft}  '`），不是冒号。
                            append("草稿  ")
                        }
                        withStyle(SpanStyle(color = scheme.onSurface)) { append(draftHead.take(30)) }
                    }
                } else {
                    buildAnnotatedString {
                        append(
                            when {
                                locked -> "话题已关闭"
                                !enabled -> "登录后参与讨论"
                                // 引用回复优先于草稿摘要：这一句说明"这条要挂到哪一楼下面"，
                                // 比摘要更该先说。引用状态和草稿是分开的两件事（人格化一点说：
                                // 引用了哪一楼，跟正文写了几个字无关；正文可以一个字没写就
                                // 已经选好了要回的那一楼）。
                                replyTo != null -> {
                                    val (floor, name) = replyTo
                                    if (floor.isEmpty()) "回复 $name" else "回复 #$floor · $name"
                                }
                                else -> "写下你的回复…"
                            },
                        )
                    }
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled && !locked) {
                        scheme.onSurfaceVariant
                    } else {
                        scheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    // 面板：整页压暗 + 从底部升起的输入区（`topic_quick_reply.dart`）。
    if (open) {
        ReplyComposerPanel(
            reply = reply,
            busy = busy,
            onChange = onChange,
            onSend = {
                onSend()
                onOpenChange(false)
            },
            onDismiss = { onOpenChange(false) },
        )
    }
}

/**
 * 回复面板，照 fluxdo `topic_quick_reply.dart`：
 *
 * - 圆角 **24**（`_panelRadius = 24`，`:84`），贴底时**上圆下平**（`:426-427`）
 * - 遮罩 **0.32** 黑（`:341` 的 `0.32 * raw`）
 * - 面板底 **`surfaceContainerLow`**、输入框 **`surfaceContainerHighest`**（`:432-436` / `:520-526`）
 * - 展开 **340ms** / 收起 **260ms**，曲线 `easeInOutCubicEmphasized`（`:85-91`）——
 *   本仓走的是 `Dialog` 的默认进出动画，**没有**做参考那套"从回复胶囊长出面板"的形变
 *   （`_MorphClipper` + 截图接替，`:382-460`）：那要自绘一层覆盖在整页上的动画容器，
 *   与本文件里"面板是 Dialog"这条实现绑在一起，不在这一处改。
 * - 输入框：圆角 **16**、`minLines 3 / maxLines 8`、
 *   内边距 **14**、`bodyLarge`（`:524-546`）
 * - 面板内边距 LTRB(16, 16, 12, 8)（`:473-483`）
 * - 发送：`FilledButton(minimumSize = Size(72, 38))`，文案「发送」，忙碌时 16dp 转圈（`:602-618`）
 */
@Composable
private fun ReplyComposerPanel(
    reply: String,
    busy: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // 关闭只交给下面那块遮罩：Dialog 自己的"点外面也关"在 `usePlatformDefaultWidth =
            // false` 时把整个窗口当"外面"，判定含糊，留着会偶发"点面板却关掉"。
            dismissOnClickOutside = false,
        ),
    ) {
        // 整个对话框内容 = 一块透明的全屏层：上面是遮罩（点它收起），下面贴着面板。
        // `imePadding` 在这一层：键盘弹起时**整个对话区**缩小，面板跟着被顶到键盘上沿。
        // 放在面板那一层只会在面板内部加出空白，输入区照样被键盘盖住。
        Box(modifier = Modifier.fillMaxSize().imePadding()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 上圆下平：`RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)`。
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    // 面板底色是 `surfaceContainerLow`，**不是** `surfaceContainerHighest`：
                    // 参考的面板 Material 底色是 `Color.lerp(surfaceContainerHighest,
                    // surfaceContainerLow, t)` 的终点（`topic_quick_reply.dart:432-436`）。
                    // 上一版把面板和输入框的两档底色写反了（面板 Highest、输入框 Low），
                    // 结果输入区是"亮面板里挖一个暗坑"，而参考是"暗面板上浮一个亮输入框"。
                    .background(scheme.surfaceContainerLow),
            ) {
                // 输入框：圆角 16、底色**比面板高一档**、内边距 14、3~8 行。
                OutlinedTextField(
                    value = reply,
                    onValueChange = onChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        // 输入框 **`surfaceContainerHighest`**（`topic_quick_reply.dart:520-526`
                        // 的 `ShapeDecoration(color: surfaceContainerHighest, radius 16)`）：
                        // 比面板那一档亮，输入区因此"浮"在面板上 —— 与上面的面板底色是**一对**，
                        // 改其中任何一个都要同时看另一个。
                        .background(scheme.surfaceContainerHighest),
                    minLines = 3,
                    maxLines = 8,
                    placeholder = { Text("写下你的回复…") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                )
                // 底部一行：右边一颗「发送」。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            // `minimumSize = Size(72, 38)`
                            .heightIn(min = 38.dp)
                            .widthIn(min = 72.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (reply.isBlank() || busy) scheme.surfaceVariant else scheme.primary)
                            .clickable(
                                enabled = reply.isNotBlank() && !busy,
                                role = Role.Button,
                                onClickLabel = "发送",
                                onClick = onSend,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = scheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = "发送",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (reply.isBlank()) scheme.onSurfaceVariant else scheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 「创建话题」页，布局照 fluxdo 的 `create_topic_page.dart`：
 *
 * ```
 * 顶栏：返回 + 标题 + [预览] + 圆形纸飞机发送（ComposerSubmitButton，40dp 正圆）
 * 可滚内容：
 *   标题（无边框、headlineSmall / w600、提示文字 onSurfaceVariant 50%）+ 右下字数角标
 *   间距 16
 *   正文（无边框、bodyLarge、提示「正文内容（支持 Markdown）…」）+ 右下字数角标
 * 底部（常驻吸底）：
 *   属性条：[选择分类 ⌄] [+ 添加标签] [分享方案] —— 胶囊
 *   工具栏：[B] [I] [H] [列表] [引用] [代码] [链接] —— 图标 16dp、触控 48dp
 * ```
 *
 * 为什么把分类/标签挪到底部：写作时手在正文上，属性条是"发之前核对一眼"的东西；放在标题
 * 下面会把它和正文隔开，一屏里出现两条横带。fluxdo 的 `ComposerMetaBar` 就是常驻底部。
 *
 * @param onInsertShare 点「分享方案」时调用；null 时不画那一格。
 */
/**
 * 顶栏那颗圆形发送（照 fluxdo 的 `ComposerSubmitButton`）：40dp 正圆、图标 21dp、
 * 外面套 48dp 触控框、`padding = 0`；忙碌时圆里滚一个 18dp 的进度圈，不再是文字「正在发…」。
 *
 * 为什么是正圆而不是一颗文字按钮：写完之后整个屏幕只有这一个"结束"动作，圆形把它从
 * 顶栏的一排文字里拎出来；而且它不随标题长短改变宽度，位置永远在同一个角上。
 */
/**
 * 顶栏那颗**预览**开关：44×44 的圆图标按钮，图标 21dp。
 *
 * 数值照 fluxdo 的 `ComposerActionButton`（`composer_page_chrome.dart:27-48`）与
 * `ComposerPreviewButton`（`composer_view_mode_switcher.dart:41-58`）：
 *
 *  - 触控框 `BoxConstraints.tightFor(width: 44, height: 44)`、内边距 10、图标 21
 *  - 图标 `openBook` ↔ 预览中 `edit`；预览中染 `primary`（`color: previewing ? primary : null`）
 *  - 文案只进 tooltip：`ComposerActionButton` 的 `tooltip: label`，不在按钮上占宽度
 *
 * 为什么不用 `FlBarButton`：那个默认是 44dp 的**实心圆底**（`drawCircle` 画一层
 * `Page.raised`），而这里在顶栏上、旁边就是一颗 36dp 的实心 primary 提交圆 ——
 * 两个实心圆并排会把顶栏压得很重。fluxdo 这一颗是"裸图标 + 44dp 触控框"，所以这里自己画
 * 那一层：`filled = false` 的写法在 `FlBarButton` 里也要能拿，但它还带压感缩放与
 * `minimumInteractiveComponentSize()`（那会把 44 撑到 48，破坏顶栏那一行的对齐）。
 *
 * 预览与"源码/富文本"是两件事（`composer_view_mode_switcher.dart:40` 的注释：
 * 「预览是独立开关，退出时不改变富文本/源码模式」）。本仓没有富文本模式，
 * 所以只保留预览这一颗，另一颗不做。
 */
@Composable
private fun ComposerPreviewButton(previewing: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = if (previewing) "退出预览" else "点击查看预览"
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (previewing) Icons.Rounded.Edit else Icons.Rounded.Visibility,
            contentDescription = label,
            modifier = Modifier.size(21.dp),
            // 预览中染 primary：一眼看得出"现在看到的是渲染后的样子"，
            // 而不是以为自己还在编辑、敲了字却没反应。
            tint = if (previewing) scheme.primary else scheme.onSurface,
        )
    }
}

/**
 * 顶栏的「更多」：**草稿状态唯一的去处**。
 *
 * 照 fluxdo `composer_draft_status.dart`：草稿状态不在顶栏常显，而是菜单里的一项
 * （`ComposerDraftMenuEntry` 的 `height => 48`、图标 21dp、图标与文字间距 12dp、
 * 文字走 `bodyMedium`）。
 *
 * 为什么抽屉挪进来：原来那行「已自动保存 · HH:mm」常驻正文顶上，占掉 10 + 13 + 6 = 29dp，
 * 正文区就少一整行多的高度。而这条信息**写的时候并不需要一直看**——要看的时候点一下
 * 「更多」就有。
 *
 * **红点（失败态）没做**：[PostDraftStore.save] 是同步写进内存、然后丢给一个后台协程
 * `runCatching { storage.write(...) }` 落盘 —— 写失败被 `runCatching` 吃掉了，调用方拿不到
 * 任何失败信号，也没有"保存中"这一档（`save` 返回时内存里已经是新的一份）。所以本仓只有
 * **两档**：「草稿自动保存 · HH:mm」（存过）与「草稿自动保存」（还没存过）。
 * fluxdo 的 `DraftSaveStatus.error` / `.saving` 与那颗 6dp 红点（`ComposerDraftAttention`）
 * 在这里没有对应的事实可读，凭空画一个红点等于骗人 —— 要做的话得先让 `save` 返回结果，
 * 那是另一件事。
 */
@Composable
private fun ComposerDraftMenuButton(savedAt: Long) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        TopBarIconButton(
            icon = Icons.Rounded.MoreVert,
            label = "更多",
            onClick = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ComposerDraftMenuRow(savedAt = savedAt)
        }
    }
}

/**
 * 菜单里那行草稿状态：图标 21dp + 12dp + 一行字，整行高 48dp、点不点都一样。
 *
 * 文案照 fluxdo 的 `composer_draftAutoSave`（`editor_zh.arb:27` = **「草稿自动保存」**）
 * 后面接上本仓的 `HH:mm`（[draftClock]）—— 源码那句只有"自动保存"这件事本身，
 * 本仓多给一个时刻，因为人在菜单里点开它多半是想确认"我刚才那一段进去了没有"。
 *
 * 不用 `DropdownMenuItem`：它的默认图标是 24dp、行高与内边距走 M3 默认，
 * 而 fluxdo 这里写死 48 / 21 / 12。自己排一行，数字直接对得上。
 *
 * 不设 `enabled = false` 的禁用样式：这一项不是动作（本仓没有"重试保存"），
 * 但**没点击行为**的菜单项要看起来像"一条说明"。给 `clickable` 会让人以为点一下会重存。
 */
@Composable
private fun ComposerDraftMenuRow(savedAt: Long) {
    val scheme = MaterialTheme.colorScheme
    val clock = draftClock(savedAt)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 菜单项最小 48dp（fluxdo 的 `height => 48`）；不给的话中文字的行高会把这一项
            // 压到 40 出头，和上面那些 `MenuRow` 高度对不齐。
            .heightIn(min = 48.dp)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 云朵：存过用 `Icons.Rounded.CloudDownload`，没存过用 `Icons.Rounded.Drafts`。
        // 本仓没有 `cloud_done` / `cloud_upload` / `cloud_off` 三种云，所以只按"存过没有"
        // 分两个图标，不假装有第三档。
        Icon(if (clock.isNotEmpty()) Icons.Rounded.CloudDownload else Icons.Rounded.Drafts, null, Modifier.size(21.dp), tint = scheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (clock.isNotEmpty()) "草稿自动保存 · $clock" else "草稿自动保存",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PublishCircleButton(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (enabled || busy) scheme.primary
                    else scheme.surfaceVariant,
                )
                .clickable(
                    enabled = enabled && !busy,
                    role = Role.Button,
                    onClickLabel = "发送",
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = scheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                // 与详情页吸底回复栏那颗发送同一个图标（`ReplyBar` 用的是 `Icons.AutoMirrored.Rounded.Send`）：
                // 同一个页面里两个"发送"不能一个 Material 纸飞机、一个自绘纸飞机。
                Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(21.dp), tint = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PostEditorPane(
    title: String,
    /** 正文带游标：工具栏那几颗 Markdown 按钮要插在"正在写的地方"，见 [insertMarkdown]。 */
    body: TextFieldValue,
    category: String,
    tags: List<String>,
    tagInput: String,
    error: String?,
    previewing: Boolean,
    onTitleChange: (String) -> Unit,
    onBodyChange: (TextFieldValue) -> Unit,
    onCategoryChange: (String) -> Unit,
    onTagInputChange: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    /** 「标签」面板点「完成」：整份新列表一起写回（见 [ComposerPickerSheet]）。 */
    onTagsApply: (List<String>) -> Unit,
    onInsertShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // 分类与标签都**点开一个底部面板**（[ComposerPickerSheet]），照 fluxdo
    // `topic_editor_helpers.dart:653-683` 的 `showAppBottomSheet`：
    //   - 分类：一列候选 + 第一项「不选分类」，当前那个打勾（`CategorySelectionSheet`）；
    //   - 标签：已选 chip（点一下删）+ 一格输入，最多 [TAG_MAX] 个（`TagSelectionSheet`）。
    //
    // 面板是这一层画的、不是 `PostsScreen`：它只影响这一屏的排版。位置（`Column` 最后）见
    // 下面的说明 —— 必须在正文那块 `weight(1f)` **后面**，否则它在布局上会先占掉高度。
    //
    // 几个开合都放在这里（而不是提到 PostsScreen）：它们只影响这一屏的排版，
    // 页面级状态里已经有十几个了，再塞几个会让"编辑器状态"那段更难读。
    var categorySheetOpen by remember { mutableStateOf(false) }
    var tagSheetOpen by remember { mutableStateOf(false) }
    /**
     * 标签面板里**草稿式的选择**：确认才写回外面的 [tags]。
     *
     * fluxdo 的 `TagSelectionSheet` 是一个 `showAppBottomSheet<List<String>>`，结果由弹层的
     * `Navigator.pop` 带回来 —— 也就是说面板里的增删在**关掉之前**不影响正文。这里照做：
     * 打开时从 [tags] 拷一份进来，点「完成」才 `onTagsChanged`。不这样做的话，面板里点错一个
     * 标签、随手关掉，已经改了外面；而外面那个 chip 行这时在面板底下，看不见。
     */
    var tagDraft by remember { mutableStateOf<List<String>>(emptyList()) }
    var tagField by remember { mutableStateOf("") }
    /**
     * 标签面板里那一句错误（单个标签太长 / 已满 5 个）。
     *
     * 不调 `platform.showMessage`：面板盖在编辑器上，toast 从底下弹出来会被面板压住一半；
     * 而这句话说的是"你刚敲的这一个没进去"，摆在那格输入框下面才是它该在的位置。
     */
    var pendingTagError by remember { mutableStateOf<String?>(null) }
    // 面板开没开。与上面两个一样放在这一层：它只影响这一屏的排版。
    var toolsPanelOpen by remember { mutableStateOf(false) }
    // 点了面板里的一格之后统一走这里：
    //   - `Insert`：照旧走 [insertMarkdown]（插在游标处 / 包住选中那一段）；
    //   - `Clear`：**真的清空正文**（不是"插一段空模板"），游标回到开头；
    //   - 成功了就收起面板：面板占的是正文的高度，插完还开着，人就看不到自己刚插的东西。
    //     状态没变（正文已到上限 / 正文本来就是空的）则**不关**，那一格自然看起来"没反应"，
    //     但面板留在原地，人还能换一颗。
    //
    // 清空不弹确认框：正文还有自动存的草稿兜底（`AUTOSAVE_DELAY_MS` 那套），
    // 而"点了一颗工具却什么都没发生"才是这一版要消灭的东西。
    val applyTool: (EditorTool) -> Unit = { tool ->
        when (tool.action) {
            // 照旧走 [insertMarkdown]：插在游标处 / 包住选中那一段。状态没变时它返回同一个
            // 对象，等于什么都不做 —— 那种情况不关面板，让人看得见"这一下没插进去"。
            EditorToolAction.Insert -> {
                val next = insertMarkdown(body, tool.snippet, POST_BODY_LIMIT)
                if (next !== body) {
                    onBodyChange(next)
                    toolsPanelOpen = false
                }
            }
            // 正文本来就是空的：没有"清空"可言，面板也不关（同上，别让人以为点了没反应）。
            EditorToolAction.Clear -> {
                if (body.text.isNotEmpty()) {
                    onBodyChange(TextFieldValue("", selection = TextRange(0)))
                    toolsPanelOpen = false
                }
            }
        }
    }

    // 无边框输入：标题与正文都当"一页纸"写，不套输入框的壳。
    val plainField = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

    // 外层吃 IME：正文靠 `weight(1f)` 撑满，键盘一弹可用的高度就变少，
// 不在这一层垫 inset，正文会被键盘压到只剩一行。底部属性条那层就**不再**垫，避免垫两次。
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        // 布局照 fluxdo `create_topic_page.dart`：**标题区固定高、正文 `expands: true` 撑满**
        // 剩下所有高度（`markdown_editor.dart:917` —— `expands` 为真时 `minLines` 是 null，
        // 正文自己滚动，不是整页滚动）。
        //
        // 上一版是「整页 `verticalScroll` + 正文 `minLines = 14`」：正文只有 14 行高，
        // 再往下写就整页滚，标题跟着滚出屏幕 —— 写到第三屏时看不见"我给这条帖起了什么名"。
        Column(
            modifier = Modifier
                // 这一层的 `weight(1f)` 是必需的：它里面的正文也在用 `weight(1f)`，
                // 而 `weight` 只在父级**高度有界**时才有意义。这层不给 weight 的话，
                // 它在父 Column 里是 wrap 高度（无界），正文的 weight 就退化成"按内容量"，
                // 正文框只剩一行高 —— 正好和"撑满"这个目标相反。
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            // 顶上那行「已自动保存 · HH:mm」/「还没存过草稿」**搬进顶栏的「更多」菜单**了
            // （见 [ComposerDraftMenuRow]）：它原来是常显的一行小字，占掉 10 + 13 + 6 = 29dp，
            // 也就是正文区少一整行多的可用高度。fluxdo 的草稿状态本来就不在顶栏常显
            // （`composer_draft_status.dart:69` 的 `height => 48`，那是 `PopupMenuItem` 的高度），
            // 只在「更多」里出现。这里把那 29dp 还给正文，第一步的 10dp 留白也一起还了。
            Spacer(modifier = Modifier.height(6.dp))

            // 标题：24sp / w600 / letterSpacing 0，提示文字压到 50% 透明度。
            // 字号照 fluxdo `create_topic_page.dart:1127-1130`（`headlineSmall` 基底 24sp + w600）。
            // 注意本仓的 `headlineSmall` 被 FlTypography 压到 17sp（那是 FlClash 的二级页标题），
            // 所以这里必须显式给 24 —— 直接写 `typography.headlineSmall.copy(w600)` 会是 17sp。
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "输入标题…",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                        ),
                        color = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Normal,
                    )
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                ),
                colors = plainField,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            // 字数角标：右下角，小字灰，不占一行。
            Text(
                text = "${title.length}/$POST_TITLE_LIMIT",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.outline,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (previewing) {
                // 预览也吃满同一块高度：切过去时页面不跳、不缩，只是同一块区域换了内容。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                ) {
                    if (body.text.isBlank()) {
                        Text(
                            text = "（没有正文）",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        // `fillMaxWidth` 不能省：`MiniMarkdown` 内部是一个 `Column`，在
                        // `verticalScroll` 的 Box 里不给宽度就会按内容 intrinsic 量，长段落不换行。
                        MiniMarkdown(
                            text = body.text,
                            modifier = Modifier.fillMaxWidth(),
                            baseStyle = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = body,
                    onValueChange = onBodyChange,
                    // `weight(1f)` 就是源码里的 `expands: true`：正文占满标题与底栏之间的
                    // 全部高度，超出的部分自己在框内滚，**不再有 `minLines` 下限**
                    //（`markdown_editor.dart:917`：`minLines = expands ? null : 5`）。
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "正文内容（支持 Markdown）…",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                            ),
                            color = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    // 16sp / 24sp 行高：fluxdo `markdown_editor.dart` 不显式指定，
                    // 走 `TextField` 默认的 `bodyLarge`（16/24）。本仓的 bodyLarge 被
                    // FlTypography 改成 17/22，所以要显式收回来。
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                    ),
                    colors = plainField,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                )
            }

            // 展开的工具面板在工具栏**上方**长出来（fluxdo 的「浮岛展开」）：它只有几行，
            // 长在工具栏下面会先顶到 IME 那条线上，键盘一弹就被压没了。
            // 它是**兄弟节点**而不是套在工具栏 `Row` 里：`Row` 是横排，塞进去会跑到右边去；
            // 横向内缩 6dp 与下面那排的工具按钮左边对齐；纵向用 expand/collapse，时长照
            // `composer_workbench.dart`（展开 280ms / 收起 200ms）。
            AnimatedVisibility(
                visible = toolsPanelOpen,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                ),
                // Compose 的 `ExitTransition` 工厂里没有 `collapseVertically`：收起就是
                // `shrinkVertically`，方向与上面的 `expandVertically`（默认从 `Alignment.Top` 长）
                // 对齐——`shrinkVertically` 的 `shrinkTowards` 默认也是 `Alignment.Top`，往顶部收回。
                // 同仓 `SettingsScreen.kt` / `component/Dropdown.kt` / `component/CommentThread.kt`
                // 都用这个名字，这里照现有写法只传时长与缓动。
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                ),
            ) {
                EditorToolsPanel(
                    pinnedTools = PINNED_TOOLS,
                    onTool = { tool -> applyTool(tool) },
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }

            // 工具栏：图标 16dp、触控 48dp。常驻那 7 颗**不再横滚**（原来的 `horizontalScroll` 去掉）：
            // 常驻 7 颗 + 「更多工具」= 8 × 48dp = 384dp，360dp 的窄屏上比可用宽度（348dp）
            // 还宽一点，而横滚会把最右端那颗「更多工具」滑出可视区 —— 面板就永远点不开了。
            // 所以这一排固定不滚，7 颗的实际尺寸由 [EditorPinnedToolRow] 按可用宽度算
            // （≥40dp，装得下就是 48dp）；「更多工具」那颗始终 48dp——它是唯一的面板入口。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 常驻那几颗：每颗 48dp（fluxdo 的固定尺寸）。7 × 48 = 336dp，加上右端
                // 那颗 48dp 的「更多工具」共 384dp——比 360dp 的窄屏多 24dp，所以交给
                // `EditorPinnedToolRow` 去量一次可用宽度、按需把这 7 颗一起收到 ≥40dp。
                EditorPinnedToolRow(onTool = { tool -> applyTool(tool) })
                // 「更多工具」贴着**最右端**：它不属于常驻那组，得和右边对齐，
                // 所以中间垫一个 `weight(1f)` 的 Spacer 把它推过去。
                Spacer(modifier = Modifier.weight(1f))
                EditorMoreToolsButton(expanded = toolsPanelOpen) {
                    toolsPanelOpen = !toolsPanelOpen
                }
            }
        }
    }

    /*
     * 两个底部面板挂在**最外层 `Column` 的最后**，不在正文那块 `weight(1f)` 里面：
     *
     *  - `ComposerPickerSheet` 内部是 `Dialog`（见它的注释），它不属于这条路面的布局，
     *    放在哪儿都不会被压；但把它写进 `weight(1f)` 的那一段会让人误以为它参与高度分配。
     *  - 它必须在最外层：那一层带着 `imePadding()`（见函数开头），`ModalBottomSheet` 的
     *    `imePadding` 要接在这条 inset 链上才有键盘高度可吃。弹窗本身是另一个 window，
     *    `imePadding()` 会读宿主给的 `WindowInsets.ime`，所以标签面板里键盘弹起时，
     *    输入框会被顶到键盘上方，而不是被盖在底下。
     */
    if (categorySheetOpen) {
        ComposerPickerSheet(
            title = "选择分类",
            onDismiss = { categorySheetOpen = false },
        ) {
            // 第一项是"不选"：分类本来就可以空着（后端当字符串存，空串合法），
            // 没有这一项的话，选错了就只能再点一次同一个才能取消。
            PickerOptionRow(
                label = "不选分类",
                picked = category.isBlank(),
                color = null,
                onClick = {
                    onCategoryChange("")
                    categorySheetOpen = false
                },
            )
            POST_CATEGORIES.forEach { label ->
                PickerOptionRow(
                    label = label,
                    picked = label == category,
                    // 分类色点 7dp：与列表卡片、详情页那颗同一个来源（[categoryColorOf]），
                    // 面板里选的和外面看到的不能是两种颜色。
                    color = categoryColorOf(label),
                    onClick = {
                        onCategoryChange(label)
                        categorySheetOpen = false
                    },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (tagSheetOpen) {
        ComposerPickerSheet(
            title = "标签",
            onDismiss = { tagSheetOpen = false },
            /** 底部那颗「完成」：整份列表写回父级，并把输入框里没按逗号的最后一截也算上。 */
            confirm = "完成",
            onConfirm = {
                onTagsApply(tagsWithPending(tagDraft, tagField))
                // 外面那格隐藏输入框（属性条那一行）跟着清空：不然下次点开标签面板，
                // 外面还挂着一截没成为 chip 的字，人会以为面板里的选择没生效。
                onTagInputChange("")
                tagSheetOpen = false
            },
            imeAware = true,
        ) {
            // 已选：点一下删掉（fluxdo 是 `RemovableTagBadge`，这里用 [TagChips] 的 × chip）。
            if (tagDraft.isNotEmpty()) {
                TagChips(tags = tagDraft, onRemove = { tag -> tagDraft = tagDraft - tag })
                Spacer(modifier = Modifier.height(10.dp))
            }
            // 输入：与正文那格同一个切分规则（[onTagInputChange] 的逻辑），
            // 逗号 / 顿号 / 分号 / 空格一到就切一个 chip，凑满 [TAG_MAX] 就提示。
            OutlinedTextField(
                value = tagField,
                onValueChange = { raw ->
                    // 先清上一句：这次敲的字合法，还挂着上次的红字会像"又错了一次"。
                    pendingTagError = null
                    var buffer = StringBuilder()
                    var next = tagDraft
                    raw.take(TAG_LIMIT * TAG_MAX + 64).forEach { ch ->
                        if (!isTagSeparator(ch)) {
                            buffer.append(ch)
                            return@forEach
                        }
                        val tag = buffer.toString().trim()
                        buffer = StringBuilder()
                        if (tag.isEmpty()) return@forEach
                        when {
                            // 单个标签太长：不收，但把话说到位（不然看起来像吞字）。
                            tag.length > TAG_LIMIT -> pendingTagError = "单个标签最多 $TAG_LIMIT 字"
                            next.contains(tag) -> Unit
                            next.size >= TAG_MAX -> pendingTagError = "标签最多 $TAG_MAX 个"
                            else -> next = next + tag
                        }
                    }
                    tagField = buffer.toString()
                    tagDraft = next
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("输入标签，用逗号或空格分开，最多 $TAG_MAX 个") },
                textStyle = MaterialTheme.typography.bodyMedium,
                // `ImeAction.Next` 而不是 `Done`：这一格不是"最后一步"。
                // 面板底部的「完成」按钮才是提交，给它配一个 `Done` 却不接
                // `KeyboardActions(onDone)`，按下去什么都不会发生 ——
                // 键盘上出现两个"完成"、只有一个是有效的，比不出现更糟。
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = scheme.outlineVariant,
                    unfocusedBorderColor = scheme.outlineVariant.copy(alpha = 0.6f),
                ),
            )
            pendingTagError?.let { message ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.error,
                )
            }
            if (tagDraft.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${tagDraft.size}/$TAG_MAX",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 通用的**底部选择面板**：标题 + 内容 + （可选）底部一颗确认。
 *
 * 照 fluxdo 的 `showAppBottomSheet` + `CategorySelectionSheet` / `TagSelectionSheet`
 * （`topic_editor_helpers.dart:653-683` 调的就是这两个）。分类与标签两个面板共用它，
 * 因为两者的差别只在"里面装什么**"，不在壳。
 *
 * **为什么不是 `ModalBottomSheet`**：本仓另外两个从底下推出来的面板
 * （`UploadPlanDialog`、`PlanScopeDialog`）都已经从 `ModalBottomSheet` 退回来了，
 * 理由是同一个 —— 平台 sheet 会把列表的滚动**抢去拖它自己**，手指往上滑想翻列表，
 * 结果是整块面板跟着往上走、又被弹回一半，来回抖。所以这里用 `BasicAlertDialog` 自己画：
 * 顶部圆角 28 的 `Surface` 贴底，上面那条留白是遮罩。本仓的既有写法在
 * `component/PlanScopePicker.kt` 的 `PlanScopeDialog`，这里照它的骨架。
 *
 * `@OptIn(ExperimentalMaterial3Api::class)` 仍然要有：`BasicAlertDialog` 本身在
 * material3 里还是实验 API（`PlanScopeDialog` 也是这么标的）。
 *
 * @param confirm 底部那颗按钮的字；null 就不画（分类面板是"选完即关"，没有确认这一步）。
 * @param imeAware 是否把键盘高度算进去：标签面板里有一格输入框，`true`。
 *   实现是给**面板内容**加 `imePadding()`，不是给整个 `Dialog` —— 键盘弹起时只有内容区
 *   缩上去，遮罩和圆角不动，看起来才是"键盘把面板顶起来"而不是"面板整体抖一下"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerPickerSheet(
    title: String,
    onDismiss: () -> Unit,
    confirm: String? = null,
    onConfirm: (() -> Unit)? = null,
    imeAware: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        // 对话框自己占满窗口：用平台默认宽度的话，面板只有屏幕中间一条，
        // 而列表项是按"整宽一行"排的（手指从左边点到右边都该选中）。
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 遮罩**整屏铺满**，面板叠在它上面。
            //
            // 曾经写成 `fillMaxHeight(0.4f)`（"只铺面板上面那一段"），那是按"面板高度 = 屏高
            // 40%"假设的：分类面板 6 行可能就顶到 50% 以上，那一段露在遮罩外面成了死区
            // （点下去既关不掉面板、也没有任何反馈）；面板矮时又只剩一块半屏黑幕。
            // 面板自己是一个不透明的 `Surface`，它天然挡住点击，所以遮罩铺满不会误关。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = FlSurfaceLevel.Block.colorOf(scheme),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 导航栏留白垫在**面板**上：面板贴底，不垫的话最后一行会被手势条压住。
                        .navigationBarsPadding()
                        // 键盘高度垫在**内容**上（见 @param imeAware 的说明）。
                        .then(if (imeAware) Modifier.imePadding() else Modifier),
                ) {
                    // 顶部那条抓手：形状照 MD3 的 sheet（40×4、`onSurfaceVariant` α0.3），
                    // 右边一颗关闭 —— 面板挡着正文，得有一眼能看见的出口。
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(width = 40.dp, height = 4.dp)
                                .background(scheme.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(50)),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable(role = Role.Button, onClickLabel = "关闭", onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Close, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = title,
                        // 20sp / w700：与本仓其它底部面板的标题一致（`PlanScopeDialog`
                        // 用的就是这个数）。这里**不要**用 `headlineSmall` —— 本仓那个
                        // 是 17sp，面板标题会比正文还小。
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                    )
                    // 内容区自己滚：分类可能不止五个（历史数据里有「公告」），标签会更长。
                    // `heightIn(max = …)` 而不是 `weight(1f)`：面板高度是"内容多少就多高"，
                    // 两个候选的短列表不该被撑成半屏；超过 60% 屏高才开始滚。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        content = content,
                    )
                    if (confirm != null && onConfirm != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onConfirm) {
                                Text(confirm, color = scheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 面板里的一行候选：左边一个色点（可选）+ 名字，右边当前项打勾。
 *
 * 高 48dp：与菜单项、胶囊触控同一个数 —— 从属性条点进来，手指还是那根手指。
 * [color] 是分类色（[categoryColorOf]）：没有分类（「不选分类」）就传 null，那一格不画点，
 * 否则会误导成"有一个透明的分类"。
 */
@Composable
private fun PickerOptionRow(
    label: String,
    picked: Boolean,
    color: Color?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color != null) {
            // 分类色点 7dp：与列表卡片上那颗色块同一个尺寸，同一个来源。
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        } else {
            // 没色的那一行**仍然留出 7dp**：不占位的话，「不选分类」比下面每一行都往左
            // 错一格，看起来像另一个层级的东西。
            Spacer(modifier = Modifier.width(7.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
            color = if (picked) scheme.primary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (picked) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = scheme.primary)
        }
    }
}

/**
 * 属性条上的一个胶囊。数值逐项照 fluxdo `topic_editor_helpers.dart:711-761` 的 `_pill`
 * 手机分支（`if (!desktop)` 那一块）：
 *
 * ```
 * ConstrainedBox(minHeight: 48)            ← 触控高度，不是画出来的底
 *   └ DecoratedBox(
 *       color: surfaceContainerHighest α 0.42
 *       side:  outlineVariant α 0.55, width 0.6
 *       radius: 9999                          ← 胶囊
 *     )
 *     └ Padding(horizontal: 8, vertical: 6)   ← 只有这 12dp 是画出来的边
 * ```
 *
 * 为什么上面是两层、而不是一个 48dp 高的胶囊：48 是**手指**要的，胶囊本身只有
 * `字号行高 + 12` ≈ 32dp。把 48 直接写进胶囊，属性条会凭空高出 16dp，正文区跟着缩
 * 一整行；fluxdo 用 `ConstrainedBox` + `Center(widthFactor/heightFactor: 1)` 把"触控 48、
 * 视觉 32"分开，这里照抄：外层 `heightIn(min = 48.dp)` 吃点击，内层 `Row` 只装内容。
 *
 * [dropdown] 决定右端那颗小箭头画不画：分类是"点开一列选项"，箭头照着 `chevronDown` 画
 * （16dp，原来 14dp —— fluxdo 的 `_pill` 里箭头与文字同一行、同一个 `iconSize`）；
 * 标签和分享方案是开关/动作，挂一个 ▾ 反而像"这里还有一层菜单"。
 */
@Composable
private fun ComposerPill(
    text: String,
    accent: Boolean,
    onClick: () -> Unit,
    dropdown: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // 外层只负责触控与居中：48dp 的最小高度，胶囊在它里面垂直居中（`center`）。
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(scheme.surfaceContainerHighest.copy(alpha = 0.42f))
                // 0.6dp 的发丝描边：`border` 必须排在 `background` **之后**、`padding` 之前 ——
                // 顺序反了描边会被背景盖掉，或者内边距把描边推进内容里。
                .border(
                    width = 0.6.dp,
                    color = scheme.outlineVariant.copy(alpha = 0.55f),
                    shape = CircleShape,
                ),
            // 内边距下沉到内容上：写在 Row 上时，窄屏被邻居挤压之后背景是**按压扁后的尺寸**
            // 画的，胶囊会被切成一条，文字边界和底色对不齐。
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 属性条不横滚（见调用点注释），所以文字要自己收：分类名、标签计数都不会太长，
            // 但"分享方案"这一格在超窄屏上仍可能把邻居挤出去，省略号比换行好。
            //
            // **不能给 weight**：这颗胶囊在 `Row` 里是被父级按内容量出来的，没有有界宽度时
            // `weight` 会算成 0，文字直接消失。用 `widthIn(max = …)` 兜一个上限，超了才省略。
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (accent) scheme.primary else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 140.dp)
                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = if (dropdown) 0.dp else 8.dp),
            )
            if (dropdown) {
                // 箭头那一格也要吃同一份内边距，否则右端会贴到胶囊边缘上。
                Row(
                    modifier = Modifier.padding(end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 下拉小三角：告诉人"这里点开是一个面板"，不是一个开关。
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp), tint = if (accent) scheme.primary else scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 工具栏里的一个按钮：图标 16dp、触控 [size]（fluxdo 的 `_ToolbarButton`，默认 48dp）。 */
@Composable
private fun EditorToolButton(tool: EditorTool, size: Dp, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = tool.label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = tool.icon,
            contentDescription = tool.label,
            modifier = Modifier.size(16.dp),
            tint = scheme.onSurfaceVariant,
        )
    }
}

/**
 * 常驻那一排里的 7 颗（不含「更多工具」）。
 *
 * 为什么要在外面再包一层 `BoxWithConstraints`：48dp × 7 = 336dp 是 fluxdo 的固定尺寸，
 * 加上右端那颗 48dp 的「更多工具」一共 384dp，而 360dp 的安卓机扣掉两侧 6dp 内边距只剩
 * 348dp —— 不处理的话最后两颗会被裁掉。这里量一次可用宽度，装不下就把这 7 颗**一起**
 * 收到 `可用宽度 / 7`，下限 40dp（Material 对密集工具栏给的最小可触尺寸，再小不该点）。
 * 收的是触控盒，图标仍是 16dp，所以视觉密度不变、只是手指间距紧一点。
 *
 * `widthIn(max = 48 * 7)` 不是装饰：`BoxWithConstraints` 在 `Row` 里会吃满剩下的宽度，
 * 不封顶的话 `maxWidth` 永远是"整行"，每颗算出来就超过 48，右端那颗就被推出屏幕。
 */
@Composable
private fun EditorPinnedToolRow(onTool: (EditorTool) -> Unit) {
    // `48.dp * PINNED_TOOLS.size`：高版本 Compose 有 `Dp.times(Int)`；这里把整数先转成
    // Float 再乘，绕开"operator 只对 Float 声明"的老版本差异 —— 结果一样，不会因为
    // 平台版本不同而编译失败。
    BoxWithConstraints(modifier = Modifier.widthIn(max = 48.dp * PINNED_TOOLS.size.toFloat())) {
        val perTool = (maxWidth / PINNED_TOOLS.size).coerceIn(40.dp, 48.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PINNED_TOOLS.forEach { tool ->
                // 插在光标处 / 包住选中那一段，不再往末尾追加（见 [insertMarkdown]）。
                EditorToolButton(tool = tool, size = perTool) { onTool(tool) }
            }
        }
    }
}

/**
 * 「更多工具」那一颗：常驻排最右端，展开/收起上面那块 [EditorToolsPanel]。
 *
 * 位置照 fluxdo `composer_tool_style.dart:16-18` 的 `IconButton`：**48dp 见方**、图标 16dp。
 * 展开时换成 [Icons.Rounded.Close] 并铺 `secondaryContainer` 底色 —— fluxdo 用底色的变化告诉人
 * "现在这排上面那块是我开出来的"；只换图标的话，点完会以为按钮变成了别的东西。
 * 动画 150ms，与 fluxdo 那个切换同一个时长。
 */
@Composable
private fun EditorMoreToolsButton(expanded: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // 底色也要渐变：直接 `if` 换色的话，展开/收起时是"啪"地跳一下。
    val background by animateColorAsState(
        targetValue = if (expanded) {
            scheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 150),
        label = "editorMoreToolsBg",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) "收起更多工具" else "更多工具",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (expanded) Icons.Rounded.Close else Icons.Rounded.GridView, null, Modifier.size(16.dp), tint = if (expanded) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
    }
}

// ---- 展开的工具面板（照 fluxdo `composer_tools_panel.dart` + `composer_tool_cell.dart`） ----

/** 网格左右内边距（fluxdo `gridInset = 8`）。 */
private val TOOLS_GRID_INSET = 8.dp

/** 一格图标块的边长与圆角（fluxdo `composer_tool_cell.dart` 的 52 / 14）。 */
private val TOOL_TILE = 52.dp
private const val TOOL_TILE_RADIUS = 14

/** 图标块与名字之间的间距（fluxdo `SizedBox(width: 6)`）。 */
private val TOOL_TILE_GAP = 6.dp

/**
 * 面板：以 [FlowRow] 铺「分组标题 + 该组的格子」，**不推路由、不开新页面**，就是底部块里
 * 长出来的一块（调用点用 `AnimatedVisibility` 展开/收起）。
 *
 * 为什么是 `FlowRow` 而不是 `LazyVerticalGrid`：
 *
 *  1. 面板只有一个"自然高度"（这里一共 13 项、3 个分组标题，窄屏最多 4 行格子），
 *     没有"滚起来才加载"的需求；`LazyVerticalGrid` 反而必须给它**有界高度**才能量出来，
 *     在底部这个 wrap 高度的块里得先算一遍 `mainAxisExtent * 行数`，等于把布局又抄一遍。
 *  2. `LazyVerticalGrid` 不能把"分组标题"当整行塞进同一张网格（要么拆成几个网格、
 *     要么用 `span`，两者都要另写一套间距），而 `FlowRow` 里标题就是一个占满宽度的
 *     `Text`，天然按顺序排下去。
 *  3. 内容全是本地常量：没有图片、没有异步，懒加载省不下任何东西。
 *
 * 列宽的算法与 fluxdo 一致：先按 fluxdo 的 `columns` 公式算列数（`2..6`），
 * 再把可用宽度平均分——`FlowRow` 没有"列"的概念，所以这里用**定宽格子 + 定宽占位**
 * 把每格钉成 `columnWidth`，效果与网格一样，还能让窄屏每行 2 格、宽屏每行 6 格。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorToolsPanel(
    pinnedTools: List<EditorTool>,
    onTool: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // fluxdo：`(maxWidth / max(80, 56 + 24)).floor().clamp(2, 6)`。
        // 本仓没有 `scale()`：它就是"系统字号缩放"，而 56 与 24 这两档在 16sp 基准下已经够宽，
        // 所以直接取 56 + 24 = 80 —— 与公式里那个下限 `max(80, …)` 是同一个数。
        val columns = ((maxWidth.value / 80f).toInt()).coerceIn(2, 6)
        val gridWidth = maxWidth - TOOLS_GRID_INSET * 2
        val columnWidth = gridWidth / columns
        // 分组标题与格子**左对齐到同一条线**：格子居中在 columnWidth 里，标题的左边距就得多
        // 让出「(columnWidth - 52) 的一半」，照 fluxdo 的 `headingInset`（不让的话标题比格子凸出来）。
        // 折半用 `* 0.5f`（不用 `/ 2f`）。
        val headingInset = TOOLS_GRID_INSET + ((columnWidth - TOOL_TILE) * 0.5f).coerceAtLeast(0.dp)

        Column(modifier = Modifier.fillMaxWidth()) {
            // 顶部 4dp / 底部 8dp：面板是"贴在工具栏上方"的，两头的留白不对齐的话，
            // 展开的一瞬间看起来像是工具栏被往下挤了。
            Spacer(modifier = Modifier.height(4.dp))
            TOOL_GROUPS.forEach { group ->
                val tools = TOOLS_BY_GROUP[group].orEmpty()
                if (tools.isEmpty()) return@forEach
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = headingInset,
                        top = 10.dp,
                        end = headingInset,
                        bottom = 4.dp,
                    ),
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TOOLS_GRID_INSET),
                    // 不给 `horizontalArrangement`：每格已经由外层 `Box(width = columnWidth)` 定死，
                    // 一列刚好 `columns` 格，中间不留缝（留缝会让最后一格被挤到下一行）。
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tools.forEach { tool ->
                        Box(modifier = Modifier.width(columnWidth)) {
                            ToolCellBody(
                                icon = tool.icon,
                                label = tool.label,
                                pinned = pinnedTools.any { it === tool },
                                onClick = { onTool(tool) },
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 面板里的一格：上面 52dp 的圆角方块住图标（20dp），下面一行 12sp 的名字。
 *
 * [pinned] = 这一项在常驻排里已经有一颗，面板里把图标块换成 `primaryContainer`：
 * fluxdo 用它标"这条已经在外面了"，免得人以为是两个不同的动作。
 */
@Composable
private fun ToolCellBody(
    icon: ImageVector,
    label: String,
    pinned: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tile by animateColorAsState(
        targetValue = if (pinned) {
            scheme.primaryContainer
        } else {
            scheme.surfaceContainerHighest.copy(alpha = 0.6f)
        },
        animationSpec = tween(durationMillis = 150),
        label = "toolTileBg",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TOOL_TILE_RADIUS))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(TOOL_TILE)
                .clip(RoundedCornerShape(TOOL_TILE_RADIUS))
                .background(tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                // 置顶那格用 `onPrimaryContainer`：`primaryContainer` 上再压一层
                // `onSurfaceVariant` 的灰，对比度会掉到看不见。
                tint = if (pinned) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(TOOL_TILE_GAP))
        // 名字单行省略：窄屏 2 列时 columnWidth 可能只有 ~150dp，足够，但大字号下"清空正文"
        // 仍会溢出——省略比换行好，换行会让同一行的两格高度不一致。
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 14.sp),
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


/**
 * 分类**不再**用一排 chip 铺在底部：它现在是一个 [ComposerPill] 点开的 [DropdownMenu]
 * （照 fluxdo 的 `_CategoryPicker`）。内联铺 chip 的做法会把底部那两块高度顶上去 26dp，
 * 每选一次分类正文区就缩一下。
 *
 * 选择逻辑本身没变，仍在 [PostsSection] 的 `onCategoryChange`，候选仍是 [POST_CATEGORIES]。
 */

/** 已经成形的标签：点一下删掉它。 */
@Composable
private fun TagChips(tags: List<String>, onRemove: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.forEach { tag ->
            PostChip(
                text = "$tag ×",
                picked = false,
                onClick = { onRemove(tag) },
            )
        }
    }
}

/** 一枚可点的 chip：选中时实心，没选时描边。 */
@Composable
private fun PostChip(
    text: String,
    picked: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (picked) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (picked) scheme.primary else FlSurfaceLevel.Page.raised.colorOf(scheme))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * 只读的一行 chip：分类 + 最多 [maxTags] 个标签，多出来的写 `+N`。
 *
 * 列表一行只有一行的宽度，所以标签截断；详情传 `maxTags = tags.size` 把全部画出来。
 */
@Composable
private fun PostMetaChips(
    category: String,
    tags: List<String>,
    modifier: Modifier = Modifier,
    maxTags: Int = 3,
    /** 这一行的退灰系数：全部读完后传 0.55（`topic_card.dart:851` 的 `opacityFactor`）。 */
    dim: Float = 1f,
    /** 后端下发的分类色（`#RRGGBB`）；空串就退回按名字哈希取兜底色。 */
    categoryColor: String = "",
) {
    if (category.isBlank() && tags.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val shown = tags.take(maxTags)
    val hidden = tags.size - shown.size
    // 元信息小字：未读 0.8；全读完是 **0.6 × 0.55 两层**（`topic_card.dart:850-851`：
    // metaColor 先取 0.6，再乘 `opacityFactor = 0.55`）。两档不能拿同一个 0.8 当底去乘 dim，
    // 那会把已读档算成 0.44，比 fluxdo 亮一截。
    val metaColor = if (dim < 1f) {
        metaTint(scheme.onSurfaceVariant, 0.6f * dim)
    } else {
        metaTint(scheme.onSurfaceVariant, 0.8f)
    }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (category.isNotBlank()) {
            // 分类：一个小色块 + 名字（Discourse 的样子）。色块让"这帖属于哪一类"变成一眼可见的
            // 颜色；颜色优先用后端下发的那一格（见 [categoryTint]），没有才按名字哈希兜底。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(categoryTint(category, categoryColor, dim)),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelSmall,
                    color = metaColor,
                    maxLines = 1,
                )
            }
        }
        shown.forEach { tag ->
            Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelSmall,
                color = metaColor,
                maxLines = 1,
            )
        }
        if (hidden > 0) {
            Text(
                text = "+$hidden",
                style = MaterialTheme.typography.labelSmall,
                color = metaColor,
                maxLines = 1,
            )
        }
    }
}

/**
 * 分类色**兜底**用的色板：只在后端没下发 `category_color` 时才用到。
 *
 * 为什么不拿它当正路：fluxdo 那边的分类色是 Discourse **下发给每个分类自己的那一格**
 * （`ColorUtils.parseHex` → `ColorUtils.themed`，见 `lib/utils/color_utils.dart`），
 * 按名字哈希是猜的——同名的分类在不同站是不同颜色，同站的不同分类也可能撞到同一个哈希槽。
 * 所以有 `#RRGGBB` 就用那个，没有才落到这张表。
 */
private val CATEGORY_COLORS = listOf(
    Color(0xFF25A6E8), // 公告 / 蓝
    Color(0xFFE8543F), // 重要 / 红
    Color(0xFF2E9E5B), // 分享 / 绿
    Color(0xFFE8A33F), // 资源 / 橙
    Color(0xFF8E6FE8), // 闲聊 / 紫
    Color(0xFF3FA9A0), // 求助 / 青
)

/**
 * 一个分类的显示色：后端给了 `#RRGGBB` 就用它，否则按名字哈希从兜底色板取一个。
 *
 * 两条路之后都过一遍 [readableOnCurrentTheme]：品牌色只调明度，保证深色/亮色主题下都看得清，
 * 色相不变（同一个分类换主题还是那个颜色）。[dim] 是"全读完"的退灰（fluxdo 是 0.55）。
 *
 * 同包里的分类侧栏（`PostsDrawer.kt`）也用它：抽屉里那颗色点必须和卡片上的分类色同一个。
 */
@Composable
internal fun categoryColorOf(category: String, hex: String = "", dim: Float = 1f): Color {
    val dark = MaterialTheme.colorScheme.isDark
    val base = parseHexColor(hex)
        ?: fallbackBrandColor(category, CATEGORY_COLORS)
    val readable = readableBrandColor(base, dark)
    return if (dim == 1f) readable else readable.copy(alpha = readable.alpha * dim)
}

/**
 * 列表/详情里那个分类色块：[categoryColorOf] 的简写，接的是帖子自己的 `categoryColor` 字段。
 *
 * 非 composable 的调用方（比如抽屉里的 `remember`）直接调 [categoryColorOf]。
 */
@Composable
private fun categoryTint(category: String, hex: String, dim: Float): Color =
    categoryColorOf(category = category, hex = hex, dim = dim)

/** 「草稿 N 篇 · 继续编辑」那一行：点它接着写最新那篇。横向 12 由列表的 `contentPadding` 给。 */
@Composable
private fun DraftBanner(
    count: Int,
    newest: PostDraft,
    onContinue: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .clickable(role = Role.Button, onClick = onContinue)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "草稿 $count 篇 · 继续编辑",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "最近 ${draftClock(newest.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Text(
            text = newest.title.ifBlank { newest.body.take(40).ifBlank { "（空草稿）" } },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 草稿列表里的一行：标题、一句预览、时间，右边一个删除。横向 12 由列表的 `contentPadding` 给。 */
@Composable
private fun DraftRow(
    draft: PostDraft,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = draft.title.ifBlank { "未命名草稿" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    val preview = draft.body.replace('\n', ' ').trim()
                    if (preview.isNotEmpty()) {
                        append(preview.take(40))
                        append(" · ")
                    }
                    append(draftClock(draft.updatedAt))
                    append(" 改过")
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onDelete) { Text("删除") }
    }
}

/**
 * 一个区块的标题：名字，带条数时再跟一个数字。
 *
 * 样式照 fluxdo `topic_more_topics.dart:86-130`：`titleSmall / w600` + 前一颗 16dp 的
 * 强调色小图标 + 6dp 间距，区块内边距 `LTRB(16, 8, 16, 6)`（上一版 16/8 ≈ 44dp 高，现在约 34dp）。
 * 上一版是 `labelMedium + primary` 的"彩色小标签"，那是 FlClash 的区块头写法，
 * 在帖子详情里看着像一条可点的链接。
 *
 * **收内距的两个理由**：
 *  1. 参考 `topic_detail_header.dart` 里**没有这一行** —— 它把回复数并进了页头元信息
 *     （`_buildMetadataItem` 的「N 回复」）。本仓保留这一行是有代价的：它是一条不承载内容的横带。
 *  2. 但**不能删这个 item**：`FLOORS_FIRST = 3` 与 `floorIndexOf`（以及 `jumpToFloorId` /
 *     目录面板）都按 **item 序号**定位楼层，「回复 · N」占了第 3 格。删掉它，楼层索引、
 *     跳楼、帖内搜索的落点、目录定位会**全部错一格**。所以只能压它自己的内距：
 *     顶 16 → 8、底 8 → 6，高度约 44 → 34dp，省下 10dp 而不动任何索引。
 *     这里压内距换密度，是"保留 item"与"对齐参考密度"之间唯一不破索引的做法。
 */
@Composable
private fun PostSectionHeader(label: String, count: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 顶 8 / 底 6（上一版 16 / 8，约 44dp → 约 34dp）：只压留白，不动 item 序列，见 KDoc。
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 「置顶话题 · N」那一行。数值照 fluxdo 的 `pinned_topics_header.dart`：
 * 圆角 12（`PinnedTopicsGroup.outerRadius`，:12）、**没有横向外边距**、内边距
 * `EdgeInsets.symmetric(horizontal: 12, vertical: 14)`（:81-84，在 `InkWell` 里）、
 * 文字 `labelLarge`、未读角标、箭头 20dp 且展开时旋转 180°（180ms）。
 *
 * 横向 12 不在这里：列表的 `contentPadding` 已经给了（和卡片同一条线）。
 * 上一版这里还多一层 `horizontal = 12`，等于 24，和卡片左缘错开一格，看着像另一个分组。
 *
 * 展开状态由调用方持有（必须在 `LazyListScope` 之外，见 [PostListPane]）。
 */
@Composable
private fun PinnedTopicsHeader(
    count: Int,
    unreadCount: Int,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val turns by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "pinned-arrow",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "置顶话题 · $count",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (unreadCount > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = unreadCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(scheme.primary)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.Rounded.ExpandMore, null, Modifier.size(20.dp).rotate(turns), tint = scheme.onSurfaceVariant)
    }
}

/**
 * 「↑ 查看 N 个新的或更新的话题」。数值照 fluxdo 的 `topic_list_update_banner.dart`：
 * 圆角 8（:38）、底色 `primaryContainer` 20%（:36）、**横向上没有 margin，只有
 * `EdgeInsets.only(bottom: 12)`**（:34）、内部 `symmetric(vertical: 8)`（:46）、
 * 图标 14dp（:64）、图标与文字间距 8（:67）、文字 13sp / w500 / 主色（:70-74）。
 *
 * 左右 12 由列表的 `contentPadding` 给（见 [PostListPane]）——上一版这里自己又加了一层，
 * 与卡片对齐就变成了 24，这条横幅会比它下面的卡片窄一圈。
 * 列表里的空状态/错误那几行（[PostNote]）同理，横向由列表给，自己只管纵向。
 *
 * `onClick` 在加载中要不要禁用：fluxdo 是 `onTap: loading ? null : onTap`（:44），
 * 本仓的加载态由外层 `PullToRefreshBox` 与 `loadingMore` 各自表达，这一条只负责"点一下重拉"，
 * 重复点会被 `onRefresh` 里的 `if (!refreshing)` 吃掉，所以不另设参数——不是漏做。
 */
@Composable
private fun FreshTopicsBanner(count: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.primaryContainer.copy(alpha = 0.2f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 向上的箭头用 `Icons.Rounded.ArrowUpward`（与 fluxdo 的 `arrow_upward_rounded` 同一个）：
        // `arrowUpward`（与 `arrowBack` 同构，转 90°）。
        Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(14.dp), tint = scheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "查看 $count 个新的或更新的话题",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.primary,
        )
    }
}

/**
 * 「你还没改名字」那一条。
 *
 * 放在列表顶端而不是弹窗：这是**可以被忽略**的提示（有人就是不想起名），但它得一直在，
 * 因为改名之前每条帖子上的署名都是打码的样子。红点负责把眼睛勾过来，文案负责说清去哪儿改。
 */
@Composable
private fun AnonymousHintBanner() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnonymousDot(size = 7.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "你还没改名字：头像和名字现在都用占位块盖着（骨架样式），别人认不出你。" +
                "去「我的 → 昵称」写一个，帖子和评论马上换成真名。",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * 一句说明 / 空状态。
 *
 * 横向：列表里用的那一档由 `LazyColumn` 的 `contentPadding` 给左右 12，这里只再补 4，
 * 与卡片正文的 12+12 左缘同一列（卡片自带内边距 12）；详情页没有列表层，
 * 那边传 `modifier` 进来自己接管横向。
 *
 * 图标 + 文字的那一套照 fluxdo `topic_state_summary.dart:26-54`：
 * - 图标 **18dp**（`ExcludeSemantics` → 这里用 `contentDescription = null`，读屏不念装饰图）
 * - 图标与文字之间 **8dp**
 * - 文字 `bodySmall`（本仓被 FlTypography 抬到 15/20，**不压字号**：这一行本来就是正文级的
 *   说明句，压到 11 会跟楼层的元信息抢权重），颜色 `onSurfaceVariant`
 * - 每一条上下各 **4dp**，整组底部再留 **12dp**
 *
 * 为什么加图标而不是留一行灰字：详情页里"还没有人回"、"回帖最多 200 层"这些句子和上面
 * 的楼层正文同一段灰度，读起来像正文的一部分；前面站一颗 info 才明确是"说明"。
 */
@Composable
private fun PostNote(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    // 文字与图标**首行顶端对齐**（`Alignment.Top` + 两边各压 1dp）：整块居中时，句子
    // 超过一行图标就悬在两行正中间，读起来像另一段的东西。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 图标与首行文字顶端对齐：18dp 的图标比 20sp 的行高略矮，往下压 1dp 才和字头齐。
        Icon(Icons.Rounded.Info, null, Modifier.size(18.dp).padding(top = 1.dp), tint = scheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 一枚小标记：置顶、已锁、我的。 */
@Composable
private fun PostBadge(
    text: String,
    color: Color,
    foreground: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = foreground,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** 删帖的二次确认：删了回帖和赞一起没了，不能只靠一次点错。 */
@Composable
private fun PostDeleteDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "删除这条帖子？",
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
            Button(enabled = !busy, onClick = onConfirm) {
                Text(if (busy) "正在删…" else "删除")
            }
        },
    ) {
        Text(
            text = "删了之后帖子、回帖和赞都一起没了，恢复不了。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 「分享方案」的选择器：一列方案，点一条就把它的分享文案插进正文。
 *
 * 每一行都带着方案自己的机型（`deviceLabel`），这正是分享时最要紧的那一句：同一份包在
 * REDMI K80 Ultra 上能删，换一台机器不一定是同一个结论。所以插入的文案由
 * [ShareCode.message] 生成，机型从方案那一行走，写帖的人不用手打。
 *
 * 列表只到「挑一个」为止：方案能不能用、哪些包真被删过，仍然由方案详情页回答，
 * 这里不重复一遍也不替它打包票。
 */
@Composable
private fun SharePlanPickerDialog(
    plans: List<CommunityPlan>,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPick: (CommunityPlan) -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "分享一个方案",
        scrollable = true,
        buttons = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        val scheme = MaterialTheme.colorScheme
        when {
            loading -> Text(
                text = "正在拉方案…",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )

            error != null -> Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.error,
            )

            plans.isEmpty() -> Text(
                text = "还没有能分享的方案。先去「方案」页传一份，或者让它拉完再来。",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )

            else -> plans.forEach { plan ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPick(plan) }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = plan.title.ifBlank { "未命名方案" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        // 机型是这一格最要紧的一行：它决定这个分享码对别人是不是同一件事。
                        text = plan.deviceLabel.ifBlank { "机型没记录" },
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = ShareCode.of(plan.id),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * 草稿时间：`HH:mm`（**北京时间**，和页面上其它时间同一个时区）。
 *
 * 这里不用 `relativeStamp`：那是"3 分钟前"，而自动保存要的是"几点几分存的"，一眼能对上墙上的钟。
 * 0 或者算不出来就返回空串，调用方据此不画那一行。
 */
private fun draftClock(updatedAt: Long): String {
    if (updatedAt <= 0L) return ""
    return runCatching {
        val local = Instant.fromEpochMilliseconds(updatedAt).toLocalDateTime(BeijingTime.zone)
        val hour = if (local.hour < 10) "0${local.hour}" else local.hour.toString()
        val minute = if (local.minute < 10) "0${local.minute}" else local.minute.toString()
        "$hour:$minute"
    }.getOrNull().orEmpty()
}

/** 标签的分隔符：中文逗号、英文逗号、顿号、分号、空格、换行都算。 */
private fun isTagSeparator(ch: Char): Boolean =
    ch == ',' || ch == '，' || ch == '、' || ch == ';' || ch == '；' || ch == ' ' || ch == '\n'

/**
 * 把一段 Markdown 模板插进正文：有选中就**包住选中那一段**，没有就插在光标处。
 *
 * 这是工具栏那七个按钮真正的行为。以前只能往末尾追加，因为正文是 `String` 拿不到游标；
 * 现在正文是 `TextFieldValue`，游标位置一直在手上。
 *
 * 规则：
 * - **有选中**：`选中的字` → 例如粗体变成 `**选中的字**`，并保持这段仍被选中（这样连点两次
 *   粗体就是取消，符合所有人对工具栏的预期）。
 * - **没选中**：插入模板并把游标放在"该接着写的地方" —— `**粗体**` 里两个星号中间，
 *   `> 引用` 的末尾，`[文字](https://)` 的"文字"两个字上。
 * - 总长仍受 [limit] 约束：超了就**不插**，原地不动。截断会把 `**` 截成半个，正文就坏了。
 *
 * @param template 模板原文，模板里那一段文字用来定位"该选哪儿"。
 */
private fun insertMarkdown(
    value: TextFieldValue,
    template: String,
    limit: Int,
): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    // 选中的范围里已经就是这个模板包的（例如已经是 `**粗体**`）：取消掉这层。
    val selected = text.substring(start, end)
    // 模板的"包装形态"：星号、反引号、括号这类成对的东西，以及只有前缀的那几种。
    val wrapped = unwrapTemplate(selected)
    if (wrapped != null) {
        val next = text.replaceRange(start, end, wrapped.first)
        // 解开也是变短，但仍守一次上限：凑巧超了就不动。
        if (next.length > limit) return value
        // 解开后保持选中的长度由 [unwrapTemplate] 给出（内文自己的长度），
        // 不是"整段"——这样连点两次粗体是在同一个选中的字上反复开关。
        return value.copy(
            text = next,
            selection = TextRange(start, start + wrapped.second),
        )
    }
    // 没选中：插入模板，并把选区落在"该接着写的地方"。
    //
    // 块级模板（`> ` / `- ` / `1. ` / `## ` / 分隔线 / 代码块 / 表格）**自带开头那个换行**
    // （见 EDITOR_TOOLS 里那几个 snippet）。不在函数里补：模板字符串本身就是"插进去以后
    // 会变成什么"的说明，凭空多一个 `\n` 会让人对不上；而且补出来的换行还要在算游标时
    // 额外偏移，多一处算错的机会。
    //
    // 前缀只在**没落在行首**时补：落在行首还补，正文会凭空多一行空行（空行在 Markdown 里
    // 是段落分隔，预览里会多一个 gap）。
    val block = template.startsWith("> ") ||
        template.startsWith("- ") ||
        template.startsWith("1. ") ||
        template.startsWith("## ") ||
        template.startsWith("\n")
    val prefix = if (block && start > 0 && text[start - 1] != '\n') "\n" else ""
    val next = text.replaceRange(start, end, prefix + template)
    if (next.length > limit) return value
    // 链接模板选中"文字"两个字：那是要替换的占位符，选中它就能直接打字覆盖，
    // 不用先手动删掉。
    val focus = focusRangeFor(template)
    return value.copy(
        text = next,
        selection = TextRange(start + prefix.length + focus.first, start + prefix.length + focus.second),
    )
}

/**
 * 选中内容已经带着 Markdown 记号时，返回解开后的文字与它应保持的选中长度；否则 null。
 *
 * `**粗体**` 的包裹对是 `**`+`**`，`[文字](https://)` 的是 `[`+`](https://)`。
 * 只处理工具栏这几种模板的形状，不求通用 —— 通用化就要解析 Markdown，那是另一件事。
 *
 * 顺序有讲究：`~~` 必须排在 `~` 之前（本仓还没有 `~` 前缀，但删掉 `~~` 的两端以后
 * 剩下的是 `删除线`，不会退化成单星号）；`1. ` 与 `- ` 这类"只有前缀"的项统一放在末尾，
 * 因为 `**`/`*`/`` ` `` 这些成对的更具体，先试它们。
 *
 * 不需要知道是哪一颗按钮：判断全靠"选中的这段字自己长什么样"。所以在 `**x**` 上点斜体
 * 也会把它解成 `x`（`*` 也是它的前缀），这是可接受的——人看到的是"星号没了"。
 */
private fun unwrapTemplate(selected: String): Pair<String, Int>? {
    if (selected.isEmpty()) return null
    // 三列：前缀、后缀、这套模板是不是"只有前缀"（`## ` / `- ` / `> ` 那种）。
    val pairs = listOf(
        Triple("**", "**", false),
        Triple("~~", "~~", false),
        Triple("*", "*", false),
        Triple("`", "`", false),
        Triple("[", "](https://)", false),
        Triple("## ", "", true),
        Triple("1. ", "", true),
        Triple("- ", "", true),
        Triple("> ", "", true),
    )
    pairs.forEach { (prefix, suffix, prefixOnly) ->
        if (!selected.startsWith(prefix)) return@forEach
        if (!prefixOnly && !selected.endsWith(suffix)) return@forEach
        if (selected.length <= prefix.length + suffix.length) return@forEach
        val inner = selected.substring(prefix.length, selected.length - suffix.length)
        // 第二个值 = 解开后该保持选中的长度（就是内文自己的长度）。
        return inner to inner.length
    }
    return null
}

/**
 * 模板插进去之后，选区该落在哪：**相对模板开头**的 (起点, 终点) 字符偏移。
 *
 * 相等 = 光标停在那儿；不等 = 那段被选中（链接的"文字"两个字）。
 * 不用 `IntRange`：空区间要写成 `2..1`，读起来像倒着数。
 */
private fun focusRangeFor(template: String): Pair<Int, Int> = when (template) {
    // `**粗体**`：落在两个 `*` 之后、`粗` 之前。
    "**粗体**" -> 2 to 2
    "*斜体*" -> 1 to 1
    "`代码`" -> 1 to 1
    "~~删除线~~" -> 2 to 5
    "## 小标题" -> 3 to 6
    // 有序列表与无序列表一样，光标停在 `- ` / `1. ` 之后、正文之前。
    "1. 一项" -> 3 to 3
    "- 一项" -> 2 to 2
    "> 引用" -> 2 to 2
    // `[文字](https://)`：选中"文字"两个字（偏移 1、2）。
    "[文字](https://)" -> 1 to 3
    // 代码块：光标落在两个围栏中间那一行（行 1 的行首 = 偏移 4）。模板自带开头那个 `\n`，
    // 它在函数里作为 `prefix` **不重复加**（见 insertMarkdown），所以这里按模板自身偏移算。
    "\n```\n代码\n```" -> 5 to 7
    // 分隔线是"插一整块骨架"，光标放到最后：接着写的是下一段正文，
    // 而不是插到 `---` 中间（那儿没有"中间"）。前后各留一个换行，光标停在最后一个之后。
    "\n---\n" -> 6 to 6
    // 表格：选中第一行第一个格子里的「列 1」，直接改名比先删占位符快。
    "\n| 列 1 | 列 2 |\n| --- | --- |\n| 内容 | 内容 |" -> 3 to 6
    else -> template.length to template.length
}

/**
 * 把输入框里还没被分隔符切开的那一截并进标签表，规则与输入时一致（去空、20 字、不重复、最多 5 个），
 * 但**不提示**：发布或存草稿那一刻不该再弹一句话。
 */
private fun tagsWithPending(tags: List<String>, pending: String): List<String> {
    val tag = pending.trim()
    if (tag.isEmpty()) return tags
    if (tag.length > TAG_LIMIT) return tags
    if (tags.contains(tag)) return tags
    if (tags.size >= TAG_MAX) return tags
    return tags + tag
}

