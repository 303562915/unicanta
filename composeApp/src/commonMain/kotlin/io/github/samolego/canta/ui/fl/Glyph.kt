package io.github.samolego.canta.ui.fl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The icon language FlClash draws with (prism_ui's glyphs): every icon is a set of
 * shapes on a 24×24 grid, traced along the centre of a 1.8 wide stroke, so one glyph
 * can be outlined or filled and morph between the two.
 */
enum class GlyphRole { Body, Detail, Line }

/** One geometric piece of a glyph, on the 24×24 grid. */
sealed class GlyphShape {
    abstract val role: GlyphRole

    /** Drawn as an area rather than a stroke; only a circle or a path may be solid. */
    open val solid: Boolean get() = false

    /** The centre line of the stroke, in grid units. */
    abstract fun buildPath(): Path
}

/**
 * A rounded rectangle. FlClash draws these as superellipses; a rounded rectangle with
 * the same corner radius is within a fifth of a pixel at icon sizes.
 */
class GlyphBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val radius: Float,
    override val role: GlyphRole = GlyphRole.Body,
) : GlyphShape() {
    override fun buildPath(): Path = Path().apply {
        addRoundRect(
            RoundRect(
                Rect(left, top, right, bottom),
                CornerRadius(radius, radius),
            )
        )
    }
}

class GlyphCircle(
    val x: Float,
    val y: Float,
    val radius: Float,
    override val role: GlyphRole = GlyphRole.Body,
    override val solid: Boolean = false,
) : GlyphShape() {
    override fun buildPath(): Path = Path().apply {
        addOval(Rect(center = Offset(x, y), radius = radius))
    }
}

class GlyphOval(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    override val role: GlyphRole = GlyphRole.Body,
) : GlyphShape() {
    override fun buildPath(): Path = Path().apply {
        addOval(Rect(left, top, right, bottom))
    }
}

/** An arc of a circle, in degrees: 0 points right, positive sweeps clockwise. */
class GlyphArc(
    val x: Float,
    val y: Float,
    val radius: Float,
    val start: Float,
    val sweep: Float,
    override val role: GlyphRole = GlyphRole.Body,
) : GlyphShape() {
    override fun buildPath(): Path = Path().apply {
        addArc(
            Rect(center = Offset(x, y), radius = radius),
            start,
            sweep,
        )
    }
}

/** Joins two circles with a line whose round caps end on their strokes. */
class GlyphLink(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val radius: Float,
) : GlyphShape() {
    override val role: GlyphRole get() = GlyphRole.Line

    override fun buildPath(): Path = Path().apply {
        val deltaX = toX - fromX
        val deltaY = toY - fromY
        val length = hypot(deltaX, deltaY)
        if (length <= 0f) {
            moveTo(fromX, fromY)
            return@apply
        }
        val inset = (radius + FlGlyph.strokeWidth) / length
        moveTo(fromX + deltaX * inset, fromY + deltaY * inset)
        lineTo(toX - deltaX * inset, toY - deltaY * inset)
    }
}

/** A point of a polyline; a positive [radius] rounds the corner it sits on. */
class GlyphVertex(val x: Float, val y: Float, val radius: Float = 0f)

class GlyphPolyline(
    val vertices: List<GlyphVertex>,
    val closed: Boolean = false,
    override val role: GlyphRole = GlyphRole.Body,
) : GlyphShape() {
    override fun buildPath(): Path {
        val count = vertices.size
        val path = Path()
        if (count == 0) return path
        if (count == 1) {
            path.moveTo(vertices[0].x, vertices[0].y)
            return path
        }
        // The pair of points a rounded corner leaves and rejoins at.
        fun corner(index: Int): Pair<Offset, Offset> {
            val vertex = vertices[index]
            val previous = vertices[(index - 1 + count) % count]
            val next = vertices[(index + 1) % count]
            val toPreviousX = previous.x - vertex.x
            val toPreviousY = previous.y - vertex.y
            val toNextX = next.x - vertex.x
            val toNextY = next.y - vertex.y
            val previousLength = hypot(toPreviousX, toPreviousY)
            val nextLength = hypot(toNextX, toNextY)
            val enterScale = if (previousLength > 0f) {
                min(vertex.radius, previousLength / 2f) / previousLength
            } else {
                0f
            }
            val exitScale = if (nextLength > 0f) {
                min(vertex.radius, nextLength / 2f) / nextLength
            } else {
                0f
            }
            return Offset(vertex.x + toPreviousX * enterScale, vertex.y + toPreviousY * enterScale) to
                Offset(vertex.x + toNextX * exitScale, vertex.y + toNextY * exitScale)
        }

        val first = if (closed) corner(0).second else Offset(vertices[0].x, vertices[0].y)
        path.moveTo(first.x, first.y)
        val last = if (closed) count else count - 1
        var index = 1
        while (index <= last) {
            val vertex = vertices[index % count]
            if (!closed && index == count - 1) {
                path.lineTo(vertex.x, vertex.y)
                break
            }
            val (enter, exit) = corner(index % count)
            path.lineTo(enter.x, enter.y)
            path.quadraticBezierTo(vertex.x, vertex.y, exit.x, exit.y)
            index++
        }
        if (closed) path.close()
        return path
    }
}

/**
 * SVG path data on the grid, for curves the other shapes cannot draw. Supports the
 * subset FlClash's icons use: M, L, H, V, C, Q, A and Z, absolute and relative.
 */
class GlyphPathData(
    val data: String,
    override val role: GlyphRole = GlyphRole.Body,
    override val solid: Boolean = false,
) : GlyphShape() {
    override fun buildPath(): Path = SvgPath.parse(data)
}

/** A glyph: the shapes an icon is drawn from, plus whether it mirrors in RTL. */
class FlGlyph(
    val shapes: List<GlyphShape>,
    val matchTextDirection: Boolean = false,
) {
    /** Paths are built once and reused: a glyph is a constant of the icon set. */
    val paths: List<Path> by lazy(LazyThreadSafetyMode.NONE) {
        shapes.map { it.buildPath() }
    }

    companion object {
        /** Icons are designed on this grid. */
        const val size = 24f

        /** The weight a glyph is outlined at. */
        const val strokeWidth = 1.8f
    }
}

/**
 * Paints a glyph. [fill] runs from 0 (outlined) to 1 (filled): a body is drawn as a
 * stroke that widens until it swallows its own inside, its details thin away and then
 * come back as cutouts, and lines gain a little weight.
 */
internal fun DrawScope.drawGlyph(glyph: FlGlyph, fill: Float, color: Color) {
    val opaque = color.copy(alpha = 1f)
    // A local named `scale` would shadow the DrawScope extension this calls.
    val factor = size.minDimension / FlGlyph.size
    if (factor <= 0f) return

    translate(
        left = (size.width - FlGlyph.size * factor) / 2f,
        top = (size.height - FlGlyph.size * factor) / 2f,
    ) {
        scale(factor, factor, pivot = Offset.Zero) {
            val cut = fill > detailSwap
            val detailScale = if (cut) {
                (fill - detailSwap) / (1f - detailSwap)
            } else {
                1f - fill / detailSwap
            }
            // Details above the swap are cut out of the glyph's own layer; a partial one
            // is thinned instead so a detail never reads as a stroke and a cutout at once.
            val layered = color.alpha < 1f || (cut && detailScale > 0f)
            drawIntoCanvas { canvas ->
                if (layered) {
                    canvas.saveLayer(
                        Rect(0f, 0f, FlGlyph.size, FlGlyph.size),
                        Paint().apply { alpha = color.alpha },
                    )
                } else {
                    canvas.save()
                }

                glyph.shapes.forEachIndexed { index, shape ->
                    if (shape.role == GlyphRole.Body) {
                        drawBody(glyph.paths[index], opaque, fill)
                    }
                }
                if (detailScale > 0f) {
                    glyph.shapes.forEachIndexed { index, shape ->
                        if (shape.role == GlyphRole.Detail) {
                            drawDetail(glyph.paths[index], shape, opaque, detailScale, cut)
                        }
                    }
                }
                val lineWidth = FlGlyph.strokeWidth + (filledLineWidth - FlGlyph.strokeWidth) * fill.coerceIn(0f, 1f)
                glyph.shapes.forEachIndexed { index, shape ->
                    val path = glyph.paths[index]
                    when {
                        shape.role == GlyphRole.Line && shape.solid ->
                            drawScaledPath(path, lineWidth / FlGlyph.strokeWidth, opaque)

                        shape.role != GlyphRole.Detail ->
                            drawPath(path, opaque, style = strokeOf(if (shape.role == GlyphRole.Line) lineWidth else FlGlyph.strokeWidth))

                        else -> Unit
                    }
                }
                canvas.restore()
            }
        }
    }
}

private const val detailSwap = 0.5f
private const val filledLineWidth = 2.2f

private fun strokeOf(width: Float) = Stroke(
    width = width,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)

private fun DrawScope.drawBody(path: Path, color: Color, fill: Float) {
    if (fill <= 0f) return
    if (fill >= 1f) {
        drawPath(path, color)
        return
    }
    // A stroke wide enough to reach the middle fills the shape from its edge inwards.
    val bounds = path.getBounds()
    val depth = bounds.size.minDimension / 2f
    val reach = FlGlyph.strokeWidth / 2f + (depth - FlGlyph.strokeWidth / 2f) * fill
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.clipPath(path)
        drawPath(path, color, style = strokeOf(reach * 2f))
        canvas.restore()
    }
}

private fun DrawScope.drawDetail(
    path: Path,
    shape: GlyphShape,
    color: Color,
    scale: Float,
    cut: Boolean,
) {
    val blend = if (cut) BlendMode.Clear else BlendMode.SrcOver
    if (!shape.solid) {
        drawPath(
            path,
            color,
            style = strokeOf(FlGlyph.strokeWidth * scale),
            blendMode = blend,
        )
        return
    }
    val center = path.getBounds().center
    drawScaledAt(center, scale) {
        drawPath(path, color, style = Fill, blendMode = blend)
    }
}

/** Draws [block] scaled about [center], which is how a solid detail grows. */
private inline fun DrawScope.drawScaledAt(center: Offset, factor: Float, block: DrawScope.() -> Unit) {
    translate(left = center.x, top = center.y) {
        scale(factor, factor, pivot = Offset.Zero) {
            translate(left = -center.x, top = -center.y) {
                block()
            }
        }
    }
}

/** Draws a solid path scaled about its own centre. */
private fun DrawScope.drawScaledPath(path: Path, factor: Float, color: Color) {
    drawScaledAt(path.getBounds().center, factor) {
        drawPath(path, color)
    }
}

/**
 * A small SVG path reader: enough of the grammar for the icons FlClash ships, with
 * elliptical arcs converted to cubics.
 */
internal object SvgPath {
    private val tokenPattern = Regex("[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?")

    fun parse(data: String): Path {
        val path = Path()
        val tokens = tokenPattern.findAll(data).map { it.value }.toList()
        var index = 0
        var current = Offset.Zero
        var start = Offset.Zero
        var command = ' '
        var previousControl: Offset? = null

        fun hasNumber(): Boolean = index < tokens.size && tokens[index].firstOrNull()?.let { !it.isLetter() } == true
        fun number(): Float = tokens[index++].toFloat()
        fun flag(): Boolean = number() != 0f

        while (index < tokens.size) {
            val token = tokens[index]
            if (token.length == 1 && token[0].isLetter()) {
                command = token[0]
                index++
            }
            val relative = command.isLowerCase()
            val origin = if (relative) current else Offset.Zero
            when (command.uppercaseChar()) {
                'M' -> {
                    val x = origin.x + number()
                    val y = origin.y + number()
                    path.moveTo(x, y)
                    current = Offset(x, y)
                    start = current
                    // Extra pairs after a moveto are linetos.
                    command = if (relative) 'l' else 'L'
                    previousControl = null
                }

                'L' -> {
                    if (!hasNumber()) break
                    val x = origin.x + number()
                    val y = origin.y + number()
                    path.lineTo(x, y)
                    current = Offset(x, y)
                    previousControl = null
                }

                'H' -> {
                    if (!hasNumber()) break
                    val x = origin.x + number()
                    path.lineTo(x, current.y)
                    current = Offset(x, current.y)
                    previousControl = null
                }

                'V' -> {
                    if (!hasNumber()) break
                    val y = origin.y + number()
                    path.lineTo(current.x, y)
                    current = Offset(current.x, y)
                    previousControl = null
                }

                'C' -> {
                    if (!hasNumber()) break
                    val control1 = Offset(origin.x + number(), origin.y + number())
                    val control2 = Offset(origin.x + number(), origin.y + number())
                    val end = Offset(origin.x + number(), origin.y + number())
                    path.cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
                    previousControl = control2
                    current = end
                }

                'S' -> {
                    if (!hasNumber()) break
                    val reflected = previousControl?.let { Offset(2 * current.x - it.x, 2 * current.y - it.y) }
                        ?: current
                    val control2 = Offset(origin.x + number(), origin.y + number())
                    val end = Offset(origin.x + number(), origin.y + number())
                    path.cubicTo(reflected.x, reflected.y, control2.x, control2.y, end.x, end.y)
                    previousControl = control2
                    current = end
                }

                'Q' -> {
                    if (!hasNumber()) break
                    val control = Offset(origin.x + number(), origin.y + number())
                    val end = Offset(origin.x + number(), origin.y + number())
                    path.quadraticBezierTo(control.x, control.y, end.x, end.y)
                    previousControl = control
                    current = end
                }

                'T' -> {
                    if (!hasNumber()) break
                    val control = previousControl?.let { Offset(2 * current.x - it.x, 2 * current.y - it.y) }
                        ?: current
                    val end = Offset(origin.x + number(), origin.y + number())
                    path.quadraticBezierTo(control.x, control.y, end.x, end.y)
                    previousControl = control
                    current = end
                }

                'A' -> {
                    if (!hasNumber()) break
                    val radiusX = number()
                    val radiusY = number()
                    val rotation = number()
                    val largeArc = flag()
                    val sweep = flag()
                    val end = Offset(origin.x + number(), origin.y + number())
                    arcTo(path, current, end, radiusX, radiusY, rotation, largeArc, sweep)
                    current = end
                    previousControl = null
                }

                'Z' -> {
                    path.close()
                    current = start
                    previousControl = null
                }

                else -> index++
            }
            // A coordinate group after a close has nothing to apply to; the next
            // command letter, if any, starts a new subpath.
            if (command.uppercaseChar() == 'Z') command = ' '
        }
        return path
    }

    /** Endpoint parameterisation of an elliptical arc, appended as cubics. */
    private fun arcTo(
        path: Path,
        from: Offset,
        to: Offset,
        radiusX: Float,
        radiusY: Float,
        rotationDegrees: Float,
        largeArc: Boolean,
        sweep: Boolean,
    ) {
        var rx = abs(radiusX)
        var ry = abs(radiusY)
        if (rx == 0f || ry == 0f || from == to) {
            path.lineTo(to.x, to.y)
            return
        }
        val rotation = rotationDegrees * (PI.toFloat() / 180f)
        val cosRotation = cos(rotation)
        val sinRotation = sin(rotation)
        val halfDeltaX = (from.x - to.x) / 2f
        val halfDeltaY = (from.y - to.y) / 2f
        val x1 = cosRotation * halfDeltaX + sinRotation * halfDeltaY
        val y1 = -sinRotation * halfDeltaX + cosRotation * halfDeltaY

        // Radii too small for the endpoints grow until the arc fits.
        val lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
        if (lambda > 1f) {
            val growth = sqrt(lambda)
            rx *= growth
            ry *= growth
        }

        val numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
        val denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1
        val coefficient = sqrt((numerator / denominator).coerceAtLeast(0f)) *
            (if (largeArc == sweep) -1f else 1f)
        val centerX = coefficient * rx * y1 / ry
        val centerY = -coefficient * ry * x1 / rx
        val center = Offset(
            cosRotation * centerX - sinRotation * centerY + (from.x + to.x) / 2f,
            sinRotation * centerX + cosRotation * centerY + (from.y + to.y) / 2f,
        )

        fun angle(ux: Float, uy: Float, vx: Float, vy: Float): Float {
            val dot = ux * vx + uy * vy
            val length = hypot(ux, uy) * hypot(vx, vy)
            if (length == 0f) return 0f
            val value = (dot / length).coerceIn(-1f, 1f)
            val sign = if (ux * vy - uy * vx < 0f) -1f else 1f
            return sign * acos(value)
        }

        val startAngle = angle(1f, 0f, (x1 - centerX) / rx, (y1 - centerY) / ry)
        var sweepAngle = angle(
            (x1 - centerX) / rx,
            (y1 - centerY) / ry,
            (-x1 - centerX) / rx,
            (-y1 - centerY) / ry,
        )
        val fullTurn = 2 * PI.toFloat()
        if (!sweep && sweepAngle > 0f) sweepAngle -= fullTurn
        if (sweep && sweepAngle < 0f) sweepAngle += fullTurn

        val quarter = PI.toFloat() / 2f
        val segments = ceil(abs(sweepAngle) / quarter).toInt().coerceAtLeast(1)
        val step = sweepAngle / segments
        var angleStart = startAngle
        repeat(segments) {
            val angleEnd = angleStart + step
            appendArcSegment(path, center, rx, ry, rotation, angleStart, angleEnd)
            angleStart = angleEnd
        }
    }

    /** One stretch of the arc, at most a quarter turn, as a single cubic. */
    private fun appendArcSegment(
        path: Path,
        center: Offset,
        rx: Float,
        ry: Float,
        rotation: Float,
        startAngle: Float,
        endAngle: Float,
    ) {
        val alpha = 4f / 3f * tan((endAngle - startAngle) / 4f)
        val cosRotation = cos(rotation)
        val sinRotation = sin(rotation)
        fun point(angle: Float): Offset = Offset(
            center.x + rx * cos(angle) * cosRotation - ry * sin(angle) * sinRotation,
            center.y + rx * cos(angle) * sinRotation + ry * sin(angle) * cosRotation,
        )

        fun derivative(angle: Float): Offset = Offset(
            -rx * sin(angle) * cosRotation - ry * cos(angle) * sinRotation,
            -rx * sin(angle) * sinRotation + ry * cos(angle) * cosRotation,
        )

        val start = point(startAngle)
        val end = point(endAngle)
        val startDerivative = derivative(startAngle)
        val endDerivative = derivative(endAngle)
        path.cubicTo(
            start.x + startDerivative.x * alpha,
            start.y + startDerivative.y * alpha,
            end.x - endDerivative.x * alpha,
            end.y - endDerivative.y * alpha,
            end.x,
            end.y,
        )
    }
}
