package io.github.samolego.canta

import android.content.Context
import io.github.samolego.canta.core.AndroidCantaHandler
import io.github.samolego.canta.core.AndroidCantaPlatform
import io.github.samolego.canta.core.CantaAppDependencies
import io.github.samolego.canta.core.DhizukuCantaHandler
import io.github.samolego.canta.core.PrivilegeChain
import io.github.samolego.canta.core.StoredData
import io.github.samolego.canta.core.cantaAppDependencies
import io.github.samolego.canta.core.dhizuku.DhizukuPrivilege
import io.github.samolego.canta.core.root.RootCantaHandler
import io.github.samolego.canta.core.root.RootTransport
import io.github.samolego.canta.core.root.hasSuBinary
import io.github.samolego.canta.data.FileAppStorage
import io.github.samolego.canta.util.attachLogSink
import java.io.File

/**
 * Builds the [CantaAppDependencies] for the Android target. Call once from the
 * Android application class; the MainActivity attaches/detaches itself to the
 * [platform] for activity-scoped flows (biometrics).
 */
fun provideAndroidDependencies(context: Context, platform: AndroidCantaPlatform): CantaAppDependencies {
    val appContext = context.applicationContext
    // Dhizuku needs the app context before the first status read, which can come from the settings
    // page or from a notification tap, not only from the handler below.
    DhizukuPrivilege.attach(appContext)
    // 更新链路（检查 → 下载 → 安装）的日志另落一份盘：`pm install` 装成功那一刻进程会被换掉，
    // 日志页内存里那些行跟着消失，事后就没法回答「到底哪条通道报了错、最后又是怎么装上的」。
    attachLogSink(File(appContext.filesDir, "update-log.txt").absolutePath)
    // Settings and presets stay where the DataStore-based releases wrote them,
    // so existing data is picked up in place.
    val legacyDataStoreDirectory = File(appContext.filesDir, "datastore")
    return cantaAppDependencies(
        storage = { name ->
            val directory = if (name == StoredData.BLOAT_LIST) appContext.filesDir else legacyDataStoreDirectory
            FileAppStorage(File(directory, name))
        },
        platform = platform,
        platformName = "android",
        handler = {
            // One chain over the privileges this phone can offer, in the order they are worth
            // trying: a `su` that answers (Magisk, KernelSU, APatch, or a build that ships its
            // own) and otherwise the runtime apps. Root is only built when a su binary exists, so
            // an unrooted phone pays one reference and nothing else.
            //
            // Whether root is granted is asked on the first call that needs a device, not here:
            // a probe at startup would put a root prompt over the splash screen, and a refusal
            // there would decide the whole session before the user saw what it was for.
            //
            // The Dhizuku route shares the same read paths as the Shizuku one: it is the same
            // handler object, so the list, the icons and the device facts cannot drift apart.
            val runtime = AndroidCantaHandler(appContext)
            PrivilegeChain(
                // Built even when no `su` file is visible: a manager mounts the binary from its own
                // daemon, so "not there yet" is not the same as "not rooted", and asking is the only
                // way to find out. An unrooted phone gets one failed exec and then a runtime app.
                root = RootCantaHandler(RootTransport(appContext.cacheDir), appContext),
                shizuku = runtime,
                packageManager = appContext.packageManager,
                dhizuku = DhizukuCantaHandler(appContext, runtime),
            )
        },
    )
}
