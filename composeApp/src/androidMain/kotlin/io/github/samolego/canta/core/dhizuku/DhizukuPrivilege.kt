package io.github.samolego.canta.core.dhizuku

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import com.rosan.dhizuku.api.DhizukuUserServiceArgs
import io.github.samolego.canta.core.PrivilegeStatus
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The third privilege Canta can run on: [Dhizuku](https://github.com/iamr0s/Dhizuku), which shares
 * the DeviceOwner role instead of a shell.
 *
 * It is not a shell, but two of the three writes still work, through different doors:
 *
 *  - **uninstall** goes through the package installer, which deletes silently when
 *    `DevicePolicyManagerInternal.canSilentlyInstallPackage(callerPackage, callingUid)` says the
 *    caller is the device owner or an affiliated profile owner
 *    ([`PackageInstallerService.uninstall`](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/pm/PackageInstallerService.java)).
 *    The framework also checks that the claimed package belongs to the calling uid, so the call has
 *    to run as Dhizuku and claim Dhizuku — see [DhizukuServices];
 *  - **freeze / thaw** goes through `DevicePolicyManager.setPackagesSuspended`, which needs the
 *    owner role from inside the owner's own process — see [CantaDhizukuService];
 *  - **reinstall** does not work: `installExistingPackageAsUser` asks for `INSTALL_PACKAGES` and has
 *    no owner carve-out, so that half stays with Shizuku or root.
 *
 * Everything the device has to do happens inside [CantaDhizukuService], which Dhizuku instantiates
 * in *its own* process: only from there is the caller the device owner, and only from there can
 * `DevicePolicyManager` be called for a package Canta does not own. The binder protocol is hand
 * rolled for the same reason [io.github.samolego.canta.core.shizuku.ShizukuShell] is: no AIDL
 * generation step, one small transaction per call.
 */
object DhizukuPrivilege {

    private const val TAG = "DhizukuPrivilege"

    /** The official manager. Forks register a different owner, and `Dhizuku.init` finds it. */
    const val PACKAGE_NAME = "com.rosan.dhizuku"

    /** Where the manager is published; there is no Play Store listing for it. */
    const val DOWNLOAD_URL = "https://github.com/iamr0s/Dhizuku/releases"

    /** How long Dhizuku may take to start our user service before the call reports a failure. */
    private const val BIND_TIMEOUT_MS = 10_000L

    /** Upper bound on the permission prompt: the user may simply walk away from it. */
    private const val REQUEST_TIMEOUT_MS = 20_000L

    @Volatile
    private var appContext: Context? = null

    /** Whether `Dhizuku.init` has ever handed us its binder; false until the first attempt. */
    @Volatile
    private var connected = false

    /** Set from the application class, before any page asks for the privilege state. */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Asks Dhizuku for its binder. False when it is not installed, not activated (its own server is
     * not running) or the provider refused us — all three mean "this route is not usable now".
     */
    fun connect(): Boolean {
        val context = appContext ?: return false
        val ok = runCatching { Dhizuku.init(context) }
            .onFailure { LogUtils.w(TAG, "Dhizuku.init failed: $it") }
            .getOrDefault(false)
        connected = ok
        return ok
    }

    /** Whether Canta may use the device owner right now: binder received *and* permission granted. */
    fun isActive(): Boolean {
        if (appContext == null) return false
        // A server that went away throws rather than answering: treat that as "no binder" so the
        // status page says "not running" instead of leaving the last answer standing.
        val granted = runCatching {
            if (!connected && !connect()) return false
            Dhizuku.isPermissionGranted()
        }.onFailure {
            LogUtils.w(TAG, "Dhizuku did not answer: $it")
            connected = false
        }.getOrDefault(false)
        return granted
    }

    /** Whether the manager app is on the phone at all; the forks keep their own package name. */
    fun installed(packageManager: PackageManager): Boolean = runCatching {
        packageManager.getPackageInfo(PACKAGE_NAME, 0)
        true
    }.getOrDefault(false)

    /**
     * The package that holds the owner role — a fork ships under its own name — or null when the
     * binder is not there. The privileged installer has to claim exactly this package: the
     * framework rejects a claim that does not belong to the calling uid.
     */
    fun ownerPackage(): String? = runCatching {
        if (!connected && !connect()) return null
        Dhizuku.getOwnerPackageName()
    }.onFailure { LogUtils.w(TAG, "Dhizuku did not name its owner package: $it") }.getOrNull()

    fun status(packageManager: PackageManager): PrivilegeStatus = when {
        isActive() -> PrivilegeStatus.ACTIVE
        !installed(packageManager) -> PrivilegeStatus.NOT_AVAILABLE
        // Installed and its server answered, but Canta is not on its allow list yet.
        connected -> PrivilegeStatus.NOT_AUTHORIZED
        // Installed, server not up (not activated, or the app was force-stopped).
        else -> PrivilegeStatus.NOT_RUNNING
    }

    /**
     * Raises Dhizuku's own approval screen and reports the answer. Must run on the main thread for
     * the activity launch; the waiting happens there too, so this returns once the user decided or
     * the manager stayed silent.
     */
    suspend fun requestPermission(): Boolean {
        if (appContext == null) return false
        if (!connect()) return false
        if (isActive()) return true
        val answer = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) {
            runCatching {
                // `DhizukuRequestPermissionListener` is an abstract class extending the AIDL Stub,
                // not an interface: it needs the constructor call.
                Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                    override fun onRequestPermission(grantResult: Int) {
                        answer.complete(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                })
            }.onFailure {
                LogUtils.w(TAG, "The permission prompt could not be shown: $it")
                answer.complete(false)
            }
        }
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) { answer.await() } ?: isActive()
    }

    // ---- the device owner half, run inside Dhizuku's process --------------------------------

    /**
     * The component Dhizuku is asked to start. A component name is only a key here: Dhizuku loads
     * the class from our APK by that name, and the package has to be ours or Dhizuku refuses the
     * bind. Built per call rather than cached, so a bind that happens before [attach] is not
     * poisoned by a null.
     */
    private fun serviceArgs(): DhizukuUserServiceArgs? = appContext?.let { context ->
        DhizukuUserServiceArgs(ComponentName(context.packageName, CantaDhizukuService::class.java.name))
    }

    /** One bind at a time: two taps in a row would otherwise start the service twice. */
    private val bindLock = Mutex()

    @Volatile
    private var service: IBinder? = null

    /** Completed by [connection] once the service is up; set while a bind is in flight. */
    @Volatile
    private var pending: CompletableDeferred<IBinder?>? = null

    // Dhizuku posts both callbacks on the main looper, and `complete` may be called from there.
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder
            pending?.complete(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    /** Freezes ([suspended] = true) or thaws one package. False when Dhizuku refused or is gone. */
    suspend fun setSuspended(packageName: String, suspended: Boolean): Boolean {
        val binder = service ?: bindService() ?: return false
        if (!binder.isBinderAlive) {
            service = null
            return false
        }
        return withContext(Dispatchers.IO) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(CantaDhizukuService.DESCRIPTOR)
                data.writeString(packageName)
                data.writeInt(if (suspended) 1 else 0)
                if (!binder.transact(CantaDhizukuService.TRANSACTION_SET_SUSPENDED, data, reply, 0)) {
                    false
                } else {
                    reply.readException()
                    reply.readInt() == 1
                }
            } catch (e: Exception) {
                LogUtils.w(TAG, "The Dhizuku service failed: $e")
                if (!binder.isBinderAlive) service = null
                false
            } finally {
                reply.recycle()
                data.recycle()
            }
        }
    }

    /**
     * Which of [packageNames] the device owner currently keeps paused. Canta cannot tell on its
     * own: a suspended package is filtered out of the ordinary `PackageManager` view, and its
     * `ApplicationInfo` carries no flag for it, so the answer comes from the process that owns the
     * policy.
     */
    suspend fun suspendedAmong(packageNames: List<String>): Set<String> {
        if (packageNames.isEmpty()) return emptySet()
        val binder = service ?: bindService() ?: return emptySet()
        if (!binder.isBinderAlive) {
            service = null
            return emptySet()
        }
        return withContext(Dispatchers.IO) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(CantaDhizukuService.DESCRIPTOR)
                data.writeStringArray(packageNames.toTypedArray())
                if (!binder.transact(CantaDhizukuService.TRANSACTION_SUSPENDED_AMONG, data, reply, 0)) {
                    emptySet()
                } else {
                    reply.readException()
                    reply.createStringArray()?.toSet().orEmpty()
                }
            } catch (e: Exception) {
                LogUtils.w(TAG, "The Dhizuku service did not answer the pause list: $e")
                if (!binder.isBinderAlive) service = null
                emptySet()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }
    }

    /** Starts [CantaDhizukuService] inside Dhizuku if it is not running yet; null when it cannot. */
    private suspend fun bindService(): IBinder? = bindLock.withLock {
        service?.let { return@withLock it }
        val args = serviceArgs() ?: return@withLock null
        if (!isActive()) return@withLock null

        val answer = CompletableDeferred<IBinder?>()
        pending = answer
        try {
            val started = runCatching { Dhizuku.bindUserService(args, connection) }
                .onFailure { LogUtils.w(TAG, "Could not start the Dhizuku service: $it") }
                .getOrDefault(false)
            if (!started) return@withLock null
            withTimeoutOrNull(BIND_TIMEOUT_MS) { answer.await() }?.also { service = it }
        } finally {
            pending = null
        }
    }
}

/**
 * Lives inside Dhizuku's process, not Canta's: Dhizuku instantiates this class by name from our
 * APK and every call therefore runs with the device owner's identity. That is the only place where
 * `DevicePolicyManager` accepts a policy call for a package Canta does not own.
 *
 * The class keeps to framework types on purpose — it is loaded into a foreign process, so it must
 * not drag Canta's own classes in with it.
 *
 * Dhizuku's own lifecycle transactions (`FIRST_CALL_TRANSACTION + 1` and `+ 2`) are left to
 * [Binder]; the protocol here starts far enough above them not to collide.
 */
class CantaDhizukuService(private val context: Context) : Binder() {

    companion object {
        const val DESCRIPTOR = "io.github.samolego.canta.core.dhizuku.CANTA"

        /** Pause or resume one package: `(packageName: String, suspended: Int)` → `Int`. */
        const val TRANSACTION_SET_SUSPENDED = IBinder.FIRST_CALL_TRANSACTION + 10

        /** Which of the given packages are paused: `String[]` in → `String[]` out. */
        const val TRANSACTION_SUSPENDED_AMONG = IBinder.FIRST_CALL_TRANSACTION + 11

        private const val TAG = "CantaDhizukuService"
    }

    /**
     * Dhizuku writes this descriptor into the lifecycle transactions it sends us, and rejects a
     * service whose descriptor is null: without it the class would never be started at all.
     */
    override fun getInterfaceDescriptor(): String = DESCRIPTOR

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            TRANSACTION_SET_SUSPENDED -> {
                data.enforceInterface(DESCRIPTOR)
                val packageName = data.readString().orEmpty()
                val suspended = data.readInt() != 0
                val ok = setSuspended(packageName, suspended)
                reply?.writeNoException()
                reply?.writeInt(if (ok) 1 else 0)
                return true
            }

            TRANSACTION_SUSPENDED_AMONG -> {
                data.enforceInterface(DESCRIPTOR)
                val packages = data.createStringArray()?.toList().orEmpty()
                val paused = suspendedAmong(packages)
                reply?.writeNoException()
                reply?.writeStringArray(paused.toTypedArray())
                return true
            }
        }
        return super.onTransact(code, data, reply, flags)
    }

    /**
     * Pauses or resumes [packageName] through the device owner. Everything is guarded: an
     * exception here would take Dhizuku down with it, and Dhizuku is holding the phone's owner
     * role, not just this call.
     */
    private fun setSuspended(packageName: String, suspended: Boolean): Boolean = runCatching {
        if (packageName.isEmpty()) return@runCatching false
        // Suspending a package by policy exists from Android 7; below that there is no such lever.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return@runCatching false
        val manager = manager() ?: return@runCatching false
        val admin = ownerAdmin(manager) ?: return@runCatching false
        val refused = manager.setPackagesSuspended(admin, arrayOf(packageName), suspended)
        refused == null || packageName !in refused
    }.onFailure { Log.w(TAG, "setPackagesSuspended($packageName, $suspended) failed", it) }
        .getOrDefault(false)

    private fun suspendedAmong(packages: List<String>): List<String> = runCatching {
        if (packages.isEmpty()) return@runCatching emptyList()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return@runCatching emptyList()
        val manager = manager() ?: return@runCatching emptyList()
        val admin = ownerAdmin(manager) ?: return@runCatching emptyList()
        packages.filter { packageName ->
            runCatching { manager.isPackageSuspended(admin, packageName) }.getOrDefault(false)
        }
    }.onFailure { Log.w(TAG, "Reading the paused packages failed", it) }
        .getOrDefault(emptyList())

    private fun manager(): DevicePolicyManager? =
        runCatching { context.getSystemService(DevicePolicyManager::class.java) }.getOrNull()

    /**
     * The administrator that holds the owner role. Read from the policy manager rather than
     * hardcoded: Dhizuku can be device owner or profile owner, and a fork ships its own receiver.
     */
    private fun ownerAdmin(manager: DevicePolicyManager): ComponentName? =
        runCatching {
            manager.activeAdmins?.firstOrNull { admin ->
                manager.isDeviceOwnerApp(admin.packageName) || manager.isProfileOwnerApp(admin.packageName)
            }
        }.getOrNull()
}
