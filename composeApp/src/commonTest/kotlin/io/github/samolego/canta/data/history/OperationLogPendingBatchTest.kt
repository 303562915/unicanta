package io.github.samolego.canta.data.history

import io.github.samolego.canta.testing.FakePlatform
import io.github.samolego.canta.testing.MemoryStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The batch that never finished is kept beside the log, with what it was doing: a freeze resumed as
 * an uninstall would delete apps the user only meant to pause.
 */
class OperationLogPendingBatchTest {

    private fun store(storage: MemoryStorage) = OperationLogStore(storage, FakePlatform())

    private fun record(pkg: String, kind: OperationKind = OperationKind.DISABLE, batchId: Long = 1000L) =
        OperationRecord(
            timestamp = batchId + 1,
            kind = kind,
            packageName = pkg,
            label = pkg,
            success = true,
            batchId = batchId,
            batchIndex = 1,
            batchSize = 2,
        )

    @Test
    fun nothingIsPendingUntilABatchStarts() = runTest {
        assertNull(store(MemoryStorage()).pendingBatch())
    }

    @Test
    fun theKindOfARunningBatchSurvivesAReopen() = runTest {
        val storage = MemoryStorage()
        store(storage).startPendingBatch(listOf("com.a", "com.b"), 1000L, OperationKind.DISABLE)

        // Another launch of the app reads the same storage.
        val reopened = assertNotNull(store(storage).pendingBatch())

        assertEquals(OperationKind.DISABLE, reopened.kind, "a freeze batch is not resumed as an uninstall")
        assertEquals(listOf("com.a", "com.b"), reopened.packages)
        assertEquals(1000L, reopened.startedAt)
    }

    @Test
    fun aThawBatchKeepsItsOwnKind() = runTest {
        val storage = MemoryStorage()
        store(storage).startPendingBatch(listOf("com.a"), 2000L, OperationKind.ENABLE)

        assertEquals(OperationKind.ENABLE, store(storage).pendingBatch()?.kind)
    }

    @Test
    fun aFinishedBatchLeavesNothingRemembered() = runTest {
        val storage = MemoryStorage()
        val log = store(storage)
        log.startPendingBatch(listOf("com.a", "com.b"), 1000L, OperationKind.DISABLE)

        log.clearPendingBatch()

        assertNull(log.pendingBatch(), "every app was answered, so there is nothing to resume")
        assertNull(store(storage).pendingBatch(), "and the next start does not ask either")
    }

    @Test
    fun writingTheLogDoesNotDropTheRememberedBatch() = runTest {
        val storage = MemoryStorage()
        val log = store(storage)
        log.startPendingBatch(listOf("com.a", "com.b"), 1000L, OperationKind.DISABLE)

        log.record(listOf(record("com.a")))

        val pending = assertNotNull(log.pendingBatch(), "an answer is not the end of the batch")
        assertEquals(listOf("com.a", "com.b"), pending.packages)
        assertEquals(OperationKind.DISABLE, pending.kind)
    }

    @Test
    fun clearingTheLogForgetsTheBatchToo() = runTest {
        val storage = MemoryStorage()
        val log = store(storage)
        log.startPendingBatch(listOf("com.a", "com.b"), 1000L, OperationKind.DISABLE)

        log.clear()

        // Without the records there is no way to tell which apps were answered, so the batch would
        // be redone from the first one: it goes with the log it is worked out against.
        assertNull(log.pendingBatch())
    }

    @Test
    fun aBlobFromBeforeTheBatchWasKeptDecodesAsNothingPending() = runTest {
        // No `pending` field: exactly what versions before this one wrote.
        val legacy = """
            {"records":[{"timestamp":10,"kind":"UNINSTALL","packageName":"com.a","label":"com.a",
            "success":true,"batchId":5,"batchIndex":1,"batchSize":1}]}
        """.trimIndent()
        val log = store(MemoryStorage(legacy.encodeToByteArray()))

        assertNull(log.pendingBatch(), "old data has no stored batch; the settings copy is used instead")
        assertEquals(listOf("com.a"), log.records.value.map { it.packageName }, "the log itself still reads")
    }

    @Test
    fun anUnknownKindReadsAsAnUninstall() = runTest {
        // A kind written by a build that knows more operations than this one: the batch is still
        // there, and an uninstall is what data without a kind has always meant.
        val blob = """
            {"records":[],"pending":{"packages":["com.a","com.b"],"startedAt":5,"kind":"VAPORIZE"}}
        """.trimIndent()
        val pending = assertNotNull(store(MemoryStorage(blob.encodeToByteArray())).pendingBatch())

        assertEquals(OperationKind.UNINSTALL, pending.kind)
        assertEquals(listOf("com.a", "com.b"), pending.packages)
    }
}