package io.github.samolego.canta.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The grey under a comment box or a search field.
 *
 * Not the theme's tinted container: a box you type into is a sheet of paper, and the container
 * colour carries the accent, which made the field read as a disabled control. This is a neutral
 * grey — the one a short-video composer puts under its comment box — and it stays neutral in the
 * dark theme too, where it is a lifted charcoal rather than a purple-tinted one.
 */
@Composable
fun inputFieldColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF2B2B2E) else Color(0xFFF1F2F4)