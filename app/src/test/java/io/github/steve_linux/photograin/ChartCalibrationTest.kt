// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.ChartCalibration
import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.CubeLut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChartCalibrationTest {
    @Test
    fun fitsColorCorrectionAndWritesAnImportableLut() {
        val scale = doubleArrayOf(0.8, 1.1, 0.9)
        val patches = listOf(0xb23c43, 0x388f60, 0x45619c, 0xb69756, 0x9caebc).map { reference ->
            val measured = DoubleArray(3) { channel ->
                val sample = (reference shr (16 - channel * 8)) and 255
                ColorProfile.SRGB.decode(sample / 255.0) * scale[channel]
            }
            ChartCalibration.Patch(measured, reference)
        }
        val fit = checkNotNull(ChartCalibration.fit(patches))
        for (row in 0..2) for (col in 0..2) {
            assertEquals(if (row == col) 1 / scale[row] else 0.0, fit[row * 3 + col], 0.000001)
        }
        val lut = CubeLut.parse(ChartCalibration.cube("Studio", fit).decodeToString())
        assertNotNull(lut)
        assertEquals("Studio", lut.title)
        assertEquals(17, lut.size)
    }

    @Test
    fun rejectsUnmeasurableColorFromNeutrals() {
        val samples = listOf(0.1, 0.25, 0.55).map {
            ChartCalibration.Patch(doubleArrayOf(it, it, it), 0x808080)
        }
        assertNull(ChartCalibration.fit(samples))
    }
}
