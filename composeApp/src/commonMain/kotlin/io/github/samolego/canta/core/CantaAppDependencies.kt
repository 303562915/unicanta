package io.github.samolego.canta.core

import io.github.samolego.canta.data.AppBlobStorage
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.app.AppListSnapshotStore
import io.github.samolego.canta.data.bloat.BloatDescriptions
import io.github.samolego.canta.data.bloat.BloatOverrides
import io.github.samolego.canta.data.bloat.BloatRepository
import io.github.samolego.canta.data.bloat.BundledBloatList
import io.github.samolego.canta.data.cache.ScreenCache
import io.github.samolego.canta.data.community.AppNoteStore
import io.github.samolego.canta.data.community.ChatMessageCache
import io.github.samolego.canta.data.community.ChatMessageStore
import io.github.samolego.canta.data.community.CommunityRpc
import io.github.samolego.canta.data.community.ConversationCache
import io.github.samolego.canta.data.community.ConversationStore
import io.github.samolego.canta.data.community.PlanCache
import io.github.samolego.canta.data.community.PostDraftStore
import io.github.samolego.canta.data.community.PostReadStore
import io.github.samolego.canta.data.history.OperationLogStore
import io.github.samolego.canta.data.preset.CantaPresetStore
import io.github.samolego.canta.util.PackageTelemetry
import io.github.samolego.canta.util.Telemetry
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * Bundle of the platform implementations handed to the UI.
 * Built once per platform at application start (see [cantaAppDependencies])
 * and passed down explicitly, keeping the UI free of global singletons.
 */
class CantaAppDependencies(
    val handler: CantaHandler,
    val settings: CantaSettings,
    val presetStore: CantaPresetStore,
    val platform: CantaPlatform,
    val bloatRepository: BloatRepository,
    val telemetry: Telemetry,
    /** Shared, so the UI and background checks use the same lock around the pending uninstalls. */
    val packageTelemetry: PackageTelemetry,
    /** Reviews, and the apps and plans this install liked. */
    val notes: AppNoteStore,
    /** The plans this phone saw last time, shown before the network answers. */
    val planCache: PlanCache,
    /** What was uninstalled and put back, and when; mirrored to a CSV in the download folder. */
    val history: OperationLogStore,
    /** 帖子草稿：写在本机、按身份分开，发出去成功才删（见 [PostDraftStore]）。 */
    val postDrafts: PostDraftStore,
    /** 帖子的已读痕迹与收藏：都写在本机，不进后端（见 [PostReadStore]）。 */
    val postReads: PostReadStore,
    /** 上次扫出来的包列表：进应用列表页的第一帧先把这张表画出来（见 [AppListSnapshotStore]）。 */
    val appListSnapshot: AppListSnapshotStore,
)

/** Names of the persisted blobs; each platform maps them to a file or storage key. */
object StoredData {
    const val SETTINGS = "app_settings.pb"
    const val PRESETS = "presets.pb"
    const val BLOAT_LIST = "bloat_list.json"

    /**
     * 库里那张家里的分级修正（`package_level_fix_list()` 的原文）。
     *
     * 单独一个文件而不是塞进 [BLOAT_LIST]：列表是上游的、会被整份覆盖，修正是我们自己的、要能
     * 在列表没换的情况下单独生效（见 [BloatRepository]）。
     */
    const val BLOAT_LEVEL_FIXES = "bloat_level_fixes.json"

    /** Reviews and likes this install wrote: see [io.github.samolego.canta.data.community.AppNoteStore]. */
    const val APP_NOTES = "app_notes.json"

    /** The uninstall/restore log behind the records tab: see [OperationLogStore]. */
    const val OPERATION_LOG = "operation_log.json"

    /** The last plans lists the ranking/plans page loaded — this phone's and every model's: see [PlanCache]. */
    const val PLAN_CACHE = "plan_cache.json"

    /** 消息页上一次画出来的那份会话列表：冷启动时先画它，不等网络（见 [ConversationStore]）。 */
    const val CONVERSATIONS = "conversations.json"

    /** 最近几个会话的最后几条消息：点进会话时先画它（见 [ChatMessageStore]）。 */
    const val CHAT_MESSAGES = "chat_messages.json"

    /** 帖子草稿：写在本机，发出去成功才删（见 [PostDraftStore]）。 */
    const val POST_DRAFTS = "post_drafts.json"

    /** 帖子的已读痕迹与收藏（见 [PostReadStore]）：本机的东西，换机不跟着走。 */
    const val POST_READS = "post_reads.json"

    /** 各页面快照落盘的那一份（见 [ScreenCache]）：冷启动时先画上次那一屏。 */
    const val SCREEN_CACHE = "screen_cache.json"

    /** 上次扫出来的包列表（见 [AppListSnapshotStore]）：冷启动先画它，不等整机扫描。 */
    const val APP_LIST = "app_list.json"
}

/**
 * Wires up the shared stores and services. Platforms only decide where data
 * lives ([storage], keyed by [StoredData] names) and provide their
 * [platform] and [handler].
 */
fun cantaAppDependencies(
    storage: (name: String) -> AppBlobStorage,
    platform: CantaPlatform,
    /** Reported with analytics: "android", "desktop" or "web". */
    platformName: String,
    handler: (CantaSettings) -> CantaHandler,
): CantaAppDependencies {
    val settings = CantaSettings(storage(StoredData.SETTINGS))
    val client = HttpClient()
    val cantaHandler = handler(settings)
    // 只用来问一件事：库里的分级修正表。跟列表核对同一个七天窗口，不是每次进列表都发请求。
    val communityRpc = CommunityRpc(client)
    // 消息页的会话列表落盘：冷启动那一屏要能先画出来，而不是转几秒等网络。
    // 这里绑一次就够，之后写入由 ConversationCache 自己派到后台。
    ConversationCache.bind(ConversationStore(storage(StoredData.CONVERSATIONS)))
    // 聊天页同理：最近几个会话的最后几条也落盘，点进去先有东西看。
    ChatMessageCache.bind(ChatMessageStore(storage(StoredData.CHAT_MESSAGES)))
    // 各页面快照也落一份：不接这一步，冷启动时它们还是空的，进来又要等网络。
    ScreenCache.bind(storage(StoredData.SCREEN_CACHE))
    val telemetry = Telemetry(
        client = client,
        platform = platformName,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        isEnabled = { settings.analyticsEnabledFlow.first() },
    )
    return CantaAppDependencies(
        handler = cantaHandler,
        settings = settings,
        presetStore = CantaPresetStore(storage(StoredData.PRESETS)),
        platform = platform,
        bloatRepository = BloatRepository(
            client,
            storage(StoredData.BLOAT_LIST),
            settings,
            descriptions = { BloatDescriptions.bundled() },
            bundled = { BundledBloatList.raw() },
            overrides = { BloatOverrides.bundled() },
            remoteCorrections = { communityRpc.post("package_level_fix_list", "{}") },
            correctionsCache = storage(StoredData.BLOAT_LEVEL_FIXES),
        ),
        telemetry = telemetry,
        packageTelemetry = PackageTelemetry(cantaHandler, telemetry, settings),
        notes = AppNoteStore(storage(StoredData.APP_NOTES)),
        planCache = PlanCache(storage(StoredData.PLAN_CACHE)),
        history = OperationLogStore(storage(StoredData.OPERATION_LOG), platform),
        postDrafts = PostDraftStore(storage(StoredData.POST_DRAFTS)),
        postReads = PostReadStore(storage(StoredData.POST_READS)),
        appListSnapshot = AppListSnapshotStore(storage(StoredData.APP_LIST)),
    )
}
