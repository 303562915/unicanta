package io.github.samolego.canta.core.dhizuku

import android.os.IBinder
import android.os.Process
import com.rosan.dhizuku.api.Dhizuku
import io.github.samolego.canta.packages.SystemServices
import rikka.shizuku.SystemServiceHelper

/**
 * [SystemServices] through Dhizuku: every transaction is relayed to Dhizuku's server and executed
 * there, so the framework sees *its* uid as the caller.
 *
 * That identity is the whole reason this route can uninstall: the package installer deletes
 * silently when `DevicePolicyManagerInternal.canSilentlyInstallPackage(callerPackage, callingUid)`
 * says the caller is the device owner or an affiliated profile owner, and it verifies that the
 * claimed package belongs to that uid. Both hold only from inside Dhizuku.
 */
object DhizukuServices : SystemServices {

    override fun service(name: String): IBinder = wrap(SystemServiceHelper.getSystemService(name))

    override fun wrap(binder: IBinder): IBinder = Dhizuku.binderWrapper(binder)

    /**
     * The user Canta runs in, not Dhizuku's: Dhizuku is a single system-wide manager, while the
     * list being managed is this phone's current user (or the profile Canta was installed into).
     */
    override val userId: Int
        get() = Process.myUserHandle().hashCode()
}
