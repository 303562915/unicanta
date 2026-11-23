package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.PIN_LENGTH
import io.github.samolego.canta.ui.component.PinDots
import io.github.samolego.canta.ui.component.PinPad
import io.github.samolego.canta.ui.fl.FlText
import kotlinx.coroutines.delay

/** How long an error line stays before the dots clear themselves. */
private const val WRONG_PIN_PAUSE_MILLIS = 260L

/** What the dialog is asking for right now. */
private enum class PinStep {
    /** Pick the four digits. */
    Enter,

    /** Type them once more, so a slip of the finger does not become the password. */
    Confirm,

    /** The old number, before the protection is turned off. */
    VerifyCurrent,
}

/**
 * Sets, replaces or clears the four-digit start-up lock. [existingPin] is what the store holds now:
 * empty means the lock is off, and the dialog then only offers to turn it on; anything else also
 * offers to turn it off, which asks for the current number first.
 *
 * The number is asked for twice when it is set. That is not ceremony: the whole point of this lock
 * is that a person who forgot what they typed has no way back in, and a confirmation is the only
 * cheap guard against a mistyped digit becoming the password.
 */
@Composable
fun LaunchPinDialog(
    existingPin: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(PinStep.Enter) }
    var typed by remember { mutableStateOf("") }
    /** The first entry, held while the second one is typed. */
    var candidate by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // The fourth digit is what moves the dialog on, so the step machine lives here rather than in
    // three separate tap handlers.
    LaunchedEffect(typed, step) {
        if (typed.length < PIN_LENGTH) return@LaunchedEffect
        when (step) {
            PinStep.Enter -> {
                candidate = typed
                typed = ""
                wrong = false
                message = null
                step = PinStep.Confirm
            }

            PinStep.Confirm -> {
                if (typed == candidate) {
                    onSave(typed)
                    onDismiss()
                } else {
                    wrong = true
                    message = "两次不一样，重新设一次"
                    delay(WRONG_PIN_PAUSE_MILLIS)
                    typed = ""
                    candidate = ""
                    wrong = false
                    step = PinStep.Enter
                }
            }

            PinStep.VerifyCurrent -> {
                if (typed == existingPin) {
                    onClear()
                    onDismiss()
                } else {
                    wrong = true
                    message = "密码不对"
                    delay(WRONG_PIN_PAUSE_MILLIS)
                    typed = ""
                    wrong = false
                }
            }
        }
    }

    val append: (Char) -> Unit = { digit ->
        if (typed.length < PIN_LENGTH) {
            // A fresh digit means a fresh attempt: an error line left over from the previous one
            // would still be sitting there while the correct number is being typed.
            message = null
            wrong = false
            typed += digit
        }
    }
    val backspace: () -> Unit = {
        typed = typed.dropLast(1)
        wrong = false
    }

    val title = when (step) {
        PinStep.Enter -> if (existingPin.isEmpty()) "设置生日密码" else "修改生日密码"
        PinStep.Confirm -> "再输一次"
        PinStep.VerifyCurrent -> "关闭启动密码"
    }
    val hint = when (step) {
        PinStep.Enter ->
            "输入四位数字，用生日就行，比如 0315。设好后每次打开软件都要输它，" +
                "请记牢——忘了只能清除应用数据才能进来。"
        PinStep.Confirm -> "确认刚才那四位，两次一样才算设好。"
        PinStep.VerifyCurrent -> "输入现在的密码，确认是你本人在关闭保护。"
    }

    CantaDialog(
        onDismissRequest = onDismiss,
        title = title,
        // A short screen still has to reach the keypad's last row.
        scrollable = true,
        buttons = {
            if (step == PinStep.VerifyCurrent) {
                // Back to picking a new number instead of leaving the page.
                TextButton(onClick = {
                    step = PinStep.Enter
                    typed = ""
                    wrong = false
                    message = null
                }) { Text("返回") }
            } else if (existingPin.isNotEmpty()) {
                TextButton(onClick = {
                    step = PinStep.VerifyCurrent
                    typed = ""
                    wrong = false
                    message = null
                }) { Text("关闭保护") }
            }
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = hint,
                style = FlText.caption2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            PinDots(filled = typed.length, wrong = wrong)
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.height(18.dp), contentAlignment = Alignment.Center) {
                message?.let {
                    Text(
                        text = it,
                        style = FlText.caption2,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            PinPad(
                onDigit = append,
                onBackspace = backspace,
                keySize = 54.dp,
                keyGap = 10.dp,
            )
        }
    }
}
