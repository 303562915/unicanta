package io.github.samolego.canta.util

import io.github.samolego.canta.generated.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One system tweak: a single thing the stock system does, filed under the vendor whose ROM it is
 * about, described as a card that offers either a button Canta can run itself or a command to copy.
 *
 * @param rom the ROM key of the phone it is for (`hyperos`, `miui`, `coloros`, `realmeui`,
 *   `originos`, `funtouch`, `emui`, `magicos`, `oneui`); see [familyKeyOf].
 * @param why what the system does and why the tweak works, one or two sentences.
 * @param command the shell equivalent of the tweak, shown so it can be run by hand.
 * @param action the operation behind the card's button, one of the `ACTION_*` keys of [RomTweaks];
 *   null for a card that only explains.
 */
@Serializable
data class RomTweak(
    val id: String,
    val rom: String,
    val title: String,
    val why: String = "",
    val command: String = "",
    val action: String? = null,
)

@Serializable
data class RomTweakSet(val version: Int = 1, val updated: String = "", val tweaks: List<RomTweak> = emptyList())

/**
 * The shipped tools, filed by the vendor whose ROM each one is about.
 *
 * There are exactly the five vendor groups in [FAMILIES] and nothing else: no "other" bucket and no
 * "general" group. A tweak filed under a ROM that maps to none of them is dropped rather than shown
 * under a made-up heading — the fix is to file it under a real vendor in the JSON.
 */
class RomTweaks(private val set: RomTweakSet) {

    constructor(json: String) : this(Json { ignoreUnknownKeys = true }.decodeFromString(RomTweakSet.serializer(), json))

    /**
     * The tools to show for [profile], grouped by vendor.
     *
     * A tool is written for one vendor's ROM, so a phone whose ROM is recognised only gets its own
     * group: a Xiaomi owner is not offered a ColorOS script, and a vendor without tools is simply
     * absent instead of appearing as an empty section. An unrecognised ROM keeps every group,
     * because nothing can be ruled out for it; the group of the phone's own ROM, when there is
     * one, comes first.
     */
    fun groupedFor(profile: DeviceProfile?): List<Pair<String, List<RomTweak>>> {
        val mine = profile?.let { familyKeyOf(it.rom) }
        val groups = set.tweaks.groupBy { familyKeyOf(it.rom) }
        return FAMILIES
            .mapNotNull { family -> groups[family]?.let { family to it } }
            .filter { (family, _) -> mine == null || family == mine }
            .sortedBy { (family, _) -> if (family == mine) 0 else 1 }
    }

    companion object {
        const val RESOURCE_PATH = "files/rom_tweaks.json"

        /** Puts back every app uninstalled for the current user, one by one. */
        const val ACTION_RESTORE_ALL = "restore-uninstalled"

        /** Runs the card's [RomTweak.command] as one `sh` script through the privileged shell. */
        const val ACTION_SHELL = "shell"

        /** The vendor groups, in display order. */
        val FAMILIES = listOf("xiaomi", "oppo", "vivo", "honor", "samsung")

        suspend fun bundled(): RomTweaks = runCatching {
            RomTweaks(Res.readBytes(RESOURCE_PATH).decodeToString())
        }.getOrDefault(RomTweaks(RomTweakSet()))
    }
}

/** The vendor group a [DeviceContext.rom] key belongs to, or null when it has none. */
fun familyKeyOf(rom: String): String? = when (rom.lowercase()) {
    "hyperos", "miui" -> "xiaomi"
    "coloros", "realmeui" -> "oppo"
    "originos", "funtouch" -> "vivo"
    "emui", "magicos" -> "honor"
    "oneui" -> "samsung"
    else -> null
}

/** Display name of a family key. */
fun familyLabel(key: String): String = when (key) {
    "xiaomi" -> "小米 / Redmi（MIUI · HyperOS）"
    "oppo" -> "OPPO / realme（ColorOS · realme UI）"
    "vivo" -> "vivo / iQOO（OriginOS · Funtouch）"
    "honor" -> "华为 / 荣耀（EMUI · MagicOS）"
    "samsung" -> "三星（One UI）"
    else -> key
}
