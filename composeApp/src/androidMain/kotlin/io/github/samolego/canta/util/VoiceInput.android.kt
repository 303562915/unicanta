package io.github.samolego.canta.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/** Request code of the microphone dialog; the answer is read back from the context again. */
private const val VOICE_REQUEST_CODE = 0x7A11

private fun Context.findActivityOrNull(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 系统自带的语音识别：`SpeechRecognizer` 拿到的就是手机上装的那套引擎（Google 语音服务、
 * 各家 ROM 自带的输入法引擎都算），所以不加依赖、不涨包体，识别质量跟着系统走。
 *
 * 识别必须走主线程：Compose 里就是在主线程，别把它挪进协程。
 */
@Composable
actual fun rememberVoiceInput(): VoiceInput {
    val context = LocalContext.current
    val recognizer = remember(context) {
        val usable = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
        if (usable) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else {
            null
        }
    }
    // 识别器是系统服务代理，页面走掉要还回去，不然会一直占着麦克风。
    DisposableEffect(recognizer) {
        onDispose { runCatching { recognizer?.destroy() } }
    }

    val listeningState = remember { mutableStateOf(false) }
    val messageState = remember { mutableStateOf<String?>(null) }

    fun cancelListening() {
        listeningState.value = false
        runCatching { recognizer?.cancel() }
    }

    return remember(context, recognizer) {
        object : VoiceInput {
            override val available: Boolean get() = recognizer != null
            override val listening: Boolean get() = listeningState.value
            override val message: String? get() = messageState.value

            override fun start(onText: (String) -> Unit) {
                val engine = recognizer ?: run {
                    messageState.value = "这台设备没有语音识别"
                    return
                }
                if (!context.hasMicPermission()) {
                    messageState.value = "需要麦克风权限，同意后再点一次"
                    context.findActivityOrNull()?.let { activity ->
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            VOICE_REQUEST_CODE,
                        )
                    }
                    return
                }
                // 上一次还没结束就再点：先收掉，再开一轮，免得两条录音叠在一起。
                runCatching { engine.cancel() }

                engine.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        messageState.value = "正在听…"
                    }

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() = Unit

                    override fun onError(error: Int) {
                        listeningState.value = false
                        messageState.value = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                            -> "没听清，再说一次"

                            SpeechRecognizer.ERROR_AUDIO -> "麦克风被打断，再点一次"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限"
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                            -> "识别服务连不上网"

                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器还在忙，等一下再点"
                            SpeechRecognizer.ERROR_CLIENT -> "语音识别被打断"
                            else -> "语音识别失败（$error）"
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        listeningState.value = false
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                            .trim()
                        if (text.isEmpty()) {
                            messageState.value = "没听清，再说一次"
                        } else {
                            messageState.value = null
                            onText(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) = Unit

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    // 固定中文：这个界面全是中文，跟着系统语言走到英文引擎只会听不懂。
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
                listeningState.value = true
                messageState.value = "正在听…"
                val started = runCatching { engine.startListening(intent) }.isSuccess
                if (!started) {
                    listeningState.value = false
                    messageState.value = "启动语音识别失败，再点一次"
                }
            }

            override fun stop() {
                messageState.value = null
                cancelListening()
            }
        }
    }
}
