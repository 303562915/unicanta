package io.github.samolego.canta.core

import io.github.samolego.canta.util.LogUtils
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.security.MessageDigest

/** Desktop [CantaPlatform], using AWT for the browser and clipboard. */
class DesktopCantaPlatform : AdbCantaPlatform(TAG) {

    private companion object {
        const val TAG = "DesktopCantaPlatform"
    }

    override fun openUrl(url: String) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(java.net.URI(url))
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to open URL '$url'", e)
        }
    }

    override fun copyToClipboard(text: String) {
        try {
            Toolkit.getDefaultToolkit()
                .systemClipboard
                .setContents(StringSelection(text), null)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to copy to clipboard", e)
        }
    }

    override fun readClipboard(): String? {
        return try {
            Toolkit.getDefaultToolkit()
                .systemClipboard
                .getData(DataFlavor.stringFlavor) as? String
        } catch (e: Exception) {
            null
        }
    }

    /** The user's Downloads folder, or the home directory when there is none. */
    override fun exportLog(fileName: String, content: String): String? = try {
        val home = File(System.getProperty("user.home"))
        val downloads = File(home, "Downloads").let { if (it.isDirectory) it else home }
        val file = File(downloads, fileName)
        file.writeText(content)
        file.absolutePath
    } catch (e: Exception) {
        LogUtils.e(TAG, "Could not write '$fileName' to the Downloads folder", e)
        null
    }

    /**
     * The desktop has no equivalent of `ANDROID_ID`: an identity scoped to this machine and user
     * is the best available. Unlike Android this does survive a reinstall, which is what the
     * lookup key is for.
     */
    override fun deviceKey(): String {
        val raw = listOf(
            System.getProperty("os.name").orEmpty(),
            System.getProperty("os.arch").orEmpty(),
            System.getProperty("user.name").orEmpty(),
            System.getProperty("user.home").orEmpty(),
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { byte -> ((byte.toInt() and 0xFF) or 0x100).toString(16).substring(1) }
    }
}