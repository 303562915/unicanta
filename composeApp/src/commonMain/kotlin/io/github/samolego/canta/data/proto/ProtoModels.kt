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
    /**
     * The accent the app draws with, as ARGB. 0 means "nothing picked yet", which is FlClash's
     * own `#007AFF`; the value is one of `FlAccent`'s colors. Local only, like everything else
     * about how this phone looks.
     */
    @ProtoNumber(31) val themeAccent: Int = 0,
    /**
     * Which dark mode the app draws in: 0 follows the system, 1 forces light, 2 forces dark.
     * 0 is both the proto3 zero value and the default behaviour, so an install that never picked
     * one keeps following the phone.
     */
    @ProtoNumber(32) val themeMode: Int = 0,
    /**
     * Whether the "no VPN" reminder is switched off for good. Off by default: the community data
     * lives in a Tokyo deployment, so a phone without a proxy silently shows cached or empty pages
     * that look like a broken app, and this reminder is the only thing that says why. "不再提示"
     * sets it, and it stays set — asking again after somebody said no is the nagging this exists
     * to avoid.
     */
    @ProtoNumber(33) val hideVpnNotice: Boolean = false,
    /**
     * 34 was a four-digit start-up lock during development and never shipped: the lock is a
     * question plus a date now ([launchQuestion] / [launchAnswer]). Kept so a build that stored
     * the old form still decodes, and never reused — the number stays spent.
     */
    @ProtoNumber(34) val launchRetiredPin: String = "",
    /**
     * 有人发消息来的时候，不要往系统通知栏弹。
     *
     * 存的是"静音"而不是"开启"，因为 proto3 会把零值省略、缺失的字段读回 Kotlin 默认值：默认值
     * 只能是 `false`，而这件事的出厂状态是"要提醒"。所以字段反过来命名——`false` 就是提醒开着，
     * 一个从没写过设置的老安装读出来正是这个意思，不必迁移。
     *
     * 它只管"要不要弹"，不管权限：权限没给的时候即使这里是 `false` 也弹不出来，
     * 那条申请在设置的同一格上（见 `CantaPlatform.requestNotifyPermission`）。
     */
    @ProtoNumber(35) val muteChatNotices: Boolean = false,
    /**
     * Which question the start-up lock asks, by `LaunchQuestion.id` (see `LAUNCH_QUESTIONS`).
     * Empty means no lock at all, which is the default: an app that opens straight into the list
     * is what everyone expects, and this is for the phone that gets handed to somebody who taps
     * first and reads later.
     *
     * The id, never the index: inserting a question in the middle of the list must not change
     * what an install that locked itself a year ago is being asked.
     */
    @ProtoNumber(36) val launchQuestion: String = "",
    /**
     * The date that answers [launchQuestion], written `YYYY-MM-DD` (for example `2000-11-01`).
     * Empty means no lock.
     *
     * Stored in plain text on purpose: this protects against a stray tap, not against somebody who
     * has the phone and its files, and the owner has to be able to read back what they set. Local
     * only, like every other setting.
     */
    @ProtoNumber(37) val launchAnswer: String = "",
    /**
     * 关于页「作者的群」那一行的小红点是不是点过了。
     *
     * 存的是"看过"而不是"没看过"：proto3 省略零值，默认值只能是 `false`，而这件事的出厂状态是
     * "有个点等着你去点"——所以字段反过来命名。点过群之后写 `true`，红点就再也不出现。
     */
    @ProtoNumber(38) val qqGroupSeen: Boolean = false,

    /**
     * 安全页顶部那个「本机机型」胶囊是不是点过。
     *
     * 胶囊在没被点过时会轻轻呼吸一下，提示"这里能换手机"；点过一次就写 `true`，之后不再闪。
     * 和 [qqGroupSeen] 同一个道理：存"点过"，因为出厂状态是"还在闪"。
     */
    @ProtoNumber(39) val scopeHintSeen: Boolean = false,
    /**
     * 每个会话没发出去的草稿，存成一段 JSON 数组文本：`[{"id":12,"t":"你好"}]`。
     *
     * 为什么不是一个 map 字段：protobuf 的 map 在 proto3 里不保序，而这里需要"最旧的先丢"——
     * 数组天然有序，写的时候追加到末尾、超过上限从头部裁。
     *
     * 为什么每敲一个字不直接落盘：见 `ChatScreen` 里那个 600ms 防抖，`CantaSettings` 只负责存。
     * 上限 20 个会话（约 40KB 封顶，单条正文截到 2000 字），再多也没有人会回到那么久的草稿。
     */
    @ProtoNumber(40) val chatDrafts: String = "",
    /**
     * 在应用列表里点一个包，是弹小窗（[AppSettingsProto] 的老样子）还是直接进它的详情页。
     *
     * 存的是"弹窗"而不是"进详情页"：proto3 省略零值，默认值只能是 `false`，而这件事的出厂状态
     * 是"直接开详情页"——所以字段反过来命名，`false` 就是出厂行为，老安装读出来正是这个意思，
     * 不用迁移。界面把它读成「点击应用打开详情页」那个开关。
     */
    @ProtoNumber(41) val appTapOpensDialog: Boolean = false,
    /**
     * 被本机拉黑的人，存的是对方身份号。
     *
     * 后端没有拉黑接口（也不想为它编一套 RPC 和迁移），所以拉黑只在这一台手机上生效：消息页
     * 不再列出这些人的私聊，聊天页把输入框关掉。空列表就是谁都没拉黑，也就是出厂状态。
     *
     * 存身份号而不是名字：人随时能改名，号码不变。
     */
    @ProtoNumber(42) val blockedPeers: List<String> = emptyList(),
    /**
     * The release whose automatic install already failed (or was handed to the system installer
     * and never confirmed), spelled the way the update check labels it (`4.3.0` or
     * `4.3.0 (dev 239)`).
     *
     * Without it the start-up check finds the same "newer" build on every launch — the version on
     * the device never changed, because the install did not go through — downloads the APK it
     * already has and shows "正在安装" again, forever, with no success and no failure to see. With
     * it, one automatic attempt per version: the next launches say "有更新，但自动装不上去" and
     * leave the manual install to the About page.
     */
    @ProtoNumber(43) val skippedAutoInstallVersion: String = "",
    /**
     * 自己设的头像：图床上的**直链**（`https://.../file/unicanta/头像/<owner>.jpg`），空 = 没设过
     * （这时界面画首字母圆，见 `ui/component/UserAvatar`）。
     *
     * 存整条网址而不是图床的 id：读头像的那一侧（Compose 里的 Ktor GET）只需要一个能直接下载的
     * 地址，公开免鉴权，多一层"id → 网址"的拼装对谁都没好处。地址里带着身份号，所以它自己就说明了
     * 这张图是谁的——换头像时对象名不变（覆盖式上传），这里的字符串也就不必跟着变。
     */
    @ProtoNumber(44) val avatarUrl: String = "",
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
