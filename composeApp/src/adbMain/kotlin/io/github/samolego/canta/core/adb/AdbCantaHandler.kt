package io.github.samolego.canta.core.adb

import androidx.compose.ui.graphics.ImageBitmap
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.core.DeviceConnectionFailure
import io.github.samolego.canta.core.DeviceUser
import io.github.samolego.canta.core.TweakFailure
import io.github.samolego.canta.core.TweakRun
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.packages.OperationResult
import io.github.samolego.canta.packages.PackageDetails
import io.github.samolego.canta.packages.canResetToFactory
import io.github.samolego.canta.util.DeviceContext
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.LOCAL_PROP_KEYS
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.deviceContextOf
import io.github.samolego.canta.util.marketNameFallback
import io.github.samolego.canta.util.marketNameOf
import io.github.samolego.canta.util.parseGetprop
import io.github.samolego.canta.util.payloadHash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Everything the ADB-backed handlers (desktop via dadb, web via WebUSB) share.
 * All package work goes through the on-device [AdbHelper]; subclasses only
 * implement discovering and connecting devices.
 */
abstract class AdbCantaHandler(private val transport: AdbTransport) : CantaHandler {

    private companion object {
        const val TAG = "AdbCantaHandler"
        const val ICON_SIZE_PX = 96
    }

    private val helper = AdbHelper(transport)

    /** The packages from the last [loadApps], for lookups that need no device round trip. */
    private var packages: Map<String, PackageDetails> = emptyMap()

    override val selectedUserId: Int
        get() = helper.userId

    /** Whether a device is connected and the transport can reach it. */
    protected abstract val isConnected: Boolean

    override val isAuthorized: Boolean
        get() = isConnected

    final override var lastConnectionFailure: DeviceConnectionFailure? = null
        protected set

    /**
     * Call once a device is connected: sets up the helper, without which
     * nothing works. Returns false (with [lastConnectionFailure] set) if the
     * device is too old for it or it can't be set up; the caller should then
     * drop the connection. The managed user is reset to the device's current one.
     */
    protected suspend fun onConnected(): Boolean {
        packages = emptyMap()
        deviceContext = null
        profile = null
        helper.userId = helper.currentUserId() ?: 0
        lastConnectionFailure = when {
            !helper.supportsDevice() -> DeviceConnectionFailure.UnsupportedAndroidVersion
            !helper.ensureInstalled() -> DeviceConnectionFailure.HelperUnavailable
            else -> return true
        }
        return false
    }

    override suspend fun availableUsers(): List<DeviceUser> = if (isConnected) helper.users() else emptyList()

    override suspend fun selectUser(id: Int) {
        helper.userId = id
        packages = emptyMap()
    }

    override suspend fun loadApps(): List<AppInfo> {
        if (!isConnected) return emptyList()
        val details = helper.packages()
        if (details == null) {
            LogUtils.w(TAG, "Could not read packages from the helper")
            return emptyList()
        }
        packages = details.associateBy { it.packageName }
        return details.map { AppInfo(it) }
    }

    override fun loadIcons(): Flow<Pair<String, ImageBitmap>> {
        if (!isConnected) return emptyFlow()
        return helper.icons(ICON_SIZE_PX)
            .mapNotNull { icon ->
                runCatching { icon.packageName to icon.png.decodeToImageBitmap() }
                    .onFailure { LogUtils.w(TAG, "Undecodable icon for '${icon.packageName}': $it") }
                    .getOrNull()
            }
            .flowOn(Dispatchers.Default) // Decoding PNGs is CPU work; keep it off the UI thread.
    }

    override suspend fun canResetToFactory(packageName: String): Boolean =
        packages[packageName]?.canResetToFactory == true

    override fun uninstallApps(packageNames: List<String>, resetToFactory: Boolean): Flow<OperationResult> =
        if (isConnected) helper.uninstall(packageNames, resetToFactory) else emptyFlow()

    override fun reinstallApps(packageNames: List<String>): Flow<OperationResult> =
        if (isConnected) helper.reinstall(packageNames) else emptyFlow()

    /** The device shell is already at hand, so the tools page's scripts can run there too. */
    override val supportsTweakCommands: Boolean get() = true

    override suspend fun runTweakCommand(script: String): TweakRun {
        if (!isConnected) return TweakRun(ok = false, failure = TweakFailure.NoPrivilege)
        return try {
            // The transports hand the string to the device shell, which runs it with `sh -c`, so
            // a multi-line script needs no extra quoting here.
            val result = transport.shell(script)
            TweakRun(
                ok = result.exitCode == 0,
                output = listOf(result.stdout, result.stderr)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .trim(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtils.w(TAG, "Could not run a tweak command: $e")
            TweakRun(ok = false, failure = TweakFailure.Broken, output = e.message.orEmpty())
        }
    }

    /** Trimmed output of [cmd]'s output, or null if disconnected, it failed or printed nothing. */
    private suspend fun shellLine(cmd: String, what: String): String? {
        if (!isConnected) return null
        return try {
            transport.shell(cmd).takeIf { it.exitCode == 0 }?.stdout?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtils.w(TAG, "Could not read the $what: $e")
            null
        }
    }

    override suspend fun bootId(): String? = shellLine("cat /proc/sys/kernel/random/boot_id", "boot id")

    override suspend fun buildFingerprint(): String? = shellLine("getprop ro.build.fingerprint", "build fingerprint")

    /** SHA-256 of the device's serial number plus the selected user; never sent. */
    override suspend fun deviceKey(): String? =
        shellLine("getprop ro.serialno", "device serial")?.let { payloadHash(it) + ":" + helper.userId }

    /** Read once per connected device; reset in [onConnected]. */
    private var deviceContext: DeviceContext? = null

    /** The phone as the plans and safety rules need it; read once per connection. */
    private var profile: DeviceProfile? = null

    /** `getprop`, filtered to what Canta may read (telemetry whitelist + local identity). */
    private suspend fun localProps(): Map<String, String>? {
        if (!isConnected) return null
        return try {
            val result = transport.shell("getprop")
            if (result.exitCode != 0) return null
            parseGetprop(result.stdout, LOCAL_PROP_KEYS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtils.w(TAG, "Could not read the device properties: $e")
            null
        }
    }

    override suspend fun deviceContext(): DeviceContext? {
        deviceContext?.let { return it }
        return localProps()?.let { deviceContextOf(it).also { ctx -> deviceContext = ctx } }
    }

    /**
     * The connected phone: model, market name, ROM and fingerprint, all read locally. Without it
     * the plan page has nothing to match against, so a missing profile means an empty plan list.
     */
    override suspend fun deviceProfile(): DeviceProfile? {
        profile?.let { return it }
        val props = localProps() ?: return null
        fun prop(key: String): String = props[key]?.trim().orEmpty()
        val ctx = deviceContextOf(props)
        return DeviceProfile(
            brand = prop("ro.product.brand").ifEmpty { prop("ro.product.manufacturer") }
                .ifEmpty { "unknown" }
                .replaceFirstChar { it.uppercase() },
            model = prop("ro.product.model").ifEmpty { "unknown" },
            device = prop("ro.product.device").ifEmpty { "unknown" },
            sdk = prop("ro.build.version.sdk").toIntOrNull() ?: 0,
            release = prop("ro.build.version.release").ifEmpty { "unknown" },
            rom = ctx.rom,
            romVersion = ctx.romVersion,
            fingerprint = prop("ro.build.fingerprint"),
            // Same fallback as the Android handler: the bulk `getprop` can come back without the
            // market name, and a plan stored under the retail number shows that number to everyone.
            marketName = marketNameOf(props)
                .ifBlank { marketNameFallback { key -> shellLine("getprop $key", "market name of $key") } },
        ).also { profile = it }
    }

    override suspend fun packageExists(packageName: String): Boolean {
        if (!isConnected) return false
        if (packages.isEmpty()) loadApps()
        return packageName in packages
    }

    override suspend fun openAppDetails(packageName: String) {
        if (!isConnected) return
        if (packageName.any { !it.isLetterOrDigit() && it != '.' && it != '_' }) {
            LogUtils.w(TAG, "Refusing to open app details for suspicious package name")
            return
        }
        runCatching {
            transport.shell(
                "am start --user ${helper.userId} -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:$packageName"
            )
        }.onFailure { LogUtils.e(TAG, "Failed to open app details for '$packageName'", it) }
    }
}
