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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import io.github.samolego.canta.core.shizuku.ShizukuShell
import io.github.samolego.canta.util.LogUtils
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.system.exitProcess

class AndroidCantaPlatform(
    private val context: Context,
) : CantaPlatform {

    companion object {
        private const val TAG = "AndroidCantaPlatform"
    }

    @Volatile
    private var biometricActivity: FragmentActivity? = null

    /** Runs `pm install` as Shizuku; created on first update, not for every page. */
    private val shell by lazy { ShizukuShell(context) }

    /** Attach the activity (from `onCreate`); used for biometric prompts. */
    fun attachActivity(activity: FragmentActivity) {
        biometricActivity = activity
    }

    /** Detach the activity when it is destroyed. */
    fun detachActivity(activity: FragmentActivity) {
        if (biometricActivity === activity) {
            biometricActivity = null
        }
    }

    override fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

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

    override fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

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
            writeDownload(fileName, content)
        } else {
            writeExternalFile(fileName, content)
        }
    } catch (e: Exception) {
        LogUtils.e(TAG, "Could not write '$fileName' to the Download folder", e)
        null
    }

    private fun writeDownload(fileName: String, content: String): String? {
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
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            }
            resolver.insert(collection, values) ?: return writeExternalFile(fileName, content)
        }

        // "wt" truncates: the file always holds the whole log, never the tail of an older one.
        resolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) }
            ?: return writeExternalFile(fileName, content)
        return "Download/Canta/$fileName"
    }

    /**
     * Downloads the APK into a unique file in the app's external files directory.
     * Where the privileged shell may read it is decided at install time; see [installUpdate].
     */
    override suspend fun downloadUpdate(
        url: String,
        fileName: String,
        onProgress: (Int) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        var target: File? = null
        try {
            val directory = context.getExternalFilesDir("updates") ?: context.filesDir
            if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
                LogUtils.e(TAG, "Could not create ${directory.absolutePath}")
                return@withContext null
            }
            val client = HttpClient(OkHttp)
            val bytes = try {
                client.get(url) {
                    onDownload { sent, total ->
                        if (total != null && total > 0) {
                            onProgress(((sent * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                    header(HttpHeaders.UserAgent, "Canta-Android")
                }.body<ByteArray>()
            } finally {
                client.close()
            }

            coroutineContext.ensureActive()
            val downloaded = File.createTempFile("canta-update-", ".apk", directory)
            target = downloaded
            downloaded.writeBytes(bytes)
            coroutineContext.ensureActive()
            onProgress(100)
            LogUtils.i(TAG, "Downloaded ${bytes.size} bytes to ${downloaded.absolutePath}")
            downloaded.absolutePath
        } catch (e: CancellationException) {
            target?.delete()
            throw e
        } catch (e: Exception) {
            target?.delete()
            LogUtils.e(TAG, "Could not download the update", e)
            null
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
    override suspend fun installUpdate(path: String): String? = withContext(Dispatchers.IO) {
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