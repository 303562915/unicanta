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
class ShizukuShell private constructor(private val context: Context) {

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

        /**
         * 全进程一个实例。
         *
         * 为什么必须是这一个：Shizuku 的用户服务按「应用 + tag」认，而每个 [ShizukuShell] 都带着
         * 自己的 [ServiceConnection]。以前 `AndroidCantaHandler`（应用列表、卸载、工具页）和
         * `AndroidCantaPlatform`（自更新安装）各 `lazy { ShizukuShell(context) }` 一个，等于同一份
         * 用户服务上挂了两个连接：第二条连接拿不到服务，10 秒后就是我们看到的那句
         * 「Shizuku 没有响应」。线上 `NOT_READY` 的报告里三条通道同时"没有响应"，正是这个形状 ——
         * 同一个进程里明明已经能跑 `pm`（应用列表和卸载都靠它），自更新那一路却偏偏绑不上。
         * 一个实例、一条连接、一个 binder，就没有这件事。
         */
        @Volatile
        private var instance: ShizukuShell? = null

        fun shared(context: Context): ShizukuShell =
            instance ?: synchronized(this) {
                instance ?: ShizukuShell(context.applicationContext).also { instance = it }
            }
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
            // 把「为什么没绑上」原样带出去：这句话会进安装失败的 detail / update_reports，
            // 下次不用再让人读屏幕，报告本身就写着是权限、版本还是进程起不来。
            val alive = runtimeAlive()
            return TweakRun(
                ok = false,
                output = lastBindError.ifBlank { shizukuState() },
                failure = if (alive) TweakFailure.Timeout else TweakFailure.NoPrivilege,
            )
        }
        // 在单独的 scope 上等：超时之后这次调用就此作废，不会再挂着界面。
        val call = callScope.async { transact(remote, script) }
        val answer = withTimeoutOrNull(timeoutMs) { call.await() }
        if (answer == null) {
            // 这次没答上来：丢掉这个 binder，下一次重新绑（绑不上的话就是我们自己给的那句
            // 「没有响应」，而不是一条永远转不完的进度）。
            binder = null
            lastBindError = "${timeoutMs / 1000} 秒内没有回复"
            LogUtils.w(TAG, "The shell service did not answer within ${timeoutMs}ms")
            return TweakRun(ok = false, output = lastBindError, failure = TweakFailure.Timeout)
        }
        return answer
    }

    /**
     * Shizuku 现在长什么样，一句话：版本、uid、有没有 binder。
     *
     * 只在失败时读：`getUid()` 在没授权时会抛，所以整段包在 runCatching 里；这段文字的唯一用途是
     * 让「没有响应」不再是一句没有信息的话。
     */
    private fun shizukuState(): String = runCatching {
        "Shizuku uid=${Shizuku.getUid()} ping=${Shizuku.pingBinder()} preV11=${Shizuku.isPreV11()} " +
            "manager=${runCatching { context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0).versionName }.getOrNull() ?: "?"}"
    }.getOrElse { "读不到 Shizuku 状态：${it.message ?: it}" }

    /** 上一次绑定为什么没成，给 [run] 的原话；空串表示还没失败过。 */
    @Volatile
    private var lastBindError: String = ""

    /** Starts [ShellUserService] if it is not running yet; null when it cannot be started. */
    private suspend fun bind(): IBinder? = bindLock.withLock {
        binder?.let { return@withLock it }
        if (!runtimeAlive()) {
            lastBindError = ""
            return@withLock null
        }

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
                val started = runCatching { Shizuku.bindUserService(args, connection) }
                if (started.isFailure) {
                    lastBindError = "bindUserService 失败：${started.exceptionOrNull()?.message ?: started.exceptionOrNull()}"
                    LogUtils.w(TAG, "Could not start the shell service: ${started.exceptionOrNull()}")
                    continue
                }
                val bound = withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }
                if (bound != null) {
                    binder = bound
                    lastBindError = ""
                    return@withLock bound
                }
                lastBindError = "用户服务 ${BIND_TIMEOUT_MS / 1000} 秒内没连上（第 ${round + 1} 轮）"
                LogUtils.w(TAG, "The shell service did not connect (round ${round + 1}/$BIND_ROUNDS)")
            } finally {
                pending = null
            }
        }
        // 两轮都没连上：把「哪一步卡住」和「这台机器上的 Shizuku 长什么样」一起带出去，
        // 下一次的报告就能直接判是版本、是权限，还是用户服务进程起不来。
        lastBindError = listOf(lastBindError.ifBlank { "用户服务没连上" }, shizukuState()).joinToString("；")
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
