package io.github.samolego.canta.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
    val cell = size / columns
    // 外框一点点圆角、格子本身是方的：和微信那种"一块方图"是同一个观感。
    val cellShape = RoundedCornerShape(cell * 0.06f)
    Column(modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.22f))) {
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

/** 九宫格最多画几格。 */
private const val GRID_MAX_CELLS = 9
