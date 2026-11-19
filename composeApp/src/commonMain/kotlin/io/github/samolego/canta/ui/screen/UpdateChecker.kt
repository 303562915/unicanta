package io.github.samolego.canta.ui.screen

import io.github.samolego.canta.core.CantaPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

/** GitHub rejects anonymous API calls without one. */
private const val USER_AGENT = "Canta-Android (+$UPDATE_REPO_URL)"

/** What the last check said, and what happened to the package it found. */
internal sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Latest : UpdateState
    data class Available(val version: String) : UpdateState
    data object Failed : UpdateState

    /** The APK is being fetched; [progress] is 0-100. */
    data class Downloading(val version: String, val progress: Int) : UpdateState

    /** Downloaded and waiting for the user to allow the install. */
    data class ReadyToInstall(val version: String, val path: String) : UpdateState

    /** `pm install` is running for the package at [path]. */
    data class Installing(val version: String, val path: String) : UpdateState

    /** The download or the install did not go through; [reason] is what to show. */
    data class FailedWith(val reason: String) : UpdateState
}

/** `tag_name` of the latest release, e.g. `v4.2.0`; throws when GitHub cannot be reached. */
internal suspend fun latestRelease(client: HttpClient): String {
    val body = client.get(LATEST_RELEASE_API) {
        header(HttpHeaders.UserAgent, USER_AGENT)
        header(HttpHeaders.Accept, "application/vnd.github+json")
    }.bodyAsText()
    return Json.parseToJsonElement(body)
        .jsonObject["tag_name"]
        ?.jsonPrimitive
        ?.content
        ?.takeIf { it.isNotBlank() }
        ?: error("no tag in the release payload")
}

/**
 * Where the APK of [tag] lives. Published releases carry exactly one APK, named after the
 * module, so the URL is stable enough to build without asking the API for the asset list.
 */
internal fun apkUrl(tag: String): String =
    "$UPDATE_REPO_URL/releases/download/$tag/androidApp-release.apk"

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
    onProgress: (Int) -> Unit,
): String? = platform.downloadUpdate(apkUrl(tag), "canta-$tag.apk", onProgress)
