package io.github.samolego.canta.ui.screen

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.app.BackupApp
import io.github.samolego.canta.data.app.BackupTime
import io.github.samolego.canta.data.app.DeletionBackup
import io.github.samolego.canta.data.app.DeletionBackupExport
import io.github.samolego.canta.data.app.DeletionListStore
import io.github.samolego.canta.data.app.DeletionSnapshot
import io.github.samolego.canta.data.app.ImportedBackup
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.ui.component.AppCardCorner
import io.github.samolego.canta.ui.component.AppCardInset
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.fl.FlBarButton
import io.github.samolego.canta.ui.fl.FlGlyphIcon
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * 「卸载记录」页：**点备份**，然后看一份份备份。
 *
 * 页面从上到下三段：
 *  1. 顶上一个「备份」按钮 —— 点了就用**此刻**的两份名单（卸载名单 + 冻结名单）存一份，
 *     名字默认就是点的那个时间，之后可以改。
 *  2. 备份列表 —— 每行一份，点开是详情（那条分隔线下面铺开每个包）。
 *  3. 详情里的「恢复这一份」—— 照这份备份把包装回来 / 把停用的启用回来。
 *
 * 导入、分享、重命名、删除都在触手可及的位置；导入的备份和本机做的备份在列表里长得一样，
 * 只是多一行「导入」。
 */
@Composable
fun DeletionListScreen(
    deletionList: DeletionListStore,
    platform: CantaPlatform,
    onNavigateBack: () -> Unit,
    /** 装回 / 启用一个包，返回成功的条数（走应用列表那条路，不在这里另写一套）。 */
    onRestore: suspend (packageName: String, kind: OperationKind) -> Int = { _, _ -> 0 },
    /** 现在这一刻本机的两份名单（卸载名单 / 冻结名单）：备份按钮用它，只用来做备份，不落盘。 */
    currentSnapshot: suspend () -> DeletionSnapshot =
        suspend { DeletionSnapshot() },
    /** 这台手机型号 / ROM 一句话，备份时记进文件。 */
    deviceLabel: suspend () -> String = { "" },
) {
    val backups by deletionList.backups.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    /** 点开的那一份：非空就把它那条铺开，旁边给「恢复这一份」。 */
    var expanded by remember { mutableStateOf<String?>(null) }
    /** 详情里的包列表是否全部铺开：默认只放前几个，一长串包名不该一次挤满屏。 */
    var allPackages by remember { mutableStateOf(false) }
    /** 正在恢复的那一份；带进度（done / total）。 */
    var working by remember { mutableStateOf<Restoring?>(null) }
    var pendingImport by remember { mutableStateOf<ImportedBackup?>(null) }
    var renameTarget by remember { mutableStateOf<DeletionBackup?>(null) }
    var deleteTarget by remember { mutableStateOf<DeletionBackup?>(null) }
    var exportTarget by remember { mutableStateOf<DeletionBackup?>(null) }
    var lastExport by remember { mutableStateOf<DeletionBackupExport?>(null) }
    var snapshotSize by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        deletionList.ensureReady()
        runCatching { currentSnapshot() }.getOrNull()?.let { snapshotSize = it.total }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = "卸载记录",
                onNavigateBack = onNavigateBack,
                actions = {
                    // 导入：把别处那一份带进来，和本机做的备份并列排着。
                    FlBarButton(
                        glyph = FlGlyphs.importFile,
                        contentDescription = "导入备份",
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
            BackupButton(
                subtitle = if (snapshotSize > 0) {
                    "这一刻有 $snapshotSize 个包可以记下来"
                } else {
                    "把此刻的卸载名单和冻结名单存成一份"
                },
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        val snapshot = runCatching { currentSnapshot() }.getOrNull() ?: DeletionSnapshot()
                        val device = runCatching { deviceLabel() }.getOrDefault("")
                        if (snapshot.total == 0) {
                            platform.showMessage("现在没有可备份的：没有删掉未装回的包，也没有冻结的包")
                        } else {
                            val backup = deletionList.createBackup(snapshot.uninstall, snapshot.frozen, device)
                            snapshotSize = backup.total
                            expanded = backup.id
                            platform.showMessage("已备份「${backup.name}」：${backup.counts}")
                        }
                        busy = false
                    }
                },
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = "备份列表（${backups.size}）",
                style = FlText.caption1.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppCardInset + 2.dp, vertical = 4.dp),
            )

            if (backups.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "还没有备份。点上面的「备份」按此刻的两份名单存一份；以后换手机用右上角导回来。",
                    style = FlText.footnote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppCardInset + 10.dp),
                )
            }

            backups.forEach { backup ->
                val open = expanded == backup.id
                BackupRow(
                    backup = backup,
                    expanded = open,
                    allPackages = allPackages,
                    restoring = working?.takeIf { it.id == backup.id },
                    onToggle = { expanded = if (open) null else backup.id; allPackages = false },
                    onToggleAllPackages = { allPackages = !allPackages },
                    onRestore = {
                        if (working == null) {
                            working = Restoring(id = backup.id, done = 0, total = backup.total)
                            scope.launch {
                                val result = deletionList.restore(
                                    backup = backup,
                                    restore = { pkg, kind -> onRestore(pkg, kind) },
                                    onProgress = { d, t -> working = Restoring(backup.id, d, t) },
                                )
                                working = null
                                val failedNote = if (result.failed.isEmpty()) {
                                    ""
                                } else {
                                    "，${result.failed.size} 个没恢复（${result.failed.take(3).joinToString("、")}）"
                                }
                                platform.showMessage("恢复完成：成功 ${result.succeeded} / ${result.total}$failedNote")
                            }
                        }
                    },
                    onRename = { renameTarget = backup },
                    onShare = { exportTarget = backup },
                    onDelete = { deleteTarget = backup },
                )
                Spacer(Modifier.height(6.dp))
            }

            lastExport?.let { exported ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (exported.shared) "已交给分享面板：${exported.fileName}" else "上次保存：${exported.path}",
                    style = FlText.caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = AppCardInset + 2.dp),
                )
            }
        }
    }

    // 备份去哪儿：分享出去，还是存到手机（自己挑位置）。
    exportTarget?.let { backup ->
        ExportDialog(
            backup = backup,
            onDismiss = { exportTarget = null },
            onShare = {
                exportTarget = null
                scope.launch {
                    val exported = deletionList.shareAs(backup)
                    lastExport = exported
                    if (!exported.shared) {
                        platform.showMessage(
                            exported.path?.let { "这个版本没有分享面板，已存到 $it" }
                                ?: "这个版本没有分享面板，也没能写文件"
                        )
                    }
                }
            },
            onSaveToDevice = {
                exportTarget = null
                scope.launch {
                    val exported = deletionList.saveAs(backup)
                    lastExport = exported
                    if (exported.path != null) {
                        platform.showMessage("已保存到 ${exported.path}")
                    } else {
                        platform.showMessage("已取消，没有写出文件")
                    }
                }
            },
        )
    }

    renameTarget?.let { backup ->
        RenameDialog(
            backup = backup,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                scope.launch {
                    val renamed = deletionList.rename(backup.id, name)
                    platform.showMessage(renamed?.let { "已改名为「${it.name}」" } ?: "这一份已经不在了")
                }
            },
        )
    }

    deleteTarget?.let { backup ->
        CantaDialog(
            onDismissRequest = { deleteTarget = null },
            title = "删除「${backup.name}」",
            buttons = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(Res.string.cancel))
                }
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val gone = deletionList.removeBackup(backup.id)
                        if (expanded == backup.id) expanded = null
                        platform.showMessage(if (gone) "已删除这一份备份" else "这一份已经不在了")
                    }
                }) { Text("删除") }
            },
        ) {
            Text("删的是这一份备份本身，手机上的应用不会被改动。")
        }
    }

    pendingImport?.let { parsed ->
        ImportConfirmDialog(
            parsed = parsed,
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                scope.launch {
                    val backup = deletionList.import(parsed.raw)
                    if (backup == null) {
                        platform.showMessage("这份文件恢复不了任何包")
                    } else {
                        expanded = backup.id
                        val rejectedNote =
                            if (parsed.rejected > 0) "，丢掉 ${parsed.rejected} 条看不懂的" else ""
                        platform.showMessage("已导入「${backup.name}」：${backup.uninstall.size} 个包$rejectedNote")
                    }
                }
            },
        )
    }
}

/** 正在跑的那一份恢复：哪一份、到第几个。 */
private data class Restoring(val id: String, val done: Int, val total: Int)

/** 详情里每份名单先露几个：够认出这是哪一次删的，又不至于把页面撑成一张长表。 */
private const val PACKS_PREVIEW = 12

/** 页面上那唯一一个主动作：备份。写文字不写图标，因为不认识图标的人读不出"备份"。 */
@Composable
private fun BackupButton(subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(if (enabled) scheme.primary else scheme.surfaceContainerHigh)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlGlyphIcon(
            glyph = FlGlyphs.addCircle,
            size = 20.dp,
            tint = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant,
            fill = 1f,
        )
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "备份",
                style = FlText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant,
            )
            Text(
                text = subtitle,
                style = FlText.caption2,
                color = (if (enabled) scheme.onPrimary else scheme.onSurfaceVariant).copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * 列表里的一行：名字、时间、两份名单各几条；点一下把里面每个包铺开。
 *
 * 铺开后那一段就是详情：先用一串小字说清这份备份是什么时候、哪台机器上的，
 * 然后是「恢复这一份」，最后是一份份应用包。
 */
@Composable
private fun BackupRow(
    backup: DeletionBackup,
    expanded: Boolean,
    allPackages: Boolean,
    restoring: Restoring?,
    onToggle: () -> Unit,
    onToggleAllPackages: () -> Unit,
    onRestore: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
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
                    glyph = if (backup.imported) FlGlyphs.importFile else FlGlyphs.copy,
                    size = 18.dp,
                    tint = scheme.primary,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = backup.name,
                    style = FlText.subheadline,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(BackupTime.point(backup.createdAt))
                        append(" · ")
                        append(backup.counts)
                        if (backup.imported) append(" · 导入")
                    },
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            // 转一下箭头就够了：整行可点，铺开详情。
            FlGlyphIcon(
                glyph = if (expanded) FlGlyphs.chevronDown else FlGlyphs.chevronForward,
                size = 16.dp,
                tint = scheme.onSurfaceVariant,
                fill = 0f,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
                if (backup.device.isNotEmpty()) {
                    Text(
                        text = "机型：${backup.device}",
                        style = FlText.caption2,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "备份于 ${BackupTime.point(backup.createdAt)}",
                    style = FlText.caption2,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailAction(
                        label = when {
                            restoring != null -> "恢复中 ${restoring.done}/${restoring.total}"
                            else -> "恢复这一份"
                        },
                        enabled = restoring == null && backup.total > 0,
                        onClick = onRestore,
                        modifier = Modifier.weight(1f),
                    )
                    DetailAction(
                        label = "分享/导出",
                        enabled = true,
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        secondary = true,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailAction(
                        label = "重命名",
                        enabled = true,
                        onClick = onRename,
                        modifier = Modifier.weight(1f),
                        secondary = true,
                    )
                    DetailAction(
                        label = "删除",
                        enabled = true,
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        secondary = true,
                        danger = true,
                    )
                }
                if (backup.uninstall.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionHeader("卸载名单（${backup.uninstall.size}）")
                    // 一长串包名不用一次铺满屏：先放前 PACKS_PREVIEW 个，点「展开」再放全。
                    val shown = if (allPackages) backup.uninstall else backup.uninstall.take(PACKS_PREVIEW)
                    shown.forEach { app -> AppRow(app, "装回") }
                    MoreRow(
                        hidden = backup.uninstall.size - shown.size,
                        all = allPackages,
                        onClick = onToggleAllPackages,
                    )
                }
                if (backup.frozen.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionHeader("冻结名单（${backup.frozen.size}）")
                    val shown = if (allPackages) backup.frozen else backup.frozen.take(PACKS_PREVIEW)
                    shown.forEach { app -> AppRow(app, "启用") }
                    MoreRow(
                        hidden = backup.frozen.size - shown.size,
                        all = allPackages,
                        onClick = onToggleAllPackages,
                    )
                }
                if (backup.total == 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "这一份是空的。",
                        style = FlText.footnote,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 详情里那两段名单的标题。 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = FlText.caption1.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** 一长串应用包先只看前几个，剩下的用这一行收着；点一下铺开 / 收起。 */
@Composable
private fun MoreRow(hidden: Int, all: Boolean, onClick: () -> Unit) {
    if (hidden <= 0 && !all) return
    Text(
        text = if (all) "收起" else "展开剩下 $hidden 个",
        style = FlText.caption1,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** 备份里的一个应用包：应用名、包名、（知道的）版本号，右边写着恢复它会做什么。 */
@Composable
private fun AppRow(app: BackupApp, action: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(FlSurfaceLevel.Inset.colorOf(scheme))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.shownLabel,
                style = FlText.caption1,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(app.packageName)
                    if (app.versionName.isNotEmpty()) append(" · ${app.versionName}")
                },
                style = FlText.caption2.copy(fontFamily = FontFamily.Monospace),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = action, style = FlText.caption2, color = scheme.primary)
    }
}

/** 详情里那几格动作之一。[secondary] 画成淡底主色字，[danger] 画成红字。 */
@Composable
private fun DetailAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    danger: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val background = when {
        !enabled -> scheme.surfaceContainerHigh
        danger -> scheme.error.copy(alpha = 0.12f)
        secondary -> scheme.primary.copy(alpha = 0.12f)
        else -> scheme.primary
    }
    val foreground = when {
        !enabled -> scheme.onSurfaceVariant
        danger -> scheme.error
        secondary -> scheme.primary
        else -> scheme.onPrimary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = FlText.subheadline,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 改名：一个输入框，默认把现名填好，直接删改就行。 */
@Composable
private fun RenameDialog(
    backup: DeletionBackup,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(backup.id) { mutableStateOf(backup.name) }
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "重命名备份",
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = { onConfirm(text) }) { Text("保存") }
        },
    ) {
        Column {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("备份名字") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "留空就用默认名字：${DeletionBackup.defaultName(backup.createdAt)}",
                style = FlText.caption2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 分享出去 / 存到手机：两条路都给，因为"导出"在不同人手里是不同的事。 */
@Composable
private fun ExportDialog(
    backup: DeletionBackup,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSaveToDevice: () -> Unit,
) {
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "导出「${backup.name}」",
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    ) {
        Column {
            Text(
                "导出的是一份 JSON（卸载名单 + 冻结名单）。分享给别人或换手机后，用这一页右上角的「导入」带回来。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OptionRow(
                title = "分享出去",
                subtitle = "发到微信文件传输助手、QQ、邮件或 U 盘",
                onClick = onShare,
            )
            Spacer(Modifier.height(6.dp))
            OptionRow(
                title = "存到手机",
                subtitle = "自己挑文件夹和文件名",
                onClick = onSaveToDevice,
            )
        }
    }
}

/** 对话框里的一行：标题 + 说明，整行可点。 */
@Composable
private fun OptionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppCardCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = FlText.subheadline, color = MaterialTheme.colorScheme.onSurface)
        Text(text = subtitle, style = FlText.caption2, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 导入确认框：说清这份文件里写了多少条、来自哪台机器、会丢掉几条。
 * 确认之后拿 [ImportedBackup.raw]（原文）真的导进来。
 */
@Composable
private fun ImportConfirmDialog(
    parsed: ImportedBackup,
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
            Text("这份文件里写了 ${parsed.uninstall.size} 个包名，导入后存成一份新备份，随时可以恢复。")
            if (parsed.device.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "来自 ${parsed.device}",
                    style = FlText.caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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