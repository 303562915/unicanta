package io.github.samolego.canta.data.history

/**
 * One batch that can be undone in a single tap: what it removed, newest removal first.
 *
 * @param batchId when the batch started, epoch milliseconds.
 * @param packages the packages still gone from it, in the reverse of the order they were removed.
 *   A batch takes the apps one after another, so undoing it walks back the same way.
 */
data class BatchRollback(
    val batchId: Long,
    val packages: List<String>,
) {
    val size: Int get() = packages.size
}

/** Batches smaller than this are undone by tapping their own row, which is right there. */
private const val ROLLBACK_MIN_SIZE = 2

/**
 * The newest batch that is worth undoing in one tap, or null when there is none.
 *
 * A batch is one tap on the action button, so every record in it carries the same
 * [OperationKind] — only an uninstall batch can be rolled back, and only the parts of it that are
 * still gone: an app the user already put back by hand must not be put back twice.
 *
 * @param currentlyUninstalled whether a package is gone right now; the records alone cannot say,
 *   since a later operation on the same package may have brought it back.
 */
fun List<OperationRecord>.rollbackCandidate(
    currentlyUninstalled: (String) -> Boolean,
): BatchRollback? {
    // The log is newest first, so the first uninstall batch met is the newest one.
    val batch = groupBy { it.batchId }
        .entries
        .firstOrNull { (_, records) -> records.all { it.kind == OperationKind.UNINSTALL } }
        ?: return null
    val stillGone = batch.value
        .filter { it.success && currentlyUninstalled(it.packageName) }
        .sortedByDescending { it.batchIndex }
        .map { it.packageName }
        .distinct()
    if (stillGone.size < ROLLBACK_MIN_SIZE) return null
    return BatchRollback(batchId = batch.key, packages = stillGone)
}