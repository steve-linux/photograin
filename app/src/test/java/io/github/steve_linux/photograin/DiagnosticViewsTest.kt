// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.MaskOverlayMode
import io.github.steve_linux.photograin.model.ToneHistogram
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticViewsTest {
    @Test
    fun waveformKeepsSeparateChannelsAndHorizontalPositions() {
        val histogram = ToneHistogram(withWaveform = true)
        val width = ToneHistogram.SCOPE_SIZE
        histogram.addWaveform(8, red = 255, green = 128, blue = 0)
        histogram.addWaveform(99, red = 255, green = 0, blue = 128)
        val waveform = checkNotNull(histogram.waveform)
        assertEquals(1, waveform[0][127 * width + 8])
        assertEquals(1, waveform[0][127 * width + 99])
        assertEquals(1, waveform[1][64 * width + 8])
        assertEquals(1, waveform[2][64 * width + 99])
        assertEquals(6, waveform.sumOf { it.sum() })
    }

    @Test
    fun vectorscopeCentersNeutralsAndSeparatesRedFromBlue() {
        val histogram = ToneHistogram(withVectorscope = true)
        for (gray in 0..255) histogram.add(gray, gray, gray)
        histogram.add(255, 0, 0)
        histogram.add(0, 0, 255)
        val density = checkNotNull(histogram.vectorscope)
        val grid = ToneHistogram.SCOPE_SIZE
        val center = (grid / 2) * grid + grid / 2
        assertEquals(256, density[center])
        assertEquals(258, density.sum())
        val red = ToneHistogram.chromaIndex(255, 0, 0)
        val blue = ToneHistogram.chromaIndex(0, 0, 255)
        assertTrue(red / grid > center / grid)
        assertTrue(blue % grid > center % grid)
        assertEquals(1, density[red])
        assertEquals(1, density[blue])
        assertNull(ToneHistogram().vectorscope)
    }

    @Test
    fun maskPreviewKeepsLegacyDefaultAndSerializesNewModes() {
        val old = Json.decodeFromString<LocalAdjustment>("{}")
        assertEquals(MaskOverlayMode.COLOR, old.overlayMode)
        assertTrue(MaskOverlayMode.COLOR_ON_MONO.usesColorOverlay)
        assertFalse(MaskOverlayMode.MASK_ONLY.usesColorOverlay)
        val changed = old.copy(overlayMode = MaskOverlayMode.MASK_ONLY)
        assertEquals(changed, Json.decodeFromString<LocalAdjustment>(Json.encodeToString(changed)))
        assertEquals(5, changed.overlayMode.shaderId)
    }
}
