package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.samolego.canta.data.cache.ScreenCache
import io.github.samolego.canta.data.community.ChatMessageCache
import io.github.samolego.canta.data.community.Conversation
import io.github.samolego.canta.data.community.ConversationCache
import io.github.samolego.canta.data.community.InboxSummary
import io.github.samolego.canta.data.community.MessageHit
import io.github.samolego.canta.data.community.OfficialGroup
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.SocialPerson
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.ui.component.UnreadBadge
import io.github.samolego.canta.ui.component.UnreadDot
import io.github.samolego.canta.ui.component.bottomHairline
import io.github.samolego.canta.ui.component.UnreadRed
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBarHeight
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.BeijingTime
import io.github.samolego.canta.util.currentTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 页面在前台时多久问一次后端：没有推送，靠这一行让列表跟上别人的动作。
 *
 * 30 秒而不是 15 秒：这一趟只花一次增量查询（自上次以来有动静的那几个会话），但一次仍然要
 * 三次请求（会话 + 关注 + 互动总数），15 秒等于每分钟 12 次、每小时 720 次。人对"列表晚半分钟
 * 更新"几乎没有感知，而后端额度、电量和流量都是实打实的。退到后台则一次都不问（见下面的
 * `inForeground`）。
 */
private const val MessagePollMs: Long = 30_000L

/**
 * 身份还没读出来时先等多久：冷启动读一次本地设置是毫秒级的事，超过这个数就是真读不到了。
 * 到了就把骨架收起来并写一句原因，而不是让四个空卡片一直挂着。
 */
private const val IdentityGraceMs: Long = 2_500L

/**
 * 置顶那一行铺的主色淡底有多深。
 *
 * 置顶只用色差说话：不写字、不点圆点，整行颜色深一档就是了。0.12 是「隔着看也分得出、
 * 又不至于把名字和预览糊掉」的那一档；浅色主题下在这层近白底上叠出很淡的主题色，
 * 深色主题下同样成立。
 */
private const val PinnedTintAlpha: Float = 0.12f

/** 预取几个会话的消息：够覆盖「刚聊过的那几个」，再多就是替没打开的页面花流量。 */
private const val WarmConversationLimit = 4

/** 每个会话预取多少条：和聊天页首屏那一页一致。 */
private const val WarmMessageLimit = 30

/** 两次预取之间停一下：并发打出去只会抢同一条连接。 */
private const val WarmPauseMillis = 150L

/** 头像那一排最多画多少位最近联系人：再多就不是「最近」了。 */
private const val RecentContactCap: Int = 8

/** 横排里从会话、从关注列表各取多少位，取完去重再截到 [RecentContactCap]。 */
private const val ContactsFromChats: Int = 6
private const val ContactsFromFollows: Int = 6

/**
 * 一次消息搜索的回答在 [ScreenCache] 里留多久。
 *
 * 30 秒刚好覆盖「打错一个字删了重打」和「收起来再打开搜索」这两种重复：这两种情况下同一个词
 * 再问一遍后端拿不到任何新东西，只会让结果区白闪一次。再长就不合适了——消息搜索回答的是
 * 「刚才有没有人说过这句」，隔太久以前的答案已经不算答案。
 */
private const val SearchCacheTtlMs: Long = 30_000L

/**
 * 搜索缓存最多记多少个关键词。
 *
 * 每个关键词都是一个新键（`"screen:search:<owner>:<query>"`），不封顶就是无界增长：一页搜十次
 * 十条，来回切几次越攒越多，而且再也不会被命中。20 个够覆盖一两次会话里的反复搜索。
 */
private const val SearchCacheLimit: Int = 20

/** 「限时日常」与每位联系人那一格的宽度：圆头像 44，下面一行字居中。 */
private val ContactCellWidth = 64.dp

/** 「还没有好友」那一格的宽度：比头像格宽一点，五个字不用折行。 */
private val HintCellWidth = 88.dp

/**
 * 会话长按菜单的宽度。
 *
 * 菜单里最长的一句是「取消免打扰」（五个字），加上左边的图标和内边距，210dp 足够一行放下；
 * 不定宽的话它会跟着每条文字自己变，一列菜单宽窄不一。
 */
private val ConversationMenuWidth = 210.dp

/** 折叠群聊那个圆底的绿：和互动消息的红一样，一个颜色一件事。 */
private val GroupGreen: Color = Color(0xFF07C160)

/**
 * 「消息」页：私聊与群聊的入口，摆在互动消息下面。
 *
 * 结构照抖音的消息页从上到下排：自绘顶栏（菜单 / 标题 / 搜索 + 建群）、一排最近联系人、
 * 整块的入口（互动消息，以及确实有折叠群时的「折叠的群聊」）、最后是会话列表。
 *
 * 两种群分两处：折叠过的群只出现在「折叠的群聊」那一行后面，没折叠的群照旧留在主列表里。
 * 一个折叠的群都没有时那一行整块不画——入口里没有内容，摆一个「还没有群聊」在那儿等于请人点进
 * 一个空页面。
 *
 * 这一页只负责「去哪儿」：点会话把会话号交给 [onOpenChat]，点互动把页面交给
 * [onOpenInteractions]，群聊列表交给 [onOpenGroups]，还没做的入口一律 [onMessage]。什么都不自己
 * 写：会话、关注、未读数全部来自 [SocialRepository] 和 [PlanRepository]，一页一次拉，失败就画空态。
 *
 * 顶栏两处以前只弹「还没做」的按钮现在是真做事的：放大镜展开一条本地搜索（只过滤已经拉回来的
 * 会话与关注的人，不发请求），汉堡菜单给出「全部标为已读 / 折叠的群聊 / 发起群聊」三项。
 */
@Composable
fun MessagesScreen(
    repository: SocialRepository,
    planRepository: PlanRepository,
    owner: String,
    userName: String,
    token: String,
    inboxUnread: Int,
    inboxPreview: String,
    onOpenInteractions: () -> Unit,
    onOpenChat: (conversationId: Long) -> Unit,
    /** 发起群聊：挑人、起名、建群。 */
    onOpenCreateGroup: () -> Unit = {},
    /** 打开和某人的私聊：没有会话就先开一个（后端 dm_open 是幂等的）。 */
    onOpenDm: (peerOwner: String) -> Unit = {},
    /** 打开「折叠的群聊」页：这一页只露最近说话的那个折叠群，全部折叠群在那边看。 */
    onOpenGroups: () -> Unit = {},
    /**
     * 本机拉黑过的人（身份号）：这些人的私聊不上这一页的列表。
     *
     * 页面不自己去读设置——名单由 [io.github.samolego.canta.ui.CantaApp] 从
     * [io.github.samolego.canta.data.CantaSettings.blockedPeersFlow] 读出来传进来，这一层只画。
     */
    blockedPeers: Set<String> = emptySet(),
    /** 拉黑 / 取消拉黑某个人；落盘是上层的事，这里只把动作交出去。 */
    onBlockPeer: (peer: String, blocked: Boolean) -> Unit = { _, _ -> },
    /**
     * 系统没给通知权限：页顶摆一条「当前无法接收朋友的消息提醒」。
     *
     * 判定在上层（`CantaPlatform.canNotify()`），这一页只按结果画——通知这条链路只在 Android
     * 上存在，桌面端永远不会摆这一条。
     */
    noticesBlocked: Boolean = false,
    /** 点「打开提醒」：申请一次通知权限。 */
    onEnableNotices: () -> Unit = {},
    onMessage: (String) -> Unit = {},
) {
    // 上一屏留下的快照：有它就先画它，画面不闪；没有才走一次完整的加载。
    //
    // 这一页每次被导航进来都是新实例，`remember` 跟着一起没了——所以快照放在
    // [ConversationCache] 那个进程级对象里，而不是这里的 `remember` 中。
    //
    // `getRecent()` 兜第一帧：调用方是从 `DeviceIdentity.current(...)` 异步取身份的，
    // 第一帧的 `owner` 是空串，按它查必然查不到。这台设备的身份是它自己生成的，
    // 缓存里通常只有一份，拿它顶上是对的。
    val cached = remember { ConversationCache.get(owner) ?: ConversationCache.getRecent() }
    var loading by remember { mutableStateOf(cached == null) }
    var conversations by remember {
        mutableStateOf(sortedConversations(cached?.conversations.orEmpty()))
    }
    var followers by remember { mutableStateOf(cached?.followers.orEmpty()) }
    // 刚数回来的互动总数：这一版的副标题和红点都按调用方传进来的值画（见下面两行入口），
    // 这个数先留着，页面拿到它才算把「有多少条新互动」问过一遍。
    var summary by remember { mutableStateOf(cached?.summary ?: InboxSummary.EMPTY) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // 已经拉过几次：定时刷新不能把 loading 又点亮一遍，否则每半分钟整页闪一下骨架。
    var reloads by remember { mutableIntStateOf(0) }
    // 这一趟加载走没走完。定时器只在它没在走的时候才戳一下：一趟是三个串行请求，慢的时候比
    // 30 秒还长，逐个戳过去等于每 30 秒把在跑的那一趟取消重来，`loading` 永远等不到落地，
    // 界面上就是四个空卡片一直挂着。骨架收不起来比骨架本身难看得多。
    var loadBusy by remember { mutableStateOf(false) }
    // 每一趟的序号：只有最新那一趟（或被最新那一趟顶掉的）有权收骨架，免得被取消的旧那一趟
    // 把新那一趟的「正在加载」抹掉。
    var loadPass by remember { mutableIntStateOf(0) }
    // 手指下拉的那一次还在不在转：与应用页同一个 PullToRefreshBox，指示器样式跟那一页一致。
    var refreshing by remember { mutableStateOf(false) }
    // 顶栏那条搜索：开没开、打了什么字。会话与联系人是本地过滤（数据已经在手上），
    // 第三段「消息」要问后端，所以单独存一份结果。
    var searchActive by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }
    var messageHits by remember { mutableStateOf<List<MessageHit>>(emptyList()) }
    var searchingMessages by remember { mutableStateOf(false) }
    // 搜索这一次为什么没拿到新结果：结果区留上一次那一份，原因单独在顶部写一行。
    var searchError by remember { mutableStateOf<String?>(null) }
    // 「全部标为已读」是一次网络调用：点完就把协程交出去，界面不等它。
    val scope = rememberCoroutineScope()
    /** 正在加入官方内测群：挡住连点，也把那一行的小字换成「加入中…」。 */
    var joiningOfficial by remember { mutableStateOf(false) }
    // 这一页此刻在不在前台。切到后台时轮询必须停：这一页离开屏幕不会被销毁（导航栈里还活着），
    // 以前那个 `while (true)` 照转，夜里 4–5 千次/小时的基线就是它——没人在看的时候在问后端。
    //
    // 用 ON_RESUME / ON_PAUSE 维护，没用 `LocalLifecycleOwner` + `currentStateFlow`：commonMain 里
    // 已经在用的是 `androidx.lifecycle.compose.LifecycleEventEffect`（就在下面几行），这个子集在
    // 桌面端 / wasm 上同样存在；`currentStateFlow` 那条路要自己攒 lifecycle-runtime 的 common API，
    // 而这一页只需要一个布尔。宁可少一个依赖，也不引只在 Android 上可用的类。
    var inForeground by remember { mutableStateOf(true) }
    // 这一趟加载带不带增量起点。只有定时轮询会往里放值（见下面的循环），别的触发方式一律全量：
    // 列表里有些东西不发消息也会变，最典型的是我自己点进聊天页读完消息——动的只有 last_read_at，
    // `last_message_at` 一动不动，增量拉不到它，红点就会一直挂着。读一次就清，上一次的值不漏到下一趟。
    val pollSince = remember { mutableStateOf<String?>(null) }

    // 没有推送通道，页面在前台的时候只能自己问。30 秒一次，比"退出再进"快得多，又不至于把
    // 每分钟 120 次的后端额度吃满（一次刷新是三四个查询）。
    LaunchedEffect(owner) {
        if (owner.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(MessagePollMs)
            // 在后台就一次都不发：回到前台时 ON_RESUME 那一支会立刻补一次全量，漏不掉东西。
            if (!inForeground) continue
            // 上一趟还在走就别戳：戳一下等于把它取消重来，见 loadBusy 的注释。
            if (!loadBusy) {
                pollSince.value = sinceOf(conversations)
                reloads++
            }
        }
    }

    // 从会话页、建群页退回来时立刻再问一次：定时器最久还要等 30 秒，人刚说完话回来却看到
    // 旧的预览和旧的未读数，会以为没发出去。第一次进入不重复拉（那时 reloads 还是 0）。
    var resumedOnce by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        inForeground = true
        if (resumedOnce) {
            // 回前台要的是一次新数据，不是把还在跑的那一趟掐掉；在跑的时候等它自己落地。
            // 顺手把增量起点清掉：这一趟要的是真实的未读数（刚在聊天页读完消息），不是增量。
            pollSince.value = null
            if (!loadBusy) reloads++
        } else {
            resumedOnce = true
        }
    }
    // 退到后台就停：下一次 ON_RESUME 会立刻补一次全量。
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { inForeground = false }

    // 一页三次请求，一次拉完再画。
    LaunchedEffect(owner, reloads) {
        // 这一趟带不带增量起点：只有轮询那一支会放值，读完就清。放值和 reloads++ 之间没有别的
        // 挂起点，所以不会有一趟"捡到别人的 since"这种情况。
        val since = pollSince.value
        pollSince.value = null
        if (owner.isEmpty()) {
            // 身份是上层异步读出来的：读到了本效应会被换掉重跑。一直没读到就别让人对着四个
            // 空卡片等下去——等一小会儿，收骨架，写一句原因。
            delay(IdentityGraceMs)
            if (conversations.isEmpty()) {
                loadError = "本机身份还没读到"
                loading = false
            }
            // 这一趟没去拉后端，下拉那一次的转圈也得收：不然手指拽出来的指示器会一直转下去。
            refreshing = false
            loadBusy = false
            return@LaunchedEffect
        }
        loadPass++
        val mine = loadPass
        loadBusy = true
        try {
            // 首帧读到的那份可能来自 `getRecent()`，也可能什么都没读到（第一次装、刚清过缓存）。
            // 这里再按真正的 owner 补一次：补到了就先铺上，画面不闪；补不到才转圈。
            if (reloads == 0 && conversations.isEmpty()) {
                ConversationCache.get(owner)?.let { snapshot ->
                    conversations = sortedConversations(snapshot.conversations)
                    followers = snapshot.followers
                    summary = snapshot.summary
                }
                loading = conversations.isEmpty()
            }
            // `loadError` 不在这里清：清了的话，一次持续的失败会让那行原因在每次刷新开头先消失、
            // 三次请求跑完再回来，看起来就是每 30 秒闪一下。它只在下面拿到结果时被赋成这一次的真值。
            //
            // 轮询那一趟带 since（本地列表里最新的那个最后活跃时间），后端只回有动静的会话。
            // 列表还是空的（第一次装、刚清过缓存）时 since 是 null，那就是全量。
            val chats = repository.conversationsFor(owner, since)
            // 逐句判成败：three 个请求共用一个 `lastError`，最后一句的结果会盖掉前面的。
            // 以前只看最后一句，结果「关注列表没拉到」也会让会话列表那份缓存不写——下次从聊天页
            // 退回来，内存里还是空的，于是又转一次圈。会话列表拿到就是拿到，跟另外两句无关。
            val chatsFailed = repository.lastError != null
            val people = repository.followersOf(owner)
            val peopleFailed = repository.lastError != null
            val counted = planRepository.inboxSummary(owner, token) ?: InboxSummary.EMPTY
            if (!chatsFailed) {
                // 按会话号 upsert 而不是整份替换：增量那一趟只回来几个会话，整份替换等于把没动静的
                // 几十行全删掉。内容没变的行沿用原对象，刷新时那几行不会被当成新行重新组合
                // （长按菜单开着、手指正按着的时候尤其要紧）。
                // 全量那一趟（since == null）直接铺：它带回来的是完整名单，再走 merge 就等于
                // 「后端已经不给我了的会话」永远删不掉——删掉的会话、退掉的群会一直挂在列表上。
                // 只有增量那一趟需要 merge，把没动静的几十行留在原地。
                conversations = sortedConversations(
                    if (since == null) chats else mergeConversations(conversations, chats),
                )
                if (!peopleFailed) followers = people
                summary = counted
                ConversationCache.put(owner, conversations, followers, counted)
            }
            // 拉不到就说一句：空列表和后端没通在界面上长得一样，这一行是唯一的区别。
            loadError = repository.lastError
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 意料之外的失败也得留一句话：宁可写一行原因，也别把整页顶在骨架上或者直接崩掉。
            loadError = e.message ?: "加载失败"
        } finally {
            // 这一趟不管怎么结束（正常、出错、被下一趟顶掉）都得把骨架放下。只有最新那一趟有权
            // 清：被取消的旧那一趟收尾时，新那一趟可能已经开始了，它一清就把「正在加载」抹掉。
            if (mine == loadPass) {
                loading = false
                refreshing = false
                loadBusy = false
            }
        }
    }

    /**
     * 预热：打开消息页时，顺手把最近几个会话（群优先、有未读的优先）的首屏消息取回来写进
     * [ChatMessageCache]。
     *
     * 为什么在这里做：聊天页的首屏只有两件事——缓存里有没有、以及要不要等网络。既然消息页
     * 已经把会话列表拿到了，「点进去立刻能看见最后几条」就只差这一步预取。
     *
     * 每个会话只预取一次（`warmedConversations` 记着），否则每 30 秒的刷新都会再拉一遍，等于
     * 拿流量换一个已经有的东西。预取失败不记账，下次刷新会再试。
     */
    val warmedConversations = remember { mutableSetOf<Long>() }
    LaunchedEffect(owner, conversations.map { it.id }, reloads) {
        if (owner.isEmpty()) return@LaunchedEffect
        val targets = conversations
            .filter { it.isGroup || it.unread > 0 }
            .sortedByDescending { it.lastAt }
            .take(WarmConversationLimit)
        for (conversation in targets) {
            if (conversation.id <= 0L || conversation.id in warmedConversations) continue
            if (ChatMessageCache.get(conversation.id) != null) {
                warmedConversations += conversation.id
                continue
            }
            val fresh = repository.messagesFor(
                owner = owner,
                conversation = conversation.id,
                offset = 0,
                limit = WarmMessageLimit,
            )
            if (fresh.isNotEmpty()) {
                ChatMessageCache.put(conversation.id, fresh)
                warmedConversations += conversation.id
            }
            // 一条一条来：四个请求同时打出去只会抢同一条连接，反而更慢。
            delay(WarmPauseMillis)
        }
    }

    /**
     * 本地改完会话列表的地方都走这里。
     *
     * 两件事一起做：界面立刻变（等下一次 30 秒刷新太慢，人点了看不到变化会以为没生效），
     * 缓存也跟着变（否则下一次从聊天页退回来，先画出来的还是改动之前那一份，看着像回退了）。
     *
     * 排序在这里统一做：置顶的那一行要当场跳到最上面，调用方只管把改好的列表递进来。
     */
    val commitConversations: (List<Conversation>) -> Unit = { next ->
        conversations = sortedConversations(next)
        ConversationCache.putConversations(owner, conversations)
    }

    /**
     * 长按菜单里的几条动作：免打扰、置顶、不显示、拉黑、删除。
     *
     * 都是「先发一次请求，再把结果落到本地列表上」：等下一次 30 秒刷新太慢，人点了看不到变化
     * 就会以为没生效。会话行由 [sortedConversations] 统一重排，置顶的那一行才会当场跳到最上面。
     */
    val togglePin: (Conversation) -> Unit = { conversation ->
        scope.launch {
            val pin = !conversation.pinned
            if (repository.pinConversation(owner, conversation.id, pin)) {
                commitConversations(
                    conversations.map { row ->
                        if (row.id == conversation.id) row.copy(pinned = pin) else row
                    },
                )
            } else {
                onMessage("没成功，检查代理后再试")
            }
        }
    }

    /** 不显示：后端叫 hide——消息还在，只是这一行不再出现在列表里；成功才从本地列表拿掉。 */
    val hideRow: (Conversation) -> Unit = { conversation ->
        scope.launch {
            if (repository.hideConversation(owner, conversation.id)) {
                commitConversations(conversations.filterNot { it.id == conversation.id })
            } else {
                onMessage("没成功，检查代理后再试")
            }
        }
    }

    /**
     * 删除（菜单里那个红字）：先清掉自己这边的消息，再把这一行藏起来。
     *
     * 两步都要成：只清了历史、行还留着，看起来就像没删；只藏了行、消息还在，下次它冒出来
     * 又是一整屏旧话。所以两件事都成功才把行从本地列表拿掉，否则一句实话。
     */
    val deleteRow: (Conversation) -> Unit = { conversation ->
        scope.launch {
            val cleared = repository.clearHistory(owner, conversation.id)
            if (cleared != null && repository.hideConversation(owner, conversation.id)) {
                commitConversations(conversations.filterNot { it.id == conversation.id })
            } else {
                onMessage("没成功，检查代理后再试")
            }
        }
    }

    /**
     * 开关免打扰。只改自己那一行，所以本地也只改自己看到的这一行。
     *
     * 红点**不清**：静音是"别响"，不是"不重要"。清掉等于替这个人判断了哪条消息可以不看。
     */
    val toggleMute: (Conversation) -> Unit = { conversation ->
        scope.launch {
            val mute = !conversation.muted
            if (repository.setMuted(owner, conversation.id, mute)) {
                commitConversations(
                    conversations.map { row ->
                        if (row.id == conversation.id) row.copy(muted = mute) else row
                    },
                )
            } else {
                onMessage("没成功，检查代理后再试")
            }
        }
    }

    /**
     * 拉黑 / 取消拉黑。
     *
     * 后端没有拉黑接口，所以名单是本机的（[blockedPeers]），这里只做三件事：把这一位交出去落盘、
     * 拉黑时顺手让后端把这一行藏掉（免得下次装上又冒出来）、群聊直接说一句——群不是一个人，
     * 拉黑无从谈起。列表的过滤在读的时候做，见下面的 `visibleConversations`。
     */
    val toggleBlock: (Conversation) -> Unit = { conversation ->
        val peer = conversation.peer.ifEmpty { conversation.peerName }
        if (conversation.isGroup || peer.isEmpty()) {
            onMessage("群聊不能拉黑")
        } else {
            val blocked = peer in blockedPeers
            onBlockPeer(peer, !blocked)
            if (!blocked) {
                scope.launch {
                    // 拉黑之后这一行本来就会被过滤掉；这里只是顺手让后端也别再给它。
                    if (!repository.hideConversation(owner, conversation.id)) {
                        onMessage("没成功，检查代理后再试")
                    }
                }
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    // 拉黑过的人的私聊不上列表。会话列表本身没变（后端没有拉黑接口，也没地方"变"），
    // 所以在画的时候过滤：取消拉黑那一刻这一行会当场回来，不必等一次重新拉取。
    val reachableConversations = conversations.filterNot { it.blockedBy(blockedPeers) }
    // 折叠过的群不进主列表：它已经在下面那一行「折叠的群聊」后面了，再列一遍等于同一个群占两处。
    // 官方内测群排在列表最前：它就是「置顶」，不依赖后端那一列，也不会因为本地没点过置顶就沉下去。
    val visibleConversations = reachableConversations
        .filterNot { it.isFoldedGroup() }
        .sortedByDescending { it.title == OfficialGroup.TITLE }
    // 已经在群里的话，上面那一行点下去直接进这个会话，不必再走一次加入。
    val officialConversation = conversations.firstOrNull {
        it.kind == "group" && it.title == OfficialGroup.TITLE
    }
    // 「折叠的群聊」只收折叠了的群。聊天还在，只是平时不用看——这是折叠的意思。
    val foldedGroups = conversations.filter { it.isFoldedGroup() }
    // 折叠的群聊那一行只亮「最近说过话的那个群」：它的最后一句就是这个入口的预览。
    val latestGroup = foldedGroups.maxByOrNull { it.lastAt }
    val groupUnread = foldedGroups.sumOf { it.unread }
    val mutual = mutualKeysOf(followers)
    val recentContacts = recentContactsOf(visibleConversations, followers)
    // 搜索是本地过滤：只有打了字才算在搜，打完又删掉就回到原样。
    val keyword = searchKeyword.trim()
    val searching = searchActive && keyword.isNotEmpty()
    // 搜索仍能找到折叠的群：主列表不列它是为了别占地方，人是特意去找它的时候得找得到。
    val hits = if (searching) searchHitsOf(reachableConversations, followers, keyword) else emptyList()

    // 消息搜索要问后端，所以按输入停顿去抖：每敲一个字就发一次请求，既费额度又会让结果乱跳。
    // 300ms 是"还在打字"和"打完了"之间的常用分界。
    //
    // 同一个 (owner, 关键词) 在 [SearchCacheTtlMs] 内不再问后端：删了又打回来的词就是同一个词，
    // 重打一次请求换不回任何新东西。命中缓存时直接铺上次那份，**连「正在搜索消息…」都不显示**
    // ——那行字是给"真的在等网络"用的，命中缓存时它只会让人以为慢。
    LaunchedEffect(keyword) {
        if (keyword.isEmpty()) {
            messageHits = emptyList()
            searchingMessages = false
            searchError = null
            return@LaunchedEffect
        }
        // 身份还没读到时不问：空身份在后端一律回空数组，还会把这份空当成一次成功的搜索写进缓存。
        if (owner.isEmpty()) return@LaunchedEffect
        delay(300)
        val cacheKey = searchCacheKey(owner, keyword)
        val cached = ScreenCache.get<SearchCacheEntry>(cacheKey)
        val now = currentTimeMillis()
        if (cached != null && now - cached.at <= SearchCacheTtlMs) {
            messageHits = cached.hits
            searchingMessages = false
            searchError = null
            return@LaunchedEffect
        }
        searchingMessages = true
        // 人又在打字时这个 effect 会被取消；`finally` 顺手把标记复位，否则「正在搜索消息…」
        // 会卡在屏幕上，直到下一次搜索自己把它翻回去。
        val fresh = try {
            repository.searchMessages(owner, keyword)
        } finally {
            searchingMessages = false
        }
        // `searchMessages` 失败时返回的也是空列表，只有这两个标记分得出「没搜到」和「没连上」。
        // 分不出来就会因为断一次网把结果区清空，人得再把关键词打一遍。
        val failed = repository.lastError != null || repository.lastFailureWasNetwork
        if (failed) {
            searchError = searchFailureLine(repository.lastError, repository.lastFailureWasNetwork)
            return@LaunchedEffect
        }
        messageHits = fresh
        searchError = null
        ScreenCache.put(cacheKey, SearchCacheEntry(fresh, currentTimeMillis()))
        // 写完顺手把最旧的关键词连同它的值一起丢掉，缓存才不会无界增长。
        SearchCacheKeys.remember(cacheKey, SearchCacheLimit) { stale -> ScreenCache.remove(stale) }
    }

    Scaffold(
        topBar = {
            MessagesTopBar(
                searchActive = searchActive,
                searchKeyword = searchKeyword,
                onSearchKeywordChange = { searchKeyword = it },
                onToggleSearch = {
                    // 放大镜是开关：开着就收起来，收起顺手把字清掉，下次打开是一条空框。
                    if (searchActive) {
                        searchKeyword = ""
                        searchActive = false
                    } else {
                        searchActive = true
                    }
                },
                onCloseSearch = {
                    searchKeyword = ""
                    searchActive = false
                },
                onNewGroup = onOpenCreateGroup,
                onMarkAllRead = {
                    scope.launch {
                        repository.markAllConversationsRead(owner)
                        // 返回值 0 分不清「全都读过了」和「没连上」，看这两个标记才分得清：
                        // 两个都干净才算成功，否则这一页的未读数不能假装已经清掉。
                        if (repository.lastError == null && !repository.lastFailureWasNetwork) {
                            commitConversations(conversations.map { it.copy(unread = 0) })
                            onMessage("已全部标为已读")
                        } else {
                            onMessage("没成功，检查代理后再试")
                        }
                    }
                },
                onOpenGroups = onOpenGroups,
            )
        },
    ) { padding ->
        // 下拉刷新与应用页（`MainScreen` 里那个 `PullToRefreshBox`）是同一个组件、同一套指示器：
        // 两页往下拽的手感从此一致，不再是「应用页有、消息页什么都没有」。下拉之后走的是同一条
        // 加载路径（reloads++），所以缓存、失败原因、骨架收起都只有一套逻辑。
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                if (!refreshing) {
                    refreshing = true
                    loadBusy = true
                    reloads++
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                // 只有「内存没有、盘上也没有」才走到这里（第一次装、或者那份解不出来）。
                // 画骨架而不是转圈：转圈看起来像卡住，骨架至少说明「马上会出现一列会话」。
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(scheme.surfaceContainer),
                        )
                    }
                }
            } else {
                // 整页一条列表：联系人那一排、几个入口行、会话行都在同一个 LazyColumn 里。
                //
                // 为什么不再把头部放在列表外面固定住：`PullToRefreshBox` 的下拉走的是嵌套滚动，
                // 手指按在「没有滚动容器」的那半屏（联系人排、入口行）上时，事件根本传不到它手上——
                // 于是同一个「往下拽」，应用页整页都能刷新，消息页只有下半截能，这就是两页刷新
                // 感觉不一样的原因。并成一条列表之后整页都能拽，也跟抖音消息页一致（头部跟着滚）。
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    loadError?.let { reason ->
                        item(key = "load-error") {
                            Text(
                                text = "后端没有返回消息：$reason",
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            )
                        }
                    }
                    // 搜索失败：结果区还是上一次那一份（比空白有用），原因只占这一行。只在搜索开着时
                    // 画——收起搜索之后它还挂在那儿，人就会以为列表本身出了问题。
                    searchError?.let { reason ->
                        if (searching) {
                            item(key = "search-error") {
                                Text(
                                    text = "搜索没有更新：$reason",
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    color = scheme.error,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    // 「当前无法接收朋友的消息提醒」：系统没给通知权限时才有这一条。抖音那条是提醒
                    // 用户去开；这里点一下就是申请权限——不开也不会假装能收。
                    if (noticesBlocked) {
                        item(key = "notices") { NoticeBanner(onOpen = onEnableNotices) }
                    }
                    item(key = "contacts") {
                        RecentContactsRow(
                            contacts = recentContacts,
                            // 抖音「限时日常」的那一格，在这里是真正的动作：发起群聊。
                            onStartGroup = onOpenCreateGroup,
                            onTapContact = { contact ->
                                // 有关会话就进去；只有关注关系的，开一个私聊再进去——那一格点下去就是
                                // "和这个人说话"，弹一句"还没聊过"等于让用户自己去别处找入口。
                                if (contact.conversationId > 0L) {
                                    onOpenChat(contact.conversationId)
                                } else {
                                    onOpenDm(contact.key)
                                }
                            },
                        )
                    }
                    // 搜索时把两行整块的入口收起来：它们不是搜索结果，留着只会把答案往下挤。
                    if (!searching) {
                        item(key = "entry-inbox") {
                            MessageEntryRow(
                                glyph = FlGlyphs.send,
                                circle = UnreadRed,
                                title = "互动消息",
                                subtitle = inboxPreview.ifBlank { inboxLine(summary) },
                                unread = inboxUnread,
                                onClick = onOpenInteractions,
                            )
                        }
                        // 一个折叠的群都没有时这一行整块不画：入口里没有内容，摆一个「还没有群聊」
                        // 在那儿等于请人点进一个空页面。折叠是人在群设置里自己开的，不是自动的。
                        if (foldedGroups.isNotEmpty()) {
                            item(key = "entry-folded") {
                                MessageEntryRow(
                                    glyph = FlGlyphs.appsList,
                                    circle = GroupGreen,
                                    title = "折叠的群聊",
                                    subtitle = latestGroup?.lastBody?.ifBlank { "还没有消息" } ?: "",
                                    unread = groupUnread,
                                    onClick = {
                                        val group = latestGroup
                                        if (group != null) onOpenChat(group.id) else onMessage("还没有折叠的群聊")
                                    },
                                    // 折叠的群不止一个时，这一行右边再给一个「全部 N 个群」的小字入口：
                                    // 这里只剩最近说话的那个群可以点，剩下的得有地方去。
                                    trailing = {
                                        AllGroupsLink(count = foldedGroups.size, onClick = onOpenGroups)
                                    },
                                )
                            }
                        }
                        // 还没进群的人才需要这一行：进了群的，它就在下面的列表里排在最前面（那才是"置顶"）。
                        // 也不写人数——这个群还在内测，人数是主办方的事。
                        if (officialConversation == null) {
                            item(key = "entry-official") {
                                OfficialGroupRow(
                                    joined = false,
                                    joining = joiningOfficial,
                                    onClick = {
                                        if (!joiningOfficial) {
                                            scope.launch {
                                                joiningOfficial = true
                                                // 后端幂等：点两次、或者其实早就在群里，都返回同一个会话号。
                                                val id = repository.joinByInvite(owner, OfficialGroup.INVITE_CODE)
                                                joiningOfficial = false
                                                if (id != null) {
                                                    reloads++
                                                    onOpenChat(id)
                                                } else {
                                                    onMessage("没进成内测群，检查代理后再试")
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    when {
                        // 有搜索词时列表只剩命中的人、会话与消息；三段都空才说一句，不留一块空白。
                        // 消息那一段还在路上时先不判空——否则打完字会先闪一下"没找到"。
                        searching && hits.isEmpty() && messageHits.isEmpty() && !searchingMessages ->
                            item(key = "search-empty") {
                                Text(
                                    text = "没有找到「${keyword}」",
                                    style = FlText.footnote,
                                    color = scheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                                )
                            }

                        searching -> {
                            items(hits, key = { it.key }) { hit ->
                                SearchHitRow(
                                    hit = hit,
                                    onOpen = {
                                        // 会话就进会话；只有关注关系的人先开私聊（后端 dm_open 幂等）。
                                        val conversation = hit.conversation
                                        if (conversation != null) {
                                            onOpenChat(conversation.id)
                                        } else {
                                            onOpenDm(hit.personId)
                                        }
                                    },
                                )
                            }
                            // 第三段：消息正文的命中。会话与联系人是本地过滤，这一段的每一条都在
                            // "我参与过的会话"里，点一条直接进那个会话——搜索结果只是入口，不在这里读。
                            if (messageHits.isNotEmpty()) {
                                item(key = "hit-messages-head") {
                                    SearchSectionLabel(
                                        text = "消息 · ${messageHits.size} 条",
                                        modifier = Modifier.padding(top = 10.dp),
                                    )
                                }
                                items(messageHits, key = { "msg-${it.id}" }) { hit ->
                                    MessageHitRow(
                                        hit = hit,
                                        onClick = { onOpenChat(hit.conversationId) },
                                    )
                                }
                            } else if (searchingMessages) {
                                item(key = "hit-messages-loading") {
                                    Text(
                                        text = "正在搜索消息…",
                                        style = FlText.caption2,
                                        color = scheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }

                        visibleConversations.isEmpty() ->
                            item(key = "no-chats") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 60.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "还没有私聊，去方案页找人聊聊",
                                        style = FlText.footnote,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }

                        else -> items(visibleConversations, key = { it.conversationKey() }) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                // 只有两个方向都成立的人，名字右边才挂那个灰标。
                                mutual = mutual.contains(conversation.peer) ||
                                    mutual.contains(conversation.peerName),
                                // 拉黑过的行本来就被过滤掉了，长按不到；这里按名单决定文案就行。
                                blocked = conversation.blockedBy(blockedPeers),
                                // 官方内测群也是「钉在最上面」的一行，就该跟手动置顶那行长得一样：
                                // 以前一个灰底一个白底，同一页里两种置顶，看着像两套规则。
                                forcePinnedLook = conversation.title == OfficialGroup.TITLE,
                                onOpen = { onOpenChat(conversation.id) },
                                onTogglePin = { togglePin(conversation) },
                                onToggleMute = { toggleMute(conversation) },
                                onHide = { hideRow(conversation) },
                                onToggleBlock = { toggleBlock(conversation) },
                                onDelete = { deleteRow(conversation) },
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * 顶栏自己画：左边一个「菜单」，中间「消息」，右边搜索与建群。
 *
 * 不用 [io.github.samolego.canta.ui.component.ScreenTopBar]：那个只留一个返回箭头和一个
 * 尾部动作位，抖音这一页是左边菜单、右边两个图标。高度、留白、标题字号照它的那一套
 * （[FlTopBarHeight]、20.dp 边距、[FlText.headline]）。
 *
 * 放大镜把一条搜索框放到这一行下面（[MessagesSearchField]），右边跟一个收起用的叉；菜单按钮
 * 弹一个三项的 [DropdownMenu]，位置贴着按下去的那个按钮，和 CantaTopBar 里那几个菜单一样。
 */
@Composable
private fun MessagesTopBar(
    searchActive: Boolean,
    searchKeyword: String,
    onSearchKeywordChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onNewGroup: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenGroups: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menuExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(FlTopBarHeight).padding(horizontal = 20.dp),
        ) {
            // 菜单和它自己的下拉菜单放在同一个 Box 里，菜单才会贴着手按的那个按钮弹出。
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                FlBarButton(
                    glyph = FlGlyphs.more,
                    contentDescription = "菜单",
                    onClick = { menuExpanded = true },
                    iconSize = 20.dp,
                    filled = false,
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.width(200.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("全部标为已读") },
                        onClick = {
                            menuExpanded = false
                            onMarkAllRead()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("折叠的群聊") },
                        onClick = {
                            menuExpanded = false
                            onOpenGroups()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("发起群聊") },
                        onClick = {
                            menuExpanded = false
                            onNewGroup()
                        },
                    )
                }
            }
            Text(
                text = "消息",
                style = FlText.headline,
                color = scheme.onSurface,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlBarButton(
                    glyph = FlGlyphs.search,
                    contentDescription = "搜索",
                    onClick = onToggleSearch,
                    iconSize = 20.dp,
                    filled = false,
                )
                FlBarButton(
                    glyph = FlGlyphs.addCircle,
                    contentDescription = "新建群聊",
                    onClick = onNewGroup,
                    iconSize = 20.dp,
                    filled = false,
                )
            }
        }
        // 搜索框在这一行下面展开：上面那行的菜单、标题、建群都要留着手按。
        if (searchActive) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MessagesSearchField(
                    value = searchKeyword,
                    onValueChange = onSearchKeywordChange,
                    focusRequester = searchFocusRequester,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FlBarButton(
                    glyph = FlGlyphs.close,
                    contentDescription = "收起搜索",
                    onClick = onCloseSearch,
                    iconSize = 20.dp,
                    filled = false,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // 顶栏下面那条发丝线：列表从线下开始，和参考图一致。
        HorizontalDivider(
            thickness = 0.5.dp,
            color = scheme.outlineVariant.copy(alpha = 0.6f),
        )
    }

    // 展开就把光标放进去：点一下放大镜之后还要再点一下输入框，等于白点一次。
    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
    }
}

/**
 * 顶栏里那条搜索框：形状与尺寸照 [io.github.samolego.canta.ui.component.CantaTopBar] 里同名
 * 那一份——百分之五十圆角的面、左起占位、光标用主题色，只把占位换成这一页要搜的东西。
 *
 * 它只把每一次输入交上去，过滤是页面在本地做的：这条框不发请求。
 */
@Composable
private fun MessagesSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = FlSurfaceLevel.Page.raised.colorOf(scheme)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = "搜索联系人或群聊",
                style = FlText.subheadline,
                color = scheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            textStyle = LocalTextStyle.current.merge(FlText.subheadline.copy(color = scheme.onSurface)),
            singleLine = true,
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
        )
    }
}

/**
 * 「折叠的群聊」那一行右侧的小字入口：这一行只预览最近说话的那个群，其余的从这里进去。
 *
 * 0 个群时调用方不画它，这里也不替它编一句话——没有「全部」可以看。
 */
@Composable
private fun AllGroupsLink(count: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = "全部 $count 个群 ›",
        style = FlText.caption2,
        color = scheme.primary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClickLabel = "全部折叠的群聊", onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** 横排里的一位最近联系人；[conversationId] 为 0 表示只关注过、还没有会话。 */
private data class RecentContact(
    val key: String,
    val label: String,
    val conversationId: Long,
)

/** 会话行在列表里的 key：会话号本身就是唯一的。 */
private fun Conversation.conversationKey(): String = "conversation-$id"

/**
 * 这一行是不是拉黑过的那个人的私聊。
 *
 * 群聊一律不算：群不是一个人，拉黑无从谈起，名单里也不可能出现群。命中身份号或今天用的名字
 * 都算——后端给的 `peer` 和 `peer_name` 是同一件事的两种写法，名单里存的可能只是其中一个。
 */
private fun Conversation.blockedBy(blocked: Set<String>): Boolean =
    !isGroup && blocked.isNotEmpty() &&
        ((peer.isNotEmpty() && peer in blocked) || (peerName.isNotEmpty() && peerName in blocked))

/**
 * 这一行是不是一个折叠起来的群。
 *
 * 折叠与置顶是两件事：置顶是「这件事我先看」，折叠是「这件事平时不用看」。折叠的群整行挪进
 * 「折叠的群聊」那个入口，主列表里不再重复一遍。`folded` 默认 `false`，所以后端还没给这个
 * 字段的会话一律按「没折叠」处理——老会话不会莫名其妙被收进折叠。
 */
private fun Conversation.isFoldedGroup(): Boolean = isGroup && folded

/**
 * 会话列表的排法：置顶的永远在最前，其余按最后一条的时间倒序。
 *
 * 后端给的就是这个顺序，本地改过一次置顶之后也得自己重排一遍，否则点了「置顶」这一行还停在原处。
 * [Conversation.lastAt] 是 `2026-11-22T11:31:07.310336Z` 这样的定长串，字符串倒序就是时间倒序，
 * 不用为了排个序去解析时间。
 */
private fun sortedConversations(list: List<Conversation>): List<Conversation> =
    list.sortedWith(
        compareByDescending<Conversation> { it.pinned }.thenByDescending { it.lastAt },
    )

/**
 * 增量拉取的起点：本地列表里最新的那个最后活跃时间，没有就 null（= 全量）。
 *
 * 用会话自己的 `last_at` 而不是 `ConversationCache` 那份快照的时刻：快照的时刻是**本机时钟**记的
 * 「上一次刷新的时间」，而后端比的是它自己的 `conversations.last_message_at`，两台机器的钟不保证
 * 一致，差几秒就会漏掉几条。`last_at` 是后端给的、定长 UTC 串（`2026-11-22T11:31:07.310336Z`），
 * 字符串比较就是时间比较，直接当 `p_since` 发过去它能认。
 *
 * 空串是"这个会话还没有消息"，不参与取最大：拿它当起点等于从头拉。
 */
private fun sinceOf(conversations: List<Conversation>): String? =
    conversations.map { it.lastAt }.filter { it.isNotEmpty() }.maxOrNull()

/**
 * 刷新时按会话号 upsert：回来的这几行盖掉本地同号的，**没回来的原样留着**。
 *
 * 为什么不能整份替换：轮询那一趟是增量的（后端只下发自 since 以来有动静的会话），整份铺上去
 * 等于把没动静的那几十行全删了，列表会瞬间只剩一条。
 *
 * 为什么值得多这一步"内容没变就沿用原对象"：后端每次都返回新实例，直接铺上去的话列表里每个
 * 元素都是新对象；而 [ConversationRow] 的参数里还有每一次重组新建的 lambda（`onOpen`、`onTogglePin`
 * 那一串），参数相等这一关本来就过不去，能救的只有「这一行的数据还是不是原来那个」。保留原对象
 * 之后，没变的行拿到同一个引用，行内部那点 remember（长按菜单开没开）和点击态才不会被刷新顺手抹掉。
 *
 * 顺序也不在这里定：调用方拿到之后再走一遍 [sortedConversations]（增量回来的几行可能排到任意位置）。
 *
 * **只在增量那一趟用**（`since != null`）。全量那一趟必须整份铺，否则「后端已经不返回的会话」
 * ——被删掉的、退掉的群——永远留在列表上。代价是增量这几轮里删掉的会话要等到下一次全量
 * （进页面、回前台、下拉）才消失，最长一个 30 秒周期。
 */
private fun mergeConversations(
    previous: List<Conversation>,
    fresh: List<Conversation>,
): List<Conversation> {
    if (fresh.isEmpty()) return previous
    if (previous.isEmpty()) return fresh
    val byId = previous.associateBy { it.id }
    val freshIds = fresh.mapTo(mutableSetOf()) { it.id }
    val merged = ArrayList<Conversation>(previous.size + fresh.size)
    fresh.forEach { row ->
        val old = byId[row.id]
        merged += if (old != null && old == row) old else row
    }
    // 没被这次带回来的旧行原样接在后面：它们没变，对象也复用。次序由调用方统一重排。
    previous.forEach { if (it.id !in freshIds) merged += it }
    return merged
}

/**
 * 互相关注的人，按编号和名字各记一份。
 *
 * 后端的会话只带 `peer`（身份）和 `peer_name`（今天用的名字），关注列表给的是 `id` / `name`，
 * 两个字段对得上任何一个就算同一个人。
 */
private fun mutualKeysOf(followers: List<SocialPerson>): Set<String> =
    followers.filter { it.iFollow && it.followsMe }
        .flatMap { listOf(it.id, it.name) }
        .filter { it.isNotEmpty() }
        .toSet()

/**
 * 头像那一排的人：先取聊过的（非群聊会话），再取关注列表里的人，同一个身份只画一格。
 *
 * 聊过的排在前面是有意的——那一格点了能直接进会话；只有关注关系的排在后面，点了会说明还
 * 没聊过。已经聊过的人不会因为出现在关注列表里而画第二遍。
 */
private fun recentContactsOf(
    conversations: List<Conversation>,
    followers: List<SocialPerson>,
): List<RecentContact> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<RecentContact>()
    conversations.filter { !it.isGroup }.take(ContactsFromChats).forEach { conversation ->
        val key = conversation.peer.ifEmpty { conversation.label }
        if (key.isNotEmpty() && seen.add(key)) {
            out += RecentContact(
                key = key,
                label = conversation.label,
                conversationId = conversation.id,
            )
        }
    }
    followers.take(ContactsFromFollows).forEach { person ->
        val key = person.id.ifEmpty { person.name }
        if (key.isNotEmpty() && seen.add(key)) {
            out += RecentContact(key = key, label = person.label, conversationId = 0L)
        }
    }
    return out.take(RecentContactCap)
}

/**
 * 一次「消息」搜索的结果，外加拿到它的时刻。
 *
 * 放 [ScreenCache]，键是 `"screen:search:<owner>:<关键词>"`。为什么要带上时间：单存一份
 * `List<MessageHit>` 的话，缓存分不出新旧，等于把这个关键词永久钉在第一次的答案上；有了时刻
 * 才能只认 [SearchCacheTtlMs] 以内的那一份。
 */
private data class SearchCacheEntry(
    val hits: List<MessageHit>,
    /** 拿到 [hits] 的时刻（[currentTimeMillis]），算新鲜度用。 */
    val at: Long,
)

/** 搜索缓存的键。owner 也在键里：换身份要换整份结果，不能把上一个身份的命中画给这个人。 */
private fun searchCacheKey(owner: String, query: String): String = "screen:search:$owner:$query"

/**
 * 缓存里现有哪些关键词，按写进去的先后排队（队头最旧）。
 *
 * [ScreenCache] 只提供按键读写，没有「一共存了几项」这种视图，也就没法自己淘汰；而关键词是
 * 无限增长的（每敲一次字就是一个新键）。所以这份顺序单独记在这里：写完新条目顺手把最旧的那几个
 * 连同 `ScreenCache` 里的值一起删掉。留着几十个命不中的旧词只是占内存。
 *
 * 和 `ScreenCache` 一样只活在内存、只在主线程访问，所以不加锁。
 */
private object SearchCacheKeys {
    private val order = mutableListOf<String>()

    /** 记下 [key] 刚被写入；超过 [limit] 项时用 [evict] 把最旧的那些丢掉。 */
    fun remember(key: String, limit: Int, evict: (String) -> Unit) {
        order.remove(key)
        order += key
        while (order.size > limit) {
            evict(order.removeAt(0))
        }
    }
}

/**
 * 搜索失败时要说的那一句。
 *
 * 与这一页其它失败提示同一套判断：`lastError` 是后端拒绝的理由，`lastFailureWasNetwork` 是请求
 * 根本没出去。两个都空说明失败得没头没脑，也得给一句实话，不能把上一次的结果冒充成这一次的。
 */
private fun searchFailureLine(error: String?, network: Boolean): String = when {
    network -> "连不上后端，这是上一次的结果"
    error != null -> "后端拒绝：$error"
    else -> "这次没拿到新结果"
}

/**
 * 搜索结果里的一行。
 *
 * 命中一个会话就带着那个 [conversation]（点了进会话）；命中一个只有关注关系的人就只有
 * [personId]（点了先开私聊——后端 `dm_open` 是幂等的，不会一次点出两个会话）。
 */
private data class SearchHit(
    val key: String,
    val label: String,
    val subtitle: String,
    val conversation: Conversation? = null,
    val personId: String = "",
)

/**
 * 本地搜索：在会话列表和「只有关注关系的人」里找名字或身份号含 [keyword] 的那些。
 *
 * 只看已经拉回来的数据，不发请求——搜索框每敲一个字都问一次后端，既慢又会把那点额度吃光。
 * 大小写一律忽略：身份号是小写十六进制，人打出来却不一定是。
 *
 * 群聊也在会话里，所以搜群名同样命中；已经聊过的人不会在人那一半再出现一次。
 */
private fun searchHitsOf(
    conversations: List<Conversation>,
    followers: List<SocialPerson>,
    keyword: String,
): List<SearchHit> {
    fun matches(vararg fields: String): Boolean =
        keyword.isNotEmpty() && fields.any { it.contains(keyword, ignoreCase = true) }

    val hits = mutableListOf<SearchHit>()
    // 两半都往这一个集合里登记 key：会话号与身份号各自唯一，这里只是保险。
    val seen = mutableSetOf<String>()
    conversations.forEach { conversation ->
        if (!matches(conversation.label, conversation.title, conversation.peer, conversation.peerName)) {
            return@forEach
        }
        val key = "search-conversation-" + conversation.id
        if (seen.add(key)) {
            hits += SearchHit(
                key = key,
                label = conversation.label,
                subtitle = conversation.lastBody.ifBlank { "还没有消息" },
                conversation = conversation,
            )
        }
    }
    val chatPeers = conversations.map { it.peer }.filter { it.isNotEmpty() }.toSet()
    followers.forEach { person ->
        // 已经聊过的人在会话那一半已经有一行，这里只补只有关注关系的人。
        if (person.id.isNotEmpty() && chatPeers.contains(person.id)) return@forEach
        if (!matches(person.label, person.name, person.id)) return@forEach
        val key = "search-person-" + person.id.ifEmpty { person.name }
        if (seen.add(key)) {
            hits += SearchHit(
                key = key,
                label = person.label,
                subtitle = if (person.id.isNotEmpty()) "#" + person.id else "关注的人",
                personId = person.id,
            )
        }
    }
    return hits
}

/**
 * 搜索结果的一行：头像、名字、第二行放着最后一句（人那一行是身份号）。
 *
 * 与会话行同一个形状，只是不带长按菜单：搜出来的行点进去就好，置顶、删除留在列表里做。
 */
@Composable
private fun SearchHitRow(hit: SearchHit, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .bottomHairline(scheme.outlineVariant.copy(alpha = 0.6f))
            .clickable(onClickLabel = hit.label, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(label = hit.label)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.label,
                style = FlText.subheadline,
                fontWeight = FlText.emphasized,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = hit.subtitle,
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 搜索结果里分段用的一行小标题（「消息 · 3 条」）。
 *
 * 会话和联系人混在一列里本来就靠头像区分，第三段是正文命中，性质完全不同——不隔一下，
 * 人会以为某条消息也是一个会话。
 */
@Composable
private fun SearchSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = FlText.caption2,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

/**
 * 一条消息命中：谁说的、在哪个会话里、说了什么。
 *
 * 正文只给两行：搜索结果的用处是"找到那条消息"，不是在这儿读完它——点进去就到了原处。
 * 关键词本身不标色：后端是 `ilike` 匹配，大小写和通配符展开之后，前端按字面去找会漏标，
 * 标一半比不标更让人怀疑。
 */
@Composable
private fun MessageHitRow(hit: MessageHit, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .bottomHairline(scheme.outlineVariant.copy(alpha = 0.6f))
            .clickable(onClickLabel = "打开这条消息所在的会话", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        InitialAvatar(label = hit.senderName.ifBlank { hit.sender })
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hit.senderName.ifBlank { "#${hit.sender}" },
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = hit.where,
                    style = FlText.caption2,
                    color = scheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = hit.body,
                style = FlText.caption2,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 页顶那条灰底提示：「当前无法接收朋友的消息提醒　打开提醒 ›」。
 *
 * 照抖音消息页的位置和形状：一条浅灰圆角带，左边一个感叹号，右边蓝色的一句话。它只在系统
 * 真的没给通知权限时出现（判定在调用方），所以这句话永远是真的。
 */
@Composable
private fun NoticeBanner(onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIcon(glyph = FlGlyphs.warning, size = 14.dp, tint = scheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "当前无法接收朋友的消息提醒",
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "打开提醒 ›",
            style = FlText.caption2,
            color = scheme.primary,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onOpen)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

/**
 * 「限时日常」那一格和最近联系人共用的一排。
 *
 * 一个人都没有的时候不整排空着，而是画一格「还没有好友」：一排空的横线看起来像没加载出来。
 */
@Composable
private fun RecentContactsRow(
    contacts: List<RecentContact>,
    onStartGroup: () -> Unit,
    onTapContact: (RecentContact) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "daily") { NewChatCell(onClick = onStartGroup) }
        if (contacts.isEmpty()) {
            item(key = "no-friends") { NoFriendsCell() }
        } else {
            items(contacts, key = { "contact-${it.key}" }) { contact ->
                ContactCell(contact = contact, onClick = { onTapContact(contact) })
            }
        }
    }
}

/** 那排的第一格：抖音「限时日常」的位置换成我们真正的动作——发起群聊。 */
@Composable
private fun NewChatCell(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(ContactCellWidth)
            .clickable(onClickLabel = "发起群聊", onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, scheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(glyph = FlGlyphs.add, size = 18.dp, tint = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "群聊",
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一位最近联系人：首字画圆，下面一行名字。 */
@Composable
private fun ContactCell(contact: RecentContact, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(ContactCellWidth)
            .clickable(onClickLabel = contact.label, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InitialAvatar(label = contact.label)
        Spacer(Modifier.height(6.dp))
        Text(
            text = contact.label,
            style = FlText.caption2,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一个好友都没有时的提示格：灰圆里一个人像，下面一句实话。 */
@Composable
private fun NoFriendsCell() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.width(HintCellWidth).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(scheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(glyph = FlGlyphs.profiles, size = 18.dp, tint = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "还没有好友",
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 官方内测群那一行，钉在消息页最上面。
 *
 * 它和会话行长得像，但不是会话行：没进群的人列表里根本没有这个会话，所以它由 [OfficialGroup]
 * 那份常量长出来，点一下走 `group_invite_join`（幂等）加入再进去；已经在群里就直接进去。
 *
 * **不写人数**：这是内测群，人还少，人数不该摆在谁都能看见的地方。
 */
@Composable
private fun OfficialGroupRow(
    joined: Boolean,
    joining: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .bottomHairline(scheme.outlineVariant.copy(alpha = 0.6f))
            .clickable(
                enabled = !joining,
                onClickLabel = if (joined) "进入内测群" else "加入内测群",
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(label = "测")
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = OfficialGroup.TITLE,
                style = FlText.subheadline,
                fontWeight = FlText.emphasized,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    joining -> "正在加入…"
                    joined -> OfficialGroup.TAGLINE
                    else -> "官方内测群 · 点一下加入"
                },
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (joined) "进入" else "加入",
            style = FlText.caption2,
            fontWeight = FlText.emphasized,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(if (joined) scheme.outline else scheme.primary)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * 整块的一行入口：左边一个色圆里放图标，中间标题与副标题，右边未读数。
 *
 * 互动消息和折叠的群聊共用这一个形状，只是圆底的颜色和图标不同——两行看起来是一对，
 * 读起来也就能一眼分出「别人对我做了什么」和「几个人的群」。
 */
@Composable
private fun MessageEntryRow(
    glyph: FlGlyph,
    circle: Color,
    title: String,
    subtitle: String,
    unread: Int,
    onClick: () -> Unit,
    /** 标题行右端、未读数左边的额外入口；不需要的行走默认的空 lambda。 */
    trailing: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .bottomHairline(scheme.outlineVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(circle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(glyph = glyph, size = 22.dp, tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = FlText.subheadline,
                fontWeight = FlText.emphasized,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
        // 时间只留给有时间的行（会话列表）：这两行是入口，没有「最后一条」可言。
        UnreadBadge(count = unread)
    }
}

/**
 * 一条会话：头像、名字（互相关注带灰标）、最后一句、时间与未读数。
 *
 * 点一下进会话，长按弹五条动作，顺序照抖音的消息页：免打扰 / 置顶 / 不显示 / 拉黑 / 删除。
 * 「标记已读」不在这里——进会话本身就标了，再多一个按钮只是让人多按一次。置顶只靠**色差**：
 * 整行底色换成主色的淡底（[PinnedTintAlpha]），不写字也不点头像上的圆点——色差本身就是标记，
 * 再加一个小点只是多一处要看的东西。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    mutual: Boolean,
    /** 这个会话对面的人是不是已经在本机拉黑名单里；决定菜单里那条写「拉黑」还是「取消拉黑」。 */
    blocked: Boolean,
    /**
     * 画成置顶那一行的样子，但后端并没有把它置顶。
     *
     * 现在只有一个来源：官方内测群——它靠排序钉在最上面（[visibleConversations]），
     * 不是 `pinned`。以前它和手动置顶的群并排在最上面，一个灰底一个白底，看着像两个人
     * 用了两套规则；这里只改样子，不动菜单文案（菜单仍按真实 `pinned` 说话）。
     */
    forcePinnedLook: Boolean = false,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    /** 不显示：消息留着，只是这一行从列表里消失。 */
    onHide: () -> Unit,
    onToggleBlock: () -> Unit,
    /** 删除：清掉自己这边的消息再把行拿走，所以它和「不显示」是两条，不合并。 */
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val pinnedLook = conversation.pinned || forcePinnedLook
    var menuOpen by remember { mutableStateOf(false) }
    // 菜单锚在这一行上：用 Box 包一层，DropdownMenu 才能贴着这一行的边上弹出来。
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    // 置顶只靠色差说话：整行铺一层主色的淡底。以前用的是 surfaceContainerHigh，
                    // 那个和普通行的 surface 在浅色主题里都在白色附近（#F0ECED vs #FCF8F9），
                    // 屏幕上几乎看不出来，置顶等于没标。主题色叠一层深浅主题都成立，一眼分得出。
                    if (pinnedLook) {
                        scheme.primary.copy(alpha = PinnedTintAlpha).compositeOver(scheme.surface)
                    } else {
                        scheme.surface
                    },
                )
                .bottomHairline(scheme.outlineVariant.copy(alpha = 0.6f))
                .combinedClickable(
                    onClickLabel = "打开会话",
                    onLongClickLabel = "会话操作",
                    onLongClick = { menuOpen = true },
                    onClick = onOpen,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                InitialAvatar(label = conversation.label)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.label,
                        style = FlText.subheadline,
                        fontWeight = FlText.emphasized,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (mutual) {
                        Spacer(Modifier.width(6.dp))
                        MutualFollowTag()
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 有人 @ 我时，摘要前面挂一个小标：这是这一行里唯一"要立刻看"的信息，
                    // 只靠红点说不清是闲聊还是点名。和未读数同一个已读判据，不会自相矛盾。
                    if (conversation.unreadMentions > 0) {
                        Text(
                            text = "[有人@我]",
                            style = FlText.caption2,
                            color = scheme.primary,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = conversation.lastBody.ifBlank { "还没有消息" },
                        style = FlText.caption2,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val time = clockOf(conversation.lastAt)
                if (time.isNotEmpty()) {
                    Text(text = time, style = FlText.caption2, color = scheme.outline)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 静音的行照抖音那样：只画一个划掉的铃 + 一个红点，不写数字——"别响"的群
                    // 不需要你数还有几条；不静音的才写数字。
                    if (conversation.muted) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = "已设为免打扰",
                            modifier = Modifier.size(12.dp),
                            tint = scheme.outline,
                        )
                        if (conversation.unread > 0) {
                            Spacer(Modifier.width(4.dp))
                            UnreadDot(size = 8.dp)
                        }
                    } else {
                        UnreadBadge(count = conversation.unread)
                    }
                }
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            // 宽度定死：默认宽度跟着内容走，五条里长短不一，弹出来会一条比一条窄；窄到放不下
            // 「文字 + 图标」时文字就折成两行，一行菜单里出现两行字最难看。
            modifier = Modifier.align(Alignment.TopEnd).width(ConversationMenuWidth),
        ) {
            // 顺序照抖音的消息页：免打扰、置顶、不显示、拉黑、删除。
            ConversationMenuItem(
                label = if (conversation.muted) "取消免打扰" else "设为免打扰",
                onClick = {
                    menuOpen = false
                    onToggleMute()
                },
                // FlGlyphs 里没有铃铛，这一条借用 Material 的图：开着是一条划斜线的铃，
                // 已经静音时换回普通铃铛——菜单写的是「取消」，图标就不该还带那道斜线。
                leadingIcon = {
                    Icon(
                        imageVector = if (conversation.muted) {
                            Icons.Default.Notifications
                        } else {
                            Icons.Default.NotificationsOff
                        },
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            ConversationMenuItem(
                label = if (conversation.pinned) "取消置顶" else "置顶",
                onClick = {
                    menuOpen = false
                    onTogglePin()
                },
                leadingIcon = {
                    FlGlyphIcon(glyph = FlGlyphs.pin, size = 16.dp, tint = scheme.onSurfaceVariant)
                },
            )
            ConversationMenuItem(
                label = "不显示",
                onClick = {
                    menuOpen = false
                    onHide()
                },
                leadingIcon = {
                    FlGlyphIcon(glyph = FlGlyphs.eyeOff, size = 16.dp, tint = scheme.onSurfaceVariant)
                },
            )
            // 群聊不给这一条：拉黑是对一个人做的事，群里没有"那个人"。
            if (!conversation.isGroup) {
                ConversationMenuItem(
                    label = if (blocked) "取消拉黑" else "拉黑",
                    onClick = {
                        menuOpen = false
                        onToggleBlock()
                    },
                    leadingIcon = {
                        FlGlyphIcon(glyph = FlGlyphs.block, size = 16.dp, tint = scheme.onSurfaceVariant)
                    },
                )
            }
            ConversationMenuItem(
                label = "删除",
                danger = true,
                onClick = {
                    menuOpen = false
                    onDelete()
                },
                leadingIcon = {
                    FlGlyphIcon(glyph = FlGlyphs.delete, size = 16.dp, tint = scheme.error)
                },
            )
        }
    }
}

/**
 * 会话长按菜单的一行：左边一个图标，右边一句 13sp 的字，不折行。
 *
 * 图标由调用方给：免打扰用的是 Material 的铃铛（[FlGlyphs] 里没有），其余几条都是 FlGlyphs
 * 里本来就有的图形。文字大小与 [io.github.samolego.canta.ui.screen.InteractionsScreen] 里那几个
 * 菜单一致，两个页面的下拉菜单看起来才是一套。
 */
@Composable
private fun ConversationMenuItem(
    label: String,
    onClick: () -> Unit,
    /** 删除那一行：图标和文字都用 error 色，看得出来它和上面几条不同。 */
    danger: Boolean = false,
    leadingIcon: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (danger) scheme.error else scheme.onSurface,
                maxLines = 1,
            )
        },
        onClick = onClick,
        leadingIcon = leadingIcon,
    )
}

/** 名字右边那个小灰标：两个人互相都关注了，说一句。 */
@Composable
private fun MutualFollowTag() {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = "互相关注",
        style = FlText.caption2,
        color = scheme.outline,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** 头像：名字首字画圆。没有名字（连编号都没有）就画一个问号，不留一个空圆。 */
@Composable
private fun InitialAvatar(label: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(scheme.primary.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.take(1).ifEmpty { "?" },
            style = FlText.subheadline,
            fontWeight = FlText.emphasized,
            color = scheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * 列表右侧那一格时间，**按北京时间**：刚刚 / N分钟前 / 今天 HH:mm / 昨天 HH:mm / MM-dd / yyyy-MM-dd。
 *
 * 换算在 [BeijingTime] 里做（后端给的是 UTC）。以前这里是切字符串，等于把 UTC 的钟点直接当本地
 * 时间画，下午发的消息会显示成上午。
 */
private fun clockOf(stamp: String): String = BeijingTime.listStamp(stamp)

/**
 * 互动消息那一行的副标题：调用方没有现成的一句时，用后端已经数好的四个数字拼一句。
 *
 * 顺序按读者关心的先后：有人回复你比有人点了个赞要紧。一个数字都没有就等于没有新闻，
 * 这时说「还没有新的互动」比列四个 0 诚实。
 */
private fun inboxLine(summary: InboxSummary): String {
    if (summary.total <= 0) return "还没有新的互动"
    val parts = buildList {
        if (summary.replies > 0) add("回复 ${summary.replies}")
        if (summary.planComments > 0) add("评论 ${summary.planComments}")
        val hearts = summary.commentHearts + summary.planHearts
        if (hearts > 0) add("赞 $hearts")
    }
    return parts.joinToString(" · ").ifBlank { "还没有新的互动" }
}
