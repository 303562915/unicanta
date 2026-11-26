# 社区数据库备份

最近一次导出：2026-11-26T09:56:13Z　（每天 06:17 UTC 由 `db-backup.yml` 自动跑）

只含用户贡献的社区内容；身份表、设备表、遥测表不在里面（见工作流头部说明）。

| 表 | 条数 | 上一次 | 变化 | 指纹 |
| --- | ---: | ---: | ---: | --- |
| 方案 plans | 138 | 138 | — | `c9ab80de6172ca45` |
| 方案点赞 plan_votes | 4412 | 4411 | +1 | `cc07bca230fc728f` |
| 评论 community_comments | 400 | 398 | +2 | `e40f61299c7175c7` |
| 推荐条 recommend_suggestions | 28 | 28 | — | `cc6bc87997ccb7eb` |
| 描述修正 description_fixes | 10 | 10 | — | `872573e65f439e0d` |
| 档位修正 level_fixes | 0 | 0 | — | `4f53cda18c2baa0c` |

恢复：把对应 JSON 读回库里的那张表（`plans` 需要先补齐 `owner` 对应的 `canta_users` 行，
否则作者会显示成编号）。这一步是手工的，别在 CI 里做。
