package io.github.samolego.canta.data.history

/**
 * One batch that can be undone in a single tap: what it did, newest operation first.
 *
 * @param batchId when the batch started, epoch milliseconds.
 * @param packages the packages still affected by it, in the reverse of the order the batch took
 *   them. A batch takes the apps one after another, so undoing it walks back the same way.
 * @param kind what the batch did: an uninstall batch is put back, a freeze batch is thawed.
 */
data class BatchRollback(
    val batchId: Long,
    val packages: List<String>,
    val kind: OperationKind = OperationKind.UNINSTALL,
) {
    val size: Int get() = packages.size

    /** The operation that undoes this batch: a removal is put back, a freeze is unfrozen. */
    val undoKind: OperationKind
        get() = when (kind) {
            OperationKind.UNINSTALL -> OperationKind.REINSTALL
            OperationKind.DISABLE -> OperationKind.ENABLE
            OperationKind.REINSTALL -> OperationKind.UNINSTALL
            OperationKind.ENABLE -> OperationKind.DISABLE
        }
}

/**
 * Whether a whole batch of this kind is undone in one tap.
 *
 * Only the two that take something away: a removal can be put back and a freeze can be thawed,
 * while a batch that already gave the apps back has nothing left undone in one step.
 */
val OperationKind.undoableAsABatch: Boolean
    get() = this == OperationKind.UNINSTALL || this == OperationKind.DISABLE

/** Batches smaller than this are undone by tapping their own row, which is right there. */
private const val ROLLBACK_MIN_SIZE = 2

/**
 * The newest batch that is worth undoing in one tap, or null when there is none.
 *
 * A batch is one tap on the action button, so every record in it carries the same
 * [OperationKind] — only a removal or freeze batch can be rolled back, and only the parts of it
 * that are still in effect: an app the user already put back by hand must not be put back twice,
 * and one that was thawed by hand is not frozen any more.
 *
 * @param currentlyUninstalled whether a package is gone right now; the records alone cannot say,
 *   since a later operation on the same package may have brought it back.
 * @param currentlyDisabled whether a package is frozen right now, for the same reason. Read only
 *   for freeze batches, so a caller that has no such notion can leave it out.
 */
fun List<OperationRecord>.rollbackCandidate(
    currentlyUninstalled: (String) -> Boolean,
    currentlyDisabled: (String) -> Boolean = { false },
): BatchRollback? {
    // The log is newest first, so the first undoable batch met is the newest one of any kind.
    val batch = groupBy { it.batchId }
        .entries
        .firstOrNull { (_, records) ->
            val kind = records.firstOrNull()?.kind ?: return@firstOrNull false
            kind.undoableAsABatch && records.all { it.kind == kind }
        }
        ?: return null
    val kind = batch.value.first().kind
    val stillInEffect = batch.value
        .filter { record ->
            record.success && when (kind) {
                OperationKind.UNINSTALL -> currentlyUninstalled(record.packageName)
                OperationKind.DISABLE -> currentlyDisabled(record.packageName)
                else -> false
            }
        }
        .sortedByDescending { it.batchIndex }
        .map { it.packageName }
        .distinct()
    if (stillInEffect.size < ROLLBACK_MIN_SIZE) return null
    return BatchRollback(batchId = batch.key, packages = stillInEffect, kind = kind)
}