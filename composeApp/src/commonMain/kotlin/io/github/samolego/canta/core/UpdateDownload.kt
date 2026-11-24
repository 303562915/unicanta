package io.github.samolego.canta.core

/**
 * Why an APK download came back empty-handed.
 *
 * The updater needs this as a value, not as a null: "the file did not arrive" is the state the
 * about page has to turn into an action, and "it is still 0%" is what a download that failed
 * silently looks like. Every reason below has its own sentence and its own fallback.
 */
enum class UpdateDownloadFailure {
    /** The transfer never delivered a usable answer: DNS, a refused connection, TLS, a dead node. */
    NETWORK,

    /** Nothing moved for long enough that the transfer is treated as stalled, not slow. */
    TIMEOUT,

    /** The bytes could not be written: no space, a directory the app cannot write to. */
    STORAGE,

    /** This build has no downloader at all (web, desktop, tests); the browser is the only way. */
    UNSUPPORTED,

    /** Anything else. */
    UNKNOWN,
}

/** What [CantaPlatform.downloadUpdate] ended with: the file on disk, or the reason it is not there. */
sealed interface UpdateDownload {
    /** [bytes] is the size that landed, 0 when the platform could not say; the report carries it. */
    data class Done(val path: String, val bytes: Long = 0L) : UpdateDownload

    data class Failed(val failure: UpdateDownloadFailure) : UpdateDownload
}
