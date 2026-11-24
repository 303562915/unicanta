package io.github.samolego.canta.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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
     * as Shizuku's identity. Returns null on success, or the reason it failed.
     *
     * Platforms without a privileged helper answer with an explanation instead of throwing: the
     * caller shows it and leaves the manual download link in place.
     */
    suspend fun installUpdate(path: String): String? = "this build cannot install updates"

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
     * 让用户挑一张图片，返回它的字节。
     *
     * 聊天里发图片走这条路：只有真的有相册的平台才需要实现它。桌面、网页、测试这些没有相册的
     * 目标沿用默认实现回答 null，调用方把 null 当成"没有图片"（用户取消也是这样），不报错。
     */
    suspend fun pickImage(): ByteArray? = null

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
