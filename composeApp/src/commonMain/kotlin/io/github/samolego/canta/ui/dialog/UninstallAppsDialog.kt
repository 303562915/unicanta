package io.github.samolego.canta.ui.dialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.samolego.canta.generated.resources.Res
import io.github.samolego.canta.generated.resources.are_you_sure_to_uninstall_apps
import io.github.samolego.canta.generated.resources.cancel
import io.github.samolego.canta.generated.resources.community_bootloop_warning
import io.github.samolego.canta.generated.resources.ok
import io.github.samolego.canta.generated.resources.records_action_disable
import io.github.samolego.canta.generated.resources.reset_to_factory_version
import io.github.samolego.canta.generated.resources.uninstall_expert_level_warning
import io.github.samolego.canta.generated.resources.uninstall_huge_batch_warning
import io.github.samolego.canta.generated.resources.uninstall_large_batch_warning
import io.github.samolego.canta.generated.resources.uninstall_new_packages
import io.github.samolego.canta.generated.resources.uninstall_new_packages_more
import io.github.samolego.canta.generated.resources.uninstall_or_freeze_hint
import io.github.samolego.canta.generated.resources.uninstall_recovery_hint
import io.github.samolego.canta.generated.resources.uninstall_unsafe_level_warning
import io.github.samolego.canta.ui.component.CantaDialog
import io.github.samolego.canta.ui.component.CheckboxRow
import io.github.samolego.canta.util.isLargeBatch
import org.jetbrains.compose.resources.stringResource

/** Most newly added package names listed in the dialog; the rest is summarised. */
private const val MAX_SHOWN_NEW = 8

/**
 * Past this many packages the dialog stops talking about "a large batch" and says what actually
 * happens: a phone that does not come back, and no way to tell which of them did it.
 */
private const val HUGE_BATCH = 100

@Composable
fun UninstallAppsDialog(
    appCount: Int,
    canResetToFactory: Boolean = false,
    bootloopRisk: Boolean = false,
    /** 名单把这批里的几个包标成了「不安全」（可能破坏系统关键部分、有卡在开机的风险）。 */
    unsafeLevelCount: Int = 0,
    /** 名单把这批里的几个包标成了「专家」（会破坏常用功能，但一般不会卡在开机）。 */
    expertLevelCount: Int = 0,
    newPackages: List<String> = emptyList(),
    /** Whether this device can freeze apps; the reversible way out is hidden when it cannot. */
    canFreeze: Boolean = false,
    onDismiss: () -> Unit,
    onAgree: (resetToFactory: Boolean) -> Unit,
    /** Freeze instead of removing: same packages, but everything stays on the device. */
    onFreeze: () -> Unit = {},
) {
    var resetToFactory by rememberSaveable { mutableStateOf(false) }
    val largeBatch = isLargeBatch(appCount)
    val hugeBatch = appCount >= HUGE_BATCH
    val shownNew = newPackages.take(MAX_SHOWN_NEW)

    CantaDialog(
        onDismissRequest = onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            // The two ways out are both on screen, and named after what they cost: freezing is
            // undoable without a copy of the app, removing is not.
            if (canFreeze) {
                TextButton(onClick = onFreeze) { Text(stringResource(Res.string.records_action_disable)) }
            }
            TextButton(onClick = { onAgree(resetToFactory) }) { Text(stringResource(Res.string.ok)) }
        },
    ) {
        Text(stringResource(Res.string.are_you_sure_to_uninstall_apps, appCount))
        if (canFreeze) {
            // Said plainly, because this is the one place the user chooses between a pause and a
            // removal, and the difference is not visible afterwards.
            Text(
                text = stringResource(Res.string.uninstall_or_freeze_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (bootloopRisk) {
            Text(
                text = stringResource(Res.string.community_bootloop_warning),
                color = MaterialTheme.colorScheme.error,
            )
        }
        // 名单自己的判定，和上面那条社区报告分开说：社区那条是"有人删了开不了机"，
        // 这两条是"名单在删之前就标着不安全/专家"。一个包可能两者都中，也可能只有其一。
        if (unsafeLevelCount > 0) {
            Text(
                text = stringResource(Res.string.uninstall_unsafe_level_warning, unsafeLevelCount),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (expertLevelCount > 0) {
            Text(
                text = stringResource(Res.string.uninstall_expert_level_warning, expertLevelCount),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (newPackages.isNotEmpty()) {
            // A batch pasted from a shared list looks like any other: name what was just added.
            Text(
                text = stringResource(Res.string.uninstall_new_packages, shownNew.joinToString(", ")),
                color = MaterialTheme.colorScheme.error,
            )
            if (newPackages.size > shownNew.size) {
                Text(stringResource(Res.string.uninstall_new_packages_more, newPackages.size - shownNew.size))
            }
        }
        if (hugeBatch) {
            Text(
                text = stringResource(Res.string.uninstall_huge_batch_warning, appCount),
                color = MaterialTheme.colorScheme.error,
            )
        } else if (largeBatch) {
            Text(stringResource(Res.string.uninstall_large_batch_warning))
        }
        // A single app from the list is routine, so the reminder stays out of the way; the moment a
        // batch is large or a bootloop is already on record, it is the most useful thing on screen.
        if (largeBatch || bootloopRisk) {
            Text(
                text = stringResource(Res.string.uninstall_recovery_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canResetToFactory) {
            CheckboxRow(
                checked = resetToFactory,
                onCheckedChange = { resetToFactory = it },
                label = stringResource(Res.string.reset_to_factory_version),
            )
        }
    }
}
