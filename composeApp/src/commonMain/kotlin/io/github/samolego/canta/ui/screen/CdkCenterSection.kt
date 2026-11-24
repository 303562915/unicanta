package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.community.CdkCode
import io.github.samolego.canta.data.community.CdkOffer
import io.github.samolego.canta.data.community.CdkRedeemResult
import io.github.samolego.canta.data.community.CdkStore
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import kotlinx.coroutines.launch

/** 这一块自己左右留 16dp：父级的 LazyColumn 不管别人的边距，子项各自留才对齐。 */
private val CdkInset = 16.dp

/** 商品类型：值 → 页面上的名字，顺序就是选择器的顺序（与后端 `point_offer_publish_codes` 的归一化一致）。 */
private val CdkKinds = listOf(
    "airport" to "订阅",
    "invite" to "邀请码",
    "account" to "账号",
    "other" to "其它",
)

/** 粘贴框最多收多少字符：200 条 × 600 字符的正文，多出来的直接截掉，免得一个手滑粘进一整本书。 */
private const val CDK_PASTE_LIMIT = 40_000

/**
 * CDK 兑换中心：把"一批码"当成商品卖的那一块。
 *
 * 它与积分商城的分工：商城那一条的交付内容是一段固定的话（网站链接、群号），一份卖一次；这里
 * 的交付内容是一串**互不相同**的码，卖一份就从池子里取走一个，取走的码只属于买家。所以这一块
 * 自己有一份"我的码"，商城那边只有订单。
 *
 * 四段排下来，顺序就是这件事怎么转起来的：
 *
 *  1. **余额**——签到 +10；兑换扣买家的分、全额进卖家账（平台不抽成）。
 *  2. **发布码**——标题、说明、价格、类型，再贴一大段码（一行一个）。
 *  3. **可兑换**——谁挂的、多少分、还剩几个；自己挂的显示「你的」，没码了显示「发完了」。
 *  4. **我的码**——已经换到手的码，点一下复制。
 *
 * 这一块**不带滚动容器**：它按设计是放进父级列表里的一个 item（`RecommendationsAirportTab` 的
 * LazyColumn 或任何 `Column(verticalScroll)`），自带 LazyColumn 会在别人的无限高约束下直接崩。
 *
 * @param store 数据层；null 时只显示一句说明，不装作有数据。
 * @param platform 用来提示和复制。
 * @param author 署名，[authorId] 为空时当身份用（与推荐页那一块同一套规则）。
 * @param authorId 身份码：积分、上架、兑换都挂在它上面。
 */
@Composable
internal fun CdkCenterSection(
    store: CdkStore?,
    platform: CantaPlatform,
    author: String,
    authorId: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val who = remember(authorId, author) { authorId.ifEmpty { author } }

    var balance by remember(store) { mutableStateOf<Long?>(null) }
    var offers by remember(store) { mutableStateOf<List<CdkOffer>>(emptyList()) }
    var codes by remember(store) { mutableStateOf<List<CdkCode>>(emptyList()) }
    var loading by remember(store) { mutableStateOf(true) }
    var error by remember(store) { mutableStateOf<String?>(null) }
    /** 换一次、贴一批之后 +1：只有它变了才重新拉。 */
    var reloadKey by remember(store) { mutableIntStateOf(0) }
    /** 正在进行的那一件事：`publish` / `redeem:<id>`。商品和各种 id 各从 1 起，不能共用一个数字。 */
    var busyKey by remember { mutableStateOf<String?>(null) }
    var composer by remember { mutableStateOf(false) }
    var publishError by remember { mutableStateOf<String?>(null) }
    /** 上架成功后留在标题下面的一句话：「已上架 N 个码」。 */
    var publishNote by remember { mutableStateOf<String?>(null) }
    var redeemed by remember { mutableStateOf<CdkRedeemResult?>(null) }

    LaunchedEffect(store, who, reloadKey) {
        val s = store
        if (s == null) {
            loading = false
            return@LaunchedEffect
        }
        if (offers.isEmpty()) loading = true
        s.balance()?.let { balance = it }
        val shelf = s.offers()
        if (shelf != null) {
            offers = shelf
            error = null
        } else {
            error = s.lastError ?: "货架没拉到，检查一下网络或代理。"
        }
        // 码只增不减（后端不会把取走的码收回去），所以这一趟没拉到东西时不要把已经画出来的擦掉。
        val mine = s.myCodes()
        if (mine.isNotEmpty()) codes = mine
        loading = false
    }

    val redeem: (CdkOffer) -> Unit = redeem@{ offer ->
        val s = store
        if (s == null) {
            platform.showMessage("社区后端没连上，兑换没发出去。")
            return@redeem
        }
        if (busyKey != null) return@redeem
        busyKey = "redeem:${offer.id}"
        scope.launch {
            val result = s.redeem(offer.id)
            busyKey = null
            when {
                result == null -> platform.showMessage("没换成：${s.lastError ?: "检查一下网络再试"}")
                !result.ok -> platform.showMessage(result.error.ifBlank { "没换成" })
                else -> {
                    redeemed = result
                    reloadKey++
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = CdkInset),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CdkBalanceBar(balance = balance ?: 0L, loading = loading && balance == null)

        CdkEntryRow(
            title = "贴一批码上架",
            hint = "一行一个码或订阅链接；别人用积分换，换走一个就少一个。",
            onClick = {
                publishError = null
                composer = true
            },
        )

        publishNote?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        val emptyNote = if (store == null) {
            "社区后端没连上，这一块只看得到说明。"
        } else {
            error ?: "还没有人挂码。你手里有闲着的订阅或邀请码，贴一批上来换点积分。"
        }

        CdkSectionHeader(label = "可兑换的码", count = offers.size)

        when {
            loading && offers.isEmpty() -> CdkLoadingRow()

            offers.isEmpty() -> CdkNote(text = emptyNote)

            else -> offers.forEach { offer ->
                CdkOfferCard(
                    offer = offer,
                    balance = balance ?: 0L,
                    busy = busyKey == "redeem:${offer.id}",
                    canAct = who.isNotEmpty() && busyKey == null,
                    onRedeem = { redeem(offer) },
                )
            }
        }

        CdkSectionHeader(label = "我的码", count = codes.size)

        if (codes.isEmpty()) {
            CdkNote(text = "还没换到过码。换到一个，它就会留在这里，随时能复制。")
        } else {
            codes.forEach { code ->
                CdkCodeRow(
                    code = code,
                    onCopy = {
                        platform.copyToClipboard(code.code)
                        platform.showMessage("码已复制")
                    },
                )
            }
        }
    }

    if (composer) {
        CdkPublishDialog(
            submitting = busyKey == "publish",
            error = publishError,
            onDismiss = {
                composer = false
                publishError = null
            },
            onSubmit = submit@{ title, detail, price, codesText, kind ->
                val s = store
                if (s == null) {
                    publishError = "社区后端没连上，提交没发出去。"
                    return@submit
                }
                if (busyKey != null) return@submit
                busyKey = "publish"
                publishError = null
                scope.launch {
                    val result = s.publish(
                        title = title,
                        detail = detail,
                        price = price,
                        codes = codesText,
                        kind = kind,
                        ownerName = author,
                    )
                    busyKey = null
                    if (result == null) {
                        publishError = s.lastError ?: "没发出去：检查一下网络再试"
                    } else {
                        composer = false
                        publishNote = "已上架 ${result.codes} 个码"
                        platform.showMessage("已上架 ${result.codes} 个码")
                        reloadKey++
                    }
                }
            },
        )
    }

    redeemed?.let { result ->
        CdkRedeemDialog(
            result = result,
            onDismiss = { redeemed = null },
            onCopy = {
                platform.copyToClipboard(result.code)
                platform.showMessage("码已复制")
            },
        )
    }
}

/** 余额条：数字、单位，和这一套积分怎么转的一句话。 */
@Composable
private fun CdkBalanceBar(balance: Long, loading: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                text = "签到 +10 / 兑换扣买家的分、全额进卖家账",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (loading) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

/** 一行能点的入口：加号、标题、一句说明（与机场那一块的入口同一副样子，组件是这里自己的）。 */
@Composable
private fun CdkEntryRow(title: String, hint: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
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

/** 一个区块的标题：名字 + 条数。 */
@Composable
private fun CdkSectionHeader(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
private fun CdkNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
}

/** 第一次拉货架时的一行转圈：不占满屏，父级列表还在它上下画别的东西。 */
@Composable
private fun CdkLoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "正在拉货架…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 货架上的一批码：标题、类型、价格、还剩几个、卖家，和兑换按钮。 */
@Composable
private fun CdkOfferCard(
    offer: CdkOffer,
    balance: Long,
    busy: Boolean,
    canAct: Boolean,
    onRedeem: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val short = balance < offer.price
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                }
                Text(
                    text = buildString {
                        append("${offer.price} 积分")
                        append(" · 剩 ${offer.stock} 个")
                        if (offer.sold > 0) append(" · 已兑 ${offer.sold}")
                        if (offer.seller.isNotBlank()) append(" · ${offer.seller}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (short && !offer.mine) scheme.error else scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
            when {
                offer.mine -> Text(
                    text = "你的",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )

                !offer.available -> Text(
                    text = "发完了",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )

                else -> Button(onClick = onRedeem, enabled = canAct && !busy) {
                    Text(if (busy) "…" else "兑换")
                }
            }
        }
    }
}

/** 我的一个码：标题 + 取走的日子，码文本本身点一下就复制。 */
@Composable
private fun CdkCodeRow(code: CdkCode, onCopy: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerLow)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = code.title.ifBlank { "兑换记录 #${code.id}" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (code.takenAt.isNotBlank()) {
                Text(
                    // 后端盖的是 UTC 的时间戳，只取日期：到秒的 UTC 在中文界面上会被读成本地时间。
                    text = code.takenAt.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = code.code,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                .clickable(role = Role.Button, onClick = onCopy)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** 贴一批码的表单：标题、说明、价格、类型，和一大段码。 */
@Composable
private fun CdkPublishDialog(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Int, String, String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("20") }
    var kindIndex by remember { mutableIntStateOf(0) }
    var codes by remember { mutableStateOf("") }

    val priceValue = price.toIntOrNull()?.coerceIn(1, 5000)
    val canSend = title.trim().isNotEmpty() && codes.isNotBlank() && priceValue != null && !submitting

    CantaDialog(
        onDismissRequest = onDismiss,
        title = "贴一批码上架",
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
            Button(
                enabled = canSend,
                onClick = {
                    onSubmit(
                        title.trim(),
                        detail.trim(),
                        priceValue ?: 20,
                        codes,
                        CdkKinds[kindIndex].first,
                    )
                },
            ) { Text(if (submitting) "正在上架…" else "上架") }
        },
    ) {
        Text(
            text = "一行一个码。上架不花积分；别人兑换时，买家扣分、你得分，全额到账，" +
                "他拿到的是池子里一个没人用过的码。",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlSegmentedControl(
            segments = CdkKinds.map { FlSegment(it.first, it.second) },
            selectedIndex = kindIndex,
            onSelect = { kindIndex = it },
            // 对话框自己已经有 22dp 的边距，选择器再缩 16 就会比下面的输入框窄一截。
            insetHorizontal = 0.dp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("标题") },
            placeholder = { Text("机场 A 一个月订阅 ×10") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = price,
                onValueChange = { price = it.filter { ch -> ch.isDigit() }.take(4) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("每个多少积分") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 份数不由人填：份数就是码的条数，填一个和粘贴内容对不上的数字只会让人以为挂错了。
            Text(
                text = "份数 =\n码的条数",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = detail,
            onValueChange = { detail = it.take(400) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("说明（可留空）") },
            placeholder = { Text("什么线路、有效期多久、怎么用") },
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = codes,
            onValueChange = { codes = it.take(CDK_PASTE_LIMIT) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            maxLines = 12,
            label = { Text("码（一行一个）") },
            placeholder = {
                Text("https://…/api/v1/client/subscribe?token=…\nABCD-1234-EFGH\n…")
            },
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = error, style = MaterialTheme.typography.labelSmall, color = scheme.error)
        }
    }
}

/** 兑换成功：把换到的码摆出来，能直接复制走。 */
@Composable
private fun CdkRedeemDialog(
    result: CdkRedeemResult,
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
            if (result.code.isNotBlank()) {
                Button(onClick = onCopy) { Text("复制码") }
            }
        },
    ) {
        Text(
            text = "${result.title} · ${result.price} 积分 · 余额 ${result.balance}",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (result.code.isNotBlank()) {
            Text(
                text = result.code,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                    .padding(10.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "这个码只发给了你，卖家和你各有一份记录；它不会再发给第二个人。",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
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
