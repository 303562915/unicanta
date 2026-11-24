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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

        /**
         * 绑几次。实测的形状是：Shizuku 的用户服务第一次常常起不来（进程被杀、管理器刚启动），
         * 隔一秒再绑一次就好了。只绑一次的话这一次安装就白等，然后退到系统安装器。
         */
        const val BIND_ROUNDS = 2

        /** 两次绑定之间等多久；先 unbind 再 bind，否则 Shizuku 会当成同一次未完成的请求。 */
        const val BIND_RETRY_DELAY_MS = 1_000L

        /**
         * 一次脚本调用最多等多久。`binder.transact` 是阻塞调用、没有自己的超时：Shizuku 那边
         * 卡住（用户服务死了但 binder 还在）时它会一直不返回，界面就永远停在「正在安装 90%」。
         * 所以这条等待必须由我们自己放弃。
         */
        const val RUN_TIMEOUT_MS = 30_000L
    }

    /**
     * 放着那些可能永远不返回的 `transact`。
     *
     * 单独一个 scope、而不是调用方的 scope：超时之后我们要丢掉这次调用继续往下走，而结构化并发
     * 的 scope 会等所有子协程结束——那正好是这里唯一不能做的事。
     */
    private val callScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    /**
     * Runs [script] and reports what it printed, or why it never ran.
     *
     * [timeoutMs] is a ceiling on the *answer*, not on the command: `pm install` of a 30 MB APK on a
     * busy phone legitimately takes a minute, while a shell whose user service died never answers at
     * all. Callers that run something long pass their own number; everything else keeps the default.
     */
    suspend fun run(script: String, timeoutMs: Long = RUN_TIMEOUT_MS): TweakRun {
        val remote = binder ?: bind()
        if (remote == null) {
            return TweakRun(
                ok = false,
                failure = if (runtimeAlive()) TweakFailure.Timeout else TweakFailure.NoPrivilege,
            )
        }
        // 在单独的 scope 上等：超时之后这次调用就此作废，不会再挂着界面。
        val call = callScope.async { transact(remote, script) }
        val answer = withTimeoutOrNull(timeoutMs) { call.await() }
        if (answer == null) {
            // 这次没答上来：丢掉这个 binder，下一次重新绑（绑不上的话就是我们自己给的那句
            // 「没有响应」，而不是一条永远转不完的进度）。
            binder = null
            LogUtils.w(TAG, "The shell service did not answer within ${timeoutMs}ms")
            return TweakRun(ok = false, failure = TweakFailure.Timeout)
        }
        return answer
    }

    /** Starts [ShellUserService] if it is not running yet; null when it cannot be started. */
    private suspend fun bind(): IBinder? = bindLock.withLock {
        binder?.let { return@withLock it }
        if (!runtimeAlive()) return@withLock null

        for (round in 0 until BIND_ROUNDS) {
            if (round > 0) {
                // 上一轮没连上：先把那次绑定收回去再重来，Shizuku 不会为同一个用户服务重发连接。
                // 第三个参数是 Shizuku 的 `remove`：为 true 时连用户服务实例一起丢掉，下一轮
                // `bindUserService` 才会真的重建一次（否则拿回来的还是那个半死不活的 binder）。
                runCatching { Shizuku.unbindUserService(args, connection, true) }
                    .onFailure { LogUtils.w(TAG, "Could not drop the shell service: $it") }
                delay(BIND_RETRY_DELAY_MS)
                if (!runtimeAlive()) return@withLock null
            }
            val deferred = CompletableDeferred<IBinder?>()
            pending = deferred
            try {
                val requested = runCatching { Shizuku.bindUserService(args, connection) }
                    .onFailure { LogUtils.w(TAG, "Could not start the shell service: $it") }
                    .isSuccess
                if (!requested) continue
                val bound = withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }
                if (bound != null) {
                    binder = bound
                    return@withLock bound
                }
                LogUtils.w(TAG, "The shell service did not connect (round ${round + 1}/$BIND_ROUNDS)")
            } finally {
                pending = null
            }
        }
        null
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
