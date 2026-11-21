// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

internal enum class GallerySort { COLLECTION, CAPTURE, FILE_NAME, RATING }

internal enum class GalleryGrouping {
    NONE, YEAR, MONTH, DAY, RATING;

    val usesCaptureDate: Boolean get() = this == YEAR || this == MONTH || this == DAY
}

internal data class GallerySortKey(
    val collectionIndex: Int,
    val capturedAt: LocalDateTime? = null,
    val fileName: String? = null,
    val rating: Int = 0,
)

internal data class GallerySection<T>(
    val photos: List<T>,
    val date: LocalDate? = null,
    val rating: Int? = null,
)

internal object GalleryOrder {
    private val exifFormat = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    fun captureTime(value: String?): LocalDateTime? =
        value?.let { runCatching { LocalDateTime.parse(it.trim(), exifFormat) }.getOrNull() }

    /** Direction reverses available values. Missing metadata remains last in either direction. */
    fun comparator(sort: GallerySort, reversed: Boolean): Comparator<GallerySortKey> = Comparator { a, b ->
        when (sort) {
            GallerySort.COLLECTION -> compareKnown(a.collectionIndex, b.collectionIndex, reversed)
            GallerySort.CAPTURE -> compareKnown(a.capturedAt, b.capturedAt, !reversed)
            GallerySort.FILE_NAME -> compareKnown(a.fileName?.lowercase(Locale.ROOT), b.fileName?.lowercase(Locale.ROOT), reversed)
            GallerySort.RATING -> compareKnown(a.rating, b.rating, !reversed)
        }
    }

    fun <T> sections(
        ordered: List<T>,
        grouping: GalleryGrouping,
        reverse: Boolean,
        key: (T) -> GallerySortKey,
    ): List<GallerySection<T>> = when (grouping) {
        GalleryGrouping.NONE -> listOf(GallerySection(ordered))
        GalleryGrouping.YEAR, GalleryGrouping.MONTH, GalleryGrouping.DAY -> ordered.groupBy { item ->
            key(item).capturedAt?.toLocalDate()?.let { date ->
                when (grouping) {
                    GalleryGrouping.YEAR -> date.withDayOfYear(1)
                    GalleryGrouping.MONTH -> date.withDayOfMonth(1)
                    else -> date
                }
            }
        }.entries.sortedWith { a, b -> compareKnown(a.key, b.key, !reverse) }
            .map { GallerySection(it.value, date = it.key) }
        GalleryGrouping.RATING -> ordered.groupBy { key(it).rating }
            .entries.sortedWith { a, b -> compareKnown(a.key, b.key, !reverse) }
            .map { GallerySection(it.value, rating = it.key) }
    }

    private fun <T : Comparable<T>> compareKnown(a: T?, b: T?, descending: Boolean): Int = when {
        a == null -> if (b == null) 0 else 1
        b == null -> -1
        descending -> b.compareTo(a)
        else -> a.compareTo(b)
    }
}
