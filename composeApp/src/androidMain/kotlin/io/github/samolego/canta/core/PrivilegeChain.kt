package io.github.samolego.canta.core
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import io.github.samolego.canta.core.root.RootCantaHandler
import io.github.samolego.canta.core.root.suBinaryPath
import io.github.samolego.canta.core.shizuku.ShizukuServices
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.packages.OperationResult
import io.github.samolego.canta.util.DeviceContext
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One entry point over the privileges an Android phone can offer, in the order they are worth
 * trying: a `su` that answers (Magisk, KernelSU, APatch, or a build that ships its own su), then
 * Stellar/Shizuku, and — when it is the one that answered — Dhizuku, which shares the DeviceOwner
 * role rather than a shell and therefore only carries the freeze half of the app.
 *
 * The point is that the user should not have to know which one they have, and should not be stuck
 * when the one the app guessed is refused: [picked] asks root once, on the first call that needs a
 * device, and moves on if that answer is no. A phone with no `su` binary at all never even builds a
 * root handler, so the chain is then just the two runtime apps with one indirection.
 *
 * Everything that only reports state reads [active] directly: a property cannot suspend, and by the
 * time the UI asks, [loadApps] has already settled the question. Everything that touches the device
 * goes through [picked], so a refusal cannot leave half the calls on a privilege that is not there.
 *
 * Installs are the one exception: they ask every time which privilege can do the job fastest, see
 * [installRoute], because the route worth using for a reinstall is not always the settled one.
 */
class PrivilegeChain(
    private val root: RootCantaHandler?,
    private val shizuku: AndroidCantaHandler,
    private val packageManager: PackageManager,
    /**
     * The device-owner route, when it is worth offering: null keeps the chain exactly as it was
     * before Dhizuku support, which is what the desktop-facing tests and older builds see.
     */
    private val dhizuku: DhizukuCantaHandler? = null,
) : CantaHandler {

    private companion object {
        const val TAG = "PrivilegeChain"
    }

    /**
     * Starts on root when the phone has a su binary: that is the privilege to try first. A phone
     * without one starts on whichever runtime app is already authorized, and falls back to Shizuku
     * when neither is — its setup page is the one that can actually install and start something.
     */
    @Volatile
    private var active: CantaHandler = root ?: authorizedRuntime(shizuku, dhizuku)

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
                active = when {
                    candidate != null && worthTrying && candidate.prepare() -> candidate
                    // Someone already granted one of the runtime apps: use it rather than asking
                    // for a grant that has been given. Shizuku first, since it can do more.
                    shizuku.isAuthorized -> shizuku
                    dhizuku?.isAuthorized == true -> dhizuku
                    else -> shizuku
                }
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
                // Reference comparison on purpose: this list is built from the same two handlers
                // the chain holds, so identity is the exact answer to "which one is in use". The
                // grant is checked too: root that has not answered yet is not in use, it is about
                // to be asked.
                current = active === handler && handler.isAuthorized,
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
            current = active === shizuku && shizuku.isAuthorized,
        )
        // No root entry at all when the phone has no su binary: the chain is not even built with a
        // root handler then, and a row offering something that does not exist would be a dead end.
        // Dhizuku is only listed once it is actually there (or already granting us): it is a third
        // manager to install, so offering it to every unrooted phone would be noise.
        val dhizukuChoice = dhizuku?.let { handler ->
            val status = handler.privilegeStatus
            if (status == PrivilegeStatus.NOT_AVAILABLE && !handler.isAuthorized) {
                null
            } else {
                PrivilegeChoice(
                    kind = PrivilegeKind.DHIZUKU,
                    status = status,
                    installed = status != PrivilegeStatus.NOT_AVAILABLE,
                    current = active === handler && handler.isAuthorized,
                )
            }
        }
        return listOfNotNull(rootChoice, shizukuChoice, dhizukuChoice)
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

    /**
     * Reinstalls through the fastest privilege that is available right now — see [installRoute].
     *
     * A batch that the first route refuses is retried once on the other one, and each package is
     * emitted exactly once either way: the batch log counts results, so a package that reached the
     * list twice would be counted twice.
     */
    override fun reinstallApps(packageNames: List<String>): Flow<OperationResult> = flow {
        val primary = installRoute()
        LogUtils.i(TAG, "Installing ${packageNames.size} package(s) through ${primary.privilegeKind.wire}")
        val answered = mutableSetOf<String>()
        val failures = linkedMapOf<String, OperationResult>()
        primary.reinstallApps(packageNames).collect { result ->
            answered += result.packageName
            if (result.success) emit(result) else failures[result.packageName] = result
        }
        // A route that never connects hands back an empty flow: without this, those packages would
        // reach the batch as neither a success nor a failure, and the dialog would explain nothing.
        packageNames.distinct().filterNot { it in answered }.forEach { name ->
            failures[name] = OperationResult(
                packageName = name,
                success = false,
                status = OperationResult.STATUS_NO_RESULT,
                message = "No result from the install",
            )
        }
        if (failures.isEmpty()) return@flow
        val retry = retryRoute(primary)
        if (retry == null) {
            failures.values.forEach { emit(it) }
            return@flow
        }
        LogUtils.i(
            TAG,
            "${failures.size} install(s) refused through ${primary.privilegeKind.wire}; " +
                "retrying through ${retry.privilegeKind.wire}",
        )
        // Emitted as they arrive, not collected first: the caller counts progress off each result,
        // and a slow route (root cold-starting the helper) would otherwise freeze the counter.
        val retried = mutableSetOf<String>()
        retry.reinstallApps(failures.keys.toList()).collect { result ->
            retried += result.packageName
            emit(result)
        }
        failures.values.filterNot { it.packageName in retried }.forEach { emit(it) }
    }.flowOn(Dispatchers.IO)

    /**
     * The route a reinstall takes: the fastest privilege this phone can offer right now, which is
     * not necessarily the one the chain settled on.
     *
     * A reinstall through root costs a `su -c` that cold-starts `app_process` and the helper for
     * every batch; Shizuku's binder answers inside this process, so an install is the one operation
     * where the runtime is worth preferring even on a phone that runs everything else through root.
     *
     * The binder is checked live: the cached grant survives the runtime being killed, and a batch
     * sent into a dead binder fails every package with the same "start it again".
     */
    private suspend fun installRoute(): CantaHandler {
        if (shizukuCanInstallNow()) return shizuku
        val rootHandler = root
        if (rootHandler != null && rootHandler.prepare()) return rootHandler
        if (shizukuCanInstallNow()) return shizuku
        // Neither answered: the settled route reports its own refusal, which is the one whose
        // setup page the user has to open.
        return active
    }

    /**
     * Whether Shizuku is up, granted, and pointed at the right user — i.e. whether an install sent
     * through it can succeed right now.
     *
     * The liveness check comes first and [isAuthorized] is guarded: the cached grant outlives a
     * runtime that was killed, and asking a dead binder throws, which would abort the whole batch.
     */
    private fun shizukuCanInstallNow(): Boolean =
        shizuku.isRuntimeUp &&
            runCatching { shizuku.isAuthorized }.getOrDefault(false) &&
            shizukuActsOnSelectedUser()

    /**
     * Whether Shizuku would install into the same user the phone is being managed for.
     *
     * The root route can be pointed at another user or a work profile ([selectUser]); Shizuku acts
     * on the user Canta itself runs in. When those two differ, the faster binder would put the app
     * back into the wrong profile, so the route that owns the choice keeps the job.
     */
    private fun shizukuActsOnSelectedUser(): Boolean {
        val target = selectedUserId ?: return true
        return target == ShizukuServices.userId
    }

    /** The other route worth retrying a refused install on, or null when there is only one. */
    private suspend fun retryRoute(primary: CantaHandler): CantaHandler? = when {
        primary === shizuku -> {
            // Asked again rather than trusted from the earlier answer: [RootCantaHandler.prepare]
            // caches a "no" for the whole process, and a grant the user gave since must count.
            val handler = root
            if (handler != null && handler.prepare()) handler else null
        }
        primary === root -> shizuku.takeIf { shizukuCanInstallNow() }
        else -> null
    }

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
            // Already allowed: requesting again would put Shizuku's own prompt over a grant that
            // exists, and switching back from Dhizuku is not a new question.
            if (!shizuku.isAuthorized) shizuku.requestAuthorization()
            shizuku.isAuthorized
        }

        PrivilegeKind.DHIZUKU -> {
            val candidate = dhizuku ?: return false
            // Asked again rather than trusted from the last look: the grant lives in Dhizuku's own
            // screen, and the user may have just come back from it. A grant that is already there
            // is not asked for a second time — this is also the switch back from Shizuku.
            val granted = candidate.isAuthorized || candidate.grant()
            if (granted) {
                settleLock.withLock {
                    active = candidate
                    settled = true
                }
            }
            granted
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
 * The runtime app that already answered "yes" when the app started, Shizuku first because it can do
 * more than the device owner route. A phone with neither falls back to Shizuku: its setup page is
 * the one that can install and start something.
 */
private fun authorizedRuntime(
    shizuku: AndroidCantaHandler,
    dhizuku: DhizukuCantaHandler?,
): CantaHandler = when {
    shizuku.isAuthorized -> shizuku
    dhizuku?.isAuthorized == true -> dhizuku
    else -> shizuku
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