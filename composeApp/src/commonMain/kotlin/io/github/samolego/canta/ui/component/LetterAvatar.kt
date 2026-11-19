package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A text avatar: the first character of an id or a name, on a filled circle. The app has no
 * profile pictures, so every review, comment and profile row shows this instead of an image.
 *
 * The letter comes from [avatarLetter]: the first character of [name], so a review signed
 * "#10000" and one signed "Alice" both get a one-character avatar.
 */
@Composable
fun LetterAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    background: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatarLetter(name),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** The first character of [name], kept whole when it is a surrogate pair; "?" when empty. */
fun avatarLetter(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val first = trimmed[0]
    return if (first.isHighSurrogate() && trimmed.length > 1) trimmed.substring(0, 2) else first.toString()
}
