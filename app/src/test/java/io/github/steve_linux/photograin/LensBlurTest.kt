// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.LocalParams
import io.github.steve_linux.photograin.model.Mask
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.render.ShaderParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LensBlurTest {
    @Test
    fun backgroundBlurDefocusesEverythingButTheSubject() {
        val local = LocalAdjustment.backgroundBlur()
        assertEquals(MaskType.SUBJECT, local.mask.type)
        assertTrue(local.mask.invert)
        assertEquals(LocalAdjustment.BACKGROUND_BLUR, local.blur, 0f)
        assertTrue(local.isBackgroundBlur)
        assertTrue(local.onlyBlurs)
    }

    @Test
    fun onlyBlursIgnoresNameAndSwitchButNotOtherSliders() {
        val local = LocalAdjustment.backgroundBlur()
        assertTrue(local.copy(name = "Bokeh", enabled = false).onlyBlurs)
        assertFalse(local.copy(exposure = 0.3f).onlyBlurs)
        assertFalse(local.copy(blur = 0f).onlyBlurs)
        // A subject mask that is not inverted blurs the subject, not the background.
        assertFalse(local.copy(mask = Mask(MaskType.SUBJECT)).isBackgroundBlur)
    }

    @Test
    fun blurIsALocalSliderFromZeroToOne() {
        val spec = LocalParams.all.single { it.id == "blur" }
        assertEquals(0f, spec.min, 0f)
        assertEquals(1f, spec.max, 0f)
        assertEquals(0.7f, spec.get(spec.set(LocalAdjustment(), 0.7f)), 0f)
        assertEquals(0f, spec.get(LocalAdjustment()), 0f)
    }

    @Test
    fun recipesKeepTheBlurAndOldOnesOpenSharp() {
        val edit = EditState(masks = listOf(LocalAdjustment.backgroundBlur().copy(blur = 0.8f)))
        assertEquals(edit, EditState.fromJson(edit.toJson()))
        // Recipes are pretty-printed: drop the key with its comma and spacing.
        val old = edit.toJson().replace(Regex(",?\\s*\"blur\":\\s*[0-9.]+"), "")
        assertFalse(old.contains("\"blur\""))
        assertEquals(0f, EditState.fromJson(old)!!.masks.single().blur, 0f)
    }

    @Test
    fun shaderGetsEachMaskBlurAndSkipsDisabledOnes() {
        val masks = listOf(
            LocalAdjustment(Mask(MaskType.RADIAL), exposure = 0.5f),
            LocalAdjustment.backgroundBlur().copy(blur = 0.6f),
            LocalAdjustment(Mask(MaskType.LINEAR), blur = 0.9f, enabled = false),
        )
        val mu = ShaderParams.maskUniforms(masks)
        assertEquals(0f, mu.blur[0], 0f)
        assertEquals(0.6f, mu.blur[1], 0f)
        assertEquals(0f, mu.blur[2], 0f)
        assertTrue(mu.lensBlur)
        assertFalse(ShaderParams.maskUniforms(masks.take(1)).lensBlur)
        assertEquals(0.6f, ShaderParams.maxLensBlur(masks), 0f)
    }

    @Test
    fun blurRadiusScalesWithTheOutput() {
        // The same look at any size: a fraction of the long edge.
        val small = ShaderParams.lensBlurRadiusPx(1f, 1000)
        val large = ShaderParams.lensBlurRadiusPx(1f, 4000)
        assertEquals(4.0, large / small, 1e-9)
        assertEquals(ShaderParams.LENS_BLUR_FRACTION * 2000, ShaderParams.lensBlurRadiusPx(1f, 2000), 1e-9)
        assertEquals(0.0, ShaderParams.lensBlurRadiusPx(-1f, 2000), 0.0)
    }

    @Test
    fun exportTilesReachAsFarAsTheBlur() {
        val long = 6000
        val plain = ShaderParams.tileMarginPx(long)
        assertEquals(plain, ShaderParams.tileMarginPx(long, emptyList()))
        assertEquals(plain, ShaderParams.tileMarginPx(long, listOf(LocalAdjustment(exposure = 1f))))
        val blurred = ShaderParams.tileMarginPx(long, listOf(LocalAdjustment.backgroundBlur().copy(blur = 1f)))
        assertTrue(blurred >= plain + ShaderParams.lensBlurRadiusPx(1f, long))
    }
}
