package io.github.samolego.canta.core

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import io.github.samolego.canta.core.dhizuku.DhizukuPrivilege
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.packages.OperationResult
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Android [CantaHandler] on Dhizuku: the route for a phone that shares the DeviceOwner role instead
 * of a shell.
 *
 * What it can do is one half of what the other routes do, and that half is real:
 *
 *  - the app list, the records and everything else read-only comes from [reads] unchanged;
 *  - freezing and thawing work, through `DevicePolicyManager.setPackagesSuspended` — the pause is
 *    a system-level stop the app cannot undo on its own;
 *  - uninstalling and reinstalling do not, and say so instead of pretending: the device owner has
 *    no `DELETE_PACKAGES` and no `INSTALL_PACKAGES`, so `PackageInstaller` and
 *    `IPackageManager.deletePackageAsUser` both refuse it. Every app that supports Dhizuku has the
 *    same limit; the fix is Shizuku or root, and the failure text points there.
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

        /**
         * A message the failure mapping recognises (`OperationFailure.keywordFailure`). The status
         * code alone would read as "an unknown failure", which explains nothing about the route.
         */
        const val ROUTE_CANNOT_INSTALL = "dhizuku route cannot install or remove packages"

        /** Same idea for the freeze half: device owner refused this package. */
        const val SUSPEND_REFUSED = "dhizuku refused to suspend this package"
    }

    private val packageManager: PackageManager get() = context.packageManager

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

    /** Freezing is the one write this route has, and it is the reversible one. */
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

    /** The whole point of this route: the device owner role cannot remove a package. */
    override val supportsUninstall: Boolean get() = false

    override fun uninstallApps(packageNames: List<String>, resetToFactory: Boolean): Flow<OperationResult> =
        refused(packageNames)

    override fun reinstallApps(packageNames: List<String>): Flow<OperationResult> = refused(packageNames)

    /** Every package refused for the same reason, one result each, so the batch still reports per app. */
    private fun refused(packageNames: List<String>): Flow<OperationResult> = flow {
        packageNames.forEach { packageName ->
            emit(
                OperationResult(
                    packageName = packageName,
                    success = false,
                    status = OperationResult.STATUS_NEGATIVE_RESPONSE,
                    message = ROUTE_CANNOT_INSTALL,
                )
            )
        }
    }

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
}
