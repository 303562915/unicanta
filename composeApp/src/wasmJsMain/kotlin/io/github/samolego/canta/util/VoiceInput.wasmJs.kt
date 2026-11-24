package io.github.samolego.canta.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * 网页端没有接语音识别：点了只说一句话。浏览器自己的 `SpeechRecognition` 只有 Chrome 系
 * 有实现，保持和其他平台同一套行为，不做半套。
 */
@Composable
actual fun rememberVoiceInput(): VoiceInput {
    val messageState = remember { mutableStateOf<String?>(null) }
    return remember {
        object : VoiceInput {
            override val available: Boolean get() = false
            override val listening: Boolean get() = false
            override val message: String? get() = messageState.value

            override fun start(onText: (String) -> Unit) {
                messageState.value = "网页版不支持语音输入，用手机发"
            }

            override fun stop() {
                messageState.value = null
            }
        }
    }
}
