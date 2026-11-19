// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.DepthFocus
import io.github.steve_linux.photograin.model.DepthMap
import io.github.steve_linux.photograin.model.HighlightGain
import io.github.steve_linux.photograin.model.Mask
import io.github.steve_linux.photograin.model.MaskType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthAndHdrTest {
    private val size = DepthMap.SIZE
    private val ramp = ByteArray(size * size) { i -> if (i % size < size / 2) 0 else 255.toByte() }

    @Test
    fun depthSamplingAlignsWithStoredPixelsAndInterpolatesTheBoundary() {
        val plain = DepthMap.fromUpright(ramp, BaseOrientation())
        val rotated = DepthMap.fromUpright(ramp, BaseOrientation.fromExif(6))
        val mirrored = DepthMap.fromUpright(ramp, BaseOrientation.fromExif(2))
        assertEquals(0.5f, plain.at(0.5f, 0.5f)!!, 0.0001f)
        assertEquals(1f, plain.at(0.9f, 0.5f)!!, 0f)
        assertEquals(1f, rotated.at(0.5f, 0.1f)!!, 0f)
        assertEquals(0f, rotated.at(0.5f, 0.9f)!!, 0f)
        assertEquals(1f, mirrored.at(0.1f, 0.5f)!!, 0f)
    }

    @Test
    fun recipeRoundTripKeepsFocusAndMapAndRejectsInvalidMapData() {
        val original = Mask(MaskType.DEPTH, depth = DepthFocus(DepthMap.fromUpright(ramp, BaseOrientation()), center = 0.35f))
        val restored = Json.decodeFromString<Mask>(Json.encodeToString(original))
        assertEquals(original, restored)
        assertEquals(1f, restored.depth!!.map.at(0.9f, 0.5f)!!, 0f)
        assertNull(DepthMap("invalid").raster)
        assertNull(Json.decodeFromString<Mask>("""{"type":"RADIAL"}""").depth)
    }

    @Test
    fun hdrGainKeepsLegacyDefaultAndLimitsHighlightBoost() {
        for (gray in 0..255) {
            val light = gray / 255f
            val t = ((light - 0.52f) / 0.48f).coerceIn(0f, 1f)
            assertEquals((t * t * 255).toInt(), HighlightGain.encode(gray * 0x010101, HighlightGain.DEFAULT_THRESHOLD))
        }
        assertEquals(2f, HighlightGain.ratio(HighlightGain.DEFAULT_STOPS), 0f)
        assertEquals(8f, HighlightGain.ratio(99f), 0f)
        assertEquals(0, HighlightGain.encode(0xBBBBBB, 0.75f))
        assertTrue(HighlightGain.encode(0xBBBBBB, 0.5f) > 0)
        assertEquals(255, HighlightGain.encode(0xFFFFFF, 0.95f))
    }
}
