// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.BurstFrame
import io.github.steve_linux.photograin.model.BurstReview
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.MaskCombination
import io.github.steve_linux.photograin.model.MaskComponent
import io.github.steve_linux.photograin.model.PhotoFlag
import io.github.steve_linux.photograin.model.PhotoRating
import io.github.steve_linux.photograin.model.SmartAlbum
import io.github.steve_linux.photograin.render.ShaderParams
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowFeaturesTest {
    @Test
    fun smartAlbumCombinesSavedCriteriaAndUpdatesWithRatings() {
        val album = SmartAlbum("dng Veneto", rawOnly = true, editedOnly = true,
            keyword = "Torneo", minimumStars = 4, excludeRejected = true)
        val restored = Json.decodeFromString<SmartAlbum>(Json.encodeToString(album))
        val keywords = setOf("torneo", "Veneto")
        assertEquals(album, restored)
        assertFalse(restored.accepts("DSC_001.dng", true, true, keywords, PhotoRating(3)))
        assertTrue(restored.accepts("DSC_001.dng", true, true, keywords, PhotoRating(4)))
        assertFalse(restored.accepts("DSC_001.dng", true, true, keywords, PhotoRating(4, PhotoFlag.REJECTED)))
        assertFalse(restored.accepts("DSC_001.dng", true, false, keywords, PhotoRating(5)))
    }

    @Test
    fun allSevenOrderedMaskComponentsArePackedAndSurviveSerialization() {
        val components = (1..7).map { MaskComponent(it, MaskCombination.entries[it % 3]) }
        val entry = LocalAdjustment(invertSelection = true, enabled = false).withComponents(components)
        val restored = Json.decodeFromString<LocalAdjustment>(Json.encodeToString(entry))
        val data = ShaderParams.maskUniforms(listOf(restored) + List(7) { LocalAdjustment() }).locEffects
        val flags = data[3].toInt()
        assertEquals(28, flags and 31)
        for ((i, component) in components.withIndex()) {
            val code = (if (i < 4) data[2].toInt() ushr (5 * i) else flags ushr (5 * (i - 3))) and 31
            assertEquals(1 + component.index + 8 * component.operation.ordinal, code)
        }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), restored.afterMaskRemoved(4).componentList().map { it.index })
        val legacy = Json.decodeFromString<LocalAdjustment>("""{"combineWith":3,"combination":"SUBTRACT"}""")
        assertEquals(listOf(MaskComponent(3, MaskCombination.SUBTRACT)), legacy.componentList())
        assertEquals(listOf(MaskComponent(2, MaskCombination.SUBTRACT)), legacy.afterMaskRemoved(1).componentList())
    }

    @Test
    fun burstsIgnoreDuplicatesAndSeparateCamerasAndTimeGaps() {
        val anchor = LocalDateTime.of(2026, 11, 19, 17, 0)
        fun photo(id: Int, sec: Long, camera: String) = BurstFrame(id, anchor.plusSeconds(sec), camera)
        val shots = listOf(photo(2, 2, "one"), photo(1, 0, "one"), photo(2, 2, "one"),
            photo(3, 3, "two"), photo(4, 4, "two"), photo(5, 8, "two"))
        assertEquals(listOf(listOf(1, 2), listOf(3, 4)), BurstReview.groups(shots).map { g -> g.map { it.photo } })
    }

    @Test
    fun sharperFeatureScoresAboveBlurredFeatureAndFlatImage() {
        val edge = FloatArray(81) { if (it % 9 > 4) 255f else 0f }
        val blurred = FloatArray(81) { (it % 9) * 24f }
        assertTrue(BurstReview.sharpness(edge, 9) > BurstReview.sharpness(blurred, 9))
        assertEquals(0f, BurstReview.sharpness(FloatArray(81) { 25f }, 9), 0f)
    }

    @Test
    fun ranksReadableBurstShotsAndKeepsCaptureOrderOnTies() {
        val shots = listOf("missing", "first", "best", "invalid", "last")
        val scores = mapOf("first" to 10f, "best" to 50f, "invalid" to Float.NaN, "last" to 10f)
        assertEquals(listOf("best", "first", "last"), BurstReview.ranked(shots, scores))
    }
}
