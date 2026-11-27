package io.github.samolego.canta.data

import io.github.samolego.canta.data.bloat.DEFAULT_BLOAT_COMMITS_URL
import io.github.samolego.canta.data.bloat.DEFAULT_BLOAT_URL
import io.github.samolego.canta.data.proto.AppSettingsProto
import io.github.samolego.canta.data.proto.PendingUninstallProto
import io.github.samolego.canta.data.proto.ProtoBlobStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

/** 本机最多留几条收藏的聊天消息：再多也不会回头翻。 */
private const val MAX_CHAT_SAVED = 100

/** 收藏里那条正文最多存多少字：和聊天正文同一档，防的是被手改过的超大值。 */
private const val MAX_CHAT_SAVED_CHARS = 4000

/**
 * 一条被本机收藏的聊天消息。
 *
 * 只活在这台设备上（见 [AppSettingsProto.chatSaved]），所以字段按"重新打开时画得出来"来定：
 * [conversationId] 用来筛这个会话的，[id] 用来去重，其余几项就是那一行的全部内容。
 */
data class SavedChat(
    val conversationId: Long,
    val id: Long,
    val sender: String,
    val senderName: String,
    val body: String,
    val createdAt: String,
)

/** 解 [AppSettingsProto.chatSaved]；坏数据给空列表——一条收藏撑不起一次崩溃。 */
private fun decodeChatSaved(raw: String): List<SavedChat> =
    if (raw.isBlank()) {
        emptyList()
    } else {
        runCatching {
            Json.Default.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                val row = element.jsonObject
                val id = row["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val conversationId = row["c"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                SavedChat(
                    conversationId = conversationId,
                    id = id,
                    sender = row["s"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    senderName = row["n"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    body = row["b"]?.jsonPrimitive?.contentOrNull.orEmpty().take(MAX_CHAT_SAVED_CHARS),
                    createdAt = row["t"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

/** [decodeChatSaved] 的反向；一条都没有就存空串，和这个字段的出厂值一致。 */
private fun encodeChatSaved(saved: List<SavedChat>): String =
    if (saved.isEmpty()) {
        ""
    } else {
        buildJsonArray {
            saved.forEach { entry ->
                add(
                    buildJsonObject {
                        put("c", entry.conversationId)
                        put("id", entry.id)
                        put("s", entry.sender)
                        put("n", entry.senderName)
                        put("b", entry.body.take(MAX_CHAT_SAVED_CHARS))
                        put("t", entry.createdAt)
                    },
                )
            }
        }.toString()
    }

/**
 * 群公告的「已确认」记录：`会话号 -> 公告指纹`。
 *
 * 公告是"看一遍就够了"的东西：确认过还每次进群都挂一条横幅，等于每次都要再关一次。
 * 换一条新公告指纹就变了，横幅会重新出现一次。
 *
 * 坏数据给空表：一段记录撑不起一次崩溃（设置文件在用户自己的存储里，可能被手改过）。
 */
private fun decodeNoticeSeen(raw: String): Map<Long, String> =
    if (raw.isBlank()) {
        emptyMap()
    } else {
        runCatching {
            Json.Default.parseToJsonElement(raw).jsonObject.mapNotNull { (key, value) ->
                val id = key.toLongOrNull() ?: return@mapNotNull null
                id to value.jsonPrimitive.contentOrNull.orEmpty()
            }.toMap()
        }.getOrDefault(emptyMap())
    }

/** [decodeNoticeSeen] 的反向；一条都没有就存空串。 */
private fun encodeNoticeSeen(seen: Map<Long, String>): String =
    if (seen.isEmpty()) {
        ""
    } else {
        buildJsonObject {
            seen.forEach { (id, fingerprint) -> put(id.toString(), fingerprint) }
        }.toString()
    }

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
    /**
     * 上次和 GitHub 核对 bloat 列表的日期（`YYYY-MM-DD`，空串 = 没核对过）。
     * 应用列表拿它当"今天还问不问"的判据，见 [io.github.samolego.canta.data.bloat.BloatRepository.refresh]。
     */
    val bloatListCheckedDayFlow = field { it.bloatListCheckedDay }
    /**
     * 修正表（`site/bloat/level_fixes.json`，GitHub 上的静态文件，不是数据库），以及它是什么时候拿的。
     *
     * 与 [bloatListCheckedDayFlow] 分开记账：两者都是自然周一次。分开记是因为它们的来源和周期
     * 将来都可能各自变——修正表要能独立地改频率，而不牵动那份 1.6 MB 的名单。
     */
    val correctionsFetchedDayFlow = field { it.correctionsFetchedDay }
    val bloatListUrlFlow = field { it.bloatListUrl.ifEmpty { DEFAULT_BLOAT_URL } }
    val commitsUrlFlow = field { it.commitsUrl.ifEmpty { DEFAULT_BLOAT_COMMITS_URL } }
    val allowUnsafeUninstallsFlow = field { it.allowUnsafeUninstalls }
    val hideSuccessDialogFlow = field { it.hideSuccessDialog }
    val authEnabledFlow = field { it.authEnabled }
    val analyticsEnabledFlow = field { it.analyticsEnabled }
    val userNameFlow = field { it.userName }
    val showTweaksTabFlow = field { it.showTweaksTab }
    val showRecommendTabFlow = field { it.showRecommendTab }
    /** 底栏要不要给帖子页留一格；与另外两个可选页同一个开关形状。 */
    val showPostsTabFlow = field { it.showPostsTab }
    /** 底栏要不要给商城页留一格。 */
    val showStoreTabFlow = field { it.showStoreTab }
    /** Which arrangement the recommendations page was left in: false grid, true list. */
    val recommendationsListModeFlow = field { it.recommendationsListMode }
    /** Whether the safety page shows the picture of this phone; see [AppSettingsProto.showDevicePhoto]. */
    val showDevicePhotoFlow = field { it.showDevicePhoto }
    /** Downloads a found release on its own and offers the Shizuku install; see [AppSettingsProto.autoInstallUpdates]. */
    val autoInstallUpdatesFlow = field { it.autoInstallUpdates }
    /** Whether a downloaded release is installed at once instead of after a confirmation tap. */
    val confirmBeforeInstallFlow = field { it.confirmBeforeInstall }
    /**
     * The release whose automatic install already failed, so the start-up check does not download
     * and try it again on every launch; see [AppSettingsProto.skippedAutoInstallVersion].
     */
    val skippedAutoInstallVersionFlow = field { it.skippedAutoInstallVersion }
    /** The accent the app draws with, as ARGB; 0 is FlClash's own `#007AFF`; see [AppSettingsProto.themeAccent]. */
    val themeAccentFlow: Flow<Int> = field { it.themeAccent }
    /** Dark mode: 0 follows the system, 1 forces light, 2 forces dark; see [AppSettingsProto.themeMode]. */
    val themeModeFlow: Flow<Int> = field { it.themeMode }
    /**
     * 手动挑的界面语言，BCP-47 标签；空串 = 跟随系统。见 [AppSettingsProto.appLanguage]。
     *
     * 这一格同时决定「进来第一帧用哪种语言」：读的是同步的一份（见 [appLanguageNow]），
     * 否则会先按系统语言画一遍、设置读回来后再整棵树重建一次。
     */
    val appLanguageFlow: Flow<String> = field { it.appLanguage }

    /** 界面语言的存储值，**同步**读（空串 = 跟随系统）；理由同 [planScopeNow]。 */
    val appLanguageNow: String get() = store.state.value.appLanguage
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
     * 上次「选择机型」里挑的那台机器，空串 = 全部机型。
     *
     * 界面一侧自己解析成 `型号|版本`（见 `PlanScope`），这一层只管原样存取：它不需要知道
     * 那串东西怎么摆，换个存法也不必改设置代码。
     */
    val planScopeFlow: Flow<String> = field { it.planScope }

    /**
     * 「选择机型」当前的存储值，**同步**读（空串 = 全部机型）。
     *
     * 存在的理由：`planScopeFlow` 是派生出来的冷 `Flow`，没有 `value`；而界面需要"第一帧就知道选的是
     * 哪台"，不能先去订阅、等一帧。底层 `store.state` 是 `StateFlow`，而且构造时已从磁盘同步读完，
     * 所以这里读到的就是真值 —— 不是猜测，也不会先给个空串再跳。
     */
    val planScopeNow: String get() = store.state.value.planScope

    /**
     * 各会话没发出去的草稿，已经是解好的 `会话号 -> 正文`，最近写的在最后。
     *
     * 解在 flow 这一层而不是让调用方自己解析原始 JSON：只有这里的 [setChatDraft] 知道格式，
     * 界面不该跟着一起背。坏数据当空列表——一段草稿不值得让页面崩。
     */
    val chatDraftsFlow: Flow<List<Pair<Long, String>>> = field { decodeChatDrafts(it.chatDrafts) }
    /**
     * 本机收藏的聊天消息，收得最晚的在最后。
     *
     * 和草稿一样解在 flow 这一层：只有 [setChatSaved] 知道那段 JSON 的形状。坏数据当空列表。
     */
    val chatSavedFlow: Flow<List<SavedChat>> = field { decodeChatSaved(it.chatSaved) }
    /**
     * 已经确认过的群公告：`会话号 -> 公告指纹`。
     *
     * 聊天页拿它决定要不要在消息上面挂那条公告横幅（见 `ChatScreen` 的 `noticeSeen`）。
     */
    val noticeSeenFlow: Flow<Map<Long, String>> = field { decodeNoticeSeen(it.noticeSeen) }
    /** Fresh installs follow the rolling dev build; an explicit stable choice is still stored and honoured. */
    val updateChannelFlow = field { it.updateChannel.ifBlank { "dev" } }
    /** 关于页最下面那张「最近更新」是否展开；出厂折叠，见 [AppSettingsProto.updateHistoryExpanded]。 */
    val updateHistoryExpandedFlow: Flow<Boolean> = field { it.updateHistoryExpanded }
    /** 关于页「赞助者（贡献值）」是否展开；出厂折叠，与「最近更新」同一个做法。 */
    val sponsorsExpandedFlow: Flow<Boolean> = field { it.sponsorsExpanded }
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

    /**
     * 消息页上本人删掉（「不显示」）的那几行：官方群记群名，其余会话记会话号。
     *
     * 只影响这一页的列表，不动消息、不动成员：删是一个看得见的动作，不是退出群。见
     * [AppSettingsProto.hiddenMessageRows]。
     */
    val hiddenMessageRowsFlow: Flow<List<String>> = field { it.hiddenMessageRows }

    /** This install's identity token; empty until the plans page has created one. */
    val identityTokenFlow: Flow<String> = field { it.identityToken }

    /** The number that token owns on the backend; 0 until the first login. */
    val identityUserIdFlow: Flow<Long> = field { it.identityUserId }

    /**
     * 自己设的头像：图床直链，空串就是没设过（界面画首字母圆）；见 [AppSettingsProto.avatarUrl]。
     *
     * 只存这一个字符串：头像图本身在图床上，覆盖式上传时对象名不变，所以换头像之后这里的值也不用
     * 改——改的只是同一个地址背后的字节（界面那边的进程内缓存要跟着清，见 `ui/component/UserAvatar`）。
     */
    val avatarUrlFlow: Flow<String> = field { it.avatarUrl }

    suspend fun setAutoUpdateBloatList(autoUpdate: Boolean) = update { it.copy(autoUpdateBloatList = autoUpdate) }
    suspend fun setConfirmBeforeUninstall(needsConfirm: Boolean) = update { it.copy(confirmBeforeUninstall = needsConfirm) }
    suspend fun setLatestCommitHash(hash: String) = update { it.copy(latestBloatCommitHash = hash) }
    /** Records the local date (`YYYY-MM-DD`) the bloat list was last checked; see [AppSettingsProto.bloatListCheckedDay]. */
    suspend fun setBloatListCheckedDay(day: String) = update { it.copy(bloatListCheckedDay = day) }
    /** Records the local date (`YYYY-MM-DD`) the level corrections were last fetched; see [AppSettingsProto.correctionsFetchedDay]. */
    suspend fun setCorrectionsFetchedDay(day: String) = update { it.copy(correctionsFetchedDay = day) }
    suspend fun setBloatListUrl(url: String) = update { it.copy(bloatListUrl = url) }
    suspend fun setCommitsUrl(url: String) = update { it.copy(commitsUrl = url) }
    suspend fun setAllowUnsafeUninstalls(allow: Boolean) = update { it.copy(allowUnsafeUninstalls = allow) }
    suspend fun setHideSuccessDialog(hide: Boolean) = update { it.copy(hideSuccessDialog = hide) }
    suspend fun setAuthEnabled(enabled: Boolean) = update { it.copy(authEnabled = enabled) }
    suspend fun setAnalyticsEnabled(enabled: Boolean) = update { it.copy(analyticsEnabled = enabled) }
    suspend fun setUserName(name: String) = update { it.copy(userName = name) }
    suspend fun setShowTweaksTab(show: Boolean) = update { it.copy(showTweaksTab = show) }
    suspend fun setShowRecommendTab(show: Boolean) = update { it.copy(showRecommendTab = show) }
    suspend fun setShowPostsTab(show: Boolean) = update { it.copy(showPostsTab = show) }
    suspend fun setShowStoreTab(show: Boolean) = update { it.copy(showStoreTab = show) }
    suspend fun setRecommendationsListMode(list: Boolean) = update { it.copy(recommendationsListMode = list) }
    suspend fun setShowDevicePhoto(show: Boolean) = update { it.copy(showDevicePhoto = show) }
    suspend fun setAutoInstallUpdates(auto: Boolean) = update { it.copy(autoInstallUpdates = auto) }
    suspend fun setConfirmBeforeInstall(confirm: Boolean) = update { it.copy(confirmBeforeInstall = confirm) }
    /**
     * Remembers that the automatic install of [version] did not go through, so the same build is
     * not fetched and retried on every launch. Empty clears the mark: a manual install that
     * succeeds, or a new version, makes the stored one meaningless.
     */
    suspend fun setSkippedAutoInstallVersion(version: String) =
        update { it.copy(skippedAutoInstallVersion = version.trim()) }

    /**
     * 这一版自动安装已经失败过几次（跨进程）。
     *
     * 读法容忍任何垃圾内容：这段字符串只是本机的一份计数，读坏了就当没失败过，绝不能因为一个
     * 解析异常让更新卡片整张画不出来。见 [AppSettingsProto.installFailureCounts]。
     */
    suspend fun installFailureCount(version: String): Int {
        val stored = field { it.installFailureCounts }.first()
        return stored.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { row ->
                val at = row.indexOf('\r')
                if (at <= 0) null else row.take(at) to row.drop(at + 1).toIntOrNull()
            }
            .firstOrNull { (label, _) -> label == version }
            ?.second ?: 0
    }

    /** 把这一版失败次数加一。调用方不需要返回值：接下来该不该再试由 [installFailureCount] 决定。 */
    suspend fun bumpInstallFailureCount(version: String) = update { proto ->
        val rows = proto.installFailureCounts.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { row ->
                val at = row.indexOf('\r')
                if (at <= 0) null else row.take(at) to (row.drop(at + 1).toIntOrNull() ?: 0)
            }
            .toMutableList()
        val index = rows.indexOfFirst { (label, _) -> label == version }
        val next = if (index >= 0) rows[index].second + 1 else 1
        if (index >= 0) rows[index] = version to next else rows.add(version to next)
        // 只留最近 4 个版本：这台手机不会再回去装更早的版本，留下来的只有噪音。
        val kept = rows.takeLast(4).joinToString("\n") { (label, times) -> "$label\r$times" }
        proto.copy(installFailureCounts = kept)
    }

    /** 版本装成功就清掉它的失败计数（成功之后再留着只会在下一次误伤）。 */
    suspend fun clearInstallFailureCount(version: String) = update { proto ->
        val kept = proto.installFailureCounts.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { row -> row.substringBefore('\r') == version }
            .joinToString("\n")
        proto.copy(installFailureCounts = kept)
    }
    suspend fun setThemeAccent(argb: Int) = update { it.copy(themeAccent = argb) }
    suspend fun setThemeMode(mode: Int) = update { it.copy(themeMode = mode.coerceIn(0, 2)) }
    /**
     * 记下界面语言（BCP-47 标签）；空串 = 跟随系统。
     *
     * 不做白名单校验：清单在界面那一侧（`AppLanguageOptions`），设置层只管原样存。
     * 存进来一个这门资源没有的标签，效果是回到默认语言，不是崩溃。
     */
    suspend fun setAppLanguage(tag: String) = update { it.copy(appLanguage = tag.trim()) }
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
    /** 记下「选择机型」挑的是哪一台；空串 = 全部机型。 */
    suspend fun setPlanScope(scope: String) = update { it.copy(planScope = scope.trim()) }

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

    /**
     * 收藏 / 取消收藏一条聊天消息。[keep] 为 false 就是删掉这一条。
     *
     * 同一条消息重复收藏不会在列表里堆两行（先按消息号过滤再按需追加）；超过
     * [MAX_CHAT_SAVED] 从头部裁——收藏是"最近留着的那些"，先丢最旧的。
     */
    suspend fun setChatSaved(entry: SavedChat, keep: Boolean) {
        update { current ->
            val kept = decodeChatSaved(current.chatSaved).filterNot { it.id == entry.id }
            val next = if (keep) kept + entry else kept
            current.copy(chatSaved = encodeChatSaved(next.takeLast(MAX_CHAT_SAVED)))
        }
    }

    /**
     * 记下「这个群的这一条公告我看过了」。
     *
     * 只存指纹不存正文：判断只需"还是不是同一条"，正文存在聊天页那一层（会话详情）。
     */
    suspend fun setNoticeSeen(conversationId: Long, fingerprint: String) {
        if (conversationId <= 0L || fingerprint.isBlank()) return
        update { current ->
            val seen = decodeNoticeSeen(current.noticeSeen).toMutableMap()
            seen[conversationId] = fingerprint
            current.copy(noticeSeen = encodeNoticeSeen(seen))
        }
    }
    /** 记住关于页最下面那张「最近更新」是展开还是折叠。 */
    suspend fun setUpdateHistoryExpanded(expanded: Boolean) = update {
        it.copy(updateHistoryExpanded = expanded)
    }
    /** 记住关于页「赞助者（贡献值）」那一块是展开还是折叠。 */
    suspend fun setSponsorsExpanded(expanded: Boolean) = update {
        it.copy(sponsorsExpanded = expanded)
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

    /** 记下自己的头像直链；传空串就是清掉（界面回到首字母圆）。见 [AppSettingsProto.avatarUrl]。 */
    suspend fun setAvatarUrl(url: String) = update { it.copy(avatarUrl = url.trim()) }

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

    /**
     * 删掉 / 恢复消息页上的一行：官方群传群名，其余会话传会话号（[rowKey] 负责拼）。
     *
     * 单条写而不是"读出整份再写回"：两行几乎同时删掉时，读→改→写会丢掉其中一条。
     */
    suspend fun setMessageRowHidden(key: String, hidden: Boolean) {
        val id = key.trim()
        if (id.isEmpty()) return
        update { current ->
            val next = if (hidden) current.hiddenMessageRows + id else current.hiddenMessageRows - id
            current.copy(hiddenMessageRows = next.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
        }
    }
}
