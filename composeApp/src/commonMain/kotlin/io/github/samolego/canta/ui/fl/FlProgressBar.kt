package io.github.samolego.canta.ui.fl

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** The bar is a hairline: it marks "something is moving" without becoming a section of its own. */
private val BarHeight = 3.dp

/** Rounded to half its height, so the moving and the resting half of the bar end the same way. */
private val BarShape = RoundedCornerShape(percent = 50)

/**
 * The progress bar the "about" page's update card draws, as a shared piece.
 *
 * [progress] is 0f..1f once there is something to measure; `null` means the length is unknown and
 * the indeterminate sweep runs instead. Both callers must look the same, because the list waiting
 * for its data and the phone waiting for a new build are the same question to the person holding it
 * — the app-list loaders used to answer it with a spinner, which reads as a stuck clock next to this.
 */
@Composable
fun FlProgressBar(
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val bar = modifier
        .fillMaxWidth()
        .height(BarHeight)
        .clip(BarShape)
    val color = scheme.primary
    val track = scheme.primary.copy(alpha = 0.18f)
    if (progress == null) {
        LinearProgressIndicator(modifier = bar, color = color, trackColor = track)
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = bar,
            color = color,
            trackColor = track,
        )
    }
}
