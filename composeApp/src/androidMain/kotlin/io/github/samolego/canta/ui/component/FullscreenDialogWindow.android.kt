package io.github.samolego.canta.ui.component

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.ViewParent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.samolego.canta.util.LogUtils

/**
 * Android 侧：把这个全屏 Dialog 的窗口铺到系统栏底下。
 *
 * 三件事，缺一不可：
 *  1. `setDecorFitsSystemWindows(false)`：让内容真的能画进状态栏那一块。只改这一项时状态栏底下
 *     是窗口底色（默认是主题的白色/透明），所以还要第 2 项；
 *  2. 窗口底色刷黑：图片没铺满时（比如横图上下留白）那一圈也是黑的，不会露出白色边；
 *  3. 状态栏 / 导航栏图标改成浅色：黑底上深色图标等于没有。
 *
 * 关掉 Dialog 时窗口一起销毁，Activity 那套配色（`followSystemBarColor`）不受影响，所以这里不必
 * 恢复什么。
 */
@Composable
actual fun FullscreenDialogWindow(backgroundIsDark: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, backgroundIsDark) {
        // 从当前节点往上找：Dialog 内容里 `LocalView` 是 Dialog 的 decorView，它的消费方就是
        // `DialogWindowProvider`。中间加多层容器时第一层找不到，所以沿 parent 链走一遍。
        val provider = generateSequence<ViewParent>(view.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>()
            .firstOrNull()
        runCatching {
            val window = provider?.window
                ?: error("这个 Dialog 没有窗口（不是在 Dialog 里调用？）")
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))
            WindowInsetsControllerCompat(window, view).apply {
                // 黑底要浅图标：`isAppearanceLightStatusBars = true` 是"深色图标"，别写反。
                isAppearanceLightStatusBars = !backgroundIsDark
                isAppearanceLightNavigationBars = !backgroundIsDark
            }
        }.onFailure { LogUtils.w(TAG, "全屏窗口没设上：${it.message}") }
        onDispose { }
    }
}

/** 日志标签：设不上时那一行会进「日志」页，不用靠猜"顶上为什么还是蓝的"。 */
private const val TAG = "FullscreenDialog"
