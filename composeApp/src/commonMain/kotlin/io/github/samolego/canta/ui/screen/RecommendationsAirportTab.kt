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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import io.github.samolego.canta.ui.component.rememberRememberedListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.recommend.CommunityAirport
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.recommend_airport_vote_empty
import io.github.samolego.canta.generated.resources.recommend_airport_vote_foot
import io.github.samolego.canta.generated.resources.recommend_tab_airport_vote
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.IdentitySetup
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * 跨「切走再切回来」要留住的那一份。
 *
 * 这一块只在「机场」和「机场投票」这两格组合，切到应用目录再切回来整棵子树会被丢掉，于是列表、
 * 已取到的订阅和滚动位置全部归零，还要重跑五趟网络。放在这里由推荐页持有（它是常驻的），
 * 回来时先画上一份、数据还在就不重拉。两格共用同一个实例：在榜单里投的一票，切回机场那一格
 * 立刻就是新的票数。
 */
internal class AirportTabState {
    var airports by mutableStateOf<List<CommunityAirport>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var subscriptions by mutableStateOf<Map<Long, String>>(emptyMap())
    /** 投一票、兑换一次、发一件之后 +1：只有它变了才重新拉。 */
    var reloadKey by mutableIntStateOf(0)
    /** 上一次拉完之后 [reloadKey] 的值：相等就说明这一屏已经是最新的，切回来不必再拉。 */
    var loadedKey by mutableIntStateOf(-1)
}

/** 认同这一条：与社区队列的绿色同一个。 */
internal val AirportAgree = Color(0xFF2FA84F)

/** 不认同： quieter 的灰红。 */
internal val AirportDisagree = Color(0xFFC0564C)

/** 星星的金色。 */
private val StarGold = Color(0xFFE8A33D)

/** 机场列表一行里图标方块的边长。 */
internal val AirportTileSize = 34.dp

/**
 * 一页几条。
 *
 * 榜单现在有六十多家，一口气全画出来会卡；页面先画这一页，划到底再追加同样多，直到画完。
 * 十条刚好是手机一屏多一点，慢机也测得出来，快机划两下就到底。
 */
private const val AirportPageSize = 10

/** 机场类型：值 → 页面上的名字，顺序就是选择器的顺序。 */
private val AirportKinds = listOf(
    "airport" to "机场",
    "vpn" to "VPN",
    "self" to "自建",
)

/**
 * 推荐页的第三块：机场投票。
 *
 * 一件事说完：谁都能把自己在用的机场贴上来；别人认同/不认同决定它排多前，打星决定它多好用。
 * 订阅链接不在列表里——投过票或打过分才取得出来，这就是"投票"在这里的实际作用。
 *
 * **积分与兑换不在这里**：那是商城，自己一页（[StoreScreen]）。以前两块挤在同一条列表上，
 * 看起来像"商城是机场榜单的一部分"，所以拆开了。
 *
 * @param repository 社区后端；null 时这一块只显示说明，不装作有数据。
 * @param platform 用来提示、复制、开链接。
 * @param author 署名，[authorId] 为空时当身份用。
 * @param authorId 身份码：投票挂在它上面。
 */
@Composable
internal fun RecommendationsAirportTab(
    repository: PlanRepository?,
    platform: CantaPlatform,
    author: String,
    authorId: String,
    state: AirportTabState,
    modifier: Modifier = Modifier,
    /** 名字与头像设过了没有：没设过能看能投能兑换，但分享不了新机场（见 [IdentitySetup]）。 */
    setupGate: IdentitySetup = IdentitySetup.UNSET,
    /**
     * true = 「机场投票」那一格：同一份机场数据，按认同票排成榜单，不再是分享/收录的列表。
     *
     * 两种模式共用这一个 composable 而不是各写一份：拉数据、投票、打分、取订阅、详情弹窗
     * 全都一样，差的是列表本身画什么。分成两份就等于把这些各抄一遍，改一处漏一处。
     */
    board: Boolean = false,
    /** 滚动位置存在哪一格：两个 tab 各给一个键，不然第四个 tab 会接着机场的滚动位置往上长。 */
    stateKey: String = "airport",
    /**
     * 点开一条机场：交给调用方导航到整页详情（[AirportDetailScreen]）。
     *
     * 以前这里是弹窗：卡片里多出几行会顶开整条列表，所以拿弹窗盖在原位。可详情里还要读评论、
     * 回评论，弹窗高度有限、外面压着遮罩，回一句要跟滚动条抢——所以改成整页，这一层只负责说
     * "点的是哪一条"。
     */
    onOpenAirport: (CommunityAirport) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val who = remember(authorId, author) { authorId.ifEmpty { author } }
    val listState = rememberRememberedListState(stateKey)

    var airports by remember(state) { mutableStateOf(state.airports) }
    var loading by remember(state) { mutableStateOf(state.loading) }
    var error by remember(state) { mutableStateOf(state.error) }
    var reloadKey by remember(state) { mutableIntStateOf(state.reloadKey) }
    /** 正在进行的那一件事：`airport:7`——同一个 id 只发一趟。 */
    var busyKey by remember { mutableStateOf<String?>(null) }
    /** 已经取到的订阅链接，按条目 id 存：再点一次就不再跑一趟网络。 */
    var subscriptions by remember(state) { mutableStateOf(state.subscriptions) }
    var airportComposer by remember { mutableStateOf(false) }
    var airportBusy by remember { mutableStateOf(false) }
    var airportError by remember { mutableStateOf<String?>(null) }
    /** 正在向 airport-head 之后追加的那一页位置：首页 10 条，往下划一页给 `pageSize` 条。 */
    var visiblePages by remember { mutableIntStateOf(1) }

    // 每次拿到新的一屏就写回外层：切走再回来先画它，而不是从空列表开始转圈。
    LaunchedEffect(airports, loading, error, subscriptions, reloadKey) {
        // 列表换了（投过票重排、刚分享完又拉了一趟）就从第一页重来：翻到第 5 页的人，
        // 对着一份重排过的榜单继续往下划，看到的不是他想看的顺序。
        visiblePages = 1
        state.airports = airports
        state.loading = loading
        state.error = error
        state.subscriptions = subscriptions
        state.reloadKey = reloadKey
    }

    LaunchedEffect(repository, who, reloadKey) {
        val repo = repository
        if (repo == null) {
            loading = false
            return@LaunchedEffect
        }
        // 切回这一块时 reloadKey 与上次拉完的值相同：这份数据就是刚拉过的，不必再跑一趟网络。
        if (state.loadedKey == reloadKey) return@LaunchedEffect
        if (airports.isEmpty()) loading = true
        val freshAirports = repo.airports(who)
        if (freshAirports != null) {
            airports = freshAirports
            error = null
        } else {
            error = repo.lastError ?: "机场列表没拉到，检查一下网络或代理。"
        }
        loading = false
        state.loadedKey = reloadKey
    }

    val vote: (CommunityAirport, Boolean) -> Unit = vote@{ entry, agree ->
        val repo = repository
        if (repo == null) {
            platform.showMessage("社区后端没连上，投票没发出去。")
            return@vote
        }
        if (busyKey != null) return@vote
        busyKey = "airport:${entry.id}"
        scope.launch {
            val fresh = repo.voteAirport(entry.id, who, agree)
            busyKey = null
            if (fresh == null) {
                platform.showMessage("没投上：${repo.lastError ?: "检查一下网络再试"}")
            } else {
                reloadKey++
            }
        }
    }

    val rate: (CommunityAirport, Int) -> Unit = rate@{ entry, stars ->
        val repo = repository
        if (repo == null) {
            platform.showMessage("社区后端没连上，评分没发出去。")
            return@rate
        }
        if (busyKey != null) return@rate
        busyKey = "airport:${entry.id}"
        scope.launch {
            val fresh = repo.rateAirport(entry.id, who, stars)
            busyKey = null
            if (fresh == null) {
                platform.showMessage("没评上：${repo.lastError ?: "检查一下网络再试"}")
            } else {
                reloadKey++
            }
        }
    }

    val subscription: (CommunityAirport) -> Unit = sub@{ entry ->
        subscriptions[entry.id]?.let { link ->
            platform.copyToClipboard(link)
            platform.showMessage("订阅链接已复制")
            return@sub
        }
        // 不解锁也照样问一次：门槛由服务端按身份判断，本地那面 `unlocked` 只是上一次列表的
        // 快照，拿着它挡人会把"已经投过票、但列表还没刷新"的人挡在门外。
        val repo = repository ?: return@sub
        scope.launch {
            val fresh = repo.airportSubscription(entry.id, who)
            when {
                fresh == null -> platform.showMessage("订阅没取到：${repo.lastError ?: "检查一下网络"}")
                fresh.unlocked && fresh.subscribeUrl.isNotBlank() -> {
                    subscriptions = subscriptions + (entry.id to fresh.subscribeUrl)
                    platform.copyToClipboard(fresh.subscribeUrl)
                    platform.showMessage("订阅链接已复制")
                }
                else -> platform.showMessage("先投一票或打个星，才能取订阅")
            }
        }
    }

    when {
        loading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        else -> Column(modifier = modifier.fillMaxSize()) {
            // 「划到底了没有」用的是 listState 的布局信息，所以它得在 LazyColumn 外面算完再给
            // 懒列表用：放在 item 里算，等于让每一帧的组合去读它自己的测量结果。
            val shownCount = (visiblePages * AirportPageSize).coerceAtMost(airports.size)
            val hasMore = shownCount < airports.size
            val atEnd by remember(shownCount, hasMore) {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    hasMore && last >= info.totalItemsCount - 2
                }
            }
            LaunchedEffect(atEnd, shownCount, hasMore) {
                if (atEnd) visiblePages++
            }

            // 榜单的顺序：认同票多的排前面；票一样多时看评分人数（评的人多说明更被用过），
            // 再一样就按创建时间——先上来的排前面，免得同票的两家每次刷新都换位置。
            // 后端给的是 ISO 时间串，升序即「早的在先」；缺时间的排到最后，不抢位置。
            val ranked = remember(airports) {
                airports.sortedWith(
                    compareByDescending<CommunityAirport> { it.agrees }
                        .thenByDescending { it.ratingCount }
                        .thenBy { it.createdAt.ifEmpty { "9999" } },
                )
            }
            val boardTitle = stringResource(Res.string.recommend_tab_airport_vote)

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                if (board) {
                    // 投票榜：只有排名、名字和那一行小字。投票、打分、取订阅、评论都在点开的
                    // 详情里——同一层操作在两个 tab 上必须完全一样，所以弹的是同一个弹窗。
                    item(key = "airport-vote-head") {
                        AirportSectionHeader(label = boardTitle, count = ranked.size)
                    }

                    if (ranked.isEmpty()) {
                        item(key = "airport-vote-empty") {
                            Note(
                                text = error ?: stringResource(Res.string.recommend_airport_vote_empty),
                                inset = 16.dp,
                            )
                        }
                    }

                    itemsIndexed(
                        ranked.take(visiblePages * AirportPageSize),
                        key = { _, entry -> "airport-rank-${entry.id}" },
                    ) { index, entry ->
                        AirportVoteRow(
                            rank = index + 1,
                            entry = entry,
                            onClick = { onOpenAirport(entry) },
                        )
                    }

                    item(key = "airport-vote-foot") {
                        Note(
                            text = if (hasMore) {
                                "已经拉出 $shownCount / ${ranked.size} 家，继续往下划就再来一页。"
                            } else {
                                stringResource(Res.string.recommend_airport_vote_foot)
                            },
                            inset = 16.dp,
                        )
                    }
                } else {
                    item(key = "airport-new") {
                        EntryRow(
                            title = "分享一个机场",
                            hint = "贴官网和订阅就行；订阅只给投过票、打过分的人。",
                            inset = 16.dp,
                            onClick = {
                                airportError = null
                                airportComposer = true
                            },
                        )
                    }

                    item(key = "airport-head") {
                        AirportSectionHeader(label = "机场投票", count = airports.size)
                    }

                    if (airports.isEmpty()) {
                        item(key = "airport-empty") {
                            Note(
                                text = error ?: "还没有人分享机场。你在用的那个，贴上来让大家评一评。",
                                inset = 16.dp,
                            )
                        }
                    }

                    items(airports.take(visiblePages * AirportPageSize), key = { "airport-${it.id}" }) { entry ->
                        AirportCard(
                            entry = entry,
                            busy = busyKey == "airport:${entry.id}",
                            canAct = who.isNotEmpty(),
                            subscribeUrl = subscriptions[entry.id].orEmpty(),
                            inset = 16.dp,
                            onOpenDetail = { onOpenAirport(entry) },
                            onVote = { agree -> vote(entry, agree) },
                            onRate = { stars -> rate(entry, stars) },
                            onSubscribe = { subscription(entry) },
                            onOpenUrl = {
                                if (entry.url.isNotBlank()) platform.openUrl(entry.url)
                            },
                        )
                    }

                    // 一次只画 AirportPageSize 条，多的划到底再给一页：一口气画 60 张卡片，
                    // 每一张都带可点区域，组合和测量都在争帧。判据是「划到了列表末尾」，
                    // 用 `lastVisibleItemIndex` 而不是在页脚上做 `LaunchedEffect`——后者在列表
                    // 短到根本滚不动时会立刻再给一页，等于没分页。
                    item(key = "board-foot") {
                        Note(
                            text = if (hasMore) {
                                "已经拉出 $shownCount / ${airports.size} 家，继续往下划就再来一页。"
                            } else {
                                "认同 / 不认同决定它排多前，五星决定它多好用；投过票或打过星，才取得到订阅。"
                            },
                            inset = 16.dp,
                        )
                    }
                }
            }
        }
    }

    if (airportComposer) {
        AirportComposerDialog(
            submitting = airportBusy,
            error = airportError,
            onDismiss = {
                airportComposer = false
                airportError = null
            },
            onSubmit = submit@{ name, kind, url, subscribeUrl, summary ->
                val repo = repository
                if (!setupGate.ready) {
                    airportError = setupGate.refusal
                    return@submit
                }
                if (repo == null) {
                    airportError = "社区后端没连上，提交没发出去。"
                    return@submit
                }
                scope.launch {
                    airportBusy = true
                    airportError = null
                    val id = repo.addAirport(
                        name = name,
                        url = url,
                        subscribeUrl = subscribeUrl,
                        kind = kind,
                        summary = summary,
                        author = author,
                        authorId = who,
                    )
                    airportBusy = false
                    if (id != null) {
                        airportComposer = false
                        reloadKey++
                        platform.showMessage("已分享，等大家来评")
                    } else {
                        airportError = repo.lastError ?: "没发出去：检查一下网络再试"
                    }
                }
            },
            setupGate = setupGate,
        )
    }

    // 详情是**另一页**，不是这一层里的弹窗：点某一条由调用方导航过去（见 [onOpenAirport]），
    // 回来时这一页还在原来的滚动位置上——滚动位置记在 [listState] 里，由 rememberRememberedListState 存着。
}

/** 钱包卡：余额、今天领没领、连签几天，以及"这些分哪来的"。 */
@Composable
private fun EntryRow(
    title: String,
    hint: String,
    inset: Dp,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+", color = scheme.onPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 一个区块的标题，与目录那一半的 SectionHeader 同一副样子。 */
@Composable
private fun AirportSectionHeader(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 一句解释 / 空状态。 */
@Composable
private fun Note(text: String, inset: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = inset, end = inset, top = 8.dp, bottom = 2.dp),
    )
}

/**
 * 榜单上的一行：名次、图标、名字，以及 `metaLine` 那一行小字（类型 · 评分 · 票数）。
 *
 * 前三名用强调色，第 4 名往后只画普通颜色的数字：一眼看得出「前几名是这几家」，又不把整列染成
 * 彩虹。整行可点，点开的是机场详情——投票、打分、取订阅、评论都在那里，与分享那一格是同一个弹窗，
 * 所以两个 tab 上「点进去能做的事」不会出现两套。
 */
@Composable
private fun AirportVoteRow(
    rank: Int,
    entry: CommunityAirport,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val top = rank <= 3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AirportTileSize)
                .clip(RoundedCornerShape(50))
                .background(
                    if (top) {
                        scheme.primary.copy(alpha = 0.14f)
                    } else {
                        FlSurfaceLevel.Page.raised.colorOf(scheme)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (top) FontWeight.Bold else FontWeight.Medium,
                color = if (top) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(9.dp))
        Box(
            modifier = Modifier
                .size(AirportTileSize)
                .clip(RoundedCornerShape(10.dp))
                .background(scheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.tileMark,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = entry.kindLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSecondaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(scheme.secondaryContainer)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Text(
                text = entry.metaLine.ifEmpty { "还没人评" },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "详情",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.primary.copy(alpha = 0.10f))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

/** 一条机场：名字、类型、评分、票，以及一条能点的星和订阅。 */
@Composable
private fun AirportCard(
    entry: CommunityAirport,
    busy: Boolean,
    canAct: Boolean,
    subscribeUrl: String,
    inset: Dp,
    onOpenDetail: () -> Unit,
    onVote: (Boolean) -> Unit,
    onRate: (Int) -> Unit,
    onSubscribe: () -> Unit,
    onOpenUrl: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerLow)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(AirportTileSize)
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.tileMark,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable(role = Role.Button, onClick = onOpenDetail),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = entry.kindLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(scheme.secondaryContainer)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                Text(
                    text = entry.metaLine.ifEmpty { "还没人评" },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.summary.isNotBlank()) {
                    Text(
                        text = entry.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        // 卡片只放两行：完整的那段话在详情弹窗里。
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            AirportVotePill(
                label = "认同",
                count = entry.agrees,
                selected = entry.mine == true,
                color = AirportAgree,
                enabled = canAct && !busy,
                onClick = { onVote(true) },
            )
            Spacer(modifier = Modifier.width(4.dp))
            AirportVotePill(
                label = "不认同",
                count = entry.disagrees,
                selected = entry.mine == false,
                color = AirportDisagree,
                enabled = canAct && !busy,
                onClick = { onVote(false) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            StarRow(myStars = entry.myStars, enabled = canAct && !busy, onRate = onRate)
            Spacer(modifier = Modifier.width(8.dp))
            if (entry.hasSubscription) {
                Text(
                    text = if (subscribeUrl.isNotEmpty()) "复制订阅" else "取订阅",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(scheme.primary.copy(alpha = 0.10f))
                        .clickable(role = Role.Button, onClick = onSubscribe)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
            if (entry.url.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "官网",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                        .clickable(role = Role.Button, onClick = onOpenUrl)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!canAct) {
                Text(
                    text = "认不出身份，只能看",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "详情",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(scheme.primary.copy(alpha = 0.10f))
                        .clickable(role = Role.Button, onClick = onOpenDetail)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
        }

        if (subscribeUrl.isNotEmpty()) {
            Text(
                text = subscribeUrl,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

/** 五颗星，按过的填上。点第 n 颗就是 n 星，重复点是改分。 */
@Composable
internal fun StarRow(myStars: Int, enabled: Boolean, onRate: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { star ->
            Text(
                text = "★",
                fontSize = 15.sp,
                color = if (star <= myStars) StarGold else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .clickable(enabled = enabled, onClick = { onRate(star) })
                    .padding(end = 2.dp),
            )
        }
    }
}

/** 一侧的票：选中的填色，没选的是一块浅底。 */
@Composable
internal fun AirportVotePill(
    label: String,
    count: Int,
    selected: Boolean,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) color else FlSurfaceLevel.Page.raised.colorOf(scheme))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Text(text = "✓", fontSize = 9.sp, color = Color.White)
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(
            text = if (count > 0) "$label $count" else label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else scheme.onSurface,
            maxLines = 1,
        )
    }
}

/** 货架上的一件商品：标题、价格、库存，和兑换按钮。 */
@Composable
private fun AirportComposerDialog(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String) -> Unit,
    /** 名字与头像设过了没有：没设过提交键灰掉并说一句为什么（见 [IdentitySetup]）。 */
    setupGate: IdentitySetup = IdentitySetup.UNSET,
) {
    val scheme = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var kindIndex by remember { mutableIntStateOf(0) }
    var url by remember { mutableStateOf("") }
    var subscribeUrl by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }

    val canSend = name.trim().isNotEmpty() && !submitting && setupGate.ready

    CantaDialog(
        onDismissRequest = onDismiss,
        title = "分享一个机场",
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
            Button(
                enabled = canSend,
                onClick = {
                    onSubmit(
                        name.trim(),
                        AirportKinds[kindIndex].first,
                        url.trim(),
                        subscribeUrl.trim(),
                        summary.trim(),
                    )
                },
            ) { Text(if (submitting) "正在提交…" else "提交") }
        },
    ) {
        // 差哪一步先摆出来：藏在灰按钮里没人看得懂。
        if (!setupGate.ready) {
            Text(
                text = setupGate.prompt,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.error,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            text = "名字和一句介绍就够。订阅链接可以留空；填了的话，只有投过票或打过分的人才取得出来。",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlSegmentedControl(
            segments = AirportKinds.map { FlSegment(it.first, it.second) },
            selectedIndex = kindIndex,
            onSelect = { kindIndex = it },
            // 对话框自己已经有 22dp 的边距，选择器再缩 16 就会比下面的输入框窄一截。
            insetHorizontal = 0.dp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("名字") },
            placeholder = { Text("现在在用的那家") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it.take(400) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("官网 / 邀请页（可留空）") },
            placeholder = { Text("https://…") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = subscribeUrl,
            onValueChange = { subscribeUrl = it.take(600) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("订阅链接（可留空）") },
            placeholder = { Text("https://…/api/v1/client/subscribe?token=…") },
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = summary,
            onValueChange = { summary = it.take(400) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("一句介绍") },
            placeholder = { Text("线路、价格区间、什么协议") },
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = error, style = MaterialTheme.typography.labelSmall, color = scheme.error)
        }
    }
}

/** 发布商品的表单。 */
