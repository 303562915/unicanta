package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.util.DeviceProfile
import kotlinx.coroutines.launch

/**
 * 「我的方案」：这台设备上传过的方案，一行一个。
 *
 * 点一行进方案详情；右边的垃圾桶删掉它——删方案是把整件事都删掉（包列表、票、底下每一条评论），
 * 所以先问一句，问的对话框里把代价写清楚。
 *
 * 从前这一页还管两件事：「我写的评论」和「别人对我的互动」。两样都搬走了——评论和互动都在
 * 「消息 → 互动消息」里，一页看「我做过什么、别人对我做过什么」；这一页只剩「我上传过什么」，
 * 标题也就跟着改成了「我的方案」。
 *
 * 方案按身份取，不按手机取：换过一次系统也得还能删掉自己以前传的那份。老后端没有
 * `plans_by_owner` 时，退回这台手机上的列表。
 */
@Composable
fun MyPlansScreen(
    repository: PlanRepository,
    /** This install's identity number: what [PlanRepository.plansByOwner] asks by. */
    owner: String = "",
    /** This install's secret, needed to delete a plan and to ask as this identity. */
    token: String = "",
    /** This phone: what "my plans" falls back to when the deployment has no `plans_by_owner`. */
    profile: DeviceProfile? = null,
    onOpenPlan: (Long) -> Unit = {},
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var myPlans by remember { mutableStateOf<List<CommunityPlan>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloads by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<CommunityPlan?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(owner, token, reloads) {
        loading = true
        loadError = null
        myPlans = repository.plansByOwner(owner).ifEmpty {
            repository.plansFor(profile).filter { it.owner == owner }
        }
        // A backend that is missing the function, or a phone that is offline, must not read as
        // "you never uploaded anything": say which one it was.
        loadError = repository.lastError
        loading = false
    }

    // Deleting a plan is deleting the whole thing: its packages, its votes and every comment under
    // it. A one-line confirmation cannot say that, so this one says what goes.
    pendingDelete?.let { plan ->
        CantaDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除方案",
            buttons = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        val ok = repository.delete(plan.id, owner, token)
                        notice = if (ok) "已删除「${plan.title}」" else "删不掉：这个方案不是本机上传的，或者后端没通。"
                        if (ok) myPlans = myPlans.filterNot { it.id == plan.id }
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
        ) {
            Text(
                text = "「${plan.title}」和它下面的 ${plan.comments} 条评论会一起消失，别人的点赞也不算数了。",
                fontSize = 12.sp,
            )
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                onNavigateBack = onNavigateBack,
                title = "我的方案",
                actions = {
                    FlBarButton(
                        glyph = FlGlyphs.refresh,
                        contentDescription = "刷新",
                        onClick = { reloads++ },
                        iconSize = 20.dp,
                        filled = false,
                    )
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            notice?.let { line ->
                Text(
                    text = line,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
            when {
                loading -> Text("…", fontSize = 12.sp, modifier = Modifier.padding(14.dp))

                myPlans.isEmpty() -> Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                ) {
                    Text("你还没有上传过方案。", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "在安全页的悬浮按钮里上传一份，这里就能管理和删除。",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    loadError?.let { reason ->
                        Text(
                            text = "后端没有返回列表：$reason",
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(myPlans, key = { plan -> "plan-${plan.id}" }) { plan ->
                        MyPlanRow(
                            plan = plan,
                            onOpen = { onOpenPlan(plan.id) },
                            onDelete = { pendingDelete = plan },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One plan this install uploaded: tapping opens it, the bin deletes it.
 *
 * The row says what deleting costs — how many packages it publishes, how many people endorsed it,
 * whether it ever passed a reboot — so the confirmation is not the first time that is visible.
 */
@Composable
private fun MyPlanRow(plan: CommunityPlan, onOpen: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plan.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append("${plan.packages.size} 包 · ${plan.votes} 赞")
                    append(if (plan.isTest) " · 测试" else " · 重启验证")
                    append(" · ${plan.rebootedAt.take(10)}")
                },
                fontSize = 9.sp,
                lineHeight = 12.sp,
                color = colors.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除这个方案",
                modifier = Modifier.size(16.dp),
                tint = colors.error,
            )
        }
    }
}
