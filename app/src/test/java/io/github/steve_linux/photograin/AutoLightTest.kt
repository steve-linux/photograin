// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.math.Homography
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.AutoLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLightTest {
    @Test
    fun darkPhotoGetsMoreExposureAndOpenShadows() {
        val result = requireNotNull(AutoLight.estimate(64, 1, ramp(18, 85), Homography.IDENTITY))
        assertTrue(result.exposure > 0)
        assertTrue(result.shadows > 0)
        assertTrue(result.contrast > 0)
        assertEquals(0f, result.highlights, 0f)
    }

    @Test
    fun brightPhotoReducesExposureAndHighlights() {
        val result = requireNotNull(AutoLight.estimate(64, 1, ramp(160, 255), Homography.IDENTITY))
        assertTrue(result.exposure < 0)
        assertTrue(result.highlights < 0)
    }

    @Test
    fun saturatedHighlightsLimitExposureEvenWhenLuminanceIsLow() {
        val pixels = ramp(18, 85)
        for (i in 60 until pixels.size) pixels[i] = 0xff0000ff.toInt()
        val result = requireNotNull(AutoLight.estimate(64, 1, pixels, Homography.IDENTITY))
        assertEquals(0f, result.exposure, 0f)
        assertTrue(result.shadows > 0)
    }

    @Test
    fun cropAndTransparencyExcludeBrightBorder() {
        val dark = IntArray(48) { gray(18 + it) }
        val expected = requireNotNull(AutoLight.estimate(48, 1, dark, Homography.IDENTITY))
        val withBorder = dark + IntArray(48) { gray(255) }
        val crop = Homography(a = 0.5)
        assertEquals(expected, AutoLight.estimate(96, 1, withBorder, crop))
        val withTransparentBorder = dark + IntArray(48) { 0x00ffffff }
        assertEquals(expected, AutoLight.estimate(96, 1, withTransparentBorder, Homography.IDENTITY))
    }

    @Test
    fun flatAndTransparentPhotosHaveNoSuggestion() {
        assertNull(AutoLight.estimate(64, 1, IntArray(64) { gray(120) }, Homography.IDENTITY))
        assertNull(AutoLight.estimate(64, 1, IntArray(64), Homography.IDENTITY))
    }

    @Test
    fun applyingSuggestionOnlyReplacesFourLightControls() {
        val before = Adjustments(exposure = 2f, shadows = -0.3f, temperature = 0.4f, whites = 0.2f)
        val result = requireNotNull(AutoLight.estimate(64, 1, ramp(18, 85), Homography.IDENTITY))
        val after = result.applyTo(before)
        assertEquals(before.temperature, after.temperature, 0f)
        assertEquals(before.whites, after.whites, 0f)
        assertEquals(result.exposure, after.exposure, 0f)
        assertEquals(after, result.applyTo(after))
    }

    private fun ramp(from: Int, to: Int) = IntArray(64) { gray(from + (to - from) * it / 63) }

    private fun gray(value: Int) = 0xff000000.toInt() or (value * 0x010101)
}
