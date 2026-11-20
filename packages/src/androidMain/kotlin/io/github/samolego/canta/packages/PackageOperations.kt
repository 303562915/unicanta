package io.github.samolego.canta.packages

import android.content.Context
import android.content.IntentSender
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import io.github.samolego.canta.packages.OperationResult.Companion.STATUS_NO_RESULT
import java.lang.reflect.InvocationTargetException

/**
 * Uninstalls and reinstalls apps through the framework's package installer,
 * with [services] providing privileged access (Shizuku in the app, direct in
 * the ADB helper). Calls block until the installer reports back; run them
 * off the main thread.
 */
class PackageOperations(
    private val services: SystemServices,
    private val context: Context,
) {
    private companion object {
        const val TAG = "PackageOperations"

        // Hidden PackageManager constants.
        const val DELETE_SYSTEM_APP = 0x00000004
        const val INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS = 0x00400000

        /** How long to wait for the installer's status. */
        const val RESULT_TIMEOUT_MS = 30_000L

        /** Installer identity; `getMySessions` checks the installer's owner, which is shell under ADB. */
        const val INSTALLER_PACKAGE = "com.android.shell"
    }

    private val packageManager: PackageManager get() = context.packageManager

    private val privilegedPackageManager: IPackageManager by lazy {
        allowHiddenApis()
        IPackageManager.Stub.asInterface(services.service("package"))
    }

    private fun privilegedInstaller(): IPackageInstaller =
        IPackageInstaller.Stub.asInterface(services.wrap(privilegedPackageManager.packageInstaller.asBinder()))

    /**
     * Packages as the privileged identity sees them, or an empty list when the hidden call is
     * unavailable. Some ROMs (HyperOS/MIUI) hand a gutted list to the app's own PackageManager
     * until the user grants the "get installed apps" permission, which cannot be requested at
     * runtime; asking the package service directly gets past that.
     */
    fun privilegedInstalledPackages(): List<PackageInfo> = try {
        allowHiddenApis()
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()
        val slice = try {
            IPackageManager::class.java
                .getMethod("getInstalledPackages", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(privilegedPackageManager, flags, services.userId)
        } catch (_: NoSuchMethodException) {
            IPackageManager::class.java
                .getMethod("getInstalledPackages", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(privilegedPackageManager, PackageManager.MATCH_UNINSTALLED_PACKAGES, services.userId)
        }
        // ParceledListSlice is hidden: read its list reflectively.
        @Suppress("UNCHECKED_CAST")
        (slice?.javaClass?.getMethod("getList")?.invoke(slice) as? List<PackageInfo>).orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "Privileged package listing failed: $e")
        emptyList()
    }

    /**
     * Uninstalls [packageName] for the user. With [resetToFactory], an updated
     * system app first has its update removed: for system apps, the first
     * `DELETE_SYSTEM_APP` uninstall only removes the update, the second then
     * uninstalls the factory version.
     */
    fun uninstall(packageName: String, resetToFactory: Boolean): OperationResult = reportingFailure(packageName) {
        val appInfo = packageManager.getInfoForPackage(packageName)?.applicationInfo
            ?: return OperationResult(packageName, success = false, status = PackageInstaller.STATUS_FAILURE_INVALID, message = "Unknown package")
        // Only for services.userId: other users and profiles keep their copy.
        val flags = if (appInfo.isSystem) DELETE_SYSTEM_APP else 0
        val installer = packageInstaller()
        Log.i(TAG, "Uninstalling '$packageName' [system: ${appInfo.isSystem}, update: ${appInfo.hasSystemUpdate}, reset: $resetToFactory]")

        if (resetToFactory && appInfo.canResetToFactory) {
            val reset = awaitStatus(packageName) { sender -> invokeUninstall(installer, packageName, flags, sender) }
            if (!reset.success) Log.w(TAG, "Reset of '$packageName' failed (${reset.message}); uninstalling anyway")
        }
        val result = awaitStatus(packageName) { sender -> invokeUninstall(installer, packageName, flags, sender) }
        // No status, but gone (or no longer installed for the user) counts as done.
        if (result.status == STATUS_NO_RESULT && !isInstalled(packageName)) result.copy(success = true) else result
    }

    /**
     * Freezes ([disabled] = true) or unfreezes ([disabled] = false) [packageName] for the user.
     *
     * This is the reversible half: the package keeps its data, its place in the system and its
     * permissions, and comes back with another call. Unfreezing sets the state back to *default*
     * rather than *enabled*, so an app that the ROM ships disabled stays the way the ROM wanted it
     * — "undo what was done here", not "turn it on".
     */
    fun setDisabled(packageName: String, disabled: Boolean): OperationResult = reportingFailure(packageName) {
        val state = if (disabled) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        Log.i(TAG, "${if (disabled) "Freezing" else "Unfreezing"} '$packageName'")
        try {
            allowHiddenApis()
            IPackageManager::class.java
                .getMethod(
                    "setApplicationEnabledSetting",
                    String::class.java, // packageName
                    Int::class.javaPrimitiveType, // newState
                    Int::class.javaPrimitiveType, // flags
                    Int::class.javaPrimitiveType, // userId
                    String::class.java, // callingPackage
                )
                .invoke(privilegedPackageManager, packageName, state, 0, services.userId, "android")
            OperationResult(packageName, success = true, status = PackageInstaller.STATUS_SUCCESS)
        } catch (e: Exception) {
            // A ROM that refuses returns here rather than reporting asynchronously, so there is
            // no status to wait for: the message is the whole answer.
            val cause = (e as? InvocationTargetException)?.targetException ?: e
            Log.w(TAG, "Could not change '$packageName' to $state: $cause")
            OperationResult(
                packageName,
                success = false,
                status = STATUS_NEGATIVE_RESPONSE,
                message = cause.toString(),
            )
        }
    }

    /** Reinstalls [packageName] for the user, from the copy still on the device. */
    fun reinstall(packageName: String): OperationResult = reportingFailure(packageName) {
        Log.i(TAG, "Reinstalling '$packageName'")
        val installer = privilegedInstaller()
        val result = awaitStatus(packageName) { sender ->
            IPackageInstaller::class.java
                .getMethod(
                    "installExistingPackage",
                    String::class.java, // packageName
                    Int::class.javaPrimitiveType, // installFlags
                    Int::class.javaPrimitiveType, // installReason
                    IntentSender::class.java, // statusReceiver
                    Int::class.javaPrimitiveType, // userId
                    List::class.java, // whitelistedRestrictedPermissions
                )
                .invoke(
                    installer,
                    packageName,
                    INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS,
                    PackageManager.INSTALL_REASON_UNKNOWN,
                    sender,
                    services.userId,
                    null,
                )
        }
        if (result.status == STATUS_NO_RESULT && isInstalled(packageName)) result.copy(success = true) else result
    }

    private fun invokeUninstall(installer: PackageInstaller, packageName: String, flags: Int, sender: IntentSender) {
        PackageInstaller::class.java
            .getMethod("uninstall", String::class.java, Int::class.javaPrimitiveType, IntentSender::class.java)
            .invoke(installer, packageName, flags, sender)
    }

    /** Runs [operation] with a status receiver and waits for the status it reports. */
    private fun awaitStatus(packageName: String, operation: (IntentSender) -> Unit): OperationResult = reportingFailure(packageName) {
        val receiver = LocalIntentSender()
        operation(receiver.intentSender)
        val intent = receiver.await(RESULT_TIMEOUT_MS)
            ?: return OperationResult(packageName, success = false, status = STATUS_NO_RESULT, message = "No result")
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        if (status != PackageInstaller.STATUS_SUCCESS) Log.w(TAG, "'$packageName': status $status ($message)")
        OperationResult(packageName, success = status == PackageInstaller.STATUS_SUCCESS, status = status, message = message)
    }

    /**
     * Runs [operation], turning anything it throws (missing hidden APIs, a
     * dead binder, ...) into a failed result, so one package never aborts a batch.
     */
    private inline fun reportingFailure(packageName: String, operation: () -> OperationResult): OperationResult =
        try {
            operation()
        } catch (e: Exception) {
            val cause = (e as? InvocationTargetException)?.targetException ?: e
            Log.e(TAG, "Operation on '$packageName' failed", cause)
            OperationResult(packageName, success = false, status = STATUS_NO_RESULT, message = cause.toString())
        }

    private fun isInstalled(packageName: String): Boolean =
        packageManager.getInfoForPackage(packageName)?.applicationInfo?.isInstalledForUser == true

    /** A [PackageInstaller] backed by the privileged installer (constructors differ by SDK). */
    private fun packageInstaller(): PackageInstaller {
        val installer = privilegedInstaller()
        return when {
            Build.VERSION.SDK_INT > Build.VERSION_CODES.R ->
                PackageInstaller::class.java
                    .getConstructor(IPackageInstaller::class.java, String::class.java, String::class.java, Int::class.javaPrimitiveType)
                    .newInstance(installer, INSTALLER_PACKAGE, null, services.userId)
            else ->
                PackageInstaller::class.java
                    .getConstructor(IPackageInstaller::class.java, String::class.java, Int::class.javaPrimitiveType)
                    .newInstance(installer, INSTALLER_PACKAGE, services.userId)
        }
    }
}
