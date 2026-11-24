package io.github.samolego.canta.util

import kotlinx.serialization.Serializable

/**
 * What became of the app's own updater on one phone: the check, the download, the install.
 *
 * Posted to the backend's `update_report` RPC, one row per stage that ended. This is the record
 * that answers "为什么他那儿更新装不上" without asking the person to describe their screen: the
 * stage names where it stopped, [code] names why, [detail] keeps the per-route `pm install` answer,
 * and the device fields say which phone it was — the same phone model refuses the same APK for the
 * same reason, so the model is the key the analysis groups by.
 *
 * No identity is in it: no token, no account, no package list, nothing but this app's own version
 * and the device's coarse fingerprint. That is why it is not behind the package-data opt-in — see
 * `PackageTelemetry.recordUpdate`.
 *
 * @param stage `check`, `download`, `install` or `handoff` (the APK was given to the system installer).
 * @param outcome `ok`, `failed` or `cancelled`.
 * @param channel `stable` or `dev`, empty when the check never got that far.
 * @param progress the last percentage the download reached, [INSTALL_PROGRESS_UNKNOWN]-style -1 when unknown.
 * @param elapsedMs how long the stage took before it answered, 0 when not measured.
 */
@Serializable
data class UpdateReport(
    /** Payload version; no default, so it is always on the wire even with defaults omitted. */
    val v: Int,
    /** The client's own label: "android", "desktop" or "web". */
    val platform: String,
    val stage: String,
    val outcome: String,
    val channel: String = "",
    /** The release label the update was about (`4.3.0`, `4.3.0 (dev 239)`). */
    val version: String = "",
    /** The failure enum's own name (`NETWORK`, `TIMEOUT`, `STORAGE`, `UNSUPPORTED`, `MALFORMED`…). */
    val code: String = "",
    /** The sentence the page put on screen, already localized: what the person actually read. */
    val reason: String = "",
    /** The install path's per-route report (`Shizuku·临时目录：…`), or the thrown error's text. */
    val detail: String = "",
    val progress: Int = -1,
    val bytes: Long = 0L,
    val elapsedMs: Long = 0L,
    /** This app's version, so "only 4.3.0 does this" stays answerable. */
    val cantaVersion: String = "",
    val device: FailureReportDevice,
) {
    companion object {
        const val STAGE_CHECK = "check"
        const val STAGE_DOWNLOAD = "download"
        const val STAGE_INSTALL = "install"
        const val STAGE_HANDOFF = "handoff"

        const val OUTCOME_OK = "ok"
        const val OUTCOME_FAILED = "failed"
        const val OUTCOME_CANCELLED = "cancelled"
    }
}
