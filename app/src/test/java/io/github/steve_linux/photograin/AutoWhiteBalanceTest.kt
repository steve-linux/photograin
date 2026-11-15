// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.math.Homography
import io.github.steve_linux.photograin.model.AutoWhiteBalance
import io.github.steve_linux.photograin.model.CropSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoWhiteBalanceTest {
    @Test
    fun warmAndGreenCastsProduceCoolingAndMagentaCorrections() {
        val warm = requireNotNull(estimate(IntArray(96) { rgb(172, 160, 148) }))
        assertTrue(warm.first < -0.2f)
        val green = requireNotNull(estimate(IntArray(96) { rgb(150, 167, 150) }))
        assertEquals(0f, green.first, 0f)
        assertTrue(green.second > 0.3f)
    }

    @Test
    fun neutralAndClippedOrSaturatedAreasAreHandledSeparately() {
        assertEquals(0f to 0f, estimate(IntArray(96) { rgb(160, 160, 160) }))
        val noisyScene = IntArray(96) {
            when (it % 3) {
                0 -> rgb(250, 242, 232)
                1 -> rgb(30, 17, 15)
                else -> rgb(35, 85, 210)
            }
        }
        assertNull(estimate(noisyScene))
        assertNull(estimate(IntArray(96) { 0x00cccccc }))
    }

    @Test
    fun contradictoryIlluminantsAreRejectedAndCropRestrictsTheAnalysis() {
        val warm = IntArray(48) { rgb(172, 160, 148) }
        val cool = IntArray(48) { rgb(146, 160, 174) }
        assertNull(estimate(warm + cool))
        assertEquals(estimate(warm), estimate(warm + cool, Homography(a = 0.5)))
    }

    private fun estimate(pixels: IntArray, map: Homography = Homography.IDENTITY) =
        AutoWhiteBalance.estimate(CropSamples(pixels.size, 1, pixels, map))

    private fun rgb(r: Int, g: Int, b: Int) = 0xff000000.toInt() or (r shl 16) or (g shl 8) or b
}
