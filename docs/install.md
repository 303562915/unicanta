---
title: Canta · unicanta - Installation
description: 安装 unicanta 分支的步骤。
---
# 安装

1. 安装 [Shizuku](https://shizuku.rikka.app/guide/setup/)
   - Android 16 及以上建议使用 [fork 版](https://github.com/thedjchi/Shizuku)
2. [激活 Shizuku](https://shizuku.rikka.app/guide/setup/)
3. 安装本仓库构建出的 APK：
   - [GitHub Releases](https://github.com/303562915/unicanta/releases/latest/)
   - [下载页](https://303562915.github.io/unicanta/)（稳定版 / 开发版可切换）
   - 或自行构建：`./gradlew :androidApp:assembleDebug`

## F-Droid

包名 `io.github.a303562915.unicanta`，[包页](https://f-droid.org/packages/io.github.a303562915.unicanta/)
在上架完成后生效，当前状态是**待收录**：本仓库源码为私有，而 F-Droid 只从公开源码复现构建，
因此在源码公开并提交到 [fdroiddata](https://gitlab.com/fdroid/fdroiddata) 之前不会上架。
不接受 Google Play / IzzyOnDroid。

上架所需的仓库侧准备已经就位，缺的只有“源码公开”这一步：

- `androidApp/build.gradle.kts` 关闭了 `dependenciesInfo`（可复现构建不写入依赖元数据）
- `metadata/en-US/`、`metadata/zh-CN/` 是 fastlane 结构的商店文案，F-Droid 直接读取
- 构建产物签名密钥不进仓库，F-Droid 用自己的密钥重签

> Shizuku 的更详细配置步骤见 [Android Police 教程](https://www.androidpolice.com/how-to-use-shizuku-for-adb-rootless-mods-on-any-android-device/)
