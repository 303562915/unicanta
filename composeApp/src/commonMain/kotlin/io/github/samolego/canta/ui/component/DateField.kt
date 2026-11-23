package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Digits a date holds once its separators are gone: `YYYYMMDD`. */
const val DATE_DIGITS = 8

/** What an empty field shows. */
private const val DATE_PLACEHOLDER = "YYYY-MM-DD"

/**
 * A date typed as eight digits and shown as `2000-11-01`.
 *
 * The dashes are drawn rather than typed: [value] only ever holds digits, and everything that
 * reads it — the check for "long enough to submit", the comparison with the stored answer — works
 * on that form. The reader still sees the date the way it is written on every form they have ever
 * filled in, and a backspace takes away a digit instead of stopping at a separator.
 *
 * There is no box and no border, only a hairline under the digits: on the lock screen this is one
 * line of text on an empty page, which is the whole look the page is going for.
 */
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val style = TextStyle(
        // Big, monospaced and tracked out: eight digits that read as one date rather than as a
        // number somebody is typing into a form.
        fontSize = 30.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center,
        color = scheme.onSurface,
    )
    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = { typed ->
                // Everything but digits is dropped as it arrives, so a paste of "2000-11-01" and a
                // keyboard that only offers numbers both end up in the same eight digits.
                onValueChange(typed.filter { it in '0'..'9' }.take(DATE_DIGITS))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            textStyle = style,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            visualTransformation = DateVisualTransformation,
            cursorBrush = SolidColor(scheme.primary),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(
                            text = DATE_PLACEHOLDER,
                            style = style.copy(color = scheme.onSurfaceVariant.copy(alpha = 0.4f)),
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        // The only line on the page that is not text: it turns red when the answer is wrong.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (isError) scheme.error else scheme.outlineVariant),
        ) {}
    }
}

/**
 * The eight digits as `YYYY-MM-DD`.
 *
 * A dash is inserted after the fourth and sixth digit; the offset mappings keep the caret next to
 * the digit it belongs to, so editing in the middle behaves the way the visible text suggests.
 * Both mappings clamp into range: a caret that lands past the end is a crash, not a glitch.
 */
private object DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val shown = buildAnnotatedString {
            raw.forEachIndexed { index, digit ->
                if (index == 4 || index == 6) append('-')
                append(digit)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val from = offset.coerceIn(0, raw.length)
                val shownOffset = from + (if (from > 4) 1 else 0) + (if (from > 6) 1 else 0)
                return shownOffset.coerceIn(0, shown.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val from = offset.coerceIn(0, shown.length)
                val rawOffset = from - (if (from > 4) 1 else 0) - (if (from > 7) 1 else 0)
                return rawOffset.coerceIn(0, raw.length)
            }
        }
        return TransformedText(shown, mapping)
    }
}
