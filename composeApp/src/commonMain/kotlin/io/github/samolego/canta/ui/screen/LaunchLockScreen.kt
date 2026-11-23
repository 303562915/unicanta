package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.ui.component.PIN_LENGTH
import io.github.samolego.canta.ui.component.PinDots
import io.github.samolego.canta.ui.component.PinPad
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import kotlinx.coroutines.delay

/** How long a wrong entry stays on screen before the dots clear themselves. */
private const val WRONG_PIN_PAUSE_MILLIS = 700L

/** Height of everything above and below the keys: icon, title, dots, error line and the gaps. */
private val FixedContent = 241.dp

/** Key sizes between which the grid may shrink to fit a short screen or a large system font. */
private val MinKey = 38.dp
private val MaxKey = 68.dp

/**
 * The gate in front of everything: four dots, a keypad, and the birthday the app was locked with.
 *
 * This exists for the phone that gets handed around — a grandparent tapping at the icon in a
 * pocket, a child swiping through the list. It is a lock against a stray tap rather than against
 * somebody who owns the phone, so it is deliberately plain: no attempt counter, no timeout, no
 * lockout that would leave the owner outside their own app. An entry that comes out wrong just
 * clears the dots and is asked for again.
 *
 * It is drawn as an opaque cover over the pages rather than replacing them, so what was on screen
 * before the app was put down is still there behind the keypad, unchanged.
 *
 * [onUnlock] runs as soon as the fourth digit matches [pin].
 */
@Composable
fun LaunchLockScreen(pin: String, onUnlock: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    // The check lives here rather than inside the tap handler: the fourth digit is what triggers
    // it, and a handler that both stores the value and reacts to it fights the recomposition the
    // store causes.
    LaunchedEffect(typed) {
        if (typed.length < PIN_LENGTH) return@LaunchedEffect
        if (typed == pin) {
            onUnlock()
        } else {
            wrong = true
            delay(WRONG_PIN_PAUSE_MILLIS)
            // The red dots stay only while they carry digits; the line under them stays until the
            // next digit, which is what says why the row emptied itself.
            typed = ""
        }
    }

    val append: (Char) -> Unit = { digit ->
        if (typed.length < PIN_LENGTH) {
            wrong = false
            typed += digit
        }
    }
    // Clearing the error as well: the line says "that was wrong", and a backspace is already the
    // next attempt.
    val backspace: () -> Unit = {
        typed = typed.dropLast(1)
        wrong = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Opaque, because this sits on top of the pages rather than in place of them.
            .background(scheme.background)
            // A cover that lets a tap through to the list underneath is not a lock. The keys take
            // their own taps first; whatever is left is swallowed here instead of reaching the
            // page below, which is otherwise still composed and still listening.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            // Edge to edge: the dots and the keypad both have to stay off the status bar and off
            // the navigation bar, or the last row of keys sits under the system's own buttons.
            .safeDrawingPadding()
            .padding(horizontal = 26.dp, vertical = 20.dp),
    ) {
        // The keys give way before the last row is cut off: a phone with a large system font has
        // less room than the design assumes, and a keypad whose "0" cannot be reached is a keypad
        // that can lock somebody out of their own app.
        val keyGap = 14.dp
        val keySize = ((maxHeight - FixedContent - keyGap * 3) / 4).coerceIn(MinKey, MaxKey)
        val gap = if (keySize < 60.dp) 10.dp else keyGap

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                FlGlyphIcon(glyph = FlGlyphs.lock, size = 30.dp, tint = scheme.primary, fill = 1f)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "输入生日密码",
                style = FlText.title2,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "这台手机给软件上了锁，输入四位数字才能打开。",
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))

            PinDots(filled = typed.length, wrong = wrong)
            Spacer(Modifier.height(10.dp))
            // A fixed height whether or not there is an error: a line that appears and disappears
            // would move the keypad under the thumb in the middle of an entry.
            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                if (wrong) {
                    Text(
                        text = "密码不对，再试一次",
                        style = FlText.caption2,
                        color = scheme.error,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            PinPad(
                onDigit = append,
                onBackspace = backspace,
                keySize = keySize,
                keyGap = gap,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
