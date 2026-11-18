package io.github.samolego.canta.ui.fl

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * FlClash's type scale: Apple's Dynamic Type sizes at their default step, a point to a
 * dp, plus the two steps iOS lacks — a 24 sp page title for an app bar and a 40 sp
 * display figure.
 */
object FlText {
    val display = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.W700)
    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.W700)
    val title1 = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.W700)
    val pageTitle = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.W600)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.W700)
    val title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.W600)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp)
    val subheadline = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val caption1 = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val caption2 = TextStyle(fontSize = 11.sp, lineHeight = 13.sp)

    /** The weight a caption takes when it has to stand out, as the dock's labels do. */
    val emphasized = FontWeight.W600
}

/**
 * Material's type roles, filled from the scale above so a screen that still writes
 * `MaterialTheme.typography.bodyLarge` lands on the same sizes FlClash uses.
 */
val FlTypography = Typography(
    displayLarge = FlText.display,
    displayMedium = FlText.largeTitle,
    displaySmall = FlText.title1,
    headlineLarge = FlText.title2,
    headlineMedium = FlText.title3,
    headlineSmall = FlText.headline,
    titleLarge = FlText.headline,
    titleMedium = FlText.body,
    titleSmall = FlText.subheadline,
    bodyLarge = FlText.body,
    bodyMedium = FlText.subheadline,
    bodySmall = FlText.footnote,
    labelLarge = FlText.callout,
    labelMedium = FlText.caption1,
    labelSmall = FlText.caption2,
)
