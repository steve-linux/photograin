// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.CollectionPhoto
import io.github.steve_linux.photograin.model.PhotoCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoCollectionTest {
    @Test
    fun takesOnePhotoPerOriginalInTheDisplayedOrderAndOpensTheTappedVersion() {
        val selected = CollectionPhoto("third", 2)
        val displayed = listOf(CollectionPhoto("third", 1), CollectionPhoto("second", 3), selected, CollectionPhoto("first", 1))
        val strip = PhotoCollection.snapshot(displayed, selected)
        assertEquals(listOf(selected, CollectionPhoto("second", 3), CollectionPhoto("first", 1)), strip)
    }

    @Test
    fun navigatesByOriginalAndStopsAtBothEnds() {
        val strip = listOf(CollectionPhoto("a", 2), CollectionPhoto("b", 1), CollectionPhoto("c", 3))
        assertEquals(strip[0], PhotoCollection.neighbor(strip, "b", -1))
        assertEquals(strip[2], PhotoCollection.neighbor(strip, "b", 1))
        assertNull(PhotoCollection.neighbor(strip, "a", -1))
        assertNull(PhotoCollection.neighbor(strip, "c", 1))
        assertNull(PhotoCollection.neighbor(strip, "missing", 1))
    }

    @Test
    fun remembersAVersionWithoutMovingItsOriginal() {
        val strip = listOf(CollectionPhoto("a", 2), CollectionPhoto("b", 1))
        val changed = PhotoCollection.rememberVersion(strip, CollectionPhoto("a", 3))
        assertEquals(listOf(CollectionPhoto("a", 3), CollectionPhoto("b", 1)), changed)
        assertEquals(2, strip[0].version)
    }
}
