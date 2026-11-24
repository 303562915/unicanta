---
title: unicanta - F-Droid 上架
description: 把 unicanta 提交到 F-Droid 的步骤与当前进度。
---
# F-Droid 上架

## 当前状态

| 项 | 状态 |
|---|---|
| 源码公开 | ✅ `github.com/303562915/unicanta` 是 public |
| 许可证 | ✅ LGPL-3.0（`LICENSE`），F-Droid 接受 |
| 可复现构建准备 | ✅ `dependenciesInfo` 已关闭 |
| 商店文案（fastlane） | ✅ `metadata/en-US/`、`metadata/zh-CN/` |
| recipe | ✅ 草稿在 `metadata/fdroid/io.github.a303562915.unicanta.yml` |
| **提交 MR** | ❌ 未提交——需要 GitLab 账号（见下） |

包页 `https://f-droid.org/packages/io.github.a303562915.unicanta/` 现在返回 404，这是正常的：
包在 fdroiddata 合并并构建成功之前不存在。

## 为什么还差一步

F-Droid 只接受 GitLab 上的合并请求，地址是 <https://gitlab.com/fdroid/fdroiddata>。
本机没有 GitLab 凭据（无 `glab`、无 `GITLAB_TOKEN`），所以提交这一步需要你的账号。

## 提交步骤

1. 注册 / 登录 <https://gitlab.com>，把 <https://gitlab.com/fdroid/fdroiddata> fork 到自己名下。
2. 克隆 fork，把 recipe 放进去：

   ```bash
   git clone https://gitlab.com/<你的用户名>/fdroiddata.git
   cd fdroiddata
   cp <本仓库>/metadata/fdroid/io.github.a303562915.unicanta.yml \
      metadata/io.github.a303562915.unicanta.yml
   git checkout -b unicanta-new-app
   git add metadata/io.github.a303562915.unicanta.yml
   git commit -m "New app: unicanta"
   git push origin unicanta-new-app
   ```

3. 在 GitLab 上向 `fdroid/fdroiddata` 提 merge request。

## MR 描述里要写清的三件事

reviewer 一定会问，先说在前面能省一轮往返：

1. **与上游的关系**：这是 `samolego/Canta` 的分支，包名 `io.github.a303562915.unicanta`
   与上游 `io.github.samolego.canta` 不同，可独立收录；差异见 `README.md`。
2. **反特性**：应用内置匿名使用事件上报（`ComposeApp/src/commonMain/.../util/Telemetry.kt`），
   默认在某条件下开启、可在设置里关闭，且是自建端点而非第三方统计 SDK。
   按 Inclusion Policy 需要在 recipe 里加 `AntiFeatures: Tracking`，不要隐瞒。
3. **构建范围**：只构建 `:androidApp`，recipe 的 `prebuild` 里删掉了 `:webApp` / `:desktopApp`
   两个模块，避免连带下载 Node.js、Yarn、Binaryen 分发包。

## 时间预期

F-Droid 没有承诺的审核时限。经验值：MR 提出后 1–4 周有结论（被打回改一轮会再等），
合并后进入构建服务器、出现在索引里通常再要 3–7 天。顺利的话首次收录 2–4 周。

## 上架之后

- F-Droid 用自己的密钥重签 APK，与 GitHub Releases 分发的包签名不同，两者不能互相覆盖。
  应用已经识别这种情况：`CantaPlatform.isInstalledFromFdroid()` 命中时，
  设置页的「检查更新」与开机自动更新都会改为提示走商店，不再下载本仓库的 APK。
- 每次发版需要在 fdroiddata 里给 `Builds` 追加一条（`AutoUpdateMode: Version` 会自动提 MR）。
