---
title: unicanta - F-Droid 上架待办
description: 以后要上 F-Droid 时需要做完的事，按"硬门槛 / 流程 / 构建验证 / 上架后"分组。
---
# F-Droid 上架待办

> 2026-11-24 定的。当前决定：**暂时不提交**，等以后真要上架时照这份清单做完再跑
> `tools/fdroid/submit-mr.ps1`。进度与背景见 [F-Droid 上架](/fdroid)。

## 0. 先记住结论：不需要上游同意

F-Droid Inclusion Policy 的 fork 条款原文（`fdroid-website/_docs/Inclusion_Policy.md`）：

> **Forks:** Forked applications need to obtain a fresh Android ID that differs from the original
> application even if the source app exists outside F-Droid and they must receive corresponding
> name, icon and string changes including translation adjustments.

只要求四件：**新包名、新名称、新图标、新字符串（含翻译）**。没有"原作者同意"这一条。
MR 模板里那句 "author has been notified and does not oppose" 是模板配套文案，不是政策条款——
真被追问就把上面这段原文回给 reviewer。

## 1. 硬门槛（不做完一定被打回）

### 1.1 换图标 ❌ 未做

现状：`androidApp/src/main/res/mipmap-*/` 下 20 个 png 与 `samolego/Canta` 同名文件
**sha256 全部相同**（`ic_launcher.png` / `_foreground` / `_background` / `_monochrome` ×
mdpi / hdpi / xhdpi / xxhdpi / xxxhdpi）。这是 reviewer 一眼就能比出来的硬伤。

要做：

- [ ] 出一套全新图标：上面 20 个 png 全部替换，尺寸按密度
      mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192
- [ ] adaptive icon 的前景 / 背景 / monochrome 三层都要新图（前景 108dp 画布）
- [ ] `mipmap-anydpi-v26/ic_launcher.xml` 检查引用是否还指向旧资源名
- [ ] `metadata/en-US/images/icon.png`（F-Droid 商店图标，现在 12622 B）一起换
- [ ] 顺带看 `metadata/en-US/images/phoneScreenshots/` 里的截图是否还带上游品牌痕迹

验证：`Get-FileHash` 比一遍，别再有任何一个与上游相同。

### 1.2 清字符串 ⚠️ 部分未做

现状：`app_name` 已经是 `UniCanta` ✅，但界面文案里还有一批自称 "Canta" 的。
`values/`（英文）与 `values-zh-rCN/`（中文）**两份都要改**。

- [ ] `helper_notice` — "Canta places a small helper in /data/local/tmp…"
- [ ] `device_android_too_old` — "…which Canta doesn't support"
- [ ] `helper_unavailable` — "Couldn't set up Canta's helper…"
- [ ] `app_list_restricted_title` — "The system hides the app list from Canta"
- [ ] `app_list_incomplete` — "…the apps Android lets Canta see…"
- [ ] `whats_sent_description` — "This is an example of the data Canta sends."
- [ ] `grant_shizuku_permission_to_canta` — 值 "Grant permission to Canta in Shizuku"（key 名也带 canta，可一并改）
- [ ] 导入预设文案 — "…a valid Canta preset JSON"
- [ ] `onboarding_origin_step1_title` — "Upstream Canta"
- [ ] `onboarding_about_original_sub` — "samolego · upstream Canta"
- [ ] `cannot_uninstall_canta` — 彩蛋 "Can'ta ouch this!"
- [ ] `canta_description` — key 可改名；正文里 "Special thanks to %1$s" **建议保留**（许可证礼仪，不是品牌残留）

改完自查：`Select-String -Path composeApp/src/commonMain/composeResources/values*/strings.xml -Pattern 'Canta'`
应该只剩致谢那一处。

### 1.3 英文标题 ⚠️ 缺

`metadata/en-US/` 只有 `full_description.txt`、`short_description.txt`，**没有 `title.txt`**；
`metadata/zh-CN/` 有。F-Droid 英文列表会退回 recipe 的 `AutoName: unicanta`（全小写），
和应用内的 `UniCanta` 不一致。

- [ ] 补 `metadata/en-US/title.txt`，内容 `UniCanta`
- [ ] recipe 的 `AutoName` 同步成 `UniCanta`

## 2. 提交流程（材料已备好）

- [ ] GitLab token：<https://gitlab.com/-/user_settings/personal_access_tokens>，scope 选 **`api`**
- [ ] `$env:GITLAB_TOKEN = 'glpat-…'` 后先 `pwsh -File tools/fdroid/submit-mr.ps1 -DryRun`
- [ ] 真提交：`pwsh -File tools/fdroid/submit-mr.ps1`（fork → 写 metadata → 开 MR）
- [ ] recipe 版本号必须与 `gradle.properties` 一致、`commit` 必须指向已推送的 tag；
      脚本会自检，不一致直接停

上游通知：**决定不发**。如果 reviewer 追问，用 `metadata/fdroid/UPSTREAM_NOTICE.md` 的稿子
发到 <https://github.com/samolego/Canta/issues>，再把链接贴进 MR。

## 3. 构建侧未验证项（只能等 F-Droid 构建机试）

这些本地一律不跑（仓库铁律：不本地构建），出问题就按报错改 recipe：

- [ ] **Gradle 版本**：AGP 9.4.1 需要 Gradle 9.x；fdroidserver 用的是构建机的系统 `gradle`，
      版本不够就在 recipe 里加 `sudo` 安装，或降 AGP
- [ ] **JDK 21 toolchain**：`jvmToolchain(21)` + `foojay-resolver-convention` 在本机缺 JDK 时会
      自动下载，而 F-Droid 构建禁网 → 要确认构建机自带 21
- [ ] **SDK 平台**：`compileSdk`/`targetSdk` = 37，`minSdk` = 28；构建机能否装 android-37
- [ ] **根 `assembleRelease` 委托任务**能否在真实构建里拉起 `:androidApp:assembleRelease`
      （已在 `e0dcc68` 的 CI 里证明不破坏配置阶段，但没真跑过该任务）
- [x] `dependenciesInfo` 已关（可复现构建前提）
- [x] `prebuild` 已删 `:webApp` / `:desktopApp`，不需要 Node/Yarn/Binaryen
- [x] 无 keystore 时 release 未签名，由构建服务器重签

## 4. 上架之后

- [ ] 每次发版在 fdroiddata 给 `Builds` 追加一条（`AutoUpdateMode: Version` 会自动提 MR），
      同时同步本仓库 recipe 的 `Builds` / `CurrentVersion` / `CurrentVersionCode`
- [ ] `site/index.html` 里 F-Droid 徽标的 `<span class="tag">待提交</span>` 换成正式包页链接
- [ ] F-Droid 用自己的密钥重签，与 GitHub Releases 的包不能互相覆盖；
      `CantaPlatform.isInstalledFromFdroid()` 已处理（命中时更新改为提示走商店）
