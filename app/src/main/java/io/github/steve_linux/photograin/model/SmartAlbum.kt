// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlinx.serialization.Serializable

@Serializable
internal data class SmartAlbum(
    val query: String = "",
    val rawOnly: Boolean = false,
    val editedOnly: Boolean = false,
    val keyword: String? = null,
    val minimumStars: Int = 0,
    val flag: Int? = null,
    val excludeRejected: Boolean = false,
) {
    @kotlinx.serialization.Transient
    private val tokens = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)

    fun accepts(text: String, raw: Boolean, edited: Boolean, keywords: Set<String>, rating: PhotoRating): Boolean =
        (!rawOnly || raw) && (!editedOnly || edited) &&
            (keyword == null || PhotoKeywords.containsKeyword(keywords, keyword)) &&
            rating.stars >= minimumStars && (flag == null || rating.flag.stored == flag) &&
            (!excludeRejected || rating.flag != PhotoFlag.REJECTED) &&
            tokens.all { text.contains(it, ignoreCase = true) || PhotoKeywords.matchesToken(keywords, it) }
}
