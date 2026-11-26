package io.github.samolego.canta.data.community

/**
 * 一次级别修正操作的结果：成功带 id，失败带**原因**。
 *
 * 之前这里返回的是 `Long?`，页面拿到 null 只能写一句「没能提交：检查网络，或换一个档位再试」——
 * 网络没通、被限速、身份认不出来、后端没这个函数，四种完全不同的原因被压成同一句话，出了问题
 * 只能靠猜。现在把 [PlanRepository.lastError] / [lastFailureWasNetwork] 一起带出来。
 */
sealed interface LevelFixResult<out T> {
    data class Ok<T>(val value: T) : LevelFixResult<T>

    /**
     * @param reason 给人看的一句：后端自己拒绝时的原话，或者"网络没通"。
     * @param network true 表示什么都没答（断网、代理、超时），不是后端拒绝。
     */
    data class Failed(val reason: String, val network: Boolean) : LevelFixResult<Nothing>
}

/** 把 null 结果翻成一句能看的原因。 */
internal fun PlanRepository.failureReason(): LevelFixResult.Failed {
    val message = lastError?.takeIf { it.isNotBlank() }
    return when {
        message != null -> LevelFixResult.Failed(message, network = false)
        lastFailureWasNetwork -> LevelFixResult.Failed("网络没通，请求没发出去", network = true)
        else -> LevelFixResult.Failed("后端没给答复", network = true)
    }
}