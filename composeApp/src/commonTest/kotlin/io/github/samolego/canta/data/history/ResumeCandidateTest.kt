package io.github.samolego.canta.data.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResumeCandidateTest {

    private fun record(pkg: String, batchId: Long = 1000L, index: Int = 1) = OperationRecord(
        timestamp = batchId + index,
        kind = OperationKind.UNINSTALL,
        packageName = pkg,
        label = pkg,
        success = true,
        batchId = batchId,
        batchIndex = index,
        batchSize = 10,
    )

    @Test
    fun theAppsThatNeverGotAnAnswerAreLeft() {
        val records = listOf(record("com.a", index = 1), record("com.b", index = 2))

        val resume = resumeCandidate(
            interrupted = listOf("com.a", "com.b", "com.c", "com.d"),
            startedAt = 1000L,
            records = records,
        )

        assertEquals(listOf("com.c", "com.d"), resume?.packages)
        assertEquals(2, resume?.size)
    }

    @Test
    fun aRefusalIsAnAnswerTooAndIsNotRetried() {
        val refused = OperationRecord(
            timestamp = 1001,
            kind = OperationKind.UNINSTALL,
            packageName = "com.a",
            label = "com.a",
            success = false,
            batchId = 1000L,
            batchIndex = 1,
            batchSize = 2,
        )

        val resume = resumeCandidate(listOf("com.a", "com.b"), 1000L, listOf(refused))

        assertEquals(listOf("com.b"), resume?.packages, "asking again would only refuse again")
    }

    @Test
    fun linesFromAnotherBatchDontCount() {
        val other = listOf(record("com.a", batchId = 999L))

        val resume = resumeCandidate(listOf("com.a"), 1000L, other)

        assertEquals(listOf("com.a"), resume?.packages, "a different batch answered nothing")
    }

    @Test
    fun aFinishedBatchLeavesNothingToResume() {
        val records = listOf(
            record("com.a", index = 1),
            record("com.b", index = 2),
        )

        assertNull(resumeCandidate(listOf("com.a", "com.b"), 1000L, records))
    }

    @Test
    fun nothingStoredMeansNothingToResume() {
        assertNull(resumeCandidate(emptyList(), 1000L, emptyList()))
        assertNull(resumeCandidate(listOf("com.a"), 0L, emptyList()), "no start time, no batch")
    }

    @Test
    fun aBatchWithNoKindIsAnUninstall() {
        // The migration rule for data stored before batches carried a kind: those versions only
        // ever remembered uninstall batches, so that is what an untyped entry means.
        val resume = resumeCandidate(listOf("com.a", "com.b"), 1000L, emptyList())

        assertEquals(OperationKind.UNINSTALL, resume?.kind)
        assertEquals(OperationKind.UNINSTALL, resume?.finishKind)
    }

    @Test
    fun aFreezeBatchResumesAsAFreeze() {
        val resume = resumeCandidate(
            interrupted = listOf("com.a", "com.b"),
            startedAt = 1000L,
            records = emptyList(),
            kind = OperationKind.DISABLE,
        )

        assertEquals(OperationKind.DISABLE, resume?.kind, "resuming a freeze must not delete the app")
        assertEquals(OperationKind.DISABLE, resume?.finishKind)
        assertEquals(2, resume?.size)
    }

    @Test
    fun aThawBatchResumesAsAThaw() {
        val resume = resumeCandidate(listOf("com.a"), 1000L, emptyList(), OperationKind.ENABLE)

        assertEquals(OperationKind.ENABLE, resume?.finishKind)
    }

    @Test
    fun aBatchThatIsNeverRememberedIsNotResumed() {
        // Putting apps back is not remembered while it runs: a stored one is stale data, and
        // "continue restoring" a day later is a prompt nobody can act on.
        assertNull(resumeCandidate(listOf("com.a"), 1000L, emptyList(), OperationKind.REINSTALL))
    }

    @Test
    fun onlyUninstallFreezeAndThawAreRemembered() {
        assertEquals(true, OperationKind.UNINSTALL.resumable)
        assertEquals(true, OperationKind.DISABLE.resumable)
        assertEquals(true, OperationKind.ENABLE.resumable)
        assertEquals(false, OperationKind.REINSTALL.resumable)
    }

    @Test
    fun anAnsweredFreezeBatchIsFinished() {
        val disabled = OperationRecord(
            timestamp = 1001,
            kind = OperationKind.DISABLE,
            packageName = "com.a",
            label = "com.a",
            success = true,
            batchId = 1000L,
            batchIndex = 1,
            batchSize = 2,
        )

        assertNull(resumeCandidate(listOf("com.a"), 1000L, listOf(disabled), OperationKind.DISABLE))
    }
}