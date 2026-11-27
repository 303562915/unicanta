package io.github.samolego.canta.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 弹幕设置面板：颜色、大小、速度。
 *
 * 从顶栏那个字幕图标**长按**进来（点一下是开关弹幕，长按是调它长什么样）。三组都是档位而不是滑条：
 * 弹幕不是要精调的东西，"小/中/大/特大"和"慢/中/快"一眼就能选，滑条反而要求人盯着一条细线拖。
 *
 * 底下那行预览不是装饰：字号和颜色都在蓝色卡片上用同一份 [DanmakuStyle] 画出来，所以在面板里看着
 * 是什么样，飘起来就是什么样。
 */
@Composable
fun DanmakuSettingsDialog(
    style: DanmakuStyle,
    onStyleChange: (DanmakuStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    CantaDialog(
        onDismissRequest = onDismiss,
        title = "弹幕设置",
        widthFraction = WIDE_DIALOG_WIDTH,
        scrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    ) {
        Text(
            text = "点顶栏那个图标是开关弹幕，长按就是这里。",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = scheme.onSurfaceVariant,
        )

        // 颜色：一排圆点，选中那个描一圈主色边。
        DialogGroup(label = "颜色") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DanmakuColorChoices.forEach { (label, argb) ->
                    val selected = style.colorArgb == argb
                    val fill = if (argb == DANMAKU_COLOR_FOLLOW_THEME) {
                        scheme.onPrimaryContainer
                    } else {
                        Color(argb)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.RadioButton) {
                                onStyleChange(style.copy(colorArgb = argb))
                            }
                            .padding(horizontal = 3.dp, vertical = 2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(fill)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) scheme.primary else scheme.outlineVariant,
                                    shape = CircleShape,
                                ),
                        )
                        Text(
                            text = label,
                            fontSize = 9.sp,
                            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }

        // 大小：字号档位；「大小」这个词在这里就是它字面上的意思。
        DialogGroup(label = "大小") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DanmakuSizeChoices.forEach { (label, sp) ->
                    ChoiceChip(
                        label = label,
                        selected = style.fontSizeSp == sp,
                        onClick = { onStyleChange(style.copy(fontSizeSp = sp)) },
                    )
                }
            }
        }

        // 速度：每秒走多少 dp。
        DialogGroup(label = "速度") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DanmakuSpeedChoices.forEach { (label, speed) ->
                    ChoiceChip(
                        label = label,
                        selected = style.speedDpPerSec == speed,
                        onClick = { onStyleChange(style.copy(speedDpPerSec = speed)) },
                    )
                }
            }
        }

        // 预览：这块底色就是卡片那块底色，字用的就是当前这一份样式。
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = "这条评论会这样飘过去",
                fontSize = style.fontSizeSp.sp,
                fontWeight = FontWeight.SemiBold,
                color = style.resolvedColor(),
            )
        }
    }
}

/** 一组设置：一个小标题 + 下面的选项。 */
@Composable
private fun DialogGroup(label: String, content: @Composable () -> Unit) {
    Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
    content()
}

/** 一个档位按钮：选中的那个填主色底。 */
@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) scheme.primary else scheme.surfaceVariant)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
