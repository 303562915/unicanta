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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
 * 「删除名单」页：一份可以自己保管、也可以再带回来的包名清单。
 *
 * 只有三个动作：导出成一份 JSON、把 JSON 导回来、以及把清单清空。名单里有没有它并不影响能不能
 * 删——这一页只是让人把"我要删的东西"记下来，换手机的时候不用再想一遍。
 *
 * 导入**合并**而不是覆盖：导回来的那份是加进来的，原来已经在的不会被顶掉。
 */
@Composable
fun DeletionListScreen(
    deletionList: DeletionListStore,
    platform: CantaPlatform,
    onNavigateBack: () -> Unit,
) {
    val packages by deletionList.packages.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    /** 导进来、还没落地的那一份；非空就弹确认框。 */
    var pendingImport by remember { mutableStateOf<DeletionListImport?>(null) }
    var clearArmed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { deletionList.ensureReady() }

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
                subtitle = if (packages.isEmpty()) {
                    "现在一份都没有，导出的会是一个空名单"
                } else {
                    "把 ${packages.size} 个包名写成一份 JSON，发到电脑或者存下来"
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
                subtitle = "挑一个之前导出的 JSON，把里面的包名加进来（不覆盖原有内容）",
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
            if (packages.isNotEmpty()) {
                AppLinkCard(
                    glyph = FlGlyphs.delete,
                    title = if (clearArmed) "再点一次清空" else "清空名单",
                    subtitle = "只清掉这份清单，不动手机里的应用",
                    onClick = {
                        if (clearArmed) {
                            clearArmed = false
                            scope.launch {
                                val gone = deletionList.clear()
                                platform.showMessage("已清空 $gone 个包名")
                            }
                        } else {
                            clearArmed = true
                        }
                    },
                )
                Spacer(Modifier.height(14.dp))
            }

            if (packages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "名单是空的。导入一份 JSON，或者在应用详情页里把包名加进来。",
                        style = FlText.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // 行数不会很大（一个人真正想删的东西几十个），所以不用 LazyColumn 也能画；
                // 但超过一屏时它得能滚，于是放进一个定高的 LazyColumn 里。
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height((packages.size.coerceAtMost(12) * 44).dp),
                    verticalArrangement = Arrangement.spacedBy(RowGap),
                ) {
                    items(packages, key = { it }) { packageName ->
                        PackageRow(packageName)
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

/** 名单里的一行：包名一行，名字留白处不写（这个列表里只有包名这一个事实）。 */
@Composable
private fun PackageRow(packageName: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppCardInset)
            .clip(RoundedCornerShape(AppCardCorner))
            .background(FlSurfaceLevel.Card.colorOf(scheme))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = packageName,
            style = FlText.subheadline,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
            androidx.compose.material3.TextButton(onClick = onConfirm) { Text("导入") }
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