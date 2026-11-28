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
     * 底栏给帖子页留一格。与上面两个同类：出厂关着，设置里把那一格点亮才出现。
     *
     * 帖子是独立一页（`posts` 路由），不是推荐页的第四个分段——它自带编辑器与本地草稿，
     * 和"看东西"的三页不是一回事。要不要摆在底栏由用户自己定。
     */
    /**
     * 底栏给帖子页留一格。
     *
     * **出厂就是开着的**：帖子是这五个页面之一，不是"想起来了才打开"的附加页，所以新装/升级
     * 上来底栏就有它（应用、规则、帖子、消息、我的，五格）。不想要的人在设置里把那一格点灭即可。
     * 工具页与推荐页不一样，那两个是可选页，出厂关着。
     */
    @ProtoNumber(46) val showPostsTab: Boolean = true,
    /**
     * 底栏给商城页留一格（积分、积分商城、兑换中心）。
     *
     * 与帖子同样出厂开着：它是"钱怎么来怎么花"的独立系统，不该藏在机场榜单里。
     */
    @ProtoNumber(47) val showStoreTab: Boolean = true,
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
    /**
     * 上一次和 GitHub 核对 bloat 列表的本地日期，写成 `YYYY-MM-DD`；空串 = 从没核对过。
     *
     * 存日期而不是时间戳：要的是"每天最多问一次"，不是"隔 24 小时问一次"——上午开过、晚上再开
     * 不该再问；第二天第一次开才问。核对成功（无论有没有新东西）才写这一格，所以离线那次不算用掉
     * 当天的额度，下次启动还会再试。见 `BloatRepository.refresh()`。
     */
    @ProtoNumber(45) val bloatListCheckedDay: String = "",
    /**
     * 关于页最下面那张「最近更新」是不是展开着；出厂是折叠。
     *
     * 记在设置里而不是页面的 `remember`：它是"这个人选择怎么看这一页"，关掉应用再进来应该还是那个样子；
     * 折叠是默认，因为更新说明有一整屏，压在最下面的位置本来就不该一进来就占屏幕。
     *
     * **编号是 53，不是 46**：46 已经被上面的 [showPostsTab] 占了（两个字段同号会让它们在同一个
     * protobuf 槽上互相覆盖）。这一格以前写的就是 46，于是「帖子页那一格开着」被读成「最近更新
     * 展开着」——只影响首屏展开态，所以一直没被发现。
     */
    @ProtoNumber(53) val updateHistoryExpanded: Boolean = false,
    /**
     * 关于页「赞助者（贡献值）」那一块是不是展开着；出厂是折叠。
     *
     * 与 [updateHistoryExpanded] 同一个做法：折叠是默认，展开与否是这个人的选择，记在设置里。
     */
    @ProtoNumber(52) val sponsorsExpanded: Boolean = false,
    /**
     * 上一次拉「分级修正表」的本地日期（`YYYY-MM-DD`）；空串 = 从没拉过。
     *
     * 单独一格、不与 [bloatListCheckedDay] 共用：两者现在都是自然周一次，但来源不同、将来频率也可能
     * 各自变。见 `BloatRepository.refreshCorrections()`。
     */
    @ProtoNumber(48) val correctionsFetchedDay: String = "",
    /**
     * 上次在「选择机型」里挑的那台机器（`型号|版本` 或 `型号|`），留空就是「全部机型」这个默认范围。
     *
     * 为什么必须落盘：手机型号那一格是"这台手机"的属性，用户挑别的机型看方案，是这个**人**的选择。
     * 以前它只是页面里的 `remember`，离开安全规则页（去应用详情再返回）就重置成全部机型，看起来像
     * 「记不住本机机型」。存下来之后，返回还是那一台，点「全部机型」才会清空。
     */
    @ProtoNumber(49) val planScope: String = "",
    /**
     * 消息页上被本人删掉（不显示）的那些行：官方群用群名，其余会话用会话号（十进制）。
     *
     * 为什么官方群也要落盘：那两个群是**注册时就自动加入**的，后端每趟刷新都会把会话行带回来，
     * 只在前端过滤等于"藏一帧"，断网重进又冒出来。名单在本机，就是本人的选择：
     * 删了就一直不显示，直到他长按同一处恢复。
     *
     * 官方群用名字而不是会话号：群号在没有网络、没有刷新过的时候是拿不到的，而那一行是由
     * `OfficialGroups` 的常量画出来的——它本来就只有名字。
     */
    @ProtoNumber(50) val hiddenMessageRows: List<String> = emptyList(),
    /**
     * 自动安装失败过几次，按版本记：`版本标签\r次数`，多个版本用 `\n` 分隔；空串 = 一次都没失败过。
     *
     * 为什么必须落盘：`UpdateCenter` 里那个重试计数器是**进程内**的，App 一退出就归零。而
     * 「装不上」这条路最后一步是把包交给系统安装器并让 Canta 退出——用户再打开 Canta，计数器
     * 又是满的，于是再重试 4 次、再失败、再退出。线上实测同一台机器同一版本下载 41 次、安装
     * 失败 19 次，屏幕上就是「一直在下、一直退出、以为装好了、打开还是旧的」。
     *
     * 存在这里之后，一次启动最多重试一轮，第二轮的额度不会再被刷新；次数用尽就永久停在
     * 「包在本地，去点安装」，直到版本号真的变了。
     */
    @ProtoNumber(51) val installFailureCounts: String = "",
    /**
     * 手动挑的界面语言，BCP-47 标签（`zh-CN`、`en`、`ja-JP`…）；空串 = 跟随系统。
     *
     * 为什么要能自己挑：Compose Resources 取的是手机报的语言，而不少 ROM 的语言列表只有
     * 「简体中文 / English」，系统语言被识别成英文的机器上界面就整页英文。存的是语言标签而不是
     * 序号：清单以后会增减，序号一挪，已经选过的人就被换到别的语言上。
     */
    @ProtoNumber(54) val appLanguage: String = "",
    /**
     * 本机收藏的聊天消息，`[{c,id,s,n,b,t}]` 一段 JSON；空串 = 一条都没收。
     *
     * 只在这台设备上：群里的「收藏」是本人挑出来留着看的几条，后端没有这一列，和帖子收藏
     * （[io.github.samolego.canta.data.community.PostReadStore]）同一个性质。
     *
     * 存的是**收藏那一刻**的正文：原消息后来被撤回、或者被删掉，收藏里那句话仍然在——收藏要的是
     * "我当时留着的那句话"，不是"现在还剩什么"。
     */
    @ProtoNumber(55) val chatSaved: String = "",
    /**
     * 已经确认过的群公告：`{会话号: 公告指纹}` 的一段 JSON；空串 = 一条都没确认过。
     *
     * 为什么需要它：群公告是"看一遍就够了"的东西。确认过还每次进群都在消息上面挂一条横幅，
     * 等于每次都要再关一次；换一条新公告指纹就变，横幅才重新出现。
     */
    @ProtoNumber(56) val noticeSeen: String = "",
    /**
     * 记录页里点一行是打开那个包的详情页（`true`），还是直接做反向操作（`false`，出厂）。
     *
     * 出厂的 `false` 就是这一页原本的行为：点一条卸载记录就把它装回来。开了之后点一下先进详情页，
     * 那里能看这个包现在什么状态、再决定要不要动它——批量删完回头看记录的人要的是这个。
     * 为什么没有反过来命名：proto3 省略零值，默认值只能是 `false`，而这里的出厂状态正是"直接操作"。
     */
    @ProtoNumber(57) val recordTapOpensDetail: Boolean = false,
    /**
     * 安全页的包搜索要不要也认应用介绍里的字（`true`），还是只搜包名（`false`，出厂）。
     *
     * 出厂 `false` 是搜索栏原本的行为：按包名/标签过滤。开了之后介绍正文里的词也算命中——
     * "找那些介绍里提到广告的包"是这一页最常见的用法，但把整段介绍塞进每一次搜索是纯浪费，
     * 所以做成开关，默认不搜。
     */
    @ProtoNumber(58) val searchDescriptions: Boolean = false,
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
