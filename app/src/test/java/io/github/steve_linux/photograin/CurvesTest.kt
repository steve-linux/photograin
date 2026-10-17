// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.CurvePoint
import io.github.steve_linux.photograin.model.Curves
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Hsl
import io.github.steve_linux.photograin.model.HslBand
import io.github.steve_linux.photograin.model.ToneCurve
import io.github.steve_linux.photograin.render.ShaderParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurvesTest {
    @Test
    fun identityCurveSamplesAsIdentity() {
        val s = ToneCurve().sample(256)
        for (i in s.indices) assertEquals(i / 255f, s[i], 1e-5f)
        assertTrue(ToneCurve().isIdentity)
    }

    @Test
    fun curvePassesThroughPointsAndStaysMonotone() {
        val c = ToneCurve(listOf(CurvePoint(0f, 0f), CurvePoint(0.25f, 0.15f), CurvePoint(0.75f, 0.9f), CurvePoint(1f, 1f)))
        val s = c.sample(1001)
        assertEquals(0.15f, s[250], 1e-3f)
        assertEquals(0.9f, s[750], 1e-3f)
        for (i in 1 until s.size) assertTrue("monotone at $i", s[i] >= s[i - 1] - 1e-6f)
    }

    @Test
    fun flatSegmentDoesNotOvershoot() {
        val c = ToneCurve(listOf(CurvePoint(0f, 0f), CurvePoint(0.4f, 0.5f), CurvePoint(0.6f, 0.5f), CurvePoint(1f, 1f)))
        val s = c.sample(1001)
        for (i in 400..600) assertEquals(0.5f, s[i], 1e-4f)
    }

    @Test
    fun pointEditing() {
        var c = ToneCurve().withPoint(CurvePoint(0.5f, 0.7f))
        assertEquals(3, c.points.size)
        c = c.movePoint(1, CurvePoint(2f, 0.2f)) // x clamped below the last point
        assertTrue(c.points[1].x < 1f)
        assertEquals(0.2f, c.points[1].y, 1e-6f)
        assertEquals(c, c.removePoint(0)) // endpoints stay
        assertEquals(2, c.removePoint(1).points.size)
    }

    @Test
    fun lutComposesMasterThenChannel() {
        val invert = ToneCurve(listOf(CurvePoint(0f, 1f), CurvePoint(1f, 0f)))
        val lut = Curves(master = invert).toLut()
        assertEquals(255, lut[0].toInt() and 0xff)
        assertEquals(0, lut[255 * 4].toInt() and 0xff)
        val both = Curves(master = invert, red = invert).toLut()
        // Red is inverted twice, green once.
        assertEquals(0, both[0].toInt() and 0xff)
        assertEquals(255, both[1].toInt() and 0xff)
    }

    @Test
    fun hslEditingAndUniformPacking() {
        val hsl = Hsl().withHue(HslBand.BLUE, 0.5f).withLuminance(HslBand.ORANGE, -2f)
        assertFalse(hsl.isIdentity)
        val u = ShaderParams.hslUniform(Adjustments(hsl = hsl))
        assertEquals(24, u.size)
        assertEquals(0.5f, u[HslBand.BLUE.ordinal * 3], 0f)
        assertEquals(-1f, u[HslBand.ORANGE.ordinal * 3 + 2], 0f)
    }

    @Test
    fun advancedAdjustmentsSurviveRecipeRoundTrip() {
        val state = EditState(
            Adjustments(
                noiseReduction = 0.3f,
                curves = Curves(green = ToneCurve().withPoint(CurvePoint(0.3f, 0.4f))),
                hsl = Hsl().withSaturation(HslBand.AQUA, -0.6f),
            ),
        )
        assertEquals(state, EditState.fromJson(state.toJson()))
    }
}
