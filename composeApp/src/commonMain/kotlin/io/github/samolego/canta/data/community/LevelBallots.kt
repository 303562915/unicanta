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
    /** 这一维度全部票数（各项相加）。 */
    fun totalOf(dimension: String): Int = votes[dimension]?.values?.sum() ?: 0

    /** 某一项在本维度里的百分比，四舍五入到整数；本维度还没人投时是 0。 */
    fun percent(dimension: String, choice: String): Int {
        val total = totalOf(dimension)
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
