package io.github.samolego.canta.ui.navigation

sealed class Screen(val route: String) {    data object Main : Screen("main")
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

    /**
     * How the app looks: the accent picked from the twelve, and which dark mode to draw it in.
     *
     * A page of its own rather than a card in the settings, the way the reference app does it:
     * the swatches want the whole width, and the choice is one a user comes back to.
     */
    data object Theme : Screen("theme")

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
     * One app's page, opened on one comment: what a mention in the inbox leads to.
     *
     * A separate route rather than an argument on [AppDetail], because a package name is a path
     * segment with dots in it and an optional query on top of that is a URL a reader cannot read.
     */
    data object AppComment : Screen("app/{package}/comment/{commentId}") {
        fun routeFor(packageName: String, commentId: Long) = "app/$packageName/comment/$commentId"
    }

    /**
     * One plan's page, opened on one comment: the other half of what a mention leads to.
     */
    data object PlanComment : Screen("plan/{planId}/comment/{commentId}") {
        fun routeFor(planId: Long, commentId: Long) = "plan/$planId/comment/$commentId"
    }

    /**
     * One uploader and everything they put up: the answer to "should I trust their other plans too",
     * which is what a reader wonders after one of them worked.
     *
     * The owner is an identity number, a path argument like every other page's key.
     */
    data object Uploader : Screen("user/{owner}") {
        fun routeFor(owner: String) = "user/$owner"
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
