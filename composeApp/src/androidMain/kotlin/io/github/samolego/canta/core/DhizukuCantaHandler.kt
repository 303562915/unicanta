package io.github.samolego.canta.core

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import io.github.samolego.canta.core.dhizuku.DhizukuPrivilege
import io.github.samolego.canta.core.dhizuku.DhizukuServices
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.packages.OperationResult
import io.github.samolego.canta.packages.PackageOperations
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Android [CantaHandler] on Dhizuku: the route for a phone that shares the DeviceOwner role instead
 * of a shell.
 *
 * Two of the three writes work, each through the door the framework opens for an owner:
 *
 *  - **uninstall** runs through [PackageOperations] over [DhizukuServices], so the installer call is
 *    executed by Dhizuku itself. The package installer deletes silently when the calling uid is the
 *    device owner (`canSilentlyInstallPackage`), which is why the flags, the status callback and
 *    the reset-to-factory sequence are the same code the Shizuku route uses;
 *  - **freeze / thaw** goes through `DevicePolicyManager.setPackagesSuspended` from inside Dhizuku's
 *    process — a pause the app cannot undo on its own;
 *  - **reinstall** is refused with a plain reason: `installExistingPackageAsUser` demands
 *    `INSTALL_PACKAGES`, and no owner carve-out exists for it. Putting an app back needs Shizuku or
 *    root.
 *
 * [reads] is the same [AndroidCantaHandler] the Shizuku route uses, so the list, the icons and the
 * device facts cannot drift between the two routes.
 *
 * @param context the app context, for the package manager.
 * @param reads the handler whose read paths this one borrows.
 */
class DhizukuCantaHandler(
    private val context: Context,
    private val reads: AndroidCantaHandler,
) : CantaHandler by reads {

    private companion object {
        const val TAG = "DhizukuCantaHandler"

        /** A message the failure mapping recognises (`OperationFailure.keywordFailure`). */
        const val ROUTE_CANNOT_INSTALL = "dhizuku route cannot install existing packages"

        /** Same idea for the freeze half: device owner refused this package. */
        const val SUSPEND_REFUSED = "dhizuku refused to suspend this package"

        /** The grant is missing or Dhizuku went away between two calls. */
        const val NO_PRIVILEGE = "dhizuku is not authorized"
    }

    private val packageManager: PackageManager get() = context.packageManager

    /**
     * The installer calls, run as Dhizuku. Built lazily and keyed on the owner package it reports:
     * the framework rejects a claimed package that does not belong to the calling uid, and a fork
     * ships under a name of its own.
     */
    private val operations: PackageOperations? by lazy {
        val owner = DhizukuPrivilege.ownerPackage()
        if (owner == null) {
            LogUtils.w(TAG, "Dhizuku did not name its owner package; uninstall is unavailable")
            null
        } else {
            PackageOperations(DhizukuServices, context, installerPackage = owner)
        }
    }

    override val isAuthorized: Boolean get() = DhizukuPrivilege.isActive()

    /** This handler is the Dhizuku route; the setup pages must describe the device owner, not a shell. */
    override val privilegeKind: PrivilegeKind get() = PrivilegeKind.DHIZUKU

    override val privilegeStatus: PrivilegeStatus get() = DhizukuPrivilege.status(packageManager)

    /**
     * The same list as the other Android route, with one correction: a package this phone keeps
     * paused reads as *enabled* to `PackageManager` (suspension is policy state, not the component
     * state Canta reads), so it would otherwise show up as a normal installed app and the frozen
     * tab would be empty. The owner's own answer is asked for the whole batch at once.
     */
    override suspend fun loadApps(): List<AppInfo> {
        val apps = reads.loadApps()
        if (apps.isEmpty()) return apps
        val paused = runCatching { DhizukuPrivilege.suspendedAmong(apps.map { it.packageName }) }
            .onFailure { LogUtils.w(TAG, "Could not read the paused packages: $it") }
            .getOrDefault(emptySet())
        if (paused.isEmpty()) return apps
        LogUtils.i(TAG, "${paused.size} packages are paused through Dhizuku")
        return apps.map { app ->
            if (!app.isDisabled && app.packageName in paused) app.withDisabled(true) else app
        }
    }

    /**
     * Removes every package for the user, one status per package, with the same reset-to-factory
     * sequence the Shizuku route runs: an updated system app first loses its update.
     */
    override fun uninstallApps(packageNames: List<String>, resetToFactory: Boolean): Flow<OperationResult> = flow {
        val installer = operations
        packageNames.forEach { packageName ->
            emit(
                if (installer == null || !isAuthorized) {
                    refused(packageName, NO_PRIVILEGE)
                } else {
                    installer.uninstall(packageName, resetToFactory)
                }
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Not available on this route, and said so: restoring an app asks the framework for
     * `INSTALL_PACKAGES`, which a device owner does not hold.
     */
    override fun reinstallApps(packageNames: List<String>): Flow<OperationResult> = flow {
        packageNames.forEach { emit(refused(it, ROUTE_CANNOT_INSTALL)) }
    }

    /** Freezing is the one write that needs the owner role itself, not the installer. */
    override fun setDisabledApps(packageNames: List<String>, disabled: Boolean): Flow<OperationResult> = flow {
        packageNames.forEach { packageName ->
            val ok = DhizukuPrivilege.setSuspended(packageName, disabled)
            emit(
                OperationResult(
                    packageName = packageName,
                    success = ok,
                    status = if (ok) PackageInstaller.STATUS_SUCCESS else OperationResult.STATUS_NEGATIVE_RESPONSE,
                    message = if (ok) "" else SUSPEND_REFUSED,
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    override val supportsDisabling: Boolean get() = true

    /** The tools page needs a shell; a device owner is not one. The page hides itself on this flag. */
    override val supportsTweakCommands: Boolean get() = false

    override suspend fun runTweakCommand(script: String): TweakRun =
        TweakRun(ok = false, failure = TweakFailure.Unsupported)

    /** Raises Dhizuku's approval screen, which is where the grant is given. */
    override suspend fun requestAuthorization() {
        DhizukuPrivilege.requestPermission()
    }

    override suspend fun authorize(kind: PrivilegeKind): Boolean = when (kind) {
        PrivilegeKind.DHIZUKU -> grant()
        else -> false
    }

    /** Asks for the grant and reports whether it arrived; the chain moves onto this route on `true`. */
    suspend fun grant(): Boolean = DhizukuPrivilege.requestPermission()

    /** One package the route refuses before trying, with the reason the mapping turns into advice. */
    private fun refused(packageName: String, message: String): OperationResult = OperationResult(
        packageName = packageName,
        success = false,
        status = OperationResult.STATUS_NEGATIVE_RESPONSE,
        message = message,
    )
}
