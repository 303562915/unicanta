package io.github.samolego.canta.core

import androidx.compose.ui.graphics.ImageBitmap
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.packages.OperationResult
import io.github.samolego.canta.util.DeviceContext
import io.github.samolego.canta.util.DeviceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Which privilege Canta is running on.
 *
 * The UI needs this because the two are set up in completely different ways: one is an app to
 * install and start, the other is a grant from a root manager. Telling a rooted user to install
 * Shizuku would be an instruction that cannot help them.
 */
enum class PrivilegeKind {
    /** Nothing privileged is available; only the app list works. */
    NONE,

    /** Shizuku or Sui: a runtime app the user installs, starts and authorizes. */
    SHIZUKU,

    /**
     * Dhizuku: a manager that shares the DeviceOwner role. It is not a shell, so it can freeze an
     * app but cannot remove one — the setup pages have to name it separately for exactly that
     * reason, or a Dhizuku user would be told to look for a permission it cannot give.
     */
    DHIZUKU,

    /** A rooted phone: no runtime app at all, only the root manager's own prompt. */
    ROOT,
    ;

    /**
     * How this route is spelled in reports to the backend (`root`, `shizuku`, `dhizuku`, `none`).
     *
     * Sent with the install's identity and with every refused operation, so "how many people run
     * this on Dhizuku, and what does that route refuse" is a query rather than a guess. Lowercase
     * and stable: the database stores these strings as they are.
     */
    val wire: String get() = name.lowercase()
}

/**
 * Status of the privileged runtime (Shizuku / Sui on Android).
 */
enum class PrivilegeStatus {
    /** Privileged runtime is running and Canta is authorized. */
    ACTIVE,

    /** Runtime is running but hasn't authorized Canta yet. */
    NOT_AUTHORIZED,

    /** Runtime is installed but its service isn't running. */
    NOT_RUNNING,

    /** No privileged runtime is installed (always the case on desktop and web). */
    NOT_AVAILABLE
}

/**
 * One privilege the phone could run Canta on, and how far it got.
 *
 * [status] and [installed] are separate because they lead to different fixes — install something
 * that is not there, or grant something that is.
 */
data class PrivilegeChoice(
    val kind: PrivilegeKind,
    val status: PrivilegeStatus,
    /** Installed at all: the manager app for Shizuku, the su binary for root. */
    val installed: Boolean,
    /**
     * Whether the chain is running on this route right now.
     *
     * Two routes can both be [PrivilegeStatus.ACTIVE] on the same phone (Shizuku and Dhizuku
     * granted at once), and only one of them is the one operations actually go through. The
     * chooser marks it, so "which one am I using" is a thing the dialog answers rather than
     * something the user has to infer from the settings row.
     */
    val current: Boolean = false,
)

/**
 * OS-specific flavor of [DeviceConnectionFailure.AccessDenied], so the UI
 * can point at the real fix (udev rules on Linux, WinUSB driver on Windows)
 * instead of generic replug advice.
 */
enum class UsbAccessHint {
    Generic,
    LinuxUdev,
    WindowsDriver,
}

/**
 * Why connecting to a device failed. Carried from the handler to the UI so
 * failures can be explained instead of silently reloading the device list.
 */
sealed interface DeviceConnectionFailure {
    /** The OS/browser refused to open the device (e.g. WebUSB access denied). */
    data class AccessDenied(val hint: UsbAccessHint = UsbAccessHint.Generic) : DeviceConnectionFailure

    /** The device is already open, e.g. claimed by another tab or program. */
    data class DeviceBusy(val hint: UsbAccessHint = UsbAccessHint.Generic) : DeviceConnectionFailure

    /** The selected device disappeared from discovery. */
    data object DeviceUnavailable : DeviceConnectionFailure

    /** Canta's on-device helper couldn't be placed or verified on the device. */
    data object HelperUnavailable : DeviceConnectionFailure

    /** The device runs an Android version older than Canta supports. */
    data object UnsupportedAndroidVersion : DeviceConnectionFailure

    /** The platform cannot reach devices at all (e.g. no WebUSB support). */
    data object UnsupportedBrowser : DeviceConnectionFailure

    /** Anything else, with the technical detail for diagnosis. */
    data class Unknown(val detail: String) : DeviceConnectionFailure
}

/**
 * A physical device that Canta can manage over ADB (desktop) or WebUSB (web).
 *
 * @param id opaque identifier used to reconnect ([CantaHandler.selectDevice]).
 * @param displayName human-readable name (model, or serial when unknown).
 * @param detail secondary line (usually the serial), or null.
 */
data class CantaDevice(
    val id: String,
    val displayName: String,
    val detail: String? = null,
)

/**
 * An Android user or profile (e.g. a work profile) on the device, whose apps
 * Canta can manage.
 *
 * @param id the Android user id (0 is the main user).
 * @param name the user's display name; may be empty.
 */
data class DeviceUser(
    val id: Int,
    val name: String,
)

/** How the user picks the device Canta manages. */
enum class DeviceDiscovery {
    /** Always the local device (Android, through Shizuku). */
    None,

    /** Canta lists reachable devices itself (desktop, through ADB). */
    List,

    /** A system picker grants access to and selects a device (web, the WebUSB chooser). */
    SystemPicker,
}

/**
 * A job a package is doing on the phone right now. Removing a package that holds one of these
 * takes the job with it: no home screen to go back to, no keyboard to type with, no way to undo it
 * from inside the app.
 */
enum class Occupancy {
    /** The home screen (launcher) the phone boots into. */
    LAUNCHER,

    /** The input method the phone is set to, the one that comes up when a field is tapped. */
    INPUT_METHOD,

    /** Holds device administrator rights, which have to be released before the app can go. */
    DEVICE_ADMIN,

    /** Providing an accessibility service, which whatever set it up depends on. */
    ACCESSIBILITY,
}

/**
 * Abstract contract that takes care of the platform-specific, low-level
 * operations: loading installed/uninstalled apps and uninstalling/reinstalling.
 *
 * Each target provides its own implementation:
 *  - Android: powered by Shizuku + hidden API bypass.
 *  - Desktop: powered by ADB (dadb).
 *  - Web/WASM: powered by WebUSB (ya-webadb).
 *
 * Operations report their real outcome: they return once the device has
 * answered, not when the command was merely dispatched.
 */
interface CantaHandler {
    val isAuthorized: Boolean

    /**
     * Which privilege [isAuthorized] is about, so the setup pages can name the right one: a rooted
     * phone is authorized by its root manager's prompt, not by a runtime app to install.
     */
    val privilegeKind: PrivilegeKind
        get() = PrivilegeKind.NONE

    /** State of the privileged runtime; only meaningful on Android (Shizuku). */
    val privilegeStatus: PrivilegeStatus
        get() = if (isAuthorized) PrivilegeStatus.ACTIVE else PrivilegeStatus.NOT_AVAILABLE

    /**
     * What this phone can actually offer right now, root first, so the settings page can let the
     * user pick the privilege instead of being told which one the app guessed.
     *
     * Reports state and nothing else: it never raises a grant prompt, because looking at what is
     * available is not the moment to ask for anything. Empty where there is nothing to pick from —
     * the desktop and web builds reach a phone over a cable and have no runtime of their own.
     */
    suspend fun privilegeChoices(): List<PrivilegeChoice> = emptyList()

    /** Installed apps and apps uninstalled for the user (still on the device). */
    suspend fun loadApps(): List<AppInfo>

    /**
     * Whether the last [loadApps] looks gutted because the ROM hides the package list from the
     * app (HyperOS/MIUI ship such a permission, off by default, that cannot be requested at
     * runtime). The list screen then tells the user which switch to flip.
     */
    val appListRestricted: Boolean get() = false

    /**
     * Opens the system screen where the package-list access of the ROM can be granted.
     * Returns false when the platform has no such screen (desktop, web).
     */
    fun openPackageListSettings(): Boolean = false

    /** Whether [openPackageListSettings] can lead anywhere on this platform. */
    val supportsPackageListSettings: Boolean get() = false

    /**
     * Icons for the apps from the last [loadApps], as (package name, icon)
     * pairs streamed in display order, so the list can show before they are
     * all rendered: in-process on Android, by the on-device helper over ADB.
     */
    fun loadIcons(): Flow<Pair<String, ImageBitmap>>

    /** Whether [packageName] is an updated system app that can be reset to its factory version. */
    suspend fun canResetToFactory(packageName: String): Boolean

    /**
     * Uninstalls [packageNames] for the user, optionally resetting updated
     * system apps to their factory version first. Emits one result per
     * package as each one completes.
     */
    fun uninstallApps(packageNames: List<String>, resetToFactory: Boolean = false): Flow<OperationResult>

    /** Reinstalls apps previously uninstalled for the user; one result per package. */
    fun reinstallApps(packageNames: List<String>): Flow<OperationResult>

    /**
     * Freezes ([disabled] = true) or unfreezes ([disabled] = false) apps for the user, one result
     * per package.
     *
     * This is the reversible half of the two: the package stays on the device, keeps its data and
     * its place in the system, and comes back with another tap — which is what makes it the right
     * answer for anything that might be needed again. Nothing here is offered when the connected
     * device cannot do it, see [supportsDisabling].
     */
    fun setDisabledApps(packageNames: List<String>, disabled: Boolean): Flow<OperationResult> =
        emptyFlow()

    /** Whether this device can freeze and unfreeze packages; false where only removal exists. */
    val supportsDisabling: Boolean get() = false

    /**
     * Whether [runTweakCommand] can do anything here. The Android app has Shizuku's shell, the
     * ADB targets have the device's own; a desktop with no phone connected has neither.
     */
    val supportsTweakCommands: Boolean get() = false

    /**
     * Runs [script] with `sh -c` as the privileged user (shell, or root when Shizuku/Sui runs as
     * root) and returns what it printed. Only the tools page calls it, with the fixed scripts
     * shipped in `rom_tweaks.json`; nothing typed by the user ever reaches it.
     */
    suspend fun runTweakCommand(script: String): TweakRun =
        TweakRun(ok = false, failure = TweakFailure.Unsupported)

    /**
     * The device's boot id (changes on every boot), or null when it can't be
     * read. Used to tell whether a reboot happened since an uninstall.
     */
    suspend fun bootId(): String? = null

    /**
     * The device's build fingerprint, or null when it can't be read. Only compared
     * locally to tell an OS update from anything else; never sent.
     */
    suspend fun buildFingerprint(): String? = null

    /**
     * Identifies the connected device (a hash, never sent) so pending uninstalls are only
     * resolved against the device they happened on; null when the handler always talks to
     * the same device or it can't be read.
     */
    suspend fun deviceKey(): String? = null

    /**
     * Whether [packageName] was (re)installed for the current user after the
     * current boot started, or null when the platform can't tell.
     */
    suspend fun installedSinceBoot(packageName: String): Boolean? = null

    /**
     * Coarse description of the connected device (brand, ROM family and
     * version, Android API level), or null when it can't be read.
     */
    suspend fun deviceContext(): DeviceContext? = null

    /**
     * Model, ROM and build of the device, used to pick the safety rules for it. Stays on the
     * device: unlike [deviceContext] nothing here is ever sent anywhere.
     */
    suspend fun deviceProfile(): DeviceProfile? = null

    /**
     * Which of [packageNames] are doing a job the phone cannot do without right now; packages with
     * no such job are left out of the map. Empty by default: a desktop or web handler talks to a
     * phone over ADB and has no way to see its launcher, keyboard or admin lists.
     */
    suspend fun occupiedBy(packageNames: Collection<String>): Map<String, List<Occupancy>> = emptyMap()

    /** Whether the given package is on the device (installed or uninstalled for the user). */
    suspend fun packageExists(packageName: String): Boolean

    /**
     * Opens the system's app details (App info) page for [packageName]:
     * the Settings screen on Android itself, the on-device Settings screen
     * over ADB elsewhere. Only called for installed apps.
     */
    suspend fun openAppDetails(packageName: String) {}

    /**
     * Requests authorization from the privileged runtime (Android: the
     * Shizuku permission prompt). Only used where [deviceDiscovery] is
     * [DeviceDiscovery.None].
     */
    suspend fun requestAuthorization() {}

    /**
     * Asks [kind] for its grant and keeps working through it when the answer is yes.
     *
     * Needed because a handler made of several privileges decides once: a root grant given later,
     * from the settings page, has to be able to win back over Shizuku instead of waiting for the
     * next launch. False where the phone does not offer [kind], or where it said no.
     */
    suspend fun authorize(kind: PrivilegeKind): Boolean = false

    /** How the device is chosen; see [DeviceDiscovery]. */
    val deviceDiscovery: DeviceDiscovery
        get() = DeviceDiscovery.None

    /** Whether the user must pick a device before apps can load. */
    val requiresDeviceSelection: Boolean
        get() = deviceDiscovery != DeviceDiscovery.None

    /** Devices available for selection ([DeviceDiscovery.List] only). */
    suspend fun availableDevices(): List<CantaDevice> = emptyList()

    /** Connects to a device previously returned by [availableDevices]. */
    suspend fun selectDevice(device: CantaDevice) {}

    /**
     * Opens the system device picker and connects to the chosen device
     * ([DeviceDiscovery.SystemPicker] only). Returns null when cancelled/failed.
     */
    suspend fun pickSystemDevice(): CantaDevice? = null

    /** Users and profiles on the device whose apps can be managed; empty when the choice doesn't exist (Android). */
    suspend fun availableUsers(): List<DeviceUser> = emptyList()

    /** The id of the user whose apps are managed, or null when there is no choice. Not persisted. */
    val selectedUserId: Int?
        get() = null

    /** Switches to the user [id] from [availableUsers]; apps must then be reloaded. */
    suspend fun selectUser(id: Int) {}

    /**
     * Whether the platform can reach devices at all. Web returns false when
     * the browser has no WebUSB support; everything else defaults to true.
     */
    val connectionSupported: Boolean
        get() = true

    /** Why the last connection attempt failed, if any, so the UI can explain it. */
    val lastConnectionFailure: DeviceConnectionFailure?
        get() = null
}
