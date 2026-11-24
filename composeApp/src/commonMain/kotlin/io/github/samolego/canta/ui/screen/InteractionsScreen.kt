package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.cache.ScreenCache
import kotlinx.serialization.Serializable
import io.github.samolego.canta.data.community.COMMENT_KIND_APP
import io.github.samolego.canta.data.community.InboxFilter
import io.github.samolego.canta.data.community.InboxItem
import io.github.samolego.canta.data.community.InboxKind
import io.github.samolego.canta.data.community.InboxSummary
import io.github.samolego.canta.data.community.MyComment
import io.github.samolego.canta.data.community.Page
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.SocialPerson
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.data.community.matching
import io.github.samolego.canta.data.community.newestInboxCommentId
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.MyCommentRow
import io.github.samolego.canta.ui.component.PersonAvatar
import io.github.samolego.canta.ui.component.ScrollToTopEffect
import io.github.samolego.canta.ui.component.UnreadRed
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBarHeight
import io.github.samolego.canta.util.BeijingTime
import kotlinx.coroutines.launch

/** 一页互动消息从每个来源各取多少行。 */
private const val INTERACTION_PER_SOURCE = 8

/** 顶栏现在只有两段：消息（标题带下拉菜单）和关注名单。 */
private enum class InteractionSection { MESSAGES, FOLLOWERS }

/** 关注那一栏的标题，左边菜单之外就它一个入口。 */
private const val FOLLOWERS_LABEL = "新关注我的"

/**
 * 消息这一栏里的分类，顺序就是下拉菜单的顺序。
 *
 * [filter] 是它落到 [InboxFilter] 的那一组；[InteractionMessages.MINE] 不在消息流里（那是「我
 * 写过的评论」，走 [PlanRepository.myCommentsPage]），所以为 null——这一栏是本地判断的，不看后端。
 */
private enum class InteractionMessages(
    val label: String,
    val filter: InboxFilter?,
    /** 菜单里的名字：顶栏那条叫「互动消息」，菜单第一项按后端的分组叫「全部消息」。 */
    val menuLabel: String = label,
) {
    ALL_MESSAGES("互动消息", InboxFilter.ALL, "全部消息"),
    REPLY("回复我的", InboxFilter.REPLY),
    PLAN_COMMENT("方案评论", InboxFilter.PLAN_COMMENT),
    HEART("赞与收藏", InboxFilter.HEART),
    MINE("我的评论", null),
}

/**
 * 这一屏上一次画出来的样子，整份进 [ScreenCache]。
 *
 * 只放画一屏要用的只读数据：三段列表、两段计数、以及翻页游标。MutableState、协程、回调、
 * 对话框开关都不在这里——那些是「这一次会话里的事」，下一次进来该从干净的状态开始。
 *
 * 所有字段捆在一起进出一趟，是因为它们来自同一屏：分开存会出现「消息是新的、关注是旧的」
 * 这种半新半旧的中间态，而这三段是同一个顶栏下的三栏。
 *
 * [mine] 为 null 表示「我的评论」这一栏还没拉过，**和「拉过、但一条都没有」不是一回事**（后者是空列表）：
 * 菜单里那个「我的评论 · N」只有在真读过之后才写得出来，没读过就该按没读过画。
 */
@Serializable
private data class InteractionsSnapshot(
    val inbox: List<InboxItem>,
    val inboxHasMore: Boolean,
    /** 屏幕上这一页是从 offset 0 取到的，恢复后翻页要接着这个游标走，不然会重取第一屏。 */
    val inboxOffset: Int,
    val summary: InboxSummary,
    val followers: List<SocialPerson>,
    val mine: List<MyComment>?,
    val mineHasMore: Boolean,
)

/**
 * 这一页缓存的槽位：按身份分。
 *
 * 身份为空（调用方是从 `DeviceIdentity.current(...)` 异步取的，第一帧就是空串）时返回空串——
 * 空键会把所有人的互动、关注、评论串进同一个槽位；[ScreenCache] 本身也会丢弃空键的写入，
 * 所以那一帧宁可没有缓存，也不能画错人。
 */
private fun interactionsCacheKey(owner: String): String =
    if (owner.isEmpty()) "" else "screen:interactions:$owner"

/**
 * 「互动消息」页：别人对我做过什么、我自己写过什么，以及谁新近关注了我。
 *
 * 三件事画在一个页面里，因为它们是同一个消息中心的三个问题：*别人对我做过什么*（回复、方案下的
 * 评论、点赞、认同）走 [PlanRepository.inboxPage]，*我自己写过什么* 走
 * [PlanRepository.myCommentsPage]，*谁刚关注了我* 走 [SocialRepository.followersOf]。顶栏标题点开
 * 是一个下拉菜单，五条分类（全部消息 / 回复我的 / 方案评论 / 赞与收藏 / 我的评论）在那里选；
 * 「我的评论」从「我的」页搬到了这里，因为它和互动是同一件事的两面：一个看别人对我做了什么，
 * 一个看我做过什么。
 *
 * 「我的评论」里的每一行点下去都带着评论 id 交给调用方，落到方案 / 应用详情页的那条评论上：
 * 那一页会把它排到评论最前面、自动展开它的回复，并滚到评论那一段——不然一百多个包的行会把答案
 * 埋到屏幕下面去。
 *
 * 名单只翻一页：消息流是一年一年长出来的历史，所以要分页（滚到底再问下一段 offset）；
 * 关注列表是当下的状态，一次给全。
 *
 * 打开消息这一栏，就等于把它们读过：第一页到货后把「最新的一条」和「后端计数时的那个瞬间」
 * 交回上层（[onInboxSeen]），红点靠那对值熄灭，而不是靠本页记住看了几行。
 */
@Composable
fun InteractionsScreen(
    repository: PlanRepository,
    social: SocialRepository,
    owner: String,
    token: String,
    /** 这台设备签评论用的名字：只是「我的评论」那一行的头像首字。 */
    author: String = "",
    seenAt: String = "",
    onInboxSeen: (newestCommentId: Long, checkedAt: String) -> Unit = { _, _ -> },
    onOpenPlanComment: (planId: Long, commentId: Long) -> Unit = { id, _ -> },
    onOpenAppComment: (packageName: String, commentId: Long) -> Unit = { pkg, _ -> },
    /** 点行头像看那个人的主页；后端没给身份号时行内不挂这个点击。 */
    onOpenProfile: (owner: String) -> Unit = {},
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 顶栏的两段；消息段里再选分类。两个状态分开记，是因为「切到关注再切回来」要回到原来的分类。
    var section by remember { mutableStateOf(InteractionSection.MESSAGES) }
    var messages by remember { mutableStateOf(InteractionMessages.ALL_MESSAGES) }
    // 上一屏留下的那一份先摆上：这一页每次被导航进来都是新的 Composable 实例，`remember` 跟着一起
    // 没了，所以快照放在进程级的 [ScreenCache] 里。有它就直接画，没有再走一次完整加载。
    //
    // 按 [cacheKey] 做 remember 的键：身份是异步到的，`owner` 从空串变成真身份时这几个状态会按缓存
    // 重新初始化一次，那一刻缓存才接得上（不是每次重组都重来——键没变就不重跑）。
    val cacheKey = interactionsCacheKey(owner)
    val cached = remember(cacheKey) {
        if (cacheKey.isEmpty()) null else ScreenCache.get<InteractionsSnapshot>(cacheKey)
    }
    var inbox by remember(cacheKey) {
        mutableStateOf(cached?.let { Page(it.inbox, it.inboxHasMore) } ?: Page.EMPTY)
    }
    // 每个来源读到哪儿：一页从同一个 offset 各取 [INTERACTION_PER_SOURCE] 行，所以 offset 按
    // 这个窗口往前推，而不是按回来多少行数——某一路只回了三行，不该把别路的七行一起往前拖。
    var inboxOffset by remember(cacheKey) { mutableIntStateOf(cached?.inboxOffset ?: 0) }
    var summary by remember(cacheKey) { mutableStateOf(cached?.summary ?: InboxSummary.EMPTY) }
    // 有缓存就不摆骨架：这一趟刷新只是把新的一版顶上去。
    var loading by remember(cacheKey) { mutableStateOf(cached == null) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var followers by remember(cacheKey) { mutableStateOf(cached?.followers.orEmpty()) }
    var followersLoading by remember { mutableStateOf(false) }
    var followersError by remember { mutableStateOf<String?>(null) }
    // 正在写关注关系的那一行：写的时候按钮先别理手指，免得一次点击写两遍。
    var followBusy by remember { mutableStateOf("") }

    // 「我的评论」：这台设备写过的评论，按身份取，一页一页往下读。
    var mine by remember(cacheKey) {
        mutableStateOf(
            cached?.mine?.let { comments -> Page(comments, cached.mineHasMore) } ?: Page.EMPTY,
        )
    }
    // 只有读过一次之后，菜单里才写得出条数；没读过就画「我的评论」四个字，不先报一个 0。
    // 缓存里有这一栏就等于读过——那是上一次真读到的结果，不是空的。
    var mineLoaded by remember(cacheKey) { mutableStateOf(cached?.mine != null) }
    var mineLoading by remember { mutableStateOf(false) }
    var mineLoadingMore by remember { mutableStateOf(false) }
    var mineError by remember { mutableStateOf<String?>(null) }
    // 点了垃圾桶的那条：删评论是要收走的，先问一句。
    var pendingDelete by remember { mutableStateOf<MyComment?>(null) }
    // 删完 / 删不掉的回执，摆在这一栏顶上。
    var notice by remember { mutableStateOf<String?>(null) }
    // 这一趟里我点过赞的评论：后端只回一个总数，这一页不回读，所以先按会话记住自己那条。
    var likedRows by remember { mutableStateOf(setOf<Long>()) }

    /**
     * 把现在画在屏幕上的这一版写回缓存，下一次进来先画它。
     *
     * 三栏是分三次拉到的（消息首屏、关注名单、我的评论），所以每次回来都整份重写一遍：只换自己
     * 那一段的话，三处调用各要维护一份「其余两段现在是谁」，读起来比写全份更容易错。
     *
     * 没拉过的「我的评论」一律记成 null 而不是空列表：记空的话，下一次进来会把「还没拉过」画成
     * 「一条都没写过」，而这两件事在界面上是不同的空态。
     */
    val commitCache: () -> Unit = {
        if (cacheKey.isNotEmpty()) {
            ScreenCache.put(
                cacheKey,
                InteractionsSnapshot(
                    inbox = inbox.items,
                    inboxHasMore = inbox.hasMore,
                    inboxOffset = inboxOffset,
                    summary = summary,
                    followers = followers,
                    mine = if (mineLoaded) mine.items else null,
                    mineHasMore = mineLoaded && mine.hasMore,
                ),
            )
        }
    }

    val inboxListState = rememberLazyListState()
    val followersListState = rememberLazyListState()
    val mineListState = rememberLazyListState()

    // 双击底栏「消息」那一格：三个分页各自的列表回顶部（不在屏幕上的那些本来就在顶上，不动）。
    ScrollToTopEffect(inboxListState)
    ScrollToTopEffect(followersListState)
    ScrollToTopEffect(mineListState)

    // 首屏：一次计数 + 一次消息页。计数是为了拿到「最新一条」和「从哪个瞬间开始算」，
    // 没有它，红点就只能靠本页自己猜。
    LaunchedEffect(owner, token) {
        // 有缓存的那一趟不点 loading：上一次画出来的内容还在屏幕上，转圈会把它盖住。
        if (cached == null) loading = true
        loadError = null
        val freshSummary = repository.inboxSummary(owner, token, seenAt) ?: InboxSummary.EMPTY
        val freshInbox = repository.inboxPage(owner, token, offset = 0, perSource = INTERACTION_PER_SOURCE)
        // 后端没这个函数、或者手机没连上，都不能读成「没人理你」：把原因摆出来。
        //
        // 而且失败时**不覆盖**屏幕上那一份：`inboxPage` 拉挂了给的也是空页，照着它画就等于因为
        // 一次断网把刚才还在的消息清空，人还得再进一次页面才可能看到它们回来。
        val failure = repository.lastError
        if (failure == null) {
            summary = freshSummary
            inbox = freshInbox
            // 游标跟着画出来的那一页走：缓存里记的就是「屏幕上这一页取到哪儿」。
            inboxOffset = INTERACTION_PER_SOURCE
            commitCache()
        }
        loadError = failure
        loading = false
    }

    // 滚到底才问下一页：用 [loadingMore] 挡一下，免得一次快滑排出十个一样的请求；按 key 合并，
    // 免得正好落在两页之间的那一行被画两次。
    val loadMore: () -> Unit = {
        if (!loadingMore) {
            scope.launch {
                loadingMore = true
                val next = repository.inboxPage(
                    owner = owner,
                    token = token,
                    offset = inboxOffset,
                    perSource = INTERACTION_PER_SOURCE,
                )
                val failure = repository.lastError
                // 翻页失败不并进列表：空页会把 hasMore 改成 false，从此这一栏再不会往下问。
                if (failure == null) {
                    inbox = Page(
                        items = (inbox.items + next.items)
                            .distinctBy { "${it.kind}-${it.id}-${it.createdAt}" },
                        hasMore = next.hasMore,
                    )
                    inboxOffset += INTERACTION_PER_SOURCE
                    commitCache()
                }
                loadError = failure
                loadingMore = false
            }
        }
    }

    // 消息这一栏被真正看见了，才算读过：交回去的是「最新的一条」和「后端计数时的那个瞬间」，
    // 后者才是游标，本页记住看了几行没有意义。「我的评论」不是消息流，不该把别的分类标成读过。
    LaunchedEffect(section, messages, inbox, summary) {
        if (section == InteractionSection.MESSAGES &&
            messages != InteractionMessages.MINE &&
            inbox.items.isNotEmpty()
        ) {
            onInboxSeen(
                maxOf(newestInboxCommentId(inbox.items), summary.newestCommentId),
                summary.checkedAt,
            )
        }
    }

    // 关注列表：每次切到这一栏都重新问一次，别人刚关注你那一眼就该在。
    LaunchedEffect(section, owner) {
        if (section == InteractionSection.FOLLOWERS) {
            followersLoading = true
            followersError = null
            val fresh = social.followersOf(owner)
            val failure = interactionErrorOf(social.lastError, social.lastFailureWasNetwork)
            // 失败不覆盖：缓存里那份关注名单比空列表有用，为什么没刷新由上面那一行说。
            if (failure == null) {
                followers = fresh
                commitCache()
            }
            followersError = failure
            followersLoading = false
        }
    }

    // 我的评论：每次切到这一栏都问一次——这一栏是「我写过什么」，在别处新写的那一条只有在问过
    // 之后才会出现；一次只一页，和关注列表同一套「进来就拉」的做法。
    //
    // [mineLoading] 用 try/finally 复位：切走那一栏会把 effect 取消，停在 `true` 的话这一栏就永久
    // 停在「…」上，切回来也再不会拉。
    LaunchedEffect(section, messages, owner, token) {
        if (section != InteractionSection.MESSAGES || messages != InteractionMessages.MINE) {
            return@LaunchedEffect
        }
        mineLoading = true
        mineError = null
        try {
            val fresh = repository.myCommentsPage(owner, token, offset = 0)
            val failure = interactionErrorOf(repository.lastError, false)
            // 失败不覆盖屏幕上那一份（可能是上一次进来留下的缓存），只把原因摆在顶上。
            if (failure == null) {
                mine = fresh
                commitCache()
            }
            mineError = failure
            mineLoaded = true
        } finally {
            mineLoading = false
        }
    }

    /** 「我的评论」滚到底时再问下一页；和互动消息那一栏同一套挡板与去重。 */
    val loadMoreMine: () -> Unit = {
        if (!mineLoadingMore) {
            scope.launch {
                mineLoadingMore = true
                val next = repository.myCommentsPage(owner, token, offset = mine.items.size)
                val failure = interactionErrorOf(repository.lastError, false)
                if (failure == null) {
                    mine = Page(
                        items = (mine.items + next.items).distinctBy { it.id },
                        hasMore = next.hasMore,
                    )
                    commitCache()
                }
                mineError = failure
                mineLoadingMore = false
            }
        }
    }

    pendingDelete?.let { comment ->
        CantaDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除评论",
            buttons = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        val ok = repository.deleteComment(comment.id, owner, token)
                        notice = if (ok) "已删除这条评论" else "删不掉：这条评论不是本机写的，或者后端没通。"
                        // 删完当场从列表里拿掉：等下一次进这一栏太慢，人也看不出来删没删掉。
                        if (ok) {
                            mine = Page(mine.items.filterNot { it.id == comment.id }, mine.hasMore)
                            // 缓存一起改：不改的话下一次进来先画出来的那条还能点，而它其实已经没了。
                            commitCache()
                        }
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
        ) {
            Text(
                text = "「${comment.body.take(40)}」会被删除，下面别人给的回复一起去掉。",
                fontSize = 12.sp,
            )
        }
    }

    /** 打开一行：方案评论落到方案页的那条评论上，应用评论落到应用页的那条评论上。 */
    val open: (InboxItem) -> Unit = { item ->
        val commentId = item.commentIdOrZero
        if (item.targetKind == COMMENT_KIND_APP) {
            if (item.target.isNotEmpty()) onOpenAppComment(item.target, commentId)
        } else {
            item.planIdOrNull()?.let { planId -> onOpenPlanComment(planId, commentId) }
        }
    }

    /**
     * 打开我写的一条评论：带的是**这条评论自己的 id**，不是它下面某条回复的 id。
     *
     * 于是详情页会把这一条排到评论最前面、展开它的回复、并滚到评论那一段——「我的评论」点一条，
     * 看到的就是自己写的那一行，而不是一整页包的列表头。
     */
    val openMine: (MyComment) -> Unit = { comment ->
        val plan = comment.planIdOrNull()
        val pkg = comment.packageOrNull()
        when {
            pkg != null -> onOpenAppComment(pkg, comment.id)
            plan != null -> onOpenPlanComment(plan, comment.id)
            else -> Unit
        }
    }

    /** 关注 / 取关。写成功才改本行，写失败把原因摆到列表顶上。 */
    val toggleFollow: (SocialPerson) -> Unit = { person ->
        if (followBusy.isEmpty()) {
            scope.launch {
                followBusy = person.id
                val following = social.setFollow(owner, person.id, !person.iFollow)
                val reason = interactionErrorOf(social.lastError, social.lastFailureWasNetwork)
                if (reason == null) {
                    followersError = null
                    followers = followers.map { row ->
                        if (row.id == person.id) row.copy(iFollow = following) else row
                    }
                    // 回关是这一栏里唯一会改数据的地方，缓存跟着改，否则切回来先画的还是「回关」。
                    commitCache()
                } else {
                    followersError = reason
                }
                followBusy = ""
            }
        }
    }

    Scaffold(
        topBar = {
            InteractionsTopBar(
                section = section,
                messages = messages,
                mineCount = if (mineLoaded) mine.items.size else null,
                onSelectMessages = { messages = it },
                onToggleSection = {
                    section = if (section == InteractionSection.MESSAGES) {
                        InteractionSection.FOLLOWERS
                    } else {
                        // 切回消息时保留上次选的分类，只有第一次进来才是「全部消息」。
                        InteractionSection.MESSAGES
                    }
                },
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 这一栏该看的行：ALL 就是全部，其余按 kind 过一遍。过滤在本地做，不额外问后端。
            val visibleInbox = messages.filter?.let { filter -> inbox.items.matching(filter) }
                ?: inbox.items
            // 下一页只在这一栏、还有页、而且没有正在加载的时候才问。
            InteractionEndlessScroll(
                state = inboxListState,
                enabled = section == InteractionSection.MESSAGES &&
                    messages != InteractionMessages.MINE &&
                    inbox.hasMore &&
                    !loadingMore,
                onEnd = loadMore,
            )
            InteractionEndlessScroll(
                state = mineListState,
                enabled = section == InteractionSection.MESSAGES &&
                    messages == InteractionMessages.MINE &&
                    mine.hasMore &&
                    !mineLoadingMore,
                onEnd = loadMoreMine,
            )
            if (section == InteractionSection.FOLLOWERS) {
                Column(modifier = Modifier.fillMaxSize()) {
                    followersError?.let { reason -> InteractionErrorLine(reason) }
                    when {
                        followersLoading && followers.isEmpty() ->
                            Text("…", fontSize = 12.sp, modifier = Modifier.padding(14.dp))

                        followers.isEmpty() -> InteractionEmptyNotice(
                            title = "还没有人关注你",
                            hint = "别人关注你之后，会出现在这里，你可以从这里回关。",
                        )

                        else -> LazyColumn(
                            state = followersListState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(followers) { person ->
                                FollowerRow(
                                    person = person,
                                    busy = followBusy == person.id,
                                    onToggleFollow = { toggleFollow(person) },
                                )
                            }
                        }
                    }
                }
            } else if (messages == InteractionMessages.MINE) {
                Column(modifier = Modifier.fillMaxSize()) {
                    mineError?.let { reason -> InteractionErrorLine(reason) }
                    notice?.let { line ->
                        Text(
                            text = line,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                    }
                    when {
                        (mineLoading || !mineLoaded) && mine.items.isEmpty() ->
                            Text("…", fontSize = 12.sp, modifier = Modifier.padding(14.dp))

                        mine.items.isEmpty() -> InteractionEmptyNotice(
                            title = "你还没有写过评论。",
                            hint = "在方案详情或应用详情里写一条，这里就会出现；点一条就能回到你写的那一行。",
                        )

                        else -> LazyColumn(
                            state = mineListState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(mine.items, key = { comment -> "mine-${comment.id}" }) { comment ->
                                MyCommentRow(
                                    // 头像首字：这台设备的名字，没名字时用「我」，反正这一栏全是自己的。
                                    author = author.ifBlank { "我" },
                                    // 这一栏全是自己的评论：有了身份号，头像就能显示自己那张照片。
                                    owner = owner,
                                    body = comment.body,
                                    targetLabel = mineTargetLabel(comment),
                                    createdAt = comment.createdAt,
                                    replies = comment.replies,
                                    likes = comment.likes,
                                    onOpen = { openMine(comment) },
                                    onDelete = { pendingDelete = comment },
                                )
                            }
                            if (mineLoadingMore) {
                                item(key = "mine-more") { InteractionLoadingMore() }
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 「后端没回答」和「真的没人理你」长得一样，所以原因要摆在列表顶上。
                    loadError?.let { reason -> InteractionErrorLine(reason) }
                    when {
                        loading -> Text("…", fontSize = 12.sp, modifier = Modifier.padding(14.dp))

                        visibleInbox.isEmpty() -> InteractionEmptyNotice(
                            title = if (messages == InteractionMessages.ALL_MESSAGES) {
                                "还没有新的互动。"
                            } else {
                                "「${messages.label}」这一栏还没有消息。"
                            },
                            hint = "有人回复你、评论你上传的方案，或者赞了你的评论和方案时，会出现在这里。",
                        )

                        else -> LazyColumn(
                            state = inboxListState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = visibleInbox,
                                key = { row -> "${row.kind}-${row.id}-${row.createdAt}" },
                            ) { row ->
                                InteractionRow(
                                    item = row,
                                    onOpen = { open(row) },
                                    onOpenProfile = onOpenProfile,
                                    liked = row.commentIdOrZero in likedRows,
                                    // 赞落在这一行指向的那条评论上：先改本行，再把结果交给后端，
                                    // 失败就把这一下撤回去（总数以后端下次给的为准）。
                                    onLike = {
                                        val id = row.commentIdOrZero
                                        if (id > 0L) {
                                            val wanted = id !in likedRows
                                            likedRows = if (wanted) likedRows + id else likedRows - id
                                            scope.launch {
                                                val result = repository.likeComment(id, owner, wanted)
                                                if (result == null) {
                                                    likedRows = if (wanted) {
                                                        likedRows - id
                                                    } else {
                                                        likedRows + id
                                                    }
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                            if (loadingMore) {
                                item(key = "interactions-more") { InteractionLoadingMore() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顶栏：左边返回（旁边挂未读数），中间两栏并排居中，右边留一块等宽的空白把中间压正。
 *
 * 参考图（抖音 / 汽水音乐的通知页）里，「互动消息 ⌃」和「新关注我的」是**紧挨着居中**的一对：
 * 当前那一栏是深色加粗、下面一条短下划线（点它弹分类菜单），另一栏是浅灰、点它换栏。返回箭头旁边
 * 那个小圆徽标是没读的消息数——它和列表里的红点、底栏的角标是同一个数。
 *
 * [mineCount] 为 null 表示「我的评论」还没读过：没读过就不写数字，写了「· 0」等于先说了一句假话。
 */
@Composable
private fun InteractionsTopBar(
    section: InteractionSection,
    messages: InteractionMessages,
    mineCount: Int?,
    onSelectMessages: (InteractionMessages) -> Unit,
    onToggleSection: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val inMessages = section != InteractionSection.FOLLOWERS
    // 左边那一栏的名字：消息段是选中的分类，关注段就是「新关注我的」。
    val title = if (inMessages) messages.label else FOLLOWERS_LABEL
    // 紧挨着的另一个入口。
    val other = if (inMessages) FOLLOWERS_LABEL else "互动消息"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // 参考图的排法是「返回（最左）· 标题（整条顶栏的正中，带下拉）· 另一栏（最右）」。
        // 不是把标题和另一栏拼成一对再居中（那样标题偏左、另一栏跑到中间），也不是两头各贴一个边
        // （那样标题紧挨着返回箭头，中间空一大片）——标题按整条栏居中，另一栏才真正靠右。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlTopBarHeight),
        ) {
            // 左：返回
            Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp)) {
                FlBarButton(
                    glyph = FlGlyphs.chevronBack,
                    contentDescription = "返回",
                    onClick = onNavigateBack,
                    iconSize = 20.dp,
                    filled = false,
                )
            }
            // 中：标题 + 分类菜单的锚点，落在屏幕正中
            Box(modifier = Modifier.align(Alignment.Center)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (inMessages) Modifier.clickable { expanded = true } else Modifier,
                            )
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = FlText.headline,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // 下划线画在标题自己的 bounds 上，宽度跟着字走，而不是跟着整条栏。
                            modifier = Modifier.titleUnderline(colors.primary),
                        )
                        // 关注那一栏没有分类，就不画箭头，免得点上去以为还有菜单。
                        if (inMessages) {
                            Spacer(Modifier.width(3.dp))
                            FlGlyphIcon(
                                glyph = FlGlyphs.chevronDown,
                                size = 13.dp,
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.width(196.dp),
                    ) {
                        InteractionMessages.entries.forEach { option ->
                            val selected = option == messages
                            val tint = if (selected) colors.primary else colors.onSurfaceVariant
                            // 选中项的勾：自己画在 trailingIcon 里，没选中就什么都不放。
                            val trailing: (@Composable () -> Unit)? = if (selected) {
                                { FlGlyphIcon(glyph = FlGlyphs.check, size = 14.dp, tint = tint) }
                            } else {
                                null
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (option == InteractionMessages.MINE && mineCount != null) {
                                            "${option.menuLabel} · $mineCount"
                                        } else {
                                            option.menuLabel
                                        },
                                        fontSize = 13.sp,
                                        color = if (selected) colors.primary else colors.onSurface,
                                        maxLines = 1,
                                    )
                                },
                                onClick = {
                                    expanded = false
                                    onSelectMessages(option)
                                },
                                leadingIcon = {
                                    // 只有「赞与收藏」用 Material 的心形，FlGlyph 里没有真·心形图标。
                                    if (option == InteractionMessages.HEART) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = tint,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    } else {
                                        FlGlyphIcon(glyph = menuGlyphOf(option), size = 16.dp, tint = tint)
                                    }
                                },
                                trailingIcon = trailing,
                            )
                        }
                    }
                }
            // 右：另一栏。靠右，但和标题一样各留一段边距，不贴屏幕边缘。
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) {
                Text(
                    text = other,
                    style = FlText.subheadline,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = onToggleSection)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
        }
        // 顶栏下面那条发丝线：参考图里标题区和列表之间有一条线，列表从线下开始。
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
    }
}

/** 菜单里每一项的小图标；「赞与收藏」走 Material 的心形，不在这里。 */
private fun menuGlyphOf(option: InteractionMessages): FlGlyph = when (option) {
    InteractionMessages.ALL_MESSAGES -> FlGlyphs.appsList
    InteractionMessages.REPLY -> FlGlyphs.send
    InteractionMessages.PLAN_COMMENT -> FlGlyphs.profiles
    InteractionMessages.HEART -> FlGlyphs.info
    InteractionMessages.MINE -> FlGlyphs.edit
}

/**
 * 标题下面那条短粗下划线。
 *
 * 画在标题自己的绘制区域上（而不是另起一个 Box），所以宽度天然等于文字宽度。线的位置贴在文字
 * bounds 的**里面**（`size.height - stroke`）：画到 bounds 外面会被外层 Row 的 `clip` 切掉一截，
 * 看起来像条虚线。
 */
private fun Modifier.titleUnderline(color: Color): Modifier = drawBehind {
    val stroke = 2.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, size.height - stroke),
        size = Size(size.width, stroke),
        cornerRadius = CornerRadius(stroke / 2f),
    )
}

/**
 * 一行互动：谁 + 做了什么 + 说了什么 + 什么时候，右下角给一个直接去回复的入口。
 *
 * 整行都能点，打开的地方和「回复评论」一样：一行新闻的价值在于跳到那条评论上，
 * 而不是在于读这三十个字。
 */
@Composable
private fun InteractionRow(
    item: InboxItem,
    onOpen: () -> Unit,
    onOpenProfile: (owner: String) -> Unit,
    /** 这一条后面那条评论我赞过没有（本机会话内记住的），决定小胶囊写「赞」还是「已赞」。 */
    liked: Boolean = false,
    onLike: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    // 一行一条，不是一张卡：参考图里这一页是通栏白底 + 发丝分割线，卡片会把整列切成一格一格的。
    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
            // 头像是一块自己的入口：点开那个人的主页。行本身的点击仍然是开那条消息指向的页。
            // 右下角那个小圆标按事件类型换图：赞是心，回复/评论是气泡——不读字也看得出发生了什么。
            Box {
                InteractionAvatar(
                    name = item.actor,
                    owner = item.actorId,
                    size = 40.dp,
                    modifier = Modifier.clickable(
                        enabled = item.actorId.isNotEmpty(),
                        onClickLabel = "看主页",
                    ) { onOpenProfile(item.actorId) },
                )
                InteractionKindBadge(kind = item.kind, modifier = Modifier.align(Alignment.BottomEnd))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 名字和动作短语一起占左边这一块，时间被 weight 挤到最右。
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.actor.ifBlank { "有人" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = interactionActionOf(item),
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = interactionStampOf(item.createdAt),
                        fontSize = 9.sp,
                        color = colors.outline,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(3.dp))
                // 正文只留两行：这是列表，读得下去的那两行就够决定点不点开了。
                // 回复那种行按参考图的写法加「回复: 」前缀——一眼看出这是"他回了我哪句"，
                // 而不是一条没头没尾的话。（方案评论、点赞那些不加：名字那一行已经说了是什么事。）
                Text(
                    text = if (item.kind == InboxKind.REPLY) "回复: " + item.body else item.body,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                // 动作行：只有「有人写了字」的那两种才有——回复、方案下的评论。参考图里
                // 「赞了你的评论 / 认同了你的方案」那种行下面没有按钮：那件事已经发生完了，
                // 摆一个「回复评论」在那儿只会让人以为要回复一句"谢谢"。
                val written = item.kind == InboxKind.REPLY || item.kind == InboxKind.PLAN_COMMENT
                if (written) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InteractionPill(text = "回复评论", onClick = onOpen)
                        if (item.commentIdOrZero > 0L) {
                            Spacer(Modifier.width(8.dp))
                            InteractionPill(
                                text = if (liked) "已赞" else "赞",
                                liked = liked,
                                heart = true,
                                onClick = onLike,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // 右边只留一张缩略图。参考图里那个灰色圆底纸飞机只长在「某人访问了你的主页」那种行上
            // （那里没有正文可预览，纸飞机是"去 TA 主页"的入口）；回复、评论、赞这几种行右侧都是
            // 一张图，多画一个圆钮就多一个和整行重复的点击区。
            InteractionThumbnail(label = item.title.ifEmpty { item.target })
            }
        }
        InteractionDivider()
    }
}

/** 行与行之间那条发丝线：通栏白底的一列，靠它分出"这是一条一条的"。 */
@Composable
private fun InteractionDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

/**
 * 头像右下角那颗类型小圆标：赞是心，回复与方案下的评论是气泡。
 *
 * 圆标压在头像上，边框用行的底色（白）描一圈，这样头像和人名之间的边界还是清楚的。
 */
@Composable
private fun InteractionKindBadge(kind: InboxKind, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val heart = kind == InboxKind.COMMENT_HEART || kind == InboxKind.PLAN_HEART
    Box(
        modifier = modifier
            .size(17.dp)
            .clip(CircleShape)
            .background(if (heart) colors.error else colors.primary)
            .border(1.5.dp, colors.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (heart) Icons.Default.Favorite else Icons.Default.Chat,
            contentDescription = if (heart) "赞" else "评论",
            modifier = Modifier.size(9.dp),
            tint = Color.White,
        )
    }
}

/**
 * 行尾那张小缩略图：目标的首字画在圆角方块里。
 *
 * 参考图那儿是一张真截图，后端没有这类图片可拿（只有标题 / 包名），所以用首字占住同一个形状，
 * 一行里"这是哪件事"的位置留着。
 */
@Composable
private fun InteractionThumbnail(label: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.trim().take(1).ifEmpty { "?" },
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onPrimaryContainer,
            maxLines = 1,
        )
    }
}

/**
 * 动作行里的小胶囊：「回复评论」「赞」「已赞」共用这一个形状。
 *
 * 照参考图：底色是**灰**的（不是主色的浅蓝），文字也灰；赞过之后不是把胶囊刷成实底，而是
 * 把里面那颗心换成**红的实心**、字换成「已赞」——一行里颜色只在心上有，眼睛一眼扫得到。
 *
 * [liked] 是「已经赞过了」；[heart] 打开时在文字前面画一颗心。
 */
@Composable
private fun InteractionPill(
    text: String,
    onClick: () -> Unit,
    liked: Boolean = false,
    glyph: FlGlyph? = null,
    heart: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            heart -> Icon(
                imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = if (liked) colors.error else colors.onSurfaceVariant,
            )

            glyph != null -> FlGlyphIcon(glyph = glyph, size = 11.dp, tint = colors.onSurfaceVariant)
        }
        if (heart || glyph != null) Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 谁刚关注了我：头像、名字、时间（有就原样，没有就说「最近」），右边是回关 / 已关注。 */
@Composable
private fun FollowerRow(person: SocialPerson, busy: Boolean, onToggleFollow: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // 和消息那一栏同一套：通栏一行 + 发丝线，不做卡片。
    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        InteractionAvatar(name = person.label, owner = person.id, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = person.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (person.since.isBlank()) {
                    "最近关注了你"
                } else {
                    // 后端给的是 `2026-11-22T13:09:33.5Z`：只留日期，整串贴上去读不懂也太长。
                    // 日期按**北京时间**：`2026-11-22T13:09:33.5Z` 在 UTC 里是 22 号，在北京也是 22 号；
                    // 但凌晨那几个小时会跨天，所以按北京日期取，而不是切 UTC 串的前 10 个字符。
                    val day = BeijingTime.dayKey(person.since).ifEmpty { person.since.substringBefore('T') }
                    "$day 关注了你"
                },
                fontSize = 10.sp,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        InteractionFollowButton(followed = person.iFollow, enabled = !busy, onClick = onToggleFollow)
        Spacer(Modifier.width(2.dp))
        FlGlyphIcon(glyph = FlGlyphs.chevronForward, size = 13.dp, tint = colors.outline)
        }
        InteractionDivider()
    }
}

/** 回关按钮：没关注是红底白字的「回关」，关注过是灰底的「已关注」，再点一次就是取关。 */
@Composable
private fun InteractionFollowButton(followed: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (followed) colors.surfaceContainerHighest else UnreadRed)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (followed) "已关注" else "回关",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (followed) colors.onSurfaceVariant else Color.White,
            maxLines = 1,
        )
    }
}

/**
 * 一个人头像：设过照片就是照片，没设过就是名字首字（判断在 [PersonAvatar] 里）。
 *
 * 照片按身份号从图床取：互动这一页每一行都有 `actorId`，所以别人的头像也能显示。
 */
@Composable
private fun InteractionAvatar(
    name: String,
    owner: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    PersonAvatar(
        name = name,
        owner = owner,
        size = size,
        modifier = modifier,
        background = colors.primary.copy(alpha = 0.15f),
        letterColor = colors.primary,
    )
}

/** 一行说明「没拿到列表」的灰字，摆在列表顶上。 */
@Composable
private fun InteractionErrorLine(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

/** 空态：一句说清楚这里会长出什么。 */
@Composable
private fun InteractionEmptyNotice(title: String, hint: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
        Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = hint,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 列表末尾那一行转圈：还有下一页在后台读着。 */
@Composable
private fun InteractionLoadingMore() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
    }
}

/**
 * 列表滚到最后一行时才叫一次 [onEnd]，也就是「下一页值得问了」的那一刻。
 *
 * [enabled] 是调用方的「还有下一页、而且没有正在读」：已经拿全了的列表不该在每次重组时再问一次。
 */
@Composable
private fun InteractionEndlessScroll(state: LazyListState, enabled: Boolean, onEnd: () -> Unit) {
    val nearEnd by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(nearEnd, enabled) {
        if (enabled && nearEnd) onEnd()
    }
}

/** 「方案 · 标题」或「应用 · com.example.app」：我这条评论写在哪儿。 */
private fun mineTargetLabel(comment: MyComment): String {
    val kind = if (comment.kind == COMMENT_KIND_APP) "应用" else "方案"
    val name = comment.title.ifEmpty { comment.target }
    return "$kind · $name"
}

/** 一行互动的动词：做了什么，落在什么上。 */
private fun interactionActionOf(item: InboxItem): String = when (item.kind) {
    InboxKind.REPLY -> "回复了你"
    InboxKind.PLAN_COMMENT -> "评论了你的方案"
    InboxKind.COMMENT_HEART ->
        if (item.likes > 1) "等 ${item.likes} 人赞了你的评论" else "赞了你的评论"
    InboxKind.PLAN_HEART ->
        if (item.likes > 1) "等 ${item.likes} 人认同了你的方案" else "认同了你的方案"
    else -> "有新的互动"
}

/**
 * 时间那一格：按北京时间走 [BeijingTime.listStamp]（刚刚 / N分钟前 / HH:mm / 昨天 HH:mm / MM-dd）。
 *
 * 原来这里是把 `2026-11-22T19:20:33Z` 截到分钟、把 `T` 换成空格——直接显示 UTC，比北京时间早八小时。
 * 参考图那边是"昨天 20:09""周日"这种相对写法，名单越长，相对时间越好扫。
 */
private fun interactionStampOf(createdAt: String): String = BeijingTime.listStamp(createdAt)

/** 列表读空的两种原因分开说：没连上是网络的事，被拒绝是后端的事。 */
private fun interactionErrorOf(reason: String?, network: Boolean): String? = when {
    network -> "没连上后端，检查代理或网络后再试。"
    !reason.isNullOrBlank() -> "后端没有返回列表：$reason"
    else -> null
}
