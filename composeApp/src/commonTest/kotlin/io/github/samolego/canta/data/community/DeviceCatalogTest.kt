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

    /**
     * 同一台机器报了两种 brand，列表里只能是一条。
     *
     * 真实数据：`25102RKBEC`（REDMI K90 Pro Max）有人报 `ro.product.manufacturer = Xiaomi`、
     * 有人报 `Redmi`，后端因此给了两行；用 `(brand, model)` 当聚合键的话，机型列表里就是
     * 两个一模一样的「REDMI K90 Pro Max」（2026-11-28 用户报的）。
     */
    @Test
    fun sameModelUnderTwoBrandsIsOneModel() {
        val models = listOf(
            build(brand = "Xiaomi", model = "25102RKBEC", marketName = "REDMI K90 Pro Max", romVersion = 4),
            build(brand = "Redmi", model = "25102RKBEC", marketName = "REDMI K90 Pro Max", romVersion = 4),
        ).toCatalogModels()

        assertEquals(1, models.size)
        assertEquals("REDMI K90 Pro Max", models.single().label)
        // 两个 brand 说的是同一个 build：去重之后只留一条，而不是两条一模一样的版本。
        assertEquals(1, models.single().builds.size)
    }

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
    fun modelsInsideAFamilyGoByNumberNotByPeople() {
        val groups = listOf(
            build(model = "a", marketName = "REDMI K70", modelPeople = 900),
            build(model = "b", marketName = "REDMI K100 Pro Max", modelPeople = 2),
            build(model = "c", marketName = "Xiaomi 17 Pro", modelPeople = 30),
            build(model = "d", marketName = "Xiaomi 18", modelPeople = 7),
        ).toCatalogModels().groupedByFamily()

        assertEquals(
            listOf("Xiaomi 18", "Xiaomi 17 Pro", "REDMI K100 Pro Max", "REDMI K70"),
            groups.single().models.map { it.label },
        )
    }

    @Test
    fun aStrongerVariantOfTheSameNumberComesFirst() {
        val ordered = listOf(
            build(model = "1", marketName = "Xiaomi 18"),
            build(model = "2", marketName = "Xiaomi 18 SE"),
            build(model = "3", marketName = "Xiaomi 18 Pro"),
            build(model = "4", marketName = "Xiaomi 18 Pro Max"),
        ).toCatalogModels().sortedForPicker().map { it.label }

        assertEquals(
            listOf("Xiaomi 18 Pro Max", "Xiaomi 18 Pro", "Xiaomi 18", "Xiaomi 18 SE"),
            ordered,
        )
    }

    @Test
    fun aRetailIdHasNoGenerationAndGoesLast() {
        assertEquals(0, modelNumber("23113RKC6C"))
        assertEquals(0, modelNumber("SM-F9710"))
        assertEquals(18, modelNumber("小米 18 Pro Max"))
        assertEquals(100, modelNumber("REDMI K100 Pro Max"))
        assertEquals(25, modelNumber("Galaxy S25 Ultra"))
    }

    @Test
    fun onlyMoreThanFiftyPeopleAreWorthPrinting() {
        assertEquals("", peopleSuffix(0))
        assertEquals("", peopleSuffix(50))
        assertEquals("", peopleSuffix(49))
        assertEquals(" · 51人", peopleSuffix(51))
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
