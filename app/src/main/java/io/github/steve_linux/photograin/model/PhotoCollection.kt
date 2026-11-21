// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

/** A photo and the version to open from the gallery. */
data class CollectionPhoto<T>(val source: T, val version: Int)

internal object PhotoCollection {
    /** Keep the displayed order and use the tapped version for its own photo. */
    fun <T> snapshot(ordered: List<CollectionPhoto<T>>, selected: CollectionPhoto<T>): List<CollectionPhoto<T>> =
        rememberVersion(ordered.distinctBy { it.source }, selected)

    fun <T> rememberVersion(photos: List<CollectionPhoto<T>>, selected: CollectionPhoto<T>): List<CollectionPhoto<T>> =
        photos.map { if (it.source == selected.source) selected else it }

    fun <T> neighbor(photos: List<CollectionPhoto<T>>, current: T, offset: Int): CollectionPhoto<T>? {
        val index = photos.indexOfFirst { it.source == current }
        return if (index < 0) null else photos.getOrNull(index + offset)
    }
}
