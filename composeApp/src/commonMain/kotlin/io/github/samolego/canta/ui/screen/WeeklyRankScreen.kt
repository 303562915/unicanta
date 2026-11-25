package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.rank.RankRow
import io.github.samolego.canta.data.rank.WeeklyRankRepository
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.currentTimeMillis

/** 前三名与净增数那支黄：跟海报上同一个色。 */
private val RankGold = Color(0xFFFFD60A)

/** 第 4 名往后的灰：看得见，但不抢眼。 */
private val RankDimmed = Color(0xFF5F6A73)

/**
 * 帖子列表顶上那一行：只有文字，不加缩略图也不列前三名。
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
 * 开源周榜这一页：真的去 GitHub 拉一次，再按本机快照算出本周净增。
 *
 * 进页面就刷新（这里才是唯一会打 GitHub 的地方），失败时把盘上那一屏继续画着并说明原因。
 */
@Composable
internal fun WeeklyRankScreen(
    repository: WeeklyRankRepository,
    onNavigateBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
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

    Scaffold(
        topBar = {
            ScreenTopBar(
                onNavigateBack = onNavigateBack,
                title = "开源周榜",
                actions = {
                    TextButton(
                        onClick = { reloads++ },
                        enabled = !loading,
                    ) {
                        Text("刷新", fontSize = 12.sp)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "state") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "数据来自 GitHub Search API · 本周净增由本机按天快照算出",
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = board?.let {
                            "抓取于 ${agoText(it.fetchedAt)} · 本机已攒 ${it.snapshotDays} 天快照"
                        } ?: "还没有抓到数据",
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            if (loading && board == null) {
                item(key = "loading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    }
                }
            }

            error?.let { message ->
                item(key = "error") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = scheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "这次没拉到：$message",
                                fontSize = 11.sp,
                                color = scheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (board != null) "下面是上次抓到的，点右上角「刷新」再试。" else "点右上角「刷新」再试。",
                                fontSize = 10.sp,
                                color = scheme.onErrorContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            val rows = board?.rows.orEmpty()
            if (rows.isNotEmpty()) {
                item(key = "legend") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(
                            text = if (board?.hasWeekly == true) "本周净增（近 7 天）" else "按 star 总数（本机还没攒够 7 天快照）",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                        if (board?.hasWeekly != true) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "再过几天，这一列会换成真实的周净增：每天打开一次，本机就会多一个采样点。",
                                fontSize = 10.sp,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                itemsIndexed(rows, key = { _, row -> row.repo }) { index, row ->
                    RankRowBlock(rank = index + 1, row = row)
                }
            }

            item(key = "rules") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "口径：候选池是 GitHub 上带 topic:llm / ai / ai-agent 标签、按 star 最多的仓库；" +
                            "「本周净增」= 今天与 7 天前那两个采样点的 star 差；「上期」再看前 7 天，用来判断加速还是放缓。",
                        fontSize = 10.sp,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "为什么第一次装上看不到净增：GitHub 只给当前 star 总数，不提供历史曲线；" +
                            "净增只能靠本机一天一个采样点自己攒，攒不满 7 天就不编数字。",
                        fontSize = 10.sp,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/** 榜单的一行：名次 / 仓库与说明 / 本周净增。 */
@Composable
private fun RankRowBlock(rank: Int, row: RankRow) {
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
                text = "$rank",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (rank <= 3) RankGold else RankDimmed,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.repo,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = row.description,
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(RankGold.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "★ ${thousands(row.stars)}",
                    fontSize = 10.sp,
                    color = scheme.onSurfaceVariant,
                )
                row.previous?.let { previous ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "上期 ${signed(previous)} · ${pace(row.week, previous)}",
                        fontSize = 10.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = row.week?.let { signed(it) } ?: "—",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (row.week != null) RankGold else RankDimmed,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = if (row.week != null) "近 7 天" else "待攒够",
                fontSize = 9.sp,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

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

/** 这一期比上期快还是慢。 */
private fun pace(week: Int?, previous: Int): String {
    if (week == null) return "—"
    if (previous <= 0) return if (week > 0) "加速" else "持平"
    val ratio = (week - previous) * 100 / previous
    return when {
        ratio > 5 -> "加速 +$ratio%"
        ratio < -5 -> "放缓 −${-ratio}%"
        else -> "持平"
    }
}
