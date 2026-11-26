package io.github.samolego.canta.ui.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.samolego.canta.CANTA_PACKAGE_NAME
import io.github.samolego.canta.BuildFlags
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.core.explainFailure
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.app.AppListSnapshotStore
import io.github.samolego.canta.data.app.Filter
import io.github.samolego.canta.data.app.PackageSnapshot
import io.github.samolego.canta.data.bloat.BloatData
import io.github.samolego.canta.data.bloat.BloatRepository
import io.github.samolego.canta.data.history.BatchRollback
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.data.history.OperationLogStore
import io.github.samolego.canta.data.history.OperationRecord
import io.github.samolego.canta.data.history.ResumeCandidate
import io.github.samolego.canta.data.history.resumable
import io.github.samolego.canta.data.history.resumeCandidate
import io.github.samolego.canta.packages.OperationResult
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.canta_description
import io.github.samolego.canta.ui.AppsType
import io.github.samolego.canta.ui.operationKind
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.CommunityScore
import io.github.samolego.canta.util.DeviceContext
import io.github.samolego.canta.util.fakeScores
import io.github.samolego.canta.util.FailureDetail
import io.github.samolego.canta.util.PackageTelemetry
import io.github.samolego.canta.util.currentTimeMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/** The app list: loading apps and their bloat info, filtering, selection and (un)installing. */
class AppListViewModel(
    private val cantaHandler: CantaHandler,
    private val bloatRepository: BloatRepository,
    /** Opt-in analytics; null when there is none (tests). */
    private val telemetry: PackageTelemetry? = null,
    /** The record of what was removed and put back; null when there is none (tests). */
    private val history: OperationLogStore? = null,
    /** Where an interrupted batch is remembered so it can be finished later; null in tests. */
    private val settings: CantaSettings? = null,
    /**
     * 上一次扫出来的包列表（见 [AppListSnapshotStore]）。
     *
     * 有它，进列表页的第一帧就是上次那张表；没有（测试、第一次装）只是照旧等一次扫描。
     */
    private val snapshot: AppListSnapshotStore? = null,
) : ViewModel() {

    companion object {
        private const val TAG = "AppListViewModel"
        /** Apply streamed icons after this many arrive... */
        private const val ICON_APPLY_BATCH = 20
        /** ...or after this long, whichever comes first. */
        private const val ICON_APPLY_INTERVAL_MS = 100L
        private const val UAD_CREDIT =
            "Universal Debloater Alliance (https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation)"
    }

    private val _apps = mutableStateListOf<AppInfo>()

    private val _scores = MutableStateFlow<Map<String, CommunityScore>>(emptyMap())

    /** Community scores by package name; empty while analytics is off. Shown next to, never instead of, the bloat info. */
    val scores: StateFlow<Map<String, CommunityScore>> = _scores

    /** What the server said last (or nothing, with analytics off); shown whenever fake scores are off. */
    private var realScores: Map<String, CommunityScore> = emptyMap()

    private val _fakeScores = MutableStateFlow(false)

    /** Debug only, never persisted: show made-up scores instead of fetching any. */
    val fakeScoresEnabled: StateFlow<Boolean> = _fakeScores

    fun setFakeScores(enabled: Boolean) {
        // Fake scores only exist in debug builds.
        _fakeScores.value = enabled && BuildFlags.IS_DEBUG
        viewModelScope.launch { applyScores() }
    }

    private var analyticsOn = false

    /** The device the [realScores] were fetched for; they are fetched again once a different one is known. */
    private var scoresFor: DeviceContext? = null

    private suspend fun fetchRealScores(telemetry: PackageTelemetry) {
        scoresFor = telemetry.deviceContext()
        realScores = telemetry.fetchScores().orEmpty()
        applyScores()
    }

    /** A device may only become known after the start (ADB targets connect later): then per-ROM scores are due. */
    private fun refreshScoresForDevice() {
        val telemetry = telemetry ?: return
        viewModelScope.launch {
            val device = telemetry.deviceContext()
            if (analyticsOn && device != null && device != scoresFor) fetchRealScores(telemetry)
        }
    }

    private suspend fun applyScores() {
        _scores.value = if (_fakeScores.value) fakeScores(_apps.toList(), telemetry?.deviceContext()) else realScores
    }

    /** Whether the pending-reboot check has run since the app started. */
    private var survivorsChecked = false

    init {
        // 上次扫出来的那份先摆上：进列表页不该是空白等扫描，而是「先看见上次那张表，稍后就地刷新」。
        // 不能碰 `_apps` 之外的东西——这一段跑在构造里，那时 handler 还没扫过任何东西。
        snapshot?.read()?.takeIf { it.isNotEmpty() }?.let { cached ->
            _apps.addAll(cached.map { AppInfo(it) })
            LogUtils.i(TAG, "Seeded ${cached.size} apps from the local snapshot")
        }
        telemetry?.let { telemetry ->
            // Fetched when analytics is on at start (or gets turned on), cleared when it is turned off.
            viewModelScope.launch {
                telemetry.analyticsEnabled.collectLatest { enabled ->
                    analyticsOn = enabled
                    if (enabled) fetchRealScores(telemetry) else {
                        realScores = emptyMap()
                        scoresFor = null
                        applyScores()
                    }
                }
            }
        }
    }

    /** All apps, regardless of search and filters. */
    val apps: List<AppInfo> get() = _apps

    /** 这台手机的型号 / ROM 一句话，备份时记下来，换手机后看列表能认出是哪台机器。 */
    suspend fun deviceLabel(): String =
        runCatching { cantaHandler.deviceProfile()?.shortLabel() }.getOrNull().orEmpty()

    val selectedApps = mutableStateSetOf<String>()

    var searchQuery by mutableStateOf("")
    var onlySystem by mutableStateOf(true)
    var isLoading by mutableStateOf(false)
        private set

    /**
     * The ROM hides the package list until its own permission is granted (HyperOS/MIUI). The list
     * screen shows a banner with the way to that switch; dismissed for this run only.
     */
    var appListRestricted by mutableStateOf(false)
        private set

    /** Whether the "the system hides the app list" dialog was already shown in this run. */
    private var noticeShown = false

    /**
     * True when this load ran without a privileged runtime, so the list is only what the ROM lets
     * the app see: a phone with 400 packages can come back with 60 and nothing complains. The tabs
     * then look empty for no visible reason, which reads as a bug in the app rather than as "the
     * runtime is not running" (see [AppsTab] and the banner in the list).
     */
    var listIncomplete by mutableStateOf(false)
        private set

    /** True the first time a restricted list is loaded: the screen then asks for the permission. */
    var askForAppListPermission by mutableStateOf(false)
        private set

    fun onAppListPermissionPrompted() {
        askForAppListPermission = false
        noticeShown = true
    }

    /** Opens the ROM's package-list permission screen, if this platform has one. */
    fun openPackageListSettings() {
        cantaHandler.openPackageListSettings()
    }

    fun dismissAppListNotice() {
        appListRestricted = false
    }
    var isLoadingBadges by mutableStateOf(false)
        private set

    /**
     * 按危险级别 / 用户应用这些**分类**筛。默认 [Filter.any]（不筛）。
     *
     * 它和 [onlySystem] 是两个维度，同时生效：前者按徽标挑，后者按"是否系统应用"挑。
     */
    var selectedFilter by mutableStateOf(Filter.any)

    val selectedAppsSorted by derivedStateOf {
        sortedList.filter { it.packageName in selectedApps }
    }

    private val sortedList by derivedStateOf {
        _apps.filter { selectedFilter.shouldShow(it) }
            .sortedWith(compareBy { it.name.lowercase() })
    }

    /**
     * The apps matching the search query, the category filter and the system switch.
     *
     * 顶栏那个按钮先管「只看系统 / 全部」，再点一次换成按分类筛（推荐卸载、进阶、专家、
     * 有风险、未分类）；两者可以同时生效，[Filter.any] 加上"全部"就是最宽的列表。
     */
    val appList by derivedStateOf {
        sortedList
            .filter {
                it.name.contains(searchQuery, true) ||
                    it.packageName.contains(searchQuery, true)
            }
            .filter { it.isSystemApp || !onlySystem }
    }

    private var loadJob: Job? = null

    /** Icons received so far; kept across reloads, since icons rarely change. */
    private val iconCache = mutableMapOf<String, ImageBitmap>()

    /**
     * (Re)loads all apps, cancelling a load that is still running. The returned job completes once
     * apps, the local badges and the streamed icons are loaded; the once-a-day bloat-list check runs
     * behind it (see [startBadgeRefresh]) and is not something the caller has to wait for.
     */
    fun loadApps(): Job {
        loadJob?.cancel()
        badgeRefreshJob?.cancel()
        return viewModelScope.launch { load() }.also { loadJob = it }
    }

    /**
     * The background check of the bloat list, if one is running. Kept apart from [loadJob] because
     * it must not hold the list hostage: the page is already usable when it starts.
     */
    private var badgeRefreshJob: Job? = null

    private suspend fun load() = coroutineScope {
        isLoading = true
        isLoadingBadges = true
        // Whether the badges on screen came from the local copy; the finally block reads it to decide
        // if the "loading badges" bar still has anything to say.
        var badgesFromLocal = false
        // Whether the background check was actually started; when the load throws before reaching it
        // there is nothing that would ever clear the flag, so the finally does it instead.
        var refreshStarted = false
        try {
            val start = currentTimeMillis()
            val all = cantaHandler.loadApps().distinctBy { it.packageName }
            LogUtils.i(TAG, "Loaded ${all.size} apps (uninstalled=${all.count { it.isUninstalled }}) in ${currentTimeMillis() - start}ms")

            // Show the list right away; a reload keeps the previous badges until the new ones arrive.
            //
            // 合并按包名做，而且**这个包没变时原样复用旧对象**：`AppInfo` 是不变类型，同一个实例
            // 进列表，Compose 那一行才真的不会重组。一次刷新往往一个包都没变，那时整张表只是
            // 「复查了一遍」，不该重画。
            val previous = _apps.associateBy { it.packageName }
            _apps.clear()
            _apps.addAll(
                all.map { app ->
                    val old = previous[app.packageName]
                    // 新的先说：扫描回来的徽标/图标优先，缺的才拿旧那份补。
                    val merged = app.copy(
                        bloatData = app.bloatData ?: old?.bloatData,
                        icon = app.icon ?: old?.icon ?: iconCache[app.packageName],
                    )
                    // 这一行什么都没变（包、徽标、图标三样都是同一份）→ 直接把旧实例放回去：
                    // 列表项因此完全相等，Compose 那一条不重组，滚动位置也不动。
                    if (old != null &&
                        old.details == merged.details &&
                        old.bloatData === merged.bloatData &&
                        old.icon === merged.icon
                    ) {
                        old
                    } else {
                        merged
                    }
                }
            )
            // 这次扫到的包列表存回本地：下次冷启动的那一帧就靠它，不用再等整机扫描。
            //
            // 只在授权正常时写：没拿到运行时权限时只能看见 ROM 放出来的那几十个包，拿它覆盖旧快照
            // 会让下一次冷启动先画一张「缺三分之二」的表，比空白更误导。
            if (cantaHandler.isAuthorized) snapshot?.write(all.map { it.details })
            isLoading = false
            if (_fakeScores.value) applyScores()
            refreshScoresForDevice()
            checkSurvivors(all)

            // Icons and badges load in parallel.
            if (all.any { it.icon == null && it.packageName !in iconCache }) launch { streamIcons() }
            // 手上那份先贴上去：缓存里有就用缓存，没有就用随包发的那份，都不联网。
            val badgesStart = currentTimeMillis()
            val localBadges = bloatRepository.local()
            applyBloatData(localBadges)
            badgesFromLocal = localBadges.isNotEmpty()
            LogUtils.i(TAG, "Loaded ${localBadges.size} local badges in ${currentTimeMillis() - badgesStart}ms")
            // 有没有新东西交给后台问（一天最多一次）：有变化才替换列表上的徽标。
            refreshStarted = true
            startBadgeRefresh()
        } finally {
            isLoading = false
            // 本地那份已经在列表上了，就不必让「正在加载特殊标志」继续转；本地一个都没有时
            // 由后台那次核对收尾（见 startBadgeRefresh）。
            if (badgesFromLocal || !refreshStarted) isLoadingBadges = false
            appListRestricted = cantaHandler.appListRestricted && _apps.isNotEmpty()
            // Read on every load, so pulling the list down after starting the runtime clears it.
            listIncomplete = !cantaHandler.isAuthorized && _apps.isNotEmpty()
            if (appListRestricted && !noticeShown) askForAppListPermission = true
        }
    }

    /**
     * Asks GitHub whether the bloat list changed — in the background, and at most once a day
     * (`BloatRepository.refresh` decides that, including "offline means try again next launch").
     *
     * Runs on [viewModelScope] rather than inside `load()`'s `coroutineScope`: a child of that scope
     * would keep the load job (and the pull-to-refresh spinner) alive for as long as the check takes,
     * which is exactly the wait this is meant to remove.
     */
    private fun startBadgeRefresh() {
        badgeRefreshJob?.cancel()
        badgeRefreshJob = viewModelScope.launch {
            val start = currentTimeMillis()
            // 修正表和名单同一个周期（一周）：先跑修正，带回来新内容就重画；再跑名单核对。
            // 两个都是 GitHub 上的静态文件、每个周期各一个请求，不碰数据库。
            val correctionsChanged = runCatching { bloatRepository.refreshCorrections() }.getOrDefault(false)
            if (correctionsChanged) applyBloatData(bloatRepository.local())
            val fresh = runCatching { bloatRepository.refresh() }.getOrNull()
            if (fresh != null) {
                applyBloatData(fresh)
                LogUtils.i(TAG, "Refreshed ${fresh.size} badges in ${currentTimeMillis() - start}ms")
            } else {
                LogUtils.i(TAG, "Bloat list unchanged after ${currentTimeMillis() - start}ms")
            }
            isLoadingBadges = false
        }
    }

    /** Once per app start, after the first successful load, reports uninstalls that survived a reboot. */
    private fun checkSurvivors(loaded: List<AppInfo>) {
        if (survivorsChecked || telemetry == null || loaded.isEmpty()) return
        survivorsChecked = true
        viewModelScope.launch { telemetry.resolveSurvivors(loaded) }
    }

    /**
     * Re-runs the reboot check on what is loaded now, without waiting for another app start.
     *
     * The safety page calls this: coming back from a reboot, the check may have run before the app
     * list was there (or not at all, when the boot broadcast was never delivered), and the page
     * then showed "0 reboot-verified" although the phone had in fact rebooted.
     */
    fun refreshSurvivors() {
        val tracker = telemetry ?: return
        val loaded = _apps.toList()
        if (loaded.isEmpty()) return
        viewModelScope.launch { tracker.resolveSurvivors(loaded) }
    }

    private suspend fun applyBloatData(bloat: Map<String, BloatData>) {
        val cantaBloat = BloatData(description = getString(Res.string.canta_description, UAD_CREDIT), badgeInfo = null)
        for (index in _apps.indices) {
            val app = _apps[index]
            val bloatData = if (app.packageName == CANTA_PACKAGE_NAME) cantaBloat else bloat[app.packageName]
            if (bloatData != null && bloatData != app.bloatData) _apps[index] = app.copy(bloatData = bloatData)
        }
    }

    /**
     * Applies icons the handler streams (ADB targets load them lazily through
     * the on-device helper) in small chunks, so the list fills in top-down
     * without recomposing for every single icon.
     */
    private suspend fun streamIcons() {
        val start = currentTimeMillis()
        val chunk = mutableMapOf<String, ImageBitmap>()
        var lastApplied = start
        cantaHandler.loadIcons().collect { (packageName, icon) ->
            iconCache[packageName] = icon
            chunk[packageName] = icon
            if (chunk.size >= ICON_APPLY_BATCH || currentTimeMillis() - lastApplied >= ICON_APPLY_INTERVAL_MS) {
                applyIcons(chunk)
                chunk.clear()
                lastApplied = currentTimeMillis()
            }
        }
        applyIcons(chunk)
        LogUtils.i(TAG, "Loaded ${iconCache.size} icons in ${currentTimeMillis() - start}ms")
    }

    private fun applyIcons(icons: Map<String, ImageBitmap>) {
        if (icons.isEmpty()) return
        for (index in _apps.indices) {
            val app = _apps[index]
            if (app.icon == null) icons[app.packageName]?.let { _apps[index] = app.withIcon(it) }
        }
    }

    /**
     * Uninstalls ([AppsType.INSTALLED]), reinstalls ([AppsType.UNINSTALLED]) or unfreezes
     * ([AppsType.DISABLED]) every selected app, updating the list as each one succeeds. Returns how
     * many succeeded.
     */
    suspend fun applyToSelected(type: AppsType, resetToFactory: Boolean = false): Int =
        applyPackages(selectedApps.toList(), type.operationKind, resetToFactory)

    /**
     * The failures of the batch that just ran, as they were recorded: newest batch only, cleared
     * when the next one starts. The dialog that says "that did not work" reads this, so the reason
     * is on screen at the moment it matters instead of only in the records tab afterwards.
     */
    private val _lastFailures = MutableStateFlow<List<OperationRecord>>(emptyList())
    val lastFailures: StateFlow<List<OperationRecord>> = _lastFailures

    /**
     * Runs one batch of [kind] over [packageNames] without touching the selection: the rescue
     * prompt reinstalls a whole batch this way, and the records tab reverses one operation.
     * Returns how many succeeded.
     */
    suspend fun applyPackages(
        packageNames: List<String>,
        kind: OperationKind,
        resetToFactory: Boolean = false,
    ): Int {
        if (packageNames.isEmpty()) return 0
        _lastFailures.value = emptyList()
        // Written before the first app is touched: if the app dies on the second one, this is
        // what tells the next start which apps the batch never reached, and which operation to
        // repeat on them.
        val batchId = currentTimeMillis()
        if (kind.resumable) rememberBatch(packageNames, batchId, kind)
        val before = _apps.associateBy { it.packageName }
        val results = when (kind) {
            OperationKind.UNINSTALL -> cantaHandler.uninstallApps(packageNames, resetToFactory)
            OperationKind.REINSTALL -> cantaHandler.reinstallApps(packageNames)
            OperationKind.DISABLE -> cantaHandler.setDisabledApps(packageNames, disabled = true)
            OperationKind.ENABLE -> cantaHandler.setDisabledApps(packageNames, disabled = false)
        }
        val succeededApps = mutableListOf<AppInfo>()
        val failed = mutableListOf<OperationRecord>()
        // One per refused package, for the report the backend keeps: the records tab says what
        // happened on this phone, this says it to the person who can fix the app.
        val failureDetails = mutableListOf<FailureDetail>()
        val log = mutableListOf<OperationRecord>()
        var succeeded = 0
        results.collect { result ->
            val app = before[result.packageName]
            val record = operationRecord(
                result = result,
                kind = kind,
                label = app?.name ?: result.packageName,
                batchId = batchId,
                batchIndex = log.size + 1,
                batchSize = packageNames.size,
                resetToFactory = resetToFactory,
                // Taken before the outcome is applied: an app that is gone can no longer be read,
                // and the snapshot is of the thing that was there.
                snapshot = app?.snapshot()?.takeIf { kind == OperationKind.UNINSTALL },
            )
            log += record
            if (result.success) {
                succeeded++
                before[result.packageName]?.let(succeededApps::add)
                // The list follows the batch in the direction it was applied, so a restore from
                // the records tab moves the app back to the installed list, and a freeze marks
                // the app without moving it: it is still there, just stopped.
                when (kind) {
                    OperationKind.UNINSTALL -> setUninstalled(result.packageName, true)
                    OperationKind.REINSTALL -> setUninstalled(result.packageName, false)
                    OperationKind.DISABLE -> setDisabled(result.packageName, true)
                    OperationKind.ENABLE -> setDisabled(result.packageName, false)
                }
                selectedApps.remove(result.packageName)
            } else {
                failed += record
                failureDetails += FailureDetail(
                    kind = kind,
                    packageName = result.packageName,
                    status = result.status,
                    message = result.message,
                    reason = record.reason,
                    advice = record.advice,
                    isSystemApp = app?.isSystemApp == true,
                    batchIndex = record.batchIndex,
                    batchSize = record.batchSize,
                    appVersion = app?.versionName.orEmpty(),
                )
                LogUtils.w(TAG, "${kind.name.lowercase()} '${result.packageName}' failed: ${result.message} (${result.status})")
            }
        }
        _lastFailures.value = failed
        recordOperations(log)
        // Off this batch's path: a report that cannot be sent must never delay the next app or the
        // dialog that explains why this one was refused. No retry — the next batch reports its own.
        if (failureDetails.isNotEmpty()) {
            val details = failureDetails.toList()
            viewModelScope.launch {
                runCatching { telemetry?.recordFailures(details) }
                    .onFailure { LogUtils.w(TAG, "Failure report not sent: $it") }
            }
        }
        // The batch is over: every app has an answer, so there is nothing left to resume. This
        // runs even when some apps failed — a refusal is an answer, and asking again would only
        // produce the same refusal twice.
        if (kind.resumable) forgetRememberedBatch(kind)
        if (succeededApps.isNotEmpty()) {
            when (kind) {
                OperationKind.UNINSTALL ->
                    telemetry?.recordUninstalls(succeededApps, batchSize = packageNames.size)
                OperationKind.REINSTALL -> telemetry?.recordReinstalls(succeededApps)
                // Freezing is not reported: the app is still installed, so none of the survival
                // evidence the telemetry collects applies to it.
                OperationKind.DISABLE, OperationKind.ENABLE -> Unit
            }
        }
        return succeeded
    }

    /**
     * Reinstalls [packageNames] without touching the current selection (the rescue prompt of a
     * previous batch). Returns how many succeeded.
     */
    suspend fun reinstallPackages(packageNames: List<String>): Int =
        applyPackages(packageNames, OperationKind.REINSTALL)

    /**
     * Honours the records tab's "delete records at system start" switch: on, the log is dropped the
     * first time the app runs after a reboot; off, it stays for good. Returns whether it cleared.
     *
     * The boot id is remembered on every start, switch or no switch, so turning the switch on while
     * the app is open never wipes the log being read — only the next reboot does. A platform that
     * reports no boot id (desktop, web) never clears on its own: a fresh process is not a boot.
     *
     * Called once at start, before the resume prompt reads the log.
     */
    suspend fun clearRecordsIfRebooted(): Boolean {
        val store = history ?: return false
        val currentSettings = settings ?: return false
        val bootId = cantaHandler.bootId()?.takeIf { it.isNotEmpty() } ?: return false
        val lastBoot = currentSettings.recordsBootIdFlow.first()
        if (lastBoot == bootId) return false
        // A stored id is what makes this a restart rather than the first start after an install.
        val cleared = lastBoot.isNotEmpty() && currentSettings.clearRecordsAfterBootFlow.first()
        if (cleared) {
            store.clear()
            LogUtils.i(TAG, "System start with the switch on: the operation log was cleared")
        }
        currentSettings.setRecordsBootId(bootId)
        return cleared
    }

    /**
     * What is left of a batch that was cut short, or null when nothing is. Called once at start:
     * the batch is the one the user was in the middle of when the app went away, whatever it was
     * doing — an uninstall, a freeze or a thaw.
     */
    suspend fun interruptedBatch(): ResumeCandidate? {
        // The log carries the kind of the batch; the settings only ever knew about uninstalls, so
        // they are the fallback for a batch remembered before the kind existed. Reading the store
        // here also loads it, which is what makes the records below the whole log.
        val stored = history?.pendingBatch() ?: legacyPendingBatch() ?: return null
        val records = history?.records?.value.orEmpty()
        val candidate = resumeCandidate(stored.packages, stored.startedAt, records, stored.kind)
            ?: return null
        resumingKind = candidate.kind
        return candidate
    }

    /**
     * The batch remembered by [CantaSettings]: packages and start time, nothing more. Data written
     * before the log kept the kind only ever holds uninstall batches, so that is what it means.
     */
    private suspend fun legacyPendingBatch(): ResumeCandidate? {
        val current = settings ?: return null
        val interrupted = current.interruptedBatchFlow.first()
        val startedAt = current.interruptedBatchStartedAtFlow.first()
        if (interrupted.isEmpty() || startedAt <= 0) return null
        return ResumeCandidate(
            packages = interrupted,
            startedAt = startedAt,
            kind = OperationKind.UNINSTALL,
        )
    }

    /**
     * The kind of the batch the resume prompt is about, remembered by [interruptedBatch] so the
     * answer runs the same operation again instead of guessing.
     */
    private var resumingKind: OperationKind? = null

    /**
     * Finishes the interrupted batch with the operation it was already running. The packages
     * already answered are skipped by [resumeCandidate], so nothing that was already handled is
     * attempted a second time.
     */
    suspend fun resumeInterruptedBatch(
        packages: List<String>,
        resetToFactory: Boolean = false,
    ): Int {
        // A freeze batch resumed as an uninstall would delete apps the user only meant to pause,
        // so the kind comes from what was stored when the batch started.
        val kind = resumingKind ?: interruptedBatch()?.kind ?: OperationKind.UNINSTALL
        resumingKind = null
        return applyPackages(packages, kind, resetToFactory && kind == OperationKind.UNINSTALL)
    }

    /** Finishes an interrupted batch whose kind the caller already knows (the resume prompt's). */
    suspend fun resumeInterruptedBatch(
        packages: List<String>,
        kind: OperationKind,
        resetToFactory: Boolean = false,
    ): Int {
        resumingKind = null
        return applyPackages(packages, kind, resetToFactory && kind == OperationKind.UNINSTALL)
    }

    /**
     * Undoes a whole batch in one tap: what an uninstall batch removed is put back, what a freeze
     * batch froze is thawed. The outcome and its failures are reported exactly as any other batch,
     * so a rollback that half worked says which apps it could not do. Returns how many succeeded.
     */
    suspend fun rollbackBatch(batch: BatchRollback): Int =
        applyPackages(batch.packages, batch.undoKind)

    /** The interrupted batch was abandoned: stop offering it at every start. */
    suspend fun forgetInterruptedBatch() {
        resumingKind = null
        runCatching { history?.clearPendingBatch() }
        settings?.clearPendingBatch()
    }

    /**
     * Remembers a batch before its first app is touched, so a batch cut short is still known at the
     * next start. The log is the copy that carries the kind; [CantaSettings] keeps the uninstall
     * batch as well, so a build that predates this still offers to finish it.
     */
    private suspend fun rememberBatch(packages: List<String>, batchId: Long, kind: OperationKind) {
        runCatching { history?.startPendingBatch(packages, batchId, kind) }
            .onFailure { LogUtils.e(TAG, "Could not remember the ${kind.name.lowercase()} batch", it) }
        if (kind == OperationKind.UNINSTALL) {
            runCatching { settings?.startPendingBatch(packages, batchId) }
        }
    }

    /** The batch finished (or was abandoned): neither copy of it is worth resuming. */
    private suspend fun forgetRememberedBatch(kind: OperationKind) {
        resumingKind = null
        runCatching { history?.clearPendingBatch() }
        if (kind == OperationKind.UNINSTALL) {
            runCatching { settings?.clearPendingBatch() }
        }
    }

    /** One line of the records tab for [result]; the phone's own model is asked once per batch. */
    private suspend fun operationRecord(
        result: OperationResult,
        kind: OperationKind,
        label: String,
        batchId: Long,
        batchIndex: Int,
        batchSize: Int,
        resetToFactory: Boolean = false,
        snapshot: PackageSnapshot? = null,
    ): OperationRecord {
        val device = if (batchIndex == 1) {
            runCatching { cantaHandler.deviceProfile()?.shortLabel() }.getOrNull().orEmpty()
        } else {
            batchDevice
        }
        if (batchIndex == 1) batchDevice = device
        val explanation = result.explainFailure()
        return OperationRecord(
            timestamp = currentTimeMillis(),
            kind = kind,
            packageName = result.packageName,
            label = label,
            success = result.success,
            message = if (result.success) "" else result.message,
            reason = explanation?.reason.orEmpty(),
            advice = explanation?.advice.orEmpty(),
            // Only an uninstall keeps the snapshot: that is the record a user reads when asking
            // "what was that app, and what would putting it back bring?".
            snapshot = snapshot,
            batchId = batchId,
            batchIndex = batchIndex,
            batchSize = batchSize,
            resetToFactory = resetToFactory,
            device = device,
            user = cantaHandler.selectedUserId,
        )
    }

    /** Model of the phone the running batch talks to; read once instead of per package. */
    private var batchDevice: String = ""

    /** Writes [records] to the log; the records tab and the CSV in Download both read from it. */
    private suspend fun recordOperations(records: List<OperationRecord>) {
        val store = history ?: return
        if (records.isEmpty()) return
        runCatching { store.record(records) }
            .onFailure { LogUtils.e(TAG, "Could not record the batch", it) }
    }

    /** Marks one app as removed or back, leaving the others alone. */
    private fun setUninstalled(packageName: String, uninstalled: Boolean) {
        val index = _apps.indexOfFirst { it.packageName == packageName }
        if (index < 0) return
        val current = _apps[index]
        if (current.isUninstalled != uninstalled) _apps[index] = current.withUninstalled(uninstalled)
    }

    /** Marks one app as frozen or back, leaving the others alone. */
    private fun setDisabled(packageName: String, disabled: Boolean) {
        val index = _apps.indexOfFirst { it.packageName == packageName }
        if (index < 0) return
        val current = _apps[index]
        if (current.isDisabled != disabled) _apps[index] = current.withDisabled(disabled)
    }
}
