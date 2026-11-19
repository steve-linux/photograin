// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.Background
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.Mask
import io.github.steve_linux.photograin.model.MaskCombination
import io.github.steve_linux.photograin.model.MaskRange
import io.github.steve_linux.photograin.model.RangeType
import io.github.steve_linux.photograin.render.ShaderParams
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskManagementTest {
    @Test
    fun duplicateRetainsReferenceRangeAndAdjustmentsButUsesOneCutout() {
        val source = LocalAdjustment(
            mask = Mask(invert = true, range = MaskRange(type = RangeType.LUMINANCE)),
            exposure = 0.6f, combination = MaskCombination.SUBTRACT, combineWith = 1,
            background = Background.TRANSPARENT, name = "Original",
        )
        val duplicate = source.duplicate("Copia", invert = true)
        assertEquals(source.copy(name = "Copia", background = Background.KEEP, invertSelection = true), duplicate)
        assertFalse(duplicate.duplicate("Seconda copia", invert = true).invertSelection)
        assertEquals(0, LocalAdjustment.cutoutIndex(listOf(source, duplicate)))
        assertEquals(-1, LocalAdjustment.cutoutIndex(listOf(source.copy(enabled = false), duplicate)))
    }

    @Test
    fun preservesDisabledReferencesAndPacksTheirOwnEffectFlagSeparately() {
        val first = LocalAdjustment(combineWith = 1, combination = MaskCombination.INTERSECT, invertSelection = true)
        val muted = LocalAdjustment(enabled = false, exposure = 0.5f)
        val uniforms = ShaderParams.maskUniforms(listOf(first, muted))
        assertEquals(2, uniforms.count)
        assertEquals(1f, uniforms.locEffects[2], 0f)
        assertEquals(6f, uniforms.locEffects[3], 0f)
        assertEquals(8f, uniforms.locEffects[7], 0f)
    }

    @Test
    fun roundTripsNewSettingsAndKeepsOldRecipeBehavior() {
        val legacy = Json.decodeFromString<LocalAdjustment>("""{"exposure":0.5}""")
        assertTrue(legacy.enabled)
        assertFalse(legacy.invertSelection)
        val changed = legacy.copy(enabled = false, invertSelection = true)
        assertEquals(changed, Json.decodeFromString<LocalAdjustment>(Json.encodeToString(changed)))
    }
}
