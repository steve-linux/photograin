// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.ColorGrade
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.GradeTone
import io.github.steve_linux.photograin.model.TonalRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorGradeTest {
    @Test
    fun existingRecipesHaveNeutralGradingAndNewRecipesRetainEachTonalRange() {
        val old = requireNotNull(EditState.fromJson("""{"version":1,"edit":{"adjustments":{"exposure":0.4}}}"""))
        assertTrue(old.adjustments.colorGrade.isNeutral)
        val grade = ColorGrade(balance = 0.25f)
            .withTone(TonalRange.SHADOWS, GradeTone(hue = 210f, intensity = 0.65f))
            .withTone(TonalRange.MIDTONES, GradeTone(luminance = -0.2f))
            .withTone(TonalRange.HIGHLIGHTS, GradeTone(hue = 32f, intensity = 0.3f))
        assertFalse(grade.isNeutral)
        val edit = old.copy(adjustments = Adjustments(exposure = 0.4f, colorGrade = grade))
        assertEquals(edit, EditState.fromJson(edit.toJson()))
    }
}
