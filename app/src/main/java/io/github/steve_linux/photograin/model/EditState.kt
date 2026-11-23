// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Everything needed to reproduce an edit from the untouched original: the "recipe". */
@Serializable
data class EditState(
    val adjustments: Adjustments = Adjustments(),
    val geometry: Geometry = Geometry(),
    /** Local adjustments, applied in order on top of [adjustments]. */
    val masks: List<LocalAdjustment> = emptyList(),
    /** Healing and clone spots, applied to the original before everything else. */
    val retouch: List<RetouchSpot> = emptyList(),
    /** Border around the photo; it makes the output larger. */
    val frame: Frame = Frame(),
    /** Text drawn over the finished photo and its frame. */
    val texts: List<TextItem> = emptyList(),
    /** Lines, arrows and boxes, drawn under the texts. */
    val shapes: List<ShapeItem> = emptyList(),
    val correctLens: Boolean = false,
    /** Manual distortion (-1..1): positive straightens barrel distortion, negative pincushion. */
    val lensDistortion: Float = 0f,
    /** Manual lens vignetting (-1..1): positive brightens the corners, negative darkens them. */
    val lensVignetting: Float = 0f,
    /** Removes purple and green fringes along high-contrast edges. */
    val removeChromaticAberration: Boolean = false,
) {
    /** Whether any optics correction is on: profile, manual distortion or vignetting, or fringes. */
    val hasOptics: Boolean
        get() = correctLens || lensDistortion != 0f || lensVignetting != 0f || removeChromaticAberration

    /** Duplicate independently editable immutable data; keep the original as the only cutout. */
    fun duplicateMask(index: Int): EditState {
        val original = masks.getOrNull(index) ?: return this
        if (masks.size >= LocalAdjustment.MAX) return this
        return copy(masks = masks + original.copy(background = Background.KEEP))
    }

    fun toJson(): String = json.encodeToString(Recipe(version = Recipe.CURRENT_VERSION, edit = this))

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            prettyPrint = true
        }

        /** Parses a recipe; returns null if it is unreadable rather than throwing. */
        fun fromJson(text: String): EditState? =
            runCatching { json.decodeFromString<Recipe>(text).edit }.getOrNull()
    }
}

/** On-disk envelope, versioned so later releases can migrate old sidecars. */
@Serializable
private data class Recipe(val version: Int, val edit: EditState) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
