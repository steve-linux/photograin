// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.math.Affine
import io.github.steve_linux.photograin.math.Homography
import io.github.steve_linux.photograin.math.Vec2
import io.github.steve_linux.photograin.math.toHomography
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.CropRect
import io.github.steve_linux.photograin.model.Geometry
import io.github.steve_linux.photograin.model.GeometryMapper
import io.github.steve_linux.photograin.model.PxSize
import io.github.steve_linux.photograin.model.flippedHorizontally
import io.github.steve_linux.photograin.model.flippedVertically
import io.github.steve_linux.photograin.model.rotatedClockwise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    // Crops are stored as Float, so allow a small fraction of a pixel.
    private fun assertPoint(expected: Vec2, actual: Vec2) {
        assertEquals(expected.x, actual.x, 1e-3)
        assertEquals(expected.y, actual.y, 1e-3)
    }

    @Test
    fun affineInverseRoundTrips() {
        val t = Affine.rotateAround(0.3, 10.0, 20.0).then(Affine.scale(2.0, 3.0)).then(Affine.translate(5.0, -7.0))
        val p = Vec2(13.0, 42.0)
        assertPoint(p, t.inverse().apply(t.apply(p)))
    }

    @Test
    fun identityGeometryMapsOutputToSource() {
        val m = GeometryMapper(400, 300, BaseOrientation(), Geometry())
        assertEquals(PxSize(400, 300), m.outputSize)
        assertPoint(Vec2(12.0, 34.0), m.outputToSource().apply(Vec2(12.0, 34.0)))
    }

    @Test
    fun quarterTurnSwapsDimensionsAndCorners() {
        val m = GeometryMapper(400, 300, BaseOrientation(), Geometry(quarterTurns = 1))
        assertEquals(PxSize(300, 400), m.outputSize)
        // After a clockwise turn the output's top-left shows the source's bottom-left.
        assertPoint(Vec2(0.0, 300.0), m.outputToSource().apply(Vec2(0.0, 0.0)))
        // And the output's top-right shows the source's top-left.
        assertPoint(Vec2(0.0, 0.0), m.outputToSource().apply(Vec2(300.0, 0.0)))
    }

    @Test
    fun exifRotationIsApplied() {
        val m = GeometryMapper(400, 300, BaseOrientation.fromExif(6), Geometry())
        assertEquals(PxSize(300, 400), m.outputSize)
    }

    @Test
    fun exifTransposeMapsDiagonally() {
        val m = GeometryMapper(400, 300, BaseOrientation.fromExif(5), Geometry())
        assertEquals(PxSize(300, 400), m.outputSize)
        // Transpose: output (x, y) shows source (y, x).
        assertPoint(Vec2(40.0, 10.0), m.outputToSource().apply(Vec2(10.0, 40.0)))
    }

    @Test
    fun cropOffsetsOutput() {
        val g = Geometry(crop = CropRect(0.25f, 0.5f, 0.75f, 1f))
        val m = GeometryMapper(400, 300, BaseOrientation(), g)
        assertEquals(PxSize(200, 150), m.outputSize)
        assertPoint(Vec2(100.0, 150.0), m.outputToSource().apply(Vec2(0.0, 0.0)))
    }

    @Test
    fun straightenedFullCropIsInvalidAndFitCropFixesIt() {
        val g = Geometry(straighten = 10f)
        val m = GeometryMapper(400, 300, BaseOrientation(), g)
        assertFalse(m.isCropValid(CropRect.FULL))
        val fitted = m.fitCrop(CropRect.FULL)
        assertTrue(m.isCropValid(fitted))
        assertTrue(fitted.width < 1f)
        // Aspect ratio is preserved.
        assertEquals(1f, fitted.width / fitted.height, 1e-4f)
    }

    @Test
    fun rotatingCarriesCropAlong() {
        val g = Geometry(crop = CropRect(0.1f, 0.2f, 0.5f, 0.9f))
        val before = GeometryMapper(400, 300, BaseOrientation(), g)
        val after = GeometryMapper(400, 300, BaseOrientation(), g.rotatedClockwise())
        // Same source pixels end up in the output, just rotated.
        assertEquals(before.outputSize.width, after.outputSize.height)
        assertEquals(before.outputSize.height, after.outputSize.width)
        val srcTopLeftBefore = before.outputToSource().apply(Vec2(0.0, 0.0))
        val srcTopRightAfter = after.outputToSource().apply(Vec2(after.outputSize.width.toDouble(), 0.0))
        assertPoint(srcTopLeftBefore, srcTopRightAfter)
    }

    @Test
    fun flippingTwiceRestoresGeometry() {
        val g = Geometry(straighten = 5f, crop = CropRect(0.1f, 0.2f, 0.5f, 0.9f))
        val twice = g.flippedHorizontally().flippedHorizontally()
        assertEquals(g.flipH, twice.flipH)
        assertEquals(g.straighten, twice.straighten, 1e-6f)
        assertEquals(g.crop.left, twice.crop.left, 1e-6f)
        assertEquals(g.crop.right, twice.crop.right, 1e-6f)
    }

    @Test
    fun centeredCropHasRequestedPixelRatio() {
        val m = GeometryMapper(400, 300, BaseOrientation(), Geometry())
        val c = m.centeredCrop(1f)
        assertEquals(c.width * 400, c.height * 300, 0.5f)
    }

    @Test
    fun homographyInverseAndAffineAgree() {
        val h = Homography(1.2, 0.1, 5.0, -0.2, 0.9, 3.0, 0.001, -0.002, 1.0)
        val p = Vec2(40.0, 70.0)
        assertPoint(p, h.inverse().apply(h.apply(p)))
        val a = Affine.rotateAround(0.4, 3.0, 4.0).then(Affine.translate(2.0, -1.0))
        assertPoint(a.apply(p), a.toHomography().apply(p))
        assertPoint(h.apply(a.apply(p)), a.toHomography().then(h).apply(p))
    }

    @Test
    fun verticalPerspectiveWidensTheTop() {
        val m = GeometryMapper(400, 300, BaseOrientation(), Geometry(vertical = 1f))
        val toFrame = m.sourceToFrame
        val topWidth = toFrame.apply(Vec2(400.0, 0.0)).x - toFrame.apply(Vec2(0.0, 0.0)).x
        val bottomWidth = toFrame.apply(Vec2(400.0, 300.0)).x - toFrame.apply(Vec2(0.0, 300.0)).x
        assertTrue(topWidth > 400)
        assertTrue(bottomWidth < 400)
        // The center stays put.
        assertPoint(Vec2(200.0, 150.0), toFrame.apply(Vec2(200.0, 150.0)))
        // Empty corners at the bottom: the full crop is no longer valid, and fitting fixes it.
        assertFalse(m.isCropValid(CropRect.FULL))
        assertTrue(m.isCropValid(m.fitCrop(CropRect.FULL)))
    }

    @Test
    fun rotatingAndFlippingCarryThePerspective() {
        val g = Geometry(vertical = 0.6f, horizontal = -0.3f)
        val src = Vec2(50.0, 40.0)
        fun framePoint(geo: Geometry) = GeometryMapper(400, 300, BaseOrientation(), geo).let {
            val f = it.sourceToFrame.apply(src)
            Vec2(f.x / it.frameWidth, f.y / it.frameHeight)
        }
        val before = framePoint(g)
        // A clockwise turn moves the normalized point (x, y) to (1 - y, x).
        val turned = framePoint(g.rotatedClockwise())
        assertPoint(Vec2(1 - before.y, before.x), turned)
        val mirrored = framePoint(g.flippedHorizontally())
        assertPoint(Vec2(1 - before.x, before.y), mirrored)
        val flipped = framePoint(g.flippedVertically())
        assertPoint(Vec2(before.x, 1 - before.y), flipped)
    }
}
