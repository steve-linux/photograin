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
    val images: List<ImageLayer> = emptyList(),
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val rawHighlight: RawHighlight = RawHighlight.BLEND,
    val rawWhiteBalance: RawWhiteBalance = RawWhiteBalance(),
) {
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

/** LibRaw highlight handling, applied during development before the editor adjustments. */
@Serializable
enum class RawHighlight(val libRawMode: Int) { CLIP(0), UNCLIP(1), BLEND(2), RECONSTRUCT(5) }

@Serializable
enum class RawWhiteBalanceMode { CAMERA, AUTO, MANUAL }

@Serializable
data class RawWhiteBalance(
    val mode: RawWhiteBalanceMode = RawWhiteBalanceMode.CAMERA,
    val red: Float = 1f,
    val blue: Float = 1f,
    val hotPixels: Boolean = false,
)
