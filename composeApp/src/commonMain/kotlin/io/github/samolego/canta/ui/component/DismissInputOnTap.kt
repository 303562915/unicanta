package io.github.samolego.canta.ui.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * 点空白处收起输入：清焦点 + 收输入法，再让调用方把自己的面板一起收掉。
 *
 * 为什么需要它：Compose 里点一个不是输入框的地方**不会**自动把焦点还回去，输入法也就留在屏幕上。
 * 于是评论页、聊天页只要点一下正文，键盘就赖着不走——抖音那一套是点哪儿都收，这里跟着它。
 *
 * 用法是挂在这块"空白"的父节点上（列表外面那一层），不是挂在每一行上：
 *
 * ```
 * Box(Modifier.fillMaxSize().dismissInputOnTap { pickerOpen = false }) { LazyColumn { ... } }
 * ```
 *
 * 挂在父节点上不会抢走子节点的点击：`detectTapGestures` 等的是**没被子节点消费**的 down，
 * 行自己的 `clickable` 先吃到这一下，父节点这里就什么都不会发生。
 *
 * [onDismiss] 用 [rememberUpdatedState] 取最新那份、`pointerInput` 的 key 保持 `Unit`：
 * 每次重组都换一个 lambda 当 key 的话，手势检测器会在滚动中途被重启，一次点击可能就丢了。
 *
 * @param onDismiss 收起输入时顺带要做的事，比如收掉表情面板 / 更多面板。
 */
@Composable
fun Modifier.dismissInputOnTap(onDismiss: () -> Unit = {}): Modifier {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val latest = rememberUpdatedState(onDismiss)
    return this.pointerInput(Unit) {
        detectTapGestures {
            focus.clearFocus(force = true)
            keyboard?.hide()
            latest.value()
        }
    }
}
