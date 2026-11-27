package io.github.samolego.canta.ui.component

/**
 * 「刚刚 / N分钟前 / N小时前 / N天前」—— 本机时钟差，不联网、不解析时区。
 *
 * 只用来回答一个问题：**手上这份数据有多新。** 所以刻意不写「昨天 18:30」那种日历写法
 * （那套在 `relativeStamp` 里，服务的是「这条评论是什么时候发的」）。两处的用途不同，
 * 就别硬合成一个函数：数据新鲜度只关心「隔了多久」，越短越好读，超过一天就没什么可看的了。
 *
 * 调用方自己拼后缀（「更新」/空），因为两处的句子不一样：
 * 「共 3 个方案 · 4分钟前更新」 vs 「选择机型　　4分钟前」。
 */
internal fun dataAgeLabel(elapsedMillis: Long): String {
    if (elapsedMillis < 60_000L) return "刚刚"
    val minutes = elapsedMillis / 60_000L
    if (minutes < 60L) return "${minutes}分钟前"
    val hours = minutes / 60L
    if (hours < 24L) return "${hours}小时前"
    return "${hours / 24L}天前"
}
