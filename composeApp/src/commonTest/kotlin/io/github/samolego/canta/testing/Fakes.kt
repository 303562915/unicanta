package io.github.samolego.canta.testing

import androidx.compose.ui.graphics.ImageBitmap
import io.github.samolego.canta.core.CantaDevice
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.core.DeviceConnectionFailure
import io.github.samolego.canta.core.DeviceDiscovery
import io.github.samolego.canta.core.Occupancy
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.bloat.BloatRepository
import io.github.samolego.canta.packages.OperationResult
import io.github.samolego.canta.packages.PackageDetails
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow

/** Installed apps named after their package, for fakes. */
fun installedApps(vararg packageNames: String): List<AppInfo> =
    packageNames.map { AppInfo(PackageDetails(packageName = it, installed = true)) }

/** A [CantaHandler] whose device state and operation outcomes the test controls, recording calls. */
class FakeHandler(
    var apps: List<AppInfo> = installedApps("com.a", "com.b", "com.c"),
    var authorized: Boolean = true,
    override val deviceDiscovery: DeviceDiscovery = DeviceDiscovery.None,
) : CantaHandler {
    var icons: Flow<Pair<String, ImageBitmap>> = emptyFlow()
    var iconLoads = 0
        private set
    val failing = mutableSetOf<String>()

    /** Status code a failing package reports; the default is what a real refusal looks like. */
    var failureStatus = 1
    /** The device's own wording, which is what carries a vendor's specific reason. */
    var failureMessage = ""
    val resettable = mutableSetOf<String>()
    val uninstallCalls = mutableListOf<Pair<List<String>, Boolean>>()
    val reinstallCalls = mutableListOf<List<String>>()
    var devices = emptyList<CantaDevice>()
    var connectFailure: DeviceConnectionFailure? = null

    /** Packages the test says are the home screen, keyboard, an administrator or an a11y service. */
    var occupied = emptyMap<String, List<Occupancy>>()

    override suspend fun occupiedBy(packageNames: Collection<String>) =
        occupied.filterKeys { it in packageNames }

    override val isAuthorized get() = authorized
    override var lastConnectionFailure: DeviceConnectionFailure? = null

    override suspend fun loadApps() = apps
    override fun loadIcons() = icons.also { iconLoads++ }
    override suspend fun canResetToFactory(packageName: String) = packageName in resettable
    override suspend fun packageExists(packageName: String) = apps.any { it.packageName == packageName }

    override fun uninstallApps(packageNames: List<String>, resetToFactory: Boolean): Flow<OperationResult> {
        uninstallCalls += packageNames to resetToFactory
        return results(packageNames)
    }

    override fun reinstallApps(packageNames: List<String>): Flow<OperationResult> {
        reinstallCalls += packageNames
        return results(packageNames)
    }

    val disableCalls = mutableListOf<Pair<List<String>, Boolean>>()

    override fun setDisabledApps(packageNames: List<String>, disabled: Boolean): Flow<OperationResult> {
        disableCalls += packageNames to disabled
        return results(packageNames)
    }

    override val supportsDisabling: Boolean = true

    /** The boot this fake device reports; null is a platform that cannot tell one boot from another. */
    var boot: String? = null

    override suspend fun bootId(): String? = boot

    private fun results(packageNames: List<String>) =
        packageNames.map { name ->
            if (name in failing) {
                OperationResult(name, success = false, status = failureStatus, message = failureMessage)
            } else {
                OperationResult(name, success = true)
            }
        }.asFlow()

    override suspend fun availableDevices() = devices

    override suspend fun selectDevice(device: CantaDevice) {
        lastConnectionFailure = connectFailure
        if (connectFailure == null) authorized = true
    }
}

/** A [CantaPlatform] that records messages and holds back biometric prompts until [approveBiometric]. */
class FakePlatform : CantaPlatform {
    val shownMessages = mutableListOf<String>()
    private var pendingBiometric: (() -> Unit)? = null

    val biometricRequested get() = pendingBiometric != null

    fun approveBiometric() {
        pendingBiometric?.invoke()
        pendingBiometric = null
    }

    override fun showMessage(message: String) {
        shownMessages += message
    }

    override fun requireBiometric(title: String, subtitle: String, onSuccess: () -> Unit) {
        pendingBiometric = onSuccess
    }

    override fun openUrl(url: String) = Unit
    override fun copyToClipboard(text: String) = Unit
    override fun readClipboard(): String? = null
}

/** A [BloatRepository] serving [cachedList] with auto-update off: never touches the network. */
suspend fun offlineBloatRepository(cachedList: String = "{}"): BloatRepository {
    val settings = CantaSettings(MemoryStorage()).apply { setAutoUpdateBloatList(false) }
    val noNetwork = HttpClient(MockEngine { error("tests must not use the network") })
    return BloatRepository(noNetwork, MemoryStorage(cachedList.encodeToByteArray()), settings)
}
