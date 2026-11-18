package io.github.samolego.canta.ui.navigation

sealed class Screen(val route: String) {
    data object Main : Screen("main")
    data object Logs : Screen("logs")
    data object Settings : Screen("settings")
    data object Presets : Screen("presets")
    data object Rules : Screen("rules")
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
}
