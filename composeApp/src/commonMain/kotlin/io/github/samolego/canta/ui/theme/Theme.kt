package io.github.samolego.canta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import io.github.samolego.canta.ui.fl.FlAccent
import io.github.samolego.canta.ui.fl.FlTheme
import io.github.samolego.canta.ui.locale.AppLocaleOverride

/**
 * Canta's theme. The palette, type scale and shapes are FlClash's, seeded with its
 * default accent `#007AFF`: the app looks the same on every phone rather than tinting
 * itself after the wallpaper. [accent] is the color picked in the settings, which only
 * re-derives the accent roles and leaves the surfaces where FlClash put them.
 *
 * [appLocale] 是设置里手动挑的界面语言（`zh-CN`、`en`…），`null` 表示跟随系统；见
 * [io.github.samolego.canta.ui.locale.AppLocale]。它顺手挂在这里，而不是在 `CantaApp` 里再包一层：
 * 换语言要把整页按新语言重起一遍，而这一层本来就是"整页"的入口，接线只有一处。
 */
@Composable
fun CantaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: FlAccent = FlAccent.Default,
    appLocale: String? = null,
    content: @Composable () -> Unit
) {
    AppLocaleOverride(appLocale) {
        FlTheme(darkTheme = darkTheme, accent = accent, content = content)
    }
}
