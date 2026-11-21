// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.math.Vec2
import io.github.steve_linux.photograin.model.CropGuide
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropGuideTest {
    @Test
    fun guidesStayWithinTheCropForSquarePortraitAndLandscape() {
        val epsilon = 1e-9
        for (ratio in listOf(2.0 / 3, 1.0, 3.0 / 2, 21.0 / 9)) {
            for (guide in CropGuide.entries) {
                for (orientation in 0 until guide.orientations) {
                    val paths = guide.paths(ratio, orientation)
                    assertEquals(guide == CropGuide.NONE, paths.isEmpty())
                    for (path in paths) {
                        assertTrue(path.size >= 2)
                        assertTrue(path.all { it.x in -epsilon..1 + epsilon && it.y in -epsilon..1 + epsilon })
                    }
                }
            }
        }
        val thirds = CropGuide.THIRDS.paths(1.0)
        assertEquals(4, thirds.size)
        assertEquals(1.0 / 3, thirds.first().first().x, epsilon)
        val golden = CropGuide.GOLDEN_RATIO.paths(1.0)
        assertEquals(0.61803398875, golden[2].first().x, epsilon)
    }

    @Test
    fun diagonalMethodAndTriangleGuidesUseTheVisibleAspect() {
        for (ratio in listOf(3.0 / 2, 2.0 / 3)) {
            for ((first, second) in CropGuide.DIAGONAL.paths(ratio)) {
                assertEquals(abs(second.x - first.x) * ratio, abs(second.y - first.y), 1e-9)
            }
            for (orientation in 0..1) {
                val paths = CropGuide.TRIANGLES.paths(ratio, orientation)
                val (start, end) = paths.first()
                val mainX = (end.x - start.x) * ratio
                val mainY = end.y - start.y
                for ((a, b) in paths.drop(1)) {
                    val x = (b.x - a.x) * ratio
                    val y = b.y - a.y
                    assertEquals(0.0, mainX * x + mainY * y, 1e-9)
                    val fraction = (b.y - start.y) / (end.y - start.y)
                    assertEquals(start.x + (end.x - start.x) * fraction, b.x, 1e-9)
                }
            }
        }
    }

    @Test
    fun spiralHasEightDistinctOrientationsAndContinuousArcs() {
        val directions = List(8) { CropGuide.GOLDEN_SPIRAL.paths(1.5, it).single() }
        assertEquals(8, directions.toSet().size)
        val spiral = directions.first()
        assertEquals(Vec2(0.0, 0.0), spiral.first())
        assertEquals(1.0, spiral[12].y, 1e-9)
        assertEquals(1.0, spiral[24].x, 1e-9)
        assertTrue(spiral.zipWithNext().all { (a, b) -> hypot(b.x - a.x, b.y - a.y) < 0.14 })
        assertEquals(spiral, CropGuide.GOLDEN_SPIRAL.paths(1.5, 8).single())
    }
}
