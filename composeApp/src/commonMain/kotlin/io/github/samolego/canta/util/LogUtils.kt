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
 * 往磁盘上的「上次运行」日志追加一行；Android 有实现，其它平台是空的。
 *
 * 自动安装这一条链路有个先天的坑：装成功那一刻 `pm install` 会替换掉包，进程跟着被杀，
 * 内存里的日志（[LogUtils.logs]）一起没了 —— 而「某条通道报了错、最后却装上了」正是发生在
 * 那之前。所以更新链路每一行都另写一份到磁盘，日志页下次启动时读回来。
 */
internal expect fun persistLogLine(level: LogUtils.LogLevel, tag: String, message: String)

/** 上一次运行落盘的更新日志，最旧在前。没有实现/没有文件的平台返回空表。 */
internal expect fun readPersistedLogLines(): List<String>

/** 落盘文件在哪；Android 在 app 启动时注入，其它平台不注入（也不落盘）。 */
internal expect fun attachLogSink(path: String)

/**
 * In-app log shown on the logs page, mirrored to the platform log.
 */
object LogUtils {
    /** How many lines stay in memory; older ones are dropped, newest first in the list. */
    private const val MAX_ENTRIES = 2_000

    /**
     * 更新链路（检查 → 下载 → 安装）统一用的 tag。
     *
     * 这条链路横跨两个模块：`UpdateCenter` 决定什么时候查/下/装，`AndroidCantaPlatform` 真正
     * 落盘和 `pm install`。以前各打各的 tag，日志页里混在设备/包列表那几千行中间，事后想找
     * 「到底哪条通道报了错、最后又是怎么装上的」只能翻半天。现在两边都写这一个 tag，日志页
     * 的「更新」筛选按它过滤，一次只留下这条链路的时序。
     *
     * 只给更新链路用；别把它当通用 tag，否则筛选出来的东西就不是「更新」了。
     */
    const val UPDATE_TAG = "Update"

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
        // 更新链路额外落一份盘：装成功那一刻进程会被换掉，内存里的这些行活不到下一次打开。
        if (tag.startsWith(UPDATE_TAG)) persistLogLine(level, tag, text)
        val entry = LogEntry(level, tag, text, currentTimeMillis())
        _logs.update { current ->
            val kept = if (current.size >= MAX_ENTRIES) current.subList(current.size - MAX_ENTRIES + 1, current.size) else current
            kept + entry
        }
    }

    /**
     * 上一次运行落盘的更新日志（tag 带 `·上次` 后缀），最旧在前。
     *
     * 只在日志页的「更新」筛选里显示：它们和本次运行的行混在一起会看不出哪条是刚才的。
     */
    fun persistedUpdateEntries(): List<LogEntry> = readPersistedLogLines().mapNotNull { line ->
        val parts = line.split('|', limit = 4)
        if (parts.size < 4) return@mapNotNull null
        val time = parts[0].toLongOrNull() ?: return@mapNotNull null
        val level = LogLevel.entries.firstOrNull { it.name == parts[1] } ?: LogLevel.INFO
        LogEntry(
            level = level,
            tag = "${parts[2]}·上次",
            message = parts[3].replace("\\n", "\n"),
            timestamp = time,
        )
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
