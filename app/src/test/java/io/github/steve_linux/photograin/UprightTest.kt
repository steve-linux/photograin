// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.math.Homography
import io.github.steve_linux.photograin.math.LineSegment
import io.github.steve_linux.photograin.math.LineSegments
import io.github.steve_linux.photograin.math.Vec2
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.Geometry
import io.github.steve_linux.photograin.model.GeometryMapper
import io.github.steve_linux.photograin.model.Upright
import io.github.steve_linux.photograin.model.UprightMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UprightTest {
    private val w = 600
    private val h = 450

    /**
     * A facade of windows on a plain wall, in "world" coordinates where its edges are exactly
     * vertical and horizontal, seen through [view] (world -> photo pixels). Luminance 0..1.
     */
    private fun facade(view: Homography, noise: Double = 0.02, seed: Int = 1): FloatArray {
        val inv = view.inverse()
        val rnd = Random(seed)
        // Sampled 4 x 4 per pixel, so slanted edges are smooth as in a real photo.
        return FloatArray(w * h) { i ->
            var sum = 0.0
            for (sy in 0 until 4) for (sx in 0 until 4) {
                val p = inv.apply((i % w) + (sx + 0.5) / 4, (i / w) + (sy + 0.5) / 4)
                val wall = if (p.x in 40.0..560.0 && p.y in -200.0..440.0) 0.75 else 0.35
                // Windows: 50 x 70 every 90 x 110, darker.
                val fx = p.x - 70
                val fy = p.y + 180
                val inWindow = p.x in 60.0..540.0 && p.y in -190.0..430.0 &&
                    fx - floor(fx / 90) * 90 < 50 && fy - floor(fy / 110) * 110 < 70
                sum += if (inWindow) 0.2 else wall
            }
            (sum / 16 + noise * (rnd.nextDouble() - 0.5)).toFloat()
        }
    }

    /** A camera tilted up at a facade: verticals lean in towards the top, by [lean]. */
    private fun keystone(lean: Double, roll: Double = 0.0, side: Double = 0.0): Homography {
        val cx = w / 2.0
        val cy = h / 2.0
        val toUnit = Homography(a = 1 / cx, c = -1.0, e = 1 / cy, f = -1.0)
        val k = Homography(g = side, h = -lean)
        val r = Homography(a = cos(roll), b = -sin(roll), d = sin(roll), e = cos(roll))
        return toUnit.then(k).then(r).then(toUnit.inverse())
    }

    /** Worst angle, in degrees, of the world's vertical (or horizontal) lines after [toFrame]. */
    private fun worstLean(world: Homography, toFrame: Homography, vertical: Boolean): Double {
        var worst = 0.0
        val m = world.then(toFrame)
        for (k in 0..6) {
            val (a, b) = if (vertical) {
                val x = 80.0 + k * 70
                Vec2(x, -100.0) to Vec2(x, 400.0)
            } else {
                val y = -100.0 + k * 80
                Vec2(100.0, y) to Vec2(500.0, y)
            }
            val p = m.apply(a)
            val q = m.apply(b)
            val angle = if (vertical) atan2(q.x - p.x, q.y - p.y) else atan2(q.y - p.y, q.x - p.x)
            var d = abs(angle) * 180 / PI
            if (d > 90) d = 180 - d
            worst = maxOf(worst, d)
        }
        return worst
    }

    private fun corrected(view: Homography, mode: UprightMode): Homography {
        val segments = LineSegments.detect(facade(view), w, h)
        val result = Upright.correct(segments, w.toDouble(), h.toDouble(), mode)
        assertTrue("expected a correction, got $result", result is Upright.Result.Corrected)
        val geometry = Geometry(quad = (result as Upright.Result.Corrected).quad)
        return GeometryMapper(w, h, BaseOrientation(), geometry).sourceToFrame
    }

    @Test
    fun detectsTheStraightEdgesOfAFacade() {
        val segments = LineSegments.detect(facade(Homography.IDENTITY), w, h)
        assertTrue("too few segments: ${segments.size}", segments.size >= 20)
        // Every edge here is vertical or horizontal; nothing in between.
        for (s in segments) {
            val a = abs(atan2(s.y2 - s.y1, s.x2 - s.x1)) * 180 / PI
            val off = minOf(a, abs(90 - a), abs(180 - a))
            assertTrue("segment at $a degrees", off < 1.5)
        }
    }

    @Test
    fun verticalModeMakesLeaningBuildingsStraight() {
        val view = keystone(0.25)
        assertTrue(worstLean(view, Homography.IDENTITY, vertical = true) > 5)
        val fix = corrected(view, UprightMode.VERTICAL)
        assertTrue(worstLean(view, fix, vertical = true) < 0.6)
        // Horizontal lines stay level.
        assertTrue(worstLean(view, fix, vertical = false) < 0.6)
    }

    @Test
    fun verticalModeAlsoLevelsATiltedCamera() {
        val view = keystone(0.2, roll = 4 * PI / 180)
        val fix = corrected(view, UprightMode.VERTICAL)
        assertTrue(worstLean(view, fix, vertical = true) < 0.6)
    }

    @Test
    fun fullModeSquaresUpAFacadeShotFromTheSide() {
        val view = keystone(0.2, side = 0.25)
        assertTrue(worstLean(view, Homography.IDENTITY, vertical = false) > 3)
        val fix = corrected(view, UprightMode.FULL)
        assertTrue(worstLean(view, fix, vertical = true) < 0.6)
        assertTrue(worstLean(view, fix, vertical = false) < 0.6)
    }

    @Test
    fun aStraightPhotoNeedsNothing() {
        val segments = LineSegments.detect(facade(Homography.IDENTITY), w, h)
        assertEquals(Upright.Result.AlreadyStraight, Upright.correct(segments, w.toDouble(), h.toDouble(), UprightMode.VERTICAL))
    }

    @Test
    fun noiseHasNoLinesToGoBy() {
        val rnd = Random(3)
        val lum = FloatArray(w * h) { rnd.nextFloat() }
        val segments = LineSegments.detect(lum, w, h)
        assertEquals(Upright.Result.NotFound, Upright.correct(segments, w.toDouble(), h.toDouble(), UprightMode.VERTICAL))
    }

    @Test
    fun linesMeetingInsideThePhotoAreTooStrong() {
        // Lines converging on a point just above the frame: straightening them would tear it apart.
        val vx = 300.0
        val vy = -60.0
        val segments = (0..8).map { k ->
            val bx = 60.0 + k * 60
            val t1 = 0.55
            val t2 = 0.95
            LineSegment(vx + (bx - vx) * t1, vy + (440 - vy) * t1, vx + (bx - vx) * t2, vy + (440 - vy) * t2)
        }
        assertEquals(Upright.Result.TooStrong, Upright.correct(segments, w.toDouble(), h.toDouble(), UprightMode.VERTICAL))
    }

    @Test
    fun theQuadStaysInsideTheFrame() {
        for (lean in listOf(0.1, 0.2, 0.3)) {
            val segments = LineSegments.detect(facade(keystone(lean)), w, h)
            val r = Upright.correct(segments, w.toDouble(), h.toDouble(), UprightMode.VERTICAL) as Upright.Result.Corrected
            for ((x, y) in r.quad.points) assertTrue(x in 0f..1f && y in 0f..1f)
            assertTrue(r.quad.isUsable(w.toDouble(), h.toDouble()))
        }
    }

    @Test
    fun linesAreFoundInTheTurnedFrame() {
        // The same photo stored sideways, with an EXIF quarter turn: lines found in the stored
        // pixels and carried into the frame give the same points as the upright photo.
        val lum = facade(keystone(0.2))
        val stored = FloatArray(w * h) { i ->
            val x = i % h
            val y = i / h
            lum[x * w + (w - 1 - y)]
        }
        val toFrame = GeometryMapper(h, w, BaseOrientation(quarterTurns = 1), Geometry()).sourceToFrame
        val segments = LineSegments.detect(stored, h, w).map {
            val a = toFrame.apply(it.x1, it.y1)
            val b = toFrame.apply(it.x2, it.y2)
            LineSegment(a.x, a.y, b.x, b.y)
        }
        val turned = Upright.correct(segments, w.toDouble(), h.toDouble(), UprightMode.VERTICAL) as Upright.Result.Corrected
        val direct = Upright.correct(LineSegments.detect(lum, w, h), w.toDouble(), h.toDouble(), UprightMode.VERTICAL)
            as Upright.Result.Corrected
        for ((p, q) in turned.quad.points.zip(direct.quad.points)) {
            assertEquals(q.first, p.first, 0.01f)
            assertEquals(q.second, p.second, 0.01f)
        }
    }
}
