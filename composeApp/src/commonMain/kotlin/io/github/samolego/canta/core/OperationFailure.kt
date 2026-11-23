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
        // The package service handed back no installer at all, so the call never left the app.
        // Seen on One UI 8 (33 of the first 58 reports): it arrives as STATUS_NO_RESULT too, and
        // "the device never answered" hides that nothing was even attempted.
        mentions("ipackageinstaller") ->
            FailureExplanation(
                reason = "系统没有把包安装器交给 Canta，这次操作没有执行。",
                advice = "重启一次 Shizuku 再试；一直这样的话改用「停用」，这台 ROM 多半只允许冻结、不允许卸载。",
            )

        // Shizuku is gone: the app thought the runtime was still there, but the call never left
        // this process. It reads as "no result" (STATUS_NO_RESULT) and used to be explained as
        // "the device never answered", which sent people to refresh a list that was perfectly fine.
        // Nothing on the phone was touched, and only starting the runtime again can change that.
        mentions("binder haven't been received", "binder haven't", "shizuku binder") ->
            FailureExplanation(
                reason = "Shizuku 服务已经断开，这次操作根本没送到手机上。",
                advice = "把 Shizuku 重新启动（手机重启、或系统清理后台之后它不会自己起来），" +
                    "等 Canta 显示已授权再试一次。用无线调试启动的，需要重新 adb 连接后再启动它。",
            )

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

        // The lookup before the call found nothing: on a system package the first uninstall removes
        // the whole registration, so a second tap on a stale row reports this instead of a success.
        mentions("unknown package") ->
            FailureExplanation(
                reason = "设备上已经找不到这个包了：多半上一次已经卸掉，或者系统把它清掉了。",
                advice = "下拉刷新一次列表再决定要不要重试；它不影响同一批里的其它应用。",
            )

        // vivo's own restriction, applied to dozens of its system packages: the user is not allowed
        // to remove this one at all, whatever the caller's identity is.
        mentions("user_restricted", "user restricted") ->
            FailureExplanation(
                reason = "这台 ROM 把这个包标成了受限应用，shell 身份删不掉它。",
                advice = "只有 root 能试；没有 root 就改用「停用」，或把它留在原地——它不占什么资源。",
            )

        // Android's own protection list (`canPackageBeChanged`), checked before anything runs: no
        // shell — and therefore no Shizuku route — can change these, whoever asks. Seen on packages
        // a vendor preloads as essential (an IME, a launcher, the store that ships with the build).
        mentions("cannot disable a protected package", "cannot uninstall a protected package") ->
            FailureExplanation(
                reason = "这是 Android 的保护包（设备所有者、设备锁或关键系统组件），shell 身份改不了它的状态。",
                advice = "只有 root 或设备所有者能试；没有 root 就别管它，这个包一般不影响日常使用。",
            )

        // The ROM's own rule, not Android's: Xiaomi and a few others refuse this for every system
        // package, so the advice is about the route rather than about this one package.
        mentions("cannot disable system packages") ->
            FailureExplanation(
                reason = "这台 ROM 自己禁止停用系统应用，跟是哪个包无关。",
                advice = "换 root 身份再试；没有 root 就用「卸载」，或者到安全规则里把它标成禁区。",
            )

        mentions("no root permission") ->
            FailureExplanation(
                reason = "这个操作要 root 身份，Shizuku 的 shell 权限不够。",
                advice = "到「设置 → 权限来源」里切到 root（Magisk / KernelSU / APatch）再试一次。",
            )

        // The framework will not even name a package setting to a shell ("for null"): that is the
        // device-owner / device-lock tier, one step above the protected list above.
        mentions("cannot change component state") ->
            FailureExplanation(
                reason = "这个包在设备所有者 / 设备锁这一层受保护，系统连它的状态都不给 shell 看。",
                advice = "只有 root 能改；没有 root 的话它会保持现在的状态，不影响日常使用。",
            )

        // The Dhizuku route deletes through the package installer's device-owner path; a refusal
        // there means the owner role or the app's own policy said no, not that the route is blind.
        mentions("dhizuku is not authorized") ->
            FailureExplanation(
                reason = "Dhizuku 没有授权给 UniCanta，这次操作没有执行。",
                advice = "打开 Dhizuku 允许 UniCanta，或者到「设置 → 权限来源」里点重新检测。",
            )

        // Restoring an app is the one write this route cannot do: it asks for INSTALL_PACKAGES,
        // which the device owner role does not carry.
        mentions("dhizuku route cannot install") ->
            FailureExplanation(
                reason = "Dhizuku 不能把应用装回来：设备所有者权限里没有安装权限。",
                advice = "恢复应用请把「设置 → 权限来源」切到 Shizuku 或 root。",
            )

        // The freeze half was refused for this package: a policy call the owner cannot make.
        mentions("dhizuku refused") ->
            FailureExplanation(
                reason = "Dhizuku（设备所有者）没有接受这次暂停。",
                advice = "这个包多半受系统保护；先取消它的设备管理员身份，或改用 Shizuku / root 处理。",
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