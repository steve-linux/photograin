// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.AspectRatio
import io.github.steve_linux.photograin.model.CropDrag
import io.github.steve_linux.photograin.model.CropHandle
import io.github.steve_linux.photograin.model.CropRect
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Geometry
import io.github.steve_linux.photograin.model.History
import io.github.steve_linux.photograin.model.Params
import io.github.steve_linux.photograin.render.ShaderParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test
    fun historyUndoRedo() {
        val h = History(0)
        h.commit(1)
        h.commit(2)
        h.commit(2) // no-op
        assertEquals(1, h.undo())
        assertEquals(0, h.undo())
        assertFalse(h.canUndo)
        assertEquals(1, h.redo())
        h.commit(5)
        assertFalse(h.canRedo)
        assertEquals(1, h.undo())
    }

    @Test
    fun recipeRoundTripsThroughJson() {
        val state = EditState(
            Adjustments(exposure = 0.7f, clarity = -0.3f, grain = 0.4f),
            Geometry(quarterTurns = 3, flipH = true, straighten = -2.5f, crop = CropRect(0.1f, 0.1f, 0.9f, 0.8f), aspect = AspectRatio.R3_2),
        )
        assertEquals(state, EditState.fromJson(state.toJson()))
    }

    @Test
    fun recipeToleratesUnknownFieldsAndGarbage() {
        val json = """{"version":1,"edit":{"adjustments":{"exposure":1.0,"futureKnob":3}},"extra":true}"""
        assertEquals(1f, EditState.fromJson(json)!!.adjustments.exposure)
        assertNull(EditState.fromJson("not json"))
    }

    @Test
    fun everyParamDefaultsToNeutral() {
        for (p in Params.all) {
            assertTrue("${p.id} default in range", p.default in p.min..p.max)
            val changed = p.set(Adjustments(), p.max)
            assertEquals(p.max, p.get(changed))
        }
    }

    @Test
    fun neutralWhiteBalanceIsUnity() {
        val g = ShaderParams.whiteBalanceGains(Adjustments())
        for (v in g) assertEquals(1f, v, 1e-6f)
        val warm = ShaderParams.whiteBalanceGains(Adjustments(temperature = 1f))
        assertTrue(warm[0] > warm[2])
        assertEquals(1.0, 0.2126 * warm[0] + 0.7152 * warm[1] + 0.0722 * warm[2], 1e-5)
    }

    @Test
    fun blurDownsampleKeepsSigmaSmall() {
        for (long in listOf(500, 2560, 6000, 12000)) {
            val sigma = ShaderParams.localSigmaPx(long)
            val d = ShaderParams.blurDownsample(sigma)
            assertTrue(sigma / d <= ShaderParams.BLUR_SIGMA_TEXELS)
            // Kernel half-width must cover ~3 sigma.
            assertTrue(ShaderParams.BLUR_TAPS >= 2.9 * sigma / d)
        }
    }

    @Test
    fun cropHitTestFindsCornersEdgesAndInside() {
        val c = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        assertEquals(CropHandle.TOP_LEFT, CropDrag.hitTest(c, 0.21f, 0.19f, 0.03f, 0.03f))
        assertEquals(CropHandle.RIGHT, CropDrag.hitTest(c, 0.8f, 0.5f, 0.03f, 0.03f))
        assertEquals(CropHandle.MOVE, CropDrag.hitTest(c, 0.5f, 0.5f, 0.03f, 0.03f))
        assertNull(CropDrag.hitTest(c, 0.05f, 0.05f, 0.03f, 0.03f))
    }

    @Test
    fun lockedCornerDragKeepsRatioAndAnchor() {
        val start = CropRect(0.2f, 0.2f, 0.6f, 0.6f)
        val r = CropDrag.drag(start, CropHandle.BOTTOM_RIGHT, 0.1f, 0.02f, ratio = 1f)
        assertEquals(0.2f, r.left, 1e-6f)
        assertEquals(0.2f, r.top, 1e-6f)
        assertEquals(r.width, r.height, 1e-6f)
        assertEquals(0.5f, r.width, 1e-5f)
    }

    @Test
    fun lockedDragStaysInsideFrame() {
        val start = CropRect(0.2f, 0.2f, 0.6f, 0.6f)
        val r = CropDrag.drag(start, CropHandle.BOTTOM_RIGHT, 5f, 5f, ratio = 2f)
        assertTrue(r.right <= 1f + 1e-6f && r.bottom <= 1f + 1e-6f)
        assertEquals(2f, r.width / r.height, 1e-4f)
    }

    @Test
    fun moveIsClampedToFrame() {
        val r = CropDrag.drag(CropRect(0.2f, 0.2f, 0.6f, 0.6f), CropHandle.MOVE, 1f, -1f, null)
        assertEquals(0.6f, r.left, 1e-6f)
        assertEquals(0.4f, r.bottom, 1e-6f)
        assertEquals(1f, r.right, 1e-6f)
        assertEquals(0f, r.top, 1e-6f)
    }
}
