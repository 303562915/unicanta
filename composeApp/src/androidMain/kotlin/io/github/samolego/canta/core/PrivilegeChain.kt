package io.github.samolego.canta.core

import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import io.github.samolego.canta.core.root.RootCantaHandler
import io.github.samolego.canta.core.root.suBinaryPath
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.packages.OperationResult
import io.github.samolego.canta.util.DeviceContext
import io.github.samolego.canta.util.DeviceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One entry point over the privileges an Android phone can offer, in the order they are worth
 * trying: a `su` that answers (Magisk, KernelSU, APatch, or a build that ships its own su), and
 * otherwise Stellar/Shizuku.
 *
 * The point is that the user should not have to know which one they have, and should not be stuck
 * when the one the app guessed is refused: [picked] asks root once, on the first call that needs a
 * device, and moves to Shizuku for good if that answer is no. A phone with no `su` binary at all
 * never even builds a root handler, so the chain is then just Shizuku with one indirection.
 *
 * Everything that only reports state reads [active] directly: a property cannot suspend, and by the
 * time the UI asks, [loadApps] has already settled the question. Everything that touches the device
 * goes through [picked], so a refusal cannot leave half the calls on a privilege that is not there.
 */
class PrivilegeChain(
    private val root: RootCantaHandler?,
    private val shizuku: AndroidCantaHandler,
    private val packageManager: PackageManager,
) : CantaHandler {

    /** Starts on root when the phone has a su binary: that is the privilege to try first. */
    @Volatile
    private var active: CantaHandler = root ?: shizuku

    @Volatile
    private var settled: Boolean = root == null

    private val settleLock = Mutex()

    /**
     * Whether a root manager's app is on the phone. Its `su` is mounted by that manager's daemon, so
     * "manager installed" is the case where asking is still worth it even if no `su` file is visible
     * from this process right now.
     */
    private fun rootManagerInstalled(): Boolean =
        ROOT_MANAGERS.any { packageManager.getLaunchIntentForPackage(it) != null }

    /**
     * The privilege to use, decided once: root if it answers, Shizuku if it does not.
     *
     * Held under a lock because the first call that needs a device can come from more than one
     * coroutine (the app list and the setup page both ask), and two `su` prompts would be two
     * dialogs the user has to answer separately.
     */
    private suspend fun picked(): CantaHandler {
        if (settled) return active
        settleLock.withLock {
            if (!settled) {
                val candidate = root
                // Worth one attempt without a su file: Magisk, KernelSU and APatch mount `su` only
                // while their daemon runs, and a phone whose manager app is installed but whose `su`
                // is not there yet is exactly the phone that would otherwise never see a prompt.
                val worthTrying = suBinaryPath() != null || rootManagerInstalled()
                active = if (candidate != null && worthTrying && candidate.prepare()) candidate else shizuku
                settled = true
            }
        }
        return active
    }

    override val isAuthorized: Boolean get() = active.isAuthorized

    override val privilegeKind: PrivilegeKind get() = active.privilegeKind

    override val privilegeStatus: PrivilegeStatus get() = active.privilegeStatus

    /**
     * Both routes this phone has, root first, so the settings page can offer the choice instead of
     * showing what the chain happened to pick.
     *
     * Reads state only. Root's status comes from [RootCantaHandler.isAuthorized], which stays false
     * until root has answered: calling [RootCantaHandler.prepare] here would put the root manager's
     * prompt over the settings page merely because the user opened a list.
     */
    override suspend fun privilegeChoices(): List<PrivilegeChoice> {
        val rootChoice = root?.let { handler ->
            // Checked again rather than trusted from construction: root can be taken away (the
            // manager was uninstalled, the ROM was updated) between two starts of this process.
            // A manager app with no visible `su` still counts as available: its daemon mounts the
            // binary, and that is the case where the user needs the grant button most.
            val hasSu = suBinaryPath() != null || rootManagerInstalled()
            PrivilegeChoice(
                kind = PrivilegeKind.ROOT,
                status = when {
                    !hasSu -> PrivilegeStatus.NOT_AVAILABLE
                    handler.isAuthorized -> PrivilegeStatus.ACTIVE
                    else -> PrivilegeStatus.NOT_AUTHORIZED
                },
                installed = hasSu,
            )
        }
        val shizukuStatus = shizuku.privilegeStatus
        val shizukuChoice = PrivilegeChoice(
            kind = PrivilegeKind.SHIZUKU,
            status = shizukuStatus,
            // NOT_AVAILABLE is exactly the "neither manager package is on this phone" answer: that
            // status reads the package list for it, and the two answers that skip that check (Sui
            // granting Canta directly, and Canta already authorized) both mean a runtime is there.
            installed = shizukuStatus != PrivilegeStatus.NOT_AVAILABLE,
        )
        // No root entry at all when the phone has no su binary: the chain is not even built with a
        // root handler then, and a row offering something that does not exist would be a dead end.
        return listOfNotNull(rootChoice, shizukuChoice)
    }

    override suspend fun loadApps(): List<AppInfo> = picked().loadApps()

    override val appListRestricted: Boolean get() = active.appListRestricted

    override fun openPackageListSettings(): Boolean = active.openPackageListSettings()

    override val supportsPackageListSettings: Boolean get() = active.supportsPackageListSettings

    override fun loadIcons(): Flow<Pair<String, ImageBitmap>> = active.loadIcons()

    override suspend fun canResetToFactory(packageName: String): Boolean =
        picked().canResetToFactory(packageName)

    override fun uninstallApps(packageNames: List<String>, resetToFactory: Boolean): Flow<OperationResult> =
        active.uninstallApps(packageNames, resetToFactory)

    override fun reinstallApps(packageNames: List<String>): Flow<OperationResult> =
        active.reinstallApps(packageNames)

    override fun setDisabledApps(packageNames: List<String>, disabled: Boolean): Flow<OperationResult> =
        active.setDisabledApps(packageNames, disabled)

    override val supportsDisabling: Boolean get() = active.supportsDisabling

    override val supportsTweakCommands: Boolean get() = active.supportsTweakCommands

    override suspend fun runTweakCommand(script: String): TweakRun = picked().runTweakCommand(script)

    override suspend fun bootId(): String? = picked().bootId()

    override suspend fun buildFingerprint(): String? = picked().buildFingerprint()

    override suspend fun deviceKey(): String? = picked().deviceKey()

    override suspend fun installedSinceBoot(packageName: String): Boolean? =
        picked().installedSinceBoot(packageName)

    override suspend fun deviceContext(): DeviceContext? = picked().deviceContext()

    override suspend fun deviceProfile(): DeviceProfile? = picked().deviceProfile()

    override suspend fun occupiedBy(packageNames: Collection<String>): Map<String, List<Occupancy>> =
        picked().occupiedBy(packageNames)

    override suspend fun packageExists(packageName: String): Boolean =
        picked().packageExists(packageName)

    override suspend fun openAppDetails(packageName: String) {
        picked().openAppDetails(packageName)
    }

    override suspend fun requestAuthorization() {
        picked().requestAuthorization()
    }

    /**
     * Grants [kind] and moves the chain onto it when it answers, undoing the earlier "no".
     *
     * Without this, a phone that settled on Shizuku at startup would keep using it even after the
     * user granted root from the picker: the decision is made once per process, on purpose, and a
     * grant has to be the one thing that may overturn it.
     */
    override suspend fun authorize(kind: PrivilegeKind): Boolean = when (kind) {
        PrivilegeKind.ROOT -> {
            val candidate = root ?: return false
            // The press is a new question: without this the cached "no" from startup answers it
            // and no `su` runs, so nothing is shown and the button looks broken.
            candidate.forgetRootAnswer()
            val granted = candidate.prepare()
            if (granted) {
                settleLock.withLock {
                    active = candidate
                    settled = true
                }
            }
            granted
        }

        PrivilegeKind.SHIZUKU -> {
            settleLock.withLock {
                active = shizuku
                settled = true
            }
            shizuku.requestAuthorization()
            shizuku.isAuthorized
        }

        PrivilegeKind.NONE -> false
    }

    override val deviceDiscovery: DeviceDiscovery get() = active.deviceDiscovery

    override val requiresDeviceSelection: Boolean get() = active.requiresDeviceSelection

    override suspend fun availableDevices(): List<CantaDevice> = active.availableDevices()

    override suspend fun selectDevice(device: CantaDevice) {
        picked().selectDevice(device)
    }

    override suspend fun pickSystemDevice(): CantaDevice? = active.pickSystemDevice()

    override suspend fun availableUsers(): List<DeviceUser> = picked().availableUsers()

    override val selectedUserId: Int? get() = active.selectedUserId

    override suspend fun selectUser(id: Int) {
        picked().selectUser(id)
    }

    override val connectionSupported: Boolean get() = active.connectionSupported

    override val lastConnectionFailure: DeviceConnectionFailure? get() = active.lastConnectionFailure
}

/**
 * Package names of the root managers this build knows. Magisk is the one most phones carry; the
 * alpha and delta builds ship under their own ids; KernelSU, its Next fork and APatch are the
 * kernel-side ones. Any of them means "ask for root", file on disk or not.
 */
private val ROOT_MANAGERS = listOf(
    // Magisk: stable, alpha and delta all ship as separate packages.
    "com.topjohnwu.magisk",
    "io.github.vvb2060.magisk",
    "io.github.huskydg.magisk",
    // KernelSU and its Next fork.
    "me.weishu.kernelsu",
    "com.rifsxd.ksunext",
    // APatch.
    "me.bmax.apatch",
)