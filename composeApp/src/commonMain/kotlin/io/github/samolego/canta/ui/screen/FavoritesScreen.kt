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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.CommunityPost
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.community.PlanSavedStore
import io.github.samolego.canta.data.community.PostReadStore
import io.github.samolego.canta.data.community.PostStore
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf

/** 一次从后端问回几条：收藏里可能存着两百条，逐个 RPC 打过去不合适，也打不完。 */
private const val FavoritesPage = 20

/**
 * 收藏页：两栏——「方案」与「帖子」。
 *
 * 分开两栏而不是混排，是因为两者的条目本来就长得不一样（方案卡有票数与包数，帖子卡有标题与回复数），
 * 混在一起读不出秩序；两栏各一张卡，靠卡上的「方案」/「帖子」标签一眼分清。
 *
 * 收藏**只在本机**（[PlanSavedStore] / [PostReadStore]），这一页只拿着编号去后端把内容问回来：
 * 被作者删掉的方案、被删掉的帖子在这里自然不出现，读不到的那几条也不会画成一条死行。
 */
@Composable
fun FavoritesScreen(
    repository: PlanRepository,
    /** 帖子那一侧的数据层；null 时「帖子」栏只说"连不上。 */
    posts: PostStore?,
    savedPlans: PlanSavedStore,
    postReads: PostReadStore,
    onOpenPlan: (Long) -> Unit,
    onOpenPost: (Long) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var tab by remember { mutableIntStateOf(0) }
    var planIds by remember { mutableStateOf(savedPlans.savedIds()) }
    var postIds by remember { mutableStateOf(postReads.savedIds()) }
    var planRows by remember { mutableStateOf<List<CommunityPlan>>(emptyList()) }
    var postRows by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var planLoading by remember { mutableStateOf(true) }
    var postLoading by remember { mutableStateOf(true) }

    // 方案：编号变了就重问这一批（收藏 / 取消收藏都走这里）。
    LaunchedEffect(planIds) {
        if (planIds.isEmpty()) {
            planRows = emptyList()
            planLoading = false
        } else {
            planLoading = planRows.isEmpty()
            planRows = planIds.take(FavoritesPage).mapNotNull { id -> repository.planById(id) }
            planLoading = false
        }
    }
    LaunchedEffect(postIds) {
        val store = posts
        if (store == null || postIds.isEmpty()) {
            postRows = emptyList()
            postLoading = false
        } else {
            postLoading = postRows.isEmpty()
            postRows = postIds.take(FavoritesPage).mapNotNull { id -> store.get(id)?.post }
            postLoading = false
        }
    }

    var notice by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = "收藏",
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(scheme.surfaceContainerLowest),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FavoritesPill(
                    label = if (planIds.isEmpty()) "方案" else "方案 ${planIds.size}",
                    selected = tab == 0,
                    onClick = { tab = 0 },
                )
                FavoritesPill(
                    label = if (postIds.isEmpty()) "帖子" else "帖子 ${postIds.size}",
                    selected = tab == 1,
                    onClick = { tab = 1 },
                )
            }
            notice?.let { line ->
                Text(
                    text = line,
                    style = FlText.caption2,
                    color = scheme.outline,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                )
            }
            when (tab) {
                0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        planIds.isEmpty() -> item("plans-empty") {
                            FavoritesEmpty("还没有收藏的方案：在方案卡右上点一下星")
                        }
                        planRows.isEmpty() && planLoading -> item("plans-loading") {
                            FavoritesEmpty("正在读收藏的方案…")
                        }
                        planRows.isEmpty() -> item("plans-unreadable") {
                            FavoritesEmpty("收藏的方案暂时读不到：检查一下网络再来")
                        }
                        else -> {
                            items(planRows, key = { plan -> "fav-plan-${plan.id}" }) { plan ->
                                PlanCard(
                                    plan = plan,
                                    mine = false,
                                    foreign = false,
                                    saved = true,
                                    onOpen = { onOpenPlan(plan.id) },
                                    onToggleSave = {
                                        savedPlans.toggleSaved(plan.id)
                                        planIds = savedPlans.savedIds()
                                    },
                                )
                            }
                            if (planIds.size > planRows.size) {
                                item("plans-more") {
                                    FavoritesEmpty(
                                        "还有 ${planIds.size - planRows.size} 条收藏没在这一屏：收藏多的时候这一页只读最近 $FavoritesPage 条。",
                                    )
                                }
                            }
                        }
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        posts == null -> item("posts-offline") {
                            FavoritesEmpty("这一版没有接上帖子后端，收藏的帖子暂时打不开")
                        }
                        postIds.isEmpty() -> item("posts-empty") {
                            FavoritesEmpty("还没有收藏的帖子：在帖子卡片右下角点一下星")
                        }
                        postRows.isEmpty() && postLoading -> item("posts-loading") {
                            FavoritesEmpty("正在读收藏的帖子…")
                        }
                        postRows.isEmpty() -> item("posts-unreadable") {
                            FavoritesEmpty("收藏的帖子暂时读不到：可能被删了，或者网络不通")
                        }
                        else -> {
                            items(postRows, key = { post -> "fav-post-${post.id}" }) { post ->
                                FavoritePostCard(
                                    post = post,
                                    onOpen = { onOpenPost(post.id) },
                                    onToggleSave = {
                                        postReads.toggleSaved(post.id)
                                        postIds = postReads.savedIds()
                                        notice = "已取消收藏这条帖子"
                                    },
                                )
                            }
                            if (postIds.size > postRows.size) {
                                item("posts-more") {
                                    FavoritesEmpty(
                                        "还有 ${postIds.size - postRows.size} 条收藏没在这一屏：收藏多的时候这一页只读最近 $FavoritesPage 条。",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 一栏的选择：选中填主色，未选中是抬起一层的底色。 */
@Composable
private fun FavoritesPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        style = FlText.caption1.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
        color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) scheme.primary else FlSurfaceLevel.Page.raised.colorOf(scheme))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** 空态那一行：整宽一句灰字。 */
@Composable
private fun FavoritesEmpty(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = FlText.footnote,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 收藏里的一条帖子：一张自己的小卡，因为帖子卡的样式在 `PostsSection` 里是私有的，
 * 而这里只需要「标题 + 摘要 + 回复数 + 取消收藏」这几样。
 */
@Composable
private fun FavoritePostCard(post: CommunityPost, onOpen: () -> Unit, onToggleSave: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FlSurfaceLevel.Page.raised.colorOf(scheme))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "帖子",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(scheme.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = post.title.ifBlank { "（无标题）" },
                    style = FlText.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (post.excerpt.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = post.excerpt,
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("${post.comments} 回复 · ${post.likes} 赞")
                    if (post.author.isNotBlank()) append(" · @${post.author}")
                },
                style = FlText.caption2,
                color = scheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onToggleSave),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = "取消收藏",
                modifier = Modifier.size(16.dp),
                tint = scheme.primary,
            )
        }
    }
}
