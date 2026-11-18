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
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.auth_required
import io.github.samolego.canta.generated.resources.auth_required_description
import io.github.samolego.canta.generated.resources.cannot_uninstall_canta
import io.github.samolego.canta.generated.resources.device_unreachable
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
    ) : MainDialog
    data class Success(val count: Int, val isReinstall: Boolean) : MainDialog

    /** Packages that came back although the build did not change: a rescue of the last batch. */
    data class RestoreSuspects(val packages: List<String>) : MainDialog

    /**
     * Removing these is known to strand *this* device (model/ROM specific rules), so they are
     * held back unless unsafe removals are allowed in the settings.
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
) : ViewModel() {

    var dialog by mutableStateOf<MainDialog?>(null)
        private set

    /** Shipped device-aware rules; loaded once. */
    private var rules: SafetyRules? = null

    private suspend fun safetyRules(): SafetyRules = rules ?: SafetyRules.bundled().also { rules = it }

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
        if (suspects.isEmpty() || dialog != null) return
        dialog = MainDialog.RestoreSuspects(suspects)
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
        if (type == AppsType.INSTALLED && settings.confirmBeforeUninstallFlow.first()) {
            val selected = appList.selectedApps.toList()
            if (selected.isEmpty()) return
            // Device-specific safety first: a package that strands *this* model/ROM never reaches
            // the normal confirmation, whatever the generic badge says.
            val verdicts = safetyRules().verdicts(selected, handler.deviceProfile())
            val blocked = verdicts.values.filter { it.status == SafetyStatus.CRITICAL }
            val risky = verdicts.values.filter { it.status == SafetyStatus.RISKY }
            if (blocked.isNotEmpty() || risky.isNotEmpty()) {
                dialog = MainDialog.UnsafeSelection(
                    blocked = blocked,
                    risky = risky,
                    deviceLabel = handler.deviceProfile()?.label().orEmpty(),
                    unknownCount = selected.count { it !in verdicts },
                )
                return
            }
            val known = settings.lastConfirmedPackagesFlow.first().toSet()
            dialog = MainDialog.ConfirmUninstall(
                appCount = selected.size,
                canResetToFactory = selected.any { handler.canResetToFactory(it) },
                bootloopRisk = selected.any { appList.scores.value[it]?.verdict == Verdict.BOOTLOOP },
                // With no batch confirmed yet everything is new, which would flag every first run.
                newPackages = if (known.isEmpty()) emptyList() else selected.filterNot { it in known },
            )
        } else {
            authenticateAndApply(type, resetToFactory = false)
        }
    }

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
        if (count > 0 && !settings.hideSuccessDialogFlow.first()) {
            dialog = MainDialog.Success(count = count, isReinstall = type == AppsType.UNINSTALLED)
        }
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
