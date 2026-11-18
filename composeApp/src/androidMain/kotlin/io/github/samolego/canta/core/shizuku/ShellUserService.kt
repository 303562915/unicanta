package io.github.samolego.canta.core.shizuku

import android.content.Context
import android.os.Binder
import android.os.Parcel
import android.util.Log

/**
 * The privileged half of [ShizukuShell].
 *
 * Shizuku starts this class in a process of its own, with the identity of its server (shell for
 * the ADB backend, root for Sui/Magisk), which is what lets Canta write into another package's
 * `Android/data` or run `appops` — things the app itself is not allowed to do. Every transaction
 * runs one `sh -c` script and returns its exit code plus everything it printed.
 *
 * It is a raw [Binder] rather than an AIDL stub: the protocol is one string in, one
 * (exit code, output) pair out, so an AIDL file and its build step would buy nothing. Shizuku
 * wants the service class itself to be the binder, hence no [android.app.Service] here.
 */
class ShellUserService(private val context: Context?) : Binder() {

    /** Shizuku before v13 has no Context to hand over; it uses this constructor instead. */
    constructor() : this(null)

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == TRANSACTION_DESTROY) {
            // Shizuku calls this when the service is replaced or the app is gone; everything this
            // process exists for lives in the caller, so exiting is the whole cleanup.
            System.exit(0)
            return true
        }
        if (code != TRANSACTION_RUN) return super.onTransact(code, data, reply, flags)

        data.enforceInterface(DESCRIPTOR)
        val script = data.readString().orEmpty()
        val (exitCode, output) = execute(script)
        reply?.apply {
            writeNoException()
            writeInt(exitCode)
            writeString(output)
        }
        Log.i(TAG, "Ran a script: exit $exitCode, ${output.length} chars back")
        return true
    }

    /** Runs [script] and returns its exit code plus stdout and stderr, interleaved. */
    private fun execute(script: String): Pair<Int, String> = try {
        val builder = ProcessBuilder(SHELL, "-c", script).redirectErrorStream(true)
        // A user service is started by Shizuku, outside any app's environment: without a PATH the
        // scripts would not find `pm`, `cmd` or `appops`.
        builder.environment()["PATH"] = PATH
        val process = builder.start()
        // Drained before waiting, so a chatty script cannot fill the pipe and stall itself.
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() to output
    } catch (e: Exception) {
        Log.w(TAG, "Could not run a command: $e")
        127 to "canta: could not run the command: $e"
    }

    companion object {
        private const val TAG = "ShellUserService"

        /** Interface name every transaction is tagged with. */
        const val DESCRIPTOR = "io.github.samolego.canta.SHELL"

        /** Runs the string in the parcel and replies with (exit code, output). */
        const val TRANSACTION_RUN = 1

        /** Shizuku's cleanup call, see the docs of [ShellUserService]. */
        const val TRANSACTION_DESTROY = 16777115

        private const val SHELL = "/system/bin/sh"
        private const val PATH = "/system/bin:/system/xbin:/vendor/bin"
    }
}
