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
                    onPermissionResult(grantResult)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(SHIZUKU_CODE)
        }
    }

    private fun checkRequirements(): Boolean {
        return binderStatus && !Shizuku.isPreV11() && !Shizuku.shouldShowRequestPermissionRationale()
    }

    private fun isPermissionGranted(): Boolean {
        return isSui || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun isCantaAuthorized(): Boolean {
        return checkRequirements() && isPermissionGranted()
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