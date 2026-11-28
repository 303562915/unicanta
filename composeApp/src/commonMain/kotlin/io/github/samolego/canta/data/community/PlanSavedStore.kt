package io.github.samolego.canta.data.community

import io.github.samolego.canta.data.AppBlobStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 「哪些方案我收起来了」——只写本机，不进后端。
 *
 * 与帖子收藏（[PostReadStore]）同一个思路，也是同一个理由：收藏是**这台手机上的这个人**的选择，
 * 不是方案的公共属性；后端加一张表就要同步、要考虑换机、一人多机，而客户端要的只是「收藏页里
 * 能翻到那几条」。所以它同样落在 [AppBlobStorage] 里：同步读一次，写入丢后台。
 *
 * 只存 id 与顺序，不存整份方案：方案会被作者改、会被删，缓存下来的那一份副本会变成一条谁也
 * 点不开的死数据。收藏页拿着 id 逐条去问后端，删掉的方案自然就不出现，报错也只是那一行空着。
 *
 * 上限 [SavedLimit] 条，最近的在前：收藏是「最近留着的那些」，旧的自然淘汰。
 */
class PlanSavedStore(private val storage: AppBlobStorage) {

    /** 盘上的形状：id 列表，最近的在前。 */
    @Serializable
    private data class Blob(val saved: List<Long> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    /** 盘上那一份，解码一次就留在内存里；null 表示还没读过。 */
    private var loaded: Blob? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun blob(): Blob {
        loaded?.let { return it }
        val decoded = runCatching {
            storage.read()?.decodeToString()?.let { json.decodeFromString(Blob.serializer(), it) }
        }.getOrNull() ?: Blob()
        loaded = decoded
        return decoded
    }

    fun isSaved(planId: Long): Boolean = blob().saved.contains(planId)

    /** 收藏里的方案编号，最近的在前。 */
    fun savedIds(): List<Long> = blob().saved

    /** 一共收了几条；收藏页的空态要靠它区分「还没收」和「收了但都还没拉到」。 */
    fun savedCount(): Int = blob().saved.size

    /**
     * 收藏 / 取消收藏，回答改完之后「现在收着没有」。
     *
     * 重复收藏同一个方案不会在列表里占两行（先剔掉旧的再插到最前）。
     */
    fun toggleSaved(planId: Long): Boolean {
        if (planId <= 0L) return false
        val current = blob()
        val nowSaved = if (current.saved.contains(planId)) {
            current.saved.filterNot { it == planId }
        } else {
            (listOf(planId) + current.saved).take(SavedLimit)
        }
        write(current.copy(saved = nowSaved))
        return nowSaved.contains(planId)
    }

    /** 内存里立刻换成新的一份（下一次读取马上看到），盘上的写丢到后台。 */
    private fun write(next: Blob) {
        loaded = next
        scope.launch {
            runCatching {
                storage.write(json.encodeToString(Blob.serializer(), next).encodeToByteArray())
            }
        }
    }
}

/** 收藏留多少条方案：够用，又不会把这一页变成第二个书签管理器。 */
private const val SavedLimit = 200
