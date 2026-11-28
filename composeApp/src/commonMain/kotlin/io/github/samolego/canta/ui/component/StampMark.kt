package io.github.samolego.canta.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.samolego.canta.ui.fl.FlText

/**
 * 印章的墨色。只有三种，且由**章名**决定，不跟主题走：换主题不该让「病毒」从红变成蓝。
 *
 * 两档颜色就是页面上能一眼分出来的两档：黑档是「这个包在害人」，橙档是「这个包在烦人」。
 * 灰档留给还没定性的词，首发那六个用不到。
 */
object StampInk {
    /** 极恶：毒瘤 / 病毒 / 远控 / 木马。 */
    val Severe: Color = Color(0xFFB3261E)

    /** 扰民：广告 / 弹窗。 */
    val Nuisance: Color = Color(0xFFE8710A)

    /** 其余（以后加进来的词）先灰着，别给一个没定性的章上重色。 */
    val Flaw: Color = Color(0xFF8A8A8A)

    fun colorOf(title: String): Color = when (title) {
        "毒瘤", "病毒", "远控", "木马", "间谍", "窃密", "挖矿", "劫持", "诈骗", "寄生", "后门" -> Severe
        "广告", "弹窗", "捆绑", "自启", "保活", "吸血", "偷电", "越权", "引流", "残留" -> Nuisance
        else -> Flaw
    }
}

/**
 * 详情页上那枚章：斜着盖在软件信息上，双线方框，两个字。
 *
 * 角度固定 -11°、不随包名抖：抖一下就变成"随机装饰"，而这个东西要看起来像**盖上去的**，
 * 每次看都得长一个样。透明度 0.86 是为了看得见下面的东西——章是压上去的，不是贴上去的。
 */
@Composable
fun StampMark(
    text: String,
    modifier: Modifier = Modifier,
    ink: Color = StampInk.colorOf(text),
    size: Dp = 96.dp,
) {
    Box(
        modifier = modifier
            .rotate(-11f)
            .size(size)
            .graphicsLayer { alpha = 0.86f }
            .border(3.dp, ink, RoundedCornerShape(4.dp))
            .padding(4.dp)
            .border(1.dp, ink, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = FlText.title3.copy(
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.30f).sp,
                letterSpacing = 1.sp,
            ),
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * 投票选项里那枚小章：和 [StampMark] 同一套形状，只是小、只略微斜、没选中时是淡墨。
 *
 * 用章本身当选项的"图标"，是为了让人投票时就看到盖上去长什么样——这一页的三组选项
 * 左边都是徽标，章这一组换成章，投完和详情页上的样子对得上。
 */
@Composable
fun StampChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    ink: Color = StampInk.colorOf(text),
) {
    val alpha = if (selected) 1f else 0.55f
    Box(
        modifier = modifier
            .rotate(-6f)
            .border(1.8.dp, ink.copy(alpha = alpha), RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = FlText.caption2.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            ),
            color = ink.copy(alpha = alpha),
            maxLines = 1,
        )
    }
}
