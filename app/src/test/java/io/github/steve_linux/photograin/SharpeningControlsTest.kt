// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Preset
import org.junit.Assert.assertEquals
import org.junit.Test

class SharpeningControlsTest {
    @Test
    fun oldRecipesKeepThePreviousSharpeningAndNewSettingsRoundTrip() {
        val old = requireNotNull(EditState.fromJson("""{"version":1,"edit":{"adjustments":{"sharpness":0.7}}}"""))
        assertEquals(0.7f, old.adjustments.sharpness, 0f)
        assertEquals(1f, old.adjustments.sharpnessRadius, 0f)
        assertEquals(0f, old.adjustments.sharpnessMasking, 0f)
        val edit = old.copy(adjustments = old.adjustments.copy(sharpnessRadius = 2.3f, sharpnessMasking = 0.85f))
        assertEquals(edit, EditState.fromJson(edit.toJson()))
    }

    @Test
    fun applyingPresetRetainsSharpeningSettingsTogether() {
        val preset = Preset.BUILT_IN.first()
        val current = preset.adjustments.copy(sharpness = 0.6f, sharpnessRadius = 2f, sharpnessMasking = 0.8f)
        assertEquals(current, preset.applyTo(current))
    }
}
