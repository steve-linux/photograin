// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

internal enum class PhotoFlag(val stored: Int) { NONE(0), PICKED(1), REJECTED(-1) }

internal data class PhotoRating(val stars: Int = 0, val flag: PhotoFlag = PhotoFlag.NONE) {
    fun withStars(stars: Int): PhotoRating = copy(stars = stars.coerceIn(0, 5))

    fun toggleStars(stars: Int): PhotoRating = withStars(if (this.stars == stars) 0 else stars)

    companion object {
        fun fromStored(stars: Int?, flag: Int?): PhotoRating = PhotoRating(
            stars = (stars ?: 0).coerceIn(0, 5),
            flag = PhotoFlag.entries.firstOrNull { it.stored == flag } ?: PhotoFlag.NONE,
        )
    }
}
