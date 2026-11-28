package io.github.samolego.canta.util

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable

/**
 * Local crash reports. A report is plain text with app version, platform, OS version,
 * a timestamp and the stack trace; nothing else. It is only ever stored on the device,
 * and leaves it only if the user chooses to open a GitHub issue with it.
 */
object CrashReport {
    /** Largest stored report, in characters. */
    const val MAX_LENGTH = 16 * 1024

    /** Longest allowed issue URL; browsers and GitHub reject much more than this. */
    const val MAX_URL_LENGTH = 7000

    private const val NEW_ISSUE_URL = "https://github.com/303562915/unicanta/issues/new"
    private const val TRUNCATED_MARKER = "\n... (truncated)"

    /** Builds the report text, cutting the stack trace so the result fits in [MAX_LENGTH]. */
    fun format(
        appVersion: String,
        platform: String,
        osVersion: String,
        timestamp: String,
        stackTrace: String,
    ): String {
        val header = "Canta $appVersion\nPlatform: $platform\nOS: $osVersion\nTime: $timestamp\n\n"
        return truncate(header + stackTrace, MAX_LENGTH)
    }

    /** The URL that opens a prefilled GitHub issue; the report is cut so the URL stays within [MAX_URL_LENGTH]. */
    fun issueUrl(report: String): String {
        val title = "Crash: " + (report.lineSequence().firstOrNull { it.startsWith("Caused by") || it.contains("Exception") || it.contains("Error") }
            ?.trim()?.take(80) ?: "uncaught exception")
        val prefix = "$NEW_ISSUE_URL?title=${title.encodeURLParameter()}&body="
        val budget = MAX_URL_LENGTH - prefix.length
        // Percent-encoding can grow the text up to 3x; shrink until the encoded body fits.
        var length = minOf(report.length, budget)
        var body = report.take(length).encodeURLParameter()
        while (body.length > budget && length > 0) {
            length = (length * budget / body.length).coerceAtMost(length - 1)
            body = report.take(length).encodeURLParameter()
        }
        return prefix + body
    }

    internal fun truncate(text: String, maxLength: Int): String =
        if (text.length <= maxLength) text else text.take(maxLength - TRUNCATED_MARKER.length) + TRUNCATED_MARKER

    /** 盘上那份文本拆开之后的四段；[stack] 是空行之后剩下的全部原文。 */
    data class Parsed(
        val version: String,
        val platform: String,
        val os: String,
        val timestamp: String,
        val stack: String,
    )

    /**
     * 把 [format] 写出去的那份文本拆回来。
     *
     * 为什么按前缀逐行认、而不是按固定行号：这份文件是**上一版应用**写的，格式可能和这一版不一样
     * （老版本没有 Platform 行）。认不出来的字段就留空，堆栈照原文带上去，别因为少一行就整条丢掉。
     */
    fun parse(report: String): Parsed {
        val lines = report.lineSequence().toList()
        fun after(prefix: String): String =
            lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim().orEmpty()
        val blank = lines.indexOfFirst { it.isBlank() }
        val stack = if (blank >= 0) lines.drop(blank + 1).joinToString("\n").trim() else report.trim()
        return Parsed(
            version = after("Canta "),
            platform = after("Platform:"),
            os = after("OS:"),
            timestamp = after("Time:"),
            stack = stack,
        )
    }

    /**
     * 这一条崩溃的签名：堆栈里第一行像异常的话。
     *
     * 后台按它分组，同一处崩溃才会归成一条（不同手机的时间、行号会飘，但异常那句话是一样的）。
     * 认不出来就退到第一行非空文本，再不行就是 "unknown"。
     */
    fun signature(stack: String): String =
        stack.lineSequence()
            .map { it.trim() }
            .firstOrNull {
                it.startsWith("Caused by") || it.contains("Exception") || it.contains("Error")
            }
            ?.take(MAX_SIGNATURE_LENGTH)
            ?: stack.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(MAX_SIGNATURE_LENGTH)
            ?: "unknown"

    /** 签名截到这么长：够认出是哪一行，又不会被一条超长 message 撑爆。 */
    private const val MAX_SIGNATURE_LENGTH = 200
}

/** Where the last crash report is kept; only the platforms with an uncaught-exception handler have one. */
interface CrashReportStore {
    /** The stored report, or `null` when the app did not crash last time. */
    fun read(): String?

    /** Deletes the stored report. */
    fun delete()
}

/**
 * 一次闪退上报到后端的内容（RPC `crash_report`）。
 *
 * 为什么要有它：2026-11-28 那次「装上就闪退」在后台一点痕迹都没有——崩溃只落在手机上那份
 * `crash_report.txt` 里，人不说、不看，就永远不知道有多少台、哪台、崩在哪。上报之前，判断影响面
 * 只能靠「更新上报量有没有断崖」这种间接推断（见当天的排查记录）。
 *
 * 内容和 [CrashReport.format] 那份文本一致：应用版本、平台、系统版本、时间、堆栈，加上机型的
 * 粗糙指纹（与 `update_report` 同一套字段，这样两张表能按机型 join）。**没有身份、没有账号、
 * 没有包列表**，所以它和更新上报一样不看「分享匿名卸载数据」那个开关。
 *
 * @param signature 堆栈里第一行像异常的那一行，截到 200 字：后台按它分组去重，
 *   不然同一处崩溃会因为时针不同变成上千条。
 * @param stack 完整堆栈原文，截到 6000 字。
 */
@Serializable
data class CrashReportPayload(
    /** 载荷版本；没有默认值，保证它在线上一定出现。 */
    val v: Int,
    val platform: String,
    val cantaVersion: String = "",
    /** 系统版本那一行（`Android 15 (SDK 35)`）。 */
    val os: String = "",
    /** 客户端自己写的崩溃时间；解析不出就空着，服务端另有 received_at。 */
    val crashedAt: String = "",
    val signature: String = "",
    val stack: String = "",
    val device: FailureReportDevice,
)
