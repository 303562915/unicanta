# 发给上游作者的说明（GitHub issue 正文，英文）

**状态：备用稿，暂不发送。** 现在的决定是不主动问上游；只有 fdroiddata 的 reviewer 明确要求
"通知原作者"时才用它（见 `docs/fdroid.md`）。真要发时：在 <https://github.com/samolego/Canta/issues>
开一条 issue，标题建议：

```
Fork with a separate application ID — planned F-Droid submission, any objection?
```

正文（直接复制，按需改链接）：

---

Hi, and thanks for Canta.

I maintain a fork at <https://github.com/303562915/unicanta>. It keeps the same
core idea (uninstalling system apps via Shizuku / device owner / root) and adds a
community layer — shared presets with votes, per-package description fixes,
comments — plus desktop and web clients in the same repository.

Planned change on my side: I'd like to publish it on F-Droid under its **own
application ID** `io.github.a303562915.unicanta` and with my own signing key.

What that means for you, concretely:

- **No conflict with your package.** Different ID, different key. Both apps can be
  installed side by side; nothing replaces or upgrades `io.github.samolego.canta`.
- **Your listing is untouched.** I'm not proposing any change to your metadata or
  builds in fdroiddata.
- F-Droid's inclusion checklist asks the original author to be notified and not to
  oppose the fork being packaged, so I'm asking here. If you'd rather I didn't
  submit it under a name that reads like "another Canta", tell me and I'll rename
  the app / drop the plan.
- LICENSE stays LGPL-3.0 and the fork's source is public, with your copyright
  notices intact.

If you're fine with it, a short "no objection" reply is all I need — I'll link it
in the F-Droid merge request. Thanks!

---

拿到回复后，把链接补进 `metadata/fdroid/MR_DESCRIPTION.md` 的 "Upstream review points" 第 1 条。
