package io.github.samolego.canta.data.community

import io.github.samolego.canta.util.romLabelOf
import kotlinx.serialization.Serializable

/**
 * One build the community has plans for: a model plus the ROM build those plans were made on.
 *
 * The counts come straight from the backend's `plan_catalog`. [plans]/[people] belong to this
 * build; [modelPlans]/[modelPeople] to the whole model, which is what its chip shows — somebody who
 * registered on the model but never published anything still joined it.
 *
 * [romBuild] is the ROM's own string ("16.0.3"): one model ships several builds a year, and the
 * package set that fits one does not have to fit the next. Rows uploaded before builds were stored
 * carry "", and are then pooled under the major version alone.
 */
data class CatalogBuild(
    val brand: String,
    val model: String,
    val marketName: String = "",
    val rom: String = "",
    val romVersion: Int = 0,
    val romBuild: String = "",
    val plans: Int = 0,
    val people: Int = 0,
    val modelPlans: Int = plans,
    val modelPeople: Int = people,
) {
    /** The build as people name it: "HyperOS 16.0.3", or "Android" on a stock ROM. */
    val romLabel: String get() = romLabelOf(rom, romVersion, romBuild)

    /** "全部版本" for the model-wide pseudo build; the version otherwise. */
    val versionLabel: String get() = romLabel

    /** Identity of this chip, so two builds of one model never collapse into one selection. */
    val key: String get() = "$rom/$romVersion/$romBuild"
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
                // Newest build first: "16.0.501" above "16.0.3", and the rows that carry no build
                // (uploaded before builds were stored) settle at the bottom of the model.
                compareByDescending<CatalogBuild> { it.romVersion }
                    .thenByDescending { it.romBuild }
                    .thenBy { it.rom }
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

/** One heading of the model list: a family and the models in it, biggest number first. */
data class FamilyGroup(
    val family: DeviceFamily,
    val models: List<CatalogModel>,
)

/**
 * The lines inside a family, in the order a picker shows them: the parent brand first, its
 * sub-brands after it. "小米系" is Xiaomi, then Redmi, then POCO; "vivo 系" is vivo, then iQOO.
 *
 * A brand that is not named here sorts after every named one, so an unknown maker never pushes a
 * flagship line out of the top of the list.
 */
private val SUB_BRAND_LINES = listOf(
    listOf("xiaomi", "小米"),
    listOf("redmi", "红米"),
    listOf("poco"),
    listOf("vivo"),
    listOf("iqoo"),
    listOf("oppo"),
    listOf("oneplus", "one plus", "一加"),
    listOf("realme"),
    listOf("honor", "荣耀"),
    listOf("huawei", "华为"),
    listOf("samsung", "三星", "galaxy"),
    listOf("meizu", "魅族"),
    listOf("nubia", "redmagic", "red magic", "努比亚", "红魔"),
    listOf("motorola", "moto"),
    listOf("google", "pixel"),
)

/**
 * Which line of [SUB_BRAND_LINES] a model belongs to.
 *
 * The marketing name is read first: a Redmi uploads with `ro.product.manufacturer` = "Xiaomi", so
 * the brand alone would put every Redmi in the parent line. The brand is only the fallback for a
 * model that has no marketing name and shows its retail id.
 */
fun subBrandRankOf(brand: String, label: String): Int {
    val fromName = SUB_BRAND_LINES.indexOfFirst { line -> line.any { it in label.lowercase() } }
    if (fromName >= 0) return fromName
    val fromBrand = SUB_BRAND_LINES.indexOfFirst { line -> line.any { it in brand.lowercase() } }
    return if (fromBrand >= 0) fromBrand else SUB_BRAND_LINES.size
}

private val DIGITS = Regex("""\d+""")

/**
 * The generation in a model's name: "小米 18 Pro Max" -> 18, "REDMI K100 Pro Max" -> 100,
 * "Galaxy S25 Ultra" -> 25.
 *
 * A retail model number is not a generation: "PHK110", "SM-F9710", "V2337A" are pure ASCII with no
 * space in them, and read as 0 so they land after the phones whose name says how new they are.
 * "K100" on its own would be read the same way, which is why the picker's order relies on the
 * marketing name ("REDMI K100 Pro Max") rather than on a bare code.
 */
fun modelNumber(label: String): Int {
    val text = label.trim()
    if (text.isEmpty()) return 0
    if (text.none { it == ' ' } && text.all { it.code < 128 }) return 0
    return DIGITS.findAll(text).mapNotNull { it.value.toIntOrNull() }.maxOrNull() ?: 0
}

private val SE_WORD = Regex("""\bse\b""")
private val LITE_WORD = Regex("""\blite\b""")

/**
 * How far up a name sits within its own generation: "18 Pro Max" over "18 Pro" over "18", and any
 * of them over the "SE"/"青春版" of the same number.
 */
fun modelVariantRank(label: String): Int {
    val text = label.lowercase()
    val budget = SE_WORD.containsMatchIn(text) || LITE_WORD.containsMatchIn(text) ||
        "青春" in text || "活力" in text
    return when {
        "ultra" in text -> 5
        "max" in text -> 4
        "pro+" in text || "pro plus" in text -> 4
        "pro" in text -> 3
        "plus" in text || '+' in text || "至尊" in text -> 2
        budget -> 0
        else -> 1
    }
}

/**
 * The order the picker lists models in: the parent brand's line first, then each sub-brand, and
 * inside every line the biggest number first — "小米 18 Pro Max", "小米 18", "小米 17", … then
 * "REDMI K100 Pro Max" downwards. Ties go to the stronger variant, then to the name.
 *
 * People counts do not take part: the list is a line-up, and a well-known phone is not a newer one.
 */
fun List<CatalogModel>.sortedForPicker(): List<CatalogModel> = sortedWith(
    compareBy<CatalogModel> { subBrandRankOf(it.brand, it.label) }
        .thenByDescending { modelNumber(it.label) }
        .thenByDescending { modelVariantRank(it.label) }
        .thenBy { it.label }
)

/**
 * Groups models under their family headings, in a fixed order (小米系 first, "其他机型" last), and
 * ordered inside a heading by [sortedForPicker] — flagship line first, biggest number first.
 */
fun List<CatalogModel>.groupedByFamily(): List<FamilyGroup> {
    val groups = sortedForPicker().groupBy { familyOf(it.brand, it.model) }
    return groups.entries
        .sortedBy { it.key.order }
        .map { FamilyGroup(it.key, it.value) }
}

/**
 * How many people it takes before a count is worth printing at all.
 *
 * Under 50 the number reads as "almost nobody", which is worse than saying nothing: the chip is
 * there to point at a model the community actually uses, and a small count argues against itself.
 * One constant, so the chip suffix and any sentence carrying the same number agree.
 */
const val PEOPLE_COUNT_THRESHOLD = 50

/**
 * The count shown next to a model or build, or "" when there is nothing worth saying.
 *
 * A handful of people is not a community yet, and a number under every chip would read as one: only
 * more than [threshold] people are worth printing.
 */
fun peopleSuffix(people: Int, threshold: Int = PEOPLE_COUNT_THRESHOLD): String =
    if (people > threshold) " · ${people}人" else ""

/**
 * One phone a plan's package set was reboot-proven on, as `plan_device_coverage` counts it.
 *
 * A plan page names the uploader's device, which answers "who wrote this". This answers the other
 * question — "did anyone with a phone like mine keep it and come back with a working device" — and
 * the page shows the top few as coverage rather than as a single claim.
 *
 * @param verified how many uploads of this same package set, on this same build, passed the check.
 */
@Serializable
data class DeviceCoverage(
    val brand: String,
    val model: String,
    val marketName: String = "",
    val rom: String = "",
    val romVersion: Int = 0,
    /** The ROM's own build string ("16.0.3"); "" on uploads that predate it. */
    val romBuild: String = "",
    val sdk: Int = 0,
    val verified: Int = 0,
) {
    /** "Redmi K70": the marketing name when the backend has one, else the retail model number. */
    val label: String get() = marketName.ifBlank { model }

    /** "Redmi K70 · HyperOS 16.0.3": the same shape the plan's own device line uses. */
    val buildLabel: String
        get() = listOf(label, romLabelOf(rom, romVersion, romBuild))
            .filter { it.isNotBlank() }
            .joinToString(" · ")
}
