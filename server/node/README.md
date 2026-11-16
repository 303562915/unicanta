# 独立后端（不需要 Supabase）

`server.mjs` 一个文件 + 内置 SQLite（`node:sqlite`，无需编译原生模块），协议和 App 完全一致：

```
POST /rest/v1/rpc/challenge          {}                        -> {challenge, sig}
POST /rest/v1/rpc/record             {payload, challenge, sig, x} -> {ok, accepted}
POST /rest/v1/rpc/package_scores     {}                        -> [...]
POST /rest/v1/rpc/package_scores_for {p_rom, p_rom_version}    -> [...]
```

PoW 与客户端 `util/Pow.kt` 一致：`sha256(challenge + ":" + hex(sha256(payload)) + ":" + x)` 前 8 字节大端
小于 `floor(2^64 / (10000 * 事件数))`。

## 跑起来

```bash
cd server/node
PORT=8787 DB_PATH=/tmp/telemetry.db node server.mjs
```

环境变量：`PORT`（默认 8787）、`DB_PATH`（默认同目录 `telemetry.db`）、`RETENTION_DAYS`（默认 35）、`POW_K`（默认 10000）。

## 测试

```bash
cd server/node
node test/e2e.test.mjs     # 真 HTTP + 真 PoW
```

覆盖：正常批次、challenge 重放、篡改 payload、非法事件类型、伪造 sig、五种 verdict、
ROM 版本/家族/未知 ROM 查询、保留期清理。当前结果：**ALL PASS**。

## 让 App 连它

```bash
./gradlew :androidApp:assembleDebug \
  -Pcanta.telemetry.baseUrl="http://127.0.0.1:8787/rest/v1/rpc/" \
  -Pcanta.telemetry.apiKey="local-dev-key"
```

- URL 和 key 由 `generateBuildFlags` 生成到 `TelemetryEndpoints`，不传就还是上游那套；
- debug 清单已放行 cleartext（`androidApp/src/debug/AndroidManifest.xml`），否则 http://127.0.0.1 会被系统拦；
- 手机和这个容器是同一台机器时，`127.0.0.1:8787` 能直接通。

## 和 Supabase 版的关系

- `server/supabase/migrations/*.sql`：同一套协议的 Postgres 实现，用 PGlite 验过，适合托管；
- `server/node/server.mjs`：不依赖任何账号，适合自己一台机器跑或先本地联调；
- 两边判分阈值一致，`server/README.md` 里有说明。
