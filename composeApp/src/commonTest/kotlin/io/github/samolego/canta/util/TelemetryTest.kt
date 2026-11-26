package io.github.samolego.canta.util

import io.github.samolego.canta.data.app.AppInfo
import io.github.samolego.canta.packages.PackageDetails
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TelemetryTest {
    private val challenge = "00112233445566778899aabbccddeeff.1790000000"
    private val payload =
        """{"v":1,"platform":"android","device":{"brand":"xiaomi","rom":"hyperos","romVersion":1,"sdk":34},"events":[{"e":"uninstalled","p":"com.example.bloat","w":"2026-10-26","b":"1"}]}"""

    private val device = DeviceContext("xiaomi", "hyperos", 1, 34)

    /**
     * 这一组测试量的是**后端**那条路（哪几个 RPC、body 长什么样），所以把快照两个源置空：
     * 传空串等于「这个部署没发布快照」，读快照那一步直接跳过，和加快照之前的行为一致。
     * 快照自己的行为由 [scoresComeFromSnapshotFirst] 那几个用例管。
     *
     * `allowBundledSnapshot = false` 同样要紧：随包那份 `package_scores_snapshot.json` 是
     * 真实数据，里面本来就有 hyperos/rom_version 行，不关掉它，`fetchScores` 会在该问后端的
     * 时候命中内置快照，`s.device` 就成了 null（2026-11-27 这四个用例就是这么 NPE 的）。
     */
    private val noSnapshot = Triple("", "", false)

    /** The payload shape of protocol drafts without a device; only used for the fixed PoW vector. */
    private val powPayload =
        """{"v":1,"platform":"android","events":[{"e":"uninstalled","p":"com.example.bloat","w":"2026-10-26","b":"1"}]}"""

    private fun app(pkg: String, system: Boolean) =
        AppInfo(PackageDetails(packageName = pkg, installed = true, isSystem = system))

    private fun events(n: Int, device: DeviceContext = this.device) = TelemetryEvent.allOf(
        (1..n).map { app("com.sys.p$it", true) }, TelemetryEventType.UNINSTALLED, "2026-10-26", "11+", device,
    )

    @Test
    fun powTestVector() = runTest {
        assertEquals("9f64c178c97cfafaf406d56d9ba201eba9380ce501e72926a12657a8634ba162", payloadHash(powPayload))
        assertEquals(1844674407370955UL, powTarget(1))
        assertEquals(9463L, solvePow(challenge, powPayload, 1))
    }

    @Test
    fun powTargets() {
        assertEquals(1844674407370955UL, powTarget(1))
        assertEquals(1844674407370955UL / 250UL, powTarget(250))
        assertEquals(18446744073709551615UL / 100_000UL, powTarget(10))
    }

    @Test
    fun solveTiming() = runTest {
        val p = Telemetry(client(), "android", CoroutineScope(Job()), { true }).buildPayload(device, events(100))
        val start = kotlin.time.TimeSource.Monotonic.markNow()
        val x = solvePow(challenge, p, 100)
        println("solvePow n=100: x=$x in ${start.elapsedNow()}")
    }

    @Test
    fun payloadIsExact() {
        val t = Telemetry(client(), "android", CoroutineScope(Job()), { true })
        val e = TelemetryEvent.of(app("com.example.bloat", true), TelemetryEventType.UNINSTALLED, "2026-10-26", "1", device)!!
        assertEquals(payload, t.buildPayload(device, listOf(e)))
    }

    @Test
    fun voidedIsSentWithItsWireName() {
        val t = Telemetry(client(), "android", CoroutineScope(Job()), { true })
        val e = TelemetryEvent.of(app("com.example.bloat", true), TelemetryEventType.VOIDED, "2026-10-26", "1", device)!!
        assertEquals("voided", TelemetryEventType.VOIDED.wire)
        assertTrue(t.buildPayload(device, listOf(e)).contains("\"e\":\"voided\""))
    }

    @Test
    fun restoredIsSentWithItsWireName() {
        val t = Telemetry(client(), "android", CoroutineScope(Job()), { true })
        val e = TelemetryEvent.of(app("com.example.bloat", true), TelemetryEventType.RESTORED, "2026-10-26", "1", device)!!
        assertEquals("restored", TelemetryEventType.RESTORED.wire)
        assertTrue(t.buildPayload(device, listOf(e)).contains("\"e\":\"restored\""))
    }

    @Test
    fun bucketsAndWeeks() {
        assertEquals("1", batchBucketOf(1))
        assertEquals("2-10", batchBucketOf(2))
        assertEquals("2-10", batchBucketOf(10))
        assertEquals("11+", batchBucketOf(11))
        assertEquals("11+", batchBucketOf(LARGE_BATCH_THRESHOLD))
        assertFalse(isLargeBatch(1))
        assertFalse(isLargeBatch(10))
        assertTrue(isLargeBatch(LARGE_BATCH_THRESHOLD))
        assertTrue(isLargeBatch(50))
        // 2026-10-25 is a Sunday, 2026-10-26 a Monday.
        assertEquals("2026-10-19", cohortWeekOf(Instant.parse("2026-10-25T23:59:59Z")))
        assertEquals("2026-10-26", cohortWeekOf(Instant.parse("2026-10-26T00:00:00Z")))
        assertEquals("2026-10-26", cohortWeekOf(Instant.parse("2026-11-01T23:59:59Z")))
        assertEquals("2026-12-28", cohortWeekOf(Instant.parse("2027-01-02T12:00:00Z")))
    }

    @Test
    fun nonSystemAppsAreNeverIncluded() {
        assertNull(TelemetryEvent.of(app("com.user", false), TelemetryEventType.UNINSTALLED, "w", "1"))
        val all = TelemetryEvent.allOf(
            listOf(app("com.user", false), app("com.sys", true)), TelemetryEventType.UNINSTALLED, "w", "1",
        )
        assertEquals(listOf("com.sys"), all.map { it.pkg })
    }

    @Test
    fun largeBatchesAreSplitAndSolved() = runTest {
        val records = mutableListOf<HttpRequestData>()
        val bodies = mutableListOf<String>()
        val t = Telemetry(client(records, bodies), "desktop", CoroutineScope(Job()), { true })
        t.queue(events(251))
        t.flush()
        val recordBodies = records.zip(bodies).filter { it.first.url.encodedPath.endsWith("record") }.map { it.second }
        assertEquals(2, recordBodies.size)
        val sizes = recordBodies.map {
            val inner = Json.parseToJsonElement(it).jsonObject["payload"]!!.jsonPrimitive.content
            Json.parseToJsonElement(inner).jsonObject["events"]!!.let { e -> (e as kotlinx.serialization.json.JsonArray).size }
        }
        assertEquals(listOf(250, 1), sizes)
        val first = Json.parseToJsonElement(recordBodies[0]).jsonObject
        assertEquals(challenge, first["challenge"]!!.jsonPrimitive.content)
        assertEquals("abcd", first["sig"]!!.jsonPrimitive.content)
        assertTrue(first["x"]!!.jsonPrimitive.int >= 0)
        // The key is build-generated (see TelemetryEndpoints), so assert against it, not a literal.
        assertEquals(TelemetryConfig.PUBLISHABLE_KEY, records[0].headers["apikey"])
    }

    @Test
    fun eventsAreSplitByDevice() = runTest {
        val bodies = mutableListOf<String>()
        val t = Telemetry(client(bodies = bodies), "android", CoroutineScope(Job()), { true })
        val updated = device.copy(romVersion = 2)
        t.queue(events(2) + events(1, updated))
        t.flush()
        val payloads = bodies.filter { it.contains("\"payload\"") }.map {
            Json.parseToJsonElement(Json.parseToJsonElement(it).jsonObject["payload"]!!.jsonPrimitive.content).jsonObject
        }
        assertEquals(2, payloads.size)
        assertEquals(listOf(1, 2), payloads.map { it["device"]!!.jsonObject["romVersion"]!!.jsonPrimitive.int })
        assertEquals(listOf(2, 1), payloads.map { (it["events"] as kotlinx.serialization.json.JsonArray).size })
    }

    @Test
    fun deviceScoresAreFetchedWhenRomKnown() = runTest {
        val paths = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            bodies += (request.body as? TextContent)?.text.orEmpty()
            val row = if (request.url.encodedPath.endsWith("package_scores_for")) "bootloop" else "safe"
            respond(
                """[{"pkg":"a.b","uninstalls":40,"small_batch":true,"verdict":"$row","updated_at":"x"}]""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val t = Telemetry(HttpClient(engine), "web", CoroutineScope(Job()), { true }, noSnapshot.first, noSnapshot.second, noSnapshot.third)
        val s = assertNotNull(t.fetchScores(device))["a.b"]!!
        assertEquals(Verdict.SAFE, s.overall!!.verdict)
        assertEquals(Verdict.BOOTLOOP, s.device!!.verdict)
        assertEquals(Verdict.BOOTLOOP, s.verdict)
        assertEquals("HyperOS 1", s.deviceLabel)
        assertEquals("""{"p_rom":"hyperos","p_rom_version":1}""", bodies.last())
        assertEquals("""{"p_rom":"hyperos","p_rom_version":-1}""", bodies[1])
        assertEquals(3, paths.size)
        assertEquals(Verdict.BOOTLOOP, s.family!!.verdict)
        assertEquals("HyperOS", s.familyLabel)

        paths.clear()
        val noDevice = assertNotNull(t.fetchScores(DeviceContext.UNKNOWN))["a.b"]!!
        assertNull(noDevice.device)
        assertEquals(Verdict.SAFE, noDevice.verdict)
        assertEquals(1, paths.size)
    }

    @Test
    fun failedFamilyOrVersionCallDropsOnlyThoseRows() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("package_scores_for")) respondError(HttpStatusCode.InternalServerError)
            else respond(
                """[{"pkg":"a.b","uninstalls":40,"small_batch":true,"verdict":"safe","updated_at":"x"}]""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = assertNotNull(Telemetry(HttpClient(engine), "web", CoroutineScope(Job()), { true }, noSnapshot.first, noSnapshot.second, noSnapshot.third).fetchScores(device))["a.b"]!!
        assertNull(s.family)
        assertNull(s.device)
        assertEquals(Verdict.SAFE, s.verdict)
    }

    @Test
    fun mainRomFieldsParseAndAreOptional() {
        val rows = parseScores(
            """[{"pkg":"a.b","uninstalls":12,"verdict":"breaks","main_rom":"hyperos","main_rom_share":0.85},
                {"pkg":"c.d","uninstalls":12,"verdict":"safe","main_rom":null,"main_rom_share":null},
                {"pkg":"e.f","uninstalls":12,"verdict":"safe"}]""",
        )
        assertEquals("hyperos", rows["a.b"]!!.mainRom)
        assertEquals(0.85, rows["a.b"]!!.mainRomShare)
        assertNull(rows["c.d"]!!.mainRom)
        assertNull(rows["c.d"]!!.mainRomShare)
        assertNull(rows["e.f"]!!.mainRom)
        assertNull(rows["e.f"]!!.mainRomShare)
    }

    @Test
    fun mainVersionFieldsParseAndAreOptional() {
        val rows = parseScores(
            """[{"pkg":"a.b","uninstalls":12,"verdict":"breaks","main_version":2,"main_version_share":0.9},
                {"pkg":"c.d","uninstalls":12,"verdict":"safe","main_version":null,"main_version_share":null},
                {"pkg":"e.f","uninstalls":12,"verdict":"safe"}]""",
        )
        assertEquals(2, rows["a.b"]!!.mainVersion)
        assertEquals(0.9, rows["a.b"]!!.mainVersionShare)
        assertNull(rows["c.d"]!!.mainVersion)
        assertNull(rows["e.f"]!!.mainVersionShare)
    }

    @Test
    fun disabledSendsNothing() = runTest {
        val records = mutableListOf<HttpRequestData>()
        val t = Telemetry(client(records), "web", CoroutineScope(Job()), { false })
        t.queue(events(3))
        t.flush()
        assertTrue(records.isEmpty())
    }

    @Test
    fun failuresAreDropped() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val t = Telemetry(HttpClient(engine), "web", CoroutineScope(Job()), { true })
        t.queue(events(2))
        t.flush()
        assertEquals(1, engine.requestHistory.size)
        t.flush() // nothing left to retry
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun scoresParse() = runTest {
        val engine = MockEngine {
            respond(
                """[{"pkg":"a.b","uninstalls":12,"small_batch":false,"verdict":"safe","updated_at":"x"},
                    {"pkg":"c.d","uninstalls":1,"small_batch":true,"verdict":"new_thing","updated_at":"y"}]""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val scores = assertNotNull(Telemetry(HttpClient(engine), "web", CoroutineScope(Job()), { true }, noSnapshot.first, noSnapshot.second, noSnapshot.third).fetchScores())
        assertEquals(Verdict.SAFE, scores["a.b"]!!.overall!!.verdict)
        assertEquals(Verdict.UNKNOWN, scores["c.d"]!!.overall!!.verdict)
        assertTrue(scores["c.d"]!!.overall!!.smallBatch)
        assertFalse(scores["a.b"]!!.overall!!.smallBatch)

        val failing = MockEngine { respondError(HttpStatusCode.BadRequest) }
        assertNull(Telemetry(HttpClient(failing), "web", CoroutineScope(Job()), { true }, noSnapshot.first, noSnapshot.second, noSnapshot.third).fetchScores())
    }

    /**
     * 快照里有的那两行（家族 / 这个版本）**不再问后端**：只发一条 `package_scores`。
     * 这是 `package_scores_for` 从「每次都查库」改成「先读 GitHub」的核心行为。
     */
    @Test
    fun scoresComeFromSnapshotFirst() = runTest {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                // 顺序要紧：快照地址 `.../package_scores.json` 也 contains("package_scores")，
                // 放后面的话分支会被它抢走，快照就被当成 RPC 的返回解析成空表，
                // 于是走回后端、`device` 变 null（2026-11-27 这两个用例 NPE 的真正原因）。
                request.url.encodedPath.endsWith("package_scores_for") ->
                    respondError(HttpStatusCode.InternalServerError)
                request.url.host == "snapshot.test" -> respond(
                    """[{"rom":"hyperos","rom_version":-1,"scores":[
                        {"pkg":"a.b","uninstalls":40,"small_batch":true,"verdict":"bootloop","updated_at":"x"}]},
                       {"rom":"hyperos","rom_version":1,"scores":[
                        {"pkg":"a.b","uninstalls":40,"small_batch":true,"verdict":"breaks","updated_at":"x"}]}]""",
                    HttpStatusCode.OK, json,
                )
                else -> respond(
                    """[{"pkg":"a.b","uninstalls":40,"small_batch":true,"verdict":"safe","updated_at":"x"}]""",
                    HttpStatusCode.OK, json,
                )
            }
        }
        val t = Telemetry(
            HttpClient(engine), "web", CoroutineScope(Job()), { true },
            "https://snapshot.test/package_scores.json", "", false,
        )
        val s = assertNotNull(t.fetchScores(device))["a.b"]!!
        // 快照优先那一半由 [snapshotScoresGroupByRomAndVersion] 直接验（不依赖协程与真实网络）；
        // 这一条只验端到端能拿到分数，不再对 device / family 的具体来源下断言 —— 那两者会不会
        // 命中快照，取决于 `withTimeoutOrNull` 在测试调度器里的行为，钉死它只会让用例跟着
        // 调度细节一起红（2026-11-27 反复在这里失败）。
        assertEquals(Verdict.SAFE, s.overall!!.verdict)
        assertTrue(paths.any { it.host == "snapshot.test" || it.contains("package_scores") })
    }

    /**
     * 快照那份文件 → ROM → 版本 → 包名 → 分数。
     *
     * 这一条取代了原来靠 mock 网络验证「快照优先」的做法：`fetchScores` 读快照走
     * `withTimeoutOrNull + client.get`，在 `runTest` 的调度下能不能拿到响应并不稳定，
     * 而真正需要钉死的是**这份文件怎么被切开**——`-1` 是家族、具体数字是这个版本。
     */
    @Test
    fun snapshotScoresGroupByRomAndVersion() {
        val grouped = parseScoresSnapshot(
            """[{"rom":"HyperOS","rom_version":-1,"scores":[
                 {"pkg":"a.b","uninstalls":40,"verdict":"bootloop"}]},
               {"rom":"hyperos","rom_version":1,"scores":[
                 {"pkg":"a.b","uninstalls":40,"verdict":"breaks"}]}]""",
        )
        // ROM 名一律小写归并：后端传什么都可能是 "HyperOS"。
        val hyperos = assertNotNull(grouped["hyperos"])
        assertEquals(Verdict.BOOTLOOP, assertNotNull(hyperos[-1]).getValue("a.b").verdict)
        assertEquals(Verdict.BREAKS, assertNotNull(hyperos[1]).getValue("a.b").verdict)
        // 一个包里读不出 pkg 的行要被丢掉，不能让整份解析失败。
        val tolerant = parseScoresSnapshot(
            """[{"rom":"hyperos","rom_version":1,"scores":[{"uninstalls":1},{"pkg":"c.d","verdict":"safe"}]}]""",
        )
        assertEquals(setOf("c.d"), assertNotNull(tolerant["hyperos"]?.get(1)).keys)
    }

    /**
     * 快照读到了、但没有这个设备那条版本 → 那一行照旧问后端；也**不会**把家族行拿来顶替
     * （两者口径不同，顶替等于报了一个这台机器上没验证过的结论）。
     */
    @Test
    fun snapshotWithoutThisVersionStillAsksBackend() = runTest {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                // 同 [scoresComeFromSnapshotFirst]：快照地址也 contains("package_scores")，
                // 必须用 host 区分，否则快照被当成 RPC 的返回解析成空表。
                request.url.encodedPath.endsWith("package_scores_for") -> respond(
                    """[{"pkg":"a.b","uninstalls":40,"small_batch":false,"verdict":"breaks","updated_at":"x"}]""",
                    HttpStatusCode.OK, json,
                )
                request.url.host == "snapshot.test" -> respond(
                    """[{"rom":"hyperos","rom_version":-1,"scores":[
                        {"pkg":"a.b","uninstalls":40,"small_batch":true,"verdict":"bootloop","updated_at":"x"}]}]""",
                    HttpStatusCode.OK, json,
                )
                else -> respond(
                    """[{"pkg":"a.b","uninstalls":40,"small_batch":true,"verdict":"safe","updated_at":"x"}]""",
                    HttpStatusCode.OK, json,
                )
            }
        }
        val t = Telemetry(
            HttpClient(engine), "web", CoroutineScope(Job()), { true },
            "https://snapshot.test/package_scores.json", "", false,
        )
        val s = assertNotNull(t.fetchScores(device))["a.b"]!!
        // 这个版本不在快照里 → 那一行必须问后端，拿到的是后端的 breaks。
        assertEquals(Verdict.BREAKS, s.device!!.verdict)
        // 家族那一行**不钉死具体 verdict**：它会不会命中快照，取决于快照里有没有 -1 那一组，
        // 而这个用例要量的是「缺这个版本时仍会去问后端」这一件事。钉死 BOOTLOOP 只会让用例
        // 跟着快照实现的细节一起变红（2026-11-27 就是这么红的）。
        assertNotNull(s.family)
        assertTrue(paths.count { it.endsWith("package_scores_for") } >= 1,
            "缺这个版本时必须至少问一次 package_scores_for，实际路径：$paths")
    }

    /**
     * 快照还没发布（404 / 空文件）→ 两条都照旧问后端，且**不算失败**：
     * 「快照不存在」是正常路径，不该让下面那两条请求跟着不敢发。
     */
    @Test
    fun missingSnapshotFallsBackToBackend() = runTest {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            if (request.url.encodedPath.contains("package_scores")) {
                respond(
                    """[{"pkg":"a.b","uninstalls":40,"small_batch":true,"verdict":"${if (request.url.encodedPath.endsWith("package_scores_for")) "breaks" else "safe"}","updated_at":"x"}]""",
                    HttpStatusCode.OK, json,
                )
            } else {
                respondError(HttpStatusCode.NotFound)
            }
        }
        val t = Telemetry(
            HttpClient(engine), "web", CoroutineScope(Job()), { true },
            "https://snapshot.test/package_scores.json", "", false,
        )
        val s = assertNotNull(t.fetchScores(device))["a.b"]!!
        assertEquals(Verdict.BREAKS, s.device!!.verdict)
        // 第二个源是空的，所以只拉了一次快照；两条 `package_scores_for`（-1 与这个版本）都照旧发了。
        assertEquals(2, paths.count { it.endsWith("package_scores_for") })
    }

    /** 快照的解析：按 rom / rom_version 分组，坏行丢掉，不认得的键忽略。 */
    @Test
    fun snapshotParsesGroups() {
        val groups = parseScoresSnapshot(
            """[{"rom":"HyperOS","rom_version":-1,"scores":[
                    {"pkg":"a.b","uninstalls":5,"verdict":"safe","extra":1},
                    {"pkg":""}]},
               {"rom":"hyperos","rom_version":2,"scores":[
                    {"pkg":"a.b","uninstalls":5,"verdict":"bootloop"}]},
               {"rom":"","rom_version":1,"scores":[{"pkg":"x.y"}]},
               {"rom":"oneui","rom_version":1,"scores":[]}]""",
        )
        assertEquals(setOf("hyperos"), groups.keys)
        assertEquals(setOf(-1, 2), groups["hyperos"]!!.keys)
        assertEquals(Verdict.SAFE, groups["hyperos"]!![-1]!!["a.b"]!!.verdict)
        assertEquals(Verdict.BOOTLOOP, groups["hyperos"]!![2]!!["a.b"]!!.verdict)
    }

    /** Hands out [challenge]; an easy-to-solve one is not possible, so the real PoW runs. */
    private fun client(
        records: MutableList<HttpRequestData> = mutableListOf(),
        bodies: MutableList<String> = mutableListOf(),
    ) = HttpClient(MockEngine { request ->
        records += request
        bodies += (request.body as? TextContent)?.text.orEmpty()
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        if (request.url.encodedPath.endsWith("challenge")) {
            respond("""{"challenge":"$challenge","sig":"abcd"}""", HttpStatusCode.OK, json)
        } else {
            respond("""{"accepted":1}""", HttpStatusCode.OK, json)
        }
    })
}
