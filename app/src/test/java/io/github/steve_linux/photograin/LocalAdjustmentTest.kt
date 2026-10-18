// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.math.Affine
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.BrushRaster
import io.github.steve_linux.photograin.model.BrushStroke
import io.github.steve_linux.photograin.model.CropRect
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Geometry
import io.github.steve_linux.photograin.model.GeometryMapper
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.LocalParams
import io.github.steve_linux.photograin.model.Mask
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.render.ShaderParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class LocalAdjustmentTest {
    @Test
    fun linearMaskFadesFromStartToEnd() {
        val m = Mask(MaskType.LINEAR, x0 = 0.5f, y0 = 0.2f, x1 = 0.5f, y1 = 0.6f)
        assertEquals(1f, m.weight(0.3f, 0.1f, 1f, 1f), 1e-6f)
        assertEquals(0.5f, m.weight(0.9f, 0.4f, 1f, 1f), 1e-6f)
        assertEquals(0f, m.weight(0.5f, 0.9f, 1f, 1f), 1e-6f)
        assertEquals(1f, m.copy(invert = true).weight(0.5f, 0.9f, 1f, 1f), 1e-6f)
    }

    @Test
    fun radialMaskIsRoundInLongEdgeUnits() {
        // A 2:1 source: a circle of radius 0.25 long edges spans half the width and the full height.
        val m = Mask(MaskType.RADIAL, x0 = 0.5f, y0 = 0.5f, radiusX = 0.25f, radiusY = 0.25f, feather = 0.2f)
        assertEquals(1f, m.weight(0.5f, 0.5f, 1f, 0.5f), 1e-6f)
        assertEquals(1f, m.weight(0.69f, 0.5f, 1f, 0.5f), 1e-6f) // 0.19 long edges right
        assertEquals(1f, m.weight(0.5f, 0.88f, 1f, 0.5f), 1e-6f) // 0.19 long edges down
        assertEquals(0f, m.weight(0.76f, 0.5f, 1f, 0.5f), 1e-6f)
        assertEquals(0f, m.weight(0.5f, 1.02f, 1f, 0.5f), 1e-6f)
    }

    @Test
    fun radialAngleTurnsTheEllipse() {
        val wide = Mask(MaskType.RADIAL, radiusX = 0.3f, radiusY = 0.1f, feather = 0.01f)
        assertEquals(1f, wide.weight(0.75f, 0.5f, 1f, 1f), 1e-6f)
        assertEquals(0f, wide.weight(0.5f, 0.75f, 1f, 1f), 1e-6f)
        val tall = wide.copy(angle = (PI / 2).toFloat())
        assertEquals(0f, tall.weight(0.75f, 0.5f, 1f, 1f), 1e-6f)
        assertEquals(1f, tall.weight(0.5f, 0.75f, 1f, 1f), 1e-6f)
    }

    @Test
    fun recipeKeepsMasksAndReadsOldOnes() {
        val state = EditState(
            masks = listOf(
                LocalAdjustment(Mask(MaskType.LINEAR, 0.1f, 0.2f, 0.3f, 0.4f), exposure = -0.7f, saturation = 0.2f),
                LocalAdjustment(Mask(angle = 0.5f, invert = true), clarity = 0.4f, temperature = -0.1f),
            ),
        )
        assertEquals(state, EditState.fromJson(state.toJson()))
        val old = """{"version":1,"edit":{"adjustments":{"exposure":1.0}}}"""
        assertTrue(EditState.fromJson(old)!!.masks.isEmpty())
    }

    @Test
    fun localParamsStartNeutral() {
        for (p in LocalParams.all) {
            assertEquals(p.id, 0f, p.get(LocalAdjustment()))
            assertEquals(p.max, p.get(p.set(LocalAdjustment(), p.max)))
        }
    }

    @Test
    fun normalizedMappingIgnoresResolution() {
        val g = Geometry(quarterTurns = 1, flipH = true, straighten = 7f, crop = CropRect(0.2f, 0.1f, 0.8f, 0.7f))
        val small = GeometryMapper(400, 300, BaseOrientation.fromExif(6), g).normalizedOutputToSource()
        val large = GeometryMapper(4000, 3000, BaseOrientation.fromExif(6), g).normalizedOutputToSource()
        for ((x, y) in listOf(0.0 to 0.0, 1.0 to 0.0, 0.3 to 0.8)) {
            val a = small.apply(x, y)
            val b = large.apply(x, y)
            assertEquals(a.x, b.x, 1e-9)
            assertEquals(a.y, b.y, 1e-9)
        }
        // Without geometry the output is the source.
        val id = GeometryMapper(400, 300, BaseOrientation(), Geometry()).normalizedOutputToSource().apply(0.25, 0.75)
        assertEquals(0.25, id.x, 1e-9)
        assertEquals(0.75, id.y, 1e-9)
    }

    @Test
    fun newRadialMaskFollowsTheView() {
        // Output turned a quarter: the view's horizontal runs down the source.
        val turned = GeometryMapper(400, 300, BaseOrientation(), Geometry(quarterTurns = 1)).normalizedOutputToSource()
        val local = LocalAdjustment.create(MaskType.RADIAL, turned, 400, 300)
        assertEquals(0.5f, local.mask.x0, 1e-5f)
        assertEquals(0.5f, local.mask.y0, 1e-5f)
        assertEquals((PI / 2).toFloat(), abs(local.mask.angle), 1e-5f)
        assertTrue(local.mask.radiusX > local.mask.radiusY)
        val linear = LocalAdjustment.create(MaskType.LINEAR, Affine.IDENTITY, 400, 300)
        assertTrue(linear.mask.y0 < linear.mask.y1)
        assertTrue(linear.exposure < 0f)
    }

    @Test
    fun maskUniformsPackEachMask() {
        val masks = List(10) { LocalAdjustment(Mask(MaskType.LINEAR, x0 = it / 10f), exposure = it.toFloat()) }
        val u = ShaderParams.maskUniforms(masks)
        assertEquals(LocalAdjustment.MAX, u.count)
        assertEquals(0.3f, u.geo[12], 1e-6f)
        assertEquals(0f, u.opt[12], 0f)
        assertEquals(7f, u.locA[28], 0f)
        val radial = ShaderParams.maskUniforms(listOf(LocalAdjustment(Mask(radiusX = 0.4f, invert = true), clarity = 0.5f)))
        assertEquals(0.4f, radial.geo[2], 0f)
        assertEquals(1f, radial.opt[0], 0f)
        assertEquals(1f, radial.opt[3], 0f)
        assertEquals(0.5f, radial.locB[3], 0f)
    }

    @Test
    fun brushPaintsAlongTheStrokeAndErases() {
        val raster = BrushRaster(2000, 1000)
        assertEquals(1024, raster.width)
        assertEquals(512, raster.height)
        val paint = BrushStroke(listOf(0.2f, 0.5f, 0.8f, 0.5f), radius = 0.05f, hardness = 0.5f)
        assertTrue(raster.sync(listOf(paint)))
        assertEquals(1f, raster.valueAt(0.5f, 0.5f), 0f)
        assertEquals(0f, raster.valueAt(0.5f, 0.1f), 0f)
        assertEquals(0f, raster.valueAt(0.95f, 0.5f), 0f)
        val erase = BrushStroke(listOf(0.5f, 0.5f), radius = 0.02f, hardness = 0.9f, erase = true)
        raster.sync(listOf(paint, erase))
        assertEquals(0f, raster.valueAt(0.5f, 0.5f), 0f)
        assertEquals(1f, raster.valueAt(0.3f, 0.5f), 0f)
    }

    @Test
    fun growingStrokeMatchesStrokeDrawnWhole() {
        val points = listOf(0.1f, 0.1f, 0.3f, 0.25f, 0.35f, 0.6f, 0.7f, 0.65f, 0.9f, 0.2f)
        val live = BrushRaster(1200, 900)
        for (n in 2..points.size step 2) {
            live.sync(listOf(BrushStroke(points.subList(0, n).toList(), 0.04f, 0.3f)))
        }
        val whole = BrushRaster(1200, 900)
        whole.sync(listOf(BrushStroke(points, 0.04f, 0.3f)))
        assertTrue(live.data.contentEquals(whole.data))
    }
}
