package io.github.samolego.canta.ui.component

import androidx.compose.runtime.Composable

/**
 * 把一个**全屏 Dialog** 的窗口铺到系统栏底下，窗口底色刷成纯黑。
 *
 * 为什么需要它：`DialogProperties(usePlatformDefaultWidth = false)` 只去掉平台给的窄边，窗口自己
 * 仍然 `fitsSystemWindows` —— 于是状态栏那一条不被 Dialog 内容覆盖。全屏看图时顶上就露出一条底下
 * 那一页的颜色：个人主页是蓝色渐变封面，于是"看大图"看起来像顶栏没适配。
 *
 * 调用点在 Dialog 内容里，只对 Android 有实际动作（改的是这个 Dialog 自己的窗口）；桌面 / 网页的
 * 对话框本来就是独立窗口、没有系统栏，那边是空实现。
 *
 * 参数：
 *  * [backgroundIsDark]：窗口底色是不是深色。状态栏图标颜色要跟着翻——深底配浅图标，写反了在
 *    纯黑条上直接看不见。
 */
@Composable
expect fun FullscreenDialogWindow(backgroundIsDark: Boolean)
