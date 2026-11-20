package io.github.samolego.canta.core

import io.github.samolego.canta.packages.OperationResult

/**
 * Why an operation did not go through, said in a way the user can act on: what the device refused,
 * and what to try next. Both halves are plain sentences, because a status code plus
 * "DELETE_FAILED_INTERNAL_ERROR" tells nobody what to do.
 *
 * @param reason what happened.
 * @param advice the next step that might work.
 */
data class FailureExplanation(val reason: String, val advice: String)

/**
 * Turns a failed [OperationResult] into a [FailureExplanation], or null when it succeeded.
 *
 * [OperationResult.status] is the framework's `PackageInstaller.STATUS_*` code, which says what
 * kind of failure it was; [OperationResult.message] is the device's own wording and often carries
 * the specific cause the code does not (a vendor blocking the call, a device administrator holding
 * the package, a full partition). The message is checked first for those causes, because a vendor
 * refusal usually arrives as a generic `STATUS_FAILURE`.
 */
fun OperationResult.explainFailure(): FailureExplanation? {
    if (success) return null
    keywordFailure(message)?.let { return it }
    return statusFailure(status)
}

/** Failures the device spells out in [message], which outrank the generic status code. */
private fun keywordFailure(message: String): FailureExplanation? {
    val text = message.lowercase()

    fun mentions(vararg needles: String) = needles.any { it in text }

    return when {
        // The helper never answered: nothing was tried on the phone at all.
        mentions("helper unavailable", "no result from helper", "helper") ->
            FailureExplanation(
                reason = "没能连上设备上的助手，这次操作没有执行。",
                advice = "确认 Shizuku 还在运行、USB 调试没有被关掉，然后再试一次。",
            )

        mentions("invalid package name") ->
            FailureExplanation(
                reason = "包名格式不对，跳过没有执行。",
                advice = "重新从列表里勾选这个应用，不要手抄包名。",
            )

        // A device administrator owns the package: it cannot be removed until that is given up.
        mentions(
            "device_policy", "device policy", "devicepolicy", "device admin",
            "device_administrator", "active admin",
        ) ->
            FailureExplanation(
                reason = "这个应用正被设备管理器或企业策略占用。",
                advice = "先到「设置 → 安全 → 设备管理应用」里取消勾选它，再回来卸载。",
            )

        // The vendor's own block, seen on Huawei/Honor and some carrier builds.
        mentions("not allowed", "permission denial", "permission denied", "securityexception") ->
            FailureExplanation(
                reason = "系统拒绝了这次操作，厂商把这个包锁住了。",
                advice = "换用「停用」试试；仍被拒绝说明这台机器不允许动它，去安全规则里标成禁区。",
            )

        mentions("delete_failed_internal_error") ->
            FailureExplanation(
                reason = "系统内部报错，通常是厂商层拦下了这次调用。",
                advice = "重启后重试一次；反复失败就是这个包不该在这里动。",
            )

        mentions("still installed", "already exists") ->
            FailureExplanation(
                reason = "这个包仍然在设备上，恢复没有生效。",
                advice = "下拉刷新列表确认状态；如果它显示已卸载，重启一次让系统重新登记。",
            )

        mentions("insufficient_storage", "no space") ->
            FailureExplanation(
                reason = "存储空间不足，装不回来或删不掉。",
                advice = "腾出一些空间再试。",
            )

        mentions("version_downgrade", "downgrade") ->
            FailureExplanation(
                reason = "设备上已有更高的版本，不能用这个副本覆盖。",
                advice = "先在应用信息里卸载更新，再回来恢复。",
            )

        mentions("invalid_apk", "parse") ->
            FailureExplanation(
                reason = "系统镜像里的那份副本已经损坏。",
                advice = "这个包只能靠系统更新或刷入原厂包恢复，Canta 动不了它。",
            )

        mentions("timeout", "timed out") ->
            FailureExplanation(
                reason = "系统超时没有回应。",
                advice = "刷新列表看它现在的状态；如果已经消失，这次其实成功了。",
            )

        else -> null
    }
}

/** The framework's own `PackageInstaller.STATUS_*` code, when the message said nothing specific. */
private fun statusFailure(status: Int): FailureExplanation = when (status) {
    OperationResult.STATUS_NO_RESULT -> FailureExplanation(
        reason = "设备没有在限时内回应，这次操作的结果不明。",
        advice = "刷新列表确认它现在的状态，再决定要不要重试。",
    )

    // PackageInstaller.STATUS_FAILURE
    1 -> FailureExplanation(
        reason = "系统报告了失败，但没给出具体原因。",
        advice = "重启后重试一次；反复失败就到安全规则里看这个包的风险等级。",
    )

    // PackageInstaller.STATUS_FAILURE_BLOCKED
    2 -> FailureExplanation(
        reason = "这次操作被系统或厂商策略挡住了。",
        advice = "换用「停用」；仍被挡住说明这台机器不允许移除它。",
    )

    // PackageInstaller.STATUS_FAILURE_ABORTED
    3 -> FailureExplanation(
        reason = "操作被中止了。",
        advice = "直接重试一次。",
    )

    // PackageInstaller.STATUS_FAILURE_INVALID
    4 -> FailureExplanation(
        reason = "这个包名在这台设备上无效或已经不在那个状态。",
        advice = "刷新列表，按现在的状态重新操作。",
    )

    // PackageInstaller.STATUS_FAILURE_CONFLICT
    5 -> FailureExplanation(
        reason = "和已安装的版本冲突，签名或版本对不上。",
        advice = "先在应用信息里卸载更新，再回来操作。",
    )

    // PackageInstaller.STATUS_FAILURE_STORAGE
    6 -> FailureExplanation(
        reason = "存储相关的原因失败了，可能是分区只读或空间不足。",
        advice = "确认空间够用；只读分区上的包只能靠系统更新改动。",
    )

    // PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
    7 -> FailureExplanation(
        reason = "这个包与当前系统版本不兼容。",
        advice = "它属于另一个 Android 版本，本机不该动它。",
    )

    // PackageInstaller.STATUS_FAILURE_TIMEOUT
    8 -> FailureExplanation(
        reason = "系统超时没有回应。",
        advice = "刷新列表看它现在的状态；如果已经消失，这次其实成功了。",
    )

    // PackageInstaller.STATUS_PENDING_USER_ACTION
    -1 -> FailureExplanation(
        reason = "系统还在等用户在别处的确认。",
        advice = "看看通知栏或系统弹窗，处理掉那里的提示再回来。",
    )

    else -> FailureExplanation(
        reason = "设备返回了一个 Canta 不认识的失败码（$status）。",
        advice = "把这条记录的原文发到 GitHub issue，能补上这条映射。",
    )
}