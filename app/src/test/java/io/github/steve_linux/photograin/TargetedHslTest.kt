// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.Hsl
import io.github.steve_linux.photograin.model.HslBand
import io.github.steve_linux.photograin.model.HslControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetedHslTest {
    @Test
    fun sampledColorsChooseTheNearestBandAcrossTheRedWrap() {
        assertEquals(HslBand.RED, HslBand.fromColor(0.8f, 0.1f, 0.15f))
        assertEquals(HslBand.ORANGE, HslBand.fromColor(0.8f, 0.45f, 0.10f))
        assertEquals(HslBand.BLUE, HslBand.fromColor(0.13f, 0.16f, 0.7f))
        assertEquals(HslBand.AQUA, HslBand.fromColor(0.10f, 0.7f, 0.68f))
    }

    @Test
    fun nearNeutralColorsDoNotSelectARandomBand() {
        assertNull(HslBand.fromColor(0.4f, 0.4f, 0.4f))
        assertNull(HslBand.fromColor(0.4f, 0.41f, 0.4f))
    }

    @Test
    fun changingOneControlKeepsOtherBandsAndControls() {
        val before = Hsl().withSaturation(HslBand.RED, -0.25f).withLuminance(HslBand.BLUE, -0.4f)
        val after = HslControl.SATURATION.update(before, HslBand.BLUE, 1.3f)
        assertEquals(1f, HslControl.SATURATION.value(after, HslBand.BLUE), 0f)
        assertEquals(-0.25f, HslControl.SATURATION.value(after, HslBand.RED), 0f)
        assertEquals(before.luminance, after.luminance)
        assertEquals(before.hue, after.hue)
    }
}
