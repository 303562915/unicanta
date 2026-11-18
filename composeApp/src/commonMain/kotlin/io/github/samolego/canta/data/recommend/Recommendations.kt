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
) {
    /** What to draw in the tile: the catalog's mark, or the first character of the name. */
    val tileMark: String
        get() = mark.ifBlank { name.trim().take(1) }.ifBlank { "?" }
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
