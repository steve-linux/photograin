// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.Background
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.BrushRaster
import io.github.steve_linux.photograin.model.BrushStroke
import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.Mask
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.model.SubjectMask
import io.github.steve_linux.photograin.render.ShaderParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectMaskTest {
    private val n = SubjectMask.MODEL_SIZE

    @Test
    fun uprightMatchesTheFileOrientation() {
        // A clockwise turn brings the stored bottom-left corner to the top-left.
        assertEquals((n - 1) * n, SubjectMask.uprightToStored(BaseOrientation(quarterTurns = 1))[0])
        // Mirroring swaps left and right.
        assertEquals(n - 1, SubjectMask.uprightToStored(BaseOrientation(mirror = true))[0])
        assertEquals(n * n - 1, SubjectMask.uprightToStored(BaseOrientation(quarterTurns = 2))[0])
        val identity = SubjectMask.uprightToStored(BaseOrientation())
        assertTrue(identity.indices.all { identity[it] == it })
    }

    @Test
    fun inputAveragesThePixelsItCovers() {
        // 640 x 320: each input pixel covers two, one black and one white.
        val w = 2 * n
        val pixels = IntArray(w * n) { if (it % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val input = SubjectMask.modelInput(pixels, w, n, BaseOrientation())
        assertEquals(n * n, input.size)
        assertEquals(0xFF808080.toInt(), input[0])
    }

    @Test
    fun maskLandsWhereTheSubjectIsStored() {
        // A bright square in the stored top-left of a photo shown turned and mirrored.
        val w = 400
        val h = 300
        val pixels = IntArray(w * h) { i -> if (i % w < 100 && i / w < 100) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        for (base in listOf(BaseOrientation(), BaseOrientation(quarterTurns = 1), BaseOrientation(true, 3))) {
            // A stand-in model that calls whatever is bright the subject.
            val raw = SubjectMask.modelInput(pixels, w, h, base).map { (it and 0xFF).toByte() }.toByteArray()
            val grid = BrushRaster.size(w, h)
            val mask = SubjectMask.fromModel(raw, base, grid.width, grid.height)
            assertEquals(255, mask.data[(grid.height / 10) * grid.width + grid.width / 10].toInt() and 0xFF)
            assertEquals(0, mask.data[(grid.height * 9 / 10) * grid.width + grid.width * 9 / 10].toInt() and 0xFF)
        }
    }

    @Test
    fun unsureValuesBecomeSubjectOrBackground() {
        assertEquals(0f, SubjectMask.curve(SubjectMask.BACKGROUND), 0f)
        assertEquals(1f, SubjectMask.curve(SubjectMask.SUBJECT), 0f)
        assertEquals(0.5f, SubjectMask.curve((SubjectMask.BACKGROUND + SubjectMask.SUBJECT) / 2), 1e-6f)
    }

    @Test
    fun resizingKeepsTheShape() {
        val mask = SubjectMask(4, 2, byteArrayOf(0, 0, -1, -1, 0, 0, -1, -1))
        val big = mask.resizedTo(8, 4)
        assertEquals(0, big.data[0].toInt() and 0xFF)
        assertEquals(255, big.data[7].toInt() and 0xFF)
        assertTrue(big === mask.resizedTo(8, 4))
        assertTrue(mask === mask.resizedTo(4, 2))
    }

    @Test
    fun strokesRefineTheSubject() {
        val raster = BrushRaster(1000, 500)
        val base = ByteArray(raster.width * raster.height) { i -> if (i % raster.width < raster.width / 2) -1 else 0 }
        val erase = BrushStroke(listOf(0.25f, 0.5f), radius = 0.05f, hardness = 0.9f, erase = true)
        val paint = BrushStroke(listOf(0.75f, 0.5f), radius = 0.05f, hardness = 0.9f)
        assertTrue(raster.sync(emptyList(), base))
        assertEquals(1f, raster.valueAt(0.25f, 0.5f), 0f)
        raster.sync(listOf(erase, paint), base)
        assertEquals(0f, raster.valueAt(0.25f, 0.5f), 0f)
        assertEquals(1f, raster.valueAt(0.75f, 0.5f), 0f)
        assertEquals(1f, raster.valueAt(0.1f, 0.1f), 0f)
        // Without the subject only the paint is left.
        raster.sync(listOf(erase, paint), null)
        assertEquals(0f, raster.valueAt(0.1f, 0.1f), 0f)
        assertEquals(1f, raster.valueAt(0.75f, 0.5f), 0f)
    }

    @Test
    fun subjectMaskReadsItsRasterLayer() {
        val masks = listOf(LocalAdjustment(), LocalAdjustment(mask = Mask(MaskType.SUBJECT)))
        val u = ShaderParams.maskUniforms(masks)
        assertEquals(2f, u.opt[4], 0f)
        assertEquals(1f, u.opt[5], 0f)
    }

    @Test
    fun onlyTheFirstCutoutCounts() {
        val keep = LocalAdjustment()
        val cut = LocalAdjustment(background = Background.TRANSPARENT)
        assertEquals(-1, LocalAdjustment.cutoutIndex(listOf(keep)))
        assertEquals(1, LocalAdjustment.cutoutIndex(listOf(keep, cut, cut)))
    }

    @Test
    fun backgroundColorIsInTheWorkingSpace() {
        val transparent = LocalAdjustment(background = Background.TRANSPARENT)
        assertArrayEquals(FloatArray(4), ShaderParams.cutoutFill(transparent, ColorProfile.SRGB), 0f)
        val red = LocalAdjustment(background = Background.COLOR, backgroundColor = 0xFFFF0000.toInt())
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 1f), ShaderParams.cutoutFill(red, ColorProfile.SRGB), 1e-5f)
        // White stays white in any space; sRGB red is less saturated in Display P3.
        val white = LocalAdjustment(background = Background.COLOR)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f, 1f), ShaderParams.cutoutFill(white, ColorProfile.DISPLAY_P3), 1e-3f)
        val p3 = ShaderParams.cutoutFill(red, ColorProfile.DISPLAY_P3)
        assertTrue(p3[0] < 1f && p3[1] > 0.1f)
    }
}
