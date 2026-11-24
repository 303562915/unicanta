package io.github.samolego.canta.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import io.github.samolego.canta.core.shizuku.ShizukuShell
import io.github.samolego.canta.util.LogUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.system.exitProcess

class AndroidCantaPlatform(
    private val context: Context,
) : CantaPlatform {

    companion object {
        private const val TAG = "AndroidCantaPlatform"

        /** How long a silent socket may stay open before the transfer is called stalled. */
        private const val STALL_TIMEOUT_MILLIS = 45_000L

        /** Reaching GitHub at all: DNS plus TLS plus one round trip, with room for a slow node. */
        private const val CONNECT_TIMEOUT_MILLIS = 20_000L

        /**
         * Ceiling for one resumed segment, not for the whole APK: a transfer that is cut is
         * continued by the next attempt, so slow is fine and never finishing is not.
         */
        private const val DOWNLOAD_TIMEOUT_MILLIS = 10 * 60 * 1000L

        /** Bytes per read, and the unit progress moves in when the server announces no size. */
        private const val DOWNLOAD_CHUNK_BYTES = 64 * 1024

        /** Where a file handed to the share sheet is staged when the Download folder is out of reach. */
        private const val SHARE_DIRECTORY = "share"

        /**
         * How many times one APK is attempted before the page is told it failed. Every attempt
         * after the first resumes from the bytes already on disk, so this is not "download it six
         * times": a flaky proxy node costs a few seconds, not the whole file.
         */
        private const val DOWNLOAD_ATTEMPTS = 6

        /** Back-off between attempts: 1.5 s, 3 s, 6 s, 12 s, 20 s. */
        private const val RETRY_BASE_DELAY_MILLIS = 1_500L
        private const val RETRY_MAX_DELAY_MILLIS = 20_000L

        /**
         * The two answer codes the resume depends on. They are compared as numbers, not through
         * `HttpStatusCode` constants: over HTTP/2 the engine's value carries an empty reason
         * phrase and never equals the constant, so the transfer would quietly start over.
         */
        private const val HTTP_PARTIAL_CONTENT = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416

        /**
         * The installers whose builds cannot be replaced by this repo's APK: F-Droid and the
         * clients that talk to it, all of which re-sign what they install. The plain package name
         * of F-Droid itself is what most devices report; the rest are the clients people actually
         * use to browse the same index.
         */
        private val FDROID_INSTALLERS = setOf(
            "org.fdroid.fdroid",
            "com.looker.droidify",
            "com.machiav3lli.fdroid",
        )
    }

    @Volatile
    private var biometricActivity: FragmentActivity? = null

    /** Runs `pm install` as Shizuku; created on first update, not for every page. */
    private val shell by lazy { ShizukuShell(context) }

    /**
     * One update download at a time. The start-up check and the about page ask for the same APK,
     * and both write the same partial file: without this, two writers would interleave into a
     * file neither of them can finish. The second caller waits and then finds the finished APK.
     */
    private val updateDownload = Mutex()

    /**
     * One install at a time. The start-up path and the about page can both decide to install the
     * same APK (both fetched it, both found it newer), and two `pm install` runs of one package
     * make one of them fail for no reason the reader can act on — the second is serialized here
     * and then finds the package already replaced.
     */
    private val updateInstall = Mutex()

    /** 相册挑选器：在 [attachActivity] 里注册，注册之后才能 launch。 */
    private var imagePicker: ActivityResultLauncher<String>? = null

    /** 正在等哪一次挑选的结果；同一时刻只会有一个。 */
    private var pendingImage: CompletableDeferred<ByteArray?>? = null

    /**
     * 通知栏那一条。第一次真正要弹（或被问"能不能弹"）的时候才建：一个从不看消息的启动不该
     * 顺手去碰系统通知服务。
     */
    private val chatNotifier by lazy { ChatNotifier(context) }

    /** 通知权限的申请器；和相册挑选器一样，只能在 [attachActivity] 里注册。 */
    private var notifyPermissionLauncher: ActivityResultLauncher<String>? = null

    /** 正在等哪一次权限申请的结果；同一时刻只会有一个。 */
    private var pendingNotifyPermission: CompletableDeferred<Boolean>? = null

    /**
     * 从通知栏点进来的会话号。
     *
     * 用 `SharedFlow` 而不是 `StateFlow`：这是一次性事件，"点了一条通知"发生过一次就该让页面
     * 走一次，状态式的值会让界面每重组一次又把那一页推一遍。
     */
    private val chatIntentFlow = MutableSharedFlow<Long>(extraBufferCapacity = 8)

    /**
     * 还没有订阅者时要补发的那一个会话号。
     *
     * 冷启动的顺序是 `attachActivity` → 交 intent → 才轮到 Compose 开始收集，所以第一条必然是
     * 在没人听的时候到的。`SharedFlow` 在没有订阅者时会把值丢掉（replay 是 0），于是这一格就是
     * 那份"先存着"：第一个收集者上来时先把它交出去，再转去听后面的。
     */
    @Volatile
    private var pendingChatIntent: Long = 0L

    /**
     * 点群邀请链接进来的邀请码。
     *
     * 和会话号那一格同样的理由：冷启动时 intent 比第一个收集者先到，`SharedFlow` 没有 replay
     * 就会把它丢掉，所以没人听的时候先存在这里，第一个收集者上来时补发一次。
     */
    @Volatile
    private var pendingInviteCode: String = ""

    private val inviteCodeFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Attach the activity (from `onCreate`); used for biometric prompts and the photo picker. */
    fun attachActivity(activity: FragmentActivity) {
        biometricActivity = activity
        // 必须在这里注册（Activity 还没进入 STARTED），之后再注册会抛 IllegalStateException。
        imagePicker = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val waiting = pendingImage
            pendingImage = null
            if (uri == null) {
                waiting?.complete(null)
            } else {
                val bytes = runCatching {
                    activity.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
                }.getOrNull()
                waiting?.complete(bytes)
            }
        }
        // 通知权限同理：`registerForActivityResult` 必须在 Activity 进入 STARTED 之前登记。
        notifyPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            val waiting = pendingNotifyPermission
            pendingNotifyPermission = null
            waiting?.complete(granted)
        }
    }

    /** Detach the activity when it is destroyed. */
    fun detachActivity(activity: FragmentActivity) {
        if (biometricActivity === activity) {
            biometricActivity = null
        }
        // 活动没了，挂着的挑选器也就没用了：再点图片会重新进 attachActivity 注册。
        imagePicker = null
        pendingImage = null
        notifyPermissionLauncher = null
        pendingNotifyPermission = null
    }

    /**
     * 让用户从相册挑一张图：拉起系统的图片选择器，等结果回来再继续。
     *
     * 用户取消、超时（三分钟）或者当前没有活动可以挂选择器，都回答 null——调用方把 null 当成
     * "没有图片"，不弹错误：取消不是失败。
     */
    override suspend fun pickImage(): ByteArray? {
        val launcher = imagePicker ?: return null
        val waiting = CompletableDeferred<ByteArray?>()
        pendingImage = waiting
        withContext(Dispatchers.Main) { launcher.launch("image/*") }
        val bytes = withTimeoutOrNull(180_000L) { waiting.await() }
        if (pendingImage === waiting) {
            pendingImage = null
        }
        return bytes
    }

    // ------------------------------------------------------------------ 通知

    /**
     * 入口（`MainActivity`）把拿到的 intent 交进来，解析出"从哪条通知点进来的"。
     *
     * 不是从通知来的 intent 直接返回：正常点图标启动时 extra 是空的，这里什么也不会发生。
     * 解析完就把 extra 摘掉——同一个 intent 可能被再交一次（配置变化重建 Activity），摘掉之后
     * 第二次不会再导航一遍。
     */
    fun takeChatIntent(intent: Intent?) {
        val conversation = chatNotifier.conversationOf(intent)
        if (conversation <= 0L) return
        intent?.removeExtra(ChatNotifier.EXTRA_CONVERSATION_ID)
        // 没人听的时候先存着（见 [pendingChatIntent]）；有人在听就直接投进去。
        if (chatIntentFlow.subscriptionCount.value == 0) {
            pendingChatIntent = conversation
        } else {
            chatIntentFlow.tryEmit(conversation)
        }
    }

    override val chatIntents: Flow<Long> = flow {
        val waiting = pendingChatIntent
        if (waiting > 0L) {
            pendingChatIntent = 0L
            emit(waiting)
        }
        emitAll(chatIntentFlow)
    }

    /**
     * 入口（`MainActivity`）把拿到的 intent 交进来，解析出「点的是哪一条群邀请链接」。
     *
     * 只认 `unicanta://group/<code>`：scheme 和 host 都对上才取最后一段当邀请码，别的 URL、
     * 普通点图标启动都当没发生。解析完把 data 换成空的，同一个 intent 被再交一次（配置变化
     * 重建 Activity、或者 `singleTop` 复用）不会重复进群。
     */
    fun takeInviteIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme ?: return
        if (!scheme.equals("unicanta", ignoreCase = true)) return
        val host = data.host ?: return
        if (!host.equals("group", ignoreCase = true)) return
        val code = data.lastPathSegment?.trim().orEmpty()
        if (code.isEmpty()) return
        intent.data = Uri.EMPTY
        if (inviteCodeFlow.subscriptionCount.value == 0) {
            pendingInviteCode = code
        } else {
            inviteCodeFlow.tryEmit(code)
        }
    }

    override val groupInviteCodes: Flow<String> = flow {
        val waiting = pendingInviteCode
        if (waiting.isNotEmpty()) {
            pendingInviteCode = ""
            emit(waiting)
        }
        emitAll(inviteCodeFlow)
    }

    override fun canNotify(): Boolean = chatNotifier.canPost()

    /**
     * 申请通知权限，回答最后给没给。
     *
     * 没有活动可以挂申请器、或者这台机器根本不需要这一步（Android 13 以下）时，直接回答
     * [canNotify]——调用方拿它当"现在能不能弹"，不当成"用户拒绝了一次"。
     */
    override suspend fun requestNotifyPermission(): Boolean {
        if (canNotify()) return true
        val launcher = notifyPermissionLauncher ?: return false
        val waiting = CompletableDeferred<Boolean>()
        pendingNotifyPermission = waiting
        withContext(Dispatchers.Main) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // 和相册一样的耐心上限：人可能把系统对话框放着不管，界面不能永远挂在那里。
        val granted = withTimeoutOrNull(180_000L) { waiting.await() } ?: false
        if (pendingNotifyPermission === waiting) {
            pendingNotifyPermission = null
        }
        return granted
    }

    override fun notifyChat(conversationId: Long, title: String, body: String, unread: Int) {
        // 权限没给就什么也不弹：这一步不申请，申请是设置页那个开关的事。
        if (!canNotify()) return
        chatNotifier.notify(conversationId, title, body, unread)
    }

    override fun clearChatNotices() = chatNotifier.clearAll()

    /**
     * The toast is queued by the system, and a queued one is not replaced by the next call: "正在安装"
     * followed by "已安装" within its 3.5 s would leave the second one waiting until the first has
     * gone, which is after the install path has already closed the app. Cancelling first makes the
     * newest sentence the one on screen, which is what every caller means by "show this now".
     */
    private var currentToast: Toast? = null

    override fun showMessage(message: String) {
        currentToast?.cancel()
        currentToast = Toast.makeText(context, message, Toast.LENGTH_LONG).also { it.show() }
    }

    override fun supportsVpnDetection(): Boolean = true

    /**
     * True while a VPN, or an app told to route through Android's always-on VPN setting, carries the
     * traffic. Clash-style clients (FlClash, Karing on this phone) describe themselves as VPN apps
     * when they take over routing, so this covers the usual proxy setups without knowing about any
     * one of them.
     */
    override fun isVpnActive(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)

    /**
     * Opens the proxy client's own screen when one is installed: connecting is a button inside that
     * app, and this is the shortest path to it. Falls back to the system VPN settings, then to the
     * wireless settings, so the button never does nothing.
     */
    override fun openProxySettings(): Boolean {
        val candidates = listOf(
            // Same order as the runtime page: the client this phone is most likely to run first.
            Intent(Intent.ACTION_MAIN).setPackage("com.follow.clash").addCategory(Intent.CATEGORY_LAUNCHER),
            Intent(Intent.ACTION_MAIN).setPackage("com.nebula.karing").addCategory(Intent.CATEGORY_LAUNCHER),
            Intent(Settings.ACTION_VPN_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
        )
        for (intent in candidates) {
            val opened = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return true
        }
        LogUtils.w(TAG, "No VPN settings screen answered")
        return false
    }

    /**
     * Asks the package manager who installed this package. `getInstallSourceInfo` needs API 30;
     * below that the deprecated `getInstallerPackageName` answers the same question and is the only
     * thing available, so the warning is suppressed rather than worked around.
     */
    override fun isInstalledFromFdroid(): Boolean = runCatching {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
        installer in FDROID_INSTALLERS
    }.getOrDefault(false)

    override fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Opens the manager's own screen. [android.content.pm.PackageManager.getLaunchIntentForPackage]
     * returns null for an installed app that has no launcher entry, which is the whole test here:
     * nothing else about the package has to be known, and a missing app is a plain false.
     */
    override fun openApp(packageName: String): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return@runCatching false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    /**
     * Tries the app's own schemes before the web page. `startActivity` is the check: a scheme no
     * installed app claims throws [android.content.ActivityNotFoundException] here, which is the one
     * test that still works on Android 11+, where [Intent.resolveActivity] also needs a
     * `queries` entry in the manifest per scheme.
     */
    override fun openLink(web: String, vararg appSchemes: String): Boolean {
        for (scheme in appSchemes) {
            val intent = Intent(Intent.ACTION_VIEW, scheme.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val opened = try {
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                // No handler (or a security refusal): the next spelling, then the web page.
                LogUtils.w(TAG, "No app for '$scheme': ${e.message}")
                false
            }
            if (opened) return true
        }
        if (web.isNotEmpty()) openUrl(web)
        return false
    }

    override fun requireBiometric(title: String, subtitle: String, onSuccess: () -> Unit) {
        val activity = biometricActivity
        if (activity == null) {
            // Fail closed: skipping authentication would bypass the setting.
            LogUtils.e(TAG, "No FragmentActivity attached; cannot authenticate")
            return
        }
        val executor = ContextCompat.getMainExecutor(context)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    LogUtils.e(
                        TAG,
                        "Biometric error. Code: $errorCode, message: $errString"
                    )
                }

                override fun onAuthenticationFailed() {
                    LogUtils.w(TAG, "Biometric authentication failed!")
                }
            })

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * The device's lookup key for the login-free identity: a hash of `ANDROID_ID` and the build
     * fingerprint. `ANDROID_ID` is per app signing key, per user and per device, and it is the only
     * identifier a normal app may read that survives an uninstall — the serial number and the IMEI
     * need `READ_PHONE_STATE` and answer "unknown" for non-privileged apps since Android 10.
     *
     * A factory reset changes it, which is the intended limit: a new phone, or a wiped one, is a
     * new person. The raw values never leave the device; only this hash is ever sent.
     */
    override fun deviceKey(): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val raw = listOf(
            androidId,
            Build.FINGERPRINT,
            Build.MANUFACTURER,
            Build.MODEL,
            context.packageName,
        ).joinToString("|")
        return sha256Hex(raw)
    }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { byte -> ((byte.toInt() and 0xFF) or 0x100).toString(16).substring(1) }

    override fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Canta", text))
    }

    override fun readClipboard(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).text?.toString()
    }

    /**
     * Writes to the shared Download folder through MediaStore, which needs no permission for
     * the app's own files and shows up in every file manager. Android 9 has no MediaStore
     * collection for Downloads, so it falls back to the app's own external folder there —
     * still readable through a file manager, no storage permission asked for.
     */
    override fun exportLog(fileName: String, content: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (writeDownload(fileName, content, "text/csv") != null) {
                "Download/Canta/$fileName"
            } else {
                writeExternalFile(fileName, content)
            }
        } else {
            writeExternalFile(fileName, content)
        }
    } catch (e: Exception) {
        LogUtils.e(TAG, "Could not write '$fileName' to the Download folder", e)
        null
    }

    /**
     * Hands [content] to the share sheet as [fileName], so it can be sent to a computer instead of
     * being looked for in a folder. Download first (the file manager sees it too), and when the
     * system has no Downloads collection the copy in the app's own folder is served through the
     * FileProvider instead.
     */
    override fun shareFile(fileName: String, content: String, mimeType: String): Boolean = try {
        val downloaded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeDownload(fileName, content, mimeType)
        } else {
            null
        }
        val uri = downloaded ?: externalFileUri(fileName, content)

        if (uri == null) {
            false
        } else {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "发送到电脑")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        }
    } catch (e: Exception) {
        LogUtils.e(TAG, "Could not share '$fileName'", e)
        false
    }

    /** A shareable URI for a copy under the app's external share folder, or null when unwritable. */
    private fun externalFileUri(fileName: String, content: String): Uri? {
        val directory = context.getExternalFilesDir(SHARE_DIRECTORY)
            ?: File(context.cacheDir, SHARE_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) return null
        val file = File(directory, fileName)
        file.writeText(content)
        return runCatching {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }.getOrNull()
    }

    private fun writeDownload(fileName: String, content: String, mimeType: String): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val folder = "${Environment.DIRECTORY_DOWNLOADS}/Canta/"

        // Reuse this app's own entry when it is already there, so the file is replaced instead of
        // piling up as "canta-operations (1).csv", "(2)" and so on.
        val existing = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(fileName, folder),
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

        val uri = if (existing != null) {
            ContentUris.withAppendedId(collection, existing)
        } else {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            }
            resolver.insert(collection, values) ?: return null
        }

        // "wt" truncates: the file always holds the whole log, never the tail of an older one.
        resolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) } ?: return null
        return uri
    }

    /**
     * Downloads the APK into the app's external files directory, resuming wherever the last try
     * stopped and retrying by itself until the file is whole.
     *
     * Three things this deliberately does not do. It does not buffer the whole APK in a
     * `ByteArray` (30 MB of heap for a file that belongs on disk). It does not rely on the progress
     * callback of the engine: a download whose length the server never announces, or a plugin that
     * is not installed, leaves that callback silent — which reads as "0%" forever even though
     * bytes are arriving; counting what is written cannot lie. And it no longer throws the bytes it
     * already has away when a transfer dies: the partial file is named after the APK itself, the
     * next attempt asks for `Range: bytes=<have>-`, and a dropped proxy node costs the seconds it
     * takes to reconnect instead of the whole file.
     *
     * A transfer that stops moving fails instead of hanging: the socket timeout below is what turns
     * a stalled node into an answer, and the answer is one more attempt rather than a dead end the
     * reader has to clear by hand.
     */
    override suspend fun downloadUpdate(
        url: String,
        fileName: String,
        onProgress: (received: Long, total: Long) -> Unit,
    ): UpdateDownload = updateDownload.withLock { downloadUpdateLocked(url, fileName, onProgress) }

    /** The body of [downloadUpdate]; only ever runs while [updateDownload] is held. */
    private suspend fun downloadUpdateLocked(
        url: String,
        fileName: String,
        onProgress: (received: Long, total: Long) -> Unit,
    ): UpdateDownload = withContext(Dispatchers.IO) {
        val directory = context.getExternalFilesDir("updates") ?: context.filesDir
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
            LogUtils.e(TAG, "Could not create ${directory.absolutePath}")
            return@withContext UpdateDownload.Failed(UpdateDownloadFailure.STORAGE)
        }
        // The first caller may already have fetched this release while the second one waited for
        // the lock: the bytes are on disk, so there is nothing to download.
        val existing = File(directory, fileName)
        if (existing.isFile) {
            if (isCompleteApk(existing)) {
                deleteStaleUpdateApks(directory, existing)
                val length = existing.length()
                onProgress(length, length)
                return@withContext UpdateDownload.Done(existing.absolutePath)
            }
            // A half-written or corrupted leftover would win the name in `uniqueUpdateFile` and be
            // installed by mistake; the partial below is the one that gets resumed.
            existing.delete()
        }
        // One release, one partial file: a download that gave up is picked up by the next attempt —
        // in this process or after a restart — instead of starting the APK over from byte zero.
        val partial = File(directory, "$fileName.part")
        // A process killed between the last byte and the rename leaves a whole APK under the
        // partial's name; that is a finished download, not something to fetch again.
        if (partial.isFile && isCompleteApk(partial)) {
            val landed = settle(partial, uniqueUpdateFile(directory, fileName))
            deleteStaleUpdateApks(directory, landed)
            val length = landed.length()
            onProgress(length, length)
            LogUtils.i(TAG, "Resumed a finished download at ${landed.absolutePath}")
            return@withContext UpdateDownload.Done(landed.absolutePath)
        }
        val client = newDownloadClient()
        var failure = UpdateDownloadFailure.UNKNOWN
        // What the partial held before the last attempt: a file that ends without being an APK is
        // kept as long as it is still growing (the next range request adds to it), and dropped once
        // an attempt adds nothing, because resuming a file that never grows is a loop.
        var previousLength = partial.length()
        try {
            for (attempt in 1..DOWNLOAD_ATTEMPTS) {
                coroutineContext.ensureActive()
                try {
                    val written = transferOnce(client, url, partial, onProgress)
                    coroutineContext.ensureActive()
                    if (!isCompleteApk(partial)) {
                        // A resumed file can be a good prefix with a bad tail: a proxy that
                        // appended its own error page, a range answered from another revision.
                        // Nothing that is not an APK may reach `pm install`.
                        val length = partial.length()
                        LogUtils.w(TAG, "Attempt $attempt from $url did not produce an APK at $length bytes")
                        if (length <= previousLength) partial.delete()
                        previousLength = length
                        failure = UpdateDownloadFailure.NETWORK
                    } else if (written > 0L) {
                        val landed = settle(partial, uniqueUpdateFile(directory, fileName))
                        // Partials of older releases are not this download's leftovers to keep: a
                        // failed 4.1 download is dead weight once 4.2 is on disk.
                        directory.listFiles { file -> file.name.endsWith(".part") && file != landed }
                            ?.forEach { it.delete() }
                        // So are the whole APKs of releases that were downloaded but never
                        // installed: without this, every release the reader skipped leaves a
                        // ~30 MB corpse behind and the folder grows one file per version.
                        deleteStaleUpdateApks(directory, landed)
                        onProgress(written, written)
                        LogUtils.i(TAG, "Downloaded $written bytes to ${landed.absolutePath}")
                        return@withContext UpdateDownload.Done(landed.absolutePath)
                    } else {
                        LogUtils.e(TAG, "Downloaded an empty file from $url")
                        partial.delete()
                        failure = UpdateDownloadFailure.NETWORK
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = classifyDownloadFailure(e)
                    LogUtils.w(TAG, "Attempt $attempt of $DOWNLOAD_ATTEMPTS failed for $url: $e")
                    // A full disk or a build without a downloader will not get better by waiting,
                    // and neither will a release GitHub says does not exist.
                    val permanent = failure == UpdateDownloadFailure.STORAGE ||
                        failure == UpdateDownloadFailure.UNSUPPORTED ||
                        (e as? DownloadFailure)?.retryable == false
                    if (permanent) {
                        // A full disk is the one permanent failure whose bytes are worth keeping:
                        // free some space and the next attempt continues where this one stopped.
                        if (failure != UpdateDownloadFailure.STORAGE) partial.delete()
                        return@withContext UpdateDownload.Failed(failure)
                    }
                    if (attempt < DOWNLOAD_ATTEMPTS) delay(retryDelayMillis(attempt))
                }
            }
            UpdateDownload.Failed(failure)
        } finally {
            client.close()
        }
    }

    /** One HTTP client per download: the timeouts below belong to the transfer, not to the check. */
    private fun newDownloadClient(): HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = STALL_TIMEOUT_MILLIS
            // A ceiling for one segment, so a node that keeps the socket alive but never finishes
            // cannot hold the update screen open indefinitely.
            requestTimeoutMillis = DOWNLOAD_TIMEOUT_MILLIS
        }
    }

    /**
     * One transfer of [url] into [partial], continuing from whatever the file already holds.
     *
     * A server that answers 206 gets its bytes appended. A server that ignores the range (200) is
     * honoured by truncating and starting over — gluing a second copy onto the first would produce
     * a file twice as long that installs nowhere. 416 means the partial is already at or past the
     * announced length, so it is dropped and the next attempt starts clean.
     */
    private suspend fun transferOnce(
        client: HttpClient,
        url: String,
        partial: File,
        onProgress: (received: Long, total: Long) -> Unit,
    ): Long {
        val have = if (partial.isFile) partial.length() else 0L
        return client.prepareGet(url) {
            header(HttpHeaders.UserAgent, "Canta-Android")
            if (have > 0) header(HttpHeaders.Range, "bytes=$have-")
        }.execute { response ->
            val status = response.status
            // Compared by number, never by `HttpStatusCode` equality: over HTTP/2 the status line
            // carries no reason phrase, so the constant and the engine's value differ (the second
            // is `HttpStatusCode(206, "")`) and an equality test would never match — the resume
            // would silently be a full download again, and 416 would fall through as permanent.
            if (status.value == HTTP_RANGE_NOT_SATISFIABLE) {
                partial.delete()
                throw DownloadFailure(UpdateDownloadFailure.NETWORK, "GitHub answered 416 for bytes=$have-")
            }
            if (status.value !in 200..299) {
                throw DownloadFailure(
                    failure = UpdateDownloadFailure.NETWORK,
                    message = "GitHub answered ${status.value}",
                    // 408 and 429 are the two 4xx answers that mean "come back later"; 403 and 404
                    // will answer the same forever.
                    retryable = status.value !in 400..499 ||
                        status.value == 408 || status.value == 429,
                )
            }
            // 0 or less: no announced size, so the page shows movement, not a percentage.
            val announced = response.contentLength() ?: -1L
            // Where the server says this segment starts. A proxy is free to answer a range from an
            // offset nobody asked for, so the number is checked; a server that omits the header is
            // taken at its word.
            val rangeStart = response.headers[HttpHeaders.ContentRange]
                ?.substringAfter("bytes ", "")
                ?.substringBefore('-')
                ?.trim()
                ?.toLongOrNull()
            val resumed = have > 0 && status.value == HTTP_PARTIAL_CONTENT &&
                (rangeStart == null || rangeStart == have)
            // A 206 announces what is left, not the size of the file: the page wants the whole count.
            val total = when {
                announced <= 0 -> -1L
                resumed -> have + announced
                else -> announced
            }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(DOWNLOAD_CHUNK_BYTES)
            var received = if (resumed) have else 0L
            var reported = -1L
            FileOutputStream(partial, resumed).use { out ->
                // Say where the transfer starts before the first byte: after a retry the page keeps
                // the percentage it had instead of falling back to a spinner.
                onProgress(received, total)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read == 0) continue
                    out.write(buffer, 0, read)
                    received += read
                    // Report once per percent of an announced size, and once per chunk when there
                    // is no size to divide by; a per-byte callback is just churn.
                    val step = if (total > 0) (received * 100) / total else received / DOWNLOAD_CHUNK_BYTES
                    if (step != reported) {
                        reported = step
                        onProgress(received, total)
                    }
                }
                out.flush()
            }
            received
        }
    }

    /** Whether [file] is a whole APK: a zip that still carries the manifest `pm install` reads. */
    private fun isCompleteApk(file: File): Boolean = runCatching {
        ZipFile(file).use { it.getEntry("AndroidManifest.xml") != null }
    }.getOrDefault(false)

    /** [name] inside [directory], suffixed when an older download of the same release is there. */
    private fun uniqueUpdateFile(directory: File, name: String): File {
        val direct = File(directory, name)
        if (!direct.exists()) return direct
        val extension = name.substringAfterLast('.', "")
        val base = if (extension.isEmpty()) name else name.removeSuffix(".$extension")
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        for (index in 1..20) {
            val candidate = File(directory, "$base-$index$suffix")
            if (!candidate.exists()) return candidate
        }
        return File(directory, "$base-${System.currentTimeMillis()}$suffix")
    }

    /** Exponential back-off with a ceiling: waiting longer than that stops looking like a retry. */
    private fun retryDelayMillis(attempt: Int): Long =
        (RETRY_BASE_DELAY_MILLIS * (1L shl (attempt - 1))).coerceAtMost(RETRY_MAX_DELAY_MILLIS)

    /**
     * Moves the finished partial onto [target] and answers the file the installer should read.
     *
     * A rename can fail while the source is still held open, so the bytes are copied instead. What
     * is never handed over is the partial's own name: MediaStore shows it, and the installer's
     * fallbacks read it, and neither should ever see `…apk.part`.
     */
    private fun settle(partial: File, target: File): File =
        if (partial.renameTo(target)) {
            target
        } else {
            partial.copyTo(target, overwrite = true)
            partial.delete()
            target
        }

    /**
     * Deletes every whole APK in [directory] other than [keep], so the folder holds the one
     * release being installed now and nothing else: an APK the reader never installed (an
     * install that was refused, a release the poll superseded) would otherwise sit there
     * forever, one file per version.
     */
    private fun deleteStaleUpdateApks(directory: File, keep: File) {
        directory.listFiles { file -> file.isFile && file != keep && file.name.endsWith(".apk") }
            ?.forEach { file ->
                if (file.delete()) {
                    LogUtils.i(TAG, "Removed the stale update APK ${file.name}")
                }
            }
    }

    /** A download that ended for a reason already known, carrying it out unchanged. */
    private class DownloadFailure(
        val failure: UpdateDownloadFailure,
        message: String,
        val retryable: Boolean = true,
    ) : Exception(message)

    /**
     * Sorts a thrown error into the reasons the page names. The same rule as the check: the
     * rendered exception is what means the same thing on every engine, so its text is matched
     * rather than its type.
     */
    private fun classifyDownloadFailure(e: Throwable): UpdateDownloadFailure {
        if (e is DownloadFailure) return e.failure
        val text = e.toString().lowercase()
        return when {
            "enospc" in text || "no space" in text || "space left" in text -> UpdateDownloadFailure.STORAGE
            "timeout" in text || "timed out" in text -> UpdateDownloadFailure.TIMEOUT
            "unresolvedaddress" in text || "unknownhost" in text || "connect" in text ||
                "ioexception" in text || "socket" in text || "ssl" in text ||
                "handshake" in text || "refused" in text || "offline" in text ||
                "network" in text || "http" in text -> UpdateDownloadFailure.NETWORK
            else -> UpdateDownloadFailure.UNKNOWN
        }
    }

    /**
     * Installs [path] over this app with `pm install -r -d` as Shizuku's identity: `-r` replaces
     * the installed copy and keeps its data, `-d` allows the older version code that a debug build
     * carries. Returns null on success, or the output that explains the failure.
     *
     * Where the shell may read the file from is decided by scoped storage, not by us: on Q+ the
     * FUSE view hides another app's `Android/data` from the shell identity, and `pm install`
     * answers "Consider using a file under /data/local/tmp/" because it cannot open the APK. So
     * the same file is offered to `pm` through three homes, cheapest first — the first one that
     * installs wins, and every staging copy is cleaned up afterwards:
     *
     *  1. the downloaded file itself: works before Q, and on ROMs that never closed the hole;
     *  2. a copy in the public `Download/Canta/` folder through MediaStore, which the shell reads
     *     like any other shared-storage file;
     *  3. the bytes pushed into `/data/local/tmp/` in base64 chunks — the one place `pm` itself
     *     recommends, readable wherever Shizuku can run.
     */
    override suspend fun installUpdate(path: String): String? =
        updateInstall.withLock {
            // The bound is this attempt, not this process: `now - elapsedRealtime` names the boot,
            // and any update since the phone started would be read as "this one landed".
            val startedAt = System.currentTimeMillis()
            val failure = installUpdateLocked(path)
            // `pm install` replaces the package under this very process, and Android kills the
            // app as part of doing so: the shell call can come back with an error even though the
            // APK did land. Asking the package manager whether this app was updated after
            // [startedAt] tells "it really failed" from "it succeeded and the shell was cut
            // short" — without it the system installer is opened over an update that is already
            // in place, which is read as "又弹安装，还没装上".
            if (failure != null && wasReplacedSince(startedAt)) {
                LogUtils.i(TAG, "pm install reported a failure but this app was replaced anyway: $failure")
                null
            } else {
                failure
            }
        }

    /** Whether this package was updated at or after [since], epoch milliseconds. */
    private fun wasReplacedSince(since: Long): Boolean = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime >= since
    }.getOrDefault(false)

    /** The body of [installUpdate]; only ever runs while [updateInstall] is held. */
    private suspend fun installUpdateLocked(path: String): String? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext "update file is missing: $path"

        val direct = runInstall(file.absolutePath)
        if (direct == null) {
            file.delete()
            return@withContext null
        }
        LogUtils.w(TAG, "pm install from the app dir failed: $direct")

        // The public Downloads folder is one MediaStore write away and readable by the shell.
        val staged = stageInDownloads(file)
        if (staged != null) {
            val (stagedPath, stagedUri) = staged
            val failure = runInstall(stagedPath)
            runCatching { stagedUri?.let { context.contentResolver.delete(it, null, null) } }
            if (failure == null) {
                file.delete()
                return@withContext null
            }
            LogUtils.w(TAG, "pm install from $stagedPath failed: $failure")
        }

        // The chunked push to /data/local/tmp is the path that works everywhere Shizuku does; its
        // answer is the one shown to the reader, since it is the last attempt made.
        installViaTmp(file)?.let { return@withContext it }
        file.delete()
        null
    }

    /** One `pm install -r -d` of [where]; null on success, otherwise the output that explains it. */
    private suspend fun runInstall(where: String): String? {
        val result = shell.run("pm install -r -d ${shellQuote(where)}")
        return when {
            result.ok && !result.output.contains("Failure", ignoreCase = true) -> null
            result.output.isBlank() -> "pm install failed without output"
            else -> result.output.trim()
        }
    }

    /**
     * Copies [file] into the public `Download/Canta/` folder and answers the filesystem path the
     * shell can read plus the MediaStore row to clean up afterwards; null when any step failed.
     */
    private fun stageInDownloads(file: File): Pair<String, Uri>? = if (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    ) {
        null // Before Q there is no scoped storage: the direct path already worked.
    } else {
        runCatching {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val folder = "${Environment.DIRECTORY_DOWNLOADS}/Canta/"

            // Reuse this app's own row when it is already there, so a retry replaces the file.
            val existing = resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(file.name, folder),
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

            val uri = if (existing != null) {
                ContentUris.withAppendedId(collection, existing)
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                }
                resolver.insert(collection, values) ?: return null
            }
            resolver.openOutputStream(uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: return null
            // `pm install` wants a filesystem path, not a content:// URI: the DATA column is the
            // real one ("/storage/emulated/0/Download/Canta/<name>").
            val where = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: return null
            where to uri
        }.getOrNull()
    }

    /**
     * Pushes [file] to `/data/local/tmp/.canta-update.apk` in base64 chunks and installs it there.
     * Chunks stay far under the 128 KB per-argument limit of `execve`, so every `printf` is one
     * legal `sh -c` script; the pushed byte count is verified before `pm install` runs, and both
     * temporaries are removed afterwards whatever the outcome.
     */
    private suspend fun installViaTmp(file: File): String? {
        val base = "/data/local/tmp/.canta-update"
        shell.run("rm -f '$base.b64' '$base.apk'")
        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val chunkSize = 90_000
        var offset = 0
        while (offset < encoded.length) {
            val end = minOf(offset + chunkSize, encoded.length)
            val chunk = encoded.substring(offset, end)
            val run = shell.run("printf %s '${chunk.replace("'", "'\\''")}' >> '$base.b64'")
            if (!run.ok) {
                shell.run("rm -f '$base.b64'")
                return "推送到 /data/local/tmp 失败（偏移 $offset）：${run.output.trim().take(200)}"
            }
            offset = end
        }
        val verify = shell.run(
            "base64 -d '$base.b64' > '$base.apk' && rm -f '$base.b64' && " +
                "test \"\$(wc -c < '$base.apk')\" -eq ${file.length()} && echo ok"
        )
        if (!verify.ok || !verify.output.contains("ok")) {
            shell.run("rm -f '$base.b64' '$base.apk'")
            return "暂存的 APK 校验失败：${verify.output.trim().take(200)}"
        }
        val failure = runInstall("$base.apk")
        shell.run("rm -f '$base.apk'")
        return failure
    }

    /** Closes the process: the installer already replaced the APK, so the next launch is the new build. */
    override fun exitApp() {
        exitProcess(0)
    }

    /** Single-quotes [value] for `sh -c`, the quoting `pm install` needs for a path with spaces. */
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /**
     * Hands the APK to the system installer: a content:// URI the installer is granted read
     * access to, so the system reads the file through the provider and no path permission on this
     * phone matters. The reader confirms the install in the system dialog, which also survives the
     * case where every privileged location was refused.
     */
    override fun requestInstall(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            LogUtils.w(TAG, "No installer could take the update: $e")
            false
        }
    }

    private fun writeExternalFile(fileName: String, content: String): String? {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val file = File(directory, fileName)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file.absolutePath
    }
}