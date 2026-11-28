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
import org.jetbrains.compose.resources.stringResource
import io.github.samolego.canta.data.app.AppBadgeInfo
import io.github.samolego.canta.data.community.LevelBallotDimensions
import io.github.samolego.canta.data.community.LevelBallots
import io.github.samolego.canta.data.community.LevelFix
import io.github.samolego.canta.data.community.LevelFixRepository
import io.github.samolego.canta.data.community.LevelFixResult
import io.github.samolego.canta.ui.component.AppBadge
import io.github.samolego.canta.ui.component.BadgeImpact
import io.github.samolego.canta.ui.component.BadgeImpactTag
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
    var ballots by remember(packageName) { mutableStateOf<LevelBallots?>(null) }
    var note by remember { mutableStateOf("") }
    var votingBallot by remember { mutableStateOf(false) }
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
        // 三个维度（级别 / 后果 / 系统应用）的票数与"我投了什么"：和上面那趟一起取，
        // 进这一页只多一个请求，不会变成两个页面轮着问。
        ballots = repository.ballots(packageName, authorId) ?: ballots
        loading = false
    }

    // 门槛由后端给（同一个包统一），这里只用来把"还差多少"说清楚。
    val needed = ballots?.needed ?: fixes.firstOrNull()?.needed ?: LevelFix.VOTES_NEEDED
    val shareNeeded = ballots?.shareNeeded ?: fixes.firstOrNull()?.shareNeeded ?: LevelFix.SHARE_NEEDED
    val passedRemoval = fixes.firstOrNull { it.applied && !it.superseded }?.removal

    fun pick(dimension: String, choice: String) {
        if (votingBallot) return
        // 名字/头像没设完：这一页只能看，投不了（见 [IdentitySetup]）。
        if (!setupGate.ready) {
            notice = setupGate.refusal
            return
        }
        if (authorId.isEmpty()) {
            notice = "本机还没有身份，先设置名字和头像"
            return
        }
        votingBallot = true
        notice = null
        scope.launch {
            // 级别那一票顺手带上下面的"依据"（选填）；后果与系统应用不带。
            val result = repository.voteBallot(
                packageName = packageName,
                dimension = dimension,
                choice = choice,
                note = if (dimension == LevelBallotDimensions.LEVEL) note.trim() else "",
                voter = authorId,
            )
            when (result) {
                is LevelFixResult.Ok -> {
                    ballots = result.value
                    if (dimension == LevelBallotDimensions.LEVEL) {
                        notice = "已记下你选的一档；票够了会由工作流合并进名单"
                        reloads++
                    }
                }
                // 原因原样显示：网络没通 / 后端拒绝的原话 / 选项不合法，必须是不同的字。
                is LevelFixResult.Failed -> notice = "没记上：${result.reason}"
            }
            votingBallot = false
        }
    }

    /**
     * 只把「依据」写进已经投过的那一票。
     *
     * 选项点是即投，但依据是一段文字：写完了要重新提交同一票，后端才会更新 note
     * （`level_fix_ballot_vote` 对同一身份同一维度是 upsert）。还没选级别就没有可挂的地方。
     */
    fun submitNote() {
        if (votingBallot) return
        val level = ballots?.mineOf(LevelBallotDimensions.LEVEL)
        if (level == null) {
            notice = "先在上面选一个级别，依据跟着那一票一起存"
            return
        }
        if (note.isBlank()) {
            notice = "依据还是空的"
            return
        }
        if (!setupGate.ready) {
            notice = setupGate.refusal
            return
        }
        votingBallot = true
        notice = null
        scope.launch {
            when (val result = repository.voteBallot(
                packageName = packageName,
                dimension = LevelBallotDimensions.LEVEL,
                choice = level,
                note = note.trim(),
                voter = authorId,
            )) {
                is LevelFixResult.Ok -> {
                    ballots = result.value
                    notice = "依据已提交"
                }
                is LevelFixResult.Failed -> notice = "没提交上：${result.reason}"
            }
            votingBallot = false
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
                    } else {
                        Text(
                            text = "这个包不在这份名单里（不是系统包，或者名单还没收录它），没有可改的级别。",
                            style = FlText.footnote,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (publishedBadge != null) {
                item {
                    Card {
                        Text(
                            text = "投票",
                            style = FlText.caption1.copy(fontWeight = FlText.emphasized),
                            color = scheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        BallotGroup(
                            title = "级别 · 四选一",
                            dimension = LevelBallotDimensions.LEVEL,
                            options = LEVEL_OPTIONS,
                            ballots = ballots,
                            enabled = authorId.isNotEmpty() && !votingBallot,
                            onPick = { pick(LevelBallotDimensions.LEVEL, it) },
                        )
                        Spacer(Modifier.height(12.dp))
                        BallotGroup(
                            title = "后果 · 二选一",
                            dimension = LevelBallotDimensions.IMPACT,
                            options = IMPACT_OPTIONS,
                            ballots = ballots,
                            enabled = authorId.isNotEmpty() && !votingBallot,
                            onPick = { pick(LevelBallotDimensions.IMPACT, it) },
                        )
                        Spacer(Modifier.height(12.dp))
                        BallotGroup(
                            title = "系统应用 · 是 / 否",
                            dimension = LevelBallotDimensions.SYSTEM,
                            options = SYSTEM_OPTIONS,
                            ballots = ballots,
                            enabled = authorId.isNotEmpty() && !votingBallot,
                            onPick = { pick(LevelBallotDimensions.SYSTEM, it) },
                        )
                        Spacer(Modifier.height(12.dp))
                        // 「选这一档会发生什么」跟着**我投的级别**走；我还没投就用名单现在那一档。
                        val pickedLevel = ballots?.mineOf(LevelBallotDimensions.LEVEL)
                            ?.let { AppBadgeInfo.byNameIgnoreCaseOrNull(it) }
                            ?: publishedBadge
                        if (pickedLevel != null) {
                            Spacer(Modifier.height(9.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "我选的这一档",
                                    style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                                    color = scheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                                // 两枚标记就在标题这一行：会破坏功能 / 可能卡在开机。
                                BadgeImpactTags(pickedLevel)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = badgeRiskSummary(pickedLevel),
                                style = FlText.caption1.copy(fontWeight = FlText.emphasized),
                                color = scheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(300) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = FlText.caption1,
                            minLines = 2,
                            maxLines = 4,
                            // 这个框是**选填**：不填也能投，填了就跟着级别那一票存下来。
                            label = {
                                Text(text = "依据（选填）", style = FlText.caption2)
                            },
                            placeholder = {
                                Text(
                                    text = "可以不填",
                                    style = FlText.caption1,
                                    color = scheme.outline,
                                )
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = notice ?: if (votingBallot) {
                                    "正在记录…"
                                } else {
                                    "级别票到 $needed 票、占比到 ${(shareNeeded * 100).toInt()}% 才通过"
                                },
                                style = FlText.caption2,
                                color = if (notice != null && notice!!.startsWith("没")) {
                                    scheme.error
                                } else {
                                    scheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            // 「依据」是唯一需要自己按一下的东西：选了级别之后，写下来的话
                            // 要重新提交那一票才会存进去（选项本身点一下就已经记上了）。
                            val canSubmit = note.isNotBlank() && !votingBallot &&
                                ballots?.mineOf(LevelBallotDimensions.LEVEL) != null
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(
                                        if (canSubmit) scheme.primary
                                        else FlSurfaceLevel.Inset.colorOf(scheme),
                                    )
                                    .clickable(enabled = canSubmit) { submitNote() }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            ) {
                                Text(
                                    text = "提交依据",
                                    style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                                    color = if (canSubmit) scheme.onPrimary else scheme.onSurfaceVariant,
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
                        // 历史只读：投票在上面的三组里（每个维度一人一票）。再给旧提案留一套
                        // "认同 / 不认同"会把两套口径混在一起，页面自己先说不清哪个算数。
                        LevelFixCard(fix = fix, passedRemoval = passedRemoval)
                    }
                }
            }
        }
    }
}

/**
 * 三个维度的选项，key 就是后端约束里那套值。
 *
 * 级别只有四档：`System` 不在这里——它回答的是第三组「是不是系统应用」，不是什么级别。
 */
/** 选项块里那一枚徽标：级别用档位徽标，后果用后果标记，系统应用用 sys，其余不画。 */
private sealed interface BallotBadge {
    data class Level(val level: AppBadgeInfo) : BallotBadge
    data class Impact(val impact: BadgeImpact) : BallotBadge
    data object SystemApp : BallotBadge
    data object None : BallotBadge
}

/** 一个选项：后端接收的 key、界面上的名字、前面那枚徽标。 */
private data class BallotOption(
    val choice: String,
    val label: String,
    val badge: BallotBadge,
)

/**
 * 级别只有四档：`System` 不在这里——它回答的是第三组「是不是系统应用」，不是什么级别。
 * 徽标沿用列表那套（颜色也是），页面不另造一套。
 */
private val LEVEL_OPTIONS: List<BallotOption> = listOf(
    BallotOption("Recommended", "推荐卸载", BallotBadge.Level(AppBadgeInfo.RECOMMENDED)),
    BallotOption("Advanced", "进阶", BallotBadge.Level(AppBadgeInfo.ADVANCED)),
    BallotOption("Expert", "专家", BallotBadge.Level(AppBadgeInfo.EXPERT)),
    BallotOption("Unsafe", "不安全", BallotBadge.Level(AppBadgeInfo.UNSAFE)),
)

/** 后果：删了会不会破坏功能 / 会不会卡在开机。 */
private val IMPACT_OPTIONS: List<BallotOption> = listOf(
    BallotOption("breaks", "会破坏功能", BallotBadge.Impact(BadgeImpact.BREAKS_FEATURES)),
    BallotOption("bootloop", "可能卡在开机", BallotBadge.Impact(BadgeImpact.BOOTLOOP)),
)

/** 系统应用：是不是随机器预装的那一类。 */
private val SYSTEM_OPTIONS: List<BallotOption> = listOf(
    BallotOption("yes", "是", BallotBadge.SystemApp),
    BallotOption("no", "否", BallotBadge.None),
)

/**
 * 三个维度共用的选项块：标题 + 一项一行（徽标 + 名字 + 右侧百分比 + 比例条）。
 *
 * 百分比按维度各自归一（级别四项加起来 100%，后果两项加起来 100%），不跨维度相加——
 * 那是三个问题，不是一道多选题。一项一行是因为每行都要放徽标和百分比，挤成两列会把
 * "可能卡在开机"截成"可能卡在…"。
 */
@Composable
private fun BallotGroup(
    title: String,
    dimension: String,
    options: List<BallotOption>,
    ballots: LevelBallots?,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = FlText.caption2.copy(fontWeight = FlText.emphasized),
            color = scheme.onSurfaceVariant,
        )
        options.forEach { option ->
            val picked = ballots?.mineOf(dimension) == option.choice
            val percent = ballots?.percent(dimension, option.choice) ?: 0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    // 选中用统一的主题色；未选中就是这一页原来的底色，徽标各自保留原本的颜色。
                    .background(
                        if (picked) scheme.primary.copy(alpha = 0.12f)
                        else FlSurfaceLevel.Inset.colorOf(scheme),
                    )
                    .border(
                        width = if (picked) 1.5.dp else 1.dp,
                        color = if (picked) scheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable(enabled = enabled) { onPick(option.choice) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 徽标保持它本来的颜色（那是它的身份）；选中只把这一块的底色、边框、
                    // 名字和百分比换成主题色，两者不冲突。
                    when (val badge = option.badge) {
                        is BallotBadge.Level -> AppBadge(badge.level)
                        is BallotBadge.Impact -> BadgeImpactTag(badge.impact)
                        BallotBadge.SystemApp -> SystemBadge()
                        BallotBadge.None -> Unit
                    }
                    if (option.badge !is BallotBadge.None) Spacer(Modifier.width(6.dp))
                    Text(
                        text = option.label,
                        style = FlText.caption1.copy(
                            fontWeight = if (picked) FlText.emphasized else null,
                        ),
                        color = if (picked) scheme.primary else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    // 右侧百分比：这一项在本维度里占多少。
                    Text(
                        text = "$percent%",
                        style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                        color = if (picked) scheme.primary else scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                VoteBar(
                    fraction = percent / 100f,
                    colour = if (picked) scheme.primary else scheme.outlineVariant,
                )
            }
        }
    }
}

/** 一条级别提案：谁提的、要改成哪档、依据，以及当时有多少人认同。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelFixCard(
    fix: LevelFix,
    /** 同包已经通过的那一档；用来把「被取代」和「票并进了通过的那一档」分开说。 */
    passedRemoval: String?,
) {
    val scheme = MaterialTheme.colorScheme
    val levelBadge = AppBadgeInfo.byNameIgnoreCaseOrNull(fix.removal)
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

        // 票数只作记录：这一条当时有多少人认同 / 不认同。现在只有上面三组投票才算数，
        // 所以这里不再给"认同 / 不认同"按钮——两套口径同时存在，页面自己会说不清哪个是结果。
        Spacer(Modifier.height(8.dp))
        Text(
            text = "当时 ${fix.agrees} 人认同 · ${fix.disagrees} 人不认同",
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * 一根 4dp 的比例条：填充比例由调用方给（这一组选项在本维度里的占比），颜色也跟着选中状态走。
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

