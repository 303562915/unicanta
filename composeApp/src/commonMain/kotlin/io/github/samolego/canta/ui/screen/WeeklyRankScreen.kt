package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.weekly_rank_47
import io.github.samolego.canta.generated.resources.weekly_rank_47_thumb
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import org.jetbrains.compose.resources.painterResource

/**
 * 一条榜单：名次、仓库、总 star、分类、本期净增、上周对比、一句话说明，加一个分类色。
 *
 * 这些都是定值，不做成 `StateFlow`：一期周榜是某一周的快照，不会在开着页面的时候变。
 */
internal data class WeeklyRankRow(
    val rank: Int,
    val repo: String,
    /** 总 star，照原榜写作「47.4 万」。 */
    val total: String,
    val category: String,
    /** 本期净增，带正负号。 */
    val delta: String,
    val note: String,
    /** 上周那一格：`上周 +2,284 · 加速 +156%` / `上周 +11,678 · 持平` / `上周 +172 · 放缓 −8%`。 */
    val previous: String,
    /** 分类色，与海报上那个 chip 同一支。 */
    val accent: Color,
)

/** 一期榜单：期次、抓取区间、三句摘要、口径说明和十条行。 */
internal data class WeeklyRankWeek(
    val week: Int,
    val range: String,
    /** 这一期的一句话结论，海报上写在右上角那块。 */
    val brief: String,
    /** 净增比上周快的条数 / 一共测了几条。 */
    val accelerating: Int,
    val measured: Int,
    /** 第一名的净增占全榜的比例，与上一期那个数。 */
    val topShare: String,
    val topSharePrevious: String,
    val rules: List<String>,
    val rows: List<WeeklyRankRow>,
)

/** 海报上那支黄：名次前三位与净增数都用它。 */
private val RankGold = Color(0xFFFFD60A)

/** 名次 4 名往后的灰：看得见，但不抢眼。 */
private val RankDimmed = Color(0xFF5F6A73)

/**
 * 第 47 周（2026.11.16 — 11.22）的榜单。
 *
 * 写死在客户端而不是从后端拉：这一期是「同批 10 个仓库两次快照的 src 差值」算出来的死数，
 * 后端没有这张表；要做成每周自动更新，得先有一个跑 GitHub 快照的定时任务，那是另一件事。
 */
internal val weeklyRankWeek47 = WeeklyRankWeek(
    week = 47,
    range = "2026.11.16 — 11.22",
    brief = "中段在发力",
    accelerating = 8,
    measured = 10,
    topShare = "45%",
    topSharePrevious = "60%",
    rules = listOf(
        "口径：同批 10 个仓库在 11—16 与 11—22 两次快照的 star 差值，整 7 天（上期 5 天）。",
        "两期榜单面板相同，可逐行对比；不混新仓库，避免用没测过的仓充数。",
    ),
    rows = listOf(
        WeeklyRankRow(
            1, "rea", "47.4 万", "逆向", "+11,684",
            "用 Agent 逆向分析一个 App 到底怎么跑的", "上周 +11,678 · 持平", Color(0xFFFF897B),
        ),
        WeeklyRankRow(
            2, "superpowers", "32.4 万", "技能框架", "+5,854",
            "把软件工程流程封装成 AI 能用的技能包", "上周 +2,284 · 加速 +156%", Color(0xFF84C9FF),
        ),
        WeeklyRankRow(
            3, "ui-ux-pro-max-skill", "15.3 万", "设计", "+2,774",
            "给 AI 装上设计师脑子，出的页面不再土", "上周 +2,123 · 加速 +31%", Color(0xFFFFA1C8),
        ),
        WeeklyRankRow(
            4, "hermes-agent", "26.5 万", "Agent", "+1,702",
            "会越用越懂你的 AI 助手，可以自己部署", "上周 +1,152 · 加速 +48%", Color(0xFFFFD400),
        ),
        WeeklyRankRow(
            5, "n8n", "21.2 万", "自动化", "+1,187",
            "拖拽拼流程做自动化，自带 AI 节点", "上周 +571 · 加速 +108%", Color(0xFF95E6AB),
        ),
        WeeklyRankRow(
            6, "spec-kit", "15.1 万", "开发流程", "+1,062",
            "把需求写成 AI 能照着执行的规格书", "上周 +722 · 加速 +47%", Color(0xFFF8CB92),
        ),
        WeeklyRankRow(
            7, "openclaw", "39.5 万", "Agent", "+502",
            "AI 直接接管你的电脑，跨系统执行任务", "上周 +225 · 加速 +123%", Color(0xFFFFD400),
        ),
        WeeklyRankRow(
            8, "open-webui", "15.9 万", "界面", "+498",
            "给自己搭一个网页版 ChatGPT，数据留本地", "上周 +430 · 加速 +16%", Color(0xFFC6A9F9),
        ),
        WeeklyRankRow(
            9, "langchain", "10 万", "框架", "+397",
            "搭 Agent 的基础框架，开发者天天用", "上周 +249 · 加速 +59%", Color(0xFF8BDDDB),
        ),
        WeeklyRankRow(
            10, "langflow", "15.7 万", "流程编排", "+159",
            "拖拽连线就能搭 AI 流程，不写代码", "上周 +172 · 放缓 −8%", Color(0xFFFBB56D),
        ),
    ),
)

/**
 * 帖子列表顶上那张卡：一张海报大小的缩略图 + 期次 + 一句结论。
 *
 * 放在帖子页最上面而不是底栏多一格：它是一周一次的内容，不是每天都要点的地方；
 * 底栏已经挤了六七格，再塞一格会把每一格都压窄。
 */
@Composable
internal fun WeeklyRankTeaserCard(
    week: WeeklyRankWeek = weeklyRankWeek47,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = FlSurfaceLevel.Block.colorOf(scheme),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.weekly_rank_47_thumb),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .width(58.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GitHub AI 开源周榜",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "第 ${week.week} 周 · ${week.range}",
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${week.brief} · ${week.accelerating}/${week.measured} 个在加速",
                    fontSize = 11.sp,
                    color = RankGold,
                )
            }
            Text(
                text = "查看",
                fontSize = 11.sp,
                color = scheme.primary,
            )
        }
    }
}

/**
 * 开源周榜这一页：整张海报 + 可逐条读的榜单。
 *
 * 海报是这一页的主体（它本来就是一张图），下面的列表是为了两件事：图在窄屏上放缩到看不清
 * 数字时还能读到数，以及长按能选中仓库名去搜。
 */
@Composable
internal fun WeeklyRankScreen(
    onNavigateBack: () -> Unit,
    week: WeeklyRankWeek = weeklyRankWeek47,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            ScreenTopBar(
                onNavigateBack = onNavigateBack,
                title = "开源周榜",
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "poster") {
                Image(
                    painter = painterResource(Res.drawable.weekly_rank_47),
                    contentDescription = "GitHub AI 开源周榜 第 ${week.week} 周",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        // 先留边距再按可用宽度定高度：比例锁在这里，宽窗口（平板横屏、桌面端）
                        // 不会因为 LazyColumn 给的无界高度把海报上下各裁掉一截。
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .aspectRatio(1080f / 1886f)
                        .clip(RoundedCornerShape(18.dp)),
                )
            }
            item(key = "summary") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "第 ${week.week} 周 · ${week.range}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "按本周净增排 · 第一名占 ${week.topShare}（上期 ${week.topSharePrevious}）· " +
                            "${week.accelerating}/${week.measured} 个在加速 · ${week.brief}",
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            items(week.rows, key = { row -> row.rank }) { row ->
                WeeklyRankRowBlock(row)
            }
            item(key = "rules") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    week.rules.forEach { line ->
                        Text(
                            text = line,
                            fontSize = 10.sp,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }
        }
    }
}

/** 榜单的一行：名次 / 仓库与说明 / 分类色 / 本期净增。 */
@Composable
private fun WeeklyRankRowBlock(row: WeeklyRankRow) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.width(26.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                text = "${row.rank}",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (row.rank <= 3) RankGold else RankDimmed,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.repo,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "· 总 ${row.total}",
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.note,
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(row.accent),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = row.category,
                    fontSize = 10.sp,
                    color = row.accent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = row.previous,
                    fontSize = 10.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = row.delta,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = RankGold,
        )
    }
}
