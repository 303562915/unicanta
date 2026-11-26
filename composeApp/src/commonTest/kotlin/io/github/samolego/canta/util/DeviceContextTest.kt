package io.github.samolego.canta.util

import io.github.samolego.canta.data.community.CommunityPlan
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceContextTest {
    private fun ctx(vararg props: Pair<String, String>) =
        deviceContextOf(mapOf("ro.product.manufacturer" to "Xiaomi", "ro.build.version.sdk" to "34") + props)

    private fun rom(vararg props: Pair<String, String>) = ctx(*props).let { it.rom to it.romVersion }

    @Test
    fun brandAndSdk() {
        assertEquals(DeviceContext("xiaomi", "stock", 0, 34), ctx())
        assertEquals(DeviceContext("unknown", "stock", 0, 0), deviceContextOf(emptyMap()))
        assertEquals("unknown", deviceContextOf(mapOf("ro.product.manufacturer" to "  ")).brand)
        assertEquals(0, deviceContextOf(mapOf("ro.build.version.sdk" to "abc")).sdk)
        assertEquals("google", deviceContextOf(mapOf("ro.product.manufacturer" to " Google ")).brand)
    }

    @Test
    fun hyperos() {
        assertEquals("hyperos" to 1, rom("ro.mi.os.version.name" to "OS1.0"))
        assertEquals("hyperos" to 2, rom("ro.mi.os.version.name" to "OS2.0.200"))
        // HyperOS phones still carry the MIUI props; HyperOS wins.
        assertEquals("hyperos" to 1, rom("ro.mi.os.version.name" to "OS1.0", "ro.miui.ui.version.name" to "V816"))
    }

    @Test
    fun miui() {
        assertEquals("miui" to 14, rom("ro.miui.ui.version.name" to "V14"))
        assertEquals("miui" to 12, rom("ro.miui.ui.version.name" to "V125"))
        assertEquals("miui" to 8, rom("ro.miui.ui.version.name" to "V816"))
        assertEquals("miui" to 13, rom("ro.miui.ui.version.name" to "V130", "ro.miui.ui.version.code" to "13"))
        assertEquals("miui" to 10, rom("ro.miui.ui.version.name" to "V125", "ro.miui.ui.version.code" to "10"))
    }

    @Test
    fun otherRoms() {
        assertEquals("oneui" to 6, rom("ro.build.version.oneui" to "60100"))
        assertEquals("oneui" to 5, rom("ro.build.version.oneui" to "50101"))
        assertEquals("coloros" to 13, rom("ro.build.version.opporom" to "V13.1"))
        assertEquals("realmeui" to 2, rom("ro.build.version.realmeui" to "V2.0", "ro.build.version.opporom" to "V13.1"))
        assertEquals("funtouch" to 13, rom("ro.vivo.os.version" to "13.0"))
        assertEquals("originos" to 3, rom("ro.vivo.os.version" to "3.0", "ro.vivo.os.name" to "OriginOS"))
        assertEquals("magicos" to 7, rom("ro.build.version.magic" to "MagicOS_7.1.0"))
        assertEquals("emui" to 12, rom("ro.build.version.emui" to "EmotionUI_12.0.0"))
        assertEquals("oxygenos" to 13, rom("ro.oxygen.version" to "13.1"))
        assertEquals("oxygenos" to 14, rom("ro.rom.version" to "OxygenOS_14.0.0"))
        assertEquals("nothingos" to 2, rom("ro.build.nothing.version" to "2.5.2"))
        assertEquals("stock" to 0, rom("ro.rom.version" to "something else"))
    }

    @Test
    fun oppoOneplusRealmeMeizuNubiaAreNotReportedAsAndroid() {
        // ColorOS 6 以后 `opporom` 是空的，实机只报 oplus 那个键——以前这条链落到 stock，界面上是
        // 「Android」，版本 0。
        assertEquals("coloros" to 16, rom("ro.build.version.oplusrom" to "V16.0.3"))
        assertEquals("coloros" to 15, rom("ro.build.version.oplusrom" to "V15.0.0", "ro.build.version.opporom" to ""))
        // 一加：OxygenOS 坐在 ColorOS 底座上，同一个 oplus 键，但氧气自己的键在就认氧气。
        assertEquals(
            "oxygenos" to 16,
            rom("ro.build.version.oplusrom" to "V16.0.0", "ro.oxygen.version" to "16.0"),
        )
        // 魅族 Flyme：只有 `display.id` 这一行可读。
        assertEquals("flyme" to 8, rom("ro.build.display.id" to "Flyme OS 8.2.0.0A"))
        assertEquals("flyme" to 10, rom("ro.flyme.version" to "10.5.0.1A"))
        // 努比亚 / 红魔 MyOS。
        assertEquals("myos" to 10, rom("ro.build.display.id" to "RedMagic OS 10.0.1"))
        assertEquals("myos" to 3, rom("ro.build.version.myos" to "MyOS 3.0"))
        // 一个 ROM 键都读不到时，至少把 Android 版本报出来，而不是 0。
        assertEquals("stock" to 16, rom("ro.build.version.release" to "16"))
        assertEquals("stock" to 0, rom("ro.build.version.release" to ""))
    }

    @Test
    fun getpropParsingKeepsOnlyKnownKeys() {
        val out = """
            [ro.product.manufacturer]: [Xiaomi]
            [ro.product.model]: [23049PCD8G]
            [ro.build.fingerprint]: [xiaomi/x/y:14/z]
            [ro.mi.os.version.name]: [OS1.0]
            [ro.build.version.sdk]: [34]
            [ro.empty.value]: []
            garbage line
            [multi.line]: [first
            second]
        """.trimIndent()
        val props = parseGetprop(out)
        assertEquals(setOf("ro.product.manufacturer", "ro.mi.os.version.name", "ro.build.version.sdk"), props.keys)
        assertEquals(DeviceContext("xiaomi", "hyperos", 1, 34), deviceContextOf(props))
        assertEquals(mapOf("ro.empty.value" to ""), parseGetprop(out, setOf("ro.empty.value")))
    }

    @Test
    fun labels() {
        assertEquals("HyperOS 1", DeviceContext("xiaomi", "hyperos", 1, 34).romLabel())
        assertEquals("One UI 6", DeviceContext("samsung", "oneui", 6, 34).romLabel())
        assertEquals("Android", DeviceContext("google", "stock", 0, 34).romLabel())
    }

    @Test
    fun romBuildIsTheWholeVersionNotJustTheMajorOne() {
        // The raw string is what a plan row stores and is matched on: the same phone ships 16.0.3
        // and 16.0.501, both major version 16, and the row has to keep the part that tells them apart.
        assertEquals("V16.0.3", romBuildOf(mapOf("ro.build.version.opporom" to "V16.0.3")))
        assertEquals("V16.0.501", romBuildOf(mapOf("ro.build.version.opporom" to "V16.0.501")))
        assertEquals("OS2.0.7.0.VNCCNXM", romBuildOf(mapOf("ro.mi.os.version.name" to "OS2.0.7.0.VNCCNXM")))
        assertEquals("", romBuildOf(mapOf("ro.rom.version" to "something else")))
        // HyperOS wins over the MIUI props it still carries, exactly like the family does.
        assertEquals(
            "OS2.0.7.0.VNCCNXM",
            romBuildOf(mapOf("ro.mi.os.version.name" to "OS2.0.7.0.VNCCNXM", "ro.miui.ui.version.name" to "V816")),
        )
    }

    @Test
    fun hyperOsThreeTakesTheIncrementalBuildNotTheName() {
        // HyperOS 3 起 `ro.mi.os.version.name` 只到 "OS3.0"，完整版本在增量串里；方案行要存后者，
        // 否则界面永远停在「HyperOS 3.0」，看不出它是在 3.0.0355 上验证的。
        assertEquals(
            "OS3.0.0355.0.WNCCNXM",
            romBuildOf(
                mapOf(
                    "ro.mi.os.version.name" to "OS3.0",
                    "ro.build.version.incremental" to "OS3.0.0355.0.WNCCNXM",
                ),
            ),
        )
        // 增量串读不到时仍退回 name，不至于丢成空。
        assertEquals("OS3.0", romBuildOf(mapOf("ro.mi.os.version.name" to "OS3.0")))
    }

    @Test
    fun buildLabelFallsBackToTheMajorVersion() {
        assertEquals("ColorOS 16.0.3", romLabelOf("coloros", 16, "V16.0.3"))
        assertEquals("ColorOS 16.0.501", romLabelOf("coloros", 16, "V16.0.501"))
        // A row from before builds were stored: the old label, unchanged.
        assertEquals("ColorOS 16", romLabelOf("coloros", 16, ""))
        // One UI reports one number; the label reads it as major.minor.
        assertEquals("One UI 8.1", romLabelOf("oneui", 8, "80100"))
        // A stock ROM has no name to qualify.
        assertEquals("Android", romLabelOf("stock", 0, "16.0.3"))
    }

    @Test
    fun planMatchesStayOnTheMajorVersionWhileSameBuildIsExact() {
        val mine = DeviceProfile(
            brand = "OPPO", model = "PHK110", device = "salto", sdk = 36, release = "16",
            rom = "coloros", romVersion = 16, fingerprint = "x", romBuild = "V16.0.501",
        )
        val older = CommunityPlan(
            id = 1, title = "t", note = "", packages = listOf("com.a"), votes = 0, comments = 0,
            createdAt = "", rebootedAt = "", model = "PHK110", rom = "coloros", romVersion = 16,
            romBuild = "V16.0.3",
        )
        // Listed, because hiding a whole major version would empty the page after a point update…
        assertEquals(true, older.matches(mine))
        // …but the page can say the package list was proven on another build.
        assertEquals(false, older.sameBuild(mine))
        assertEquals(true, older.copy(romBuild = "V16.0.501").sameBuild(mine))
        // Rows that carry no build predate the column: nothing to compare, so no warning.
        assertEquals(true, older.copy(romBuild = "").sameBuild(mine))
    }
}
