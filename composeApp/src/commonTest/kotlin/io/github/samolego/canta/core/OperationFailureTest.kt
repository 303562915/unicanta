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
    fun aNullPackageInstallerIsNamedAsSuch() {
        // 33 of the first 58 reports from the field: the package service hands back no installer,
        // which is not the device "staying silent" either.
        val explanation = assertNotNull(
            failed(
                OperationResult.STATUS_NO_RESULT,
                "java.lang.NullPointerException: Attempt to invoke interface method " +
                    "'android.os.IBinder android.content.pm.IPackageInstaller.asBinder()' " +
                    "on a null object reference",
            ).explainFailure()
        )
        assertTrue("包安装器" in explanation.reason, explanation.reason)
        assertTrue("刷新" !in explanation.advice, explanation.advice)
    }

    @Test
    fun aDeadShizukuBinderSaysToStartTheRuntime() {
        // What a phone reports when Shizuku was killed or restarted under the app: nothing reached
        // the device, so "refresh the list and see if it worked" would be wrong advice.
        val explanation = assertNotNull(
            failed(
                OperationResult.STATUS_NO_RESULT,
                "java.lang.IllegalStateException: binder haven't been received",
            ).explainFailure()
        )
        assertTrue("Shizuku" in explanation.reason, explanation.reason)
        assertTrue("Shizuku" in explanation.advice, explanation.advice)
        assertTrue(
            "刷新" !in explanation.advice,
            "a dead binder is not a device that stayed silent: ${explanation.advice}",
        )
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
    fun aPackageThatIsAlreadyGoneIsNotAnError() {
        // The uninstall path looks the package up first; a system package that was already removed
        // leaves nothing to look up, and the old wording sent people to retry a finished job.
        val explanation = assertNotNull(
            failed(OperationResult.STATUS_NO_RESULT, "Unknown package").explainFailure()
        )
        assertTrue("找不到" in explanation.reason, explanation.reason)
        assertTrue("刷新" in explanation.advice, explanation.advice)
    }

    @Test
    fun aVivoUserRestrictionIsNamed() {
        val explanation = assertNotNull(
            failed(OperationResult.STATUS_NO_RESULT, "DELETE_FAILED_USER_RESTRICTED").explainFailure()
        )
        assertTrue("受限" in explanation.reason, explanation.reason)
    }

    @Test
    fun theMessageIsMatchedWithoutCase() {
        assertNotNull(failed(1, "Permission Denial: not allowed").explainFailure())
        assertEquals(
            failed(1, "permission denial").explainFailure()?.reason,
            failed(1, "PERMISSION DENIAL").explainFailure()?.reason,
        )
    }

    @Test
    fun androidsProtectionListIsNotBlamedOnTheVendor() {
        // Reported from the field as "厂商把这个包锁住了", which sent people looking for a vendor
        // switch: this one is Android's own list, and only root gets past it.
        val explanation = assertNotNull(
            failed(
                OperationResult.STATUS_NEGATIVE_RESPONSE,
                "java.lang.SecurityException: Cannot disable a protected package: com.freeme.ime",
            ).explainFailure()
        )
        assertTrue("保护包" in explanation.reason, explanation.reason)
        assertTrue("root" in explanation.advice, explanation.advice)
        assertTrue("厂商" !in explanation.reason, explanation.reason)
    }

    @Test
    fun aRomThatRefusesEverySystemPackageIsNamedAsSuch() {
        val explanation = assertNotNull(
            failed(
                OperationResult.STATUS_NEGATIVE_RESPONSE,
                "java.lang.SecurityException: Cannot disable system packages.",
            ).explainFailure()
        )
        assertTrue("ROM" in explanation.reason, explanation.reason)
    }

    @Test
    fun aShellThatNeedsRootSaysSo() {
        val explanation = assertNotNull(
            failed(
                OperationResult.STATUS_NEGATIVE_RESPONSE,
                "java.lang.SecurityException: Cannot disable com.vivo.abe no root permission",
            ).explainFailure()
        )
        assertTrue("root" in explanation.advice, explanation.advice)
    }

    @Test
    fun theDeviceOwnerTierIsNotReadAsAVendorLock() {
        // What DeviceLockController answers to a shell: the framework will not even name the
        // package setting, so there is no vendor switch to go looking for.
        val explanation = assertNotNull(
            failed(
                OperationResult.STATUS_NEGATIVE_RESPONSE,
                "java.lang.SecurityException: Shell cannot change component state for null to 0",
            ).explainFailure()
        )
        assertTrue("设备所有者" in explanation.reason, explanation.reason)
        assertTrue("厂商" !in explanation.reason, explanation.reason)
    }
}