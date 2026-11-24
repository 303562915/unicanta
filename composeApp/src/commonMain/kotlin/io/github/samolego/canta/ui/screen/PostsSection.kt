package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.samolego.canta.data.community.PostStore
import io.github.samolego.canta.data.community.PostThread
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ScrollToTopEffect
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.WIDE_DIALOG_WIDTH
import io.github.samolego.canta.ui.component.relativeStamp
import io.github.samolego.canta.ui.component.rememberRememberedListState
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

/** 置顶标记的颜色：与「认同」同一个绿，页面上一眼是"被挑出来的那条"。 */
private val PostPinned = Color(0xFF2FA84F)

/**
 * 「社区帖子」这一页：发帖、看帖、回帖、点赞，带本地草稿。
 *
 * 一页两种形态，同一个 composable 里切（`composing`），不另开路由：
 *
 *  1. **列表** —— 顶上先是「草稿 N 篇 · 继续编辑」和那几篇草稿（有草稿时才画），下面是帖子：
 *     置顶在前、其余按时间倒序，每条显示置顶标记、标题、摘要、作者、时间、回帖数、赞数。
 *     点一条打开详情对话框（正文全文、点赞、回帖列表、回复框、作者本人的删除）。
 *  2. **编辑器** —— 标题 + 正文 + 字数，底下「取消」「发布」。输入停顿 700 毫秒自动存成草稿，
 *     顶部一行「已自动保存 · HH:mm」；取消时把内容存成草稿并明说，发布成功后草稿从盘上删掉。
 *
 * 身份：写操作只认后端从 token 认出来的身份（见 `PostStore`），草稿按同一个身份分开存
 * （见 `PostDraftStore`）。[authorId] 为空时这一页只读：点「写帖子」会说"还没认出身份"，
 * 而不是让后端记在一串自报号码上、或者把草稿存在没有主人的名下。
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
    // 有缓存时第一帧就是一张有内容的表，不是转圈。
    var posts by remember {
        mutableStateOf(ScreenCache.get<List<CommunityPost>>(POSTS_CACHE_KEY).orEmpty())
    }
    var loading by remember { mutableStateOf(posts.isEmpty()) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** 发帖、回帖、点赞、删帖之后 +1：只有它变了才重新拉。 */
    var reloadKey by remember { mutableIntStateOf(0) }
    /** 正在进行的那一件事：`like:7` / `reply:7` / `delete:7`。 */
    var busyKey by remember { mutableStateOf<String?>(null) }

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

    // ---- 详情对话框 ----
    /** 打开的那条帖子的 id；0 表示没开。 */
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
            return@LaunchedEffect
        }
        if (posts.isEmpty()) loading = true
        val fresh = current.list(0, POSTS_PAGE)
        if (fresh != null) {
            // 就地合并：内容没变的那几条沿用旧实例，整表不重画；一个字节都没变时连状态写入都省掉。
            val merged = stableMerge(posts, fresh) { it.id }
            if (merged !== posts) {
                posts = merged
                ScreenCache.put(POSTS_CACHE_KEY, merged)
            }
            error = null
        } else {
            error = current.reason ?: "帖子没拉到，检查一下网络或代理。"
        }
        loading = false
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
        val text = raw.take(TAG_LIMIT * 3)
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
                fresh.isEmpty() -> platform.showMessage("到底了")
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

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = "帖子",
                // 底栏一级页面不给返回箭头（null 时 ScreenTopBar 自己就不画）；
                // 有返回的时候才把「先存草稿再走」挂上去。
                onNavigateBack = onNavigateBack?.let { leave },
                actions = {
                    // 编辑器开着的时候不再重复一个「写帖子」：那会让人以为能同时写两篇。
                    if (!composing) {
                        TextButton(onClick = { openEditor(null) }) { Text("写帖子") }
                    }
                },
            )
        },
    ) { padding ->
        if (composing) {
            PostEditorPane(
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
        } else {
            val listError = error
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                state = postListState,
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                if (draftList.isNotEmpty()) {
                    val newest = draftList.first()
                    item(key = "drafts-head") {
                        DraftBanner(
                            count = draftList.size,
                            newest = newest,
                            onContinue = { openEditor(newest) },
                        )
                    }
                    items(draftList, key = { "draft-${it.id}" }) { draft ->
                        DraftRow(
                            draft = draft,
                            onClick = { openEditor(draft) },
                            onDelete = { discardDraft(draft) },
                        )
                    }
                }

                item(key = "posts-head") {
                    PostSectionHeader(label = "帖子", count = posts.size)
                }

                if (listError != null) {
                    item(key = "posts-error") { PostNote(text = listError) }
                }

                if (loading && posts.isEmpty()) {
                    // 还不知道有没有帖子的时候只转圈，不说"还没有人发帖"：那是两件事。
                    item(key = "posts-loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                if (posts.isEmpty() && !loading && listError == null) {
                    item(key = "posts-empty") {
                        PostNote(text = "还没有人发帖。第一帖可以是你在用的某个方案、某台机器，或者一句吐槽。")
                    }
                }

                items(posts, key = { "post-${it.id}" }) { post ->
                    PostRow(
                        post = post,
                        mine = authorId.isNotBlank() && post.authorId == authorId,
                        onClick = {
                            thread = null
                            threadError = null
                            reply = ""
                            openId = post.id
                        },
                    )
                }

                if (posts.size >= POSTS_PAGE) {
                    item(key = "posts-more") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            TextButton(onClick = loadMore, enabled = !loadingMore && busyKey == null) {
                                Text(if (loadingMore) "正在拉…" else "加载更多")
                            }
                        }
                    }
                }

                item(key = "posts-foot") {
                    PostNote(
                        text = "帖子只经后端读写：认不出身份就发不出去，也删不了别人的帖子。" +
                            "写下的字每 700 毫秒存一次本地草稿，发出去成功之后才删。",
                    )
                }
            }
        }
    }

    if (openId > 0L) {
        val current = thread?.takeIf { it.post.id == openId }
        PostThreadDialog(
            post = current?.post,
            comments = current?.comments.orEmpty(),
            loading = threadLoading,
            error = threadError,
            busyKey = busyKey,
            reply = reply,
            locked = current?.post?.locked == true,
            canDelete = authorId.isNotBlank() && current?.post?.authorId == authorId,
            onReplyChange = { reply = it.take(POST_BODY_LIMIT) },
            onSend = sendReply,
            onToggleLike = { current?.post?.let(toggleLike) },
            onDeleteRequest = { current?.post?.let { confirmTarget = it } },
            onDismiss = {
                openId = 0L
                thread = null
                threadError = null
                reply = ""
            },
        )
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
 * 编辑器：标题、分类、标签、正文、字数、自动保存的那一行，以及「取消」「发布」。
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

/** 列表上一行帖子：置顶/锁定标记、标题、摘要、作者与时间、两个数。 */
@Composable
private fun PostRow(
    post: CommunityPost,
    mine: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Block.colorOf(scheme))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (post.pinned) {
                PostBadge(text = "置顶", color = PostPinned)
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (post.locked) {
                PostBadge(text = "已锁", color = scheme.outline)
                Spacer(modifier = Modifier.width(6.dp))
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
        PostMetaChips(category = post.category, tags = post.tags)
        if (post.excerpt.isNotBlank()) {
            Text(
                text = post.excerpt,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = buildString {
                append(post.author.ifBlank { "匿名" })
                val stamp = relativeStamp(post.createdAt)
                if (stamp.isNotBlank()) {
                    append(" · ")
                    append(stamp)
                }
                append(" · ")
                append(post.comments)
                append(" 回复 · ")
                append(post.likes)
                append(" 赞")
                if (mine) append(" · 我的")
            },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一枚小标记：置顶、已锁。 */
@Composable
private fun PostBadge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/**
 * 一条帖子的详情：正文全文、点赞、回帖列表、回复框。
 *
 * 回帖放在可滚动的对话框里而不是 LazyColumn：`CantaDialog(scrollable = true)` 已经给了一个
 * 纵向滚动，里面再套一个滚动列表会互相抢手势。后端一次最多给 200 条，撑得住。
 */
@Composable
private fun PostThreadDialog(
    post: CommunityPost?,
    comments: List<PostComment>,
    loading: Boolean,
    error: String?,
    busyKey: String?,
    reply: String,
    locked: Boolean,
    canDelete: Boolean,
    onReplyChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleLike: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 收进一个 val 再用：参数在下面几层 lambda 里要 smart cast，写作一处最省心。
    val target = post
    CantaDialog(
        onDismissRequest = onDismiss,
        title = target?.title?.ifBlank { "帖子" } ?: "帖子",
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text("关闭") }
            if (canDelete) {
                target?.let { shown ->
                    TextButton(
                        enabled = busyKey == null,
                        onClick = onDeleteRequest,
                    ) { Text(if (busyKey == "delete:${shown.id}") "正在删…" else "删除") }
                }
            }
        },
    ) {
        when {
            target == null && loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            target == null -> {
                Text(
                    text = error ?: "这条帖子没拉到。",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.error,
                )
            }

            else -> {
                Text(
                    text = buildString {
                        append(target.author.ifBlank { "匿名" })
                        val stamp = relativeStamp(target.createdAt)
                        if (stamp.isNotBlank()) {
                            append(" · ")
                            append(stamp)
                        }
                        if (target.pinned) append(" · 置顶")
                        if (target.locked) append(" · 已锁定")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                // 详情里把分类和标签全列出来（列表那一行只放得下三个）。
                if (target.category.isNotBlank() || target.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    PostMetaChips(
                        category = target.category,
                        tags = target.tags,
                        maxTags = target.tags.size,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (target.body.isNotBlank()) {
                    Text(
                        text = target.body,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
                            .padding(10.dp),
                    )
                } else {
                    Text(
                        text = "（没有正文）",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = busyKey == null || busyKey == "like:${target.id}",
                        onClick = onToggleLike,
                    ) {
                        Text(
                            if (busyKey == "like:${target.id}") "…"
                            else if (target.mineLike) "已赞 ${target.likes}"
                            else "赞 ${target.likes}",
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${target.comments} 条回复",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "回复",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                )
                if (comments.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "还没有人回。",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    comments.forEach { comment ->
                        PostCommentRow(comment = comment)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                if (locked) {
                    Text(
                        text = "这条帖已经锁了，回不了。",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = reply,
                        onValueChange = onReplyChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        label = { Text("写一句回复") },
                        textStyle = MaterialTheme.typography.bodySmall,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            enabled = reply.isNotBlank() && busyKey == null,
                            onClick = onSend,
                        ) { Text(if (busyKey == "reply:${target.id}") "正在回…" else "回复") }
                    }
                }
            }
        }
    }
}

/** 详情里的一行回帖：作者、时间、正文。 */
@Composable
private fun PostCommentRow(comment: PostComment) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceContainerLow)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = buildString {
                append(comment.author.ifBlank { "匿名" })
                val stamp = relativeStamp(comment.createdAt)
                if (stamp.isNotBlank()) {
                    append(" · ")
                    append(stamp)
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = comment.body,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface,
        )
    }
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
        widthFraction = WIDE_DIALOG_WIDTH,
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
