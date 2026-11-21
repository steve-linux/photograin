// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import java.text.Normalizer
import java.util.Locale

/** Metadata only: searching does not decode photos or render their edits. Input is recent-first. */
object PhotoLibraryQuery {
    enum class Sort { RECENT, NAME, RATING }
    enum class Status { ALL, EDITED, UNEDITED }
    data class Entry(val key: String, val name: String, val version: String, val rating: Int, val edited: Boolean)

    fun apply(entries: List<Entry>, query: String, minimumRating: Int, status: Status, sort: Sort): List<Entry> {
        val words = normalized(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val filtered = entries.filter { entry ->
            entry.rating >= minimumRating && when (status) {
                Status.ALL -> true
                Status.EDITED -> entry.edited
                Status.UNEDITED -> !entry.edited
            } && words.all { it in normalized(entry.name + " " + entry.version) }
        }
        // Stable sorting keeps recent order for equal names or ratings and groups same-file versions.
        return when (sort) {
            Sort.RECENT -> filtered
            Sort.NAME -> filtered.sortedBy { normalized(it.name) }
            Sort.RATING -> filtered.sortedByDescending { it.rating }
        }
    }

    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
}
