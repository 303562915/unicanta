package io.github.samolego.canta.data.community

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The model chips: how rows fold into models, how families group them, and when a count is shown. */
class DeviceCatalogTest {

    private fun build(
        brand: String = "Xiaomi",
        model: String = "23113RKC6C",
        marketName: String = "Redmi K70",
        rom: String = "hyperos",
        romVersion: Int = 2,
        plans: Int = 1,
        people: Int = 1,
        modelPlans: Int = plans,
        modelPeople: Int = people,
    ) = CatalogBuild(
        brand = brand,
        model = model,
        marketName = marketName,
        rom = rom,
        romVersion = romVersion,
        plans = plans,
        people = people,
        modelPlans = modelPlans,
        modelPeople = modelPeople,
    )

    @Test
    fun buildsOfOneModelFoldIntoOneModel() {
        val models = listOf(
            build(romVersion = 1),
            build(romVersion = 2),
            build(brand = "OPPO", model = "CPH2581", marketName = "Find X7"),
        ).toCatalogModels()

        assertEquals(2, models.size)
        assertEquals("Redmi K70", models[0].label)
        assertEquals(2, models[0].versions)
        // Newest build first: that is the one a visitor most likely runs.
        assertEquals(listOf(2, 1), models[0].builds.map { it.romVersion })
        assertEquals("Find X7", models[1].label)
    }

    @Test
    fun modelCountsComeFromTheModelLevelColumns() {
        val model = listOf(
            build(romVersion = 2, plans = 3, people = 2, modelPlans = 9, modelPeople = 12),
            build(romVersion = 1, plans = 6, people = 4, modelPlans = 9, modelPeople = 12),
        ).toCatalogModels().single()

        assertEquals(9, model.plans)
        assertEquals(12, model.people)
    }

    @Test
    fun labelFallsBackToTheRetailModelNumber() {
        val model = listOf(build(marketName = "")).toCatalogModels().single()
        assertEquals("23113RKC6C", model.label)
    }

    @Test
    fun familiesFollowTheBrandThenTheModel() {
        assertEquals(DeviceFamily.XIAOMI, familyOf("Xiaomi"))
        assertEquals(DeviceFamily.XIAOMI, familyOf("unknown", "Redmi K70"))
        assertEquals(DeviceFamily.XIAOMI, familyOf("Xiaomi", "POCO F6"))
        assertEquals(DeviceFamily.OPPO, familyOf("OPPO"))
        assertEquals(DeviceFamily.VIVO, familyOf("vivo"))
        assertEquals(DeviceFamily.VIVO, familyOf("vivo", "iQOO 12"))
        assertEquals(DeviceFamily.ONE_PLUS, familyOf("OnePlus"))
        assertEquals(DeviceFamily.REALME, familyOf("realme"))
        assertEquals(DeviceFamily.SAMSUNG, familyOf("samsung"))
        assertEquals(DeviceFamily.OTHER, familyOf("unknown", "Some Phone"))
    }

    @Test
    fun groupingPutsXiaomiFirstAndTheRestLast() {
        val groups = listOf(
            build(brand = "unknown", model = "X", marketName = "Mystery"),
            build(brand = "vivo", model = "V2309A", marketName = "X100"),
            build(brand = "Xiaomi", model = "23113RKC6C", marketName = "Redmi K70"),
        ).toCatalogModels().groupedByFamily()

        assertEquals(
            listOf(DeviceFamily.XIAOMI, DeviceFamily.VIVO, DeviceFamily.OTHER),
            groups.map { it.family },
        )
    }

    @Test
    fun modelsInsideAFamilySortByPeople() {
        val groups = listOf(
            build(model = "a", marketName = "Redmi A", modelPeople = 2),
            build(model = "b", marketName = "Redmi B", modelPeople = 30),
            build(model = "c", marketName = "Redmi C", modelPeople = 7),
        ).toCatalogModels().groupedByFamily()

        assertEquals(
            listOf("Redmi B", "Redmi C", "Redmi A"),
            groups.single().models.map { it.label },
        )
    }

    @Test
    fun onlyMoreThanFivePeopleAreWorthPrinting() {
        assertEquals("", peopleSuffix(0))
        assertEquals("", peopleSuffix(5))
        assertEquals(" · 6人", peopleSuffix(6))
        assertEquals(" · 120人", peopleSuffix(120))
    }

    @Test
    fun aPlanKnowsWhetherItFitsThisPhone() {
        val profile = io.github.samolego.canta.util.DeviceProfile(
            brand = "Xiaomi",
            model = "23113RKC6C",
            device = "vermeer",
            sdk = 35,
            release = "15",
            rom = "hyperos",
            romVersion = 2,
            fingerprint = "x",
            marketName = "Redmi K70",
        )
        val mine = plan(model = "23113RKC6C", rom = "hyperos", romVersion = 2)
        val otherModel = plan(model = "23127PN0CC", marketName = "Xiaomi 14", rom = "hyperos", romVersion = 2)
        val otherBuild = plan(model = "23113RKC6C", rom = "hyperos", romVersion = 1)
        val unknown = plan(model = "", rom = "")

        assertTrue(mine.matches(profile))
        assertFalse(otherModel.matches(profile))
        assertFalse(otherBuild.matches(profile))
        // An old row says nothing about its device: it must not be called a mismatch.
        assertTrue(unknown.matches(profile))
        assertTrue(otherModel.matches(null))
    }

    @Test
    fun deviceLabelReadsLikeTheRestOfTheApp() {
        assertEquals(
            "Xiaomi 14 · HyperOS 2",
            plan(model = "23127PN0CC", marketName = "Xiaomi 14", rom = "hyperos", romVersion = 2).deviceLabel,
        )
        assertEquals("", plan(model = "", rom = "").deviceLabel)
    }

    @Test
    fun deviceLabelNamesTheUploadersBuildNotTheReaders() {
        val uploader = plan(
            model = "23127PN0CC",
            marketName = "Xiaomi 14",
            rom = "hyperos",
            romVersion = 2,
            sdk = 35,
        )
        // The level is the row's own, so the line ends with the uploader's Android version.
        assertEquals("Xiaomi 14 · HyperOS 2 · Android 15", uploader.deviceLabel)

        // A row that predates the column stops after the ROM: no version is invented for it.
        assertEquals(
            "Xiaomi 14 · HyperOS 2",
            plan(model = "23127PN0CC", marketName = "Xiaomi 14", rom = "hyperos", romVersion = 2).deviceLabel,
        )
    }

    private fun plan(
        model: String,
        marketName: String = "",
        rom: String,
        romVersion: Int = 0,
        sdk: Int = 0,
    ) = CommunityPlan(
        id = 1,
        title = "plan",
        note = "",
        packages = listOf("com.example.a"),
        votes = 1,
        comments = 0,
        createdAt = "2026-11-19T00:00:00Z",
        rebootedAt = "2026-11-19T00:00:00Z",
        model = model,
        marketName = marketName,
        rom = rom,
        romVersion = romVersion,
        sdk = sdk,
    )
}
