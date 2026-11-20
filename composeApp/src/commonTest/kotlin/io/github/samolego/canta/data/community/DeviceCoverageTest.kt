package io.github.samolego.canta.data.community

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The device coverage lines under a plan: which builds the same package set survived on.
 *
 * These are display rules over what the backend counted, so they are worth pinning: a line that
 * says "HyperOS 2" for a stock ROM, or that prints "1 人" for the only person who tried it, reads
 * wrong in a way numbers alone do not show.
 */
class DeviceCoverageTest {

    @Test
    fun theMarketingNameWinsOverTheRetailNumber() {
        val device = DeviceCoverage(brand = "Xiaomi", model = "23127PN0CC", marketName = "Redmi K70")
        assertEquals("Redmi K70", device.label)
    }

    @Test
    fun aRowWithoutAMarketingNameFallsBackToTheModel() {
        val device = DeviceCoverage(brand = "Xiaomi", model = "23127PN0CC")
        assertEquals("23127PN0CC", device.label)
    }

    @Test
    fun theBuildLabelJoinsModelAndRom() {
        val device = DeviceCoverage(
            brand = "Xiaomi",
            model = "23127PN0CC",
            marketName = "Redmi K70",
            // The backend stores the family key, not the display name.
            rom = "hyperos",
            romVersion = 2,
        )
        assertEquals("Redmi K70 · HyperOS 2", device.buildLabel)
    }

    @Test
    fun aRowWithoutARomStillNamesTheSystem() {
        val device = DeviceCoverage(brand = "Xiaomi", model = "23127PN0CC", marketName = "Redmi K70")
        // An unknown family reads as "Android" rather than as nothing: the line is about a phone and
        // its system, and "Android" is the honest answer when the ROM was never recorded.
        assertEquals("Redmi K70 · Android", device.buildLabel)
    }

    @Test
    fun theCountIsKeptAsTheBackendSentIt() {
        val device = DeviceCoverage(brand = "Xiaomi", model = "fuxi", verified = 4)
        assertEquals(4, device.verified)
    }
}