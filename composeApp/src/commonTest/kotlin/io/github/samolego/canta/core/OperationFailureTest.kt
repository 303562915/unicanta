package io.github.samolego.canta.core

import io.github.samolego.canta.packages.OperationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperationFailureTest {

    private fun failed(status: Int, message: String = "") =
        OperationResult(packageName = "com.example.app", success = false, status = status, message = message)

    @Test
    fun successHasNoExplanation() {
        assertNull(OperationResult("com.example.app", success = true).explainFailure())
    }

    @Test
    fun everyExplanationSaysWhatToDo() {
        val codes = listOf(
            OperationResult.STATUS_NO_RESULT, -1, 1, 2, 3, 4, 5, 6, 7, 8, 999,
        )
        codes.forEach { status ->
            val explanation = assertNotNull(
                failed(status).explainFailure(),
                "status $status has no explanation",
            )
            assertTrue(explanation.reason.isNotBlank(), "status $status has no reason")
            assertTrue(explanation.advice.isNotBlank(), "status $status has no advice")
        }
    }

    @Test
    fun theDeviceMessageOutranksAGenericStatus() {
        // A vendor block arrives as STATUS_FAILURE; the message is what says so.
        val text = assertNotNull(failed(1, "not allowed to disable this package").explainFailure())
        assertTrue(
            "厂商" in text.reason || "锁住" in text.reason,
            "a vendor block should not read as a generic failure: ${text.reason}",
        )
    }

    @Test
    fun aDeviceAdministratorIsNamed() {
        val explanation = assertNotNull(
            failed(1, "DELETE_FAILED_DEVICE_POLICY_MANAGER").explainFailure()
        )
        assertTrue("设备管理" in explanation.reason, explanation.reason)
        assertTrue("设备管理" in explanation.advice, explanation.advice)
    }

    @Test
    fun anUnreachableHelperIsNotBlamedOnThePackage() {
        val explanation = assertNotNull(
            failed(OperationResult.STATUS_NO_RESULT, "Helper unavailable").explainFailure()
        )
        assertTrue("助手" in explanation.reason, explanation.reason)
    }

    @Test
    fun aMissingResultStillSaysWhatToDo() {
        val explanation = assertNotNull(failed(OperationResult.STATUS_NO_RESULT).explainFailure())
        assertTrue(explanation.advice.isNotBlank())
    }

    @Test
    fun anUnknownCodeStillSaysSomething() {
        val explanation = assertNotNull(failed(4242).explainFailure())
        assertTrue("4242" in explanation.reason, explanation.reason)
    }

    @Test
    fun theMessageIsMatchedWithoutCase() {
        assertNotNull(failed(1, "Permission Denial: not allowed").explainFailure())
        assertEquals(
            failed(1, "permission denial").explainFailure()?.reason,
            failed(1, "PERMISSION DENIAL").explainFailure()?.reason,
        )
    }
}