package io.github.samolego.canta.util

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Forwards a log line to the platform log (logcat, stderr, browser console). */
internal expect fun platformLog(level: LogUtils.LogLevel, tag: String, message: String, throwable: Throwable?)

/**
 * In-app log shown on the logs page, mirrored to the platform log.
 */
object LogUtils {
    /** How many lines stay in memory; older ones are dropped, newest first in the list. */
    private const val MAX_ENTRIES = 2_000

    /**
     * 日志是**任意线程**写进来的：协程、ADB/Shizuku 回调、都可能在主线程重组的同时打一行。
     *
     * 所以这里刻意**不用** Compose 的 snapshot state（`mutableStateListOf`）：后台线程在 global
     * 快照里把 record 推到最新的 snapshotId，而主线程重组时手里是一份更早的 `MutableSnapshot`，
     * 再往同一个 list 里 add 就会抛
     * `Reading a state that was created after the snapshot was taken or in a snapshot that has not yet
     * been applied` —— 崩在 `add` 里，栈顶看起来和日志毫无关系。`StateFlow` 本身线程安全，
     * 日志页用 `collectAsStateWithLifecycle` 订阅。
     */
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())

    /** What the logs page draws: the same list, replaced on every new line. */
    val logs: StateFlow<List<LogEntry>> = _logs

    private val TIME_FORMAT = LocalTime.Format {
        hour(); char(':'); minute(); char(':'); second(); char('.'); secondFraction(3)
    }

    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)

    fun w(tag: String, message: String) = log(LogLevel.WARNING, tag, message)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable)

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        platformLog(level, tag, message, throwable)
        val text = throwable?.let { "$message\n${it.stackTraceToString()}" } ?: message
        val entry = LogEntry(level, tag, text, currentTimeMillis())
        _logs.update { current ->
            val kept = if (current.size >= MAX_ENTRIES) current.subList(current.size - MAX_ENTRIES + 1, current.size) else current
            kept + entry
        }
    }

    /**
     * The lines collected so far, newest last. For a one-off read (copying, exporting); the logs page
     * subscribes to [logs] instead so it also redraws when something is logged while it is open.
     */
    fun getLogs(): List<LogEntry> = _logs.value

    data class LogEntry(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val timestamp: Long,
    ) {
        /** Local wall-clock time, e.g. `13:07:57.528`. */
        fun getFormattedTime(): String =
            TIME_FORMAT.format(
                Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).time
            )

        /** The line shown on the logs page and copied to the clipboard. */
        fun format(): String = "[${getFormattedTime()}] $level $tag: $message"
    }

    enum class LogLevel(val color: Color) {
        INFO(Color.Green),
        WARNING(Color.Yellow),
        ERROR(Color.Red)
    }
}
