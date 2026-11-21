package io.github.samolego.canta.ui.permission

import androidx.compose.runtime.Composable

/**
 * Desktop and web read the package list over ADB, where the ROM permission does not apply, so
 * there is nothing to ask for.
 */
@Composable
actual fun rememberAppListAccess(): AppListAccess = AppListAccess(
    supported = false,
    granted = true,
    request = {},
    openSettings = {},
)
