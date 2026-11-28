package io.github.samolego.canta.ui.component

import io.github.samolego.canta.data.app.AppBadgeInfo

/**
 * 这一档落到用户手上会发生什么——一句话，先说最要紧的那件事。
 *
 * 与 [AppBadgeInfo.description]（名单自己的原话）并存，不是替代：原话解释的是**这一档的定义**
 * （"breaks obscure or minor parts of functionality"），这句话说的是**我这台机器会怎样**
 * （"会少掉一些次要功能""有卡在开机的风险"）。
 *
 * 三处共用同一份文案，避免又一次"列表上写着会破坏功能、投票页只写着专家"：
 * 应用详情页的徽标说明、级别修正页的档位选择器、修正历史里的每一条提案。
 *
 * 中文硬编码，与这两页其余文案一致；要本地化时连同那两页一起改，不要只改这里。
 */
fun badgeRiskSummary(level: AppBadgeInfo): String = when (level) {
    AppBadgeInfo.RECOMMENDED -> "基本不影响使用：删了需要时能装回来。"
    AppBadgeInfo.ADVANCED -> "会少掉一些次要功能（输入法、相册、桌面这类），通常有替代品。"
    AppBadgeInfo.EXPERT -> "会破坏常用功能（通知、定位、某个系统服务等），但一般不会卡在开机。"
    AppBadgeInfo.UNSAFE -> "会破坏系统关键部分，有卡在开机（无限重启）的风险。"
    AppBadgeInfo.SYSTEM -> "系统预装应用，删了这台机器就少一块，不建议动。"
}
