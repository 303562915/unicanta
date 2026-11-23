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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.ConversationDetail
import io.github.samolego.canta.data.community.ConversationMember
import io.github.samolego.canta.data.community.SocialPerson
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.UnreadRed
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBarHeight
import kotlinx.coroutines.launch

/** 成员网格一行放几格：五格在手机上刚好一格半个字宽都不到，名字还读得下去。 */
private const val GroupMembersPerRow = 5

/** 群名最多打几个字：后端 `group_rename` 自己会截到 120，这边先挡住，免得名字悄悄变短。 */
private const val GroupInfoTitleMaxLength = 40

/** 成员/「+」那一格的圆：和消息页的头像同一个尺寸，两页看着是同一套。 */
private val GroupInfoCellAvatarSize = 44.dp

/** 勾选圈的直径。 */
private val GroupInfoCheckSize = 22.dp

/**
 * 群资料页：群名、成员、加人、退群。
 *
 * 进来的第一件事是把详情拉全（[SocialRepository.conversationDetail]）：群名、成员表、创建时间
 * 都在那一次返回里，页面不等第二次请求。之后所有写操作（改名、加人、退群）都由后端判定成不成，
 * 本页只负责把结果搬回界面：改名就地更新标题，加人重新问一次详情，退群退回上一页。
 *
 * 成员是网格，一行五格、按五个一组切；「+」格是网格的最后一格，点开才去问「关注我的人」与
 * 「我关注的人」，并把已经在群里的人滤掉——拉进来一个已经在群里的人只是白点一次。
 */
@Composable
fun GroupInfoScreen(
    repository: SocialRepository,
    owner: String,
    conversationId: Long,
    onNavigateBack: () -> Unit,
    onOpenChat: (conversationId: Long) -> Unit = {},
    /** 点成员格子，打开那个人的主页。 */
    onOpenProfile: (owner: String) -> Unit = {},
    onMessage: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // 邀请链接只做到"复制"这一步：这个 App 没有能接 `unicanta://` 的注册入口，
    // 假装有一个再提示"已打开"会骗人。复制出去，粘给谁由人自己决定。
    val clipboard = LocalClipboardManager.current
    var detail by remember { mutableStateOf<ConversationDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // 加完人要把详情再问一次；改这一位就是「重拉」，比在页面里手工拼成员表可靠。
    var reloads by remember { mutableIntStateOf(0) }

    var renaming by remember { mutableStateOf(false) }
    var titleDraft by remember { mutableStateOf("") }
    var savingTitle by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    var adding by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<SocialPerson>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loadingCandidates by remember { mutableStateOf(false) }
    var savingMembers by remember { mutableStateOf(false) }

    // 群公告：编辑态与草稿。只有建群的人能进这一态（后端也这么判，这里只是不给按钮）。
    var editingAnnouncement by remember { mutableStateOf(false) }
    var announcementDraft by remember { mutableStateOf("") }
    var savingAnnouncement by remember { mutableStateOf(false) }

    // 踢人：先弹确认，再发请求。要踢的那个人的身份挂在 [kickTarget] 上。
    var kickTarget by remember { mutableStateOf<ConversationMember?>(null) }
    var kicking by remember { mutableStateOf(false) }

    // 邀请码：生成过一次就留着，反复点不该每次都请求。
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var inviting by remember { mutableStateOf(false) }

    // 详情：进来问一次，加完人再问一次。已经有详情时不摆「…」，免得刷新的时候整页闪一下。
    //
    // `owner` 也在 key 里，这不是可选项：调用方是从 `DeviceIdentity.current(...)` 异步拿到身份的，
    // 第一帧传进来的是空串。`conversationDetail` 对空 owner 直接返回 null（它没法知道你是谁），
    // 如果 key 里没有 owner，那次空请求就定了终身——身份加载好也不会再问一次，页面永远停在
    // 「会话不存在或还没同步」。key 带上它，身份一到就重跑。
    LaunchedEffect(conversationId, reloads, owner) {
        if (owner.isEmpty()) {
            // 还没读到身份。留着「…」比报「不存在」诚实：这时候我们确实还不知道。
            loading = true
            return@LaunchedEffect
        }
        loading = detail == null
        loadError = null
        val loaded = repository.conversationDetail(owner, conversationId)
        detail = loaded
        // 拉不到就留一句原因：空页和「后端没通」在界面上长得一样。
        loadError = repository.lastError
        if (loaded != null) titleDraft = loaded.title
        loading = false
    }

    // 候选名单只在展开「添加成员」的时候问：不进这个面板就不花这两个请求。
    LaunchedEffect(adding, owner) {
        if (!adding) {
            candidates = emptyList()
            selected = emptySet()
            return@LaunchedEffect
        }
        loadingCandidates = true
        val following = repository.followingOf(owner)
        val followers = repository.followersOf(owner)
        candidates = groupAddPeople(following, followers)
        loadingCandidates = false
    }

    val colors = MaterialTheme.colorScheme
    val current = detail
    val memberIds = current?.members?.map { member -> member.owner }?.toSet() ?: emptySet()
    // 已经在群里的人不再列出来：后端会去掉重复，界面上就不该给一个点了也没变化的勾。
    val pickable = candidates.filter { person ->
        person.id.isNotEmpty() && !memberIds.contains(person.id)
    }

    /** 所有人点一下：勾上或者取消，只动本地这一份选择。 */
    val togglePerson: (SocialPerson) -> Unit = { person ->
        selected = if (selected.contains(person.id)) {
            selected - person.id
        } else {
            selected + person.id
        }
    }

    /** 改群名：后端说成了才改标题，失败留一句原因。 */
    val saveTitle: () -> Unit = {
        if (!savingTitle) {
            scope.launch {
                savingTitle = true
                val title = titleDraft.trim()
                val renamed = repository.renameGroup(owner, conversationId, title)
                if (renamed) {
                    // 就地更新：这一页改名之后变的只有标题，不用为它重拉整页。
                    detail = detail?.copy(title = title)
                    renaming = false
                } else {
                    onMessage("改名失败，检查代理后再试")
                }
                savingTitle = false
            }
        }
    }

    /** 加人：写成功就收起面板并重拉详情，成员名字只有后端才知道。 */
    val addMembers: () -> Unit = {
        if (selected.isNotEmpty() && !savingMembers) {
            scope.launch {
                savingMembers = true
                val added = repository.addGroupMembers(owner, conversationId, selected.toList())
                if (added != null) {
                    adding = false
                    selected = emptySet()
                    reloads += 1
                } else {
                    onMessage("加人失败，检查代理后再试")
                }
                savingMembers = false
            }
        }
    }

    /** 退群：成了就离开这一页，没成说一句——群还在，别让人以为已经退了。 */
    val leave: () -> Unit = {
        if (!leaving) {
            scope.launch {
                leaving = true
                if (repository.leaveConversation(owner, conversationId)) {
                    onNavigateBack()
                } else {
                    onMessage("退群失败，检查代理后再试")
                }
                leaving = false
            }
        }
    }

    /**
     * 存群公告。空串等于撤掉，所以这里不拦空——"把公告清掉"是正常操作。
     *
     * 失败只提示不保留编辑态：留着草稿会让人以为它已经存进去了，一退出页面就白写。
     */
    val saveAnnouncement: () -> Unit = {
        if (!savingAnnouncement) {
            scope.launch {
                savingAnnouncement = true
                val text = announcementDraft.trim()
                if (repository.setAnnouncement(owner, conversationId, text)) {
                    detail = detail?.copy(announcement = text)
                    editingAnnouncement = false
                    onMessage(if (text.isEmpty()) "公告已撤下" else "公告已更新")
                } else {
                    onMessage("改公告失败：只有建群的人能改")
                }
                savingAnnouncement = false
            }
        }
    }

    /**
     * 踢掉确认框里的那个人。成了就把本地成员表里那一行拿掉并重拉一次详情。
     *
     * 本地先拿掉是为了让这一下立刻有反应；重拉才是准的（成员数、候选名单都跟着变）。
     */
    val kick: () -> Unit = {
        val target = kickTarget
        if (target != null && !kicking) {
            scope.launch {
                kicking = true
                if (repository.kickMember(owner, conversationId, target.owner)) {
                    detail = detail?.let { loaded ->
                        loaded.copy(members = loaded.members.filterNot { it.owner == target.owner })
                    }
                    reloads++
                    onMessage("已移出群聊")
                } else {
                    onMessage("没踢成：只有建群的人能踢")
                }
                kicking = false
                kickTarget = null
            }
        }
    }

    /**
     * 拿邀请码并复制成一条 `unicanta://group/<code>`。
     *
     * 生成的码是长期的：这个群只发一个，反复点拿到的都是同一个，所以第一次拿到就留在
     * [inviteCode] 里，后面点只是重新复制一遍，不再请求后端。
     */
    val copyInvite: () -> Unit = {
        val known = inviteCode
        if (known != null) {
            clipboard.setText(AnnotatedString(inviteLinkOf(known)))
            onMessage("邀请链接已复制")
        } else if (!inviting) {
            scope.launch {
                inviting = true
                val code = repository.createInvite(owner, conversationId)
                if (code != null) {
                    inviteCode = code
                    clipboard.setText(AnnotatedString(inviteLinkOf(code)))
                    onMessage("邀请链接已复制")
                } else {
                    onMessage("生成邀请链接失败，检查代理后再试")
                }
                inviting = false
            }
        }
    }

    Scaffold(
        topBar = { GroupInfoTopBar(onNavigateBack = onNavigateBack) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            loadError?.let { reason -> GroupInfoErrorLine(reason) }
            when {
                // 有详情就先画详情：加完人重拉的时候不摆「…」，整页不闪。
                current != null -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "group-header") {
                        GroupInfoHeader(
                            detail = current,
                            renaming = renaming,
                            titleDraft = titleDraft,
                            savingTitle = savingTitle,
                            onTitleChange = { titleDraft = it },
                            onStartRename = {
                                titleDraft = current.title
                                renaming = true
                            },
                            onSaveTitle = saveTitle,
                        )
                    }
                    // 没公告又不是群主时这一项根本不插：一张写着"暂无公告"的空卡只是占地方。
                    if (current.announcement.isNotEmpty() || current.isOwner(owner)) {
                        item(key = "group-announcement") {
                            GroupAnnouncementCard(
                                announcement = current.announcement,
                                isOwner = current.isOwner(owner),
                                editing = editingAnnouncement,
                                draft = announcementDraft,
                                saving = savingAnnouncement,
                                onStartEdit = {
                                    announcementDraft = current.announcement
                                    editingAnnouncement = true
                                },
                                onDraftChange = { announcementDraft = it },
                                onSave = saveAnnouncement,
                                onCancel = { editingAnnouncement = false },
                            )
                        }
                    }
                    if (current.isGroup) {
                        item(key = "group-invite") {
                            GroupInviteRow(
                                busy = inviting,
                                generated = inviteCode != null,
                                onClick = copyInvite,
                            )
                        }
                    }
                    item(key = "group-members") {
                        GroupInfoMembers(
                            members = current.members,
                            adding = adding,
                            // 踢人只给建群的人：成员表本身是公开的，能长按出菜单这件事不该人人都行。
                            kickable = current.isOwner(owner) && !adding,
                            // 名单也只给建群的人；别人只看得到「群成员」和进入聊天的入口。
                            showRoster = current.isOwner(owner),
                            onKick = { member -> kickTarget = member },
                            onTapAdd = { adding = !adding },
                            onOpenChat = { onOpenChat(conversationId) },
                            onOpenProfile = onOpenProfile,
                        )
                    }
                    if (adding) {
                        item(key = "group-add-header") {
                            GroupInfoAddHeader(
                                selectedCount = selected.size,
                                busy = savingMembers,
                                onConfirm = addMembers,
                                onCancel = {
                                    adding = false
                                    selected = emptySet()
                                },
                            )
                        }
                        when {
                            loadingCandidates -> item(key = "group-add-loading") {
                                Text(
                                    text = "…",
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }

                            pickable.isEmpty() -> item(key = "group-add-empty") {
                                Text(
                                    text = "没有可以添加的人了，先去别人的主页关注一下。",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                )
                            }

                            else -> items(
                                items = pickable,
                                key = { person -> "group-add-${person.id}" },
                            ) { person ->
                                GroupInfoPickRow(
                                    person = person,
                                    selected = selected.contains(person.id),
                                    onToggle = { togglePerson(person) },
                                )
                            }
                        }
                    }
                    item(key = "group-leave") {
                        GroupInfoLeaveButton(busy = leaving, onLeave = leave)
                    }
                }

                loading -> Text(
                    text = "…",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )

                else -> Text(
                    text = "会话不存在或还没同步",
                    style = FlText.footnote,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )
            }
        }

        // 踢人要先问一遍：长按弹出菜单已经是一次确认了，但"移出群聊"是不可逆的
        // （对方要重新被拉进来），多一次点击比事后解释便宜。
        val target = kickTarget
        if (target != null) {
            CantaDialog(
                onDismissRequest = { if (!kicking) kickTarget = null },
                title = "移出群聊",
                buttons = {
                    TextButton(onClick = { if (!kicking) kickTarget = null }) {
                        Text("取消")
                    }
                    TextButton(onClick = kick, enabled = !kicking) {
                        Text(if (kicking) "处理中…" else "移出")
                    }
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
    }
}

/**
 * 顶栏：回去的箭头 + 标题。
 *
 * 高度、留白与字号照这一套页面里其它自绘顶栏（[FlTopBarHeight]、10.dp 边距、
 * [FlText.headline]）：顶栏在几个页面之间换来换去时不该跳高度。
 */
@Composable
private fun GroupInfoTopBar(onNavigateBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(FlTopBarHeight).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlBarButton(
                glyph = FlGlyphs.chevronBack,
                contentDescription = "返回",
                onClick = onNavigateBack,
                iconSize = 20.dp,
                filled = false,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "群聊信息",
                style = FlText.headline,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 头部：群名（右边一个改名的笔），下面是建群时间。
 *
 * 改名不弹对话框，就在原地把标题换成输入框：这一页只有一件事是「改名字」，弹一层窗反而多一次点击。
 */
@Composable
private fun GroupInfoHeader(
    detail: ConversationDetail,
    renaming: Boolean,
    titleDraft: String,
    savingTitle: Boolean,
    onTitleChange: (String) -> Unit,
    onStartRename: () -> Unit,
    onSaveTitle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (renaming) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = titleDraft,
                    onValueChange = { onTitleChange(it.take(GroupInfoTitleMaxLength)) },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    textStyle = TextStyle(fontSize = 15.sp, color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    maxLines = 1,
                    decorationBox = { inner ->
                        if (titleDraft.isEmpty()) {
                            Text(text = "群聊名称（可不填）", fontSize = 15.sp, color = colors.outline)
                        }
                        inner()
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "保存",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (savingTitle) colors.outline else colors.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(
                            enabled = !savingTitle,
                            onClickLabel = "保存群名",
                            onClick = onSaveTitle,
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = detail.label,
                    style = FlText.headline,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
                FlBarButton(
                    glyph = FlGlyphs.edit,
                    contentDescription = "改群名",
                    onClick = onStartRename,
                    size = 32.dp,
                    iconSize = 16.dp,
                    filled = false,
                )
                Spacer(Modifier.weight(1f))
            }
        }
        if (detail.createdAt.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "创建于 ${groupStampOf(detail.createdAt)}",
                style = FlText.caption2,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 邀请码拼成链接的唯一一处。
 *
 * 拼法只在这里出现一次：以后要换成网页落地页（`https://.../join/<code>`），改这一行就够，
 * 不用去找每个用到的地方。
 */
private fun inviteLinkOf(code: String): String = "unicanta://group/$code"

/**
 * 群公告：一张卡。
 *
 * 有公告时谁都能看；没有公告时只有建群的人看到「设置群公告」这一行——别人看到的是**什么都不显示**
 * （调用方在没内容又不是群主时根本不插这一项）。空着一张卡写着"暂无公告"只是占地方。
 *
 * [editing] 时整张卡变成一个输入框 + 存/取消：公告是一段话，不该用单行输入框。
 */
@Composable
private fun GroupAnnouncementCard(
    announcement: String,
    isOwner: Boolean,
    editing: Boolean,
    draft: String,
    saving: Boolean,
    onStartEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "群公告",
                style = FlText.caption2,
                fontWeight = FlText.emphasized,
                color = colors.primary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (isOwner && !editing) {
                Text(
                    text = if (announcement.isEmpty()) "设置" else "编辑",
                    fontSize = 11.sp,
                    color = colors.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClickLabel = "编辑群公告", onClick = onStartEdit)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                textStyle = TextStyle(fontSize = 13.sp, color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                maxLines = 6,
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(
                            text = "写点什么给全群看（留空保存就是撤下公告）",
                            fontSize = 13.sp,
                            color = colors.outline,
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (saving) "保存中…" else "保存",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (saving) colors.outline else colors.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(enabled = !saving, onClickLabel = "保存公告", onClick = onSave)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Text(
                    text = "取消",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(enabled = !saving, onClickLabel = "取消编辑", onClick = onCancel)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        } else if (announcement.isEmpty()) {
            Text(
                text = "还没有公告",
                style = FlText.caption2,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        } else {
            Text(
                text = announcement,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = colors.onSurface,
            )
        }
    }
}

/**
 * 邀请链接那一行：点了生成并复制。
 *
 * 不显示二维码、也不显示那条链接本身：链接有几十个字符，摆在群资料页上又长又没法选，
 * 而这个页面的用途是"给朋友发一条能进群的消息"——复制走就是全部。
 */
@Composable
private fun GroupInviteRow(busy: Boolean, generated: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .clickable(enabled = !busy, onClickLabel = "复制邀请链接", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIcon(glyph = FlGlyphs.link, size = 18.dp, tint = colors.primary)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "邀请链接",
                style = FlText.subheadline,
                color = colors.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    busy -> "正在生成…"
                    generated -> "已生成 · 点一下复制"
                    else -> "生成一条能发出去的进群链接"
                },
                style = FlText.caption2,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        FlGlyphIcon(glyph = FlGlyphs.copy, size = 16.dp, tint = colors.outline)
    }
}

/**
 * 成员区：标题「群成员 (N)」+ 一句「进入聊天」，下面一行五个的网格。
 *
 * 网格末尾那一格是「+」：它和成员共用同一套列宽，所以按五个一组切，切到最后一行不满五格就用
 * 空白格补齐，格子才会对齐在左边那五列上。
 *
 * 每个成员格子（[GroupMemberCell]）自己是一块入口，点开那个人的主页（[onOpenProfile]）。
 */
@Composable
private fun GroupInfoMembers(
    members: List<ConversationMember>,
    adding: Boolean,
    kickable: Boolean,
    /**
     * 看不看得到这份名单。
     *
     * 只有建群的人为 true。别人进来只看得到一行「群成员」和「进入聊天」——人数是内测群的
     * 主办方的事，人少的时候摆在谁都能点开的地方只会让人尴尬。
     */
    showRoster: Boolean,
    onKick: (ConversationMember) -> Unit,
    onTapAdd: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (showRoster) "群成员 (${members.size})" else "群成员",
                style = FlText.footnote,
                fontWeight = FlText.emphasized,
                color = colors.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "进入聊天",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.primary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClickLabel = "进入聊天", onClick = onOpenChat)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        // 名单只给建群的人：别人看到的是"这里有一群人"，看不到有几个。
        if (showRoster) {
            // 末尾补一个 null 当「+」那一格：它混在同一张名单里，才和成员排在同一行网格上。
            val cells: List<ConversationMember?> = buildList {
                members.forEach { member -> add(member) }
                add(null)
            }
            cells.chunked(GroupMembersPerRow).forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    line.forEach { member ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (member == null) {
                                GroupAddCell(expanded = adding, onClick = onTapAdd)
                            } else {
                                GroupMemberCell(
                                    label = member.label,
                                    owner = member.owner,
                                    // 建群的人长按别人的格子才弹「移出群聊」；自己的格子不给这个菜单，
                                    // 想走是「退出群聊」那个按钮的事。
                                    onKick = if (kickable && member.owner.isNotEmpty()) {
                                        { onKick(member) }
                                    } else {
                                        null
                                    },
                                    onOpenProfile = onOpenProfile,
                                )
                            }
                        }
                    }
                    repeat(GroupMembersPerRow - line.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 一位成员：圆头像 + 下面一行名字（一行就截断）。
 *
 * 整格（头像 + 名字）一起可点，点开 [owner] 的主页。没有身份号的人点了也没处去，
 * 所以 [owner] 为空时这一格不响应——不留一个点了没反应的格子。
 *
 * [onKick] 非空时（只有建群的人看别人时才非空）长按弹「移出群聊」。长按而不是加一个小叉：
 * 格子只有五个像素那么宽，多一个叉会把名字挤成一团，而且踢人是要确认的动作，不该一点就中。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupMemberCell(
    label: String,
    owner: String,
    onKick: (() -> Unit)?,
    onOpenProfile: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                enabled = owner.isNotEmpty(),
                onClickLabel = "看主页",
                onLongClickLabel = if (onKick != null) "移出群聊" else null,
                onLongClick = onKick,
                onClick = { onOpenProfile(owner) },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GroupInfoAvatar(label = label)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}

/** 网格末尾那一格「+」：描边圆里一个加号，点一下展开加人面板，再点一下收起。 */
@Composable
private fun GroupAddCell(expanded: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = if (expanded) "收起添加成员" else "添加成员",
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(GroupInfoCellAvatarSize)
                .clip(CircleShape)
                .border(1.dp, colors.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(
                glyph = FlGlyphs.add,
                size = 18.dp,
                tint = if (expanded) colors.primary else colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (expanded) "收起" else "添加",
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 「添加成员」面板的标题行：已选几个人，右边是取消与确认。 */
@Composable
private fun GroupInfoAddHeader(
    selectedCount: Int,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val canConfirm = selectedCount > 0 && !busy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "选择要添加的人 · 已选 ${selectedCount}",
            style = FlText.footnote,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "取消",
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClickLabel = "取消添加成员", onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            text = "确认",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            // 一个人都没勾的时候它是灰的，也点不动。
            color = if (canConfirm) colors.primary else colors.outline,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable(enabled = canConfirm, onClickLabel = "确认添加成员", onClick = onConfirm)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * 一位可添加的人：圆头像、名字、右边一个勾选圈。
 *
 * 和「发起群聊」页那一行是同一套画法，但这里是本文件自己的私有实现：那一页的勾选行是它的私有
 * 符号，两个页面各写一份，谁改都不牵动另一边。
 */
@Composable
private fun GroupInfoPickRow(person: SocialPerson, selected: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .clickable(onClickLabel = person.label, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupInfoAvatar(label = person.label, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = person.label,
            style = FlText.subheadline,
            fontWeight = FlText.emphasized,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        GroupInfoCheck(checked = selected)
    }
}

/** 勾选圈：选中是红底白勾，没选中是空心描边。 */
@Composable
private fun GroupInfoCheck(checked: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(GroupInfoCheckSize)
            .clip(CircleShape)
            .then(
                if (checked) {
                    Modifier.background(UnreadRed)
                } else {
                    Modifier.border(1.5.dp, colors.outlineVariant, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            FlGlyphIcon(glyph = FlGlyphs.check, size = 14.dp, tint = Color.White)
        }
    }
}

/** 首字的圆头像：名字是别人给的，取第一个字符，取不到就画一个问号。 */
@Composable
private fun GroupInfoAvatar(label: String, size: Dp = GroupInfoCellAvatarSize) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.trim().take(1).ifEmpty { "?" },
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            maxLines = 1,
        )
    }
}

/** 底部那颗红色的字：退群是不可逆的离开，用错误色说清楚它和上面那些动作不是一类。 */
@Composable
private fun GroupInfoLeaveButton(busy: Boolean, onLeave: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "退出群聊",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (busy) colors.outline else colors.error,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceContainer)
                .clickable(enabled = !busy, onClickLabel = "退出群聊", onClick = onLeave)
                .padding(vertical = 12.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "退出之后这个群不会再出现在消息列表里。",
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 一行说明「没拿到详情」的灰字，摆在页面顶上。 */
@Composable
private fun GroupInfoErrorLine(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

/**
 * 建群时间只用最朴素的那种写法：后端给的是 `2026-11-22T19:20:33Z`，这里截到分钟，
 * 把中间那个 `T` 换成空格。不引时间库，也不换算时区。
 */
private fun groupStampOf(createdAt: String): String = createdAt.take(16).replace('T', ' ')

/**
 * 「关注我的人」与「我关注的人」合并成一张可添加名单，按身份去重。
 *
 * [groups] 的顺序就是优先顺序：先给的先占位。只留带编号的人——加人交上去的是编号，没有编号的
 * 行勾上了也加不进来，还会和别的空编号行撞成同一个列表 key。
 */
private fun groupAddPeople(vararg groups: List<SocialPerson>): List<SocialPerson> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<SocialPerson>()
    groups.forEach { group ->
        group.forEach { person ->
            if (person.id.isNotEmpty() && seen.add(person.id)) out += person
        }
    }
    return out
}
