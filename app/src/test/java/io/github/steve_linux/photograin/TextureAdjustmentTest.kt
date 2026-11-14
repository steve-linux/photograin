// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Params
import io.github.steve_linux.photograin.model.Preset
import io.github.steve_linux.photograin.model.ToolGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class TextureAdjustmentTest {
    @Test
    fun detailSliderStoresTextureSeparatelyAndOldRecipesDefaultToNeutral() {
        val old = requireNotNull(EditState.fromJson("""{"version":1,"edit":{"adjustments":{"clarity":0.4}}}"""))
        val slider = Params.forGroup(ToolGroup.DETAIL).single { it.id == "texture" }
        assertEquals(0f, slider.get(old.adjustments), 0f)
        val edit = old.copy(adjustments = slider.set(old.adjustments, -0.6f))
        assertEquals(-0.6f, slider.get(edit.adjustments), 0f)
        assertEquals(old.adjustments.clarity, edit.adjustments.clarity, 0f)
        assertEquals(edit, EditState.fromJson(edit.toJson()))
        assertEquals(-0.6f, Preset("test", adjustments = edit.adjustments).applyTo(old.adjustments).texture, 0f)
    }
}
