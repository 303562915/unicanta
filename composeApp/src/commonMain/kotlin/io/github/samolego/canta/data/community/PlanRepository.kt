package io.github.samolego.canta.data.community

import io.github.samolego.canta.util.DeviceProfile
import io.github.samolego.canta.util.TelemetryConfig
import io.github.samolego.canta.util.androidReleaseOf
import io.github.samolego.canta.util.romLabelOf
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
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

/** How long one backend call may take before the app treats it as never having arrived. */
private const val RPC_TIMEOUT_MS = 30_000L

/** How long the preflight waits for any answer at all: a phone with no route must be told quickly. */
private const val PROBE_TIMEOUT_MS = 6_000L

/** One community plan: a set of packages someone removed and confirmed by a reboot. */
@Serializable
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
    /** True for a plan uploaded without a reboot proof: older rows, kept and labelled as such. */
    val isTest: Boolean = false,
    /** The name the uploader uses today; empty when the backend does not send it (old deployment). */
    val uploader: String = "",
    /**
     * The phone the plan was made on. Empty on rows from a deployment that predates the device
     * columns; the page then cannot say whether the plan fits this phone, and treats it as "this
     * one" instead of inventing a mismatch.
     */
    val brand: String = "",
    val model: String = "",
    /**
     * The model's marketing name ("Redmi K70") when the uploader's ROM reported one. The retail
     * number in [model] is what MIUI/HyperOS puts in `ro.product.model`, and nobody recognises it.
     */
    val marketName: String = "",
    val rom: String = "",
    val romVersion: Int = 0,
    /**
     * The Android API level the uploader ran when the reboot check passed. 0 on rows from a
     * deployment that predates the column, which is why it only ever extends [deviceLabel] and
     * never stands in for something the row does not have.
     */
    val sdk: Int = 0,
) {
    /**
     * What the card shows next to the avatar: the uploader's name, or the identity number
     * ("#10000") for a plan from before names were synced.
     */
    val authorName: String
        get() = uploader.ifBlank { if (owner.isNotEmpty()) "#$owner" else "" }

    /**
     * "Redmi K70 · HyperOS 2", or "" when the backend did not say which device this is for.
     *
     * Every part here comes from the row — the *uploader's* phone and build — never from the reader's
     * own [DeviceProfile]: the detail page also shows the reader's device next to it, and a header
     * that repeated the reader's model would say "this plan is mine" about somebody else's phone.
     * A row that carries only a model code still reads as something: the code is better than silence.
     *
     * The Android release comes from the plan's own [sdk] — the level the uploader ran when the
     * reboot check passed — so the line describes one phone start to finish. Backends that predate
     * that column send no [sdk], and the line then stops after the ROM instead of guessing.
     */
    val deviceLabel: String
        get() = buildList {
            val phone = marketName.ifBlank { model }
            if (phone.isNotBlank()) add(phone)
            if (rom.isNotBlank()) add(romLabelOf(rom, romVersion))
            androidReleaseOf(sdk).takeIf { it.isNotEmpty() }?.let { add("Android $it") }
        }.joinToString(" · ")

    /**
     * Whether this plan was made for [profile]'s own build. A plan from an older deployment
     * carries no device at all, and "unknown" must not read as "another phone": only a known,
     * different model or build does.
     */
    fun matches(profile: DeviceProfile?): Boolean {
        if (profile == null || model.isBlank()) return true
        if (!model.equals(profile.model, ignoreCase = true)) return false
        if (rom.isBlank()) return true
        return rom.equals(profile.rom, ignoreCase = true) && romVersion == profile.romVersion
    }
}

/** Id of the local "test upload" row: never a server id, so it can be told apart. */
const val TEST_PLAN_ID: Long = -1L

/**
 * A comment under a plan or an app page.
 *
 * [replyTo] is what makes a thread: 0 for the comment that starts the conversation, the starter's
 * [id] for an answer to it. A backend that predates the thread table sends neither id nor parent,
 * which is why both default to 0 and the row still reads as a plain comment.
 */
data class PlanComment(
    val author: String,
    val body: String,
    val createdAt: String,
    /** The comment's own id; 0 when the server did not send one. */
    val id: Long = 0L,
    /** The comment this one answers; 0 when it starts a thread. */
    val replyTo: Long = 0L,
    /**
     * The identity number behind [author], as the backend keeps it. A display name can be picked
     * twice or changed at any time; the number is what says a comment belongs to the plan's
     * uploader ("作者"), and what keeps "delete" working after a rename.
     */
    val authorId: String = "",
    /** How many answers hang under this comment. */
    val replies: Int = 0,
    /**
     * How many hearts the comment carries. It is the only reaction a comment has: a wrong comment is
     * answered, not counted down, which is why there is no second number beside this one.
     */
    val likes: Int = 0,
    /** Whether this install is one of those hearts. */
    val liked: Boolean = false,
    /** The phone it was written on ("Redmi K70"); empty on comments written before it was kept. */
    val deviceModel: String = "",
    /** Its system, the way the ROM line reads ("HyperOS 2"); the API level is not shown at all. */
    val systemName: String = "",
) {
    /** "Redmi K70 · HyperOS 2", or "" when the writer's device is unknown. */
    val deviceLabel: String
        get() = buildList {
            if (deviceModel.isNotBlank()) add(deviceModel)
            if (systemName.isNotBlank()) add(systemName)
        }.joinToString(" · ")
}

/**
 * A comment's hearts, as the backend counts them: how many there are, and whether this install is
 * one of them. Returned by the tap so the page shows the server's number, not its own guess.
 */
data class CommentLikes(val likes: Int = 0, val liked: Boolean = false)

/**
 * What people said about removing one package: "it was fine" against "it broke something".
 *
 * Two scopes at once, because both are worth seeing: [canRemove]/[cannotRemove] only count the
 * votes cast on this model and ROM version, the `all` ones count every device.
 */
data class PackageVotes(
    val canRemove: Int = 0,
    val cannotRemove: Int = 0,
    val allCanRemove: Int = 0,
    val allCannotRemove: Int = 0,
    val bySystem: Map<String, Pair<Int, Int>> = emptyMap(),
) {
    /** Votes on this device. */
    val total: Int get() = canRemove + cannotRemove

    /** Votes on every device. */
    val allTotal: Int get() = allCanRemove + allCannotRemove

    /** Share of "this can go" on this device, null while nobody voted. */
    val share: Float? get() = if (total == 0) null else canRemove.toFloat() / total

    /** Share of "this can go" across all devices, null while nobody voted. */
    val allShare: Float? get() = if (allTotal == 0) null else allCanRemove.toFloat() / allTotal

    companion object {
        val EMPTY = PackageVotes()
    }
}

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

    /**
     * Why the last call came back empty: the backend's own message when it refused (a missing
     * function, a rejected upload, a bad key), or the network error. "上传失败" without this is a
     * dead end, so the pages that can fail show it.
     */
    var lastError: String? = null
        private set

    /**
     * True when the last call failed because nothing answered — no route out, a blocked host, a
     * timeout — rather than because the server refused what was sent. An upload that never left the
     * phone has to be told apart from one the server rejected: only the first is a network or VPN
     * problem, and only the first is worth telling the user to check their connection.
     */
    var lastFailureWasNetwork: Boolean = false
        private set

    /** What one call to the backend came back as. */
    private sealed interface Call {
        class Ok(val text: String) : Call
        class Refused(val reason: String) : Call
        object Unreachable : Call
    }

    /**
     * Whether the community backend answers at all.
     *
     * Uploading needs the request to leave the phone, and on many networks that only happens
     * through a VPN or a proxy. Any answer proves reachability — the REST root answering "not
     * found" is still a host that answered — so the check is about the route, not about the call.
     */
    suspend fun isReachable(): Boolean {
        if (answers { client.head(baseUrl) { header("apikey", apiKey) } }) return true
        return answers { client.get(baseUrl) { header("apikey", apiKey) } }
    }

    /** True when [request] came back inside [PROBE_TIMEOUT_MS]; a connection that never opens is not an answer. */
    private suspend fun answers(request: suspend () -> Any?): Boolean =
        // `withTimeoutOrNull` answers null when the probe ran out of time, and runCatching answers a
        // failure; both are "no". The Boolean is spelled out on the lambda rather than inferred:
        // the nested ternaries of the two wrappers are what turned this into a Boolean? once.
        runCatching {
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                request()
                true
            } ?: false
        }.getOrDefault(false)

    private suspend fun call(name: String, body: String): Call {
        val answered = runCatching {
            withTimeoutOrNull(RPC_TIMEOUT_MS) {
                val response = client.post(baseUrl + name) {
                    header("apikey", apiKey)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(body)
                }
                if (response.status.isSuccess()) {
                    Call.Ok(response.bodyAsText())
                } else {
                    // PostgREST answers a refusal with a JSON body carrying `message`; keep a short
                    // form of it so the upload dialog can say what the server did not like.
                    val text = runCatching { response.bodyAsText() }.getOrDefault("")
                    val message = runCatching {
                        Json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull().orEmpty().ifBlank { text.take(200) }
                    Call.Refused(message.ifBlank { "HTTP ${response.status.value}" })
                }
            }
        }
        // A call that threw, and a call that ran out of time, both mean nothing answered.
        return answered.getOrNull() ?: Call.Unreachable
    }

    private suspend fun rpc(name: String, body: String): String? = when (val result = call(name, body)) {
        is Call.Ok -> {
            lastFailureWasNetwork = false
            lastError = null
            result.text
        }
        is Call.Refused -> {
            lastFailureWasNetwork = false
            lastError = result.reason
            null
        }
        Call.Unreachable -> {
            lastFailureWasNetwork = true
            lastError = null
            null
        }
    }

    /**
     * True when the last call failed only because this deployment does not have the function yet:
     * an older backend answers PostgREST with `PGRST202` / "Could not find the function". The pages
     * use it to fall back to the calls that deployment does have instead of reporting a failure.
     */
    private val functionMissing: Boolean
        get() = lastError?.let { it.contains("PGRST202") || it.contains("Could not find the function") } == true

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
        return parsePlans(text)
    }

    /** Every plan row in [text]; an answer the parser cannot read is an empty list, not a crash. */
    private fun parsePlans(text: String): List<CommunityPlan> = runCatching {
        json.parseToJsonElement(text).jsonArray.mapNotNull { element -> parsePlan(element.jsonObject) }
    }.getOrDefault(emptyList())

    /**
     * The plans of one model (and optionally one of its builds) — what the model chips open.
     *
     * [rom] empty asks for every build of the model, which is the "全部" chip. The answer carries
     * the device columns, so the list can say which of these plans are for another phone. Null when
     * the backend could not be reached; an empty list is an answer ("nobody uploaded here yet").
     *
     * A deployment without `plans_for_model` still answers `plans_for`, which matches on model and
     * build alone when the codename is left out: the same rows, without the device columns.
     */
    suspend fun plansForModel(
        brand: String,
        model: String,
        rom: String = "",
        romVersion: Int = 0,
    ): List<CommunityPlan>? {
        if (model.isBlank()) return null
        val body = buildJsonObject {
            put("p_brand", brand)
            put("p_model", model)
            put("p_rom", rom)
            put("p_rom_version", romVersion)
        }.toString()
        rpc("plans_for_model", body)?.let { return parsePlans(it) }

        if (functionMissing && rom.isNotBlank() && romVersion > 0) {
            val legacy = buildJsonObject {
                put("p_model", model)
                // Empty codename: `plans_for` then matches the model and build, whichever the
                // device codename of that phone is.
                put("p_codename", "")
                put("p_rom", rom)
                put("p_rom_version", romVersion)
            }.toString()
            rpc("plans_for", legacy)?.let { return parsePlans(it) }
        }
        return null
    }

    /**
     * Every verified plan the backend has, whatever the phone — the ranking's widest scope.
     *
     * A phone that matches nothing (a fresh install on a model nobody uploaded for, a young
     * community) would otherwise read an empty page although the server has plans. Null when the
     * backend could not be reached; an empty list is an answer ("nobody uploaded anything yet").
     */
    suspend fun plansAll(limit: Int = 200): List<CommunityPlan>? {
        if (limit <= 0) return emptyList()
        val body = buildJsonObject { put("p_limit", limit) }.toString()
        rpc("plans_all", body)?.let { return parsePlans(it) }
        return null
    }

    /**
     * Every model the community has plans for, one row per build, with how many plans and people
     * each has. Null when the backend could not be reached or predates the catalog call.
     */
    suspend fun catalog(): List<CatalogBuild>? {
        val text = rpc("plan_catalog", "{}") ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonArray.mapNotNull { element -> parseCatalogBuild(element.jsonObject) }
        }.getOrNull()
    }

    private fun parseCatalogBuild(o: JsonObject): CatalogBuild? {
        val model = o["model"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (model.isBlank()) return null
        return CatalogBuild(
            brand = o["brand"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            model = model,
            marketName = o["market_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            rom = o["rom"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            romVersion = o["rom_version"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            plans = o["plans"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            people = o["people"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            modelPlans = o["model_plans"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            modelPeople = o["model_people"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
        )
    }

    /**
     * Every plan [owner] uploaded, whatever device it was matched to, newest first.
     *
     * "Manage my plans" needs this: after an OS update the plan no longer matches this phone's
     * `plans_for` row, so a list built from that call would lose the very plans the user wants to
     * delete. Empty when the backend cannot be reached or predates the call.
     */
    suspend fun plansByOwner(owner: String): List<CommunityPlan> {
        if (owner.isEmpty()) return emptyList()
        val text = rpc("plans_by_owner", buildJsonObject { put("p_owner", owner) }.toString())
            ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(text).jsonArray.mapNotNull { parsePlan(it.jsonObject) }
        }.getOrDefault(emptyList())
    }

    /**
     * One plan by id, whatever device it was uploaded for.
     *
     * The page a comment or a notification links to may not be in this phone's own list: another
     * model, a plan the list did not carry, or one whose ROM version changed since. Asking for that
     * one plan is what still lets the page open instead of "找不到这个方案". Null when it is gone —
     * or when the deployment predates `plan_get`.
     */
    suspend fun planById(id: Long): CommunityPlan? {
        if (id <= 0L) return null
        val text = rpc("plan_get", buildJsonObject { put("p_id", id) }.toString()) ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonArray.firstNotNullOfOrNull { parsePlan(it.jsonObject) }
        }.getOrNull()
    }

    /** One row of `plans_for` / `plan_get`, or null when it carries no id. */
    private fun parsePlan(o: JsonObject): CommunityPlan? {
        val id = o["id"]?.jsonPrimitive?.longOrNull ?: return null
        return CommunityPlan(
            id = id,
            title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            note = o["note"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            packages = (o["packages"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            votes = o["votes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            comments = o["comments"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            rebootedAt = o["rebooted_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            owner = o["owner"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            isTest = o["is_test"]?.jsonPrimitive?.booleanOrNull ?: false,
            uploader = o["owner_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            // Absent on a deployment that predates the device catalog: the page then treats the
            // plan as this phone's, which is what the old backend meant anyway.
            brand = o["brand"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            model = o["model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            marketName = o["market_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            rom = o["rom"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            romVersion = o["rom_version"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            // The API level the uploader ran; absent on a deployment that predates the column.
            sdk = o["sdk"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
        )
    }

    /**
     * Uploads [packages] as a plan.
     *
     * The app only calls this with a batch the restart check proved, so [test] is false on every
     * upload this build makes; the flag is kept because the server still understands and stores it
     * for rows that older builds sent, and the list has to label those for what they are.
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
            // The name people recognise, so the model chips read "Redmi K70" and not "23113RKC6C".
            put("market_name", profile.marketName)
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
     * Rewrites the note under [planId], which is the one part of a plan its author can still change
     * after uploading. Both [owner] and [token] are needed, exactly as for [delete]: a plan's
     * number is printed next to every plan and every comment, so it proves nothing on its own.
     *
     * Returns false when the backend refused — somebody else's plan, a stale token, no note.
     */
    suspend fun setNote(planId: Long, owner: String, token: String, note: String): Boolean {
        if (owner.isEmpty() || token.isEmpty() || planId <= 0) return false
        val body = buildJsonObject {
            put("p_id", planId)
            put("p_owner", owner)
            put("p_token", token)
            put("p_note", note)
        }.toString()
        return rpc("plan_set_note", body)
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

    /**
     * Deletes the plans this install already uploaded with exactly the same package set.
     *
     * Uploading the same set twice is a retry, not a second plan: without this the list ends up
     * with two identical rows and the ranking counts the same removal twice.
     */
    suspend fun deleteOwnDuplicates(
        profile: DeviceProfile,
        owner: String,
        token: String,
        packages: List<String>,
    ): Int {
        if (owner.isEmpty() || token.isEmpty() || packages.isEmpty()) return 0
        val wanted = packages.toSet()
        val duplicates = plansFor(profile).filter { it.owner == owner && it.packages.toSet() == wanted }
        var removed = 0
        duplicates.forEach { if (delete(it.id, owner, token)) removed++ }
        return removed
    }

    suspend fun vote(planId: Long, voter: String): Int? {
        val body = buildJsonObject {
            put("p_id", planId)
            put("p_voter", voter)
        }.toString()
        return rpc("plan_vote", body)?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.longOrNull?.toInt() }.getOrNull() }
    }

    /**
     * What people say about removing [pkg]: this device's votes and everybody's, in one call.
     * Null when the backend cannot be reached (or the table is not there yet).
     */
    suspend fun packageVotes(pkg: String, profile: DeviceProfile?): PackageVotes? {
        val body = buildJsonObject {
            put("p_pkg", pkg)
            put("p_model", profile?.model.orEmpty())
            put("p_codename", profile?.device.orEmpty())
            put("p_rom", profile?.rom.orEmpty())
            put("p_rom_version", profile?.romVersion ?: 0)
        }.toString()
        val text = rpc("package_vote_counts", body) ?: return null
        return parseVotes(text)
    }

    /**
     * Records that [voter] says [canRemove] about [pkg] on this device, and answers the fresh
     * counts. Voting again replaces the earlier vote, so the same call changes someone's mind.
     */
    suspend fun votePackage(
        pkg: String,
        voter: String,
        canRemove: Boolean,
        profile: DeviceProfile?,
    ): PackageVotes? {
        if (voter.isEmpty()) return null
        val body = buildJsonObject {
            put("p_pkg", pkg)
            put("p_voter", voter)
            put("p_can_remove", canRemove)
            put("p_brand", profile?.brand.orEmpty())
            put("p_model", profile?.model.orEmpty())
            put("p_codename", profile?.device.orEmpty())
            put("p_rom", profile?.rom.orEmpty())
            put("p_rom_version", profile?.romVersion ?: 0)
            put("p_sdk", profile?.sdk ?: 0)
        }.toString()
        val text = rpc("package_vote", body) ?: return null
        return parseVotes(text)
    }

    private fun parseVotes(text: String): PackageVotes? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        val systems = o["systems"]?.jsonObject.orEmpty().mapValues { (_, value) ->
            val counts = value.jsonObject
            (counts["yes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0) to
                (counts["no"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0)
        }
        PackageVotes(
            canRemove = o["yes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            cannotRemove = o["no"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            allCanRemove = o["all_yes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            allCannotRemove = o["all_no"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            bySystem = systems,
        )
    }.getOrNull()

    // ---------------------------------------------------------------- comments

    /**
     * The thread of one plan, newest first.
     *
     * A backend that predates the shared comment table only knows `plan_comments_for`: falling back
     * to it keeps an older deployment readable instead of showing an empty thread.
     */
    suspend fun comments(planId: Long, viewer: String = ""): List<PlanComment> =
        commentsFor(COMMENT_KIND_PLAN, planId.toString(), viewer)

    /** The thread of one app's page, newest first. [viewer] is whose hearts are marked. */
    suspend fun appComments(packageName: String, viewer: String = ""): List<PlanComment> =
        commentsFor(COMMENT_KIND_APP, packageName, viewer)

    private suspend fun commentsFor(kind: String, target: String, viewer: String = ""): List<PlanComment> {
        if (target.isEmpty()) return emptyList()
        val body = buildJsonObject {
            put("p_kind", kind)
            put("p_target", target)
            put("p_voter", viewer)
        }.toString()
        rpc("comments_for", body)?.let { text -> return parseComments(text) }
        // A backend older than the hearts reads `comments_for` with two arguments only. Asking again
        // without the viewer keeps the thread readable there, unhearted rather than empty.
        if (viewer.isNotEmpty()) {
            val plain = buildJsonObject {
                put("p_kind", kind)
                put("p_target", target)
            }.toString()
            rpc("comments_for", plain)?.let { text -> return parseComments(text) }
        }
        if (kind != COMMENT_KIND_PLAN) return emptyList()
        val planId = target.toLongOrNull() ?: return emptyList()
        val legacy = rpc("plan_comments_for", buildJsonObject { put("p_id", planId) }.toString())
            ?: return emptyList()
        return parseComments(legacy)
    }

    private fun parseComments(text: String): List<PlanComment> = runCatching {
        json.parseToJsonElement(text).jsonArray.map { element ->
            val o = element.jsonObject
            PlanComment(
                author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                replyTo = o["parent_id"]?.jsonPrimitive?.longOrNull ?: 0L,
                // Absent on a backend older than the thread table: an empty id simply matches
                // nobody, which is the right answer when the server does not say who wrote it.
                authorId = o["author_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                replies = o["replies"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                // Absent on a backend older than the hearts: no hearts is the right answer there.
                likes = o["likes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                liked = o["liked"]?.jsonPrimitive?.booleanOrNull == true,
                deviceModel = o["device_model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                systemName = o["system_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }.getOrElse { emptyList() }

    /**
     * Posts one comment, or one answer to [replyTo], and answers its id — null when the backend did
     * not take it.
     *
     * [author] is the name the page shows next to the avatar; [authorId] is the identity number
     * behind it, which is what keeps the comment deleteable after the user renames themselves.
     */
    suspend fun comment(
        kind: String,
        target: String,
        body: String,
        author: String,
        authorId: String = "",
        replyTo: Long = 0L,
        deviceModel: String = "",
        systemName: String = "",
        /** Who wrote the comment being answered; only the old, flat backend needs the name. */
        replyToName: String = "",
    ): Long? {
        if (target.isEmpty() || body.isBlank()) return null
        val payload = buildJsonObject {
            put("p_kind", kind)
            put("p_target", target)
            put("p_body", body)
            put("p_author", author)
            put("p_author_id", authorId)
            put("p_model", deviceModel)
            put("p_system", systemName)
            if (replyTo > 0L) put("p_parent", replyTo)
        }.toString()
        rpc("comment_add", payload)?.let { text ->
            return runCatching { json.parseToJsonElement(text).jsonPrimitive.longOrNull }.getOrNull()
        }
        // A deployment that predates the thread table has no `comment_add`, but it still has the
        // old plan writer. Posting there keeps the thread readable instead of showing "发送失败" on
        // a comment the old page would have taken; a reply carries its "回复 @name" in the text,
        // because the old table has no parent column to put it in.
        if (kind == COMMENT_KIND_PLAN && functionMissing) {
            val planId = target.toLongOrNull() ?: return null
            val text = if (replyTo > 0L && replyToName.isNotEmpty()) "回复 @$replyToName：$body" else body
            val legacyBody = buildJsonObject {
                put("p_id", planId)
                put("p_body", text)
                put("p_author", author)
            }.toString()
            return rpc("plan_comment", legacyBody)
                ?.let { text -> runCatching { json.parseToJsonElement(text).jsonPrimitive.longOrNull }.getOrNull() }
        }
        return null
    }

    /** A comment on plan [planId]. */
    suspend fun commentOnPlan(
        planId: Long,
        body: String,
        author: String,
        authorId: String = "",
        replyTo: Long = 0L,
        deviceModel: String = "",
        systemName: String = "",
        replyToName: String = "",
    ): Long? = comment(
        kind = COMMENT_KIND_PLAN,
        target = planId.toString(),
        body = body,
        author = author,
        authorId = authorId,
        replyTo = replyTo,
        deviceModel = deviceModel,
        systemName = systemName,
        replyToName = replyToName,
    )

    /** A comment on the page of [pkg]. */
    suspend fun commentOnApp(
        pkg: String,
        body: String,
        author: String,
        authorId: String = "",
        replyTo: Long = 0L,
        deviceModel: String = "",
        systemName: String = "",
        replyToName: String = "",
    ): Long? = comment(
        kind = COMMENT_KIND_APP,
        target = pkg,
        body = body,
        author = author,
        authorId = authorId,
        replyTo = replyTo,
        deviceModel = deviceModel,
        systemName = systemName,
        replyToName = replyToName,
    )

    /**
     * Deletes one comment this install wrote. [owner] is the identity number and [token] the
     * secret that proves it, exactly like [delete]: somebody else's comment — or a guessed number —
     * deletes nothing.
     */
    suspend fun deleteComment(id: Long, owner: String, token: String): Boolean {
        // The token is the proof; the number only narrows the match. A comment written before the
        // number arrived is signed with the token, and must still be deletable.
        if (id <= 0L || token.isEmpty()) return false
        val body = buildJsonObject {
            put("p_id", id)
            put("p_owner", owner)
            put("p_token", token)
        }.toString()
        return rpc("comment_delete", body)
            ?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.booleanOrNull }.getOrNull() } == true
    }

    /**
     * Taps the heart on comment [id]: [liked] true adds it, false takes it back.
     *
     * Answers the count and the state the server now holds — the page shows that rather than the
     * number it guessed, so two devices watching the same comment cannot drift apart. Null when the
     * heart did not reach the backend, in which case the page keeps what it already showed.
     *
     * [voter] is the identity number, the same one the comment was written under; a backend that
     * predates the hearts has no `comment_like` and simply answers nothing.
     */
    suspend fun likeComment(id: Long, voter: String, liked: Boolean): CommentLikes? {
        if (id <= 0L || voter.isEmpty()) return null
        val body = buildJsonObject {
            put("p_id", id)
            put("p_voter", voter)
            put("p_liked", liked)
        }.toString()
        return rpc("comment_like", body)?.let(::parseCommentLikes)
    }

    private fun parseCommentLikes(text: String): CommentLikes? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        CommentLikes(
            likes = o["likes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            liked = o["liked"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }.getOrNull()

    /**
     * Which side this install picked for [pkg]: true, false, or null when it never voted.
     *
     * Asked of the backend rather than remembered locally, so the page agrees with the count after a
     * reinstall or a second device. Null also comes back when the call fails; the page keeps its
     * local memory in that case instead of clearing a vote it knows about.
     */
    suspend fun myPackageVote(pkg: String, voter: String): Boolean? {
        if (pkg.isEmpty() || voter.isEmpty()) return null
        val body = buildJsonObject {
            put("p_pkg", pkg)
            put("p_voter", voter)
        }.toString()
        val text = rpc("package_my_vote", body) ?: return null
        return runCatching { json.parseToJsonElement(text).jsonPrimitive.booleanOrNull }.getOrNull()
    }

    /**
     * Everything [owner] wrote — comments and answers alike — newest first, one page at a time.
     *
     * [owner] is the identity number, not the display name: renaming yourself must not lose the
     * list. [token] is sent as well, because a comment written before the backend handed out that
     * number was signed with the token instead; the backend matches either.
     *
     * [offset] is how far the list has already been read: the reader asks for the next page when it
     * scrolls to the end, so a person with a year of comments still draws the first screen at once.
     * A backend older than the paging simply ignores the limit and answers with everything it has,
     * which is why the app keeps its own page size rather than trusting the server's.
     */
    suspend fun myComments(
        owner: String,
        token: String = "",
        offset: Int = 0,
        limit: Int = INBOX_PAGE_SIZE,
    ): List<MyComment> = myCommentsPage(owner, token, offset, limit).items

    /** The same, with "is there another page" alongside: what a scrolling list needs. */
    suspend fun myCommentsPage(
        owner: String,
        token: String = "",
        offset: Int = 0,
        limit: Int = INBOX_PAGE_SIZE,
    ): Page<MyComment> {
        if (owner.isEmpty() && token.isEmpty()) return Page.EMPTY
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_token", token)
            put("p_limit", limit)
            put("p_offset", offset)
        }.toString()
        val text = rpc("comments_by_author", body) ?: return Page.EMPTY
        val items = runCatching {
            json.parseToJsonElement(text).jsonArray.map { element ->
                val o = element.jsonObject
                MyComment(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    kind = o["kind"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    target = o["target"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    author = "",
                    body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    replies = o["replies"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                    likes = o["likes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                )
            }
        }.getOrElse { emptyList() }
        return Page(items, hasMore = items.size >= limit)
    }

    /** The answers other people left under [owner]'s comments, newest first; [token] proves it too. */
    suspend fun repliesToMe(
        owner: String,
        token: String = "",
        offset: Int = 0,
        limit: Int = INBOX_PAGE_SIZE,
    ): List<MyComment> = repliesToMePage(owner, token, offset, limit).items

    /** The same, with "is there another page" alongside. */
    suspend fun repliesToMePage(
        owner: String,
        token: String = "",
        offset: Int = 0,
        limit: Int = INBOX_PAGE_SIZE,
    ): Page<MyComment> {
        if (owner.isEmpty() && token.isEmpty()) return Page.EMPTY
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_token", token)
            put("p_limit", limit)
            put("p_offset", offset)
        }.toString()
        val text = rpc("comments_replies_to", body) ?: return Page.EMPTY
        val items = runCatching {
            json.parseToJsonElement(text).jsonArray.map { element ->
                val o = element.jsonObject
                MyComment(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    kind = o["kind"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    target = o["target"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    parentBody = o["parent_body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    parentId = o["parent_id"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }
        }.getOrElse { emptyList() }
        return Page(items, hasMore = items.size >= limit)
    }

    // ---------------------------------------------------------------- the inbox feed

    /**
     * One page of the whole feed: replies, comments under my plans, hearts on my comments, and
     * endorsements of my plans, merged and ordered by the backend.
     *
     * [perSource] rows are taken from each of the four at [offset], so the feed grows by asking for
     * the next offset — one request per scroll, and the same rows whatever happened in between.
     * A backend that predates `inbox_page` answers nothing, and the page falls back to the two
     * list readers it does have; that is why [fallback] exists rather than the page failing.
     */
    suspend fun inboxPage(
        owner: String,
        token: String = "",
        offset: Int = 0,
        perSource: Int = INBOX_PAGE_SIZE / 2,
    ): Page<InboxItem> {
        if (owner.isEmpty() && token.isEmpty()) return Page.EMPTY
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_token", token)
            put("p_per_source", perSource)
            put("p_offset", offset)
        }.toString()
        val text = rpc("inbox_page", body) ?: return Page.EMPTY
        val items = runCatching {
            json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
                val o = element.jsonObject
                val src = o["src"]?.jsonPrimitive?.contentOrNull.orEmpty()
                // A heart says "小张 等 3 人赞了你的评论": the name the voter uses today is what it
                // has to read, and an unnamed voter falls back to the identity string beside it.
                val actorName = o["actor_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                InboxItem(
                    kind = InboxKind.of(src),
                    id = o["src_id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    targetKind = o["kind"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        .ifEmpty { COMMENT_KIND_PLAN },
                    target = o["target"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    parentBody = o["parent_body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    actor = actorName.ifEmpty { o["actor"]?.jsonPrimitive?.contentOrNull.orEmpty() },
                    createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    likes = o["likes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                )
            }
        }.getOrElse { emptyList() }
        return Page(items, hasMore = pageHasMore(items, perSource))
    }

    // ---------------------------------------------------------------- corrected descriptions

    /**
     * Every rewrite of [packageName]'s description, the accepted one first — null when the backend
     * cannot be reached (or does not carry the function yet, where the page shows the published
     * line and says nothing about corrections).
     *
     * [viewer] is the identity whose votes are marked, so the page can light up the side this
     * install already picked.
     */
    suspend fun descriptionFixes(packageName: String, viewer: String = ""): List<DescriptionFix>? {
        if (packageName.isEmpty()) return emptyList()
        val body = buildJsonObject {
            put("p_pkg", packageName)
            put("p_voter", viewer)
        }.toString()
        val text = rpc("description_fixes_for", body) ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonArray.map(::parseDescriptionFix)
        }.getOrNull()
    }

    /**
     * The rewrite that currently stands in for [packageName]'s published description, or null while
     * the published one still does.
     */
    suspend fun appliedDescriptionFix(packageName: String): DescriptionFix? {
        if (packageName.isEmpty()) return null
        val body = buildJsonObject { put("p_pkg", packageName) }.toString()
        val text = rpc("description_fix_current", body) ?: return null
        return runCatching {
            val element = json.parseToJsonElement(text)
            if (element is kotlinx.serialization.json.JsonNull) null else parseDescriptionFix(element)
        }.getOrNull()
    }

    /**
     * Files [body] as a corrected description of [packageName], signed with [author] and owned by
     * [authorId]. Writing again replaces the pending rewrite this identity already filed, so this
     * is also how one's own suggestion is edited.
     *
     * Answers the rewrite's id; null when the backend refused it (text too long, no identity).
     */
    suspend fun addDescriptionFix(
        packageName: String,
        body: String,
        note: String = "",
        author: String = "",
        authorId: String = "",
    ): Long? {
        if (packageName.isEmpty() || body.isBlank()) return null
        val payload = buildJsonObject {
            put("p_pkg", packageName)
            put("p_body", body)
            put("p_note", note)
            put("p_author", author)
            put("p_author_id", authorId)
        }.toString()
        val text = rpc("description_fix_add", payload) ?: return null
        return runCatching {
            json.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.longOrNull
        }.getOrNull()
    }

    /**
     * Votes [agree] on rewrite [id] as [voter], and answers the fresh counts.
     *
     * The backend re-settles the package after every vote, so [DescriptionFixVote.applied] can turn
     * true here: that is the moment the rewrite replaces the published description.
     */
    suspend fun voteDescriptionFix(id: Long, voter: String, agree: Boolean): DescriptionFixVote? {
        if (id <= 0L || voter.isEmpty()) return null
        val payload = buildJsonObject {
            put("p_id", id)
            put("p_voter", voter)
            put("p_agree", agree)
        }.toString()
        val text = rpc("description_fix_vote", payload) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            DescriptionFixVote(
                agrees = o["agrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                disagrees = o["disagrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                applied = o["applied"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }.getOrNull()
    }

    /** One row of [descriptionFixes], with every field optional: an old answer stays readable. */
    private fun parseDescriptionFix(element: kotlinx.serialization.json.JsonElement): DescriptionFix {
        val o = element.jsonObject
        return DescriptionFix(
            id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            note = o["note"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            authorId = o["author_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            agrees = o["agrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            disagrees = o["disagrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            mine = o["mine"]?.jsonPrimitive?.booleanOrNull,
            applied = o["applied"]?.jsonPrimitive?.booleanOrNull == true,
            needed = o["needed"]?.jsonPrimitive?.longOrNull?.toInt() ?: DescriptionFix.VOTES_NEEDED,
            shareNeeded = o["share"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: DescriptionFix.SHARE_NEEDED,
        )
    }

    /**
     * What is waiting for [owner] since [seenAt]: four counts, the newest comment id and the instant
     * this answer was taken at, as one cheap call.
     *
     * This is what the red dot is drawn from, and [seenAt] is what makes it go away: pass the
     * previous answer's [InboxSummary.checkedAt] and every count comes back as "newer than that",
     * which is 0 once the list has been read. Empty = everything ever, for an install that has never
     * opened the inbox.
     *
     * An older backend has no `inbox_summary` at all, so this answers null there and the caller
     * counts the only way that deployment supports — one page of [repliesToMePage], which is still
     * bounded.
     */
    suspend fun inboxSummary(owner: String, token: String = "", seenAt: String = ""): InboxSummary? {
        if (owner.isEmpty() && token.isEmpty()) return null
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_token", token)
            put("p_seen_at", seenAt)
        }.toString()
        val text = rpc("inbox_summary", body) ?: return null
        return runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            InboxSummary(
                replies = o["replies"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                planComments = o["plan_comments"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                commentHearts = o["comment_hearts"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                planHearts = o["plan_hearts"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                newestCommentId = o["newest_comment_id"]?.jsonPrimitive?.longOrNull ?: 0L,
                checkedAt = o["checked_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.getOrNull()
    }
}

@Suppress("unused")
private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

@Suppress("unused")
private fun JsonObject.num(key: String): Double = this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
