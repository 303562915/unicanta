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
import io.github.samolego.canta.generated.resources.about_install_done
import io.github.samolego.canta.generated.resources.about_install_handoff
import io.github.samolego.canta.generated.resources.about_installing_version
import io.github.samolego.canta.generated.resources.about_update_download_toast
import io.github.samolego.canta.ui.screen.DOWNLOAD_PROGRESS_UNKNOWN
import io.github.samolego.canta.ui.screen.INSTALL_DONE_LINGER_MILLIS
import io.github.samolego.canta.ui.screen.UpdateChannel
import io.github.samolego.canta.ui.screen.UpdateCheckException
import io.github.samolego.canta.ui.screen.UpdateFailure
import io.github.samolego.canta.ui.screen.UpdateRelease
import io.github.samolego.canta.ui.screen.UpdateState
import io.github.samolego.canta.ui.screen.downloadReleaseWithRetries
import io.github.samolego.canta.ui.screen.isNewerThanInstalled
import io.github.samolego.canta.ui.screen.latestRelease
import io.github.samolego.canta.ui.screen.latestReleaseFromPage
import io.github.samolego.canta.ui.screen.updateLabel
import io.github.samolego.canta.util.PackageTelemetry
import io.github.samolego.canta.util.UpdateReport
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
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

    /** 安装四条通道全拒时的逐条说明；非空就由调用方弹窗，而不是挤在卡片那一行里。 */
    var installReport by mutableStateOf<String?>(null)
        private set

    /** 下载完了等一句话（「安装前确认」开关开着时才这样）。 */
    var pendingConfirm by mutableStateOf<UpdateState.ReadyToInstall?>(null)
        private set

    /** 同一时刻只允许一次安装：自动路径和手动路径可能同时走到这里。 */
    private var installing = false

    private var downloadJob: Job? = null

    /** 正在下（或刚下完）的那个版本，取消下载之后要退回到它，而不是退回一张空白卡片。 */
    private var lastRelease: UpdateRelease? = null

    /** 切换更新通道之后：手上这份状态说的是另一个通道的版本，不能再画。 */
    fun onChannelChanged() {
        downloadJob?.cancel()
        downloadJob = null
        lastRelease = null
        pendingConfirm = null
        installReport = null
        state = UpdateState.Idle
    }

    fun dismissInstallReport() {
        installReport = null
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
                    label != settings.skippedAutoInstallVersionFlow.first() -> startDownload(found)
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
    fun startDownload(release: UpdateRelease) {
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
            // Not one shot: the rounds and the platform's resume keep a flapping proxy from
            // turning into "download failed, tap it yourself again".
            val result = downloadReleaseWithRetries(
                platform = platform,
                tag = release.tag,
                buildNumber = release.buildNumber,
                onRound = { attempt ->
                    // Every round but the first follows a failed one: back to a spinner instead of
                    // leaving an error sentence on screen while the next attempt is already running.
                    if (attempt > 1) state = UpdateState.Downloading(label, DOWNLOAD_PROGRESS_UNKNOWN)
                },
                onProgress = { percent ->
                    reached = percent
                    state = UpdateState.Downloading(label, percent)
                },
            )
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
            val failure = platform.installUpdate(ready.path) { stage, percent ->
                lastStage = stage
                state = UpdateState.Installing(ready.version, ready.path, stage, percent)
            }
            val elapsed = startedAt.elapsedNow().inWholeMilliseconds
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
                    state = UpdateState.FailedWith(failure)
                    installReport = failure
                }
                installing = false
            }
        }
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
