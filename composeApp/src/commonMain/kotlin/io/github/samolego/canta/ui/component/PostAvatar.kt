package io.github.samolego.canta.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 帖子与楼层里的头像。
 *
 * 全 app 的头像只有两种样子（见 [UserAvatar]）：**上传过就显示那张图，没传过就是骨架占位**。
 * 帖子这一侧现在没有头像地址，所以永远落在第二种——一块磨砂灰圆（[SkeletonAvatar]），
 * 和会话列表、群成员、评论里的"还没图"完全一样，位置也永远占着，不会让左边空一块。
 *
 * [name] 与 [seed] 留在签名里：以后帖子也接图床时按 [seed] 去取 `personAvatarUrl`，
 * 调用点一行都不用改。
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun PostAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    seed: String = name,
) {
    SkeletonAvatar(modifier = modifier, size = size)
}
