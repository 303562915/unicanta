package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import io.github.samolego.canta.data.community.PostComment
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf

/**
 * 帖子详情页的**帖内搜索面板**：在这条帖子的楼层里找词，点一条结果跳到那一楼。
 *
 * ## 它搜的是什么（先看这一节，别做成全站搜索）
 *
 * 只搜**当前这一帖的回帖**（[PostSearchSheet] 的 `comments`），不查库、不跨帖。这一点是从参考源码
 * 核出来的：详情页顶栏那颗搜索图标（`lib/pages/topic_detail_page/topic_detail_page.dart:1659-1672`）
 * 只做了一件事 —— `topicSearchProvider(widget.topicId).enterSearchMode()`，即"进入**这条帖**的
 * 搜索态"；它的结果视图 `lib/widgets/search/topic_search_view.dart:17-24` 的入参就是 `topicId` +
 * `onJumpToPost`，点一条走 `onJumpToPost(post.postNumber)`（:210-212）在**当前帖里**滚到那一楼。
 * 所以这里不接网络层：`comments` 已经是这一帖的全部回帖（`PostThread`，见
 * `data/community/PostStore.kt:85-89`），过滤纯客户端做。
 *
 * ## 与参考的对齐与有意差异
 *
 *  - **结果行**：参考用一枚 `#N` 小胶囊标楼层（`search_post_card.dart:203-224`：`labelSmall` +
 *    `surfaceContainerHighest`、字 10）。这里拆成"定宽 40dp、右对齐的 `#N`"——面板窄，一列数字
 *    对齐之后个位成一条竖线，扫起来比一枚枚胶囊快（同一套排法见 `PostTocSheet.kt:404-412`）。
 *  - **命中高亮**：参考把服务端塞进来的 `<span class="search-highlight">` 解析成 span，给
 *    `primaryContainer` 底 + `onPrimaryContainer` 字 + `w500`（`search_post_card.dart:439-448`）。
 *    这里没有服务端标记，命中位置由 `indexOf(ignoreCase = true)` 现算；样式按本仓要求收成
 *    `primary` + `FontWeight.Medium`——**不铺底色**：一行摘要里压一块色带，整行看起来像"被选中"，
 *    而它只是"含有这个词"。
 *  - **状态数**：参考有未搜索 / 加载中 / 出错 / 无结果四态（`topic_search_view.dart:76-161`），
 *    这里只剩两态（未输入 / 无命中）：纯本地过滤没有"加载中"和"出错"可言。
 *
 * ## 为什么是自绘面板（而不是 `ModalBottomSheet`）
 *
 * 外壳逐项照本仓既有的贴底面板 `ComposerPickerSheet`（`PostsSection.kt:5860-5967`）：
 * `BasicAlertDialog(usePlatformDefaultWidth = false)` + 自绘 `Surface`（顶部圆角 24）+
 * 整屏遮罩 α0.32 + `navigationBarsPadding()`，输入框样式照它的标签输入格（`PostsSection.kt:5777-5816`）。
 * 唯一一个不一样的数是圆角：`ComposerPickerSheet` 是 28dp，这里 24dp（`PostTocSheet.kt:108` 也是 24，
 * 搜索面板比"选标签"矮一截，圆角跟着收一点才不显得头重）；其余数值两边一致，别单方面改一边。
 * 不用 `ModalBottomSheet` 的理由本仓已经踩过两次（`UploadPlanDialog` / `PlanScopeDialog` 都从它退回来，
 * 见 `PostsSection.kt:5845-5850`）：平台 sheet 会把**列表的滚动**抢去拖它自己，手指上滑想翻列表，
 * 结果是整块面板跟着走又被弹回一半。`PostTocSheet` 至今仍用 `ModalBottomSheet`（它自己的 KDoc 记了
 * 这条退路，`PostTocSheet.kt:72-79`），但那个面板里没有输入框；这个面板一打开就弹键盘，
 * 手势冲突的概率更高，所以直接走自绘那条、不再试一遍。
 *
 * ## 键盘
 *
 * `imePadding()` 垫在**面板内容**上而不是整个 `Dialog` 上（照 `PostsSection.kt:5904-5907`）：
 * 键盘弹起时只有内容区缩上去，遮罩与圆角不动，看起来才是"键盘把面板顶起来"。
 *
 * ## 命中之间「上一处 / 下一处」：为什么循环，而不是到头禁灰
 *
 * 输入框右侧挂着两颗 32dp 的小圆钮（`KeyboardArrowUp` / `KeyboardArrowDown`），夹着 `3 / 12`
 * 计数；按「下一处」越过最后一条就**回到第一条**，一直循环。
 *
 * 到头禁灰看起来更"安全"，实际是把一个问题拆成两个：按到按不动的那一刻，人分不清是"已经到底"
 * 还是"这条帖子里就这么点东西"，还得回头去读计数。而这个面板的列表本来就是个环 —— 第一条就在
 * 列表顶上，循环回去的成本只是一次滚动。键盘上那个「搜索」键也是「接着找下一条」的连续动作，
 * 一旦禁灰，连续按到一半就断在那里，得换手去点列表。
 *
 * 参考侧没有这条交互可抄：`topic_search_view.dart:167-182` 只报「共 N 条结果」，没有"第几条"
 * 这种下标概念，也就没有"到头"的语义；它那边跳到某一楼只有 tap 一条路（`:209-212`），
 * 这里多补的是键盘可达的步进。
 *
 * ## 为什么"选中"不自动跳楼层
 *
 * 选中（那一行铺一层 `primary` α0.12 的底）只是「我现在看的是这一条」的标记，**不**调 `onJump`。
 * 面板是盖在正文上的：一边打字一边被拽走，正文每滚一次屏幕就抖一下，人还看不清滚到了哪；而
 * "跳楼层"是这个面板唯一的重动作，只该由一次明确的点击触发。参考侧同样只在 tap 到卡片时才
 * `onJumpToPost(post.postNumber)`（`topic_search_view.dart:209-212`），没有任何"选中即跳"的路径。
 * 所以连按「下一处」到同一条（只有一条命中时会一直停在第 1 条）不产生额外的滚动或跳转 ——
 * 选中 ≠ 跳转。
 *
 * @param topicTitle 帖子标题：抬头写它，让人确认"我是在哪条帖子里找"。
 * @param comments 回帖，后端按时间正序（主楼**不在**里面，见 `PostThread`）。
 * @param floorLabelOf 楼层 id → 楼层号：主楼 1、第 i 条 i+2、找不到 -1
 *   （与 `PostsSection.kt:2886-2887` 是同一套编号）。
 * @param onJump 点一条结果：交出那一楼的 id（跳转动作在调用方落地，本面板不碰滚动）。
 * @param onDismiss 收起面板；面板自己先把输入焦点清掉，调用方只管状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostSearchSheet(
    topicTitle: String,
    comments: List<PostComment>,
    floorLabelOf: (Long) -> Int,
    onJump: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }

    // 收键盘放在**每一个出口**上，而不是只挂 `onDismissRequest`：遮罩、右上角 ×、点结果行
    // （先 `onJump` 再关）三条路都要收。漏一条就是"面板没了、键盘还杵在那儿挡住正文"。
    val close: () -> Unit = {
        focusManager.clearFocus()
        onDismiss()
    }

    // 自动聚焦：先把焦点要过来，键盘自己会跟着弹（`OutlinedTextField` 拿到焦点即请求 IME）。
    //
    // 先等一帧再请求：`FocusRequester` 在 modifier 还没挂上时调 `requestFocus()` 会抛
    // `IllegalStateException: FocusRequester is not initialized`，而对话框是"先组合、后布局"，
    // 同一个 `LaunchedEffect` 里直接请求正好撞进那个窗口。`withFrameNanos { }` 是等下一帧
    // （本仓已有同样的用法，见 `ChatScreen.kt:929`）。
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
    }

    // 过滤：`remember(comments, query)` —— 每帧重组都不重扫。回帖上限 200 条（见 `PostThread`
    // 的 KDoc），每条两次 `contains(ignoreCase = true)`；放在组合体里裸算就是每帧 400 次
    // 大小写折叠的字符串比较，滚动时明显掉帧。
    //
    // 匹配规则两条：正文**或**作者名包含关键词，大小写不敏感。不按词切分、不做正则：
    // 中文没有词边界，切了反而搜不到（参考侧同样是把整段摘要交给服务端标位置，
    // `topic_search_view.dart:164-182` 只数条数）。
    // 关键词本身（已 trim）：过滤、摘要、高亮都用这一份，免得到处再 trim 一遍、算成两份值。
    val needle = query.trim()
    val hits: List<PostComment> = remember(comments, query) {
        if (needle.isEmpty()) {
            emptyList<PostComment>()
        } else {
            comments.filter { comment ->
                comment.body.contains(needle, ignoreCase = true) ||
                    comment.author.contains(needle, ignoreCase = true)
            }
        }
    }
    // 只渲染前 [RESULT_LIMIT] 条：命中 200 条 × 每条一段 `buildAnnotatedString`，一次全铺出来
    // 在低端机上是肉眼可见的卡顿，而"翻到第 150 条命中"这种需求本来也罕见。
    val shown: List<PostComment> = remember(hits) { hits.take(RESULT_LIMIT) }

    // 结果列的滚动状态：导航到某一条时用它把那一行带进可见区。只滚这颗 `LazyColumn`，
    // 不碰正文页（见文件头「为什么选中不自动跳楼层」）。
    val listState = rememberLazyListState()

    // 当前选中的命中（`shown` 的 0 基下标）。键挂 `query`：关键词改一个字就回到第 1 条 ——
    // 换了词之后"第 3 条命中"指的就是另一批行了，留着旧下标只会选到一条碰巧同号的无关行。
    //
    // 用 `remember(query)` 而不是在 `LaunchedEffect` 里只写 `selected = 0`：状态对象跟着关键词
    // 一起换掉，换词的**那一帧**读到的就已经是 0，不会先画一次"还停在第 3 条、列表却已经换了内容"。
    var selected by remember(query) { mutableStateOf(0) }
    val hitTotal = shown.size
    // 分母取 `shown.size` 而不是 `hits.size`：导航只在**画出来的** ≤[RESULT_LIMIT] 行之间走，
    // 分母写 `hits.size` 会算出"201 / 250"这种跳不过去的序号（超出的那一行列表里根本没有）。
    // 头部 [SearchCountRow] 仍然报 `hits.size`（真命中数），两者管的是两件事。
    val canNavigate = hitTotal > 0
    // `shown` 只随 `comments` / `query` 变：前一种已经 coerce 过，后一种由上面的 `remember(query)`
    // 直接归零。这层 coerce 是兜底 —— 回帖被删、列表缩短而关键词没变时下标会越界。
    val selectedIndex = if (canNavigate) selected.coerceIn(0, hitTotal - 1) else -1
    // 选中行的 id：行内拿它比较（比传下标稳，`items` 的 key 本来就是 id）。
    val selectedId: Long? = if (selectedIndex >= 0) shown[selectedIndex].id else null

    // 换词：序号回 1 并跳回列表顶部。用 `scrollToItem`（无动画）—— 一边打字一边看列表滑一段，
    // 打完这个词之前那几次滑动全是噪音。
    LaunchedEffect(query) {
        selected = 0
        if (hitTotal > 0) listState.scrollToItem(0)
    }

    // 序号变了就把那一行带进视野。锚点仍然在列表里，不在正文页。
    LaunchedEffect(selectedIndex, hitTotal) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }

    // 「上一处 / 下一处」：到头**循环**回去，不禁灰（理由见文件头「为什么循环」）。只有一条命中时
    // 两边都原地不动，于是 `selected` 不变、上面那个滚动 effect 也不会重跑 —— 连按不会让列表抖。
    val goNext: () -> Unit = {
        if (canNavigate) selected = if (selectedIndex >= hitTotal - 1) 0 else selectedIndex + 1
    }
    val goPrev: () -> Unit = {
        if (canNavigate) selected = if (selectedIndex <= 0) hitTotal - 1 else selectedIndex - 1
    }

    BasicAlertDialog(
        onDismissRequest = close,
        // 对话框自己占满窗口：平台默认宽度只有屏幕中间一条，而结果行是按"整宽一行"排的
        // （手指从左点到右都该选中）。照 `PostsSection.kt:5871-5876`。
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 遮罩整屏铺满，面板是不透明的 `Surface`、天然挡住点击，所以铺满不会误关
            // （`PostsSection.kt:5877-5893` 记了改成"只铺上半截"踩过的死区）。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = close,
                    ),
            )
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
                        // 键盘高度垫在**内容**上（见文件头「键盘」一节）。
                        .imePadding(),
                ) {
                    SearchSheetTopBar(onClose = close)
                    SearchSheetTitle(title = topicTitle)
                    SearchInput(
                        query = query,
                        onQueryChange = { query = it.take(QUERY_MAX) },
                        focusRequester = focusRequester,
                        // 计数与两颗钮只认"画出来的那几行"：0 条命中时计数不画、两颗钮禁用。
                        position = selectedIndex + 1,
                        total = hitTotal,
                        onPrev = goPrev,
                        // 键盘上的「搜索」键也走这里：从"收键盘"改成**跳下一处** —— 边打边出
                        // 结果的时候，最顺手的下一步是"看下一条命中"，而收键盘本来也不是这里
                        // 缺的能力（点遮罩或 × 都会收，见 `close`）。配 `ImeAction.Search` 却
                        // 不接 `KeyboardActions` 的话按下去什么都不发生（同一套判断见
                        // `PostsSection.kt:5807-5813` 对 `ImeAction.Next` 的说明）。
                        onNext = goNext,
                    )
                    when {
                        // 空查询不画结果区，只给一句提示：一行"0 条命中"会让人以为搜过、没搜到。
                        needle.isEmpty() -> SearchHint(text = HINT_IDLE)
                        hits.isEmpty() -> SearchHint(text = HINT_EMPTY)
                        else -> {
                            SearchCountRow(count = hits.size)
                            LazyColumn(
                                // `heightIn(max = …)` 而不是 `weight(1f)`：面板高度是"内容多少就多高"，
                                // 三条命中不该把面板撑成半屏；超过 360dp 才开始滚（同族取值是
                                // `ComposerPickerSheet` 的 420dp，`PostsSection.kt:5940-5947`，
                                // 这里收小是为了键盘起来后输入框还留在屏幕上）。
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = RESULT_MAX_HEIGHT),
                                // 让「上一处 / 下一处」能把某一行滚进来（`animateScrollToItem`）。
                                state = listState,
                                contentPadding = PaddingValues(vertical = 4.dp),
                            ) {
                                // `key = { it.id }`：结果集随关键词变，行按 id 复用才不会把上一条
                                // 命中的高亮/摘要带到新的一行上（同族理由见 `PostTocSheet.kt:153-155`）。
                                items(shown, key = { it.id }) { comment ->
                                    // 楼层号与行文本都在行内 `remember`：
                                    //  - 楼层号按 `(id, floorLabelOf)` 缓存 —— `floorLabelOf` 内部是
                                    //    `indexOfFirst`，不缓存就是"每帧每行扫一遍 200 条"
                                    //    （`PostsSection.kt:3327-3336` 记过同一笔开销）；
                                    //  - 摘要与高亮按 `(id, query)` 缓存。回帖只有"发"和"删"两个接口
                                    //    （`PostStore.kt:91-104` 那六个 RPC），没有编辑，id 不变则正文不变，
                                    //    所以 id + 关键词就够做键，不必再把 body 列进去。
                                    //
                                    // **键里没有"当前序号"**：选中只是给这一行加一层底（`selected`），
                                    // 走的是一条不参与缓存的参数；按 ↑↓ 时这些 `remember` 全部命中，
                                    // 只重画背景色，不重算剥标记 / 找位置 / 切窗口。
                                    val floor = remember(comment.id, floorLabelOf) { floorLabelOf(comment.id) }
                                    val accent = scheme.primary
                                    val rowText = remember(comment.id, query) {
                                        rowTextOf(body = comment.body, author = comment.author, needle = needle)
                                    }
                                    val authorText = remember(comment.id, query, accent) {
                                        highlighted(text = comment.author, hit = rowText.authorHit, color = accent)
                                    }
                                    val summaryText = remember(comment.id, query, accent) {
                                        highlighted(text = rowText.snippet, hit = rowText.snippetHit, color = accent)
                                    }
                                    SearchResultRow(
                                        floorText = searchFloorText(floor),
                                        author = authorText,
                                        summary = summaryText,
                                        selected = comment.id == selectedId,
                                        onClickLabel = if (floor > 0) "跳到第 $floor 层" else "跳到这一楼",
                                        onClick = {
                                            focusManager.clearFocus()
                                            onJump(comment.id)
                                            onDismiss()
                                        },
                                    )
                                }
                                if (hits.size > RESULT_LIMIT) {
                                    item(key = LIMIT_FOOTER_KEY) { SearchLimitFooter() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 输入框里的占位文案。 */
private const val SEARCH_PLACEHOLDER = "在这条帖子里找…"

/** 还没输入时的提示（空查询不画结果区）。 */
private const val HINT_IDLE = "输入关键词开始搜索"

/** 有查询但一条没命中。 */
private const val HINT_EMPTY = "没有匹配的楼层"

/** 最多渲染多少条命中；再多就只画前这么多，末尾补一行说明。 */
private const val RESULT_LIMIT = 200

/**
 * 关键词长度上限：正文有几百字，粘一段进来会让 `contains` 与摘要窗口都变得没有意义。
 * 80 个字符足够装下任何"想在这帖里找的东西"。
 */
private const val QUERY_MAX = 80

/** 摘要窗口：命中位置**前后各**多少个字符（需求值，也正好一行放得下）。 */
private const val SNIPPET_PAD = 20

/** 正文里没命中（只命中作者名）时，摘要取开头多少个字符。 */
private const val SNIPPET_HEAD = SNIPPET_PAD * 2

/** 「只显示前 200 条」那一行在 `LazyColumn` 里的键（与回帖的 `Long` 键不会撞）。 */
private const val LIMIT_FOOTER_KEY = "search-limit"

/** 结果列表的最大高度：再高面板会把上面那条帖子整个盖住（见调用点注释）。 */
private val RESULT_MAX_HEIGHT = 360.dp

/** 命中那一段的字重：`Medium` 与参考的 `w500` 对齐（`search_post_card.dart:444`）。 */
private val HIT_WEIGHT = FontWeight.Medium

/** 「上一处 / 下一处」小圆钮的直径：比 40dp 的关闭钮小一档，它是输入框里的配件，不是面板级按钮。 */
private val NAV_BUTTON_SIZE = 32.dp

/** 清空钮的占位边长：沿用原来那颗 36dp，只是改成"永远占位"（见 [SearchInput] 的 KDoc）。 */
private val CLEAR_SLOT_SIZE = 36.dp

/**
 * `3 / 12` 那一格的最小宽度：`labelSmall` 下 5 个字符大约 34dp，留 44dp 让 `12 / 200` 也放得下。
 * 有了这块最小宽度，0 条命中（计数不画）与 3 条命中之间不会把两颗钮推着横移。
 */
private val COUNTER_MIN_WIDTH = 44.dp

/** 没有命中时两颗钮的透明度：与 Material 的 disabled 一档，看着是"现在没得翻"而不是"坏了"。 */
private const val NAV_DISABLED_ALPHA = 0.38f

/**
 * 选中那一行的底色透明度。
 *
 * 0.12 是"一眼看得出、又不至于被当成正文高亮"的那一档：命中词本身用的是 `primary` **字色**
 * （`FontWeight.Medium`，见 [highlighted]），底色再重就会盖过它。整行铺淡底而不是画一条边：
 * 行是 48dp 高的可点区域，边上一条线在滚动时容易和分隔线混在一起。
 */
private const val SELECTED_ROW_ALPHA = 0.12f

// ------------------------------------------------------------------ 文本剥标记
//
// 全部提到文件级：`Regex` 的构造要编译一次模式，放进 composable 里就是每帧编译一遍
// （同一个做法见 `PostTocSheet.kt:190-205`）。顺序在 [stripMarkdown] 里是有讲究的，改之前先读那里。

private val MD_IMAGE_INLINE = Regex("""!\[([^\]]*)]\([^)]*\)""")
private val MD_LINK_INLINE = Regex("""\[([^\]]*)]\([^)]*\)""")
private val MD_FENCE_LINE = Regex("""^\s*(```|~~~).*$""")
private val MD_THEMATIC_BREAK = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
private val MD_ATX_HEADING = Regex("""^\s{0,3}#{1,6}\s*""")
private val MD_BLOCK_QUOTE = Regex("""^\s{0,3}>\s?""")
private val MD_BULLET_MARK = Regex("""^\s{0,3}([-*+]|\d{1,3}[.)])\s+""")
private val MD_HTML_TAG = Regex("""<[^>]*>""")
private val MD_CODE_TICK = Regex("""`+""")
private val MD_EMPHASIS_MARK = Regex("""[*_~]{1,3}""")
private val MD_RUN_OF_SPACE = Regex("""\s+""")

/**
 * 把一份 Markdown 正文压成一行纯文字，供摘要取窗口用。
 *
 * 为什么不复用 `MiniMarkdown`：渲染器给的是节点树，从树里"按字符下标取前后 20 字"得先把节点
 * 拼回字符串，等于多绕一圈；这里只需要**字符位置对得上原文**，所以按行扫、删标记字符最直接。
 *
 * 与 `PostTocSheet.plainSummary`（`PostTocSheet.kt:227-254`）的差别只有一个，但很关键：那个函数
 * **只取第一段有字的行**（目录要"一眼认出哪一楼"），这里要**整篇拼平**——命中可能在第 5 段，
 * 只取第一行就会得到一段不含关键词的摘要，看的人不知道为什么这行会被搜出来。
 *
 * 边界（纯代码块 / 纯链接 / 空正文都不崩）：
 *  - 空串、纯空白 → `""`；
 *  - 围栏那一行（``` ``` / ```kotlin ）整行丢掉，围栏**里面**的行当普通文字处理 ——
 *    纯代码块于是得到的是代码本身（比一片空白有用）；
 *  - 图片留 alt、链接只留文字、裸 URL 不动；HTML 标签删掉；
 *  - 整篇都是标记（纯图片、纯分隔线）时返回 `""`，调用方据此画"只有作者名的一行"。
 */
private fun stripMarkdown(markdown: String): String {
    if (markdown.isBlank()) return ""
    val flat = buildString {
        for (raw in markdown.lineSequence()) {
            if (MD_FENCE_LINE.matches(raw)) continue
            // 先把"图"和"链接"换成它们的**文字**，再删别的标记：顺序反了的话 `[a](u)` 里的
            // `]` / `)` 先被当标点留下，摘要里会冒出 `a(u` 这种半截。
            var line = MD_IMAGE_INLINE.replace(raw) { it.groupValues[1] }
            line = MD_LINK_INLINE.replace(line) { it.groupValues[1] }
            line = MD_HTML_TAG.replace(line, "")
            if (MD_THEMATIC_BREAK.matches(line)) continue
            line = MD_ATX_HEADING.replace(line, "")
            line = MD_BLOCK_QUOTE.replace(line, "")
            line = MD_BULLET_MARK.replace(line, "")
            line = MD_CODE_TICK.replace(line, "")
            line = MD_EMPHASIS_MARK.replace(line, "")
            if (line.isBlank()) continue
            append(line.trim()).append(' ')
        }
    }
    return MD_RUN_OF_SPACE.replace(flat, " ").trim()
}

/**
 * 一行结果要画的两段文字（都已算好命中位置）。
 *
 * 为什么先算成数据、而不是在 composable 里现算：摘要窗口是"剥标记 + 找位置 + 切窗口 + 加省略号"
 * 四步字符串运算，一次重组里每行都要，包进 `remember` 才不用每帧重算。
 *
 * @param snippet 已剥标记、已截成"命中前后各 [SNIPPET_PAD] 字符"的摘要（可能带首尾 `…`）。
 * @param snippetHit 命中在摘要里的位置；null 表示这一行是靠作者名命中的（或只剩标记）。
 * @param authorHit 命中在作者名里的位置；null 表示作者名不含关键词。
 */
private class RowText(
    val snippet: String,
    val snippetHit: IntRange?,
    val authorHit: IntRange?,
)

/**
 * 算出一行结果的摘要与两处命中位置。
 *
 * 摘要窗口取"命中位置前后各 [SNIPPET_PAD] 个字符"，并在**真的被截断的那一侧**加一个 `…`
 * （两侧都没截就一个都不加：明明没省略却挂着省略号，人会以为正文就这么短）。高亮范围是相对
 * 截好的那一段算的，所以加省略号时后面那个下标要跟着 +1 —— 这是这里唯一容易错一拍的地方。
 *
 * @param needle 已 trim 的关键词；空串时不做命中判断（调用方在空查询时根本不画结果区）。
 */
private fun rowTextOf(body: String, author: String, needle: String): RowText {
    val authorIndex = if (needle.isEmpty()) -1 else author.indexOf(needle, ignoreCase = true)
    val authorHit = if (authorIndex >= 0) authorIndex until authorIndex + needle.length else null

    val plain = stripMarkdown(body)
    if (needle.isEmpty() || plain.isEmpty()) return RowText(plain, null, authorHit)

    val hit = plain.indexOf(needle, ignoreCase = true)
    if (hit < 0) {
        // 正文里没有：要么命中在作者名上，要么只命中 Markdown 源码（比如链接 URL 里的词，
        // 剥完标记就没了）。这时给开头一段、不加高亮 —— 总比一整行只有作者名有信息量。
        val head = if (plain.length <= SNIPPET_HEAD) {
            plain
        } else {
            plain.take(SNIPPET_HEAD - 1).trimEnd() + "…"
        }
        return RowText(head, null, authorHit)
    }

    val from = (hit - SNIPPET_PAD).coerceAtLeast(0)
    val to = (hit + needle.length + SNIPPET_PAD).coerceAtMost(plain.length)
    val cutHead = from > 0
    // 用 `buildString` 拼：`+` 三次会造四个中间串，而这段每行都要算一遍。
    val snippet = buildString {
        if (cutHead) append('…')
        append(plain, from, to)
        if (to < plain.length) append('…')
    }
    val start = (if (cutHead) 1 else 0) + (hit - from)
    return RowText(snippet, start until start + needle.length, authorHit)
}

/**
 * 把 `text` 里 `hit` 那一段染成 `color` + [HIT_WEIGHT]，其余照画。
 *
 * 用 `buildAnnotatedString` 而不是"把命中的词切开拼三个 `Text`"：一行是 `maxLines = 1` 的省略行，
 * 拼三段就变成三个各自省略的 `Text`，中间还得自己算宽度；`AnnotatedString` 是同一个排版对象，
 * 省略与折行只在一处发生（参考侧同样走 span，`search_post_card.dart:439-448`）。
 *
 * `hit` 越界或为空时整段照画、不抛：摘要被截过、关键词恰好落在 `…` 之外，这些都不是异常情况。
 */
private fun highlighted(text: String, hit: IntRange?, color: Color): AnnotatedString =
    buildAnnotatedString {
        if (hit == null || hit.isEmpty() || hit.first < 0 || hit.last >= text.length) {
            append(text)
            return@buildAnnotatedString
        }
        if (hit.first > 0) append(text.substring(0, hit.first))
        withStyle(SpanStyle(color = color, fontWeight = HIT_WEIGHT)) {
            append(text.substring(hit.first, hit.last + 1))
        }
        if (hit.last + 1 < text.length) append(text.substring(hit.last + 1))
    }

/**
 * 楼层号文字的写法：`#N`；`floorLabelOf` 说找不到（-1）时写一个破折号。
 *
 * 不写 `#-1`：那是把"查不到"当成一个楼层号画出来，人会以为真有一层叫 -1
 * （同一判断见 `PostTocSheet.kt:256-262` 与 `PostsSection.kt:3910-3911` 的引用指示条）。
 */
private fun searchFloorText(floor: Int): String = if (floor > 0) "#$floor" else "—"

/**
 * 面板最上面那一条：中间一颗抓手、右边一颗 ×。
 *
 * 抓手 40×4、`onSurfaceVariant` α0.3 —— 与 `ComposerPickerSheet` 完全同一组数
 * （`PostsSection.kt:5909-5917`），这两个面板在同一页里出现，抓手粗细不一样会像是两套组件。
 * × 是 40dp 的点击格（`PostsSection.kt:5918-5928`）：面板盖住了正文，得有一眼能看见的出口。
 */
@Composable
private fun SearchSheetTopBar(onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
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
                .clickable(role = Role.Button, onClickLabel = "关闭", onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
        }
    }
}

/**
 * 抬头：帖子标题。
 *
 * 单行省略 + 16sp `SemiBold`，不是 `ComposerPickerSheet` 那个 20sp `Bold`：那边写的是面板自己的
 * 名字（「标签」「分类」两三个字），这里写的是**用户自己的帖子标题**，可以长到两行；给 20sp 会把
 * 输入框一路推到面板下半截，键盘一起来就顶到屏幕外。它的作用只有一个：确认"我在哪条帖子里找"。
 */
@Composable
private fun SearchSheetTitle(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
    )
}

/**
 * 那一格真输入框：左放大镜，右侧从右往左是「清空」→「下一处」→ 计数 →「上一处」。
 *
 * 样式照 `ComposerPickerSheet` 里的标签输入格（`PostsSection.kt:5777-5816`）：`singleLine` +
 * `bodyMedium` + 边框 `outlineVariant`（未聚焦再淡一档）。差别三处：
 *  - 清空按钮在这个面板里是**必需**的（搜索词是临时状态，没有"提交"这一步来清空它）；
 *  - 右边多了「上一处 / 下一处」两颗 32dp 小圆钮和 `3 / 12` 计数（见文件头「为什么循环」）；
 *  - 左边那颗放大镜是装饰，`contentDescription = null` —— 它不承载动作，念屏器念它就多余了。
 *
 * **两颗钮放在清空按钮左边**，清空仍然钉在最右：那是它现在的位置（`PostsSection.kt:5813` 起
 * 同一套排法），一次改动不该顺手把手感挪走；而两颗钮要成组（↑ 计数 ↓），贴着清空右边放会把
 * "清空"夹进这组中间、读起来像第三颗导航钮。清空外壳固定占 36dp、只在有字时才画出来，
 * 于是打字第一下不会把 ↑↓ 推着横移。
 *
 * @param focusRequester 由调用方创建并自动请求一次焦点（见 [PostSearchSheet] 的 `LaunchedEffect`）。
 * @param position 当前是第几条命中（1 基）；`<= 0` 表示没有选中（此时计数不画）。
 * @param total 可导航的命中条数；0 时两颗钮禁用（α0.38）。
 * @param onPrev / @param onNext 上一处 / 下一处，到头循环（不禁灰）；[onNext] 同时挂在键盘的
 *   「搜索」键上（[ImeAction.Search] + `KeyboardActions`）。
 */
@Composable
private fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    position: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .focusRequester(focusRequester),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                text = SEARCH_PLACEHOLDER,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
        },
        trailingIcon = {
            Row(
                modifier = Modifier.padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchNavButton(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    onClickLabel = "上一处",
                    enabled = total > 0,
                    onClick = onPrev,
                )
                // 计数夹在两颗钮中间（`3 / 12`），`labelSmall` + `onSurfaceVariant`：它是导航的
                // 读数，不是标题，字号与颜色都跟着结果区那行「N 条命中」走。没有命中时**不画**，
                // 但这一格带一个最小宽度（[COUNTER_MIN_WIDTH]）：否则命中一出现，两颗钮会横移一格。
                Text(
                    text = if (total > 0) "$position / $total" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .widthIn(min = COUNTER_MIN_WIDTH)
                        .padding(horizontal = 2.dp),
                )
                SearchNavButton(
                    icon = Icons.Rounded.KeyboardArrowDown,
                    onClickLabel = "下一处",
                    enabled = total > 0,
                    onClick = onNext,
                )
                // 清空：外壳永远占 36dp（见 KDoc），有字时才画那颗 ×，空框上不留"按了没反应"的钮。
                Box(modifier = Modifier.size(CLEAR_SLOT_SIZE), contentAlignment = Alignment.Center) {
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(CLEAR_SLOT_SIZE)
                                .clip(RoundedCornerShape(50))
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "清空",
                                    onClick = { onQueryChange("") },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onNext() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = scheme.outlineVariant,
            unfocusedBorderColor = scheme.outlineVariant.copy(alpha = 0.6f),
        ),
    )
}

/**
 * 输入框里那颗「上一处 / 下一处」小圆钮：32dp 的圆、`onSurfaceVariant` 前景。
 *
 * 自己用 `Box` + `clickable` 拼，不用 `IconButton`：`IconButton` 会把最小触控尺寸撑到 48dp
 * （`minimumInteractiveComponentSize`），两颗并排就吃掉 96dp 的输入框宽度，写字的格子剩不了多少。
 * 同一套自绘见右上角那颗 ×（[SearchSheetTopBar]，`PostsSection.kt:5918-5928`）。
 *
 * 禁用态只降透明度、**不**加一层灰底：面板上本来就没有"禁用样式"的第二种画法，跟着父级的
 * `onSurfaceVariant` 淡下去最省事，也不会在深色主题里冒出一块浅色块。
 *
 * @param icon 图标（调用方显式传 `Icons.Rounded.KeyboardArrowUp` / `KeyboardArrowDown`）。
 * @param onClickLabel 无障碍读出来的动作名（「上一处」/「下一处」）。
 * @param enabled 没有命中时为 false：点了没反应，也不吃水波纹。
 */
@Composable
private fun SearchNavButton(
    icon: ImageVector,
    onClickLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(NAV_BUTTON_SIZE)
            .clip(RoundedCornerShape(50))
            .clickable(
                role = Role.Button,
                onClickLabel = onClickLabel,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = scheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else NAV_DISABLED_ALPHA),
        )
    }
}

/**
 * 结果区顶上那一行「N 条命中」。
 *
 * `labelSmall` + `onSurfaceVariant`、左右 20dp，与抬头同一条竖线：它是**次要信息**（告诉人
 * "下面这一列有多少"），不该和楼层号抢注意力。参考侧也是同一种排法
 * （`topic_search_view.dart:164-182` 的 `search_resultCount`，用 `outline` 色的小字）。
 *
 * 计的是**命中的总数**，不是渲染出来的条数：超过 [RESULT_LIMIT] 时下面还有一行说明
 * （[SearchLimitFooter]），两者分开放才不会让人以为"只有 200 条"。
 */
@Composable
private fun SearchCountRow(count: Int) {
    Text(
        text = "$count 条命中",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 4.dp),
    )
}

/**
 * 一行结果：`#N` → 作者 → 正文摘要。
 *
 * 几何与 `PostTocSheet.TocEntryRow`（`PostTocSheet.kt:374-445`）对齐：最小触控高 48dp、
 * 行内左右 8dp、整行可点。三格的排法与理由：
 *  - `#N` **定宽 40dp、右对齐**：位数不一样（`#9` vs `#120`），右对齐之后个位对齐，扫下来是
 *    一绺整齐的数字；不定宽则每行作者名的左边缘都跟着抖。
 *  - 作者名 `labelMedium`、摘要 `bodySmall` + `onSurfaceVariant`：两行小字，主次由字号与颜色
 *    分出来，不靠缩进；摘要 `maxLines = 1`（行高要恒定，否则滚动时一列行在跳）。
 *
 * 点下去是**跳楼层**而不是展开：这个面板的全部意义是"带我过去"，跳完就关
 *（参考侧 `onJumpToPost(post.postNumber)` 之后同样是离开结果视图）。
 *
 * @param floorText 楼层号文字（`#N` / `—`，见 [searchFloorText]）。
 * @param author 作者名（已带高亮）。
 * @param summary 摘要（已带高亮）；空串时这一格不画（纯代码块/纯图片的正文剥完就是空）。
 * @param selected 是不是「上一处 / 下一处」当前指到的那一行：只多铺一层 `primary` α0.12 的底，
 *   不改变任何文字 —— 命中词自己已经用 `primary` 字色标出来了，选中若也改字色，两者就分不开。
 * @param onClickLabel 无障碍读出来的动作名。
 */
@Composable
private fun SearchResultRow(
    floorText: String,
    author: AnnotatedString,
    summary: AnnotatedString,
    selected: Boolean,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            // `clip` 必须在 `clickable` **之前**：水波纹跟着 8dp 圆角走，否则点下去是一块方角
            // 高亮压在圆角底上（同一顺序见 `PostTocSheet.kt:389-399`）。
            // 选中底色夹在两者中间：先被 `clip` 裁成 8dp 圆角，又在水波纹**下面**（水波纹盖在
            // 底色上才看得见），所以顺序只能是 clip → background → clickable。
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) scheme.primary.copy(alpha = SELECTED_ROW_ALPHA) else Color.Transparent,
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
            // 作者名只在非空时画：空一行会让这一行比别的行矮一截、垂直居中看着偏
            //（`PostTocSheet.kt:416-424` 同一个判断）。
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
    }
}

/**
 * 命中超过 [RESULT_LIMIT] 时列表末尾那一行「只显示前 200 条」。
 *
 * 写在列表**里面**而不是外面：外面那行会一直挂在可见区，看起来像"面板的脚注"；放在末尾，
 * 只有真的滚到底才出现，才读得成"到此为止，后面还有"。
 */
@Composable
private fun SearchLimitFooter() {
    Text(
        text = "只显示前 $RESULT_LIMIT 条",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    )
}

/**
 * 结果区的两种"没有内容"：还没输入 / 一条没命中。
 *
 * 拿 `heightIn(min = 120.dp)` 撑出一块再居中，而不是贴着输入框下面写一行：一行字紧贴输入框会
 * 让人以为"下面还在加载"。参考侧这两种态也是整块居中，还带一枚大图标
 * （`topic_search_view.dart:76-98` / `:134-161`）——这里去掉图标，面板本来就矮，
 * 一枚 64dp 的灰图标会把面板撑到半屏（与 `PostTocSheet.TocEmptyHint` 去掉图标的理由一致，
 * `PostTocSheet.kt:447-462`）。
 */
@Composable
private fun SearchHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}


