package io.github.samolego.canta.core

/** Why a card on the tools page could not run its command. */
enum class TweakFailure {
    /** This build has no privileged shell at all (desktop or web without a device). */
    Unsupported,

    /** The privileged runtime is installed but not running, or Canta is not authorized. */
    NoPrivilege,

    /** The privileged shell never answered. */
    Timeout,

    /** The privileged shell answered with an error instead of a result. */
    Broken,
}

/**
 * What running a tools page command did.
 *
 * @param ok the command ran to completion and exited 0.
 * @param output everything it printed, stdout and stderr interleaved, shown as-is in the dialog.
 * @param failure set when the command never got to run; [ok] is false and [output] empty then.
 */
data class TweakRun(
    val ok: Boolean,
    val output: String = "",
    val failure: TweakFailure? = null,
)
