package io.github.samolego.canta.ui

import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.tab_installed
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
