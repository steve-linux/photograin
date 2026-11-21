// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.PhotoAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoAnalysisTest {
    @Test
    fun sameStructureToleratesEncodingChangesButNotFlipsOrDifferentFraming() {
        val pixels = IntArray(81) { i -> 30 + i % 9 * 18 + i / 9 * 7 }
        val signature = PhotoAnalysis.signature(pixels, 1.5f)
        assertTrue(PhotoAnalysis.similar(signature,
            PhotoAnalysis.signature(IntArray(81) { pixels[it] + it % 4 }, 1.51f)))
        assertFalse(PhotoAnalysis.similar(signature, PhotoAnalysis.signature(pixels.reversedArray(), 1.5f)))
        assertFalse(PhotoAnalysis.similar(signature, PhotoAnalysis.signature(pixels, 1f)))
        val flat = PhotoAnalysis.signature(IntArray(81) { 100 }, 1.5f)
        assertFalse(PhotoAnalysis.similar(flat, flat))
    }

    @Test
    fun exposureIsMeasuredInStopsAndRejectsInvisibleRegions() {
        assertEquals(1f, checkNotNull(PhotoAnalysis.exposureDelta(0.4, 0.2)), 0.001f)
        assertEquals(-1f, checkNotNull(PhotoAnalysis.exposureDelta(0.2, 0.4)), 0.001f)
        assertEquals(2f, checkNotNull(PhotoAnalysis.exposureDelta(0.8, 0.05)), 0.001f)
        assertNull(PhotoAnalysis.exposureDelta(0.3, 0.001))
        assertNull(PhotoAnalysis.exposureDelta(Double.NaN, 0.5))
    }
}
