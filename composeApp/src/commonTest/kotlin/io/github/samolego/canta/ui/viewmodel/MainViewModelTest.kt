package io.github.samolego.canta.ui.viewmodel

import io.github.samolego.canta.CANTA_PACKAGE_NAME
import io.github.samolego.canta.core.CantaDevice
import io.github.samolego.canta.core.DeviceConnectionFailure
import io.github.samolego.canta.core.DeviceDiscovery
import io.github.samolego.canta.core.Occupancy
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.history.OperationKind
import io.github.samolego.canta.data.history.OperationLogStore
import io.github.samolego.canta.data.history.OperationRecord
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.auth_required
import io.github.samolego.canta.generated.resources.auth_required_description
import io.github.samolego.canta.generated.resources.cannot_uninstall_canta
import io.github.samolego.canta.generated.resources.device_no_longer_available
import io.github.samolego.canta.generated.resources.occupied_accessibility
import io.github.samolego.canta.generated.resources.occupied_device_admin
import io.github.samolego.canta.generated.resources.occupied_input_method
import io.github.samolego.canta.generated.resources.occupied_launcher
import io.github.samolego.canta.testing.FakeHandler
import io.github.samolego.canta.testing.FakePlatform
import io.github.samolego.canta.testing.MemoryStorage
import io.github.samolego.canta.testing.offlineBloatRepository
import io.github.samolego.canta.ui.AppsType
import io.github.samolego.canta.util.SafetyRule
import io.github.samolego.canta.util.SafetyRuleSet
import io.github.samolego.canta.util.SafetyRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.getString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val handler = FakeHandler()
    private val platform = FakePlatform()
    private val settings = CantaSettings(MemoryStorage())

    /**
     * Compose resources read a string file on an IO thread the first time, so
     * the view model's coroutines would finish at an unpredictable moment.
     * Cached strings resolve synchronously, keeping the view model on the
     * test dispatcher.
     */
    private fun runVmTest(body: suspend TestScope.() -> Unit): TestResult = runTest {
        listOf(
            Res.string.cannot_uninstall_canta,
            Res.string.auth_required,
            Res.string.auth_required_description,
            Res.string.device_no_longer_available,
            Res.string.occupied_launcher,
            Res.string.occupied_input_method,
            Res.string.occupied_device_admin,
            Res.string.occupied_accessibility,
        ).forEach { getString(it) }
        body()
    }

    private suspend fun mainViewModel(
        loadRules: suspend () -> SafetyRules = { SafetyRules(SafetyRuleSet()) },
    ): Pair<MainViewModel, AppListViewModel> {
        val appList = AppListViewModel(handler, offlineBloatRepository())
        val main = MainViewModel(handler, platform, settings, appList, loadRules)
        return main to appList
    }

    /** Rules written by the test: the shipped set is a resource, and the tests need it to be fixed. */
    private fun rules(vararg rules: SafetyRule) = SafetyRules(SafetyRuleSet(rules = rules.toList()))

    @Test
    fun cantaNeverUninstallsItself() = runVmTest {
        val (main, appList) = mainViewModel()
        appList.selectedApps.addAll(listOf("com.a", CANTA_PACKAGE_NAME))

        main.applyToSelected(AppsType.INSTALLED)

        assertEquals(listOf(getString(Res.string.cannot_uninstall_canta)), platform.shownMessages)
        assertTrue(handler.uninstallCalls.isEmpty())
        assertNull(main.dialog)
    }

    @Test
    fun uninstallIsConfirmedFirstAndReportsSuccess() = runVmTest {
        handler.resettable += "com.a"
        val (main, appList) = mainViewModel()
        appList.selectedApps.add("com.a")

        main.applyToSelected(AppsType.INSTALLED)
        assertEquals(MainDialog.ConfirmUninstall(appCount = 1, canResetToFactory = true, canFreeze = true), main.dialog)
        assertTrue(handler.uninstallCalls.isEmpty(), "nothing happens before confirming")

        main.onUninstallConfirmed(resetToFactory = true)
        assertEquals(listOf(listOf("com.a") to true), handler.uninstallCalls)
        assertEquals(MainDialog.Success(count = 1, isReinstall = false), main.dialog)
    }

    @Test
    fun packagesNewerThanTheLastConfirmedBatchAreFlagged() = runVmTest {
        handler.resettable += "com.a"
        settings.setLastConfirmedPackages(listOf("com.other"))
        val (main, appList) = mainViewModel()
        appList.selectedApps.add("com.a")

        main.applyToSelected(AppsType.INSTALLED)

        assertEquals(
            MainDialog.ConfirmUninstall(appCount = 1, canResetToFactory = true, newPackages = listOf("com.a"), canFreeze = true),
            main.dialog,
        )
    }

    @Test
    fun packagesFromTheLastConfirmedBatchAreNotFlaggedAsNew() = runVmTest {
        handler.resettable += "com.a"
        settings.setLastConfirmedPackages(listOf("com.a"))
        val (main, appList) = mainViewModel()
        appList.selectedApps.add("com.a")

        main.applyToSelected(AppsType.INSTALLED)

        assertEquals(MainDialog.ConfirmUninstall(appCount = 1, canResetToFactory = true, canFreeze = true), main.dialog)
    }

    @Test
    fun restoreSuspectsAreShownOnceThenForgotten() = runVmTest {
        settings.addRestoreSuspects(listOf("com.a", "com.b"))
        val (main, _) = mainViewModel()

        assertEquals(MainDialog.RestoreSuspects(listOf("com.a", "com.b")), main.dialog)

        main.onRestoreSuspectsDismissed()
        assertNull(main.dialog)
        assertEquals(emptyList(), settings.restoreSuspectsFlow.first())
    }

    @Test
    fun aBatchThatAllFailedIsExplainedInsteadOfSilent() = runVmTest {
        handler.failing += "com.a"
        handler.failureStatus = 1
        handler.failureMessage = "not allowed to disable this package"
        val (main, appList) = mainViewModel()
        appList.selectedApps.add("com.a")

        main.applyToSelected(AppsType.INSTALLED)
        main.onUninstallConfirmed(resetToFactory = false)

        val dialog = assertIs<MainDialog.Failure>(main.dialog)
        assertEquals(1, dialog.attempts)
        assertFalse(dialog.isReinstall)
        assertEquals(1, dialog.reasons.size, "one cause, said once")
        assertTrue("厂商" in dialog.reasons.single().first, dialog.reasons.single().first)
    }

    @Test
    fun freezingKeepsTheAppsAndSaysSoSeparatelyFromRemoval() = runVmTest {
        val (main, appList) = mainViewModel()
        appList.selectedApps.addAll(listOf("com.a", "com.b"))

        main.applyToSelected(AppsType.INSTALLED)
        main.onFreezeConfirmed()

        assertEquals(listOf(listOf("com.a", "com.b") to true), handler.disableCalls)
        assertTrue(handler.uninstallCalls.isEmpty(), "freezing is not removing")
        assertTrue(appList.apps.none { it.isUninstalled }, "a frozen app is still installed")
    }

    @Test
    fun aPartialFailedBatchStillReportsTheSuccesses() = runVmTest {
        handler.failing += "com.b"
        val (main, appList) = mainViewModel()
        appList.selectedApps.addAll(listOf("com.a", "com.b"))

        main.applyToSelected(AppsType.INSTALLED)
        main.onUninstallConfirmed(resetToFactory = false)

        assertEquals(MainDialog.Success(count = 1, isReinstall = false), main.dialog)
    }

    @Test
    fun reinstallSkipsConfirmationAndHiddenSuccessDialogStaysHidden() = runVmTest {
        settings.setHideSuccessDialog(true)
        val (main, appList) = mainViewModel()
        appList.selectedApps.add("com.a")

        main.applyToSelected(AppsType.UNINSTALLED)

        assertEquals(listOf(listOf("com.a")), handler.reinstallCalls)
        assertNull(main.dialog)
    }

    @Test
    fun nothingIsAppliedUntilBiometricsSucceed() = runVmTest {
        settings.setConfirmBeforeUninstall(false)
        settings.setAuthEnabled(true)
        val (main, appList) = mainViewModel()
        appList.selectedApps.add("com.a")

        main.applyToSelected(AppsType.INSTALLED)
        assertTrue(platform.biometricRequested)
        assertTrue(handler.uninstallCalls.isEmpty())

        platform.approveBiometric()
        assertEquals(listOf(listOf("com.a") to false), handler.uninstallCalls)
    }

    @Test
    fun failedConnectionIsExplainedAndTheChooserStaysOpen() = runVmTest {
        val device = CantaDevice(id = "serial", displayName = "Phone")
        val adbHandler = FakeHandler(authorized = false, deviceDiscovery = DeviceDiscovery.List).apply {
            devices = listOf(device)
            connectFailure = DeviceConnectionFailure.DeviceUnavailable
        }
        val appList = AppListViewModel(adbHandler, offlineBloatRepository())
        val main = MainViewModel(adbHandler, platform, settings, appList)
        assertEquals(MainDialog.DeviceChooser(devices = listOf(device)), main.dialog, "opens with discovered devices")

        main.selectDevice(device)

        assertEquals(listOf(getString(Res.string.device_no_longer_available)), platform.shownMessages)
        assertIs<MainDialog.DeviceChooser>(main.dialog)
        assertTrue(appList.apps.isEmpty(), "no apps without a device")

        adbHandler.connectFailure = null
        main.selectDevice(device)

        assertNull(main.dialog, "chooser closes once connected")
        assertFalse(appList.apps.isEmpty(), "apps load after connecting")
    }

    @Test
    fun aPackageThatWouldStrandThePhoneIsBlockedEvenWithConfirmationOff() = runVmTest {
        settings.setConfirmBeforeUninstall(false)
        val (main, appList) = mainViewModel { rules(SafetyRule("com.a", "critical", why = "no launcher left")) }
        appList.selectedApps.add("com.a")

        main.applyToSelected(AppsType.INSTALLED)

        val dialog = assertIs<MainDialog.UnsafeSelection>(main.dialog)
        assertEquals(listOf("com.a"), dialog.blocked.map { it.pkg })
        assertTrue(handler.uninstallCalls.isEmpty(), "turning the prompt off must not turn the rules off")

        main.dismissDialog()
        assertNull(main.dialog)
        assertTrue(handler.uninstallCalls.isEmpty(), "cancelling the rules dialog removes nothing")
    }

    @Test
    fun aCostlyPackageGoesThroughWhenConfirmationIsOff() = runVmTest {
        settings.setConfirmBeforeUninstall(false)
        val (main, appList) = mainViewModel { rules(SafetyRule("com.a", "risky", why = "some settings are lost")) }
        appList.selectedApps.add("com.a")

        main.applyToSelected(AppsType.INSTALLED)

        assertEquals(listOf(listOf("com.a") to false), handler.uninstallCalls, "risky is not blocked, only mentioned")
        assertEquals(MainDialog.Success(count = 1, isReinstall = false), main.dialog)
    }

    @Test
    fun aCostlyPackageIsAskedAboutAndStillRemovedWhenTheUserContinues() = runVmTest {
        val (main, appList) = mainViewModel { rules(SafetyRule("com.a", "risky", why = "some settings are lost")) }
        appList.selectedApps.add("com.a")

        main.applyToSelected(AppsType.INSTALLED)

        val dialog = assertIs<MainDialog.UnsafeSelection>(main.dialog)
        assertTrue(dialog.blocked.isEmpty(), "nothing in this batch strands the phone")
        assertEquals(listOf("com.a"), dialog.risky.map { it.pkg })
        assertTrue(handler.uninstallCalls.isEmpty())

        main.onUnsafeSelectionAccepted()
        assertEquals(listOf(listOf("com.a") to false), handler.uninstallCalls)
    }

    @Test
    fun continuingTheRulesDialogDropsOnlyWhatWouldStrandThePhone() = runVmTest {
        settings.setConfirmBeforeUninstall(false)
        val (main, appList) = mainViewModel {
            rules(SafetyRule("com.a", "critical"), SafetyRule("com.b", "risky"))
        }
        appList.selectedApps.addAll(listOf("com.a", "com.b"))

        main.applyToSelected(AppsType.INSTALLED)
        main.onUnsafeSelectionAccepted()

        assertEquals(listOf(listOf("com.b") to false), handler.uninstallCalls)
    }

    @Test
    fun aPackageDoingAJobThePhoneNeedsIsHeldBackEvenWithoutARuleAboutIt() = runVmTest {
        settings.setConfirmBeforeUninstall(false)
        handler.occupied = mapOf("com.a" to listOf(Occupancy.LAUNCHER))
        val (main, appList) = mainViewModel()
        appList.selectedApps.add("com.a")

        main.applyToSelected(AppsType.INSTALLED)

        val dialog = assertIs<MainDialog.UnsafeSelection>(main.dialog)
        assertEquals(listOf("com.a"), dialog.blocked.map { it.pkg })
        assertTrue(dialog.blocked.single().why.isNotBlank(), "the dialog has to say which job it is")
        assertTrue(handler.uninstallCalls.isEmpty(), "removing the home screen leaves nothing to go back to")
    }

    @Test
    fun aPackageBothRiskyByRuleAndInUseIsListedOnceAsBlocked() = runVmTest {
        handler.occupied = mapOf("com.a" to listOf(Occupancy.LAUNCHER))
        val (main, appList) = mainViewModel { rules(SafetyRule("com.a", "risky", why = "loses a setting")) }
        appList.selectedApps.add("com.a")

        main.applyToSelected(AppsType.INSTALLED)

        val dialog = assertIs<MainDialog.UnsafeSelection>(main.dialog)
        assertEquals(listOf("com.a"), dialog.blocked.map { it.pkg })
        assertTrue(dialog.risky.isEmpty(), "the same package must not be offered as removable further down")
        assertTrue("loses a setting" in dialog.blocked.single().why, "both reasons survive in one line")
    }

    /**
     * The records tab's switch: on, the log is dropped the first time the app runs after a reboot;
     * off, it stays. Never on the very first start of the switch either — a reboot that happened
     * before the app ever ran is not what was asked for.
     *
     * Asked of the list directly rather than through a start: the answer is what the start acts on
     * (it is also what makes the app say the log was cleared), and awaiting it keeps the test out
     * of the dispatchers a real start hops through.
     */
    @Test
    fun theRecordsAreClearedAtASystemStartOnlyWhenTheSwitchIsOn() = runVmTest {
        val log = OperationLogStore(MemoryStorage(), platform)
        suspend fun aRecord() {
            log.record(
                listOf(
                    OperationRecord(
                        timestamp = 1_000L,
                        kind = OperationKind.UNINSTALL,
                        packageName = "com.a",
                        label = "A",
                        success = true,
                    )
                )
            )
            assertEquals(1, log.records.value.size, "the log starts out with something to clear")
        }
        suspend fun start(): Boolean = AppListViewModel(
            handler,
            offlineBloatRepository(),
            history = log,
            settings = settings,
        ).clearRecordsIfRebooted()

        settings.setClearRecordsAfterBoot(true)
        handler.boot = "boot-1"

        aRecord()
        assertFalse(start(), "no boot was stored yet, so there is no restart to see")
        assertEquals(1, log.records.value.size)
        assertEquals("boot-1", settings.recordsBootIdFlow.first())

        // The same boot, opened again: opening the app is not a system start.
        assertFalse(start())
        assertEquals(1, log.records.value.size)

        // A reboot with the switch on: the log goes, and the answer is what the app reports.
        handler.boot = "boot-2"
        assertTrue(start(), "the switch is on and the phone restarted")
        assertTrue(log.records.value.isEmpty())
        assertEquals("boot-2", settings.recordsBootIdFlow.first())

        // A reboot with the switch off: the log stays, and the boot is still remembered.
        settings.setClearRecordsAfterBoot(false)
        aRecord()
        handler.boot = "boot-3"
        assertFalse(start(), "off means the records are kept through restarts")
        assertEquals(1, log.records.value.size)
        assertEquals("boot-3", settings.recordsBootIdFlow.first())
    }
}
