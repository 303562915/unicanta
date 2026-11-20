package io.github.samolego.canta.data.app

import io.github.samolego.canta.packages.PackageDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackageSnapshotTest {

    @Test
    fun theLabelCarriesTheBuildThatLeft() {
        val snapshot = PackageSnapshot(
            packageName = "com.miui.analytics",
            label = "MIUI 分析",
            versionName = "5.2.1",
        )
        assertEquals("MIUI 分析 5.2.1", snapshot.labelWithVersion)
    }

    @Test
    fun aNamelessPackageFallsBackToItsPackageName() {
        val snapshot = PackageSnapshot(packageName = "com.miui.analytics", versionName = "5.2.1")
        assertEquals("com.miui.analytics 5.2.1", snapshot.labelWithVersion)
    }

    @Test
    fun nothingToAddWhenOnlyThePackageIsKnown() {
        val snapshot = PackageSnapshot(packageName = "com.a")
        assertTrue(snapshot.isBare, "no version, no installer: the row says nothing extra")
        assertEquals("com.a", snapshot.labelWithVersion)
    }

    @Test
    fun anInstallerAloneIsWorthShowing() {
        val snapshot = PackageSnapshot(packageName = "com.a", installerPackage = "com.android.vending")
        assertFalse(snapshot.isBare)
    }

    @Test
    fun anAppSnapshotKeepsVersionInstallerAndSystemFlag() {
        val app = AppInfo(
            PackageDetails(
                packageName = "com.miui.analytics",
                label = "MIUI 分析",
                versionName = "5.2.1",
                versionCode = 42,
                isSystem = true,
                installed = true,
                installerPackage = "com.android.vending",
                firstInstallTime = 1_700_000_000_000,
            )
        )

        val snapshot = app.snapshot()

        assertEquals("com.miui.analytics", snapshot.packageName)
        assertEquals("MIUI 分析", snapshot.label)
        assertEquals("5.2.1", snapshot.versionName)
        assertEquals(42L, snapshot.versionCode)
        assertTrue(snapshot.isSystem)
        assertEquals("com.android.vending", snapshot.installerPackage)
        assertEquals(1_700_000_000_000, snapshot.firstInstallTime)
    }

    @Test
    fun anUnknownInstallTimeIsZeroNotTheEpoch() {
        val app = AppInfo(PackageDetails(packageName = "com.a", installed = true))
        assertEquals(0, app.snapshot().firstInstallTime)
    }
}