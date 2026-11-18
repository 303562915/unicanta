package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.PlanComment
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.plan_comment_hint
import io.github.samolego.canta.generated.resources.plan_comment_send
import io.github.samolego.canta.generated.resources.plan_comments_title
import io.github.samolego.canta.generated.resources.plan_detail_title
import io.github.samolego.canta.generated.resources.plan_packages_title
import io.github.samolego.canta.generated.resources.plan_upload_failed
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.util.DeviceProfile
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

/**
 * One community plan, opened from the plans list.
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
) {
    val scope = rememberCoroutineScope()

    var plan by remember { mutableStateOf<CommunityPlan?>(null) }
    var comments by remember { mutableStateOf<List<PlanComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var submitFailed by remember { mutableStateOf(false) }

    // Stable for the lifetime of the screen, unlike the plan: comments need an author.
    val author = remember { "u-" + Random.nextLong().toString(16) }

    LaunchedEffect(planId, profile) {
        // Keep the already loaded plan on screen while a late profile reloads it.
        if (plan == null) loading = true
        plan = repository.plansFor(profile).firstOrNull { it.id == planId }
        comments = repository.comments(planId)
        loading = false
    }

    val sendComment: () -> Unit = {
        val body = draft.trim()
        if (body.isNotEmpty() && !sending) {
            scope.launch {
                sending = true
                submitFailed = !repository.comment(planId, body, author)
                if (!submitFailed) {
                    draft = ""
                    comments = repository.comments(planId)
                }
                sending = false
            }
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                onNavigateBack = onNavigateBack,
                title = { Text(stringResource(Res.string.plan_detail_title)) },
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
        val loadedPlan = plan
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("…", style = MaterialTheme.typography.bodyLarge)
            }

            loadedPlan == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "找不到这个方案（可能已被删除，或只对上传它的机型可见）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> PlanDetailContent(
                plan = loadedPlan,
                comments = comments,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

/** Header, package list and comment list of a loaded plan. */
@Composable
private fun PlanDetailContent(
    plan: CommunityPlan,
    comments: List<PlanComment>,
    modifier: Modifier = Modifier,
) {
    val packagesTitle = stringResource(Res.string.plan_packages_title)
    val commentsTitle = stringResource(Res.string.plan_comments_title)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { PlanHeaderCard(plan) }

        item { SectionTitle(packagesTitle) }
        items(plan.packages) { packageName ->
            // Package names are often longer than the screen: keep one line and shrink instead of
            // wrapping into a block of text nobody can scan.
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        item { SectionTitle(commentsTitle) }
        if (comments.isEmpty()) {
            item {
                Text(
                    text = "还没有评论",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(comments) { comment -> CommentRow(comment) }
    }
}

/** Title, note, size, votes and the reboot date that made the plan trustworthy. */
@Composable
private fun PlanHeaderCard(plan: CommunityPlan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = plan.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (plan.note.isNotEmpty()) {
                Text(
                    text = plan.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                MetaText("${plan.votes}")
                Spacer(modifier = Modifier.width(12.dp))
                MetaText("${plan.packages.size} 个包")
                Spacer(modifier = Modifier.width(12.dp))
                MetaText(plan.rebootedAt.take(10))
            }
        }
    }
}

/** Small secondary line inside the header row. */
@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Label ("Packages", "Comments") that starts a block of the list. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/** One comment, newest first because the backend already orders them that way. */
@Composable
private fun CommentRow(comment: PlanComment) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = comment.author,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = comment.createdAt.take(10),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = comment.body,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp),
        )
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(Res.string.plan_comment_hint)) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSend, enabled = draft.isNotBlank() && !sending) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(Res.string.plan_comment_send))
                }
            }
            if (submitFailed) {
                Text(
                    text = stringResource(Res.string.plan_upload_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
