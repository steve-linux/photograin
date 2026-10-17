// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.CubeLut
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Look
import io.github.steve_linux.photograin.model.Preset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LutTest {
    private fun identityCube(size: Int, header: String = ""): String = buildString {
        appendLine("# made by a test")
        appendLine("TITLE \"Identity\"")
        appendLine("LUT_3D_SIZE $size")
        append(header)
        val n = (size - 1).toFloat()
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            appendLine("${r / n} ${g / n} ${b / n}")
        }
    }

    @Test
    fun parsesIdentity3d() {
        val lut = CubeLut.parse(identityCube(5))
        assertEquals("Identity", lut.title)
        assertEquals(5, lut.size)
        assertArrayEquals(floatArrayOf(0.3f, 0.6f, 0.9f), lut.sample(0.3f, 0.6f, 0.9f), 1e-5f)
        assertArrayEquals(floatArrayOf(1f, 0f, 1f), lut.sample(2f, -1f, 1f), 1e-5f)
    }

    @Test
    fun redVariesFastest() {
        // Size 2: entry order is (r,g,b) = 000, 100, 010, 110, 001, ...; make red output inverted.
        val text = "LUT_3D_SIZE 2\n" + listOf(
            "1 0 0", "0 0 0", "1 1 0", "0 1 0", "1 0 1", "0 0 1", "1 1 1", "0 1 1",
        ).joinToString("\n")
        val lut = CubeLut.parse(text)
        assertArrayEquals(floatArrayOf(0.75f, 0.5f, 0.5f), lut.sample(0.25f, 0.5f, 0.5f), 1e-5f)
    }

    @Test
    fun parses1dAsCurve() {
        val text = "LUT_1D_SIZE 3\n0 0 0\n0.25 0.5 0.75\n1 1 1\n"
        val lut = CubeLut.parse(text)
        assertNull(lut.title)
        assertArrayEquals(floatArrayOf(0.25f, 0.5f, 0.75f), lut.sample(0.5f, 0.5f, 0.5f), 1e-5f)
    }

    @Test
    fun honorsDomain() {
        // Table covers 0..2: input 0.5 sits a quarter of the way in, where identity data is 0.25.
        val lut = CubeLut.parse(identityCube(5, "DOMAIN_MIN 0 0 0\nDOMAIN_MAX 2 2 2\n"))
        assertArrayEquals(floatArrayOf(0.25f, 0.25f, 0.25f), lut.sample(0.5f, 0.5f, 0.5f), 1e-4f)
    }

    @Test
    fun rejectsBrokenFiles() {
        assertThrows(IllegalArgumentException::class.java) { CubeLut.parse("hello") }
        assertThrows(IllegalArgumentException::class.java) { CubeLut.parse("LUT_3D_SIZE 2\n0 0 0\n") }
        assertThrows(IllegalArgumentException::class.java) { CubeLut.parse("LUT_3D_SIZE 200\n") }
        assertThrows(IllegalArgumentException::class.java) { CubeLut.parse("LUT_3D_SIZE 2\n0 0 x\n") }
    }

    @Test
    fun lookRoundTripsAndOldRecipesStillLoad() {
        val edit = EditState(adjustments = Adjustments(look = Look("abc", "Teal", 0.6f)))
        assertEquals(edit, EditState.fromJson(edit.toJson()))
        val old = """{"version":1,"edit":{"adjustments":{"exposure":0.5}}}"""
        assertEquals(Look(), EditState.fromJson(old)?.adjustments?.look)
    }

    @Test
    fun presetKeepsPhotoSpecificDetail() {
        val current = Adjustments(sharpness = 0.4f, noiseReduction = 0.3f, exposure = 1f)
        val bw = Preset.BUILT_IN.first { it.builtIn == "bw" }
        val applied = bw.applyTo(current)
        assertEquals(-1f, applied.saturation)
        assertEquals(0f, applied.exposure)
        assertEquals(0.4f, applied.sharpness)
        assertEquals(0.3f, applied.noiseReduction)
    }
}
