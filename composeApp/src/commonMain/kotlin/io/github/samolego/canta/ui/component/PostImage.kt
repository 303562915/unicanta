package io.github.samolego.canta.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.roundToInt

/**
 * 方图墙的格子间距 6dp：参考 `image_grid_builder.dart:51` 就是 `const double spacing = 6.0`
 * （注释写着"与 Discourse 一致"），横竖两个方向同值（那边是 `Wrap(spacing:, runSpacing:)`，
 * 同一个数，`:63-65`）。原来按"缩小一档"取 4dp，格子之间的缝比参考窄，九宫格看着像糊成一片。
 */
private val PostImageGridSpacing = 6.dp

/** 方图墙的圆角。Flutter 那边是 4（`image_grid_builder.dart:184`），按本仓库正文图的 8dp 统一。 */
private val PostImageGridShape = RoundedCornerShape(8.dp)

/**
 * 方图墙最多铺几格。
 *
 * 三列 × 三行 = 9，正好三整行；第 10 张往后折进第 9 格的「+N」，点进去在查看器里左右翻，
 * 墙本身不变长。
 */
private const val PostImageGridMaxTiles = 9

/** 查看器的最大放大倍数，与聊天那条（`ChatScreen.kt` 的 `ChatImageMaxZoom`）保持一致。 */
private const val PostImageViewerMaxZoom = 4f

/** 双击放大到几倍，与聊天那条（`ChatScreen.kt:334` 的 `ChatImageDoubleTapZoom`）一致。 */
private const val PostImageViewerDoubleTapZoom = 2.5f

/**
 * 复制链接后那条自绘提示停留多久（毫秒）。
 *
 * 1.2 秒：与本仓 shortcut 提示的节奏一致，长到够看清、短到不挡下一眼的内容。
 * 为什么是自绘而不是 Toast：本文件是 commonMain、**不引 platform**，拿不到 `android.widget.Toast`；
 * 为一个"复制成功"把 platform 期望/expect-actual 拉进来，代价远大于收益。
 */
private const val PostImageHintMillis = 1_200L

/**
 * 正文里的一张图（Markdown 的 `![说明](地址)`）。
 *
 * 为什么要有它：`MiniMarkdown` 原来只画文字，`![截图](https://…)` 会原样显示成一串带括号的字符
 * —— 帖子里贴的图在手机上一个都看不到，只剩链接。这里把"取字节 → 解码 → 画出来"接上，
 * 与聊天图片（`ChatScreen` 的 `ChatImageBody`）同一套做法：Ktor 取字节、
 * compose resources 的 `ByteArray.decodeToImageBitmap()` 解码、失败就留在占位框上。
 *
 * 三态，与聊天那条一致：
 *  * 还没到 → 16:9 的灰框（先占住位置，图到了列表不会往下跳）；
 *  * 到了   → 按图片自身的宽高比铺满内容宽（比例是图片的，不是写死的）；
 *  * 失败   → 同一个灰框上写「图片加载失败」，点一下重试。
 *
 * 点图片进全屏（[PostImageViewer]）：正文宽度只有屏宽减 32，长截图在楼里读不清。
 * 长按弹菜单（[PostImageActionMenu]）：复制图片链接 / 在浏览器打开 —— 与 fluxdo 的图片上下文
 * 菜单同一档（`image_context_menu.dart:157-177` 的「复制链接」「打开原图」）。
 *
 * 多张图请用 [PostImageGrid]：一张一张按内容宽铺满，一楼的长度会翻几倍。
 *
 * [alt] 是 Markdown 里那句说明，用作无障碍描述；空的时候退回「图片」。
 */
@Composable
fun PostImage(
    url: String,
    modifier: Modifier = Modifier,
    alt: String = "",
) {
    val address = url.trim()
    var bitmap by remember(address) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(address) { mutableStateOf(false) }
    var zoomed by remember(address) { mutableStateOf(false) }
    // 失败之后重试一次的机会：点灰框把它 +1，LaunchedEffect 就重跑一遍。
    var attempt by remember(address) { mutableIntStateOf(0) }

    LaunchedEffect(address, attempt) {
        if (address.isEmpty()) {
            failed = true
            return@LaunchedEffect
        }
        // 同一个地址在别的楼层已经解过就不再下一次。
        val cached = PostImageCache.get(address)
        if (cached != null) {
            bitmap = cached
            failed = false
            return@LaunchedEffect
        }
        bitmap = null
        failed = false
        val decoded = PostImageCache.load(address)
        if (decoded == null) {
            failed = true
        } else {
            bitmap = decoded
        }
    }

    val loaded = bitmap
    val shape = RoundedCornerShape(8.dp)
    // 长按菜单的锚点（手指按下的位置；null = 没弹）与复制后的那行提示。两者都挂在外层 Box 上，
    // 而不是挂在图片本身上：加载失败时也要能长按（那会儿至少「复制链接」还有用）。
    var menuAt by remember(address) { mutableStateOf<Offset?>(null) }
    var hint by remember(address) { mutableStateOf<String?>(null) }

    // 外层 Box 只是"让菜单和提示能浮在图上"的容器：`modifier` 从两个分支搬到这里，
    // 所以外框尺寸、内边距与改动前完全一致（图片/灰框仍旧自己 fillMaxWidth + 比例）。
    Box(modifier = modifier.fillMaxWidth()) {
        if (loaded != null) {
            // 宽高比按图片自己算，但夹在 0.5..2.5：一张 1:4 的竖版长截图铺满内容宽会把三层楼都推不见，
            // 夹到 1:2 并裁掉两端，既看得清内容又不把这一楼撑爆。
            val ratio = if (loaded.height > 0) {
                (loaded.width.toFloat() / loaded.height.toFloat()).coerceIn(0.5f, 2.5f)
            } else {
                16f / 9f
            }
            Image(
                bitmap = loaded,
                contentDescription = alt.ifBlank { "图片" },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(shape)
                    // 原来的 `clickable(onClickLabel = "查看大图")` 换成了 `detectTapGestures`：
                    // 长按要拿手指坐标去锚菜单，`clickable` 不给坐标。无障碍标签用
                    // `semantics { onClick(label = …) }` 补回来（不然读屏只剩"图片"两个字）。
                    .semantics { onClick(label = "查看大图") { zoomed = true; true } }
                    .pointerInput(address) {
                        detectTapGestures(
                            onTap = { zoomed = true },
                            // `detectTapGestures` 里 onTap 与 onLongPress 天然互斥：长按判成功会吃掉
                            // 这一轮，抬手不再触发 onTap。所以"长按出菜单"不会顺手把查看器也打开，
                            // 也就不需要两个 pointerInput 各自计时（那样长按会同时点开大图）。
                            onLongPress = { position -> menuAt = position },
                        )
                    },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 占位固定 16:9：真实尺寸还不知道，先给一个不会太矮的比例。
                    .aspectRatio(16f / 9f)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(
                        // 显式写成分支块：`if (cond) Modifier.clickable(...) { … } else Modifier` 这种
                        // 紧跟尾随 lambda 的写法，Kotlin 的解析器会和 `else` 抢那个 `}`，容易读成
                        // "clickable 的第二个参数"。
                        if (failed) {
                            Modifier.clickable(onClickLabel = "重试") { attempt++ }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (failed) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BrokenImage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            text = "图片加载失败",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }

        // 单图菜单里没有「关闭」：这张图本来就在正文里，菜单收起即结束，没有"关掉查看器"这回事。
        PostImageActionMenu(
            url = address,
            anchor = menuAt,
            onDismiss = { menuAt = null },
            onCloseViewer = null,
            onHint = { hint = it },
        )
        PostImageHint(
            text = hint,
            onTimeout = { hint = null },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }

    // 全屏：已经解码好的那张直接从缓存取（[PostImageCache.get] 命中），不再下一次网络。
    if (zoomed && loaded != null) {
        PostImageViewer(urls = listOf(address), startIndex = 0, onDismiss = { zoomed = false })
    }
}

/**
 * 列表卡片首图缩略的圆角。8dp 与正文图（[PostImage] 里的 `RoundedCornerShape(8.dp)`）和
 * 方图墙（[PostImageGridShape]）同一档：缩略图将来若被换成大图，圆角不该跟着变一次。
 */
private val PostImageThumbShape = RoundedCornerShape(8.dp)

/** 缩略图加载失败时那颗破图图标的边长。16dp：72dp 的方框里再大就变成主角了。 */
private val PostImageThumbBrokenIcon = 16.dp

/**
 * 列表卡片里的首图缩略：固定方图、裁切、不响应点击（点击归卡片本身）。
 *
 * 为什么有它：列表卡片（`PostsSection.kt` 的 `PostCard`）原来只有标题 + 署名两行字，
 * 一条贴了截图的帖子在列表里和一条纯文字帖子长得一模一样。fluxdo 那边卡片内本来就会铺缩略
 * （`topic_expandable_excerpt.dart:233-277` 的 `_buildImages`：`BoxFit.cover` + 圆角 +
 * `thumbnailMode: true`），本仓补上同一件事。
 *
 * **为什么是固定方图、不照原文按内容宽铺**：Flutter 那边缩略块**独占一行**（宽 = 卡片内容宽，
 * 单图按 16:9 夹 80..144 高，多图 104 高），本仓的卡片把缩略放在内容**右侧**（见 `PostCard` 的
 * `Row`），右侧只剩一列宽度；铺满内容宽会把标题挤成三行、卡片长一截。方图 + [ContentScale.Crop]
 * 裁掉两端，是"一格宽换一格高"的最省地方案 —— 与方图墙同一套 Crop 口径（见 `PostImageGridTile`）。
 *
 * **为什么不挂任何手势**：卡片自己是 `combinedClickable`（点击进详情、长按收藏）。缩略图再挂
 * `clickable` / `pointerInput` 就会把这块方图从卡片的手势范围里切走 —— 手指落在图上时进不了
 * 详情，长按也不弹收藏菜单，同一张卡片上"能点的地方"变成两个不相交的区域。缩略图在这里只承担
 * "这条帖有图"的信息，动作一律交给卡片，所以这条修饰符链里**没有** `clickable`、
 * 没有 `pointerInput`、也没有 `semantics { onClick { … } }`。
 *
 * **为什么 `contentDescription = null`**：图与卡片上的标题说的是同一件事，读屏念完"图片"再念标题
 * 等于同一格停两次；而且它不可点，念出来也没法操作。真正要接无障碍描述的是详情页的 [PostImage]，
 * 那边有 Markdown 的说明文字和「查看大图」这个动作。
 *
 * 三态与 [PostImage] 同源，走同一张 [PostImageCache]（同一张图在列表、详情、方图墙里只下一次网络）：
 *  * 还没到 → `surfaceContainerHigh` 圆角方块（与正文图的占位同一个色，落在卡片底色上不突兀、
 *    也不会先闪一下白）；
 *  * 到了   → 铺满方框、`Crop` 裁切；
 *  * 失败   → 同一块灰底上画一颗 [PostImageThumbBrokenIcon] 的 `Icons.Rounded.BrokenImage`
 *    （`outline` 色），不是整块红字、也不留一个空的洞 —— 空着会让人以为"这条帖子没图"。
 *
 * 与 [PostImage] 的差别只有两处，都是刻意的：**不提供重试**（列表里滚一下就是一次新的组合，
 * 而"点一下重试"会与卡片自己的点击抢手势）与**不进全屏**（看大图请进详情）。
 */
@Composable
fun PostImageThumb(
    url: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val address = url.trim()
    var bitmap by remember(address) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(address) { mutableStateOf(false) }

    LaunchedEffect(address) {
        if (address.isEmpty()) {
            failed = true
            return@LaunchedEffect
        }
        // 同一个地址在详情页 / 别的卡片里已经解过就不再下一次（`PostImageCache`）。
        val cached = PostImageCache.get(address)
        if (cached != null) {
            bitmap = cached
            failed = false
            return@LaunchedEffect
        }
        bitmap = null
        failed = false
        val decoded = PostImageCache.load(address)
        if (decoded == null) {
            failed = true
        } else {
            bitmap = decoded
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(PostImageThumbShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (failed) {
            Icon(
                imageVector = Icons.Rounded.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(PostImageThumbBrokenIcon),
            )
        }
    }
}

/**
 * 正文里的一组图：同一楼贴了好几张时排成方图墙，点任意一格从那一张开始看大图。
 *
 * 为什么要有它：`MiniMarkdown` 把每张图分别交给 [PostImage] 时，每张都按内容宽铺满，
 * 一楼五张图就是五个整屏高的长条；而 fluxdo 是把连续的图收进一个网格
 * （`image_grid_builder.dart:42-90`：解析 `data-columns`，默认 2 列，`Wrap` 铺格子，
 * 每格 `BoxFit.cover` + 圆角，见 `:184`）。这里按 Compose 的做法用 `Column` + `Row` 自己铺：
 * 正文本身已经在一个纵向滚动容器里，再套一个 `LazyVerticalGrid` 会拿到无限高约束，直接崩。
 *
 * 规则（与需求一致，不再额外加"第几张"角标）：
 *  * 1 张 → 原样交给 [PostImage]，比例、灰底、点开全屏都和以前一模一样；
 *  * 2~4 张 → 2 列；
 *  * 5 张以上 → 3 列；
 *  * 第 10 张往后不铺格子，折进第 9 格的「+N」蒙层（[PostImageGridMaxTiles]），
 *    点它打开查看器并停在第九张 —— 37 张图也不会把一楼拉成相册。
 *
 * 加载中/失败沿用正文图的灰底占位（[PostImageGridTile]）：某一格失败只是那一格空着，
 * 不挡其它格，也不挡点开查看器。
 */
@Composable
fun PostImageGrid(
    urls: List<String>,
    modifier: Modifier = Modifier,
) {
    // 空串（`![]( )`）在 MiniMarkdown 那边已经挡过一次，这里再滤一遍：空格子会让整行错位。
    val images = remember(urls) { urls.map { it.trim() }.filter { it.isNotEmpty() } }
    if (images.isEmpty()) return
    if (images.size == 1) {
        // 一张不排墙：行为完全交给 PostImage，别人依赖的那条路径一行都不动。
        PostImage(url = images.first(), modifier = modifier)
        return
    }

    // 从哪一张点开。null = 查看器没开：查看器整棵子树只在非 null 时进 composition，
    // 关掉即销毁，pager 与缩放状态跟着一起走（不会残留上一次停在第几页）。
    var viewerStart by remember(images) { mutableStateOf<Int?>(null) }

    // 2~4 张两列、5 张以上三列。参照 fluxdo 默认 2 列（`image_grid_builder.dart:42-44`），
    // 三列只在张数多到两列会把楼层拉太长时才上。
    val columns = if (images.size <= 4) 2 else 3
    val shown = images.size.coerceAtMost(PostImageGridMaxTiles)
    val hidden = images.size - shown

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PostImageGridSpacing),
    ) {
        (0 until shown).chunked(columns).forEach { rowIndexes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PostImageGridSpacing),
            ) {
                rowIndexes.forEach { index ->
                    PostImageGridTile(
                        url = images[index],
                        // 「+N」只挂最后一格，且只在实际有折叠时才挂。
                        badge = if (hidden > 0 && index == shown - 1) "+$hidden" else null,
                        modifier = Modifier.weight(1f),
                        onClick = { viewerStart = index },
                    )
                }
                // 末行不满要补空位：不补的话最后一张会被 weight 拉成整行宽，看着像另一张图。
                repeat(columns - rowIndexes.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    val start = viewerStart
    if (start != null) {
        PostImageViewer(
            urls = images,
            startIndex = start,
            onDismiss = { viewerStart = null },
        )
    }
}

/**
 * 方图墙里的一格：1:1、`Crop`、圆角 8dp，右下角什么都不放（除了整组折叠时的「+N」）。
 *
 * 为什么固定 1:1 而不是跟图片比例走：一堆不同比例的图混在一行里，格子高度各不相同，
 * 墙会变成锯齿。Flutter 那边是"按列宽 × 图片比例算高度再夹 80..300"
 * （`image_grid_builder.dart:208-217`），在方图形态下等价于把比例钉成 1。
 *
 * 三态与 [PostImage] 同源：灰底 → 图；失败就只是这一格变成灰底 + 破图图标，
 * 点一下重试（不打开查看器，因为查看器里也是"图片加载失败"）。
 *
 * [badge] 非空时在格子上盖一层半透明黑 + 白字（"+N"）。盖层不带手势，
 * 点击仍旧落到外层 [Box] 的 clickable 上，所以点「+N」等于点这一格。
 */
@Composable
private fun PostImageGridTile(
    url: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val address = url.trim()
    var bitmap by remember(address) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(address) { mutableStateOf(false) }
    var attempt by remember(address) { mutableIntStateOf(0) }

    LaunchedEffect(address, attempt) {
        if (address.isEmpty()) {
            failed = true
            return@LaunchedEffect
        }
        val cached = PostImageCache.get(address)
        if (cached != null) {
            bitmap = cached
            failed = false
            return@LaunchedEffect
        }
        bitmap = null
        failed = false
        val decoded = PostImageCache.load(address)
        if (decoded == null) {
            failed = true
        } else {
            bitmap = decoded
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(PostImageGridShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (failed) {
                    Modifier.clickable(onClickLabel = "重试") { attempt++ }
                } else {
                    Modifier.clickable(onClickLabel = "查看大图", onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = "图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (failed) {
            Icon(
                imageVector = Icons.Rounded.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * 全屏看一组图：黑底、按 `Fit` 居中、可左右翻页、可缩放拖动，点空白或右上角 × 关掉。
 *
 * 结构照 fluxdo 的查看器：一个横向 PageView 承载画廊（`image_viewer_page.dart:1248-1275`），
 * 页码提示用 `1 / N` 计数器（`image_carousel_builder.dart:296-303`），左右翻页与缩放/平移的
 * 手势冲突靠"放大时禁掉翻页"解决 —— Flutter 那边是把下滑关闭限制成只走纵向并让页面自己
 * reset 缩放（`image_viewer_page.dart:1174`、`:1253-1255`），这里落到 Compose 就是
 * `userScrollEnabled = !zoomed`。
 *
 * 手势分三层挂，前两层的用法与 `ChatScreen` 的 `ChatImageViewer`（`ChatScreen.kt:3339-3440`）一致：
 *  * `detectTapGestures`：单击关闭，双击在 1f ↔ [PostImageViewerDoubleTapZoom] 之间切，
 *    长按在手指位置弹菜单（[PostImageActionMenu]，三项里多一项「关闭」）；
 *  * `detectTransformGestures`（只在 [zoomed] 时挂）：捏合缩放 + 拖动。`zoom` 是相对上一次的
 *    增量，所以乘上去而不是赋值；缩回 ≤1f 时把位移清零并夹回 1f —— 图小于画面时还留着位移会歪。
 *  * [detectTwoFingerPinch]（一直挂着）：**只认两指**的捏合，专门负责"1 倍时也能捏进去"。
 *
 * 为什么 1 倍时不能直接挂 `detectTransformGestures`：它不区分手指数量，单指横向拖动也会被它
 * consume，pager 于是再也翻不动。所以两层分工：放大后 pager 的 `userScrollEnabled` 关掉、拖动
 * 归变换层做平移；没放大时变换层整个不挂，改由两指那一层在 Initial 阶段抢事件（单指从不消费，
 * 翻页照旧）。这样首次放大不再只能靠双击 —— 参考 fluxdo 的用法：查看器里捏合与双击是并列的
 * 入口（`image_viewer_page.dart:1220-1223` 的 `onDoubleTap`，手势本体由 `GestureImageView` 的
 * controller 承担，缩放与翻页由 `inPageView` 决定谁让谁，见 `:1294-1300`）。
 *
 * 图仍然从 [PostImageCache] 取：正文里已经解过的那张直接命中，不重复下载。
 * 取不到就只画灰底占位 + 「图片加载失败」，右上角的 × 是唯一能看见的出口。
 */
@Composable
private fun PostImageViewer(
    urls: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    val start = startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
    Dialog(
        onDismissRequest = onDismiss,
        // 只关掉 Dialog 自己的默认窄边；不写 `decorFitsSystemWindows`（那是 Android 版独有的参数，
        // Compose Multiplatform 这份没有，写了必编译不过 —— 见 ChatScreen 里同一处注释）。
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 状态按 (这组地址, 起始页) 分桶：换一组图或换起始页就是另一份 pager/缩放，
        // 关掉 Dialog 整棵子树离开 composition，连这份分桶一起丢，下次点开是干净的。
        key(urls, start) {
            val pagerState = rememberPagerState(initialPage = start, pageCount = { urls.size })
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            // 长按菜单的锚点与复制后的提示，理由同 [PostImage]：都在这一层持有，
            // 菜单与提示浮在 pager 之上。
            var menuAt by remember { mutableStateOf<Offset?>(null) }
            var hint by remember { mutableStateOf<String?>(null) }
            val zoomed = scale > 1f

            // 翻到另一页就把缩放归位：Flutter 那边是离场页 reset（`image_viewer_page.dart:1253-1255`），
            // 不归位的话下一张会接着上一张的倍数显示。菜单也一起收起：它指的是上一张的地址。
            LaunchedEffect(pagerState.currentPage) {
                scale = 1f
                offset = Offset.Zero
                menuAt = null
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onDismiss() },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = PostImageViewerDoubleTapZoom
                                }
                            },
                            // 与 onTap 天然互斥：长按吃掉这一轮，抬手不会顺手把查看器关掉。
                            onLongPress = { position -> menuAt = position },
                        )
                    }
                    .pointerInput(zoomed) {
                        // 没放大就不挂变换层：单指拖动必须留给 pager（见上面的说明）。
                        if (!zoomed) return@pointerInput
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, PostImageViewerMaxZoom)
                            scale = next
                            offset = if (next <= 1f) Offset.Zero else offset + pan
                        }
                    }
                    // 两指那一层**不带 zoomed 键**：一旦按倍数重建，捏合刚过 1f 就会被重建打断，
                    // 只能放大一丁点、抬手再捏一次。它内部自己判断"这一轮有没有第二根手指"。
                    .pointerInput(Unit) {
                        detectTwoFingerPinch { pan, zoom ->
                            val next = (scale * zoom).coerceIn(1f, PostImageViewerMaxZoom)
                            scale = next
                            offset = if (next <= 1f) Offset.Zero else offset + pan
                        }
                    },
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // 放大状态下横向拖动是平移图片，不能同时翻页。
                    userScrollEnabled = !zoomed,
                ) { page ->
                    PostImageViewerPage(url = urls[page], scale = scale, offset = offset)
                }

                // 页码只在多图时出现：单图写个「1 / 1」是噪音。底部不放任何按钮。
                // 形状照参考查看器顶栏那颗页码：黑底 **50%**、圆角 **20**、内边距 12 / 6、
                // 白字 **14sp**（`lib/pages/image_viewer_page.dart:1457-1481`，那边同样只在
                // `pageCount > 1` 时出现）。原来是一行 70% 白字裸印在黑底上，压在浅色图片上
                // （白底截图占多数）时几乎读不出来；胶囊底色是参考给出的解法。
                if (urls.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${urls.size}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 14.sp,
                            color = Color.White,
                        ),
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                // 44dp 是触达尺寸（图标 22dp）：加载失败时这也是唯一的明确出口。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(10.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClickLabel = "关闭", onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // 长按菜单：地址取**当前页**（不是长按那一刻的页面，不过菜单一翻页就收起，
                // 见上面 LaunchedEffect，所以两者始终是同一张）。
                PostImageActionMenu(
                    url = urls[pagerState.currentPage],
                    anchor = menuAt,
                    onDismiss = { menuAt = null },
                    onCloseViewer = onDismiss,
                    onHint = { hint = it },
                )
                // 提示放底部居中、离底 96dp：躲开拇指与虚拟导航条，也不压住顶部的页码。
                PostImageHint(
                    text = hint,
                    onTimeout = { hint = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp),
                )
            }
        }
    }
}

/**
 * 只认**两指及以上**的捏合：第一次放大不再必须双击。
 *
 * 为什么不能直接用 `detectTransformGestures` 常挂：它不数手指，单指横向拖动也会被它 consume，
 * pager 从此翻不动（这正是原来只能"先双击再捏合"的原因）。所以这里自己数指针：
 *
 *  * 按下第一根手指时**什么都不消费**，这一轮先让给 pager，翻页、单击、双击都照旧；
 *  * 出现第二根手指才接手，并且**在 Initial 阶段**消费。Initial 阶段是从外往里派的，
 *    pager 的 scrollable 在 Main 阶段才看到事件；它一旦发现 change 被别人消费就会取消这一轮
 *    拖动（`awaitDragOrCancellation` 的判据），所以双指捏合不会顺手把页翻过去；
 *  * 抬起一根后回到"不认"的状态（基准清空），剩下那根不会再突然变成平移。
 *
 * 质心/半径用当前**所有**按下的指针算（两指、三指都一样），与 `detectTransformGestures` 的
 * 语义对齐：pan 取质心位移，zoom 取"平均到质心的距离"的变化比。指针数量一变就只记基准、跳过
 * 这一帧 —— 否则质心会从"单指最后位置"跳成"两指中点"，图会闪一下、还会被多缩放一截。
 *
 * [onTransform] 里要沿用调用方自己的 `coerceIn(1f, 4f)` 规则（见 [PostImageViewerMaxZoom]）。
 */
private suspend fun PointerInputScope.detectTwoFingerPinch(
    onTransform: (pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        // 只要"有人按下了"，不消费：这一轮在第二根手指出现之前都属于 pager。
        awaitFirstDown(requireUnconsumed = false)
        var lastCentroid: Offset? = null
        var lastRadius = 0f
        var lastCount = 0
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) return@awaitEachGesture
            if (pressed.size < 2) {
                lastCentroid = null
                lastRadius = 0f
                lastCount = 0
                continue
            }
            val centroid = pressed.fold(Offset.Zero) { acc, change -> acc + change.position } /
                pressed.size.toFloat()
            val radius = pressed
                .map { (it.position - centroid).getDistance() }
                .average()
                .toFloat()
            val previousCentroid = lastCentroid
            val previousRadius = lastRadius
            val previousCount = lastCount
            // 记下这一帧的基准。手指数量变过的那一帧只当"重新起算"：既不算位移也不算缩放。
            lastCentroid = centroid
            lastRadius = radius
            lastCount = pressed.size
            // 双指按下的这一轮归本层：先消费，pager 的拖动才能被取消掉。
            pressed.forEach { it.consume() }
            if (previousCentroid == null || previousCount != pressed.size) continue
            if (previousRadius <= 0.01f || radius <= 0.01f) continue
            onTransform(centroid - previousCentroid, radius / previousRadius)
        }
    }
}

/**
 * 查看器里的一页：从 [PostImageCache] 取图，取不到就画灰底占位。
 *
 * 缩放/位移由外层持有（[PostImageViewer]）并作为参数送进来：pager 要读同一个缩放值来决定
 * 是否接受翻页手势，所以它不能只待在这一页里面。
 */
@Composable
private fun PostImageViewerPage(url: String, scale: Float, offset: Offset) {
    val address = url.trim()
    var bitmap by remember(address) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(address) { mutableStateOf(false) }

    LaunchedEffect(address) {
        // 先看缓存：正文里那张已经解好的直接拿来画，不再下一次网络。
        val cached = PostImageCache.get(address)
        if (cached != null) {
            bitmap = cached
            failed = false
            return@LaunchedEffect
        }
        bitmap = null
        failed = false
        val decoded = PostImageCache.load(address)
        if (decoded == null) {
            failed = true
        } else {
            bitmap = decoded
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = "图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        } else {
            // 占位沿用正文的灰底（`PostImage` 的三态之一），只是在黑底上收成一块方框，
            // 免得整屏变灰。
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (failed) "图片加载失败" else "加载中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/**
 * 长按图片弹出的小菜单：复制图片链接 / 在浏览器打开（查看器里再多一项「关闭」）。
 *
 * 为什么是 `DropdownMenu` 而不是自己画一层浮层：它的"点外面就收起""返回键收起""贴边自动
 * 往里挪"都是现成的，这几件事自己写要处理 popup 之外的点击与窗口安全区，漏一个就是关不掉。
 *
 * 锚点用**手指按下的位置**（不选整块居中）：正文图可能只有半屏高，查看器里的长截图几乎满屏，
 * 居中弹的话菜单会正好压在手指底下那块内容上；跟着手指走，菜单落在按的地方旁边，松手就看得到
 * 自己按的是哪一块。落点靠一个 0 尺寸的 [Box] + `Modifier.offset`：`DropdownMenu` 的弹窗是相对
 * 它的父布局定位的，把父布局挪到手指处，菜单就跟过去。
 *
 * [anchor] 为 null 表示"没弹"；[onCloseViewer] 非空才多出「关闭」——正文单图没有"查看器"可关，
 * 查看器里则本来就缺一个明确的出口（右上角的 × 在长截图上离拇指很远）。
 *
 * 三项的顺序与 fluxdo 的图片上下文菜单一致（`image_context_menu.dart:157-177` 复制链接 / 打开，
 * 关闭项在最后，见 `:218-224`）。保存到相册那一项没做：本文件是 commonMain，写文件必须引 platform。
 */
@Composable
private fun PostImageActionMenu(
    url: String,
    anchor: Offset?,
    onDismiss: () -> Unit,
    /** 非空 = 在查看器里：菜单多一项「关闭」，点了直接关查看器。 */
    onCloseViewer: (() -> Unit)?,
    /** 让调用方挂一行自绘提示（[PostImageHint]）——本文件没有 Toast 可用。 */
    onHint: (String) -> Unit,
) {
    // 先落成非空的局部 val：下面 `Modifier.offset { … }` 的 lambda 里要用坐标，
    // 直接拿可空参数在 lambda 里做 smart cast 是能过，但读起来像在赌编译器。
    val press = anchor ?: return
    val closeViewer = onCloseViewer
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    Box(modifier = Modifier.offset { IntOffset(press.x.roundToInt(), press.y.roundToInt()) }) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
            PostImageMenuRow(icon = Icons.Rounded.ContentCopy, label = "复制图片链接") {
                clipboard.setText(AnnotatedString(url))
                // 先收菜单再提示：提示条要落在图中间，菜单还开着会被压在下面。
                onDismiss()
                onHint("链接已复制")
            }
            PostImageMenuRow(icon = Icons.Rounded.Link, label = "在浏览器打开") {
                onDismiss()
                // 没有默认浏览器（桌面/网页）、或者地址不是 http(s) 时会抛：不该让一次点击把
                // 整个正文炸掉，所以吞掉异常，只把结果说成一句话。
                if (!runCatching { uriHandler.openUri(url) }.isSuccess) onHint("打不开这个链接")
            }
            if (closeViewer != null) {
                PostImageMenuRow(icon = Icons.Rounded.Close, label = "关闭") {
                    onDismiss()
                    closeViewer()
                }
            }
        }
    }
}

/**
 * 图片菜单里的一行：图标 20dp + 12dp 间距 + 文字、最小行高 48dp、圆角 4dp。
 *
 * 数值照本仓 `PostsSection.kt:4280` 的 `MenuRow`（fluxdo 的 `PopupMenuItem` 形态：
 * 图标 20dp、行高 48dp），但不直接复用它 —— 那个是 `private`，跨文件用不到；也不想为了这一个
 * 菜单把那套（带 enabled/tint 的完整版）在 builder 里再暴露一遍，所以这里复制最小的一份。
 *
 * 圆角 4dp 不能省：菜单整体是圆角的，行上没有圆角的话点击涟漪是一个直角矩形，看着像补丁。
 */
@Composable
private fun PostImageMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = ink,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            maxLines = 1,
            // 12dp 间距用 padding 而不是再插一个 Spacer：`width` 这个 modifier 本文件没引，
            // 为一个间距多加一条 import 不值当。
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * 复制成功之后那行自绘提示：黑底白字的小胶囊，[PostImageHintMillis] 之后自己消失。
 *
 * 为什么自绘：本文件是 commonMain，`Toast` 是 Android 独有的，为一个"已复制"拉 expect/actual
 * 进来代价太大。放在调用方的 Box 里、底部居中，跟 Toast 的观感接近，也不挡图。
 *
 * [text] 为 null = 不显示。计时写成 `LaunchedEffect(text)`：提示一出现就是 1.2 秒的倒计时，
 * 时间到就把状态清空（`onTimeout`）。同一句话连点两次「复制」不会重新计时（键没变就不重启），
 * 那也没关系 —— 屏幕上本来就还写着"链接已复制"。
 */
@Composable
private fun PostImageHint(
    text: String?,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(text) {
        if (text != null) {
            delay(PostImageHintMillis)
            onTimeout()
        }
    }
    if (text == null) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/**
 * 正文图片的解码缓存与抓取。
 *
 * 与头像那一套（`UserAvatar.kt` 的 `AvatarLoader`）同一套写法，但**不限定域名**：正文里的图来自
 * 图床、GitHub、别人的博客，按域名白名单筛等于什么都不显示。代价是任何地址都会发一次请求，
 * 所以有三道闸：
 *
 *  * 只认 `http(s)`（Markdown 那边已经挡过一次，这里再挡）；
 *  * 单张上限 [MAX_BYTES]，超了当失败 —— 一屏几十兆会把内存吃光；
 *  * 缓存上限 [CACHE_ENTRIES]，满了整表丢掉重来（与头像表同一个策略：不做 LRU）。
 *
 * 只读网络、不写盘：帖子本来就是联网才有的内容，这张表只负责"滚动时别把同一张再下一次"。
 * 方图墙的格子与查看器的每一页都走这张表，所以同一张图在墙里和在查看器里只下一次。
 */
internal object PostImageCache {

    /** 单张图的上限。手机上一张长截图 2~4 MB，12 MB 已经覆盖绝大多数「贴个截图」。 */
    private const val MAX_BYTES = 12 * 1024 * 1024

    /**
     * 缓存几张。一张 1080 宽的解码位图约 4 MB，24 张就是 96 MB —— 到顶整表丢弃。
     *
     * 比头像表（200 张 256×256）保守得多：正文图的像素量高两个数量级，按张数算没有意义。
     */
    private const val CACHE_ENTRIES = 24

    private const val USER_AGENT = "Canta/4.3 (post image; https://github.com/303562915/unicanta)"

    private val map = mutableMapOf<String, ImageBitmap>()

    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 20_000 }
        // 正文图常被放在短链后面（`imgur.com/xxx` → 真实文件、CDN 跳转），这里必须跟随；
        // 头像那边不跟随是因为只认自家图床、防的是劫持。两处前提不同，做法也不同。
        followRedirects = true
        // 4xx/5xx 自己判：`expectSuccess = true` 会抛异常，而"这张图画不出来"不该让整段正文崩掉。
        expectSuccess = false
    }

    fun get(url: String): ImageBitmap? = map[url]

    /**
     * 取字节并解码；任何一步失败都回答 null（调用点据此画「加载失败」）。
     *
     * 解码放到 [Dispatchers.Default]：一张 1080×2000 的 PNG 解码在主线程上要几十毫秒，
     * 一屏两张就能看出掉帧。
     */
    suspend fun load(url: String): ImageBitmap? {
        map[url]?.let { return it }
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val bytes = runCatching {
            val response = client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }
            if (!response.status.isSuccess()) null else response.body<ByteArray>()
        }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        val bitmap = withContext(Dispatchers.Default) {
            runCatching { bytes.decodeToImageBitmap() }.getOrNull()
        } ?: return null
        if (map.size >= CACHE_ENTRIES) map.clear()
        map[url] = bitmap
        return bitmap
    }
}
