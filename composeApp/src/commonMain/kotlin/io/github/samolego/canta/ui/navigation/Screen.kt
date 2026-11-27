package io.github.samolego.canta.ui.navigation

sealed class Screen(val route: String) {    data object Main : Screen("main")
    data object Logs : Screen("logs")

    /**
     * 新设置页：外观、通用开关、权限、底栏槽位、启动密码与高级项，布局照关于页的卡片流。
     *
     * 从 [Mine] 进；底栏不再直接进这里。
     */
    data object Settings : Screen("settings")

    /**
     * "我的"：这个安装自己的身份，以及只有它自己知道的那些东西（评论、方案）。
     *
     * 底栏那个齿轮进的是这一页 —— 用户说的"把设置改为我的"改的就是这个入口。
     */
    data object Mine : Screen("mine")

    /**
     * 个人主页：自己的名字、头像和身份号，从 [Mine] 上那张身份卡点进来。
     *
     * 单独一页是因为"换头像"要走相册、压缩、上传图床三步，每一步都可能失败并要求一句人话；
     * [Mine] 是入口清单，不适合放这套流程。
     */
    data object Profile : Screen("profile")

    /** The app's own page: version, the switches that shape how it behaves, and where it lives. */
    data object About : Screen("about")

    /**
     * 卸载记录：一份份「方案」——本机的卸载记录算一份，导入进来的每一份各算一份。
     *
     * 可以导出、导入、点开看包含哪些包、整份装回来。从 [Settings] 那一页进，不占底栏。
     */
    data object DeletionList : Screen("deletion-list")
    data object Rules : Screen("rules")
    data object Tweaks : Screen("tweaks")

    /**
     * 音频白名单：HyperOS / MIUI 按应用切低频、压音量的那一套，一页看完名单、一键开合。
     *
     * 从 [Tweaks] 的小米分组进。单独一页而不是卡片弹窗，是因为名单本身就有几十行：弹窗只能读，
     * 页面能筛、能滚、能把本机装了的排到前面。
     */
    data object Audio : Screen("audio")

    /**
     * Everything this install wrote and everything other people wrote back: the comments it left
     * on plans and app pages, and the replies under them. Each row opens the page it lives on.
     */
    data object MyComments : Screen("my-comments")

    /** Open-source apps worth installing; the cards only link out to their project pages. */
    data object Recommendations : Screen("recommend")

    /**
     * 一个推荐条目的整页：图标、作者、许可证、介绍、这台手机还装着的那些被替换的包，以及下面
     * 那条能回话的评论线程。
     *
     * 为什么是整页而不是 [Recommendations] 上的弹窗：评论要读、要回、要滚，弹窗高度有限、外面
     * 还压着一层遮罩，回一句要跟滚动条抢；[PlanDetail] 和 [AppDetail] 那种页面才是"可以交流的"
     * 样子。
     *
     * 路径里放「来源 + id」，而不是把项目地址塞进来：地址里带 `/`（`https://github.com/x/y`），
     * 当作一段路径参数取出来只剩前半截，评论就挂到别的 target 上了。`source` 只有
     * `app`（内置目录条目的字符串 id）和 `suggestion`（社区条目的数字 id）两个值——两边的 id
     * 可能撞名（一个是 `cyime`，一个是 `7`），所以来源必须一起带。
     */
    data object RecommendDetail : Screen("recommend/{source}/{key}") {
        /** 具体路由：`recommend/app/cyime`、`recommend/suggestion/7`。 */
        fun routeFor(source: String, key: String) = "recommend/$source/$key"
    }

    /**
     * 一条机场的整页：名字、类型、六格参数、投票、打分、订阅链接，以及下面的评论区。
     *
     * 与 [RecommendDetail] 同一个理由：机场详情原来也是弹窗，评论塞在最下面一截，读长一点的
     * 线路吐槽就得跟弹窗自己的滚动抢。
     */
    data object AirportDetail : Screen("airport/{airportId}") {
        /** 具体路由：`airport/7`，配对 `airportId` 那个参数。 */
        fun routeFor(id: Long) = "airport/$id"
    }

    /**
     * 社区帖子：一页自己的讨论区——发帖、看帖、回帖、点赞，带本地草稿。
     *
     * 从推荐页右上角的「帖子」进。不挂在推荐页的顶部分段里：那是一张要滚很久的列表，
     * 还要自己带一个编辑器，塞进推荐页会让那一页同时是目录、投票、机场和论坛。
     */
    data object Posts : Screen("posts")

    /**
     * 写新话题：从 [Posts] 里单独开出来的一页，**不挂底栏**。
     *
     * 以前编辑器是 [Posts] 内部的一个 pane —— 点「发帖」后整页在原地换成编辑态，
     * 底栏还在下面。写东西的时候底栏那一排图标既没用又占地方，手指还容易误触走掉；
     * 所以改成一条独立路由，离开时 [Posts] 的列表就留在它自己的返回栈里。
     *
     * 草稿仍然只在 [Posts] 那一份（`deps.postDrafts`），这里不另存。
     */
    data object PostEditor : Screen("posts/new")

    /**
     * 开源周榜：帖子页顶上那张卡片的详情——整期海报 + 这一周的十条榜单。
     *
     * 从 [Posts] 列表顶部的卡片进（不占底栏）。数据写死在
     * `io.github.samolego.canta.ui.screen.WeeklyRankScreen.kt` 里的 `weeklyRankWeek47`：
     * 一期周榜是一份快照，不是后端能实时算出来的东西，所以这一页不联网，也不用等。
     */
    data object WeeklyRank : Screen("weekly-rank")

    /**
     * 商城：积分怎么来、怎么花。每天领分、积分商城（谁都能挂）、兑换中心（CDK 码池）。
     *
     * 它与机场分开：机场那一页只管投票榜单，钱和兑换都在这。
     */
    data object Store : Screen("store")

    /**
     * The messages page: what other people did around this install, the chats it is in, and the
     * way into both.
     *
     * It is a bar page of its own rather than a tab of [MyComments]: news about other people is
     * something a reader checks while doing nothing else, and it was buried behind the page about
     * this install's own writing.
     */
    data object Messages : Screen("messages")

    /**
     * The four kinds of news in full: replies, comments under my plans, hearts, and endorsements,
     * with the list of who started following this install beside them.
     */
    data object Interactions : Screen("interactions")

    /**
     * One conversation — a direct message or a group — in full.
     *
     * The id is a path argument, so [routeFor] is the only place that builds the URL.
     */
    data object Chat : Screen("chat/{conversationId}") {
        /** Concrete route for conversation [id]; pairs with the `conversationId` argument. */
        fun routeFor(id: Long) = "chat/$id"
    }

    /**
     * 同一个聊天页，但**开门那一刻就知道这是群**。
     *
     * 为什么需要另一条路由：聊天页写进返回栈之后，形参 `isGroup` 就定死了——它自己那一趟
     * `conversation_detail` 回来只更新内部的 `grouped`，不会回头改形参。而从消息页进来时，路由
     * 只带会话号，群身份要靠聊天页再去问一次 `conversations_for`，问回来之前一律当私聊：群里
     * 别人的消息一条头像都没有，顶栏还写着「私聊」（截图里就是这个）。
     *
     * 消息页手上本来就有这一行（`Conversation.isGroup`），所以给它一个能把这个结论带进来的入口：
     * 结论在**进页那一刻**就定下，不必等第二个请求。老路由 `chat/{id}` 保留给别的入口
     * （搜索结果之外没有别的地方带得出结论），它只是少了这一份提前量。
     */
    data object ChatGroup : Screen("chat-group/{conversationId}") {
        /** 群聊的具体路由：`group` 那一段就是「这是群」这个结论。 */
        fun routeFor(id: Long) = "chat-group/$id"
    }

    /** 发起群聊：挑人、起名、建一个群。不是 `group/…` 底下的一段，免得和上面的群资料抢路由。 */
    data object CreateGroup : Screen("group-new")

    /**
     * 一个群的资料：成员、改名、加人、退群。
     *
     * The id is a path argument, so [routeFor] is the only place that builds the URL.
     */
    data object GroupInfo : Screen("group/{conversationId}") {
        /** Concrete route for conversation [id]. */
        fun routeFor(id: Long) = "group/$id"
    }

    /** 这个身份折叠起来的那些群，从消息页「折叠的群聊」进去；没折叠的群留在消息页主列表里。 */
    data object Groups : Screen("groups")

    /**
     * 某人的关注与粉丝两个列表，两段路径参数分别是人和要打开的那一栏。
     *
     * 一栏一份路由而不是 query：`followers` / `following` 是两种列表，读 URL 就该知道是哪一种。
     */
    data object FollowList : Screen("follow/{owner}/{tab}") {
        /** Concrete route for person [owner], on the followers or the following list. */
        fun routeFor(owner: String, followers: Boolean) =
            "follow/$owner/${if (followers) "followers" else "following"}"
    }

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

    /**
     * 「这个包该被标成什么级别」：提案、投票，票够之后由工作流合并进名单仓库。
     *
     * 与 [DescriptionFix] 分开一条路由：两件事的对象不同（一个改说明、一个改徽标），
     * 门槛不同，生效方式也不同（说明直接下发、级别要等工作流），合在一页只会让人分不清
     * "投完了到底什么时候生效"。
     */
    data object LevelFix : Screen("app/{package}/level") {
        /** Concrete route for [packageName]'s level corrections. */
        fun routeForApp(packageName: String) = "app/$packageName/level"
    }
}
