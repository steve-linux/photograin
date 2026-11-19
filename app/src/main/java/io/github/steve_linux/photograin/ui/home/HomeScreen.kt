// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.Metadata
import io.github.steve_linux.photograin.data.MetadataEntry
import io.github.steve_linux.photograin.data.RawImage
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.PhotoKeywordStore
import io.github.steve_linux.photograin.data.PhotoRatingStore
import io.github.steve_linux.photograin.data.RecipeStore
import io.github.steve_linux.photograin.data.RecipeVersion
import io.github.steve_linux.photograin.model.BurstFrame
import io.github.steve_linux.photograin.model.BurstReview
import io.github.steve_linux.photograin.model.CollectionPhoto
import io.github.steve_linux.photograin.model.GalleryGrouping
import io.github.steve_linux.photograin.model.GalleryOrder
import io.github.steve_linux.photograin.model.GallerySort
import io.github.steve_linux.photograin.model.GallerySortKey
import io.github.steve_linux.photograin.model.PhotoCollection
import io.github.steve_linux.photograin.model.PhotoKeywords
import io.github.steve_linux.photograin.model.PhotoFlag
import io.github.steve_linux.photograin.model.SmartAlbum
import io.github.steve_linux.photograin.model.PhotoRating
import io.github.steve_linux.photograin.ui.editor.versionLabel
import io.github.steve_linux.photograin.ui.editor.QuickAction
import io.github.steve_linux.photograin.ui.editor.QuickActionsCard
import io.github.steve_linux.photograin.ui.guide.GuideEntry
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recents: List<Uri>,
    onOpenPicker: () -> Unit,
    onOpen: (photo: CollectionPhoto<Uri>, collection: List<CollectionPhoto<Uri>>) -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    onBatch: () -> Unit,
    showTip: Boolean,
    onDismissTip: () -> Unit,
    onSettings: () -> Unit,
    onGuide: () -> Unit,
) {
    var showBursts by rememberSaveable { mutableStateOf(false) }
    var burst by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var shotPair by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val comparePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { if (it.size >= 2) shotPair = it.take(2) }
    if (shotPair.size == 2) CompareShotsDialog(shotPair) { shotPair = emptyList() }
    var differenceEntry by remember { mutableStateOf<HomeEntry?>(null) }
    differenceEntry?.let { EditDifferenceDialog(it.uri, it.version.id) { differenceEntry = null } }
    var composing by rememberSaveable { mutableStateOf(false) }
    if (composing) CombinePhotosDialog(onDismiss = { composing = false })
    var contactSelection by remember { mutableStateOf<List<Uri>>(emptyList()) }
    if (contactSelection.isNotEmpty()) CombinePhotosDialog(contactSelection) { contactSelection = emptyList() }
    var comparing by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val keywordStore = remember(context) { PhotoKeywordStore.get(context) }
    val photoKeywords by keywordStore.state.collectAsStateWithLifecycle()
    var keywordTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var keywordFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showKeywordFilter by rememberSaveable { mutableStateOf(false) }
    keywordTarget?.let { PhotoKeywordEditor(Uri.parse(it)) { keywordTarget = null } }

    val ratingStore = remember(context) { PhotoRatingStore.get(context) }
    val marks by ratingStore.state.collectAsStateWithLifecycle()
    var ratingFilter by rememberSaveable { mutableStateOf(0) }
    var ratingTarget by rememberSaveable { mutableStateOf<String?>(null) }
    fun rating(uri: Uri): Int = marks[uri.toString()]?.stars ?: 0

    ratingTarget?.let { key ->
        val uri = Uri.parse(key)
        AlertDialog(
            onDismissRequest = { ratingTarget = null },
            title = { Text(stringResource(R.string.photo_rating)) },
            text = {
                PhotoReviewControls(
                    rating = marks[key] ?: PhotoRating(),
                    onStars = { ratingStore.toggleStars(uri, it) },
                    onFlag = { ratingStore.flag(uri, it) },
                )
            },
            confirmButton = { TextButton(onClick = { ratingTarget = null }) { Text(stringResource(R.string.close)) } },
        )
    }
    val albumPreferences = remember(context) { context.getSharedPreferences("photo_albums", android.content.Context.MODE_PRIVATE) }
    var albumNames by remember {
        mutableStateOf(albumPreferences.getStringSet("names", emptySet()).orEmpty().sorted())
    }
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var albumRevision by remember { mutableStateOf(0) }
    var showAlbums by rememberSaveable { mutableStateOf(false) }
    var albumTargetUri by rememberSaveable { mutableStateOf<String?>(null) }
    var albumDraft by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var rawOnly by rememberSaveable { mutableStateOf(false) }
    var editedOnly by rememberSaveable { mutableStateOf(false) }
    var smartNames by remember {
        mutableStateOf(albumPreferences.getStringSet("smart_names", emptySet()).orEmpty().sorted())
    }
    var smartName by rememberSaveable { mutableStateOf<String?>(null) }
    var savingSmart by rememberSaveable { mutableStateOf(false) }
    var smartDraft by rememberSaveable { mutableStateOf("") }
    var smartStars by rememberSaveable { mutableStateOf(0) }
    val smartRule = remember(smartName) {
        smartName?.let { name ->
            albumPreferences.getString("smart:$name", null)?.let {
                runCatching { Json.decodeFromString<SmartAlbum>(it) }.getOrNull()
            }
        }
    }
    val liveRule = remember(query, rawOnly, editedOnly, keywordFilter, ratingFilter) {
        SmartAlbum(
            query = query, rawOnly = rawOnly, editedOnly = editedOnly, keyword = keywordFilter,
            minimumStars = if (ratingFilter == 2) 3 else 0,
            flag = when (ratingFilter) {
                1 -> PhotoFlag.PICKED.stored
                3 -> PhotoFlag.REJECTED.stored
                else -> null
            },
            excludeRejected = ratingFilter == 2,
        )
    }
    if (savingSmart) {
        val name = smartDraft.trim()
        AlertDialog(
            onDismissRequest = { savingSmart = false },
            title = { Text(stringResource(R.string.smart_save)) },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = smartDraft, onValueChange = { smartDraft = it.take(64) },
                        label = { Text(stringResource(R.string.photo_album_name)) }, singleLine = true,
                    )
                    TextButton(onClick = { smartStars = (smartStars + 1) % 6 }) {
                        Text(stringResource(R.string.smart_min_stars, smartStars))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotEmpty() && name !in smartNames && name !in albumNames, onClick = {
                    smartNames = (smartNames + name).sorted()
                    albumPreferences.edit().putStringSet("smart_names", smartNames.toSet())
                        .putString("smart:$name", Json.encodeToString(liveRule.copy(minimumStars = smartStars))).apply()
                    savingSmart = false
                }) { Text(stringResource(R.string.photo_album_create)) }
            },
            dismissButton = { TextButton(onClick = { savingSmart = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    fun openSmart(name: String) {
        album = null
        smartName = name
        query = ""
        rawOnly = false
        editedOnly = false
        keywordFilter = null
        ratingFilter = 0
        showAlbums = false
    }
    fun members(name: String): Set<String> = albumPreferences.getStringSet("album:$name", emptySet()).orEmpty()
    val photos = remember(recents, album, albumRevision, smartRule, albumNames) {
        when {
            smartRule != null -> (recents + albumNames.flatMap { members(it).map(Uri::parse) }).distinct()
            album != null -> members(checkNotNull(album)).sorted().map(Uri::parse)
            else -> recents
        }
    }

    if (showKeywordFilter) {
        val available = remember(photos, photoKeywords) {
            photos.asSequence().flatMap { uri -> photoKeywords[uri.toString()].orEmpty().asSequence() }
                .distinctBy(PhotoKeywords::key).sortedBy(PhotoKeywords::key).toList()
        }
        KeywordFilterDialog(
            keywords = available,
            selected = keywordFilter,
            onSelect = { keywordFilter = it; showKeywordFilter = false },
            onDismiss = { showKeywordFilter = false },
        )
    }

    if (showAlbums) {
        val target = albumTargetUri
        val proposed = albumDraft.trim()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAlbums = false },
            title = { Text(stringResource(R.string.photo_albums)) },
            text = {
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    if (target == null) {
                        TextButton(onClick = { album = null; smartName = null; showAlbums = false }) {
                            Text(stringResource(R.string.photo_recent_album))
                        }
                        for (name in smartNames) {
                            TextButton(onClick = { openSmart(name) }) {
                                Text(stringResource(R.string.smart_album_label, name))
                            }
                        }
                        smartName?.let { name ->
                            TextButton(onClick = {
                                smartNames = smartNames - name
                                albumPreferences.edit().putStringSet("smart_names", smartNames.toSet())
                                    .remove("smart:$name").apply()
                                smartName = null
                            }) { Text(stringResource(R.string.photo_album_delete)) }
                        }
                        val currentAlbum = album
                        if (currentAlbum != null) {
                            TextButton(onClick = {
                                albumNames = albumNames - currentAlbum
                                albumPreferences.edit().putStringSet("names", albumNames.toSet())
                                    .remove("album:$currentAlbum").apply()
                                album = null
                                albumRevision++
                            }) { Text(stringResource(R.string.photo_album_delete)) }
                        }
                    }
                    for (name in albumNames) {
                        val contained = remember(name, target, albumRevision) { target != null && target in members(name) }
                        TextButton(onClick = {
                            if (target == null) {
                                album = name
                                smartName = null
                                showAlbums = false
                            } else {
                                val uri = target
                                val updated = if (contained) members(name) - uri else members(name) + uri
                                albumPreferences.edit().putStringSet("album:$name", updated).apply()
                                albumRevision++
                            }
                        }) { Text((if (contained) "✓ " else "") + name) }
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = albumDraft,
                        onValueChange = { if (it.length <= 64) albumDraft = it },
                        label = { Text(stringResource(R.string.photo_album_name)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = proposed.isNotEmpty() && proposed !in albumNames && proposed !in smartNames, onClick = {
                    albumNames = (albumNames + proposed).sorted()
                    val update = albumPreferences.edit().putStringSet("names", albumNames.toSet())
                    if (target != null) update.putStringSet("album:$proposed", setOf(target))
                    update.apply()
                    albumRevision++
                    if (target == null) {
                        album = proposed
                        smartName = null
                    }
                    albumDraft = ""
                    showAlbums = false
                }) { Text(stringResource(R.string.photo_album_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showAlbums = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
    var sort by rememberSaveable { mutableStateOf(GallerySort.COLLECTION) }
    var grouping by rememberSaveable { mutableStateOf(GalleryGrouping.NONE) }
    var reverseSort by rememberSaveable { mutableStateOf(false) }
    var showOrder by rememberSaveable { mutableStateOf(false) }
    if (showOrder) {
        GalleryOrderDialog(sort, grouping, reverseSort, { sort = it }, { grouping = it }, { reverseSort = it }) {
            showOrder = false
        }
    }

    val needsExif = showBursts || query.isNotBlank() || !smartRule?.query.isNullOrBlank() || sort == GallerySort.CAPTURE || grouping.usesCaptureDate
    val needsIndex = needsExif || rawOnly || smartRule?.rawOnly == true || sort == GallerySort.FILE_NAME
    val index by produceState<PhotoSearchIndex?>(null, photos, needsIndex, needsExif) {
        value = null
        if (!needsIndex) return@produceState
        value = withContext(Dispatchers.IO) {
            val data = mutableMapOf<Uri, PhotoSearchData>()
            for (uri in photos.distinct()) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val name = ImageLoader.queryName(context, uri)?.takeIf { it.isNotBlank() }
                val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                val (metadata, capturedAt) = if (needsExif) Metadata.readForGallery(context, uri)
                    else emptyList<MetadataEntry>() to null
                val terms = listOfNotNull(name, mime) + metadata.filter { it.label in photoSearchFields }.map { it.value }
                val camera = metadata.firstOrNull { it.label == R.string.metadata_camera_model }?.value
                data[uri] = PhotoSearchData(name, capturedAt, terms.joinToString(" "), RawImage.isRaw(mime, name), camera)
            }
            PhotoSearchIndex(photos, data, needsExif)
        }
    }
    val currentIndex = index?.takeIf { it.recents == photos && (!needsExif || it.hasExif) }
    // Each version of a photo gets its own tile, as Darktable shows duplicates.
    val fallbackEntries = remember(photos) { photos.map { HomeEntry(it, RecipeVersion(1), 1) } }
    val loadedEntries by produceState<Pair<List<Uri>, List<HomeEntry>>?>(null, photos) {
        value = withContext(Dispatchers.IO) {
            val store = RecipeStore(context)
            val versionsByPhoto = photos.flatMap { uri ->
                val versions = store.versions(uri)
                versions.map { HomeEntry(uri, it, versions.size, store.load(uri, it.id)?.let { edit -> edit != io.github.steve_linux.photograin.model.EditState() } == true) }
            }
            photos to versionsByPhoto
        }
    }
    val entries = loadedEntries?.takeIf { it.first == photos }?.second ?: fallbackEntries
    val filtered = remember(entries, liveRule, smartRule, marks, currentIndex, photoKeywords) {
        entries.filter { entry ->
            val indexed = currentIndex?.data?.get(entry.uri)
            val keywords = photoKeywords[entry.uri.toString()].orEmpty()
            val text = indexed?.terms.orEmpty() + " " + entry.version.name.orEmpty()
            val rating = marks[entry.uri.toString()] ?: PhotoRating()
            val raw = indexed?.raw == true
            liveRule.accepts(text, raw, entry.edited, keywords, rating) &&
                (smartRule?.accepts(text, raw, entry.edited, keywords, rating) ?: true)
        }
    }
    val displayedGrouping = if (grouping.usesCaptureDate && currentIndex == null) GalleryGrouping.NONE else grouping
    val sections = remember(filtered, photos, currentIndex, sort, reverseSort, displayedGrouping, marks) {
        val keys = photos.withIndex().associate { (position, uri) ->
            val source = currentIndex?.data?.get(uri)
            uri to GallerySortKey(position, source?.capturedAt, source?.fileName, rating(uri))
        }
        fun key(entry: HomeEntry) = keys[entry.uri] ?: GallerySortKey(Int.MAX_VALUE)
        val comparator = GalleryOrder.comparator(sort, reverseSort)
        val ordered = filtered.sortedWith { a, b -> comparator.compare(key(a), key(b)) }
        val reverseGroups = reverseSort && (
            (displayedGrouping.usesCaptureDate && sort == GallerySort.CAPTURE) ||
                (displayedGrouping == GalleryGrouping.RATING && sort == GallerySort.RATING)
            )
        GalleryOrder.sections(ordered, displayedGrouping, reverseGroups, ::key)
    }
    val galleryPhotos = remember(sections) {
        sections.flatMap { section -> section.photos.map { CollectionPhoto(it.uri, it.version.id) } }
    }
    if (showBursts) {
        val groups = remember(galleryPhotos, currentIndex) {
            val data = currentIndex?.data.orEmpty()
            BurstReview.groups(galleryPhotos.distinctBy { it.source }.mapNotNull { photo ->
                val indexed = data[photo.source] ?: return@mapNotNull null
                val time = indexed.capturedAt ?: return@mapNotNull null
                BurstFrame(photo.source, time, indexed.camera)
            })
        }
        AlertDialog(
            onDismissRequest = { showBursts = false },
            title = { Text(stringResource(R.string.burst_title)) },
            text = {
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    when {
                        currentIndex == null -> Text(stringResource(R.string.photo_indexing))
                        groups.isEmpty() -> Text(stringResource(R.string.burst_empty))
                        else -> for (group in groups) {
                            val date = group.first().time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
                            TextButton(onClick = {
                                burst = group.map { it.photo }
                                showBursts = false
                            }) { Text(stringResource(R.string.burst_group, date, group.size)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBursts = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (burst.size >= 2) {
        CompareShotsDialog(
            photos = burst,
            onPick = { ratingStore.flag(it, PhotoFlag.PICKED); burst = emptyList() },
            onDismiss = { burst = emptyList() },
        )
    }
    fun openFromGallery(entry: HomeEntry) {
        val selected = CollectionPhoto(entry.uri, entry.version.id)
        onOpen(selected, PhotoCollection.snapshot(galleryPhotos, selected))
    }
    val locale = LocalConfiguration.current.locales[0]
    val dateGroupFormat = remember(locale, displayedGrouping) {
        when (displayedGrouping) {
            GalleryGrouping.YEAR -> DateTimeFormatter.ofPattern("uuuu", locale)
            GalleryGrouping.MONTH -> DateTimeFormatter.ofPattern("LLLL uuuu", locale)
            else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(smartName ?: album ?: stringResource(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (recents.isNotEmpty()) {
                        IconButton(onClick = { comparing = !comparing }) {
                            Icon(
                                if (comparing) Icons.Outlined.GridView else Icons.Outlined.Compare,
                                contentDescription = stringResource(
                                    if (comparing) R.string.home_show_grid else R.string.home_show_compare,
                                ),
                            )
                        }
                    }
                    TextButton(onClick = { albumTargetUri = null; albumDraft = ""; showAlbums = true }) {
                        Text(stringResource(R.string.photo_albums))
                    }
                    TextButton(onClick = { composing = true }) {
                        Text(stringResource(R.string.combine_photos))
                    }
                    TextButton(onClick = onBatch) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_batch))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenPicker,
                icon = { Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null) },
                text = { Text(stringResource(R.string.open_photo)) },
            )
        },
    ) { padding ->
        if (photos.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(if (album == null && smartName == null) R.string.home_empty_title else R.string.photo_album_empty),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (album == null && smartName == null) R.string.home_empty_body else R.string.photo_album_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                QuickActionsCard(onQuickAction)
                Spacer(Modifier.height(12.dp))
                GuideEntry(onGuide)
                if (showTip) {
                    Spacer(Modifier.height(12.dp))
                    PresetTip(onDismissTip)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { QuickActionsCard(onQuickAction) }
                item(span = { GridItemSpan(maxLineSpan) }) { GuideEntry(onGuide) }
                if (showTip) {
                    item(span = { GridItemSpan(maxLineSpan) }) { PresetTip(onDismissTip) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.recent_edits),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(4.dp),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        androidx.compose.material3.OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.photo_search)) },
                            trailingIcon = if (query.isNotEmpty()) {
                                {
                                    TextButton(onClick = { query = "" }) {
                                        Text(stringResource(R.string.photo_clear_search))
                                    }
                                }
                            } else null,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.FilterChip(
                                selected = rawOnly, onClick = { rawOnly = !rawOnly },
                                label = { Text("RAW") },
                            )
                            androidx.compose.material3.FilterChip(
                                selected = editedOnly, onClick = { editedOnly = !editedOnly },
                                label = { Text(stringResource(R.string.photo_edited_only)) },
                            )
                        }
                        FilterChip(
                            selected = keywordFilter != null,
                            onClick = { showKeywordFilter = true },
                            label = {
                                Text(
                                    keywordFilter?.let { stringResource(R.string.photo_keyword_filter_active, it) }
                                        ?: stringResource(R.string.photo_keyword_filter),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                    TextButton(onClick = { showOrder = true }) {
                        Text(stringResource(R.string.gallery_order_current, stringResource(gallerySortLabel(sort))))
                    }
                    TextButton(onClick = { comparePicker.launch(arrayOf("image/*")) }) {
                        Text(stringResource(R.string.compare_shots))
                    }
                    TextButton(onClick = { showBursts = true }) {
                        Text(stringResource(R.string.burst_title))
                    }
                    val filters = listOf(R.string.photo_filter_all, R.string.photo_pick, R.string.photo_filter_three, R.string.photo_reject)
                    TextButton(onClick = { ratingFilter = (ratingFilter + 1) % filters.size }) {
                        Text(stringResource(R.string.photo_filter, stringResource(filters[ratingFilter])))
                    }
                    if (smartName == null) TextButton(onClick = {
                        smartDraft = ""
                        smartStars = liveRule.minimumStars
                        savingSmart = true
                    }) { Text(stringResource(R.string.smart_save)) }
                    if (filtered.isNotEmpty()) {
                        TextButton(onClick = { contactSelection = sections.flatMap { section -> section.photos.map { it.uri } }.distinct().take(12) }) {
                            Text(stringResource(R.string.combine_contact))
                        }
                    }
                }
                if (needsIndex && currentIndex == null) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.photo_indexing))
                } else if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.photo_filter_empty))
                }
                if (comparing) item(span = { GridItemSpan(maxLineSpan) }) { CompareHeader() }
                for (section in sections) {
                    if (displayedGrouping != GalleryGrouping.NONE) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val label = when (displayedGrouping) {
                                GalleryGrouping.YEAR, GalleryGrouping.MONTH, GalleryGrouping.DAY ->
                                    section.date?.format(dateGroupFormat) ?: stringResource(R.string.gallery_no_capture_date)
                                GalleryGrouping.RATING -> section.rating?.takeIf { it > 0 }?.let { stringResource(R.string.photo_stars, it) }
                                    ?: stringResource(R.string.gallery_unrated)
                                GalleryGrouping.NONE -> ""
                            }
                            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    if (comparing) {
                        items(section.photos, key = { "compare ${it.key}" }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                            Column {
                                CompareRow(entry, onClick = { openFromGallery(entry) })
                                if (sort == GallerySort.FILE_NAME) GalleryFileName(currentIndex?.data?.get(entry.uri)?.fileName)
                                GalleryRatingButton(marks[entry.uri.toString()] ?: PhotoRating()) {
                                    ratingTarget = entry.uri.toString()
                                }
                                TextButton(onClick = { keywordTarget = entry.uri.toString() }) {
                                    Text(stringResource(R.string.photo_keywords))
                                }
                                if (entry.edited) TextButton(onClick = { differenceEntry = entry }) {
                                    Text(stringResource(R.string.edit_difference))
                                }
                            }
                        }
                    } else {
                        items(section.photos, key = { it.key }) { entry ->
                            Column {
                                if (entry.count > 1) {
                                    VersionTile(entry, onClick = { openFromGallery(entry) })
                                } else {
                                    RecentThumbnail(entry.uri, onClick = { openFromGallery(entry) })
                                }
                                if (sort == GallerySort.FILE_NAME) GalleryFileName(currentIndex?.data?.get(entry.uri)?.fileName)
                                TextButton(onClick = {
                                    albumTargetUri = entry.uri.toString()
                                    albumDraft = ""
                                    showAlbums = true
                                }) { Text(stringResource(R.string.photo_albums)) }
                                GalleryRatingButton(marks[entry.uri.toString()] ?: PhotoRating()) {
                                    ratingTarget = entry.uri.toString()
                                }
                                TextButton(onClick = { keywordTarget = entry.uri.toString() }) {
                                    Text(stringResource(R.string.photo_keywords))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryRatingButton(rating: PhotoRating, onClick: () -> Unit) {
    val label = stringResource(R.string.photo_rating)
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Text(ratingBadge(rating).ifEmpty { label })
    }
}

@Composable
private fun GalleryFileName(name: String?) {
    if (name == null) return
    Text(
        name,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun GalleryOrderDialog(
    sort: GallerySort,
    grouping: GalleryGrouping,
    reverse: Boolean,
    onSort: (GallerySort) -> Unit,
    onGrouping: (GalleryGrouping) -> Unit,
    onReverse: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gallery_order)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                for (option in GallerySort.entries) {
                    FilterChip(
                        selected = sort == option,
                        onClick = { onSort(option) },
                        label = { Text(stringResource(gallerySortLabel(option))) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = stringResource(R.string.gallery_reverse_order)
                    Text(label, Modifier.weight(1f))
                    Switch(
                        checked = reverse,
                        onCheckedChange = onReverse,
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
                Text(stringResource(R.string.gallery_grouping), style = MaterialTheme.typography.titleSmall)
                for (option in GalleryGrouping.entries) {
                    FilterChip(
                        selected = grouping == option,
                        onClick = { onGrouping(option) },
                        label = { Text(stringResource(galleryGroupingLabel(option))) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private fun gallerySortLabel(sort: GallerySort): Int = when (sort) {
    GallerySort.COLLECTION -> R.string.gallery_sort_collection
    GallerySort.CAPTURE -> R.string.gallery_sort_capture
    GallerySort.FILE_NAME -> R.string.gallery_sort_file_name
    GallerySort.RATING -> R.string.gallery_sort_rating
}

private fun galleryGroupingLabel(group: GalleryGrouping): Int = when (group) {
    GalleryGrouping.NONE -> R.string.gallery_group_none
    GalleryGrouping.YEAR -> R.string.gallery_group_year
    GalleryGrouping.MONTH -> R.string.gallery_group_month
    GalleryGrouping.DAY -> R.string.gallery_group_day
    GalleryGrouping.RATING -> R.string.gallery_sort_rating
}

@Composable
private fun KeywordFilterDialog(
    keywords: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedKey = selected?.let(PhotoKeywords::key)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photo_keyword_filter)) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.photo_keyword_any)) }
                if (keywords.isEmpty()) Text(stringResource(R.string.photo_keyword_filter_empty))
                for (keyword in keywords) {
                    FilterChip(
                        selected = PhotoKeywords.key(keyword) == selectedKey,
                        onClick = { onSelect(keyword) },
                        label = { Text(keyword) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** Explains where presets are saved, which new users rarely find on their own. */
@Composable
private fun PresetTip(onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_tip_title), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            // {icon} in the text stands for the editor's compare button, drawn inline.
            val body = stringResource(R.string.home_tip_body)
            val text = buildAnnotatedString {
                val parts = body.split("{icon}", limit = 2)
                append(parts[0])
                if (parts.size == 2) {
                    appendInlineContent(COMPARE_ICON, "[compare]")
                    append(parts[1])
                }
            }
            val iconContent = mapOf(
                COMPARE_ICON to InlineTextContent(
                    Placeholder(1.3.em, 1.3.em, PlaceholderVerticalAlign.TextCenter),
                ) { Icon(Icons.Outlined.Compare, contentDescription = stringResource(R.string.compare)) },
            )
            Text(
                text,
                inlineContent = iconContent,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.home_tip_dismiss))
            }
        }
    }
}

private const val COMPARE_ICON = "compare"

@Composable
internal fun RecentThumbnail(uri: Uri, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb: Bitmap? by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { ImageLoader.thumbnail(context, uri, 384) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        thumb?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** One version of a recent photo; [count] is how many the photo has. */
private data class PhotoSearchIndex(val recents: List<Uri>, val data: Map<Uri, PhotoSearchData>, val hasExif: Boolean)

private data class PhotoSearchData(val fileName: String?, val capturedAt: LocalDateTime?, val terms: String, val raw: Boolean, val camera: String?)

private val photoSearchFields = setOf(
    R.string.metadata_camera_model, R.string.metadata_lens, R.string.metadata_iso, R.string.metadata_taken,
)

private data class HomeEntry(val uri: Uri, val version: RecipeVersion, val count: Int, val edited: Boolean = false) {
    val key get() = "$uri#${version.id}"
}

/** A version of a photo with several: its own edit, and its name in a corner. */
@Composable
private fun VersionTile(entry: HomeEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb: Bitmap? by produceState<Bitmap?>(null, entry.uri, entry.version.id) {
        value = EditedThumbnails.load(context, entry.uri, 384, entry.version.id)
            ?: withContext(Dispatchers.IO) { ImageLoader.thumbnail(context, entry.uri, 384) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        thumb?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        VersionBadge(entry.version, Modifier.align(Alignment.BottomStart).padding(4.dp))
    }
}

@Composable
private fun VersionBadge(version: RecipeVersion, modifier: Modifier = Modifier) {
    Text(
        versionLabel(version),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun CompareHeader() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (label in listOf(R.string.home_original, R.string.home_edited)) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The original and the edited photo side by side, whole, so the change is easy to judge. */
@Composable
private fun CompareRow(entry: HomeEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val uri = entry.uri
    val original: Bitmap? by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { ImageLoader.thumbnail(context, uri, COMPARE_SIZE) }
    }
    // (rendered, bitmap): the right side stays empty until the render is done.
    val edited by produceState<Pair<Boolean, Bitmap?>>(false to null, uri, entry.version.id) {
        value = true to EditedThumbnails.load(context, uri, COMPARE_SIZE, entry.version.id)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
    ) {
        // Without a recipe the photo is unchanged, so both sides show the original.
        for (bitmap in listOf(original, if (edited.first) edited.second ?: original else null)) {
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (entry.count > 1 && bitmap != null && bitmap !== original) {
                    VersionBadge(entry.version, Modifier.align(Alignment.BottomStart).padding(4.dp))
                }
            }
        }
    }
}

private const val COMPARE_SIZE = 512
