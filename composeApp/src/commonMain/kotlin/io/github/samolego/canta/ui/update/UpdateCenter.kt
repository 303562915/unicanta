package io.github.samolego.canta.ui.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.samolego.canta.core.CantaPlatform
import io.github.samolego.canta.core.InstallStage
import io.github.samolego.canta.core.UpdateDownload
import io.github.samolego.canta.core.UpdateDownloadFailure
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.about_auto_download_failed
import io.github.samolego.canta.generated.resources.about_download_failed
import io.github.samolego.canta.generated.resources.about_download_failed_network
import io.github.samolego.canta.generated.resources.about_download_failed_storage
import io.github.samolego.canta.generated.resources.about_download_failed_timeout
import io.github.samolego.canta.generated.resources.about_download_failed_unsupported
import io.github.samolego.canta.generated.resources.about_download_too_slow
import io.github.samolego.canta.generated.resources.about_install_done
import io.github.samolego.canta.generated.resources.about_install_handoff
import io.github.samolego.canta.generated.resources.about_install_failed
import io.github.samolego.canta.generated.resources.about_install_refused
import io.github.samolego.canta.generated.resources.about_installing_version
import io.github.samolego.canta.generated.resources.about_update_download_toast
import io.github.samolego.canta.ui.screen.DOWNLOAD_PAGE_URL
import io.github.samolego.canta.ui.screen.DOWNLOAD_PROGRESS_UNKNOWN
import io.github.samolego.canta.ui.screen.INSTALL_DONE_LINGER_MILLIS
import io.github.samolego.canta.ui.screen.UpdateChannel
import io.github.samolego.canta.ui.screen.UpdateCheckException
import io.github.samolego.canta.ui.screen.UpdateFailure
import io.github.samolego.canta.ui.screen.UpdateHistoryEntry
import io.github.samolego.canta.ui.screen.UpdateRelease
import io.github.samolego.canta.ui.screen.UpdateState
import io.github.samolego.canta.ui.screen.downloadReleaseWithRetries
import io.github.samolego.canta.ui.screen.isNewerThanInstalled
import io.github.samolego.canta.ui.screen.latestRelease
import io.github.samolego.canta.ui.screen.latestReleaseFromPage
import io.github.samolego.canta.ui.screen.releaseHistory
import io.github.samolego.canta.ui.screen.updateLabel
import io.github.samolego.canta.util.PackageTelemetry
import io.github.samolego.canta.util.UpdateReport
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 更新这件事的唯一实现：检查、下载、安装、留痕，以及那份唯一的 [state]。
 *
 * 为什么要有它：以前启动时的自动更新（`CantaApp` 里那条轮询）和「关于」页的点击各写了一遍
 * 下载 + 安装 + 上报，两边状态互不相通 —— 自动下载时关于页什么也不显示，看起来像"没在更新"，
 * 只有手点才看得到进度条。现在两条路走的是同一份代码和同一个状态：自动下载进来，关于页那张
 * 卡片照样有进度条在动。
 *
 * 安装只说 toast（[CantaPlatform.showMessage]）：开始一次、结束一次，别的什么也不打印。
 */
/**
 * 更新这一整块的唯一实现：查、下、装，以及卡片要画的那一份状态。
 *
 * `internal` 而不是 public：它的状态与返回值是 `UpdateState` / `UpdateRelease`，那两个也是
 * internal（同一条链上只给本模块用），public 类把它们暴露出去编译器会直接拒绝。
 */
internal class UpdateCenter(
    private val platform: CantaPlatform,
    private val settings: CantaSettings,
    private val telemetry: PackageTelemetry?,
    private val client: HttpClient,
    private val scope: CoroutineScope,
) {
    /** 卡片画的唯一状态；自动路径和手动点击都改这一个。 */
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** 下载完了等一句话（「安装前确认」开关开着时才这样）。 */
    var pendingConfirm by mutableStateOf<UpdateState.ReadyToInstall?>(null)
        private set

    /**
     * 最近几个版本的更新要点，关于页那张「最近更新」卡片画的就是它。
     *
     * 空的两种含义：还没读过（[loadHistory] 才会去读），或者这份渠道没有发布说明可读。页面在这种
     * 情况下不画这张卡片，而不是先画一个「正在加载」再换成空白。
     */
    var history by mutableStateOf<List<UpdateHistoryEntry>>(emptyList())
        private set

    private var historyJob: Job? = null

    /** 同一时刻只允许一次安装：自动路径和手动路径可能同时走到这里。 */
    private var installing = false

    /** 「运行时没就绪」重试的是哪一版、已经重试了几次；换版本就清零。 */
    private var notReadyVersion = ""
    private var notReadyRetries = 0

    private var downloadJob: Job? = null

    /** 正在下（或刚下完）的那个版本，取消下载之后要退回到它，而不是退回一张空白卡片。 */
    private var lastRelease: UpdateRelease? = null

    /** 切换更新通道之后：手上这份状态说的是另一个通道的版本，不能再画。 */
    fun onChannelChanged() {
        downloadJob?.cancel()
        downloadJob = null
        lastRelease = null
        pendingConfirm = null
        state = UpdateState.Idle
    }

    /**
     * 读最近几个版本的更新要点（关于页打开时调一次，换渠道时重读）。
     *
     * 只是一个句子列表：读失败就保持空，页面不画这张卡片。这不是更新检查，没有「失败」这个状态
     * 值得写在屏幕上。
     */
    fun loadHistory(force: Boolean = false) {
        if (!force && (historyJob?.isActive == true || history.isNotEmpty())) return
        historyJob?.cancel()
        historyJob = scope.launch {
            val target = channel()
            history = try {
                releaseHistory(client, target)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /** 点了「安装」：等确认的那个版本开始装。 */
    fun confirmPending() {
        val ready = pendingConfirm ?: return
        pendingConfirm = null
        install(ready)
    }

    /** 点了「以后再说」：这一版不再自动装，下次启动只提示、不重下。 */
    fun postponePending() {
        val ready = pendingConfirm ?: return
        pendingConfirm = null
        // 卡片不能一直写着「已下载，点击安装」：这一版已经被放下了，下一次点「检查更新」才是明确的请求。
        state = UpdateState.Idle
        scope.launch { settings.setSkippedAutoInstallVersion(ready.version) }
    }

    /** 手动检查：走 API（答得干净），失败会把原因写在卡片上并留一行。 */
    fun check() {
        if (state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing) return
        // A store build cannot be replaced by this repo's APK: its signature is the store's.
        if (platform.isInstalledFromFdroid()) {
            state = UpdateState.StoreManaged
            return
        }
        state = UpdateState.Checking
        scope.launch {
            val target = channel()
            val channelName = channelName(target)
            val startedAt = TimeSource.Monotonic.markNow()
            val found = try {
                latestRelease(client, target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateCheckException) {
                state = UpdateState.Failed(e.failure)
                telemetry?.recordUpdate(
                    stage = UpdateReport.STAGE_CHECK,
                    outcome = UpdateReport.OUTCOME_FAILED,
                    channel = channelName,
                    code = e.failure.name,
                    elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
                )
                return@launch
            } catch (e: Exception) {
                state = UpdateState.Failed(UpdateFailure.UNKNOWN)
                telemetry?.recordUpdate(
                    stage = UpdateReport.STAGE_CHECK,
                    outcome = UpdateReport.OUTCOME_FAILED,
                    channel = channelName,
                    code = UpdateFailure.UNKNOWN.name,
                    detail = e.toString().take(400),
                    elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
                )
                return@launch
            }
            val label = updateLabel(found)
            telemetry?.recordUpdate(
                stage = UpdateReport.STAGE_CHECK,
                outcome = UpdateReport.OUTCOME_OK,
                channel = channelName,
                version = label,
                elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
            )
            when {
                !found.isNewerThanInstalled() -> state = UpdateState.Latest
                // Downloading on its own is what the switch is for; it is on by default. A build
                // that already failed to install on its own is left for a manual tap: fetching it
                // again only to fail again is the loop this mark breaks.
                settings.autoInstallUpdatesFlow.first() &&
                    label != settings.skippedAutoInstallVersionFlow.first() -> startDownload(found, auto = true)
                else -> state = UpdateState.Available(label, found)
            }
        }
    }

    /**
     * 后台轮询：只读发布页（不吃 GitHub 的匿名 API 配额），失败不说话 —— 下一次轮询就是重试。
     */
    suspend fun poll(): UpdateRelease? = try {
        latestReleaseFromPage(client, channel())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** 开始下载 [release]：自动发现的版本和手点的版本走同一条路，卡片因此都有进度条。 */
    fun startDownload(release: UpdateRelease, auto: Boolean = false) {
        val label = updateLabel(release)
        downloadJob?.cancel()
        lastRelease = release
        state = UpdateState.Downloading(label, DOWNLOAD_PROGRESS_UNKNOWN)
        // 下载在后台跑，唯一能说一句的就是 toast。
        scope.launch { platform.showMessage(getString(Res.string.about_update_download_toast, label)) }
        downloadJob = scope.launch {
            val channelName = channelName(channel())
            val startedAt = TimeSource.Monotonic.markNow()
            // The percentage the transfer reached, so a failed download reports where it stopped
            // instead of only that it stopped.
            var reached = DOWNLOAD_PROGRESS_UNKNOWN
            // 速度判断：上一次进度变化的时间，以及有没有已经因为太慢停过。
            var lastPercent = DOWNLOAD_PROGRESS_UNKNOWN
            var lastChange = TimeSource.Monotonic.markNow()
            val tooSlow = MutableStateFlow(false)
            // Not one shot: the rounds and the platform's resume keep a flapping proxy from
            // turning into "download failed, tap it yourself again".
            val result = try {
                downloadReleaseWithRetries(
                    platform = platform,
                    tag = release.tag,
                    buildNumber = release.buildNumber,
                    // 站点给的直链优先：它是下载页上那个按钮指向的同一个文件。
                    fileUrl = release.fileUrl,
                    onRound = { attempt ->
                        // Every round but the first follows a failed one: back to a spinner instead of
                        // leaving an error sentence on screen while the next attempt is already running.
                        // The stall clock restarts too: a new round has not had time to be slow yet.
                        if (attempt > 1) {
                            state = UpdateState.Downloading(label, DOWNLOAD_PROGRESS_UNKNOWN)
                            lastChange = TimeSource.Monotonic.markNow()
                        }
                    },
                    onProgress = { percent ->
                        reached = percent
                        state = UpdateState.Downloading(label, percent)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            lastChange = TimeSource.Monotonic.markNow()
                        }
                        // 慢到这个地步就别再耗着：停下，把手交给下载页（浏览器断点续传、下载器也都比
                        // 应用内这条连接快）。只在能算出百分比时判断——没有 Content-Length 时根本量不出速度。
                        if (!tooSlow.value && isDownloadTooSlow(startedAt, lastChange, percent)) {
                            tooSlow.value = true
                            downloadJob?.cancel()
                        }
                    },
                )
            } catch (e: CancellationException) {
                // 这次取消是我们自己发的（太慢），收尾得在 NonCancellable 里做：协程已经处于取消态，
                // 任何 suspend 调用（读资源字符串、发 toast）都会立刻再抛一次。
                if (tooSlow.value) withContext(NonCancellable) { onDownloadTooSlow(release, label, auto, channelName, reached, startedAt) }
                throw e
            }
            val elapsed = startedAt.elapsedNow().inWholeMilliseconds
            when (result) {
                is UpdateDownload.Done -> {
                    telemetry?.recordUpdate(
                        stage = UpdateReport.STAGE_DOWNLOAD,
                        outcome = UpdateReport.OUTCOME_OK,
                        channel = channelName,
                        version = label,
                        progress = 100,
                        bytes = result.bytes,
                        elapsedMs = elapsed,
                    )
                    val ready = UpdateState.ReadyToInstall(label, result.path)
                    state = ready
                    // Asking first is a switch, off by default: the download only ran because
                    // automatic updates are on, so the install is the thing that was asked for.
                    if (settings.confirmBeforeInstallFlow.first()) pendingConfirm = ready else install(ready)
                }
                is UpdateDownload.Failed -> {
                    val reason = getString(result.failure.reason())
                    telemetry?.recordUpdate(
                        stage = UpdateReport.STAGE_DOWNLOAD,
                        outcome = UpdateReport.OUTCOME_FAILED,
                        channel = channelName,
                        version = label,
                        code = result.failure.name,
                        reason = reason,
                        progress = reached,
                        elapsedMs = elapsed,
                    )
                    state = UpdateState.FailedWith(reason, result.failure)
                    // Said out loud, because a download that ends without a word is what made the
                    // "正在下载" toast read as a lie.
                    platform.showMessage(getString(Res.string.about_auto_download_failed))
                }
            }
        }
    }

    /**
     * 下载慢到不值得再等：停下，退回「可以下载」那张卡片，并说清楚去哪里下。
     *
     * 自动更新（[auto] = true）不直接跳浏览器——那会在用户没点的时候抢走屏幕；手动点的那一次
     * 才跳下载页，因为那本来就是他想要的。
     */
    private suspend fun onDownloadTooSlow(
        release: UpdateRelease,
        label: String,
        auto: Boolean,
        channelName: String,
        reached: Int,
        startedAt: TimeMark,
    ) {
        state = UpdateState.Available(label, release)
        platform.showMessage(getString(Res.string.about_download_too_slow))
        if (!auto) platform.openUrl(DOWNLOAD_PAGE_URL)
        telemetry?.recordUpdate(
            stage = UpdateReport.STAGE_DOWNLOAD,
            outcome = UpdateReport.OUTCOME_CANCELLED,
            channel = channelName,
            version = label,
            reason = "slow",
            progress = reached,
            elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
        )
    }

    /** 停掉正在跑的下载；卡片退回到那个还没下的版本，而不是一张空白。 */
    fun cancelDownload() {
        if (state !is UpdateState.Downloading) return
        downloadJob?.cancel()
        downloadJob = null
        val release = lastRelease
        state = release?.let { UpdateState.Available(updateLabel(it), it) } ?: UpdateState.Idle
        release?.let { stopped ->
            // channel() 是挂起的（读设置），所以整段上报放进协程里；取消本身已经发生，
            // 上报什么时候完成都不影响卡片。
            scope.launch {
                val channelName = channelName(channel())
                telemetry?.recordUpdate(
                    stage = UpdateReport.STAGE_DOWNLOAD,
                    outcome = UpdateReport.OUTCOME_CANCELLED,
                    channel = channelName,
                    version = updateLabel(stopped),
                )
            }
        }
    }

    /**
     * 装 [ready]：一次安装只在这里，自动和手动都调它。
     *
     * 说话只说 toast：开始时一句（这是应用被替换前最后看到的东西），结束时一句（成功 / 交给系统
     * 安装器 / 装不上）。中间那些阶段只改 [state]，不再逐条打印文字。
     *
     * 失败要分两种（见 [InstallAttempt]）：运行时那一刻没就绪的，这一版**不算装不上** —— 不进
     * 「暂停自动安装」、不甩给系统安装器，等一会儿自己再试；只有 `pm` 真的拒绝了这个包，才按旧的
     * 兜底走。混在一起就是「下载了却不自动装」：启动那一刻 Shizuku 还没起，一次就够钉死一版。
     */
    fun install(ready: UpdateState.ReadyToInstall) {
        if (installing) return
        installing = true
        state = UpdateState.Installing(ready.version, ready.path)
        scope.launch {
            val channelName = channelName(channel())
            val startedAt = TimeSource.Monotonic.markNow()
            platform.showMessage(getString(Res.string.about_installing_version, ready.version))
            // Every route `pm install` is offered to is reported back here, so the record names the
            // route the failure happened on; the card only draws the stage as progress.
            var lastStage = InstallStage.PREPARING
            val attempt = platform.installUpdate(ready.path) { stage, percent ->
                lastStage = stage
                state = UpdateState.Installing(ready.version, ready.path, stage, percent)
            }
            val elapsed = startedAt.elapsedNow().inWholeMilliseconds
            val failure = attempt.failure
            if (failure == null) {
                telemetry?.recordUpdate(
                    stage = UpdateReport.STAGE_INSTALL,
                    outcome = UpdateReport.OUTCOME_OK,
                    channel = channelName,
                    version = ready.version,
                    elapsedMs = elapsed,
                )
                platform.showMessage(getString(Res.string.about_install_done, ready.version))
                // Let the line be read before the APK's process goes away; closing at once is
                // indistinguishable from a crash, which is what people kept reporting.
                delay(INSTALL_DONE_LINGER_MILLIS)
                platform.exitApp()
                // 桌面 / 网页版里 exitApp 不会真的结束进程：把闸门放开，手动重试还能用。
                installing = false
            } else if (attempt.retryable && noteNotReady(ready.version)) {
                // 环境没就绪：这个包本身没问题。记一条之后把卡片退回「已下载」，隔一会儿自己再来
                // 一次 —— 绝大多数情况是 App 先起来了、Shizuku 的用户服务还在绑。
                //
                // 屏幕上必须给出原因（第一条通道的原话）：以前这一步什么都不说，只把卡片留在
                // 「已下载，点击安装」，看起来就是「点了安装、什么也没发生」。
                telemetry?.recordUpdate(
                    stage = UpdateReport.STAGE_INSTALL,
                    outcome = UpdateReport.OUTCOME_FAILED,
                    channel = channelName,
                    version = ready.version,
                    code = "NOT_READY",
                    reason = failure,
                    detail = "stage=$lastStage\n$failure",
                    elapsedMs = elapsed,
                )
                platform.showMessage(
                    getString(Res.string.about_install_failed, firstReason(failure)),
                )
                state = UpdateState.ReadyToInstall(ready.version, ready.path)
                installing = false
                scope.launch {
                    delay(INSTALL_NOT_READY_RETRY_DELAY_MILLIS)
                    // 这中间用户可能自己点了「以后再说」，也可能换了渠道：只在这张卡片还停在
                    // 「已下载」时才续上，不抢别人的状态。
                    if (state is UpdateState.ReadyToInstall) install(ready)
                }
            } else {
                // This build will not install itself on this phone: a foreign signature, a ROM that
                // refuses `pm install`, a runtime that stopped. Remembering the version is what
                // stops the next launch from downloading it again and showing the same endless
                // "正在安装" — the About page keeps the manual retry.
                settings.setSkippedAutoInstallVersion(ready.version)
                if (platform.requestInstall(ready.path)) {
                    telemetry?.recordUpdate(
                        stage = UpdateReport.STAGE_HANDOFF,
                        outcome = UpdateReport.OUTCOME_OK,
                        channel = channelName,
                        version = ready.version,
                        detail = failure,
                        elapsedMs = elapsed,
                    )
                    platform.showMessage(getString(Res.string.about_install_handoff, ready.version))
                    state = UpdateState.HandedOff(ready.version)
                } else {
                    telemetry?.recordUpdate(
                        stage = UpdateReport.STAGE_INSTALL,
                        outcome = UpdateReport.OUTCOME_FAILED,
                        channel = channelName,
                        version = ready.version,
                        code = "REFUSED_ALL_ROUTES",
                        reason = failure,
                        // The last route tried, then every route's own refusal: together they say
                        // whether it was permissions or the APK the package manager refused.
                        detail = "stage=$lastStage\n$failure",
                        elapsedMs = elapsed,
                    )
                    // 逐条的 pm 原文只进 update_reports（上面的 detail）；屏幕上只留一句人话。
                    val refused = getString(Res.string.about_install_refused)
                    state = UpdateState.FailedWith(refused)
                    platform.showMessage(refused)
                }
                installing = false
            }
        }
    }

    /**
     * 记一次「环境没就绪」的重试，回答还剩几次。
     *
     * 上限存在的理由：Shizuku 真的没装、真的没授权时，无限重试就是无限弹「正在安装」。到上限之后
     * 走旧的兜底（交给系统安装器 + 不再自动装这一版），和以前一样，只是多等了一两轮。
     */
    private fun noteNotReady(version: String): Boolean {
        if (notReadyVersion != version) {
            notReadyVersion = version
            notReadyRetries = 0
        }
        if (notReadyRetries >= INSTALL_NOT_READY_RETRIES) return false
        notReadyRetries += 1
        return true
    }

    private suspend fun channel(): UpdateChannel =
        if (settings.updateChannelFlow.first() == "dev") UpdateChannel.DEVELOPMENT else UpdateChannel.STABLE

    private fun channelName(channel: UpdateChannel): String =
        if (channel == UpdateChannel.DEVELOPMENT) "dev" else "stable"
}

/**
 * The same for a download that did not finish. Every one of these is read together with the
 * download-page row under the card, so each sentence only has to say what went wrong.
 */
private fun UpdateDownloadFailure.reason(): StringResource = when (this) {
    UpdateDownloadFailure.NETWORK -> Res.string.about_download_failed_network
    UpdateDownloadFailure.TIMEOUT -> Res.string.about_download_failed_timeout
    UpdateDownloadFailure.STORAGE -> Res.string.about_download_failed_storage
    UpdateDownloadFailure.UNSUPPORTED -> Res.string.about_download_failed_unsupported
    UpdateDownloadFailure.UNKNOWN -> Res.string.about_download_failed
}

/**
 * 慢到什么程度就别再等这条连接了：20 秒还没到 5%，或者 12 秒一个百分点都没动。
 *
 * 这两个数只用来判断「明显不正常」：正常情况下 26 MB 的包在 20 秒里早就过 5% 了，而在手机上
 * 挂着代理、连接被限速或断流时，往往是十几秒纹丝不动。判断保守一点，让它只抓真正卡住的下载。
 */
private const val SLOW_EARLY_SECONDS = 20
private const val SLOW_EARLY_PERCENT = 5
private const val SLOW_STALL_SECONDS = 12

/**
 * 「运行时没就绪」这一版最多自己再试几次，以及两次之间等多久。
 *
 * 20 秒是照着 Shizuku 用户服务起来的节奏定的：管理器刚被系统拉起来时，第一次绑定常常超时，隔十几秒
 * 再绑就好。次数给 2：够救回启动那一次，又不会让「真的没装 Shizuku」的手机一直停在「正在安装」。
 */
private const val INSTALL_NOT_READY_RETRIES = 2
private const val INSTALL_NOT_READY_RETRY_DELAY_MILLIS = 20_000L

/**
 * 失败原因的第一行，用于 toast。
 *
 * `installUpdate` 的失败文字是一份逐通道的报告（每行一条通道），整份进 `update_reports.detail`；
 * 屏幕上只放得下一行，就是第一条通道自己说的那句 —— 它已经带了 Shizuku 那边返回的原话。
 */
private fun firstReason(failure: String): String =
    failure.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(140).orEmpty()

/** 这条下载是不是慢到该停下、改去下载页。没有 `Content-Length`（[percent] 为负）时量不出速度，就不猜。 */
private fun isDownloadTooSlow(startedAt: TimeMark, lastChange: TimeMark, percent: Int): Boolean {
    if (percent < 0) return false
    // 快下完了就别打断：剩下的那点时间比重新去网页下载更短。
    if (percent >= 95) return false
    if (lastChange.elapsedNow().inWholeSeconds >= SLOW_STALL_SECONDS) return true
    return percent <= SLOW_EARLY_PERCENT && startedAt.elapsedNow().inWholeSeconds >= SLOW_EARLY_SECONDS
}
