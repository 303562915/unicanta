package io.github.samolego.canta.data.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BatchRollbackTest {

    private fun record(
        pkg: String,
        kind: OperationKind = OperationKind.UNINSTALL,
        batchId: Long = 1L,
        batchIndex: Int = 1,
        success: Boolean = true,
    ) = OperationRecord(
        timestamp = batchId * 1000 + batchIndex,
        kind = kind,
        packageName = pkg,
        label = pkg,
        success = success,
        batchId = batchId,
        batchIndex = batchIndex,
        batchSize = 2,
    )

    private fun gone(vararg packages: String): (String) -> Boolean {
        val set = packages.toSet()
        return { it in set }
    }

    private fun frozen(vararg packages: String): (String) -> Boolean {
        val set = packages.toSet()
        return { it in set }
    }

    @Test
    fun aBatchWhoseAppsAreStillGoneIsOffered() {
        val records = listOf(
            record("com.a", batchIndex = 1),
            record("com.b", batchIndex = 2),
        )

        val rollback = records.rollbackCandidate(gone("com.a", "com.b"))

        assertEquals(1L, rollback?.batchId)
        assertEquals(listOf("com.b", "com.a"), rollback?.packages, "the reverse of the order it was removed in")
    }

    @Test
    fun appsAlreadyPutBackAreLeftOut() {
        val records = listOf(
            record("com.a", batchIndex = 1),
            record("com.b", batchIndex = 2),
            record("com.c", batchIndex = 3),
        )

        val rollback = records.rollbackCandidate(gone("com.a", "com.c"))

        assertEquals(listOf("com.c", "com.a"), rollback?.packages)
    }

    @Test
    fun oneAppLeftIsNotABatch() {
        val records = listOf(
            record("com.a", batchIndex = 1),
            record("com.b", batchIndex = 2),
        )

        assertNull(records.rollbackCandidate(gone("com.a")), "a single row is undone by tapping it")
    }

    @Test
    fun aRestoreBatchIsNotRolledBackAsIfItWereAnUninstall() {
        val records = listOf(
            record("com.a", kind = OperationKind.REINSTALL, batchId = 2, batchIndex = 1),
            record("com.b", kind = OperationKind.REINSTALL, batchId = 2, batchIndex = 2),
        )

        assertNull(records.rollbackCandidate(gone("com.a", "com.b")))
    }

    @Test
    fun theNewestUninstallBatchWins() {
        val records = listOf(
            record("com.c", batchId = 5, batchIndex = 1),
            record("com.d", batchId = 5, batchIndex = 2),
            record("com.a", batchId = 4, batchIndex = 1),
            record("com.b", batchId = 4, batchIndex = 2),
        )

        val rollback = records.rollbackCandidate(gone("com.a", "com.b", "com.c", "com.d"))

        assertEquals(5L, rollback?.batchId)
    }

    @Test
    fun failuresAreNotRolledBack() {
        val records = listOf(
            record("com.a", batchIndex = 1),
            record("com.b", batchIndex = 2, success = false),
        )

        assertNull(records.rollbackCandidate(gone("com.a", "com.b")))
    }

    @Test
    fun anEmptyLogOffersNothing() {
        assertNull(emptyList<OperationRecord>().rollbackCandidate(gone()))
    }

    @Test
    fun aFreezeBatchIsOfferedWithTheThawThatUndoesIt() {
        val records = listOf(
            record("com.a", kind = OperationKind.DISABLE, batchId = 7, batchIndex = 1),
            record("com.b", kind = OperationKind.DISABLE, batchId = 7, batchIndex = 2),
        )

        val rollback = records.rollbackCandidate(gone(), frozen("com.a", "com.b"))

        assertEquals(OperationKind.DISABLE, rollback?.kind, "a freeze batch is undone by thawing")
        assertEquals(OperationKind.ENABLE, rollback?.undoKind, "the batch is not reinstalled")
        assertEquals(listOf("com.b", "com.a"), rollback?.packages, "thawed back the way it was frozen")
    }

    @Test
    fun anAppThawedByHandIsNotPartOfTheUndo() {
        val records = listOf(
            record("com.a", kind = OperationKind.DISABLE, batchId = 7, batchIndex = 1),
            record("com.b", kind = OperationKind.DISABLE, batchId = 7, batchIndex = 2),
            record("com.c", kind = OperationKind.DISABLE, batchId = 7, batchIndex = 3),
        )

        val rollback = records.rollbackCandidate(gone(), frozen("com.a", "com.c"))

        assertEquals(listOf("com.c", "com.a"), rollback?.packages, "com.b is already unfrozen")
    }

    @Test
    fun aFreezeBatchWhoseAppsAreNoLongerFrozenIsNotOffered() {
        val records = listOf(
            record("com.a", kind = OperationKind.DISABLE, batchId = 7, batchIndex = 1),
            record("com.b", kind = OperationKind.DISABLE, batchId = 7, batchIndex = 2),
        )

        assertNull(records.rollbackCandidate(gone(), frozen("com.a")), "a single row is undone by tapping it")
    }

    @Test
    fun aFreezeBatchIsNotJudgedByWhetherItsAppsAreGone() {
        val records = listOf(
            record("com.a", kind = OperationKind.DISABLE, batchId = 7, batchIndex = 1),
            record("com.b", kind = OperationKind.DISABLE, batchId = 7, batchIndex = 2),
        )

        // Both packages are still installed: that is the normal state of a frozen app, and it must
        // not be what a freeze batch is measured against.
        assertNull(records.rollbackCandidate(gone("com.a", "com.b")))
        assertEquals(
            OperationKind.DISABLE,
            records.rollbackCandidate(gone("com.a", "com.b"), frozen("com.a", "com.b"))?.kind,
        )
    }

    @Test
    fun aThawBatchIsNotRolledBack() {
        val records = listOf(
            record("com.a", kind = OperationKind.ENABLE, batchId = 8, batchIndex = 1),
            record("com.b", kind = OperationKind.ENABLE, batchId = 8, batchIndex = 2),
        )

        assertNull(records.rollbackCandidate(gone(), frozen("com.a", "com.b")))
    }

    @Test
    fun theNewestBatchWinsAcrossKinds() {
        val records = listOf(
            record("com.c", kind = OperationKind.DISABLE, batchId = 9, batchIndex = 1),
            record("com.d", kind = OperationKind.DISABLE, batchId = 9, batchIndex = 2),
            record("com.a", kind = OperationKind.UNINSTALL, batchId = 8, batchIndex = 1),
            record("com.b", kind = OperationKind.UNINSTALL, batchId = 8, batchIndex = 2),
        )

        val rollback = records.rollbackCandidate(gone("com.a", "com.b"), frozen("com.c", "com.d"))

        assertEquals(9L, rollback?.batchId, "the freeze batch happened last")
        assertEquals(OperationKind.DISABLE, rollback?.kind)
    }

    @Test
    fun aMixedBatchIsNotUndoneAsIfItWereOneOperation() {
        val records = listOf(
            record("com.a", kind = OperationKind.UNINSTALL, batchId = 3, batchIndex = 1),
            record("com.b", kind = OperationKind.DISABLE, batchId = 3, batchIndex = 2),
        )

        assertNull(records.rollbackCandidate(gone("com.a"), frozen("com.b")))
    }
}