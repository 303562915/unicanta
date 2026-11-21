package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The composer's field has to wrap a long comment and grow with it, not scroll it sideways.
 *
 * This is measured rather than reasoned about: the field is laid out for real in a narrow column,
 * a short draft is measured, the draft is replaced by a long one, and the same field is measured
 * again. A field that grew by a line proves the line wrapped — a single-line field keeps one line's
 * height whatever is typed into it.
 */
@OptIn(ExperimentalTestApi::class)
class CommentComposerWrapTest {

    @Test
    fun aLongDraftMakesTheFieldTaller() = runComposeUiTest {
        var draft by mutableStateOf("短")
        setContent {
            MaterialTheme {
                // Narrow, so a handful of characters already reaches the edge of the field and has
                // somewhere to wrap to.
                Box(Modifier.width(260.dp)) {
                    CommentComposer(
                        draft = draft,
                        onDraftChange = { draft = it },
                        onSend = {},
                    )
                }
            }
        }

        val field = onNode(hasSetTextAction())
        val shortHeight = field.fetchSemanticsNode().size.height

        runOnIdle { draft = "这是一条很长的评论，长到一行放不下".repeat(6) }
        waitForIdle()

        val tallHeight = field.fetchSemanticsNode().size.height
        assertTrue(
            tallHeight > shortHeight,
            "输入框应随内容折行变高：短 $shortHeight -> 长 $tallHeight",
        )
    }
}