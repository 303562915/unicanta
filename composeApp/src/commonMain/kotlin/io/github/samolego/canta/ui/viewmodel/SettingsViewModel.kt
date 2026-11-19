package io.github.samolego.canta.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.community.PlanRepository
import io.github.samolego.canta.util.DeviceIdentity
import io.github.samolego.canta.util.PackageTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bump to show the onboarding again to everyone (existing users have 0). */
const val CURRENT_ONBOARDING_VERSION = 1

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

    /** The optional bar entries: off by default, turned on in the settings. */
    val showTweaksTab = settingsStore.showTweaksTabFlow.state(false)
    val showRecommendTab = settingsStore.showRecommendTabFlow.state(false)

    /** Null until the stored settings have loaded, so the UI doesn't flash the wrong screen. */
    val onboardingVersion: StateFlow<Int?> = settingsStore.onboardingVersionFlow.state<Int?>(null)

    init {
        viewModelScope.launch { syncName() }
    }

    /**
     * Pulls the name that belongs to this install's token and pushes the local one when the cloud
     * has nothing yet. A fresh install therefore shows the name it used before, and renaming
     * anywhere else on this token wins over the (still empty) local value.
     */
    private suspend fun syncName() {
        val repo = repository ?: return
        val token = DeviceIdentity.ensure(settingsStore)
        val remote = repo.profile(token) ?: return
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
