package io.github.samolego.canta.core.root

import io.github.samolego.canta.core.adb.AdbTransport
import io.github.samolego.canta.core.adb.ShellResult
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A device shell that runs as root, so Canta can work on a rooted phone without Shizuku.
 *
 * This is the same [AdbTransport] contract the desktop and web builds implement over a cable; here
 * the "device" is the phone itself and the privileged identity comes from `su`. Everything above it
 * — the on-device helper, the package listing, uninstall/reinstall/freeze — is shared code that
 * never learns which of the two privileges it is running on.
 *
 * The helper is pushed the same way it is over ADB: written to a staging file the app can create,
 * then copied into place by root, because a normal app cannot write to `/data/local/tmp`.
 *
 * @param cacheDirectory a directory this app may write; the staging file lives there and is removed
 *   once root has copied it.
 */
class RootTransport(
    private val cacheDirectory: File,
) : AdbTransport {

    private companion object {
        const val TAG = "RootTransport"

        /** Where the helper is staged before root moves it; inside the app's own cache. */
        const val STAGING_NAME = "canta-helper-stage.apk"

        /** Permission bits only: `chmod` is given `644`, not the `stat` value that includes the type. */
        const val PERMISSION_MASK = 0xFFF

        /**
         * How long the authorization probe waits. A prompt nobody taps must not leave the app
         * claiming to be "checking root" forever; a denial is an answer too. Long enough that a
         * person who reads the dialog still gets their answer accepted.
         */
        const val PROBE_TIMEOUT_MILLIS = 30_000L
    }

    /** Whether a `su` binary answered as root. Cached: a probe per operation would be a spawn each. */
    @Volatile
    private var rootChecked = false

    @Volatile
    private var rootGranted = false

    /**
     * Throws the cached answer away so the next probe runs `su` again.
     *
     * The cache exists because a probe spawns a process. It must not survive the user pressing
     * "grant": that press is a new question, and answering it from the startup probe is why the
     * button used to do nothing at all.
     */
    fun forgetProbe() {
        rootChecked = false
        rootGranted = false
        // A new press is a new question: reusing the open shell would answer it from the grant the
        // manager already gave, and the button would look like it did nothing.
        RootShell.session.close()
    }

    /** True when `su` exists and handed this app a root shell. Probed once. */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (rootChecked) return@withContext rootGranted
        val binary = suBinaryPath()
        var timedOut = false
        val granted = runCatching {
            // Through the shared shell, not a one-off `su -c`: the probe is what opens the session,
            // so the manager's single prompt is all the authorization this run of the app needs.
            val result = RootShell.session.run("id", timeoutMs = PROBE_TIMEOUT_MILLIS)
            timedOut = "超时" in result.stderr
            result.exitCode == 0 && result.stdout.contains("uid=0")
        }.onFailure { LogUtils.w(TAG, "Could not probe for root: $it") }.getOrDefault(false)
        if (timedOut) {
            // Not a refusal: the user may have tapped "allow" after the probe gave up, and caching
            // that as "no root" would keep the privilege unusable for the whole process.
            LogUtils.i(TAG, "The root prompt was not answered in time; asking again on the next use")
            return@withContext false
        }
        rootChecked = true
        rootGranted = granted
        if (!granted) {
            // Which of the three it was matters: no binary at all (never rooted), a binary that
            // refused (the manager said no, or it only trusts shell), or one that could not be
            // executed (SELinux). "No root shell available" alone sends people to the wrong fix.
            LogUtils.i(
                TAG,
                when {
                    binary == null -> "No su binary on this phone; root is not an option here"
                    else -> "su at $binary did not grant root (refused, or not allowed for this uid)"
                },
            )
        }
        granted
    }

    override suspend fun shell(command: String): ShellResult = RootShell.session.run(command)

    /**
     * The helper streams one line per package, and the shared shell reads it as it arrives, so the
     * list fills while the helper is still working.
     */
    override fun shellLines(command: String): Flow<String> = RootShell.session.lines(command)

    override suspend fun push(bytes: ByteArray, remotePath: String, mode: Int) {
        withContext(Dispatchers.IO) {
            val staging = File(cacheDirectory, STAGING_NAME)
            staging.writeBytes(bytes)
            // The remote path is `/data/local/tmp/...`, which only root can write; the app's own
            // cache is where the bytes can be created, and `su` does the move.
            val permissions = Integer.toOctalString(mode and PERMISSION_MASK)
            val quotedStaging = staging.absolutePath.shellQuoted()
            val quotedRemote = remotePath.shellQuoted()
            val result = RootShell.session.run(
                "cp $quotedStaging $quotedRemote && chmod $permissions $quotedRemote && rm -f $quotedStaging"
            )
            if (result.exitCode != 0) {
                LogUtils.e(TAG, "Could not place the helper at $remotePath: ${result.stderr.take(500)}")
            }
        }
    }

    /**
     * Wraps [this] in single quotes for the shell the session keeps open, so a path with a space
     * (or an odd character) stays one word.
     */
    private fun String.shellQuoted(): String = "'" + replace("'", "'\\''") + "'"
}

/**
 * Where a `su` binary usually is when the phone is rooted: AOSP's own, the older xbin, and the paths
 * Magisk and KernelSU use.
 */
private val SU_PATHS = listOf(
    "/system/bin/su",
    "/system/xbin/su",
    "/sbin/su",
    "/su/bin/su",
    "/debug_ramdisk/su",
    // Partition layouts some OEM builds ship their own su in; a ROM that grants root itself keeps
    // it here rather than in /system, and nothing else would ever find it.
    "/vendor/bin/su",
    "/system_ext/bin/su",
    "/product/bin/su",
    "/odm/bin/su",
)

/** The `su` this phone actually has, in the order above and then along `PATH`; null when none. */
fun suBinaryPath(): String? =
    SU_PATHS.firstOrNull { File(it).exists() }
        ?: (System.getenv("PATH") ?: "")
            .split(':')
            .firstNotNullOfOrNull { directory ->
                if (directory.isEmpty()) null else File(directory, "su").takeIf { it.exists() }?.absolutePath
            }

/**
 * Whether this phone carries a root binary at all — not a promise that it will grant one.
 *
 * Checked as files rather than by running `su`: this decides which privilege path the app starts
 * on, at startup, and a probe there would put a root prompt over the splash screen. The prompt
 * belongs on the first real operation, which is where [RootCantaHandler.prepare] asks for it.
 */
fun hasSuBinary(): Boolean = suBinaryPath() != null