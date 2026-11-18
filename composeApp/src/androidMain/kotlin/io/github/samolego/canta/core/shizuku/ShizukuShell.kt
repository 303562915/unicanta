package io.github.samolego.canta.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import io.github.samolego.canta.core.TweakFailure
import io.github.samolego.canta.core.TweakRun
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Runs shell scripts as Shizuku's identity, through [ShellUserService].
 *
 * Shizuku's own `newProcess` is private since 13.1.1 and slated for removal, so the supported way
 * to run a command is a user service: Shizuku starts [ShellUserService] in a separate process and
 * every call is one transaction on its binder. The service is not a daemon, so it goes away with
 * Canta instead of outliving it.
 */
class ShizukuShell(private val context: Context) {

    private companion object {
        const val TAG = "ShizukuShell"

        /** Bumped whenever [ShellUserService] changes protocol, so Shizuku starts the new code. */
        const val SERVICE_VERSION = 1

        /** How long the service may take to come up before the card reports a timeout. */
        const val BIND_TIMEOUT_MS = 10_000L
    }

    private val args: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellUserService::class.java.name))
            .daemon(false)
            .version(SERVICE_VERSION)
            .processNameSuffix("shell")
            .tag(TAG)
    }

    /** One bind at a time: two taps in a row would otherwise start the service twice. */
    private val bindLock = Mutex()

    @Volatile
    private var binder: IBinder? = null

    /** Completed by [connection] once the service is up; set while a bind is in flight. */
    @Volatile
    private var pending: CompletableDeferred<IBinder?>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service
            pending?.complete(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
        }
    }

    /** Runs [script] and reports what it printed, or why it never ran. */
    suspend fun run(script: String): TweakRun {
        val remote = binder ?: bind()
        if (remote == null) {
            return TweakRun(
                ok = false,
                failure = if (runtimeAlive()) TweakFailure.Timeout else TweakFailure.NoPrivilege,
            )
        }
        return withContext(Dispatchers.IO) { transact(remote, script) }
    }

    /** Starts [ShellUserService] if it is not running yet; null when it cannot be started. */
    private suspend fun bind(): IBinder? = bindLock.withLock {
        binder?.let { return@withLock it }
        if (!runtimeAlive()) return@withLock null

        val deferred = CompletableDeferred<IBinder?>()
        pending = deferred
        try {
            runCatching { Shizuku.bindUserService(args, connection) }
                .onFailure { LogUtils.w(TAG, "Could not start the shell service: $it") }
                .getOrNull() ?: return@withLock null
            withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }?.also { binder = it }
        } finally {
            pending = null
        }
    }

    /** Whether Shizuku's binder is there and Canta is allowed to use it. */
    private fun runtimeAlive(): Boolean =
        runCatching { Shizuku.pingBinder() && Shizuku.getUid() != -1 }.getOrDefault(false)

    private fun transact(remote: IBinder, script: String): TweakRun {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(ShellUserService.DESCRIPTOR)
            data.writeString(script)
            if (!remote.transact(ShellUserService.TRANSACTION_RUN, data, reply, 0)) {
                TweakRun(ok = false, failure = TweakFailure.Broken)
            } else {
                reply.readException()
                val exitCode = reply.readInt()
                val output = reply.readString().orEmpty()
                TweakRun(ok = exitCode == 0, output = output)
            }
        } catch (e: Exception) {
            LogUtils.w(TAG, "The shell service failed: $e")
            if (!remote.isBinderAlive) binder = null
            TweakRun(ok = false, failure = TweakFailure.Broken)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
