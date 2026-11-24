package io.github.samolego.canta.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
     * 下载失败过的地址 → 失败的时间。
     *
     * 没设过头像的人占多数，而头像地址是可以从身份号**推**出来的（见 [personAvatarUrl]），所以
     * 一屏几十行就是几十个 404。不记这一笔的话，每次重组、每次翻页都会再问一遍图床。
     * 只记 [AVATAR_MISS_TTL_MS] 这么久：人家刚换完头像，这一趟里也该能看见。
     */
    val misses = mutableMapOf<String, Long>()
}

/** 失败过的地址记多久：够挡住一屏的重复请求，又不至于让"刚换的头像"等太久。 */
private const val AVATAR_MISS_TTL_MS = 10 * 60 * 1000L

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
    AvatarCache.map[key] = bitmap
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
    // 命中缓存时连一帧首字母都不闪：remember 的初值就是缓存里那一张。
    var bitmap by remember(key) { mutableStateOf(cachedAvatar(key)) }

    LaunchedEffect(key) {
        if (key.isEmpty()) {
            bitmap = null
            return@LaunchedEffect
        }
        val hit = cachedAvatar(key)
        if (hit != null) {
            bitmap = hit
            return@LaunchedEffect
        }
        // 换了 url 就先清掉上一张，不然新旧两张会同时存在一帧。
        bitmap = null
        // 这个地址刚刚 404 过：没设过头像的人很多，这一趟不必再问一次。
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
