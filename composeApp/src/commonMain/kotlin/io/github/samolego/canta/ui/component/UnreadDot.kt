package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
 * It has no text of its own — where the size of the news is a number, [UnreadBadge] is the one to
 * draw — and no click target: the row or the button behind it is what opens the page, so the dot
 * only says "here".
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

/** The height of a numbered marker: a single digit makes it a circle, more digits a pill. */
private val BadgeHeight = 16.dp

/** The room a digit keeps on either side of the pill, so "12" is not two letters touching. */
private val BadgeInset = 4.dp

/** Past this the exact number stops being useful, and the pill would cover the button under it. */
private const val BadgeCap = 99

/**
 * The same marker with the count on it: the red pill a chat app puts on a messages entry, saying
 * *how many* are waiting rather than only *that* something is.
 *
 * It is used where the number is known (the inbox's own count, the votes that arrived) and the
 * plain [UnreadDot] stays for "something changed, the size of the change is not a number".
 * [count] of 0 or less draws nothing, so callers can hand it a running total without a guard.
 */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier, height: Dp = BadgeHeight) {
    if (count <= 0) {
        return
    }
    val text = if (count > BadgeCap) "$BadgeCap+" else count.toString()
    Box(
        modifier = modifier
            .heightIn(min = height)
            .widthIn(min = height)
            .clip(RoundedCornerShape(percent = 50))
            .background(UnreadRed)
            .padding(horizontal = BadgeInset),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            // White on the marker's red in both themes, like the dot: the pill is a colour of its
            // own, not a toned piece of the page.
            color = Color.White,
            style = TextStyle(
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}
