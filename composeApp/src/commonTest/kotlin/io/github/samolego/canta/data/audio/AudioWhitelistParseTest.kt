package io.github.samolego.canta.data.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The state script prints tagged lines and [AudioWhitelist.parse] reads them back; these tests fix
 * the two ends together. A tag renamed on one side would otherwise read as "no apps, no config" —
 * a page that looks empty rather than broken.
 */
class AudioWhitelistParseTest {

    private val sample = """
        ##LEVEL##1
        ##CFG##/odm/etc/audio/trackefx_config.xml
        ##PKG##com.example.unknown
        ##PKG##com.ss.android.ugc.aweme
        ##PKG##in.mohalla.video
        ##PKG##tv.danmaku.bili
        ##INST##com.ss.android.ugc.aweme
        ##INST##tv.danmaku.bili
        ##END##
    """.trimIndent()

    @Test
    fun theLevelAndTheConfigAreRead() {
        val state = AudioWhitelist.parse(sample)
        assertEquals("1", state.level)
        assertEquals("/odm/etc/audio/trackefx_config.xml", state.configPath)
        assertTrue(state.supported)
        assertFalse(state.bypassed)
        assertTrue(state.active)
    }

    @Test
    fun everyNamedPackageBecomesARow() {
        val state = AudioWhitelist.parse(sample)
        assertEquals(4, state.entries.size)
        assertEquals(
            setOf("com.example.unknown", "com.ss.android.ugc.aweme", "in.mohalla.video", "tv.danmaku.bili"),
            state.entries.map { it.packageName }.toSet(),
        )
    }

    @Test
    fun installedRowsComeFirstAndTheFlagIsCarried() {
        val state = AudioWhitelist.parse(sample)
        assertEquals(listOf(true, true, false, false), state.entries.map { it.installed })
    }

    @Test
    fun aKnownPackageGetsItsChineseNameAndAnUnknownOneKeepsItsOwn() {
        val state = AudioWhitelist.parse(sample)
        assertEquals("抖音", state.entries.first { it.packageName == "com.ss.android.ugc.aweme" }.label)
        assertEquals(
            "com.example.unknown",
            state.entries.first { it.packageName == "com.example.unknown" }.label,
        )
    }

    @Test
    fun minusOneReadsAsBypassed() {
        val state = AudioWhitelist.parse("##LEVEL##-1\n##CFG##/odm/etc/audio/trackefx_config.xml\n")
        assertTrue(state.bypassed)
        assertFalse(state.active)
    }

    @Test
    fun aPhoneWithoutTheConfigIsUnsupportedRatherThanEmpty() {
        val state = AudioWhitelist.parse("##LEVEL##1\n##CFG##\n##INST##com.example.app\n")
        assertFalse(state.supported)
        assertTrue(state.entries.isEmpty())
    }

    @Test
    fun noiseLinesAreIgnored() {
        val state = AudioWhitelist.parse("some banner\n##LEVEL##1\nnoise\n##PKG##tv.danmaku.bili\n")
        assertEquals("1", state.level)
        assertEquals(1, state.entries.size)
    }
}
