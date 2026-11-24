package io.github.samolego.canta.data.community

import io.github.samolego.canta.util.TelemetryConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 一次 RPC 最多等多久：与 PlanRepository 的 RPC_TIMEOUT_MS 同一个数。 */
private const val RPC_TIMEOUT_MS = 30_000L

/**
 * 社区后端的最小 RPC 客户端：一次 POST、一段 JSON、一句失败说明。
 *
 * 为什么单独存在：推荐页新加的两块（CDK 兑换、帖子）各自成文件，各自有自己的仓储；它们
 * 不该再往 `PlanRepository` 里挤——那个类已经一千七百多行，两个新文件同时改它必然撞车。
 *
 * 判定规则与 `PlanRepository.call` / `rpc` 完全一致（PostgREST 的 `PGRST202` 视为「这个部署还没
 * 有这个函数」），两处要一起看：改一处时别忘了另一处。
 */
class CommunityRpc(
    private val client: HttpClient,
    private val baseUrl: String = TelemetryConfig.BASE_URL,
    private val apiKey: String = TelemetryConfig.PUBLISHABLE_KEY,
    /**
     * 这台手机的身份凭据。与 PlanRepository 的构造参数同名同义：认不出来的写操作由后端拒绝，
     * 而不是按客户端自报的号码记账。
     */
    private val identityToken: suspend () -> String = { "" },
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 上一次调用为什么没成：后端自己的一句话（拒绝）或 null（网络没通）。 */
    var lastError: String? = null
        private set

    /** 上一次失败是不是"根本没答"（没有路由、被挡、超时），而不是"答了但拒绝"。 */
    var lastFailureWasNetwork: Boolean = false
        private set

    /** 这次写调用要带的凭据；取不到就是空串，后端据此拒绝记在某个身份名下。 */
    suspend fun token(): String = runCatching { identityToken() }.getOrDefault("").trim()

    /**
     * 调一次 `POST baseUrl + name`。成功返回响应正文；被拒返回 null 并把理由写进 [lastError]；
     * 根本没通返回 null 并把 [lastFailureWasNetwork] 置 true。
     */
    suspend fun post(name: String, body: String): String? {
        val answered = runCatching {
            withTimeoutOrNull(RPC_TIMEOUT_MS) {
                val response = client.post(baseUrl + name) {
                    header("apikey", apiKey)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(body)
                }
                if (response.status.isSuccess()) {
                    Ok(response.bodyAsText())
                } else {
                    val text = runCatching { response.bodyAsText() }.getOrDefault("")
                    val message = runCatching {
                        json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull().orEmpty().ifBlank { text.take(200) }
                    Refused(message.ifBlank { "HTTP ${response.status.value}" })
                }
            }
        }.getOrNull() ?: Unreachable

        return when (answered) {
            is Ok -> {
                lastFailureWasNetwork = false
                lastError = null
                answered.text
            }
            is Refused -> {
                lastFailureWasNetwork = false
                lastError = answered.reason
                null
            }
            Unreachable -> {
                lastFailureWasNetwork = true
                lastError = null
                null
            }
        }
    }

    /**
     * 上一次失败只是因为「这个部署还没有这个函数」。
     *
     * 新功能在灰度里就靠它：老后端答 `PGRST202`，页面据此藏起那一块，而不是弹一个红字。
     */
    val functionMissing: Boolean
        get() = lastError?.let { it.contains("PGRST202") || it.contains("Could not find the function") } == true

    private sealed interface Answer {
        class Ok(val text: String) : Answer
        class Refused(val reason: String) : Answer
        object Unreachable : Answer
    }
}
