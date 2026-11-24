package io.github.samolego.canta.util

import androidx.compose.runtime.Composable

/**
 * 语音转文字：点一下开始听，说完把文字交回来。
 *
 * 用的是手机自带的识别服务（Android 的 `SpeechRecognizer`），不打包任何模型、不新增依赖，
 * 安装包大小一分不涨；机器上没有识别引擎时 [available] 就是 false，由调用方说一句「没有」。
 *
 * 只有 Android 有真实现；电脑端与网页端返回一个永远不可用的对象（见各平台的 actual）。
 */
interface VoiceInput {
    /** 这台设备/这个平台有没有可用的识别器。 */
    val available: Boolean

    /** 正在听。结束时自己变回 false。 */
    val listening: Boolean

    /** 上一次的提示：「正在听…」、失败原因、或「没听清」；识别成功后清空。 */
    val message: String?

    /** 开始听。识别出文字走 [onText]（可能被叫多次，最后的才算）。 */
    fun start(onText: (String) -> Unit)

    /** 停止听，之后不会再回调。 */
    fun stop()
}

@Composable
expect fun rememberVoiceInput(): VoiceInput
