package io.github.samolego.canta.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.util.DeviceIdentity
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.PackageTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bump to show the onboarding again to everyone (existing users have 0). */
const val CURRENT_ONBOARDING_VERSION = 3

/**
 * Exposes [CantaSettings] as [StateFlow]s for the UI. Every value is derived
 * from the store, so the UI can never drift from what is persisted.
 *
 * [repository] is only used for the user's display name: it is the one setting that belongs to
 * the person rather than to the phone, so it is written through to the backend and pulled back on
 * start. Everything else stays on the device.
 */
class SettingsViewModel(
    private val settingsStore: CantaSettings,
    private val packageTelemetry: PackageTelemetry? = null,
    private val repository: PlanRepository? = null,
    /** The phone's stable lookup key, so a reinstall gets its old identity back. */
    private val deviceKey: String = "",
    /**
     * This phone, read when the identity is claimed. Sent with the bind so the backend knows which
     * model an install is even when the user never opens the community page — the catalog counts
     * people per model, and an identity with no model is a person nobody can count.
     */
    private val deviceProfile: suspend () -> DeviceProfile? = { null },
    /**
     * Which privilege route is in force right now, read at the moment the identity is claimed
     * (`root`, `shizuku`, `dhizuku`, `none`). Reported with the bind so the backend can say how
     * many installs run on each route — the Dhizuku one is new and would otherwise be invisible.
     */
    private val privilege: () -> String = { "" },
) : ViewModel() {

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    private fun save(block: suspend CantaSettings.() -> Unit) {
        viewModelScope.launch { settingsStore.block() }
    }

    val autoUpdateBloatList = settingsStore.autoUpdateBloatListFlow.state(true)
    val confirmBeforeUninstall = settingsStore.confirmBeforeUninstallFlow.state(true)
    val bloatListUrl = settingsStore.bloatListUrlFlow.state("")
    val commitsUrl = settingsStore.commitsUrlFlow.state("")
    val allowUnsafeUninstalls = settingsStore.allowUnsafeUninstallsFlow.state(false)
    val hideSuccessDialog = settingsStore.hideSuccessDialogFlow.state(false)
    val authEnabled = settingsStore.authEnabledFlow.state(false)

    val analyticsEnabled = settingsStore.analyticsEnabledFlow.state(false)

    /** The display name reviews and comments are signed with; the avatar is its first character. */
    val userName = settingsStore.userNameFlow.state("")

    /**
     * The number the backend handed this install, or 0 before it has one.
     *
     * The settings page shows it as "#10134" while no name has been picked: that number is what
     * everybody else sees this install as, which is the reason the page keeps asking them to pick
     * something better.
     */
    val identityUserId = settingsStore.identityUserIdFlow.state(0L)

    /** The optional bar entries: off by default, turned on in the settings. */
    val showTweaksTab = settingsStore.showTweaksTabFlow.state(false)
    val showRecommendTab = settingsStore.showRecommendTabFlow.state(false)

    /** Which arrangement the recommendations page was left in: false grid, true list. */
    val recommendationsListMode = settingsStore.recommendationsListModeFlow.state(false)

    /** Whether the safety page names this phone and opens on its picture; off unless turned on. */
    val showDevicePhoto = settingsStore.showDevicePhotoFlow.state(false)

    /** Whether the records tab empties itself at the next system start; off unless turned on. */
    val clearRecordsAfterBoot = settingsStore.clearRecordsAfterBootFlow.state(false)

    /**
     * 列表里点一个包是弹小窗（`true`）还是直接进详情页（`false`，出厂）。
     * 存的是"弹窗"，所以界面上那个「点击应用打开详情页」的开关读的是它的反；
     * 见 `AppSettingsProto.appTapOpensDialog`。
     */
    val appTapOpensDialog = settingsStore.appTapOpensDialogFlow.state(false)

    /** Downloads a found release on its own; [confirmBeforeInstall] decides whether it waits for a tap. */
    val autoInstallUpdates = settingsStore.autoInstallUpdatesFlow.state(true)
    /** Whether installing a downloaded release asks first; off means it installs right away. */
    val confirmBeforeInstall = settingsStore.confirmBeforeInstallFlow.state(false)
    val updateChannel = settingsStore.updateChannelFlow.state<String?>(null)

    /** The accent the app draws with, as ARGB; 0 is FlClash's own `#007AFF`. */
    val themeAccent = settingsStore.themeAccentFlow.state(0)
    /** Dark mode: 0 follows the system, 1 forces light, 2 forces dark. */
    val themeMode = settingsStore.themeModeFlow.state(0)

    /** Whether the "no VPN" reminder was turned off for good; off unless the reader said so. */
    val hideVpnNotice = settingsStore.hideVpnNoticeFlow.state(false)

    /**
     * The question the start-up lock asks and the date that answers it; both empty means no lock.
     *
     * Null until the store has been read, like [onboardingVersion]: the lock screen must never be
     * decided from a "not loaded yet" empty answer, or an install with a lock would flash the app
     * list for a frame before the question appeared. The two are read separately because they are
     * two fields, and the caller builds the gate with `launchGateOf`, which is also what decides
     * whether a stored pair is usable at all.
     */
    val launchQuestion: StateFlow<String?> = settingsStore.launchQuestionFlow.state<String?>(null)
    val launchAnswer: StateFlow<String?> = settingsStore.launchAnswerFlow.state<String?>(null)

    /**
     * 新消息要不要往系统通知栏弹；`false` 是出厂状态（要弹）。
     *
     * 存的是"静音"而不是"开启"，理由在 `AppSettingsProto.muteChatNotices` 上：proto3 省略零值，
     * 默认值只能是 `false`，而这件事的出厂状态是开着的。界面把它读成"消息通知"那个开关。
     */
    val muteChatNotices = settingsStore.muteChatNoticesFlow.state(false)

    /**
     * 关于页「作者的群」那一行的红点是不是点过了。出厂是没点过（`false`），点一次就永久熄灭。
     */
    val qqGroupSeen = settingsStore.qqGroupSeenFlow.state(false)

    /**
     * 各会话没发出去的草稿，`会话号 -> 正文`，最近写的在最后。
     *
     * 给界面读的是一份快照（`Map`）：`ChatScreen` 只需要在进来那一刻问一句「这个会话有没有草稿」，
     * 之后由它自己拿着 `draft` 改，不需要跟着每条草稿的写入重组整个页面。
     */
    val chatDrafts = settingsStore.chatDraftsFlow.state(emptyList<Pair<Long, String>>())

    /**
     * 安全页顶部机型胶囊是不是点过了。出厂是没点过（`false`），点一次就永久停止呼吸提示。
     */
    val scopeHintSeen = settingsStore.scopeHintSeenFlow.state(false)

    /**
     * Whether the lock has been answered in this process.
     *
     * It lives here rather than in the composition for two reasons. Turning on the lock from the
     * about page must not lock the page that just turned it on — the writer is obviously the
     * owner — and a configuration change (rotation, dark mode, font size) rebuilds the activity,
     * which would otherwise ask for the answer again although the person never left the app.
     *
     * A ViewModel does not survive the process, so a real restart still asks: that is the whole
     * point of the feature.
     */
    private val _sessionUnlocked = MutableStateFlow(false)
    val sessionUnlocked: StateFlow<Boolean> = _sessionUnlocked.asStateFlow()

    /** The keypad was answered, or the owner just set or cleared the lock. */
    fun markSessionUnlocked() {
        _sessionUnlocked.value = true
    }

    /** The app left the screen: the next look at it starts behind the question again. */
    fun markSessionLocked() {
        _sessionUnlocked.value = false
    }

    /** Null until the stored settings have loaded, so the UI doesn't flash the wrong screen. */
    val onboardingVersion: StateFlow<Int?> = settingsStore.onboardingVersionFlow.state<Int?>(null)

    init {
        viewModelScope.launch { syncName() }
    }

    /**
     * Claims this phone's identity and pulls the name that belongs to it.
     *
     * The device key is what makes a reinstall look like the same person: when this phone bound a
     * token before, the backend answers with that old token and the app adopts it, plus the number
     * and the name that came with it. A local name the cloud does not have yet is pushed up.
     */
    private suspend fun syncName() {
        val repo = repository ?: return
        val localToken = DeviceIdentity.ensure(settingsStore)
        // Best effort: a read that fails (no helper, no root) leaves the model empty here and the
        // backend keeps whatever an earlier call knew.
        val phone = runCatching { deviceProfile() }.getOrNull()
        val remote = repo.identify(localToken, deviceKey, phone, privilege()) ?: return
        // Adopt the token the phone already owned, if this is a reinstall.
        val token = remote.token.ifEmpty { localToken }
        if (token != localToken) settingsStore.setIdentityToken(token)
        if (remote.userId > 0) settingsStore.setIdentityUserId(remote.userId)
        val local = settingsStore.userNameFlow.first().trim()
        when {
            local.isEmpty() && remote.name.isNotEmpty() -> settingsStore.setUserName(remote.name)
            local.isNotEmpty() && local != remote.name -> repo.setName(token, local)
        }
    }

    /** Records the onboarding choice; [contribute] is the analytics opt-in. */
    fun completeOnboarding(contribute: Boolean) = save {
        setAnalyticsEnabled(contribute)
        setOnboardingVersion(CURRENT_ONBOARDING_VERSION)
    }

    fun setAnalyticsEnabled(enabled: Boolean) = save {
        setAnalyticsEnabled(enabled)
        if (!enabled) packageTelemetry?.clearPending() ?: setPendingReboot(emptyList())
    }

    fun saveAutoUpdateBloatList(autoUpdate: Boolean) = save { setAutoUpdateBloatList(autoUpdate) }
    fun saveConfirmBeforeUninstall(confirm: Boolean) = save { setConfirmBeforeUninstall(confirm) }
    fun saveBloatListUrl(url: String) = save { setBloatListUrl(url) }
    fun saveCommitsUrl(url: String) = save { setCommitsUrl(url) }
    fun saveAllowUnsafeUninstalls(allow: Boolean) = save { setAllowUnsafeUninstalls(allow) }
    fun saveHideSuccessDialog(hide: Boolean) = save { setHideSuccessDialog(hide) }
    fun saveAuthEnabled(enabled: Boolean) = save { setAuthEnabled(enabled) }
    fun saveShowTweaksTab(show: Boolean) = save { setShowTweaksTab(show) }
    fun saveShowRecommendTab(show: Boolean) = save { setShowRecommendTab(show) }
    fun saveRecommendationsListMode(list: Boolean) = save { setRecommendationsListMode(list) }
    fun saveShowDevicePhoto(show: Boolean) = save { setShowDevicePhoto(show) }
    fun saveClearRecordsAfterBoot(clear: Boolean) = save { setClearRecordsAfterBoot(clear) }
    /** 列表里点一个包：弹小窗（`true`）或直接进详情页（`false`，出厂）。 */
    fun saveAppTapOpensDialog(showsDialog: Boolean) = save { setAppTapOpensDialog(showsDialog) }
    fun saveAutoInstallUpdates(auto: Boolean) = save { setAutoInstallUpdates(auto) }
    fun saveConfirmBeforeInstall(confirm: Boolean) = save { setConfirmBeforeInstall(confirm) }
    fun saveUpdateChannel(channel: String) = save { setUpdateChannel(channel) }
    fun saveThemeAccent(argb: Int) = save { setThemeAccent(argb) }
    fun saveThemeMode(mode: Int) = save { setThemeMode(mode) }

    /** "不再提示" on the proxy reminder: persisted, so the next start does not ask again. */
    fun saveHideVpnNotice(hide: Boolean) = save { setHideVpnNotice(hide) }

    /**
     * Sets the start-up lock to [questionId] answered by [answer] (`YYYY-MM-DD`), or clears it when
     * both are empty.
     *
     * The session counts as answered either way: whoever just picked the question and typed the
     * date in is the owner, and dropping them onto their own lock screen the instant they set it
     * would look like the app forgot what they were doing.
     */
    fun saveLaunchGate(questionId: String, answer: String) {
        markSessionUnlocked()
        save { setLaunchGate(questionId, answer) }
    }

    /** Turns the start-up lock off. */
    fun clearLaunchGate() = saveLaunchGate("", "")

    /** 静音（`true`）或恢复（`false`）新消息的通知栏提醒。 */
    fun saveMuteChatNotices(mute: Boolean) = save { setMuteChatNotices(mute) }

    /** 「作者的群」那行点过了：红点熄灭，而且不再亮起来。 */
    fun saveQqGroupSeen(seen: Boolean) = save { setQqGroupSeen(seen) }

    /** 记下（或清掉，传空串）一个会话的草稿；防抖在调用方那边，这里每次都落盘。 */
    fun saveChatDraft(conversationId: Long, text: String) = save { setChatDraft(conversationId, text) }

    /** 顶部机型胶囊点过了：呼吸提示停掉，以后只当普通按钮。 */
    fun saveScopeHintSeen(seen: Boolean) = save { setScopeHintSeen(seen) }

    /**
     * Renames this install: the value is stored at once, so the page and the next review use it
     * even offline, and written to the backend so the same token gets it back anywhere.
     */
    fun saveUserName(name: String) {
        val trimmed = name.trim()
        viewModelScope.launch {
            settingsStore.setUserName(trimmed)
            val repo = repository ?: return@launch
            val token = DeviceIdentity.ensure(settingsStore)
            repo.setName(token, trimmed)?.let { if (it.userId > 0) settingsStore.setIdentityUserId(it.userId) }
        }
    }
}
