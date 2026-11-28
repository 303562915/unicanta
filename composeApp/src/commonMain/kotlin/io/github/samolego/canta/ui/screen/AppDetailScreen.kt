package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.bloat.BloatData
import io.github.samolego.canta.data.cache.ScreenCache
import io.github.samolego.canta.data.cache.stableMerge
import kotlinx.serialization.Serializable
import io.github.samolego.canta.data.community.AppNoteStore
import io.github.samolego.canta.data.community.AppReview
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.DescriptionFix
import io.github.samolego.canta.data.community.PackageVotes
import io.github.samolego.canta.data.community.PlanComment
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.data.community.FIRST_REPLY_BATCH
import io.github.samolego.canta.data.community.NEXT_REPLY_BATCH
import io.github.samolego.canta.data.community.threadOf
import io.github.samolego.canta.data.community.threads
import io.github.samolego.canta.data.community.withAnchorFirst
import io.github.samolego.canta.data.community.updatedComment
import io.github.samolego.canta.data.recommend.RecommendedApp
import io.github.samolego.canta.data.recommend.Recommendations
import io.github.samolego.canta.ui.component.AppBadge
import io.github.samolego.canta.ui.component.badgeRiskSummary
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.component.CommentComposer
import io.github.samolego.canta.ui.component.dismissInputOnTap
import io.github.samolego.canta.ui.component.PersonAvatar
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.commentThreads
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlTopBarCompactHeight
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.description_source
import io.github.samolego.canta.generated.resources.description_translated_source
import org.jetbrains.compose.resources.stringResource
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.IdentitySetup
import io.github.samolego.canta.util.currentTimeMillis
import kotlinx.coroutines.launch

/** Green for "the phone was fine without it", the colour of a pass. */
private val CAN_REMOVE = Color(0xFF2FA84F)

/** Red for "removing it broke something". */
private val CANNOT_REMOVE = Color(0xFFE0453B)

/** Height of the two-sided vote bar. */
private val VOTE_BAR = 10.dp

/**
 * 列表里「评论 · N」那一行的位置：0 是这张卡的 hero，「评论」的段标题紧接在它后面。
 *
 * 从互动消息 / 我的评论点一条评论进来时滚到这里——应用页的头部比一屏还长，不滚过去的话，
 * 读者点完还得自己滑半天才看见自己那条。
 */
private const val COMMENT_SECTION_INDEX = 1

/**
 * 这一页上一次画出来的那些东西：两边的票数、本机投的哪一边、评论、描述修正、抽屉里的方案。
 *
 * 只放画一屏要用的只读数据，不放 `MutableState` / 协程 / 回调：这份快照要跨 Composable 实例活着，
 * 而 `remember` 出来的状态属于某一个实例，把它存进进程级缓存等于把上一页的执行挂到下一页上。
 * 本机自己写的那些评论（[AppNoteStore]）也不在里面——它在本地，读一次比查缓存还快，也不算旧。
 */
@Serializable
private data class AppDetailSnapshot(
    val votes: PackageVotes,
    val mine: Boolean?,
    val comments: List<PlanComment>,
    val fixes: List<DescriptionFix>,
    val relatedPlans: List<CommunityPlan>,
    /** 全社区（不分机型）收了这个包的方案：抽屉的第二部分。 */
    val widerPlans: List<CommunityPlan> = emptyList(),
)

/**
 * 这一屏在进程缓存里的槽位。
 *
 * 只按包名分份：本机身份（这一页的 `voter`）是这台设备自己生成的，换身份意味着清数据重装，那时
 * 进程也重启了，缓存跟着没。把 voter 拼进键反而会在身份还没读出来的第一帧查不中，又退回「先空白」。
 */
private fun appDetailCacheKey(packageName: String) = "screen:appdetail:$packageName"

/**
 * 详情抽屉那几个读接口的「新鲜期」。
 *
 * 打开详情是这一屏最频繁的动作——点开一个应用、返回、再点下一个，刚才看过的那一页很快又被点回来，
 * 每回都串行发四个请求（票数、我的票、评论、描述修正），线上量就是这么堆出来的。
 *
 * 60 秒的理由：这些数字一分钟里不会有人去质疑；而「来回翻列表」这一串重复打开全在几十秒内，60 秒
 * 足以把它们全兜住。再长就有实际代价了——投完票、别人留言之后看不出变化；所以真正要求立刻变的那几处
 * 自己会打破 TTL（投票见 `castVote`，发评论 / 点赞 / 删评论见各自的写路径），不靠这个时长兜着。
 */
private const val DETAIL_TTL_MILLIS = 60_000L

/**
 * 一条 TTL 记录：四组结果 + 它们的时间；抽屉里那批方案另记一个时间。
 *
 * 每组都是可空的，因为「这一次没拿到」和「拿到了一个空」不一样：失败的那一组不写进来（留 null），
 * 命中时那一块就不动，等于屏幕上「失败的那一块保留旧值」。方案的时间单独记，是它由另一个 effect 拉，
 * 共用一个时间戳会让先成功的那一组被后失败的那一组顶成过期。
 */
private data class AppDetailCacheEntry(
    val votes: PackageVotes? = null,
    val mine: Boolean? = null,
    val comments: List<PlanComment>? = null,
    val fixes: List<DescriptionFix>? = null,
    val at: Long = 0L,
    val plans: List<CommunityPlan>? = null,
    val plansAt: Long = 0L,
    /** 全社区（不分机型）那一批：和本机型分开记，两趟请求互相不覆盖。 */
    val widerPlans: List<CommunityPlan>? = null,
    val widerAt: Long = 0L,
)

/**
 * 详情抽屉那几个读接口的进程内 TTL 缓存，特意和 [ScreenCache] 分开。
 *
 * [ScreenCache] 回答的是「这一屏上一次画成什么样」，它没有新鲜度——有它照样得把四个请求再发一遍，
 * 那正是要省掉的东西。这里回答的是另一个问题：「这些数字还新不新」。所以它只在内存里：时间戳跨
 * 进程重启没有意义，冷启动该问的还得问。
 *
 * 只按包名分份：本机身份（`voter`）换掉就等于清数据重装，进程也重启了，理由同 [appDetailCacheKey]。
 */
private object AppDetailCache {

    /** 最多记这么多个包名；再写就按时间挤掉最久没更新过的那一条，别让浏览记录无限涨。 */
    private const val MAX_ENTRIES = 50

    private val records = mutableMapOf<String, AppDetailCacheEntry>()

    /**
     * 缓存键：包名 + 机型 + 身份。
     *
     * 光用包名不够。这四个查询里 `packageVotes` / `plansFor` 是**按机型**算的，
     * `myPackageVote` / `appComments` / `descriptionFixes` 是**按身份**算的；而调用方的这三样
     * 都是首帧之后才异步到位的（`appProfile` / `appVoter` 在 `CantaApp` 里由 `LaunchedEffect` 填）。
     * 只按包名分份的话，首帧那趟用「空机型 + 空身份」问到的结果会占住这个键，第二趟（参数到位、
     * effect 重跑）直接命中缓存返回——于是票数按空机型算、「我的票」退回本机笔记，还冻 60 秒。
     * 把三者并进键，参数到位就是一次自然 miss，该重取就重取。
     */
    fun tag(packageName: String, profile: DeviceProfile?, voter: String): String =
        "$packageName|${profile?.model.orEmpty()}|${profile?.rom.orEmpty()}|${profile?.romVersion ?: 0}|$voter"

    /** 这四个查询还新鲜就返回那一条，调用方一个请求都不发；过期或者没记过返回 null。 */
    fun freshDetail(tag: String): AppDetailCacheEntry? =
        records[tag]?.takeIf { isFresh(it.at) }

    /** 抽屉里的方案；失败过的不记，所以这里拿到的一定是问成功过的那一份。 */
    fun freshPlans(tag: String): List<CommunityPlan>? =
        records[tag]?.takeIf { it.plans != null && isFresh(it.plansAt) }?.plans

    /** 全社区那一批；同样只在问成功过时才有值。 */
    fun freshWiderPlans(tag: String): List<CommunityPlan>? =
        records[tag]?.takeIf { it.widerPlans != null && isFresh(it.widerAt) }?.widerPlans

    /**
     * 把这一次真的拿到的组写进缓存；传 null 的组保持上一条里的值（第一次就是空着）。
     *
     * [mineLoaded] 单独一个参数：`mine` 的 null 本身就是「这台手机还没投过票」，和「这一次没问到」
     * 是两件事，光看值分不出来。
     *
     * 时间戳只在**真有一组拿到东西**时才往前推。四组全挂（断网、额度打满）时保持旧时间戳：
     * 否则下一次打开这一页会命中一份「什么都没拿到」的缓存，60 秒内连重试都没有，
     * 屏幕上就一直停在空白或上一条旧值上——那比多问一次贵得多。
     */
    fun putDetail(
        tag: String,
        votes: PackageVotes? = null,
        mine: Boolean? = null,
        mineLoaded: Boolean = false,
        comments: List<PlanComment>? = null,
        fixes: List<DescriptionFix>? = null,
    ) {
        val previous = records[tag]
        val touched = votes != null || mineLoaded || comments != null || fixes != null
        records[tag] = AppDetailCacheEntry(
            votes = votes ?: previous?.votes,
            mine = if (mineLoaded) mine else previous?.mine,
            comments = comments ?: previous?.comments,
            fixes = fixes ?: previous?.fixes,
            at = if (touched) currentTimeMillis() else (previous?.at ?: 0L),
            plans = previous?.plans,
            plansAt = previous?.plansAt ?: 0L,
            widerPlans = previous?.widerPlans,
            widerAt = previous?.widerAt ?: 0L,
        )
        trim()
    }

    /** 方案这一组单独写：它由另一个 effect 拉，时间戳不能和上面四个共用（见 [AppDetailCacheEntry]）。 */
    fun putPlans(tag: String, plans: List<CommunityPlan>) {
        val previous = records[tag]
        records[tag] = AppDetailCacheEntry(
            votes = previous?.votes,
            mine = previous?.mine,
            comments = previous?.comments,
            fixes = previous?.fixes,
            at = previous?.at ?: 0L,
            plans = plans,
            plansAt = currentTimeMillis(),
            widerPlans = previous?.widerPlans,
            widerAt = previous?.widerAt ?: 0L,
        )
        trim()
    }

    /** 全社区那一批也单独写，理由同上：它和本机型是两次独立的请求。 */
    fun putWiderPlans(tag: String, plans: List<CommunityPlan>) {
        val previous = records[tag]
        records[tag] = AppDetailCacheEntry(
            votes = previous?.votes,
            mine = previous?.mine,
            comments = previous?.comments,
            fixes = previous?.fixes,
            at = previous?.at ?: 0L,
            plans = previous?.plans,
            plansAt = previous?.plansAt ?: 0L,
            widerPlans = plans,
            widerAt = currentTimeMillis(),
        )
        trim()
    }

    /** 没有记录时 `at` 是 0：那是「只记过方案」，四个查询仍然算没拉过。 */
    private fun isFresh(at: Long): Boolean = at > 0L && currentTimeMillis() - at < DETAIL_TTL_MILLIS

    /** 条数上限：挤掉「最后一次更新」最早的那一条，浏览过的一堆长尾包名不会一直占着内存。 */
    private fun trim() {
        while (records.size > MAX_ENTRIES) {
            val oldest = records.entries.minByOrNull {
                maxOf(it.value.at, it.value.plansAt, it.value.widerAt)
            } ?: return
            records.remove(oldest.key)
        }
    }
}

/**
 * One app's own page: what it is, whether this phone can live without it, and what other
 * owners of the same phone said about removing it.
 *
 * The vote is two-sided: people say either "the phone was fine without it" (green) or "removing
 * it broke something" (red), and the bar shows both sides at once. Votes are counted twice — on
 * this exact model and ROM version, and on every device — and the page lets the reader switch
 * between the two, because a verdict from a different ROM is context, not advice.
 *
 * The comments below the vote are the same thread a plan has: anybody can answer anybody, the
 * install can delete what it wrote, and a review written here is kept on the device as well, so it
 * is still readable when the backend cannot be reached.
 */
@Composable
fun AppDetailScreen(
    packageName: String,
    app: AppInfo?,
    profile: DeviceProfile?,
    notes: AppNoteStore,
    author: String,
    onOpenUrl: (String) -> Unit,
    onNavigateBack: () -> Unit,
    /** The community backend; null keeps the vote and the reviews local. */
    repository: PlanRepository? = null,
    /** Who the vote and the comments are recorded under: this install's identity number. */
    voter: String = "",
    /** This install's secret, needed to delete a comment it wrote. */
    token: String = "",
    /** Opens the page where this app's description can be corrected and voted on. */
    onOpenDescriptionFixes: (String) -> Unit = {},
    /**
     * 打开「这个包该标成什么级别」那一页：提案 + 投票，票够之后由工作流合并进名单仓库。
     *
     * 与 [onOpenDescriptionFixes] 分开两个入口：一个改说明、一个改徽标，门槛和生效方式都不同。
     */
    onOpenLevelFixes: (String) -> Unit = {},
    /** Opens one of the plans that hold this app, from the related-plans drawer. */
    onOpenPlan: (Long) -> Unit = {},
    /**
     * The comment this page was opened on, from the inbox: its thread is put first and opened, so
     * "somebody answered you" lands on the answer rather than on the top of the page.
     */
    anchorCommentId: Long = 0L,
    /**
     * 聊天那一侧的仓储，只用于「把这个应用分享到某个会话」。
     *
     * null 表示这一页不提供分享——顶栏那个按钮会整颗不画，而不是点了以后报错。
     */
    social: SocialRepository? = null,
    /** 分享时署谁的名字；和 [voter] 是同一个身份，分开一个参数只是让调用点读起来更清楚。 */
    owner: String = "",
    /**
     * 直接在这一页卸载这个包；null 表示这个调用点没有接上卸载能力。
     *
     * 它不是另一套流程：确认框、设备规则、生物识别和结果提示都来自列表页那一套（同一个
     * view model），所以两处点的都是同一件事。
     */
    onUninstall: (() -> Unit)? = null,
    /** 已卸载的包直接装回去；null 与 [onUninstall] 同理。 */
    onReinstall: (() -> Unit)? = null,
    /**
     * 名字与头像设过了没有——没设过这一页的评论发不出去（见 [IdentitySetup]）。
     *
     * 默认 [IdentitySetup.UNSET] 就是"没设过"：漏接的调用点宁可发不出去，也不给一条顶着 `#10000`
     * 的评论。上层传的是 `settingsViewModel.identitySetup`，改完名字当场就放开。
     */
    setupGate: IdentitySetup = IdentitySetup.UNSET,
    /**
     * 这台手机**没有**这个包时，用来画说明与级别的那条总库条目。
     *
     * 为什么需要它：方案详情里点一个包就进这一页，而方案常常带着别的机型才有的包
     * （比如 `com.qti.qcc` 这种高通平台组件）。那种包在本机扫不到，[app] 是 null，于是
     * 「这个包是什么」整段不画——用户看到的就是点进来一片空白。总库是按包名索引的，
     * 与这台机器装没装无关，所以拿它兜底：没有包，也还有“它是什么、能不能删”。
     */
    fallback: BloatData? = null,
) {
    val scope = rememberCoroutineScope()
    // 这个包上一次画出来的那一份（进程级，见 [ScreenCache]）：有它就先画它，网络回来的那一版再
    // 顶上去。这一页每次被导航进来都是新的 Composable 实例，`remember` 跟着一起没了，所以快照
    // 不能放在这里。
    val cacheKey = appDetailCacheKey(packageName)
    // 没有后端时不用缓存：快照里存的都是社区内容，这一屏不会画它们。
    val cached = remember(packageName, repository) {
        if (repository == null) null else ScreenCache.get<AppDetailSnapshot>(cacheKey)
    }
    var reviews by remember { mutableStateOf<List<AppReview>>(emptyList()) }
    var comments by remember(packageName) { mutableStateOf(cached?.comments.orEmpty()) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var submitFailed by remember { mutableStateOf(false) }
    // Whether the sticker panel under the bar is open; the page closes it when a tap lands outside.
    var pickerOpen by remember { mutableStateOf(false) }
    /** The comment being answered; null while the composer writes a new one. */
    var replyTo by remember { mutableStateOf<PlanComment?>(null) }
    var pendingDelete by remember { mutableStateOf<PlanComment?>(null) }
    // What this install said about the app, and what everybody else said. Both start from the cached
    // screen: the counts beside the buttons are the first thing that reads as "this page is loaded".
    var mine by remember(packageName) { mutableStateOf(cached?.mine) }
    var votes by remember(packageName) { mutableStateOf(cached?.votes ?: PackageVotes.EMPTY) }
    var voting by remember { mutableStateOf(false) }
    /**
     * Which votes and comments the page shows: every device by default, because a thread narrowed to
     * one model is usually empty, and an empty thread reads as "nobody uses this" when the rest of
     * the world is one tap away. 0 is this model and ROM, 1 every device, 2 one system version.
     */
    var voteScope by remember(packageName) { mutableIntStateOf(1) }
    var systemsExpanded by remember(packageName) { mutableStateOf(false) }
    val selectedSystemState = remember(packageName) { mutableStateOf("") }
    val selectedSystem = selectedSystemState.value
    val onSystemChange: (String) -> Unit = { selectedSystemState.value = it }
    var recommendations by remember { mutableStateOf<Recommendations?>(null) }
    /**
     * The community's corrections of this app's description: the accepted one replaces the published
     * line, the rest are counted for the entry that opens their page.
     */
    var descriptionFixes by remember(packageName) { mutableStateOf(cached?.fixes.orEmpty()) }
    // Why the last vote or comment did not go through, when the backend said something.
    var voteError by remember { mutableStateOf<String?>(null) }
    var sendError by remember { mutableStateOf<String?>(null) }
    /** 分享到聊天的会话选择开着没有。 */
    var sharing by remember { mutableStateOf(false) }
    /** Threads the reader opened with "展开 N 条回复"; the rest keep their answers folded. */
    var expandedThreads by remember { mutableStateOf(setOf<Long>()) }
    /** How many answers each open thread shows; absent means the first batch. */
    var shownReplies by remember { mutableStateOf(mapOf<Long, Int>()) }
    /**
     * Comments this install put away with the broken heart: a personal hide, this session only —
     * nothing on the server changes, and the page forgets it on the way out.
     */
    var hiddenComments by remember { mutableStateOf(setOf<Long>()) }
    /**
     * The plans of this phone that hold this app. The page answers "can this phone live without it";
     * the plans answer the other half — "who already proved a whole set that drops it", which is the
     * step a reader takes after one green vote. Loaded with the page so the drawer opens instantly.
     */
    var relatedPlans by remember(packageName) { mutableStateOf(cached?.relatedPlans.orEmpty()) }
    /**
     * 全社区里收了这个包的方案，不分机型。本机型那一批只有「同一台手机」的方案，对大多数人来说是空的；
     * 别人用老机型、别的 ROM 写的方案照样能抄（包名和顺序都在）。所以抽屉分两层：本机型在上，全社区在下。
     */
    var widerPlans by remember(packageName) { mutableStateOf(cached?.widerPlans.orEmpty()) }
    var widerLoading by remember(packageName) { mutableStateOf(false) }
    var relatedLoading by remember(packageName) { mutableStateOf(false) }
    /** Whether the related-plans drawer is out; it closes on the scrim, on the ×, and on a tap on a plan. */
    var drawerOpen by remember(packageName) { mutableStateOf(false) }

    /**
     * 把这一屏当前画着的东西写回进程缓存。
     *
     * 两个加载 effect 各管一半，所以不各存各的——那样谁后回来都会把另一半覆盖成过期的值。投票、
     * 发评论、删评论之后也走这里：否则下次进来先画出来的还是改动之前那一份，看着像回退。
     */
    fun saveToCache() {
        ScreenCache.put(
            cacheKey,
            AppDetailSnapshot(
                votes = votes,
                mine = mine,
                comments = comments,
                fixes = descriptionFixes,
                relatedPlans = relatedPlans,
                widerPlans = widerPlans,
            ),
        )
    }

    // 详情缓存的键。带机型与身份：这两样在首帧之后才异步到位，只按包名分份会让「空机型 + 空身份」
    // 那一趟的结果占住键位（见 [AppDetailCache.tag]）。每次重组按当前值重算，参数变了自然换键。
    //
    // 声明必须在下面的 LaunchedEffect 之前：Kotlin 的局部变量先声明后使用，而这个 composable
    // 里第一个用到它的 effect 在 415 行，原来把它写在 448 行，编译直接报未解析。
    val cacheTag = AppDetailCache.tag(packageName, profile, voter)

    LaunchedEffect(packageName, profile, repository) {
        val repo = repository ?: return@LaunchedEffect
        // 两批分开取：本机型（`plansFor`，按型号 + ROM + 版本匹配）与全社区（`plansForPackage`，
        // 不看机型）。本机型那批为空是常态——社区里同一台手机的方案很少——所以抽屉不能只画这一层，
        // 否则大多数人打开都看到「还没有这样的方案」，而这个包其实早被别的机型写进去过很多次。
        val warmPlans = AppDetailCache.freshPlans(cacheTag)
        val warmWider = AppDetailCache.freshWiderPlans(cacheTag)
        if (warmPlans != null && warmWider != null) {
            relatedPlans = warmPlans
            widerPlans = warmWider
            return@LaunchedEffect
        }
        // 有缓存就别点亮「正在读」：抽屉一打开先看到上一次的数字，刷新在背后发生。
        if (warmPlans == null && relatedPlans.isEmpty()) relatedLoading = true
        if (warmWider == null && widerPlans.isEmpty()) widerLoading = true
        if (warmPlans == null) {
            // The plans come back for this model and build only, which is the scope the first
            // section claims: a plan for another phone would put a set on screen that was never
            // proven here.
            val fresh = repo.plansFor(profile).filter { packageName in it.packages }
            // 失败时**不覆盖**：`plansFor` 拉挂了给的也是空列表，照着它画就等于因为一次断网把抽屉
            // 清空，而空抽屉写的是「本机型还没有这样的方案」——一句凭空的结论。
            if (repo.lastError == null && !repo.lastFailureWasNetwork) {
                relatedPlans = fresh
                AppDetailCache.putPlans(cacheTag, fresh)
            }
            relatedLoading = false
        }
        if (warmWider == null) {
            val wider = repo.plansForPackage(packageName)?.filter { it.id !in relatedPlans.map { p -> p.id } }
            // 同一套「失败不覆盖」：断网时保留上一次的名单，不画成「全社区都没有」。
            if (wider != null && repo.lastError == null && !repo.lastFailureWasNetwork) {
                widerPlans = wider
                AppDetailCache.putWiderPlans(cacheTag, wider)
            }
            widerLoading = false
        }
        saveToCache()
    }

    LaunchedEffect(packageName) {
        recommendations = Recommendations.bundled()
    }

    /** Where this page's unsent comment is kept; see [AppNoteStore.draftOf]. */
    val draftKey = "app:$packageName"

    // Half-typed text survives leaving the page: this is the page people leave to go and look
    // something up, which is exactly when a comment gets abandoned by accident.
    LaunchedEffect(draftKey) {
        notes.ensureReady()
        if (draft.isEmpty()) draft = notes.draftOf(draftKey)
    }
    LaunchedEffect(draft, draftKey) {
        notes.setDraft(draftKey, draft)
    }

    LaunchedEffect(packageName, voter) {
        // 本机笔记每次都读：它在本地，很快，也跟后端无关，不算「旧数据」。
        reviews = notes.reviewsOf(packageName)
        val repo = repository
        if (repo == null) {
            // 没有后端：这一页只剩本机自己的记录，缓存里那些社区内容在这一屏上不再成立。
            votes = PackageVotes.EMPTY
            mine = notes.packageVerdict(packageName)
            comments = emptyList()
            descriptionFixes = emptyList()
            return@LaunchedEffect
        }
        // 60 秒内拉过这一页就直接用那一份画，一个请求都不发——这是省掉那几万次调用的地方。
        val warm = AppDetailCache.freshDetail(cacheTag)
        if (warm != null) {
            warm.votes?.let { votes = it }
            warm.comments?.let { comments = it }
            warm.fixes?.let { descriptionFixes = it }
            // 缓存里 `mine` 是 null 表示「还没投过」或者「上一次没问着后端」，两种都退回本机笔记，
            // 和下面拉取路径的兜底是同一句。
            mine = warm.mine ?: notes.packageVerdict(packageName)
            return@LaunchedEffect
        }
        // 四个查询各自记成败：`lastError` / `lastFailureWasNetwork` 每调一次就被重写，所以每次
        // 调完立刻读一遍。失败的那一块保留屏幕上原来那份（第一次进来时就是缓存），别的照常更新
        // ——一个查询挂了不该把整页换回空白。
        val fetchedVotes = repo.packageVotes(packageName, profile)
        val votesOk = repo.lastError == null && !repo.lastFailureWasNetwork
        val backendVote = if (voter.isNotEmpty()) repo.myPackageVote(packageName, voter) else null
        val voteOk = repo.lastError == null && !repo.lastFailureWasNetwork
        val fetchedComments = repo.appComments(packageName, voter)
        val commentsOk = repo.lastError == null && !repo.lastFailureWasNetwork
        val fetchedFixes = repo.descriptionFixes(packageName, voter)
        val fixesOk = repo.lastError == null && !repo.lastFailureWasNetwork

        if (votesOk && fetchedVotes != null) votes = fetchedVotes
        // 就地合并：没变的评论沿用旧实例，评论区不整块重画。
        if (commentsOk) {
            val mergedComments = stableMerge(comments, fetchedComments) { it.id }
            if (mergedComments !== comments) comments = mergedComments
        }
        if (fixesOk && fetchedFixes != null) descriptionFixes = fetchedFixes
        // The backend's copy of this install's vote wins over the local one: it is what the counts
        // next to it were computed from, and it survives a reinstall. 问不到的时候保留屏幕上那一份。
        if (voter.isEmpty() || voteOk) {
            mine = backendVote ?: notes.packageVerdict(packageName)
        }
        // 只写这一次真的拿到的组：失败的那一组留 null，缓存里上一次的值不动，和屏幕上留着旧的
        // 是同一件事；四组全挂时这次调用不会把时间戳往前推，下次打开照旧重试。
        AppDetailCache.putDetail(
            tag = cacheTag,
            votes = fetchedVotes.takeIf { votesOk },
            mine = mine,
            mineLoaded = voter.isEmpty() || voteOk,
            comments = fetchedComments.takeIf { commentsOk },
            fixes = fetchedFixes.takeIf { fixesOk },
        )
        saveToCache()
    }

    val castVote: (Boolean) -> Unit = { canRemove ->
        if (!voting) {
            scope.launch {
                voting = true
                voteError = null
                val fresh = repository?.votePackage(packageName, voter, canRemove, profile)
                if (fresh == null) {
                    // Nothing was recorded: keep the page showing what the server still says.
                    voteError = repository?.lastError?.let { "投票没记上：$it" } ?: "投票没发出去，检查一下网络。"
                    if (repository == null) {
                        // No backend configured: the vote is this phone's own note, as before.
                        mine = canRemove
                        notes.setPackageVerdict(packageName, canRemove)
                    }
                } else {
                    mine = canRemove
                    votes = fresh
                    notes.setPackageVerdict(packageName, canRemove)
                    // 票数变了，缓存也跟着变：否则下次进来先画出来的是投之前那个数。
                    saveToCache()
                    // 顺手打破 TTL：投票返回的就是新鲜计数，没有理由再等 60 秒才看到自己那一票。
                    AppDetailCache.putDetail(
                        tag = cacheTag,
                        votes = fresh,
                        mine = canRemove,
                        mineLoaded = true,
                    )
                }
                voting = false
            }
        }
    }

    val send: () -> Unit = {
        val body = draft.trim()
        // 名字/头像没设完：连本地那份也不写——写了就是一条谁都没收到的"我的评论"。
        if (!setupGate.ready) {
            sendError = setupGate.refusal
            submitFailed = true
        } else if (body.isNotEmpty() && !sending) {
            val signature = author.ifEmpty { "u-anon" }
            scope.launch {
                sending = true
                // The device copy first: a review written offline is still this install's, and it
                // is what the page falls back to while the community thread is out of reach.
                notes.addReview(
                    packageName,
                    AppReview(
                        author = signature,
                        body = body,
                        model = profile?.displayModel.orEmpty(),
                        sdk = profile?.sdk ?: 0,
                        createdAt = "",
                        removable = mine == true,
                        system = profile?.systemLabel().orEmpty(),
                    ),
                )
                reviews = notes.reviewsOf(packageName)
                val posted = repository?.commentOnApp(
                    pkg = packageName,
                    body = body,
                    author = signature,
                    authorId = voter,
                    replyTo = replyTo?.id ?: 0L,
                    deviceModel = profile?.displayModel.orEmpty(),
                    systemName = profile?.systemLabel().orEmpty(),
                    replyToName = replyTo?.author.orEmpty(),
                )
                submitFailed = repository != null && posted == null
                if (submitFailed) {
                    // "网络问题" is wrong half the time: the backend names the actual refusal.
                    sendError = repository?.lastError?.let { "发送失败：$it" } ?: "发送失败，检查一下网络再试"
                } else {
                    sendError = null
                    draft = ""
                    replyTo = null
                    // 重新拉一遍线程；拉不到就留着刚才那份，别把已经读到的评论清空。
                    repository?.let { repo ->
                        val latest = repo.appComments(packageName, voter)
                        if (repo.lastError == null && !repo.lastFailureWasNetwork) {
                            comments = latest
                            // 刚发出去那条要能马上再看见：缓存里的评论列表比这一份旧，写回去。
                            AppDetailCache.putDetail(tag = cacheTag, comments = latest)
                        }
                    }
                    saveToCache()
                }
                sending = false
            }
        }
    }

    val likeComment: (PlanComment) -> Unit = { comment ->
        if (comment.id > 0L) {
            val wanted = !comment.liked
            // The page answers the tap at once; the backend's count corrects it when it answers.
            comments = comments.updatedComment(comment.id) {
                it.copy(liked = wanted, likes = (it.likes + if (wanted) 1 else -1).coerceAtLeast(0))
            }
            scope.launch {
                val result = repository?.likeComment(comment.id, voter, wanted)
                if (result != null) {
                    comments = comments.updatedComment(comment.id) {
                        it.copy(liked = result.liked, likes = result.likes)
                    }
                    // 心形和计数都落到缓存里，切回来不会先画成没点过的样子。
                    saveToCache()
                    AppDetailCache.putDetail(tag = cacheTag, comments = comments)
                }
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
                        repository?.deleteComment(comment.id, voter, token)
                        // 删完重拉；拉不到就保留原样，缓存也不动——屏幕上的东西没变，缓存不该变。
                        repository?.let { repo ->
                            val latest = repo.appComments(packageName, voter)
                            if (repo.lastError == null && !repo.lastFailureWasNetwork) {
                                comments = latest
                                // 删掉的那一条不能靠 60 秒缓存又冒出来，这一份比缓存里的新。
                                AppDetailCache.putDetail(tag = cacheTag, comments = latest)
                                saveToCache()
                            }
                        }
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
        ) {
            Text(
                text = "「${comment.body.take(40)}」会被删除，它下面的回复一起去掉。",
                fontSize = 12.sp,
            )
        }
    }

    Scaffold(
        // The title bar floats over the content as a transparent layer, like the plan page: what
        // scrolls up under it stays readable through it, and the page's first card starts just
        // under the status bar.
        bottomBar = {
            CommentComposer(
                draft = draft,
                onDraftChange = {
                    draft = it
                    if (submitFailed) submitFailed = false
                },
                onSend = send,
                replyToName = replyTo?.author.orEmpty(),
                onCancelReply = { replyTo = null },
                sending = sending,
                failed = submitFailed,
                failLabel = sendError ?: "发送失败，检查一下网络再试",
                pickerOpen = pickerOpen,
                onPickerOpenChange = { pickerOpen = it },
                gateHint = setupGate.prompt,
            )
        },
    ) { padding ->
        // The thread follows the same device scope as the vote above it: with "只看本机" picked, a
        // Redmi K70 page lists what K70 owners wrote, not what a Redmi K30 Ultra wrote.
        val visibleComments = remember(comments, voteScope, selectedSystem, profile?.displayModel) {
            scopedToDevice(comments, voteScope, selectedSystem, profile?.displayModel.orEmpty())
        }
        val threads = remember(visibleComments, anchorCommentId) {
            visibleComments.threads().withAnchorFirst(anchorCommentId)
        }
        // The thread the reader came for is opened, not folded: an answer reached from the inbox is
        // behind "展开 N 条回复", and having to open it again means the tap did not really take them
        // there.
        //
        // [visibleComments] is part of the key on purpose: on the first frame the comments have not
        // arrived yet, so an effect keyed on the anchor alone never finds the row and leaves the
        // thread folded.
        //
        // 找到那一条之后才滚：锚点是一条已经被删掉的旧评论时，`threadOf` 给 null，这时滚到评论区
        // 只会让人落到一段跟他无关的评论里，停在页顶反而更诚实。
        var anchorOpened by remember(anchorCommentId) { mutableStateOf(false) }
        // 评论那一段的落点：头一张卡是 hero，「评论 · N」那一行接着它。
        val commentListState = rememberLazyListState()
        // 闪光：哪一行（[flashId]）该亮，亮度从 [flash] 里读。0L 表示没有行在亮。
        var flashId by remember { mutableStateOf(0L) }
        val flash = remember { Animatable(0f) }
        LaunchedEffect(anchorCommentId, threads) {
            if (anchorCommentId <= 0L || anchorOpened) return@LaunchedEffect
            val anchored = threads.threadOf(anchorCommentId) ?: return@LaunchedEffect
            anchorOpened = true
            expandedThreads = expandedThreads + anchored.comment.id
            // 点一条评论进来，落点是评论那一段，不是这页的顶上：应用页的头几张卡（描述、投票、
            // 修正）加起来比一屏还长，只把线程排到最前而不滚过去，人还是要自己滑半天才看见自己那条。
            //
            // 目标是锚点那一行本身，不是「评论 · N」那一行：`withAnchorFirst` 把线程排到第一，
            // 所以是评论段头下面一行；段头和线程之间还可能有那行「只看本机：另有 N 条…」。
            val scopedGap = if (visibleComments.size < comments.size) 1 else 0
            val targetIndex = COMMENT_SECTION_INDEX + 1 + scopedGap
            commentListState.scrollToItem(targetIndex)
            // 再补一段 scrollBy 把这一行挪到竖直中间：负偏移的 scrollToItem 会被量测夹回 0。
            val layout = commentListState.layoutInfo
            val row = layout.visibleItemsInfo.firstOrNull { it.index == targetIndex }
            val viewport = layout.viewportSize.height
            if (row != null && viewport > 0) {
                val delta = (row.offset + row.size / 2f) - viewport / 2f
                if (kotlin.math.abs(delta) > 1f) commentListState.scrollBy(delta)
            }
            flashId = anchorCommentId
        }
        // 闪光单独一个 effect，只跟 [flashId] 走：上面那个 effect 找的是线程，两件事互不牵连。
        LaunchedEffect(flashId) {
            if (flashId == 0L) return@LaunchedEffect
            repeat(2) {
                flash.animateTo(0.9f, tween(170))
                flash.animateTo(0f, tween(240))
            }
            flashId = 0L
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                // 点页面空白：不只收表情面板，连输入框的焦点和输入法一起收掉（见 dismissInputOnTap）。
                .dismissInputOnTap { pickerOpen = false },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = commentListState,
                // The slim bar's height is exactly the content's top edge: no gap of its own.
                contentPadding = PaddingValues(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + FlTopBarCompactHeight - 8.dp,
                    bottom = 16.dp,
                ),
            ) {
            item {
                AppHero(
                    app = app,
                    packageName = packageName,
                    recommendations = recommendations,
                    onOpenRecommendation = { onOpenUrl(it.url) },
                    selectedSystem = selectedSystem,
                    onSystemChange = onSystemChange,
                    systemsExpanded = systemsExpanded,
                    onSystemsExpandedChange = { systemsExpanded = it },
                    votes = votes,
                    scope = voteScope,
                    onScopeChange = { voteScope = it },
                    profile = profile,
                    mine = mine,
                    voting = voting,
                    voteError = voteError,
                    onVote = castVote,
                    fixes = descriptionFixes,
                    onOpenDescriptionFixes = { onOpenDescriptionFixes(packageName) },
                    onOpenLevelFixes = { onOpenLevelFixes(packageName) },
                    onUninstall = onUninstall,
                    onReinstall = onReinstall,
                    // 本机没有这个包（方案里带着别的机型的包）时，说明与级别从总库那条取。
                    fallback = fallback,
                )
            }
            item { SectionHeading("评论 · ${visibleComments.size}") }
            // The count above is the scoped one; say what it left out, so a short thread does not
            // read as "nobody else wrote anything" when the rest is one tap away.
            if (visibleComments.size < comments.size) {
                item {
                    Text(
                        text = "只看本机：另有 ${comments.size - visibleComments.size} 条来自其他机型或系统版本，切到「全部机型」查看。",
                        style = FlText.caption2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
            when {
                threads.isNotEmpty() -> commentThreads(
                    threads = threads,
                    me = author,
                    // 评论线程自己那一格缓存：这一页有页面级快照，但评论是它单独一批拉回来的，
                    // 换了机型/视野重进时先垫上一次那批，省一次空白。
                    cacheTarget = packageName,
                    expanded = expandedThreads,
                    shownReplies = shownReplies,
                    onShowMoreReplies = { key ->
                        shownReplies = shownReplies + (key to ((shownReplies[key] ?: FIRST_REPLY_BATCH) + NEXT_REPLY_BATCH))
                    },
                    onToggleThread = { key ->
                        if (key in expandedThreads) shownReplies = shownReplies - key
                        expandedThreads =
                            if (key in expandedThreads) expandedThreads - key else expandedThreads + key
                    },
                    onReply = { comment ->
                        replyTo = comment
                        submitFailed = false
                    },
                    onDelete = { comment -> pendingDelete = comment },
                    onLike = likeComment,
                    hidden = hiddenComments,
                    onHide = { key ->
                        hiddenComments = if (key in hiddenComments) hiddenComments - key else hiddenComments + key
                    },
                    highlightId = flashId,
                    highlight = flash.value,
                )

                // Nothing from the community: what this install wrote itself is still worth
                // showing, and it is all there is until the thread loads again.
                reviews.isNotEmpty() -> {
                    item {
                        Text(
                            text = "还没有别人的评论，下面是本机写过的：",
                            style = FlText.caption2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    items(reviews) { review -> ReviewRow(review) }
                }

                else -> item {
                    Text(
                        text = "还没有人评论这个应用。写一条，告诉别人它在你的机型上是什么样。",
                        style = FlText.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            }
            // The floating title bar, drawn last so it sits over the scroll.
            ScreenTopBar(
                title = app?.name ?: packageName,
                onNavigateBack = onNavigateBack,
                height = FlTopBarCompactHeight,
                actions = {
                    // The glyph plus the count: the number is the reason to open the drawer, and a
                    // bare icon would leave "are there even any plans with this app?" unanswered
                    // until after the tap.
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable { drawerOpen = true }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAddCheck,
                            contentDescription = "包含这个应用的方案",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        val planTotal = relatedPlans.size + widerPlans.size
                        if (planTotal > 0) {
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "$planTotal",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
            // The drawer: a scrim that fades and a panel that slides in from the right, both drawn
            // over the bar — a surface opened by the bar should be able to cover it.
            AnimatedVisibility(
                visible = drawerOpen,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable { drawerOpen = false },
                )
            }
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                RelatedPlansPanel(
                    plans = relatedPlans,
                    widerPlans = widerPlans,
                    loading = relatedLoading,
                    widerLoading = widerLoading,
                    onOpenPlan = { id ->
                        drawerOpen = false
                        onOpenPlan(id)
                    },
                    onClose = { drawerOpen = false },
                )
            }
        }
    }
}

/** The app itself: icon, name, package, version, size, and the two-sided vote about removing it. */
@Composable
private fun AppHero(
    app: AppInfo?,
    packageName: String,
    recommendations: Recommendations?,
    onOpenRecommendation: (RecommendedApp) -> Unit,
    selectedSystem: String,
    onSystemChange: (String) -> Unit,
    systemsExpanded: Boolean,
    onSystemsExpandedChange: (Boolean) -> Unit,
    votes: PackageVotes,
    scope: Int,
    onScopeChange: (Int) -> Unit,
    profile: DeviceProfile?,
    mine: Boolean?,
    voting: Boolean,
    voteError: String? = null,
    onVote: (Boolean) -> Unit,
    /** The community's corrections of this app's description; empty when nobody filed one. */
    fixes: List<DescriptionFix> = emptyList(),
    onOpenDescriptionFixes: () -> Unit = {},
    /** 打开「级别修正」那一页；这一页自己不发请求，只在点开时联网。 */
    onOpenLevelFixes: () -> Unit = {},
    /** 卸载这个包；null 表示这一页不提供这个动作。 */
    onUninstall: (() -> Unit)? = null,
    /** 把已卸载的包装回去；null 与 [onUninstall] 同理。 */
    onReinstall: (() -> Unit)? = null,
    /** 本机没有这个包时，总库里那一条（说明与级别都从它取）。 */
    fallback: BloatData? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val recommended = remember(recommendations, packageName) {
        recommendations?.apps?.firstOrNull { packageName in it.replacesPackages }
    }
    val appliedFix = remember(fixes) { fixes.firstOrNull { it.applied } }
    val uninstalled = app?.isUninstalled == true
    // 本机扫不到这个包，但总库认识它：级别与说明照样画出来，别让人点进来只看到一行包名。
    val badge = app?.badgeInfo ?: fallback?.badgeInfo
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 图标和它的状态一起：那个「未卸载 / 已卸载」的胶囊就贴在图标底下，说明的是这颗
            // 图标本身；右侧那块位置留给真正能做的事——卸载，或者把它装回去。
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (app != null) {
                    AppIconImage(app, size = 56.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.surfaceContainerHighest),
                    )
                }
                if (app != null) {
                    Spacer(Modifier.height(5.dp))
                    UninstallStatus(app)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app?.name ?: packageName,
                    style = FlText.title3,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = packageName,
                    style = FlText.caption2.copy(fontFamily = FontFamily.Monospace),
                    // 卸载完了就划掉：包名上一条和状态同一个颜色的横杠，一眼能看出这一页说的是
                    // 一个这台手机上已经没有了的包。
                    color = if (uninstalled) CANNOT_REMOVE else scheme.onSurfaceVariant,
                    textDecoration = if (uninstalled) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val facts = buildList {
                    app?.let { add("v${it.versionName}") }
                    app?.apkSize?.let { add(formatSize(it)) }
                    if (app?.isSystemApp == true) add("系统应用")
                }
                if (facts.isNotEmpty()) {
                    Text(
                        text = facts.joinToString(" · "),
                        style = FlText.caption1,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (app != null) {
                Spacer(Modifier.width(8.dp))
                UninstallAction(
                    uninstalled = uninstalled,
                    onUninstall = onUninstall,
                    onReinstall = onReinstall,
                )
            }
        }

        // What can stand in for this app, as a card of its own: the tile, the name, and a plain
        // "推荐替换" that says what tapping it does. It used to be a bare arrow of text.
        recommended?.let { suggestion ->
            Spacer(Modifier.height(10.dp))
            ReplacementRow(suggestion = suggestion, onOpen = { onOpenRecommendation(suggestion) })
        }

        badge?.let { shown ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                AppBadge(shown)
                Spacer(Modifier.width(8.dp))
                Column {
                    // 先一句「这台机器会怎样」：会不会破坏功能、会不会卡在开机。名单自己的定义
                    // （"breaks obscure or minor parts of functionality"）放在它下面当补充——
                    // 定义解释的是档位，这句解释的是删了之后会发生什么。
                    Text(
                        text = badgeRiskSummary(shown),
                        style = FlText.caption1.copy(fontWeight = FlText.emphasized),
                        color = scheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(shown.description),
                        style = FlText.caption1,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        // The accepted correction, when there is one, stands in for the published line: that is the
        // whole point of voting on it. The source is named either way, so the reader knows whose
        // words these are.
        val shownDescription = appliedFix?.body?.takeIf { it.isNotBlank() }
            ?: app?.description?.takeIf { it.isNotBlank() }
            ?: fallback?.description?.takeIf { it.isNotBlank() }
        if (shownDescription != null) {
            Spacer(Modifier.height(8.dp))
            io.github.samolego.canta.ui.component.text.UrlText(
                text = shownDescription,
                style = FlText.footnote,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (appliedFix != null) {
                    "社区修正 · 由 ${appliedFix.author.ifEmpty { "匿名" }} 提出 · " +
                        "${appliedFix.agrees}/${appliedFix.votes} 人认同"
                } else {
                    stringResource(
                        // 本机没装、说明来自总库那条时，仍然如实标「来自名单」，而不是假装是这台机器报的。
                        if ((app?.descriptionTranslated ?: fallback?.translated) == true) {
                            Res.string.description_translated_source
                        } else {
                            Res.string.description_source
                        }
                    )
                },
                style = FlText.caption2,
                color = if (appliedFix != null) scheme.primary else scheme.onSurfaceVariant,
            )
        }

        if (app != null) {
            Spacer(Modifier.height(8.dp))
            DescriptionFixEntry(
                fixCount = fixes.size,
                accepted = appliedFix != null,
                onClick = onOpenDescriptionFixes,
            )
            // 级别那一行紧跟在徽标与说明下面：读的人刚看完「这包标成推荐卸载」，下一个问题
            // 通常就是「这台机器上真的能删吗」。
            if (badge != null) {
                Spacer(Modifier.height(2.dp))
                EntriesRow(
                    label = "级别标错了？提一条修正、按档位投票",
                    accepted = false,
                    onClick = onOpenLevelFixes,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        VoteBlock(
            votes = votes,
            scope = scope,
            onScopeChange = onScopeChange,
            profile = profile,
            selectedSystem = selectedSystem,
            onSystemChange = onSystemChange,
            systemsExpanded = systemsExpanded,
            onSystemsExpandedChange = onSystemsExpandedChange,
            mine = mine,
            voting = voting,
            voteError = voteError,
            onVote = onVote,
        )
    }
}

/**
 * The two-sided vote: green "it was fine without it" against red "it broke something", as a bar,
 * the two counts, the phone the counts are for, and the two buttons that cast this install's vote.
 *
 * [scope] picks which counts are shown: 0 is this model and ROM version, 1 is every device. Both
 * are computed by the backend in one call, so switching costs nothing.
 */
@Composable
private fun VoteBlock(
    votes: PackageVotes,
    scope: Int,
    selectedSystem: String,
    onSystemChange: (String) -> Unit,
    systemsExpanded: Boolean,
    onSystemsExpandedChange: (Boolean) -> Unit,
    onScopeChange: (Int) -> Unit,
    profile: DeviceProfile?,
    mine: Boolean?,
    voting: Boolean,
    voteError: String? = null,
    onVote: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val systemVotes = votes.bySystem[selectedSystem]
    val canRemove = when (scope) {
        1 -> votes.allCanRemove
        2 -> systemVotes?.first ?: 0
        else -> votes.canRemove
    }
    val cannotRemove = when (scope) {
        1 -> votes.allCannotRemove
        2 -> systemVotes?.second ?: 0
        else -> votes.cannotRemove
    }
    val total = canRemove + cannotRemove
    val share = if (total == 0) 0f else canRemove.toFloat() / total

    Column(modifier = Modifier.fillMaxWidth()) {
        // Two pills instead of a segmented control: this sits inside the hero's own 16dp, and a
        // control with its own padding would end up too narrow to read.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScopePill(
                label = "只看本机",
                selected = scope == 0,
                onClick = { onScopeChange(0) },
            )
            Spacer(Modifier.width(8.dp))
            ScopePill(
                label = "全部机型",
                selected = scope == 1,
                onClick = { onScopeChange(1) },
            )
            Spacer(Modifier.width(8.dp))
            Box {
                ScopePill(
                    label = if (scope == 2) selectedSystem else "系统版本",
                    selected = scope == 2,
                    onClick = { onSystemsExpandedChange(true) },
                )
                DropdownMenu(
                    expanded = systemsExpanded,
                    onDismissRequest = { onSystemsExpandedChange(false) },
                ) {
                    if (votes.bySystem.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("暂无系统版本数据") },
                            onClick = { onSystemsExpandedChange(false) },
                            enabled = false,
                        )
                    }
                    votes.bySystem.keys.sorted().forEach { system ->
                        DropdownMenuItem(
                            text = { Text(system) },
                            onClick = {
                                onSystemChange(system)
                                onSystemsExpandedChange(false)
                                onScopeChange(2)
                            },
                        )
                    }
                }
            }
        }

        // The bar: one track, split by what people said. Green is left, red is right, and the
        // split is the share of "can remove".
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(VOTE_BAR)
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.surfaceContainerHighest),
        ) {
            if (total > 0) {
                if (canRemove > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(canRemove.toFloat())
                            .background(CAN_REMOVE),
                    )
                }
                if (cannotRemove > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(cannotRemove.toFloat())
                            .background(CANNOT_REMOVE),
                    )
                }
            }
        }

        // The two counts, with their share of the bar right next to them: a number without the
        // other side is not a ratio, and the bar alone cannot be read to the vote.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$canRemove 人可以删" + if (total > 0) " · ${(share * 100).toInt()}%" else "",
                style = FlText.caption1.copy(fontWeight = FontWeight.W600),
                color = if (canRemove > 0) CAN_REMOVE else scheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$cannotRemove 人不可以删" + if (total > 0) " · ${((1f - share) * 100).toInt()}%" else "",
                style = FlText.caption1.copy(fontWeight = FontWeight.W600),
                color = if (cannotRemove > 0) CANNOT_REMOVE else scheme.onSurfaceVariant,
            )
        }

        // Which phone the counts above are for, and how many votes are behind them.
        Text(
            text = buildString {
                append(
                    when (scope) {
                        1 -> "所有机型"
                        // The system list only ever holds this phone's own builds, so name the
                        // phone once here: "HyperOS 3" alone does not say whose HyperOS 3.
                        2 -> (profile?.displayModel.orEmpty().ifBlank { "本机" }) +
                            " · " + selectedSystem.ifBlank { "系统版本" }
                        else -> profile?.shortLabel() ?: "本机"
                    }
                )
                append(" · 共 ")
                append(total)
                append(" 票")
                if (total == 0) append(" · 还没有人投票，你可以是第一票")
            },
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoteButton(
                label = "可以删",
                color = CAN_REMOVE,
                selected = mine == true,
                enabled = !voting,
                onClick = { onVote(true) },
            )
            Spacer(Modifier.width(10.dp))
            VoteButton(
                label = "不可以删",
                color = CANNOT_REMOVE,
                selected = mine == false,
                enabled = !voting,
                onClick = { onVote(false) },
            )
        }
        Text(
            text = when {
                voting -> "正在投票…"
                voteError != null -> voteError
                mine != null -> "本机已投：${if (mine) "可以删" else "不可以删"}（再投一次就是改票）"
                else -> "点选你的判断"
            },
            style = FlText.caption2,
            color = if (voteError != null) scheme.error else scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Whether this app is still on the phone, as a state of its own: a coloured dot and a word on a
 * tinted capsule, green while it is there and red once it is gone. A grey "状态未知" stands in while
 * the app list has not been read yet — the page can be opened from a link before it is.
 */
@Composable
private fun UninstallStatus(app: AppInfo?) {
    val scheme = MaterialTheme.colorScheme
    val uninstalled = app?.isUninstalled == true
    val color = when {
        app == null -> scheme.onSurfaceVariant
        uninstalled -> CANNOT_REMOVE
        else -> CAN_REMOVE
    }
    val label = when {
        app == null -> "状态未知"
        uninstalled -> "已卸载"
        else -> "未卸载"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = FlText.caption2.copy(fontWeight = FlText.emphasized),
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * 这个应用右边那个按钮：还在手机上就是「卸载」，已经没了就是「恢复」。
 *
 * 形状和颜色与 [UninstallStatus] 同一套，所以一行里读起来是「状态 + 动作」，而不是两个互不
 * 相干的小玩意儿。真正发生的事不在这里：点击只是把动作交给同一个 view model，确认、规则、
 * 生物识别的提示都由列表页用的那套代码画出来。
 */
@Composable
private fun UninstallAction(
    uninstalled: Boolean,
    onUninstall: (() -> Unit)?,
    onReinstall: (() -> Unit)?,
) {
    val onClick = (if (uninstalled) onReinstall else onUninstall) ?: return
    val color = if (uninstalled) CAN_REMOVE else CANNOT_REMOVE
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.14f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (uninstalled) "恢复" else "卸载",
            style = FlText.caption1.copy(fontWeight = FlText.emphasized),
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * What can replace this app: the catalog entry's own tile, its name, and a plain "推荐替换" with the
 * arrow that says the tap goes somewhere. The catalog ships no images, so the tile is the entry's
 * mark on its accent colour — the same tile the recommendations page draws.
 */
@Composable
private fun ReplacementRow(suggestion: RecommendedApp, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = remember(suggestion.accent, suggestion.id) { recommendationAccent(suggestion) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
            .clickable(onClick = onOpen)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    Brush.linearGradient(
                        listOf(lerp(accent, Color.White, 0.18f), lerp(accent, Color.Black, 0.18f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = suggestion.tileMark,
                color = Color.White,
                fontSize = if (suggestion.tileMark.length > 1) 11.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "推荐替换",
                style = FlText.caption2.copy(fontWeight = FlText.emphasized),
                color = scheme.primary,
                maxLines = 1,
            )
            Text(
                text = suggestion.name,
                style = FlText.footnote.copy(fontWeight = FlText.emphasized),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "看看",
            style = FlText.caption2,
            color = scheme.primary,
            maxLines = 1,
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = scheme.primary,
        )
    }
}

/** The accent the catalog entry names, or the theme's own colour when it names none it can parse. */
private fun recommendationAccent(app: RecommendedApp): Color {
    val hex = app.accent.removePrefix("#").trim()
    if (hex.length == 6) {
        val value = hex.toLongOrNull(16)
        if (value != null) return Color(0xFF000000 or value)
    }
    return Color(0xFF7A6FF0)
}

/**
 * 与 [DescriptionFixEntry] 同一行的样子，但只承担一件事：把「级别有异议」那一页的入口指出来。
 *
 * 单独抽出来是因为它不带计数——级别修正的票数只在打开那一页时才问后端（应用列表页每进一次已经
 * 四个请求了，不能再多一个），所以这一行只画一句固定的话。
 */
@Composable
private fun EntriesRow(label: String, accepted: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = scheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = FlText.caption1.copy(fontWeight = FlText.emphasized),
            color = scheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (accepted) {
            Text(
                text = "已有修正生效",
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = scheme.primary,
        )
    }
}

/**
 * The way into the corrected descriptions: one row under the description itself, with the count of
 * rewrites already filed. It is always here — a description that everyone agrees with still benefits
 * from saying "you can propose a better one".
 */
@Composable
private fun DescriptionFixEntry(fixCount: Int, accepted: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = scheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (fixCount > 0) "描述有误？看 $fixCount 条修正" else "描述有误？写一条修正",
            style = FlText.caption1.copy(fontWeight = FlText.emphasized),
            color = scheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (accepted) {
            Text(
                text = "已有修正生效",
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = scheme.primary,
        )
    }
}

/** One scope choice: filled once picked, a raised page surface while not. */
@Composable
private fun ScopePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        style = FlText.caption1.copy(
            fontWeight = if (selected) FontWeight.W600 else FontWeight.Normal,
        ),
        color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) scheme.primary else FlSurfaceLevel.Page.raised.colorOf(scheme))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** One side of the vote: outlined until this install picked it, filled once it did. */
@Composable
private fun VoteButton(
    label: String,
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) color else FlSurfaceLevel.Page.raised.colorOf(scheme))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            FlGlyphIconInline(glyph = FlGlyphs.checkCircle, tint = Color.White)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = FlText.footnote.copy(fontWeight = FlText.emphasized),
            color = if (selected) Color.White else scheme.onSurface,
        )
    }
}

/** A review this install wrote, shown while the community thread has nothing to say. */
@Composable
private fun ReviewRow(review: AppReview) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.medium)
            .drawBehind { drawRect(FlSurfaceLevel.Page.raised.colorOf(scheme)) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Every review carries an avatar: the reviewer's picture when we can address it, and the
            // first character of the signature otherwise. A signature is either a chosen name or the
            // install code, and the unnamed form is `#10000` — strip the `#` and that number *is* the
            // identity the picture lives under. A chosen name cannot be turned into an address, so it
            // keeps the letter instead of asking the image bed for a page that cannot exist.
            val reviewerOwner = review.author.trim().removePrefix("#")
                .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
                .orEmpty()
            PersonAvatar(
                name = review.author,
                owner = reviewerOwner,
                size = 24.dp,
                background = scheme.secondaryContainer,
                letterColor = scheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = review.author,
                style = FlText.caption1.copy(fontWeight = FlText.emphasized),
                color = scheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            // The phone and its Android version: a review is only worth as much as the
            // device it was written on.
            val device = review.deviceLabel
            if (device.isNotEmpty()) {
                Text(
                    text = device,
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            if (review.removable) {
                Text(
                    text = "可卸",
                    style = FlText.caption2,
                    color = scheme.primary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // The same size the community comment list uses for a comment: a review and a comment are
        // the same kind of thing to read, and two sizes for them would read as two kinds.
        Text(text = review.body, style = FlText.subheadline, color = scheme.onSurface)
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = FlText.headline,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FlGlyphIconInline(
    glyph: io.github.samolego.canta.ui.fl.FlGlyph,
    tint: androidx.compose.ui.graphics.Color,
) {
    io.github.samolego.canta.ui.fl.FlGlyphIcon(glyph = glyph, size = 16.dp, tint = tint, fill = 1f)
}

/**
 * The thread narrowed to the device scope the page is showing.
 *
 * [scope] 1 is every model and keeps the whole thread; [scope] 0 keeps only what this exact phone
 * wrote; 2 keeps this phone on the picked build. A comment stored before the device was kept has no
 * model to compare with, so it stays in — unknown is not the same as another phone. An unknown
 * [model] (the profile was not read yet) also leaves the thread whole rather than emptying it.
 */
private fun scopedToDevice(
    comments: List<PlanComment>,
    scope: Int,
    system: String,
    model: String,
): List<PlanComment> = when {
    scope == 1 || model.isBlank() -> comments
    else -> comments.filter { comment ->
        val sameModel = comment.deviceModel.isBlank() ||
            comment.deviceModel.equals(model, ignoreCase = true)
        sameModel && (
            scope != 2 || system.isBlank() ||
                comment.systemName.equals(system, ignoreCase = true)
            )
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) "${(mb * 10).toInt() / 10.0} MB" else "${bytes / 1024} KB"
}

/**
 * The plans that hold this app, as a drawer over the page.
 *
 * It is a drawer and not a section of the page because the page is read top to bottom for one
 * answer — "can this phone live without it" — and the plans are a *branch* off that answer: what a
 * reader does after a green vote, when they want a whole set somebody already proved. Putting them
 * in the scroll would either bury them under the thread or push the thread down for everyone who
 * came for the votes. One tap on a row opens that plan.
 */
@Composable
private fun RelatedPlansPanel(
    plans: List<CommunityPlan>,
    widerPlans: List<CommunityPlan>,
    loading: Boolean,
    widerLoading: Boolean,
    onOpenPlan: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val total = plans.size + widerPlans.size
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.86f)
            .widthIn(max = 380.dp),
        color = scheme.surfaceContainerHigh,
        // Rounded on the side it slides in from: the edge it came out of, not the one it is
        // attached to.
        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The panel reaches the top of the screen on purpose; the status bar's own height
                // is what keeps the title clear of the clock.
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "包含这个应用的方案",
                        style = FlText.subheadline,
                        color = scheme.onSurface,
                    )
                    Text(
                        // 两层的数都说出来：本机型 0、全社区 37 的时候，读者该看到的正是这一句，
                        // 而不是「本机型还没有这样的方案」那一句像结论的话。
                        text = when {
                            loading && widerLoading -> "正在读…"
                            total == 0 && !loading && !widerLoading -> "还没有人把它写进方案"
                            else -> "本机型 ${plans.size} 个 · 全社区 ${widerPlans.size} 个"
                        },
                        style = FlText.caption2,
                        color = scheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(16.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item("mine-header") {
                    SectionLabel(
                        title = "本机型",
                        hint = if (plans.isEmpty()) {
                            if (loading) "正在读这一机型的方案…" else "这台手机上还没有人写过"
                        } else {
                            "同一台手机、同一个版本"
                        },
                    )
                }
                items(plans, key = { "mine-${it.id}" }) { plan ->
                    RelatedPlanRow(plan = plan, onOpen = { onOpenPlan(plan.id) })
                }
                item("wider-header") {
                    SectionLabel(
                        title = "全社区",
                        hint = if (widerPlans.isEmpty()) {
                            if (widerLoading) "正在读全社区的方案…" else "别的机型也没有写过"
                        } else {
                            "别的机型写的，包名和顺序照样能抄"
                        },
                    )
                }
                items(widerPlans, key = { "wider-${it.id}" }) { plan ->
                    RelatedPlanRow(plan = plan, showDevice = true, onOpen = { onOpenPlan(plan.id) })
                }
            }
        }
    }
}

/** 抽屉里两层之间的分隔标题：说清这一批是按什么筛出来的。 */
@Composable
private fun SectionLabel(title: String, hint: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(
            text = title,
            style = FlText.caption1.copy(fontWeight = FontWeight.SemiBold),
            color = scheme.onSurface,
        )
        Text(text = hint, style = FlText.caption2, color = scheme.outline)
    }
}

/** One plan in the drawer: its name, and the numbers that say whether it is worth opening. */
@Composable
private fun RelatedPlanRow(plan: CommunityPlan, showDevice: Boolean = false, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = plan.title,
                style = FlText.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = scheme.outline,
            )
        }
        Text(
            // The package count leads: a plan that holds forty packages is a different thing from
            // one that holds five, and that is the number the drawer is compared on.
            text = buildString {
                append("${plan.packages.size} 包 · 点赞 ${plan.votes} · ${plan.comments} 评论")
                val author = plan.authorName
                if (author.isNotEmpty()) append(" · @$author")
                // 全社区那一批要写出是哪台手机：同一份包在一台老机型上验证过，拿到本机型上是参考，
                // 不是承诺，读者得能看见这个差别。
                if (showDevice) {
                    val device = plan.deviceLabel
                    if (device.isNotBlank()) append(" · $device")
                }
            },
            style = FlText.caption2,
            color = scheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
