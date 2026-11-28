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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.recommend.CrowdAirport
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.IdentitySetup
import kotlinx.coroutines.launch

/**
 * 机场页的第三格「众筹机场」：一群人合买同一家的订阅，凑够人数就开团。
 *
 * 与另外两格（机场推荐 / 机场投票）分开一条数据线，理由写在 [CrowdAirport] 上：投票决定
 * 「这家的东西好不好」，众筹决定「这单谁一起出钱」，两套门槛混在一张表里改一边就会牵动另一边。
 *
 * 后端还没推众筹那张表时，`crowdAirports` 返回 null：这一格说「没拉到、可能还没开」，
 * 而不是画成一条都没有——后者会让人以为按钮没生效。
 *
 * @param repository 社区后端；null 时只显示说明。
 * @param platform 用来提示、开链接。
 * @param author / [authorId] 身份：发起的众筹挂在它上面。
 * @param setupGate 名字与头像设过了没有：没设过能认筹，但发起不了新的（见 [IdentitySetup]）。
 */
@Composable
internal fun AirportCrowdTab(
    repository: PlanRepository?,
    platform: CantaPlatform,
    author: String,
    authorId: String,
    modifier: Modifier = Modifier,
    setupGate: IdentitySetup = IdentitySetup.UNSET,
) {
    val scope = rememberCoroutineScope()
    val who = remember(authorId, author) { authorId.ifEmpty { author } }

    var entries by remember(repository, who) { mutableStateOf<List<CrowdAirport>>(emptyList()) }
    var loading by remember(repository, who) { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    /** 发起、认筹之后 +1：只有它变了才重新拉一遍。 */
    var reloadKey by remember { mutableIntStateOf(0) }
    /** 正在认筹的那一条：同一个 id 只发一趟，别的行照常可点。 */
    var pledgingId by remember { mutableStateOf(0L) }
    var composerOpen by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var composerError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository, who, reloadKey) {
        val repo = repository
        if (repo == null) {
            loading = false
            error = "社区后端没连上。"
            return@LaunchedEffect
        }
        if (entries.isEmpty()) loading = true
        val fresh = repo.crowdAirports(who)
        if (fresh != null) {
            entries = fresh
            error = null
        } else {
            error = repo.lastError ?: "众筹列表没拉到，检查一下网络或代理。"
        }
        loading = false
    }

    val pledge: (CrowdAirport) -> Unit = pledge@{ entry ->
        val repo = repository
        if (repo == null) {
            platform.showMessage("社区后端没连上，认筹没发出去。")
            return@pledge
        }
        if (pledgingId != 0L || !entry.joinable || entry.pledged) return@pledge
        pledgingId = entry.id
        scope.launch {
            val fresh = repo.pledgeCrowdAirport(entry.id, who)
            pledgingId = 0L
            if (fresh == null) {
                platform.showMessage("没认上：${repo.lastError ?: "检查一下网络再试"}")
            } else {
                // 就地换掉这一行的三个数，并让列表下一趟重拉（别的设备也会有人跟进，人数不是死的）。
                entries = entries.map { row ->
                    if (row.id == entry.id) {
                        row.copy(
                            joined = fresh.joined,
                            target = fresh.target,
                            status = fresh.status,
                            pledged = fresh.pledged,
                        )
                    } else {
                        row
                    }
                }
                platform.showMessage(
                    if (fresh.funded) "「${entry.name}」人数够了，等发起人开团" else "已认筹「${entry.name}」",
                )
                reloadKey++
            }
        }
    }

    when {
        loading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "crowd-new") {
                CrowdEntryRow(
                    onClick = {
                        composerError = null
                        composerOpen = true
                    },
                )
            }

            item(key = "crowd-head") {
                CrowdSectionHeader(label = "众筹合买", count = entries.size)
            }

            if (entries.isEmpty()) {
                item(key = "crowd-empty") {
                    CrowdNote(
                        text = error
                            ?: "还没有人发起众筹。想合买哪家，点上面那一行贴出来；人数凑够了，发起人再统一买。",
                    )
                }
            }

            items(entries, key = { "crowd-${it.id}" }) { entry ->
                CrowdRow(
                    entry = entry,
                    pledging = pledgingId == entry.id,
                    onClick = { if (entry.url.isNotBlank()) platform.openUrl(entry.url) },
                    onPledge = { pledge(entry) },
                )
            }

            item(key = "crowd-foot") {
                CrowdNote(
                    text = "众筹只是把愿意合买的人聚在一起，钱不走这里：人数凑够后由发起人联系大家，" +
                        "订阅怎么分、用多久，写在每一条的说明里。",
                )
            }
        }
    }

    if (composerOpen) {
        CrowdComposerDialog(
            submitting = submitting,
            error = composerError,
            onDismiss = {
                composerOpen = false
                composerError = null
            },
            onSubmit = submit@{ name, url, share, target, summary ->
                val repo = repository
                if (!setupGate.ready) {
                    composerError = setupGate.refusal
                    return@submit
                }
                if (repo == null) {
                    composerError = "社区后端没连上，提交没发出去。"
                    return@submit
                }
                scope.launch {
                    submitting = true
                    composerError = null
                    val id = repo.addCrowdAirport(
                        name = name,
                        url = url,
                        share = share,
                        summary = summary,
                        target = target,
                        author = author,
                        authorId = who,
                    )
                    submitting = false
                    if (id != null) {
                        composerOpen = false
                        reloadKey++
                        platform.showMessage("已发起，等大家来认筹")
                    } else {
                        composerError = repo.lastError ?: "没发出去：检查一下网络再试"
                    }
                }
            },
        )
    }
}

/** 列表最上面那一行入口：加号 + 标题 + 一句说明（与机场那一格的入口同一副样子）。 */
@Composable
private fun CrowdEntryRow(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
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
                text = "发起一个众筹",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = "贴名字、官网、每人分摊和目标人数；人凑够了再统一买。",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 区块标题，与机场那两格的 SectionHeader 同一副样子。 */
@Composable
private fun CrowdSectionHeader(label: String, count: Int) {
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

/** 一句解释 / 空状态 / 页脚。 */
@Composable
private fun CrowdNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

/**
 * 众筹列表的一行：图标、名字、分摊与人数组，以及一个认筹键。
 *
 * 整行点开官网（有链接的时候），「认筹」是行里唯一一个按钮——与机场推荐那一格同一个理由：
 * 一条能点的行里有几个按钮，点哪儿算点哪儿就说不清了。
 */
@Composable
private fun CrowdRow(
    entry: CrowdAirport,
    pledging: Boolean,
    onClick: () -> Unit,
    onPledge: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val funded = entry.status.equals("funded", ignoreCase = true)
    val closed = entry.status.equals("closed", ignoreCase = true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
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
                    text = entry.name.ifBlank { "未命名众筹" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = entry.statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (funded) scheme.onPrimaryContainer else scheme.onSecondaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (funded) scheme.primaryContainer else scheme.secondaryContainer)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Text(
                text = entry.metaLine,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.summary.isNotBlank()) {
                Text(
                    text = entry.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = entry.progressLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (funded) scheme.primary else scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        when {
            entry.mine -> CrowdTag(text = "我发起的")
            entry.pledged -> CrowdTag(text = "已认筹", emphasized = true)
            closed -> CrowdTag(text = "已关闭")
            funded -> CrowdTag(text = "已凑够", emphasized = true)
            else -> CrowdPledgeButton(
                label = if (pledging) "认筹中…" else "认筹",
                enabled = !pledging,
                onClick = onPledge,
            )
        }
    }
}

/** 行尾一个小胶囊：不是可点的，只是把状态写出来。 */
@Composable
private fun CrowdTag(text: String, emphasized: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (emphasized) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (emphasized) scheme.primary else FlSurfaceLevel.Page.raised.colorOf(scheme))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** 「认筹」：一个小一号的主色按钮。 */
@Composable
private fun CrowdPledgeButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (enabled) scheme.primary else scheme.surfaceContainerHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * 发起一团众筹：名字、官网、每人分摊、目标人数、说明。
 *
 * 门槛只收两样：名字不能空、目标人数在 1–999。官网与分摊可以是空的——先有人认，再谈价，
 * 这两格是给「已经问清楚价」的人写的。
 */
@Composable
private fun CrowdComposerDialog(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, url: String, share: String, target: Int, summary: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("10") }
    var summary by remember { mutableStateOf("") }

    CantaDialog(
        onDismissRequest = onDismiss,
        title = "发起一个众筹",
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
            TextButton(
                onClick = {
                    onSubmit(
                        name.trim(),
                        url.trim(),
                        share.trim(),
                        targetText.trim().toIntOrNull() ?: 10,
                        summary.trim(),
                    )
                },
                enabled = !submitting && name.isNotBlank(),
            ) {
                Text(if (submitting) "提交中…" else "发起")
            }
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("机场 / 商家名字") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("官网或邀请页（可空）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = share,
                onValueChange = { share = it },
                label = { Text("每人分摊") },
                placeholder = { Text("¥18 / 人 / 季") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = targetText,
                onValueChange = { text -> targetText = text.filter { it.isDigit() }.take(3) },
                label = { Text("几人开团") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.weight(0.6f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = summary,
            onValueChange = { summary = it },
            label = { Text("说明：几个人怎么分、用多久") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
