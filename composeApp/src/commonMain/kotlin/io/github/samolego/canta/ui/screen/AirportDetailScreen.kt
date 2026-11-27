package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.community.CommentOrder
import io.github.samolego.canta.data.community.FIRST_REPLY_BATCH
import io.github.samolego.canta.data.community.NEXT_REPLY_BATCH
import io.github.samolego.canta.data.community.PlanComment
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.inOrder
import io.github.samolego.canta.data.community.threads
import io.github.samolego.canta.data.community.updatedComment
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.airport_comment_empty
import io.github.samolego.canta.generated.resources.airport_detail_intro
import io.github.samolego.canta.generated.resources.airport_detail_loading
import io.github.samolego.canta.generated.resources.airport_detail_missing
import io.github.samolego.canta.generated.resources.airport_detail_open_site
import io.github.samolego.canta.generated.resources.airport_detail_shared_by
import io.github.samolego.canta.generated.resources.airport_detail_subscribe_copy
import io.github.samolego.canta.generated.resources.airport_detail_subscribe_get
import io.github.samolego.canta.generated.resources.airport_detail_subscribe_note
import io.github.samolego.canta.generated.resources.airport_detail_title
import io.github.samolego.canta.generated.resources.airport_detail_worth
import io.github.samolego.canta.generated.resources.airport_field_devices
import io.github.samolego.canta.generated.resources.airport_field_price
import io.github.samolego.canta.generated.resources.airport_field_protocols
import io.github.samolego.canta.generated.resources.airport_field_site
import io.github.samolego.canta.generated.resources.airport_field_speed
import io.github.samolego.canta.generated.resources.airport_field_traffic
import io.github.samolego.canta.generated.resources.comment_section_title
import io.github.samolego.canta.generated.resources.plan_comment_send
import io.github.samolego.canta.generated.resources.recommend_close
import io.github.samolego.canta.generated.resources.recommend_detail_back
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.CommentComposer
import io.github.samolego.canta.ui.component.CommentOrderRow
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.commentThreads
import io.github.samolego.canta.ui.component.dismissInputOnTap
import io.github.samolego.canta.ui.component.threadKey
import io.github.samolego.canta.ui.component.text.UrlText
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.IdentitySetup
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** 详情格里左边那一列标签的宽度：六格里最长的是「设备数」，固定下来才对得齐。 */
private val FieldLabelWidth = 56.dp

/**
 * 一条机场的整页：名字、类型、票数、五星，六格参数，一段介绍，一个去官网的入口，以及下面的评论区。
 *
 * 为什么从弹窗改成整页（用户的原话是「我不是让你改成单独页面吗，就跟那个应用详情和方案详情的
 * 那种页面一样，可以交流的」）：评论要读、要回、要滚，弹窗高度有限、外面还压着一层遮罩，
 * 回一句要跟滚动条抢；应用详情页和方案详情页怎么画评论，这里就怎么画。
 *
 * 数据来源是 [PlanRepository.airports] 的列表，按 [airportId] 找出这一条——和方案详情页
 * 从列表里按 id 捞是同一条路子，代价是每次进来要拉一趟列表；换来的是"后端没有单条查询"
 * 这件事不必假装存在。列表没拉到、或里面没有这一条，页面说一句、给一个返回，不会崩。
 *
 * [state] 是列表那一页手里的同一份机场快照：进来先用它把这一条摆上（不闪"没找到"），投票 /
 * 打分 / 取到订阅之后写回它——返回列表时票数、订阅都是新的，不必等它重拉一遍。
 *
 * 评论走机场专用的那一对函数（[PlanRepository.airportComments] / [PlanRepository.commentOnAirport]），
 * 不是通用 `comment`：机场的评论在后端有自己的表与迁移，换一条路等于把已有的那些评论藏起来。
 *
 * @param repository 社区后端；null 时页面只说明"没连上"。
 * @param state 列表那一页的跨格状态（见 [AirportTabState]）：进页先读、改完写回。
 * @param author 署名；[authorId] 为空时也当身份用。
 * @param authorId 身份号：投票、打分、评论都挂在它上面。
 * @param token 证明这个身份号的秘密；删除自己写的评论要它。
 * @param profile 本机机型与系统。发评论时一起交给后端，别人看到的评论行才有「Redmi K70 · HyperOS 2」
 *   那一列——取法与 [AppDetailScreen]、方案详情页相同（都是 `CantaHandler.deviceProfile()`）；拿不到时
 *   是 null，那时评论就不带这一列，而不是写一个假机型。
 * @param onOpenUploader 点「由 X 分享」时去哪：分享者的主页（`Screen.Uploader`）。null 或那条没有身份号
 *   时这一行只是文字，"该不该信他分享的其他几条"就走不过去。
 */
@Composable
fun AirportDetailScreen(
    airportId: Long,
    repository: PlanRepository?,
    platform: CantaPlatform,
    state: AirportTabState,
    author: String = "",
    authorId: String = "",
    token: String = "",
    /** 本机机型与系统：发评论时带上，评论行才有那一列机型 / 系统（见上面的 `@param`）。 */
    profile: DeviceProfile? = null,
    /** 点分享者那一行去哪：他的主页（见上面的 `@param`）。 */
    onOpenUploader: ((String) -> Unit)? = null,
    onNavigateBack: () -> Unit = {},
    /** 名字与头像设过了没有：没设过这条线程收不了评论（见 [IdentitySetup]）。 */
    setupGate: IdentitySetup = IdentitySetup.UNSET,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val who = remember(authorId, author) { authorId.ifEmpty { author } }
    val authorLabel = author.ifEmpty { "u-anon" }

    // 列表刚刚画过这一条：先摆上它，页面第一帧就有内容，也不会闪一句"没找到"。
    var entry by remember(airportId) {
        mutableStateOf(state.airports.firstOrNull { it.id == airportId })
    }
    var loading by remember(airportId) { mutableStateOf(entry == null) }
    /** 这一趟没拿到（网络 / 后端）：和"列表里没有这一条"是两件事。 */
    var loadError by remember(airportId) { mutableStateOf<String?>(null) }
    /** 正在进行的那一件事；同一时刻只发一趟。 */
    var busy by remember(airportId) { mutableStateOf(false) }
    /** 已经取到的订阅链接；再点一次就是复制它，不再跑网络。 */
    var subscribeUrl by remember(airportId) {
        mutableStateOf(state.subscriptions[airportId].orEmpty())
    }

    // 评论：与推荐条目、方案详情页同一套。
    var comments by remember(airportId) { mutableStateOf<List<PlanComment>>(emptyList()) }
    var commentsLoading by remember(airportId) { mutableStateOf(true) }
    var commentsError by remember(airportId) { mutableStateOf<String?>(null) }
    var reloadKey by remember(airportId) { mutableIntStateOf(0) }
    var order by remember(airportId) { mutableStateOf(CommentOrder.NEWEST) }
    var expandedThreads by remember(airportId) { mutableStateOf(setOf<Long>()) }
    var shownReplies by remember(airportId) { mutableStateOf(mapOf<Long, Int>()) }
    var replyTo by remember(airportId) { mutableStateOf<PlanComment?>(null) }
    var pendingDelete by remember(airportId) { mutableStateOf<PlanComment?>(null) }
    var draft by remember(airportId) { mutableStateOf("") }
    var sending by remember(airportId) { mutableStateOf(false) }
    var submitFailed by remember(airportId) { mutableStateOf(false) }
    var sendError by remember(airportId) { mutableStateOf<String?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(airportId, who) {
        // 列表已经画过这一条就不挂加载态：那一屏刚才还在，再转一圈只会让人以为页面没进来。
        if (entry == null) loading = true
        loadError = null
        val repo = repository
        if (repo == null) {
            loadError = "社区后端没连上，这条机场现在读不到。"
        } else {
            val fresh = repo.airports(who)
            if (fresh == null) {
                loadError = repo.lastError ?: "机场列表没拉到，检查一下网络或代理。"
            } else {
                entry = fresh.firstOrNull { it.id == airportId }
            }
        }
        loading = false
    }

    LaunchedEffect(airportId, who, reloadKey) {
        val repo = repository
        if (repo == null) {
            commentsLoading = false
            commentsError = "社区后端没连上，评论拉不到。"
            return@LaunchedEffect
        }
        commentsLoading = true
        val fresh = repo.airportComments(airportId, who)
        // `airportComments` 失败时给的是空表：要读 lastError 才分得开"没人评论"和"没拉到"。
        if (repo.lastError != null || repo.lastFailureWasNetwork) {
            commentsError = repo.lastError ?: "评论没拉到，检查一下网络或代理。"
        } else {
            comments = fresh
            commentsError = null
        }
        commentsLoading = false
    }

    val vote: (Boolean) -> Unit = { agree ->
        val repo = repository
        val current = entry
        when {
            repo == null -> platform.showMessage("社区后端没连上，投票没发出去。")
            current == null || busy -> Unit
            else -> {
                // busy 在协程外面置上：同一帧里点两下都会走到这里，第二下会把第一下的标记覆盖掉。
                busy = true
                scope.launch {
                    val fresh = repo.voteAirport(current.id, who, agree)
                    busy = false
                    if (fresh == null) {
                        platform.showMessage("没投上：${repo.lastError ?: "检查一下网络再试"}")
                    } else {
                        val updated = current.copy(
                            agrees = fresh.agrees,
                            disagrees = fresh.disagrees,
                            mine = fresh.mine,
                        )
                        entry = updated
                        // 写回列表那一份并让它下一趟重拉：否则返回列表还是旧票数（键见 AirportTabState）。
                        state.publish(updated)
                    }
                }
            }
        }
    }

    val rate: (Int) -> Unit = { stars ->
        val repo = repository
        val current = entry
        when {
            repo == null -> platform.showMessage("社区后端没连上，评分没发出去。")
            current == null || busy -> Unit
            else -> {
                busy = true
                scope.launch {
                    val fresh = repo.rateAirport(current.id, who, stars)
                    busy = false
                    if (fresh == null) {
                        platform.showMessage("没评上：${repo.lastError ?: "检查一下网络再试"}")
                    } else {
                        val updated = current.copy(
                            stars = fresh.stars,
                            ratingCount = fresh.ratingCount,
                            myStars = fresh.myStars,
                        )
                        entry = updated
                        state.publish(updated)
                    }
                }
            }
        }
    }

    val subscribe: () -> Unit = subscribe@{
        val current = entry ?: return@subscribe
        if (subscribeUrl.isNotEmpty()) {
            platform.copyToClipboard(subscribeUrl)
            platform.showMessage("订阅链接已复制")
            return@subscribe
        }
        // 不解锁也照样问一次后端：`unlocked` 只是上一次列表的快照，拿旧快照挡人会把刚投过票、
        // 但列表还没刷新的人挡在门外。所以按钮不看它，答案由这一趟的 `unlocked` 决定。
        val repo = repository ?: return@subscribe
        if (busy) return@subscribe
        busy = true
        scope.launch {
            val fresh = repo.airportSubscription(current.id, who)
            busy = false
            when {
                fresh == null -> platform.showMessage("订阅没取到：${repo.lastError ?: "检查一下网络"}")
                fresh.unlocked && fresh.subscribeUrl.isNotBlank() -> {
                    subscribeUrl = fresh.subscribeUrl
                    // 记进列表那一份：返回列表时卡片直接写「复制订阅」，不用再取一趟。
                    state.rememberSubscription(current.id, fresh.subscribeUrl)
                    platform.copyToClipboard(fresh.subscribeUrl)
                    platform.showMessage("订阅链接已复制")
                }
                else -> platform.showMessage("先投一票或打个星，才能取订阅")
            }
        }
    }

    val send: () -> Unit = send@{
        val body = draft.trim()
        // 名字/头像没设完：发送键已经是灰的，这里再挡一次（键盘回车也会走到这儿）。
        if (!setupGate.ready) {
            sendError = setupGate.refusal
            submitFailed = true
            return@send
        }
        if (body.isEmpty() || sending) return@send
        val repo = repository
        if (repo == null) {
            sendError = "社区后端没连上，评论没发出去。"
            submitFailed = true
            return@send
        }
        scope.launch {
            sending = true
            val answered = replyTo
            val id = repo.commentOnAirport(
                airportId = airportId,
                body = body,
                author = authorLabel,
                authorId = who,
                replyTo = answered?.id ?: 0L,
                // 机型与系统：评论行前面那一小列。原来在弹窗里写评论时没带上，于是这一格的评论
                // 全都只有名字和时间，说「这条线路在某某机器上掉线」的时候看不出是哪台机器。
                deviceModel = profile?.displayModel.orEmpty(),
                systemName = profile?.systemLabel().orEmpty(),
            )
            sending = false
            submitFailed = id == null
            sendError = if (id == null) {
                repo.lastError?.let { "发送失败：$it" } ?: "发送失败，检查一下网络再试"
            } else {
                null
            }
            if (id != null) {
                draft = ""
                replyTo = null
                // 回复挂在起始评论下面：把那一条拉开，让人立刻看见自己刚发的那句。
                // 键是 `threadKey(comment, index)`，回复的 `replyTo` 就是起始评论的 id。
                answered?.let { reply ->
                    val starter = comments.firstOrNull { it.id == reply.replyTo } ?: reply
                    expandedThreads = expandedThreads + threadKey(starter, 0)
                }
                reloadKey++
            }
        }
    }

    val like: (PlanComment) -> Unit = { comment ->
        if (comment.id > 0L) {
            val wanted = !comment.liked
            comments = comments.updatedComment(comment.id) {
                it.copy(liked = wanted, likes = (it.likes + if (wanted) 1 else -1).coerceAtLeast(0))
            }
            scope.launch {
                val result = repository?.likeComment(comment.id, who, wanted)
                if (result != null) {
                    comments = comments.updatedComment(comment.id) {
                        it.copy(liked = result.liked, likes = result.likes)
                    }
                }
            }
        }
    }

    pendingDelete?.let { comment ->
        CantaDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除评论",
            buttons = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(Res.string.recommend_close)) }
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        repository?.deleteComment(comment.id, who, token)
                        reloadKey++
                    }
                }) {
                    Text("删除", color = scheme.error)
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
        topBar = {
            ScreenTopBar(
                title = entry?.name.orEmpty().ifBlank { stringResource(Res.string.airport_detail_title) },
                onNavigateBack = onNavigateBack,
            )
        },
        bottomBar = {
            if (entry != null) {
                CommentComposer(
                    draft = draft,
                    onDraftChange = { changed ->
                        draft = changed
                        if (submitFailed) submitFailed = false
                    },
                    onSend = send,
                    replyToName = replyTo?.author.orEmpty(),
                    onCancelReply = { replyTo = null },
                    sending = sending,
                    failed = submitFailed,
                    sendLabel = stringResource(Res.string.plan_comment_send),
                    failLabel = sendError ?: "发送失败，检查一下网络再试",
                    pickerOpen = pickerOpen,
                    onPickerOpenChange = { pickerOpen = it },
                    gateHint = setupGate.prompt,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissInputOnTap { pickerOpen = false },
        ) {
            val loaded = entry
            when {
                loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.airport_detail_loading),
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }

                loaded == null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 列表没拉到、或里面没有这一条：两句话不一样，人才能决定是刷新还是算了。
                    Text(
                        text = loadError ?: stringResource(Res.string.airport_detail_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onNavigateBack) {
                        Text(stringResource(Res.string.recommend_detail_back))
                    }
                }

                else -> {
                    val canAct = who.isNotEmpty()
                    val threads = remember(comments, order) { comments.threads().inOrder(order) }
                    // 价格到协议：一行一条，缺哪条少哪条，不写"未填"。在组合里拼好再交给懒列表：
                    // 列表的内容 lambda 不是 @Composable，`stringResource` 只能在 item 里面或这里读。
                    val rows = listOf(
                        stringResource(Res.string.airport_field_price) to loaded.detail.price,
                        stringResource(Res.string.airport_field_traffic) to loaded.detail.traffic,
                        stringResource(Res.string.airport_field_speed) to loaded.detail.speed,
                        stringResource(Res.string.airport_field_devices) to loaded.detail.devices,
                        stringResource(Res.string.airport_field_protocols) to loaded.detail.protocols,
                        stringResource(Res.string.airport_field_site) to loaded.url,
                    ).filter { it.second.isNotBlank() }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "hero") {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(AirportTileSize)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(scheme.primary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = loaded.tileMark,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.primary,
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = loaded.metaLine.ifEmpty { "还没人评" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                    if (loaded.author.isNotBlank()) {
                                        // 分享者那一行：有身份号、调用方也接了「去主页」，它就是一个入口——
                                        // 「他还分享过哪几条、信不信得过」是看完一条之后的下一个问题，
                                        // 与方案详情页点上传者是同一个动作。缺一样就只是文字。
                                        val toProfile = onOpenUploader
                                        val sharerId = loaded.authorId
                                        val openable = toProfile != null && sharerId.isNotEmpty()
                                        Text(
                                            text = stringResource(
                                                Res.string.airport_detail_shared_by,
                                                loaded.author,
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (openable) scheme.primary else scheme.onSurfaceVariant,
                                            modifier = if (openable) {
                                                Modifier
                                                    .clip(RoundedCornerShape(percent = 50))
                                                    // toProfile 是可空的函数值：这里用 ?.invoke，
                                                    // 而不是指望上面的 openable 替它做智能转换。
                                                    .clickable(role = Role.Button) { toProfile?.invoke(sharerId) }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            } else {
                                                Modifier
                                            },
                                        )
                                    }
                                }
                                Text(
                                    text = loaded.kindLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSecondaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(scheme.secondaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        // 价格到协议：一行一条，缺哪条少哪条，不写"未填"。
                        if (rows.isNotEmpty()) {
                            item(key = "detail-rows") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                                        .padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    rows.forEach { (label, value) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = scheme.onSurfaceVariant,
                                                modifier = Modifier.width(FieldLabelWidth),
                                            )
                                            Text(
                                                text = value,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = scheme.onSurface,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (loaded.summary.isNotBlank() || loaded.detail.note.isNotBlank()) {
                            item(key = "intro") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = stringResource(Res.string.airport_detail_intro),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.primary,
                                    )
                                    if (loaded.summary.isNotBlank()) {
                                        // 介绍里常贴着官网、邀请页、汇率说明那种带地址的原文：与应用详情页
                                        // 的描述同一套排版（[UrlText]），地址可以点，别的字照常换行。
                                        UrlText(
                                            text = loaded.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (loaded.detail.note.isNotBlank()) {
                                        // 整页自己会滚，所以这里不再做"展开全文"：那一段本来就该读完。
                                        UrlText(
                                            text = loaded.detail.note,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "actions") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(Res.string.airport_detail_worth),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.primary,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 票数够、认同率也够：这一轮已经结束，两个按钮换成「投票结束」，
                                    // 与榜单那一行同一个判断（见 CommunityAirport.ballotSettled）。
                                    if (loaded.ballotSettled) {
                                        SettledVote()
                                    } else {
                                        AirportVotePill(
                                            label = "认同",
                                            count = loaded.agrees,
                                            selected = loaded.mine == true,
                                            color = AirportAgree,
                                            enabled = canAct && !busy,
                                            onClick = { vote(true) },
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        AirportVotePill(
                                            label = "不认同",
                                            count = loaded.disagrees,
                                            selected = loaded.mine == false,
                                            color = AirportDisagree,
                                            enabled = canAct && !busy,
                                            onClick = { vote(false) },
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (!canAct) {
                                        Text(
                                            text = "认不出身份，只能看",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    text = airportVoteStateOf(loaded),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (loaded.ballotSettled) scheme.primary else scheme.onSurfaceVariant,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StarRow(
                                        myStars = loaded.myStars,
                                        enabled = canAct && !busy,
                                        onRate = rate,
                                    )
                                    if (loaded.ratingCount > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${loaded.starsText} · ${loaded.ratingCount} 人评分",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (loaded.hasSubscription) {
                                    if (subscribeUrl.isNotEmpty()) {
                                        Text(
                                            text = subscribeUrl,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = scheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                        )
                                    }
                                    // 能不能取到由后端按身份现算（`AirportSubscription.unlocked`），
                                    // 页面不拿列表里那份旧快照挡人：有身份、又不在忙就能点，
                                    // 没解锁的那次点击会回一句"先投一票或打个星"。
                                    val canFetch = canAct && !busy
                                    Text(
                                        text = stringResource(
                                            if (subscribeUrl.isNotEmpty()) {
                                                Res.string.airport_detail_subscribe_copy
                                            } else {
                                                Res.string.airport_detail_subscribe_get
                                            },
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (canFetch) scheme.primary else scheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(
                                                if (canFetch) {
                                                    scheme.primary.copy(alpha = 0.10f)
                                                } else {
                                                    FlSurfaceLevel.Page.raised.colorOf(scheme)
                                                },
                                            )
                                            .clickable(
                                                enabled = canFetch,
                                                role = Role.Button,
                                                onClick = subscribe,
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                // 这句话以前只在"这有条订阅链接"时出现，于是"这家没贴订阅"看起来像
                                // 页面忘了画按钮。它回答的是"为什么这里没有取订阅的入口"，所以对
                                // 每一条都说一遍。
                                Text(
                                    text = stringResource(Res.string.airport_detail_subscribe_note),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                )
                                if (loaded.url.isNotBlank()) {
                                    Text(
                                        text = stringResource(Res.string.airport_detail_open_site),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                                            .clickable(role = Role.Button) { platform.openUrl(loaded.url) }
                                            .padding(horizontal = 9.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }

                        // 评论头：条数与排序开关同一行。
                        item(key = "comment-head") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(Res.string.comment_section_title),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.primary,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = comments.size.toString(),
                                    fontSize = 11.sp,
                                    color = scheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (commentsLoading) {
                                    Text(
                                        text = "正在读…",
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceVariant,
                                    )
                                } else if (comments.isNotEmpty()) {
                                    CommentOrderRow(
                                        order = order,
                                        onOrderChange = { order = it },
                                        inline = true,
                                    )
                                }
                            }
                        }

                        if (!commentsLoading && comments.isEmpty()) {
                            item(key = "comment-empty") {
                                Column {
                                    Text(
                                        text = stringResource(Res.string.airport_comment_empty),
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceVariant,
                                    )
                                    commentsError?.let { reason ->
                                        Text(
                                            text = "后端没有返回评论：$reason",
                                            fontSize = 10.sp,
                                            color = scheme.error,
                                            modifier = Modifier.padding(top = 3.dp),
                                        )
                                    }
                                }
                            }
                        }

                        commentThreads(
                            threads = threads,
                            me = who,
                            // 分享者自己的那几条回复带红色的「作者」徽章：与方案详情页同一套判据
                            // （先认身份号，老评论没有号时退回名字）。
                            authorId = loaded.authorId,
                            authorName = loaded.author,
                            onReply = { comment ->
                                replyTo = comment
                                submitFailed = false
                            },
                            onDelete = { comment -> pendingDelete = comment },
                            expanded = expandedThreads,
                            shownReplies = shownReplies,
                            onShowMoreReplies = { threadId ->
                                shownReplies = shownReplies + (
                                    threadId to ((shownReplies[threadId] ?: FIRST_REPLY_BATCH) + NEXT_REPLY_BATCH)
                                    )
                            },
                            onToggleThread = { threadId ->
                                if (threadId in expandedThreads) shownReplies = shownReplies - threadId
                                expandedThreads = if (threadId in expandedThreads) {
                                    expandedThreads - threadId
                                } else {
                                    expandedThreads + threadId
                                }
                            },
                            onLike = like,
                            // 这一格按机场 id 分格：首帧网络还没到，先垫上一次那批。
                            cacheTarget = airportId.toString(),
                            loading = commentsLoading,
                        )
                    }
                }
            }
        }
    }
}
