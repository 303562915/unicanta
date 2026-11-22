package io.github.samolego.canta.util

import io.github.samolego.canta.BuildInfo
import io.github.samolego.canta.core.CantaHandler
import io.github.samolego.canta.data.CantaSettings
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.proto.BootSnapshotProto
import io.github.samolego.canta.data.proto.PendingUninstallProto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Prefix of the Android fallback boot id (`Settings.Global.BOOT_COUNT`). */
const val BOOT_COUNT_PREFIX = "boot_count:"

/** Which check is resolving the pending uninstalls. */
enum class ResolveMode {
    /** Right after the device finished booting: an app that is back was not there when the boot completed. */
    BOOT,

    /** When Canta starts, possibly long after the boot. */
    START,
}

/**
 * What is known about a package on the device right now.
 * [installedSinceBoot]: reinstalled for the user after the current boot started; null if unknown.
 */
class PackageState(val uninstalled: Boolean, val installedSinceBoot: Boolean? = null)

/** Both builds known and different: the OS was updated since the uninstall. */
private fun fingerprintChanged(then: String, now: String?) = then.isNotEmpty() && !now.isNullOrEmpty() && then != now

/** Both builds known and equal: the OS was not updated since the uninstall. */
private fun buildUnchanged(then: String, now: String?) = then.isNotEmpty() && !now.isNullOrEmpty() && then == now

/**
 * Decides what to do with the uninstalls waiting for their "did it survive a
 * reboot" report. Returns the events to send and the entries that stay pending.
 * Silence only ever means "the device never completed a boot after the uninstall";
 * every other outcome is reported.
 *
 *  - older than [TelemetryConfig.PENDING_MAX_AGE]: dropped
 *  - recorded on another device ([PendingUninstallProto.deviceKey] differs from [currentDeviceKey],
 *    both known): kept untouched until it expires
 *  - same boot as when uninstalled (or boot unknown): kept
 *  - package unknown ([states] has no entry): kept until it expires (another device may be
 *    connected, e.g. on desktop and web; nothing device-specific is stored but the boot id,
 *    which a different device never matches)
 *  - rebooted, not reported yet, still uninstalled: `survived_reboot`, kept as survived
 *  - rebooted, already reported as survived, installed again: `reinstalled` (dropped silently if the
 *    build changed: an OS update restored it)
 *  - rebooted, not reported yet, installed again:
 *    - build changed ([currentFingerprint] differs): `voided` (an OS update restored it), dropped
 *    - [ResolveMode.BOOT], build unchanged: `restored`, dropped (someone put the package back, so
 *      it leaves the survival count, but it is worth reporting as a probable rescue)
 *    - [ResolveMode.BOOT], build unknown: `voided`, dropped (nothing can be claimed either way)
 *    - [ResolveMode.START]: `survived_reboot` + `reinstalled` if it was reinstalled after the boot
 *      started, else `voided` (can't tell), dropped
 */
@OptIn(ExperimentalTime::class)
fun resolvePending(
    pending: List<PendingUninstallProto>,
    mode: ResolveMode,
    currentBootId: String?,
    currentFingerprint: String?,
    states: Map<String, PackageState>,
    now: Instant,
    currentDeviceKey: String? = null,
    countBaselinesAsSurvived: Boolean = false,
): Pair<List<TelemetryEvent>, List<PendingUninstallProto>> {
    val cutoff = now - TelemetryConfig.PENDING_MAX_AGE
    val events = mutableListOf<TelemetryEvent>()
    val remaining = mutableListOf<PendingUninstallProto>()
    for (entry in pending) {
        if (!entry.baseline && Instant.fromEpochMilliseconds(entry.uninstalledAt) < cutoff) continue
        // Ids of different kinds (boot_id vs the "boot_count:" fallback) can't be compared.
        val comparable = entry.bootId.startsWith(BOOT_COUNT_PREFIX) == currentBootId?.startsWith(BOOT_COUNT_PREFIX)
        val state = states[entry.pkg]
        val otherDevice = entry.deviceKey.isNotEmpty() && currentDeviceKey != null && entry.deviceKey != currentDeviceKey
        if (otherDevice || currentBootId == null || !comparable || entry.bootId == currentBootId || state == null) {
            remaining += entry
            continue
        }
        if (entry.baseline) {
            // A baseline is a removal that was already there when this build first looked: it is
            // proved by a later boot that still finds the package gone, and only when the caller
            // asked for that proof. Until then it has to stay pending — dropping it here would
            // mean it can never be proved. One that is back on the phone proves nothing at all.
            when {
                !state.uninstalled -> {}
                countBaselinesAsSurvived -> remaining += entry.copy(survived = true)
                else -> remaining += entry
            }
            continue
        }
        fun report(type: TelemetryEventType) = events.add(TelemetryEvent.ofPending(entry, type))
        when {
            entry.survived -> when {
                state.uninstalled -> remaining += entry
                // An OS update brought it back, not the user.
                fingerprintChanged(entry.buildFingerprint, currentFingerprint) -> {}
                else -> report(TelemetryEventType.REINSTALLED)
            }
            state.uninstalled -> {
                report(TelemetryEventType.SURVIVED_REBOOT)
                remaining += entry.copy(survived = true)
            }
            fingerprintChanged(entry.buildFingerprint, currentFingerprint) -> report(TelemetryEventType.VOIDED)
            // Installed again although the build did not change: nothing but a person can have put it
            // back, so this most likely was a rescue of a device that would not boot. Still not
            // survival evidence (the entry is dropped), but reported, unlike before.
            mode == ResolveMode.BOOT && buildUnchanged(entry.buildFingerprint, currentFingerprint) ->
                report(TelemetryEventType.RESTORED)
            // installedSinceBoot is ignored here: it can't be told apart from a bootloop rescue.
            mode == ResolveMode.BOOT -> report(TelemetryEventType.VOIDED)
            state.installedSinceBoot == true -> {
                report(TelemetryEventType.SURVIVED_REBOOT)
                report(TelemetryEventType.REINSTALLED)
            }
            else -> report(TelemetryEventType.VOIDED)
        }
    }
    return events to remaining
}

/** Whether this entry is for [pkg] on the device [deviceKey]; entries without a key (legacy) match any device. */
internal fun PendingUninstallProto.isFor(pkg: String, deviceKey: String) =
    this.pkg == pkg && (this.deviceKey.isEmpty() || deviceKey.isEmpty() || this.deviceKey == deviceKey)

/** The device the uninstall happened on; unknown for entries from before it was recorded. */
fun PendingUninstallProto.device() = DeviceContext(brand.ifBlank { "unknown" }, rom.ifBlank { "unknown" }, romVersion, sdk)

/**
 * Ties [Telemetry] to what happens in the app: reports uninstalls and
 * reinstalls done through Canta and, once per start, which uninstalls survived
 * a reboot.
 *
 * The pending uninstalls and the "this one came back by hand" suspects are kept
 * locally even while analytics is off: they drive a warning the user sees, and
 * only the events are gated behind analytics.
 */
@OptIn(ExperimentalTime::class)
class PackageTelemetry(
    private val handler: CantaHandler,
    private val telemetry: Telemetry,
    private val settings: CantaSettings,
    private val clock: Clock = Clock.System,
) {
    private val pendingLock = Mutex()

    val analyticsEnabled: Flow<Boolean> get() = settings.analyticsEnabledFlow

    /** The connected device, or null if it can't be read. */
    suspend fun deviceContext(): DeviceContext? = handler.deviceContext()

    /** Community scores, or null when analytics is off or they couldn't be fetched. */
    suspend fun fetchScores() =
        if (settings.analyticsEnabledFlow.first()) telemetry.fetchScores(handler.deviceContext()) else null

    /**
     * Reports the packages a batch could not touch, one row each, so a refusal that only shows up
     * on somebody else's phone can still be explained afterwards (see [FailureReport]).
     *
     * Gated by the analytics opt-in like every other report; fire-and-forget, and never awaited by
     * the batch that produced it.
     */
    suspend fun recordFailures(details: List<FailureDetail>) {
        if (details.isEmpty()) return
        if (!settings.analyticsEnabledFlow.first()) return
        val device = failureDevice()
        val user = handler.selectedUserId ?: 0
        val version = BuildInfo.VERSION_NAME
        for (detail in details) {
            val report = FailureReport(
                v = 1,
                platform = telemetry.platformName,
                // Which route refused: root, Stellar/Shizuku or Dhizuku behave differently, and the
                // Dhizuku one is the newest, so its refusals are the ones worth reading first.
                privilege = handler.privilegeKind.wire,
                kind = detail.kind.name.lowercase(),
                pkg = detail.packageName,
                status = detail.status,
                // The device's own wording is the point of the report, but a stack trace pasted
                // into it must not become the whole row.
                message = detail.message.take(MAX_FAILURE_TEXT),
                reason = detail.reason.take(MAX_FAILURE_TEXT),
                advice = detail.advice.take(MAX_FAILURE_TEXT),
                system = detail.isSystemApp,
                batchIndex = detail.batchIndex,
                batchSize = detail.batchSize,
                user = user,
                appVersion = detail.appVersion,
                cantaVersion = version,
                device = device,
            )
            telemetry.sendFailureReport(report)
        }
    }

    /** The phone as the reports describe it: the coarse context plus the model, which is the key the analysis groups by. */
    private suspend fun failureDevice(): FailureReportDevice {
        val context = runCatching { handler.deviceContext() }.getOrNull() ?: DeviceContext.UNKNOWN
        val profile = runCatching { handler.deviceProfile() }.getOrNull()
        return FailureReportDevice(
            brand = context.brand,
            rom = context.rom,
            romVersion = context.romVersion,
            sdk = context.sdk,
            model = profile?.model.orEmpty(),
            codename = profile?.device.orEmpty(),
        )
    }

    /** Longest piece of text kept per field of a [FailureReport]. */
    private companion object {
        const val MAX_FAILURE_TEXT = 400
    }

    /**
     * Forgets everything queued for sending (analytics turned off). The pending uninstalls stay:
     * they are local only and still tell the user that a batch had to be rescued.
     */
    suspend fun clearPending() {
        telemetry.clear()
    }

    /** Records that [apps] were uninstalled as part of one user action on [batchSize] apps. */
    suspend fun recordUninstalls(apps: List<AppInfo>, batchSize: Int) {
        val systemApps = apps.filter { it.isSystemApp }
        if (systemApps.isEmpty()) return
        val bootId = handler.bootId() ?: return
        val now = clock.now()
        val week = cohortWeekOf(now)
        val bucket = batchBucketOf(batchSize)
        val device = handler.deviceContext() ?: DeviceContext.UNKNOWN
        val fingerprint = handler.buildFingerprint().orEmpty()
        val deviceKey = handler.deviceKey().orEmpty()
        if (settings.analyticsEnabledFlow.first()) {
            telemetry.queue(TelemetryEvent.allOf(systemApps, TelemetryEventType.UNINSTALLED, week, bucket, device))
        }
        pendingLock.withLock {
            val replaced = systemApps.map { it.packageName }.toSet()
            val keep = settings.pendingRebootFlow.first().filter { e -> replaced.none { e.isFor(it, deviceKey) } }
            val added = systemApps.map {
                PendingUninstallProto(
                    it.packageName, bootId, week, bucket, now.toEpochMilliseconds(),
                    device.brand, device.rom, device.romVersion, device.sdk,
                    buildFingerprint = fingerprint, deviceKey = deviceKey,
                )
            }
            settings.setPendingReboot(keep + added)
        }
    }

    /** Records that [apps] were reinstalled; only those with a pending uninstall (known cohort) are reported. */
    suspend fun recordReinstalls(apps: List<AppInfo>) {
        pendingLock.withLock {
            val pending = settings.pendingRebootFlow.first()
            if (pending.isEmpty()) return
            val deviceKey = handler.deviceKey().orEmpty()
            val events = apps.mapNotNull { app ->
                val entry = pending.firstOrNull { it.isFor(app.packageName, deviceKey) } ?: return@mapNotNull null
                TelemetryEvent.of(app, TelemetryEventType.REINSTALLED, entry.cohortWeek, entry.batchBucket, entry.device())
            }
            if (settings.analyticsEnabledFlow.first()) telemetry.queue(events)
            val reinstalled = apps.map { it.packageName }
            settings.setPendingReboot(pending.filter { e -> reinstalled.none { e.isFor(it, deviceKey) } })
        }
    }

    /**
     * Remembers the packages that came back although the build had not changed: someone put them
     * back by hand, which is the only trace of a rescue (see [TelemetryEventType.RESTORED]). Kept
     * locally so the user is told at the next start, whether or not analytics is on.
     */
    private suspend fun recordRestores(events: List<TelemetryEvent>) {
        val restored = events.filter { it.event == TelemetryEventType.RESTORED }.map { it.pkg }
        if (restored.isNotEmpty()) settings.addRestoreSuspects(restored)
    }

    /** Adds pre-existing uninstalls as a local baseline; their next confirmed reboot is the first proof. */
    suspend fun seedExistingUninstalls(apps: List<AppInfo>): Int {
        val uninstalled = apps.filter { it.isSystemApp && it.isUninstalled }
        if (uninstalled.isEmpty()) return 0
        val bootId = handler.bootId() ?: return 0
        val now = clock.now()
        val device = handler.deviceContext() ?: DeviceContext.UNKNOWN
        val deviceKey = handler.deviceKey().orEmpty()
        val fingerprint = handler.buildFingerprint().orEmpty()
        val week = cohortWeekOf(now)
        val bucket = batchBucketOf(uninstalled.size)
        return pendingLock.withLock {
            val existing = settings.pendingRebootFlow.first()
            val known = existing.filter { it.deviceKey.isEmpty() || it.deviceKey == deviceKey }.map { it.pkg }.toSet()
            val baseline = uninstalled.filterNot { app ->
                app.packageName in known || existing.any { entry ->
                    entry.pkg == app.packageName && entry.deviceKey.isNotEmpty() && entry.deviceKey != deviceKey
                }
            }.map { app ->
                PendingUninstallProto(
                    pkg = app.packageName,
                    bootId = bootId,
                    cohortWeek = week,
                    batchBucket = bucket,
                    uninstalledAt = now.toEpochMilliseconds(),
                    brand = device.brand,
                    rom = device.rom,
                    romVersion = device.romVersion,
                    sdk = device.sdk,
                    buildFingerprint = fingerprint,
                    deviceKey = deviceKey,
                    baseline = true,
                )
            }
            if (baseline.isEmpty()) 0
            else if (settings.seedPendingReboot(baseline)) baseline.size
            else 0
        }
    }

    /** Reports what became of the pending uninstalls, given the freshly loaded [apps] (Canta start). */
    suspend fun resolveSurvivors(apps: List<AppInfo>) {
        val analytics = settings.analyticsEnabledFlow.first()
        val bootId = handler.bootId() ?: return
        val fingerprint = handler.buildFingerprint()
        val deviceKey = handler.deviceKey()
        pendingLock.withLock {
            val pending = settings.pendingRebootFlow.first()
            if (pending.isEmpty()) return
            val byPackage = apps.associateBy { it.packageName }
            val states = pending.mapNotNull { entry ->
                val app = byPackage[entry.pkg] ?: return@mapNotNull null
                entry.pkg to PackageState(app.isUninstalled, if (app.isUninstalled) null else handler.installedSinceBoot(entry.pkg))
            }.toMap()
            val (events, remaining) = resolvePending(
                pending, ResolveMode.START, bootId, fingerprint, states, clock.now(), deviceKey,
                countBaselinesAsSurvived = true,
            )
            recordRestores(events)
            // The survival mark is local state the user is told about ("重启验证 N 个"), so it is kept
            // even when the reports cannot be sent: an offline phone must not look unverified.
            if (remaining != pending) settings.setPendingReboot(remaining)
            if (analytics) telemetry.sendAndWait(events)
        }
    }

    /**
     * Called when the device finished booting (no privileged access then, so the caller reads
     * the package states): the states of the pending packages, read at that moment so that a later
     * reinstall outside Canta isn't mistaken for a restore at boot; null if there is nothing to check.
     */
    suspend fun snapshotAtBoot(
        bootId: String?,
        fingerprint: String?,
        stateOf: (String) -> PackageState?,
    ): BootSnapshotProto? {
        if (bootId == null) return null
        val pending = pendingLock.withLock { settings.pendingRebootFlow.first() }
        if (pending.isEmpty()) return null
        val states = pending.mapNotNull { e -> stateOf(e.pkg)?.let { e.pkg to it } }.toMap()
        return BootSnapshotProto(
            bootId, fingerprint.orEmpty(),
            uninstalled = states.filterValues { it.uninstalled }.keys.toList(),
            installed = states.filterValues { !it.uninstalled }.keys.toList(),
        )
    }

    /**
     * The check after the device finished booting, using the [snapshot] taken at that moment.
     * A snapshot of another boot is stale and ignored (the next start's check handles it).
     * With analytics on, waits for the reports to be delivered and only then updates the pending
     * uninstalls, returning false if sending failed and the check should be retried; with analytics
     * off nothing is sent and it returns true once the snapshot is done with.
     */
    suspend fun resolveAfterBoot(snapshot: BootSnapshotProto, currentBootId: String?): Boolean {
        val analytics = settings.analyticsEnabledFlow.first()
        if (currentBootId == null || snapshot.bootId != currentBootId) return true
        val deviceKey = handler.deviceKey()
        val states = snapshot.uninstalled.associateWith { PackageState(uninstalled = true) } +
            snapshot.installed.associateWith { PackageState(uninstalled = false) }
        pendingLock.withLock {
            val pending = settings.pendingRebootFlow.first()
            if (pending.isEmpty()) return true
            val (events, remaining) = resolvePending(
                pending, ResolveMode.BOOT, currentBootId, snapshot.buildFingerprint.ifEmpty { null }, states, clock.now(), deviceKey,
                countBaselinesAsSurvived = true,
            )
            recordRestores(events)
            // Local first: the check itself has happened, whether or not the reports can leave the
            // phone right now. Only the delivery is retried by the worker.
            if (remaining != pending) settings.setPendingReboot(remaining)
            val delivered = !analytics || telemetry.sendAndWait(events)
            return delivered
        }
    }
}
