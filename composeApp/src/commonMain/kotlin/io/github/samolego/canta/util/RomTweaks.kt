package io.github.samolego.canta.util

import io.github.samolego.canta.generated.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One per-ROM tweak: something the stock system does that can be worked around, described as
 * steps a person can follow. Actions that need root or shell are deliberately not automated;
 * only the paths are offered for copying.
 *
 * [rom] is a ROM family key (`xiaomi`, `oppo`, `vivo`, `honor`, …) or `all`.
 */
@Serializable
data class RomTweak(
    val id: String,
    val rom: String,
    val title: String,
    val why: String = "",
    val steps: List<String> = emptyList(),
    val paths: List<String> = emptyList(),
    /** Packages the tweak is about, shown so the reader can check whether they are installed. */
    val packages: List<String> = emptyList(),
)

@Serializable
data class RomTweakSet(val version: Int = 1, val updated: String = "", val tweaks: List<RomTweak> = emptyList())

/**
 * The shipped tweaks, grouped for display. The ROM family of the current phone comes first and is
 * marked, so a Xiaomi owner is not made to scroll past OPPO advice.
 */
class RomTweaks(private val set: RomTweakSet) {

    constructor(json: String) : this(Json { ignoreUnknownKeys = true }.decodeFromString(RomTweakSet.serializer(), json))

    val all: List<RomTweak> get() = set.tweaks

    /** [all] ordered so the groups matching [profile] come first; group key is included. */
    fun groupedFor(profile: DeviceProfile?): List<Pair<String, List<RomTweak>>> {
        val mine = profile?.let { familyKeyOf(it.rom) }
        val groups = set.tweaks.groupBy { familyKeyOf(it.rom) }
        return groups.entries
            .sortedWith(
                compareBy(
                    { entry -> if (entry.key == "all") 2 else if (entry.key == mine) 0 else 1 },
                    { entry -> entry.key },
                )
            )
            .map { it.key to it.value }
    }

    companion object {
        const val RESOURCE_PATH = "files/rom_tweaks.json"

        suspend fun bundled(): RomTweaks = runCatching {
            RomTweaks(Res.readBytes(RESOURCE_PATH).decodeToString())
        }.getOrDefault(RomTweaks(RomTweakSet()))
    }
}

/** Maps a [DeviceContext.rom] key onto the family a tweak is filed under. */
fun familyKeyOf(rom: String): String = when (rom.lowercase()) {
    "hyperos", "miui" -> "xiaomi"
    "coloros", "realmeui" -> "oppo"
    "originos", "funtouch" -> "vivo"
    "magicos", "emui" -> "honor"
    "oneui" -> "samsung"
    "oxygenos", "nothingos" -> "other"
    "all" -> "all"
    else -> "other"
}

/** Display name of a family key. */
fun familyLabel(key: String): String = when (key) {
    "xiaomi" -> "小米 / Redmi（MIUI · HyperOS）"
    "oppo" -> "OPPO / realme（ColorOS · realme UI）"
    "vivo" -> "vivo / iQOO（OriginOS · Funtouch）"
    "honor" -> "荣耀 / 华为（MagicOS · EMUI）"
    "samsung" -> "三星（One UI）"
    "all" -> "通用"
    else -> "其他"
}
