package io.github.samolego.canta.util

import java.io.File

/** 截断后保留的行数：更新链路一天也就几十行，400 行够翻好几次更新。 */
private const val MAX_PERSISTED_LINES = 400

/** 超过这个体积才截断，避免每写一行都把整份文件读一遍。 */
private const val MAX_PERSISTED_BYTES = 256L * 1024L

private object UpdateLogFile {
    @Volatile
    var file: File? = null
}

internal actual fun attachLogSink(path: String) {
    UpdateLogFile.file = File(path)
}

/**
 * 同步写：这几行存在的意义就是「进程马上要被 `pm install` 换掉」的那一刻还留在盘上，
 * 丢给后台线程就可能在替换时还没落盘。更新链路一次只有十几行，代价可以忽略。
 */
internal actual fun persistLogLine(level: LogUtils.LogLevel, tag: String, message: String) {
    val target = UpdateLogFile.file ?: return
    // 一行的格式：`epochMillis|LEVEL|tag|message`，换行转义成 `\n`（见 LogUtils.persistedUpdateEntries）。
    val line = buildString {
        append(System.currentTimeMillis()); append('|')
        append(level.name); append('|')
        append(tag); append('|')
        append(message.replace("\r", "").replace("\n", "\\n"))
    }
    runCatching {
        target.parentFile?.mkdirs()
        target.appendText(line + "\n")
        if (target.length() > MAX_PERSISTED_BYTES) {
            target.writeText(
                target.readLines().takeLast(MAX_PERSISTED_LINES).joinToString("\n", postfix = "\n")
            )
        }
    }
}

internal actual fun readPersistedLogLines(): List<String> =
    runCatching { UpdateLogFile.file?.takeIf { it.isFile }?.readLines().orEmpty() }.getOrDefault(emptyList())
