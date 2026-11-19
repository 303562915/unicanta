---
title: Canta · unicanta - Download
description: 下载 Canta 的 unicanta 分支。
---
# 下载 Canta · unicanta

本分支（包名 `io.github.a303562915.unicanta`）不上架任何应用商店，只从本仓库发布构建产物。

## GitHub Releases

[![GitHub](https://img.shields.io/github/v/release/303562915/unicanta?include_prereleases&style=for-the-badge&logo=GitHub&label=GitHub&color=blue)](https://github.com/303562915/unicanta/releases/latest/)

## 自行构建

```bash
./gradlew :androidApp:assembleDebug
```

产物在 `androidApp/build/outputs/apk/debug/`。要求 JDK 21，并使用 Shizuku 授权。

> [!Warning]
> 使用风险自负。卸载关键应用会导致 bootloop，只能恢复出厂设置。
