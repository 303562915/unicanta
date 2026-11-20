package io.github.samolego.canta.data.app

import androidx.compose.ui.graphics.ImageBitmap
import io.github.samolego.canta.data.bloat.BloatData
import io.github.samolego.canta.packages.PackageDetails
import io.github.samolego.canta.packages.displayName

/**
 * An app as shown in Canta: what the device reports about the package
 * ([details], identical on every platform) plus what Canta adds on top.
 *
 * @param details package data read from the device: in-process on Android,
 *   through the ADB helper (or minimal `pm list packages` data) elsewhere.
 * @param bloatData the app's entry in the bloat list, if any.
 * @param icon streamed in after the list is shown (see `CantaHandler.loadIcons`); null until then.
 */
data class AppInfo(
    val details: PackageDetails,
    val bloatData: BloatData? = null,
    val icon: ImageBitmap? = null,
) {

    val packageName: String
        get() = details.packageName
    /** Shared with the helper's icon order, so icons arrive top-down. */
    val name: String
        get() = details.displayName
    val versionName: String
        get() = details.versionName.ifEmpty { "unknown" }
    val versionCode: Long
        get() = details.versionCode
    val isSystemApp: Boolean
        get() = details.isSystem
    val isUninstalled: Boolean
        get() = !details.installed
    val isDisabled: Boolean
        get() = details.disabled
    /** Size of the base and split APKs in bytes, or null when unknown. */
    val apkSize: Long?
        get() = details.apkSize.takeIf { it > 0 }

    /** Who installed this app (a store's package name), or empty when the platform does not say. */
    val installerPackage: String
        get() = details.installerPackage

    /** When the app was first installed in epoch milliseconds, or null when unknown. */
    val firstInstallTime: Long?
        get() = details.firstInstallTime.takeIf { it > 0 }

    /**
     * The package state worth keeping before it is removed: what was there, so a log read later
     * says which build left the phone rather than only which package name did.
     */
    fun snapshot(): PackageSnapshot = PackageSnapshot(
        packageName = packageName,
        label = name,
        versionName = versionName,
        versionCode = versionCode,
        isSystem = isSystemApp,
        installerPackage = installerPackage,
        firstInstallTime = firstInstallTime ?: 0,
    )

    val badgeInfo: AppBadgeInfo?
        get() = bloatData?.badgeInfo
    val description: String?
        get() = bloatData?.description
    /** True when [description] is this build's translation, not the bloat list's own wording. */
    val descriptionTranslated: Boolean
        get() = bloatData?.translated == true

    fun withIcon(newIcon: ImageBitmap?): AppInfo = copy(icon = newIcon)
    fun withUninstalled(uninstalled: Boolean): AppInfo =
        copy(details = details.copy(installed = !uninstalled))
    fun withDisabled(disabled: Boolean): AppInfo =
        copy(details = details.copy(disabled = disabled))
}
