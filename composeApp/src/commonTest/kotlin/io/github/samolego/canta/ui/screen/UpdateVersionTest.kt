package io.github.samolego.canta.ui.screen

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
}
