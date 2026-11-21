package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.community.AppNoteStore
import io.github.samolego.canta.data.community.AppReview
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.DescriptionFix
import io.github.samolego.canta.data.community.PackageVotes
import io.github.samolego.canta.data.community.PlanComment
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.FIRST_REPLY_BATCH
import io.github.samolego.canta.data.community.NEXT_REPLY_BATCH
import io.github.samolego.canta.data.community.threadOf
import io.github.samolego.canta.data.community.threads
import io.github.samolego.canta.data.community.withAnchorFirst
import io.github.samolego.canta.data.community.updatedComment
import io.github.samolego.canta.data.recommend.RecommendedApp
import io.github.samolego.canta.data.recommend.Recommendations
import io.github.samolego.canta.ui.component.AppBadge
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.component.CommentComposer
import io.github.samolego.canta.ui.component.LetterAvatar
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.commentThreads
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlTopBarCompactHeight
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
    onOpenUrl: (String) -> Unit,
    onNavigateBack: () -> Unit,
    /** The community backend; null keeps the vote and the reviews local. */
    repository: PlanRepository? = null,
    /** Who the vote and the comments are recorded under: this install's identity number. */
    voter: String = "",
    /** This install's secret, needed to delete a comment it wrote. */
    token: String = "",
    /** Opens the page where this app's description can be corrected and voted on. */
    onOpenDescriptionFixes: (String) -> Unit = {},
    /** Opens one of the plans that hold this app, from the related-plans drawer. */
    onOpenPlan: (Long) -> Unit = {},
    /**
     * The comment this page was opened on, from the inbox: its thread is put first and opened, so
     * "somebody answered you" lands on the answer rather than on the top of the page.
     */
    anchorCommentId: Long = 0L,
) {
    val scope = rememberCoroutineScope()
    var reviews by remember { mutableStateOf<List<AppReview>>(emptyList()) }
    var comments by remember { mutableStateOf<List<PlanComment>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var submitFailed by remember { mutableStateOf(false) }
    // Whether the sticker panel under the bar is open; the page closes it when a tap lands outside.
    var pickerOpen by remember { mutableStateOf(false) }
    /** The comment being answered; null while the composer writes a new one. */
    var replyTo by remember { mutableStateOf<PlanComment?>(null) }
    var pendingDelete by remember { mutableStateOf<PlanComment?>(null) }
    // What this install said about the app, and what everybody else said.
    var mine by remember { mutableStateOf<Boolean?>(null) }
    var votes by remember { mutableStateOf(PackageVotes.EMPTY) }
    var voting by remember { mutableStateOf(false) }
    /**
     * Which votes and comments the page shows: every device by default, because a thread narrowed to
     * one model is usually empty, and an empty thread reads as "nobody uses this" when the rest of
     * the world is one tap away. 0 is this model and ROM, 1 every device, 2 one system version.
     */
    var voteScope by remember(packageName) { mutableIntStateOf(1) }
    var systemsExpanded by remember(packageName) { mutableStateOf(false) }
    val selectedSystemState = remember(packageName) { mutableStateOf("") }
    val selectedSystem = selectedSystemState.value
    val onSystemChange: (String) -> Unit = { selectedSystemState.value = it }
    var recommendations by remember { mutableStateOf<Recommendations?>(null) }
    /**
     * The community's corrections of this app's description: the accepted one replaces the published
     * line, the rest are counted for the entry that opens their page.
     */
    var descriptionFixes by remember { mutableStateOf<List<DescriptionFix>>(emptyList()) }
    // Why the last vote or comment did not go through, when the backend said something.
    var voteError by remember { mutableStateOf<String?>(null) }
    var sendError by remember { mutableStateOf<String?>(null) }
    /** Threads the reader opened with "展开 N 条回复"; the rest keep their answers folded. */
    var expandedThreads by remember { mutableStateOf(setOf<Long>()) }
    /** How many answers each open thread shows; absent means the first batch. */
    var shownReplies by remember { mutableStateOf(mapOf<Long, Int>()) }
    /**
     * Comments this install put away with the broken heart: a personal hide, this session only —
     * nothing on the server changes, and the page forgets it on the way out.
     */
    var hiddenComments by remember { mutableStateOf(setOf<Long>()) }
    /**
     * The plans of this phone that hold this app. The page answers "can this phone live without it";
     * the plans answer the other half — "who already proved a whole set that drops it", which is the
     * step a reader takes after one green vote. Loaded with the page so the drawer opens instantly.
     */
    var relatedPlans by remember(packageName) { mutableStateOf<List<CommunityPlan>>(emptyList()) }
    var relatedLoading by remember(packageName) { mutableStateOf(false) }
    /** Whether the related-plans drawer is out; it closes on the scrim, on the ×, and on a tap on a plan. */
    var drawerOpen by remember(packageName) { mutableStateOf(false) }

    LaunchedEffect(packageName, profile, repository) {
        val repo = repository ?: return@LaunchedEffect
        relatedLoading = true
        // The plans come back for this model and build only, which is the scope the drawer claims:
        // a plan for another phone would put a set on screen that was never proven here.
        relatedPlans = repo.plansFor(profile).filter { packageName in it.packages }
        relatedLoading = false
    }

    LaunchedEffect(packageName) {
        recommendations = Recommendations.bundled()
    }

    /** Where this page's unsent comment is kept; see [AppNoteStore.draftOf]. */
    val draftKey = "app:$packageName"

    // Half-typed text survives leaving the page: this is the page people leave to go and look
    // something up, which is exactly when a comment gets abandoned by accident.
    LaunchedEffect(draftKey) {
        notes.ensureReady()
        if (draft.isEmpty()) draft = notes.draftOf(draftKey)
    }
    LaunchedEffect(draft, draftKey) {
        notes.setDraft(draftKey, draft)
    }

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
        comments = repository?.appComments(packageName, voter).orEmpty()
        descriptionFixes = repository?.descriptionFixes(packageName, voter).orEmpty()
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
            val signature = author.ifEmpty { "u-anon" }
            scope.launch {
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
                    comments = repository?.appComments(packageName, voter).orEmpty()
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
                        comments = repository?.appComments(packageName, voter).orEmpty()
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

    Scaffold(
        // The title bar floats over the content as a transparent layer, like the plan page: what
        // scrolls up under it stays readable through it, and the page's first card starts just
        // under the status bar.
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
                pickerOpen = pickerOpen,
                onPickerOpenChange = { pickerOpen = it },
            )
        },
    ) { padding ->
        // The thread follows the same device scope as the vote above it: with "只看本机" picked, a
        // Redmi K70 page lists what K70 owners wrote, not what a Redmi K30 Ultra wrote.
        val visibleComments = remember(comments, voteScope, selectedSystem, profile?.displayModel) {
            scopedToDevice(comments, voteScope, selectedSystem, profile?.displayModel.orEmpty())
        }
        val threads = remember(visibleComments, anchorCommentId) {
            visibleComments.threads().withAnchorFirst(anchorCommentId)
        }
        // The thread the reader came for is opened, not folded: an answer reached from the inbox is
        // behind "展开 N 条回复", and having to open it again means the tap did not really take them
        // there.
        LaunchedEffect(anchorCommentId) {
            val anchored = threads.threadOf(anchorCommentId) ?: return@LaunchedEffect
            expandedThreads = expandedThreads + anchored.comment.id
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                // A tap on the page behind the sticker panel closes it, the way tapping outside a
                // keyboard closes the keyboard.
                .pointerInput(pickerOpen) {
                    if (pickerOpen) {
                        detectTapGestures { pickerOpen = false }
                    }
                },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // The slim bar's height is exactly the content's top edge: no gap of its own.
                contentPadding = PaddingValues(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + FlTopBarCompactHeight - 8.dp,
                    bottom = 16.dp,
                ),
            ) {
            item {
                AppHero(
                    app = app,
                    packageName = packageName,
                    recommendations = recommendations,
                    onOpenRecommendation = { onOpenUrl(it.url) },
                    selectedSystem = selectedSystem,
                    onSystemChange = onSystemChange,
                    systemsExpanded = systemsExpanded,
                    onSystemsExpandedChange = { systemsExpanded = it },
                    votes = votes,
                    scope = voteScope,
                    onScopeChange = { voteScope = it },
                    profile = profile,
                    mine = mine,
                    voting = voting,
                    voteError = voteError,
                    onVote = castVote,
                    fixes = descriptionFixes,
                    onOpenDescriptionFixes = { onOpenDescriptionFixes(packageName) },
                )
            }
            item { SectionHeading("评论 · ${visibleComments.size}") }
            // The count above is the scoped one; say what it left out, so a short thread does not
            // read as "nobody else wrote anything" when the rest is one tap away.
            if (visibleComments.size < comments.size) {
                item {
                    Text(
                        text = "只看本机：另有 ${comments.size - visibleComments.size} 条来自其他机型或系统版本，切到「全部机型」查看。",
                        style = FlText.caption2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
            when {
                threads.isNotEmpty() -> commentThreads(
                    threads = threads,
                    me = author,
                    expanded = expandedThreads,
                    shownReplies = shownReplies,
                    onShowMoreReplies = { key ->
                        shownReplies = shownReplies + (key to ((shownReplies[key] ?: FIRST_REPLY_BATCH) + NEXT_REPLY_BATCH))
                    },
                    onToggleThread = { key ->
                        if (key in expandedThreads) shownReplies = shownReplies - key
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
                    onHide = { key ->
                        hiddenComments = if (key in hiddenComments) hiddenComments - key else hiddenComments + key
                    },
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
            // The floating title bar, drawn last so it sits over the scroll.
            ScreenTopBar(
                title = app?.name ?: packageName,
                onNavigateBack = onNavigateBack,
                height = FlTopBarCompactHeight,
                actions = {
                    // The glyph plus the count: the number is the reason to open the drawer, and a
                    // bare icon would leave "are there even any plans with this app?" unanswered
                    // until after the tap.
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable { drawerOpen = true }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAddCheck,
                            contentDescription = "包含这个应用的方案",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        if (relatedPlans.isNotEmpty()) {
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "${relatedPlans.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
            // The drawer: a scrim that fades and a panel that slides in from the right, both drawn
            // over the bar — a surface opened by the bar should be able to cover it.
            AnimatedVisibility(
                visible = drawerOpen,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable { drawerOpen = false },
                )
            }
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                RelatedPlansPanel(
                    plans = relatedPlans,
                    loading = relatedLoading,
                    onOpenPlan = { id ->
                        drawerOpen = false
                        onOpenPlan(id)
                    },
                    onClose = { drawerOpen = false },
                )
            }
        }
    }
}

/** The app itself: icon, name, package, version, size, and the two-sided vote about removing it. */
@Composable
private fun AppHero(
    app: AppInfo?,
    packageName: String,
    recommendations: Recommendations?,
    onOpenRecommendation: (RecommendedApp) -> Unit,
    selectedSystem: String,
    onSystemChange: (String) -> Unit,
    systemsExpanded: Boolean,
    onSystemsExpandedChange: (Boolean) -> Unit,
    votes: PackageVotes,
    scope: Int,
    onScopeChange: (Int) -> Unit,
    profile: DeviceProfile?,
    mine: Boolean?,
    voting: Boolean,
    voteError: String? = null,
    onVote: (Boolean) -> Unit,
    /** The community's corrections of this app's description; empty when nobody filed one. */
    fixes: List<DescriptionFix> = emptyList(),
    onOpenDescriptionFixes: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val recommended = remember(recommendations, packageName) {
        recommendations?.apps?.firstOrNull { packageName in it.replacesPackages }
    }
    val appliedFix = remember(fixes) { fixes.firstOrNull { it.applied } }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)) {
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
                }
                if (facts.isNotEmpty()) {
                    Text(
                        text = facts.joinToString(" · "),
                        style = FlText.caption1,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // Whether it is still on the phone is the first thing a reader checks, so it is a state
            // of its own — a coloured dot and a word — not a caption that happens to say "已卸载".
            UninstallStatus(app)
        }

        // What can stand in for this app, as a card of its own: the tile, the name, and a plain
        // "推荐替换" that says what tapping it does. It used to be a bare arrow of text.
        recommended?.let { suggestion ->
            Spacer(Modifier.height(10.dp))
            ReplacementRow(suggestion = suggestion, onOpen = { onOpenRecommendation(suggestion) })
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

        // The accepted correction, when there is one, stands in for the published line: that is the
        // whole point of voting on it. The source is named either way, so the reader knows whose
        // words these are.
        val shownDescription = appliedFix?.body?.takeIf { it.isNotBlank() }
            ?: app?.description?.takeIf { it.isNotBlank() }
        if (shownDescription != null) {
            Spacer(Modifier.height(8.dp))
            io.github.samolego.canta.ui.component.text.UrlText(
                text = shownDescription,
                style = FlText.footnote,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (appliedFix != null) {
                    "社区修正 · 由 ${appliedFix.author.ifEmpty { "匿名" }} 提出 · " +
                        "${appliedFix.agrees}/${appliedFix.votes} 人认同"
                } else {
                    stringResource(
                        if (app?.descriptionTranslated == true) Res.string.description_translated_source
                        else Res.string.description_source
                    )
                },
                style = FlText.caption2,
                color = if (appliedFix != null) scheme.primary else scheme.onSurfaceVariant,
            )
        }

        if (app != null) {
            Spacer(Modifier.height(8.dp))
            DescriptionFixEntry(
                fixCount = fixes.size,
                accepted = appliedFix != null,
                onClick = onOpenDescriptionFixes,
            )
        }

        Spacer(Modifier.height(14.dp))
        VoteBlock(
            votes = votes,
            scope = scope,
            onScopeChange = onScopeChange,
            profile = profile,
            selectedSystem = selectedSystem,
            onSystemChange = onSystemChange,
            systemsExpanded = systemsExpanded,
            onSystemsExpandedChange = onSystemsExpandedChange,
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
    selectedSystem: String,
    onSystemChange: (String) -> Unit,
    systemsExpanded: Boolean,
    onSystemsExpandedChange: (Boolean) -> Unit,
    onScopeChange: (Int) -> Unit,
    profile: DeviceProfile?,
    mine: Boolean?,
    voting: Boolean,
    voteError: String? = null,
    onVote: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val systemVotes = votes.bySystem[selectedSystem]
    val canRemove = when (scope) {
        1 -> votes.allCanRemove
        2 -> systemVotes?.first ?: 0
        else -> votes.canRemove
    }
    val cannotRemove = when (scope) {
        1 -> votes.allCannotRemove
        2 -> systemVotes?.second ?: 0
        else -> votes.cannotRemove
    }
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
            Spacer(Modifier.width(8.dp))
            Box {
                ScopePill(
                    label = if (scope == 2) selectedSystem else "系统版本",
                    selected = scope == 2,
                    onClick = { onSystemsExpandedChange(true) },
                )
                DropdownMenu(
                    expanded = systemsExpanded,
                    onDismissRequest = { onSystemsExpandedChange(false) },
                ) {
                    if (votes.bySystem.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("暂无系统版本数据") },
                            onClick = { onSystemsExpandedChange(false) },
                            enabled = false,
                        )
                    }
                    votes.bySystem.keys.sorted().forEach { system ->
                        DropdownMenuItem(
                            text = { Text(system) },
                            onClick = {
                                onSystemChange(system)
                                onSystemsExpandedChange(false)
                                onScopeChange(2)
                            },
                        )
                    }
                }
            }
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
                append(
                    when (scope) {
                        1 -> "所有机型"
                        // The system list only ever holds this phone's own builds, so name the
                        // phone once here: "HyperOS 3" alone does not say whose HyperOS 3.
                        2 -> (profile?.displayModel.orEmpty().ifBlank { "本机" }) +
                            " · " + selectedSystem.ifBlank { "系统版本" }
                        else -> profile?.shortLabel() ?: "本机"
                    }
                )
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
                label = "可以删",
                color = CAN_REMOVE,
                selected = mine == true,
                enabled = !voting,
                onClick = { onVote(true) },
            )
            Spacer(Modifier.width(10.dp))
            VoteButton(
                label = "不可以删",
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
                else -> "点选你的判断"
            },
            style = FlText.caption2,
            color = if (voteError != null) scheme.error else scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Whether this app is still on the phone, as a state of its own: a coloured dot and a word on a
 * tinted capsule, green while it is there and red once it is gone. A grey "状态未知" stands in while
 * the app list has not been read yet — the page can be opened from a link before it is.
 */
@Composable
private fun UninstallStatus(app: AppInfo?) {
    val scheme = MaterialTheme.colorScheme
    val uninstalled = app?.isUninstalled == true
    val color = when {
        app == null -> scheme.onSurfaceVariant
        uninstalled -> CANNOT_REMOVE
        else -> CAN_REMOVE
    }
    val label = when {
        app == null -> "状态未知"
        uninstalled -> "已卸载"
        else -> "未卸载"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = FlText.caption2.copy(fontWeight = FlText.emphasized),
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * What can replace this app: the catalog entry's own tile, its name, and a plain "推荐替换" with the
 * arrow that says the tap goes somewhere. The catalog ships no images, so the tile is the entry's
 * mark on its accent colour — the same tile the recommendations page draws.
 */
@Composable
private fun ReplacementRow(suggestion: RecommendedApp, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = remember(suggestion.accent, suggestion.id) { recommendationAccent(suggestion) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
            .clickable(onClick = onOpen)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    Brush.linearGradient(
                        listOf(lerp(accent, Color.White, 0.18f), lerp(accent, Color.Black, 0.18f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = suggestion.tileMark,
                color = Color.White,
                fontSize = if (suggestion.tileMark.length > 1) 11.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "推荐替换",
                style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                color = scheme.primary,
                maxLines = 1,
            )
            Text(
                text = suggestion.name,
                style = FlText.footnote.copy(fontWeight = FlText.emphasized),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "看看",
            style = FlText.caption2,
            color = scheme.primary,
            maxLines = 1,
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = scheme.primary,
        )
    }
}

/** The accent the catalog entry names, or the theme's own colour when it names none it can parse. */
private fun recommendationAccent(app: RecommendedApp): Color {
    val hex = app.accent.removePrefix("#").trim()
    if (hex.length == 6) {
        val value = hex.toLongOrNull(16)
        if (value != null) return Color(0xFF000000 or value)
    }
    return Color(0xFF7A6FF0)
}

/**
 * The way into the corrected descriptions: one row under the description itself, with the count of
 * rewrites already filed. It is always here — a description that everyone agrees with still benefits
 * from saying "you can propose a better one".
 */
@Composable
private fun DescriptionFixEntry(fixCount: Int, accepted: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = scheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (fixCount > 0) "描述有误？看 $fixCount 条修正" else "描述有误？写一条修正",
            style = FlText.caption1.copy(fontWeight = FlText.emphasized),
            color = scheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (accepted) {
            Text(
                text = "已有修正生效",
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = scheme.primary,
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
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            FlGlyphIconInline(glyph = FlGlyphs.checkCircle, tint = Color.White)
            Spacer(Modifier.width(6.dp))
        }
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

/**
 * The thread narrowed to the device scope the page is showing.
 *
 * [scope] 1 is every model and keeps the whole thread; [scope] 0 keeps only what this exact phone
 * wrote; 2 keeps this phone on the picked build. A comment stored before the device was kept has no
 * model to compare with, so it stays in — unknown is not the same as another phone. An unknown
 * [model] (the profile was not read yet) also leaves the thread whole rather than emptying it.
 */
private fun scopedToDevice(
    comments: List<PlanComment>,
    scope: Int,
    system: String,
    model: String,
): List<PlanComment> = when {
    scope == 1 || model.isBlank() -> comments
    else -> comments.filter { comment ->
        val sameModel = comment.deviceModel.isBlank() ||
            comment.deviceModel.equals(model, ignoreCase = true)
        sameModel && (
            scope != 2 || system.isBlank() ||
                comment.systemName.equals(system, ignoreCase = true)
            )
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) "${(mb * 10).toInt() / 10.0} MB" else "${bytes / 1024} KB"
}

/**
 * The plans that hold this app, as a drawer over the page.
 *
 * It is a drawer and not a section of the page because the page is read top to bottom for one
 * answer — "can this phone live without it" — and the plans are a *branch* off that answer: what a
 * reader does after a green vote, when they want a whole set somebody already proved. Putting them
 * in the scroll would either bury them under the thread or push the thread down for everyone who
 * came for the votes. One tap on a row opens that plan.
 */
@Composable
private fun RelatedPlansPanel(
    plans: List<CommunityPlan>,
    loading: Boolean,
    onOpenPlan: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.86f)
            .widthIn(max = 380.dp),
        color = scheme.surfaceContainerHigh,
        // Rounded on the side it slides in from: the edge it came out of, not the one it is
        // attached to.
        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The panel reaches the top of the screen on purpose; the status bar's own height
                // is what keeps the title clear of the clock.
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "包含这个应用的方案",
                        style = FlText.subheadline,
                        color = scheme.onSurface,
                    )
                    Text(
                        // What the list is scoped to, said out loud: these plans are this phone's
                        // model and build, so an empty drawer means "nobody here yet", never
                        // "nobody anywhere".
                        text = when {
                            loading -> "正在读这一机型的方案…"
                            plans.isEmpty() -> "本机型还没有这样的方案"
                            else -> "本机型共 ${plans.size} 个"
                        },
                        style = FlText.caption2,
                        color = scheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(16.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
            if (!loading && plans.isEmpty()) {
                Text(
                    text = "还没有人把它写进方案。用「社区」页上传一个精简方案，别人在这个应用的页面上" +
                        "就能看到它被谁去掉过。",
                    style = FlText.caption1,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(plans, key = { it.id }) { plan ->
                    RelatedPlanRow(plan = plan, onOpen = { onOpenPlan(plan.id) })
                }
            }
        }
    }
}

/** One plan in the drawer: its name, and the numbers that say whether it is worth opening. */
@Composable
private fun RelatedPlanRow(plan: CommunityPlan, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = plan.title,
                style = FlText.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = scheme.outline,
            )
        }
        Text(
            // The package count leads: a plan that holds forty packages is a different thing from
            // one that holds five, and that is the number the drawer is compared on.
            text = buildString {
                append("${plan.packages.size} 包 · 点赞 ${plan.votes} · ${plan.comments} 评论")
                val author = plan.authorName
                if (author.isNotEmpty()) append(" · @$author")
            },
            style = FlText.caption2,
            color = scheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
