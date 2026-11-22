package io.github.samolego.canta.util

import io.github.samolego.canta.data.history.OperationKind
import kotlinx.serialization.Serializable

/**
 * One operation the device refused, as the batch that tried it saw it: what was attempted, what
 * the device answered, and how the app explained that answer.
 *
 * Collected for the person who maintains the app: a phone that refuses to freeze a package is
 * almost never reproducible from the outside, and the device's own wording ([message]) plus the
 * batch shape is what makes it diagnosable afterwards.
 */
data class FailureDetail(
    val kind: OperationKind,
    val packageName: String,
    /** The `PackageInstaller.STATUS_*` code, or one of [io.github.samolego.canta.packages.OperationResult]'s own. */
    val status: Int,
    /** The device's own wording, never rewritten: the specific cause usually lives here. */
    val message: String,
    /** The app's reading of [message]/[status], already shown to the user. */
    val reason: String,
    /** What the app told the user to try next. */
    val advice: String,
    val isSystemApp: Boolean,
    val batchIndex: Int,
    val batchSize: Int,
    /** The app's own version, so "this only happens with 5.2 of that app" stays answerable. */
    val appVersion: String = "",
)

/** The phone a report came from. The model code is included: the same refusal means different things per device. */
@Serializable
data class FailureReportDevice(
    val brand: String,
    val rom: String,
    val romVersion: Int,
    val sdk: Int,
    /** The code the phone reports (`SM-F9710`, `PHK110`), empty when it could not be read. */
    val model: String = "",
    /** The build's device name (`fuxi`, `aston`), empty when it could not be read. */
    val codename: String = "",
)

/**
 * What is posted to the backend's `operation_failure` RPC, one row per refused package.
 *
 * @param platform the client's own label: "android", "desktop" or "web".
 * @param privilege which route the attempt went through (`root`, `shizuku`, `dhizuku`, `none`):
 *   the same refusal means different things per route, and a route that only exists on some phones
 *   would otherwise be invisible in the data.
 * @param kind `uninstall`, `reinstall`, `disable` or `enable` (lowercase [OperationKind] names).
 */
@Serializable
data class FailureReport(
    /** Payload version; no default, so it is always on the wire even with defaults omitted. */
    val v: Int,
    val platform: String,
    /** The privilege route this attempt ran on; empty on clients that predate the field. */
    val privilege: String = "",
    val kind: String,
    val pkg: String,
    val status: Int,
    val message: String,
    val reason: String,
    val advice: String,
    val system: Boolean,
    val batchIndex: Int,
    val batchSize: Int,
    /** Which Android user/profile the attempt was for (0 is the main one). */
    val user: Int,
    val appVersion: String,
    val cantaVersion: String,
    val device: FailureReportDevice,
)
