package io.github.samolego.canta.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
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
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import io.github.samolego.canta.core.root.rootMayBeAvailable
import io.github.samolego.canta.core.shizuku.ShizukuPermission
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
import io.github.samolego.canta.core.root.RootShell
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
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

        /**
         * 压缩后长边的上限（像素）。2048 够聊天里点开放大看，再大就只是流量：一张 4000×3000 的
         * 手机照片按这个采样先减到 2000×1500，内存从 48MB 掉到 12MB，后面的质量循环才跑得动。
         */
        private const val COMPRESS_LONG_EDGE = 2048

        /**
         * 尺寸最多退几轮，每轮 ×0.75：2048 → 1536 → 1152 → 864。配合质量 90/80/70/60 四档，
         * 一次调用最坏 4 轮 × 4 档 = 16 次 JPEG 编码；正常照片第一轮第一档就落地。
         */
        private const val COMPRESS_ROUNDS = 4

        /**
         * 头像最后的分辨率：256×256 够一个 96dp 的圆在 3 倍屏上清晰（288 像素），再大只是体积。
         */
        private const val AVATAR_EDGE = 256

        /**
         * 头像压不下去时退到的下限：128 已经是一个 44dp 小圆的 3 倍屏分辨率，再小就看得出来糊。
         */
        private const val AVATAR_EDGE_FLOOR = 128

        /**
         * 头像解码时的长边上限：一张两千万像素的原图直接解 ARGB_8888 要几十 MB，而这里最后只留一个
         * 256 的方图。1024 留出了裁切和缩放需要的余量，也把内存压到 4MB 以内。
         */
        private const val AVATAR_SOURCE_EDGE = 1024

        /** One channel's error line in the install report: the cause, not the whole `pm` dump. */
        private const val INSTALL_ERROR_CHARS = 160

        /** 「存储到」界面的默认类型：名单是 JSON，但用户常把它改名成 .txt，所以按纯文本给。 */
        private const val SAVE_MIME = "text/plain"

        /** Suffix of the file that records the SHA-256 of a downloaded APK, next to the APK. */
        private const val HASH_SUFFIX = ".sha256"

        /**
         * How long a root install may take. Generous on purpose: `pm install` of a 30 MB APK on a
         * busy phone can take a while, and cutting it short would kill the shared shell — the next
         * command would then cost another authorization prompt.
         */
        private const val ROOT_INSTALL_TIMEOUT_MILLIS = 120_000L

        /**
         * How long one `pm install` over Shizuku may take. Longer than a bare shell command: the
         * package manager unpacks and verifies a 30 MB APK inside this wait, and cutting it short
         * would report a failure for an install that is in fact completing.
         */
        private const val PM_INSTALL_TIMEOUT_MILLIS = 180_000L

        /** Where root puts the APK before `pm` reads it: `/data/local/tmp` is the place `pm` names. */
        private const val ROOT_TMP_APK = "/data/local/tmp/.canta-update.apk"

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
    private val shell by lazy { ShizukuShell.shared(context) }

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

    /** 文件挑选器（导入名单用）：同样只能在 [attachActivity] 里注册。 */
    private var textFilePicker: ActivityResultLauncher<Array<String>>? = null

    /** 正在等哪一次挑文件的结果；同一时刻只会有一个。 */
    private var pendingTextFile: CompletableDeferred<String?>? = null

    /** 「保存到」挑选器（导出到用户选的位置用）：同样只能在 [attachActivity] 里注册。 */
    private var saveFileLauncher: ActivityResultLauncher<String>? = null

    /** 正在等哪一次保存的结果；同一时刻只会有一个。 */
    private var pendingSaveFile: CompletableDeferred<String?>? = null

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
        // 导入名单：系统文件选择器挑一个 json/txt，读成字符串交回去。
        textFilePicker = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            val waiting = pendingTextFile
            pendingTextFile = null
            if (uri == null) {
                waiting?.complete(null)
            } else {
                val text = runCatching {
                    activity.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().decodeToString()
                    }
                }.getOrNull()
                waiting?.complete(text)
            }
        }
        // 导出名单 / 恢复脚本：系统的「存储到」界面，用户自己决定放哪里（U 盘、网盘挂载、
        // 随便哪个文件夹），而不是只能落在 Download/Canta 里。
        saveFileLauncher = activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument(SAVE_MIME),
        ) { uri ->
            val waiting = pendingSaveFile
            pendingSaveFile = null
            if (uri == null) {
                waiting?.complete(null)
            } else {
                val where = runCatching {
                    activity.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(pendingSaveContent.toByteArray())
                    }
                    displayNameOf(uri)
                }.getOrNull()
                pendingSaveContent = ""
                waiting?.complete(where)
            }
        }
    }

    /** 正在写的正文；SAF 的回调不带内容，只能从这一格取。写完就清空。 */
    private var pendingSaveContent: String = ""

    /** 「Download/Canta/canta-deletion-list.json」这种给人看的落点。 */
    private fun displayNameOf(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: uri.lastPathSegment ?: uri.toString()
    }.getOrDefault(uri.toString())

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
        textFilePicker = null
        pendingTextFile = null
        saveFileLauncher = null
        pendingSaveFile = null
        pendingSaveContent = ""
    }

    /**
     * 让用户自己挑保存位置。取消、超时（三分钟）或没有活动可挂界面都回答 null。
     *
     * 拉起的是系统的「存储到」界面（SAF），所以位置由用户定：U 盘、网盘挂载、任意文件夹。
     */
    override suspend fun saveFileAs(
        fileName: String,
        content: String,
        mimeType: String,
    ): String? {
        val launcher = saveFileLauncher ?: return null
        val waiting = CompletableDeferred<String?>()
        pendingSaveFile = waiting
        pendingSaveContent = content
        withContext(Dispatchers.Main) { launcher.launch(fileName) }
        val where = withTimeoutOrNull(180_000L) { waiting.await() }
        if (pendingSaveFile === waiting) {
            pendingSaveFile = null
            pendingSaveContent = ""
        }
        return where
    }

    /**
     * 让用户从文件管理器里挑一个文本文件。
     *
     * 用户取消、超时（三分钟）或者当前没有活动可以挂选择器，都回答 null——调用方把 null 当成
     * "没有选"，不弹错误。编码按 UTF-8 解：导出的 JSON 就是它写的。
     */
    override suspend fun pickTextFile(mimeTypes: List<String>): String? {
        val launcher = textFilePicker ?: return null
        val waiting = CompletableDeferred<String?>()
        pendingTextFile = waiting
        withContext(Dispatchers.Main) { launcher.launch(mimeTypes.toTypedArray()) }
        val text = withTimeoutOrNull(180_000L) { waiting.await() }
        if (pendingTextFile === waiting) {
            pendingTextFile = null
        }
        return text
    }

    /**
     * 系统栏底色跟着页面背景走，见 [CantaPlatform.followSystemBarColor]。
     *
     * 顺手把窗口自身的底色也换成同一个颜色：`enableEdgeToEdge()` 生效时用不到（内容本来就画到
     * 系统栏底下），但在不让应用铺到系统栏下面的 ROM 上，那两条的缝隙露的就是窗口底色——同色
     * 之后「黑框」不再是一条看得见的边。
     */
    @Suppress("DEPRECATION")
    override fun followSystemBarColor(backgroundArgb: Long, lightBars: Boolean) {
        // `biometricActivity` 就是 attachActivity 记下的那个 Activity，这里只是借它，不再多一个字段。
        val activity = biometricActivity ?: return
        val color = backgroundArgb.toInt()
        activity.runOnUiThread {
            runCatching {
                val window = activity.window
                window.setBackgroundDrawable(ColorDrawable(color))
                // Android 15 起系统强制 edge-to-edge，这两个 setter 已经是空操作；更早的版本上
                // 它们才是真正决定系统栏颜色的那一个。
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    window.statusBarColor = color
                    window.navigationBarColor = color
                }
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    // 浅色背景要深色图标，深色背景要浅色图标；写反了在纯色条上直接看不见。
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }.onFailure { LogUtils.w(TAG, "系统栏配色没设上：${it.message}") }
        }
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

    /**
     * 把一张图压到 [maxBytes] 以内，返回 JPEG 字节；解不开、压不下去就回答 null。
     *
     * **同步、会占住线程几十到几百毫秒**（一张两千万像素的照片光是解码就要这么久），调用方
     * （`ChatScreen.sendImage`）已经把它挪到 `Dispatchers.Default` 上再调，别在组合或主线程里直接调。
     *
     * 三步，上一步不够就进下一步：
     *  1. 只读尺寸（`inJustDecodeBounds`，不解码像素），按 2 的幂设 `inSampleSize`，让长边落地
     *     不超过 [COMPRESS_LONG_EDGE]（2048）。这一步决定后面会不会 OOM：原图 4000×3000 直接解
     *     ARGB_8888 要 48MB，采样到 4 之后只剩 12MB。
     *  2. 质量从 90 往下试到 60（每 10 一档），第一档 ≤ [maxBytes] 就返回。多数照片到这儿就完了。
     *  3. 还不够就把尺寸乘 0.75 重来，最多 [COMPRESS_ROUNDS] 轮（2048 → 1536 → 1152 → 864）。
     *     到最后一轮还超就回答 null——回去让人换一张，比硬塞一张 3MB 的进聊天好。
     *
     * 中间那张缩放过的位图用完就 `recycle()`：一段聊天连着发几张图时，不回收会攒出一堆
     * 十几 MB 的 native 内存。
     */
    override fun compressImage(bytes: ByteArray, maxBytes: Int): ByteArray? {
        if (bytes.isEmpty() || maxBytes <= 0) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 2 的幂采样：BitmapFactory 只认 2 的幂，其余值会向下取到最近的 2 的幂。
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > COMPRESS_LONG_EDGE) {
            sample *= 2
        }
        var current = runCatching {
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }.getOrNull() ?: return null

        try {
            var round = 0
            while (true) {
                for (quality in 90 downTo 60 step 10) {
                    val out = ByteArrayOutputStream()
                    val encoded = runCatching {
                        current.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    }.getOrDefault(false)
                    if (encoded && out.size() <= maxBytes) return out.toByteArray()
                }
                round++
                if (round >= COMPRESS_ROUNDS) return null
                val scaled = runCatching {
                    Bitmap.createScaledBitmap(
                        current,
                        (current.width * 3 / 4).coerceAtLeast(1),
                        (current.height * 3 / 4).coerceAtLeast(1),
                        true,
                    )
                }.getOrNull() ?: return null
                if (scaled !== current) {
                    current.recycle()
                    current = scaled
                }
            }
        } finally {
            if (!current.isRecycled) current.recycle()
        }
    }

    /**
     * 头像：按 [crop] 裁成方块、缩到 [AVATAR_EDGE]，再压到 [maxBytes] 以内（20–200KB 那一档）。
     *
     * 和 [compressImage] 的差别是目的：聊天图要能放大看，所以留长边 2048；头像最后只画一个 96dp 的
     * 圆，体积全给质量更划算。四步，前一步不够就进下一步：
     *
     *  1. 只读尺寸，按 2 的幂采样到长边 ≤ [AVATAR_SOURCE_EDGE]（1024）。这一步决定会不会 OOM。
     *  2. 按 [crop] 的归一化矩形取下那块，取短边裁成正方形；[crop] 为 null 就是整图居中裁方。
     *  3. 缩到 256×256，质量从 92 往下试到 70，第一档 ≤ [maxBytes] 就返回。
     *  4. 都超就把边长缩到 ×0.75（256 → 192 → 128）再来一轮，到 [AVATAR_EDGE_FLOOR] 还超就回答 null，
     *     让调用方说一句"换一张"。
     *
     * 中间产生的位图用完就 `recycle()`：连着换几张头像时不回收会攒出几十 MB 的 native 内存。
     * **同步、会占住线程几十到几百毫秒**，调用方必须挪到 `Dispatchers.Default`。
     */
    override fun compressAvatar(bytes: ByteArray, crop: ImageCrop?, maxBytes: Int): ByteArray? {
        if (bytes.isEmpty() || maxBytes <= 0) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > AVATAR_SOURCE_EDGE) {
            sample *= 2
        }
        var current = runCatching {
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }.getOrNull() ?: return null

        try {
            // 归一化矩形 → 采样后位图上的像素；越界的部分夹回图内，块边取两者的短边。
            val left = ((crop?.left ?: 0f).coerceIn(0f, 1f) * current.width).toInt()
                .coerceIn(0, current.width - 1)
            val top = ((crop?.top ?: 0f).coerceIn(0f, 1f) * current.height).toInt()
                .coerceIn(0, current.height - 1)
            val wantedWidth = ((crop?.width ?: 1f).coerceIn(0.01f, 1f) * current.width).toInt().coerceAtLeast(1)
            val wantedHeight = ((crop?.height ?: 1f).coerceIn(0.01f, 1f) * current.height).toInt().coerceAtLeast(1)
            val side = minOf(wantedWidth, wantedHeight, current.width - left, current.height - top)
                .coerceAtLeast(1)
            val squared = runCatching { Bitmap.createBitmap(current, left, top, side, side) }
                .getOrNull() ?: return null
            if (squared !== current) {
                current.recycle()
                current = squared
            }

            var edge = AVATAR_EDGE
            while (true) {
                val scaled = if (current.width == edge && current.height == edge) {
                    current
                } else {
                    runCatching { Bitmap.createScaledBitmap(current, edge, edge, true) }.getOrNull()
                        ?: return null
                }
                try {
                    for (quality in 92 downTo 70 step 2) {
                        val out = ByteArrayOutputStream()
                        val encoded = runCatching {
                            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                        }.getOrDefault(false)
                        if (encoded && out.size() <= maxBytes) return out.toByteArray()
                    }
                } finally {
                    if (scaled !== current && !scaled.isRecycled) scaled.recycle()
                }
                if (edge <= AVATAR_EDGE_FLOOR) return null
                edge = maxOf(edge * 3 / 4, AVATAR_EDGE_FLOOR)
            }
        } finally {
            if (!current.isRecycled) current.recycle()
        }
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

    /** Android 有「申请通知权限」这一步（13 起是运行时权限），所以页顶那条提示摆得出来。 */
    override val canRequestNotify: Boolean = true

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
     * 打开系统那块 Wi-Fi / 移动数据面板。
     *
     * API 29 起有专门的面板（`ACTION_INTERNET_CONNECTIVITY`），拉下来就是网络开关，换 Wi-Fi 或
     * 切流量一步到位；旧系统退回无线设置页。与 [openProxySettings] 分开，是因为"连不上"的原因
     * 不一样：代理没开、还是这张网自己不通，解法不是同一个按钮。
     */
    override fun openNetworkSettings(): Boolean {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            }
            add(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
        for (intent in candidates) {
            val opened = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return true
        }
        LogUtils.w(TAG, "No network settings screen answered")
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
            if (isCompleteApk(existing) && verifyOrRecordHash(existing)) {
                deleteStaleUpdateApks(directory, existing)
                val length = existing.length()
                onProgress(length, length)
                return@withContext UpdateDownload.Done(existing.absolutePath, length)
            }
            // A half-written, corrupted or swapped leftover would win the name in `uniqueUpdateFile`
            // and be installed by mistake; the partial below is the one that gets resumed.
            existing.delete()
            sidecarOf(existing).delete()
        }
        // One release, one partial file: a download that gave up is picked up by the next attempt —
        // in this process or after a restart — instead of starting the APK over from byte zero.
        val partial = File(directory, "$fileName.part")
        // A process killed between the last byte and the rename leaves a whole APK under the
        // partial's name; that is a finished download, not something to fetch again.
        if (partial.isFile && isCompleteApk(partial)) {
            val landed = settle(partial, uniqueUpdateFile(directory, fileName))
            deleteStaleUpdateApks(directory, landed)
            recordHash(landed)
            val length = landed.length()
            onProgress(length, length)
            LogUtils.i(TAG, "Resumed a finished download at ${landed.absolutePath}")
            return@withContext UpdateDownload.Done(landed.absolutePath, length)
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
                        recordHash(landed)
                        onProgress(written, written)
                        LogUtils.i(TAG, "Downloaded $written bytes to ${landed.absolutePath}")
                        return@withContext UpdateDownload.Done(landed.absolutePath, written)
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

    /**
     * Whether [file] is a whole APK: a zip that still carries the manifest `pm install` reads.
     *
     * Only completeness is decided here. Whether the APK belongs to this app is checked once, at
     * install time — doing it here as well would turn a foreign build (a debug install fetching
     * the release APK) into six download retries instead of one sentence that says why.
     */
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
        val keepHash = sidecarOf(keep).name
        directory.listFiles { file ->
            file.isFile && file != keep && file.name != keepHash &&
                (file.name.endsWith(".apk") || file.name.endsWith(HASH_SUFFIX))
        }?.forEach { file ->
            if (file.delete()) {
                LogUtils.i(TAG, "Removed the stale update file ${file.name}")
            }
        }
    }

    /** Where the SHA-256 of [file] is recorded next to it. */
    private fun sidecarOf(file: File): File = File(file.parentFile, file.name + HASH_SUFFIX)

    /** SHA-256 of [file], hex, or null when it cannot be read. */
    private fun hashOf(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }.getOrNull()

    /** Records [file]'s hash, so the next reuse can prove the bytes are the ones that arrived. */
    private fun recordHash(file: File) {
        val hash = hashOf(file) ?: return
        runCatching { sidecarOf(file).writeText(hash) }
            .onFailure { LogUtils.w(TAG, "Could not record the checksum of ${file.name}: $it") }
    }

    /**
     * Whether [file] still has the bytes that were recorded for it; a file without a record is
     * accepted and gets one, which is how downloads from before this check keep working.
     */
    private fun verifyOrRecordHash(file: File): Boolean {
        val record = sidecarOf(file)
        val actual = hashOf(file) ?: return false
        val recorded = runCatching { record.readText().trim() }.getOrNull()
        if (recorded.isNullOrBlank()) {
            runCatching { record.writeText(actual) }
            return true
        }
        if (recorded == actual) return true
        LogUtils.w(TAG, "Checksum mismatch for ${file.name}: recorded $recorded, actual $actual")
        runCatching { record.delete() }
        return false
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
     * carries. Answers [InstallAttempt.Success], or why it did not work plus whether that reason is
     * about the runtime (`retryable`) rather than about this APK.
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
    override suspend fun installUpdate(
        path: String,
        onStage: (InstallStage, Int) -> Unit,
    ): InstallAttempt =
        updateInstall.withLock {
            // The bound is this attempt, not this process: `now - elapsedRealtime` names the boot,
            // and any update since the phone started would be read as "this one landed".
            val startedAt = System.currentTimeMillis()
            onStage(InstallStage.PREPARING, INSTALL_PROGRESS_UNKNOWN)
            val attempt = installUpdateLocked(path, onStage)
            // `pm install` replaces the package under this very process, and Android kills the
            // app as part of doing so: the shell call can come back with an error even though the
            // APK did land. Asking the package manager whether this app was updated after
            // [startedAt] tells "it really failed" from "it succeeded and the shell was cut
            // short" — without it the system installer is opened over an update that is already
            // in place, which is read as "又弹安装，还没装上".
            if (!attempt.succeeded && wasReplacedSince(startedAt)) {
                LogUtils.i(TAG, "pm install reported a failure but this app was replaced anyway: ${attempt.failure}")
                // 这种情况屏幕上一定有"××装不上"的 toast（可能三四条），但包其实已经换掉了。
                // 不补这一句，用户会以为安装失败了：必须明说"已经装上了"。
                withContext(Dispatchers.Main) { showMessage("其实已经装上了（pm 被切断，包已替换）") }
                InstallAttempt.Success
            } else {
                attempt
            }
        }

    /**
     * Whether this package was updated at or after [since], epoch milliseconds.
     *
     * A two-second margin: `lastUpdateTime` comes from the package manager's own clock and can be
     * truncated to the second, and a success read as a failure would reopen the system installer
     * over an update that is already in place.
     */
    private fun wasReplacedSince(since: Long): Boolean = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime >= since - 2_000L
    }.getOrDefault(false)

    /** The body of [installUpdate]; only ever runs while [updateInstall] is held. */
    private suspend fun installUpdateLocked(
        path: String,
        onStage: (InstallStage, Int) -> Unit,
    ): InstallAttempt = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext InstallAttempt.refused("安装包不见了：$path")

        // A debug install downloads the same release APK, and `pm install` would happily put that
        // second copy next to this one under its own package name — a second Canta, not an update.
        // Refused with a sentence that says exactly that.
        val archiveName = runCatching {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)?.packageName
        }.getOrNull()
        if (archiveName != null && archiveName != context.packageName) {
            return@withContext InstallAttempt.refused(
                "这个安装包的包名是 $archiveName，当前应用是 ${context.packageName}，它不会覆盖安装",
            )
        }

        // Every home the APK is offered to `pm` through, cheapest first, plus the root shell as the
        // last privileged route. Each refusal keeps its own line: "装不上" is not actionable,
        // "Shizuku 读不到这个目录 / root 没授权" is — and the caller prints all of them.
        val attempts = linkedMapOf<String, String>()

        /** 从 IO 线程弹一句 toast：Toast 必须在有 Looper 的线程上 show，所以回主线程。 */
        suspend fun toast(message: String) {
            withContext(Dispatchers.Main) { showMessage(message) }
        }

        /**
         * 「走哪条通道装上的」。
         *
         * 之前只有失败才弹 toast，成功那条一声不响：用户在屏幕上看到的是一串 "××装不上"，
         * 然后对话框突然没了 —— 读起来像"报了一堆错但不知道到底装没装"。第一条通道就成了不说话，
         * 反而是"下载目录被 avc denied 挡掉、最后走临时目录装上"这种情形最容易引起误会。
         * 只有前面的通道失败过才补这一句（一次就成的普通情况不用重复说"装好了"）。
         */
        suspend fun announceSuccess(channel: String) {
            if (attempts.isEmpty()) return
            LogUtils.i(TAG, "installed through $channel after: ${attempts.keys.joinToString()}")
            toast("$channel 装上了（前面 ${attempts.size} 条通道没成）")
        }

        suspend fun tryChannel(channel: String, install: suspend () -> String?): Boolean {
            // A channel that throws is a channel that failed: an out-of-memory base64 push or a
            // runtime that dies mid-call must become one report line, not an aborted batch.
            val failure = try {
                install()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // `Throwable`, not `Exception`: the base64 push allocates the whole APK, and an
                // OutOfMemoryError from it has to become a line in the report, not the end of the
                // install path.
                "通道异常：${summarized(e.toString())}"
            } ?: return true
            attempts[channel] = summarized(failure)
            // 这一条通道没成，用户要看得见：以前只写 LogUtils（"print"），屏幕上什么也没有，
            // 于是整个安装过程像卡住。一句话说完一条通道，下一条通道接着试。
            LogUtils.w(TAG, "$channel refused the install: $failure")
            toast("$channel 装不上：${summarized(failure).take(60)}")
            return false
        }

        // Shizuku 没在运行（被关掉 / 没启动）：那三条通道**一条都不用试**。
        //
        // 以前是硬试三条，每条都抛 `requires permission`（服务端任何拒绝都是这个文案），于是：
        //   ① 每条都弹一次「装不上」toast —— 用户看到三条红字，其实只是 Shizuku 没开；
        //   ② 每条的失败都会触发一次授权申请 ——「明明关了 Shizuku，它还一直要授权」；
        //   ③ 白等三次超时，安装明显变慢。
        // 运行时不在了就是不在，直接把包交给系统安装器，那才是这台手机上唯一能成的路。
        if (!ShizukuPermission.isRuntimeRunning()) {
            attempts["Shizuku"] = "没在运行：已改用系统安装器"
        } else {
            if (tryChannel("Shizuku·应用目录") {
                    onStage(InstallStage.INSTALLING, INSTALL_PROGRESS_UNKNOWN)
                    runInstall(file.absolutePath)
                }) {
                announceSuccess("Shizuku·应用目录")
                file.delete()
                return@withContext InstallAttempt.Success
            }

            // The public Downloads folder is one MediaStore write away and readable by the shell.
            onStage(InstallStage.STAGING, INSTALL_PROGRESS_UNKNOWN)
            val staged = stageInDownloads(file)
            if (staged != null) {
                val (stagedPath, stagedUri) = staged
                val installed = tryChannel("Shizuku·下载目录") {
                    onStage(InstallStage.INSTALLING, INSTALL_PROGRESS_UNKNOWN)
                    runInstall(stagedPath)
                }
                runCatching { stagedUri?.let { context.contentResolver.delete(it, null, null) } }
                if (installed) {
                    announceSuccess("Shizuku·下载目录")
                    file.delete()
                    return@withContext InstallAttempt.Success
                }
            }

            // The chunked push to /data/local/tmp is the path that works wherever Shizuku does.
            if (tryChannel("Shizuku·临时目录") { installViaTmp(file, onStage) }) {
                announceSuccess("Shizuku·临时目录")
                file.delete()
                return@withContext InstallAttempt.Success
            }
        }

        // A rooted phone can install what the shell cannot read, but not a build the package
        // manager itself refuses (a foreign signature, a downgrade): that refusal is the same
        // whoever asks, and a root prompt for it is a prompt for nothing.
        //
        // The gate is the same one the rest of the app uses: a `su` file *or* a root manager app.
        // Magisk, KernelSU and APatch mount `su` where a plain file check cannot see it, and the
        // stricter check here is why a phone that uninstalls system apps through root still got
        // "这台手机没有 su" from its own updater.
        // 「这台手机没有 su」不算一条**失败尝试**，只算"这条路没得走"。以前它被塞进 attempts，
        // 结果两个副作用：① 它参与下面 `all { looksLikeRuntimeNotReady }` 的投票，把"pm 真的拒绝
        // 了这个包"的批次误判成"运行时没就绪"，于是无限重试；② 报告里多一条与本次失败无关的行。
        // 现在只写在最后那份报告里，不参与判定。
        val rootSkipped: String
        if (!rootMayBeAvailable(context.packageManager)) {
            rootSkipped = "root：这台手机没有 su"
        } else if (attempts.values.any { looksLikeAccessProblem(it) }) {
            if (tryChannel("root") {
                    onStage(InstallStage.ROOT, INSTALL_PROGRESS_UNKNOWN)
                    installAsRoot(file.absolutePath)
                }) {
                announceSuccess("root")
                file.delete()
                return@withContext InstallAttempt.Success
            }
            rootSkipped = ""
        } else {
            rootSkipped = "root：没试：上面的失败不是权限或路径问题"
        }

        // The APK stays on disk on purpose: the caller's last move is the system installer, which
        // reads this file. The report is one line per route, in the order they were tried.
        val report = (attempts.entries.map { (channel, error) -> "$channel：$error" } +
            listOfNotNull(rootSkipped.ifBlank { null })).joinToString("\n")
        // "这一次运行时根本没就绪" 与 "这个包 pm 不接受" 是两件事：前者重试可能就好，后者重试只会
        // 得到同一句话。每一条通道的失败都只是"运行时不在"，才算前者。
        //
        // **但 Shizuku 压根没在运行时不算前者**：那不是"绑一下就好"，是用户把它关了。
        // 以前这里归成 notReady，于是等 20/30/60/120 秒重试四轮、每轮都失败，用户看到的是
        // 「一直要授权、一直装不上」。现在这种直接算 refused，交给系统安装器 —— 一次就到位。
        val runtimeAbsentOnly = attempts.keys.all { it == "Shizuku" } &&
            attempts["Shizuku"].orEmpty().startsWith("没在运行")
        if (!runtimeAbsentOnly && attempts.isNotEmpty() && attempts.values.all { looksLikeRuntimeNotReady(it) }) {
            InstallAttempt.notReady(report)
        } else {
            InstallAttempt.refused(report)
        }
    }

    /** One line that names the cause without filling the dialog: the whole text stays in the log. */
    private fun summarized(failure: String): String {
        val text = failure.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        return if (text.length <= INSTALL_ERROR_CHARS) text else text.take(INSTALL_ERROR_CHARS) + "…"
    }

    /**
     * Whether a channel failed for a reason root could actually change: the shell could not read
     * the APK, or was not there at all. A `INSTALL_FAILED_*` answer is about the package, not about
     * who asked, so it is not worth a root prompt.
     */
    private fun looksLikeAccessProblem(error: String): Boolean {
        val text = error.lowercase()
        if ("install_failed" in text) return false
        return listOf(
            "shizuku", "permission", "denied", "eacces", "consider using a file",
            "unable to open", "failed to open", "no such file", "not found",
            "没有输出", "没运行", "没授权", "没有响应", "服务异常", "通道异常",
            // 这几句是 Shizuku 侧的「没就绪 / 没授权」原话（见 ShizukuPermission.refusalText
            // 与 ShizukuShell）。少了它们，"Shizuku 没在运行" 会被判成「不是权限问题」，
            // 于是台账里那句 `root：没试：上面的失败不是权限或路径问题` 直接跳过 root ——
            // 一台明明能用 root 的手机，只能眼看着装不上。
            "这台手机没装 shizuku", "没找到 shizuku", "这台手机没有 su",
            "没被 shizuku 授权", "shizuku 没在运行", "没答上来", "没有可用的 shizuku",
            "binder haven't been received", "adduserservice", "requires permission",
        ).any { it in text }
    }

    /**
     * Whether a channel's answer is about the runtime not being there, rather than about the APK.
     *
     * This is the difference between "再等等，Shizuku 起来之后自己就装上了" and "这个包 pm 不接受，
     * 交给系统安装器吧". Every string here is one this file produces when no `pm install` ever ran.
     */
    private fun looksLikeRuntimeNotReady(error: String): Boolean {
        val text = error.lowercase()
        if ("install_failed" in text) return false
        return listOf(
            "没在运行", "没被 shizuku 授权", "没有响应", "没答上来", "服务异常", "通道异常",
            "没有可用的 shizuku", "没装 shizuku", "这台手机没有 su", "root 没有授予", "root 会话",
            // Shizuku 服务端那句 `Permission Denial: addUserService … requires permission`
            // 描述的是「授权状态没同步」，不是「这个包 pm 不接受」：重试一轮（ShizukuShell 会顺手
            // 重新申请一次授权）有意义，不该直接甩给系统安装器。
            "requires permission", "adduserservice", "binder haven't been received",
            "canta 还没被 shizuku 授权",
        ).any { it in text }
    }

    /**
     * `pm install` through the shared root shell.
     *
     * Two places are offered, because a rooted phone is still subject to where the *package manager*
     * may read from: the download sits on FUSE (`Android/data/…`), and `pm` runs inside the system
     * server, which is denied there on current ROMs — the answer is that `avc: denied { read } for
     * … fuse` line. So the APK is first tried as it is, and otherwise copied through the app's own
     * cache into `/data/local/tmp`, which is where `pm` itself points people at.
     *
     * The wait is bounded by [ROOT_INSTALL_TIMEOUT_MILLIS]: a prompt nobody answers would
     * otherwise hold the install open forever, and "安装中" that never ends is exactly the report
     * this whole path exists to stop.
     */
    private suspend fun installAsRoot(path: String): String? = withContext(Dispatchers.IO) {
        val direct = runRootInstall(path)
        if (direct == null) return@withContext null
        val staged = copyIntoCache(File(path)) ?: return@withContext direct
        try {
            val copied = RootShell.session.run(
                "cp ${shellQuote(staged.absolutePath)} '$ROOT_TMP_APK'",
                timeoutMs = ROOT_INSTALL_TIMEOUT_MILLIS,
            )
            if (copied.exitCode != 0) {
                val why = (copied.stdout + copied.stderr).trim().take(200)
                return@withContext "$direct\nroot 也读不到这个包：${why.ifBlank { "cp 退出码 ${copied.exitCode}" }}"
            }
            runRootInstall(ROOT_TMP_APK)
        } finally {
            staged.delete()
            runCatching { RootShell.session.run("rm -f '$ROOT_TMP_APK'") }
        }
    }

    /** One root `pm install -r -d` of [where]; null on success, otherwise what root printed. */
    private suspend fun runRootInstall(where: String): String? {
        val result = RootShell.session.run(
            "pm install -r -d ${shellQuote(where)}",
            timeoutMs = ROOT_INSTALL_TIMEOUT_MILLIS,
        )
        if (result.exitCode == 0 && !result.stdout.contains("Failure", ignoreCase = true)) return null
        return (result.stdout + result.stderr).trim().ifBlank { "pm install 退出码 ${result.exitCode}" }
    }

    /**
     * A copy of [file] inside the app's own cache, which root can read and FUSE cannot hide.
     *
     * Root reads `/data/data/<pkg>/cache` directly; nothing is asked of the shell here, so a
     * refusal later is about `pm`, not about where the bytes are.
     */
    private fun copyIntoCache(file: File): File? = runCatching {
        val copy = File(context.cacheDir, "update-root.apk")
        file.inputStream().use { input -> copy.outputStream().use { output -> input.copyTo(output) } }
        copy
    }.getOrNull()

    /** One `pm install -r -d` of [where]; null on success, otherwise the output that explains it. */
    private suspend fun runInstall(where: String): String? {
        val result = shell.run("pm install -r -d ${shellQuote(where)}", timeoutMs = PM_INSTALL_TIMEOUT_MILLIS)
        if (result.ok && !result.output.contains("Failure", ignoreCase = true)) return null
        return refusalText(result, "pm install 没有输出")
    }

    /**
     * Why a shell call did not do its job, in a sentence.
     *
     * A shell that never ran prints nothing: naming the runtime's own state is what makes the
     * per-channel report actionable instead of three identical empty lines (which is what the
     * report used to say when Shizuku was not up: "推送到 /data/local/tmp 失败（偏移 0）：").
     */
    private fun refusalText(result: TweakRun, emptyOutput: String): String = when (result.failure) {
        // 「没在运行，或没授权」合成一句的话，用户照着做也没法确定该开服务还是该点授权；
        // 后面的括号是 ShizukuShell 带回来的原话（版本 / uid / 绑定失败的那句），
        // 它就写在 update_reports.detail 里，下一次不用再靠人描述屏幕。
        TweakFailure.NoPrivilege -> withDetail(ShizukuPermission.refusalText(context.packageManager), result.output)
        TweakFailure.Timeout -> withDetail("Shizuku 没有响应", result.output)
        TweakFailure.Broken -> "Shizuku 服务异常：${summarized(result.output)}"
        TweakFailure.Unsupported -> "这台设备没有可用的 Shizuku 通道"
        null -> result.output.trim().ifBlank { emptyOutput }
    }

    /** 「一句结论（原因）」，原因空就不加括号。 */
    private fun withDetail(headline: String, detail: String): String {
        val reason = summarized(detail)
        return if (reason.isBlank()) headline else "$headline（$reason）"
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
    private suspend fun installViaTmp(file: File, onStage: (InstallStage, Int) -> Unit): String? {
        val base = "/data/local/tmp/.canta-update"
        val prepared = shell.run("rm -f '$base.b64' '$base.apk'")
        // Nothing to push into if the shell is not there: say that, instead of allocating a
        // base64 copy of the whole APK and reporting "偏移 0：" with an empty reason.
        if (!prepared.ok) return refusalText(prepared, "Shizuku 没有响应")
        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val chunkSize = 90_000
        var offset = 0
        // The push is the slow part of an install that has to go through /data/local/tmp (a 60 MB
        // APK is ~850 chunks), so it is the one stage with a real percentage behind it.
        onStage(InstallStage.PUSHING, 0)
        while (offset < encoded.length) {
            val end = minOf(offset + chunkSize, encoded.length)
            val chunk = encoded.substring(offset, end)
            val run = shell.run("printf %s '${chunk.replace("'", "'\\''")}' >> '$base.b64'")
            if (!run.ok) {
                shell.run("rm -f '$base.b64'")
                return "推送到 /data/local/tmp 失败（偏移 $offset）：${refusalText(run, "没有输出").take(200)}"
            }
            offset = end
            onStage(InstallStage.PUSHING, ((offset.toLong() * 100) / encoded.length).toInt().coerceIn(0, 100))
        }
        val verify = shell.run(
            "base64 -d '$base.b64' > '$base.apk' && rm -f '$base.b64' && " +
                "test \"\$(wc -c < '$base.apk')\" -eq ${file.length()} && echo ok"
        )
        if (!verify.ok || !verify.output.contains("ok")) {
            shell.run("rm -f '$base.b64' '$base.apk'")
            return "暂存的 APK 校验失败：${refusalText(verify, "没有输出").take(200)}"
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