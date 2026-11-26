package io.github.samolego.canta.util

/**
 * Coarse description of the phone, sent once per analytics request: the
 * manufacturer, the ROM family and its major version, and the Android API
 * level. Never the model, build number, fingerprint or anything else that
 * could single a device out.
 */
data class DeviceContext(
    val brand: String,
    val rom: String,
    val romVersion: Int,
    val sdk: Int,
) {
    companion object {
        /** Used when the device can't be read. */
        val UNKNOWN = DeviceContext("unknown", "unknown", 0, 0)
    }

    /** Whether per-ROM scores can be asked for. */
    val hasRom: Boolean get() = rom != "unknown" && rom != "other"

    /** "HyperOS 1", or just "Android" for stock. */
    fun romLabel(): String {
        val name = romName(rom)
        return if (romVersion > 0 && name != "Android") "$name $romVersion" else name
    }
}

/**
 * A ROM family with its build, the way a plan row names the build it was made on:
 * "HyperOS 16.0.3", or just "Android" on a stock build — a version number means nothing there.
 *
 * [build] is the ROM's own string, kept on the phone and stored on the row (see [romBuildOf]).
 * One model ships several builds a year — "16.0.3" and "16.0.501" are different package sets on
 * the same phone — so the major version alone is too coarse to tell two plans apart. A row from
 * before builds were stored has none, and then the major version is all there is.
 */
fun romLabelOf(rom: String, romVersion: Int, build: String = ""): String {
    val name = romName(rom)
    if (name == "Android") return name
    val detail = romBuildLabel(rom, build)
    return if (detail.isNotEmpty()) "$name $detail"
    else if (romVersion > 0) "$name $romVersion"
    else name
}

private val BUILD_NUMBERS = Regex("""\d+(?:\.\d+)*""")

/**
 * The part of a build string worth showing: "16.0.3" out of "V16.0.3", "2.0.7" out of
 * "OS2.0.7.0.VNCCNXM" (the tail is a region code, not a version), and "8.1" out of One UI's
 * single number "80100".
 *
 * Empty when the string carries no digits at all — the caller then falls back to the major version.
 */
fun romBuildLabel(rom: String, build: String): String {
    val text = build.trim()
    if (text.isEmpty()) return ""
    val numbers = BUILD_NUMBERS.find(text)?.value ?: return ""
    return if (rom.equals("oneui", ignoreCase = true) && numbers.length >= 5) {
        val n = numbers.toLongOrNull() ?: return numbers
        "${n / 10000}.${(n / 100) % 100}"
    } else {
        numbers.split('.').filter { it.isNotEmpty() }.take(3).joinToString(".")
    }
}

/**
 * The Android release a given API level is sold as: 34 for "14", 36 for "16".
 *
 * Used where only the level was kept — a plan row stores `sdk`, not the release string — so a page
 * can name the version of the phone that produced it without borrowing the reader's own. An unknown
 * level answers "" and the caller leaves the part out: "Android 0" on a header is worse than nothing.
 * 32 is "12L", the large-screen line, and would otherwise read as plain "12".
 */
fun androidReleaseOf(sdk: Int): String = when (sdk) {
    in 21..22 -> if (sdk == 21) "5.0" else "5.1"
    23 -> "6"
    24 -> "7"
    25 -> "7.1"
    26 -> "8"
    27 -> "8.1"
    28 -> "9"
    29 -> "10"
    30 -> "11"
    31 -> "12"
    32 -> "12L"
    33 -> "13"
    34 -> "14"
    35 -> "15"
    36 -> "16"
    else -> ""
}

/** Display name of a ROM family ("hyperos" becomes "HyperOS"), "Android" for stock or unknown ones. */
fun romName(rom: String): String = when (rom) {
    "hyperos" -> "HyperOS"
    "miui" -> "MIUI"
    "oneui" -> "One UI"
    "coloros" -> "ColorOS"
    "realmeui" -> "realme UI"
    "originos" -> "OriginOS"
    "funtouch" -> "Funtouch OS"
    "magicos" -> "MagicOS"
    "emui" -> "EMUI"
    "oxygenos" -> "OxygenOS"
    "nothingos" -> "Nothing OS"
    "flyme" -> "Flyme"
    "myos" -> "MyOS"
    else -> "Android"
}

/**
 * The names the phone is *sold* under, best first.
 *
 * MIUI/HyperOS carries the retail model number in `ro.product.model` ("23113RKC6C" for a Redmi
 * K70) and keeps the marketing name in a separate `marketname` property; stock Android and most
 * other ROMs put the marketing name straight into `ro.product.model`. Reading these first is what
 * makes the UI say "Redmi K70" instead of a part number.
 *
 * The `marketname` family is Xiaomi's and Honor's spelling; the vendors that carry a retail code
 * instead spell it their own way, one key each — `ro.vivo.market.name` on Funtouch/OriginOS
 * ("vivo X Fold3 Pro"), `ro.oppo.market.name` on ColorOS, and the two oplus keys OnePlus uses for
 * the Chinese and English name ("一加 Ace 2"). Without those, a vivo or OnePlus upload stores the
 * code, and every chip for that model shows a part number forever. Samsung's One UI has no such
 * property at all — its name is a lookup table, not a prop, which is what
 * `private.device_names` on the backend is for.
 */
val MARKET_NAME_PROP_KEYS: List<String> = listOf(
    "ro.product.marketname",
    "ro.product.odm.marketname",
    "ro.product.vendor.marketname",
    "ro.product.system.marketname",
    "ro.product.product.marketname",
    // vivo / iQOO.
    "ro.vivo.market.name",
    // OPPO / realme / OnePlus on ColorOS, Chinese name then English one.
    "ro.oppo.market.name",
    "ro.vendor.oplus.market.name",
    "ro.vendor.oplus.market.enname",
    "ro.config.marketing_name",
    "ro.product.model.marketing",
)

/** The marketing name of the phone in [props], or an empty string when the ROM has none. */
fun marketNameOf(props: Map<String, String>): String =
    MARKET_NAME_PROP_KEYS.firstNotNullOfOrNull { key ->
        props[key]?.trim()?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
    }.orEmpty()

/**
 * The market name, asked for one property at a time when a bulk `getprop` read came back without it.
 *
 * The bulk read is one process and usually enough, but it is also the first thing to come back empty
 * on a locked-down ROM, on a helper that truncates long output, or after a read that failed and left
 * an empty map behind. That is how a plan ends up stored with the retail number ("M2006J10C") and no
 * name people recognise ("Redmi K30 Ultra") — the model chips then show the number to everybody.
 * Asking for the handful of keys directly costs one short read each and only happens when the first
 * answer was empty.
 *
 * [read] is the platform's own "get this property" call: a process on Android, a shell line over ADB.
 */
suspend fun marketNameFallback(read: suspend (String) -> String?): String =
    MARKET_NAME_PROP_KEYS.firstNotNullOfOrNull { key ->
        runCatching { read(key)?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
    }.orEmpty()

/**
 * Properties that identify this exact phone. They are read locally only, for matching plans and
 * safety rules; none of them is ever sent as telemetry.
 */
val DEVICE_IDENTITY_PROP_KEYS: Set<String> = MARKET_NAME_PROP_KEYS.toSet() + setOf(
    "ro.product.brand",
    "ro.product.model",
    "ro.product.device",
    "ro.build.version.release",
    "ro.build.fingerprint",
)

/** The only system properties ever read to build a [DeviceContext]. */
val DEVICE_PROP_KEYS: Set<String> = setOf(
    "ro.product.manufacturer",
    "ro.build.version.sdk",
    "ro.mi.os.version.name",
    "ro.mi.os.version.incremental",
    "ro.build.version.incremental",
    "ro.miui.ui.version.name",
    "ro.miui.ui.version.code",
    "ro.build.version.oneui",
    "ro.build.version.realmeui",
    "ro.build.version.opporom",
    // ColorOS 6 以后 OPPO 实机上报的是这个键，`opporom` 多半是空的；一加/realme 共用同一套
    // oplus 前缀。少了它，那些机器一路落到 `ctx("stock", 0)`，界面上就只剩「Android」。
    "ro.build.version.oplusrom",
    "ro.build.version.oplusrom.display",
    "ro.vivo.os.version",
    "ro.vivo.os.name",
    "ro.build.version.magic",
    "ro.build.version.emui",
    "ro.oxygen.version",
    "ro.rom.version",
    "ro.build.nothing.version",
    // 魅族 Flyme，以及努比亚/红魔 MyOS（键名是固定字面量，与机器名无关）。
    "ro.build.display.id",
    "ro.flyme.version",
    "ro.build.flyme.version",
    "ro.flyme.public.version",
    "ro.build.version.myos",
    "ro.myos.version",
    "ro.nubia.os.version",
    // 兜底：所有 ROM 都有，至少能把 Android 版本说出来。
    "ro.build.version.release",
    "ro.system.build.version.release",
)

/**
 * Everything a device read keeps: the telemetry whitelist plus the local-only identity props. Only
 * [DeviceContext] out of this ever leaves the phone; the identity props stay on it.
 */
val LOCAL_PROP_KEYS: Set<String> = DEVICE_PROP_KEYS + DEVICE_IDENTITY_PROP_KEYS

/**
 * Parses `getprop` output (`[key]: [value]` lines), keeping only [keys];
 * everything else is dropped immediately.
 */
fun parseGetprop(output: String, keys: Set<String> = DEVICE_PROP_KEYS): Map<String, String> {
    val result = mutableMapOf<String, String>()
    for (line in output.lineSequence()) {
        val l = line.trim()
        if (!l.startsWith("[")) continue
        val keyEnd = l.indexOf("]: [")
        if (keyEnd < 0 || !l.endsWith("]")) continue
        val key = l.substring(1, keyEnd)
        if (key in keys) result[key] = l.substring(keyEnd + 4, l.length - 1)
    }
    return result
}

/** First run of digits in [s] as an int, or null. */
private fun leadingNumber(s: String?): Int? {
    val digits = s?.dropWhile { !it.isDigit() }?.takeWhile { it.isDigit() } ?: return null
    return digits.toIntOrNull()
}

private fun miuiMajor(name: String?, code: String?): Int? {
    code?.trim()?.toIntOrNull()?.takeIf { it in 1..20 }?.let { return it }
    val digits = name?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() } ?: return null
    val n = digits.toIntOrNull() ?: return null
    if (n <= 20) return n
    return digits.take(2).toInt().takeIf { it <= 20 } ?: digits.take(1).toInt()
}

/**
 * The ROM's own build string, exactly as the property reports it ("16.0.3", "OS2.0.7.0.VNCCNXM",
 * "80100"), or "" on a stock build.
 *
 * [DeviceContext.romVersion] is the major version on purpose: telemetry must not be able to
 * single a phone out, and a build number is close to a fingerprint. This one never leaves the
 * phone by itself — it is stored only on a plan the user uploads, next to the model, so that two
 * plans for the same phone but different builds ("16.0.3" and "16.0.501") can be told apart.
 */
fun romBuildOf(props: Map<String, String>): String {
    // HyperOS 3 起 `ro.mi.os.version.name` 只到主版本（"OS3.0"），完整版本在增量串里
    // （"OS3.0.0355.0.WNCCNXM"）。只读 name 的话，方案存下来的永远是「OS3.0」，界面也只能
    // 显示「HyperOS 3.0」——读的人看不出它是在哪个具体版本上验证的。
    val miName = props["ro.mi.os.version.name"]?.trim()
    if (!miName.isNullOrEmpty()) {
        val detailed = MI_BUILD_DETAIL_KEYS.firstNotNullOfOrNull { key ->
            props[key]?.trim()?.takeIf { it.isNotEmpty() }
        }
        return detailed ?: miName
    }
    val key = ROM_BUILD_PROP_KEYS.firstOrNull { !props[it].isNullOrBlank() } ?: return ""
    return props[key]?.trim().orEmpty()
}

/** The MIUI/HyperOS properties that carry the full build, most specific first. */
private val MI_BUILD_DETAIL_KEYS: List<String> = listOf(
    "ro.mi.os.version.incremental",
    "ro.build.version.incremental",
)

/**
 * [romBuildOf] with a fallback: the same keys, asked for one at a time when the bulk `getprop`
 * read came back without any of them.
 *
 * The bulk read is a single process and usually enough, but a locked-down ROM, a helper that
 * truncates long output, or a read that failed and left an empty map behind leaves it empty — and
 * a plan uploaded from that read stores no build at all, which is exactly how a row ends up
 * showing "HyperOS 3" instead of "HyperOS 3.0.0355".
 */
suspend fun romBuildFallback(read: suspend (String) -> String?): String {
    suspend fun ask(key: String): String? = runCatching { read(key)?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    // Same precedence as [romBuildOf]: on MIUI/HyperOS the full build is the incremental string,
    // not the name (which stopped at "OS3.0" on HyperOS 3).
    val miName = ask("ro.mi.os.version.name")
    if (miName != null) {
        for (key in MI_BUILD_DETAIL_KEYS) ask(key)?.let { return it }
        return miName
    }
    return ROM_BUILD_PROP_KEYS.firstNotNullOfOrNull { key -> ask(key) }.orEmpty()
}

/**
 * The properties [romBuildOf] reads, in the same precedence as [deviceContextOf] so the build and
 * the family never come from two different ROMs (a HyperOS phone whose old MIUI keys linger).
 */
val ROM_BUILD_PROP_KEYS: List<String> = listOf(
    "ro.mi.os.version.name",
    "ro.miui.ui.version.name",
    "ro.build.version.oneui",
    "ro.build.version.realmeui",
    "ro.build.version.opporom",
    "ro.build.version.oplusrom",
    "ro.vivo.os.version",
    "ro.build.version.magic",
    "ro.build.version.emui",
    "ro.oxygen.version",
    "ro.flyme.version",
    "ro.build.version.myos",
    "ro.build.nothing.version",
)

/** Builds the [DeviceContext] from system properties (see [DEVICE_PROP_KEYS]). */
fun deviceContextOf(props: Map<String, String>): DeviceContext {
    fun prop(key: String) = props[key]?.trim()?.takeIf { it.isNotEmpty() }

    val brand = prop("ro.product.manufacturer")?.lowercase() ?: "unknown"
    val sdk = prop("ro.build.version.sdk")?.toIntOrNull() ?: 0

    fun ctx(rom: String, version: Int?) = DeviceContext(brand, rom, version ?: 0, sdk)

    prop("ro.mi.os.version.name")?.let { return ctx("hyperos", leadingNumber(it)) }
    if (prop("ro.miui.ui.version.name") != null || prop("ro.miui.ui.version.code") != null) {
        return ctx("miui", miuiMajor(prop("ro.miui.ui.version.name"), prop("ro.miui.ui.version.code")))
    }
    prop("ro.build.version.oneui")?.let { v -> v.toIntOrNull()?.let { return ctx("oneui", it / 10000) } }
    prop("ro.build.version.realmeui")?.let { return ctx("realmeui", leadingNumber(it)) }
    // ColorOS 6 以后 `ro.build.version.opporom` 多半是空的，实机只报 `ro.build.version.oplusrom`；
    // 一加的 OxygenOS 装在 ColorOS 底座上，报的也是 oplus 那个键，所以先看 OxygenOS 自己的键。
    prop("ro.build.version.oplusrom")?.let { value ->
        val oxygen =
            prop("ro.oxygen.version") != null ||
                prop("ro.rom.version")?.contains("oxygen", ignoreCase = true) == true
        return ctx(if (oxygen) "oxygenos" else "coloros", leadingNumber(value))
    }
    prop("ro.build.version.opporom")?.let { return ctx("coloros", leadingNumber(it)) }
    prop("ro.build.version.oplusrom.display")?.let { return ctx("coloros", leadingNumber(it)) }
    prop("ro.vivo.os.version")?.let {
        val rom = if (prop("ro.vivo.os.name")?.contains("origin", ignoreCase = true) == true) "originos" else "funtouch"
        return ctx(rom, leadingNumber(it))
    }
    prop("ro.build.version.magic")?.let { return ctx("magicos", leadingNumber(it)) }
    prop("ro.build.version.emui")?.let { return ctx("emui", leadingNumber(it)) }
    prop("ro.oxygen.version")?.let { return ctx("oxygenos", leadingNumber(it)) }
    prop("ro.rom.version")?.takeIf { it.contains("oxygenos", ignoreCase = true) }
        ?.let { return ctx("oxygenos", leadingNumber(it)) }
    // 魅族 Flyme：`ro.build.display.id` 形如「Flyme OS 8.2.0.0A」，没有数字前缀，取第一段数字。
    flymeVersion { prop(it) }?.let { return ctx("flyme", leadingNumber(it)) }
    // 努比亚 / 红魔 MyOS：`ro.build.display.id` 形如「RedMagic OS 10.0.1」。
    myosVersion { prop(it) }?.let { return ctx("myos", leadingNumber(it)) }
    prop("ro.build.nothing.version")?.let { return ctx("nothingos", leadingNumber(it)) }
    // 兜底：既没有 ROM 键（努比亚、部分定制机），也至少把 Android 版本说出来，别一律 0。
    val release = prop("ro.build.version.release") ?: prop("ro.system.build.version.release")
    return ctx("stock", leadingNumber(release))
}

/** Flyme's own version string, from the dedicated keys or the `display.id` line, or null. */
private fun flymeVersion(prop: (String) -> String?): String? {
    prop("ro.flyme.version")?.let { return it }
    prop("ro.build.flyme.version")?.let { return it }
    prop("ro.flyme.public.version")?.let { return it }
    val display = prop("ro.build.display.id") ?: return null
    return display.takeIf { it.contains("flyme", ignoreCase = true) }
}

/** MyOS / RedMagic OS's own version string, from the dedicated keys or the `display.id` line, or null. */
private fun myosVersion(prop: (String) -> String?): String? {
    prop("ro.build.version.myos")?.let { return it }
    prop("ro.myos.version")?.let { return it }
    prop("ro.nubia.os.version")?.let { return it }
    val display = prop("ro.build.display.id") ?: return null
    return display.takeIf { it.contains("myos", ignoreCase = true) || it.contains("redmagic", ignoreCase = true) }
}
