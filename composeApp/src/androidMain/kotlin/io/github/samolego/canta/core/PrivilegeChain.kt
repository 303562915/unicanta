package io.github.samolego.canta.core

import androidx.compose.ui.graphics.ImageBitmap
import io.github.samolego.canta.core.root.RootCantaHandler
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
) : CantaHandler {

    /** Starts on root when the phone has a su binary: that is the privilege to try first. */
    @Volatile
    private var active: CantaHandler = root ?: shizuku

    @Volatile
    private var settled: Boolean = root == null

    private val settleLock = Mutex()

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
                active = if (candidate != null && candidate.prepare()) candidate else shizuku
                settled = true
            }
        }
        return active
    }

    override val isAuthorized: Boolean get() = active.isAuthorized

    override val privilegeKind: PrivilegeKind get() = active.privilegeKind

    override val privilegeStatus: PrivilegeStatus get() = active.privilegeStatus

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