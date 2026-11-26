package io.github.samolego.canta.ui.fl

// Ported from FlClash (chen08209/FlClash) — lib/ui/icons/app_glyphs.dart and
// prism_ui/lib/icons/glyphs.dart. Coordinates live on a 24x24 grid, traced along the
// centre of the stroke, exactly as the originals do.
object FlGlyphs {

    val dashboard = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(3.5f, 3.5f, 10.5f, 11.5f, 2f),
            GlyphBox(13.5f, 3.5f, 20.5f, 9.5f, 2f),
            GlyphBox(3.5f, 14.5f, 10.5f, 20.5f, 2f),
            GlyphBox(13.5f, 12.5f, 20.5f, 20.5f, 2f),
        ),
    )

    val proxies = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(12f, 12f, 8.8f),
            GlyphOval(8.2f, 3.2f, 15.8f, 20.8f, role = GlyphRole.Detail),
            GlyphPolyline(listOf(GlyphVertex(3.2f, 12f, 0f), GlyphVertex(20.8f, 12f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val profiles = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(5f, 3f, 2.5f), GlyphVertex(13.5f, 3f, 1f), GlyphVertex(19f, 8.5f, 1f), GlyphVertex(19f, 21f, 2.5f), GlyphVertex(5f, 21f, 2.5f)), closed = true),
            GlyphPolyline(listOf(GlyphVertex(13.5f, 3.2f, 0f), GlyphVertex(13.5f, 8.5f, 1.5f), GlyphVertex(18.8f, 8.5f, 0f)), role = GlyphRole.Detail),
            GlyphPolyline(listOf(GlyphVertex(8.5f, 12.5f, 0f), GlyphVertex(15.5f, 12.5f, 0f)), role = GlyphRole.Detail),
            GlyphPolyline(listOf(GlyphVertex(8.5f, 16f, 0f), GlyphVertex(13f, 16f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val requests = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(12f, 12f, 8.8f),
            GlyphPolyline(listOf(GlyphVertex(12f, 7.2f, 0f), GlyphVertex(12f, 12f, 0.6f), GlyphVertex(15.2f, 14f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val dns = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(3.5f, 3.5f, 20.5f, 10.5f, 2.5f),
            GlyphBox(3.5f, 13.5f, 20.5f, 20.5f, 2.5f),
            GlyphCircle(7.2f, 7f, 1.1f, role = GlyphRole.Detail, solid = true),
            GlyphCircle(7.2f, 17f, 1.1f, role = GlyphRole.Detail, solid = true),
        ),
    )

    val resources = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(3f, 3.5f, 21f, 8.5f, 1.8f),
            GlyphBox(4.5f, 10.8f, 19.5f, 20.5f, 2.2f),
            GlyphPolyline(listOf(GlyphVertex(10f, 14.2f, 0f), GlyphVertex(14f, 14.2f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val logs = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(3f, 4.5f, 21f, 19.5f, 3.5f),
            GlyphPolyline(listOf(GlyphVertex(7.3f, 9.3f, 0f), GlyphVertex(9.9f, 11.9f, 0.4f), GlyphVertex(7.3f, 14.5f, 0f)), role = GlyphRole.Detail),
            GlyphPolyline(listOf(GlyphVertex(12.2f, 14.5f, 0f), GlyphVertex(16.2f, 14.5f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val tools = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(3f, 7f, 21f, 20f, 2.8f),
            GlyphPolyline(listOf(GlyphVertex(9f, 7f, 0f), GlyphVertex(9f, 4.6f, 1.4f), GlyphVertex(15f, 4.6f, 1.4f), GlyphVertex(15f, 7f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(3.2f, 12.6f, 0f), GlyphVertex(9.6f, 12.6f, 0f)), role = GlyphRole.Detail),
            GlyphPolyline(listOf(GlyphVertex(14.4f, 12.6f, 0f), GlyphVertex(20.8f, 12.6f, 0f)), role = GlyphRole.Detail),
            GlyphBox(10.4f, 11f, 13.6f, 14.4f, 1f, role = GlyphRole.Detail),
        ),
    )

    val target = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphArc(12f, 12f, 8.8f, -167.4f, 154.8f, role = GlyphRole.Line),
            GlyphArc(12f, 12f, 8.8f, 12.6f, 154.8f, role = GlyphRole.Line),
            GlyphCircle(12f, 12f, 2f),
        ),
    )

    val connections = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(12f, 5.2f, 2.4f),
            GlyphCircle(5.2f, 17.6f, 2.4f),
            GlyphCircle(18.8f, 17.6f, 2.4f),
            GlyphLink(12f, 5.2f, 5.2f, 17.6f, 2.4f),
            GlyphLink(12f, 5.2f, 18.8f, 17.6f, 2.4f),
            GlyphLink(5.2f, 17.6f, 18.8f, 17.6f, 2.4f),
        ),
    )

    val settings = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(9.87f, 5.44f, 0.6f), GlyphVertex(10.09f, 3f, 0.9f), GlyphVertex(13.91f, 3f, 0.9f), GlyphVertex(14.13f, 5.44f, 0.6f), GlyphVertex(16.62f, 6.87f, 0.6f), GlyphVertex(18.84f, 5.84f, 0.9f), GlyphVertex(20.75f, 9.16f, 0.9f), GlyphVertex(18.75f, 10.57f, 0.6f), GlyphVertex(18.75f, 13.43f, 0.6f), GlyphVertex(20.75f, 14.84f, 0.9f), GlyphVertex(18.84f, 18.16f, 0.9f), GlyphVertex(16.62f, 17.13f, 0.6f), GlyphVertex(14.13f, 18.56f, 0.6f), GlyphVertex(13.91f, 21f, 0.9f), GlyphVertex(10.09f, 21f, 0.9f), GlyphVertex(9.87f, 18.56f, 0.6f), GlyphVertex(7.38f, 17.13f, 0.6f), GlyphVertex(5.16f, 18.16f, 0.9f), GlyphVertex(3.25f, 14.84f, 0.9f), GlyphVertex(5.25f, 13.43f, 0.6f), GlyphVertex(5.25f, 10.57f, 0.6f), GlyphVertex(3.25f, 9.16f, 0.9f), GlyphVertex(5.16f, 5.84f, 0.9f), GlyphVertex(7.38f, 6.87f, 0.6f)), closed = true),
            GlyphCircle(12f, 12f, 3f, role = GlyphRole.Detail),
        ),
    )

    val rules = FlGlyph(
        matchTextDirection = true,
        shapes = listOf(
            GlyphBox(3f, 4f, 21f, 20f, 3f),
            GlyphCircle(7f, 8f, 1.1f, role = GlyphRole.Detail, solid = true),
            GlyphPolyline(listOf(GlyphVertex(10.8f, 8f, 0f), GlyphVertex(17.2f, 8f, 0f)), role = GlyphRole.Detail),
            GlyphCircle(7f, 12f, 1.1f, role = GlyphRole.Detail, solid = true),
            GlyphPolyline(listOf(GlyphVertex(10.8f, 12f, 0f), GlyphVertex(17.2f, 12f, 0f)), role = GlyphRole.Detail),
            GlyphCircle(7f, 16f, 1.1f, role = GlyphRole.Detail, solid = true),
            GlyphPolyline(listOf(GlyphVertex(10.8f, 16f, 0f), GlyphVertex(17.2f, 16f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val rulePresets = FlGlyph(
        matchTextDirection = true,
        shapes = listOf(
            GlyphBox(3f, 9.5f, 21f, 21f, 3f),
            GlyphPolyline(listOf(GlyphVertex(5.5f, 6.2f, 0f), GlyphVertex(18.5f, 6.2f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(8f, 3f, 0f), GlyphVertex(16f, 3f, 0f)), role = GlyphRole.Line),
            GlyphCircle(7f, 13.4f, 1.1f, role = GlyphRole.Detail, solid = true),
            GlyphPolyline(listOf(GlyphVertex(10.8f, 13.4f, 0f), GlyphVertex(17.2f, 13.4f, 0f)), role = GlyphRole.Detail),
            GlyphCircle(7f, 17.1f, 1.1f, role = GlyphRole.Detail, solid = true),
            GlyphPolyline(listOf(GlyphVertex(10.8f, 17.1f, 0f), GlyphVertex(17.2f, 17.1f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val sliders = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(3.2f, 5.5f, 0f), GlyphVertex(10.4f, 5.5f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(18.6f, 5.5f, 0f), GlyphVertex(20.8f, 5.5f, 0f)), role = GlyphRole.Line),
            GlyphCircle(14.5f, 5.5f, 2.5f),
            GlyphPolyline(listOf(GlyphVertex(3.2f, 12f, 0f), GlyphVertex(4.4f, 12f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(12.6f, 12f, 0f), GlyphVertex(20.8f, 12f, 0f)), role = GlyphRole.Line),
            GlyphCircle(8.5f, 12f, 2.5f),
            GlyphPolyline(listOf(GlyphVertex(3.2f, 18.5f, 0f), GlyphVertex(11.4f, 18.5f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(19.6f, 18.5f, 0f), GlyphVertex(20.8f, 18.5f, 0f)), role = GlyphRole.Line),
            GlyphCircle(15.5f, 18.5f, 2.5f),
        ),
    )

    val wrench = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPathData("M3.58 17.59L10.1 11.07A1.2 1.2 0 0 0 10.41 9.89A5.4 5.4 0 0 1 16.36 3.05A0.9 0.9 0 0 1 16.87 4.58L14.04 7.41A1.8 1.8 0 0 0 16.59 9.96L19.42 7.13A0.9 0.9 0 0 1 20.95 7.64A5.4 5.4 0 0 1 14.11 13.59A1.2 1.2 0 0 0 12.93 13.9L6.41 20.42A2 2 0 0 1 3.58 17.59Z"),
        ),
    )

    val code = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(7.5f, 7.5f, 0f), GlyphVertex(3f, 12f, 0.5f), GlyphVertex(7.5f, 16.5f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(16.5f, 7.5f, 0f), GlyphVertex(21f, 12f, 0.5f), GlyphVertex(16.5f, 16.5f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(13.8f, 5f, 0f), GlyphVertex(10.2f, 19f, 0f)), role = GlyphRole.Line),
        ),
    )

    val toggle = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(2.9f, 6.5f, 21.1f, 17.5f, 5.5f),
            GlyphCircle(8.4f, 12f, 2.8f, role = GlyphRole.Detail, solid = true),
        ),
    )

    val toggleOn = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(2.9f, 6.5f, 21.1f, 17.5f, 5.5f),
            GlyphCircle(15.6f, 12f, 2.8f, role = GlyphRole.Detail, solid = true),
        ),
    )

    val arrowBack = FlGlyph(
        matchTextDirection = true,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(5f, 12f, 0f), GlyphVertex(19f, 12f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(11.5f, 5.5f, 0f), GlyphVertex(5f, 12f, 0.4f), GlyphVertex(11.5f, 18.5f, 0f)), role = GlyphRole.Line),
        ),
    )

    val chevronBack = FlGlyph(
        matchTextDirection = true,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(15.25f, 5.5f, 0f), GlyphVertex(8.75f, 12f, 0.4f), GlyphVertex(15.25f, 18.5f, 0f)), role = GlyphRole.Line),
        ),
    )

    val chevronForward = FlGlyph(
        matchTextDirection = true,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(8.75f, 5.5f, 0f), GlyphVertex(15.25f, 12f, 0.4f), GlyphVertex(8.75f, 18.5f, 0f)), role = GlyphRole.Line),
        ),
    )

    val chevronDown = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(5.5f, 8.75f, 0f), GlyphVertex(12f, 15.25f, 0.4f), GlyphVertex(18.5f, 8.75f, 0f)), role = GlyphRole.Line),
        ),
    )

    val close = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(6f, 6f, 0f), GlyphVertex(18f, 18f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(18f, 6f, 0f), GlyphVertex(6f, 18f, 0f)), role = GlyphRole.Line),
        ),
    )

    val clearField = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(12f, 12f, 8.8f),
            GlyphPolyline(listOf(GlyphVertex(9.3f, 9.3f, 0f), GlyphVertex(14.7f, 14.7f, 0f)), role = GlyphRole.Detail),
            GlyphPolyline(listOf(GlyphVertex(14.7f, 9.3f, 0f), GlyphVertex(9.3f, 14.7f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val check = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(4.8f, 12.5f, 0f), GlyphVertex(9.6f, 17.3f, 0f), GlyphVertex(19.4f, 6.7f, 0f)), role = GlyphRole.Line),
        ),
    )

    val checkCircle = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(12f, 12f, 8.8f),
            GlyphPolyline(listOf(GlyphVertex(7.59f, 12.3f, 0f), GlyphVertex(10.49f, 15.2f, 0f), GlyphVertex(16.41f, 8.79f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val more = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(12f, 4.2f, 1.65f, role = GlyphRole.Line, solid = true),
            GlyphCircle(12f, 12f, 1.65f, role = GlyphRole.Line, solid = true),
            GlyphCircle(12f, 19.8f, 1.65f, role = GlyphRole.Line, solid = true),
        ),
    )

    val moreCircle = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(12f, 12f, 8.8f),
            GlyphCircle(7.4f, 12f, 1.4f, role = GlyphRole.Detail, solid = true),
            GlyphCircle(12f, 12f, 1.4f, role = GlyphRole.Detail, solid = true),
            GlyphCircle(16.6f, 12f, 1.4f, role = GlyphRole.Detail, solid = true),
        ),
    )

    val dragHandle = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(4f, 7f, 0f), GlyphVertex(20f, 7f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(4f, 12f, 0f), GlyphVertex(20f, 12f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(4f, 17f, 0f), GlyphVertex(20f, 17f, 0f)), role = GlyphRole.Line),
        ),
    )

    val sort = FlGlyph(
        matchTextDirection = true,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(4f, 7f, 0f), GlyphVertex(20f, 7f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(4f, 12f, 0f), GlyphVertex(15f, 12f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(4f, 17f, 0f), GlyphVertex(10f, 17f, 0f)), role = GlyphRole.Line),
        ),
    )

    val filter = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(4f, 7f, 0f), GlyphVertex(20f, 7f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(7f, 12f, 0f), GlyphVertex(17f, 12f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(10f, 17f, 0f), GlyphVertex(14f, 17f, 0f)), role = GlyphRole.Line),
        ),
    )

    val search = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(10.3f, 10.3f, 6.9f, role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(15.18f, 15.18f, 0f), GlyphVertex(20.6f, 20.6f, 0f)), role = GlyphRole.Line),
        ),
    )

    val refresh = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphArc(12f, 12f, 7.78f, 34.2f, 299.88f, role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(19f, 5f, 0f), GlyphVertex(19f, 8.6f, 0.4f), GlyphVertex(15.4f, 8.6f, 0f)), role = GlyphRole.Line),
        ),
    )

    val reset = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphArc(12f, 13.77f, 7.07f, 207f, -297f, role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(12f, 6.7f, 0f), GlyphVertex(9.9f, 6.7f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(13.7f, 10.5f, 0f), GlyphVertex(9.9f, 6.7f, 0.4f), GlyphVertex(13.7f, 2.9f, 0f)), role = GlyphRole.Line),
        ),
    )

    val add = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(12f, 5f, 0f), GlyphVertex(12f, 19f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(5f, 12f, 0f), GlyphVertex(19f, 12f, 0f)), role = GlyphRole.Line),
        ),
    )

    val remove = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(5f, 12f, 0f), GlyphVertex(19f, 12f, 0f)), role = GlyphRole.Line),
        ),
    )

    val addCircle = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(12f, 12f, 8.8f, role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(12f, 7.8f, 0f), GlyphVertex(12f, 16.2f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(7.8f, 12f, 0f), GlyphVertex(16.2f, 12f, 0f)), role = GlyphRole.Line),
        ),
    )

    val removeCircle = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(12f, 12f, 8.8f, role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(7.8f, 12f, 0f), GlyphVertex(16.2f, 12f, 0f)), role = GlyphRole.Line),
        ),
    )

    val delete = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(5.8f, 6.8f, 0f), GlyphVertex(18.2f, 6.8f, 0f), GlyphVertex(17.2f, 21f, 2.5f), GlyphVertex(6.8f, 21f, 2.5f)), closed = true),
            GlyphPolyline(listOf(GlyphVertex(10f, 10.5f, 0f), GlyphVertex(10f, 17.5f, 0f)), role = GlyphRole.Detail),
            GlyphPolyline(listOf(GlyphVertex(14f, 10.5f, 0f), GlyphVertex(14f, 17.5f, 0f)), role = GlyphRole.Detail),
            GlyphPolyline(listOf(GlyphVertex(3.8f, 6.8f, 0f), GlyphVertex(20.2f, 6.8f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(9f, 6.8f, 0f), GlyphVertex(9f, 3.2f, 1.4f), GlyphVertex(15f, 3.2f, 1.4f), GlyphVertex(15f, 6.8f, 0f)), role = GlyphRole.Line),
        ),
    )

    val edit = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(4f, 20f, 1f), GlyphVertex(8.75f, 18.75f, 1f), GlyphVertex(20.25f, 7.25f, 1.5f), GlyphVertex(16.75f, 3.75f, 1.5f), GlyphVertex(5.25f, 15.25f, 1f)), closed = true),
            GlyphPolyline(listOf(GlyphVertex(13.25f, 7.25f, 0f), GlyphVertex(16.75f, 10.75f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val info = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(12f, 12f, 8.8f),
            GlyphCircle(12f, 7.5f, 1.4f, role = GlyphRole.Detail, solid = true),
            GlyphPolyline(listOf(GlyphVertex(12f, 11.8f, 0f), GlyphVertex(12f, 16.7f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val warning = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(12f, 1.6f, 3f), GlyphVertex(21.9f, 20.3f, 2.5f), GlyphVertex(2.1f, 20.3f, 2.5f)), closed = true),
            GlyphPolyline(listOf(GlyphVertex(12f, 9f, 0f), GlyphVertex(12f, 12.1f, 0f)), role = GlyphRole.Detail),
            GlyphCircle(12f, 16.2f, 1.4f, role = GlyphRole.Detail, solid = true),
        ),
    )

    val error = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(12f, 12f, 8.8f),
            GlyphPolyline(listOf(GlyphVertex(12f, 7.3f, 0f), GlyphVertex(12f, 12.2f, 0f)), role = GlyphRole.Detail),
            GlyphCircle(12f, 16.5f, 1.4f, role = GlyphRole.Detail, solid = true),
        ),
    )

    val block = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(12f, 12f, 8.8f, role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(5.78f, 5.78f, 0f), GlyphVertex(18.22f, 18.22f, 0f)), role = GlyphRole.Line),
        ),
    )

    val lock = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(5f, 10.5f, 19f, 20.8f, 2.5f),
            GlyphPathData("M8.5 10.5V7A3.5 3.5 0 0 1 15.5 7V10.5", role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(12f, 14.4f, 0f), GlyphVertex(12f, 17f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val lockOpen = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(5f, 10.5f, 19f, 20.8f, 2.5f),
            GlyphPathData("M8.5 10.5V7A3.5 3.5 0 0 1 15.5 7", role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(12f, 14.4f, 0f), GlyphVertex(12f, 17f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val key = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(9.27f, 7.99f, 5f),
            GlyphCircle(9.27f, 7.99f, 1.4f, role = GlyphRole.Detail, solid = true),
            GlyphPolyline(listOf(GlyphVertex(12.8f, 11.53f, 0f), GlyphVertex(19.73f, 18.46f, 0.8f), GlyphVertex(17.19f, 21.01f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(17.19f, 15.91f, 0f), GlyphVertex(15.07f, 18.04f, 0f)), role = GlyphRole.Line),
        ),
    )

    val pin = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(15.42f, 3.5f, 1.5f), GlyphVertex(20.5f, 8.58f, 1.5f), GlyphVertex(15.73f, 13.35f, 1.4f), GlyphVertex(14.94f, 19.55f, 1f), GlyphVertex(4.45f, 9.06f, 1f), GlyphVertex(10.65f, 8.27f, 1.4f)), closed = true),
            GlyphPolyline(listOf(GlyphVertex(9.7f, 14.3f, 0f), GlyphVertex(3.5f, 20.5f, 0f)), role = GlyphRole.Line),
        ),
    )

    val eye = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPathData("M3.01 11.32A10.08 10.08 0 0 1 20.99 11.32A1.5 1.5 0 0 1 20.99 12.68A10.08 10.08 0 0 1 3.01 12.68A1.5 1.5 0 0 1 3.01 11.32Z"),
            GlyphCircle(12f, 12f, 2.8f, role = GlyphRole.Detail),
        ),
    )

    val eyeOff = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPathData("M10.95 5.86A10.08 10.08 0 0 1 20.99 11.32A1.5 1.5 0 0 1 20.99 12.68A10.08 10.08 0 0 1 19.71 14.62", role = GlyphRole.Line),
            GlyphPathData("M16.92 16.92A10.08 10.08 0 0 1 3.01 12.68A1.5 1.5 0 0 1 3.01 11.32A10.08 10.08 0 0 1 7.08 7.08", role = GlyphRole.Line),
            GlyphArc(12f, 12f, 2.8f, 45f, 180f, role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(4f, 4f, 0f), GlyphVertex(20f, 20f, 0f)), role = GlyphRole.Line),
        ),
    )

    val copy = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(8.5f, 7.5f, 20.5f, 21f, 2.5f),
            GlyphPolyline(listOf(GlyphVertex(4f, 16.5f, 0f), GlyphVertex(4f, 3f, 2.5f), GlyphVertex(16f, 3f, 0f)), role = GlyphRole.Line),
        ),
    )

    val openExternal = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(3f, 3f, 21f, 21f, 3f),
            GlyphPolyline(listOf(GlyphVertex(7.6f, 16.4f, 0f), GlyphVertex(16.4f, 7.6f, 0f)), role = GlyphRole.Detail),
            GlyphPolyline(listOf(GlyphVertex(11f, 7.6f, 0f), GlyphVertex(16.4f, 7.6f, 0.4f), GlyphVertex(16.4f, 13f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val importFile = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(5f, 3f, 2.5f), GlyphVertex(13.5f, 3f, 1f), GlyphVertex(19f, 8.5f, 1f), GlyphVertex(19f, 21f, 2.5f), GlyphVertex(5f, 21f, 2.5f)), closed = true),
            GlyphPolyline(listOf(GlyphVertex(13.5f, 3.2f, 0f), GlyphVertex(13.5f, 8.5f, 1.5f), GlyphVertex(18.8f, 8.5f, 0f)), role = GlyphRole.Detail),
            GlyphPolyline(listOf(GlyphVertex(12f, 17.6f, 0f), GlyphVertex(12f, 11.4f, 0f)), role = GlyphRole.Detail),
            GlyphPolyline(listOf(GlyphVertex(9f, 14.4f, 0f), GlyphVertex(12f, 11.4f, 0.4f), GlyphVertex(15f, 14.4f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val cloudDownload = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPathData("M7 19.4A4.2 4.2 0 0 1 5.06 11.47A6 6 0 1 1 16.95 9.83A4.8 4.8 0 0 1 16.4 19.4Z"),
            GlyphPolyline(listOf(GlyphVertex(12f, 8.4f, 0f), GlyphVertex(12f, 16f, 0f)), role = GlyphRole.Detail),
            GlyphPolyline(listOf(GlyphVertex(9f, 13f, 0f), GlyphVertex(12f, 16f, 0.4f), GlyphVertex(15f, 13f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val link = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPathData("M11.01 8.46L14.55 4.93A3.2 3.2 0 0 1 19.07 9.45L15.54 12.99", role = GlyphRole.Line),
            GlyphPathData("M12.99 15.54L9.45 19.07A3.2 3.2 0 0 1 4.93 14.55L8.46 11.01", role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(9.17f, 14.83f, 0f), GlyphVertex(14.83f, 9.17f, 0f)), role = GlyphRole.Line),
        ),
    )

    /** 四宫格：聊天输入栏最左边那个入口（「更多」面板）。 */
    val grid2x2 = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(3.6f, 3.6f, 10.4f, 10.4f, 2f),
            GlyphBox(13.6f, 3.6f, 20.4f, 10.4f, 2f),
            GlyphBox(3.6f, 13.6f, 10.4f, 20.4f, 2f),
            GlyphBox(13.6f, 13.6f, 20.4f, 20.4f, 2f),
        ),
    )

    /** 语音：五根高低交替的竖线，中间那根最长。手机聊天栏左侧那个「按住说话」的图标。 */
    val voiceWave = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(3.6f, 10.6f, 0f), GlyphVertex(3.6f, 13.4f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(7.8f, 7.4f, 0f), GlyphVertex(7.8f, 16.6f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(12f, 4.4f, 0f), GlyphVertex(12f, 19.6f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(16.2f, 7.4f, 0f), GlyphVertex(16.2f, 16.6f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(20.4f, 10.6f, 0f), GlyphVertex(20.4f, 13.4f, 0f)), role = GlyphRole.Line),
        ),
    )

    /** 表情：一个圆脸、两只实心眼、一道上扬的嘴。输入栏上那个笑脸。 */
    val smile = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(x = 12f, y = 12f, radius = 8.6f),
            GlyphCircle(x = 8.9f, y = 9.7f, radius = 1.05f, role = GlyphRole.Detail, solid = true),
            GlyphCircle(x = 15.1f, y = 9.7f, radius = 1.05f, role = GlyphRole.Detail, solid = true),
            GlyphPolyline(
                listOf(
                    GlyphVertex(8.3f, 14.1f, 0f),
                    GlyphVertex(10.1f, 16.3f, 0.9f),
                    GlyphVertex(12f, 16.9f, 0.7f),
                    GlyphVertex(13.9f, 16.3f, 0.9f),
                    GlyphVertex(15.7f, 14.1f, 0f),
                ),
                role = GlyphRole.Detail,
            ),
        ),
    )

    val send = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(3.2f, 10.8f, 1f), GlyphVertex(20.6f, 3.4f, 1f), GlyphVertex(13.2f, 20.8f, 1f), GlyphVertex(10.6f, 13.4f, 0.6f)), closed = true),
            GlyphPolyline(listOf(GlyphVertex(10.6f, 13.4f, 0f), GlyphVertex(15.2f, 8.8f, 0f)), role = GlyphRole.Detail),
        ),
    )

    val appsList = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(3.5f, 3.3f, 6.9f, 6.7f, 1f),
            GlyphBox(3.5f, 10.3f, 6.9f, 13.7f, 1f),
            GlyphBox(3.5f, 17.3f, 6.9f, 20.7f, 1f),
            GlyphPolyline(listOf(GlyphVertex(10.5f, 5f, 0f), GlyphVertex(20.5f, 5f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(10.5f, 12f, 0f), GlyphVertex(20.5f, 12f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(10.5f, 19f, 0f), GlyphVertex(20.5f, 19f, 0f)), role = GlyphRole.Line),
        ),
    )

    /**
     * 三条等长横线：「分类侧栏」那个 ☰。
     *
     * 和 [appsList] 不是同一个符号：那个左右两半不一样（左边三块、右边三根线），看着像
     * "应用列表 / 网格"；`Symbols.menu_rounded` 才是三条一样长的横线，也就是"把这边的东西
     * 列出来"的那个 ☰。
     */
    val menu = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(listOf(GlyphVertex(4f, 7f, 0f), GlyphVertex(20f, 7f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(4f, 12f, 0f), GlyphVertex(20f, 12f, 0f)), role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(4f, 17f, 0f), GlyphVertex(20f, 17f, 0f)), role = GlyphRole.Line),
        ),
    )

    val iconTile = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(3f, 3f, 21f, 21f, 5f),
            GlyphPolyline(listOf(GlyphVertex(5.6f, 16.8f, 0f), GlyphVertex(9.6f, 12.8f, 0.8f), GlyphVertex(12.8f, 16f, 0.8f), GlyphVertex(14.6f, 14.2f, 0.8f), GlyphVertex(18.4f, 18f, 0f)), role = GlyphRole.Detail),
            GlyphCircle(15.2f, 8.8f, 1.5f, role = GlyphRole.Detail, solid = true),
        ),
    )
}
