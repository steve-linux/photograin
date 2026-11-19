// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.AdjustmentSelection
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.ColorGrading
import io.github.steve_linux.photograin.model.CurvePoint
import io.github.steve_linux.photograin.model.Curves
import io.github.steve_linux.photograin.model.Hsl
import io.github.steve_linux.photograin.model.HslBand
import io.github.steve_linux.photograin.model.Look
import io.github.steve_linux.photograin.model.ToneCurve
import io.github.steve_linux.photograin.model.ToolGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustmentSelectionTest {
    @Test
    fun transfersSelectedPanelsIncludingNeutralValuesWithoutOverwritingOthers() {
        val current = Adjustments(exposure = 0.4f, contrast = 0.4f, saturation = 0.3f, lensDistortion = 0.4f)
        val previous = Adjustments(exposure = 1f, saturation = -0.2f, lensDistortion = -0.5f)
        val selection = AdjustmentSelection(setOf(ToolGroup.LIGHT))
        assertEquals(current.copy(exposure = 1f, contrast = 0f), selection.apply(current, previous))
    }

    @Test
    fun transfersCurvesHslAndLutIndependentlyOfColorGrading() {
        val previous = Adjustments(
            curves = Curves(master = ToneCurve(listOf(CurvePoint(0f, 0.1f), CurvePoint(1f, 0.9f)))),
            hsl = Hsl().withSaturation(HslBand.GREEN, 0.4f),
            look = Look(lutId = "source"),
            grading = ColorGrading(balance = 0.5f),
        )
        val current = Adjustments(look = Look(lutId = "target"), saturation = 0.2f)
        assertEquals(
            current.copy(curves = previous.curves, hsl = previous.hsl),
            AdjustmentSelection(emptySet(), curves = true, hsl = true).apply(current, previous),
        )
        assertEquals(current.copy(look = previous.look), AdjustmentSelection(emptySet(), lut = true).apply(current, previous))
        assertEquals(current.copy(saturation = 0f, grading = previous.grading), AdjustmentSelection(setOf(ToolGroup.COLOR)).apply(current, previous))
    }

    @Test
    fun emptySelectionLeavesEverythingIntact() {
        val current = Adjustments(exposure = 2f, vignette = -0.3f)
        val empty = AdjustmentSelection(emptySet())
        assertTrue(empty.isEmpty)
        assertFalse(AdjustmentSelection(emptySet(), curves = true).isEmpty)
        assertEquals(current, empty.apply(current, Adjustments()))
    }
}
