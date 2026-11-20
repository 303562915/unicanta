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
        if (isSui || isCantaAuthorized()) {
            return PrivilegeStatus.ACTIVE
        }
        if (!runtimeInstalled(packageManager)) {
            return PrivilegeStatus.NOT_AVAILABLE
        }
        return if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
            PrivilegeStatus.NOT_AUTHORIZED
        } else {
            // Installed but the service isn't up; either manager counts, they are interchangeable.
            PrivilegeStatus.NOT_RUNNING
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
}