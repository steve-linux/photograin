// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.PhotoFlag
import io.github.steve_linux.photograin.model.PhotoRating
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoRatingTest {
    @Test
    fun readsExistingStarsAndFlags() {
        assertEquals(PhotoRating(4, PhotoFlag.PICKED), PhotoRating.fromStored(4, 1))
        assertEquals(PhotoRating(3, PhotoFlag.REJECTED), PhotoRating.fromStored(3, -1))
        assertEquals(PhotoRating(2, PhotoFlag.NONE), PhotoRating.fromStored(2, 0))
        assertEquals(PhotoRating(), PhotoRating.fromStored(null, null))
        assertEquals(PhotoRating(5), PhotoRating.fromStored(6, 99))
    }

    @Test
    fun assigningStarsAndFlagsKeepsTheOtherValue() {
        val picked = PhotoRating(1, PhotoFlag.PICKED)
        assertEquals(PhotoRating(4, PhotoFlag.PICKED), picked.withStars(4))
        assertEquals(PhotoRating(1, PhotoFlag.REJECTED), picked.copy(flag = PhotoFlag.REJECTED))
        assertEquals(PhotoRating(0, PhotoFlag.PICKED), picked.withStars(0))
    }

    @Test
    fun tappingTheSelectedStarClearsTheRatingButAssigningItDoesNot() {
        val rated = PhotoRating(3, PhotoFlag.REJECTED)
        assertEquals(PhotoRating(0, PhotoFlag.REJECTED), rated.toggleStars(3))
        assertEquals(rated, rated.withStars(3))
        assertEquals(PhotoRating(4, PhotoFlag.REJECTED), rated.toggleStars(4))
    }
}
