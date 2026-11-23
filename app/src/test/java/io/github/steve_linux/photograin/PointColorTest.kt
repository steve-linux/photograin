// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.AdjustmentGroup
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.CopiedAdjustments
import io.github.steve_linux.photograin.model.EditChanges
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.PointColor
import io.github.steve_linux.photograin.render.ShaderParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointColorTest {
    private val orange = checkNotNull(PointColor.sample(0.9f, 0.5f, 0.2f))

    @Test
    fun theSampledColorIsFullyChanged() {
        assertEquals(1f, orange.weight(0.9f, 0.5f, 0.2f), 1e-4f)
    }

    @Test
    fun grayHasNoColorToPick() {
        assertNull(PointColor.sample(0.5f, 0.5f, 0.5f))
        assertNull(PointColor.sample(0.01f, 0.0f, 0.0f))
        assertEquals(0f, orange.weight(0.6f, 0.6f, 0.6f), 1e-6f)
    }

    @Test
    fun distantColorsAreLeftAlone() {
        // Blue, green and a dark version of the same hue.
        assertEquals(0f, orange.weight(0.2f, 0.3f, 0.9f), 1e-6f)
        assertEquals(0f, orange.weight(0.2f, 0.8f, 0.2f), 1e-6f)
        assertEquals(0f, orange.weight(0.09f, 0.05f, 0.02f), 1e-6f)
    }

    @Test
    fun rangeWidensWhatIsReached() {
        // A yellower orange, about 13 degrees away.
        val near = floatArrayOf(0.9f, 0.62f, 0.2f)
        val narrow = orange.copy(range = 0f).weight(near[0], near[1], near[2])
        val wide = orange.copy(range = 1f).weight(near[0], near[1], near[2])
        assertTrue("narrow $narrow wide $wide", wide > narrow)
        assertEquals(1f, wide, 1e-4f)
    }

    @Test
    fun hueWrapsAroundRed() {
        val red = checkNotNull(PointColor.sample(0.9f, 0.1f, 0.15f))
        // The same red leaning the other way, across hue 0.
        assertTrue(red.weight(0.9f, 0.15f, 0.1f) > 0.9f)
    }

    @Test
    fun swatchShowsTheSample() {
        val c = PointColor.swatch(orange)
        assertEquals(230f, (c shr 16 and 0xFF).toFloat(), 1f)
        assertEquals(128f, (c shr 8 and 0xFF).toFloat(), 1f)
        assertEquals(51f, (c and 0xFF).toFloat(), 1f)
    }

    @Test
    fun uniformsSkipPointsThatChangeNothingButKeepTheShownOne() {
        val active = orange.copy(saturationShift = 0.5f)
        val idle = checkNotNull(PointColor.sample(0.2f, 0.3f, 0.9f))
        val packed = ShaderParams.pointColorUniforms(listOf(idle, active), show = -1)
        assertEquals(1, packed.count)
        assertEquals(-1, packed.shown)
        assertEquals(0.5f, packed.shifts[1], 0f)

        val shown = ShaderParams.pointColorUniforms(listOf(idle, active), show = 0)
        assertEquals(2, shown.count)
        assertEquals(0, shown.shown)
        assertEquals(idle.hue, shown.samples[0], 0f)
    }

    @Test
    fun hueShiftIsPackedInTurns() {
        val packed = ShaderParams.pointColorUniforms(listOf(orange.copy(hueShift = 1f)))
        assertEquals(PointColor.HUE_DEGREES / 360f, packed.shifts[0], 1e-6f)
    }

    @Test
    fun colorGroupCopiesPointColors() {
        val source = Adjustments(pointColors = listOf(orange.copy(hueShift = 0.3f)))
        val pasted = CopiedAdjustments(source, setOf(AdjustmentGroup.COLOR)).applyTo(Adjustments(exposure = 1f))
        assertEquals(source.pointColors, pasted.pointColors)
        assertEquals(1f, pasted.exposure, 0f)
        assertTrue(AdjustmentGroup.COLOR in CopiedAdjustments.modifiedGroups(source))
    }

    @Test
    fun recipesKeepPointColorsAndOldRecipesStillLoad() {
        val edit = EditState(adjustments = Adjustments(pointColors = listOf(orange.copy(luminanceShift = -0.4f, range = 0.8f))))
        assertEquals(edit, EditState.fromJson(edit.toJson()))
        val old = EditState.fromJson("""{"version":1,"edit":{"adjustments":{"exposure":0.5}}}""")
        assertNotNull(old)
        assertTrue(old!!.adjustments.pointColors.isEmpty())
    }

    @Test
    fun historyNamesPointColorChanges() {
        val before = EditState()
        val after = before.copy(adjustments = Adjustments(pointColors = listOf(orange)))
        assertTrue(EditChanges.Area.POINT_COLOR in EditChanges.between(before, after).areas)
    }
}
