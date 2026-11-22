package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    onMessage: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
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

    // 详情：进来问一次，加完人再问一次。已经有详情时不摆「…」，免得刷新的时候整页闪一下。
    LaunchedEffect(conversationId, reloads) {
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
                    item(key = "group-members") {
                        GroupInfoMembers(
                            members = current.members,
                            adding = adding,
                            onTapAdd = { adding = !adding },
                            onOpenChat = { onOpenChat(conversationId) },
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
 * 成员区：标题「群成员 (N)」+ 一句「进入聊天」，下面一行五个的网格。
 *
 * 网格末尾那一格是「+」：它和成员共用同一套列宽，所以按五个一组切，切到最后一行不满五格就用
 * 空白格补齐，格子才会对齐在左边那五列上。
 */
@Composable
private fun GroupInfoMembers(
    members: List<ConversationMember>,
    adding: Boolean,
    onTapAdd: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "群成员 (${members.size})",
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
                            GroupMemberCell(label = member.label)
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

/** 一位成员：圆头像 + 下面一行名字（一行就截断）。 */
@Composable
private fun GroupMemberCell(label: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
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
