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

internal enum class UpdateChannel { STABLE, DEVELOPMENT }

internal data class UpdateRelease(val tag: String, val version: String, val buildNumber: Int = 0)

/** GitHub rejects anonymous API calls without one. */
private const val USER_AGENT = "Canta-Android (+$UPDATE_REPO_URL)"

/** What the last check said, and what happened to the package it found. */
internal sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Latest : UpdateState
    data class Available(val version: String, val release: UpdateRelease) : UpdateState
    data object Failed : UpdateState

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

/** Reads the selected published release and its immutable build identity. */
internal suspend fun latestRelease(client: HttpClient, channel: UpdateChannel): UpdateRelease {
    val body = client.get(if (channel == UpdateChannel.DEVELOPMENT) DEVELOPMENT_RELEASE_API else LATEST_RELEASE_API) {
        header(HttpHeaders.UserAgent, USER_AGENT)
        header(HttpHeaders.Accept, "application/vnd.github+json")
    }.bodyAsText()
    val payload = Json.parseToJsonElement(body).jsonObject
    val tag = payload["tag_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: error("no tag in the release payload")
    if (channel == UpdateChannel.STABLE) return UpdateRelease(tag, tag)
    require(tag == DEVELOPMENT_TAG && payload["prerelease"]?.jsonPrimitive?.content == "true")
    val notes = payload["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
    fun field(name: String): String? = notes.lineSequence()
        .firstOrNull { it.startsWith("$name:") }
        ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
    val version = field("version")?.takeIf { Regex("[vV]?\\d+(\\.\\d+)+([+-][0-9A-Za-z.-]+)?").matches(it) }
        ?: error("no development version in release notes")
    val buildNumber = field("development-build")?.toIntOrNull()?.takeIf { it > 0 }
        ?: error("no development build number in release notes")
    return UpdateRelease(tag, version, buildNumber)
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
