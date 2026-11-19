// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.Hsl
import io.github.steve_linux.photograin.model.HslBand
import io.github.steve_linux.photograin.model.Look
import io.github.steve_linux.photograin.model.Preset
import io.github.steve_linux.photograin.model.PresetPart
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectivePresetTest {
    @Test
    fun selectivePresetChangesOnlyTheIncludedSettingsAfterSerialization() {
        val source = Adjustments(
            exposure = 1.5f,
            saturation = 0.8f,
            sharpness = 0.1f,
            hsl = Hsl().withHue(HslBand.RED, 0.25f),
            look = Look(lutId = "unselected"),
        )
        val saved = Preset.capture("Style", source, setOf(PresetPart.LIGHT, PresetPart.MIX))
        assertNull(saved.adjustments.look.lutId)
        val restored = Json.decodeFromString<Preset>(Json.encodeToString(saved))
        val target = Adjustments(saturation = -0.2f, sharpness = 0.7f, look = Look(lutId = "kept"))
        assertEquals(target.copy(exposure = 1.5f, hsl = source.hsl), restored.applyTo(target))
    }

    @Test
    fun explicitDetailTransfersSharpeningButLegacyPresetPreservesIt() {
        val target = Adjustments(sharpness = 0.7f, noiseReduction = 0.25f)
        val legacy = Json.decodeFromString<Preset>("""{"id":"legacy","adjustments":{"exposure":1.0,"sharpness":0.4}}""")
        assertNull(legacy.parts)
        assertEquals(target.copy(exposure = 1f), legacy.applyTo(target))
        val detail = Preset.capture("Detail", legacy.adjustments, setOf(PresetPart.DETAIL))
        assertEquals(Adjustments(sharpness = 0.4f), detail.applyTo(target))
    }
}
