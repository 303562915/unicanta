package io.github.samolego.canta.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
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
import io.github.samolego.canta.data.community.PostReadStore
import io.github.samolego.canta.data.community.PostStore
import io.github.samolego.canta.data.community.PostThread
import io.github.samolego.canta.data.rank.WeeklyRankRepository
import io.github.samolego.canta.ui.component.AuthorLabel
import io.github.samolego.canta.ui.component.AnonymousDot
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.MiniMarkdown
import io.github.samolego.canta.ui.component.PostAvatar
import io.github.samolego.canta.ui.component.ScrollToTopEffect
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.fallbackBrandColor
import io.github.samolego.canta.ui.component.isDefaultAuthorName
import io.github.samolego.canta.ui.component.metaTint
import io.github.samolego.canta.ui.component.parseHexColor
import io.github.samolego.canta.ui.component.readableBrandColor
import io.github.samolego.canta.ui.component.relativeStamp
import io.github.samolego.canta.ui.component.rememberRememberedListState
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.BeijingTime
import io.github.samolego.canta.util.IdentitySetup
import io.github.samolego.canta.util.ShareCode
import io.github.samolego.canta.util.currentTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** 一页拉多少条。后端把 `p_limit` 夹在 1..50，这里取一屏够看的数。 */
private const val POSTS_PAGE = 20

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
 * 编辑器工具栏的一项：图标 + 无障碍名 + 往正文末尾追加的 Markdown 片段。
 *
 * 片段都是"能直接念出来"的样子（`**粗体**` 而不是 `****`）：插进去以后改掉中间那几个字就行，
 * 不用先猜这串符号是干嘛的。
 */
private class EditorTool(
    val label: String,
    val glyph: FlGlyph,
    val snippet: String,
)

/**
 * 工具栏的顺序照 discourse/fluxdo 的编辑器：粗体、斜体、标题、列表、引用、代码、链接。
 *
 * 图标一律用 [FlGlyphs] 自绘：编辑器和顶栏、FAB 在同一屏里，图标族的笔画粗细必须一致
 * （FlGlyph 是 1.8/24 的中心线，Material 的 outlined 在 16dp 下会掉到 1.3px，混着摆一眼能看出
 * 两套粗细）。`bold / italic / heading / bulletList / quote` 是本仓新补的，FlClash 原版没有
 * 写作工具；`code / link` 用的是它原有的那两个。
 *
 * 只往末尾追加：我们用的是 String 值而不是 TextFieldValue，拿不到游标位置；与其为了
 * "插在光标处"把整页输入框改成 TextFieldValue（会连带改草稿、自动保存和预览），
 * 不如让人写完点一下、再把这行挪上去——多一步，但不会插错地方。
 */
private val EDITOR_TOOLS = listOf(
    EditorTool("粗体", FlGlyphs.bold, "**粗体**"),
    EditorTool("斜体", FlGlyphs.italic, "*斜体*"),
    EditorTool("标题", FlGlyphs.heading, "## 小标题"),
    EditorTool("列表", FlGlyphs.bulletList, "- 一项"),
    EditorTool("引用", FlGlyphs.quote, "> 引用"),
    EditorTool("代码", FlGlyphs.code, "`代码`"),
    EditorTool("链接", FlGlyphs.link, "[文字](https://)"),
)

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
 * - 右：**没有搜索**。fluxdo 的搜索是工具栏**下面一行**的胶囊（`_capsuleRowHeight = 48`），
 *   顶栏右端只有一个"胶囊收拢成图标时"的落位格；不登录时那儿是 🔔。我们这边胶囊常驻在
 *   列表上方一行，所以右端只留 8dp。
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
            glyph = FlGlyphs.menu,
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
                FlGlyphIcon(
                    glyph = FlGlyphs.chevronDown,
                    size = 15.dp,
                    tint = if (menuOpen) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.rotate(turn),
                )
            }
        }

        // 右端：开源周榜那颗按钮（有数据层才画） + 8dp 留白。
        // 搜索不在这条栏上：胶囊常驻在它下面那一行。
        if (onOpenWeeklyRank != null) {
            WeeklyRankIconButton(onClick = onOpenWeeklyRank)
        }
        // 「创建话题」：一颗直达的写作入口，照 fluxdo 首页工具栏右端的创作按钮。
        // 图标用 [FlGlyphs.edit]（笔），和 FAB 菜单里「写新帖」同一颗——同一件事在哪儿都是同一件图标。
        // 身份没认出来时（[onCompose] 由调用方传 null）不画：那颗按钮点了也只能说"还没认出身份"。
        if (onCompose != null) {
            TopBarIconButton(
                glyph = FlGlyphs.edit,
                label = "创建话题",
                onClick = onCompose,
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
    glyph: FlGlyph,
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
        FlGlyphIcon(
            glyph = glyph,
            size = 24.dp,
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
            glyph = FlGlyphs.refresh,
            label = "刷新",
            onClick = onRefresh,
        )
        if (canWrite) {
            // 顺序照 `M3eFabMenu`：离 FAB 最近的是"写新帖"（主动作），草稿在它上面一格。
            FabAction(
                visible = open,
                glyph = FlGlyphs.edit,
                label = "写新帖",
                onClick = onNewPost,
            )
            FabAction(
                visible = open,
                glyph = FlGlyphs.drafts,
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
            // 和 FlGlyph 的 1.8/24 对不上（24→20 缩放后 Material 是 2.5 个逻辑像素）。
            FlGlyphIcon(
                glyph = if (open) FlGlyphs.close else FlGlyphs.add,
                size = (24f - 4f * tint).dp,
                tint = ink,
            )
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
private fun FabAction(visible: Boolean, glyph: FlGlyph, label: String, onClick: () -> Unit) {
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
                FlGlyphIcon(
                    glyph = glyph,
                    size = 24.dp,
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
    var openId by remember { mutableStateOf(0L) }
    var thread by remember { mutableStateOf<PostThread?>(null) }
    var threadLoading by remember { mutableStateOf(false) }
    var threadError by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf("") }
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
        scope.launch {
            val count = current.comment(openId, text, author)
            busyKey = null
            if (count == null) {
                platform.showMessage("没回上：${current.reason ?: "检查一下网络"}")
            } else {
                reply = ""
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
    }

    /** 打开详情：先清空上一次的正文，免得新帖刷新前先显示上一条的字。 */
    val openThread: (CommunityPost) -> Unit = open@{ post ->
        thread = null
        threadError = null
        reply = ""
        openId = post.id
        // 先进加载态：详情那一帧还没有正文，不置 true 会闪一句「这条帖子没拉到」。
        threadLoading = true
    }

    /** 引用某一楼：把那句话做成 Markdown 引用塞进回复框，接着写就行。 */
    val quoteComment: (PostComment) -> Unit = quote@{ comment ->
        val snippet = comment.body.lineSequence().firstOrNull().orEmpty().trim().take(40)
        val head = if (snippet.isEmpty()) "" else "> ${comment.author.ifBlank { "匿名" }}：$snippet\n"
        reply = (head + reply).take(POST_BODY_LIMIT)
        platform.showMessage("已引用，写完点发送")
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
                    // 标题照 fluxdo 的 `textTheme.titleMedium`：≈16sp、**常规字重**
                    // （不是本仓二级页那套 17sp 加粗，也不是首页 24sp 大标题）。
                    titleStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                    actions = {
                        val open = thread?.post
                        if (open != null) {
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
                        // 预览切换留在这里而不是正文上面：写的时候看不到它，看一眼才点。
                        TextButton(onClick = { previewing = !previewing }) {
                            Text(if (previewing) "改文字" else "预览")
                        }
                        // 圆形纸飞机发送（fluxdo 的 ComposerSubmitButton：40dp 正圆，图标 21dp）。
                        // 改帖时这颗按钮的含义是"保存修改"，仍然是同一个位置、同一个形状。
                        PublishCircleButton(
                            busy = posting,
                            enabled = canWrite && !posting && setupGate.ready,
                            onClick = publish,
                        )
                    },
                )

else -> Column {
                    // 列表页顶上不是「标题栏」而是 fluxdo 那条 48dp 工具栏：☰ / 最新▾（没有 🔍，
                    // 搜索是它下面那一行常驻的胶囊）。
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
                                        { FlGlyphIcon(glyph = FlGlyphs.check, size = 16.dp) }
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
                saved = reads?.isSaved(openId) == true,
                onReplyChange = { reply = it.take(POST_BODY_LIMIT) },
                onSend = sendReply,
                onToggleLike = { thread?.post?.let(toggleLike) },
                onToggleSave = {
                    reads?.let { store ->
                        store.toggleSaved(openId)
                        readsVersion++
                    }
                },
                onQuote = quoteComment,
                onCopy = {
                    thread?.post?.let { post ->
                        platform.copyToClipboard(copyablePost(post))
                        platform.showMessage("帖子已复制")
                    }
                },
                onOpenRelated = { post -> openThread(post) },
                modifier = Modifier.padding(padding),
            )

            composing -> PostEditorPane(
                title = title,
                body = body,
                category = category,
                tags = tags,
                tagInput = tagInput,
                savedAt = savedAt,
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
                onOpen = openThread,
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
 * - 底色 `surfaceContainerHighest` **50%**
 * - 左侧留白 **16**，放大镜 **20dp**，用**强调色**（不是灰色 outline）
 * - 图标与提示文字之间 **8**，文字 **14sp**、色 `onSurfaceVariant`、右边留 **12**
 *
 * 为什么不用 `OutlinedTextField`：它自带 56dp 的最小高度与内部 padding，压不到 40；
 * 而且它的 placeholder 走的是输入框那套排版（12sp 起），和这里要的 14sp 对不上。
 * 自己搭一层 `decorationBox` 更短的代码就能把上面五个数逐个落实。
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
                FlGlyphIcon(
                    glyph = FlGlyphs.search,
                    size = 20.dp,
                    tint = scheme.primary,
                )
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
                Spacer(modifier = Modifier.width(12.dp))
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
    /** 搜索胶囊的焦点（胶囊常驻，这一页没有「点图标跳过去」的入口）。 */
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 96.dp),
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
 * 列表上的一张帖子卡片。
 *
 * 左边是作者字母头像（同一个人永远同一个色），右边四行：标题、分类与标签、摘要、元信息。
 * 摘要只放两行：列表是用来扫的，点进去才是读。
 *
 * [unread] 是"没看过 / 有新回复"：标题左边一个实心点，扫列表时先认它。
 */
/**
 * 列表上的一张帖子卡片，逐项照 fluxdo 的 `TopicCard`（`lib/widgets/topic/topic_card.dart`
 * 的 `_buildNormalBody` + `_buildMetadataBlock`，默认 `TopicCardAvatarLayout.inline`）：
 *
 * ```
 * Column(
 *   行1: 标题（满宽，最多两行，15sp/1.3）+ 右端未读点（间距 6、下沉 5）
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
 * `EdgeInsets.fromLTRB(12, 10, 12, 10)`），不是估的。
 *
 * 标题为什么独占一行：论坛列表以标题为主信息，照片和署名排在它后面，视线沿左边缘直扫。
 *
 * [unread] 画在标题行右端（位置固定），[saved] / [mine] 只加在名字后面，不抢标题的注意力。
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
    // 三档颜色，照 `topic_card.dart:288-293`：
    //   未读       标题 onSurface             元信息 onSurfaceVariant ×0.8
    //   读过没读完 标题 onSurfaceVariant×0.75  元信息 onSurfaceVariant ×0.8
    //   全读完     标题 onSurfaceVariant×0.75  元信息 onSurfaceVariant ×0.6
    val titleColor = if (read) {
        metaTint(scheme.onSurfaceVariant, 0.75f)
    } else {
        scheme.onSurface
    }
    // 时间那一格：未读用主色（和标题右端那个点呼应），其余跟着元信息色走。
    val timeColor = if (unread) {
        scheme.primary
    } else {
        metaTint(scheme.onSurfaceVariant, if (read) 0.6f else 0.8f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            // 长按 = 收藏 / 取消收藏：列表上最常用的两个动作（看、收）各占一种手势，不必先进详情。
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = "收藏或取消收藏",
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
    ) {
        // 行1：标题满宽。这一行必须 fillMaxWidth —— 标题吃 weight(1f)，无界宽度下会被算成 0。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            if (post.pinned) {
                FlGlyphIcon(glyph = FlGlyphs.pin, size = 13.dp, tint = PostPinned)
                Spacer(modifier = Modifier.width(4.dp))
            }
            if (post.locked) {
                FlGlyphIcon(glyph = FlGlyphs.lock, size = 13.dp, tint = titleColor.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = post.title.ifBlank { "（无标题）" },
                // 15sp / 行高 1.3：与 `TopicCardStyle.titleFontSize` 的默认值同一个数。
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 19.5.sp),
                // 读完的帖子退一档：w600 → w500（`topic_card.dart:313`）
                fontWeight = if (read) FontWeight.Medium else FontWeight.SemiBold,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (unread) {
                Spacer(modifier = Modifier.width(6.dp))
                // 蓝点与标题首行视觉居中：`EdgeInsets.only(top: 5)`；直径 **8dp**
                // （fluxdo `topic_card.dart:880` / `topic_card_layout.dart:1075`）。
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(8.dp)
                        .background(scheme.primary, CircleShape),
                )
            }
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
                        style = MaterialTheme.typography.labelLarge,
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
                    PostMetric(glyph = Icons.Outlined.FavoriteBorder, count = post.likes, active = post.mineLike)
                    Spacer(modifier = Modifier.width(10.dp))
                    // 回复数用主色：一列里它是最想让人点进去的那个数。
                    PostMetric(glyph = Icons.Outlined.ChatBubbleOutline, count = post.comments, active = true)
                }
            }
        }
    }
}

/**
 * 卡片右下角的一个统计：一个小图标 + 数字。
 *
 * 不用 [FlGlyphIcon]：那套是 FlClash 的线框图标，没有爱心和气泡；这里要的是 Discourse 那两个
 * 一眼认得出的形状，所以用 Material 的 outlined 版，颜色跟着主题走。
 */
@Composable
private fun PostMetric(glyph: ImageVector, count: Int, active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = if (active) scheme.primary else scheme.outline,
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) scheme.primary else scheme.outline,
            maxLines = 1,
        )
    }
}

/**
 * 首屏占位卡片：三条长短不一的灰条，形状与真卡片一样（标题两行 → 作者行 → 分类行）。
 *
 * 目的是让"正在拉"这件事有个形状：转圈看不出下面会出现什么，灰卡看得出是一列帖子。
 */
@Composable
private fun PostSkeletonCard() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBar(widthFraction = 0.86f)
        SkeletonBar(widthFraction = 0.52f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceVariant),
            )
            Spacer(modifier = Modifier.width(6.dp))
            SkeletonBar(widthFraction = 0.28f, align = false)
        }
        SkeletonBar(widthFraction = 0.4f)
    }
}

/**
 * 骨架里的一条灰条。
 *
 * [align] 为 true 时吃满一行（`fillMaxWidth(fraction)`），为 false 时按内容宽自己量——
 * 放在 Row 里那种位置不能用 fillMaxWidth，否则它会把整行撑满、把邻居挤出去。
 */
@Composable
private fun SkeletonBar(widthFraction: Float, align: Boolean = true) {
    val base = Modifier
        .height(10.dp)
        .clip(RoundedCornerShape(50))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    Box(
        modifier = if (align) base.fillMaxWidth(widthFraction) else base.width(56.dp),
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
                    imageVector = Icons.Outlined.ChatBubbleOutline,
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
    onReplyChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleSave: () -> Unit,
    onQuote: (PostComment) -> Unit,
    onCopy: () -> Unit,
    onOpenRelated: (CommunityPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    // 收进一个局部 val：下面几个 lambda 里要读它的字段，写一处最稳。
    val shown = post

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
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        footer = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                        enabled = busyKey == null,
                                    onClick = onToggleLike,
                                ) {
                                    Text(
                                        if (busyKey == "like:${shown.id}") "…"
                                        else if (shown.mineLike) "已赞 ${shown.likes}"
                                        else "赞 ${shown.likes}",
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${shown.comments} 条回复",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                // 复制整帖（标题、作者、正文、标签）：「这帖转给我看看」最省事的做法。
                                TextButton(onClick = onCopy) { Text("复制") }
                                // 收藏放在这里而不是顶栏：顶栏已经有删除，再挤一个按钮标题就要被压扁了。
                                TextButton(onClick = onToggleSave) {
                                    Text(if (saved) "已收藏" else "收藏")
                                }
                            }
                        },
                    )
                }

                item(key = "thread-head") {
                    PostSectionHeader(label = "回复", count = shown.comments)
                }

                if (comments.isEmpty()) {
                    item(key = "thread-empty") {
                        PostNote(text = "还没有人回。")
                    }
                }

                // 楼层号就是时间序里的位置：后端按时间正序给，这里 index + 1 直接对得上。
                itemsIndexed(comments, key = { _, item -> "c-${item.id}" }) { index, comment ->
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
                        footer = {
                            if (!shown.locked) {
                                TextButton(onClick = { onQuote(comment) }) { Text("引用回复") }
                            }
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

            // 锁帖不另起一条横带：fluxdo 是把"话题已关闭"写进回复胶囊的文案里
            // （`topic_bottom_bar.dart:376-379` 的 `TopicReplyEntryState.closed` →「话题已关闭」+ 锁图标）。
            // 那条横带每屏白占 40dp，而胶囊已经在同一位置说明同一件事。
            ReplyBar(
                reply = reply,
                busy = busyKey == "reply:${shown.id}",
                locked = shown.locked,
                // 认不出身份的人写不了回帖，胶囊文字换成"登录后参与讨论"而不是点了才报错。
                enabled = authorId.isNotBlank(),
                onChange = onReplyChange,
                onSend = onSend,
            )
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
            .padding(horizontal = 12.dp)
            .padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 标题 + 锁图标：锁住的帖子在标题**前面**画一颗锁（`topic_detail_header.dart:109-120`
            // 的 20dp / `outline` 色），而不是另起一条"这条帖已经锁了"的横带 —— 那条横带
            // 每屏白占一行，而锁本来就是标题的属性。
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (post.locked) {
                    FlGlyphIcon(
                        glyph = FlGlyphs.lock,
                        size = 20.dp,
                        tint = scheme.outline,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = post.title.ifBlank { "（无标题）" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

        // 分类 + 标签：discourse 那种一排水洗标签。分类带自己那一小块颜色。
        if (post.category.isNotBlank() || post.tags.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (post.category.isNotBlank()) {
                    CategoryPill(category = post.category, colorHex = post.categoryColor)
                }
                post.tags.forEach { tag -> TagPill(tag = tag) }
            }
        }

        // 元信息：回复 / 赞 / 时间。三件事挤一行，用中点分开——比三行小字省地方。
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThreadMeta(
                glyph = Icons.Outlined.ChatBubbleOutline,
                text = "${post.comments} 回复",
                tint = scheme.onSurfaceVariant,
            )
            ThreadMetaDivider()
            ThreadMeta(
                glyph = Icons.Outlined.FavoriteBorder,
                text = "${post.likes} 赞",
                tint = if (post.mineLike) scheme.primary else scheme.onSurfaceVariant,
            )
            val stamp = relativeStamp(post.createdAt)
            if (stamp.isNotBlank()) {
                ThreadMetaDivider()
                Text(
                    text = stamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        // 统计行：参与者头像 + 「N 参与者」+ 「X 分钟阅读」。
        // 照 fluxdo 的 `topic_statistics.dart`：一个 `Wrap(spacing: 12)`，每项是"数值 + 浅色标签"
        // 两段文字，阅读时长是**下一项**而不是被推到行右端。
        if (participants.isNotEmpty() || readMinutes > READ_MINUTES_MIN) {
            // Compose 里对应的不是 Dart 的 `Wrap` 而是 `FlowRow`（换行 + 水平间距）。
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
                            imageVector = Icons.Outlined.MenuBook,
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

        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
    }
}

/** 元信息里的一个小图标 + 一句话。 */
@Composable
private fun ThreadMeta(glyph: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = tint,
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

/** 元信息之间的那个中点分隔。 */
@Composable
private fun ThreadMetaDivider() {
    Text(
        text = "·",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
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
            FlGlyphIcon(
                glyph = FlGlyphs.more,
                size = 24.dp,
                tint = scheme.onSurface,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (canManage) {
                // 第一项：编辑话题（作者本人 / 有编辑权）。
                MenuRow(
                    glyph = FlGlyphs.edit,
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
                    glyph = FlGlyphs.copy,
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
                    glyph = FlGlyphs.delete,
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
                    glyph = FlGlyphs.copy,
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
    glyph: FlGlyph,
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
        FlGlyphIcon(glyph = glyph, size = 20.dp, tint = ink)
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
    footer: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 详情页的 LazyColumn 不再给左右 contentPadding（header 要占满整行），所以卡片
            // 自己留 12dp：和列表页的卡片、header 是同一条左边缘。
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 头部照 fluxdo `post_header.dart:233-360`：左边一列是「名字 + 徽章」，
            // 右边一列是「时间 / 楼层号」**竖排两行**（时间在上、楼层号在下）。
            // 上一版把楼层号和名字挤在同一行，长名字时时间/楼层号会被压到 0 宽。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AuthorLabel(
                        name = author.ifBlank { "匿名" },
                        style = MaterialTheme.typography.titleSmall,
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
                Column(
                    modifier = Modifier.padding(start = 8.dp),
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
            if (body.isBlank()) {
                Text(
                    text = "（没有正文）",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                MiniMarkdown(
                    text = body,
                    baseStyle = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
            }
            footer()
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
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 面板开着没有：开着才画遮罩与面板，关着时这一栏只有那颗胶囊。
    var composerOpen by remember { mutableStateOf(false) }

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
                        onClick = { composerOpen = true },
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlGlyphIcon(
                    glyph = FlGlyphs.edit,
                    size = 18.dp,
                    tint = if (enabled && !locked) scheme.onSurfaceVariant else scheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        locked -> "话题已关闭"
                        !enabled -> "登录后参与讨论"
                        // 草稿摘要取**第一个非空行**：只取 `firstOrNull()` 时，正文以一个空行开头就会
                        // 显示成"草稿："后面什么都没有，看着像把草稿弄丢了。
                        reply.isNotBlank() -> {
                            val head = reply.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                                ?: reply.trim()
                            "草稿：${head.take(30)}"
                        }
                        else -> "写下你的回复…"
                    },
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
    if (composerOpen) {
        ReplyComposerPanel(
            reply = reply,
            busy = busy,
            onChange = onChange,
            onSend = {
                onSend()
                composerOpen = false
            },
            onDismiss = { composerOpen = false },
        )
    }
}

/**
 * 回复面板，照 fluxdo `topic_quick_reply.dart`：
 *
 * - 圆角 **24**（`_panelRadius = 24`，`:84`），贴底时**上圆下平**（`:426-427`）
 * - 遮罩 **0.32** 黑（`:341` 的 `0.32 * raw`）
 * - 展开 **340ms** / 收起 **260ms**，曲线 `easeInOutCubicEmphasized`（`:85-91`）
 * - 输入框：`surfaceContainerHighest` 底、圆角 **16**、`minLines 3 / maxLines 8`、
 *   内边距 **14**、`bodyLarge`（`:524-546`）
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
                    .background(scheme.surfaceContainerHighest),
            ) {
                // 输入框：圆角 16、底色低一档、内边距 14、3~8 行。
                OutlinedTextField(
                    value = reply,
                    onValueChange = onChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.surfaceContainerLow),
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
                // 与详情页吸底回复栏那颗发送同一个 glyph（`ReplyBar` 用的是 `FlGlyphs.send`）：
                // 同一个页面里两个"发送"不能一个 Material 纸飞机、一个自绘纸飞机。
                FlGlyphIcon(
                    glyph = FlGlyphs.send,
                    size = 21.dp,
                    tint = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PostEditorPane(
    title: String,
    /** 正文带游标：工具栏那七个 Markdown 按钮要插在"正在写的地方"，见 [insertMarkdown]。 */
    body: TextFieldValue,
    category: String,
    tags: List<String>,
    tagInput: String,
    savedAt: Long,
    error: String?,
    previewing: Boolean,
    onTitleChange: (String) -> Unit,
    onBodyChange: (TextFieldValue) -> Unit,
    onCategoryChange: (String) -> Unit,
    onTagInputChange: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onInsertShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val savedClock = draftClock(savedAt)
    // 分类是一列选项、标签是一行输入：两者形态不同，所以各用一种展开方式。
    //   - 分类照着 fluxdo 的 `_CategoryPicker`：**点开是一个 DropdownMenu**（一列五个候选，
    //     当前那个打勾），选完就收起。之前是"在底部内联铺一排 chip"，等于每次选分类都把
    //     底部那两块高度顶上去 26dp，正文区跟着缩一下。
    //   - 标签是一格输入框 + 已成的 chip，没办法做成"一列选项"，所以还是内联展开。
    //
    // 两个开合都放在这里（而不是提到 PostsScreen）：它们只影响这一屏的排版，
    // 页面级状态里已经有十几个了，再塞两个会让"编辑器状态"那段更难读。
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var tagsOpen by remember { mutableStateOf(tags.isNotEmpty() || tagInput.isNotBlank()) }

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
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (savedClock.isNotEmpty()) "已自动保存 · $savedClock" else "还没存过草稿",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
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
            // 字数角标：右下角，小字灰，不占一行。
            Text(
                text = "${body.text.length}/$POST_BODY_LIMIT",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.outline,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                textAlign = TextAlign.End,
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = error, style = MaterialTheme.typography.labelSmall, color = scheme.error)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

        // 底部常驻：属性条 + 工具栏。这两块是"发之前最后核对的东西"，吸在最下面。
        // **这里不再垫 `imePadding`**：IME 由最外层那个 Column 吃掉了。两层都垫的话，
        // 键盘弹起时这块会往上跳两倍 inset 的高度，中间留一条空白。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FlSurfaceLevel.Block.colorOf(scheme)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // 属性条**不横滚**：`DropdownMenu` 的 popup 只在 `expanded` 变化时算一次屏幕坐标，
// 外层套 `horizontalScroll` 的话，滚动之后菜单还钉在原处、跟手走不了。三个胶囊
// （选择分类 / + 标签 / 分享方案）在正常宽度下放得下，超出时让文字自己省略。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 分类：胶囊 + 下拉菜单，两件事必须包在一个 Box 里 —— DropdownMenu 拿不到
                // 外面的滚动坐标，锚点必须紧跟胶囊，否则 popup 会落在屏幕上别处。
                Box {
                    ComposerPill(
                        text = category.ifBlank { "选择分类" },
                        accent = category.isBlank(),
                        dropdown = true,
                        onClick = { categoryMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = categoryMenuOpen,
                        onDismissRequest = { categoryMenuOpen = false },
                    ) {
                        // 第一项是"不选"：分类本来就可以空着（后端当字符串存，空串合法），
                        // 没有这一项的话，选错了就只能点同一个 chip 再点一次才能取消。
                        DropdownMenuItem(
                            text = { Text("不选分类") },
                            onClick = {
                                onCategoryChange("")
                                categoryMenuOpen = false
                            },
                            trailingIcon = if (category.isBlank()) {
                                { FlGlyphIcon(glyph = FlGlyphs.check, size = 16.dp) }
                            } else {
                                null
                            },
                        )
                        POST_CATEGORIES.forEach { label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onCategoryChange(label)
                                    categoryMenuOpen = false
                                },
                                trailingIcon = if (label == category) {
                                    { FlGlyphIcon(glyph = FlGlyphs.check, size = 16.dp) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
                // 标签没有"一列候选"可给（标签是用户自己写的词），所以还是内联展开一格输入框。
                ComposerPill(
                    text = if (tags.isEmpty()) "+ 添加标签" else "+ 标签 (${tags.size}/$TAG_MAX)",
                    accent = tags.isEmpty(),
                    onClick = { tagsOpen = !tagsOpen },
                )
                if (onInsertShare != null) {
                    // 比模板更进一步的一格：插进来的是一份真实的方案分享（名字 + 机型 + 分享码），
                    // 而不是一段要自己填的骨架。
                    ComposerPill(text = "分享方案", accent = false, onClick = onInsertShare)
                }
            }

            if (tagsOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (tags.isNotEmpty()) {
                        TagChips(tags = tags, onRemove = onRemoveTag)
                    }
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = onTagInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("标签，用逗号或空格分开，最多 $TAG_MAX 个") },
                        textStyle = MaterialTheme.typography.bodySmall,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                }
            }

            // 工具栏：图标 16dp、触控 48dp，横向可滚（按钮多，窄屏放不下就滚）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EDITOR_TOOLS.forEach { tool ->
                    EditorToolButton(tool = tool) {
                        // 插在光标处 / 包住选中那一段，不再往末尾追加（见 [insertMarkdown]）。
                        onBodyChange(insertMarkdown(body, tool.snippet, POST_BODY_LIMIT))
                    }
                }
            }
        }
    }
}

/**
 * 属性条上的一个胶囊。数值照 fluxdo `topic_editor_helpers.dart` 的 `_pill`（移动端分支）。
 *
 * [dropdown] 决定右端那颗小箭头画不画：分类是"点开一列选项"，箭头照着 `chevronDown` 画；
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
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerHighest.copy(alpha = 0.42f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 属性条不再横滚（见调用点注释），所以文字要自己收：分类名、标签计数都不会太长，
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
            modifier = Modifier.widthIn(max = 140.dp),
        )
        if (dropdown) {
            Spacer(modifier = Modifier.width(2.dp))
            // 下拉小三角：告诉人"这里点开是一列选项"，不是一个开关。
            FlGlyphIcon(
                glyph = FlGlyphs.chevronDown,
                size = 14.dp,
                tint = if (accent) scheme.primary else scheme.onSurfaceVariant,
            )
        }
    }
}

/** 工具栏里的一个按钮：图标 16dp、触控 48dp（fluxdo 的 `_ToolbarButton`）。 */
@Composable
private fun EditorToolButton(tool: EditorTool, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = tool.label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FlGlyphIcon(
            glyph = tool.glyph,
            size = 16.dp,
            tint = scheme.onSurfaceVariant,
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

/** 「草稿 N 篇 · 继续编辑」那一行：点它接着写最新那篇。 */
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
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
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

/** 草稿列表里的一行：标题、一句预览、时间，右边一个删除。 */
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
            .padding(start = 12.dp, end = 12.dp, top = 6.dp)
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
 * 强调色小图标 + 6dp 间距，区块内边距 `LTRB(16, 16, 16, 8)`。
 * 上一版是 `labelMedium + primary` 的"彩色小标签"，那是 FlClash 的区块头写法，
 * 在帖子详情里看着像一条可点的链接。
 */
@Composable
private fun PostSectionHeader(label: String, count: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIcon(
            glyph = FlGlyphs.star,
            size = 16.dp,
            tint = MaterialTheme.colorScheme.primary,
        )
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
 * 圆角 12（分组首项 outer radius）、内边距 12/14、文字 `labelLarge`、未读角标、
 * 箭头 20dp 且展开时旋转 180°（180ms）。
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
            .padding(horizontal = 12.dp)
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
        FlGlyphIcon(
            glyph = FlGlyphs.chevronDown,
            size = 20.dp,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.rotate(turns),
        )
    }
}

/**
 * 「↑ 查看 N 个新的或更新的话题」。数值照 fluxdo 的 `topic_list_update_banner.dart`：
 * 圆角 8、底色 `primaryContainer` 20%、上下 8 的内边距、图标 14dp、图标与文字间距 8、
 * 文字 13sp / w500 / 主色。整条横满（不是居中的小胶囊）。
 */
@Composable
private fun FreshTopicsBanner(count: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.primaryContainer.copy(alpha = 0.2f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 向上的箭头用自绘：FlGlyphs 有四个方向的 chevron，但没有直箭头，所以新补了
        // `arrowUpward`（与 `arrowBack` 同构，转 90°）。
        FlGlyphIcon(
            glyph = FlGlyphs.arrowUpward,
            size = 14.dp,
            tint = scheme.primary,
        )
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
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
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

/** 一句说明 / 空状态。 */
@Composable
private fun PostNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp),
    )
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
    val next = text.replaceRange(start, end, template)
    if (next.length > limit) return value
    // 链接模板选中"文字"两个字：那是要替换的占位符，选中它就能直接打字覆盖，
    // 不用先手动删掉。
    val focus = focusRangeFor(template)
    return value.copy(
        text = next,
        selection = TextRange(start + focus.first, start + focus.second),
    )
}

/**
 * 选中内容已经带着 Markdown 记号时，返回解开后的文字与它应保持的选中长度；否则 null。
 *
 * `**粗体**` 的包裹对是 `**`+`**`，`[文字](https://)` 的是 `[`+`](https://)`。
 * 只处理这七种工具栏模板的形状，不求通用 —— 通用化就要解析 Markdown，那是另一件事。
 *
 * 不需要知道是哪一颗按钮：判断全靠"选中的这段字自己长什么样"。所以在 `**x**` 上点斜体
 * 也会把它解成 `x`（`*` 也是它的前缀），这是可接受的——人看到的是"星号没了"。
 */
private fun unwrapTemplate(selected: String): Pair<String, Int>? {
    if (selected.isEmpty()) return null
    // 三列：前缀、后缀、这套模板是不是"只有前缀"（`## ` / `- ` / `> ` 那种）。
    val pairs = listOf(
        Triple("**", "**", false),
        Triple("*", "*", false),
        Triple("`", "`", false),
        Triple("[", "](https://)", false),
        Triple("## ", "", true),
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
    "## 小标题" -> 3 to 3
    "- 一项" -> 2 to 2
    "> 引用" -> 2 to 2
    // `[文字](https://)`：选中"文字"两个字（偏移 1、2）。
    "[文字](https://)" -> 1 to 3
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

