package io.github.samolego.canta.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.samolego.canta.CANTA_PACKAGE_NAME
import io.github.samolego.canta.core.CantaDevice
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.core.DeviceDiscovery
import io.github.samolego.canta.core.Occupancy
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.app.AppBadgeInfo
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.auth_required
import io.github.samolego.canta.generated.resources.auth_required_description
import io.github.samolego.canta.generated.resources.cannot_uninstall_canta
import io.github.samolego.canta.generated.resources.device_unreachable
import io.github.samolego.canta.generated.resources.occupied_accessibility
import io.github.samolego.canta.generated.resources.occupied_device_admin
import io.github.samolego.canta.generated.resources.occupied_input_method
import io.github.samolego.canta.generated.resources.occupied_launcher
import io.github.samolego.canta.generated.resources.records_cleared_after_reboot
import io.github.samolego.canta.ui.AppsType
import io.github.samolego.canta.ui.dialog.message
import io.github.samolego.canta.ui.operationKind
import io.github.samolego.canta.util.SafetyRules
import io.github.samolego.canta.util.SafetyStatus
import io.github.samolego.canta.util.SafetyVerdict
import io.github.samolego.canta.util.Verdict
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/** The dialog the main screen is showing; at most one at a time. */
sealed interface MainDialog {
    data class ConfirmUninstall(
        val appCount: Int,
        val canResetToFactory: Boolean,
        /** The community reports that at least one of the apps can cause a bootloop. */
        val bootloopRisk: Boolean = false,
        /**
         * 名单自己把这些包标成了「不安全」/「专家」。
         *
         * 与 [bootloopRisk] 是两个来源：那个是社区投票（有人踩过坑才出现），这个是名单的判定
         * （删之前就写着）。两者都要说——名单标着"不安全"的包，不该等出过事故才提醒。
         */
        val unsafeLevelCount: Int = 0,
        val expertLevelCount: Int = 0,
        /** Selected packages that were not in the last confirmed batch: a list pasted from elsewhere. */
        val newPackages: List<String> = emptyList(),
        /** Whether freezing is on the table here; a device without it only has removal. */
        val canFreeze: Boolean = false,
    ) : MainDialog
    /**
     * What the batch did, so the dialog can say "uninstalled" / "restored" / "frozen" / "unfrozen"
     * instead of guessing from a boolean.
     */
    data class Success(
        val count: Int,
        val kind: OperationKind,
    ) : MainDialog
    /**
     * Nothing in the batch went through: instead of staying silent, name what the device refused
     * and what to try next, one line per distinct reason.
     */
    data class Failure(
        val attempts: Int,
        val reasons: List<Pair<String, String>>,
        /** What was being attempted: it titles the dialog. */
        val kind: OperationKind,
    ) : MainDialog

    /** Packages that came back although the build did not change: a rescue of the last batch. */
    data class RestoreSuspects(val packages: List<String>) : MainDialog

    /**
     * The uninstall batch that was cut short: [packages] never got an answer. Offered once at
     * start, so the list does not have to be found and selected again.
     */
    data class ResumeBatch(val packages: List<String>) : MainDialog

    /**
     * The device rules stopped something in this selection. [blocked] are packages known to strand
     * *this* model/ROM and are always held back; [risky] are the ones with a known cost, which are
     * only shown when the user asked to be asked before uninstalling. [unknownCount] is how many
     * packages no rule mentions — unknown is not the same as safe.
     */
    data class UnsafeSelection(
        val blocked: List<SafetyVerdict>,
        val risky: List<SafetyVerdict>,
        val deviceLabel: String,
        val unknownCount: Int,
    ) : MainDialog
    data object ShizukuSetup : MainDialog
    data object ExplainBadges : MainDialog
    data class DeviceChooser(val devices: List<CantaDevice> = emptyList(), val isLoading: Boolean = false) : MainDialog
}

/**
 * Drives the main screen's flows: connecting to a device and the
 * authorize → confirm → authenticate → uninstall/reinstall sequence, exposing
 * the current step as [dialog].
 */
class MainViewModel(
    private val handler: CantaHandler,
    private val platform: CantaPlatform,
    private val settings: CantaSettings,
    private val appList: AppListViewModel,
    /** Where the shipped rules come from; tests hand in their own set. */
    private val loadRules: suspend () -> SafetyRules = { SafetyRules.bundled() },
) : ViewModel() {

    var dialog by mutableStateOf<MainDialog?>(null)
        private set

    /** Shipped device-aware rules; loaded once. */
    private var rules: SafetyRules? = null

    private suspend fun safetyRules(): SafetyRules = rules ?: loadRules().also { rules = it }

    /** What to do once the pending connection/authorization succeeds. */
    private var afterConnect: (suspend () -> Unit)? = null

    init {
        if (handler.requiresDeviceSelection && !handler.isAuthorized) {
            openDeviceChooser { appList.loadApps() }
        } else {
            appList.loadApps()
        }
        viewModelScope.launch {
            // First, because a reboot with the switch on means the log is gone by design — and the
            // prompts below read that log.
            clearRecordsAfterReboot()
            showRestoreSuspects()
        }
    }

    /**
     * Honours the records tab's "delete records at system start" switch, before anything below
     * reads the log the switch is about: the list owns the log, and this only says it happened.
     */
    private suspend fun clearRecordsAfterReboot() {
        if (appList.clearRecordsIfRebooted()) {
            platform.showMessage(getString(Res.string.records_cleared_after_reboot))
        }
    }

    /** Tells the user once per start that the last batch had to be put back by hand. */
    private suspend fun showRestoreSuspects() {
        val suspects = settings.restoreSuspectsFlow.first()
        if (suspects.isNotEmpty() && dialog == null) {
            dialog = MainDialog.RestoreSuspects(suspects)
            return
        }
        showInterruptedBatch()
    }

    /**
     * Offers to finish the batch that was cut short. A device that had to be rescued comes first:
     * that prompt says the phone may be broken, while this one only says there is work left.
     */
    private suspend fun showInterruptedBatch() {
        if (dialog != null) return
        val candidate = appList.interruptedBatch() ?: return
        dialog = MainDialog.ResumeBatch(candidate.packages)
    }

    /** [MainDialog.ResumeBatch] confirmed: finish the list, which is read off the device again. */
    fun onResumeBatchConfirmed() {
        val packages = (dialog as? MainDialog.ResumeBatch)?.packages ?: return
        dialog = null
        viewModelScope.launch {
            val done = appList.resumeInterruptedBatch(packages)
            appList.loadApps()
            if (done > 0 && !settings.hideSuccessDialogFlow.first()) {
                dialog = MainDialog.Success(count = done, kind = OperationKind.UNINSTALL)
            }
        }
    }

    /** [MainDialog.ResumeBatch] dismissed: do not ask again for this batch. */
    fun onResumeBatchDismissed() {
        dialog = null
        viewModelScope.launch { appList.forgetInterruptedBatch() }
    }

    /** [MainDialog.RestoreSuspects] dismissed: keep believing it (the batch is already uninstalled). */
    fun onRestoreSuspectsDismissed() {
        dialog = null
        viewModelScope.launch { settings.setRestoreSuspects(emptyList()) }
    }

    /** [MainDialog.RestoreSuspects] confirmed: put those packages back. */
    fun onRestoreSuspectsReverted() {
        val suspects = (dialog as? MainDialog.RestoreSuspects)?.packages ?: return
        dialog = null
        viewModelScope.launch {
            settings.setRestoreSuspects(emptyList())
            val reverted = appList.reinstallPackages(suspects)
            if (reverted > 0 && !settings.hideSuccessDialogFlow.first()) {
                dialog = MainDialog.Success(count = reverted, kind = OperationKind.REINSTALL)
            } else {
                appList.loadApps()
            }
        }
    }

    fun dismissDialog() {
        dialog = null
        afterConnect = null
        pendingRemoval = emptyList()
    }

    fun showBadgesExplanation() {
        dialog = MainDialog.ExplainBadges
    }

    /**
     * 确认框正在处理的那批包：列表页是选中集合的快照，应用详情页只有那一个包。
     *
     * 不现读 [AppListViewModel.selectedApps]，是因为两个页面共用同一套确认、生物识别与结果
     * 流程，而详情页不该碰用户的勾选；执行途中勾选被清空也不该影响已经在跑的批次。
     */
    private var pendingRemoval: List<String> = emptyList()

    /** The FAB: uninstall ([AppsType.INSTALLED]) or reinstall the selected apps. */
    fun applyToSelected(type: AppsType) {
        viewModelScope.launch {
            if (CANTA_PACKAGE_NAME in appList.selectedApps) {
                platform.showMessage(getString(Res.string.cannot_uninstall_canta))
                return@launch
            }
            whenAuthorized { confirm(type) }
        }
    }

    /**
     * 应用详情页的「卸载」：一个包，走和列表页完全相同的路——设备规则、占用检查、确认框、
     * 生物识别、成功/失败提示，所以两个页面的措辞与拒绝理由不会走样。
     */
    fun uninstallPackage(packageName: String) {
        viewModelScope.launch {
            if (packageName == CANTA_PACKAGE_NAME) {
                platform.showMessage(getString(Res.string.cannot_uninstall_canta))
                return@launch
            }
            whenAuthorized { confirmRemoval(listOf(packageName)) }
        }
    }

    /** 应用详情页的「恢复」：把已卸载的包放回去，提示与列表页的恢复一致。 */
    fun reinstallPackage(packageName: String) {
        viewModelScope.launch {
            whenAuthorized {
                authenticateAndApply(AppsType.UNINSTALLED, listOf(packageName), resetToFactory = false)
            }
        }
    }

    private suspend fun whenAuthorized(action: suspend () -> Unit) {
        when {
            handler.isAuthorized -> action()
            handler.requiresDeviceSelection -> openDeviceChooser(action)
            else -> {
                afterConnect = action
                dialog = MainDialog.ShizukuSetup
            }
        }
    }

    /** [MainDialog.ShizukuSetup] closed; [proceed] requests the Shizuku permission. */
    fun onShizukuSetupClosed(proceed: Boolean) {
        val action = afterConnect
        dismissDialog()
        if (!proceed || action == null) return
        viewModelScope.launch {
            handler.requestAuthorization()
            if (handler.isAuthorized) action() else platform.showMessage(getString(Res.string.device_unreachable))
        }
    }

    private suspend fun confirm(type: AppsType) {
        if (type != AppsType.INSTALLED) {
            authenticateAndApply(type, appList.selectedApps.toList(), resetToFactory = false)
            return
        }
        val selected = appList.selectedApps.toList()
        if (selected.isEmpty()) return
        confirmRemoval(selected)
    }

    /**
     * 卸载前的唯一那条路：列表页的 FAB 与一个应用自己的页面都从这里走，所以两处的弹窗、
     * 规则与结果提示必然一致。
     */
    private suspend fun confirmRemoval(selected: List<String>) {
        pendingRemoval = selected
        // The device rules are not the confirmation dialog: they are the thing that stops a package
        // known to strand *this* model or ROM. They therefore run whether or not the user asked to
        // be asked twice — turning the "are you sure" prompt off must not turn the safety check off
        // with it, which is what it used to do.
        val verdicts = safetyRules().verdicts(selected, handler.deviceProfile())
        // A package that is the home screen, the keyboard, an administrator or an accessibility
        // service right now is blocked even when no rule mentions it: the shipped rules cannot know
        // what this user switched on, and removing it takes the phone's way back with it.
        val blocked = blockedVerdicts(verdicts.values, occupancyVerdicts(selected))
        val blockedPackages = blocked.map { it.pkg }.toSet()
        // What is blocked is already listed above; repeating it as merely risky would read as "this
        // one you may remove anyway", the opposite of what the rules just said.
        val risky = verdicts.values.filter { it.status == SafetyStatus.RISKY && it.pkg !in blockedPackages }
        if (blocked.isNotEmpty()) {
            dialog = unsafeSelection(selected, verdicts, blocked, risky)
            return
        }
        if (!settings.confirmBeforeUninstallFlow.first()) {
            // Confirmation is off, so "有代价" packages go through without a prompt. The route is
            // the same one the dialog would have taken, which keeps the two paths behaving alike.
            authenticateAndApply(AppsType.INSTALLED, selected, resetToFactory = false)
            return
        }
        if (risky.isNotEmpty()) {
            dialog = unsafeSelection(selected, verdicts, blocked, risky)
            return
        }
        val known = settings.lastConfirmedPackagesFlow.first().toSet()
        // 名单档位：`Unsafe` 是"可能破坏系统关键部分、有卡在开机的风险"，`Expert` 是"会破坏
        // 常用功能、一般不会卡在开机"。社区投票只知道有人踩过的坑，档位是删之前就写着的判断，
        // 所以两句话分开说，不把某个包的票数当成它的档位。
        val badges = appList.apps.associate { it.packageName to it.badgeInfo }
        dialog = MainDialog.ConfirmUninstall(
            appCount = selected.size,
            canResetToFactory = selected.any { handler.canResetToFactory(it) },
            bootloopRisk = selected.any { appList.scores.value[it]?.verdict == Verdict.BOOTLOOP },
            unsafeLevelCount = selected.count { badges[it] == AppBadgeInfo.UNSAFE },
            expertLevelCount = selected.count { badges[it] == AppBadgeInfo.EXPERT },
            // With no batch confirmed yet everything is new, which would flag every first run.
            newPackages = if (known.isEmpty()) emptyList() else selected.filterNot { it in known },
            canFreeze = handler.supportsDisabling,
        )
    }

    /**
     * Packages in use are turned into the same kind of verdict the rule set produces, so a single
     * dialog explains both: rules can only guess what the user made their default.
     */
    private suspend fun occupancyVerdicts(packageNames: List<String>): List<SafetyVerdict> =
        handler.occupiedBy(packageNames).map { (pkg, roles) ->
            val reasons = roles.map { reasonFor(it) }
            SafetyVerdict(pkg, SafetyStatus.CRITICAL, reasons.joinToString("; "))
        }

    private suspend fun reasonFor(role: Occupancy): String = when (role) {
        Occupancy.LAUNCHER -> getString(Res.string.occupied_launcher)
        Occupancy.INPUT_METHOD -> getString(Res.string.occupied_input_method)
        Occupancy.DEVICE_ADMIN -> getString(Res.string.occupied_device_admin)
        Occupancy.ACCESSIBILITY -> getString(Res.string.occupied_accessibility)
    }

    /**
     * One entry per package, and both reasons kept: a package can be critical by rule, in use, or
     * both, and "it would strand the phone" plus "it is the keyboard" are separate facts. A package
     * the rules merely find risky but that is in use now is blocked, so its rule still gets a word.
     */
    private fun blockedVerdicts(
        ruleVerdicts: Collection<SafetyVerdict>,
        inUse: List<SafetyVerdict>,
    ): List<SafetyVerdict> {
        val byRule = ruleVerdicts.filter { it.status == SafetyStatus.CRITICAL }.associateBy { it.pkg }
        val busy = inUse.associateBy { it.pkg }
        val anyRule = ruleVerdicts.associateBy { it.pkg }
        return (byRule.keys + busy.keys).map { pkg ->
            val why = listOfNotNull(anyRule[pkg]?.why, busy[pkg]?.why)
                .filter { it.isNotBlank() }
                .joinToString("; ")
            SafetyVerdict(pkg, SafetyStatus.CRITICAL, why)
        }
    }

    /**
     * The one dialog both rule paths show, so a blocked batch and a merely risky one cannot drift
     * apart in wording: everything the second call would have said is passed in.
     */
    private suspend fun unsafeSelection(
        selected: List<String>,
        verdicts: Map<String, SafetyVerdict>,
        blocked: List<SafetyVerdict>,
        risky: List<SafetyVerdict>,
    ): MainDialog.UnsafeSelection = MainDialog.UnsafeSelection(
        blocked = blocked,
        risky = risky,
        deviceLabel = handler.deviceProfile()?.label().orEmpty(),
        // A package in use has no rule either, but it is not unknown to the dialog: it is listed
        // above with the job it is doing.
        unknownCount = selected.count { pkg -> pkg !in verdicts && blocked.none { it.pkg == pkg } },
    )

    /** [MainDialog.UnsafeSelection] accepted: drop the blocked ones and continue with the rest. */
    fun onUnsafeSelectionAccepted() {
        val unsafe = dialog as? MainDialog.UnsafeSelection ?: return
        val blocked = unsafe.blocked.map { it.pkg }.toSet()
        // The batch that was confirmed, not the current selection: an app's own page confirms a
        // single package without ever touching what the list has ticked.
        val remaining = pendingRemoval.filterNot { it in blocked }
        viewModelScope.launch {
            blocked.forEach { appList.selectedApps.remove(it) }
            dialog = null
            if (remaining.isEmpty()) return@launch
            authenticateAndApply(AppsType.INSTALLED, remaining, resetToFactory = false)
        }
    }

    fun onUninstallConfirmed(resetToFactory: Boolean) {
        val packages = pendingRemoval
        dialog = null
        viewModelScope.launch { authenticateAndApply(AppsType.INSTALLED, packages, resetToFactory) }
    }

    /**
     * Freeze the confirmed packages instead of removing them: the same apps, the same login and
     * data, and an undo that needs no copy of anything.
     */
    fun onFreezeConfirmed() {
        val packages = pendingRemoval
        dialog = null
        viewModelScope.launch {
            if (settings.authEnabledFlow.first()) {
                platform.requireBiometric(
                    title = getString(Res.string.auth_required),
                    subtitle = getString(Res.string.auth_required_description),
                ) { viewModelScope.launch { freezePackages(packages) } }
            } else {
                freezePackages(packages)
            }
        }
    }

    private suspend fun freezePackages(packages: List<String>) {
        if (packages.isEmpty()) return
        val count = appList.applyPackages(packages, OperationKind.DISABLE)
        if (count > 0 && !settings.hideSuccessDialogFlow.first()) {
            // Reported as a freeze, not as a removal: "1 app uninstalled" would be a lie.
            dialog = MainDialog.Success(count = count, kind = OperationKind.DISABLE)
        } else if (count == 0) {
            val failures = appList.lastFailures.value
            if (failures.isNotEmpty()) {
                dialog = MainDialog.Failure(
                    attempts = failures.size,
                    reasons = failures.map { it.reason.ifEmpty { it.message } to it.advice }.distinct(),
                    kind = OperationKind.DISABLE,
                )
            }
        }
    }

    private suspend fun authenticateAndApply(
        type: AppsType,
        packages: List<String>,
        resetToFactory: Boolean,
    ) {
        if (packages.isEmpty()) return
        if (settings.authEnabledFlow.first()) {
            platform.requireBiometric(
                title = getString(Res.string.auth_required),
                subtitle = getString(Res.string.auth_required_description),
            ) { viewModelScope.launch { apply(type, packages, resetToFactory) } }
        } else {
            apply(type, packages, resetToFactory)
        }
    }

    private suspend fun apply(type: AppsType, packages: List<String>, resetToFactory: Boolean) {
        // Remembered before applying: a successful package leaves the list it was picked from.
        val attempted = if (type == AppsType.INSTALLED) packages else emptyList()
        val count = appList.applyPackages(packages, type.operationKind, resetToFactory)
        if (attempted.isNotEmpty()) settings.setLastConfirmedPackages(attempted)
        val kind = type.operationKind
        if (count > 0) {
            if (!settings.hideSuccessDialogFlow.first()) {
                dialog = MainDialog.Success(count = count, kind = kind)
            }
            return
        }
        // Nothing went through. Staying silent here is the worst case: the user tapped, nothing
        // happened, and only the records tab would ever say why. Name the reasons instead, one
        // line per distinct one, since a whole batch usually fails for the same cause.
        val failures = appList.lastFailures.value
        if (failures.isEmpty()) return
        val reasons = failures
            .map { it.reason.ifEmpty { it.message } to it.advice }
            .distinct()
        dialog = MainDialog.Failure(attempts = failures.size, reasons = reasons, kind = kind)
    }

    // Device selection (desktop, web).

    private fun openDeviceChooser(then: suspend () -> Unit) {
        afterConnect = then
        dialog = MainDialog.DeviceChooser()
        if (handler.deviceDiscovery == DeviceDiscovery.List) refreshDevices()
    }

    fun refreshDevices() {
        viewModelScope.launch {
            updateChooser { copy(isLoading = true) }
            val devices = handler.availableDevices()
            updateChooser { copy(devices = devices, isLoading = false) }
        }
    }

    fun selectDevice(device: CantaDevice) = connect {
        handler.selectDevice(device)
        handler.isAuthorized
    }

    fun pickSystemDevice() = connect { handler.pickSystemDevice() != null }

    private fun connect(attempt: suspend () -> Boolean) {
        viewModelScope.launch {
            updateChooser { copy(isLoading = true) }
            if (attempt()) {
                val action = afterConnect
                dismissDialog()
                action?.invoke()
            } else {
                updateChooser { copy(isLoading = false) }
                handler.lastConnectionFailure?.let { platform.showMessage(it.message()) }
                if (handler.deviceDiscovery == DeviceDiscovery.List) refreshDevices()
            }
        }
    }

    private fun updateChooser(update: MainDialog.DeviceChooser.() -> MainDialog.DeviceChooser) {
        (dialog as? MainDialog.DeviceChooser)?.let { dialog = it.update() }
    }
}
