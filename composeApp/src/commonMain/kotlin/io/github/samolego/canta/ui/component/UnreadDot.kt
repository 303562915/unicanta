package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The red of an unread marker, and of the "作者" badge: the one colour in the app that means
 * "somebody did something you have not looked at yet", borrowed from the short-video apps these
 * pages are laid out like. It is the same in both themes — a theme-toned dot reads as decoration.
 */
val UnreadRed: Color = Color(0xFFFE2C55)

/**
 * The dot that says something new is waiting: an inbox with unread replies, a row that is a reply
 * this install has not opened.
 *
 * It has no text of its own — a number would need dismissing — and no click target: the row or the
 * button behind it is what opens the page, so the dot only says "here".
 */
@Composable
fun UnreadDot(modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(UnreadRed),
    )
}
