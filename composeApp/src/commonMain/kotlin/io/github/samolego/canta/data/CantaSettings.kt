package io.github.samolego.canta.data

import io.github.samolego.canta.data.bloat.DEFAULT_BLOAT_COMMITS_URL
import io.github.samolego.canta.data.bloat.DEFAULT_BLOAT_URL
import io.github.samolego.canta.data.proto.AppSettingsProto
import io.github.samolego.canta.data.proto.PendingUninstallProto
import io.github.samolego.canta.data.proto.ProtoBlobStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Most restore suspects kept in the settings. */
private const val MAX_RESTORE_SUSPECTS = 20

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
    /** Fresh installs follow the rolling dev build; an explicit stable choice is still stored and honoured. */
    val updateChannelFlow = field { it.updateChannel.ifBlank { "dev" } }
    /** Whether the records tab empties itself at the next system start; off by default. */
    val clearRecordsAfterBootFlow = field { it.clearRecordsAfterBoot }
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
    suspend fun setUpdateChannel(channel: String) = update {
        it.copy(updateChannel = if (channel == "dev") "dev" else "stable")
    }
    suspend fun setClearRecordsAfterBoot(clear: Boolean) = update { it.copy(clearRecordsAfterBoot = clear) }
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

    /** Adds [packages] to the restore suspects, keeping the list short and ordered. */
    suspend fun addRestoreSuspects(packages: List<String>) = update {
        it.copy(restoreSuspects = (it.restoreSuspects + packages).distinct().takeLast(MAX_RESTORE_SUSPECTS))
    }
}
