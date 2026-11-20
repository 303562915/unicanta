# AGENTS.md

面向在本仓库里工作的 AI agent / 协作者。**先读第 0 节，它覆盖后面所有内容。**

用户说不认识的快捷词（如 `ga`）时，见**第 8 节**：先按词表理解，猜错就把词和含义登记进去。

---

## 0. 铁律：只改代码，构建全部交给 GitHub Actions

本仓库**不提供也不支持本地构建**。你（agent）在本地只做一件事：改源码、提交、推送，然后等云端出结果。

**绝对不做：**

- 不跑 `./gradlew` / `gradle` / `sh gradlew`，任何 task 都不跑（包括 `--version`、`tasks`、`help`、`test`、`assemble*`）。
- 不安装任何工具链：JDK、Android SDK / build-tools / NDK / platform-tools、Kotlin、Node.js、npm / pnpm / yarn、Docker、Gradle 发行版、KVM 之类。
- 不 `apt install` / `brew install` / `sdkmanager` / `mise` / `asdf` / `nix` / `pip install` 去补齐构建依赖。
- 不改 `ANDROID_HOME` / `ANDROID_SDK_ROOT` / `JAVA_HOME`，不往 shell profile、`.env`、`local.properties` 里塞 SDK 路径来"让构建能跑"。
- 不下载 Gradle wrapper 分发包、不预热 `~/.gradle` 缓存。
- 不用"本地跑一下看看能不能编译"当作验证手段。验证手段只有一个：看云端 run 的结论。

**允许做：**

- 读 / 写 / grep 仓库里的源码、资源、文档、`.github/workflows/*.yml`。
- `git status` / `add` / `commit` / `push` / `log` / `diff` / `fetch` / `pull`（见第 3 节）。
- `gh` 查 run、看日志、下 artifact（见第 4、5 节）。
- 如果需要理解某个编译错误，读云端日志，而不是重建本地环境。

> 环境事实：本机 `/sdcard` 是 FUSE 挂载，`gradlew` 无可执行位，且 `ANDROID_HOME` 为空、没有 Android SDK。这是**设计状态**，不是待修的 bug。别去"修复"它。

---

## 1. 仓库与身份

| 项 | 值 |
|---|---|
| 本地路径 | `/sdcard/Aaaa/unicanta/Canta` |
| 远程 | `origin` → `https://github.com/303562915/unicanta.git` |
| 默认分支 | `master` |
| 构建身份 | `gh` 已登录 `303562915`（scopes: repo, workflow, read:org, gist） |

Gradle 模块：`:composeApp`（共享 UI/逻辑）、`:androidApp`、`:desktopApp`、`:webApp`（wasmJs）、`:packages`、`:helper`、`:hiddenApiStubs`。
版本号在 `gradle.properties`：`version_code=229` / `version_name=4.2.0`。

---

## 2. 工作流清单（唯一的构建入口）

全部在 `.github/workflows/` 下。

| 文件 | 触发 | 干什么 | 产物 artifact |
|---|---|---|---|
| `build.yml` 🏗️ Push Build | `push`（任意分支）、`workflow_dispatch` | 测试 → debug APK → release APK（有签名 secret 才做） | `test-reports`、`app-debug`、`app-release` |
| `fork-ci.yml` 🔧 Fork CI | `push`（任意分支）、`workflow_dispatch` | 先跑 `composeApp/util` 测试，再全量 desktopTest，然后 debug + minified release | `test-reports`、`app-debug`、`app-release` |
| `pull_request_build.yml` 🔍 PR Check | `pull_request` → `master`（opened / synchronize / reopened） | `:packages:desktopTest` + `:composeApp:desktopTest`，再 assembleDebug、desktopJar、wasm 分发 | `app-debug` |
| `release.yml` 📦🚀 | 仅 `workflow_dispatch` | 签名 release APK → 校验和 → attestation → 建 **draft** GitHub release | 上传到 release（`v$version_name`） |
| `docs.yml` | `push` 到 `master` 且改 `docs/**`、`release: published`、`workflow_dispatch` | 用最新 `v*` tag 的源码编 WASM，合并 docs 后发 GitHub Pages | `github-pages` |
| `supabase_migrations.yml` | `push` 到 `master` 且改 `server/supabase/migrations/**`、`workflow_dispatch` | `supabase link` + `db push` | — |
| `crowdin_escapes.yml` 🧹 | `push` 到 `l10n_master` | 清 Crowdin 转义后自动提交推送 | — |

关键 task（云端跑的，你**不要**在本地跑）：
`:composeApp:desktopTest`、`:packages:desktopTest`、`:androidApp:assembleDebug`、`:androidApp:assembleRelease`、`:desktopApp:desktopJar`、`:webApp:wasmJsBrowserDistribution`。

产物落点：`androidApp/build/outputs/apk/{debug,release}/`（含 `SHA256SUMS.txt`）。

---

## 3. 标准工作流：改完就推，等云端

```bash
cd /sdcard/Aaaa/unicanta/Canta

git status --short                 # 先确认改了什么
git add -A
git commit -m "fix: <一句话，说清修了什么编译/行为问题>"
git push origin master             # 不要 --force，不要推空提交试探
```

推送后**立刻**触发 `build.yml` 与 `fork-ci.yml`（同一 push 会同时跑这两套，见第 6 节）。

约定：

- 一次 push 只带一个有意义的改动，方便对照日志定位。
- 提交信息写"修了什么"而不是"改了哪些文件"；这个仓库的历史是「先连续几次 CI 失败、再一次性修好」的节奏，保持同样的可读性。
- 不要为了让 CI 变绿去删测试、跳过测试、加 `|| true`、加 `continue-on-error`、把 `needs` 去掉。要修的是代码。
- 不要 `git push --force`，不要 rebase 掉已推送的历史，不要直接改 `master` 上别人刚推的提交。

---

## 4. 取结果 / 取 APK

```bash
gh run list --limit 10
gh run watch <run-id> --exit-status          # 阻塞到结束，失败返回非零
gh run view <run-id> --log-failed            # 只看失败的步骤日志
gh run download <run-id> -n app-debug -D /sdcard/Aaaa/unicanta/artifacts
```

- 下载后看 `/sdcard/Aaaa/unicanta/artifacts/app-debug/SHA256SUMS.txt` 与 `*.apk`。
- 需要发版：`gh workflow run release.yml`，它会建 **draft** release，人工去网页确认后再 publish。
- 需要只跑构建不跑其它：`gh workflow run build.yml`（`workflow_dispatch` 手动触发）。

---

## 5. 排查失败：只读日志

1. `gh run view <run-id> --log-failed` 拿到报错段。
2. 按报错定位源码，改，回到第 3 节。
3. 如果错误来自 `composeApp` 的 Compose Multiplatform API 变动（这个仓库近期在跟 compose 1.12 的 API 移除，比如 `toPx()`、`ColorScheme.shadow`、`MinLensWidth` 的类型），优先按新 API 改写调用点，不要降级依赖版本去绕。
4. **不要**因为"想看完整堆栈"就在本地跑一次构建。

---

## 6. 已知问题与坑（改动前先看）

1. **重复构建**：`build.yml` 和 `fork-ci.yml` 都是 `on: push`，同一次推送会完整跑两遍（各约 8–9 分钟）。要提效就该合并或收窄触发条件（例如 `fork-ci.yml` 只在非 `master` 分支跑），但这属于工作流改动，需要显式确认后再动。
2. **`release.yml` 的 secret 名字对不上**：它读 `KEYSTORE_FILE_BASE64` / `KEYSTORE_KEY_ALIAS`，而仓库里实际配置的是 `CANTA_KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`。直接 `workflow_dispatch` 会因 keystore 为空而失败。发版前必须先统一两侧名字。
3. **`supabase_migrations.yml` 缺 secret**：需要 `SUPABASE_ACCESS_TOKEN`，当前仓库 secrets 里没有，触碰 `server/supabase/migrations/**` 的推送会让该 run 失败。
4. **过期的 action 版本**：`pull_request_build.yml` 与 `release.yml` 仍用 `actions/checkout@v3`、`actions/setup-java@v3`、`distribution: adopt`；`build.yml` / `fork-ci.yml` 已用 `@v4` + `temurin`。改这两个文件时顺手对齐。
5. **`release.yml` 里有已废弃写法**：`echo "::set-output name=tag::..."` 已不被支持，`VERSION_NAME` 实际走的是 `$GITHUB_ENV`。
6. **本地不可构建是刻意的**：`gradlew` 无执行位、无 SDK、`/sdcard` 挂载限制。别提交"修 gradlew 权限""加 local.properties"这类补丁。
7. `gradle.properties` 开了 `org.gradle.configuration-cache=true`，新增 Gradle 任务/插件若读取环境变量，需要在任务上声明输入，否则云端会因缓存命中而行为异常。

---

## 7. 提交前自检清单

- [ ] 没有新增任何"本地构建脚本"、`local.properties`、SDK 路径、工具链安装步骤。
- [ ] 没有改 `gradlew` 权限位或 wrapper 分发地址。
- [ ] 没有删/跳过测试，没有 `|| true`。
- [ ] 如果改了 `.github/workflows/*.yml`，确认 YAML 可直接解析、`needs` 依赖的 job 名存在、artifact 名与第 2 节表格一致。
- [ ] 如果改了 `gradle.properties` 版本号，说明这是有意的发版动作。
- [ ] 推送后盯到 run 结束，把结论（成功/失败 + run 链接）写进汇报，而不是"应该没问题"。

---

## 8. 用户快捷词：猜错就学，学完就记（强制）

用户会用他自己的**快捷词**说话。这些词在他这里有固定含义，但对你是陌生的。规则只有三条：

1. **优先按词表理解**。用户说出的词先查本节的表。表里有的，直接按表里的含义理解并动手，不要再问、不要再猜、不要往常见技术含义上套。
2. **猜错立刻纠正，不许含糊过去**。如果你猜了、猜错了、用户纠正了你，这一轮必须做到：承认理解偏了 → 按用户的解释重做 → **把词和含义写进下面的表**。不登记就当作这次纠正没发生。
3. **拿不准先猜，猜完必须落表**。不要因为不认识词就停下问"这是什么意思"；先按上下文猜一版执行，用户一纠正就回填词表。除非猜错会造成不可逆的破坏性操作（删数据、强推、发版），那种情况才先问一句。

登记要求：

- 只记**用户实际用过的词**，不预填、不臆造同义词。
- 一行一个词，写成 `| 词 | 含义 | 首次确认时间 | 备注 |`。
- 含义写**用户的原话意思**，不要改写成你以为更标准的术语；必要时把用户原句摘进备注。
- 同一个词后来含义变了，保留旧行并加一行新的，标 `（含义已更新）`，不要静默覆盖。
- 一有纠正就在**同一轮**里改这个文件，不要攒到最后、不要"下次再说"。

### 8.1 词表

| 词 | 含义 | 首次确认时间 | 备注 |
|---|---|---|---|
| `ga` | GitHub Actions —— 工作流相关的所有事（改 `.github/workflows/*.yml`、查 run、看日志、下载 artifact、发版触发） | 2026-11-20 | 不是 Google Analytics，不是 general availability。见到 `ga` 先想到工作流 |
| `action` / `actions` | 同上，GitHub Actions 工作流 | 2026-11-20 | 与 `ga` 同义 |

> 说明：`ga` 默认指 GitHub Actions，但**不是绝对**——若上下文明显在谈别的东西（如统计分析、灰度发布），以当轮上下文为准；判断依据写进备注。默认顺序：先 Actions，再看上下文。
