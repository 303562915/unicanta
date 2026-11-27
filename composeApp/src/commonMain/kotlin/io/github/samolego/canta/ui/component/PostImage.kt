package io.github.samolego.canta.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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

    // 全屏：已经解码好的那张直接画，不再下一次网络。
    if (zoomed && loaded != null) {
        PostImageViewer(bitmap = loaded, onDismiss = { zoomed = false })
    }
}

/**
 * 一张图的全屏查看：黑底铺满、按 `Fit` 居中，点任意处关掉。
 *
 * 只做"看得清"，不做相册：不挂缩放与拖动 —— 那需要一个完整的变换状态机，而这里要解决的是
 * 长截图在楼层宽度里读不清。关掉后回到原来的滚动位置（Dialog 不参与列表布局）。
 */
@Composable
private fun PostImageViewer(bitmap: ImageBitmap, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        // 只关掉 Dialog 自己的默认窄边；不写 `decorFitsSystemWindows`（那是 Android 版独有的参数，
        // Compose Multiplatform 这份没有，写了必编译不过 —— 见 ChatScreen 里同一处注释）。
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                bitmap = bitmap,
                contentDescription = "图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            // 满屏透明层接点击：黑底 Box 只有子元素的位置能点，不铺这一层就只能点在图本身上。
            // 用 `Spacer` 而不是空 `Box`：`Box` 的 `content` 没有默认值，空着调用编译不过。
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClickLabel = "关闭", onClick = onDismiss),
            )
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
