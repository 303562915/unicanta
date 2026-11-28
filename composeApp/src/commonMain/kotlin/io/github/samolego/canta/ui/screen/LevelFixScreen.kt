package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import io.github.samolego.canta.data.app.AppBadgeInfo
import io.github.samolego.canta.data.community.LevelFix
import io.github.samolego.canta.data.community.LevelFixRepository
import io.github.samolego.canta.data.community.LevelFixResult
import io.github.samolego.canta.ui.component.AppBadge
import io.github.samolego.canta.ui.component.BadgeImpactTags
import io.github.samolego.canta.ui.component.DisabledBadge
import io.github.samolego.canta.ui.component.PersonAvatar
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SystemBadge
import io.github.samolego.canta.ui.component.avatarColorOf
import io.github.samolego.canta.ui.component.badgeRiskSummary
import io.github.samolego.canta.ui.component.relativeStamp
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBarCompactHeight
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.IdentitySetup
import kotlinx.coroutines.launch

/** Green for「这条改得对」，与描述修正那一页同一个色。 */
private val LEVEL_AGREE = Color(0xFF2FA84F)

/** 灰红 for「不该这么改」；比 [LEVEL_AGREE] 安静，因为被否掉不是过错。 */
private val LEVEL_DISAGREE = Color(0xFFC0564C)

/**
 * 「这个包该被标成什么级别」：现在是哪一档、谁提过什么、票数到哪了、以及自己要投哪一边。
 *
 * 与「描述修正」是同一套做法，但这一页多一句必须说清楚的话：票够了**不等于**马上生效——通过的
 * 那几条要由工作流合并进仓库里的 `level_fixes.json`，手机下一次核对（一周内）才看到新级别。
 * 所以这里的「已通过」写的是「已通过，等待合并下发」，不假装已经生效。
 *
 * 只在真正打开这一页时联网：应用列表页每进一次已经四个请求了，级别修正不能再多一个。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelFixScreen(
    packageName: String,
    appName: String,
    /** 名单现在给的级别；null = 这个包不在名单里，页面只能说"没有可改的"。 */
    publishedBadge: AppBadgeInfo?,
    /**
     * 是不是系统预装应用。列表上这枚**一定会画**（是不是系统包直接决定"删了会不会动到系统"），
     * 这一页照同一套画法，不把它并进别的标签里省掉。
     */
    systemApp: Boolean = false,
    /** 已停用：与 [systemApp] 同时成立就两枚都画，与 [AppTile] 一致，不二选一。 */
    disabled: Boolean = false,
    repository: LevelFixRepository,
    /** 本机签名用的显示名。 */
    author: String = "",
    /** 本机身份号：「我提的」「一人一票」都按它算。 */
    authorId: String = "",
    /** 名字与头像设过了没有：没设过这一页只能看和投票，提不了新修正（见 [IdentitySetup]）。 */
    setupGate: IdentitySetup = IdentitySetup.UNSET,
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var fixes by remember(packageName) {
        mutableStateOf(repository.cachedLevelFixes(packageName))
    }
    var loading by remember(packageName) { mutableStateOf(fixes.isEmpty()) }
    var reloads by remember { mutableIntStateOf(0) }
    var chosen by remember(packageName) { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var votingId by remember { mutableStateOf(0L) }
    var notice by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(packageName, authorId, reloads) {
        if (fixes.isEmpty()) loading = true
        val fetched = repository.fixes(packageName, authorId)
        if (fetched == null) {
            loadError = "没拉到最新的一版（网络或后端没通）"
        } else {
            fixes = fetched
            loadError = null
            repository.remember(packageName, fetched)
        }
        loading = false
    }

    val mine = fixes.firstOrNull { it.authorId.isNotEmpty() && it.authorId == authorId && !it.superseded }

    // 门槛由后端给（同一个包统一），这里只用来把"还差多少"说清楚。
    val needed = fixes.firstOrNull()?.needed ?: LevelFix.VOTES_NEEDED
    val shareNeeded = fixes.firstOrNull()?.shareNeeded ?: LevelFix.SHARE_NEEDED

    // 与后端 `private.level_fix_settle` 同一个口径：按档位聚合，而不是按单条提案。
    // 两处口径必须一致，否则会出现"页面说还差 12 票、后端已经通过了"这种自相矛盾的画面。
    val ballot = remember(fixes) {
        fixes.asSequence()
            .filter { !it.applied && !it.superseded && it.removal.isNotEmpty() }
            .groupBy { it.removal }
            .map { (removal, rows) ->
                LevelBallot(
                    removal = removal,
                    agrees = rows.sumOf { it.agrees },
                    disagrees = rows.sumOf { it.disagrees },
                    firstAt = rows.map { it.createdAt }.minOrNull().orEmpty(),
                )
            }
            .sortedWith(
                compareByDescending<LevelBallot> { it.net }
                    .thenByDescending { it.agrees }
                    .thenBy { it.firstAt },
            )
    }
    val leader = ballot.firstOrNull()
    val passedRemoval = fixes.firstOrNull { it.applied && !it.superseded }?.removal

    fun submit() {
        if (sending) return
        // 名字/头像没设完：发送键已经是灰的，这里再挡一次。
        if (!setupGate.ready) {
            notice = setupGate.refusal
            return
        }
        val removal = chosen.ifEmpty { mine?.removal.orEmpty() }
        if (removal.isEmpty()) return
        sending = true
        notice = null
        scope.launch {
            when (val result = repository.propose(packageName, removal, note.trim(), author, authorId)) {
                is LevelFixResult.Ok -> {
                    notice = "已提交。同一档位的票会合起来算；票最多的那一档到门槛后由工作流合并进名单"
                    note = ""
                    reloads++
                }
                // 原因原样显示：网络没通 / 后端拒绝的原话 / 档位不合法，必须是三种不同的字，
                // 否则用户看到的永远是"没能提交"，我们这边也只能靠猜。
                is LevelFixResult.Failed -> notice = "没能提交：${result.reason}"
            }
            sending = false
        }
    }

    fun vote(fix: LevelFix, agree: Boolean) {
        if (votingId != 0L) return
        votingId = fix.id
        scope.launch {
            when (val result = repository.vote(fix.id, authorId, agree)) {
                is LevelFixResult.Ok -> {
                    notice = if (result.value.applied) {
                        "这条已通过，等待合并进名单（一周内生效）"
                    } else {
                        null
                    }
                    reloads++
                }
                is LevelFixResult.Failed -> notice = "投票没成功：${result.reason}"
            }
            votingId = 0L
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                onNavigateBack = onNavigateBack,
                title = "级别修正",
                // 这一页第一张卡就是"这包现在什么级别"，顶栏用紧凑高度，卡片紧贴其下：
                // 原来 72dp 顶栏 + 6dp 留白，看起来像内容飘在半空。
                height = FlTopBarCompactHeight,
                actions = {
                    FlBarButton(
                        glyph = FlGlyphs.refresh,
                        contentDescription = "刷新",
                        onClick = { reloads++ },
                        iconSize = 20.dp,
                        filled = false,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card {
                    // 应用名当标题，下面一行才是"名单现在标的是"：原来把两句话挤在一行 caption2 里，
                    // 看完不知道这块在说哪个包。
                    Text(
                        text = appName,
                        style = FlText.footnote.copy(fontWeight = FlText.emphasized),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "名单现在标的是",
                        style = FlText.caption2,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (publishedBadge != null) {
                        // 档位徽标与两枚后果标记（会破坏功能 / 可能卡在开机）同排、同一种形状：
                        // 它们说的是同一件事（这一档有多危险），分开摆会像两类信息。
                        // 用 FlowRow 是因为小屏一行装不下"徽标 + 名称 + 两枚标记 + 系统/停用"。
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            AppBadge(publishedBadge)
                            Text(
                                text = stringResource(publishedBadge.nameRes),
                                style = FlText.caption1,
                                color = scheme.onSurface,
                                modifier = Modifier.padding(horizontal = 2.dp),
                            )
                            BadgeImpactTags(publishedBadge)
                            // 照列表 [AppTile] 原来的画法：系统应用这一枚**必画**（是不是系统包，
                            // 直接决定"删了会不会动到系统"），已停用再叠一枚。
                            if (systemApp) SystemBadge()
                            if (disabled) DisabledBadge()
                        }
                        // 名单现在这一档意味着什么，就写在档位名后面：读的人第一件事是
                        // 「这个包现在标成推荐卸载，那我删了会不会出事」。
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = badgeRiskSummary(publishedBadge),
                            style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "名单只按包名定级别，不知道这台机器往同一个包里塞了什么。" +
                                "删过、出过问题就写下来；票数够了级别会被改掉。",
                            style = FlText.caption2,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "这个包不在这份名单里（不是系统包，或者名单还没收录它），没有可改的级别。",
                            style = FlText.footnote,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "票最多的那一档：认同到 $needed 票、且认同率到 " +
                            "${(shareNeeded * 100).toInt()}% 时才算通过，之后工作流把它合并进仓库里的 " +
                            "level_fixes.json，手机下一次核对（一周内）才看到新级别——不是立刻生效。",
                        style = FlText.caption2,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            if (publishedBadge != null) {
                item {
                    Card {
                        Text(
                            text = if (mine != null) "改我提交的那条" else "提一条修正",
                            style = FlText.caption1.copy(fontWeight = FlText.emphasized),
                            color = scheme.onSurface,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            // 单选不是没做多选：一个包只有一个级别，点另一档是"改选"，不是叠加。
                            text = "档位只能选一档",
                            style = FlText.caption2,
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(7.dp))
                        // 五个档位各占一块，每块里就写着这一档现在有几票、离门槛还差多远：
                        // 选项和票数本来就是同一个对象，分开摆（上面五个选项、下面另列两档）
                        // 读起来像两份不相干的东西。点哪块就是选哪档，一行放两个或三个由 FlowRow 决定。
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LEVELS.forEach { level ->
                                val picked = (chosen.ifEmpty { mine?.removal.orEmpty() }) == level.listName
                                val rowBallot = ballot.firstOrNull { it.removal == level.listName }
                                val agrees = rowBallot?.agrees ?: 0
                                Column(
                                    modifier = Modifier
                                        .widthIn(min = 92.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        // 选中用统一的主题色，不按档位换色：五块说的是同一件事
                                        // （"选哪一档"），每块一个颜色反而像五类东西。
                                        .background(
                                            if (picked) scheme.primary.copy(alpha = 0.12f)
                                            else FlSurfaceLevel.Inset.colorOf(scheme),
                                        )
                                        .border(
                                            width = if (picked) 1.5.dp else 1.dp,
                                            color = if (picked) scheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .clickable { chosen = level.listName }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                ) {
                                    // 名字就是文字，前面不再放彩色块：色块留给上面的档位徽标，
                                    // 这里每块只回答"选哪一档、现在几票"。
                                    Text(
                                        text = stringResource(level.nameRes),
                                        style = FlText.caption1.copy(
                                            fontWeight = if (picked) FlText.emphasized else null,
                                        ),
                                        color = if (picked) scheme.primary else scheme.onSurface,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = when {
                                            // 已通过的那一档不在 `ballot` 里（那是"还悬着的"），
                                            // 所以这里单独说一句，别让通过之后反倒显示"还没有票"。
                                            passedRemoval == level.listName -> "已通过 · 待下发"
                                            agrees <= 0 -> "还没有票"
                                            rowBallot == leader -> "$agrees / $needed 票 · 最多"
                                            else -> "$agrees / $needed 票"
                                        },
                                        style = FlText.caption2,
                                        color = scheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    // 比例条：认同票 / 门槛。选中的那条跟着主题色走，其余用灰。
                                    VoteBar(
                                        fraction = agrees.toFloat() / needed.coerceAtLeast(1),
                                        colour = if (picked) scheme.primary else scheme.outlineVariant,
                                    )
                                }
                            }
                        }
                        // 选中哪一档，下面就说清这一档意味着什么（会不会破坏功能、会不会开不了机）。
                        // 这段就是「已安装」列表里点徽标看到的同一句，不另写一份。
                        val pickedLevel = AppBadgeInfo.byNameIgnoreCaseOrNull(
                            chosen.ifEmpty { mine?.removal.orEmpty() },
                        ) ?: publishedBadge
                        if (pickedLevel != null) {
                            Spacer(Modifier.height(9.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "选这一档会发生什么",
                                    style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                                    color = scheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                                // 两枚标记就在标题这一行：会破坏功能 / 可能卡在开机。
                                BadgeImpactTags(pickedLevel)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                // 最要紧的那件事（会不会破坏功能、会不会卡在开机）先说，
                                // 名单那段定义放在它下面当补充。
                                text = badgeRiskSummary(pickedLevel),
                                style = FlText.caption1.copy(fontWeight = FlText.emphasized),
                                color = scheme.onSurface,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stringResource(pickedLevel.description),
                                style = FlText.caption2,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(300) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = FlText.caption1,
                            minLines = 2,
                            maxLines = 4,
                            // 这个框是**选填**：不填也能提交，填了别人投票时多一条依据。
                            label = {
                                Text(text = "依据（选填）", style = FlText.caption2)
                            },
                            placeholder = {
                                Text(
                                    text = "可以不填。想写就写删了会怎样，例如「这台机器删掉它，深色模式自动切换失效」",
                                    style = FlText.caption1,
                                    color = scheme.outline,
                                )
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = notice ?: if (sending) "正在提交…" else "提交后别人可以投票",
                                style = FlText.caption2,
                                color = if (notice != null && notice!!.startsWith("没能")) {
                                    scheme.error
                                } else {
                                    scheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            val canSend = chosen.ifEmpty { mine?.removal.orEmpty() }.isNotEmpty() && !sending &&
                                setupGate.ready
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(if (canSend) scheme.primary else scheme.surfaceContainerHighest)
                                    .clickable(enabled = canSend) { submit() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = if (mine != null) "更新修正" else "提交修正",
                                    style = FlText.footnote.copy(fontWeight = FlText.emphasized),
                                    color = if (canSend) scheme.onPrimary else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
                // 原来是 headline：比顶栏标题还大，一进页面像被一块"修正历史"砸了一下。
                // 现在是小标题 + 计数胶囊，和上面两块卡片的层级一致。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                ) {
                    Text(
                        text = "修正历史",
                        style = FlText.caption1.copy(fontWeight = FlText.emphasized),
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${fixes.size}",
                        style = FlText.caption2,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(FlSurfaceLevel.Inset.colorOf(scheme))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }

            when {
                loading && fixes.isEmpty() -> item {
                    Text(
                        text = "…",
                        style = FlText.footnote,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                    )
                }

                fixes.isEmpty() -> item {
                    Text(
                        text = loadError ?: "还没有人提过这个包的级别修正。你是第一个用这版系统的人。",
                        style = FlText.footnote,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
                    )
                }

                else -> {
                    loadError?.let { reason ->
                        item {
                            Text(
                                text = "这次没刷新成功：$reason",
                                style = FlText.caption2,
                                color = scheme.error,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                            )
                        }
                    }
                    items(fixes, key = { it.id }) { fix ->
                        // 这一条所在档位的合计票（同档几条提案相加）；单条票数与它可能不一样，
                        // 页面两个都写出来，"票怎么算的"才不用猜。
                        val level = ballot.firstOrNull { it.removal == fix.removal }
                        LevelFixCard(
                            fix = fix,
                            levelAgrees = level?.agrees ?: fix.agrees,
                            levelDisagrees = level?.disagrees ?: fix.disagrees,
                            passedRemoval = passedRemoval,
                            voting = votingId == fix.id,
                            canVote = authorId.isNotEmpty(),
                            onVote = { agree -> vote(fix, agree) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 同一个档位（`removal`）下所有还悬着的提案合起来算的票。
 *
 * 后端的结算（`private.level_fix_settle`）就是按这个口径：同档几条提案的票相加，过门槛、且净票
 * 最多的那一档胜出。页面必须用同一个口径，"哪个档位票最多""还差几票"才不会和后端说的相反。
 *
 * 净票（认同减不认同）比单纯的认同票更能反映"这一档到底有没有争议"：一档 40 认同 30 不认同，
 * 不如一档 35 认同 2 不认同。
 */
private data class LevelBallot(
    val removal: String,
    val agrees: Int,
    val disagrees: Int,
    /** 这一档最早被提出的时间；同票时先到先得，与后端 `order by` 的最后一把标尺一致。 */
    val firstAt: String,
) {
    val votes: Int get() = agrees + disagrees
    val net: Int get() = agrees - disagrees
}

/** 名单的五个档位，顺序就是从严到宽再从严之外的那一档。 */
private val LEVELS = listOf(
    AppBadgeInfo.RECOMMENDED,
    AppBadgeInfo.ADVANCED,
    AppBadgeInfo.EXPERT,
    AppBadgeInfo.UNSAFE,
    AppBadgeInfo.SYSTEM,
)

/** 一条级别提案：谁提的、要改成哪档、依据、票数，以及两个投票按钮。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelFixCard(
    fix: LevelFix,
    /** 这一条所在档位的认同票合计（同档多条提案相加）。 */
    levelAgrees: Int,
    /** 同一档位的不认同票合计。 */
    levelDisagrees: Int,
    /** 同包已经通过的那一档；用来把「被取代」和「票并进了通过的那一档」分开说。 */
    passedRemoval: String?,
    voting: Boolean,
    canVote: Boolean,
    onVote: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val levelBadge = AppBadgeInfo.byNameIgnoreCaseOrNull(fix.removal)
    val levelColour = levelBadge?.badgeColor ?: scheme.primary
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(
                name = fix.author.ifEmpty { "?" },
                owner = fix.authorId,
                size = 20.dp,
                background = avatarColorOf(fix.author),
                letterColor = Color.White,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = fix.author.ifEmpty { "匿名" },
                style = FlText.caption1.copy(fontWeight = FlText.emphasized),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = relativeStamp(fix.createdAt),
                style = FlText.caption2,
                color = scheme.outline,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            when {
                fix.applied -> Text(
                    text = "已通过 · 待合并",
                    style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                    color = scheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(LEVEL_AGREE)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )

                fix.superseded -> Text(
                    // 同一档几条提案各自算票时，同一档永远凑不够门槛；所以结算按档聚合，
                    // 被并掉的那几条要说清是"票并进去了"，不是"被否了"。
                    text = if (passedRemoval != null && passedRemoval == fix.removal) {
                        "票已并入通过的这一档"
                    } else {
                        "已被后来的取代"
                    },
                    style = FlText.caption2,
                    color = scheme.outline,
                )
            }
        }

        Spacer(Modifier.height(7.dp))
        // 一行里放齐：建议标成 + 档位徽标 + 名称 + 两枚后果标记。形状与列表 item 的徽标一致，
        // 换行由 FlowRow 决定（小屏放不下"徽标 + 两枚标记"时折行，而不是把标记藏起来）。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "建议标成",
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            levelBadge?.let { badge ->
                AppBadge(badge)
                Text(
                    text = stringResource(badge.nameRes),
                    style = FlText.caption1,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                BadgeImpactTags(badge)
            }
        }
        // 这一档意味着什么，这里写两行：第一行是「这台机器会发生什么」（会不会破坏功能、
        // 会不会卡在开机），第二行才是名单自己的定义。看历史的人要判断的是「这个包到底能不能删」，
        // 光知道档位名叫"专家"没有意义。
        levelBadge?.let { badge ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = badgeRiskSummary(badge),
                style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(badge.description),
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val why = fix.note("zh").ifBlank { fix.note("en") }
        if (why.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = why,
                style = FlText.footnote,
                color = scheme.onSurface,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // 「这一档」才是后端结算的口径：同档几条提案的票相加。本条自己的数字只在
                // 提案还没通过、还有一条同档提案时才有意义，所以只在后面小字里提一句。
                text = "这一档 认同 $levelAgrees · 不认同 $levelDisagrees",
                style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                color = scheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            if (!fix.applied && !fix.superseded) {
                val missing = (fix.needed - levelAgrees).coerceAtLeast(0)
                Text(
                    text = if (missing > 0) "还差 $missing 票" else "票数够了",
                    style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                    color = if (missing > 0) scheme.onSurfaceVariant else LEVEL_AGREE,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        VoteBar(
            fraction = levelAgrees.toFloat() / fix.needed.coerceAtLeast(1),
            colour = levelColour,
        )

        if (!fix.applied && !fix.superseded) {
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (agree in listOf(true, false)) {
                    val picked = fix.mine == agree
                    VotePill(
                        label = when {
                            voting && picked -> "…"
                            agree -> "确实该这么改"
                            else -> "不该这么改"
                        },
                        count = if (agree) fix.agrees else fix.disagrees,
                        selected = picked,
                        color = if (agree) LEVEL_AGREE else LEVEL_DISAGREE,
                        enabled = canVote && !voting,
                        onClick = { onVote(agree) },
                    )
                }
            }
        }
    }
}

/** 一侧投票按钮：选中后填成这一侧的颜色并打勾，与「描述修正」那一页同一套。 */
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
            .background(if (selected) color else FlSurfaceLevel.Inset.colorOf(scheme))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Text(text = "✓", fontSize = 11.sp, color = Color.White)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = if (count > 0) "$label $count" else label,
            style = FlText.caption2.copy(fontWeight = if (selected) FlText.emphasized else null),
            color = if (selected) Color.White else scheme.onSurface,
        )
    }
}

/**
 * 一根 4dp 的票数条：填充比例 = 认同票 / 门槛，颜色是这一档自己的徽标色。
 *
 * 比再写一串"还差 23 票"更好读：差得多长一眼就看得出来，差得少条纹就快贴到头。
 */
@Composable
private fun VoteBar(fraction: Float, colour: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colour.copy(alpha = 0.16f)),
    ) {
        val filled = fraction.coerceIn(0f, 1f)
        if (filled > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(filled)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colour),
            )
        }
    }
}

/** 这一页里统一的卡片底：与「描述修正」那一页同一套（14dp 圆角、Block 层、12/10 内边距）。 */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(MaterialTheme.colorScheme))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}

