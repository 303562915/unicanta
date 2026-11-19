package io.github.samolego.canta.data.community

import io.github.samolego.canta.data.AppBlobStorage
import io.github.samolego.canta.util.DeviceProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The device a cached list belongs to, so a list from another phone is never shown. */
private fun DeviceProfile.cacheKey(): String = listOf(model, device, rom, romVersion).joinToString("|")

@Serializable
private data class PlanCacheBlob(val device: String = "", val plans: List<CommunityPlan> = emptyList())

/**
 * The plans this phone saw last time, kept on the device.
 *
 * The plans page shows them before the network answers, and keeps showing them when the request
 * fails — after a reboot the first load used to leave both tabs empty, which reads like "nothing
 * is there" instead of "could not reach the backend".
 */
class PlanCache(private val storage: AppBlobStorage) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var loaded: PlanCacheBlob? = null

    private suspend fun ensureLoaded(): PlanCacheBlob = mutex.withLock {
        loaded ?: runCatching {
            storage.read()?.decodeToString()?.let { json.decodeFromString(PlanCacheBlob.serializer(), it) }
        }.getOrNull().also { loaded = it ?: PlanCacheBlob() }
    }

    /** The cached plans for [profile]; empty when nothing was cached for this device. */
    suspend fun read(profile: DeviceProfile): List<CommunityPlan> {
        val blob = ensureLoaded()
        return if (blob.device == profile.cacheKey()) blob.plans else emptyList()
    }

    /** Replaces the cache with [plans] for [profile]. */
    suspend fun write(profile: DeviceProfile, plans: List<CommunityPlan>) {
        mutex.withLock {
            val blob = PlanCacheBlob(profile.cacheKey(), plans)
            loaded = blob
            runCatching { storage.write(json.encodeToString(PlanCacheBlob.serializer(), blob).encodeToByteArray()) }
        }
    }
}

/**
 * Drops plans that say the same thing twice: the same uploader and the same package set.
 *
 * Uploading twice (retrying after a load that looked empty) used to leave two identical rows in
 * the list, because the backend stores every submit as a new plan.
 */
fun List<CommunityPlan>.withoutDuplicates(): List<CommunityPlan> {
    val seen = mutableSetOf<String>()
    return filter { plan ->
        seen.add(plan.owner + "#" + plan.packages.sorted().joinToString(","))
    }
}
