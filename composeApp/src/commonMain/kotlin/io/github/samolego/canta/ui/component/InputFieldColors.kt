package io.github.samolego.canta.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The fill under a comment box or a search field: plain white, not a tint.
 *
 * A box you type into is a sheet of paper on the page. The theme's container colours carry the
 * accent, which is what made the field read as a pale blue control instead of as paper — so this
 * stays neutral in both themes, and a hairline beside it is what gives it an edge on a light page.
 */
@Composable
fun inputFieldColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF2A2A2D) else Color(0xFFFFFFFF)

/** The hairline that keeps a white field from melting into a light page. */
@Composable
fun inputFieldBorderColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF3A3A3D) else Color(0xFFE6E7E9)