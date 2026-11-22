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

> 本仓库**不支持本地构建**：没有 Android SDK，构建全部在 GitHub Actions 里完成
> （见 `AGENTS.md` 第 0 节）。要自己出包就 fork 后走 Actions，别在本地跑 `gradlew`。

## F-Droid

包名 `io.github.a303562915.unicanta`，[包页](https://f-droid.org/packages/io.github.a303562915.unicanta/)
在收录完成后生效。当前状态是**待提交**——源码已公开、许可证是 LGPL-3.0，仓库侧准备也齐了，
只差向 [fdroiddata](https://gitlab.com/fdroid/fdroiddata) 提合并请求；进度与步骤见 [F-Droid 上架](/fdroid)。
不接受 Google Play / IzzyOnDroid。

上架所需的仓库侧准备已经就位：

- `androidApp/build.gradle.kts` 关闭了 `dependenciesInfo`（可复现构建不写入依赖元数据）
- `metadata/en-US/`、`metadata/zh-CN/` 是 fastlane 结构的商店文案，F-Droid 直接读取
- recipe 草稿在 `metadata/fdroid/io.github.a303562915.unicanta.yml`
- 构建产物签名密钥不进仓库，F-Droid 用自己的密钥重签

> Shizuku 的更详细配置步骤见 [Android Police 教程](https://www.androidpolice.com/how-to-use-shizuku-for-adb-rootless-mods-on-any-android-device/)
