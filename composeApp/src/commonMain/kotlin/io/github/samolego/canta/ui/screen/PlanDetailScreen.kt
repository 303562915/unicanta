package io.github.samolego.canta.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.PackageRank
import io.github.samolego.canta.data.community.PlanComment
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.keyed
import io.github.samolego.canta.data.community.rankPackages
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.plan_comment_hint
import io.github.samolego.canta.generated.resources.plan_comment_send
import io.github.samolego.canta.generated.resources.plan_comments_title
import io.github.samolego.canta.generated.resources.plan_detail_missing
import io.github.samolego.canta.generated.resources.plan_detail_no_comments
import io.github.samolego.canta.generated.resources.plan_detail_no_rank
import io.github.samolego.canta.generated.resources.plan_detail_ranking_hint
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
import io.github.samolego.canta.generated.resources.ranking_plans
import io.github.samolego.canta.generated.resources.ranking_votes
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.SafetyRules
import io.github.samolego.canta.util.SafetyStatus
import io.github.samolego.canta.util.SafetyVerdict
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Icon size of a package row: small enough to fit a plan of twenty packages on one screen. */
private val PACKAGE_ICON = 34.dp

/**
 * One community plan, opened from the plans list.
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
 * The comment field posts as an anonymous per-install handle, so a thread keeps one identity
 * without asking for an account.
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
) {
    val scope = rememberCoroutineScope()

    var plan by remember { mutableStateOf<CommunityPlan?>(null) }
    var ranking by remember { mutableStateOf<Map<String, PackageRank>>(emptyMap()) }
    var comments by remember { mutableStateOf<List<PlanComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var submitFailed by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(false) }

    // The install's own code (see DeviceIdentity): one stable name per phone, no account.
    val commentAuthor = author.ifEmpty { "u-anon" }

    LaunchedEffect(planId, profile) {
        // Keep the already loaded plan on screen while a late profile reloads it.
        if (plan == null) loading = true
        val all = repository.plansFor(profile)
        plan = all.firstOrNull { it.id == planId }
        ranking = rankPackages(all).keyed()
        comments = repository.comments(planId)
        loading = false
    }

    val sendComment: () -> Unit = {
        val body = draft.trim()
        if (body.isNotEmpty() && !sending) {
            scope.launch {
                sending = true
                submitFailed = !repository.comment(planId, body, commentAuthor)
                if (!submitFailed) {
                    draft = ""
                    comments = repository.comments(planId)
                }
                sending = false
            }
        }
    }

    val vote: () -> Unit = {
        val current = plan
        if (current != null && !liked) {
            liked = true
            scope.launch {
                val votes = repository.vote(current.id, commentAuthor) ?: return@launch
                plan = current.copy(votes = votes)
            }
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                onNavigateBack = onNavigateBack,
                title = stringResource(Res.string.plan_detail_title),
            )
        },
        bottomBar = {
            CommentInputBar(
                draft = draft,
                sending = sending,
                submitFailed = submitFailed,
                onDraftChange = { changed ->
                    draft = changed
                    if (submitFailed) submitFailed = false
                },
                onSend = sendComment,
            )
        },
    ) { padding ->
        val loaded = plan
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("…", style = MaterialTheme.typography.bodyLarge)
            }

            loaded == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
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
                comments = comments,
                liked = liked,
                onVote = vote,
                modifier = Modifier.fillMaxSize().padding(padding),
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
    comments: List<PlanComment>,
    liked: Boolean,
    onVote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    // Ranking order, not upload order: the point of the section is where each package stands here.
    val ranked = plan.packages
        .map { pkg -> pkg to ranking[pkg] }
        .sortedWith(compareBy(nullsLast()) { (_, rank) -> rank?.rank })

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        item { PlanHero(plan, profile, liked, onVote) }

        item {
            SectionTitle(
                title = stringResource(Res.string.plan_detail_ranking_title),
                subtitle = stringResource(
                    Res.string.plan_detail_ranking_hint,
                    profile?.displayModel ?: "-",
                ),
            )
        }
        items(ranked, key = { pair -> pair.first }) { (pkg, rank) ->
            PackageRankRow(
                pkg = pkg,
                rank = rank,
                app = byPackage[pkg],
                verdict = rules?.verdict(pkg, profile),
            )
        }

        item {
            SectionTitle(
                title = "${stringResource(Res.string.plan_comments_title)} · ${comments.size}",
                subtitle = null,
            )
        }
        if (comments.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.plan_detail_no_comments),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        items(comments) { comment -> CommentRow(comment) }
    }
}

/**
 * What the plan is, on this phone: title, whether it was reboot-proven, the device line the plan
 * was matched against, and the three numbers people judge plans by.
 */
@Composable
private fun PlanHero(
    plan: CommunityPlan,
    profile: DeviceProfile?,
    liked: Boolean,
    onVote: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plan.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Chip(
                    label = if (plan.isTest) {
                        stringResource(Res.string.plan_test_chip)
                    } else {
                        stringResource(Res.string.plan_verified_chip)
                    },
                    color = if (plan.isTest) colors.tertiary else colors.primary,
                )
            }
            Text(
                text = profile?.shortLabel() ?: "-",
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = colors.onPrimaryContainer.copy(alpha = 0.78f),
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (plan.note.isNotEmpty()) {
                Text(
                    text = plan.note,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stat(
                    value = "${plan.votes}",
                    label = stringResource(Res.string.plan_detail_stats_votes),
                    modifier = Modifier.weight(1f),
                )
                Stat(
                    value = "${plan.packages.size}",
                    label = stringResource(Res.string.plan_detail_stats_packages),
                    modifier = Modifier.weight(1f),
                )
                Stat(
                    value = "${plan.comments}",
                    label = stringResource(Res.string.plan_detail_stats_comments),
                    modifier = Modifier.weight(1f),
                )
                VoteAction(liked = liked, votes = plan.votes, onVote = onVote)
            }
            Text(
                text = stringResource(Res.string.plan_detail_uploader, plan.owner.ifEmpty { "—" }) +
                    " · " + plan.rebootedAt.take(10),
                fontSize = 9.sp,
                color = colors.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One big number over its label, the way stats are read at a glance. */
@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = value,
            fontSize = 17.sp,
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
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.ThumbUp,
            contentDescription = stringResource(Res.string.plan_vote),
            modifier = Modifier.size(14.dp),
            tint = content,
        )
        Spacer(Modifier.width(5.dp))
        Text(text = stringResource(Res.string.plan_vote), fontSize = 11.sp, color = content)
    }
}

/** A small label: "测试", "重启验证". */
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
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
                color = colors.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = rank?.let { stringResource(Res.string.ranking_votes, it.votes) }
                    ?: stringResource(Res.string.plan_detail_no_rank),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (rank != null) colors.onSurface else colors.outline,
            )
            Text(
                text = rank?.let { stringResource(Res.string.ranking_plans, it.plans) } ?: "",
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = colors.outline,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))
        SafetyChip(verdict)
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

/** One comment, newest first because the backend already orders them that way. */
@Composable
private fun CommentRow(comment: PlanComment) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // A round monogram instead of an avatar: the author is a number, not a person.
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = comment.author.takeLast(2),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = comment.createdAt.take(10),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text = comment.body,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

/** Draft field plus the send button, pinned under the list. */
@Composable
private fun CommentInputBar(
    draft: String,
    sending: Boolean,
    submitFailed: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(Res.string.plan_comment_hint), fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSend,
                    enabled = draft.isNotBlank() && !sending,
                    modifier = Modifier.height(44.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(Res.string.plan_comment_send), fontSize = 11.sp)
                }
            }
            if (submitFailed) {
                Text(
                    text = stringResource(Res.string.plan_upload_failed),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
