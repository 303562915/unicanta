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
import io.github.samolego.canta.ui.AppsType
import io.github.samolego.canta.ui.dialog.message
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
        /** Selected packages that were not in the last confirmed batch: a list pasted from elsewhere. */
        val newPackages: List<String> = emptyList(),
        /** Whether freezing is on the table here; a device without it only has removal. */
        val canFreeze: Boolean = false,
    ) : MainDialog
    data class Success(val count: Int, val isReinstall: Boolean) : MainDialog
    /**
     * Nothing in the batch went through: instead of staying silent, name what the device refused
     * and what to try next, one line per distinct reason.
     */
    data class Failure(
        val attempts: Int,
        val reasons: List<Pair<String, String>>,
        val isReinstall: Boolean,
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
        viewModelScope.launch { showRestoreSuspects() }
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
                dialog = MainDialog.Success(count = done, isReinstall = false)
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
                dialog = MainDialog.Success(count = reverted, isReinstall = true)
            } else {
                appList.loadApps()
            }
        }
    }

    fun dismissDialog() {
        dialog = null
        afterConnect = null
    }

    fun showBadgesExplanation() {
        dialog = MainDialog.ExplainBadges
    }

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
            authenticateAndApply(type, resetToFactory = false)
            return
        }
        val selected = appList.selectedApps.toList()
        if (selected.isEmpty()) return
        // The device rules are not the confirmation dialog: they are the thing that stops a package
        // known to strand *this* model or ROM. They therefore run whether or not the user asked to
        // be asked twice — turning the "are you sure" prompt off must not turn the safety check off
        // with it, which is what it used to do.
        val verdicts = safetyRules().verdicts(selected, handler.deviceProfile())
        val ruleBlocked = verdicts.values.filter { it.status == SafetyStatus.CRITICAL }
        // A package that is the home screen, the keyboard, an administrator or an accessibility
        // service right now is blocked even when no rule mentions it: the shipped rules cannot know
        // what this user switched on, and removing it takes the phone's way back with it.
        val blocked = mergeVerdicts(ruleBlocked, occupancyVerdicts(selected))
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
            authenticateAndApply(type, resetToFactory = false)
            return
        }
        if (risky.isNotEmpty()) {
            dialog = unsafeSelection(selected, verdicts, blocked, risky)
            return
        }
        val known = settings.lastConfirmedPackagesFlow.first().toSet()
        dialog = MainDialog.ConfirmUninstall(
            appCount = selected.size,
            canResetToFactory = selected.any { handler.canResetToFactory(it) },
            bootloopRisk = selected.any { appList.scores.value[it]?.verdict == Verdict.BOOTLOOP },
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

    /** One entry per package: a package both risky by rule and in use would otherwise be listed twice. */
    private fun mergeVerdicts(rules: List<SafetyVerdict>, inUse: List<SafetyVerdict>): List<SafetyVerdict> {
        if (inUse.isEmpty()) return rules
        val busy = inUse.associateBy { it.pkg }
        val merged = rules.map { verdict ->
            val extra = busy[verdict.pkg]
            if (extra == null) {
                verdict
            } else {
                verdict.copy(why = listOf(verdict.why, extra.why).filter { it.isNotBlank() }.joinToString("; "))
            }
        }
        val covered = merged.map { it.pkg }.toSet()
        return merged + inUse.filterNot { it.pkg in covered }
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
        viewModelScope.launch {
            blocked.forEach { appList.selectedApps.remove(it) }
            dialog = null
            if (appList.selectedApps.isEmpty()) return@launch
            authenticateAndApply(AppsType.INSTALLED, resetToFactory = false)
        }
    }

    fun onUninstallConfirmed(resetToFactory: Boolean) {
        dialog = null
        viewModelScope.launch { authenticateAndApply(AppsType.INSTALLED, resetToFactory) }
    }

    /**
     * Freeze the selection instead of removing it: the same packages, the same login and data,
     * and an undo that needs no copy of anything.
     */
    fun onFreezeConfirmed() {
        dialog = null
        viewModelScope.launch {
            if (settings.authEnabledFlow.first()) {
                platform.requireBiometric(
                    title = getString(Res.string.auth_required),
                    subtitle = getString(Res.string.auth_required_description),
                ) { viewModelScope.launch { freezeSelected() } }
            } else {
                freezeSelected()
            }
        }
    }

    private suspend fun freezeSelected() {
        val attempted = appList.selectedApps.toList()
        val count = appList.applyPackages(attempted, OperationKind.DISABLE)
        if (count > 0 && !settings.hideSuccessDialogFlow.first()) {
            // Reported as a freeze, not as a removal: "1 app uninstalled" would be a lie.
            dialog = MainDialog.Success(count = count, isReinstall = true)
        } else if (count == 0) {
            val failures = appList.lastFailures.value
            if (failures.isNotEmpty()) {
                dialog = MainDialog.Failure(
                    attempts = failures.size,
                    reasons = failures.map { it.reason.ifEmpty { it.message } to it.advice }.distinct(),
                    isReinstall = true,
                )
            }
        }
    }

    private suspend fun authenticateAndApply(type: AppsType, resetToFactory: Boolean) {
        if (settings.authEnabledFlow.first()) {
            platform.requireBiometric(
                title = getString(Res.string.auth_required),
                subtitle = getString(Res.string.auth_required_description),
            ) { viewModelScope.launch { apply(type, resetToFactory) } }
        } else {
            apply(type, resetToFactory)
        }
    }

    private suspend fun apply(type: AppsType, resetToFactory: Boolean) {
        // Remembered before applying: the selection is cleared as every app succeeds.
        val attempted = if (type == AppsType.INSTALLED) appList.selectedApps.toList() else emptyList()
        val count = appList.applyToSelected(type, resetToFactory)
        if (attempted.isNotEmpty()) settings.setLastConfirmedPackages(attempted)
        val isReinstall = type == AppsType.UNINSTALLED
        if (count > 0) {
            if (!settings.hideSuccessDialogFlow.first()) {
                dialog = MainDialog.Success(count = count, isReinstall = isReinstall)
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
        dialog = MainDialog.Failure(attempts = failures.size, reasons = reasons, isReinstall = isReinstall)
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
