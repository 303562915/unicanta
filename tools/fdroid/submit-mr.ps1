# 一键把 metadata/fdroid/*.yml 提成 fdroiddata 的 merge request。
#
# 用法（本机 pwsh 7）：
#   $env:GITLAB_TOKEN = 'glpat-xxxx'      # 需要 api 权限
#   pwsh -File tools/fdroid/submit-mr.ps1 -DryRun     # 先看要提交什么
#   pwsh -File tools/fdroid/submit-mr.ps1             # 真提交
#
# 它做的事：校验 recipe 与 gradle.properties / git tag 对齐 → fork fdroiddata（若还没有）
# → 在 fork 上建分支写 metadata/<appid>.yml → 向上游开 MR → 打印 MR 地址。
# 不做：不 clone fdroiddata（走 GitLab API 直接写文件），不改本仓库，不推送本仓库。

[CmdletBinding()]
param(
    [string]$Token = $env:GITLAB_TOKEN,
    [string]$Branch = 'unicanta-new-app',
    [string]$MrTitle = 'New app: unicanta',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$Here = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)   # 仓库根
if (-not (Test-Path (Join-Path $Here 'gradle.properties'))) { throw "找不到仓库根（$Here）" }
Set-Location $Here

$AppId       = 'io.github.a303562915.unicanta'
$Upstream    = 'fdroid/fdroiddata'
$RecipeLocal = "metadata/fdroid/$AppId.yml"
$RecipeName  = "metadata/$AppId.yml"
$MrBodyFile  = 'metadata/fdroid/MR_DESCRIPTION.md'
$Gitlab      = 'https://gitlab.com'
$ProxyUrl    = $env:HTTPS_PROXY

# ---------------------------------------------------------------- 0. 本地自检

if (-not (Test-Path $RecipeLocal)) { throw "缺 recipe：$RecipeLocal" }
if (-not (Test-Path $MrBodyFile))  { throw "缺 MR 描述：$MrBodyFile" }

$props     = Get-Content gradle.properties
$versionCode = ($props | Select-String '^version_code=').Line -replace '^version_code=', ''
$versionName = ($props | Select-String '^version_name=').Line -replace '^version_name=', ''
$recipe    = Get-Content $RecipeLocal -Raw

$mismatch = @()
if ($recipe -notmatch [regex]::Escape("versionName: $versionName")) { $mismatch += "versionName（gradle.properties $versionName）" }
if ($recipe -notmatch [regex]::Escape("versionCode: $versionCode")) { $mismatch += "versionCode（gradle.properties $versionCode）" }
if ($recipe -notmatch [regex]::Escape("CurrentVersion: $versionName"))   { $mismatch += "CurrentVersion" }
if ($recipe -notmatch [regex]::Escape("CurrentVersionCode: $versionCode")) { $mismatch += "CurrentVersionCode" }

$commitLine = [regex]::Match($recipe, '(?m)^\s*commit:\s*(\S+)\s*$')
$commit = if ($commitLine.Success) { $commitLine.Groups[1].Value } else { '' }
if (-not $commit) { $mismatch += 'commit 行' }
else {
    $tagOk = (git ls-remote --tags origin 2>$null) -match "refs/tags/$([regex]::Escape($commit))(\^\{\})?$"
    if (-not $tagOk) { $mismatch += "commit $commit 不是已推送的 tag" }
}

if ($mismatch.Count -gt 0) {
    throw "recipe 与仓库状态不一致：$($mismatch -join '、')。改 metadata/fdroid/$AppId.yml 后重跑。"
}
Write-Host "recipe 自检通过：$versionName ($versionCode) @ $commit" -ForegroundColor Green

$mrBody = (Get-Content $MrBodyFile -Raw)

if ($DryRun) {
    Write-Host "`n--- DryRun：将要提交 ---" -ForegroundColor Cyan
    Write-Host "目标仓库 : $Upstream"
    Write-Host "文件     : $RecipeName"
    Write-Host "分支     : $Branch"
    Write-Host "MR 标题  : $MrTitle"
    Write-Host "`n--- recipe ---`n$recipe"
    Write-Host "--- MR 描述（前 600 字）---`n$($mrBody.Substring(0, [Math]::Min(600, $mrBody.Length)))"
    return
}

if (-not $Token) {
    throw '缺少 GitLab token：先 $env:GITLAB_TOKEN = ''glpat-xxxx''（scope: api），或者直接在 gitlab.com 网页上手工做（见 docs/fdroid.md）'
}

# ---------------------------------------------------------------- 1. GitLab API 封装

$headers = @{ 'PRIVATE-TOKEN' = $Token; 'User-Agent' = 'unicanta-fdroid-submit' }

function Invoke-Gitlab {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        $Body
    )
    $params = @{
        Method             = $Method
        Uri                = "$Gitlab/api/v4$Path"
        Headers            = $headers
        SkipHttpErrorCheck = $true
        TimeoutSec         = 120
    }
    if ($ProxyUrl) { $params.Proxy = $ProxyUrl }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body        = ([Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8 -Compress)))
    }
    $resp = Invoke-WebRequest @params
    $json = $null
    if ($resp.Content) { try { $json = $resp.Content | ConvertFrom-Json } catch { $json = $resp.Content } }
    [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $json }
}

function Enc([string]$s) { [uri]::EscapeDataString($s) }

# ---------------------------------------------------------------- 2. 身份

$me = Invoke-Gitlab GET '/user'
if ($me.Status -ne 200) { throw "token 无效或没网（$($me.Status)）：$($me.Body.message)（代理 $ProxyUrl）" }
$user = $me.Body.username
Write-Host "GitLab 身份：$user" -ForegroundColor Green

# ---------------------------------------------------------------- 3. 上游项目

$target = Invoke-Gitlab GET "/projects/$(Enc $Upstream)"
if ($target.Status -ne 200) { throw "取不到 $Upstream（$($target.Status)）" }
$targetId      = $target.Body.id
$targetBranch  = $target.Body.default_branch
Write-Host "上游项目 id=$targetId，默认分支=$targetBranch"

# ---------------------------------------------------------------- 4. fork（已有就复用）

$forkPath = "$user/fdroiddata"
$fork     = Invoke-Gitlab GET "/projects/$(Enc $forkPath)"
if ($fork.Status -eq 404) {
    Write-Host "还没有 fork，正在 fork（大仓库，可能要几分钟）…" -ForegroundColor Yellow
    $created = Invoke-Gitlab POST "/projects/$targetId/fork" @{ namespace_path = $user; visibility = 'public' }
    if ($created.Status -notin 201, 202, 400) { throw "fork 失败（$($created.Status)）：$($created.Body.message)" }
} else {
    Write-Host "复用已有 fork：$forkPath"
}

Write-Host '等 fork 就绪…'
$forkId = $null
for ($i = 1; $i -le 60; $i++) {
    $f = Invoke-Gitlab GET "/projects/$(Enc $forkPath)"
    if ($f.Status -eq 200) {
        $forkId = $f.Body.id
        $branchProbe = Invoke-Gitlab GET "/projects/$forkId/repository/branches/$(Enc $targetBranch)"
        if ($branchProbe.Status -eq 200) { break }
    }
    Start-Sleep -Seconds 10
    if ($i -eq 60) { throw 'fork 60 次轮询仍未就绪，稍后重跑本脚本（fork 会继续在 GitLab 后台完成）' }
}
Write-Host "fork 就绪 id=$forkId" -ForegroundColor Green

# ---------------------------------------------------------------- 5. 分支

$chosen = $Branch
for ($n = 2; $n -le 20; $n++) {
    $exists = Invoke-Gitlab GET "/projects/$forkId/repository/branches/$(Enc $chosen)"
    if ($exists.Status -eq 404) { break }
    $chosen = "$Branch-$n"
}
if ($chosen -ne $Branch) { Write-Host "分支 $Branch 已存在，改用 $chosen" -ForegroundColor Yellow }

# ---------------------------------------------------------------- 6. 写 metadata

$fileB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($recipe))
$commitMessage = "New app: $AppId ($versionName / $versionCode)"
$payload = @{
    branch         = $chosen
    start_branch   = $targetBranch
    content        = $fileB64
    encoding       = 'base64'
    commit_message = $commitMessage
    author_name    = $user
}
$put = Invoke-Gitlab POST "/projects/$forkId/repository/files/$(Enc $RecipeName)" $payload
if ($put.Status -notin 200, 201) { throw "写 $RecipeName 失败（$($put.Status)）：$($put.Body.message)" }
Write-Host "已写入 $RecipeName @ $chosen" -ForegroundColor Green

# ---------------------------------------------------------------- 7. 开 MR

$mrPayload = @{
    source_branch      = $chosen
    target_branch      = $targetBranch
    title              = $MrTitle
    description        = $mrBody
    target_project_id  = $targetId
    remove_source_branch = $false
}
$mr = Invoke-Gitlab POST "/projects/$forkId/merge_requests" $mrPayload
if ($mr.Status -notin 200, 201) {
    if ($mr.Body.message -match 'Another open merge request already exists') {
        $existing = Invoke-Gitlab GET "/projects/$forkId/merge_requests?source_branch=$(Enc $chosen)&state=opened"
        Write-Host "已有同名 MR：$($existing.Body[0].web_url)" -ForegroundColor Yellow
        return
    }
    throw "开 MR 失败（$($mr.Status)）：$($mr.Body | ConvertTo-Json -Depth 5 -Compress)"
}

Write-Host "`nMR 已创建：$($mr.Body.web_url)" -ForegroundColor Cyan
Write-Host '接下来：等 reviewer 回复；被打回就改 metadata 后重跑本脚本（会自动换分支重开 MR）。'
