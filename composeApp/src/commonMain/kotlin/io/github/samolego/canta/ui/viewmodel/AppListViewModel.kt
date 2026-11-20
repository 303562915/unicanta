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
import io.github.samolego.canta.data.app.Filter
import io.github.samolego.canta.data.app.PackageSnapshot
import io.github.samolego.canta.data.bloat.BloatData
import io.github.samolego.canta.data.bloat.BloatRepository
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.data.history.OperationLogStore
import io.github.samolego.canta.data.history.OperationRecord
import io.github.samolego.canta.data.history.ResumeCandidate
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

    var selectedFilter by mutableStateOf(Filter.any)

    val selectedAppsSorted by derivedStateOf {
        sortedList.filter { it.packageName in selectedApps }
    }

    private val sortedList by derivedStateOf {
        _apps.filter { selectedFilter.shouldShow(it) }
            .sortedWith(compareBy { it.name.lowercase() })
    }

    /** The apps matching the search query and filters. */
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
     * (Re)loads all apps, cancelling a load that is still running. The
     * returned job completes once apps, badges and streamed icons are loaded.
     */
    fun loadApps(): Job {
        loadJob?.cancel()
        return viewModelScope.launch { load() }.also { loadJob = it }
    }

    private suspend fun load() = coroutineScope {
        isLoading = true
        isLoadingBadges = true
        try {
            val start = currentTimeMillis()
            val all = cantaHandler.loadApps().distinctBy { it.packageName }
            LogUtils.i(TAG, "Loaded ${all.size} apps (uninstalled=${all.count { it.isUninstalled }}) in ${currentTimeMillis() - start}ms")

            // Show the list right away; a reload keeps the previous badges until the new ones arrive.
            val previous = _apps.associateBy { it.packageName }
            _apps.clear()
            _apps.addAll(
                all.map { app ->
                    app.copy(
                        bloatData = app.bloatData ?: previous[app.packageName]?.bloatData,
                        icon = app.icon ?: iconCache[app.packageName],
                    )
                }
            )
            isLoading = false
            if (_fakeScores.value) applyScores()
            refreshScoresForDevice()
            checkSurvivors(all)

            // Icons and badges load in parallel.
            if (all.any { it.icon == null && it.packageName !in iconCache }) launch { streamIcons() }
            val badgesStart = currentTimeMillis()
            applyBloatData(bloatRepository.load())
            LogUtils.i(TAG, "Loaded badges in ${currentTimeMillis() - badgesStart}ms")
        } finally {
            isLoading = false
            isLoadingBadges = false
            appListRestricted = cantaHandler.appListRestricted && _apps.isNotEmpty()
            if (appListRestricted && !noticeShown) askForAppListPermission = true
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
     * Uninstalls ([AppsType.INSTALLED]) or reinstalls ([AppsType.UNINSTALLED])
     * every selected app, updating the list as each one succeeds. Returns how
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
        // what tells the next start which apps the batch never reached.
        val batchId = currentTimeMillis()
        if (kind == OperationKind.UNINSTALL) {
            runCatching { settings?.startPendingBatch(packageNames, batchId) }
        }
        val before = _apps.associateBy { it.packageName }
        val results = when (kind) {
            OperationKind.UNINSTALL -> cantaHandler.uninstallApps(packageNames, resetToFactory)
            OperationKind.REINSTALL -> cantaHandler.reinstallApps(packageNames)
        }
        val succeededApps = mutableListOf<AppInfo>()
        val failed = mutableListOf<OperationRecord>()
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
                // the records tab moves the app back to the installed list.
                setUninstalled(result.packageName, kind == OperationKind.UNINSTALL)
                selectedApps.remove(result.packageName)
            } else {
                failed += record
                LogUtils.w(TAG, "${kind.name.lowercase()} '${result.packageName}' failed: ${result.message} (${result.status})")
            }
        }
        _lastFailures.value = failed
        recordOperations(log)
        // The batch is over: every app has an answer, so there is nothing left to resume. This
        // runs even when some apps failed — a refusal is an answer, and asking again would only
        // produce the same refusal twice.
        if (kind == OperationKind.UNINSTALL) {
            runCatching { settings?.clearPendingBatch() }
        }
        if (succeededApps.isNotEmpty()) {
            when (kind) {
                OperationKind.UNINSTALL ->
                    telemetry?.recordUninstalls(succeededApps, batchSize = packageNames.size)
                OperationKind.REINSTALL -> telemetry?.recordReinstalls(succeededApps)
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
     * What is left of an uninstall batch that was cut short, or null when nothing is. Called once
     * at start: the batch is the one the user was in the middle of when the app went away.
     */
    suspend fun interruptedBatch(): ResumeCandidate? {
        val current = settings ?: return null
        val interrupted = current.interruptedBatchFlow.first()
        if (interrupted.isEmpty()) return null
        val startedAt = current.interruptedBatchStartedAtFlow.first()
        val records = history?.records?.value.orEmpty()
        return resumeCandidate(interrupted, startedAt, records)
    }

    /**
     * Finishes the interrupted batch. The packages already answered are skipped by [applyPackages]
     * asking the device for each one's state, so nothing that was already removed is attempted a
     * second time.
     */
    suspend fun resumeInterruptedBatch(packages: List<String>, resetToFactory: Boolean = false): Int =
        applyPackages(packages, OperationKind.UNINSTALL, resetToFactory)

    /** The interrupted batch was abandoned: stop offering it at every start. */
    suspend fun forgetInterruptedBatch() {
        settings?.clearPendingBatch()
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
}
