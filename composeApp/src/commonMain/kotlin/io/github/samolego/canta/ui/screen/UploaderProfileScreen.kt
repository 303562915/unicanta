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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import io.github.samolego.canta.data.cache.ScreenCache
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.ui.component.LetterAvatar
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.fl.FlText

/**
 * 这一页的一屏快照：只有那份方案名单。
 *
 * 为什么要有它：这一页是从方案卡片点进来的，读完返回、再点同一个人，原本又要从空白转圈重拉一遍。
 * 名单是后端已经给过的东西，进页面先把上一份摆上、网络回来再替换，看起来就是「立刻打开」。
 *
 * 只放画一屏需要的只读数据（[CommunityPlan] 是不可变 data class）；不存 MutableState、协程、回调。
 * 名字不单独存：它就是从这份名单里取出来的（[UploaderProfileScreen] 里那行 `firstNotNullOfOrNull`）。
 */
private data class UploaderProfileSnapshot(val plans: List<CommunityPlan>)

/** 键带上身份：不同上传者不能共用一格，否则点开 B 会先闪一下 A 的名单。身份为空时不读不写。 */
private fun uploaderCacheKey(owner: String): String? =
    if (owner.isEmpty()) null else "screen:uploader:$owner"

/**
 * One uploader, and everything they put up.
 *
 * The plan card answers "who wrote this" in a line; this page answers "should I trust their other
 * plans too", which is the question a reader has after one of them worked. It shows what the
 * backend actually keeps — the plans, how many people endorsed each, how many talked about it — and
 * nothing it would have to invent, such as a reply rate.
 *
 * @param owner the identity number whose plans to list.
 * @param onOpenPlan opens one plan's own page.
 */
@Composable
fun UploaderProfileScreen(
    owner: String,
    repository: PlanRepository,
    onOpenPlan: (Long) -> Unit,
    onNavigateBack: () -> Unit,
) {
    // 上一屏留下的名单：有就先画它（`plans` 不是 null，就不会走下面那个转圈），没有才等网络。
    val cacheKey = uploaderCacheKey(owner)
    val cached = remember(owner) { cacheKey?.let { key -> ScreenCache.get<UploaderProfileSnapshot>(key) } }
    var plans by remember(owner) { mutableStateOf(cached?.plans) }

    LaunchedEffect(owner) {
        val fetched = repository.plansByOwner(owner)
        // 后端答了才替换屏幕上的那一份：`plansByOwner` 拉挂了给的也是空列表，照着画就等于因为
        // 一次断网把看过的名单清空。没有缓存时仍然照旧退成空列表，下面那句「或者后端连不上」就是为它写的。
        if (repository.lastError == null && !repository.lastFailureWasNetwork) {
            plans = fetched
            cacheKey?.let { key -> ScreenCache.put(key, UploaderProfileSnapshot(fetched)) }
        } else if (plans == null) {
            plans = fetched
        }
    }

    // The name the backend put on their newest plan; an identity that never picked one is shown by
    // its number, the same way the plan card falls back.
    val name = plans?.firstNotNullOfOrNull { it.uploader.ifEmpty { null } }
        ?: if (owner.isNotEmpty()) "#$owner" else ""

    Scaffold(
        topBar = {
            ScreenTopBar(
                onNavigateBack = onNavigateBack,
                title = "上传者",
            )
        },
    ) { padding ->
        val loaded = plans
        if (loaded == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 13.dp, end = 13.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "header") { UploaderHeader(name = name, owner = owner, plans = loaded) }

            if (loaded.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "这个人还没有公开的方案，或者后端连不上。",
                        style = FlText.caption1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }

            items(loaded, key = { it.id }) { plan ->
                UploaderPlanRow(plan = plan, onClick = { onOpenPlan(plan.id) })
            }
        }
    }
}

/** Who this is: the avatar, the name, and the numbers their plans add up to. */
@Composable
private fun UploaderHeader(name: String, owner: String, plans: List<CommunityPlan>) {
    val scheme = MaterialTheme.colorScheme
    val votes = plans.sumOf { it.votes }
    val comments = plans.sumOf { it.comments }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = scheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LetterAvatar(name = name, size = 34.dp, background = scheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (owner.isNotEmpty()) {
                        Text(
                            text = "#$owner",
                            fontSize = 10.sp,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Three numbers, all of them counted from the plans below: nothing here is a rate or an
            // average, because a rate over four plans would say more about the sample than the person.
            Row(modifier = Modifier.fillMaxWidth()) {
                HeaderStat(value = "${plans.size}", label = "方案", modifier = Modifier.weight(1f))
                HeaderStat(value = "$votes", label = "赞", modifier = Modifier.weight(1f))
                HeaderStat(value = "$comments", label = "评论", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeaderStat(value: String, label: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onPrimaryContainer,
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
    }
}

/** One of their plans, as a row: what it is, which phone it was made on, how it did. */
@Composable
private fun UploaderPlanRow(plan: CommunityPlan, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = plan.title,
                style = FlText.subheadline,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(
                    plan.deviceLabel.ifBlank { "机型未知" },
                    if (plan.isTest) "未验证" else "已验过重启",
                ).joinToString(" · "),
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = "${plan.packages.size} 个包 · ${plan.votes} 赞 · ${plan.comments} 评论",
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}