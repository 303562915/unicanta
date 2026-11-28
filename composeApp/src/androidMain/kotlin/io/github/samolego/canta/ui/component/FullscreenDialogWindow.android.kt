package io.github.samolego.canta.ui.component

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewParent
import android.view.Window
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
 *     是窗口底色（默认是主题色），所以还要第 2 项；
 *  2. 窗口底色刷黑：图片没铺满时（横图上下留白）那一圈也是黑的，不会露出白边；
 *  3. 状态栏 / 导航栏图标改成浅色：黑底上深色图标等于没有。
 *
 * 窗口从哪来：`DialogWindowProvider` 由 **Dialog 的根布局**实现（不是 Window 本身，见 Android
 * 官方文档 "Implemented by dialog's root layout"），所以要从当前节点往上找那个 View，再取
 * `window`。两层保险：先看直接的 parent（Compose 的 Dialog 就是这一层），找不到再沿整条 parent
 * 链走一遍（外面再包几层容器时）。
 *
 * 为什么用 `post` 而不是当场设：Dialog 的窗口是在组合之后才 attach 的，组合那一刻 parent 链可能
 * 还没接上，当场找会扑空；推一帧再设，decorView 一定已经在窗口里。
 *
 * 关掉 Dialog 时窗口一起销毁，Activity 那套配色（`followSystemBarColor`）不受影响，所以这里不必
 * 恢复什么。
 */
@Composable
actual fun FullscreenDialogWindow(backgroundIsDark: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, backgroundIsDark) {
        val apply = Runnable {
            val window = findDialogWindow(view)
            if (window == null) {
                LogUtils.w(TAG, "找不到这个 Dialog 的窗口，系统栏底下仍会露出页面底色")
                return@Runnable
            }
            runCatching {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))
                WindowInsetsControllerCompat(window, view).apply {
                    // 黑底要浅图标：`isAppearanceLightStatusBars = true` 是"深色图标"，别写反。
                    isAppearanceLightStatusBars = !backgroundIsDark
                    isAppearanceLightNavigationBars = !backgroundIsDark
                }
            }.onFailure { LogUtils.w(TAG, "全屏窗口没设上：${it.message}") }
        }
        view.post(apply)
        onDispose { view.removeCallbacks(apply) }
    }
}

/** 从 [view] 往上找实现 [DialogWindowProvider] 的那一层（Dialog 的根布局），拿到它的窗口。 */
private fun findDialogWindow(view: View): Window? {
    (view.parent as? DialogWindowProvider)?.let { return it.window }
    var parent: ViewParent? = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    // 兜底：有些实现把 provider 放在根布局上，而内容挂在更深的位置；`rootView` 的 parent 就是它。
    return (view.rootView.parent as? DialogWindowProvider)?.window
}

/** 日志标签：设不上时那一行会进「日志」页，不用靠猜"顶上为什么还是蓝的"。 */
private const val TAG = "FullscreenDialog"
