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
import io.github.samolego.canta.data.recommend.AirportDetail
import io.github.samolego.canta.data.recommend.CommunityAirport
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.IdentitySetup
import kotlinx.coroutines.launch

/**
 * 跨「切走再切回来」要留住的那一份。
 *
 * 这一块只在 `tab == TAB_AIRPORT` 时组合，切到应用目录再切回来整棵子树会被丢掉，于是列表、
 * 钱包、已取到的订阅和滚动位置全部归零，还要重跑五趟网络。放在这里由推荐页持有（它是常驻的），
 * 回来时先画上一份、数据还在就不重拉。
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
) {
    val scope = rememberCoroutineScope()
    val who = remember(authorId, author) { authorId.ifEmpty { author } }
    val listState = rememberRememberedListState("airport")

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
    /** 摊开看的那一条：点名字或「详情」打开，点同一个再关。 */
    var openedId by remember { mutableStateOf<Long?>(null) }
    /** 摊开时再点一次「详情」才展开完整介绍，列表里那两行字已经够长了。 */
    var detailsExpanded by remember { mutableStateOf(false) }

    // 每次拿到新的一屏就写回外层：切走再回来先画它，而不是从空列表开始转圈。
    LaunchedEffect(airports, loading, error, subscriptions, reloadKey) {
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
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
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
                        val opened = openedId == entry.id
                        AirportCard(
                            entry = entry,
                            opened = opened,
                            busy = busyKey == "airport:${entry.id}",
                            canAct = who.isNotEmpty(),
                            subscribeUrl = subscriptions[entry.id].orEmpty(),
                            inset = 16.dp,
                            onToggleOpen = {
                                detailsExpanded = false
                                openedId = if (opened) null else entry.id
                            },
                            onToggleNote = { detailsExpanded = !detailsExpanded },
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

/** 一条机场：名字、类型、评分、票，以及一条能点的星和订阅。 */
@Composable
private fun AirportCard(
    entry: CommunityAirport,
    /** 摊开了没有：摊开时多画价格、流量、速度、设备数、协议和完整介绍。 */
    opened: Boolean,
    busy: Boolean,
    canAct: Boolean,
    subscribeUrl: String,
    inset: Dp,
    onToggleOpen: () -> Unit,
    onToggleNote: () -> Unit,
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
                            .clickable(role = Role.Button, onClick = onToggleOpen),
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
                        // 摊开之后不再截断：这时候人点开就是为了看完整的那段话。
                        maxLines = if (opened) Int.MAX_VALUE else 2,
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
            } else {
                Text(
                    text = if (opened) "收起" else "详情",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(scheme.primary.copy(alpha = 0.10f))
                        .clickable(role = Role.Button, onClick = onToggleOpen)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
        }

        // 摊开的那几格：价格、流量、速度、设备数、协议。全空的就不画，免得一片"未填"。
        if (opened && !entry.detail.isEmpty) {
            AirportDetailBlock(
                detail = entry.detail,
                expanded = detailsExpanded,
                onToggleNote = onToggleNote,
            )
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

/**
 * 摊开之后那几格：一行两列，右边是原文不是数字——"1.5"离开"¥ / 月"就没有意义。
 *
 * 有完整介绍时再给一行「展开介绍 / 收起介绍」：第一屏只放一行，点开才把那段话整个摆出来。
 */
@Composable
private fun AirportDetailBlock(
    detail: AirportDetail,
    expanded: Boolean,
    onToggleNote: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(
            "价格" to detail.price,
            "流量" to detail.traffic,
            "速度" to detail.speed,
            "设备数" to detail.devices,
            "协议" to detail.protocols,
        ).filter { it.second.isNotBlank() }.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.width(52.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (detail.note.isNotBlank()) {
            Text(
                text = detail.note,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = if (expanded) "收起介绍" else "展开介绍",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(role = Role.Button, onClick = onToggleNote)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
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
