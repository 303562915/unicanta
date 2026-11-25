package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.cache.ScreenCache
import io.github.samolego.canta.data.cache.stableMerge
import io.github.samolego.canta.data.community.CommunityPost
import io.github.samolego.canta.data.community.PostComment
import io.github.samolego.canta.data.community.PostDraft
import io.github.samolego.canta.data.community.PostDraftStore
import io.github.samolego.canta.data.community.PostReadStore
import io.github.samolego.canta.data.community.PostStore
import io.github.samolego.canta.data.community.PostThread
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.MiniMarkdown
import io.github.samolego.canta.ui.component.ScrollToTopEffect
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.PostAvatar
import io.github.samolego.canta.ui.component.relativeStamp
import io.github.samolego.canta.ui.component.rememberRememberedListState
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.colorOf
import io.github.samolego.canta.util.BeijingTime
import io.github.samolego.canta.util.currentTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** 一页拉多少条。后端把 `p_limit` 夹在 1..50，这里取一屏够看的数。 */
private const val POSTS_PAGE = 20

/**
 * 帖子列表在 [ScreenCache] 里的槽位。
 *
 * 键里不带身份：帖子是公共列表，谁看都是同一份；带了身份的键反而会让换身份后白等一次加载。
 */
private const val POSTS_CACHE_KEY = "screen:posts"

/** 标题、正文的上限，与后端 `community_posts` 的约束同一个数。 */
private const val POST_TITLE_LIMIT = 120
private const val POST_BODY_LIMIT = 8000

/**
 * 自动存草稿的防抖窗口：还在敲就重新计时，700 毫秒没再动才写一次盘。
 *
 * 不是每一键都写：写盘一次比一次内存赋值贵得多，而且草稿要的是"手停下来那一刻的样子"，
 * 不是每一次中间态。
 */
private const val AUTOSAVE_DELAY_MS = 700L

/**
 * 分类的候选：发帖时点一个，再点一下取消。
 *
 * 固定五个、写死在客户端：后端只把分类当字符串存，所以这一串是"页面给什么"而不是"数据库允许
 * 什么"——以后加一个候选不需要动迁移。
 */
private val POST_CATEGORIES = listOf("闲聊", "求助", "分享", "资源", "其它")

/** 标签上限：个数与每个的长度，与后端 `post_add` 里的归整规则同一个数。 */
private const val TAG_MAX = 5
private const val TAG_LIMIT = 20

/**
 * 编辑器里那排 Markdown 快捷插入：名字 → 追加的片段。
 *
 * 片段都是"能直接念出来"的样子（`**粗体**` 而不是 `****`）：插进去以后改掉中间那几个字就行，
 * 不用先猜这串符号是干嘛的。
 */
private val MD_SNIPPETS = listOf(
    "粗体" to "**粗体**",
    "标题" to "## 小标题",
    "列表" to "- 一项",
    "引用" to "> 引用",
    "代码" to "`代码`",
    "代码块" to "```\n代码\n```",
)

/** 置顶标记的颜色：与「认同」同一个绿，页面上一眼是"被挑出来的那条"。 */
private val PostPinned = Color(0xFF2FA84F)

/** 列表上方的四个口径：最新（后端原序）、热门（赞与回复算出来的热度）、收藏、我的。 */
private enum class PostBoard(val label: String) {
    Latest("最新"),
    Hot("热门"),
    Saved("收藏"),
    Mine("我的"),
}

/**
 * 「社区帖子」这一页：列表 → 帖子详情 → 编辑器，三种形态在同一个 composable 里切。
 *
 * 样子照 fluxdo（Linux.do 第三方客户端）的帖子页取的：
 *
 *  1. **列表** —— 顶上「最新 / 热门 / 我的」+ 分类横向筛选，下面是卡片：左边作者字母头像，
 *     右边标题、分类与标签、摘要、一行元信息（时间、回复数、赞数）。点卡片进详情。
 *  2. **详情** —— 整页而不是对话框：主楼与回帖按楼层排（楼主 / #1 / #2…），正文按 Markdown
 *     渲染，底下一个吸底的回复栏。作者本人能删。
 *  3. **编辑器** —— 标题 + 分类 + 标签 + Markdown 正文（可切预览），输入停顿自动存草稿。
 *
 * 身份：写操作只认后端从 token 认出来的身份（见 `PostStore`），草稿按同一个身份分开存
 * （见 `PostDraftStore`）。[authorId] 为空时这一页只读：点「写帖子」会说"还没认出身份"。
 *
 * @param platform 用来提示（[CantaPlatform.showMessage]）。
 * @param store 帖子数据层；null 时列表只显示一句说明，不装作有数据。
 * @param drafts 草稿；null 时编辑器照样能写，只是关掉就没了。
 * @param author 署名：发帖、回帖时一起发上去，只当名字显示，不当身份。
 * @param authorId 身份码：草稿按它分开，删除按钮也只在作者本人的帖子上出现。
 * @param onNavigateBack 顶栏返回；null 表示这是底栏上的一级页面，顶栏不画返回箭头。
 */
@Composable
internal fun PostsScreen(
    platform: CantaPlatform,
    store: PostStore?,
    drafts: PostDraftStore?,
    author: String,
    authorId: String,
    onNavigateBack: (() -> Unit)? = null,
    /** 已读痕迹与收藏；null 时列表不画未读点、详情里也没有收藏按钮。 */
    reads: PostReadStore? = null,
) {
    val scope = rememberCoroutineScope()
    /** 草稿挂在能认出来的身份上：身份码有了就用它，没有才退回署名。 */
    val who = remember(authorId, author) { authorId.ifEmpty { author } }
    val canWrite = authorId.isNotBlank()
    // 双击底栏「帖子」那一格：这一页的列表回顶部。
    // 位置带记忆（见 [rememberRememberedListState]）：冷启动再进来还停在原来那条帖子。
    val postListState = rememberRememberedListState("posts")
    ScrollToTopEffect(postListState)

    // 这一屏上一次画出来的帖子列表（见 [ScreenCache]）：进来先画它，网络回来再顶上去。
    var posts by remember {
        mutableStateOf(ScreenCache.get<List<CommunityPost>>(POSTS_CACHE_KEY).orEmpty())
    }
    var loading by remember { mutableStateOf(posts.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    /** 还有没有下一页：翻到底之后「加载更多」就收起来，别让人一直点。 */
    var hasMore by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    /** 发帖、回帖、点赞、删帖之后 +1：只有它变了才重新拉。 */
    var reloadKey by remember { mutableIntStateOf(0) }
    /** 正在进行的那一件事：`like:7` / `reply:7` / `delete:7`。 */
    var busyKey by remember { mutableStateOf<String?>(null) }
    /**
     * 已读 / 收藏变了就 +1。
     *
     * 这两件事存在盘上（见 [PostReadStore]），不是 Compose 状态；没有这个计数器，收藏完列表
     * 不会重算，人点了"收藏"却看不到它跑到收藏栏里。
     */
    var readsVersion by remember { mutableIntStateOf(0) }

    // ---- 列表筛选 ----
    var board by remember { mutableStateOf(PostBoard.Latest) }
    var categoryPick by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    /** 盘上这个身份的草稿，最近改过的在前。首帧同步读一次（`read()` 是同步的）。 */
    var draftList by remember(who) {
        mutableStateOf(if (drafts != null) drafts.drafts(who) else emptyList())
    }

    // ---- 编辑器 ----
    var composing by remember { mutableStateOf(false) }
    /** 正在编辑的那篇草稿的本地 id；空串表示没在编辑。 */
    var editorId by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    /** 分类：空串表示没选。候选见 [POST_CATEGORIES]，后端原样存。 */
    var category by remember { mutableStateOf("") }
    /** 已经成形的标签（chip）。 */
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    /** 标签输入框里还没被逗号/空格切开的那一截。 */
    var tagInput by remember { mutableStateOf("") }
    /** 最后一次自动保存的时刻（epoch 毫秒）；0 表示这次还没存过。 */
    var savedAt by remember { mutableStateOf(0L) }
    var posting by remember { mutableStateOf(false) }
    var editorError by remember { mutableStateOf<String?>(null) }

    // ---- 详情 ----
    /** 打开的那条帖子的 id；0 表示还在列表上。 */
    var openId by remember { mutableStateOf(0L) }
    var thread by remember { mutableStateOf<PostThread?>(null) }
    var threadLoading by remember { mutableStateOf(false) }
    var threadError by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf("") }
    /** 二次确认要删的那条帖子；null 表示没有待确认的删除。 */
    var confirmTarget by remember { mutableStateOf<CommunityPost?>(null) }

    // 列表：第一次进来拉一页，之后每次 reloadKey 变动再拉。
    LaunchedEffect(store, reloadKey) {
        val current = store
        if (current == null) {
            loading = false
            refreshing = false
            return@LaunchedEffect
        }
        if (posts.isEmpty()) loading = true
        val fresh = current.list(0, POSTS_PAGE)
        if (fresh != null) {
            // 首页拿回来的条数就能说明还有没有下一页：不够一页就是到底了。
            hasMore = fresh.size >= POSTS_PAGE
            // 就地合并：内容没变的那几条沿用旧实例，整表不重画；一个字节都没变时连状态写入都省掉。
            val merged = stableMerge(posts, fresh) { it.id }
            if (merged !== posts) {
                posts = merged
                // 空回答不写缓存：一次失败或者后端返回空，不该把已经看到过的帖子从本地抹掉，
                // 否则下次进来先画的是一张空表，看着就像「什么都没有」。
                if (merged.isNotEmpty()) ScreenCache.put(POSTS_CACHE_KEY, merged)
            }
            error = null
        } else {
            error = current.reason ?: "帖子没拉到，检查一下网络或代理。"
        }
        loading = false
        refreshing = false
    }

    // 详情：打开哪条拉哪条；回帖/点赞之后 reloadKey 变了也跟着刷一遍，数字与后端一致。
    LaunchedEffect(store, openId, reloadKey) {
        val current = store
        if (current == null || openId <= 0L) return@LaunchedEffect
        threadLoading = true
        threadError = null
        val fresh = current.get(openId)
        if (fresh == null) {
            thread = null
            threadError = current.reason ?: "这条帖子没拉到。"
        } else {
            thread = fresh
            // 已经读了正文，就把这条帖子此时的回帖数记下来：列表上的未读点因此灭掉，
            // 以后回帖变多再重新亮起来（"有新回复"）。
            if (reads != null && reads.markRead(fresh.post.id, fresh.post.comments)) {
                readsVersion++
            }
        }
        threadLoading = false
    }

    /** 加一个标签：去空、单个 20 字、最多 5 个、不许重复；不收的给一句提示。 */
    val addTag: (String) -> Unit = addTag@{ raw ->
        val tag = raw.trim()
        if (tag.isEmpty()) return@addTag
        if (tag.length > TAG_LIMIT) {
            platform.showMessage("单个标签最多 $TAG_LIMIT 字")
            return@addTag
        }
        if (tags.contains(tag)) return@addTag
        if (tags.size >= TAG_MAX) {
            platform.showMessage("标签最多 $TAG_MAX 个")
            return@addTag
        }
        tags = tags + tag
    }

    /**
     * 标签输入：遇到逗号、顿号、分号或空格就地切一个 chip 出来，最后一截没写完的留在输入框里。
     *
     * 就地切成 chip 而不是"发布时再解析"：写的人得能看见这一栏到底有几行标签、哪一行打错了，
     * 才有机会在按发布之前删掉它。
     */
    val onTagInputChange: (String) -> Unit = onTag@{ raw ->
        val text = raw.take(TAG_LIMIT * TAG_MAX + 64)
        var buffer = StringBuilder()
        text.forEach { ch ->
            if (isTagSeparator(ch)) {
                if (buffer.isNotEmpty()) {
                    addTag(buffer.toString())
                    buffer = StringBuilder()
                }
            } else {
                buffer.append(ch)
            }
        }
        tagInput = buffer.toString()
    }

    /*
     * 自动存草稿：键变了就重新计时，所以"一直在敲"不会写盘，手停下来 700 毫秒才写一次。
     *
     * 延时之后要再确认一次还在同一条草稿上：发布成功会把 `composing` / `editorId` 清掉，但那个
     * 协程可能还没被取消，照旧写下去就等于把刚发出去的帖子又存成了草稿。
     *
     * 分类与标签也进键里：只改分类、只加一个标签，同样是"手停下来就该记住"的改动。
     */
    LaunchedEffect(composing, editorId, title, body, category, tags, tagInput) {
        if (!composing) return@LaunchedEffect
        val draftStore = drafts ?: return@LaunchedEffect
        val id = editorId
        if (id.isEmpty() || who.isEmpty()) return@LaunchedEffect
        if (title.isBlank() && body.isBlank()) return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MS)
        if (!composing || editorId != id) return@LaunchedEffect
        val stamp = currentTimeMillis()
        draftStore.save(
            who,
            PostDraft(
                id = id,
                title = title,
                body = body,
                category = category,
                tags = tagsWithPending(tags, tagInput),
                updatedAt = stamp,
            ),
        )
        savedAt = stamp
        draftList = draftStore.drafts(who)
    }

    /** 打开编辑器：[draft] 为 null 就是一篇新的（本地新 id，空标题空正文、没选分类、没标签）。 */
    val openEditor: (PostDraft?) -> Unit = edit@{ draft ->
        if (!canWrite) {
            platform.showMessage("还没认出身份，帖子记不上")
            return@edit
        }
        editorId = draft?.id?.takeIf { it.isNotEmpty() } ?: currentTimeMillis().toString()
        title = draft?.title.orEmpty()
        body = draft?.body.orEmpty()
        category = draft?.category.orEmpty()
        tags = draft?.tags.orEmpty()
        tagInput = ""
        savedAt = draft?.updatedAt ?: 0L
        editorError = null
        composing = true
    }

    /** 取消：有内容就存成草稿并说一声；空的一次什么都不留。 */
    val cancelEditor: () -> Unit = cancel@{
        val draftStore = drafts
        val hasText = title.isNotBlank() || body.isNotBlank()
        if (!hasText) {
            draftStore?.delete(who, editorId)
            draftList = draftStore?.drafts(who).orEmpty()
            composing = false
            editorId = ""
            return@cancel
        }
        if (draftStore != null && who.isNotEmpty()) {
            draftStore.save(
                who,
                PostDraft(
                    id = editorId,
                    title = title,
                    body = body,
                    category = category,
                    tags = tagsWithPending(tags, tagInput),
                    updatedAt = currentTimeMillis(),
                ),
            )
            draftList = draftStore.drafts(who)
        }
        composing = false
        platform.showMessage("已存为草稿")
    }

    /** 发布：成功后删草稿、关编辑器、刷新列表；失败就把后端那句话摆在编辑器里。 */
    val publish: () -> Unit = pub@{
        val current = store
        if (current == null) {
            editorError = "社区后端没连上，帖子没发出去。"
            return@pub
        }
        if (posting) return@pub
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            editorError = "标题不能空"
            return@pub
        }
        val draftId = editorId
        // 输入框里那半截标签也一起发上去：写完了没按逗号的那一个，人也当它是标签。
        val finalTags = tagsWithPending(tags, tagInput)
        posting = true
        editorError = null
        scope.launch {
            val id = current.add(cleanTitle, body, author, category, finalTags)
            posting = false
            if (id == null) {
                editorError = current.reason ?: "没发出去：检查一下网络再试"
                return@launch
            }
            composing = false
            editorId = ""
            title = ""
            body = ""
            category = ""
            tags = emptyList()
            tagInput = ""
            savedAt = 0L
            drafts?.delete(who, draftId)
            draftList = drafts?.drafts(who).orEmpty()
            reloadKey++
            platform.showMessage("已发布")
        }
    }

    /** 从草稿列表里删掉一篇（用户明确不要了）。 */
    val discardDraft: (PostDraft) -> Unit = discarded@{ draft ->
        drafts?.delete(who, draft.id)
        draftList = drafts?.drafts(who).orEmpty()
        if (composing && editorId == draft.id) {
            composing = false
            editorId = ""
            title = ""
            body = ""
            category = ""
            tags = emptyList()
            tagInput = ""
            savedAt = 0L
        }
        platform.showMessage("草稿已删除")
    }

    val loadMore: () -> Unit = more@{
        val current = store ?: return@more
        if (loadingMore || busyKey != null) return@more
        loadingMore = true
        scope.launch {
            val fresh = current.list(posts.size, POSTS_PAGE)
            loadingMore = false
            when {
                fresh == null -> platform.showMessage("没拉到更多：${current.reason ?: "检查一下网络"}")
                fresh.isEmpty() -> {
                    hasMore = false
                    platform.showMessage("到底了")
                }
                else -> posts = posts + fresh.filterNot { next -> posts.any { it.id == next.id } }
            }
        }
    }

    val toggleLike: (CommunityPost) -> Unit = like@{ post ->
        val current = store ?: return@like
        if (busyKey != null) return@like
        busyKey = "like:${post.id}"
        scope.launch {
            val fresh = current.like(post.id, !post.mineLike)
            busyKey = null
            if (fresh == null) {
                platform.showMessage("没点上：${current.reason ?: "检查一下网络"}")
            } else {
                reloadKey++
            }
        }
    }

    val sendReply: () -> Unit = send@{
        val current = store ?: return@send
        val text = reply.trim()
        if (text.isEmpty() || busyKey != null || openId <= 0L) return@send
        busyKey = "reply:$openId"
        scope.launch {
            val count = current.comment(openId, text, author)
            busyKey = null
            if (count == null) {
                platform.showMessage("没回上：${current.reason ?: "检查一下网络"}")
            } else {
                reply = ""
                reloadKey++
            }
        }
    }

    val remove: (CommunityPost) -> Unit = del@{ post ->
        val current = store ?: return@del
        if (busyKey != null) return@del
        busyKey = "delete:${post.id}"
        scope.launch {
            val done = current.delete(post.id)
            busyKey = null
            confirmTarget = null
            if (done) {
                if (openId == post.id) {
                    openId = 0L
                    thread = null
                }
                reloadKey++
                platform.showMessage("已删除")
            } else {
                platform.showMessage("没删掉：${current.reason ?: "只有作者本人能删"}")
            }
        }
    }

    /**
     * 顶栏返回：编辑器里还有字就先存成草稿再走。
     *
     * 防抖窗口是 700 毫秒 —— 敲完立刻按返回，那一刻还没写盘；这里补一次，人才不会因为
     * "点错了返回"丢掉半篇。
     */
    val leave: () -> Unit = leave@{
        if (composing && who.isNotEmpty() && (title.isNotBlank() || body.isNotBlank())) {
            drafts?.save(
                who,
                PostDraft(
                    id = editorId,
                    title = title,
                    body = body,
                    category = category,
                    tags = tagsWithPending(tags, tagInput),
                    updatedAt = currentTimeMillis(),
                ),
            )
            platform.showMessage("已存为草稿")
        }
        onNavigateBack?.invoke()
    }

    /** 关掉详情：把这条的临时状态清干净，回列表时不会闪上一跳的旧内容。 */
    val closeThread: () -> Unit = close@{
        openId = 0L
        thread = null
        threadError = null
        reply = ""
    }

    /** 打开详情：先清空上一次的正文，免得新帖刷新前先显示上一条的字。 */
    val openThread: (CommunityPost) -> Unit = open@{ post ->
        thread = null
        threadError = null
        reply = ""
        openId = post.id
        // 先进加载态：详情那一帧还没有正文，不置 true 会闪一句「这条帖子没拉到」。
        threadLoading = true
    }

    /** 引用某一楼：把那句话做成 Markdown 引用塞进回复框，接着写就行。 */
    val quoteComment: (PostComment) -> Unit = quote@{ comment ->
        val snippet = comment.body.lineSequence().firstOrNull().orEmpty().trim().take(40)
        val head = if (snippet.isEmpty()) "" else "> ${comment.author.ifBlank { "匿名" }}：$snippet\n"
        reply = (head + reply).take(POST_BODY_LIMIT)
        platform.showMessage("已引用，写完点发送")
    }

    /** 当前这一屏的口径算出来的列表：分类、搜索词、排序都只在这一层做，不动网络。 */
    val shown = remember(posts, board, categoryPick, query, authorId, reads, readsVersion) {
        val keyword = query.trim()
        // 收藏用到的是**顺序**（最近收的在前），所以要 List 而不是 Set。
        val savedOrder = if (board == PostBoard.Saved) reads?.savedIds().orEmpty() else emptyList()
        val byFilter = posts.filter { post ->
            (categoryPick.isBlank() || post.category == categoryPick) &&
                (keyword.isEmpty() ||
                    post.title.contains(keyword, ignoreCase = true) ||
                    post.excerpt.contains(keyword, ignoreCase = true)) &&
                (board != PostBoard.Mine || (authorId.isNotBlank() && post.authorId == authorId)) &&
                (board != PostBoard.Saved || savedOrder.contains(post.id))
        }
        when (board) {
            // 置顶永远在前，其余按"赞 3 分、回复 2 分"排：这个权重只是让热闹的浮上来，
            // 不参与任何排序决定，改动它不会改变数据。
            PostBoard.Hot -> byFilter.sortedWith(
                compareByDescending<CommunityPost> { it.pinned }
                    .thenByDescending { it.likes * 3 + it.comments * 2 },
            )

            // 收藏栏按收藏时间倒序：这是"我收起来那一批"的自然顺序。
            // 还没拉到列表里的那几条排到最后（indexOf 是 -1），翻到它们自然会浮上来。
            PostBoard.Saved -> byFilter.sortedBy { post ->
                savedOrder.indexOf(post.id).let { index -> if (index < 0) savedOrder.size else index }
            }

            else -> byFilter
        }
    }

    val canDeleteOpen = authorId.isNotBlank() && thread?.post?.authorId == authorId

    /**
     * 打开的那条帖子旁边能去的地方：同分类、不是它自己，按热闹程度取三条。
     *
     * 从已经拉到手的列表里挑，不额外发请求——帖子详情页底部"还能看点什么"是个顺手的出口，
     * 不该为它多一次往返；列表没拉到的时候就干脆不画。
     */
    val related = remember(posts, thread) {
        val open = thread?.post ?: return@remember emptyList<CommunityPost>()
        posts.asSequence()
            .filter { it.id != open.id }
            .filter { open.category.isBlank() || it.category == open.category }
            .sortedByDescending { it.likes * 2 + it.comments }
            .take(3)
            .toList()
    }

    Scaffold(
        topBar = {
            when {
                openId > 0L -> ScreenTopBar(
                    title = thread?.post?.title?.ifBlank { "帖子" } ?: "帖子",
                    onNavigateBack = closeThread,
                    actions = {
                        val open = thread?.post
                        if (canDeleteOpen && open != null) {
                            TextButton(
                                enabled = busyKey == null,
                                onClick = { confirmTarget = open },
                            ) { Text(if (busyKey == "delete:${open.id}") "正在删…" else "删除") }
                        }
                    },
                )

                composing -> ScreenTopBar(
                    title = "写帖子",
                    onNavigateBack = cancelEditor,
                )

                else -> ScreenTopBar(
                    title = "帖子",
                    // 底栏一级页面不给返回箭头（null 时 ScreenTopBar 自己就不画）；
                    // 有返回的时候才把「先存草稿再走」挂上去。
                    onNavigateBack = onNavigateBack?.let { leave },
                    actions = {
                        // 编辑器开着的时候不再重复一个「写帖子」：那会让人以为能同时写两篇。
                        if (!searchOpen) {
                            TextButton(onClick = { searchOpen = true }) { Text("搜索") }
                        }
                        TextButton(onClick = { openEditor(null) }) { Text("写帖子") }
                    },
                )
            }
        },
    ) { padding ->
        when {
            openId > 0L -> PostThreadPane(
                post = thread?.post,
                comments = thread?.comments.orEmpty(),
                loading = threadLoading,
                error = threadError,
                busyKey = busyKey,
                reply = reply,
                authorId = authorId,
                related = related,
                saved = reads?.isSaved(openId) == true,
                onReplyChange = { reply = it.take(POST_BODY_LIMIT) },
                onSend = sendReply,
                onToggleLike = { thread?.post?.let(toggleLike) },
                onToggleSave = {
                    reads?.let { store ->
                        store.toggleSaved(openId)
                        readsVersion++
                    }
                },
                onQuote = quoteComment,
                onCopy = {
                    thread?.post?.let { post ->
                        platform.copyToClipboard(copyablePost(post))
                        platform.showMessage("帖子已复制")
                    }
                },
                onOpenRelated = { post -> openThread(post) },
                modifier = Modifier.padding(padding),
            )

            composing -> PostEditorPane(
                title = title,
                body = body,
                category = category,
                tags = tags,
                tagInput = tagInput,
                savedAt = savedAt,
                error = editorError,
                posting = posting,
                canPublish = canWrite && !posting,
                onTitleChange = { title = it.take(POST_TITLE_LIMIT) },
                onBodyChange = { body = it.take(POST_BODY_LIMIT) },
                onCategoryChange = { category = it },
                onTagInputChange = onTagInputChange,
                onRemoveTag = { tag -> tags = tags - tag },
                onPublish = publish,
                onCancel = cancelEditor,
                modifier = Modifier.padding(padding),
            )

            else -> PostListPane(
                posts = posts,
                shown = shown,
                loading = loading,
                refreshing = refreshing,
                loadingMore = loadingMore,
                error = error,
                drafts = draftList,
                board = board,
                category = categoryPick,
                query = query,
                searchOpen = searchOpen,
                authorId = authorId,
                canWrite = canWrite,
                hasMore = hasMore,
                hasBackend = store != null,
                unreadOf = { post ->
                    // 一次都没读过（全新装上）时不画点：满屏都是点等于没有提示。
                    reads != null && reads.hasHistory() && reads.isUnread(post.id, post.comments)
                },
                savedOf = { post -> reads?.isSaved(post.id) == true },
                savedTotal = reads?.savedIds()?.size ?: 0,
                onLongPress = { post ->
                    val store = reads
                    if (store == null) {
                        platform.showMessage("收藏要等本机存储就绪")
                    } else {
                        val nowSaved = store.toggleSaved(post.id)
                        readsVersion++
                        platform.showMessage(if (nowSaved) "已收藏" else "已取消收藏")
                    }
                },
                listState = postListState,
                onBoardChange = { board = it },
                onCategoryChange = { categoryPick = it },
                onQueryChange = { query = it },
                onCloseSearch = {
                    searchOpen = false
                    query = ""
                },
                onRefresh = {
                    if (!refreshing) {
                        refreshing = true
                        reloadKey++
                    }
                },
                onLoadMore = loadMore,
                onOpen = openThread,
                onOpenDraft = { openEditor(it) },
                onDeleteDraft = { discardDraft(it) },
                onWrite = { openEditor(null) },
                modifier = Modifier.padding(padding),
            )
        }
    }

    confirmTarget?.let { target ->
        PostDeleteDialog(
            busy = busyKey == "delete:${target.id}",
            onDismiss = { confirmTarget = null },
            onConfirm = { remove(target) },
        )
    }
}

/**
 * 列表：筛选栏（口径 + 分类 + 搜索）和帖子卡片。
 *
 * [shown] 是已经在上面算好的口径（分类/搜索/排序），这里只负责画；点一条交给 [onOpen]。
 * 之所以把口径在外面算：筛选改动不该触发一次网络请求，也不该重置列表滚动位置。
 */
@Composable
private fun PostListPane(
    posts: List<CommunityPost>,
    shown: List<CommunityPost>,
    loading: Boolean,
    refreshing: Boolean,
    loadingMore: Boolean,
    error: String?,
    drafts: List<PostDraft>,
    board: PostBoard,
    category: String,
    query: String,
    searchOpen: Boolean,
    authorId: String,
    canWrite: Boolean,
    hasMore: Boolean,
    hasBackend: Boolean,
    /** 这条帖子现在算不算没看过 / 有新回复（见 [PostReadStore.isUnread]）。 */
    unreadOf: (CommunityPost) -> Boolean,
    /** 这条帖子是不是收起来了。 */
    savedOf: (CommunityPost) -> Boolean,
    /** 收藏一共几条（不一定都在已加载的列表里，空态文案要区分这两件事）。 */
    savedTotal: Int,
    /** 长按一张卡片：切收藏。 */
    onLongPress: (CommunityPost) -> Unit,
    listState: LazyListState,
    onBoardChange: (PostBoard) -> Unit,
    onCategoryChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (CommunityPost) -> Unit,
    onOpenDraft: (PostDraft) -> Unit,
    onDeleteDraft: (PostDraft) -> Unit,
    onWrite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = refreshState,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (searchOpen) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                        singleLine = true,
                        label = { Text("搜标题或摘要") },
                        textStyle = MaterialTheme.typography.bodySmall,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        trailingIcon = {
                            TextButton(onClick = onCloseSearch) { Text("收起") }
                        },
                    )
                }
            }

            item(key = "boards") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PostBoard.entries.forEach { entry ->
                        PostChip(
                            text = entry.label,
                            picked = entry == board,
                            onClick = { onBoardChange(entry) },
                        )
                    }
                    // 一条细竖线把"口径"和"分类"分开：两者能同时生效，但含义不是一件事。
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(scheme.outlineVariant),
                    )
                    PostChip(
                        text = "全部",
                        picked = category.isBlank(),
                        onClick = { onCategoryChange("") },
                    )
                    POST_CATEGORIES.forEach { label ->
                        PostChip(
                            text = label,
                            picked = label == category,
                            onClick = { onCategoryChange(if (label == category) "" else label) },
                        )
                    }
                }
            }

            if (drafts.isNotEmpty()) {
                val newest = drafts.first()
                item(key = "drafts-head") {
                    DraftBanner(
                        count = drafts.size,
                        newest = newest,
                        onContinue = { onOpenDraft(newest) },
                    )
                }
                items(drafts, key = { "draft-${it.id}" }) { draft ->
                    DraftRow(
                        draft = draft,
                        onClick = { onOpenDraft(draft) },
                        onDelete = { onDeleteDraft(draft) },
                    )
                }
            }

            item(key = "posts-head") {
                PostSectionHeader(
                    label = when {
                        board == PostBoard.Mine -> "我发的"
                        board == PostBoard.Hot -> "热门"
                        category.isNotBlank() -> category
                        else -> "帖子"
                    },
                    count = shown.size,
                )
            }

            if (error != null) {
                item(key = "posts-error") { PostNote(text = error) }
            }

            if (loading && posts.isEmpty()) {
                // 首屏还不知道有没有帖子：画四张灰卡而不是转圈。灰卡说的是"一列帖子马上到"，
                // 转圈说的是"卡住了"。也不说"还没有人发帖"：那是另一件事。
                items(count = 4, key = { "posts-skeleton-$it" }) {
                    PostSkeletonCard()
                }
            }

            if (!loading && posts.isNotEmpty() && shown.isEmpty()) {
                item(key = "posts-filtered-empty") {
                    PostNote(
                        text = if (board == PostBoard.Mine && authorId.isBlank()) {
                            "还没认出身份，「我的」这一栏要等身份就绪。"
                        } else if (board == PostBoard.Saved && savedTotal > 0) {
                            "收藏里有 $savedTotal 条，只是都还没拉到列表里：往下翻，或者点「加载更多」。"
                        } else if (board == PostBoard.Saved) {
                            "收藏是空的：在帖子详情里点「收藏」，它就会收到这一栏。"
                        } else {
                            "这个口径下没有帖子，换个分类或者点「最新」看看。"
                        },
                    )
                }
            }

            if (posts.isEmpty() && !loading && error == null) {
                item(key = "posts-empty") {
                    PostNote(
                        text = if (hasBackend) {
                            "还没有人发帖。第一帖可以是你在用的某个方案、某台机器，或者一句吐槽。"
                        } else {
                            "社区后端没连上：这一栏现在只是一张空页，连上没有帖子的时候才是真的没人发。"
                        },
                    )
                }
            }

            items(shown, key = { "post-${it.id}" }) { post ->
                PostCard(
                    post = post,
                    mine = authorId.isNotBlank() && post.authorId == authorId,
                    unread = unreadOf(post),
                    saved = savedOf(post),
                    onClick = { onOpen(post) },
                    onLongClick = { onLongPress(post) },
                )
            }

            if (hasMore && posts.size >= POSTS_PAGE) {
                item(key = "posts-more") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(onClick = onLoadMore, enabled = !loadingMore) {
                            Text(if (loadingMore) "正在拉…" else "加载更多")
                        }
                    }
                }
            }

            item(key = "posts-foot") {
                PostNote(
                    text = if (canWrite) {
                        "帖子只经后端读写：认不出身份就发不出去，也删不了别人的帖子。" +
                            "手停下来 700 毫秒存一次本地草稿，发出去成功之后才删。"
                    } else {
                        "现在只能看：还没认出身份，写帖子会提示重新进一次社区页。"
                    },
                )
            }
        }
    }
}

/**
 * 列表上的一张帖子卡片。
 *
 * 左边是作者字母头像（同一个人永远同一个色），右边四行：标题、分类与标签、摘要、元信息。
 * 摘要只放两行：列表是用来扫的，点进去才是读。
 *
 * [unread] 是"没看过 / 有新回复"：标题左边一个实心点，扫列表时先认它。
 */
@Composable
private fun PostCard(
    post: CommunityPost,
    mine: Boolean,
    unread: Boolean,
    saved: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            // 长按 = 收藏 / 取消收藏：列表上最常用的两个动作（看、收）各占一种手势，不必先进详情。
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = "收藏或取消收藏",
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PostAvatar(
            name = post.author.ifBlank { "匿" },
            seed = post.authorId.ifEmpty { post.author },
            size = 38.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (unread) {
                    // 未读 / 有新回复：一个实心点。比写"未读"两个字省地方，也更容易一眼扫到。
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(scheme.primary, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (post.pinned) {
                    FlGlyphIcon(
                        glyph = FlGlyphs.pin,
                        size = 13.dp,
                        tint = PostPinned,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (post.locked) {
                    FlGlyphIcon(
                        glyph = FlGlyphs.lock,
                        size = 13.dp,
                        tint = scheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = post.title.ifBlank { "（无标题）" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            // 分类 + 标签紧贴标题下面：扫列表的人是按"这帖属于哪一类"挑，而不是按摘要第一句。
            PostMetaChips(category = post.category, tags = post.tags, maxTags = 2)
            if (post.excerpt.isNotBlank()) {
                Text(
                    text = post.excerpt,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append(post.author.ifBlank { "匿名" })
                        val stamp = relativeStamp(post.createdAt)
                        if (stamp.isNotBlank()) {
                            append(" · ")
                            append(stamp)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(8.dp))
                PostStat(count = post.comments, label = "回复", mine = false)
                Spacer(modifier = Modifier.width(8.dp))
                PostStat(count = post.likes, label = "赞", mine = post.mineLike)
                if (mine) {
                    Spacer(modifier = Modifier.width(6.dp))
                    PostBadge(text = "我的", color = scheme.primary)
                }
                if (saved) {
                    Spacer(modifier = Modifier.width(6.dp))
                    // 底色是 secondary，前景就用它的 on 色：深色主题下白字压在浅紫上读不清。
                    PostBadge(text = "收藏", color = scheme.secondary, foreground = scheme.onSecondary)
                }
            }
        }
    }
}

/**
 * 首屏占位卡片：一块头像、三条长短不一的灰条，和真卡片的骨架一样。
 *
 * 目的是让"正在拉"这件事有个形状：转圈看不出下面会出现什么，灰卡看得出是一列帖子。
 */
@Composable
private fun PostSkeletonCard() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(scheme.surfaceVariant),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonBar(widthFraction = 0.72f)
            SkeletonBar(widthFraction = 1f)
            SkeletonBar(widthFraction = 0.45f)
        }
    }
}

/** 骨架里的一条灰条。 */
@Composable
private fun SkeletonBar(widthFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** 元信息里的一个数：大于零才算"有"，零只画一个灰色 0，不抢注意力。 */
@Composable
private fun PostStat(count: Int, label: String, mine: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = "$count $label",
        style = MaterialTheme.typography.labelSmall,
        color = if (mine && count > 0) scheme.primary else scheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/**
 * 详情底部「相关帖子」的一行：标题 + 一行元信息，点它换到那一条。
 *
 * 比列表卡片轻：这里是"看完这帖还能顺手看什么"，不是再铺一张列表。
 */
@Composable
private fun RelatedPostRow(post: CommunityPost, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = post.title.ifBlank { "（无标题）" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                if (post.category.isNotBlank()) {
                    append(post.category)
                    append(" · ")
                }
                append(post.comments)
                append(" 回复 · ")
                append(post.likes)
                append(" 赞")
            },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 复制到剪贴板的那一份：标题、作者与时间、正文、分类与标签。
 *
 * 不拼帖子 id、也不拼站内链接：这个社区没有可分享的帖子地址，拼出来别人也点不开，
 * 反而像一条坏链接。要传给别人时，贴上去的就是能读的整段字。
 */
private fun copyablePost(post: CommunityPost): String = buildString {
    appendLine(post.title.ifBlank { "（无标题）" })
    append("—— ")
    append(post.author.ifBlank { "匿名" })
    // 日期用绝对的那一段（ISO 前 10 位就是 yyyy-MM-dd）：复制出去的文字可能是过几天才被人看到，
    // "3 分钟前"贴到别处就失真了。
    val day = post.createdAt.take(10)
    if (day.length == 10) {
        append(" · ")
        append(day)
    }
    appendLine()
    if (post.body.isNotBlank()) {
        appendLine()
        appendLine(post.body.trim())
    }
    if (post.category.isNotBlank() || post.tags.isNotEmpty()) {
        appendLine()
        if (post.category.isNotBlank()) append("#${post.category} ")
        post.tags.forEach { tag -> append("#$tag ") }
    }
    appendLine()
    append("（${post.comments} 回复 · ${post.likes} 赞）")
}

/**
 * 一条帖子的详情：主楼 + 回帖按楼层排，底下吸着一个回复栏。
 *
 * 用整页而不是对话框：回帖一多，对话框里那点高度连三层都看不完，「楼层」这个概念也就没了。
 */
@Composable
private fun PostThreadPane(
    post: CommunityPost?,
    comments: List<PostComment>,
    loading: Boolean,
    error: String?,
    busyKey: String?,
    reply: String,
    authorId: String,
    related: List<CommunityPost>,
    /** 这条帖子现在收着没有。 */
    saved: Boolean,
    onReplyChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleSave: () -> Unit,
    onQuote: (PostComment) -> Unit,
    onCopy: () -> Unit,
    onOpenRelated: (CommunityPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    // 收进一个局部 val：下面几个 lambda 里要读它的字段，写一处最稳。
    val shown = post

    Column(modifier = modifier.fillMaxSize()) {
        if (shown == null) {
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = error ?: "这条帖子没拉到。",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.error,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "op") {
                    PostFloorCard(
                        floorLabel = "楼主",
                        author = shown.author,
                        authorSeed = shown.authorId.ifEmpty { shown.author },
                        stamp = relativeStamp(shown.createdAt),
                        body = shown.body,
                        badges = buildList {
                            if (shown.pinned) add("置顶")
                            if (shown.locked) add("已锁")
                            if (authorId.isNotBlank() && shown.authorId == authorId) add("我")
                        },
                        footer = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                        enabled = busyKey == null,
                                    onClick = onToggleLike,
                                ) {
                                    Text(
                                        if (busyKey == "like:${shown.id}") "…"
                                        else if (shown.mineLike) "已赞 ${shown.likes}"
                                        else "赞 ${shown.likes}",
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${shown.comments} 条回复",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                // 复制整帖（标题、作者、正文、标签）：「这帖转给我看看」最省事的做法。
                                TextButton(onClick = onCopy) { Text("复制") }
                                // 收藏放在这里而不是顶栏：顶栏已经有删除，再挤一个按钮标题就要被压扁了。
                                TextButton(onClick = onToggleSave) {
                                    Text(if (saved) "已收藏" else "收藏")
                                }
                            }
                        },
                    )
                }

                item(key = "thread-head") {
                    PostSectionHeader(label = "回复", count = shown.comments)
                }

                if (comments.isEmpty()) {
                    item(key = "thread-empty") {
                        PostNote(text = "还没有人回。")
                    }
                }

                // 楼层号就是时间序里的位置：后端按时间正序给，这里 index + 1 直接对得上。
                itemsIndexed(comments, key = { _, item -> "c-${item.id}" }) { index, comment ->
                    PostFloorCard(
                        floorLabel = "#${index + 1}",
                        author = comment.author,
                        authorSeed = comment.authorId.ifEmpty { comment.author },
                        stamp = relativeStamp(comment.createdAt),
                        body = comment.body,
                        badges = if (authorId.isNotBlank() && comment.authorId == authorId) {
                            listOf("我")
                        } else {
                            emptyList()
                        },
                        footer = {
                            if (!shown.locked) {
                                TextButton(onClick = { onQuote(comment) }) { Text("引用回复") }
                            }
                        },
                    )
                }

                if (related.isNotEmpty()) {
                    item(key = "related-head") {
                        PostSectionHeader(label = "相关帖子", count = related.size)
                    }
                    items(related, key = { "rel-${it.id}" }) { rel ->
                        RelatedPostRow(post = rel, onClick = { onOpenRelated(rel) })
                    }
                }

                item(key = "thread-foot") {
                    PostNote(
                        text = "回帖按时间正序，最多显示 200 层；正文支持 Markdown（标题、列表、引用、代码块）。",
                    )
                }
            }

            if (shown.locked) {
                Surface(color = FlSurfaceLevel.Block.colorOf(scheme)) {
                    Text(
                        text = "这条帖已经锁了，回不了。",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            } else {
                ReplyBar(
                    reply = reply,
                    busy = busyKey == "reply:${shown.id}",
                    onChange = onReplyChange,
                    onSend = onSend,
                )
            }
        }
        }
    }

/**
 * 一层楼：左边头像，右边作者、楼层号、时间与正文，底下一行动作。
 *
 * [floorLabel] 是"楼主"或者 "#3" —— 楼层号让"第几层"这件事一眼对得上，也是引用回复的抓手。
 */
@Composable
private fun PostFloorCard(
    floorLabel: String,
    author: String,
    authorSeed: String,
    stamp: String,
    body: String,
    badges: List<String>,
    footer: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PostAvatar(name = author.ifBlank { "匿" }, seed = authorSeed, size = 34.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = author.ifBlank { "匿名" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 名字占满剩下的宽度，楼层号、徽章、时间跟着名字走；名字太长就省略号，
                    // 而不是像以前那样被砍掉一半宽度、右边却留一片白。
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = floorLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                badges.forEach { badge ->
                    Spacer(modifier = Modifier.width(4.dp))
                    PostBadge(
                        text = badge,
                        color = if (badge == "置顶") PostPinned else scheme.primary,
                    )
                }
                if (stamp.isNotBlank()) {
                    Text(
                        text = stamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (body.isBlank()) {
                Text(
                    text = "（没有正文）",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                MiniMarkdown(
                    text = body,
                    baseStyle = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
            }
            footer()
        }
    }
}

/** 吸底回复栏：一行输入 + 发送。点一下就开始写，不占整屏。 */
@Composable
private fun ReplyBar(
    reply: String,
    busy: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 整块吃输入法 inset：edge-to-edge 下系统不再缩窗口，不垫这一下吸底输入栏会被键盘盖住。
    Surface(modifier = Modifier.imePadding(), color = FlSurfaceLevel.Block.colorOf(scheme)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = reply,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 4,
                placeholder = { Text("回复…") },
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (reply.isBlank() || busy) scheme.surfaceVariant else scheme.primary)
                    .clickable(enabled = reply.isNotBlank() && !busy, role = Role.Button, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                FlGlyphIcon(
                    glyph = FlGlyphs.send,
                    size = 20.dp,
                    tint = if (reply.isBlank() || busy) scheme.onSurfaceVariant else scheme.onPrimary,
                )
            }
        }
    }
}

/**
 * 编辑器：标题、分类、标签、正文（可切 Markdown 预览）、字数、自动保存的那一行，以及「取消」「发布」。
 *
 * 整页可滚：正文一长，键盘一弹，按钮就在屏幕外了；`verticalScroll` 让它们跟着走。
 */
@Composable
private fun PostEditorPane(
    title: String,
    body: String,
    category: String,
    tags: List<String>,
    tagInput: String,
    savedAt: Long,
    error: String?,
    posting: Boolean,
    canPublish: Boolean,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onTagInputChange: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onPublish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val savedClock = draftClock(savedAt)
    // 预览只是看一眼：不跨离开保存，所以放在这里 remember 就够。
    var previewing by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (savedClock.isNotEmpty()) "已自动保存 · $savedClock" else "还没存过草稿",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("标题") },
            placeholder = { Text("一句话说清这帖讲什么") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Text(
            text = "${title.length}/$POST_TITLE_LIMIT",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )

        FieldLabel(text = "分类（可不选，再点一下取消）")
        CategoryChips(selected = category, onPick = onCategoryChange)

        FieldLabel(text = "标签（用逗号或空格分开，最多 $TAG_MAX 个）")
        if (tags.isNotEmpty()) {
            TagChips(tags = tags, onRemove = onRemoveTag)
        }
        OutlinedTextField(
            value = tagInput,
            onValueChange = onTagInputChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("标签") },
            placeholder = { Text("标签，用逗号或空格分开，最多 $TAG_MAX 个") },
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(text = if (previewing) "正文 · 预览" else "正文（支持 Markdown）")
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { previewing = !previewing }) {
                Text(if (previewing) "改文字" else "预览")
            }
        }
        if (!previewing) {
            // 一排名目按钮：手机上打 `**` 要切键盘，点一下就能插一段模板，改字比找符号快。
            // 只能往末尾追加——`OutlinedTextField` 用的是 String 值，拿不到游标位置。
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MD_SNIPPETS.forEach { (label, snippet) ->
                    PostChip(
                        text = label,
                        picked = false,
                        onClick = {
                            val glue = if (body.isBlank() || body.endsWith("\n")) "" else "\n"
                            onBodyChange((body + glue + snippet).take(POST_BODY_LIMIT))
                        },
                    )
                }
            }
        }
        if (previewing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                    .padding(10.dp),
            ) {
                if (body.isBlank()) {
                    Text(
                        text = "（没有正文）",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    MiniMarkdown(
                        text = body,
                        baseStyle = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface,
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = body,
                onValueChange = onBodyChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 10,
                label = { Text("正文") },
                placeholder = { Text("细节、机型、ROM、试过什么") },
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )
        }
        Text(
            text = "${body.length}/$POST_BODY_LIMIT",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        if (error != null) {
            Text(text = error, style = MaterialTheme.typography.labelSmall, color = scheme.error)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(enabled = canPublish, onClick = onPublish) {
                Text(if (posting) "正在发…" else "发布")
            }
        }
        Text(
            text = "写下的字（含分类与标签）自动存成本地草稿，取消也不会丢；发出去成功之后草稿才删掉。" +
                "同一身份一分钟只能发一帖。",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** 编辑器里某一栏的小标题。 */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 分类：一行可点的 chip。
 *
 * 用一排手画的 chip 而不是 `FlSegmentedControl`：那个是按等宽分段画的，而且只认"选中第几个"，
 * 表达不了"一个都没选"这个状态——而分类默认就是不选。
 */
@Composable
private fun CategoryChips(
    selected: String,
    onPick: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        POST_CATEGORIES.forEach { label ->
            val picked = label == selected
            PostChip(
                text = label,
                picked = picked,
                onClick = { onPick(if (picked) "" else label) },
            )
        }
    }
}

/** 已经成形的标签：点一下删掉它。 */
@Composable
private fun TagChips(tags: List<String>, onRemove: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.forEach { tag ->
            PostChip(
                text = "$tag ×",
                picked = false,
                onClick = { onRemove(tag) },
            )
        }
    }
}

/** 一枚可点的 chip：选中时实心，没选时描边。 */
@Composable
private fun PostChip(
    text: String,
    picked: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (picked) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (picked) scheme.primary else FlSurfaceLevel.Page.raised.colorOf(scheme))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * 只读的一行 chip：分类 + 最多 [maxTags] 个标签，多出来的写 `+N`。
 *
 * 列表一行只有一行的宽度，所以标签截断；详情传 `maxTags = tags.size` 把全部画出来。
 */
@Composable
private fun PostMetaChips(
    category: String,
    tags: List<String>,
    modifier: Modifier = Modifier,
    maxTags: Int = 3,
) {
    if (category.isBlank() && tags.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val shown = tags.take(maxTags)
    val hidden = tags.size - shown.size
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (category.isNotBlank()) {
            Text(
                text = category,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        shown.forEach { tag ->
            Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        if (hidden > 0) {
            Text(
                text = "+$hidden",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** 「草稿 N 篇 · 继续编辑」那一行：点它接着写最新那篇。 */
@Composable
private fun DraftBanner(
    count: Int,
    newest: PostDraft,
    onContinue: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .clickable(role = Role.Button, onClick = onContinue)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "草稿 $count 篇 · 继续编辑",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "最近 ${draftClock(newest.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Text(
            text = newest.title.ifBlank { newest.body.take(40).ifBlank { "（空草稿）" } },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 草稿列表里的一行：标题、一句预览、时间，右边一个删除。 */
@Composable
private fun DraftRow(
    draft: PostDraft,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = draft.title.ifBlank { "未命名草稿" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    val preview = draft.body.replace('\n', ' ').trim()
                    if (preview.isNotEmpty()) {
                        append(preview.take(40))
                        append(" · ")
                    }
                    append(draftClock(draft.updatedAt))
                    append(" 改过")
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onDelete) { Text("删除") }
    }
}

/** 一个区块的标题：名字，带条数时再跟一个数字。 */
@Composable
private fun PostSectionHeader(label: String, count: Int? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一句说明 / 空状态。 */
@Composable
private fun PostNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

/** 一枚小标记：置顶、已锁、我的。 */
@Composable
private fun PostBadge(
    text: String,
    color: Color,
    foreground: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = foreground,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** 删帖的二次确认：删了回帖和赞一起没了，不能只靠一次点错。 */
@Composable
private fun PostDeleteDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "删除这条帖子？",
        buttons = {
            TextButton(onClick = onDismiss) { Text("取消") }
            Button(enabled = !busy, onClick = onConfirm) {
                Text(if (busy) "正在删…" else "删除")
            }
        },
    ) {
        Text(
            text = "删了之后帖子、回帖和赞都一起没了，恢复不了。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 草稿时间：`HH:mm`（**北京时间**，和页面上其它时间同一个时区）。
 *
 * 这里不用 `relativeStamp`：那是"3 分钟前"，而自动保存要的是"几点几分存的"，一眼能对上墙上的钟。
 * 0 或者算不出来就返回空串，调用方据此不画那一行。
 */
private fun draftClock(updatedAt: Long): String {
    if (updatedAt <= 0L) return ""
    return runCatching {
        val local = Instant.fromEpochMilliseconds(updatedAt).toLocalDateTime(BeijingTime.zone)
        val hour = if (local.hour < 10) "0${local.hour}" else local.hour.toString()
        val minute = if (local.minute < 10) "0${local.minute}" else local.minute.toString()
        "$hour:$minute"
    }.getOrNull().orEmpty()
}

/** 标签的分隔符：中文逗号、英文逗号、顿号、分号、空格、换行都算。 */
private fun isTagSeparator(ch: Char): Boolean =
    ch == ',' || ch == '，' || ch == '、' || ch == ';' || ch == '；' || ch == ' ' || ch == '\n'

/**
 * 把输入框里还没被分隔符切开的那一截并进标签表，规则与输入时一致（去空、20 字、不重复、最多 5 个），
 * 但**不提示**：发布或存草稿那一刻不该再弹一句话。
 */
private fun tagsWithPending(tags: List<String>, pending: String): List<String> {
    val tag = pending.trim()
    if (tag.isEmpty()) return tags
    if (tag.length > TAG_LIMIT) return tags
    if (tags.contains(tag)) return tags
    if (tags.size >= TAG_MAX) return tags
    return tags + tag
}

