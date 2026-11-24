package io.github.samolego.canta.util

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 界面上所有时间一律按**北京时间**（Asia/Shanghai）显示。
 *
 * 后端盖的全是 UTC（`2026-11-20T10:00:00.000000Z`，或者 PostgREST 那种带 `+00:00` 的写法）。
 * 以前列表里的 `HH:mm` 是直接切字符串切出来的，于是同一条"刚发的消息"在北京显示成早了八小时的
 * 时间——用户看到的是"我下午发的，列表写上午"。
 *
 * 这里集中处理三件事，别在各页面各写一份：
 *  * 解析：容忍 `Z` / `+00:00` / 带微秒的写法，解析不了就返回 null（调用方画空白，不画猜的时间）；
 *  * 换算：`Instant` → 北京时区的 `LocalDateTime`；
 *  * 格式化：[clock]、[stamp]、[fullStamp] 三种定长写法，以及列表 / 日期分隔条用的 [listStamp]、[dayLabel]。
 *
 * 日期一律走 `LocalDate.toString()`（`yyyy-MM-dd`）再切片，不碰 `Month` 那套 API：那个在
 * kotlinx-datetime 0.7 之后改名过（`monthNumber` → `month.number`），切片写法跟着版本走不了样。
 *
 * 为什么不用系统时区：[ChatMessage] / [Conversation] 里的时间是服务端的一个瞬间，没有"谁的本地
 * 时间"这回事；这一版只有中文社区在用，统一北京时间比"跟着手机设置漂"更好解释。
 */
@OptIn(ExperimentalTime::class)
object BeijingTime {

    /** 北京时间：UTC+8，没有夏令时。 */
    val zone: TimeZone = TimeZone.of("Asia/Shanghai")

    /** 解析后端那串 ISO-8601；不是时间就 null。`+00:00` 尾缀换成 `Z`，微秒和纳秒都由解析器吃掉。 */
    fun instantOf(iso: String): Instant? {
        val text = iso.trim()
        if (text.isEmpty()) return null
        return runCatching {
            Instant.parse(if (text.endsWith("+00:00")) text.dropLast("+00:00".length) + "Z" else text)
        }.getOrNull()
    }

    /** `HH:mm`（北京）。解析不了返回空串。 */
    fun clock(iso: String): String {
        val at = instantOf(iso) ?: return ""
        val time = at.toLocalDateTime(zone).time
        return two(time.hour) + ":" + two(time.minute)
    }

    /** `MM-dd HH:mm`（北京）。 */
    fun stamp(iso: String): String {
        val at = instantOf(iso) ?: return ""
        val local = at.toLocalDateTime(zone)
        return local.date.toString().substring(5) + " " + two(local.hour) + ":" + two(local.minute)
    }

    /** `yyyy-MM-dd HH:mm`（北京）。 */
    fun fullStamp(iso: String): String {
        val at = instantOf(iso) ?: return ""
        val local = at.toLocalDateTime(zone)
        return local.date.toString() + " " + two(local.hour) + ":" + two(local.minute)
    }

    /**
     * 列表右侧那一格时间，照聊天软件的习惯从近到远换写法：
     * 半分钟内「刚刚」、一小时内「N分钟前」、今天「HH:mm」、昨天「昨天 HH:mm」、今年「MM-dd」、
     * 更早「yyyy-MM-dd」。解析不了返回空串。
     */
    fun listStamp(iso: String, now: Instant = Clock.System.now()): String {
        val at = instantOf(iso) ?: return ""
        val local = at.toLocalDateTime(zone)
        val current = now.toLocalDateTime(zone)
        val minutes = (now - at).inWholeMinutes
        if (minutes in 0..0) return "刚刚"
        if (minutes in 1 until 60) return "${minutes}分钟前"
        val clock = two(local.hour) + ":" + two(local.minute)
        if (local.date == current.date) return clock
        val days = current.date.toEpochDays() - local.date.toEpochDays()
        if (days == 1L) return "昨天 $clock"
        // 两到六天前用「周日」这种写法：一周之内"上周日"比"11-22"更好认，也更像聊天软件。
        if (days in 2L..6L) return weekdayOf(local.date.dayOfWeek)
        val date = local.date.toString()
        return if (local.date.year == current.date.year) date.substring(5) else date
    }

    /** `周一`…`周日`：`DayOfWeek` 的序号从周一开始（1..7）。 */
    private fun weekdayOf(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        else -> "周日"
    }

    /**
     * 聊天里的日期分隔条：「今天」「昨天」「M月d日」「yyyy年M月d日」（都是北京时间）。
     *
     * 只给一行灰字用，所以不带补零的月份——"11月5日"比拼成"11月05日"像人话。
     */
    fun dayLabel(iso: String, now: Instant = Clock.System.now()): String {
        val at = instantOf(iso) ?: return ""
        val local = at.toLocalDateTime(zone)
        val current = now.toLocalDateTime(zone)
        val days = current.date.toEpochDays() - local.date.toEpochDays()
        val date = local.date.toString()
        val month = date.substring(5, 7).toInt()
        val day = date.substring(8).toInt()
        return when {
            days == 0L -> "今天"
            days == 1L -> "昨天"
            local.date.year == current.date.year -> "${month}月${day}日"
            else -> "${local.date.year}年${month}月${day}日"
        }
    }

    /** 北京时区下这条时间落在哪一天（判断"要不要画日期分隔条"用）；解析不了返回空串。 */
    fun dayKey(iso: String): String {
        val at = instantOf(iso) ?: return ""
        return at.toLocalDateTime(zone).date.toString()
    }

    private fun two(value: Int): String = if (value < 10) "0$value" else value.toString()
}
