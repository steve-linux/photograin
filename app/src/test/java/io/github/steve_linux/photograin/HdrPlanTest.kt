// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.HdrAlignment
import io.github.steve_linux.photograin.model.HdrFrame
import io.github.steve_linux.photograin.model.HdrPlan
import io.github.steve_linux.photograin.model.HdrProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.GregorianCalendar

class HdrPlanTest {
    private val jpeg = HdrFrame(4000, 3000, raw = false)

    @Test
    fun needsTwoToNineSameShapedFrames() {
        assertEquals(HdrProblem.TOO_FEW, HdrPlan.problem(listOf(jpeg)))
        assertNull(HdrPlan.problem(listOf(jpeg, jpeg)))
        assertNull(HdrPlan.problem(List(9) { jpeg }))
        assertEquals(HdrProblem.TOO_MANY, HdrPlan.problem(List(10) { jpeg }))
        // Portrait next to landscape cannot be stacked.
        assertEquals(HdrProblem.DIFFERENT_SHAPES, HdrPlan.problem(listOf(jpeg, HdrFrame(3000, 4000, raw = false))))
        // A RAW and the camera's half-size JPEG of the same shot have the same shape.
        assertNull(HdrPlan.problem(listOf(HdrFrame(6000, 4000, raw = true), HdrFrame(3008, 2000, raw = false))))
    }

    @Test
    fun memoryGrowsWithFramesAndRaw() {
        val three = HdrPlan.bytesNeeded(List(3) { jpeg })
        val five = HdrPlan.bytesNeeded(List(5) { jpeg })
        val raws = HdrPlan.bytesNeeded(List(3) { HdrFrame(4000, 3000, raw = true) })
        assertTrue(five > three)
        assertTrue(raws > three)
        // 12 MP, three JPEGs: well under a gigabyte.
        assertTrue(three in 500_000_000L..900_000_000L)
        // Half the size each way is a quarter of the memory.
        assertEquals(three / 4.0, HdrPlan.bytesNeeded(List(3) { jpeg }, 0.5f).toDouble(), three * 0.01)
    }

    @Test
    fun scalesDownOnlyWhenMemoryIsShort() {
        val frames = List(3) { jpeg }
        val full = HdrPlan.bytesNeeded(frames)
        assertEquals(1f, HdrPlan.scaleFor(frames, full), 0f)
        assertEquals(1f, HdrPlan.scaleFor(frames, full * 2), 0f)
        val half = HdrPlan.scaleFor(frames, full / 4)
        assertTrue(half in 0.45f..0.5f)
        assertTrue(HdrPlan.bytesNeeded(frames, half) <= full / 4)
        // Never smaller than a quarter of each side.
        assertEquals(0.25f, HdrPlan.scaleFor(frames, 1_000L), 0f)
        assertEquals(12f, HdrPlan.megapixels(frames, 1f), 0.01f)
        assertEquals(3f, HdrPlan.megapixels(frames, 0.5f), 0.01f)
    }

    @Test
    fun readsTheNativeAlignment() {
        val a = HdrAlignment.fromNative(intArrayOf(1, 5, 4, 628, 470, 7, -4, 0, 0, -5, 6))
        assertEquals(1, a.reference)
        assertEquals(628, a.width)
        assertEquals(listOf(7, 0, -5), a.dx)
        assertEquals(listOf(-4, 0, 6), a.dy)
        assertTrue(a.shifted)
        assertEquals(7, a.largestShift)
        val still = HdrAlignment.fromNative(intArrayOf(0, 0, 0, 100, 80, 0, 0, 0, 0))
        assertFalse(still.shifted)
        assertEquals(0, still.largestShift)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedAlignment() {
        HdrAlignment.fromNative(intArrayOf(0, 0, 0, 100, 80, 0))
    }

    @Test
    fun namesTheMergedPhoto() {
        assertEquals("IMG_1234_HDR.jpg", HdrPlan.fileName("IMG_1234.JPG", dated = false))
        assertEquals("photo_HDR.jpg", HdrPlan.fileName(null, dated = false))
        val date = GregorianCalendar(2026, 10, 25, 21, 30, 5).time
        assertEquals("Photograin_HDR_20261125_213005.jpg", HdrPlan.fileName("IMG_1.jpg", dated = true, now = date))
    }
}
