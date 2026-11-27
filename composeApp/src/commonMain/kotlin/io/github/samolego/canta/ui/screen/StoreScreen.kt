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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.cache.ScreenCache
import io.github.samolego.canta.data.cache.stableMerge
import io.github.samolego.canta.data.community.CdkStore
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.recommend.PointOffer
import io.github.samolego.canta.data.recommend.PointOrder
import io.github.samolego.canta.data.recommend.PointRedeemResult
import io.github.samolego.canta.data.recommend.PointsAccount
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ScrollToTopEffect
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.IdentitySetup
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch

/** 商品类型：值 → 页面上的名字。 */
private val OfferKinds = listOf(
    "airport" to "订阅",
    "invite" to "邀请码",
    "account" to "账号",
    "other" to "其它",
)

/**
 * 商城一屏的快照：余额、货架、我兑换过的。
 *
 * 按身份分槽（见 [storeCacheKey]）：余额和订单都是某一个人的，串了槽就是拿别人的钱画给这个人。
 */
@Serializable
private data class StoreSnapshot(
    val balance: Long = 0L,
    val offers: List<PointOffer> = emptyList(),
    val orders: List<PointOrder> = emptyList(),
)

/** 商城这一屏在 [ScreenCache] 里的键：每人一格。 */
private fun storeCacheKey(who: String): String = "screen:store:$who"

/**
 * 商城：积分怎么来、怎么花。
 *
 * 它以前挤在机场页里，和"机场榜单"共用一条长列表，结果两个系统互相看着像同一件事。现在
 * 拆成自己的一页，跟 cdk.linux.do 那个站一个思路：上面是余额，下面是能换的东西，兑换完的码
 * 留在自己手里。**机场页只剩投票榜单**。
 *
 * 一页里三块，顺序就是钱的流向：
 *
 *  1. **钱包**——每天进来领 10 分（后端按天幂等，刷新不会刷分）。
 *  2. **积分商城**——谁都能挂一件（订阅、邀请码、账号），兑换的人扣分、挂的人全额得分。
 *  3. **兑换中心（CDK 码池）**——一次贴一批码，兑换时从池子里取一个没用过的；取到的码在
 *     「我的码」里随时回看、复制。
 *
 * @param repository 社区后端，商城的商品与订单都走它；null 时页面只说明、不装作有数据。
 * @param cdk 码池那一套（发布一批码 / 兑换 / 我的码）；null 时那一块不画。
 * @param author 署名，[authorId] 为空时当身份用。
 */
@Composable
internal fun StoreScreen(
    platform: CantaPlatform,
    repository: PlanRepository?,
    cdk: CdkStore?,
    author: String,
    authorId: String,
    modifier: Modifier = Modifier,
    /** 名字与头像设过了没有：没设过能买能兑换，但上不了架（见 [IdentitySetup]）。 */
    setupGate: IdentitySetup = IdentitySetup.UNSET,
    /**
     * 顶栏的返回箭头。
     *
     * 商城不再是底栏的一格（见设置页「商城」那一行），所以它现在是一个**二级页**：顶栏要有回退，
     * 否则进来之后只能靠系统返回键出去。
     */
    onNavigateBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val who = remember(authorId, author) { authorId.ifEmpty { author } }
    val listState = rememberLazyListState()
    // 双击底栏「商城」那一格：这一页回顶部。
    ScrollToTopEffect(listState)

    var account by remember { mutableStateOf<PointsAccount?>(null) }
    // 上一屏画出来的那一份（见 [ScreenCache]）：**在 state 初始化时就摆上**，和方案详情、消息页一样。
    // 以前是"先 `loading = true`，再用 `LaunchedEffect(who)` 补缓存"，那一帧的转圈是躲不掉的：
    // 缓存明明在内存里，第一帧还是画了 `CircularProgressIndicator`，身份晚到一点就多转几百毫秒。
    val cached = remember(who) {
        if (who.isEmpty()) null else ScreenCache.get<StoreSnapshot>(storeCacheKey(who))
    }
    var balance by remember(who) { mutableStateOf(cached?.balance ?: 0L) }
    var offers by remember(who) { mutableStateOf(cached?.offers.orEmpty()) }
    var orders by remember(who) { mutableStateOf(cached?.orders.orEmpty()) }
    // 有缓存就不挂 loading：那一屏刚才还在，再转一圈只会让人以为页面没进来。
    var loading by remember(who) { mutableStateOf(cached == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    /** 正在进行的那一件事：`checkin` / `offer:3`。 */
    var busyKey by remember { mutableStateOf<String?>(null) }
    var offerComposer by remember { mutableStateOf(false) }
    var offerBusy by remember { mutableStateOf(false) }
    var offerError by remember { mutableStateOf<String?>(null) }
    var redeemed by remember { mutableStateOf<PointRedeemResult?>(null) }
    var checkedIn by remember { mutableStateOf(false) }

    // 每天进来领 10 分：后端按天幂等，重复进来只回答 already。
    LaunchedEffect(repository, who) {
        val repo = repository ?: return@LaunchedEffect
        if (who.isEmpty()) return@LaunchedEffect
        val result = repo.checkIn(who)
        if (result != null) {
            checkedIn = true
            if (result.awarded > 0) {
                platform.showMessage("每日登录 +${result.awarded} 积分")
                reloadKey++
            }
        }
    }

    LaunchedEffect(repository, who, reloadKey) {
        val repo = repository
        if (repo == null) {
            loading = false
            return@LaunchedEffect
        }
        // 身份还没认出来就先去请求，只会拿一个空 `who` 换回一份空货架，白跑一趟。
        if (who.isEmpty()) return@LaunchedEffect
        if (offers.isEmpty()) loading = true
        account = repo.pointsAccount(who) ?: account
        repo.pointShelf(who)?.let { shelf ->
            balance = shelf.balance
            // 就地合并：价格、库存没变的商品沿用旧实例，货架不整块重画。
            val merged = stableMerge(offers, shelf.offers) { it.id }
            if (merged !== offers) offers = merged
            error = null
        }
        // 订单那趟挂了给的是空列表：照着画就等于因为一次断网把「我兑换的」清空，
        // 缓存里那份也白留了，所以只在后端真答了的时候替换。
        if (repository.lastError == null) orders = repo.myPointOrders(who)
        loading = false
        // 存回本地：下次进来先画这一份，再等网络顶上去。
        // 空货架不写：一次失败/空响应不该把已经看到的商品从本地抹掉。
        if (who.isNotEmpty() && offers.isNotEmpty()) {
            ScreenCache.put(storeCacheKey(who), StoreSnapshot(balance, offers, orders))
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
            } else if (result.ok) {
                redeemed = result
                reloadKey++
            } else {
                platform.showMessage(result.error.ifBlank { "没换成" })
            }
        }
    }

    Scaffold(
        topBar = {
            // 底栏一级页面：不给返回箭头（与其它一级页一致）。
            ScreenTopBar(title = "商城", onNavigateBack = onNavigateBack)
        },
    ) { padding ->
        // 不再整页画转圈：点进商城那一刻就该看见这一页（顶栏、钱包、货架标题都在），
        // 数据各块自己在位子上填。以前这里 `if (loading) { Box { CircularProgressIndicator() }; return }`
        // 会让"点进来"先空白转一圈、数据回来才出现，读起来像页面加载了两遍。
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
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
                StoreSectionHeader(label = "积分商城", count = offers.size)
            }

            if (offers.isEmpty()) {
                item(key = "store-empty") {
                    Note(
                        // 第一次进来（没有缓存）货架还在路上：写一句话，而不是让整页空着或转圈。
                        //
                        // `error` 是 `remember { mutableStateOf<String?>(null) }` 的委托属性，
                        // 编译器不对它做智能转换 —— 写成 `error != null -> error` 那一支仍然是
                        // `String?`，`Note(text: String)` 收不了（`ba438bd` 就是这么红的）。
                        // 所以这里用 `?:` 给默认句，而不是靠 when 的空值分支去收窄。
                        text = when {
                            loading -> "正在读取货架…"
                            else -> error ?: "还没有人挂东西。你手上多出来的码，可以放上来换积分。"
                        },
                        inset = 16.dp,
                    )
                }
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
                    StoreSectionHeader(label = "我兑换的", count = orders.size)
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
                    setupGate = setupGate,
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
                if (!setupGate.ready) {
                    offerError = setupGate.refusal
                    return@submit
                }
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
            setupGate = setupGate,
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
                    append(
                        if (checkedIn) "今天已领 +${account?.reward ?: 10}"
                        else "今天还没领，点右边 +10",
                    )
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
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.primary)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(text = "+", color = scheme.onPrimary, fontWeight = FontWeight.Bold)
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

/** 一个区块的标题。 */
@Composable
private fun StoreSectionHeader(label: String, count: Int) {
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

/** 发布商品的表单。 */
@Composable
private fun OfferComposerDialog(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Int, Int, String, String) -> Unit,
    /** 名字与头像设过了没有：没设过上架键灰掉并说一句为什么（见 [IdentitySetup]）。 */
    setupGate: IdentitySetup = IdentitySetup.UNSET,
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
    val canSend = title.trim().isNotEmpty() && priceValue != null && stockValue != null &&
        !submitting && setupGate.ready

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
        if (!setupGate.ready) {
            Text(
                text = setupGate.prompt,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.error,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
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
