package io.github.samolego.canta.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import io.github.samolego.canta.data.community.AiSender
import io.github.samolego.canta.data.imagebed.ImageBed
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * 这一趟进程里解好的头像，键是图床直链。
 *
 * 为什么要一个进程级的表：同一个人的头像会同时出现在「我的」、个人页、评论列表和聊天气泡里，
 * 同一张图每处各下一次既慢又费流量；而重组（滚动、主题切换）比网络快得多，没有这一层就会反复
 * 下载同一张。
 *
 * **不是 Compose 状态**：它只在 [LaunchedEffect]、[remember] 里被读写，不参与快照，所以放成普通
 * `mutableMapOf` 就够了——把一个 `ImageBitmap` 塞进 `mutableStateOf` 会随每次进页面通知所有读者
 * 重组，而这里要的只是"下次别下载"。
 */
private object AvatarCache {
    val map = mutableMapOf<String, ImageBitmap>()

    /**
     * 失败过的地址 → 失败的时间。
     *
     * 没设过头像的人占多数，而头像地址是可以从身份号**推**出来的（见 [personAvatarUrl]），所以
     * 一屏几十行就是几十个 404。不记这一笔的话，每次重组、每次翻页都会再问一遍图床。
     * 只记 [AVATAR_MISS_TTL_MS] 这么久：人家刚换完头像，这一趟里也该能看见。
     */
    val misses = mutableMapOf<String, Long>()

    /**
     * 每个地址被写过几次。
     *
     * 换头像是**覆盖式上传**：直链不变、背后那张图换了。没有它，屏幕上已经画好的 [UserAvatar]
     * （`remember(url)` 拿着的还是旧值）就一直是首字，必须退出页面再进来才换。
     *
     * **按地址记**而不是一个全局计数：全局计数一响，整屏头像的 `LaunchedEffect` 全部重启，正在下的
     * 那些请求互相取消重来，一屏 N 个头像会放大成 N² 次请求。
     */
    private val revisions = mutableMapOf<String, Int>()

    /** 此刻正看着某个地址的那些头像：写缓存时只叫醒它们。 */
    private val watchers = mutableMapOf<String, MutableList<() -> Unit>>()

    fun revisionOf(url: String): Int = revisions[url] ?: 0

    fun watch(url: String, handler: () -> Unit) {
        watchers.getOrPut(url) { mutableListOf() } += handler
    }

    fun unwatch(url: String, handler: () -> Unit) {
        val list = watchers[url] ?: return
        list.remove(handler)
        if (list.isEmpty()) watchers.remove(url)
    }

    /** 这个地址刚被写过：只有看着它的头像重读一次。 */
    fun noteWritten(url: String) {
        revisions[url] = revisionOf(url) + 1
        watchers[url]?.toList()?.forEach { notify -> notify() }
    }
}

/** 失败过的地址记多久：够挡住一屏的重复请求，又不至于让"刚换的头像"等太久。 */
private const val AVATAR_MISS_TTL_MS = 10 * 60 * 1000L

/**
 * 解码好的头像最多存几张。
 *
 * 256×256 的 ARGB 位图一张约 256KB，200 张就是 50MB——正好是一个还算体面的上限：超了整表丢掉，
 * 最坏是重新下一次。不做 LRU 是有意的：这个表只在滚动和切页时被读，挑选"最久没用的那张"要多维护
 * 一棵链，收益却只是少下几张图。
 */
private const val AVATAR_CACHE_ENTRIES = 200

/** 这个地址是不是刚刚失败过。 */
private fun avatarMissed(url: String): Boolean {
    val at = AvatarCache.misses[url] ?: return false
    if (currentTimeMillis() - at <= AVATAR_MISS_TTL_MS) return true
    AvatarCache.misses.remove(url)
    return false
}

/** 记一笔失败，下一次同一地址先别问了。 */
private fun rememberAvatarMiss(url: String) {
    AvatarCache.misses[url] = currentTimeMillis()
}

/**
 * 一个人的头像直链。
 *
 * 后端没有"头像"这一列：图床上的对象名是固定的 `unicanta/头像/<owner>.jpg`（覆盖式上传，见
 * [io.github.samolego.canta.data.imagebed.ImageBed.avatarId]），所以**任何一个身份号都能推出自己的
 * 地址**。推出来的地址对没设过头像的人是 404，[UserAvatar] 那边会先画首字、失败记一笔（见
 * [AvatarCache.misses]），不会每次都去问一遍。
 *
 * 空身份、以及那个不是人的 AI 发送者，都回答空串：[UserAvatar] 收到空串连请求都不发，直接画首字。
 */
fun personAvatarUrl(owner: String): String {
    val who = owner.trim()
    if (who.isEmpty() || who == AiSender) return ""
    return ImageBed.fileUrl(ImageBed.avatarId(who))
}

/**
 * [url] 已经下载并解好的那张图，没有就 null。
 *
 * 头像上传是**覆盖式**的（同一个对象名，见 `ImageBed.avatarId`），所以换完头像那条直链不会变；
 * 换头像的页面手里正好有新的字节，解码后用 [cacheAvatar] 直接盖掉这一格，不然会当场把旧头像
 * 当成缓存命中画出来，以为没换成功。
 */
internal fun cachedAvatar(url: String): ImageBitmap? = AvatarCache.map[url.trim()]

/** 把 [bitmap] 记到 [url] 名下；[url] 为空或位图为空时什么也不做。 */
internal fun cacheAvatar(url: String, bitmap: ImageBitmap) {
    val key = url.trim()
    if (key.isEmpty()) return
    // 换头像之后同一个地址背后的字节换了：上次那次 404 的记号必须一起清掉，否则这一趟里
    // 别的页面还会被它挡十分钟。
    AvatarCache.misses.remove(key)
    // 一份 256×256 的位图约 256KB，人数一多这张表就只增不减；满了就整张丢掉重来——
    // 头像重下一次比把内存吃掉好。
    if (AvatarCache.map.size >= AVATAR_CACHE_ENTRIES) AvatarCache.map.clear()
    AvatarCache.map[key] = bitmap
    // 通知看着这个地址的头像：缓存变了，重读一次（覆盖式上传时地址不变，见 [AvatarCache.revisionOf]）。
    AvatarCache.noteWritten(key)
}

/**
 * 下载并解码头像的那一半：一个 client、一次 GET、一次 decode。
 *
 * 构造照 `ui/component/DeviceHero.kt`（仓库里读远程图片的现成先例）：同样的 `HttpClient` +
 * `HttpTimeout`、同样走 Ktor 自带的 `body<ByteArray>()`、同样用 compose resources 的
 * `ByteArray.decodeToImageBitmap()`（那一处也没有额外的 OptIn）。
 *
 * 失败一律 null（连不上、4xx、字节不是图）：头像不是页面能不能用的前提，退回首字母圆就好，
 * 没必再往上抛一层异常。
 */
internal object AvatarLoader {

    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 9_000 }
        // **不跟随重定向**。图床被劫持/被墙时回的不是 4xx，而是一条 `302` 指向别的域名（实测
        // 2026-11-26：`Location: https://illegal.mdes.go.th/`）。跟随过去要么连不上、要么拿回一张
        // 不是图的网页，两种情况都落在「解码失败」上——于是谁也说不出这一屏为什么全是首字。
        // 不跟，3xx 就落进下面按状态码分类的那一支，日志里直接写清楚是 302。
        //
        // 开关在这里的写法有讲究：`followRedirects` 是 `HttpClientConfig` 上的属性，**只能在这里配**，
        // 进了请求阶段就没有这个口了（`HttpRequestBuilder` 上没有它，`HttpRequestPipeline` 这个类型
        // 在当前 Ktor 版本里也已经不存在——照旧写法会直接编译不过）。
        followRedirects = false
        expectSuccess = false
    }

    /**
     * 图床根地址。**只认这一个域名的图**：图床被劫持时回的是 `302` 指向别的站点，跟着走就是去下别人的
     * 网页；而且那条跳转本身可能就是钓鱼/广告。头像只从自己那台图床取，别的地址一律当「这次没拿到」。
     *
     * 反过来说，这也就把"劫持"这件事变成一句能查的话：图床域名背后回 200 但字节不是图，日志里是解码失败。
     */
    private const val IMAGE_BED_HOST = "cloudflare-imgbed-erh.pages.dev"

    /** [url] 是不是我们自己那台图床上的地址。 */
    private fun isOwnImageBed(url: String): Boolean {
        val rest = url.trim().removePrefix("https://").removePrefix("http://")
        val host = rest.substringBefore('/').substringBefore('?')
        return host.equals(IMAGE_BED_HOST, ignoreCase = true) ||
            host.equals("www.$IMAGE_BED_HOST", ignoreCase = true)
    }

    /** 说清楚是谁在要这张图；图床不挑，但请求里有个像样的 UA 总比拼一个空串好查。 */
    private const val USER_AGENT = "Canta/4.3 (avatar; https://github.com/303562915/unicanta)"

    /** 写日志用的标签：头像取不到时那一行会进「日志」页，从此不用靠问人屏幕上画的是什么。 */
    private const val AVATAR_TAG = "Avatar"

    /**
     * 正在下载的地址 → 那一趟下载。
     *
     * 一屏里同一个人常常出现好几次（会话行、头像排、搜索结果、聊天气泡），每次各发一个 GET 既慢
     * 又费流量；这里让同地址的并发请求共用一趟。**只共享字节、不共享解码**：解码留在调用方的
     * 线程上做（和以前一样），省得把 Skia 的解码器搬来搬去。
     */
    private val inFlight = mutableMapOf<String, Deferred<ByteArray?>>()

    /**
     * 服务器明确说「这个地址没有图」的那些地址（404/403/410）。
     *
     * 和 [inFlight] 一样只在 IO 侧读写，用同一个作用域。存在的理由：调用方要分清
     * 「这个人没设过头像」和「这次网络没通」——前者值得记一笔十分钟别再来问，
     * 后者记了就等于一次代理抖动让一整屏头像消失十分钟。
     */
    private val missing = mutableMapOf<String, Boolean>()

    /** 上一次 [load] 的结果是不是「服务器明确说没有这张图」。 */
    fun wasMissing(url: String): Boolean = missing[url.trim()] == true

    /** 共享下载用的后台作用域：某个头像滚出屏幕、等待被取消，不该把别人的那一份也掐掉。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun load(url: String): ImageBitmap? {
        val key = url.trim()
        val bytes = bytesOf(key) ?: return null
        return runCatching { bytes.decodeToImageBitmap() }
            .onSuccess { missing.remove(key) }
            .onFailure { LogUtils.w(AVATAR_TAG, "头像不是图（${bytes.size} 字节）：$key") }
            .getOrNull()
    }

    private suspend fun bytesOf(url: String): ByteArray? {
        inFlight[url]?.let { return it.await() }
        val job = scope.async(start = CoroutineStart.LAZY) {
            val result = fetch(url)
            // 失败也清掉：这个地址下次还能再问。以前只清成功的，一个地址在 `inFlight` 里
            // 留着失败的 Deferred 时，后面的 `await()` 会一直拿到那个 null。
            inFlight.remove(url)
            result
        }
        inFlight[url] = job
        return job.await()
    }

    private suspend fun fetch(url: String): ByteArray? = withTimeoutOrNull(12_000) {
        // 只从自己那台图床取图：别的地址（被人改成跳转目标的直链、共享进来的外链）不问。
        if (!isOwnImageBed(url)) {
            LogUtils.w(AVATAR_TAG, "头像地址不在自家图床上，跳过：$url")
            return@withTimeoutOrNull null
        }
        runCatching {
            // 跟随重定向在 client 那边已经关掉（见 `DisableRedirects`），所以 3xx 会原样回到这里。
            val response = client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }
            if (!response.status.isSuccess()) {
                // 4xx = 这个地址本来就没有图（没设过头像的占多数），记下来别反复问；
                // 3xx（关掉了跟随，所以能看见它）= 图床把这张图指到别处去了，**不是**"这人没头像"，
                // 更不能记那一笔；5xx / 超时 / 断流 = 这一次没成，同样不记。
                val code = response.status.value
                if (code in 400..499) missing[url] = true else missing.remove(url)
                LogUtils.w(AVATAR_TAG, "头像没取到 $code：$url")
                null
            } else {
                val bytes = response.body<ByteArray>().takeIf { it.isNotEmpty() }
                if (bytes == null) LogUtils.w(AVATAR_TAG, "头像是空响应：$url")
                bytes
            }
        }.getOrNull()
    }
}

/**
 * 一个人的头像：[url] 有图就是那张圆形裁剪的图，没有（或还在下、下不下来）就是 [LetterAvatar]。
 *
 * 三态和聊天里的图片气泡一样，只是这里的"占位"本身就是有意义的头像：首字母圆一直画着，图到了
 * 再换上去，所以加载过程不会闪一个空洞，失败也不会变成一块灰。
 *
 * [url] 为空是**最常见**的情况（没设过头像的人占多数），那条路上一个网络请求都不发。
 *
 * 参数：
 *  * [name]：首字母圆的来源（[avatarLetter] 取它的第一个字符），也是图的 `contentDescription`；
 *  * [size]：直径；圆用 `clip(CircleShape)` 裁，图按 [ContentScale.Crop] 填满；
 *  * [background]：首字母圆的底色，null 就用 [LetterAvatar] 自己那个（主题主色）——
 *    评论列表按人挑颜色，调用方传，没传就跟着主题走。
 */
@Composable
fun UserAvatar(
    name: String,
    url: String,
    size: Dp,
    modifier: Modifier = Modifier,
    background: Color? = null,
    letterColor: Color = Color.White,
    /** 首字那个字的样式：大圆传大样式，见 [LetterAvatar]。 */
    letterStyle: TextStyle? = null,
    /** 外形：默认圆形；群头像传方形圆角（见 [GroupAvatarView]）。 */
    shape: Shape = CircleShape,
) {
    val key = url.trim()
    // 这个地址被写过几次。有人把新图塞进缓存（换头像）时它 +1，只有**看着同一个地址**的头像会重读
    // 一次——不是全屏重来，否则一屏 N 个头像的请求会互相取消重下。
    var revision by remember(key) { mutableIntStateOf(AvatarCache.revisionOf(key)) }
    DisposableEffect(key) {
        val handler = { revision = AvatarCache.revisionOf(key) }
        AvatarCache.watch(key, handler)
        onDispose { AvatarCache.unwatch(key, handler) }
    }
    // 命中缓存时连一帧首字母都不闪：remember 的初值就是缓存里那一张。
    var bitmap by remember(key) { mutableStateOf(cachedAvatar(key)) }

    LaunchedEffect(key, revision) {
        if (key.isEmpty()) {
            bitmap = null
            return@LaunchedEffect
        }
        val hit = cachedAvatar(key)
        if (hit != null) {
            bitmap = hit
            return@LaunchedEffect
        }
        // 同一地址被重写而新图还没到（或缓存被清过）时，先留着手上的那张：退回一个首字再换回来，
        // 才是换头像那一刻最刺眼的闪。换 url 的情况由 `remember(key)` 负责清空。
        if (avatarMissed(key)) return@LaunchedEffect
        val loaded = AvatarLoader.load(key)
        if (loaded != null) {
            cacheAvatar(key, loaded)
            bitmap = loaded
        } else {
            // **网络失败不记成「这个人没头像」**：以前这里无条件记一笔，于是一次代理抖动就等于
            // 这个人十分钟没有头像——而没设过头像的人（404）和没网上的人在该记的账上是两回事。
            // 只有**服务器明确回了 4xx**（这个人确实没有头像/没权限）才记；超时、断流、解码失败
            // 一律当成「这次没取到」，下次进页面/下一屏还会再试。
            if (AvatarLoader.wasMissing(key)) rememberAvatarMiss(key)
        }
    }

    val picture = bitmap
    // 文字头像一直在底下占着位置，照片到了再淡入盖上去。
    //
    // 为什么不是"没图就灰块"：那样没上传过照片的人（大多数）满屏都是灰圆，一个字都读不到；
    // 也做过"先灰块后淡入"，结果一样——用户看到的仍是一屏灰。所以底色那块**永远是名字首字**。
    val pictureAlpha by animateFloatAsState(
        targetValue = if (picture != null) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "avatar-fade",
    )
    Box(modifier = modifier.size(size).clip(shape)) {
        LetterAvatar(
            name = name,
            size = size,
            background = background ?: MaterialTheme.colorScheme.primary,
            letterColor = letterColor,
            letterStyle = letterStyle,
            shape = shape,
        )
        if (picture != null) {
            Image(
                bitmap = picture,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .alpha(pictureAlpha),
            )
        }
    }
}

/**
 * 按**身份号**画一个人的头像。
 *
 * 全 app 的头像都走这里：[personAvatarUrl] 从身份号推出图床地址，设过照片的人显示照片，没设过的
 * 是名字首字（[LetterAvatar]）——两态由 [UserAvatar] 自己决定，调用方只管给"这是谁"。
 *
 * 参数和 [UserAvatar] 一样，只是把 `url` 换成 `owner`：一处地方拼地址，别的页面不必各记一遍
 * 「头像在哪个目录、文件名怎么拼」。
 */
@Composable
fun PersonAvatar(
    name: String,
    owner: String,
    size: Dp,
    modifier: Modifier = Modifier,
    background: Color? = null,
    letterColor: Color = Color.White,
    letterStyle: TextStyle? = null,
    /** 外形：默认圆形；群头像九宫格里传方形。 */
    shape: Shape = CircleShape,
) {
    UserAvatar(
        name = name,
        url = personAvatarUrl(owner),
        size = size,
        modifier = modifier,
        background = background,
        letterColor = letterColor,
        letterStyle = letterStyle,
        shape = shape,
    )
}
