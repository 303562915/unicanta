package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.community.COMMENT_KIND_RECOMMEND
import io.github.samolego.canta.data.community.CommentOrder
import io.github.samolego.canta.data.community.FIRST_REPLY_BATCH
import io.github.samolego.canta.data.community.NEXT_REPLY_BATCH
import io.github.samolego.canta.data.community.PlanComment
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.inOrder
import io.github.samolego.canta.data.community.recommendCommentTarget
import io.github.samolego.canta.data.community.threads
import io.github.samolego.canta.data.community.updatedComment
import io.github.samolego.canta.data.recommend.RecommendedApp
import io.github.samolego.canta.data.recommend.Recommendations
import io.github.samolego.canta.data.recommend.SuggestedApp
import io.github.samolego.canta.data.recommend.SuggestedAppVote
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.comment_section_title
import io.github.samolego.canta.generated.resources.plan_comment_send
import io.github.samolego.canta.generated.resources.recommend_by
import io.github.samolego.canta.generated.resources.recommend_close
import io.github.samolego.canta.generated.resources.recommend_comment_empty
import io.github.samolego.canta.generated.resources.recommend_detail_back
import io.github.samolego.canta.generated.resources.recommend_detail_hint
import io.github.samolego.canta.generated.resources.recommend_detail_loading
import io.github.samolego.canta.generated.resources.recommend_detail_missing
import io.github.samolego.canta.generated.resources.recommend_detail_title
import io.github.samolego.canta.generated.resources.recommend_open_browser
import io.github.samolego.canta.generated.resources.recommend_uninstall_hint
import io.github.samolego.canta.generated.resources.recommend_uninstall_short
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.CommentComposer
import io.github.samolego.canta.ui.component.CommentOrderRow
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.commentThreads
import io.github.samolego.canta.ui.component.dismissInputOnTap
import io.github.samolego.canta.ui.component.threadKey
import io.github.samolego.canta.util.IdentitySetup
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** 来源之一：内置目录里的条目，`key` 是它的字符串 id（`cyime` 这种）。 */
internal const val RECOMMEND_SOURCE_APP: String = "app"

/** 来源之二：社区提交的条目，`key` 是它后端的数字 id；认不出来就当"没了"。 */
internal const val RECOMMEND_SOURCE_SUGGESTION: String = "suggestion"

/** 详情页头像方块的边长：比列表里的卡片大一档，因为它现在是整页的主视觉。 */
private val HeroTileSize = 58.dp

/**
 * 一条推荐条目在详情页上要的几样东西。
 *
 * 目录条目（[RecommendedApp]）和社区条目（[SuggestedApp]）是两个数据类，能画的东西不一样：社区那条
 * 没有"能替换哪些包"，目录那条没有票数。页面只认这一份小的，两个来源各写一个转换，
 * 免得详情页里到处 `when (source)`。
 */
private data class DetailEntry(
    val name: String,
    val author: String,
    val category: String,
    val url: String,
    val summary: String,
    val license: String,
    val tileMark: String,
    /** 目录条目自带的图标底色；社区条目没有这一项，空串时按 [key] 现挑一个（见 [accentOf]）。 */
    val accent: String,
    /** 这条替换掉的系统包；社区条目没有这一栏，就是空表。 */
    val replaces: List<String>,
    /**
     * 社区条目本身，含票数、我投过什么、够不够票上榜；目录条目是 null。
     *
     * 留住整条而不是只留几个数：投票之后要按 [voteStateOf] 重算"还差几票"，而那句话是从这条
     * 数据算出来的。目录条目不投票，所以这里没有第二套字段。
     */
    val suggestion: SuggestedApp? = null,
)

/** 目录条目 → 详情页那一份。 */
private fun detailOf(app: RecommendedApp): DetailEntry = DetailEntry(
    name = app.name,
    author = app.author,
    category = app.category,
    url = app.url,
    summary = app.summary,
    license = app.license,
    tileMark = app.tileMark,
    accent = app.accent,
    replaces = app.replacesPackages,
)

/** 社区条目 → 详情页那一份。 */
private fun detailOf(app: SuggestedApp): DetailEntry = DetailEntry(
    name = app.name,
    author = app.author,
    category = app.category,
    url = app.url,
    summary = app.summary,
    license = app.license,
    tileMark = app.tileMark,
    accent = "",
    replaces = emptyList(),
    suggestion = app,
)

/**
 * 一个推荐条目的整页：图标、名字、作者、许可证、分类、介绍，这台手机还装着的那些被替换的包，
 * 一个「在浏览器打开」，以及下面那条能回话的评论线程。
 *
 * 为什么从弹窗改成整页（用户的原话是「我不是让你改成单独页面吗，就跟那个应用详情和方案详情的
 * 那种页面一样，可以交流的」）：评论是要读、要回、要滚的东西，弹窗高度有限、外面还压着一层遮罩，
 * 一屏只放得下三条，回一句要跟滚动条抢；应用详情页和方案详情页怎么画评论，这里就怎么画。
 *
 * 条目本身按 [source] + [key] 重新取一次，而不是从上一个页面把对象塞进参数：页面被写进返回栈
 * 之后参数就定死了，进程被回收再回来也只能靠路由里的那两个字段重新找一遍——和方案详情页
 * 按 id 去列表里捞是同一条路子。
 *
 * 评论的身份是「项目地址」（[recommendCommentTarget]），不是列表下标：地址跨设备、跨版本对得齐
 * 同一件事，榜单重排也不会把评论挂到别人名下。
 *
 * @param source [RECOMMEND_SOURCE_APP] 或 [RECOMMEND_SOURCE_SUGGESTION]。
 * @param key 目录条目的字符串 id，或社区条目的数字 id（路由里是字符串）。
 * @param installedPackages 这台手机装着的包，用来挑出「能替换哪些」里还剩下的那几个。
 * @param author 署名；[authorId] 为空时也当身份用。
 * @param authorId 身份号：评论挂在这上面，删除和点赞也按它算。
 * @param token 证明这个身份号的秘密；删除自己写的评论要它。
 * @param onVoteSuggestion 给社区条目投认同 / 不认同，返回投完之后的三个数；null 表示没投上。
 *   由调用方接（`CantaApp` 里就是 `communityRepository.voteSuggestedApp(...)`）：这一页是独立路由，
 *   拿不到推荐页里那份"正在投哪一条"的状态，所以投票这一件事走回调，两个页面用同一个后端调用。
 *   null 表示没接线——那时按钮不画成可点，而不是点了报"没投上"。
 */
@Composable
fun RecommendDetailScreen(
    source: String,
    key: String,
    platform: CantaPlatform,
    repository: PlanRepository? = null,
    installedPackages: Set<String> = emptySet(),
    author: String = "",
    authorId: String = "",
    token: String = "",
    onNavigateBack: () -> Unit = {},
    /** 打开一个包的详情页：那些「还能替换掉」的包从这里去卸载。 */
    onOpenApp: (String) -> Unit = {},
    /** 社区条目的认同 / 不认同；见上面的 `@param`。 */
    onVoteSuggestion: (suspend (id: Long, agree: Boolean) -> SuggestedAppVote?)? = null,
    /** 名字与头像设过了没有：没设过这条线程收不了评论（见 [IdentitySetup]）。 */
    setupGate: IdentitySetup = IdentitySetup.UNSET,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // 评论署名：目录条目和社区条目共用同一套身份，与推荐页那些投稿是同一份。
    val me = authorId.ifEmpty { author }
    val authorLabel = author.ifEmpty { "u-anon" }

    var entry by remember(source, key) { mutableStateOf<DetailEntry?>(null) }
    var loading by remember(source, key) { mutableStateOf(true) }
    /** 这一趟没拿到（网络 / 后端），和"回答里没有这一条"是两件事，说法也不同。 */
    var loadError by remember(source, key) { mutableStateOf<String?>(null) }
    /** 正在投的那一票：同一时刻只发一趟。 */
    var voting by remember(source, key) { mutableStateOf(false) }

    // 评论：与方案详情页同一套——线程、排序、展开、回复、删除、点赞、底部发表框。
    var comments by remember(source, key) { mutableStateOf<List<PlanComment>>(emptyList()) }
    var commentsLoading by remember(source, key) { mutableStateOf(true) }
    var commentsError by remember(source, key) { mutableStateOf<String?>(null) }
    var reloadKey by remember(source, key) { mutableIntStateOf(0) }
    var order by remember(source, key) { mutableStateOf(CommentOrder.NEWEST) }
    var expandedThreads by remember(source, key) { mutableStateOf(setOf<Long>()) }
    var shownReplies by remember(source, key) { mutableStateOf(mapOf<Long, Int>()) }
    var replyTo by remember(source, key) { mutableStateOf<PlanComment?>(null) }
    var pendingDelete by remember(source, key) { mutableStateOf<PlanComment?>(null) }
    var draft by remember(source, key) { mutableStateOf("") }
    var sending by remember(source, key) { mutableStateOf(false) }
    var submitFailed by remember(source, key) { mutableStateOf(false) }
    var sendError by remember(source, key) { mutableStateOf<String?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(source, key, me) {
        loading = true
        loadError = null
        val found = when (source) {
            RECOMMEND_SOURCE_APP -> Recommendations.bundled().apps
                .firstOrNull { it.id == key }
                ?.let(::detailOf)

            else -> {
                val repo = repository
                val id = key.toLongOrNull() ?: 0L
                if (repo == null) {
                    loadError = "社区后端没连上，这条推荐现在读不到。"
                    null
                } else {
                    val fresh = repo.suggestedApps(me)
                    if (fresh == null) {
                        loadError = repo.lastError ?: "社区推荐拉不到，检查一下网络或代理再刷新。"
                        null
                    } else {
                        val hit = fresh.firstOrNull { it.id == id }?.let(::detailOf)
                        // `suggestedApps` 有离线快照兜底：网络没通时它照样给一份列表。所以"列表里
                        // 没有这一条"并不等于"这一条被下掉了"——先看那一趟是不是失败过，是就说网络/
                        // 代理，别让人以为条目被删了，更别让人以为是自己看错了。
                        if (hit == null && (repo.lastFailureWasNetwork || repo.lastError != null)) {
                            loadError = repo.lastError ?: "社区推荐拉不到，检查一下网络或代理再刷新。"
                        }
                        hit
                    }
                }
            }
        }
        // 请求成功、回答里却没有这一条：`entry` 留空，下面那句"这条推荐没找到"就是它。
        // 失败时上面已经写好了原因，两者不会同时出现。
        entry = found
        loading = false
    }

    val target = remember(entry) { entry?.let { recommendCommentTarget(it.url) }.orEmpty() }

    LaunchedEffect(target, me, reloadKey) {
        if (target.isEmpty()) {
            commentsLoading = false
            return@LaunchedEffect
        }
        val repo = repository
        if (repo == null) {
            commentsLoading = false
            commentsError = "社区后端没连上，评论拉不到。"
            return@LaunchedEffect
        }
        commentsLoading = true
        val fresh = repo.commentsOf(COMMENT_KIND_RECOMMEND, target, me)
        // `commentsOf` 失败时给的是空表，所以"没人评论"和"没拉到"要看 lastError 才分得开。
        if (repo.lastError != null || repo.lastFailureWasNetwork) {
            commentsError = repo.lastError ?: "评论没拉到，检查一下网络或代理。"
        } else {
            comments = fresh
            commentsError = null
        }
        commentsLoading = false
    }

    val send: () -> Unit = send@{
        val body = draft.trim()
        // 名字/头像没设完：发送键已经是灰的，这里再挡一次（键盘回车也会走到这儿）。
        if (!setupGate.ready) {
            sendError = setupGate.refusal
            submitFailed = true
            return@send
        }
        if (body.isEmpty() || sending || target.isEmpty()) return@send
        val repo = repository
        if (repo == null) {
            sendError = "社区后端没连上，评论没发出去。"
            submitFailed = true
            return@send
        }
        scope.launch {
            sending = true
            // 先把回的是哪一条记下来：下面会把 `replyTo` 清掉，清掉之后就取不到了。
            val answered = replyTo
            val id = repo.comment(
                kind = COMMENT_KIND_RECOMMEND,
                target = target,
                body = body,
                author = authorLabel,
                authorId = me,
                replyTo = answered?.id ?: 0L,
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
                //
                // 展开的键是 `threadKey(comment, index)`——后端两层结构里回复的 `replyTo` 就是起始
                // 评论的 id，所以先按它找回起始那一条；拿不到（老后端没有 id）才退回被回复的那条。
                // 直接用 `answered.id` 会在"回的是别人的回复"时打开一个不存在的线程。
                answered?.let { reply ->
                    val starter = comments.firstOrNull { it.id == reply.replyTo } ?: reply
                    expandedThreads = expandedThreads + threadKey(starter, 0)
                }
                reloadKey++
            }
        }
    }

    /** 认同 / 不认同：走调用方接的那个后端调用，回来只改这一条自己的票数。 */
    val voted: (Boolean) -> Unit = voted@{ agree ->
        val suggestion = entry?.suggestion
        // 目录条目不在投票里，按钮也不会画出来；这里再挡一次只是为了让类型收窄。
        if (suggestion == null) return@voted
        val vote = onVoteSuggestion
        if (vote == null || voting) return@voted
        voting = true
        scope.launch {
            val fresh = vote(suggestion.id, agree)
            voting = false
            if (fresh == null) {
                platform.showMessage("没投上：${repository?.lastError ?: "检查一下网络再试"}")
            } else {
                // 票数、够不够上榜、"我投过什么"一起换掉：`voteStateOf` 是从这条数据算出来的，
                // 所以只要换掉它，"还差几票"那半句会跟着更新，不必另算一遍。
                entry = entry?.copy(
                    suggestion = suggestion.copy(
                        agrees = fresh.agrees,
                        disagrees = fresh.disagrees,
                        applied = fresh.applied,
                        mine = agree,
                    ),
                )
            }
        }
    }

    val like: (PlanComment) -> Unit = { comment ->
        if (comment.id > 0L) {
            val wanted = !comment.liked
            // 先按点击改一次屏幕：网络那一趟回来之前，心是立刻亮的。
            comments = comments.updatedComment(comment.id) {
                it.copy(liked = wanted, likes = (it.likes + if (wanted) 1 else -1).coerceAtLeast(0))
            }
            scope.launch {
                val result = repository?.likeComment(comment.id, me, wanted)
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
                        repository?.deleteComment(comment.id, me, token)
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
                title = entry?.name.orEmpty().ifBlank { stringResource(Res.string.recommend_detail_title) },
                onNavigateBack = onNavigateBack,
            )
        },
        bottomBar = {
            // 条目还没到（或已经没了）就没有可评论的对象：这时不画输入栏，免得发到空 target 上。
            if (target.isNotEmpty()) {
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
                // 点页面空白：收表情面板 + 清焦点 + 收输入法，与方案详情页同一套。
                .dismissInputOnTap { pickerOpen = false },
        ) {
            val loaded = entry
            when {
                loading -> CenteredNote(stringResource(Res.string.recommend_detail_loading))

                loaded == null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 失败说原因，成功但没有这一条说"没了"：两句话不一样，人才能决定是刷新还是算了。
                    Text(
                        text = loadError ?: stringResource(Res.string.recommend_detail_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onNavigateBack) {
                        Text(stringResource(Res.string.recommend_detail_back))
                    }
                }

                else -> {
                    val installed = remember(loaded, installedPackages) {
                        loaded.replaces.filter { it in installedPackages }
                    }
                    val threads = remember(comments, order) { comments.threads().inOrder(order) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "hero") {
                            RecommendHero(
                                entry = loaded,
                                installed = installed,
                                accent = remember(loaded.accent, key) { accentOf(loaded.accent, key) },
                                onOpen = { platform.openUrl(loaded.url) },
                                onOpenApp = onOpenApp,
                                // 投票只对社区条目、且只有接上了后端调用时才是可点的；
                                // 目录条目本来就不投票，那一块整个不画（见 [RecommendHero]）。
                                canVote = me.isNotEmpty() && onVoteSuggestion != null,
                                voting = voting,
                                onVote = voted,
                            )
                        }

                        // 评论头：条数与排序开关同一行，和方案详情页一样——它们都在回答"我正在看什么"。
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
                                        text = stringResource(Res.string.recommend_comment_empty),
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceVariant,
                                    )
                                    // "还没有评论"和"这一趟没拉到"不是一件事：后者要说出后端那句话。
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
                            me = me,
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
                                // 收起时把"展开到第几批"一起丢掉：再打开从第一批开始。
                                if (threadId in expandedThreads) shownReplies = shownReplies - threadId
                                expandedThreads = if (threadId in expandedThreads) {
                                    expandedThreads - threadId
                                } else {
                                    expandedThreads + threadId
                                }
                            },
                            onLike = like,
                            // 这一格按被评论的对象（项目地址）分格：首帧网络还没到，先垫上一次那批。
                            cacheTarget = target,
                            loading = commentsLoading,
                        )
                    }
                }
            }
        }
    }
}

/** 一行安静的话，居中摆在空的页面上；加载态与"没找到"共用。 */
@Composable
private fun CenteredNote(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 条目本身的那一段：图标、名字、作者/许可证、分类、介绍、票数状态、能替换的包，和一个去浏览器的按钮。
 *
 * 「能替换掉这些」只在其中还有包装在本机时才画（[installed]）：那些包都没了的时候，
 * 这一栏已经不是下一步，只是一段历史。
 */
@Composable
private fun RecommendHero(
    entry: DetailEntry,
    installed: List<String>,
    accent: Color,
    onOpen: () -> Unit,
    onOpenApp: (String) -> Unit,
    /** 能不能投票：社区条目 + 有身份 + 调用方接了后端调用。 */
    canVote: Boolean = false,
    /** 这一票正在路上：两个按钮一起灰掉，免得同一帧点两下。 */
    voting: Boolean = false,
    onVote: (Boolean) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val suggestion = entry.suggestion
    /** 社区条目够票上榜：标题旁边那枚"已上榜"徽章、以及"投票关闭"那个分支都看它。 */
    val applied = suggestion?.applied == true
    val meta = listOfNotNull(
        authorLineOf(entry.name, entry.author)?.let { stringResource(Res.string.recommend_by, it) },
        entry.license.trim().ifEmpty { null },
    ).joinToString(" · ")

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AppTile(mark = entry.tileMark, accent = accent, size = HeroTileSize, corner = 16.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = scheme.secondaryContainer,
                    ) {
                        Text(
                            text = categoryLabel(entry.category),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (applied) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "已上榜",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(SuggestAgree)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        if (entry.summary.isNotBlank()) {
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (suggestion != null) {
            // 票数那句话：还差几票 / 认同率多少 / 已上榜，来自 [voteStateOf] 那一条现算的答案；
            // 两个数并排写出来，与原来那个弹窗里的一样。
            Text(
                text = voteStateOf(suggestion) +
                    " · ${suggestion.agrees} 认同 / ${suggestion.disagrees} 不认同",
                style = MaterialTheme.typography.labelSmall,
                color = if (applied) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (applied) {
                // 票够了，投票就关了：这里留结果，不留一对还能再按的按钮——那等于在问一个
                // 页面已经回答过的问题。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettledVote()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "票数已够，投票关闭",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompactVote(
                        label = "认同",
                        count = suggestion.agrees,
                        selected = suggestion.mine == true,
                        color = SuggestAgree,
                        enabled = canVote && !voting,
                        onClick = { onVote(true) },
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    CompactVote(
                        label = "不认同",
                        count = suggestion.disagrees,
                        selected = suggestion.mine == false,
                        color = SuggestDisagree,
                        enabled = canVote && !voting,
                        onClick = { onVote(false) },
                    )
                    if (voting) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "…",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(Res.string.recommend_detail_hint),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )

        if (installed.isNotEmpty()) {
            // "装这个，就能卸掉那些" 只有那些包还在时才是下一步；它们都不在了，这一段整个不画。
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.recommend_uninstall_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                installed.forEach { pkg ->
                    TextButton(
                        onClick = { onOpenApp(pkg) },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                    ) {
                        Text(text = pkg, style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = stringResource(Res.string.recommend_uninstall_short),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        Button(
            onClick = onOpen,
            modifier = Modifier.padding(top = 14.dp),
        ) {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(Res.string.recommend_open_browser),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
