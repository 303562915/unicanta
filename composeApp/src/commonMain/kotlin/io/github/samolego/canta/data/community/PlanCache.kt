package io.github.samolego.canta.data.community

import io.github.samolego.canta.data.AppBlobStorage
import io.github.samolego.canta.util.DeviceProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The device a cached list belongs to, so a list from another phone is never shown. The build is
 * part of the key: after "16.0.3" turns into "16.0.501" the cached rows are another build's plans,
 * and the page has to load the new build's instead of painting the old ones as this phone's.
 */
private fun DeviceProfile.cacheKey(): String =
    listOf(model, device, rom, romVersion, romBuild).joinToString("|")

/**
 * The key the same phone's list was written under before the build was part of it (four parts).
 * Those blobs are still this phone's own list — the key exists to keep *another* phone's rows out,
 * not to throw the user's last list away on upgrade — so [PlanCache.read] accepts it as well.
 */
private fun DeviceProfile.legacyCacheKey(): String =
    listOf(model, device, rom, romVersion).joinToString("|")

/**
 * 两份列表存在同一个 blob 里，各占一个字段：
 *
 * * [all]：「全部机型」那一份，也就是默认范围——不跟 `device` 走，它本来就不属于某台手机。
 * * [plans] + [device]：本机那一份，带机型键，换手机就查不到。
 *
 * 两个字段分开是为了让后写的不要顺手把先写的那份清掉：以前整个 blob 只有本机一份，`write` 直接
 * 整份替换，加了「全部机型」之后那样写会把另一份抹掉。
 */
@Serializable
private data class PlanCacheBlob(
    val device: String = "",
    val plans: List<CommunityPlan> = emptyList(),
    val all: List<CommunityPlan> = emptyList(),
)

/**
 * The plans this phone saw last time, kept on the device — both lists the page has.
 *
 * 排行页和方案页读的是同一份数据（排行就是把方案里的包按点赞聚合），所以缓存只有这一处：
 * 存的是方案行，两个页签都从它先画。
 *
 * 用的方式是「先画缓存、再后台刷新」：进来先把上一份铺上，请求回来真的拿到了更新就整份换掉并写回；
 * 请求没到就保持屏幕上那一份，不清空。清空等于因为一次断网把整页排行抹掉，看起来像「社区里没东西」，
 * 而不是「后端没通」。
 *
 * 他机（另一个机型）的列表**从不入缓存**：缓存属于本机这一份和全机型那一份，把别人机型的行当成
 * 「你的」画出来是错的信息。
 */
class PlanCache(private val storage: AppBlobStorage) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var loaded: PlanCacheBlob? = null

    private suspend fun ensureLoaded(): PlanCacheBlob = mutex.withLock {
        loaded?.let { return@withLock it }
        val blob = runCatching {
            storage.read()?.decodeToString()?.let { json.decodeFromString(PlanCacheBlob.serializer(), it) }
        }.getOrNull() ?: PlanCacheBlob()
        loaded = blob
        blob
    }

    /** The cached plans for [profile]; empty when nothing was cached for this device. */
    suspend fun read(profile: DeviceProfile): List<CommunityPlan> {
        val blob = ensureLoaded()
        val stored = blob.device
        return when {
            stored == profile.cacheKey() -> blob.plans
            // Written before the build was part of the key: still this phone's own list.
            stored == profile.legacyCacheKey() -> blob.plans
            else -> emptyList()
        }
    }

    /** Replaces this phone's cached plans with [plans], leaving the widest scope's copy alone. */
    suspend fun write(profile: DeviceProfile, plans: List<CommunityPlan>) {
        update { it.copy(device = profile.cacheKey(), plans = plans) }
    }

    /** The cached "every model" list — the default scope the page opens with; empty when never written. */
    suspend fun readAll(): List<CommunityPlan> = ensureLoaded().all

    /** Replaces the "every model" copy with [plans], leaving this phone's list alone. */
    suspend fun writeAll(plans: List<CommunityPlan>) {
        update { it.copy(all = plans) }
    }

    /**
     * 读一份、改一个字段、整份写回。
     *
     * `ensureLoaded` 自己也要拿锁，所以先在锁外读；锁只包住「写回」这一段，并在锁内重新取一次
     * `loaded`——两个写并发时，后到的要基于前一个的结果改，不能拿锁外的旧值把对方那份覆盖掉。
     */
    private suspend fun update(transform: (PlanCacheBlob) -> PlanCacheBlob) {
        val base = ensureLoaded()
        mutex.withLock {
            val blob = transform(loaded ?: base)
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
