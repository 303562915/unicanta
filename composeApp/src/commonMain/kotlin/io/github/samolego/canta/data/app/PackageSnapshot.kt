package io.github.samolego.canta.data.app

import kotlinx.serialization.Serializable

/**
 * What a package looked like the moment before it was removed.
 *
 * The point is the log: "com.miui.analytics removed" is a package name, while "MIUI 分析 5.2.1
 * (来自身份不明的安装器，2023-04-01 装上)" is a fact about a phone. It is also what makes a restore
 * answerable — the version and the installer say what putting it back would actually put back.
 *
 * @param versionName the build's name as the device reported it, empty when unknown.
 * @param versionCode the build's number, 0 when unknown.
 * @param installerPackage who installed it (a store's package name), empty when unknown.
 * @param firstInstallTime epoch milliseconds, 0 when the platform would not say.
 */
@Serializable
data class PackageSnapshot(
    val packageName: String,
    val label: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    /** Whether the package lives on the system image, which changes what removing it means. */
    val isSystem: Boolean = false,
    val installerPackage: String = "",
    val firstInstallTime: Long = 0,
) {
    /** "MIUI 分析 5.2.1": the app and the build that left the phone. */
    val labelWithVersion: String
        get() = listOf(label.ifEmpty { packageName }, versionName.ifEmpty { "" })
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    /** True when nothing but the name is known, so callers can keep the line short. */
    val isBare: Boolean
        get() = versionName.isEmpty() && versionCode == 0L && installerPackage.isEmpty()
}