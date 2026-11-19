package io.github.samolego.canta.data.community

import io.github.samolego.canta.util.romLabelOf

/**
 * One build the community has plans for: a model plus the ROM version those plans were made on.
 *
 * The counts come straight from the backend's `plan_catalog`. [plans]/[people] belong to this
 * build; [modelPlans]/[modelPeople] to the whole model, which is what its chip shows — somebody who
 * registered on the model but never published anything still joined it.
 */
data class CatalogBuild(
    val brand: String,
    val model: String,
    val marketName: String = "",
    val rom: String = "",
    val romVersion: Int = 0,
    val plans: Int = 0,
    val people: Int = 0,
    val modelPlans: Int = plans,
    val modelPeople: Int = people,
) {
    /** The build as people name it: "HyperOS 2", or "Android" on a stock ROM. */
    val romLabel: String get() = romLabelOf(rom, romVersion)

    /** "全部版本" for the model-wide pseudo build; the version otherwise. */
    val versionLabel: String get() = romLabel
}

/** One model with every build the backend knows, most active first. */
data class CatalogModel(
    val brand: String,
    val model: String,
    val marketName: String = "",
    val builds: List<CatalogBuild> = emptyList(),
) {
    /** "Redmi K70": the marketing name when the backend has one, else the retail model number. */
    val label: String get() = marketName.ifBlank { model }

    /** Plans of the whole model. */
    val plans: Int get() = builds.firstOrNull()?.modelPlans ?: 0

    /** People who joined the model, whether they uploaded or only registered. */
    val people: Int get() = builds.firstOrNull()?.modelPeople ?: 0

    /** How many builds of it have plans ("全部" plus one chip per build). */
    val versions: Int get() = builds.size
}

/**
 * Folds the backend's build rows into models, each with its builds, newest ROM version first.
 *
 * The rows arrive sorted by how many people the model has, so the order of first appearance is
 * already the order worth showing; builds inside a model are ordered by version, because that is
 * how a phone's builds are read.
 */
fun List<CatalogBuild>.toCatalogModels(): List<CatalogModel> {
    val order = mutableListOf<Pair<String, String>>()
    val byModel = linkedMapOf<Pair<String, String>, MutableList<CatalogBuild>>()
    for (build in this) {
        val key = build.brand.lowercase() to build.model.lowercase()
        val list = byModel.getOrPut(key) {
            order += key
            mutableListOf()
        }
        list += build
    }
    return order.mapNotNull { key ->
        val builds = byModel[key] ?: return@mapNotNull null
        val first = builds.first()
        CatalogModel(
            brand = first.brand,
            model = first.model,
            // Any build carries the model's marketing name; the newest one wins.
            marketName = builds.firstOrNull { it.marketName.isNotBlank() }?.marketName.orEmpty(),
            builds = builds.sortedWith(
                compareByDescending<CatalogBuild> { it.romVersion }.thenBy { it.rom }
            ),
        )
    }
}

/**
 * The family a phone belongs to, as the chip list groups it: one heading per maker's line-up
 * ("小米系" covers Xiaomi, Redmi and POCO), because a list of sixty models is unreadable without
 * them and the brands mean more to people than the manufacturer string does.
 *
 * Matching is on the brand first and the model only as a fallback: the backend stores whatever
 * `ro.product.manufacturer` said, which is "Xiaomi" for Redmi, "OPPO" for OnePlus, and often just
 * "unknown" for a plan uploaded before it was read.
 */
enum class DeviceFamily(val title: String, val order: Int) {
    XIAOMI("小米系", 0),
    HONOR("荣耀", 1),
    OPPO("OPPO 系", 2),
    VIVO("vivo 系", 3),
    HUAWEI("华为", 4),
    SAMSUNG("三星", 5),
    REALME("realme", 6),
    ONE_PLUS("一加", 7),
    MEIZU("魅族", 8),
    NUBIA("努比亚 / 红魔", 9),
    MOTOROLA("摩托罗拉", 10),
    GOOGLE("谷歌", 11),
    OTHER("其他机型", 12),
}

/** The family [brand] (and, when the brand says nothing, [model]) belongs to. */
fun familyOf(brand: String, model: String = ""): DeviceFamily {
    val text = (brand + " " + model).lowercase()
    fun has(vararg words: String) = words.any { text.contains(it) }
    return when {
        has("xiaomi", "redmi", "poco") -> DeviceFamily.XIAOMI
        has("honor") -> DeviceFamily.HONOR
        has("oneplus", "one plus") -> DeviceFamily.ONE_PLUS
        has("realme") -> DeviceFamily.REALME
        has("oppo", "find x", "find n") -> DeviceFamily.OPPO
        has("vivo", "iqoo") -> DeviceFamily.VIVO
        has("huawei") -> DeviceFamily.HUAWEI
        has("samsung") -> DeviceFamily.SAMSUNG
        has("meizu") -> DeviceFamily.MEIZU
        has("nubia", "redmagic", "red magic") -> DeviceFamily.NUBIA
        has("motorola", "moto ") -> DeviceFamily.MOTOROLA
        has("google", "pixel") -> DeviceFamily.GOOGLE
        else -> DeviceFamily.OTHER
    }
}

/** One heading of the model list: a family and the models in it, most joined first. */
data class FamilyGroup(
    val family: DeviceFamily,
    val models: List<CatalogModel>,
)

/**
 * Groups models under their family headings, in a fixed order (小米系 first, "其他机型" last), and
 * sorted inside a heading by how many people joined — the model worth opening is the one at the top.
 */
fun List<CatalogModel>.groupedByFamily(): List<FamilyGroup> {
    val groups = sortedWith(
        compareByDescending<CatalogModel> { it.people }
            .thenByDescending { it.plans }
            .thenBy { it.label }
    ).groupBy { familyOf(it.brand, it.model) }
    return groups.entries
        .sortedBy { it.key.order }
        .map { FamilyGroup(it.key, it.value) }
}

/**
 * The count shown next to a model or build, or "" when there is nothing worth saying.
 *
 * A handful of people is not a community yet, and a number under every chip would read as one: only
 * more than [threshold] people are worth printing.
 */
fun peopleSuffix(people: Int, threshold: Int = 5): String =
    if (people > threshold) " · ${people}人" else ""
