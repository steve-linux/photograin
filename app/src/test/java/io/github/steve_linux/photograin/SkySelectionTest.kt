// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.math.Homography
import io.github.steve_linux.photograin.model.*
import io.github.steve_linux.photograin.render.ShaderParams
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SkySelectionTest {
    private fun at(mask: SubjectMask, x: Int, y: Int) = mask.data[y * mask.width + x].toInt() and 255

    @Test fun preservesAbsoluteProbabilitiesOnNoSkyImages() {
        for (value in listOf(0, 30, 80, 120, 160)) {
            val mask = SkySelection.fromModel(ByteArray(48 * 48) { value.toByte() }, BaseOrientation(), 96, 64, 48)
            assertTrue(mask.data.all { it == 0.toByte() })
        }
    }

    @Test fun rejectsIsolatedHighConfidenceSpecks() {
        val raw = ByteArray(48 * 48) { 15 }
        raw[0] = 255.toByte()
        val mask = SkySelection.fromModel(raw, BaseOrientation(), 96, 64, 48)
        assertTrue(mask.data.all { it == 0.toByte() })
    }

    @Test fun keepsConfidentSkyAndLeavesForegroundOutside() {
        val raw = ByteArray(48 * 48) { if (it / 48 < 24) 240.toByte() else 10 }
        val mask = SkySelection.fromModel(raw, BaseOrientation(), 96, 64, 48)
        assertEquals(255, at(mask, 20, 5))
        assertEquals(0, at(mask, 20, 50))
    }

    @Test fun permitsSkyInsideAWindowWithoutABorderSeed() {
        val raw = ByteArray(48 * 48) { if (it % 48 in 12..35 && it / 48 in 12..35) 240.toByte() else 10 }
        val mask = SkySelection.fromModel(raw, BaseOrientation(), 96, 64, 48)
        assertEquals(255, at(mask, 48, 32))
        assertEquals(0, at(mask, 0, 0))
    }

    @Test fun preservesEveryExifOrientationOnANonSquareImage() {
        val n = 48
        val stored = ByteArray(n * n) { if (it / n < 24 && it % n !in 20..24) 240.toByte() else 10 }
        val expected = SkySelection.fromModel(stored, BaseOrientation(), 96, 64, n)
        for (mirror in listOf(false, true)) for (turns in 0..3) {
            val upright = ByteArray(stored.size)
            // Independent forward EXIF transform of the model output square.
            for (sy in 0 until n) for (sx in 0 until n) {
                var x = if (mirror) n - 1 - sx else sx
                var y = sy
                repeat(turns) { val nx = n - 1 - y; y = x; x = nx }
                upright[y * n + x] = stored[sy * n + sx]
            }
            val actual = SkySelection.fromModel(upright, BaseOrientation(mirror, turns), 96, 64, n)
            assertArrayEquals("orientation $mirror/$turns", expected.data, actual.data)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedModelOutput() {
        SkySelection.fromModel(ByteArray(3), BaseOrientation(), 96, 64)
    }

    @Test fun skyCanBeRefinedWithoutLosingItsAutomaticBase() {
        val selected = SkySelection.fromModel(ByteArray(48 * 48) { if (it / 48 < 24) 240.toByte() else 10 }, BaseOrientation(), 96, 64, 48)
        val raster = BrushRaster(96, 64)
        val base = selected.resizedTo(raster.width, raster.height).data
        val erase = BrushStroke(points = listOf(0.25f, 0.25f), radius = 0.06f, hardness = 0.9f, erase = true)
        val paint = BrushStroke(points = listOf(0.75f, 0.75f), radius = 0.06f, hardness = 0.9f)
        raster.sync(listOf(erase, paint), base)
        assertEquals(0f, raster.valueAt(0.25f, 0.25f), 0.01f)
        assertEquals(1f, raster.valueAt(0.75f, 0.75f), 0.01f)
        assertEquals(1f, raster.valueAt(0.5f, 0.1f), 0.01f)
        raster.sync(emptyList(), base)
        assertEquals(1f, raster.valueAt(0.25f, 0.25f), 0.01f)
        assertEquals(0f, raster.valueAt(0.75f, 0.75f), 0.01f)
    }

    @Test fun skyRecipesKeepTheirTypeAndUseTheRasterShader() {
        val mask = Mask(MaskType.SKY, range = MaskRange(RangeType.LUMINANCE), invert = true)
        assertEquals(mask, Json.decodeFromString<Mask>(Json.encodeToString(mask)))
        val uniforms = ShaderParams.maskUniforms(listOf(LocalAdjustment(mask = mask)))
        assertEquals(2f, uniforms.opt[0], 0f)
        assertTrue(LocalAdjustment.usesRaster(MaskType.SKY))
        assertEquals(MaskType.SUBJECT, Json.decodeFromString<Mask>("{\"type\":\"SUBJECT\"}").type)
        assertEquals(MaskType.SKY, LocalAdjustment.create(MaskType.SKY, Homography.IDENTITY, 96, 64).mask.type)
    }

    @Test fun subjectAndObjectUpdatesPreserveTheSky() {
        val sky = SubjectMask(2, 2, ByteArray(4) { 255.toByte() })
        val subject = SubjectMask(2, 2, ByteArray(4))
        val subjects = Subjects(main = subject, objects = mapOf(listOf(0.5f, 0.5f, 1f) to subject), sky = sky)
        assertSame(sky, subjects.copy(main = null).of(Mask(MaskType.SKY)))
        assertSame(sky, subjects.copy(objects = emptyMap()).sky)
        assertSame(subject, subjects.of(Mask(MaskType.SUBJECT)))
        assertNull(subjects.of(Mask(MaskType.BRUSH)))
    }
}
