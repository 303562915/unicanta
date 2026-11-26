# 备案：2026-11-25/26 全站白屏事故 —— 根因、处置、待办

> **一句话**：不是磁盘坏了，是**免费档 426 MB 内存装不下 `shared_buffers 224MB` → 开 swap → 磁盘写变 60 秒 → 连接池被拖死 → 全站 500/503/504**。
>
> 下次再遇到「所有社区页面一片空白」，**先翻这一页，别先去查客户端缓存**。

- 事故日期：2026-11-24 ~ 2026-11-25（三次），处置与验证：2026-11-26
- 项目：`axuhfogeovtsbxokqccf`（ap-northeast-1，PG 17.12，Nano / Free）
- 关联文档：`AGENTS.md` 第 15 节（速查版）、[backend-incident-2026-11.md](backend-incident-2026-11.md)（首份事故报告 + 英文工单模板）

---

## 1. 现象（用户看到的样子）

| 时间 | 表现 |
|---|---|
| 11-24 约 2 小时 | 所有社区页面空白，登录后什么都没有 |
| 11-25 约 4 小时 | 同上；500 峰值 140 次/分，504 峰值 79 次/分 |
| 11-25 晚间 | 500 × 73 次/小时 |
| 用户原话 | 「为什么，我这登录进入啥都没有请求了」「什么都没有」 |

关键判据：**客户端缓存只会造成「先画旧数据再刷新」，绝不会造成长期空白。** 一旦真空白 → 后端挂了。

---

## 2. 真因（Supabase 官方诊断原文）

```
Disk I/O is exhausted: burst balance reached 0%, I/O wait averaged 13.3%, peaked at 118.6%
Memory pressure: swap peaked at 557 MB on an instance with 426 MB of memory
```

### 2.1 完整故障链

```
shared_buffers = 224 MB（占 426 MB 内存的 53%）
  + 26 条连接各自进程内存 + maintenance_work_mem 32MB  →  物理内存装不下
  →  内核开始 swap（而 swap 就在同一块数据盘上）
  →  checkpoint 写数据页时：一边往盘写、一边等 swap 读写
  →  checkpoint 的 write= 从毫秒级变成 43–148 秒
  →  查询排队超过 8 秒 statement timeout
  →  连接被占满，pgbouncer connect timeout / query_wait_timeout
  →  PostgREST 对所有人返回 500 / 503 / 504
```

### 2.2 为什么一开始误判成「磁盘坏了」

当时只能看到 PostgreSQL 这一层的日志，看到的全是 `write()` 卡住、而 `sync=` 只要 3 ms。

> **`sync` 快而 `write` 慢，既可能是盘/宿主机 IO 故障，也可能是 swap 拖累。** 当时只想到前一种，没查内存。

判别证据在 **Dashboard → Observability → Compute**（`burst balance 0%`、`swap 557MB`），**不在 SQL 日志里**。

### 2.3 最容易踩的坑（写在最前面）

> **小算力的 Disk IOPS 图表只统计数据盘，swap 造成的卡顿在那张图上可能看着完全正常。**
> 判断内存问题必须看 **CPU iowait** 和 **memory / swap** 两张图，不能只看 Disk IOPS。

---

## 3. 已做的处置（2026-11-26，全部免费）

通过 `PATCH /v1/projects/{ref}/config/database/postgres` 改，**改内存类参数会自动重启实例**。

| 参数 | 之前 | 现在 | 为什么这么改 | 实测确认 |
|---|---|---|---|---|
| `shared_buffers` | 224 MB | **96 MB** | 整库只有 44 MB，96 MB 仍能装两遍，命中率不会掉；直接释放 128 MB 给系统，swap 压力骤降 | ✅ `96MB` |
| `max_connections` | 60 | **30** | 实际活跃只有 4–26 条，每条连接都是一份进程内存；外部连接走 supavisor 池化，客户端无感 | ✅ `30` |
| `checkpoint_timeout` | 5 min | **15 min** | 刷盘频率降到 1/3 | ✅ `15min` |
| `max_wal_size` | 1 GB | **2 GB** | 判据：`num_requested/(num_timed+num_requested)` 长期远低于 10%，不用继续加 | ✅ `2GB` |

修改时的正常现象：日志出现
`FATAL: 57P03: the database system is shutting down`（50 条）+ `terminating connection due to administrator command`（25 条）
—— 这是**重启本身**造成的，不是故障，不要误读。

### 3.1 处置后的实测效果

| 指标 | 03:01 重启之前 | 03:01 重启之后 |
|---|---|---|
| `statement timeout` | 246 条（近 4 小时） | **0 条** |
| `FATAL` / `PANIC` | 75 条 | **0 条** |
| checkpoint `write=` | 43 ~ 148 秒 | 一度降到 **0.047 秒** |
| 缓冲区命中率 | — | **99.938%**（磁盘读仅 2,611 次，96MB 完全够用） |
| 重启后 WAL 生成 | — | 176 MB / 13 段（不是写入洪峰） |
| 最大表 | — | `telemetry_events` 9.2 MB / 30 行；`write_rate` 1.5 MB / 3,974 行；`inbox_seen` 1.4 MB / 11,720 行 |

### 3.2 处置**没有**根治 —— 必须知道的事实

**3:16:30 那次 checkpoint 的 `write=` 又回到 52.7 秒。**

也就是说：内存压力被压制了一轮，但**没有消失**。免费档只有 426 MB，参数已经压到极限；
只要请求量还是原来那个量级（24 小时 49.6 万次），52 秒的 checkpoint 会重新出现。

---

## 4. 还没做的事（客户端侧，才是根治）

24 小时实测流量：**496,243 次请求 / 41.7 MB 出口**（出口远低于 5 GB 免费额度 → **流量不是瓶颈，请求数才是**）。

| 路径 | 次数/24h | 要做的事 |
|---|---|---|
| `HEAD /rpc/` | **105,330** | 后端探测，每次 231 字节纯浪费 → 改成「出错之后才探」，不要常驻 |
| `POST /rpc/inbox_summary_kinds` | 44,941 | 轮询 30s → 60~90s |
| `POST /rpc/inbox_seen_mark` | 40,932 | 每会话发一次 → **合并成批量**，一次标记多个 |
| `POST /rpc/conversations_for` | 36,195 | 轮询放慢 |
| `POST /rpc/package_vote_counts` | 28,872 | 轮询放慢 |
| `POST /rpc/plans_for` | 27,874 | 轮询放慢 |
| `POST /rpc/recommend_suggestions` | 27,320 | 轮询放慢 |
| `POST /rpc/comments_for` | 23,836 | 轮询放慢 |

补充：

- 聊天已有 Realtime 广播（`ef2230d`），轮询只是兜底，**可以放得很慢**。
- 不要在 `onResume` / 每次切 tab 都重拉：先看缓存，不新鲜才发请求。
- `package_scores()`（平均 194 ms / 6,288 次）、`plan_catalog()`（平均 74 ms / 11,576 次）是数据库热点且都是 `stable`，适合加**结果缓存**（物化视图 + 定时刷新，或 TTL 表），目标单次 < 20 ms。
- 前端应有一个「后端忙」的状态，而不是白页。

**保守估计：轮询放慢 + 砍掉 `HEAD /rpc/` + 批量已读，能砍掉 50% 以上请求量。**

---

## 5. 下次再白屏：5 步判定（按顺序，别乱猜）

| # | 看哪里 | 结论 |
|---|---|---|
| 1 | Dashboard → Observability → **Compute**（Memory / Swap / CPU iowait） | swap 非零或 iowait 高 = **资源耗尽**，不是代码问题 |
| 2 | Database → Logs → `postgres_logs` 搜 `checkpoint complete` | `write=` 两位数秒 = 盘/swap 拖累 |
| 3 | Management API 跑一次 `select 1` | 超过 8 秒 = 连接池已经被慢查询拖住 |
| 4 | `postgrest_logs` 按 `source='postgrest_logs'` 过滤 | **有请求 + 5xx** = 后端问题；**没有请求** = 客户端/链路问题 |
| 5 | 本机连不上 `api.supabase.com` 时：`gh workflow run supabase-log-probe.yml -f source=postgrest_logs -f minutes=15` | 在 CI 里跑第 4 步 |

### 5.1 查日志的可用配方（2026-11-26 验证通过）

接口 `POST /v1/projects/{ref}/analytics/endpoints/logs`，body 只能有 `sql` / `iso_timestamp_start` / `iso_timestamp_end`。

踩过的坑（都会报错）：

| 写法 | 报错 |
|---|---|
| `log_attributes['message']` | `UNKNOWN_IDENTIFIER` → 正确字段是 **`event_message`** |
| `timestamp >= '2026-11-26T00:15:13Z'` | `TYPE_MISMATCH` → 必须 `toDateTime64('2026-11-26 00:15:13', 9)`，**空格不用 T/Z** |
| `iso_timestamp_start` 当 SQL 字段用 | `Unknown expression or function identifier` → 它只是 body 参数，不是列；SQL 里用 `timestamp` |
| `source='logs.all'` | 410 Gone |

可直接用（已验证）：

```sql
-- 状态码分布
select log_attributes['response.status_code'] code, count(*) n
  from logs where source='postgrest_logs'
   and timestamp >= toDateTime64('2026-11-26 00:17:00', 9)
 group by 1 order by n desc;

-- checkpoint 耗时（从正文抽数字）
select timestamp,
       extract(event_message,'write=([0-9.]+) s') w,
       extract(event_message,'sync=([0-9.]+) s')  s
  from logs where source='postgres_logs'
   and timestamp >= toDateTime64('2026-11-26 00:17:00', 9)
   and event_message like '%checkpoint complete%'
 order by timestamp desc;

-- FATAL / ERROR 分类
select log_attributes['parsed.error_severity'] sev, left(event_message,100) msg, count(*) n
  from logs where source='postgres_logs'
   and timestamp >= toDateTime64('2026-11-26 00:17:00', 9)
   and log_attributes['parsed.error_severity'] in ('ERROR','FATAL','PANIC')
 group by 1,2 order by n desc;
```

时间窗口上限 24 小时；单次返回过大时会返回空，收窄窗口即可。

---

## 6. 花钱的那条路（要算清楚，别被误导）

| 档位 | 价格 | 内存 / 磁盘 | 说明 |
|---|---|---|---|
| **Pro** | **$25/月，自带 Micro** | 1 GB / 87 MB/s | **不需要再另外买 Micro**。早期说的「$25 + $15 = $40」是高估，真实门槛就是 $25 |
| Pro + Small | 约 +$5/月 | 2 GB / 174 MB/s / 90 连接 | 想彻底不 swap，这一档更稳 |
| Pro + Medium | 约 $60/月 | 4 GB / 347 MB/s | 没必要，除非用户量再涨一个量级 |

**Free 档的现实约束（都实测过）**

- 改不了任何 addon：`400 entitlement_required, feature=project_addons`
- **没有工单入口**：`/support/tickets` 三个路径全 404 —— 所以「找 Supabase 开单」这条路走不通，别浪费时间
- `backups: []`、`pitr_enabled: false` → **免费档没有任何自动备份**
- Free 档里唯一能「换一台机器」的办法是**新建一个项目迁过去**（免费，但要发版改 base URL）

---

## 7. 看门狗：留着，但别指望它

`.github/workflows/backend-watchdog.yml` 保留（当断路器用），边界写清楚：

- **它有用的地方**：后端变慢/挂掉时自动 `/restart`，把「4 小时白屏」压到「几分钟」；
  钉钉告警正文**必须**带关键词 `rr`，否则 `errcode 310000` 被丢弃；
  checkpoint `write=` > 5 秒就提前预警。
- **它靠不住的地方**：GitHub 的 `schedule` 会被**静默丢弃** —— 两次事故期间分别出现
  **5 小时**和 **20 小时**零触发，而 `state` 一直显示 `active`。改 cron 表达式解决不了。
- 所以真实触发方式是**外部 cron 每 10–15 分钟**打一次 dispatch：

```
POST https://api.github.com/repos/303562915/unicanta/actions/workflows/backend-watchdog.yml/dispatches
Authorization: Bearer <fine-grained PAT，权限只需 Actions: Read and write>
Accept: application/vnd.github+json
Content-Type: application/json
Body: {"ref":"master"}          # 成功返回 204
```

- **它不解决根因**：重启只是清掉卡住的连接池。治本是本文第 3 节的内存参数 + 第 4 节的降请求量。
  只要 swap 还非零，故障就会复发。

---

## 8. 备份纪律（免费档唯一的安全网）

数据库没有自动备份，只能手工脱敏快照。**必须剥掉敏感列**：

```sql
select jsonb_agg(to_jsonb(t) - 'token' - 'device_key')::text from public.canta_users t;
select jsonb_agg(to_jsonb(t) - 'token' - 'device_key')::text from public.canta_device_keys t;
```

> 2026-11-25 踩过一次：第一次 dump 没剥列，**泄露了 3125 个用户 token**到本地文件；
> 已清空并以脱敏方式重做，验证 `grep '"token"'` 为空。**以后一律先剥列再落地。**

本地快照位置：`backups/db-local-only/2026-11-25_snapshot/`（22 个文件 / 5.6 MB，已脱敏）。

---

## 9. 时间线

| 时间 | 事件 |
|---|---|
| 11-24 | 首次全站不可用，约 2 小时恢复 |
| 11-25 | 第二次，约 4 小时（500 峰值 140/分、504 峰值 79/分）；当晚再次 500 × 73/小时 |
| 11-25 | 手工脱敏快照保存到本地 |
| 11-26 | 收到 Supabase 官方诊断（资源耗尽） |
| 11-26 | 应用 4 项免费参数改动；实例重启 |
| 11-26 03:01 | 重启完成，`statement timeout` / `FATAL` 清零 |
| 11-26 03:16 | checkpoint `write=` 再次回到 52.7 秒 → **确认未根治** |
| 11-26 | 本文档建立；`AGENTS.md` 第 15 节（速查版）同步 |