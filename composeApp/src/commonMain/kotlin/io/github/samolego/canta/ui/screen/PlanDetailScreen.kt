package io.github.samolego.canta.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.PackageRank
import io.github.samolego.canta.data.community.AppNoteStore
import io.github.samolego.canta.data.community.CommentOrder
import io.github.samolego.canta.data.community.DeviceCoverage
import io.github.samolego.canta.data.community.FIRST_REPLY_BATCH
import io.github.samolego.canta.data.community.NEXT_REPLY_BATCH
import io.github.samolego.canta.data.community.NOTE_MAX_LENGTH
import io.github.samolego.canta.data.community.PlanComment
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.inOrder
import io.github.samolego.canta.data.community.peopleSuffix
import io.github.samolego.canta.data.community.threadOf
import io.github.samolego.canta.data.community.withAnchorFirst
import io.github.samolego.canta.data.community.keyed
import io.github.samolego.canta.data.community.rankPackages
import io.github.samolego.canta.data.community.threads
import io.github.samolego.canta.data.community.updatedComment
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.plan_comment_send
import io.github.samolego.canta.generated.resources.plan_detail_missing
import io.github.samolego.canta.generated.resources.plan_detail_no_comments
import io.github.samolego.canta.generated.resources.plan_detail_no_rank
import io.github.samolego.canta.generated.resources.plan_detail_ranking_hint
import io.github.samolego.canta.generated.resources.ranking_marks
import io.github.samolego.canta.generated.resources.plan_detail_ranking_title
import io.github.samolego.canta.generated.resources.plan_detail_stats_comments
import io.github.samolego.canta.generated.resources.plan_detail_stats_packages
import io.github.samolego.canta.generated.resources.plan_detail_stats_votes
import io.github.samolego.canta.generated.resources.plan_detail_title
import io.github.samolego.canta.generated.resources.plan_detail_uploader
import io.github.samolego.canta.generated.resources.plan_test_chip
import io.github.samolego.canta.generated.resources.plan_upload_failed
import io.github.samolego.canta.generated.resources.plan_verified_chip
import io.github.samolego.canta.generated.resources.plan_vote
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.component.CommentOrderRow
import io.github.samolego.canta.ui.component.CommentComposer
import io.github.samolego.canta.ui.component.LetterAvatar
import io.github.samolego.canta.ui.component.PackageSearchField
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.commentThreads
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlTopBarCompactHeight
import io.github.samolego.canta.util.DeviceIdentity
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.SafetyRules
import io.github.samolego.canta.util.SafetyStatus
import io.github.samolego.canta.util.SafetyVerdict
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Icon size of a package row: small enough to fit a plan of twenty packages on one screen. */
private val PACKAGE_ICON = 34.dp

/**
 * The filter's place in the list: the header is 0, the packages' section title is 1, and the field
 * follows it. Opening the filter scrolls to this index.
 */
private const val PACKAGE_FILTER_INDEX = 2

/**
 * One community plan, opened from the plans list — or from a share code somebody pasted.
 *
 * The page answers the question the list only hints at: *does removing these packages work on a
 * phone like mine?* — so the plan's packages are shown in this model's own ranking, each with how
 * many likes it collected on the same model and ROM, next to what the device-aware rules say about
 * it. Nothing here is generic advice: a plan for another build is never served, so every number is
 * about this phone.
 *
 * Plans are only served for the caller's own model and ROM version, so the whole plan has to come
 * back from [PlanRepository.plansFor] instead of a dedicated by-id endpoint: [planId] selects it
 * out of that list, and a plan that is not in it (deleted, or for another build) is reported as
 * missing rather than guessed at.
 *
 * Who uploaded it is part of the answer, so the header shows the uploader's letter avatar next to
 * their number — and the comments below it are a thread: every comment can be answered, this
 * install can delete what it wrote, and the reply count says whether anyone came back.
 */
@Composable
fun PlanDetailScreen(
    planId: Long,
    repository: PlanRepository,
    profile: DeviceProfile?,
    onNavigateBack: () -> Unit,
    author: String = "",
    apps: List<AppInfo> = emptyList(),
    rules: SafetyRules? = null,
    /** Where this install keeps what it liked, so a like is still a like after a restart. */
    notes: AppNoteStore? = null,
    /** This install's identity number: what comments are recorded and deleted under. */
    owner: String = "",
    /** This install's secret, needed to delete a comment it wrote. */
    token: String = "",
    /** Opens one app's own page from a package row. */
    onOpenApp: (String) -> Unit = {},
    /** Selects the plan's packages on the app list and goes there. */
    onUsePlan: (List<String>) -> Unit = {},
    /** Shares this plan: copies its code to the clipboard. */
    onShare: (CommunityPlan) -> Unit = {},
    /**
     * The comment this page was opened on, from the inbox: its thread is put first and opened, so
     * "somebody answered you" leads to the answer rather than to the top of the page.
     */
    anchorCommentId: Long = 0L,
    /** Opens this uploader's own page; null when this install has no identity to open. */
    onOpenUploader: ((String) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()

    var plan by remember { mutableStateOf<CommunityPlan?>(null) }
    var ranking by remember { mutableStateOf<Map<String, PackageRank>>(emptyMap()) }
    var comments by remember { mutableStateOf<List<PlanComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var submitFailed by remember { mutableStateOf(false) }
    // Whether the sticker panel under the bar is open. It lives here rather than inside the bar
    // because the page is what knows a tap landed outside it.
    var pickerOpen by remember { mutableStateOf(false) }
    /** Why the last comment did not go through: the backend's own words, not "网络问题". */
    var sendError by remember { mutableStateOf<String?>(null) }
    /** Why the thread is empty, when it is empty because the call failed. */
    var loadError by remember { mutableStateOf<String?>(null) }
    var liked by remember { mutableStateOf(false) }
    /** The comment being answered; null while the composer writes a new one. */
    var replyTo by remember { mutableStateOf<PlanComment?>(null) }
    /** The comment the delete confirmation is about. */
    var pendingDelete by remember { mutableStateOf<PlanComment?>(null) }
    /** Threads the reader opened with "展开 N 条回复", by their own key; see [threadKey]. */
    var expandedThreads by remember { mutableStateOf(setOf<Long>()) }
    /** How many answers each open thread shows; absent means the first batch. */
    var shownReplies by remember { mutableStateOf(mapOf<Long, Int>()) }
    /**
     * Comments this install put away with the broken heart: a personal hide, this session only —
     * nothing on the server changes, and the page forgets it on the way out.
     */
    var hiddenComments by remember { mutableStateOf(setOf<Long>()) }
    /** The "use this plan" tap that has to be confirmed, because the plan is for another phone. */
    var confirmForeign by remember { mutableStateOf(false) }
    /**
     * How the comment threads are ordered. A reading preference, not a stored one: the page opens
     * on newest every time, because that is what "what did people say about this plan" means.
     */
    var order by remember { mutableStateOf(CommentOrder.NEWEST) }
    /** Whether the uploader is rewriting the plan's note; see [onEditNote]. */
    var editingNote by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }
    /**
     * The other phones this exact package set was reboot-proven on. Loaded with the plan, so the hero
     * can say "and four other builds" instead of only naming the uploader's.
     */
    var coverage by remember { mutableStateOf<List<DeviceCoverage>>(emptyList()) }
    /**
     * The package filter over the plan's own list. A plan can carry over a hundred packages, and
     * "is `com.miui.daemon` even in this one?" is the question the list is read for — the field
     * narrows the rows as it is typed, in the ranking's own order.
     */
    var searchOpen by remember(planId) { mutableStateOf(false) }
    var query by remember(planId) { mutableStateOf("") }
    /** The scroll of the page's list: opening the filter scrolls the packages into view. */
    val listState = rememberLazyListState()
    /**
     * True once the hero has scrolled away. The bar then shows the plan's own name instead of the
     * generic page title: a hundred package rows later, "方案详情" no longer says which plan this
     * is, and the reader who forgot has to scroll all the way back up to find out.
     */
    val heroScrolledAway by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    // The install's own code (see DeviceIdentity): one stable name per phone, no account.
    val commentAuthor = author.ifEmpty { "u-anon" }

    /** Where this page's unsent comment is kept; see [AppNoteStore.draftOf]. */
    val draftKey = "plan:$planId"

    LaunchedEffect(planId) {
        // A plan voted for before opening the page is already liked: the device remembers,
        // because `plan_vote` never says who voted.
        notes?.ensureReady()
        liked = notes?.likedPlans?.value?.contains(planId) == true
        // Something half-typed last time comes back rather than being lost: leaving the page to
        // check a package name is exactly when a comment gets interrupted.
        draft = notes?.draftOf(draftKey) ?: ""
    }

    // Written back as it is typed, not on the way out: leaving through the system back gesture,
    // a call, or the app being killed all end the page without running anything of its own.
    LaunchedEffect(draft, draftKey) {
        notes?.setDraft(draftKey, draft)
    }

    LaunchedEffect(planId, profile) {
        // Keep the already loaded plan on screen while a late profile reloads it.
        if (plan == null) loading = true
        val all = repository.plansFor(profile)
        plan = all.firstOrNull { it.id == planId }
        // Opened from a comment or a share code: the plan may belong to another model, or be one
        // this phone's list does not carry. Ask for that one plan by id before calling it missing.
        if (plan == null) plan = repository.planById(planId)
        ranking = rankPackages(all).keyed()
        // The viewer is sent with the read, not only with the tap: `liked` is a per-identity answer,
        // and without it every heart on the thread comes back empty and a like looks forgotten.
        comments = repository.comments(planId, owner)
        loadError = if (comments.isEmpty()) repository.lastError else null
        loading = false
    }

    val reloadComments: suspend () -> Unit = { comments = repository.comments(planId, owner) }

    // Asked once the plan is known, because the question is about its package set. A backend that
    // predates the call answers null and the list stays empty: the page then says what it always
    // said, the uploader's own device.
    LaunchedEffect(plan?.packages) {
        val packages = plan?.packages.orEmpty()
        coverage = if (packages.isEmpty()) emptyList() else repository.deviceCoverage(packages)
    }

    val sendComment: () -> Unit = {
        val body = draft.trim()
        if (body.isNotEmpty() && !sending) {
            scope.launch {
                sending = true
                val posted = repository.commentOnPlan(
                    planId = planId,
                    body = body,
                    author = commentAuthor,
                    authorId = owner,
                    replyTo = replyTo?.id ?: 0L,
                    deviceModel = profile?.displayModel.orEmpty(),
                    systemName = profile?.systemLabel().orEmpty(),
                    // Only an older backend needs the name: it stores no parent id, so the answer
                    // goes in as text that starts with who it answers.
                    replyToName = replyTo?.author.orEmpty(),
                )
                submitFailed = posted == null
                sendError = if (submitFailed) {
                    repository.lastError?.let { "发送失败：$it" } ?: "发送失败，检查一下网络再试"
                } else {
                    null
                }
                if (!submitFailed) {
                    draft = ""
                    replyTo = null
                    reloadComments()
                }
                sending = false
            }
        }
    }

    val deleteComment: (PlanComment) -> Unit = { comment ->
        pendingDelete = comment
    }

    val hideComment: (Long) -> Unit = { key ->
        hiddenComments = if (key in hiddenComments) hiddenComments - key else hiddenComments + key
    }

    /**
     * The uploader rewriting the note: the one part of a plan that a week of use can improve, and
     * the only alternative was deleting the plan and losing its votes and comments with it.
     */
    val saveNote: (String) -> Unit = { text ->
        val current = plan
        scope.launch {
            if (current == null) return@launch
            editingNote = false
            val ok = repository.setNote(
                planId = planId,
                // The plan's own owner, or this install's identity on a backend that sends none.
                owner = current.owner.ifEmpty { owner },
                token = token,
                note = text.trim(),
            )
            if (ok) {
                plan = current.copy(note = text.trim())
                noteDraft = text.trim()
            } else {
                // The composer's own error line is the page's one place for "that call did not go
                // through", so a failed note is said there rather than in a second place.
                submitFailed = true
                sendError = "说明没存上：${repository.lastError ?: "检查一下网络再试"}"
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
                val result = repository.likeComment(comment.id, owner, wanted)
                if (result != null) {
                    comments = comments.updatedComment(comment.id) {
                        it.copy(liked = result.liked, likes = result.likes)
                    }
                }
            }
        }
    }

    val vote: () -> Unit = {
        val current = plan
        if (current != null && !liked) {
            liked = true
            scope.launch {
                notes?.markPlanLiked(current.id)
                val votes = repository.vote(current.id, commentAuthor) ?: return@launch
                plan = current.copy(votes = votes)
            }
        }
    }

    if (editingNote) {
        CantaDialog(
            onDismissRequest = { editingNote = false },
            title = "改说明",
            buttons = {
                TextButton(onClick = { editingNote = false }) { Text("取消") }
                TextButton(onClick = { saveNote(noteDraft) }) { Text("保存") }
            },
        ) {
            Text(
                text = "这一句会显示在方案卡片上，读的人先看到标题，再看到它。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = noteDraft,
                onValueChange = { noteDraft = it.take(NOTE_MAX_LENGTH) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 6,
                decorationBox = { inner ->
                    if (noteDraft.isEmpty()) {
                        Text(
                            text = "比如：这几个包在这台机器上会一起把桌面图标删掉，重启前先把桌面换回来。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    inner()
                },
            )
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
                        repository.deleteComment(comment.id, owner, token)
                        reloadComments()
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
        // The title bar is not a Scaffold topBar: it floats over the content as a transparent
        // layer, so what scrolls up under it stays readable through it.
        bottomBar = {
            // The composer gives way to the search: a filter and a comment box are two different
            // things to do with the same page, and the bar at the foot can only be one of them. A
            // slim "评论" entry takes its place, so the way back is one tap and always visible.
            if (searchOpen) {
                CommentTabBar(
                    count = comments.size,
                    onOpen = {
                        searchOpen = false
                        query = ""
                    },
                )
            } else {
                CommentComposer(
                    draft = draft,
                    onDraftChange = { changed ->
                        draft = changed
                        if (submitFailed) submitFailed = false
                    },
                    onSend = sendComment,
                    replyToName = replyTo?.author.orEmpty(),
                    onCancelReply = { replyTo = null },
                    sending = sending,
                    failed = submitFailed,
                    sendLabel = stringResource(Res.string.plan_comment_send),
                    failLabel = sendError ?: stringResource(Res.string.plan_upload_failed),
                    pickerOpen = pickerOpen,
                    onPickerOpenChange = { pickerOpen = it },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                // A tap on the page behind the panel closes it, the way tapping outside a keyboard
                // closes the keyboard: the panel is a temporary surface, and the way out of it
                // should not be the key that opened it.
                .pointerInput(pickerOpen) {
                    if (pickerOpen) {
                        detectTapGestures { pickerOpen = false }
                    }
                },
        ) {
            val loaded = plan
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    // "…" alone gave no sign the page was working: the plan is a backend call and
                    // on a slow proxy it takes a while, so say what is being waited for.
                    Text(
                        text = "正在读取方案…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                loaded == null -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.plan_detail_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> PlanDetailContent(
                    plan = loaded,
                    ranking = ranking,
                    apps = apps,
                    rules = rules,
                    profile = profile,
                    coverage = coverage,
                    query = query,
                    searchOpen = searchOpen,
                    listState = listState,
                    onQueryChange = { query = it },
                    // Closing drops the filter with the field: a narrowed list with nothing on
                    // screen saying why it is narrow is the one state this page must not keep.
                    onSearchClose = {
                        searchOpen = false
                        query = ""
                    },
                    onOpenUploader = onOpenUploader,
                    comments = comments,
                    loadError = loadError,
                    me = commentAuthor,
                    liked = liked,
                    expandedThreads = expandedThreads,
                    shownReplies = shownReplies,
                    // One more batch, ten at a time; `take` on the thread's own list is what keeps
                    // the count from running past the end, so it needs no arithmetic here.
                    onShowMoreReplies = { key ->
                        shownReplies = shownReplies + (key to ((shownReplies[key] ?: FIRST_REPLY_BATCH) + NEXT_REPLY_BATCH))
                    },
                    onToggleThread = { key ->
                        // Closing drops the thread's place in the batches, so opening it again
                        // starts from the first three rather than from wherever it was left.
                        if (key in expandedThreads) shownReplies = shownReplies - key
                        expandedThreads = if (key in expandedThreads) expandedThreads - key else expandedThreads + key
                    },
                    onVote = vote,
                    onOpenApp = onOpenApp,
                    foreign = !loaded.matches(profile),
                    // Another model's plan is applied the same way — Canta does not block it — but the
                    // tap asks first, because nothing about it was proven on this phone.
                    onUsePlan = {
                        if (loaded.matches(profile)) onUsePlan(loaded.packages) else confirmForeign = true
                    },
                    onReply = { comment ->
                        replyTo = comment
                        submitFailed = false
                    },
                    onDeleteComment = deleteComment,
                    onLikeComment = likeComment,
                    hiddenComments = hiddenComments,
                    onHideComment = hideComment,
                    order = order,
                    onOrderChange = { order = it },
                    anchorCommentId = anchorCommentId,
                    onEditNote = { currentNote ->
                        noteDraft = currentNote
                        editingNote = true
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // The floating title bar, drawn last so it sits over the scroll: a layer of nothing —
            // no background of its own — with the page's name and its way out.
            ScreenTopBar(
                onNavigateBack = onNavigateBack,
                // The plan's name takes over once the hero is gone; see [heroScrolledAway].
                title = if (heroScrolledAway) {
                    plan?.title.orEmpty().ifBlank { stringResource(Res.string.plan_detail_title) }
                } else {
                    stringResource(Res.string.plan_detail_title)
                },
                height = FlTopBarCompactHeight,
                actions = {
                    plan?.let { current ->
                        IconButton(
                            onClick = {
                                searchOpen = !searchOpen
                                if (searchOpen) {
                                    // The sticker panel belongs to the composer, which steps out
                                    // while the filter is open: leaving it up would put a grid of
                                    // stickers under a box the reader is not writing in.
                                    pickerOpen = false
                                } else {
                                    query = ""
                                }
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索方案里的包",
                                modifier = Modifier.size(17.dp),
                                tint = if (searchOpen) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        // The share glyph, not the clipboard one: what the button does is hand the
                        // plan to somebody else, and only the mechanism is a copy.
                        IconButton(onClick = { onShare(current) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "分享方案",
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        }
    }

    // The one place a plan for another phone is turned into a selection on this one: say what will
    // happen, then do exactly that. The wording states the limit — nothing here blocks an uninstall.
    if (confirmForeign) {
        val planDevice = plan?.deviceLabel.orEmpty().ifBlank { "未知机型" }
        val thisDevice = profile?.shortLabel() ?: "-"
        CantaDialog(
            onDismissRequest = { confirmForeign = false },
            title = "机型不符",
            buttons = {
                TextButton(onClick = { confirmForeign = false }) { Text("取消") }
                Button(onClick = {
                    confirmForeign = false
                    plan?.let { onUsePlan(it.packages) }
                }) { Text("仍然使用") }
            },
        ) {
            Text(
                text = "这套方案来自「$planDevice」，本机是「$thisDevice」。\n\n" +
                    "使用不会被拦截：Canta 只把方案里的包选上，不会因为机型不同而阻止卸载，" +
                    "也不会替你校验这些包在这台手机上安不安全。",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Header, the plan's packages *in this model's ranking*, and the comment thread. */
@Composable
private fun PlanDetailContent(
    plan: CommunityPlan,
    ranking: Map<String, PackageRank>,
    apps: List<AppInfo>,
    rules: SafetyRules?,
    profile: DeviceProfile?,
    /** True when the plan was proven on another model or build: the page says so, and asks first. */
    foreign: Boolean = false,
    /** The other phones this package set was proven on, most-proven first. */
    coverage: List<DeviceCoverage> = emptyList(),
    /** The package filter typed above the rows, and the handle that scrolls it into view. */
    query: String = "",
    /** True while the filter field is in the list, above the package rows. */
    searchOpen: Boolean = false,
    /** The page's own scroll, so opening the filter can bring it on screen wherever the reader is. */
    listState: LazyListState = rememberLazyListState(),
    onQueryChange: (String) -> Unit = {},
    onSearchClose: () -> Unit = {},
    /** Opens this uploader's own page; null when there is no identity to open. */
    onOpenUploader: ((String) -> Unit)? = null,
    comments: List<PlanComment>,
    loadError: String? = null,
    me: String,
    liked: Boolean,
    /** The comment threads the reader opened; the rest keep their answers behind "展开 N 条回复". */
    expandedThreads: Set<Long> = emptySet(),
    onToggleThread: (Long) -> Unit = {},
    /** How many answers each open thread shows; absent means the first batch. */
    shownReplies: Map<Long, Int> = emptyMap(),
    onShowMoreReplies: (Long) -> Unit = {},
    onVote: () -> Unit,
    onOpenApp: (String) -> Unit,
    onUsePlan: () -> Unit,
    onReply: (PlanComment) -> Unit,
    onDeleteComment: (PlanComment) -> Unit,
    onLikeComment: (PlanComment) -> Unit = {},
    /** Hide keys of the comments this install put away with the broken heart, this session only. */
    hiddenComments: Set<Long> = emptySet(),
    onHideComment: (Long) -> Unit = {},
    /** How the threads are ordered; see [CommentOrder]. */
    order: CommentOrder = CommentOrder.NEWEST,
    onOrderChange: (CommentOrder) -> Unit = {},
    /**
     * The comment this page was opened on, from a mention or a reply in the inbox. Its thread is put
     * first and opened, so the row the reader came for is the first thing under the title.
     */
    anchorCommentId: Long = 0L,
    /** The uploader's tap on "补一句说明 / 改说明"; null on a plan this install did not upload. */
    onEditNote: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    // What this phone no longer has, the same answer the uninstall ranking paints red: a plan is a
    // list of things to remove, so the rows that already went have to be told apart on the page too.
    val removedPkgs = remember(apps) {
        apps.filter { it.isUninstalled }.mapTo(mutableSetOf()) { it.packageName }
    }
    val needle = query.trim()
    // Ranking order, not upload order: the point of the section is where each package stands here.
    // The filter narrows that order without re-sorting it: a filtered ranking is still a ranking.
    val ranked = remember(plan.packages, ranking, needle) {
        val ordered = plan.packages
            .map { pkg -> pkg to ranking[pkg] }
            .sortedWith(compareBy(nullsLast()) { (_, rank) -> rank?.rank })
        if (needle.isEmpty()) {
            ordered
        } else {
            ordered.filter { (pkg, _) -> pkg.contains(needle, ignoreCase = true) }
        }
    }
    val threads = remember(comments, order, anchorCommentId) {
        comments.threads().inOrder(order).withAnchorFirst(anchorCommentId)
    }
    // 从互动消息 / 我的评论点进来的那一条：它的线程由 [withAnchorFirst] 排到最前，这里负责
    // 把它的回复展开，并把整页滚到评论那一段——一百多个包的行会把评论压在屏幕外好几屏，
    // 只排到最前而不滚过去，等于什么都没做。
    //
    // effect 的 key 里带 [threads]：首帧评论还没到，`threads` 是空的，只在 [anchorCommentId]
    // 上跑一次的话永远找不到那一条（评论到货时 key 没变，effect 不会重跑），折叠也就一直合着。
    //
    // 滚动是**一次性**的（[anchorScrolled]）：[commentSectionIndex] 也在 key 里，因为包列表的
    // 排序会晚一步到货、索引会变；但要是每变一次就滚一次，之后打字过滤包、开关搜索框都会把页面
    // 拽回评论区，跟「滚到包过滤框」那个 effect 抢滚动。
    var anchorOpened by remember(anchorCommentId) { mutableStateOf(false) }
    var anchorScrolled by remember(anchorCommentId) { mutableStateOf(false) }
    // 评论头之前可能有这几块：hero(1)、段标题(1)、包过滤框(0/1)、过滤没命中的那行(0/1)、包行。
    val filterMissed = needle.isNotEmpty() && ranked.isEmpty()
    val commentSectionIndex = 2 + (if (searchOpen) 1 else 0) + (if (filterMissed) 1 else 0) + ranked.size
    LaunchedEffect(anchorCommentId, threads, commentSectionIndex) {
        if (anchorCommentId <= 0L) return@LaunchedEffect
        if (!anchorOpened) {
            threads.threadOf(anchorCommentId)?.let { anchored ->
                anchorOpened = true
                // 「展开」只能按 threadKey 交回去（线程开合在页面那一层），所以先补一次展开。
                if (anchored.replies.isNotEmpty() && anchored.comment.id !in expandedThreads) {
                    onToggleThread(anchored.comment.id)
                }
            }
        }
        // 评论那一段的头一行（「评论 · N」+ 换序）就是落点：排在最前的正是读者那条。
        if (anchorOpened && !anchorScrolled) {
            anchorScrolled = true
            listState.scrollToItem(commentSectionIndex.coerceAtLeast(0))
        }
    }
    // The uploader's current name (or the identity number before names were synced), in the form the
    // rest of the app shows: the plan card and this header must read the same.
    val uploaderName = plan.authorName
    // The content starts below the floating title bar but scrolls under it: this is where the bar
    // ends, not a wall it draws on.
    // The slim floating bar's height is exactly the content's top edge: the card starts where the
    // bar ends, with no gap to spare.
    val topSpace = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        FlTopBarCompactHeight

    // Opening the filter brings it on screen: it lives in the list above the rows, so a reader who
    // is halfway down the page would otherwise tap the glyph and see nothing happen.
    LaunchedEffect(searchOpen) {
        if (searchOpen) listState.animateScrollToItem(PACKAGE_FILTER_INDEX)
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(top = topSpace, bottom = 16.dp),
    ) {
        item { PlanHero(plan, uploaderName, me, liked, foreign, onVote, onUsePlan, coverage = coverage, onOpenUploader = onOpenUploader, onEditNote = onEditNote) }

        item {
            SectionTitle(
                title = stringResource(Res.string.plan_detail_ranking_title),
                // With a filter on, the subtitle is the answer it just gave: how many of the plan's
                // packages survived the needle, so an empty stretch of page still says why.
                subtitle = if (needle.isEmpty()) {
                    stringResource(
                        Res.string.plan_detail_ranking_hint,
                        profile?.displayModel ?: "-",
                    )
                } else {
                    "${ranked.size}/${plan.packages.size} 个包匹配「$needle」"
                },
            )
        }
        if (searchOpen) {
            // Above the rows, where the packages are: the filter is about them, so it sits with
            // them instead of floating over the plan's header.
            item(key = "package-filter") {
                PackageSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onClose = onSearchClose,
                )
            }
        }
        if (needle.isNotEmpty() && ranked.isEmpty()) {
            item(key = "package-filter-empty") {
                Text(
                    text = "这个方案里没有包含「$needle」的包。清掉搜索框里的字看全部 ${plan.packages.size} 个。",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(ranked, key = { pair -> pair.first }) { (pkg, rank) ->
            PackageRankRow(
                pkg = pkg,
                rank = rank,
                app = byPackage[pkg],
                verdict = rules?.verdict(pkg, profile),
                removed = pkg in removedPkgs,
                onClick = { onOpenApp(pkg) },
            )
        }

        // The count and the order switcher share one line: both answer "what am I looking at",
        // and two stacked headers above the same list cost a whole row for nothing.
        item(key = "comment-order") {
            CommentSectionHeader(
                count = comments.size,
                order = order,
                onOrderChange = onOrderChange,
                showOrder = comments.isNotEmpty(),
            )
        }
        if (comments.isEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        text = stringResource(Res.string.plan_detail_no_comments),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // "no comments" and "the call failed" are different things, and the second one
                    // is what a broken backend looks like.
                    loadError?.let { reason ->
                        Text(
                            text = "后端没有返回评论：$reason",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
        commentThreads(
            threads = threads,
            me = me,
            // The uploader's own comments carry the red "作者" badge, matched on the identity
            // number; their number-shaped name is the fallback for rows written before it existed.
            authorId = plan.owner,
            authorName = uploaderName,
            expanded = expandedThreads,
            shownReplies = shownReplies,
            onShowMoreReplies = onShowMoreReplies,
            onToggleThread = onToggleThread,
            onReply = onReply,
            onDelete = onDeleteComment,
            onLike = onLikeComment,
            hidden = hiddenComments,
            onHide = onHideComment,
        )
    }
}

/**
 * What the plan is: who uploaded it, the title, whether it was reboot-proven, the device the plan
 * was proven on — the uploader's, never the reader's — and the three numbers people judge plans by.
 *
 * The card reads like an editorial header, not a poster: a strict left edge carries everything —
 * who wrote it on top as a quiet eyebrow, the title as the loudest line under it, then the chips,
 * the note, and the numbers. Nothing is centred, because centred type in a card this full turns
 * every line into its own little island and buries the order the reader needs.
 */
@Composable
private fun PlanHero(
    plan: CommunityPlan,
    uploaderName: String,
    me: String,
    liked: Boolean,
    foreign: Boolean,
    onVote: () -> Unit,
    onUsePlan: () -> Unit,
    /** The other phones this package set was proven on, most-proven first; empty when unknown. */
    coverage: List<DeviceCoverage> = emptyList(),
    /** Opens this uploader's own page; null when there is no identity to open. */
    onOpenUploader: ((String) -> Unit)? = null,
    /** Only offered to the uploader: rewriting somebody else's note is what [saveNote] refuses.
     *  Handed the note as it stands, so the editor opens with the current text in it. */
    onEditNote: ((String) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val mine = plan.owner.isNotEmpty() && me.isNotEmpty() && plan.owner == me
    // The block is a card, not a band: it keeps a margin from the page edge and from the bar above,
    // so the top of the page stays the plain surface the floating title bar sits over, and its
    // corners are rounded like every other card in the app.
    Surface(
        color = colors.primaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
        ) {
            // Who wrote it: a plan is advice from a person on the same phone, and the avatar is the
            // first character of that person's name, like everywhere else. The name leads the line;
            // the badge, if there is one, stands at the end of it. Tapping it opens the uploader's
            // other plans, which is the next question after one of them worked.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (onOpenUploader != null) {
                            Modifier.clickable { onOpenUploader(plan.owner) }
                        } else {
                            Modifier
                        }
                    ),
            ) {
                LetterAvatar(
                    name = uploaderName,
                    size = 20.dp,
                    background = colors.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.plan_detail_uploader, uploaderName),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // The date rides the uploader's line instead of owning one: on the card's own
                // surface a standalone "2026-11-21" cost a whole row and read like a second title.
                // Hidden when the field is empty: a spacer plus an empty text is a blank gap that
                // looks like the card forgot to load something.
                if (plan.rebootedAt.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = plan.rebootedAt.take(10),
                        fontSize = 9.sp,
                        color = colors.onPrimaryContainer.copy(alpha = 0.65f),
                        maxLines = 1,
                    )
                }
                if (mine) {
                    Spacer(Modifier.width(6.dp))
                    Chip(label = "我的方案", color = colors.primary)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = plan.title,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(
                    label = if (plan.isTest) {
                        stringResource(Res.string.plan_test_chip)
                    } else {
                        stringResource(Res.string.plan_verified_chip)
                    },
                    color = if (plan.isTest) colors.tertiary else colors.primary,
                )
                Spacer(Modifier.width(8.dp))
                // The phone the verdict belongs to is the uploader's, not this one: the reboot check
                // passed on a real device someone else held, and the reader still needs to know
                // whether that device is theirs.
                Text(
                    text = plan.deviceLabel.ifBlank { "上传者机型未知" },
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            // The plan names the phone it was proven on. When that is not this one, the page says
            // it twice: once as the mismatch, once as what will happen (nothing will stop it).
            if (foreign) {
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .background(colors.error.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        // The phone is already named on the chip line right above; repeating it
                        // here made the warning read as a second device line instead of a warning.
                        text = "这不是本机机型的方案",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.error,
                    )
                    Text(
                        text = "使用不会被拦截：Canta 只按你选中的包执行，不会因为机型不同而停下。",
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            if (plan.note.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = plan.note,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = colors.onPrimaryContainer,
                )
            }
            // The other phones, when there are any: one device line says who wrote this, this says
            // whether anybody else lived with it. Only shown past the uploader's own row, so a plan
            // nobody else has tried stays as quiet as before.
            if (coverage.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "也在这些机器上验过",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                )
                coverage.forEach { device ->
                    Text(
                        text = device.buildLabel +
                            // Same line as the model chips: a count that is not a crowd (see
                            // peopleSuffix) is noise on a line that is already one phone long.
                            peopleSuffix(device.verified),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = colors.onPrimaryContainer.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            // The uploader's own tap: a note written a week after the plan, once use has shown what
            // the upload could not. Shown even with an empty note — that is the case it is for.
            if (mine && onEditNote != null) {
                val edit = onEditNote
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (plan.note.isEmpty()) "补一句说明" else "改说明",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.onPrimaryContainer.copy(alpha = 0.10f))
                        .clickable { edit(plan.note) }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stat(
                    value = "${plan.votes}",
                    label = stringResource(Res.string.plan_detail_stats_votes),
                    modifier = Modifier.weight(1f),
                )
                StatDivider()
                Stat(
                    value = "${plan.packages.size}",
                    label = stringResource(Res.string.plan_detail_stats_packages),
                    modifier = Modifier.weight(1f),
                )
                StatDivider()
                Stat(
                    value = "${plan.comments}",
                    label = stringResource(Res.string.plan_detail_stats_comments),
                    modifier = Modifier.weight(1f),
                )
                VoteAction(liked = liked, votes = plan.votes, onVote = onVote)
            }
            // One tap from reading a plan to acting on it: its packages become the
            // selection on the app list, where the uninstall button already is.
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.primary)
                    .clickable(onClick = onUsePlan)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistAddCheck,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = colors.onPrimary,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "使用这套方案（${plan.packages.size} 个包）",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimary,
                )
            }
        }
    }
}

/**
 * One big number over its label, left-aligned on the card's shared left edge — the way an
 * editorial stat line reads, top to bottom, rather than a row of little centred islands.
 */
@Composable
private fun Stat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onPrimaryContainer,
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = colors.onPrimaryContainer.copy(alpha = 0.72f),
        )
    }
}

/** A hairline between two stats, as tall as the numbers it separates. */
@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .height(22.dp)
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f)),
    )
}

/** The like button of the detail page: filled once this install voted, springing when tapped. */
@Composable
private fun VoteAction(liked: Boolean, votes: Int, onVote: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val colors = MaterialTheme.colorScheme
    val container = if (liked) colors.primary else colors.surface
    val content = if (liked) colors.onPrimary else colors.primary
    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable {
                scope.launch {
                    scale.animateTo(1.3f, spring(stiffness = 900f))
                    scale.animateTo(1f, spring(stiffness = 420f))
                }
                onVote()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.ThumbUp,
            contentDescription = stringResource(Res.string.plan_vote),
            modifier = Modifier.size(13.dp),
            tint = content,
        )
        Spacer(Modifier.width(5.dp))
        Text(text = stringResource(Res.string.plan_vote), fontSize = 11.sp, color = content)
    }
}

/** A small label: "测试", "重启验证", "我的方案". */
@Composable
private fun Chip(label: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = label,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * One package of the plan as this model ranks it: its position here, what it is, and how many
 * likes the same package collected on phones of this exact model and ROM.
 */
@Composable
private fun PackageRankRow(
    pkg: String,
    rank: PackageRank?,
    app: AppInfo?,
    verdict: SafetyVerdict?,
    /** True when this phone no longer has the package: its package line is painted red. */
    removed: Boolean = false,
    onClick: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    // Only the package line turns red, exactly as the uninstall ranking does it: the app's name is
    // how the row is read, while the package name is the thing that actually went.
    val goneColor = colors.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The position is the whole point of the section, so it leads the row.
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    when (rank?.rank) {
                        1 -> colors.primary
                        2 -> colors.tertiary
                        3 -> colors.secondary
                        else -> colors.surfaceContainerHighest
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rank?.let { "${it.rank}" } ?: "—",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = when (rank?.rank) {
                    1 -> colors.onPrimary
                    2 -> colors.onTertiary
                    3 -> colors.onSecondary
                    else -> colors.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        if (app != null) AppIconImage(app, size = PACKAGE_ICON) else Spacer(Modifier.width(PACKAGE_ICON))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app?.name ?: pkg,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pkg,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (removed) goneColor else colors.onSurfaceVariant,
            )
        }
        // One number, one line, the wording the ranking page uses: how many people marked it.
        // How many plans it was marked in is a detail — the package's own page has room for it.
        // Only when it is ranked here: "Not ranked" beside a grey "未知" chip was two dim labels
        // stacked in the spot where the number belongs.
        rank?.let { marked ->
            Text(
                text = stringResource(Res.string.ranking_marks, marked.votes),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = colors.onSurface,
            )
            Spacer(Modifier.width(6.dp))
        }
        SafetyChip(verdict)
        // The row opens the package's own page; a chevron says that without a word of copy.
        Spacer(Modifier.width(3.dp))
        FlGlyphIcon(glyph = FlGlyphs.chevronForward, size = 12.dp, tint = colors.outline)
    }
}

@Composable
private fun SafetyChip(verdict: SafetyVerdict?) {
    val (label, color) = when (verdict?.status) {
        SafetyStatus.CRITICAL -> "禁区" to MaterialTheme.colorScheme.error
        SafetyStatus.RISKY -> "有代价" to MaterialTheme.colorScheme.tertiary
        SafetyStatus.SAFE -> "可卸" to MaterialTheme.colorScheme.primary
        null -> "未知" to MaterialTheme.colorScheme.outline
    }
    Text(
        text = label,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/**
 * The comment section's one header: how many there are on the left, in the same voice as every
 * other section title, and the order switcher on the right. The switcher is dropped while there
 * are no comments — an ordering control over an empty list is a dead control.
 */
@Composable
private fun CommentSectionHeader(
    count: Int,
    order: CommentOrder,
    onOrderChange: (CommentOrder) -> Unit,
    showOrder: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "共 $count 条评论",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            if (showOrder) CommentOrderRow(order = order, onOrderChange = onOrderChange, inline = true)
        }
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
}

/** A section heading over its own one-line explanation. */
@Composable
private fun SectionTitle(title: String, subtitle: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * What the foot of the page shows while the package filter is open: a slim entry to the comments.
 *
 * The composer is the bar the page normally ends with, but a filter and a comment box are two
 * different things to do on the same page and only one of them can own the foot — a keyboard popping
 * up under a field that was just opened to type a package name is the wrong half of the page
 * fighting for the caret. So the foot becomes this: the thread's own count, and one tap that closes
 * the filter and brings the composer back.
 */
@Composable
private fun CommentTabBar(count: Int, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The bar clears the navigation bar itself: the composer does, and this stands in
                // for the composer.
                .padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.surfaceContainerHighest)
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (count > 0) "评论 · $count 条" else "评论",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "写评论",
                modifier = Modifier.size(16.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}
