// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.math.Homography
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.Mask
import io.github.steve_linux.photograin.model.MaskRange
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.model.RangeType
import io.github.steve_linux.photograin.model.WhiteBalance
import io.github.steve_linux.photograin.render.ShaderParams
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class MaskRangeTest {
    @Test
    fun noRangeCoversEverything() {
        val r = MaskRange()
        assertEquals(1f, r.weight(0f, 0f, 0f), 0f)
        assertEquals(1f, r.weight(0.3f, 0.8f, 0.1f), 0f)
    }

    @Test
    fun luminanceRangeKeepsTheBrightPartOnly() {
        val r = MaskRange(RangeType.LUMINANCE, low = 0.6f, high = 1f, smooth = 0.2f)
        assertEquals(1f, r.weight(0.9f, 0.9f, 0.9f), 1e-4f)
        assertEquals(0f, r.weight(0.2f, 0.2f, 0.2f), 1e-4f)
        // Halfway through the fade at the low edge.
        assertEquals(0.5f, r.weight(0.6f, 0.6f, 0.6f), 1e-3f)
    }

    @Test
    fun colorRangePicksSimilarHuesAndSkipsGrays() {
        val sky = MaskRange.hueOf(0.3f, 0.5f, 0.9f)
        val r = MaskRange(RangeType.COLOR, hue = sky, tolerance = 0.4f)
        assertEquals(1f, r.weight(0.35f, 0.55f, 0.95f), 1e-4f)
        assertEquals(0f, r.weight(0.9f, 0.4f, 0.2f), 1e-4f)
        assertEquals(0f, r.weight(0.5f, 0.5f, 0.5f), 1e-4f)
    }

    @Test
    fun hueWrapsAroundRed() {
        val r = MaskRange(RangeType.COLOR, hue = 0.99f, tolerance = 0.3f)
        assertEquals(1f, r.weight(0.9f, 0.1f, 0.12f), 1e-4f)
        assertEquals(1f, r.weight(0.9f, 0.12f, 0.1f), 1e-4f)
    }

    @Test
    fun wholePhotoMaskStartsOnTheHighlights() {
        val local = LocalAdjustment.create(MaskType.FULL, Homography(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0), 400, 300)
        assertEquals(MaskType.FULL, local.mask.type)
        assertEquals(RangeType.LUMINANCE, local.mask.range.type)
        assertEquals(1f, local.mask.weight(0.1f, 0.9f, 1f, 0.75f), 0f)
        val u = ShaderParams.maskUniforms(listOf(local))
        assertEquals(3f, u.opt[0], 0f)
        assertEquals(1f, u.range[0], 0f)
    }

    @Test
    fun oldRecipesReadWithoutARange() {
        val json = """{"type":"RADIAL","x0":0.4}"""
        val m = Json { ignoreUnknownKeys = true }.decodeFromString(Mask.serializer(), json)
        assertEquals(RangeType.NONE, m.range.type)
    }

    @Test
    fun eyedropperNeutralizesAWarmGray() {
        fun enc(v: Double) = if (v <= 0.0031308) (v * 12.92).toFloat() else (1.055 * v.pow(1 / 2.4) - 0.055).toFloat()
        // A gray lit warm: more red, less blue, in linear light.
        val picked = WhiteBalance.neutralize(enc(0.30), enc(0.24), enc(0.18))
        assertNotNull(picked)
        val (t, n) = picked!!
        assertTrue(t < 0)
        val g = ShaderParams.whiteBalanceGains(Adjustments(temperature = t, tint = n))
        val r = 0.30 * g[0]
        val gg = 0.24 * g[1]
        val b = 0.18 * g[2]
        assertEquals(r, b, 1e-4)
        assertEquals(r, gg, 1e-4)
        assertNull(WhiteBalance.neutralize(0f, 0f, 0f))
    }
}
