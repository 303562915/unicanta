# AGENTS.md

面向在本仓库里工作的 AI agent / 协作者。**先读第 0 节，它覆盖后面所有内容。**

用户说不认识的快捷词（如 `ga`）时，见**第 8 节**：先按词表理解，猜错就把词和含义登记进去。

---

## 0. 铁律：只改代码，构建全部交给 GitHub Actions

本仓库**不提供也不支持本地构建**。每次完成用户要求的修改后，检查改动、提交并推送到远端——**推送是必做动作，不是可选项**。

推送之后**直接结束**：**不派子智能体盯 CI，不蹲 `gh run watch`，不轮询 `gh run list`，不等 run 结论**。盯 run 是纯浪费时间——推完把提交号报给用户就算这一次任务完成。只有用户明确要求「看下 CI」，或已经知道某个 run 红了要排查编译错误时，才去读日志（第 5 节）。

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

> 环境事实：`gradlew` 无可执行位，且 `ANDROID_HOME` 为空、没有 Android SDK。这是**设计状态**，不是待修的 bug。别去"修复"它。
> 早前笔记里的 `/sdcard/...` 是那台**安卓设备**上的路径（FUSE 挂载）；当前检出在 Windows 上，路径见第 1 节。

---

## 1. 仓库与身份

| 项 | 值 |
|---|---|
| 本地路径 | 当前检出：`G:\trae\respositories\unicanta`（历史笔记里的 `/sdcard/Aaaa/unicanta/Canta` 是另一台设备，不要照抄） |
| 远程 | `origin` → `https://github.com/303562915/unicanta.git`（**canonical**，2026-11-22 用 `gh api` 核过）。若 `git remote -v` 显示 `Canta.git`，那是旧名，GitHub 目前会重定向，但别依赖它：`git remote set-url origin https://github.com/303562915/unicanta.git` 改回来 |
| 默认分支 | `master` |
| 构建身份 | `gh` 已登录 `303562915`（scopes: repo, workflow, read:org, gist） |

Gradle 模块：`:composeApp`（共享 UI/逻辑）、`:androidApp`、`:desktopApp`、`:webApp`（wasmJs）、`:packages`、`:helper`、`:hiddenApiStubs`。
版本号在 `gradle.properties`（当前 `version_code=239` / `version_name=4.3.0`）；发版前以文件为准，别抄这里的数字。

### Supabase 本地入口

涉及 Supabase API、数据库访问或迁移时，先查阅仓库根目录的 `SUPABASE.local.md`。该文档仅保留在本地，已由 `.gitignore` 排除；其他检出中缺少该文件是正常的。操作所需凭据从环境变量或密钥管理器获取，避免写入文档、源码或日志。

---

## 2. 工作流清单（唯一的构建入口）

全部在 `.github/workflows/` 下。

| 文件 | 触发 | 干什么 | 产物 artifact |
|---|---|---|---|
| `development-release.yml` 🧪 Development APK | `push` 到 `master` | 构建签名 APK 并发布滚动开发版 | GitHub prerelease `dev` |
| `build.yml` 🏗️ Push Build | `push`（任意分支）、`workflow_dispatch` | 测试 → debug APK → release APK（有签名 secret 才做；**master 上跳过**，签名 APK 由 dev release 出，避免同一份编译两次） | `test-reports`、`app-debug`（master 上无 `app-release`） |
| `fork-ci.yml` 🔧 Fork CI | `push`（**非 master** 分支）、`workflow_dispatch` | 先跑 `composeApp/util` 测试，再全量 desktopTest，然后 debug + minified release | `test-reports`、`app-debug`、`app-release` |
| `pull_request_build.yml` 🔍 PR Check | `pull_request` → `master`（opened / synchronize / reopened） | `:packages:desktopTest` + `:composeApp:desktopTest`，再 assembleDebug、desktopJar、wasm 分发 | `app-debug` |
| `release.yml` 📦🚀 | `push: tags: v*`、`workflow_dispatch` | 签名 release APK → 校验和 → attestation → 建 GitHub release（tag push 直接发布；dispatch 传 `keep_draft=true` 才是 draft） | 上传到 release（tag 用推上来的 `v*` tag 名，dispatch 时才用 `v$version_name`） |
| `docs.yml` | `push` 到 `master` 且改 `docs/**`、`release: published`、`workflow_dispatch` | 用最新 `v*` tag 的源码编 WASM，**只验证文档站能构建**（不部署，Pages 归 `pages.yml`） | `docs-site` |
| `pages.yml` 🌐 Deploy download page | `push` 改 `site/**` 或本文件、`release: published`、`workflow_run`（🧪 Development APK 成功）、`workflow_dispatch` | 把 `site/` + `latest.json` + `development.json` 推到 `gh-pages` 分支 | GitHub Pages：<https://303562915.github.io/unicanta/> |
| `apk-handoff.yml` 📤 APK Handoff | 仅 `workflow_dispatch`（`run_id`，`tag` 必须以 `ci-` 开头） | 把某个 run 的 `app-debug` 复制成预发布资产，不构建任何东西 | release 资产 |
| `daily-device-report.yml` 📊 Daily device report | 定时 + `workflow_dispatch` | 钉钉日报（读 `canta_users`；识别率口径见第 9 节）；也被 `pages.yml` 在部署完成后 `workflow_call` 调来发「已发布」通知（带本次更新内容） | — |
| `group-release-notice.yml` 📣 Group release notice | 仅 `workflow_call`（**只被 `pages.yml` 调**） | 下载页部署完成后用 `sender='ai'`（群聊AI）在内测灰度群插一条公告：版本、提交、本次更新、下载页、APK 直链、校验和；同时推 `last_message_at` | — |
| `bug-inbox.yml` 🐞 Bug inbox | `workflow_dispatch`（`conversation_id` / `limit` / `since_id`） | 读 Bug 反馈群的消息（JSON 打日志 + run summary），供 AI 整理问题；见第 13 节 | — |
| `bootstrap-bug-group.yml` 🐞 | `workflow_dispatch` | 一次性/幂等：建「UniCanta Bug 反馈」群并生成邀请码（已跑过，重复跑只会把群号与邀请码再打一遍） | — |
| `db-backup.yml` 🗃️ Community DB backup | 每小时 17 分（UTC，兜底）+ `workflow_dispatch`；实际准点由外部 cron 打 dispatch | 把**用户贡献的社区内容**导出成 JSON 提交进仓库，并给客户端发一份快照；**内容没变不提交**；见**第 16 节** | `db-backup`（14 天）+ 仓库里的 `ops/db-backup/`、`site/db/plans.json` |
| `crowdin_escapes.yml` 🧹 | `push` 到 `l10n_master` | 清 Crowdin 转义后自动提交推送 | — |

> 数据库那一侧（`server/supabase/**`、`server/test/**`、`server/README.md`）
> **只留在本地**，已在 `.gitignore` 里排除，不进公开仓库；迁移与查库在本地做，凭据见 `SUPABASE.local.md`。
> **推迁移没有 CI，唯一路径是手工走 Management API，步骤见第 10 节。**
> 本地备份在 `backups/db-local-only/`。**钉钉日报 `daily-device-report.yml` 照旧留在仓库里跑，不要删。**

关键 task（云端跑的，你**不要**在本地跑）：
`:composeApp:desktopTest`、`:packages:desktopTest`、`:androidApp:assembleDebug`、`:androidApp:assembleRelease`、`:desktopApp:desktopJar`、`:webApp:wasmJsBrowserDistribution`。

产物落点：`androidApp/build/outputs/apk/{debug,release}/`（含 `SHA256SUMS.txt`）。

### 2.1 更新通道与发版节奏（默认策略，不用再问）

**默认：更新最快的是开发版。** 用户说"推个更新""让用户装上最新的"这类默认诉求，一律走开发版
（`development-release.yml` → prerelease `dev`）：push 到 `master` 就自动出，不升版本号、不打 tag、不发稳定版。

**稳定版：每个自然日（Asia/Shanghai）最多一个。** 要做发版动作前，先查当天有没有已发布的稳定版：

```bash
gh api repos/303562915/unicanta/releases \
  --jq '.[] | select(.prerelease==false and .draft==false) | [.tag_name, .published_at] | @tsv'
```

- 当天**已经发过**稳定版 → 这一轮**不再发**，只保留开发版通道。
- 当天**还没发过**稳定版 → 发一个：`gh workflow run release.yml`（`keep_draft` 默认 false，即直接发布；只预演才传 `keep_draft=true`）。发完在同一轮把日期记下来，别在同一天发第二次。

判据是"发布日期 + `prerelease=false`"，不是版本号有没有涨：同一天即使 `version_name` 又变了，也不追加第二个稳定版。
稳定版发布后 `pages.yml` / `docs.yml` 会随 `release: published` 触发，这是预期行为，不用手动补。

---

## 3. 标准工作流：改完就推，推完收工

```bash
cd /g/trae/respositories/unicanta   # 当前检出（Windows 上就是仓库根，别照抄旧笔记的 /sdcard 路径）

git status --short                 # 先确认改了什么
git add -A
git commit -m "fix: <一句话，说清修了什么编译/行为问题>"
git push origin master             # 不要 --force，不要推空提交试探
```

推送后由 CI 跑 `build.yml`：master 上只跑这一套（`fork-ci.yml` 的 job 带 `if: github.ref != 'refs/heads/master'`，只在其他分支跑，见第 6 节）。

约定：

- 一次 push 只带一个有意义的改动，方便对照日志定位。
- 提交信息写"修了什么"而不是"改了哪些文件"；这个仓库的历史是「先连续几次 CI 失败、再一次性修好」的节奏，保持同样的可读性。
- 不要为了让 CI 变绿去删测试、跳过测试、加 `|| true`、加 `continue-on-error`、把 `needs` 去掉。要修的是代码。
- 不要 `git push --force`，不要 rebase 掉已推送的历史，不要直接改 `master` 上别人刚推的提交。

### 3.1 推送后：不盯 CI，推完收工

**改完 → 推送 → 报提交号 → 收工。** 没有「等 CI 绿了才算完成」这一步。

- **不派子智能体盯 CI**，`subagent` 不用在盯 run 上。
- 不 `gh run watch`、不轮询 `gh run list`、不 `gh run view --log`。
- 推完只报一句：改了什么 + 提交号（`git rev-parse --short HEAD`）。
- 例外（只这两种情况才碰 run）：① 用户明确说「看下 CI / 绿了没」；② 已知某个 run 红了要查编译错误，按第 5 节读 `--log-failed`。
- 出错也不用提前义务性排查——用户拿到开发版发现坏了，再回来查日志定位、修、再推。

---

## 4. 取结果 / 取 APK

```bash
gh run list --limit 10
gh run watch <run-id> --exit-status          # 阻塞到结束，失败返回非零
gh run view <run-id> --log-failed            # 只看失败的步骤日志
gh run download <run-id> -n app-debug -D artifacts
```

- 下载后看 `artifacts/app-debug/SHA256SUMS.txt` 与 `*.apk`。
- 需要发版：先按**第 2.1 节**确认当天还没发过稳定版；没发过才 `gh workflow run release.yml`（`keep_draft` 默认 false → 直接发布；只预演才传 `keep_draft=true`）。
- 需要只跑构建不跑其它：`gh workflow run build.yml`（`workflow_dispatch` 手动触发）。

---

## 5. 排查失败：只读日志

1. `gh run view <run-id> --log-failed` 拿到报错段。
2. 按报错定位源码，改，回到第 3 节。
3. 如果错误来自 `composeApp` 的 Compose Multiplatform API 变动（这个仓库近期在跟 compose 1.12 的 API 移除，比如 `toPx()`、`ColorScheme.shadow`、`MinLensWidth` 的类型），优先按新 API 改写调用点，不要降级依赖版本去绕。
4. **不要**因为"想看完整堆栈"就在本地跑一次构建。

---

## 6. 已知问题与坑（改动前先看）

下面第 1–5 条是历史上记在这里的坑，**现在都已经在仓库里修好**，留作对照：再看到别处的旧笔记说"会失败"，以这里的现状为准。

1. **重复构建（已修）**：`fork-ci.yml` 的 job 带 `if: github.ref != 'refs/heads/master'`，master 推送只由 `build.yml` 跑一遍，不再双跑。
2. **`release.yml` 的 secret 名（已对齐）**：它现在读 `CANTA_KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`，与仓库已配置的 secrets 一致，发版不再因 keystore 为空而失败。
3. **数据库那一侧不进仓库（已迁出）**：`server/supabase/**`、`server/test/**`、`server/README.md`
   只留在本地并已 gitignore。`supabase_migrations.yml` **已经不存在了**（2026-11-22 核实：磁盘上没有、
   索引里也没有，只剩 `.gitignore` 里一条过时规则），所以任何"推送自动 `db push`"的说法都不成立；
   改 schema 只能手工推，步骤见**第 10 节**。
   **`daily-device-report.yml`（每日钉钉日报）不在此列，它留在仓库里正常跑。**
4. **action 版本（已统一）**：所有 workflow 都是 `actions/checkout@v4` + `actions/setup-java@v4` + `distribution: temurin`。
5. **`::set-output`（已移除）**：仓库里已无该写法，`VERSION_NAME` 走 `$GITHUB_ENV`。
6. **本地不可构建是刻意的**：没有 SDK、没有 `ANDROID_HOME`、`gradlew` 无执行位。别提交"修 gradlew 权限""加 local.properties""装 SDK"这类补丁。（旧笔记里的 `/sdcard` 挂载限制是**安卓设备**上的事，与当前 Windows 检出无关。）
7. `gradle.properties` 开了 `org.gradle.configuration-cache=true`，新增 Gradle 任务/插件若读取环境变量，需要在任务上声明输入，否则云端会因缓存命中而行为异常。

---

## 7. 提交前自检清单

- [ ] 没有新增任何"本地构建脚本"、`local.properties`、SDK 路径、工具链安装步骤。
- [ ] 没有改 `gradlew` 权限位或 wrapper 分发地址。
- [ ] 没有删/跳过测试，没有 `|| true`。
- [ ] 如果改了 `.github/workflows/*.yml`，确认 YAML 可直接解析、`needs` 依赖的 job 名存在、artifact 名与第 2 节表格一致。
- [ ] 如果改了 `gradle.properties` 版本号，说明这是有意的发版动作。
- [ ] 已推送；汇报里只有「改了什么 + 提交号」，不需要 CI 结论。

---

## 8. 用户快捷词：猜错就学，学完就记（强制）

用户会用他自己的**快捷词**说话。这些词在他这里有固定含义，但对你是陌生的。规则只有四条：

1. **优先按词表理解**。用户说出的词先查本节的表。表里有的，直接按表里的含义理解并动手，不要再问、不要再猜、不要往常见技术含义上套。
2. **猜错立刻纠正，不许含糊过去**。如果你猜了、猜错了、用户纠正了你，这一轮必须做到：承认理解偏了 → 按用户的解释重做 → **把词和含义写进下面的表**。不登记就当作这次纠正没发生。
3. **拿不准先猜，猜完必须落表**。不要因为不认识词就停下问"这是什么意思"；先按上下文猜一版执行，用户一纠正就回填词表。除非猜错会造成不可逆的破坏性操作（删数据、强推、发版），那种情况才先问一句。
4. **默认输入是语音转写来的，先按读音还原意图**。用户的消息常常是**语音识别转文字**后发出来的，中文尤其容易出同音错字、漏字、断句错，技术词也会被写歪（例：`ga` 被识别成「尬」、`刷票` 识别成「刷嫖」、`方案` 识别成「放案」、`部署` 识别成「布署」）。所以：
   - 先把句子在心里「念一遍」，想这句话说出来像什么，再对照词表和当前任务判断，**不要按字面死磕**。
   - **不因为错字就说看不懂、也不要让用户重发**。自己还原一版接着做；只有真歧义会让动作做错时才问一句「你是不是指 X」。
   - **错写不单独登记成新词**。词表记的是用户原意，语音转写造成的不同写法当作同一行的别名处理（写进备注）；反过来，用户投来的怪词先假设是转写误差，不要急着当新快捷词。
   - 涉及路径、包名、SQL、命令这类**必须逐字准确**的东西，别照抄用户口述的版本，用仓库里的真实文件/表名核对一遍再动手。

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
| `ga 开发版` / `最快推送` | 默认更新通道走开发版：由 GitHub Actions 的 `development-release.yml` 在 push `master` 时自动出 prerelease `dev`，不等稳定版 | 2026-11-24 | 用户原话：「默认都是最快推送是更新的，ga 开发版」。配套节奏见第 2.1 节：稳定版每天最多一个 |
| `action` / `actions` | 同上，GitHub Actions 工作流 | 2026-11-20 | 与 `ga` 同义 |
| `型号` / `机型显示不对` / `他那个vivo和三星` / `他展示的是这个型号` | 「选择机型」列表里的机型芯片显示的是**零售编号**（`SM-F9710`、`PHK110`、`V2337A`）而不是营销名（`Galaxy Z Fold8`、`一加 Ace 2`、`vivo X Fold3 Pro`） | 2026-11-21 | 用户原话：「它展示的是这个型号，对吗？」「我感觉不太对，只有小米才是正确的。和荣耀是正确的」→ 按第 9 节查 |
| `调用他们的日志` / `他们上传的日志` / `数据库里面他们上传的这个` | 后台数据库里 App 上传的机型/设备数据（`plans.market_name`、`plans.codename`、`canta_users.model`、`community_comments.device_model`），不是客户端日志文件 | 2026-11-21 | 用户原话：「你可以调用他们的日志让他们上传上来呀」「如果你这次解决不了，那你下次就去找这个文件，就找数据库里面他们上传的这个日志去分析他们手机是什么」 |
| `刷票` / `刷到45` / `刷到N` | 直接往 `recommend_suggestion_votes` 灌认同票，把某条推荐条目的 agree 数刷到指定值，再 `private.recommend_settle(url)` 让它 `applied`。**不是**把 `private.recommend_votes_needed()` 的门槛数字改小 | 2026-11-21 | 曾猜成「降门槛 50→45」被纠正 |
| `认同` / `认同票` | 推荐投票里的赞成票，即 `recommend_suggestion_votes.agree = true`；反义是「不认同」（`agree = false`） | 2026-11-21 | 同上，用户原话「把那个认同两个票给他刷到45」= 认同票数从 2 变成 45 |
| `影视星球` | 推荐条目 `recommend_suggestions.id = 1`：`name='影视星球'`、`url=https://github.com/303562915/tv`、`author='开发者'`（用户自己那个影视 App 仓库） | 2026-11-21 | 同上。刷票对象就是这一条 |
| `方案` / `方案详情` | 社区里用户提交的**精简方案**，即 `public.plans` 的一行（`plans.title` 是方案名）；「方案详情」= 该方案的详情页（`plan_get`） | 2026-11-21 | 用户原话：「方案详情里面有一个…再一次精简他这一个方案」 |
| `站` / `他现在的站只有两个` / `刷到83` | 方案详情里的**票数**，即 `plan_votes` 里该 `plan_id` 的行数（`plan_get().votes`）；「站只有两个」= 票数很少，「刷到83」= 直接插 votes 行把票数补到 83 | 2026-11-21 | 与「刷票」同义，对象是 `plans`/`plan_votes` |
| `再一次精简` | 某个用户方案的标题名（如 `plans.id=35`） | 2026-11-21 | 用户用方案标题指代该方案 |
| `修正历史` / `描述修正` | 应用详情页里「描述修正」的列表，即 `description_fixes_for(pkg)` 读的 `public.description_fixes` 行（带 `agrees`/`disagrees`/`applied`）；「拉到50」= 往 `description_fix_votes` 灌 `agree=true` 补到 50，「直接通过」= 调 `private.description_fix_settle(pkg)` 让 `applied=true` | 2026-11-21 | 用户原话：「把修正历史的有一条…你把他那个票给他拉到50，直接通过」 |
| `级别修正` / `这个投票` | 应用详情页并排两张卡里右边那张（`ui/screen/LevelFixScreen.kt`）：三个维度（级别 / 后果 / 系统应用）的投票页，读写 `public.level_fix_ballots`。用户说的「加个不知道」= 三个维度各多一项 `unknown`：不进百分比分母、不参与结算、不下发（见 `.github/sql/level_ballot_unknown.sql`） | 2026-11-29 | 用户原话：「级别修正 应用详情 这个投票的应该加个不知道 选项，不然人家选错了怎么办」 |
| `指定代理` / `你为指定代理agent` / `帮我管理这些回话` | 让当前会话当**多会话跟进者**：管共享台账 `session-tasks/`、给其他会话发任务、验收通过才允许归档，每 10 分钟汇报一次 | 2026-11-24 | 用户原话：「你为指定代理agent,帮我管理这些回话，你去跟进，大部分可以处理，每隔10分钟汇报情况给我」。完整流程见第 14 节。注意「回话」是语音转写的「会话」 |
| `默认是一开的` / `节拍`（含义已更新） | **反过来：默认不开节拍。** 每 10 分钟轮询查台账和 CI 太烧 token、太耗时间；改成事件驱动（CI 出错才介入、状态变了才汇报、用户问才给快照），只有用户明确要求才开定时汇报 | 2026-11-24 | 用户原话：「Action 给它关掉又这样感觉会不会浪费 token？如果出现错误，我们让你主代理去干活，行不行？或者你去分配一个子代理……不用每个任务都去监控，这样太耗时间」。已把第 14.6 整节改成事件驱动 |
| `新电话` / `开启新的电话` | 语音转写的**新会话**（不是打电话）；「新会话应该会建立他的这个文档」= 新会话开工要自动建 `session-tasks/` 档案 | 2026-11-24 | 用户原话：「如果我开启新的电话，他应该会建立他的这个文档吧」。已据此把第 14.1 改成硬规则 |
| `他这个` / `他那个` | 指代**上一轮提到的那款第三方软件/方案**，不是指人、不是指某台设备 | 2026-11-22 | 曾据「他这个不行吗」核实 Dhizuku 卸载能力，我先前结论错了 |
| `开源项目` / `关于页面的那个开源项目` | 关于页（`AboutScreen`）里那一行「开源项目」入口（原本 `about_source_repo` + `REPO_URL`）；用户要求把它改成「最新版本下载」，链接用 `https://303562915.github.io/unicanta/` | 2026-11-24 | 用户原话：「不是让你把关于页面的那个 开源项目 改为最新版本下载 https://303562915.github.io/unicanta/ 这个链接吗」。我先前误以为指的是「更新检查的数据来源」（改成了读站点 json），被这句纠正；现在那一行是 `about_latest_download` + `DOWNLOAD_PAGE_URL` |
| `print` / `打印` | 用户**看得见**的提示（toast）；不是 logcat / 控制台日志 | 2026-11-25 | 用户原话：「所有的那个print都给它改成toast」。理解为：该让用户知道的结果要弹 toast，而不是只写日志。技术日志（`LogUtils` / `PlatformLog`）不算 `print`，不用改 |

> 说明：`ga` 默认指 GitHub Actions，但**不是绝对**——若上下文明显在谈别的东西（如统计分析、灰度发布），以当轮上下文为准；判断依据写进备注。默认顺序：先 Actions，再看上下文。

---

## 9. 机型名显示：零售编号 vs 营销名

用户说的「型号不对」「怎么显示的是这个型号」，指的是**机型芯片标题显示零售编号而不是营销名**。链路如下，四层依次回退：

1. **UI**：`CatalogModel.label` / `Plan.deviceLabel` / `DeviceProfile.displayModel` 都是 `marketName.ifBlank { model }`
   （[DeviceCatalog.kt](composeApp/src/commonMain/kotlin/io/github/samolego/canta/data/community/DeviceCatalog.kt)）。
2. **后端** `plan_catalog()`：`plans.market_name` → `private.model_names`（评论区学到的名字）→
   `private.device_names`（人工/迁移种下的名字）→ 同 codename 的别的行借一个 → `''`
   （种子迁移 `20261121150000_device_name_seed.sql`，只在本地 `server/supabase/migrations/` 下）。
3. **上传**：`plans.market_name` 只有上传那台手机自己报才有，由 `marketNameOf(props)` 读系统属性
   （[DeviceContext.kt](composeApp/src/commonMain/kotlin/io/github/samolego/canta/util/DeviceContext.kt) 的 `MARKET_NAME_PROP_KEYS`）。
4. **各厂属性不同**：
   - 小米 / 荣耀：`ro.product.marketname` 有 → 正常，芯片显示名字。
   - vivo / iQOO：`ro.vivo.market.name`；OPPO / realme / 一加：`ro.oppo.market.name`、`ro.vendor.oplus.market.name`（中）、`ro.vendor.oplus.market.enname`（英）。这些键不读，上传就是空。
   - 三星 One UI：**没有名字属性**，机器里只有 `SM-xxxx`；只能靠查表。

**再遇到「某台机器显示编号」，按这个顺序处理**（不要先去翻客户端日志，那里没有）：

```sql
-- 1. 谁没有名字（凭据与连法见 SUPABASE.local.md + 第 10.1 节）
select brand, model, coalesce(codename,'') as codename, rom, rom_version, sdk
  from plans where coalesce(market_name,'') = '' order by brand, model;
```

2. 有 codename / 型号 → 查这台机器到底是什么（三星型号可查 `doc.samsungmobile.com/SM-XXXX`）。
3. 没属性可读的（三星这类）：往 `private.device_names` 插一行即可，**不用发版**：

```sql
insert into private.device_names (model, market_name, source)
values ('sm-xxxx', 'Galaxy ...', 'manual')
on conflict (model) do update set market_name = excluded.market_name, updated_at = now();
update plans p set market_name = d.market_name
  from private.device_names d
 where coalesce(p.market_name,'') = '' and lower(p.model) = d.model;
```

4. 属性名对不上（vivo/一加这类）：改 `MARKET_NAME_PROP_KEYS`，手机下次上传/评论就自带名字；
   已上传的旧行靠第 3 步补。
5. 验证：`select brand, model, market_name from plan_catalog() where model in (...);`

**日报「总人数 / 有型号」怎么读**：这两个数出自 `canta_users`，不是 `plans`。`canta_users.model`
以前只有打开「社区/安全规则」页触发 `user_login` 才写，所以 2026-11-21 实测 594 人里 549 人空
（92%），那个「有型号 45」= 打开过社区页的人数，**不是**识别率。从 `20261121160000_identity_bind_model.sql` 起 `identity_bind` 随身份一起收 `model`/`codename`，装机即上报（迁移只在本地）。识别率要看 `plans`：
`select count(distinct model) filter (where coalesce(market_name,'') <> '')::float / count(distinct model) from plans;`

---

## 10. 数据库迁移：没有 CI，只有手工一条路

`.github/workflows/supabase_migrations.yml` **不存在**（2026-11-22 核实：磁盘上没有、git 索引里也没有），
`server/supabase/**` 整个不进仓库。所以「推送迁移 → CI 自动 `db push`」这件事**从来没有发生，也不会发生**。
改 schema 的唯一路径是手工推，下面是实际用过、验证过的那一套。

### 10.1 连上去（本机没有 supabase CLI / psql / docker）

```powershell
$root = 'G:\trae\respositories\unicanta'
$txt  = Get-Content (Join-Path $root 'SUPABASE.local.md') -Raw
$pat  = [regex]::Match($txt,'sbp_[0-9a-f]+').Value
$ref  = [regex]::Match($txt,'Project Ref\s*\|\s*`([a-z0-9]+)`').Groups[1].Value
$h    = @{ Authorization = "Bearer $pat"; 'Content-Type' = 'application/json' }
function Q($sql) {
  $b = @{ query = $sql } | ConvertTo-Json -Compress
  Invoke-RestMethod -Uri "https://api.supabase.com/v1/projects/$ref/database/query" `
    -Method Post -Headers $h -Body $b -Proxy $env:HTTPS_PROXY
}
Q 'select 1 as ok'   # 先证明：地址通、token 对、代理没坏
```

`api.supabase.com` 必须走 `$env:HTTPS_PROXY`，否则报 SSL 握手失败。

**多语句是支持的，而且走隐式事务（2026-11-23 实测）**：把整段 SQL 拼成一条 `query` 发过去，Postgres 在
simple query protocol 下把它们当**一个隐式事务**执行——中途任一条报错则全部回滚。实测：发
`create table _atomic_probe; select 1/0;` 之后表不存在，证明原子。所以推迁移时**整包发一次**，
不要逐条发：逐条发会在 `drop function` 和 `create function` 之间留出空窗，那段时间线上调这个函数就是 404。
整包发没有空窗，也不可能留下半成品。

> 早前这一节写的是「一次请求只能带一条语句」，那是错的（从未验证过）。真正需要注意的是**拆语句用于核对**
> （看每条边界对不对），不是用于发送。

### 10.2 推一个迁移：七步，顺序不能换

1. **备份要被替换的东西**：函数就
   `Q "select pg_get_functiondef('public.xxx(sig)'::regprocedure) as d"`，写进
   `backups/db-local-only/<name>_before_<date>.sql`。
2. **数行数**：`Q 'select count(*) from public.<table>'`，把数字记下来。
3. **核对语句边界**：带 `$$ … $$` 的 `create or replace function` **不能按分号硬拆**。
   按顶层关键字切块（`create or replace function …` / `revoke …` / `do $$ … $$;`）**只是为了核对**
   每条边界对不对、有没有漏掉 `grant`、`$$` 是否配成对——**不是**为了逐条发送。
4. **整包发一次**（见 10.1）：把整个文件当一条 `query` 发过去。隐式事务保证要么全成、要么全回滚，
   中间没有 `drop → create` 的空窗。**不要逐条发**，那会把空窗留到线上。
5. **再数一遍行数**，必须和步骤 2 一样。不一样就是出事了，立刻用步骤 1 的备份回滚。
   `canta_users` 例外：它是真实装机表，会自己增长，要按 `created_at` 判断是注册还是迁移副作用。
6. **验定义真的换了**：`pg_get_functiondef(...)` 里能搜到新逻辑的关键字；或查
   `pg_proc` 的 `pg_get_function_identity_arguments` 看新参数在不在。
7. **空跑一次**：拿明显无效的入参调一次，确认走的是拒绝分支、不抛异常。
   注意有些函数对空参数是 `raise exception` 而不是返回 false（比如 `message_send` 的空正文），
   那也算走对了分支，但要看清异常是不是**你自己改出来的**。挑无副作用的入参（假号码 + 不存在的 id）。

### 10.3 不要做的事

- 不用 `delete` / `drop` 来「重置后重建」：`plans`、`community_comments`、`canta_users` 是真数据。
- 不把 `server/supabase/**` 加进仓库来「让 CI 推」——那是把 live 服务的 schema 公开。
  真要走那条路是独立决定，先算清代价：git 历史永久留痕，推上去撤不回来。
- `server/supabase/ops/` 里的脚本会删数据（`purge_plans` 清空全部方案与方案评论），
  只在明确要清空时手工跑。

### 10.4 写接口限速（2026-11-22 已上线）

`private.write_rate` + `private.throttle_write(bucket, max, window_seconds)`，按**来源 IP**
计数（`x-forwarded-for`，取不到就 `cf-connecting-ip`，都没有记成 `unknown`）。

九个写函数开头各多了一行 `perform private.throttle_write('<bucket>', <max>, 60)`：
`plan_submit` 5、`comment_add`/`comment_delete`/`description_fix_vote`/`recommend_suggestion_vote`
各 20、`plan_vote`/`package_vote` 各 30、`comment_like` 40、`inbox_seen_mark` 60（每分钟）。

要点：

- **按 IP 不按自报身份**：九个函数的身份全是客户端自报（`p_voter` / `p_owner` / `p_author_id`
  / `p_plan->>'owner'`），按自报值限速等于没限。
- **不改签名、不改返回、不改既有分支**，所以现有客户端一行不用动。
- **只管 RPC 层**：直接连库灌行（手动运维、`insert`）不受影响——那些没有 `request.headers`。
- 变量不能叫 `ip`：`private.write_rate` 有同名列，PL/pgSQL 报 42702 ambiguous。
- 改函数的做法：先用 `pg_get_functiondef` 取线上原文、插一行、再 `create or replace` 回去；
  原文备份在 `backups/db-local-only/functions-before-throttle/`。

### 10.5 身份 token 收口（2026-11-23 第一批已上线）

`20261123110000_identity_token_write.sql`：`private.identity_of(token)` + 17 个写函数（社交 14 + 方案/投票/评论 3）
末尾加 `p_token text default ''`，身份取 `coalesce(private.identity_of(p_token), 自报值)`。

- **必须是 drop 旧签名再建新的**，不能用 `create or replace`：多一个参数就是新重载，PostgREST 会报
  `Could not choose the best candidate function`。drop 带走 grant，末尾的 DO 块会重发 anon/authenticated。
- **灰度靠两件事撑着**：`p_token` 有默认值（老客户端不带也能调）、`identity_of` 查不到返回 **null 而不是抛错**
  （认不出来就退回自报）。**别把这两条改掉**，否则线上老客户端直接挂。
- **顺序固定：服务端先、客户端后**。客户端接线在 `CantaApp.kt` 的两个仓储构造（`identityToken = { DeviceIdentity.ensure(...) }`，
  提交 `7cc728c`）。反序会让新客户端在旧库上报 `function ... does not exist`。
- **还没收的**：`comment_like`、`package_vote`、`description_fix_add`、`description_fix_vote`、
  `recommend_suggestion_add`、`recommend_suggestion_vote` 六个仍信自报，配方同上。
- **还没做"强制"**：现在认不出仍退回自报。要收紧得等所有在线客户端都升级完之后单独一轮。
- 回滚材料：`backups/db-local-only/functions-before-identity-token/functions_before_20261123.sql`（17 个函数原文）。

---

## 11. dependabot 告警：先分级，再动手

2026-11-22 的教训：`ws` 那条 high 告警（dev server 的传递依赖）花了 5 轮交互、4 次 CI
才关掉，而当天的真问题（删评论越权，能删别人的评论）只用了两三步。**严重度标签不等于优先级，
要按"有没有进用户的运行路径"分级。**

### 11.1 三类，处理强度完全不同

| 类 | 判断 | 处理 |
|---|---|---|
| **进运行路径** | `composeApp` 的 `wasmJsMain` npm 依赖（`@yume-chan/*`）、`androidMain`/`commonMain` 的库、`helper` | 认真修，带验证 |
| **只在构建/dev 工具链** | `ws`、webpack 那层、yarn 自己、`kotlin-js-store/**` 里的锁 | **一步升得上就升；升不动就记成已知项，不追** |
| **只在测试依赖** | `kotlin-test`、`ktor-client-mock`、pglite | 不管 |

判据一句话：**这个包会不会出现在用户手机或用户的浏览器里加载的产物中？** 不会，就当第二类。

### 11.2 第二类的成本上限（硬规则）

- 最多 **2 次 CI 试错**。第 3 次之前必须已经从文档/源码确认了 API 名，不能靠盲改试。
- 仍升不动 → 在本文档第 6 节加一行「已知项 + 为什么升不动 + 影响面」，**收工**。
- 不要为它建分支跑全量验证；本地读源码能确认的，不要推 CI 试。

### 11.3 `kotlin-js-store/wasm/yarn.lock` 的两条铁律

1. **不要手改**。`kotlinWasmStoreYarnLock` 会校验，改了必红：
   `Lock file was changed. Run the kotlinWasmUpgradeYarnLock task`。
2. 要抬版本只能：① 在 `composeApp/build.gradle.kts` 里加 yarn resolution，
   且必须挂 **wasm** 的扩展 —— `withType<...targets.wasm.yarn.WasmYarnPlugin>()` +
   `the<...targets.wasm.yarn.WasmYarnRootExtension>()`（挂 js 的 `YarnPlugin` 编译能过但**不生效**）；
   ② 跑 `wasm-lock-upgrade.yml` 拿到工具链重算的锁，下载后提交。
   注意 `settings.gradle.kts` 里 `rootProject` 是 `Settings` 不是 `Project`，那段写不进去。
3. 摘掉锁里某条再重算，只能用来**证明**范围不接受新版本，不是修法。

---

## 12. 应用更新：检查 → 下载 → 安装（百分比、进度条、诊断上报）

用户说的「观影页面 / 关于页面那个检查更新」指的就是这一套：入口是**「关于」页**（`AboutScreen`）的
「检查更新」卡片，加上 `CantaApp.kt` 里的启动自动更新。代码与数据链路如下，改之前先读这一节。

### 12.1 文件与职责

| 位置 | 干什么 |
|---|---|
| `ui/screen/AboutScreen.kt` | 关于页的更新卡片、下载/安装的调用方、进度条与阶段文字 |
| `ui/screen/UpdateChecker.kt` | 查发布（API 失败退到 release 页）、`UpdateState` 状态机、`downloadRelease*` |
| `core/CantaPlatform.kt` | `downloadUpdate` / `installUpdate(path, onStage)` / `requestInstall`，`InstallStage` 枚举 |
| `androidMain/.../AndroidCantaPlatform.kt` | 真正的下载（断点续传、三次重试）与 `pm install` 四条通道 |
| `util/UpdateReport.kt` + `util/PackageTelemetry.recordUpdate` | 上报载荷与发送入口 |
| `server/supabase/migrations/20261124140000_update_reports.sql` | 表 `update_reports` + RPC `update_report` + 视图 `update_failure_summary`（本地文件，手工推） |

### 12.2 百分号只有一个：`%%` 在 Compose 里不会转义（2026-11-24 修）

Compose Multiplatform 的字符串格式化**只认 `%N$s` / `%N$d`**，用的是 `replaceWithArgs` 的正则
`%(\d+)\$[ds]`，**不认识 `%%` 转义**——资源里写 `%1$d%%`，屏幕上就是 `42%%`。

所以：

- `about_downloading` 只写到 `%1$d`，**百分号在代码里拼**（`AboutScreen` 的 `"...${stringResource(...)}%"`）。
- 任何新增的带百分比文案都照这个来；不要在 `composeResources/values*/strings.xml` 里写 `%%`，
  也不要依赖 Android 的 `String.format` 转义规则。
- 同理别写 `%1$02d` 这种带宽度/补零的格式，格式化器不认，会原样显示。

### 12.3 进度条：下载有百分比，安装只有阶段

- **下载**：`UpdateState.Downloading(progress)`，`progress` 是 0–100，`DOWNLOAD_PROGRESS_UNKNOWN(-1)`
  表示服务器没给 `Content-Length`（这时画不确定进度条，不画数字）。
- **安装**：`UpdateState.Installing(version, path, stage, percent)`。`pm install` 本身不报进度，
  所以只有 `InstallStage.PUSHING`（base64 分块推到 `/data/local/tmp`，大包最慢的一段）有真实百分比，
  其余阶段 `INSTALL_PROGRESS_UNKNOWN`，用不确定进度条 + 阶段文字（`about_install_stage_*`）。
- 要加阶段：`InstallStage` 加枚举值 → `AndroidCantaPlatform.installUpdateLocked` 里 `onStage(...)` →
  `AboutScreen.installStageLabel` 加一行字符串映射（`values/` 与 `values-zh-rCN/` 都要加）。

### 12.3.1 顶栏那条常驻进度（2026-11-25 加）

进度以前只画在「关于」页那张卡片里，人在应用列表/消息/帖子页时什么都看不见，看起来像「点了没反应」。
现在：

- `ui/update/UpdateProgress.kt` 是一个 `internal object`，只存一份 `active: Item?`（阶段 + 版本 +
  `fraction`）。`UpdateCenter.state` 的 setter 每次赋值都调 `UpdateProgress.sync(value)` ——
  **改状态只有那一处**，卡片和顶栏读的是同一份，不会一个在动一个不动。
- `ui/fl/FlTopBar.kt` 读 `UpdateProgress.active`，在顶栏下面画一行小字（`update_progress_downloading`
  / `update_progress_installing`）加一根 `FlProgressBar(progress = fraction)`。所有用 `FlTopBar`
  的页面（主界面 `CantaTopBar`、二级页 `ScreenTopBar`）都自动有；`ChatScreen` 那几个自绘顶栏没有。
- 百分比来自 `UpdateState.Installing.shownPercent()`，与关于页同一份判断；量不出长度时
  `fraction = null`，只画不确定进度条，不编数字。百分号在代码里拼（见 12.2）。
- 「该让用户知道的结果都要 toast」= 第 8 节词表里的 `print`。所以 `installUpdateLocked` 里每条通道
  被拒时，除了 `LogUtils.w` 还 `toast("$channel 装不上：…")`（`toast()` 会 `withContext(Dispatchers.Main)`
  —— Toast 只能在有 Looper 的线程上 show）。

### 12.4 诊断上报：每一步结束都写一行到 `update_reports`

**为什么有它**：「更新装不上」以前只能靠问当事人屏幕上写了什么。现在每次检查/下载/安装结束都上报一行，
按机型 + 阶段 + code 就能直接看出是哪一步、为什么、在哪些机器上。

- 客户端：`PackageTelemetry.recordUpdate(stage, outcome, channel, version, code, reason, detail,
  progress, bytes, elapsedMs)` → `Telemetry.sendUpdateReport` → `POST /rest/v1/rpc/update_report {payload}`。
- 上报点：`AboutScreen`（检查失败/成功、下载成功/失败/取消、安装成功/失败、交给系统安装器）、
  `CantaApp`（启动自动下载成功/失败、自动安装成功/失败）。
- **不受「分享匿名卸载数据」开关控制**：那个开关是分享"卸了哪些系统包"，这条只含应用自己的版本、
  失败 code、阶段、机型与 ROM，没有 token、没有账号、没有包列表。所以它照发；别再给它加 `analyticsEnabled` 判断。
- 载荷版本 `v=1`，后端 `update_report` 会校验 stage/outcome 并把每条文本截断（reason 400 / detail 2000）。
- 表只经 RPC 写入（RLS 开、无 policy、`revoke all from anon/authenticated`），读取只有运维。

查问题（连库方式见 10.1，`Q '...'`）：

```sql
-- 最近失败的都长什么样
select received_at, stage, outcome, code, model, rom, rom_version, sdk, canta_version, reason
  from update_reports where outcome = 'failed' order by received_at desc limit 50;

-- 哪台机器、哪一步最容易挂（现成视图）
select * from update_failure_summary where outcome = 'failed' order by reports desc limit 50;

```

code 取值：检查 `UpdateFailure`（`RATE_LIMITED`/`TIMEOUT`/`NETWORK`/`SERVER`/`NO_RELEASE`/`MALFORMED`）、
下载 `UpdateDownloadFailure`（`NETWORK`/`TIMEOUT`/`STORAGE`/`UNSUPPORTED`/`UNKNOWN`）、
安装 `REFUSED_ALL_ROUTES`（四条通道全拒，`detail` 是每通道自己的一句）、
`NOT_READY`（运行时那一刻没就绪：没在运行 / 没授权 / 没响应；**不**算这一版装不上，等 20 秒自己再试，
最多 2 次），`handoff` 表示交给了系统安装器。

### 12.4.1 「下载了却不自动装」的两个真因（2026-11-25 修）

`InstallAttempt.retryable` 就是为这两件事分出来的，改这块前先看：

1. **把「环境没就绪」当成「这个包装不上」**。旧逻辑任何一次失败都写 `skippedAutoInstallVersion`
   并把包甩给系统安装器 —— 启动那一刻 Shizuku 还没起来就够钉死一版，用户看到的正是
   「下载了、不自动装，关于页写着上次没成功」。现在只有 `pm` 真拒绝（`INSTALL_FAILED_*`、包名不符）
   才进这个兜底。
2. **`binder.transact` 没有超时**。Shizuku 用户服务半死不活时它永不返回，界面就停在
   「正在安装 … 90%」（90 是 `InstallStage.INSTALLING` 的固定档，见 `shownPercentage`）。
   现在 `ShizukuShell.run` 有 `timeoutMs`（默认 30 s，`pm install` 180 s），超时就丢 binder、报
   「没有响应」。

另外 root 那条路以前用 `suBinaryPath() != null` 当门槛，而 Magisk/KernelSU 的 `su` 在文件系统上
看不见 —— 于是一台「别的功能都走 root」的手机，自更新却报「这台手机没有 su」。门槛统一成
`rootMayBeAvailable()`（`su` 文件或 root 管理器 App），并且 root 装不动时先 `cp` 到
`/data/local/tmp` 再 `pm install`（`pm` 读不了 FUSE 上的 `Android/data`）。

### 12.4.2 「Shizuku 没有响应」= 用户服务绑不上，先看是不是两个连接（2026-11-25）

`NOT_READY` 的报告里三条通道同时写「Shizuku 没有响应」，这一点**不是**「Shizuku 没启动」：那句话
只在 `pingBinder()` 为真、`getUid() != -1`（也就是已授权）时才会出现，含义是
`Shizuku.bindUserService` 两轮都没把 `ShellUserService` 连上。三个已知来源，按排查顺序：

1. **同一进程里两个 `ShizukuShell`**（已修）：`AndroidCantaHandler` 与 `AndroidCantaPlatform` 以前各
   `lazy { ShizukuShell(context) }` 一个，等于同一份用户服务上挂两条 `ServiceConnection`——应用列表/
   卸载那条能跑，自更新那条偏偏绑不上。现在只有 `ShizukuShell.shared(context)` 一个实例。
2. **Shizuku 的用户服务进程被系统拦/杀**（HyperOS 省电策略最常见）。
3. **管理器太老 / 是只支持 `newProcess` 的分支**：13.1 起才有 `bindUserService`。

为了不必再靠人描述屏幕，`ShizukuShell` 现在把绑不上的原文（`bindUserService 失败：…` /
`用户服务 N 秒内没连上（第 k 轮）` + `Shizuku uid=… ping=… preV11=… manager=<版本>`）放进
`TweakRun.output`，由 `refusalText` 拼成「Shizuku 没有响应（…）」，所以它**会出现在
`update_reports.detail` 里**；同一句话也进了安装失败的 toast（`about_install_failed`，只取第一行）。
看报告就能判是版本、权限还是进程起不来。

### 12.5 排查顺序（别一上来就翻客户端日志）

1. 先查 `update_reports` 最近同机型的行，看 `stage` + `code` + `detail`。
2. `stage=check` 且 `RATE_LIMITED`/`NETWORK`/`TIMEOUT` → 代理/出口 IP 问题，不是应用 bug。
3. `stage=download` 且 `NETWORK`/`TIMEOUT` + 平均进度很低 → 节点断流；`STORAGE` → 磁盘满。
4. `stage=install` 且 `REFUSED_ALL_ROUTES` → 读 `detail` 里每个通道的原文；`INSTALL_FAILED_UPDATE_INCOMPATIBLE`
   多半是签名不同（F-Droid 装的那个），`Shizuku 没在运行` 是运行时掉了。
5. 版本号没变但用户说「没更新」→ 看 `stage=check/outcome=ok` 的 `version`，再看 `skippedAutoInstallVersion`
   （自动安装失败过的版本会被记下来，停在关于页手动安装）。

### 12.6 日志页的「更新」筛选：在手机上看这一整轮更新（2026-11-27 加）

以前「某条通道报了错，但最后还是装上了」只能靠后台 `update_reports` 反推：屏幕上是一串
「××装不上」，然后对话框没了。现在这一整轮更新在**日志页**里能自己看：

- 更新链路所有日志统一用 `LogUtils.UPDATE_TAG`（`"Update"`），两边一起写：`UpdateCenter`
  （检查 → 决定 → 下载 → 安装的开始/阶段/结果）与 `AndroidCantaPlatform`（下载重试、清旧包、
  每条通道的尝试与拒绝原文、`pm 说失败但包已被替换`）。
- 日志页顶部有两个 chip：「全部」「更新」。选「更新」只剩这条链路，时间线式的：检查 → 下载 →
  试通道 → 哪条装上了 / 交系统安装器。
- **落盘**：`UpdateLogFile.android.kt` 把 `Update` 开头的行同步追加到 `filesDir/update-log.txt`
  （超过 256 KB 才截断到 400 行）。理由是安装成功那一刻 `pm install` 会把进程换掉，内存日志
  （`LogUtils.logs`）跟着消失，只看内存永远看不到最后那几行。日志页「更新」筛选里那些带
  `·上次` 后缀的条目就是从这份文件读回来的（上次运行）。桌面/网页没有这条需求，`nonAndroid`
  里是空实现。
- 想在手机上定位一句报错的出处：日志页 → 更新 → 展开那一行（点击）+ 长按复制，整段原文就是
  `update_reports.detail` 里的同一份。

### 12.7 通道顺序按「能不能成」排：Q+ 上 `pm` 读不了 FUSE（2026-11-28 改）

`pm install` 的执行者是 **system_server**，Android 10 起 SELinux 不允许它读 FUSE：

```
avc: denied { read } for scontext=u:r:system_server:s0 tcontext=u:object_r:fuse:s0 tclass=file permissive=0
Error: Can't open file: /storage/emulated/0/Download/Canta/canta-dev-726.apk
Consider using a file under /data/local/tmp/
```

所以 `/storage/emulated/…` 上的两条通道（`应用目录` / `下载目录`）在 Q+ 上**注定失败**——以前它们排在
前面，每次更新就是「两条红字 toast + 最后靠临时目录装上」。现在按能不能成排：

1. Q+ 先 `stageInDownloads` 拿到公共路径，`installViaTmpCopy`：**一次 `cp`** 到 `/data/local/tmp`
   再 `pm install`（30 MB 几秒；以前是 base64 分块推送，几百次 binder 往返）；
2. `cp` 不行（MediaStore 不可写 / shell 读不到公共目录）→ `installViaTmp`（base64 分块推送，慢但通）；
3. 都不成，才用 `tryChannel(quiet = true)` 试 FUSE 那两条（放开 fuse 的定制 ROM），
   **日志里留原文、屏幕上不弹 toast**；
4. 全不成 → root（有 su 且失败像权限问题）→ 系统安装器。

Q 之前没有 scoped storage，保持原顺序（直接装最快）。`tryChannel(channel, quiet = true, …)` 是这轮
新加的开关：「预期内会被拒」的通道只写日志。一次成功时 `attempts` 为空，所以 `announceSuccess` 一个字
都不说——正常更新全程零红字。

---

## 13. Bug 反馈群：用户贴问题，AI 自己读、自己修

群：`conversations.id = 18`「UniCanta Bug 反馈」，邀请码 `ceaeb0a30182`，群主 `created_by = 10003`。
客户端里它在 `OfficialGroup.kt` 的 `OfficialGroups.entries` 第二项 —— 消息页最上面那一行，点一下
`group_invite_join`（幂等）就进群。灰度测试群是 `id = 12`，发版公告也发在那里（见第 2 节
`group-release-notice.yml`）。

**读群消息**（本机到 `api.supabase.com` 不通，所以一律走 CI）：

```bash
gh workflow run bug-inbox.yml -f conversation_id=18 -f limit=50      # 增量：再加 -f since_id=<上次最大 id>
sleep 20; gh run list --workflow bug-inbox.yml --limit 1 --json databaseId,status,conclusion --jq '.[0]'
gh run view <id> --log | sed -n '/--- json ---/,$p'                  # id / sender / sender_name / kind / created_at / body
```

**处理约定**：

1. 每条按「机型 + 系统版本 + 现象 + 复现步骤」归类；同一现象合并成一条，别按消息条数算 bug 数。
2. 能复现、能定位的改代码修（照第 3 节：改完推 master，推完即收工，不盯 CI）。
3. 修完在群里回一句结论（`sender='ai'`、`sender_name='群聊AI'`，直接往 `public.messages` 插一行
   并推 `conversations.last_message_at`，写法与 `group-release-notice.yml` 里那段 SQL 一致）。
4. 修不了的（需要用户机型复现、缺日志）就在群里问，问清再动手；不要留一个"已处理"的假状态。

**建群/换群**：`bootstrap-bug-group.yml` 是幂等的（群名存在就只读回群号与邀请码），改名或重建时
改它里面那句 `title` 再跑一次，然后把新邀请码同步进 `OfficialGroups.entries`。

### 13.1 所有注册的人默认都在两个官方群里，且默认置顶（2026-11-24 上线）

- **存量**：`auto-join-official-groups.yml` 把 `canta_users` 每个身份都补进 `12`（灰度）和 `18`（Bug 反馈），
  `conversation_members.pinned_at = now()`。跑完实测：用户 2328 人，两群各 2328 成员、2328 置顶。
- **以后新人**：`canta_users` 上挂了 `AFTER INSERT` 触发器 `canta_users_auto_join` →
  `private.auto_join_official_groups()`，注册即入两群并置顶。整块吞异常，入群失败不会让注册失败。
  用触发器而不是改 `identity_bind`，是为了不动既有登录函数。
- 「置顶」就是 `conversation_members.pinned_at` 非空，`conversations_for` 的排序是
  `pinned desc, last_message_at desc`，所以客户端不用改。
- 再要加官方群：改 `auto-join-official-groups.yml` 里那两处 `values (12::bigint), (18::bigint)` 再跑一次。


---

## 14. 多会话任务管理：共享台账 + 指定代理跟进

多个会话并行干活时，谁在做什么、做完没做完、验收过了没，不靠人脑记，全部落到一个共享文档文件夹里。
文件夹叫 `session-tasks/`（仓库根，**不要**放进 `docs/`，那是文档站构建目录）。

```
session-tasks/
├── README.md                     # 总索引：一行一个会话，谁负责、几条任务、几条完成、归档没有
├── _TEMPLATE.md                  # 单会话档案模板，新会话复制一份改名用
├── <会话ID>-<简述>.md            # 每个会话一份自己的档案，只有它自己写自己的
└── archive/                      # 全部验收通过后，由指定代理把档案移到这里
```

### 14.1 每个会话维护自己的档案（强制）

- **开工第一动作（不是"迟早要做"，是"第一件事"）**：任何会话收到用户第一条指令，先 `ls session-tasks/`；
  没有自己那份档案就复制 `session-tasks/_TEMPLATE.md` → `session-tasks/<会话ID>-<简述>.md` 填满再干活。
  **没有档案就干活 = 违规**，指定代理一律按「未开工」处理，下发的第一句就是「先建档」。
  判据是文件名里含本会话 ID 或会话名（`lead`、`ci-watch` 这类 agent 名也算），不必逐字等于 session id。
- **档案按事件更新，不按节拍**：有状态变化（任务开始/完成/卡住、拿到 CI 结论）才写；收到指定代理指令时立刻回填。
  台账长期空着不行，但也不为了「证明在运行」每 10 分钟刷一行（见 14.6）。
- **只有这个会话自己写自己的档案**：任务状态、验收证据、卡点，都写在里面，别写进别人的档案。
- 指定代理只做三件事：读所有档案、维护 `README.md` 索引、给各会话发指令。**指定代理不改别人档案里的任务状态**。
- **指定代理兜底**：每轮汇报用 `list_agents` 对一遍「有会话没档案」，缺的立刻 `send_message` 要求补建；
  新会话第一次出现在 `list_agents` 里而台账没有它，这一条必须在当轮汇报里点出来。
- 台账默认只在本地维护，状态变更不逐次提交（避免每 10 分钟一次脏提交）；用户明确要求时才 `git add`。

档案必含字段（照模板填，不许空着）：

| 字段 | 内容 |
|---|---|
| 会话标识 | session id、会话名、负责 agent、开始时间、最近更新 |
| 任务清单 | 编号 / 任务 / 状态 / 验收标准 / 验收结论 / 证据 |
| 跟进记录 | 时间 / 指定代理下发的指令 / 本会话回复 / 下一步 |
| 卡点 | 需要用户拍板的事，写清楚等什么 |

状态只有四个值：`待办`、`进行中`、`已完成`、`未完成（附原因）`。没有"差不多"。

### 14.2 验收口径

写「已完成」必须同时满足四条，缺一条就写「未完成」：

1. 改动真的落地了（代码/文档/配置在仓库里，不是口头说改完）；
2. 按第 3 节推送过（推送即完成，不要求 CI 结论）；
3. 任务写下的验收标准逐条对得上；
4. 证据写进档案：提交号、`gh run` 链接或 run id、关键命令输出。

### 14.3 归档（= 删除权限）：只有指定代理，且必须全绿

- **只有指定代理可以归档/移走档案**。任何会话都不许自己删、自己挪自己的档案。
- 前置条件：该档案里**所有**任务 `已完成` 且验收结论全部通过。不是"大部分过了"。
- 删除按 A2 执行：**不做真删**，把档案 `move` 到 `session-tasks/archive/`，同时在 `README.md` 索引里标 `archived` + 日期。
- 没全绿 → 指定代理**不许归档**，必须继续向该会话的 agent 发任务（同机 `send_message`，跨机 `remote_send_message`），要求修改调整，直到全绿为止。

### 14.4 触发方式：用户开口指定

用户新开一个会话，说这类话：

> 你为指定代理 agent，帮我管理这些会话，你去跟进，大部分可以处理，每隔 10 分钟汇报情况给我

这一句就是开关。听到就**直接进入本流程**，不要反问细则：

1. 当前会话角色 = **指定代理**；其余会话 = **被跟进会话**。
2. 立刻 `list_agents`（跨机再 `remote_list_sessions`）把所有会话列出来，逐个建档案、写 `README.md` 索引。
3. **不建定时节拍**，按 14.6 的事件驱动走：CI 出错才介入，会话状态变了才汇报，用户问才给快照。

### 14.5 指定代理的动作循环

1. **读**：扫一遍 `session-tasks/*.md`，只看状态、验收结论、卡点。
2. **挑**：把「待办 / 进行中 / 未完成」的会话挑出来。
3. **发指令**：每条指令说清三件事——要改什么、验收标准是什么、什么时候回。一次只发一件，别群发糊成一坨。
4. **自己能做的直接做**：台账维护、只读排查、查 CI、核对证据，这些指定代理自己处理，不推回用户。
5. **留痕**：在对应档案的「跟进记录」块里记一行（时间 / 指令 / 回复 / 下一步）。
6. **全绿收口**：所有档案都归档后，发最后一次总结，流程结束。

### 14.6 汇报节奏：事件驱动，不轮询（2026-11-24 修订）

**不要建每 10 分钟的定时汇报。** 那样每 10 分钟就要扫一遍台账 + 查一次 CI，token 全烧在「没变化」上，
用户明确否掉了这种做法（原话：「不用每个任务都去监控，这样太耗时间」「会不会浪费 token」）。

改为按事件触发：

- **CI 出错才介入**：`gh run list` 发现 `failure` → 才读 `--log-failed` → 交主智能体修。绿的不报。
  推送后**不盯 run**（第 3.1 节）：推完即收工，不派子智能体蹲守，也不轮询。
- **会话有事件才汇报**：某会话任务从「进行中」变「已完成/未完成」、或提出卡点时，汇报一次。
  没有状态变化就不发消息。
- **用户问才汇报**：用户说「汇报一下」时，扫台账 + `list_agents` + CI，按下面的格式给一次完整快照。
- 真要开节拍（用户明确要求时才开）：`schedule_create` + `RRULE:FREQ=MINUTELY;INTERVAL=10`，
  并写明何时该关——不用的节拍必须 `schedule_edit` `active: false` 关掉。

被问到时的汇报格式（固定）：

```
[汇报] HH:MM 共 N 个会话
- 会话A：3/5 完成；未完成：xxx（等用户确认）；本轮已下发：改 yyy
- 会话B：5/5 完成，验收通过 → 已归档
卡点：需要你决定的 1 件（xxx）
```

### 14.7 边界

- 指定代理**不改**其他会话正在改的代码，避免并发覆盖；它只发指令 + 维护台账。
- 被跟进会话必须在**自己档案**里写证据，不写等于没做，指定代理按未完成处理。
- 台账不进 `docs/`，避免触发 `docs.yml` / `pages.yml` 的站点构建。

---

## 15. 后端容量：全站「一片空白」怎么看

**遇到「页面全空」，先在本地读备案文档，别先怀疑客户端：**
`ops-notes/backend-incident-2026-11-25.md`（**本地文件，已 gitignore，不在公开仓库里**；
换机器/新检出没有它是正常的）。根因、实测数据、验过的查询配方、备份纪律、时间线全在那里，本节只留速查。

### 15.1 一句话根因

2026-11-24/25 三次全站不可用的真因是**资源耗尽**，不是磁盘坏：

```
swap peaked at 557 MB on an instance with 426 MB of memory
burst balance reached 0%, I/O wait peaked at 118.6%
```

`shared_buffers 224MB` 塞不进 426 MB 内存 → swap（swap 就在数据盘上）→ checkpoint `write=` 从毫秒变
60–148 秒 → 查询超 8 秒 `statement timeout` → 连接占满 → PostgREST 对所有人 500/503/504。

**当时误判成盘坏**：SQL 日志只见 `write()` 卡住、`sync` 仅 3 ms —— 这形状也可能是 swap 拖累。
判别证据在 Dashboard **Observability → Compute**，不在 SQL 日志。
**小算力的 Disk IOPS 图只统计数据盘，swap 卡顿在图上可能正常**，要看 CPU iowait 与 memory/swap。

### 15.2 已改的免费参数（会影响将来判断基线）

| 参数 | 现在 |
|---|---|
| `shared_buffers` | **96MB**（原 224MB；整库仅 44MB，够装两遍） |
| `max_connections` | **30**（原 60） |
| `checkpoint_timeout` | **15min**（原 5min） |
| `max_wal_size` | **2GB**（原 1GB） |

改内存参数会**自动重启实例**（`FATAL: 57P03: shutting down` + `terminating connection due to administrator command`
属正常，别当成故障）。只走 `PATCH /v1/projects/{ref}/config/database/postgres`；
`bgwriter_*` / `checkpoint_completion_target` 改不了（`Unrecognized keys`），别浪费时间。

> **仍未根治**：参数压到 Free 档极限后，checkpoint `write=` 仍会回到 50 秒级。
> 真正的治本是**降请求量**（客户端侧，见 15.3）。

### 15.3 待办：客户端降请求量（根治项）

24h 实测 49.6 万请求 / 41.7 MB 出口（出口远低于 5GB 免费额度 → 瓶颈是次数不是流量）：

| 路径 | 次数/24h | 要做的事 |
|---|---|---|
| `HEAD /rpc/` | **105,330** | 后端探测 → 改「出错后才探」，别常驻 |
| `inbox_summary_kinds` | 44,941 | 轮询 30s → 60~90s |
| `inbox_seen_mark` | 40,932 | 每会话一次 → **合并批量** |
| `conversations_for` / `package_vote_counts` / `plans_for` / `recommend_suggestions` / `comments_for` | 2.4–3.6 万 | 一律轮询放慢 |

聊天已有 Realtime 广播（`ef2230d`），轮询只是兜底，可放很慢；**别**在 `onResume`/切 tab 重拉，先看缓存。
另需：`package_scores()`（194ms）、`plan_catalog()`（74ms）加热点结果缓存；前端加「后端忙」态而不是白页。

### 15.4 判定五步（顺序别乱）

1. Dashboard → Compute：Memory / Swap / CPU iowait —— swap 非零或 iowait 高 = 资源耗尽，不是代码。
2. `postgres_logs` 搜 `checkpoint complete` —— `write=` 两位数秒 = 盘/swap 拖累。
3. Management API 跑 `select 1` —— 超 8 秒 = 连接池被慢查询拖住。
4. `postgrest_logs` 按 `source='postgrest_logs'` 过滤 —— **有请求 + 5xx = 后端问题；没请求 = 客户端/链路**。
5. 本机连不上时走 CI：`gh workflow run supabase-log-probe.yml -f source=postgrest_logs -f minutes=15`。

**别**因为「页面空」先怀疑 `ScreenCache` / `AppListSnapshotStore`：它们只造成「先画旧数据再刷新」。

### 15.5 看门狗：留，但别指望它

`backend-watchdog.yml` 保留（断路器：挂了自动重启，把「4 小时白屏」压到「几分钟」）。
钉钉正文**必须**带关键词 `rr`，否则 `errcode 310000` 丢弃。

但 GitHub 的 `schedule` 会**静默丢弃**——两次事故分别 5 小时和 20 小时零触发，而 `state` 一直 `active`。
真实触发靠**外部 cron 每 10–15 分钟**打 dispatch（`POST .../actions/workflows/backend-watchdog.yml/dispatches`，
body `{"ref":"master"}`，成功返回 204）。它**不解决根因**。

---

## 16. 社区数据库备份：定时进 GitHub + 客户端离线快照（2026-11-26 落地）

用户要求「把我们数据库方案定时上传到 GitHub，减小用户损失」。落地成 **`db-backup.yml`（🗃️ Community DB backup）**，
现在 GitHub 上已注册、状态 `active`。

**调度（2026-11-26 定稿）**：

- 仓库自带 `schedule: - cron: "17 * * * *"`（每小时 17 分，UTC）只作**兜底**——GitHub 的 schedule 会静默丢触发。
- **真正的准点由外部 cron 打 dispatch**：15 分钟一次，`POST /repos/303562915/unicanta/actions/workflows/db-backup.yml/dispatches`
  + body `{"ref":"master"}`（204 = 已接受）。cron-job.org 之类的服务按这个配。
- **15 分钟一次必须配合下面的「内容没变不提交」**，否则一天会堆 96 个只改时间戳的 commit。

### 16.0 内容没变就不提交（2026-11-26 改）

判断只看**数据本身**：六张表各自的 sha256 指纹（对排序后的 JSON 算）拼成一串，和上一版 `_meta.json` 里记的那串比。
一样 → 这一步直接退出，**连 `_meta.json` 和 `LATEST.md` 都不写**，后面的客户端快照步骤也整个跳过（`if:` 挂在
`steps.content.outputs.changed`），提交步骤再挡一次。所以：

- 「今天没有新 commit」= **数据确实没变**，不是没跑。
- `LATEST.md` 里的「最近一次导出」= **最近一次内容真的变了**的时间点。
- 提交信息会带变化的那几张表（`（变化：plan_votes,comments）`），一眼能看出这轮动了什么。
- 第一次跑、或上一版 `_meta.json` 缺 `digests` 字段（老格式）→ 一律当作「变了」，保证第一次能落地。

**改这里前必须知道**：`site/db/plans.json` 那一步会**覆盖工作区里的文件**。如果内容没变仍让它跑，
`git add` 就会看到一个只差格式的脏文件，等于把「不提交」又绕开了。所以它的 `if:` 条件不是装饰。

### 16.1 它导出什么

只导**用户贡献的社区内容**，六个文件，一个表一个（diff 才读得懂）：

| 文件 | 表 | 内容 |
|---|---|---|
| `ops/db-backup/plans.json` | `plans` | 方案：标题、备注、包列表、机型、ROM/版本/build、sdk、owner（身份编号）、验证状态 |
| `ops/db-backup/plan_votes.json` | `plan_votes` | 方案点赞（`plan_id` + `voter` + 时间） |
| `ops/db-backup/comments.json` | `community_comments` | 评论：kind/target/parent、作者编号、机型、正文 |
| `ops/db-backup/recommendations.json` | `recommend_suggestions` | 推荐条 + 赞成/反对数 |
| `ops/db-backup/description_fixes.json` | `description_fixes` | 描述修正 + 赞成/反对数 |
| `ops/db-backup/level_fixes.json` | `level_fixes` | 档位修正提案 + 票数 |

另有两份汇总：`ops/db-backup/_meta.json`（导出时间、project ref、各表条数、sha256 前 16 位指纹、上一版条数）、
`ops/db-backup/LATEST.md`（人看的清单：时间、条数、比上次多了多少）。

### 16.2 客户端读的那一份

`site/db/plans.json` —— **只含重启验证通过的方案**，列名与 `plans_for` / `plans_all` 的返回一致、点赞已算好、
按点赞排序。`pages.yml` 会把它随全站发到 gh-pages，所以两个地址都可用：

- `https://303562915.github.io/unicanta/db/plans.json`（Pages，国内 CDN，首选）
- `https://raw.githubusercontent.com/303562915/unicanta/master/site/db/plans.json`（raw）

客户端接线在 `PlanRepository`：`offlinePlans()` 先 Pages 再 raw、15 秒超时、读到一次就缓存。
调用点两处——`plansAll()`（`lastFailureWasNetwork` 为真时退回快照）与 `plansFor()`（按型号 + ROM + 主版本本地筛）。
**兜底不改写 `lastError` / `lastFailureWasNetwork`**：后端挂了仍旧如实报"没通"，快照只负责"还有内容可看"。
写操作（上传/投票/评论）没有兜底，那本来就得连库。

### 16.3 隐私红线（改这个工作流前必读）

仓库是 **public**，所以：

1. **只按白名单导**：`canta_users`、`canta_device_keys`、`update_reports`、`operation_failures`、`inbox_seen`、
   `messages`、`point_*`、`private.*` 一律**不导**——里面是 token、IP、设备指纹、失败明细、私聊内容。
2. 方案 `owner` 与评论 `author` 是**身份编号**（`canta_users.id`），不是 `token`；token 在 `canta_users.token`，那张表永不导出。
   由于名字表不导出，离线快照里 `owner_name` 是空串，卡片只显示 `#10000` 这种编号——这是有意的取舍。
3. 新增一张表先想清楚"它能不能公开"，再决定要不要加进白名单。
4. 恢复是**手工**的（把 JSON 读回对应表），别在 CI 里写自动恢复。

### 16.4 排查

```bash
gh run list --workflow db-backup.yml --limit 3        # 最近几次
gh run view <id> --log-failed                         # 失败就看这条
```

- 常见失败：`SUPABASE_ACCESS_TOKEN` 失效、Management API 超时（第 15 节那套资源耗尽的余波）、
  某张表被改名/加列导致 SQL 报 `column ... does not exist`。
- 内容没变时它**不提交**（`git diff --cached --quiet` 直接退出），所以「今天没有新 commit」不等于「没跑」。
- Actions artifact `db-backup` 保留 14 天，仓库文件被改坏时可以从那儿捞回来。