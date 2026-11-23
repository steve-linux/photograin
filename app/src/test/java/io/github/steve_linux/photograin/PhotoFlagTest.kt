// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.PhotoLibraryQuery
import io.github.steve_linux.photograin.model.PhotoLibraryQuery.Entry
import io.github.steve_linux.photograin.model.PhotoLibraryQuery.Flag
import io.github.steve_linux.photograin.model.PhotoLibraryQuery.FlagFilter
import io.github.steve_linux.photograin.model.PhotoLibraryQuery.Sort
import io.github.steve_linux.photograin.model.PhotoLibraryQuery.Status
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoFlagTest {
    private val entries = listOf(
        Entry("1", "a.jpg", "", 3, true, Flag.PICK),
        Entry("2", "b.jpg", "", 0, false, Flag.REJECT),
        Entry("3", "c.jpg", "", 5, true),
        Entry("4", "d.jpg", "", 4, false, Flag.PICK),
    )

    private fun find(flags: FlagFilter, minimum: Int = 0) =
        PhotoLibraryQuery.apply(entries, "", minimum, Status.ALL, Sort.RECENT, flags).map { it.key }

    @Test
    fun filtersByFlag() {
        assertEquals(listOf("1", "2", "3", "4"), find(FlagFilter.ALL))
        assertEquals(listOf("1", "4"), find(FlagFilter.PICKED))
        assertEquals(listOf("3"), find(FlagFilter.UNFLAGGED))
        assertEquals(listOf("2"), find(FlagFilter.REJECTED))
    }

    @Test
    fun flagsComposeWithStars() {
        assertEquals(listOf("4"), find(FlagFilter.PICKED, minimum = 4))
    }
}
