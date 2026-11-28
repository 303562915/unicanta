package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.rank.RankBoard
import io.github.samolego.canta.data.rank.RankRow
import io.github.samolego.canta.data.rank.WeeklyRankRepository
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.currentTimeMillis
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** 海报上那支黄：名次前三位、净增数和分区标签都用它。 */
private val RankGold = Color(0xFFE0A400)

/** 海报底色与三层文字：浅色主题下也是这块深色面板，因为整页就是一张海报。 */
private val RankBackground = Color(0xFFFCFBFA)
private val RankInk = Color(0xFF1B1F24)
private val RankMuted = Color(0xFF6B7280)

/** 涨、跌各自一句颜色；「持平」用 [RankMuted]。 */
private val RankUp = Color(0xFF1E9E5A)
private val RankDown = Color(0xFFD93A2B)

/** 分类标签的四种主色，按 GitHub topic 分。 */
private val ChipAgent = Color(0xFFB07C00)
private val ChipModel = Color(0xFF1F6FA8)
private val ChipAi = Color(0xFF1E8E56)
private val ChipOther = Color(0xFF7B52C4)

/** 周榜口径里「一周」的长度；只用来画日期区间，不参与算数。 */
private const val WEEK_MS = 604_800_000L

/**
 * 帖子页顶栏右上角那个「开源周榜」按钮：一个 40dp 的圆，里头是排行榜图标
 * （[Icons.Rounded.Leaderboard]：三根高低不同的领奖台柱子，一看就是"榜"）。
 *
 * 为什么从列表里搬到顶栏：它原来是列表顶上一条 40dp 的横条，等于每屏少一条帖子；而它是一个
 * **入口**不是内容，入口归顶栏。
 */
@Composable
internal fun WeeklyRankIconButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "GitHub AI 开源周榜", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Leaderboard,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 帖子列表顶上那一行（**列表里的入口卡**，已不再挂在帖子页上，留给别处复用）：只有文字，
 * 不加缩略图也不列前三名。
 *
 * 它是一条"入口"，不是一块内容——帖子的列表才是这一页的主体，卡片每高一行，能看到的帖子就少一条。
 * 这里也**不发请求**：帖子列表是打开频率最高的一页，一页列表滚一下就够把 GitHub 未认证的额度用掉；
 * 真正的抓取发生在点开详情页的时候，卡片只读 [WeeklyRankRepository.cachedBoard] 里盘上那一份。
 */
@Composable
internal fun WeeklyRankTeaserCard(
    repository: WeeklyRankRepository,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val revision by repository.revision.collectAsState()
    val cached = remember(revision) { repository.cachedBoard() }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = FlSurfaceLevel.Block.colorOf(scheme),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "GitHub AI 开源周榜",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (cached != null) {
                    "实时 · 抓取于 ${agoText(cached.fetchedAt)}"
                } else {
                    "实时 · 数据现拉"
                },
                fontSize = 10.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "查看",
                fontSize = 11.sp,
                color = scheme.primary,
            )
        }
    }
}

/**
 * 开源周榜详情：排版照海报（深色底、左侧大标题、右侧三格指标、圆角交替色榜单、底部口径）。
 *
 * 数据仍然是真的：进页面去 GitHub 拉一次，周净增来自本机按天快照。抓不到时把盘上那一屏继续画着，
 * 顶上写一句为什么——不编数字，也不把满屏清空。
 */
@Composable
internal fun WeeklyRankScreen(
    repository: WeeklyRankRepository,
    onNavigateBack: () -> Unit,
    /** 点一行榜单时去哪：由调用方给出打开浏览器的方式（见 CantaPlatform.openUrl）。 */
    onOpenRepo: (String) -> Unit = {},
) {
    var board by remember(repository) { mutableStateOf(repository.cachedBoard()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloads by remember { mutableIntStateOf(0) }

    LaunchedEffect(repository, reloads) {
        loading = true
        error = null
        val fetched = repository.refresh(force = reloads > 0)
        if (fetched != null) board = fetched
        error = repository.lastError
        loading = false
    }

    val current = board
    val rows = current?.rows.orEmpty()
    val hasWeekly = current?.hasWeekly == true

    // 不带顶栏：这一页本身就是一张海报，顶栏的标题和返回箭头会把它压成"又一个列表页"。
    // 返回靠系统手势/返回键；「刷新」和返回箭头画在页面里，不占一整条。
    Scaffold(containerColor = RankBackground) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // 顶部不要色差：整页就是一块纯色，不再叠渐变（之前那条会把页头和工具栏衬出两种白）。
                .background(RankBackground),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item(key = "actions") {
                RankOverlayActions(
                    loading = loading,
                    onBack = onNavigateBack,
                    onRefresh = { reloads++ },
                )
            }

            item(key = "header") {
                RankHeader(board = current, loading = loading, failed = error != null)
            }

            if (loading && rows.isEmpty()) {
                item(key = "loading") {
                    CircularProgressIndicator(
                        color = RankGold,
                        modifier = Modifier
                            .padding(24.dp)
                            .size(24.dp),
                    )
                }
            }

            error?.let { message ->
                item(key = "error") {
                    Text(
                        text = if (rows.isEmpty()) {
                            "这次没拉到：$message · 点右上角「刷新」再试"
                        } else {
                            "这次没拉到：$message · 下面是上次抓到的"
                        },
                        color = RankDown,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            itemsIndexed(rows, key = { _, row -> "repo:${row.repo}" }) { index, row ->
                RankPosterRow(
                    rank = index + 1,
                    row = row,
                    hasWeekly = hasWeekly,
                    onOpen = { onOpenRepo(row.url) },
                )
            }

            item(key = "rules") {
                Column(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "GitHub AI  |  开源周榜",
                        color = RankGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "口径：候选池是 GitHub 上带 topic:llm / ai / ai-agent 标签、star 最多的仓库；" +
                            "「本周净增」= 今天与 7 天前两个采样点的 star 差，「上期」再看前 7 天。",
                        color = RankMuted,
                        fontSize = 10.sp,
                    )
                    Text(
                        text = "为什么第一次装上看不到净增：GitHub 只给当前 star 总数，不给历史曲线；" +
                            "净增只能靠本机一天一个采样点自己攒，攒不满 7 天就按 star 总数排，不编数字。",
                        color = RankMuted,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

/** 海报头：左边标题（GitHub AI / 开源周榜 / 周期），右边是抓取时刻、加速格、第一名的占比。 */
@Composable
private fun RankHeader(board: RankBoard?, loading: Boolean, failed: Boolean) {
    val end = board?.fetchedAt?.takeIf { it > 0L }
    val weekly = board?.hasWeekly == true
    val rows = board?.rows.orEmpty()
    val comparable = rows.filter { it.week != null && (it.previous ?: 0) > 0 }
    val accelerated = comparable.count { weekIncreasePercent(it.week, it.previous) > 5 }
    val positiveSum = rows.sumOf { (it.week ?: 0).coerceAtLeast(0).toLong() }
    val leader = rows.firstOrNull()?.week ?: 0
    val share = if (leader > 0 && positiveSum > 0) "${leader * 100L / positiveSum}%" else null

    BoxWithConstraints(
        modifier = Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .padding(horizontal = 7.dp, vertical = 8.dp),
    ) {
        val fontScale = LocalDensity.current.fontScale
        val narrow = maxWidth < 300.dp || fontScale > 1.25f
        val dateLine = when {
            end == null -> if (loading) "正在抓取榜单" else "还没有榜单数据"
            weekly -> "近 7 天  |  ${rankDate(end - WEEK_MS).takeLast(5)} — ${rankDate(end).takeLast(5)}"
            else -> "Star 总榜  |  ${rankDate(end).takeLast(5)}"
        }
        val title: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("GitHub  |  开源项目榜单", color = RankMuted, fontSize = 10.sp)
                Text(
                    text = "GitHub AI",
                    color = RankInk,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                )
                Text(
                    text = "开源周榜",
                    color = RankGold,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 33.sp,
                    lineHeight = 37.sp,
                )
                Text(dateLine, color = RankMuted, fontSize = 10.sp)
            }
        }
        val summary: @Composable (Modifier, Boolean) -> Unit = { modifier, rightAligned ->
            val align = if (rightAligned) Alignment.End else Alignment.Start
            Column(
                modifier = modifier,
                horizontalAlignment = align,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // 图上那一列小标签：排序口径 / 抓取方式 / 速度档位，各自一块底，不是一行光字。
                RankHeaderPill(
                    text = if (end == null) "数据来自 GitHub" else if (weekly) "按本周净增排" else "按 star 总数排",
                    tint = RankGold,
                )
                if (end != null) {
                    RankHeaderPill(
                        text = when {
                            failed -> "缓存 · ${agoText(end)}"
                            loading -> "正在刷新"
                            else -> "11—22 抓取 · 7 天"
                        },
                        tint = RankMuted,
                    )
                    if (comparable.isNotEmpty()) {
                        RankHeaderStat(
                            value = "$accelerated/${comparable.size}",
                            label = "个在加速",
                            tint = RankGold,
                        )
                    }
                    if (share?.isNotEmpty() == true) {
                        RankHeaderStat(value = share, label = "第一名占比", tint = RankInk)
                    } else if (!weekly) {
                        RankHeaderPill(text = "待攒够周快照", tint = RankMuted)
                    }
                }
            }
        }
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                title(Modifier.fillMaxWidth())
                summary(Modifier.fillMaxWidth(), false)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                title(Modifier.weight(2f))
                summary(Modifier.weight(1f), true)
            }
        }
    }
}

/** 词条小标签：底下一行那种不带底的细字，和图里「逆向 / 技能框架」的排法一致。 */
@Composable
private fun RankMiniTag(text: String, tint: Color) {
    Text(
        text = text,
        color = tint,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** 页面内那一行小操作：左边返回、右边刷新，浮在海报顶上，不画成一整条顶栏。 */
@Composable
private fun RankOverlayActions(loading: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹ 返回",
            color = RankMuted,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (loading) "刷新中…" else "刷新",
            color = if (loading) RankMuted else RankGold,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = !loading, onClick = onRefresh)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

/** 顶部那种小方标签：浅色半透明底 + 一点色边，和图里「按本周净增排」那块一样。 */
@Composable
private fun RankHeaderPill(text: String, tint: Color) {
    Text(
        text = text,
        color = tint,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** 顶部那种数字块：大号数字 + 底下一句说明。 */
@Composable
private fun RankHeaderStat(value: String, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = value,
            color = tint,
            fontSize = 24.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = label,
            color = RankMuted,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

/** 榜单的一行：名次牌 / 仓库名与说明 / 分类标签 / 本周净增与上期。 */
@Composable
private fun RankPosterRow(
    rank: Int,
    row: RankRow,
    hasWeekly: Boolean,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    val topic = row.topics.firstOrNull { it == "ai-agent" || it == "llm" }
        ?: row.topics.firstOrNull { it.isNotBlank() }
    val week = row.week
    val previous = row.previous
    val percent = weekIncreasePercent(week, previous)
    val pace = when {
        week == null || previous == null -> null
        previous <= 0 && week > previous -> "加速"
        previous <= 0 && week < previous -> "放缓"
        percent > 5 -> "加速 +$percent%"
        percent < -5 -> "放缓 −${-percent}%"
        else -> "持平"
    }
    val paceColor = when {
        pace == null -> RankMuted
        pace.startsWith("加速") -> RankUp
        pace.startsWith("放缓") -> RankDown
        else -> RankMuted
    }
    BoxWithConstraints(
        modifier = Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .clip(shape)
            .clickable(
                enabled = row.url.isNotBlank(),
                role = Role.Button,
                onClickLabel = "在浏览器打开 ${row.repo}",
                onClick = onOpen,
            )
            .background(if (rank % 2 == 0) Color(0xFFF4F1EA) else Color(0xFFFBF9F4))
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        val fontScale = LocalDensity.current.fontScale
        val tight = maxWidth < 270.dp || fontScale > 1.35f
        val inlineTopic = topic != null && maxWidth >= 330.dp && fontScale <= 1.15f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (tight) 5.dp else 7.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(if (tight) 25.dp else 29.dp)
                    .background(if (rank <= 3) RankGold else Color(0xFFE9E7E2), RoundedCornerShape(6.dp))
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$rank",
                    color = if (rank <= 3) RankBackground else Color(0xFF3A4048),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.repo.substringAfterLast('/'),
                    color = RankInk,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.repo.substringBefore('/', "") + " · " + starLine(row.stars, hasWeekly),
                    color = RankMuted,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.description.isNotBlank()) {
                    Text(
                        text = row.description,
                        color = RankInk,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!inlineTopic && topic != null) RankTopicChip(topic)
                // 图里每行下面那条细标签带：主语种 / 本周涨幅 / 名次变化，只画真实算得出来的。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    row.language?.takeIf { it.isNotBlank() }?.let { RankMiniTag(it, RankInk) }
                    if (hasWeekly && week != null && week > 0 && row.stars > week) {
                        val growth = week.toLong() * 100 / (row.stars - week).coerceAtLeast(1)
                        RankMiniTag("本周 +$growth%", RankUp)
                    }
                    if (week != null && previous != null && previous > 0 && week > previous) {
                        RankMiniTag("名次在升", RankUp)
                    } else if (week != null && previous != null && week < previous) {
                        RankMiniTag("名次在降", RankDown)
                    }
                }
            }
            if (inlineTopic && topic != null) RankTopicChip(topic)
            Column(
                modifier = Modifier.widthIn(min = if (tight) 60.dp else 76.dp, max = if (tight) 97.dp else 108.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (hasWeekly) week?.let(::signed) ?: "—" else thousands(row.stars),
                    color = if (hasWeekly && week == null) RankMuted else RankGold,
                    fontSize = if (tight) 14.sp else 16.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.End,
                )
                if (hasWeekly) {
                    if (previous != null) {
                        Text(
                            text = "上期 ${signed(previous)}",
                            color = RankMuted,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            textAlign = TextAlign.End,
                        )
                        pace?.let {
                            Text(
                                text = it,
                                color = paceColor,
                                fontSize = 9.sp,
                                lineHeight = 12.sp,
                                textAlign = TextAlign.End,
                            )
                        }
                    } else {
                        Text(
                            text = if (week == null) "缺周快照" else "暂无上期",
                            color = RankMuted,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            textAlign = TextAlign.End,
                        )
                    }
                } else {
                    Text("总 Star", color = RankMuted, fontSize = 9.sp, lineHeight = 12.sp, textAlign = TextAlign.End)
                }
            }
        }
    }
}

/** 分类标签：用的是仓库自己在 GitHub 上挂的 topic，不按名字猜。 */
@Composable
private fun RankTopicChip(topic: String) {
    val label = when (topic) {
        "ai-agent" -> "Agent"
        "llm" -> "大模型"
        "ai" -> "AI"
        else -> topic
    }
    val tint = when (topic) {
        "ai-agent" -> ChipAgent
        "llm" -> ChipModel
        "ai" -> ChipAi
        else -> ChipOther
    }
    Text(
        text = label,
        color = Color(0xFFFFFFFF),
        fontSize = 9.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 65.dp)
            .background(tint, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** 一周一个日期的写法：`2026.11.22`。 */
private fun rankDate(at: Long): String =
    Instant.fromEpochMilliseconds(at).toLocalDateTime(TimeZone.UTC).date.toString().replace('-', '.')

/** 有周净增时副标题写「总 N ★」，没有时只说 star 总数。 */
private fun starLine(stars: Int, weekly: Boolean): String =
    if (weekly) "总 ${thousands(stars)} ★" else "${thousands(stars)} ★"

/** 这一周比上一周快多少（百分数）；估不出来时返回 0。 */
private fun weekIncreasePercent(week: Int?, previous: Int?): Long =
    if (week == null || previous == null || previous <= 0) 0L else (week.toLong() - previous) * 100 / previous

/** `+2,284` / `−8`：正负号都用真符号，读的人一眼知道涨还是跌。 */
private fun signed(value: Int): String = if (value >= 0) "+${thousands(value)}" else "−${thousands(-value)}"

/** 千分位：`11,684`。 */
private fun thousands(value: Int): String {
    val digits = value.toString()
    val builder = StringBuilder()
    for ((index, char) in digits.withIndex()) {
        if (index > 0 && (digits.length - index) % 3 == 0) builder.append(',')
        builder.append(char)
    }
    return builder.toString()
}

/** 相对时间：卡片和页面顶部都要一句"多久之前"。 */
private fun agoText(at: Long): String {
    if (at <= 0L) return "还没抓过"
    val diff = currentTimeMillis() - at
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        else -> "${diff / 86_400_000L} 天前"
    }
}
