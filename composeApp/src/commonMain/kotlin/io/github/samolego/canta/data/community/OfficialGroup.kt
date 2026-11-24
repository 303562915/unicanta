package io.github.samolego.canta.data.community

/**
 * 一个官方群。
 *
 * 它要对**还没进群的人**也显示在消息页最上面，所以不能只靠会话列表——没加入的人列表里没有这一条。
 * 邀请码存在后端（`conversations.invite_code`），入群走现成的 `group_invite_join`（幂等，已经在群里
 * 也返回同一个会话号）。群改名或者换了邀请码，改下面 [OfficialGroups.entries] 里那几行就够。
 *
 * 刻意不带人数：这些群是官方群，人数不该从界面上读出来。
 */
data class OfficialGroupEntry(
    /** 群名，就是 `conversations.title`；消息页上那一行显示它。 */
    val title: String,
    /** 邀请码（`conversations.invite_code`）。 */
    val inviteCode: String,
    /** 已加入时那一行下面的说明。 */
    val tagline: String,
    /** 头像圆里那一个字。 */
    val badge: String,
)

/**
 * 所有官方群，按消息页上的先后顺序排。
 *
 * 目前两个：内测灰度群（发布公告也发在这里）和 Bug 反馈群（只收问题，AI 会自己读）。
 * 服务端对应的行：灰度群 `conversations.id = 12`，Bug 反馈群 `id = 18`（2026-11-24 建）。
 */
object OfficialGroups {

    val entries: List<OfficialGroupEntry> = listOf(
        OfficialGroupEntry(
            title = "UniCanta 灰度测试群",
            inviteCode = "7115b5e672aa",
            tagline = "内测版本抢先体验，问题直接在这里说",
            badge = "测",
        ),
        OfficialGroupEntry(
            title = "UniCanta Bug 反馈",
            inviteCode = "ceaeb0a30182",
            tagline = "遇到问题就贴在这里：机型、系统版本、怎么复现",
            badge = "B",
        ),
    )

    /** 群名集合：排序、钉在最上面、判断某一行是不是官方群，都用它。 */
    val titles: Set<String> = entries.mapTo(LinkedHashSet()) { it.title }

    /** 官方群在列表里的排列位次；不是官方群返回 [Int.MAX_VALUE]（保持原有顺序排在后面）。 */
    fun rankOf(title: String): Int =
        entries.indexOfFirst { it.title == title }.let { if (it < 0) Int.MAX_VALUE else it }

    fun byTitle(title: String): OfficialGroupEntry? = entries.firstOrNull { it.title == title }
}
