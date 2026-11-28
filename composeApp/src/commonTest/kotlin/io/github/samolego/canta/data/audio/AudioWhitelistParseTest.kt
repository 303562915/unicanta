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

    @Test
    fun theLowPowerListIsReadSeparatelyFromTheSceneConfig() {
        // K90 Pro Max 这一代没有 trackefx 名单，压外放的是低功耗清单：两份东西必须分开读，
        // 否则这台机器会显示成「没有名单、什么都不支持」，而它其实有另一套机制。
        val state = AudioWhitelist.parse(
            "##LEVEL##\n" +
                "##CFG##\n" +
                "##LOWPOWER##/odm/etc/audio/audio_lowpower_app_list.xml\n" +
                "##LOWPOWER##/odm/etc/audio/audio_lowpower_app_list.xml\ttv.danmaku.bili\n" +
                "##LOWPOWER##/odm/etc/audio/audio_lowpower_app_list.xml\tcom.ss.android.ugc.aweme\n" +
                "##INST##tv.danmaku.bili\n",
        )
        assertEquals("/odm/etc/audio/audio_lowpower_app_list.xml", state.lowPowerPath)
        assertEquals(2, state.lowPowerPackages.size)
        assertEquals(1, state.lowPowerInstalled)
        assertTrue(state.hasLowPower)
        // 场景开关那份没找到，但低功耗清单找到了：不能因为前者为空就把整页说成「不支持」。
        assertFalse(state.supported)
        // 清单必须进诊断行，否则页面上看得见、数据库里永远是空的。
        assertTrue(state.diagnostics.any { it.startsWith("lowpower: ") && it.contains("audio_lowpower_app_list.xml") })
        assertTrue(state.diagnostics.any { it.startsWith("lowpower pkg: ") && it.contains("tv.danmaku.bili") })
    }

    @Test
    fun aPhoneWithoutAnyLowPowerListSaysSo() {
        val state = AudioWhitelist.parse("##LEVEL##1\n##CFG##/odm/etc/audio/trackefx_config.xml\n##LOWPOWER##\n")
        assertFalse(state.hasLowPower)
        assertTrue(state.lowPowerPackages.isEmpty())
        assertTrue(state.supported)
    }

    @Test
    fun theShellIdentityDecidesWhetherTheListCanBeEdited() {
        // 低功耗清单在 /odm 只读分区：Shizuku 的 shell（uid 2000）写不了，页面不该给他点亮按钮。
        val shizuku = AudioWhitelist.parse("##ROOT##2000\n##LOWPOWER##/odm/etc/audio/audio_lowpower_app_list.xml\n")
        assertFalse(shizuku.isRoot)
        assertTrue(shizuku.diagnostics.any { it.contains("非 root") })

        val rooted = AudioWhitelist.parse("##ROOT##0\n##LOWPOWER##/odm/etc/audio/audio_lowpower_app_list.xml\n")
        assertTrue(rooted.isRoot)
    }

    @Test
    fun theListHeadGoesIntoTheDiagnostics() {
        // 清单头里有可能是 enable 之类的开关字段：能不能免 root 关掉，全靠这一行判断。
        val state = AudioWhitelist.parse(
            "##LOWPOWER##/odm/etc/audio/audio_lowpower_app_list.xml\n" +
                "##LOWPOWERHEAD##<lowPowerAppList enable=\"true\">\n",
        )
        assertTrue(state.diagnostics.any { it.startsWith("lowpower head: ") && it.contains("enable") })
    }

    @Test
    fun theDiagnosticLinesAreKeptVerbatim() {
        // These are what a bug report carries: the properties the ROM has and what the two global
        // settings hold. Dropping them silently would leave "no effect" undiagnosable.
        val state = AudioWhitelist.parse(
            // The leading newline matters: sample ends with "##END##" and no trailing line break.
            sample + "\n" +
                "##PROP##[persist.sys.scene.partition.level]:   [1]\n" +
                "##SETTING##debug_audio_cloud=null\n" +
                "##XMLHEAD##<TrackCustomEfxConfig name=\"trackefx_config\">\n",
        )
        assertEquals(3, state.diagnostics.size)
        assertEquals("[persist.sys.scene.partition.level]: [1]", state.diagnostics[0])
        assertEquals("setting debug_audio_cloud=null", state.diagnostics[1])
        assertTrue(state.diagnostics[2].startsWith("xml: <TrackCustomEfxConfig"))
    }
}
