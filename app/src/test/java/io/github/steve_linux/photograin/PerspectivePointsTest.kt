// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.math.Affine
import io.github.steve_linux.photograin.math.Homography
import io.github.steve_linux.photograin.math.Horizon
import io.github.steve_linux.photograin.math.Vec2
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.Geometry
import io.github.steve_linux.photograin.model.GeometryMapper
import io.github.steve_linux.photograin.model.Quad
import io.github.steve_linux.photograin.model.flippedHorizontally
import io.github.steve_linux.photograin.model.flippedVertically
import io.github.steve_linux.photograin.model.rotatedClockwise
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerspectivePointsTest {
    private val facade = Quad(0.3f, 0.2f, 0.7f, 0.25f, 0.8f, 0.85f, 0.2f, 0.8f)

    @Test
    fun homographyFromPointsMapsEachPoint() {
        val from = listOf(Vec2(10.0, 20.0), Vec2(300.0, 40.0), Vec2(280.0, 250.0), Vec2(30.0, 200.0))
        val to = listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, 80.0), Vec2(0.0, 80.0))
        val h = Homography.fromPoints(from, to)!!
        for (k in 0 until 4) {
            val p = h.apply(from[k])
            assertEquals(to[k].x, p.x, 1e-6)
            assertEquals(to[k].y, p.y, 1e-6)
        }
        // Three points on a line leave no unique answer.
        val line = listOf(Vec2(0.0, 0.0), Vec2(1.0, 1.0), Vec2(2.0, 2.0), Vec2(0.0, 5.0))
        assertNull(Homography.fromPoints(line, to))
    }

    @Test
    fun quadBecomesARectangle() {
        val m = GeometryMapper(600, 400, BaseOrientation(), Geometry(quad = facade))
        val c = facade.points.map { (x, y) -> m.sourceToFrame.apply(x * 600.0, y * 400.0) }
        assertEquals(c[0].y, c[1].y, 1e-6)
        assertEquals(c[3].y, c[2].y, 1e-6)
        assertEquals(c[0].x, c[3].x, 1e-6)
        assertEquals(c[1].x, c[2].x, 1e-6)
        // Centered where the points were.
        val cx = facade.points.sumOf { it.first.toDouble() } / 4 * 600
        assertEquals(cx, (c[0].x + c[1].x) / 2, 1e-6)
        // The far side is squeezed, so tiles there need more source.
        assertTrue(m.perspectiveStretch() > 1.05)
        assertEquals(1.0, GeometryMapper(600, 400, BaseOrientation(), Geometry(straighten = 10f)).perspectiveStretch(), 1e-6)
    }

    @Test
    fun largestCropBeatsTheCentredOne() {
        // A lopsided correction: the content leans to one side of the frame.
        val lopsided = Quad(0.1f, 0.1f, 0.5f, 0.3f, 0.5f, 0.7f, 0.1f, 0.9f)
        val m = GeometryMapper(600, 400, BaseOrientation(), Geometry(quad = lopsided))
        val ratio = 1.5f
        val centred = m.centeredCrop(ratio)
        val largest = m.largestCrop(ratio)
        assertTrue(m.isCropValid(largest))
        assertTrue(largest.width * largest.height >= centred.width * centred.height)
        assertTrue("largest ${largest.width * largest.height} centred ${centred.width * centred.height}", largest.width * largest.height > 1.1f * centred.width * centred.height)
        assertEquals(ratio.toDouble(), (largest.width * 600.0) / (largest.height * 400.0), 0.01)
    }

    @Test
    fun unusableQuadIsIgnored() {
        val crossed = Quad(0.2f, 0.2f, 0.8f, 0.8f, 0.8f, 0.2f, 0.2f, 0.8f)
        val tiny = Quad(0.5f, 0.5f, 0.51f, 0.5f, 0.51f, 0.51f, 0.5f, 0.51f)
        assertFalse(crossed.isUsable(600.0, 400.0))
        assertFalse(tiny.isUsable(600.0, 400.0))
        assertTrue(facade.isUsable(600.0, 400.0))
        val m = GeometryMapper(600, 400, BaseOrientation(), Geometry(quad = crossed))
        val p = m.sourceToFrame.apply(123.0, 45.0)
        assertEquals(123.0, p.x, 1e-9)
        assertEquals(45.0, p.y, 1e-9)
    }

    @Test
    fun quadFollowsRotationAndMirroring() {
        val g = Geometry(quad = facade, quarterTurns = 1)
        val m = GeometryMapper(600, 400, BaseOrientation(), g)
        fun norm(m: GeometryMapper, s: Vec2) = m.sourceToFrame.apply(s).let { Vec2(it.x / m.frameWidth, it.y / m.frameHeight) }
        val sources = listOf(Vec2(100.0, 50.0), Vec2(420.0, 300.0), Vec2(300.0, 200.0))
        val cases: List<Pair<Geometry, (Vec2) -> Vec2>> = listOf(
            g.rotatedClockwise() to { p -> Vec2(1 - p.y, p.x) },
            g.flippedHorizontally() to { p -> Vec2(1 - p.x, p.y) },
            g.flippedVertically() to { p -> Vec2(p.x, 1 - p.y) },
        )
        for ((changed, expect) in cases) {
            val m2 = GeometryMapper(600, 400, BaseOrientation(), changed)
            assertTrue(changed.quad!!.isUsable(m2.frameWidth, m2.frameHeight))
            for (s in sources) {
                val e = expect(norm(m, s))
                val a = norm(m2, s)
                assertEquals(e.x, a.x, 1e-6)
                assertEquals(e.y, a.y, 1e-6)
            }
        }
    }

    /** A w x h image of light sky over dark ground, the horizon tilted clockwise by [degrees], with noise. */
    private fun horizonImage(w: Int, h: Int, degrees: Double, seed: Int): FloatArray {
        val r = Random(seed)
        val t = degrees * PI / 180
        return FloatArray(w * h) { i ->
            val x = i % w - w / 2.0
            val y = i / w - h / 2.0
            // Signed distance across the tilted line, softened over a pixel or two.
            val d = -x * sin(t) + y * cos(t)
            val sky = (0.5 - d / 2).coerceIn(0.0, 1.0).toFloat()
            0.25f + 0.55f * sky + (r.nextFloat() - 0.5f) * 0.04f
        }
    }

    @Test
    fun horizonIsLevelled() {
        for (degrees in listOf(-7.5, -2.0, 0.0, 3.3, 12.0)) {
            val angle = Horizon.straightenAngle(horizonImage(400, 300, degrees, degrees.toInt() + 3), 400, 300)
            assertNotNull("tilt $degrees", angle)
            assertEquals("tilt $degrees", -degrees, angle!!.toDouble(), 0.3)
            // Turning the line's direction by the angle makes it horizontal.
            val t = degrees * PI / 180
            val dir = Affine.rotate(angle.toDouble() * PI / 180).apply(Vec2(cos(t), sin(t)))
            assertEquals(0.0, dir.y, 0.006)
        }
    }

    @Test
    fun tiltedVerticalLinesCountToo() {
        // Stripes leaning clockwise by 4 degrees: a vertical line turned the same way.
        val w = 400
        val h = 300
        val t = 4.0 * PI / 180
        val img = FloatArray(w * h) { i ->
            val x = i % w - w / 2.0
            val y = i / w - h / 2.0
            val across = x * cos(t) + y * sin(t)
            // Soft-edged stripes 40 pixels wide, as a camera would record them.
            val phase = ((across % 80) + 80) % 80
            val ramp = minOf(phase, 80 - phase) - 20
            0.45f + 0.25f * ramp.coerceIn(-1.0, 1.0).toFloat()
        }
        val angle = Horizon.straightenAngle(img, w, h)!!
        assertEquals(-4.0, angle.toDouble(), 0.3)
    }

    @Test
    fun disagreeingLinesAreLeftAlone() {
        // Rows leaning one way and columns the other, as on a box shot at an angle: no single
        // turn levels both, so nothing is suggested.
        val w = 400
        val h = 300
        val rows = 6.0 * PI / 180
        val columns = -6.0 * PI / 180
        fun stripes(across: Double): Float {
            val phase = ((across % 60) + 60) % 60
            return (minOf(phase, 60 - phase) - 15).coerceIn(-1.0, 1.0).toFloat()
        }
        val img = FloatArray(w * h) { i ->
            val x = i % w - w / 2.0
            val y = i / w - h / 2.0
            0.5f + 0.12f * stripes(-x * sin(rows) + y * cos(rows)) + 0.12f * stripes(x * cos(columns) + y * sin(columns))
        }
        assertNull(Horizon.straightenAngle(img, w, h))
    }

    @Test
    fun slantedEdgesAmongConvergingLinesAreLeftAlone() {
        // Rows slanted by 5 degrees among as many columns fanning out from a vanishing point, as
        // on a box seen from above: the slant comes from the perspective, not from the camera.
        val w = 480
        val h = 640
        val t = 5.0 * PI / 180
        val img = FloatArray(w * h) { i ->
            val x = i % w - w / 2.0
            val y = i / w - h / 2.0
            val a = -x * sin(t) + y * cos(t)
            val p = ((a % 120) + 120) % 120
            val row = (minOf(p, 120 - p) - 30).coerceIn(-1.0, 1.0)
            val q = ((atan2(x, y + 1600) * 180 / PI * 4 % 6) + 6) % 6
            val column = (minOf(q, 6 - q) - 1.5).coerceIn(-1.0, 1.0)
            (0.5 + 0.15 * row + 0.15 * column).toFloat()
        }
        assertNull(Horizon.straightenAngle(img, w, h))
    }

    @Test
    fun noiseHasNoHorizon() {
        val r = Random(7)
        val img = FloatArray(200 * 200) { r.nextFloat() }
        assertNull(Horizon.straightenAngle(img, 200, 200))
    }
}
