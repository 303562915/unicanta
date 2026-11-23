package io.github.samolego.canta.data.community

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 谁在告诉聊天页「有新消息了」。
 *
 * 只有一个动作：[updates] 给一条会话，回一串「可以再去问一次了」的信号。它说的是**有变化**，
 * 不是变化本身：正文仍旧由 [SocialRepository.messagesFor] 带着身份去取，所以推送这一层即使被
 * 别人订阅到，也拿不到任何一句话。
 *
 * 做成接口而不是直接写死连接，是因为网页版没有 websocket 引擎：那一侧传 null，页面就照旧轮询。
 */
fun interface ChatLiveSource {
    /** [conversationId] 这个会话每一次「有新消息」发一个信号；连接断了就结束，由调用方决定要不要退回轮询。 */
    fun updates(conversationId: Long): Flow<Unit>
}

/**
 * Supabase Realtime 的裸 websocket 客户端，订阅一条会话的广播频道。
 *
 * 服务端那道闸在 `public.messages` 的 INSERT 触发器上（`realtime.send(..., 'new_message',
 * 'chat:<id>', false)`），payload 里只有消息 id 和会话号，没有正文。这里只认 `broadcast` 事件，
 * 收到就报一声，别的一律忽略（心跳回执、presence、system 那些都不该让页面去拉一次）。
 *
 * 失败不吵：连不上、被拒、引擎不存在，都只是把这条 flow 结束掉，聊天页那边还有轮询兜着。
 */
class ChatRealtimeHub(
    /** REST 的 rpc 入口，形如 `https://<ref>.supabase.co/rest/v1/rpc/`；这里只取主机名。 */
    restUrl: String,
    private val apiKey: String,
) : ChatLiveSource {

    private val socketUrl: String = socketUrlOf(restUrl)

    /** 带 apikey 的完整地址：算一次，每次重连都用同一个。 */
    private val connectedUrl: String = "$socketUrl?apikey=$apiKey&vsn=1.0.0"

    /**
     * 一条连接只服务一条会话，所以客户端本身可以留到进程里复用；引擎缺失（网页版）时是 null，
     * 那一刻起这个 hub 就什么也不做。
     */
    private val client: HttpClient? by lazy {
        runCatching { HttpClient { install(WebSockets) } }.getOrNull()
    }

    override fun updates(conversationId: Long): Flow<Unit> = channelFlow {
        val socket = client ?: return@channelFlow
        var attempt = 0
        while (isActive && attempt <= MaxAttempts) {
            val delivered = runCatching {
                socket.webSocket(connectedUrl) {
                    send(Frame.Text(joinFrame(conversationId)))
                    attempt = 0
                    val heart = launch {
                        var ref = 100
                        while (isActive) {
                            delay(HeartbeatMs)
                            if (runCatching { send(Frame.Text(heartbeatFrame(ref++))) }.isFailure) break
                        }
                    }
                    try {
                        for (frame in incoming) {
                            val text = (frame as? Frame.Text)?.readText() ?: continue
                            // 显式指到外层的 channel：session 自己也有 send，写 send(Unit) 会读着别扭。
                            if (mentionsNewMessage(text)) this@channelFlow.send(Unit)
                        }
                    } finally {
                        heart.cancel()
                    }
                }
            }.isSuccess
            // 连上过又断开，退避从零开始算：一阵网络抖动之后不该等半分钟。
            attempt += 1
            if (!delivered) {
                // 一次都没连上（引擎不支持、被墙、key 不对）：不要再空转，交给轮询。
                if (attempt >= 2) break
            }
            delay(backoffMillis(attempt))
        }
    }

    private fun joinFrame(conversationId: Long): String =
        """{"topic":"realtime:chat:$conversationId","event":"phx_join",""" +
            """"payload":{"config":{"broadcast":{"self":false},"presence":{"key":""}}},"ref":"1"}"""

    private fun heartbeatFrame(ref: Int): String =
        """{"topic":"phoenix","event":"heartbeat","payload":{},"ref":"$ref"}"""

    private companion object {
        /** 一条连接最多试几次就不再纠缠：剩下的交给轮询，别让手机一直重连。 */
        const val MaxAttempts = 6

        /** Supabase 的心跳窗口是 30 秒，早一点发，别卡在边上。 */
        const val HeartbeatMs = 25_000L

        fun backoffMillis(attempt: Int): Long {
            val shift = attempt.coerceAtMost(5)
            return minOf(30_000L, 1_000L shl shift)
        }

        /** 只认「这个频道里有人说了 new_message」这一种下行，其它一律当噪音。 */
        fun mentionsNewMessage(text: String): Boolean =
            text.contains("\"event\":\"broadcast\"") && text.contains("new_message")

        /** `https://<ref>.supabase.co/rest/v1/rpc/` → `wss://<ref>.supabase.co/realtime/v1/websocket?...`。 */
        fun socketUrlOf(restUrl: String): String {
            val host = restUrl.substringBefore("/rest/v1").trimEnd('/')
            val socketHost = when {
                host.startsWith("https://") -> "wss://" + host.removePrefix("https://")
                host.startsWith("http://") -> "ws://" + host.removePrefix("http://")
                else -> host
            }
            return "$socketHost/realtime/v1/websocket"
        }
    }
}
