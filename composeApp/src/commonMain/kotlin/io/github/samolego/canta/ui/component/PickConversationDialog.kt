package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.Conversation
import io.github.samolego.canta.data.community.SocialRepository
import kotlinx.coroutines.launch

/**
 * 挑一个会话，然后把某样东西发过去：转发一条消息、分享一个 App、递一张名片，都是这件事。
 *
 * 所以它自己拉会话列表、自己调 [onPick]、自己显示成败，调用方只需要说"发什么"和"怎么发"。
 * 把这套状态留在页面里会让每个入口各写一遍，而它们长得一模一样。
 *
 * 失败时**不关窗**：人还站在这里，换一个会话再试是显然的下一步；关掉反而要重新点开一遍。
 *
 * [onPick] 返回 true 才算发出去——它是个 suspend，网络请求就在里面。
 */
@Composable
fun PickConversationDialog(
    repository: SocialRepository,
    owner: String,
    title: String,
    /** 顶部那儿一行摘要：转发是正文前几十字，分享是应用名，名片是那个人的名字。 */
    preview: String,
    /** 成功那一下说什么（「已转发」「已分享」「已发送」）。 */
    doneHint: String = "已发送",
    onPick: suspend (Conversation) -> Boolean,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // null 表示还在拉；空列表是"拉到了，但没有"——两种状态在界面上一句话就分得开。
    var targets by remember { mutableStateOf<List<Conversation>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(owner, repository) {
        if (owner.isEmpty()) {
            targets = emptyList()
            return@LaunchedEffect
        }
        targets = runCatching { repository.conversationsFor(owner) }.getOrNull().orEmpty()
    }

    CantaDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = title,
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        Text(
            text = preview,
            fontSize = 11.sp,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = hint.orEmpty(),
                fontSize = 10.sp,
                color = if (hint == doneHint) scheme.primary else scheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))
        val loaded = targets
        when {
            loaded == null -> Text(
                text = "…",
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
            )

            loaded.isEmpty() -> Text(
                text = "还没有可以发送的会话",
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
            )

            else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(loaded, key = { it.id }) { target ->
                    val label = target.title.ifBlank { target.peerName }.ifBlank { "会话" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !busy) {
                                if (busy) return@clickable
                                scope.launch {
                                    busy = true
                                    hint = null
                                    val ok = runCatching { onPick(target) }.getOrDefault(false)
                                    busy = false
                                    if (ok) {
                                        hint = doneHint
                                        onDismiss()
                                    } else {
                                        // 失败留在这扇窗里：换一个会话再点是下一步，不是重来一次。
                                        hint = "没发出去，检查代理后再试"
                                    }
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(scheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label.take(1),
                                fontSize = 13.sp,
                                color = scheme.primary,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
