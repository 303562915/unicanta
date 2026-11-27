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
            // 外框必须是 `Line`（见文件里其它 glyph 的同类注释）：`FlGlyphIcon` 默认 fill=0f，
            // 而 `drawBody` 在 fill<=0f 时直接 return —— 留默认 Body 的话信封轮廓不画，
            // 只剩里面两道横线，「还没有好友」那块会像两根悬空短线。
            GlyphPolyline(listOf(GlyphVertex(5f, 3f, 2.5f), GlyphVertex(13.5f, 3f, 1f), GlyphVertex(19f, 8.5f, 1f), GlyphVertex(19f, 21f, 2.5f), GlyphVertex(5f, 21f, 2.5f)), closed = true, role = GlyphRole.Line),
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

    // ---- 编辑器工具组（「创建话题」底部工具栏）----
    //
    // FlClash 原版没有这几个：它是代理客户端，不带写作工具。这里按同一套语言补——
    // 24×24 网格、中心线、`GlyphRole.Line` 给主笔画，视觉内框落在 4–20 之间，
    // 和 [code]（3..21）、[add]（5..19）是同一档，16dp 缩放下笔画不至于糊在一起。
    //
    // 只用折线和圆拼，不用 [GlyphPathData] 的曲线：这几个形状在 16dp 下笔画只有 ~1.3px，
    // 曲线细节会丢掉，直线段反而更清楚。

    /** 粗体：一笔 B —— 竖干 + 上下两个瓣（上瓣窄、下瓣宽）。 */
    val bold = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(
                listOf(GlyphVertex(8f, 4.8f, 0f), GlyphVertex(8f, 19.2f, 0f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(
                    GlyphVertex(8f, 4.8f, 0f),
                    GlyphVertex(13.4f, 4.8f, 1.8f),
                    GlyphVertex(15.2f, 6.8f, 1.6f),
                    GlyphVertex(13.4f, 9f, 1.8f),
                    GlyphVertex(8f, 9f, 0f),
                ),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(
                    GlyphVertex(8f, 9f, 0f),
                    GlyphVertex(14.2f, 9f, 1.8f),
                    GlyphVertex(16.2f, 12.2f, 1.8f),
                    GlyphVertex(14.2f, 15.2f, 1.8f),
                    GlyphVertex(8f, 15.2f, 0f),
                ),
                role = GlyphRole.Line,
            ),
        ),
    )

    /** 斜体：一条右上斜的干 + 上下两道平头。 */
    val italic = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(
                listOf(GlyphVertex(15.4f, 5.2f, 0.6f), GlyphVertex(10.6f, 18.8f, 0.6f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(9f, 5.2f, 0f), GlyphVertex(15.4f, 5.2f, 0f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(8.6f, 18.8f, 0f), GlyphVertex(15f, 18.8f, 0f)),
                role = GlyphRole.Line,
            ),
        ),
    )

    /**
     * 标题（H1）：极简的 H（两竖 + 一横梁）+ 右侧一个小 1。
     *
     * 不画字母 'H' 的完整轮廓：16dp 下横梁只剩一点几个像素，画成完整字母会糊成方框。
     */
    val heading = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(
                listOf(GlyphVertex(6f, 5.2f, 0f), GlyphVertex(6f, 18.8f, 0f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(12.4f, 5.2f, 0f), GlyphVertex(12.4f, 18.8f, 0f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(6f, 12f, 0f), GlyphVertex(12.4f, 12f, 0f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(
                    GlyphVertex(17.6f, 18.4f, 0f),
                    GlyphVertex(17.6f, 6.8f, 1.4f),
                    GlyphVertex(15.8f, 8.8f, 0f),
                ),
                role = GlyphRole.Line,
            ),
        ),
    )

    /** 无序列表：左侧三颗实心点 + 右侧三根短线。点的读法是"列表符号"。 */
    val bulletList = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphCircle(5.4f, 7f, 1.15f, role = GlyphRole.Detail, solid = true),
            GlyphCircle(5.4f, 12f, 1.15f, role = GlyphRole.Detail, solid = true),
            GlyphCircle(5.4f, 17f, 1.15f, role = GlyphRole.Detail, solid = true),
            GlyphPolyline(
                listOf(GlyphVertex(9.4f, 7f, 0f), GlyphVertex(19.4f, 7f, 0f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(9.4f, 12f, 0f), GlyphVertex(19.4f, 12f, 0f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(9.4f, 17f, 0f), GlyphVertex(19.4f, 17f, 0f)),
                role = GlyphRole.Line,
            ),
        ),
    )

    /** 引用：左边一根竖条（两端圆角）+ 右边两道横杠。Markdown 的 `>` 就是这两件事。 */
    val quote = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(
                listOf(GlyphVertex(4.6f, 5.6f, 1.2f), GlyphVertex(4.6f, 18.4f, 1.2f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(8.6f, 8.4f, 0f), GlyphVertex(19.4f, 8.4f, 0f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(8.6f, 15.6f, 0f), GlyphVertex(19.4f, 15.6f, 0f)),
                role = GlyphRole.Line,
            ),
        ),
    )

    /**
     * 删除线：一个被两道短杠「划断」的 S 形折线。
     *
     * 读法是"这行字被划掉了"：主体是一条 6.4 到 17.6 的 S（上端往右、下端也往右，中间一道
     * 反向的腰），两道水平短杠从 4.2 划到 19.8，比 S 还宽出一点——线要"越过"字才像划掉，
     * 卡在字里像装饰。两杠在 y 10.4 与 y 13.6，正好夹住那道腰（y=12），
     * 所以 16dp 下即使 S 糊了，人读到的仍是"被两道横线划过的字"。
     */
    val strikethrough = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(
                listOf(
                    GlyphVertex(17.4f, 6.4f, 1.6f),
                    GlyphVertex(9.6f, 6.4f, 2.2f),
                    GlyphVertex(6.6f, 12f, 2.2f),
                    GlyphVertex(9.6f, 17.6f, 2.2f),
                    GlyphVertex(17.4f, 17.6f, 1.6f),
                ),
                role = GlyphRole.Line,
            ),
            // 两道横杠比字形略宽（4.2 到 19.8）：线要"越过"字才像划掉，卡在字里像装饰。
            GlyphPolyline(
                listOf(GlyphVertex(4.2f, 10.4f, 0f), GlyphVertex(19.8f, 10.4f, 0f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(4.2f, 13.6f, 0f), GlyphVertex(19.8f, 13.6f, 0f)),
                role = GlyphRole.Line,
            ),
        ),
    )

    /**
     * 有序列表：左侧三个序号（1、2、3，画成「竖干 + 右下小勾」的极简数字）+ 右侧三根短线。
     *
     * 和无序列表 [bulletList] 是同一套栅格：点/序号在 x≈5.4，文字在 x 9.4 到 19.4，
     * 三行 y 7、12、17，这样两颗图标并排放在面板里不会被看成两种不同的列表。
     * 序号只画到 16dp 下还看得见的程度（3 格宽、4 格高），再多细节就糊成方块了。
     */
    val orderedList = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            // 1：一根竖干，左上带一小段起笔。
            GlyphPolyline(
                listOf(GlyphVertex(6.4f, 5.2f, 0.6f), GlyphVertex(5.2f, 6.2f, 0f)),
                role = GlyphRole.Detail,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(6.4f, 5.2f, 0f), GlyphVertex(6.4f, 9f, 0f)),
                role = GlyphRole.Detail,
            ),
            // 2：上弧 + 斜下 + 底横。
            GlyphPolyline(
                listOf(
                    GlyphVertex(4.6f, 11.2f, 0.6f),
                    GlyphVertex(5.8f, 10.2f, 0.9f),
                    GlyphVertex(7.2f, 11.2f, 0.9f),
                    GlyphVertex(4.6f, 14.4f, 0.4f),
                ),
                role = GlyphRole.Detail,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(4.6f, 14.4f, 0f), GlyphVertex(7.6f, 14.4f, 0f)),
                role = GlyphRole.Detail,
            ),
            // 3：两道上弧叠成 "3"。不用两道独立笔画是因为 16dp 下中间那道缝会消失，
            // 连成一条 S 形折线反而更稳。
            GlyphPolyline(
                listOf(
                    GlyphVertex(4.8f, 15.8f, 0.6f),
                    GlyphVertex(6.2f, 16.8f, 0.9f),
                    GlyphVertex(7.4f, 17.8f, 0.9f),
                    GlyphVertex(6.2f, 19f, 0.9f),
                    GlyphVertex(4.8f, 19.2f, 0.4f),
                ),
                role = GlyphRole.Detail,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(9.4f, 7f, 0f), GlyphVertex(19.4f, 7f, 0f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(9.4f, 12f, 0f), GlyphVertex(19.4f, 12f, 0f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(9.4f, 17f, 0f), GlyphVertex(19.4f, 17f, 0f)),
                role = GlyphRole.Line,
            ),
        ),
    )

    /**
     * 代码块：一个方框（四个围栏）里两条短横线（里面的代码行），和 [code] 的尖括号区分开。
     *
     * 外框**必须显式写 `role = GlyphRole.Line`**：`GlyphBox` 默认是 `Body`，而
     * `FlGlyphIcon` 默认 `fill = 0f`，`drawBody` 在 `fill <= 0f` 时直接 return ——
     * 漏了这个参数，画出来只剩框里那两条线（和 [drafts] 踩过的是同一个坑）。
     * 框 3.2 到 20.8 宽、4.4 到 19.6 高、圆角 2.4，比 [code] 的 3..21 略窄一点，靠"是个闭合框"来区分。
     */
    val codeBlock = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(3.2f, 4.4f, 20.8f, 19.6f, 2.4f, role = GlyphRole.Line),
            GlyphPolyline(
                listOf(GlyphVertex(6.8f, 9.6f, 0f), GlyphVertex(17.2f, 9.6f, 0f)),
                role = GlyphRole.Detail,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(6.8f, 14.2f, 0f), GlyphVertex(13.4f, 14.2f, 0f)),
                role = GlyphRole.Detail,
            ),
        ),
    )

    /**
     * 分隔线：一道通长横线 + 右上角一颗小方块 —— "这一行只是一条线，不是字"。
     *
     * 横线画在 y=12（网格正中），两端各留 3.6 的边距（3.6 到 20.4），与 `GlyphBox(3.2…20.8)`
     * 是同一档内框。只用一条横线会和 `- ` 那个连字符撞脸，所以右端压一颗
     * 15.4 到 20.4 宽、9.4 到 14.6 高 的小方块（`Detail`），让右半边"有个块"，一眼不像减号。
     */
    val horizontalRule = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(
                listOf(GlyphVertex(3.6f, 12f, 0f), GlyphVertex(20.4f, 12f, 0f)),
                role = GlyphRole.Line,
            ),
            GlyphBox(15.4f, 9.4f, 20.4f, 14.6f, 1.2f, role = GlyphRole.Detail),
        ),
    )

    /**
     * 表格骨架：外框 + 一横一竖两道内线（2×2 四格）。
     *
     * 只切 2×2：再细分就成了 3×3 的蜘蛛网，16dp 缩放下内线会糊成一块灰。
     * 内线端点与外框边线重合（3.2 到 20.8 宽、4.4 到 19.6 高），靠描边的圆头把接头补平。
     * 外框同样**必须显式 `role = GlyphRole.Line`**（见 [codeBlock] 那段）。
     */
    val table = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(3.2f, 4.4f, 20.8f, 19.6f, 2f, role = GlyphRole.Line),
            GlyphPolyline(
                listOf(GlyphVertex(3.2f, 12f, 0f), GlyphVertex(20.8f, 12f, 0f)),
                role = GlyphRole.Detail,
            ),
            GlyphPolyline(
                listOf(GlyphVertex(12f, 4.4f, 0f), GlyphVertex(12f, 19.6f, 0f)),
                role = GlyphRole.Detail,
            ),
        ),
    )

    /**
     * 草稿：一个信封轮廓 + 中间一道 V。
     *
     * FlClash 原版没有信封（它不发消息），而底栏「消息」用的是 [send]；
     * 草稿这一格要的是"还没发出去的东西"，所以画的是信封而不是纸飞机。
     */
    val drafts = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            // 外框必须是 `GlyphRole.Line`，不能留默认的 `Body`：`drawBody` 在 `fill <= 0f` 时
            // 直接 return（见 Glyph.kt），而 `FlGlyphIcon` 默认就是 `fill = 0f` —— 留 Body 的话
            // 这个图标画出来只剩中间那道 V，像个孤零零的 "^"。
            GlyphBox(3.2f, 5.2f, 20.8f, 18.8f, 2.2f, role = GlyphRole.Line),
            GlyphPolyline(
                listOf(
                    GlyphVertex(4f, 6.6f, 0.4f),
                    GlyphVertex(12f, 12.8f, 0.4f),
                    GlyphVertex(20f, 6.6f, 0.4f),
                ),
                role = GlyphRole.Detail,
            ),
        ),
    )

    /** 向上：与 [arrowBack] 同构，转 90°。「查看 N 个新话题」横幅用。 */
    val arrowUpward = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphPolyline(
                listOf(GlyphVertex(12f, 19f, 0f), GlyphVertex(12f, 5f, 0f)),
                role = GlyphRole.Line,
            ),
            GlyphPolyline(
                listOf(
                    GlyphVertex(5.5f, 11.5f, 0f),
                    GlyphVertex(12f, 5f, 0.4f),
                    GlyphVertex(18.5f, 11.5f, 0f),
                ),
                role = GlyphRole.Line,
            ),
        ),
    )

    /**
     * 五角星：区块标题前面那颗「这是挑出来的内容」的记号。
     *
     * FlClash 原版没有星（它的区块头用竖条），这里补一颗：外接圆半径 8.4、五个尖角，
     * 顶点从正上方（-90°）起，用 24 网格的中心点 (12, 12)。折线闭合、`GlyphRole.Line`。
     */
    val star = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            // 五个外尖与五个内凹交替：外半径 8.4、内半径 3.6（金色分割比附近的取整）。
            GlyphPolyline(
                vertices = listOf(
                    GlyphVertex(12f, 3.6f, 0.5f),
                    GlyphVertex(14.1f, 9.2f, 0.4f),
                    GlyphVertex(20.1f, 9.4f, 0.4f),
                    GlyphVertex(15.3f, 13.0f, 0.4f),
                    GlyphVertex(17.1f, 18.8f, 0.4f),
                    GlyphVertex(12f, 15.4f, 0.4f),
                    GlyphVertex(6.9f, 18.8f, 0.4f),
                    GlyphVertex(8.7f, 13.0f, 0.4f),
                    GlyphVertex(3.9f, 9.4f, 0.4f),
                    GlyphVertex(9.9f, 9.2f, 0.4f),
                ),
                closed = true,
                role = GlyphRole.Line,
            ),
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

    /**
     * 四宫格：聊天输入栏最左边那个入口（「更多」面板），也是编辑器工具栏的「更多工具」。
     *
     * 四个方块都是 `Line` 而不是默认的 `Body`：这个 glyph 的**全部**笔画就是这四个框，
     * 留 Body 的话 `fill = 0f` 下一个都画不出来，整颗图标是空的。
     */
    val grid2x2 = FlGlyph(
        matchTextDirection = false,
        shapes = listOf(
            GlyphBox(3.6f, 3.6f, 10.4f, 10.4f, 2f, role = GlyphRole.Line),
            GlyphBox(13.6f, 3.6f, 20.4f, 10.4f, 2f, role = GlyphRole.Line),
            GlyphBox(3.6f, 13.6f, 10.4f, 20.4f, 2f, role = GlyphRole.Line),
            GlyphBox(13.6f, 13.6f, 20.4f, 20.4f, 2f, role = GlyphRole.Line),
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
            // 三个小方块必须是 `Line`：这颗图标只靠它们和右侧三条线撑形状，
            // 留默认 `Body` 在 fill=0f 下三个方块全不画，只剩右边三根线（左侧一片空）。
            GlyphBox(3.5f, 3.3f, 6.9f, 6.7f, 1f, role = GlyphRole.Line),
            GlyphBox(3.5f, 10.3f, 6.9f, 13.7f, 1f, role = GlyphRole.Line),
            GlyphBox(3.5f, 17.3f, 6.9f, 20.7f, 1f, role = GlyphRole.Line),
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
            // 外框 `Line`：这颗的主体就是那个圆角方框，留 Body 只剩里面那道折线，
            // 看着像"一张图破了"。
            GlyphBox(3f, 3f, 21f, 21f, 5f, role = GlyphRole.Line),
            GlyphPolyline(listOf(GlyphVertex(5.6f, 16.8f, 0f), GlyphVertex(9.6f, 12.8f, 0.8f), GlyphVertex(12.8f, 16f, 0.8f), GlyphVertex(14.6f, 14.2f, 0.8f), GlyphVertex(18.4f, 18f, 0f)), role = GlyphRole.Detail),
            GlyphCircle(15.2f, 8.8f, 1.5f, role = GlyphRole.Detail, solid = true),
        ),
    )
}
