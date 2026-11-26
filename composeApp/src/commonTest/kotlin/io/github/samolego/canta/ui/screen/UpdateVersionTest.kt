package io.github.samolego.canta.ui.screen

import io.github.samolego.canta.BuildInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateVersionTest {

    @Test
    fun newerPatchIsAnUpdate() {
        assertTrue(isNewerVersion("v4.2.1", "4.2.0"))
    }

    @Test
    fun sameVersionIsNotAnUpdate() {
        assertFalse(isNewerVersion("v4.2.0", "4.2.0"))
        assertFalse(isNewerVersion("4.2.0", "4.2.0"))
    }

    @Test
    fun localBuildAheadIsNotAnUpdate() {
        assertFalse(isNewerVersion("v4.2.0", "4.3.0"))
    }

    @Test
    fun numericPartsAreComparedAsNumbers() {
        // A string compare would call 4.10.0 older than 4.9.0.
        assertTrue(isNewerVersion("v4.10.0", "4.9.0"))
        assertFalse(isNewerVersion("v4.9.0", "4.10.0"))
    }

    @Test
    fun missingPartsCountAsZero() {
        assertTrue(isNewerVersion("v4.2.1", "4.2"))
        assertFalse(isNewerVersion("v4.2", "4.2.0"))
    }

    @Test
    fun prereleaseSuffixIsIgnored() {
        assertFalse(isNewerVersion("v4.2.0-beta1", "4.2.0"))
        assertTrue(isNewerVersion("v4.2.0", "4.1.9-rc2"))
    }

    @Test
    fun garbageDoesNotCrash() {
        assertFalse(isNewerVersion("", ""))
        assertFalse(isNewerVersion("release", "4.2.0"))
    }

    @Test
    fun stableAtTheInstalledVersionOffersNothing() {
        // 「选稳定版却被推开发版」的根子：同一个 version_name 两边各发一份。装机跑着 dev 构建时，
        // 稳定版同一个版本号不能算新；开发版那边要靠 build 号才算新。
        val stable = UpdateRelease(tag = "v${BuildInfo.VERSION_NAME}", version = BuildInfo.VERSION_NAME)
        val dev = UpdateRelease(
            tag = "dev",
            version = BuildInfo.VERSION_NAME,
            buildNumber = BuildInfo.DEVELOPMENT_BUILD_NUMBER + 1,
        )
        if (BuildInfo.DEVELOPMENT_BUILD_NUMBER > 0) {
            assertFalse(stable.isNewerThanInstalled())
        }
        assertTrue(dev.isNewerThanInstalled())
        // 同一个 build 号的开发版也不算新：滚动构建重新打了包，提交没变，说出来只会让人反复装同一个包。
        assertFalse(
            UpdateRelease(
                tag = "dev",
                version = BuildInfo.VERSION_NAME,
                buildNumber = BuildInfo.DEVELOPMENT_BUILD_NUMBER,
            ).isNewerThanInstalled(),
        )
    }
}
