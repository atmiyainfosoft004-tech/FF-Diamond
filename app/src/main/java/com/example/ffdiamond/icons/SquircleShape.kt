package com.example.ffdiamond.icons

import android.graphics.Matrix
import android.graphics.Path
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The icon silhouette: a rounded square with **corner smoothing**, not a plain rounded rect.
 *
 * A plain `RoundRect` jumps from a straight edge into a circular arc, and that discontinuity in
 * curvature is exactly what makes an icon mask look cheap next to One UI. Smoothing replaces the
 * ends of each arc with cubic segments that ease into the straight edge, so curvature changes
 * gradually. Corner radius is 28% of the icon size and smoothing is 60%, per the design tokens.
 *
 * The geometry is solved once on a 1 x 1 square and then scaled, because the maths below is far
 * more expensive than a matrix multiply and every icon needs the same shape at the same size.
 */
object SquircleShape {

    const val CORNER_RADIUS_RATIO = 0.28f
    const val CORNER_SMOOTHING = 0.6f

    private val unitPath: Path by lazy { buildUnitPath() }
    private val matrix = Matrix()

    /** A fresh path for [size] pixels. Callers own the result and may transform it further. */
    @Synchronized
    fun pathFor(size: Float): Path {
        val scaled = Path()
        matrix.setScale(size, size)
        unitPath.transform(matrix, scaled)
        return scaled
    }

    /**
     * Solves the corner into five run lengths and traces all four corners with them.
     *
     * Each corner spends a budget of `p = (1 + smoothing) * radius` along the edge, split into a
     * shortened circular arc in the middle and a cubic on either side that blends it into the
     * straight edge. `a`, `b`, `c` and `d` are the control-point offsets of those cubics.
     */
    private fun buildUnitPath(): Path {
        val r = CORNER_RADIUS_RATIO
        val s = CORNER_SMOOTHING

        val p = (1f + s) * r
        val arcSweepDeg = 90f * (1f - s)
        val arcChordLeg = sin(Math.toRadians(arcSweepDeg / 2.0)).toFloat() * r * sqrt(2f)

        val alphaDeg = (90f - arcSweepDeg) / 2f
        val cornerToTangent = r * tan(Math.toRadians(alphaDeg / 2.0)).toFloat()
        val betaDeg = 45f * s
        val c = cornerToTangent * cos(Math.toRadians(betaDeg.toDouble())).toFloat()
        val d = c * tan(Math.toRadians(betaDeg.toDouble())).toFloat()

        val b = (p - arcChordLeg - c - d) / 3f
        val a = 2f * b

        val path = Path()
        var x = 1f - p
        var y = 0f
        path.moveTo(x, y)

        // Top-right.
        path.cubicTo(x + a, y, x + a + b, y, x + a + b + c, y + d)
        x += a + b + c; y += d
        clockwiseArc(path, x, y, arcChordLeg, arcChordLeg, r)
        x += arcChordLeg; y += arcChordLeg
        path.cubicTo(x + d, y + c, x + d, y + b + c, x + d, y + a + b + c)
        x += d; y += a + b + c

        path.lineTo(1f, 1f - p)
        x = 1f; y = 1f - p

        // Bottom-right.
        path.cubicTo(x, y + a, x, y + a + b, x - d, y + a + b + c)
        x -= d; y += a + b + c
        clockwiseArc(path, x, y, -arcChordLeg, arcChordLeg, r)
        x -= arcChordLeg; y += arcChordLeg
        path.cubicTo(x - c, y + d, x - b - c, y + d, x - a - b - c, y + d)
        x -= a + b + c; y += d

        path.lineTo(p, 1f)
        x = p; y = 1f

        // Bottom-left.
        path.cubicTo(x - a, y, x - a - b, y, x - a - b - c, y - d)
        x -= a + b + c; y -= d
        clockwiseArc(path, x, y, -arcChordLeg, -arcChordLeg, r)
        x -= arcChordLeg; y -= arcChordLeg
        path.cubicTo(x - d, y - c, x - d, y - b - c, x - d, y - a - b - c)
        x -= d; y -= a + b + c

        path.lineTo(0f, p)
        x = 0f; y = p

        // Top-left.
        path.cubicTo(x, y - a, x, y - a - b, x + d, y - a - b - c)
        x += d; y -= a + b + c
        clockwiseArc(path, x, y, arcChordLeg, -arcChordLeg, r)
        x += arcChordLeg; y -= arcChordLeg
        path.cubicTo(x + c, y - d, x + b + c, y - d, x + a + b + c, y - d)

        path.close()
        return path
    }

    /**
     * Appends a clockwise circular arc of radius [r] from the current point to a point [dx],[dy]
     * away, as a single cubic.
     *
     * A cubic approximation of a circular arc is accurate to about one part in a million below 45
     * degrees, and these arcs sweep only 36 degrees, so one segment is plenty — and it keeps the
     * whole shape in cubics instead of mixing in an `arcTo` that needs an oval rect.
     */
    private fun clockwiseArc(path: Path, x0: Float, y0: Float, dx: Float, dy: Float, r: Float) {
        val x1 = x0 + dx
        val y1 = y0 + dy
        val chord = hypot(dx, dy)
        val ux = dx / chord
        val uy = dy / chord

        // For clockwise travel in screen coordinates the centre lies 90 degrees left of the chord.
        val h = sqrt(max(0f, r * r - (chord / 2f) * (chord / 2f)))
        val cx = (x0 + x1) / 2f - uy * h
        val cy = (y0 + y1) / 2f + ux * h

        val sweep = 2f * asin((chord / 2f) / r)
        val k = 4f / 3f * tan(sweep / 4f)

        // Tangent of a clockwise circle is the radius rotated a quarter turn: (x, y) -> (-y, x).
        val t0x = -(y0 - cy)
        val t0y = x0 - cx
        val t1x = -(y1 - cy)
        val t1y = x1 - cx

        path.cubicTo(x0 + k * t0x, y0 + k * t0y, x1 - k * t1x, y1 - k * t1y, x1, y1)
    }
}
