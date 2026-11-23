// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.math.Vec2
import io.github.steve_linux.photograin.model.EditChanges
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.LensCorrection
import io.github.steve_linux.photograin.model.lensCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualOpticsTest {
    private val profile = LensCorrection(
        name = "Test lens",
        calibrationScale = 1.0,
        vignetteScale = 1.0,
        distortion = listOf(1.0, 0.0, -0.1, 0.0, 0.0),
        red = listOf(1.0, 0.0, 0.0),
        blue = listOf(1.0, 0.0, 0.0),
        vignette = listOf(-0.3, 0.0, 0.0),
    )

    @Test
    fun nothingToCorrectWithoutProfileOrManualValues() {
        assertNull(EditState().lensCorrection(null))
        assertNull(EditState().lensCorrection(profile))
        assertSame(profile, EditState(correctLens = true).lensCorrection(profile))
    }

    @Test
    fun positiveDistortionPullsInABarrelBulge() {
        val mapping = EditState(lensDistortion = 1f).lensCorrection(null)!!.mapping(6000, 4000)
        // Barrel correction reads the original closer to the center: nothing to crop away.
        assertEquals(1.0, mapping.scale, 1e-9)
        val corner = mapping.toOriginal(Vec2(1.0, 1.0))
        assertTrue(corner.x < 1.0 && corner.y < 1.0)
        val center = mapping.toOriginal(Vec2(0.5, 0.5))
        assertEquals(0.5, center.x, 1e-12)
    }

    @Test
    fun pincushionCorrectionZoomsInToKeepTheCornersFilled() {
        val mapping = EditState(lensDistortion = -1f).lensCorrection(null)!!.mapping(6000, 4000)
        assertTrue(mapping.scale < 1.0)
        val corner = mapping.toOriginal(Vec2(1.0, 1.0))
        assertTrue(corner.x <= 1.0000001 && corner.y <= 1.0000001)
    }

    @Test
    fun manualValuesAddToTheProfile() {
        val combined = EditState(correctLens = true, lensDistortion = 0.5f, lensVignetting = 1f).lensCorrection(profile)!!
        assertEquals(-0.1 - 0.075, combined.distortion[2], 1e-9)
        assertEquals(-0.3 - 0.5, combined.vignette[0], 1e-9)
        assertEquals("Test lens", combined.name)
        // With the profile switched off only the manual values remain.
        val manual = EditState(correctLens = false, lensVignetting = -1f).lensCorrection(profile)!!
        assertEquals(0.0, manual.distortion[2], 1e-12)
        assertEquals(0.5, manual.vignette[0], 1e-9)
    }

    @Test
    fun opticsChangesAreRecordedAndSaved() {
        val edit = EditState(lensDistortion = 0.25f, lensVignetting = -0.5f, removeChromaticAberration = true)
        assertTrue(edit.hasOptics)
        assertFalse(EditState().hasOptics)
        assertEquals(edit, EditState.fromJson(edit.toJson()))
        assertTrue(EditChanges.Area.OPTICS in EditChanges.between(EditState(), edit).areas)
        assertTrue(EditChanges.Area.OPTICS in EditChanges.between(EditState(), EditState(removeChromaticAberration = true)).areas)
    }
}
