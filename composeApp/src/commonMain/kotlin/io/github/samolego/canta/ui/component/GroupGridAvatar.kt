package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.data.community.AiSender
import io.github.samolego.canta.data.community.ConversationMember

/**
 * 群头像：把几个成员的头像拼成一张**方形**九宫格（微信群头像那种观感）。
 *
 * 格子是方的、不裁圆：这一块整体就是一个「群」的图标，方格拼贴才读得出"这是好几个人"；
 * 每个格子再用圆形裁一次，看着就是一盘散落的头像，反而不像群。也不做任何模糊/灰块——
 * 没上传照片的成员那一格显示他自己的名字首字（[PersonAvatar] 的默认态）。
 *
 * **只在群里用**：群聊顶栏、群资料页。会话列表那种只拿到"群名 + 人数"的地方没有成员名单，
 * 仍旧是群名首字的文字头像，不然一屏几十个群会各自去拉一份成员表。
 *
 * 几列由人数定：
 *  * 1 个人（或者根本没拿到名单）→ 普通头像（照片优先、首字兜底）；
 *  * 2–4 个 → 2×2；
 *  * 5 个以上 → 3×3，最多 9 个（头像只有几十像素，塞满就是一片糊）。
 */
@Composable
fun GroupGridAvatar(
    /** 群名：拿不到成员时用它做文字头像。 */
    name: String,
    members: List<ConversationMember>,
    size: Dp,
    modifier: Modifier = Modifier,
    /**
     * 「消息列表里那一行头像有多大」。拼图的视觉重量靠它对齐：
     *
     * 会话列表的每一列（未读角标、时间、静音标）都按 [PersonAvatar] 那个直径的比例摆，而拼图
     * 几列方块叠起来看上去比同直径的一张脸小一圈——直接同一尺寸摆进去，群头像会明显"缩水"。
     * 所以拼图按 [systemSize] 反推一个更大的外框，让**观感**与同尺寸的个人头像一致。
     *
     * 传 0 或与 [size] 相同表示"按 [size] 本来该有的样子画"（群聊顶栏、群资料页那种没有相邻头像的
     * 地方，不需要对齐谁）。
     */
    systemSize: Dp = size,
) {
    val people = members
        .filter { it.owner.isNotEmpty() && it.owner != AiSender }
        .take(GRID_MAX_CELLS)
    val columns = when {
        people.size >= 5 -> 3
        people.size >= 2 -> 2
        else -> 1
    }
    if (columns == 1) {
        val only = people.firstOrNull()
        PersonAvatar(
            name = only?.label?.ifBlank { name } ?: name,
            owner = only?.owner.orEmpty(),
            size = size,
            modifier = modifier,
        )
        return
    }
    // 观感对齐：拼图外框按 systemSize 放大一点（见 [systemSize] 的说明），但没有它的调用点
    // （systemSize == size）保持原样，顶栏和群资料页的尺寸不受影响。
    val extent = remember(size, systemSize, columns) { gridExtent(size, systemSize, columns) }
    val cell = extent / columns
    // 外框一点点圆角、格子本身是方的：和微信那种"一块方图"是同一个观感。
    val cellShape = RoundedCornerShape(cell * 0.06f)
    Column(modifier = modifier.size(extent).clip(RoundedCornerShape(extent * 0.22f))) {
        for (row in 0 until columns) {
            Row {
                for (column in 0 until columns) {
                    val person = people.getOrNull(row * columns + column)
                    if (person == null) {
                        Spacer(Modifier.size(cell))
                    } else {
                        PersonAvatar(
                            name = person.label,
                            owner = person.owner,
                            size = cell,
                            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            letterColor = MaterialTheme.colorScheme.primary,
                            shape = cellShape,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 拼图该画多大，才能和同直径的个人头像看起来一样大。
 *
 * 一张圆脸铺满 [size] × [size]；而 n×n 的方格里，每个格子中心落着一张 `extent/n` 的圆脸，
 * 加上格子间的空隙，整块的"墨迹"大约只占 `extent × (2n-1)/n`。取两者相等：
 * `extent × (2n-1)/n = size` → `extent = size × n / (2n-1)`。2 列放大 1/3，3 列放大 1/5。
 *
 * 只放大到 [systemSize] 为止：调用方说好了这一行就占那么大，画出去会把相邻的列挤走。
 */
private fun gridExtent(size: Dp, systemSize: Dp, columns: Int): Dp {
    if (systemSize.value <= 0f || systemSize == size || columns <= 1) return size
    val scale = columns.toFloat() / (2 * columns - 1)
    val grown = size * scale
    return if (grown > systemSize) systemSize else grown
}

/** 九宫格最多画几格。 */
private const val GRID_MAX_CELLS = 9
