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

Micro 档官方基线：**11 MB/s、500 IOPS**。我们跑在基线的 **0.07%–0.7%**。

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

1. **根因**：实例存储卷的持续写带宽掉到 11 MB/s 基线的 0.07%–0.7%。表现为 Supabase 文档
   所述 Disk IO Budget 耗尽后回落基线的形态（[High Disk I/O](https://supabase.com/kb/troubleshooting/exhaust-disk-io)、
   [Interpreting Supabase Grafana IO charts](https://supabase.com/kb/troubleshooting/interpreting-supabase-grafana-io-charts-MUynDR)），
   但实测值远低于文档给出的 Micro 基线，需要平台侧确认是否被异常限流。
2. **免费手段已用尽**：参数已到社区标准、换过卷、重启多次。
3. **唯一能提高基线的手段是升 compute 档**（Small：22 MB/s、1000 IOPS、2 GB 内存）。
4. **同时开 support ticket**，附本文第 2 节全部数据。

---

## 5. 可直接粘贴的工单正文

> **Subject:** Sustained 7.5–78 KB/s disk write throughput on Micro compute — project `axuhfogeovtsbxokqccf` (ap-northeast-1)
>
> **Summary**
> Checkpoint `write=` times have been 30–148 s for two days, i.e. effective write throughput of
> 7.5–78 KB/s, against the documented Micro baseline of 11 MB/s (500 IOPS baseline). `sync=` stays at
> 0.003–0.177 s, so the bottleneck is the `write()` path, not fsync. This exhausted the connection
> pool and made PostgREST return 500/504/503 for roughly 2 hours on 2026-11-24 and 4 hours on
> 2026-11-25.
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
> 1. Is this project being throttled below the documented Micro baseline? The measured throughput is
>    0.07%–0.7% of it, which looks anomalous rather than "budget exhausted".
> 2. Can the disk be re-provisioned / the underlying volume moved to a host with normal performance?
> 3. If not, is upgrading compute the only remedy we should plan for?

---

## 6. 下次怎么快速判断

1. **先看 checkpoint**：Supabase Dashboard → Logs → `postgres_logs`，搜 `checkpoint complete`，
   看 `write=` 的秒数。正常是 `0.0xx`，出现两位数就是盘的问题，别去翻客户端日志。
2. **看是不是「能连但查询超时」**：`select 1` 要 8 秒以上 → 连接池已被慢查询拖住。
3. **确认服务有没有在发请求**：`postgrest_logs` 里按 `source='postgrest_logs'` 过滤，看有没有
   请求、状态码是什么。有请求 + 5xx = 后端问题；没请求 = 客户端/链路问题。
4. **查接口级**：`gh workflow run supabase-log-probe.yml -f source=postgrest_logs -f minutes=15`。