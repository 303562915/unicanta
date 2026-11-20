package io.github.samolego.canta.ui.screen

import io.github.samolego.canta.core.CantaPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import io.github.samolego.canta.BuildInfo

/*
 * Everything the app needs to know about releases: where they are, whether one is newer than
 * this build, and how to fetch it. The about page shows it step by step; the start-up check
 * uses the same functions so both paths can never disagree about what "newer" means.
 */

/** Where the app is published. */
internal const val UPDATE_REPO_URL = "https://github.com/303562915/unicanta"

/** The download page published from `site/`; it always shows the newest published release. */
internal const val DOWNLOAD_PAGE_URL = "https://303562915.github.io/unicanta/"

private const val LATEST_RELEASE_API = "https://api.github.com/repos/303562915/unicanta/releases/latest"
private const val DEVELOPMENT_RELEASE_API = "https://api.github.com/repos/303562915/unicanta/releases/tags/dev"
private const val DEVELOPMENT_TAG = "dev"

/**
 * The same two releases as plain pages. They carry no API quota, which matters on a phone behind a
 * shared proxy: GitHub counts anonymous API calls per exit IP, and a popular node burns the 60
 * requests an hour long before this app asks for anything.
 */
private const val LATEST_RELEASE_PAGE = "$UPDATE_REPO_URL/releases/latest"
private const val DEVELOPMENT_RELEASE_PAGE = "$UPDATE_REPO_URL/releases/tag/$DEVELOPMENT_TAG"

/** One try plus one retry: a flapping proxy often answers the second time. */
private const val CHECK_ATTEMPTS = 2
private const val RETRY_DELAY_MILLIS = 1_200L

/** A request that takes longer than this is treated as a broken proxy, not as a slow server. */
private const val CHECK_TIMEOUT_MILLIS = 12_000L

internal enum class UpdateChannel { STABLE, DEVELOPMENT }

internal data class UpdateRelease(val tag: String, val version: String, val buildNumber: Int = 0)

/**
 * Why a check came back empty-handed. The about page turns this into one sentence that names the
 * actual problem, because "check failed" leaves a user on a VPN guessing at their node.
 */
internal enum class UpdateFailure {
    /** GitHub answered 403/429: the anonymous quota of this exit IP is gone. */
    RATE_LIMITED,

    /** Nothing came back in time — the usual shape of a proxy that is up but not carrying data. */
    TIMEOUT,

    /** The request never reached GitHub: DNS, refused connection, TLS, a node that is simply dead. */
    NETWORK,

    /** GitHub answered 5xx. */
    SERVER,

    /** This channel has no published release yet (404). */
    NO_RELEASE,

    /** The answer was not release data — a captive portal or a hijacked response. */
    MALFORMED,

    /** Anything else. */
    UNKNOWN,
}

/** Thrown when a check cannot answer; [failure] says which of the above happened. */
internal class UpdateCheckException(val failure: UpdateFailure) : Exception(failure.name)

/** Whether asking again can plausibly help. A missing release or a rewritten answer cannot change. */
private fun UpdateFailure.isTransient(): Boolean = when (this) {
    UpdateFailure.RATE_LIMITED, UpdateFailure.TIMEOUT, UpdateFailure.NETWORK, UpdateFailure.SERVER -> true
    UpdateFailure.NO_RELEASE, UpdateFailure.MALFORMED, UpdateFailure.UNKNOWN -> false
}

/** GitHub rejects anonymous API calls without one. */
private const val USER_AGENT = "Canta-Android (+$UPDATE_REPO_URL)"

/** What the last check said, and what happened to the package it found. */
internal sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Latest : UpdateState
    data class Available(val version: String, val release: UpdateRelease) : UpdateState

    /** The check answered with a reason instead of a release; [failure] says which one. */
    data class Failed(val failure: UpdateFailure) : UpdateState

    /**
     * F-Droid installed this build, so the store owns updates: its builds are signed with its own
     * key, and an APK from the GitHub release cannot replace one of them.
     */
    data object StoreManaged : UpdateState

    /** The APK is being fetched; [progress] is 0-100. */
    data class Downloading(val version: String, val progress: Int) : UpdateState

    /** Downloaded and waiting for the user to allow the install. */
    data class ReadyToInstall(val version: String, val path: String) : UpdateState

    /** `pm install` is running for the package at [path]. */
    data class Installing(val version: String, val path: String) : UpdateState

    /** The privileged install failed, so the APK was handed to the system installer instead. */
    data class HandedOff(val version: String) : UpdateState

    /** The download or the install did not go through; [reason] is what to show. */
    data class FailedWith(val reason: String) : UpdateState
}

/**
 * Reads the selected published release and its immutable build identity.
 *
 * A flaky proxy is the normal case, not the exception: each attempt is bounded by
 * [CHECK_TIMEOUT_MILLIS], a transient failure is tried once more, and a rate-limited API call is
 * answered from the release page instead of counting against the same exhausted quota.
 */
internal suspend fun latestRelease(client: HttpClient, channel: UpdateChannel): UpdateRelease {
    var failure = UpdateFailure.UNKNOWN
    for (attempt in 0 until CHECK_ATTEMPTS) {
        try {
            return fetchRelease(client, channel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateCheckException) {
            failure = e.failure
        } catch (e: Exception) {
            failure = classifyFailure(e)
        }
        if (!failure.isTransient()) throw UpdateCheckException(failure)
        if (attempt < CHECK_ATTEMPTS - 1) delay(RETRY_DELAY_MILLIS)
    }
    throw UpdateCheckException(failure)
}

/** The API first; when its anonymous quota is gone, the release page answers the same question. */
private suspend fun fetchRelease(client: HttpClient, channel: UpdateChannel): UpdateRelease {
    try {
        return fetchFromApi(client, channel)
    } catch (e: UpdateCheckException) {
        if (e.failure != UpdateFailure.RATE_LIMITED) throw e
        return fetchFromPage(client, channel)
    }
}

private suspend fun fetchFromApi(client: HttpClient, channel: UpdateChannel): UpdateRelease {
    val url = if (channel == UpdateChannel.DEVELOPMENT) DEVELOPMENT_RELEASE_API else LATEST_RELEASE_API
    val body = requestText(client, url, "application/vnd.github+json")
    val payload = try {
        Json.parseToJsonElement(body).jsonObject
    } catch (e: Exception) {
        throw UpdateCheckException(UpdateFailure.MALFORMED)
    }
    val tag = payload["tag_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: throw UpdateCheckException(UpdateFailure.MALFORMED)
    if (channel == UpdateChannel.STABLE) return UpdateRelease(tag, tag)
    if (tag != DEVELOPMENT_TAG || payload["prerelease"]?.jsonPrimitive?.content != "true") {
        throw UpdateCheckException(UpdateFailure.MALFORMED)
    }
    val notes = payload["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
    fun field(name: String): String? = notes.lineSequence()
        .firstOrNull { it.startsWith("$name:") }
        ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
    val version = field("version")?.takeIf { Regex("[vV]?\\d+(\\.\\d+)+([+-][0-9A-Za-z.-]+)?").matches(it) }
        ?: throw UpdateCheckException(UpdateFailure.MALFORMED)
    val buildNumber = field("development-build")?.toIntOrNull()?.takeIf { it > 0 }
        ?: throw UpdateCheckException(UpdateFailure.MALFORMED)
    return UpdateRelease(tag, version, buildNumber)
}

/**
 * The same release read off its web page. The page is not rate limited, so this is what keeps a
 * check working on an IP whose API quota someone else already spent.
 */
private suspend fun fetchFromPage(client: HttpClient, channel: UpdateChannel): UpdateRelease {
    val page = requestText(
        client,
        if (channel == UpdateChannel.DEVELOPMENT) DEVELOPMENT_RELEASE_PAGE else LATEST_RELEASE_PAGE,
        "text/html",
    )
    // `/releases/latest` redirects to the newest tag, and the page links to it.
    val tag = Regex("/releases/tag/([^\"'#?<>\\s]+)").find(page)?.groupValues?.get(1)
        ?.takeIf { it.isNotBlank() && it != "latest" }
        ?: throw UpdateCheckException(UpdateFailure.MALFORMED)
    if (channel == UpdateChannel.STABLE) return UpdateRelease(tag, tag)
    // The development release body is the only place its version and build number live.
    val version = Regex("version:\\s*[vV]?(\\d+(?:\\.\\d+)+[0-9A-Za-z.+-]*)").find(page)
        ?.groupValues?.get(1)
        ?: throw UpdateCheckException(UpdateFailure.MALFORMED)
    val buildNumber = Regex("development-build:\\s*(\\d+)").find(page)
        ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
        ?: throw UpdateCheckException(UpdateFailure.MALFORMED)
    return UpdateRelease(DEVELOPMENT_TAG, version, buildNumber)
}

/**
 * One bounded GET; the status decides which failure the caller reports. The body is read inside
 * the timeout too: a proxy that answers the headers and then stalls is exactly the case this covers.
 */
private suspend fun requestText(client: HttpClient, url: String, accept: String): String {
    val (status, body) = try {
        withTimeoutOrNull(CHECK_TIMEOUT_MILLIS) {
            val response = client.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, accept)
            }
            response.status.value to response.bodyAsText()
        } ?: throw UpdateCheckException(UpdateFailure.TIMEOUT)
    } catch (e: CancellationException) {
        throw e
    } catch (e: UpdateCheckException) {
        throw e
    } catch (e: Exception) {
        throw UpdateCheckException(classifyFailure(e))
    }
    when {
        status == 403 || status == 429 -> throw UpdateCheckException(UpdateFailure.RATE_LIMITED)
        status == 404 -> throw UpdateCheckException(UpdateFailure.NO_RELEASE)
        status >= 500 -> throw UpdateCheckException(UpdateFailure.SERVER)
        status !in 200..299 -> throw UpdateCheckException(UpdateFailure.UNKNOWN)
    }
    return body
}

/**
 * Sorts a thrown network error into the same words the page shows. The exception types differ per
 * platform (Android throws `UnknownHostException`, the desktop build throws `IOException`, the web
 * build a JavaScript one), so the rendered exception — which carries its own class name — is what
 * means the same thing everywhere.
 */
private fun classifyFailure(e: Throwable): UpdateFailure {
    val text = e.toString().lowercase()
    return when {
        "timeout" in text || "timed out" in text -> UpdateFailure.TIMEOUT
        "unresolvedaddress" in text || "unknownhost" in text || "connect" in text ||
            "ioexception" in text || "socket" in text || "ssl" in text || "handshake" in text ||
            "refused" in text || "offline" in text || "network" in text -> UpdateFailure.NETWORK
        else -> UpdateFailure.UNKNOWN
    }
}

internal fun UpdateRelease.isNewerThanInstalled(): Boolean {
    if (isNewerVersion(version, BuildInfo.VERSION_NAME)) return true
    if (isNewerVersion(BuildInfo.VERSION_NAME, version)) return false
    return if (tag == DEVELOPMENT_TAG) buildNumber > BuildInfo.DEVELOPMENT_BUILD_NUMBER
    else BuildInfo.DEVELOPMENT_BUILD_NUMBER > 0
}

/**
 * Where the APK of [tag] lives. Published releases carry exactly one APK, named after the
 * module, so the URL is stable enough to build without asking the API for the asset list.
 */
internal fun apkUrl(tag: String, buildNumber: Int = 0): String {
    val asset = if (tag == DEVELOPMENT_TAG) "androidApp-release-$buildNumber.apk" else "androidApp-release.apk"
    return "$UPDATE_REPO_URL/releases/download/$tag/$asset"
}

/**
 * Whether [remote] (e.g. `v4.3.0`) is strictly newer than [local] (e.g. `4.2.0`).
 *
 * Compares the numeric parts only: a plain string compare would call `4.10.0` older than
 * `4.9.0`, and an equality check would offer an "update" to anyone running a local build
 * ahead of the last release.
 */
internal fun isNewerVersion(remote: String, local: String): Boolean {
    fun parts(version: String): List<Int> =
        version.trim()
            .trimStart('v', 'V')
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    val a = parts(remote)
    val b = parts(local)
    val length = maxOf(a.size, b.size)
    for (i in 0 until length) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/**
 * Downloads the release [tag] and answers with the file's path, or null when it could not be
 * fetched. [onProgress] receives 0-100.
 */
internal suspend fun downloadRelease(
    platform: CantaPlatform,
    tag: String,
    buildNumber: Int = 0,
    onProgress: (Int) -> Unit,
): String? = platform.downloadUpdate(apkUrl(tag, buildNumber), "canta-$tag-$buildNumber.apk", onProgress)
