package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.ui.component.DateField
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.lock.LaunchGate
import io.github.samolego.canta.ui.lock.answersGate
import io.github.samolego.canta.ui.lock.normalizeBirthday

/**
 * The gate in front of everything: one question, one line for the date, one button.
 *
 * This exists for the phone that gets handed around — a grandparent tapping at the icon in a
 * pocket, a child swiping through the list. It is a lock against a stray tap rather than against
 * somebody who owns the phone, so it is deliberately plain: no attempt counter, no timeout, no
 * lockout that would leave the owner outside their own app. A date that does not match says so
 * once and leaves what was typed where it is, so a mistyped digit can be fixed instead of retyped.
 *
 * Everything on this page is text on an empty surface — a small label, the question at reading
 * size, the date under it, one button. That is the whole design: there is nothing here to look at
 * except what is being asked.
 *
 * It is drawn as an opaque cover over the pages rather than replacing them, so what was on screen
 * before the app was put down is still there behind it, unchanged.
 *
 * [onUnlock] runs when [gate]'s date has been typed correctly.
 */
@Composable
fun LaunchLockScreen(gate: LaunchGate, onUnlock: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var digits by remember { mutableStateOf("") }
    var rejected by remember { mutableStateOf(false) }

    val submit = {
        if (answersGate(digits, gate.answer)) {
            onUnlock()
        } else {
            rejected = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Opaque, because this sits on top of the pages rather than in place of them.
            .background(scheme.background)
            // A cover that lets a tap through to the list underneath is not a lock. Children take
            // their own taps first; whatever is left is swallowed here instead of reaching the
            // page below, which is otherwise still composed and still listening.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .safeDrawingPadding()
            .padding(horizontal = 30.dp, vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // A label, not a title: the question under it is the thing being read.
            Text(
                text = "已锁定",
                style = FlText.caption2.copy(letterSpacing = 2.sp),
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = gate.question.label,
                style = FlText.title1,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "输入这一天的日期来解锁",
                style = FlText.footnote,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))
            DateField(
                value = digits,
                onValueChange = {
                    digits = it
                    // A fresh digit means a fresh attempt; a red line left over from the previous
                    // one would still be sitting there while the right date is being typed.
                    rejected = false
                },
                isError = rejected,
            )
            Spacer(Modifier.height(10.dp))
            // Fixed height whether or not there is an error: a line that appears and disappears
            // would move the field under the finger in the middle of an entry.
            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                if (rejected) {
                    Text(
                        text = "日期不对，再试一次",
                        style = FlText.caption2,
                        color = scheme.error,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = submit,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = CircleShape,
                // Flat on purpose: a raised button would be the only piece of depth on a page that
                // is otherwise nothing but text.
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                // The one filled thing here, so "this is the tap" needs no reading.
                enabled = normalizeBirthday(digits) != null,
            ) {
                Text(text = "解锁", fontSize = 16.sp)
            }

            Spacer(Modifier.weight(1.2f))
        }
    }
}
