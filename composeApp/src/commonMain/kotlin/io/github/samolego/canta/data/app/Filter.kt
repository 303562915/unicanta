package io.github.samolego.canta.data.app

import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.filter_any
import io.github.samolego.canta.generated.resources.filter_unclassified
import io.github.samolego.canta.generated.resources.filter_user
import org.jetbrains.compose.resources.StringResource

/**
 * 应用列表的一个分类筛选。
 *
 * @param label 这一类的名字，翻译在打印的地方做。
 * @param shouldShow 判断一个应用算不算这一类的。
 * @param badgeInfo 这一类的徽标；为 null 表示"不按徽标分"（全部 / 用户应用 / 未分类）。
 */
class Filter(
    val label: StringResource,
    val shouldShow: (AppInfo) -> Boolean,
    val badgeInfo: AppBadgeInfo? = null
) {
    companion object {
        /** 全部应用，不做任何筛选。 */
        val any: Filter = Filter(label = Res.string.filter_any, shouldShow = { true })

        /** 只有用户自己装的应用（非系统）。 */
        val user = Filter(label = Res.string.filter_user, shouldShow = { app -> !app.isSystemApp })

        /** 名单还没给过级别、也没算出徽标的那些。 */
        val unclassified =
            Filter(label = Res.string.filter_unclassified, shouldShow = { app -> app.badgeInfo == null })

        /**
         * 所有可选的分类，按列表里出现的顺序：全部、用户、未分类，然后是四个危险级别。
         *
         * SYSTEM 不在里面：它本来就由顶栏那个「只看系统 / 全部」按钮管，混进分类里会出现
         * 两个入口管同一件事。
         */
        val availableFilters: List<Filter> = buildList {
            add(any)
            add(user)
            add(unclassified)
            AppBadgeInfo.entries
                .filter { it != AppBadgeInfo.SYSTEM }
                .forEach { entry ->
                    add(
                        Filter(
                            label = entry.nameRes,
                            shouldShow = { app -> app.badgeInfo == entry },
                            badgeInfo = entry,
                        )
                    )
                }
        }
    }
}