package io.github.samolego.canta.core

import io.github.samolego.canta.data.AppBlobStorage
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.bloat.BloatDescriptions
import io.github.samolego.canta.data.bloat.BloatRepository
import io.github.samolego.canta.data.bloat.BundledBloatList
import io.github.samolego.canta.data.community.AppNoteStore
import io.github.samolego.canta.data.community.PlanCache
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
)

/** Names of the persisted blobs; each platform maps them to a file or storage key. */
object StoredData {
    const val SETTINGS = "app_settings.pb"
    const val PRESETS = "presets.pb"
    const val BLOAT_LIST = "bloat_list.json"

    /** Reviews and likes this install wrote: see [io.github.samolego.canta.data.community.AppNoteStore]. */
    const val APP_NOTES = "app_notes.json"

    /** The uninstall/restore log behind the records tab: see [OperationLogStore]. */
    const val OPERATION_LOG = "operation_log.json"

    /** The last plans lists the ranking/plans page loaded — this phone's and every model's: see [PlanCache]. */
    const val PLAN_CACHE = "plan_cache.json"
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
        ),
        telemetry = telemetry,
        packageTelemetry = PackageTelemetry(cantaHandler, telemetry, settings),
        notes = AppNoteStore(storage(StoredData.APP_NOTES)),
        planCache = PlanCache(storage(StoredData.PLAN_CACHE)),
        history = OperationLogStore(storage(StoredData.OPERATION_LOG), platform),
    )
}
