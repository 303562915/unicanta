package io.github.samolego.canta.util

import kotlinx.serialization.Serializable

/**
 * 一次「工具页」操作在这台机器上的结果：读了什么、跑了什么、成了没有。
 *
 * 为什么需要它：「音频白名单用不了」这类反馈只能靠人描述屏幕，而真正决定成败的是那台机器上的
 * 原生事实——有没有那个配置文件、开关属性叫什么、云控入口在不在、ROM 是哪个。这些正好是页面
 * 上那栏「诊断信息」里的东西，所以这里把它原样带上。
 *
 * **不带的东西**：包列表（`pm list packages` 的结果一行都不传，只传两个计数）、token、账号、
 * 任何文件内容。诊断行只含 `getprop` 的音频/场景属性、两个 global 设置的值、几个目录的文件名、
 * 以及配置文件开头 400 字节（那是 ROM 的公共配置，不含个人信息）。
 *
 * @param tweak 工具卡的 id（`xiaomi-audio-whitelist`），页面级的工具用页面名。
 * @param stage `read`（只读刷新）、`run`（执行）、`restore`（恢复）。
 * @param outcome `ok` / `failed` / `unsupported`（本机没有这套配置）/ `blocked`（没权限）。
 * @param code 具体失败的代号：`SET_FAIL`、`TIMEOUT`、`NO_CONFIG`、`NO_PRIVILEGE` …
 * @param level 开关属性的原始值，空表示这台机器没有这个属性。
 * @param configPath 找到的配置文件路径，空表示没找到。
 * @param entries 名单里被点名的应用数（只传数量，不传名字）。
 * @param installed 其中本机装了的数量。
 * @param diagnostics 诊断行原文，逐行拼起来；不含包列表。
 * @param output 脚本自己打印的最后一段输出，失败时是唯一的现场。
 */
@Serializable
data class TweakReport(
    /** 载荷版本；不给默认值，保证它永远在线上。 */
    val v: Int,
    /** 客户端的平台标签："android"、"desktop" 或 "web"。 */
    val platform: String,
    val tweak: String,
    val stage: String,
    val outcome: String,
    val code: String = "",
    val level: String = "",
    val configPath: String = "",
    val entries: Int = 0,
    val installed: Int = 0,
    val diagnostics: String = "",
    val output: String = "",
    val cantaVersion: String = "",
    val device: FailureReportDevice,
) {
    companion object {
        const val STAGE_READ = "read"
        const val STAGE_RUN = "run"
        const val STAGE_RESTORE = "restore"

        const val OUTCOME_OK = "ok"
        const val OUTCOME_FAILED = "failed"
        const val OUTCOME_UNSUPPORTED = "unsupported"
        const val OUTCOME_BLOCKED = "blocked"
    }
}
