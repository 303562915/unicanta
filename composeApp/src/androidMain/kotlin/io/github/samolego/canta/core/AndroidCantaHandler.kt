package io.github.samolego.canta.core

import io.github.samolego.canta.util.BOOT_COUNT_PREFIX
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.samolego.canta.core.shizuku.ShizukuPermission
import io.github.samolego.canta.core.shizuku.ShizukuServices
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.packages.OperationResult
import io.github.samolego.canta.packages.PackageDetails
import io.github.samolego.canta.packages.PackageOperations
import io.github.samolego.canta.packages.canResetToFactory
import io.github.samolego.canta.packages.getAllPackages
import io.github.samolego.canta.packages.getInfoForPackage
import io.github.samolego.canta.packages.inDisplayOrder
import io.github.samolego.canta.packages.loadIconBitmap
import io.github.samolego.canta.packages.readSafely
import io.github.samolego.canta.packages.toPackageDetails
import io.github.samolego.canta.ui.component.APP_ICON_SIZE
import io.github.samolego.canta.util.DeviceContext
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.deviceContextOf
import io.github.samolego.canta.util.parseGetprop
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Android [CantaHandler]: reads packages in-process and changes them through
 * Shizuku, with the same [PackageOperations] the ADB helper uses.
 */
class AndroidCantaHandler(
    private val context: Context,
) : CantaHandler {

    private companion object {
        const val TAG = "AndroidCantaHandler"

        /**
         * Every Android device has more packages than this; fewer means the ROM is hiding them
         * (or something is very wrong), so the privileged list is asked for as well.
         */
        const val MIN_PLAUSIBLE_PACKAGES = 20

        /** Intent MIUI/HyperOS use for their per-app permission editor. */
        const val MIUI_PERMISSION_EDITOR = "miui.intent.action.APP_PERM_EDITOR"
    }

    /**
     * [local] plus whatever only the privileged identity can see. The extras carry no label when
     * the ROM hides app resources too; the list falls back to the package name.
     */
    private fun restrictedFallback(local: List<PackageInfo>): List<PackageInfo> {
        if (!isAuthorized) return local
        val privileged = operations.privilegedInstalledPackages()
        if (privileged.isEmpty()) return local
        val known = local.mapTo(HashSet()) { it.packageName }
        val extra = privileged.filterNot { it.packageName in known }
        if (extra.isNotEmpty()) {
            LogUtils.w(TAG, "Package list from the ROM had ${local.size} entries; added ${extra.size} from the privileged one")
        }
        return local + extra
    }

    /**
     * Opens the ROM's permission screen. HyperOS/MIUI put "get installed apps" under the app's
     * other permissions; the editor is reachable with their own intent, and the standard app
     * details page is the fallback.
     */
    override fun openPackageListSettings(): Boolean {
        val detail = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val editor = Intent(MIUI_PERMISSION_EDITOR)
            .putExtra("extra_pkgname", context.packageName)
            .putExtra("packageName", context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (intent in listOf(editor, detail)) {
            val error = runCatching { context.startActivity(intent) }.exceptionOrNull() ?: return true
            LogUtils.w(TAG, "Cannot open $intent: $error")
        }
        return false
    }

    private val packageManager: PackageManager get() = context.packageManager

    private val operations by lazy { PackageOperations(ShizukuServices, context) }

    /**
     * Runs the hundreds of package reads and icon renders. Not
     * [Dispatchers.Default]: Compose resources load there while the main
     * thread waits for them (`runBlocking` in `stringResource`), so a queue of
     * our work on it would freeze the UI until the queue drains.
     */
    private val packageDispatcher = Dispatchers.IO.limitedParallelism(Runtime.getRuntime().availableProcessors())

    private val iconSizePx: Int
        get() = (APP_ICON_SIZE.value * context.resources.displayMetrics.density).roundToInt()

    /**
     * Set when the ROM handed us a gutted package list: HyperOS/MIUI keep their own "get installed
     * apps" permission, off by default and not requestable at runtime.
     */
    @Volatile
    private var restrictedList = false

    override val appListRestricted: Boolean get() = restrictedList

    override val supportsPackageListSettings: Boolean get() = true

    /** The packages from the last [loadApps], whose icons [loadIcons] renders. */
    @Volatile
    private var packages: List<Pair<PackageDetails, ApplicationInfo?>> = emptyList()

    override val isAuthorized: Boolean
        get() = ShizukuPermission.isCantaAuthorized()

    override val privilegeStatus: PrivilegeStatus
        get() = ShizukuPermission.privilegeStatus(packageManager)

    override suspend fun loadApps(): List<AppInfo> = withContext(packageDispatcher) {
        // Reading labels loads each app's resources, which is slow on a cold
        // start; read packages in parallel, like the ADB helper does.
        val local = packageManager.getAllPackages()
        val all = if (local.size < MIN_PLAUSIBLE_PACKAGES) restrictedFallback(local) else local
        restrictedList = all.size > local.size && local.size < MIN_PLAUSIBLE_PACKAGES
        // Logged so a report can tell "the ROM hid the list" from "the list really is short".
        LogUtils.i(TAG, "Packages: ${local.size} from PackageManager, ${all.size} after the privileged fallback")
        packages = all
            .map { info -> async { readSafely(info) { info.toPackageDetails(packageManager) to info.applicationInfo } } }
            .awaitAll()
            .filterNotNull()
        packages.map { (details, _) -> AppInfo(details) }
    }

    /**
     * Renders icons in parallel but emits them in display order, so the list
     * fills in top-down while the rest are still rendering.
     */
    override fun loadIcons(): Flow<Pair<String, ImageBitmap>> = flow {
        val appInfos = packages.associate { (details, appInfo) -> details.packageName to appInfo }
        val sizePx = iconSizePx
        coroutineScope {
            val icons = packages.map { it.first }.inDisplayOrder().map { details ->
                async(packageDispatcher) {
                    appInfos[details.packageName]
                        ?.let { packageManager.loadIconBitmap(it, sizePx) }
                        ?.let { details.packageName to it.asImageBitmap() }
                }
            }
            icons.forEach { icon -> icon.await()?.let { emit(it) } }
        }
    }

    override suspend fun canResetToFactory(packageName: String): Boolean =
        packageManager.getInfoForPackage(packageName)?.applicationInfo?.canResetToFactory == true

    override fun uninstallApps(packageNames: List<String>, resetToFactory: Boolean): Flow<OperationResult> =
        flow { packageNames.forEach { emit(operations.uninstall(it, resetToFactory)) } }.flowOn(Dispatchers.IO)

    override fun reinstallApps(packageNames: List<String>): Flow<OperationResult> =
        flow { packageNames.forEach { emit(operations.reinstall(it)) } }.flowOn(Dispatchers.IO)

    override suspend fun requestAuthorization() {
        val deferred = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) {
            ShizukuPermission.requestShizukuPermission { result ->
                deferred.complete(result == PackageManager.PERMISSION_GRANTED)
            }
        }
        deferred.await()
    }

    /**
     * The kernel's boot id. Shizuku here only exposes binder calls, not a
     * shell, and the file is readable by apps anyway; falls back to the boot
     * counter if the read is denied.
     */
    override suspend fun bootId(): String? = withContext(Dispatchers.IO) {
        runCatching { java.io.File("/proc/sys/kernel/random/boot_id").readText().trim() }
            .getOrNull()?.takeIf { it.isNotEmpty() }
            ?: runCatching {
                BOOT_COUNT_PREFIX + android.provider.Settings.Global.getInt(context.contentResolver, "boot_count")
            }.getOrNull()
    }

    override suspend fun buildFingerprint(): String? = android.os.Build.FINGERPRINT.takeIf { it.isNotEmpty() }

    /** Always this phone. */
    override suspend fun deviceKey(): String = "local"

    /**
     * Compares the user's `firstInstallTime` with the boot time. From API 33 on it is per user
     * and `pm install-existing` sets it to the reinstall time (verified on Android 14), while
     * `lastUpdateTime` stays at the system image's; the latter also changes on store updates,
     * so it is ignored. Before API 33 `firstInstallTime` isn't per user: unknown.
     */
    override suspend fun installedSinceBoot(packageName: String): Boolean? = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT < 33) return@withContext null
        runCatching {
            val info = packageManager.getPackageInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            val bootTime = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
            info.firstInstallTime > bootTime
        }.getOrNull()
    }

    @Volatile
    private var deviceContext: DeviceContext? = null

    /**
     * Runs `getprop` as the app's own process and keeps only the handful of
     * properties [deviceContextOf] needs; falls back to [android.os.Build]
     * for the brand and API level when it can't run.
     */
    override suspend fun deviceContext(): DeviceContext? = deviceContext ?: withContext(Dispatchers.IO) {
        val props = runCatching {
            val process = Runtime.getRuntime().exec("getprop")
            try {
                parseGetprop(process.inputStream.bufferedReader().readText())
            } finally {
                process.destroy()
            }
        }.onFailure { LogUtils.w(TAG, "getprop failed: $it") }.getOrNull().orEmpty()
        val withFallback = props.toMutableMap().apply {
            putIfAbsent("ro.product.manufacturer", android.os.Build.MANUFACTURER)
            putIfAbsent("ro.build.version.sdk", android.os.Build.VERSION.SDK_INT.toString())
        }
        deviceContextOf(withFallback).also {
            LogUtils.i(TAG, "Device context: $it")
            deviceContext = it
        }
    }

    override suspend fun packageExists(packageName: String): Boolean =
        packageManager.getInfoForPackage(packageName) != null

    override suspend fun openAppDetails(packageName: String) {
        try {
            val uri = android.net.Uri.fromParts("package", packageName, null)
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to open app details for '$packageName'", e)
        }
    }
}
