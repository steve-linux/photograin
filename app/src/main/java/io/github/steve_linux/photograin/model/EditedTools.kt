// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

/**
 * Which editing panels hold a change, for the dot Lightroom shows next to an edited panel.
 * Grouped as the panels show them, not as [Params] files them: Texture, Clarity and Dehaze
 * sit with the Effects, and the frame, texts and shapes are Effects too.
 */
object EditedTools {
    private val effectsSliders = setOf("texture", "clarity", "dehaze")

    fun of(edit: EditState): Set<ToolGroup> {
        val a = edit.adjustments
        val defaults = Adjustments()
        fun changed(id: String) = Params.all.first { it.id == id }.let { it.get(a) != it.get(defaults) }
        fun sliders(group: ToolGroup) = Params.forGroup(group).any { it.id !in effectsSliders && changed(it.id) }
        return buildSet {
            if (sliders(ToolGroup.LIGHT) || !a.curves.isIdentity) add(ToolGroup.LIGHT)
            if (sliders(ToolGroup.COLOR) || !a.hsl.isIdentity || !a.colorGrade.isNeutral ||
                a.monochrome != MonochromeMix() || a.pointColors.isNotEmpty()
            ) add(ToolGroup.COLOR)
            if (sliders(ToolGroup.EFFECTS) || effectsSliders.any(::changed) || edit.frame != Frame() ||
                edit.texts.isNotEmpty() || edit.shapes.isNotEmpty()
            ) add(ToolGroup.EFFECTS)
            if (sliders(ToolGroup.DETAIL)) add(ToolGroup.DETAIL)
            if (edit.hasOptics) add(ToolGroup.OPTICS)
            if (edit.geometry != Geometry()) add(ToolGroup.CROP)
            if (a.look.lutId != null) add(ToolGroup.LOOK)
            // Masks only: healing and clone spots have their own tab, see EditState.retouch.
            if (edit.masks.isNotEmpty()) add(ToolGroup.LOCAL)
        }
    }
}
