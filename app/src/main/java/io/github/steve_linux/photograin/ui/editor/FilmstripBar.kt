// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.model.CollectionPhoto
import io.github.steve_linux.photograin.model.PhotoFlag
import io.github.steve_linux.photograin.model.PhotoRating
import io.github.steve_linux.photograin.ui.home.ratingBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FilmstripBar(
    photos: List<CollectionPhoto<Uri>>,
    current: Uri?,
    enabled: Boolean,
    previous: CollectionPhoto<Uri>?,
    next: CollectionPhoto<Uri>?,
    ratings: Map<String, PhotoRating>,
    onOpen: (CollectionPhoto<Uri>) -> Unit,
) {
    val selected = photos.indexOfFirst { it.source == current }
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceAtLeast(0))
    LaunchedEffect(selected) {
        if (selected >= 0 && scroll.layoutInfo.visibleItemsInfo.none {
                it.index == selected && it.offset >= scroll.layoutInfo.viewportStartOffset &&
                    it.offset + it.size <= scroll.layoutInfo.viewportEndOffset
            }
        ) scroll.animateScrollToItem(selected)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(enabled = enabled && previous != null, onClick = { previous?.let(onOpen) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.filmstrip_previous))
            }
            LazyRow(
                modifier = Modifier.weight(1f).selectableGroup(),
                state = scroll,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(photos, key = { _, photo -> photo.source.toString() }) { index, photo ->
                    FilmstripThumbnail(
                        photo.source, index, index == selected, enabled, ratings[photo.source.toString()] ?: PhotoRating(),
                    ) { onOpen(photo) }
                }
            }
            IconButton(enabled = enabled && next != null, onClick = { next?.let(onOpen) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.filmstrip_next))
            }
        }
    }
}

private data class StripThumbnail(val bitmap: Bitmap?, val name: String?)

@Composable
private fun FilmstripThumbnail(
    uri: Uri,
    index: Int,
    selected: Boolean,
    enabled: Boolean,
    rating: PhotoRating,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val thumbnail by produceState<StripThumbnail?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            StripThumbnail(ImageLoader.thumbnail(context, uri, 144), ImageLoader.queryName(context, uri))
        }
    }
    val label = thumbnail?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.filmstrip_photo_number, index + 1)
    val starsLabel = if (rating.stars > 0) stringResource(R.string.photo_stars, rating.stars) else null
    val flagLabel = when (rating.flag) {
        PhotoFlag.PICKED -> stringResource(R.string.photo_flag_picked)
        PhotoFlag.REJECTED -> stringResource(R.string.photo_flag_rejected)
        PhotoFlag.NONE -> null
    }
    val spokenLabel = listOfNotNull(label, starsLabel, flagLabel).joinToString(", ")
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .size(54.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = spokenLabel }
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = thumbnail?.bitmap
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text((index + 1).toString(), style = MaterialTheme.typography.labelMedium)
        }
        val badge = ratingBadge(rating)
        if (badge.isNotEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).clearAndSetSemantics { },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    badge, modifier = Modifier.padding(horizontal = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
