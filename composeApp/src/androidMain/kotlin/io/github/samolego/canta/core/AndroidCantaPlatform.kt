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
import io.github.samolego.canta.util.LogUtils
import java.io.File
import java.security.MessageDigest

class AndroidCantaPlatform(
    private val context: Context,
) : CantaPlatform {

    companion object {
        private const val TAG = "AndroidCantaPlatform"
    }

    @Volatile
    private var biometricActivity: FragmentActivity? = null

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

    private fun writeExternalFile(fileName: String, content: String): String? {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val file = File(directory, fileName)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file.absolutePath
    }
}