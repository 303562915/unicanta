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
    /** 正要备份的那一份；非空就弹「存到手机 / 分享出去」。 */
    var backupTarget by remember { mutableStateOf<DeletionPlan?>(null) }
    var lastBackup by remember { mutableStateOf<String?>(null) }

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
            onBackup = { backupTarget = plan },
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
                    // 恢复在右上角：它是"把别处那份带回来"，不是列表本身的一部分。
                    FlBarButton(
                        glyph = FlGlyphs.importFile,
                        contentDescription = "从备份恢复",
                        onClick = {
                            scope.launch {
                                val text = platform.pickTextFile()
                                if (text == null) return@launch
                                val parsed = deletionList.parse(text)
                                if (parsed == null) {
                                    platform.showMessage("这份文件读不懂：要选备份出来的那份 JSON")
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
                text = "点一份方案看它删了哪些包；每行右边的「备份」可以存到手机或分享出去",
                style = FlText.caption2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppCardInset + 2.dp, vertical = 4.dp),
            )
            plans.forEach { plan ->
                PlanRow(
                    plan = plan,
                    onClick = { openPlan = plan },
                    // 每一行自己带一个「备份」：点它是"存到哪 / 分享给谁"，不是直接写文件。
                    onBackup = { backupTarget = plan },
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
            lastBackup?.let { where ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "上次备份：$where",
                    style = FlText.caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = AppCardInset + 2.dp),
                )
            }
        }
    }

    // 备份去哪儿：存到手机（自己挑位置）还是分享出去。
    backupTarget?.let { plan ->
        BackupTargetDialog(
            planName = plan.name,
            onDismiss = { backupTarget = null },
            onSaveToDevice = {
                backupTarget = null
                scope.launch {
                    val exported = deletionList.savePlanAs(plan)
                    if (exported.path != null) {
                        lastBackup = exported.path
                        platform.showMessage("已备份到 ${exported.path}")
                    } else {
                        platform.showMessage("已取消，没有备份任何文件")
                    }
                }
            },
            onShare = {
                backupTarget = null
                scope.launch {
                    val shared = deletionList.sharePlanAs(plan)
                    platform.showMessage(
                        if (shared) "选一个应用把它发出去" else "这个版本没有分享面板，改成存到手机吧"
                    )
                }
            },
        )
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
                            append("已恢复「${plan.name}」：${plan.packages.size} 个包")
                            if (parsed.rejected > 0) append("，丢掉 ${parsed.rejected} 条看不懂的")
                        }
                    )
                }
            },
        )
    }
}

/** 一份方案：名字、有多少条、从哪来；点进去看包，右边的「备份」存位置或分享出去。 */
@Composable
private fun PlanRow(plan: DeletionPlan, onClick: () -> Unit, onBackup: () -> Unit) {
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
        // 备份按钮单独一块点击区：点它只问"备份去哪"，不会顺带把这一行当成"点进去"。
        // 写成带文字的胶囊而不是一个光秃秃的小图标：只画图标时人根本认不出这里是备份。
        Text(
            text = "备份",
            style = FlText.caption1,
            color = scheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.primary.copy(alpha = 0.12f))
                .clickable(onClick = onBackup)
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
    onBackup: () -> Unit,
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
            // 两个整份动作并排：左边恢复，右边备份。都写文字，不用图标猜。
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
                    label = "备份这份",
                    enabled = plan.packages.isNotEmpty(),
                    onClick = onBackup,
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

/** 恢复前先说清会加进来多少条、丢掉多少条。 */
@Composable
private fun ImportConfirmDialog(
    parsed: DeletionListImport,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "恢复「${parsed.name}」",
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
            TextButton(onClick = onConfirm) { Text("恢复") }
        },
    ) {
        Column {
            Text("这份备份有 ${parsed.accepted.size} 个包名，会存成一份新方案。")
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

/**
 * 备份去哪儿：**存到手机**（自己挑位置：U 盘、网盘挂载、任意文件夹）或者**分享出去**
 * （微信文件传输助手、QQ、邮件）。两条路都给，因为"备份"在不同人手里是不同的事。
 */
@Composable
private fun BackupTargetDialog(
    planName: String,
    onDismiss: () -> Unit,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "备份「$planName」",
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    ) {
        Column {
            Text(
                "备份成一份 JSON。以后换手机，用这一页右上角的「恢复」把它带回来。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            BackupOptionRow(
                title = "存到手机",
                subtitle = "自己挑文件夹和文件名",
                onClick = onSaveToDevice,
            )
            Spacer(Modifier.height(6.dp))
            BackupOptionRow(
                title = "分享出去",
                subtitle = "发到微信文件传输助手、QQ、邮件或 U 盘",
                onClick = onShare,
            )
        }
    }
}

/** 备份对话框里的一行：标题 + 说明，整行可点。 */
@Composable
private fun BackupOptionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppCardCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = FlText.subheadline,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = FlText.caption2,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}