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
     * Deletes a plan uploaded by [owner]. The server only deletes when the owner matches, so
     * this returns false for somebody else's plan instead of doing anything.
     */
    suspend fun delete(planId: Long, owner: String): Boolean {
        if (owner.isEmpty() || planId <= 0) return false
        val body = buildJsonObject {
            put("p_id", planId)
            put("p_owner", owner)
        }.toString()
        return rpc("plan_delete", body)
            ?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.booleanOrNull }.getOrNull() } == true
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
