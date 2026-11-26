package io.github.samolego.canta.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import io.github.samolego.canta.data.community.ConversationMember
import io.github.samolego.canta.data.imagebed.ImageBed

/**
 * 一个群的群头像：**设过就显示那张图，没设过就是群名首字的方块**。
 *
 * 地址不用后端存：群头像是覆盖式上传的固定对象名（见 [ImageBed.groupAvatarId]），按会话号就能算出来；
 * 没上传过的群那个地址是 404，于是退回方块——两种样子之间的切换不需要任何新字段。
 *
 * **不再拼成员头像**（2026-11-26 改）：群里大多数人没设过头像，九宫格拼出来就是一堆放大的名字首字，
 * 看着又花又不认识；而且消息页拿不到成员名单、聊天页拿得到，同一个群在两处长不一样。现在两处
 * 都画群名首字（设过群头像就是那张图），一眼认得是同一个群。
 *
 * 加载/缓存复用个人头像那一套（[AvatarLoader] + [cachedAvatar]）：同一个群头像会在顶栏和群资料页
 * 同时出现，两处共用一张解码结果，换完头像也只下载一次。
 *
 * [members] 已经不用了，留着只是不让调用点全部改一遍签名；[systemSize] 同理。
 */
@Composable
fun GroupAvatarView(
    name: String,
    conversationId: Long,
    @Suppress("UNUSED_PARAMETER") members: List<ConversationMember> = emptyList(),
    size: Dp,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") systemSize: Dp = size,
) {
    val url = if (conversationId > 0L) ImageBed.fileUrl(ImageBed.groupAvatarId(conversationId)) else ""
    // 缓存命中就直接画，不再闪一下拼图。
    var bitmap by remember(url) { mutableStateOf(cachedAvatar(url)) }

    LaunchedEffect(url) {
        if (url.isEmpty()) {
            bitmap = null
            return@LaunchedEffect
        }
        cachedAvatar(url)?.let {
            bitmap = it
            return@LaunchedEffect
        }
        val loaded: ImageBitmap? = AvatarLoader.load(url)
        if (loaded != null) {
            cacheAvatar(url, loaded)
            bitmap = loaded
        }
    }

    val picture = bitmap
    if (picture != null) {
        Image(
            bitmap = picture,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.22f)),
        )
    } else {
        // 群名首字：方形圆角，和设过的那张群头像同一个外形，换头像前后位置不跳。
        UserAvatar(
            name = name.ifBlank { "群" },
            url = "",
            size = size,
            modifier = modifier,
            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            letterColor = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(size * 0.22f),
        )
    }
}
