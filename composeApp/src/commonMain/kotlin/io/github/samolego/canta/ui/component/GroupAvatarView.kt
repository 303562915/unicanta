package io.github.samolego.canta.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * 一个群的群头像：**设过就显示那张图，没设过就是成员拼图**（[GroupGridAvatar]）。
 *
 * 地址不用后端存：群头像是覆盖式上传的固定对象名（见 [ImageBed.groupAvatarId]），按会话号就能算出来；
 * 没上传过的群那个地址是 404，于是退回拼图——两种样子之间的切换不需要任何新字段。
 *
 * 加载/缓存复用个人头像那一套（[AvatarLoader] + [cachedAvatar]）：同一个群头像会在顶栏和群资料页
 * 同时出现，两处共用一张解码结果，换完头像也只下载一次。
 *
 * [size] 与 [systemSize] 的关系：画的那张圆图永远是 [size] 见方，`systemSize` 是「消息列表里那一
 * 行头像有多大」。消息页那一行的每一列（未读角标、时间、静音标）都按 `systemSize` 的比例摆，
 * 所以群头像必须跟同尺寸的 `PersonAvatar` **看起来一样大**——拼图那几个小方块要算到这个比例里，
 * 而不是各画各的。
 */
@Composable
fun GroupAvatarView(
    name: String,
    conversationId: Long,
    members: List<ConversationMember>,
    size: Dp,
    modifier: Modifier = Modifier,
    systemSize: Dp = size,
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
        GroupGridAvatar(name = name, members = members, size = size, modifier = modifier, systemSize = systemSize)
    }
}
