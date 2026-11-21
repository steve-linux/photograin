// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import io.github.steve_linux.photograin.math.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Composition overlays drawn in crop-normalized coordinates, with no effect on the image. */
enum class CropGuide(val orientations: Int = 1) {
    NONE, THIRDS, GRID, CENTER, DIAGONAL, TRIANGLES(2), GOLDEN_RATIO, GOLDEN_SPIRAL(8);

    /** [aspect] is the visible crop width divided by its height. */
    fun paths(aspect: Double, orientation: Int = 0): List<List<Vec2>> {
        if (!aspect.isFinite() || aspect <= 0) return emptyList()
        val direction = orientation.mod(orientations)
        return when (this) {
            NONE -> emptyList()
            THIRDS -> grid(listOf(1.0 / 3, 2.0 / 3))
            GRID -> grid((1..5).map { it / 6.0 })
            CENTER -> grid(listOf(0.5))
            DIAGONAL -> diagonals(aspect)
            TRIANGLES -> triangles(aspect, flip = direction == 1)
            GOLDEN_RATIO -> grid(listOf(1 / (PHI * PHI), 1 / PHI))
            GOLDEN_SPIRAL -> listOf(spiral.map { orient(it, direction) })
        }
    }

    private companion object {
        val PHI = (1 + sqrt(5.0)) / 2

        fun grid(positions: List<Double>) = positions.flatMap {
            listOf(listOf(Vec2(it, 0.0), Vec2(it, 1.0)), listOf(Vec2(0.0, it), Vec2(1.0, it)))
        }

        // The diagonal method draws a 45-degree line from each corner to the opposite edge.
        fun diagonals(aspect: Double): List<List<Vec2>> {
            val dx = minOf(1.0, 1 / aspect)
            val dy = minOf(1.0, aspect)
            val top = listOf(listOf(Vec2(0.0, 0.0), Vec2(dx, dy)), listOf(Vec2(1.0, 0.0), Vec2(1 - dx, dy)))
            if (dx == 1.0 && dy == 1.0) return top
            return top + listOf(
                listOf(Vec2(0.0, 1.0), Vec2(dx, 1 - dy)),
                listOf(Vec2(1.0, 1.0), Vec2(1 - dx, 1 - dy)),
            )
        }

        fun triangles(aspect: Double, flip: Boolean): List<List<Vec2>> {
            val projection = 1.0 / (1.0 + (1 / aspect) * (1 / aspect))
            val opposite = 1 - projection
            val guides = listOf(
                listOf(Vec2(0.0, 0.0), Vec2(1.0, 1.0)),
                listOf(Vec2(1.0, 0.0), Vec2(projection, projection)),
                listOf(Vec2(0.0, 1.0), Vec2(opposite, opposite)),
            )
            return if (flip) guides.map { line -> line.map { Vec2(1 - it.x, it.y) } } else guides
        }

        // Quarter circles in successive golden rectangle squares. Each local square frame
        // rotates around the remaining rectangle, then shrinks by the golden ratio.
        val spiral = buildList {
            var x = 0.0
            var y = 0.0
            var side = 1.0
            val axes = listOf(Vec2(1.0, 0.0), Vec2(0.0, -1.0), Vec2(-1.0, 0.0), Vec2(0.0, 1.0))
            repeat(12) { i ->
                val u = axes[i % 4]
                val v = axes[(i + 3) % 4]
                for (j in (if (i == 0) 0 else 1)..12) {
                    val angle = j * PI / 24
                    val a = side * (1 - cos(angle))
                    val b = side * sin(angle)
                    val px = (x + a * u.x + b * v.x) / PHI
                    val py = y + a * u.y + b * v.y
                    add(Vec2(px, py))
                }
                x += side * (u.x + v.x)
                y += side * (u.y + v.y)
                side /= PHI
            }
        }

        fun orient(point: Vec2, direction: Int): Vec2 {
            val x = if (direction >= 4) 1 - point.x else point.x
            val y = point.y
            return when (direction % 4) {
                1 -> Vec2(1 - y, x)
                2 -> Vec2(1 - x, 1 - y)
                3 -> Vec2(y, 1 - x)
                else -> Vec2(x, y)
            }
        }
    }
}
