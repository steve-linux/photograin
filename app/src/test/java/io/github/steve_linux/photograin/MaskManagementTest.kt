// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.*
import io.github.steve_linux.photograin.render.ShaderParams
import org.junit.Assert.*
import org.junit.Test

class MaskManagementTest {
    @Test fun disabledMaskZerosAllAdjustmentsWithoutChangingIndicesOrGeometry() {
        val active = LocalAdjustment(mask = Mask(MaskType.SKY), exposure = 1f, contrast = .2f,
            highlights = .3f, shadows = .4f, temperature = .5f, tint = .6f, saturation = .7f,
            clarity = .8f, texture = .9f, dehaze = 1f)
        val masks = listOf(active.copy(enabled = false), active)
        val u = ShaderParams.maskUniforms(masks)
        assertEquals(2, u.count)
        assertTrue(u.locA.take(4).all { it == 0f })
        assertTrue(u.locB.take(4).all { it == 0f })
        assertTrue(u.effects.take(2).all { it == 0f })
        assertEquals(1f, u.locA[4], 0f)
        assertEquals(.9f, u.effects[2], 0f)
        assertEquals(0f, u.opt[1], 0f)
        assertEquals(1f, u.opt[5], 0f)
        val restored = ShaderParams.maskUniforms(listOf(masks[0].copy(enabled = true)))
        assertEquals(active.exposure, restored.locA[0], 0f)
        assertEquals(active.clarity, restored.locB[3], 0f)
    }

    @Test fun disabledCutoutDoesNotRemoveBackground() {
        val cutout = LocalAdjustment(background = Background.TRANSPARENT, enabled = false)
        assertEquals(-1, LocalAdjustment.cutoutIndex(listOf(cutout)))
        assertEquals(1, LocalAdjustment.cutoutIndex(listOf(cutout, cutout.copy(enabled = true))))
    }

    @Test fun namesAndDisabledStateSurviveRecipesAndDuplicate() {
        val m = LocalAdjustment(name = "Cielo al tramonto", enabled = false, exposure = -.7f)
        val edit = EditState(masks = listOf(m)).duplicateMask(0)
        assertEquals(edit, EditState.fromJson(edit.toJson()))
        assertEquals(m.name, edit.masks[1].name)
        assertFalse(edit.masks[1].enabled)
    }

    @Test fun legacyRecipeStartsEnabledWithAutomaticName() {
        val edit = EditState.fromJson("""{"version":1,"edit":{"masks":[{"exposure":0.5}]}}""")!!
        assertTrue(edit.masks[0].enabled)
        assertEquals("", edit.masks[0].name)
    }

    @Test fun reorderingPreservesSelectionsAndChangesOrderedResult() {
        val shape = Mask(MaskType.FULL)
        val mask = Mask(MaskType.BRUSH, parts = listOf(
            MaskPart(shape, MaskOperation.ADD), MaskPart(shape, MaskOperation.SUBTRACT)))
        val r = CompoundRaster(100, 100)
        r.sync(mask, null)
        assertTrue(r.data.all { it == 0.toByte() })
        val moved = mask.movePart(1, 0)
        assertEquals(mask.parts[1], moved.parts[0])
        assertEquals(mask.parts[0], moved.parts[1])
        assertEquals(mask.type, moved.type)
        assertTrue(r.sync(moved, null))
        assertTrue(r.data.all { it == 255.toByte() })
        assertEquals(mask, moved.movePart(0, 1))
    }

    @Test fun invalidReorderIsNoOpAndHistoryRestoresPreviousOrder() {
        val mask = Mask(parts = listOf(MaskPart(Mask(MaskType.SKY)), MaskPart(Mask(MaskType.SUBJECT))))
        assertSame(mask, mask.movePart(-1, 0))
        assertSame(mask, mask.movePart(0, 2))
        assertSame(mask, mask.movePart(0, 0))
        val h = History(mask)
        val moved = mask.movePart(0, 1)
        h.commit(moved)
        assertEquals(mask, h.undo())
        assertEquals(moved, h.redo())
    }
}
