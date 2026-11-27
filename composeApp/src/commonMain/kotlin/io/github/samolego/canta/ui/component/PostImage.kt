package io.github.samolego.canta.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** 方图墙的格子间距。参考 fluxdo 的网格（`image_grid_builder.dart:51` 是 6.0），这里缩小一档。 */
private val PostImageGridSpacing = 4.dp

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
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(shape)
                .clickable(onClickLabel = "查看大图") { zoomed = true },
        )
    } else {
        Box(
            modifier = modifier
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

    // 全屏：已经解码好的那张直接从缓存取（[PostImageCache.get] 命中），不再下一次网络。
    if (zoomed && loaded != null) {
        PostImageViewer(urls = listOf(address), startIndex = 0, onDismiss = { zoomed = false })
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
 * 手势分两层挂，用法与 `ChatScreen` 的 `ChatImageViewer`（`ChatScreen.kt:3339-3440`）一致：
 *  * `detectTapGestures`：单击关闭，双击在 1f ↔ [PostImageViewerDoubleTapZoom] 之间切；
 *  * `detectTransformGestures`：捏合缩放 + 拖动。`zoom` 是相对上一次的增量，所以乘上去而
 *    不是赋值；缩回 ≤1f 时把位移清零并夹回 1f —— 图小于画面时还留着位移会歪在一边。
 *
 * 唯一的差别是缩放那一层还多一个开关条件（`pointerInput(zoomed)`）：没放大时
 * `detectTransformGestures` 会把单指横向拖动 consume 掉，pager 就再也翻不动了，所以没放大时
 * 只挂点击层、把拖动整个让给 pager；放大后 pager 的 `userScrollEnabled` 关掉，拖动才归缩放层
 * 做平移。代价是首次放大要双击（或先双击再捏合微调），换来的是两条手势永远不打架。
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
            val zoomed = scale > 1f

            // 翻到另一页就把缩放归位：Flutter 那边是离场页 reset（`image_viewer_page.dart:1253-1255`），
            // 不归位的话下一张会接着上一张的倍数显示。
            LaunchedEffect(pagerState.currentPage) {
                scale = 1f
                offset = Offset.Zero
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
                if (urls.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${urls.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(top = 16.dp),
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
            }
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
