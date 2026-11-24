package io.github.samolego.canta.core

import io.github.samolego.canta.util.BOOT_COUNT_PREFIX
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.samolego.canta.core.shizuku.ShizukuPermission
import io.github.samolego.canta.core.shizuku.ShizukuServices
import io.github.samolego.canta.core.shizuku.ShizukuShell
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
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.LOCAL_PROP_KEYS
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.deviceContextOf
import io.github.samolego.canta.util.marketNameFallback
import io.github.samolego.canta.util.marketNameOf
import io.github.samolego.canta.util.parseGetprop
import io.github.samolego.canta.util.romBuildOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
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

        /**
         * What a package gets back when there is no Shizuku binder at all. Worded with Shizuku's
         * own phrase, because [io.github.samolego.canta.core.explainFailure] matches on it and
         * turns it into the one advice that helps: start the runtime again.
         */
        const val BINDER_GONE = "Shizuku binder haven't been received"
    }

    /**
     * Shizuku's binder *right now*; the cached [ShizukuPermission] status lags a runtime that just
     * died. Sui counts as up without a binder ping: it hands Canta the binder from its own process.
     */
    val isRuntimeUp: Boolean
        get() = ShizukuPermission.isSuiRuntime || runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /**
     * One result per package, in order. Without a binder there is nothing to try, so every package
     * gets the same honest answer instead of a stack trace out of a dead wrapper: the wrapper's
     * exception reads as "the device never answered", which blames the phone for a runtime that is
     * simply not running.
     */
    private fun batchResults(
        packageNames: List<String>,
        operation: (String) -> OperationResult,
    ): Flow<OperationResult> = flow {
        packageNames.forEach { name ->
            emit(
                if (isRuntimeUp) {
                    operation(name)
                } else {
                    OperationResult(
                        packageName = name,
                        success = false,
                        status = OperationResult.STATUS_NEGATIVE_RESPONSE,
                        message = BINDER_GONE,
                    )
                }
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * The privileged listing, completed with [local] for anything it does not contain. Returns
     * [local] unchanged when the privileged call yields nothing, so a broken binder never leaves
     * an empty list.
     */
    private fun privilegedFirst(local: List<PackageInfo>): List<PackageInfo> {
        val privileged = operations.privilegedInstalledPackages()
        if (privileged.isEmpty()) {
            LogUtils.w(TAG, "Privileged package listing returned nothing; using the local one")
            return local
        }
        val known = privileged.mapTo(HashSet()) { it.packageName }
        val extra = local.filterNot { it.packageName in known }
        if (local.size < privileged.size) {
            LogUtils.w(TAG, "The ROM only showed ${local.size} packages; the privileged listing has ${privileged.size}")
        }
        return privileged + extra
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

    /** What the phone is currently using; the root handler reads the same lists. */
    private val occupancyProbe = OccupancyProbe(context)

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

    /** This handler is the Shizuku path: the setup pages describe a runtime app, not a root grant. */
    override val privilegeKind: PrivilegeKind
        get() = PrivilegeKind.SHIZUKU

    override val privilegeStatus: PrivilegeStatus
        get() = ShizukuPermission.privilegeStatus(packageManager)

    override suspend fun loadApps(): List<AppInfo> = withContext(packageDispatcher) {
        // Reading labels loads each app's resources, which is slow on a cold
        // start; read packages in parallel, like the ADB helper does.
        val local = packageManager.getAllPackages()
        // With the privileged runtime, its own listing is complete no matter what the ROM does to
        // our PackageManager, so it is the primary source and the local one only fills gaps.
        val all = if (isAuthorized) privilegedFirst(local) else local
        restrictedList = local.size < MIN_PLAUSIBLE_PACKAGES && all.size <= local.size
        LogUtils.i(TAG, "Packages: ${local.size} from PackageManager, ${all.size} after the privileged listing")

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
        batchResults(packageNames) { operations.uninstall(it, resetToFactory) }

    override fun reinstallApps(packageNames: List<String>): Flow<OperationResult> =
        batchResults(packageNames) { operations.reinstall(it) }

    /** Shizuku's package service can freeze and unfreeze apps, so both halves are available. */
    override fun setDisabledApps(packageNames: List<String>, disabled: Boolean): Flow<OperationResult> =
        batchResults(packageNames) { operations.setDisabled(it, disabled) }

    override val supportsDisabling: Boolean get() = true

    /** The tools page's scripts need a shell; Shizuku's user service is the only one Canta has. */
    private val shell by lazy { ShizukuShell(context) }

    override val supportsTweakCommands: Boolean get() = true

    override suspend fun runTweakCommand(script: String): TweakRun = shell.run(script)

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

    @Volatile
    private var profile: DeviceProfile? = null

    /** Build.* plus the ROM fields of [deviceContext]; never leaves the device. */
    override suspend fun deviceProfile(): DeviceProfile? = profile ?: withContext(Dispatchers.IO) {
        val ctx = deviceContext()
        val props = localProps()
        DeviceProfile(
            brand = Build.MANUFACTURER.orEmpty().ifBlank { "unknown" }.replaceFirstChar { it.uppercase() },
            model = Build.MODEL.orEmpty().ifBlank { "unknown" },
            device = Build.DEVICE.orEmpty().ifBlank { "unknown" },
            sdk = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE.orEmpty(),
            rom = ctx?.rom ?: "unknown",
            romVersion = ctx?.romVersion ?: 0,
            // The build itself, kept for plans only: two builds of one model are two different
            // package sets, and a row that names only the major version cannot say which.
            romBuild = romBuildOf(props),
            fingerprint = Build.FINGERPRINT.orEmpty(),
            // The market name is the only field Build does not expose: MIUI/HyperOS keeps the
            // retail number in MODEL and the name people know in ro.product.marketname. When the
            // one bulk read came back without it, ask for the handful of keys directly — a plan
            // uploaded with an empty name shows the retail number to every reader.
            marketName = marketNameOf(props).ifBlank { marketNameFallback { key -> getpropOne(key) } },
        ).also { profile = it }
    }

    /**
     * The four jobs that make a removal unrecoverable from inside the app. Read on every call
     * rather than cached: what the user made their home screen or keyboard changes while Canta runs.
     */
    override suspend fun occupiedBy(packageNames: Collection<String>): Map<String, List<Occupancy>> =
        withContext(Dispatchers.IO) { occupancyProbe.rolesOf(packageNames) }

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

    /** `getprop` output, filtered to the local whitelist; read once per process. */
    @Volatile
    private var localProps: Map<String, String>? = null

    /**
     * Runs `getprop` as the app's own process and keeps only the properties Canta may read: the
     * [DeviceContext] whitelist plus the local-only identity props. Falls back to
     * [android.os.Build] for the brand and API level when it can't run.
     */
    private suspend fun localProps(): Map<String, String> = localProps ?: withContext(Dispatchers.IO) {
        val props = runCatching {
            val process = Runtime.getRuntime().exec("getprop")
            try {
                parseGetprop(process.inputStream.bufferedReader().readText(), LOCAL_PROP_KEYS)
            } finally {
                process.destroy()
            }
        }.onFailure { LogUtils.w(TAG, "getprop failed: $it") }.getOrNull().orEmpty()
        props.toMutableMap().apply {
            putIfAbsent("ro.product.manufacturer", android.os.Build.MANUFACTURER)
            putIfAbsent("ro.build.version.sdk", android.os.Build.VERSION.SDK_INT.toString())
        }.also { localProps = it }
    }

    /**
     * One property, read on its own. Used only as the fallback when the bulk [localProps] read
     * came back without the market name: one short `getprop <key>` per candidate key, no shell.
     */
    private suspend fun getpropOne(key: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            try {
                process.inputStream.bufferedReader().use { it.readText() }
            } finally {
                process.destroy()
            }
        }.onFailure { LogUtils.w(TAG, "getprop $key failed: $it") }.getOrNull()
    }

    /** The ROM family and version of this phone, from the same `getprop` read as [deviceProfile]. */
    override suspend fun deviceContext(): DeviceContext? = deviceContext ?: run {
        deviceContextOf(localProps()).also {
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
