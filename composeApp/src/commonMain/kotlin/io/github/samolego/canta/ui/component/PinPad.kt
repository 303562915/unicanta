package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf

/**
 * How many digits the start-up lock takes. Four because the thing being asked for is a birthday
 * without its century — the shortest thing a person remembers without writing it down.
 */
const val PIN_LENGTH = 4

/**
 * True when [value] is a lock that can actually be satisfied: exactly [PIN_LENGTH] ASCII digits.
 *
 * Everything that reads the stored number goes through this. A value that failed it — a file
 * edited by hand, a format from some other build — must be treated as "no lock" rather than as a
 * keypad nobody can type their way past, because the one mistake this feature cannot afford is
 * locking the owner out of their own app for good.
 *
 * The range test is '0'..'9' rather than `Char.isDigit()` on purpose: `isDigit` also accepts
 * Arabic-Indic and other decimal scripts, and the keypad below can only ever produce ASCII.
 */
fun isUsablePin(value: String): Boolean = value.length == PIN_LENGTH && value.all { it in '0'..'9' }

/** Key geometry of the full-size grid; [PinPad] can be asked for anything from about 40 dp up. */
private val KeySize = 68.dp
private val KeyGap = 14.dp
/** Size at which the digit style steps down and the backspace glyph follows the smaller keys. */
private val SmallKey = 60.dp
private val DotSize = 13.dp
private val DotsGap = 16.dp

/**
 * One dot per digit: filled while [filled] covers it, and red while [wrong] says the entry that
 * filled them just came out wrong. The row is used both by the lock screen and by the dialog that
 * sets the number, so the two always look like the same lock.
 *
 * [wrong] only colours the dots that carry a digit: a row of four red dots with nothing typed into
 * it would read as "four digits entered" at the very moment the row is empty again.
 */
@Composable
fun PinDots(filled: Int, wrong: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(DotsGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PIN_LENGTH) { index ->
            val lit = index < filled
            val colour = when {
                lit && wrong -> scheme.error
                lit -> scheme.primary
                else -> scheme.onSurfaceVariant.copy(alpha = 0.22f)
            }
            Box(modifier = Modifier.size(DotSize).clip(CircleShape).background(colour)) {}
        }
    }
}

/**
 * The keypad: 1–9, a blank slot, 0, and backspace — the arrangement a phone dialer has used for
 * forty years, so nothing here has to be learned by the person holding the phone.
 *
 * [keySize] is passed in rather than fixed: the lock screen shrinks it when the screen is short or
 * the system font is large, because a keypad whose last row is cut off is a keypad whose "0" can
 * never be typed. The dialog that sets the number draws the same grid a size down.
 */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    keySize: Dp = KeySize,
    keyGap: Dp = KeyGap,
) {
    // One style step down once the keys get small, so a digit never touches the rim of its circle.
    val digitStyle = if (keySize < SmallKey) FlText.title2 else FlText.title1
    // Null is a slot with no digit: the bottom row has one empty corner and one backspace.
    val rows: List<List<Char?>> = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf(null, '0', null),
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(keyGap),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(keyGap)) {
                row.forEachIndexed { column, digit ->
                    when {
                        digit != null -> DigitKey(
                            digit = digit,
                            onDigit = onDigit,
                            keySize = keySize,
                            digitStyle = digitStyle,
                        )
                        // The bottom row keeps both outer slots so the 0 sits centred: the left one
                        // stays empty, the right one is the backspace.
                        column == 0 -> Spacer(Modifier.size(keySize))
                        else -> BackspaceKey(onBackspace = onBackspace, keySize = keySize)
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitKey(
    digit: Char,
    onDigit: (Char) -> Unit,
    keySize: Dp,
    digitStyle: TextStyle,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(keySize)
            .clip(CircleShape)
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDigit(digit) },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = digit.toString(), style = digitStyle, color = scheme.onSurface)
    }
}

@Composable
private fun BackspaceKey(onBackspace: () -> Unit, keySize: Dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(keySize)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBackspace,
            ),
        contentAlignment = Alignment.Center,
    ) {
        FlGlyphIcon(
            glyph = FlGlyphs.arrowBack,
            size = if (keySize < SmallKey) 20.dp else 24.dp,
            tint = scheme.onSurfaceVariant,
            fill = 0f,
        )
    }
}
