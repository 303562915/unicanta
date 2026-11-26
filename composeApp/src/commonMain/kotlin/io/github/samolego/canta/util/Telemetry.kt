package io.github.samolego.canta.util

import io.github.samolego.canta.data.app.AppBadgeInfo
import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.data.community.GITHUB_SCORES_URL
import io.github.samolego.canta.data.community.PAGES_SCORES_URL
import io.github.samolego.canta.generated.resources.Res
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What happened to a package; [wire] is the value sent to the server. */
enum class TelemetryEventType(val wire: String) {
    UNINSTALLED("uninstalled"),
    SURVIVED_REBOOT("survived_reboot"),
    REINSTALLED("reinstalled"),

    /** The uninstall is void as survival evidence: the package came back by itself (system update, restore). */
    VOIDED("voided"),

    /**
     * The package was back although the build had not changed, so nothing but a person can have
     * put it there: the most likely reason is a rescue of a device that would not boot or lost its
     * launcher after the uninstall. Never survival evidence, but it is the only signal Canta has
     * for "this uninstall had to be undone by hand" (see [PackageTelemetry]).
     */
    RESTORED("restored"),
}

/**
 * One anonymous event. Only system packages can be turned into one, through [of];
 * there is no way to construct one for a user-installed app.
 */
class TelemetryEvent private constructor(
    val event: TelemetryEventType,
    val pkg: String,
    /** ISO date of the (UTC) Monday of the week the cohort was uninstalled in. */
    val cohortWeek: String,
    /** Size of the uninstall batch, see [batchBucketOf]. */
    val batchBucket: String,
    /** The device the cohort was uninstalled on (not necessarily the current one, after an OS update). */
    val device: DeviceContext,
) {
    override fun equals(other: Any?) = other is TelemetryEvent &&
        event == other.event && pkg == other.pkg &&
        cohortWeek == other.cohortWeek && batchBucket == other.batchBucket && device == other.device

    override fun hashCode() = listOf(event, pkg, cohortWeek, batchBucket, device).hashCode()

    override fun toString() = "TelemetryEvent($event, $pkg, $cohortWeek, $batchBucket, $device)"

    companion object {
        /** The event for [app], or null (the app is never reported) if it isn't a system app. */
        fun of(
            app: AppInfo,
            event: TelemetryEventType,
            cohortWeek: String,
            batchBucket: String,
            device: DeviceContext = DeviceContext.UNKNOWN,
        ): TelemetryEvent? =
            if (app.isSystemApp) TelemetryEvent(event, app.packageName, cohortWeek, batchBucket, device) else null

        /** Events for the system apps among [apps]; everything else is dropped. */
        fun allOf(
            apps: Collection<AppInfo>,
            event: TelemetryEventType,
            cohortWeek: String,
            batchBucket: String,
            device: DeviceContext = DeviceContext.UNKNOWN,
        ) = apps.mapNotNull { of(it, event, cohortWeek, batchBucket, device) }

        /**
         * The event for a pending uninstall entry. Entries are only ever created from
         * system apps (see [of]), so no further check is needed.
         */
        internal fun ofPending(
            entry: io.github.samolego.canta.data.proto.PendingUninstallProto,
            event: TelemetryEventType,
        ) = TelemetryEvent(event, entry.pkg, entry.cohortWeek, entry.batchBucket, entry.device())
    }
}

/** UTC Monday (ISO date) of the week containing [instant]. */
fun cohortWeekOf(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.UTC).date
    return date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY).toString()
}

/** Above this many apps in one go, a batch counts as large (see [batchBucketOf] and the confirm dialog). */
const val LARGE_BATCH_THRESHOLD = 11

/**
 * 包分数快照的地址：GitHub Pages（国内 CDN）与 raw，两个都失效才算真读不到。
 *
 * 同一个 `db-backup.yml` 生成，文件名换成 `package_scores.json`。它回答的是
 * `package_scores_for(p_rom, p_rom_version)` 那一问，而那个函数**不带身份**——
 * 同一个 ROM 上所有人拿到同一份数据，没有理由让每台手机各自去库里聚一遍。
 */
private const val SCORES_SNAPSHOT_MIRROR_URL = PAGES_SCORES_URL
private const val SCORES_SNAPSHOT_URL = GITHUB_SCORES_URL

/** 快照最多等这么久；它是内容来源，不该比一次正常请求还慢。 */
private const val SCORES_SNAPSHOT_TIMEOUT_MS = 15_000L

/**
 * 快照读失败之后多久之内不再重试。
 *
 * 两轮各 15 秒超时，断网时一次刷新就是 30 秒；失败后停 60 秒，期间直接问后端。
 */
private const val SCORES_SNAPSHOT_RETRY_BACKOFF_MS = 60_000L

/**
 * 随包带的那一份包分数（composeResources/files/package_scores_snapshot.json）。
 *
 * 由 db-backup.yml 与 `site/db/package_scores.json` 一起生成，行是**扁平**的
 * （每行带 rom / rom_version），读不到网络快照时就靠它——断网的人看到的是装机那天的事实，
 * 而不是「没有任何数据」。
 */
private const val BUNDLED_SCORES_PATH = "files/package_scores_snapshot.json"

/** Coarse uninstall batch size, so exact counts are never sent. */
fun batchBucketOf(count: Int): String = when {
    count <= 1 -> "1"
    count < LARGE_BATCH_THRESHOLD -> "2-10"
    else -> "11+"
}

/** Whether [count] apps in one go is a batch large enough to be worth a warning. */
fun isLargeBatch(count: Int): Boolean = count >= LARGE_BATCH_THRESHOLD

enum class Verdict {
    INSUFFICIENT, BOOTLOOP, BREAKS, SAFE, UNCLEAR,

    /** A verdict this version doesn't know. */
    UNKNOWN;

    companion object {
        fun parse(value: String?): Verdict = entries.firstOrNull {
            it != UNKNOWN && it.name.equals(value, ignoreCase = true)
        } ?: UNKNOWN
    }
}

/** Whether the verdict says something (not "too few reports" or unknown). */
val Verdict.isInformative: Boolean get() = this != Verdict.INSUFFICIENT && this != Verdict.UNKNOWN

/**
 * The verdict to show next to the app's UAD [badge]. Never "safe" when UAD rates the package
 * Expert or Unsafe: a wrong "safe" can brick phones, so it shows as unclear instead.
 */
fun displayVerdict(verdict: Verdict, badge: AppBadgeInfo?): Verdict =
    if (verdict == Verdict.SAFE && (badge == AppBadgeInfo.EXPERT || badge == AppBadgeInfo.UNSAFE)) Verdict.UNCLEAR
    else verdict

/** This score with [displayVerdict] applied. */
fun PackageScore.displayed(badge: AppBadgeInfo?): PackageScore {
    val shown = displayVerdict(verdict, badge)
    return if (shown == verdict) this else copy(verdict = shown)
}

/** This score with [displayVerdict] applied to all rows. */
fun CommunityScore.displayed(badge: AppBadgeInfo?): CommunityScore =
    copy(overall = overall?.displayed(badge), family = family?.displayed(badge), device = device?.displayed(badge))

data class PackageScore(
    val pkg: String,
    /** Floored to hundreds by the server, so 0 means fewer than 100. */
    val uninstalls: Int,
    val smallBatch: Boolean,
    val verdict: Verdict,
    val updatedAt: String?,
    /** ROM family with the most uninstalls of this package (overall rows only), null if unknown. */
    val mainRom: String? = null,
    /** [mainRom]'s share (0..1) of the uninstalls from known ROM families. */
    val mainRomShare: Double? = null,
    /** ROM version most of this family row's reports came from (family rows only), null if unknown. */
    val mainVersion: Int? = null,
    /** [mainVersion]'s share (0..1) of the family row's uninstalls. */
    val mainVersionShare: Double? = null,
)

/** Which row a [ChosenVerdict] comes from. */
enum class VerdictScope { VERSION, FAMILY, OVERALL }

/**
 * The verdict picked by [chooseVerdict]. Only [actionable] ones may drive badges and warnings;
 * otherwise it is information only, mostly from another ROM ([otherRomLabel], "One UI").
 */
data class ChosenVerdict(
    val verdict: Verdict,
    val scope: VerdictScope,
    val actionable: Boolean,
    /** "One UI" for an overall verdict, "HyperOS 2" for a family one; set when not [actionable]. */
    val otherRomLabel: String? = null,
)

/** Share of uninstalls from one other ROM above which the overall verdict is information only. */
const val OTHER_ROM_DOMINANCE = 0.8

/** Share of a family row's uninstalls from one other version above which it is information only. */
const val OTHER_VERSION_DOMINANCE = 0.8

/**
 * Picks the verdict for a device on ROM [deviceRom] (null when unknown): the ROM version's row,
 * else the ROM family's (information only when mostly from another version), else the overall one. The overall one is only information when it comes
 * mostly from another ROM. Null if there is no row at all.
 */
fun chooseVerdict(
    overall: PackageScore?,
    family: PackageScore?,
    device: PackageScore?,
    deviceRom: String?,
    deviceRomVersion: Int = 0,
): ChosenVerdict? {
    if (device != null && device.verdict.isInformative) return ChosenVerdict(device.verdict, VerdictScope.VERSION, true)
    if (family != null && family.verdict.isInformative) {
        // Mostly another version's reports: information only, and the overall row (a superset) is no better.
        val mainVersion = family.mainVersion
        val dominated = deviceRom != null && deviceRomVersion > 0 && mainVersion != null &&
            mainVersion != deviceRomVersion && (family.mainVersionShare ?: 0.0) > OTHER_VERSION_DOMINANCE
        return ChosenVerdict(
            family.verdict, VerdictScope.FAMILY, actionable = !dominated,
            otherRomLabel = if (dominated) DeviceContext("", deviceRom!!, mainVersion!!, 0).romLabel() else null,
        )
    }
    if (overall == null) return null
    val main = overall.mainRom
    val dominated = deviceRom != null && main != null && main != deviceRom &&
        (overall.mainRomShare ?: 0.0) > OTHER_ROM_DOMINANCE
    return ChosenVerdict(
        overall.verdict, VerdictScope.OVERALL, actionable = !dominated,
        otherRomLabel = if (dominated) romName(main!!) else null,
    )
}

/**
 * What the community says about a package: [overall] across all devices and,
 * when published for the user's ROM, [family] (all versions) and [device] (this version).
 * [deviceLabel] names the ROM version ("HyperOS 1"), [familyLabel] the ROM ("HyperOS");
 * [deviceRom] is the ROM family, null when the device's is unknown.
 */
data class CommunityScore(
    val overall: PackageScore?,
    val device: PackageScore?,
    val deviceLabel: String,
    val family: PackageScore? = null,
    val deviceRom: String? = null,
    val familyLabel: String = "",
    val deviceRomVersion: Int = 0,
) {
    /** Whether any row has a verdict worth showing. */
    val hasInfo: Boolean
        get() = listOf(overall, family, device).any { it?.verdict?.isInformative == true }

    /** The verdict to show, see [chooseVerdict]. */
    val chosen: ChosenVerdict? get() = chooseVerdict(overall, family, device, deviceRom, deviceRomVersion)

    /** The verdict to act on (badge, bootloop warning): the chosen one if actionable, else unknown. */
    val verdict: Verdict
        get() = chosen?.takeIf { it.actionable }?.verdict ?: Verdict.UNKNOWN
}

@Serializable
private class PayloadEvent(val e: String, val p: String, val w: String, val b: String)

@Serializable
private class PayloadDevice(val brand: String, val rom: String, val romVersion: Int, val sdk: Int)

@Serializable
private class Payload(val v: Int, val platform: String, val device: PayloadDevice, val events: List<PayloadEvent>)

@Serializable
private class Challenge(val challenge: String, val sig: String)

private val compactJson = Json
private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * Opt-in anonymous package-safety telemetry: fire-and-forget, no identifiers,
 * no retries, nothing persisted. Events are queued in memory and sent
 * [TelemetryConfig.DEBOUNCE] after the last one, or on [flush].
 *
 * Every send checks [isEnabled] first, which is the single gate.
 *
 * [queue] is meant to be called from one thread at a time (the UI thread).
 */
@OptIn(ExperimentalTime::class)
class Telemetry(
    private val client: HttpClient,
    private val platform: String,
    private val scope: CoroutineScope,
    private val isEnabled: suspend () -> Boolean,
    /**
     * 包分数快照的两个源（Pages 优先）。**两个都传空串＝这一趟完全不读快照**，直接问后端：
     * 量「后端那条路」的测试、以及「某个部署还没发布快照」，走的都是这条。
     *
     * 注意「完全不读」包括**随包内置那一份**：它也是一个快照源（`BUNDLED_SCORES_PATH`），
     * 里面同样有 hyperos/rom_version 行。只关网络源而不关它，快照仍然会命中，`fetchScores`
     * 就不再问 `package_scores_for` —— 这正是 2026-11-27 那四个 `TelemetryTest` 用例
     * NPE 的原因（`s.device` 变成 null）。
     */
    private val scoresSnapshotUrl: String = SCORES_SNAPSHOT_URL,
    private val scoresSnapshotMirrorUrl: String = SCORES_SNAPSHOT_MIRROR_URL,
    /**
     * 网络源都读不到时，要不要退回**随包内置**那份快照（`BUNDLED_SCORES_PATH`）。
     *
     * 默认要：断网时它比什么都没有强。但**测试里关掉**——那份文件是真实数据（1.1 MB，
     * 里面真有 hyperos/rom_version 行），开着就等于让测试的 mock 之外多出一个内容来源，
     * `fetchScores` 会在该问后端的时候直接命中内置快照，用例量到的东西全变。
     */
    private val allowBundledSnapshot: Boolean = true,
) {
    private companion object {
        const val TAG = "Telemetry"
    }

    private val pending = Channel<TelemetryEvent>(Channel.UNLIMITED)
    private var debounce: Job? = null

    /** The platform label this client signs its reports with ("android", "desktop", "web"). */
    val platformName: String get() = platform

    /** Adds [events] to the queue and restarts the debounce timer. */
    fun queue(events: List<TelemetryEvent>) {
        if (events.isEmpty()) return
        events.forEach { pending.trySend(it) }
        debounce?.cancel()
        debounce = scope.launch {
            delay(TelemetryConfig.DEBOUNCE)
            sendPending()
        }
    }

    /** Sends everything queued right now (app going to background, window closing). */
    suspend fun flush() {
        debounce?.cancel()
        debounce = null
        // In our own scope, so cancelling the caller (activity going away) can't lose the drained events.
        scope.launch { sendPending() }.join()
    }

    /** Drops everything queued (analytics turned off). */
    fun clear() {
        debounce?.cancel()
        debounce = null
        while (pending.tryReceive().isSuccess) Unit
    }

    /** Sends [events] and waits; returns false if something was not delivered (or analytics is off). */
    suspend fun sendAndWait(events: List<TelemetryEvent>): Boolean {
        if (events.isEmpty()) return true
        return send(events)
    }

    /**
     * Posts one [FailureReport] to the backend's `operation_failure` RPC and returns whether it was
     * accepted. Gated by [isEnabled] like the events: with analytics off, nothing leaves the phone.
     *
     * Not queued and not retried: a refusal that could not be reported while the phone was offline
     * is not worth carrying into the next start, and the user is never made to wait for it.
     */
    suspend fun sendFailureReport(report: FailureReport): Boolean {
        if (!isEnabled()) return false
        val body = JsonObject(
            mapOf("payload" to JsonPrimitive(compactJson.encodeToString(report)))
        ).toString()
        return try {
            val response = client.post(TelemetryConfig.BASE_URL + "operation_failure") {
                rpcHeaders()
                setBody(body)
            }
            if (response.status.isSuccess()) {
                LogUtils.i(TAG, "Sent failure report ${report.kind} ${report.pkg} (${report.status})")
                true
            } else {
                LogUtils.w(
                    TAG,
                    "Failure report rejected: ${response.status} ${response.bodyAsText().take(200)}",
                )
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtils.w(TAG, "Failure report dropped: $e")
            false
        }
    }

    /**
     * Posts one [UpdateReport] to the backend's `update_report` RPC and returns whether it landed.
     *
     * Deliberately **not** behind [isEnabled]: the package-data opt-in is about sharing which system
     * packages a person removed, and this report carries none of that — it is this app's own updater
     * failing on a device, with no token and no package list. Without it, "下载装不上" can only be
     * diagnosed by asking the person what their screen said, which is the request behind this path.
     *
     * Not queued and not retried, like [sendFailureReport]: a refusal that happened while the phone
     * was offline is not worth carrying into the next start, and nobody waits for the answer.
     */
    suspend fun sendUpdateReport(report: UpdateReport): Boolean {
        val body = JsonObject(
            mapOf("payload" to JsonPrimitive(compactJson.encodeToString(report)))
        ).toString()
        return try {
            val response = client.post(TelemetryConfig.BASE_URL + "update_report") {
                rpcHeaders()
                setBody(body)
            }
            if (response.status.isSuccess()) {
                LogUtils.i(TAG, "Sent update report ${report.stage}/${report.outcome} ${report.code}")
                true
            } else {
                LogUtils.w(
                    TAG,
                    "Update report rejected: ${response.status} ${response.bodyAsText().take(200)}",
                )
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtils.w(TAG, "Update report dropped: $e")
            false
        }
    }

    /**
     * 把一次工具页操作的结果发到后端的 `tweak_report` RPC。
     *
     * 和 [sendUpdateReport] 一样不看 `isEnabled()`：那个开关管的是「卸了哪些系统包」，这条里只有
     * 这台机器自己的音频/场景属性、几个目录的文件名和工具自己的输出——没有包列表、没有身份。
     * 「音频白名单用不了」占了反馈的大头，没有这一条只能继续靠人描述屏幕。
     *
     * 不入队、不重试：离线时没发出去就算了，没人等这个答案。
     */
    suspend fun sendTweakReport(report: TweakReport): Boolean {
        val body = JsonObject(
            mapOf("payload" to JsonPrimitive(compactJson.encodeToString(report)))
        ).toString()
        return try {
            val response = client.post(TelemetryConfig.BASE_URL + "tweak_report") {
                rpcHeaders()
                setBody(body)
            }
            if (response.status.isSuccess()) {
                LogUtils.i(TAG, "Sent tweak report ${report.tweak}/${report.stage} ${report.code}")
                true
            } else {
                LogUtils.w(
                    TAG,
                    "Tweak report rejected: ${response.status} ${response.bodyAsText().take(200)}",
                )
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtils.w(TAG, "Tweak report dropped: $e")
            false
        }
    }

    private suspend fun sendPending() {
        val events = buildList {
            while (true) add(pending.tryReceive().getOrNull() ?: break)
        }
        send(events)
    }

    /** One request carries a single device object, so events are split by device before chunking. */
    private suspend fun send(events: List<TelemetryEvent>): Boolean {
        if (events.isEmpty()) return true
        if (!isEnabled()) return false
        var delivered = true
        for ((device, forDevice) in events.groupBy { it.device }) {
            for (chunk in forDevice.chunked(TelemetryConfig.MAX_EVENTS)) {
                try {
                    if (!sendChunk(device, chunk)) delivered = false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogUtils.w(TAG, "Dropped ${chunk.size} telemetry events: $e")
                    return false
                }
            }
        }
        return delivered
    }

    private suspend fun sendChunk(device: DeviceContext, events: List<TelemetryEvent>): Boolean {
        val payload = buildPayload(device, events)
        val challengeResponse = client.post(TelemetryConfig.BASE_URL + "challenge") {
            rpcHeaders()
            setBody("{}")
        }
        if (!challengeResponse.status.isSuccess()) {
            LogUtils.w(TAG, "Challenge request failed: ${challengeResponse.status}")
            return false
        }
        val challenge = lenientJson.decodeFromString<Challenge>(challengeResponse.bodyAsText())
        val x = solvePow(challenge.challenge, payload, events.size)

        // The payload is embedded as a string so the server hashes exactly what we did.
        val body = JsonObject(
            mapOf(
                "payload" to JsonPrimitive(payload),
                "challenge" to JsonPrimitive(challenge.challenge),
                "sig" to JsonPrimitive(challenge.sig),
                "x" to JsonPrimitive(x),
            )
        ).toString()
        val response = client.post(TelemetryConfig.BASE_URL + "record") {
            rpcHeaders()
            setBody(body)
        }
        if (response.status.isSuccess()) {
            val types = events.groupingBy { it.event.wire }.eachCount()
            LogUtils.i(TAG, "Sent ${events.size} telemetry events $types")
        } else {
            LogUtils.w(TAG, "Telemetry rejected: ${response.status} ${response.bodyAsText().take(200)}")
        }
        return response.status.isSuccess()
    }

    private fun HttpRequestBuilder.rpcHeaders() {
        header("apikey", TelemetryConfig.PUBLISHABLE_KEY)
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }

    /** The exact JSON text that is hashed and sent. */
    fun buildPayload(device: DeviceContext, events: List<TelemetryEvent>): String =
        compactJson.encodeToString(payloadOf(device, events))

    private fun payloadOf(device: DeviceContext, events: List<TelemetryEvent>) = Payload(
        v = 1,
        platform = platform,
        device = PayloadDevice(device.brand, device.rom, device.romVersion, device.sdk),
        events = events.map { PayloadEvent(it.event.wire, it.pkg, it.cohortWeek, it.batchBucket) },
    )

    /**
     * Community scores by package name, or null if the overall ones couldn't be
     * fetched. With a known ROM in [device] the scores for the ROM family and
     * for its version are fetched too; if either fails, those rows are left out.
     */
    suspend fun fetchScores(device: DeviceContext? = null): Map<String, CommunityScore>? {
        val overall = fetchRows("package_scores", "{}") ?: return null
        val known = device != null && device.hasRom
        // **先在 GitHub 快照里本地筛**，筛不出来才问后端。方向与 PlanRepository.plansAll() 一样：
        // `package_scores_for` 实测 377,695 个数据块，而它要回答的问题只是「这个 ROM 上这个包安不安全」。
        // 那个函数不带身份（同一个 ROM 上所有人看到同一份），所以适合快照；快照里 rom 与
        // rom_version 都在，本地过滤一次网络往返都不用。
        //
        // 快照还没发布过（仓库里没这个文件）、或者两个源都读不到，就退回 `rpc`——那是**正常路径**，
        // 不是错误：这一趟是「内容来源」，失败不该让下面那两条请求跟着不敢发。
        val snapshot = if (known) deviceScoresFromSnapshot(device!!) else SnapshotScores.Empty
        suspend fun forRom(version: Int) = if (snapshot.has(version)) {
            snapshot.of(version)
        } else {
            fetchRows(
                "package_scores_for", """{"p_rom":"${device!!.rom}","p_rom_version":$version}""",
            ).orEmpty()
        }
        val family = if (known) forRom(-1) else emptyMap()
        val forDevice = if (known) forRom(device!!.romVersion) else emptyMap()
        val label = device?.romLabel().orEmpty()
        val familyLabel = if (known) romName(device!!.rom) else ""
        val rom = if (known) device!!.rom else null
        return (overall.keys + family.keys + forDevice.keys).associateWith {
            CommunityScore(overall[it], forDevice[it], label, family[it], rom, familyLabel, device?.romVersion ?: 0)
        }
    }

    /** 快照读成功过一次就记着：内容是所有人一样的，整个会话不必再拉一遍几百 KB。 */
    @Volatile
    private var snapshotGroups: Map<String, Map<Int, Map<String, PackageScore>>>? = null

    /** 上一次「三个源都读不到」的时间；失败后 [SCORES_SNAPSHOT_RETRY_BACKOFF_MS] 内不再去网上试。 */
    @Volatile
    private var snapshotFailedAt: Long = 0L

    /** 同一时刻只让一个调用去拉快照：列表页与详情页可能同时走到这里。 */
    private val snapshotLock = Mutex()

    /** 这个 ROM 在快照里有的那几个版本（`-1` 是整个家族）。 */
    private class SnapshotScores(private val byVersion: Map<Int, Map<String, PackageScore>>) {
        fun has(version: Int) = byVersion.containsKey(version)

        fun of(version: Int): Map<String, PackageScore> = byVersion[version].orEmpty()

        companion object {
            val Empty = SnapshotScores(emptyMap())
        }
    }

    /**
     * 这个设备的 ROM 那几组行，依次从 GitHub Pages、GitHub raw、APK 内置取。
     *
     * 失败不出声：这里不是一次业务请求，读不到就是「这边没有，去问后端」。
     */
    private suspend fun deviceScoresFromSnapshot(device: DeviceContext): SnapshotScores {
        val rom = device.rom.lowercase()
        // 两个网络源都是空串 ⇒ 调用方明确说了「这一趟不读快照」，那内置那份也算快照，一起跳过。
        // 不这么判的话，传了两个空串仍然会命中随包文件，`fetchScores` 就不再问
        // `package_scores_for`（见构造参数上那段注释）。
        if (scoresSnapshotUrl.isBlank() && scoresSnapshotMirrorUrl.isBlank()) return SnapshotScores.Empty
        snapshotGroups?.get(rom)?.let { return SnapshotScores(it) }
        return snapshotLock.withLock {
            // 等锁的这段时间里，别的调用可能已经把这台设备的 ROM 拉下来了。
            snapshotGroups?.get(rom)?.let { return@withLock SnapshotScores(it) }
            val inBackoff = inSnapshotBackoff()
            var fetched: Map<String, Map<Int, Map<String, PackageScore>>>? = null
            for (url in listOf(scoresSnapshotMirrorUrl, scoresSnapshotUrl)) {
                if (url.isBlank() || inBackoff) continue
                val text = runCatching {
                    withTimeoutOrNull(SCORES_SNAPSHOT_TIMEOUT_MS) {
                        val response = client.get(url)
                        if (response.status.isSuccess()) response.bodyAsText() else null
                    }
                }.getOrNull() ?: continue
                val groups = runCatching { parseScoresSnapshot(text) }.getOrDefault(emptyMap())
                // 解析出一个空表多半是文件被写坏了：那也算没读到，接着试下一个源。
                if (groups.isEmpty()) continue
                fetched = groups
                break
            }
            if (fetched != null) {
                snapshotGroups = fetched
                snapshotFailedAt = 0L
                // 快照读得到，但里面没有这个 ROM（小众 ROM 不会进快照）——那就是「这边没有」，
                // 别再去试第二个源，也别记成失败。
                return@withLock SnapshotScores(fetched[rom].orEmpty())
            }
            if (inBackoff) return@withLock SnapshotScores.Empty
            // 调用方说不要内置那份（测试）：网络源已经全试过了，这里到此为止。
            if (!allowBundledSnapshot) return@withLock SnapshotScores.Empty
            snapshotFailedAt = currentTimeMillis()
            val bundled = runCatching {
                parseScoresRows(Res.readBytes(BUNDLED_SCORES_PATH).decodeToString())
            }.getOrDefault(emptyList())
            if (bundled.isEmpty()) return@withLock SnapshotScores.Empty
            // 内置那份是扁平行，按 rom / rom_version 现分组；某个版本没行就不算「有」，
            // 那一问照常去问后端。
            val grouped = bundled.groupBy { it.rom.lowercase() }.mapValues { (_, rows) ->
                rows.groupBy { it.romVersion }.mapValues { (_, ofVersion) ->
                    ofVersion.associate { it.score.pkg to it.score }
                }
            }
            snapshotGroups = grouped
            SnapshotScores(grouped[rom].orEmpty())
        }
    }

    private fun inSnapshotBackoff(): Boolean =
        snapshotFailedAt != 0L && currentTimeMillis() - snapshotFailedAt < SCORES_SNAPSHOT_RETRY_BACKOFF_MS

    private suspend fun fetchRows(function: String, body: String): Map<String, PackageScore>? = try {
        val response = client.post(TelemetryConfig.BASE_URL + function) {
            rpcHeaders()
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            LogUtils.w(TAG, "Fetching $function failed: ${response.status}")
            null
        } else {
            parseScores(response.bodyAsText())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LogUtils.w(TAG, "Fetching $function failed: $e")
        null
    }
}

/**
 * RPC（`package_scores` / `package_scores_for`）的一行数组 → 包名到分数的表。
 *
 * 用显式 `map {}` 再 `toMap()`，不靠 `associateBy(...).mapValues { }` 的类型推断：
 * 后者在 Kotlin 里会因为 `associateBy` 的第二个类型参数被推断成 `Map<String, SnapshotScoreRow>`
 * 而让整条表达式报 return type mismatch（2026-11-27 CI 就是这么红的）。
 */
internal fun parseScores(json: String): Map<String, PackageScore> =
    parseScoresRows(json).map { it.score.pkg to it.score }.toMap()

/**
 * 一份 JSON 数组行 → 分数行。快照与 `package_scores_for` 的返回**同形状**（列名一致），
 * 所以两条路共用这一支；丢掉读不出 pkg 的行，一个坏行不该毁掉整份。
 *
 * [SnapshotScoreRow.rom] / [SnapshotScoreRow.romVersion] 只在**扁平**那份内置快照里才有值；
 * RPC 返回本来就已经按 ROM 筛过，那两列是空串 / 0。
 */
internal fun parseScoresRows(json: String): List<SnapshotScoreRow> =
    Json.parseToJsonElement(json).jsonArray.mapNotNull { element ->
        val o = element.jsonObject
        val pkg = o["pkg"]?.jsonPrimitive?.content ?: return@mapNotNull null
        SnapshotScoreRow(
            rom = o["rom"]?.jsonPrimitive?.content.orEmpty(),
            romVersion = o["rom_version"]?.jsonPrimitive?.intOrNull ?: 0,
            score = PackageScore(
                pkg = pkg,
                uninstalls = o["uninstalls"]?.jsonPrimitive?.intOrNull ?: 0,
                smallBatch = o["small_batch"]?.jsonPrimitive?.booleanOrNull ?: false,
                verdict = Verdict.parse(o["verdict"]?.jsonPrimitive?.content),
                updatedAt = o["updated_at"]?.jsonPrimitive?.content,
                mainRom = (o["main_rom"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content,
                mainRomShare = o["main_rom_share"]?.jsonPrimitive?.doubleOrNull,
                mainVersion = o["main_version"]?.jsonPrimitive?.intOrNull,
                mainVersionShare = o["main_version_share"]?.jsonPrimitive?.doubleOrNull,
            ),
        )
    }

/** 快照里的一行：哪个 ROM、哪个版本、这个包的分数。 */
internal data class SnapshotScoreRow(val rom: String, val romVersion: Int, val score: PackageScore)

/**
 * `site/db/package_scores.json` → ROM（小写）→ ROM 版本 → 包名 → 分数。
 *
 * 文件形如 `[{"rom":"hyperos","rom_version":-1,"scores":[...]}]`，`-1` 是整个家族
 * （后端 `package_scores_for` 的约定）。行内列名与那个 RPC 的返回逐列对齐，只是外面多套了
 * rom / rom_version：它把**每一个** ROM 的答案都放进了同一个文件。
 */
internal fun parseScoresSnapshot(text: String): Map<String, Map<Int, Map<String, PackageScore>>> {
    val root = Json.parseToJsonElement(text).jsonArray
    return root.fold(mutableMapOf()) { acc, group ->
        val o = group as? JsonObject ?: return@fold acc
        val rom = o["rom"]?.jsonPrimitive?.content?.lowercase().orEmpty()
        val version = o["rom_version"]?.jsonPrimitive?.intOrNull ?: return@fold acc
        if (rom.isBlank()) return@fold acc
        val scores = (o["scores"] as? JsonArray).orEmpty().mapNotNull { row ->
            val e = row as? JsonObject ?: return@mapNotNull null
            parseScoresRows("[$e]").firstOrNull()
        }.associate { it.score.pkg to it.score }
        if (scores.isNotEmpty()) acc[rom] = acc[rom].orEmpty() + (version to scores)
        acc
    }
}
