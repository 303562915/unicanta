package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.rules_device_title
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
import org.jetbrains.compose.resources.stringResource

/** Icon size of a ranking row: smaller than a tile, so more of them fit. */
private val ROW_ICON = 32.dp

/** Icon size inside a row's action buttons. */
private val ACTION_ICON = 15.dp

/**
 * Two views of the same question.
 *
 * *Apps*: every package on this phone with what the device-aware rules say about removing it, as
 * a compact row with its icon — system packages marked, so a shared list can be checked against
 * reality.
 * *Plans*: sets of packages other owners of the *same model and ROM version* removed and verified
 * by a reboot, most liked first. A test plan needs no reboot: the test password unlocks it, and
 * the plan is labelled as a test. Plans this install uploaded can be deleted again.
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
            DeviceHeader(profile)
            HorizontalDivider()
            when (tab) {
                0 -> RankingTab(apps = appListViewModel.apps, rules = rules, profile = profile)
                else -> PlansTab(
                    profile = profile,
                    settings = settings,
                    selectedPackages = appListViewModel.selectedApps.toList(),
                    onOpenPlan = onOpenPlan,
                )
            }
        }
    }
}

/** One thin line: what phone these rules are being matched against. */
@Composable
private fun DeviceHeader(profile: DeviceProfile?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.rules_device_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = profile?.label() ?: "-",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One package of the ranking: how much the community likes removing it on this device. */
private data class RankRow(val pkg: String, val votes: Int, val plans: Int, val app: AppInfo?)

/**
 * Packages ranked by the likes their plans got on this model and ROM version: the more people
 * endorse a plan that removes a package, the higher it sits. Votes come from the plans, so a
 * package nobody put in a verified plan does not appear at all.
 */
@Composable
private fun RankingTab(apps: List<AppInfo>, rules: SafetyRules?, profile: DeviceProfile?) {
    val repository = remember { PlanRepository(HttpClient()) }
    var rows by remember { mutableStateOf<List<RankRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(profile) {
        loading = true
        val byPackage = linkedMapOf<String, Pair<Int, Int>>()
        repository.plansFor(profile).forEach { plan ->
            plan.packages.forEach { pkg ->
                val (votes, count) = byPackage[pkg] ?: (0 to 0)
                byPackage[pkg] = (votes + plan.votes) to (count + 1)
            }
        }
        val byName = apps.associateBy { it.packageName }
        rows = byPackage.entries
            .map { (pkg, agg) -> RankRow(pkg, agg.first, agg.second, byName[pkg]) }
            .sortedWith(compareByDescending<RankRow> { it.votes }.thenByDescending { it.plans })
        loading = false
    }

    when {
        loading -> Text("…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        rows.isEmpty() -> Text(
            text = "这台机器还没有人上传方案，所以没有排行。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(rows, key = { _, row -> row.pkg }) { index, row ->
                RankRowView(index + 1, row, rules?.verdict(row.pkg, profile))
            }
        }
    }
}

@Composable
private fun RankRowView(rank: Int, row: RankRow, verdict: SafetyVerdict?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(18.dp),
        )
        if (row.app != null) AppIconImage(row.app, size = ROW_ICON) else Spacer(Modifier.width(ROW_ICON))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.app?.name ?: row.pkg,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.pkg,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${row.votes} 赞", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
            Text(
                text = "${row.plans} 方案" + if (row.app?.isSystemApp == true) " · system" else "",
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

/** One upload the dialog is about: which packages, and whether they are reboot-proven. */
private data class UploadRequest(
    val packages: List<String>,
    val canUpload: Boolean,
    val testMode: Boolean,
)

@OptIn(ExperimentalTime::class)
@Composable
private fun PlansTab(
    profile: DeviceProfile?,
    settings: CantaSettings,
    selectedPackages: List<String>,
    onOpenPlan: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = remember { PlanRepository(HttpClient()) }
    var plans by remember { mutableStateOf<List<CommunityPlan>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var upload by remember { mutableStateOf<UploadRequest?>(null) }
    var pendingDelete by remember { mutableStateOf<CommunityPlan?>(null) }
    var notice by remember { mutableStateOf("") }
    // The install's own code: created once, then it is who we are — no account, no login, and it
    // is what marks a plan as ours, so it can be deleted again.
    var identity by remember { mutableStateOf("") }

    val pending by settings.pendingRebootFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // Only a batch whose removal survived a reboot proves anything, so it is the only uploadable set.
    val verified = pending.filter { it.survived }.mapNotNull { it.pkg }.distinct()
    // What a test upload could carry: the whole pending batch (survived or not) plus whatever is
    // ticked in the app list right now.
    val testPackages = (pending.mapNotNull { it.pkg } + selectedPackages).distinct()
    val owner = remember(identity) { DeviceIdentity.ownerOf(identity) }
    val code = remember(identity) { DeviceIdentity.codeOf(identity) }

    LaunchedEffect(Unit) { identity = DeviceIdentity.ensure(settings) }

    LaunchedEffect(profile, identity) {
        loading = true
        plans = repository.plansFor(profile)
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    upload = UploadRequest(verified, canUpload = verified.isNotEmpty(), testMode = false)
                },
                enabled = profile != null,
                modifier = Modifier.height(30.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
            ) {
                Text("上传", fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            // Short, always visible: it is the whole login story of this page.
            Text(
                text = "身份码 $code · 免登录",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (notice.isNotEmpty()) {
            Text(
                text = notice,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        if (loading) {
            Text("…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item(key = "test") {
                    TestPlanRow(
                        packageCount = testPackages.size,
                        onOpen = {
                            notice = ""
                            if (profile == null) {
                                notice = "还没有读到机型信息，稍等一下再试。"
                            } else if (testPackages.isEmpty()) {
                                notice = "先卸载一批（不用重启）或在应用列表勾选几个包，再回来测试上传。"
                            } else {
                                upload = UploadRequest(testPackages, canUpload = false, testMode = true)
                            }
                        },
                    )
                    HorizontalDivider()
                }
                if (plans.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "这台机器（${profile?.model ?: "?"} / ${romName(profile?.rom ?: "")} ${profile?.romVersion ?: 0}）还没有人上传正式方案；网不通时这里也会是空的。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                items(plans, key = { it.id }) { plan ->
                    PlanRow(
                        plan = plan,
                        mine = owner.isNotEmpty() && plan.owner == owner,
                        onOpen = { onOpenPlan(plan.id) },
                        onVote = {
                            scope.launch {
                                val votes = repository.vote(plan.id, owner.ifEmpty { "u-" + Random.nextLong().toString(16) })
                                    ?: return@launch
                                plans = plans.map { if (it.id == plan.id) it.copy(votes = votes) else it }
                            }
                        },
                        onDelete = { pendingDelete = plan },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    upload?.let { request ->
        UploadPlanDialog(
            packages = request.packages,
            canUpload = request.canUpload,
            testMode = request.testMode,
            onDismiss = { upload = null },
            onSubmit = { title, note, test ->
                upload = null
                val target = profile
                if (target != null) {
                    scope.launch {
                        repository.submit(
                            profile = target,
                            title = title,
                            note = note,
                            packages = request.packages,
                            rebootedAt = Clock.System.now().toString(),
                            reporter = owner,
                            owner = owner,
                            test = test,
                        )
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
                            val ok = repository.delete(plan.id, owner)
                            notice = if (ok) "" else "删除失败：这个方案不是本机上传的，或者后端没通。"
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
 * The test entry of the plans list: always there, even before the first upload.
 *
 * It uploads without a reboot check, which is what the test password is for; the resulting plan
 * is stored as a test and can be deleted like any other plan of this install.
 */
@Composable
private fun TestPlanRow(packageCount: Int, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "测试方案 · 免重启",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                TestChip()
            }
            Text(
                text = "填测试密码 $TEST_UPLOAD_PASSWORD 就能上传，不用卸载后重启",
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$packageCount 个包待测试 · 点这里开始",
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Icon(
            imageVector = Icons.Default.ThumbUp,
            contentDescription = null,
            modifier = Modifier.size(ACTION_ICON),
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

/** The "test" mark: small, monospace-free, unmistakably not a verified plan. */
@Composable
private fun TestChip() {
    Text(
        text = "测试",
        fontSize = 9.sp,
        lineHeight = 11.sp,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun PlanRow(
    plan: CommunityPlan,
    mine: Boolean,
    onOpen: () -> Unit,
    onVote: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).clickable(onClick = onOpen)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (plan.isTest) {
                    Spacer(Modifier.width(6.dp))
                    TestChip()
                }
                if (mine) {
                    Spacer(Modifier.width(6.dp))
                    Text("我的", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (plan.note.isNotEmpty()) {
                Text(
                    plan.note,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${plan.packages.size} 个包 · ${plan.rebootedAt.take(10)} · ${plan.comments} 评论",
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
            )
        }
        IconButton(onClick = onVote, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.ThumbUp, contentDescription = "vote", modifier = Modifier.size(ACTION_ICON))
        }
        Text(plan.votes.toString(), fontSize = 10.sp)
        if (mine) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "delete",
                    modifier = Modifier.size(ACTION_ICON),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
