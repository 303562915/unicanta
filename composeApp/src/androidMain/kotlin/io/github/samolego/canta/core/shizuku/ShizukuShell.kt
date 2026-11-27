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
class ShizukuShell private constructor(private val context: Context) {

    /**
     * 常量留在 companion 里，但**这个 companion 本身是公开的**：[shared] 要被
     * `AndroidCantaHandler` 与 `AndroidCantaPlatform` 调用，companion 写成 private 就等于把它藏起来
     * （编译期直接报 `Cannot access 'companion object'`）。所以只有成员是 private，容器不 private。
     */
    companion object {
        private const val TAG = "ShizukuShell"

        /** Bumped whenever [ShellUserService] changes protocol, so Shizuku starts the new code. */
        private const val SERVICE_VERSION = 1

        /** How long the service may take to come up before the card reports a timeout. */
        private const val BIND_TIMEOUT_MS = 10_000L

        /**
         * 绑几次。实测的形状是：Shizuku 的用户服务第一次常常起不来（进程被杀、管理器刚启动），
         * 隔一秒再绑一次就好了。只绑一次的话这一次安装就白等，然后退到系统安装器。
         *
         * 3 轮：多出的那一轮是给「权限被拒 → 重新申请授权」留的。管理器弹出确认框、用户点允许、
         * 服务端重建 ClientRecord 需要时间，2 轮会在用户刚点上之前就放弃。
         */
        private const val BIND_ROUNDS = 3

        /** 两次绑定之间等多久；先 unbind 再 bind，否则 Shizuku 会当成同一次未完成的请求。 */
        private const val BIND_RETRY_DELAY_MS = 1_000L

        /** 重新申请 Shizuku 授权时用的请求码，与 `ShizukuPermission` 里那个不冲突即可。 */
        private const val PERMISSION_REQUEST_CODE = 0xCA07B

        /**
         * 弹出授权确认后给用户多久去点。
         *
         * 不给这段等待就是白弹一次框：下一轮 `bindUserService` 会在用户点之前发出，服务端仍然
         * 认为 Canta 没授权，这次的安装就以"还是没授权"收场。
         */
        private const val PERMISSION_PROMPT_WAIT_MS = 6_000L

        /**
         * 一次脚本调用最多等多久。`binder.transact` 是阻塞调用、没有自己的超时：Shizuku 那边
         * 卡住（用户服务死了但 binder 还在）时它会一直不返回，界面就永远停在「正在安装 90%」。
         * 所以这条等待必须由我们自己放弃。
         */
        private const val RUN_TIMEOUT_MS = 30_000L

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
        val remote = binder ?: run {
            // Shizuku 根本没在运行（没启动 / 用户关掉了）：连 `bindUserService` 都不必试。
            //
            // 这一步以前没有，代价是"关了 Shizuku 还在被要授权"：绑定会抛一句
            // `requires permission`（服务端任何拒绝都是这个文案，它分不出"没运行"和"没授权"），
            // 于是每跑一条通道就弹一次授权框，弹几次都不可能好。
            // 现在先行判断，运行时不在了就直接把结论交回去，由上层走系统安装器兜底。
            if (!runtimeAlive()) {
                lastBindError = "Shizuku 没在运行"
                lastBindDenied = null
                return TweakRun(ok = false, output = lastBindError, failure = TweakFailure.NoPrivilege)
            }
            bind()
        }
        if (remote == null) {
            // 把「为什么没绑上」原样带出去：这句话会进安装失败的 detail / update_reports，
            // 下次不用再让人读屏幕，报告本身就写着是权限、版本还是进程起不来。
            val alive = runtimeAlive()
            // 权限被拒时不能说成 Timeout（"没有响应"）：那是「再等等它自己就好」，而这一种
            // 只有用户去管理器里给 Canta 授权才能好。归成 NoPrivilege，上层才会走
            // 「Canta 还没被 Shizuku 授权」那句，也才会记得试 root 通道。
            val denied = isPermissionDenial(lastBindDenied)
            return TweakRun(
                ok = false,
                output = lastBindError.ifBlank { shizukuState() },
                failure = when {
                    denied -> TweakFailure.NoPrivilege
                    alive -> TweakFailure.Timeout
                    else -> TweakFailure.NoPrivilege
                },
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

    /** 上一次绑定失败是不是「Shizuku 说 Canta 没被授权」那个异常。 */
    @Volatile
    private var lastBindDenied: Throwable? = null

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
                    val why = started.exceptionOrNull()
                    lastBindDenied = if (isPermissionDenial(why)) why else null
                    lastBindError = if (isPermissionDenial(why)) {
                        // 不许说成"没有响应"：这句话会进 toast 和 update_reports.detail，而它描述的
                        // 是完全不同的动作（去管理器里给 Canta 授权），说错方向用户永远修不好。
                        "Canta 还没被 Shizuku 授权（${why?.message ?: why}）"
                    } else {
                        "bindUserService 失败：${why?.message ?: why}"
                    }
                    LogUtils.w(TAG, "Could not start the shell service: $why")
                    // 「requires permission」不是"服务起不来"，是 Shizuku 认为 Canta 没被授权：
                    // 服务端 `enforceCallingPermission("addUserService")` 查到 `clientRecord.allowed=false`
                    // 就抛这句。这个 allowed 只在 attachApplication 那一刻从配置里读一次，Canta 后来
                    // 在管理器里获得授权，也不会自动同步到已经 attach 的 ClientRecord 上 —— 所以必须
                    // 主动重新走一次授权确认，让服务端重建这个 ClientRecord，而不是傻等重试。
                    if (isPermissionDenial(why)) {
                        // 只有在 Shizuku **真的在运行**时才值得弹授权框。
                        //
                        // 用户把 Shizuku 关掉之后，`bindUserService` 抛的也是这句 `requires
                        // permission`（服务端侧的任何拒绝都是同一个 SecurityException 文案），
                        // 以前这里照样去 `requestPermission` —— 于是"明明关了 Shizuku，
                        // Canta 还一直要授权"，弹多少遍都不可能成功。运行时不在了就该安静地
                        // 把失败交回上层，由上层走系统安装器兜底。
                        val aliveNow = runtimeAlive()
                        runCatching { ShizukuPermission.noteServerDenied() }
                        if (aliveNow) {
                            // 弹框之后不能立刻重绑：用户还在看管理器的确认框，这一刻服务端仍然
                            // 认为 Canta 没授权。给它一段真的等待时间，用户点完"允许"再重试才有意义。
                            requestPermissionAgain()
                            delay(PERMISSION_PROMPT_WAIT_MS)
                            if (!runtimeAlive()) return@withLock null
                        }
                    }
                    continue
                }
                val bound = withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }
                if (bound != null) {
                    binder = bound
                    lastBindError = ""
                    lastBindDenied = null
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

    /**
     * 「Shizuku 说 Canta 没被授权」的那句异常。
     *
     * 服务端 `enforceCallingPermission("addUserService")` 在 `clientRecord.allowed == false` 时抛
     * `SecurityException("Permission Denial: addUserService from pid=… requires permission")`。
     * 这与「用户没开 Shizuku」「管理器版本太老」是不同的两件事，处理方式也不同：这一种只需要
     * 在管理器里把 Canta 的授权重新确认一次。
     */
    private fun isPermissionDenial(e: Throwable?): Boolean {
        if (e == null) return false
        val text = (e.message ?: e.toString()).lowercase()
        return "requires permission" in text || "permission denial" in text
    }

    /**
     * 重新走一次授权确认，逼 Shizuku 重建这个 ClientRecord。
     *
     * `clientRecord.allowed` 是 `attachApplication` 那一刻从配置里读的快照，之后在管理器里点
     * 允许**不会**同步到已 attach 的 ClientRecord 上；而 API 的 `checkSelfPermission()` 又会缓存
     * 第一次拿到的 GRANTED。于是界面上写着"已授权"，`addUserService` 却一直被拒 —— 线上报告里
     * `ping=true` 却 `requires permission` 正是这个形状。这里先清缓存再申请一次，让服务端把
     * `allowed` 重新算出来；申请走的是同一个回调，用户看到的是管理器弹出的一次确认框。
     */
    private suspend fun requestPermissionAgain() {
        // `requestPermission` 会弹管理器界面，必须在主线程；这里在 IO 线程的 bind() 里。
        withContext(Dispatchers.Main) {
            runCatching {
                // API 说已授权而服务端说没有：正是快照过期，必须再申请一次。
                val granted = runCatching {
                    Shizuku.checkSelfPermission()
                }.getOrDefault(android.content.pm.PackageManager.PERMISSION_DENIED)
                if (granted == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    LogUtils.w(TAG, "Shizuku 说已授权但 addUserService 被拒，重新申请授权")
                }
                // 这个请求码上的结果要有人接：否则用户点了"允许"，Canta 这边的"被拒"标记
                // 永远不会清掉，界面会一直显示未授权，下一次还是要重新申请。
                val listener = object : Shizuku.OnRequestPermissionResultListener {
                    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                        if (requestCode != PERMISSION_REQUEST_CODE) return
                        Shizuku.removeRequestPermissionResultListener(this)
                        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            ShizukuPermission.clearDenial()
                        }
                    }
                }
                Shizuku.addRequestPermissionResultListener(listener)
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }.onFailure { LogUtils.w(TAG, "Re-requesting the Shizuku permission failed: $it") }
        }
    }

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
