// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.Curves
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.ParametricCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParametricCurveTest {
    @Test
    fun neutralSlidersLeaveTheCurvesAlone() {
        assertTrue(Curves().isIdentity)
        assertTrue(ParametricCurve().toCurve().isIdentity)
        val lut = Curves().toLut()
        for (i in 0 until 256) assertEquals(i, lut[i * 4].toInt() and 0xFF)
    }

    @Test
    fun eachSliderMovesItsOwnQuarter() {
        val shadows = ParametricCurve(shadows = 1f).toCurve().sample()
        assertEquals(0.125f + ParametricCurve.MAX_SHIFT, shadows[32], 0.01f)
        // The highlights barely move.
        assertEquals(0.875f, shadows[224], 0.01f)
        val highlights = ParametricCurve(highlights = -1f).toCurve().sample()
        assertEquals(0.875f - ParametricCurve.MAX_SHIFT, highlights[224], 0.01f)
        assertEquals(0.125f, highlights[32], 0.01f)
        assertFalse(Curves(parametric = ParametricCurve(darks = 0.2f)).isIdentity)
    }

    @Test
    fun noCombinationTurnsTheCurveBack() {
        val values = listOf(-1f, 0f, 1f)
        for (h in values) for (l in values) for (d in values) for (s in values) {
            val samples = ParametricCurve(h, l, d, s).toCurve().sample()
            for (i in 1 until samples.size) assertTrue("$h $l $d $s at $i", samples[i] >= samples[i - 1])
        }
    }

    @Test
    fun parametricRunsBeforeThePointCurve() {
        val lifted = Curves(parametric = ParametricCurve(darks = 1f)).toLut()
        val plain = Curves().toLut()
        assertTrue((lifted[96 * 4].toInt() and 0xFF) > (plain[96 * 4].toInt() and 0xFF))
        // Black and white stay where they are.
        assertEquals(0, lifted[0].toInt() and 0xFF)
        assertEquals(255, lifted[255 * 4].toInt() and 0xFF)
    }

    @Test
    fun savedAndLoadedWithOldRecipesStillReadable() {
        val edit = EditState(adjustments = Adjustments(curves = Curves(parametric = ParametricCurve(lights = 0.4f, shadows = -0.2f))))
        assertEquals(edit, EditState.fromJson(edit.toJson()))
        val old = EditState.fromJson("""{"version":1,"edit":{"adjustments":{"curves":{"master":{"points":[{"x":0,"y":0},{"x":0.5,"y":0.6},{"x":1,"y":1}]}}}}}""")!!
        assertTrue(old.adjustments.curves.parametric.isIdentity)
        assertFalse(old.adjustments.curves.isIdentity)
    }
}
