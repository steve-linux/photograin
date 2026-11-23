// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.CropRect
import io.github.steve_linux.photograin.model.Curves
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.EditedTools
import io.github.steve_linux.photograin.model.Geometry
import io.github.steve_linux.photograin.model.ParametricCurve
import io.github.steve_linux.photograin.model.PointColor
import io.github.steve_linux.photograin.model.ToolGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class EditedToolsTest {
    @Test
    fun anUntouchedPhotoHasNoDots() {
        assertEquals(emptySet<ToolGroup>(), EditedTools.of(EditState()))
    }

    @Test
    fun slidersMarkThePanelThatShowsThem() {
        assertEquals(setOf(ToolGroup.LIGHT), EditedTools.of(EditState(adjustments = Adjustments(exposure = 0.3f))))
        // Clarity is filed with Detail but shown in Effects.
        assertEquals(setOf(ToolGroup.EFFECTS), EditedTools.of(EditState(adjustments = Adjustments(clarity = 0.2f))))
        assertEquals(setOf(ToolGroup.DETAIL), EditedTools.of(EditState(adjustments = Adjustments(sharpness = 0.4f))))
        assertEquals(setOf(ToolGroup.EFFECTS), EditedTools.of(EditState(adjustments = Adjustments(grainSize = 0.8f))))
    }

    @Test
    fun panelsWithoutSlidersCountToo() {
        val point = PointColor(hue = 0.1f, saturation = 0.5f, value = 0.6f)
        assertEquals(setOf(ToolGroup.COLOR), EditedTools.of(EditState(adjustments = Adjustments(pointColors = listOf(point)))))
        assertEquals(
            setOf(ToolGroup.LIGHT),
            EditedTools.of(EditState(adjustments = Adjustments(curves = Curves(parametric = ParametricCurve(darks = 0.1f))))),
        )
        assertEquals(setOf(ToolGroup.OPTICS), EditedTools.of(EditState(lensVignetting = 0.5f)))
        assertEquals(setOf(ToolGroup.CROP), EditedTools.of(EditState(geometry = Geometry(crop = CropRect(0.1f, 0.1f, 0.9f, 0.9f)))))
    }
}
