package io.github.samolego.canta.data.community

import kotlinx.serialization.Serializable

/**
 * 一条「这个包该改成什么级别」的提案，以及大家怎么投的。
 *
 * 名单是按**包名**定级别的，它不知道某个 ROM 往同一个包里塞了别的东西——荣耀上的
 * `com.qualcomm.location` 就是紧急呼叫定位服务，名单却写着「推荐卸载」。描述修正修的是那行字，
 * 这条修的是**徽标**，而徽标对应的是「全选」之后会不会一键删掉系统服务，所以门槛不一样（见 [needed]）。
 *
 * 与 [DescriptionFix] 一样的约定：`id = 0` 表示后端没给号，字段全可选，老答复仍然读得懂。
 * 区别在 [applied] 的含义——那边是"后端已经让这条生效"，这边是"票够了，等着工作流把它合进
 * 名单仓库"（客户端真正能看到新级别，是下一次从 GitHub 拉到修正表之后）。
 */
@Serializable
data class LevelFix(
    val id: Long = 0L,
    /** 提议改成什么：`Recommended` / `Advanced` / `Expert` / `Unsafe` / `System`，就是名单那套词。 */
    val removal: String = "",
    /** 为什么，按界面语言。 */
    val notes: Map<String, String> = emptyMap(),
    /** 签名用的显示名。 */
    val author: String = "",
    /** 名字背后的身份号：「我提的」「一人一票」都按它算。 */
    val authorId: String = "",
    val createdAt: String = "",
    /** 认同这条的票数。 */
    val agrees: Int = 0,
    /** 不认同的票数。 */
    val disagrees: Int = 0,
    /** 本机投的哪边：true 认同、false 不认同、null 还没投。 */
    val mine: Boolean? = null,
    /** 后端已判定通过（⇒ 等着被工作流合并进名单仓库）。 */
    val applied: Boolean = false,
    /** 同包有更新提案顶上，或者已经合并下发过了。 */
    val superseded: Boolean = false,
    /** 还需多少认同票才能通过；后端给的数字，不是自己猜的。 */
    val needed: Int = VOTES_NEEDED,
    /** 认同票要占全部票的比例；后端给的数字。 */
    val shareNeeded: Float = SHARE_NEEDED,
) {
    val votes: Int get() = agrees + disagrees

    /** 认同占比，没人投时为 null。 */
    val share: Float? get() = if (votes == 0) null else agrees.toFloat() / votes

    /** 票数是否已经够让这条通过。 */
    val satisfied: Boolean get() = agrees >= needed && (share ?: 0f) >= shareNeeded

    /** 还差几票，够了就是 0。 */
    val missingVotes: Int get() = (needed - agrees).coerceAtLeast(0)

    /** 用当前界面语言取理由；没有该语言时退回 [fallback]。 */
    fun note(language: String, fallback: String = "en"): String =
        notes[language]?.takeIf { it.isNotBlank() } ?: notes[fallback].orEmpty()

    companion object {
        /** 后端默认门槛；答复里带 `needed` 时以答复为准。 */
        const val VOTES_NEEDED = 30

        /** 后端默认的认同占比；答复里带 `share` 时以答复为准。 */
        const val SHARE_NEEDED = 0.70f
    }
}

/** [PlanRepository.voteLevelFix] 的答复：投完之后的计数与是否通过。 */
@Serializable
data class LevelFixVote(
    val agrees: Int = 0,
    val disagrees: Int = 0,
    val applied: Boolean = false,
    val needed: Int = LevelFix.VOTES_NEEDED,
)