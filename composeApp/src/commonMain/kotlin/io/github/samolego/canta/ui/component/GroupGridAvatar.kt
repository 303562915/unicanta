package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
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
 * 群头像：把前几个成员的头像拼成一张九宫格（微信群头像那种）。
 *
 * **只在群里用**：群聊顶栏、群资料页。会话列表那种只拿到"群名 + 人数"的地方没有成员名单，
 * 仍旧是文字头像（群名首字），不然一屏几十个群会各自去拉一份成员表。
 *
 * 几列由人数定：
 *  * 1 个人（或者根本没拿到名单）→ 普通头像（照片优先、首字兜底）；
 *  * 2–4 个 → 2×2；
 *  * 5 个以上 → 3×3，最多 9 个，多的不画（头像只有几十像素，塞满就是一片糊）。
 *
 * 每一格都是 [PersonAvatar]：成员设过照片就是照片，没设过就是名字首字——所以九宫格里"谁有头像谁没有"
 * 一眼看得出，而不是所有人的格子都长一个样。
 */
@Composable
fun GroupGridAvatar(
    /** 群名：拿不到成员时用它做文字头像。 */
    name: String,
    members: List<ConversationMember>,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
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
    Column(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 30))
            // 底色只负责填满格子之间的缝：每一格自己是圆的，缝里不能透出后面的内容。
            .background(scheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
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
                            background = scheme.primary.copy(alpha = 0.16f),
                            letterColor = scheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** 九宫格最多画几格。 */
private const val GRID_MAX_CELLS = 9
