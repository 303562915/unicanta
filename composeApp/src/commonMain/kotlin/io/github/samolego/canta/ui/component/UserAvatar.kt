package io.github.samolego.canta.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import io.github.samolego.canta.data.community.AiSender
import io.github.samolego.canta.data.imagebed.ImageBed
import io.github.samolego.canta.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
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
private object AvatarLoader {

    private val client = HttpClient { install(HttpTimeout) { requestTimeoutMillis = 9_000 } }

    /** 说清楚是谁在要这张图；图床不挑，但请求里有个像样的 UA 总比拼一个空串好查。 */
    private const val USER_AGENT = "Canta/4.3 (avatar; https://github.com/303562915/unicanta)"

    suspend fun load(url: String): ImageBitmap? = withTimeoutOrNull(12_000) {
        runCatching {
            val response = client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }
            if (!response.status.isSuccess()) {
                null
            } else {
                val bytes = response.body<ByteArray>()
                if (bytes.isEmpty()) null else bytes.decodeToImageBitmap()
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
            rememberAvatarMiss(key)
        }
    }

    val picture = bitmap
    when {
        picture != null -> Image(
            bitmap = picture,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
        )

        background == null -> LetterAvatar(
            name = name,
            modifier = modifier,
            size = size,
            letterColor = letterColor,
            letterStyle = letterStyle,
        )

        else -> LetterAvatar(
            name = name,
            modifier = modifier,
            size = size,
            background = background,
            letterColor = letterColor,
            letterStyle = letterStyle,
        )
    }
}

/**
 * 按**身份号**画一个人的头像。
 *
 * 全 app 的头像都走这里：[personAvatarUrl] 从身份号推出图床地址，设过照片的人显示照片，没设过的
 * 就是名字首字——两态由 [UserAvatar] 自己决定，调用方只管给"这是谁"。
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
) {
    // 还没改名字的人：整块马赛克。与帖子卡片、楼层用的是同一个组件（见 AuthorMark.kt），
    // 所以「没名字」在评论区和帖子区长得一模一样。
    if (isDefaultAuthorName(name)) {
        MosaicAvatar(modifier = modifier, size = size)
        return
    }
    UserAvatar(
        name = name,
        url = personAvatarUrl(owner),
        size = size,
        modifier = modifier,
        background = background,
        letterColor = letterColor,
        letterStyle = letterStyle,
    )
}
