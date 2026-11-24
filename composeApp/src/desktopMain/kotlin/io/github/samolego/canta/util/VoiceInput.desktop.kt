package io.github.samolego.canta.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * 桌面端没有接语音识别：点了只说一句话，不假装在听。
 *
 * 系统级识别只有 Android 有（`SpeechRecognizer`）；要在这里做就得打包一整套离线模型，
 * 那是几百 MB 的事，不值当。
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
                messageState.value = "电脑版不支持语音输入，用手机发"
            }

            override fun stop() {
                messageState.value = null
            }
        }
    }
}
