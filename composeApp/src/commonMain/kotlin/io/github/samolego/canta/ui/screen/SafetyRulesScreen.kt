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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.community.CatalogModel
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.PackageRank
import io.github.samolego.canta.data.community.PlanCache
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.rankPackages
import io.github.samolego.canta.data.community.toCatalogModels
import io.github.samolego.canta.data.community.withoutDuplicates
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.plan_test_chip
import io.github.samolego.canta.generated.resources.plan_upload_fail
import io.github.samolego.canta.generated.resources.plan_upload_failed_network
import io.github.samolego.canta.generated.resources.plan_upload_offline
import io.github.samolego.canta.generated.resources.plan_upload_ok_detail
import io.github.samolego.canta.generated.resources.plan_vote
import io.github.samolego.canta.generated.resources.plans_empty_body
import io.github.samolego.canta.generated.resources.plans_empty_title
import io.github.samolego.canta.generated.resources.plans_reload
import io.github.samolego.canta.generated.resources.plans_reload_failed
import io.github.samolego.canta.generated.resources.plans_verified_short
import io.github.samolego.canta.generated.resources.plans_upload_cta
import io.github.samolego.canta.generated.resources.plans_upload_gate_none
import io.github.samolego.canta.generated.resources.ranking_empty
import io.github.samolego.canta.generated.resources.ranking_marks
import io.github.samolego.canta.generated.resources.safety_tab_apps
import io.github.samolego.canta.generated.resources.safety_tab_plans
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.dismissInputOnTap
import io.github.samolego.canta.ui.component.DeviceHero
import io.github.samolego.canta.ui.component.PlanScope
import io.github.samolego.canta.ui.component.PlanScopeBar
import io.github.samolego.canta.ui.component.PlanScopeDialog
import io.github.samolego.canta.ui.component.label
import io.github.samolego.canta.ui.component.memoryKey
import io.github.samolego.canta.ui.component.planScopeOfStored
import io.github.samolego.canta.ui.component.rememberRememberedListState
import io.github.samolego.canta.ui.component.toStoredValue
import io.github.samolego.canta.ui.fl.FlSegment
import io.github.samolego.canta.ui.fl.FlSegmentedControl
import io.github.samolego.canta.ui.dialog.UploadPlanDialog
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.util.DeviceIdentity
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.PackageTelemetry
import io.github.samolego.canta.util.Identity
import io.github.samolego.canta.util.SafetyRules
import io.github.samolego.canta.util.SafetyStatus
import io.github.samolego.canta.util.SafetyVerdict
import io.github.samolego.canta.util.romLabelOf
import io.github.samolego.canta.util.romName
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Space the floating dock takes at the foot of a page: its height plus its margin. */
private val DOCK_CLEARANCE = 84.dp

/** Icon size of a ranking row: smaller than a tile, so more of them fit. */
private val ROW_ICON = 32.dp

/** How often the plans of this phone are asked for before the page accepts an empty answer. */
private const val PLAN_ATTEMPTS = 3

/** Waits between those attempts: a phone that just booted is often not on the network yet. */
private val PLAN_RETRY_DELAYS = longArrayOf(1_000L, 3_000L)

/** How often the device profile is read before the page gives up for this visit. */
private const val PROFILE_ATTEMPTS = 5

/** A line under the header: what the last upload or delete said. */
private data class Notice(val text: String, val error: Boolean = false)

/** One upload the dialog is about: what it may contain, and what is already ticked. */
private data class UploadRequest(
    val candidates: List<AppInfo>,
    val preselected: Set<String>,
)

/**
 * Two views of the same question, scoped to one phone at a time.
 *
 * *Top removed*: packages ranked by the likes their plans got on that model and ROM version — the
 * more people endorse a plan that removes a package, the higher it sits. Every row is a package
 * that phone could remove, with what the device-aware rules say about it.
 * *Plans*: the plans themselves, most liked first. A plan goes up only after the packages in it
 * were removed, the phone restarted and the restart check passed: there is no password that stands
 * in for that any more, and a plan uploaded by an older build without the proof keeps its "test"
 * mark. Plans this install uploaded can be deleted again.
 *
 * The phone is named the way it is sold ("Redmi K70"), never by the retail model number: that
 * number is what the ROM reports but not what anyone recognises.
 *
 * The phone is a *choice*, not a given: the name on the band opens the model chips, which list
 * every model the backend has plans for — grouped by family, with how many people joined each one —
 * and then the builds of the model that was opened. Both lists are that model's; nothing is
 * silently mixed with this phone's. A plan read there is still readable and still applicable, and
 * the plan's own page says so before it is used: another model's plan is not blocked, it is
 * labelled.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun SafetyRulesScreen(
    handler: CantaHandler,
    appListViewModel: AppListViewModel,
    settings: CantaSettings,
    telemetry: PackageTelemetry? = null,
    onOpenPlan: (Long) -> Unit,
    /** Opens one app's own page from a ranking row. */
    onOpenApp: (String) -> Unit = {},
    /** The community repository of the app; the page makes its own when none is given. */
    repository: PlanRepository? = null,
    /** The plans this phone saw last time; null keeps the page network-only. */
    planCache: PlanCache? = null,
    /**
     * 把「选择机型」的选择写回设置的那条路；不传就只有页面内的记忆（返回即丢失）。
     *
     * 用回调而不是直接收一个 SettingsViewModel：这一页在测试与网页版里都不需要整个 view model，
     * 只需要一个"记住它"的动作。
     */
    onScopeRemember: (String) -> Unit = {},
    onNavigateBack: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var profile by remember { mutableStateOf<DeviceProfile?>(null) }
    var rules by remember { mutableStateOf<SafetyRules?>(null) }
    // Which phone's plans are on screen. Every model by default: with a young community this
    // phone's own list is often empty, and an empty ranking says nothing useful about what the
    // community removes. A chip in the picker narrows it to this phone or to another model.
    //
    // 上次挑的机型存在设置里（见 AppSettingsProto.planScope）：这一页是二级页，去应用详情再返回
    // 会重新组合，只放在 `remember` 里就变回「全部机型」——群里说的「记不住本机机型，返回就成了
    // 全部机型」正是这个。
    //
    // 现在它**就是**页面状态本身（下面那个 `planScopeOfStored(storedScope, catalog)`），不再是
    // "先 remember 一份、再一次性还原一次"。一次性还原的写法有两个洞，正好就是「返回 / 切回来还要
    // 重选」：
    //  * 还原要求目录先到，`catalog` 一空就 `return@LaunchedEffect`，而 `scopeRestored` 那一位在
    //    数据到手前没有任何东西可还原时就白花了；
    //  * 每次重组都从 `PlanScope.All` 重新开始，靠那个效应再"跳"回某台机器——中间会闪一下
    //    「全部机型」，用户看到的就是"又让我重选"。
    // 读设置是同步的（`planScopeFlow` 背后是 `StateFlow`），首帧就已经是上次那台机器。
    val storedScope by settings.planScopeFlow.collectAsStateWithLifecycle(initialValue = "")
    // Every model the backend has plans for, with the counts its chips print.
    var catalog by remember { mutableStateOf<List<CatalogModel>>(emptyList()) }
    // 目录里认不出存的那台机器（型号改名、方案删光）就退到「全部机型」：和没选过一样，不会崩。
    //
    // 换范围之后的「清旧行 + 立加载中」写在下面 `plans` / `loading` / `notice` 之后那个
    // `LaunchedEffect(planScope)` 里，原因见那里。
    val planScope: PlanScope = remember(storedScope, catalog) {
        planScopeOfStored(storedScope, catalog)
    }
    // Every model the backend has plans for, with the counts its chips print.
    var catalogLoading by remember { mutableStateOf(true) }
    var pickerOpen by remember { mutableStateOf(false) }
    // The package filter of the list under the switch: the switch says which phone, this says
    // which package in it. Not remembered across a reload — a stale filter looks like a shorter
    // list, not like a search that is still on.
    var searchPackages by remember { mutableStateOf(false) }
    var packageQuery by remember { mutableStateOf("") }

    // The photo at the top is decoration: the About page can turn it off, and the page then keeps
    // the band with the phone's name and the switch but not the picture under it. A plain `Flow`,
    // like the other stored settings, so it needs the value to show before the first read comes
    // back — and the picture is on by default, which is what a fresh install should start with.
    val showDevicePhoto by settings.showDevicePhotoFlow.collectAsStateWithLifecycle(initialValue = false)

    // 顶部机型胶囊点过一次就不再呼吸：提示是为了让人知道那里能换手机，知道了就不该一直闪。
    // 存的是"点过"，见 AppSettingsProto.scopeHintSeen。
    val scopeHintSeen by settings.scopeHintSeenFlow.collectAsStateWithLifecycle(initialValue = false)

    val scope = rememberCoroutineScope()
    val fallbackRepository = remember { PlanRepository(HttpClient()) }
    val repo = repository ?: fallbackRepository
    var plans by remember { mutableStateOf<List<CommunityPlan>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // The device this phone's plans were asked for; retried explicitly when the answer was empty
    // or the request did not arrive.
    var reloads by remember { mutableIntStateOf(0) }
    // True when the last request did not arrive: the list may then be the cached one, not this
    // phone's plans.
    var offline by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    var upload by remember { mutableStateOf<UploadRequest?>(null) }
    var uploading by remember { mutableStateOf(false) }
    // Whether the community backend answered the preflight: null while it is being asked, and it is
    // what the sheet shows before the upload button is allowed to do anything.
    var reach by remember { mutableStateOf<Boolean?>(null) }
    var reaching by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CommunityPlan?>(null) }
    // Plans this install already liked: the button keeps its filled state, the count stays put.
    var liked by remember { mutableStateOf(setOf<Long>()) }
    // The install's own identity: a token created once, plus the number the backend gave it.
    var identity by remember { mutableStateOf(Identity("")) }

    // 换了范围就把上一份 rows 清掉、把「加载中」立起来。这是 [pickScope] / 发布后切回本机写进设置的
    // **后果**，所以放在对被观察值的反应里，而不是放在点击那一刻：无论谁写下这个值，列表都不会拿旧
    // 范围的数据顶着新范围的名字显示一帧，也不会因为漏写而"点了没反应"。
    //
    // 放在这一组 `remember` 之后，是因为它首帧就要读 `plans` / `loading` / `notice`：
    // 在 Kotlin 属性初始化顺序里，写在前面的 `LaunchedEffect` 块能编译，但读到的会是未初始化值。
    LaunchedEffect(planScope) {
        if (loading || plans.isNotEmpty()) {
            plans = emptyList()
            loading = true
        }
        notice = null
    }

    val apps = appListViewModel.apps
    val ticked = appListViewModel.selectedApps.toList()
    val uninstalledApps = apps.filter { it.isUninstalled }
    val deletedPackages = uninstalledApps.map { it.packageName }
    val tickedApps = apps.filter { it.packageName in ticked }

    val pending by settings.pendingRebootFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // A package is verified only after a tracked uninstall (or a baseline from before Canta) is
    // still absent after a later boot. Older uninstalls seed a baseline instead of counting as proven.
    val verifiedPackages = pending.filter { it.survived }.mapNotNull { it.pkg }
        .filter { it in deletedPackages }.distinct().toSet()
    val confirmed = verifiedPackages.size
    val awaitingFirstReboot = pending.count { it.baseline && !it.survived && it.pkg in deletedPackages }
    val owner = identity.owner

    /**
     * 把屏幕上这份列表写回缓存。
     *
     * 只在一个范围里写：缓存分「全机型」和「本机」两份，他机的列表从不入缓存。
     *
     * 调用点都是「真的变了」的地方——刷新拿到了新数据、本机投了一票、删掉了一条、上传后重读——
     * 而不是每次重画都写：界面上的列表和缓存必须同步，否则下次进来先画出来的是改动之前那一版，
     * 看着像回退。
     */
    val persistPlans: suspend (List<CommunityPlan>) -> Unit = { next ->
        when (planScope) {
            PlanScope.All -> planCache?.writeAll(next)
            PlanScope.ThisPhone -> profile?.let { planCache?.write(it, next) }
            is PlanScope.Other -> Unit
        }
    }

    LaunchedEffect(Unit) {
        rules = SafetyRules.bundled()
        if (appListViewModel.apps.isEmpty()) appListViewModel.loadApps()
        // The profile is read from the device, which can still be busy right after a boot: keep
        // asking instead of leaving the page without a device for the rest of the visit.
        repeat(PROFILE_ATTEMPTS) {
            profile = handler.deviceProfile()
            if (profile != null) return@LaunchedEffect
            delay(1_200L)
        }
    }

    // The reboot check runs at every start, but a boot broadcast can be missed and the app list
    // may have been empty when it ran: asking again here is what makes "重启验证" show up.
    LaunchedEffect(apps.size) {
        if (apps.isNotEmpty()) {
            telemetry?.seedExistingUninstalls(apps)
            appListViewModel.refreshSurvivors()
        }
    }

    // Read the remembered identity first; only a phone that has never signed in asks for a number.
    LaunchedEffect(profile) {
        var current = DeviceIdentity.current(settings)
        if (!current.known) {
            repo.signIn(current.token, profile)?.let { issued ->
                settings.setIdentityUserId(issued)
                current = current.copy(userId = issued)
            }
        }
        identity = current
    }

    // The catalog is what the picker draws: one row per build, folded into models. It is asked for
    // with the plans, so opening the picker right after the page does not sit on an empty list.
    // 登录完成（identity 从未登录的那个值变成带号码的值）后重拉一次：上一次拉的时候后端还不知道
    // 这台机器跑哪一版，而登录那一下会把本机版本一起报上去、并顺手补回自己的老方案，所以必须再看一眼。
    LaunchedEffect(reloads, identity) {
        catalogLoading = true
        catalog = repo.catalog()?.toCatalogModels().orEmpty()
        catalogLoading = false
    }

    LaunchedEffect(profile, reloads, planScope, identity) {
        when (val scope = planScope) {
            PlanScope.All -> {
                // 上一份先画出来再问后端：「全部机型」是默认范围，这一页永远用它打开，没缓存就等于
                // 每次进来都要盯着加载符等一圈网络。排行和方案读的是同一份，所以两页一起受益。
                if (plans.isEmpty()) {
                    planCache?.readAll()?.takeIf { it.isNotEmpty() }?.let { cached ->
                        plans = cached.withoutDuplicates()
                    }
                }
                loading = plans.isEmpty()
                offline = false
                var fetched: List<CommunityPlan>? = null
                for (attempt in 0 until PLAN_ATTEMPTS) {
                    val result = repo.plansAll()
                    if (result != null) {
                        fetched = result
                        break
                    }
                    if (attempt < PLAN_ATTEMPTS - 1) delay(PLAN_RETRY_DELAYS[attempt.coerceIn(0, PLAN_RETRY_DELAYS.lastIndex)])
                }
                offline = fetched == null
                // 拉不到就保持屏幕上那一份：清空等于因为一次断网把整页排行抹掉，而「后端没通」和
                // 「社区里没有方案」在界面上长得一样，区别只剩下面那个重试提示。
                if (fetched != null) {
                    val clean = fetched.withoutDuplicates()
                    // 内容没变就什么都不做：屏幕上已经是这一份，重写一遍只是白写一次磁盘。
                    // 有变化才换掉画面、写回缓存——这就是「没更新用缓存，更新了就加载更新」。
                    if (clean != plans) {
                        plans = clean
                        persistPlans(clean)
                    }
                }
                loading = false
            }

            PlanScope.ThisPhone -> {
                val target = profile
                if (target == null) {
                    loading = false
                    return@LaunchedEffect
                }
                // Whatever was cached shows immediately, so a page opened right after a reboot is
                // not empty while the request is in flight.
                if (plans.isEmpty()) {
                    planCache?.read(target)?.takeIf { it.isNotEmpty() }?.let { cached ->
                        plans = cached.withoutDuplicates()
                    }
                }
                loading = plans.isEmpty()
                var fetched: List<CommunityPlan>? = null
                for (attempt in 0 until PLAN_ATTEMPTS) {
                    val result = repo.plansFor(target)
                    if (result.isNotEmpty()) {
                        fetched = result
                        break
                    }
                    if (attempt < PLAN_ATTEMPTS - 1) delay(PLAN_RETRY_DELAYS[attempt.coerceIn(0, PLAN_RETRY_DELAYS.lastIndex)])
                }
                offline = fetched == null
                // Nothing fetched keeps what is on screen — the cache is the list in that case.
                if (fetched != null) {
                    val clean = fetched.withoutDuplicates()
                    if (clean != plans) {
                        plans = clean
                        persistPlans(clean)
                    }
                }
                loading = false
            }

            is PlanScope.Other -> {
                // Another model's list is never cached: the cache belongs to this phone's own build,
                // and a list from another model shown as "yours" after a reboot would be a lie.
                loading = true
                offline = false
                var fetched: List<CommunityPlan>? = null
                for (attempt in 0 until PLAN_ATTEMPTS) {
                    val result = repo.plansForModel(
                        brand = scope.model.brand,
                        model = scope.model.model,
                        rom = scope.build?.rom.orEmpty(),
                        romVersion = scope.build?.romVersion ?: 0,
                        romBuild = scope.build?.romBuild.orEmpty(),
                    )
                    if (result != null) {
                        fetched = result
                        break
                    }
                    if (attempt < PLAN_ATTEMPTS - 1) delay(PLAN_RETRY_DELAYS[attempt.coerceIn(0, PLAN_RETRY_DELAYS.lastIndex)])
                }
                offline = fetched == null
                plans = fetched.orEmpty().withoutDuplicates().map { plan ->
                    // A backend without the device columns sends no model: the scope already says
                    // which phone these rows belong to, and the warning about using them needs it.
                    if (plan.model.isNotBlank()) {
                        plan
                    } else {
                        plan.copy(
                            brand = scope.model.brand,
                            model = scope.model.model,
                            marketName = scope.model.marketName,
                            rom = scope.build?.rom ?: plan.rom,
                            romVersion = scope.build?.romVersion ?: plan.romVersion,
                            romBuild = scope.build?.romBuild ?: plan.romBuild,
                        )
                    }
                }
                loading = false
            }
        }
    }

    // What a plan may contain: everything this phone no longer has, plus whatever is ticked right
    // now. The sheet decides which of them may actually go up — only the reboot-checked ones.
    val candidates = (uninstalledApps + tickedApps).distinctBy { it.packageName }
    val noProfileHint = "还没有读到机型信息，稍等一下再试。"
    // The way in is closed while nothing passed the check: the button then says the order of the
    // work instead of opening a sheet that could only say "nothing to upload yet".
    val canUpload = confirmed > 0
    val blockedHint = stringResource(Res.string.plans_upload_gate_none)
    // The preflight: one call, short timeout, and the answer is kept so the sheet can refuse to
    // start an upload it knows cannot leave the phone. Retried from the sheet when it failed.
    val checkReach: () -> Unit = {
        if (!reaching) {
            scope.launch {
                reaching = true
                reach = repo.isReachable()
                reaching = false
            }
        }
    }
    // One way in, and the sheet is where the order of the work is stated: remove, restart, pass the
    // check, then (and only then) name the plan. The button does not pre-empt that with a question.
    val openUpload: () -> Unit = {
        notice = null
        if (profile == null) {
            notice = Notice(noProfileHint, error = true)
        } else {
            upload = UploadRequest(
                candidates = candidates,
                // Nothing is ticked by default except what passed the boot check: an unverified
                // package is listed to be explained, never to be part of the plan.
                preselected = verifiedPackages,
            )
            // The sheet opens straight away — a probe must not make the button feel dead — and the
            // reachability check runs beside it, so a phone with no route to the backend is told
            // before it fills in a name instead of after an upload that silently does nothing.
            reach = null
            checkReach()
        }
    }
    val vote: (CommunityPlan) -> Unit = { plan ->
        if (plan.id !in liked) {
            liked = liked + plan.id
            scope.launch {
                val votes = repo.vote(
                    plan.id,
                    owner.ifEmpty { "u-" + Random.nextLong().toString(16) },
                ) ?: return@launch
                plans = plans.map { if (it.id == plan.id) it.copy(votes = votes) else it }
                // 这一票也要进缓存：不然退出再进来，先画出来的还是投票之前那一份票数。
                persistPlans(plans)
            }
        }
    }

    val ownLabel = profile?.displayModel.orEmpty()
    // One place that changes the phone on screen: the sheet and the build capsules both go
    // through it, so the list is never left showing another scope's rows for a frame.
    //
    // 现在只写设置这一处：`planScope` 由 `storedScope` 派生，清列表与"加载中"由上面那个
    // `LaunchedEffect(planScope)` 做——同一次动作只有一条写入路径，不会出现"点了没生效"或者
    // 中间拿旧数据顶着新名字的一帧。
    val pickScope: (PlanScope) -> Unit = { picked ->
        if (picked != planScope) onScopeRemember(picked.toStoredValue())
    }
    val opened = planScope as? PlanScope.Other
    val allModels = planScope == PlanScope.All
    // The band names whatever the list below is about. This phone keeps the wording it had; an
    // opened model says so, because a plan read here may be for a phone that is not in hand.
    val heroTitle = when {
        opened != null -> opened.model.label
        allModels -> PlanScope.All.label(ownLabel)
        showDevicePhoto -> ownLabel.ifBlank { "-" }
        else -> "本机"
    }
    val heroSubtitle = when {
        opened != null -> "他机方案 · " + (opened.build?.versionLabel ?: "全部版本")
        allModels -> "全社区已验证方案 · 不分机型"
        else -> profile?.let { romLine(it) }.orEmpty()
    }
    // What the empty states and the ranking call the device: the model on screen, or "本机" when
    // the name is being kept off the page.
    val listLabel = when {
        opened != null -> opened.model.label
        allModels -> PlanScope.All.label(ownLabel)
        else -> ownLabel.ifBlank { "本机" }
    }

    Scaffold(
        topBar = {
            // The phone is the header: its name and counters on the band, the page switch right
            // under them, and the picture of the model below the switch.
            // The switch covers the name as well as the picture: with it off the page says
            // "本机" and looks nothing up, which is the point of having the switch.
            DeviceHero(
                // No phone on the band when every model is on screen: a picture of this one under
                // the words "全部机型" would read as if the list were about it.
                model = if (showDevicePhoto && !allModels) opened?.model?.label ?: ownLabel else "",
                title = heroTitle,
                subtitle = heroSubtitle,
                showPhoto = showDevicePhoto && !allModels,
                // The name is the way to another phone: it is drawn as a capsule that says
                // "切换", and that capsule — not the whole heading — is what opens the chips.
                onTitleClick = {
                    pickerOpen = true
                    // The first tap answers the hint: stop breathing, and remember it across starts.
                    if (!scopeHintSeen) scope.launch { settings.setScopeHintSeen(true) }
                },
                // Breathes only while the reader has never opened the picker.
                pulseTitleHint = !scopeHintSeen,
                // No plan count on the band: the switch under it already says which tab is
                // which, and a counter there only squeezed the model's system line.
                // 「上传本机方案」的图标按钮贴着计数器：群里有人找半天没找到分享入口，因为它原来只在
                // 「本机」范围下才出现在右下角。现在它常驻顶端，任何范围下都在。
                countersLeading = {
                    IconButton(
                        onClick = openUpload,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = stringResource(Res.string.plans_upload_cta),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                counters = buildList {
                    // The reboot proof is this install's own: it means nothing under another model,
                    // and under "全部机型" it would be read as if the whole community were counted.
                    val pending = deletedPackages.size
                    if (pending > 0 && opened == null && !allModels) {
                        add("$confirmed/$pending" to stringResource(Res.string.plans_verified_short))
                        if (awaitingFirstReboot > 0) {
                            add("$awaitingFirstReboot" to "待首次重启验证")
                        }
                    }
                },
                footer = {
                    FlSegmentedControl(
                        segments = listOf(
                            FlSegment("apps", stringResource(Res.string.safety_tab_apps)),
                            FlSegment("plans", stringResource(Res.string.safety_tab_plans)),
                        ),
                        selectedIndex = tab,
                        onSelect = { tab = it },
                        // The switch is on the page band now, not on the photo: it takes the
                        // ordinary groove colours and the ordinary side inset.
                        insetHorizontal = 16.dp,
                    )
                    Spacer(Modifier.height(4.dp))
                    PlanScopeBar(
                        scope = planScope,
                        ownLabel = ownLabel,
                        onOpen = { pickerOpen = true },
                        // The builds are capsules of their own: picking one changes the list on the
                        // spot instead of making the reader open the sheet for a value the bar can
                        // already show.
                        onSelectBuild = { build ->
                            (planScope as? PlanScope.Other)?.let { open ->
                                pickScope(PlanScope.Other(open.model, build))
                            }
                        },
                        // On the switch bar because that is where the list it filters is read from,
                        // and because the switch itself already owns the taps on the chips.
                        searching = searchPackages,
                        onSearch = {
                            searchPackages = !searchPackages
                            if (!searchPackages) packageQuery = ""
                        },
                    )
                },
            )
        },
        floatingActionButton = {
            // 方案页就能发布，跟范围无关：以前只有切到「本机」才出现这个按钮，结果是大部分人
            // 根本找不到分享入口。范围只决定列表里是谁的机型，上传的永远是本机这一份。
            if (tab == 1) {
                Column(horizontalAlignment = Alignment.End) {
                    ExtendedFloatingActionButton(
                        onClick = openUpload,
                        // The page's own floating dock sits at the bottom centre: the button has to
                        // stand above it or it lands under a bar it cannot be tapped through.
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(bottom = DOCK_CLEARANCE),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(Res.string.plans_upload_cta),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                    )
                    // Nothing passed the check: the line under the button is the whole explanation,
                    // so the tap is not wasted on a sheet that has nothing to offer yet.
                    if (!canUpload) {
                        Text(
                            text = blockedHint,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(top = 2.dp, end = 4.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // 点列表空白：收起包名筛选框的焦点与输入法。
                .dismissInputOnTap(),
        ) {
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
            if (searchPackages) {
                PackageSearchField(
                    query = packageQuery,
                    onQueryChange = { packageQuery = it },
                    // The list is filtered as the reader types, so leaving is one tap on the ×.
                    onClose = {
                        searchPackages = false
                        packageQuery = ""
                    },
                )
            }
            if (tab == 0) {
                RankingTab(
                    plans = plans,
                    loading = loading,
                    apps = apps,
                    rules = rules,
                    profile = profile,
                    deviceLabel = listLabel,
                    otherDevice = opened != null,
                    offline = offline,
                    query = packageQuery,
                    // 位置记忆按「哪个范围」分开存：本机、全部机型、某一台他机，三者的列表不是同一份，
                    // 共用一个键会让从应用详情返回时落到另一份列表的第 N 行。
                    page = "ranking:" + planScope.memoryKey(),
                    onOpenApp = onOpenApp,
                    onRetry = { reloads++ },
                )
            } else {
                PlansTab(
                    plans = plans,
                    loading = loading,
                    profile = profile,
                    deviceLabel = listLabel,
                    owner = owner,
                    liked = liked,
                    offline = offline,
                    widerScope = opened != null || allModels,
                    query = packageQuery,
                    page = "plans:" + planScope.memoryKey(),
                    onOpenPlan = onOpenPlan,
                    onVote = vote,
                    onDelete = { pendingDelete = it },
                    onRetry = { reloads++ },
                )
            }
        }
    }

    if (pickerOpen) {
        PlanScopeDialog(
            models = catalog,
            ownLabel = ownLabel,
            loading = catalogLoading,
            scope = planScope,
            onSelect = { picked ->
                pickerOpen = false
                pickScope(picked)
            },
            onRetry = { reloads++ },
            onDismiss = { pickerOpen = false },
        )
    }

    upload?.let { request ->
        UploadPlanDialog(
            candidates = request.candidates,
            preselected = request.preselected,
            verified = verifiedPackages,
            profile = profile,
            publishing = uploading,
            reachable = reach,
            checking = reaching,
            onRecheck = checkReach,
            // 「最精简」那一格按本机规则挑包：规则还没读到时它是灰的，不会凭空勾一堆。
            rules = rules,
            onDismiss = { if (!uploading) upload = null },
            onSubmit = { title, note, packages ->
                val target = profile
                // The sheet only hands over packages that passed the boot check, so the upload is
                // a verified one by construction: there is no second way in any more.
                if (target != null && packages.isNotEmpty()) {
                    scope.launch {
                        uploading = true
                        // Preflight: the backend is not reachable from every network (many carriers
                        // and campus Wi-Fis need a VPN or a proxy for it), so the upload is not
                        // attempted blind. Checking first turns a silent failure into a line that
                        // says what to do about it.
                        if (!repo.isReachable()) {
                            uploading = false
                            upload = null
                            notice = Notice(getString(Res.string.plan_upload_offline), error = true)
                            return@launch
                        }
                        // Uploading the same set again is a retry, not a second plan: the earlier
                        // copy is removed first, so the list keeps one row per package set.
                        repo.deleteOwnDuplicates(target, owner, identity.token, packages)
                        val id = repo.submit(
                            profile = target,
                            title = title,
                            note = note,
                            packages = packages,
                            rebootedAt = Clock.System.now().toString(),
                            reporter = owner,
                            owner = owner,
                        )
                        uploading = false
                        upload = null
                        notice = if (id != null) {
                            Notice(getString(Res.string.plan_upload_ok_detail))
                        } else {
                            // A call that never arrived and a call the server refused are different
                            // problems: only the first one is about the connection, and telling them
                            // apart is what keeps the line from being a generic "上传失败".
                            val reason = repo.lastError
                            Notice(
                                text = when {
                                    repo.lastFailureWasNetwork ->
                                        getString(Res.string.plan_upload_failed_network)
                                    reason.isNullOrBlank() ->
                                        getString(Res.string.plan_upload_fail)
                                    else ->
                                        getString(Res.string.plan_upload_fail) + " · " + reason
                                },
                                error = true,
                            )
                        }
                        // Re-read either way: a failed upload simply leaves the list as it was.
                        // 发布的是本机这一份：范围还停在「他机 / 全部机型」时，先把范围切回本机，
                        // 否则下面那句 plansFor(target) 会把屏幕上的名单换成自己的，标题却还写着
                        // 别人的机型。切范围本身会触发一次本机列表的重新读取。
                        if (planScope == PlanScope.ThisPhone) {
                            val refreshed = repo.plansFor(target).withoutDuplicates()
                            plans = refreshed
                            if (refreshed.isNotEmpty()) persistPlans(refreshed)
                        } else {
                            // 只写设置：列表清空与"加载中"由 `LaunchedEffect(planScope)` 负责。
                            onScopeRemember(PlanScope.ThisPhone.toStoredValue())
                        }
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
                        scope.launch {
                            val ok = repo.delete(plan.id, owner, identity.token)
                            notice = if (ok) {
                                null
                            } else {
                                Notice("删除失败：这个方案不是本机上传的，或者后端没通。", error = true)
                            }
                            // Re-read the same list that is on screen: under "全部机型" that is every
                            // model's, and reloading this phone's instead would silently narrow it.
                            val refreshed = when (val current = planScope) {
                                PlanScope.All -> repo.plansAll()
                                PlanScope.ThisPhone -> profile?.let { repo.plansFor(it) }
                                is PlanScope.Other -> repo.plansForModel(
                                    brand = current.model.brand,
                                    model = current.model.model,
                                    rom = current.build?.rom.orEmpty(),
                                    romVersion = current.build?.romVersion ?: 0,
                                    romBuild = current.build?.romBuild.orEmpty(),
                                )
                            }
                            // 删掉的那一行也得从缓存里消失，否则下次进来会先把它画回来再刷掉。
                            // 请求没到（null）就别动缓存：那次什么都没删成，屏幕上也还是原来那一份。
                            if (refreshed != null) {
                                plans = refreshed.withoutDuplicates()
                                persistPlans(plans)
                            }
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

/** "HyperOS 16.0.3 · Android 16", without the doubled space a stock ROM would leave behind. */
private fun romLine(profile: DeviceProfile): String {
    val romPart = romLabelOf(profile.rom, profile.romVersion, profile.romBuild)
    return if (romPart == "Android") {
        "Android ${profile.release}"
    } else {
        "$romPart · Android ${profile.release}"
    }
}

/**
 * The package filter of the list under the switch: it searches what the rows are, not which phone
 * they belong to. The list filters as the reader types — there is no submit step and no separate
 * result page — so the only control next to the field is the way out.
 */
@Composable
private fun PackageSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(scheme.surfaceContainerHighest)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = scheme.onSurfaceVariant,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                .focusRequester(focus),
            textStyle = TextStyle(fontSize = 12.sp, color = scheme.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        // The rows print the package under the app's name, so this is the string
                        // people already have in front of them.
                        text = "搜索包名，如 com.miui.daemon",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭搜索",
                modifier = Modifier.size(16.dp),
                tint = scheme.onSurfaceVariant,
            )
        }
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
    deviceLabel: String,
    /** True when the list belongs to another model: the verdicts below are this phone's, not its. */
    otherDevice: Boolean,
    offline: Boolean,
    /** Package filter from the switch bar: matched against the package name and the app's label. */
    query: String = "",
    /** 位置记忆的键：这一份列表是哪个范围的（见 [rememberRememberedListState]）。 */
    page: String = "ranking",
    onOpenApp: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val ranking = remember(plans) { rankPackages(plans) }
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    // What this phone no longer has, asked of the app list rather than of the plans: the ranking is
    // about what should go, so the rows that already went have to be told apart from the ones that
    // are still here — and that verdict belongs to this phone, not to the model the list is about.
    val removed = remember(apps) { apps.filter { it.isUninstalled }.mapTo(mutableSetOf()) { it.packageName } }
    val needle = query.trim()
    val rows = if (needle.isEmpty()) {
        ranking
    } else {
        // A filter over the ranking keeps the vote order it was ranked in: a search narrows the
        // list, it does not re-rank it.
        ranking.filter { row ->
            row.pkg.contains(needle, ignoreCase = true) ||
                byPackage[row.pkg]?.name.orEmpty().contains(needle, ignoreCase = true)
        }
    }

    // 位置带记忆（见 [rememberRememberedListState]）：点进应用详情再返回、冷启动再进来，都还停在
    // 原来那几行。裸的 `rememberLazyListState` 只能在同一个组合里活着——详情页是导航栈里的新页面，
    // 返回时这一页被重新组合，`remember` 跟着一起没了，列表从第一行开始，看上去就是「点进去返回，
    // 顺序跳到下面去了」。
    val listState = rememberRememberedListState(page = page)
    // 过滤词变了（包名筛选）要先回顶部：换的是「这一份列表是什么」，不是滚动位置。
    LaunchedEffect(needle) {
        if (needle.isNotEmpty()) listState.scrollToItem(0)
    }

    when {
        loading -> Text(
            "…",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
        ranking.isEmpty() -> EmptyCard(
            title = stringResource(Res.string.safety_tab_apps),
            body = stringResource(Res.string.ranking_empty, deviceLabel.ifBlank { "-" }),
            onRetry = onRetry,
            retryHint = offline,
        )
        rows.isEmpty() -> Text(
            text = "这个机型下没有包名包含「$needle」的方案。",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            itemsIndexed(rows, key = { _, row -> row.pkg }) { _, row ->
                RankRowView(
                    rank = row,
                    app = byPackage[row.pkg],
                    // The device rules describe packages on *this* phone: under another model their
                    // answer would be somebody else's build wearing this one's name.
                    verdict = if (otherDevice) null else rules?.verdict(row.pkg, profile),
                    otherDevice = otherDevice,
                    // Whether it is gone is not the same question as the rules' verdict, and it is
                    // true under any scope: the phone in hand is the one that did the removing.
                    removed = row.pkg in removed,
                    onClick = { onOpenApp(row.pkg) },
                )
            }
        }
    }
}

/** One ranking row: position, icon, what it is, and how much the community likes removing it. */
@Composable
private fun RankRowView(
    rank: PackageRank,
    app: AppInfo?,
    verdict: SafetyVerdict?,
    otherDevice: Boolean = false,
    /** True when this phone no longer has the package: its package line is painted red. */
    removed: Boolean = false,
    onClick: () -> Unit = {},
) {
    // Only the package line changes colour, not the app's name: the name is how the row is read,
    // while the package name is the thing that actually went. Red on that line is the signal, and
    // it is red rather than a tint of the accent so it cannot be mistaken for the chip on the right.
    val goneColor = MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 3.dp),
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
                // Full strength, not a faded tint: on a list of grey package names this line is the
                // only thing saying the package is no longer on the phone.
                color = if (removed) goneColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // One number, one line: how many people marked it. How many plans it was marked in is a
        // detail for the package's own page, where there is room to read it next to the plans.
        Text(
            // A mark in this list means "this can go", not "this is good".
            text = stringResource(Res.string.ranking_marks, rank.votes),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        StatusChip(verdict, otherDevice)
    }
}

/** The position of a package in the ranking. */
@Composable
private fun RankBadge(rank: PackageRank) = RankBadge(position = rank.rank)

/**
 * The position, painted differently on the podium so the top three read at a glance. Plans are
 * numbered by the same rule, so the badge is drawn from a plain position too.
 */
@Composable
private fun RankBadge(position: Int) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (position) {
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
            text = "$position",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = content,
        )
    }
}

@Composable
private fun StatusChip(verdict: SafetyVerdict?, otherDevice: Boolean = false) {
    val (label, color) = when {
        // Nothing is known about this package *on that phone*: saying "未知" would read as "the
        // rules do not cover it", while the truth is that the rules were not asked.
        otherDevice -> "他机" to MaterialTheme.colorScheme.outline
        verdict?.status == SafetyStatus.CRITICAL -> "禁区" to MaterialTheme.colorScheme.error
        verdict?.status == SafetyStatus.RISKY -> "有代价" to MaterialTheme.colorScheme.tertiary
        verdict?.status == SafetyStatus.SAFE -> "可卸" to MaterialTheme.colorScheme.primary
        else -> "未知" to MaterialTheme.colorScheme.outline
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
 * The plans of this phone, most recommended first. Publishing is not a section of this list: it is
 * the floating button the page carries, and the dialog it opens is where the conditions live.
 */
@Composable
private fun PlansTab(
    plans: List<CommunityPlan>,
    loading: Boolean,
    profile: DeviceProfile?,
    deviceLabel: String,
    owner: String,
    liked: Set<Long>,
    offline: Boolean,
    /** True when the list is not this phone's: uploading only ever happens for the phone in hand. */
    widerScope: Boolean = false,
    /** Package filter from the switch bar: a plan is kept when it holds a package that matches. */
    query: String = "",
    /** 位置记忆的键：这一份列表是哪个范围的（见 [rememberRememberedListState]）。 */
    page: String = "plans",
    onOpenPlan: (Long) -> Unit,
    onVote: (CommunityPlan) -> Unit,
    onDelete: (CommunityPlan) -> Unit,
    onRetry: () -> Unit,
) {
    val needle = query.trim()
    val shown = if (needle.isEmpty()) {
        plans
    } else {
        plans.filter { plan -> plan.packages.any { it.contains(needle, ignoreCase = true) } }
    }
    // 位置带记忆：点进方案详情 / 应用详情再返回，列表还停在原来那几张卡片上。这一屏尤其明显——
    // 它的分支是「加载中 → 空 → 列表」，列表本来就比别的东西晚一两帧出现，没有记忆就等于每次回来
    // 都从第一张读起（而且 `plans` 还会被重新请求换一遍）。
    //
    // 调这个 Composable 是强制的：列表处在分支里，一旦现在不是「有内容」那一支，整段列表连同它的
    // 状态会被销毁、再按新的一份重建，位置当场丢光——**这一行必须留在 `when` 前面**。
    val plansListState = rememberRememberedListState(page = page)
    // 过滤词变了（包名筛选）要先回顶部：换的是「这一份列表是什么」，不是滚动位置。
    LaunchedEffect(needle) {
        if (needle.isNotEmpty()) plansListState.scrollToItem(0)
    }
    when {
        loading -> Text(
            "…",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
        plans.isEmpty() -> EmptyCard(
            title = stringResource(Res.string.plans_empty_title, deviceLabel.ifBlank { "-" }),
            body = stringResource(Res.string.plans_empty_body),
            onRetry = onRetry,
            retryHint = offline,
        )
        shown.isEmpty() -> Text(
            text = "这个范围内没有包含「$needle」的方案。",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
        else -> Column(modifier = Modifier.fillMaxSize()) {
            // 上传入口现在跟范围无关、常驻在页面顶端，所以这一行只解释列表是谁的（以及上传的是谁的）。
            if (widerScope) {
                Text(
                    text = "这是全部机型共用的方案，按社区点赞排序；上传的一直是本机这一份。",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp),
                )
            }
            LazyColumn(
                state = plansListState,
                modifier = Modifier.fillMaxSize(),
                // The floating button and the dock hang over the foot of the list: the last card
                // has to be able to scroll clear of both.
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 6.dp,
                    bottom = DOCK_CLEARANCE + 56.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(shown, key = { _, plan -> plan.id }) { index, plan ->
                    PlanCard(
                        plan = plan,
                        mine = owner.isNotEmpty() && plan.owner == owner,
                        // A plan for another model is still readable, but the card has to say so: the
                        // selection it applies is this phone's, not the one it was proven on.
                        foreign = !plan.matches(profile),
                        // The position *is* the recommendation: the badge on the left says who the
                        // community trusts most, exactly like the package ranking does.
                        rank = index + 1,
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
 * One plan in the community ranking: its position, its title, what it holds, who uploaded it, and
 * how much people like it. The uploader is a name here — the avatar belongs to the plan's own page,
 * where there is room for it.
 */
@Composable
private fun PlanCard(
    plan: CommunityPlan,
    mine: Boolean,
    foreign: Boolean,
    rank: Int?,
    liked: Boolean,
    onOpen: () -> Unit,
    onVote: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val author = plan.authorName

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The rank leads: the position itself is the recommendation.
                if (rank != null) {
                    RankBadge(position = rank)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = plan.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // The test mark goes with the numbers on the right: it qualifies the plan, it is
                // not part of its name.
                if (plan.isTest) {
                    Spacer(Modifier.width(6.dp))
                    TestChip()
                }
                if (foreign) {
                    Spacer(Modifier.width(6.dp))
                    ForeignChip()
                }
                if (mine) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colors.error,
                        )
                    }
                }
            }
            if (plan.note.isNotEmpty()) {
                Text(
                    text = plan.note,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = colors.onSurfaceVariant,
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
                    // "12 包 · 3 评论 · 2026-11-18 · @李雷（Redmi K70）": the author closes the line,
                    // so who wrote it reads after when it was written — and the phone follows the
                    // name, because "does this plan fit mine?" is the question the row is read for,
                    // and a name alone does not answer it.
                    //
                    // The model is the marketing name ("Redmi K70"), never the code the ROM reports
                    // in `ro.product.model` ("23113RKC6C"): nobody recognises the code, and it is
                    // what the uploader's own settings show as blank on the older rows.
                    text = buildString {
                        append("${plan.packages.size} 包 · ${plan.comments} 评论 · ${plan.rebootedAt.take(10)}")
                        if (author.isNotEmpty()) append(" · @$author")
                        val phone = plan.marketName.ifBlank { plan.model }
                        if (phone.isNotBlank()) append("（$phone）")
                    },
                    fontSize = 9.sp,
                    color = colors.outline,
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

/**
 * The "another model" mark: the plan is readable, its device line is not this phone's, and using it
 * will not be blocked — the mark is the warning, not a lock.
 */
@Composable
private fun ForeignChip() {
    Text(
        text = "他机方案",
        fontSize = 9.sp,
        lineHeight = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
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

/** An empty list still has to say why it is empty — and, when nothing loaded, offer another try. */
@Composable
private fun EmptyCard(
    title: String,
    body: String,
    onRetry: (() -> Unit)? = null,
    retryHint: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 20.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = body,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (onRetry != null) {
            Text(
                text = stringResource(
                    if (retryHint) Res.string.plans_reload_failed else Res.string.plans_reload,
                ),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
