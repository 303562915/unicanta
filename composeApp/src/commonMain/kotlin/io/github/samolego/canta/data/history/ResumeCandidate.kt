package io.github.samolego.canta.data.history

/**
 * What is left of a batch that was cut short, or null when nothing is.
 *
 * A batch runs app after app through the device; if the app dies, the device is unplugged, or the
 * user leaves, the packages after the last answer were never attempted and the selection that
 * named them is gone with the screen. The batch is remembered when it starts, and this works out
 * what it never reached.
 *
 * Finishing it means repeating the same operation on the rest, so the batch's [kind] travels with
 * the candidate: resuming a freeze batch as an uninstall would delete the apps instead of thawing
 * them, which is the one mistake this class exists to prevent.
 *
 * @param packages the apps still to do, in the order the batch would have taken them.
 * @param startedAt when the batch began, epoch milliseconds.
 * @param kind what the batch was doing. Batches remembered by versions that only knew about
 *   uninstalls have no such field, so the default is [OperationKind.UNINSTALL]: that is exactly
 *   what those versions were in the middle of, and it is the migration rule for old stored data.
 */
data class ResumeCandidate(
    val packages: List<String>,
    val startedAt: Long,
    val kind: OperationKind = OperationKind.UNINSTALL,
) {
    val size: Int get() = packages.size

    /** The operation that finishes this batch: the same one it was running when it stopped. */
    val finishKind: OperationKind get() = kind
}

/**
 * Whether a batch of this kind is remembered while it runs, so a cut-short one can be finished
 * later.
 *
 * Uninstalling, freezing and thawing walk the whole selection app after app with an answer per
 * app, and each answer is what says the app is done. Putting apps back is not remembered: that
 * batch is the way out of a rescue, and offering "continue putting apps back" a day later would be
 * a prompt the user cannot act on.
 */
val OperationKind.resumable: Boolean
    get() = when (this) {
        OperationKind.UNINSTALL, OperationKind.DISABLE, OperationKind.ENABLE -> true
        OperationKind.REINSTALL -> false
    }

/**
 * The packages of an interrupted batch that never got an answer.
 *
 * @param interrupted the list stored when the batch started.
 * @param startedAt the batch's id, which is also when it started.
 * @param records the operation log; every package the batch reached has a line under [startedAt],
 *   successful or not — a refusal is an answer, so it is not retried either.
 * @param kind what the batch was doing; an uninstall when the stored batch says nothing, which is
 *   what data written before batches had kinds says.
 */
fun resumeCandidate(
    interrupted: List<String>,
    startedAt: Long,
    records: List<OperationRecord>,
    kind: OperationKind = OperationKind.UNINSTALL,
): ResumeCandidate? {
    if (interrupted.isEmpty() || startedAt <= 0) return null
    // A batch of a kind that is never remembered cannot be resumed: a stored one of those is
    // stale data, not work left over.
    if (!kind.resumable) return null
    val answered = records
        .filter { it.batchId == startedAt }
        .map { it.packageName }
        .toSet()
    val left = interrupted.filterNot { it in answered }
    if (left.isEmpty()) return null
    return ResumeCandidate(packages = left, startedAt = startedAt, kind = kind)
}