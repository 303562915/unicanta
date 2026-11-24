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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.cache.ScreenCache
import kotlinx.serialization.Serializable
import io.github.samolego.canta.data.community.DescriptionFix
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.ui.component.LetterAvatar
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.avatarColorOf
import io.github.samolego.canta.ui.component.relativeStamp
import io.github.samolego.canta.ui.component.text.UrlText
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import kotlinx.coroutines.launch

/** Green for "这条修正说得对", the same pass colour the vote bar uses. */
private val FIX_AGREE = Color(0xFF2FA84F)

/** Grey-red for "这条修正不对"; quieter than [FIX_AGREE], because a rejected rewrite is not a fault. */
private val FIX_DISAGREE = Color(0xFFC0564C)

/**
 * 这一页上一次画出来的那份修正历史。
 *
 * 只存列表：上面「现在显示」那张卡片、下面每条票数、以及「我提交的那条」，都是从这一份列表里
 * 算出来的（`applied` / `mine` / `share` 都在 [DescriptionFix] 自己身上），一份列表就够画一屏。
 * 不放 `MutableState`、输入框里写到一半的字——那是这一页的执行状态，不是后端数据。
 */
@Serializable
private data class DescriptionFixSnapshot(val fixes: List<DescriptionFix>)

/** 这一屏在进程缓存里的槽位；按包名分份，不同应用的修正历史绝不能互相串。 */
private fun descriptionFixCacheKey(packageName: String) = "screen:descfix:$packageName"

/**
 * The corrected descriptions of one app: what people proposed, how the votes stand, and the two
 * numbers a rewrite has to reach before the app page shows it instead of the published line.
 *
 * The page is a page rather than a dialog because it is a list that grows: the history of a popular
 * package is worth scrolling, and a sheet tall enough to hold it would be a page anyway.
 *
 * Nothing is read from or written to this phone: the corrections live on the backend, keyed by
 * package, and an install only appears here as the identity that filed and voted.
 */
@Composable
fun DescriptionFixScreen(
    packageName: String,
    appName: String,
    /** The line the app page shows today, from the bundled bloat list. */
    publishedDescription: String,
    repository: PlanRepository?,
    /** How this install signs what it files. */
    author: String = "",
    /** This install's identity number: what "my rewrite" and one vote per identity are keyed on. */
    authorId: String = "",
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    // 这个应用上一次画出来的那份历史（进程级，见 [ScreenCache]）：有它就先画它，`loading` 直接是
    // false，于是进页面不再先闪一下「…」。每次被导航进来都是新的 Composable，`remember` 跟着一起
    // 没了，所以快照得放在进程里。
    val cacheKey = descriptionFixCacheKey(packageName)
    // 没有后端时不用缓存：这一屏的每一条都来自后端，没有后端就没有东西可对。
    val cached = remember(packageName, repository) {
        if (repository == null) null else ScreenCache.get<DescriptionFixSnapshot>(cacheKey)
    }
    var fixes by remember(packageName) { mutableStateOf(cached?.fixes.orEmpty()) }
    var loading by remember(packageName) { mutableStateOf(cached == null) }
    var reloads by remember { mutableIntStateOf(0) }
    var body by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var votingId by remember { mutableStateOf(0L) }
    var notice by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(packageName, authorId, reloads) {
        // 只在没有东西可画的时候才点亮「…」：刷新和投票会重跑这个 effect，每次都转一圈的话，
        // 屏幕上明明有内容却又被换成一个省略号。
        if (fixes.isEmpty()) loading = true
        val fetched = repository?.descriptionFixes(packageName, authorId)
        if (fetched == null) {
            // 拉不到（没有后端、被拒、断网）：屏幕上那一份**不换**，缓存也不动。把整页换成
            // 「还没有人改过这条说明」等于把断网画成「数据被清空」。
            // `lastError` 在断网时是 null（那是"没答"不是"被拒"），所以两种情况分开说。
            loadError = repository?.lastError
                ?: if (repository?.lastFailureWasNetwork == true) "网络没通，没拉到最新的一版" else null
        } else {
            fixes = fetched
            // A backend without the function is not a failure of the page: it simply has no corrections
            // to show, and `lastError` there is the function-missing message. Keep it as the hint.
            loadError = if (fetched.isEmpty()) repository?.lastError else null
            ScreenCache.put(cacheKey, DescriptionFixSnapshot(fetched))
        }
        loading = false
    }

    val applied = fixes.firstOrNull { it.applied }
    val mine = fixes.firstOrNull { it.authorId.isNotEmpty() && it.authorId == authorId && !it.applied }

    val submit: () -> Unit = {
        val text = body.trim()
        val why = note.trim()
        if (text.length >= 4 && !sending) {
            scope.launch {
                sending = true
                val id = repository?.addDescriptionFix(
                    packageName = packageName,
                    body = text,
                    note = why,
                    author = author.ifEmpty { "u-anon" },
                    authorId = authorId,
                )
                notice = when {
                    id != null -> {
                        body = ""
                        note = ""
                        null
                    }
                    repository == null -> "没有连上社区后端，修正没发出去。"
                    else -> "没发出去：${repository?.lastError ?: "检查一下网络再试"}"
                }
                sending = false
                if (id != null) reloads++
            }
        }
    }

    val vote: (DescriptionFix, Boolean) -> Unit = { fix, agree ->
        if (votingId == 0L) {
            scope.launch {
                votingId = fix.id
                val fresh = repository?.voteDescriptionFix(fix.id, authorId.ifEmpty { author }, agree)
                notice = if (fresh == null) {
                    "没投上：${repository?.lastError ?: "检查一下网络再试"}"
                } else {
                    null
                }
                votingId = 0L
                if (fresh != null) reloads++
            }
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                onNavigateBack = onNavigateBack,
                title = "描述修正",
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
                // What the page shows today: the accepted rewrite when there is one, the published
                // line otherwise. Naming the source is what keeps "这是谁写的" answerable.
                Card {
                    Text(
                        text = if (applied != null) "现在显示（社区修正）" else "现在显示（官方说明）",
                        style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                        color = if (applied != null) scheme.primary else scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    val shown = applied?.body?.takeIf { it.isNotBlank() }
                        ?: publishedDescription.takeIf { it.isNotBlank() }
                    if (shown != null) {
                        UrlText(text = shown, style = FlText.footnote)
                    } else {
                        Text(
                            text = "这个应用还没有说明，你可以写第一条。",
                            style = FlText.footnote,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    applied?.let { fix ->
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "由 ${fix.author.ifEmpty { "匿名" }} 修正 · ${fix.agrees}/${fix.votes} 人认同",
                            style = FlText.caption2,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Text(
                    text = "认同满 ${DescriptionFix.VOTES_NEEDED} 票、且认同率到 " +
                        "${(DescriptionFix.SHARE_NEEDED * 100).toInt()}% 时自动替换上面的说明；" +
                        "票数掉下去也会自动退回官方说明。",
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item {
                Card {
                    Text(
                        text = if (mine != null) "修改我提交的修正" else "写一条修正",
                        style = FlText.caption1.copy(fontWeight = FlText.emphasized),
                        color = scheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it.take(4000) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = FlText.footnote,
                        minLines = 3,
                        maxLines = 8,
                        placeholder = {
                            Text(
                                text = "$appName 是做什么的？用你自己的话说一遍。",
                                style = FlText.footnote,
                                color = scheme.outline,
                            )
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(300) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = FlText.caption1,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        placeholder = {
                            Text(
                                text = "哪里不对（可选，例如「这条是 2 年前的旧说明」）",
                                style = FlText.caption1,
                                color = scheme.outline,
                            )
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                sending -> "正在提交…"
                                notice != null -> notice.orEmpty()
                                mine != null -> "再提交会覆盖你还没被采纳的那条"
                                else -> "提交后别人可以投票"
                            },
                            style = FlText.caption2,
                            color = if (notice != null) scheme.error else scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        val canSend = body.trim().length >= 4 && !sending
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(if (canSend) scheme.primary else scheme.surfaceContainerHighest)
                                .clickable(enabled = canSend, onClick = submit)
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

            item {
                Text(
                    text = "修正历史 · ${fixes.size}",
                    style = FlText.headline,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }

            when {
                // 只有在没有东西可画时才显示这行省略号：有缓存的情况下这一页从第一帧就是列表，
                // 刷新也只是列表上面的数字在变。
                loading && fixes.isEmpty() -> item {
                    Text(
                        text = "…",
                        style = FlText.footnote,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                    )
                }

                fixes.isEmpty() -> item {
                    Text(
                        text = loadError?.let { "后端没有返回修正：$it" }
                            ?: "还没有人改过这条说明。写一条，让下一个人不用对着旧文案猜。",
                        style = FlText.footnote,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
                    )
                }

                else -> {
                    // 这次刷新没成功：列表留着（它是上一次拉到的），只在上面加一行说明。整页换成
                    // 一句「还没有人改过」会把「没拉到」说成「没有」，那是两件事。
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
                        FixCard(
                            fix = fix,
                            voting = votingId == fix.id,
                            canVote = authorId.isNotEmpty() || author.isNotEmpty(),
                            onVote = { agree -> vote(fix, agree) },
                        )
                    }
                }
            }
        }
    }
}

/** One rewrite: who wrote it, what it says, and the two counts its fate is decided by. */
@Composable
private fun FixCard(
    fix: DescriptionFix,
    voting: Boolean,
    canVote: Boolean,
    onVote: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterAvatar(
                name = fix.author.ifEmpty { "?" },
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
            if (fix.applied) {
                Text(
                    text = "已采纳",
                    style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                    color = scheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(FIX_AGREE)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        UrlText(text = fix.body, style = FlText.subheadline)

        if (fix.note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "为什么改：${fix.note}",
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        // The bar: share of agreeing votes, the same reading as the removal vote's green/red split.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.surfaceContainerHighest),
        ) {
            if (fix.votes > 0) {
                if (fix.agrees > 0) {
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .weight(fix.agrees.toFloat())
                            .background(FIX_AGREE),
                    )
                }
                if (fix.disagrees > 0) {
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .weight(fix.disagrees.toFloat())
                            .background(FIX_DISAGREE),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildString {
                    append("${fix.agrees} 人认同")
                    fix.share?.let { append(" · ${(it * 100).toInt()}%") }
                    append(" · ")
                    append("${fix.disagrees} 人不认同")
                },
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = when {
                fix.applied -> "已在所有页面生效；认同率掉下去会自动退回官方说明。"
                fix.missingVotes > 0 -> "还差 ${fix.missingVotes} 票认同" +
                    ((fix.share?.let { " · 现在 ${(it * 100).toInt()}%" }) ?: "")
                (fix.share ?: 0f) < fix.shareNeeded ->
                    "票数够了，但认同率只有 ${((fix.share ?: 0f) * 100).toInt()}%，还差 " +
                        "${(fix.shareNeeded * 100).toInt()}%"
                else -> "已达成条件，等待后端结算"
            },
            style = FlText.caption2,
            color = if (fix.applied) scheme.primary else scheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            VotePill(
                label = if (fix.mine == true) "已认同" else "认同",
                count = fix.agrees,
                selected = fix.mine == true,
                color = FIX_AGREE,
                enabled = canVote && !voting,
                onClick = { onVote(true) },
            )
            Spacer(Modifier.width(8.dp))
            VotePill(
                label = if (fix.mine == false) "已不认同" else "不认同",
                count = fix.disagrees,
                selected = fix.mine == false,
                color = FIX_DISAGREE,
                enabled = canVote && !voting,
                onClick = { onVote(false) },
            )
            Spacer(Modifier.weight(1f))
            if (voting) {
                Text(text = "…", style = FlText.caption2, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** One side of a rewrite's vote: filled once this install picked it, raised surface while not. */
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
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Text(
                text = "✓",
                fontSize = 11.sp,
                color = Color.White,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = "$label $count",
            style = FlText.caption1.copy(fontWeight = if (selected) FlText.emphasized else FontWeight.Normal),
            color = if (selected) Color.White else scheme.onSurface,
        )
    }
}

/** The block every section of this page sits on: one raised surface, 14 dp corners. */
@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(MaterialTheme.colorScheme))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}
