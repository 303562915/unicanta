package io.github.samolego.canta.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 帖子与楼层里的头像。
 *
 * 跟评论、方案列表用的是**同一套文字头像**：颜色来自 [avatarColorOf]（同一个人到处同一个色），
 * 字来自 [LetterAvatar]（首字，跳过 `#` 这类符号）。这里只多一种状态——**还没改名字的人**：
 * 那串装机码（`#10000`、`A1B2C-3D4E5`）当头像的头一个字没有任何意义，所以整块换成骨架灰圆
 * （见 [SkeletonAvatar]），跟列表加载时的占位是同一套视觉，也与评论那边的处理完全一致。
 *
 * [seed] 与 [name] 分开：名字改了颜色不该跳，颜色认的是身份号这类稳定的种子。
 */
@Composable
fun PostAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    seed: String = name,
) {
    if (isDefaultAuthorName(name)) {
        SkeletonAvatar(modifier = modifier, size = size)
        return
    }
    LetterAvatar(
        name = name,
        modifier = modifier,
        size = size,
        background = avatarColorOf(seed.ifBlank { name }),
    )
}
