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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.data.app.DeletionListExport
import io.github.samolego.canta.data.app.DeletionListImport
import io.github.samolego.canta.data.app.DeletionListStore
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.ui.component.AppCardCorner
import io.github.samolego.canta.ui.component.AppCardInset
import io.github.samolego.canta.ui.component.AppCardPair
import io.github.samolego.canta.ui.component.AppLinkCard
import io.github.samolego.canta.ui.component.AppLinkTile
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.ScreenTopBar
import io.github.samolego.canta.ui.fl.FlGlyphs
import io.github.samolego.canta.ui.fl.FlSurfaceLevel
import io.github.samolego.canta.ui.fl.FlText
import io.github.samolego.canta.ui.fl.colorOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** 列表里每一行的高度观感，与设置页那些卡片一致。 */
private val RowGap = 6.dp

/**
 * 「删除名单」页：这台手机上**已经删掉、还没装回来**的那些包名。
 *
 * 名单不在这里编辑——它是卸载记录算出来的，删一个包就多一条。这一页做四件事：
 * 导出到用户自己挑的位置、把别处导出的 JSON 导进来、把导进来的那部分清掉，
 * 以及**点一条直接装回来**（不用再去「未安装」页里把它找出来）。
 */
@Composable
fun DeletionListScreen(
    deletionList: DeletionListStore,
    platform: CantaPlatform,
    onNavigateBack: () -> Unit,
    /**
     * 点一条就恢复，返回成功的条数。和「未安装」页那一下是同一个动作，所以调用方直接把
     * AppListViewModel 那条路接进来即可。
     */
    onRestore: suspend (String) -> Int = { 0 },
) {
    val imported by deletionList.imported.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    /** 记录算出来的「已删除」。 */
    var removed by remember { mutableStateOf(emptyList<String>()) }
    /** 导进来、还没落地的那一份；非空就弹确认框。 */
    var pendingImport by remember { mutableStateOf<DeletionListImport?>(null) }
    var clearArmed by remember { mutableStateOf(false) }
    /** 最近一次导出落到哪里：导完显示在下面，文件管理器里找不到时这就是答案。 */
    var lastExport by remember { mutableStateOf<DeletionListExport?>(null) }
    /** 正在装回来的那个包。恢复是逐个跑的，第二个点下去要知道第一个还没完。 */
    var restoring by remember { mutableStateOf<String?>(null) }

    // 记录是唯一的事实来源：进这一页、以及导入 / 清空 / 恢复之后都重算一遍。
    suspend fun reload() {
        deletionList.ensureReady()
        removed = deletionList.current()
    }

    LaunchedEffect(Unit) { reload() }

    val all = remember(removed, imported) {
        (removed + imported).distinct().sortedBy { it.lowercase() }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(title = "删除名单", onNavigateBack = onNavigateBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 28.dp),
        ) {
            // 导出 / 导入并排：两个都是"文件进出"，字数也短。
            AppCardPair {
                AppLinkTile(
                    glyph = FlGlyphs.send,
                    title = "导出名单",
                    onClick = {
                        scope.launch {
                            // 位置由用户在系统界面里挑；取消就什么都不写、什么都不弹。
                            val exported = deletionList.saveJsonAs()
                            if (exported.path != null) {
                                lastExport = exported
                                platform.showMessage("已保存到 ${exported.path}")
                            }
                        }
                    },
                )
                AppLinkTile(
                    glyph = FlGlyphs.importFile,
                    title = "导入名单",
                    onClick = {
                        scope.launch {
                            val text = platform.pickTextFile()
                            // 取消不是失败，什么都不说。
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
            }
            // 上一次导到哪儿了。
            lastExport?.path?.let { where ->
                Text(
                    text = "上次导出：$where",
                    style = FlText.caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = AppCardInset + 2.dp),
                )
            }
            Spacer(Modifier.height(10.dp))

            if (imported.isNotEmpty()) {
                AppLinkCard(
                    glyph = FlGlyphs.delete,
                    title = if (clearArmed) "再点一次清空导入的名单" else "清空导入的名单",
                    subtitle = "只清导进来的那部分，不动应用与卸载记录",
                    onClick = {
                        if (clearArmed) {
                            clearArmed = false
                            scope.launch {
                                val gone = deletionList.clear()
                                reload()
                                platform.showMessage("已清空 $gone 个导入的包名")
                            }
                        } else {
                            clearArmed = true
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
            }

            if (all.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "还没有删过东西。卸载一个应用，它的包名就会出现在这里。",
                        style = FlText.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = "点一条就能装回来",
                    style = FlText.caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppCardInset + 2.dp, vertical = 4.dp),
                )
                // 行数不会很大，但超过一屏要能滚，所以放进定高的 LazyColumn。
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height((all.size.coerceAtMost(12) * 44).dp),
                    verticalArrangement = Arrangement.spacedBy(RowGap),
                ) {
                    items(all, key = { it }) { packageName ->
                        PackageRow(
                            packageName = packageName,
                            // 只有这台手机真删过的才装得回来；导入进来的那些是别的手机的记录。
                            restoreable = packageName in removed,
                            importedOnly = packageName in imported && packageName !in removed,
                            busy = restoring == packageName,
                            onRestore = {
                                if (restoring == null && packageName in removed) {
                                    restoring = packageName
                                    scope.launch {
                                        val done = onRestore(packageName)
                                        restoring = null
                                        reload()
                                        platform.showMessage(
                                            if (done > 0) {
                                                "已装回 $packageName"
                                            } else {
                                                "没能装回 $packageName"
                                            }
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
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
                    val added = deletionList.addAll(parsed.accepted)
                    val skipped = parsed.rejected
                    reload()
                    platform.showMessage(
                        buildString {
                            append("导入完成：新增 $added 个")
                            if (skipped > 0) append("，丢掉 $skipped 条看不懂的")
                        }
                    )
                }
            },
        )
    }
}

/**
 * 名单里的一行：包名在左，右边是它的状态。
 *
 * 这一台真删过的那条**整行可点**，点一下就是装回来；导入进来的那些只标「导入」，不可点——
 * 它们是别的手机上的记录，这台手机没有对应的卸载动作可以撤销。
 */
@Composable
private fun PackageRow(
    packageName: String,
    restoreable: Boolean,
    importedOnly: Boolean,
    busy: Boolean,
    onRestore: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .then(if (restoreable && !busy) Modifier.clickable(onClick = onRestore) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = packageName,
            style = FlText.subheadline,
            color = if (restoreable) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        when {
            busy -> Text(text = "恢复中…", style = FlText.caption2, color = scheme.primary)
            importedOnly -> Text(text = "导入", style = FlText.caption2, color = scheme.primary)
            restoreable -> Text(text = "装回", style = FlText.caption2, color = scheme.primary)
        }
    }
}

/** 导入前先说清会加进来多少条、丢掉多少条，别让人点一个不知道结果的确认。 */
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
            Text("这份文件有 ${parsed.accepted.size} 个包名，会加进现有名单。")
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