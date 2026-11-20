// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.*
import io.github.steve_linux.photograin.render.ShaderParams
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CompoundMaskTest {
    private val left = Mask(MaskType.RADIAL, x0 = .25f, y0 = .5f, radiusX = .2f, radiusY = .2f, feather = 0f)
    private val right = left.copy(x0 = .75f)
    private fun at(r: CompoundRaster, x: Float, y: Float) = r.data[(y * r.height).toInt() * r.width + (x * r.width).toInt()].toInt() and 255

    @Test fun unionKeepsTwoSeparatedSelections() {
        val r = CompoundRaster(100, 100)
        r.sync(left.copy(parts = listOf(MaskPart(right))), null)
        assertEquals(255, at(r, .25f, .5f)); assertEquals(255, at(r, .75f, .5f))
        assertEquals(0, at(r, .5f, .1f))
    }
    @Test fun subtractionRemovesOnlyOverlapAndOrderingAllowsAddingBack() {
        val r = CompoundRaster(100, 100)
        val mask = Mask(MaskType.FULL, parts = listOf(MaskPart(left, MaskOperation.SUBTRACT)))
        r.sync(mask, null)
        assertEquals(0, at(r, .25f, .5f)); assertEquals(255, at(r, .75f, .5f))
        r.sync(mask.copy(parts = mask.parts + MaskPart(left)), null)
        assertEquals(255, at(r, .25f, .5f))
    }
    @Test fun compoundUsesSingleShaderSlotAndWholeResultInvertRange() {
        val m = left.copy(invert = true, range = MaskRange(RangeType.LUMINANCE), parts = listOf(MaskPart(right)))
        val r = CompoundRaster(100, 100)
        assertTrue(r.sync(m, null))
        val data = r.data.copyOf()
        assertFalse(r.sync(m.copy(invert = false, range = MaskRange()), null))
        assertArrayEquals(data, r.data)
        val u = ShaderParams.maskUniforms(listOf(LocalAdjustment(mask = m)))
        assertEquals(2f, u.opt[0], 0f)
        assertEquals(1f, u.opt[3], 0f)
    }
    @Test fun skyAndSubjectComponentsUseIndependentBasesAndRefresh() {
        val r = CompoundRaster(100, 100)
        val sky = SubjectMask(r.width, r.height, ByteArray(r.width * r.height) { 255.toByte() })
        val subject = SubjectMask(r.width, r.height, ByteArray(r.width * r.height) { if (it % r.width < r.width / 2) 255.toByte() else 0 })
        val m = Mask(MaskType.SKY, parts = listOf(MaskPart(Mask(MaskType.SUBJECT), MaskOperation.SUBTRACT)))
        r.sync(m, Subjects(main = subject, sky = sky))
        assertEquals(0, at(r, .25f, .5f)); assertEquals(255, at(r, .75f, .5f))
        assertFalse(r.sync(m, Subjects(main = subject, sky = sky)))
        assertTrue(r.sync(m, Subjects(main = sky, sky = sky)))
        assertTrue(r.data.all { it == 0.toByte() })
    }
    @Test fun brushComponentAndUndoRestoreRasterWithoutStalePixels() {
        val r = CompoundRaster(100, 100)
        val brush = Mask(MaskType.BRUSH, strokes = listOf(BrushStroke(listOf(.25f, .5f), .1f, 1f)))
        r.sync(brush, null)
        val original = r.data.copyOf()
        r.sync(brush.copy(parts = listOf(MaskPart(Mask(MaskType.FULL)))), null)
        assertEquals(255, at(r, .75f, .5f))
        r.sync(brush, null)
        assertArrayEquals(original, r.data)
    }
    @Test fun legacyRecipesAndCompoundRecipesRoundTrip() {
        val old = Json.decodeFromString<Mask>("""{"type":"LINEAR","x0":0.3}""")
        assertTrue(old.parts.isEmpty()); assertFalse(old.usesRaster)
        val m = old.copy(parts = listOf(MaskPart(right, MaskOperation.SUBTRACT), MaskPart(Mask(MaskType.SKY))))
        assertEquals(m, Json.decodeFromString<Mask>(Json.encodeToString(m)))
    }
    @Test fun editingComponentPreservesSiblingsAndWholeMaskProperties() {
        val m = left.copy(invert = true, range = MaskRange(RangeType.COLOR), parts = listOf(MaskPart(right)))
        val edited = m.withSelection(1, m.selection(1).copy(x0 = .6f))
        assertEquals(left.x0, edited.x0, 0f)
        assertEquals(.6f, edited.parts[0].mask.x0, 0f)
        assertTrue(edited.invert); assertEquals(m.range, edited.range)
        assertFalse(edited.parts[0].mask.invert)
        assertEquals(RangeType.NONE, edited.parts[0].mask.range.type)
        assertEquals(edited.parts, edited.withSelection(0, edited.selection(0).copy(x0 = .1f)).parts)
        assertEquals(2, edited.selections().size)
    }
    @Test fun identicalPreviewAndExportAspectHaveSameRaster() {
        val m = left.copy(parts = listOf(MaskPart(right, MaskOperation.SUBTRACT)))
        val preview = CompoundRaster(1200, 800); val export = CompoundRaster(6000, 4000)
        preview.sync(m, null); export.sync(m, null)
        assertArrayEquals(preview.data, export.data)
    }
    @Test fun movingAComponentInvalidatesCacheButNotAdjustingExposure() {
        val r = CompoundRaster(100, 100)
        val m = left.copy(parts = listOf(MaskPart(right)))
        assertTrue(r.sync(m, null)); assertFalse(r.sync(m.copy(), null))
        assertTrue(r.sync(m.withSelection(1, right.copy(x0 = .5f)), null))
        assertEquals(255, at(r, .5f, .5f))
    }
}
