package io.github.samolego.canta.core

/**
 * 头像要裁下哪一块：**归一化矩形**，按原图宽高各算各的（0..1）。
 *
 * 为什么是归一化而不是像素：给用户看裁剪框的那张预览图是**缩过的**（见
 * `ProfileScreen` 里先用 `compressImage` 压一份），它和原图的像素尺寸不一样；而真正下刀的是
 * 平台层手里那张原图。两边共用的只有比例，所以只传比例。
 *
 * 四个数各自独立，不假设是正方形——裁剪框在预览里是方的，按宽高各算一次之后落到原图上还是方的；
 * 平台那边（`CantaPlatform.compressAvatar`）取两者的短边当作最终方块，遇到浮点误差也不会当成
 * 一个长方形去裁。
 *
 * 没有裁剪信息时传 `null` 而不是这个类：`null` 的意思是"整图居中裁方"。
 */
data class ImageCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
)
