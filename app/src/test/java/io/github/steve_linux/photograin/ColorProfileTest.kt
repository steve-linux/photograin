// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.Mat3
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorProfileTest {
    @Test
    fun srgbToXyzMatchesTheStandard() {
        val expected = doubleArrayOf(
            0.4124, 0.3576, 0.1805,
            0.2126, 0.7152, 0.0722,
            0.0193, 0.1192, 0.9505,
        )
        assertArrayEquals(expected, ColorProfile.SRGB.toXyz, 2e-4)
    }

    @Test
    fun displayP3ToSrgbMatchesKnownMatrix() {
        val expected = doubleArrayOf(
            1.2249, -0.2247, 0.0,
            -0.0420, 1.0419, 0.0,
            -0.0197, -0.0786, 1.0979,
        )
        assertArrayEquals(expected, ColorProfile.DISPLAY_P3.conversionTo(ColorProfile.SRGB), 5e-4)
    }

    @Test
    fun whiteStaysWhiteAndRoundTripsAreIdentity() {
        for (a in ColorProfile.entries) for (b in ColorProfile.entries) {
            val m = a.conversionTo(b)
            assertArrayEquals(doubleArrayOf(1.0, 1.0, 1.0), Mat3.apply(m, doubleArrayOf(1.0, 1.0, 1.0)), 1e-9)
            assertArrayEquals(Mat3.IDENTITY, Mat3.multiply(b.conversionTo(a), m), 1e-9)
        }
    }

    @Test
    fun lumaWeightsSumToOne() {
        for (p in ColorProfile.entries) assertEquals(1.0, p.luma.sum(), 1e-9)
        assertArrayEquals(doubleArrayOf(0.2126, 0.7152, 0.0722), ColorProfile.SRGB.luma, 2e-4)
    }

    @Test
    fun transferCurvesRoundTrip() {
        for (p in ColorProfile.entries) {
            for (i in 0..20) {
                val v = i / 20.0
                assertEquals(v, p.encode(p.decode(v)), 1e-9)
            }
        }
        assertEquals(0.2140, ColorProfile.SRGB.decode(0.5), 1e-4)
        assertEquals(0.2178, ColorProfile.ADOBE_RGB.decode(0.5), 1e-4)
    }

    @Test
    fun glMatrixIsColumnMajor() {
        val m = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0)
        assertArrayEquals(floatArrayOf(1f, 4f, 7f, 2f, 5f, 8f, 3f, 6f, 9f), Mat3.toGl(m), 0f)
    }
}
