package io.github.samolego.canta.util

import io.github.samolego.canta.data.CantaSettings
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/**
 * The password that turns an upload into a test upload: no reboot verification.
 *
 * It is deliberately a constant in the app, not a server secret: the point is to be able to try
 * the plan flow (and upload a draft) on a phone that has not been rebooted yet. Every plan
 * uploaded this way is stored and shown with the "test" mark, so nobody mistakes it for a scheme
 * that was proven to boot.
 */
const val TEST_UPLOAD_PASSWORD: String = "369369"

/**
 * Who this install is, without an account.
 *
 * The first time the plans page is opened a random token is generated and written to the settings;
 * from then on that token is the identity — the owner of a plan (so it can be deleted again), the
 * voter (one vote per phone) and the comment author. Nothing is asked from the user, nothing is
 * derived from the hardware, and the code shown on screen is only the readable head of the token.
 */
object DeviceIdentity {

    /** Length of [newToken]; 32 hex characters, i.e. 128 random bits. */
    const val TOKEN_LENGTH: Int = 32

    /** Enough of the token to be recognisable, not enough to be the whole key. */
    private const val CODE_CHARS: Int = 10

    /** A fresh random token; injectable random so the format can be tested. */
    fun newToken(random: Random = Random.Default): String =
        buildString(TOKEN_LENGTH) {
            while (length < TOKEN_LENGTH) {
                append(random.nextInt().toUInt().toString(16).padStart(8, '0'))
            }
        }

    /** True when [input] is the test password; surrounding whitespace is forgiven. */
    fun isTestPassword(input: String): Boolean = input.trim() == TEST_UPLOAD_PASSWORD

    /** "A1B2C-3D4E5": stable, quotable, and short enough for a one-line footer. */
    fun codeOf(token: String): String {
        val clean = token.filter { it.isLetterOrDigit() }.uppercase()
        if (clean.isEmpty()) return "—"
        return clean.take(CODE_CHARS).padEnd(CODE_CHARS, '0').chunked(5).joinToString("-")
    }

    /** The value the backend stores as owner/voter/author for this install. */
    fun ownerOf(token: String): String = if (token.isEmpty()) "" else "dev-$token"

    /** Stored token if there is one, a freshly generated and stored one otherwise. */
    suspend fun ensure(settings: CantaSettings): String {
        val stored = settings.identityTokenFlow.first()
        if (stored.isNotEmpty()) return stored
        val token = newToken()
        settings.setIdentityToken(token)
        return token
    }
}
