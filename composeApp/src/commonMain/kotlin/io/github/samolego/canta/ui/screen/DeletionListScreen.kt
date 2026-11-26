package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.app.DeletionListImport
import io.github.samolego.canta.data.app.DeletionListStore
import io.github.samolego.canta.data.app.DeletionPlan
import io.github.samolego.canta.data.app.LOCAL_PLAN_ID
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.ui.component.AppCardCorner
import io.github.samolego.canta.ui.component.AppCardInset
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.component.SettingsChevron
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * 「卸载记录」页：所有能整份装回来的**方案**。
 *
 * 一条方案 = 一份名单：最上面那份是**本机的卸载记录**（删了还没装回来的包，实时算），
 * 下面是**导入进来的**每一份（导一次就留一份，可以并存）。点一条进去看它包含哪些包，
 * 在里面既可以逐个装回，也可以整份装回。
 *
 * 导入放在右上角，因为它是"文件进来"，和页面内容是并列的动作，不该占着列表的位置。
 */
@Composable
fun DeletionListScreen(
    deletionList: DeletionListStore,
    platform: CantaPlatform,
    onNavigateBack: () -> Unit,
    /** 装回一个包，返回成功的条数。 */
    onRestore: suspend (String) -> Int = { 0 },
) {
    val importedPlans by deletionList.importedPlans.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var local by remember { mutableStateOf<DeletionPlan?>(null) }
    var openPlan by remember { mutableStateOf<DeletionPlan?>(null) }
    var pendingImport by remember { mutableStateOf<DeletionListImport?>(null) }
    var lastExport by remember { mutableStateOf<String?>(null) }

    suspend fun reload() { local = deletionList.localPlan() }
    LaunchedEffect(Unit) {
        deletionList.ensureReady()
        reload()
    }

    // 点开的那一条也要跟着记录刷新：装回一个之后，它这一份就少一条。
    LaunchedEffect(openPlan?.id) {
        openPlan?.let { opened ->
            openPlan = if (opened.id == LOCAL_PLAN_ID) {
                deletionList.localPlan()
            } else {
                deletionList.plan(opened.id)
            }
        }
    }

    openPlan?.let { plan ->
        DeletionPlanDetail(
            plan = plan,
            onBack = { openPlan = null },
            onRestoreOne = { pkg ->
                scope.launch {
                    val done = onRestore(pkg)
                    openPlan = if (plan.id == LOCAL_PLAN_ID) {
                        deletionList.localPlan()
                    } else {
                        deletionList.plan(plan.id)
                    }
                    platform.showMessage(if (done > 0) "已装回 $pkg" else "没能装回 $pkg")
                }
            },
            onRestoreAll = {
                scope.launch {
                    val targets = plan.packages
                    var done = 0
                    targets.forEach { pkg -> done += onRestore(pkg) }
                    openPlan = if (plan.id == LOCAL_PLAN_ID) {
                        deletionList.localPlan()
                    } else {
                        deletionList.plan(plan.id)
                    }
                    platform.showMessage("整份恢复：成功 $done / ${targets.size}")
                }
            },
            onExport = {
                scope.launch {
                    val exported = deletionList.savePlanAs(plan)
                    if (exported.path != null) {
                        lastExport = exported.path
                        platform.showMessage("已保存到 ${exported.path}")
                    }
                }
            },
        )
        return
    }

    val plans = buildList {
        local?.let { add(it) }
        addAll(importedPlans)
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = "卸载记录",
                onNavigateBack = onNavigateBack,
                actions = {
                    // 导入在右上角：它是"把别处那份带进来"，不是列表本身的一部分。
                    FlBarButton(
                        glyph = FlGlyphs.importFile,
                        contentDescription = "导入方案",
                        onClick = {
                            scope.launch {
                                val text = platform.pickTextFile()
                                if (text == null) return@launch
                                val parsed = deletionList.parse(text)
                                if (parsed == null) {
                                    platform.showMessage("这份文件读不懂：要的是导出时那份 JSON")
                                } else {
                                    pendingImport = parsed
                                }
                            }
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 28.dp),
        ) {
            Text(
                text = "点一份方案看它删了哪些包；每行右边的 ↑ 直接导出这一份",
                style = FlText.caption2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppCardInset + 2.dp, vertical = 4.dp),
            )
            plans.forEach { plan ->
                PlanRow(
                    plan = plan,
                    onClick = { openPlan = plan },
                    // 每一行自己带一个导出：列表页就能直接导，不用先点进去。
                    onExport = {
                        scope.launch {
                            val exported = deletionList.savePlanAs(plan)
                            if (exported.path != null) {
                                lastExport = exported.path
                                platform.showMessage("已保存到 ${exported.path}")
                            } else {
                                platform.showMessage("已取消，没有导出任何文件")
                            }
                        }
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
            if (plans.all { it.packages.isEmpty() }) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "还没有卸载过应用。卸载之后它的包会出现在「本机卸载记录」里。",
                    style = FlText.footnote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppCardInset + 10.dp),
                )
            }
            lastExport?.let { where ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "上次导出：$where",
                    style = FlText.caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = AppCardInset + 2.dp),
                )
            }
        }
    }

    pendingImport?.let { parsed ->
        ImportConfirmDialog(
            parsed = parsed,
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                scope.launch {
                    val plan = deletionList.importAsPlan(parsed)
                    platform.showMessage(
                        buildString {
                            append("已导入「${plan.name}」：${plan.packages.size} 个包")
                            if (parsed.rejected > 0) append("，丢掉 ${parsed.rejected} 条看不懂的")
                        }
                    )
                }
            },
        )
    }
}

/** 一份方案：名字、有多少条、从哪来；点进去看包，右边的 ↑ 直接导出这一份。 */
@Composable
private fun PlanRow(plan: DeletionPlan, onClick: () -> Unit, onExport: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            FlGlyphIcon(
                glyph = if (plan.origin == DeletionPlan.Origin.LOCAL) FlGlyphs.appsList else FlGlyphs.importFile,
                size = 18.dp,
                tint = scheme.primary,
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plan.name,
                style = FlText.subheadline,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append("${plan.packages.size} 个包")
                    if (plan.origin == DeletionPlan.Origin.IMPORTED) append(" · 导入")
                },
                style = FlText.caption2,
                color = scheme.onSurfaceVariant,
            )
        }
        // 导出按钮单独一块点击区：点它只导出，不会顺带把这一行当成"点进去"。
        // 写成带文字的胶囊而不是一个光秃秃的小图标：只画图标时人根本认不出这里是导出。
        Text(
            text = "导出",
            style = FlText.caption1,
            color = scheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.primary.copy(alpha = 0.12f))
                .clickable(onClick = onExport)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Spacer(Modifier.width(6.dp))
        SettingsChevron()
    }
}

/** 一份方案的详情：包列表，每条可点装回；顶部两个整份动作。 */
@Composable
private fun DeletionPlanDetail(
    plan: DeletionPlan,
    onBack: () -> Unit,
    onRestoreOne: (String) -> Unit,
    onRestoreAll: () -> Unit,
    onExport: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = plan.name,
                onNavigateBack = onBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 两个整份动作并排：左边恢复，右边导出。都写文字，不用图标猜。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppCardInset, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailAction(
                    label = "整份恢复",
                    enabled = plan.packages.isNotEmpty(),
                    onClick = onRestoreAll,
                    modifier = Modifier.weight(1f),
                )
                DetailAction(
                    label = "导出这份",
                    enabled = plan.packages.isNotEmpty(),
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                    secondary = true,
                )
            }
            if (plan.packages.isEmpty()) {
                Text(
                    text = "这一份是空的。",
                    style = FlText.footnote,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppCardInset + 10.dp, vertical = 20.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppCardInset,
                        end = AppCardInset,
                        top = 4.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(plan.packages, key = { it }) { pkg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppCardCorner))
                                .background(FlSurfaceLevel.Card.colorOf(scheme))
                                .clickable { onRestoreOne(pkg) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = pkg,
                                style = FlText.subheadline,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = "装回", style = FlText.caption2, color = scheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** 详情页顶部那两个整份动作之一。[secondary] 画成淡底主色字，用来区分主次。 */
@Composable
private fun DetailAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val background = when {
        !enabled -> scheme.surfaceContainerHigh
        secondary -> scheme.primary.copy(alpha = 0.12f)
        else -> scheme.primary
    }
    val foreground = when {
        !enabled -> scheme.onSurfaceVariant
        secondary -> scheme.primary
        else -> scheme.onPrimary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = FlText.subheadline,
            color = foreground,
        )
    }
}

/** 导入前先说清会加进来多少条、丢掉多少条。 */
@Composable
private fun ImportConfirmDialog(
    parsed: DeletionListImport,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "导入「${parsed.name}」",
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
            TextButton(onClick = onConfirm) { Text("导入") }
        },
    ) {
        Column {
            Text("这份文件有 ${parsed.accepted.size} 个包名，会存成一份新方案。")
            if (parsed.rejected > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "另有 ${parsed.rejected} 条不是合法包名，会被丢掉。",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}