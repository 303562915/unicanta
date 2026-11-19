package io.github.samolego.canta.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.Dispatchers
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
     * Downloads the APK into the app's own external files directory.
     *
     * The privileged helper runs as `shell`, which can read `/storage/emulated/0/Android/data/...`
     * but not the app's internal `filesDir`, so the update has to land outside the sandbox for
     * `pm install` to open it.
     */
    override suspend fun downloadUpdate(
        url: String,
        fileName: String,
        onProgress: (Int) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val directory = context.getExternalFilesDir("updates") ?: context.filesDir
            if (!directory.exists() && !directory.mkdirs()) {
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

            val target = File(directory, fileName)
            target.writeBytes(bytes)
            onProgress(100)
            LogUtils.i(TAG, "Downloaded ${bytes.size} bytes to ${target.absolutePath}")
            target.absolutePath
        } catch (e: Exception) {
            LogUtils.e(TAG, "Could not download the update", e)
            null
        }
    }

    /**
     * Installs [path] over this app with `pm install -r -d` as Shizuku's identity: `-r` replaces
     * the installed copy and keeps its data, `-d` allows the older version code that a debug build
     * carries. Returns null on success, or the output that explains the failure.
     */
    override suspend fun installUpdate(path: String): String? {
        if (!File(path).exists()) return "update file is missing: $path"
        val result = shell.run("pm install -r -d ${shellQuote(path)}")
        return when {
            result.ok && !result.output.contains("Failure", ignoreCase = true) -> null
            result.output.isBlank() -> "pm install failed without output"
            else -> result.output.trim()
        }
    }

    /** Closes the process: the installer already replaced the APK, so the next launch is the new build. */
    override fun exitApp() {
        exitProcess(0)
    }

    /** Single-quotes [value] for `sh -c`, the quoting `pm install` needs for a path with spaces. */
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun writeExternalFile(fileName: String, content: String): String? {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val file = File(directory, fileName)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file.absolutePath
    }
}