package io.github.samolego.canta.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.SavedChat
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.data.proto.AppSettingsProto
import io.github.samolego.canta.util.DeviceIdentity
import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.IdentitySetup
import io.github.samolego.canta.util.PackageTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    /**
     * 自己设的头像直链；空串 = 没设过，界面画首字母圆。
     *
     * 出厂就是空串，和 [userName] 一样是"读了才知道"之前也可以安全用的值：没有头像本来就是这个
     * 默认状态，页面不会先闪一张别人的图再换回来。
     */
    val avatarUrl = settingsStore.avatarUrlFlow.state("")

    /**
     * 这台机器的身份号（`#10003` 里的那个数字），读到之后才非空。
     *
     * 只为 [identitySetup] 服务：本机没存头像直链时，靠它把地址推回来（见那道门的注释）。
     *
     * **必须跟着存储的 flow 走，不能只读一次**。以前这里是 `init` 里 `DeviceIdentity.current()`
     * 读一遍：那一次读排在 `syncName()` 前面（同一个 `viewModelScope`，先注册先跑），而
     * **刚清完应用数据**时本机还没有身份号——`identify()` 是网络回来的，晚一步才由
     * `setIdentityUserId` 写进来。于是这一次读拿到 0，`avatarOwner` 永远空着，这道门就一直
     * 关着：头像在图床上明明还在（别的页面按身份号推得出来、也画出来了），这里却一直说
     * 「换头像」，还不让人发言。改成订阅 `identityUserIdFlow`，身份号一到就当场变绿，不用重启。
     */
    private val avatarOwner: StateFlow<String> =
        settingsStore.identityUserIdFlow
            .map { DeviceIdentity.ownerOf(it) }
            .state("")

    /**
     * 名字与头像都设过了没有——**没设过就不许往社区里写任何东西**（见 [IdentitySetup]）。
     *
     * 由 [userName] 与 [avatarUrl] 合出来，所以改名或换头像当场就变绿，不用重启；两样都没读出来
     * 的那一版是 `UNSET`，也就是"按没设过处理"，没人能在设置还没读到的那几帧里先发一条。
     */
    private val storedIdentitySetup: Flow<IdentitySetup> =
        combine(settingsStore.userNameFlow, settingsStore.avatarUrlFlow) { name, avatar ->
            IdentitySetup(name = name, avatarUrl = avatar)
        }

    val identitySetup: StateFlow<IdentitySetup> =
        combine(storedIdentitySetup, avatarOwner) { setup, owner ->
            // 本机没存直链 ≠ 没设过头像。头像上传是覆盖式的、后端没有头像列，图床对象名固定是
            // `unicanta/头像/<身份号>.jpg`（见 `ImageBed.avatarId`），所以清一次应用数据之后
            // 这扇门不该关着——地址能从身份号推回来（见 `personAvatarUrl`）。
            //
            // 这里只解决"有没有"：`ready` 只回答"能不能发言"，不回答"图片下载到了没有"。
            // 图床此刻被拦着、图下不下来，是网络的事，不该把人锁在"没换头像"里。
            if (setup.hasAvatar || owner.isEmpty()) setup else setup.copy(avatarUrl = owner)
        }.state(IdentitySetup.UNSET)

    /** The optional bar entries: off by default, turned on in the settings. */
    val showTweaksTab = settingsStore.showTweaksTabFlow.state(false)
    val showRecommendTab = settingsStore.showRecommendTabFlow.state(false)
    /** 底栏要不要给帖子页留一格。 */
    val showPostsTab = settingsStore.showPostsTabFlow.state(true)
    /** 底栏要不要给商城页留一格：出厂开着，与帖子同类。 */
    val showStoreTab = settingsStore.showStoreTabFlow.state(true)

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
    /**
     * The release whose automatic install already failed. The start-up check reads it so a build
     * that would not install is not downloaded and retried on every launch.
     */
    val skippedAutoInstallVersion = settingsStore.skippedAutoInstallVersionFlow.state("")
    val updateChannel = settingsStore.updateChannelFlow.state<String?>(null)

    /** The accent the app draws with, as ARGB; 0 is FlClash's own `#007AFF`. */
    val themeAccent = settingsStore.themeAccentFlow.state(0)
    /** Dark mode: 0 follows the system, 1 forces light, 2 forces dark. */
    val themeMode = settingsStore.themeModeFlow.state(0)

    /**
     * 手动挑的界面语言，BCP-47 标签；空串 = 跟随系统。
     *
     * 初值取盘上那一份而不是空串：空串会让应用先按系统语言画一遍、等这一格到了再按新语言整棵树
     * 重建一次，看起来像"刚打开就被刷了一下"。
     */
    val appLanguage = settingsStore.appLanguageFlow.state(settingsStore.appLanguageNow)

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

    /** 关于页最下面那张「最近更新」是否展开；出厂折叠，展开与否记在设置里。 */
    val updateHistoryExpanded = settingsStore.updateHistoryExpandedFlow.state(false)
    /** 关于页「赞助者（贡献值）」展开没展开；出厂折叠。 */
    val sponsorsExpanded = settingsStore.sponsorsExpandedFlow.state(false)

    /**
     * 各会话没发出去的草稿，`会话号 -> 正文`，最近写的在最后。
     *
     * 给界面读的是一份快照（`Map`）：`ChatScreen` 只需要在进来那一刻问一句「这个会话有没有草稿」，
     * 之后由它自己拿着 `draft` 改，不需要跟着每条草稿的写入重组整个页面。
     */
    val chatDrafts = settingsStore.chatDraftsFlow.state(emptyList<Pair<Long, String>>())

    /**
     * 本机收藏的聊天消息，收得最晚的在最后。
     *
     * 和草稿一样给一份快照：聊天页只在进这一屏时按会话号筛一遍，收藏/取消收藏会写回设置，
     * 下一帧自然跟着变。
     */
    val chatSaved = settingsStore.chatSavedFlow.state(emptyList<SavedChat>())

    /**
     * 本机拉黑过的人（身份号）。
     *
     * 后端没有拉黑接口，这份名单只落在设置文件里：消息页据此把这些人的私聊从列表里滤掉。
     * 界面只问「这个人在不在里面」，所以在这一层就换成 `Set`，别让每个调用点各转一次。
     */
    val blockedPeers: StateFlow<Set<String>> =
        settingsStore.blockedPeersFlow.map { peers -> peers.toSet() }.state(emptySet<String>())

    /** 消息页上本人删掉的那几行（官方群名 / 会话号）；见 [AppSettingsProto.hiddenMessageRows]。 */
    val hiddenMessageRows: StateFlow<Set<String>> =
        settingsStore.hiddenMessageRowsFlow.map { rows -> rows.toSet() }.state(emptySet<String>())

    /**
     * 安全页顶部机型胶囊是不是点过了。出厂是没点过（`false`），点一次就永久停止呼吸提示。
     */
    val scopeHintSeen = settingsStore.scopeHintSeenFlow.state(false)

    /**
     * 「选择机型」上一次挑的是哪一台（见 `PlanScope.toStoredValue`，空串 = 全部机型）。
     *
     * **为什么放在这里而不是页面自己订阅**：页面级的 `collectAsStateWithLifecycle(initialValue = "")`
     * 在页面重新进入时（从应用详情返回、切 tab）第一帧拿到的是那个 `initialValue`，也就是空串 =
     * 「全部机型」，要等协程把存储读回来才换成真正选的那台。那一段中间态就是用户说的
     * 「每次退出来都先显示全部，然后再跳到我们之前选的」。
     *
     * 这个 ViewModel 跟着导航图活，比页面久：它一早就把值收进 `StateFlow`，页面任何时候读到的都是
     * 已经读好的那一份，不会先画一遍「全部机型」。
     *
     * **初值必须是存储里的现有值**，不能写 `state("")`：那个空串就是「全部机型」，首帧照样会先画
     * 全部再跳回来。`settingsStore.planScopeNow` 是同步读的现有值（存储构造时就已从磁盘读完），
     * 所以拿到的是真值。
     */
    val planScope = settingsStore.planScopeFlow.state(settingsStore.planScopeNow)

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
    fun saveShowPostsTab(show: Boolean) = save { setShowPostsTab(show) }
    fun saveShowStoreTab(show: Boolean) = save { setShowStoreTab(show) }
    fun saveRecommendationsListMode(list: Boolean) = save { setRecommendationsListMode(list) }
    fun saveShowDevicePhoto(show: Boolean) = save { setShowDevicePhoto(show) }
    fun saveClearRecordsAfterBoot(clear: Boolean) = save { setClearRecordsAfterBoot(clear) }
    /** 列表里点一个包：弹小窗（`true`）或直接进详情页（`false`，出厂）。 */
    fun saveAppTapOpensDialog(showsDialog: Boolean) = save { setAppTapOpensDialog(showsDialog) }
    fun saveAutoInstallUpdates(auto: Boolean) = save { setAutoInstallUpdates(auto) }
    fun saveConfirmBeforeInstall(confirm: Boolean) = save { setConfirmBeforeInstall(confirm) }
    /** Records that the automatic install of [version] failed; empty clears the mark. */
    fun saveSkippedAutoInstallVersion(version: String) = save { setSkippedAutoInstallVersion(version) }

    /**
     * 清掉某一版的安装失败次数。
     *
     * 用户明确点「重新尝试自动更新」时一起清：那个计数是跨进程的自动重试闸门，只清 skip 标记
     * 而不清它，`check()` 仍然会因为次数用尽而拒绝自动下载，那一下点击就没有效果。
     */
    fun clearInstallFailureCount(version: String) = save {
        if (version.isNotBlank()) this.clearInstallFailureCount(version)
    }
    fun saveUpdateChannel(channel: String) = save { setUpdateChannel(channel) }
    fun saveThemeAccent(argb: Int) = save { setThemeAccent(argb) }
    fun saveThemeMode(mode: Int) = save { setThemeMode(mode) }

    /** 选一门界面语言（BCP-47 标签）；空串 = 跟随系统。 */
    fun saveAppLanguage(tag: String) = save { setAppLanguage(tag) }

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

    /** 记住「最近更新」那张卡片是展开还是折叠。 */
    fun saveUpdateHistoryExpanded(expanded: Boolean) = save { setUpdateHistoryExpanded(expanded) }
    /** 记住关于页「赞助者（贡献值）」是展开还是折叠。 */
    fun saveSponsorsExpanded(expanded: Boolean) = save { setSponsorsExpanded(expanded) }

    /** 记下（或清掉，传空串）一个会话的草稿；防抖在调用方那边，这里每次都落盘。 */
    fun saveChatDraft(conversationId: Long, text: String) = save { setChatDraft(conversationId, text) }

    /** 收藏 / 取消收藏一条聊天消息；只落本机设置，后端不知道。 */
    fun saveChatSaved(entry: SavedChat, keep: Boolean) = save { setChatSaved(entry, keep) }

    /** 拉黑或取消拉黑一个人；后端没有这个接口，落的是本机设置里的那份名单。 */
    fun saveBlockedPeer(peer: String, blocked: Boolean) = save { setBlockedPeer(peer, blocked) }

    /** 消息页上删掉 / 恢复一行（官方群传群名，其余会话传会话号）。 */
    fun saveMessageRowHidden(key: String, hidden: Boolean) = save { setMessageRowHidden(key, hidden) }

    /**
     * 记下换好的头像直链（传空串就是清掉，界面回到首字母圆）。
     *
     * 只落本机设置：图在上传那一步已经进了图床，这里存的是它的地址。后端没有头像字段，所以这份
     * 地址只在这台手机上用——「我的」那张身份卡和个人页照着它去图床取图。
     */
    fun saveAvatarUrl(url: String) = save { setAvatarUrl(url) }

    /** 顶部机型胶囊点过了：呼吸提示停掉，以后只当普通按钮。 */
    fun saveScopeHintSeen(seen: Boolean) = save { setScopeHintSeen(seen) }

    /**
     * 记下「选择机型」挑的是哪一台（空串 = 全部机型）。
     *
     * 走 viewModel 而不是页面自己起协程：和 [saveScopeHintSeen] 同一条路，页面销毁不会把这次写入
     * 半路掐掉——"返回就变回全部机型"正是页面级状态被销毁的结果。
     */
    fun savePlanScope(scope: String) = save { setPlanScope(scope) }

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
