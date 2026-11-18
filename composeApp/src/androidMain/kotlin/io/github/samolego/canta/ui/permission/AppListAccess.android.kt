package io.github.samolego.canta.ui.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** The permission some ROMs add on top of `QUERY_ALL_PACKAGES`; AOSP itself does not define it. */
const val GET_INSTALLED_APPS = "com.android.permission.GET_INSTALLED_APPS"

/** Request code for the system dialog; the result is read back from [PackageManager]. */
private const val REQUEST_CODE = 0x1A57

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun Context.hasInstalledAppsPermission(): Boolean =
    checkPermission(GET_INSTALLED_APPS, packageName) == PackageManager.PERMISSION_GRANTED

private fun Context.supportsInstalledAppsPermission(): Boolean =
    runCatching { packageManager.getPermissionInfo(GET_INSTALLED_APPS, 0) }.isSuccess

@Composable
actual fun rememberAppListAccess(): AppListAccess {
    val context = LocalContext.current
    val supported = remember(context) { context.supportsInstalledAppsPermission() }
    var granted by remember(context) { mutableStateOf(context.hasInstalledAppsPermission()) }

    // The dialog hands its result to the activity, not to Compose, so the state is re-read when
    // the user comes back to the app.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = context.hasInstalledAppsPermission()
    }

    return remember(context, supported, granted) {
        AppListAccess(
            supported = supported,
            granted = granted,
            request = {
                val activity = context.findActivity()
                if (activity != null && supported) {
                    ActivityCompat.requestPermissions(activity, arrayOf(GET_INSTALLED_APPS), REQUEST_CODE)
                }
            },
            openSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                Unit
            },
        )
    }
}
