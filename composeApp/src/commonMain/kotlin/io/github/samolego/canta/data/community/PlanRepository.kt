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
    /** True for a plan uploaded with the test password: no reboot proof behind it. */
    val isTest: Boolean = false,
    /** The name the uploader uses today; empty when the backend does not send it (old deployment). */
    val uploader: String = "",
) {
    /**
     * What the card shows next to the avatar: the uploader's name, or the identity number
     * ("#10000") for a plan from before names were synced.
     */
    val authorName: String
        get() = uploader.ifBlank { if (owner.isNotEmpty()) "#$owner" else "" }
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

    private suspend fun rpc(name: String, body: String): String? = try {
        val response = client.post(baseUrl + name) {
            header("apikey", apiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body)
        }
        if (response.status.isSuccess()) {
            lastError = null
            response.bodyAsText()
        } else {
            // PostgREST answers a refusal with a JSON body carrying `message`; keep a short form of
            // it so the upload dialog can say what the server did not like.
            val text = runCatching { response.bodyAsText() }.getOrDefault("")
            val message = runCatching {
                Json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.contentOrNull
            }.getOrNull().orEmpty().ifBlank { text.take(200) }
            lastError = if (message.isBlank()) "HTTP ${response.status.value}" else message
            null
        }
    } catch (e: Exception) {
        lastError = e.message?.take(200) ?: e::class.simpleName
        null
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
        return runCatching {
            json.parseToJsonElement(text).jsonArray.mapNotNull { element -> parsePlan(element.jsonObject) }
        }.getOrDefault(emptyList())
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
        )
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
        PackageVotes(
            canRemove = o["yes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            cannotRemove = o["no"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            allCanRemove = o["all_yes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            allCannotRemove = o["all_no"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
        )
    }.getOrNull()

    // ---------------------------------------------------------------- comments

    /**
     * The thread of one plan, newest first.
     *
     * A backend that predates the shared comment table only knows `plan_comments_for`: falling back
     * to it keeps an older deployment readable instead of showing an empty thread.
     */
    suspend fun comments(planId: Long): List<PlanComment> =
        commentsFor(COMMENT_KIND_PLAN, planId.toString())

    /** The thread of one app's page, newest first. */
    suspend fun appComments(packageName: String): List<PlanComment> =
        commentsFor(COMMENT_KIND_APP, packageName)

    private suspend fun commentsFor(kind: String, target: String): List<PlanComment> {
        if (target.isEmpty()) return emptyList()
        val body = buildJsonObject {
            put("p_kind", kind)
            put("p_target", target)
        }.toString()
        rpc("comments_for", body)?.let { text -> return parseComments(text) }
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
     * Everything [owner] wrote — comments and answers alike — newest first. [owner] is the identity
     * number, not the display name: renaming yourself must not lose the list.
     *
     * [token] is sent as well, because a comment written before the backend handed out that number
     * was signed with the token instead; the backend matches either.
     */
    suspend fun myComments(owner: String, token: String = ""): List<MyComment> {
        if (owner.isEmpty() && token.isEmpty()) return emptyList()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_token", token)
        }.toString()
        val text = rpc("comments_by_author", body) ?: return emptyList()
        return runCatching {
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
                )
            }
        }.getOrElse { emptyList() }
    }

    /** The answers other people left under [owner]'s comments, newest first; [token] proves it too. */
    suspend fun repliesToMe(owner: String, token: String = ""): List<MyComment> {
        if (owner.isEmpty() && token.isEmpty()) return emptyList()
        val body = buildJsonObject {
            put("p_owner", owner)
            put("p_token", token)
        }.toString()
        val text = rpc("comments_replies_to", body) ?: return emptyList()
        return runCatching {
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
    }
}

@Suppress("unused")
private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

@Suppress("unused")
private fun JsonObject.num(key: String): Double = this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
