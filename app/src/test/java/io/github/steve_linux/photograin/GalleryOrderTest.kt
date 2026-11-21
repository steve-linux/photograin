// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.GalleryGrouping
import io.github.steve_linux.photograin.model.GalleryOrder
import io.github.steve_linux.photograin.model.GallerySort
import io.github.steve_linux.photograin.model.GallerySortKey
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryOrderTest {
    @Test
    fun readsExifDatesWithoutInventingDatesForMissingOrInvalidValues() {
        assertEquals(LocalDateTime.of(2024, 2, 29, 10, 2, 17), GalleryOrder.captureTime("2024:02:29 10:02:17"))
        assertNull(GalleryOrder.captureTime("2025:02:29 10:02:17"))
        assertNull(GalleryOrder.captureTime("0000:00:00 00:00:00"))
        assertNull(GalleryOrder.captureTime(null))
    }

    @Test
    fun captureAndFilenameSortingKeepMissingMetadataLastWhenReversed() {
        val a = GallerySortKey(0, LocalDateTime.of(2025, 11, 1, 4, 0), "Zebra.jpg")
        val b = GallerySortKey(1, LocalDateTime.of(2024, 1, 2, 4, 0), "alba.jpg")
        val missing = GallerySortKey(2)
        val input = listOf(missing, b, a)
        assertEquals(listOf(a, b, missing), input.sortedWith(GalleryOrder.comparator(GallerySort.CAPTURE, false)))
        assertEquals(listOf(b, a, missing), input.sortedWith(GalleryOrder.comparator(GallerySort.CAPTURE, true)))
        assertEquals(listOf(b, a, missing), input.sortedWith(GalleryOrder.comparator(GallerySort.FILE_NAME, false)))
        assertEquals(listOf(a, b, missing), input.sortedWith(GalleryOrder.comparator(GallerySort.FILE_NAME, true)))
    }

    @Test
    fun groupsByCaptureDateOrRatingAndKeepsOriginalOrderWithinEachGroup() {
        val keys = mapOf(
            "v2" to GallerySortKey(0, LocalDateTime.of(2025, 11, 11, 4, 0), rating = 3),
            "v1" to GallerySortKey(0, LocalDateTime.of(2025, 11, 11, 4, 0), rating = 3),
            "early" to GallerySortKey(1, LocalDateTime.of(2024, 11, 30, 4, 0), rating = 5),
            "unknown" to GallerySortKey(2),
        )
        val input = keys.keys.toList()
        val month = GalleryOrder.sections(input, GalleryGrouping.MONTH, true, keys::getValue)
        assertEquals(listOf(LocalDate.of(2024, 11, 1), LocalDate.of(2025, 11, 1), null), month.map { it.date })
        assertEquals(listOf("v2", "v1"), month[1].photos)
        val day = GalleryOrder.sections(input, GalleryGrouping.DAY, false, keys::getValue)
        assertEquals(LocalDate.of(2025, 11, 11), day.first().date)
        val years = GalleryOrder.sections(input, GalleryGrouping.YEAR, false, keys::getValue)
        assertEquals(LocalDate.of(2025, 1, 1), years.first().date)
        val stars = GalleryOrder.sections(input, GalleryGrouping.RATING, false, keys::getValue)
        assertEquals(listOf(5, 3, 0), stars.map { it.rating })
    }
}
