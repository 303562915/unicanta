package io.github.samolego.canta.ui

import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.tab_disabled
import io.github.samolego.canta.generated.resources.tab_installed
import io.github.samolego.canta.generated.resources.tab_records
import io.github.samolego.canta.generated.resources.tab_uninstalled
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphs
import org.jetbrains.compose.resources.StringResource

/**
 * The app lists: installed apps (to uninstall), uninstalled ones (to reinstall) and frozen ones
 * (to unfreeze).
 */
enum class AppsType(val glyph: FlGlyph, val label: StringResource) {
    INSTALLED(FlGlyphs.resources, Res.string.tab_installed),
    UNINSTALLED(FlGlyphs.profiles, Res.string.tab_uninstalled),
    DISABLED(FlGlyphs.block, Res.string.tab_disabled);

    /**
     * Whether [app] belongs on this list. The three lists partition the apps: a frozen app is not
     * on the installed one (its action there is removal, which is not what is wanted for it), and
     * an app removed for the user stays on the uninstalled list even if its frozen flag is still
     * set, because that is the list whose action brings it back.
     */
    fun matches(app: AppInfo): Boolean = when (this) {
        INSTALLED -> !app.isUninstalled && !app.isDisabled
        UNINSTALLED -> app.isUninstalled
        DISABLED -> app.isDisabled && !app.isUninstalled
    }
}

/** What the action on this tab records: an uninstall, a reinstall, an unfreeze. */
val AppsType.operationKind: OperationKind
    get() = when (this) {
        AppsType.INSTALLED -> OperationKind.UNINSTALL
        AppsType.UNINSTALLED -> OperationKind.REINSTALL
        AppsType.DISABLED -> OperationKind.ENABLE
    }

/**
 * The pages of the app list, in the order they are swiped through: what is installed, the record
 * of what Canta did to it, what is uninstalled, and what is frozen.
 *
 * The records page is the middle one because it explains the others: an app leaves the first
 * list, is written down here, and appears in one of the two below it.
 */
enum class AppsTab(
    val glyph: FlGlyph,
    val label: StringResource,
    /** The list this page shows, or null for the records page, which is not a list of apps. */
    val appsType: AppsType?,
) {
    INSTALLED(FlGlyphs.resources, Res.string.tab_installed, AppsType.INSTALLED),
    RECORDS(FlGlyphs.requests, Res.string.tab_records, null),
    UNINSTALLED(FlGlyphs.profiles, Res.string.tab_uninstalled, AppsType.UNINSTALLED),
    // Last, so the three lists that came before keep their page indexes (a preset applied from
    // another page falls back to the first one).
    DISABLED(FlGlyphs.block, Res.string.tab_disabled, AppsType.DISABLED),
}
