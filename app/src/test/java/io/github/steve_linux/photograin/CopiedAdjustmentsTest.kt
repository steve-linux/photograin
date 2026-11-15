// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.AdjustmentGroup
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.ColorGrade
import io.github.steve_linux.photograin.model.CopiedAdjustments
import io.github.steve_linux.photograin.model.Hsl
import io.github.steve_linux.photograin.model.HslBand
import io.github.steve_linux.photograin.model.Look
import io.github.steve_linux.photograin.model.MonochromeMix
import io.github.steve_linux.photograin.ui.batch.BatchSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CopiedAdjustmentsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun colorTransfersAdvancedSettingsWithoutReplacingLightOrOtherGroups() {
        val source = Adjustments(
            exposure = 2f,
            temperature = 0.25f,
            hsl = Hsl().withSaturation(HslBand.BLUE, 0.6f),
            colorGrade = ColorGrade(balance = -0.3f),
            monochrome = MonochromeMix(enabled = true),
        )
        val destination = Adjustments(exposure = -0.4f, grain = 0.3f, look = Look(lutId = "existing"))
        val copied = CopiedAdjustments(source, setOf(AdjustmentGroup.COLOR))
        val expected = destination.copy(
            temperature = source.temperature, hsl = source.hsl,
            colorGrade = source.colorGrade, monochrome = source.monochrome,
        )
        assertEquals(expected, copied.applyTo(destination))
        assertEquals(expected, BatchSource.Copied(copied).applyTo(destination))
    }

    @Test
    fun roundTripRetainsSelectionAndCopiesNeutralValuesInSelectedGroups() {
        val source = Adjustments(texture = 0.3f, vignetteHighlights = 0.6f)
        val copied = CopiedAdjustments(source, setOf(AdjustmentGroup.DETAIL, AdjustmentGroup.EFFECTS))
        val decoded = requireNotNull(CopiedAdjustments.decode(json, json.encodeToString(CopiedAdjustments.serializer(), copied)))
        assertEquals(copied, decoded)
        assertEquals(
            Adjustments(exposure = -0.5f, texture = 0.3f, vignetteHighlights = 0.6f),
            decoded.applyTo(Adjustments(exposure = -0.5f, sharpness = 0.7f, vignette = -0.8f)),
        )
    }

    @Test
    fun legacyClipboardsStillCopyAllGroupsAndMalformedSelectionsAreRejected() {
        val old = requireNotNull(CopiedAdjustments.decode(json, """{"exposure":0.4,"texture":0.3}"""))
        assertEquals(AdjustmentGroup.entries.toSet(), old.groups)
        assertEquals(Adjustments(exposure = 0.4f, texture = 0.3f), old.applyTo(Adjustments(temperature = -0.3f)))
        assertNull(CopiedAdjustments.decode(json, """{"adjustments":{},"groups":[]}"""))
        assertNull(CopiedAdjustments.decode(json, """{"adjustments":{},"groups":["FUTURE_GROUP"]}"""))
    }

    @Test
    fun modifiedShortcutDetectsSettingsOutsideTheBasicSliders() {
        assertEquals(emptyList<AdjustmentGroup>(), CopiedAdjustments.modifiedGroups(Adjustments()))
        val adjusted = Adjustments(monochrome = MonochromeMix(enabled = true), look = Look(lutId = "test"))
        assertEquals(listOf(AdjustmentGroup.COLOR, AdjustmentGroup.LOOK), CopiedAdjustments.modifiedGroups(adjusted))
    }
}
