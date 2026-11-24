package io.github.samolego.canta.data.community

import kotlinx.serialization.Serializable

/**
 * One package as the community voted on it *for this model and ROM*.
 *
 * @param rank 1-based position: most liked package first.
 * @param votes the likes of every plan that contains the package.
 * @param plans how many plans contain it.
 */
@Serializable
data class PackageRank(
    val pkg: String,
    val rank: Int,
    val votes: Int,
    val plans: Int,
) {
    /** The top three get a painted badge; everyone else a plain number. */
    val podium: Boolean get() = rank <= 3
}

/** Indexes a ranking by package name, which is how every UI row looks a package up. */
fun List<PackageRank>.keyed(): Map<String, PackageRank> = associateBy { it.pkg }

/**
 * Builds the per-device ranking out of the plans that are already filtered to this phone.
 *
 * Votes come from the plans themselves, so a package nobody put in a plan does not appear at all;
 * ties go to the package that shows up in more plans, then alphabetically, so the order is stable
 * between reloads.
 */
fun rankPackages(plans: List<CommunityPlan>): List<PackageRank> {
    val votes = linkedMapOf<String, Int>()
    val counts = mutableMapOf<String, Int>()
    plans.forEach { plan ->
        plan.packages.forEach { pkg ->
            votes[pkg] = (votes[pkg] ?: 0) + plan.votes
            counts[pkg] = (counts[pkg] ?: 0) + 1
        }
    }
    return votes.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenByDescending { counts[it.key] ?: 0 }
                .thenBy { it.key }
        )
        .mapIndexed { index, entry ->
            PackageRank(
                pkg = entry.key,
                rank = index + 1,
                votes = entry.value,
                plans = counts[entry.key] ?: 0,
            )
        }
}
