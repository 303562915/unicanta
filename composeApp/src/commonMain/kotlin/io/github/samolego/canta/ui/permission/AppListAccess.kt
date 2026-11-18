package io.github.samolego.canta.ui.permission

import androidx.compose.runtime.Composable

/**
 * Access to the device's package list.
 *
 * Some ROMs (HyperOS/MIUI, ColorOS and friends) gate the list behind
 * `com.android.permission.GET_INSTALLED_APPS`, which — unlike most ROM-specific switches — *is*
 * a permission that can be requested at runtime. FlClash does the same: declare it in the
 * manifest, check it with `checkPermission`, and ask for it with `requestPermissions`.
 */
class AppListAccess(
    /** Whether this ROM knows the permission at all. */
    val supported: Boolean,
    /** Whether it is granted right now. */
    val granted: Boolean,
    /** Pops the system permission dialog; no-op where unsupported. */
    val request: () -> Unit,
    /** Opens the ROM's settings page, for the case where the dialog is suppressed for good. */
    val openSettings: () -> Unit,
)

/** Whether the list can be read without asking the user anything. */
val AppListAccess.needsRequest: Boolean get() = supported && !granted

/** Remembers (and re-checks) the state of the installed-apps permission. */
@Composable
expect fun rememberAppListAccess(): AppListAccess
