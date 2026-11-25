package io.github.samolego.canta.util

import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.packages.isValidPackageName
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
    /**
     * What the tool needs before it can do anything, in one line at the top of the detail dialog:
     * the privilege it demands and the ones that are not enough for it. Empty when the tool works
     * with whatever the shell already has.
     */
    val needs: String = "",
    /**
     * 芯片右上角那一小块字，代替标题里的括号。
     *
     * 括号挂在标题后面会把整块撑宽（`停用应用（root）` 比 `停用应用` 宽出一大截，一行就少放一个，
     * 右边的空位全浪费），所以「只有 root 能做」写成 [badge] = "root" 画在角上，标题只留动作本身。
     */
    val badge: String = "",
    val why: String = "",
    val command: String = "",
    val action: String? = null,
    /**
     * What the card's own button says on it. Empty on the cards where "run" is the whole story;
     * a card that can also be undone says "关闭" here and gets the undo from [restore].
     */
    val runLabel: String = "",
    /**
     * The way back, when the tweak can be put back the way it was: the button in the detail dialog
     * that undoes [command], with its own label and its own script.
     */
    val restore: RomTweakChoice? = null,
    /**
     * What the card asks for before it can run: a package name, in the words of the JSON. Empty on
     * the cards that need nothing, and the command then has no [PACKAGE_TOKEN] to fill in.
     */
    val prompt: String = "",
)

/** One button in the detail dialog, with the script it runs: see [RomTweak.restore]. */
@Serializable
data class RomTweakChoice(val label: String, val command: String)

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

    /**
     * The card with [id], or null when this build does not ship it.
     *
     * A page that acts on the same switch as one card looks it up here instead of carrying a second
     * copy of the script: the two entry points then always run exactly the same thing.
     */
    fun byId(id: String): RomTweak? = set.tweaks.firstOrNull { it.id == id }

    companion object {
        const val RESOURCE_PATH = "files/rom_tweaks.json"

        /** Puts back every app uninstalled for the current user, one by one. */
        const val ACTION_RESTORE_ALL = "restore-uninstalled"

        /** Runs the card's [RomTweak.command] as one `sh` script through the privileged shell. */
        const val ACTION_SHELL = "shell"

        /**
         * The same, for a card whose command is only known once the user names a package: the
         * shell command carries [PACKAGE_TOKEN], and [commandFor] fills it in — or refuses, when the
         * name is not a package name or is one of the few that must never be touched.
         *
         * This exists because some refusals are about *who* is asking rather than about the
         * package: a ROM that answers "no root permission" for every `setApplicationEnabledSetting`
         * leaves the shell command as the only route, and that route needs the name typed in.
         */
        const val ACTION_SHELL_WITH_PACKAGE = "shell-with-package"

        /** Where [commandFor] puts the package the user typed. */
        const val PACKAGE_TOKEN = "{{pkg}}"

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

/**
 * Packages a typed-in shell command must never touch: without them the phone has no home screen,
 * no keyboard, no way into settings, or no window manager. The list is deliberately short — it is
 * a backstop against a typo, not a safety model.
 */
val NEVER_TOUCH: Set<String> = setOf(
    "android",
    "com.android.systemui",
    "com.android.settings",
    "com.android.packageinstaller",
    "com.android.phone",
    "com.android.shell",
    "com.android.launcher3",
    "com.android.inputmethod.latin",
    "com.miui.home",
    "com.miui.securitycenter",
)

/**
 * The script to run for a prompt-driven card, with [packageName] in place of the token, or null
 * when this card takes no package or the name may not be used.
 *
 * The name is checked against the package-name shape before it reaches the shell — the command is a
 * `sh` script, so an unchecked string would be an injection point, not just a wrong answer.
 */
fun RomTweak.commandFor(packageName: String): String? {
    if (action != RomTweaks.ACTION_SHELL_WITH_PACKAGE) return null
    val name = packageName.trim()
    if (!isValidPackageName(name) || name in NEVER_TOUCH) return null
    return command.replace(RomTweaks.PACKAGE_TOKEN, name)
}
