package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.data.app.AppBadgeInfo

/**
 * 一个档位带来的两件后果，做成**标记**而不是一段话：
 * 「会不会破坏功能」和「会不会卡在开机」。
 *
 * 为什么是两枚独立的标记，而不是一句描述：这两件事的严重程度不一样（会掉通知 vs 开不了机），
 * 而且它们在列表、详情页、级别修正页要反复出现——写成一句话，每处都得重新读一遍才知道严重到哪；
 * 写成标记，扫一眼颜色就够了。文案与 [badgeRiskSummary] 是同一条判断的两种写法，改的时候一起改。
 *
 * 形状刻意和 [AppBadge] 完全一样（同样的圆角、12dp 图标、8sp 字）：这两枚和档位徽标是同一排里的
 * 一类东西，样式不一样会让人以为是两种信息。所以直接走 [AppBadgeContent] 那个内部实现，
 * 不另画一套。
 */
enum class BadgeImpact(
    /** 徽标里那枚 12dp 小图标。 */
    val icon: ImageVector,
    /** 徽标上的短字：一行里要和档位徽标并排，装不下整句。 */
    val code: String,
    /** 完整说法：无障碍朗读用。 */
    val label: String,
    /** 底色：破坏功能是橙（要修），卡在开机是红（要救）。 */
    val colour: Color,
) {
    /** 会破坏功能：掉通知、定位、某个系统服务，或者某个默认应用。 */
    BREAKS_FEATURES(
        icon = Icons.Default.Warning,
        code = "破坏功能",
        label = "会破坏常用功能",
        colour = Color(0xFF9A5B00),
    ),

    /** 可能卡在开机：删错就是开不了机（无限重启），要恢复脚本或重刷。 */
    BOOTLOOP(
        icon = Icons.Default.Block,
        code = "卡开机",
        label = "可能卡在开机",
        colour = Color(0xFFC62828),
    ),
}

/** 这一档会带来哪些后果；`Recommended` 与 `System` 不带这两枚标记。 */
fun badgeImpacts(level: AppBadgeInfo): List<BadgeImpact> = when (level) {
    AppBadgeInfo.RECOMMENDED -> emptyList()
    // 进阶：破的是次要功能（输入法、相册、桌面这类），仍然算"会破坏功能"，但不会卡开机。
    AppBadgeInfo.ADVANCED -> listOf(BadgeImpact.BREAKS_FEATURES)
    // 专家：破的是常用功能（通知、定位、系统服务），一般不会卡开机。
    AppBadgeInfo.EXPERT -> listOf(BadgeImpact.BREAKS_FEATURES)
    // 不安全：上面两件都占，而且"卡在开机"正是它和专家级的区别。
    AppBadgeInfo.UNSAFE -> listOf(BadgeImpact.BREAKS_FEATURES, BadgeImpact.BOOTLOOP)
    // 系统应用：不是"会破坏"，是"本来就在系统里"，删了少一块。这两枚标记都不贴。
    AppBadgeInfo.SYSTEM -> emptyList()
}

/**
 * 把这个档位的后果画成一排小标记，和档位徽标同一排、同一套形状。
 *
 * 没有后果的档位（推荐卸载 / 系统应用）什么都不画——空着一块比贴一枚"不会出事"更有信息量。
 */
@Composable
fun BadgeImpactTags(
    level: AppBadgeInfo,
    modifier: Modifier = Modifier,
) {
    val impacts = badgeImpacts(level)
    if (impacts.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        impacts.forEach { impact ->
            AppBadgeContent(
                label = impact.code,
                icon = impact.icon,
                color = impact.colour,
                description = impact.label,
            )
        }
    }
}
