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
import io.github.samolego.canta.data.community.LevelBallotChoices
import io.github.samolego.canta.data.community.LevelBallotDimensions
import io.github.samolego.canta.data.community.LevelBallotEntry
import io.github.samolego.canta.data.community.LevelBallots
import io.github.samolego.canta.data.community.LevelFix
import io.github.samolego.canta.data.community.LevelFixRepository
import io.github.samolego.canta.data.community.LevelFixResult
import io.github.samolego.canta.data.community.StampSummary
import io.github.samolego.canta.data.community.Stamps
import io.github.samolego.canta.ui.component.AppBadge
import io.github.samolego.canta.ui.component.BadgeImpact
import io.github.samolego.canta.ui.component.BadgeImpactTag
import io.github.samolego.canta.ui.component.BadgeImpactTags
import io.github.samolego.canta.ui.component.DisabledBadge
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.StampChip
import io.github.samolego.canta.ui.component.StampInk
import io.github.samolego.canta.ui.component.SystemBadge
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


/**
 * 「这个包该被标成什么级别」：现在是哪一档、谁提过什么、票数到哪了、以及自己要投哪一边。
 *
 * 四组：级别 / 后果 / 系统应用 / **印章**。前三组回答「该不该删、删了会不会出事」，是一件事的
 * 三个面；印章回答「它犯了什么罪」（毒瘤 / 病毒 / 远控 / 木马 / 广告 / 弹窗，都是两字），票够了
 * 会斜盖到应用详情页上。所以印章是独立的一组票、独立一张表（见 `StampSummary`），不并进前三组，
 * 也不参与它们的百分比，更不参与"删了会不会出事"的结算。
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
    var ballots by remember(packageName) { mutableStateOf<LevelBallots?>(null) }
    // 印章那一路：与三组投票分开一趟（后端也是两张表），失败不影响上面三组正常画。
    var stamps by remember(packageName) { mutableStateOf<StampSummary?>(null) }
    var history by remember(packageName) { mutableStateOf<List<LevelBallotEntry>>(emptyList()) }
    var loading by remember(packageName) { mutableStateOf(true) }
    var reloads by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    var votingBallot by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(packageName, authorId, reloads) {
        // 两个请求一起发：一个给三组的票数与"我投了什么"，一个给「修正历史」里的人和依据。
        val freshBallots = repository.ballots(packageName, authorId)
        if (freshBallots == null) {
            loadError = "没拉到最新的一版（网络或后端没通）"
        } else {
            ballots = freshBallots
            loadError = null
        }
        history = repository.history(packageName) ?: history
        // 印章这一路失败就保持上一次的样子（没投过就是全 0）：它只是标签，不该把整页拖成"没拉到"。
        stamps = repository.stamps(packageName, authorId) ?: stamps
        loading = false
    }

    // 门槛由后端给（同一个包统一），这里只用来把"还差多少"说清楚。
    val needed = ballots?.needed ?: LevelFix.VOTES_NEEDED
    val shareNeeded = ballots?.shareNeeded ?: LevelFix.SHARE_NEEDED

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
            // 级别那一票顺手带上下面的"依据"（选填）；后果与系统应用不带，「不知道」也不带。
            val result = repository.voteBallot(
                packageName = packageName,
                dimension = dimension,
                choice = choice,
                note = if (dimension == LevelBallotDimensions.LEVEL &&
                    choice != LevelBallotChoices.UNKNOWN
                ) {
                    note.trim()
                } else {
                    ""
                },
                voter = authorId,
                // 显示名只为历史里认得出是谁；没有名字就写"匿名"。
                name = author,
            )
            when (result) {
                is LevelFixResult.Ok -> {
                    ballots = result.value
                    if (dimension == LevelBallotDimensions.LEVEL) {
                        // 「不知道」不是一档，别再说"记下你选的一档"——那句话只对真实档位成立。
                        notice = if (choice == LevelBallotChoices.UNKNOWN) {
                            "已记下：你不确定这一档（不计入百分比，随时可以改）"
                        } else {
                            "已记下你选的一档；票够了会由工作流合并进名单"
                        }
                    }
                    // 三组都要刷新历史：这一票刚进去，历史里该多一行（或那一行改了选项）。
                    reloads++
                }
                // 原因原样显示：网络没通 / 后端拒绝的原话 / 选项不合法，必须是不同的字。
                is LevelFixResult.Failed -> notice = "没记上：${result.reason}"
            }
            votingBallot = false
        }
    }

    /**
     * 盖一枚章。一人一包一枚：点另一枚就是改票，不是两枚一起挂着。
     *
     * 与三组投票同一套身份门槛与报错口径（没设名字/头像就投不了、失败原因原样显示）。
     */
    fun pickStamp(title: String) {
        if (votingBallot) return
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
            when (val result = repository.voteStamp(packageName, title, authorId, author)) {
                is LevelFixResult.Ok -> {
                    stamps = result.value
                    notice = if (result.value.applied == title) {
                        "已盖：$title（这枚已经是这个包的章了）"
                    } else {
                        "已记下：$title（票到 ${result.value.needed} 票、占多数就盖上去）"
                    }
                }
                is LevelFixResult.Failed -> notice = "没记上：${result.reason}"
            }
            votingBallot = false
        }
    }

    /**
     * 只把「依据」写进已经投过的那一票。
     *
     * 选项点是即投，但依据是一段文字：写完了要重新提交同一票，后端才会更新 note
     * （`level_fix_ballot_vote_v2` 对同一身份同一维度是 upsert）。还没选级别就没有可挂的地方。
     */
    fun submitNote() {
        if (votingBallot) return
        val level = ballots?.mineOf(LevelBallotDimensions.LEVEL)
        if (level == null) {
            notice = "先在上面选一个级别，依据跟着那一票一起存"
            return
        }
        if (level == LevelBallotChoices.UNKNOWN) {
            notice = "选了「不知道」就没有级别可挂依据；先从上面选一档"
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
                name = author,
            )) {
                is LevelFixResult.Ok -> {
                    ballots = result.value
                    notice = "依据已提交"
                    // 历史里那条记录现在带着依据，重新拉一趟让它显示出来。
                    reloads++
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
                            text = if (ballots?.applied == true) "投票 · 已通过，等待下发" else "投票",
                            style = FlText.caption1.copy(fontWeight = FlText.emphasized),
                            color = scheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        BallotGroup(
                            title = "级别",
                            dimension = LevelBallotDimensions.LEVEL,
                            options = LEVEL_OPTIONS,
                            ballots = ballots,
                            enabled = authorId.isNotEmpty() && !votingBallot,
                            onPick = { pick(LevelBallotDimensions.LEVEL, it) },
                        )
                        Spacer(Modifier.height(12.dp))
                        BallotGroup(
                            title = "后果",
                            dimension = LevelBallotDimensions.IMPACT,
                            options = IMPACT_OPTIONS,
                            ballots = ballots,
                            enabled = authorId.isNotEmpty() && !votingBallot,
                            onPick = { pick(LevelBallotDimensions.IMPACT, it) },
                        )
                        Spacer(Modifier.height(12.dp))
                        BallotGroup(
                            title = "系统应用",
                            dimension = LevelBallotDimensions.SYSTEM,
                            options = SYSTEM_OPTIONS,
                            ballots = ballots,
                            enabled = authorId.isNotEmpty() && !votingBallot,
                            onPick = { pick(LevelBallotDimensions.SYSTEM, it) },
                        )
                        Spacer(Modifier.height(12.dp))
                        // 第四组：章。前三组问「该不该删」，这一组问「它犯了什么罪」——所以它不并进
                        // 上面任何一组，也不参与那三组的百分比。票够了才盖到详情页上。
                        StampGroup(
                            stamps = stamps,
                            enabled = authorId.isNotEmpty() && !votingBallot,
                            onPick = { pickStamp(it) },
                        )
                        Spacer(Modifier.height(12.dp))
                        // 「选这一档会发生什么」跟着**我投的级别**走；我还没投就用名单现在那一档。
                        // 投的是「不知道」时这一块整块不画：它不是一档，硬拿名单那一档顶上，
                        // 等于替用户说他自己没说的话。
                        val myLevel = ballots?.mineOf(LevelBallotDimensions.LEVEL)
                        val pickedLevel = myLevel
                            ?.takeIf { it != LevelBallotChoices.UNKNOWN }
                            ?.let { AppBadgeInfo.byNameIgnoreCaseOrNull(it) }
                            ?: publishedBadge.takeIf { myLevel == null }
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
                                    "级别票到 $needed 票、占比到 ${(shareNeeded * 100).toInt()}% 才通过（不知道不算票）"
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
                                ballots?.mineOf(LevelBallotDimensions.LEVEL)?.let {
                                    it != LevelBallotChoices.UNKNOWN
                                } == true
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
                        text = "${history.size}",
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
                loading && history.isEmpty() -> item {
                    Text(
                        text = "…",
                        style = FlText.footnote,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                    )
                }

                history.isEmpty() -> item {
                    Text(
                        text = loadError ?: "还没有人投过。投完就会出现在这里（待通过的也在）。",
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
                    // 每一条就是"某人在某组投了什么"：待通过的、没通过的、已经下发的都在里面，
                    // 顺序按投票时间倒序。页面不在这里判断通过与否，那是顶部门槛那条线的事。
                    items(history) { entry ->
                        BallotHistoryRow(entry)
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
 *
 * 最后一档是「不知道」：没见过这个包的人不该被逼着在四档里挑一个（挑错了还会在历史里挂着）。
 * 它没有徽标、不算进百分比，后端结算时也排除（见 [LevelBallotChoices.UNKNOWN]）。
 */
private val LEVEL_OPTIONS: List<BallotOption> = listOf(
    BallotOption("Recommended", "推荐卸载", BallotBadge.Level(AppBadgeInfo.RECOMMENDED)),
    BallotOption("Advanced", "进阶", BallotBadge.Level(AppBadgeInfo.ADVANCED)),
    BallotOption("Expert", "专家", BallotBadge.Level(AppBadgeInfo.EXPERT)),
    BallotOption("Unsafe", "不安全", BallotBadge.Level(AppBadgeInfo.UNSAFE)),
    BallotOption(LevelBallotChoices.UNKNOWN, "不知道", BallotBadge.None),
)

/** 后果：删了会不会破坏功能 / 会不会卡在开机；没见过就说「不知道」。 */
private val IMPACT_OPTIONS: List<BallotOption> = listOf(
    BallotOption("breaks", "会破坏功能", BallotBadge.Impact(BadgeImpact.BREAKS_FEATURES)),
    BallotOption("bootloop", "可能卡在开机", BallotBadge.Impact(BadgeImpact.BOOTLOOP)),
    BallotOption(LevelBallotChoices.UNKNOWN, "不知道", BallotBadge.None),
)

/** 系统应用：是不是随机器预装的那一类；不确定就说「不知道」。 */
private val SYSTEM_OPTIONS: List<BallotOption> = listOf(
    BallotOption("yes", "是", BallotBadge.SystemApp),
    BallotOption("no", "否", BallotBadge.None),
    BallotOption(LevelBallotChoices.UNKNOWN, "不知道", BallotBadge.None),
)

/**
 * 第四组：印章。选项本身就是一枚小章，让人投票的时候就看到盖在详情页上长什么样。
 *
 * 与 [BallotGroup] 有一处不同：右边显示的是**票数**，不是百分比。首发只有六个词，票数普遍是个位数，
 * 一张 0% / 17% / 33% 的表会把「还没人盖」说得太郑重；占比只在结算门槛上起作用（[Stamps.SHARE_NEEDED]）。
 */
@Composable
private fun StampGroup(
    stamps: StampSummary?,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val mine = stamps?.mine
    val applied = stamps?.applied.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "印章",
                style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            // 已经盖上去的是哪一枚，就写在标题这一行；没盖就问它犯了什么罪。
            Text(
                text = if (applied.isNotEmpty()) "已盖：$applied" else "它犯了什么罪",
                style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                color = if (applied.isNotEmpty()) StampInk.colorOf(applied) else scheme.outline,
            )
        }
        Stamps.TITLES.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { title ->
                    val picked = mine == title
                    val votes = stamps?.votesOf(title) ?: 0
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            // 选中用这一枚章自己的墨色：红章被选是红的，橙章被选是橙的，
                            // 换成主题色就看不出自己选的是哪一档了。
                            .background(
                                if (picked) StampInk.colorOf(title).copy(alpha = 0.10f)
                                else FlSurfaceLevel.Inset.colorOf(scheme),
                            )
                            .border(
                                width = if (picked) 1.5.dp else 1.dp,
                                color = if (picked) StampInk.colorOf(title) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable(enabled = enabled) { onPick(title) }
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StampChip(text = title, selected = picked)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = if (votes > 0) "$votes 票" else "没人盖",
                            style = FlText.caption2.copy(
                                fontWeight = if (picked) FlText.emphasized else null,
                            ),
                            color = if (picked) scheme.onSurface else scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                // 单数行：占住右半格，别让最后一枚撑满整行。
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            text = "票到 ${stamps?.needed ?: Stamps.NEEDED} 票、占 " +
                "${((stamps?.shareNeeded ?: Stamps.SHARE_NEEDED) * 100).toInt()}% 就盖到详情页上；" +
                "换一枚就是改票",
            style = FlText.caption2,
            color = scheme.outline,
        )
    }
}

/**
 * 三个维度共用的选项块：标题 + 一行两个的选项，每项右侧是**本维度内**的百分比。
 *
 * 百分比按维度各自归一（级别四项加起来 100%，后果两项加起来 100%），不跨维度相加——
 * 那是三个问题，不是一道多选题。一行两个：每项只要放得下徽标、名字和一个百分比就够了。
 *
 * 每组最后一项是「不知道」：它没有徽标、不参与归一（不进分母），右侧显示的是它自己的票数。
 * 没见过这个包的人不该被逼着在真实选项里挑一个——选错了会作为一条记录留在历史里。
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
        options.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { option ->
                    val picked = ballots?.mineOf(dimension) == option.choice
                    val percent = ballots?.percent(dimension, option.choice) ?: 0
                    val isUnknown = option.choice == LevelBallotChoices.UNKNOWN
                    val unknownVotes = ballots?.votesOf(dimension, option.choice) ?: 0
                    Row(
                        modifier = Modifier
                            .weight(1f)
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
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 徽标保持它本来的颜色（那是它的身份）；选中只把这一块的底色、边框、
                        // 名字和百分比换成主题色，两者不冲突。
                        when (val badge = option.badge) {
                            is BallotBadge.Level -> AppBadge(badge.level)
                            is BallotBadge.Impact -> BadgeImpactTag(badge.impact)
                            BallotBadge.SystemApp -> SystemBadge()
                            BallotBadge.None -> Unit
                        }
                        if (option.badge !is BallotBadge.None) Spacer(Modifier.width(5.dp))
                        Text(
                            text = option.label,
                            style = FlText.caption2.copy(
                                fontWeight = if (picked) FlText.emphasized else null,
                            ),
                            color = if (picked) scheme.primary else scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(4.dp))
                        // 右侧数字：真实选项是这一项在本维度里的占比；「不知道」不参与这个算法，
                        // 所以显示它自己的票数（0 票就不显示，别写一个误导人的 "0%"）。
                        Text(
                            text = if (isUnknown) {
                                if (unknownVotes > 0) "${unknownVotes}票" else ""
                            } else {
                                "$percent%"
                            },
                            style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                            color = if (picked) scheme.primary else scheme.onSurfaceVariant,
                        )
                    }
                }
                // 单数行：占住右半格，别让最后一项撑满整行。
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 一条投票记录：某人在某一组投了什么、什么时候，以及（级别组）写下的依据。 */
@Composable
private fun BallotHistoryRow(entry: LevelBallotEntry) {
    val scheme = MaterialTheme.colorScheme
    // 「不知道」不是任何一档，也就没有徽标：它会落到 else 分支被当成 breaks/否 画错。
    val undecided = entry.choice == LevelBallotChoices.UNKNOWN
    val badge = when {
        undecided -> null
        entry.dimension == LevelBallotDimensions.LEVEL -> AppBadgeInfo.byNameIgnoreCaseOrNull(entry.choice)
        else -> null
    }
    val impact = when {
        undecided -> null
        entry.dimension == LevelBallotDimensions.IMPACT -> when (entry.choice) {
            "bootloop" -> BadgeImpact.BOOTLOOP
            else -> BadgeImpact.BREAKS_FEATURES
        }
        else -> null
    }
    val label = when {
        undecided -> "不知道"
        entry.dimension == LevelBallotDimensions.LEVEL -> badge?.let { stringResource(it.nameRes) } ?: entry.choice
        entry.dimension == LevelBallotDimensions.IMPACT -> impact?.label ?: entry.choice
        entry.dimension == LevelBallotDimensions.SYSTEM -> if (entry.choice == "yes") "是" else "否"
        else -> entry.choice
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                badge != null -> {
                    AppBadge(badge)
                    Spacer(Modifier.width(5.dp))
                }
                impact != null -> {
                    BadgeImpactTag(impact)
                    Spacer(Modifier.width(5.dp))
                }
                entry.dimension == LevelBallotDimensions.SYSTEM && entry.choice == "yes" -> {
                    SystemBadge()
                    Spacer(Modifier.width(5.dp))
                }
            }
            Text(
                text = "$label · ${entry.name.ifBlank { "匿名" }}",
                style = FlText.caption1.copy(fontWeight = FlText.emphasized),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = relativeStamp(entry.at),
                style = FlText.caption2,
                color = scheme.outline,
                maxLines = 1,
            )
        }
        if (entry.note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.note,
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
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

