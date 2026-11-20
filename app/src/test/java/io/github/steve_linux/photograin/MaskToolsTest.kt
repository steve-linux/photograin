// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.*
import org.junit.Assert.*
import org.junit.Test

class MaskToolsTest {
    private fun values(r: CompoundRaster) = (0..3).map { band ->
        r.data[(r.height / 2) * r.width + (band * r.width / 4) + r.width / 8].toInt() and 255
    }
    private fun field(r: CompoundRaster, values: ByteArray) = SubjectMask(r.width, r.height,
        ByteArray(r.width * r.height) { values[(it % r.width) * 4 / r.width] })

    @Test fun intersectionKeepsOnlyCommonAreaIncludingSoftEdges() {
        val r = CompoundRaster(4, 1)
        val sky = field(r, byteArrayOf(255.toByte(), 200.toByte(), 128.toByte(), 0))
        val subject = field(r, byteArrayOf(0, 150.toByte(), 255.toByte(), 255.toByte()))
        val mask = Mask(MaskType.SKY, parts = listOf(MaskPart(Mask(MaskType.SUBJECT), MaskOperation.INTERSECT)))
        r.sync(mask, Subjects(main = subject, sky = sky))
        assertEquals(listOf(0, 150, 128, 0), values(r))
    }

    @Test fun intersectionWithEmptySelectionIsEmpty() {
        val r = CompoundRaster(4, 1)
        r.sync(Mask(MaskType.FULL, parts = listOf(MaskPart(Mask(MaskType.BRUSH), MaskOperation.INTERSECT))), null)
        assertEquals(listOf(0, 0, 0, 0), values(r))
    }

    @Test fun changingOperationInvalidatesCacheAndLaterAdditionCanRestorePixels() {
        val r = CompoundRaster(4, 1)
        val sky = field(r, byteArrayOf(255.toByte(), 0, 0, 0))
        val subject = field(r, byteArrayOf(0, 255.toByte(), 0, 0))
        val subjects = Subjects(main = subject, sky = sky)
        val mask = Mask(MaskType.SKY, parts = listOf(MaskPart(Mask(MaskType.SUBJECT))))
        r.sync(mask, subjects)
        assertEquals(listOf(255, 255, 0, 0), values(r))
        val intersect = mask.copy(parts = listOf(mask.parts[0].copy(operation = MaskOperation.INTERSECT)))
        assertTrue(r.sync(intersect, subjects))
        assertEquals(listOf(0, 0, 0, 0), values(r))
        assertFalse(r.sync(intersect, subjects))
        r.sync(intersect.copy(parts = intersect.parts + MaskPart(Mask(MaskType.SKY))), subjects)
        assertEquals(listOf(255, 0, 0, 0), values(r))
    }

    private fun original() = LocalAdjustment(
        mask = Mask(MaskType.SKY, invert = true, range = MaskRange(RangeType.LUMINANCE), parts = listOf(
            MaskPart(Mask(MaskType.BRUSH, strokes = listOf(BrushStroke(listOf(.2f, .3f), .1f))), MaskOperation.INTERSECT),
        )), exposure = .6f, texture = .3f, background = Background.TRANSPARENT,
    )

    @Test fun duplicateRetainsSelectionsAndAdjustmentsButDoesNotAddAnotherCutout() {
        val original = original()
        val edit = EditState(masks = listOf(original))
        val result = edit.duplicateMask(0)
        assertEquals(2, result.masks.size)
        assertEquals(original, result.masks[0])
        assertEquals(original.copy(background = Background.KEEP), result.masks[1])
        assertEquals(0, LocalAdjustment.cutoutIndex(result.masks))
        assertEquals(1, edit.masks.size)
        val changed = result.masks[1].copy(mask = result.masks[1].mask.withSelection(1, Mask(MaskType.RADIAL)))
        assertEquals(MaskType.BRUSH, result.masks[0].mask.parts[0].mask.type)
        assertEquals(MaskType.RADIAL, changed.mask.parts[0].mask.type)
    }

    @Test fun duplicateRejectsInvalidIndexAndFullMaskList() {
        val edit = EditState(masks = listOf(original()))
        assertSame(edit, edit.duplicateMask(-1))
        assertSame(edit, edit.duplicateMask(1))
        val full = edit.copy(masks = List(LocalAdjustment.MAX) { original() })
        assertSame(full, full.duplicateMask(0))
    }

    @Test fun duplicateCanBeUndoneAndRedoneWithoutChangingOriginal() {
        val edit = EditState(masks = listOf(original()))
        val history = History(edit)
        val duplicated = edit.duplicateMask(0)
        history.commit(duplicated)
        assertEquals(edit, history.undo())
        assertEquals(duplicated, history.redo())
    }

    @Test fun compoundIntersectionAndDuplicateSurviveRecipeRoundTrip() {
        val edit = EditState(masks = listOf(original())).duplicateMask(0)
        assertEquals(edit, EditState.fromJson(edit.toJson()))
    }
}
