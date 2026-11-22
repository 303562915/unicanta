@file:OptIn(ExperimentalSerializationApi::class)

package io.github.samolego.canta.data.proto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/*
 * Protobuf schema for persisted settings and presets, encoded with
 * kotlinx-serialization-protobuf. These mirror the former `settings.proto`
 * and `presets.proto` (proto3) and must stay byte-compatible with the
 * `app_settings.pb` / `presets.pb` files written by earlier Android releases:
 * never renumber or reuse a field number.
 *
 * All defaults are proto3 zero values on purpose: proto3 omits zero-valued
 * fields when encoding, so any other default would change the meaning of
 * a stored `false` / `""`.
 */

@Serializable
data class AppSettingsProto(
    @ProtoNumber(1) val autoUpdateBloatList: Boolean = false,
    @ProtoNumber(2) val confirmBeforeUninstall: Boolean = false,
    // 3 (disableRiskDialog) is unused now; kept so old files still decode.
    @ProtoNumber(3) val disableRiskDialog: Boolean = false,
    @ProtoNumber(4) val latestBloatCommitHash: String = "",
    @ProtoNumber(5) val bloatListUrl: String = "",
    @ProtoNumber(6) val commitsUrl: String = "",
    @ProtoNumber(7) val allowUnsafeUninstalls: Boolean = false,
    @ProtoNumber(8) val hideSuccessDialog: Boolean = false,
    @ProtoNumber(9) val authEnabled: Boolean = false,
    // 10 (disable_adb_helper) was used by development builds: never reuse it.
    @ProtoNumber(11) val analyticsEnabled: Boolean = false,
    @ProtoNumber(12) val onboardingVersion: Int = 0,
    @ProtoNumber(13) val pendingReboot: List<PendingUninstallProto> = emptyList(),
    /**
     * Packages of the last batch the user confirmed in the uninstall dialog; the next dialog
     * highlights whatever is after them, so a list pasted from somewhere else stands out.
     */
    @ProtoNumber(14) val lastConfirmedPackages: List<String> = emptyList(),
    /**
     * Packages that came back although the build had not changed, i.e. were most likely put back
     * by hand to rescue the device. Local only, never part of any request; shown once at start.
     */
    @ProtoNumber(15) val restoreSuspects: List<String> = emptyList(),
    /**
     * Random identity of this install (see `DeviceIdentity`), created on first use and kept
     * forever: it owns the uploaded plans, casts the vote and signs the comments, so the plans
     * page needs no account. Never leaves the device except inside those requests.
     */
    @ProtoNumber(16) val identityToken: String = "",
    /**
     * The number the backend gave this install on first login (10000, 10001, ...). Kept so the
     * app knows who it is without asking again; 0 means "not signed in yet".
     */
    @ProtoNumber(17) val identityUserId: Long = 0,
    /**
     * The name the settings page greets the user with. Local only: it never leaves the device,
     * and the avatar next to it is just its first character.
     */
    @ProtoNumber(18) val userName: String = "",
    /**
     * Whether the bar keeps an entry for the ROM tweaks page. Both optional pages are off by
     * default, so a fresh install shows the three pages everyone needs.
     */
    @ProtoNumber(19) val showTweaksTab: Boolean = false,
    /** Whether the bar keeps an entry for the recommended-apps page; see [showTweaksTab]. */
    @ProtoNumber(20) val showRecommendTab: Boolean = false,
    /**
     * When on, a new release is downloaded as soon as the update check finds one, and the app
     * installs it through Shizuku right after the download. The extra confirmation is the
     * [confirmBeforeInstall] switch, off by default.
     *
     * On by default: a check that finds something new and then waits for a second tap is a check
     * most people never finish.
     */
    @ProtoNumber(21) val autoInstallUpdates: Boolean = true,
    /**
     * Whether the safety page names this phone and opens on its photograph. Off by default: the
     * model a phone reports is not something every user wants on screen, and the page reads the
     * same without it — the band, the counters and the page switch stay exactly where they were.
     */
    @ProtoNumber(22) val showDevicePhoto: Boolean = false,
    /** Update channel: empty defaults to the dev stream; "stable" keeps an explicit stable choice. */
    @ProtoNumber(23) val updateChannel: String = "",
    /**
     * The uninstall batch that was running when the app last stopped, if it was interrupted: the
     * packages that never got an answer. The next start offers to finish the list instead of
     * making the user find the same forty apps again. Empty when no batch was interrupted.
     */
    @ProtoNumber(24) val interruptedBatch: List<String> = emptyList(),
    /** When that batch started, epoch milliseconds; 0 when nothing is pending. */
    @ProtoNumber(25) val interruptedBatchStartedAt: Long = 0,
    /**
     * Which of the two arrangements the recommendations page was left in: false is the icon grid
     * it opens on, true is one card per app. Stored rather than remembered per visit, so leaving
     * the page and coming back shows the arrangement the reader chose — the page used to forget it
     * as soon as it left the composition.
     */
    @ProtoNumber(26) val recommendationsListMode: Boolean = false,
    /**
     * Whether the record of uninstalls and restores is dropped at the next system start. Off by
     * default: a log that empties itself is a surprise unless someone asked for it, and the log is
     * the only way back to what was removed for anyone who did not write it down.
     */
    @ProtoNumber(27) val clearRecordsAfterBoot: Boolean = false,
    /**
     * The boot this install last ran on (see `CantaHandler.bootId`), so "the phone was restarted"
     * can be told apart from "the app was opened again". Empty until a start reports one, and left
     * empty on platforms that cannot report a boot id.
     */
    @ProtoNumber(28) val recordsBootId: String = "",
    /**
     * The vote queue as it stood when this install last looked at it: one `id:agrees:disagrees`
     * triple per suggestion, in id order. Compared against the queue the backend answers now, it
     * is what lights the red dot on the recommendations tab — "somebody voted since you looked"
     * rather than "there are votes", which would never go out. Empty until the queue was seen.
     */
    @ProtoNumber(29) val votesSeen: String = "",
    /**
     * Whether a downloaded release waits for a tap before it replaces the running app. Off by
     * default: the download only happened because automatic updates are on, so the install is
     * what was asked for, and the install itself is slow enough that the "正在安装" line is read
     * before anything closes. Turned on, the confirmation dialog comes back.
     */
    @ProtoNumber(30) val confirmBeforeInstall: Boolean = false,
)

/**
 * A system-package uninstall awaiting its "did the phone reboot fine" report.
 * [cohortWeek] is the ISO date of that week's Monday; [batchBucket] is
 * "1", "2-10" or "11+".
 */
@Serializable
data class PendingUninstallProto(
    @ProtoNumber(1) val pkg: String = "",
    @ProtoNumber(2) val bootId: String = "",
    @ProtoNumber(3) val cohortWeek: String = "",
    @ProtoNumber(4) val batchBucket: String = "",
    @ProtoNumber(5) val uninstalledAt: Long = 0,
    /** Device context at uninstall time; blank in entries from before it was recorded. */
    @ProtoNumber(6) val brand: String = "",
    @ProtoNumber(7) val rom: String = "",
    @ProtoNumber(8) val romVersion: Int = 0,
    @ProtoNumber(9) val sdk: Int = 0,
    /** Whether `survived_reboot` was already reported; the entry then waits for a possible reinstall. */
    @ProtoNumber(10) val survived: Boolean = false,
    /** `Build.FINGERPRINT` at uninstall time; stored locally only, never sent. Blank if unknown. */
    @ProtoNumber(11) val buildFingerprint: String = "",
    /** Which device the uninstall happened on (see `CantaHandler.deviceKey`); stored locally only, never sent. Blank if unknown. */
    @ProtoNumber(12) val deviceKey: String = "",
    /** True when first observed by the safety page after predating Canta's uninstall tracking. */
    @ProtoNumber(13) val baseline: Boolean = false,
)

/**
 * Whether the pending packages were uninstalled right when the device finished booting; stored
 * locally by the boot receiver until the post-boot check delivers its reports. Never sent.
 */
@Serializable
data class BootSnapshotProto(
    @ProtoNumber(1) val bootId: String = "",
    @ProtoNumber(2) val buildFingerprint: String = "",
    @ProtoNumber(3) val uninstalled: List<String> = emptyList(),
    @ProtoNumber(4) val installed: List<String> = emptyList(),
)

@Serializable
data class CantaPresetProto(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val description: String = "",
    @ProtoNumber(3) val createdDate: Long = 0,
    @ProtoNumber(4) val apps: List<String> = emptyList(),
    @ProtoNumber(5) val version: String = "",
    @ProtoNumber(6) val uuid: String = "",
)

@Serializable
data class PresetsListProto(
    @ProtoNumber(1) val presets: List<CantaPresetProto> = emptyList(),
)
