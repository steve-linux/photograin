// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.LocalAdjustment
import io.github.steve_linux.photograin.model.Mask
import io.github.steve_linux.photograin.model.MaskType
import io.github.steve_linux.photograin.model.ObjectSelection
import io.github.steve_linux.photograin.model.PxSize
import io.github.steve_linux.photograin.model.SubjectMask
import io.github.steve_linux.photograin.model.Subjects
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ObjectSelectionTest {
    private val bases = (1..8).map { BaseOrientation.fromExif(it) }

    @Test
    fun uprightPixelsAndPointsAgree() {
        val w = 6
        val h = 4
        val pixels = IntArray(w * h) { it }
        for (base in bases) {
            val (upright, size) = ObjectSelection.upright(pixels, w, h, base)
            assertEquals(ObjectSelection.uprightSize(w, h, base), size)
            // Each stored pixel's center lands on the upright pixel that holds it.
            for (y in 0 until h) for (x in 0 until w) {
                val (ux, uy) = ObjectSelection.toUpright((x + 0.5f) / w, (y + 0.5f) / h, base)
                val px = (ux * size.width).toInt()
                val py = (uy * size.height).toInt()
                assertEquals("$base at $x, $y", y * w + x, upright[py * size.width + px])
            }
        }
    }

    @Test
    fun turnedPhotosAreUprightForTheModel() {
        // Turned a quarter clockwise: the stored top left corner ends up at the top right.
        val (ux, uy) = ObjectSelection.toUpright(0f, 0f, BaseOrientation(quarterTurns = 1))
        assertEquals(1f, ux, 1e-6f)
        assertEquals(0f, uy, 1e-6f)
        assertEquals(PxSize(768, 1024), ObjectSelection.uprightSize(1024, 768, BaseOrientation(quarterTurns = 3)))
    }

    @Test
    fun promptHasTheTapsInModelPixels() {
        val (coords, labels) = ObjectSelection.prompt(listOf(0.5f, 0.25f, 1f, 0.1f, 0.9f, 0f), BaseOrientation(), PxSize(1024, 768))
        assertArrayEquals(floatArrayOf(512f, 192f, 102.4f, 691.2f), coords, 1e-3f)
        assertArrayEquals(intArrayOf(1, 0), labels)
    }

    @Test
    fun maskFollowsTheModelsAnswer() {
        val n = ObjectSelection.LOGITS
        // The model saw a 1024 x 512 image: its answer covers the top half of the square.
        val size = PxSize(1024, 512)
        // The object is the left half of the image.
        val logits = FloatArray(n * n) { if (it % n < n / 2) 10f else -10f }
        val mask = ObjectSelection.fromLogits(logits, size, BaseOrientation(), 100, 50)
        fun at(x: Int, y: Int) = mask.data[y * 100 + x].toInt() and 0xFF
        assertEquals(255, at(10, 25))
        assertEquals(0, at(90, 25))
        // The bottom rows of the square, padding to the model, do not leak in.
        assertEquals(255, at(10, 49))
        // Soft only right at the edge.
        assertEquals(255, at(48, 10))
        assertEquals(0, at(52, 10))
    }

    @Test
    fun maskOfATurnedPhotoIsStoredAsThePhoto() {
        val n = ObjectSelection.LOGITS
        // Stored 100 x 50, shown turned a quarter clockwise: the model saw it 512 x 1024.
        val base = BaseOrientation(quarterTurns = 1)
        val size = ObjectSelection.uprightSize(1024, 512, base)
        // The object is the top of the upright image, which is the stored left side.
        val logits = FloatArray(n * n) { if (it / n < n / 4) 10f else -10f }
        val mask = ObjectSelection.fromLogits(logits, size, base, 100, 50)
        fun at(x: Int, y: Int) = mask.data[y * 100 + x].toInt() and 0xFF
        assertEquals(255, at(10, 25))
        assertEquals(0, at(90, 25))
    }

    @Test
    fun subjectMasksFindTheirBase() {
        val main = SubjectMask(1, 1, byteArrayOf(1))
        val cup = SubjectMask(1, 1, byteArrayOf(2))
        val points = listOf(0.2f, 0.3f, 1f)
        val subjects = Subjects(main, mapOf(points to cup))
        assertSame(main, subjects.of(Mask(MaskType.SUBJECT)))
        assertSame(cup, subjects.of(Mask(MaskType.SUBJECT, points = points)))
        assertNull(subjects.of(Mask(MaskType.SUBJECT, points = listOf(0.5f, 0.5f, 1f))))
    }

    @Test
    fun tapsSurviveTheRecipe() {
        val edit = EditState(masks = listOf(LocalAdjustment(mask = Mask(MaskType.SUBJECT, points = listOf(0.2f, 0.3f, 1f, 0.4f, 0.5f, 0f)))))
        assertEquals(edit, EditState.fromJson(edit.toJson()))
        // Recipes from before taps read as the main subject.
        assertEquals(emptyList<Float>(), EditState.fromJson(EditState(masks = listOf(LocalAdjustment(mask = Mask(MaskType.SUBJECT)))).toJson())!!.masks[0].mask.points)
    }
}
