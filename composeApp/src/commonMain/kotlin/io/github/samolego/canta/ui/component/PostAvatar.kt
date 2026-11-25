package io.github.samolego.canta.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 帖子与楼层里的头像。
 *
 * 和全 app 其他地方一样走 [PersonAvatar]：**设过照片就是照片，没设过是名字首字**。
 * [seed] 是这个人的身份号（发帖人 / 楼层作者），按它就能推出图床地址；拿不到照片就显示 [name]
 * 的首字，不会再画一块灰。
 */
@Composable
fun PostAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    seed: String = name,
) {
    PersonAvatar(name = name, owner = seed, size = size, modifier = modifier)
}
