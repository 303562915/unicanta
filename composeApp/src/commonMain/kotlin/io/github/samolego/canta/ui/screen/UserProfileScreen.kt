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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.cache.ScreenCache
import kotlinx.serialization.Serializable
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.PlanSavedStore
import io.github.samolego.canta.data.community.SocialRepository
import io.github.samolego.canta.data.community.SocialUserProfile
import io.github.samolego.canta.ui.component.AuthorLabel
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.PersonAvatar
import io.github.samolego.canta.ui.component.PickConversationDialog
import io.github.samolego.canta.ui.component.UnreadRed
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyph
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlText
import kotlinx.coroutines.launch

/** 封面高度：抖音那一张背景图的份量，白字压在它下半截。 */
private val CoverHeight = 190.dp

/** 头像直径，以及那圈白色描边的宽度。 */
private val AvatarSize = 96.dp
private val AvatarRing = 3.dp

/** 头像往下压出封面的那一截；[CoverGap] 是封面之后留给这半截头像的高度。 */
private val AvatarHang = 36.dp
private val CoverGap = AvatarHang + 8.dp

/** 封面上那几个半透明圆钮的直径。 */
private val GlassButtonSize = 32.dp

/** 关注按钮的高度、私信方钮的宽度，以及两个按钮共用的圆角。 */
private val FollowHeight = 44.dp
private val MessageButtonWidth = 56.dp
private val ActionShape = RoundedCornerShape(10.dp)

/** 「作品 / 收藏」页签下面那条选中下划线。 */
private val TabUnderlineWidth = 28.dp

/** 作品与收藏两栏的格子缝：一条一行，卡片之间留两个点的缝。 */
private val GridGap = 2.dp

/** 主页下面的两栏页签。 */
private enum class ProfileTab { Plans, Favorites }

/**
 * 这一页的一屏快照。
 *
 * 为什么放在 [ScreenCache] 而不是这里的 `remember`：这一页每次被导航进来都是新的 Composable
 * 实例，`remember` 跟着一起没了，于是「进主页 → 骨架 → 转圈 → 等网络」成了固定动作。存一份只读
 * 快照在进程里，进来先把上一次画的东西摆上，网络回来了那一版再顶上去。
 *
 * 只放画一屏需要的只读数据（[SocialUserProfile] / [CommunityPlan] 都是不可变 data class）；
 * 不存 MutableState、不存协程、不存回调——那些属于这一份 Composable 实例，存下来要么泄漏，
 * 要么一读就是错的状态。
 *
 * [following] 单独存一份：它虽然等于 `profile.iFollow`，但点过关注之后本地那个值会先变，
 * 下次进来不带它的话先画出来的会是「还没关注」，看着像点丢了。
 */
@Serializable
private data class UserProfileSnapshot(
    val profile: SocialUserProfile?,
    val plans: List<CommunityPlan>,
    val following: Boolean,
)

/**
 * 缓存键带上 [owner]：不同人的主页不能共用一格，否则点开 B 会先闪一下 A 的封面、数字和作品。
 *
 * 身份为空时返回 null（这个页面不读也不写缓存）：第一帧的身份可能是异步拿到之前的空串，
 * 空身份那一格会被所有人串用。
 */
private fun profileCacheKey(owner: String): String? =
    if (owner.isEmpty()) null else "screen:profile:$owner"

/**
 * 一个人的主页，照抖音个人主页的样子排：一张封面横幅、压着封面的圆头像与昵称、一行
 * 「获赞 / 关注 / 粉丝」、简介与 IP、关注 + 私信两个按钮、两栏页签，最后是作品网格。
 *
 * 这一页只画后端真的存了的东西：数字来自 [SocialRepository.userProfile]，作品来自
 * [PlanRepository.plansByOwner]，两样都是拉不到就退成 0 与空列表，页面照常画，失败的原因
 * 用一行灰字交代（[SocialRepository.lastError] / [PlanRepository.lastError]）。简介来自
 * [SocialUserProfile.bio]，本人看这一页时右侧有一支笔，点开改（[SocialRepository.setBio]）；
 * 没写过就还是那一句「这个人很懒」占位。
 *
 * @param owner 主页是谁的：身份字符串，也是昵称拿不到时的兜底名字。
 * @param viewer 看这一页的人，关注按钮写的就是他和 [owner] 之间那条边。
 * @param onOpenPlan 点作品格子，打开那个方案。
 * @param onOpenChat 点私信，把 [owner] 交给调用方去开会话。
 */
@Composable
fun UserProfileScreen(
    repository: PlanRepository,
    social: SocialRepository,
    owner: String,
    viewer: String,
    onOpenPlan: (planId: Long) -> Unit,
    onOpenChat: (owner: String) -> Unit,
    onNavigateBack: () -> Unit,
    /** 点「关注」或「粉丝」那一格：打开那一栏的名单，[followers] 为 true 时是粉丝。 */
    onOpenFollows: (followers: Boolean) -> Unit = {},
    onMessage: (String) -> Unit = {},
    /**
     * 收起来的方案（见 [PlanSavedStore]）：主页这一栏的星读它、写它。
     *
     * 收藏只在本机；null 时这里不画星、"收藏"那一栏也就只有一句空态。
     */
    savedPlans: PlanSavedStore? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // 上一屏留在进程里的快照：有它就直接铺上，`loading` 从 false 开始，不再闪骨架。
    val cacheKey = profileCacheKey(owner)
    val cached = remember(owner) { cacheKey?.let { key -> ScreenCache.get<UserProfileSnapshot>(key) } }
    var loading by remember(owner, viewer) { mutableStateOf(cached == null) }
    var profile by remember(owner, viewer) { mutableStateOf(cached?.profile) }
    var plans by remember(owner, viewer) { mutableStateOf(cached?.plans.orEmpty()) }
    var following by remember(owner, viewer) { mutableStateOf(cached?.following ?: false) }
    var busy by remember(owner, viewer) { mutableStateOf(false) }
    // 名片分享：点开时弹会话选择，选完由对话框自己调接口。
    var sharing by remember(owner, viewer) { mutableStateOf(false) }
    var tab by remember(owner, viewer) { mutableStateOf(ProfileTab.Plans) }
    // 改简介的对话框三位一体：开没开、框里的草稿、保存请求在不在路上。
    var showBioEditor by remember(owner, viewer) { mutableStateOf(false) }
    var bioDraft by remember(owner, viewer) { mutableStateOf("") }
    var savingBio by remember(owner, viewer) { mutableStateOf(false) }
    var problem by remember(owner, viewer) { mutableStateOf<String?>(null) }
    // 收藏只写本机（[PlanSavedStore]），所以这一页自己留一份编号与一个版本号：点了星当场重画。
    var savedPlanIds by remember(owner) { mutableStateOf(savedPlans?.savedIds().orEmpty()) }
    var savesVersion by remember(owner) { mutableIntStateOf(0) }
    // 「收藏」那一栏要画出方案本身，而这台机器上只存了编号：编号逐条去问后端，问不到的跳过。
    var savedPlanRows by remember(owner) { mutableStateOf<List<CommunityPlan>>(emptyList()) }
    var savedLoading by remember(owner) { mutableStateOf(false) }
    val toggleSaved: (CommunityPlan) -> Unit = { plan ->
        savedPlans?.let { store ->
            store.toggleSaved(plan.id)
            savedPlanIds = store.savedIds()
            savesVersion++
        }
    }

    LaunchedEffect(savedPlanIds, savesVersion, tab) {
        if (tab != ProfileTab.Favorites) return@LaunchedEffect
        val wanted = savedPlanIds
        if (wanted.isEmpty()) {
            savedPlanRows = emptyList()
            return@LaunchedEffect
        }
        savedLoading = savedPlanRows.isEmpty()
        val rows = wanted.take(20).mapNotNull { id -> repository.planById(id) }
        if (rows.isNotEmpty()) savedPlanRows = rows
        savedLoading = false
    }

    // 一次把两样都问完再画：先画骨架再补数字，这一页会闪两下，和消息页同一个理由。
    //
    // 已经画着东西时不点 loading：这一次刷新是后台补丁，不是重开一页。`loading` 的初值
    // （`cached == null`）已经把「第一次进来什么都没有」那一格管住了。
    LaunchedEffect(owner, viewer) {
        problem = null
        val fetched = social.userProfile(owner, viewer)
        val uploaded = repository.plansByOwner(owner)
        // 失败时**不覆盖**屏幕上这一份：`userProfile` / `plansByOwner` 拉挂了给的是 null 与空列表，
        // 照着它画就等于因为一次断网把人看过的主页清空，人还得再等一次往返才看得回来。
        // 看的是那两位错误标志，而不是返回值——返回空本来就是"他真的没有作品"。
        val failed = social.lastError != null ||
            repository.lastError != null ||
            social.lastFailureWasNetwork
        if (!failed) {
            val nextFollowing = fetched?.iFollow == true
            profile = fetched
            plans = uploaded
            following = nextFollowing
            cacheKey?.let { key ->
                ScreenCache.put(key, UserProfileSnapshot(fetched, uploaded, nextFollowing))
            }
        }
        problem = when {
            social.lastError != null -> "后端没有返回完整资料：${social.lastError}"
            repository.lastError != null -> "后端没有返回作品：${repository.lastError}"
            // 网络那一侧没有 error 文案，只有这一位布尔值说得清是不是请求没出去。
            social.lastFailureWasNetwork -> "连不上后端，检查代理后再试"
            else -> null
        }
        loading = false
    }

    val shown = profile
    val displayName = (shown?.name ?: "").ifBlank { owner }.ifBlank { "未知用户" }
    // 列表拉失败时退回头像那一行给的数目：后端说他有几个方案，页签就写几个。
    val planCount = if (plans.isNotEmpty()) plans.size else (shown?.plans ?: 0)

    Box(modifier = Modifier.fillMaxSize().background(scheme.surfaceContainerLowest)) {
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                // 作品与收藏都是**方案卡**，一条一行：和「安全规则」页那份方案列表同一张卡
                // （`PlanCard`），所以同一条方案在三个地方读起来是同一个东西。
                columns = GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(GridGap),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 封面、数字、简介、按钮、页签全是这一格：它们横跨三列，跟着同一只手一起滚。
                item(key = "profile-head", span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(scheme.surfaceContainerLowest),
                    ) {
                        ProfileCover(
                            name = displayName,
                            owner = owner,
                            onBack = onNavigateBack,
                            onSearch = { onMessage("搜索还没做") },
                            onMore = { onMessage("更多还没做") },
                        )
                        Spacer(Modifier.height(CoverGap))
                        ProfileStats(
                            votes = shown?.votes ?: 0,
                            following = shown?.following ?: 0,
                            followers = shown?.followers ?: 0,
                            onOpenFollowing = { onOpenFollows(false) },
                            onOpenFollowers = { onOpenFollows(true) },
                        )
                        Spacer(Modifier.height(10.dp))
                        ProfileBio(
                            bio = shown?.bio.orEmpty(),
                            editable = viewer == owner && viewer.isNotEmpty(),
                            problem = problem,
                            onEdit = {
                                bioDraft = profile?.bio.orEmpty()
                                showBioEditor = true
                            },
                            // 用 [shown] 而不是 [profile]：改简介时本地那份会先变，属地跟着它一起走就好。
                            ipRegion = shown?.ipRegion.orEmpty(),
                        )
                        Spacer(Modifier.height(2.dp))
                        ProfileActionRow(
                            following = following,
                            busy = busy,
                            onToggleFollow = {
                                when {
                                    busy -> Unit
                                    viewer.isBlank() -> onMessage("还没有身份，先登录再来关注")
                                    else -> {
                                        val want = !following
                                        scope.launch {
                                            busy = true
                                            val next = social.setFollow(viewer, owner, want)
                                            // false 既可能是「写失败」也可能是「本来就没关注」，
                                            // 所以除了返回值还要看那两位错误标志。
                                            val refused = social.lastError != null ||
                                                social.lastFailureWasNetwork ||
                                                next != want
                                            busy = false
                                            if (refused) {
                                                onMessage("操作失败，检查代理后再试")
                                            } else {
                                                following = next
                                                // 快照跟上：否则下次进来先画的是点关注之前那一版，
                                                // 按钮写着「+ 关注」而人明明刚点过。
                                                cacheKey?.let { key ->
                                                    ScreenCache.put(
                                                        key,
                                                        UserProfileSnapshot(profile, plans, next),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onOpenChat = { onOpenChat(owner) },
                            // 自己都还没有身份，就没有"谁在分享"这件事，按钮不出现。
                            onShare = if (viewer.isNotBlank()) {
                                { sharing = true }
                            } else {
                                null
                            },
                        )
                        ProfileTabs(
                            planCount = planCount,
                            savedCount = savedPlanIds.size,
                            selected = tab,
                            onSelect = { tab = it },
                        )
                        // 名片分享的会话选择：Dialog 不占布局，放在这一列里也不会把下面顶开。
                        if (sharing) {
                            PickConversationDialog(
                                repository = social,
                                owner = viewer,
                                title = "分享名片",
                                preview = profile?.name?.ifBlank { "#$owner" } ?: "#$owner",
                                doneHint = "已发送",
                                onPick = { conversation ->
                                    social.sendCard(viewer, conversation.id, owner) != null
                                },
                                onDismiss = { sharing = false },
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }

                when (tab) {
                    ProfileTab.Plans -> {
                        if (plans.isEmpty()) {
                            item(key = "empty-plans", span = { GridItemSpan(maxLineSpan) }) {
                                ProfileEmptyNote(text = "还没有上传过方案")
                            }
                        } else {
                            items(
                                items = plans,
                                key = { plan -> "plan-${plan.id}" },
                                span = { GridItemSpan(maxLineSpan) },
                            ) { plan ->
                                PlanCard(
                                    plan = plan,
                                    // 别人的主页：删不了、也不用在这里投票——票属于那份方案自己的页面。
                                    mine = false,
                                    foreign = false,
                                    saved = plan.id in savedPlanIds,
                                    onOpen = { onOpenPlan(plan.id) },
                                    onToggleSave = { toggleSaved(plan) },
                                )
                            }
                        }
                    }
                    // 收藏只在本机：编号拿得到、方案本身要从后端逐条问回（见上面那个 effect）。
                    // 问不回来的那几条不画——收藏一个已经删掉的方案，画出来也点不开。
                    ProfileTab.Favorites -> {
                        val rows = savedPlanRows.filter { it.id in savedPlanIds }
                        when {
                            savedPlanIds.isEmpty() -> item(
                                key = "empty-favorites",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                ProfileEmptyNote(text = "还没有收藏的方案：在方案卡右上点一下星")
                            }
                            rows.isEmpty() -> item(
                                key = "loading-favorites",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                ProfileEmptyNote(
                                    text = if (savedLoading) "正在读收藏的方案…" else "收藏的方案暂时读不到",
                                )
                            }
                            else -> items(
                                items = rows,
                                key = { plan -> "saved-${plan.id}" },
                                span = { GridItemSpan(maxLineSpan) },
                            ) { plan ->
                                PlanCard(
                                    plan = plan,
                                    mine = false,
                                    foreign = false,
                                    saved = true,
                                    onOpen = { onOpenPlan(plan.id) },
                                    onToggleSave = { toggleSaved(plan) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 改简介的对话框：笔一点开它，草稿预填当前简介，保存走 social.setBio。
    // 保存中的按钮置灰、字换成「保存中」，防一只手连点出两笔请求。
    if (showBioEditor) {
        CantaDialog(
            onDismissRequest = { if (!savingBio) showBioEditor = false },
            title = "编辑简介",
            buttons = {
                TextButton(onClick = { showBioEditor = false }) { Text("取消") }
                TextButton(
                    enabled = !savingBio,
                    onClick = {
                        val next = bioDraft.trim()
                        scope.launch {
                            savingBio = true
                            val ok = social.setBio(owner, next)
                            savingBio = false
                            if (ok) {
                                profile = profile?.copy(bio = next)
                                showBioEditor = false
                                // 简介改了也要进快照：否则从方案页退回来，先画出来的是改之前那句。
                                cacheKey?.let { key ->
                                    ScreenCache.put(
                                        key,
                                        UserProfileSnapshot(profile, plans, following),
                                    )
                                }
                                onMessage("简介已更新")
                            } else {
                                onMessage("没保存成，检查代理后再试")
                            }
                        }
                    },
                ) {
                    Text(if (savingBio) "保存中" else "保存")
                }
            },
        ) {
            BasicTextField(
                value = bioDraft,
                onValueChange = { bioDraft = it.take(200) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                textStyle = TextStyle(fontSize = 14.sp, color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                minLines = 3,
                decorationBox = { inner ->
                    if (bioDraft.isEmpty()) {
                        Text(
                            text = "介绍一下自己（可不填）",
                            fontSize = 14.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
    }
}

/**
 * 封面：主色渐变 + 一层压暗，左上角返回、右上角搜索与更多，底部压着头像与昵称。
 *
 * 没有图片资源，所以「背景图」就是这两层色块：底下那一层是主色到 75% 主色，上面再叠一层
 * 从透明到底部 35% 黑，白字压在下半截也看得清。
 */
@Composable
private fun ProfileCover(
    name: String,
    owner: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth().height(CoverHeight)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(scheme.primary, scheme.primary.copy(alpha = 0.75f)),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBarButton(
                glyph = FlGlyphs.chevronBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            GlassBarButton(
                glyph = FlGlyphs.search,
                contentDescription = "搜索",
                onClick = onSearch,
            )
            Spacer(Modifier.width(8.dp))
            GlassBarButton(
                glyph = FlGlyphs.more,
                contentDescription = "更多",
                onClick = onMore,
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像整个往下挪半截：上半截在封面上，下半截压进下面的白底。
            Box(modifier = Modifier.offset(y = AvatarHang)) {
                HeroAvatar(name = name, owner = owner)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 没改过名的人：名字显示成占位条 + 红点（见 AuthorLabel），而不是 `#1000x`。
                AuthorLabel(
                    name = name,
                    style = FlText.headline,
                    color = Color.White,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "ID: $owner",
                    style = FlText.footnote,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 封面上那一个半透明圆钮：黑 24% 的圆底，白图标，和消息页的圆钮同一个尺寸语感。 */
@Composable
private fun GlassBarButton(glyph: FlGlyph, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(GlassButtonSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.24f))
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FlGlyphIcon(glyph = glyph, size = 18.dp, tint = Color.White)
    }
}

/**
 * 大头像：白色描边圈里放**照片**；没设过头像（或者图还没下下来）就是名字首字。
 *
 * 照片按身份号从图床取（见 [PersonAvatar]）：设过头像的人显示照片，没设过的人落回首字圆，
 * 两态由组件自己决定，这一页不必关心"他到底传没传"。
 */
@Composable
private fun HeroAvatar(name: String, owner: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(Color.White)
            .padding(AvatarRing),
        contentAlignment = Alignment.Center,
    ) {
        PersonAvatar(
            name = name,
            owner = owner,
            size = AvatarSize - AvatarRing * 2,
            // 首字圆沿用原来那个主色底：图没到的那一帧不能是一块灰。
            background = scheme.primary.copy(alpha = 0.9f),
            letterColor = Color.White,
            letterStyle = FlText.title1,
        )
    }
}

/** 一行三个数字：等宽三列，数字在上、灰标签在下；后两格能点，进那一栏的名单。 */
@Composable
private fun ProfileStats(
    votes: Int,
    following: Int,
    followers: Int,
    onOpenFollowing: () -> Unit,
    onOpenFollowers: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        ProfileStat(value = votes, label = "获赞", modifier = Modifier.weight(1f))
        ProfileStat(
            value = following,
            label = "关注",
            modifier = Modifier.weight(1f).clickable(onClick = onOpenFollowing),
        )
        ProfileStat(
            value = followers,
            label = "粉丝",
            modifier = Modifier.weight(1f).clickable(onClick = onOpenFollowers),
        )
    }
}

@Composable
private fun ProfileStat(value: Int, label: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            style = FlText.headline,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 简介区：本人写过的那句话，或「这个人很懒」的灰字占位，下面跟着「IP：某省」小灰标。
 *
 * bio 与占位二选一：有简介就放开多行写，颜色用 onSurface；没有时保持原来的灰字。
 * [editable] 表示看主页的人就是本人：那一行右侧放一支笔，点开改简介；别人看时只有字。
 *
 * [ipRegion] 是后端算好的省/州名，还没查出来就是空串，那时照旧显示「未知」——这一行从来不空着，
 * 空着会让人以为界面坏了。
 */
@Composable
private fun ProfileBio(
    bio: String,
    editable: Boolean,
    problem: String?,
    onEdit: () -> Unit,
    /**
     * 上一次从哪来，后端算好的省/州名。空串表示后端还没查出来——那台机器可能是在一个
     * 拿不到真实出口 IP 的网络里装的，或者刚装上还没轮到 cron 那一分钟。
     */
    ipRegion: String = "",
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            if (bio.isNotBlank()) {
                Text(
                    text = bio,
                    style = FlText.subheadline,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = "这个人很懒，什么都没写",
                    style = FlText.footnote,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (editable) {
                Spacer(Modifier.width(4.dp))
                FlBarButton(
                    glyph = FlGlyphs.edit,
                    contentDescription = "编辑简介",
                    onClick = onEdit,
                    iconSize = 18.dp,
                    filled = false,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            // 模板里别把中文直接拼在 `$` 后面：Kotlin 会把紧跟着的汉字当成标识符的一部分
            // （`"$ipRegion未知"` 会被读成变量 `ipRegion未知`），所以这里用 `${}` 括起来。
            text = "IP：${ipRegion.ifBlank { "未知" }}",
            style = FlText.caption2,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(scheme.surfaceContainerHighest)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        if (problem != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = problem,
                style = FlText.caption2,
                color = scheme.outline,
            )
        }
    }
}

/**
 * 关注 + 私信：左边整宽的关注按钮（红底白字，已关注换灰底描边），右边一个方形的私信钮。
 *
 * [busy] 是上一条关注请求还没回来：这时按钮不重复发请求，只把字换成「处理中」。
 */
@Composable
private fun ProfileActionRow(
    following: Boolean,
    busy: Boolean,
    onToggleFollow: () -> Unit,
    onOpenChat: () -> Unit,
    /** 把这个人的名片发给谁；null 表示这一处不提供（比如自己都还没有身份）。 */
    onShare: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(FollowHeight)
                .clip(ActionShape)
                .then(
                    if (following) {
                        Modifier
                            .background(scheme.surfaceContainerHigh)
                            .border(1.dp, scheme.outlineVariant, ActionShape)
                    } else {
                        Modifier.background(UnreadRed)
                    },
                )
                .clickable(onClick = onToggleFollow),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    busy -> "处理中"
                    following -> "已关注"
                    else -> "+ 关注"
                },
                style = FlText.callout,
                fontWeight = FontWeight.Bold,
                color = if (following) scheme.onSurfaceVariant else Color.White,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .width(MessageButtonWidth)
                .height(FollowHeight)
                .clip(ActionShape)
                .background(scheme.surfaceContainerHigh)
                .clickable(onClick = onOpenChat),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(glyph = FlGlyphs.send, size = 20.dp, tint = scheme.onSurface)
        }
        // 名片：和私信钮同形，放在它右边。图标用「链接」，因为它做的事就是把人递出去。
        if (onShare != null) {
            Box(
                modifier = Modifier
                    .width(MessageButtonWidth)
                    .height(FollowHeight)
                    .clip(ActionShape)
                    .background(scheme.surfaceContainerHigh)
                    .clickable(onClick = onShare),
                contentAlignment = Alignment.Center,
            ) {
                FlGlyphIcon(glyph = FlGlyphs.link, size = 20.dp, tint = scheme.onSurface)
            }
        }
    }
}

/** 两栏页签：「作品 N ▾」与「收藏 N」；选中的那一栏加粗，下面压一条红下划线。 */
@Composable
private fun ProfileTabs(
    planCount: Int,
    savedCount: Int,
    selected: ProfileTab,
    onSelect: (ProfileTab) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        ProfileTabCell(
            label = "作品 $planCount ▾",
            selected = selected == ProfileTab.Plans,
            onClick = { onSelect(ProfileTab.Plans) },
            modifier = Modifier.weight(1f),
        )
        ProfileTabCell(
            label = if (savedCount > 0) "收藏 $savedCount" else "收藏",
            selected = selected == ProfileTab.Favorites,
            onClick = { onSelect(ProfileTab.Favorites) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ProfileTabCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = FlText.subheadline,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        // 没选中也占着这条位置，免得选中时整行跳一下。
        Box(
            modifier = Modifier
                .width(TabUnderlineWidth)
                .height(2.dp)
                .background(
                    color = if (selected) UnreadRed else Color.Transparent,
                    shape = RoundedCornerShape(1.dp),
                ),
        )
    }
}

/** 空态：整行居中一句灰字。 */
@Composable
private fun ProfileEmptyNote(text: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = FlText.footnote,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
