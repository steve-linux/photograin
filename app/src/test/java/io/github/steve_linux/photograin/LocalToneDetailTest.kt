// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.Background
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.LocalParams
import io.github.steve_linux.photograin.render.ShaderParams
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalToneDetailTest {
    @Test
    fun oldMasksKeepNewCorrectionsNeutral() {
        val mask = Json.decodeFromString<LocalAdjustment>("""{"clarity":0.5}""")
        val uniforms = ShaderParams.maskUniforms(listOf(mask))
        assertArrayEquals(FloatArray(LocalAdjustment.MAX * 4), uniforms.locToneDetail, 0f)
        assertEquals(0.5f, uniforms.locB[3], 0f)
        assertEquals(1f, mask.amount, 0f)
        assertArrayEquals(FloatArray(LocalAdjustment.MAX * 4), uniforms.locDetail, 0f)
    }

    @Test
    fun controlsStayIndependentAndRoundTripIntoTheCorrectMaskUniforms() {
        val values = linkedMapOf("whites" to 0.4f, "blacks" to -0.2f, "textureAmount" to -0.6f, "dehaze" to 0.3f)
        var mask = LocalAdjustment(enabled = false)
        for ((id, value) in values) {
            val spec = LocalParams.all.single { it.id == id }
            mask = spec.set(mask, value)
            assertEquals(value, spec.get(mask), 0f)
        }
        val restored = Json.decodeFromString<LocalAdjustment>(Json.encodeToString(mask))
        val uniforms = ShaderParams.maskUniforms(listOf(LocalAdjustment(), restored))
        assertArrayEquals(values.values.toFloatArray(), uniforms.locToneDetail.copyOfRange(4, 8), 0f)
        assertEquals(8f, uniforms.locEffects[7], 0f)
    }

    @Test
    fun amountScalesAllCorrectionsAndPacksDetailForTheEighthMask() {
        val defaults = LocalAdjustment()
        val adjusted = LocalParams.all.fold(defaults) { mask, spec -> spec.set(mask, spec.max) }
        val sharpness = LocalParams.all.single { it.id == "localSharpness" }
        val mask = sharpness.set(adjusted, -0.75f).copy(amount = 0.5f, hueShift = -90f)
        val restored = Json.decodeFromString<LocalAdjustment>(Json.encodeToString(mask))
        assertEquals(0.5f, restored.amount, 0f)
        assertEquals(-90f, restored.hueShift, 0f)
        assertEquals(4f, restored.exposure, 0f)
        assertEquals(-0.75f, restored.sharpness, 0f)
        assertEquals(1f, restored.noiseReduction, 0f)
        assertEquals(1f, restored.defringe, 0f)

        val uniforms = ShaderParams.maskUniforms(List(7) { defaults } + restored)
        assertArrayEquals(floatArrayOf(2f, 0.5f, 0.5f, 0.5f), uniforms.locA.copyOfRange(28, 32), 0f)
        assertArrayEquals(FloatArray(4) { 0.5f }, uniforms.locB.copyOfRange(28, 32), 0f)
        assertArrayEquals(FloatArray(4) { 0.5f }, uniforms.locToneDetail.copyOfRange(28, 32), 0f)
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, -1f, 0f), uniforms.locEffects.copyOfRange(28, 32), 0f)
        assertArrayEquals(FloatArray(28), uniforms.locDetail.copyOfRange(0, 28), 0f)
        assertArrayEquals(floatArrayOf(-0.125f, -0.375f, 0.5f, 0.5f), uniforms.locDetail.copyOfRange(28, 32), 0f)
    }

    @Test
    fun zeroAmountRetainsTheSelectionForCutoutsAndCombinations() {
        val mask = LocalAdjustment(
            exposure = 2f,
            blur = 1f,
            hueShift = 135f,
            sharpness = 0.5f,
            noiseReduction = 0.75f,
            defringe = 1f,
            background = Background.TRANSPARENT,
            invertSelection = true,
            combineWith = 1,
            amount = 0f,
        )
        val masks = listOf(mask, LocalAdjustment())
        val uniforms = ShaderParams.maskUniforms(masks)
        assertEquals(0, LocalAdjustment.cutoutIndex(masks))
        assertArrayEquals(FloatArray(4), uniforms.locA.copyOfRange(0, 4), 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 4f), uniforms.locEffects.copyOfRange(0, 4), 0f)
        assertArrayEquals(FloatArray(4), uniforms.locDetail.copyOfRange(0, 4), 0f)
    }
}
