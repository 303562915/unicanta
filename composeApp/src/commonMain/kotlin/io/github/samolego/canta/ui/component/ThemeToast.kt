package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.ui.fl.FlText

/**
 * Short notices, painted in the theme colour instead of Material's neutral inverse surface.
 *
 * It is a snackbar underneath — one message at a time, queued, dismissed by itself — but the
 * message sits on `primary`, so an update notice reads as part of this app rather than as a system
 * strip. Callers hand it a [SnackbarHostState] and show messages on it the usual way.
 */
@Composable
fun ThemeToastHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = scheme.primary,
            contentColor = scheme.onPrimary,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(horizontal = 6.dp).widthIn(max = 520.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = data.visuals.message,
                    style = FlText.subheadline,
                    color = scheme.onPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}