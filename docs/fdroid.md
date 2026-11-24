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
| recipe | ✅ `metadata/fdroid/io.github.a303562915.unicanta.yml`，已对齐 4.3.4 / 243 |
| MR 描述 | ✅ `metadata/fdroid/MR_DESCRIPTION.md` |
| 一键提交脚本 | ✅ `tools/fdroid/submit-mr.ps1` |
| **提交 MR** | ❌ 未提交——只差一个 GitLab token（见下） |

包页 `https://f-droid.org/packages/io.github.a303562915.unicanta/` 现在返回 404，这是正常的：
包在 fdroiddata 合并并构建成功之前不存在。

上游 `io.github.samolego.canta` 已经在 F-Droid 里（页面返回 200）。本应用包名不同、签名不同，
可独立收录，两者能同时在索引里，也能装在同一台手机上。

## 为什么还差一步

F-Droid 只接受 GitLab 上的 merge request，仓库是 <https://gitlab.com/fdroid/fdroiddata>。
本机有 `gh`（GitHub）但没有 GitLab 凭据，所以提交这一步需要你给一个 token。

## 一键提交

在 <https://gitlab.com/-/user_settings/personal_access_tokens> 建一个 token，scope 选 `api`
（`read_api` 不够，fork 和写文件都要写权限），然后：

```powershell
cd G:\trae\respositories\unicanta
$env:GITLAB_TOKEN = 'glpat-xxxxxxxxxxxx'

pwsh -File tools/fdroid/submit-mr.ps1 -DryRun   # 先看要提交什么，不联网
pwsh -File tools/fdroid/submit-mr.ps1           # 真提交：fork → 写 metadata → 开 MR
```

脚本会做的事：

1. 校验 recipe 与 `gradle.properties` 的版本号、`commit` 指向的 tag 已推送——不一致直接停，不会提交半成品。
2. `GET /user` 确认 token 有效、走 `$env:HTTPS_PROXY`。
3. fork `fdroid/fdroiddata`（已有 fork 就复用，fork 是大仓库，第一次可能几分钟）。
4. 在 fork 上建分支 `unicanta-new-app`（重名自动加 `-2`），写 `metadata/io.github.a303562915.unicanta.yml`。
5. 向上游开 MR，标题 `New app: unicanta`，正文取 `metadata/fdroid/MR_DESCRIPTION.md`，打印 MR 地址。

不 clone fdroiddata，全部走 GitLab API；不改本仓库，不推送本仓库。

被打回改一轮时：改 `metadata/fdroid/io.github.a303562915.unicanta.yml`，重跑脚本即可（会自动换分支重开 MR）。

## 上游通知：先不发，reviewer 要了再说

fdroiddata 的新 app MR 模板（`.gitlab/merge_request_templates/Default.md`）里有一条 checklist：

> The original app author has been notified (and does not oppose the inclusion).
> If you are not the author, please paste the link of the reply from the author.

**当前决定：不主动去 <https://github.com/samolego/Canta/issues> 发这条询问。** 判断是上游大概率
不会给"不反对"的回复，先发反而把话说死。

代价要认：这条 checklist 我们只能空着，MR 描述里已如实写明"尚未联系上游"，并承诺
reviewer 要求时立刻去开 thread 并贴链接。可能的结果：

- reviewer 不追这条 → 正常往下走（包名、签名都不同，不构成覆盖上游）；
- reviewer 追这条 → 那时才发 issue，正文在 `metadata/fdroid/UPSTREAM_NOTICE.md`，复制即可；
- 上游明确反对 → 这一单基本就到头了，只剩改应用名/放弃两条路。

`UPSTREAM_NOTICE.md` 留着当备用稿，不发出去。

## MR 里已经写清的三件事

1. **与上游的关系**：`samolego/Canta` 的分支，包名 `io.github.a303562915.unicanta` 与上游不同，可独立收录。
2. **反特性**：`AntiFeatures: Tracking`。应用内置匿名使用事件上报（自建端点、非第三方 SDK、可关），
   按 Inclusion Policy 如实标注。
3. **构建范围**：只出 Android 包。`gradle: - yes` 在根目录跑 `assembleRelease`，根 `build.gradle.kts`
   有委托任务转到 `:androidApp:assembleRelease`；`output:` 指到 `androidApp/build/outputs/apk/release/*.apk`；
   `prebuild` 删掉 `:webApp` / `:desktopApp` 两个模块，省掉 Node.js / Yarn / Binaryen 依赖。

## 时间预期

F-Droid 没有承诺的审核时限。经验值：MR 提出后 1–4 周有结论（被打回改一轮会再等），
合并后进入构建服务器、出现在索引里通常再要 3–7 天。顺利的话首次收录 2–4 周。

## 上架之后

- F-Droid 用自己的密钥重签 APK，与 GitHub Releases 分发的包签名不同，两者不能互相覆盖。
  应用已经识别这种情况：`CantaPlatform.isInstalledFromFdroid()` 命中时，
  设置页的「检查更新」与开机自动更新都会改为提示走商店，不再下载本仓库的 APK。
- 每次发版需要在 fdroiddata 里给 `Builds` 追加一条（`AutoUpdateMode: Version` 会自动提 MR）。
  发版后同步本仓库 recipe 的 `Builds` / `CurrentVersion` / `CurrentVersionCode`。
