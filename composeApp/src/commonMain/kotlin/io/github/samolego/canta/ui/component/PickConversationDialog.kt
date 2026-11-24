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
import io.github.samolego.canta.data.cache.ScreenCache
import kotlinx.serialization.Serializable
import io.github.samolego.canta.data.community.Conversation
import io.github.samolego.canta.data.community.SocialRepository
import kotlinx.coroutines.launch

/**
 * 这扇窗上一次列出来的那份会话，进程级存着（见 [ScreenCache]）。
 *
 * 为什么需要它：转发一条消息、分享一个 App、递一张名片，三个入口做的是同一件事，都要先把
 * 「能发给谁」列一遍。列表原来每次都从网络开始，于是每次点开窗先看到一个「…」，几百毫秒之后
 * 才变成会话行——而这份名单刚才明明就在屏幕上。留在进程里，窗一开就是满的，网络回来再顶掉。
 *
 * 只放画这一屏要用的只读数据（[Conversation] 是不可变 data class）；**不放** MutableState、
 * 协程、回调、`busy` / `hint` / 正在发的是哪一条——那些是这一回的事，重新打开本就该是干净的，
 * 缓存它们只会把上一回失败的提示带回来。
 */
@Serializable
private data class ForwardTargetsSnapshot(val targets: List<Conversation>)

/**
 * 快照的键：带上身份，一个人的会话不能画给另一个人看。
 *
 * 身份为空（调用方还在异步读身份，第一帧就是这个状态）时返回 null：那时既不读也不写——
 * 空串那一格会被所有身份串用。键的惯例见 [ScreenCache]：`"screen:<部件>:<身份或编号>"`。
 */
private fun forwardTargetsCacheKey(owner: String): String? =
    if (owner.isEmpty()) null else "screen:forwardtargets:$owner"

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
    // 上一次列出来的那份会话：有它就先铺上，`targets` 从非空开始，那个「…」根本不会出现。
    // 按身份取（`remember(owner)`），换一个身份看就是另一份列表。
    val cacheKey = forwardTargetsCacheKey(owner)
    val cached = remember(owner) { cacheKey?.let { key -> ScreenCache.get<ForwardTargetsSnapshot>(key) } }
    // null 表示还在拉；空列表是"拉到了，但没有"——两种状态在界面上一句话就分得开。
    var targets by remember(owner) { mutableStateOf(cached?.targets) }
    var busy by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    /** 这一次没能拉到最新的一版：值是原因，界面在顶部留一行，下面缓存的内容照旧画着。 */
    var loadError by remember(owner) { mutableStateOf<String?>(null) }
    /** 现在画着的是上次那一份（刷新失败之后保留下来的），顶部那句话据此说"下面是上次的"。 */
    var stale by remember(owner) { mutableStateOf(false) }

    LaunchedEffect(owner, repository) {
        if (owner.isEmpty()) {
            targets = emptyList()
            loadError = null
            stale = false
            return@LaunchedEffect
        }
        // 失败与"真的一条会话都没有"在返回值上分不开（两种都是空列表），所以成没成要看
        // `lastError` / `lastFailureWasNetwork`：照着返回值画就等于因为一次断网把名单清空。
        val result = runCatching { repository.conversationsFor(owner) }
        val failed = result.isFailure ||
            repository.lastError != null ||
            repository.lastFailureWasNetwork
        if (failed) {
            loadError = if (repository.lastFailureWasNetwork) {
                "没连上后端，检查代理或网络"
            } else {
                "后端没有答上来"
            }
            // 有上一份就留着画（只在顶部说一行原因）；没有才退成空列表，走下面那句"还没有可以发送的会话"。
            stale = cached != null
            if (targets == null) targets = emptyList()
        } else {
            val fetched = result.getOrDefault(emptyList())
            // 网络成功才覆盖并写回：失败时写进去的会是一份空名单，下次进来先画的就成了"没有会话"。
            targets = fetched
            stale = false
            loadError = null
            cacheKey?.let { key -> ScreenCache.put(key, ForwardTargetsSnapshot(fetched)) }
        }
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
        // 拉挂了只在顶部说一行原因，不把下面画着的那份名单换成错误页：人还站在这扇窗前，
        // 换一个会话再试是显然的下一步，先把手上这份名单拿走。
        loadError?.let { reason ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (stale) "$reason，下面是上次列出来的会话" else "$reason，没拉到会话列表",
                fontSize = 10.sp,
                color = scheme.error,
            )
        }
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
                text = "正在读取会话…",
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
            )

            // 拉挂了的时候上面那行已经说明了原因，这里不再说一遍"还没有会话"——那是另一件事。
            loaded.isEmpty() -> if (loadError == null) Text(
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
                        PersonAvatar(
                            name = label,
                            // 私聊用对面那个人的身份号取照片；群聊没有 peer，落回群名首字。
                            owner = target.peer,
                            size = 32.dp,
                            background = scheme.primary.copy(alpha = 0.12f),
                            letterColor = scheme.primary,
                        )
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
