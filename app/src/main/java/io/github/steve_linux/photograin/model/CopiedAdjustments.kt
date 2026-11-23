// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
enum class AdjustmentGroup(private val tool: ToolGroup) {
    LIGHT(ToolGroup.LIGHT),
    COLOR(ToolGroup.COLOR),
    DETAIL(ToolGroup.DETAIL),
    EFFECTS(ToolGroup.EFFECTS),
    LOOK(ToolGroup.LOOK);

    internal fun transfer(source: Adjustments, target: Adjustments): Adjustments {
        val sliders = Params.forGroup(tool).fold(target) { result, spec ->
            spec.set(result, spec.get(source))
        }
        return when (this) {
            LIGHT -> sliders.copy(curves = source.curves)
            COLOR -> sliders.copy(
                hsl = source.hsl, colorGrade = source.colorGrade, monochrome = source.monochrome,
                pointColors = source.pointColors,
            )
            LOOK -> sliders.copy(look = source.look)
            DETAIL, EFFECTS -> sliders
        }
    }
}

/** The selected groups replace those of each target. Values from unchecked groups are kept. */
@Serializable
data class CopiedAdjustments(val adjustments: Adjustments, val groups: Set<AdjustmentGroup>) {
    fun applyTo(target: Adjustments): Adjustments =
        if (groups.size == AdjustmentGroup.entries.size) adjustments
        else groups.fold(target) { result, group -> group.transfer(adjustments, result) }

    companion object {
        fun modifiedGroups(adjustments: Adjustments): List<AdjustmentGroup> {
            val defaults = Adjustments()
            return AdjustmentGroup.entries.filter { it.transfer(adjustments, defaults) != defaults }
        }

        internal fun decode(json: Json, text: String): CopiedAdjustments? = runCatching {
            val payload = json.parseToJsonElement(text) as? JsonObject ?: return null
            if ("adjustments" in payload || "groups" in payload) {
                json.decodeFromJsonElement(serializer(), payload).takeIf { it.groups.isNotEmpty() }
            } else {
                // Earlier versions saved the adjustment values directly and always pasted all groups.
                CopiedAdjustments(
                    json.decodeFromJsonElement(Adjustments.serializer(), payload),
                    AdjustmentGroup.entries.toSet(),
                )
            }
        }.getOrNull()
    }
}
