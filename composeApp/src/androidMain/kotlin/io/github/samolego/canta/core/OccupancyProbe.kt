package io.github.samolego.canta.core

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager

/**
 * Reads the jobs a package is doing on this phone, the things a removal cannot be undone from:
 * the home screen, the keyboard, device administrator rights, an accessibility service.
 *
 * Plain `Context` reads, so it works under any privilege: the Shizuku handler and the root handler
 * both sit on the same phone and see the same lists. What differs between them is how packages are
 * uninstalled, not what the phone is currently using.
 */
class OccupancyProbe(private val context: Context) {

    private val packageManager: PackageManager get() = context.packageManager

    /** Roles held by each of [packageNames]; packages holding none are left out of the map. */
    fun rolesOf(packageNames: Collection<String>): Map<String, List<Occupancy>> {
        if (packageNames.isEmpty()) return emptyMap()
        val home = launcherPackage()
        val keyboard = defaultInputMethodPackage()
        val admins = deviceAdminPackages()
        val accessibility = accessibilityPackages()
        return packageNames.distinct().mapNotNull { pkg ->
            val roles = buildList {
                if (pkg == home) add(Occupancy.LAUNCHER)
                if (pkg == keyboard) add(Occupancy.INPUT_METHOD)
                if (pkg in admins) add(Occupancy.DEVICE_ADMIN)
                if (pkg in accessibility) add(Occupancy.ACCESSIBILITY)
            }
            if (roles.isEmpty()) null else pkg to roles
        }.toMap()
    }

    /** The package the home button goes to; null when no launcher answers. */
    private fun launcherPackage(): String? = runCatching {
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
    }.getOrNull()

    /**
     * The keyboard the phone actually types with. The other enabled ones are left out on purpose:
     * people keep two or three around and removing a spare keyboard strands nothing.
     */
    private fun defaultInputMethodPackage(): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let { ComponentName.unflattenFromString(it)?.packageName }
            ?: context.getSystemService(InputMethodManager::class.java)
                ?.let { manager -> manager.enabledInputMethodList.firstOrNull()?.packageName }
    }.getOrNull()

    /** Apps holding device administrator rights, which cannot be removed until they give them up. */
    private fun deviceAdminPackages(): Set<String> = runCatching {
        context.getSystemService(DevicePolicyManager::class.java)
            ?.activeAdmins
            ?.map { it.packageName }
            .orEmpty()
            .toSet()
    }.getOrDefault(emptySet())

    /** Accessibility services that are switched on, seen both through the manager and the setting. */
    private fun accessibilityPackages(): Set<String> = runCatching {
        val running = context.getSystemService(AccessibilityManager::class.java)
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
            .orEmpty()
        val configured = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')
            ?.mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
            .orEmpty()
        (running + configured).toSet()
    }.getOrDefault(emptySet())
}