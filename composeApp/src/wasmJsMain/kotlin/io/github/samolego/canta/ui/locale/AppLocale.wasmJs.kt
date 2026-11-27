package io.github.samolego.canta.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key

/**
 * 网页侧：只按语言重起一遍内容，**不改浏览器的语言**。
 *
 * 网页版的资源语言由 `navigator.languages` 决定，JS 里换掉它需要在 `index.html` 里先铺一段
 * 改写 `Navigator.prototype.languages` 的脚本（JetBrains 文档里的临时方案），而那个属性是
 * 只读的、铺法又依赖具体宿主页面。网页版是文档站/预览用的那一份，不为了它给整页加这种钩子：
 * 这里的选择会存下来，等这一份在 Android / 桌面上跑时生效。
 */
@Composable
actual fun AppLocaleOverride(locale: String?, content: @Composable () -> Unit) {
    key(locale) { content() }
}
