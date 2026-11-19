package io.github.samolego.canta.data.community

import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.TelemetryConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** One community plan: a set of packages someone removed and confirmed by a reboot. */
data class CommunityPlan(
    val id: Long,
    val title: String,
    val note: String,
    val packages: List<String>,
    val votes: Int,
    val comments: Int,
    val createdAt: String,
    val rebootedAt: String,
    /** Identity of the uploader; only that identity may delete the plan. */
    val owner: String = "",
    /** True for a plan uploaded with the test password: no reboot proof behind it. */
    val isTest: Boolean = false,
)

/** Id of the local "test upload" row: never a server id, so it can be told apart. */
const val TEST_PLAN_ID: Long = -1L

/** A comment under a plan. */
data class PlanComment(val author: String, val body: String, val createdAt: String)

/**
 * An identity as the backend knows it: the token it resolves to, the number it owns, and the
 * display name the user picked. [name] is empty until they rename themselves, in which case the
 * install code is shown instead.
 */
data class UserProfile(val token: String, val userId: Long, val name: String = "")

/**
 * Reads and writes the community plans for the caller's own device. Matching is exact on model,
 * codename, ROM and ROM version: a plan for another build is worthless and potentially harmful,
 * so it is never shown.
 */
class PlanRepository(
    private val client: HttpClient,
    private val baseUrl: String = TelemetryConfig.BASE_URL,
    private val apiKey: String = TelemetryConfig.PUBLISHABLE_KEY,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun rpc(name: String, body: String): String? = try {
        val response = client.post(baseUrl + name) {
            header("apikey", apiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body)
        }
        if (response.status.isSuccess()) response.bodyAsText() else null
    } catch (e: Exception) {
        null
    }

    /** Verified plans for [profile], most liked first; empty on any failure. */
    suspend fun plansFor(profile: DeviceProfile?): List<CommunityPlan> {
        profile ?: return emptyList()
        val body = buildJsonObject {
            put("p_model", profile.model)
            put("p_codename", profile.device)
            put("p_rom", profile.rom)
            put("p_rom_version", profile.romVersion)
        }.toString()
        val text = rpc("plans_for", body) ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(text).jsonArray.map { element ->
                val o = element.jsonObject
                CommunityPlan(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: 0,
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    note = o["note"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    packages = (o["packages"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                    votes = o["votes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                    comments = o["comments"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                    createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    rebootedAt = o["rebooted_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    owner = o["owner"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    isTest = o["is_test"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Uploads [packages] as a plan.
     *
     * A normal upload is only ever called with a verified reboot. A [test] upload comes from the
     * test password: it still sends a reboot time (the server rejects anything else) but is
     * stored flagged as a test, so the list can label it for what it is.
     */
    suspend fun submit(
        profile: DeviceProfile,
        title: String,
        note: String,
        packages: List<String>,
        rebootedAt: String,
        reporter: String,
        owner: String = "",
        test: Boolean = false,
    ): Long? {
        if (packages.isEmpty()) return null
        val body = buildJsonObject {
            put("brand", profile.brand)
            put("model", profile.model)
            put("codename", profile.device)
            put("rom", profile.rom)
            put("rom_version", profile.romVersion)
            put("sdk", profile.sdk)
            put("title", title)
            put("note", note)
            putJsonArray("packages") { packages.forEach { add(it) } }
            put("reboot_verified", true)
            put("rebooted_at", rebootedAt)
            put("reporter", reporter)
            put("owner", owner)
            put("test", test)
        }.toString()
        return rpc("plan_submit", buildJsonObject { put("p_plan", Json.parseToJsonElement(body)) }.toString())
            ?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.longOrNull }.getOrNull() }
    }

    /**
     * Deletes a plan uploaded by [owner]. The server needs the [token] of that identity as well,
     * so this returns false for somebody else's plan — or for a guessed number — instead of
     * doing anything.
     */
    suspend fun delete(planId: Long, owner: String, token: String): Boolean {
        if (owner.isEmpty() || token.isEmpty() || planId <= 0) return false
        val body = buildJsonObject {
            put("p_id", planId)
            put("p_owner", owner)
            put("p_token", token)
        }.toString()
        return rpc("plan_delete", body)
            ?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.booleanOrNull }.getOrNull() } == true
    }

    /**
     * This install's token, number and display name, creating the row on first use.
     *
     * [deviceKey] is the phone's stable lookup key (see `CantaPlatform.deviceKey`). When that phone
     * already bound a token — i.e. the app was reinstalled — the backend answers with the old one,
     * so the identity, its number and its name survive the reinstall. The returned [UserProfile.token]
     * is therefore the token the caller must use from now on, which may differ from [token].
     *
     * Null when the backend cannot be reached; the caller then keeps its local token.
     */
    suspend fun identify(token: String, deviceKey: String = ""): UserProfile? {
        if (token.isEmpty()) return null
        val body = buildJsonObject {
            put("p_token", token)
            put("p_device_key", deviceKey)
        }.toString()
        val text = rpc("identity_bind", body) ?: return null
        return parseProfile(text)
    }

    /**
     * This install's number and display name, creating the row on first use. The name is the one
     * the user picked in Settings; it is empty until they do, and the install code shows instead.
     * Null when the backend cannot be reached.
     */
    suspend fun profile(token: String): UserProfile? {
        if (token.isEmpty()) return null
        val text = rpc("profile_get", buildJsonObject { put("p_token", token) }.toString()) ?: return null
        return parseProfile(text)
    }

    /** Stores [name] for [token] and answers what the backend kept; null on failure. */
    suspend fun setName(token: String, name: String): UserProfile? {
        if (token.isEmpty()) return null
        val body = buildJsonObject {
            put("p_token", token)
            put("p_name", name)
        }.toString()
        val text = rpc("profile_set_name", body) ?: return null
        return parseProfile(text)
    }

    private fun parseProfile(text: String): UserProfile? = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject
        val id = o["id"]?.jsonPrimitive?.longOrNull ?: return null
        if (id <= 0) return null
        UserProfile(
            token = o["token"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            userId = id,
            name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }.getOrNull()

    /**
     * The number this [token] owns, creating it on first use: 10000 for the first install, then
     * one up. Returns null when the backend cannot be reached. The token is created locally and
     * never leaves the device except in these calls.
     */
    suspend fun signIn(token: String, profile: DeviceProfile? = null): Long? {
        if (token.isEmpty()) return null
        val body = buildJsonObject {
            put("p_token", token)
            put("p_model", profile?.model.orEmpty())
            put("p_codename", profile?.device.orEmpty())
        }.toString()
        return rpc("user_login", body)
            ?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.longOrNull }.getOrNull() }
            ?.takeIf { it > 0 }
    }

    suspend fun vote(planId: Long, voter: String): Int? {
        val body = buildJsonObject {
            put("p_id", planId)
            put("p_voter", voter)
        }.toString()
        return rpc("plan_vote", body)?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.longOrNull?.toInt() }.getOrNull() }
    }

    suspend fun comments(planId: Long): List<PlanComment> {
        val body = buildJsonObject { put("p_id", planId) }.toString()
        val text = rpc("plan_comments_for", body) ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(text).jsonArray.map {
                val o = it.jsonObject
                PlanComment(
                    author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun comment(planId: Long, body: String, author: String): Boolean {
        val payload = buildJsonObject {
            put("p_id", planId)
            put("p_body", body)
            put("p_author", author)
        }.toString()
        return rpc("plan_comment", payload) != null
    }
}

@Suppress("unused")
private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

@Suppress("unused")
private fun JsonObject.num(key: String): Double = this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
