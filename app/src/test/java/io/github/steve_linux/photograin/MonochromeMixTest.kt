// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.HslBand
import io.github.steve_linux.photograin.model.MonochromeMix
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MonochromeMixTest {
    @Test
    fun oldRecipesStayInColorAndNewRecipesKeepTheMix() {
        val old = requireNotNull(EditState.fromJson("""{"version":1,"edit":{"adjustments":{"saturation":-1}}}"""))
        assertFalse(old.adjustments.monochrome.enabled)
        val mix = MonochromeMix(enabled = true).withBand(HslBand.BLUE, -0.7f).withBand(HslBand.ORANGE, 0.3f)
        val updated = old.copy(adjustments = old.adjustments.copy(monochrome = mix))
        assertEquals(updated, EditState.fromJson(updated.toJson()))
        val disabled = updated.copy(adjustments = updated.adjustments.copy(monochrome = mix.copy(enabled = false)))
        assertEquals(disabled, EditState.fromJson(disabled.toJson()))
    }

    @Test
    fun shaderBandOrderAndBoundsAreStableForShortRecipes() {
        val mix = MonochromeMix(bands = listOf(-2f, 0.4f)).withBand(HslBand.BLUE, 2f)
        assertArrayEquals(floatArrayOf(-1f, 0.4f, 0f, 0f, 0f, 1f, 0f, 0f), mix.uniform(), 0f)
    }
}
