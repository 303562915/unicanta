package io.github.samolego.canta.data.app

import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.filter_any
import io.github.samolego.canta.generated.resources.filter_disabled
import io.github.samolego.canta.generated.resources.filter_unclassified
import io.github.samolego.canta.generated.resources.filter_user
import org.jetbrains.compose.resources.StringResource

/**
 * Filter for the app list.
 * @param label Resource for the filter's name, translated where it is printed.
 * @param shouldShow Function to determine if the app should be shown.
 */
class Filter(
    val label: StringResource,
    val shouldShow: (AppInfo) -> Boolean,
    val badgeInfo: AppBadgeInfo? = null
) {
    companion object {
        /**
         * Filter to show all apps.
         */
        val any: Filter = Filter(label = Res.string.filter_any, shouldShow = { true })

        val user = Filter(label = Res.string.filter_user, shouldShow = { app -> !app.isSystemApp })

        /**
         * List of available filters.
         */
        val availableFilters: List<Filter>

        init {
            val removalFilters =
                AppBadgeInfo.entries.filter { AppBadgeInfo.SYSTEM != it }
                    .map { entry ->
                        Filter(
                            label = entry.nameRes,
                            shouldShow = { app -> app.badgeInfo == entry },
                            badgeInfo = entry
                        )
                    }.toMutableList()
            removalFilters.add(0, any)

            removalFilters.add(1, user)

            val unclassified =
                Filter(label = Res.string.filter_unclassified, shouldShow = { app -> app.badgeInfo == null })
            removalFilters.add(2, unclassified)

            val disabled = Filter(label = Res.string.filter_disabled, shouldShow = { app -> app.isDisabled })
            removalFilters.add(3, disabled)

            availableFilters = removalFilters
        }
    }
}
