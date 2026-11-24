package io.github.samolego.canta.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 列表行底边那条发丝线：通栏一列白底，行与行之间靠它分开。
 *
 * 为什么画在行自己身上，而不是在每个列表里塞一个 `HorizontalDivider` 行：这一层是行自己的绘制
 * 区域，高度、点击区域都不用为它改结构；列表那边也就不必知道"行之间要有线"。
 *
 * 线画在 `size.height - 半个线宽` 上，而不是刚好 `size.height`：贴边那半像素常被父层的裁剪切掉，
 * 看起来时有时无。
 */
fun Modifier.bottomHairline(color: Color, thickness: Float = 0.5f): Modifier = drawBehind {
    val stroke = thickness.dp.toPx()
    val y = size.height - stroke / 2f
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = stroke,
    )
}
