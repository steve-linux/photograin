// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.PhotoLibraryQuery
import io.github.steve_linux.photograin.model.PhotoLibraryQuery.Entry
import io.github.steve_linux.photograin.model.PhotoLibraryQuery.Sort
import io.github.steve_linux.photograin.model.PhotoLibraryQuery.Status
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PhotoLibraryQueryTest {
    private val entries = listOf(
        Entry("1", "Città.jpg", "Tramonto caldo", 4, true),
        Entry("2", "Alba.dng", "Versione 1", 5, false),
        Entry("3", "Città.jpg", "Bianco e nero", 4, true),
        Entry("4", "Mare.jpg", "Versione 1", 0, false),
    )
    private fun find(query: String = "", minimum: Int = 0, status: Status = Status.ALL, sort: Sort = Sort.RECENT) =
        PhotoLibraryQuery.apply(entries, query, minimum, status, sort).map { it.key }

    @Test fun findsFileAndVersionAcrossCaseAccentsAndWhitespace() {
        assertEquals(listOf("1", "3"), find("CITTA"))
        assertEquals(listOf("1"), find("  citta   CALDO  "))
        assertEquals(listOf("2"), find("DNG"))
        assertEquals(listOf("2", "4"), find("versione 1"))
    }
    @Test fun filtersComposeWithStarsAndVersionEditState() {
        assertEquals(listOf("1", "3"), find(minimum = 4, status = Status.EDITED))
        assertEquals(listOf("2"), find(minimum = 4, status = Status.UNEDITED))
        assertTrue(find("mare", minimum = 1).isEmpty())
        assertTrue(find("caldo", status = Status.UNEDITED).isEmpty())
    }
    @Test fun sortsStablyWithoutChangingInputOrVersionIdentity() {
        assertEquals(listOf("2", "1", "3", "4"), find(sort = Sort.NAME))
        assertEquals(listOf("2", "1", "3", "4"), find(sort = Sort.RATING))
        assertEquals(listOf("1", "2", "3", "4"), find())
        assertEquals(4, entries.size)
    }
    @Test fun blankSearchAndNoMatchesAreSafe() {
        assertEquals(find(), find(" \t\n "))
        assertTrue(find("missing").isEmpty())
        assertTrue(PhotoLibraryQuery.apply(emptyList(), "a", 0, Status.ALL, Sort.NAME).isEmpty())
    }
    @Test fun searchDoesNotDependOnSystemLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf("1", "3"), find("CITTA"))
        } finally { Locale.setDefault(original) }
    }
    @Test fun recipesOfSamePhotoCanHaveDifferentEditedState() {
        val versions = listOf(Entry("photo#1", "photo.jpg", "Original", 3, false),
            Entry("photo#2", "photo.jpg", "Portrait", 3, true))
        assertEquals(listOf("photo#2"), PhotoLibraryQuery.apply(versions, "photo", 3, Status.EDITED, Sort.RECENT).map { it.key })
        assertEquals(listOf("photo#1"), PhotoLibraryQuery.apply(versions, "original", 3, Status.UNEDITED, Sort.RECENT).map { it.key })
    }
}
