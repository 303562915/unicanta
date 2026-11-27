package io.github.samolego.canta.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import java.util.Locale

/** 进程启动时的语言；选「跟随系统」回到它。理由见 Android 那一份同样的一格。 */
private var systemLocale: Locale? = null

/**
 * 桌面侧的语言切换：换 JVM 的默认 `Locale`，再按语言重起一遍内容。
 *
 * 桌面版的资源按 `Locale.getDefault()` 挑 `values-*`，所以换掉它之后每一句 `stringResource`
 * 都会按新语言解；已经画好的字同样要靠 [key] 重起才会变。
 */
@Composable
actual fun AppLocaleOverride(locale: String?, content: @Composable () -> Unit) {
    if (systemLocale == null) systemLocale = Locale.getDefault()
    val target = if (locale.isNullOrBlank()) systemLocale!! else Locale.forLanguageTag(locale)
    if (Locale.getDefault() != target) Locale.setDefault(target)
    key(locale) { content() }
}
