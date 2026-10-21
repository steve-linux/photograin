// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.BrushStroke
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Removal
import io.github.steve_linux.photograin.model.RetouchSpot
import io.github.steve_linux.photograin.model.RetouchType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovalTest {
    private val dot = BrushStroke(listOf(0.5f, 0.5f), radius = 0.05f, hardness = Removal.HARDNESS)

    @Test
    fun cropIsASquareAroundTheStrokes() {
        // 4000 x 3000: the dot covers 400 pixels, with half as much on each side.
        val crop = Removal.crop(listOf(dot), 4000, 3000)!!
        assertEquals(800.0, (crop[2] - crop[0]).toDouble(), 2.0)
        assertEquals(crop[2] - crop[0], crop[3] - crop[1])
        assertEquals(2000.0, (crop[0] + crop[2]) / 2.0, 1.0)
        assertEquals(1500.0, (crop[1] + crop[3]) / 2.0, 1.0)
        assertNull(Removal.crop(emptyList(), 4000, 3000))
    }

    @Test
    fun cropStaysInsideThePhoto() {
        val corner = BrushStroke(listOf(0.01f, 0.99f), radius = 0.05f)
        val crop = Removal.crop(listOf(corner), 4000, 3000)!!
        assertEquals(0, crop[0])
        assertEquals(3000, crop[3])
        assertEquals(crop[2] - crop[0], crop[3] - crop[1])
        // A stroke as large as the photo sees the whole photo.
        val huge = BrushStroke(listOf(0.1f, 0.5f, 0.9f, 0.5f), radius = 0.3f)
        assertArrayEquals(intArrayOf(0, 0, 4000, 3000), Removal.crop(listOf(huge), 4000, 3000))
    }

    @Test
    fun smallObjectsKeepSomeContext() {
        val speck = BrushStroke(listOf(0.5f, 0.5f), radius = 0.001f)
        val crop = Removal.crop(listOf(speck), 4000, 3000)!!
        // 4% of the long edge on each side.
        assertTrue(crop[2] - crop[0] >= 2 * 160)
    }

    @Test
    fun cropIsTheSameFractionAtAnySize() {
        val stroke = BrushStroke(listOf(0.3f, 0.4f, 0.35f, 0.45f), radius = 0.02f)
        val big = Removal.crop(listOf(stroke), 4000, 3000)!!
        val small = Removal.crop(listOf(stroke), 1000, 750)!!
        for (i in 0..3) assertEquals(big[i] / 4.0, small[i].toDouble(), 1.0)
    }

    @Test
    fun modelSeesTheCropAtItsSize() {
        assertEquals(512, Removal.modelSize(1200, 1200).width)
        val wide = Removal.modelSize(1000, 500)
        assertEquals(512, wide.width)
        assertEquals(256, wide.height)
        // Small crops are enlarged.
        assertEquals(512, Removal.modelSize(100, 100).height)
    }

    @Test
    fun rasterCoversTheStrokes() {
        val crop = Removal.crop(listOf(dot), 4000, 3000)!!
        val soft = Removal.raster(listOf(dot), crop, 4000, 3000, 120, 120)
        // The dot is half the crop, in its middle.
        assertEquals(255, soft[60 * 120 + 60].toInt() and 0xFF)
        assertEquals(0, soft[5 * 120 + 5].toInt() and 0xFF)
        // Soft at the edge of the brush, fully on when grown for the model.
        val edge = 60 * 120 + 60 + 19
        assertTrue((soft[edge].toInt() and 0xFF) in 1..254)
        val holes = Removal.raster(listOf(dot), crop, 4000, 3000, 120, 120, Removal.GROW)
        assertEquals(255, holes[edge].toInt() and 0xFF)
        assertTrue((holes[60 * 120 + 60 + 22].toInt() and 0xFF) > 0)
    }

    @Test
    fun paddingMirrorsTheImage() {
        // A 3 x 2 image: each pixel's red is its index.
        val rgba = ByteArray(3 * 2 * 4) { if (it % 4 == 0) (it / 4).toByte() else 0 }
        val out = Removal.padded(rgba, 3, 2)
        val n = Removal.MODEL_SIZE
        fun red(x: Int, y: Int) = out[(y * n + x) * 4].toInt()
        assertEquals(2, red(2, 0))
        assertEquals(1, red(3, 0))
        assertEquals(0, red(4, 0))
        assertEquals(3, red(0, 1))
        assertEquals(0, red(0, 2))
        assertEquals(5, red(2, 3))
    }

    @Test
    fun circleSurroundsTheStrokes() {
        val (x, y, r) = Removal.circle(listOf(dot), 4000, 3000)!!
        assertEquals(0.5f, x, 1e-6f)
        assertEquals(0.5f, y, 1e-6f)
        assertEquals(0.05f, r, 1e-6f)
    }

    @Test
    fun removalsSurviveTheRecipe() {
        val spot = RetouchSpot(RetouchType.REMOVE, 0.5f, 0.5f, 0.5f, 0.5f, 0.05f, strokes = listOf(dot))
        val edit = EditState(retouch = listOf(spot))
        assertEquals(edit, EditState.fromJson(edit.toJson()))
    }
}
