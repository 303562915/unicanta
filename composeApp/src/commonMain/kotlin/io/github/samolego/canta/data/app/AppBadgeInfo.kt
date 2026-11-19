package io.github.samolego.canta.data.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.samolego.canta.data.bloat.BloatData
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.badge_advanced_code
import io.github.samolego.canta.generated.resources.badge_advanced_description
import io.github.samolego.canta.generated.resources.badge_advanced_name
import io.github.samolego.canta.generated.resources.badge_expert_code
import io.github.samolego.canta.generated.resources.badge_expert_description
import io.github.samolego.canta.generated.resources.badge_expert_name
import io.github.samolego.canta.generated.resources.badge_recommended_code
import io.github.samolego.canta.generated.resources.badge_recommended_description
import io.github.samolego.canta.generated.resources.badge_recommended_name
import io.github.samolego.canta.generated.resources.badge_system_code
import io.github.samolego.canta.generated.resources.badge_system_description
import io.github.samolego.canta.generated.resources.badge_system_name
import io.github.samolego.canta.generated.resources.badge_unsafe_code
import io.github.samolego.canta.generated.resources.badge_unsafe_description
import io.github.samolego.canta.generated.resources.badge_unsafe_name
import org.jetbrains.compose.resources.StringResource

/**
 * Badge info for an app, sourced from the UAD list ([BloatData.badgeInfo])
 * or local fallbacks. [SYSTEM] is a local badge (not a UAD removal level)
 * and the base for future badges.
 *
 * The wording lives in the string resources rather than in the constructor: a badge used to
 * print the English it was built with, so a Chinese list showed "rec" and a paragraph of
 * English next to a translated page. Every device prints its own language now.
 */
enum class AppBadgeInfo(
    val icon: ImageVector,
    val badgeColor: Color,
) {
    RECOMMENDED(Icons.Default.Check, Color.Green),
    ADVANCED(Icons.Default.Settings, Color.Yellow),
    EXPERT(Icons.Default.Warning, Color.Red),
    UNSAFE(Icons.Default.Close, Color.Magenta),
    SYSTEM(Icons.Default.Android, Color.DarkGray);

    /** What the badge prints: a row has no room for the whole word. */
    val code: StringResource
        get() = when (this) {
            RECOMMENDED -> Res.string.badge_recommended_code
            ADVANCED -> Res.string.badge_advanced_code
            EXPERT -> Res.string.badge_expert_code
            UNSAFE -> Res.string.badge_unsafe_code
            SYSTEM -> Res.string.badge_system_code
        }

    /** The badge's own name, the one the filter menu lists. */
    val nameRes: StringResource
        get() = when (this) {
            RECOMMENDED -> Res.string.badge_recommended_name
            ADVANCED -> Res.string.badge_advanced_name
            EXPERT -> Res.string.badge_expert_name
            UNSAFE -> Res.string.badge_unsafe_name
            SYSTEM -> Res.string.badge_system_name
        }

    /** The explanation the badge dialog and the app page print under the badge. */
    val description: StringResource
        get() = when (this) {
            RECOMMENDED -> Res.string.badge_recommended_description
            ADVANCED -> Res.string.badge_advanced_description
            EXPERT -> Res.string.badge_expert_description
            UNSAFE -> Res.string.badge_unsafe_description
            SYSTEM -> Res.string.badge_system_description
        }

    companion object {
        /** The UAD list carries the enum name, so this stays keyed on it, not on the wording. */
        fun byNameIgnoreCaseOrNull(input: String): AppBadgeInfo? {
            return entries.firstOrNull { it.name.equals(input, true) }
        }
    }
}
