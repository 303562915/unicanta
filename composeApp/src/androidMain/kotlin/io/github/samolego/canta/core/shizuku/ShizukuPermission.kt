package io.github.samolego.canta.core.shizuku

import android.content.pm.PackageManager
import io.github.samolego.canta.core.PrivilegeStatus
import io.github.samolego.canta.util.LogUtils
import rikka.shizuku.Shizuku
import rikka.sui.Sui

object ShizukuPermission {
    private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"

    /**
     * Stellar is a Shizuku fork with a built-in compatibility layer: it feeds the same binder to
     * [rikka.shizuku.ShizukuProvider] under the same API, so an app cannot tell the two apart. Only
     * the manager package name differs, and that is what tells "not installed" from "installed but
     * not running" here.
     */
    private const val STELLAR_PACKAGE_NAME = "roro.stellar.manager"
    private const val SHIZUKU_CODE = 0xCA07A

    private val isSui: Boolean = Sui.init("io.github.samolego.canta")
    private val TAG: String = ShizukuPermission::class.java.simpleName
    // Written from Shizuku's binder threads.
    @Volatile
    private var binderStatus = Shizuku.pingBinder()

    /**
     * 本进程里服务端曾经说过「Canta 没被授权」。
     *
     * `checkSelfPermission()` 的返回值会被 API 缓存成常量（拿到一次 GRANTED 就永远返回 GRANTED），
     * 而服务端判权限用的是 attach 时的快照。设置这个标记后 [privilegeStatus] 不再信那个缓存，
     * 界面会老实显示"未授权"，[requestShizukuPermission] 也会真的去申请。
     */
    @Volatile
    private var lastDenialSeen = false

    init {
        Shizuku.addBinderDeadListener { binderStatus = false }
        Shizuku.addBinderReceivedListener { binderStatus = true }
    }

    /**
     * Sui hands Canta its binder directly instead of running a manager's own service, so `pingBinder`
     * is not what tells whether the runtime is there on that setup: [isSui] is.
     */
    val isSuiRuntime: Boolean get() = isSui

    /**
     * Requests the Shizuku permission, reporting the result to
     * [onPermissionResult] (immediately if already decided). Call from the
     * main thread only!
     */
    fun requestShizukuPermission(onPermissionResult: (Int) -> Unit) {
        if (!checkRequirements()) {
            LogUtils.i(
                TAG,
                "Shizuku permission result: ping: ${Shizuku.pingBinder()}, preV11: ${Shizuku.isPreV11()}"
            )
            onPermissionResult(PackageManager.PERMISSION_DENIED)
        } else if (isPermissionGranted()) {
            LogUtils.i(
                TAG,
                "Shizuku permission result: ${Shizuku.checkSelfPermission()}, sui status: $isSui"
            )
            onPermissionResult(PackageManager.PERMISSION_GRANTED)
        } else {
            LogUtils.i(TAG, "Requesting shizuku permission")
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != SHIZUKU_CODE) return
                    // One-shot: otherwise listeners pile up and old callbacks re-fire.
                    Shizuku.removeRequestPermissionResultListener(this)
                    // 用户这次真的点了允许：清掉"被拒"标记，让状态回到最新，否则会一直显示未授权。
                    if (grantResult == PackageManager.PERMISSION_GRANTED) clearDenial()
                    onPermissionResult(grantResult)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(SHIZUKU_CODE)
        }
    }

    /**
     * 运行时能答话吗（binder 在、且不是 pre-v11）。
     *
     * **这里不再看 `shouldShowRequestPermissionRationale()`**。那个方法回答的是「授权对话框该不该
     * 先解释一句」，被当成「不可用」是错的：真机上出现过 rationale 返回 true（用户上次点了"以后"、
     * 或管理器把它记成了未决定），于是 [isCantaAuthorized] 直接 false —— 界面说"没授权"，
     * 而服务端那边 Canta 其实 attach 着、只是 `clientRecord.allowed=false`。
     * 这条误判会连累 `requestShizukuPermission` 根本不发请求，用户点多少次授权都没反应。
     */
    private fun checkRequirements(): Boolean {
        return binderStatus && !Shizuku.isPreV11()
    }

    private fun isPermissionGranted(): Boolean {
        return isSui || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun isCantaAuthorized(): Boolean {
        // 被服务端拒过就不再信 API 那个缓存值（见 [lastDenialSeen]）：这种状态下说"已授权"会让
        // 用户以为什么都好，实际每一步调用都失败。
        if (lastDenialSeen && !isSui) return false
        return checkRequirements() && isPermissionGranted()
    }

    /** 授权确认走完之后，清掉"被拒"标记，让界面按最新状态显示。 */
    fun clearDenial() {
        lastDenialSeen = false
    }

    fun privilegeStatus(packageManager: PackageManager): PrivilegeStatus {
        // 熟人的 fork（Stellar / Sui）先判：它们复用同一个 binder，系统里查不到"正在运行"以外的
        // 证据。
        if (isSui || isCantaAuthorized()) {
            return PrivilegeStatus.ACTIVE
        }
        // 然后是 binder 本身：**谁是管理器不重要**。群里提的 AXManager、改过包名的分支，走的都是
        // 同一套 API；只要 binder 在，这台机器上就有能用的运行时，只差授权。
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
            return PrivilegeStatus.NOT_AUTHORIZED
        }
        // 「装没装」不能只看两个包名（原 [runtimeInstalled] 的做法）：包名一栏为空不代表没装。
        // binder 不在时判不出「装了没起」还是「根本没装」，就给一句两端都成立的话。
        return if (runtimeInstalled(packageManager)) {
            PrivilegeStatus.NOT_RUNNING
        } else {
            PrivilegeStatus.NOT_AVAILABLE
        }
    }

    /**
     * 记录一次「服务端把 Canta 的调用按未授权拒了」。
     *
     * 由 [ShizukuShell] 在 `addUserService` 抛 `requires permission` 时调用。标记只在本进程有效，
     * 进程重启后会用最新状态重新判 —— 那正是我们想要的，因为重启后 ClientRecord 也重建了。
     */
    fun noteServerDenied() {
        lastDenialSeen = true
    }

    /** 授权确认走完之后，清掉"被拒"标记，让界面按最新状态显示。 */
    fun clearDenial() {
        lastDenialSeen = false
    }

    /** Whether either manager is on this phone: the original, or a fork that speaks its API. */
    private fun runtimeInstalled(packageManager: PackageManager): Boolean =
        listOf(SHIZUKU_PACKAGE_NAME, STELLAR_PACKAGE_NAME).any { name ->
            try {
                packageManager.getPackageInfo(name, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

    /**
     * Why Shizuku could not answer, as a sentence naming the one thing to fix.
     *
     * 「没在运行，或没授权给 Canta」把两种完全不同的处理方式（去开服务 / 去点授权）塞进同一句
     * 话里，用户照着做也没法确定该做哪一步；这里分出四档，每一档只留一个动作。
     */
    fun refusalText(packageManager: PackageManager): String = when (privilegeStatus(packageManager)) {
        PrivilegeStatus.ACTIVE -> "Shizuku 在运行，但这次调用没答上来"
        PrivilegeStatus.NOT_AUTHORIZED -> "Canta 还没被 Shizuku 授权"
        PrivilegeStatus.NOT_RUNNING -> "Shizuku 没在运行"
        PrivilegeStatus.NOT_AVAILABLE -> "没找到 Shizuku 服务：装一个 Shizuku / Stellar，或用同样 API 的管理器（如 AXManager）"
    }
}