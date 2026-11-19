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
}
