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
 * A ROM family with its major version, the way a plan row names the build it was made on:
 * "HyperOS 2", or just "Android" on a stock build — a version number means nothing there.
 */
fun romLabelOf(rom: String, romVersion: Int): String {
    val name = romName(rom)
    return if (romVersion > 0 && name != "Android") "$name $romVersion" else name
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
    else -> "Android"
}

/**
 * The names the phone is *sold* under, best first.
 *
 * MIUI/HyperOS carries the retail model number in `ro.product.model` ("23113RKC6C" for a Redmi
 * K70) and keeps the marketing name in a separate `marketname` property; stock Android and most
 * other ROMs put the marketing name straight into `ro.product.model`. Reading these first is what
 * makes the UI say "Redmi K70" instead of a part number.
 */
val MARKET_NAME_PROP_KEYS: List<String> = listOf(
    "ro.product.marketname",
    "ro.product.odm.marketname",
    "ro.product.vendor.marketname",
    "ro.product.system.marketname",
    "ro.product.product.marketname",
    "ro.config.marketing_name",
    "ro.product.model.marketing",
)

/** The marketing name of the phone in [props], or an empty string when the ROM has none. */
fun marketNameOf(props: Map<String, String>): String =
    MARKET_NAME_PROP_KEYS.firstNotNullOfOrNull { key ->
        props[key]?.trim()?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
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
    "ro.miui.ui.version.name",
    "ro.miui.ui.version.code",
    "ro.build.version.oneui",
    "ro.build.version.realmeui",
    "ro.build.version.opporom",
    "ro.vivo.os.version",
    "ro.vivo.os.name",
    "ro.build.version.magic",
    "ro.build.version.emui",
    "ro.oxygen.version",
    "ro.rom.version",
    "ro.build.nothing.version",
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
    prop("ro.build.version.opporom")?.let { return ctx("coloros", leadingNumber(it)) }
    prop("ro.vivo.os.version")?.let {
        val rom = if (prop("ro.vivo.os.name")?.contains("origin", ignoreCase = true) == true) "originos" else "funtouch"
        return ctx(rom, leadingNumber(it))
    }
    prop("ro.build.version.magic")?.let { return ctx("magicos", leadingNumber(it)) }
    prop("ro.build.version.emui")?.let { return ctx("emui", leadingNumber(it)) }
    prop("ro.oxygen.version")?.let { return ctx("oxygenos", leadingNumber(it)) }
    prop("ro.rom.version")?.takeIf { it.contains("oxygenos", ignoreCase = true) }
        ?.let { return ctx("oxygenos", leadingNumber(it)) }
    prop("ro.build.nothing.version")?.let { return ctx("nothingos", leadingNumber(it)) }
    return ctx("stock", 0)
}
