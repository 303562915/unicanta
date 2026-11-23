package io.github.samolego.canta.data

import io.github.samolego.canta.data.bloat.DEFAULT_BLOAT_COMMITS_URL
import io.github.samolego.canta.data.bloat.DEFAULT_BLOAT_URL
import io.github.samolego.canta.data.proto.AppSettingsProto
import io.github.samolego.canta.data.proto.PendingUninstallProto
import io.github.samolego.canta.data.proto.ProtoBlobStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Most restore suspects kept in the settings. */
private const val MAX_RESTORE_SUSPECTS = 20

/** 最多留几个会话的草稿；再多也没人会回到那么久以前。 */
private const val MAX_CHAT_DRAFTS = 20

/** 单条草稿最多存多少字：一条消息本身就被后端截到 4000，草稿存一半足够了。 */
private const val MAX_CHAT_DRAFT_CHARS = 2000

/**
 * 把 [AppSettingsProto.chatDrafts] 那段 JSON 解回 `会话号 -> 正文`，最近的在后。
 *
 * 坏数据给空列表而不是抛：一段草稿撑不起一次崩溃，而这段文本是可能被手改过的
 * （设置文件就在用户自己的存储里）。
 */
private fun decodeChatDrafts(raw: String): List<Pair<Long, String>> =
    if (raw.isBlank()) {
        emptyList()
    } else {
        runCatching {
            Json.Default.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                val row = element.jsonObject
                val id = row["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val text = row["t"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (text.isBlank()) null else id to text
            }
        }.getOrDefault(emptyList())
    }

/** [decodeChatDrafts] 的反向；没有草稿就存空串，和这个字段的出厂值一致。 */
private fun encodeChatDrafts(drafts: List<Pair<Long, String>>): String =
    if (drafts.isEmpty()) {
        ""
    } else {
        buildJsonArray {
            drafts.forEach { (id, text) ->
                add(
                    buildJsonObject {
                        put("id", id)
                        put("t", text)
                    },
                )
            }
        }.toString()
    }

/** Settings used when nothing has been stored yet. */
private val DEFAULT_SETTINGS = AppSettingsProto(
    autoUpdateBloatList = true,
    confirmBeforeUninstall = true,
)

/**
 * App settings, persisted as one protobuf-encoded [AppSettingsProto] in
 * [storage]; platforms only choose where that is. The URL flows already
 * resolve empty values to the defaults.
 */
class CantaSettings(storage: AppBlobStorage) {

    private val store =
        ProtoBlobStore(storage, AppSettingsProto.serializer(), DEFAULT_SETTINGS, "CantaSettings")

    private fun <T> field(read: (AppSettingsProto) -> T): Flow<T> = store.state.map(read)

    private suspend fun update(transform: (AppSettingsProto) -> AppSettingsProto) {
        store.update(transform)
    }

    val autoUpdateBloatListFlow = field { it.autoUpdateBloatList }
    val confirmBeforeUninstallFlow = field { it.confirmBeforeUninstall }
    val latestCommitHashFlow = field { it.latestBloatCommitHash }
    val bloatListUrlFlow = field { it.bloatListUrl.ifEmpty { DEFAULT_BLOAT_URL } }
    val commitsUrlFlow = field { it.commitsUrl.ifEmpty { DEFAULT_BLOAT_COMMITS_URL } }
    val allowUnsafeUninstallsFlow = field { it.allowUnsafeUninstalls }
    val hideSuccessDialogFlow = field { it.hideSuccessDialog }
    val authEnabledFlow = field { it.authEnabled }
    val analyticsEnabledFlow = field { it.analyticsEnabled }
    val userNameFlow = field { it.userName }
    val showTweaksTabFlow = field { it.showTweaksTab }
    val showRecommendTabFlow = field { it.showRecommendTab }
    /** Which arrangement the recommendations page was left in: false grid, true list. */
    val recommendationsListModeFlow = field { it.recommendationsListMode }
    /** Whether the safety page shows the picture of this phone; see [AppSettingsProto.showDevicePhoto]. */
    val showDevicePhotoFlow = field { it.showDevicePhoto }
    /** Downloads a found release on its own and offers the Shizuku install; see [AppSettingsProto.autoInstallUpdates]. */
    val autoInstallUpdatesFlow = field { it.autoInstallUpdates }
    /** Whether a downloaded release is installed at once instead of after a confirmation tap. */
    val confirmBeforeInstallFlow = field { it.confirmBeforeInstall }
    /** The accent the app draws with, as ARGB; 0 is FlClash's own `#007AFF`; see [AppSettingsProto.themeAccent]. */
    val themeAccentFlow: Flow<Int> = field { it.themeAccent }
    /** Dark mode: 0 follows the system, 1 forces light, 2 forces dark; see [AppSettingsProto.themeMode]. */
    val themeModeFlow: Flow<Int> = field { it.themeMode }
    /** Whether the "no VPN" reminder was switched off on this install; see [AppSettingsProto.hideVpnNotice]. */
    val hideVpnNoticeFlow = field { it.hideVpnNotice }
    /** The question the start-up lock asks, by id; empty means the app opens straight away. */
    val launchQuestionFlow: Flow<String> = field { it.launchQuestion }    /** The `YYYY-MM-DD` date that answers [launchQuestionFlow]; empty means the lock is off. */
    val launchAnswerFlow: Flow<String> = field { it.launchAnswer }
    /**
     * 新消息要不要往系统通知栏弹。存的是"静音"，所以 `false`（出厂值）就是提醒开着；
     * 为什么反过来命名见 [AppSettingsProto.muteChatNotices]。
     */
    val muteChatNoticesFlow: Flow<Boolean> = field { it.muteChatNotices }
    /** 关于页「作者的群」那一行的红点是不是点过了；点过就再也不亮，见 [AppSettingsProto.qqGroupSeen]。 */
    val qqGroupSeenFlow: Flow<Boolean> = field { it.qqGroupSeen }
    /** 安全页顶部机型胶囊是不是点过了；点过就不再呼吸，见 [AppSettingsProto.scopeHintSeen]。 */
    val scopeHintSeenFlow: Flow<Boolean> = field { it.scopeHintSeen }

    /**
     * 各会话没发出去的草稿，已经是解好的 `会话号 -> 正文`，最近写的在最后。
     *
     * 解在 flow 这一层而不是让调用方自己解析原始 JSON：只有这里的 [setChatDraft] 知道格式，
     * 界面不该跟着一起背。坏数据当空列表——一段草稿不值得让页面崩。
     */
    val chatDraftsFlow: Flow<List<Pair<Long, String>>> = field { decodeChatDrafts(it.chatDrafts) }
    /** Fresh installs follow the rolling dev build; an explicit stable choice is still stored and honoured. */
    val updateChannelFlow = field { it.updateChannel.ifBlank { "dev" } }
    /** Whether the records tab empties itself at the next system start; off by default. */
    val clearRecordsAfterBootFlow = field { it.clearRecordsAfterBoot }
    /**
     * 列表里点一个包是弹小窗（`true`）还是直接进详情页（`false`，出厂）；
     * 为什么反过来命名见 [AppSettingsProto.appTapOpensDialog]。
     */
    val appTapOpensDialogFlow: Flow<Boolean> = field { it.appTapOpensDialog }
    /** The boot the records check last ran on; see [AppSettingsProto.recordsBootId]. */
    val recordsBootIdFlow = field { it.recordsBootId }
    /** The vote queue as this install last saw it; see [AppSettingsProto.votesSeen]. */
    val votesSeenFlow: Flow<String> = field { it.votesSeen }
    val onboardingVersionFlow = field { it.onboardingVersion }
    val pendingRebootFlow: Flow<List<PendingUninstallProto>> = field { it.pendingReboot }
    val lastConfirmedPackagesFlow: Flow<List<String>> = field { it.lastConfirmedPackages }
    val restoreSuspectsFlow: Flow<List<String>> = field { it.restoreSuspects }

    /** The packages of an interrupted uninstall batch that never got an answer. */
    val interruptedBatchFlow: Flow<List<String>> = field { it.interruptedBatch }
    val interruptedBatchStartedAtFlow: Flow<Long> = field { it.interruptedBatchStartedAt }

    /**
     * 本机拉黑过的人（对方身份号）；见 [AppSettingsProto.blockedPeers]。
     *
     * 后端没有拉黑接口，这份名单只活在这台手机上：消息页用它过滤私聊，聊天页用它关输入框。
     */
    val blockedPeersFlow: Flow<List<String>> = field { it.blockedPeers }

    /** This install's identity token; empty until the plans page has created one. */
    val identityTokenFlow: Flow<String> = field { it.identityToken }

    /** The number that token owns on the backend; 0 until the first login. */
    val identityUserIdFlow: Flow<Long> = field { it.identityUserId }

    suspend fun setAutoUpdateBloatList(autoUpdate: Boolean) = update { it.copy(autoUpdateBloatList = autoUpdate) }
    suspend fun setConfirmBeforeUninstall(needsConfirm: Boolean) = update { it.copy(confirmBeforeUninstall = needsConfirm) }
    suspend fun setLatestCommitHash(hash: String) = update { it.copy(latestBloatCommitHash = hash) }
    suspend fun setBloatListUrl(url: String) = update { it.copy(bloatListUrl = url) }
    suspend fun setCommitsUrl(url: String) = update { it.copy(commitsUrl = url) }
    suspend fun setAllowUnsafeUninstalls(allow: Boolean) = update { it.copy(allowUnsafeUninstalls = allow) }
    suspend fun setHideSuccessDialog(hide: Boolean) = update { it.copy(hideSuccessDialog = hide) }
    suspend fun setAuthEnabled(enabled: Boolean) = update { it.copy(authEnabled = enabled) }
    suspend fun setAnalyticsEnabled(enabled: Boolean) = update { it.copy(analyticsEnabled = enabled) }
    suspend fun setUserName(name: String) = update { it.copy(userName = name) }
    suspend fun setShowTweaksTab(show: Boolean) = update { it.copy(showTweaksTab = show) }
    suspend fun setShowRecommendTab(show: Boolean) = update { it.copy(showRecommendTab = show) }
    suspend fun setRecommendationsListMode(list: Boolean) = update { it.copy(recommendationsListMode = list) }
    suspend fun setShowDevicePhoto(show: Boolean) = update { it.copy(showDevicePhoto = show) }
    suspend fun setAutoInstallUpdates(auto: Boolean) = update { it.copy(autoInstallUpdates = auto) }
    suspend fun setConfirmBeforeInstall(confirm: Boolean) = update { it.copy(confirmBeforeInstall = confirm) }
    suspend fun setThemeAccent(argb: Int) = update { it.copy(themeAccent = argb) }
    suspend fun setThemeMode(mode: Int) = update { it.copy(themeMode = mode.coerceIn(0, 2)) }
    /** Remembers that the "no VPN" reminder must never come back; see [AppSettingsProto.hideVpnNotice]. */
    suspend fun setHideVpnNotice(hide: Boolean) = update { it.copy(hideVpnNotice = hide) }
    /**
     * Sets or clears the start-up lock. Both halves are written in one call: a question without an
     * answer and an answer without a question are both "no lock", and a state where only one of
     * them landed would leave the page describing a lock the lock screen does not agree with.
     * Empty strings turn the lock off.
     */
    suspend fun setLaunchGate(question: String, answer: String) = update {
        it.copy(launchQuestion = question, launchAnswer = answer)
    }
    /** 静音（`true`）或恢复（`false`）新消息的通知栏提醒。 */
    suspend fun setMuteChatNotices(mute: Boolean) = update { it.copy(muteChatNotices = mute) }
    /** 记下「作者的群」那行已经点过了，红点不再出现。 */
    suspend fun setQqGroupSeen(seen: Boolean) = update { it.copy(qqGroupSeen = seen) }
    /** 记下顶部机型胶囊点过了，呼吸提示停掉。 */
    suspend fun setScopeHintSeen(seen: Boolean) = update { it.copy(scopeHintSeen = seen) }

    /**
     * 记下（或清掉）一个会话的草稿。[text] 空串等于删除这一条。
     *
     * 先过滤掉这个会话原有的那条再按需追加，所以重复改同一个会话不会在数组里堆一串。
     * 超过 [MAX_CHAT_DRAFTS] 就从头部裁：头部是写得最早的那个，草稿本来就是"过一阵就不想要了"
     * 的东西，先丢最旧的是唯一说得通的选择。
     */
    suspend fun setChatDraft(conversationId: Long, text: String) {
        val trimmed = text.take(MAX_CHAT_DRAFT_CHARS)
        update { current ->
            val kept = decodeChatDrafts(current.chatDrafts).filterNot { it.first == conversationId }
            val next = if (trimmed.isBlank()) kept else kept + (conversationId to trimmed)
            current.copy(chatDrafts = encodeChatDrafts(next.takeLast(MAX_CHAT_DRAFTS)))
        }
    }
    suspend fun setUpdateChannel(channel: String) = update {
        it.copy(updateChannel = if (channel == "dev") "dev" else "stable")
    }
    suspend fun setClearRecordsAfterBoot(clear: Boolean) = update { it.copy(clearRecordsAfterBoot = clear) }
    /** 列表里点一个包：弹小窗（`true`）或直接进详情页（`false`，出厂）。 */
    suspend fun setAppTapOpensDialog(showsDialog: Boolean) = update { it.copy(appTapOpensDialog = showsDialog) }
    suspend fun setRecordsBootId(bootId: String) = update { it.copy(recordsBootId = bootId) }

    suspend fun setVotesSeen(fingerprint: String) = update { it.copy(votesSeen = fingerprint) }
    suspend fun setOnboardingVersion(version: Int) = update { it.copy(onboardingVersion = version) }
    suspend fun setPendingReboot(pending: List<PendingUninstallProto>) = update { it.copy(pendingReboot = pending) }
    /**
     * Adds [packages] to the pending list and answers whether any of them was new. [update] only
     * returns Unit, so the answer is taken from inside the transform: "already pending" must not
     * be counted as a fresh baseline by the caller.
     */
    suspend fun seedPendingReboot(packages: List<PendingUninstallProto>): Boolean {
        if (packages.isEmpty()) return false
        val deviceKey = packages.firstOrNull()?.deviceKey.orEmpty()
        var added = false
        update { current ->
            val known = current.pendingReboot.filter { entry ->
                entry.deviceKey.isEmpty() || entry.deviceKey == deviceKey
            }.map { entry -> entry.pkg }.toSet()
            val fresh = packages.filterNot { entry -> entry.pkg in known }
            added = fresh.isNotEmpty()
            current.copy(pendingReboot = current.pendingReboot + fresh)
        }
        return added
    }
    suspend fun setLastConfirmedPackages(packages: List<String>) = update { it.copy(lastConfirmedPackages = packages) }
    suspend fun setRestoreSuspects(packages: List<String>) = update { it.copy(restoreSuspects = packages) }

    /**
     * Remembers that an uninstall batch is running, so a batch cut short by the app dying still
     * knows which apps it never reached. [packages] is the whole list; the finished ones are
     * dropped as their answers arrive.
     */
    suspend fun startPendingBatch(packages: List<String>, startedAt: Long) = update {
        it.copy(interruptedBatch = packages, interruptedBatchStartedAt = startedAt)
    }

    /** Narrows the running batch to the packages still waiting for an answer. */
    suspend fun setPendingBatch(packages: List<String>) = update { it.copy(interruptedBatch = packages) }

    /** The batch finished (or was abandoned): nothing is left to resume. */
    suspend fun clearPendingBatch() = update {
        it.copy(interruptedBatch = emptyList(), interruptedBatchStartedAt = 0)
    }
    suspend fun setIdentityToken(token: String) = update { it.copy(identityToken = token) }
    suspend fun setIdentityUserId(id: Long) = update { it.copy(identityUserId = id) }

    /**
     * 整份改写拉黑名单。
     *
     * 去空白再 distinct：名单里出现一个空串或重复项，界面按名字去比对时会莫名其妙地命中
     * 所有人。空列表就是"谁都没拉黑"，也就是出厂状态，不用删字段。
     */
    suspend fun setBlockedPeers(peers: List<String>) = update {
        it.copy(blockedPeers = peers.map { peer -> peer.trim() }.filter { peer -> peer.isNotEmpty() }.distinct())
    }

    /**
     * 拉黑或取消拉黑 [peer] 这一个人。
     *
     * 单条改动走这里而不是让调用方读出整份名单再写回去：拉黑是从列表里一行一行点的，
     * 两条几乎同时落下时，"读→改→写"会丢掉其中一条。
     */
    suspend fun setBlockedPeer(peer: String, blocked: Boolean) {
        val id = peer.trim()
        if (id.isEmpty()) return
        update { current ->
            val next = if (blocked) current.blockedPeers + id else current.blockedPeers - id
            current.copy(blockedPeers = next.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
        }
    }

    /** Adds [packages] to the restore suspects, keeping the list short and ordered. */
    suspend fun addRestoreSuspects(packages: List<String>) = update {
        it.copy(restoreSuspects = (it.restoreSuspects + packages).distinct().takeLast(MAX_RESTORE_SUSPECTS))
    }
}
