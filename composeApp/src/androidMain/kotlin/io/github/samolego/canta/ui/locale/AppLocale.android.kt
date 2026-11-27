package io.github.samolego.canta.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * 进来时系统给的那个语言：选「跟随系统」要回到它，而不是回到"上一个人点过的那个"。
 *
 * 进程级的一份就够：手机的默认语言在一次运行里不会变（真变了，`Locale.getDefault()` 也会跟着换，
 * 而这一格是第一次用到时写下的快照——所以只在第一次记）。
 */
private var systemLocale: Locale? = null

/**
 * Android 侧的语言切换：改 `Resources` 的 Configuration，界面语言随之换掉。
 *
 * 这是 JetBrains 文档给的临时做法（公共 API 还没有），要点三条：
 * 1. `Locale.setDefault` 管那些走 `java.util.Locale` 的格式化（日期、数字、复数）；
 * 2. `resources.updateConfiguration` 管 `stringResource` —— 它最终读的就是这个 Resources；
 * 3. 只改上面两处**不会**让已经画出来的字自己变，所以最后由 [key] 按语言重起一遍内容。
 *
 * 判断"要不要改"用当前 Configuration 里的 locale：一样就不动，免得每次重组都去写一遍全局状态。
 */
@Suppress("DEPRECATION")
@Composable
actual fun AppLocaleOverride(locale: String?, content: @Composable () -> Unit) {
    val resources = LocalContext.current.resources
    if (systemLocale == null) systemLocale = Locale.getDefault()
    val target = if (locale.isNullOrBlank()) systemLocale!! else Locale.forLanguageTag(locale)
    val configuration = resources.configuration
    val current = configuration.locale ?: Locale.getDefault()
    if (current != target) {
        Locale.setDefault(target)
        configuration.setLocale(target)
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }
    key(locale) { content() }
}
