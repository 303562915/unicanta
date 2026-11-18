package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.plan_upload_button
import io.github.samolego.canta.generated.resources.rules_device_title
import io.github.samolego.canta.generated.resources.rules_empty
import io.github.samolego.canta.generated.resources.rules_title
import io.github.samolego.canta.generated.resources.safety_tab_apps
import io.github.samolego.canta.generated.resources.safety_tab_plans
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.dialog.UploadPlanDialog
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.SafetyRules
import io.github.samolego.canta.util.SafetyStatus
import io.github.samolego.canta.util.SafetyVerdict
import io.github.samolego.canta.util.romName
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.jetbrains.compose.resources.stringResource

/**
 * Two views of the same question.
 *
 * *Apps*: every package on this phone with what the device-aware rules say about removing it, as
 * a compact row with its icon — system packages marked, so a shared list can be checked against
 * reality.
 * *Plans*: sets of packages other owners of the *same model and ROM version* removed and verified
 * by a reboot, most liked first.
 */
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

    LaunchedEffect(Unit) {
        profile = handler.deviceProfile()
        rules = SafetyRules.bundled()
        if (appListViewModel.apps.isEmpty()) appListViewModel.loadApps()
    }

    Scaffold(
        topBar = {
            Column {
                ScreenTopBar(onNavigateBack = onNavigateBack, title = { Text(stringResource(Res.string.rules_title)) })
                TabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text(stringResource(Res.string.safety_tab_apps)) },
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text(stringResource(Res.string.safety_tab_plans)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DeviceHeader(profile)
            HorizontalDivider()
            when (tab) {
                0 -> AppsTab(apps = appListViewModel.apps, rules = rules, profile = profile)
                else -> PlansTab(profile = profile, settings = settings, onOpenPlan = onOpenPlan)
            }
        }
    }
}

@Composable
private fun DeviceHeader(profile: DeviceProfile?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = stringResource(Res.string.rules_device_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(profile?.label() ?: "-", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AppsTab(apps: List<AppInfo>, rules: SafetyRules?, profile: DeviceProfile?) {
    if (apps.isEmpty()) {
        Text(
            text = stringResource(Res.string.rules_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(apps, key = { it.packageName }) { app ->
            val verdict = rules?.verdict(app.packageName, profile)
            AppSafetyRow(app, verdict)
        }
    }
}

@Composable
private fun AppSafetyRow(app: AppInfo, verdict: SafetyVerdict?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(app)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            StatusChip(verdict)
            Text(
                text = if (app.isSystemApp) "system" else "user",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalTime::class)
@Composable
private fun PlansTab(
    profile: DeviceProfile?,
    settings: CantaSettings,
    onOpenPlan: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = remember { PlanRepository(HttpClient()) }
    var plans by remember { mutableStateOf<List<CommunityPlan>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var showUpload by remember { mutableStateOf(false) }
    // Per-install random id: the backend counts one vote per id, and a shared model name would
    // let one owner's vote stand in for everybody with the same phone.
    val voter = remember { "u-" + Random.nextLong().toString(16) }
    // Stable for this screen instance so the backend can tell reporters apart.
    val reporter = remember { "r-" + Random.nextLong().toString(16) }

    val pending by settings.pendingRebootFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // Only a batch whose removal survived a reboot proves anything, so it is the only uploadable set.
    val verified = pending.filter { it.survived }.mapNotNull { it.pkg }.distinct()
    // No device profile means nothing to match the plan against, so uploading is pointless.
    // An unverified batch still opens the dialog: it explains why it cannot be uploaded.
    val canUpload = profile != null && verified.isNotEmpty()

    LaunchedEffect(profile) {
        loading = true
        val loaded = repository.plansFor(profile)
        error = loaded.isEmpty()
        plans = loaded
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { showUpload = true }, enabled = profile != null) {
                Text(stringResource(Res.string.plan_upload_button))
            }
        }
        when {
            loading -> Text("…", modifier = Modifier.padding(16.dp))
            plans.isEmpty() -> Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (error) "暂时读不到社区方案（网络或后端没通）" else "这台机器（${profile?.model ?: "?"} / ${romName(profile?.rom ?: "")} ${profile?.romVersion ?: 0}）还没有人上传方案",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "只有同一机型 + 同一系统版本、并且上传者确认过「卸载后手机正常重启」的方案才会出现在这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(plans, key = { it.id }) { plan ->
                    PlanRow(
                        plan = plan,
                        onOpen = { onOpenPlan(plan.id) },
                        onVote = {
                            scope.launch {
                                val votes = repository.vote(plan.id, voter) ?: return@launch
                                plans = plans.map { if (it.id == plan.id) it.copy(votes = votes) else it }
                                    .sortedByDescending { it.votes }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showUpload) {
        UploadPlanDialog(
            packages = verified,
            canUpload = canUpload,
            onDismiss = { showUpload = false },
            onSubmit = { title, note ->
                showUpload = false
                val target = profile
                if (target != null) {
                    scope.launch {
                        repository.submit(target, title, note, verified, Clock.System.now().toString(), reporter)
                        // Re-read either way: a failed upload simply leaves the list as it was.
                        val loaded = repository.plansFor(target)
                        error = loaded.isEmpty()
                        plans = loaded
                    }
                }
            },
        )
    }
}

@Composable
private fun PlanRow(plan: CommunityPlan, onOpen: () -> Unit, onVote: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).clickable(onClick = onOpen)) {
            Text(plan.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (plan.note.isNotEmpty()) {
                Text(
                    plan.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Text(
                text = "${plan.packages.size} 个包 · 重启确认于 ${plan.rebootedAt.take(10)} · ${plan.comments} 条评论",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onVote) {
            Icon(Icons.Default.ThumbUp, contentDescription = "vote", modifier = Modifier.size(18.dp))
        }
        Text(plan.votes.toString(), style = MaterialTheme.typography.labelLarge)
    }
}
