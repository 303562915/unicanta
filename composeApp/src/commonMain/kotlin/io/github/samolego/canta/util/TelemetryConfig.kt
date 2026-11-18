package io.github.samolego.canta.util

import io.github.samolego.canta.TelemetryEndpoints

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/** Constants for the opt-in package-safety telemetry (see [Telemetry]). */
object TelemetryConfig {
    /** Generated from the build: `-Pcanta.telemetry.baseUrl=...` points a build at another backend. */
    val BASE_URL: String = TelemetryEndpoints.BASE_URL

    /** Publishable/anon key of that backend; meant to be public. */
    val PUBLISHABLE_KEY: String = TelemetryEndpoints.API_KEY

    /** Proof-of-work difficulty: about this many hashes per event. */
    const val POW_K = 10_000L

    /** Most events the server accepts per request. */
    const val MAX_EVENTS = 250

    /** Quiet time after the last queued event before the batch is sent. */
    val DEBOUNCE = 30.seconds

    /** Pending events older than this are dropped rather than sent. */
    val PENDING_MAX_AGE = 30.days
}
