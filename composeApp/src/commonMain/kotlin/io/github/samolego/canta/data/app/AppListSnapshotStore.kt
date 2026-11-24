package io.github.samolego.canta.data.app

import io.github.samolego.canta.data.AppBlobStorage
import io.github.samolego.canta.packages.PackageDetails
import io.github.samolego.canta.util.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 应用列表上一次扫出来的那一份，落在本地文件里。
 *
 * 为什么需要它：`CantaHandler.loadApps()` 要把整台机器的包读一遍（几百个包，还要查每个包的大小、
 * 安装器、版本），冷启动那几秒界面上什么都没有——这正是「进去先空白，然后一次性加载」。
 * 有了这份快照，进列表页的第一帧就是上次那张表，扫描回来的那一版再原地顶上去。
 *
 * 存的是 [PackageDetails] 而不是 `AppInfo`：图标（`ImageBitmap`）落不了盘，徽标（bloat 列表）
 * 有自己的一份缓存，它们由 `load()` 回填。所以这份文件只描述「这台机器上有哪些包、各是什么版本」。
 *
 * 写入是后台的、内容相同就不写：包列表一次刷新往往一个包都没变，那种情况下磁盘一次都不该动。
 */
class AppListSnapshotStore(private val storage: AppBlobStorage) {

    @Serializable
    private data class Blob(
        /** 什么时候扫的，epoch 毫秒；0 表示盘上没有。 */
        val at: Long = 0L,
        val packages: List<PackageDetails> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 盘上那一份，解码一次就留在内存里；`null` 表示还没读过。 */
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

    /** 上一次扫出来的包列表；没扫过就是空（第一次装、刚清过数据）。 */
    fun read(): List<PackageDetails> = blob().packages

    /** 这份快照是什么时候扫的（epoch 毫秒），0 表示盘上没有。 */
    fun readAt(): Long = blob().at

    /**
     * 换一份快照。
     *
     * 内容和盘上那份一样就直接返回：一次扫描常常一个包都没变，那时不该序列化几十 KB 再写一次盘。
     */
    fun write(packages: List<PackageDetails>) {
        if (packages.isEmpty()) return
        val current = blob()
        if (current.packages == packages) return
        val next = Blob(currentTimeMillis(), packages)
        loaded = next
        scope.launch {
            runCatching {
                storage.write(json.encodeToString(Blob.serializer(), next).encodeToByteArray())
            }
        }
    }
}
