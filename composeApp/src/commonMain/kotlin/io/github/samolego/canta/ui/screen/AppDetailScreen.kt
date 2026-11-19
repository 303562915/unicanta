package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.community.AppNoteStore
import io.github.samolego.canta.data.community.AppReview
import io.github.samolego.canta.data.community.PackageVotes
import io.github.samolego.canta.data.community.PlanComment
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.threads
import io.github.samolego.canta.data.community.updatedComment
import io.github.samolego.canta.ui.component.AppBadge
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.component.CommentComposer
import io.github.samolego.canta.ui.component.LetterAvatar
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.commentThreads
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.description_source
import io.github.samolego.canta.generated.resources.description_translated_source
import org.jetbrains.compose.resources.stringResource
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.DeviceProfile
import kotlinx.coroutines.launch

/** Green for "the phone was fine without it", the colour of a pass. */
private val CAN_REMOVE = Color(0xFF2FA84F)

/** Red for "removing it broke something". */
private val CANNOT_REMOVE = Color(0xFFE0453B)

/** Height of the two-sided vote bar. */
private val VOTE_BAR = 10.dp

/**
 * One app's own page: what it is, whether this phone can live without it, and what other
 * owners of the same phone said about removing it.
 *
 * The vote is two-sided: people say either "the phone was fine without it" (green) or "removing
 * it broke something" (red), and the bar shows both sides at once. Votes are counted twice — on
 * this exact model and ROM version, and on every device — and the page lets the reader switch
 * between the two, because a verdict from a different ROM is context, not advice.
 *
 * The comments below the vote are the same thread a plan has: anybody can answer anybody, the
 * install can delete what it wrote, and a review written here is kept on the device as well, so it
 * is still readable when the backend cannot be reached.
 */
@Composable
fun AppDetailScreen(
    packageName: String,
    app: AppInfo?,
    profile: DeviceProfile?,
    notes: AppNoteStore,
    author: String,
    onNavigateBack: () -> Unit,
    /** The community backend; null keeps the vote and the reviews local. */
    repository: PlanRepository? = null,
    /** Who the vote and the comments are recorded under: this install's identity number. */
    voter: String = "",
    /** This install's secret, needed to delete a comment it wrote. */
    token: String = "",
) {
    val scope = rememberCoroutineScope()
    var reviews by remember { mutableStateOf<List<AppReview>>(emptyList()) }
    var comments by remember { mutableStateOf<List<PlanComment>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var submitFailed by remember { mutableStateOf(false) }
    /** The comment being answered; null while the composer writes a new one. */
    var replyTo by remember { mutableStateOf<PlanComment?>(null) }
    var pendingDelete by remember { mutableStateOf<PlanComment?>(null) }
    // What this install said about the app, and what everybody else said.
    var mine by remember { mutableStateOf<Boolean?>(null) }
    var votes by remember { mutableStateOf(PackageVotes.EMPTY) }
    var voting by remember { mutableStateOf(false) }
    // 0 = this phone only, 1 = every device.
    var voteScope by remember { mutableIntStateOf(0) }
    // A vote is confirmed before it is sent: one phone casts one vote, so a mistap is a real change.
    var pendingVote by remember { mutableStateOf<Boolean?>(null) }
    // Why the last vote or comment did not go through, when the backend said something.
    var voteError by remember { mutableStateOf<String?>(null) }
    var sendError by remember { mutableStateOf<String?>(null) }
    /** Threads the reader opened with "展开 N 条回复"; the rest keep their answers folded. */
    var expandedThreads by remember { mutableStateOf(setOf<Long>()) }
    /**
     * Comments this install put away with the broken heart: a personal hide, this session only —
     * nothing on the server changes, and the page forgets it on the way out.
     */
    var hiddenComments by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(packageName, voter) {
        reviews = notes.reviewsOf(packageName)
        votes = repository?.packageVotes(packageName, profile) ?: PackageVotes.EMPTY
        // The backend's copy of this install's vote wins over the local one: it is what the counts
        // next to it were computed from, and it survives a reinstall.
        mine = if (repository != null && voter.isNotEmpty()) {
            repository.myPackageVote(packageName, voter) ?: notes.packageVerdict(packageName)
        } else {
            notes.packageVerdict(packageName)
        }
        comments = repository?.appComments(packageName).orEmpty()
    }

    val castVote: (Boolean) -> Unit = { canRemove ->
        if (!voting) {
            scope.launch {
                voting = true
                voteError = null
                val fresh = repository?.votePackage(packageName, voter, canRemove, profile)
                if (fresh == null) {
                    // Nothing was recorded: keep the page showing what the server still says.
                    voteError = repository?.lastError?.let { "投票没记上：$it" } ?: "投票没发出去，检查一下网络。"
                    if (repository == null) {
                        // No backend configured: the vote is this phone's own note, as before.
                        mine = canRemove
                        notes.setPackageVerdict(packageName, canRemove)
                    }
                } else {
                    mine = canRemove
                    votes = fresh
                    notes.setPackageVerdict(packageName, canRemove)
                }
                voting = false
            }
        }
    }

    val send: () -> Unit = {
        val body = draft.trim()
        if (body.isNotEmpty() && !sending) {
            val signature = author.ifEmpty { "u-anon" }            scope.launch {
                sending = true
                // The device copy first: a review written offline is still this install's, and it
                // is what the page falls back to while the community thread is out of reach.
                notes.addReview(
                    packageName,
                    AppReview(
                        author = signature,
                        body = body,
                        model = profile?.displayModel.orEmpty(),
                        sdk = profile?.sdk ?: 0,
                        createdAt = "",
                        removable = mine == true,
                        system = profile?.systemLabel().orEmpty(),
                    ),
                )
                reviews = notes.reviewsOf(packageName)
                val posted = repository?.commentOnApp(
                    pkg = packageName,
                    body = body,
                    author = signature,
                    authorId = voter,
                    replyTo = replyTo?.id ?: 0L,
                    deviceModel = profile?.displayModel.orEmpty(),
                    systemName = profile?.systemLabel().orEmpty(),
                    replyToName = replyTo?.author.orEmpty(),
                )
                submitFailed = repository != null && posted == null
                if (submitFailed) {
                    // "网络问题" is wrong half the time: the backend names the actual refusal.
                    sendError = repository?.lastError?.let { "发送失败：$it" } ?: "发送失败，检查一下网络再试"
                } else {
                    sendError = null
                    draft = ""
                    replyTo = null
                    comments = repository?.appComments(packageName).orEmpty()
                }
                sending = false
            }
        }
    }

    val likeComment: (PlanComment) -> Unit = { comment ->
        if (comment.id > 0L) {
            val wanted = !comment.liked
            // The page answers the tap at once; the backend's count corrects it when it answers.
            comments = comments.updatedComment(comment.id) {
                it.copy(liked = wanted, likes = (it.likes + if (wanted) 1 else -1).coerceAtLeast(0))
            }
            scope.launch {
                val result = repository?.likeComment(comment.id, voter, wanted)
                if (result != null) {
                    comments = comments.updatedComment(comment.id) {
                        it.copy(liked = result.liked, likes = result.likes)
                    }
                }
            }
        }
    }

    pendingDelete?.let { comment ->
        CantaDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除评论",
            buttons = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        repository?.deleteComment(comment.id, voter, token)
                        comments = repository?.appComments(packageName).orEmpty()
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
        ) {
            Text(
                text = "「${comment.body.take(40)}」会被删除，它下面的回复一起去掉。",
                fontSize = 12.sp,
            )
        }
    }

    // The vote a tap asked for, waiting for a yes: the counts are public, so a vote is a statement.
    pendingVote?.let { wanted ->
        CantaDialog(
            onDismissRequest = { pendingVote = null },
            title = "确认投票",
            buttons = {
                TextButton(onClick = { pendingVote = null }) { Text("再想想") }
                TextButton(onClick = {
                    pendingVote = null
                    castVote(wanted)
                }) {
                    Text("确认", fontWeight = FontWeight.Bold)
                }
            },
        ) {
            Text(
                text = if (wanted) {
                    "投「可以删」：告诉别人你的${profile?.displayModel ?: "机型"}上删掉它没问题。\n每台设备只有一票，投了以后可以改。"
                } else {
                    "投「不可以删」：告诉别人删掉它会让你的${profile?.displayModel ?: "机型"}出问题。\n每台设备只有一票，投了以后可以改。"
                },
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = app?.name ?: packageName,
                onNavigateBack = onNavigateBack,
            )
        },
        bottomBar = {
            CommentComposer(
                draft = draft,
                onDraftChange = {
                    draft = it
                    if (submitFailed) submitFailed = false
                },
                onSend = send,
                replyToName = replyTo?.author.orEmpty(),
                onCancelReply = { replyTo = null },
                sending = sending,
                failed = submitFailed,
                failLabel = sendError ?: "发送失败，检查一下网络再试",
            )
        },
    ) { padding ->
        val threads = remember(comments) { comments.threads() }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                AppHero(
                    app = app,
                    packageName = packageName,
                    votes = votes,
                    scope = voteScope,
                    onScopeChange = { voteScope = it },
                    profile = profile,
                    mine = mine,
                    voting = voting,
                    voteError = voteError,
                    onVote = { wanted -> pendingVote = wanted },
                )
            }
            item { SectionHeading("评论 · ${comments.size}") }
            when {
                threads.isNotEmpty() -> commentThreads(
                    threads = threads,
                    me = author,
                    expanded = expandedThreads,
                    onToggleThread = { key ->
                        expandedThreads =
                            if (key in expandedThreads) expandedThreads - key else expandedThreads + key
                    },
                    onReply = { comment ->
                        replyTo = comment
                        submitFailed = false
                    },
                    onDelete = { comment -> pendingDelete = comment },
                    onLike = likeComment,
                    hidden = hiddenComments,
                    onHide = { key -> hiddenComments = hiddenComments + key },
                )

                // Nothing from the community: what this install wrote itself is still worth
                // showing, and it is all there is until the thread loads again.
                reviews.isNotEmpty() -> {
                    item {
                        Text(
                            text = "还没有别人的评论，下面是本机写过的：",
                            style = FlText.caption2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    items(reviews) { review -> ReviewRow(review) }
                }

                else -> item {
                    Text(
                        text = "还没有人评论这个应用。写一条，告诉别人它在你的机型上是什么样。",
                        style = FlText.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** The app itself: icon, name, package, version, size, and the two-sided vote about removing it. */
@Composable
private fun AppHero(
    app: AppInfo?,
    packageName: String,
    votes: PackageVotes,
    scope: Int,
    onScopeChange: (Int) -> Unit,
    profile: DeviceProfile?,
    mine: Boolean?,
    voting: Boolean,
    voteError: String? = null,
    onVote: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (app != null) {
                AppIconImage(app, size = 56.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surfaceContainerHighest),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app?.name ?: packageName,
                    style = FlText.title3,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = packageName,
                    style = FlText.caption2.copy(fontFamily = FontFamily.Monospace),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val facts = buildList {
                    app?.let { add("v${it.versionName}") }
                    app?.apkSize?.let { add(formatSize(it)) }
                    if (app?.isSystemApp == true) add("系统应用")
                    if (app?.isUninstalled == true) add("已卸载")
                }
                if (facts.isNotEmpty()) {
                    Text(
                        text = facts.joinToString(" · "),
                        style = FlText.caption1,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        app?.badgeInfo?.let { badge ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppBadge(badge)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(badge.description),
                    style = FlText.caption1,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        app?.description?.takeIf { it.isNotBlank() }?.let { text ->
            Spacer(Modifier.height(8.dp))
            Text(text = text, style = FlText.footnote, color = scheme.onSurface)
            // The bloat list is published in English only. Say where the words came from: either
            // "upstream English" or "translated by this build", never an unlabelled block.
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(
                    if (app.descriptionTranslated) Res.string.description_translated_source
                    else Res.string.description_source
                ),
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))
        VoteBlock(
            votes = votes,
            scope = scope,
            onScopeChange = onScopeChange,
            profile = profile,
            mine = mine,
            voting = voting,
            voteError = voteError,
            onVote = onVote,
        )
    }
}

/**
 * The two-sided vote: green "it was fine without it" against red "it broke something", as a bar,
 * the two counts, the phone the counts are for, and the two buttons that cast this install's vote.
 *
 * [scope] picks which counts are shown: 0 is this model and ROM version, 1 is every device. Both
 * are computed by the backend in one call, so switching costs nothing.
 */
@Composable
private fun VoteBlock(
    votes: PackageVotes,
    scope: Int,
    onScopeChange: (Int) -> Unit,
    profile: DeviceProfile?,
    mine: Boolean?,
    voting: Boolean,
    voteError: String? = null,
    onVote: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val canRemove = if (scope == 0) votes.canRemove else votes.allCanRemove
    val cannotRemove = if (scope == 0) votes.cannotRemove else votes.allCannotRemove
    val total = canRemove + cannotRemove
    val share = if (total == 0) 0f else canRemove.toFloat() / total

    Column(modifier = Modifier.fillMaxWidth()) {
        // Two pills instead of a segmented control: this sits inside the hero's own 16dp, and a
        // control with its own padding would end up too narrow to read.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScopePill(
                label = "只看本机",
                selected = scope == 0,
                onClick = { onScopeChange(0) },
            )
            Spacer(Modifier.width(8.dp))
            ScopePill(
                label = "全部机型",
                selected = scope == 1,
                onClick = { onScopeChange(1) },
            )
        }

        // The bar: one track, split by what people said. Green is left, red is right, and the
        // split is the share of "can remove".
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(VOTE_BAR)
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.surfaceContainerHighest),
        ) {
            if (total > 0) {
                if (canRemove > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(canRemove.toFloat())
                            .background(CAN_REMOVE),
                    )
                }
                if (cannotRemove > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(cannotRemove.toFloat())
                            .background(CANNOT_REMOVE),
                    )
                }
            }
        }

        // The two counts, with their share of the bar right next to them: a number without the
        // other side is not a ratio, and the bar alone cannot be read to the vote.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$canRemove 人可以删" + if (total > 0) " · ${(share * 100).toInt()}%" else "",
                style = FlText.caption1.copy(fontWeight = FontWeight.W600),
                color = if (canRemove > 0) CAN_REMOVE else scheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$cannotRemove 人不可以删" + if (total > 0) " · ${((1f - share) * 100).toInt()}%" else "",
                style = FlText.caption1.copy(fontWeight = FontWeight.W600),
                color = if (cannotRemove > 0) CANNOT_REMOVE else scheme.onSurfaceVariant,
            )
        }

        // Which phone the counts above are for, and how many votes are behind them.
        Text(
            text = buildString {
                append(if (scope == 0) (profile?.shortLabel() ?: "本机") else "所有机型")
                append(" · 共 ")
                append(total)
                append(" 票")
                if (total == 0) append(" · 还没有人投票，你可以是第一票")
            },
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoteButton(
                label = if (mine == true) "我选：可以删" else "可以删",
                color = CAN_REMOVE,
                selected = mine == true,
                enabled = !voting,
                onClick = { onVote(true) },
            )
            Spacer(Modifier.width(10.dp))
            VoteButton(
                label = if (mine == false) "我选：不可以删" else "不可以删",
                color = CANNOT_REMOVE,
                selected = mine == false,
                enabled = !voting,
                onClick = { onVote(false) },
            )
        }
        Text(
            text = when {
                voting -> "正在投票…"
                voteError != null -> voteError
                mine != null -> "本机已投：${if (mine) "可以删" else "不可以删"}（再投一次就是改票）"
                else -> "投票前会再确认一次"
            },
            style = FlText.caption2,
            color = if (voteError != null) scheme.error else scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** One scope choice: filled once picked, a raised page surface while not. */
@Composable
private fun ScopePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        style = FlText.caption1.copy(
            fontWeight = if (selected) FontWeight.W600 else FontWeight.Normal,
        ),
        color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) scheme.primary else FlSurfaceLevel.Page.raised.colorOf(scheme))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** One side of the vote: outlined until this install picked it, filled once it did. */
@Composable
private fun VoteButton(
    label: String,
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) color else FlSurfaceLevel.Page.raised.colorOf(scheme))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIconInline(
            glyph = if (selected) FlGlyphs.checkCircle else FlGlyphs.check,
            tint = if (selected) Color.White else color,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = FlText.footnote.copy(fontWeight = FlText.emphasized),
            color = if (selected) Color.White else scheme.onSurface,
        )
    }
}

/** A review this install wrote, shown while the community thread has nothing to say. */
@Composable
private fun ReviewRow(review: AppReview) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.medium)
            .drawBehind { drawRect(FlSurfaceLevel.Page.raised.colorOf(scheme)) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Every review carries an avatar: the first character of the name, or of the
            // install code when the reviewer has not renamed themselves.
            LetterAvatar(
                name = review.author,
                size = 24.dp,
                background = scheme.secondaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = review.author,
                style = FlText.caption1.copy(fontWeight = FlText.emphasized),
                color = scheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            // The phone and its Android version: a review is only worth as much as the
            // device it was written on.
            val device = review.deviceLabel
            if (device.isNotEmpty()) {
                Text(
                    text = device,
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            if (review.removable) {
                Text(
                    text = "可卸",
                    style = FlText.caption2,
                    color = scheme.primary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // The same size the community comment list uses for a comment: a review and a comment are
        // the same kind of thing to read, and two sizes for them would read as two kinds.
        Text(text = review.body, style = FlText.subheadline, color = scheme.onSurface)
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = FlText.headline,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FlGlyphIconInline(
    glyph: io.github.samolego.canta.ui.fl.FlGlyph,
    tint: androidx.compose.ui.graphics.Color,
) {
    io.github.samolego.canta.ui.fl.FlGlyphIcon(glyph = glyph, size = 16.dp, tint = tint, fill = 1f)
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) "${(mb * 10).toInt() / 10.0} MB" else "${bytes / 1024} KB"
}
