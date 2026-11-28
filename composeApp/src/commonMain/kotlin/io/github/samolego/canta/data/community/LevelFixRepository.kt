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
     * 失败时带原因（[LevelFixResult.Failed]）：网络没通、被限速、档位不合法、后端没有这个函数，
     * 这四种在页面上必须能分开说，否则只剩一句"没能提交"什么都查不出来。
     */
    suspend fun propose(
        packageName: String,
        removal: String,
        note: String = "",
        author: String = "",
        authorId: String = "",
    ): LevelFixResult<Long> {
        if (packageName.isEmpty()) return LevelFixResult.Failed("没有包名", network = false)
        if (removal.isEmpty()) return LevelFixResult.Failed("还没选档位", network = false)
        val proof = repository.callerTokenForLevelFix()
        val payload = buildJsonObject {
            put("p_pkg", packageName)
            put("p_removal", removal)
            put("p_note", note)
            put("p_author", author)
            put("p_author_id", authorId)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = repository.addLevelFix(payload) ?: return repository.failureReason()
        val id = runCatching {
            JSON.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.longOrNull
        }.getOrNull()
        // 后端对"档位不在名单那五个里"这类是**安静返回 null**，不是报错：这一句也要说清。
        return if (id == null) {
            LevelFixResult.Failed("后端没接受这条（档位不合法或被限速）", network = false)
        } else {
            LevelFixResult.Ok(id)
        }
    }

    /** 给提案 [id] 投 [agree]（true = 确实该这么改），返回投完之后的计数。 */
    suspend fun vote(id: Long, voter: String, agree: Boolean): LevelFixResult<LevelFixVote> {
        if (id <= 0L) return LevelFixResult.Failed("这条提案没有 id", network = false)
        if (voter.isEmpty()) return LevelFixResult.Failed("本机还没有身份", network = false)
        val proof = repository.callerTokenForLevelFix()
        val payload = buildJsonObject {
            put("p_id", id)
            put("p_voter", voter)
            put("p_agree", agree)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = repository.voteLevelFix(payload) ?: return repository.failureReason()
        val vote = runCatching {
            val o = JSON.parseToJsonElement(text).jsonObject
            LevelFixVote(
                agrees = o["agrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                disagrees = o["disagrees"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                applied = o["applied"]?.jsonPrimitive?.booleanOrNull == true,
                needed = o["needed"]?.jsonPrimitive?.longOrNull?.toInt() ?: LevelFix.VOTES_NEEDED,
            )
        }.getOrNull()
        return if (vote == null) {
            LevelFixResult.Failed("后端没接受这一票", network = false)
        } else {
            LevelFixResult.Ok(vote)
        }
    }

    /**
     * 三个维度（级别 / 后果 / 系统应用）现在的票数与本人投的选项；null = 这次没问到。
     *
     * 与 [fixes] 分开一趟：那一趟是历史记录（谁提过什么），这一趟才是页面上百分比的数据。
     * 两者同样只在打开这一页时才请求。
     */
    suspend fun ballots(packageName: String, viewer: String = ""): LevelBallots? {
        if (packageName.isEmpty()) return null
        val body = buildJsonObject {
            put("p_pkg", packageName)
            put("p_voter", viewer)
        }.toString()
        val text = repository.levelFixBallotsFor(body) ?: return null
        return runCatching {
            val element = JSON.parseToJsonElement(text)
            if (element is JsonNull) null else parseBallots(element)
        }.getOrNull()
    }

    /**
     * 投一个维度的一票，返回投完之后三个维度的完整计数。
     *
     * [dimension] 是 `level` / `impact` / `system`，[choice] 是该维度的选项（后端有约束，只有
     * 那一套值接受）。每人每维度一票，重复投 = 改票；[note] 只有级别维度会存下来当下发依据。
     */
    suspend fun voteBallot(
        packageName: String,
        dimension: String,
        choice: String,
        note: String = "",
        voter: String = "",
        name: String = "",
    ): LevelFixResult<LevelBallots> {
        if (packageName.isEmpty()) return LevelFixResult.Failed("没有包名", network = false)
        if (dimension.isEmpty() || choice.isEmpty()) {
            return LevelFixResult.Failed("还没选选项", network = false)
        }
        if (voter.isEmpty()) return LevelFixResult.Failed("本机还没有身份", network = false)
        val proof = repository.callerTokenForLevelFix()
        val payload = buildJsonObject {
            put("p_pkg", packageName)
            put("p_dimension", dimension)
            put("p_choice", choice)
            put("p_note", note)
            put("p_voter", voter)
            // 显示名只为「修正历史」里能认出是谁；旧 APK 不带，历史里就是"匿名"。
            put("p_name", name)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = repository.voteLevelFixBallot(payload) ?: return repository.failureReason()
        val parsed = runCatching {
            val element = JSON.parseToJsonElement(text)
            if (element is JsonNull) null else parseBallots(element)
        }.getOrNull()
        return if (parsed == null) {
            LevelFixResult.Failed("后端没接受这一票（选项不合法或被限速）", network = false)
        } else {
            LevelFixResult.Ok(parsed)
        }
    }

    /**
     * 这个包现在被盖了什么章（每枚几票、我投了哪一枚、已经盖上去的是哪一枚）。
     *
     * 先看 [StampCache]：详情页不该为了一枚章变成五个请求。**级别修正**那一页要拿准的，
     * 传 [force] = true 直接问后端。null = 没问到——断网，或者后端还没推这张表（那就什么都不画）。
     */
    suspend fun stamps(
        packageName: String,
        viewer: String = "",
        force: Boolean = false,
    ): StampSummary? {
        if (packageName.isEmpty()) return null
        if (!force) StampCache.get(packageName)?.let { return it }
        val body = buildJsonObject {
            put("p_pkg", packageName)
            put("p_voter", viewer)
        }.toString()
        val text = repository.stampsFor(body) ?: return null
        return runCatching {
            val element = JSON.parseToJsonElement(text)
            if (element is JsonNull) null else parseStamps(element)
        }.getOrNull()?.also { StampCache.put(packageName, it) }
    }

    /**
     * 给这个包盖一枚章（[title] 必须是 [Stamps.TITLES] 里的两字词），返回盖完之后整份计数。
     *
     * 一人一包一票：换一枚就是改票，不会两枚同时挂着。
     */
    suspend fun voteStamp(
        packageName: String,
        title: String,
        voter: String = "",
        name: String = "",
    ): LevelFixResult<StampSummary> {
        if (packageName.isEmpty()) return LevelFixResult.Failed("没有包名", network = false)
        if (title.isEmpty()) return LevelFixResult.Failed("还没选印章", network = false)
        if (voter.isEmpty()) return LevelFixResult.Failed("本机还没有身份", network = false)
        val proof = repository.callerTokenForLevelFix()
        val payload = buildJsonObject {
            put("p_pkg", packageName)
            put("p_title", title)
            put("p_voter", voter)
            put("p_name", name)
            if (proof.isNotEmpty()) put("p_token", proof)
        }.toString()
        val text = repository.voteStamp(payload) ?: return repository.failureReason()
        val parsed = runCatching {
            val element = JSON.parseToJsonElement(text)
            if (element is JsonNull) null else parseStamps(element)
        }.getOrNull()
        return if (parsed == null) {
            LevelFixResult.Failed("后端没接受这一票（印章不在名单里，或被限速）", network = false)
        } else {
            // 投完就把这一份塞进缓存：详情页不用再问一趟，点回去当场看到新的票数（和可能的新章）。
            StampCache.put(packageName, parsed)
            LevelFixResult.Ok(parsed)
        }
    }

    /**
     * 印章那一路的答复；字段都可缺省，后端还没推新表时页面就当「没人盖过」画。
     *
     * `counts` 里后端会把没被投过的章也带上一行 0 票，所以页面直接按 [Stamps.TITLES] 取就行，
     * 不必自己补空行。
     */
    private fun parseStamps(element: kotlinx.serialization.json.JsonElement): StampSummary? = runCatching {
        val o = element.jsonObject
        val counts = o["counts"]?.jsonArray?.map { row ->
            val c = row.jsonObject
            StampCount(
                title = c["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                votes = c["votes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                share = c["share"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f,
                mine = c["mine"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }.orEmpty()
        StampSummary(
            counts = counts,
            applied = o["applied"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            appliedVotes = o["applied_votes"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            needed = o["needed"]?.jsonPrimitive?.longOrNull?.toInt() ?: Stamps.NEEDED,
            shareNeeded = o["share_needed"]?.jsonPrimitive?.doubleOrNull?.toFloat()
                ?: Stamps.SHARE_NEEDED,
        )
    }.getOrNull()

    /**
     * 最近的投票记录（谁、在哪个维度、投了什么、写了什么依据），用于「修正历史」。
     *
     * 与 [ballots] 分开一趟：那个给百分比与"我投了什么"，这个给人名、时间和依据。
     * null = 这次没问到（断网、后端还没这个函数）。
     */
    suspend fun history(packageName: String, limit: Int = 30): List<LevelBallotEntry>? {
        if (packageName.isEmpty()) return null
        val body = buildJsonObject {
            put("p_pkg", packageName)
            put("p_limit", limit)
        }.toString()
        val text = repository.levelFixBallotHistory(body) ?: return null
        return runCatching {
            val element = JSON.parseToJsonElement(text)
            if (element is JsonNull) {
                emptyList()
            } else {
                element.jsonArray.map { row ->
                    val o = row.jsonObject
                    LevelBallotEntry(
                        dimension = o["dimension"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        choice = o["choice"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        note = o["note"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        at = o["at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    )
                }
            }
        }.getOrNull()
    }

    /** 三个维度的答复；字段都可缺省，后端没给就按 0 画。 */
    private fun parseBallots(element: kotlinx.serialization.json.JsonElement): LevelBallots? = runCatching {
        val o = element.jsonObject
        val votes = o["votes"]?.jsonObject?.mapValues { (_, dimension) ->
            dimension.jsonObject.mapValues { (_, count) -> count.jsonPrimitive.longOrNull?.toInt() ?: 0 }
        }.orEmpty()
        val mine = o["mine"]?.jsonObject?.mapValues { (_, choice) ->
            choice.jsonPrimitive.contentOrNull.orEmpty()
        }.orEmpty()
        LevelBallots(
            votes = votes,
            mine = mine,
            needed = o["needed"]?.jsonPrimitive?.longOrNull?.toInt() ?: LevelFix.VOTES_NEEDED,
            shareNeeded = o["share"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: LevelFix.SHARE_NEEDED,
            applied = o["applied"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }.getOrNull()

    /** 一行 [fixes] 的答复；每个字段都可缺省，老答复照样读得懂。 */
    private fun parseLevelFix(element: kotlinx.serialization.json.JsonElement): LevelFix {        val o = element.jsonObject
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