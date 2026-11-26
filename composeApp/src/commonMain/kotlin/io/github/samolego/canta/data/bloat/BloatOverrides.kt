package io.github.samolego.canta.data.bloat

import io.github.samolego.canta.data.app.AppBadgeInfo
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.bloat_description_language
import io.github.samolego.canta.util.LogUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString

/**
 * One correction to what the UAD list says about a package.
 *
 * [removal] replaces the list's level and [notes] is appended to its description. Both are
 * optional: an entry with only a note fixes the wording, an entry with only a level fixes the
 * badge.
 */
@Serializable
data class BloatOverrideEntry(
    /** Same vocabulary as the list's `removal`; blank means "keep the list's level". */
    val removal: String = "",
    /** Extra sentence per interface language (`en` is the fallback). */
    val notes: Map<String, String> = emptyMap(),
    /** 谁定下这条：`maintainer` = 我们自己写的（随包发），`community` = 用户提、工作流合并的。 */
    val source: String = "",
    /** 用户提案所在的那张 issue 的编号；我们自己写的那几条是 0。 */
    val issue: Int = 0,
)

/**
 * One package's entry in the corrections table, as this project publishes it in
 * `site/bloat/level_fixes.json` (and ships the same shape in `files/bloat_overrides.json`).
 *
 * 为什么带着 [source] / [issue]：这两个字段让「这条是谁改的、依据是什么」跟着文件走。一类是
 * 我们自己写下的（[source] = `maintainer`），一类是用户从应用里提、工作流核对合并的
 * （[source] = `community`，[issue] 指向那张提案）。
 */
@Serializable
data class BloatOverrideTable(
    /** Free-form explanation for whoever edits the file; never read by the app. */
    val about: String = "",
    val version: Int = 1,
    val updated: String = "",
    /** Package name -> the correction. */
    val packages: Map<String, BloatOverrideEntry> = emptyMap(),
)

/**
 * The corrections this build ships for the bloat list.
 *
 * The list is the UAD community's, graded **per package name**: it cannot know that a vendor ROM
 * gave a package an extra job, so a level that is right on AOSP can be wrong on one phone — the
 * Honor `com.qualcomm.location` case, listed as "Recommended" while being the emergency-call
 * locator. Editing the downloaded list is not an option (it is replaced on the next refresh) and
 * forking the list is worse; this table is the smallest thing that stays under our control.
 *
 * 两张同样的表：随包发的 `files/bloat_overrides.json`（我们写完要等下一个版本），以及仓库里
 * `site/bloat/level_fixes.json`（改一行、推上去，一周内所有手机都拿到，不用发版）。后者叠在前者
 * 上面，见 [overriddenBy]。
 *
 * Everything here degrades to "no correction": a missing or malformed file leaves the upstream
 * list exactly as it is, which is the behaviour every earlier build had.
 */
class BloatOverrides(
    private val table: BloatOverrideTable,
    /** Interface language, matching [BloatDescriptionTable.language] conventions ("zh", "en"). */
    val language: String = "en",
) {

    /** How many packages this build corrects; 0 means "the list is taken as published". */
    val size: Int get() = table.packages.size

    /**
     * [this] with [remote] laid over it: a package both sides correct takes the remote entry.
     *
     * The database row is the newer of the two — it is the one an operator can change today, while
     * the bundled table only moves with a release — so it wins wherever they disagree. The language
     * of the result stays this table's, because the notes are picked at [apply] time either way.
     */
    fun overriddenBy(remote: BloatOverrides): BloatOverrides {
        if (remote.size == 0) return this
        return BloatOverrides(table.copy(packages = table.packages + remote.table.packages), language)
    }

    /**
     * [data] as this table wants it. Returns [data] itself when there is no correction, so callers
     * can tell "unchanged" by reference and hand back the original map.
     */
    fun apply(pkg: String, data: BloatData): BloatData {
        val entry = table.packages[pkg] ?: return data
        val badge = if (entry.removal.isBlank()) {
            data.badgeInfo
        } else {
            // An unreadable level is a typo in the file, not a reason to drop the badge: keep the
            // list's level and let the note (if any) still land.
            AppBadgeInfo.byNameIgnoreCaseOrNull(entry.removal) ?: data.badgeInfo
        }
        val note = entry.notes[language]?.takeIf { it.isNotBlank() }
            ?: entry.notes["en"]?.takeIf { it.isNotBlank() }
        if (note == null) {
            return if (badge == data.badgeInfo) data else data.copy(badgeInfo = badge)
        }
        val description = if (data.description.isNullOrBlank()) note else "${data.description}\n\n$note"
        // The note is our wording, not the list's: the app page has to stop calling the whole
        // description "the upstream text" once one is appended.
        return data.copy(description = description, badgeInfo = badge, translated = true)
    }

    companion object {
        /** Path of the corrections under `composeResources`. */
        const val RESOURCE_PATH = "files/bloat_overrides.json"

        /** No corrections: the list is shown exactly as published. */
        fun empty(): BloatOverrides = BloatOverrides(BloatOverrideTable())

        /**
         * The corrections as **we publish them in `site/bloat/level_fixes.json`**, or [empty] when
         * the payload is unreadable.
         *
         * Same shape as the bundled `files/bloat_overrides.json`, deliberately: one format means the
         * repository copy can be laid straight over the shipped one ([overriddenBy]), and a maintainer
         * moving an entry between them never has to translate it.
         *
         * Nothing here is fatal: a missing file, a build with no route to GitHub, or a payload someone
         * broke leaves the shipped table in charge — which is exactly what earlier builds did.
         */
        fun fromRemote(json: String, language: String = "en"): BloatOverrides = runCatching {
            val table = Json { ignoreUnknownKeys = true }
                .decodeFromString(BloatOverrideTable.serializer(), json)
            BloatOverrides(table.copy(packages = table.packages.filterValues { it.removal.isNotBlank() || it.notes.isNotEmpty() }), language)
        }.onFailure {
            LogUtils.w("BloatOverrides", "Remote corrections are unreadable: $it")
        }.getOrDefault(empty())

        /**
         * The bundled table, or [empty] when it is missing or unreadable.
         *
         * Same language probe as [BloatDescriptions.bundled]: Compose Resources already picked the
         * interface language, so the notes follow it instead of a platform locale that may differ.
         */
        suspend fun bundled(): BloatOverrides = runCatching {
            val language = getString(Res.string.bloat_description_language)
            val table = Json { ignoreUnknownKeys = true }.decodeFromString(
                BloatOverrideTable.serializer(),
                Res.readBytes(RESOURCE_PATH).decodeToString(),
            )
            BloatOverrides(table, language)
        }.onFailure {
            LogUtils.w("BloatOverrides", "Shipped corrections are unreadable: $it")
        }.getOrDefault(empty())
    }
}

/** [this] with every package [overrides] corrects replaced; identical when nothing matches. */
internal fun Map<String, BloatData>.withOverrides(overrides: BloatOverrides): Map<String, BloatData> {
    if (overrides.size == 0) return this
    var replaced = false
    val out = mapValues { (pkg, data) ->
        val patched = overrides.apply(pkg, data)
        if (patched === data) {
            data
        } else {
            replaced = true
            patched
        }
    }
    // Most packages are not in the table: hand back the original map instead of a copy of it.
    return if (replaced) out else this
}
