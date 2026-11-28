package io.github.samolego.canta.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 预览里最大放大到几倍，与帖子查看器（`PostImage.kt` 的 `PostImageViewerMaxZoom`）同一档。 */
private const val AvatarViewerMaxZoom = 4f

/** 双击放大到几倍，与帖子查看器同一档。 */
private const val AvatarViewerDoubleTapZoom = 2.5f

/**
 * 头像的**全屏预览**：黑底、图片按 Fit 居中，单击关闭、双击在 1f 与 [AvatarViewerDoubleTapZoom] 之间切、
 * 放大后能拖动，捏合按 [AvatarViewerMaxZoom] 封顶。
 *
 * 为什么有它：头像在列表里最大也就是主页那圈 96dp，图和首字圆都小得读不出细节；用户想看清楚
 * 一个人传的照片时没有办法。这里不重新下载——[bitmap] 直接来自 [UserAvatar] 已经解码好的那一张
 * （图床头像是 256×256 的覆盖式对象，能拿到的最高分辨率就是它），所以点开是瞬时的、断网也能看。
 *
 * 参数：
 *  * [bitmap]：要放大的那张，已经解码好；
 *  * [name]：图片的无障碍描述，也是下面那行小字（换头像的缓存键之外，用户没别的东西可看）；
 *  * [onDismiss]：关闭出口，三个：单击任意处、右上角的 ×、返回键（Dialog 自带）。
 *
 * 与帖子查看器（`PostImageViewer`）的差别只在"什么都不加载"：那里是按地址取图，这里手里就有位图，
 * 所以没有加载/失败两态，也就没有占位框。
 */
@Composable
fun AvatarViewerDialog(
    bitmap: ImageBitmap,
    name: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // 只关掉 Dialog 自己的默认窄边。窗口"铺到系统栏底下"这件事在 common 里没有参数可设
        // （`decorFitsSystemWindows` 是 Android 版 DialogProperties 独有），所以由内容里的
        // [FullscreenDialogWindow] 到平台侧去改。
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 让这个窗口自己铺到状态栏底下：不然顶上露出一条底下那一页的颜色（个人主页是蓝色封面），
        // 看起来就是"顶栏没适配"。见 [FullscreenDialogWindow]。
        FullscreenDialogWindow(backgroundIsDark = true)
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val zoomed = scale > 1f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = AvatarViewerDoubleTapZoom
                            }
                        },
                    )
                }
                // 一层变换手势管"捏合缩放 + 放大后拖动"。这里没有 pager，单指拖动不用留给谁，
                // 所以不必像帖子查看器那样分两层挂。轻点不受影响：变换手势要过了 touch slop 才接手。
                .pointerInput(zoomed) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, AvatarViewerMaxZoom)
                        scale = next
                        offset = if (next <= 1f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )

            // 右上角的 ×：黑底上唯一常驻的按钮。点击任意处也能关，但那句话没法写在屏幕上；
            // 这里给它一个真正的热区（而不是只画个图标），点上去和点背景是一个结果。
            // 内容现在铺到了状态栏底下（见 FullscreenDialogWindow），所以按钮要让开那一条。
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "关闭", onClick = onDismiss)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            // 底部一行小字：图片是谁的。头像图的文件名就是身份号，屏幕上写清楚更好认。
            if (name.isNotBlank()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                )
            }
        }
    }
}
