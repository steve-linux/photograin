// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.BrushRaster
import io.github.steve_linux.photograin.model.BrushStroke
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.Mask
import io.github.steve_linux.photograin.model.MaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LassoTest {
    private fun square(l: Float, t: Float, r: Float, b: Float, erase: Boolean = false) =
        BrushStroke(listOf(l, t, r, t, r, b, l, b), radius = 0f, erase = erase, fill = true)

    @Test
    fun lassoFillsWhatItSurrounds() {
        val raster = BrushRaster(1000, 1000)
        raster.sync(listOf(square(0.25f, 0.25f, 0.75f, 0.75f)))
        assertEquals(1f, raster.valueAt(0.5f, 0.5f), 0f)
        assertEquals(1f, raster.valueAt(0.26f, 0.74f), 0f)
        assertEquals(0f, raster.valueAt(0.2f, 0.5f), 0f)
        assertEquals(0f, raster.valueAt(0.5f, 0.8f), 0f)
    }

    @Test
    fun lassoEdgeIsSmooth() {
        val raster = BrushRaster(1024, 1024)
        // The edge falls in the middle of a texel: half covered.
        val x = 100.5f / 1024
        raster.sync(listOf(square(x, 0.2f, 0.8f, 0.8f)))
        assertEquals(0.5f, raster.valueAt(100.5f / 1024, 0.5f), 0.01f)
    }

    @Test
    fun lassoErasesFromTheSubject() {
        val raster = BrushRaster(1000, 500)
        val base = ByteArray(raster.width * raster.height) { -1 }
        raster.sync(listOf(square(0.1f, 0.1f, 0.3f, 0.3f, erase = true)), base)
        assertEquals(0f, raster.valueAt(0.2f, 0.2f), 0f)
        assertEquals(1f, raster.valueAt(0.6f, 0.6f), 0f)
    }

    @Test
    fun anyShapeIsFilled() {
        // A triangle, traced clockwise or not.
        val raster = BrushRaster(1000, 1000)
        val triangle = BrushStroke(listOf(0.5f, 0.1f, 0.1f, 0.9f, 0.9f, 0.9f), radius = 0f, fill = true)
        raster.sync(listOf(triangle))
        assertEquals(1f, raster.valueAt(0.5f, 0.6f), 0f)
        assertEquals(0f, raster.valueAt(0.15f, 0.2f), 0f)
        assertEquals(0f, raster.valueAt(0.85f, 0.2f), 0f)
    }

    @Test
    fun paintAfterALassoAddsToIt() {
        val raster = BrushRaster(1000, 1000)
        val lasso = square(0.1f, 0.1f, 0.3f, 0.3f)
        raster.sync(listOf(lasso))
        val dab = BrushStroke(listOf(0.7f, 0.7f), radius = 0.05f, hardness = 0.9f)
        assertTrue(raster.sync(listOf(lasso, dab)))
        assertEquals(1f, raster.valueAt(0.2f, 0.2f), 0f)
        assertEquals(1f, raster.valueAt(0.7f, 0.7f), 0f)
    }

    @Test
    fun lassoSurvivesTheRecipe() {
        val local = LocalAdjustment(mask = Mask(MaskType.BRUSH, strokes = listOf(square(0.1f, 0.1f, 0.3f, 0.3f))))
        val edit = EditState(masks = listOf(local))
        assertEquals(edit, EditState.fromJson(edit.toJson()))
    }
}
