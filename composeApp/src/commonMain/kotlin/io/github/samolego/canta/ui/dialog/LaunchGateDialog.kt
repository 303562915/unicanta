package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.DATE_DIGITS
import io.github.samolego.canta.ui.component.DateField
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.lock.LAUNCH_QUESTIONS
import io.github.samolego.canta.ui.lock.LaunchGate
import io.github.samolego.canta.ui.lock.answersGate
import io.github.samolego.canta.ui.lock.normalizeBirthday

/** What the dialog is asking for right now. */
private enum class GateStep {
    /** Pick which birthday the lock asks for. */
    Choose,

    /** Type the date that answers it. */
    Answer,

    /** Type it once more, so a slip of the finger does not become the answer. */
    Confirm,

    /** The date that answers the lock already in force, before it is turned off. */
    Verify,
}

/**
 * Sets, replaces or clears the start-up question.
 *
 * The lock is a question and a date, so setting it is two decisions: which birthday to ask about,
 * and what that date is. [existing] is what the store holds now — null means the lock is off and
 * the dialog only offers to turn it on; anything else also offers to turn it off, which asks for
 * the current date first so that a stray tap in a pocket cannot undo the protection.
 *
 * The date is asked for twice when it is set. That is not ceremony: a person who mistyped the day
 * has no way back in, and `2000-11-01` and `2000-10-11` are one finger apart.
 */
@Composable
fun LaunchGateDialog(
    existing: LaunchGate?,
    onSave: (questionId: String, answer: String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(GateStep.Choose) }
    var questionId by remember { mutableStateOf(existing?.question?.id ?: LAUNCH_QUESTIONS.first().id) }
    var digits by remember { mutableStateOf("") }
    /** The first date typed, held while the second one is entered. */
    var candidate by remember { mutableStateOf("") }
    var rejected by remember { mutableStateOf<String?>(null) }

    val label = LAUNCH_QUESTIONS.firstOrNull { it.id == questionId }?.label ?: "生日"

    /** Shared by all steps: a rejected line clears the moment something is typed again. */
    val onDigits: (String) -> Unit = {
        digits = it
        rejected = null
    }

    // Eight digits that are not a date — a month of 13, a day of 45 — cannot be submitted, and a
    // button that stays grey with no explanation reads as a broken button. Say what is wrong
    // instead, once the field is full and the answer is conclusive.
    val fullButInvalid = digits.length == DATE_DIGITS && normalizeBirthday(digits) == null
    val errorText = rejected ?: if (fullButInvalid) "这不像一个日期" else null

    val title = when (step) {
        GateStep.Choose -> if (existing == null) "设置启动问题" else "更换启动问题"
        GateStep.Answer -> label
        GateStep.Confirm -> "再输一次"
        GateStep.Verify -> "关闭启动密码"
    }

    CantaDialog(
        onDismissRequest = onDismiss,
        title = title,
        // A short screen still has to reach the last row of the picker and the button under it.
        scrollable = true,
        buttons = {
            when (step) {
                GateStep.Choose -> {
                    if (existing != null) {
                        TextButton(onClick = {
                            step = GateStep.Verify
                            digits = ""
                            rejected = null
                        }) { Text("关闭保护") }
                    }
                    TextButton(onClick = onDismiss) { Text("取消") }
                }

                GateStep.Answer -> {
                    TextButton(onClick = {
                        step = GateStep.Choose
                        digits = ""
                        rejected = null
                    }) { Text("返回") }
                    TextButton(
                        enabled = normalizeBirthday(digits) != null,
                        onClick = {
                            candidate = normalizeBirthday(digits).orEmpty()
                            digits = ""
                            step = GateStep.Confirm
                        },
                    ) { Text("下一步") }
                }

                GateStep.Confirm -> {
                    TextButton(onClick = {
                        step = GateStep.Answer
                        digits = ""
                        rejected = null
                    }) { Text("返回") }
                    TextButton(
                        enabled = normalizeBirthday(digits) != null,
                        onClick = {
                            val typed = normalizeBirthday(digits).orEmpty()
                            if (typed == candidate) {
                                onSave(questionId, typed)
                                onDismiss()
                            } else {
                                rejected = "两次输入不一样，重新设一次"
                                digits = ""
                                step = GateStep.Answer
                            }
                        },
                    ) { Text("保存") }
                }

                GateStep.Verify -> {
                    TextButton(onClick = {
                        step = GateStep.Choose
                        digits = ""
                        rejected = null
                    }) { Text("返回") }
                    TextButton(
                        enabled = normalizeBirthday(digits) != null,
                        onClick = {
                            if (existing != null && answersGate(digits, existing.answer)) {
                                onClear()
                                onDismiss()
                            } else {
                                rejected = "日期不对"
                                digits = ""
                            }
                        },
                    ) { Text("关闭") }
                }
            }
        },
    ) {
        when (step) {
            GateStep.Choose -> QuestionPicker(
                selected = questionId,
                onSelect = {
                    questionId = it
                    digits = ""
                    rejected = null
                    step = GateStep.Answer
                },
            )

            GateStep.Answer -> DateStep(
                hint = "输入$label，比如 2000-11-01。设好后每次打开软件都要填它，" +
                    "请记牢——忘了只能清除应用数据才能进来。",
                digits = digits,
                onDigits = onDigits,
                error = errorText,
            )

            GateStep.Confirm -> DateStep(
                hint = "确认刚才那一天，两次一样才算设好。",
                digits = digits,
                onDigits = onDigits,
                error = errorText,
            )

            GateStep.Verify -> DateStep(
                hint = "输入现在的日期答案，确认是你本人在关闭保护。",
                digits = digits,
                onDigits = onDigits,
                error = errorText,
            )
        }
    }
}

/** The five questions, one row each: the whole picker is a list, not a form. */
@Composable
private fun QuestionPicker(selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "选一个家里人记得住的日子",
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        LAUNCH_QUESTIONS.forEachIndexed { index, question ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 2.dp),
                    thickness = 0.6.dp,
                    color = scheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onSelect(question.id) }
                    .padding(horizontal = 2.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = question.label,
                    style = FlText.subheadline,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (question.id == selected) {
                    Spacer(Modifier.width(8.dp))
                    FlGlyphIcon(glyph = FlGlyphs.check, size = 18.dp, tint = scheme.primary, fill = 0.3f)
                }
            }
        }
    }
}

/** A sentence of help, the date, and one red line of why the last attempt was not accepted. */
@Composable
private fun DateStep(
    hint: String,
    digits: String,
    onDigits: (String) -> Unit,
    error: String?,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = hint,
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        DateField(value = digits, onValueChange = onDigits, isError = error != null)
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.height(18.dp), contentAlignment = Alignment.Center) {
            error?.let {
                Text(text = it, style = FlText.caption2, color = scheme.error)
            }
        }
    }
}
