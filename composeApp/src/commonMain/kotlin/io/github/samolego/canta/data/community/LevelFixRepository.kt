package io.github.samolego.canta.data.community

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 「这个包该改成什么级别」的提案与投票。
 *
 * 与 [PlanRepository.descriptionFixes] 一套骨架，但只在真的要看/要改级别时才发请求：应用列表页
 * 每进一次已经发四个请求了（见 `AppDetailScreen` 顶部那段注释），级别修正不能让它变五个——
 * 列表上那一行只读**本地缓存**（[cachedLevelFixes]），联网只在进「级别修正」页时发生。
 */
class LevelFixRepository(private val repository: PlanRepository) {

    /**
     * `Json.parseToJsonElement` 的接收者：序列化 1.6 起顶层扩展 `parseToJsonElement` 已移除，
     * 只能走实例（`PlanRepository` 里也是这么用的）。
     */
    private val JSON = Json { ignoreUnknownKeys = true }

    /**
     * 这个包的级别修正，通过的排前面；null = 这次没问到（断网、后端还没有这个函数）。
     *
     * [viewer] 是本机身份号：答复里 `mine` 按它标出本机投过哪边。
     */
    suspend fun fixes(packageName: String, viewer: String = ""): List<LevelFix>? {
        if (packageName.isEmpty()) return null
        val body = buildJsonObject {
            put("p_pkg", packageName)
            put("p_voter", viewer)
        }.toString()
        val text = repository.levelFixesFor(body) ?: return null
        return runCatching {
            val element = JSON.parseToJsonElement(text)
            if (element is JsonNull) emptyList()
            else element.jsonArray.map(::parseLevelFix)
        }.getOrNull()
    }

    /**
     * 本机上一次拿到的这个包的那份列表（不联网）。
     *
     * 应用详情页用它画「级别有异议？」那一行的计数：没有缓存就按 0 画，绝不因为这一行而多一个请求。
     */
    fun cachedLevelFixes(packageName: String): List<LevelFix> =
        LevelFixCache.get(packageName)

    /** 把 [fixes] 存起来给详情页用；应用列表刷新时清不掉它，因为它是按包名的。 */
    fun remember(packageName: String, fixes: List<LevelFix>) {
        LevelFixCache.put(packageName, fixes)
    }

    /**
     * 提一条级别修正，签 [author]、算在 [authorId] 名下。同一个包同一个人已有一条悬空提案时是改写它。
     *
     * 返回提案 id；null = 后端没接受（档位不在名单那五个里、被限速、没身份）。
     */
    suspend fun propose(
        packageName: String,
        removal: String,
        note: String = "",
        author: String = "",
        authorId: String = "",
    ): Long? {
        if (packageName.isEmpty() || removal.isEmpty()) return null
        val proof = repository.callerTokenForLevelFix()
        val payload = buildJsonObject {
            put("p_pkg", packageName)
            put("p_removal", removal)
            put("p_note", note)
            put("p_author", author)
            put("p_author_id", authorId)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = repository.addLevelFix(payload) ?: return null
        return runCatching {
            JSON.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.longOrNull
        }.getOrNull()
    }

    /** 给提案 [id] 投 [agree]（true = 确实该这么改），返回投完之后的计数。 */
    suspend fun vote(id: Long, voter: String, agree: Boolean): LevelFixVote? {
        if (id <= 0L || voter.isEmpty()) return null
        val proof = repository.callerTokenForLevelFix()
        val payload = buildJsonObject {
            put("p_id", id)
            put("p_voter", voter)
            put("p_agree", agree)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = repository.voteLevelFix(payload) ?: return null
        return runCatching {
            val o = JSON.parseToJsonElement(text).jsonObject
            LevelFixVote(
                agrees = o["agrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                disagrees = o["disagrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                applied = o["applied"]?.jsonPrimitive?.booleanOrNull == true,
                needed = o["needed"]?.jsonPrimitive?.longOrNull?.toInt() ?: LevelFix.VOTES_NEEDED,
            )
        }.getOrNull()
    }

    /** 一行 [fixes] 的答复；每个字段都可缺省，老答复照样读得懂。 */
    private fun parseLevelFix(element: kotlinx.serialization.json.JsonElement): LevelFix {
        val o = element.jsonObject
        val notes = o["notes"]?.let { notesElement ->
            runCatching {
                notesElement.jsonObject.mapValues { (_, value) -> value.jsonPrimitive.contentOrNull.orEmpty() }
            }.getOrDefault(emptyMap())
        }.orEmpty()
        return LevelFix(
            id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            removal = o["removal"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            notes = notes,
            author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            authorId = o["author_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            agrees = o["agrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            disagrees = o["disagrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            mine = o["mine"]?.jsonPrimitive?.booleanOrNull,
            applied = o["applied"]?.jsonPrimitive?.booleanOrNull == true,
            superseded = o["superseded"]?.jsonPrimitive?.booleanOrNull == true,
            needed = o["needed"]?.jsonPrimitive?.longOrNull?.toInt() ?: LevelFix.VOTES_NEEDED,
            shareNeeded = o["share"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: LevelFix.SHARE_NEEDED,
        )
    }
}

/**
 * 按包名的「上次拿到的级别修正」，进程级。
 *
 * 放进程里而不是落盘：它只用来给应用详情页那一行画计数，掉了一次就从 0 开始，用户看不出来，
 * 也不该为此往磁盘上多写一个文件。上限按包名数截断——应用列表可能有几百个包，但真正点开看过的
 * 只有几十个。
 */
internal object LevelFixCache {
    private const val MAX_PACKAGES = 64

    private val entries = LinkedHashMap<String, List<LevelFix>>(MAX_PACKAGES, 0.75f, true)

    fun get(packageName: String): List<LevelFix> = synchronized(entries) {
        entries[packageName].orEmpty()
    }

    fun put(packageName: String, fixes: List<LevelFix>) = synchronized(entries) {
        entries[packageName] = fixes
        while (entries.size > MAX_PACKAGES) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }

    /** 本机提交或投票之后立刻让缓存变成刚拿到的样子，详情页那一行不用等下一趟网络。 */
    fun patch(packageName: String, fixes: List<LevelFix>) = put(packageName, fixes)
}