package io.github.samolego.canta.data.history

/**
 * What is left of an uninstall batch that was cut short, or null when nothing is.
 *
 * A batch runs app after app through `PackageInstaller`; if the app dies, the device is
 * unplugged, or the user leaves, the packages after the last answer were never attempted and the
 * selection that named them is gone with the screen. The batch is remembered when it starts, and
 * this works out what it never reached.
 *
 * @param packages the apps still to do, in the order the batch would have taken them.
 * @param startedAt when the batch began, epoch milliseconds.
 */
data class ResumeCandidate(
    val packages: List<String>,
    val startedAt: Long,
) {
    val size: Int get() = packages.size
}

/**
 * The packages of an interrupted batch that never got an answer.
 *
 * @param interrupted the list stored when the batch started.
 * @param startedAt the batch's id, which is also when it started.
 * @param records the operation log; every package the batch reached has a line under [startedAt],
 *   successful or not — a refusal is an answer, so it is not retried either.
 */
fun resumeCandidate(
    interrupted: List<String>,
    startedAt: Long,
    records: List<OperationRecord>,
): ResumeCandidate? {
    if (interrupted.isEmpty() || startedAt <= 0) return null
    val answered = records
        .filter { it.batchId == startedAt }
        .map { it.packageName }
        .toSet()
    val left = interrupted.filterNot { it in answered }
    if (left.isEmpty()) return null
    return ResumeCandidate(packages = left, startedAt = startedAt)
}