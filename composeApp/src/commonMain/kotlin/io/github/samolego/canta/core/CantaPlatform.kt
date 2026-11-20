package io.github.samolego.canta.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Abstraction over small, UI-facing platform capabilities so the shared UI
 * can remain target-agnostic. Each platform provides its own implementation.
 */
interface CantaPlatform {
    /**
     * Show a transient short message. Android shows a Toast; platforms without
     * one emit it on [messages] instead, which the app shows as a snackbar.
     */
    fun showMessage(message: String)

    /** Messages to show as snackbars; empty where [showMessage] shows them itself. */
    val messages: Flow<String>
        get() = emptyFlow()

    /** Open a URL in the platform's default browser. */
    fun openUrl(url: String)

    /**
     * True when F-Droid put this build in place. Every store re-signs what it ships, so an APK from
     * the GitHub release can never replace an F-Droid install (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`,
     * or a silent no-op after the user confirms): the updater has to send people back to the store
     * instead of downloading and installing on its own.
     */
    fun isInstalledFromFdroid(): Boolean = false

    /**
     * Whether a VPN currently carries this device's traffic. The community backend is Supabase in
     * Tokyo, so on a phone without a proxy every community read looks like "the server is offline"
     * even though the network is fine; the onboarding asks this to say so instead of letting people
     * believe the app is broken.
     */
    fun isVpnActive(): Boolean = false

    /**
     * Opens the place where the VPN is switched on: an installed proxy client when there is one
     * (its connect button is what actually matters), otherwise the system VPN screen. False where
     * the platform has neither.
     */
    fun openProxySettings(): Boolean = false

    /**
     * Open [appSchemes] in the app that claims them — the author's Coolapk page, a QQ group card —
     * and fall back to [web] when none of them is handled.
     *
     * The schemes are tried in order because the same app answers to more than one spelling of the
     * same deep link. Platforms without app deep links (the web build, the desktop build, tests)
     * just open [web]; that default is what keeps this an optional capability. An empty [web] means
     * there is no page worth opening, and the caller handles the "nothing opened" answer itself.
     *
     * Returns true when an app scheme was what got opened.
     */
    fun openLink(web: String, vararg appSchemes: String): Boolean {
        if (web.isNotEmpty()) openUrl(web)
        return false
    }

    /**
     * Request biometric/device-credential authentication before an action.
     * [title] and [subtitle] are shown by the system prompt; platforms
     * without biometrics should invoke [onSuccess] immediately.
     */
    fun requireBiometric(title: String, subtitle: String, onSuccess: () -> Unit)

    fun copyToClipboard(text: String)
    fun readClipboard(): String?

    /**
     * Writes [content] to a file the user can pull off the device without the app: on Android
     * `Download/Canta/[fileName]`, on the desktop the user's Downloads folder. Returns where the
     * file landed, or null where there is no such place (the web build).
     */
    fun exportLog(fileName: String, content: String): String? = null

    /**
     * A stable, hashed identifier of this device, used only to hand a reinstall its old identity
     * back: the identity token itself is random and stays the secret, this is just the lookup key.
     *
     * It must survive uninstalling the app and must not be a raw hardware identifier. Android
     * answers with a hash of `Settings.Secure.ANDROID_ID` plus the build fingerprint (ANDROID_ID is
     * scoped to the app's signing key and survives reinstalls; IMEI and the serial number are
     * unreadable for a normal app on Android 10+). Empty where the platform has no such value, in
     * which case the app keeps working with its token alone.
     */
    fun deviceKey(): String = ""

    /**
     * Downloads [url] into a place the privileged helper can read, reporting the bytes that arrived
     * and the size the server announced.
     *
     * [onProgress] receives a [total] of 0 or less when the answer carries no length — a chunked
     * response, or a proxy that drops `Content-Length`. That case has to be shown as an
     * indeterminate spinner: computing a percentage against a size nobody announced is exactly how
     * a running download sits at 0% while it works.
     *
     * Returns [UpdateDownload.Done] with the absolute path, or [UpdateDownload.Failed] with the
     * reason, so the caller can offer the download page instead of a dead end. The file has to
     * survive until [installUpdate] reads it, so it goes next to the app's own cache rather than
     * into a temporary directory that is cleaned up on the next start.
     */
    suspend fun downloadUpdate(
        url: String,
        fileName: String,
        onProgress: (received: Long, total: Long) -> Unit,
    ): UpdateDownload = UpdateDownload.Failed(UpdateDownloadFailure.UNSUPPORTED)

    /**
     * Installs the APK at [path] over this app through the privileged runtime, i.e. `pm install`
     * as Shizuku's identity. Returns null on success, or the reason it failed.
     *
     * Platforms without a privileged helper answer with an explanation instead of throwing: the
     * caller shows it and leaves the manual download link in place.
     */
    suspend fun installUpdate(path: String): String? = "this build cannot install updates"

    /**
     * Hands the APK at [path] to the system package installer through a content:// URI the
     * installer is granted read access to — the path-agnostic way to install, used when the
     * privileged `pm install` cannot read any location the app can write (scoped storage hides
     * `Android/data` from the shell, and ROMs differ on what else they allow). The reader then
     * confirms the install in the system dialog. Returns true when an installer screen launched.
     */
    fun requestInstall(path: String): Boolean = false

    /** Closes the app, so the freshly installed version starts clean on the next launch. */
    fun exitApp() = Unit
}
