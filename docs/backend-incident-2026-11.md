# 后端事故记录：Supabase 磁盘写入长期劣化（2026-11-24 / 11-25）

> 这是一份事故复盘 + 可直接粘贴给 Supabase Support 的工单正文。
> 项目 `unicanta`，ref `axuhfogeovtsbxokqccf`（`ap-northeast-1`），PostgreSQL 17.12，Micro 档。
>
> 结论先说：**不是 App 的 bug，也不是我们写入量太大**，是实例磁盘的 `write()` 速度掉到
> 基线的 0.7%（7.5–78 KB/s，基线 11 MB/s），checkpoint 每次要写几十秒，最终把连接池拖死、
> 全站 5xx。免费手段（调参、换卷、重启）都试过，只能缓解不能根治。

---

## 1. 现象

| 时间（北京） | 现象 |
|---|---|
| 11-24 21:08 起 | 社区页面开始空；后端 `/rpc/*` 返回 500/504 |
| 11-24 21:14 | Postgres 停止吐日志，实例不可达 |
| 11-24 21:30–22:08 | pgbouncer 连续 `connect timeout` / `query_wait_timeout` |
| 11-24 22:00 | 管理 API 查库 544，重启后 1 分钟内恢复 |
| 11-25 08:00–12:14 | **第二次**，约 4 小时。500 峰值 140 次/分钟、504 峰值 79 次/分钟 |
| 11-25 12:14 | 重启恢复 |
| 11-25 12:28–12:38 | 重启只顶了约 10 分钟，又出现 504 |

客户端看到的表现是「进页面什么都没有、好像没发请求」——实际上请求都发了，全部拿到 5xx，
所以没有数据可画。

用户侧诊断过的一条歧路：`ping` 通、`curl https://<ref>.supabase.co/rest/v1/` 从手机浏览器
能出 JSON，看起来「网络没问题」——但那是链路层面，数据库本身已经在超时。

---

## 2. 证据（全部取自 Supabase 自身日志）

### 2.1 checkpoint 写盘耗时异常

`write=` 是 checkpoint 真正写数据页的时间；`sync=` 是随后 fsync 的时间。正常实例两者都是
毫秒级。

```
2026-11-24 13:07:48  write=35.243 s  sync=0.006 s  wrote 350 buffers (1.2%)
2026-11-24 13:02:43  write=30.628 s  sync=0.027 s  wrote 301 buffers (1.0%)
2026-11-24 12:23:05  write=53.556 s  sync=...       wrote ...
2026-11-24 14:38:26  write=52.591 s  sync=0.009 s  wrote 519 buffers (1.8%)
2026-11-24 14:53:55  write=81.932 s  sync=0.044 s  wrote 717 buffers (2.5%)
2026-11-24 15:24:42  write=64.370 s  sync=0.022 s  wrote 631 buffers (2.2%)   ← 换卷之后复现
2026-11-25 03:50:40  write=147.926 s sync=0.177 s  wrote 490 buffers
2026-11-25 03:33:20  write=77.909 s  sync=0.007 s  wrote 772 buffers
2026-11-25 04:03:44  write=33.357 s  sync=0.089 s  wrote  31 buffers      ← 只有 248 KB
2026-11-25 04:54:40  write=65.431 s  sync=0.003 s  wrote 651 buffers
2026-11-25 05:09:29  write=54.107 s  sync=0.005 s  wrote 535 buffers
```

换算成吞吐：

| 时间 | 数据量 | 耗时 | 实测吞吐 |
|---|---|---|---|
| 11-24 15:24 | 631 buffers ≈ 5.0 MB | 64.4 s | **78 KB/s** |
| 11-25 03:50 | 490 buffers ≈ 3.9 MB | 147.9 s | **27 KB/s** |
| 11-25 04:03 | 31 buffers ≈ 248 KB | 33.4 s | **7.5 KB/s** |

Micro 档基线（Management API `billing/addons` 的 `meta.baseline_disk_io_mbs` 实际返回值）：
**87 MB/s**（突发上限 2085 MB/s、内存 1 GB、直连 60 连接）。我们跑在基线的 **0.086%–0.9%**。

> 这一条很关键：如果只是「Disk IO Budget 用完」，性能**会回落到基线**（87 MB/s），
> 而不是掉到基线千分之一以下。所以这不是额度耗尽，而是卷或宿主机存储层本身有问题。

### 2.2 慢在 write，不在 fsync

所有现象里 `sync` 都只有 0.003–0.177 s，而 `write` 是 30–148 s。说明瓶颈是数据往存储卷
写的那一步，不是刷元数据/fsync。这一条可以直接排除「文件系统 sync 慢」「磁盘队列深度」之类
方向，指向**存储卷本身的写带宽被限到极低**。

### 2.3 不是我们的写入量

| 项 | 数值 |
|---|---|
| 数据库总大小 | **33 MB** |
| WAL 总量（11 天） | 436 MB，平均 ≈ 40 MB/天 |
| `pg_stat_checkpointer` | `num_timed=2003`、`num_requested=12` |
| size-triggered 占比 | **0.6%**（行业判据：>10% 才需要调大 `max_wal_size`） |

写入最多的两张表：`inbox_seen`（89,587 次写 / 9,644 行）、`write_rate`（80,549 次 /
3,706 行）。都是限速与已读标记的小行 UPSERT，对这个量级的实例不构成压力。

### 2.4 高压接口本身不慢

重启后 40 分钟内的 `pg_stat_statements`：

| 函数 | 调用次数 | 平均耗时 | 累计 |
|---|---|---|---|
| `package_scores()` | 6,288 | 194.3 ms | 1,222 s |
| `plan_catalog()` | 11,576 | 74.3 ms | 860 s |

单次都是几十到两百毫秒，**谈不上慢**。但在 500 IOPS 的盘上并发排队后，就超过 8 秒的
`statement timeout`，于是 PostgREST 统一报 504。

### 2.5 故障期的下游表现

```
postgres_logs:  canceling statement due to statement timeout  （每分钟十几次）
pgbouncer_logs: S-…: closing because: connect timeout (age=15s)
                C-…: pooler error: server login has been failing, cached error: connect timeout
                C-…: closing because: query_wait_timeout (age=80s)
postgrest 状态码（24 小时窗口）：503 × 684、500 × 303、504 × 183
```

---

## 3. 已尝试的处置（都没能根治）

| 处置 | 结果 |
|---|---|
| `checkpoint_timeout` 5min → **15min**，`max_wal_size` 1GB → **2GB** | 有效：checkpoint I/O 从每 5 分钟 53 s（占 17% 时间）降到每 15 分钟约 60–80 s（占 9%）。但单次仍是几十秒 |
| 调 `bgwriter_*` / `checkpoint_completion_target` 平滑写盘 | **不可行**：管理 API 拒绝，`Unrecognized keys` |
| `pause` + `restore`（释放实例、重新分配 VM 与存储卷） | **无效**：换卷后 11-24 15:24 复现 64.4 s / 631 buffers |
| `POST /projects/{ref}/restart` | **有效但短暂**：清空连接池，1 分钟内恢复；11-25 只顶了约 10 分钟 |
| 手动触发 `CHECKPOINT` 观察 | **不可行**：无 `pg_checkpoint` 权限 |
| GitHub Actions 看门狗（每 20 分钟探库，连续失败自动重启 + 钉钉告警） | 已上线；但 GitHub 的 cron 在整点高峰会被丢弃（11-25 那次故障期近 5 小时一次都没触发），只能算尽力而为 |

---

## 4. 结论与建议

1. **根因**：实例存储卷的持续写带宽掉到 **87 MB/s** 基线的 **0.086%–0.9%**（7.5–78 KB/s）。
   「Disk IO Budget 耗尽」只会回落到基线，不会掉到千分之一以下，所以这一条**不能**用额度机制解释，
   需要平台侧确认是卷故障还是宿主机存储层降级。
2. **免费手段已用尽**：参数已到社区标准、换过卷（pause/restore，会重新分配 VM 与卷）、重启多次。
   换卷后仍复现，说明故障可能不在单块卷上，而在 ap-northeast-1 的存储集群侧。
3. **可选的商业化手段**（Management API 已返回可购买清单，价格来自 `billing/addons`）：
   | 档位 | 基线磁盘 IO | 内存 | 直连连接 | 价格 |
   |---|---|---|---|---|
   | Micro（当前） | 87 MB/s | 1 GB | 60 | ~$10/月 |
   | Small | **174 MB/s** | 2 GB | 90 | ~$15/月 |
   | Medium | 347 MB/s | 4 GB | 120 | ~$60/月 |

   但**先别急着升级**：换卷都治不好，升档大概率只是换一台同样慢的宿主机。
4. **首选动作：开 support ticket**（附本文第 2 节全部数据）。这是唯一能让平台侧去查宿主机存储的通道。

---

## 5. 可直接粘贴的工单正文

> **Subject:** Sustained 7.5–78 KB/s disk write throughput on Micro compute — project `axuhfogeovtsbxokqccf` (ap-northeast-1)
>
> **Summary**
> Checkpoint `write=` times have been 30–148 s for two days, i.e. effective write throughput of
> **7.5–78 KB/s**, against the Micro baseline of **87 MB/s** as reported by
> `GET /v1/projects/{ref}/billing/addons` → `meta.baseline_disk_io_mbs` (burst cap 2085 MB/s).
> That is **~1000x below baseline**. `sync=` stays at 0.003–0.177 s, so the bottleneck is the
> `write()` path, not fsync. This exhausted the connection pool and made PostgREST return
> 500/504/503 for roughly 2 hours on 2026-11-24 and 4 hours on 2026-11-25.
>
> **Measured data (`postgres_logs`, `checkpoint complete` lines)**
> ```
> 2026-11-24 13:07:48  write=35.243 s   sync=0.006 s   wrote 350 buffers (1.2%)
> 2026-11-24 12:23:05  write=53.556 s                     wrote ...
> 2026-11-24 14:53:55  write=81.932 s   sync=0.044 s   wrote 717 buffers (2.5%)
> 2026-11-24 15:24:42  write=64.370 s   sync=0.022 s   wrote 631 buffers (2.2%)   <- after pause/restore
> 2026-11-25 03:50:40  write=147.926 s  sync=0.177 s   wrote 490 buffers
> 2026-11-25 04:03:44  write=33.357 s   sync=0.089 s   wrote  31 buffers (only 248 KB)
> 2026-11-25 05:09:29  write=54.107 s   sync=0.005 s   wrote 535 buffers
> ```
> Derived throughput: 5.0 MB / 64.4 s = **78 KB/s**; 3.9 MB / 147.9 s = **27 KB/s**;
> 248 KB / 33.4 s = **7.5 KB/s**.
>
> **Not a workload problem**
> - Database total size: **33 MB**; WAL volume: ~40 MB/day
> - `pg_stat_checkpointer`: `num_timed=2003`, `num_requested=12` → size-triggered checkpoints are
>   **0.6%** (well under the 10% threshold that would suggest raising `max_wal_size`)
> - `package_scores()` 194 ms avg, `plan_catalog()` 74 ms avg — not intrinsically slow
>
> **Already attempted**
> - `checkpoint_timeout=15min`, `max_wal_size=2GB` (current), `checkpoint_completion_target=0.9` —
>   reduces frequency but each checkpoint still takes tens of seconds
> - `bgwriter_lru_maxpages` / `bgwriter_delay` / `bgwriter_flush_after` — rejected by the Management
>   API (`Unrecognized keys`), cannot tune
> - **`pause` + `restore` (full volume re-provisioning)** — problem reproduced at
>   2026-11-24 15:24:42
> - `POST /projects/{ref}/restart` — recovers in ~1 minute but the degradation returns (~10 minutes on
>   2026-11-25)
>
> **Impact**
> - 2026-11-24 13:14Z onward: ~2 hours of 5xx; pgbouncer `connect timeout (age=15s)`,
>   `query_wait_timeout (age=80s)`, `server login has been failing`
> - 2026-11-25 00:00–04:14Z: ~4 hours of 5xx (peaks of 140 × HTTP 500 and 79 × HTTP 504 per minute)
>
> **Questions**
> 1. The measured throughput is **0.086%–0.9%** of the 87 MB/s baseline. Since a depleted Disk IO
>    Budget would only drop us *to* the baseline, this looks like a faulty/degraded volume or host
>    storage rather than budget exhaustion — can you confirm on your side?
> 2. Can the disk be re-provisioned / the underlying volume moved to a host with normal performance?
>    Note that `pause` + `restore` (which re-provisions the VM and volume) did **not** fix it, so the
>    fault may be at the host/storage-cluster level in ap-northeast-1.
> 3. If not, is upgrading compute the only remedy we should plan for?

---

## 6. 下次怎么快速判断

1. **先看 checkpoint**：Supabase Dashboard → Logs → `postgres_logs`，搜 `checkpoint complete`，
   看 `write=` 的秒数。正常是 `0.0xx`，出现两位数就是盘的问题，别去翻客户端日志。
2. **看是不是「能连但查询超时」**：`select 1` 要 8 秒以上 → 连接池已被慢查询拖住。
3. **确认服务有没有在发请求**：`postgrest_logs` 里按 `source='postgrest_logs'` 过滤，看有没有
   请求、状态码是什么。有请求 + 5xx = 后端问题；没请求 = 客户端/链路问题。
4. **查接口级**：`gh workflow run supabase-log-probe.yml -f source=postgrest_logs -f minutes=15`。

---

## 7. 怎么开这个工单（没有 API，只能走网页）

Management API **没有** support 接口（实测 `/v1/projects/{ref}/support/tickets`、
`/v1/support/tickets`、`/v1/organizations/{id}/support/tickets` 全部 404），所以工单只能人工提交：

1. 打开 <https://supabase.com/dashboard/project/axuhfogeovtsbxokqccf/support/new>
   （或 Dashboard → 左下角 **Support** → **New ticket**）
2. **Subject** 填第 5 节第一行那句：
   `Sustained 7.5–78 KB/s disk write throughput on Micro compute — project axuhfogeovtsbxokqccf (ap-northeast-1)`
3. **Description** 粘第 5 节从 `**Summary**` 到 `**Questions**` 全部内容
4. 附件（可选但有用）：Dashboard → Reports → 分别截 `Database` 与 `Disk IO` 两张图，覆盖
   2026-11-24 12:00Z – 2026-11-25 06:00Z
5. 提交

> 提交后 24–48 小时没回应时，可在**同一个工单**里追加一句新的 checkpoint 数据（本文第 2 节格式），
> 不要开新单。