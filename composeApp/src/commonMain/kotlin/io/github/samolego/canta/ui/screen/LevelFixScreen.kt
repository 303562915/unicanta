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
import io.github.samolego.canta.data.community.LevelFix
import io.github.samolego.canta.data.community.LevelFixRepository
import io.github.samolego.canta.ui.component.AppBadge
import io.github.samolego.canta.ui.component.PersonAvatar
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.avatarColorOf
import io.github.samolego.canta.ui.component.relativeStamp
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
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
@Composable
fun LevelFixScreen(
    packageName: String,
    appName: String,
    /** 名单现在给的级别；null = 这个包不在名单里，页面只能说"没有可改的"。 */
    publishedBadge: AppBadgeInfo?,
    repository: LevelFixRepository?,
    /** 本机签名用的显示名。 */
    author: String = "",
    /** 本机身份号：「我提的」「一人一票」都按它算。 */
    authorId: String = "",
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var fixes by remember(packageName) {
        mutableStateOf(repository?.cachedLevelFixes(packageName).orEmpty())
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
        val fetched = repository?.fixes(packageName, authorId)
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
    val chosenFix = fixes.firstOrNull { !it.superseded && !it.applied }

    fun submit() {
        if (repository == null || sending) return
        val removal = chosen.ifEmpty { mine?.removal.orEmpty() }
        if (removal.isEmpty()) return
        sending = true
        notice = null
        scope.launch {
            val id = repository.propose(packageName, removal, note.trim(), author, authorId)
            sending = false
            if (id == null) {
                notice = "没能提交：检查网络，或换一个档位再试"
            } else {
                notice = "已提交。别人可以投票；票数够了会由工作流合并进名单"
                note = ""
                reloads++
            }
        }
    }

    fun vote(fix: LevelFix, agree: Boolean) {
        if (repository == null || votingId != 0L) return
        votingId = fix.id
        scope.launch {
            val answer = repository.vote(fix.id, authorId, agree)
            votingId = 0L
            notice = when {
                answer == null -> "投票没成功，稍后再试"
                answer.applied -> "这条已通过，等待合并进名单（一周内生效）"
                else -> null
            }
            if (answer != null) reloads++
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                onNavigateBack = onNavigateBack,
                title = "级别修正",
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
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card {
                    Text(
                        text = "名单现在标的是",
                        style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(5.dp))
                    if (publishedBadge != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppBadge(publishedBadge)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(publishedBadge.nameRes),
                                style = FlText.footnote,
                                color = scheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "名单是按包名定的，它不知道这台机器往同一个包里塞了什么。" +
                                "如果你删过这个包、机器出过问题，把你的情况写下来；别人认同到门槛，" +
                                "级别就会被改掉，所有人看到的新级别都带着你这条依据。",
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
                        text = "票数够之后，工作流把它合并进仓库里的 level_fixes.json，" +
                            "手机下一次核对（一周内）才看到新级别——不是立刻生效。",
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
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LEVELS.forEach { level ->
                                val picked = (chosen.ifEmpty { mine?.removal.orEmpty() }) == level.name
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(if (picked) scheme.primary else scheme.surfaceContainerHighest)
                                        .clickable { chosen = level.name }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                ) {
                                    Text(
                                        text = stringResource(level.nameRes),
                                        style = FlText.caption1.copy(
                                            fontWeight = if (picked) FlText.emphasized else null,
                                        ),
                                        color = if (picked) scheme.onPrimary else scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(300) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = FlText.caption1,
                            minLines = 2,
                            maxLines = 4,
                            placeholder = {
                                Text(
                                    text = "删了会怎样？例如「这台机器删掉它，深色模式自动切换失效」",
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
                            val canSend = chosen.ifEmpty { mine?.removal.orEmpty() }.isNotEmpty() && !sending
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
                Text(
                    text = "修正历史 · ${fixes.size}",
                    style = FlText.headline,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
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
                        LevelFixCard(
                            fix = fix,
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

/** 名单的五个档位，顺序就是从严到宽再从严之外的那一档。 */
private val LEVELS = listOf(
    AppBadgeInfo.RECOMMENDED,
    AppBadgeInfo.ADVANCED,
    AppBadgeInfo.EXPERT,
    AppBadgeInfo.UNSAFE,
    AppBadgeInfo.SYSTEM,
)

/** 一条级别提案：谁提的、要改成哪档、依据、票数，以及两个投票按钮。 */
@Composable
private fun LevelFixCard(
    fix: LevelFix,
    voting: Boolean,
    canVote: Boolean,
    onVote: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
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
                    text = "已被后来的取代",
                    style = FlText.caption2,
                    color = scheme.outline,
                )
            }
        }

        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "建议标成",
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            AppBadgeInfo.byNameIgnoreCaseOrNull(fix.removal)?.let { badge ->
                AppBadge(badge)
                Spacer(Modifier.width(6.dp))
                Text(text = stringResource(badge.nameRes), style = FlText.caption1, color = scheme.onSurface)
            }
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

        Spacer(Modifier.height(7.dp))
        Text(
            text = buildString {
                append("认同 ${fix.agrees} · 不认同 ${fix.disagrees}")
                if (!fix.applied && !fix.superseded) {
                    append(" · 还差 ${fix.missingVotes} 票")
                    fix.share?.let { append(" · 认同率 ${(it * 100).toInt()}%") }
                }
            },
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
        )

        if (!fix.applied && !fix.superseded) {
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (agree in listOf(true, false)) {
                    val picked = fix.mine == agree
                    val colour = if (agree) LEVEL_AGREE else LEVEL_DISAGREE
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (picked) colour else scheme.surfaceContainerHighest)
                            .clickable(enabled = canVote && !voting) { onVote(agree) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = when {
                                voting && picked -> "…"
                                agree -> "确实该这么改"
                                else -> "不该这么改"
                            },
                            style = FlText.caption1.copy(
                                fontWeight = if (picked) FlText.emphasized else null,
                            ),
                            color = if (picked) Color.White else scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 这一页里统一的卡片底：与「描述修正」那一页同一套内边距。 */
@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        content()
    }
}

