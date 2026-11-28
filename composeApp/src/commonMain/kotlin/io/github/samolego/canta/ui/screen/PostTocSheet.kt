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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.data.community.PostComment
import kotlinx.coroutines.delay

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
 * 想看得更全再往上拖。**列表自己滚**（`LazyColumn` + `heightIn(max = 420.dp)`，见下文
 * 「为什么用 heightIn 而不是 weight」），不吃面板的拖动手势 ——
 * 本仓另外两个从底下推出来的面板（`UploadPlanDialog` / `PlanScopeDialog`）曾因"上滑翻列表
 * 反把整块面板拖走"退回成自绘对话框（`PostsSection.kt:5208-5213`）；这个面板的列表只在
 * sheet 内部滚，若日后真的出现抢手势，退路是保留下面全部结构与数值、只把外壳换成
 * `ComposerPickerSheet` 那套 `BasicAlertDialog`，行与标题都不用动。
 *
 * ## 关键词过滤
 *
 * 标题下面一个 `OutlinedTextField`（长相照 `PostSearchSheet.kt:643-718` 的那一处），边打边过滤，
 * 不查库、不跨帖、不弹第二个面板。
 *
 * **为什么主楼那一行永远不被过滤掉**：主楼不在 `comments` 里（见参数说明），它没有作者、没有
 * 正文可以匹配 —— 拿关键词去筛它只会得到"永远不命中"，于是**有关键词的时候**主楼会从目录上
 * 消失。可它在目录里的作用是"回到帖子开头"，是一件与"找楼层"无关的动作，不该因为搜索词而
 * 失去入口；而且它固定在列表第一项，占的是一行的高度，留着不会让命中列表变难读。所以过滤
 * 只作用于回帖，主楼行照常在顶上（也不计入「命中」）。
 *
 * 匹配对象是**作者名 + 正文原 Markdown**（不是 [plainSummary] 的产物：摘要被截到 40 字，
 * 拿它匹配会漏掉第 41 字之后的内容）。`contains(ignoreCase = true)`，不切词、不上正则 ——
 * 中文没有词边界，切了反而搜不到（同一套取舍见 `PostSearchSheet.kt:180-183`）。
 *
 * ## 跳转期间冻结高亮
 *
 * 点一行先记 `jumpingTo`，`600ms` 内高亮只认它，不跟 `currentFloorId`：
 * 跳转是"一路滚过去"的，滚动途中 scroll-spy 会把视口扫过的每一个中间楼层依次报成当前楼层，
 * 不冻结的话高亮就从第 3 层一路闪到第 30 层，最后才停在目标上。参考侧对这同一件事的做法是
 * 跳转期间直接**不重算**激活项：`topic_toc_controller.dart:69-71`（`isJumping` 的注释就写着
 * "不冻结会闪隐"）、`:217`（`scheduleSpyUpdate` 里 `if (!hasToc || isJumping) return`）、
 * `:229`（`updateActiveHeading` 里 `if (_jumpTargetId != null) return`）。这里没有"滚动结束"
 * 事件可以挂（滚动发生在调用方 `PostsSection.kt`，本面板参数里拿不到滚动状态），所以退一步用
 * **估算的时长**：600ms 是普通列表一次跳转的落地时间，**不是精确同步**，它唯一的目标是把滚动
 * 途中扫过的那些中间态压掉；真落地之后哪怕再晚一点解除冻结，`currentFloorId` 也已经等于目标
 * 楼层，解除那一下不会跳变。到点由 `LaunchedEffect(jumpingTo)` + `delay` 清空。
 *
 * ## 为什么用 `heightIn` 而不是 `weight(1f)`
 *
 * 输入框占掉一行的位置之后，列表再拿 `weight(1f)` 就会把面板顶到半屏以上：`ModalBottomSheet`
 * 的 `weight` 吃的是"半屏还剩多少"，内容一多就一路填满，于是三层楼的帖子也会弹成一块半屏的
 * 空板。改成 `heightIn(max = 420.dp)` 是"内容多少就多高、超过 420dp 才开始滚"，与
 * `PostSearchSheet.kt:298-305` 的结果列同一个写法（那一处收小到 360dp 是为了键盘起来后输入框
 * 还留在屏幕上；这里 420 与本仓 `ComposerPickerSheet` 的弹层同高，`PostsSection.kt:5940-5947`）。
 *
 * 键盘：面板里有输入框，所以 `ModalBottomSheet` 的 modifier 上挂了 `imePadding()` ——
 * 键盘起来时整块面板抬到键盘上沿。**不**把垫子挂到内容上：内容里空态那句 `weight(1f)`
 * 会跟着变化（见下），挂外层只改可用高度、内容排版一个字不用动。
 *
 * @param comments 回帖，后端按时间正序（主楼**不在**里面，见 `PostThread`）。
 * @param floorLabelOf 楼层 id → 楼层号：主楼是 1、第 i 条回复是 i+2、找不到是 -1
 *   （`PostsSection.kt:2811-2813` 的同一套编号）。
 * @param currentFloorId 当前视口所在楼层；`0L` 表示"在主楼 / 还不知道"。它是**跟随值**：
 *   面板只在没有 `jumpingTo` 的时候把它画成高亮（见上文「跳转期间冻结高亮」）。
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
    val focusManager = LocalFocusManager.current

    // 关键词：面板自己持有，不放调用方 —— 它只影响这一列怎么画，跳楼层与正文页都不需要知道
    // （与 `PostSearchSheet.kt:156` 同一个所有权判断）。
    var query by remember { mutableStateOf("") }
    // 关键词本身（已 trim）：过滤与「命中 M」共用这一份，免得到处再 trim 一遍、算成两份值
    // （同一套写法见 `PostSearchSheet.kt:184`）。
    val needle = query.trim()
    val filtering = needle.isNotEmpty()

    // 过滤：`remember(comments, query)` —— 每帧重组都不重扫。回帖上限 200 条（见 `PostThread`
    // 的 KDoc），每条两次 `contains(ignoreCase = true)`；放在组合体里裸算就是每帧 400 次
    // 大小写折叠的字符串比较，打字时明显掉帧（同一笔开销、同一个键见
    // `PostSearchSheet.kt:176-194`）。
    //
    // 键里**没有** `floorLabelOf` / `currentFloorId`：过滤只看"哪一层里有关键词"，与楼层号、
    // 与视口在哪一层都无关；把它们挂上去，滚动一次就得重扫整张表。
    val hits: List<PostComment> = remember(comments, query) {
        if (needle.isEmpty()) {
            comments
        } else {
            comments.filter { comment ->
                // 正文按**原 Markdown** 匹配，不是剥好的摘要（理由见文件头「关键词过滤」）。
                comment.body.contains(needle, ignoreCase = true) ||
                    comment.author.contains(needle, ignoreCase = true)
            }
        }
    }

    // 跳转期间冻结高亮（理由与参考行号见文件头「跳转期间冻结高亮」）：`null` = 没在跳，
    // 高亮照常跟 `currentFloorId`；非 `null` = 正在跳，高亮钉在这一项上（`0L` 是主楼）。
    var jumpingTo by remember { mutableStateOf<Long?>(null) }
    val highlightedId: Long = jumpingTo ?: currentFloorId

    // 到点解除冻结。键挂 `jumpingTo`：连点两行时计时器跟着**新目标**重开，不会出现"第一行的
    // 那份 600ms 先把第二行的冻结解除掉"（参考侧对同一类交叠是靠自增 generation 挡的，
    // `topic_toc_controller.dart:167` 与 `:184-186`）。
    LaunchedEffect(jumpingTo) {
        if (jumpingTo != null) {
            delay(JUMP_FREEZE_MS)
            jumpingTo = null
        }
    }

    // 所有出口共用一个跳转动作：收键盘 + 记冻结 + 交 id + 收面板。三条路（主楼 / 回帖行 /
    // 空列表时那颗主楼行）各写一遍就等于给"漏掉 freeze"留了三次机会。
    val jump: (Long) -> Unit = { id ->
        focusManager.clearFocus()
        jumpingTo = id
        onJump(id)
        onDismiss()
    }

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
        // 键盘垫**挂在整个 sheet 上**：面板里现在有输入框，键盘起来时若不给这块让位，
        // 焦点那一行会被`ime`盖住（sheet 自己不会替内容算键盘高度）。
        // 挂在外壳而不是内容上，是为了不动空态那句 `weight(1f)`（见上面那段注释）：
        // `.imePadding()` 只改外层可用高度，内容怎么排一个字都不用改。
        modifier = Modifier.fillMaxWidth().imePadding(),
    ) {
        TocDragHandle()
        // 「N 层」含主楼：楼层号从主楼的 #1 起算，所以总层数 = 回帖数 + 1。
        //
        // `hitCount` 只有"有关键词**且**确实有回帖可筛"时才给：一条回复都没有时输入框里打什么
        // 都不会命中，写个「命中 0」只会让人以为搜索坏了（那一支的结论是「还没有回复」，
        // 见下面的 [TocEmptyHint]）。命中数**不含主楼** —— 主楼不参与过滤，也就没有"命中"一说。
        TocSheetHeader(
            floorCount = comments.size + 1,
            hitCount = if (filtering && comments.isNotEmpty()) hits.size else null,
        )
        TocSearchField(
            query = query,
            onQueryChange = { query = it },
            // 边打边过滤，所以键盘上那颗「搜索」键没有"再搜一次"要触发的事：它只收键盘。
            // 接了 `ImeAction.Search` 却不接 `KeyboardActions` 的话按下去什么都不发生
            // （同一条判断见 `PostSearchSheet.kt:285-291`，那边按键是"跳下一处"）。
            onSearch = { focusManager.clearFocus() },
        )

        if (comments.isEmpty()) {
            // 主楼行照旧给：一条回复都没有时，"回主楼"仍然是有意义的动作。
            TocOpRow(current = highlightedId == 0L, onClick = { jump(0L) })
            TocEmptyHint(modifier = Modifier.fillMaxWidth().weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    // `heightIn(max = …)` 而不是 `weight(1f)`：理由见文件头「为什么用 heightIn」。
                    // 一句话：输入框已经占掉一行，`weight(1f)` 会接着把面板撑到半屏以上。
                    .heightIn(max = LIST_MAX_HEIGHT)
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
                //
                // 它**不在**过滤范围内：有关键词时也照常画（理由见文件头「关键词过滤」）。
                item(key = OP_KEY) {
                    TocOpRow(current = highlightedId == 0L, onClick = { jump(0L) })
                }
                if (hits.isEmpty()) {
                    // 只有主楼 + 这一行。放在列表里而不是列表外：面板高度就由"两行 + 这一行"
                    // 决定，不用为"无命中"再长出一套高度逻辑。
                    item(key = NO_MATCH_KEY) { TocNoMatchHint() }
                } else {
                    // `key = { it.id }`：列表刷新换血后行位置会变，按 id 复用才能让
                    // "当前楼层"那颗圆点跟着楼层走，而不是跟着第 N 个槽位走
                    // （同一套理由见 `PostsSection.kt:2799-2800`）。换关键词时命中的是另一批
                    // id，复用同样按 id 落到对的槽位上。
                    items(hits, key = { it.id }) { comment ->
                        val floor = floorLabelOf(comment.id)
                        // 摘要剥标记 + 截断是纯字符串运算，但正文有几百字、正则也有十来条：
                        // 包在 `remember(comment.body)` 里，滚动时每帧重组不再重算
                        // （参考侧是靠渲染缓存省这笔，`topic_toc_controller.dart:104-110`）。
                        //
                        // **键里只有 `comment.body`，不含 `query`**：摘要只由正文决定，把关键词
                        // 挂上去等于"每敲一个字，把画出来的每条摘要全部重剥一遍"。这里不涂高亮
                        // （摘要只是"认出这是哪一楼"），所以不需要像
                        // `PostSearchSheet.kt:326-334` 那样按 `(id, query)` 缓存带命中色的文本。
                        val summary = remember(comment.body) { plainSummary(comment.body) }
                        TocEntryRow(
                            floorText = floorTextOf(floor),
                            author = comment.author,
                            summary = summary,
                            // 找不到的那一层（`floorLabelOf` 给 -1）与"还没定"的 0L 都不会命中任何一行：
                            // 圆点只在真正对得上时出现，不编一个"你在第 0 层"。
                            // 跳转途中读的是 `jumpingTo` 而不是 `currentFloorId`（见文件头）。
                            current = floor > 0 && comment.id == highlightedId,
                            onClickLabel = "跳到第 $floor 层",
                            onClick = { jump(comment.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 主楼那一行的楼层号写法：参考里主楼也是 `#1`，这里写「主楼」是因为面板里只有它不在 [PostTocSheet] 的 `comments` 里。 */
private const val OP_FLOOR_TEXT = "主楼"

/** 主楼行在 `LazyColumn` 里的键（与回帖的 `Long` 键区分开、不会撞）。 */
private const val OP_KEY = "toc-op"

/** 无命中那一行在 `LazyColumn` 里的键（同样与回帖的 `Long` 键区分开）。 */
private const val NO_MATCH_KEY = "toc-no-match"

/** 摘要字符上限（含省略号，见 [plainSummary]）。 */
private const val SUMMARY_MAX = 40

/** 过滤框的占位文案。与搜索面板的「在这条帖子里找…」（`PostSearchSheet.kt:361`）差一个字：这里找的是**楼层**。 */
private const val TOC_SEARCH_PLACEHOLDER = "在这条帖子的楼层里找…"

/** 有关键词但一条回帖都没命中时那一行。 */
private const val TOC_NO_MATCH_TEXT = "没有匹配的楼层"

/**
 * 跳转期间冻结高亮的时长（毫秒）。
 *
 * 600ms 是**估算**：普通列表一次跳转落地（滚动动画 + 一帧排版）大致在这个量级。它**不是**
 * 精确同步 —— 本面板拿不到"滚动已结束"的信号（滚动在调用方做，参数里只有 `currentFloorId`）。
 * 它唯一要买到的东西是"把滚动途中扫过的中间楼层压掉"；早一点解除不会出错（那时
 * `currentFloorId` 还没落地，会闪一下中间态），晚一点解除也只是让高亮多停 100ms 在目标上。
 */
private const val JUMP_FREEZE_MS = 600L

/**
 * 列表最高 [420.dp]（理由见文件头「为什么用 heightIn」）。
 *
 * 420 与本仓底部弹层的同族取值一致（`ComposerPickerSheet`，`PostsSection.kt:5940-5947`）：
 * 手机竖屏半屏约 400–450dp，这个上限刚好让面板"一次能看到七八层、再长就自己滚"。
 */
private val LIST_MAX_HEIGHT = 420.dp

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
 * 有关键词时计数变成「N 层 · 命中 M」：层数是**这一帖一共多少层**（不随过滤变），命中是
 * **画出来的回帖行数**（不含主楼）。两个数用同一个 `labelSmall`、同一行：它们是同一条读数，
 * 分成两块反而要人自己对。无命中时也照写「命中 0」—— 那是"什么都没找到"的唯一证据，列表那边
 * 还会再给一行 [TOC_NO_MATCH_TEXT]。
 *
 * @param floorCount 总层数（含主楼），由调用点算好传进来。
 * @param hitCount 命中数；`null` = 没有在过滤（此时只写「N 层」）。
 */
@Composable
private fun TocSheetHeader(floorCount: Int, hitCount: Int?) {
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
            // 单个 `Text` 拼完再画，而不是「层数」「·」「命中」三个文本节点：三个节点各自
            // 参与一次测量、还要靠 `Row` 对齐，而这一行本来就是一句话。
            text = if (hitCount == null) "$floorCount 层" else "$floorCount 层 · 命中 $hitCount",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 过滤框：标题下面那一行，行为与长相照 `PostSearchSheet.kt:643-718` 的那一处。
 *
 * 只做三件事：
 *  - `singleLine` + `ImeAction.Search`：它是"找楼层"，不是写正文，回车不收行；
 *  - 有字才画清空键：空框上摆一颗按了没反应的 ×，看起来像坏了（同一条规则见
 *    `PostSearchSheet.kt:692-709`；那边为了不把 ↑↓ 推着横移留了个固定 36dp 的外壳，
 *    这里没有别的钮，不需要占位）；
 *  - 边打边过滤，所以「搜索」键只收键盘（[onSearch]）。
 *
 * 左右 16dp 比标题行的 20dp 收 4dp：`OutlinedTextField` 自带一圈内边距，视觉上对齐到同一条线。
 *
 * @param query 当前关键词（原样，未 trim —— 清空键判断的是"框里有没有字"）。
 * @param onQueryChange 每次输入。
 * @param onSearch 键盘「搜索」键。
 */
@Composable
private fun TocSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                text = TOC_SEARCH_PLACEHOLDER,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
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
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        // 边框收到 `outlineVariant`：面板底就是 `surface`，用默认的 `outline` 会在深色主题里
        // 显得比标题还重（与 `PostSearchSheet.kt:714-717` 同一套取值）。
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = scheme.outlineVariant,
            unfocusedBorderColor = scheme.outlineVariant.copy(alpha = 0.6f),
        ),
    )
}

/**
 * 「没有匹配的楼层」：有关键词、但一条回帖都没命中时的那一行。
 *
 * 画在列表里（主楼下面）而不是替换整个列表：主楼那一行照旧要在 —— 它是"回帖子开头"的入口，
 * 与"找楼层"无关（理由见文件头「关键词过滤」）。`heightIn(min = 96.dp)` 而不是一行贴着列表
 * 顶端：太矮会让人以为下面还有内容没画出来（同一个判断见 `PostSearchSheet.kt:889-903` 的
 * 「提示占一块再居中」）。
 *
 * 字号 `labelSmall` + `onSurfaceVariant`：它是"结果为空"的读数，不该和楼层行抢注意力。
 */
@Composable
private fun TocNoMatchHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = TOC_NO_MATCH_TEXT,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
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
 * @param current 高亮落在主楼时为 true —— 即 `currentFloorId == 0L`（视口在主楼 / 还不知道），
 *   或者刚点了这一行、还在 600ms 的冻结里（见文件头「跳转期间冻结高亮」）。
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
