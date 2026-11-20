package io.github.samolego.canta.ui.navigation

sealed class Screen(val route: String) {
    data object Main : Screen("main")
    data object Logs : Screen("logs")
    data object Settings : Screen("settings")

    /** The app's own page: version, the switches that shape how it behaves, and where it lives. */
    data object About : Screen("about")
    data object Presets : Screen("presets")
    data object Rules : Screen("rules")
    data object Tweaks : Screen("tweaks")

    /**
     * Everything this install wrote and everything other people wrote back: the comments it left
     * on plans and app pages, and the replies under them. Each row opens the page it lives on.
     */
    data object MyComments : Screen("my-comments")

    /** Open-source apps worth installing; the cards only link out to their project pages. */
    data object Recommendations : Screen("recommend")
    data object Onboarding : Screen("onboarding")

    /**
     * One community plan in full: its packages and comments.
     *
     * The id is a path argument, so [routeFor] is the only place that builds the URL and the
     * `planId` segment is what [route] declares.
     */
    data object PlanDetail : Screen("plan/{planId}") {
        /** Concrete route for plan [id]; pairs with the `planId` argument of [route]. */
        fun routeFor(id: Long) = "plan/$id"
    }

    /**
     * One app's own page: what it is, whether it can be removed, and its reviews.
     *
     * The package name is a path argument, so [routeForApp] is the only place that builds
     * the URL and the `package` segment is what [route] declares.
     */
    data object AppDetail : Screen("app/{package}") {
        /** Concrete route for [packageName]; pairs with the `package` argument of [route]. */
        fun routeForApp(packageName: String) = "app/$packageName"
    }

    /**
     * The corrected descriptions of one app: what people proposed, how the votes stand, and the two
     * numbers a rewrite has to reach before the page shows it instead of the published line.
     *
     * The package is a path argument again, so [routeForApp] is the only place that builds the URL.
     */
    data object DescriptionFix : Screen("app/{package}/fixes") {
        /** Concrete route for [packageName]'s corrections. */
        fun routeForApp(packageName: String) = "app/$packageName/fixes"
    }
}
