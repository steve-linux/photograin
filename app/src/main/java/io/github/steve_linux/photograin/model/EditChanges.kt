// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

/** Describes the actual difference, including resets/paste, rather than guessing an action. */
object EditChanges {
    enum class Area { CURVES, HSL, GRADING, MONOCHROME, LOOK, GEOMETRY, MASKS, RETOUCH, FRAME, TEXT, SHAPES, OPTICS }
    data class Summary(val parameters: List<String>, val areas: List<Area>)
    fun between(before: EditState, after: EditState): Summary {
        val a = before.adjustments
        val b = after.adjustments
        val areas = buildList {
            if (a.curves != b.curves) add(Area.CURVES)
            if (a.hsl != b.hsl) add(Area.HSL)
            if (a.colorGrade != b.colorGrade) add(Area.GRADING)
            if (a.monochrome != b.monochrome) add(Area.MONOCHROME)
            if (a.look != b.look) add(Area.LOOK)
            if (before.geometry != after.geometry) add(Area.GEOMETRY)
            if (before.masks != after.masks) add(Area.MASKS)
            if (before.retouch != after.retouch) add(Area.RETOUCH)
            if (before.frame != after.frame) add(Area.FRAME)
            if (before.texts != after.texts) add(Area.TEXT)
            if (before.shapes != after.shapes) add(Area.SHAPES)
            if (before.correctLens != after.correctLens) add(Area.OPTICS)
        }
        return Summary(Params.all.filter { it.get(a) != it.get(b) }.map { it.id }, areas)
    }
}
