# AGENTS.md

面向在本仓库里工作的 AI agent / 协作者。**先读第 0 节，它覆盖后面所有内容。**

用户说不认识的快捷词（如 `ga`）时，见**第 8 节**：先按词表理解，猜错就把词和含义登记进去。

---

## 0. 铁律：只改代码，构建全部交给 GitHub Actions

本仓库**不提供也不支持本地构建**。每次完成用户要求的修改后，检查改动、提交并推送到远端，推送完成后直接结束，不等待或跟踪 GitHub Actions 的运行结果。用户明确要求查看或排查构建时再查询。

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
| `daily-device-report.yml` 📊 Daily device report | 定时 + `workflow_dispatch` | 钉钉日报（读 `canta_users`；识别率口径见第 9 节） | — |
| `crowdin_escapes.yml` 🧹 | `push` 到 `l10n_master` | 清 Crowdin 转义后自动提交推送 | — |

> 数据库那一侧（`server/supabase/**`、`server/test/**`、`server/README.md`）
> **只留在本地**，已在 `.gitignore` 里排除，不进公开仓库；迁移与查库在本地做，凭据见 `SUPABASE.local.md`。
> **推迁移没有 CI，唯一路径是手工走 Management API，步骤见第 10 节。**
> 本地备份在 `backups/db-local-only/`。**钉钉日报 `daily-device-report.yml` 照旧留在仓库里跑，不要删。**

关键 task（云端跑的，你**不要**在本地跑）：
`:composeApp:desktopTest`、`:packages:desktopTest`、`:androidApp:assembleDebug`、`:androidApp:assembleRelease`、`:desktopApp:desktopJar`、`:webApp:wasmJsBrowserDistribution`。

产物落点：`androidApp/build/outputs/apk/{debug,release}/`（含 `SHA256SUMS.txt`）。

---

## 3. 标准工作流：改完就推，推送后直接结束

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

---

## 4. 取结果 / 取 APK

```bash
gh run list --limit 10
gh run watch <run-id> --exit-status          # 阻塞到结束，失败返回非零
gh run view <run-id> --log-failed            # 只看失败的步骤日志
gh run download <run-id> -n app-debug -D artifacts
```

- 下载后看 `artifacts/app-debug/SHA256SUMS.txt` 与 `*.apk`。
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
- [ ] 推送成功后直接汇报提交和推送结果，GitHub Actions 构建结果尚未确认时如实说明。

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
| `型号` / `机型显示不对` / `他那个vivo和三星` / `他展示的是这个型号` | 「选择机型」列表里的机型芯片显示的是**零售编号**（`SM-F9710`、`PHK110`、`V2337A`）而不是营销名（`Galaxy Z Fold8`、`一加 Ace 2`、`vivo X Fold3 Pro`） | 2026-11-21 | 用户原话：「它展示的是这个型号，对吗？」「我感觉不太对，只有小米才是正确的。和荣耀是正确的」→ 按第 9 节查 |
| `调用他们的日志` / `他们上传的日志` / `数据库里面他们上传的这个` | 后台数据库里 App 上传的机型/设备数据（`plans.market_name`、`plans.codename`、`canta_users.model`、`community_comments.device_model`），不是客户端日志文件 | 2026-11-21 | 用户原话：「你可以调用他们的日志让他们上传上来呀」「如果你这次解决不了，那你下次就去找这个文件，就找数据库里面他们上传的这个日志去分析他们手机是什么」 |
| `刷票` / `刷到45` / `刷到N` | 直接往 `recommend_suggestion_votes` 灌认同票，把某条推荐条目的 agree 数刷到指定值，再 `private.recommend_settle(url)` 让它 `applied`。**不是**把 `private.recommend_votes_needed()` 的门槛数字改小 | 2026-11-21 | 用户原话：「刚才不是让你刷票吗？把那个我的那个影视星球投票，把那个认同两个票给他刷到45」→ 我先前猜成「降门槛 50→45」（`5dfa2fa`），被这句纠正 |
| `认同` / `认同票` | 推荐投票里的赞成票，即 `recommend_suggestion_votes.agree = true`；反义是「不认同」（`agree = false`） | 2026-11-21 | 同上，用户原话「把那个认同两个票给他刷到45」= 认同票数从 2 变成 45 |
| `影视星球` | 推荐条目 `recommend_suggestions.id = 1`：`name='影视星球'`、`url=https://github.com/303562915/tv`、`author='开发者'`（用户自己那个影视 App 仓库） | 2026-11-21 | 同上。刷票对象就是这一条 |
| `方案` / `方案详情` | 社区里用户提交的**精简方案**，即 `public.plans` 的一行（`plans.title` 是方案名）；「方案详情」= 该方案的详情页（`plan_get`） | 2026-11-21 | 用户原话：「方案详情里面有一个…再一次精简他这一个方案」 |
| `站` / `他现在的站只有两个` / `刷到83` | 方案详情里的**票数**，即 `plan_votes` 里该 `plan_id` 的行数（`plan_get().votes`）；「站只有两个」= 票数很少，「刷到83」= 直接插 votes 行把票数补到 83 | 2026-11-21 | 用户原话：「他现在的站只有两个，帮我刷到83」。与「刷票」同义，但对象是 `plans`/`plan_votes` 而不是 `recommend_suggestion_votes`。本次对象 `plans.id=35`「再一次精简」 |
| `再一次精简` | 方案 `plans.id = 35`（Redmi K40 / `M2012K11AC`，hyperos 4，109 个包） | 2026-11-21 | 用户原话里的「一个叫再一次精简的这一个方案」 |
| `修正历史` / `描述修正` | 应用详情页里「描述修正」的列表，即 `description_fixes_for(pkg)` 读的 `public.description_fixes` 行（带 `agrees`/`disagrees`/`applied`）；「拉到50」= 往 `description_fix_votes` 灌 `agree=true` 补到 50，「直接通过」= 调 `private.description_fix_settle(pkg)` 让 `applied=true` | 2026-11-21 | 用户原话：「把修正历史的有一条是只有一条修正历史的，你把他那个票给他拉到50，直接通过」。本次对象 `description_fixes.id=3`（`com.android.browser`，author=开发者） |
| `他这个` / `他那个` | 指代**上一轮提到的那款第三方软件/方案**（本次指 Dhizuku），不是指人、不是指某台设备 | 2026-11-22 | 用户原话：「他这个不行吗？」= 追问 Dhizuku 是不是也能做上一轮我说它不能做的事（卸载）。按此理解后核实：设备所有者可通过 `PackageInstaller` 静默卸载，我上一轮结论是错的 |

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
-- 1. 谁没有名字（凭据见 SUPABASE.local.md，走 Management API，一条一条发）
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

`api.supabase.com` 必须走 `$env:HTTPS_PROXY`，否则报 SSL 握手失败。**一次请求只能带一条语句。**

### 10.2 推一个迁移：七步，顺序不能换

1. **备份要被替换的东西**：函数就
   `Q "select pg_get_functiondef('public.xxx(sig)'::regprocedure) as d"`，写进
   `backups/db-local-only/<name>_before_<date>.sql`。
2. **数行数**：`Q 'select count(*) from public.<table>'`，把数字记下来。
3. **拆语句**：带 `$$ … $$` 的 `create or replace function` **不能按分号硬拆**。
   按顶层关键字切块（`create or replace function …` / `revoke …` / `do $$ … $$;`），
   照 `20261122100000_comment_delete_owner_match.sql` 那次的三段切法。
4. **逐条发**，每条都要看到 OK；任何一条报错就停下，别继续发后面的。
5. **再数一遍行数**，必须和步骤 2 一样。不一样就是出事了，立刻用步骤 1 的备份回滚。
6. **验定义真的换了**：`pg_get_functiondef(...)` 里能搜到新逻辑的关键字。
7. **空跑一次**：拿明显无效的入参调一次，确认走的是拒绝分支、不抛异常。

### 10.3 不要做的事

- 不用 `delete` / `drop` 来「重置后重建」：`plans`、`community_comments`、`canta_users` 是真数据。
- 不把 `server/supabase/**` 加进仓库来「让 CI 推」——那是把 live 服务的 schema 公开。
  真要走那条路是独立决定，先算清代价：git 历史永久留痕，推上去撤不回来。
- `server/supabase/ops/` 里的脚本会删数据（`purge_plans` 清空全部方案与方案评论），
  只在明确要清空时手工跑。

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
