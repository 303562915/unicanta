package io.github.samolego.canta

import android.content.Context
import io.github.samolego.canta.core.AndroidCantaHandler
import io.github.samolego.canta.core.AndroidCantaPlatform
import io.github.samolego.canta.core.CantaAppDependencies
import io.github.samolego.canta.core.PrivilegeChain
import io.github.samolego.canta.core.StoredData
import io.github.samolego.canta.core.cantaAppDependencies
import io.github.samolego.canta.core.root.RootCantaHandler
import io.github.samolego.canta.core.root.RootTransport
import io.github.samolego.canta.core.root.hasSuBinary
import io.github.samolego.canta.data.FileAppStorage
import java.io.File

/**
 * Builds the [CantaAppDependencies] for the Android target. Call once from the
 * Android application class; the MainActivity attaches/detaches itself to the
 * [platform] for activity-scoped flows (biometrics).
 */
fun provideAndroidDependencies(context: Context, platform: AndroidCantaPlatform): CantaAppDependencies {
    val appContext = context.applicationContext
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
            // One chain over both privileges, in the order they are worth trying: a `su` that
            // answers (Magisk, KernelSU, APatch, or a build that ships its own) and otherwise
            // Stellar/Shizuku. Root is only built when a su binary exists, so an unrooted phone
            // pays one reference and nothing else.
            //
            // Whether root is granted is asked on the first call that needs a device, not here:
            // a probe at startup would put a root prompt over the splash screen, and a refusal
            // there would decide the whole session before the user saw what it was for.
            PrivilegeChain(
                root = if (hasSuBinary()) {
                    RootCantaHandler(RootTransport(appContext.cacheDir), appContext)
                } else {
                    null
                },
                shizuku = AndroidCantaHandler(appContext),
            )
        },
    )
}
