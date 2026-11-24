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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.cache.ScreenCache
import kotlinx.serialization.Serializable
import io.github.samolego.canta.data.community.SocialPerson
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.ui.component.UnreadRed
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBarHeight
import kotlinx.coroutines.launch

/**
 * 群名最多打几个字。
 *
 * 后端 `group_create` 会把超过 120 的截掉，这里先挡在 40：群名是列表上那一行的标题，再长也读
 * 不完，而且人不该打完一长串之后才发现它变短了。
 */
private const val GroupCreateTitleMaxLength = 40

/** 勾选圈的直径：比头像小一点，重点留给名字。 */
private val GroupPickCheckSize = 22.dp

/**
 * 这一屏上一次画出来的候选人名单，整份进 [ScreenCache]。
 *
 * 只放画一屏要用的只读数据：能勾的那几张脸。**群名和已勾的人不进缓存**——那是人在这一页正在做的
 * 事，下次进来该是一张干净的勾选表；把上次没建成的选择恢复出来，人只会以为自己已经选过了。
 */
@Serializable
private data class CreateGroupSnapshot(val candidates: List<SocialPerson>)

/**
 * 这一页缓存的槽位：按身份分。
 *
 * 候选人来自「我关注的人」与「关注我的人」，两个方向都是按身份算的，不同身份共用一个槽位会
 * 把别人的关注关系画给这个人。身份为空（异步还没读到）时返回空串，[ScreenCache] 会丢弃空键的写入。
 */
private fun createGroupCacheKey(owner: String): String =
    if (owner.isEmpty()) "" else "screen:creategroup:$owner"

/**
 * 「发起群聊」页：起个名，勾几个人，然后建群。
 *
 * 结构照抖音的发起群聊从上到下排：自绘顶栏（返回 / 标题 / 完成）、一行群名输入、一行「已选 N」，
 * 下面是可勾选的联系人。候选人是「我关注的人」与「关注我的人」两个方向的并集——建群时你大概既
 * 想拉熟人，也想拉刚认识你的人，两个列表分开摆反而要多翻一次页。
 *
 * 这一页只负责收集：群名和身份列表交给 [SocialRepository.createGroup]，成功把会话号交回
 * [onCreated]（去哪一页由调用方决定），失败就说一句 [onMessage]。留空的群名原样传空串，让后端
 * 用它自己的默认名，前端不替它编一个。
 */
@Composable
fun CreateGroupScreen(
    repository: SocialRepository,
    owner: String,
    onCreated: (conversationId: Long) -> Unit,
    onNavigateBack: () -> Unit,
    onMessage: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // 群名是这一页的输入，不进缓存：下次进来该是空的，不该带上一次没建成的那串字。
    var title by remember { mutableStateOf("") }
    // 上一次进来读到的那份候选人先摆上：从消息页点「发起群聊」进来的第一眼就该是能勾的人，
    // 而不是一个「…」。快照在进程级的 [ScreenCache] 里，因为这个 Composable 每次都是新实例。
    //
    // 群名与已勾的人**不从缓存恢复**：那是这一页正在做的动作，不是上一屏的内容。
    val cacheKey = createGroupCacheKey(owner)
    val cached = remember(cacheKey) {
        if (cacheKey.isEmpty()) null else ScreenCache.get<CreateGroupSnapshot>(cacheKey)
    }
    var candidates by remember(cacheKey) {
        mutableStateOf(cached?.candidates.orEmpty())
    }
    // 选中的是身份，不是行：同一个人在两个列表里出现也只算一张票。
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 有缓存就不摆「…」：这一趟刷新只是把名单里新关注的人补进来。
    var loading by remember(cacheKey) { mutableStateOf(cached == null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // 正在建群：这段时间里「完成」不再理会手指，免得一次点击建出两个群。
    var creating by remember { mutableStateOf(false) }

    // 两个方向各问一次，合并去重之后才画：谁先在名单里，那一行就长什么样。
    LaunchedEffect(owner) {
        // 有缓存的那一趟不点 loading：名单还在屏幕上，转圈会把它盖住。
        if (cached == null) loading = true
        loadError = null
        val following = repository.followingOf(owner)
        val followers = repository.followersOf(owner)
        // 拉不到就是空列表，和「真的一个熟人都没有」在界面上长得一样，所以要留一句原因。
        // 失败时**不覆盖**屏幕上那份名单：一次断网不该把能勾的人清空。
        val failure = repository.lastError
        if (failure == null) {
            candidates = groupPickPeople(following, followers)
            if (cacheKey.isNotEmpty()) ScreenCache.put(cacheKey, CreateGroupSnapshot(candidates))
        }
        loadError = failure
        loading = false
    }

    val colors = MaterialTheme.colorScheme
    val canSubmit = selected.isNotEmpty() && !creating

    /** 建群：成功就把会话号交回去，失败留在这页，让人修好代理再点一次。 */
    val create: () -> Unit = {
        if (canSubmit) {
            scope.launch {
                creating = true
                val created = repository.createGroup(owner, title.trim(), selected.toList())
                if (created != null && created > 0L) {
                    onCreated(created)
                } else {
                    onMessage("建群失败，检查代理后再试")
                }
                creating = false
            }
        }
    }

    Scaffold(
        topBar = {
            CreateGroupTopBar(
                canSubmit = canSubmit,
                onNavigateBack = onNavigateBack,
                onDone = create,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            GroupTitleField(title = title, onTitleChange = { title = it })
            Text(
                text = "选择联系人 · 已选 ${selected.size}",
                style = FlText.footnote,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )
            loadError?.let { reason -> CreateGroupErrorLine(reason) }
            when {
                loading -> Text(
                    text = "…",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )

                candidates.isEmpty() -> Text(
                    text = "还没有可以拉进群的人，先去别人的主页关注一下。",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(candidates, key = { person -> "create-group-${person.id}" }) { person ->
                        GroupPickRow(
                            person = person,
                            selected = selected.contains(person.id),
                            onToggle = {
                                selected = if (selected.contains(person.id)) {
                                    selected - person.id
                                } else {
                                    selected + person.id
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶栏：回去的箭头、标题、右边的「完成」。
 *
 * 照这一套页面里其它自绘顶栏的高度与留白（[FlTopBarHeight]、10.dp 边距、[FlText.headline]），
 * 只是尾部动作从圆按钮换成一个文字按钮——「完成」是这一页唯一往前走的路，字比图标说得清。
 */
@Composable
private fun CreateGroupTopBar(
    canSubmit: Boolean,
    onNavigateBack: () -> Unit,
    onDone: () -> Unit,
) {
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
                text = "发起群聊",
                style = FlText.headline,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "完成",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                // 一个人都没勾的时候它是灰的，也点不动：没有「完成」可做。
                color = if (canSubmit) colors.primary else colors.outline,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(enabled = canSubmit, onClickLabel = "建群", onClick = onDone)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 群名那一行：一个圆角输入框，占位说清楚它可以不填。
 *
 * 留空就把空串交给后端，由它落一个默认名——前端猜一个「某某的群」只会和后端的默认名打架。
 */
@Composable
private fun GroupTitleField(title: String, onTitleChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = title,
        onValueChange = { onTitleChange(it.take(GroupCreateTitleMaxLength)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        textStyle = TextStyle(fontSize = 14.sp, color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        maxLines = 1,
        decorationBox = { inner ->
            if (title.isEmpty()) {
                Text(text = "群聊名称（可不填）", fontSize = 14.sp, color = colors.outline)
            }
            inner()
        },
    )
}

/**
 * 一位候选人：圆头像、名字、右边一个勾选圈。
 *
 * 整行都能点，不只是那个圈：手指落在名字上就该勾上，这是这一页唯一要做的事。
 */
@Composable
private fun GroupPickRow(person: SocialPerson, selected: Boolean, onToggle: () -> Unit) {
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
        GroupPickAvatar(label = person.label, size = 40.dp)
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
        GroupPickCheck(checked = selected)
    }
}

/** 勾选圈：选中是红底白勾，没选中是空心描边——两种状态在深浅色下都分得开。 */
@Composable
private fun GroupPickCheck(checked: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(GroupPickCheckSize)
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

/** 首字的圆头像：名字是别人给的，这里只取第一个字符，取不到就画一个问号。 */
@Composable
private fun GroupPickAvatar(label: String, size: Dp) {
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

/** 一行说明「没拿到名单」的灰字，摆在名单顶上。 */
@Composable
private fun CreateGroupErrorLine(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

/**
 * 两个关注方向的人合并成一张候选人名单，按身份去重。
 *
 * [groups] 的顺序就是优先顺序：先给的先占位，后面重复的身份直接跳过。只留带编号的人——建群交上去
 * 的就是编号，一个没有编号的行勾上了也拉不进来，还会和别的空编号行撞成同一个列表 key。
 */
private fun groupPickPeople(vararg groups: List<SocialPerson>): List<SocialPerson> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<SocialPerson>()
    groups.forEach { group ->
        group.forEach { person ->
            // 编号是认人的那一位：名字可以改，编号不会。
            if (person.id.isNotEmpty() && seen.add(person.id)) out += person
        }
    }
    return out
}
