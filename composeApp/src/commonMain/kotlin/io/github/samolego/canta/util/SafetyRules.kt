package io.github.samolego.canta.util

import io.github.samolego.canta.generated.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The phone Canta is talking to, read locally only: model and build fingerprint are exactly what
 * the telemetry opt-in promises never to send, so they live here and nowhere else.
 *
 * The point is the safety rules: whether a package may be removed depends on the model and the
 * ROM version, not just on the package name.
 */
data class DeviceProfile(
    val brand: String,
    val model: String,
    val device: String,
    val sdk: Int,
    val release: String,
    val rom: String,
    val romVersion: Int,
    val fingerprint: String,
) {
    /** "Xiaomi 23127PN0CC (fuxi) · HyperOS 2 · Android 15" */
    fun label(): String {
        val name = romName(rom)
        val romPart = if (romVersion > 0 && name != "Android") "$name $romVersion" else name
        return "$brand $model ($device) · $romPart · Android $release"
    }
}

/** How dangerous removing a package is on a given device. */
enum class SafetyStatus {
    /** Removing it is known to be fine here. */
    SAFE,

    /** Works for most people, but something is known to break. */
    RISKY,

    /** Removing it is known to strand the device: bootloop, no launcher, no settings. */
    CRITICAL,
}

/**
 * One rule, as shipped in `files/safety_rules.json`.
 *
 * [scope] narrows the rule to part of the fleet: `ALL`, `BRAND:xiaomi`, `ROM:hyperos`,
 * `ROM:hyperos:2` (that ROM major version) or `MODEL:fuxi` (device codename).
 */
@Serializable
data class SafetyRule(
    val pkg: String,
    val status: String,
    val scope: String = "ALL",
    val why: String = "",
) {
    val safety: SafetyStatus get() = runCatching { SafetyStatus.valueOf(status.uppercase()) }.getOrDefault(SafetyStatus.RISKY)

    fun matches(profile: DeviceProfile?): Boolean {
        if (scope == "ALL") return true
        if (profile == null) return false
        val parts = scope.split(':')
        return when (parts[0].uppercase()) {
            "BRAND" -> parts.getOrNull(1)?.equals(profile.brand, ignoreCase = true) == true
            "MODEL" -> parts.getOrNull(1)?.equals(profile.device, ignoreCase = true) == true
            "ROM" -> {
                if (!parts.getOrNull(1).equals(profile.rom, ignoreCase = true)) return false
                val wanted = parts.getOrNull(2)?.toIntOrNull() ?: return true
                wanted == profile.romVersion
            }
            else -> false
        }
    }
}

@Serializable
data class SafetyRuleSet(
    val version: Int = 1,
    val updated: String = "",
    val rules: List<SafetyRule> = emptyList(),
)

/** What the rules say about one package on this device. */
data class SafetyVerdict(val pkg: String, val status: SafetyStatus, val why: String)

/**
 * Judge for "can this be removed on *this* phone": a rule for the exact model or ROM version wins
 * over a ROM-family rule, which wins over a fleet-wide one. A package no rule mentions is unknown,
 * which is not the same as safe.
 */
class SafetyRules(private val set: SafetyRuleSet) {

    constructor(json: String) : this(Json { ignoreUnknownKeys = true }.decodeFromString(SafetyRuleSet.serializer(), json))

    /** Every shipped rule, unfiltered: the screen shows which ones apply here. */
    val all: List<SafetyRule> get() = set.rules

    val size: Int get() = set.rules.size
    val updated: String get() = set.updated

    /** The most specific rule for [pkg], or null. */
    fun ruleFor(pkg: String, profile: DeviceProfile?): SafetyRule? =
        set.rules.filter { it.pkg == pkg && it.matches(profile) }
            .maxByOrNull { specificity(it.scope) }

    /** Verdict for [pkg], or null when no rule applies (unknown is not safe). */
    fun verdict(pkg: String, profile: DeviceProfile?): SafetyVerdict? =
        ruleFor(pkg, profile)?.let { SafetyVerdict(pkg, it.safety, it.why) }

    fun verdicts(packages: Collection<String>, profile: DeviceProfile?): Map<String, SafetyVerdict> =
        packages.mapNotNull { pkg -> verdict(pkg, profile)?.let { pkg to it } }.toMap()

    private fun specificity(scope: String): Int = when {
        scope.startsWith("MODEL:", true) -> 3
        scope.count { it == ':' } >= 2 -> 2 // ROM:family:version
        scope.startsWith("BRAND:", true) || scope.startsWith("ROM:", true) -> 1
        else -> 0
    }

    companion object {
        /** Path of the bundled rule set. */
        const val RESOURCE_PATH = "files/safety_rules.json"

        /** The shipped rules; empty (everything unknown) if the resource cannot be read. */
        suspend fun bundled(): SafetyRules = runCatching {
            SafetyRules(Res.readBytes(RESOURCE_PATH).decodeToString())
        }.getOrDefault(SafetyRules(SafetyRuleSet()))
    }
}
