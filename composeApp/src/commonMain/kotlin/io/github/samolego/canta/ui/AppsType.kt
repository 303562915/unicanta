package io.github.samolego.canta.ui

import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.tab_installed
import io.github.samolego.canta.generated.resources.tab_records
import io.github.samolego.canta.generated.resources.tab_uninstalled
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphs
import org.jetbrains.compose.resources.StringResource

/** The two app tabs: installed apps (to uninstall) and uninstalled ones (to reinstall). */
enum class AppsType(val glyph: FlGlyph, val label: StringResource) {
    INSTALLED(FlGlyphs.resources, Res.string.tab_installed),
    UNINSTALLED(FlGlyphs.profiles, Res.string.tab_uninstalled);

    fun matches(app: AppInfo): Boolean = when (this) {
        INSTALLED -> !app.isUninstalled
        UNINSTALLED -> app.isUninstalled
    }
}

/** What the action on this tab records: an uninstall, or a reinstall. */
val AppsType.operationKind: OperationKind
    get() = when (this) {
        AppsType.INSTALLED -> OperationKind.UNINSTALL
        AppsType.UNINSTALLED -> OperationKind.REINSTALL
    }

/**
 * The pages of the app list, in the order they are swiped through: what is installed, the record
 * of what Canta did to it, and what is uninstalled.
 *
 * The records page is the middle one because it explains the other two: an app leaves the first
 * list, is written down here, and appears in the third.
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
}
