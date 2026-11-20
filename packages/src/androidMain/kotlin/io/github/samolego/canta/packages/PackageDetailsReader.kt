package io.github.samolego.canta.packages

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.File

private const val TAG = "PackageDetailsReader"

/** Launcher icon size used when a drawable has no intrinsic size (xxxhdpi). */
private const val FALLBACK_ICON_SIZE_PX = 192

/**
 * Reads the [PackageDetails] of this package. Shared by the Android app and
 * the on-device ADB helper, so both report identical data.
 */
fun PackageInfo.toPackageDetails(packageManager: PackageManager): PackageDetails {
    val appInfo = applicationInfo
    val installed = appInfo?.isInstalledForUser == true
    return PackageDetails(
        packageName = packageName,
        label = appInfo?.loadLabel(packageManager)?.toString().orEmpty(),
        versionName = versionName.orEmpty(),
        versionCode = PackageInfoCompat.getLongVersionCode(this),
        disabled = installed && !appInfo.enabled,
        apkSize = appInfo?.apkSize() ?: 0,
        isSystem = appInfo?.isSystem == true,
        installed = installed,
        hasSystemUpdate = appInfo?.hasSystemUpdate == true,
        installerPackage = installerPackageName(packageManager),
        firstInstallTime = firstInstallTime,
    )
}

/**
 * Who installed this package, or empty when the platform will not say. `getInstallSourceInfo` is
 * the API that answers this properly from Android 11; before that the deprecated single-name call
 * is all there is, and it can still come back null.
 */
private fun PackageInfo.installerPackageName(packageManager: PackageManager): String =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName).orEmpty()
        }
    } catch (e: Exception) {
        // A package the caller may not inspect: unknown is the right answer, not a failure.
        ""
    }

/**
 * Runs [read] for [info], logging and returning null if the package can't be
 * read (e.g. its label resources are broken), so one bad package is skipped
 * rather than fatal.
 */
fun <T> readSafely(info: PackageInfo, read: () -> T): T? =
    try {
        read()
    } catch (e: Exception) {
        Log.w(TAG, "Skipping unreadable package ${info.packageName}", e)
        null
    }

/** Base APK plus all split APKs. */
private fun ApplicationInfo.apkSize(): Long =
    (listOfNotNull(sourceDir) + splitSourceDirs.orEmpty())
        .sumOf { File(it).length() }

/**
 * Renders the app's icon (bitmap, vector, adaptive icon, ...) into a
 * [Bitmap], or returns null when it can't be loaded.
 *
 * @param sizePx edge length of the square bitmap; defaults to the icon's
 *   intrinsic size, falling back to [FALLBACK_ICON_SIZE_PX] for adaptive icons,
 *   which report no intrinsic size.
 */
fun PackageManager.loadIconBitmap(appInfo: ApplicationInfo, sizePx: Int? = null): Bitmap? =
    try {
        val drawable = getApplicationIcon(appInfo)
        val width = sizePx ?: drawable.intrinsicWidth.takeIf { it > 0 } ?: FALLBACK_ICON_SIZE_PX
        val height = sizePx ?: drawable.intrinsicHeight.takeIf { it > 0 } ?: FALLBACK_ICON_SIZE_PX
        drawable.toBitmap(width, height, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) {
        Log.w(TAG, "No icon for ${appInfo.packageName}: $e")
        null
    }
