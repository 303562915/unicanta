package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.SocialPerson
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.ui.component.UnreadRed
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.FlTopBarHeight
import io.github.samolego.canta.ui.fl.colorOf
import kotlinx.coroutines.launch

/** 页签下标：0 是「关注」，1 是「粉丝」。 */
private const val FOLLOWS_TAB_FOLLOWING = 0
private const val FOLLOWS_TAB_FOLLOWERS = 1

/**
 * 「关注 / 粉丝」两栏名单：一个人关注了谁、谁关注了他。
 *
 * 两栏画在同一页，因为它们是同一个问题的两个方向，也是同一种行：头像、名字、一句关系说明，
 * 右边一颗回关按钮和一颗私信按钮。顶栏把两个方向做成两个可点的页签，选中的那个大一号、
 * 加粗、用正文色，另一个缩成一行小灰字——两边都带着各自的条数，切过去之前就知道那边有几个。
 *
 * 两栏在同一个 [LaunchedEffect] 里一起拉：标题上那两个数字要立刻是对的，只拉当前这一栏
 * 会让另一边的数字停在 0。切页签、点刷新（reloads 加一）和换人（owner 变了）都重新问一次，
 * 因为关注关系是当下的状态，缓存一秒都可能已经不对了。
 *
 * 写关注关系失败时不动本行——[SocialRepository.setFollow] 的 false 同时也表示"现在没关注"，
 * 所以失败与否要看 [SocialRepository.lastError] / [SocialRepository.lastFailureWasNetwork]，
 * 而不是看那个布尔；失败的那一行原样留着，由 [onMessage] 说一句原因。
 */
@Composable
fun FollowListScreen(
    repository: SocialRepository,
    owner: String,
    startOnFollowers: Boolean,
    onOpenProfile: (owner: String) -> Unit,
    onOpenChat: (peerOwner: String) -> Unit,
    onNavigateBack: () -> Unit,
    onMessage: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // 从哪个页签进来：粉丝列表从粉丝那一边打开，关注列表从关注那一边。
    var tab by remember(owner, startOnFollowers) {
        mutableIntStateOf(
            if (startOnFollowers) FOLLOWS_TAB_FOLLOWERS else FOLLOWS_TAB_FOLLOWING,
        )
    }
    // 刷新按钮就把它加一：页签没变、人没变，但这一栏要重新问一次。
    var reloads by remember { mutableIntStateOf(0) }

    var following by remember { mutableStateOf<List<SocialPerson>>(emptyList()) }
    var followers by remember { mutableStateOf<List<SocialPerson>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // 正在写关注关系的那几行：写的时候按钮先别理手指，免得一次点击写两遍。
    var busy by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(tab, owner, reloads) {
        loading = true
        loadError = null
        following = repository.followingOf(owner)
        followers = repository.followersOf(owner)
        // 后端没这个函数、或者手机没连上，都不能读成"没有人"：把原因摆到列表顶上。
        val reason = repository.lastError
        loadError = when {
            reason != null -> reason
            repository.lastFailureWasNetwork -> "没连上后端，检查代理或网络后再试。"
            else -> null
        }
        loading = false
    }

    /**
     * 回关 / 取关。写成功才改这一行，写失败留着原样并说一句：false 在成功取关时也会回来，
     * 所以失败只能由 [SocialRepository.lastError] 那两条来判。
     */
    val toggleFollow: (SocialPerson) -> Unit = { person ->
        if (!busy.contains(person.id)) {
            scope.launch {
                busy = busy + person.id
                val result = repository.setFollow(owner, person.id, !person.iFollow)
                val failed = repository.lastError != null || repository.lastFailureWasNetwork
                if (failed) {
                    onMessage("操作失败，检查代理后再试")
                } else {
                    following = following.withIFollow(person.id, result)
                    followers = followers.withIFollow(person.id, result)
                }
                busy = busy - person.id
            }
        }
    }

    val rows = if (tab == FOLLOWS_TAB_FOLLOWERS) followers else following

    Scaffold(
        topBar = {
            FollowListTopBar(
                tab = tab,
                followingCount = following.size,
                followersCount = followers.size,
                onSelectTab = { tab = it },
                onRefresh = { reloads++ },
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 「后端没回答」和「真的没有人」长得一样，所以原因要摆在列表顶上。
            loadError?.let { reason -> FollowErrorLine(reason) }
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }

                rows.isEmpty() -> FollowEmptyNotice(
                    text = if (tab == FOLLOWS_TAB_FOLLOWERS) {
                        "还没有人关注你"
                    } else {
                        "还没有关注任何人"
                    },
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(rows) { person ->
                        FollowPersonRow(
                            person = person,
                            tab = tab,
                            busy = busy.contains(person.id),
                            onOpenProfile = { onOpenProfile(person.id) },
                            onToggleFollow = { toggleFollow(person) },
                            onOpenChat = { onOpenChat(person.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 名单里 [id] 那一行换成新的关注状态；别人的行原样留着。 */
private fun List<SocialPerson>.withIFollow(id: String, value: Boolean): List<SocialPerson> =
    map { row -> if (row.id == id) row.copy(iFollow = value) else row }

/** 第二行那句小字：后端给了关系开始的时间就原样显示，没给就按页签说清方向。 */
private fun followNoteOf(since: String, tab: Int): String = when {
    since.isNotEmpty() -> since
    tab == FOLLOWS_TAB_FOLLOWERS -> "关注了你"
    else -> "你关注的"
}

/**
 * 顶栏：左边是回去的箭头，中间是两个页签（「关注 N」/「粉丝 N」），右边是重新拉一次的按钮。
 *
 * 页签居中放，两边各留一个 44.dp 的按钮位，标题因此不会偏；两个页签宽度不同也不换行，
 * 切换时只有字号和颜色变，位置不跳。
 */
@Composable
private fun FollowListTopBar(
    tab: Int,
    followingCount: Int,
    followersCount: Int,
    onSelectTab: (Int) -> Unit,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlTopBarHeight)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlBarButton(
                glyph = FlGlyphs.chevronBack,
                contentDescription = "返回",
                onClick = onNavigateBack,
                iconSize = 20.dp,
                filled = false,
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FollowTabText(
                        label = "关注 ${followingCount}",
                        active = tab == FOLLOWS_TAB_FOLLOWING,
                        onClick = { onSelectTab(FOLLOWS_TAB_FOLLOWING) },
                    )
                    Spacer(Modifier.width(6.dp))
                    FollowTabText(
                        label = "粉丝 ${followersCount}",
                        active = tab == FOLLOWS_TAB_FOLLOWERS,
                        onClick = { onSelectTab(FOLLOWS_TAB_FOLLOWERS) },
                    )
                }
            }
            FlBarButton(
                glyph = FlGlyphs.refresh,
                contentDescription = "刷新",
                onClick = onRefresh,
                iconSize = 20.dp,
                filled = false,
            )
        }
    }
}

/** 顶栏上的一个页签：选中的是标题（大一号、加粗、正文色），没选中的是入口（小一号、灰）。 */
@Composable
private fun FollowTabText(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = label,
        style = if (active) FlText.headline else FlText.subheadline,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        color = if (active) colors.onSurface else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/**
 * 一行名单：首字头像、名字、一句关系说明，右边是回关和私信。
 *
 * 整行都能点，打开的是这个人的主页；两颗胶囊自己吃掉点击，所以点按钮不会顺手把主页也开了。
 */
@Composable
private fun FollowPersonRow(
    person: SocialPerson,
    tab: Int,
    busy: Boolean,
    onOpenProfile: () -> Unit,
    onToggleFollow: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(FlSurfaceLevel.Card.colorOf(colors))
            .clickable(onClick = onOpenProfile)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FollowAvatar(label = person.label)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = person.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = followNoteOf(person.since, tab),
                fontSize = 10.sp,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        FollowTogglePill(followed = person.iFollow, enabled = !busy, onClick = onToggleFollow)
        Spacer(Modifier.width(6.dp))
        FollowChatPill(onClick = onOpenChat)
    }
}

/** 回关按钮：没关注是红底白字的「回关」，关注过是灰底的「已关注」，再点一次就是取关。 */
@Composable
private fun FollowTogglePill(followed: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (followed) colors.surfaceContainerHighest else UnreadRed)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (followed) "已关注" else "回关",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (followed) colors.onSurfaceVariant else Color.White,
            maxLines = 1,
        )
    }
}

/** 私信入口：比回关安静一颗，因为它不是这一行要做的事，只是可以从这里去做。 */
@Composable
private fun FollowChatPill(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "私信",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 首字的圆头像：名字是别人给的，这里只取第一个字符，取不到就画一个问号。 */
@Composable
private fun FollowAvatar(label: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(colors.primary.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.take(1).ifEmpty { "?" },
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            maxLines = 1,
        )
    }
}

/** 一行说明「没拿到名单」的灰字，摆在列表顶上。 */
@Composable
private fun FollowErrorLine(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

/** 空态：一句说清这一栏为什么是空的。 */
@Composable
private fun FollowEmptyNotice(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
