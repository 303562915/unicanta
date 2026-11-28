package io.github.samolego.canta.data.community

/**
 * 级别修正的三个维度：级别、后果、系统应用。
 *
 * 一个包只有一个级别，所以级别是四选一；「删了会怎样」和「它是不是系统应用」是另外两件事，
 * 各自二选一/是否。三者各投各的，页面上每项右边显示的是**本维度内**的百分比。
 */
object LevelBallotDimensions {
    const val LEVEL = "level"
    const val IMPACT = "impact"
    const val SYSTEM = "system"
}

/**
 * 各维度共用的「不知道」选项。
 *
 * 加它是因为三个维度都是"要么选这个、要么选那个"：一个没见过的包，用户其实分不清该标哪一档，
 * 硬选就等于往票箱里塞噪声，而且选完还会在历史里挂着。所以每个维度最后都有一项「不知道」，
 * 投了表示**我了解过、但不表态**——它和"还没投"是两件事（这里能看出自己已经表过态），
 * 但**不进任何百分比的分母**（见 [LevelBallots.percent]）：投票箱里只数确定的票，
 * 不数"我不知道"，否则一票不确定就能把 70% 的占比门槛稀释掉。
 *
 * 后端约束里这个 key 就是 `unknown`（三个维度都接受），结算时排除掉，不下发给名单。
 */
object LevelBallotChoices {
    const val UNKNOWN = "unknown"
}

/** 一条投票记录：谁、在哪个维度、投了什么、写了什么依据。 */
data class LevelBallotEntry(
    /** level / impact / system。 */
    val dimension: String,
    /** 该维度的选项 key。 */
    val choice: String,
    /** 投票人显示名；旧版客户端不带名字，这里是空串。 */
    val name: String,
    /** 级别维度选填的依据。 */
    val note: String,
    /** 投票/改票时间（ISO）。 */
    val at: String,
)

/**
 * 三个维度现在的票数与本机投了什么。
 *
 * 后端只返回有票的选项（没有票的不会出现），所以取票数一律用 [votesOf]，缺项按 0 算；
 * 页面按固定选项列表渲染，不依赖后端返回顺序。
 */
data class LevelBallots(
    /** dimension -> (choice -> votes)。 */
    val votes: Map<String, Map<String, Int>> = emptyMap(),
    /** dimension -> 本机投的 choice。 */
    val mine: Map<String, String> = emptyMap(),
    /** 级别通过所需的认同票（后端给的数字）。 */
    val needed: Int = LevelFix.VOTES_NEEDED,
    /** 级别通过所需的占比（后端给的数字）。 */
    val shareNeeded: Float = LevelFix.SHARE_NEEDED,
    /** 级别维度已经通过（⇒ 等着工作流合并进 level_fixes.json）。 */
    val applied: Boolean = false,
) {
    /** 这一维度全部票数（各项相加，含「不知道」）。 */
    fun totalOf(dimension: String): Int = votes[dimension]?.values?.sum() ?: 0

    /** 这一维度里算进百分比的分母：全票减去「不知道」（它是一句弃权，不是一个选项）。 */
    fun decidedTotalOf(dimension: String): Int {
        val all = votes[dimension] ?: return 0
        val undecided = all[LevelBallotChoices.UNKNOWN] ?: 0
        return (all.values.sum() - undecided).coerceAtLeast(0)
    }

    /**
     * 某一项在本维度里的百分比，四舍五入到整数；本维度还没人投时是 0。
     *
     * 分母不含 [LevelBallotChoices.UNKNOWN]：否则"我不知道"越多、每个真实选项的占比越低，
     * 一票弃权就能拖住 70% 的门槛。`unknown` 自己也不参与这个算法，页面直接显示它的票数。
     */
    fun percent(dimension: String, choice: String): Int {
        val total = decidedTotalOf(dimension)
        if (total <= 0) return 0
        val votes = votes[dimension]?.get(choice) ?: 0
        return (votes * 100 + total / 2) / total
    }

    /** 某一项的票数。 */
    fun votesOf(dimension: String, choice: String): Int =
        votes[dimension]?.get(choice) ?: 0

    /** 本机在这一维度投的选项；没投是 null。 */
    fun mineOf(dimension: String): String? = mine[dimension]
}
