package io.github.samolego.canta.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import io.github.samolego.canta.data.app.DeletionListImport
import io.github.samolego.canta.data.app.DeletionListStore
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.ui.component.AppCardInset
import io.github.samolego.canta.ui.component.AppCardCorner
import io.github.samolego.canta.ui.component.AppLinkCard
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
 * 「删除名单」页：这台手机上**已经删掉、还没装回来**的那些包名，可以导成一份 JSON 带走。
 *
 * 名单本身不在这里编辑——它是卸载记录算出来的，删一个包它就多一条。这一页只做三件事：
 * 导出、把别处导出的那份 JSON 导进来、以及把导进来的那部分清掉。导进来**合并**而不是覆盖。
 */
@Composable
fun DeletionListScreen(
    deletionList: DeletionListStore,
    platform: CantaPlatform,
    onNavigateBack: () -> Unit,
) {
    val imported by deletionList.imported.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    /** 记录算出来的「已删除」；records 变一次就重算一次。 */
    var removed by remember { mutableStateOf(emptyList<String>()) }
    /** 导进来、还没落地的那一份；非空就弹确认框。 */
    var pendingImport by remember { mutableStateOf<DeletionListImport?>(null) }
    var clearArmed by remember { mutableStateOf(false) }

    // 记录是唯一的事实来源：进这一页、以及每次导入/清空之后都重新算一遍。
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
            AppLinkCard(
                glyph = FlGlyphs.send,
                title = "导出名单",
                subtitle = if (all.isEmpty()) {
                    "现在还没有删过东西，导出的会是一个空名单"
                } else {
                    "把 ${all.size} 个包名写成一份 JSON，发到电脑或者存下来"
                },
                onClick = {
                    scope.launch {
                        val exported = deletionList.shareJson()
                        platform.showMessage(
                            when {
                                exported.shared -> "已生成 ${exported.packages} 个包名的 JSON，选「发送到电脑」即可"
                                exported.path != null -> "已保存到 ${exported.path}"
                                else -> "这个版本不能导出文件"
                            }
                        )
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            AppLinkCard(
                glyph = FlGlyphs.importFile,
                title = "导入名单",
                subtitle = "挑一个别处导出的 JSON，把里面的包名加进来（不覆盖原有内容）",
                onClick = {
                    scope.launch {
                        val text = platform.pickTextFile()
                        if (text == null) {
                            // 取消不是失败，什么都不说。
                            return@launch
                        }
                        val parsed = deletionList.parse(text)
                        if (parsed == null) {
                            platform.showMessage("这份文件读不懂：要的是导出时那份 JSON")
                        } else {
                            pendingImport = parsed
                        }
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            if (imported.isNotEmpty()) {
                AppLinkCard(
                    glyph = FlGlyphs.delete,
                    title = if (clearArmed) "再点一次清空导入的名单" else "清空导入的名单",
                    subtitle = "只清掉导进来的那部分，不动手机里的应用与卸载记录",
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
                Spacer(Modifier.height(14.dp))
            }

            if (all.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "还没有删过东西。在这里卸载一个应用，它的包名就会出现在这份名单里。",
                        style = FlText.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // 行数不会很大（一个人真正删掉的东西几十个），但超过一屏时它得能滚，
                // 于是放进一个定高的 LazyColumn 里。
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height((all.size.coerceAtMost(12) * 44).dp),
                    verticalArrangement = Arrangement.spacedBy(RowGap),
                ) {
                    items(all, key = { it }) { packageName ->
                        PackageRow(
                            packageName = packageName,
                            importedOnly = packageName in imported && packageName !in removed,
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

/** 名单里的一行：包名，导进来的那些在右边标一句，免得和这台手机真删过的混在一起。 */
@Composable
private fun PackageRow(packageName: String, importedOnly: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = packageName,
            style = FlText.subheadline,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (importedOnly) {
            Text(
                text = "导入",
                style = FlText.caption2,
                color = scheme.primary,
            )
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