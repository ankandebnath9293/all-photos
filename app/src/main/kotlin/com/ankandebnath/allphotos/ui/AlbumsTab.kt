package com.ankandebnath.allphotos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ankandebnath.allphotos.data.Album
import com.ankandebnath.allphotos.data.Db
import com.ankandebnath.allphotos.data.Library
import com.ankandebnath.allphotos.data.Media
import com.ankandebnath.allphotos.data.MediaRepo
import com.ankandebnath.allphotos.data.Prefs
import com.ankandebnath.allphotos.logic.Chat
import com.ankandebnath.allphotos.logic.DocKind
import com.ankandebnath.allphotos.logic.Fmt
import com.ankandebnath.allphotos.logic.Links

private class Shortcut(val icon: ImageVector, val label: String, val dest: Dest, val tint: @Composable () -> Pair<Color, Color>)

@Composable
fun AlbumsTab(lib: Library, app: AppState, state: LazyGridState, bottom: Dp) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val s by prefs.settings.collectAsState()
    val albums = remember(lib, s.pinnedAlbums) { MediaRepo.albums(lib, s.pinnedAlbums) }
    val docs by produceState(emptyList<Pair<DocKind, List<Media>>>(), lib) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val map = Db.get(ctx).docs()
            DocKind.entries.mapNotNull { k ->
                val list = lib.items.filter { map[it.id] == k.ordinal }
                if (list.isEmpty()) null else k to list
            }
        }
    }
    val c = MaterialTheme.colorScheme
    val shortcuts = listOf(
        Shortcut(Icons.Rounded.VideoLibrary, "Videos", Dest.Group(SetKind.VIDEOS, "Videos")) { c.primaryContainer to c.onPrimaryContainer },
        Shortcut(Icons.Rounded.Star, "Important", Dest.Group(SetKind.IMPORTANT, "Important")) { c.tertiaryContainer to c.onTertiaryContainer },
        Shortcut(Icons.Rounded.Favorite, "Favourites", Dest.Group(SetKind.FAVORITES, "Favourites")) { c.secondaryContainer to c.onSecondaryContainer },
        Shortcut(Icons.Rounded.Schedule, "Recent", Dest.Group(SetKind.RECENT, "Recently added")) { c.surfaceContainerHighest to c.onSurface },
        Shortcut(Icons.Rounded.Delete, "Trash", Dest.Trash) { c.errorContainer to c.onErrorContainer },
    )
    val shapes = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Pill, MaterialShapes.Cookie6Sided)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = state,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottom + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                PageHeader(
                    "Albums", "${albums.size} folders",
                    trailing = { TabActions(app) },
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    shortcuts.forEachIndexed { i, sc ->
                        val (bg, fg) = sc.tint()
                        Column(
                            Modifier.clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = { app.open(sc.dest) }).padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            IconBadge(sc.icon, shapes[i].toShape(), bg, fg, 56.dp)
                            Gap(6.dp)
                            Text(sc.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (docs.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Documents") }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 6.dp),
                ) {
                    docs.forEach { (kind, list) ->
                        DocRow(kind, list) { app.open(Dest.Group(SetKind.DOCS, kind.label, kind.ordinal.toString())) }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Folders") }
        }
        items(albums, key = { it.id }) { a ->
            AlbumCard(
                a,
                pinned = a.id in s.pinnedAlbums,
                onOpen = { app.open(Dest.Album(a.id, a.name)) },
                onPin = { on -> prefs.update { it.copy(pinnedAlbums = if (on) it.pinnedAlbums + a.id else it.pinnedAlbums - a.id) } },
            )
        }
    }
}

@Composable
private fun AlbumCard(a: Album, pinned: Boolean, onOpen: () -> Unit, onPin: (Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val px = with(LocalDensity.current) { 180.dp.roundToPx() }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))) {
            Thumb(a.cover, px, Modifier.fillMaxSize())
            if (pinned) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.PushPin, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (pinned) "Unpin" else "Pin to top") },
                    leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                    onClick = { menu = false; onPin(!pinned) },
                )
            }
        }
        Text(
            a.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp, top = 8.dp),
        )
        Text(
            "${a.count} · ${Fmt.size(a.bytes)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
        )
    }
}

/** One kind of document: its newest photo, its name and how many there are. */
@Composable
private fun DocRow(kind: DocKind, list: List<Media>, onClick: () -> Unit) {
    val px = with(LocalDensity.current) { 64.dp.roundToPx() }
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(16.dp))) { Thumb(list.first(), px, Modifier.fillMaxSize()) }
        GapW(16.dp)
        Column(Modifier.weight(1f)) {
            Text(kind.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${list.size} photo${if (list.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Items of one ready-made collection. */
fun groupItems(ctx: android.content.Context, lib: Library, kind: SetKind, key: String): List<Media> {
    val db = Db.get(ctx)
    return when (kind) {
        SetKind.VIDEOS -> lib.items.filter { it.isVideo }
        SetKind.FAVORITES -> lib.items.filter { it.favorite }
        SetKind.IMPORTANT -> {
            val imp = db.important.value
            lib.items.filter { it.id in imp }
        }
        SetKind.SCREENSHOTS -> lib.screenshots
        SetKind.SHOT_APP -> {
            val src = db.sources.value
            lib.screenshots.filter { (src[it.id]?.pkg ?: "") == key }
        }
        SetKind.SHOT_SITE -> {
            val src = db.sources.value
            lib.screenshots.filter { Links.domain(src[it.id]?.url) == key }
        }
        SetKind.WHATSAPP -> lib.items.filter { it.chat == Chat.WHATSAPP }
        SetKind.TELEGRAM -> lib.items.filter { it.chat == Chat.TELEGRAM }
        SetKind.FACEBOOK -> lib.items.filter { it.chat == Chat.FACEBOOK }
        SetKind.DOCS -> {
            val k = key.toIntOrNull() ?: -1
            val docs = db.docs()
            lib.items.filter { docs[it.id] == k }
        }
        SetKind.RECENT -> lib.items.sortedByDescending { it.added }.take(300)
    }
}

@Composable
fun GroupPage(d: Dest.Group, lib: Library, app: AppState, actions: Actions, bottom: Dp, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val important by remember { Db.get(ctx).important }.collectAsState()
    val sources by remember { Db.get(ctx).sources }.collectAsState()
    val items = remember(lib, d, important, sources) { groupItems(ctx, lib, d.kind, d.key) }
    val shots = d.kind == SetKind.SHOT_APP || d.kind == SetKind.SHOT_SITE || d.kind == SetKind.SCREENSHOTS
    val badge: (@Composable BoxScope.(Media) -> Unit)? = if (shots) { { m -> ExpiryBadge(m) } } else null
    GridPage(
        items = items,
        title = d.title,
        sub = countLabel(items) + if (items.isNotEmpty()) " · ${Fmt.size(items.sumOf { it.size })}" else "",
        app = app,
        actions = actions,
        bottom = bottom,
        onBack = onBack,
        headers = d.kind != SetKind.RECENT,
        badge = badge,
        empty = { NoPhotos() },
    )
}
