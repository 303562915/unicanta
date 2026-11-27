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
import io.github.samolego.canta.data.cache.ScreenCache
import kotlinx.serialization.Serializable
import io.github.samolego.canta.data.community.CommunityPlan
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.util.DeviceProfile
import kotlinx.coroutines.launch

/**
 * 这一页的一屏快照：我上传过的方案。
 *
 * 为什么要有它：这一页从安全页的入口点进来，看完返回、再点一次，原本都要先看那一个「…」等一次
 * 网络往返。上一份留在进程里，进来先画出来，网络回来再替换，页面就是立刻的。
 *
 * 只放画一屏需要的只读数据（[CommunityPlan] 是不可变 data class）；不存 MutableState、协程、回调。
 */
@Serializable
private data class MyPlansSnapshot(val plans: List<CommunityPlan>)

/**
 * 键带上身份：这台设备（这个身份）的方案不能和别人共用一格。身份为空时不读也不写——
 * 调用方的身份是异步拿到的，第一帧那个空串槽位会被所有人串用。
 */
private fun myPlansCacheKey(owner: String): String? =
    if (owner.isEmpty()) null else "screen:myplans:$owner"

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
    /**
     * This phone's profile — **保留在签名里，但这一页不再用它**。
     *
     * 「我上传过的方案」现在只查数据库（`plans_by_owner`）：那一条本来就精确、条数也少，
     * 不需要拿机型去全社区快照里再筛一遍（那样反而会把「换过 ROM 的自己」筛丢，
     * 见下面 `LaunchedEffect` 里那段注释）。留着参数是为了不动所有调用方。
     */
    @Suppress("UNUSED_PARAMETER")
    profile: DeviceProfile? = null,
    onOpenPlan: (Long) -> Unit = {},
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 上一次画过的这份列表：有就直接铺上，`loading` 从 false 开始，不再先闪那一个「…」。
    val cacheKey = myPlansCacheKey(owner)
    val cached = remember(owner) { cacheKey?.let { key -> ScreenCache.get<MyPlansSnapshot>(key) } }
    var myPlans by remember(owner) { mutableStateOf(cached?.plans.orEmpty()) }
    var loading by remember(owner) { mutableStateOf(cached == null) }
    var reloads by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<CommunityPlan?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(owner, token, reloads) {
        // 不点 loading：点刷新不该把已经画着的列表换成那个「…」，那是后台替换。
        // 「第一次进来什么都没有」由 `loading` 的初值（`cached == null`）管。
        loadError = null
        // **「我上传过的方案」只认数据库那一条路。**
        //
        // 以前这里是 `plansByOwner(owner).ifEmpty { plansFor(profile).filter { it.owner == owner } }`，
        // 那个兜底是错的、而且是 2026-11-27「点进我的方案是空的」的直接原因：
        // `plansFor` 现在**先读 GitHub 快照**并在命中时调 `markAnswered()` 把 `lastError` 清成 null，
        // 于是下面那句 `lastError == null` 判成「后端答了」，把快照里**按机型筛不出来**的空列表
        // 当成真答案写进 `myPlans` —— 把 `plansByOwner` 刚拿到的真实结果盖掉了。
        // （真我 Neo8 那次：库里 `plans_by_owner` 明明返回了 id=400，页面却是空的。）
        //
        // 这也是整份代码里唯一一处「我自己传的」列表：条数屈指可数，直接查库最准，
        // 不必也不能从全社区的快照里再筛一遍。快照那份本来就不该包含「谁传的」这种判断。
        val fetched = repository.plansByOwner(owner)
        // A backend that is missing the function, or a phone that is offline, must not read as
        // "you never uploaded anything": say which one it was.
        loadError = repository.lastError
        // 后端答了才替换：拉挂了这两个调用给的也是空列表，照着画就等于因为一次断网把
        // 「我上传过的方案」清空。没有缓存可留时才退成空列表（下面那句「你还没有上传过方案」照旧）。
        if (repository.lastError == null && !repository.lastFailureWasNetwork) {
            myPlans = fetched
            cacheKey?.let { key -> ScreenCache.put(key, MyPlansSnapshot(fetched)) }
        } else if (myPlans.isEmpty()) {
            myPlans = fetched
        }
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
                        if (ok) {
                            val remaining = myPlans.filterNot { it.id == plan.id }
                            myPlans = remaining
                            // 快照跟着删：否则退出再进来，先画出来的会是这条已经删掉的方案。
                            cacheKey?.let { key -> ScreenCache.put(key, MyPlansSnapshot(remaining)) }
                        }
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
            // 失败原因挪到顶上这一行：列表有缓存时会继续画着（不再走下面的空态），只写在空态里
            // 就等于断网时什么都不说。
            loadError?.let { reason ->
                Text(
                    text = "后端没有返回列表：$reason",
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
