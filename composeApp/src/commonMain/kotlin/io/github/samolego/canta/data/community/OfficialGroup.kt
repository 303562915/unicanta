package io.github.samolego.canta.data.community

/**
 * 官方的内测群。
 *
 * 它要对**还没进群的人**也显示在消息页最上面，所以不能只靠会话列表——没加入的人列表里没有这一条。
 * 邀请码存在后端（`conversations.invite_code`），入群走现成的 `group_invite_join`（幂等，已经在群里
 * 也返回同一个会话号）。群改名或者换了邀请码，改这里三行就够。
 *
 * 刻意不带人数：这个群是内测的，人数还少，谁都不该从界面上读出"就这几个人"。
 */
object OfficialGroup {
    /** 群名：消息页上那一行显示的就是它。 */
    const val TITLE: String = "UniCanta 灰度测试群"

    /** 邀请码（`conversations.invite_code`）。 */
    const val INVITE_CODE: String = "7115b5e672aa"

    /** 一行说明：已加入时显示这个，未加入时显示「点一下加入」。 */
    const val TAGLINE: String = "内测版本抢先体验，问题直接在这里说"
}
