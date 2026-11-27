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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.community.SponsorEntry
import io.github.samolego.canta.data.community.SponsorStore
import io.github.samolego.canta.data.community.SponsorWall
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.PersonAvatar
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import kotlinx.coroutines.launch

/** 这一块自己左右留 16dp：父级是 LazyColumn，子项各自留边才对齐（与 CDK 那一块同一套）。 */
private val SponsorInset = 16.dp

/** 人头像的直径：够看清脸，又不至于把一行撑成两行。 */
private val SponsorAvatar = 34.dp

/** 一次展开几个人：人少的时候一屏看完，人多了先给这几个，剩下的点「展开」。 */
private const val SPONSOR_COLLAPSED = 8

/** 赞助形式：值 → 页面上的名字，顺序就是选择器的顺序（与后端 `sponsor_add` 的归一化一致）。 */
private val SponsorKinds = listOf(
    "money" to "打赏金额",
    "ai" to "AI 邀请",
)

/**
 * 赞助榜（贡献值那一栏里的一块）。
 *
 * 它只回答一件事：**谁赞助了这个软件，多少**。所有赞助都进同一本账，写清用在哪儿——服务器建设
 * 和 token 采购，为的是让开发能一直转下去。所以这一块的顶部是总额 + 人数，下面是榜本身。
 *
 * 榜上每一行的人头像是真的头像：[SponsorEntry.owner] 是身份号，图床对象名固定是
 * `unicanta/头像/<身份号>.jpg`（见 `PersonAvatar`），所以设过头像的人显示照片、没设过的是名字
 * 首字。点这一行进那个人的主页（[onOpenProfile]），和互动页、群成员网格里点头像走同一条路由。
 *
 * **人少的时候全画出来，人多了先画 [SPONSOR_COLLAPSED] 个**，剩下的收在一行「展开全部 N 位」
 * 下面：榜刚上线时不该是一屏折叠，人多了也不该把下面的商城挤到看不见。
 *
 * 这一块**不带滚动容器**：它是父级 LazyColumn 的一个 item（见 [CdkCenterSection] 的同一条理由）。
 *
 * @param store 数据层；null 时只说一句为什么看不见，不装作有数据。
 * @param onOpenProfile 点榜上那一行：进那个人的主页（身份号）。
 */
@Composable
internal fun SponsorWallSection(
    store: SponsorStore?,
    platform: CantaPlatform,
    /** 提交这一刻的名字（榜上显示它；匿名的人不显示）。身份由 token 认，不用自报号码。 */
    author: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var wall by remember(store) { mutableStateOf<SponsorWall?>(null) }
    var loading by remember(store) { mutableStateOf(true) }
    var error by remember(store) { mutableStateOf<String?>(null) }
    /** 记一笔之后 +1：只有它变了才重新拉榜。 */
    var reloadKey by remember(store) { mutableIntStateOf(0) }
    /** 点「我也赞助」打开的那张表单。 */
    var composer by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    /** 榜展开到全部了没有。 */
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(store, reloadKey) {
        val s = store
        if (s == null) {
            loading = false
            return@LaunchedEffect
        }
        // 重进/重拉时把转圈补回来：以前只有第一次是 true，`reloadKey++` 之后那一趟全程没有转圈。
        if (wall == null) loading = true
        val fresh = s.wall()
        if (fresh != null) {
            wall = fresh
            error = null
        } else {
            error = if (s.functionMissing) {
                "这一版的社区后端还没有赞助榜，等一次服务端更新。"
            } else {
                s.lastError ?: "榜没拉到，检查一下网络或代理。"
            }
        }
        loading = false
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = SponsorInset),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SponsorTotalCard(
            wall = wall,
            loading = loading && wall == null,
            onSponsor = {
                submitError = null
                composer = true
            },
        )

        SponsorHeader(label = "赞助者", count = wall?.entries?.size ?: 0)

        val shown = wall?.entries.orEmpty()
        when {
            loading && shown.isEmpty() -> SponsorLoadingRow()

            shown.isEmpty() -> SponsorNote(
                text = error ?: "还没有赞助记录。第一个赞助的人，名字会出现在这里。",
            )

            else -> {
                val visible = if (expanded) shown else shown.take(SPONSOR_COLLAPSED)
                visible.forEach { entry ->
                    SponsorRow(
                        entry = entry,
                        onOpenProfile = {
                            if (entry.canOpenProfile) onOpenProfile(entry.owner)
                        },
                    )
                }
                if (shown.size > visible.size) {
                    SponsorExpandRow(
                        rest = shown.size - visible.size,
                        onClick = { expanded = true },
                    )
                }
            }
        }

        SponsorNote(
            text = "所有赞助都用在服务器建设和 token 采购上，支撑软件一直开发下去。" +
                "赞助形式有两种：打赏金额，或者邀请一个人来用（AI 邀请）。",
        )
    }

    if (composer) {
        SponsorComposerDialog(
            submitting = submitting,
            error = submitError,
            defaultName = author,
            onDismiss = {
                composer = false
                submitError = null
            },
            onSubmit = submit@{ amount, kind, note, anonymous ->
                val s = store
                if (s == null) {
                    submitError = "社区后端没连上，这一笔没记上。"
                    return@submit
                }
                // 先立牌子再 launch：`submitting = true` 写在协程里时，launch 真正开始之前还有一帧
                // 按钮是亮的，连点两下会发两次（后端 24h 幂等兜住第二笔，但会弹两次 toast）。
                submitting = true
                submitError = null
                scope.launch {
                    val result = s.add(
                        amountYuan = if (kind == "money") amount else 0,
                        kind = kind,
                        name = author,
                        note = note,
                        anonymous = anonymous,
                    )
                    submitting = false
                    when {
                        result == null -> {
                            submitError = s.lastError ?: "没记上：检查一下网络再试"
                        }
                        result.duplicate -> {
                            composer = false
                            platform.showMessage("这一笔已经在榜上了")
                        }
                        else -> {
                            composer = false
                            reloadKey++
                            platform.showMessage(
                                if (kind == "money") "谢谢！已记上 ¥$amount" else "谢谢！已记上 AI 邀请",
                            )
                        }
                    }
                }
            },
        )
    }
}

/**
 * 顶部那张卡：一共收了多少、多少人，右边一个「我也赞助」。
 *
 * 总额只算打赏金额（`kind = money`）——AI 邀请不涉及钱，混进去会让"收了多少"变成一句假话。
 */
@Composable
private fun SponsorTotalCard(
    wall: SponsorWall?,
    loading: Boolean,
    onSponsor: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (loading) "—" else "¥${wall?.totalYuan ?: 0L}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "已收到",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                text = buildString {
                    append("${wall?.count ?: 0} 位赞助者")
                    val money = wall?.moneyCount ?: 0
                    val ai = wall?.aiCount ?: 0
                    if (money > 0 || ai > 0) append(" · 打赏 $money / AI 邀请 $ai")
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onSponsor) {
            Text("我也赞助")
        }
    }
}

/** 榜上的一位：头像、名字、形式与金额。整行可点（进那个人的主页）。 */
@Composable
private fun SponsorRow(entry: SponsorEntry, onOpenProfile: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shownName = if (entry.anonymous || entry.name.isBlank()) {
        if (entry.anonymous) "匿名" else "#${entry.owner}"
    } else {
        entry.name
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = entry.canOpenProfile,
                role = Role.Button,
                onClick = onOpenProfile,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 匿名的头像不给照片：名字都藏了，脸还挂着等于没藏。
        if (entry.anonymous) {
            Box(
                modifier = Modifier
                    .size(SponsorAvatar)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(FlSurfaceLevel.Block.colorOf(scheme)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "匿",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
        } else {
            PersonAvatar(
                name = shownName,
                owner = entry.owner,
                size = SponsorAvatar,
                // 首字圆沿用主色底：图没到的那一帧不能是一块灰。
                background = scheme.primary.copy(alpha = 0.9f),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = shownName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.kindLabel,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            // 出钱的写金额，AI 邀请写「邀请」：两者不是一回事，用一个数糊住会让人以为是 ¥0。
            text = entry.badge,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (entry.amountYuan > 0) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 人多了才出现的一行：还有几位没画出来，点一下全部展开。 */
@Composable
private fun SponsorExpandRow(rest: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "展开全部（还有 $rest 位）",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 一个区块的标题：名字 + 人数。 */
@Composable
private fun SponsorHeader(label: String, count: Int) {
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
private fun SponsorNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 还没拉到榜时的一行转圈：只占一行，不整块空着。 */
@Composable
private fun SponsorLoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "正在读取赞助榜…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 「我也赞助」那张表单：形式（打赏金额 / AI 邀请）、金额、一句话说明、匿名开关。
 *
 * 这里不接支付：钱是在群里/私聊里收的，这张表只是把它记进榜。所以按钮写「记上」而不是「支付」，
 * 免得人以为点一下就付了钱。
 */
@Composable
private fun SponsorComposerDialog(
    submitting: Boolean,
    error: String?,
    defaultName: String,
    onDismiss: () -> Unit,
    onSubmit: (amount: Int, kind: String, note: String, anonymous: Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var kindIndex by remember { mutableStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var anonymous by remember { mutableStateOf(false) }

    val measured = amount.toIntOrNull() ?: 0
    val ready = if (SponsorKinds[kindIndex].first == "money") measured > 0 else true

    CantaDialog(
        onDismissRequest = onDismiss,
        title = "我也赞助",
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
            Button(
                enabled = ready && !submitting,
                onClick = {
                    onSubmit(measured, SponsorKinds[kindIndex].first, note.trim(), anonymous)
                },
            ) { Text(if (submitting) "正在记…" else "记上") }
        },
    ) {
        error?.let { line ->
            Text(text = line, style = MaterialTheme.typography.labelSmall, color = scheme.error)
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            text = "所有赞助都用在服务器建设和 token 采购上，支撑软件一直开发下去。" +
                "记上之后名字会出现在榜上；不想露名字就打开匿名。",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        FlSegmentedControl(
            segments = SponsorKinds.map { FlSegment(it.first, it.second) },
            selectedIndex = kindIndex,
            onSelect = { kindIndex = it },
            insetHorizontal = 0.dp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (SponsorKinds[kindIndex].first == "money") {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() }.take(7) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("金额（元）") },
                placeholder = { Text("20") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
        } else {
            Text(
                text = "AI 邀请：邀请一个人来用这个软件，不涉及钱，所以榜上记一笔「邀请」。",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(200) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("一句话（可不填）") },
            placeholder = { Text("这笔钱想用在哪儿") },
            minLines = 2,
            maxLines = 3,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "匿名赞助",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
                Text(
                    text = if (defaultName.isBlank()) "榜上显示「匿名」" else "榜上显示「匿名」，不显示 $defaultName",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = anonymous, onCheckedChange = { anonymous = it })
        }
    }
}