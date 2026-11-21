package io.github.samolego.canta.data.recommend

import io.github.samolego.canta.generated.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One open-source app worth installing, as shown on the recommendations page: a name, who makes
 * it, one sentence on why it is worth the space, and the page a tap opens in the browser.
 *
 * @param category one of [Recommendations.CATEGORIES]; an app filed under anything else is dropped
 *   rather than shown under a made-up heading.
 * @param mark the one or two characters drawn in the icon tile, since the catalog carries no
 *   images: `星`, `S`, `AM`. Falls back to the first character of [name].
 * @param accent `#RRGGBB` of the icon tile, so each card has a colour of its own.
 * @param featured whether it is pinned above the categories instead of sitting in one.
 */
@Serializable
data class RecommendedApp(
    val id: String,
    val name: String,
    val author: String = "",
    val category: String,
    val url: String,
    val summary: String = "",
    val license: String = "",
    val mark: String = "",
    val accent: String = "",
    val featured: Boolean = false,
    val replacesPackages: List<String> = emptyList(),
) {
    /** What to draw in the tile: the catalog's mark, or the first character of the name. */
    val tileMark: String
        get() = mark.ifBlank { name.trim().take(1) }.ifBlank { "?" }

    /**
     * Whether [needle] (already trimmed, not lower-cased) is found in anything this entry says
     * about itself. Package names are searched as well as prose: pasting a package from a debloat
     * list is a common way to arrive at this page.
     */
    fun matches(needle: String): Boolean {
        val lower = needle.lowercase()
        fun has(value: String) = value.lowercase().contains(lower)
        return has(name) || has(author) || has(summary) || has(license) ||
            replacesPackages.any(::has) || has(category)
    }

    /**
     * Which of [replacesPackages] this phone still has, in the order the entry lists them.
     *
     * "Install this and you can drop those" is only an instruction while those packages are still
     * installed: once they are gone there is no next step left to point at, and the page keeps the
     * entry quiet. The match is exact — package names are lower-case and case-sensitive — because a
     * near miss here would send the reader to the detail page of an app that is not there.
     */
    fun installedReplacements(installed: Set<String>): List<String> =
        replacesPackages.filter { it in installed }
}

@Serializable
data class RecommendedAppSet(
    val version: Int = 1,
    val updated: String = "",
    val apps: List<RecommendedApp> = emptyList(),
)

/**
 * The shipped recommendations, pinned entries first and then one group per category in
 * [CATEGORIES] order, so a reader looking for a file manager is not walked past six video players.
 */
class Recommendations(private val set: RecommendedAppSet) {

    constructor(json: String) : this(
        Json { ignoreUnknownKeys = true }.decodeFromString(RecommendedAppSet.serializer(), json)
    )

    /** Every app of the catalog, in the order the file lists them. */
    val apps: List<RecommendedApp> get() = set.apps

    /** The pinned apps, in file order; they are not repeated inside their category. */
    val featured: List<RecommendedApp> get() = set.apps.filter { it.featured }

    /** The catalog's apps grouped by category, in [CATEGORIES] order; empty groups are left out. */
    fun grouped(): List<Pair<String, List<RecommendedApp>>> {
        val byCategory = set.apps.filterNot { it.featured }.groupBy { it.category }
        return CATEGORIES.mapNotNull { category ->
            byCategory[category]?.let { category to it }
        }
    }

    /**
     * What a search box keeps: the entries whose name, author, summary or replaced package names
     * contain [query], case-insensitively.
     *
     * The package names are searched too, because that is what someone pasting "com.miui.analytics"
     * from a debloat list is actually looking for — the app that replaces it.
     */
    fun search(query: String): List<RecommendedApp> {
        val needle = query.trim()
        if (needle.isEmpty()) return set.apps
        return set.apps.filter { it.matches(needle) }
    }

    companion object {
        const val RESOURCE_PATH = "files/recommended_apps.json"

        /** The category groups, in display order. */
        val CATEGORIES = listOf("system", "store", "privacy", "files", "media", "tools")

        /** The bundled catalog; an unreadable file leaves the page empty rather than crashing. */
        suspend fun bundled(): Recommendations = runCatching {
            Recommendations(Res.readBytes(RESOURCE_PATH).decodeToString())
        }.getOrDefault(Recommendations(RecommendedAppSet()))
    }
}
