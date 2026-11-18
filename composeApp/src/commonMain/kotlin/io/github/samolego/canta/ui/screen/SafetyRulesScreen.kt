package io.github.samolego.canta.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.PackageRank
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.rankPackages
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.plan_test_chip
import io.github.samolego.canta.generated.resources.plan_upload_fail
import io.github.samolego.canta.generated.resources.plan_upload_ok
import io.github.samolego.canta.generated.resources.plan_upload_submit
import io.github.samolego.canta.generated.resources.plan_vote
import io.github.samolego.canta.generated.resources.plans_empty_body
import io.github.samolego.canta.generated.resources.plans_empty_title
import io.github.samolego.canta.generated.resources.plans_identity
import io.github.samolego.canta.generated.resources.plans_mine
import io.github.samolego.canta.generated.resources.plans_test_entry_body
import io.github.samolego.canta.generated.resources.plans_test_entry_count
import io.github.samolego.canta.generated.resources.plans_test_entry_title
import io.github.samolego.canta.generated.resources.plans_upload_cta
import io.github.samolego.canta.generated.resources.plans_upload_cta_hint
import io.github.samolego.canta.generated.resources.plans_upload_cta_none
import io.github.samolego.canta.generated.resources.ranking_empty
import io.github.samolego.canta.generated.resources.ranking_plans
import io.github.samolego.canta.generated.resources.ranking_votes
import io.github.samolego.canta.generated.resources.rules_title
import io.github.samolego.canta.generated.resources.safety_tab_apps
import io.github.samolego.canta.generated.resources.safety_tab_plans
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.dialog.UploadPlanDialog
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.util.DeviceIdentity
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.Identity
import io.github.samolego.canta.util.SafetyRules
import io.github.samolego.canta.util.SafetyStatus
import io.github.samolego.canta.util.SafetyVerdict
import io.github.samolego.canta.util.TEST_UPLOAD_PASSWORD
import io.github.samolego.canta.util.romName
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Icon size of a ranking row: smaller than a tile, so more of them fit. */
private val ROW_ICON = 32.dp

/** A line under the header: what the last upload or delete said. */
private data class Notice(val text: String, val error: Boolean = false)

/** One upload the dialog is about: what it may contain, and whether it counts as verified. */
private data class UploadRequest(
    val candidates: List<AppInfo>,
    val preselected: Set<String>,
    val canUpload: Boolean,
    val testMode: Boolean,
)

/**
 * Two views of the same question, both scoped to *this* phone.
 *
 * *Top removed*: packages ranked by the likes their plans got on this model and ROM version — the
 * more people endorse a plan that removes a package, the higher it sits. Every row is a package
 * this phone could remove, with what the device-aware rules say about it.
 * *Plans*: the plans themselves, most liked first. A test plan needs no reboot: the test password
 * unlocks it and the plan is labelled as one. Plans this install uploaded can be deleted again.
 *
 * The phone is named the way it is sold ("Redmi K70"), never by the retail model number: that
 * number is what the ROM reports but not what anyone recognises.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun SafetyRulesScreen(
    handler: CantaHandler,
    appListViewModel: AppListViewModel,
    settings: CantaSettings,
    onOpenPlan: (Long) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var profile by remember { mutableStateOf<DeviceProfile?>(null) }
    var rules by remember { mutableStateOf<SafetyRules?>(null) }

    val scope = rememberCoroutineScope()
    val repository = remember { PlanRepository(HttpClient()) }
    var plans by remember { mutableStateOf<List<CommunityPlan>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    var upload by remember { mutableStateOf<UploadRequest?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CommunityPlan?>(null) }
    // Plans this install already liked: the button keeps its filled state, the count stays put.
    var liked by remember { mutableStateOf(setOf<Long>()) }
    // The install's own identity: a token created once, plus the number the backend gave it.
    var identity by remember { mutableStateOf(Identity("")) }

    val apps = appListViewModel.apps
    val ticked = appListViewModel.selectedApps.toList()
    val uninstalledApps = apps.filter { it.isUninstalled }
    val deletedPackages = uninstalledApps.map { it.packageName }
    val tickedApps = apps.filter { it.packageName in ticked }

    val pending by settings.pendingRebootFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val verified = pending.filter { it.survived }.mapNotNull { it.pkg }.distinct()
    val confirmed = deletedPackages.count { it in verified }
    val owner = identity.owner

    LaunchedEffect(Unit) {
        profile = handler.deviceProfile()
        rules = SafetyRules.bundled()
        if (appListViewModel.apps.isEmpty()) appListViewModel.loadApps()
    }

    // Read the remembered identity first; only a phone that has never signed in asks for a number.
    LaunchedEffect(profile) {
        var current = DeviceIdentity.current(settings)
        if (!current.known) {
            repository.signIn(current.token, profile)?.let { issued ->
                settings.setIdentityUserId(issued)
                current = current.copy(userId = issued)
            }
        }
        identity = current
    }

    LaunchedEffect(profile) {
        loading = true
        plans = repository.plansFor(profile)
        loading = false
    }

    // What a plan may contain: everything this phone no longer has, plus whatever is ticked right
    // now — the test flow is meant to be usable before anything has been removed.
    val candidates = (uninstalledApps + tickedApps).distinctBy { it.packageName }
    val emptyHint = stringResource(Res.string.plans_upload_cta_none)
    val noProfileHint = "还没有读到机型信息，稍等一下再试。"
    val openUpload: (Boolean) -> Unit = { test ->
        notice = null
        when {
            profile == null -> notice = Notice(noProfileHint, error = true)
            candidates.isEmpty() -> notice = Notice(emptyHint, error = true)
            else -> upload = UploadRequest(
                candidates = candidates,
                preselected = if (test) candidates.map { it.packageName }.toSet() else deletedPackages.toSet(),
                canUpload = !test && deletedPackages.isNotEmpty() && confirmed > 0,
                testMode = test,
            )
        }
    }
    val vote: (CommunityPlan) -> Unit = { plan ->
        if (plan.id !in liked) {
            liked = liked + plan.id
            scope.launch {
                val votes = repository.vote(
                    plan.id,
                    owner.ifEmpty { "u-" + Random.nextLong().toString(16) },
                ) ?: return@launch
                plans = plans.map { if (it.id == plan.id) it.copy(votes = votes) else it }
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                ScreenTopBar(
                    onNavigateBack = onNavigateBack,
                    title = { Text(stringResource(Res.string.rules_title)) },
                )
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text(stringResource(Res.string.safety_tab_apps), fontSize = 13.sp) },
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text(stringResource(Res.string.safety_tab_plans), fontSize = 13.sp) },
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DeviceBanner(profile = profile, planCount = plans.size)
            notice?.let { line ->
                Text(
                    text = line.text,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = if (line.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
            HorizontalDivider()
            if (tab == 0) {
                RankingTab(
                    plans = plans,
                    loading = loading,
                    apps = apps,
                    rules = rules,
                    profile = profile,
                )
            } else {
                PlansTab(
                    plans = plans,
                    loading = loading,
                    profile = profile,
                    identity = identity,
                    owner = owner,
                    deletedCount = deletedPackages.size,
                    confirmedCount = confirmed,
                    tickedCount = tickedApps.size,
                    liked = liked,
                    onUpload = { openUpload(false) },
                    onTestUpload = { openUpload(true) },
                    onOpenPlan = onOpenPlan,
                    onVote = vote,
                    onDelete = { pendingDelete = it },
                )
            }
        }
    }

    upload?.let { request ->
        UploadPlanDialog(
            candidates = request.candidates,
            preselected = request.preselected,
            profile = profile,
            canUpload = request.canUpload,
            testMode = request.testMode,
            publishing = uploading,
            onDismiss = { if (!uploading) upload = null },
            onSubmit = { title, note, packages, test ->
                val target = profile
                if (target != null) {
                    scope.launch {
                        uploading = true
                        val id = repository.submit(
                            profile = target,
                            title = title,
                            note = note,
                            packages = packages,
                            rebootedAt = Clock.System.now().toString(),
                            reporter = owner,
                            owner = owner,
                            test = test,
                        )
                        uploading = false
                        upload = null
                        notice = if (id != null) {
                            Notice(getString(Res.string.plan_upload_ok))
                        } else {
                            Notice(getString(Res.string.plan_upload_fail), error = true)
                        }
                        // Re-read either way: a failed upload simply leaves the list as it was.
                        plans = repository.plansFor(target)
                    }
                }
            },
        )
    }

    pendingDelete?.let { plan ->
        CantaDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除方案",
            buttons = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                Button(
                    onClick = {
                        pendingDelete = null
                        val target = profile
                        scope.launch {
                            val ok = repository.delete(plan.id, owner, identity.token)
                            notice = if (ok) {
                                null
                            } else {
                                Notice("删除失败：这个方案不是本机上传的，或者后端没通。", error = true)
                            }
                            if (target != null) plans = repository.plansFor(target)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("删除") }
            },
        ) {
            Text(
                text = "「${plan.title}」会被永久删除，别人的点赞和评论一起消失。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The phone as it is sold, over the colour that marks this section: model, ROM, Android version,
 * and how many plans exist for it.
 */
@Composable
private fun DeviceBanner(profile: DeviceProfile?, planCount: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.primaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile?.displayModel ?: "-",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profile?.let { romLine(it) } ?: "",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$planCount",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                )
                Text(
                    text = stringResource(Res.string.safety_tab_plans),
                    fontSize = 9.sp,
                    color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/** "HyperOS 2 · Android 15", without the doubled space a stock ROM would leave behind. */
private fun romLine(profile: DeviceProfile): String {
    val name = romName(profile.rom)
    val romPart = if (profile.romVersion > 0 && name != "Android") "$name ${profile.romVersion}" else name
    return if (romPart == "Android") {
        "Android ${profile.release}"
    } else {
        "$romPart · Android ${profile.release}"
    }
}

/**
 * Packages ranked by the likes their plans got on this model and ROM version. Votes come from the
 * plans, so a package nobody put in a verified plan does not appear at all.
 */
@Composable
private fun RankingTab(
    plans: List<CommunityPlan>,
    loading: Boolean,
    apps: List<AppInfo>,
    rules: SafetyRules?,
    profile: DeviceProfile?,
) {
    val ranking = remember(plans) { rankPackages(plans) }
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }

    when {
        loading -> Text(
            "…",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
        ranking.isEmpty() -> EmptyCard(
            title = stringResource(Res.string.safety_tab_apps),
            body = stringResource(Res.string.ranking_empty, profile?.displayModel ?: "-"),
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            itemsIndexed(ranking, key = { _, row -> row.pkg }) { _, row ->
                RankRowView(
                    rank = row,
                    app = byPackage[row.pkg],
                    verdict = rules?.verdict(row.pkg, profile),
                )
            }
        }
    }
}

/** One ranking row: position, icon, what it is, and how much the community likes removing it. */
@Composable
private fun RankRowView(rank: PackageRank, app: AppInfo?, verdict: SafetyVerdict?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(rank)
        Spacer(Modifier.width(8.dp))
        if (app != null) AppIconImage(app, size = ROW_ICON) else Spacer(Modifier.width(ROW_ICON))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app?.name ?: rank.pkg,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = rank.pkg,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(Res.string.ranking_votes, rank.votes),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.ranking_plans, rank.plans),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))
        StatusChip(verdict)
    }
}

/** The position, painted differently on the podium so the top three read at a glance. */
@Composable
private fun RankBadge(rank: PackageRank) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (rank.rank) {
        1 -> colors.primary to colors.onPrimary
        2 -> colors.tertiary to colors.onTertiary
        3 -> colors.secondary to colors.onSecondary
        else -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${rank.rank}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = content,
        )
    }
}

@Composable
private fun StatusChip(verdict: SafetyVerdict?) {
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

/** The plans of this phone: an upload entry on top, then the plans themselves, most liked first. */
@Composable
private fun PlansTab(
    plans: List<CommunityPlan>,
    loading: Boolean,
    profile: DeviceProfile?,
    identity: Identity,
    owner: String,
    deletedCount: Int,
    confirmedCount: Int,
    tickedCount: Int,
    liked: Set<Long>,
    onUpload: () -> Unit,
    onTestUpload: () -> Unit,
    onOpenPlan: (Long) -> Unit,
    onVote: (CommunityPlan) -> Unit,
    onDelete: (CommunityPlan) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        UploadEntry(
            profile = profile,
            deletedCount = deletedCount,
            confirmedCount = confirmedCount,
            tickedCount = tickedCount,
            identity = identity,
            onUpload = onUpload,
            onTestUpload = onTestUpload,
        )
        HorizontalDivider()
        when {
            loading -> Text("…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            plans.isEmpty() -> EmptyCard(
                title = stringResource(Res.string.plans_empty_title, profile?.displayModel ?: "-"),
                body = stringResource(Res.string.plans_empty_body),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plans, key = { it.id }) { plan ->
                    PlanCard(
                        plan = plan,
                        mine = owner.isNotEmpty() && plan.owner == owner,
                        liked = plan.id in liked,
                        onOpen = { onOpenPlan(plan.id) },
                        onVote = { onVote(plan) },
                        onDelete = { onDelete(plan) },
                    )
                }
            }
        }
    }
}

/**
 * The way into the upload flow: one tap-target that also says what would be published, plus the
 * identity this install uploads and votes under.
 */
@Composable
private fun UploadEntry(
    profile: DeviceProfile?,
    deletedCount: Int,
    confirmedCount: Int,
    tickedCount: Int,
    identity: Identity,
    onUpload: () -> Unit,
    onTestUpload: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.primaryContainer.copy(alpha = 0.55f))
                .clickable(onClick = onUpload)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.plans_upload_cta),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    text = if (deletedCount + tickedCount == 0) {
                        stringResource(Res.string.plans_upload_cta_none)
                    } else {
                        stringResource(Res.string.plans_upload_cta_hint, deletedCount, confirmedCount)
                    },
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.plans_identity, identity.code),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onUpload,
                enabled = profile != null,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.plan_upload_submit), fontSize = 11.sp)
            }
        }
        // The test flow: no reboot proof, no waiting, labelled as a test everywhere it shows up.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTestUpload)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.plans_test_entry_title),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    TestChip()
                }
                Text(
                    text = stringResource(Res.string.plans_test_entry_body, TEST_UPLOAD_PASSWORD),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(Res.string.plans_test_entry_count, tickedCount),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** One plan: what it is, what it contains, and how many people liked it. Tapping it opens it. */
@Composable
private fun PlanCard(
    plan: CommunityPlan,
    mine: Boolean,
    liked: Boolean,
    onOpen: () -> Unit,
    onVote: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plan.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (plan.isTest) {
                    Spacer(Modifier.width(6.dp))
                    TestChip()
                }
                if (mine) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.plans_mine),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (mine) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (plan.note.isNotEmpty()) {
                Text(
                    text = plan.note,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${plan.packages.size} 包 · ${plan.comments} 评论 · ${plan.rebootedAt.take(10)}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                VoteButton(votes = plan.votes, liked = liked, onVote = onVote)
            }
        }
    }
}

/**
 * The like button: it springs when tapped and fills in once this install has voted — one vote per
 * phone, which is what the backend counts.
 */
@Composable
private fun VoteButton(votes: Int, liked: Boolean, onVote: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val colors = MaterialTheme.colorScheme
    val content = if (liked) colors.primary else colors.onSurfaceVariant
    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(50))
            .background(if (liked) colors.primary.copy(alpha = 0.16f) else colors.surfaceContainerHighest)
            .clickable {
                scope.launch {
                    scale.animateTo(1.3f, spring(stiffness = 900f))
                    scale.animateTo(1f, spring(stiffness = 420f))
                }
                onVote()
            }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.ThumbUp,
            contentDescription = stringResource(Res.string.plan_vote),
            modifier = Modifier.size(13.dp),
            tint = content,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$votes",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
    }
}

/** The "test" mark: small, unmistakably not a verified plan. */
@Composable
private fun TestChip() {
    Text(
        text = stringResource(Res.string.plan_test_chip),
        fontSize = 9.sp,
        lineHeight = 11.sp,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** An empty list still has to say why it is empty. */
@Composable
private fun EmptyCard(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 20.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = body,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
