// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Params
import io.github.steve_linux.photograin.model.Preset
import io.github.steve_linux.photograin.model.ToolGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class VignetteControlsTest {
    @Test
    fun oldRecipeKeepsTheOriginalShapeAndNewValuesSurviveSaving() {
        val previous = requireNotNull(EditState.fromJson("""{"version":1,"edit":{"adjustments":{"vignette":-0.4}}}"""))
        val a = previous.adjustments
        assertEquals(-0.4f, a.vignette, 0f)
        assertEquals(0.5f, a.vignetteMidpoint, 0f)
        assertEquals(0.5f, a.vignetteFeather, 0f)
        assertEquals(1f, a.vignetteRoundness, 0f)
        assertEquals(0f, a.vignetteHighlights, 0f)

        val controls = Params.forGroup(ToolGroup.EFFECTS).filter { it.id.startsWith("vignette") && it.id != "vignette" }
        assertEquals(4, controls.size)
        val edited = controls.fold(a) { adjustments, control -> control.set(adjustments, 0.25f) }
        assertEquals(-0.4f, edited.vignette, 0f)
        controls.forEach { assertEquals(0.25f, it.get(edited), 0f) }
        val recipe = previous.copy(adjustments = edited)
        assertEquals(recipe, EditState.fromJson(recipe.toJson()))
        assertEquals(edited, Preset("test", adjustments = edited).applyTo(a))
    }
}
