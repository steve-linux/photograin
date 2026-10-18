// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Retouch
import io.github.steve_linux.photograin.model.RetouchSpot
import io.github.steve_linux.photograin.model.RetouchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RetouchTest {
    private val w = 40
    private val h = 40

    private fun patch(f: (Int, Int) -> Float): FloatArray {
        val out = FloatArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) for (c in 0..2) out[(y * w + x) * 3 + c] = f(x, y)
        return out
    }

    private fun at(p: FloatArray, x: Int, y: Int) = p[(y * w + x) * 3]

    @Test
    fun cloneCopiesTheSourceInsideAndKeepsTheOutside() {
        val dst = patch { _, _ -> 100f }
        val src = patch { _, _ -> 30f }
        val spot = RetouchSpot(RetouchType.CLONE, 0.5f, 0.5f, 0.6f, 0.5f, 0.01f, feather = 0.2f)
        val out = Retouch.apply(dst, src, w, h, 20.0, 20.0, 12.0, spot)
        assertEquals(30f, at(out, 20, 20), 1e-3f)
        assertEquals(100f, at(out, 0, 0), 1e-3f)
        assertEquals(100f, at(out, 20, 33), 1e-3f)
    }

    @Test
    fun healMatchesTheSurroundingBrightness() {
        // A dark blemish on a bright, sloping background; the source has the texture but is darker.
        val dst = patch { x, y -> if (abs(x - 20) < 4 && abs(y - 20) < 4) 20f else 150f + x }
        val src = patch { x, _ -> 90f + x + (if (x % 2 == 0) 4f else -4f) }
        val spot = RetouchSpot(RetouchType.HEAL, 0.5f, 0.5f, 0.6f, 0.5f, 0.01f, feather = 0.3f)
        val out = Retouch.apply(dst, src, w, h, 20.0, 20.0, 12.0, spot)
        // The blemish is gone: the middle follows the background (150 + x), give or take the texture.
        for (x in 17..23) assertEquals(150f + x, at(out, x, 20), 6f)
        assertEquals(150f + 1, at(out, 1, 1), 1e-3f)
    }

    @Test
    fun findSourcePicksASimilarArea() {
        // Stripes on the left half, plain on the right; a spot in the plain half should take plain.
        val size = 200
        val pixel = { x: Int, _: Int -> val v = if (x < 100) (if ((x / 4) % 2 == 0) 0f else 255f) else 128f; floatArrayOf(v, v, v) }
        val (sx, sy) = Retouch.findSource(size, size, 150.0, 100.0, 10.0, true, pixel)
        assertTrue("source $sx,$sy", sx - 10 >= 100)
        assertTrue(abs(sx - 150) + abs(sy - 100) >= 20)
    }

    @Test
    fun recipeKeepsSpots() {
        val state = EditState(retouch = listOf(RetouchSpot(RetouchType.CLONE, 0.1f, 0.2f, 0.3f, 0.4f, 0.02f, opacity = 0.7f)))
        assertEquals(state, EditState.fromJson(state.toJson()))
    }

    /** A face-like patch: skin around a red pupil of radius 6 at (20, 20), with a white catchlight. */
    private fun redEyePatch(): FloatArray {
        val out = FloatArray(w * h * 3)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 3
            val d = Math.hypot(x - 20.0, y - 20.0)
            val rgb = when {
                d < 1.5 -> floatArrayOf(250f, 245f, 245f)
                d < 6 -> floatArrayOf(200f, 40f, 45f)
                else -> floatArrayOf(215f, 165f, 140f)
            }
            rgb.copyInto(out, i)
        }
        return out
    }

    @Test
    fun redEyeDarkensThePupilAndLeavesSkinAndCatchlight() {
        val p = redEyePatch()
        val spot = RetouchSpot(RetouchType.RED_EYE, 0.5f, 0.5f, 0.5f, 0.5f, 0.01f, feather = 0.3f)
        val out = Retouch.apply(p, p, w, h, 20.0, 20.0, 9.0, spot)
        val i = (20 * w + 24) * 3
        assertEquals(42.5f, out[i], 1f)
        assertEquals(40f, out[i + 1], 0.01f)
        // Skin inside the circle stays: it is not red enough.
        val skin = (20 * w + 27) * 3
        assertEquals(215f, out[skin], 0.01f)
        // The catchlight is white, not red.
        assertEquals(250f, out[(20 * w + 20) * 3], 0.01f)
    }

    @Test
    fun redEyeIsFoundAndFittedFromANearbyTap() {
        val p = redEyePatch()
        val found = Retouch.findRedEye(w, h, 23.0, 17.0, 15.0) { x, y -> p.copyOfRange((y * w + x) * 3, (y * w + x) * 3 + 3) }
        assertTrue(found != null)
        val (cx, cy, r) = found!!
        assertEquals(20.5, cx, 0.6)
        assertEquals(20.5, cy, 0.6)
        // Covers the pupil (radius 6) without reaching far into the skin.
        assertTrue("radius $r", r in 6.0..10.0)
    }

    @Test
    fun noRedEyeOnSkin() {
        val p = patch { _, _ -> 0f }.also { for (i in 0 until w * h) floatArrayOf(215f, 165f, 140f).copyInto(it, i * 3) }
        assertEquals(null, Retouch.findRedEye(w, h, 20.0, 20.0, 15.0) { x, y -> p.copyOfRange((y * w + x) * 3, (y * w + x) * 3 + 3) })
    }
}
