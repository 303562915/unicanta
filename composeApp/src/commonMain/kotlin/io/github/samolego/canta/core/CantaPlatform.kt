package io.github.samolego.canta.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * What the privileged installer is doing, so a screen can say more than "正在安装".
 *
 * `pm install` itself announces nothing while it runs, so the only honest progress is naming the
 * stage: a reader stuck on "正在安装" for a minute cannot tell a slow push from a dead Shizuku.
 */
enum class InstallStage {
    /** Reading the APK, checking it is this app's own package. */
    PREPARING,

    /** Copying the APK into the public downloads folder so the shell can read it. */
    STAGING,

    /** Pushing the APK into `/data/local/tmp` in base64 chunks — the one stage with a real percentage. */
    PUSHING,

    /** `pm install` is running over Shizuku. */
    INSTALLING,

    /** Every shell route was refused for an access reason, so the same APK is tried as root. */
    ROOT,
}

/** The [InstallStage] percentage that means "this stage has no measurable length". */
const val INSTALL_PROGRESS_UNKNOWN = -1

/**
 * 一次自更新安装的结果。
 *
 * [failure] 为 null 就是装上了，否则是一句人话的原因。真正要看的是 [retryable]：它说的是
 * 「这次失败怪环境，不怪这个包」——特权运行时没起来、shell 没答上来、Shizuku 的用户服务这次
 * 没绑上，这些等一会儿再试就可能好了。调用方据此分开处理：
 *
 *  - `retryable = true`：别把这个版本钉成「装不上」，也别把包甩给系统安装器；等一会儿自己再试。
 *  - `retryable = false`：`pm` 真的拒绝了这个包（签名不符、降级、包名不符…），重试只会得到同一句
 *    话，这时候交给系统安装器、并且不再自动重试这一版才是对的。
 *
 * 分开的理由是实测的形状：绝大多数失败都是「下载完了、Shizuku 那一刻没就绪」，而旧逻辑把这种
 * 也会当成「这个包装不上」，于是永久停掉自动安装，用户只能看到「下载了却不自动装」。
 */
data class InstallAttempt(
    val failure: String? = null,
    val retryable: Boolean = false,
) {
    /** 装上了。 */
    val succeeded: Boolean get() = failure == null

    companion object {
        /** The APK replaced this app. */
        val Success = InstallAttempt()

        /** `pm` refused this package; asking again answers the same. */
        fun refused(failure: String) = InstallAttempt(failure)

        /** Nothing about the package was wrong — the runtime simply was not ready. */
        fun notReady(failure: String) = InstallAttempt(failure, retryable = true)
    }
}

/**
 * Abstraction over small, UI-facing platform capabilities so the shared UI
 * can remain target-agnostic. Each platform provides its own implementation.
 */
interface CantaPlatform {
    /**
     * Show a transient short message. Android shows a Toast; platforms without
     * one emit it on [messages] instead, which the app shows as a snackbar.
     */
    fun showMessage(message: String)

    /** Messages to show as snackbars; empty where [showMessage] shows them itself. */
    val messages: Flow<String>
        get() = emptyFlow()

    /** Open a URL in the platform's default browser. */
    fun openUrl(url: String)

    /**
     * Brings another app to the front by package name, false when this platform cannot or the app
     * is not there. The privilege managers keep their service in their own app, so "start the
     * service" or "allow Canta" is something only they can do: this is the door to them.
     */
    fun openApp(packageName: String): Boolean = false

    /**
     * True when F-Droid put this build in place. Every store re-signs what it ships, so an APK from
     * the GitHub release can never replace an F-Droid install (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`,
     * or a silent no-op after the user confirms): the updater has to send people back to the store
     * instead of downloading and installing on its own.
     */
    fun isInstalledFromFdroid(): Boolean = false

    /**
     * Whether [isVpnActive] can answer at all. The reminder that asks for a proxy runs only where
     * this is true: a desktop or web build has no VPN transport to inspect, and the constant `false`
     * of the default implementation would nag about something that platform cannot do anything
     * about.
     */
    fun supportsVpnDetection(): Boolean = false

    /**
     * Whether a VPN currently carries this device's traffic. The community backend is Supabase in
     * Tokyo, so on a phone without a proxy every community read looks like "the server is offline"
     * even though the network is fine; the onboarding asks this to say so instead of letting people
     * believe the app is broken.
     */
    fun isVpnActive(): Boolean = false

    /**
     * Opens the place where the VPN is switched on: an installed proxy client when there is one
     * (its connect button is what actually matters), otherwise the system VPN screen. False where
     * the platform has neither.
     */
    fun openProxySettings(): Boolean = false

    /**
     * 打开系统的网络开关：Wi-Fi / 移动数据的那一块面板。
     *
     * 与 [openProxySettings] 分开，因为"连不上"有两种很不一样的解法：代理没开是前者，Wi-Fi
     * 自己不通（校园网要认证、酒店网要登录、家里路由器掉线）是后者。提示里给出两个入口，
     * 让人一眼选对，而不是把「换网络」也塞进 VPN 设置里。
     *
     * 默认 false：桌面、网页和测试没有这样一块面板，调用方据此自己收场。
     */
    fun openNetworkSettings(): Boolean = false

    /**
     * Open [appSchemes] in the app that claims them — the author's Coolapk page, a QQ group card —
     * and fall back to [web] when none of them is handled.
     *
     * The schemes are tried in order because the same app answers to more than one spelling of the
     * same deep link. Platforms without app deep links (the web build, the desktop build, tests)
     * just open [web]; that default is what keeps this an optional capability. An empty [web] means
     * there is no page worth opening, and the caller handles the "nothing opened" answer itself.
     *
     * Returns true when an app scheme was what got opened.
     */
    fun openLink(web: String, vararg appSchemes: String): Boolean {
        if (web.isNotEmpty()) openUrl(web)
        return false
    }

    /**
     * Request biometric/device-credential authentication before an action.
     * [title] and [subtitle] are shown by the system prompt; platforms
     * without biometrics should invoke [onSuccess] immediately.
     */
    fun requireBiometric(title: String, subtitle: String, onSuccess: () -> Unit)

    fun copyToClipboard(text: String)
    fun readClipboard(): String?

    /**
     * Writes [content] to a file the user can pull off the device without the app: on Android
     * `Download/Canta/[fileName]`, on the desktop the user's Downloads folder. Returns where the
     * file landed, or null where there is no such place (the web build).
     */
    fun exportLog(fileName: String, content: String): String? = null

    /**
     * 把一份文本当成文件交给系统的分享面板，让用户自己选去处——发到电脑（微信文件传输助手、
     * QQ、附近分享、邮件），或者存下来再用数据线拷走。
     *
     * 用在"这个手机马上要被救回来"这种文件上：恢复脚本要在电脑上双击运行，手机自己跑不了，
     * 所以不能只写进下载目录等用户去找。Android 写成 `Download/Canta/[fileName]` 再分享那个
     * URI；没有分享面板的平台（桌面、网页、测试）沿用默认实现回答 false，调用方改成提示保存路径。
     */
    fun shareFile(fileName: String, content: String, mimeType: String = "text/plain"): Boolean = false

    /**
     * 让用户从文件管理器里挑一个文本文件，返回它的内容（UTF-8）。取消、超时、打不开都回答 null。
     *
     * 用在「导入名单」这类回到设备上的文件：导出的 JSON 由用户自己保管、自己再挑回来。
     * 桌面端用 AWT 的文件对话框，网页与测试这些没有文件系统的目标沿用默认实现回答 null。
     */
    suspend fun pickTextFile(mimeTypes: List<String> = listOf("application/json", "text/plain")): String? = null

    /**
     * A stable, hashed identifier of this device, used only to hand a reinstall its old identity
     * back: the identity token itself is random and stays the secret, this is just the lookup key.
     *
     * It must survive uninstalling the app and must not be a raw hardware identifier. Android
     * answers with a hash of `Settings.Secure.ANDROID_ID` plus the build fingerprint (ANDROID_ID is
     * scoped to the app's signing key and survives reinstalls; IMEI and the serial number are
     * unreadable for a normal app on Android 10+). Empty where the platform has no such value, in
     * which case the app keeps working with its token alone.
     */
    fun deviceKey(): String = ""

    /**
     * Downloads [url] into a place the privileged helper can read, reporting the bytes that arrived
     * and the size the server announced.
     *
     * [onProgress] receives a [total] of 0 or less when the answer carries no length — a chunked
     * response, or a proxy that drops `Content-Length`. That case has to be shown as an
     * indeterminate spinner: computing a percentage against a size nobody announced is exactly how
     * a running download sits at 0% while it works.
     *
     * A platform downloader is expected to survive a transfer that dies: keep the bytes it already
     * has, continue them with a `Range` request on the next attempt, and retry a few times by
     * itself. The caller therefore treats one call as "get me this APK", not as "one HTTP request":
     * asking the reader to tap download again after a dropped proxy node is the failure this
     * contract exists to remove.
     *
     * Returns [UpdateDownload.Done] with the absolute path, or [UpdateDownload.Failed] with the
     * reason, so the caller can offer the download page instead of a dead end. The file has to
     * survive until [installUpdate] reads it, so it goes next to the app's own cache rather than
     * into a temporary directory that is cleaned up on the next start.
     */
    suspend fun downloadUpdate(
        url: String,
        fileName: String,
        onProgress: (received: Long, total: Long) -> Unit,
    ): UpdateDownload = UpdateDownload.Failed(UpdateDownloadFailure.UNSUPPORTED)

    /**
     * Installs the APK at [path] over this app through the privileged runtime, i.e. `pm install`
     * as Shizuku's identity. Answers [InstallAttempt.Success] when the APK replaced this app, or
     * the reason it did not — see [InstallAttempt] for why "没装成" is two different outcomes.
     *
     * [onStage] is called before every route the APK is offered to, with a 0–100 percentage while
     * the APK is being pushed (`/data/local/tmp`) and [INSTALL_PROGRESS_UNKNOWN] everywhere else:
     * `pm install` reports nothing while it runs, and a made-up number is worse than the stage name.
     *
     * Platforms without a privileged helper answer with an explanation instead of throwing: the
     * caller shows it and leaves the manual download link in place.
     */
    suspend fun installUpdate(
        path: String,
        onStage: (InstallStage, Int) -> Unit = { _, _ -> },
    ): InstallAttempt = InstallAttempt.refused("this build cannot install updates")

    /**
     * Hands the APK at [path] to the system package installer through a content:// URI the
     * installer is granted read access to — the path-agnostic way to install, used when the
     * privileged `pm install` cannot read any location the app can write (scoped storage hides
     * `Android/data` from the shell, and ROMs differ on what else they allow). The reader then
     * confirms the install in the system dialog. Returns true when an installer screen launched.
     */
    fun requestInstall(path: String): Boolean = false

    /** Closes the app, so the freshly installed version starts clean on the next launch. */
    fun exitApp() = Unit

    /**
     * 让系统栏（状态栏 / 导航栏）的底色跟着页面背景走。
     *
     * 正常情况下用不上：`enableEdgeToEdge` 之后系统栏是透明的，页面自己就画到那底下。但小米 /
     * 澎湃这类 ROM 在「经典导航键」（三键导航）下不让应用画到系统栏下面，那两条由系统自己填色，
     * 于是在浅色页面上看着是上下各一条黑框。系统既然不给铺，就让它的颜色等于页面底色，视觉上
     * 连成一片。桌面 / 网页 / 测试没有系统栏，沿用默认的空实现。
     *
     * @param backgroundArgb 页面背景色（ARGB，`MaterialTheme.colorScheme.background`）
     * @param lightBars 背景是浅色时为 true，此时系统栏图标要用深色
     */
    fun followSystemBarColor(backgroundArgb: Long, lightBars: Boolean) = Unit

    /**
     * 让用户挑一张图片，返回它的字节。
     *
     * 聊天里发图片走这条路：只有真的有相册的平台才需要实现它。桌面、网页、测试这些没有相册的
     * 目标沿用默认实现回答 null，调用方把 null 当成"没有图片"（用户取消也是这样），不报错。
     */
    suspend fun pickImage(): ByteArray? = null

    /**
     * 把一张图压到 [maxBytes] 以内；压不动就回答 null。平台没有解码能力时也回答 null。
     *
     * 聊天里发图要先过这一关：图床不限制大小，2.5MB 是我们自己的产品规则（见
     * `data/imagebed/ImageBed.MAX_BYTES`）。压不动的老实现（桌面、网页、测试）沿用默认回答
     * null，调用方据此说"这张图太大，换一张"——它们本来也没有相册，[pickImage] 先一步回了 null。
     *
     * **这是同步的、会占住线程几百毫秒**：解码一张两千万像素的照片就是这个代价。调用方必须
     * 在协程里、并且自己挪到 `Dispatchers.Default` 之类的后台上下文再调它，不要在组合里直接调。
     * 输出是 JPEG，原图的透明通道会变成黑底（聊天图是相册里的照片，这个代价换来的体积更划算）。
     */
    fun compressImage(bytes: ByteArray, maxBytes: Int): ByteArray? = null

    /**
     * 头像专用：按 [crop] 裁成正方形、缩到头像尺寸，再压到 [maxBytes] 以内；压不下去回答 null。
     *
     * 和 [compressImage] 分开是因为两边的目标不一样：聊天图要能放大看（长边留 2048、2.5MB 上限），
     * 头像最后只画一个 96dp 的圆，体积该落在 `20–200KB` 这一档（见
     * `data/imagebed/ImageBed.AVATAR_MAX_BYTES`）——太大是浪费，太小就糊了。
     *
     * [crop] 是归一化矩形（见 [ImageCrop]）；传 null 就是"整图居中裁方"，也就是以前的默认行为。
     *
     * **同步、会占住线程几十到几百毫秒**：调用方必须挪到 `Dispatchers.Default` 再调。
     * 没有解码能力的平台（桌面、网页、测试）沿用默认回答 null——它们也没有相册，[pickImage] 先一步回 null。
     */
    fun compressAvatar(bytes: ByteArray, crop: ImageCrop?, maxBytes: Int): ByteArray? = null

    /**
     * 有人给这个身份发了新消息，在系统通知栏提醒一次。
     *
     * [conversationId] 同时是"点开去哪一页"的钥匙和通知的 id：同一会话的新消息覆盖上一条，
     * 不同会话各占一格，所以一个人连发三条不会堆成三条通知。
     *
     * 这一条只负责"显示"，判断"该不该显示"是调用方的事（它才知道基线、知道用户有没有关开关）。
     * 没有通知栏的平台（桌面、网页、测试）沿用默认实现，什么也不做。
     */
    fun notifyChat(conversationId: Long, title: String, body: String, unread: Int) = Unit

    /**
     * 把通知栏上关于聊天的提醒全部收掉。回到前台时调用：人已经在看着 app，那几条没有意义了。
     */
    fun clearChatNotices() = Unit

    /**
     * 通知权限现在是不是拿到了。没有这一步的平台回答 false，调用方据此不去申请。
     *
     * Android 13 起这是一项运行时权限，不给就什么都弹不出来；更早的版本装上就有。
     */
    fun canNotify(): Boolean = false

    /**
     * 申请通知权限，回答最后给没给。
     *
     * 平台没有这一步（桌面、网页、Android 13 以下）时回答 false——不是"失败"，是"不需要"，
     * 调用方只看 [canNotify] 决定要不要真的弹。
     */
    suspend fun requestNotifyPermission(): Boolean = false

    /**
     * 这个平台有没有「申请通知权限」这一步，也就是页顶那条「当前无法接收朋友的消息提醒」该不该摆。
     *
     * 桌面/网页没有系统通知这一层，[canNotify] 永远是 false；这时候再摆一条"打开提醒"就是
     * 一条点不开的提示。Android 13 起这是运行时权限，回答 true。
     */
    val canRequestNotify: Boolean get() = false

    /**
     * 用户从通知栏点开的那条消息属于哪个会话；平台没有通知栏的一侧是空流。
     *
     * 这是一次性事件而不是状态：冷启动时由入口把 intent 里的会话号塞进来，界面收一次就导航过去。
     * 用流而不是属性，是因为"点了一条通知"这件事发生过一次就是一次，重复读它会把页面又推一遍。
     */
    val chatIntents: Flow<Long>
        get() = emptyFlow()

    /**
     * 有人点了群邀请链接（`unicanta://group/<code>`）而进到这个 app 时，链接里的邀请码。
     *
     * 和 [chatIntents] 同一套：一次性事件、冷启动那一条由入口先存着，平台没有 scheme 入口的一侧
     * 是空流。入群动作不在这里做——平台不知道身份，也不该知道后端。
     */
    val groupInviteCodes: Flow<String>
        get() = emptyFlow()
}
