package io.github.samolego.canta.data.community

import io.github.samolego.canta.testing.MemoryStorage
import io.github.samolego.canta.util.DeviceProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 缓存里的两份列表：「本机」和「全部机型」。
 *
 * 这两份存在同一个文件里，而页面会按当前范围各写各的——所以真正要守住的是「写一份不弄丢另一份」，
 * 以及换手机之后不要把上一台的那份当成本机的画出来。
 */
class PlanCacheScopeTest {

    private val phone = DeviceProfile(
        brand = "Redmi",
        model = "23113RKC6C",
        device = "fuxi",
        sdk = 34,
        release = "14",
        rom = "hyperos",
        romVersion = 1,
        fingerprint = "fp",
        marketName = "Redmi K70",
    )

    private fun plan(id: Long, pkg: String = "com.a") = CommunityPlan(
        id = id, title = "t$id", note = "", packages = listOf(pkg), votes = 0,
        comments = 0, createdAt = "", rebootedAt = "",
    )

    @Test
    fun theTwoScopesDoNotOverwriteEachOther() = runTest {
        val storage = MemoryStorage()
        val cache = PlanCache(storage)
        cache.write(phone, listOf(plan(1)))
        cache.writeAll(listOf(plan(2), plan(3)))

        // A second instance over the same bytes is what the next launch of the app is.
        val reopened = PlanCache(storage)
        assertEquals(listOf(1L), reopened.read(phone).map { it.id })
        assertEquals(listOf(2L, 3L), reopened.readAll().map { it.id })

        // And the other way round: writing this phone's list must not wipe the widest scope's.
        reopened.write(phone, listOf(plan(4)))
        assertEquals(listOf(4L), reopened.read(phone).map { it.id })
        assertEquals(listOf(2L, 3L), reopened.readAll().map { it.id })
    }

    @Test
    fun anotherPhonesListIsNeverShownAsThisOnes() = runTest {
        val storage = MemoryStorage()
        PlanCache(storage).write(phone, listOf(plan(1)))

        val afterOsUpgrade = phone.copy(romVersion = 2)
        assertTrue(PlanCache(storage).read(afterOsUpgrade).isEmpty(), "another build is another list")
        // The widest scope is nobody's build, so it survives the same change.
        PlanCache(storage).writeAll(listOf(plan(2)))
        assertEquals(listOf(2L), PlanCache(storage).readAll().map { it.id })
    }

    @Test
    fun aBlobFromBeforeTheWidestScopeExistedStillReads() = runTest {
        val legacy = """
            {"device":"23113RKC6C|fuxi|hyperos|1","plans":[{"id":7,"title":"t7","note":"",
            "packages":["com.a"],"votes":0,"comments":0,"createdAt":"","rebootedAt":""}]}
        """.trimIndent().toByteArray()
        val cache = PlanCache(MemoryStorage(legacy))

        assertEquals(listOf(7L), cache.read(phone).map { it.id })
        assertTrue(cache.readAll().isEmpty(), "that version had no widest scope to read")
    }

    @Test
    fun nothingWrittenReadsAsNothing() = runTest {
        val cache = PlanCache(MemoryStorage())
        assertTrue(cache.read(phone).isEmpty())
        assertTrue(cache.readAll().isEmpty())
    }
}
