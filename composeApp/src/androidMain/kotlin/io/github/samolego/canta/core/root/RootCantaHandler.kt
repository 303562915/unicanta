package io.github.samolego.canta.core.root

import io.github.samolego.canta.core.PrivilegeKind
import io.github.samolego.canta.core.adb.AdbCantaHandler
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Android [io.github.samolego.canta.core.CantaHandler] that works on a rooted phone with no Shizuku.
 *
 * Everything that reads or changes a package is the same code the desktop build runs over a cable —
 * the on-device helper, driven through [RootTransport]. Only the privilege underneath differs, and
 * from here down nothing knows which of the two it has.
 *
 * The helper is set up lazily, on the first call that needs the device: construction happens during
 * app startup, where a `su` prompt would be a dialog on top of the splash screen.
 */
class RootCantaHandler(
    private val rootTransport: RootTransport,
) : AdbCantaHandler(rootTransport) {

    private companion object {
        const val TAG = "RootCantaHandler"
    }

    /** Guards the one-time helper setup. */
    private val setupMutex = Mutex()

    @Volatile
    private var ready = false

    @Volatile
    private var setupTried = false

    /** Whether a `su` binary was there at construction; the app only builds this handler when it is. */
    private val suDetected = hasSuBinary()

    /** Whether root has answered; false until [prepare] has run at least once. */
    override val isAuthorized: Boolean
        get() = ready || suDetected

    /** Whether root has answered; false until [prepare] has run at least once. */
    override val isConnected: Boolean
        get() = ready

    /** This handler is the root path: there is no runtime app to install, only a grant to give. */
    override val privilegeKind: PrivilegeKind
        get() = PrivilegeKind.ROOT

    /** Root is not a device to pick: it is the phone this app is installed on. */
    override val deviceDiscovery
        get() = io.github.samolego.canta.core.DeviceDiscovery.None

    override val requiresDeviceSelection: Boolean
        get() = false

    /**
     * Sets up the helper once. Returns whether the device is usable.
     *
     * The failure it can hit is [io.github.samolego.canta.core.DeviceConnectionFailure.HelperUnavailable],
     * which `onConnected` reports: root granted the shell but the helper could not be placed or did
     * not answer its `version`.
     */
    suspend fun prepare(): Boolean {
        if (ready) return true
        setupMutex.withLock {
            if (ready) return true
            if (!rootTransport.isRootAvailable()) {
                LogUtils.i(TAG, "Root is not available; this handler stays unusable")
                setupTried = true
                return false
            }
            val ok = onConnected()
            ready = ok
            setupTried = true
            if (ok) LogUtils.i(TAG, "Working through root")
            ok
        }
        return ready
    }

    /** True once [prepare] has run and failed, so the UI can stop asking. */
    val setupFailed: Boolean
        get() = setupTried && !ready

    override suspend fun loadApps(): List<io.github.samolego.canta.data.app.AppInfo> {
        // The first call is what a fresh start makes, so this is where the helper gets its one
        // setup attempt: a list that came back without it would read as "no apps".
        if (!prepare()) return emptyList()
        return super.loadApps()
    }

    override suspend fun requestAuthorization() {
        prepare()
    }
}