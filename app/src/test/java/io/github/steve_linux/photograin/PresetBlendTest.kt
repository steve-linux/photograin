// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.ColorGrading
import io.github.steve_linux.photograin.model.CurvePoint
import io.github.steve_linux.photograin.model.Curves
import io.github.steve_linux.photograin.model.GradingTone
import io.github.steve_linux.photograin.model.Hsl
import io.github.steve_linux.photograin.model.HslBand
import io.github.steve_linux.photograin.model.Look
import io.github.steve_linux.photograin.model.Preset
import io.github.steve_linux.photograin.model.PresetBlend
import io.github.steve_linux.photograin.model.PresetPart
import io.github.steve_linux.photograin.model.ToneCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetBlendTest {
    @Test
    fun interpolatesFromCurrentValuesAndKeepsUnselectedSettings() {
        val before = Adjustments(exposure = 0.5f, saturation = 0.7f, sharpness = 0.4f)
        val preset = Preset.capture("Light", Adjustments(exposure = -1.5f), setOf(PresetPart.LIGHT))
        val full = preset.applyTo(before)
        val blend = PresetBlend(before, full)
        assertSame(before, blend.at(0f))
        assertSame(full, blend.at(1f))
        assertEquals(before.copy(exposure = -0.5f), blend.at(0.5f))
        assertEquals(before.copy(exposure = 0f), blend.at(0.25f))
    }

    @Test
    fun blendsCurveAndColorMixWithoutChangingOtherChannels() {
        val custom = ToneCurve(listOf(CurvePoint(0f, 0f), CurvePoint(1f, 0.8f)))
        val before = Adjustments(curves = Curves(blue = custom))
        val target = before.copy(
            curves = before.curves.copy(red = custom),
            hsl = Hsl().withSaturation(HslBand.RED, 0.8f),
        )
        val result = PresetBlend(before, target).at(0.5f)
        assertSame(custom, result.curves.blue)
        assertEquals(0.9f, result.curves.red.points.last().y, 1e-5f)
        assertEquals(0.4f, result.hsl.saturation[HslBand.RED.ordinal], 1e-5f)
    }

    @Test
    fun blendsGradingHueAcrossZeroAndFadesASingleLut() {
        val start = Adjustments(grading = ColorGrading(global = GradingTone(350f, 0.5f)))
        val target = Adjustments(grading = ColorGrading(global = GradingTone(10f, 0.5f)), look = Look(lutId = "one", amount = 0.8f))
        val blend = PresetBlend(start, target)
        assertTrue(blend.canBlend)
        val half = blend.at(0.5f)
        assertEquals(0f, half.grading.global.hue, 1e-5f)
        assertEquals("one", half.look.lutId)
        assertEquals(0.4f, half.look.amount, 1e-5f)
        assertFalse(PresetBlend(target, target.copy(look = Look(lutId = "two"))).canBlend)
        assertFalse(PresetBlend(start, start.copy(colorKey = 0xffffff)).canBlend)
    }
}
