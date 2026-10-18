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
}
