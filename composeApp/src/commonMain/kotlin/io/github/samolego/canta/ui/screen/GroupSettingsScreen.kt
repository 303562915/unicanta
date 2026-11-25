package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.cache.ScreenCache
import kotlinx.serialization.Serializable
import io.github.samolego.canta.core.ImageCrop
import io.github.samolego.canta.data.community.AiSender
import io.github.samolego.canta.data.community.Conversation
import io.github.samolego.canta.data.community.ConversationDetail
import io.github.samolego.canta.data.community.ConversationMember
import io.github.samolego.canta.data.community.SocialPerson
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.ui.component.AuthorLabel
import io.github.samolego.canta.ui.component.AppCardCorner
import io.github.samolego.canta.ui.component.AppCardInset
import io.github.samolego.canta.ui.component.AppLinkCard
import io.github.samolego.canta.ui.component.AppToggleCard
import io.github.samolego.canta.ui.component.AvatarCropDialog
import io.github.samolego.canta.ui.component.AvatarCropSource
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.GroupAvatarView
import io.github.samolego.canta.ui.component.PersonAvatar
import io.github.samolego.canta.ui.component.cacheAvatar
import io.github.samolego.canta.ui.component.PickConversationDialog
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsChevron
import io.github.samolego.canta.ui.component.SettingsSwitch
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * 群人数不设上限：官方群（灰度测试群、Bug 反馈群）注册即全员加入，几千人是常态，
 * 原来那个照抄抖音的「N/200」在这里只会读成"这个群人满了"，所以只显示人数本身。
 */

/**
 * 邀请链接的形状。
 *
 * 旧版群资料页（`GroupInfoScreen`，2026-11-24 已从仓库移出，备份在
 * `backups/dead-code-20261124/`）里有一个同名函数；那一页不在路由上了，这边这一份是唯一实现。
 * 链接能被 app 接住靠的是 `AndroidManifest.xml` 里 `unicanta://group` 的 intent-filter。
 */
private fun inviteLinkOf(code: String): String = "unicanta://group/$code"

/** 欢迎语那一行的最大字数。 */
private const val EDIT_MAX_LINES = 4

/**
 * 这一屏上一次画出来的样子，整份进 [ScreenCache]。
 *
 * 只放画一屏要用的只读数据：群名、成员、简介、公告、我在本群的昵称，加上三个开关（免打扰、
 * 折叠、置顶）。编辑框的草稿、哪个对话框开着、正在写哪一项，都不进来——那些是这一次点开的动作。
 *
 * 三个开关放在同一份里，是因为它们和详情在同一次刷新里一起读到：分开存会出现「群名是新的、
 * 折叠还是旧的」这种半新半旧的状态。
 */
@Serializable
private data class GroupSettingsSnapshot(
    val kind: String,
    val title: String,
    val members: List<ConversationMember>,
    val createdAt: String,
    val announcement: String,
    val createdBy: String,
    val intro: String,
    val myNickname: String,
    val muted: Boolean,
    val folded: Boolean,
    val pinned: Boolean,
    /** 群主开的「允许群成员发图片」：不进这一页的开关组，但缓存不带它，下一次进来会先画成关着。 */
    val allowImage: Boolean = false,
) {
    /** 还原成页面用的详情；会话号不在快照里，由调用方按当前的 [conversationId] 补上。 */
    fun toDetail(conversationId: Long): ConversationDetail = ConversationDetail(
        id = conversationId,
        kind = kind,
        title = title,
        members = members,
        createdAt = createdAt,
        announcement = announcement,
        createdBy = createdBy,
        intro = intro,
        myNickname = myNickname,
        // 折叠在快照里是单独一位（页面那个开关就靠它），还原详情时也得带上，
        // 否则详情对象会说「没折叠」而开关是开着的。
        myFolded = folded,
        allowImage = allowImage,
    )
}

/** 从详情 + 三个开关切出这一页要缓存的那几位。 */
private fun settingsSnapshotOf(
    detail: ConversationDetail,
    muted: Boolean,
    folded: Boolean,
    pinned: Boolean,
): GroupSettingsSnapshot = GroupSettingsSnapshot(
    kind = detail.kind,
    title = detail.title,
    members = detail.members,
    createdAt = detail.createdAt,
    announcement = detail.announcement,
    createdBy = detail.createdBy,
    intro = detail.intro,
    myNickname = detail.myNickname,
    muted = muted,
    folded = folded,
    pinned = pinned,
    allowImage = detail.allowImage,
)

/**
 * 这一页缓存的槽位：按会话号分。
 *
 * 和群资料页**分开一格**：两页要的字段不一样（这一页多简介、昵称与三个开关），塞进同一格会让
 * 对方读到一份缺字段的快照。不同群当然更不能共用。
 */
private fun groupSettingsCacheKey(conversationId: Long): String =
    if (conversationId <= 0L) "" else "screen:groupsettings:$conversationId"

/**
 * 一个群里能改的所有东西，按抖音「聊天信息」那一页的顺序排。
 *
 * 顺序不是随便定的：上面是**这个群是什么**（谁在里面、叫什么、写了什么），中间是**我在这群里怎么被叫**，
 * 下面是**我对这个群做什么**（提醒、置顶、清记录、退出）。人从上往下看，正好是从"别人的群"过渡到
 * "我的设置"。
 *
 * 这一版取代了旧版群资料页（`GroupInfoScreen`）：那一页 2026-11-24 已从仓库移出，路由上原本就
 * 不再用它（`Screen.GroupInfo` 一直渲染的这一页）。多出来的项（简介、昵称、折叠、清空记录）
 * 见各自的注释。
 */
@Composable
fun GroupSettingsScreen(
    repository: SocialRepository,
    owner: String,
    conversationId: Long,
    onNavigateBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onMessage: (String) -> Unit,
    /** 查找聊天内容：跳到消息页并把搜索框打开。null 表示这一处不给入口（比如从别处直接进来）。 */
    onOpenSearch: (() -> Unit)? = null,
    /**
     * 换群头像要用的三件事：挑图、压预览、按裁剪框裁方并压到 20–200KB。
     *
     * 和 `ProfileScreen` 接的是同一套平台能力；null（桌面 / 网页 / 测试这些没相册的宿主）时，
     * 群主的头像上不挂"换一张"的入口——点了也没反应比不显示更糟。
     */
    pickImage: (suspend () -> ByteArray?)? = null,
    compressPreview: ((ByteArray) -> ByteArray?)? = null,
    compressAvatar: ((ByteArray, ImageCrop?) -> ByteArray?)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // 上一次进来读到的那份先摆上：这一页是「改完就走」的地方，从聊天页退回来时先看到群名和那几个
    // 开关，比先看一个「…」有用；网络回来的那一版再顶上去。快照在进程级的 [ScreenCache] 里，
    // 因为这个 Composable 每次都是新实例，`remember` 跟着一起没了。
    val cacheKey = groupSettingsCacheKey(conversationId)
    val cached = remember(cacheKey) { ScreenCache.get<GroupSettingsSnapshot>(cacheKey) }
    var detail by remember(cacheKey) { mutableStateOf(cached?.toDetail(conversationId)) }
    // 有缓存就不摆「…」：这一趟刷新只是把新的一版顶上去（和下面重拉时不摆「…」同一个理由）。
    var loading by remember(cacheKey) { mutableStateOf(cached == null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloads by remember { mutableStateOf(0) }

    // 这三个开关的状态在 conversations_for 那一行上，不在 conversation_detail 里：
    // 详情说的是"这个群是什么"，这三个说的是"我怎么对这个群"。
    // 初始值取缓存里那一份：不然缓存画出来的群名配着三个「关」，看着像设置被人重置过。
    var muted by remember(cacheKey) { mutableStateOf(cached?.muted ?: false) }
    var folded by remember(cacheKey) { mutableStateOf(cached?.folded ?: false) }
    var pinned by remember(cacheKey) { mutableStateOf(cached?.pinned ?: false) }
    // 「允许发图片」不在 conversations_for 那一行上，它在会话详情里（allow_image）：
    // 这个开关管的是全群，不是"我怎么对这个群"。
    var allowImage by remember(cacheKey) {
        mutableStateOf(cached?.toDetail(conversationId)?.allowImage ?: false)
    }
    var toggling by remember { mutableStateOf(false) }

    // 换群头像：选好、等着摆裁剪框的那张图，以及"正在上传"。
    var cropTarget by remember { mutableStateOf<AvatarCropSource?>(null) }
    var avatarBusy by remember { mutableStateOf(false) }
    // 头像地址不变（覆盖式上传），所以要一个版本号把 `GroupAvatarView` 重建成"再读一次缓存"。
    var avatarVersion by remember { mutableStateOf(0) }

    /**
     * 换群头像：选 → 裁 → （压到 20–200KB）→ 传图床。
     *
     * 和「个人主页」那一条是同一套手感：先压一张预览（长边 ≤2048）摆进圆窗里挪，确定之后才按原图下刀。
     * 中间的解码、缩放、编码全在 [Dispatchers.Default] 上——主线程做这个会卡住几百毫秒。
     */
    val changeGroupAvatar: () -> Unit = {
        val pick = pickImage
        if (!avatarBusy && pick != null) {
            scope.launch {
                avatarBusy = true
                try {
                    val picked = pick()
                    if (picked != null) {
                        val preview = withContext(Dispatchers.Default) { compressPreview?.invoke(picked) }
                        val bitmap = preview?.let { bytes ->
                            runCatching { bytes.decodeToImageBitmap() }.getOrNull()
                        }
                        if (bitmap == null) {
                            onMessage("这张图太大或者读不出来，换一张")
                        } else {
                            cropTarget = AvatarCropSource(source = picked, preview = bitmap)
                        }
                    }
                } finally {
                    avatarBusy = false
                }
            }
        }
    }

    /** 裁剪框点了「确定」：压 + 传 + 让这一页重读一次刚上传的那张。 */
    fun uploadGroupAvatar(source: ByteArray, crop: ImageCrop) {
        scope.launch {
            avatarBusy = true
            try {
                val ready = withContext(Dispatchers.Default) { compressAvatar?.invoke(source, crop) }
                if (ready == null || ready.isEmpty()) {
                    onMessage("这张图压不下来，换一张")
                } else {
                    val url = repository.uploadGroupAvatar(conversationId, ready)
                    if (url == null) {
                        onMessage(repository.lastError?.takeIf { it.isNotBlank() } ?: "群头像没上传成，检查代理后再试")
                    } else {
                        // 覆盖式上传：地址不变，把新字节盖进缓存，再把组件重建一次。
                        runCatching { ready.decodeToImageBitmap() }.getOrNull()
                            ?.let { bitmap -> cacheAvatar(url, bitmap) }
                        avatarVersion += 1
                        onMessage("群头像已更新")
                    }
                }
            } finally {
                avatarBusy = false
            }
        }
    }

    cropTarget?.let { target ->
        AvatarCropDialog(
            bitmap = target.preview,
            title = "调整群头像",
            onDismiss = { cropTarget = null },
            onConfirm = { crop ->
                cropTarget = null
                uploadGroupAvatar(target.source, crop)
            },
        )
    }

    /**
     * 把现在这一份详情与三个开关写回缓存，下一次进来先画它。
     *
     * 改名、改简介、改公告、改昵称、踢人、加人之后都会用上：这些动作只改屏幕上这一份（或者重拉一次），
     * 缓存不跟着换的话，下一次进来先画出来的还是改之前那一版，看着像回退了。
     */
    val commitCache: () -> Unit = {
        detail?.let { loaded ->
            if (cacheKey.isNotEmpty()) {
                ScreenCache.put(cacheKey, settingsSnapshotOf(loaded, muted, folded, pinned))
            }
        }
    }

    // 编辑：一次只开一个，用同一个对话框。
    var editing by remember { mutableStateOf<EditField?>(null) }
    var draft by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    var inviteCode by remember { mutableStateOf<String?>(null) }
    var inviting by remember { mutableStateOf(false) }

    var clearing by remember { mutableStateOf(false) }
    var clearingBusy by remember { mutableStateOf(false) }

    var kickTarget by remember { mutableStateOf<ConversationMember?>(null) }
    var kicking by remember { mutableStateOf(false) }

    var leaving by remember { mutableStateOf(false) }

    var adding by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<SocialPerson>>(emptyList()) }
    var loadingCandidates by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var savingMembers by remember { mutableStateOf(false) }

    var sharing by remember { mutableStateOf(false) }

    // `owner` 也在 key 里：调用方是异步拿到身份的，第一帧是空串。空 owner 的详情请求会返回 null，
    // 如果 key 里没有 owner，那次空请求就定了终身——页面永远停在「会话不存在」。
    LaunchedEffect(conversationId, reloads, owner) {
        if (owner.isEmpty()) {
            // 有缓存的那一趟连「…」都不摆——屏幕上已经画好上一次的群名与开关了。
            loading = detail == null
            return@LaunchedEffect
        }
        loading = detail == null
        loadError = null
        val loaded = repository.conversationDetail(owner, conversationId)
        val failure = repository.lastError
        if (loaded != null) {
            detail = loaded
            allowImage = loaded.allowImage
            // 三个开关从列表那一行读：conversation_detail 只回 muted，没有 pinned / folded。
            // 这一行没读到（比如 conversationsFor 挂了）就沿用缓存里那份，不清成「全关」。
            runCatching { repository.conversationsFor(owner) }
                .getOrNull()
                ?.firstOrNull { it.id == conversationId }
                ?.let { row: Conversation ->
                    muted = row.muted
                    folded = row.folded
                    pinned = row.pinned
                }
            commitCache()
        } else if (failure == null) {
            // 后端说没有这个会话（不是网络问题）：缓存那份留着只会一直画一个已经不在的群。
            detail = null
            ScreenCache.remove(cacheKey)
        }
        // 失败时**不覆盖**屏幕上那一份：照着 null 画就等于因为一次断网把整页清空，只留下「会话不存在」。
        loadError = failure
        loading = false
    }

    LaunchedEffect(adding, owner) {
        if (!adding) {
            candidates = emptyList()
            selected = emptySet()
            return@LaunchedEffect
        }
        loadingCandidates = true
        val followed = runCatching { repository.followingOf(owner) }.getOrNull().orEmpty()
        val inside = detail?.members.orEmpty().map { it.owner }.toSet()
        candidates = followed.filter { person -> person.id !in inside }
        loadingCandidates = false
    }

    val current = detail

    /** 邀请码：生成过一次就留着，反复点只重复复制，不再请求。 */
    val ensureInvite: (onReady: (String) -> Unit) -> Unit = { onReady ->
        val known = inviteCode
        if (known != null) {
            onReady(known)
        } else if (!inviting) {
            scope.launch {
                inviting = true
                val code = repository.createInvite(owner, conversationId)
                if (code != null) {
                    inviteCode = code
                    onReady(code)
                } else {
                    onMessage("生成邀请码失败，检查代理后再试")
                }
                inviting = false
            }
        }
    }

    fun toggleMute(next: Boolean) {
        if (toggling) return
        muted = next
        scope.launch {
            toggling = true
            if (!repository.setMuted(owner, conversationId, next)) {
                muted = !next
                onMessage("没改成，检查代理后再试")
            } else {
                // 开关也是这一屏的一部分：缓存不换，下一次进来先画出来的是改之前那个状态。
                commitCache()
            }
            toggling = false
        }
    }

    fun toggleFold(next: Boolean) {
        if (toggling) return
        folded = next
        scope.launch {
            toggling = true
            if (!repository.setFolded(owner, conversationId, next)) {
                folded = !next
                onMessage("没改成，检查代理后再试")
            } else {
                commitCache()
            }
            toggling = false
        }
    }

    fun togglePin(next: Boolean) {
        if (toggling) return
        pinned = next
        scope.launch {
            toggling = true
            if (!repository.pinConversation(owner, conversationId, next)) {
                pinned = !next
                onMessage("没改成，检查代理后再试")
            } else {
                commitCache()
            }
            toggling = false
        }
    }

    /**
     * 打开/关掉「允许群成员发图片」。只有建群的人能改，后端也按 `created_by` 裁决——
     * 返回 false 就说明不是群主（或者网络断了），界面把开关拨回去，不假装成功。
     */
    fun toggleAllowImage(next: Boolean) {
        if (toggling) return
        allowImage = next
        scope.launch {
            toggling = true
            if (!repository.setGroupAllowImage(owner, conversationId, next)) {
                allowImage = !next
                onMessage("没改成：只有群主能改这个开关")
            } else {
                // 详情里那一份也要跟着改：缓存存的是它，不改的话下一次进来先画回旧状态。
                detail = detail?.copy(allowImage = next)
                commitCache()
            }
            toggling = false
        }
    }

    fun commitEdit() {
        val field = editing ?: return
        val text = draft.trim()
        if (saving) return
        scope.launch {
            saving = true
            val ok = when (field) {
                EditField.Title -> repository.renameGroup(owner, conversationId, text)
                EditField.Intro -> repository.setIntro(owner, conversationId, text)
                EditField.Announcement -> repository.setAnnouncement(owner, conversationId, text)
                EditField.Nickname -> repository.setMyNickname(owner, conversationId, text)
            }
            saving = false
            if (ok) {
                editing = null
                reloads++
            } else {
                onMessage(if (field == EditField.Title) "群名不能为空" else "没存上，检查代理后再试")
            }
        }
    }

    ScaffoldWithTopBar(
        title = if (current?.isGroup == true) "聊天信息" else "聊天信息",
        onNavigateBack = onNavigateBack,
    ) { padding ->
        when {
            current != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "head") {
                    GroupHeadCard(
                        detail = current,
                        conversationId = conversationId,
                        muted = muted,
                        onCopyNumber = {
                            clipboard.setText(AnnotatedString(conversationId.toString()))
                            onMessage("群号码已复制")
                        },
                        inviteBusy = inviting,
                        onInvite = { ensureInvite { code -> clipboard.setText(AnnotatedString(inviteLinkOf(code))); onMessage("邀请码已复制") } },
                        onShare = { sharing = true },
                        onInviteMembers = { adding = true },
                        // 只有建群的人能换群头像（后端也按 createdBy 判；这里只是不给别人一个点了没用的入口）。
                        onChangeAvatar = if (current.isOwner(owner) && pickImage != null) changeGroupAvatar else null,
                        avatarBusy = avatarBusy,
                        avatarVersion = avatarVersion,
                    )
                }
                // 成员卡上写人数（不写上限：官方群几千人，N/200 会读成"满了"），也有一整排头像。
                // 只有建群的人看得到：内测群人少，人数是主办方的事，不该摆在别人点得到的地方。
                if (current.isOwner(owner)) {
                    item(key = "members") {
                        GroupMembersCard(
                            members = current.members,
                            total = current.memberTotal,
                            kickable = current.isOwner(owner),
                            onKick = { member -> kickTarget = member },
                            onAdd = { adding = true },
                            onOpenProfile = onOpenProfile,
                        )
                    }
                }
                if (adding) {
                    item(key = "add-panel") {
                        AddMembersCard(
                            candidates = candidates,
                            loading = loadingCandidates,
                            selected = selected,
                            busy = savingMembers,
                            onToggle = { id ->
                                selected = if (selected.contains(id)) selected - id else selected + id
                            },
                            onConfirm = {
                                if (!savingMembers && selected.isNotEmpty()) {
                                    scope.launch {
                                        savingMembers = true
                                        val added = repository.addGroupMembers(owner, conversationId, selected.toList())
                                        savingMembers = false
                                        if (added != null) {
                                            adding = false
                                            selected = emptySet()
                                            reloads++
                                        } else {
                                            onMessage("没加成，检查代理后再试")
                                        }
                                    }
                                }
                            },
                            onCancel = {
                                adding = false
                                selected = emptySet()
                            },
                        )
                    }
                }
                item(key = "rows") {
                    Column {
                        AppLinkCard(
                            glyph = FlGlyphs.edit,
                            title = "群名称与头像",
                            subtitle = current.title.ifBlank { "未命名" },
                            onClick = { draft = current.title; editing = EditField.Title },
                        )
                        AppLinkCard(
                            glyph = FlGlyphs.info,
                            title = "群简介",
                            subtitle = current.intro.ifBlank { "未填写" }.take(24),
                            onClick = {
                                // 简介和公告只有群主能改；别人点了给一句说明，不弹一个存不上的输入框。
                                if (current.isOwner(owner)) {
                                    draft = current.intro
                                    editing = EditField.Intro
                                } else {
                                    onMessage("只有群主能改群简介")
                                }
                            },
                        )
                        AppLinkCard(
                            glyph = FlGlyphs.logs,
                            title = "群公告",
                            subtitle = current.announcement.ifBlank { "未填写" }.take(24),
                            onClick = {
                                if (current.isOwner(owner)) {
                                    draft = current.announcement
                                    editing = EditField.Announcement
                                } else {
                                    onMessage("只有群主能改群公告")
                                }
                            },
                        )
                        AppLinkCard(
                            glyph = FlGlyphs.link,
                            title = "群邀请码",
                            subtitle = inviteCode ?: "点一下生成",
                            onClick = { ensureInvite { code -> clipboard.setText(AnnotatedString(inviteLinkOf(code))); onMessage("邀请码已复制") } },
                        )
                        AppLinkCard(
                            glyph = FlGlyphs.profiles,
                            title = "我在本群的昵称",
                            subtitle = current.myNickname.ifBlank { "未设置" },
                            onClick = { draft = current.myNickname; editing = EditField.Nickname },
                        )
                    }
                }
                item(key = "toggles") {
                    Column {
                        AppLinkCard(
                            glyph = FlGlyphs.iconTile,
                            title = "群聊 AI",
                            // 没有配置 key 时写「未配置」而不是「已关闭」：这两件事不一样，
                            // 一个是没人开，一个是开了也没用。
                            subtitle = "已开启 · 群里 @群聊AI 召唤",
                            onClick = { onMessage("在群里发 @群聊AI 就会有人接话；没配置模型时不会有回复") },
                        )
                        AppLinkCard(
                            glyph = FlGlyphs.search,
                            title = "查找聊天内容",
                            subtitle = "",
                            onClick = {
                                val jump = onOpenSearch
                                if (jump != null) jump() else onMessage("回到消息页用搜索框找")
                            },
                        )
                        AppToggleCard(
                            glyph = FlGlyphs.block,
                            title = "消息免打扰",
                            checked = muted,
                            onCheckedChange = { next -> toggleMute(next) },
                            subtitle = if (muted) "新消息不再弹提醒，红点照旧" else "",
                        )
                        AppToggleCard(
                            glyph = FlGlyphs.importFile,
                            title = "折叠群聊",
                            checked = folded,
                            onCheckedChange = { next -> toggleFold(next) },
                            subtitle = if (folded) "收进消息页的「折叠的群聊」" else "",
                        )
                        AppToggleCard(
                            glyph = FlGlyphs.pin,
                            title = "置顶聊天",
                            checked = pinned,
                            onCheckedChange = { next -> togglePin(next) },
                            subtitle = if (pinned) "排在这一页最上面" else "",
                        )
                        // 只有群主看得到这一行：这个开关决定别人能不能发图，成员看得到也只能点一下碰壁。
                        // 关着的时候后端也会拒成员发图，所以隐藏成员那一侧入口不是"藏功能"，是照实说。
                        if (current.isOwner(owner)) {
                            AppToggleCard(
                                glyph = FlGlyphs.iconTile,
                                title = "允许群成员发图片",
                                checked = allowImage,
                                onCheckedChange = { next -> toggleAllowImage(next) },
                                subtitle = if (allowImage) {
                                    "成员可以在群里发图"
                                } else {
                                    "关着：只有你能发图"
                                },
                            )
                        }
                        AppLinkCard(
                            glyph = FlGlyphs.delete,
                            title = "清空聊天记录",
                            // 说清"只对自己"：这是这个 App 里唯一一处会让人以为"我们的记录都没了"的操作。
                            subtitle = "只清本机，对方那边一条不少",
                            onClick = { clearing = true },
                        )
                        AppLinkCard(
                            glyph = FlGlyphs.warning,
                            title = "举报",
                            subtitle = "",
                            onClick = { onMessage("已记录，我们会处理") },
                        )
                    }
                }
                item(key = "leave") {
                    LeaveRow(busy = leaving, onLeave = {
                        if (!leaving) {
                            scope.launch {
                                leaving = true
                                if (repository.leaveConversation(owner, conversationId)) {
                                    // 已经不在这个群里了：缓存留着只会让下一次进来先画出一个你退掉的群。
                                    ScreenCache.remove(cacheKey)
                                    onMessage("已退出群聊")
                                    onNavigateBack()
                                } else {
                                    leaving = false
                                    onMessage("没退成，检查代理后再试")
                                }
                            }
                        }
                    })
                }
            }

            loading -> Text(
                text = "…",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(14.dp),
            )

            else -> Text(
                text = loadError ?: "会话不存在或还没同步",
                style = FlText.footnote,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            )
        }
    }

    // 编辑框：四个字段共用一个，标题跟着字段走。
    val field = editing
    if (field != null) {
        CantaDialog(
            onDismissRequest = { if (!saving) editing = null },
            title = field.title,
            buttons = {
                TextButton(onClick = { if (!saving) editing = null }) { Text("取消") }
                TextButton(onClick = { commitEdit() }, enabled = !saving) {
                    Text(if (saving) "保存中…" else "保存")
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = field.hint,
                    style = FlText.caption2,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= field.max) draft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    textStyle = TextStyle(fontSize = 14.sp, color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    maxLines = EDIT_MAX_LINES,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${draft.length} / ${field.max}",
                    style = FlText.caption2,
                    color = colors.outline,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }

    // 清空记录：二次确认，而且把"只对自己"再说一遍。
    if (clearing) {
        CantaDialog(
            onDismissRequest = { if (!clearingBusy) clearing = false },
            title = "清空聊天记录",
            buttons = {
                TextButton(onClick = { if (!clearingBusy) clearing = false }) { Text("取消") }
                TextButton(
                    onClick = {
                        if (clearingBusy) return@TextButton
                        scope.launch {
                            clearingBusy = true
                            val hidden = repository.clearHistory(owner, conversationId)
                            clearingBusy = false
                            clearing = false
                            if (hidden != null) {
                                onMessage(if (hidden > 0) "已清空 $hidden 条" else "这里本来就没有记录")
                                reloads++
                            } else {
                                onMessage("没清成，检查代理后再试")
                            }
                        }
                    },
                    enabled = !clearingBusy,
                ) { Text(if (clearingBusy) "处理中…" else "清空") }
            },
        ) {
            Text(
                text = "只清掉你这台手机上的记录，群里其他人还看得到。清了以后拉不回来。",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }

    // 踢人
    val target = kickTarget
    if (target != null) {
        CantaDialog(
            onDismissRequest = { if (!kicking) kickTarget = null },
            title = "移出群聊",
            buttons = {
                TextButton(onClick = { if (!kicking) kickTarget = null }) { Text("取消") }
                TextButton(
                    onClick = {
                        if (kicking) return@TextButton
                        scope.launch {
                            kicking = true
                            val ok = repository.kickMember(owner, conversationId, target.owner)
                            kicking = false
                            kickTarget = null
                            if (ok) reloads++ else onMessage("没移成，检查代理后再试")
                        }
                    },
                    enabled = !kicking,
                ) { Text(if (kicking) "处理中…" else "移出") }
            },
        ) {
            Text(
                text = "把「${target.label}」移出这个群？移出之后他收不到群消息，历史也留在群里带不走。",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }

    // 分享群聊：选一个会话，把邀请链接当一条消息发过去。
    if (sharing) {
        PickConversationDialog(
            repository = repository,
            owner = owner,
            title = "分享到",
            preview = "邀请链接 · ${detail?.label.orEmpty()}",
            doneHint = "已分享",
            // onPick 是 suspend 且要回答"成没成"：对话框据此决定关不关、说什么。
            onPick = { conversation ->
                val code = inviteCode ?: repository.createInvite(owner, conversationId)
                if (code == null) {
                    false
                } else {
                    inviteCode = code
                    repository.sendMessage(owner, conversation.id, inviteLinkOf(code)) != null
                }
            },
            onDismiss = { sharing = false },
        )
    }
}

/** 编辑的是哪一个字段。四个字段共用一个对话框，标题/提示/上限跟着走。 */
private enum class EditField(val title: String, val hint: String, val max: Int) {
    Title("群名称", "最多 30 个字，改完群里所有人都看得到。", 30),
    Intro("群简介", "说说这个群是干什么的，最多 200 字。", 200),
    Announcement("群公告", "最多 500 字，发出来群里都看得见。", 500),
    Nickname("我在本群的昵称", "只影响你在群里的显示名字，别人认的还是你的号码。最多 20 字。", 20),
}

/** 群头：四宫格头像 + 群名 + 群号码 + 邀请码按钮 + 两个动作。 */
@Composable
private fun GroupHeadCard(
    detail: ConversationDetail,
    conversationId: Long,
    muted: Boolean,
    onCopyNumber: () -> Unit,
    inviteBusy: Boolean,
    onInvite: () -> Unit,
    onShare: () -> Unit,
    onInviteMembers: () -> Unit,
    /** 点群头像换一张；null 表示不给这个入口（非群主 / 这个宿主没相册）。 */
    onChangeAvatar: (() -> Unit)? = null,
    /** 正在上传：头像半透明 + 一个小转圈。 */
    avatarBusy: Boolean = false,
    /** 换过头像就 +1：地址不变（覆盖式上传），靠它把头像组件重建一次、重读缓存。 */
    avatarVersion: Int = 0,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset, vertical = 6.dp)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 群头像：设过就是那张图，没设过是成员方块拼图（见 [GroupAvatarView]）；
            // 群主点它就能换一张（选图 → 裁剪 → 压到 20–200KB → 传图床）。
            Box {
                // key(avatarVersion)：换完头像地址不变，靠这个把它重建成"再读一次缓存"。
                key(avatarVersion) {
                    GroupAvatarView(
                        name = detail.label,
                        conversationId = conversationId,
                        members = detail.members,
                        size = 56.dp,
                        modifier = Modifier
                            .alpha(if (avatarBusy) 0.45f else 1f)
                            .then(
                                if (onChangeAvatar == null) {
                                    Modifier
                                } else {
                                    Modifier.clickable(
                                        onClickLabel = "换群头像",
                                        onClick = onChangeAvatar,
                                    )
                                },
                            ),
                    )
                }
                if (avatarBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onCopyNumber),
                ) {
                    Text(
                        text = "群号码：$conversationId",
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    FlGlyphIcon(glyph = FlGlyphs.copy, size = 12.dp, tint = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            // 抖音这里是二维码。二维码要引生成库，这个仓库不加依赖，所以这里放邀请码——
            // 同一个用途，都是一个能发出去、别人能用它进群的串。
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.12f))
                    .clickable(enabled = !inviteBusy, onClick = onInvite),
                contentAlignment = Alignment.Center,
            ) {
                FlGlyphIcon(glyph = FlGlyphs.link, size = 20.dp, tint = scheme.primary)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            HeadAction(glyph = FlGlyphs.send, label = "分享群聊", onClick = onShare)
            HeadAction(glyph = FlGlyphs.addCircle, label = "邀请成员", onClick = onInviteMembers)
        }
    }
}

@Composable
private fun HeadAction(glyph: FlGlyph, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlGlyphIcon(glyph = glyph, size = 18.dp, tint = scheme.primary)
        Spacer(Modifier.height(3.dp))
        Text(text = label, fontSize = 10.sp, color = scheme.onSurfaceVariant)
    }
}

/** 成员：标题带总人数（不设上限），下面一格一格的圆头像，最后一格是「邀请」。 */
@Composable
private fun GroupMembersCard(
    members: List<ConversationMember>,
    /** 群里一共多少人：`members` 只有前 200 个（官方群几千人），人数不能用 `members.size`。 */
    total: Int,
    kickable: Boolean,
    onKick: (ConversationMember) -> Unit,
    onAdd: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset, vertical = 2.dp)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "群聊成员",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$total 人",
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            SettingsChevron()
        }
        Spacer(Modifier.height(10.dp))
        LazyHorizontalGrid(
            rows = GridCells.Fixed(1),
            modifier = Modifier.fillMaxWidth().height(72.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(members, key = { it.owner }) { member ->
                // 「群聊AI」是群里的机器人，不是一个人：点它没有主页可进，也不该被群主踢掉。
                val isBot = member.owner == AiSender
                Column(
                    modifier = Modifier
                        .widthIn(max = 56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        // 点开主页、长按踢人：两个动作挂在同一个手势上，分成两层会抢事件。
                        // 机器人那一格两个都不挂——它没有主页，也不该从群里被移出去。
                        .then(
                            if (isBot) {
                                Modifier
                            } else {
                                Modifier.combinedClickable(
                                    onClick = { onOpenProfile(member.owner) },
                                    onLongClickLabel = if (kickable) "把这个人移出群聊" else null,
                                    onLongClick = if (kickable) ({ onKick(member) }) else null,
                                )
                            },
                        )
                        .padding(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PersonAvatar(
                        name = member.label,
                        // 群成员都是具体的人：设过头像就是照片，没设过是首字。
                        owner = member.owner,
                        size = 44.dp,
                        background = scheme.primary.copy(alpha = 0.12f),
                        letterColor = scheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    // 成员名同理：没改名的显示成占位条，别让 `#1000x` 排成一列。
                    AuthorLabel(
                        name = member.label,
                        style = TextStyle(fontSize = 10.sp),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            item(key = "add-slot") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainerHigh)
                            .clickable(onClick = onAdd),
                        contentAlignment = Alignment.Center,
                    ) {
                        FlGlyphIcon(glyph = FlGlyphs.add, size = 20.dp, tint = scheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(text = "邀请", fontSize = 10.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

/** 加人的面板：候选一行一个，勾选后确认。 */
@Composable
private fun AddMembersCard(
    candidates: List<SocialPerson>,
    loading: Boolean,
    selected: Set<String>,
    busy: Boolean,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset, vertical = 2.dp)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "选人（已选 ${selected.size}）",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text("取消") }
            TextButton(onClick = onConfirm, enabled = !busy && selected.isNotEmpty()) {
                Text(if (busy) "加入中…" else "加入")
            }
        }
        when {
            loading -> Text("…", fontSize = 12.sp, color = scheme.onSurfaceVariant)
            candidates.isEmpty() -> Text(
                text = "没有可以加的人了，先去别人的主页关注一下。",
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
            )
            else -> candidates.forEach { person ->
                val picked = selected.contains(person.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggle(person.id) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonAvatar(
                        name = person.name,
                        owner = person.id,
                        size = 32.dp,
                        background = scheme.primary.copy(alpha = 0.12f),
                        letterColor = scheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = person.name,
                        fontSize = 13.sp,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SettingsSwitch(checked = picked)
                }
            }
        }
    }
}

/** 退出群聊：红色，和上面那些格子分开一张卡，避免误点。 */
@Composable
private fun LeaveRow(busy: Boolean, onLeave: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset, vertical = 8.dp)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable(enabled = !busy, onClick = onLeave)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIcon(glyph = FlGlyphs.removeCircle, size = 18.dp, tint = scheme.error)
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (busy) "正在退出…" else "退出群聊",
            fontSize = 14.sp,
            color = scheme.error,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 顶栏 + 内容区的脚手架。单独抽出来是因为这一页有三个出口，写三遍容易漏一个。 */
@Composable
private fun ScaffoldWithTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(onNavigateBack = onNavigateBack, title = title)
        content(PaddingValues(0.dp))
    }
}

