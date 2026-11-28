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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.samolego.canta.data.cache.ScreenCache
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.recommend.AirportBallot
import io.github.samolego.canta.data.recommend.CommunityAirport
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.recommend_airport_vote_empty
import io.github.samolego.canta.generated.resources.recommend_airport_vote_foot
import io.github.samolego.canta.generated.resources.recommend_tab_airport
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
 *
 * 它是 **public** 而不是 internal：机场详情是另一条路由（[AirportDetailScreen]），那一页和这一页
 * 必须拿到同一个实例才谈得上"详情里投的票，回来列表就变了"。公开类型不能出现在公开函数的参数
 * 里，所以这里不能是 internal；这个类本身没有别处会用到的秘密，只是一个 UI 快照。
 */
class AirportTabState {
    var airports by mutableStateOf<List<CommunityAirport>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var subscriptions by mutableStateOf<Map<Long, String>>(emptyMap())
    /** 投一票、兑换一次、发一件之后 +1：只有它变了才重新拉。 */
    var reloadKey by mutableIntStateOf(0)
    /** 上一次拉完之后 [reloadKey] 的值：相等就说明这一屏已经是最新的，切回来不必再拉。 */
    var loadedKey by mutableIntStateOf(-1)

    /**
     * 详情页改了一条（投票 / 打分）之后：把新的那条写回列表这一份，并让列表下一趟重拉。
     *
     * 只改 `airports` 是不够的：`reloadKey` 不变时列表会拿 `loadedKey == reloadKey` 判断"这一屏
     * 已经是最新的"，于是返回列表仍旧画着旧票数、也不再问后端。两个一起动，屏幕和后端才对得上。
     */
    fun publish(updated: CommunityAirport) {
        airports = airports.map { if (it.id == updated.id) updated else it }
        reloadKey++
    }

    /**
     * 详情页取到订阅链接之后：记进列表这一份，回去那张卡片就直接写「复制订阅」，不用再取一趟。
     *
     * 空串不记：那是"这次没取到"，记住它只会让下次点击以为已经拿到。
     */
    fun rememberSubscription(id: Long, url: String) {
        if (url.isBlank()) return
        subscriptions = subscriptions + (id to url)
    }
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

/**
 * 分页深度存在哪一格。
 *
 * 与滚动位置分开两格：`scroll:page` 记的是"停在哪一行"，这一格记的是"列表画到第几页"——两件事，
 * 但必须在同一帧就读到，否则第一帧量到的列表比记忆的位置短，位置会被 `ScrollMemory` 顶到最后一条。
 */
private fun airportPagesKey(stateKey: String): String = "airport:pages:$stateKey"

/** 机场类型：值 → 页面上的名字，顺序就是选择器的顺序。 */
private val AirportKinds = listOf(
    "airport" to "机场",
    "vpn" to "VPN",
    "self" to "自建",
)

/**
 * 推荐页里的两格机场，共用这一份数据：[board] 为 false 是「机场推荐」，true 是「机场投票」。
 *
 *   * **机场推荐**：别人贴过的机场，一行一个，**点一下直接去它的网站**。这一格只做"在推荐"这件事，
 *     不在行里塞投票、打分、取订阅、官网这些入口——一条能点的行里有五个按钮，点哪儿算点哪儿就
 *     说不清了。
 *   * **机场投票**：同一批机场按认同票排成榜单，点一条进整页详情（[AirportDetailScreen]），
 *     投票、打分、取订阅、评论全在那里。
 *
 * **积分与兑换不在这里**：那是商城，自己一页（[StoreScreen]）。以前两块挤在同一条列表上，
 * 看起来像"商城是机场榜单的一部分"，所以拆开了。
 *
 * @param repository 社区后端；null 时这一块只显示说明，不装作有数据。
 * @param platform 用来提示、复制、开链接。
 * @param author 署名，[authorId] 为空时当身份用。
 * @param authorId 身份码：分享的机场挂在它上面。
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
     * true = 「机场投票」那一格：同一份机场数据，按认同票排成榜单，点一条进整页详情。
     *
     * 两种模式共用这一个 composable 而不是各写一份：拉数据、分页、分享表单全都一样，差的是列表
     * 本身画什么、点一条去哪。分成两份就等于把这些各抄一遍，改一处漏一处。
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
    var airportComposer by remember { mutableStateOf(false) }
    var airportBusy by remember { mutableStateOf(false) }
    var airportError by remember { mutableStateOf<String?>(null) }
    /**
     * 摊开到第几页。**首帧就要对**，所以跟着滚动位置一起落盘、首帧同步读回来。
     *
     * 只按"这次划了几页"从 1 起步是不行的：滚动位置是记着的（[rememberRememberedListState]），
     * 带着 index≈30 回来、列表却只画 10 条时，`guardIndex` 会在第一帧就把位置顶回最后一条
     * （`ScrollMemory.kt` 的量测值那时只有 12），紧接着保存副作用把新位置写盘——旧位置就真的丢了。
     * 所以深度也存一份：上次停在第六页，这次首帧就画到第六页。
     *
     * 不读 `listState.firstVisibleItemIndex`：那个值每滚一行就变，在组合里读它等于整块每帧重组。
     */
    var visiblePages by remember(stateKey) {
        mutableIntStateOf(
            (ScreenCache.get<Int>(airportPagesKey(stateKey)) ?: 1).coerceAtLeast(1),
        )
    }
    LaunchedEffect(stateKey, visiblePages) {
        ScreenCache.put(airportPagesKey(stateKey), visiblePages)
    }
    /** 正在投的那一条：同一个 id 只发一趟，别的行照常可点。 */
    var votingId by remember { mutableStateOf(0L) }

    // 每次拿到新的一屏就写回外层：切走再回来先画它，而不是从空列表开始转圈。
    //
    // **不在这里把 `visiblePages` 打回 1**：行内投一票也会改 `airports`，一改就缩回一页，正在第 3 页
    // 投票的人会看到列表突然变短、位置被顶到最后一条。页数只增不减；真需要从头看的场合（分享成功）
    // 自己滚回顶部（见下面分享那个分支）。
    //
    // 订阅链接不在这里写回：取订阅已经搬到详情页，这一层手里那一份只会是旧的，写回去会把详情页
    // 刚记下的链接盖掉。
    //
    // `reloadKey` 两头对齐用「取大」：详情页 `publish` 会把它 +1（表示"后端变了，回来要重拉"），
    // 无条件写回本地值会把那次 +1 压掉，重拉就永远不发生。
    LaunchedEffect(airports, loading, error, reloadKey, state.reloadKey) {
        if (state.reloadKey > reloadKey) reloadKey = state.reloadKey
        state.airports = airports
        state.loading = loading
        state.error = error
        if (reloadKey > state.reloadKey) state.reloadKey = reloadKey
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
        if (votingId != 0L || entry.ballotSettled) return@vote
        votingId = entry.id
        scope.launch {
            val fresh = repo.voteAirport(entry.id, who, agree)
            votingId = 0L
            if (fresh == null) {
                platform.showMessage("没投上：${repo.lastError ?: "检查一下网络再试"}")
            } else {
                // 先把这一行的三个数就地换掉：重拉整张榜会让已经翻到第几页的人回到第一页，
                // 而且票一投完列表就重排，手指还停在原来那一行上，看着像点错了行。
                val updated = entry.copy(
                    agrees = fresh.agrees,
                    disagrees = fresh.disagrees,
                    mine = fresh.mine,
                )
                airports = airports.map { if (it.id == updated.id) updated else it }
                state.publish(updated)
            }
        }
    }

    when {
        loading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        else -> Column(modifier = modifier.fillMaxSize()) {
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
            // 两个 tab 用的是同一份机场，但画的东西不一样：投票榜按票排，推荐列表按后端的顺序
            // （新的在前）。所以「一共几条」也得跟着各自那一份算，否则页脚会报错数。
            val ordered: List<CommunityAirport> = if (board) ranked else airports
            val shownCount = (visiblePages * AirportPageSize).coerceAtMost(ordered.size)
            val hasMore = shownCount < ordered.size

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                if (board) {
                    // 投票榜：名次、名字、一行小字。点一条进整页详情——投票、打分、取订阅、
                    // 评论都在那里，这一层只负责排名。
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
                        ranked.take(shownCount),
                        key = { _, entry -> "airport-rank-${entry.id}" },
                    ) { index, entry ->
                        AirportVoteRow(
                            rank = index + 1,
                            entry = entry,
                            voting = votingId == entry.id,
                            canVote = who.isNotEmpty(),
                            onVote = { agree -> vote(entry, agree) },
                            onClick = { onOpenAirport(entry) },
                        )
                    }

                    item(key = "airport-vote-foot") {
                        AirportFoot(
                            hasMore = hasMore,
                            shown = shownCount,
                            total = ranked.size,
                            doneLabel = stringResource(Res.string.recommend_airport_vote_foot),
                            moreLabel = "已经拉出 $shownCount / ${ranked.size} 家",
                            onLoadMore = { visiblePages++ },
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
                        AirportSectionHeader(
                            label = stringResource(Res.string.recommend_tab_airport),
                            count = airports.size,
                        )
                    }

                    if (airports.isEmpty()) {
                        item(key = "airport-empty") {
                            Note(
                                text = error ?: "还没有人分享机场。你在用的那个，贴上来让大家评一评。",
                                inset = 16.dp,
                            )
                        }
                    }

                    // 推荐这一格只做一件事：把别人贴过的机场列出来，点一下直接去它的网站。
                    // 投票、打分、订阅、评论都在「机场投票」点进去的那一页，这里不重复一遍——
                    // 一条能点的行里再塞四五个小按钮，点哪儿算点哪儿就说不清了。
                    items(airports.take(shownCount), key = { "airport-${it.id}" }) { entry ->
                        AirportRecommendRow(
                            entry = entry,
                            inset = 16.dp,
                            onOpenUrl = {
                                if (entry.url.isNotBlank()) {
                                    platform.openUrl(entry.url)
                                } else {
                                    // 详情页是只读的，那里补不了官网；能补的是上面那张「分享一个机场」
                                    // 表单——后端按「同一身份 + 同一个名字」认成修改，不会多出一条。
                                    platform.showMessage(
                                        "这一条还没填官网；用同一个名字在「分享一个机场」里重发一次就能补上。",
                                    )
                                }
                            },
                        )
                    }

                    item(key = "board-foot") {
                        AirportFoot(
                            hasMore = hasMore,
                            shown = shownCount,
                            total = airports.size,
                            doneLabel = "想让哪家排前面，就去「机场投票」投一票。",
                            moreLabel = "已经拉出 $shownCount / ${airports.size} 家",
                            onLoadMore = { visiblePages++ },
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
                        // 刚分享完就想看自己那条：它排在列表最前面，所以顺手回到顶部，
                        // 不然人还停在中间，只看到列表抖了一下。
                        listState.scrollToItem(0)
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

/** 列表最上面那一行入口：加号 + 标题 + 一句说明（「分享一个机场」用的就是它）。 */
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
    voting: Boolean,
    canVote: Boolean,
    onVote: (Boolean) -> Unit,
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
                text = airportVoteStateOf(entry),
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.ballotSettled) scheme.primary else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        // 与应用投票同一套：这一轮还没结束就摆两个能按的票，结束了就只剩一个「投票结束」——
        // 票数够、认同率也够了还让人继续按，那只是把已经定下来的结果推来推去。
        if (entry.ballotSettled) {
            SettledVote()
        } else {
            CompactVote(
                label = "认同",
                count = entry.agrees,
                selected = entry.mine == true,
                color = AirportAgree,
                enabled = canVote && !voting,
                onClick = { onVote(true) },
            )
            Spacer(modifier = Modifier.width(4.dp))
            CompactVote(
                label = "不认同",
                count = entry.disagrees,
                selected = entry.mine == false,
                color = AirportDisagree,
                enabled = canVote && !voting,
                onClick = { onVote(false) },
            )
        }
    }
}

/**
 * 榜单那一行名字底下的一句话：离门槛还有多远，最少的字说完。
 *
 * 与应用投票的 [voteStateOf] 同一副口吻：差票就说差几票、顺手报认同率，够了就说等结算。
 */
internal fun airportVoteStateOf(entry: CommunityAirport): String {
    val share = entry.agreeShare
    val ballot = when {
        entry.ballotSettled -> "投票结束 · 认同率 ${((share ?: 0f) * 100).toInt()}%"
        entry.missingVotes > 0 ->
            "还差 ${entry.missingVotes} 票" + (share?.let { " · 认同 ${(it * 100).toInt()}%" } ?: "")
        (share ?: 0f) < AirportBallot.SHARE_NEEDED ->
            "票数够了 · 认同率 ${((share ?: 0f) * 100).toInt()}%，还差 " +
                "${(AirportBallot.SHARE_NEEDED * 100).toInt()}%"
        else -> "已达成条件，等待结算"
    }
    // 评分跟在后面：票说的是「有多少人认」，星说的是「用过的人打几分」，两个一起才是这家的样子。
    // 窄屏上这一行可能被截断——截在评分上比截在票数上可惜，所以把它放最后。
    return listOfNotNull(ballot, entry.starsText.ifEmpty { null }).joinToString(" · ")
}

/**
 * 列表最后一格：还有下一批就顺手再拉一批，拉完就摆一句说明。
 *
 * **触发点就是这一格被组合**——它只在划到底时才会出现，所以「划到底就把下一页接上」不需要去
 * 算 `layoutInfo` 的索引。以前那套算法（`lastVisibleItemIndex >= 总数 - 2`）在两个 tab 上
 * 各有各的前置项，少算一格就永远差一条，表现正是「明明写着再往下划就有，可划到底什么也没发生」。
 *
 * 也不怕"列表短到滚不动"：那时这一格本来就在屏幕上，直接就把下一批拉进来，直到填满屏幕；
 * 每拉一次 `shown` 就变，`LaunchedEffect` 重新跑，`hasMore` 一假就停。
 */
@Composable
private fun AirportFoot(
    hasMore: Boolean,
    shown: Int,
    total: Int,
    doneLabel: String,
    moreLabel: String,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(shown, hasMore) {
        if (hasMore) onLoadMore()
    }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)) {
        if (hasMore) {
            Text(
                text = "$moreLabel，继续往下划就再来一页。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 手点一下也管用：划不动、或者只差最后几条的时候，不必再去猜手势。
            TextButton(onClick = onLoadMore) {
                Text("再看一批", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Text(
                text = doneLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 推荐那一格的一行：只有名字、类型和一行小字，整行点开是它的网站。 */
@Composable
private fun AirportRecommendRow(
    entry: CommunityAirport,
    inset: Dp,
    onOpenUrl: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, top = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onOpenUrl)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                text = if (entry.votes == 0 && entry.ratingCount == 0) {
                    // metaLine 的头一项恒是类型标签，永远不会是空串，所以兜底只能自己判：
                    // 一票一星都没有时才知道"还没人评"。
                    "还没人评"
                } else {
                    entry.metaLine
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        // 一个「›」而不是「官网」两个字：这一行整条都是入口，再写一个按钮像是有两件事。
        Text(
            text = "›",
            fontSize = 18.sp,
            color = scheme.onSurfaceVariant,
        )
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
