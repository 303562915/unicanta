package io.github.samolego.canta.util

/**
 * Desktop / web: 没有落盘。
 *
 * 这两个平台的自更新不走 `pm install`，进程不会在安装中途被系统杀掉，内存日志已经够用；
 * 写文件也不是随便哪个目标都有的能力（wasm 没有文件系统）。
 */
internal actual fun attachLogSink(path: String) = Unit

internal actual fun persistLogLine(level: LogUtils.LogLevel, tag: String, message: String) = Unit

internal actual fun readPersistedLogLines(): List<String> = emptyList()
