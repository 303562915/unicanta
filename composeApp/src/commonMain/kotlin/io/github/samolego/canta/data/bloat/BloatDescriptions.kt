package io.github.samolego.canta.data.bloat

import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.bloat_description_language
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString

/**
 * The translated wording of the bloat list, shipped with the app.
 *
 * The UAD list is published in English only: every `description` in it is English, so a Chinese
 * page used to show an English paragraph under the package name. This table carries the same
 * descriptions in another language, keyed by package name, and [BloatRepository] swaps it in when
 * the interface language matches [BloatDescriptionTable.language].
 *
 * A package this table does not know keeps the bloat list's own English: a partial translation is
 * still better than none, and a missing key is never a reason to hide the description.
 */
@Serializable
data class BloatDescriptionTable(
    /** Which interface language this table is for: "zh", "ja", ... */
    val language: String = "",
    val version: Int = 1,
    /** When the table was last touched, for the settings page and for spotting a stale file. */
    val updated: String = "",
    /** The bloat list this table was translated from, so a mismatch is traceable. */
    val source: String = "",
    /** Package name -> the description in this table's language. */
    val descriptions: Map<String, String> = emptyMap(),
)

/** Judge for "is this description translated": one table, one language, looked up by package. */
class BloatDescriptions(private val table: BloatDescriptionTable) {

    /** How many packages this table covers; 0 means "use the bloat list as published". */
    val size: Int get() = table.descriptions.size

    val language: String get() = table.language

    /** The translated text for [pkg], or null when this table has nothing better than the original. */
    fun translation(pkg: String): String? = table.descriptions[pkg]?.takeIf { it.isNotBlank() }

    companion object {
        /** Path of the bundled translation; the bloat list itself is downloaded, this is not. */
        const val RESOURCE_PATH = "files/bloat_descriptions.json"

        /** A table with no entries: the bloat list is shown exactly as published. */
        fun empty(): BloatDescriptions = BloatDescriptions(BloatDescriptionTable())

        /**
         * The bundled table, but only when the interface is in the language it was translated for.
         *
         * The probe is a string resource rather than a locale read: Compose Resources already picked
         * the language, and a platform locale would disagree with it wherever the two differ. Any
         * failure (missing resource, unreadable file, malformed JSON) degrades to [empty], which
         * leaves the app on the English it shipped with rather than on a blank description.
         */
        suspend fun bundled(): BloatDescriptions = runCatching {
            val target = getString(Res.string.bloat_description_language)
            val json = Json { ignoreUnknownKeys = true }
            val table = json.decodeFromString(
                BloatDescriptionTable.serializer(),
                Res.readBytes(RESOURCE_PATH).decodeToString(),
            )
            if (table.language.equals(target, ignoreCase = true)) BloatDescriptions(table) else empty()
        }.getOrDefault(empty())
    }
}

/** [this] with every description [l10n] knows replaced; identical when nothing matches. */
internal fun Map<String, BloatData>.localisedWith(l10n: BloatDescriptions): Map<String, BloatData> {
    if (l10n.size == 0) return this
    var replaced = false
    val out = mapValues { (pkg, data) ->
        val translated = l10n.translation(pkg)
        if (translated == null || translated == data.description) {
            data
        } else {
            replaced = true
            data.copy(description = translated, translated = true)
        }
    }
    // Most packages are not in the table: hand back the original map instead of a copy of it.
    return if (replaced) out else this
}
