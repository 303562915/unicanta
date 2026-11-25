package io.github.samolego.canta.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.samolego.canta.core.ImageCrop
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.ok
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * 选好、等着摆裁剪框的那张图：原图字节（真正下刀用的）与缩过的预览位图（摆框用的）。
 *
 * 个人头像和群头像共用：两边都是"选一张图 → 摆在圆窗里挪好 → 裁方 → 压到几百 KB → 传图床"。
 */
class AvatarCropSource(val source: ByteArray, val preview: ImageBitmap)

/**
 * 头像裁剪框：圆形的取景窗，图可以拖、可以缩，按「确定」把取景窗里的那一块交回去。
 *
 * 为什么是圆形窗：头像最终画在圆里，方形窗会让人把眼睛摆在角上，裁完才发现被切掉。圆形窗所见即所得。
 *
 * 画法是"显式给源矩形"，不是把整张图缩放后平移：`Canvas` 里用 `DrawScope.drawImage` 的 src/dst 重载
 * 把源图上的一块原样画进取景窗。屏幕上看到的那一块和交出去的 [ImageCrop] 由**同一组数**算出来，
 * 所以不存在"看到的和裁下来的不是同一个地方"；夹取也变成硬边界——源矩形永远落在图内，圆窗里不会
 * 露黑边。
 *
 * 交出去的 [ImageCrop] 是**归一化**的（0..1，按预览图的宽高）：真正下刀的是原图，两张图的像素尺寸
 * 不一样，共用的只有比例（见 `core/ImageCrop`）。
 *
 * 交互只有两种：拖 = 挪位置，滑杆 = 缩放（1×–4×）。没做双指缩放：不是必须的手势，而滑杆在小屏上
 * 更容易点准。
 */
@Composable
fun AvatarCropDialog(
    bitmap: ImageBitmap,
    onDismiss: () -> Unit,
    onConfirm: (ImageCrop) -> Unit,
    /** 标题：个人头像是「调整头像」，群头像是「调整群头像」。 */
    title: String = "调整头像",
) {
    // 取景窗的边长写死：跟着屏幕缩放会让"拖多远"的手感在不同机器上来回变。
    val viewport = 260.dp
    var zoom by remember { mutableStateOf(1f) }
    // 取景窗中心相对图中心的位移，单位是**源图像素**：这样改缩放时不用重算、也不会漂。
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    val viewportPx = with(LocalDensity.current) { viewport.toPx() }
    val imageWidth = bitmap.width.toFloat().coerceAtLeast(1f)
    val imageHeight = bitmap.height.toFloat().coerceAtLeast(1f)
    // 1× 时取景窗里那一块的源图边长：取短边，所以 1× 正好是"整图居中裁方"。
    val baseSide = minOf(imageWidth, imageHeight)
    // 当前缩放下取景窗对应的源图边长。
    val sourceSide = baseSide / zoom
    val limitX = ((imageWidth - sourceSide) / 2f).coerceAtLeast(0f)
    val limitY = ((imageHeight - sourceSide) / 2f).coerceAtLeast(0f)
    val centerX = imageWidth / 2f + panX.coerceIn(-limitX, limitX)
    val centerY = imageHeight / 2f + panY.coerceIn(-limitY, limitY)
    // 源矩形左上角。夹一次是为了让四舍五入之后仍然整块落在图内（越界会画不出来）。
    val sourceLeft = (centerX - sourceSide / 2f).coerceIn(0f, imageWidth - sourceSide)
    val sourceTop = (centerY - sourceSide / 2f).coerceIn(0f, imageHeight - sourceSide)

    CantaDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(
                onClick = {
                    // 和屏幕上完全同一个算法：中心 ± 半个边长，再归一化。
                    val cx = sourceLeft + sourceSide / 2f
                    val cy = sourceTop + sourceSide / 2f
                    onConfirm(
                        ImageCrop(
                            left = ((cx - sourceSide / 2f) / imageWidth).coerceIn(0f, 1f),
                            top = ((cy - sourceSide / 2f) / imageHeight).coerceIn(0f, 1f),
                            width = (sourceSide / imageWidth).coerceIn(0.02f, 1f),
                            height = (sourceSide / imageHeight).coerceIn(0.02f, 1f),
                        ),
                    )
                },
            ) { Text(stringResource(Res.string.ok)) }
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                modifier = Modifier
                    .size(viewport)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            // 屏幕位移换算成源图像素：取景窗一整个宽 = 当前 sourceSide 个源像素。
                            // zoom / pan 都是 Compose 状态，这里读到的是**此刻**的值，所以放大之后再拖
                            // 依然是跟手的（不会拿着 1× 时的夹取范围去算）。
                            val side = baseSide / zoom
                            val maxX = ((imageWidth - side) / 2f).coerceAtLeast(0f)
                            val maxY = ((imageHeight - side) / 2f).coerceAtLeast(0f)
                            val perPixel = side / viewportPx
                            panX = (panX + drag.x * perPixel).coerceIn(-maxX, maxX)
                            panY = (panY + drag.y * perPixel).coerceIn(-maxY, maxY)
                        }
                    },
            ) {
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset(sourceLeft.roundToInt(), sourceTop.roundToInt()),
                    srcSize = IntSize(sourceSide.roundToInt(), sourceSide.roundToInt()),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "拖动图片调整位置，滑杆缩放",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Slider(
                value = zoom,
                onValueChange = { zoom = it },
                valueRange = 1f..4f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
