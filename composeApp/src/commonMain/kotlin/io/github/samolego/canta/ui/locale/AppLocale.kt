package io.github.samolego.canta.ui.locale

import androidx.compose.runtime.Composable

/**
 * 手动指定界面语言。
 *
 * 为什么需要它：Compose Resources 取的是**这台手机报的语言**，而手机报的不一定有人想看——
 * 国产 ROM 里语言列表常常只有「简体中文 / English」，系统语言被识别成英文的机器上，界面就整页
 * 英文，中文用户只能去改系统语言。设置页给一个自己的开关，就不必动系统设置。
 *
 * 做法上没有公共 API（JetBrains 官方文档给的就是每个平台各写一份临时方案），所以这里是
 * `expect`：[locale] 是 BCP-47 语言标签（`zh-CN`、`en`、`ja-JP`…），`null` 表示跟随系统。
 * 每个平台的 actual 在 `content` 之前把语言真的换掉，再按 [locale] 重新起一遍内容，
 * 这样里面的每一句 `stringResource` 都会按新语言解出来（`key()` 是关键：只改系统状态不会让
 * 已经画好的文字自己变）。
 *
 * 注意：网页版的资源语言由浏览器的 `navigator.languages` 决定，JS 里改它要动 `index.html`
 * （见 JetBrains 文档），所以那一侧只重起内容、不改浏览器语言——这个开关在网页版上不生效。
 */
@Composable
expect fun AppLocaleOverride(locale: String?, content: @Composable () -> Unit)

/**
 * 设置页「语言」里的一项。
 *
 * [tag] 是 BCP-47 语言标签，空串表示**跟随系统**（也就是应用的出厂行为）；[name] 用这门语言
 * 自己的写法——找中文的人认得「简体中文」，找日文的人认得「日本語」，翻译成当前界面语言反而更难认。
 */
data class AppLanguageOption(val tag: String, val name: String)

/**
 * 能选的语言清单：只列 `composeResources` 里真有 `values-*` 的那几门。
 *
 * 顺序不是字母序：先放用的人最多的几门（跟随系统、中文两体、英文、日韩），再按字母排剩下的——
 * 四十行一屏放不下，需要滚动，把常见的放前面能少滚几下。清单里的 tag 必须与
 * `composeResources/values-*` 目录对得上，否则选了只会退回默认语言（英文）。
 */
val AppLanguageOptions: List<AppLanguageOption> = listOf(
    AppLanguageOption("", ""),
    AppLanguageOption("zh-CN", "简体中文"),
    AppLanguageOption("zh-TW", "繁體中文"),
    AppLanguageOption("en", "English"),
    AppLanguageOption("ja-JP", "日本語"),
    AppLanguageOption("ko-KR", "한국어"),
    AppLanguageOption("ru-RU", "Русский"),
    AppLanguageOption("de-DE", "Deutsch"),
    AppLanguageOption("fr-FR", "Français"),
    AppLanguageOption("es-ES", "Español"),
    AppLanguageOption("pt-BR", "Português (Brasil)"),
    AppLanguageOption("pt-PT", "Português (Portugal)"),
    AppLanguageOption("it-IT", "Italiano"),
    AppLanguageOption("nl-NL", "Nederlands"),
    AppLanguageOption("pl-PL", "Polski"),
    AppLanguageOption("uk-UA", "Українська"),
    AppLanguageOption("tr-TR", "Türkçe"),
    AppLanguageOption("vi-VN", "Tiếng Việt"),
    AppLanguageOption("hi-IN", "हिन्दी"),
    AppLanguageOption("ar-SA", "العربية"),
    AppLanguageOption("fa", "فارسی"),
    AppLanguageOption("bn-BD", "বাংলা"),
    AppLanguageOption("cs-CZ", "Čeština"),
    AppLanguageOption("sk-SK", "Slovenčina"),
    AppLanguageOption("hu-HU", "Magyar"),
    AppLanguageOption("ro-RO", "Română"),
    AppLanguageOption("el-GR", "Ελληνικά"),
    AppLanguageOption("sv-SE", "Svenska"),
    AppLanguageOption("no-NO", "Norsk"),
    AppLanguageOption("da-DK", "Dansk"),
    AppLanguageOption("fi-FI", "Suomi"),
    AppLanguageOption("hr-HR", "Hrvatski"),
    AppLanguageOption("sl-SI", "Slovenščina"),
    AppLanguageOption("sr", "Српски"),
    AppLanguageOption("lv-LV", "Latviešu"),
    AppLanguageOption("ne-NP", "नेपाली"),
    AppLanguageOption("mn-MN", "Монгол"),
    AppLanguageOption("ks-IN", "کٲشُر"),
    AppLanguageOption("so-SO", "Soomaali"),
    AppLanguageOption("ca-ES", "Català"),
    AppLanguageOption("af-ZA", "Afrikaans"),
    AppLanguageOption("he-IL", "עברית"),
)
