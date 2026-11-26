package io.github.samolego.canta.util

/**
 * 「先起名字、先设头像，才能发言」这道闸。
 *
 * 后端把没改名的人显示成 `#10000`、没设头像的人画成一个首字母圆点。真问题不是难看，是同一个人
 * 在评论区看着像另一个人——所以发东西这件事要先过这一道：**名字**与**头像**都设过，才允许往社区
 * 里写任何一条东西（评论、方案评论、描述修正、级别修正、群聊发言、帖子、推荐、机场分享……）。
 *
 * 这一层只回答「还差什么」，不做界面：每个页面自己决定把缺口画成灰掉的发送键、一条提示还是一整条
 * 灰字输入栏（聊天页就是这么做的）。判断放在这里，是为了让这些页面**不会各写一套、各漏一处**。
 *
 * @param name 设置里存的名字（`CantaSettings.userNameFlow`）。
 * @param avatarUrl 设置里存的头像直链（`CantaSettings.avatarUrlFlow`）；空串就是没设过。
 */
data class IdentitySetup(val name: String, val avatarUrl: String) {

    /** 名字：只算真的填过东西，一串空格等于没填。 */
    val hasName: Boolean = name.trim().isNotEmpty()

    /** 头像：直链非空就算设过——`#10000` 那种首字母圆点不算。 */
    val hasAvatar: Boolean = avatarUrl.trim().isNotEmpty()

    /** 两样都齐了才能发言。 */
    val ready: Boolean get() = hasName && hasAvatar

    /** 还差哪一步：给页面拼一句话用；都齐了就是空串。 */
    val missing: String
        get() = when {
            ready -> ""
            !hasName && !hasAvatar -> "起名字、换头像"
            !hasName -> "起名字"
            else -> "换头像"
        }

    /** 灰掉的发送键上面那句话；[ready] 时调用方不会用到它。 */
    val prompt: String
        get() = if (ready) "" else "先去「我的 → 个人主页」$missing，才能发言"

    /**
     * 点了发送也发不出去时那一条 toast。
     *
     * 和 [prompt] 分开：这一句是人已经按下去之后才看见的，所以先说"没发出去"，再说差哪一步。
     */
    val refusal: String
        get() = if (ready) "" else "还没$missing，发不了言：先去「我的 → 个人主页」设好"

    companion object {
        /** 设置里还没读出来的那一版（两样都空）：读之前一律按"没设定"，不给人先发一条的空子。 */
        val UNSET: IdentitySetup = IdentitySetup("", "")
    }
}