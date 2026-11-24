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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import io.github.samolego.canta.data.community.CdkStore
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.recommend.CommunityAirport
import io.github.samolego.canta.data.recommend.PointOffer
import io.github.samolego.canta.data.recommend.PointOrder
import io.github.samolego.canta.data.recommend.PointRedeemResult
import io.github.samolego.canta.data.recommend.PointsAccount
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import kotlinx.coroutines.launch

/** 五颗星里的一颗与票的颜色，写在文件头上，两处图表共用同一套。 */
/**
 * 跨「切走再切回来」要留住的那一份。
 *
 * 这一块只在 `tab == TAB_AIRPORT` 时组合，切到应用目录再切回来整棵子树会被丢掉，于是列表、
 * 钱包、已取到的订阅和滚动位置全部归零，还要重跑五趟网络。放在这里由推荐页持有（它是常驻的），
 * 回来时先画上一份、数据还在就不重拉。
 */
internal class AirportTabState {
    var airports by mutableStateOf<List<CommunityAirport>>(emptyList())
    var account by mutableStateOf<PointsAccount?>(null)
    var balance by mutableStateOf(0L)
    var offers by mutableStateOf<List<PointOffer>>(emptyList())
    var orders by mutableStateOf<List<PointOrder>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var checkedIn by mutableStateOf(false)
    var subscriptions by mutableStateOf<Map<Long, String>>(emptyMap())
    /** 投一票、兑换一次、发一件之后 +1：只有它变了才重新拉。 */
    var reloadKey by mutableIntStateOf(0)
    /** 上一次拉完之后 [reloadKey] 的值：相等就说明这一屏已经是最新的，切回来不必再拉。 */
    var loadedKey by mutableIntStateOf(-1)
    /** 自动签到只做一次，切换 tab 不该每次都发一趟请求。 */
    var checkedOnce by mutableStateOf(false)
}

/** 认同这一条：与社区队列的绿色同一个。 */
private val AirportAgree = Color(0xFF2FA84F)

/** 不认同： quieter 的灰红。 */
private val AirportDisagree = Color(0xFFC0564C)

/** 星星的金色。 */
private val StarGold = Color(0xFFE8A33D)

/** 机场列表一行里图标方块的边长。 */
private val AirportTileSize = 34.dp

/** 机场类型：值 → 页面上的名字，顺序就是选择器的顺序。 */
private val AirportKinds = listOf(
    "airport" to "机场",
    "vpn" to "VPN",
    "self" to "自建",
)

/** 这一块的两页：0 看别人的机场（榜单），1 管自己的积分（兑换）。 */
private const val BOARD_TAB = 0

/** 商品类型：值 → 页面上的名字。 */
private val OfferKinds = listOf(
    "airport" to "订阅",
    "invite" to "邀请码",
    "account" to "账号",
    "other" to "其它",
)

/**
 * 推荐页的第三块：机场投票和积分。
 *
 * 三件事按顺序排下来，因为这个顺序就是这套东西怎么转起来的：
 *
 *  1. **钱包**——每天打开这一页领 10 分（后端按天幂等，刷新不会刷分）。
 *  2. **机场**——谁都能把自己在用的机场贴上来；别人认同/不认同决定它排多前，打星决定它多好用。
 *     订阅链接不在列表里：投过票或打过分才取得出来，这就是"投票"在这里的实际作用。
 *  3. **商城**——积分换东西。谁都能挂一件（订阅、邀请码、账号），兑换的人扣分，挂的人得分；
 *     交付内容只有买家看得到。
 *
 * @param repository 社区后端；null 时这一块只显示说明，不装作有数据。
 * @param platform 用来提示、复制、开链接。
 * @param author 署名，[authorId] 为空时当身份用。
 * @param authorId 身份码：投票、钱包、商品都挂在它上面。
 */
@Composable
internal fun RecommendationsAirportTab(
    repository: PlanRepository?,
    /** 兑换中心（码池）：发布一批码、花积分兑换、我的码。null 时这一块不画。 */
    cdk: CdkStore? = null,
    platform: CantaPlatform,
    author: String,
    authorId: String,
    state: AirportTabState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val who = remember(authorId, author) { authorId.ifEmpty { author } }
    val listState = rememberLazyListState()
    /** 这一块的两页：0 机场榜单 / 1 兑换中心。 */
    var boardTab by rememberSaveable { mutableIntStateOf(BOARD_TAB) }

    var airports by remember(state) { mutableStateOf(state.airports) }
    var account by remember(state) { mutableStateOf(state.account) }
    var balance by remember(state) { mutableStateOf(state.balance) }
    var offers by remember(state) { mutableStateOf(state.offers) }
    var orders by remember(state) { mutableStateOf(state.orders) }
    var loading by remember(state) { mutableStateOf(state.loading) }
    var error by remember(state) { mutableStateOf(state.error) }
    var reloadKey by remember(state) { mutableIntStateOf(state.reloadKey) }
    /** 正在进行的那一件事：`checkin` / `airport:7` / `offer:3`。机场和商品的 id 各从 1 起，不能共用一个数字。 */
    var busyKey by remember { mutableStateOf<String?>(null) }
    /** 已经取到的订阅链接，按条目 id 存：再点一次就不再跑一趟网络。 */
    var subscriptions by remember(state) { mutableStateOf(state.subscriptions) }
    var airportComposer by remember { mutableStateOf(false) }
    var airportBusy by remember { mutableStateOf(false) }
    var airportError by remember { mutableStateOf<String?>(null) }
    var offerComposer by remember { mutableStateOf(false) }
    var offerBusy by remember { mutableStateOf(false) }
    var offerError by remember { mutableStateOf<String?>(null) }
    var redeemed by remember { mutableStateOf<PointRedeemResult?>(null) }
    var checkedIn by remember(state) { mutableStateOf(state.checkedIn) }

    // 每天登录送 10 分：进这一页就领，后端按天幂等。领到了才多说一句，免得每次进来都弹。
    LaunchedEffect(repository, who) {
        val repo = repository ?: return@LaunchedEffect
        if (who.isEmpty() || state.checkedOnce) return@LaunchedEffect
        state.checkedOnce = true
        val result = repo.checkIn(who)
        if (result != null) {
            checkedIn = true
            if (result.awarded > 0) {
                platform.showMessage("每日登录 +${result.awarded} 积分")
                reloadKey++
            }
        }
    }

    // 每次拿到新的一屏就写回外层：切走再回来先画它，而不是从空列表开始转圈。
    LaunchedEffect(airports, account, balance, offers, orders, loading, error, checkedIn, subscriptions, reloadKey) {
        state.airports = airports
        state.account = account
        state.balance = balance
        state.offers = offers
        state.orders = orders
        state.loading = loading
        state.error = error
        state.checkedIn = checkedIn
        state.subscriptions = subscriptions
        state.reloadKey = reloadKey
    }

    LaunchedEffect(repository, who, reloadKey) {
        val repo = repository
        if (repo == null) {
            loading = false
            return@LaunchedEffect
        }
        // 切回这一块时 reloadKey 与上次拉完的值相同：这份数据就是刚拉过的，不必再跑五趟网络。
        if (state.loadedKey == reloadKey) return@LaunchedEffect
        if (airports.isEmpty() && offers.isEmpty()) loading = true
        val freshAirports = repo.airports(who)
        if (freshAirports != null) {
            airports = freshAirports
            error = null
        } else {
            error = repo.lastError ?: "机场列表没拉到，检查一下网络或代理。"
        }
        account = repo.pointsAccount(who) ?: account
        repo.pointShelf(who)?.let { shelf ->
            balance = shelf.balance
            offers = shelf.offers
        }
        orders = repo.myPointOrders(who)
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

    val checkIn: () -> Unit = {
        val repo = repository
        if (repo == null) {
            platform.showMessage("社区后端没连上")
        } else if (who.isEmpty()) {
            platform.showMessage("还没认出身份，签到记不上")
        } else if (busyKey == null) {
            busyKey = "checkin"
            scope.launch {
                val result = repo.checkIn(who)
                busyKey = null
                if (result == null) {
                    platform.showMessage("签到没成功：${repo.lastError ?: "检查一下网络"}")
                } else {
                    checkedIn = true
                    if (result.awarded > 0) platform.showMessage("签到 +${result.awarded} 积分")
                    reloadKey++
                }
            }
        }
    }

    val redeem: (PointOffer) -> Unit = buy@{ offer ->
        val repo = repository ?: run {
            platform.showMessage("社区后端没连上")
            return@buy
        }
        if (busyKey != null) return@buy
        busyKey = "offer:${offer.id}"
        scope.launch {
            val result = repo.redeemPointOffer(offer.id, who)
            busyKey = null
            if (result == null) {
                platform.showMessage("没换成：${repo.lastError ?: "检查一下网络再试"}")
            } else {
                if (result.ok) {
                    redeemed = result
                    reloadKey++
                } else {
                    platform.showMessage(result.error.ifBlank { "没换成" })
                }
            }
        }
    }

    when {
        loading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        else -> Column(modifier = modifier.fillMaxSize()) {
            // 这一块装着两件事：别人的机场（榜单），和积分怎么花（兑换）。
            // 按 cdk.linux.do 的样子分成两页——余额和兑换在一边、榜单在另一边；
            // 合成一条长列表会让人以为「商城」是机场榜单的一部分。
            FlSegmentedControl(
                segments = listOf(
                    FlSegment("board", "机场榜单"),
                    FlSegment("redeem", "兑换中心"),
                ),
                selectedIndex = boardTab,
                onSelect = { boardTab = it },
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                if (boardTab == BOARD_TAB) {
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

                    items(airports, key = { "airport-${it.id}" }) { entry ->
                        AirportCard(
                            entry = entry,
                            busy = busyKey == "airport:${entry.id}",
                            canAct = who.isNotEmpty(),
                            subscribeUrl = subscriptions[entry.id].orEmpty(),
                            inset = 16.dp,
                            onVote = { agree -> vote(entry, agree) },
                            onRate = { stars -> rate(entry, stars) },
                            onSubscribe = { subscription(entry) },
                            onOpenUrl = {
                                if (entry.url.isNotBlank()) platform.openUrl(entry.url)
                            },
                        )
                    }

                    item(key = "board-foot") {
                        Note(
                            text = "认同 / 不认同决定它排多前，五星决定它多好用；投过票或打过星，才取得到订阅。",
                            inset = 16.dp,
                        )
                    }
                } else {
                    item(key = "wallet") {
                        WalletCard(
                            account = account,
                            balance = balance,
                            checkedIn = checkedIn || account?.checkedInToday == true,
                            busy = busyKey == "checkin",
                            onCheckIn = checkIn,
                            inset = 16.dp,
                        )
                    }

                    item(key = "store-new") {
                        EntryRow(
                            title = "发布一件商品赚积分",
                            hint = "订阅、邀请码、账号都行；别人用积分换，分数直接到你账上。",
                            inset = 16.dp,
                            onClick = {
                                offerError = null
                                offerComposer = true
                            },
                        )
                    }

                    item(key = "store-head") {
                        AirportSectionHeader(label = "积分商城", count = offers.size)
                    }

                    items(offers, key = { "offer-${it.id}" }) { offer ->
                        OfferCard(
                            offer = offer,
                            balance = balance,
                            busy = busyKey == "offer:${offer.id}",
                            inset = 16.dp,
                            onRedeem = { redeem(offer) },
                        )
                    }

                    if (orders.isNotEmpty()) {
                        item(key = "orders-head") {
                            AirportSectionHeader(label = "我兑换的", count = orders.size)
                        }
                        items(orders, key = { "order-${it.id}" }) { order ->
                            OrderCard(
                                order = order,
                                inset = 16.dp,
                                onCopy = {
                                    platform.copyToClipboard(order.deliverable)
                                    platform.showMessage("已复制")
                                },
                            )
                        }
                    }

                    item(key = "cdk") {
                        // 兑换中心（码池）：卖家一次贴一批码，买家花积分取走一个。
                        CdkCenterSection(
                            store = cdk,
                            platform = platform,
                            author = author,
                            authorId = who,
                        )
                    }

                    item(key = "foot") {
                        Note(
                            text = "积分只在我们之间转：每天登录 +10，兑换扣买家的分、全额进卖家的账，平台不抽成。" +
                                "订阅链接由分享者自负，用之前确认来源。",
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
        )
    }

    if (offerComposer) {
        OfferComposerDialog(
            submitting = offerBusy,
            error = offerError,
            onDismiss = {
                offerComposer = false
                offerError = null
            },
            onSubmit = submit@{ title, kind, price, stock, detail, deliverable ->
                val repo = repository
                if (repo == null) {
                    offerError = "社区后端没连上，发布没发出去。"
                    return@submit
                }
                scope.launch {
                    offerBusy = true
                    offerError = null
                    val id = repo.publishPointOffer(
                        title = title,
                        detail = detail,
                        price = price,
                        stock = stock,
                        kind = kind,
                        deliverable = deliverable,
                        owner = author,
                        ownerId = who,
                    )
                    offerBusy = false
                    if (id != null) {
                        offerComposer = false
                        reloadKey++
                        platform.showMessage("已上架")
                    } else {
                        offerError = repo.lastError ?: "没发出去：检查一下网络再试"
                    }
                }
            },
        )
    }

    redeemed?.let { result ->
        RedeemDialog(
            result = result,
            onDismiss = { redeemed = null },
            onCopy = {
                platform.copyToClipboard(result.deliverable)
                platform.showMessage("已复制")
            },
        )
    }
}

/** 钱包卡：余额、今天领没领、连签几天，以及"这些分哪来的"。 */
@Composable
private fun WalletCard(
    account: PointsAccount?,
    balance: Long,
    checkedIn: Boolean,
    busy: Boolean,
    onCheckIn: () -> Unit,
    inset: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = balance.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "积分",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                text = buildString {
                    append(if (checkedIn) "今天已领 +${account?.todayPoints?.takeIf { it > 0 } ?: 10}"
                    else "今天还没领，点右边 +10")
                    val streak = account?.streak ?: 0
                    if (streak > 1) append(" · 连签 $streak 天")
                    val earned = account?.earned ?: 0
                    val spent = account?.spent ?: 0
                    if (earned > 0 || spent > 0) append(" · 收到 $earned / 花掉 $spent")
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onCheckIn, enabled = !busy && !checkedIn) {
            Text(if (checkedIn) "已领" else if (busy) "…" else "签到 +10")
        }
    }
}

/** 一行能点的入口：加号、标题、一句说明。 */
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

/** 一条机场：名字、类型、评分、票，以及一条能点的星和订阅。 */
@Composable
private fun AirportCard(
    entry: CommunityAirport,
    busy: Boolean,
    canAct: Boolean,
    subscribeUrl: String,
    inset: Dp,
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
                if (entry.summary.isNotBlank()) {
                    Text(
                        text = entry.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            VotePill(
                label = "认同",
                count = entry.agrees,
                selected = entry.mine == true,
                color = AirportAgree,
                enabled = canAct && !busy,
                onClick = { onVote(true) },
            )
            Spacer(modifier = Modifier.width(4.dp))
            VotePill(
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
private fun StarRow(myStars: Int, enabled: Boolean, onRate: (Int) -> Unit) {
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
private fun VotePill(
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
private fun OfferCard(
    offer: PointOffer,
    balance: Long,
    busy: Boolean,
    inset: Dp,
    onRedeem: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val short = balance < offer.price
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerLow)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = offer.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = offer.kindLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(scheme.secondaryContainer)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    if (offer.mine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "我挂的",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = buildString {
                        append("${offer.price} 积分")
                        append(" · 剩 ${offer.stock} 份")
                        if (offer.sold > 0) append(" · 已兑 ${offer.sold}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (short && !offer.mine) scheme.error else scheme.onSurfaceVariant,
                )
                if (offer.detail.isNotBlank()) {
                    Text(
                        text = offer.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (offer.mine) {
                Text(text = "你的", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            } else {
                Button(onClick = onRedeem, enabled = offer.buyable && !busy) {
                    Text(
                        when {
                            busy -> "…"
                            offer.soldOut -> "卖完"
                            else -> "兑换"
                        },
                    )
                }
            }
        }
    }
}

/** 我兑换过的一件：标题、价格和交付内容。 */
@Composable
private fun OrderCard(order: PointOrder, inset: Dp, onCopy: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerLow)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = order.title.ifBlank { "兑换记录 #${order.id}" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${order.price} 积分",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        if (order.deliverable.isNotBlank()) {
            Text(
                text = order.deliverable,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
        TextButton(onClick = onCopy, enabled = order.deliverable.isNotBlank()) {
            Text("复制内容", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** 分享机场的表单。 */
@Composable
private fun AirportComposerDialog(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var kindIndex by remember { mutableIntStateOf(0) }
    var url by remember { mutableStateOf("") }
    var subscribeUrl by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }

    val canSend = name.trim().isNotEmpty() && !submitting

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
@Composable
private fun OfferComposerDialog(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Int, Int, String, String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var title by remember { mutableStateOf("") }
    var kindIndex by remember { mutableIntStateOf(0) }
    var price by remember { mutableStateOf("20") }
    var stock by remember { mutableStateOf("1") }
    var detail by remember { mutableStateOf("") }
    var deliverable by remember { mutableStateOf("") }

    val priceValue = price.toIntOrNull()?.coerceIn(1, 5000)
    val stockValue = stock.toIntOrNull()?.coerceIn(0, 999)
    val canSend = title.trim().isNotEmpty() && priceValue != null && stockValue != null && !submitting

    CantaDialog(
        onDismissRequest = onDismiss,
        title = "发布一件商品",
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
            Button(
                enabled = canSend,
                onClick = {
                    onSubmit(
                        title.trim(),
                        OfferKinds[kindIndex].first,
                        priceValue ?: 10,
                        stockValue ?: 1,
                        detail.trim(),
                        deliverable.trim(),
                    )
                },
            ) { Text(if (submitting) "正在发布…" else "上架") }
        },
    ) {
        Text(
            text = "发布不花积分。别人兑换时，买家扣分、你得分，全额到账；交付内容只有买家看得到。",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlSegmentedControl(
            segments = OfferKinds.map { FlSegment(it.first, it.second) },
            selectedIndex = kindIndex,
            onSelect = { kindIndex = it },
            insetHorizontal = 0.dp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("标题") },
            placeholder = { Text("机场 A 一个月订阅") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = price,
                onValueChange = { price = it.filter { ch -> ch.isDigit() }.take(4) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("价格（积分）") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = stock,
                onValueChange = { stock = it.filter { ch -> ch.isDigit() }.take(3) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("库存") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = detail,
            onValueChange = { detail = it.take(400) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("说明（可留空）") },
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = deliverable,
            onValueChange = { deliverable = it.take(600) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("兑换后交付的内容") },
            placeholder = { Text("邀请码 / 订阅链接 / 配置") },
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = error, style = MaterialTheme.typography.labelSmall, color = scheme.error)
        }
    }
}

/** 兑换成功：把交付内容摆出来，能直接复制走。 */
@Composable
private fun RedeemDialog(
    result: PointRedeemResult,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "兑换成功",
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text("知道了") }
            if (result.deliverable.isNotBlank()) {
                Button(onClick = onCopy) { Text("复制内容") }
            }
        },
    ) {
        Text(
            text = "${result.title} · ${result.price} 积分 · 余额 ${result.balance}",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (result.deliverable.isNotBlank()) {
            Text(
                text = result.deliverable,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                    .padding(10.dp),
            )
        } else {
            Text(
                text = "卖家没写交付内容，去社区里找他吧。",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
