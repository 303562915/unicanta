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
 * The phone keeps a random token in its settings and asks the backend which row that token owns;
 * the backend answers with a number that starts at [FIRST_USER_ID] and only ever goes up. That
 * number is the identity from then on — the owner of the plans (so they can be deleted again), the
 * voter (one vote per phone) and the comment author — and it is stored locally, so opening the
 * page again is a read, not a login.
 *
 * The number is not a secret (it is shown on screen); the token is, and deleting a plan needs
 * both: the number says which identity, the token proves it.
 */
object DeviceIdentity {

    /** First number the backend hands out; the sequence lives in the database. */
    const val FIRST_USER_ID: Long = 10_000L

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

    /** "A1B2C-3D4E5": the token's readable head, used before a number is known. */
    fun codeOf(token: String): String {
        val clean = token.filter { it.isLetterOrDigit() }.uppercase()
        if (clean.isEmpty()) return "—"
        return clean.take(CODE_CHARS).padEnd(CODE_CHARS, '0').chunked(5).joinToString("-")
    }

    /** "10000": what a plan stores as its owner. */
    fun ownerOf(userId: Long): String = if (userId > 0) userId.toString() else ""

    /** "#10000": what the user sees. */
    fun codeOfUser(userId: Long): String = if (userId > 0) "#$userId" else "—"

    /** Stored token if there is one, a freshly generated and stored one otherwise. */
    suspend fun ensure(settings: CantaSettings): String {
        val stored = settings.identityTokenFlow.first()
        if (stored.isNotEmpty()) return stored
        val token = newToken()
        settings.setIdentityToken(token)
        return token
    }

    /** Token plus the number it owns, read from the settings (0 while not signed in). */
    suspend fun current(settings: CantaSettings): Identity {
        val token = ensure(settings)
        return Identity(token, settings.identityUserIdFlow.first())
    }

    /**
     * What a review or a comment is signed with: the name the user picked in Settings, or the
     * install code when they have not picked one. The avatar next to it is its first character.
     */
    suspend fun displayName(settings: CantaSettings): String {
        val name = settings.userNameFlow.first().trim()
        return name.ifEmpty { current(settings).code }
    }
}

/** This install: the secret token, and the number the backend gave it. */
data class Identity(val token: String, val userId: Long = 0L) {

    /** True once the backend has assigned a number. */
    val known: Boolean get() = userId > 0

    /** What a plan stores as its owner; empty while unknown. */
    val owner: String get() = if (userId > 0) userId.toString() else ""

    /** "#10000", or the token's head while the number is not known yet. */
    val code: String get() = if (userId > 0) "#$userId" else DeviceIdentity.codeOf(token)
}
