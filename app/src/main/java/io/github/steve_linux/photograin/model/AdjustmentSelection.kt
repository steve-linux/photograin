// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

internal data class AdjustmentSelection(
    val groups: Set<ToolGroup>,
    val curves: Boolean = false,
    val hsl: Boolean = false,
    val lut: Boolean = false,
) {
    val isEmpty: Boolean get() = groups.isEmpty() && !curves && !hsl && !lut

    fun apply(current: Adjustments, source: Adjustments): Adjustments {
        var result = current
        for (spec in Params.all) {
            if (spec.group in groups) result = spec.set(result, spec.get(source))
        }
        return result.copy(
            grading = if (ToolGroup.COLOR in groups) source.grading else result.grading,
            curves = if (curves) source.curves else result.curves,
            hsl = if (hsl) source.hsl else result.hsl,
            look = if (lut) source.look else result.look,
        )
    }
}
