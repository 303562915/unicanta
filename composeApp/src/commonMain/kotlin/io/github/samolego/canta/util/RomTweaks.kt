package io.github.samolego.canta.util

import io.github.samolego.canta.generated.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One system tweak: a single thing the stock system does, described as a card that offers either a
 * button Canta can run itself or a command to copy.
 *
 * @param why what the system does and why the tweak works, one or two sentences.
 * @param command the shell equivalent of the tweak, shown so it can be run by hand.
 * @param action the operation behind the card's button, one of the `ACTION_*` keys of [RomTweaks];
 *   null for a card that only explains.
 */
@Serializable
data class RomTweak(
    val id: String,
    val title: String,
    val why: String = "",
    val command: String = "",
    val action: String? = null,
)

@Serializable
data class RomTweakSet(val version: Int = 1, val updated: String = "", val tweaks: List<RomTweak> = emptyList())

/** The shipped tweaks, in file order: the page is a flat list, not a per-vendor catalogue. */
class RomTweaks(private val set: RomTweakSet) {

    constructor(json: String) : this(Json { ignoreUnknownKeys = true }.decodeFromString(RomTweakSet.serializer(), json))

    val all: List<RomTweak> get() = set.tweaks

    companion object {
        const val RESOURCE_PATH = "files/rom_tweaks.json"

        /** Puts back every app uninstalled for the current user, one by one. */
        const val ACTION_RESTORE_ALL = "restore-uninstalled"

        suspend fun bundled(): RomTweaks = runCatching {
            RomTweaks(Res.readBytes(RESOURCE_PATH).decodeToString())
        }.getOrDefault(RomTweaks(RomTweakSet()))
    }
}
